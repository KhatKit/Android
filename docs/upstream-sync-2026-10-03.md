# 上游 rikkahub 同步方案（2026-10-03）

> 对照范围：`upstream/master` @ `2267943a`（2.5.6）相对 merge-base `7263dd36`（2.5.4）的 43 个提交。
> 本地基线：`main` @ `ed8fdaf0`。
> 备份分支：`backup/pre-rikka-merge-2026-10-03`。

## 总体结论

**不要执行 `git merge upstream/master`。** 实测会产生 79 个冲突文件，且因为项目已整体重命名包名（`me.rerere.rikkahub` → `heizige.kk.khatkit`）、新增 8 个自有模块、删除 `videogen`，git 无法识别对应关系，会把定制代码大面积标记为冲突。

真实增量远小于表面：

| 类别 | 数量 | 说明 |
|---|---|---|
| 已完整存在 | 23 / 43 | rrzt 上次已逐字移植，`git cherry` 因 commit 不同而认不出 |
| 部分存在（差接线） | 5 | 底层代码已在仓库里，但**没被调用**，是死代码 |
| 真正缺失 | 14 | 其中 9 个是 1–70 行小 fix |
| 不适用 | 1 | `a26d1d55` mediagen 模块（2379 行，上游最大的一块） |

净新增工作量约 **800–1200 行 Kotlin**，一半是 1–10 行的碎 fix。

---

## 两个意外发现（优先级高于同步本身）

这两条是「代码已经在仓库里，但没有接上」，属于独立 bug，不依赖上游同步。

### 1. `chart_display` 工具是死代码

上游 `00c8d53a` 的产物全部在仓库里，但工具从未注册，模型永远调不到。

已有：
- `app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tools/local/ChartDisplayTool.kt:23` — `buildChartDisplayTool()` 定义完整
- `app/src/main/java/heizige/kk/khatkit/app/core/ui/components/charts/` — 6 个组件（`ChartCard` / `ChartPlot` / `ChartScale` / `ChartSpec` / `ChartTable`）
- 渲染链路：`ChatMessage.kt:297`、`ChatMessageCot.kt:30` 的 `ChartBlock`、`ConversationExport.kt:625`
- 8 个 chart string（`values-zh/strings.xml` 等）

缺口（3 处）：
- `LocalToolOption.kt` — 7 个 `data object` 里没有 `ChartDisplay`
- `LocalTools.kt:35` `getTools()` — 没有对应 `if` 分支，也没有 `chartDisplayTool` 属性
- `AssistantLocalToolPage.kt` — 助手设置页无开关

修法：照上游 diff 补这三处（上游改动就是 `+@SerialName("chart_display") data object ChartDisplay`、`+val chartDisplayTool by lazy { buildChartDisplayTool() }`、一个 `if` 分支、一个 Switch item）。**约 25 行。**

### 2. 内置技能（`skill-creator`）是死代码

上游 `3b346748` + `7f17dcc8` 的资产在，但从未解压到文件系统。

已有：
- `app/src/main/assets/builtin_skills/skill-creator/SKILL.md`（91 行）
- `BuiltinSkills.kt:16` `extractIfNeeded()`、`:81` `mergeWithBuiltinSkills()` — 函数写了但没人调
- `SkillsTools.kt:39` `escapeXml()`、`:68` 双形态名称匹配
- `BuiltinSkillsTest.kt`

缺口（5 处）：
- `SkillManager.kt:24` `listSkills()` 只扫用户目录，无内置目录合并
- `KhatKitApp.kt` 无启动解压调用
- `RepositoryHiltModule.kt:93` bind mount 表只有 `/skills` `/tool_outputs` `/upload`，**缺 `/builtin_skills`**
- `SkillDetailPage.kt` 无 `readOnly` 参数
- `WorkspaceReminderTransformer` 无只读提示

修法：需人工接线，约 60–100 行。要注意 `RepositoryHiltModule` 的 bind mount 表注释明确写着「同一份挂载表既用于 PRoot 的 -b 参数，也用于文件工具的路径解析，避免两处漂移」，加挂载点时这两处会一起变。

---

## Tier 1 — 极小 fix（6 处，约 30 行，纯收益）

无风险，可一次性做完。建议作为第一个提交。

