use mlua::{Lua, Value};
use serde_json::Value as Json;

/// Lua 值 -> JSON（用于把脚本参数回传宿主、以及收敛脚本返回值）。
pub fn value_to_json(value: &Value) -> Json {
    match value {
        Value::Nil => Json::Null,
        Value::Boolean(b) => Json::Bool(*b),
        Value::Integer(i) => Json::Number((*i).into()),
        Value::Number(n) => serde_json::Number::from_f64(*n)
            .map(Json::Number)
            .unwrap_or(Json::Null),
        Value::String(s) => Json::String(s.to_str().map(|s| s.to_string()).unwrap_or_default()),
        Value::Table(t) => table_to_json(t),
        _ => Json::Null,
    }
}

fn table_to_json(table: &mlua::Table) -> Json {
    let len = table.raw_len();
    if len > 0 {
        let mut array = Vec::with_capacity(len);
        for i in 1..=len {
            if let Ok(v) = table.raw_get::<Value>(i as i64) {
                array.push(value_to_json(&v));
            }
        }
        return Json::Array(array);
    }
    let mut map = serde_json::Map::new();
    for pair in table.clone().pairs::<Value, Value>() {
        if let Ok((k, v)) = pair {
            map.insert(key_to_string(&k), value_to_json(&v));
        }
    }
    Json::Object(map)
}

fn key_to_string(value: &Value) -> String {
    match value {
        Value::String(s) => s.to_str().map(|s| s.to_string()).unwrap_or_default(),
        Value::Integer(i) => i.to_string(),
        Value::Number(n) => n.to_string(),
        Value::Boolean(b) => b.to_string(),
        _ => String::new(),
    }
}

/// JSON -> Lua 值（用于注入 args）。
pub fn json_to_lua(lua: &Lua, value: &Json) -> mlua::Result<Value> {
    Ok(match value {
        Json::Null => Value::Nil,
        Json::Bool(b) => Value::Boolean(*b),
        Json::Number(n) => match n.as_i64() {
            Some(i) => Value::Integer(i),
            None => Value::Number(n.as_f64().unwrap_or(0.0)),
        },
        Json::String(s) => Value::String(lua.create_string(s)?),
        Json::Array(items) => {
            let table = lua.create_table()?;
            for (index, item) in items.iter().enumerate() {
                table.raw_set((index + 1) as i64, json_to_lua(lua, item)?)?;
            }
            Value::Table(table)
        }
        Json::Object(map) => {
            let table = lua.create_table()?;
            for (key, item) in map {
                table.raw_set(key.as_str(), json_to_lua(lua, item)?)?;
            }
            Value::Table(table)
        }
    })
}
