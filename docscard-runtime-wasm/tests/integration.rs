//! 集成测试：用 `wat` crate 编译 `fixtures/*.wat`，再交给 `docscard-runtime-wasm` 引擎执行。
//!
//! 对应规格：`docs/card-runtime-wasm.md` §5（WASM 执行器选型）、§6（WASM ABI v1）、
//! §9（安全与资源限额）。ABI 细节见 `docs/ABI.md`。
//!
//! 每个验收场景一条独立 `#[test]`，没有 `#[ignore]`：
//!
//! | 测试 | 场景 |
//! |---|---|
//! | `normal_lifecycle_init_handle_shutdown` | 正常 init / handle / shutdown |
//! | `undeclared_capability_is_denied` | 能力白名单拒绝 |
//! | `declared_capability_reaches_host` | 白名单命中（cap = 声明列表下标） |
//! | `infinite_loop_times_out` | 超时（fuel 耗尽） |
//! | `memory_limit_caps_memory_grow` | 内存上限（运行时 memory.grow） |
//! | `oversized_initial_memory_is_rejected` | 内存上限（实例化阶段） |
//! | `output_bytes_rejects_oversized_result` | output_bytes 上限 |
//! | `card_error_carries_last_error_json` | 错误码 + kk_last_error |
//! | `invalid_manifests_are_rejected` | 非法 manifest 拒绝 |
//! | `fixtures_conform_to_abi_v1` | 样例卡片全集可加载且 init/shutdown 可调用 |

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use docscard_runtime_wasm::abi;
use docscard_runtime_wasm::{CardHost, CardRuntime, DefaultHost, HostError, ManifestError, RuntimeError};

// ---------------------------------------------------------------- 基础设施

/// `fixtures/<name>` 的绝对路径（`CARGO_MANIFEST_DIR` 在 cargo test 时注入）。
fn fixture_path(name: &str) -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("fixtures")
        .join(name)
}

/// 读 WAT 源并用 `wat` crate 编译成 wasm 字节；编译失败时把 WAT 原文带进 panic 信息。
fn compile_fixture(name: &str) -> Vec<u8> {
    let path = fixture_path(name);
    let text = std::fs::read_to_string(&path)
        .unwrap_or_else(|err| panic!("cannot read {}: {err}", path.display()));
    wat::parse_str(&text).unwrap_or_else(|err| panic!("{} is not valid WAT: {err}", path.display()))
}

/// 构造 ABI v1 manifest.json。`limits` = (memory_mb, timeout_ms, output_bytes)。
fn manifest(name: &str, capabilities: &[&str], limits: (u32, u64, usize)) -> String {
    let (memory_mb, timeout_ms, output_bytes) = limits;
    serde_json::json!({
        "name": name,
        "version": "1.0.0",
        "runtime": "wasm",
        "abi_version": 1,
        "capabilities": capabilities,
        "limits": {
            "memory_mb": memory_mb,
            "timeout_ms": timeout_ms,
            "output_bytes": output_bytes,
        },
    })
    .to_string()
}

/// 会记账的宿主：记录每次 `kk.host_call` 与 `kk.log`，用于断言
/// "能力被拒绝时根本不触达宿主实现"。
#[derive(Debug, Default)]
struct RecordingHost {
    calls: Vec<(String, String)>,
    reply: String,
    logs: Vec<(i32, String)>,
}

impl RecordingHost {
    fn new() -> Self {
        Self {
            calls: Vec::new(),
            reply: r#"{"host":"ok"}"#.to_string(),
            logs: Vec::new(),
        }
    }
}

impl CardHost for RecordingHost {
    fn host_call(&mut self, cap: &str, request_json: &str) -> Result<String, HostError> {
        self.calls.push((cap.to_string(), request_json.to_string()));
        Ok(self.reply.clone())
    }

    fn log(&mut self, level: i32, message: &str) {
        self.logs.push((level, message.to_string()));
    }
}

