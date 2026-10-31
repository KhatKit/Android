//! .kkcard 包装载与安全校验（spec: docs/card-runtime-wasm.md §4 包格式 + §9 安全与资源限额）。
//!
//! .kkcard 是一个 zip：
//!
//! `text
//! manifest.json      # 必填，精确名
//! card.wasm          # 必填，精确名
//! assets/**          # 可选
//! locales/**         # 可选
//! `
//!
//! 安全约束（任何一条不满足都返回 `PackageError`，绝不 panic、绝不部分装载）：
//!
//! 1. 压缩包 <= 8 MiB；解压总量 <= 32 MiB，且**边读边扣预算**（每读 64 KiB 就扣一次，
//!    超过预算立即返回，不先整体解压再检查，防 zip bomb）；条目数 <= 256。
//! 2. 路径安全：拒绝空名、NUL、反斜杠、绝对路径（`/` 开头或盘符）、含 `..` 组件、
//!    以及 unix 符号链接条目（`S_IFLNK`）。-- 即 zip slip 防护。
//! 3. 顶层白名单：只允许 `manifest.json`、`card.wasm`、`assets/**`、`locales/**`，
//!    其它任何条目（含嵌套未知）一律拒绝。
//! 4. manifest.json 复用 `Manifest::parse_and_validate`；runtime 非 wasm / abi_version != 1
//!    会以 `PackageError::Manifest(..)` 返回，且 `PackageError::kind()` 可区分。
//! 5. 可选 sha256：`sha256_hex` + `CardPackage::verify_sha256`（按**整个 .kkcard 文件字节**计算）。

use std::collections::{BTreeMap, BTreeSet};
use std::fmt;
use std::io::{Cursor, Read};
use std::path::Path;

use sha2::{Digest, Sha256};
use zip::ZipArchive;

use crate::engine::CardRuntime;
use crate::error::RuntimeError;
use crate::host::CardHost;
use crate::manifest::{Manifest, ManifestError};

/// 压缩包（.kkcard 文件本身）大小上限：8 MiB（与市场限制一致）。
pub const MAX_ARCHIVE_BYTES: usize = 8 * 1024 * 1024;
/// 解压后总量上限：32 MiB（边读边扣）。
pub const MAX_UNCOMPRESSED_BYTES: u64 = 32 * 1024 * 1024;
/// 条目数上限。
pub const MAX_ENTRIES: usize = 256;

/// 必填条目：manifest。
pub const MANIFEST_ENTRY: &str = "manifest.json";
/// 必填条目：wasm 卡片。
pub const CARD_ENTRY: &str = "card.wasm";
/// 允许的顶层目录前缀。
pub const ASSET_PREFIXES: [&str; 2] = ["assets/", "locales/"];

const S_IFMT: u32 = 0o170000;
const S_IFLNK: u32 = 0o120000;

/// 路径不安全的类别（都在 `PackageError::UnsafePath` 里）。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PathUnsafeReason {
    /// 条目名为空。
    Empty,
    /// 含 NUL 字节。
    Nul,
    /// 含反斜杠（Windows 分隔符，绕过 `/` 检查的常见手法）。
    Backslash,
    /// 绝对路径（`/` 开头或 `C:` 盘符）。
    Absolute,
    /// 含 `..` 组件（zip slip：`../evil`）。
    ParentTraversal,
}

impl PathUnsafeReason {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Empty => "empty-name",
            Self::Nul => "nul-byte",
            Self::Backslash => "backslash",
            Self::Absolute => "absolute-path",
            Self::ParentTraversal => "parent-traversal",
        }
    }
}

impl fmt::Display for PathUnsafeReason {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

/// 装载 .kkcard 时可能出现的所有错误。每一类都可通过 `kind()` 区分。
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum PackageError {
    /// 文件读/写失败。
    Io(String),
    /// zip 结构非法（截断、central directory 损坏等）。
    Zip(String),
    /// 压缩包本身超过 8 MiB。
    ArchiveTooLarge { size: usize, limit: usize },
    /// 解压总量超过 32 MiB（读取过程中触发）。
    ///
    /// `bytes_read` 是**全局累计已读字节数**（含触发越界的那一块），因此落在
    /// `(32 MiB, 32 MiB + 64 KiB]`；`entry` 是跨过边界的那个条目名。
    UncompressedBudgetExceeded {
        entry: String,
        bytes_read: u64,
        limit: u64,
    },
    /// 条目数超过 256。
    TooManyEntries { count: usize, limit: usize },
    /// 条目名重复。
    DuplicateEntry { name: String },
    /// 路径不安全（zip slip 等）。
    UnsafePath {
        name: String,
        reason: PathUnsafeReason,
    },
    /// 符号链接条目。
    SymlinkEntry { name: String },
    /// 顶层白名单之外的条目。
    UnknownEntry { name: String },
    /// 缺 manifest.json。
    MissingManifest,
    /// 缺 card.wasm。
    MissingCardWasm,
    /// manifest 解析/校验失败（含 runtime != wasm、abi_version != 1）。
    Manifest(ManifestError),
    /// sha256 不匹配。
    Sha256Mismatch { expected: String, actual: String },
    /// 期望的 sha256 不是 64 位十六进制。
    InvalidSha256(String),
    /// 交给运行时后失败（实例化、执行、限额等）。
    Runtime(RuntimeError),
}

impl PackageError {
    /// 稳定的错误类别字符串（CLI 前缀 / 日志 / 测试断言用）。
    pub fn kind(&self) -> &'static str {
        match self {
            Self::Io(_) => "io",
            Self::Zip(_) => "zip",
            Self::ArchiveTooLarge { .. } => "archive-too-large",
            Self::UncompressedBudgetExceeded { .. } => "uncompressed-budget-exceeded",
            Self::TooManyEntries { .. } => "too-many-entries",
            Self::DuplicateEntry { .. } => "duplicate-entry",
            Self::UnsafePath { reason, .. } => match reason {
                PathUnsafeReason::Empty => "path-empty",
                PathUnsafeReason::Nul => "path-nul",
                PathUnsafeReason::Backslash => "path-backslash",
                PathUnsafeReason::Absolute => "path-absolute",
                PathUnsafeReason::ParentTraversal => "path-traversal",
            },
            Self::SymlinkEntry { .. } => "symlink-entry",
            Self::UnknownEntry { .. } => "unknown-entry",
            Self::MissingManifest => "missing-manifest",
            Self::MissingCardWasm => "missing-card-wasm",
            Self::Manifest(err) => match err {
                ManifestError::UnsupportedRuntime(_) => "manifest-unsupported-runtime",
                ManifestError::AbiVersionMismatch { .. } => "manifest-abi-mismatch",
                ManifestError::UnknownCapability(_) => "manifest-unknown-capability",
                ManifestError::InvalidCapability(_) => "manifest-invalid-capability",
                ManifestError::LimitsOutOfRange { .. } => "manifest-limits-out-of-range",
                ManifestError::InvalidJson(_) => "manifest-invalid-json",
            },
            Self::Sha256Mismatch { .. } => "sha256-mismatch",
            Self::InvalidSha256(_) => "sha256-invalid",
            Self::Runtime(_) => "runtime",
        }
    }
}

