#!/usr/bin/env python3
"""Application-level verification: feed KhatKit group-chat exports to a real SillyTavern server.

Upstream under test
-------------------
SillyTavern (https://github.com/SillyTavern/SillyTavern), pinned commit
   06bde939fb1e9c4c8d8641d810f0a916b5bce127
License: AGPL-3.0 (only run locally; never distributed; no upstream source is copied
into this repository -- this harness only talks HTTP to a server whose checkout lives
under /tmp).

What it proves (application level, not parser level)
----------------------------------------------------
The export format is SillyTavern's *group chat history* file (``groupChats/<id>.jsonl``):
line 1 is a ``chat_metadata`` header, every later line is a message. This harness:

  1. checks the pinned commit + AGPL license of the checkout;
  2. imports a .jsonl through the server's own ``POST /api/chats/group/import``;
  3. reads it back through ``POST /api/chats/group/get`` and asserts message count,
     order, ``name`` attribution, ``is_user`` / ``is_system``;
  4. checks the private top-level keys ``khatkit_group`` / ``khatkit_character_names``
     are preserved (not rejected) by the server;
  5. registers a minimal group through ``POST /api/groups/create`` and shows the file
     is invisible before registration, visible after;
  6. reproduces the client's open->save header rebuild (group-chats.js:632-636) and
     POSTs it to ``POST /api/chats/group/save`` to confirm the private top-level block
     is dropped.

Relevant server/client source (at the pinned commit):
  - src/endpoints/chats.js  getChatData (line-by-line tryParse), /group/import, /group/get,
    /group/save, /search, /recent
  - src/util.js             tryParse
  - public/scripts/group-chats.js  getGroupChat, openGroupChat (registration gate),
    saveGroupChat (header rebuild from chat_metadata only)

Usage
-----
    # 1. one-time, outside this repo (keeps AGPL checkout in /tmp):
    #    git init /tmp/opencode/sillytavern-server/SillyTavern
    #    cd ... && git remote add origin https://github.com/SillyTavern/SillyTavern.git
    #    git fetch --depth 1 origin 06bde939fb1e9c4c8d8641d810f0a916b5bce127
    #    git checkout FETCH_HEAD && npm install
    #    setsid nohup node server.js --port 8123 --browserLaunchEnabled false < /dev/null &
    # 2. run:
    python3 tools/verification/verify_sillytavern_import.py \
        --base-url http://127.0.0.1:8123 \
        --sillytavern-dir /tmp/opencode/sillytavern-server/SillyTavern \
        app/build/c1p-group-export-hash/pipeline_3roles_2rounds_jsonl.txt \
        /tmp/opencode/c1-device-r7/phaseA-pull/c1-device-export-pipeline.jsonl

Exit code 0 iff every assertion passes.
"""

from __future__ import annotations

import argparse
import hashlib
import http.cookiejar
import json
import os
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

PINNED_COMMIT = "06bde939fb1e9c4c8d8641d810f0a916b5bce127"
EXPECTED_LICENSE_SHA256 = "8486a10c4393cee1c25392769ddd3b2d6c242d6ec7928e1414efff7dfb2f07ef"
GROUP_FIELD = "khatkit_group"
CHARACTER_NAMES_FIELD = "khatkit_character_names"

# Expected export fingerprints (recomputed and compared; recorded for provenance).
EXPECTED = {
    "pipeline_3roles_2rounds_jsonl.txt": dict(
        bytes=3615,
        sha256="36e6585f9aa4a028eb8277bc70578fd2c802cdd730e3981f0d52b02430f0c8b9",
        messages=8,
    ),
    "c1-device-export-pipeline.jsonl": dict(
        bytes=2938,
        sha256="4945b85426def123b7bc07382dbf03977c35908650a114ffac7cf782f9976f4b",
        messages=7,
    ),
}

ASSERTS: list[tuple[bool, str]] = []


def check(cond: bool, msg: str) -> None:
    ASSERTS.append((bool(cond), msg))
    print(("  PASS  " if cond else "  FAIL  ") + msg)


