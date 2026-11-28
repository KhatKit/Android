# A 交接收口独立复审（2026-11-29）

## 结论与范围

**静态复审通过；编译、JVM 测试和 lint 待主代理在最终快照统一执行。** 本文没有设备验收结论。

复审起点为 `1f7e0dbd5` 及交接留下的工作树。由接手续审代理重新读取规则、比较 HEAD 与现有修改，
复核前轮代理的源码提取，未沿用 t37 实现者自评作为完成依据。该代理本轮只修正文档，
没有运行 Gradle、提交、推送或操作手机。

覆盖 A4 的 t61/t63/t65 与 t42 指定的 M37/M49/E02；B 虚拟屏会话另行评审和提交。
本结论只针对这些交接项，不表示全仓架构债务归零。

## 交接项逐项裁定

以下路径的 app 前缀统一为 `app/src/main/java/heizige/kk/khatkit/app/`。

| 项 | 证据 | 裁定 |
|---|---|---|
| t61 迟到结果 | `core/data/ai/tools/KhatKitTools.kt:760` 的 `describeLateResult` 对 Err 使用 `result.localizedMessage(appContext)`，再写入资源模板 | 已落地，错误码和结果协议未变 |
| t61 审计文案 | `feature/automation/TraceAuditPage.kt:45` 调用 `record.toStepViews(context)`；`helper/TraceAuditExtensions.kt:55` 使用 `TraceReplayHelper.toUiAction(step).localizedComment(context)` | 已落地；CLICK/PRESS 的目标标签由 `khatkit/.../record/RecordingLocalization.kt:23` 内部调用 `localizedTargetLabel(context)` |
| t63 shell 副本 | 实际共享实现为 `core/ui/shell/{MochiShell,MochiNavPages}.kt`；`feature/ui/shell` 的两份无消费者副本已删除 | 已落地，不留同名转发副本 |
| t63 ExtensionSelector | 唯一实现为 `feature/assistant/components/ui/ExtensionSelector.kt:32` | 已按单功能归位，不再由 core 持有该实现 |
| t63 余额 UI | `core/ui/components/ai/ProviderBalanceText.kt:32` 引用 `core/data/model/BalanceReading`，`:33` 引用 `core/util/isLow` | 指定直接符号依赖已收口；未宣称 AppEntryPoint 装配和推导类型的所有间接依赖归零 |
| t63 subagent UI | `core/ui/components/message/tools/SubagentTeamToolUI.kt:17` 引用 `core/data/ai/tools/DELEGATE_TEAM_TASKS` 与 `TEMPORARY_SUBAGENT` | 已用共享工具协议常量替代 feature 符号 |
| t65 快照 helper | 实现位于 `core/data/automation/UiAutomationSnapshotHelper.kt:11`；旧 `feature/automation/helper` 文件删除，`KhatKitTools.kt:32` 同步 import | 本轮补齐；`MAX_TEXT=256`、`MAX_NODES=500`、节点字段与 wire 模型保持 |
| balance JVM 编译阻塞 | `app/src/test/.../feature/balance/data/ProviderBalanceTest.kt:4` 导入共享 `BalanceReading`、`:5` 导入共享 `isLow`，旧 helper import 删除 | 本轮补齐旧包失配；测试方法和断言未改，执行结果由主代理记录 |

## t42 的三个未落地项

原 t37 以「需要传依赖/回调」为由保留业务代码，不能由此判断已符合职责分层。本轮逐项把副作用移出
Composable/ViewModel，保留原有 UI 状态、协程调度与入口。

### M37：服务商连接诊断

- 请求实现：`feature/settings/helper/ProviderConnectionTestHelper.kt:13`。
- 调用入口：`feature/settings/components/ProviderConnectionTester.kt:133`。
- 三路请求仍分别为普通生成、流式生成、工具调用；`TextGenerationParams` 仍使用所选模型及其
  `customHeaders/customBodies`，诊断消息和 `get_current_time` 工具定义保持。
- UI 的三个 `launch`、`UiState`、流式文本累加与结果资源模板保持在原组件中。Helper 无 Compose 状态。

