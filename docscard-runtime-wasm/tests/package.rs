//! .kkcard 集成测试（spec: `docs/card-runtime-wasm.md` `4 包格式 + `9 安全与资源限额）。
//!
//! 与 `src/package.rs` 的单测互补：这里只走**公开 API**（`CardPackage` / `PackageError`），
//! 并用 `zip` crate 在测试进程内现场构造 .kkcard，卡片本体用 `wat` 编译
//! `fixtures/echo_card.wat`，不依赖磁盘上的二进制资产。
//!
//! 覆盖 13 类场景，每类一条独立 `#[test]`（无 `#[ignore]`）：
//!
//! | 场景 | 测试 |
//! |---|---|
//! | 合法包（含 assets/locales）+ into_runtime 端到端 | `valid_package_with_assets_loads_and_runs` |
//! | 压缩包 > 8 MiB | `compressed_archive_over_8mib_is_rejected` |
//! | 解压炸弹 > 32 MiB（边读边扣预算） | `zip_bomb_over_32mib_uncompressed_is_rejected` |
//! | 条目数 > 256 | `too_many_entries_is_rejected` |
//! | zip slip：`../evil` | `zip_slip_parent_traversal_is_rejected` |
//! | zip slip：`/abs` | `zip_slip_absolute_path_is_rejected` |
//! | zip slip：反斜杠 | `zip_slip_backslash_is_rejected` |
//! | 缺 manifest.json | `missing_manifest_is_rejected` |
//! | 缺 card.wasm | `missing_card_wasm_is_rejected` |
//! | runtime = js | `non_wasm_runtime_is_rejected` |
//! | abi_version = 2 | `wrong_abi_version_is_rejected` |
//! | 未知顶层条目 | `unknown_top_level_entry_is_rejected` |
//! | 重名条目 | `duplicate_entry_is_rejected` |
//! | 符号链接条目 | `symlink_entry_is_rejected` |
//! | sha256 正确 | `sha256_matching_package_is_accepted` |
//! | sha256 错误 / 格式非法 | `sha256_mismatch_and_malformed_are_rejected` |
//!
//! 另有一条 `open_path_reads_disk_and_reports_io` 覆盖磁盘装载与 io 错误类别。

use std::io::{Cursor, Write};
use std::path::{Path, PathBuf};

use docscard_runtime_wasm::manifest::ManifestError;
use docscard_runtime_wasm::package::{
    check_entry_name, sha256_hex, CardPackage, PackageError, PathUnsafeReason, MAX_ARCHIVE_BYTES,
    MAX_ENTRIES, MAX_UNCOMPRESSED_BYTES,
};
use docscard_runtime_wasm::DefaultHost;
use sha2::{Digest, Sha256};
use zip::write::SimpleFileOptions;
use zip::ZipWriter;

// ---------------------------------------------------------------- 构造工具

fn fixture_path(name: &str) -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("fixtures")
        .join(name)
}

/// 编译 `fixtures/echo_card.wat`：六个 ABI v1 export 齐全、原样回显事件。
fn echo_card_wasm() -> Vec<u8> {
    let path = fixture_path("echo_card.wat");
    let text = std::fs::read_to_string(&path)
        .unwrap_or_else(|err| panic!("cannot read {}: {err}", path.display()));
    wat::parse_str(&text).unwrap_or_else(|err| panic!("{} is not valid WAT: {err}", path.display()))
}

fn manifest_json(runtime: &str, abi_version: u32) -> String {
    serde_json::json!({
        "name": "pkg-card",
        "version": "1.0.0",
        "runtime": runtime,
        "abi_version": abi_version,
        "capabilities": ["haptics"],
        "limits": { "memory_mb": 16, "timeout_ms": 1000, "output_bytes": 65536 },
    })
    .to_string()
}

fn valid_manifest() -> String {
    manifest_json("wasm", 1)
}

const LOGO_BYTES: &[u8] = b"\x89PNG-asset-bytes";
const ZH_JSON: &str = r#"{"hello":"世界"}"#;

