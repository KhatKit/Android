mod catalog;
mod ui;

use catalog::{lookup, search, RECIPES};
use serde_json::{json, Value};

pub fn run(
    event: Value,
    mut present: impl FnMut(Value) -> Result<Value, String>,
) -> Result<Value, String> {
    match event["type"].as_str() {
        Some("widget") => {
            present(
                json!({"method":"ui.show","args":[{"kind":"Text","text":format!("{} 道家常菜", RECIPES.len()),"style":"label"},{"frame":"header"}]}),
            )?;
            return Ok(json!({"ok":true}));
        }
        Some("run") => (),
        _ => return Err("不支持的菜谱事件".into()),
    }
    let args = &event["payload"];
    if !args.is_null() && !args.is_object() {
        return Err("参数须为对象".into());
    }
    match (
        args.get("recipe_id"),
        args.get("query"),
        args.get("servings"),
    ) {
        (Some(_), Some(_), _) => Err("请分别搜索或读取详情".into()),
        (Some(id), None, servings) => {
            let recipe = lookup(id.as_str().ok_or("菜谱 ID 须是字符串")?).ok_or("没有这道菜谱")?;
            let servings = match servings {
                None => 2,
                Some(s) => s
                    .as_u64()
                    .filter(|n| (1..=4).contains(n))
                    .ok_or("人数须为 1 至 4 的整数")? as u8,
            };
            Ok(recipe.detail(servings))
        }
        (None, _, Some(_)) => Err("请先指定菜谱 ID".into()),
        (None, Some(query), None) => {
            let hits = search(query.as_str().ok_or("搜索词须为字符串")?)?;
            Ok(
                json!({"count":hits.len(),"recipes":hits.iter().map(|r|r.summary()).collect::<Vec<_>>()}),
            )
        }
        (None, None, None) => ui::browse(present),
    }
}

#[cfg(target_arch = "wasm32")]
mod abi;
#[cfg(test)]
mod tests;
