# settings 及其余 feature 内聚与职责分层（t37）

依据：t28 审计 [/tmp/khatkit-reviewer-ownership.md（M33–M54、E01/E02）] 与 t26/t27 归属结论；本文件只记录本批**实际落地**的迁移、外部符号清单与未闭合项。M01–M06 共享提升、M31/M32 settings→workflow/automation 跨源移动留最后 core 批次，本次未动。

## 1. 落地映射（旧 → 新，包路径相对 `app/src/main/java/heizige/kk/khatkit/app/feature/`）

| ID | 旧路径/符号 | 新落点 |
|---|---|---|
| M34 | `settings/data/SearchServiceOrder.kt`（replacingSearchServices / add-move-removeSearchRepository） | `settings/helper/SearchServiceExtensions.kt`；函数更名 add/move/removeSearchService |
| M35 | `settings/SettingPreferencesUIFontPage.kt` 的 ImportedChatFont / CustomFontMimeTypes / CustomFontExtensions | `settings/data/ImportedChatFont.kt` |
| M35 | 同文件 importCustomChatFont / replaceCustomChatFont / deleteCustomChatFont | `settings/data/ChatFontRepository.kt` |
| M36 | `settings/SettingMemoryPage.kt` 的 SettingMemoryViewModel | `settings/SettingMemoryViewModel.kt`（包不变） |
| M36 | `settings/SettingAboutUpdate.kt` 的 AboutUpdateState | `settings/data/AboutUpdateState.kt` |
| M36 | `settings/SettingLeafIndex.kt` 的 SettingSearchEntry | `settings/data/SettingSearchEntry.kt`（filteredSettingResults / supportingLine / leafIcon 留原文件） |
| M37 | `settings/SettingPreferencesThemePage.kt` 的 parseHslCode / parseHslPercentOrFraction / formatHslCode / hslNumberRegex | `settings/helper/ThemeColorExtensions.kt` |
| M37 | `settings/components/ProviderConfigure.kt` 的 convertTo / defaultBaseUrlForReset / resetBaseUrlToDefault / isUsingDefaultBaseUrl / convertToTargetBaseUrl / convertToTargetPath / normalizePath / isValidBaseUrl / OFFICIAL_PROVIDER_HOSTS | `settings/helper/ProviderSettingExtensions.kt`（Composable 与文件选择回调留 components） |
| M38 | `stats/StatsVM.kt`（UsageRange/UsageStats/AppStats、class StatsVM、DAO 读取） | `stats/StatsViewModel.kt` + `stats/data/StatsModels.kt` + `stats/data/StatsRepository.kt`（DAO/SQL/时间窗下沉，VM 只持加载态） |
| M39 | `favorite/FavoriteVM.kt` 的 NodeFavoriteListItem；class FavoriteVM | `favorite/data/NodeFavoriteListItem.kt` + `favorite/FavoriteViewModel.kt` |
| M39 | `history/HistoryVM.kt` class HistoryVM | `history/HistoryViewModel.kt` |
| M40 | `imggen/ImgGenViewModel.kt` 的 GeneratedImage | `imggen/data/GeneratedImage.kt` |
| M40 | 同文件 GenMediaEntity.toGeneratedImage | `imggen/helper/GeneratedImageHelper.kt` |
| M43 | `tools/ToolCatalogueViewModel.kt` 的 ToolCatalogueState | `tools/data/ToolCatalogueState.kt` |
| M43 | `tools/helper/ToolGridZoom.kt` class ToolGridZoom | `tools/helper/ToolGridZoomManager.kt`（类名同步） |
| M43 | `tools/helper/ToolShortcutHelper.kt` token 读写 | `tools/data/ToolShortcutTokenRepository.kt`（prefs 名 tool_shortcuts / key / token 生成保持不变；pin/resolve 系统短路适配留 helper） |
| M44 | `tasks/CardRunsPage.kt` 顶层 isAvailable / fileOrNull | `tasks/helper/CardArtifactHelper.kt` |
| M44 | `tasks/ZZ_TEMP_BUILD_SHIM_Plus.kt` 临时 plus 垫片 | 先给 CardRunsPage 补 `core.util.plus` import，再删除垫片 |
| M45 | `lanshare/LanShareManager.kt`（LanShareState、class LanShareManager） | `lanshare/data/LanShareState.kt` + `lanshare/helper/LanShareManager.kt` |
| M45 | `lanshare/LanShareCardBridge.kt` | `lanshare/helper/LanShareCardHelper.kt`（类名/桥接口名不变） |
| M45 | `lanshare/data/LanShareSession.kt` | `lanshare/helper/LanShareSessionManager.kt`（类名不变，文档映射/HTTP 挂载归 Manager） |
| M46 | `workspace/WorkspaceDetailViewModel.kt` 的 WorkspaceDetailState / WorkspaceTerminalState / WorkspaceTerminalEntry | `workspace/data/WorkspaceDetailState.kt` |
| M46 | `workspace/data/WorkspaceTerminalSessionManager.kt` + TabsState/Tab/Readiness | `workspace/helper/WorkspaceTerminalSessionManager.kt` + `workspace/data/WorkspaceTerminalModels.kt` |
| M46 | `workspace/data/WorkspaceFileType.kt`（含 detectFileType 扩展） | `workspace/helper/WorkspaceFileExtensions.kt` |
| M46 | `workspace/data/WorkspaceTerminalSession.kt`（create/prepare/rootfsReady + 两个 Client） | `workspace/helper/WorkspaceTerminalHelper.kt`（函数） + `workspace/helper/WorkspaceTerminalClient.kt`（SessionClient/ViewClient/URL 常量/DNS 辅助） |
| M47 | `yunx/YunxCardBridge.kt` | `yunx/helper/YunxCardManager.kt`（@Keep/bindCard/call 名不变，第三方 com.yunx 未动） |
| M47 | `yunx/YunxMediaDownloadHandler.kt` | `yunx/helper/YunxMediaDownloadHelper.kt` |
| M48 | `search/SearchViewModel.kt` 的 MessageSearchScope / SearchRequest | `search/data/SearchModels.kt` |
| M48 | 同文件 SORT_ORDER_PREF_KEY 与排序偏好读写 | `search/data/SearchPreferencesRepository.kt`（key 与 enum.name 落盘格式不变） |
| M49 | `backup/BackupViewModel.kt` 的 ChatboxRestoreResult | `backup/data/ChatboxRestoreResult.kt` |
| M52 | `balance/data/ProviderBalanceRepository.kt` 的 BalanceReading | `balance/data/BalanceReading.kt` |
| M52 | 同文件 LowBalanceEpisodes / BalanceQuery / usageField / isUsageOnly / thresholdAmount / isLow | `balance/helper/BalanceQueryManager.kt` |
| E01 | `explore/McpInstallPlan.kt` 的 McpInstallPlan / McpCardStatus | `explore/data/McpInstallModels.kt` |
| E01 | 同文件纯判定/脱敏函数与私有常量 | `explore/helper/McpInstallExtensions.kt`（原 McpInstallPlan.kt 已无残留声明，文件删除） |

