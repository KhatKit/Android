# chat / assistant 内聚与职责分层（t35 批次）

依据：`../ARCHITECTURE.md` §1.2、`docs/architecture-map.md`、t28 审计 `/tmp/khatkit-reviewer-ownership.md`（M08–M18/E03）
与 t26/t27 归属审计。范围仅 `feature/chat`、`feature/assistant` 及其 `src/test`，外部调用者只登记、不在本批改。

## 1. 结果总览

| 项 | 数字 |
|---|---:|
| 新增源文件 | 34（chat/data 10、chat/helper 22、assistant/detail/helper 2） |
| 删除旧路径 | 15（整文件迁出 11、并入 `GroupExportExtensions.kt` 1、被拆分后删除 3） |
| 被拆分/裁剪的原文件 | 8（ConversationExport、GroupExportCard、ChatSizeChecker、ChatRolePanelViewModel、ChatDrawerViewModel、MessageQueue、AssistantImporter、WelcomeWidgetGrid） |
| 同步修改的测试 | 35 个文件（含 11 个源码守门测试的 import/符号） |
| 行为/文案/持久化改动 | 0（只改包、文件名、类型名与 import） |

口径：数据模型/仓库 → `chat/data`；有状态流程/生命周期 → `chat/helper` 的 `*Manager`；
无状态打杂 → `chat/helper` 的 `*Helper`；纯函数 → `helper/*Extensions.kt`；
Page/ViewModel 与 Compose 渲染留 feature 根。

## 2. 逐项落点（t28 M08–M18/E03）