| # | 上游 commit | 内容 | 目标位置 |
|---|---|---|---|
| 1 | `8b696c0c` | MCP 空名请求头过滤。设置页「添加请求头」留空会让 OkHttp 抛 `name is empty` | `McpSessionRegistry.kt:487` `val base = commonOptions.headers` → 加 `.filter { it.first.isNotBlank() }` |
| 2 | `4e4bfa62` | OpenAI 图片生成 `size=auto` 不下发；`x.ai` 判定改用 host 精确匹配（避免 `xxx-max.ai` 中转域名误判） | `OpenAIProvider.kt:217`、`:220`；需 import `okhttp3.HttpUrl.Companion.toHttpUrlOrNull` |
| 3 | `ed3569c7` | 文件编辑器不再把全文存 saved state。`rememberTextFieldState()` 会把全文塞进 Bundle，大文件切后台触发 `TransactionTooLargeException` | `WorkspaceFileEditorPage.kt:9`、`:67`；`SkillDetailPage.kt:319` 的 `rememberSaveable(skillFile.relativePath)` → `remember` |
| 4 | `158f1f68` | 混元 baseUrl `api.hunyuan.cloud.tencent.com/v1` → `tokenhub.tencentmaas.com/v1`；图标正则加 `\bhy(\b|\d)` | `DefaultProviders.kt:213`、`AIIconMatcher.kt` 的 `PATTERN_HUNYUAN` |
| 5 | `44236d7b` | 输入框玻璃 tint 透明度 `0.72f` → `0.3f`。**依赖升级部分已完成**（`material3 1.5.0-alpha29` / `nav3Core 1.2.0` / `coil 3.6.3` / `haze 2.0.0` 全在），只差这 1 行 | `ChatInput.kt:426` |
| 6 | `7508eee9` | RTL 方向图标适配。**依赖已到位**（`huge-icons 1.5`），缺 `Modifier.mirrorForRtl()` 扩展函数 + `ConversationList` 应用 | `core/util/ComposeExt.kt`（新增函数）、`ConversationList.kt` |

第 5、6 条的上游 diff 大部分是依赖升级，实际只剩 1 行和一个函数 —— 属于「上游做了一半」的典型。

---

## Tier 2 — 功能接线收尾（约 200 行）

代码已在仓库，只需接线让功能真正可用。

| # | 上游 commit | 内容 | 说明 |
|---|---|---|---|
| 7 | `00c8d53a` | 注册 `chart_display` | 见上文「意外发现 1」，约 25 行 |
| 8 | `3b346748` | 接通内置技能 | 见上文「意外发现 2」，约 60–100 行 |
| 9 | `7508eee9` | `mirrorForRtl` | 与 Tier 1 第 6 条是同一个提交，放这里做更合适 |
| 10 | `82c339c1` | 列表交互统一（`ItemActionMenu` / `ReorderableDrag`） | **组件已建且已适配 Kedge/Miuix，但全仓零引用**。13 个页面未重构（`SettingProviderPage.kt:219` 仍是旧 `dragHandle` + `.scale(0.95f)`）。**这 13 个页面要按 Kedge/Miuix 双风格重写，不是上游 patch 能直接落的，建议单独排期** |

---

## Tier 3 — 小功能移植（约 200 行）

独立、边界清晰，无高风险。

| # | 上游 commit | 内容 | 涉及面 |
|---|---|---|---|
| 11 | `282b85f2` | 复制助手时同时复制记忆 | `MemoryRepository.copyMemories()` + `AssistantViewModel.kt:74` `copyAssistant()` + `AssistantPage` 勾选 + 7 语言 string |
| 12 | `9f02586d` | 技能保存崩溃修复 + GitHub 按字节保存 | `SkillManager.kt:104` `saveSkillFile` 仍是裸 `writeText`（无 temp+rename、无 try/catch）；`SkillsViewModel.kt:111/125/317` 仍用 `downloadText`，需改 `downloadBytes` + 429/403 限流提示。**注意**：`SkillManager.kt:51` 的 `saveSkill` 已有原子写，只有 `saveSkillFile` 这条路径没修 |
| 13 | `f099cd40` | 技能页按名称/描述搜索 | `SkillsPage.kt` 中 `search`/`query`/`filter` 零命中；需加 6 语言 string |

---

## Tier 4 — 需要设计，单独立项

