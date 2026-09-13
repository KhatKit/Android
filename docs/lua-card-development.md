# KhatKit Lua 卡片脚本开发指南

KhatKit 的「卡片」= 一段脚本 + 一份 `card.json` 声明。AI 通过 function calling 选择卡片、填充参数，宿主在沙箱里执行脚本，脚本通过 bridge 调用设备能力。

- 引擎：Rust 核心（mlua，Lua 5.4 语义；JS 用 rquickjs）——**卡片脚本不要依赖引擎独有特性**，保证 Lua/JS 可互换。
- 脚本只是「下单」：网络、文件、下载、UI 全部由宿主 bridge 完成（厚宿主、薄脚本）。
- 脚本里出现的 bridge 调用和域名必须先在 `card.json` 里声明，CI（`card-validator`）会静态校验。

---

## 1. 目录结构与最小示例

```
cards/pdf-merge/
  card.json      # 必须
  main.lua       # 入口（或 main.js）
  README.md      # 可选
```

`card.json`（最小合法版）：

```json
{
  "name": "hello_card",
  "version": "1.0.0",
  "description": "示例卡片",
  "engine": "lua",
  "entry": { "lua": "main.lua" },
  "privilege": "none",
  "requires": { "bridges": ["ui"], "libs": [], "bins": [], "env": [] },
  "network": { "allow": [] },
  "parameters": {
    "type": "object",
    "properties": {
      "name": { "type": "string", "description": "要问候的人" }
    },
    "required": ["name"]
  },
  "tags": { "domain": "text", "action": "create", "scene": "daily" }
}
```

`main.lua`：

```lua
local name = args.name
local v = ui.form("打个招呼", {
  { type = "input", id = "name", label = "名字", default = name or "" }
})
if not v then return { cancelled = true } end
return { message = "Hello, " .. (v.name or name or "world") }
```

内置卡片放在 `khatkit/src/main/assets/cards/<dir>/`，App 启动后会自动同步（版本变化时覆盖）。第三方卡片通过 Hub 下载到应用内部缓存。

---

## 2. card.json 字段

| 字段 | 必填 | 说明 |
|---|---|---|
| `name` | ✅ | 全局唯一，`^[a-z][a-z0-9_]{1,63}$` |
| `version` | ✅ | SemVer，如 `1.0.0`（内置卡片靠它判断是否覆盖更新） |
| `description` | ✅ | 一句话说明（会展示给 AI/用户） |
| `engine` | ✅ | `lua` / `js` / `auto` / `command` |
| `entry` | ✅ | `{ "lua": "main.lua" }` 或 `{ "js": "main.js" }`，与 engine 一致 |
| `privilege` | ✅ | `none` / `elevated`（需要 Shizuku/root/无障碍等高权限时用 elevated） |
| `requires.bridges` | ✅ | 用到的 bridge：`tool` `ui` `download` `store` `shizuku` `root` `accessibility` |
| `requires.libs` | | 共享库声明，如 `{ "name": "@khatkit/std", "lang": "lua", "version": "^1.0.0" }` |
| `network.allow` | ✅ | 域名白名单，空数组 = 禁止联网；脚本里出现的 `http(s)://host` 必须在此 |
| `parameters` | ✅ | 标准 JSON Schema（AI 按它填参），设计上兼容 MCP tool schema |
| `ui` | | 声明哪些参数必须由人填：`{ "路径参数": { "source": "ui", "widget": "file_picker" } }` |
| `tags` | ✅ | 对象结构：`{ "domain": ..., "action": ..., "scene": ... }` |
| `compliance` | 条件 | `domain: game` 时强制：`{ "risk": "high", "note": "..." }` |
| `store` | | `{ "quota_mb": 50, "secret": false }` |

### 标签词表（受控，CI 校验）

- `domain`（10 选 1）：`file` `media` `app` `system` `net` `text` `device` `game` `social` `data`
- `action`（7 选 1）：`read` `convert` `batch` `clean` `monitor` `control` `create`
- `scene`（可选，最多 1）：`work` `dev` `daily` `privacy` `gaming`

---

## 3. 脚本运行环境

