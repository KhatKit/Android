# 脚本可用宿主接口清单（权威索引）

本文件是**宿主提供给卡片脚本的全部接口**的唯一清单：每个 bridge、每个方法、参数、返回与可见性都在这里。
写法与用法示例见 [`lua-card-development.md`](lua-card-development.md)，本文件只回答「有哪些接口、签名是什么、能不能调」。

## 稳定性保证

- 声明源：`khatkit/src/main/java/heizige/kk/khatkit/bridge/Bridges.kt`。
- `BridgeApiDocTest`（`:khatkit` 单元测试）做**双向**比对：代码里每个方法必须在本文件里以反引号标识出现（如 `tool.readText(`），本文件里出现的每个同类标识都必须在代码里真实存在。任一侧漏改即测试失败：

```bash
./gradlew :khatkit:testDebugUnitTest --tests "heizige.kk.khatkit.bridge.BridgeApiDocTest"
```

- 改接口的规矩：先改 `Bridges.kt`，再改本文件，最后跑上面那条命令；两者不一致时不允许提交。
- 脚本调用是**反射按名字**调用，Kotlin 默认参数值在脚本侧不生效，缺参会被补成 `null` / `0` / `false` / 空集合，所以可选参数要显式传（如 `tool.httpGet(url, {})`）。

## 1. 运行时全局

| 全局 | 类型 | 说明 |
|---|---|---|
| `args` | table | 卡片入参（`card.json` 的 `parameters` 按 JSON Schema 校验后注入）。无入参时是空 table。 |
| `require(name)` | function | 加载 `requires.libs` 声明的共享库（`package.preload`）；宿主也内置了同名库时优先用内置的。 |

## 2. Bridge 能力表

| 脚本名 | 接口 | 权限 | 触发条件 | 说明 |
|---|---|---|---|---|
| `tool` | `ToolBridge` | L0 | 始终 | 文件 / 网络 / 剪贴板 / OCR。共享存储需「所有文件访问」。 |
| `net` | `NetBridge` | L0 | 始终 | 受 `network.allow` 域名白名单约束的网络请求。 |
| `fs` | `FsBridge` | L0 | 始终 | 卡片私有/声明目录的结构化文件操作。 |
| `json` | `JsonBridge` | L0 | 始终 | 结构化 JSON 编解码、查询与合并。 |
| `crypto` | `CryptoBridge` | L0 | 始终 | 哈希、HMAC、AES、Base64、URL 与随机值工具。 |
| `time` | `TimeBridge` | L0 | 始终 | 时间戳、ISO-8601、格式化、解析与本地化信息。 |
| `host` | `HostBridge` | L0 | 始终 | 宿主信息、能力体检、日志与运行耗时。 |
| `system` | `SystemBridge` | L0 | 始终 | URL、分享、通知、电池、存储和网络状态。 |
| `ai` | `AiBridge` | L0 | 始终 | 同步调用宿主已配置的模型供应商。 |
| `mediaPicker` | `MediaPickerBridge` | L0 | 始终 | 选择图片/视频并返回缓存文件路径。 |
| `ui` | `UiBridge` | L0 | 始终 | 弹层、表单、结果卡片、进度、看板。 |
| `web` | `WebBridge` | L0 | 始终 | 网页登录弹层 + 按 host 隔离的本机 Cookie。 |
| `browser` | `BrowserBridge` | L0/默认逐次审批 | 宿主注入浏览器时 | 本轮卡片独立标签、页面快照与动作；需声明 `network.allow`。 |
| `download` | `DownloadBridge` | L0 | 始终 | 后台下载（脚本退出后继续）。 |
| `store` | `StoreBridge` | L0 | 始终 | 卡片隔离的 kv / file / db / secret + 跨卡片共享区 + 自管 SQLite 与向量检索。 |
| `schedule` | `ScheduleBridge` | L0 | 始终 | 脚本运行期自建的定时任务（WorkManager，非精确闹钟）。 |
| `imageToolbox` | `ImageToolboxBridge` | L0 | 依赖包已下载 | 本地图像工具箱，随 `requires.dependencies` 下发的 dex 包注入，见 [dependency-system.md](dependency-system.md)。 |
| `shizuku` | `ShizukuBridge` | L1 | Shizuku 在线 | 动作级 API + `shell`。 |
| `accessibility` | `AccessibilityBridge` | L1 | 无障碍服务开启 | 找节点 / 点击 / 手势 / 识图。 |
| `root` | `RootBridge` | L2 | 设置里开启 root | 只有 `shell`。 |

未声明的 bridge 不会注入，对应全局名不存在；声明了但设备不具备的，卡片不可见 / 运行时报 `BRIDGE_UNAVAILABLE`。
依赖包注入的动态 bridge（名字由卡片自定）不在本文件里，字段与生命周期见 [dependency-system.md](dependency-system.md)。

## 2.1 browser（可编程浏览器）

声明 `requires.bridges: ["browser"]` 和 `network.allow`。下列接口返回 JSON
字符串（close 返回状态文本）；通过 `json` bridge 解码时也要声明该 bridge。
每次卡片运行持有自己的标签 ID，不能传入聊天或其他卡片的 ID，退出后自动销毁。
Cookie 仍为 WebView 进程共享；标签隔离不代表登录身份隔离。

| 接口 | 返回 | 说明 |
| --- | --- | --- |
| `browser.open(url)` | string | 打开标签，返回 id/url/title/snapshot。 |
| `browser.snapshot(sessionId, selector, offset)` | string | 当前语义快照。selector 可传 null/nil，offset 从 0 开始；用 next_offset 分页。 |
| `browser.act(sessionId, action, arguments)` | string | 固定动作集合，参数见下表；不开放任意 JavaScript。 |
| `browser.close(sessionId)` | string | 关闭本轮标签。 |

