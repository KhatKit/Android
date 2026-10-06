#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Slow / failure-injecting OpenAI-compatible mock for the C1-06 cancel evidence.

Why a new server (not mock_openai_v2.py on 8765):
  * v2 streams the whole reply in a few microseconds (no sleep between chunks).
    A cancel test that waits for the first committed role and then calls
    stopGeneration would race against "b already finished".
  * This server lets role B stream long enough (default 0.5 s between content
    chunks, first chunk immediate) that the cancel demonstrably lands **while
    a response is still streaming**.
  * It can also fail the **first** request of a chosen role with HTTP 500, so
    the test gets a genuine production error node (round 1 FAILED/role_failed)
    before the cancel round — proving "generated messages / error nodes are
    kept" is about preservation, not fabrication.

Environment variables (all optional):
  MOCK_PORT       listen port (default 8766)
  MOCK_DELAYS     per-role seconds between content chunks, e.g. "A:0.02,B:0.5,C:0.1"
                  (first content chunk is always immediate; default "A:0.02,B:0.5,C:0.1")
  MOCK_FAIL_ROLE  role code whose FIRST request returns HTTP 500 (default "B", empty to disable)
  MOCK_PAD        extra padding characters appended to the reply per role, e.g. "B:400"
  MOCK_LOG_DIR    directory for requests.jsonl (default /tmp/opencode/c1-cancel)