- 入口脚本被当作一个 chunk 执行，**返回值必须是 table**（否则包装成 `{ result = ... }`）。
- 参数通过全局 `args` 注入：`args.foo`。
- 出错约定：`return { error = "原因" }`，宿主会把 error 文本回给 AI。
- 取消约定：用户取消 UI 表单时返回 `return { cancelled = true }`。
- 目录内可用 `require` 加载声明过的共享库（如 `local std = require("@khatkit/std")`）。

### 共享库：@khatkit/std

| Lua | JS | 说明 |
|---|---|---|
| `std.join_path(...)` | `std.joinPath(...)` | 拼接路径并合并重复斜杠 |
| `std.file_size(bytes)` | `std.formatSize(bytes)` | 人类可读体积（KB/MB/GB） |

---

## 4. Bridge API

所有 bridge 都是宿主 Kotlin 对象，脚本按名调用：`tool.readText(...)`。**先声明 `requires.bridges`，注入失败时卡片对 AI 不可见。**

### 4.1 tool —— 文件 / 网络 / 媒体

```lua
tool.readText(path)                   -- 读文本，返回 string
tool.writeText(path, content)         -- 写文本
tool.httpGet(url, headers)            -- GET，返回响应体字符串；headers 是 table（可为 {}）
tool.compressImage(path, quality)     -- 压缩图片（10~100），返回新文件路径
tool.mergePdf(paths, output)          -- 合并 PDF（paths 为数组），返回输出路径
tool.openDir(path)                    -- 用系统文件管理器打开目录
```

网络域名必须在 `network.allow` 里；`httpGet` 的 URL 建议来自参数或表单，不要在脚本里写死未声明的域名。

### 4.2 ui —— 声明式表单（宿主渲染）

```lua
local v = ui.form("标题", {
  { type = "input",   id = "name",  label = "名称", default = "" },
  { type = "slider",  id = "quality", label = "质量", min = 10, max = 100, default = 80 },
  { type = "switch",  id = "exact", label = "精确匹配" },
  { type = "select",  id = "mode",  label = "模式", options = { "快", "慢" } },
  { type = "file_picker", id = "path", label = "选择文件", multiple = false, filter = ".pdf" },
  { type = "dir_picker",  id = "dir", label = "选择目录" },
})
if not v then return { cancelled = true } end
local name = v.name
```

- `ui.form`：返回用户填好的值（table）；用户取消返回 `nil`。
- `ui.confirm(title, message, danger)`：危险操作二次确认，返回 boolean。
- `ui.progress(ratio, label)`：0.0~1.0 的进度。
- `ui.show(card)`：展示结果卡片（table）。

组件白名单（CI 校验）：`text` `input` `number` `switch` `slider` `select` `radio` `file_picker` `dir_picker` `button` `progress` `markdown` `divider` `custom`。

**重要**：`ui.form` 由脚本自己调用——AI 已经填好参数时应当跳过表单直接执行，只在缺参数时才弹：

```lua
local url = args.url
if not url or url == "" then
  local v = ui.form("网页转文本", { { type = "input", id = "url", label = "网页地址" } })
  if not v then return { cancelled = true } end
  url = v.url
end
```

### 4.3 download —— 宿主后台下载

任务跑在 App 级服务里，脚本退出后继续；所有下载会出现在全局「下载中心」。

```lua
local id = download.start({ url = "https://example.com/a.zip", name = "a.zip", headers = {} })
local st = download.status(id, 5)   -- 最多等 5 秒；返回 { id,name,state,progress,speed,file,error }
-- state: queued / running / paused / done / failed / cancelled
download.pause(id); download.resume(id); download.cancel(id)
```

### 4.4 store —— 卡片隔离存储

底层 key 自动加 `card_<name>_` 前缀，配额默认 50MB（manifest 可调），敏感数据走 Keystore。

```lua
store.kvGet(key, default); store.kvSet(key, value)
store.fileRead(name); store.fileWrite(name, content)
store.dbQuery(table, where, args); store.dbInsert(table, row)
store.secretGet(key); store.secretSet(key, value)
-- 跨卡片共享区：写自己命名空间，按名读别人产物（工作流协作）
store.sharedWrite(name, content); store.sharedRead(name); store.sharedList(); store.sharedDelete(name)
```

### 4.5 shizuku（L1）/ root（L2）

动作级 API，不暴露通用 exec；需要 `privilege: "elevated"` 且用户在设置里授予对应权限。

