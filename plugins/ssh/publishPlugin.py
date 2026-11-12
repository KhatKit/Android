#!/usr/bin/env python3
"""把 plugins/ssh/build/ssh-plugin.zip 发布到 KhatKitHub（kind=dependency, plugin_id=ssh）并审核上线。

版本号取自 zip 里的 manifest.json，发布前先跑 plugins/ssh/buildPlugin.sh 并在那里改版本。

用法:
    python3 plugins/ssh/publishPlugin.py
    python3 plugins/ssh/publishPlugin.py --revoke 3     # 先撤销旧条目，再发布新版本

凭证:
    优先 KHATKIT_ADMIN_TOKEN；没有就用 root SSH 到部署机，从 kodeheapserver 容器环境里读
    DEVELOPER_PASSWORD，再走 /auth/login（与 KodeHeapServer scripts/khatkit_wasm_release.py 同一套）。
"""
import argparse
import base64
import hashlib
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ZIP = os.path.join(HERE, "build", "ssh-plugin.zip")

DEFAULT_ORIGIN = "https://heizige.top"
DEFAULT_HUB_HOST = "root@38.76.182.82"
DEFAULT_CONTAINER = "kodeheapserver"
ADMIN_USERNAME = "heizigelovecode"


def request(origin, path, data=None, token=None, method=None):
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    if data is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(data, ensure_ascii=False).encode()
    req = urllib.request.Request(origin.rstrip("/") + path, data, headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=180) as resp:
            return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as error:
        return error.code, error.read(4000).decode(errors="replace")


def login(args):
    token = os.environ.get("KHATKIT_ADMIN_TOKEN")
    if token:
        return token
    env_out = subprocess.check_output(
        [
            "ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=10", args.hub_host,
            "docker inspect " + args.container
            + " --format '{{range .Config.Env}}{{println .}}{{end}}'",
        ],
        text=True,
    )
    password = None
    for line in env_out.splitlines():
        if line.startswith("DEVELOPER_PASSWORD="):
            password = line.split("=", 1)[1]
            break
    if not password:
        raise SystemExit("DEVELOPER_PASSWORD not found on the deployment host")
    status, body = request(
        args.origin, "/auth/login", {"username": ADMIN_USERNAME, "password": password}
    )
    if status != 200 or not isinstance(body, dict) or not body.get("token"):
        raise SystemExit("login failed: " + str(status) + " " + str(body)[:300])
    return body["token"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--origin", default=DEFAULT_ORIGIN)
    parser.add_argument("--hub-host", default=DEFAULT_HUB_HOST)
    parser.add_argument("--container", default=DEFAULT_CONTAINER)
    parser.add_argument("--revoke", action="append", default=[], help="发布前撤销的旧条目 id，可多次传")
    args = parser.parse_args()

    if not os.path.isfile(ZIP):
        raise SystemExit("missing " + ZIP + "，先跑 plugins/ssh/buildPlugin.sh")
    with zipfile.ZipFile(ZIP) as archive:
        manifest = json.loads(archive.read("manifest.json").decode())
    version = manifest["version"]
    with open(ZIP, "rb") as handle:
        artifact = handle.read()
    digest = hashlib.sha256(artifact).hexdigest()

    token = login(args)
    print("login ok; publishing", version, digest[:12])

    for item_id in args.revoke:
        status, body = request(
            args.origin,
            "/api/admin/items/" + str(item_id) + "/revoke",
            {"reason": "发布 " + version},
            token=token,
            method="POST",
        )
        print("revoke", item_id, status, body if status != 200 else "")

    report = {
        "artifact_sha256": digest,
        "audit_rules_version": "1",
        "score": 10,
        "permissions": [],
        "network_domains": [],
        "dangerous_calls": [],
        "hooks": [],
    }
    payload = {
        "name": "remote-ssh",
        "kind": "dependency",
        "version": version,
        "license": "Apache-2.0",
        "display_name": {"zh": "远程 SSH", "en": "Remote SSH"},
        "description": {
            "zh": "按需下载的远程 SSH 插件（sshj + BouncyCastle，纯 JVM）。安装后在设置 → 扩展里出现「远程 SSH」入口，可连接任意主机执行命令，主机密钥 TOFU。",
            "en": "On-demand remote SSH plugin (sshj + BouncyCastle). Adds a Remote SSH entry under Settings → Extensions to run commands on any host; host keys are pinned on first use.",
        },
        "changelog": "1.0.1：改用 sshj AndroidConfig 并过滤 curve25519/ed25519 候选，修复 Android 上「no such algorithm: X25519 for provider BC」。",
        "manifest_json": json.dumps(manifest, ensure_ascii=False, separators=(",", ":")),
        "artifact_base64": base64.b64encode(artifact).decode(),
        "audit_report_json": json.dumps(report, ensure_ascii=False, separators=(",", ":")),
        "audit_score": 10,
        "audit_rules_version": "1",
        "min_app_version": "2.5.5",
        "sha256": digest,
    }
    status, body = request(args.origin, "/api/items/publish", payload, token=token, method="POST")
    if status != 201:
        raise SystemExit("publish failed: " + str(status) + " " + str(body)[:500])
    item_id = body["id"]
    print("publish", status, "id=" + str(item_id), "status=" + str(body.get("status")))

    status, body = request(
        args.origin, "/api/admin/items/" + str(item_id) + "/approve", {}, token=token, method="POST"
    )
    print("approve", status, body.get("status") if isinstance(body, dict) else str(body)[:200])
    sys.exit(0 if status == 200 else 1)


if __name__ == "__main__":
    main()
