# 更新日志

## 未发布（自动化 Toast 与图片选择器迁移）

- 探索市场精选轮播卡（`FeaturedCard`）背景从一块平色海报换成**与关于页同款的动态流光**：复用关于页那套 OS3 着色器（`BgEffectPainter` / `BgEffectConfig`）与同一套换色弹簧（`dampingRatio 0.9` / `stiffness 35`），底色仍是原来的 `posterTone`，因此「同一张卡在哪都是同一个色」不变。配色不是「身份色的四档压暗」（四团同色，看起来还是一块平雾），而是**每档光点各按不同配比混合身份色（取色）× 当前主题色 `colorScheme.primary`**，三套配比每 4.5s 循环一次 —— 同一帧里四团光色相彼此不同（配比 0.12 → 0.82），整卡在「偏取色 ↔ 偏主题色」之间缓慢换气；换主题或开动态取色时这张卡的流光跟着整页一起换（`cardFlowingPalette`）。每个光点单独按「以它的 alpha 叠在这张卡的海报底上之后的白字对比度」反解亮度上限（`capAgainstPoster`，只同比例压亮度、色相饱和度不动）。离线复刻同一份着色器实测：8 个身份色 × 6 个主题色（MD3 默认浅/深 + 4 个动态取色）× 3 套配色 × 16 个时刻，纯白字最低 **4.71:1**（改版前 4.77:1，可读性没退步），卡面亮度空间标准差 0.0093 → **0.0346**（≈3.7×，这就是「原来太平」的量化口径）。固定吃**深色预设**（`lightOffset = 0`）：浅色预设那个 +0.1 会把白字压到 2:1。API < 33 无 `RuntimeShader` 时这一层不画，退化成原来的纯色海报卡。同时按需求去掉右上角那块有机形背景、徽标从「跟着身份形变」统一成**圆形**。✅ 实机验证：PKG110 上 `image_compare` 轮播卡呈现身份色 × 蓝色主题色的紫蓝流光，四帧内可见光斑位置与色相变化；相邻卡（身份色偏暖）流光为暖橙，与「同卡同色」一致。
- Miuix 筛选 chip 收敛成「只有选中那枚带勾」：`KedgeFilterChip` 的 Miuix 分支此前**每枚**都挂一个 `MiuixCheckbox`（未选中的画空框），现在只在 `selected` 时组合它；未选中退化成「素胶囊 + 文本」，与 MD3 侧 `FilterChip`（本来就只有选中才画勾 / 才变色）行为对齐。同时选中底色从 `primaryContainer` 改成 **`primary`**（= `CheckboxDefaults` 里 `checkedBackgroundColor` 的值，勾用 `onPrimary`），文字色同步用 `onPrimary` —— 勾与胶囊同一块蓝，不再出现「勾一个蓝、胶囊另一个蓝」两档色（Kedge `components/KedgeBadges.kt:161` 选中色 / `:185` 勾选框挂载条件）。影响面是全库调用点（聊天抽屉 / 探索市场 / 助手与技能筛选 / 触发器编辑等 20+ 处 Miuix chip），MD3Exp 分支一处未动。✅ 实机验证：探索市场 chip 轨道「全部 / 卡片 / 技能合集 / MCP 服务」与聊天抽屉「全部 / 单聊 / 群聊」中，选中项为蓝色胶囊 + 白勾、其余为无勾深色胶囊；切换筛选后勾随选中项迁移（`app-arm64-v8a-debug.apk`，PKG110）。
- 「本地」页的原生依赖包分区改为列出**已安装卡片声明的依赖 ∪ 本机已缓存的 jar**：此前只报已缓存的产物，而依赖包要到运行卡片时才下载，刚装完卡片缓存为空 ⇒ 整个分区都不显示，用户在本机页看不到任何依赖痕迹。未下载的行标注「未下载 · 运行卡片时自动获取」且不给删除入口；已缓存的行照旧报大小 / SHA-256，可单条删除、可清空。市场页维持「只报本机已缓存了什么」。
- 新增受卡片 `network.allow` 白名单约束的 `net` bridge；旧 `tool.http*` 接口保留兼容并标记废弃。
- `fs.zip` / `fs.unzip` 落地（原实现直接抛「尚未迁移」，与 `tool.zip/unzip` 标废弃矛盾）：入参与输出都过沙箱，`unzip` 逐条校验条目不越出目标目录（防 zip slip）。
- `tool.*` 的 12 个文件方法（`readText` / `writeText` / `listFiles` / `copyPath` / `deletePath` / `mkdir` / `renamePath` / `zip` / `unzip` / `readBase64` / `saveBase64` / `openDir`）标记废弃，请改用 `fs`。**旧方法行为不变**：仍按传入绝对路径操作、只受「所有文件访问」约束；因此 `tool.deletePath` 不向用户确认、`fs.delete` 每次确认，新卡片请直接用 `fs`。
- 修复若干只会在特定环境暴露的问题：语言切换后 Toast/月份名停在旧语言（composable 里用 `LocalContext` 取文案）、流光背景在 Android 13 以下会崩（`RuntimeShader` 无版本守卫）、自动化通知与蓝牙/定位调用缺显式权限检查。
- 卡片能力新增 `fs`（沙箱文件）、`json`、`crypto`、`time`、`host`、`system`、`ai` 七个 bridge，接口见 docs/script-api-reference.md；其中 `system.vibrate` / `system.connectivity` 需要宿主声明 `VIBRATE` / `ACCESS_NETWORK_STATE` 权限（已随卡片库清单合并进应用）。
- `mediaPicker.pickMedia` 接入应用内媒体选择器（与聊天附件同一套相册 / 多选 / 预览），选中文件复制进应用缓存后回传真实路径。
- `ai.chat` / `ai.complete` 接入宿主模型：使用用户已配置的供应商（卡片不能自带 key），默认每次调用都要用户授权，卡片声明 `permissions.methods["ai.chat"] = "allow"` 后免打扰；非流式，超时按 `timeoutSeconds` 与卡片运行剩余预算取小。
- `ui.form` 的 `file_picker` / `dir_picker` 落地真实选择器：媒体走应用内媒体选择器，其余走系统文件选择器，目录走系统目录选择器（tree URI 还原为绝对路径）；不点「浏览」仍是纯文本输入，旧卡片行为不变。
- 自动化提示前台显示应用内 Toast，后台切换为悬浮看板，共用 `AutomationBus` 状态。
- 应用内和悬浮提示支持点击隐藏；点击隐藏不取消正在运行的卡片，停止任务仍使用看板停止操作。
- Toast 进入/退出动画按 ImageToolbox/Khromia 规范对齐：淡入、底部中心弹性缩放、半高滑入，以及对应退出动画。
- 文档记录 ImageToolbox 完整媒体选择器迁移边界；聊天附件当前仍使用系统 Picker，完整媒体网格、相册、搜索、多选和预览迁移尚未完成。
- `store.sql` 落地：卡片可用 SQLite 自管表结构（每张卡片一个 `databases/cards/<卡片名>.db`，与宿主聊天库隔离），支持运行期 `CREATE TABLE`、参数绑定、FTS5 能力位（`host.health().fts5`，不支持时用 `LIKE`）；禁止 `ATTACH` / `VACUUM` 等跨库跨文件语句，占用计入 `store.quota_mb`。旧 `dbQuery` / `dbInsert` 保持不变。
- 新增卡片向量检索：`store.embedInsert` / `store.embedSearch`（每张卡片一个索引，与 `store.sql` 同库，暴力余弦相似度），使用用户已配置的 embedding 模型，审批策略与 `ai.chat` 一致。
- 新增 `schedule` bridge：卡片脚本可自建定时任务（`every` / `at` / `cancel` / `list`），jobId 按卡片隔离、卸载自动取消；调度走 WorkManager 一次性任务链，不保证准点（准点请用 manifest 的 schedule 事件）。
- 卡片运行超时真正生效：`host.setTimeout(ms)` 改写本轮运行的 deadline，`RustBridgeDispatcher` 在每次 dispatch 前比对，超时后所有 bridge 调用返回「卡片运行超时，已终止」且 `ui.isCancelled()` 变为 true（并发运行互不干扰）。
- `permissions.methods` 变成运行时强约束：`allow` 的方法不再弹审批框，`deny` 的方法调用前直接失败（此前只有 `ai.chat` / `store.embed*` / `fs.delete` / `system.launchApp` 认这个声明）。
- 修正文档三处返回类型标错：`accessibility.findImage` / `findColor` 与 `web.openLogin` 实际返回 **JSON 字符串**（此前标成 table），`tool.ocrBoxes` / `tool.listFiles` 也补上「需 `json.decode`」标注，示例改用 `json.decode` 取字段。
- 文档对齐实际实现：事件触发按 `CardManifest.ALL_EVENT_TYPES` 的 18 种对齐（补 notification_click / notification_reply / app_exit / app_install / app_uninstall / shortcut / tile），依赖包文档补「现状与边界」（撤销后不自动清理、依赖不能声明依赖与权限、非 imageToolbox 依赖只有通用调用面）。