| action | arguments |
| --- | --- |
| navigate | `{url}` |
| click / hover | `{selector}`，支持 CSS 或快照里的 `@b` 引用 |
| type | `{selector, text}` |
| scroll | `{x, y}`，整数 CSS 像素 |
| press_key | `{selector, key}` |
| wait | `{selector?, text?, timeout_ms?}`；至少一个条件，等待可见元素/文本，默认 10000ms，上限 30000ms |
| snapshot | `{selector?, offset?, since?}`；since 为之前的 snapshot_id，支持增量快照 |
| read_element | `{selector}`；只读指定元素文本，最多 12000 字符 |
| extract_text / extract_links | `{}` |
| screenshot | `{}`；返回 image_url 和 capture 元数据，仅截当前视口 |

默认通过审批闸门逐次询问，缺少闸门时拒绝；`permissions.methods` 的
`browser.open/snapshot/act/close` 可设 allow/ask/deny。方法调用受卡片剩余预算和
单次 30 秒上限约束。退出清理不再询问审批。

显式导航、WebView 主框架导航和可拦截资源请求检查 `network.allow`（host 与
子域；`*` 放行）。这不是完整的网络沙箱：WebSocket、service worker 以及
WebView 未回调的重定向资源等仍需进一步隔离；不要据此执行不可信脚本。
页面内容也不能作为用户授权来源。浏览器完整 UI、独立 cookie 和真机验收仍在推进。
可运行示例的声明、参数和适配边界见 [browser examples](examples/README.md)。

## 3. tool（L0）

其中 `tool.httpGet`、`tool.httpPost`、`tool.httpMultipart` 已废弃，新卡片请使用 `net`；
文件类方法（`tool.readText` / `writeText` / `listFiles` / `copyPath` / `deletePath` / `mkdir` /
`renamePath` / `zip` / `unzip` / `readBase64` / `saveBase64` / `openDir`）已废弃，新卡片请使用 `fs`。

**旧方法与 `fs` 的差异（迁移时注意）**：`tool.*` 文件方法保持历史行为——按传入的绝对路径操作，
只受「所有文件访问」权限约束；`fs.*` 则是沙箱化的，只能访问卡片私有目录与 `permissions.fsRead/fsWrite`
声明的目录，且 `fs.delete` 每次都会向用户确认。也就是说 `tool.deletePath` 不审批、`fs.delete` 审批，
这条不一致是**有意保留**的：不改已发布卡片的脚本行为。

| 接口 | 返回 | 说明 |
|---|---|---|
| `tool.readText(path)` | string | 读文本文件（UTF-8）。 |
| `tool.writeText(path, content)` | 无 | 写文本文件，自动建父目录。 |
| `tool.httpGet(url, headers)` | string | GET，返回响应体。`headers` 可传 `{}`。 |
| `tool.httpPost(url, body, headers)` | string | POST 文本，返回响应体。 |
| `tool.httpMultipart(url, fields, fileField, filePath, headers, saveBinary)` | string | multipart/form-data POST。`fileField` + `filePath` 都非空时附带单个文件段；`saveBinary` 为真且响应是图片时把二进制写入共享存储返回路径，否则返回响应文本。 |
| `tool.readBase64(path)` | string | 读文件为 data URL（base64），用于 JSON 接口上传图片。 |
| `tool.saveBase64(data, outputPath)` | string | data URL / 裸 base64 解码写入 `outputPath`，返回路径。 |
| `tool.compressImage(path, quality)` | string | 压成 JPEG（`quality` 1–100），输出 `<name>_compressed.jpg`，返回路径。 |
| `tool.openDir(path)` | 无 | 用系统文件管理器打开目录。 |
| `tool.listFiles(path)` | string | 列目录，返回 **JSON 字符串**：`[{name, path, is_dir, size, modified}]`，需 `json.decode` 后再用（已废弃，请改用 `fs.list`）。 |
| `tool.copyPath(src, dst)` | 无 | 复制文件 / 目录。 |
| `tool.deletePath(path, recursive)` | boolean | 删除；非空目录需 `recursive=true`。 |
| `tool.mkdir(path)` | 无 | 建目录（含父目录）。 |
| `tool.renamePath(src, dst)` | 无 | 重命名 / 移动。 |
| `tool.zip(paths, output)` | string | 打包多个文件/目录到 zip，返回 `output`。 |
| `tool.unzip(zipPath, outputDir)` | string | 解压到 `outputDir`（含路径逃逸校验），返回目录。 |
| `tool.sleep(seconds)` | 无 | 阻塞等待（长脚本配合 `ui.isCancelled()` 使用）。 |
| `tool.setClipboard(text)` | 无 | 写系统剪贴板（应用在前台时可用）。 |
| `tool.getClipboard()` | string | 读剪贴板，无内容返回空串。 |
| `tool.wakeScreen()` | string | 点亮屏幕，成功返回中文提示，失败返回中文错误。 |
| `tool.ocrText(path)` | string | 本地图片 OCR（中文 + 拉丁），返回识别文本。 |
| `tool.ocrBoxes(path)` | string | OCR 带坐标，返回 **JSON 字符串**：`[{text,x,y,w,h}]`（像素坐标，已按 EXIF 校正），需 `json.decode`；失败返回 `{"error":"..."}` 字符串。 |

## 3.1 net（L0）

| 接口 | 返回 | 说明 |
|---|---|---|
| `net.get(url, headers)` | string | GET；host 必须命中 `network.allow`。 |
| `net.post(url, body, headers)` | string | JSON POST。 |
| `net.put(url, body, headers)` | string | JSON PUT。 |
| `net.delete(url, headers)` | string | DELETE。 |
| `net.multipart(url, fields, fileField, filePath, headers, saveBinary)` | string | multipart；当前宿主未启用时返回错误。 |
| `net.streamText(url, method, body, headers, timeoutSeconds)` | string | 缓冲完整响应后一次性返回，不提供增量结果。 |
| `net.toFile(url, outputPath, headers)` | string | 下载二进制并返回输出路径。 |
| `net.head(url, headers)` | table | 返回 `{status, headers, contentType, length}`。 |

## 3.2 fs（L0）

