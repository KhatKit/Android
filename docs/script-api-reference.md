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
| `ui` | `UiBridge` | L0 | 始终 | 弹层、表单、结果卡片、进度、看板。 |
| `web` | `WebBridge` | L0 | 始终 | 网页登录弹层 + 按 host 隔离的本机 Cookie。 |
| `download` | `DownloadBridge` | L0 | 始终 | 后台下载（脚本退出后继续）。 |
| `store` | `StoreBridge` | L0 | 始终 | 卡片隔离的 kv / file / db / secret + 跨卡片共享区。 |
| `imageToolbox` | `ImageToolboxBridge` | L0 | 依赖包已下载 | 本地图像工具箱，随 `requires.dependencies` 下发的 dex 包注入，见 [dependency-system.md](dependency-system.md)。 |
| `shizuku` | `ShizukuBridge` | L1 | Shizuku 在线 | 动作级 API + `shell`。 |
| `accessibility` | `AccessibilityBridge` | L1 | 无障碍服务开启 | 找节点 / 点击 / 手势 / 识图。 |
| `root` | `RootBridge` | L2 | 设置里开启 root | 只有 `shell`。 |

未声明的 bridge 不会注入，对应全局名不存在；声明了但设备不具备的，卡片不可见 / 运行时报 `BRIDGE_UNAVAILABLE`。
依赖包注入的动态 bridge（名字由卡片自定）不在本文件里，字段与生命周期见 [dependency-system.md](dependency-system.md)。

## 3. tool（L0）

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
| `tool.listFiles(path)` | string | 列目录，返回 JSON 数组：`{name, path, is_dir, size, modified}`。 |
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
| `tool.ocrBoxes(path)` | string | OCR 带坐标，返回 JSON 数组 `[{text,x,y,w,h}]`（像素坐标，已按 EXIF 校正）。 |

> PDF 能力已剥离到依赖包：`mergePdf` 不再提供，改用 `imageToolbox.pdfEdit("merge", …)`。

## 4. ui（L0）

弹层都是阻塞调用：用户确认 / 超时（默认 300 秒）后才返回。

| 接口 | 返回 | 说明 |
|---|---|---|
| `ui.form(title, items, options)` | table / nil | 声明式表单；返回用户填的值，取消或超时返回 nil。items 组件白名单见 §7。 |
| `ui.sheet(title, actions, options)` | table / nil | 声明式操作弹层；返回 `{event, values}`，取消或超时返回 nil。actions 字段见 §8。 |
| `ui.confirm(title, message, danger)` | boolean | 二次确认；`danger` 为真时按钮是危险样式。取消 / 超时返回 false。 |
| `ui.progress(ratio, label)` | 无 | 顶部进度条，`ratio` 0.0–1.0，`label` 为空则只更新进度。 |
| `ui.show(card, options)` | 无 | 结果卡片，优先渲染 `markdown` / `text` / `content`，标题取 `title`；不阻塞。 |
| `ui.automationStatus(label, detail)` | 无 | 发布当前步骤到悬浮看板；连续重复 `label` 由宿主去重。 |
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
| `web.openLogin(url, title, actions, options)` | table | 打开网页登录弹层并阻塞等待。返回 `{event, values}`：`event` 为脚本声明的事件名，`values.currentUrl` 是点击时的当前页面地址。失败 / 取消见 §9。 |
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

底层 key 自动加 `card_<name>_` 前缀，卡片读不到别人的数据；`store.quota_mb` 约束 `file` + `db` 合计大小。

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

## 8. ui.form 组件白名单

| type | 渲染行为 | 取值 |
|---|---|---|
| `text` / `markdown` | 纯文本（两者相同，不渲染 Markdown 语法） | 不参与返回值 |
| `divider` | 分割线 | 不参与 |
| `input` | 单行输入框 | string |
| `number` | 数字键盘（非法输入保留原字符串） | number / string |
| `switch` | 开关 | boolean |
| `slider` | 滑杆（`min` / `max` / `default` 必填） | number |
| `select` / `radio` | 单选列表（`options` 字符串数组） | string |
| `file_picker` / `dir_picker` | 路径文本输入框（不弹系统选择器） | string |
| `progress` | 只读进度条（取 `ratio`，缺省 0） | 不参与 |
| `button` | 按钮（当前无点击回调） | 不参与 |
| `custom` | 按 `renderer` 找宿主注册的渲染器 | 取决于渲染器 |

