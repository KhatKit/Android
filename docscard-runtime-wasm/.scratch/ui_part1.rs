//! UI 宿主：ui.* capability 的宿主侧实现（ABI v1 4.4 / 4.6）。
//!
//! 卡片发 {"method":"ui.form","args":[title, tree, options?]}，这里负责三件事：
//! 1. 校验节点树（组件/属性白名单、深度 <=32、节点 <=512、重复 id、容器/叶子、
//!    组件必填属性），与 Kotlin 侧 UiNodeParser 同一套规则；
//! 2. 规范化：把卡片侧的扁平写法 {"kind":"Column", ...props} 转成宿主内部格式
//!    {"__ui":"Column","props":{...},"children":[...]}，Kotlin 宿主可直接复用既有渲染链；
//! 3. 呈现：交给 UiPresenter（CLI 用脚本应答，Kotlin 走 bridge dispatcher，测试用断言）。
//!
//! 应答语义（与 Lua bridge 一致）：form -> {id: value} 或 null；screen ->
//! {"event":...,"values":{...}} 或 null；show/progress -> null；confirm -> bool；
//! isCancelled -> bool。校验失败按 4.4 返回 {"__error":"..."}，卡片自己翻译。

use std::collections::HashSet;

use serde_json::{Map, Value};

use crate::host::{CardHost, HostError};

/// 树的最大深度（4.6）。
pub const MAX_DEPTH: u32 = 32;
/// 树的最大节点数（4.6）。
pub const MAX_NODES: usize = 512;

/// 叶子组件（不能有 children）。
const LEAVES: &[&str] = &[
    "Spacer", "Divider", "Text", "Icon", "Markdown", "Image", "Badge", "ProgressBar", "TextField",
    "NumberField", "Slider", "Switch", "Checkbox", "Select", "RadioGroup", "FilePicker",
    "DirPicker", "CounterButton", "Button", "IconButton",
];

/// 表单字段组件（必须有唯一 id）。
const FIELDS: &[&str] = &[
    "TextField", "NumberField", "Slider", "Switch", "Checkbox", "Select", "RadioGroup",
    "FilePicker", "DirPicker", "CounterButton",
];

/// 属性值类型（与 Kotlin UiNodeParser 的 schema 字符串一一对应）。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum PropKind {
    Number,
    Str,
    Bool,
    Color,
    Icon,
    Strings,
    Modifier,
    Paths,
    Enum(&'static [&'static str]),
}

/// 每个组件的专属属性（id/label/hint/required/value 由字段表统一追加）。
fn specific_props(kind: &str) -> Option<&'static [(&'static str, PropKind)]> {
    let table: &'static [(&'static str, PropKind)] = match kind {
        "Column" => &[
            ("spacing", PropKind::Number),
            ("hAlign", PropKind::Enum(&["start", "center", "end"])),
            ("scrollable", PropKind::Bool),
            ("modifier", PropKind::Modifier),
        ],
        "Row" => &[
            ("spacing", PropKind::Number),
            ("vAlign", PropKind::Enum(&["start", "center", "end", "space_between"])),
            ("modifier", PropKind::Modifier),
        ],
        "Box" => &[
            ("hAlign", PropKind::Enum(&["start", "center", "end"])),
            ("vAlign", PropKind::Enum(&["start", "center", "end"])),
            ("modifier", PropKind::Modifier),
        ],
        "Spacer" => &[("height", PropKind::Number), ("width", PropKind::Number)],
        "Divider" => &[],
        "Card" => &[("title", PropKind::Str), ("modifier", PropKind::Modifier)],
        "Section" => &[("title", PropKind::Str), ("modifier", PropKind::Modifier)],
        "Text" => &[
            ("text", PropKind::Str),
            ("style", PropKind::Enum(&["title", "subtitle", "body", "label", "code"])),
            ("tone", PropKind::Enum(&["default", "muted", "primary", "error"])),
            ("bold", PropKind::Bool),
            ("color", PropKind::Color),
            ("size", PropKind::Number),
            ("weight", PropKind::Enum(&["normal", "medium", "semibold", "bold"])),
            ("modifier", PropKind::Modifier),
        ],
        "Icon" => &[
            ("name", PropKind::Icon),
            ("tone", PropKind::Enum(&["default", "muted", "primary", "error", "success", "warning"])),
            ("color", PropKind::Color),
            ("size", PropKind::Number),
            ("contentDescription", PropKind::Str),
            ("modifier", PropKind::Modifier),
        ],
        "Markdown" => &[("text", PropKind::Str), ("modifier", PropKind::Modifier)],
        "Image" => &[
            ("src", PropKind::Str),
            ("contentDescription", PropKind::Str),
            ("modifier", PropKind::Modifier),
        ],
        "Badge" => &[
            ("text", PropKind::Str),
            ("tone", PropKind::Enum(&["default", "primary", "success", "warning", "error"])),
            ("color", PropKind::Color),
        ],
        "ProgressBar" => &[("ratio", PropKind::Number), ("label", PropKind::Str)],
        "TextField" => &[
            ("placeholder", PropKind::Str),
            ("multiline", PropKind::Bool),
            ("lines", PropKind::Number),
        ],
        "NumberField" | "Slider" => &[
            ("min", PropKind::Number),
            ("max", PropKind::Number),
            ("step", PropKind::Number),
        ],
        "Switch" | "Checkbox" => &[],
        "Select" | "RadioGroup" => &[("options", PropKind::Strings)],
        "FilePicker" => &[
            ("filter", PropKind::Enum(&["image", "video", "audio", "document", "any"])),
            ("multiple", PropKind::Bool),
        ],
        "DirPicker" => &[],
        "CounterButton" => &[
            ("action", PropKind::Str),
            ("count", PropKind::Number),
            ("localCount", PropKind::Number),
            ("caption", PropKind::Str),
            ("revision", PropKind::Number),
            ("disabled", PropKind::Bool),
            ("tone", PropKind::Enum(&["primary", "default"])),
            ("modifier", PropKind::Modifier),
        ],
        "Button" => &[
            ("label", PropKind::Str),
            ("action", PropKind::Str),
            ("tone", PropKind::Enum(&["primary", "default", "danger"])),
            ("disabled", PropKind::Bool),
            ("icon", PropKind::Icon),
            ("color", PropKind::Color),
            ("modifier", PropKind::Modifier),
        ],
        "IconButton" => &[
            ("action", PropKind::Str),
            ("icon", PropKind::Icon),
            ("tone", PropKind::Enum(&["default", "muted", "primary", "error", "success", "warning"])),
            ("color", PropKind::Color),
            ("disabled", PropKind::Bool),
            ("contentDescription", PropKind::Str),
            ("modifier", PropKind::Modifier),
        ],
        _ => return None,
    };
    Some(table)
}