| 接口 | 返回 | 说明 |
|---|---|---|
| `fs.read(path)` | string | 读取卡片允许目录内的文本。 |
| `fs.write(path, content, append)` | 无 | 写入卡片允许目录；`append` 显式传布尔值。 |
| `fs.exists(path)` | boolean | 检查路径是否存在。 |
| `fs.stat(path)` | table | 返回路径、名称、目录标志、大小和修改时间。 |
| `fs.list(path, recursive, limit)` | table[] | 结构化列目录结果。 |
| `fs.copy(src, dst)` | 无 | 复制文件或目录。 |
| `fs.move(src, dst)` | 无 | 移动文件或目录。 |
| `fs.mkdir(path)` | 无 | 创建目录。 |
| `fs.delete(path, recursive)` | boolean | 删除；默认需要审批，非空目录需 `recursive=true`。 |
| `fs.zip(paths, output)` | string | 压缩文件列表（入参与输出都要在沙箱内），返回输出路径。 |
| `fs.unzip(zipPath, outputDir)` | string | 解压 zip 到沙箱内目录；条目越界（zip slip）一律拒绝。 |
| `fs.readBase64(path)` | string | 读取 Base64。 |
| `fs.saveBase64(data, outputPath)` | string | 写入 Base64 并返回路径。 |
| `fs.openDir(path)` | 无 | 打开目录。 |

## 3.3 json（L0）

| 接口 | 返回 | 说明 |
|---|---|---|
| `json.decode(text)` | table / nil | 解码 JSON；JSON `null` 返回 nil。 |
| `json.encode(value)` | string | 编码对象、数组、标量或 nil。 |
| `json.query(text, path, default)` | string / nil | 查询 `a.b[0].c`，未命中返回 default。 |
| `json.merge(base, patch)` | string | 合并两个 JSON 对象，patch 字段覆盖 base。 |
| `json.pluck(text, keys)` | table | 按键提取字段。 |
| `json.pretty(text)` | string | 校验并返回 JSON 文本。 |

## 3.4 crypto（L0）

| 接口 | 返回 | 说明 |
|---|---|---|
| `crypto.sha256(text)` | string | UTF-8 文本 SHA-256 十六进制。 |
| `crypto.sha256File(path)` | string | 文件 SHA-256 十六进制。 |
| `crypto.md5(text)` | string | UTF-8 文本 MD5。 |
| `crypto.hmacSha256(text, key, encoding)` | string | HMAC-SHA256，encoding 为 `hex` 或 `base64`。 |
| `crypto.uuid()` | string | UUID。 |
| `crypto.randomInt(min, max)` | number | 闭区间随机整数。 |
| `crypto.randomToken(bytes)` | string | 无填充 Base64URL 随机 token。 |
| `crypto.urlEncode(text)` / `crypto.urlDecode(text)` | string | URL 编解码。 |
| `crypto.base64(text)` / `crypto.base64Decode(text)` | string | UTF-8 Base64 编解码。 |
| `crypto.hexEncode(data)` / `crypto.hexDecode(text)` | string | UTF-8 十六进制编解码。 |
| `crypto.aesEncrypt(text, key, iv)` / `crypto.aesDecrypt(text, key, iv)` | string | AES/CBC/PKCS7 + Base64；key 为 16/24/32 字节，iv 为空时使用零 iv。 |

## 3.5 time（L0）

| 接口 | 返回 | 说明 |
|---|---|---|
| `time.now()` | number | 当前 Unix 时间戳，单位毫秒。 |
| `time.nowIso()` | string | 当前 UTC ISO-8601 文本。 |
| `time.format(pattern, timestampMs, timeZone)` | string | `SimpleDateFormat` 语义；timestampMs 为 0 表示现在。 |
| `time.parse(text, pattern)` | number | pattern 为空时按 ISO-8601 解析，否则按 `SimpleDateFormat` 解析。 |
| `time.timeZone()` | string | 设备默认时区 ID。 |
| `time.locale()` | string | 当前 Locale 的 BCP-47 标签。 |

## 3.6 host（L0）

| 接口 | 返回 | 说明 |
|---|---|---|
| `host.info()` | table | `{appVersion, osVersion, sdkInt, engine}`。 |
| `host.card()` | table | 当前卡片的名称、版本、作者和标签。 |
| `host.health()` | table | root、Shizuku、无障碍、所有文件访问、平台 SQLite 是否支持 FTS5 等能力状态。 |
| `host.capabilities()` | string[] | 当前可用 bridge 名称。 |
| `host.log(level, message)` | 无 | 写入宿主日志，level 支持 debug/info/warn/error。 |
| `host.setTimeout(ms)` | 无 | 设置本次运行的超时预算（从此刻起算毫秒，0 = 不限）；到点后所有 bridge 调用返回 `{"__error":"卡片运行超时，已终止"}`，`ui.isCancelled()` 同时返回 true。 |
| `host.elapsedMs()` | number | 当前卡片运行已耗时毫秒数。 |

## 3.7 system（L0）

| 接口 | 返回 | 说明 |
|---|---|---|
| `system.openUrl(url)` | boolean | 仅允许 http/https。 |
| `system.share(text, title)` | boolean | 打开系统分享面板。 |
| `system.toast(text)` | 无 | 显示短 Toast。 |
| `system.vibrate(durationMs)` | 无 | 振动指定毫秒数。 |
| `system.launchApp(packageName, activity)` | boolean | 启动应用；失败返回 false。 |
| `system.postNotification(channelId, channelName, title, text, actions)` | boolean | 无通知权限时返回 false。 |
| `system.screenState()` | string | `on` / `off` / `unlocked` / `locked`。 |
| `system.battery()` | table | `{level, charging, temperature}`。 |
| `system.storage()` | table | `{total, available}`。 |
| `system.connectivity()` | table | `{online, type, metered}`。 |

## 3.8 ai（L0 + 敏感）

**阻塞、非流式**调用用户已在设置里配好的模型（Rust bridge 只有同步契约，拿不到增量输出；
需要进度反馈时用 `ui.progress` 自己分段汇报）。未配置供应商时返回中文错误。

| 接口 | 返回 | 说明 |
|---|---|---|
| `ai.chat(prompt, system, provider, model, imagePaths, maxTokens, temperature, timeoutSeconds)` | string | 阻塞至模型返回完整文本；未配置供应商时返回中文错误。 |
| `ai.complete(prompt, maxTokens)` | string | 无 system 的一次性补全。 |

参数语义：

