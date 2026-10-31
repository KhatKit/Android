//! 运行时错误类型。

use std::fmt;

use crate::host::HostError;
use crate::manifest::ManifestError;

/// `CardRuntime` 的所有可恢复错误。任何一条都不得 panic。
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum RuntimeError {
    /// manifest 校验失败。
    Manifest(ManifestError),
    /// wasm 字节码解析/校验失败。
    ModuleLoad(String),
    /// 实例化或 start 函数失败。
    Instantiate(String),
    /// linker 注册失败（理论上不会发生）。
    Link(String),
    /// 缺少必需的 export（明确报名字，不 panic）。
    MissingExport { name: &'static str },
    /// export 的签名与 ABI v1 不符。
    SignatureMismatch { name: &'static str, detail: String },
    /// 指针/长度越出线性内存。
    MemoryOutOfBounds { ptr: u32, len: u32, memory_len: usize },
    /// `kk_alloc` 返回 0（分配失败）。
    AllocFailed { size: u32 },
    /// 宿主输入（事件 JSON）超过 `limits.output_bytes`。
    InputTooLarge { len: usize, limit: usize },
    /// 事件结果 / 宿主回传内容超过 `limits.output_bytes`。
    OutputTooLarge { len: usize, limit: usize },
    /// fuel 耗尽（等价于 `limits.timeout_ms` 超时）。
    Timeout { timeout_ms: u64 },
    /// 卡片主动调用 `kk.abort(code)`。
    Aborted { code: i32 },
    /// 卡片导出函数以非 0 错误码失败；detail 为 `kk_last_error()` 的 JSON。
    CardError { code: i32, export: &'static str, detail: Option<String> },
    /// 卡片返回的结果不是合法 UTF-8 / 合法 JSON。
    InvalidResult { export: &'static str, detail: String },
    /// guest 执行 trap（越界、unreachable 等）。
    Trap { export: &'static str, message: String },
}

impl fmt::Display for RuntimeError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Manifest(err) => write!(f, "manifest rejected: {err}"),
            Self::ModuleLoad(detail) => write!(f, "card.wasm could not be loaded: {detail}"),
            Self::Instantiate(detail) => write!(f, "card.wasm could not be instantiated: {detail}"),
            Self::Link(detail) => write!(f, "host imports could not be registered: {detail}"),
            Self::MissingExport { name } => {
                write!(f, "card is missing required export `{name}`")
            }
            Self::SignatureMismatch { name, detail } => {
                write!(f, "export `{name}` does not match ABI v1: {detail}")
            }
            Self::MemoryOutOfBounds { ptr, len, memory_len } => write!(
                f,
                "guest memory range ptr={ptr} len={len} exceeds linear memory ({memory_len} bytes)"
            ),
            Self::AllocFailed { size } => write!(f, "kk_alloc({size}) returned 0"),
            Self::InputTooLarge { len, limit } => {
                write!(f, "input JSON is {len} bytes, limit (output_bytes) is {limit}")
            }
            Self::OutputTooLarge { len, limit } => {
                write!(f, "output JSON is {len} bytes, limit (output_bytes) is {limit}")
            }
            Self::Timeout { timeout_ms } => {
                write!(f, "card exhausted its fuel budget, timeout_ms={timeout_ms}")
            }
            Self::Aborted { code } => write!(f, "card called kk.abort({code})"),
            Self::CardError { code, export, detail } => match detail {
                Some(detail) => write!(f, "`{export}` returned error code {code}: {detail}"),
                None => write!(f, "`{export}` returned error code {code}"),
            },
            Self::InvalidResult { export, detail } => {
                write!(f, "`{export}` returned an invalid result: {detail}")
            }
            Self::Trap { export, message } => {
                write!(f, "`{export}` trapped: {message}")
            }
        }
    }
}

impl std::error::Error for RuntimeError {}

impl From<ManifestError> for RuntimeError {
    fn from(err: ManifestError) -> Self {
        Self::Manifest(err)
    }
}

impl From<HostError> for RuntimeError {
    fn from(err: HostError) -> Self {
        Self::InvalidResult {
            export: "host",
            detail: err.to_string(),
        }
    }
}

impl RuntimeError {
    /// 便于测试/CLI 判断错误类别。
    pub fn kind(&self) -> &'static str {
        match self {
            Self::Manifest(_) => "manifest",
            Self::ModuleLoad(_) => "module_load",
            Self::Instantiate(_) => "instantiate",
            Self::Link(_) => "link",
            Self::MissingExport { .. } => "missing_export",
            Self::SignatureMismatch { .. } => "signature_mismatch",
            Self::MemoryOutOfBounds { .. } => "memory_out_of_bounds",
            Self::AllocFailed { .. } => "alloc_failed",
            Self::InputTooLarge { .. } => "input_too_large",
            Self::OutputTooLarge { .. } => "output_too_large",
            Self::Timeout { .. } => "timeout",
            Self::Aborted { .. } => "aborted",
            Self::CardError { .. } => "card_error",
            Self::InvalidResult { .. } => "invalid_result",
            Self::Trap { .. } => "trap",
        }
    }
}