/// 允许的组件名。
pub fn components() -> Vec<&'static str> {
    [
        "Column", "Row", "Box", "Spacer", "Divider", "Card", "Section", "Text", "Icon",
        "Markdown", "Image", "Badge", "ProgressBar", "TextField", "NumberField", "Slider",
        "Switch", "Checkbox", "Select", "RadioGroup", "FilePicker", "DirPicker", "CounterButton",
        "Button", "IconButton",
    ]
    .into_iter()
    .filter(|kind| specific_props(kind).is_some())
    .collect()
}

fn is_leaf(kind: &str) -> bool {
    LEAVES.contains(&kind)
}

fn is_field(kind: &str) -> bool {
    FIELDS.contains(&kind)
}

fn field_value_kind(kind: &str) -> PropKind {
    match kind {
        "NumberField" | "Slider" | "CounterButton" => PropKind::Number,
        "Switch" | "Checkbox" => PropKind::Bool,
        "FilePicker" => PropKind::Paths,
        _ => PropKind::Str,
    }
}

fn prop_kind(kind: &str, key: &str) -> Option<PropKind> {
    let specific = specific_props(kind)?;
    if let Some((_, value)) = specific.iter().find(|(name, _)| *name == key) {
        return Some(*value);
    }
    if is_field(kind) {
        return match key {
            "id" | "label" | "hint" => Some(PropKind::Str),
            "required" => Some(PropKind::Bool),
            "value" => Some(field_value_kind(kind)),
            _ => None,
        };
    }
    None
}

fn fail(path: &str, reason: &str) -> String {
    format!("{path}: {reason}")
}

fn check_value(kind: PropKind, value: &Value, path: &str) -> Result<(), String> {
    match kind {
        PropKind::Number => {
            if value.is_number() {
                Ok(())
            } else {
                Err(fail(path, "expected number"))
            }
        }
        PropKind::Str | PropKind::Color | PropKind::Icon => {
            if value.is_string() {
                Ok(())
            } else {
                Err(fail(path, "expected string"))
            }
        }
        PropKind::Bool => {
            if value.is_boolean() {
                Ok(())
            } else {
                Err(fail(path, "expected boolean"))
            }
        }
        PropKind::Strings => match value.as_array() {
            Some(items) if items.iter().all(|item| item.is_string()) => Ok(()),
            _ => Err(fail(path, "expected array of strings")),
        },
        PropKind::Paths => match value.as_array() {
            Some(items) if items.iter().all(|item| item.is_string()) => Ok(()),
            _ if value.is_string() => Ok(()),
            _ => Err(fail(path, "expected path or array of paths")),
        },
        PropKind::Modifier => {
            if value.is_object() {
                Ok(())
            } else {
                Err(fail(path, "expected object"))
            }
        }
        PropKind::Enum(options) => match value.as_str() {
            Some(text) if options.contains(&text) => Ok(()),
            _ => Err(fail(path, &format!("expected one of {}", options.join("|")))),
        },
    }
}
