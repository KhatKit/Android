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
| `triggers` | | 触发方式数组：`ai`（AI 工具可调用）/ `user`（用户可在卡片市场手动运行），缺省 `["ai", "user"]` |
| `tags` | ✅ | 对象结构：`{ "domain": ..., "action": ..., "scene": ... }` |
| `compliance` | 条件 | `domain: game` 时强制：`{ "risk": "high", "note": "..." }` |
| `store` | | `{ "quota_mb": 50, "secret": false }` |

### 触发方式（triggers）

- `ai`：卡片会暴露给 AI 作为工具，AI 可自动调用；不声明 `ai` 的卡片不会出现在 AI 的工具列表里，被调用也会被拒绝。
- `user`：卡片市场中的「运行」按钮只在声明了 `user` 时出现，用户可手动执行。
- 可同时声明（`["ai", "user"]`），也是不写该字段时的默认值；CI 会拒绝未知值（如 `timer`）、重复值和空数组。

### 事件触发（events）

`events` 让卡片在没有 AI、没有用户点击的情况下自动运行（宿主总开关打开后生效）。运行参数通过 `args.event` 注入：

| type | 参数 | 说明 |
|---|---|---|
| `schedule` | `times: ["08:00"]` 或 `intervalMinutes: 30`，可选 `days: [1..7]`（1=周一，缺省每天） | 定时，分钟级精度 |
| `notification` | `package`（缺省任意应用）、`titleContains`、`textContains` | 通知到达；至少一个匹配条件 |
| `app_launch` | `package`（缺省任意应用） | 应用进入前台 |
| `charging` | `state: "connected" \| "disconnected"` | 插拔充电器 |

```json
"events": [
  { "type": "schedule", "times": ["08:00"], "days": [1, 2, 3, 4, 5] },
  { "type": "notification", "package": "com.tencent.mm", "titleContains": "红包" },
  { "type": "charging", "state": "connected" }
]
```

脚本侧取值示例：

```lua
local event = args.event or {}
if event.type == "notification" then
  local pkg = event.package   -- 包名
  local title = event.title   -- 标题
  local text = event.text     -- 正文
elseif event.type == "schedule" then
  local time = event.time     -- "08:00"
elseif event.type == "charging" then
  local state = event.state   -- connected / disconnected
end
```

注意：事件卡片同样通过沙箱与 bridge 执行；单卡有冷却（通知 3 秒、应用启动 60 秒、充电 5 秒），失败只会发通知，不影响宿主。详见 `docs/triggers.md`。

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
shizuku.shell(cmd)                           -- 通用 shell（高风险，见第 10 节）

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

---

## 9. 手机自动化扩展（剪贴板 / 截屏 / 手势 / 等待 / OCR）

以下方法挂在原有 bridge 上，`requires.bridges` 声明不变（`tool` / `accessibility`）；无障碍相关仍需 `privilege: "elevated"` 并在系统设置里开启服务。

### 9.1 tool 新增

```lua
tool.setClipboard("文本")          -- 写入系统剪贴板
tool.getClipboard()                -- 读取剪贴板文本，无内容返回 ""
tool.wakeScreen()                  -- 点亮屏幕，返回「屏幕已点亮」或中文错误说明
tool.ocrText("/sdcard/a.png")      -- 中英文 OCR，成功返回识别文本，失败返回 {"error":"..."}
tool.ocrBoxes("/sdcard/a.png")     -- 带坐标 OCR，返回 [{"text":"..","x":..,"y":..,"w":..,"h":..}]
```

- `tool.ocrText` 由随 APK 打包的 ML Kit 中文识别模型提供（支持中文 + 拉丁字母），无需联网；识别较慢，建议先 `compressImage` 或裁剪。
- `tool.wakeScreen` 依赖 `WAKE_LOCK` 权限；部分 ROM 会拦截，失败时返回错误说明，可改用 Shizuku/root 执行 `input keyevent KEYCODE_WAKEUP`。

### 9.2 accessibility 新增

```lua
accessibility.press(x, y, durationMs)                  -- 坐标长按（100~10000ms）
accessibility.gesture(strokesJson)                     -- 多段手势，入参为 JSON 字符串
accessibility.waitForIdle(timeoutMs)                   -- 等窗口内容稳定（连续两次节点摘要一致）
accessibility.waitForPackage("com.example.app", 5000)  -- 等指定应用变为前台，返回 boolean
accessibility.captureScreen()                          -- 截屏存 /sdcard/Download/KhatKit/，返回路径
accessibility.captureScreen("/sdcard/Download/a.png")  -- 指定保存路径
accessibility.paste()                                  -- 对当前聚焦输入框执行粘贴
accessibility.findImage("/sdcard/tpl.png", 0.9)        -- 模板匹配，返回 {"found":..,"x":..,"y":..,"score":..}
accessibility.tapImage("/sdcard/tpl.png", 0.9, 5000)   -- 找到模板并点击中心，最多轮询 5s
accessibility.findColor("#FF0000", 16, "0,0,1080,720") -- 找第一个匹配颜色的像素，region 可省略
```

