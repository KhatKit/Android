# KhatKit 流（Flow）

流 = 把多个卡片 / 内置工具操作按顺序合成一次执行，只需一次授权，适合「截图 → OCR → 抠图 → 水印」这类多步任务。

AI 通过内置工具 `khatkit__run_flow` 创建并运行流；每一步都走宿主的中央执行路径（自动化悬浮看板、套餐计量、取消、放手模式全部生效）。

---

## 1. 工具参数

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `steps` | string | 是 | 步骤 JSON 数组字符串，按顺序执行 |
| `stop_on_error` | boolean | 否 | 任一步失败时是否立即停止整个流，默认 `true`；步骤内 `continue_on_error` 优先 |

每个步骤对象：

```json
{
  "card": "卡片名（或内置工具名 device_screen / device_act，khatkit__ 前缀可省略）",
  "args": { "参数名": "值" },
  "continue_on_error": false
}
```

- `card` 支持：本机已安装卡片 → Hub 云端卡片（按需自动下载/更新，含 `requires.dependencies`）→ 内置工具 `device_screen` / `device_act`。
- 卡片必须声明 `triggers` 含 `ai`，否则该步返回 `TRIGGER_DENIED`。
- 步骤数上限 20。

---

## 2. 占位符（引用前面步骤的输出）

| 写法 | 含义 |
| --- | --- |
| `${steps[0].outputs[0].path}` | 第 0 步结果里 `outputs` 数组第 0 项的 `path` 字段 |
| `${steps[0].result.path}` | `result` 可省略：等价于 `${steps[0].path}`（步骤结果 map 的顶层字段） |
| `${steps[0].字段名}` | 步骤结果 map 的字段，支持多层 `.` 与列表 `[下标]`（如 `${steps[1].nodes[2].text}`） |

规则：

- 步序号 `i` 从 **0** 开始，只能引用**前面已执行**的步骤；未执行/缺失时解析为 `null`。
- 整个值就是一个占位符时保留原始类型（map / list 不序列化）；内嵌在更长字符串里时，`map` / `list` 会序列化为 JSON 文本。
- 步骤失败时其结果 map 为 `{"error": "...", "code": "..."}`，也可以被后续步骤引用。
- 内置工具 `device_act` 的 `wait_text` 等返回 JSON 对象时按字段引用；`device_screen` 的结果含 `screenshot`（PNG 路径）、`ocr`、`nodes` 等。

---

## 3. 错误处理与授权

- 每一步失败（卡片返回错误 / 抛异常）会记录到该步的 `outputs.error`：
  - `stop_on_error: true`（默认）且该步 `continue_on_error != true` → 立即停止，后续步骤不再执行；
  - `continue_on_error: true` 或 `stop_on_error: false` → 继续执行下一步。
- 流开始时只请求一次授权（看板标题「运行流：N 步」，类别 `card_run`）；通过后本次会话内每步的卡片授权闸门自动放行，不会重复询问。
- 用户在看板点「停止」或拒绝授权 → 流终止，返回已完成步骤的部分结果。
- 返回值为中文摘要 + 每步结果 JSON（`ok`、`summary`、`steps`）；结果中的本机图片路径会随聊天消息渲染缩略图。

---

## 4. 示例

### 4.1 截图 → OCR → 抠图 → 水印

```json
[
  { "card": "device_screen", "args": { "include_ocr": true, "include_nodes": false } },
  { "card": "image_ocr", "args": { "image": "${steps[0].outputs[0].path}" } },
  { "card": "image_cutout", "args": { "image": "${steps[0].outputs[0].path}" }, "continue_on_error": true },
  { "card": "image_watermark", "args": { "image": "${steps[2].outputs[0].path}", "text": "${steps[1].outputs[0].result}" } }
]
```

> 卡片名以实际安装/Hub 索引为准（可用 `khatkit__search_cloud_cards` 搜索）。

### 4.2 打开应用并等待

```json
[
  { "card": "device_act", "args": { "action": "open_app", "text": "com.tencent.mm" } },
  { "card": "device_act", "args": { "action": "wait_text", "text": "微信", "timeout_ms": 8000 } },
  { "card": "device_act", "args": { "action": "tap", "x": "${steps[1].centerX}", "y": "${steps[1].centerY}" } }
]
```

---

## 5. 与单卡片调用的关系

- 单步任务不需要流：直接调用 `khatkit__<卡片名>` 即可。
- 多步且后一步依赖前一步输出时用流，省去模型逐次调用与重复确认。
- 流内每一步仍可被看板观察、可被套餐计量与用户取消；审批与安全策略与单卡片完全一致。