```lua
shizuku.setAppEnabled(pkg, enabled)          -- 启用/禁用应用
shizuku.settingsPut(namespace, key, value)   -- namespace: system/secure/global
shizuku.pm(action, pkg)                      -- disable/enable/clear/grant/revoke...

root.shell(cmd)                              -- 仅 root 卡片，慎用
```

### 4.6 accessibility（无障碍自动化）

需要用户在系统设置开启无障碍服务；`privilege: "elevated"`。

```lua
accessibility.currentPackage()
accessibility.dumpWindow()                       -- 扁平节点列表（depth/bounds/text/desc...）
accessibility.findNodes({ text = "确定", clickable = true })
accessibility.click({ text = "确定" })           -- 返回 boolean；不可点自动向上找可点祖先
accessibility.longClick({ desc = "图标" })
accessibility.setText({ className = "EditText" }, "内容")
accessibility.tap(x, y)
accessibility.swipe(x1, y1, x2, y2, durationMs)
accessibility.scroll("down")                     -- forward/backward/up/down/left/right
accessibility.globalAction("back")               -- back/home/recents/notifications
accessibility.openApp("com.example.app")
```

查询条件字段：`text` `desc` `viewId` `className` `clickable` `exact`。

---

## 5. engine: command（无脚本形态）

纯命令封装，不经脚本引擎；宿主对 `{}` 占位符做白名单转义，脚本无法拼命令。

```json
{
  "name": "list_apps",
  "engine": "command",
  "command": "pm list packages -3",
  "privilege": "elevated",
  "requires": { "bridges": ["shizuku"] },
  "network": { "allow": [] },
  "parameters": { "type": "object", "properties": {}, "required": [] },
  "tags": { "domain": "app", "action": "read" }
}
```

---

## 6. 完整示例：抓网页存文本（含表单兜底 + store）

```lua
-- card.json: requires.bridges = ["tool","ui","store"]; tags: net/read
local url = args.url
local output = args.output

if not url or url == "" then
  local last_url = store.kvGet("last_url", "")
  local v = ui.form("网页转文本", {
    { type = "input", id = "url", label = "网页地址", default = last_url },
    { type = "input", id = "output", label = "保存路径（留空自动）" },
  })
  if not v then return { cancelled = true } end
  url = v.url
  output = v.output
end
if not url or url == "" then return { error = "缺少网页地址" } end

if not output or output == "" then
  local name = string.match(url, "([^/?#]+)[/?#]*$") or "page"
  name = string.gsub(name, "[^%w%-%._]", "_")
  output = "/sdcard/Download/khatkit-web-" .. name .. ".txt"
end

ui.progress(0, "抓取中…")
local body = tool.httpGet(url, {})
if not body or body == "" then return { error = "抓取失败或内容为空" } end

local text = string.gsub(body, "<[^>]*>", " ")
text = string.gsub(text, "&nbsp;", " ")
text = string.gsub(text, "&amp;", "&")
text = string.gsub(text, "%s+", " ")
text = string.gsub(text, "^%s*(.-)%s*$", "%1")

ui.progress(1, "保存中…")
tool.writeText(output, text)
store.kvSet("last_url", url)

return { output = output, chars = #text, preview = string.sub(text, 1, 200) }
```

---

## 7. 校验与发布（CI）

`card-validator` 是纯 JVM 模块，卡片仓库 CI 直接复用：

```bash
./gradlew :card-validator:run --args="/path/to/cards"
```

会校验：name 格式、engine/entry 一致、bridge 白名单、UI 组件白名单、标签词表、`domain: game` 必须带 `compliance`、脚本中出现的 bridge 调用 ⊆ 声明、脚本中的域名 ⊆ `network.allow`。有任何 ERROR 退出码为 1。

人工审核关注：是否越权、是否有破坏性副作用、是否与声明权限相符。

---

## 8. 开发建议

- **参数优先走 `args`**：AI 能填的不要让用户再填一遍；表单只做兜底与危险确认。
- **危险操作先 `ui.confirm`**，并在 `compliance.note` 里写清风险。
- **长任务交给 download bridge**，不要在脚本里同步等待大文件。
- **网络尽量只在 `tool.httpGet`**，域名写进 `network.allow`，不要从参数拼任意域名。
- **状态用 store**：记住上次选择、断点信息，避免重复问用户。
- 返回结构保持精简（AI 会读），大文本写文件、只回路径和摘要。