impl fmt::Display for PackageError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Io(detail) => write!(f, "package io error: {detail}"),
            Self::Zip(detail) => write!(f, "package is not a valid zip: {detail}"),
            Self::ArchiveTooLarge { size, limit } => write!(
                f,
                "package is {size} bytes, limit is {limit} bytes (8 MiB)"
            ),
            Self::UncompressedBudgetExceeded {
                entry,
                bytes_read,
                limit,
            } => write!(
                f,
                "package extraction budget exceeded at `{entry}` (read {bytes_read} bytes, limit {limit})"
            ),
            Self::TooManyEntries { count, limit } => {
                write!(f, "package has {count} entries, limit is {limit}")
            }
            Self::DuplicateEntry { name } => write!(f, "duplicate package entry `{name}`"),
            Self::UnsafePath { name, reason } => {
                write!(f, "unsafe package entry path `{name}` ({reason})")
            }
            Self::SymlinkEntry { name } => {
                write!(f, "symbolic link entry `{name}` is not allowed in a card package")
            }
            Self::UnknownEntry { name } => write!(
                f,
                "package entry `{name}` is not allowed (only manifest.json, card.wasm, assets/**, locales/**)"
            ),
            Self::MissingManifest => write!(f, "package is missing `manifest.json`"),
            Self::MissingCardWasm => write!(f, "package is missing `card.wasm`"),
            Self::Manifest(err) => write!(f, "package manifest rejected: {err}"),
            Self::Sha256Mismatch { expected, actual } => {
                write!(f, "package sha256 mismatch: expected {expected}, got {actual}")
            }
            Self::InvalidSha256(value) => {
                write!(f, "expected sha256 `{value}` is not 64 hex characters")
            }
            Self::Runtime(err) => write!(f, "package card runtime error: {err}"),
        }
    }
}

impl std::error::Error for PackageError {}

impl From<ManifestError> for PackageError {
    fn from(err: ManifestError) -> Self {
        Self::Manifest(err)
    }
}

impl From<RuntimeError> for PackageError {
    fn from(err: RuntimeError) -> Self {
        Self::Runtime(err)
    }
}

/// 校验条目路径是否安全（zip slip / 符号链接之外的路径类攻击）。
///
/// 规则：非空、无 NUL、无反斜杠、非绝对路径、无 `..` 组件。
pub fn check_entry_name(name: &str) -> Result<(), PathUnsafeReason> {
    if name.is_empty() {
        return Err(PathUnsafeReason::Empty);
    }
    if name.contains('\0') {
        return Err(PathUnsafeReason::Nul);
    }
    if name.contains('\\') {
        return Err(PathUnsafeReason::Backslash);
    }
    if name.starts_with('/') {
        return Err(PathUnsafeReason::Absolute);
    }
    let bytes = name.as_bytes();
    if bytes.len() >= 2 && bytes[0].is_ascii_alphabetic() && bytes[1] == b':' {
        // Windows 盘符（C:/x、C:\x 已被反斜杠规则拦下）
        return Err(PathUnsafeReason::Absolute);
    }
    if name.split('/').any(|component| component == "..") {
        return Err(PathUnsafeReason::ParentTraversal);
    }
    Ok(())
}

/// 计算 sha256 的小写十六进制串。
pub fn sha256_hex(bytes: &[u8]) -> String {
    let digest = Sha256::digest(bytes);
    let mut out = String::with_capacity(digest.len() * 2);
    for byte in digest {
        out.push_str(&format!("{byte:02x}"));
    }
    out
}

/// 已校验的 .kkcard 包（内容全部在内存中）。
#[derive(Debug, Clone)]
pub struct CardPackage {
    /// 校验通过的 manifest。
    pub manifest: Manifest,
    /// manifest.json 的原始文本（交给 `kk_init`）。
    pub manifest_json: String,
    /// card.wasm 字节。
    pub wasm_bytes: Vec<u8>,
    /// assets/** 与 locales/** 的内容，键为包内完整路径（如 `assets/logo.png`）。
    pub assets: BTreeMap<String, Vec<u8>>,
    /// 整个 .kkcard 文件字节的 sha256（供 `verify_sha256` 使用）。
    pub archive_sha256: String,
}