/// 一张合法的 .kkcard 内容清单（manifest + wasm + assets + locales）。
fn valid_entries() -> Vec<(&'static str, Vec<u8>)> {
    vec![
        ("manifest.json", valid_manifest().into_bytes()),
        ("card.wasm", echo_card_wasm()),
        ("assets/logo.bin", LOGO_BYTES.to_vec()),
        ("locales/zh.json", ZH_JSON.as_bytes().to_vec()),
    ]
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

/// 把包里所有出现 `from` 的条目名改成 `to`（**等长**，用于构造 zip slip / 重名）。
///
/// `zip::ZipWriter` 会拒绝重名并规范化反斜杠这类名字，所以只能「先写安全占位名，
/// 再在字节层面把 local header 与 central directory 里的名字一起改掉」。
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
    assert!(
        hits >= 2,
        "entry name must appear in both local header and central directory (hits={hits})"
    );
    out
}

/// 独立实现的 sha256（不经过被测 crate），用于交叉验证 `CardPackage::archive_sha256`。
fn sha256_independent(bytes: &[u8]) -> String {
    let digest = Sha256::digest(bytes);
    digest.iter().map(|byte| format!("{byte:02x}")).collect()
}

// ---------------------------------------------------------------- 1. 合法包 + 端到端

#[test]
fn valid_package_with_assets_loads_and_runs() {
    let archive = zip_bytes(&valid_entries());

    // 1) 装载 + 结构校验
    let package = CardPackage::open(archive.clone()).expect("valid package must load");
    assert_eq!(package.manifest.name.as_deref(), Some("pkg-card"));
    assert_eq!(package.manifest.abi_version, Some(1));
    assert!(!package.wasm_bytes.is_empty(), "card.wasm must be carried");
    assert_eq!(package.asset("assets/logo.bin"), Some(LOGO_BYTES));
    assert_eq!(package.asset("locales/zh.json"), Some(ZH_JSON.as_bytes()));
    assert_eq!(package.assets.len(), 2, "assets map holds assets/ + locales/");
    assert_eq!(
        package.assets.keys().cloned().collect::<Vec<_>>(),
        vec!["assets/logo.bin".to_string(), "locales/zh.json".to_string()]
    );
    assert_eq!(package.archive_sha256, sha256_independent(&archive));
    assert_eq!(package.archive_sha256, sha256_hex(&archive));
    assert_eq!(package.asset("assets/missing.bin"), None);

    // 2) into_runtime：包内 manifest + card.wasm 直接实例化并跑事件
    let mut runtime = package
        .into_runtime(DefaultHost::new())
        .expect("package card must instantiate");
    runtime
        .init(&package.manifest_json)
        .expect("kk_init must return 0");
    let event = r#"{"type":"tick","payload":{"n":7,"text":"卡片"}}"#;
    assert_eq!(runtime.handle(event).expect("kk_handle must echo"), event);
    runtime.shutdown().expect("kk_shutdown must return 0");
}

#[test]
fn open_path_reads_disk_and_reports_io() {
    let dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("target/package-tests");
    std::fs::create_dir_all(&dir).expect("create temp dir");
    let path = dir.join("valid.kkcard");
    std::fs::write(&path, zip_bytes(&valid_entries())).expect("write package");

    let package = CardPackage::open_path(&path).expect("open_path must load the package");
    assert_eq!(package.manifest.name.as_deref(), Some("pkg-card"));
    assert_eq!(package.assets.len(), 2);

    let err = CardPackage::open_path(dir.join("missing.kkcard"))
        .expect_err("missing file must be an io error");
    assert!(matches!(err, PackageError::Io(_)), "unexpected: {err:?}");
    assert_eq!(err.kind(), "io");
}

// ---------------------------------------------------------------- 2. 压缩包大小上限

#[test]
fn compressed_archive_over_8mib_is_rejected() {
    // 大小闸门在 zip 解析之前：不必是真的 zip，超过 8 MiB 就拒。
    let bytes = vec![0u8; MAX_ARCHIVE_BYTES + 1];
    let err = CardPackage::open(bytes).expect_err("archive > 8 MiB must be rejected");
    assert!(
        matches!(err, PackageError::ArchiveTooLarge { size, limit }
            if size == MAX_ARCHIVE_BYTES + 1 && limit == MAX_ARCHIVE_BYTES),
        "unexpected: {err:?}"
    );
    assert_eq!(err.kind(), "archive-too-large");
    assert!(
        err.to_string().contains("8 MiB"),
        "message must name the limit: {err}"
    );

    // 边界：正好 8 MiB 不因大小被拒（随后因不是 zip 而以 zip 类别失败）。
    let err =
        CardPackage::open(vec![0u8; MAX_ARCHIVE_BYTES]).expect_err("8 MiB of zeros is not a zip");
    assert_eq!(err.kind(), "zip");
}

