#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""探测 OpenAI 兼容网关在响应里**自报**的模型名（wire 级模型名）。

背景：验收「实际模型调用序列」时，如果模型名是拿本地 modelId（配置 UUID）
回查本地 provider 模型表反查出来的，那不是 wire 级证据。真正的 wire 级模型名
是网关响应体 / SSE 帧顶层 `model` 字段里的那个字符串。本脚本把它抓出来。

只依赖 Python 3 标准库（urllib / json / argparse），不引入新依赖。

API key 不写在本文件里，也不接受命令行以外的落盘来源：
    --api-key KEY
    --api-key "$KEY"        # 由执行者自行从源码/环境取出
    C1_GATEWAY_API_KEY=... python3 c1_wire_model_probe.py ...
网关不通时如实打印 http_status 与 error，绝不编造 wire_model。

用法示例：
    python3 tools/verification/c1_wire_model_probe.py \
        --base-url https://api.zenneko.top/v1 \
        --api-key "$KEY" \
        --model deepseek-v4-flash --model glm-5.2
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request

# 说明文字里刻意不出现任何真实 key；这里只是告诉使用者可以从哪里取。
KEY_HELP = (
    "网关 API key。建议用环境变量注入，避免进入 shell history 或进程列表："
    "export C1_GATEWAY_API_KEY=... ；未提供时回退读 C1_GATEWAY_API_KEY / OPENAI_API_KEY。"
)

# reasoning 型模型 max_tokens 给小了会 finish_reason=length、content 空、
# reasoning_tokens 吃掉配额，所以默认给足。
DEFAULT_MAX_TOKENS = 2048
DEFAULT_TIMEOUT = 120

PROMPT = "用一句话说明什么是 HTTP 状态码 200。"


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description="抓取 OpenAI 兼容网关自报的 wire 模型名（响应体 / SSE 顶层 model 字段）。",
    )
    parser.add_argument("--base-url", required=True, help="网关 base url，例如 https://host/v1")
    parser.add_argument("--model", required=True, action="append",
                        help="要请求的模型名，可重复传入，按传入顺序逐个探测")
    parser.add_argument("--api-key", default=None,
                        help=KEY_HELP)
    parser.add_argument("--max-tokens", type=int, default=DEFAULT_MAX_TOKENS,
                        help=f"每次请求的 max_tokens，默认 {DEFAULT_MAX_TOKENS}（reasoning 模型需给足）")
    parser.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT,
                        help=f"单次请求超时秒数，默认 {DEFAULT_TIMEOUT}")
    parser.add_argument("--no-stream", action="store_true",
                        help="改用非流式请求（stream=false），默认流式")
    parser.add_argument("--prompt", default=PROMPT, help="最小探测 prompt")
    return parser.parse_args(argv)


def resolve_api_key(explicit):
    """key 只能来自命令行或环境变量；绝不来自本文件内的硬编码。"""
    if explicit:
        return explicit
    for name in ("C1_GATEWAY_API_KEY", "OPENAI_API_KEY"):
        value = os.environ.get(name)
        if value:
            return value
    return None


def build_payload(model, max_tokens, stream, prompt):
    return {
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
        "max_tokens": max_tokens,
        "stream": stream,
        # 让网关在流的最后一帧带上 usage；不支持时下面会容错。
        "stream_options": {"include_usage": True},
    }


def open_request(url, payload, api_key, timeout):
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(url, data=body, method="POST")
    request.add_header("Content-Type", "application/json")
    request.add_header("Accept", "text/event-stream" if payload["stream"] else "application/json")
    request.add_header("Authorization", "Bearer " + api_key)
    return urllib.request.urlopen(request, timeout=timeout)


def parse_sse_line(line):
    """把一行 SSE 字节变成事件；非 data 行返回 None。"""
    if not line.startswith("data:"):
        return None
    return line[len("data:"):].lstrip(" ")