impl CardPackage {
    /// 从内存中的 .kkcard 字节装载并校验。
    pub fn open(bytes: Vec<u8>) -> Result<Self, PackageError> {
        if bytes.len() > MAX_ARCHIVE_BYTES {
            return Err(PackageError::ArchiveTooLarge {
                size: bytes.len(),
                limit: MAX_ARCHIVE_BYTES,
            });
        }
        // 哈希覆盖整个包文件（发布者签名/市场 sha256 的语义）。
        let archive_sha256 = sha256_hex(&bytes);

        // 先按 central directory 自己数条目：zip crate 会对重名条目做去重（后写覆盖先写），
        // 因此重名/条目数必须从原始字节判定，否则重复条目会被静默吞掉。
        let declared_entries = scan_central_directory(&bytes)?;
        if declared_entries > MAX_ENTRIES {
            return Err(PackageError::TooManyEntries {
                count: declared_entries,
                limit: MAX_ENTRIES,
            });
        }

        let mut archive = ZipArchive::new(Cursor::new(bytes))
            .map_err(|err| PackageError::Zip(err.to_string()))?;
        // fail-closed：EOCD/central directory 的条目数必须与 zip crate 解析结果一致，
        // 否则说明字节与解析器对不上（例如 EOCD 注释里塞了伪签名），直接拒绝。
        if archive.len() != declared_entries {
            return Err(PackageError::Zip(
                "central directory count mismatch".to_string(),
            ));
        }

        let mut budget = MAX_UNCOMPRESSED_BYTES;
        // 全局累计已读字节数（跨条目累加，用于错误里的 bytes_read）。
        let mut total_read: u64 = 0;
        let mut seen: BTreeSet<String> = BTreeSet::new();
        let mut counted = 0usize;
        let mut manifest_json: Option<String> = None;
        let mut wasm_bytes: Option<Vec<u8>> = None;
        let mut assets: BTreeMap<String, Vec<u8>> = BTreeMap::new();

        for index in 0..declared_entries {
            let mut entry = archive
                .by_index(index)
                .map_err(|err| PackageError::Zip(err.to_string()))?;
            let raw_name = entry.name().to_string();
            let is_dir = entry.is_dir() || raw_name.ends_with('/');

            counted += 1;
            if counted > MAX_ENTRIES {
                return Err(PackageError::TooManyEntries {
                    count: counted,
                    limit: MAX_ENTRIES,
                });
            }

            // 路径安全先于一切内容读取：目录条目也要验名。
            check_entry_name(&raw_name).map_err(|reason| PackageError::UnsafePath {
                name: raw_name.clone(),
                reason,
            })?;

            if is_dir {
                continue;
            }

            // unix 符号链接条目一律拒绝（解压时可能指向包外）。
            if let Some(mode) = entry.unix_mode() {
                if mode & S_IFMT == S_IFLNK {
                    return Err(PackageError::SymlinkEntry { name: raw_name });
                }
            }

            if !seen.insert(raw_name.clone()) {
                return Err(PackageError::DuplicateEntry { name: raw_name });
            }

            match classify_entry(&raw_name)? {
                EntryKind::Manifest => {
                    let data = read_entry_capped(&mut entry, &raw_name, &mut budget, &mut total_read)?;
                    let text = String::from_utf8(data).map_err(|_| {
                        PackageError::Manifest(ManifestError::InvalidJson(
                            "manifest.json is not valid UTF-8".to_string(),
                        ))
                    })?;
                    manifest_json = Some(text);
                }
                EntryKind::CardWasm => {
                    wasm_bytes = Some(read_entry_capped(&mut entry, &raw_name, &mut budget, &mut total_read)?);
                }
                EntryKind::Asset => {
                    let data = read_entry_capped(&mut entry, &raw_name, &mut budget, &mut total_read)?;
                    assets.insert(raw_name, data);
                }
            }
        }

        let manifest_json = manifest_json.ok_or(PackageError::MissingManifest)?;
        let wasm_bytes = wasm_bytes.ok_or(PackageError::MissingCardWasm)?;
        let (manifest, _spec) = Manifest::parse_and_validate(&manifest_json)?;

        Ok(Self {
            manifest,
            manifest_json,
            wasm_bytes,
            assets,
            archive_sha256,
        })
    }

    /// 从磁盘路径装载。
    ///
    /// **先 `metadata().len()` 判 8 MiB 上限，再读文件**：磁盘上超大的 .kkcard 在读之前就被拒，
    /// 不会为了判大小先把几十上百 MiB 读进内存。读完仍交给 `open` 做完整校验
    /// （read-only 文件/竞态下长度变化也由 `open` 的大小闸门兜底）。
    pub fn open_path(path: impl AsRef<Path>) -> Result<Self, PackageError> {
        let path = path.as_ref();
        let metadata = std::fs::metadata(path)
            .map_err(|err| PackageError::Io(format!("{}: {err}", path.display())))?;
        if metadata.len() > MAX_ARCHIVE_BYTES as u64 {
            return Err(PackageError::ArchiveTooLarge {
                size: metadata.len().min(usize::MAX as u64) as usize,
                limit: MAX_ARCHIVE_BYTES,
            });
        }
        let bytes = std::fs::read(path)
            .map_err(|err| PackageError::Io(format!("{}: {err}", path.display())))?;
        Self::open(bytes)
    }

    /// 校验整包 sha256（接受 `sha256:` 前缀与大小写差异）。
    pub fn verify_sha256(&self, expected_hex: &str) -> Result<(), PackageError> {
        let expected = normalize_sha256(expected_hex)?;
        if expected == self.archive_sha256 {
            Ok(())
        } else {
            Err(PackageError::Sha256Mismatch {
                expected,
                actual: self.archive_sha256.clone(),
            })
        }
    }

    /// 取一个 assets/** 或 locales/** 的内容。
    pub fn asset(&self, name: &str) -> Option<&[u8]> {
        self.assets.get(name).map(|bytes| bytes.as_slice())
    }

    /// 用包内的 manifest + card.wasm 实例化运行时。
    pub fn into_runtime<H: CardHost>(&self, host: H) -> Result<CardRuntime<H>, PackageError> {
        let runtime = CardRuntime::load(&self.wasm_bytes, &self.manifest, host)?;
        Ok(runtime)
    }
}