`gesture` 的 JSON 格式（外层数组 = 手势段，内层数组 = 单段轨迹，`t` 为段内毫秒偏移）：

```lua
accessibility.gesture('[ [ {"x":100,"y":200,"t":0}, {"x":300,"y":200,"t":500} ] ]')
```

- 段数上限取系统 `getMaxStrokeCount()`（API 30 以下按 10 处理），超出的段被忽略；单段时长 50ms~60s。
- `captureScreen` 需要 Android 11（API 30）以上并授予「所有文件访问」；API 30 以下或失败时返回中文错误文本，不抛异常。
- `waitForIdle` / `waitForPackage` 超时返回 `false`；`waitForIdle` 轮询间隔 200ms。

---

## 10. 高级自动化（模板匹配 / 颜色查找 / OCR 坐标 / Shizuku shell）

### 10.1 accessibility.findImage / tapImage

```lua
local r = accessibility.findImage("/sdcard/tpl/btn.png", 0.9)
-- 命中: {"found":true,"x":540,"y":1200,"score":0.97}
-- 未中: {"found":false}；参数/截图失败: {"error":"中文说明"}
local tx, ty = r.x, r.y

-- 找到后点击中心；timeoutMs > 0 时每 300ms 轮询一次，超时返回 false
local ok = accessibility.tapImage("/sdcard/tpl/btn.png", 0.9, 5000)
```

- 纯 Kotlin 实现，无 OpenCV 依赖：屏幕灰度图缩到长边 ≤1280 后先粗搜、再原分辨率局部精修，
  模板按 `0.8 / 1.0 / 1.25` 三个尺度匹配（归一化互相关 ZNCC）。
- `threshold` 为 0~1 的相似度阈值，缺省 `0.9`；`score` 为命中位置得分。
- 返回的 `x`/`y` 是**屏幕像素坐标**，可直接喂给 `accessibility.tap` 或 `tapImage` 内部点击。
- 与 `captureScreen` 相同，需要 Android 11（API 30）以上；不落盘，无需存储权限。
- 建议模板裁剪成按钮/图标本体并略留边距；相似度低时先降 `threshold` 到 0.8 左右再排查模板尺度。

### 10.2 accessibility.findColor

```lua
local r = accessibility.findColor("#FF5722", 16)                   -- 全屏
local r2 = accessibility.findColor("#FF5722", 16, "0,0,1080,720")  -- 限定区域 x,y,w,h
-- 命中: {"found":true,"x":12,"y":36,"color":"#FF5722"}
-- 未命中: {"found":false}；格式错误: {"error":"..."}
```

- `tolerance` 为每通道（R/G/B）容差，范围 0~255，缺省 16。
- 按行优先返回**第一个**匹配像素的坐标（屏幕像素）；`region` 省略或传 `""` 表示全屏。

### 10.3 tool.ocrBoxes

```lua
local boxes = tool.ocrBoxes("/sdcard/Download/shot.png")
-- [{"text":"确定","x":420,"y":1180,"w":180,"h":64}, ...]
```

- 与 `tool.ocrText` 同一份 ML Kit 中文模型；按文本行输出，坐标为图片像素（已按 EXIF 方向校正）。
- 典型用法：`captureScreen` 截图后 `ocrBoxes` 定位文字，再 `accessibility.tap(box.x + box.w/2, box.y + box.h/2)`。
- 失败返回 `{"error":"..."}`；识别较慢，建议配合 `compressImage` 或先裁剪。

### 10.4 shizuku.shell

```lua
local out = shizuku.shell("pm list packages -3 | head -5")
-- "[exit 0]\npackage:com.example.app\n..."
```

- 以 shell 身份执行 `/system/bin/sh -c <cmd>`，支持管道/重定向；返回 `[exit N]` + 合并的 stdout/stderr。
- 需要 `privilege: "elevated"`、Shizuku 服务在线且已授权，并且 `card.json` 声明 `shizuku` bridge。
- **高风险**：这是通用命令入口，会获得 shell 权限；脚本不应把用户输入直接拼进命令，
  能用的动作级 API（`pm` / `settingsPut` / `setAppEnabled`）优先用动作级 API。
- 超时 30s，超时返回 `[error] 命令超时`；Shizuku 不可用或版本不支持时返回 `[error] Shizuku shell 执行失败：...`。
