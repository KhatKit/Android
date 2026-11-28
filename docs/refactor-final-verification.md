# 最终验证报告（t53 round-3，隔离副本）

> 结论：**未通过**。3 条 Gradle（compile / app 单测 / 全仓 test）exit 0，**lint exit 1**（112 个 Error，全部为
> Compose `LocalContext` 资源读取类检查），故不满足「四条 Gradle 全部 exit 0」。资源类 lint 错误已清零
> （t55 生效），本次阻断全部是 **非资源类** 且在源码侧可修。本任务只验证，未改任何源码。

## 1. 快照与冻结

| 项 | 值 |
|---|---|
| 源树 HEAD | `cfc129eba3593b8f08a7a0c3552c2c18fd3eda7e` |
| 源树 dirty（复制时） | 818 |
| 源树 status 哈希（复制时） | `b8aaa3eecbd55c0d4f0f6fc9c176d4b4965c5c1be568f35117b96d0bd48fc15e` |
| 复制时间 | 2026-11-28T21:41:31+08:00 |
| 验证副本 | `/home/heizige/.verify/khatkit-verify-20261128-214129`（190M，rsync 排除 .git/build/.gradle/.kotlin/target/.idea/.agents） |
| 源树运行后 | dirty 826、status 哈希 `811514ed…`、HEAD 不变 → **源树 FROZEN=no**（运行期间被外部写入 8 条） |
| 副本 FROZEN | **yes**：`find <copy>/ -path '*/src/*' -newermt '2026-11-28 21:41:40'` 为空；根 `*.kts/*.properties/*.toml` 与 `gradle/` 亦无变更 |

说明：`/tmp` 在本机是 7.8G tmpfs（RAM），构建产物约 1.2–1.8G、Gradle daemon 4G 堆，与当时 6G 可用内存相冲突，
故副本放在磁盘 `/home/heizige/.verify/`（隔离性与「绑定同一内容」不变；路径已记录）。副本无 `.git`，
`git diff --check` 在源树只读执行（exit 0）。

## 2. 命令与真实退出码（副本内串行）

日志目录 `/tmp/t53v2/`。

| 命令 | 退出码 | 耗时 | 日志 | 摘要 |
|---|---:|---:|---|---|
| `./gradlew --console=plain :app:compileDebugKotlin` | **0** | 79s | `/tmp/t53v2/compile.log` | BUILD SUCCESSFUL，143 tasks executed |
| `./gradlew --console=plain :app:testDebugUnitTest` | **0** | 32s | `/tmp/t53v2/apptest.log` | BUILD SUCCESSFUL，166 个 app 测试类结果 |
| `./gradlew --console=plain test` | **0** | 13s | `/tmp/t53v2/test.log` | BUILD SUCCESSFUL，全仓 1991 tests / 0 failed / 21 skipped |
| `./gradlew --console=plain :app:lintDebug` | **1** | 613s | `/tmp/t53v2/lint.log` | `:app:lintDebug` "Lint found errors in the project"（112 Error） |
| `git diff --check`（源树） | **0** | <1s | `/tmp/t53v2/diffcheck.log` | 无空白错误 |

资源合并已通过（`packageDebugResources`/`generateDebugRFile` 在 compile 段成功），t45 的重复键阻断确认解除。

## 3. lint 阻断（file:line 级，全部非资源类）

规则分布：`LocalContextGetResourceValueCall` **111** + `LocalContextConfigurationRead` **1**（`SettingPage.kt:409`）。
资源类规则为零：`ExtraTranslation` 0、`StringFormatMatches` 0、`MissingQuantity` 0、`MissingDefaultResource` 0。
`MissingTranslation` 1823 与 `UnusedResources` 239 均为 **Warning**（非 Error，不阻断）。

归因方法：把每条 Error 的 worktree 行号对照 `git diff -U0` 的新增行集合 → **WIP 新增行 92 条**、**HEAD 已有行 20 条**。
批次按文件所属功能包归类（文件级，非逐行 git blame）：

| 批次 | 新增行错误 | 既有行错误 |
|---|---:|---:|
| t4/t5 设置批（`feature/settings/**`） | 55 | 9 |
| t6 会话/助手/图像/翻译批（`feature/chat|assistant|imggen|translator`） | 17 | 0 |
| t8 后台批（`feature/debug|lanshare|backup`） | 10 | 2 |
| t9 共享界面批（`core/ui/**`） | 3 | 2 |
| t7/t37 扩展/工作流/工作区批 | 6 | 5 |
| t7 自动化批（`feature/automation`） | 0 | 2 |
| **合计** | **92** | **20** |

