# 合并评审：全仓架构重构与宿主文案迁移（final gate）

评审人：reviewer-ds（独立评审）
评审对象：commit `4b333922d`（"fix(chat): 搜索框字符样式与聊天输入框统一"），当前 HEAD `8ee031639` = 该 commit + 1 个 docs-only 追加（`docs/refactor-final-verification.md` +13 行，`git diff --stat 4b333922d..HEAD` 仅该文件）。代码树与验证对象一致。
验证证据：`/tmp/kv2-results.txt` 五条全 0（compile/appTest/test/lint/diffcheck）；文档侧证据 `docs/refactor-final-verification.md`。本次评审**未运行 Gradle / 未操作设备**（verifier 独占），仅做源码与文档抽检 + 静态复算。

## 0. 结论

**needs_revision（不通过，需 2 项修复后重验）**

- **F1（blocker，交付态）** 当前工作树不是任何已验证快照：存在 3 个未提交的 chat 源码修改 + 1 个未跟踪 scratch 文件。以此树交付 = 交付未验证内容；必须先收口并重跑验证，或明确丢弃。
- **F2（high，验收面）** 内置服务商文案仍有 8 处用户可见中文直接写在 Composable 里，且**同文件另一处已用 `stringResource`**，属漏迁而非产品例外；与验收"全部宿主用户可见文案迁入 R 资源"不符。
- F3/F4 为改进项，不单独阻断。

除上述两项外，结构迁移、导航、范围外修复、资源 en/zh 对称性、Hilt 接线、旧符号清零均抽检通过（详见 §1–§6）。

## 1. 工作树与交付态（F1，blocker）

`git status --porcelain`（23:28 采样）：

| 文件 | 状态 | 规模 |
|---|---|---|
| `app/src/main/java/heizige/kk/khatkit/app/core/ui/components/message/ChatMessage.kt` | unstaged M | +43/-25±，新增 `chatMessageTextStyle`/`chatMessageBubbleColor`，可见性 private→internal |
| `app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatList.kt` | unstaged M | 搜索高亮色参数化 + 7 条 import |
| `app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatPage.kt` | unstaged M | 搜索框 `TextFieldState`→`String` 状态重构（-85/+…） |
| `.tmp-anchor-check.py` | untracked | 仓库根 scratch 脚本（AGENTS 文档守护用） |

- 这 3 个文件在验证快照 `4b333922d` **之后**产生（`git diff --stat 4b333922d..HEAD -- '*.kt'` 为空，说明是工作树改动），不在 kv2 五条 0 的覆盖内；其中 `ChatPage.kt` 改了顶栏搜索输入的持有方式，属交互面变更。
- `git diff --check` 当前 **exit 0**（无空白错误），但验证结论不能外推到未提交内容。
- **requiredFix**：二选一 —— (a) 以 `4b333922d` 为交付基准：丢弃/另存这 3 个改动并删除 `.tmp-anchor-check.py`，`git status` 干净后复跑五条命令；(b) 若要包含这批 chat 改动：先提交，再以新 commit 重建洁净 worktree 重跑五条命令，并把新证据写进 `docs/refactor-final-verification.md`。**不得**在 dirty 树上直接出包。

## 2. 文案资源化（F2，high）

`app/src/main/java/heizige/kk/khatkit/app/core/data/datastore/DefaultProviders.kt`（内置服务商种子数据，Composable 内直接渲染）：

| 行 | 现状 | 判读 |
|---|---|---|
| 142 | `append("企业级AI服务, 官网：")` | 用户可见 prose，未资源化 |
| 179 | `append("主要为 RP 玩家服务的站点")` | 同上 |
| 181 | `append("官网：")` | 同上 |
| 191 | `Text("主要为 RP 玩家服务的站点")` | 同上 |
| 204 | `append("免费 AI 网关，OpenAI 兼容，默认启用。")` | 同上 |
| 206 | `append("模型：")` | 同上 |
| 209 | `append("查看接口文档")` | 同上 |
| 216 | `Text("免费 DeepSeek V4 Flash / GLM 5.2，开箱即用")` | 同上 |

