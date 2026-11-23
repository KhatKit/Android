use super::catalog::{lookup, search, Recipe};
use serde_json::{json, Value};

fn page(title: &str, children: Vec<Value>, back: &str, push: bool) -> Value {
    json!({"method":"ui.screen","args":[title,{"kind":"Column","spacing":12,"children":children},
        {"fullscreen":true,"hide_bottombar":true,"layer":true,"push":push,"back_event":back}]})
}

fn action(label: &str, id: &str, icon: &str) -> Value {
    json!({"kind":"Button","label":label,"action":id,"icon":icon})
}

fn list(query: &str) -> Value {
    let mut controls = vec![action("搜索", "search", "search")];
    if !query.is_empty() {
        controls.push(action("清空", "clear", "close"));
    }
    let mut nodes = vec![
        json!({"kind":"TextField","id":"query","label":"菜名或食材","placeholder":"番茄、鸡蛋","value":query}),
        json!({"kind":"Row","spacing":8,"children":controls}),
    ];
    match search(query) {
        Ok(hits) if hits.is_empty() => {
            nodes.push(json!({"kind":"Text","text":"没有找到匹配的菜谱","tone":"muted"}))
        }
        Err(reason) => nodes.push(json!({"kind":"Text","text":reason,"tone":"error"})),
        Ok(hits) => {
            let rows: Vec<_> = hits.iter().map(|r| json!({"kind":"ListRow","label":r.name,
                "caption":r.ingredients.iter().take(2).map(|i|i.0).collect::<Vec<_>>().join("、"),
                "value":format!("{} 分钟",r.minutes),"chevron":true,"action":format!("open:{}",r.id)})).collect();
            nodes.push(json!({"kind":"Group","children":rows}));
        }
    }
    page("家常菜谱", nodes, "close_book", false)
}

const SERVINGS: [&str; 4] = ["1 人", "2 人", "3 人", "4 人"];

fn detail(recipe: &Recipe, servings: u8, push: bool) -> Value {
    let data = recipe.detail(servings);
    let rows: Vec<_> = data["ingredients"].as_array().unwrap().iter().map(|i| json!(
        {"kind":"ListRow","label":i["name"],"value":format!("{} {}",i["amount"],i["unit"].as_str().unwrap())}
    )).collect();
    let mut nodes = vec![
        json!({"kind":"Text","text":format!("约 {} 分钟",recipe.minutes),"style":"label","tone":"muted"}),
        json!({"kind":"Selector","id":"servings","action":"scale","options":SERVINGS,"value":SERVINGS[servings as usize-1]}),
        json!({"kind":"Section","title":"食材","children":[{"kind":"Group","children":rows}]}),
    ];
    nodes.push(json!({"kind":"Section","title":"做法","children":recipe.steps.iter().enumerate().map(|(i,s)|
        json!({"kind":"Text","text":format!("{}. {}",i+1,s)})).collect::<Vec<_>>()}));
    page(recipe.name, nodes, "close_recipe", push)
}

pub(super) fn browse(
    mut present: impl FnMut(Value) -> Result<Value, String>,
) -> Result<Value, String> {
    let mut query = String::new();
    let mut selected: Option<&Recipe> = None;
    let mut servings = 2;
    let mut push = false;
    loop {
        let screen = match selected {
            Some(recipe) => detail(recipe, servings, push),
            None => list(&query),
        };
        let reply = present(screen)?;
        if reply.is_null() {
            break;
        }
        push = false;
        let event = reply["event"].as_str().ok_or("菜谱操作缺少事件")?;
        if selected.is_none() {
            if let Some(text) = reply["values"]["query"].as_str() {
                query = text.to_owned();
            }
            match event {
                "close_book" => break,
                "search" => (),
                "clear" => query.clear(),
                _ => {
                    selected = Some(
                        event
                            .strip_prefix("open:")
                            .and_then(lookup)
                            .ok_or("未知菜谱")?,
                    );
                    servings = 2;
                    push = true;
                }
            }
        } else {
            match event {
                "close_recipe" => selected = None,
                "scale" => {
                    servings = SERVINGS
                        .iter()
                        .position(|s| reply["values"]["servings"] == *s)
                        .ok_or("无效的份量")? as u8
                        + 1
                }
                _ => return Err("未知的菜谱操作".into()),
            }
        }
    }
    Ok(json!({"closed":true}))
}