enum EntryKind {
    Manifest,
    CardWasm,
    Asset,
}

fn classify_entry(name: &str) -> Result<EntryKind, PackageError> {
    if name == MANIFEST_ENTRY {
        return Ok(EntryKind::Manifest);
    }
    if name == CARD_ENTRY {
        return Ok(EntryKind::CardWasm);
    }
    for prefix in ASSET_PREFIXES {
        if let Some(rest) = name.strip_prefix(prefix) {
            if !rest.is_empty() {
                return Ok(EntryKind::Asset);
            }
        }
    }
    Err(PackageError::UnknownEntry {
        name: name.to_string(),
    })
}

const EOCD_SIGNATURE: u32 = 0x0605_4b50;
const CENTRAL_DIR_SIGNATURE: u32 = 0x0201_4b50;
const EOCD_MIN_LEN: usize = 22;
const MAX_COMMENT_LEN: usize = 65_535;

/// 直接解析 zip 的 central directory：返回条目总数，并拒绝重名条目。
///
/// zip crate 的 ~ZipArchive~ 用名字索引条目（后写覆盖先写），重名会被静默合并，
/// 所以条目数与重名判定必须从原始字节得出（同时也避免"条目数上限"被去重绕过）。
fn scan_central_directory(bytes: &[u8]) -> Result<usize, PackageError> {
    if bytes.len() < EOCD_MIN_LEN {
        return Err(PackageError::Zip("file is too short to be a zip".to_string()));
    }
    // EOCD 在文件尾部，最多允许 64 KiB 注释（从后往前找最后一次出现）。
    let search_start = bytes.len() - EOCD_MIN_LEN - MAX_COMMENT_LEN.min(bytes.len() - EOCD_MIN_LEN);
    let mut cursor = bytes.len() - EOCD_MIN_LEN;
    let eocd = loop {
        if read_u32(bytes, cursor) == Some(EOCD_SIGNATURE) {
            break cursor;
        }
        if cursor == search_start {
            return Err(PackageError::Zip(
                "end of central directory record not found".to_string(),
            ));
        }
        cursor -= 1;
    };

    let total_entries = read_u16(bytes, eocd + 10)
        .ok_or_else(|| PackageError::Zip("truncated zip end record".to_string()))?
        as usize;
    if total_entries == u16::MAX as usize {
        return Err(PackageError::Zip(
            "zip64 archives are not supported for cards".to_string(),
        ));
    }
    let cd_size = read_u32(bytes, eocd + 12)
        .ok_or_else(|| PackageError::Zip("truncated zip end record".to_string()))?
        as usize;
    let cd_offset = read_u32(bytes, eocd + 16)
        .ok_or_else(|| PackageError::Zip("truncated zip end record".to_string()))?
        as usize;
    let cd_end = cd_offset
        .checked_add(cd_size)
        .filter(|end| *end <= eocd)
        .ok_or_else(|| PackageError::Zip("central directory is out of bounds".to_string()))?;

    let mut seen: BTreeSet<String> = BTreeSet::new();
    let mut offset = cd_offset;
    for index in 0..total_entries {
        if offset + 46 > cd_end {
            return Err(PackageError::Zip(format!(
                "truncated central directory at entry #{index}"
            )));
        }
        if read_u32(bytes, offset) != Some(CENTRAL_DIR_SIGNATURE) {
            return Err(PackageError::Zip(format!(
                "bad central directory signature at entry #{index}"
            )));
        }
        let name_len = read_u16(bytes, offset + 28).unwrap_or(0) as usize;
        let extra_len = read_u16(bytes, offset + 30).unwrap_or(0) as usize;
        let comment_len = read_u16(bytes, offset + 32).unwrap_or(0) as usize;
        let name_start = offset + 46;
        let name_end = name_start
            .checked_add(name_len)
            .filter(|end| *end <= cd_end)
            .ok_or_else(|| PackageError::Zip("central directory name is out of bounds".to_string()))?;
        let name = String::from_utf8_lossy(&bytes[name_start..name_end]).into_owned();
        if !seen.insert(name.clone()) {
            return Err(PackageError::DuplicateEntry { name });
        }
        offset = name_end
            .checked_add(extra_len)
            .and_then(|next| next.checked_add(comment_len))
            .ok_or_else(|| PackageError::Zip("central directory entry is out of bounds".to_string()))?;
    }

    Ok(total_entries)
}

fn read_u16(bytes: &[u8], at: usize) -> Option<u16> {
    let slice = bytes.get(at..at.checked_add(2)?)?;
    Some(u16::from_le_bytes([slice[0], slice[1]]))
}

fn read_u32(bytes: &[u8], at: usize) -> Option<u32> {
    let slice = bytes.get(at..at.checked_add(4)?)?;
    Some(u32::from_le_bytes([slice[0], slice[1], slice[2], slice[3]]))
}

/// 按 64 KiB 分块读取条目，**读一块扣一块预算**：超预算立即返回，不等整体解压完。
///
/// `budget` 是剩余预算（跨条目共享），`total_read` 是**全局累计已读字节数**
/// （跨条目累加，含触发越界的那一块），用于错误里的 `bytes_read`。
fn read_entry_capped<R: Read>(
    entry: &mut R,
    name: &str,
    budget: &mut u64,
    total_read: &mut u64,
) -> Result<Vec<u8>, PackageError> {
    let mut out: Vec<u8> = Vec::new();
    let mut chunk = [0u8; 64 * 1024];
    loop {
        let read = entry
            .read(&mut chunk)
            .map_err(|err| PackageError::Zip(format!("{name}: {err}")))?;
        if read == 0 {
            break;
        }
        let read = read as u64;
        // 先记账再判预算：报出来的 bytes_read 包含"跨过边界"的那一块。
        *total_read += read;
        if read > *budget {
            return Err(PackageError::UncompressedBudgetExceeded {
                entry: name.to_string(),
                bytes_read: *total_read,
                limit: MAX_UNCOMPRESSED_BYTES,
            });
        }
        *budget -= read;
        out.extend_from_slice(&chunk[..read as usize]);
    }
    Ok(out)
}