**裁定：职责提取完成，需通过最终编译和原有测试。** 本次没有重画弹窗或调整视觉/导航。

### M49：Chatbox 恢复

- 导入实现：`feature/backup/helper/ChatboxRestoreHelper.kt:17`。
- VM 入口：`feature/backup/BackupViewModel.kt:121`，传入当前设置快照并返回同一个 `ChatboxRestoreResult`。
- 保留 `Dispatchers.IO`、按 conversationId 跳过已存在会话、上传图片存储、逐会话插入顺序和全部计数；
  provider 按 id 与最新设置合并，系统提示仅对导入目标 assistant 按原条件启用。
- 未修改 Chatbox 导入器、持久化模型、资源 key 或数据库 schema。

**裁定：职责提取完成，导入算法与持久化契约保持。**

### E02：市场资源安装与 MCP 保存

- 安装实现：`feature/explore/helper/ResourceInstallHelper.kt:16`。
- VM 入口：`feature/explore/ExploreMarketViewModel.kt:393` 的安装与 `:439` 的保存回调。
- 安装顺序保持 skill 文件 → MCP 设置 → 当前助手 skills → cards；同 URL MCP 去重、缺必填时只存禁用候选、
  密钥脱敏和返回资源模板保持。
- `applyMcpInstallDecision` 纯函数迁到 `feature/explore/helper/McpInstallExtensions.kt:110`；
  VM companion 保留转发，原 `McpInstallViewModelTest` 的测试入口继续有效，未绕过缺必填规则。
- busy/job/列表刷新与回调仍留 VM；`saveMcpServerConfig` 仍在 IO 中更新设置并调用 `provider.applySettings`。

**裁定：职责提取完成，安装和保存仍共用同一纯决策函数。**

## core/data 全量反向依赖实况

`docs/refactor-core-data.md` 旧「14 → 3」只覆盖 t26 原索引，漏掉同树其他反向边。本轮修正为：

- A 自身：**7 条 feature import + 1 处生产全限定引用**。
- 加上 B 的 `BackgroundAppSessionManager`：**8 条 import + 1 处生产全限定引用**。
- 都位于 `KhatKitTools.kt`：LAN 两条、YunX 两条、automation 三条、B 一条、
  `apiHealth` 的 `feature.tools.data.ToolApiHealthRepository` 一处。
- `AutomationModels.kt` 与 `CardChatGateway.kt` 的 KDoc 类型链接不计为生产引用。

这些是仍保留的耦合；本次没有仅因「DI/运行时桥」就把它们当成共享豁免。完整逐边清单与复算命令在
`refactor-core-data.md` §2。`refactor-core-ui-integration.md` §5 及 `refactor-settings-features.md` 的未闭合表
已同步修正。

## 验证与人工回归

本代理执行 `git diff --check`，退出 0；只做源码、调用点、包路径与旧符号搜索，没有执行 Gradle。
建议主代理执行交接规定的 `:app:compileDebugKotlin`、`:app:testDebugUnitTest`、全仓 `test`、
`:app:lintDebug` 并记录绑定快照。原余额测试和 `McpInstallViewModelTest` 应保留原断言运行。

| 段落 | 人工清单（均未执行） |
|---|---|
| 改动点 | `ProviderConnectionTester.kt:133` 只改请求委派；`BackupViewModel.kt:121` 委派 Chatbox 导入；`ExploreMarketViewModel.kt:405` 委派资源安装 |
| 复现路径 | 设置 → 服务商 → 已配置服务商 → 连接测试；设置 → 备份 → Chatbox 导入；探索市场 → 资源安装/配置 MCP |
| 预期结果 | 连接诊断三项分别加载并显示结果；重复 Chatbox 导入仍跳过已存在会话且统计一致；缺必填 MCP 保持禁用且未挂入助手，补齐保存后才启用 |
| 边界 | 网络失败/无工具调用结果、重复导入、带图片导入、重复 URL MCP、缺必填密钥；连接测试展示沿用深浅色/大字号/返回关闭清单，本轮未变更几何与材质 |
