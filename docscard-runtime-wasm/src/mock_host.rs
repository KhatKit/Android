//! Mock 宿主（CLI --mock-host 与集成测试共用）。
//!
//! 键是 host_call 请求里的 method（<bridge>.<方法名>），值原样作为应答 JSON。
//! {"__raw": "..."} 表示“该方法返回值本身是一个 JSON 字符串”（如 browser.act、
//! accessibility.findImage），此时把内层字符串编码成 JSON string 应答。
//! __default 是未知方法的兜底应答；没有 __default 时未知方法返回
//! HOST_ERR_HOST_FAILURE（对应 ABI v1 §2.1 的失败码 5）。

use std::collections::HashMap;

use crate::host::{CardHost, HostError};

/// 预置应答宿主。
#[derive(Debug, Clone, Default)]
pub struct MockHost {
    replies: HashMap<String, serde_json::Value>,
    fallback: Option<serde_json::Value>,
    calls: Vec<MockCall>,
}

/// 一次 host_call 记录（capability + method + args），用于断言卡片调用了什么。
#[derive(Debug, Clone, PartialEq)]
pub struct MockCall {
    pub capability: String,
    pub method: String,
    pub args: serde_json::Value,
}

impl MockHost {
    /// 从 mock JSON 文本构造。
    pub fn from_json(text: &str) -> Result<Self, String> {
        let value: serde_json::Value = serde_json::from_str(text)
            .map_err(|err| format!("mock host is not valid JSON: {err}"))?;
        Self::from_value(value)
    }

    /// 从已解析的 mock JSON 构造。
    pub fn from_value(value: serde_json::Value) -> Result<Self, String> {
        let serde_json::Value::Object(mut map) = value else {
            return Err("mock host must be a JSON object".to_string());
        };
        let fallback = map.remove("__default");
        Ok(Self {
            replies: map.into_iter().collect(),
            fallback,
            calls: Vec::new(),
        })
    }

    /// 从文件构造。
    pub fn from_file(path: &str) -> Result<Self, String> {
        let text = std::fs::read_to_string(path)
            .map_err(|err| format!("cannot read mock host {path}: {err}"))?;
        Self::from_json(&text)
    }

    /// 已发生的 host_call 记录（按调用顺序）。
    pub fn calls(&self) -> &[MockCall] {
        &self.calls
    }

    fn reply_json(value: &serde_json::Value) -> String {
        if let Some(raw) = value.get("__raw").and_then(|v| v.as_str()) {
            return serde_json::Value::String(raw.to_string()).to_string();
        }
        value.to_string()
    }
}

impl CardHost for MockHost {
    fn host_call(&mut self, cap: &str, request_json: &str) -> Result<String, HostError> {
        let request: serde_json::Value = serde_json::from_str(request_json)
            .map_err(|err| HostError::InvalidRequest(format!("{err}: {request_json}")))?;
        let method = request
            .get("method")
            .and_then(|value| value.as_str())
            .unwrap_or_default()
            .to_string();
        let args = request
            .get("args")
            .cloned()
            .unwrap_or(serde_json::Value::Null);
        self.calls.push(MockCall {
            capability: cap.to_string(),
            method: method.clone(),
            args,
        });
        if let Some(reply) = self.replies.get(&method) {
            return Ok(Self::reply_json(reply));
        }
        match &self.fallback {
            Some(reply) => Ok(Self::reply_json(reply)),
            None => Err(HostError::Unsupported(format!(
                "mock host has no reply for {method} (cap {cap})"
            ))),
        }
    }

    fn log(&mut self, _level: i32, _message: &str) {}
}