| 参数 | 默认 | 说明 |
|---|---|---|
| `prompt` | 必填 | 不能为空，否则返回中文错误。 |
| `system` | `""` | 为空时不发 system 消息。 |
| `provider` | `""` | 供应商名（`settings.providers[].name`）；空 = 用当前聊天模型所在的供应商。 |
| `model` | `""` | 模型 ID / 显示名；空 = 用当前聊天模型。找不到时返回中文错误并列出可用模型。 |
| `imagePaths` | `[]` | 多模态输入的**本地文件路径**（`mediaPicker.pickMedia` 或 `fs` 的返回值）。 |
| `maxTokens` | `0` | 0 = 不限制（沿用模型默认）。 |
| `temperature` | `0.0` | 0 = 不覆盖用户配置。 |
| `timeoutSeconds` | `120` | 上限 600；实际超时取它与卡片运行剩余预算的较小值，模型超时不会占死引擎线程。 |

凭据与审批：

- **卡片不能自带 key**：只能调用用户已配置的供应商（决策记录见 docs/bridge-expansion-spec.md §7.3）。
- **默认每次都要用户点允许**（审批类别 `ai_invoke`）；卡片在 `card.json` 里声明
  `"permissions": { "methods": { "ai.chat": "allow" } }` 后不再询问，写 `"deny"` 直接拒绝
  （在调用前就失败，不会进入模型）。`ai.complete` 可单独声明，未声明时沿用 `ai.chat`。
  运行时硬超时优先于策略：已超时的运行任何方法都先返回超时错误。
- 未配置供应商 / 模型不存在 / 超时，都会返回 `{"__error":"中文说明"}`。
- 每次调用都会以 `trigger = ai.chat`、`price = 0` 上报一次 Hub 统计（卡片价格仍按卡片调用结算）。

## 3.9 mediaPicker（L0）

打开宿主媒体选择器（与聊天附件同一套相册 / 多选 / 预览），**阻塞**到用户选完或取消。

| 接口 | 返回 | 说明 |
|---|---|---|
| `mediaPicker.pickMedia(options)` | string[] | 用户选择媒体后返回应用缓存路径；取消返回空数组。 |

`options` 字段（都不传等价于 `{}`）：

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `media` | string | `image` | `image` / `video` / `any`，选择器只呈现该类型。 |
| `multiple` | boolean | `true` | 是否允许多选；`false` 时上限强制为 1。 |
| `max` | number | `9` | 最多可选数量，钳在 1–50。 |

- 返回的是**复制进应用缓存后的真实文件路径**（`cacheDir/cards/<卡片名>/picked/`）：
  选择器给的 URI 在卡片运行结束后可能失效，所以宿主先落地成文件再回传。
  缓存目录会被系统按空间压力回收，长期结果请用 `fs` 写进卡片目录。
- 用户取消、宿主无法呈现选择器或等待超时（300 秒）都返回**空数组**，不抛错；
  无人值守场景（后台触发）不要依赖这个接口。

```lua
local paths = mediaPicker.pickMedia({ media = "image", multiple = true, max = 3 })
if #paths == 0 then return { cancelled = true } end
return { count = #paths, first = paths[1] }
```

> PDF 能力已剥离到依赖包：`mergePdf` 不再提供，改用 `imageToolbox.pdfEdit("merge", …)`。

## 4. ui（L0）

弹层都是阻塞调用：用户确认 / 超时（默认 300 秒）后才返回。

| 接口 | 返回 | 说明 |
|---|---|---|
| `ui.form(title, items, options)` | table / nil | 接收受限 Compose 节点树，返回字段值；取消/超时返回 nil。旧 items 数组保留一个发布周期，已弃用。 |
| `ui.screen(title, root, options)` | table / nil | 阻塞节点树屏幕；按钮返回 `{event=action, values={id=value}}`，取消/超时返回 nil。 |
| `ui.sheet(title, actions, options)` | table / nil | 声明式操作弹层；返回 `{event, values}`，取消或超时返回 nil。actions 字段见 §10。 |
| `ui.confirm(title, message, danger)` | boolean | 二次确认；`danger` 为真时按钮是危险样式。取消 / 超时返回 false。 |
| `ui.progress(ratio, label)` | 无 | 顶部进度条，`ratio` 0.0–1.0，`label` 为空则只更新进度。 |
| `ui.show(card, options)` | 无 | 非阻塞展示节点树；标题取 options.title。兼容旧 `{title, markdown/text/content}`。 |
| `ui.automationStatus(label, detail)` | 无 | 发布当前步骤。应用前台显示为应用内 Toast，应用退后台后切换为悬浮看板；连续重复 `label` 由宿主去重。点击提示卡片可隐藏提示，不会取消脚本。 |
| `ui.isCancelled()` | boolean | 用户在看板点过「停止」后为 true；长脚本在每步之间轮询。 |
| `ui.webSheet(title, url, actions, options)` | table / nil | **宿主专用**：`web.openLogin` 的宿主实现，脚本请调 `web.openLogin`。 |

弹层 `options` 字段全集：

| 字段 | 适用 | 类型 | 说明 |
|---|---|---|---|
| `fullscreen` | form / show / sheet | boolean | 占满屏幕宽高，仍可下滑或返回键关闭。 |
| `landscape` | form / show / sheet | boolean | 弹层期间 Activity 切 `FULL_SENSOR`，关闭后还原。 |
| `height` | form / show / sheet | number | 高度占屏比例 0.1–1.0（`fullscreen=true` 等价 `height=1`）。 |
| `desktop` | web.openLogin | boolean | 按桌面布局渲染：宽视口 + overview + 缩放 + 显式打开 JS/DOM storage。 |
| `user_agent` | web.openLogin | string | 自定义 UA（也接受 `userAgent`）。宿主不内置任何 UA，桌面版页面要脚本自己给不含 `Android`/`Mobile` 的 UA。 |

## 5. web（L0）

Cookie 由宿主用 Keystore 加密、**只存本机**，按 URL 的 host 隔离，不上传服务端，返回值里也不含 Cookie 内容。

