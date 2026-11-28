# core UI 归位、共享提升与全 app 引用集成（t39）

依据：t27 212 文件映射（`/tmp/khatkit-verifier-ownership.md`）、t28 M01–M07 与 t35–t38 各批 external 清单。
范围：`app/src`、`docs/architecture-map.md`、本文件。

## 1. 结果总览

| 项 | 数字 |
|---|---:|
| 本批结构迁移文件 | 52（39 个 t27 单功能 core UI + 7 组 t28 共享提升 + settings 的 workflow/automation 页面 2 + workflow 偏好/文案 2 + 2 个回迁 core） |
| 共享提升 | M01 shell(2)、M02 PropertyEditor、M03 ThemePickers、M04 MCP(3)、M05 TerminalPluginInfo（此前已由 t38 落位 core/data/terminal）、M06 ToolCategory+目录函数拆 2 文件、M07 ToolHeaderExtensions |
| 跨批引用修复 | 106 条失效 import/符号 → 0（仅 1 条 SplashGlyph 成员 import 属合法，见 §5） |
| 同步范围 | `app/src/main`、`app/src/test`、`app/src/androidTest`；DI/EntryPoint/routes 调用点 |
| core 现状 | core/ui 286、core/data 212、core/network 17、core/util 26、core/di 4；feature 29 包 345 kt |

职责口径：共享提升只按 t27/t28 的真实多消费者证据；单功能 core UI 下放对应 feature；Page/VM/Compose 留 feature 根；
core 内平台/运行时桥保留并给消费者证据（§4）。

## 2. t28 M01–M07（共享提升/单功能归位）

| 项 | 旧 → 新 |
|---|---|
| M01 | `feature/ui/shell/{MochiShell,MochiNavPages}.kt` → `core/ui/shell/`（tools/workspace/imggen/settings 4 feature 消费者） |
| M02 | `feature/assistant/detail/PropertyEditor.kt` → `core/ui/components/ai/PropertyEditor.kt`（assistant+settings） |
| M03 | `feature/settings/components/ThemePickers.kt` → `core/ui/components/theme/ThemePickers.kt`（onboarding+assistant+settings） |
| M04 | `feature/settings/mcp/{McpConfigModal,McpCommonOptions,McpToolsConfigure}.kt` → `core/ui/components/ai/mcp/`（settings+explore） |
| M05 | `feature/extensions/TerminalPluginInfo.kt` → `core/data/terminal/TerminalPluginInfo.kt`（t38 已落位；本批核验消费 extensions+chat+core） |
| M06 | `feature/tools/helper/ToolCatalogueExtensions.kt` 拆为 `core/data/model/ToolCategory.kt`（枚举）+ `core/util/ToolCatalogueExtensions.kt`（toolTitle/toolDescription/matchesToolQuery 等纯函数） |
| M07 | `feature/tools/helper/ToolHeaderExtensions.kt` → `feature/chat/helper/`（仅 chat CardWidgetPage 直接调用，不借间接数据消费升 core） |

## 3. t27 单功能 core UI 下放（42 条候选，逐文件）