错误形态与修法（交 repair 轮，本任务不代修）：

1. `@Composable` 直接读：`val context = LocalContext.current` + `context.getString(R.string.x)` → 改用 `stringResource(R.string.x)`。
2. 在 `scope.launch {}` / 内联回调里读（如 `DebugPage.kt:125`、`ChatPage.kt:512`）：在 composable 作用域先把文案
   `stringResource(...)` 解析成 `val`，回调内只用该 `val`。
3. 非 composable 的内层类/函数（如 `SettingAboutTerminalPage.kt:149-156` 的终端命令执行器）：
   改用本仓已引用的 `androidx.compose.ui.platform.LocalResources.current`（`ChatAttachmentPicker.kt:51`、
   `ProxyTestState.kt:29` 已有先例），或由 composable 侧注入已解析文案。
4. `LocalContext.current.resources.configuration` → `LocalConfiguration.current`（`SettingPage.kt:409`）。

非 Composable 的 VM/Service/Worker 的 `context.getString` 不受该 lint 影响，不要误改。20 条既有行同批顺带修，避免下一轮复现。

完整 112 条（`文件:行`）：

```
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingPage.kt:409: Error: Reading Configuration using LocalContext.current.resources.configuration is not configuration-aware and may return stale values if the Configuration changes [LocalContextConfigurationRead from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/ASRProviderConfigure.kt:758: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/ASRProviderConfigure.kt:840: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatPage.kt:512: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatPage.kt:751: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatSkillSheet.kt:52: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatSkillSheet.kt:79: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/chat/ConversationExport.kt:266: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/chat/ConversationExport.kt:283: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/chat/ConversationExport.kt:287: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/chat/ConversationExport.kt:287: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/workflow/CreateWorkflowPage.kt:66: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/workflow/CreateWorkflowPage.kt:102: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/workflow/CreateWorkflowPage.kt:109: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/workflow/CreateWorkflowPage.kt:109: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/workflow/CreateWorkflowPage.kt:228: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/debug/DebugPage.kt:125: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/debug/DebugPage.kt:125: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/debug/DebugPage.kt:188: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/debug/DebugPage.kt:189: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/debug/DebugPage.kt:190: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/debug/DebugPage.kt:234: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/debug/DebugPage.kt:244: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/chat/GroupExportCard.kt:64: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/chat/GroupExportCard.kt:93: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/chat/GroupExportCard.kt:99: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/chat/GroupExportCard.kt:100: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ai/ImageCropDialog.kt:92: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ai/ImageCropDialog.kt:266: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/ImagePreviewDialog.kt:71: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/ImagePreviewDialog.kt:78: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/imggen/ImgGenPage.kt:762: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/backup/tabs/ImportExportTab.kt:169: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/backup/tabs/ImportExportTab.kt:175: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/lanshare/LanSharePage.kt:55: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/lanshare/LanSharePage.kt:91: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/lanshare/LanSharePage.kt:134: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ai/ProviderBalanceText.kt:64: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/ProviderConfigure.kt:361: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/ProviderConfigure.kt:363: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/assistant/detail/RoleMemoryPanel.kt:114: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/assistant/detail/RoleMemoryPanel.kt:127: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/assistant/detail/RoleMemoryPanel.kt:131: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/assistant/detail/RoleMemoryPanel.kt:164: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutPage.kt:126: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutPage.kt:131: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutPage.kt:132: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:149: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:152: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:153: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:154: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:155: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:156: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:157: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:158: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:161: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:162: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:163: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:166: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:167: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:168: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:171: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:172: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:174: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:175: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutTerminalPage.kt:178: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingDonatePage.kt:88: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingDonatePage.kt:88: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingDonatePage.kt:91: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingDonatePage.kt:102: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingDonatePage.kt:102: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingDonatePage.kt:104: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingDonatePage.kt:109: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingDonatePage.kt:138: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingDonatePage.kt:141: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingPage.kt:548: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingPreferencesUIFontPage.kt:96: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingPreferencesUIFontPage.kt:97: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingPreferencesUIFontPage.kt:98: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingPreferencesUIFontPage.kt:99: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingTriggersPage.kt:346: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingTriggersPage.kt:348: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingTriggersPage.kt:384: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingTriggersPage.kt:385: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingTriggersPage.kt:386: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingTriggersPage.kt:392: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingTriggersPage.kt:393: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingTriggersPage.kt:394: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/extensions/SshPluginPage.kt:155: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/extensions/SshPluginPage.kt:160: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/extensions/SshPluginPage.kt:178: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/TTSProviderConfigure.kt:38: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/TTSProviderConfigure.kt:39: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/TTSProviderConfigure.kt:40: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/TTSProviderConfigure.kt:41: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/TTSProviderConfigure.kt:43: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/TTSProviderConfigure.kt:44: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/TTSProviderConfigure.kt:45: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/TTSProviderConfigure.kt:47: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/TTSProviderConfigure.kt:54: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/TTSProviderConfigure.kt:55: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/TTSProviderConfigure.kt:56: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/TTSProviderConfigure.kt:57: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/TTSProviderConfigure.kt:60: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/TTSProviderConfigure.kt:61: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/TTSProviderConfigure.kt:62: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/translator/TranslatorPage.kt:91: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/automation/TriggerCardEditor.kt:116: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/automation/TriggerCardEditor.kt:116: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/workspace/WorkspaceFileEditorPage.kt:86: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/workspace/WorkspaceFileEditorPage.kt:116: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
app/src/main/java/heizige/kk/khatkit/app/feature/workspace/WorkspaceFileEditorPage.kt:118: Error: Querying resource values using LocalContext.current is not configuration-aware and may return stale values if the Configuration changes [LocalContextGetResourceValueCall from androidx.compose.ui]
```