反证这不是"产品例外"：同文件 `44–45` 的硅基流动 description **已经**写成
`${stringResource(R.string.silicon_flow_description)}` / `${stringResource(R.string.silicon_flow_website)}`；
同一 DSL、同一渲染路径，说明约定是资源化，这 8 行是漏迁。服务商/模型 `name`（`37/64/102/…`）是**持久化品牌值**，保持字面量合理，不在本 finding 内。

- **requiredFix**：把这 8 处 prose 抽到 `values/strings.xml` + `values-zh/strings.xml`（成对，占位符保留），Composable 内改 `stringResource`；若坚持赞助商原文不改，需在 `docs/architecture-map.md` 记录"赞助位原文例外 + 批准人"，再据此放行。

其余抽检：`Tag.kt:96-108`、`CardGroup.kt:328` 的中文都在 `@Preview` 内；`RememberPermissionState.kt` 在 KDoc 示例内；均属既定例外。全 app 资源 en/zh 独立复算：**各 3042 条目、唯一名 3042、键差集 0/0、同类型重复 0**，87 个 res XML 可解析（与最终报告方向一致，数字随 5 对新增 key 从 3037 增长）。

## 3. 结构迁移抽检（pass）

- 旧批次清单已在 `docs/refactor-core-ui-integration.md §4` 闭合；实测旧符号 grep 为 0：
  `feature.lanshare.LanShareCardBridge|LanShareManager`、`feature.yunx.YunxCardBridge|YunxMediaDownloadHandler`、`feature.workspace.data.WorkspaceTerminalSessionManager`、`feature.settings.data.addSearchRepository|SearchServiceOrder`、`feature.settings.AboutUpdateState|SettingSearchEntry|ImportedChatFont`、`StatsVM|FavoriteVM|HistoryVM|ToolGridZoom`。
- t13 报告（`docs/refactor-architecture-i18n-verification.md §3`）列出的阻断符号 `ChatFloatingWindows`、`AutomationTracer.*`、`UiAutomationSnapshotProvider`、`WorkflowLegacy.fromStored`、androidTest `AutomationBus` 在当前树均已为 0（`AutomationToastTest` 中 AutomationBus 计数 0）。
- 结构计数与文档一致：feature 包 29 个、`core/ui/icons` 153 kt；feature 下 356 kt（比 AGENTS 的 241 增长来自各批 data/helper 拆分）。
- `docs/refactor-*.md` 9 份均含"结论/不变量/逐文件映射/未闭合"四段，未发现把"以后再做"当完成项的表述；批次未闭合项都给了 file:line 证据。

## 4. 导航与 PageBackInterceptor（pass，附改进）

- `RouteActivity.kt` `PageBackInterceptor`（~892–990）新增显式 `order` 单调序号，`push` 写 `order[token]=nextOrder++`，`pop` 清 `order`；`interceptWhere` 对候选 `sortedByDescending{order[it]}`，"后登记优先"契约不再依赖 `SnapshotStateMap` 哈希序；`PageBackInterceptorTest.mostRecentlyRegisteredPredictiveOverlayWins` 覆盖该契约。行为契约未变。
- 全仓 `PredictiveBackHandler(` 代码调用点 3 处（`RouteActivity.kt:620` 单一分发、`ChatInput.kt:459`、`core/ui/hooks/PredictiveSearchBack.kt:51`）+ mediapicker 2 处，与 `docs/refactor-navigation.md §4` 的 8 条判定表一致；feature 页面无裸 `BackHandler(`（grep 仅命中 PredictiveBackHandler 前缀）。
- 改进项（不阻断）：`nextOrder` 无重置入口，若测试进程内反复 push 数千次仍不会溢出（Long），但建议测试间不共享单例状态；文档 §4 的 `RouteActivity.kt:615/622` 行号与实际 `620/626` 略有漂移，建议下次编辑时同步。

## 5. 范围外修复抽检（pass，附 F3）