| 旧（core） | 新（feature） |
|---|---|
| `core/ui/components/ai/completion/ChatCompletionProvider.kt` | `feature/chat/components/ai/completion/ChatCompletionProvider.kt` |
| `core/ui/components/ai/completion/WorkspaceIgnoreMatcher.kt` | `feature/chat/components/ai/completion/WorkspaceIgnoreMatcher.kt` |
| `core/ui/components/ui/KeepScreenOn.kt` | `feature/chat/components/ui/KeepScreenOn.kt` |
| `core/ui/hooks/Debounce.kt` | `feature/chat/hooks/Debounce.kt` |
| `core/ui/hooks/ImeAutoScroller.kt` | `feature/chat/hooks/ImeAutoScroller.kt` |
| `core/ui/theme/GoogleSans.kt` | `feature/splash/theme/GoogleSans.kt` |
| `core/ui/theme/MorphPolygonShape.kt` | `feature/settings/theme/MorphPolygonShape.kt` |
| `core/ui/hooks/PlayStore.kt` | `feature/chat/hooks/PlayStore.kt` |
| `core/ui/components/ui/QRCode.kt` | `feature/settings/components/ui/QRCode.kt` |
| `core/ui/components/ui/ListSelectableItem.kt` | `feature/chat/components/ui/ListSelectableItem.kt` |
| `core/ui/components/ui/StickyHeader.kt` | `feature/backup/components/ui/StickyHeader.kt` |
| `core/ui/components/ai/CompressContextDialog.kt` | `feature/chat/components/ai/CompressContextDialog.kt` |
| `core/ui/components/ui/QRCodeDecoder.kt` | `feature/settings/components/ui/QRCodeDecoder.kt` |
| `core/ui/floating/ChatFloatingContent.kt` | `feature/chat/floating/ChatFloatingContent.kt` |
| `core/ui/components/ui/Greeting.kt` | `feature/chat/components/ui/Greeting.kt` |
| `core/ui/components/easteregg/EmojiBurst.kt` | `feature/settings/components/easteregg/EmojiBurst.kt` |
| `core/ui/components/ui/MochiFormRows.kt` | `feature/extensions/components/ui/MochiFormRows.kt` |
| `core/ui/hooks/ChatInputState.kt` | `feature/chat/hooks/ChatInputState.kt` |
| `core/ui/components/ai/ChatAttachmentPicker.kt` | `feature/chat/components/ai/ChatAttachmentPicker.kt` |
| `core/ui/components/ai/completion/ChatInputCommand.kt` | `feature/chat/components/ai/completion/ChatInputCommand.kt` |
| `core/ui/components/khatkit/CardPageStack.kt` | `feature/chat/components/khatkit/CardPageStack.kt` |
| `core/ui/components/ai/AiChatGenieEffect.kt` | `feature/chat/components/ai/AiChatGenieEffect.kt` |
| `core/ui/components/ui/ErrorCard.kt` | `feature/chat/components/ui/ErrorCard.kt` |
| `core/ui/components/ui/KkScrollEdgeFade.kt` | `feature/chat/components/ui/KkScrollEdgeFade.kt` |
| `core/ui/components/ai/AttachmentChips.kt` | `feature/chat/components/ai/AttachmentChips.kt` |
| `core/ui/components/ai/VoiceModeRow.kt` | `feature/chat/components/ai/VoiceModeRow.kt` |
| `core/ui/components/ai/WorkspaceCwdPicker.kt` | `feature/chat/components/ai/WorkspaceCwdPicker.kt` |
| `core/ui/components/ai/WorkspaceSelectSheet.kt` | `feature/chat/components/ai/WorkspaceSelectSheet.kt` |
| `core/ui/components/ai/MessageQueuePanel.kt` | `feature/chat/components/ai/MessageQueuePanel.kt` |
| `core/ui/components/ai/ExtensionContent.kt` | `feature/assistant/components/ai/ExtensionContent.kt` |
| `core/ui/components/ai/FilesPicker.kt` | `feature/chat/components/ai/FilesPicker.kt` |
| `core/ui/components/ui/ShareSheet.kt` | `feature/settings/components/ui/ShareSheet.kt` |
| `core/ui/components/ui/JsonTree.kt` | `feature/log/components/ui/JsonTree.kt` |
| `core/ui/components/ui/Export.kt` | `feature/extensions/components/ui/Export.kt` |
| `core/ui/components/ui/TagList.kt` | `feature/assistant/components/ui/TagList.kt` |
| `core/ui/components/ui/QrScannerSheet.kt` | `feature/settings/components/ui/QrScannerSheet.kt` |
| `core/ui/components/ui/TextArea.kt` | `feature/assistant/components/ui/TextArea.kt` |
| `core/ui/components/ui/UpdateCard.kt` | `feature/chat/components/ui/UpdateCard.kt` |
| `core/ui/components/ai/completion/WorkspaceCompletionProvider.kt` | `feature/chat/components/ai/completion/WorkspaceCompletionProvider.kt` |
| `core/ui/components/ai/SearchPicker.kt` | `feature/chat/components/ai/SearchPicker.kt` |
| `core/ui/components/ui/BackupReminderCard.kt` | `feature/chat/components/ui/BackupReminderCard.kt` |
| `core/ui/components/ai/ChatInput.kt` | `feature/chat/components/ai/ChatInput.kt` |

