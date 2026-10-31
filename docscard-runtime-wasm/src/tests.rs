//! lib 单测：WAT 样例卡片（wat crate 编译）+ 引擎行为验证。
//!
//! 覆盖：ptr/len 编解码、(ptr<<32|len) + JSON 线性内存往返、五个 import 注册、
//! 六个 export 可调用、缺失 export 明确报错、能力白名单拒绝、fuel 超时、
//! memory_mb 页上限、output_bytes 上限、非法 manifest 分类。

use std::path::Path;

use crate::abi;
use crate::engine::{link_abi, CardRuntime, HostState, FUEL_PER_MS};
use crate::error::RuntimeError;
use crate::host::{CardHost, HostError};
use crate::manifest::{Capability, Manifest, ManifestError};

const OK_JSON: &str = r#"{"ok":true}"#;
const DENIED_JSON: &str = r#"{"denied":true}"#;
const BOOM_JSON: &str = r#"{"code":"boom"}"#;

/// 样例卡片骨架：五个 import + memory + kk_alloc/kk_free。
/// `{body}` 里放具体的 init/handle/shutdown/last_error/data。
fn card_wat(body: &str) -> String {
    format!(
        r#"(module
  (import "kk" "host_call" (func $host_call (param i32 i32 i32) (result i64)))
  (import "kk" "log" (func $log (param i32 i32 i32)))
  (import "kk" "rand" (func $rand (param i32 i32) (result i32)))
  (import "kk" "now_ms" (func $now_ms (result i64)))
  (import "kk" "abort" (func $abort (param i32)))
  (memory (export "memory") 2)
  (global $heap (mut i32) (i32.const 1024))
  (func (export "kk_alloc") (param $size i32) (result i32)
    (local $p i32)
    (local.set $p (global.get $heap))
    (if (i32.gt_u (i32.add (local.get $p) (local.get $size)) (i32.const 130000))
      (then (return (i32.const 0))))
    (global.set $heap (i32.add (global.get $heap) (i32.add (local.get $size) (i32.const 8))))
    (local.get $p))
  (func (export "kk_free") (param $ptr i32) (param $len i32))
{body}
)"#,
        body = body
    )
}

fn data_seg(offset: u32, text: &str) -> String {
    let escaped = text.replace('"', "\\\"");
    format!("  (data (i32.const {offset}) \"{escaped}\")\n")
}

#[derive(Debug, Default)]
struct MockHost {
    calls: Vec<(String, String)>,
    reply: Option<String>,
    fail: bool,
    log_levels: Vec<i32>,
}

impl CardHost for MockHost {
    fn host_call(&mut self, cap: &str, request_json: &str) -> Result<String, HostError> {
        self.calls.push((cap.to_string(), request_json.to_string()));
        if self.fail {
            return Err(HostError::Denied("mock host says no".to_string()));
        }
        Ok(self
            .reply
            .clone()
            .unwrap_or_else(|| OK_JSON.to_string()))
    }

    fn log(&mut self, level: i32, _message: &str) {
        self.log_levels.push(level);
    }
}

pub(crate) fn build_wasm(body: &str) -> Vec<u8> {
    let wat = card_wat(body);
    wat::parse_str(&wat).unwrap_or_else(|err| panic!("sample card must compile: {err}\n{wat}"))
}

pub(crate) fn manifest_json(caps: &[&str], limits: &str) -> String {
    let caps = caps
        .iter()
        .map(|cap| format!("\"{cap}\""))
        .collect::<Vec<_>>()
        .join(", ");
    format!(
        r#"{{"name":"unit-test-card","version":"0.1.0","runtime":"wasm","abi_version":1,"capabilities":[{caps}],"limits":{limits}}}"#
    )
}

fn default_limits() -> &'static str {
    r#"{"memory_mb":64,"timeout_ms":1000,"output_bytes":262144}"#
}

fn boot(body: &str, manifest: &str, host: MockHost) -> CardRuntime<MockHost> {
    let wasm = build_wasm(body);
    CardRuntime::from_manifest_json(&wasm, manifest, host).expect("card must load")
}