| 项 | 旧 | 新 | 说明 |
|---|---|---|---|
| M08 | `chat/ChatManager.kt`（含 ChatError/ChatErrorSolution） | `chat/data/ChatError.kt`（模型）+ `chat/helper/ChatManager.kt`（生成/session/job 流程） | 类名仍为 Manager；不改 token/锁/落库 |
| M09 | `chat/ChatWelcomeManager.kt` | `chat/data/ChatWelcomeState.kt`（WelcomeWidget/ChatWelcomeState/4 个 widget 常量与 WidgetSizeLimits 扩展）+ `chat/helper/ChatWelcomeManager.kt` | jobs/runTokens 随 Manager |
| M10 | `chat/VoiceSessionController.kt` | `chat/data/VoiceSessionState.kt`（VoicePhase/VoiceSessionState）+ `chat/helper/VoiceSessionManager.kt`（原 VoiceSessionController 改名） | ASR/TTS/job 协调 → Manager |
| M11 | `chat/ChatFloatingWindows.kt`、`chat/helper/ChatFloatingTracker.kt` | `helper/ChatFloatingManager.kt`（原 ChatFloatingWindows）+ `helper/ChatFloatingWindowManager.kt`（原 ChatFloatingTracker）+ `data/FloatingChatWindow.kt` + `helper/ChatFloatingExtensions.kt`（buildFloatingChatUiState） | 窗口句柄/前后台订阅仍为 Manager |
| M12 | `chat/ConversationExport.kt`、`chat/GroupExportCard.kt` | `data/ExportOptions.kt`、`data/GroupExportSelection.kt`（含 GROUP_EXPORT_* 常量）、`helper/ChatExportHelper.kt`（exportToMarkdown/写盘/分享）、`helper/GroupExportExtensions.kt`（纯选择与命名） | Compose 导出卡片（ChatExportSheet/exportToImage/ExportedChat*）留根；MIME/扩展名/序列化字段不变 |
| M13 | `chat/GroupRoleCards.kt` | 并入 `helper/GroupExportExtensions.kt` 的 `groupExportRoleCards` | 纯转换；历史群聊导出仍消费 |
| M14 | ChatSizeChecker/ChatRolePanelViewModel/ChatDrawerViewModel 内联模型 | `data/ChatUiModels.kt`（ConversationSizeInfo/DefaultSizeInfo/RolePanelRenameTarget/DrawerMessageSearchState）+ `helper/ChatFilterExtensions.kt`（asConversationTypeArgument） | Page/VM/remember/dialog 留根；UI 私有布局状态未拆 |
| M15 | `helper/UngeneratedMessageFilter.kt`、`helper/SummaryViewerScope.kt`、`helper/ChatContextUsage.kt` | `helper/UngeneratedMessageExtensions.kt`、`helper/SummaryViewerExtensions.kt`、`data/ChatContextUsage.kt` + `helper/ChatContextExtensions.kt` | SummaryViewer fail-closed 不变；ModelRegistry 默认 lookup 保留在 Helper，不宣称无依赖 |
| M16 | `data/GroupTurnCoordinator.kt`、`data/GroupSpeakerResolver.kt`、`data/GroupRetryEntry.kt` | `helper/GroupTurnExtensions.kt`、`helper/GroupSpeakerExtensions.kt`、`data/GroupSpeakerIdentity.kt`、`helper/GroupRetryExtensions.kt` | 三个 object 是纯判定不是 Manager；X.Y 调用 API 保持不变 |
| M17 | `data/ConversationSession.kt`、`data/ConversationSessionManager.kt`、`data/MessageQueue.kt` | `helper/ConversationRuntimeManager.kt`（原 ConversationSession，持引用计数/job）、`helper/ConversationSessionManager.kt`、`helper/MessageQueueManager.kt`（原 MessageQueue）+ `data/MessageQueue.kt`（QueuedMessage/MessageQueueState/PausedException）+ `helper/QueuedMessageExtensions.kt` | 不改 release/锁/取消语义；attachment 纯函数单独放 Helper |
| M18 | `assistant/detail/AssistantEditManager.kt`、`AssistantImporter.kt` 的 URI 导入 | `assistant/detail/helper/AssistantEditManager.kt`、`helper/AssistantImportHelper.kt`（importAssistantFromUri/localizeCardError） | pendingWrites 仍 Manager；导入 I/O 无持有可变状态为 Helper；Composable 留 detail 根 |
| E03 | `chat/WelcomeWidgetGrid.kt` | `data/WidgetGridModels.kt`（WidgetCells/WidgetSizeLimits/GridSlot/WidgetGridGeometry/ResizeEdge）+ `helper/WidgetGridExtensions.kt`（packWidgets/widgetRects/resizeTarget）+ `helper/WidgetMoveManager.kt`、`helper/WidgetResizeManager.kt` | WidgetGrid/ResizeHandles/SizeReadout/resizeSemantics 等 Compose 绑定留根 |

## 3. 明确保留项（有消费/平台证据，不是"以后再做"）

- **M02 PropertyEditor → core**：跨 assistant+settings，两处 API 消费者；本批只做 feature 内聚，core 提升留最后 core 批次（t28 M02 已归属）。
- **Page/ViewModel/Compose 留根**：ChatPage、ChatWelcome、GroupExportCard 的 `GroupTavernExportCard`、ConversationExport 的 `ChatExportSheet/exportToImage/ExportedChat*`、ChatSizeChecker 的 `rememberConversationSizeInfo/ConversationSizeWarningDialog`、AssistantImporter 的两个 Composable 都在根，避免把 UI 私有布局状态机械抽层（M14/M18 风险列）。
- **GroupTurnCoordinator 嵌套模型未再拆**：`RoundState/RejectCode/Claim/CommitAdmission/Advance/VoteResolution` 被 ChatManager 与多个 group 测试以 `GroupTurnCoordinator.X` 引用（16 处）；整文件移 helper 已满足"纯判定不叫 Manager"，再拆会要求全部调用点改写成顶层类型并产生同名冲突风险，故保持嵌套 API（t28 允许"先整文件移 helper 保 API 并登记未细拆"）。
- **SummaryViewerScope 对象名保留**：文件按职责改名 `SummaryViewerExtensions.kt`，对象名不变，避免 `SummaryViewerScopeTest/WiringSourceGuardTest` 与调用点符号漂移。
- **ChatFloatingManager 的 `TAG = "ChatFloatingWindows"` 字符串保留**：仅 Log tag，无持久化/协议意义；改名不在本批收益内。
- **平台入口未动**：`ChatGenerationForegroundService`（Service 名/FQCN）、`RoleMemoryDocumentsProvider`（authority）仍原包原名；本批只更新其内部 import。
- **R 文案**：本批没有改动任何 `strings.xml`：hosting t6 已完成的资源化写法（`stringResource`/`context.getString`）在拆分中随代码块一起迁移，未回退为字面量。

