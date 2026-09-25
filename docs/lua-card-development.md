# KhatKit 卡片脚本开发文档

本文档是 KhatKit 卡片开发的唯一权威说明，覆盖 manifest 规范、脚本运行时、全部 bridge API、触发器与自动化审批、安全合规与常见问题。内容以仓库当前代码为准（`card-validator`、`:khatkit`、`:khatkit-core`、`:app`）。

配套阅读（可选，不作为必读依赖）：

- `docs/triggers.md`：事件触发的最小说明（本文第 2.9、5 节已完整覆盖）
- `docs/zenneko-cards.md`：内置极客猫平台卡片的接口清单（本文第 4 节给出写法）

---

## 目录

1. [快速开始](#1-快速开始)
2. [card.json（manifest）完整参考](#2-cardjsonmanifest完整参考)
3. [脚本运行时](#3-脚本运行时)
4. [Bridge API 参考](#4-bridge-api-参考)
5. [自动化专题](#5-自动化专题)
6. [安全与合规](#6-安全与合规)
7. [调试与常见问题](#7-调试与常见问题)
8. [完整示例卡片](#8-完整示例卡片)
9. [附录：事件速查 / 错误码 / 能力对照](#9-附录)

---

## 1. 快速开始

### 1.1 卡片是什么

一张卡片 = 一个目录：`card.json`（声明）+ 一个入口脚本（`main.lua` / `main.js`），或 `engine: command` 时的纯命令模板。

- 卡片通过 `parameters`（标准 JSON Schema）暴露给 AI，AI 以 `khatkit__<name>` 工具名调用并填参。
- 脚本在宿主进程内执行，只能调用 `card.json` 中声明过的 bridge；网络、文件、下载、UI 都由宿主 Kotlin 实现（厚宿主、薄脚本）。
- 脚本运行时是 Rust native 单引擎（`khatkit_core`）：Lua 5.4（mlua）与 QuickJS（rquickjs）共用同一套 bridge API，**卡片脚本不要依赖任一引擎的独有特性**，保证 Lua/JS 可互换。
- 脚本里出现的 bridge 调用与域名必须先在 manifest 里声明，`card-validator` 会做静态校验。

### 1.2 目录结构

```
cards/<name>/
  card.json      必须，manifest
  main.lua       入口（entry.lua）
  main.js        入口（entry.js）
  README.md      可选
```

内置卡片位于 `khatkit/src/main/assets/cards/<dir>/`；首次组装 AI 工具或刷新触发器时由 `BuiltinCards.install()` 铺到应用内部缓存（同版本跳过，版本变化整体覆盖）。第三方卡片由卡片市场从 Hub 下载，解压到应用缓存目录的 `khatkit/cards/<name>/<version>/`。

### 1.3 最小 card.json

```json
{
  "name": "hello_card",
  "version": "1.0.0",
  "description": "示例卡片：向用户问好",
  "author": "you",
  "license": "MIT",
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
  "ui": {
    "name": { "source": "ui", "widget": "input" }
  },
  "tags": { "domain": "text", "action": "create", "scene": "daily" }
}
```

### 1.4 最小 main.lua

Lua 脚本是一个 chunk，**最后用 `return` 返回一个 table**；参数在全局 `args` 里。

```lua
local name = args.name

-- AI 已填参就跳过表单；缺参数时才弹 UI 兜底
if not name or name == "" then
  local v = ui.form("打个招呼", {
    { type = "input", id = "name", label = "名字", default = "" }
  })
  if not v then return { cancelled = true } end
  name = v.name
end

if not name or name == "" then return { error = "缺少名字" } end
return { message = "Hello, " .. name }
```

### 1.5 最小 main.js

JS 脚本顶层可以直接 `return`；参数在全局 `args` 里。

```js
let name = args.name;
if (!name) {
  const v = ui.form("打个招呼", [
    { type: "input", id: "name", label: "名字" }
  ]);
  if (!v) return { cancelled: true };
  name = v.name;
}
if (!name) return { error: "缺少名字" };
return { message: "Hello, " + name };
```

### 1.6 安装与运行方式

| 入口 | 说明 |
|---|---|
| 内置卡片 | 打包在 APK 的 `assets/cards`，启动时自动同步；同版本跳过，版本变化覆盖本地目录。 |
| 卡片市场 | 按 Hub 索引搜索 → 「安装 / 更新 / 卸载」，下载 zip 后落缓存；可在市场里查看/撤销卡片密钥。 |
| AI 调用 | `triggers` 含 `ai` 的卡片会以 `khatkit__<name>` 暴露给模型，AI 按 `parameters` 填参调用；云端未安装的卡片会在首次调用时按需下载。 |
| 用户手动 | `triggers` 含 `user` 的卡片在市场卡片上有「运行」按钮；不带参数运行，缺参数时由脚本自己弹 `ui.form`。 |
| 事件触发 | manifest 里声明 `events` 的卡片，在系统事件（定时/通知/应用启动/充电/Wi-Fi/网络/电量/屏幕/剪贴板/蓝牙/位置）发生时自动运行；总开关在「设置 → 自动化触发器」。 |
| AI 设备工具 | 无障碍服务在线时，AI 额外获得 `khatkit__device_screen`、`khatkit__device_act` 两个内置工具（不写卡片也能驱动手机）。 |

`triggers` 与 `events` 相互独立：事件触发不要求声明 `["ai"]` 或 `["user"]`。

### 1.7 推荐开发循环

1. 在 `khatkit/src/main/assets/cards/<name>/` 下写 manifest + 脚本（或直接改本地缓存目录做快速迭代）。
2. 用 AI 调用或市场「运行」触发，观察返回值与自动化看板。
3. 跑静态校验：`./gradlew :card-validator:run --args="khatkit/src/main/assets/cards"`。
4. 发布到 Hub 时保持 `version` 递增，客户端靠版本号判断更新。

---

## 2. card.json（manifest）完整参考

解析实现：`card-validator/.../CardManifest.kt` + `CardParser.kt`；校验实现：`CardValidator.kt`。JSON 解析使用 `ignoreUnknownKeys = true`，未知字段会被忽略（但校验器会拒绝未知的枚举值）。

### 2.1 顶层字段总表

| 字段 | 类型 | 默认值 | 必填 | 说明与校验 |
|---|---|---|---|---|
| `name` | string | — | 是 | 全局唯一。必须匹配 `^[a-z][a-z0-9_]{1,63}$`：小写字母开头，后接 1–63 个小写字母/数字/下划线（总长 2–64）。否则 `NAME_INVALID`。 |
| `version` | string | `"0.0.0"` | 否 | 建议 SemVer（`1.2.3`，可带 `-beta`/`+build`）。不匹配只报 WARNING `VERSION_NOT_SEMVER`。内置卡片靠它判断覆盖更新。 |
| `description` | string | `""` | 否 | 一句话说明，展示给用户并作为 AI 工具描述的一部分（Hub 的 `summary` 优先）。 |
| `author` | string | `""` | 否 | 作者。 |
| `license` | string | `""` | 否 | 许可证。 |
| `engine` | string | `"auto"` | 否 | `lua` / `js` / `auto` / `command`，其他值报 `ENGINE_UNKNOWN`。 |
| `entry` | object | `{}` | 条件 | `{ "lua": "main.lua", "js": "main.js" }`，至少一个与 `engine` 匹配（见 2.2）。 |
| `privilege` | string | `"none"` | 否 | `none` / `elevated`，其他值报 `PRIVILEGE_UNKNOWN`。声明性字段，见 6.1。 |
| `requires` | object | `{}` | 否 | 能力声明，见 2.3。 |
| `network` | object | `{}` | 否 | `{ "allow": ["api.example.com"] }`，见 2.4。 |
| `parameters` | object | `{}` | 否 | 标准 JSON Schema，AI 填参依据，见 2.5。 |
| `ui` | object | `{}` | 否 | 参数 UI 声明（CI 白名单校验），见 2.5。 |
| `triggers` | array | `["ai","user"]` | 否 | `ai` / `user`，不允许空数组、重复或未知值，见 2.8。 |
| `events` | array | `[]` | 否 | 事件触发声明，11 种类型，见 2.9。 |
| `tags` | object | null | **是** | `{ "domain": ..., "action": ..., "scene": ... }`，词表见 2.6；缺失报 `TAGS_MISSING`。 |
| `compliance` | object | null | 条件 | `{ "risk": "low|medium|high", "note": "..." }`；`tags.domain == "game"` 时必填。 |
| `store` | object | `{ "quota_mb": 50, "secret": false }` | 否 | 卡片存储配额（file + db 合计）与敏感标记；`quota_mb` 最小按 1MB 生效。 |
| `command` | string | null | 条件 | `engine == "command"` 时必填，`{}` 占位符由宿主做白名单转义，见 3.7。 |

字段命名注意：事件里的 `package`、`level_below`、`level_above`、`radius_m` 使用下划线；`text_contains` 与 `textContains` 两种写法都接受（`@JsonNames`）。

### 2.2 engine 与 entry 规则

| engine | entry 要求 | 运行时行为 |
|---|---|---|
| `lua` | `entry.lua` 非空 | 用 Lua 引擎执行 `entry.lua`。缺失报 `ENTRY_MISSING`。 |
| `js` | `entry.js` 非空 | 用 QuickJS 引擎执行 `entry.js`。缺失报 `ENTRY_MISSING`。 |
| `auto` | `entry.lua` / `entry.js` 至少一个 | 按文件存在性选择：**JS 优先**（`entry.js` 存在即用 JS，否则 Lua）；两者都缺报 `ENTRY_MISSING`。 |
| `command` | 不读 entry | 执行 `command` 模板，`command` 为空报 `COMMAND_MISSING`。 |

### 2.3 requires

```json
"requires": {
  "bridges": ["tool", "ui", "store"],
  "libs": [
    { "name": "@khatkit/std", "lang": "js", "version": "^1.0.0" }
  ],
  "bins": [],
  "env": []
}
```

- `bridges`：声明脚本会用到的桥，取值只能是 `tool` `ui` `download` `store` `shizuku` `root` `accessibility` `imageToolbox`，未知值报 `BRIDGE_UNKNOWN`。运行时要求设备**全部具备**这些能力，缺任何一个返回 `BRIDGE_UNAVAILABLE`（错误文案会提示去「+」面板开启对应权限）。
- `libs`：共享库声明。`name` 为库名；`lang` 为 `lua` / `js`（默认 `js`）；`version` 为 SemVer 范围（默认 `*`）。宿主解析顺序：内置层 `assets/libs/<dir>/<version>/` → Hub CDN 共享库 registry；**只解析与当前引擎同语言的库**。脚本内用 `require(name)` 使用（Lua 走 `package.preload`，JS 为 CommonJS 形态）。
- `bins` / `env`：当前宿主版本未消费，保留字段，可留空数组。

内置共享库 `@khatkit/std` 目前提供：

| Lua | JS | 说明 |
|---|---|---|
| `std.join_path(...)` | `std.joinPath(...)` | 拼接路径并合并重复 `/` |
| `std.file_size(bytes)` | `std.formatSize(bytes)` | 人类可读体积（B/KB/MB/GB/TB） |

### 2.4 network.allow

```json
"network": { "allow": ["api.example.com", "127.0.0.1"] }
```

- 空数组（默认）= 不允许联网，脚本里出现的任何 `http(s)://host` 都会被 CI 判定违规。
- CI 扫描脚本正文里的 `http(s)://<host>`，要求 host 等于白名单项或为其子域：`host == allowed || host.endsWith("." + allowed)`，比较时不区分大小写。例如声明 `example.com` 允许 `api.example.com`，但不允许 `example.com.evil.net`。
- 违规报 `NETWORK_UNDECLARED`。**运行时宿主不逐请求校验域名**，白名单靠 CI 静态扫描 + 人工审核保障，请自觉只访问已声明域名。
- 声明 `127.0.0.1` / `localhost` 可以访问本机服务（如 vFlow 的本地 Web API）。

### 2.5 parameters 与 ui

`parameters` 是标准 JSON Schema，宿主直接读取其中的 `properties` 与 `required` 转成 AI 工具的输入 schema（`manifestInputSchema()`）。没有 `properties` 时卡片对 AI 仍可调用，只是没有参数表单。

```json
"parameters": {
  "type": "object",
  "properties": {
    "path": { "type": "string", "description": "文件路径" },
    "quality": { "type": "integer", "minimum": 10, "maximum": 100 }
  },
  "required": ["path"]
}
```

`ui` 是参数与组件映射的声明式元数据（给工具链/审核用，**宿主不会据此自动弹表单**，实际表单由脚本调用 `ui.form` 时描述）：

```json
"ui": {
  "path":    { "source": "ui", "widget": "file_picker" },
  "quality": { "source": "ui", "widget": "slider", "min": 10, "max": 100 }
}
```

- `source` 只能是 `ai` / `ui`，否则 `UI_SOURCE_INVALID`。
- `widget` 必须在宿主已实现的白名单内，否则 `UI_WIDGET_UNKNOWN`：
  `text` `input` `number` `switch` `slider` `select` `radio` `file_picker` `dir_picker` `button` `progress` `markdown` `divider` `custom`。
- 注意：`file_picker` / `dir_picker` 在当前表单实现里渲染为**路径文本输入框**，不会弹出系统文件选择器；`filter`、`multiple` 等字段仅作为声明信息，表单渲染时被忽略。

### 2.6 tags（受控词表）

`tags` 必填（`TAGS_MISSING`），`domain` / `action` 各选一个，`scene` 可选：

- `domain`（10 选 1）：`file`（文件）`media`（图片/视频/音频）`app`（应用管理）`system`（系统设置）`net`（网络请求）`text`（文本处理）`device`（设备控制：屏幕/传感器）`game`（游戏：画面识别/挂机/自动任务）`social`（社交通讯）`data`（结构化数据：表格/数据库）。
- `action`（7 选 1）：`read` `convert` `batch` `clean` `monitor` `control` `create`。
- `scene`（可选，3 选 1）：`work` `dev` `daily` `privacy` `gaming`。

不合法分别报 `TAG_DOMAIN_INVALID` / `TAG_ACTION_INVALID` / `TAG_SCENE_INVALID`。`domain == "game"` 时必须提供 `compliance`，否则 `GAME_COMPLIANCE_MISSING`。

### 2.7 compliance / store / command

```json
"compliance": { "risk": "medium", "note": "需要无障碍权限，点击结果请自行确认" },
"store": { "quota_mb": 5, "secret": false },
"command": "pm disable {pkg}"
```

- `compliance.risk` 约定为 `low|medium|high`（默认 `low`），`note` 写清风险；除 `game` 域强制外，校验器不检查取值，请如实填写。
- `store.quota_mb` 控制卡片 `store` 文件 + 数据库的合计配额，超限写入报中文错误；`store.secret` 为保留标记，密钥 `secretGet/secretSet` 始终走 Keystore 加密。
- `command` 模板中的 `{参数名}` 会被 `args` 替换，宿主只允许 `[A-Za-z0-9._\-/:@]` 字符，含空格/中文/管道等一律拒绝执行；缺参数会失败。命令优先经 Shizuku，其次 root（见 3.7）。

### 2.8 triggers

```json
"triggers": ["ai", "user"]
```

- `ai`：卡片暴露为 AI 工具 `khatkit__<name>`，模型可调用；未声明 `ai` 的卡片不在工具列表里，调用会被拒绝（`TRIGGER_DENIED`）。
- `user`：卡片市场显示「运行」按钮。
- 默认两者都有；空数组报 `TRIGGER_EMPTY`，未知值（如 `timer`）报 `TRIGGER_UNKNOWN`，重复值报 `TRIGGER_DUPLICATE`。
- 设备能力过滤：`requires.bridges` 有缺失的卡片对 AI 不可见；`privilege: elevated` 还要求设备具备 `shizuku` / `root` / `accessibility` 之一。

### 2.9 events（事件触发）

`events` 是数组，每项一个事件；一个卡片可以声明多个。总开关关闭时不会触发。事件运行参数通过 `args.event` 注入。

```json
"events": [
  { "type": "schedule", "times": ["08:00", "21:30"], "days": [1, 2, 3, 4, 5] },
  { "type": "schedule", "intervalMinutes": 30 },
  { "type": "notification", "package": "com.tencent.mm", "titleContains": "红包" },
  { "type": "app_launch", "package": "com.tencent.mm" },
  { "type": "charging", "state": "connected" }
]
```

#### 通用字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | string | 必填，仅 11 种取值（下表）；未知报 `EVENT_TYPE_UNKNOWN`。 |
| `times` | string[] | schedule 专用，严格 `HH:mm`（`00:00`–`23:59`）。 |
| `intervalMinutes` | int | schedule 专用，间隔分钟数，与 `times` 二选一。 |
| `days` | int[] | schedule 专用，1=周一 … 7=周日；空 = 每天。 |
| `package` | string | notification / app_launch 包名；空 = 不限制。 |
| `titleContains` | string | notification 标题包含（忽略大小写）。 |
| `textContains` / `text_contains` | string | notification 正文包含；clipboard 新文本包含（忽略大小写）。 |
| `state` | string | 各状态类事件的转换方向/目标状态。 |
| `ssid` | string | wifi 热点名，空 = 任意；匹配忽略大小写。 |
| `device` | string | bluetooth 设备名包含，空 = 任意；匹配忽略大小写。 |
| `level_below` | int | battery：电量 ≤ 该值触发；`-1` 表示未设置。 |
| `level_above` | int | battery：电量 ≥ 该值触发；`-1` 表示未设置。 |
| `lat` / `lon` | double | location 圆心纬度 `[-90,90]` / 经度 `[-180,180]`。 |
| `radius_m` | int | location 半径（米，≥1）。 |

#### 11 种事件类型：匹配、校验与参数

| type | 必填/校验 | 运行时匹配 | `args.event` 字段 |
|---|---|---|---|
| `schedule` | 需要 `times` 或 `intervalMinutes`（≥1），否则 `EVENT_SCHEDULE_EMPTY`；`intervalMinutes` 不能为负；`times` 格式错 `EVENT_TIME_INVALID`；`days` 越界 `EVENT_DAY_INVALID` | 前台服务每 60 秒 tick；命中本地时间 `HH:mm`（同分钟去重，跨天可重触发）或达到间隔；`days` 限定星期 | `type`, `time`（如 `"08:00"`；间隔模式为当前时间） |
| `notification` | `package` / `titleContains` / `textContains` 不能全空，否则 `EVENT_NOTIFICATION_EMPTY` | 通知监听服务收到通知；包名精确匹配，标题/正文包含、忽略大小写 | `type`, `package`, `title`, `text` |
| `app_launch` | 无（`package` 可空 = 任意应用） | 无障碍服务每秒轮询前台包名变化；包名精确匹配 | `type`, `package` |
| `charging` | `state` 必须是 `connected` / `disconnected`，否则 `EVENT_CHARGING_STATE_INVALID` | 插拔充电器广播 | `type`, `state` |
| `wifi` | `state` 可空（任意变化）或 `connected`/`disconnected`，否则 `EVENT_WIFI_STATE_INVALID` | Wi-Fi 连接/断开回调；`ssid` 为空不限热点，否则精确匹配（忽略大小写） | `type`, `ssid`, `state` |
| `network` | `state` 必须是 `online` / `offline`，否则 `EVENT_NETWORK_STATE_INVALID` | 默认网络可用/丢失回调 | `type`, `state` |
| `battery` | 至少需要 `level_below` / `level_above` / `state` 之一（否则 `EVENT_BATTERY_EMPTY`）；state 只能 `charging`/`discharging`；level 取 `0..100`；两者同时设置时 `level_below < level_above`（否则 `EVENT_BATTERY_LEVEL_RANGE`） | 电量广播；`state` 空 = 不限充放电；`level_below>=0` 且电量 ≤ 值，或 `level_above>=0` 且电量 ≥ 值；均未设阈值时按 state 命中 | `type`, `state`, `level`（电量百分比） |
| `screen` | `state` 必须是 `on` / `off` / `unlocked` / `locked`，否则 `EVENT_SCREEN_STATE_INVALID` | 亮屏、灭屏（带锁屏时为 `locked`，否则 `off`）、解锁广播 | `type`, `state` |
| `clipboard` | `text_contains` / `textContains` 必填，否则 `EVENT_CLIPBOARD_EMPTY` | 每 2 秒轮询剪贴板，新文本包含关键字（忽略大小写）触发；Android 10+ 后台读取受限会静默跳过 | `type`, `text` |
| `bluetooth` | `state` 必须是 `connected` / `disconnected`，否则 `EVENT_BLUETOOTH_STATE_INVALID` | 蓝牙 ACL 连接/断开广播；`device` 空 = 任意，否则设备名包含（忽略大小写）；API 31+ 需要 `BLUETOOTH_CONNECT` | `type`, `state`, `device` |
| `location` | `state` 必须是 `enter` / `exit`（否则 `EVENT_LOCATION_STATE_INVALID`）；`lat`/`lon` 必填且范围合法；`radius_m ≥ 1` | 每 60 秒取一次最近位置（GPS/网络）；每个事件独立维护内外状态，**首次采样只记基线**，之后按 enter/exit 方向在边界转换时触发 | `type`, `state`, `lat`, `lon`（当前坐标） |

脚本读取事件参数：

```lua
local event = args.event or {}
if event.type == "notification" then
  local pkg, title, text = event.package, event.title, event.text
elseif event.type == "schedule" then
  local time = event.time
elseif event.type == "battery" then
  local level, state = event.level, event.state
end
```

事件运行还有冷却（防抖）与失败重试，详见 5.3。

---

## 3. 脚本运行时

### 3.1 执行模型

- 引擎：`EngineFactory` 创建 `RustScriptEngine`，native 库 `khatkit_core`（Lua 5.4 + QuickJS）。native 句柄不跨线程，每次执行新建实例并 `close()`；native 不可用时返回 `RUST_ENGINE` 错误。
- 每次 `CardExecutor.execute()` 在一个线程上同步执行脚本：先解析共享库（可能挂起），再注入 bridge，再 `eval`。脚本执行期间**没有挂起点**，宿主不会抢占。
- 并发上限：`CardRunManager` 默认最多同时运行 2 张卡片（AI / 用户 / 事件共用一个闸门）；同一卡片在事件触发下还有按类型的冷却与去重。
- Lua 使用安全标准库子集（`ALL_SAFE`，不含 `debug`）；`string` / `table` / `math` / `utf8` / `os` / `io` 可用，但文件与系统操作建议统一走 bridge。JS 是 QuickJS：支持 `const/let`、箭头函数、模板字符串等 ES2020 语法，但**没有** `console` / `setTimeout` / `fetch` / `process` 等宿主 API；引擎不驱动 Promise 任务队列，`async/await`、`Promise.then` 不会继续执行，请写同步脚本。
- 引擎差异：不要用引擎独有特性。需要通用逻辑时优先 `@khatkit/std` 或自己写库。

### 3.2 args 与返回值约定

- 参数：AI/表单填好的参数通过全局 `args` 注入（Lua table / JS object）。事件触发时额外带 `args.event`（见 2.9）。
- 返回值：脚本最后 `return` 一个值。
  - 返回对象（table/object）→ `EngineResult.Ok(map)`，键值原样回给调用方。
  - 返回数组、字符串、数字、布尔、`nil`/`undefined`、`null` → 包装成 `{ "result": <值> }`。
  - 返回空 table `{}` → 空 map。
- 宿主对返回值的解释：
  - `{ error = "..." }`：脚本级失败。用户手动运行会弹出该文案；事件触发记为失败（可重试）；AI 调用会看到包含 `error` 字段的 JSON。
  - `{ cancelled = true }`：用户在表单里取消，用户运行提示取消，事件/AI 视为正常结果（按成功处理）。
  - 其他键随意，AI 会读整个 JSON，保持精简：大文本写文件、只回路径与摘要。
- 用户运行（`runCard`）与事件运行会检查返回里的 `error` 字段；AI/`runCardWithStatus` 直接返回 Ok。

### 3.3 错误处理

两层错误要分清：

1. **脚本运行时错误**（Lua `error()`、JS `throw`、语法错误、native panic）：引擎捕获后转成 `EngineResult.Err("RUST_RUNTIME", 原因)`，AI 侧显示为 `{"error":"..."}`，不会打断整轮生成；事件触发记录失败并可重试。
2. **bridge 调用错误**（文件不存在、无权限、参数非法、反射失败）：宿主不向脚本抛异常，而是把这次调用编码成 `{"__error":"中文说明"}` 返回。**脚本拿到的是 table/object，不是异常**，需要自己检查：

```lua
local res = tool.readText(path)
if type(res) == "table" and res.__error then
  return { error = "读取失败：" .. tostring(res.__error) }
end
```

```js
const res = tool.readText(path);
if (res && res.__error) return { error: "读取失败：" + res.__error };
```

`RustScriptEngine` 约定：native 返回 JSON 对象且含 `__error` 键时转成 `Err("RUST_RUNTIME", ...)`；正常对象转成 `Ok`。因此脚本主动返回 `{ __error = "..." }` 也会被当成运行时错误。

其余执行错误码：

| 错误码 | 触发场景 |
|---|---|
| `BRIDGE_UNAVAILABLE` | 设备缺少 `requires.bridges` 里的能力（缺 Shizuku/root/无障碍/所有文件访问等）。 |
| `BRIDGE_INJECT_FAILED` | 依赖在注入阶段解析失败（能力协商兜底）。 |
| `CARD_EXECUTOR` | 执行器内未预期异常统一收敛。 |
| `CARD_RUN_CRASH` / `CARD_CANCELLED` | 并发闸门内崩溃 / 协程被取消。 |
| `CARD_DENIED` | 用户在看板上拒绝了本次运行的授权。 |
| `CARD_UNAVAILABLE` / `TRIGGER_DENIED` / `CARD_TOOL` | AI 调用时卡片不可下载 / 未声明 `ai` / 工具层异常。 |
| `COMMAND_*` | command 卡片的命令缺失、宿主无执行能力、执行失败。 |

### 3.4 超时、取消与状态

- 卡片没有全局超时；长任务请把大文件交给 `download` bridge，循环任务每步轮询 `ui.isCancelled()`。
- 取消是**协作式**的：悬浮看板点「停止」只设置标记（`AutomationBus.requestCancel()`），脚本不轮询就不会退出；宿主无法抢占正在执行的 native 线程。
- `ui.form` / `ui.confirm` 阻塞等待用户操作，超时 300 秒；超时视为取消/拒绝（form 返回 `nil`/`null`，confirm 返回 `false`）。
- `download.status(id, waitSeconds)` 的等待、`shizuku.shell`（30 秒）、`root.shell`（30 秒）有各自超时。
- 常用骨架：

```lua
for i = 1, 100 do
  if ui.isCancelled() then return { cancelled = true } end
  ui.automationStatus("第 " .. i .. " 步")
  -- ... 具体动作 ...
  tool.sleep(1)
end
```

### 3.5 engine: auto 的选择

`auto` 同时声明 `entry.lua` 与 `entry.js` 时，**优先执行 JS**（`entry.js` 文件存在即选 JS）。发布卡片时建议写死 `lua` 或 `js`，避免行为随打包内容变化。

### 3.6 共享库 require

- Lua：`local std = require("@khatkit/std")`，库源码被注册到 `package.preload`，`require` 时执行并返回其返回值。
- JS：`const std = require("@khatkit/std")`，CommonJS 形态；模块只加载一次并缓存。
- 只加载与当前引擎同语言的库；命中的内置层不联网，否则回源 Hub 并按需缓存到 `cacheDir/libs`。

### 3.7 engine: command（无脚本卡片）

```json
{
  "name": "disable_bloat",
  "engine": "command",
  "command": "pm disable {pkg}",
  "privilege": "elevated",
  "requires": { "bridges": ["shizuku"] },
  "parameters": {
    "type": "object",
    "properties": { "pkg": { "type": "string" } },
    "required": ["pkg"]
  },
  "tags": { "domain": "app", "action": "control" }
}
```

- 模板占位符为 `{参数名}`；每个值必须匹配 `^[A-Za-z0-9._\-/:@]+$`，否则直接拒绝（脚本无法拼命令、无法注入空格或管道）。
- 执行顺序：Shizuku 可用走 Shizuku（命令按空白切分直接 exec，不经 shell 解析），否则 root；两者都不可用返回 `COMMAND_UNAVAILABLE`。
- 成功返回 `{ "output": "[ok]/stdout" }`；失败返回 `COMMAND_FAILED`。

---

## 4. Bridge API 参考

通用规则：

- 脚本里按接口名点号调用：`tool.readText(...)`、`accessibility.click({...})`。只有 `requires.bridges` 声明过的桥会被注入，未声明的全局名不存在。
- 方法通过反射按名字调用；参数类型自动转换：JSON 对象 → `Map`，数组 → `List`，数字 → `Int/Long/Double/Float`，布尔 → `Boolean`，字符串 → `String`，缺省对象参数 → `null`，缺省原始类型参数 → `0/false`。**Kotlin 的默认参数值在脚本调用时不会生效**（拿不到默认值），所有可选参数请显式传参，例如 `tool.httpGet(url, {})`、`accessibility.findColor("#FF0000", 16, "")`、`tool.httpMultipart(url, fields, nil, nil, {}, true)`。
- 参数个数不足会补 `null/0/false`；多传的参数被忽略。
- 非空返回（Map/List/JSON 字符串）跨引擎会 JSON 化；Kotlin 对象（如 `DownloadHandle`）只会变成无意义的字符串，脚本不要使用返回这类对象的方法。

### 4.1 tool（L0：文件 / 网络 / 媒体 / 压缩 / OCR）

| 方法（签名） | 参数 | 返回 | 说明 |
|---|---|---|---|
| `readText(path)` | path: string | string | 读文本文件。文件不存在或无权限时返回 `{__error}` 对象。共享存储路径需要「所有文件访问」。 |
| `writeText(path, content)` | path/content: string | 无 | 写文本，自动建父目录；返回值为 null。 |
| `httpGet(url, headers)` | url: string；headers: map（可传 `{}`） | string | GET，返回响应体文本。域名必须在 `network.allow`。 |
| `httpPost(url, body, headers)` | url/body: string；headers: map | string | POST，默认 `Content-Type: application/json`；自定义请求头（如 Authorization）通过 headers 传入。 |
| `httpMultipart(url, fields, fileField, filePath, headers, saveBinary)` | fields: map（表单字段）；fileField/filePath: string 或 nil；headers: map；saveBinary: boolean | string | `multipart/form-data` POST。`fileField` 与 `filePath` 都非空时附一个文件段。`saveBinary=true` 且响应是图片时，落盘到 `/sdcard/Download/zenneko_<api>_<时间戳>.<ext>` 并返回路径（需要「所有文件访问」）；否则返回响应文本。**布尔默认值不会自动生效，需要保存图片请显式传 `true`。** |
| `readBase64(path)` | path: string | string | 读本地文件为 `data:<mime>;base64,...`，供 JSON 接口上传图片。 |
| `saveBase64(data, outputPath)` | data: data URL / 裸 base64；outputPath: string | string | 解码写入 `outputPath`，返回文件路径。 |
| `compressImage(path, quality)` | path: string；quality: 1–100 | string | 压缩为同目录 `<名字>_compressed.jpg`，返回新路径。 |
| `mergePdf(paths, output)` | paths: string[]；output: string | string | 用 PDFBox 合并，返回输出路径。 |
| `openDir(path)` | path: string | 无 | 调系统文件管理器打开目录（失败静默）。 |
| `listFiles(path)` | path: string | string | 返回 JSON 数组：`[{"name","path","is_dir","size","modified"}]`，按名字排序。 |
| `copyPath(src, dst)` | src/dst: string | 无 | 递归复制并覆盖。 |
| `deletePath(path, recursive)` | path: string；recursive: boolean | boolean | 删除文件/目录。 |
| `mkdir(path)` | path: string | 无 | 建目录，失败返回 `{__error}`。 |
| `renamePath(src, dst)` | src/dst: string | 无 | 重命名/移动。 |
| `zip(paths, output)` | paths: string[]；output: string | string | 压缩为 zip，返回输出路径。 |
| `unzip(zipPath, outputDir)` | zipPath/outputDir: string | string | 解压，带 zip-slip 校验，返回输出目录。 |
| `sleep(seconds)` | seconds: int | 无 | 阻塞脚本线程，取值被限制在 0–120 秒。 |
| `setClipboard(text)` | text: string | 无 | 写系统剪贴板（应用在前台时可用）。 |
| `getClipboard()` | 无 | string | 读剪贴板文本，无内容返回 `""`。 |
| `wakeScreen()` | 无 | string | 点亮屏幕；成功返回 `屏幕已点亮`，失败返回中文错误说明。 |
| `ocrText(path)` | path: string | string | 本地 ML Kit 中文 OCR（中文+拉丁），成功返回纯文本；失败返回**字符串** `{"error":"..."}`（是字符串不是对象）。 |
| `ocrBoxes(path)` | path: string | string | 带坐标 OCR，返回 JSON 字符串数组：`[{"text","x","y","w","h"}]`，坐标为图片像素（已按 EXIF 转正）；失败返回 `{"error":"..."}`。 |

文件类方法（read/write/list/copy/delete/mkdir/rename/zip/unzip/compress/OCR/截屏落盘/模板图片）访问 `/sdcard`、`/storage`、`/mnt/sdcard` 时要求「所有文件访问」，否则返回带申请指引的中文错误。

Lua 片段：

```lua
local body = tool.httpGet("https://api.example.com/ping", {})
local text = tool.ocrText("/sdcard/Download/shot.png")

-- listFiles 返回 JSON 字符串，可整体回传或自行解析
local files = tool.listFiles("/sdcard/Download")
if type(files) == "table" and files.__error then
  return { error = files.__error }
end

local img = tool.readBase64("/sdcard/Download/a.png")
if type(img) == "table" and img.__error then return { error = img.__error } end
local saved = tool.saveBase64(img, "/sdcard/Download/copy.png")
```

### 4.2 ui（声明式表单与看板，L0）

| 方法（签名） | 参数 | 返回 | 说明 |
|---|---|---|---|
| `form(title, items)` | title: string；items: map[] | map 或 nil | 弹出表单，阻塞等待。items 每项：`type`（组件白名单）、`id`、`label`、`default`、`min`/`max`（slider）、`options`（select/radio）、`renderer`（custom）。用户取消或 300 秒超时返回 nil。 |
| `confirm(title, message, danger)` | title/message: string；danger: boolean | boolean | 二次确认弹窗；danger 时按钮文案为危险样式。取消/超时返回 false。 |
| `progress(ratio, label)` | ratio: 0.0–1.0；label: string | 无 | 显示顶部进度；label 会同步到自动化看板。 |
| `show(card)` | card: map | 无 | 弹出结果卡片：优先渲染 `markdown` / `text` / `content`，标题取 `title`；都没有时逐行打印键值。不阻塞。 |
| `automationStatus(label, detail)` | label/detail: string | 无 | 发布当前自动化步骤到悬浮看板；detail 可空。连续重复 label 会去重。 |
| `isCancelled()` | 无 | boolean | 用户在看板点过「停止」后返回 true；新一轮运行自动复位。 |

表单组件白名单与真实渲染行为（`KhatKitForm`）：

| type | 渲染行为 | 取值 |
|---|---|---|
| `text` / `markdown` | 纯文本（`markdown` 与 `text` 相同，不渲染 Markdown） | 不参与返回值 |
| `divider` | 分割线 | 不参与 |
| `input` | 单行输入框 | string |
| `number` | 数字键盘输入框（非法输入保留原字符串） | number/string |
| `switch` | 开关 | boolean |
| `slider` | 滑杆（`min`/`max`/`default` 必须显式给） | number |
| `select` / `radio` | 单选列表（`options` 字符串数组） | string |
| `file_picker` / `dir_picker` | **路径文本输入框**，不弹系统选择器 | string |
| `progress` | 只读进度条（取 `ratio` 或默认值） | 不参与 |
| `button` | 按钮（当前无点击回调） | 不参与 |
| `custom` | 按 `renderer` 找宿主注册的渲染器，缺省显示缺失提示 | 取决于渲染器 |

```lua
local v = ui.form("压缩图片", {
  { type = "file_picker", id = "path", label = "图片路径" },
  { type = "slider", id = "quality", label = "质量", min = 10, max = 100, default = 80 },
  { type = "switch", id = "overwrite", label = "覆盖原文件" }
})
if not v then return { cancelled = true } end

if not ui.confirm("确认压缩", "将对 " .. v.path .. " 执行有损压缩", false) then
  return { cancelled = true }
end
ui.progress(0.5, "压缩中…")
return { output = tool.compressImage(v.path, math.floor(v.quality or 80)) }
```

### 4.3 download（宿主后台下载，L0）

任务跑在 App 级下载服务里，脚本退出后继续；所有任务都出现在全局「下载中心」。

任务 Map 字段：`url`（必填）、`name`（可选，缺省从 URL 推断）、`headers`（可选 map）。保存目录为应用内部 `khatkit/downloads/files/`，完成后 `file` 字段给出绝对路径。

| 方法（签名） | 脚本可用 | 返回 | 说明 |
|---|---|---|---|
| `start(task)` | 是 | string | 入队并立即返回任务 id。 |
| `status(id, waitSeconds)` | 是 | map | 查询状态；`waitSeconds > 0` 时最多阻塞等待这么久。返回 `{id,name,state,progress,speed,file,error}`；`progress` 为 0.0–1.0，`speed` 为字节/秒，任务不存在时 `state="missing"`。 |
| `pause(id)` / `resume(id)` / `cancel(id)` | 是 | boolean | 暂停/恢复/取消；失败返回 false。 |
| `remove(id)` | 是 | boolean | 从下载中心删除记录（活动任务先取消）。 |
| `enqueue(task)` | 否 | DownloadHandle | 返回句柄对象，跨引擎会退化成无意义字符串，仅供宿主 Kotlin 使用。 |
| `observeTasks()` | 否 | StateFlow | 下载中心任务流，宿主 UI 用。 |
| `list(state)` / `query(id)` | 不建议 | Handle 列表/句柄 | 返回句柄对象，脚本无法消费；查询单个任务请用 `status(id, 0)`。 |

任务状态：`queued` `running` `paused` `done` `failed` `cancelled`（磁盘恢复时 `running` 会变为 `paused`）。同名同 URL 且已完成的任务会直接复用，不重复下载。

```lua
local id = download.start({ url = "https://example.com/a.zip", name = "a.zip", headers = {} })
local st = download.status(id, 5)
while st.state == "queued" or st.state == "running" do
  if ui.isCancelled() then download.cancel(id); return { cancelled = true } end
  ui.progress(tonumber(st.progress) or 0, "下载中…")
  st = download.status(id, 5)
end
if st.state ~= "done" then return { error = st.error or ("下载失败：" .. st.state) } end
return { file = st.file, state = st.state }
```

宿主侧 `DownloadHandle`（脚本拿不到，仅作说明）：`id`、`await(seconds)`、`progress()`、`speed()`、`pause()`、`resume()`、`cancel()`、`file()`。

### 4.4 store（卡片隔离存储，L0）

底层 KV key 自动加 `card_<name>_` 前缀，卡片改不到别人的数据；`quota_mb` 约束 file + db 合计大小。

| 方法（签名） | 返回 | 说明 |
|---|---|---|
| `kvGet(key, default)` | string 或 null | 读 KV（SharedPreferences）；缺省返回 default。 |
| `kvSet(key, value)` | 无 | 写 KV。 |
| `fileRead(name)` | string 或 null | 读卡片私有文件（`khatkit/store/<name>/files/` 下），路径逃逸会被拒绝。 |
| `fileWrite(name, content)` | 无 | 写私有文件；超配额返回 `{__error}`。 |
| `dbQuery(table, where, args)` | map[] | 查 JSONL 表。`where` 语法：`列 op ? [AND 列 op ?]*`，`op ∈ = != <> > < >= <= like`；`like` 为忽略大小写的包含。空 where 返回全表。 |
| `dbInsert(table, row)` | 无 | 追加一行（JSONL）。表名需匹配 `^[A-Za-z_][A-Za-z0-9_]{0,63}$`。 |
| `secretGet(key)` | string 或 null | 读密钥（Keystore AES-GCM 加密，独立于 KV）。 |
| `secretSet(key, value)` | 无 | 写密钥。 |
| `sharedWrite(name, content)` | 无 | 写跨卡片共享区，只进自己的命名空间 `shared/<card>/`。 |
| `sharedRead(name)` | string 或 null | 优先按 `<卡片名>/<文件名>` 精确读；传裸文件名时在所有卡片目录中找最新同名文件。 |
| `sharedList()` | string[] | 列出共享区所有文件（`<卡片名>/<文件名>`）。 |
| `sharedDelete(name)` | boolean | 只能删自己命名空间里的文件。 |

```lua
store.kvSet("last_url", url)
local n = tonumber(store.kvGet("count", "0")) or 0
store.kvSet("count", tostring(n + 1))

store.dbInsert("runs", { at = os.time(), ok = true, url = url })
local rows = store.dbQuery("runs", "ok = ? AND at > ?", { true, os.time() - 86400 })

store.secretSet("token", args.token)
local token = store.secretGet("token")
```

共享区为应用私有目录（`filesDir/khatkit/shared/`），但明文保存，密钥请用 `secretSet`。

### 4.5 shizuku（L1，动作级 + shell）

需要 Shizuku 服务在线且已授权；卡片声明 `requires.bridges: ["shizuku"]`，`privilege: "elevated"`。返回值均为文本：成功输出 stdout，空输出为 `[ok]`，失败为 `[exit N] ...` 或 `[error] ...`。

| 方法（签名） | 说明 |
|---|---|
| `setAppEnabled(pkg, enabled)` | `pm enable/disable --user 0 <pkg>`；包名需匹配 `^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$`。 |
| `settingsPut(namespace, key, value)` | `settings put <namespace> <key> <value>`；namespace 只能是 `system` / `secure` / `global`，key 限 `[A-Za-z0-9_.:-]{1,128}`。 |
| `pm(action, pkg)` | 白名单动作：`disable` `enable` `disable-user` `clear` `uninstall` `install-existing` `grant` `revoke` `path` `suspend` `unsuspend`。 |
| `shell(cmd)` | 以 shell 身份执行 `/system/bin/sh -c <cmd>`，支持管道/重定向，30 秒超时；返回 `[exit N]\n` + 合并的 stdout/stderr。高风险通用入口，见第 6 节。 |

```lua
local out = shizuku.pm("disable-user", args.pkg)
local ver = shizuku.shell("getprop ro.build.version.release")
if string.find(ver, "exit") and not string.find(ver, "^%[exit 0%]") then
  return { error = "shell 执行失败：" .. ver }
end
return { output = out, android = ver }
```

### 4.6 root（L2，默认禁用）

| 方法（签名） | 说明 |
|---|---|
| `shell(cmd)` | `su -c <cmd>`，30 秒超时，stdout/stderr 合并；退出码非 0 返回 `[exit N]\n输出`，超时返回 `[timeout] 命令超时`。 |

需要用户在权限面板显式开启「Root 提权」且 `su` 探测成功；卡片需声明 `requires.bridges: ["root"]` 与 `privilege: "elevated"`。

### 4.7 accessibility（L1，无障碍自动化）

需要用户在系统设置开启 KhatKit 的无障碍服务；卡片声明 `requires.bridges: ["accessibility"]`、`privilege: "elevated"`。节点查询统一用 Map：`text` `desc`（内容描述）`viewId` `className` 支持包含匹配（`exact=true` 时 text/desc 精确匹配）；布尔过滤支持 `clickable` `editable` `scrollable`。遍历上限 600 个节点、返回上限 80 条、单字段文本截断 512 字符。

返回的节点 Map 字段：`depth` `text` `desc` `viewId` `className` `packageName` `bounds`（`"left,top,right,bottom"`）`centerX` `centerY` `clickable` `longClickable` `editable` `scrollable` `enabled` `focused` `selected`。

| 方法（签名） | 返回 | 说明 |
|---|---|---|
| `isAvailable()` | boolean | 注入后恒为 true（服务在线才会挂 bridge）。 |
| `currentPackage()` | string 或 null | 当前前台包名。 |
| `dumpWindow()` | map[] | 当前活动窗口全部节点（扁平化，含 depth/bounds）。 |
| `findNodes(query)` | map[] | 按条件查找节点。 |
| `click(query)` | boolean | 点第一个匹配节点；节点不可点时向上最多 12 层找可点击祖先，再退化为坐标点击中心。 |
| `longClick(query)` | boolean | 长按第一个匹配节点（不可长按时用 600ms 手势）。 |
| `setText(query, text)` | boolean | 给第一个可编辑的匹配节点设文本（先聚焦，再 `ACTION_SET_TEXT`）。 |
| `tap(x, y)` | boolean | 坐标点击（60ms 手势）。 |
| `swipe(x1, y1, x2, y2, durationMs)` | boolean | 坐标滑动，时长限制 50–5000ms。 |
| `scroll(direction)` | boolean | `forward` / `backward` / `up` / `down` / `left` / `right`；优先对可滚动节点发滚动动作，`left`/`right` 与无滚动节点时退化为全屏滑动手势。 |
| `globalAction(action)` | boolean | `back` / `home` / `recents` / `notifications`。 |
| `openApp(packageName)` | boolean | 启动应用主界面。 |
| `waitForNode(query, timeoutMs)` | map 或 null | 每 150ms 轮询等待匹配节点，超时（上限 120s）返回 null。 |
| `press(x, y, durationMs)` | boolean | 坐标长按，时长限制 100–10000ms。 |
| `gesture(strokesJson)` | boolean | 多段手势，入参是 JSON 字符串：外层数组=手势段，内层数组=`[{"x","y","t"}]`，`t` 为段内毫秒偏移。段数上限取系统 `getMaxStrokeCount()`（API 30 以下按 10）；单段 50ms–60s。JSON 非法时返回 `{__error}`。 |
| `waitForIdle(timeoutMs)` | boolean | 等待窗口内容稳定（连续两次节点摘要一致）；每 200ms 检查，上限 120s。 |
| `waitForPackage(packageName, timeoutMs)` | boolean | 等待指定包名成为前台，每 200ms 轮询，上限 120s。 |
| `captureScreen(outputPath)` | string | 截屏保存 PNG，返回路径。`outputPath` 可空（默认 `/sdcard/Download/KhatKit/screenshot_<时间戳>.png`）。失败返回中文错误文本（**不抛异常**）。需要 Android 11（API 30）以上 + 「所有文件访问」。 |
| `findImage(templatePath, threshold)` | string | 在当前屏幕做多尺度灰度归一化互相关模板匹配。命中返回 JSON 字符串 `{"found":true,"x","y","score"}`（x/y 为屏幕像素中心），未命中 `{"found":false}`，参数/截图失败 `{"error":"..."}`。`threshold` 0–1，显式传 0 或省略时使用默认 0.9。 |
| `tapImage(templatePath, threshold, timeoutMs)` | boolean | 查找模板并点击中心；`timeoutMs > 0` 时每 300ms 轮询直到超时（上限 120s）。 |
| `findColor(colorHex, tolerance, region)` | string | 找第一个匹配颜色的像素。`colorHex` 支持 `#RRGGBB`/`#AARRGGBB`；`tolerance` 为每通道容差（0–255，注意省略时为 0）；`region` 空串=全屏，或 `"x,y,w,h"`。命中 `{"found":true,"x","y","color":"#RRGGBB"}`。 |
| `paste()` | boolean | 对当前聚焦的输入框执行粘贴（剪贴板内容由 `tool.setClipboard` 或系统写入）。 |
| `addOverlay(view, params)` / `removeOverlay(view)` | boolean / 无 | 宿主内部 API（参数是 Android View 对象），脚本无法使用。 |

模板匹配说明：纯 Kotlin 实现（无 OpenCV）。屏幕缩到长边 ≤1280 先粗搜，再原分辨率局部精修；模板按 0.8 / 1.0 / 1.25 三个尺度匹配，返回的 x/y 是屏幕像素，可直接喂给 `tap`。模板图片建议裁剪成按钮/图标本体并留少量边距。

```lua
-- 在界面里点「确定」，没有就等它出现
if not accessibility.click({ text = "确定", exact = false }) then
  local node = accessibility.waitForNode({ text = "确定" }, 5000)
  if node then
    accessibility.tap(node.centerX, node.centerY)
  else
    return { found = false }
  end
end

-- 模板找图点击
local r = accessibility.findImage("/sdcard/KhatKit/btn.png", 0.9)
if string.find(r, '"found":true', 1, true) then
  local x = tonumber(string.match(r, '"x":(%d+)'))
  local y = tonumber(string.match(r, '"y":(%d+)'))
  accessibility.tap(x, y)
end

-- 坐标长按 + 多段手势
accessibility.press(540, 1200, 800)
accessibility.gesture('[ [ {"x":100,"y":200,"t":0}, {"x":300,"y":200,"t":500} ] ]')

-- OCR 找字再点击：captureScreen 失败时返回中文错误文本，先判断是否为路径
local shot = accessibility.captureScreen()
if string.sub(shot, 1, 1) == "/" then
  local boxes = tool.ocrBoxes(shot)
  local x, y = string.match(boxes or "", '"x":(%d+),"y":(%d+)')
  if x and y then accessibility.tap(tonumber(x), tonumber(y)) end
end
```

### 4.8 imageToolbox（本地图像工具箱，L0）

本地静态图/PDF 处理，纯 Android SDK（`Bitmap`/`Canvas`/`ColorMatrix`/`PdfDocument`/`PdfRenderer`），不联网、不上传；`/sdcard` 等共享存储路径需要「所有文件访问」。每个方法读取源文件、写出新文件到源目录（或显式输出），返回输出路径；失败返回 `{__error}`。

| 方法（签名） | 返回 | 说明 |
|---|---|---|
| `resize(path, width, height, keepAspect)` | string | 缩放；`keepAspect=true` 时按比例适配（只给一边自动算另一边）。 |
| `crop(path, x, y, width, height)` | string | 裁剪，区域超出图片范围时报错。 |
| `rotate(path, degrees)` | string | 任意角度旋转，自动扩画布。 |
| `flip(path, horizontal)` | string | 翻转，`horizontal=true` 左右。 |
| `grayscale(path)` / `invert(path)` / `sepia(path)` | string | 灰度 / 反色 / 复古。 |
| `blur(path, radius)` | string | 方框模糊（三次分离卷积近似高斯）。 |
| `sharpen(path, amount)` | string | 3×3 锐化卷积。 |
| `pixelate(path, blockSize)` | string | 像素化（`blockSize>=2`）。 |
| `brightnessContrast(path, brightness, contrast)` | string | 亮度/对比度，各 -100..100。 |
| `saturation(path, factor)` | string | 饱和度，0=灰度 1=原图。 |
| `hue(path, degrees)` | string | 色相旋转。 |
| `autoContrast(path)` | string | 自动对比度（每通道 0.5% 截断拉伸）。 |
| `watermark(path, text, position, alpha, textSize, colorHex)` | string | 文字水印；`position` 为 top-left/top-right/bottom-left/bottom-right/center，`alpha` 0–255。 |
| `border(path, width, colorHex)` | string | 纯色边框。 |
| `roundCorners(path, radius)` | string | 圆角（输出默认 png 保留透明角）。 |
| `convert(path, format, quality)` | string | 格式转换，`format` 只支持 png/jpg/webp。 |
| `stripMetadata(path)` | string | 重编码丢弃 EXIF 等元数据。 |
| `imagesToPdf(paths, output)` | string | 多图合成 A4 PDF，`output` 可空。 |
| `pdfToImages(path, outputDir)` | string[] | PDF 逐页渲染 PNG，`outputDir` 可空（源目录）。 |
| `pdfPageCount(path)` | number | PDF 页数。 |

> `compress(path, quality)` 质量压缩请直接用 `tool.compressImage`，不在 imageToolbox 里重复提供。内置卡片 `local_image_toolbox` 已封装全部操作与参数解析，可直接使用。

### 4.9 宿主侧接口速览

| 接口 | 方法 | 说明 |
|---|---|---|
| `download.enqueue` / `observeTasks` | 返回 `DownloadHandle` / `StateFlow` | 仅宿主 Kotlin 可用。 |
| `download.list` / `download.query` | 返回句柄对象 | 脚本拿不到内容，改用 `status`。 |
| `accessibility.addOverlay` / `removeOverlay` | 参数是 Android `View` | 宿主悬浮窗内部使用。 |

---

## 5. 自动化专题

### 5.1 权限与风险分级

宿主把设备能力分为三级（能力声明，不是运行时沙箱）：

| 级别 | bridge | 获取方式 |
|---|---|---|
| L0 | `tool` `ui` `download` `store` `imageToolbox` | 无需特殊授权；`tool`/`imageToolbox` 访问共享存储时需要「所有文件访问」。 |
| L1 | `shizuku` `accessibility` | 用户在权限面板/系统设置中开启；Shizuku 需服务在线并授权，无障碍需系统设置里勾选。 |
| L2 | `root` | 用户显式打开「Root 提权」且 `su` 探测成功（默认禁用）。 |

- 能力的实际可用性由 `BridgeFactory` 在启动时探测，缺失的桥不注入，依赖它的卡片对 AI 不可见且执行时返回 `BRIDGE_UNAVAILABLE`。
- `privilege: "elevated"` 是声明性标签：Hub 过滤会要求设备至少具备 Shizuku / root / 无障碍之一；它不改变运行权限，实际能做什么由 `requires.bridges` 决定。审核与用户预期以它为准，**不要用 `privilege: none` 声明需要 L1/L2 能力的卡片**。

### 5.2 审批、悬浮看板与放手模式

卡片在任何入口运行（AI / 用户 / 事件）都会经过统一的自动化流程：

1. 若已请求取消，直接返回 `CARD_CANCELLED`。
2. 发布看板状态「正在运行卡片：<name>」。
3. 通过 `AutomationBus.requestApproval("运行卡片：<name>", "触发来源：<AI|用户|事件触发>")` 请求授权。
   - 放手模式开启，或本次运行已授权过 → 直接放行（一次运行内不再重复询问）。
   - 未授予悬浮窗权限（`SYSTEM_ALERT_WINDOW`）→ **审批直接失败**，卡片不会执行（错误 `CARD_DENIED`）。无障碍服务在线时看板走 `TYPE_ACCESSIBILITY_OVERLAY` 也能显示，但授权判断仍要求悬浮窗权限；建议授予悬浮窗权限或开启放手模式。
   - 否则在悬浮看板上显示允许/拒绝按钮，**60 秒未响应按拒绝处理**（看板显示「N 秒后自动拒绝」）；同一时刻只允许一个审批请求。
4. 执行卡片（并发上限 2）。
5. 运行结束（成功或失败）调用 `finish()`，看板显示「任务完成」约 3 秒后退场；期间新的运行会取消退场。

悬浮看板（`AutomationOverlayService`）行为：

- 显示当前步骤、最多 3 条历史步骤（内部缓存 4 条，重复 label 去重）、进度文字（`ui.progress` 的 label）与「停止」按钮。
- 可拖动；运行期间保持屏幕常亮（WakeLock）；空闲/结束约 3 秒后自动隐藏；同时有一个常驻前台服务通知。
- 无悬浮窗权限且无障碍离线时看板静默跳过；此时审批弹不出来，卡片会被 `CARD_DENIED` 拒绝（除非开启放手模式或已在本次运行中授权过）。
- 点「停止」调用 `AutomationBus.requestCancel()`，脚本通过 `ui.isCancelled()` 感知；看板按钮变为「正在停止」。取消不会强杀脚本，需要脚本配合轮询。
- 「放手模式」在权限面板开启，语义是：高权限/高风险卡片不再逐条确认，全部交给 AI 执行。开启后审批一律放行。

### 5.3 事件触发的开启与配置

- 总开关：「设置 → 自动化触发器」，关闭时所有卡片不自动运行。
- 逐卡开关：可单独启用/停用某张卡片的事件触发。
- 事件覆盖：用户可为卡片改写 `events`（覆盖 manifest 声明，空列表 = 显式关闭）与失败重试（`max_retries` 0–3、`retry_delay_seconds` 0–3600）。
- 执行日志：最近 200 条（时间、卡片、事件类型、负载摘要、成功/失败、消息）。
- 冷却（同一卡片同类型事件的最小间隔）：

| 事件 | 冷却 |
|---|---|
| `schedule` | 无（靠同分钟去重与间隔锚点） |
| `notification` | 3 秒 |
| `app_launch` | 60 秒 |
| `charging` | 5 秒 |
| `wifi` | 15 秒 |
| `network` | 15 秒 |
| `battery` | 60 秒 |
| `screen` | 5 秒 |
| `clipboard` | 2 秒（轮询周期） |
| `bluetooth` | 30 秒 |
| `location` | 60 秒（轮询周期） |

- 失败重试：脚本返回 `{error=...}` 或运行时错误都算失败；配置了重试的卡片会按延迟重新派发，重试耗尽后才发「卡片自动触发失败」通知（需要通知权限）。重试沿用同一 `args.event`。
- 事件运行同样受审批与并发闸门约束；事件触发的审批弹窗可能出现在用户不在前台时，请注意脚本里用 `ui.isCancelled()` 并在无人值守场景避免依赖 `ui.form`（300 秒超时会返回 nil）。

### 5.4 事件来源与服务依赖

| 事件 | 依赖 |
|---|---|
| `schedule` | 自动化触发器前台服务（每 60 秒 tick）；精确度是分钟级，受省电策略影响。 |
| `notification` | 通知使用权（NotificationListenerService）；跳过本应用自己的通知避免自触发。 |
| `app_launch` | 无障碍服务在线（每秒轮询前台包名）。 |
| `charging` / `battery` / `screen` | 无额外权限（电量、亮屏/解锁广播）。 |
| `wifi` / `network` | 无额外权限。 |
| `clipboard` | 无额外权限；Android 10+ 后台读剪贴板被系统限制时会静默跳过。 |
| `bluetooth` | API 31+ 需要 `BLUETOOTH_CONNECT`（未授权时静默禁用该事件源）。 |
| `location` | 定位权限（粗/精至少一个）；未授权时跳过。 |

触发器前台服务在总开关打开后常驻，开机/应用升级后由 `TriggerBootReceiver` 自动拉起。脚本失败只记录日志并（在重试耗尽后）发通知，不会打断宿主。

### 5.5 AI 设备工具（无需写卡片的自动化）

无障碍服务在线时，AI 会额外获得两个内置工具，适合一次性操作而非可复用卡片：

- `khatkit__device_screen`：截屏 + 本地 OCR + 当前窗口节点清单（可用 `include_ocr` / `include_nodes` 关闭）。
- `khatkit__device_act`：执行单个无障碍动作，`action` 取值 `click_text`、`click_id`、`tap`、`swipe`、`press`、`set_text`、`back`、`home`、`recents`、`notifications`、`open_app`、`wait_text`、`wake`、`screen_state`、`unlock`。

`device_act` 每次执行前同样请求「操作手机屏幕」授权（放手模式/本次已授权则跳过），取消与看板行为与卡片一致；`screen_state` 只读、`overlay_hide` / `overlay_show` 不操作屏幕，无需授权。

自愈与屏幕处理：

- **自动重试**：`click_text` / `click_id` / `set_text` 首次找不到目标会等待约 400ms 重试，最多 2 次（共 3 次尝试）；`wait_text` 保持自身超时语义，等待预算按 3 次尝试均分。仍失败时从当前窗口（`dumpWindow`）返回按文本相似度排序的 top 5 候选节点（含类名 / bounds / 中心坐标 / click、edit 标记），模型可据此改用 `click_id` 或 `tap` 坐标。
- **熄屏唤醒**：`tap` / `swipe` / `press` / `click_text` / `click_id` / `set_text` 执行前检查 `PowerManager.isInteractive`，屏幕熄灭时调用工具 bridge `wakeScreen()` 并重试一次，仍失败返回中文错误。
- **`wake`**：点亮屏幕并返回最新屏幕状态（`interactive` / `locked`）。
- **`screen_state`**：返回 `{"interactive":..,"locked":..,"secure":..,"text":"中文描述"}`。
- **`unlock`**：锁定时优先用 root shell、其次 Shizuku shell 执行 `input keyevent KEYCODE_WAKEUP` + `input swipe 540 1800 540 600` 解除普通锁屏；安全锁屏（PIN / 密码 / 图案，`KeyguardManager.isDeviceSecure`）无法用 input 绕过，会明确提示需手动解锁或在系统设置中关闭锁屏密码；无 root / Shizuku 时返回开启指引。归类为 `ui_action` 授权类别。

---

## 6. 安全与合规

### 6.1 能力声明与审核

- 卡片是审核制：manifest 声明什么能力，脚本就只能调用注入的那些 bridge；没声明就注入不了，调用会报错。
- `card-validator` 做确定性静态检查（字段、词表、bridge 调用、域名）；越权意图、破坏性副作用、与声明不符的高风险行为仍靠人工审核。
- 人工审核关注：是否越权、是否有破坏性副作用、是否与 `privilege` / `compliance` 描述相符。
- `privilege: "elevated"` 的卡片应显式写清 `compliance.note`。

### 6.2 网络白名单

- 所有网络访问（`httpGet` / `httpPost` / `httpMultipart`）的域名必须写入 `network.allow`；CI 扫描脚本正文，子域规则见 2.4。
- 运行时宿主不逐请求拦截，白名单是发布约定，请勿从参数拼接任意域名去请求未声明的主机。
- 敏感数据（API Key、Token）不要写进脚本或日志：用 `store.secretSet` / `store.secretGet`（Keystore 加密）或跨卡片共享区（明文但应用私有，参考内置极客猫卡片的 `store.sharedRead("zenneko_key")` 模式）。
- 代码中禁止硬编码私钥/令牌；内置卡片默认 Key 只是计次演示，应让用户替换。

### 6.3 敏感操作

- `shizuku.shell` / `root.shell` 是通用命令入口：不要把用户输入直接拼进命令，优先使用动作级 API（`setAppEnabled` / `settingsPut` / `pm`）。
- `engine: command` 的占位符由宿主白名单转义，无法注入空格/管道；卡片模板本身仍应写成固定动作。
- 危险操作（删除、禁用应用、覆盖文件）应先 `ui.confirm(..., danger = true)`，并在 `compliance.note` 说明后果。
- 隐私相关（读取屏幕、剪贴板、通知、位置）卡片应使用 `device` / `system` 域并给出风险说明；无障碍/截屏类必须 `privilege: "elevated"`。

### 6.4 静态校验器（card-validator）

纯 JVM 模块，卡片仓库 CI 直接复用：

```bash
# 目录结构：cards/<card-dir>/card.json + 入口脚本
./gradlew :card-validator:run --args="/path/to/cards"
```

输出格式：

```
[WARN ] <card>: VERSION_NOT_SEMVER ...
[ERROR] <card>: NETWORK_UNDECLARED ...
[OK    ] <card> (lua, file/convert)

checked N card(s), M error(s)
```

只要有一个 ERROR 退出码为 1（cards 目录不存在退出码 2）。校验项：

| 检查 | 错误码 |
|---|---|
| name 格式 | `NAME_INVALID` |
| version 非 SemVer | `VERSION_NOT_SEMVER`（WARNING） |
| engine / privilege 取值 | `ENGINE_UNKNOWN` / `PRIVILEGE_UNKNOWN` |
| triggers 为空/未知/重复 | `TRIGGER_EMPTY` / `TRIGGER_UNKNOWN` / `TRIGGER_DUPLICATE` |
| events 类型与参数自洽 | `EVENT_*`（见 2.9） |
| bridge 白名单 | `BRIDGE_UNKNOWN` |
| engine 与 entry/command 一致性 | `ENTRY_MISSING` / `COMMAND_MISSING` |
| tags 必填与词表 | `TAGS_MISSING` / `TAG_DOMAIN_INVALID` / `TAG_ACTION_INVALID` / `TAG_SCENE_INVALID` |
| game 域缺 compliance | `GAME_COMPLIANCE_MISSING` |
| UI 组件/source 白名单 | `UI_WIDGET_UNKNOWN` / `UI_SOURCE_INVALID` |
| 脚本调用了未声明的 bridge | `BRIDGE_UNDECLARED` |
| 脚本访问了白名单外的域名 | `NETWORK_UNDECLARED` |

校验器会把 `entry.lua` / `entry.js` 的正文一起扫描；脚本里出现 `tool.`、`ui.` 等字面调用和 `https?://host` 都会被检查。

---

## 7. 调试与常见问题

**Q：卡片对 AI 不可见 / 调用返回 `BRIDGE_UNAVAILABLE`。**
检查 `requires.bridges` 与设备能力：Shizuku 是否在线授权、无障碍是否开启、root 是否显式启用、共享存储是否授予「所有文件访问」。能力不符时卡片会被 Hub 硬过滤，AI 也看不到。错误文案会列出缺失能力。

**Q：AI 看不到刚安装的卡片。**
AI 工具列表有 5 分钟索引缓存和最多 20 张候选卡片限制；重新进入聊天/重启生成会刷新。未声明 `triggers: ["ai"]` 的卡片不会暴露。

**Q：表单没弹出来。**
`ui.form` 由脚本主动调用；AI 已填参时应跳过表单。事件触发在后台运行时表单会等待用户回到应用（300 秒超时返回 nil），无人值守场景请勿依赖 UI。`file_picker` / `dir_picker` 目前是路径文本输入框。

**Q：`tool.readText` 返回了 table 而不是字符串。**
说明调用失败，返回的是 `{__error = "..."}`。先 `type(res) == "table" and res.__error` 检查。共享存储路径需要「所有文件访问」。

**Q：OCR 不工作 / 很慢。**
`tool.ocrText` / `ocrBoxes` 用随 APK 打包的 ML Kit 中文模型（支持中文+拉丁），无需联网；大图会慢，建议先 `compressImage` 或裁剪。失败返回 JSON 字符串（不是对象），用字符串包含 `"error"` 判断。首次识别可能需要初始化时间。

**Q：截屏 / 找图 / 找色失败。**
`captureScreen`、`findImage`、`tapImage`、`findColor` 都依赖无障碍 `takeScreenshot`，需要 Android 11（API 30）及以上；低版本返回中文错误文本或 `{"error":...}`。`captureScreen` 落盘到共享存储还需要「所有文件访问」；`findImage` 只在内存中匹配，不需要存储权限。锁屏/息屏时截图可能失败，可先 `tool.wakeScreen()` 或 Shizuku/root 执行 `input keyevent KEYCODE_WAKEUP`。

**Q：模板匹配明明在屏幕上却找不到。**
默认阈值 0.9，可显式降到 0.8 左右试；模板裁剪要包含按钮/图标本体并留少量边距，避免跨尺度失真；确认截屏成功（`findImage` 返回 `{"error":...}` 表示截图/解码失败）。返回的 x/y 是屏幕像素，若卡片在别的分辨率上开发，请用当前截图重新裁剪模板。

**Q：Shizuku 方法返回 `[error]`。**
确认 Shizuku 服务在运行且已授权；某些 Shizuku 版本没有私有 `newProcess`，会返回「当前 Shizuku 版本不支持 newProcess」。`shell` 超时 30 秒，输出格式是 `[exit N]` + stdout/stderr。

**Q：root 卡片不工作。**
root 默认禁用，需要在权限面板打开且 `su` 探测成功；`requires.bridges` 里要写 `root`。root 命令超时 30 秒。

**Q：锁屏 / 息屏下自动化失败。**
点击、截屏、手势在锁屏下大多不可用；脚本里可先 `tool.wakeScreen()`，再用 `accessibility.waitForPackage` 等确认前台，或通过 Shizuku/root 解锁（`input keyevent`）。Android 的 `takeScreenshot` 在锁屏或安全窗口上可能被系统拒绝。

**Q：事件触发不生效。**
依次检查：总开关是否打开；卡片是否被单独停用；manifest/覆盖里是否有对应事件；权限（通知使用权、无障碍、定位、蓝牙）；冷却是否正在生效；日志里是否有失败记录。后台省电策略可能杀掉服务，建议关闭对 KhatKit 的电池优化（权限清单有入口）。

**Q：`download.status` 一直 `queued` / `running`。**
下载有全局并发上限（设置里 1–5，默认 3），排队属正常；`waitSeconds` 只是单次最长等待。完成后 `file` 才会非空。同名同 URL 已完成任务会复用。

**Q：`store` 写入报配额超限。**
`file` + `db` 合计不能超过 manifest 的 `store.quota_mb`（最小 1MB）。清理旧数据或调大配额（这是给用户的信任边界，不要滥用）。

**Q：脚本调用了方法但结果怪怪的（比如 `download.list`、`accessibility.addOverlay`）。**
这些返回宿主对象（句柄/View），跨引擎会退化成字符串。请使用文档标注为「脚本可用」的方法。

**Q：JS 里 `console.log` / `setTimeout` / `fetch` 报错。**
QuickJS 环境没有这些宿主 API，也没有事件循环；请写同步脚本，用返回值/`ui.show` 输出结果。

**Q：可选参数没传会怎样？**
Kotlin 默认参数不会通过反射生效：对象参数变 `null`，数字变 `0`，布尔变 `false`。所有可选参数请显式传值（`headers` 传 `{}`、`saveBinary` 传 `true`、`tolerance` 传 16 等）。

**Q：写中文文件乱码？**
`readText` / `writeText` 使用 UTF-8；确保路径与内容都是 UTF-8 字符串。Lua `string.len` 是字节数，长度按字节估算。

---

## 8. 完整示例卡片

以下示例均可直接放入 `khatkit/src/main/assets/cards/<name>/` 或本地缓存目录运行。

### 8.1 无障碍自动化：按文字点击并带重试

`a11y-click-text-retry/card.json`：

```json
{
  "name": "a11y_click_text_retry",
  "version": "1.0.0",
  "description": "等待指定文字出现并点击，支持重试与停止（无障碍）",
  "author": "you",
  "license": "MIT",
  "engine": "lua",
  "entry": { "lua": "main.lua" },
  "privilege": "elevated",
  "requires": { "bridges": ["accessibility", "ui"], "libs": [], "bins": [], "env": [] },
  "network": { "allow": [] },
  "parameters": {
    "type": "object",
    "properties": {
      "text": { "type": "string", "description": "要点击的控件文字" },
      "timeout_s": { "type": "integer", "description": "等待秒数，默认 10" },
      "exact": { "type": "boolean", "description": "是否精确匹配" }
    },
    "required": ["text"]
  },
  "ui": {
    "text": { "source": "ui", "widget": "input" },
    "timeout_s": { "source": "ui", "widget": "number" },
    "exact": { "source": "ui", "widget": "switch" }
  },
  "tags": { "domain": "device", "action": "control", "scene": "daily" },
  "compliance": { "risk": "medium", "note": "需要无障碍服务；请确认点击目标" }
}
```

`main.lua`：

```lua
local text = args.text
local timeoutS = tonumber(args.timeout_s) or 10
local exact = args.exact == true

if not text or text == "" then
  local v = ui.form("无障碍点击", {
    { type = "input", id = "text", label = "要点击的文字" },
    { type = "number", id = "timeout_s", label = "等待秒数", default = 10 },
    { type = "switch", id = "exact", label = "精确匹配" }
  })
  if not v then return { cancelled = true } end
  text = v.text
  timeoutS = tonumber(v.timeout_s) or 10
  exact = v.exact == true
end
if not text or text == "" then return { error = "缺少文字" } end

ui.automationStatus("等待「" .. text .. "」出现", timeoutS .. "s")

local deadline = os.time() + timeoutS
local node = nil
while os.time() <= deadline do
  if ui.isCancelled() then return { cancelled = true } end
  node = accessibility.waitForNode({ text = text, exact = exact }, 1000)
  if node then break end
  tool.sleep(1)
end

if not node then
  local pkg = accessibility.currentPackage()
  return { clicked = false, text = text, package = pkg, reason = "等待超时" }
end

ui.automationStatus("点击「" .. text .. "」")
local ok = accessibility.click({ text = text, exact = exact })
if not ok and node.centerX and node.centerY then
  ok = accessibility.tap(node.centerX, node.centerY)
end

return { clicked = ok == true, text = text, package = accessibility.currentPackage() }
```

要点：`elevated` + `accessibility` bridge；每次循环检查 `ui.isCancelled()`；优先节点点击，失败后坐标兜底；返回值保持精简。

### 8.2 定时 + 文件：每日检修记录归档

`daily_archive/card.json`：

```json
{
  "name": "daily_archive",
  "version": "1.0.0",
  "description": "每天 21:30 把当天追加的待办归档到月度文件",
  "author": "you",
  "license": "MIT",
  "engine": "lua",
  "entry": { "lua": "main.lua" },
  "privilege": "none",
  "requires": { "bridges": ["tool", "store"], "libs": [], "bins": [], "env": [] },
  "network": { "allow": [] },
  "parameters": { "type": "object", "properties": {} },
  "triggers": ["user"],
  "events": [
    { "type": "schedule", "times": ["21:30"], "days": [1, 2, 3, 4, 5] }
  ],
  "tags": { "domain": "file", "action": "batch", "scene": "work" },
  "store": { "quota_mb": 5, "secret": false }
}
```

`main.lua`：

```lua
-- 事件触发时 args.event.time 为 "21:30"；手动运行时时无 event
local event = args.event or {}
local now = event.time or "手动"
local day = os.date("%Y-%m-%d")
local month = os.date("%Y-%m")

local inbox = "/sdcard/Download/KhatKit/inbox-" .. day .. ".txt"
local archive = "/sdcard/Download/KhatKit/archive-" .. month .. ".txt"

ui.automationStatus("归档 " .. day)

local content = tool.readText(inbox)
if type(content) == "table" and content.__error then
  return { skipped = true, reason = "今日无待办", file = inbox }
end
if not content or content == "" then
  return { skipped = true, reason = "今日无待办" }
end

local old = tool.readText(archive)
if type(old) == "table" and old.__error then old = "" end

local entry = "== " .. day .. " (" .. now .. ") ==\n" .. content .. "\n"
tool.writeText(archive, (old or "") .. entry)
tool.writeText(inbox, "")

local count = tonumber(store.kvGet("archived_days", "0")) or 0
store.kvSet("archived_days", tostring(count + 1))
store.kvSet("last_archive", archive)

return { archived = true, file = archive, bytes = #entry, total_days = count + 1 }
```

要点：事件卡片不依赖 UI；`args.event` 缺省时兼容手动运行；`tool.readText` 失败用 `__error` 判断；归档后可清空 inbox。

### 8.3 找图点击 + 网络：下载模板、执行点击、记录结果

`find-and-tap/card.json`：

```json
{
  "name": "find_and_tap",
  "version": "1.0.0",
  "description": "从网络下载模板图，在屏幕上找到并点击，支持取消",
  "author": "you",
  "license": "MIT",
  "engine": "js",
  "entry": { "js": "main.js" },
  "privilege": "elevated",
  "requires": { "bridges": ["tool", "accessibility", "ui", "store"], "libs": [], "bins": [], "env": [] },
  "network": { "allow": ["raw.githubusercontent.com"] },
  "parameters": {
    "type": "object",
    "properties": {
      "template_url": { "type": "string", "description": "模板图 URL（PNG）" },
      "threshold": { "type": "number", "description": "匹配阈值 0~1，默认 0.88" },
      "timeout_s": { "type": "integer", "description": "等待秒数，默认 30" }
    },
    "required": ["template_url"]
  },
  "ui": {
    "template_url": { "source": "ui", "widget": "input" },
    "threshold": { "source": "ui", "widget": "number" }
  },
  "tags": { "domain": "device", "action": "control", "scene": "daily" },
  "compliance": { "risk": "medium", "note": "模板图访问网络，点击行为由用户自行确认" },
  "store": { "quota_mb": 5, "secret": false }
}
```

`main.js`：

```js
const url = args.template_url;
if (!url) return { error: "缺少 template_url" };

const threshold = Number(args.threshold) || 0.88;
const timeoutS = Number(args.timeout_s) || 30;
const fileName = "tpl-" + (url.split("/").pop() || "tpl.png");
const local = "/sdcard/Download/KhatKit/" + fileName;

tool.mkdir("/sdcard/Download/KhatKit");

// 1) 用 findImage 探测本地模板是否可用；不可用时用 download bridge 取回
let probe = accessibility.findImage(local, threshold);
if (probe.indexOf('"error"') !== -1) {
  ui.automationStatus("下载模板…");
  const id = download.start({ url: url, name: fileName });
  let st = download.status(id, 5);
  while (st.state === "queued" || st.state === "running") {
    if (ui.isCancelled()) { download.cancel(id); return { cancelled: true }; }
    st = download.status(id, 5);
  }
  if (st.state !== "done") return { error: st.error || ("模板下载失败：" + st.state) };
  tool.copyPath(st.file, local); // 从应用内部下载目录复制到共享目录，供 findImage 读取
  probe = accessibility.findImage(local, threshold);
  if (probe.indexOf('"error"') !== -1) return { error: "模板不可用：" + probe };
}

// 2) 找图并点击（每 300ms 轮询由 tapImage 内部完成）
ui.automationStatus("查找模板并点击", timeoutS + "s");
const ok = accessibility.tapImage(local, threshold, timeoutS * 1000);
if (!ok) {
  if (ui.isCancelled()) return { cancelled: true };
  return { clicked: false, template: local };
}

// 3) 记录一次成功（store 隔离存储）
const n = Number(store.kvGet("tap_count", "0")) + 1;
store.kvSet("tap_count", String(n));

return {
  clicked: true,
  template: local,
  threshold: threshold,
  total: n,
  package: accessibility.currentPackage()
};
```

要点：`network.allow` 只放了模板图 CDN 域名；用 `download` bridge 处理二进制文件，避免把图片当文本读；`tapImage` 自带轮询与取消窗口内的失败返回；`store` 记录累计次数。若模板已在本地，可去掉下载段并跳过 `download` 声明。

---

## 9. 附录

### 9.1 事件字段速查（args.event）

| 事件 | 键 |
|---|---|
| schedule | `type`, `time` |
| notification | `type`, `package`, `title`, `text` |
| app_launch | `type`, `package` |
| charging | `type`, `state`（connected/disconnected） |
| wifi | `type`, `ssid`, `state` |
| network | `type`, `state`（online/offline） |
| battery | `type`, `state`（charging/discharging）, `level` |
| screen | `type`, `state`（on/off/locked/unlocked） |
| clipboard | `type`, `text` |
| bluetooth | `type`, `state`, `device` |
| location | `type`, `state`（enter/exit）, `lat`, `lon` |

空值字段不会出现在 `args.event` 里（例如通知没有标题时没有 `title` 键），脚本访问前先判空。

### 9.2 能力 / 权限对照

| 能力 | 权限或设置 | 相关 API 示例 |
|---|---|---|
| 共享存储读写 | 所有文件访问 | `tool.readText`、`captureScreen`、`ocrText` |
| 网络 | `network.allow` 白名单（运行时直连） | `tool.httpGet/httpPost/httpMultipart` |
| 剪贴板 | 应用前台时可用 | `tool.setClipboard/getClipboard`、`accessibility.paste` |
| 点亮屏幕 | `WAKE_LOCK`（部分 ROM 拦截） | `tool.wakeScreen` |
| 无障碍 | 系统设置开启服务 | `accessibility.*` |
| Shizuku | 服务在线 + 授权 | `shizuku.*` |
| root | 显式开启 + `su` 可用 | `root.shell` |
| 通知监听 | 通知使用权 | `notification` 事件 |
| 定位 | 位置权限 | `location` 事件 |
| 蓝牙 | `BLUETOOTH_CONNECT`（API 31+） | `bluetooth` 事件 |
| 悬浮看板审批 | 悬浮窗权限（或放手模式） | `ui.automationStatus` / 审批流程 |

### 9.3 返回 / 错误约定速查

| 场景 | 脚本侧表现 |
|---|---|
| 正常返回 | `return { ok = true, ... }` |
| 业务失败 | `return { error = "原因" }` |
| 用户取消 | `return { cancelled = true }` |
| 桥调用失败 | 收到 `{__error = "..."}`（table/object），需自行检查 |
| 脚本抛错/语法错误 | 引擎层 `Err("RUST_RUNTIME", ...)`，AI 看到 `{"error":"..."}` |
| 设备缺能力 | `Err("BRIDGE_UNAVAILABLE", ...)` |
| 用户拒绝审批 | `Err("CARD_DENIED", ...)` |
| 用户点停止 | 下一次 `ui.isCancelled()` 返回 true；新运行被拒为 `CARD_CANCELLED` |