/// 回显卡片：kk_handle 原样返回宿主写入的 (ptr, len)，用于验证 JSON 线性内存往返。
pub(crate) fn echo_card_body() -> String {
    r#"
  (func (export "kk_init") (param i32 i32) (result i32) (i32.const 0))
  (func (export "kk_shutdown") (result i32) (i32.const 0))
  (func (export "kk_handle") (param $ptr i32) (param $len i32) (result i64)
    (i64.or
      (i64.shl (i64.extend_i32_u (local.get $ptr)) (i64.const 32))
      (i64.extend_i32_u (local.get $len))))
"#
    .to_string()
}

/// 调 kk.host_call(cap_index, event_ptr, event_len)，按返回值选 ok/denied 结果 JSON。
fn host_call_card_body(cap_index: u32) -> String {
    let mut body = String::new();
    body.push_str(&data_seg(3200, OK_JSON));
    body.push_str(&data_seg(3300, DENIED_JSON));
    body.push_str(&format!(
        r#"  (func (export "kk_handle") (param $ptr i32) (param $len i32) (result i64)
    (if (result i64)
      (i64.lt_s (call $host_call (i32.const {cap_index}) (local.get $ptr) (local.get $len)) (i64.const 0))
      (then (i64.const {denied}))
      (else (i64.const {ok}))))
"#,
        denied = abi::pack(3300, DENIED_JSON.len() as u32),
        ok = abi::pack(3200, OK_JSON.len() as u32),
    ));
    body
}

#[test]
fn pack_unpack_roundtrip() {
    for (ptr, len) in [(0u32, 0u32), (1, 1), (0x1234_5678, 0x9abc_def0), (0x7fff_ffff, 4096)] {
        let packed = abi::pack(ptr, len);
        assert!(packed >= 0, "packed value must stay non-negative");
        assert_eq!(abi::unpack(packed), (ptr, len));
    }
    assert_eq!(abi::pack(2048, 11), 8796093022219);
    assert_eq!(abi::encode_error(abi::HOST_ERR_CAPABILITY_DENIED), -1);
    assert_eq!(
        abi::decode_error(abi::encode_error(abi::HOST_ERR_OUTPUT_TOO_LARGE)),
        Some(abi::HOST_ERR_OUTPUT_TOO_LARGE)
    );
    assert_eq!(abi::decode_error(abi::pack(4096, 8)), None);
}

#[test]
fn json_linear_memory_roundtrip() {
    let manifest = manifest_json(&[], default_limits());
    let mut runtime = boot(&echo_card_body(), &manifest, MockHost::default());
    let event = r#"{"type":"tick","payload":{"n":1,"text":"卡片"}}"#;
    runtime.init(&manifest).unwrap();
    assert_eq!(runtime.handle(event).unwrap(), event);
    assert_eq!(runtime.handle("{}").unwrap(), "{}");
    runtime.shutdown().unwrap();
}

