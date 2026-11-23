use super::codec;
use super::glyph;
use serde_json::{json, Value};

fn width() -> Value {
    json!({"__mod":[["fillMaxWidth"]]})
}
fn panel(radius: u8, padding: u8, children: Vec<Value>) -> Value {
    json!({"kind":"Column","spacing":8,"modifier":{"__mod":[["fillMaxWidth"],["clip","rounded",radius],["background","surface_container"],["padding",padding]]},"children":children})
}
fn hero(title: &str) -> Value {
    panel(
        32,
        24,
        vec![
            json!({"kind":"Icon","name":"text_fields","size":48,"tone":"primary"}),
            json!({"kind":"Text","text":title,"size":34,"weight":"bold"}),
        ],
    )
}
fn button(label: &str, action: &str) -> Value {
    json!({"kind":"Button","label":label,"action":action,"tone":"primary","modifier":width()})
}
fn field_group(title: &str, text: &Value) -> Value {
    panel(
        20,
        16,
        vec![
            json!({"kind":"Text","text":title,"style":"label","tone":"muted"}),
            json!({"kind":"Text","text":text,"style":"code"}),
        ],
    )
}
fn page(title: &str, children: Vec<Value>, back: &str, push: bool) -> Value {
    json!({"method":"ui.screen","args":[title,{"kind":"Column","spacing":10,"children":children},
        {"fullscreen":true,"hide_bottombar":true,"layer":true,"push":push,"back_event":back}]})
}

const PAGE_SIZE: usize = 24;

fn glyph_pages(
    text: &str,
    screen: &mut impl FnMut(Value) -> Result<Value, String>,
) -> Result<bool, String> {
    let chars: Vec<char> = text.chars().collect();
    let pages = chars.len().div_ceil(PAGE_SIZE).max(1);
    let mut index = 0;
    let mut push = true;
    loop {
        let rows: Vec<Value> = chars.iter().skip(index * PAGE_SIZE).take(PAGE_SIZE).map(|&c| {
            let glyph = glyph::describe(c);
            json!({"kind":"ListRow","label":glyph["code_point"],"value":if glyph["preview"] == "" { &glyph["kind"] } else { &glyph["preview"] },
                "caption":glyph["name"].as_str().unwrap_or_else(|| glyph["kind"].as_str().unwrap())})
        }).collect();
        let mut nodes = vec![
            hero("字形查看"),
            field_group("序列预览", &json!(text)),
            json!({"kind":"Group","children":rows}),
        ];
        if pages > 1 {
            nodes.push(
                json!({"kind":"Text","text":format!("{} / {}", index+1, pages),"style":"label"}),
            );
            if index > 0 {
                nodes.push(button("上一页", "prev"));
            }
            if index + 1 < pages {
                nodes.push(button("下一页", "next"));
            }
        }
        let reply = screen(page("字形查看", nodes, "glyph_close", push))?;
        push = false;
        match reply["event"].as_str() {
            Some("next") if index + 1 < pages => index += 1,
            Some("prev") if index > 0 => index -= 1,
            Some("glyph_close") => return Ok(true),
            _ if reply.is_null() => return Ok(false),
            _ => return Err("未知的字形操作".into()),
        }
    }
}

pub fn interactive(
    mut screen: impl FnMut(Value) -> Result<Value, String>,
) -> Result<Value, String> {
    let mut text = String::new();
    let mut decode = false;
    let mut error = None;
    loop {
        let mut nodes = vec![
            hero("Unicode 工具"),
            panel(
                20,
                16,
                vec![
                    json!({"kind":"Selector","id":"mode","action":"mode_changed","options":["编码","解码"],"value":if decode {"解码"} else {"编码"}}),
                    json!({"kind":"TextField","id":"text","label":if decode {"Unicode 编码"} else {"文本"},"placeholder":if decode {"U+4E2D U+1F600"} else {"A中😀"},"value":text,"multiline":true}),
                    button("转换", "submit"),
                    json!({"kind":"Button","label":"查看字形","icon":"text_fields","action":"glyphs","modifier":width()}),
                ],
            ),
        ];
        if let Some(message) = error.take() {
            nodes.push(json!({"kind":"Text","text":message,"tone":"error"}));
        }
        let reply = screen(page("Unicode 工具", nodes, "card_close", false))?;
        let action = reply["event"].as_str();
        if reply.is_null() || action == Some("card_close") {
            return Ok(json!({"closed":true}));
        }
        if let Some(s) = reply["values"]["text"].as_str() {
            text = s.to_owned();
        }
        if let Some(s) = reply["values"]["mode"].as_str() {
            decode = s == "解码";
        }
        if action == Some("mode_changed") {
            continue;
        }
        if !matches!(action, Some("submit" | "glyphs")) {
            return Err("未知的卡片操作".into());
        }
        match codec::convert(if decode { "decode" } else { "encode" }, &text) {
            Err(reason) => error = Some(reason),
            Ok(out) if action == Some("glyphs") => {
                if out["count"] == 0 {
                    error = Some("请输入要查看的字符或码点");
                } else if !glyph_pages(out["text"].as_str().unwrap(), &mut screen)? {
                    return Ok(json!({"closed":true}));
                }
            }
            Ok(out) => {
                let nodes = vec![
                    hero("转换结果"),
                    field_group("文本", &out["text"]),
                    field_group("Unicode 码点", &out["code_points"]),
                    field_group("UTF-16 转义", &out["utf16"]),
                ];
                if screen(page("转换结果", nodes, "result_close", true))?.is_null() {
                    return Ok(json!({"closed":true}));
                }
            }
        }
    }
}
