
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
