//! WASM 卡片（docscard-runtime-wasm / ABI v1）的 JNI 入口。
//!
//! Kotlin 侧 WasmScriptEngine 只做三件事：nativeCardLoad / nativeCardHandle / nativeCardClose。
//! 卡片里的 host_call 全部转成 JVM 侧 RustBridgeDispatcher.dispatch(bridge, method, argsJson)，
//! 因此 ui.* 复用既有 Compose 节点树渲染链（UiBridgeHost + UiNodeParser），无障碍/网络/图像
//! 等能力也复用既有 bridge 实现，WASM 引擎不需要第二套宿主。
//!
//! ui.form / ui.screen / ui.show 的节点树在交给 Kotlin 之前先过 Rust 侧校验与规范化
//! （见 docscard_runtime_wasm::ui）：组件/属性白名单、深度 <=32、节点数 <=512、重复 id。
//! 校验失败按 ABI 返回 HOST_ERR_INVALID_REQUEST（-2），卡片自己翻译成结果错误。

use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::Arc;

use docscard_runtime_wasm::{
    normalize_tree, CardHost, CardRuntime, HostError, Manifest,
};
use jni::objects::{GlobalRef, JByteArray, JClass, JObject, JString};
use jni::sys::{jint, jlong, jstring};
use jni::{JNIEnv, JavaVM};
use serde_json::Value;

use crate::host::Host;

struct WasmHost {
    host: Arc<Host>,
}

impl WasmHost {
    fn new(host: Arc<Host>) -> Self {
        Self { host }
    }
}

/// ui.form/ui.screen/ui.show 的节点树先校验+规范化，其余方法原样透传。
fn normalize_request(method: &str, request_json: &str) -> Result<(String, String), HostError> {
    let request: Value = serde_json::from_str(request_json)
        .map_err(|err| HostError::InvalidRequest(format!("{err}: {request_json}")))?;
    let mut args = match request.get("args") {
        Some(Value::Array(items)) => items.clone(),
        None | Some(Value::Null) => Vec::new(),
        Some(_) => {
            return Err(HostError::InvalidRequest(
                "args must be a positional array".to_string(),
            ))
        }
    };
    let tree_index = match method {
        "ui.form" | "ui.screen" => Some(1),
        "ui.show" => Some(0),
        _ => None,
    };
    if let Some(index) = tree_index {
        if let Some(tree) = args.get(index) {
            let normalized = normalize_tree(tree).map_err(HostError::InvalidRequest)?;
            if index < args.len() {
                args[index] = normalized;
            }
        }
    }
    // ui.screen 的分页树在 options.pages[*].tree 里，同样要过白名单/深度/节点数校验，
    // 并转成宿主内部 `__ui` 形状；否则 Kotlin 的 UiScreenPage 解析会失败（分页树空、整屏打不开）。
    if method == "ui.screen" {
        if let Some(options) = args.get_mut(2) {
            docscard_runtime_wasm::normalize_screen_pages(options)
                .map_err(HostError::InvalidRequest)?;
        }
    }
    Ok((method.to_string(), Value::Array(args).to_string()))
}

impl CardHost for WasmHost {
    fn host_call(&mut self, cap: &str, request_json: &str) -> Result<String, HostError> {
        let request: Value = serde_json::from_str(request_json)
            .map_err(|err| HostError::InvalidRequest(format!("{err}: {request_json}")))?;
        let method = request
            .get("method")
            .and_then(|value| value.as_str())
            .ok_or_else(|| HostError::InvalidRequest("missing method".to_string()))?
            .to_string();
        let (_, args_json) = normalize_request(&method, request_json)?;
        if self.host.interrupt_reason() != 0 {
            return Err(HostError::Failed("CARD_CANCELLED_OR_TIMEOUT".into()));
        }
        if !self.host.wasm_allowed(cap, &method, &args_json) {
            return Err(HostError::Denied(format!("{cap} does not authorize {method}")));
        }
        // ABI host locale is provided by the existing i18n bridge.
        let method = if method == "host.locale" { "i18n.locale" } else { &method };
        let (bridge, name) = split_method(method);
        Ok(self.host.dispatch(bridge, name, &args_json))
    }

    fn log(&mut self, _level: i32, _message: &str) {}
}

/// "ui.form" -> ("ui", "form")；"imageToolbox.resize" -> ("imageToolbox", "resize")。
fn split_method(method: &str) -> (&str, &str) {
    match method.split_once('.') {
        Some((bridge, name)) => (bridge, name),
        None => (method, ""),
    }
}