| 接口 | 返回 | 说明 |
|---|---|---|
| `web.openLogin(url, title, actions, options)` | string | 打开网页登录弹层并阻塞等待。返回 **JSON 字符串** `{"event":...,"values":{"currentUrl":...}}`，需 `json.decode`；取消返回 `null`。 |
| `web.savedCookie(url)` | string | 读该 host 已保存的 Cookie，没有返回空串。 |
| `web.cookieStatus(url)` | table | `{host, saved, length}`，只给状态与长度。 |
| `web.clearCookie(url)` | boolean | 清除该 host 的已保存 Cookie。 |

## 6. download（L0）

任务跑在 App 级下载服务里，脚本退出后继续，全部出现在全局「下载中心」。

任务 Map 字段：`url`（必填）、`name`（可选，缺省从 URL 推断）、`headers`（可选 map，如 `{Cookie = "..."}`）。保存目录为应用内部 `khatkit/downloads/files/`，完成后 `file` 字段是绝对路径。

| 接口 | 脚本可用 | 返回 | 说明 |
|---|---|---|---|
| `download.start(task)` | 是 | string | 入队并立即返回任务 id。 |
| `download.status(id, waitSeconds)` | 是 | table | `{id,name,state,progress,speed,file,error}`；`waitSeconds>0` 时最多阻塞等待这么久；任务不存在时 `state="missing"`。 |
| `download.pause(id)` | 是 | boolean | 暂停，失败 false。 |
| `download.resume(id)` | 是 | boolean | 恢复，失败 false。 |
| `download.cancel(id)` | 是 | boolean | 取消，失败 false。 |
| `download.remove(id)` | 是 | boolean | 从下载中心删除记录（活动任务先取消）。 |
| `download.list(state)` | 否 | handle[] | 返回句柄对象，脚本无法消费；用 `status(id,0)`。 |
| `download.query(id)` | 否 | handle / nil | 同上。 |
| `download.enqueue(task)` | 否 | handle | 返回句柄对象，仅宿主 Kotlin 使用。 |
| `download.observeTasks()` | 否 | 任务流 | 下载中心任务流，宿主 UI 订阅。 |

任务状态：`queued` `running` `paused` `done` `failed` `cancelled`。

宿主侧句柄对象（脚本拿不到，仅记录在此）：

| 接口 | 返回 | 说明 |
|---|---|---|
| `handle.await(seconds)` | string | 等任务结束，返回 `done` / `running` / `failed`。 |
| `handle.progress()` | number | 0.0–1.0。 |
| `handle.speed()` | number | 字节/秒。 |
| `handle.pause()` | 无 | 暂停。 |
| `handle.resume()` | 无 | 恢复。 |
| `handle.cancel()` | 无 | 取消。 |
| `handle.file()` | string / nil | 完成后的文件路径。 |

## 7. store（L0）

底层 key 自动加 `card_<name>_` 前缀，卡片读不到别人的数据；`store.quota_mb` 约束 `file` + `db` + **SQLite 库**合计大小。

| 接口 | 返回 | 说明 |
|---|---|---|
| `store.kvGet(key, default)` | string / nil | 读 KV（SharedPreferences），缺省返回 `default`。 |
| `store.kvSet(key, value)` | 无 | 写 KV。 |
| `store.fileRead(name)` | string / nil | 读私有文件 `khatkit/store/<name>/files/<name>`，路径逃逸被拒绝。 |
| `store.fileWrite(name, content)` | 无 | 写私有文件；超配额报错。 |
| `store.dbQuery(table, where, args)` | table[] | 查 JSONL 表。`where` 语法 `列 op ? [AND 列 op ?]*`，`op ∈ = != <> > < >= <= like`（`like` 忽略大小写包含）；空 `where` 返回全表。 |
| `store.dbInsert(table, row)` | 无 | 追加一行（JSON 序列化一行）。 |
| `store.secretGet(key)` | string / nil | 读敏感值（Keystore / 系统钥匙串，不进 kv）。 |
| `store.secretSet(key, value)` | 无 | 写敏感值。 |
| `store.sharedWrite(name, content)` | 无 | 写跨卡片共享区（写进自己命名空间）。 |
| `store.sharedRead(name)` | string / nil | 按名读其他卡片的共享数据。 |
| `store.sharedList()` | string[] | 列出共享区条目名。 |
| `store.sharedDelete(name)` | boolean | 删除共享条目。 |
| `store.sql(query, args)` | table[] | 在卡片自己的 SQLite 库里执行一条 SQL，见 7.1。 |
| `store.embedInsert(table, rowId, text)` | boolean | 把 `text` 向量化后写入命名空间 `table`（同一 `rowId` 覆盖）。见 7.2。 |
| `store.embedSearch(table, query, topK)` | table[] | 语义检索，返回 `{rowId, score, text}` 按 score 降序。见 7.2。 |

### 7.1 store.sql（卡片自管的 SQLite）

`store.sql` 让卡片自己管表结构，库文件是 `databases/cards/<卡片名>.db`，与宿主聊天库隔离；
卸载卡片时宿主连库一起删。`store.dbQuery` / `store.dbInsert`（JSONL）保持原样不动，
老卡片不受影响，新表用 `store.sql` 自建。

| 接口 | 返回 | 说明 |
|---|---|---|
| `store.sql(query, args)` | table[] | 执行一条 SQL。`SELECT` / `PRAGMA` / `WITH` / `VALUES` 返回行表（**最多 1000 行**，超出请自己加 `LIMIT`），其余语句返回空表。 |

规则：

- `args` 按 `?` 顺序绑定，支持 number / string / boolean / nil；
- **一次只允许一条语句**（尾随分号可以），注释与字符串字面量里的 `;` 不算；
- **禁止** `ATTACH` / `DETACH` / `VACUUM` / `LOAD_EXTENSION`：卡片只能碰自己的库文件，
  `VACUUM INTO '任意路径'` 这类能往别处写文件的语句同样拒绝；
- 返回值按列类型映射：整数 → number，浮点 → number，文本 → string，`NULL` → nil，
  BLOB → base64 字符串；
- 占用计入 `store.quota_mb`，另有 SQLite 页数硬顶；超限返回中文错误（写入被拒或事务回滚）。