不要混在前面的批次里。

### 14. `654a5e1f` 提供商「高级设置」tab + 自定义请求头

上游 14 文件 / +173 −29。**风险点是序列化兼容，不是代码量。**

改动链：
1. `ai/.../ProviderSetting.kt` — 加 `abstract val customHeaders: List<CustomHeader>`，3 个子类（`OpenAI:53` / `Google:116` / `Claude:181`）各加字段，3 处 `copyProvider`（`:41` / `:92` / `:157` / `:218`）各加参数
2. `ai/.../util/Request.kt` — 新增 `mergeCustomHeaders`
3. 4 个 provider 调用点：`OpenAIProvider` / `ChatCompletionsAPI` / `ResponseAPI` / `GoogleProvider` / `ClaudeProvider`
4. `SettingProviderDetailPage.kt:170` `rememberPagerState { 2 }` → `{ 3 }`，加第 3 个 tab
5. **`SettingProviderDetailPageMiuix.kt`（127 行）也要同步改** —— 项目是 Kedge/Miuix 双风格，上游只改 MD3 版
6. `PropertyEditor.kt` — 请求头名加下拉（`SelectTextField` 加 `label`）
7. 新增 `RequestHeadersTest.kt`（49 行）

**序列化影响评估（已核实）**：
- `CustomHeader` 已存在于 `ai/.../Provider.kt:119`，是 `@Serializable data class`，无需新建
- 持久化走 `SettingsRepository.kt:192` `preferences[PROVIDERS] = JsonInstant.encodeToString(settings.providers)`
- `JsonInstant`（`core/util/Json.kt:7`）配了 `ignoreUnknownKeys = true` + `encodeDefaults = true`
- 新字段带 `= emptyList()` 默认值 → **旧配置反序列化时走默认值，不会崩**
- `ShareSheet.kt:85` `decodeProviderSetting` 同样用 `JsonInstant`，分享码兼容
- `ShareSheetTest.kt` 有 4 个断言需同步（余额选项 / 前缀 / 版本号）

结论：**技术上安全**（`ignoreUnknownKeys` + 默认值兜底），但必须补测试，且别忘 Miuix 版页面。

**另注**：`setting_provider_page_advanced_settings` 这个 string 在本地**已存在**，但被 `ProviderModelList.kt:277` 用于**模型级** tab，不是上游新增的**提供商级** tab —— 别误判成「已存在」而跳过。

### 15. `70b382f5` 设置读取健壮性

上游只改 2 文件 / +60 −19，但要动 DataStore 构造方式，**与本地自有迁移链直接冲突**：

- 本地 `SettingsRepository.kt:64` 用 `preferencesDataStore(name = "settings", produceMigrations = ...)`，带 **5 个自有迁移**：`PreferenceStoreV1` ~ `V5Migration`
- 上游改成 `PreferenceDataStoreFactory.create(corruptionHandler = ReplaceFileCorruptionHandler { ... })` + `retryWhen` + 损坏文件备份

关键点：上游的 `migrations = listOf(...)` 里**没有**本地的 V1–V5 迁移。直接 cherry-pick 会**丢掉全部 5 个迁移**，导致升级用户设置被重置 —— 这正是这个提交想修的 bug 本身。

正确做法：把 `ReplaceFileCorruptionHandler` + `retryWhen` + 损坏文件备份这三样**移植进本地现有的 `preferencesDataStore` 写法**，保留 V1–V5 迁移不动。此外 `KhatKitApp.kt:127` `incrementLaunchCount()` 上游改成原子写（`store.incrementLaunchCount()`），本地这个方法零命中，可一并处理。

### 16. `db1ce811` SearchPicker 改 MD3 分段列表 — **建议放弃**

上游把 `SearchPicker.kt` 改成 MD3 `SegmentedListItem` + `BackHandler` + `SheetHeader`，并回退服务商页返回键。

**与本地方向直接冲突**：`ed8fdaf0` 刚把模型列表/搜索改成 KSU 式全屏搜索（`KedgeSearchBar` 药丸↔全屏 morph）。两套交互方案互斥，且本地版刚编译验证过。若要上游的返回键行为，应只摘 `BackHandler` 部分，不要动分段列表。

---

## Tier 5 — 可选