fn normalize_sha256(raw: &str) -> Result<String, PackageError> {
    let trimmed = raw.trim();
    let without_prefix = trimmed
        .strip_prefix("sha256:")
        .or_else(|| trimmed.strip_prefix("SHA256:"))
        .unwrap_or(trimmed)
        .trim();
    if without_prefix.len() != 64 || !without_prefix.bytes().all(|b| b.is_ascii_hexdigit()) {
        return Err(PackageError::InvalidSha256(raw.to_string()));
    }
    Ok(without_prefix.to_ascii_lowercase())
}

#[cfg(test)]
mod tests {
    use std::io::Write;

    use zip::write::SimpleFileOptions;
    use zip::ZipWriter;

    use super::*;
    use crate::host::DefaultHost;
    use crate::tests::{build_wasm, echo_card_body};

    fn manifest_with(runtime: &str, abi: u32) -> String {
        format!(
            r#"{{"name":"pkg-card","version":"1.0.0","runtime":"{runtime}","abi_version":{abi},"capabilities":["haptics"],"limits":{{"memory_mb":16,"timeout_ms":1000,"output_bytes":65536}}}}"#
        )
    }

    fn valid_manifest() -> String {
        manifest_with("wasm", 1)
    }

    fn echo_wasm() -> Vec<u8> {
        build_wasm(&echo_card_body())
    }

    fn zip_bytes(entries: &[(&str, Vec<u8>)]) -> Vec<u8> {
        let mut writer = ZipWriter::new(Cursor::new(Vec::new()));
        let options = SimpleFileOptions::default();
        for (name, data) in entries {
            writer.start_file(*name, options).expect("start_file");
            writer.write_all(data).expect("write entry");
        }
        writer.finish().expect("finish zip").into_inner()
    }