Wire shape is copied from mock_openai_v2.py (proven against the real app):
role-only opener, 24-byte content chunks with flush, finish_reason chunk,
usage-only trailer, data: [DONE], Connection: close.
"""

import json
import os
import re
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("MOCK_PORT", "8766"))
LOG_DIR = os.environ.get("MOCK_LOG_DIR", "/tmp/opencode/c1-cancel")
LOG_PATH = os.path.join(LOG_DIR, "requests.jsonl")
FAIL_ROLE = os.environ.get("MOCK_FAIL_ROLE", "B").strip().upper()

RE_CASE = re.compile(r"CASE:([A-Za-z0-9_\-]+)")
RE_ROLE = re.compile(r"ROLECODE:([A-Za-z0-9_\-]+)")


def parse_delays():
    raw = os.environ.get("MOCK_DELAYS", "A:0.02,B:0.5,C:0.1")
    out = {"A": 0.02, "B": 0.5, "C": 0.1}
    for item in raw.split(","):
        item = item.strip()
        if not item:
            continue
        k, _, v = item.partition(":")
        try:
            out[k.strip().upper()] = float(v)
        except ValueError:
            pass
    return out


DELAYS = parse_delays()


def parse_pads():
    raw = os.environ.get("MOCK_PAD", "B:400")
    out = {"B": 400}
    for item in raw.split(","):
        item = item.strip()
        if not item:
            continue
        k, _, v = item.partition(":")
        try:
            out[k.strip().upper()] = int(v)
        except ValueError:
            pass
    return out


PADS = parse_pads()

write_lock = threading.Lock()
failed_once = set()
seq_counter = 0


def write_record(record):
    os.makedirs(LOG_DIR, exist_ok=True)
    with write_lock:
        with open(LOG_PATH, "a", encoding="utf-8") as fh:
            fh.write(json.dumps(record, ensure_ascii=False) + "\n")


def extract_speaker(body_obj):
    """Speaker = the ROLECODE in the (last) system message; case likewise."""
    messages = body_obj.get("messages") or []
    speaker = None
    case = None
    for msg in messages:
        if msg.get("role") != "system":
            continue
        content = msg.get("content")
        if not isinstance(content, str):
            continue
        m = RE_ROLE.search(content)
        if m:
            speaker = m.group(1).upper()
        c = RE_CASE.search(content)
        if c:
            case = c.group(1)
    if speaker is None:
        # fall back: scan everything
        blob = json.dumps(body_obj, ensure_ascii=False)
        m = RE_ROLE.search(blob)
        speaker = m.group(1).upper() if m else "?"
        c = RE_CASE.search(blob)
        case = c.group(1) if c else "?"
    return speaker, (case or "?")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        pass

    def _json(self, code, payload):
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(raw)
        self.close_connection = True

    def do_POST(self):
        global seq_counter
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length else b""
        try:
            body = json.loads(raw.decode("utf-8"))
        except Exception:
            body = {"_unparsed": raw.decode("utf-8", "replace")}

        speaker, case = extract_speaker(body)
        requested_model = body.get("model", "?") if isinstance(body, dict) else "?"
        stream = bool(body.get("stream", False)) if isinstance(body, dict) else False

        with write_lock:
            seq_counter += 1
            seq = seq_counter

        injected_fail = False
        if FAIL_ROLE and speaker == FAIL_ROLE and speaker not in failed_once:
            failed_once.add(speaker)
            injected_fail = True
            record = {
                "seq": seq,
                "ts": time.time(),
                "speaker": speaker,
                "case": case,
                "model": requested_model,
                "stream": stream,
                "injected_http_500": True,
                "raw_body_bytes": len(raw),
            }
            write_record(record)
            sys.stderr.write("[mock-slow] seq=%d speaker=%s INJECTED HTTP 500\n" % (seq, speaker))
            sys.stderr.flush()
            self._json(500, {"error": {
                "message": "c1-cancel mock injected 500 for role %s (first request)" % speaker,
                "type": "server_error",
                "code": "mock_injected_failure",
            }})
            return

        pad = "补" * PADS.get(speaker, 0)
        reply_text = ("[mock-slow] CASE:%s ROLECODE:%s 第%d次请求，正在流式产出用于取消用例。%s"
                      % (case, speaker, seq, pad))
        completion_bytes = len(reply_text.encode("utf-8"))
        prompt_tokens = max(1, len(raw) // 4)
        completion_tokens = max(1, completion_bytes // 4)
        usage = {
            "prompt_tokens": prompt_tokens,
            "completion_tokens": completion_tokens,
            "total_tokens": prompt_tokens + completion_tokens,
        }
        record = {
            "seq": seq,
            "ts": time.time(),
            "speaker": speaker,
            "case": case,
            "model": requested_model,
            "stream": stream,
            "injected_http_500": False,
            "raw_body_bytes": len(raw),
            "reply_bytes": completion_bytes,
            "usage": usage,
            "delay_per_chunk": DELAYS.get(speaker, 0.1),
        }
        write_record(record)
        sys.stderr.write("[mock-slow] seq=%d speaker=%s stream=%s usage=%s\n"
                         % (seq, speaker, stream, usage))
        sys.stderr.flush()

        response_id = "chatcmpl-mockslow-%d" % seq
        if stream:
            self._respond_stream(response_id, requested_model, reply_text, usage, speaker)
        else:
            self._json(200, {
                "id": response_id,
                "object": "chat.completion",
                "created": int(time.time()),
                "model": requested_model,
                "choices": [{
                    "index": 0,
                    "message": {"role": "assistant", "content": reply_text},
                    "logprobs": None,
                    "finish_reason": "stop",
                }],
                "usage": usage,
            })

    def _respond_stream(self, response_id, model, text, usage, speaker):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()

        def emit(payload):
            chunk = "data: %s\n\n" % json.dumps(payload, ensure_ascii=False)
            self.wfile.write(chunk.encode("utf-8"))
            self.wfile.flush()

        emit({
            "id": response_id, "object": "chat.completion.chunk",
            "created": int(time.time()), "model": model,
            "choices": [{"index": 0, "delta": {"role": "assistant"}, "finish_reason": None}],
        })

        delay = DELAYS.get(speaker, 0.1)
        pieces = [text[i:i + 24] for i in range(0, len(text), 24)] or [""]
        for i, piece in enumerate(pieces):
            if i > 0 and delay > 0:
                time.sleep(delay)
            emit({
                "id": response_id, "object": "chat.completion.chunk",
                "created": int(time.time()), "model": model,
                "choices": [{"index": 0, "delta": {"content": piece}, "finish_reason": None}],
            })

        emit({
            "id": response_id, "object": "chat.completion.chunk",
            "created": int(time.time()), "model": model,
            "choices": [{"index": 0, "delta": {}, "finish_reason": "stop"}],
        })
        emit({
            "id": response_id, "object": "chat.completion.chunk",
            "created": int(time.time()), "model": model,
            "choices": [],
            "usage": usage,
        })
        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()
        self.close_connection = True


def main():
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    server.daemon_threads = True
    sys.stderr.write("[mock-slow] listening on 0.0.0.0:%d log=%s fail_role=%s delays=%s\n"
                     % (PORT, LOG_PATH, FAIL_ROLE, DELAYS))
    sys.stderr.flush()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        sys.stderr.write("[mock-slow] bye\n")


if __name__ == "__main__":
    main()