#[test]
fn all_five_imports_register_on_fresh_linker() {
    // 五个 import 各自单独注册：每个 import 都能被一个只声明它的模块解析成功。
    let signatures = [
        ("kk", "host_call", "(param i32 i32 i32) (result i64)"),
        ("kk", "log", "(param i32 i32 i32)"),
        ("kk", "rand", "(param i32 i32) (result i32)"),
        ("kk", "now_ms", "(result i64)"),
        ("kk", "abort", "(param i32)"),
    ];
    for (module, name, signature) in signatures {
        let config = wasmi::Config::default();
        let engine = wasmi::Engine::new(&config);
        let mut linker: wasmi::Linker<HostState<MockHost>> = wasmi::Linker::new(&engine);
        link_abi(&mut linker).expect("all five imports must register");
        let limits = wasmi::StoreLimitsBuilder::new().build();
        let mut store: wasmi::Store<HostState<MockHost>> = wasmi::Store::new(
            &engine,
            HostState::new(MockHost::default(), Vec::new(), limits, 262_144),
        );
        let wat = format!(r#"(module (import "{module}" "{name}" (func {signature})))"#);
        let wasm = wat::parse_str(&wat).expect("probe module must compile");
        let probe = wasmi::Module::new(&engine, &wasm).expect("probe module must validate");
        linker
            .instantiate(&mut store, &probe)
            .unwrap_or_else(|err| panic!("import {module}.{name} is not registered: {err}"))
            .start(&mut store)
            .expect("probe instance must start");
    }
    assert_eq!(abi::IMPORTS.len(), 5);

    // 未注册的 import 必须解析失败（证明上面的成功不是"任何名字都放行"）。
    let config = wasmi::Config::default();
    let engine = wasmi::Engine::new(&config);
    let mut linker: wasmi::Linker<HostState<MockHost>> = wasmi::Linker::new(&engine);
    link_abi(&mut linker).expect("all five imports must register");
    let limits = wasmi::StoreLimitsBuilder::new().build();
    let mut store: wasmi::Store<HostState<MockHost>> = wasmi::Store::new(
        &engine,
        HostState::new(MockHost::default(), Vec::new(), limits, 262_144),
    );
    let wasm = wat::parse_str(r#"(module (import "kk" "not_an_import" (func)))"#).unwrap();
    let probe = wasmi::Module::new(&engine, &wasm).unwrap();
    assert!(linker.instantiate(&mut store, &probe).is_err());
}

#[test]
fn all_six_exports_are_callable() {
    let mut body = echo_card_body();
    body.push_str(&data_seg(3400, BOOM_JSON));
    body.push_str(&format!(
        "  (func (export \"kk_last_error\") (result i64) (i64.const {}))\n",
        abi::pack(3400, BOOM_JSON.len() as u32)
    ));
    let manifest = manifest_json(&[], default_limits());
    let mut runtime = boot(&body, &manifest, MockHost::default());
    runtime.init(&manifest).expect("kk_init callable");
    assert_eq!(runtime.handle("{}").expect("kk_handle callable"), "{}");
    assert_eq!(
        runtime.last_error_json().expect("kk_last_error callable"),
        BOOM_JSON
    );
    runtime.shutdown().expect("kk_shutdown callable");
    assert_eq!(abi::EXPORTS.len(), 6);
}

#[test]
fn missing_export_reports_clear_error() {
    // 没有 kk_handle
    let body = "  (func (export \"kk_init\") (param i32 i32) (result i32) (i32.const 0))\n";
    let manifest = manifest_json(&[], default_limits());
    let wasm = build_wasm(body);
    let err = CardRuntime::from_manifest_json(&wasm, &manifest, MockHost::default())
        .expect_err("missing kk_handle must fail");
    assert_eq!(
        err,
        RuntimeError::MissingExport {
            name: abi::EXPORT_HANDLE
        }
    );

    // 没有导出 memory
    let wat = r#"(module
  (memory 1)
  (func (export "kk_alloc") (param i32) (result i32) (i32.const 16))
  (func (export "kk_free") (param i32 i32))
  (func (export "kk_handle") (param i32 i32) (result i64) (i64.const 0)))"#;
    let wasm = wat::parse_str(wat).unwrap();
    let err = CardRuntime::from_manifest_json(&wasm, &manifest, MockHost::default())
        .expect_err("missing memory export must fail");
    assert_eq!(
        err,
        RuntimeError::MissingExport {
            name: abi::MEMORY_EXPORT
        }
    );
}

#[test]
fn undeclared_capability_is_denied_with_error_code() {
    // manifest 只声明了 haptics；卡片却请求 cap#1（未声明）-> 必须拒绝，且不触达宿主。
    let manifest = manifest_json(&["haptics"], default_limits());
    let mut runtime = boot(&host_call_card_body(1), &manifest, MockHost::default());
    runtime.init(&manifest).unwrap();
    let result = runtime.handle(r#"{"op":"ping"}"#).unwrap();
    assert_eq!(result, DENIED_JSON);
    assert!(
        runtime.host().calls.is_empty(),
        "denied capability must not reach the host"
    );
    let failure = runtime.last_host_error().expect("denial must be recorded");
    assert_eq!(failure.code, abi::HOST_ERR_CAPABILITY_DENIED);
    assert_eq!(failure.code, 1);
    assert_eq!(runtime.host_call_failures(), 1);
}

#[test]
fn declared_capability_reaches_host() {
    let manifest = manifest_json(&["haptics"], default_limits());
    let mut runtime = boot(&host_call_card_body(0), &manifest, MockHost::default());
    runtime.init(&manifest).unwrap();
    let result = runtime.handle(r#"{"op":"vibrate"}"#).unwrap();
    assert_eq!(result, OK_JSON);
    assert_eq!(runtime.host().calls.len(), 1);
    assert_eq!(runtime.host().calls[0].0, "haptics");
    assert_eq!(runtime.host().calls[0].1, r#"{"op":"vibrate"}"#);
    assert!(runtime.last_host_error().is_none());
}

#[test]
fn host_failure_is_reported_to_guest_as_error_code() {
    let manifest = manifest_json(&["ai:chat"], default_limits());
    let host = MockHost {
        fail: true,
        ..Default::default()
    };
    let mut runtime = boot(&host_call_card_body(0), &manifest, host);
    runtime.init(&manifest).unwrap();
    assert_eq!(runtime.handle("{}").unwrap(), DENIED_JSON);
    let failure = runtime.last_host_error().unwrap();
    assert_eq!(failure.code, abi::HOST_ERR_CAPABILITY_DENIED);
    assert_eq!(failure.capability, "ai:chat");
}

#[test]
fn fuel_exhaustion_is_reported_as_timeout() {
    let manifest = manifest_json(
        &[],
        r#"{"memory_mb":16,"timeout_ms":20,"output_bytes":4096}"#,
    );
    let body = r#"
  (func (export "kk_handle") (param i32 i32) (result i64)
    (loop $spin (br $spin))
    (i64.const 0))
"#;
    let mut runtime = boot(body, &manifest, MockHost::default());
    let started = std::time::Instant::now();
    let err = runtime.handle("{}").expect_err("infinite loop must time out");
    assert_eq!(err.kind(), "timeout");
    assert!(matches!(
        err,
        RuntimeError::Timeout { timeout_ms: 20 }
    ));
    // 远小于任何墙钟超时，说明是 fuel 中断而不是挂死。
    assert!(started.elapsed() < std::time::Duration::from_secs(5));
    assert!(FUEL_PER_MS > 0);
}

#[test]
fn memory_limit_caps_memory_grow() {
    // memory_mb=1 -> 16 页上限；卡片初始 2 页，grow(100) 必须失败（返回 -1）。
    let manifest = manifest_json(
        &[],
        r#"{"memory_mb":1,"timeout_ms":1000,"output_bytes":262144}"#,
    );
    let mut body = String::new();
    body.push_str(&data_seg(3200, OK_JSON));
    body.push_str(&data_seg(3300, DENIED_JSON));
    body.push_str(&format!(
        r#"  (func (export "kk_handle") (param i32 i32) (result i64)
    (if (result i64) (i32.eq (memory.grow (i32.const 100)) (i32.const -1))
      (then (i64.const {denied}))
      (else (i64.const {ok}))))
"#,
        denied = abi::pack(3300, DENIED_JSON.len() as u32),
        ok = abi::pack(3200, OK_JSON.len() as u32),
    ));
    let mut runtime = boot(&body, &manifest, MockHost::default());
    assert_eq!(runtime.handle("{}").unwrap(), DENIED_JSON);

    // limits 越界必须在 manifest 校验阶段被拒。
    let bad = Manifest::parse(r#"{"abi_version":1,"limits":{"memory_mb":4096}}"#).unwrap();
    assert!(matches!(
        bad.validate(),
        Err(ManifestError::LimitsOutOfRange {
            field: "memory_mb",
            ..
        })
    ));
    let bad = Manifest::parse(r#"{"abi_version":1,"limits":{"memory_mb":0}}"#).unwrap();
    assert!(matches!(
        bad.validate(),
        Err(ManifestError::LimitsOutOfRange {
            field: "memory_mb",
            ..
        })
    ));
}

#[test]
fn output_bytes_limits_result_and_host_reply() {
    // 1) 事件结果超过 output_bytes -> OutputTooLarge
    let manifest = manifest_json(
        &[],
        r#"{"memory_mb":16,"timeout_ms":1000,"output_bytes":256}"#,
    );
    let body = format!(
        "  (func (export \"kk_handle\") (param i32 i32) (result i64) (i64.const {}))\n",
        abi::pack(0, 100_000)
    );
    let mut runtime = boot(&body, &manifest, MockHost::default());
    let err = runtime.handle("{}").expect_err("huge result must be rejected");
    assert!(
        matches!(err, RuntimeError::OutputTooLarge { len: 100_000, limit: 256 }),
        "unexpected error: {err:?}"
    );

    // 2) 宿主回传内容超过 output_bytes -> 卡片拿到 HOST_ERR_OUTPUT_TOO_LARGE
    let big = format!(r#"{{"data":"{}"}}"#, "x".repeat(2000));
    let host = MockHost {
        reply: Some(big),
        ..Default::default()
    };
    let declared = manifest_json(
        &["haptics"],
        r#"{"memory_mb":16,"timeout_ms":1000,"output_bytes":256}"#,
    );
    let mut runtime = boot(&host_call_card_body(0), &declared, host);
    runtime.init(&manifest).unwrap();
    assert_eq!(runtime.handle("{}").unwrap(), DENIED_JSON);
    assert_eq!(
        runtime.last_host_error().unwrap().code,
        abi::HOST_ERR_OUTPUT_TOO_LARGE
    );

    // 3) 事件输入超过 output_bytes -> InputTooLarge
    let err = runtime
        .handle(&"y".repeat(300))
        .expect_err("oversized event must be rejected");
    assert!(matches!(err, RuntimeError::InputTooLarge { len: 300, limit: 256 }));
}

#[test]
fn card_error_reads_last_error_json() {
    let body = format!(
        "{}{}",
        data_seg(3400, BOOM_JSON),
        format!(
            r#"  (func (export "kk_handle") (param i32 i32) (result i64) (i64.const 0))
  (func (export "kk_last_error") (result i64) (i64.const {}))
"#,
            abi::pack(3400, BOOM_JSON.len() as u32)
        )
    );
    let manifest = manifest_json(&[], default_limits());
    let mut runtime = boot(&body, &manifest, MockHost::default());
    let err = runtime.handle("{}").expect_err("code 0 must be a card error");
    assert_eq!(
        err,
        RuntimeError::CardError {
            code: 1,
            export: abi::EXPORT_HANDLE,
            detail: Some(BOOM_JSON.to_string()),
        }
    );

    // kk_init 返回非 0 -> CardError，并带上 kk_last_error 的 JSON
    let body = format!(
        "{}{}",
        data_seg(3400, BOOM_JSON),
        format!(
            r#"  (func (export "kk_init") (param i32 i32) (result i32) (i32.const 3))
  (func (export "kk_handle") (param i32 i32) (result i64) (i64.const 0))
  (func (export "kk_last_error") (result i64) (i64.const {}))
"#,
            abi::pack(3400, BOOM_JSON.len() as u32)
        )
    );
    let mut runtime = boot(&body, &manifest, MockHost::default());
    let err = runtime.init("{}").expect_err("kk_init code 3 must fail");
    assert_eq!(
        err,
        RuntimeError::CardError {
            code: 3,
            export: abi::EXPORT_INIT,
            detail: Some(BOOM_JSON.to_string()),
        }
    );
}

#[test]
fn negative_handle_result_is_a_card_error_code() {
    let body = format!(
        "{}{}",
        data_seg(3400, BOOM_JSON),
        format!(
            r#"  (func (export "kk_handle") (param i32 i32) (result i64) (i64.const -2))
  (func (export "kk_last_error") (result i64) (i64.const {}))
"#,
            abi::pack(3400, BOOM_JSON.len() as u32)
        )
    );
    let manifest = manifest_json(&[], default_limits());
    let mut runtime = boot(&body, &manifest, MockHost::default());
    let err = runtime
        .handle("{}")
        .expect_err("negative result must be a card error");
    assert_eq!(
        err,
        RuntimeError::CardError {
            code: 2,
            export: abi::EXPORT_HANDLE,
            detail: Some(BOOM_JSON.to_string()),
        }
    );
}

#[test]
fn abort_stops_execution() {
    let body = r#"
  (func (export "kk_handle") (param i32 i32) (result i64)
    (call $abort (i32.const 7))
    (i64.const 0))
"#;
    let manifest = manifest_json(&[], default_limits());
    let mut runtime = boot(body, &manifest, MockHost::default());
    let err = runtime.handle("{}").expect_err("kk.abort must stop the call");
    assert_eq!(err, RuntimeError::Aborted { code: 7 });
}

#[test]
fn log_rand_now_are_wired() {
    let mut body = String::new();
    body.push_str(&data_seg(3200, OK_JSON));
    body.push_str(&data_seg(3300, DENIED_JSON));
    body.push_str(&format!(
        r#"  (func (export "kk_handle") (param $ptr i32) (param $len i32) (result i64)
    (call $log (i32.const 2) (local.get $ptr) (local.get $len))
    (drop (call $rand (i32.const 6000) (i32.const 8)))
    (if (result i64) (i64.gt_s (call $now_ms) (i64.const 0))
      (then (i64.const {ok}))
      (else (i64.const {denied}))))
"#,
        ok = abi::pack(3200, OK_JSON.len() as u32),
        denied = abi::pack(3300, DENIED_JSON.len() as u32),
    ));
    let manifest = manifest_json(&[], default_limits());
    let mut runtime = boot(&body, &manifest, MockHost::default());
    assert_eq!(runtime.handle(r#"{"type":"log"}"#).unwrap(), OK_JSON);
    assert_eq!(runtime.logs().len(), 1);
    assert_eq!(runtime.logs()[0].level, 2);
    assert_eq!(runtime.logs()[0].message, r#"{"type":"log"}"#);
    assert_eq!(runtime.host().log_levels, vec![2]);
}

#[test]
fn invalid_manifests_are_distinguishable() {
    let wasm = build_wasm(&echo_card_body());

    let err = CardRuntime::from_manifest_json(
        &wasm,
        r#"{"runtime":"js","abi_version":1}"#,
        MockHost::default(),
    )
    .expect_err("runtime=js must be rejected");
    assert!(matches!(
        err,
        RuntimeError::Manifest(ManifestError::UnsupportedRuntime(ref runtime)) if runtime == "js"
    ));

    let err = CardRuntime::from_manifest_json(
        &wasm,
        r#"{"runtime":"wasm","abi_version":2}"#,
        MockHost::default(),
    )
    .expect_err("abi_version=2 must be rejected");
    assert!(matches!(
        err,
        RuntimeError::Manifest(ManifestError::AbiVersionMismatch { expected: 1, found: Some(2) })
    ));

    let err = CardRuntime::from_manifest_json(
        &wasm,
        r#"{"abi_version":1,"capabilities":["fs:root"]}"#,
        MockHost::default(),
    )
    .expect_err("unknown capability must be rejected");
    assert!(matches!(
        err,
        RuntimeError::Manifest(ManifestError::UnknownCapability(ref cap)) if cap == "fs:root"
    ));

    let err = CardRuntime::from_manifest_json(
        &wasm,
        r#"{"abi_version":1,"capabilities":["net:"]}"#,
        MockHost::default(),
    )
    .expect_err("malformed net capability must be rejected");
    assert!(matches!(
        err,
        RuntimeError::Manifest(ManifestError::InvalidCapability(_))
    ));

    let err = CardRuntime::from_manifest_json(
        &wasm,
        r#"{"abi_version":1,"limits":{"timeout_ms":999999}}"#,
        MockHost::default(),
    )
    .expect_err("timeout_ms out of range must be rejected");
    assert!(matches!(
        err,
        RuntimeError::Manifest(ManifestError::LimitsOutOfRange {
            field: "timeout_ms",
            ..
        })
    ));

    let err = CardRuntime::from_manifest_json(&wasm, "not json", MockHost::default())
        .expect_err("invalid JSON must be rejected");
    assert!(matches!(
        err,
        RuntimeError::Manifest(ManifestError::InvalidJson(_))
    ));
}

#[test]
fn manifest_defaults_and_capability_set() {
    let manifest = Manifest::parse(r#"{"abi_version":1,"capabilities":["ai:chat","ai:chat","net:api.example.com"]}"#)
        .unwrap();
    let spec = manifest.validate().unwrap();
    assert_eq!(spec.limits.memory_mb, 64);
    assert_eq!(spec.limits.timeout_ms, 5000);
    assert_eq!(spec.limits.output_bytes, 262_144);
    assert_eq!(
        spec.capabilities,
        vec![
            Capability::AiChat,
            Capability::Net("api.example.com".to_string())
        ],
        "duplicates are collapsed"
    );

    for cap in ["ai:chat", "fs:workspace", "ui:render", "haptics", "clipboard"] {
        assert_eq!(Capability::parse(cap).unwrap().as_str(), cap);
    }
    assert!(matches!(
        Capability::parse("ai:image"),
        Err(ManifestError::UnknownCapability(_))
    ));
    assert!(matches!(
        Capability::parse("net:a b"),
        Err(ManifestError::InvalidCapability(_))
    ));

    // P1 词表扩展：新增能力串都能解析并原样回写。
    for cap in [
        "host",
        "tool",
        "accessibility",
        "store",
        "download",
        "web",
        "browser",
        "media:pick",
        "schedule",
        "system",
        "image:toolbox",
        "shizuku",
        "root",
        "net:*",
        "dep:musicKey",
        "dep:imageToolbox",
        "net:f-droid.org",
    ] {
        assert_eq!(Capability::parse(cap).unwrap().as_str(), cap, "cap {cap}");
    }
    // net:* 语义与普通域名区分
    assert_eq!(Capability::parse("net:*").unwrap(), Capability::Net("*".to_string()));
    assert_eq!(
        Capability::parse("dep:musicKey").unwrap(),
        Capability::Dep("musicKey".to_string())
    );
    // 格式错误仍然拒绝
    for bad in ["net:", "net:a b", "dep:", "dep:bad name", "dep:a/b", "fs:root", "ai:image"] {
        assert!(
            matches!(
                Capability::parse(bad),
                Err(ManifestError::InvalidCapability(_)) | Err(ManifestError::UnknownCapability(_))
            ),
            "cap {bad} should be rejected"
        );
    }
    // 顺序即 cap 下标：扩展能力与已有能力混排时保持书写顺序。
    let manifest = Manifest::parse(
        r#"{"abi_version":1,"capabilities":["host","ui:render","tool","accessibility"]}"#,
    )
    .unwrap();
    assert_eq!(
        manifest.validate().unwrap().capabilities,
        vec![
            Capability::Host,
            Capability::UiRender,
            Capability::Tool,
            Capability::Accessibility
        ]
    );

    // runtime 缺省 = wasm
    let manifest = Manifest::parse(r#"{"abi_version":1}"#).unwrap();
    assert!(manifest.validate().is_ok());
}

/// 为 CLI 验证生成一份样例卡片（只在设置 DOCSCARD_EMIT_FIXTURE 时写盘）。
#[test]
fn emit_cli_fixture() {
    if std::env::var("DOCSCARD_EMIT_FIXTURE").is_err() {
        return;
    }
    let dir = Path::new("target/cli-fixture");
    std::fs::create_dir_all(dir).expect("create fixture dir");
    let mut body = echo_card_body();
    body.push_str(&data_seg(3400, BOOM_JSON));
    body.push_str(&format!(
        "  (func (export \"kk_last_error\") (result i64) (i64.const {}))\n",
        abi::pack(3400, BOOM_JSON.len() as u32)
    ));
    let wasm = build_wasm(&body);
    std::fs::write(dir.join("card.wasm"), wasm).expect("write card.wasm");
    std::fs::write(
        dir.join("manifest.json"),
        manifest_json(&["haptics"], default_limits()),
    )
    .expect("write manifest.json");
    std::fs::write(dir.join("event.json"), r#"{"type":"tick","payload":{"n":1}}"#)
        .expect("write event.json");
}