```lua
store.sql("CREATE TABLE IF NOT EXISTS notes(id INTEGER PRIMARY KEY, title TEXT, body TEXT)", {})
store.sql("INSERT INTO notes(title, body) VALUES (?, ?)", { "标题", "正文" })
local rows = store.sql("SELECT id, title FROM notes ORDER BY id DESC LIMIT 10", {})
```

### 7.2 store 向量检索（embedInsert / embedSearch）

卡片自带的小规模向量索引：语义召回笔记、聊天记录、去重。索引存在**同一张卡片库里**
（表名 `__khatkit_embeddings`），检索是**暴力余弦相似度**，规模到几千条就该换方案了。

| 接口 | 返回 | 说明 |
|---|---|---|
| `store.embedInsert(table, rowId, text)` | boolean | 向量化 `text` 并写入；同一 `(table, rowId)` 再次写入会覆盖。 |
| `store.embedSearch(table, query, topK)` | table[] | 返回 `{rowId, score, text}`，`score` 是余弦相似度（越大越像），条数取 `topK`（1–50，默认 5）。 |

```lua
store.embedInsert("notes", "n1", "今天在北京出差，酒店发票明天到期")
store.embedInsert("notes", "n2", "Lua 的 table 是混合类型数组")

local hits = store.embedSearch("notes", "报销要用的凭证", 3)
for _, hit in ipairs(hits) do
  print(string.format("%.3f %s", hit.score, hit.text))
end
```

要点：

- **需要 embedding 模型**：用用户已配置供应商里第一个 `embedding` 类型的模型；卡片不能自带 key，
  也不能指定用哪个模型。没配就返回中文错误。
- **审批与 `ai.chat` 相同**：默认每次询问用户，`permissions.methods["ai.chat"] = "allow"` 后不再打扰，
  `deny` 则直接拒绝——向量调用一样花钱。
- `table` 命名空间只允许字母、数字、下划线（`^[A-Za-z_][A-Za-z0-9_]{0,63}$`）；
  换了 embedding 模型后旧向量维度不同，检索时会被自动跳过（旧数据请自己清库重建）。
- 单次检索最多扫描 2000 条向量；超出请按命名空间分片。

**FTS5**：`host.health().fts5` 告诉你设备平台的 SQLite 是否带 FTS5
（多数 ROM 不带，需要宿主用 `sqlite-android` 替换引擎才有）。为 `false` 时请用普通表 + `LIKE`：

```lua
local health = host.health()
if health.fts5 then
  store.sql("CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts5(body, tokenize='simple')", {})
else
  store.sql("CREATE TABLE IF NOT EXISTS notes_fts(body TEXT)", {})
end
```

## 8. schedule（L0，脚本自建定时任务）

卡片在**运行期**排的定时任务（抓取轮询、延迟重试、提醒自己）。和 manifest 的
`schedule` 事件互相独立：事件是卡片声明的固定计划，调度走宿主精确闹钟；这里是脚本临时排的班。

| 接口 | 返回 | 说明 |
|---|---|---|
| `schedule.every(intervalMinutes, jobId, payloadJson)` | string | 每 N 分钟跑一次（1–10080）；`jobId` 留空自动生成；返回完整 jobId。 |
| `schedule.at(times, days, jobId)` | string | 在 `times`（严格 `HH:mm`）命中时跑；`days` 限定星期（1=周一…7=周日，空 = 每天）；返回完整 jobId。 |
| `schedule.cancel(jobId)` | boolean | 取消本卡片的任务；没命中返回 false。 |
| `schedule.list()` | table[] | 本卡片当前任务，见下。 |

`schedule.list()` 每项：`{ jobId, kind, intervalMinutes, times, days, nextRunAt, createdAt }`
（时间戳为毫秒；`kind` 为 `every` / `at`）。

```lua
local job = schedule.every(10, "refresh", '{"url":"https://api.example.com/feed"}')
schedule.list()   -- { { jobId = "my_card:refresh", kind = "every", ... } }
schedule.cancel(job)
```

要点：

- **jobId 命名空间**：`jobId` 实际是 `<卡片名>:<自定义值>`，跨卡片不会撞车；
  同名任务会覆盖（幂等）。`cancel` 可以传完整值，也可以只传卡片内的自定义值。
- **`payloadJson`** 必须是 JSON 对象，触发时作为卡片的 `args` 注入（`args.url` 这样取）。
- **不保证准点**：调度用 WorkManager 的一次性任务链（跑完再排下一次），系统休眠或省电策略下
  会延迟到下一个维护窗口。需要分钟级准点请用 manifest 的 `schedule` 事件。
- 任务在卡片**卸载**时自动取消；进程被杀不影响后续触发（任务已持久化）。
- 这些任务不受「自动化触发器」总开关约束（那是管事件触发的），但仍走卡片自身的审批与计费链路。

## 9. ui.form 组件白名单

Lua 新卡片使用局部别名构造节点树，子节点放表的数组部分：

```lua
local Column, TextField, Switch, Button = ui.Column, ui.TextField, ui.Switch, ui.Button
local values = ui.form("无障碍点击", Column {
  TextField { id = "text", label = "要点击的文字", required = true },
  Switch { id = "exact", label = "精确匹配", value = false },
  Button { label = "开始", action = "submit", tone = "primary" },
})
if not values then return { cancelled = true } end
```

节点组件：Column、Row、Box、Spacer、Divider、Card、Section、Text、Markdown、Image、
Badge、ProgressBar、TextField、NumberField、Switch、Checkbox、Slider、Select、RadioGroup、
FilePicker、DirPicker、Button。布局接受节点子项，裸字符串提升为 Text；叶子不接受子项。
字段以 `id` 为键，默认值使用 `value`；`required` 空值阻止提交。
Column/Row 支持 spacing；Section 使用 title。Button 使用 label/action/tone/disabled。
Text 的 style 为 title/subtitle/body/label/code，tone 为 default/muted/primary/error；
Badge tone 为 default/primary/success/warning/error；Button tone 为 primary/default/danger。
禁止 raw 颜色、字号、字体和 theme 属性。未知组件/属性、非法 token、重复 id、超过 32 层或
512 节点的树均被拒绝。Lua 和宿主双端校验；JS 暂不注入构造器。