其中 `KkScrollEdgeFade` 与 `MorphPolygonShape` 在下放后发现 core 消费者（MochiShell / ThemePickers），按"core 不反向依赖 feature"回迁：
`core/ui/components/ui/KkScrollEdgeFade.kt`、`core/ui/theme/MorphPolygonShape.kt`（最终仍在 core，消费者即证据）。

settings 的 workflow/automation 源另归真实功能：`settings/CreateWorkflowPage.kt` → `workflow/CreateWorkflowPage.kt`、
`settings/TriggerCardEditor.kt` → `automation/TriggerCardEditor.kt`、`settings/data/WorkflowPreferencesRepository.kt` →
`workflow/data/`、`settings/helper/WorkflowTextHelper.kt` → `workflow/helper/`；SettingTriggersPage 仅留设置入口壳。

## 4. t35–t38 遗留引用集成（本批闭合）

- 失效 import/符号 106 条（36 文件）清零；import 重写覆盖 `feature.chat.*`、`feature.automation.*`、`feature.workflow.*`、
  `feature.workspace.*`、`feature.balance/lanshare.*`、`core.*` 的旧 FQN。
- 调用点符号改名：`TriggerController→TriggerManager`、`TriggerExactAlarmScheduler→TriggerAlarmHelper`、
  `TriggerTilePublisher→TriggerTileHelper`、`TriggerSettings→TriggerSettingsRepository`、`AutomationBus→AutomationManager`
  （其嵌套 `AutomationStatus/ApprovalRequest/ApprovalCategory` 解包为 `automation.data` 顶层类型）、
  `WorkflowEventBus→WorkflowEventManager`；workflow 页面改调 `executeWorkflow/notificationClassifySample/
  WorkflowDefinition.toVisualWorkflow()/VisualWorkflow.toStoredNodes()`。
- 测试/DI/平台入口同步：`app/src/test`（chat/balance/tools/workflow）、`app/src/androidTest` 9 文件（device tests 的
  ChatManager/GroupTurnCoordinator/writeExportTempFile/GROUP_EXPORT_MIME_TYPE/AutomationStatus 等）、`core/di` 3 文件、
  `core/network/routes` 4 文件；未改 manifest 组件名/authority/Worker FQCN。
- 删掉 add-pass 误加的成员型 import（`PartData.FileItem`、`Avatar.Emoji`、`AutomationTracer.UiActionDto` 等 15 条），
  避免"限定名用法补 import"的编译错误。

## 5. 保留 core 的具体消费者证据 / 未闭合

- **core 保留**（t27 no-touch/keep，有消费证据）：`ModelList/AvatarEditSheet/UIAvatar/ChartCard/BuiltinToolUIs/AssistantPicker` 等被 2+ feature 或 core 调用链使用（共享提升的 M01–M04 亦同）；
  `core/network` 全部仍服务 web 路由（17 文件），无单 feature 独占证据。
- **平台/运行时桥**（保留 core→feature 引用，消费者具体）：`KhatKitTools` 的 `ScheduleBridgeHelper/BusApprovalHelper`
  （automation 运行时）、`ChatManager/WorkflowRepository/WorkspaceTerminalSessionManager/TriggerManager`
  （DI/routes/EntryPoint 的既有注册点）、`PermissionChecklist→KhatKitAccessibilityService`（Android 组件）。
- **合法残留**：`SplashIntro` 的 `import feature.splash.SplashGlyph.drawSplashGlyph` 是 object 成员扩展函数的导入
  （SplashGlyph.kt:201），不是失效 FQN；静态扫描器按顶层声明索引会误报，保留。
- **未闭合**：未运行 Gradle/JVM/Lint（verifier 独占）；t27 的 "no app/main 触点" 文件未删（未证实停用）；
  `TavernGroupImportGateTest.kt` 的并发改动非本批（mtime 命中也保留原样）。

## 6. 静态验证

- `git diff --check`：exit 0。
- app 内 first-party import：失效 0（除 §5 合法成员 import）；同名重复 import 行 0；同包重复顶层声明实为函数重载（0 真冲突）。
- 结构回归：本会话 touched 文件 `{}`/`()` 平衡异常 0（唯一的 `TavernGroupImportGateTest.kt` 为并发他批改动，未触碰）。
- 未改：Room/DataStore/@SerialName/wire、Worker FQCN、manifest 组件、全部 `R.string` 与既有 WIP。