def scan_stream(lines):
    """逐行扫 SSE，把顶层 model、usage、finish_reason 收集起来。

    wire 模型名取「最后一个出现过的顶层 model」：真实网关会在每个 chunk 上重复
    带上同一个 model，取最后一次等价且对「末帧才带 model」的网关更稳。
    """
    wire_model = None
    usage = None
    finish_reason = None
    saw_done = False
    chunk_count = 0
    text_chars = 0
    reasoning_chars = 0

    for raw in lines:
        line = raw.decode("utf-8", errors="replace").rstrip("\r\n")
        data = parse_sse_line(line)
        if data is None:
            continue
        if data == "[DONE]":
            saw_done = True
            break
        try:
            payload = json.loads(data)
        except json.JSONDecodeError:
            # 网关偶发的心跳/注释行，跳过但不算失败。
            continue
        if not isinstance(payload, dict):
            continue
        chunk_count += 1

        if isinstance(payload.get("model"), str) and payload["model"]:
            wire_model = payload["model"]
        if isinstance(payload.get("usage"), dict):
            usage = payload["usage"]
        for choice in payload.get("choices") or []:
            if not isinstance(choice, dict):
                continue
            if isinstance(choice.get("finish_reason"), str):
                finish_reason = choice["finish_reason"]
            delta = choice.get("delta")
            if isinstance(delta, dict):
                if isinstance(delta.get("content"), str):
                    text_chars += len(delta["content"])
                for key in ("reasoning_content", "reasoning"):
                    if isinstance(delta.get(key), str):
                        reasoning_chars += len(delta[key])

    return {
        "wire_model": wire_model,
        "usage": usage,
        "finish_reason": finish_reason,
        "saw_done": saw_done,
        "chunks": chunk_count,
        "content_chars": text_chars,
        "reasoning_chars": reasoning_chars,
    }


def scan_non_stream(body_bytes):
    payload = json.loads(body_bytes.decode("utf-8", errors="replace"))
    model = payload.get("model") if isinstance(payload, dict) else None
    finish_reason = None
    text_chars = 0
    reasoning_chars = 0
    if isinstance(payload, dict):
        for choice in payload.get("choices") or []:
            if not isinstance(choice, dict):
                continue
            if isinstance(choice.get("finish_reason"), str):
                finish_reason = choice["finish_reason"]
            message = choice.get("message")
            if isinstance(message, dict):
                if isinstance(message.get("content"), str):
                    text_chars += len(message["content"])
                for key in ("reasoning_content", "reasoning"):
                    if isinstance(message.get(key), str):
                        reasoning_chars += len(message[key])
    return {
        "wire_model": model if isinstance(model, str) and model else None,
        "usage": payload.get("usage") if isinstance(payload, dict) else None,
        "finish_reason": finish_reason,
        "content_chars": text_chars,
        "reasoning_chars": reasoning_chars,
    }


def probe(base_url, model, api_key, max_tokens, timeout, stream):
    url = base_url.rstrip("/") + "/chat/completions"
    payload = build_payload(model, max_tokens, stream, PROMPT)
    result = {"requested": model, "wire_model": None, "http_status": None, "usage": None}
    try:
        with open_request(url, payload, api_key, timeout) as response:
            result["http_status"] = getattr(response, "status", None) or response.getcode()
            if stream:
                scanned = scan_stream(response)
            else:
                scanned = scan_non_stream(response.read())
                scanned["saw_done"] = True
    except urllib.error.HTTPError as exc:
        result["http_status"] = exc.code
        result["error"] = exc.reason if isinstance(exc.reason, str) else "HTTPError"
        try:
            detail = exc.read().decode("utf-8", errors="replace")
        except Exception:  # pragma: no cover - 读 body 失败不应盖掉主错误
            detail = ""
        if detail:
            result["error_detail"] = detail[:600]
        result["ok"] = False
        return result
    except urllib.error.URLError as exc:
        result["error"] = "URLError: %s" % (exc.reason,)
        result["ok"] = False
        return result
    except Exception as exc:  # 超时、SSE 半途断开等
        result["error"] = "%s: %s" % (type(exc).__name__, exc)
        result["ok"] = False
        return result

    result.update(scanned)
    # wire_model 为 null 就是「没拿到」，明确标红，不做任何猜测填充。
    result["ok"] = result["wire_model"] is not None
    return result


def main(argv=None):
    args = parse_args(argv)
    api_key = resolve_api_key(args.api_key)
    if not api_key:
        print(json.dumps({
            "error": "missing_api_key",
            "detail": KEY_HELP,
        }, ensure_ascii=False, indent=2))
        return 2

    stream = not args.no_stream
    all_ok = True
    for model in args.model:
        record = probe(
            base_url=args.base_url,
            model=model,
            api_key=api_key,
            max_tokens=args.max_tokens,
            timeout=args.timeout,
            stream=stream,
        )
        if not record.get("ok"):
            all_ok = False
        print(json.dumps(record, ensure_ascii=False))
        sys.stdout.flush()

    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())