// ---------------------------------------------------------------- 3. 解压预算（zip bomb）

#[test]
fn zip_bomb_over_32mib_uncompressed_is_rejected() {
    // 5 x 8 MiB 全 0：压缩后几十 KB，解压后 40 MiB > 32 MiB 预算。
    let zeros = vec![0u8; 8 * 1024 * 1024];
    let mut entries: Vec<(&str, Vec<u8>)> = vec![
        ("manifest.json", valid_manifest().into_bytes()),
        ("card.wasm", echo_card_wasm()),
    ];
    for name in [
        "assets/a.bin",
        "assets/b.bin",
        "assets/c.bin",
        "assets/d.bin",
        "assets/e.bin",
    ] {
        entries.push((name, zeros.clone()));
    }
    let archive = zip_bytes(&entries);
    assert!(
        archive.len() < 1024 * 1024,
        "bomb must stay under the 8 MiB archive limit while expanding to 40 MiB ({} bytes)",
        archive.len()
    );

    let err = CardPackage::open(archive).expect_err("zip bomb must be rejected");
    match &err {
        PackageError::UncompressedBudgetExceeded {
            entry,
            bytes_read,
            limit,
        } => {
            assert_eq!(*limit, MAX_UNCOMPRESSED_BYTES);
            assert_eq!(*limit, 32 * 1024 * 1024);
            // 关键证据：读到预算边界就停，没有把 40 MiB 全解出来。
            // `bytes_read` 是**跨条目全局累计已读字节数**（先记账再判预算），因此含触发越界的那一块，
            // 最多越过一个 64 KiB 块，见 src/package.rs 的 read_entry_capped 注释。
            assert!(
                *bytes_read > MAX_UNCOMPRESSED_BYTES
                    && *bytes_read <= MAX_UNCOMPRESSED_BYTES + 64 * 1024,
                "must stop within one chunk past the budget, stopped at {bytes_read} while reading {entry}"
            );
            assert!(entry.starts_with("assets/"), "unexpected entry: {entry}");
        }
        other => panic!("expected budget error, got {other:?}"),
    }
    assert_eq!(err.kind(), "uncompressed-budget-exceeded");
}

// ---------------------------------------------------------------- 4. 条目数上限

#[test]
fn too_many_entries_is_rejected() {
    // manifest + card.wasm + 256 个 asset = 258 条目 > 256。
    let mut entries: Vec<(String, Vec<u8>)> = vec![
        ("manifest.json".to_string(), valid_manifest().into_bytes()),
        ("card.wasm".to_string(), echo_card_wasm()),
    ];
    for index in 0..MAX_ENTRIES {
        entries.push((format!("assets/f{index}.bin"), b"x".to_vec()));
    }
    let refs: Vec<(&str, Vec<u8>)> = entries
        .iter()
        .map(|(name, data)| (name.as_str(), data.clone()))
        .collect();
    let err = CardPackage::open(zip_bytes(&refs)).expect_err(">=257 entries must be rejected");
    assert!(
        matches!(err, PackageError::TooManyEntries { count, limit }
            if count > MAX_ENTRIES && limit == MAX_ENTRIES),
        "unexpected: {err:?}"
    );
    assert_eq!(err.kind(), "too-many-entries");
}

// ---------------------------------------------------------------- 5-7. zip slip

#[test]
fn zip_slip_parent_traversal_is_rejected() {
    let placeholder = "aa/evil";
    let evil = "../evil";
    let mut entries = valid_entries();
    entries.push((placeholder, b"evil".to_vec()));
    let archive = patch_names(&zip_bytes(&entries), placeholder, evil);

    let err = CardPackage::open(archive).expect_err("parent traversal must be rejected");
    assert_eq!(
        err,
        PackageError::UnsafePath {
            name: evil.to_string(),
            reason: PathUnsafeReason::ParentTraversal
        }
    );
    assert_eq!(err.kind(), "path-traversal");
    assert_eq!(check_entry_name(evil), Err(PathUnsafeReason::ParentTraversal));
    assert_eq!(
        check_entry_name("assets/../evil"),
        Err(PathUnsafeReason::ParentTraversal)
    );
}

