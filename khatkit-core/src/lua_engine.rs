use crate::host::Host;
use crate::json_util::{json_to_lua, value_to_json};
use serde_json::Value as Json;
use mlua::{Lua, Value, Variadic};
use std::sync::Arc;

/// mlua（Lua 5.4）实现。bridge 以「绑定方法表」注入，脚本用点号语法调用。
pub struct LuaEngine {
    lua: Lua,
    host: Arc<Host>,
}

impl LuaEngine {
    pub fn new(host: Arc<Host>) -> mlua::Result<Self> {
        Ok(Self {
            lua: Lua::new(),
            host,
        })
    }

    pub fn define(&self, bridge: &str, methods: &[String]) -> mlua::Result<()> {
        let table = self.lua.create_table()?;
        for method in methods {
            let host = self.host.clone();
            let bridge_name = bridge.to_string();
            let method_name = method.clone();
            let func = self.lua.create_function(move |lua, args: Variadic<Value>| {
                let json_args = Json::Array(args.iter().map(value_to_json).collect());
                let result = host.dispatch(&bridge_name, &method_name, &json_args.to_string());
                let parsed: Json = serde_json::from_str(&result).unwrap_or(Json::Null);
                json_to_lua(lua, &parsed)
            })?;
            table.set(method.as_str(), func)?;
        }
        self.lua.globals().set(bridge, table)?;
        Ok(())
    }

    /// 注册脚本库：require(name) 时执行 source 并返回其返回值。
    pub fn define_module(&self, name: &str, source: &str) -> mlua::Result<()> {
        let package: mlua::Table = self.lua.globals().get("package")?;
        let preload: mlua::Table = package.get("preload")?;
        let source = source.to_string();
        let loader = self.lua.create_function(move |lua, _args: mlua::MultiValue| {
            lua.load(source.as_str()).eval::<Value>()
        })?;
        preload.set(name, loader)
    }

    pub fn eval(&self, script: &str, args_json: &str) -> mlua::Result<String> {
        let args_value: serde_json::Value =
            serde_json::from_str(args_json).unwrap_or(serde_json::Value::Null);
        let args_lua = json_to_lua(&self.lua, &args_value)?;
        self.lua.globals().set("args", args_lua)?;
        let result: Value = self.lua.load(script).eval()?;
        Ok(value_to_json(&result).to_string())
    }
}