## 2. 内部引用与测试同步

- 全部 feature 内调用点由脚本按「旧包符号 → 新包符号」补 import/改 import；settings、stats、favorite、history、imggen、search、backup、balance、lanshare、workspace、yunx、tasks、tools、explore 的同包引用均同步。
- 测试：`app/src/test/.../feature/settings/SettingSearchFilterTest.kt`（SettingSearchEntry）、`ProviderConfigureConvertToTest.kt`（convertTo 走 helper 包）已随迁移同步。
- 已复核无残留旧 import（`grep 'import ...<旧包>.<旧符号>'` 只剩下表外部文件）。

## 3. 外部调用者符号更新清单（本批不改，交最终引用集成任务）

| 文件:行 | 现状 | 需要 |
|---|---|---|
| `core/data/ai/tools/KhatKitTools.kt:240` | `feature.lanshare.LanShareCardBridge(...)` 全限定 | → `feature.lanshare.helper.LanShareCardBridge` |
| `core/data/ai/tools/KhatKitTools.kt:512` | `feature.yunx.YunxMediaDownloadHandler()` | → `feature.yunx.helper.YunxMediaDownloadHandler` |
| `core/data/ai/tools/KhatKitTools.kt:527` | `feature.yunx.YunxCardBridge(appContext)` | → `feature.yunx.helper.YunxCardBridge` |
| `core/di/AppEntryPoint.kt:28,70` | import/返回 `feature.workspace.data.WorkspaceTerminalSessionManager` | → `feature.workspace.helper.WorkspaceTerminalSessionManager` |
| `core/di/AppHiltModule.kt:40,147` | 同上 + 构造调用 | → 同上 |
| `app/src/androidTest/.../feature/yunx/YunxUiDeviceTest.kt` | 引用 YunxCardBridge | → helper 包（androidTest 不在本批 inScope） |