## 4. 外部调用者更新清单（交最终引用集成任务，本批未越界修改）

新包路径：`feature.chat.ChatManager → feature.chat.helper.ChatManager` 等。以下 21 个 scope 外文件需要同步 import（共 35 个符号点）：

- `app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/C1DeviceEvidenceTest.kt`：GroupTurnCoordinator
- `app/src/androidTest/java/heizige/kk/khatkit/app/core/ui/components/ui/C1GroupQrBitmapRoundTripDeviceTest.kt`：writeExportTempFile、GROUP_EXPORT_MIME_TYPE
- `app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1GroupCancelDeviceTest.kt`：ChatManager、writeExportTempFile
- `app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1GroupExportDeviceEvidenceTest.kt`：writeExportTempFile、shareFile
- `app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1GroupPagingAndFilterDeviceTest.kt`：ChatManager、writeExportTempFile、asConversationTypeArgument
- `app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1GroupRetryResumeDeviceTest.kt`：ChatManager、writeExportTempFile
- `app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1GroupShareChooserDeviceTest.kt`：writeExportTempFile、shareFile、GROUP_EXPORT_MIME_TYPE
- `app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1GroupZeroOutputProbeTest.kt`：ChatManager
- `app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`：ChatManager、GroupTurnCoordinator、writeExportTempFile
- `app/src/main/java/.../core/data/ai/tavern/TavernChatCodec.kt`：GroupSpeakerResolver、GroupTurnCoordinator
- `app/src/main/java/.../core/data/ai/tools/KhatKitTools.kt`、`core/di/AppHiltModule.kt`、`core/network/WebApiModule.kt`、`core/network/WebServerManager.kt`、`core/network/routes/{ConversationRoutes,EventsRoutes,FolderRoutes}.kt`、`feature/history/HistoryViewModel.kt`：ChatManager
- `app/src/main/java/.../core/ui/components/ai/{ChatInput,VoiceModeRow}.kt`：VoicePhase、VoiceSessionState
- `app/src/main/java/.../core/ui/components/ui/ErrorCard.kt`：ChatError、ChatErrorSolution
- `app/src/test/java/.../core/data/ai/tavern/C1pGroupExportHashTest.kt`：writeExportTempFile
- 其余（`core/data/model/Conversation.kt`、`GroupChat.kt`、`MemoryRepository.kt`、`GenerationLoop.kt`、`ChatMessage*.kt`）只在 KDoc 里提到旧名，无需 import。

## 5. 静态验证（本批未跑 Gradle/设备）

- `git diff --check`：exit 0。
- 包内引用扫描：`feature.chat/feature.assistant` 域内缺失 import 0、first-party 失效 import 0、同名 import 冲突 0、同包重复顶层声明 0。
- 括号/结构回归：与改动前备份逐文件比对，`{}`/`()` 平衡异常 0；所有新文件 `package` 单一声明。
- 未闭合：外部 21 个文件的 import 未改（见 §4），因此该批完成前全仓编译不绿；`GroupTurnCoordinator` 嵌套模型细拆、M02 core 提升为登记项。
- 账号/持久化契约核对：无 `@SerialName`、Room 表/列、DataStore key、WorkManager FQCN、Intent action/authority、R.string key 变更。