### 🔄 同步上游 RikkaHub 2.5.6（2026-10-03 批次，此前漏记账）

> 该批对照上游 43 个提交：23 条此前已逐字移植、1 条（mediagen 模块）判定不适用，其余为接线与碎 fix。
> 下面只列用户可感知的部分。

- 图表工具 `chart_display` 真正可用：工具定义与图表组件早已在仓库里，但从未注册进助手的可用工具表，模型永远调不到；现补上工具项、`getTools()` 分支与助手设置页开关。
- 内置技能接线：随 APK 发布的 `skill-creator` 此前是死代码（`extractIfNeeded()` 无人调用）。现启动时解压到应用私有目录并挂载到工作区 `/builtin_skills`（**只读**，同名用户技能优先），工作区提示词同步补上只读说明。
- 复制助手时可一并复制它的记忆（助手页新增勾选项；使用全局记忆的助手不受影响）。
- 技能页支持按**名称或描述**搜索。
- 提供商设置新增「高级设置」页与**自定义请求头**（OpenAI / Google / Claude 三家生效），Miuix 风格的提供商详情页同步支持。
- 设置读取更健壮：DataStore 文件损坏时自动备份损坏文件并重置（不再启动即崩），原有本地迁移链（V1–V5）完整保留。
- 新增 Google Gemini Interactions API 支持（可在提供商设置里切换）。
- 调试页新增「聊天记录引用的助手」检查与「恢复缺失的助手」：可从历史会话里找回被删掉的助手。
- 技能文件保存改为临时文件 + 原子替换（此前裸写失败会直接崩溃）；GitHub 技能下载改为按字节下载（修复大文件与限流场景）。
- 修复 MCP 请求头留空导致 OkHttp 抛 `name is empty`；OpenAI 图片生成 `size=auto` 不再下发，`x.ai` 判定改为 host 精确匹配（`xxx-max.ai` 之类中转域名不再被误判）。
- 修复文件编辑器把全文塞进 saved state、大文件切后台触发 `TransactionTooLargeException`。
- 更新混元 baseUrl 为 `tokenhub.tencentmaas.com/v1`，图标匹配覆盖 `hy` 前缀。
- 输入框玻璃效果 tint 透明度 0.72 → 0.3；会话列表方向箭头按 RTL 镜像。