`core/ui/components/ai/ProviderBalanceText.kt:31` 仅 import data.BalanceReading（未移动，无需改）；其文件内 isLow/thresholdAmount 字样为普通词匹配，不是本批符号。

## 4. 保持不变的兼容契约

- 偏好/存储：`search_page_sort_order`、`tool_shortcuts`、`provider_balance_alerts` 名称与 key 不变；SearchService 选中项仍按 **service id 重绑**、下标语义不变。
- Worker/平台组件：`CardScheduleWorker` 等 WorkManager 持久化 FQCN、各 Service/Activity/Receiver、DocumentsProvider 包名与 authority 未动（M53/M54）。
- 业务契约：McpInstall「必填缺失不得 enable」、密钥脱敏、Chatbox 导入计数、Workspace 终端 URL/DNS 行为、Yunx bridge 方法名、GeneratedImage 序列化字段均保持。
- 资源化：此前 app 文案 R 引用、导航 R 引用未回退；本批没有新增/删除字符串资源。所有既有未提交 WIP 保留（未 checkout/覆盖任何 WIP 文件；仅对 balance 仓库文件用 `git checkout --` 恢复了本次误删前同一 index 状态）。

## 5. 未闭合项（证据，不是"以后再做"）

| 项 | 现状证据 |
|---|---|
| M33 ProxyTestState | `settings/data/ProxyTestState.kt` 全 app/src（main/test/androidTest）零引用；页面 `SettingPreferencesNetworkPage.kt` 用内联状态。审计 O2 明确「不要移动死文件制造分层完成」，删除需 captain 裁定；本批保留原位。 |
| M37 ProviderConnectionTester | 测试流程直接写 Compose `UiState`/`rememberCoroutineScope` 并调 `providerManager.getProviderByType(...).generateText`（行 92–227），拆 helper 必须引入结果模型+回调，属行为面重构；本批只迁了同文件体系的纯 HSL/ProviderSetting 部分。 |
| M37 ProviderCredentialImport | Google 服务账号 JSON 导入内联在 `ProviderConfigureGoogle` Composable（Toast/stringResource/uri launcher），Context/Compose 边界本身即 UI；未造 helper。 |
| M40 图片落盘 | `saveImagePreview`(292)/`deleteReferenceFiles`(368) 是 VM 私有成员，读写 job 状态与 FilesManager；非顶层可迁。 |
| M44 artifact 其余 | `openArtifact/shareArtifact/artifactFileUri` 是行 Composable 内局部函数（`CardRunsPage.kt`），仅顶层 isAvailable/fileOrNull 可安全外提。 |
| M46 Client 常量 | `WorkspaceTerminalClient.kt` 已含 URL_REGEX/URL_TRAILING_TRIM/activeDnsServers；`WorkspaceTerminalHelper.kt` 保留 create/prepare/rootfsReady 三函数。 |
| M49 Chatbox 导入流程 | `BackupViewModel.restoreFromChatBox`(~117–245) 依赖 VM settings 流、conversationRepository、filesManager 与计数回写；外提要参数化 4 个依赖并返回结果，超出本批最小改动，结果模型已下沉。 |
| M50 onboarding | `GreetingPage.kt:973–1047` 的私有一行扩展紧贴 AiSetup 步骤状态；审计标「可选」，本批未强拆。 |
| M51 debug | `DebugViewModel` 的 createOversizedConversation/randomMessageText 是类成员（依赖 repository/VM 状态），无顶层可迁点；审计标「可选」。 |
| E02 市场安装流程 | `ExploreMarketViewModel.kt:386–558` 的 applyResourcePackage/installResource/saveMcpServerConfig 持 busy/job/回调与 repository 副作用，外提需带 4–5 个依赖与原子安装顺序校验；本批只落 E01 纯模型/判定。 |
| M53/M54 | Worker FQCN 与平台组件按合同保留原包名，仅更新其引用的内部实现。 |

## 6. 验证

- `git diff --check` → 退出 0（见任务 commandsRun）。
- `grep` 复核：无残留旧包 import；无文件以注解行结尾（脚本扫描 0 命中）；无空残留 .kt。
- 未运行 Gradle/assemble/设备测试（由 verifier/最终验证独占）；本报告不构成编译或运行时通过结论。