struct CardState {
    runtime: CardRuntime<WasmHost>,
}

fn manifest_from(env: &mut JNIEnv, manifest: &JString) -> Result<(String, Manifest), String> {
    let text: String = env
        .get_string(manifest)
        .map_err(|err| format!("cannot read manifest: {err}"))?
        .into();
    let parsed = Manifest::parse(&text).map_err(|err| err.to_string())?;
    Ok((text, parsed))
}

fn handle_ptr<'a>(handle: jlong) -> Option<&'a mut CardState> {
    if handle == 0 {
        None
    } else {
        Some(unsafe { &mut *(handle as *mut CardState) })
    }
}

fn error_json(message: &str) -> String {
    serde_json::json!({"__error": message}).to_string()
}

/// 加载卡片：wasm 字节 + manifest 原文 + dispatcher。返回 0 表示失败。
#[no_mangle]
pub extern "system" fn Java_heizige_kk_khatkit_engine_KhatKitNative_nativeCardLoad(
    mut env: JNIEnv,
    _class: JClass,
    wasm: JByteArray,
    manifest: JString,
    dispatcher: JObject,
) -> jlong {
    catch_unwind(AssertUnwindSafe(|| {
        let jvm: JavaVM = match env.get_java_vm() {
            Ok(jvm) => jvm,
            Err(_) => return 0,
        };
        let global: GlobalRef = match env.new_global_ref(dispatcher) {
            Ok(global) => global,
            Err(_) => return 0,
        };
        let wasm_bytes = match env.convert_byte_array(&wasm) {
            Ok(bytes) => bytes,
            Err(_) => return 0,
        };
        let (_, parsed) = match manifest_from(&mut env, &manifest) {
            Ok(value) => value,
            Err(_) => return 0,
        };
        let host = WasmHost::new(Arc::new(Host::new(jvm, global)));
        match CardRuntime::load(&wasm_bytes, &parsed, host) {
            Ok(runtime) => Box::into_raw(Box::new(CardState { runtime })) as jlong,
            Err(_) => 0,
        }
    }))
    .unwrap_or(0)
}

/// kk_init：入参是 manifest 原文。返回 0 = 成功，非 0 = 错误码。
#[no_mangle]
pub extern "system" fn Java_heizige_kk_khatkit_engine_KhatKitNative_nativeCardInit(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    manifest: JString,
) -> jint {
    catch_unwind(AssertUnwindSafe(|| {
        let state = match handle_ptr(handle) {
            Some(state) => state,
            None => return 1,
        };
        let (text, _) = match manifest_from(&mut env, &manifest) {
            Ok(value) => value,
            Err(_) => return 1,
        };
        match state.runtime.init(&text) {
            Ok(()) => 0,
            Err(_) => 2,
        }
    }))
    .unwrap_or(3)
}

/// kk_handle：入参是事件 JSON，返回结果 JSON（失败时是 {"__error":...}）。
#[no_mangle]
pub extern "system" fn Java_heizige_kk_khatkit_engine_KhatKitNative_nativeCardHandle(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    event: JString,
) -> jstring {
    let text = catch_unwind(AssertUnwindSafe(|| {
        let state = match handle_ptr(handle) {
            Some(state) => state,
            None => return error_json("card handle is closed"),
        };
        let event_json: String = match env.get_string(&event) {
            Ok(value) => value.into(),
            Err(err) => return error_json(&format!("cannot read event: {err}")),
        };
        match state.runtime.handle(&event_json) {
            Ok(result) => result,
            Err(err) => error_json(&err.to_string()),
        }
    }))
    .unwrap_or_else(|_| error_json("wasm card panicked"));
    match env.new_string(text) {
        Ok(output) => output.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// kk_shutdown。
#[no_mangle]
pub extern "system" fn Java_heizige_kk_khatkit_engine_KhatKitNative_nativeCardShutdown(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    catch_unwind(AssertUnwindSafe(|| {
        match handle_ptr(handle) {
            Some(state) => match state.runtime.shutdown() {
                Ok(()) => 0,
                Err(_) => 2,
            },
            None => 1,
        }
    }))
    .unwrap_or(3)
}

/// 释放卡片。
#[no_mangle]
pub extern "system" fn Java_heizige_kk_khatkit_engine_KhatKitNative_nativeCardClose(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    let _ = catch_unwind(AssertUnwindSafe(|| {
        let _ = unsafe { Box::from_raw(handle as *mut CardState) };
    }));
}