## 4. 9 项历史单测失败逐条复核（本副本实跑）

| # | 测试类（历史失败项） | 结果 |
|---|---|---|
| 1–3 | `BrowserExampleCardsTest` | tests=3 / failures=0 / errors=0 / **skipped=3**（外部卡片仓路径缺失时自跳过；不再失败） |
| 4 | `CardRunsSchemaTest` | tests=2 / failures=0 / errors=0 → 通过 |
| 5 | `ConversationGroupCardsSchemaTest` | tests=7 / failures=0 / errors=0 → 通过 |
| 6 | `BottomSheetScrollSourceGuardTest` | tests=3 / failures=0 / errors=0 → 通过 |
| 7 | `PrimaryBottomSheetIconSourceGuardTest` | tests=3 / failures=0 / errors=0 → 通过 |
| 8–9 | `AIIconAssetsTest` | tests=4 / failures=0 / errors=0 → 通过 |

汇总：全仓 300 个测试结果文件、**1991 tests / 0 failed / 21 skipped**；模块分布 app 166 类、khatkit 53、ai 36、
speech 12、card-validator 6、highlight 5、khatkit-ui/search 各 4、common/workspace 各 3、oauth 2、
document/material3/mediapicker/web/yunx-parser 各 1。

## 5. 资源统计（副本内静态复核）

| 检查 | 结果 |
|---|---|
| app `values/strings.xml` | 3037 条目（3018 string + 17 plurals + 2 其它），唯一名 3036 |
| app `values-zh/strings.xml` | 3037 条目，唯一名 3036 |
| en↔zh 键差集 | 0 / 0 |
| 同类型重复键 | 0（en、zh 各 0） |
| 同名跨类型 | 仅 `setting_trigger_workflow_nodes`（string + plurals，aapt2 合法） |
| 第一方 res XML 解析 | 87 个文件，0 失败 |

## 6. 未执行与边界

- 未运行 `assembleDebug/assembleRelease`；未操作设备/模拟器；未 `git add/commit`；未改任何源码。
- 只跑了合同指定的 `:app:lintDebug`；其它模块 lint 未在本次合同内（如需，先跑 `:khatkit:lintDebug` 等以免被 app 阻断掩盖）。
- 副本保留在 `/home/heizige/.verify/khatkit-verify-20261128-214129` 供复核；构建日志在 `/tmp/t53v2/`。

## 7. 放行条件

修完 §3 的 112 条（至少 92 条 WIP 行），在**新建隔离副本**上重跑 §2 五条命令且全部 exit 0，重新出具绑定该副本的报告；
t53/t52/t13 在 lint 转绿前不得放行。
