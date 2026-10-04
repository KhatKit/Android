# C1 群聊验收记录

本文件是 C1 的证据登记表，不是测试计划。没有可复现的自动化或真机原始证据时，
状态必须保持 `unverified`，不能因 UI 截图或代码存在而改为通过。记录格式遵循
`beyond-operit-client-changes.md` 的 C1 实施契约（该文件 `:193-206` 是规格原文，
`:232-235` 是「验收记录格式」）。

## 现状（2026-10-05 复核，基线 `d45ebd10`，本轮验收证据登记于本文件所在 commit）

- **十条用例仍然全部 `unverified`。** C1 的代码层已实现并落在仓库里，离线
  `testDebugUnitTest` / `lint` / `packageDebug` 三条命令本轮都真跑过且退出码 0，
  但契约 `:206` 点名要的四类证据——**各角色可见消息集合、实际模型调用序列、
  token 计数、导出 SHA-256**——本机磁盘上**一项都没有**，所以用例不能算通过。
- 本机 `adb devices` 为空（无真机无模拟器）。磁盘上唯一的 app 仪器测试记录是
  **2026-10-03 12:58:29 的一次崩溃**（`tests="0"`，`Process crashed`，设备 OnePlus
  `PKG110`），比 C1 基线 `d45ebd10`（2026-10-04 02:30:01）**早 13.5 小时**，因此不能
  当作 C1 的仪器证据。详见「仪器测试状态」。
- 代码层的交付顺序与缺口演进见 `beyond-operit-implementation-status.md` 的
  「C1 交给下一位（2026-10-04）」。那节列的 7 条缺口在 `d45ebd10` 之后已在代码里
  补齐（`mention_role_ids`/`round_id`/`turn_kind` 落进 `UIMessage`
  `ai/src/main/java/heizige/kk/khatkit/ai/ui/Message.kt:29-39`；`GroupConfig` 版本化
  JSON + `extras` 兜底 `GroupChat.kt:70/79/90/214`；记忆 `source_message_id` 归因
  `MemoryExtractor.kt:127-187`；结构化投票 `GroupChat.kt:133/605`；Tavern 导出/QR/相机
  扫码 `TavernChatCodec.kt` + `GroupChatPage.kt` + `QrScannerSheet`；群页复用
  `ChatScaffold`，`GroupChatPage.kt:251`）。**代码层已实现 ≠ 用例通过**——缺的是运行
  证据，不是代码。
- 不要把已有 `GroupChat.kt` / `GroupChatPage` / 277 条 JVM 用例当成验收通过。

## 用例矩阵

「JVM 证据类」列只登记**代码层**的纯函数/纯逻辑用例及其用例数（数字取自
`app/build/test-results/testDebugUnitTest/TEST-<FQN>.xml` 的 `tests` 属性）。
它能证明判定逻辑，不能替代真机 UI、备份恢复或 Tavern 本体互操作。

| ID | 用例 | 必须断言 | JVM 证据类 | 状态 |
|---|---|---|---|---|
| C1-01 | 三角色显式 @ | 只被 @ 角色收到该消息；其他角色不可见 | `GroupTurnCoordinatorTest` 63<br>`GroupChatTest` 18<br>`GroupRoleCompletionProviderTest` 8 | `unverified` |
| C1-02 | 无 @ 的 pipeline | 按 `roles[]` 顺序串接；实际模型调用序列与日志一致 | `GroupTurnCoordinatorTest` 63<br>`GroupChatTest` 18<br>`GroupTurnModelTest` 14 | `unverified` |
| C1-03 | roundtable | 全员完成后仅 `chair_role_id` 汇总；议长前看不到未完成输出 | `GroupTurnCoordinatorTest` 63<br>`GroupChatTest` 18<br>`GroupSpeakerResolverTest` 8 | `unverified` |
| C1-04 | vote | 仅接受结构化候选/票；多数决可复算，平票按配置失败 | `GroupTurnCoordinatorTest` 63<br>`GroupChatTest` 18 | `unverified` |
| C1-05 | 轮次预算 | prompt+completion 达上限后停止剩余角色；日志含已用/上限/未运行角色 | `GroupTurnCoordinatorTest` 63<br>`GroupChatTest` 18<br>`GroupRunSchemaTest` 10 | `unverified` |
| C1-06 | 取消与超时 | 不写入未生成消息；已生成消息和错误节点保留 | `UngeneratedMessageFilterTest` 18<br>`GroupTurnCoordinatorTest` 63 | `unverified` |
| C1-07 | 失败续跑/幂等 | 同 `round_id` 重试跳过已提交 turn；不重复消息 | `GroupTurnCoordinatorTest` 63<br>`GroupRunSchemaTest` 10<br>仪器 `GroupRunDAOTest` 12 条**未跑** | `unverified` |
| C1-08 | 记忆隔离 | 三个 `group:<conversationId>:role:<roleId>` 空间互不串；来源带消息 id | `MemorySpaceGateTest` 9<br>`MemoryToolScopeTest` 7<br>`MemoryAttributionTest` 9<br>`GroupMemorySpacePolicyTest` 6<br>`MemoryExtractorParseTest` 7<br>`MemoryRoleIdMappingTest` 2<br>`GroupRunSchemaTest` 10 | `unverified` |
| C1-09 | Tavern/QR 往返 | 群配置、角色卡、role/轮次/分支哈希一致；不含密钥、记忆、授权 token | `GroupTavernExportTest` 22<br>`TavernCompatTest` 21<br>`QrScannerSheetTest` 16<br>`GroupChatTest` 18 | `unverified` |
| C1-10 | 单聊/群聊共存 | 同一列表混排；类型筛选只过滤；切换后消息与会话数据不丢 | `ConversationListQueryPlanTest` 7<br>`ConversationTypeFilterSourceGuardTest` 2<br>`GroupChatTest` 18 | `unverified` |