| 项 | 证据 | 结论 |
|---|---|---|
| `CardExecutor/BridgeFactory` 文案接口 | `khatkit/.../bridge/impl/BridgeFactory.kt:48,68,162` 新增 `messageResolver: (RuntimeText) -> String = RuntimeText::diagnosticMessage` 并透传给 `CardExecutor`（`exec/CardExecutor.kt:73` 同名默认参数）；`KhatKitTools.kt:521,2623` 注入 `{ it.localizedMessage(appContext) }`；`HubClient.kt:63` 同型默认参数 | 默认值保持旧行为，Kotlin 源兼容；唯一直接构造点经 BridgeFactory，未发现绕过注入的路径 |
| `UiWidgetVocabulary` 白名单 | `card-validator/.../UiWidgetVocabulary.kt:13,18` 新增 Group/ListRow/Stepper/Selector/ChipRow/Dropdown；`khatkit-ui/.../NodeTreeRenderer.kt:385,393,477-480` 全部有渲染分支，`khatkit/.../NodeTree.kt:69-70,83-142` 有 fields schema | 无幻影节点 |
| `khatkit/build.gradle.kts:18-23` | `unitTests.isReturnDefaultValues = true` + 原因注释 | 测试严格度取舍合理，建议 CI 文档保留说明 |
| `ResponseAPI.kt:247-252` effort 回退 | 自定义映射返回 null 时不再回退 `level.effort`，语义"null=明确不发"自洽 | **F3（low/medium）**：无专门单测覆盖 `reasoningEffort != null && invoke() == null` 分支（仅 GrokProviderTest 间接），建议在 `ai/src/test` 补一条；不回退重构交付 |

## 6. Hilt 图与平台入口（pass）

- `core/di/AppEntryPoint.kt:28,74` 与 `core/di/AppHiltModule.kt:38,147` 已指向 `feature.workspace.helper.WorkspaceTerminalSessionManager`（旧 `data.` 引用 0）。
- 新迁移类的构造方式与 Hilt 角色一致：`StatsRepository` 用 `@Inject constructor`（app 模块内 internal class，Hilt 同模块生成可见）；`WorkspaceTerminalSessionManager` 仍由 `AppHiltModule` `@Provides`（非 @Inject 构造）；平台组件（`WorkspaceDocumentsProvider`、Worker FQCN、Service/Receiver）包名未变。
- compile=0（kv2 绑定 commit）是对 Hilt/KSP 图的强证据；本次评审未发现 DI 反向依赖或丢失 binding。

## 7. 资源与文档一致性

- 资源：en/zh 3042/3042、键差集 0/0、同类型重复 0（独立复算）。
- 文档快照数字（3035 @d0982ab1a、3037 @5cb59c1da、3040 @d5b9ca5ac、3042 当前）各自绑定 commit，属正常漂移；建议在最终报告顶部统一写"数字绑定 commit"，避免被误读为现状。
- 未发现 t13 报告中"资源重复键阻断"复发：`setting_trigger_workflow_nodes` 同名跨类型为 aapt2 合法项，已由 `packageDebugResources` 通过佐证。

## 8. 放行条件（按优先级）

1. **F1**：确定交付基准（`4b333922d` 或含 chat 改动的新 commit），对应树必须 clean（删除 `.tmp-anchor-check.py`），五条命令在**同一 commit** 上重跑并写入报告。
2. **F2**：8 处 prose 资源化（en/zh 成对）**或**在 `docs/architecture-map.md` 记录赞助位例外与批准人；两种方式都需要一次 docs 提交，故可与 F1 合并到同一次验证。
3. **F3**（建议，不阻断）：补 `ResponseAPI` effort 自定义映射 null 的单测。
4. 以上完成后，本报告 §1–§6 的通过项仍然有效，可只补跑五条命令，无需重做全量评审。

## 9. 评审边界

- 未运行 Gradle/assemble/lint/设备测试（verifier 独占）；未修改源码或资源；只写本文件。
- 设备级 UI 验收仍按 `docs/refactor-navigation.md`、`docs/manual-verification.md` 的人工清单执行，本报告不冒充真机结论。
- 未逐条重做 802 行/507 文件的非 UI 中文残留审计：采信各批次报告的 file:line 分类，仅对 `DefaultProviders` 这类"同文件已资源化"的反常点做抽检（即 F2）。