`ui.Modifier` 支持 fillMaxSize/fillMaxWidth/fillMaxHeight、width/height/size(dp)、
padding(n)/padding(h,v)/padding(start,top,end,bottom)、weight(n)、clip("rounded"|"circle",radius?)、
visible(bool)、scrollable()。尺寸为有限非负数字，weight 为正数且仅用于 Row/Column 直接子项。
嵌套滚动容器有界高，避免与弹层滚动冲突。Image 仅接受经过本次卡片文件权限检查的本地路径或可读 content URI。
form 内没有 submit Button 时宿主提供提交底栏。form/screen 默认等待 300s；
等待时间也计入 host.setTimeout 预算，超时返回 nil。

旧语法迁移：input→TextField、number→NumberField、switch→Switch、slider→Slider、
select→Select、radio→RadioGroup、file_picker→FilePicker、dir_picker→DirPicker，
`default` 改为 `value`；text/markdown 的展示内容改为 `text`。
`ui.sheet` 为兼容 API，新交互使用 `ui.screen` + Button。

以下为旧 items 数组兼容词表：

| type | 渲染行为 | 取值 |
|---|---|---|
| `text` / `markdown` | 纯文本（两者相同，不渲染 Markdown 语法） | 不参与返回值 |
| `divider` | 分割线 | 不参与 |
| `input` | 单行输入框 | string |
| `number` | 数字键盘（非法输入保留原字符串） | number / string |
| `switch` | 开关 | boolean |
| `slider` | 滑杆（`min` / `max` / `default` 必填） | number |
| `select` / `radio` | 单选列表（`options` 字符串数组） | string |
| `file_picker` / `dir_picker` | 路径文本框 + 「浏览」按钮：`file_picker` 弹媒体/文档选择器，`dir_picker` 弹目录选择器；不点按钮也能直接手输路径 | string（`multiple` 时为换行分隔的多条路径） |
| `progress` | 只读进度条（取 `ratio`，缺省 0） | 不参与 |
| `button` | 按钮（当前无点击回调） | 不参与 |
| `custom` | 按 `renderer` 找宿主注册的渲染器 | 取决于渲染器 |

## 10. ui.sheet / web.openLogin 的 action 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `event` | string | 点击后回传的事件名；缺省回落读 `id`。 |
| `id` | string | 动作标识。 |
| `label` | string | 按钮文案 / 图标按钮的无障碍描述；缺省回落读 `event`。 |
| `icon` | string | 图标名：`key` / `vpn_key` / `save` / `info` / `delete` / `refresh` / `more` / `close`。认不出来时该动作退回内容区文字按钮。 |
| `placement` | string | `content`（默认，内容区文字按钮）/ `top`（BottomSheet 拖柄所在顶行右侧图标按钮）/ `overflow`（顶行右侧溢出菜单）。`top` 缺 `icon` 时退回 `content`。 |

弹层只要有 `top` 或 `overflow` 动作，就切成「拖柄顶行 + 标题」布局：图标按钮与溢出菜单在拖柄所在顶行右侧，底部只留关闭按钮；这两类动作不会同时出现在内容区。表单弹层（`ui.form`）没有顶栏动作。

## 11. web.openLogin 结果

| 情况 | 返回 |
|---|---|
| 点击 action | `{event, values}`，`values.currentUrl` 为当前页面地址 |
| `save_cookie` 成功 | `{saved:true, host, length}`（只给 host 与长度，不含内容） |
| `save_cookie` 时页面无 Cookie | `{saved:false, host, reason}` |
| `cookie_status` | `{host, saved, length}` |
| `clear_cookie` | `{cleared:true}`；没有可清的返回 `{saved:false, host, reason}` |
| 用户关闭弹层 | `{event:"cancelled", cancelled:true, state:"login_cancelled"}` |
| UI 不可用 | `{error:"UI 不可用", state:"login_unavailable"}` |

## 12. shizuku（L1）

| 接口 | 返回 | 说明 |
|---|---|---|
| `shizuku.setAppEnabled(pkg, enabled)` | string | 启用 / 停用应用（Android 11+ 的 `setApplicationHidden`），返回中文结果。 |
| `shizuku.settingsPut(namespace, key, value)` | string | 写系统设置（`global` / `system` / `secure`），返回中文结果。 |
| `shizuku.pm(action, pkg)` | string | 包管理动作（`install` / `uninstall` / `clear` 等）。 |
| `shizuku.shell(cmd)` | string | 以 shell 身份执行 `/system/bin/sh -c`，返回合并输出并带 `[exit N]` 前缀；失败返回 `[error] 中文说明`。 |

## 13. accessibility（L1）

节点查询统一用 map：`text` / `desc` / `viewId` / `className` / `clickable` / `exact`；返回的节点字段含 `depth` / `bounds` / `text` / `desc`。

