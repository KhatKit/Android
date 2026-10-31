//! ABI v1 常量与编解码。
//!
//! ## 冻结的内存交换约定（ABI v1）
//!
//! Kotlin（宿主）与卡片（wasm guest）之间**只**经由线性内存 + JSON 字符串交换，
//! 任何副作用都必须走 `kk.host_call`（受 manifest.capabilities 白名单约束）。
//!
//! - 宿主写入 guest 内存：`ptr = kk_alloc(len)` → 写入 UTF-8 JSON → 调用入口。
//! - guest 返回：`i64`，编码为 `(ptr << 32) | len`，内容是 UTF-8 JSON。
//!   宿主读完结果后 **必须** 调用 `kk_free(ptr, len)`。
//! - 错误码：`0` 成功；非 0 时宿主读 `kk_last_error()` 的 JSON
//!   （同一个 `(ptr << 32) | len` 编码；该缓冲区归 guest 所有，宿主不 free）。
//! - `kk.host_call` 失败时返回**负值** `-(code)`（见 `HOST_ERR_*`）；
//!   成功时返回 `(ptr << 32) | len` 的应答 JSON，guest 读完自行 `kk_free`。
//! - `kk_handle` 返回 `0` 或负值 `-(code)` 都表示失败，宿主读 `kk_last_error()` 的 JSON；
//!   只有正值才是 `(ptr << 32) | len` 的结果 JSON。
//!
//! 因为 `limits.memory_mb` 上限为 1024（1 GiB），指针恒 < 2^31，
//! 因此 `(ptr << 32) | len` 永远非负，负值只用于错误码，编码无歧义。

/// ABI 版本；manifest 的 `abi_version` 必须等于该值。
pub const ABI_VERSION: u32 = 1;

/// imports 的 wasm 模块名。
pub const IMPORT_MODULE: &str = "kk";

pub const IMPORT_HOST_CALL: &str = "host_call";
pub const IMPORT_LOG: &str = "log";
pub const IMPORT_RAND: &str = "rand";
pub const IMPORT_NOW_MS: &str = "now_ms";
pub const IMPORT_ABORT: &str = "abort";

/// guest 必须导出的线性内存。
pub const MEMORY_EXPORT: &str = "memory";

pub const EXPORT_INIT: &str = "kk_init";
pub const EXPORT_HANDLE: &str = "kk_handle";
pub const EXPORT_SHUTDOWN: &str = "kk_shutdown";
pub const EXPORT_ALLOC: &str = "kk_alloc";
pub const EXPORT_FREE: &str = "kk_free";
pub const EXPORT_LAST_ERROR: &str = "kk_last_error";

/// 五个 import 的 `(模块, 名称)` 列表，供注册/测试使用。
pub const IMPORTS: [(&str, &str); 5] = [
    (IMPORT_MODULE, IMPORT_HOST_CALL),
    (IMPORT_MODULE, IMPORT_LOG),
    (IMPORT_MODULE, IMPORT_RAND),
    (IMPORT_MODULE, IMPORT_NOW_MS),
    (IMPORT_MODULE, IMPORT_ABORT),
];

/// 六个 export 名称，供测试使用。
pub const EXPORTS: [&str; 6] = [
    EXPORT_INIT,
    EXPORT_HANDLE,
    EXPORT_SHUTDOWN,
    EXPORT_ALLOC,
    EXPORT_FREE,
    EXPORT_LAST_ERROR,
];

/// `kk.host_call` 的 cap 不在 manifest.capabilities 白名单内。
pub const HOST_ERR_CAPABILITY_DENIED: i32 = 1;
/// 请求参数非法（内存越界、请求超长、非 UTF-8 等）。
pub const HOST_ERR_INVALID_REQUEST: i32 = 2;
/// 指针/长度越出 guest 线性内存。
pub const HOST_ERR_POINTER_OOB: i32 = 3;
/// 宿主应答 JSON 超过 `limits.output_bytes`。
pub const HOST_ERR_OUTPUT_TOO_LARGE: i32 = 4;
/// 宿主能力实现失败（由 `HostError` 映射而来）。
pub const HOST_ERR_HOST_FAILURE: i32 = 5;
/// 结果编码非法（例如 len 与内存不匹配）。
pub const HOST_ERR_INVALID_ENCODING: i32 = 6;

/// 把 `(ptr, len)` 打包成 ABI v1 的 `i64` 返回值。
#[inline]
pub const fn pack(ptr: u32, len: u32) -> i64 {
    ((ptr as i64) << 32) | (len as i64 & 0xFFFF_FFFF)
}

/// 拆解 `(ptr << 32) | len`。
#[inline]
pub const fn unpack(packed: i64) -> (u32, u32) {
    let ptr = ((packed as u64) >> 32) as u32;
    let len = (packed as u64 & 0xFFFF_FFFF) as u32;
    (ptr, len)
}

/// 把错误码编码成 `kk.host_call` 的负返回值。
#[inline]
pub const fn encode_error(code: i32) -> i64 {
    -(code as i64)
}

/// 若 `kk.host_call` 返回值是错误码则返回该码。
#[inline]
pub const fn decode_error(packed: i64) -> Option<i32> {
    if packed < 0 {
        Some((-packed) as i32)
    } else {
        None
    }
}

/// 当前 Unix 毫秒时间戳，供 `kk.now_ms` 使用。
pub fn now_ms() -> i64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    match SystemTime::now().duration_since(UNIX_EPOCH) {
        Ok(duration) => duration.as_millis() as i64,
        Err(_) => 0,
    }
}