## 9. ui.sheet / web.openLogin 的 action 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `event` | string | 点击后回传的事件名；缺省回落读 `id`。 |
| `id` | string | 动作标识。 |
| `label` | string | 按钮文案 / 图标按钮的无障碍描述；缺省回落读 `event`。 |
| `icon` | string | 图标名：`key` / `vpn_key` / `save` / `info` / `delete` / `refresh` / `more` / `close`。认不出来时该动作退回内容区文字按钮。 |
| `placement` | string | `content`（默认，内容区文字按钮）/ `top`（弹层右上角图标按钮）/ `overflow`（右上角溢出菜单）。`top` 缺 `icon` 时退回 `content`。 |

弹层只要有 `top` 或 `overflow` 动作，就切成「标题在上」布局：图标按钮与溢出菜单在标题右侧，底部只留关闭按钮；这两类动作不会同时出现在内容区。表单弹层（`ui.form`）没有顶栏动作。

## 10. web.openLogin 结果

| 情况 | 返回 |
|---|---|
| 点击 action | `{event, values}`，`values.currentUrl` 为当前页面地址 |
| `save_cookie` 成功 | `{saved:true, host, length}`（只给 host 与长度，不含内容） |
| `save_cookie` 时页面无 Cookie | `{saved:false, host, reason}` |
| `cookie_status` | `{host, saved, length}` |
| `clear_cookie` | `{cleared:true}`；没有可清的返回 `{saved:false, host, reason}` |
| 用户关闭弹层 | `{event:"cancelled", cancelled:true, state:"login_cancelled"}` |
| UI 不可用 | `{error:"UI 不可用", state:"login_unavailable"}` |

## 11. shizuku（L1）

| 接口 | 返回 | 说明 |
|---|---|---|
| `shizuku.setAppEnabled(pkg, enabled)` | string | 启用 / 停用应用（Android 11+ 的 `setApplicationHidden`），返回中文结果。 |
| `shizuku.settingsPut(namespace, key, value)` | string | 写系统设置（`global` / `system` / `secure`），返回中文结果。 |
| `shizuku.pm(action, pkg)` | string | 包管理动作（`install` / `uninstall` / `clear` 等）。 |
| `shizuku.shell(cmd)` | string | 以 shell 身份执行 `/system/bin/sh -c`，返回合并输出并带 `[exit N]` 前缀；失败返回 `[error] 中文说明`。 |

## 12. accessibility（L1）

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
| `accessibility.findImage(templatePath, threshold)` | table | 模板匹配（多尺度灰度归一化互相关）。命中 `{found:true,x,y,score}`，未命中 `{found:false}`。 |
| `accessibility.tapImage(templatePath, threshold, timeoutMs)` | boolean | 找模板并点中心；`timeoutMs>0` 时每 300ms 轮询。 |
| `accessibility.findColor(colorHex, tolerance, region)` | table | 找颜色。命中 `{found:true,x,y,color}`，未命中 `{found:false}`；`region` 为 `""` 或 `"x,y,w,h"`。 |
| `accessibility.paste()` | boolean | 对当前聚焦的输入框粘贴。 |
| `accessibility.addOverlay(view, params)` | boolean | **宿主专用**：用 WindowManager 加悬浮窗。 |
| `accessibility.removeOverlay(view)` | 无 | **宿主专用**：移除上面加的悬浮窗。 |

## 13. root（L2）

| 接口 | 返回 | 说明 |
|---|---|---|
| `root.shell(cmd)` | string | 以 root 执行 `/system/bin/sh -c`，返回合并输出并带 `[exit N]` 前缀；未启用 root 时返回中文错误。 |

## 14. imageToolbox（L0，随依赖包注入）

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

## 15. 跨引擎约定

- 桥方法返回 Kotlin `String` / `Map` / `List` 时会 JSON 化后再进脚本：返回 JSON 对象的接口在 Lua 里是 table，返回 JSON 字符串的接口拿到的是 string（需要自己解析）。
- 返回 `Map` / `List` 的接口在 Lua 里是 table，字段名即键名；标量接口直接给标量。
- 抛异常的方法在脚本侧收到 `{"__error":"中文说明"}`（或引擎约定的错误对象），所以要用 `pcall` / 返回值判空兜住。
- 宿主对象（`DownloadHandle`、Android `View`）跨引擎只会变成无意义字符串，脚本不要用返回这类对象的方法。