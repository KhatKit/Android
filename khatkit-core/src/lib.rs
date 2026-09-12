mod host;
mod js_engine;
mod json_util;
mod lua_engine;

use host::Host;
use jni::objects::{JClass, JObject, JString};
use jni::sys::{jint, jlong, jstring};
use jni::JNIEnv;
use std::collections::BTreeMap;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::Arc;

enum EngineImpl {
    Lua(lua_engine::LuaEngine),
    Js(js_engine::JsEngine),
}

struct EngineState {
    engine: EngineImpl,
    #[allow(dead_code)]
    bridges: BTreeMap<String, Vec<String>>,
}

fn escape_json(message: &str) -> String {
    message
        .replace('\\', "\\\\")
        .replace('"', "\\\"")
        .replace('\n', "\\n")
        .replace('\r', "")
}

/// kind: 0 = Lua (mlua), 1 = JS (rquickjs)
#[no_mangle]
pub extern "system" fn Java_heizige_kk_khatkit_engine_KhatKitNative_nativeCreate(
    mut env: JNIEnv,
    _class: JClass,
    kind: jint,
    dispatcher: JObject,
) -> jlong {
    let jvm = match env.get_java_vm() {
        Ok(jvm) => jvm,
        Err(_) => return 0,
    };
    let global = match env.new_global_ref(dispatcher) {
        Ok(global) => global,
        Err(_) => return 0,
    };
    let host = Arc::new(Host::new(jvm, global));
    let engine: Result<EngineImpl, String> = if kind == 0 {
        lua_engine::LuaEngine::new(host)
            .map(EngineImpl::Lua)
            .map_err(|e| e.to_string())
    } else {
        js_engine::JsEngine::new(host)
            .map(EngineImpl::Js)
            .map_err(|e| e.to_string())
    };
    match engine {
        Ok(engine) => Box::into_raw(Box::new(EngineState {
            engine,
            bridges: BTreeMap::new(),
        })) as jlong,
        Err(_) => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_heizige_kk_khatkit_engine_KhatKitNative_nativeDefine(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    bridge: JString,
    methods: JString,
) {
    if handle == 0 {
        return;
    }
    let state = unsafe { &mut *(handle as *mut EngineState) };
    let bridge: String = env
        .get_string(&bridge)
        .map(Into::into)
        .unwrap_or_default();
    let methods_csv: String = env
        .get_string(&methods)
        .map(Into::into)
        .unwrap_or_default();
    let methods: Vec<String> = methods_csv
        .split(',')
        .filter(|s| !s.is_empty())
        .map(str::to_string)
        .collect();
    match &state.engine {
        EngineImpl::Lua(engine) => {
            let _ = engine.define(&bridge, &methods);
        }
        EngineImpl::Js(engine) => {
            let _ = engine.define(&bridge, &methods);
        }
    }
    state.bridges.insert(bridge, methods);
}

#[no_mangle]
pub extern "system" fn Java_heizige_kk_khatkit_engine_KhatKitNative_nativeDefineModule(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    name: JString,
    source: JString,
) {
    if handle == 0 {
        return;
    }
    let state = unsafe { &mut *(handle as *mut EngineState) };
    let name: String = env.get_string(&name).map(Into::into).unwrap_or_default();
    let source: String = env
        .get_string(&source)
        .map(Into::into)
        .unwrap_or_default();
    match &state.engine {
        EngineImpl::Lua(engine) => {
            let _ = engine.define_module(&name, &source);
        }
        EngineImpl::Js(engine) => {
            let _ = engine.define_module(&name, &source);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_heizige_kk_khatkit_engine_KhatKitNative_nativeEval(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    script: JString,
    args: JString,
) -> jstring {
    if handle == 0 {
        return std::ptr::null_mut();
    }
    let state = unsafe { &mut *(handle as *mut EngineState) };
    let script: String = env.get_string(&script).map(Into::into).unwrap_or_default();
    let args: String = env.get_string(&args).map(Into::into).unwrap_or_default();

    let outcome = catch_unwind(AssertUnwindSafe(|| -> Result<String, String> {
        match &state.engine {
            EngineImpl::Lua(engine) => engine.eval(&script, &args).map_err(|e| e.to_string()),
            EngineImpl::Js(engine) => engine.eval(&script, &args).map_err(|e| e.to_string()),
        }
    }));

    let json = match outcome {
        Ok(Ok(value)) => value,
        Ok(Err(err)) => format!("{{\"__error\":\"{}\"}}", escape_json(&err.to_string())),
        Err(_) => "{\"__error\":\"native panic\"}".to_string(),
    };

    match env.new_string(json) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_heizige_kk_khatkit_engine_KhatKitNative_nativeClose(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    unsafe {
        drop(Box::from_raw(handle as *mut EngineState));
    }
}
