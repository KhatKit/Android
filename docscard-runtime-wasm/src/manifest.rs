//! manifest.json 解析与校验（spec: docs/card-runtime-wasm.md §4）。
//!
//! 校验规则（顺序固定，错误可区分）：
//! 1. `runtime` 缺省 = `"wasm"`；其它值 → `UnsupportedRuntime`（js/lua 走旧引擎，不在本 crate）。
//! 2. `abi_version` 必须 == 1，否则 `AbiVersionMismatch`。
//! 3. `capabilities` 只接受已知集合 → 否则 `UnknownCapability` / `InvalidCapability`。
//! 4. `limits` 越界 → `LimitsOutOfRange`。

use std::fmt;

use serde::Deserialize;

use crate::abi;

pub const DEFAULT_MEMORY_MB: u32 = 64;
pub const MIN_MEMORY_MB: u32 = 1;
pub const MAX_MEMORY_MB: u32 = 1024;

pub const DEFAULT_TIMEOUT_MS: u64 = 5_000;
pub const MIN_TIMEOUT_MS: u64 = 10;
pub const MAX_TIMEOUT_MS: u64 = 120_000;

pub const DEFAULT_OUTPUT_BYTES: usize = 262_144;
pub const MIN_OUTPUT_BYTES: usize = 256;
pub const MAX_OUTPUT_BYTES: usize = 8 * 1024 * 1024;

/// manifest.json 的原始结构（未知字段忽略，便于向前兼容）。
#[derive(Debug, Clone, Default, Deserialize)]
pub struct Manifest {
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub version: Option<String>,
    #[serde(default)]
    pub runtime: Option<String>,
    #[serde(default)]
    pub abi_version: Option<u32>,
    #[serde(default)]
    pub entry: Option<EntryNames>,
    #[serde(default)]
    pub capabilities: Vec<String>,
    #[serde(default)]
    pub limits: Option<Limits>,
    #[serde(default)]
    pub assets: Vec<String>,
    #[serde(default)]
    pub min_app_version: Option<String>,
}

/// ABI v1 的入口名固定为 kk_init/kk_handle/kk_shutdown；这里仅解析保留，
/// 若卡片声明了自定义名，运行时仍然调用固定名（ABI v1 冻结）。
#[derive(Debug, Clone, Default, Deserialize)]
pub struct EntryNames {
    #[serde(default)]
    pub init: Option<String>,
    #[serde(default)]
    pub handle: Option<String>,
    #[serde(default)]
    pub shutdown: Option<String>,
}

#[derive(Debug, Clone, Default, Deserialize)]
pub struct Limits {
    #[serde(default)]
    pub memory_mb: Option<u32>,
    #[serde(default)]
    pub timeout_ms: Option<u64>,
    #[serde(default)]
    pub output_bytes: Option<usize>,
}

/// 校验后的限额（已套用缺省值并检查边界）。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct LimitsSpec {
    pub memory_mb: u32,
    pub timeout_ms: u64,
    pub output_bytes: usize,
}

impl LimitsSpec {
    pub fn memory_bytes(&self) -> usize {
        self.memory_mb as usize * 1024 * 1024
    }

    pub fn memory_pages(&self) -> u32 {
        // wasm 页 = 64 KiB
        self.memory_mb * 16
    }
}

impl Default for LimitsSpec {
    fn default() -> Self {
        Self {
            memory_mb: DEFAULT_MEMORY_MB,
            timeout_ms: DEFAULT_TIMEOUT_MS,
            output_bytes: DEFAULT_OUTPUT_BYTES,
        }
    }
}

/// 能力白名单条目。未在 manifest.capabilities 声明的能力**不可**调用。
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum Capability {
    AiChat,
    FsWorkspace,
    UiRender,
    Haptics,
    Clipboard,
    /// `net:<domain>`：默认无网络，声明后才可用于该域名。
    /// `net:*` = 允许任意域名（宿主仍须审计，且只对本能力生效）。
    Net(String),
    /// `host`：环境信息/时间/时区/本地化（host.*、i18n.locale、time.*）。
    Host,
    /// `tool`：文件/OCR/剪贴板工具（L0）。
    Tool,
    /// `accessibility`：无障碍服务（L1）。
    Accessibility,
    /// `store`：卡片私有存储/SQLite/向量。
    Store,
    /// `download`：后台下载。
    Download,
    /// `web`：网页登录与 Cookie。
    Web,
    /// `browser`：可编程浏览器。
    Browser,
    /// `media:pick`：媒体选择。
    MediaPick,
    /// `schedule`：运行期定时任务。
    Schedule,
    /// `system`：toast/share/vibrate/通知/launchApp/openUrl。
    System,
    /// `image:toolbox`：依赖包注入的图像工具。
    ImageToolbox,
    /// `dep:<bridge>`：动态依赖 bridge（如 `dep:musicKey`）。
    Dep(String),
    /// `shizuku`：保留（本批卡片不用）。
    Shizuku,
    /// `root`：保留（本批卡片不用）。
    Root,
}