| # | 上游 commit | 内容 | 建议 |
|---|---|---|---|
| 17 | `2cd62ad2` | Gemini Interactions API | **1412 行**（`InteractionsAPI.kt` 576 + `InteractionsStreamDecoder.kt` 289 + 499 行测试 + `useInteractionsApi` 开关 + `MessageMetadata` 新协议 + UI）。独立可移植，但工作量是其余所有项的总和。若 Google Gemini 是常用路径则值得做，否则延后 |
| 18 | `b9c0d3b7` | 数据恢复页（从聊天记录恢复助手） | 中等：`ConversationDAO.countByAssistant` + Repository + `DebugPage` 第 4 个 tab + `DebugViewModel` 两个方法。本地 `DebugPage.kt` 有 Miuix 分支需同步 |
| 19 | `2267943a` | S3 预签名 URL（`AwsSignatureV4.presignGetUrl`） | 可从该提交单独摘出，与 mediagen 无耦合。`app/.../sync/s3/AwsSignatureV4.kt:24` 现只有 `sign()`。含 106 行测试 |
| 20 | `447bb7e8` | 版本号 → 2.5.6 / 191 | **按项目自己的发版节奏**。`CHANGELOG.md` 显示 KhatKit 走独立版本通道（`GET /api/khatkit/app/update`），不宜跟上游版本号 |

---

## 明确放弃

| 上游 commit | 内容 | 原因 |
|---|---|---|
| `a26d1d55` | videogen → mediagen 统一媒体生成模块（2379 行） | 上游 43 个提交里行数最大的一块。本地从未引入 `videogen`，`settings.gradle.kts` 无此模块；本地有自研替代 `feature/imggen/` + `mediapicker` + `image-toolbox-dependency`。硬塞进来要改 `settings.gradle.kts` + `app/build.gradle.kts`，且视频生成（阿里/火山/MiniMax）本地无需求 |
| `2267943a` 的 mediagen 部分 | 同上 | 同上。仅 S3 部分可选（见 Tier 5 第 19 条） |

---

## 已完整存在，无需操作（23 条）

`2d5c51bd` `b8e0fec4` `4ba5d79f` `7a53065a` `cf79246b` `d5f0039b` `620e38cc` `9435b8a2` `3443dd45` `3a9ae690` `b2d73a65` `b2525e7b` `d21265cb` `95fed05e` `280a039c` `eaa003ad` `6c903feb` `426ede84` `53f224b1` `7f17dcc8` `233e095a` `6719c301` `598b4efa`

其中 7 条在本地历史有独立对应 commit（内容一致、commit 不同，故 `git cherry` 显示为 `+`）：

| 上游 | 本地对应 |
|---|---|
| `2d5c51bd` 日志复制过大闪退 | `49a2136a` |
| `620e38cc` JsonTree 长按闪退 | `87850d22` |
| `b8e0fec4` + `4ba5d79f` MCP OAuth | `127f0604` |
| `7a53065a` OAuth 回调 localhost | `9c6628c5` |
| `cf79246b` inputSchema `$ref` | `dabed3c5` |
| `d5f0039b` 快速模型缺失提示 | `955beadd` |
| `b2525e7b` 模型列表折叠 | `10fd9852`（后被 `ed8fdaf0` 增强为 KSU 式搜索） |

`.agents/skills` 与上游 `9435b8a2` **字节级一致**（`git diff` 输出为空，两侧均 86 文件）。

---

## 建议执行顺序

1. **Tier 1**（6 处碎 fix）→ 一个提交，半小时
2. **意外发现 1 + 2**（chart 注册、内置技能接线）→ 一个提交，激活两个「看起来已有」的功能
3. **Tier 3**（复制助手带记忆、技能保存修复、技能页搜索）→ 一个提交
4. Tier 4 三项各自独立提交，每项单独验证
5. Tier 5 按需

每步做完跑 `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`，Tier 4 的序列化改动额外补 `ShareSheetTest`。

## 测试缺口

上游为这些功能写的 8 个测试文件本地零命中，若移植应一并带上：
`RequestHeadersTest` `InteractionsApiTest` `AwsSignatureV4Test` `ChatServiceTest` `MediaGenerationProviderSettingTest` 等。

本地已有：`ModelRegistryTest` `McpToolSchemaTest` `BuiltinSkillsTest` `SkillsToolsTest`。