#[test]
fn zip_slip_absolute_path_is_rejected() {
    let placeholder = "aaaa";
    let evil = "/abs";
    let mut entries = valid_entries();
    entries.push((placeholder, b"evil".to_vec()));
    let archive = patch_names(&zip_bytes(&entries), placeholder, evil);

    let err = CardPackage::open(archive).expect_err("absolute path must be rejected");
    assert_eq!(
        err,
        PackageError::UnsafePath {
            name: evil.to_string(),
            reason: PathUnsafeReason::Absolute
        }
    );
    assert_eq!(err.kind(), "path-absolute");
    assert_eq!(check_entry_name("/abs"), Err(PathUnsafeReason::Absolute));
    assert_eq!(check_entry_name("C:/x"), Err(PathUnsafeReason::Absolute));
}

#[test]
fn zip_slip_backslash_is_rejected() {
    let placeholder = "abc";
    let evil = "a\\b";
    let mut entries = valid_entries();
    entries.push((placeholder, b"evil".to_vec()));
    let archive = patch_names(&zip_bytes(&entries), placeholder, evil);

    let err = CardPackage::open(archive).expect_err("backslash path must be rejected");
    assert_eq!(
        err,
        PackageError::UnsafePath {
            name: evil.to_string(),
            reason: PathUnsafeReason::Backslash
        }
    );
    assert_eq!(err.kind(), "path-backslash");
    assert_eq!(check_entry_name("a\\b"), Err(PathUnsafeReason::Backslash));
    // 安全路径必须放行（否则上面的拒绝可能来自「什么都拒」）。
    assert_eq!(check_entry_name("assets/img/a.png"), Ok(()));
}

// ---------------------------------------------------------------- 8-9. 缺必填条目

#[test]
fn missing_manifest_is_rejected() {
    let entries: Vec<(&str, Vec<u8>)> = vec![
        ("card.wasm", echo_card_wasm()),
        ("assets/logo.bin", LOGO_BYTES.to_vec()),
    ];
    let err = CardPackage::open(zip_bytes(&entries)).expect_err("manifest.json is required");
    assert_eq!(err, PackageError::MissingManifest);
    assert_eq!(err.kind(), "missing-manifest");
}

#[test]
fn missing_card_wasm_is_rejected() {
    let entries: Vec<(&str, Vec<u8>)> = vec![
        ("manifest.json", valid_manifest().into_bytes()),
        ("assets/logo.bin", LOGO_BYTES.to_vec()),
    ];
    let err = CardPackage::open(zip_bytes(&entries)).expect_err("card.wasm is required");
    assert_eq!(err, PackageError::MissingCardWasm);
    assert_eq!(err.kind(), "missing-card-wasm");
}

// ---------------------------------------------------------------- 10-11. manifest 校验

#[test]
fn non_wasm_runtime_is_rejected() {
    let entries: Vec<(&str, Vec<u8>)> = vec![
        ("manifest.json", manifest_json("js", 1).into_bytes()),
        ("card.wasm", echo_card_wasm()),
    ];
    let err =
        CardPackage::open(zip_bytes(&entries)).expect_err("runtime = js belongs to the old engine");
    assert!(
        matches!(err, PackageError::Manifest(ManifestError::UnsupportedRuntime(ref runtime)) if runtime == "js"),
        "unexpected: {err:?}"
    );
    assert_eq!(err.kind(), "manifest-unsupported-runtime");
}

#[test]
fn wrong_abi_version_is_rejected() {
    let entries: Vec<(&str, Vec<u8>)> = vec![
        ("manifest.json", manifest_json("wasm", 2).into_bytes()),
        ("card.wasm", echo_card_wasm()),
    ];
    let err = CardPackage::open(zip_bytes(&entries)).expect_err("abi_version must be 1");
    assert_eq!(
        err,
        PackageError::Manifest(ManifestError::AbiVersionMismatch {
            expected: 1,
            found: Some(2)
        })
    );
    assert_eq!(err.kind(), "manifest-abi-mismatch");
}

// ---------------------------------------------------------------- 12-14. 结构类拒绝

#[test]
fn unknown_top_level_entry_is_rejected() {
    // 顶层白名单只有 manifest.json / card.wasm / assets/** / locales/**。
    for name in ["extra.txt", "src/main.rs", "assets", "locales"] {
        let mut entries = valid_entries();
        entries.push((name, b"nope".to_vec()));
        let err = CardPackage::open(zip_bytes(&entries))
            .expect_err("entries outside the whitelist must be rejected");
        assert_eq!(
            err,
            PackageError::UnknownEntry {
                name: name.to_string()
            },
            "case {name}"
        );
        assert_eq!(err.kind(), "unknown-entry");
    }
}