impl Capability {
    pub fn parse(raw: &str) -> Result<Self, ManifestError> {
        let trimmed = raw.trim();
        let cap = match trimmed {
            "ai:chat" => Self::AiChat,
            "fs:workspace" => Self::FsWorkspace,
            "ui:render" => Self::UiRender,
            "haptics" => Self::Haptics,
            "clipboard" => Self::Clipboard,
            "host" => Self::Host,
            "tool" => Self::Tool,
            "accessibility" => Self::Accessibility,
            "store" => Self::Store,
            "download" => Self::Download,
            "web" => Self::Web,
            "browser" => Self::Browser,
            "media:pick" => Self::MediaPick,
            "schedule" => Self::Schedule,
            "system" => Self::System,
            "image:toolbox" => Self::ImageToolbox,
            "shizuku" => Self::Shizuku,
            "root" => Self::Root,
            other => {
                if let Some(domain) = other.strip_prefix("net:") {
                    if domain == "*" {
                        // `net:*`：允许任意域名；宿主侧必须留审计。
                        Self::Net("*".to_string())
                    } else if domain.is_empty() || !is_capability_name(domain) {
                        return Err(ManifestError::InvalidCapability(trimmed.to_string()));
                    } else {
                        Self::Net(domain.to_string())
                    }
                } else if let Some(bridge) = other.strip_prefix("dep:") {
                    // `dep:<bridge>`：动态依赖 bridge，名字与 bridge 同名。
                    if bridge.is_empty() || !is_capability_name(bridge) {
                        return Err(ManifestError::InvalidCapability(trimmed.to_string()));
                    }
                    Self::Dep(bridge.to_string())
                } else {
                    return Err(ManifestError::UnknownCapability(trimmed.to_string()));
                }
            }
        };
        Ok(cap)
    }

    pub fn as_str(&self) -> String {
        match self {
            Self::AiChat => "ai:chat".to_string(),
            Self::FsWorkspace => "fs:workspace".to_string(),
            Self::UiRender => "ui:render".to_string(),
            Self::Haptics => "haptics".to_string(),
            Self::Clipboard => "clipboard".to_string(),
            Self::Net(domain) => format!("net:{domain}"),
            Self::Host => "host".to_string(),
            Self::Tool => "tool".to_string(),
            Self::Accessibility => "accessibility".to_string(),
            Self::Store => "store".to_string(),
            Self::Download => "download".to_string(),
            Self::Web => "web".to_string(),
            Self::Browser => "browser".to_string(),
            Self::MediaPick => "media:pick".to_string(),
            Self::Schedule => "schedule".to_string(),
            Self::System => "system".to_string(),
            Self::ImageToolbox => "image:toolbox".to_string(),
            Self::Dep(bridge) => format!("dep:{bridge}"),
            Self::Shizuku => "shizuku".to_string(),
            Self::Root => "root".to_string(),
        }
    }
}

/// `net:<domain>` / `dep:<bridge>` 的名称字符集：`[A-Za-z0-9._:-]`，且非空。
fn is_capability_name(name: &str) -> bool {
    name.chars()
        .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '_' | ':'))
}

impl fmt::Display for Capability {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.as_str())
    }
}

/// 校验通过后的 manifest 视图：引擎只需要能力列表与限额。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ManifestSpec {
    pub capabilities: Vec<Capability>,
    pub limits: LimitsSpec,
}

/// manifest 相关错误。每一类都能被调用方区分（不 panic）。
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ManifestError {
    InvalidJson(String),
    UnsupportedRuntime(String),
    AbiVersionMismatch { expected: u32, found: Option<u32> },
    UnknownCapability(String),
    InvalidCapability(String),
    LimitsOutOfRange {
        field: &'static str,
        value: u64,
        min: u64,
        max: u64,
    },
}