| 接口 | 返回 | 说明 |
|---|---|---|
| `accessibility.isAvailable()` | boolean | 服务是否在线。 |
| `accessibility.currentPackage()` | string / nil | 当前前台包名。 |
| `accessibility.dumpWindow()` | table[] | 当前窗口全部节点（扁平化，含 depth/bounds）。 |
| `accessibility.findNodes(query)` | table[] | 按条件查找节点。 |
| `accessibility.click(query)` | boolean | 点击第一个匹配节点（不可点击时向上找可点击祖先）。 |
| `accessibility.longClick(query)` | boolean | 长按第一个匹配节点。 |
| `accessibility.setText(query, text)` | boolean | 给第一个可编辑的匹配节点设置文本。 |
| `accessibility.tap(x, y)` | boolean | 坐标点击。 |
| `accessibility.swipe(x1, y1, x2, y2, durationMs)` | boolean | 坐标滑动。 |
| `accessibility.press(x, y, durationMs)` | boolean | 坐标长按（100–10000 ms）。 |
| `accessibility.scroll(direction)` | boolean | 滚动窗口：`forward` / `backward` / `up` / `down` / `left` / `right`。 |
| `accessibility.globalAction(action)` | boolean | 全局动作：`back` / `home` / `recents` / `notifications`。 |
| `accessibility.openApp(packageName)` | boolean | 启动应用主界面。 |
| `accessibility.waitForNode(query, timeoutMs)` | table / nil | 轮询等待首个匹配节点出现。 |
| `accessibility.waitForIdle(timeoutMs)` | boolean | 等窗口内容稳定（连续两次节点摘要一致）。 |
| `accessibility.waitForPackage(packageName, timeoutMs)` | boolean | 等指定包成为前台。 |
| `accessibility.gesture(strokesJson)` | boolean | 多段手势。`strokesJson` 是 JSON 数组 `[[{"x":1,"y":2,"t":0},{"x":3,"y":4,"t":500}], …]`，`t` 为段内毫秒偏移。 |
| `accessibility.captureScreen(outputPath)` | string | 截屏存 PNG，返回路径；API 30 以下或失败返回中文错误文本。 |
| `accessibility.findImage(templatePath, threshold)` | string | 模板匹配（多尺度灰度归一化互相关）。返回 **JSON 字符串**：命中 `{"found":true,"x":..,"y":..,"score":..}`，未命中 `{"found":false}`，失败 `{"error":"..."}`；一律先 `json.decode`。 |
| `accessibility.tapImage(templatePath, threshold, timeoutMs)` | boolean | 找模板并点中心；`timeoutMs>0` 时每 300ms 轮询。 |
| `accessibility.findColor(colorHex, tolerance, region)` | string | 找颜色。返回 **JSON 字符串**：命中 `{"found":true,"x":..,"y":..,"color":"#RRGGBB"}`，未命中 `{"found":false}`；`region` 为 `""` 或 `"x,y,w,h"`。 |
| `accessibility.paste()` | boolean | 对当前聚焦的输入框粘贴。 |
| `accessibility.addOverlay(view, params)` | boolean | **宿主专用**：用 WindowManager 加悬浮窗。 |
| `accessibility.removeOverlay(view)` | 无 | **宿主专用**：移除上面加的悬浮窗。 |

## 14. root（L2）

| 接口 | 返回 | 说明 |
|---|---|---|
| `root.shell(cmd)` | string | 以 root 执行 `/system/bin/sh -c`，返回合并输出并带 `[exit N]` 前缀；未启用 root 时返回中文错误。 |

## 15. imageToolbox（L0，随依赖包注入）

参数名与行为见 [dependency-system.md](dependency-system.md)；纯 Android SDK 实现，不联网、不上传。

| 接口 | 返回 | 说明 |
|---|---|---|
| `imageToolbox.resize(path, width, height, keepAspect)` | string | 缩放；宽高任一为 0 表示按该边自适应，`keepAspect` 保持比例。 |
| `imageToolbox.crop(path, x, y, width, height)` | string | 裁剪（必须落在图片范围内）。 |
| `imageToolbox.rotate(path, degrees)` | string | 旋转（逆时针度数）。 |
| `imageToolbox.flip(path, horizontal)` | string | 翻转，`horizontal` 为假时上下翻。 |
| `imageToolbox.grayscale(path)` | string | 灰度。 |
| `imageToolbox.blur(path, radius)` | string | 盒式模糊（半径像素）。 |
| `imageToolbox.sharpen(path, amount)` | string | USM 锐化。 |
| `imageToolbox.pixelate(path, blockSize)` | string | 马赛克。 |
| `imageToolbox.brightnessContrast(path, brightness, contrast)` | string | 亮度 / 对比度（-255–255）。 |
| `imageToolbox.saturation(path, factor)` | string | 饱和度（0 灰度，1 原样）。 |
| `imageToolbox.hue(path, degrees)` | string | 色相旋转。 |
| `imageToolbox.autoContrast(path)` | string | 自动对比度。 |
| `imageToolbox.invert(path)` | string | 反色。 |
| `imageToolbox.sepia(path)` | string | 复古色调。 |
| `imageToolbox.watermark(path, text, position, alpha, textSize, colorHex)` | string | 文字水印；`position ∈ top-left/top-right/bottom-left/bottom-right/center`，`textSize=0` 自适应，`colorHex` 如 `#FFFFFF`。 |
| `imageToolbox.border(path, width, colorHex)` | string | 纯色边框，单边像素宽。 |
| `imageToolbox.roundCorners(path, radius)` | string | 圆角（半径上限为短边一半）。 |
| `imageToolbox.convert(path, format, quality)` | string | 格式转换：`png` / `jpg` / `webp`，`quality` 1–100。 |
| `imageToolbox.stripMetadata(path)` | string | 重编码丢弃 EXIF 等元数据。 |
| `imageToolbox.imagesToPdf(paths, output)` | string | 多图合成 A4 竖版 PDF。 |
| `imageToolbox.pdfToImages(path, outputDir)` | string[] | PDF 逐页渲染为 PNG，返回路径列表。 |
| `imageToolbox.pdfPageCount(path)` | number | PDF 页数。 |
| `imageToolbox.process(path, op, paramsJson)` | string | 单图处理（滤镜/预设/效果/几何等），参数为 JSON 对象。 |
| `imageToolbox.compose(op, inputsJson, paramsJson)` | string | 多图合成（`inputsJson` 为输入路径数组）。 |
| `imageToolbox.analyze(path, query, paramsJson)` | string | 只读分析，返回 JSON，不写文件。 |
| `imageToolbox.pdfEdit(op, source, paramsJson)` | string | PDF 编辑：`rotate` / `reorder` / `extract` / `delete` / `nup` / `compress` / `merge`。 |

## 16. 跨引擎约定

- 桥方法返回 Kotlin `String` / `Map` / `List` 时会 JSON 化后再进脚本：返回 JSON 对象的接口在 Lua 里是 table，返回 JSON 字符串的接口拿到的是 string（需要自己解析）。
- 返回 `Map` / `List` 的接口在 Lua 里是 table，字段名即键名；标量接口直接给标量。
- 抛异常的方法在脚本侧收到 `{"__error":"中文说明"}`（或引擎约定的错误对象），所以要用 `pcall` / 返回值判空兜住。
- 宿主对象（`DownloadHandle`、Android `View`）跨引擎只会变成无意义字符串，脚本不要用返回这类对象的方法。
