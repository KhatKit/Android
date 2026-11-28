# core/data 功能归属与反向依赖收口（t38）

> 输入：`/tmp/khatkit-data-auditor-ownership.md`（t26，HEAD 2ad2ae270，217 文件 / 1058 import / 14 条 core→feature）与 t35–t37 的新路径。
> t38 历史结论：原审计的 14 条边中收口后保留 3 条；该数字不等于全 `core/data` 当前反向依赖总量。
> 2026-11-29 交接复审已补全范围：A 为 **7 条 import + 1 处生产全限定引用**，
> 同树 B 另加 `BackgroundAppSessionManager` 一条 import，合计 **8 + 1**，见 §2。
> 其余归属按 t26 逐路径索引执行 MOVE/SPLIT/KEEP，无功能删减、无复制空壳。
> 未运行 Gradle/测试/设备；最终编译/JVM/Lint 由 t45 执行。

## 1. 已执行迁移（21 个 MOVE + 1 个 SPLIT）

| 旧路径 | 新路径（owner） |
|---|---|
| core/data/ai/tavern/TavernChatCodec.kt | feature/chat/data/TavernChatCodec.kt |
| core/data/ai/tools/ChatToolFactory.kt | feature/chat/helper/ChatToolFactory.kt |
| core/data/favorite/FavoriteAdapter.kt | feature/favorite/data/FavoriteAdapter.kt |
| core/data/favorite/NodeFavoriteAdapter.kt | feature/favorite/data/NodeFavoriteAdapter.kt |
| core/data/repository/FavoriteRepository.kt | feature/favorite/data/FavoriteRepository.kt |
| core/data/oauth/ProviderAccountManager.kt | feature/settings/data/oauth/ProviderAccountManager.kt |
| core/data/oauth/AccountOAuthSession.kt | feature/settings/data/oauth/AccountOAuthSession.kt |
| core/data/sync/S3Sync.kt + s3/S3Client.kt + s3/AwsSignatureV4.kt | feature/backup/data/remote/ + remote/s3/ |
| core/data/sync/webdav/WebDavClient.kt + WebDavSync.kt | feature/backup/data/remote/webdav/ |
| core/data/sync/importer/ChatboxImporter.kt + CherryStudioProviderImporter.kt | feature/backup/data/importer/ |
| feature/extensions/TerminalPluginInfo.kt | core/data/terminal/TerminalPluginInfo.kt（数据 owner 上移；3 个 core 反向 import 闭合） |
| feature/automation/data/AutomationModels.kt | core/data/automation/AutomationModels.kt（ApprovalCategory/Policy/Status/ApprovalRequest 合同） |
| feature/automation/data/AutomationTraceModels.kt + AutomationTraceRepository.kt + helper/AutomationTraceManager.kt | core/data/automation/（卡片 runtime 与审计 UI 共用） |
| feature/automation/data/CardScheduleRepository.kt | core/data/automation/（jobId/JSON schema 不变） |
| feature/automation/data/UiAutomationSnapshot.kt | core/data/automation/（DTO 与无障碍树 schema 不变） |
| core/data/export/ExportSerializer.kt | SPLIT：泛型 ExportSerializer/ExportHooks 留 core；ModeInjection/Lorebook 及 SillyTavern DTO → feature/extensions/data/export/PromptExportSerializers.kt |

新增装配（feature 单向依赖 core）：
- `feature/favorite/di/FavoriteHiltModule.kt`（FavoriteRepository provider 从 core/di/RepositoryHiltModule 迁出）
- `feature/backup/di/BackupSyncHiltModule.kt`（WebDavSync/S3Sync provider 从 core/di/DataSourceHiltModule 迁出）
- `feature/chat/di/ChatAssemblyModule.kt`（ChatToolFactory + ChatManager provider 从 core/di/AppHiltModule 迁出）
- `feature/chat/di/ChatGatewayModule.kt` + core/data/ai/tools/CardChatGateway.kt（core 端口，@Binds 由 feature 实现）
- 所有旧包 import 已全仓改写（app main/test/androidTest），同名旧 FQCN 残留 0（S3Config 按 t26 仍留 core）。

## 2. 反向依赖（t38 历史映射与交接全量复算）

| # | 原边 | 处理 |
|---|---|---|
| 1–3 | core/data/terminal/{Repository,Hub,Installer} → feature.extensions.TerminalPluginInfo | 常量上移 core/data/terminal，闭合 |
| 4 | TavernChatCodec → feature.chat.data.GroupSpeakerResolver/GroupTurnCoordinator | 文件迁 feature/chat/data，闭合 |
| 5 | ChatToolFactory → feature.chat.helper.SubagentTeamManager | 文件迁 feature/chat/helper，闭合 |
| 6–13 | KhatKitTools → automation 的 ApprovalCategory/AutomationBus/Tracer/BusApprovalGate/UiSnapshotProvider/CardScheduleRepository/ScheduleBridgeImpl | 纯数据/合同迁 core/data/automation（ApprovalCategory/Policy/Status/Request、trace 三件套、CardScheduleRepository、UiAutomationSnapshot）；其余运行时对象见下 |
| 14 | KhatKitTools → feature.chat.ChatManager | 改 `CardChatGateway` 端口 + EntryPoint `chatGateway()`，ChatManager 实现并由 ChatGatewayModule @Binds，闭合 |