// ---------------------------------------------------------------- 场景 1：正常流程

#[test]
fn normal_lifecycle_init_handle_shutdown() {
    let wasm = compile_fixture("echo_card.wat");
    let manifest_json = manifest("echo-card", &[], (16, 1_000, 4096));
    let mut runtime = CardRuntime::from_manifest_json(&wasm, &manifest_json, RecordingHost::new())
        .expect("echo card must load");

    assert_eq!(runtime.card_name(), "echo-card");
    assert!(runtime.capabilities().is_empty(), "no capability declared");
    assert_eq!(runtime.host_call_failures(), 0);

    // kk_init(manifest_ptr, len) -> i32 == 0；卡片顺手把 manifest 原文 log 出来。
    runtime.init(&manifest_json).expect("kk_init must return 0");
    assert_eq!(runtime.logs().len(), 1, "kk_init logs the manifest once");
    assert_eq!(runtime.logs()[0].level, 3);
    assert_eq!(runtime.logs()[0].message, manifest_json);
    assert_eq!(runtime.host().logs, vec![(3, manifest_json.clone())]);
    assert!(runtime.last_error_json().is_none(), "no error yet");

    // kk_handle(event_ptr, len) -> (ptr << 32) | len：逐字节回显，中文也不失真。
    let event = r#"{"type":"tick","payload":{"n":7,"text":"卡片"}}"#;
    let result = runtime.handle(event).expect("kk_handle must echo the event");
    assert_eq!(result, event);
    let parsed: serde_json::Value = serde_json::from_str(&result).expect("result is JSON");
    assert_eq!(parsed["type"], "tick");
    assert_eq!(parsed["payload"]["n"], 7);

    // 同一实例可连续处理事件（每次 kk_alloc / kk_free 成对）。
    assert_eq!(runtime.handle("{}").expect("second handle"), "{}");
    assert_eq!(
        runtime.handle(r#"{"type":"ping"}"#).expect("third handle"),
        r#"{"type":"ping"}"#
    );

    runtime.shutdown().expect("kk_shutdown must return 0");
}

// ---------------------------------------------------------------- 场景 2：能力白名单拒绝

#[test]
fn undeclared_capability_is_denied() {
    let wasm = compile_fixture("undeclared_capability.wat");
    // 只声明 1 个能力 -> 合法下标只有 0；卡片请求 cap 索引 1 = 未声明。
    let manifest_json = manifest("denied-cap-card", &["haptics"], (16, 1_000, 4096));
    let mut runtime =
        CardRuntime::from_manifest_json(&wasm, &manifest_json, RecordingHost::new())
            .expect("capability probe must load");
    runtime.init(&manifest_json).expect("kk_init must return 0");

    let result = runtime
        .handle(r#"{"op":"net.fetch"}"#)
        .expect("card must turn the negative host_call into its own JSON");
    assert_eq!(result, r#"{"denied":true}"#);

    assert!(
        runtime.host().calls.is_empty(),
        "denied capability must never reach the host implementation, got: {:?}",
        runtime.host().calls
    );
    let failure = runtime.last_host_error().expect("denial must be recorded");
    assert_eq!(failure.code, abi::HOST_ERR_CAPABILITY_DENIED);
    assert_eq!(failure.code, 1, "HOST_ERR_CAPABILITY_DENIED is frozen as 1");
    assert_eq!(failure.capability, "cap#1");
    assert_eq!(runtime.host_call_failures(), 1);
    assert_eq!(runtime.capabilities().len(), 1);
}

// ---------------------------------------------------------------- 场景 3：白名单命中

#[test]
fn declared_capability_reaches_host() {
    let wasm = compile_fixture("undeclared_capability.wat");
    // 声明 2 个能力 -> cap 索引 1 = capabilities[1] = "clipboard"。
    let manifest_json = manifest("allowed-cap-card", &["haptics", "clipboard"], (16, 1_000, 4096));
    let mut runtime =
        CardRuntime::from_manifest_json(&wasm, &manifest_json, RecordingHost::new())
            .expect("capability probe must load");
    runtime.init(&manifest_json).expect("kk_init must return 0");

    let event = r#"{"op":"copy","text":"hi"}"#;
    let result = runtime.handle(event).expect("declared capability must pass");
    assert_eq!(result, r#"{"ok":true}"#);

    assert_eq!(runtime.host().calls.len(), 1);
    assert_eq!(runtime.host().calls[0].0, "clipboard");
    assert_eq!(runtime.host().calls[0].1, event);
    assert!(runtime.last_host_error().is_none());
    assert_eq!(runtime.host_call_failures(), 0);
}

// ---------------------------------------------------------------- 场景 4：超时

#[test]
fn infinite_loop_times_out() {
    let wasm = compile_fixture("spin_forever.wat");
    let manifest_json = manifest("spin-forever", &[], (16, 20, 4096));
    let mut runtime = CardRuntime::from_manifest_json(&wasm, &manifest_json, DefaultHost::new())
        .expect("spin card must load");
    runtime.init(&manifest_json).expect("kk_init must return 0");

    let started = Instant::now();
    let err = runtime
        .handle("{}")
        .expect_err("an infinite loop must be interrupted by the fuel budget");
    let elapsed = started.elapsed();

    assert_eq!(err.kind(), "timeout");
    assert_eq!(err, RuntimeError::Timeout { timeout_ms: 20 });
    assert!(
        elapsed < Duration::from_secs(5),
        "fuel interruption must not hang the host, took {elapsed:?}"
    );

    // 单次调用重置 fuel：第二次调用照样是 timeout，而不是 panic / 挂死。
    let again = runtime.handle("{}").expect_err("timeout is per call");
    assert_eq!(again.kind(), "timeout");
}

// ---------------------------------------------------------------- 场景 5：内存上限（grow）

#[test]
fn memory_limit_caps_memory_grow() {
    let wasm = compile_fixture("memory_hog.wat");
    // memory_mb=1 -> 16 页上限；卡片从 2 页开始 grow，最终恰好停在 16 页。
    let tight = manifest("memory-hog", &[], (1, 1_000, 4096));
    let mut runtime = CardRuntime::from_manifest_json(&wasm, &tight, DefaultHost::new())
        .expect("memory hog must load");
    assert_eq!(
        runtime.handle("{}").expect("grow loop must end at the limit"),
        r#"{"grow":"capped"}"#
    );

    // 反证：把上限放宽到 64 MiB（1024 页）后同一张卡能 grow 过 16 页。
    let roomy = manifest("memory-hog-roomy", &[], (64, 1_000, 4096));
    let mut runtime = CardRuntime::from_manifest_json(&wasm, &roomy, DefaultHost::new())
        .expect("memory hog must load");
    assert_eq!(
        runtime.handle("{}").expect("grow loop must end at 1024 pages"),
        r#"{"grow":"uncapped"}"#
    );
}

// ---------------------------------------------------------------- 场景 6：内存上限（实例化）

#[test]
fn oversized_initial_memory_is_rejected() {
    let wasm = compile_fixture("memory_bomb.wat");
    // 模块声明 64 页（4 MiB）初始内存，limit 只给 16 页 -> 实例化必须失败。
    let tight = manifest("memory-bomb", &[], (1, 1_000, 4096));
    let err = CardRuntime::from_manifest_json(&wasm, &tight, DefaultHost::new())
        .expect_err("64-page initial memory must exceed memory_mb = 1");
    assert_eq!(err.kind(), "instantiate");
    assert!(
        err.to_string().contains("instantiate") || err.to_string().contains("memory"),
        "error must explain the memory limit: {err}"
    );

    // 同一个模块在 memory_mb=8（128 页）下可以正常加载。
    let roomy = manifest("memory-bomb-roomy", &[], (8, 1_000, 4096));
    let mut runtime = CardRuntime::from_manifest_json(&wasm, &roomy, DefaultHost::new())
        .expect("64 pages fit in 8 MiB");
    runtime.init(&roomy).expect("kk_init must return 0");
    runtime.shutdown().expect("kk_shutdown must return 0");
}

// ---------------------------------------------------------------- 场景 7：output_bytes

#[test]
fn output_bytes_rejects_oversized_result() {
    let wasm = compile_fixture("oversized_output.wat");
    const RESULT_LEN: usize = 2010;

    let tight = manifest("oversized-output", &[], (16, 1_000, 256));
    let mut runtime = CardRuntime::from_manifest_json(&wasm, &tight, DefaultHost::new())
        .expect("oversized output card must load");
    let err = runtime
        .handle("{}")
        .expect_err("a 2010-byte result must be rejected when output_bytes = 256");
    assert_eq!(
        err,
        RuntimeError::OutputTooLarge {
            len: RESULT_LEN,
            limit: 256
        }
    );
    assert_eq!(err.kind(), "output_too_large");

    // 事件输入同样受 output_bytes 约束（防止宿主被超大事件拖死）。
    let oversized_event = "x".repeat(300);
    let err = runtime
        .handle(&oversized_event)
        .expect_err("a 300-byte event must be rejected when output_bytes = 256");
    assert_eq!(err, RuntimeError::InputTooLarge { len: 300, limit: 256 });

    // 放宽到 4096 后同一张卡正常返回，说明拒绝来自 limits 而不是卡片本身。
    let roomy = manifest("oversized-output-roomy", &[], (16, 1_000, 4096));
    let mut runtime = CardRuntime::from_manifest_json(&wasm, &roomy, DefaultHost::new())
        .expect("oversized output card must load");
    let result = runtime
        .handle("{}")
        .expect("2010 bytes < output_bytes = 4096 must pass");
    assert_eq!(result.len(), RESULT_LEN);
    let parsed: serde_json::Value = serde_json::from_str(&result).expect("result is JSON");
    assert_eq!(parsed["pad"].as_str().map(str::len), Some(2000));
}

// ---------------------------------------------------------------- 场景 8：错误码 + last_error

#[test]
fn card_error_carries_last_error_json() {
    let wasm = compile_fixture("error_card.wat");
    let manifest_json = manifest("error-card", &[], (16, 1_000, 4096));
    let mut runtime = CardRuntime::from_manifest_json(&wasm, &manifest_json, DefaultHost::new())
        .expect("error card must load");
    runtime.init(&manifest_json).expect("kk_init must return 0");

    let err = runtime
        .handle(r#"{"type":"bad"}"#)
        .expect_err("kk_handle returning -7 must fail the call");
    assert_eq!(
        err,
        RuntimeError::CardError {
            code: 7,
            export: abi::EXPORT_HANDLE,
            detail: Some(r#"{"error":"bad_event"}"#.to_string()),
        }
    );
    assert_eq!(err.kind(), "card_error");
    let shown = err.to_string();
    assert!(
        shown.contains('7') && shown.contains("bad_event"),
        "error message must carry the code and the card's last_error JSON: {shown}"
    );

    // 宿主可以单独读到同一份 kk_last_error() JSON（缓冲区归 guest，宿主不 free）。
    assert_eq!(
        runtime.last_error_json().as_deref(),
        Some(r#"{"error":"bad_event"}"#)
    );
}

// ---------------------------------------------------------------- 场景 9：非法 manifest

#[test]
fn invalid_manifests_are_rejected() {
    let wasm = compile_fixture("echo_card.wat");

    let err = CardRuntime::from_manifest_json(
        &wasm,
        r#"{"runtime":"js","abi_version":1}"#,
        DefaultHost::new(),
    )
    .expect_err("runtime = js belongs to the old engine");
    assert_eq!(err.kind(), "manifest");
    assert!(matches!(
        err,
        RuntimeError::Manifest(ManifestError::UnsupportedRuntime(ref runtime)) if runtime == "js"
    ));

    let err = CardRuntime::from_manifest_json(
        &wasm,
        r#"{"runtime":"wasm","abi_version":2}"#,
        DefaultHost::new(),
    )
    .expect_err("abi_version must be frozen at 1");
    assert!(matches!(
        err,
        RuntimeError::Manifest(ManifestError::AbiVersionMismatch {
            expected: 1,
            found: Some(2)
        })
    ));

    let err = CardRuntime::from_manifest_json(
        &wasm,
        r#"{"abi_version":1,"capabilities":["fs:root"]}"#,
        DefaultHost::new(),
    )
    .expect_err("unknown capability must be rejected, not silently ignored");
    assert!(matches!(
        err,
        RuntimeError::Manifest(ManifestError::UnknownCapability(ref cap)) if cap == "fs:root"
    ));

    let err = CardRuntime::from_manifest_json(
        &wasm,
        r#"{"abi_version":1,"capabilities":["net:"]}"#,
        DefaultHost::new(),
    )
    .expect_err("malformed net capability must be rejected");
    assert!(matches!(
        err,
        RuntimeError::Manifest(ManifestError::InvalidCapability(_))
    ));

    let err = CardRuntime::from_manifest_json(
        &wasm,
        r#"{"abi_version":1,"limits":{"timeout_ms":999999}}"#,
        DefaultHost::new(),
    )
    .expect_err("timeout_ms out of range must be rejected");
    assert!(matches!(
        err,
        RuntimeError::Manifest(ManifestError::LimitsOutOfRange {
            field: "timeout_ms",
            ..
        })
    ));

    let err = CardRuntime::from_manifest_json(&wasm, "not json", DefaultHost::new())
        .expect_err("invalid JSON must be rejected");
    assert!(matches!(
        err,
        RuntimeError::Manifest(ManifestError::InvalidJson(_))
    ));

    // 最小的合法 manifest：runtime 缺省 = wasm，limits 全缺省。
    let minimal = r#"{"abi_version":1}"#;
    let runtime = CardRuntime::from_manifest_json(&wasm, minimal, DefaultHost::new())
        .expect("abi_version = 1 is the only required field");
    assert!(runtime.capabilities().is_empty());
    assert_eq!(runtime.card_name(), "unnamed-card");
}

// ---------------------------------------------------------------- 样例卡片全集

#[test]
fn fixtures_conform_to_abi_v1() {
    let dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("fixtures");
    let mut names: Vec<String> = std::fs::read_dir(&dir)
        .expect("fixtures/ must exist")
        .map(|entry| entry.expect("readable dir entry").path())
        .filter(|path| path.extension().and_then(|ext| ext.to_str()) == Some("wat"))
        .map(|path| {
            path.file_name()
                .expect("fixture file name")
                .to_string_lossy()
                .into_owned()
        })
        .collect();
    names.sort();
    assert!(
        names.len() >= 6,
        "expected at least the six sample cards, found {names:?}"
    );

    for name in &names {
        // 1) WAT 可编译。
        let wasm = compile_fixture(name);
        assert!(!wasm.is_empty(), "{name} compiled to an empty module");

        // 2) 六个 ABI v1 export + memory 都在：能加载即说明必需 export 齐全，
        //    再逐个调用 init/shutdown 验证可选 export 真的可调用。
        let manifest_json = manifest("conformance", &[], (64, 1_000, 4096));
        let mut runtime = CardRuntime::from_manifest_json(&wasm, &manifest_json, DefaultHost::new())
            .unwrap_or_else(|err| panic!("{name} must load: {err}"));
        runtime
            .init(&manifest_json)
            .unwrap_or_else(|err| panic!("{name}: kk_init must be callable: {err}"));
        runtime
            .shutdown()
            .unwrap_or_else(|err| panic!("{name}: kk_shutdown must be callable: {err}"));
    }
}