class Client:
    """Minimal cookie-aware HTTP client with CSRF handling."""

    def __init__(self, base_url: str) -> None:
        self.base = base_url.rstrip("/")
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.jar)
        )
        self.csrf = self._get_csrf()

    def _get_csrf(self) -> str:
        with self.opener.open(self.base + "/csrf-token", timeout=30) as r:
            return json.loads(r.read().decode("utf-8"))["token"]

    def _headers(self, content_type: str | None) -> dict:
        h = {"x-csrf-token": self.csrf}
        if content_type:
            h["content-type"] = content_type
        return h

    def post_json(self, path: str, body: dict) -> tuple[int, object]:
        req = urllib.request.Request(
            self.base + path,
            data=json.dumps(body).encode("utf-8"),
            headers=self._headers("application/json"),
            method="POST",
        )
        return self._send(req)

    def post_multipart(self, path: str, fields: dict, files: dict) -> tuple[int, object]:
        boundary = "----KhatKitVerify" + uuid.uuid4().hex
        parts = []
        for k, v in fields.items():
            parts.append(
                f"--{boundary}\r\n"
                f'Content-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode("utf-8")
            )
        for k, (filename, data) in files.items():
            parts.append(
                f"--{boundary}\r\n"
                f'Content-Disposition: form-data; name="{k}"; filename="{filename}"\r\n'
                f"Content-Type: application/octet-stream\r\n\r\n".encode("utf-8")
                + data
                + b"\r\n"
            )
        parts.append(f"--{boundary}--\r\n".encode("utf-8"))
        body = b"".join(parts)
        req = urllib.request.Request(
            self.base + path,
            data=body,
            headers=self._headers(f"multipart/form-data; boundary={boundary}"),
            method="POST",
        )
        return self._send(req)

    def _send(self, req: urllib.request.Request) -> tuple[int, object]:
        try:
            with self.opener.open(req, timeout=60) as r:
                raw = r.read()
                status = r.status
        except urllib.error.HTTPError as e:
            raw = e.read()
            status = e.code
        try:
            parsed = json.loads(raw.decode("utf-8"))
        except Exception:
            parsed = raw.decode("utf-8", "replace")
        return status, parsed


def verify_checkout(dir_path: str) -> None:
    print("[1] SillyTavern checkout")
    import subprocess

    def git(*args: str) -> str:
        return subprocess.run(
            ["git", "-C", dir_path, *args], capture_output=True, text=True
        ).stdout.strip()

    head = git("rev-parse", "HEAD")
    check(head == PINNED_COMMIT, f"checkout HEAD == pinned commit ({head})")
    license_path = Path(dir_path) / "LICENSE"
    check(license_path.is_file(), "LICENSE present")
    if license_path.is_file():
        digest = hashlib.sha256(license_path.read_bytes()).hexdigest()
        check(digest == EXPECTED_LICENSE_SHA256, f"LICENSE sha256 == {EXPECTED_LICENSE_SHA256[:16]}...")
        first = license_path.read_text(encoding="utf-8").splitlines()[0]
        check("AFFERO GENERAL PUBLIC LICENSE" in first, f"AGPL header line: {first.strip()!r}")


def verify_file(path: Path) -> dict:
    raw = path.read_bytes()
    sha = hashlib.sha256(raw).hexdigest()
    text = raw.decode("utf-8")
    lines = [json.loads(x) for x in text.split("\n") if x.strip()]
    exp = EXPECTED.get(path.name)
    print(f"[2] {path.name}: bytes={len(raw)} sha256={sha}")
    if exp:
        check(len(raw) == exp["bytes"], f"bytes == {exp['bytes']}")
        check(sha == exp["sha256"], "sha256 matches recorded fingerprint")
        check(len(lines) - 1 == exp["messages"], f"messages == {exp['messages']}")
    return {"path": path, "raw": raw, "lines": lines}


def expected_messages(lines: list[dict]) -> list[dict]:
    return [
        {"name": m.get("name"), "is_user": m.get("is_user"), "is_system": m.get("is_system")}
        for m in lines[1:]
    ]


def verify_import(client: Client, doc: dict) -> str:
    name = doc["path"].name
    print(f"[3] import/read {name}")
    status, resp = client.post_multipart(
        "/api/chats/group/import",
        {"file_type": "jsonl"},
        {"avatar": (name, doc["raw"])},
    )
    check(status == 200 and isinstance(resp, dict) and resp.get("res"), f"import HTTP {status} -> {resp}")
    chat_id = resp["res"]

    status, got = client.post_json("/api/chats/group/get", {"id": chat_id})
    check(status == 200, f"get HTTP {status}")
    check(isinstance(got, list), "get returned an array")
    check(len(got) == len(doc["lines"]), f"objects {len(got)} == source {len(doc['lines'])} (header + messages filtered by tryParse)")

    header = got[0]
    check(GROUP_FIELD in header and CHARACTER_NAMES_FIELD in header,
          "private header keys preserved by server (not rejected)")
    check(header.get("chat_metadata") == {"is_group": True}, "header chat_metadata is_group preserved")

    got_msgs = [
        {"name": m.get("name"), "is_user": m.get("is_user"), "is_system": m.get("is_system")}
        for m in got[1:]
    ]
    want_msgs = expected_messages(doc["lines"])
    check(got_msgs == want_msgs, "message order + name + is_user + is_system all match")

    status, info = client.post_json("/api/chats/group/info", {"id": chat_id})
    check(status == 200 and info.get("chat_items") == len(want_msgs),
          f"info chat_items == {len(want_msgs)} (header excluded)")
    return chat_id