**保留的 3 条（具体耦合证据，不是“以后再处理”）**：`KhatKitTools.kt:28-29,47` 仍 import
`feature.automation.helper.{AutomationManager, BusApprovalHelper, ScheduleBridgeHelper}`：
- `AutomationManager` 是 object 状态总线，直接启动/隐藏 `AutomationOverlayService`、发审批通知（`NotificationChannel automation_approval`、`ApprovalActionReceiver` action）——属平台/通知 UI 宿主桥，迁 core 会把 Service/通知栈拖进 core；
- `BusApprovalHelper` 是 bridge `ApprovalGate` 到该管理器的同步适配（默认 `AutomationManager` 单例），与上面同一生命周期；
- `ScheduleBridgeHelper` 是 `schedule` bridge 的 WorkManager 实现，必须引用平台 Worker `CardScheduleWorker` 的 FQCN/tag（已存框架 FQCN 不改），Worker 属 automation 平台组件。
这三者保持 feature 归属符合任务「工具/自动化注册保持功能与协议」；core 只保留其数据与合同。

core/ui 仍有多条 core→feature（如 ChatError/ChatViewModel、PermissionChecklist→AutomationBus 等），属 core/ui 批次（本任务 inScope 之外），未在本批改动，t26 清单外新增项由后续批次按同一规则处理——本文件不把它们当共享证据。

### 2026-11-29 全量复算（覆盖上述「3 条」的总量表述）

`UiAutomationSnapshotHelper` 已从 `feature/automation/helper` 迁至 `core/data/automation`，
与模型同层；`KhatKitTools` 引用改为共享包。全量生产代码仍保留以下反向引用，**均记录为耦合，
不是因是运行时装配点就自动符合单向依赖**：

| 位置 | feature 目标 | 条数/证据 |
|---|---|---|
| `KhatKitTools.kt` import | `lanshare.helper.LanShareCardBridge`、`LanShareCardEntryPoint` | 2；卡片调用 LAN 会话和文档挂载桥 |
| 同上 | `yunx.helper.YunxCardBridge`、`YunxMediaDownloadHandler` | 2；YunX 卡片运行时及媒体下载适配 |
| 同上 | `automation.helper.AutomationManager`、`BusApprovalHelper`、`ScheduleBridgeHelper` | 3；看板/审批/WorkManager 桥，历史保留项 |
| 同上（B） | `automation.helper.BackgroundAppSessionManager` | 1；本次虚拟屏会话，独立于 A 的既有迁移 |
| `KhatKitTools.kt` `apiHealth` 初始化 | `feature.tools.data.ToolApiHealthRepository` | 1 个生产全限定引用；工具健康记录 |

复算命令（注释/KDoc 中的类型链接不算生产边）：

```bash
rg -n '^import .*feature\.' app/src/main/java/heizige/kk/khatkit/app/core/data
rg -n 'heizige\.kk\.khatkit\.app\.feature\.' app/src/main/java/heizige/kk/khatkit/app/core/data
```

第二条输出需排除第一条的 import，以及 `AutomationModels.kt`、`CardChatGateway.kt` 的 KDoc 链接。
当前 A 不含 B 时为 7 import + 1 FQ；包含 B 的同树为 8 import + 1 FQ。本轮没有宣称 core→feature 归零。

## 3. KEEP（有共享证据）

按 t26 索引：`datastore/SettingsRepository + Settings`、`db/**`、`model/**`、`repository/{Conversation,Memory,RoleMemory,Workspace,Folder,GenMedia,Files}`、`ai/**`（generation/transformers/MCP/session/KhatKitToolProvider）、`files/**`、`network/ProxyConfig`、`oauth/ProviderAccountCredentialProvider`（AI 请求认证路径）、插件 `*PluginRepository/Hub/Installer`（extensions/explore/settings/chat 与卡片 runtime 共用）、`sync/{BackupManager,PendingRestore,DatabaseBackup,S3Config}`（KhatKitApp 启动恢复、Settings 持久化 S3Config）。逐路径 217 项结论沿用审计索引；本批仅对上述 22 个 MOVE/SPLIT 路径改变归属。

浏览器候选（t26 “无生产调用”）：`BrowserWebViewBridge/BrowserDomSnapshot/BrowserHistoryRepository` **保留**——`BrowserArchive` 仍解码 `BrowserHistoryEntry`（browser-archive.db/browser_history 活数据），bridge 可能被 WebView/反射使用，静态删除无运行时证据；保留不等于共享证据，待 t45/t33 运行期确认后再决定删除，未改功能。

## 4. 兼容不变量（未改）

- DataStore 文件名/key、`Settings`/DB 表列/SQL/migration、`@SerialName`/JSON discriminator、Favorite persisted value/refKey schema；
- `oauth_accounts` SharedPreferences、`account_<vendor>`、StoredCredential 字段；
- 插件 prefs（ocr_plugin/ssh_plugin/terminal_plugin、installed key、record JSON）、`filesDir/plugins/*`；
- WorkManager unique name/tag、jobId、审批通知 action、CardScheduleJob JSON、UiAutomationSnapshot schema；
- 全部 R.string/资源 key 与既有 WIP、平台组件 FQCN 未改。

## 5. 跨批映射与验证

- t35/t36/t37 新路径已作为本批 MOVE 的起点（TavernChatCodec→chat/data、automation→data/helper、settings 子包等）。
- 静态校验（未跑 Gradle）：package 与目录一致 25/25；旧 FQCN 0 残留；Hilt provider 无重复（favorite/sync/chat 各 1 处）；`git diff --check` exit 0。
- t45 需跑：`:app:compileDebugKotlin`、`:app:testDebugUnitTest`、Hilt 图（新增 4 个 module + CardChatGateway @Binds）、androidTest import 同步、备份/收藏/账号/自动化回归。

