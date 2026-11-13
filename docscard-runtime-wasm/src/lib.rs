//! docscard-runtime-wasm —— KhatKit 卡片 WASM 运行时（ABI v1，基于 wasmi 0.36）。
//!
//! 对应规格：`docs/card-runtime-wasm.md` §4（manifest）、§5（执行器选型）、§6（ABI v1）、§9（限额）。
//!
//! # 快速上手
//!
//! `no_run
//! use docscard_runtime_wasm::{CardRuntime, DefaultHost};
//!
//! let wasm: Vec<u8> = std::fs::read("card.wasm")?;
//! let manifest = std::fs::read_to_string("manifest.json")?;
//! let mut runtime = CardRuntime::from_manifest_json(&wasm, &manifest, DefaultHost::new())?;
//! runtime.init(&manifest)?;
//! let result = runtime.handle("{\"type\":\"tick\",\"payload\":{}}")?;
//! runtime.shutdown()?;
//! println!("{result}");
//! # Ok::<(), Box<dyn std::error::Error>>(())
//! `
//!
//! # ABI v1 冻结约定（详见 `engine` 模块文档与 `abi` 模块）
//!
//! * imports：`kk.host_call` / `kk.log` / `kk.rand` / `kk.now_ms` / `kk.abort`。
//! * exports：`kk_init` / `kk_handle` / `kk_shutdown` / `kk_alloc` / `kk_free` / `kk_last_error`。
//! * 数据只经线性内存 + JSON 交换：宿主用 `kk_alloc` 写入事件 JSON -> 读 `(ptr << 32) | len` 的结果 JSON -> `kk_free`。
//! * 未在 `manifest.capabilities` 声明的能力调用 `kk.host_call` 一律拒绝（返回负错误码）。
//! * `limits`：`memory_mb` -> StoreLimits、`timeout_ms` -> fuel、`output_bytes` -> 回传/结果长度上限。
//!
//! # .kkcard 包（`package` 模块）
//!
//! `CardPackage::open` / `open_path` 装载 zip 包并做安全校验（8 MiB 包上限、32 MiB 解压预算
//! 边读边扣、256 条目上限、zip slip / 符号链接 / 未知顶层条目拒绝、manifest 复用 `Manifest`
//! 校验、可选 `sha256` 整包校验），随后 `into_runtime(host)` 直接拿到可执行的 `CardRuntime`。

#![forbid(unsafe_code)]

pub mod abi;
pub mod engine;
pub mod error;
pub mod host;
pub mod manifest;
pub mod mock_host;
pub mod package;
pub mod ui;

#[cfg(test)]
mod tests;

pub use crate::abi::{pack, unpack, ABI_VERSION};
pub use crate::engine::{
    link_abi, CardRuntime, HostCallFailure, HostState, LogRecord, FUEL_PER_MS,
};
pub use crate::error::RuntimeError;
pub use crate::host::{CardHost, DefaultHost, HostError};
pub use crate::manifest::{Capability, Limits, LimitsSpec, Manifest, ManifestError, ManifestSpec};
pub use crate::mock_host::{MockCall, MockHost};
pub use crate::ui::{
    components, normalize_screen_pages, normalize_tree, NullUiPresenter, ScriptedUiPresenter, UiPresenter, UiRenderHost,
    UiRequest, MAX_DEPTH, MAX_NODES,
};
pub use crate::package::{
    check_entry_name, sha256_hex, CardPackage, PackageError, PathUnsafeReason, MAX_ARCHIVE_BYTES,
    MAX_ENTRIES, MAX_UNCOMPRESSED_BYTES,
};