def verify_registration(client: Client, chat_id: str) -> None:
    print("[4] group registration")
    _, before_all = client.post_json("/api/groups/all", {})
    check(isinstance(before_all, list), "groups/all returns a list")
    _, before_search = client.post_json("/api/chats/search", {"group_id": "wild", "avatar_url": "x.png"})
    check(before_search == [], "wild jsonl alone is NOT listed by search")
    _, before_recent = client.post_json("/api/chats/recent", {"max": 50})
    check(before_recent == [], "wild jsonl alone is NOT listed by recent")

    status, group = client.post_json(
        "/api/groups/create",
        {"name": "KhatKit Verify Group", "members": [], "chats": [chat_id], "chat_id": chat_id},
    )
    check(status == 200 and group.get("id"), f"groups/create HTTP {status}")
    gid = group["id"]

    _, after_all = client.post_json("/api/groups/all", {})
    match = [g for g in after_all if g.get("id") == gid]
    check(bool(match), "group appears in groups/all after registration")
    if match:
        check(match[0].get("chat_size", 0) > 0, f"registered group reports chat_size={match[0].get('chat_size')}")

    _, search = client.post_json("/api/chats/search", {"group_id": gid, "avatar_url": "x.png"})
    check(bool(search) and search[0].get("file_name") == chat_id,
          f"registered group's chat is listed ({search[0] if search else None})")


def verify_roundtrip(client: Client, doc: dict, chat_id: str) -> None:
    print(f"[5] open->save round-trip {doc['path'].name}")
    # Reproduce public/scripts/group-chats.js:
    #   getGroupChat: metadata = data[0].chat_metadata; add metadata.integrity if missing (:277)
    #   saveGroupChat: header = {chat_metadata:{...chat_metadata}, user_name:'unused',
    #                            character_name:'unused'}  (:632-636)
    _, got = client.post_json("/api/chats/group/get", {"id": chat_id})
    metadata = dict(got[0].get("chat_metadata") or {})
    metadata.setdefault("integrity", str(uuid.uuid4()))
    header = {"chat_metadata": metadata, "user_name": "unused", "character_name": "unused"}
    payload = {"id": chat_id, "chat": [header] + got[1:], "force": False}

    status, resp = client.post_json("/api/chats/group/save", payload)
    check(status == 200 and resp.get("ok") is True, f"group/save HTTP {status} -> {resp}")

    _, after = client.post_json("/api/chats/group/get", {"id": chat_id})
    h = after[0]
    check(GROUP_FIELD not in h and CHARACTER_NAMES_FIELD not in h,
          "private top-level header block DROPPED by open->save")
    check(h.get("user_name") == "unused" and h.get("character_name") == "unused",
          "header rebuilt from chat_metadata only (user_name/character_name = 'unused')")
    check(len(after) == len(got), "message objects preserved through save")

    # Consequence for KhatKit: TavernChatCodec.importGroup requires khatkit_group.
    # A header without it -> `document.header[GROUP_FIELD] ?: return null` (TavernChatCodec.kt:298)
    # -> resolveTavernGroupImport maps null to Unsupported, NOT NoConfig
    # (GroupTavernImport.kt:176-180). Pinned by GroupTavernImportTest.kt:217.
    check(GROUP_FIELD not in h, "re-saved file has no khatkit_group -> KhatKit importGroup == null -> Unsupported")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("files", nargs="+", help="KhatKit group-chat .jsonl exports")
    ap.add_argument("--base-url", default=os.environ.get("SILLYTAVERN_URL", "http://127.0.0.1:8123"))
    ap.add_argument("--sillytavern-dir", default="/tmp/opencode/sillytavern-server/SillyTavern")
    ap.add_argument("--skip-checkout-check", action="store_true")
    args = ap.parse_args()

    if not args.skip_checkout_check and Path(args.sillytavern_dir).is_dir():
        verify_checkout(args.sillytavern_dir)
    else:
        print("[1] checkout check skipped")

    client = Client(args.base_url)
    with client.opener.open(client.base + "/version", timeout=30) as r:
        version = json.loads(r.read().decode("utf-8"))
    check(version.get("gitRevision", "").startswith("06bde93"),
          f"running server reports gitRevision {version.get('gitRevision')} (pkg {version.get('pkgVersion')})")

    docs = [verify_file(Path(f)) for f in args.files]

    chat_ids = {d["path"].name: verify_import(client, d) for d in docs}

    first = docs[0]
    verify_registration(client, chat_ids[first["path"].name])
    verify_roundtrip(client, first, chat_ids[first["path"].name])

    passed = sum(1 for ok, _ in ASSERTS if ok)
    failed = [m for ok, m in ASSERTS if not ok]
    print(f"\n=== {passed}/{len(ASSERTS)} assertions passed ===")
    if failed:
        for m in failed:
            print("  FAILED: " + m)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