#[test]
fn duplicate_entry_is_rejected() {
    // zip 写入器拒绝重名：先把第二份写成等长占位名 assets/c.bin，再在字节层改成 assets/b.bin。
    let mut entries = valid_entries();
    entries.push(("assets/b.bin", b"first copy".to_vec()));
    entries.push(("assets/c.bin", b"second copy".to_vec()));
    let archive = patch_names(&zip_bytes(&entries), "assets/c.bin", "assets/b.bin");

    let err = CardPackage::open(archive).expect_err("duplicate entry must be rejected");
    assert_eq!(
        err,
        PackageError::DuplicateEntry {
            name: "assets/b.bin".to_string()
        }
    );
    assert_eq!(err.kind(), "duplicate-entry");
}

#[test]
fn symlink_entry_is_rejected() {
    let mut writer = ZipWriter::new(Cursor::new(Vec::new()));
    let options = SimpleFileOptions::default();
    for (name, data) in valid_entries() {
        writer.start_file(name, options).expect("start_file");
        writer.write_all(&data).expect("write entry");
    }
    writer
        .add_symlink("assets/link", "../../etc/passwd", options)
        .expect("add_symlink");
    let archive = writer.finish().expect("finish zip").into_inner();

    let err = CardPackage::open(archive).expect_err("symlink entries must be rejected");
    assert_eq!(
        err,
        PackageError::SymlinkEntry {
            name: "assets/link".to_string()
        }
    );
    assert_eq!(err.kind(), "symlink-entry");
}

// ---------------------------------------------------------------- 15-16. sha256

#[test]
fn sha256_matching_package_is_accepted() {
    let archive = zip_bytes(&valid_entries());
    let expected = sha256_independent(&archive);
    assert_eq!(expected.len(), 64);
    let package = CardPackage::open(archive).expect("valid package");

    package
        .verify_sha256(&expected)
        .expect("exact sha256 must pass");
    package
        .verify_sha256(&format!("sha256:{}", expected.to_uppercase()))
        .expect("sha256: prefix + uppercase must pass");
    package
        .verify_sha256(&format!("  {expected}  "))
        .expect("surrounding whitespace must be trimmed");
}

#[test]
fn sha256_mismatch_and_malformed_are_rejected() {
    let archive = zip_bytes(&valid_entries());
    let expected = sha256_independent(&archive);
    let package = CardPackage::open(archive).expect("valid package");

    // 改一个 hex 位 -> 不匹配，错误里带 expected/actual。
    let mut wrong = expected.clone();
    wrong.replace_range(0..1, if expected.starts_with('0') { "1" } else { "0" });
    let err = package
        .verify_sha256(&wrong)
        .expect_err("wrong sha256 must fail");
    match &err {
        PackageError::Sha256Mismatch {
            expected: reported,
            actual,
        } => {
            assert_eq!(reported, &wrong);
            assert_eq!(actual, &package.archive_sha256);
        }
        other => panic!("expected Sha256Mismatch, got {other:?}"),
    }
    assert_eq!(err.kind(), "sha256-mismatch");
    assert!(err.to_string().contains("sha256 mismatch"), "{err}");

    // 格式非法 -> InvalidSha256，不被当成 mismatch。
    for bad in ["not-a-hash", "deadbeef", &expected[..63]] {
        let err = package
            .verify_sha256(bad)
            .expect_err("malformed sha256 must be rejected");
        assert!(
            matches!(err, PackageError::InvalidSha256(ref value) if value == bad),
            "unexpected for {bad:?}: {err:?}"
        );
        assert_eq!(err.kind(), "sha256-invalid");
    }
}

// ---------------------------------------------------------------- 附加：目录条目

#[test]
fn directory_entries_are_skipped_and_not_assets() {
    // 以 `/` 结尾的目录条目不是"未知顶层条目"：只做路径校验，然后跳过（不进 assets）。
    let mut writer = ZipWriter::new(Cursor::new(Vec::new()));
    let options = SimpleFileOptions::default();
    writer.add_directory("assets/", options).expect("dir entry");
    writer.add_directory("assets/img/", options).expect("nested dir entry");
    for (name, data) in valid_entries() {
        writer.start_file(name, options).expect("start_file");
        writer.write_all(&data).expect("write entry");
    }
    let archive = writer.finish().expect("finish zip").into_inner();

    let package = CardPackage::open(archive).expect("directory entries must be allowed");
    assert_eq!(package.assets.len(), 2, "directories do not become assets");
    assert!(!package.assets.contains_key("assets/"));
}
