use jni::objects::{GlobalRef, JString, JValue};
use jni::{JNIEnv, JavaVM};

/// 宿主回调：把脚本里的 bridge 调用转回 JVM 侧的 Kotlin 对象。
///
/// Rust 引擎不持有具体 bridge 实现，只拿一个 `dispatch(bridge, method, argsJson)`
/// 的分发器，保持 native 层与业务解耦。
pub struct Host {
    jvm: JavaVM,
    dispatcher: GlobalRef,
}

unsafe impl Send for Host {}
unsafe impl Sync for Host {}

impl Host {
    pub fn new(jvm: JavaVM, dispatcher: GlobalRef) -> Self {
        Self { jvm, dispatcher }
    }

    pub fn dispatch(&self, bridge: &str, method: &str, args_json: &str) -> String {
        let mut guard = match self.jvm.attach_current_thread() {
            Ok(g) => g,
            Err(e) => return error_json(&format!("attach failed: {e}")),
        };
        let env: &mut JNIEnv = &mut guard;
        match dispatch_inner(env, &self.dispatcher, bridge, method, args_json) {
            Ok(s) => s,
            Err(e) => {
                let _ = env.exception_describe();
                let _ = env.exception_clear();
                error_json(&format!("dispatch failed: {e}"))
            }
        }
    }

    pub fn interrupt_reason(&self) -> i32 {
        let Ok(mut env) = self.jvm.attach_current_thread() else { return 1 };
        match env.call_method(self.dispatcher.as_obj(), "interruptReason", "()I", &[]) {
            Ok(value) => value.i().unwrap_or(1),
            Err(_) => {
                let _ = env.exception_clear();
                1
            }
        }
    }

    pub fn wasm_allowed(&self, cap: &str, method: &str, args: &str) -> bool {
        let Ok(mut env) = self.jvm.attach_current_thread() else { return false };
        let result = (|| -> jni::errors::Result<bool> {
            let cap = env.new_string(cap)?;
            let method = env.new_string(method)?;
            let args = env.new_string(args)?;
            env.call_method(self.dispatcher.as_obj(), "wasmAllowed",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z",
                &[JValue::Object(&cap), JValue::Object(&method), JValue::Object(&args)])?.z()
        })();
        if result.is_err() { let _ = env.exception_clear(); }
        result.unwrap_or(false)
    }
}

fn dispatch_inner(
    env: &mut JNIEnv,
    dispatcher: &GlobalRef,
    bridge: &str,
    method: &str,
    args_json: &str,
) -> jni::errors::Result<String> {
    let b = env.new_string(bridge)?;
    let m = env.new_string(method)?;
    let a = env.new_string(args_json)?;
    let result = env.call_method(
        dispatcher.as_obj(),
        "dispatch",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
        &[JValue::Object(&b), JValue::Object(&m), JValue::Object(&a)],
    )?;
    let obj = result.l()?;
    let string_ref = JString::from(obj);
    let js = env.get_string(&string_ref)?;
    Ok(js.into())
}

fn error_json(message: &str) -> String {
    serde_json::json!({"__error": message}).to_string()
}