impl fmt::Display for ManifestError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::InvalidJson(detail) => write!(f, "manifest.json is not valid JSON: {detail}"),
            Self::UnsupportedRuntime(runtime) => write!(
                f,
                "manifest.runtime = `{runtime}` is not supported by this crate (only `wasm`)"
            ),
            Self::AbiVersionMismatch { expected, found } => match found {
                Some(found) => write!(f, "manifest.abi_version = {found}, expected {expected}"),
                None => write!(f, "manifest.abi_version is missing, expected {expected}"),
            },
            Self::UnknownCapability(cap) => write!(
                f,
                "manifest.capabilities contains unknown capability `{cap}` \
                 (known: ai:chat, fs:workspace, ui:render, haptics, clipboard, host, tool, \
                 accessibility, store, download, web, browser, media:pick, schedule, system, \
                 image:toolbox, shizuku, root, net:<domain>|net:*, dep:<bridge>)"
            ),
            Self::InvalidCapability(cap) => {
                write!(f, "manifest.capabilities contains malformed capability `{cap}`")
            }
            Self::LimitsOutOfRange { field, value, min, max } => write!(
                f,
                "manifest.limits.{field} = {value} is out of range [{min}, {max}]"
            ),
        }
    }
}

impl std::error::Error for ManifestError {}

impl Manifest {
    /// 解析 manifest.json（不做语义校验，校验见 `validate`）。
    pub fn parse(json: &str) -> Result<Self, ManifestError> {
        serde_json::from_str(json).map_err(|err| ManifestError::InvalidJson(err.to_string()))
    }

    /// 解析 + 校验。
    pub fn parse_and_validate(json: &str) -> Result<(Self, ManifestSpec), ManifestError> {
        let manifest = Self::parse(json)?;
        let spec = manifest.validate()?;
        Ok((manifest, spec))
    }

    /// 按固定顺序校验，并返回套用缺省值后的 `ManifestSpec`。
    pub fn validate(&self) -> Result<ManifestSpec, ManifestError> {
        let runtime = self.runtime.as_deref().unwrap_or("wasm");
        if !runtime.eq_ignore_ascii_case("wasm") {
            return Err(ManifestError::UnsupportedRuntime(runtime.to_string()));
        }
        if self.abi_version != Some(abi::ABI_VERSION) {
            return Err(ManifestError::AbiVersionMismatch {
                expected: abi::ABI_VERSION,
                found: self.abi_version,
            });
        }
        let mut capabilities: Vec<Capability> = Vec::with_capacity(self.capabilities.len());
        for raw in &self.capabilities {
            let cap = Capability::parse(raw)?;
            if !capabilities.contains(&cap) {
                capabilities.push(cap);
            }
        }
        let limits = self.resolved_limits()?;
        Ok(ManifestSpec { capabilities, limits })
    }

    fn resolved_limits(&self) -> Result<LimitsSpec, ManifestError> {
        let raw = self.limits.clone().unwrap_or_default();
        let memory_mb = raw.memory_mb.unwrap_or(DEFAULT_MEMORY_MB);
        check_range(
            "memory_mb",
            memory_mb as u64,
            MIN_MEMORY_MB as u64,
            MAX_MEMORY_MB as u64,
        )?;
        let timeout_ms = raw.timeout_ms.unwrap_or(DEFAULT_TIMEOUT_MS);
        check_range(
            "timeout_ms",
            timeout_ms,
            MIN_TIMEOUT_MS,
            MAX_TIMEOUT_MS,
        )?;
        let output_bytes = raw.output_bytes.unwrap_or(DEFAULT_OUTPUT_BYTES);
        check_range(
            "output_bytes",
            output_bytes as u64,
            MIN_OUTPUT_BYTES as u64,
            MAX_OUTPUT_BYTES as u64,
        )?;
        Ok(LimitsSpec {
            memory_mb,
            timeout_ms,
            output_bytes,
        })
    }
}

fn check_range(field: &'static str, value: u64, min: u64, max: u64) -> Result<(), ManifestError> {
    if value < min || value > max {
        return Err(ManifestError::LimitsOutOfRange {
            field,
            value,
            min,
            max,
        });
    }
    Ok(())
}