    fn valid_entries() -> Vec<(&'static str, Vec<u8>)> {
        vec![
            ("manifest.json", valid_manifest().into_bytes()),
            ("card.wasm", echo_wasm()),
            ("assets/logo.bin", b"\x89PNG-asset-bytes".to_vec()),
            ("locales/zh.json", r#"{"hello":"世界"}"#.as_bytes().to_vec()),
        ]
    }

    /// 把包里所有出现 `from` 的条目名改成 `to`（等长，用于构造 zip slip / 重名用例）。
    fn patch_names(bytes: &[u8], from: &str, to: &str) -> Vec<u8> {
        assert_eq!(from.len(), to.len(), "patch must keep byte length");
        let from = from.as_bytes();
        let to = to.as_bytes();
        let mut out = bytes.to_vec();
        let mut hits = 0;
        let mut index = 0;
        while index + from.len() <= out.len() {
            if &out[index..index + from.len()] == from {
                out[index..index + from.len()].copy_from_slice(to);
                hits += 1;
                index += from.len();
            } else {
                index += 1;
            }
        }
        assert!(hits >= 2, "entry name should appear in local header and central directory");
        out
    }

    #[test]
    fn valid_package_extracts_assets_and_runs_card() {
        let bytes = zip_bytes(&valid_entries());
        let package = CardPackage::open(bytes).expect("valid package must load");

        assert_eq!(package.manifest.name.as_deref(), Some("pkg-card"));
        assert_eq!(package.manifest.abi_version, Some(1));
        assert!(!package.wasm_bytes.is_empty());
        assert_eq!(
            package.asset("assets/logo.bin"),
            Some(b"\x89PNG-asset-bytes".as_slice())
        );
        assert_eq!(
            package.asset("locales/zh.json"),
            Some(r#"{"hello":"世界"}"#.as_bytes())
        );
        assert_eq!(package.assets.len(), 2);
        assert_eq!(package.archive_sha256.len(), 64);

        // into_runtime 之后能跑 handle
        let mut runtime = package
            .into_runtime(DefaultHost::new())
            .expect("package card must instantiate");
        runtime.init(&package.manifest_json).expect("kk_init");
        let event = r#"{"type":"tick","payload":{"n":7}}"#;
        assert_eq!(runtime.handle(event).expect("kk_handle"), event);
        runtime.shutdown().expect("kk_shutdown");
    }

    #[test]
    fn open_path_reads_from_disk() {
        let dir = std::path::Path::new("target/pkg-tests");
        std::fs::create_dir_all(dir).unwrap();
        let path = dir.join("valid.kkcard");
        std::fs::write(&path, zip_bytes(&valid_entries())).unwrap();
        let package = CardPackage::open_path(&path).expect("open_path must work");
        assert_eq!(package.manifest.name.as_deref(), Some("pkg-card"));
        assert_eq!(
            CardPackage::open_path(dir.join("missing.kkcard")).unwrap_err().kind(),
            "io"
        );
    }

    #[test]
    fn rejects_oversized_archive() {
        let bytes = vec![0u8; MAX_ARCHIVE_BYTES + 1];
        let err = CardPackage::open(bytes).expect_err("oversized package must be rejected");
        assert!(
            matches!(err, PackageError::ArchiveTooLarge { size, limit } if size == MAX_ARCHIVE_BYTES + 1 && limit == MAX_ARCHIVE_BYTES)
        );
        assert_eq!(err.kind(), "archive-too-large");
    }

    #[test]
    fn rejects_too_many_entries() {
        let mut entries: Vec<(String, Vec<u8>)> = Vec::new();
        entries.push(("manifest.json".to_string(), valid_manifest().into_bytes()));
        entries.push(("card.wasm".to_string(), echo_wasm()));
        for index in 0..(MAX_ENTRIES + 1) {
            entries.push((format!("assets/f{index}.bin"), vec![0u8; 4]));
        }
        let refs: Vec<(&str, Vec<u8>)> = entries
            .iter()
            .map(|(name, data)| (name.as_str(), data.clone()))
            .collect();
        let err = CardPackage::open(zip_bytes(&refs)).expect_err("too many entries");
        assert!(matches!(
            err,
            PackageError::TooManyEntries { count, limit } if count > MAX_ENTRIES && limit == MAX_ENTRIES
        ));
        assert_eq!(err.kind(), "too-many-entries");
    }

    #[test]
    fn rejects_zip_bomb_with_incremental_budget() {
        // a/b/c/d 恰好填满 32 MiB 预算，e 跨过边界，z 在 e 之后（必须一次都没被读）。
        let manifest = valid_manifest().into_bytes();
        let wasm = echo_wasm();
        let remaining = MAX_UNCOMPRESSED_BYTES - (manifest.len() + wasm.len()) as u64;
        let per = (remaining / 4) as usize;
        let rem = (remaining % 4) as usize;

        let mut entries: Vec<(String, Vec<u8>)> = vec![
            ("manifest.json".to_string(), manifest),
            ("card.wasm".to_string(), wasm),
        ];
        for (index, name) in ["assets/a.bin", "assets/b.bin", "assets/c.bin", "assets/d.bin"]
            .iter()
            .enumerate()
        {
            let size = if index == 3 { per + rem } else { per };
            entries.push((name.to_string(), vec![0u8; size]));
        }
        entries.push(("assets/e.bin".to_string(), vec![0u8; 8 * 1024 * 1024]));
        entries.push(("assets/z.bin".to_string(), vec![0u8; 1024 * 1024]));

        let refs: Vec<(&str, Vec<u8>)> = entries
            .iter()
            .map(|(name, data)| (name.as_str(), data.clone()))
            .collect();
        let archive = zip_bytes(&refs);
        assert!(
            archive.len() < 1024 * 1024,
            "bomb should be tiny compressed ({} bytes)",
            archive.len()
        );

        let err = CardPackage::open(archive).expect_err("zip bomb must be rejected");
        match err {
            PackageError::UncompressedBudgetExceeded {
                entry,
                bytes_read,
                limit,
            } => {
                assert_eq!(limit, MAX_UNCOMPRESSED_BYTES);
                // 触发条目必须是跨过边界的那个（e），说明 z 在其后再没被读过。
                assert_eq!(entry, "assets/e.bin");
                // bytes_read 是全局累计值（含触发块）：只越过边界一个 64 KiB 块。
                assert!(
                    bytes_read > MAX_UNCOMPRESSED_BYTES
                        && bytes_read <= MAX_UNCOMPRESSED_BYTES + 64 * 1024,
                    "bytes_read={bytes_read} should be within one chunk past the limit"
                );
            }
            other => panic!("expected budget error, got {other:?}"),
        }
    }

    #[test]
    fn path_checks_reject_and_kind_mapping() {
        assert_eq!(check_entry_name("../evil"), Err(PathUnsafeReason::ParentTraversal));
        assert_eq!(check_entry_name("/abs"), Err(PathUnsafeReason::Absolute));
        assert_eq!(check_entry_name("a\\b"), Err(PathUnsafeReason::Backslash));
        assert_eq!(check_entry_name(""), Err(PathUnsafeReason::Empty));
        assert_eq!(check_entry_name("a\0b"), Err(PathUnsafeReason::Nul));
        assert_eq!(check_entry_name("C:/x"), Err(PathUnsafeReason::Absolute));
        assert_eq!(check_entry_name("assets/a/b.png"), Ok(()));

        // 恶意 zip: 用等长占位名构造，验证装载阶段被拒且错误可区分。
        for (placeholder, evil, reason) in [
            ("aa/evil", "../evil", PathUnsafeReason::ParentTraversal),
            ("aaaa", "/abs", PathUnsafeReason::Absolute),
            ("abc", "a\\b", PathUnsafeReason::Backslash),
        ] {
            let mut entries = valid_entries();
            entries.push((placeholder, b"evil".to_vec()));
            let archive = patch_names(&zip_bytes(&entries), placeholder, evil);
            let err = CardPackage::open(archive).expect_err("unsafe path must be rejected");
            assert_eq!(
                err,
                PackageError::UnsafePath {
                    name: evil.to_string(),
                    reason
                },
                "case {evil}"
            );
            assert_eq!(err.kind(), match reason {
                PathUnsafeReason::ParentTraversal => "path-traversal",
                PathUnsafeReason::Absolute => "path-absolute",
                PathUnsafeReason::Backslash => "path-backslash",
                _ => unreachable!(),
            });
        }
    }

    #[test]
    fn rejects_missing_manifest_and_card() {
        let entries: Vec<(&str, Vec<u8>)> = vec![
            ("card.wasm", echo_wasm()),
            ("assets/logo.bin", b"x".to_vec()),
        ];
        let err = CardPackage::open(zip_bytes(&entries)).expect_err("missing manifest");
        assert_eq!(err, PackageError::MissingManifest);
        assert_eq!(err.kind(), "missing-manifest");

        let entries: Vec<(&str, Vec<u8>)> = vec![
            ("manifest.json", valid_manifest().into_bytes()),
            ("assets/logo.bin", b"x".to_vec()),
        ];
        let err = CardPackage::open(zip_bytes(&entries)).expect_err("missing card.wasm");
        assert_eq!(err, PackageError::MissingCardWasm);
        assert_eq!(err.kind(), "missing-card-wasm");
    }

    #[test]
    fn rejects_non_wasm_runtime_and_wrong_abi() {
        let entries: Vec<(&str, Vec<u8>)> = vec![
            ("manifest.json", manifest_with("js", 1).into_bytes()),
            ("card.wasm", echo_wasm()),
        ];
        let err = CardPackage::open(zip_bytes(&entries)).expect_err("runtime=js must be rejected");
        assert!(matches!(
            err,
            PackageError::Manifest(ManifestError::UnsupportedRuntime(ref runtime)) if runtime == "js"
        ));
        assert_eq!(err.kind(), "manifest-unsupported-runtime");

        let entries: Vec<(&str, Vec<u8>)> = vec![
            ("manifest.json", manifest_with("wasm", 2).into_bytes()),
            ("card.wasm", echo_wasm()),
        ];
        let err = CardPackage::open(zip_bytes(&entries)).expect_err("abi_version=2 must be rejected");
        assert!(matches!(
            err,
            PackageError::Manifest(ManifestError::AbiVersionMismatch { expected: 1, found: Some(2) })
        ));
        assert_eq!(err.kind(), "manifest-abi-mismatch");
    }

    #[test]
    fn unknown_entry_is_reported() {
        for name in ["extra.txt", "src/main.rs", "assets"] {
            let mut entries = valid_entries();
            entries.push((name, b"nope".to_vec()));
            let err = CardPackage::open(zip_bytes(&entries))
                .expect_err("unknown entry must be rejected");
            assert_eq!(
                err,
                PackageError::UnknownEntry {
                    name: name.to_string()
                }
            );
            assert_eq!(err.kind(), "unknown-entry");
        }
    }

    #[test]
    fn rejects_duplicate_entries() {
        // zip 写入器拒绝重名，所以用等长占位名构造（b.bin / c.bin），再把 c 改成 b。
        let mut entries = valid_entries();
        entries.push(("assets/b.bin", b"first copy".to_vec()));
        entries.push(("assets/c.bin", b"second copy".to_vec()));
        let archive = patch_names(&zip_bytes(&entries), "assets/c.bin", "assets/b.bin");
        let err = CardPackage::open(archive).expect_err("duplicate entry");
        assert_eq!(
            err,
            PackageError::DuplicateEntry {
                name: "assets/b.bin".to_string()
            }
        );
        assert_eq!(err.kind(), "duplicate-entry");
    }

    #[test]
    fn rejects_symlink_entries() {
        let mut writer = ZipWriter::new(Cursor::new(Vec::new()));
        let options = SimpleFileOptions::default();
        for (name, data) in valid_entries() {
            writer.start_file(name, options).unwrap();
            writer.write_all(&data).unwrap();
        }
        writer
            .add_symlink("assets/link", "../../etc/passwd", options)
            .expect("add_symlink");
        let archive = writer.finish().unwrap().into_inner();

        let err = CardPackage::open(archive).expect_err("symlink entry must be rejected");
        assert_eq!(
            err,
            PackageError::SymlinkEntry {
                name: "assets/link".to_string()
            }
        );
        assert_eq!(err.kind(), "symlink-entry");
    }

    #[test]
    fn sha256_verifies_whole_archive() {
        let archive = zip_bytes(&valid_entries());
        let expected = sha256_hex(&archive);
        assert_eq!(expected.len(), 64);
        let package = CardPackage::open(archive).expect("valid package");
        package.verify_sha256(&expected).expect("correct sha256");
        package
            .verify_sha256(&format!("sha256:{}", expected.to_uppercase()))
            .expect("prefix + uppercase accepted");

        let mut wrong = expected.clone();
        wrong.replace_range(0..1, if expected.starts_with('0') { "1" } else { "0" });
        let err = package
            .verify_sha256(&wrong)
            .expect_err("wrong sha256 must fail");
        assert!(
            matches!(err, PackageError::Sha256Mismatch { ref expected, ref actual } if *expected == wrong && *actual == package.archive_sha256)
        );
        assert_eq!(err.kind(), "sha256-mismatch");

        assert_eq!(
            package.verify_sha256("not-a-hash").unwrap_err().kind(),
            "sha256-invalid"
        );
    }

    /// 磁盘上的超大 .kkcard：metadata 预检在读文件之前就拒（不会先读进内存）。
    #[test]
    fn rejects_oversized_archive_on_disk_before_reading() {
        let dir = std::path::Path::new("target/pkg-tests");
        std::fs::create_dir_all(dir).unwrap();
        let path = dir.join("too-big.kkcard");
        let file = std::fs::File::create(&path).unwrap();
        let oversized = (MAX_ARCHIVE_BYTES + 4096) as u64;
        // 稀疏文件：8 MiB+4 KiB 的逻辑长度，几乎不占盘；若实现先 fs::read 再判大小，
        // 这里就会先把 8 MiB+4 KiB 读进内存。
        file.set_len(oversized).unwrap();
        drop(file);

        let err = CardPackage::open_path(&path).expect_err("oversized file must be rejected");
        assert_eq!(
            err,
            PackageError::ArchiveTooLarge {
                size: oversized as usize,
                limit: MAX_ARCHIVE_BYTES,
            },
            "size must come from file metadata, before any read"
        );
        assert_eq!(err.kind(), "archive-too-large");

        // 目录路径 → io 错误（不是 panic、不是 zip 解析错误）
        assert_eq!(
            CardPackage::open_path(dir).unwrap_err().kind(),
            "io"
        );
    }

    /// 恰好 256 个条目（manifest + card + 254 assets）必须能装载：上限是 <= 256。
    #[test]
    fn accepts_exactly_max_entries() {
        let mut entries: Vec<(String, Vec<u8>)> = vec![
            ("manifest.json".to_string(), valid_manifest().into_bytes()),
            ("card.wasm".to_string(), echo_wasm()),
        ];
        for index in 0..254 {
            entries.push((format!("assets/f{index:03}.bin"), vec![b'x'; 8]));
        }
        assert_eq!(entries.len(), MAX_ENTRIES);

        let refs: Vec<(&str, Vec<u8>)> = entries
            .iter()
            .map(|(name, data)| (name.as_str(), data.clone()))
            .collect();
        let package =
            CardPackage::open(zip_bytes(&refs)).expect("exactly 256 entries must load");
        assert_eq!(package.assets.len(), 254);
        assert_eq!(package.manifest.name.as_deref(), Some("pkg-card"));
    }

    /// 解压总量恰好 32 MiB 必须成功；多 1 字节就必须 UncompressedBudgetExceeded。
    #[test]
    fn accepts_exactly_max_uncompressed_and_rejects_one_more_byte() {
        let manifest = valid_manifest().into_bytes();
        let wasm = echo_wasm();
        let remaining = MAX_UNCOMPRESSED_BYTES - (manifest.len() + wasm.len()) as u64;
        let per = (remaining / 4) as usize;
        let rem = (remaining % 4) as usize;

        let mut entries: Vec<(String, Vec<u8>)> = vec![
            ("manifest.json".to_string(), manifest),
            ("card.wasm".to_string(), wasm),
        ];
        for (index, name) in ["assets/a.bin", "assets/b.bin", "assets/c.bin", "assets/d.bin"]
            .iter()
            .enumerate()
        {
            let size = if index == 3 { per + rem } else { per };
            entries.push((name.to_string(), vec![0u8; size]));
        }

        let refs: Vec<(&str, Vec<u8>)> = entries
            .iter()
            .map(|(name, data)| (name.as_str(), data.clone()))
            .collect();
        let archive = zip_bytes(&refs);
        assert!(
            archive.len() < MAX_ARCHIVE_BYTES,
            "32 MiB of zeros must compress below 8 MiB (got {})",
            archive.len()
        );
        let package = CardPackage::open(archive).expect("exactly 32 MiB must load");
        let total: u64 = package.wasm_bytes.len() as u64
            + package.manifest_json.len() as u64
            + package.assets.values().map(|bytes| bytes.len() as u64).sum::<u64>();
        assert_eq!(total, MAX_UNCOMPRESSED_BYTES);

        // 再多 1 字节 → 越界
        let mut entries_with_extra = entries.clone();
        entries_with_extra.push(("assets/extra.bin".to_string(), vec![0u8; 1]));
        let refs: Vec<(&str, Vec<u8>)> = entries_with_extra
            .iter()
            .map(|(name, data)| (name.as_str(), data.clone()))
            .collect();
        let err = CardPackage::open(zip_bytes(&refs)).expect_err("32 MiB + 1 must be rejected");
        assert!(
            matches!(
                err,
                PackageError::UncompressedBudgetExceeded { ref entry, .. } if entry == "assets/extra.bin"
            ),
            "unexpected error: {err:?}"
        );
    }

    /// EOCD 注释里塞伪签名：解析器与字节扫描读到的条目数不一致，必须 fail-closed。
    #[test]
    fn rejects_eocd_comment_with_embedded_fake_signature() {
        let bodies = valid_entries();
        let mut bytes = zip_bytes(&bodies);
        let eocd = find_last_eocd(&bytes).expect("real EOCD must exist");
        let real_count = read_u16(&bytes, eocd + 10).unwrap() as usize;
        assert_eq!(real_count, bodies.len());
        let cd_size = read_u32(&bytes, eocd + 12).unwrap();
        let cd_offset = read_u32(&bytes, eocd + 16).unwrap();

        // 伪 EOCD：条目数少报（3 vs 4），comment_length 故意写坏（4，但文件尾没有对应注释）。
        let mut fake = [0u8; 22];
        fake[0..4].copy_from_slice(&0x0605_4b50u32.to_le_bytes());
        fake[8..10].copy_from_slice(&(real_count as u16 - 1).to_le_bytes());
        fake[10..12].copy_from_slice(&(real_count as u16 - 1).to_le_bytes());
        fake[12..16].copy_from_slice(&cd_size.to_le_bytes());
        fake[16..20].copy_from_slice(&cd_offset.to_le_bytes());
        fake[20..22].copy_from_slice(&4u16.to_le_bytes());

        // 真实 EOCD 的 comment_length 0 -> 22，并追加伪 EOCD（让它成为文件里最后一个签名）
        bytes[eocd + 20..eocd + 22].copy_from_slice(&22u16.to_le_bytes());
        bytes.extend_from_slice(&fake);

        let err = CardPackage::open(bytes)
            .expect_err("embedded fake EOCD signature must fail closed");
        // 伪 EOCD 少报 1 个条目：字节扫描得 3，zip crate 解析真实 EOCD 得 4 -> 一致性校验拦下。
        assert_eq!(
            err,
            PackageError::Zip("central directory count mismatch".to_string()),
            "expected the fail-closed count check, got {err:?}"
        );
    }

    fn find_last_eocd(bytes: &[u8]) -> Option<usize> {
        if bytes.len() < 22 {
            return None;
        }
        let mut cursor = bytes.len() - 22;
        loop {
            if read_u32(bytes, cursor) == Some(0x0605_4b50) {
                return Some(cursor);
            }
            if cursor == 0 {
                return None;
            }
            cursor -= 1;
        }
    }

    /// 为 CLI 验证生成包样例（只在设置 DOCSCARD_EMIT_FIXTURE 时写盘）。
    #[test]
    fn emit_package_cli_fixture() {
        if std::env::var("DOCSCARD_EMIT_FIXTURE").is_err() {
            return;
        }
        let dir = std::path::Path::new("target/cli-fixture");
        std::fs::create_dir_all(dir).expect("create fixture dir");
        std::fs::write(dir.join("good.kkcard"), zip_bytes(&valid_entries())).expect("good.kkcard");

        let bad: Vec<(&str, Vec<u8>)> = vec![
            ("manifest.json", manifest_with("js", 1).into_bytes()),
            ("card.wasm", echo_wasm()),
        ];
        std::fs::write(dir.join("bad.kkcard"), zip_bytes(&bad)).expect("bad.kkcard");
    }
}
