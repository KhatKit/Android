//! 宿主能力抽象。引擎只依赖 `CardHost` trait，CLI/测试各自注入实现。

use std::fmt;

use crate::abi;

/// 宿主能力调用失败。`code()` 会作为 `kk.host_call` 的负返回值回给卡片。
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum HostError {
    /// 能力被拒绝（manifest 未声明 / 宿主策略不允许）。
    Denied(String),
    /// 能力存在但当前宿主不支持（例如 CLI 的 DefaultHost）。
    Unsupported(String),
    /// 能力实现失败。
    Failed(String),
    /// 请求 JSON 非法。
    InvalidRequest(String),
}

impl HostError {
    /// 映射到 ABI v1 的 `HOST_ERR_*` 错误码（恒 > 0）。
    pub fn code(&self) -> i32 {
        match self {
            Self::Denied(_) => abi::HOST_ERR_CAPABILITY_DENIED,
            Self::Unsupported(_) | Self::Failed(_) => abi::HOST_ERR_HOST_FAILURE,
            Self::InvalidRequest(_) => abi::HOST_ERR_INVALID_REQUEST,
        }
    }

    /// `kk.host_call` 的负返回值。
    pub fn encoded(&self) -> i64 {
        abi::encode_error(self.code())
    }
}

impl fmt::Display for HostError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Denied(message) => write!(f, "capability denied: {message}"),
            Self::Unsupported(message) => write!(f, "capability unsupported: {message}"),
            Self::Failed(message) => write!(f, "capability failed: {message}"),
            Self::InvalidRequest(message) => write!(f, "invalid request: {message}"),
        }
    }
}

impl std::error::Error for HostError {}

/// 卡片可用的宿主能力入口。
///
/// `cap` 是 manifest.capabilities 里声明的能力字符串（如 `"ai:chat"`、`"net:api.example.com"`），
/// 引擎已保证它出现在白名单内；`request_json` 是卡片给的请求 JSON；
/// 返回的 JSON 字符串会被写入 guest 线性内存，长度受 `limits.output_bytes` 约束。
pub trait CardHost {
    fn host_call(&mut self, cap: &str, request_json: &str) -> Result<String, HostError>;
    fn log(&mut self, level: i32, message: &str);
}

/// CLI 与测试用的默认宿主：无网络、无 fs，任何 `host_call` 都返回 denied。
#[derive(Debug, Default, Clone)]
pub struct DefaultHost {
    logs: Vec<(i32, String)>,
}

impl DefaultHost {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn logs(&self) -> &[(i32, String)] {
        &self.logs
    }
}

impl CardHost for DefaultHost {
    fn host_call(&mut self, cap: &str, _request_json: &str) -> Result<String, HostError> {
        Err(HostError::Denied(format!(
            "`{cap}` is not provided by the default host (no network, no fs)"
        )))
    }

    fn log(&mut self, level: i32, message: &str) {
        if self.logs.len() < 256 {
            self.logs.push((level, message.to_string()));
        }
    }
}