C1 相关共 **22 个测试类 / 277 个用例**，全类名带包名前缀为
`heizige.kk.khatkit.app.`。逐类用例数（`tests` 属性实测）：
`feature.chat.GroupTurnCoordinatorTest` 63、`feature.chat.GroupTavernExportTest` 22、
`core.data.ai.tavern.TavernCompatTest` 21、`core.data.model.GroupChatTest` 18、
`feature.chat.UngeneratedMessageFilterTest` 18、
`core.ui.components.ui.QrScannerSheetTest` 16、`feature.chat.GroupTurnModelTest` 14、
`feature.chat.GroupMessageModelTest` 10、`core.data.db.migrations.GroupRunSchemaTest` 10、
`core.data.repository.MemorySpaceGateTest` 9、
`core.data.repository.MemoryAttributionTest` 9、
`core.data.repository.MemoryRetrievalEngineTest` 9、
`feature.chat.GroupSpeakerResolverTest` 8、
`feature.chat.GroupRoleCompletionProviderTest` 8、`feature.chat.ChatManagerTest` 8、
`core.data.ai.tools.MemoryToolScopeTest` 7、
`core.data.repository.MemoryExtractorParseTest` 7、
`feature.chat.ConversationListQueryPlanTest` 7、
`core.data.repository.GroupMemorySpacePolicyTest` 6、
`core.data.ai.tools.MemoryToolsSearchTest` 3、
`feature.chat.ConversationTypeFilterSourceGuardTest` 2、
`core.data.db.MemoryRoleIdMappingTest` 2。
合计 277，`failures=0 errors=0 skipped=0`（逐个 XML 汇总实测）。

## 证据登记

每个用例至少填写一行；多设备或多实现结果追加行，不覆盖历史证据。

命令编号见下方「构建与验证证据」：`CMD-1` = `:app:testDebugUnitTest --rerun`、
`CMD-2` = lint 三连 `--rerun`、`CMD-3` = `packageDebug/assembleDebug --rerun`，
三条退出码均为 0。XML 路径前缀统一为
`app/build/test-results/testDebugUnitTest/TEST-`，下表省略包名前缀
`heizige.kk.khatkit.app.`。

