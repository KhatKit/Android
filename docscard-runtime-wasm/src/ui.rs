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
    "DirPicker", "CounterButton", "Stepper", "Selector", "Button", "IconButton", "ListRow",
    "ChipRow", "Dropdown",
];

/// 表单字段组件（必须有唯一 id）。
const FIELDS: &[&str] = &[
    "TextField", "NumberField", "Slider", "Switch", "Checkbox", "Select", "RadioGroup",
    "FilePicker", "DirPicker", "CounterButton", "Stepper", "Selector", "ChipRow", "Dropdown",
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
    /// 字符串或字符串数组（ChipRow 的 value：单选/多选共用同一字段）。
    StrOrStrings,
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
        // 设置页那种「iOS inset grouped」的一整组行。
        "Group" => &[("modifier", PropKind::Modifier)],
        // 分组里的一行（设置项）：前导图标 + 标题 + 行尾值 + 可选对勾/箭头。
        "ListRow" => &[
            ("label", PropKind::Str),
            ("value", PropKind::Str),
            ("icon", PropKind::Icon),
            ("action", PropKind::Str),
            ("chevron", PropKind::Bool),
            ("selected", PropKind::Bool),
            ("disabled", PropKind::Bool),
            ("caption", PropKind::Str),
            ("tone", PropKind::Enum(&["primary", "warning", "danger"])),
            ("modifier", PropKind::Modifier),
        ],
        "Text" => &[
            ("text", PropKind::Str),
            ("style", PropKind::Enum(&["title", "subtitle", "body", "label", "code"])),
            ("tone", PropKind::Enum(&["default", "muted", "primary", "error"])),
            ("bold", PropKind::Bool),
            ("color", PropKind::Color),
            ("size", PropKind::Number),
            ("weight", PropKind::Enum(&["normal", "medium", "semibold", "bold"])),
            // Mochi 内容变化里的数字计数器：数字位是滚动轮，值变了滚过去。
            ("counter", PropKind::Bool),
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
            ("icon", PropKind::Icon),
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
        // Mochi 步进器：id/value 由字段表追加，value 必须落在 min..max。
        "Stepper" => &[
            ("action", PropKind::Str),
            ("min", PropKind::Number),
            ("max", PropKind::Number),
            ("step", PropKind::Number),
            ("showValue", PropKind::Bool),
            ("disabled", PropKind::Bool),
            ("revision", PropKind::Number),
            ("modifier", PropKind::Modifier),
        ],
        // Mochi 分段选择器：id/value 由字段表追加，选项为字符串数组。
        "Selector" => &[
            ("action", PropKind::Str),
            ("options", PropKind::Strings),
            ("disabled", PropKind::Bool),
            ("modifier", PropKind::Modifier),
        ],
        // Mochi 芯片行：单选（value 字符串）或多选（value 字符串数组）。
        "ChipRow" => &[
            ("action", PropKind::Str),
            ("options", PropKind::Strings),
            ("icons", PropKind::Strings),
            ("multiple", PropKind::Bool),
            ("wrap", PropKind::Bool),
            ("disabled", PropKind::Bool),
            ("modifier", PropKind::Modifier),
        ],
        // Mochi 下拉选择：按钮槽显示 label + 当前值，点开菜单原地回填。
        "Dropdown" => &[
            ("action", PropKind::Str),
            ("options", PropKind::Strings),
            ("icon", PropKind::Icon),
            ("disabled", PropKind::Bool),
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
        "Stepper", "Selector", "Button", "IconButton", "Group", "ListRow", "ChipRow", "Dropdown",
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
        "NumberField" | "Slider" | "CounterButton" | "Stepper" => PropKind::Number,
        "Switch" | "Checkbox" => PropKind::Bool,
        "FilePicker" => PropKind::Paths,
        // ChipRow 的 value 单选是字符串、多选是字符串数组，两种都收。
        "ChipRow" => PropKind::StrOrStrings,
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
        PropKind::StrOrStrings => match value {
            Value::String(_) => Ok(()),
            Value::Array(items) if items.iter().all(|item| item.is_string()) => Ok(()),
            _ => Err(fail(path, "expected string or array of strings")),
        },
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

/// 把卡片侧的扁平节点 {"kind":"Column", ...} 校验并规范化成宿主格式。
///
/// 已是宿主格式（带 __ui 与 props）的输入也接受，props 里的属性同样按 schema 校验，
/// 方便宿主把 Kotlin 侧构造的树回喂给这里做一致性检查。
pub fn normalize_tree(raw: &Value) -> Result<Value, String> {
    let mut ids: HashSet<String> = HashSet::new();
    let mut count = 0usize;
    normalize_node(raw, 1, &mut ids, &mut count)
}

fn normalize_node(
    raw: &Value,
    depth: u32,
    ids: &mut HashSet<String>,
    count: &mut usize,
) -> Result<Value, String> {
    let node = raw.as_object().ok_or_else(|| fail("node", "expected object"))?;
    let kind = node
        .get("kind")
        .or_else(|| node.get("__ui"))
        .and_then(|value| value.as_str())
        .ok_or_else(|| fail("kind", "expected component name"))?
        .to_string();
    if specific_props(&kind).is_none() {
        return Err(fail(&kind, "unknown component"));
    }
    if depth > MAX_DEPTH {
        return Err(fail(&kind, "tree depth exceeds 32"));
    }
    *count += 1;
    if *count > MAX_NODES {
        return Err(fail(&kind, "tree node count exceeds 512"));
    }
    let has_props_wrapper = node.contains_key("props");
    if has_props_wrapper {
        for key in node.keys() {
            if key != "__ui" && key != "kind" && key != "props" && key != "children" {
                return Err(fail(&format!("{kind}.{key}"), "unknown node property"));
            }
        }
    }
    let props_source: Map<String, Value> = match (has_props_wrapper, node.get("props")) {
        (true, Some(Value::Object(props))) => props.clone(),
        (true, _) => return Err(fail(&format!("{kind}.props"), "expected object")),
        (false, _) => {
            let mut props = Map::new();
            for (key, value) in node {
                if key == "kind" || key == "__ui" || key == "children" {
                    continue;
                }
                props.insert(key.clone(), value.clone());
            }
            props
        }
    };
    for (key, value) in &props_source {
        let prop_type = prop_kind(&kind, key)
            .ok_or_else(|| fail(&format!("{kind}.{key}"), "unknown property"))?;
        check_value(prop_type, value, &format!("{kind}.{key}"))?;
    }
    if is_field(&kind) {
        let id = props_source.get("id").and_then(|value| value.as_str()).unwrap_or("");
        if id.trim().is_empty() {
            return Err(fail(&format!("{kind}.id"), "required"));
        }
        if !ids.insert(id.to_string()) {
            return Err(fail(&format!("{kind}.id"), &format!("duplicate {id}")));
        }
    }
    if kind == "Section" && props_source.get("title").and_then(|value| value.as_str()).is_none() {
        return Err(fail(&format!("{kind}.title"), "required"));
    }
    // ChipRow：2..24 个选项；icons 与 options 等长；value 必须落在 options 里。
    if kind == "ChipRow" {
        let options: Vec<&str> = props_source
            .get("options")
            .and_then(|value| value.as_array())
            .map(|items| items.iter().filter_map(|item| item.as_str()).collect())
            .unwrap_or_default();
        if !(2..=24).contains(&options.len()) {
            return Err(fail(&format!("{kind}.options"), "expected 2..24 items"));
        }
        if let Some(icons) = props_source.get("icons").and_then(|value| value.as_array()) {
            if !icons.is_empty() && icons.len() != options.len() {
                return Err(fail(&format!("{kind}.icons"), "expected same length as options"));
            }
        }
        let values: Vec<&str> = match props_source.get("value") {
            Some(Value::String(text)) => vec![text.as_str()],
            Some(Value::Array(items)) => items.iter().filter_map(|item| item.as_str()).collect(),
            _ => Vec::new(),
        };
        for value in values {
            if !options.contains(&value) {
                return Err(fail(&format!("{kind}.value"), "must be one of options"));
            }
        }
    }
    // Dropdown：2..50 个选项；value（若有）必须是其中一项。
    if kind == "Dropdown" {
        let options: Vec<&str> = props_source
            .get("options")
            .and_then(|value| value.as_array())
            .map(|items| items.iter().filter_map(|item| item.as_str()).collect())
            .unwrap_or_default();
        if !(2..=50).contains(&options.len()) {
            return Err(fail(&format!("{kind}.options"), "expected 2..50 items"));
        }
        if let Some(value) = props_source.get("value").and_then(|value| value.as_str()) {
            if !options.contains(&value) {
                return Err(fail(&format!("{kind}.value"), "must be one of options"));
            }
        }
    }
    if kind == "Button"
        && props_source
            .get("action")
            .and_then(|value| value.as_str())
            .unwrap_or("")
            .is_empty()
    {
        return Err(fail(&format!("{kind}.action"), "required"));
    }
    if kind == "IconButton" {
        let action = props_source.get("action").and_then(|value| value.as_str()).unwrap_or("");
        let icon = props_source.get("icon").and_then(|value| value.as_str()).unwrap_or("");
        if action.is_empty() || icon.is_empty() {
            return Err(fail(&format!("{kind}.action"), "action and icon are required"));
        }
    }
    if kind == "Icon"
        && props_source
            .get("name")
            .and_then(|value| value.as_str())
            .unwrap_or("")
            .is_empty()
    {
        return Err(fail(&format!("{kind}.name"), "required"));
    }
    if kind == "Image" {
        let src = props_source.get("src").and_then(|value| value.as_str()).unwrap_or("");
        if src.is_empty()
            || !(src.starts_with('/')
                || src.starts_with("content://")
                || src.starts_with("https://"))
        {
            return Err(fail(
                &format!("{kind}.src"),
                "expected local path, content URI or https URL",
            ));
        }
    }
    if kind == "Text" || kind == "Icon" {
        if let Some(size) = props_source.get("size").and_then(|value| value.as_f64()) {
            if !(6.0..=64.0).contains(&size) {
                return Err(fail(&format!("{kind}.size"), "expected 6..64"));
            }
        }
    }
    if kind == "ProgressBar" {
        if let Some(ratio) = props_source.get("ratio").and_then(|value| value.as_f64()) {
            if !(0.0..=1.0).contains(&ratio) {
                return Err(fail(&format!("{kind}.ratio"), "expected 0..1"));
            }
        }
    }
    for key in ["spacing", "width", "height", "lines"] {
        if let Some(number) = props_source.get(key).and_then(|value| value.as_f64()) {
            if number < 0.0 || (key == "lines" && (number < 1.0 || number.fract() != 0.0)) {
                return Err(fail(&format!("{kind}.{key}"), "invalid size"));
            }
        }
    }
    if let (Some(min), Some(max)) = (
        props_source.get("min").and_then(|value| value.as_f64()),
        props_source.get("max").and_then(|value| value.as_f64()),
    ) {
        if min >= max {
            return Err(fail(&format!("{kind}.max"), "must exceed min"));
        }
    }
    if let Some(step) = props_source.get("step").and_then(|value| value.as_f64()) {
        if step <= 0.0 {
            return Err(fail(&format!("{kind}.step"), "must be positive"));
        }
    }
    let children_raw = match node.get("children") {
        None | Some(Value::Null) => Vec::new(),
        Some(Value::Array(items)) => items.clone(),
        Some(_) => return Err(fail(&format!("{kind}.children"), "expected array")),
    };
    if is_leaf(&kind) && !children_raw.is_empty() {
        return Err(fail(&format!("{kind}.children"), "leaf cannot contain children"));
    }
    let mut children = Vec::with_capacity(children_raw.len());
    for child in &children_raw {
        children.push(normalize_node(child, depth + 1, ids, count)?);
    }
    let mut result = Map::new();
    result.insert("__ui".to_string(), Value::String(kind));
    result.insert("props".to_string(), Value::Object(props_source));
    result.insert("children".to_string(), Value::Array(children));
    Ok(Value::Object(result))
}

/// `ui.screen` 的分页树（`options.pages[*].tree`）同样是节点树：先校验+规范化，
/// 否则呈现方（Kotlin `UiNodeParser` / CLI 呈现方）会按宿主内部格式解析扁平写法而失败。
/// 每页各自独立计数（单页 <=512 节点、深度 <=32），互不影响。
pub fn normalize_screen_pages(options: &mut Value) -> Result<(), String> {
    let Value::Object(options) = options else { return Ok(()) };
    let Some(Value::Object(pages)) = options.get_mut("pages") else { return Ok(()) };
    for (id, page) in pages.iter_mut() {
        let Some(page) = page.as_object_mut() else {
            return Err(fail(&format!("pages.{id}"), "expected object"));
        };
        if let Some(tree) = page.get_mut("tree") {
            *tree = normalize_tree(tree)?;
        }
    }
    Ok(())
}

/// 一次 UI 交互请求（已校验/规范化）。
#[derive(Debug, Clone, PartialEq)]
pub struct UiRequest {
    pub method: String,
    pub title: Option<String>,
    /// form/show 的规范化节点树（__ui 形状）。
    pub tree: Option<Value>,
    pub options: Option<Value>,
    /// 原始 args（progress/confirm 等非树方法用）。
    pub args: Vec<Value>,
}

impl UiRequest {
    /// 节点数（日志/断言用）。
    pub fn node_count(&self) -> usize {
        fn count(value: &Value) -> usize {
            let mut total = 1;
            if let Some(children) = value.get("children").and_then(|value| value.as_array()) {
                total += children.iter().map(count).sum::<usize>();
            }
            total
        }
        self.tree.as_ref().map(count).unwrap_or(0)
    }

    /// 字段 id 列表（测试断言用）。
    pub fn field_ids(&self) -> Vec<String> {
        fn walk(value: &Value, out: &mut Vec<String>) {
            if let Some(kind) = value.get("__ui").and_then(|value| value.as_str()) {
                if FIELDS.contains(&kind) {
                    if let Some(id) = value
                        .get("props")
                        .and_then(|props| props.get("id"))
                        .and_then(|value| value.as_str())
                    {
                        out.push(id.to_string());
                    }
                }
            }
            if let Some(children) = value.get("children").and_then(|value| value.as_array()) {
                for child in children {
                    walk(child, out);
                }
            }
        }
        let mut out = Vec::new();
        if let Some(tree) = &self.tree {
            walk(tree, &mut out);
        }
        out
    }
}

/// UI 呈现方（CLI/Kotlin/测试各自实现）。
pub trait UiPresenter {
    fn present(&mut self, request: &UiRequest) -> Result<Value, HostError>;
}

/// 一律取消的呈现方（无交互环境：form/screen/show 返回 null，confirm 返回 false）。
#[derive(Debug, Default, Clone)]
pub struct NullUiPresenter;

impl UiPresenter for NullUiPresenter {
    fn present(&mut self, request: &UiRequest) -> Result<Value, HostError> {
        Ok(match request.method.as_str() {
            "ui.confirm" => Value::Bool(false),
            _ => Value::Null,
        })
    }
}

/// 把 ui.* 接给 presenter 的 CardHost 实现；其它 capability 由组合宿主继续处理。
pub struct UiRenderHost<P: UiPresenter> {
    presenter: P,
    requests: Vec<UiRequest>,
    cancelled: bool,
}

impl<P: UiPresenter> UiRenderHost<P> {
    pub fn new(presenter: P) -> Self {
        Self {
            presenter,
            requests: Vec::new(),
            cancelled: false,
        }
    }

    /// 已发生的 UI 交互（按顺序）。
    pub fn requests(&self) -> &[UiRequest] {
        &self.requests
    }

    pub fn set_cancelled(&mut self, cancelled: bool) {
        self.cancelled = cancelled;
    }

    fn reject(message: String) -> String {
        // DOCSCARD_UI_DEBUG=1 时把拒绝原因打到 stderr（CLI/回归诊断用，默认静默）。
        if std::env::var_os("DOCSCARD_UI_DEBUG").is_some() {
            eprintln!("[ui:reject] {message}");
        }
        format!("{{\"__error\":{}}}", Value::String(message).to_string())
    }
}

impl<P: UiPresenter> CardHost for UiRenderHost<P> {
    fn host_call(&mut self, cap: &str, request_json: &str) -> Result<String, HostError> {
        let request: Value = serde_json::from_str(request_json)
            .map_err(|err| HostError::InvalidRequest(format!("{err}: {request_json}")))?;
        let method = request
            .get("method")
            .and_then(|value| value.as_str())
            .ok_or_else(|| HostError::InvalidRequest("missing method".to_string()))?
            .to_string();
        if !method.starts_with("ui.") {
            return Err(HostError::Unsupported(format!(
                "UiRenderHost only serves ui.*, got {method} (cap {cap})"
            )));
        }
        let args = match request.get("args") {
            Some(Value::Array(items)) => items.clone(),
            None | Some(Value::Null) => Vec::new(),
            Some(_) => {
                return Err(HostError::InvalidRequest(
                    "args must be a positional array".to_string(),
                ))
            }
        };
        let mut ui_request = UiRequest {
            method: method.clone(),
            title: None,
            tree: None,
            options: None,
            args: args.clone(),
        };
        let presented = match method.as_str() {
            "ui.form" | "ui.screen" => {
                ui_request.title = args
                    .first()
                    .and_then(|value| value.as_str())
                    .map(str::to_string);
                let tree = match args.get(1) {
                    Some(tree) => tree,
                    None => return Ok(Self::reject(format!("{method}: missing node tree"))),
                };
                match normalize_tree(tree) {
                    Ok(value) => ui_request.tree = Some(value),
                    Err(message) => return Ok(Self::reject(message)),
                }
                ui_request.options = args.get(2).cloned();
                if method == "ui.screen" {
                    if let Some(options) = ui_request.options.as_mut() {
                        if let Err(message) = normalize_screen_pages(options) {
                            return Ok(Self::reject(message));
                        }
                    }
                }
                true
            }
            "ui.show" => {
                let tree = match args.first() {
                    Some(tree) => tree,
                    None => return Ok(Self::reject("ui.show: missing node tree".to_string())),
                };
                match normalize_tree(tree) {
                    Ok(value) => ui_request.tree = Some(value),
                    Err(message) => return Ok(Self::reject(message)),
                }
                ui_request.options = args.get(1).cloned();
                true
            }
            "ui.progress" | "ui.confirm" => true,
            "ui.isCancelled" => false,
            other => {
                return Err(HostError::Unsupported(format!(
                    "unknown ui method {other} (known: ui.form, ui.screen, ui.show, ui.progress, ui.confirm, ui.isCancelled)"
                )))
            }
        };
        if !presented {
            return Ok(Value::Bool(self.cancelled).to_string());
        }
        let reply = match self.presenter.present(&ui_request) {
            Ok(value) => value,
            Err(HostError::InvalidRequest(message)) | Err(HostError::Unsupported(message)) => {
                return Ok(Self::reject(message))
            }
            Err(other) => return Err(other),
        };
        self.requests.push(ui_request);
        Ok(match method.as_str() {
            "ui.form" | "ui.screen" => reply.to_string(),
            "ui.show" | "ui.progress" => "null".to_string(),
            "ui.confirm" => Value::Bool(reply.as_bool().unwrap_or(false)).to_string(),
            _ => "null".to_string(),
        })
    }

    fn log(&mut self, _level: i32, _message: &str) {}
}

/// 按顺序应答的呈现方：录制的 UI 交互用于 CLI 验证与集成测试。
///
/// 队列里每个元素是一次 form/screen/confirm 的应答；队列耗尽后 form/screen 视为取消
/// （返回 null），confirm 视为 false。show/progress 没有返回值，不消耗队列。
#[derive(Debug, Default, Clone)]
pub struct ScriptedUiPresenter {
    replies: std::collections::VecDeque<Value>,
    cursor: usize,
}

impl ScriptedUiPresenter {
    pub fn new(replies: impl IntoIterator<Item = Value>) -> Self {
        Self {
            replies: replies.into_iter().collect(),
            cursor: 0,
        }
    }

    /// 从 JSON 文本构造：顶层必须是数组（按顺序应答）或单个值（只回一次）。
    pub fn from_json(text: &str) -> Result<Self, String> {
        let value: Value = serde_json::from_str(text)
            .map_err(|err| format!("ui replies is not valid JSON: {err}"))?;
        Ok(match value {
            Value::Array(items) => Self::new(items),
            other => Self::new(vec![other]),
        })
    }

    pub fn remaining(&self) -> usize {
        self.replies.len()
    }

    pub fn cursor(&self) -> usize {
        self.cursor
    }

    fn next_reply(&mut self) -> Value {
        self.cursor += 1;
        self.replies.pop_front().unwrap_or(Value::Null)
    }
}

impl UiPresenter for ScriptedUiPresenter {
    fn present(&mut self, request: &UiRequest) -> Result<Value, HostError> {
        Ok(match request.method.as_str() {
            // show/progress 没有返回值，不占应答位（录脚本时只需给 form/screen/confirm）。
            "ui.show" | "ui.progress" => Value::Null,
            "ui.confirm" => {
                let reply = self.next_reply();
                Value::Bool(reply.as_bool().unwrap_or(false))
            }
            _ => self.next_reply(),
        })
    }
}


#[cfg(test)]
mod ui_tests {
    use super::*;

    fn tree(children: Value) -> Value {
        let mut map = Map::new();
        map.insert("kind".to_string(), Value::String("Column".to_string()));
        map.insert("spacing".to_string(), Value::Number(8.into()));
        map.insert("children".to_string(), children);
        Value::Object(map)
    }

    fn field(id: &str) -> Value {
        let mut map = Map::new();
        map.insert("kind".to_string(), Value::String("TextField".to_string()));
        map.insert("id".to_string(), Value::String(id.to_string()));
        map.insert("label".to_string(), Value::String(id.to_string()));
        Value::Object(map)
    }

    #[test]
    fn normalizes_flat_tree_into_host_shape() {
        let raw = tree(Value::Array(vec![
            field("text"),
            Value::Object(
                [
                    ("kind".to_string(), Value::String("Button".to_string())),
                    ("label".to_string(), Value::String("开始".to_string())),
                    ("action".to_string(), Value::String("submit".to_string())),
                ]
                .into_iter()
                .collect(),
            ),
        ]));
        let normalized = normalize_tree(&raw).expect("valid tree");
        assert_eq!(normalized["__ui"], Value::String("Column".to_string()));
        assert_eq!(normalized["props"]["spacing"], Value::Number(8.into()));
        let children = normalized["children"].as_array().expect("children");
        assert_eq!(children.len(), 2);
        assert_eq!(children[0]["__ui"], Value::String("TextField".to_string()));
        assert_eq!(children[0]["props"]["id"], Value::String("text".to_string()));
        assert_eq!(children[1]["props"]["action"], Value::String("submit".to_string()));
    }

    #[test]
    fn rejects_invalid_trees() {
        let unknown_component = Value::Object(
            [
                ("kind".to_string(), Value::String("Table".to_string())),
                ("spacing".to_string(), Value::Number(1.into())),
            ]
            .into_iter()
            .collect(),
        );
        assert!(normalize_tree(&unknown_component).is_err());

        let unknown_property = tooltip_tree();
        assert!(normalize_tree(&unknown_property).is_err());

        let duplicate = tree(Value::Array(vec![field("x"), field("x")]));
        assert!(normalize_tree(&duplicate).is_err());

        let leaf_with_children = Value::Object(
            [
                ("kind".to_string(), Value::String("Text".to_string())),
                ("text".to_string(), Value::String("hi".to_string())),
                ("children".to_string(), Value::Array(vec![field("x")])),
            ]
            .into_iter()
            .collect(),
        );
        assert!(normalize_tree(&leaf_with_children).is_err());

        let missing_action = Value::Object(
            [
                ("kind".to_string(), Value::String("Button".to_string())),
                ("label".to_string(), Value::String("go".to_string())),
            ]
            .into_iter()
            .collect(),
        );
        assert!(normalize_tree(&missing_action).is_err());

        let missing_section_title = Value::Object(
            [("kind".to_string(), Value::String("Section".to_string()))]
                .into_iter()
                .collect(),
        );
        assert!(normalize_tree(&missing_section_title).is_err());

        let ratio = Value::Object(
            [
                ("kind".to_string(), Value::String("ProgressBar".to_string())),
                ("ratio".to_string(), Value::Number(2.into())),
            ]
            .into_iter()
            .collect(),
        );
        assert!(normalize_tree(&ratio).is_err());

        let size = Value::Object(
            [
                ("kind".to_string(), Value::String("Text".to_string())),
                ("text".to_string(), Value::String("hi".to_string())),
                ("size".to_string(), Value::Number(100.into())),
            ]
            .into_iter()
            .collect(),
        );
        assert!(normalize_tree(&size).is_err());
    }

    fn tooltip_tree() -> Value {
        Value::Object(
            [
                ("kind".to_string(), Value::String("Text".to_string())),
                ("text".to_string(), Value::String("hi".to_string())),
                ("tooltip".to_string(), Value::String("nope".to_string())),
            ]
            .into_iter()
            .collect(),
        )
    }

    #[test]
    fn rejects_too_deep_and_too_wide_trees() {
        let mut nested = field("x");
        for _ in 0..32 {
            nested = tree(Value::Array(vec![nested]));
        }
        assert!(normalize_tree(&nested).is_err(), "depth > 32 must fail");

        let mut ok_depth = field("x");
        for _ in 0..30 {
            ok_depth = tree(Value::Array(vec![ok_depth]));
        }
        assert!(normalize_tree(&ok_depth).is_ok(), "depth 31 must pass");

        let wide = tree(Value::Array(vec![Value::Object(
            [
                ("kind".to_string(), Value::String("Text".to_string())),
                ("text".to_string(), Value::String("x".to_string())),
            ]
            .into_iter()
            .collect(),
        ); 512]));
        assert!(normalize_tree(&wide).is_err(), "count > 512 must fail");

        let wide_ok = tree(Value::Array(vec![Value::Object(
            [
                ("kind".to_string(), Value::String("Text".to_string())),
                ("text".to_string(), Value::String("x".to_string())),
            ]
            .into_iter()
            .collect(),
        ); 511]));
        assert!(normalize_tree(&wide_ok).is_ok(), "count 512 must pass");
    }

    fn form_request(reply: Value) -> (UiRenderHost<ScriptedUiPresenter>, String) {
        let mut host = UiRenderHost::new(ScriptedUiPresenter::new(vec![reply]));
        let request = Value::Object(
            [
                ("method".to_string(), Value::String("ui.form".to_string())),
                (
                    "args".to_string(),
                    Value::Array(vec![
                        Value::String("无障碍点击".to_string()),
                        tree(Value::Array(vec![field("text")])),
                    ]),
                ),
            ]
            .into_iter()
            .collect(),
        );
        let reply = host
            .host_call("ui:render", &request.to_string())
            .expect("form must not fail at host level");
        (host, reply)
    }

    #[test]
    fn form_returns_values_and_records_request() {
        let (host, reply) = form_request(serde_json::json!({"text": "确定"}));
        assert_eq!(reply, "{\"text\":\"确定\"}");
        let requests = host.requests();
        assert_eq!(requests.len(), 1);
        assert_eq!(requests[0].method, "ui.form");
        assert_eq!(requests[0].title.as_deref(), Some("无障碍点击"));
        assert_eq!(requests[0].node_count(), 2);
        assert_eq!(requests[0].field_ids(), vec!["text".to_string()]);
    }

    #[test]
    fn form_cancel_returns_null() {
        let (_host, reply) = form_request(Value::Null);
        assert_eq!(reply, "null");
    }

    #[test]
    fn show_and_progress_return_null_confirm_returns_bool() {
        let mut host = UiRenderHost::new(ScriptedUiPresenter::new(vec![serde_json::json!(true)]));
        let show = serde_json::json!({"method": "ui.show", "args": [tree(Value::Array(vec![field("note")]))]});
        assert_eq!(host.host_call("ui:render", &show.to_string()).unwrap(), "null");
        let progress = serde_json::json!({"method": "ui.progress", "args": [0.4, "下载中"]});
        assert_eq!(host.host_call("ui:render", &progress.to_string()).unwrap(), "null");
        let confirm = serde_json::json!({"method": "ui.confirm", "args": ["删除", "确认？"]});
        assert_eq!(host.host_call("ui:render", &confirm.to_string()).unwrap(), "true");
        let cancelled = serde_json::json!({"method": "ui.isCancelled", "args": []});
        assert_eq!(host.host_call("ui:render", &cancelled.to_string()).unwrap(), "false");
    }

    #[test]
    fn invalid_tree_becomes_error_reply() {
        let mut host = UiRenderHost::new(NullUiPresenter);
        let request = serde_json::json!({
            "method": "ui.form",
            "args": ["标题", {"kind": "NotAComponent"}]
        });
        let reply = host.host_call("ui:render", &request.to_string()).unwrap();
        let value: Value = serde_json::from_str(&reply).expect("reply is json");
        assert!(value.get("__error").is_some(), "invalid tree -> __error");
        assert!(host.requests().is_empty(), "invalid request must not be recorded");
    }

    #[test]
    fn screen_pages_trees_are_normalized_and_validated() {
        // 分页树的扁平写法（kind + 平铺 props）要转成宿主内部 __ui 形状，
        // 否则 Kotlin 的 UiScreenPage 解析会抛 "ui.__ui: expected component name"。
        let mut host = UiRenderHost::new(ScriptedUiPresenter::new(vec![Value::Null]));
        let request = serde_json::json!({
            "method": "ui.screen",
            "args": [
                "Tibo 今天重置了吗",
                {"kind": "Column", "spacing": 12.0, "children": [{"kind": "Text", "text": "今日", "style": "body"}]},
                {
                    "bottom_nav": {"selected": "dashboard", "items": [
                        {"id": "dashboard", "label": "今日"},
                        {"id": "challenge", "label": "挑战"},
                    ]},
                    "pages": {
                        "dashboard": {"tree": {"kind": "Column", "spacing": 12.0, "children": [{"kind": "Text", "text": "今日", "style": "body"}]}},
                        "challenge": {"tree": {"kind": "Column", "spacing": 12.0, "children": [{"kind": "Text", "text": "挑战", "style": "body"}]}}
                    }
                }
            ]
        });
        assert_eq!(host.host_call("ui:render", &request.to_string()).unwrap(), "null");
        let recorded = host.requests();
        let options = recorded[0].options.as_ref().expect("options kept");
        let pages = options.get("pages").and_then(Value::as_object).expect("pages kept");
        let tree = pages["challenge"].get("tree").expect("challenge page tree");
        assert_eq!(tree.get("__ui").and_then(Value::as_str), Some("Column"));
        assert!(tree.get("kind").is_none(), "flat form must be rewritten");
        assert!(tree["children"][0].get("__ui").is_some(), "nodes are normalized too");
    }

    #[test]
    fn screen_page_with_invalid_tree_is_rejected() {
        let mut host = UiRenderHost::new(NullUiPresenter);
        let request = serde_json::json!({
            "method": "ui.screen",
            "args": [
                "标题",
                {"kind": "Column", "children": []},
                {"bottom_nav": {"items": [{"id": "a", "label": "A"}, {"id": "b", "label": "B"}]},
                 "pages": {"a": {"tree": {"kind": "NotAComponent"}}, "b": {"tree": {"kind": "Column"}}}}
            ]
        });
        let reply = host.host_call("ui:render", &request.to_string()).unwrap();
        let value: Value = serde_json::from_str(&reply).expect("reply is json");
        assert!(value.get("__error").is_some(), "invalid page tree -> __error");
        assert!(host.requests().is_empty(), "invalid request must not be recorded");
    }

    #[test]
    fn unknown_ui_method_is_unsupported() {
        let mut host = UiRenderHost::new(NullUiPresenter);
        let request = serde_json::json!({"method": "ui.dialog", "args": []});
        let error = host.host_call("ui:render", &request.to_string()).unwrap_err();
        assert!(matches!(error, HostError::Unsupported(_)));
    }

    #[test]
    fn non_ui_method_is_not_served() {
        let mut host = UiRenderHost::new(NullUiPresenter);
        let request = serde_json::json!({"method": "net.get", "args": ["https://x"]});
        let error = host.host_call("net:x", &request.to_string()).unwrap_err();
        assert!(matches!(error, HostError::Unsupported(_)));
    }
}


/// 让 Box<dyn UiPresenter> 也能当 presenter 用（CLI 按参数选实现）。
impl<T: UiPresenter + ?Sized> UiPresenter for Box<T> {
    fn present(&mut self, request: &UiRequest) -> Result<Value, HostError> {
        (**self).present(request)
    }
}