### 🐛 界面缺陷修复（KhatKit / Kedge / Khromia）

- 修复 Miuix 风格聊天页顶栏标题点击无响应：`KedgeMiuixMorphingTitleBar` 新增标题点击 / 长按命中层（含**折叠态小标题那一行**），并把重命名弹窗提到 Miuix 早退分支**之前**（此前 Miuix 下该弹窗永不参与组合）；两风格行为对齐 —— 点标题开模型选择 sheet、长按改标题，空会话只弹提示。
- 修复侧边栏「新建文件夹」组与顶栏零间距：文件夹组顶部补 8dp，与抽屉既有 8dp 节奏一致（两种风格共用同一条 modifier）。
- 修复 Miuix 风格聊天底部输入框无光标：显式给 Miuix 文本框 `cursorBrush`（它的默认值取 `colors.borderColor`，而该处边框被设为透明 ⇒ 光标解析成 Transparent）。
- 对齐 KodeHeap 帖子详情页 FLB：输入框上滑拉起全屏编辑器的落定改为 `tween(760ms × 行程, CubicBezierEasing(0.16, 0.82, 0.18, 1))`（原为弹簧），松手提交阈值 0.35、过冲上限 1.12、圆角从胶囊圆角 morph 到屏幕物理圆角；编辑器改为挂 decorView 的同 window 页内浮层，并新增 `PageBackInterceptor` 保证**系统返回只关编辑器、不退出聊天页**。
- 修复自动化提示被裁切：软件内看板从自建 `Popup`（WRAP_CONTENT 窗口硬裁入场动画）改为挂 decorView 的全屏宿主；软件外悬浮窗（不能做成全屏，否则 Android 12+ 触摸遮挡）把入场 / 退场滑位移 clamp 到卡片下方的窗口内留白；两条路径统一走 Khromia 新增的 `ToastCardContainer` / `ToastDefaults`，不再各自硬编码卡片规格。
- Khromia toast 按上游 ImageToolbox 规格补齐：普通 toast 时长改为 `Short 3500ms` / `Medium 5000ms` / `Long 6500ms`（**此前是 1200ms**，且全仓无调用点显式传时长 ⇒ 属**全局行为变化**）；图标改为 `IconShapeContainer` 底片（squircle + 4dp 内边距 + 24dp 图标 + 0.65dp 阴影）之后再 8dp 间距；新增 `Color.blend` 复用上游混色链。
- 修复 Miuix 底部弹层内容左右内缩过大：Miuix `WindowBottomSheet` 的横向 `insideMargin` 库默认是 24dp，现改为与各自 MD3 容器对齐 —— `KedgeModalBottomSheet` 缺省 16dp（= MD3 `KedgeCard` 默认内边距）并支持按调用点覆盖，`KedgePrimaryBottomSheet` 恒 0（= Khromia `PrimaryBottomSheet`），`GreetingPage` 显式传 0 与 MD3 齐平；纵向链与拖拽把手 / 标题居中不受影响。
- 移除 MD3 关于页的动态流光背景（流光是 Miuix 风格的观感，MD3 页底色改为不透明的 `colorScheme.background`），Miuix 关于页仍保留流光。
- ⚠️ 以上 7 项的**视觉 / 功能验证仍待用户实机确认**（原集成未做真机测试）。已实现施工图已清理，检查路径、命中边界风险及历史证据追溯见 [UI 验收与观察记录](docs/ui-verification-followups.md#1-七项-ui-修复的实机检查)。

### 🖐 MD3 overscroll 回弹恢复（2026-10-14 收口）

- 恢复 MD3 风格的默认 overscroll 回弹：`KhatKitTheme` 原先全局 `LocalOverscrollFactory provides null`（由提交 `d257ef842` 引入）会把整棵子树的回弹效果一并关掉；现改为**只在 Miuix 风格下提供 `null`**，MD3 侧交回 `LocalOverscrollFactory.current`，即平台默认（Android 12+ 拉伸回弹、更低版本 glow）。Miuix 行为逐字不变（仍由 Kedge `MiuixMaterialBridge.kt:137` 与调用点自己的 `overScrollVertical()` 负责）。⚠️ 本条的视觉验证由用户完成。

### 🔧 技能安装与调试页（2026-10-14 收口）

- 技能安装改为**按字节下载**：此前每个附属文件都按 UTF-8 解码成文本再保存，图片等二进制文件会被破坏（从 GitHub 装带图的技能，装完就是坏的）；现在 `SKILL.md` 与其余附属文件都走字节通道，`SkillManager` 只保留字节版原子写入（旧的文本版 `saveSkillFilesAtomically` 已删）。
- GitHub 技能下载遇到限流会给出明确提示：未登录的 GitHub API 每小时只有 60 次配额，返回 403 / 429 且 `X-RateLimit-Remaining: 0` 时提示「GitHub API 请求次数已达上限，请稍后再试」，不再笼统报「读取失败」。
- 调试页删掉空白第 4 个 tab：原先 4 个 tab 中的「Logging」页体是空的（上游 2.5.6 起就是空实现，日志查看器已被移除），现只剩 **Main / Colors / Recovery** 三个，页数与标题数一致；本地日志入口（设置里的日志页）不受影响。⚠️ **限定**：当前 UI 精简后调试页**没有入口**（全仓零 `navigate(Screen.Debug)` 调用，入口随 commit `48c2c0915` 摘掉），这条改动目前**只在代码层生效**、无用户可达路径，待入口恢复后自然对用户可见；本条的验收口径是代码级 + 评审级（`t26 verdict=pass`），**未做实机验证**。
- 搜索弹层补回系统返回语义：在搜索弹层里点进**供应商子页**后按系统返回，先退回列表、再按一次才关闭弹层；此前该子页不拦返回，手感与预期不符。⚠️ **限定**：搜索弹层的唯一公开入口 `SearchPickerButton` 当前**零调用者**（同一提交摘掉），这条改动同样**只在代码层生效**、无用户可达路径，待入口恢复后自然对用户可见；本条的验收口径是代码级 + 评审级（`t26 verdict=pass`），**未做实机验证**。

### ⚙️ 设置页留白 / 更新通道按 ABI 分发 / 图标中性单色（2026-10-14 收口）

- MD3 设置页列表顶部留白 8dp → **24dp**（与底部同值）：14 个设置页改用共享常量 `PageMetrics.TopContentPadding`（= `8.dp + BottomContentPadding`，且必须在 `BottomContentPadding` 之后声明，否则读到 `0.dp`）。此前顶部只写 `top = 8.dp`、底部却叠了 16dp 加码，列表首项明显比末项紧。Miuix 页面**一处未动**（`fff12c26b`，15 文件 / +36 −15）。⚠️ 留白观感由用户实机确认。
- 应用更新改为**按 ABI + 包名**分发：客户端上报 `abi=Build.SUPPORTED_ABIS.firstOrNull()` 与 `applicationId=BuildConfig.APPLICATION_ID`，两者为空串时**不追加参数** ⇒ 未上报时请求 URL 与旧实现逐字节相同（`core/util/UpdateChecker.kt` 抽出 `buildUpdateUrl` + `UpdateCheckerUrlTest` 7 例）。服务端（KodeHeapServer `325f2ed`，契约见本仓 `README.md`「应用更新接口」）按 `downloadUrls[<同名 ABI>]` → `universal` → 该版本原有 `downloadUrl` 选产物，**绝不把别的 ABI 发出去**；声明了 `applicationId` 的版本只对**同包名**客户端下发，否则 `packageMismatch=true`、`hasUpdate=false`、`downloadUrl=""`（`force_min` 也不强制），debug 包不会再被推 release 包。发布侧 fail-closed：`downloadUrls` 出现非法 key、缺 `universal` 兜底或任一地址为空即整次发布 400 拒收、写库前抛出（`c8e73ab5c`，3 文件）。
- AI 提供商图标统一为**中性单色**：底色 `MaterialTheme.colorScheme.secondaryContainer`、图形 `onSecondaryContainer`（`AIIcon.kt:37` / `:41`），并在渲染层对**每个**资产无条件做 `ColorFilter.tint(…, SrcIn)`（`AIIcon.kt:64`）——SrcIn 只保留形状 alpha、把全部 RGB 换成图形色，所以资产内部的逐元素 `fill`、内联 `style=`、`<style>` 块与 `stroke` 都漏不出第二色（这不是「资产碰巧单色」，老写法 `svg { fill: … }` 只命中根元素、会被子元素反超）。资产仍用 lobe-icons 单色版，署名与许可见 `app/src/main/assets/icons/LICENSES.md`。此前「按提供商取品牌色 + 推导对比色」的机制已**整段删除**（`AIIconMatcher.kt` 147 → 127 行），不是未接线。回归护栏：`AIIconAssetsTest` 目录全量扫单色（XML 解析覆盖 `fill` / `stroke` / `style=` / `<style>`，放行 `currentColor` / `none` / `inherit`）+ 一条渲染层源码护栏（必须无条件 tint）。⚠️ 图标观感由用户实机确认（`53c1187d7`，83 文件）。

### 🎛️ 设置项两风格统一（2026-10-14 收口）

> 用户裁定「并集补齐、两侧条目完全一致」，本批把 MD3 与 Miuix 的设置项对齐；验收规则、遗留与历史追溯见
> [UI 验收与观察记录](docs/ui-verification-followups.md#2-设置两风格对齐后的检查)。
> ⚠️ **真机观感未验证**：以下各条的视觉 / 交互观感留给用户实机确认。

- **外观三选**（跟随系统 / 浅色 / 深色）在 Miuix 主页补齐：此前 MD3 有、Miuix 没有；两侧用同一 `ColorMode` 枚举与同一默认值。
- **主题入口**：MD3 主页补上直达「主题」设置页的入口（此前只有 Miuix 主页有），两侧同一导航目标、并存而非互相替代。
- **轨迹审计**：两侧入口对齐；MD3 侧由硬编码中文改为字符串资源，Miuix 侧补上入口。新增 2 个键 `setting_page_trace_audit` / `setting_page_trace_audit_desc`，写入 7 个 `res/values*/strings.xml`（每目录 2 行、删除 0 行）。
- **字体族 + 自定义字体**进 Miuix：与 MD3 同键、同持久化字段（`DisplaySetting.chatFontFamily`）、同落盘路径与校验。
- **模型条目副行**两侧统一取**说明文案**。
- **提供商保存改为「即改即存」**（MD3 与 Miuix 详情页同时改）；随后补回 **name 离开编辑面时的一次性 `trim()` 规范化**，三处 name 输入框全覆盖，打字期间不 trim（避免「边打边规范化」）。
- **代理非法文案**两侧统一；**设备行**两侧同源、同键、同参数序（不是复制重算）。
- **通知页按钮语义**补齐：取消只置 false **不写设置**，确认才写 `updateCheckDisabledUntilEpochMillis`。
- **4 个 Miuix 变体页**（此前 Miuix 风格下直接套用 MD3 实现、观感是 MD3）：**主题页** `SettingThemePageMiuix.kt`、**Web 页** `SettingWebPageMiuix.kt`、**提供商页** `SettingProviderPageMiuix.kt`、**模型提示词页** `SettingModelPromptPageMiuix.kt`；配合两个 MD3 页的 `isMiuix` 早退块，Miuix 下不再走 MD3 实现。选项集合经双向差集核对与 MD3 **一致**（提示词页早退放在两页共用的漏斗，否则 Miuix 的提示词页签会漏分流）。
- **sheet 内搜索不再外溢**（上一批，已推送、此处补记）：底部弹层里的搜索框展开时不再铺满全屏 —— MD3 不再走页面级全屏 Dialog、Miuix 不再把展开层挂到根部 overlay，两者都在 sheet 子树内展开、受 sheet 圆角与高度上限约束（Kedge `KedgeSearch.kt` / `KedgeSearchOverlay.kt` 提交 `7881c3b0c`；KhatKit `PrimaryBottomSheet.kt` 提交 `d974cc572`）。⚠️ 观感由用户实机确认。
- **已知项与遗留**（本批**只记账、不改源码**，逐条带 `file:line`）：见 [UI 验收与观察记录](docs/ui-verification-followups.md#2-设置两风格对齐后的检查)——含 `SettingWebPageMiuix.kt:81`（与 MD3 `SettingWebPage.kt:90` 同一条）的 `LocalClipboardManager` 弃用告警（⚠️ 该批自述「零 warning」不准：实测 `feature/settings/` 下有 2 条）、主题页镜像约 380 行私有 helper（**技术债**，将来提升到 `core/` 后删除镜像）、顶栏「导入 / 新建」可及状态为超集（选项没少）、`PermissionRationaleDialog → Khromia AnimatedAlertDialog` 不做风格分流（**跨仓既有缺口**）、`values-ar` 缺既有 package 键、「探索市场 / 套餐激活」仍硬编码等。

## 2.5.2（2026-09-18）

> 应用内更新走 KodeHeadServer 的 KhatKit 独立更新通道（stable / beta / force_min）。
> 发布版本时，`description` 字段可直接取本文件对应条目。

### 🔄 同步上游 RikkaHub 2.5.2

- 新增火山引擎 TTS（豆包语音，SSE 流式合成，凭据使用 `X-Api-Key`）
- 输入栏背景效果支持 `模糊 / 玻璃` 两种类型（Haze 升级至 2.0.0-rc01）
- JavaScript 引擎迁移到 `quickjs-kt` 并修复资源泄露（Lua 仍为 Rust 单引擎）
- 刷新 MiniMax 地区与 TTS 默认值
- 修复部分非聊天请求缺少 session id 头的问题
- 完善所有语言的重新生成消息确认提示
- 修复内联代码连字显示问题、其他依赖升级

## 2.5.1（2026-09-13）

### 🧩 KhatKit 卡片系统（新增）

- 卡片运行时：支持 `lua` / `js` / `command` 三类卡片，AI 可直接调用
- bridge 能力：`tool` / `ui` / `download` / `store` / `shizuku` / `root` / `accessibility`
- 卡片触发器：支持 `ai`（模型触发）与 `user`（用户触发）
- 卡片市场：从 Hub 按索引懒加载、sha256 校验后落缓存；本地内置卡片启动时自动同步
- Rust 单引擎：Lua / JS 共用同一核心，检索候选收窄到 20，Shizuku 直连，UI 白名单与审批兜底
- `card-validator`：卡片发布前静态校验
- 内置 12 张极客猫开放平台卡片（快递 / 菜谱 / 简繁转换 / 翻译 / 网盘解析 / 聚合解析 / 视频 / 抠图 / 放大 / 移除对象 / 人脸性别转换 / Key 设置），详见 [docs/zenneko-cards.md](docs/zenneko-cards.md)
- 新增 vFlow 对接卡片（本地 Web API）与无 vFlow 场景的原生自动化能力补齐
- 文档：新增 [Lua 卡片脚本开发指南](docs/lua-card-development.md)

### ⬆️ 应用更新通道（新增）

- 客户端接入 KodeHeadServer 的 KhatKit 独立更新通道（`GET /api/khatkit/app/update`）
- 支持 `stable` / `beta` 通道与 `force_min` 强制更新，启动时检查并提供 Markdown 更新说明
- 服务端提供 APK 上传（`POST /admin/khatkit/releases/upload`）与版本发布（`PUT /admin/khatkit/versions/{channel}`）

### 🛰️ 配套服务端（KodeHeadServer / KServer）

- 新增卡片 Hub：manifest 注册、全量 / 分片索引、embedding 语义召回、共享库 registry、按需打包下载
- 公开接口：`/api/khatkit/index.json`、`/index/shard/{tag}/{page}.json`、`/search`、`/libs/index.json`、`/cards/{slug}`(`/script`、`/download`)、`/app/update`
- 管理接口：卡片 CRUD / 校验 / 重索引、库管理、更新版本发布，均需 admin / developer JWT

### 🎨 界面与交互

- 双界面风格：Material 3 Expressive 与 Miuix（Kedge / Khromia 组件桥），设置内切换即时生效
- 首次启动引导：危险权限（Root / Shizuku / 无障碍）、AI 服务商、主题与配色
- 聊天列表滚动条对齐 Now in Android DraggableScrollbar（轨道 + 可拖动滑块 + 回答位置点）
- 首页顶栏搜索改为抽屉同款 morph 动画（标题交叉淡入搜索框、返回箭头滑入）
- 输入框胶囊化 + Genie 展开动画与预测返回；底栏 FAB 二合一（语音输入 / 发送按状态切换）
- 设置项与弹层迁移到 Khromia / Kedge 组件，主题选择重构；全面 UI 精简
- 新增全局下载中心

### 🎙️ 语音

- 本地 sherpa-onnx 语音识别，引擎按需下载
- 新增 OpenAI / Gemini 转写 ASR，自动选择最佳 provider

### 📦 工作区

- 基于 proot 的 Linux 沙箱，可执行命令、读写文件
- 新增 Shell 兼容模式与 HTML / SVG 预览

### 🐛 修复

- 修复流式生成期间聊天列表滚动条跳变 / 闪现、回答点溢出等问题
- 修复侧边栏搜索进退场动画闪现
- 修复代码块连字特性、统计页面异常 JSON 崩溃等问题

### 🔧 工程

- 包名迁移至 `heizige.kk.khatkit`，Ktor 迁移，Hub / MCP 接入
- 内置极客猫免费 AI provider（默认启用），注册 DeepSeek V4.1 Flash（多模态）
- 裁剪未使用模块（videogen / trace-cli），升级 Kedge 0.1.1 / Khromia 1.6.5