| 用例 | commit | 命令·退出码 | 设备·Android | 输入或 fixture | viewer 可见消息 ID | 模型调用序列 | token (prompt+completion) | 导出 SHA-256 | 证据路径 | 状态 |
|---|---|---|---|---|---|---|---:|---|---|---|
| C1-01 三角色显式 @ | `4534f02a` 新增 GroupTurnCoordinator 纯判定内核<br>`be41fdc9` 补 63 条内核用例（含视角隔离）<br>`6c8d4932` 新增 GroupRoleCompletionProvider<br>`e5dde832` 补 8 条防伪 @ 边界用例<br>（4 个 SHA 均在 `d45ebd10~1..HEAD` 内，已 `git cat-file -t` 逐个核过） | `CMD-1` 退出码 **0**<br>2026-10-04 23:48 强制 `--rerun`，84 个 XML 全部重写<br>`CMD-2` 退出码 **0**（该例无 lint 命中，见下） | **无设备**。`adb devices` 输出为空列表；磁盘上唯一 app 仪器测试记录是 2026-10-03 12:58:29 的 `Process crashed`（`tests="0"`），早于基线 13.5 小时；Android 版本无记录 | 输入 = JVM 测试类：<br>`feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>`feature.chat.GroupRoleCompletionProviderTest`（8）<br>fixture 为 Kotlin 内联构造的 `GroupConfig`/`UIMessage` 列表，无外部文件 | **无证据（需真机）**。JVM 只断言 `viewerMessages(...)` 返回的 `UIMessage` 列表内容，没有把断言里的消息落成带真实 `message.id` 的台账，磁盘上没有「角色 A 可见 = [id1,id3]」这种记录 | **无证据（需真机）**。仓库里被 git 跟踪的 `*.jsonl` 全部在 `ai/src/test/resources/stream-traces/generated/`（10 个文件），是 AI SDK 的桩事件流，与 C1 无关；没有真实 provider 的调用序列 | **无证据（需真实模型）**。`GroupTurnCoordinatorTest` 的预算断言喂的是构造出来的 token 数，不是真实 completion 计数 | **无证据**。C1-01 不产出群聊导出文件；`app/build/outputs/apk/debug/*.apk` 的 SHA-256 是安装包哈希，与「群聊导出文件哈希」不是一回事，不能填进本列 | `app/build/test-results/testDebugUnitTest/TEST-heizige.kk.khatkit.app.feature.chat.GroupTurnCoordinatorTest.xml`<br>`…GroupChatTest.xml`<br>`…GroupRoleCompletionProviderTest.xml`<br>主源码：`app/src/main/java/heizige/kk/khatkit/app/core/data/model/GroupChat.kt` | `unverified` |
| C1-02 无 @ 的 pipeline | `4534f02a` 新增 GroupTurnCoordinator<br>`dd7b3c79` ChatManager 接上协调器 + `group_runs` 运行日志<br>`b752a310` 更新 ChatScaffold KDoc（modelId 已参与解析）<br>`139a91ba` 图片导出的模型名与气泡同口径 | `CMD-1` 退出码 **0**（同上，强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`GroupTurnModel.kt` lint 零命中） | **无设备**，同 C1-01（`adb devices` 空；唯一仪器记录 2026-10-03 崩溃且 `tests="0"`） | 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>`feature.chat.GroupTurnModelTest`（14） | **无证据（需真机）**。同 C1-01：只有纯函数返回集合的内容断言，没有真实 `message.id` 可见台账 | **无证据（需真实 provider）**。契约要求「实际模型调用序列与日志一致」，JVM 侧只能断言 `SpeakerStep` 顺序，没有一次真实请求的 provider/model/顺序记录可导出 | **无证据**。pipeline 的 prompt+completion 实际计数未采集 | **无证据**。pipeline 路径本轮不导出群聊文件 | `…TEST-heizige.kk.khatkit.app.feature.chat.GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>`…TEST-…GroupTurnModelTest.xml`<br>接线点：`app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatManager.kt:705-741`（群分支取 `GroupTurnEntry.Speak` 并按 `resolveGroupTurnModelId` 选模型） | `unverified` |
| C1-03 roundtable | `4534f02a` 新增 GroupTurnCoordinator<br>`be41fdc9` 补 63 条内核用例<br>`ab5cddb9` 新增 GroupSpeakerResolver（说话者解成纯函数）<br>`d934c4d2` 气泡上方显示说话者 + 关掉群聊不适用的三个动作 | `CMD-1` 退出码 **0**（强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`GroupSpeakerResolver.kt` / `ChatMessage.kt` lint 零命中） | **无设备**，同 C1-01 | 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>`feature.chat.GroupSpeakerResolverTest`（8） | **无证据（需真机）**。「议长前看不到未完成输出」只有集合内容断言，没有逐 viewer 的真实消息 ID 清单 | **无证据**。roundtable 的「全员完成 → 仅议长汇总」两段调用序列未实跑 | **无证据** | **无证据** | `…TEST-…GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>`…TEST-…GroupSpeakerResolverTest.xml`<br>主源码：`app/src/main/java/heizige/kk/khatkit/app/feature/chat/GroupSpeakerResolver.kt` | `unverified` |
| C1-04 vote | `4534f02a` 新增 GroupTurnCoordinator<br>`aa771637` `parseBallot` 截断选票前缀改忽略大小写（修小写 `vote:` 被静默丢票）<br>`be41fdc9` 补 63 条内核用例（含投票平票） | `CMD-1` 退出码 **0**（强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`GroupChat.kt` lint 零命中） | **无设备**，同 C1-01 | 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>选票文本是测试内联的 `vote:` 前缀字符串 | **无证据（需真机）** | **无证据**。三角色各自投票请求的真实调用序列未采集 | **无证据** | **无证据** | `…TEST-…GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>判定入口：`app/src/main/java/heizige/kk/khatkit/app/core/data/model/GroupChat.kt:605`（`parseBallot`） | `unverified` |
| C1-05 轮次预算 | `4534f02a` 新增 GroupTurnCoordinator<br>`dd7b3c79` ChatManager 接上协调器 + `group_runs`<br>`be41fdc9` 补 63 条内核用例（预算截断） | `CMD-1` 退出码 **0**（强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`GroupTurnModel.kt` / `GroupRunDAO.kt` lint 零命中） | **无设备**，同 C1-01 | 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>`core.data.db.migrations.GroupRunSchemaTest`（10）<br>预算值是构造入参，不是真实 token | **无证据（需真机）**。预算截断时点「谁被停掉」只有角色 id 顺序断言，没有消息 ID 台账 | **无证据**。「达到上限后停止剩余角色」要求日志与真实请求序列对齐，未实跑 | **无证据（本例是四条缺失里最硬的一条）**。契约 `:202` 的预算口径是「每轮累计 prompt + completion token」，`GroupTurnCoordinatorTest` 里的预算是**判定逻辑**的输入，不是真实 provider 返回的 token 数；`--rerun` 那轮 XML 里也没有任何 token 断言字段 | **无证据** | `…TEST-…GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.data.db.migrations.GroupRunSchemaTest.xml`<br>运行日志表：`group_runs`（`app/src/main/java/heizige/kk/khatkit/app/core/data/db/entity/GroupRunEntity.kt`） | `unverified` |
| C1-06 取消与超时 | `60e9f055` 新增未产出助手消息过滤纯函数<br>`97b0fa1c` `finishGeneration` 落库前丢弃本次新增空气泡<br>`adc8a0ff` 补 18 条用例（空气泡丢弃/部分产出保留/存量不清洗）<br>`be41fdc9` 补 63 条内核用例（失败取消超时） | `CMD-1` 退出码 **0**（强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`ChatManager.kt` lint 零命中） | **无设备**，同 C1-01。这是**最需要真机**的一条：取消与超时只能在真实协程取消 + 真实流式响应下复现，JVM 只能测纯函数 | 输入 = `feature.chat.UngeneratedMessageFilterTest`（18）<br>`feature.chat.GroupTurnCoordinatorTest`（63） | **无证据（需真机）** | **无证据**。取消/超时发生在流式响应中途，没有真实 provider 的部分响应记录 | **无证据**。取消时已消耗的 token 无采集 | **无证据** | `…TEST-heizige.kk.khatkit.app.feature.chat.UngeneratedMessageFilterTest.xml`<br>`…TEST-…GroupTurnCoordinatorTest.xml`<br>纯函数：`app/src/main/java/heizige/kk/khatkit/app/feature/chat/UngeneratedMessageFilter.kt:78`（`dropUngeneratedAssistantMessages`）<br>落库前调用点：`app/src/main/java/heizige/kk/khatkit/app/feature/chat/ConversationSession.kt:108`（`finishGeneration` 内） | `unverified` |
| C1-07 失败续跑/幂等 | `d976aa61` 新增 `group_runs` 表与运行 token 幂等<br>`007e4173` 改复合主键 `(conversation_id, round_id)` + `run_token/updated_at`<br>`70ed043d` `GroupRunDAO` 按 `(conversationId, roundId)` 定位 + `upsertRun` 事务<br>`892577a3` 30→31 改显式 `Migration_30_31` 并在工厂注册<br>`cc01d78d` androidTest 迁移到复合主键/`run_token` 并补幂等与 upsert 用例<br>`be41fdc9` 补 63 条内核用例（幂等/续跑） | `CMD-1` 退出码 **0**（强制 `--rerun`，JVM 侧覆盖 app 模块全部 664 个用例，含本例相关的 `GroupTurnCoordinatorTest` 63 + `GroupRunSchemaTest` 10）<br>**仪器侧未跑**：`adb devices` 空，`connectedDebugAndroidTest` 无法执行 | **无设备**。C1 相关仪器测试源码共 **19 个注解**（`GroupRunDAOTest` 12 + `Migration_30_31_Test` 7），**执行结果为零**。唯一 app 仪器记录 2026-10-03 12:58:29 崩溃且 `tests="0"`，早于基线 | 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.db.migrations.GroupRunSchemaTest`（10）<br>仪器侧 fixture 未采集：`app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/dao/GroupRunDAOTest.kt`、`…/core/data/db/migrations/Migration_30_31_Test.kt`（源码在库，执行证据不在库） | **无证据（需真机）** | **无证据** | **无证据** | **无证据** | `…TEST-…GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupRunSchemaTest.xml`<br>仪器记录（崩溃，早于基线）：`app/build/outputs/androidTest-results/connected/debug/TEST-PKG110 - 16-_app-.xml`<br>DAO：`app/src/main/java/heizige/kk/khatkit/app/core/data/db/dao/GroupRunDAO.kt` | `unverified` |
| C1-08 记忆隔离 | `966792d6` `memory_chunks` 加 `role_id` + 注册 `group_runs`（Room 30→31）<br>`63502610` `addMemory` 透传 `roleId` 且 `toModel` 带出发言角色<br>`8e5457dc` `getMentionsOfEntity` 改带 `spaceId` 的 JOIN<br>`e7606912` 检索层加 `MemorySpaceGate` 空间闸门（FTS/向量/图谱三路）<br>`68b92146` `forgetMemory/linkMemories` 加 `expectedSpaceId` 归属校验<br>`6504f023` 记忆工具接群空间（无全局/助手回退），群聊不下发 `recent_chats`<br>`2031c409` `MemoryExtractor` 写入带 `roleId`，`sourceMessageId` 按 `source_line` 归因<br>`495b5e3a` `ChatManager` 接群记忆作用域（懒建空间、只读 viewer 可见消息）<br>`58b129c7` 补 31 条用例（空间闸门/工具空间/抽取归属/懒建/跨空间泄漏回归） | `CMD-1` 退出码 **0**（强制 `--rerun`，覆盖本例 7 个类的 50 个用例）<br>`CMD-2` 退出码 **0**（`MemoryRepository.kt` / `MemoryExtractor.kt` lint 零命中） | **无设备**，同 C1-01。三空间互不串需要真实检索栈 + 真实写入，JVM 只能测纯函数闸门 | 输入 = `core.data.repository.MemorySpaceGateTest`（9）<br>`core.data.ai.tools.MemoryToolScopeTest`（7）<br>`core.data.repository.MemoryAttributionTest`（9）<br>`core.data.repository.GroupMemorySpacePolicyTest`（6）<br>`core.data.repository.MemoryExtractorParseTest`（7）<br>`core.data.db.MemoryRoleIdMappingTest`（2）<br>`core.data.db.migrations.GroupRunSchemaTest`（10） | **无证据（需真机）**。三个 `group:<conv>:role:<role>` 空间的真实检索结果可见性没有跨 viewer 的实跑记录 | **无证据**。「检索 query 只用 viewer 过滤结果」要对照真实请求的 query 文本，未采集 | **无证据** | **无证据**。契约 `:203` 要求记忆内容不进包，但没有导出文件可算哈希 | `…TEST-heizige.kk.khatkit.app.core.data.repository.MemorySpaceGateTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.data.ai.tools.MemoryToolScopeTest.xml`<br>`…TEST-…MemoryAttributionTest.xml`<br>`…TEST-…GroupMemorySpacePolicyTest.xml`<br>`…TEST-…MemoryExtractorParseTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.data.db.MemoryRoleIdMappingTest.xml`<br>`…TEST-…GroupRunSchemaTest.xml` | `unverified` |
| C1-09 Tavern/QR 往返 | `f921a02f` `GroupRole` 补 `extras` 无损往返 + `validate` 校 `schemaVersion`<br>`b3d9bf44` `TavernChatCodec` 加群聊导出/导入（保留 `role_id/round_id/turn_kind/群配置/角色卡`）<br>`efa1c27c` `encodeConfig` 写库路径补跑密钥黑名单检查<br>`53563c8c` 补 Tavern 群聊往返与密钥过滤用例<br>`a1616b6b` 群聊页接上生成二维码（载荷与文本分享共用一份）<br>`7d4a6596` 群聊页接上扫码导入，统一走 `importShare`<br>`f5212df2` 角色卡解码走 null 安全取值<br>`b548025e` 补 `importShare` 五道闸门与 `cards` 保留用例<br>`88c850ef` 群聊导出面板接入 Tavern 群聊导出卡片，打通 `exportGroupJsonl`<br>`2c9aa2e4` 补群聊导出入口纯逻辑用例（面板到可回导文件的往返）<br>`aeab5550` 新增 CameraX + MLKit 扫码弹层与入口决策单测 | `CMD-1` 退出码 **0**（强制 `--rerun`，覆盖 77 个用例：22+21+16+18）<br>`CMD-3` 退出码 **0**（打出了 3 个可安装 APK，但那是安装包不是群聊导出文件）<br>`CMD-2` 退出码 **0**（`TavernChatCodec.kt` lint 零命中） | **无设备**，同 C1-01。相机扫码（`QrScannerSheet`）与 Tavern 本体打开文件**都必须真机/桌面端**，JVM 的 16 条只测入口决策 | 输入 = `feature.chat.GroupTavernExportTest`（22）<br>`core.data.ai.tavern.TavernCompatTest`（21）<br>`core.ui.components.ui.QrScannerSheetTest`（16）<br>`core.data.model.GroupChatTest`（18）<br>往返在 JVM 里是内存对象 → JSON → 内存对象 | **无证据（需真机）** | **无证据** | **无证据** | **无证据（需真机/桌面端）**。这是四条缺失里唯一「能靠一次真机导出就补齐」的：<br>① APK 的 SHA-256 已有（见构建段），但**安装包哈希不是群聊导出文件哈希**，不能填本列；<br>② 本轮没有任何 `.jsonl` / `.json` 群聊导出产物落在仓库或 `/tmp/opencode/c1-hard/`；<br>③ `/tmp/opencode/sample-group.json` / `.jsonl`（各约 1.7 KB）**被仓库零引用**（`git grep sample-group` 无结果）、无 SHA-256、来源不明，**不作为 fixture** | `…TEST-heizige.kk.khatkit.app.feature.chat.GroupTavernExportTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.data.ai.tavern.TavernCompatTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.ui.components.ui.QrScannerSheetTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>编解码：`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tavern/TavernChatCodec.kt` | `unverified` |
| C1-10 单聊/群聊共存 | `2f1d04a2` 搜索路新增按 `type` 的 DAO 查询，空串语义与未归档路对齐<br>`c84256a1` 抽屉类型筛选下沉到 SQL，删掉只作用于已加载页的内存过滤<br>`35ea0762` `type` 谓词抽成两条查询共用的常量<br>`34493507` 补抽屉列表查询判定与「内存过滤已删」的护栏用例<br>`8528ecda` 抽出 `ChatScaffold` 共用消息区骨架<br>`4e97ff57` 群聊页复用 `ChatScaffold`，接成员头像组、@ 选择器与群配置面板 | `CMD-1` 退出码 **0**（强制 `--rerun`，覆盖 27 个用例：7+2+18）<br>`CMD-2` 退出码 **0**（`ChatList.kt` 有 3 条 `FrequentlyChangingValue`，见「已知遗留与风险」第 3 条；`ChatScaffold` 侧零命中） | **无设备**，同 C1-01。「筛选只过滤、来回切换不丢数据」是交互行为，JVM 的 9 条只钉住查询判定 | 输入 = `feature.chat.ConversationListQueryPlanTest`（7）<br>`feature.chat.ConversationTypeFilterSourceGuardTest`（2）<br>`core.data.model.GroupChatTest`（18）<br>fixture 是 SQL 谓词常量 `CONVERSATION_TYPE_PREDICATE_SQL` | **无证据（需真机）** | **无证据** | **无证据** | **无证据** | `…TEST-heizige.kk.khatkit.app.feature.chat.ConversationListQueryPlanTest.xml`<br>`…TEST-heizige.kk.khatkit.app.feature.chat.ConversationTypeFilterSourceGuardTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>DAO：`app/src/main/java/heizige/kk/khatkit/app/core/data/db/dao/ConversationDAO.kt:19/38/66`<br>仓储：`app/src/main/java/heizige/kk/khatkit/app/core/data/repository/ConversationRepository.kt:92/281` | `unverified` |

原始日志、JSONL 运行记录、截图/录屏和导出文件放在未提交的大文件目录或 CI artifact；
此表只登记稳定路径与哈希，避免把隐私消息、密钥、记忆内容提交进仓库。提交前执行
脱敏检查，并确认 QR payload 不含工具授权 token。

## 构建与验证证据

验证窗口：2026-10-04 23:47–23:55（Asia/Shanghai）。日志在仓库外的
`/tmp/opencode/c1-hard/`，**未入库**。三条命令都是强制重跑，退出码取
`${PIPESTATUS[0]}`（gradle 本身的码，不是 `tee` 的码）。

| 编号 | 命令 | 退出码 | 结论行 |
|---|---|---:|---|
| CMD-1 | `./gradlew --offline :app:testDebugUnitTest --rerun` | **0** | `BUILD SUCCESSFUL in 9s` / `282 actionable tasks: 1 executed, 281 up-to-date` |
| CMD-2 | `./gradlew --offline :app:lintAnalyzeDebug --rerun :app:lintReportDebug --rerun :app:lintDebug --rerun` | **0** | `BUILD SUCCESSFUL in 3m 48s` / `581 actionable tasks: 3 executed, 578 up-to-date` |
| CMD-3 | `./gradlew --offline :app:packageDebug --rerun :app:assembleDebug --rerun` | **0** | `BUILD SUCCESSFUL in 12s` / `362 actionable tasks: 9 executed, 353 up-to-date` |

CMD-3 另跑过一次只有 `:app:packageDebug --rerun` 的对照（`BUILD SUCCESSFUL in 6s`，
同样 `9 executed, 353 up-to-date`），用来确认 `assembleDebug` 本身是生命周期壳任务。

### 「真的重跑了」的证据（mtime 前后对比）

光看 `BUILD SUCCESSFUL` 不能证明重跑——Gradle 命中 up-to-date 也会绿。可核的证据是
产物 mtime 严格变新：

| 产物 | 重跑前 | 重跑后 | 判定 |
|---|---|---|---|
| `app/build/test-results/testDebugUnitTest/*.xml`（84 个） | `2026-10-04 23:33:25.274…282` | `2026-10-04 23:48:15.687…689` | 84/84 全部严格变新 |
| XML 内部 `timestamp` 属性 | — | `2026-10-04T15:48:09.623Z` 起（= UTC，等于本地 23:48:09） | 与文件 mtime 自洽 |
| `app/build/reports/lint-results-debug.xml` | `23:38:15.920`（取自验证代理记录，**磁盘上已被覆盖，本文无法二次复核**） | `23:53:24.503`（本机 `ls` 实测） | 可确认被改写 |

重跑前的 84 条 mtime 清单存在 `/tmp/opencode/c1-hard/xml-before.txt`（84 行，全部
`23:33:25`），重跑后在同目录留了 `xml-after.txt`。lint 报告能被确认只被改写过
一次：23:49 那次错误跑法的日志里 `lintReportDebug UP-TO-DATE`，报告没动；23:53
那次正确跑法三个 task 全部实跑后才落到 `23:53:24.503`。

### ⚠️ 方法论坑：`--rerun` 只挂紧邻其前的那一个 task

这是本轮验证最大的假绿来源，务必记住：

- **写 `:app:lintDebug --rerun` 是无效的。** `lintDebug` 是生命周期壳任务，真正的
  分析在 `lintAnalyzeDebug`。`--rerun` 挂到壳任务上，分析 task 仍报 UP-TO-DATE。
  日志实证：`/tmp/opencode/c1-hard/lint-app-rerun.log` 里命令是
  `:app:lintAnalyzeDebug :app:lintDebug --rerun`，结果第 708 行
  `> Task :app:lintAnalyzeDebug UP-TO-DATE`、第 716 行
  `> Task :app:lintReportDebug UP-TO-DATE`，`BUILD SUCCESSFUL in 4s`，
  `1 executed, 580 up-to-date`——**假绿**。必须把 `--rerun` 打到
  `lintAnalyzeDebug`（必要时也打 `lintReportDebug`）上，才是 CMD-2 那种
  `3 executed` / `3m 48s` 的真跑。
- **同理打包要打 `packageDebug` 而不是 `assembleDebug`。** `assembleDebug` 同样是
  生命周期壳任务，`:app:assembleDebug --rerun` 不会让 `packageDebug` 重跑。
  CMD-3 打的是 `packageDebug`，日志里 `> Task :app:packageDebug`（无
  UP-TO-DATE）与 `> Task :app:assembleDebug` 同时出现。

### 测试结果（实测 XML 汇总）

`:app:testDebugUnitTest` = **84 个测试类 / 664 tests / 0 failures / 0 errors /
0 skipped**。

全仓 `./gradlew --offline test` = **15 个模块 / 180 个测试类 / 1291 tests /
0 failures / 0 errors / 12 skipped**。12 个 skip 全在非 app 模块，且都是环境依赖型
`@Ignore`：`common/http/okhttp/KtorLiveTest` 5 个联网用例、`khatkit/card/
BuiltinCardSampleTest` 1、`khatkit/engine/RustEngineTest` 5、
`khatkit/exec/CardExecutorTest` 1。

逐模块（`*/build/test-results/*/*.xml` 的 `tests`/`failures`/`errors`/`skipped`
属性求和，实测；`failures` 与 `errors` 全为 0 故不单列）：

| 模块 | 类 | tests | skipped |
|---|---:|---:|---:|
| app | 84 | 664 | 0 |
| khatkit | 33 | 198 | 7 |
| ai | 26 | 194 | 0 |
| card-validator | 4 | 77 | 0 |
| highlight | 5 | 53 | 0 |
| speech | 12 | 43 | 0 |
| workspace | 3 | 20 | 0 |
| search | 3 | 12 | 0 |
| khatkit-ui | 2 | 10 | 0 |
| mediapicker | 1 | 9 | 0 |
| common | 2 | 6 | 5 |
| oauth | 2 | 2 | 0 |
| document | 1 | 1 | 0 |
| material3 | 1 | 1 | 0 |
| web | 1 | 1 | 0 |
| **合计** | **180** | **1291** | **12** |

⚠️ 有测试结果和有 lint 报告的是**两批不同的 15 个模块**：`card-validator` 有测试
结果（4 类 77 例）但**没有 lint 报告**（见下）；`image-toolbox-dependency` 有 lint
报告（5 Warning）但**没有测试结果目录**。

### lint

**统计口径**：用脚本遍历全仓 `*/build/reports/lint-results-*.xml`（共 **15** 个
文件，对应 15 个有 lint 报告的模块），逐个 `<issue>` 元素数它的 `severity` 属性。
以本次实测为准：

| 范围 | Error | Warning | Hint | 合计 |
|---|---:|---:|---:|---:|
| **app 单模块**（`app/build/reports/lint-results-debug.xml`） | **0** | **590** | **6** | **596** |
| **15 模块聚合** | **0** | **623** | **7** | **630** |

15 个模块逐个（`<issue severity>` 计数实测）：app 590W+6H、khatkit 17W、
image-toolbox-dependency 5W、ai 2W、workspace 2W、common 1W、oauth 1W、speech 1W、
khatkit-ui 4W+1H；document / highlight / material3 / mediapicker / search / web
六个模块 0 条。即 app 之外 Warning 33 条、Hint 1 条，590+33=623、6+1=7。

即验证代理报的「app 模块 0 error / 52 warning / 6 hint」与「596 issues = 590
Warning + 6 Hint / 0 Error」互相矛盾，**后者与本文一致，前者的 52 无法复现**：
app 报告里 `severity` 属性只出现过 `Warning` 和 `Hint` 两种值，distinct `id` 是 28
个、distinct `file` 是 61 个，都不是 52。本文采信 590+6=596（app）与 623+7=630
（15 模块聚合）这两个可复算的数字。**全仓 0 Error。**

⚠️ **`card-validator` 模块没有 lint 报告**：`card-validator/build/reports/` 目录
存在但里面只有 `tests/`，全模块 `find` 不到任何 `lint-results*` 文件。这是
**「没跑」不是「0 命中」**，不要在上表里给它记 0。

**C1 内核 18 个文件的 lint 命中**（按 `location/@file` 绝对路径前缀
`app/src/main/java/heizige/kk/khatkit/app/` 匹配）：

- `feature/chat/ChatList.kt` **3 条**：`FrequentlyChangingValue`，行 `584:32` /
  `587:36` / `590:41`。`git blame -L 580,595` 显示这三行归属 `d2be1c3fe`
  （2026-09-13），**早于 C1 基线 `d45ebd10`（2026-10-04）三个星期，不是 C1 引入**。
- 其余 **17 个文件零命中**：`GroupChatPage.kt`、`GroupSpeakerResolver.kt`、
  `GroupRoleCompletionProvider.kt`、`GroupMemberBar.kt`、`GroupExportCard.kt`、
  `GroupRoleCards.kt`、`GroupTurnModel.kt`、`ChatPage.kt`、`ChatManager.kt`、
  `core/data/model/GroupChat.kt`、`core/data/ai/tavern/TavernChatCodec.kt`、
  `core/data/repository/MemoryRepository.kt`、`core/data/repository/MemoryExtractor.kt`、
  `core/data/db/dao/ConversationDAO.kt`、`core/data/db/dao/GroupRunDAO.kt`、
  `core/ui/components/message/ChatMessage.kt`、
  `core/ui/components/message/ChatMessageActions.kt`。

### 三个 APK（CMD-3 产出）

`app/build/outputs/apk/debug/`，`sha256sum` 实测：

| 文件 | 字节 | SHA-256 |
|---|---:|---|
| `app-universal-debug.apk` | 107,448,104 | `deb7130188a81d27c8c02c9641dec2c46762c8c287a0f10442b47cef7f5f3dc5` |
| `app-x86_64-debug.apk` | 91,401,202 | `0d1b8eeed7567b221e45ef14163cc555b40d37ebaf3d9c44fe02b6d0f42b602c` |
| `app-arm64-v8a-debug.apk` | 90,640,941 | `79510e40401fa9d8a23bd5a58a7cd49cbe7fbf2ba1ca5a55a8c036fe7f3745b5` |

可复现性：第一次 `packageDebug --rerun` 留下的副本
`/tmp/opencode/c1-hard/run1-universal.apk`（107,448,104 B）与仓库产物
`sha256sum` **完全相同**，字节级可复现。

`output-metadata.json`：applicationId `heizige.kk.khatkit.debug`、variantName
`debug`、versionCode **190**、versionName **2.5.5**、minSdkVersionForDexing 26。

⚠️ 这三个 SHA-256 是**安装包**哈希。它们能证明构建可复现，**不能**填进证据登记
表的「导出 SHA-256」列——那一列指的是群聊导出文件。

## 仪器测试状态

- **C1 相关仪器测试 19 个注解，执行结果为零，需设备。**
  `app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/dao/GroupRunDAOTest.kt`
  **12 条 `@Test`**；
  `app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/migrations/Migration_30_31_Test.kt`
  **7 条 `@Test`**。源码在库，但**没有任何执行证据**。
- 本机 `adb devices` 为空（`List of devices attached` 后面没有条目），无真机无模拟器。
- 磁盘上唯一的 app 仪器测试记录是
  `app/build/outputs/androidTest-results/connected/debug/TEST-PKG110 - 16-_app-.xml`
  （mtime `2026-10-03 12:58:29.700`）：`testsuites tests="0" failures="0" errors="0"`
  + `<system-err>Test run failed to complete. Instrumentation run failed due to
  Process crashed.</system-err>`，设备 OnePlus `PKG110`。该记录**比 C1 基线
  `d45ebd10`（2026-10-04 02:30:01）早 13.5 小时**，且 `tests="0"`，既不能证明 C1
  仪器测试跑过，也没有任何 C1 用例结果。另一份
  `image-toolbox-dependency/build/outputs/androidTest-results/connected/debug/TEST-PKG110 - 16-_image-toolbox-dependency-.xml`
  是 2026-09-30 的 split APK 安装失败（`ErrorCode: 1`），与 C1 无关。
- `tools/verification/c1d_migration_30_31_replay.py`（16,005 字节，已入库）能用
  **真实 SQLite** 重放 30→31 迁移 SQL，但**从未运行过**，磁盘上找不到它的输出
  产物（全仓 `find` 只命中脚本自身）。它是最便宜的一步验证——见下节。

## C1 commit 台账

统计区间 `d45ebd10~1..本文件所在 commit`（`--no-merges`），**共 74 个 commit**。
口径是**截至本文件当前 commit**，且已把本轮两次订正提交计入：改本文件的
`docs(c1)` 与改 `ChatPage.kt` KDoc 的 `fix(c1-p)`。

**类型前缀分布**：`feat` 25、`fix` 19、`test` 13、`coder` 7、`refactor` 5、
`docs` 3、`build` 1、`chore` 1 = 74。

**子包标签分布**（`type(tag): subject` 里的 `tag`）：

| 标签 | commit 数 | 契约 `:214-221` 是否定义 |
|---|---:|---|
| `c1-p` | 15 | ❌ 未定义 |
| `c1-d` | 10 | ✅ C1-D 数据与迁移 |
| `c1-s` | 9 | ❌ 未定义 |
| `c1-m` | 8 | ✅ C1-M 记忆与工具接线 |
| `c1-r` | 8 | ✅ C1-R 视角与协作内核 |
| `c1`（裸标签） | 9 | ❌ 未指定子包 |
| `c1-q` | 6 | ❌ 未定义 |
| `c1-u` | 6 | ✅ C1-U 会话 UI |
| `c1-x` | 3 | ✅ C1-X 导出与恢复 |
| **合计** | **74** | |

⚠️ **`c1-p` / `c1-s` / `c1-q` 共 30 个 commit 用了 `client-changes.md:214-221`
未定义的标签**（`c1-p` 15 + `c1-s` 9 + `c1-q` 6）。契约只定义了 C1-D / C1-R /
C1-M / C1-U / C1-X / C1-E 六个子包；这三个标签的实际含义（`c1-p` 群聊页与
模型选型、`c1-s` 说话者显示与抽屉筛选、`c1-q` QR 导入导出接线）是实施过程中
长出来的，**没有回填到契约表**。另加 9 个裸 `c1` 标签 commit 未标子包。下次
整理台账时应把这四个标签正式并入契约表，或重新归并到既有六个子包。

**改 `docs/` 的 commit**：4 个。
- `d45ebd10 chore(c1): 群聊内核与 UI 草稿落基线` —— 同时改代码与文档。
- `d45cdda4 docs(c1): 补 C1 多角色群聊的开源对照记录` —— **纯文档**，
  唯一只改 `docs/beyond-operit-open-source-references.md` 的 commit。
- `d28218ae docs(c1): 重写 C1 验收记录，登记本轮硬证据并保持十例 unverified`
  —— 纯文档，只改本文件。
- 本文件当前 HEAD 上的 `docs(c1): 定点订正台账过期数字与 ChatPage KDoc 笔误`
  —— 纯文档，只改本文件；即上面统计里最新的那个 docs commit，SHA 用
  `git log --oneline -1 -- docs/eval/c1-group-chat.md` 现查（写这段时它还没生成）。

**只改测试 / 验证工具、不动任何 main 源码的 commit**：16 个，拆开看：

- 12 个 `test(...)` 前缀：其中 11 个严格只碰 `app/src/test/` + `app/src/androidTest/`；
  `14335e00`（`test(c1-d)`）额外带了
  `tools/verification/c1d_migration_30_31_replay.py`，仍不含 main 源码。
- 2 个 `coder(c1-d)`：`cc01d78d` 只改 androidTest 两个文件，`d24ef508` 只改重放脚本。
- 2 个 `fix(c1)`：`f3167d3e`、`3c9b63ce`，都只改 `GroupChatTest.kt`。

唯一的「`test(...)` 前缀但同时改 main 源码」是 `58b129c7`（`test(c1-m)`，除 4 个
测试文件外还改了 `core/data/repository/MemoryExtractor.kt`），它不算在上述 16 个里。

## 下一位怎么把 unverified 变成 verified

前置条件只有一件：**一台能装的设备**（`adb devices` 能看到 serial）。以下按用例
列「要补什么、怎么采」。所有原始产物落在仓库外或 CI artifact，表里只登记路径 +
SHA-256。

### 通用采集手段（先搭一次，十例复用）

1. **可见消息 ID 台账（契约 `:206` 第一项）**：真机建 3 角色群聊跑一轮后，从
   数据库导出三份可见集合。落点是
   `app/src/main/java/heizige/kk/khatkit/app/core/data/db/dao/ConversationDAO.kt`
   与消息节点表——按 `role_id` 分组取每个 viewer 的可见消息，输出成
   `viewer=<roleId> -> [msgId1, msgId2, ...]` 的纯文本台账。**关键是要用库里
   真实的 `message.id` UUID，不能用 JVM 测试里的构造值。**
   交叉断言：C1-08 还要额外确认三个 `group:<conv>:role:<role>` 空间的检索结果
   各只含本角色消息。
2. **实际模型调用序列（契约 `:206` 第二项）**：`app/build/` 下没有这个产物。
   两个可行抓法——(a) 在 `ChatManager.handleMessageComplete`（`ChatManager.kt:705`
   起的群分支）旁挂一个 debug 级 hook，逐轮落
   `roundId, roleId, provider, modelId, promptTokens, completionTokens` 一行；
   (b) 用一个本地 mock provider 端点接住请求，把请求体按 `viewerRoleId` 分桶
   存档。**不要用 `ai/src/test/resources/stream-traces/` 里的 `*.jsonl` 顶替**——
   那是 AI SDK 的桩事件流，与 C1 无关。
3. **token 计数（契约 `:206` 第三项）**：从上面同一条 hook 里读 provider 返回的
   usage（prompt + completion），按 `round_id` 累计，与
   `token_budget_per_round` 对齐。这条和 C1-05 是同一份数据：预算截断要能同时
   给出「已用 / 上限 / 未运行角色」三个数。
4. **导出 SHA-256（契约 `:206` 第四项）**：群聊页导出面板
   （`GroupExportCard.kt` → `exportGroupJsonl`）真机点一次，把产物落到
   `/sdcard/Download/` 后 `adb pull`，然后 `sha256sum`。二维码载荷另外存一份
   base64 PNG 及其哈希。**提交前按本文件「证据登记」段的脱敏要求检查**：不得含
   密钥、隐私记忆、工具授权 token。APK 的 SHA-256 不能拿来填这一列。
5. **仪器测试 19 个注解**：接上设备后
   `./gradlew --offline :app:connectedDebugAndroidTest`，
   产物会落到 `app/build/outputs/androidTest-results/connected/debug/`。
   或者先跑零成本的 `python3 tools/verification/c1d_migration_30_31_replay.py`
   （16,005 字节，能用真实 SQLite 重放 30→31），它能先把 C1-07 的迁移侧钉住。

### 逐例要补什么

| 用例 | 缺什么 | 怎么采 |
|---|---|---|
| C1-01 | viewer 可见消息 ID、模型调用序列 | 3 角色群，正文显式 `@角色B`，跑一轮。台账里必须出现：角色 B 收到该条、A 与 C 不可见；同时贴出这一轮只有 B 被调用的一次请求 |
| C1-02 | 模型调用序列（顺序要与日志一致）、token | 同群**不带 @**，`mode=pipeline`，`roles[]` 顺序 A→B→C。贴出 3 次调用的顺序 + 每轮 prompt/completion token，并与 `group_runs` 的 `round_id` 对上 |
| C1-03 | viewer 可见消息 ID（议长视角）、模型调用序列 | `mode=roundtable`，`chair_role_id=C`。台账要能证明 A/B 视角里没有彼此未完成的输出，只有 C 的汇总 prompt 里含全员内容；贴两段调用（全员轮 + 议长汇总轮） |
| C1-04 | 模型调用序列、平票证据 | `mode=vote`，给 3 个结构化候选。贴出各角色选票原文与多数决结果；**平票按配置失败的路径要单独跑一次**并贴错误节点 |
| C1-05 | token 计数（日志含已用/上限/未运行角色） | 把 `token_budget_per_round` 设成让第 2 个角色被截断的值。贴真实 token 累计值 + `group_runs` 里「已用 / 上限 / 未运行角色」三个字段的库内取值 |
| C1-06 | 模型调用序列（部分响应） | 边流式边点停止，再跑一次触发超时。贴：没有空气泡助手消息落库、已生成消息与错误节点都在、已消耗 token 数 |
| C1-07 | 仪器 19 条的结果 + 幂等台账 | 先 `connectedDebugAndroidTest` 拿 19 条结果；再真机让第 2 个角色失败后重试同一 `round_id`，贴库内 `committed_role_ids` 与实际消息条数对得上（不重复） |
| C1-08 | 三个空间的检索结果可见性 + 来源消息 id | 三角色各发一轮后，逐角色检索记忆。贴每个空间的命中列表（消息 id / 角色）与 `source_message_id` 归因，确认互不串、无全局/助手回退 |
| C1-09 | 导出文件 SHA-256 + Tavern 本体互操作 | 真机导出 → `adb pull` → `sha256sum`；用 Tavern 本体打开该文件截图/录屏；再用 QR 扫码导回另一台设备，贴往返前后 `role_id/round_id/turn_kind/群配置/角色卡` 的逐字段 diff 与两端哈希；**单独验证不含密钥/记忆/授权 token** |
| C1-10 | 筛选只过滤 + 切换不丢数据的真机记录 | 同一助手下混建单聊与群聊，录屏走「全部 → 群聊 → 单聊 → 全部」四个 chip 切换，贴切换前后两侧会话的消息数与最后一条消息 id |

每补完一项，在证据登记表**追加一行**（不覆盖历史行），把四列缺失证据填上，
再把用例矩阵的状态改成 `verified`。**四类证据缺任何一类，仍保持 `unverified`。**

## 已知遗留与风险

1. **角色卡 `RoleCardMeta` 只在导入后显示、不落库。**
   `RoleCardMeta` 定义在
   `app/src/main/java/heizige/kk/khatkit/app/core/data/model/GroupChat.kt:95`，
   是 `GroupSharePayload.cards`（同文件 `:114`）的成员。但 `Conversation` 只有
   `groupConfig`（`app/src/main/java/heizige/kk/khatkit/app/core/data/model/Conversation.kt:35`，
   类型 `GroupConfig?`），而 `ConversationEntity.groupConfig` 是
   `@ColumnInfo("group_config", defaultValue = "")` 的**单列 String**
   （`core/data/db/entity/ConversationEntity.kt:37-38`），
   `GroupConfigSerializer` 的契约里没有 cards 的位置。`GroupChatPage.kt:440-446`
   的 KDoc 已写明这一点，`:799-803` 只把 cards 列出来给用户看。**落库要动
   `Conversation` + `ConversationEntity` + 一次 Room 迁移**，超出本包范围。
2. **`GroupChatPage` 的 `onEdit` 只放行 USER。**
   `GroupChatPage.kt:292` 是 `canEditMessage = { it.role == MessageRole.USER }`
   （KDoc 在 `:137-138`）。这挡住了改写角色发言，但一旦放开，改写会让
   `group_runs.committed_role_ids`（`core/data/db/entity/GroupRunEntity.kt:73-74`，
   由 `GroupRunDAO.kt:134` 更新）与实际消息错位——幂等跳过逻辑读的是前者。要放开
   必须同步改写 `committed_role_ids`。
3. **抽屉搜索路缺 `folder_id = ''`。**
   `ConversationDAO.kt:38` 的 `getUnfiledConversationsOfAssistantByType` 带
   `AND folder_id = ''`，而 `:66` 的 `searchConversationsOfAssistantByType` **没有**。
   结果：无搜索词时只显示未归档会话，一搜就把文件夹内的会话也混进来
   （调用点 `core/data/repository/ConversationRepository.kt:92` 与 `:281`）。
   DAO 层已把 type 过滤下沉（`ConversationListQueryPlanTest` 7 条钉住），但
   **folder 口径要不要对齐是产品决策**，本文不擅自改。
4. **`GroupRole.modelId`：生成侧已接，历史消息未回填。**
   `GroupRole.modelId`（`GroupChat.kt:50`）现在生成侧已生效——`ChatManager`
   群分支用 `resolveGroupTurnModelId`（定义在
   `app/src/main/java/heizige/kk/khatkit/app/feature/chat/GroupTurnModel.kt:54`，
   调用点 `ChatManager.kt:726`）按角色绑定选模型，气泡显示侧用
   `resolveMessageModel`（`ChatList.kt:215`），两边同口径（`139a91ba`）。
   **但本包之前生成的历史消息，`message.modelId` 记的是助手绑的模型**，与现在
   气泡显示的角色绑定不一致。要不要写数据迁移回填，是独立决定，本包未做。
5. **`ChatManager.kt:681` / `:686` 仍是会话级模型口径。**
   `val senderName = if (assistant.useAssistantAvatar) {...} else { model.displayName }`
   （`:681`）和 `val useExternalWebSearch = shouldUseExternalWebSearch(assistant, model)`
   （`:686`）都在群分支（`:705` 起）之前按**会话**算好，群聊分支只重算了 `model`
   本身、没回填这两个值。后台通知标题可能显示错模型。
6. **`card-validator` 模块没有 lint 报告。**
   `card-validator/build/reports/` 存在但只有 `tests/`，无任何 `lint-results*`。
   这是「没跑」不是「0 命中」，别在 lint 台账里给它记 0。
7. **两处证据口径待收敛。** 其一：验证代理报的 app lint「52 warning / 6 hint」
   与实测「590 warning / 6 hint」矛盾，52 无法复现，本文采信实测值（见 lint 段）。
   其二：契约 `:214-221` 未定义 `c1-p` / `c1-s` / `c1-q` / 裸 `c1` 四个标签，
   共 36 个 commit 的子包归属需要回填或重新归并。

## 判定规则

- JVM 纯函数测试可证明过滤、路由、预算和幂等逻辑；不能替代真机 UI、备份恢复或
  Tavern 本体互操作证据。
- `unverified`、缺命令退出码、缺可见消息集合、缺调用序列或缺哈希的行均不算通过。
- 任一安全约束失败（越权消息、回退到全局记忆、导出密钥、重复提交）直接阻断该
  子包交付，先修复再重新记录。