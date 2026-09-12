use crate::host::Host;
use rquickjs::{Context, Function, Runtime};
use std::sync::Arc;

/// CommonJS 形状的 require；模块源码在 `__khatkitModules` 里按名字存放。
const REQUIRE_BOOTSTRAP: &str = r#"
globalThis.__khatkitModules = globalThis.__khatkitModules || {};
globalThis.__khatkitCache = globalThis.__khatkitCache || {};
globalThis.require = function (name) {
  if (Object.prototype.hasOwnProperty.call(globalThis.__khatkitCache, name)) {
    return globalThis.__khatkitCache[name];
  }
  var src = globalThis.__khatkitModules[name];
  if (src === undefined) throw new Error('module not found: ' + name);
  var module = { exports: {} };
  new Function('module', 'exports', 'require', src)(module, module.exports, globalThis.require);
  globalThis.__khatkitCache[name] = module.exports;
  return module.exports;
};
"#;

/// rquickjs（QuickJS）实现。与 Lua 引擎共享同一套卡片 API。
pub struct JsEngine {
    _rt: Runtime,
    ctx: Context,
    host: Arc<Host>,
}

impl JsEngine {
    pub fn new(host: Arc<Host>) -> rquickjs::Result<Self> {
        let rt = Runtime::new()?;
        let ctx = Context::full(&rt)?;
        Ok(Self {
            _rt: rt,
            ctx,
            host,
        })
    }

    pub fn define(&self, bridge: &str, methods: &[String]) -> rquickjs::Result<()> {
        self.ctx.with(|ctx| {
            let host = self.host.clone();
            let func = Function::new(
                ctx.clone(),
                move |b: String, m: String, a: String| -> String { host.dispatch(&b, &m, &a) },
            )?;
            ctx.globals().set("__khatkitCall", func)?;
            let shim = build_shim(bridge, methods);
            ctx.eval::<rquickjs::Value, _>(shim)?;
            Ok(())
        })
    }

    /// 注册脚本库：注入 CommonJS 形状的 require。
    pub fn define_module(&self, name: &str, source: &str) -> rquickjs::Result<()> {
        self.ctx.with(|ctx| {
            let has_require =
                ctx.eval::<bool, _>("typeof globalThis.require === 'function'").unwrap_or(false);
            if !has_require {
                ctx.eval::<rquickjs::Value, _>(REQUIRE_BOOTSTRAP)?;
            }
            let name_js = serde_json::to_string(name).unwrap_or_else(|_| "\"\"".to_string());
            let source_js = serde_json::to_string(source).unwrap_or_else(|_| "\"\"".to_string());
            ctx.eval::<rquickjs::Value, _>(format!(
                "globalThis.__khatkitModules[{name_js}] = {source_js};"
            ))?;
            Ok(())
        })
    }

    pub fn eval(&self, script: &str, args_json: &str) -> rquickjs::Result<String> {
        self.ctx.with(|ctx| {
            ctx.eval::<rquickjs::Value, _>(format!("globalThis.args = {args_json};"))?;
            let wrapped = format!(
                "(function(){{ var __r = (function(){{\n{script}\n}}).call(globalThis); \
                 return JSON.stringify(__r === undefined ? null : __r); }})()"
            );
            ctx.eval::<String, _>(wrapped)
        })
    }
}

fn build_shim(bridge: &str, methods: &[String]) -> String {
    let mut shim = String::with_capacity(64 + methods.len() * 96);
    shim.push_str("globalThis.");
    shim.push_str(bridge);
    shim.push_str(" = {");
    for method in methods {
        shim.push_str(method);
        shim.push_str(": function(){ return JSON.parse(__khatkitCall('");
        shim.push_str(bridge);
        shim.push_str("','");
        shim.push_str(method);
        shim.push_str("', JSON.stringify(Array.prototype.slice.call(arguments)))); },");
    }
    shim.push_str("};");
    shim
}
