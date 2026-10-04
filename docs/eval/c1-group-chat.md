# C1 群聊验收记录

本文件是 C1 的证据登记表，不是测试计划。没有可复现的自动化或真机原始证据时，
状态必须保持 `unverified`，不能因 UI 截图或代码存在而改为通过。记录格式遵循
`beyond-operit-client-changes.md` 的 C1 实施契约（该文件 `:193-206` 是规格原文，
`:232-235` 是「验收记录格式」）。

## 现状（2026-10-05 复核，基线 `d45ebd10`，C1-D 重放证据登记于锚点 commit `1b0e04a9`，
C1-P 角色卡落库 + Room 31→32 证据登记于 `b7025665`）

- **十条用例仍然全部 `unverified`。** C1 的代码层已实现并落在仓库里，离线
  `testDebugUnitTest` / `lint` / `packageDebug` 三条命令本轮都真跑过且退出码 0，
  但契约 `:206` 点名要的四类证据——**各角色可见消息集合、实际模型调用序列、
  token 计数、导出 SHA-256**——**前三类本机磁盘上仍然一项都没有**，所以用例不能算
  通过。第四类「导出 SHA-256」现在有了**零设备**的确定性证据（9 个 fixture 变体的
  字节数 + SHA-256 已实测落定，含三重确定性验证、往返幂等与 golden 护栏），但那份
  文件**不是真机导出的产物**、**没经过真实 IO 分发**，更不代表酒馆本体能打开——
  也就是「四类里三类零份、第四类只有 JVM 层一份」，因此**一条都不能改成通过**，
  仍是 0/10。
  ⚠️ 2026-10-05 又补了两条**迁移侧**证据（30→31 重放 85 条断言、31→32 重放
  49 条断言，见下方两节），它们**不覆盖上述四类中的任何一类**，因此**一条用例都没
  改成通过**，仍是 0/10。
- 本机 `adb devices` 为空（无真机无模拟器）。磁盘上唯一的 app 仪器测试记录是
  **2026-10-03 12:58:29 的一次崩溃**（`tests="0"`，`Process crashed`，设备 OnePlus
  `PKG110`），比 C1 基线 `d45ebd10`（2026-10-04 02:30:01）**早 13.5 小时**，因此不能
  当作 C1 的仪器证据。详见「仪器测试状态」。
- 代码层的交付顺序与缺口演进见 `beyond-operit-implementation-status.md` 的
  「C1 交给下一位（2026-10-05）」。那节列的 7 条缺口在 `d45ebd10` 之后已在代码里
  补齐（`mention_role_ids`/`round_id`/`turn_kind` 落进 `UIMessage`
  `ai/src/main/java/heizige/kk/khatkit/ai/ui/Message.kt:29-39`；`GroupConfig` 版本化
  JSON + `extras` 兜底 `GroupChat.kt:70/79/90/214`；记忆 `source_message_id` 归因
  `MemoryExtractor.kt:127-187`；结构化投票 `GroupChat.kt:133/605`；Tavern 导出/QR/相机
  扫码 `TavernChatCodec.kt` + `GroupChatPage.kt` + `QrScannerSheet`；群页复用
  `ChatScaffold`，`GroupChatPage.kt:251`）。**代码层已实现 ≠ 用例通过**——缺的是运行
  证据，不是代码。
- 不要把已有 `GroupChat.kt` / `GroupChatPage` / 296 条 JVM 用例当成验收通过。
- **角色卡元数据已经真落库了**（`20581bcb..b7025665` 8 个 commit）：Room **31→32**
  显式迁移 `Migration_31_32`（DDL 只有一句
  `ALTER TABLE ConversationEntity ADD COLUMN group_cards TEXT NOT NULL DEFAULT ''`），
  `Conversation.groupCards` → `ConversationEntity.group_cards`，导入时把
  `payload.cards` 一起写库、刷新后由 `ImportedRoleCardsView` 展示。**「导入的卡片」vs
  「现场生成的卡片」的分工已定案：导出永远用现场生成的**（`groupExportRoleCards`），
  导入快照只落库 + 展示，**不合并、不静默替换**。证据与未验证清单见下方
  「C1-P 角色卡元数据落库与 Room 31→32 迁移（零设备）」。
  ⚠️ 这**不改变**任何用例判定：真机迁移、6 条仪器用例、`ConversationDAO` 真实
  SQLite 行为、UI 刷新后卡片是否真在，四样全未验。

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
| C1-09 | Tavern/QR 往返 | 群配置、角色卡、role/轮次/分支哈希一致；不含密钥、记忆、授权 token | `GroupTavernExportTest` 22<br>`TavernCompatTest` 21<br>`QrScannerSheetTest` 16<br>`GroupChatTest` 18<br>`C1pGroupExportHashTest` 5 | `unverified` |
| C1-10 | 单聊/群聊共存 | 同一列表混排；类型筛选只过滤；切换后消息与会话数据不丢 | `ConversationListQueryPlanTest` 7<br>`ConversationTypeFilterSourceGuardTest` 2<br>`GroupChatTest` 18 | `unverified` |

C1 相关共 **25 个测试类 / 296 个用例**，全类名带包名前缀为
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
**`core.data.model.GroupRoleCardsPersistenceTest` 7**、
**`core.data.db.migrations.ConversationGroupCardsSchemaTest` 7**、
`core.data.repository.MemoryExtractorParseTest` 7、
`feature.chat.ConversationListQueryPlanTest` 7、
`core.data.repository.GroupMemorySpacePolicyTest` 6、
**`core.data.ai.tavern.C1pGroupExportHashTest` 5**、
`core.data.ai.tools.MemoryToolsSearchTest` 3、
`feature.chat.ConversationTypeFilterSourceGuardTest` 2、
`core.data.db.MemoryRoleIdMappingTest` 2。
合计 296，`failures=0 errors=0 skipped=0`（逐个 XML 汇总实测）。
⚠️ 加粗那两个是 C1-P（`20581bcb..b7025665`）新增的，加粗那个 5 条是 C1-09 导出哈希
（`d2b5c05c`）。早前版本这里写的「22 个测试类 / 277 个用例」与随后的
「24 个测试类 / 291 个用例」都已过期（依次 `+2 类 / +14 例`、`+1 类 / +5 例`）。
仪器测试另有 **25 条**同样一次没跑（12 + 7 + 6），见「仪器测试状态」——本轮
**没加任何 `androidTest` 用例，25 这个数不变**。

## 证据登记

每个用例至少填写一行；多设备或多实现结果追加行，不覆盖历史证据。

命令编号见下方「构建与验证证据」：`CMD-1` = `:app:testDebugUnitTest --rerun`、
`CMD-2` = lint 三连 `--rerun`、`CMD-3` = `packageDebug/assembleDebug --rerun`，
三条退出码均为 0。XML 路径前缀统一为
`app/build/test-results/testDebugUnitTest/TEST-`，下表省略包名前缀
`heizige.kk.khatkit.app.`。

| 用例 | commit | 命令·退出码 | 设备·Android | 输入或 fixture | viewer 可见消息 ID | 模型调用序列 | token (prompt+completion) | 导出 SHA-256 | 证据路径 | 状态 |
|---|---|---|---|---|---|---|---:|---|---|---|
| C1-01 三角色显式 @ | `4534f02a` 新增 GroupTurnCoordinator 纯判定内核<br>`be41fdc9` 补 63 条内核用例（含视角隔离）<br>`6c8d4932` 新增 GroupRoleCompletionProvider<br>`e5dde832` 补 8 条防伪 @ 边界用例<br>（4 个 SHA 均在锚定区间 `d45ebd10~1..1b0e04a9` 内，已 `git cat-file -t` 逐个核过） | `CMD-1` 退出码 **0**<br>2026-10-04 23:48 强制 `--rerun`，84 个 XML 全部重写<br>`CMD-2` 退出码 **0**（该例无 lint 命中，见下） | **无设备**。`adb devices` 输出为空列表；磁盘上唯一 app 仪器测试记录是 2026-10-03 12:58:29 的 `Process crashed`（`tests="0"`），早于基线 13.5 小时；Android 版本无记录 | 输入 = JVM 测试类：<br>`feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>`feature.chat.GroupRoleCompletionProviderTest`（8）<br>fixture 为 Kotlin 内联构造的 `GroupConfig`/`UIMessage` 列表，无外部文件 | **无证据（需真机）**。JVM 只断言 `viewerMessages(...)` 返回的 `UIMessage` 列表内容，没有把断言里的消息落成带真实 `message.id` 的台账，磁盘上没有「角色 A 可见 = [id1,id3]」这种记录 | **无证据（需真机）**。仓库里被 git 跟踪的 `*.jsonl` 全部在 `ai/src/test/resources/stream-traces/generated/`（10 个文件），是 AI SDK 的桩事件流，与 C1 无关；没有真实 provider 的调用序列 | **无证据（需真实模型）**。`GroupTurnCoordinatorTest` 的预算断言喂的是构造出来的 token 数，不是真实 completion 计数 | **无证据**。C1-01 不产出群聊导出文件；`app/build/outputs/apk/debug/*.apk` 的 SHA-256 是安装包哈希，与「群聊导出文件哈希」不是一回事，不能填进本列 | `app/build/test-results/testDebugUnitTest/TEST-heizige.kk.khatkit.app.feature.chat.GroupTurnCoordinatorTest.xml`<br>`…GroupChatTest.xml`<br>`…GroupRoleCompletionProviderTest.xml`<br>主源码：`app/src/main/java/heizige/kk/khatkit/app/core/data/model/GroupChat.kt` | `unverified` |
| C1-02 无 @ 的 pipeline | `4534f02a` 新增 GroupTurnCoordinator<br>`dd7b3c79` ChatManager 接上协调器 + `group_runs` 运行日志<br>`b752a310` 更新 ChatScaffold KDoc（modelId 已参与解析）<br>`139a91ba` 图片导出的模型名与气泡同口径 | `CMD-1` 退出码 **0**（同上，强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`GroupTurnModel.kt` lint 零命中） | **无设备**，同 C1-01（`adb devices` 空；唯一仪器记录 2026-10-03 崩溃且 `tests="0"`） | 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>`feature.chat.GroupTurnModelTest`（14） | **无证据（需真机）**。同 C1-01：只有纯函数返回集合的内容断言，没有真实 `message.id` 可见台账 | **无证据（需真实 provider）**。契约要求「实际模型调用序列与日志一致」，JVM 侧只能断言 `SpeakerStep` 顺序，没有一次真实请求的 provider/model/顺序记录可导出 | **无证据**。pipeline 的 prompt+completion 实际计数未采集 | **无证据**。pipeline 路径本轮不导出群聊文件 | `…TEST-heizige.kk.khatkit.app.feature.chat.GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>`…TEST-…GroupTurnModelTest.xml`<br>接线点：`app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatManager.kt:705-741`（群分支取 `GroupTurnEntry.Speak` 并按 `resolveGroupTurnModelId` 选模型） | `unverified` |
| C1-03 roundtable | `4534f02a` 新增 GroupTurnCoordinator<br>`be41fdc9` 补 63 条内核用例<br>`ab5cddb9` 新增 GroupSpeakerResolver（说话者解成纯函数）<br>`d934c4d2` 气泡上方显示说话者 + 关掉群聊不适用的三个动作 | `CMD-1` 退出码 **0**（强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`GroupSpeakerResolver.kt` / `ChatMessage.kt` lint 零命中） | **无设备**，同 C1-01 | 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>`feature.chat.GroupSpeakerResolverTest`（8） | **无证据（需真机）**。「议长前看不到未完成输出」只有集合内容断言，没有逐 viewer 的真实消息 ID 清单 | **无证据**。roundtable 的「全员完成 → 仅议长汇总」两段调用序列未实跑 | **无证据** | **无证据** | `…TEST-…GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>`…TEST-…GroupSpeakerResolverTest.xml`<br>主源码：`app/src/main/java/heizige/kk/khatkit/app/feature/chat/GroupSpeakerResolver.kt` | `unverified` |
| C1-04 vote | `4534f02a` 新增 GroupTurnCoordinator<br>`aa771637` `parseBallot` 截断选票前缀改忽略大小写（修小写 `vote:` 被静默丢票）<br>`be41fdc9` 补 63 条内核用例（含投票平票） | `CMD-1` 退出码 **0**（强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`GroupChat.kt` lint 零命中） | **无设备**，同 C1-01 | 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>选票文本是测试内联的 `vote:` 前缀字符串 | **无证据（需真机）** | **无证据**。三角色各自投票请求的真实调用序列未采集 | **无证据** | **无证据** | `…TEST-…GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>判定入口：`app/src/main/java/heizige/kk/khatkit/app/core/data/model/GroupChat.kt:605`（`parseBallot`） | `unverified` |
| C1-05 轮次预算 | `4534f02a` 新增 GroupTurnCoordinator<br>`dd7b3c79` ChatManager 接上协调器 + `group_runs`<br>`be41fdc9` 补 63 条内核用例（预算截断） | `CMD-1` 退出码 **0**（强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`GroupTurnModel.kt` / `GroupRunDAO.kt` lint 零命中） | **无设备**，同 C1-01 | 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>`core.data.db.migrations.GroupRunSchemaTest`（10）<br>预算值是构造入参，不是真实 token | **无证据（需真机）**。预算截断时点「谁被停掉」只有角色 id 顺序断言，没有消息 ID 台账 | **无证据**。「达到上限后停止剩余角色」要求日志与真实请求序列对齐，未实跑 | **无证据（本例是四条缺失里最硬的一条）**。契约 `:202` 的预算口径是「每轮累计 prompt + completion token」，`GroupTurnCoordinatorTest` 里的预算是**判定逻辑**的输入，不是真实 provider 返回的 token 数；`--rerun` 那轮 XML 里也没有任何 token 断言字段 | **无证据** | `…TEST-…GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.data.db.migrations.GroupRunSchemaTest.xml`<br>运行日志表：`group_runs`（`app/src/main/java/heizige/kk/khatkit/app/core/data/db/entity/GroupRunEntity.kt`） | `unverified` |
| C1-06 取消与超时 | `60e9f055` 新增未产出助手消息过滤纯函数<br>`97b0fa1c` `finishGeneration` 落库前丢弃本次新增空气泡<br>`adc8a0ff` 补 18 条用例（空气泡丢弃/部分产出保留/存量不清洗）<br>`be41fdc9` 补 63 条内核用例（失败取消超时） | `CMD-1` 退出码 **0**（强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`ChatManager.kt` lint 零命中） | **无设备**，同 C1-01。这是**最需要真机**的一条：取消与超时只能在真实协程取消 + 真实流式响应下复现，JVM 只能测纯函数 | 输入 = `feature.chat.UngeneratedMessageFilterTest`（18）<br>`feature.chat.GroupTurnCoordinatorTest`（63） | **无证据（需真机）** | **无证据**。取消/超时发生在流式响应中途，没有真实 provider 的部分响应记录 | **无证据**。取消时已消耗的 token 无采集 | **无证据** | `…TEST-heizige.kk.khatkit.app.feature.chat.UngeneratedMessageFilterTest.xml`<br>`…TEST-…GroupTurnCoordinatorTest.xml`<br>纯函数：`app/src/main/java/heizige/kk/khatkit/app/feature/chat/UngeneratedMessageFilter.kt:78`（`dropUngeneratedAssistantMessages`）<br>落库前调用点：`app/src/main/java/heizige/kk/khatkit/app/feature/chat/ConversationSession.kt:108`（`finishGeneration` 内） | `unverified` |
| C1-07 失败续跑/幂等 | `d976aa61` 新增 `group_runs` 表与运行 token 幂等<br>`007e4173` 改复合主键 `(conversation_id, round_id)` + `run_token/updated_at`<br>`70ed043d` `GroupRunDAO` 按 `(conversationId, roundId)` 定位 + `upsertRun` 事务<br>`892577a3` 30→31 改显式 `Migration_30_31` 并在工厂注册<br>`cc01d78d` androidTest 迁移到复合主键/`run_token` 并补幂等与 upsert 用例<br>`be41fdc9` 补 63 条内核用例（幂等/续跑）<br>`1649f5c7` 迁移重放脚本补 FTS5 触发器存活证据与对照实验 | `CMD-1` 退出码 **0**（强制 `--rerun`，JVM 侧覆盖 app 模块全部 664 个用例，含本例相关的 `GroupTurnCoordinatorTest` 63 + `GroupRunSchemaTest` 10）<br>**仪器侧未跑**：`adb devices` 空，`connectedDebugAndroidTest` 无法执行<br>**C1-D 主机侧重放已跑**：`python3 tools/verification/c1d_migration_30_31_replay.py` 退出码 **0**（连跑两次输出逐字节相同） | **无设备**。C1 相关仪器测试源码共 **19 个注解**（`GroupRunDAOTest` 12 + `Migration_30_31_Test` 7），**执行结果为零**。唯一 app 仪器记录 2026-10-03 12:58:29 崩溃且 `tests="0"`，早于基线<br>**部分证据（仅迁移侧）**：零设备主机侧重放已把 C1-D 的 30→31 迁移钉住 85 条断言 0 失败，覆盖 `group_runs` 复合主键 `(conversation_id, round_id)`、`run_token` UNIQUE 索引、同 `round_id` 第二条被主键拒绝、同 `runToken` 第二次被唯一索引拒绝、`group_runs` 仍只有 1 行（见「C1-D 迁移 30→31 主机侧重放」）。**这只是部分证据，不改状态** | 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.db.migrations.GroupRunSchemaTest`（10）<br>仪器侧 fixture 未采集：`app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/dao/GroupRunDAOTest.kt`、`…/core/data/db/migrations/Migration_30_31_Test.kt`（源码在库，执行证据不在库） | **无证据（需真机）** | **无证据** | **无证据** | **无证据** | `…TEST-…GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupRunSchemaTest.xml`<br>仪器记录（崩溃，早于基线）：`app/build/outputs/androidTest-results/connected/debug/TEST-PKG110 - 16-_app-.xml`<br>DAO：`app/src/main/java/heizige/kk/khatkit/app/core/data/db/dao/GroupRunDAO.kt`<br>主机侧重放脚本：`tools/verification/c1d_migration_30_31_replay.py`（sha256 `7cd9c3fb…8199c6`）<br>重放日志（仓库外）：`/tmp/opencode/c1-replay2/run1.log`、`run2.log`（sha256 `093981c4…4d2e7d5`）<br>变异测试驱动（仓库外）：`/tmp/opencode/c1-replay2/mutate.py` | `unverified` |
| C1-08 记忆隔离 | `966792d6` `memory_chunks` 加 `role_id` + 注册 `group_runs`（Room 30→31）<br>`63502610` `addMemory` 透传 `roleId` 且 `toModel` 带出发言角色<br>`8e5457dc` `getMentionsOfEntity` 改带 `spaceId` 的 JOIN<br>`e7606912` 检索层加 `MemorySpaceGate` 空间闸门（FTS/向量/图谱三路）<br>`68b92146` `forgetMemory/linkMemories` 加 `expectedSpaceId` 归属校验<br>`6504f023` 记忆工具接群空间（无全局/助手回退），群聊不下发 `recent_chats`<br>`2031c409` `MemoryExtractor` 写入带 `roleId`，`sourceMessageId` 按 `source_line` 归因<br>`495b5e3a` `ChatManager` 接群记忆作用域（懒建空间、只读 viewer 可见消息）<br>`58b129c7` 补 31 条用例（空间闸门/工具空间/抽取归属/懒建/跨空间泄漏回归） | `CMD-1` 退出码 **0**（强制 `--rerun`，覆盖本例 7 个类的 50 个用例）<br>`CMD-2` 退出码 **0**（`MemoryRepository.kt` / `MemoryExtractor.kt` lint 零命中）<br>**C1-D 主机侧重放已跑**：`python3 tools/verification/c1d_migration_30_31_replay.py` 退出码 **0**（连跑两次输出逐字节相同） | **无设备**，同 C1-01。三空间互不串需要真实检索栈 + 真实写入，JVM 只能测纯函数闸门<br>**部分证据（仅迁移与 FTS 侧）**：主机侧重放已钉住 `memory_chunks` 加可空 `role_id`（存量 2 行 `role_id IS NULL` 不被改写、新行可写 `'r1'`）、`role_id` 索引存在、存量 `content`/`source_message_id`/`source_ref_id`/`confidence`/时间戳/`embedding` BLOB 全保留，且 3 个 FTS5 触发器在迁移后逐字节存活、INSERT/UPDATE/DELETE 三向真同步到索引（见「C1-D 迁移 30→31 主机侧重放」）。**这不覆盖三空间互不串本身，也不改状态** | 输入 = `core.data.repository.MemorySpaceGateTest`（9）<br>`core.data.ai.tools.MemoryToolScopeTest`（7）<br>`core.data.repository.MemoryAttributionTest`（9）<br>`core.data.repository.GroupMemorySpacePolicyTest`（6）<br>`core.data.repository.MemoryExtractorParseTest`（7）<br>`core.data.db.MemoryRoleIdMappingTest`（2）<br>`core.data.db.migrations.GroupRunSchemaTest`（10） | **无证据（需真机）**。三个 `group:<conv>:role:<role>` 空间的真实检索结果可见性没有跨 viewer 的实跑记录 | **无证据**。「检索 query 只用 viewer 过滤结果」要对照真实请求的 query 文本，未采集 | **无证据** | **无证据**。契约 `:203` 要求记忆内容不进包，但没有导出文件可算哈希 | `…TEST-heizige.kk.khatkit.app.core.data.repository.MemorySpaceGateTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.data.ai.tools.MemoryToolScopeTest.xml`<br>`…TEST-…MemoryAttributionTest.xml`<br>`…TEST-…GroupMemorySpacePolicyTest.xml`<br>`…TEST-…MemoryExtractorParseTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.data.db.MemoryRoleIdMappingTest.xml`<br>`…TEST-…GroupRunSchemaTest.xml`<br>主机侧重放脚本：`tools/verification/c1d_migration_30_31_replay.py`（sha256 `7cd9c3fb…8199c6`），日志 `/tmp/opencode/c1-replay2/run1.log`、`run2.log`（仓库外） | `unverified` |
| C1-09 Tavern/QR 往返 | `f921a02f` `GroupRole` 补 `extras` 无损往返 + `validate` 校 `schemaVersion`<br>`b3d9bf44` `TavernChatCodec` 加群聊导出/导入（保留 `role_id/round_id/turn_kind/群配置/角色卡`）<br>`efa1c27c` `encodeConfig` 写库路径补跑密钥黑名单检查<br>`53563c8c` 补 Tavern 群聊往返与密钥过滤用例<br>`a1616b6b` 群聊页接上生成二维码（载荷与文本分享共用一份）<br>`7d4a6596` 群聊页接上扫码导入，统一走 `importShare`<br>`f5212df2` 角色卡解码走 null 安全取值<br>`b548025e` 补 `importShare` 五道闸门与 `cards` 保留用例<br>`88c850ef` 群聊导出面板接入 Tavern 群聊导出卡片，打通 `exportGroupJsonl`<br>`2c9aa2e4` 补群聊导出入口纯逻辑用例（面板到可回导文件的往返）<br>`aeab5550` 新增 CameraX + MLKit 扫码弹层与入口决策单测<br>`d2b5c05c` 新增导出哈希证据测试（9 变体 + 往返幂等 + SillyTavern 结构对照）<br>`eef6efb3` 新增跨两次独立 JVM 的哈希比对脚本<br>`bbe2e558` 加 golden 清单护栏（把「确定性」升级成「格式没变」） | `CMD-1` 退出码 **0**（强制 `--rerun`，覆盖 82 个用例：22+21+16+18+5）<br>`CMD-3` 退出码 **0**（打出了 3 个可安装 APK，但那是安装包不是群聊导出文件）<br>`CMD-2` 退出码 **0**（`TavernChatCodec.kt` lint 零命中）<br>**C1-P 哈希校验已跑**：`python3 tools/verification/c1p_group_export_hash.py` 退出码 **0**（两次独立 JVM + 落盘 `hashlib` 复算 + golden 清单，三重全一致） | **无设备**，同 C1-01。相机扫码（`QrScannerSheet`）与 Tavern 本体打开文件**都必须真机/桌面端**，JVM 的 16 条只测入口决策 | 输入 = `feature.chat.GroupTavernExportTest`（22）<br>`core.data.ai.tavern.TavernCompatTest`（21）<br>`core.ui.components.ui.QrScannerSheetTest`（16）<br>`core.data.model.GroupChatTest`（18）<br>**`core.data.ai.tavern.C1pGroupExportHashTest`（5）**<br>前四个类的往返在 JVM 里是内存对象 → JSON → 内存对象；新类把文件**真的落盘**（`build/c1p-group-export-hash/*.txt`）再算 SHA-256 | **无证据（需真机）** | **无证据** | **无证据** | **部分证据（零设备，JVM 层确定性）**——契约 `:206` 这一类现在有真实内容了。9 个 fixture 变体的字节数 + SHA-256 已实测落定（`d2b5c05c` / `eef6efb3` / `bbe2e558`，逐条证据见下方「C1-P 群聊导出确定性哈希（零设备）」）：<br>`pipeline_3roles_2rounds_jsonl` 3615 / `36e6585f9aa4a028eb8277bc70578fd2c802cdd730e3981f0d52b02430f0c8b9`<br>`roundtable_3roles_2rounds_jsonl` 3616 / `33c51124e5421ae46a002f025a2e61dc5712b73ddc413ca9dea3b9777ccc0fb3`<br>`vote_3roles_2rounds_jsonl` 3619 / `ad9e3fb119cbc4bea05b7e917eb180aabeb230bbbf89dfe1addcfd0040217c35`<br>`pipeline_3roles_2rounds_array` 3617 / `b8d57a6c4f15e87d1a9d0a181022ba28410a36527075e71b0daf77976c1a3653`<br>`pipeline_with_explicit_create_date_jsonl` 3652 / `92ce04902dc0c8e5bd822020e3df885b30a89adb9fef2fcd0fb712ae9faeee5e`<br>`empty_messages_jsonl` 1518 / `0ae10ea537e112c7f4d98ebd275b26a86e1b30952f1de8274f0cccf670f11e80`<br>`image_part_jsonl` 1711 / `c07d7e6ead7f06143ceae05fa5082be04af1fabc333edb60125ee337c9cbd9e9`<br>`qr_payload_pipeline` 1348 / `2d65ec04dded8a8fc29d3b7cb2d235bea908ee779b0568a6f644f34670cd2ff5`<br>`qr_payload_vote` 1352 / `8b49d47b225f32f6ad6032b1ab49eb1d6122a3af3c97652936adf7df13c2e9bf`<br>⚠️ **但这是零设备 fixture 的哈希，不是真机导出的文件哈希**：落盘走 `java.io.File.writeBytes`，`writeExportTempFile` + `ACTION_SEND` 真实 IO 分发**一次没跑过**；**酒馆本体打开、viewer 可见消息 ID、模型调用序列、token 计数仍零份**。**四类证据缺三类半，所以状态不变。**<br>① APK 的 SHA-256 已有（见构建段），但**安装包哈希不是群聊导出文件哈希**，不能填本列；<br>② `/tmp/opencode/sample-group.json` / `.jsonl`（各约 1.7 KB）**被仓库零引用**（`git grep sample-group` 无结果）、无 SHA-256、来源不明，**不作为 fixture** | `…TEST-heizige.kk.khatkit.app.feature.chat.GroupTavernExportTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.data.ai.tavern.TavernCompatTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.ui.components.ui.QrScannerSheetTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>**`…TEST-heizige.kk.khatkit.app.core.data.ai.tavern.C1pGroupExportHashTest.xml`**（`tests="5"`）<br>哈希校验脚本：`tools/verification/c1p_group_export_hash.py`<br>**golden 清单（入库，护栏本体）：`tools/verification/c1p_group_export_hash.golden.json`**<br>落盘产物（构建目录，未入库）：`app/build/c1p-group-export-hash/*.txt`<br>编解码：`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tavern/TavernChatCodec.kt`<br>C1-P 补的「QR 携带角色卡最小元数据」落库证据见下方「C1-P 角色卡元数据落库与 Room 31→32 迁移（零设备）」 | `unverified` |
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

⚠️ **这组数字是 2026-10-04 23:47 那个验证窗口的实测值，保留原样不覆写。** C1-P
（`20581bcb..b7025665`）新增 2 个测试类 14 条用例之后，`:app:testDebugUnitTest`
实测已是 **86 个测试类 / 678 tests**（0 失败 / 0 错误 / 0 跳过），全仓
`./gradlew --offline test` 已是 **15 个模块 / 182 个测试类 / 1305 tests**
（逐个 `*/build/test-results/*/TEST-*.xml` 求和实测，只有 `app` 那一行变了：
84→86 类、664→678 例；下表其余 14 行与 12 个 skip 全不变）。
新增的两个类是 `core.data.model.GroupRoleCardsPersistenceTest`（7）与
`core.data.db.migrations.ConversationGroupCardsSchemaTest`（7）。**2026-10-05 那次
复跑的命令与逐组证据见下方「C1-P 角色卡元数据落库与 Room 31→32 迁移（零设备）」。**

⚠️ **再往后一批（`d2b5c05c` C1-09 导出哈希测试）之后，`:app:testDebugUnitTest`
实测已是 87 个测试类 / 683 tests / 0 failures / 0 errors / 0 skipped**
（`./gradlew --offline :app:testDebugUnitTest --rerun` 退出码 0，87 个 XML 全部重写，
逐个 `tests` 属性汇总：基线 86 / 678 → **87 / 683**，即 `+1 类 / +5 例`）。新增的那一类
是 `core.data.ai.tavern.C1pGroupExportHashTest`（5）。命令与逐组证据见下方
「C1-P 群聊导出确定性哈希（零设备）」。

⚠️ **全仓 `./gradlew --offline test` 的合计本轮没有重跑**（只跑了
`:app:testDebugUnitTest`），所以「15 个模块 / 182 个测试类 / 1305 tests」这个合计
**保留原样、不要按 `app` 的增量外推**——外推出来的数字不是实测值。要更新合计得重跑
全仓 `test`。

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

⚠️ 上表（含 `app` 那行 84 / 664 与合计 180 / 1291）是 **2026-10-04 窗口**的实测值，
按上面说的口径**保留不覆写**。C1-P 之后的当前值是 `app` **86 / 678**、合计
**182 / 1305**，其余 14 行与 12 个 skip 不变。
⚠️ **再往后一批（C1-09 导出哈希测试）之后 `app` 实测是 87 / 683**（见上面
「测试结果」段），而**合计那一行本轮没有重跑全仓 `test`、因此不更新**——上表的
**180 / 1291** 与「182 / 1305」两个合计都按各自窗口的实测值原样保留，别拿 `app`
的增量去加。

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

### C1-D 迁移 30→31 主机侧重放（零设备）

登记于 commit `1649f5c7`（`test(c1-d): 迁移重放脚本补上 3 个 FTS5 触发器存活证据与
对照实验，修掉 4 个弱点`）。**命令**：

```
python3 tools/verification/c1d_migration_30_31_replay.py
```

| 项 | 实测值 |
|---|---|
| 真实退出码 | **两次都是 0** |
| 两次输出 | **逐字节相同**（各 9,236 字节 / 104 行） |
| 输出 SHA-256 | `093981c461d2533285e3f2a0a635d1b0d4519e6bfcb9be39c8f4f71a74d2e7d5` |
| 日志位置 | `/tmp/opencode/c1-replay2/run1.log`、`run2.log`（**仓库外，未入库**） |
| 脚本 sha256 | `7cd9c3fbc22c5e8da9248c061feda8fe860bb86c5011eba1c2399b405e8199c6`（39,231 字节 / 776 行） |
| 宿主环境 | Python 3.14.5 的 `sqlite3` 模块，SQLite **3.51.2**，支持 FTS5 |
| 变异测试驱动 | `/tmp/opencode/c1-replay2/mutate.py`（**仓库外，未入库**），收尾打印 `ALL_MUTATIONS_DETECTED` |

**宿主环境自检**在脚本第 0 组里：建一张 FTS5 虚表再 `MATCH '探测 记忆'`，断言真命中
（`PASS 宿主 SQLite 3.51.2 支持 FTS5（建虚表 + MATCH）`）。所以下面所有 FTS5 结论都
不是「假设这个库有 FTS5」跑出来的。

**脚本输入全部来自仓库真源，不是抄一份**：

- 旧库结构：`app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/30.json`
  （按 `createSql` 建 `memory_chunks` / `conversation` 等表，并断言「
  `memory_chunks` 建表 SQL 逐字节等于 `30.json` 的 `createSql`」）。
- 迁移 SQL：从
  `app/src/main/java/heizige/kk/khatkit/app/core/data/db/migrations/Migration_30_31.kt`
  里正则抠出 `db.execSQL` 的 **6 条 DDL**（`GROUP_RUNS_CREATE_SQL`、
  `GROUP_RUNS_RUN_TOKEN_INDEX_SQL`、`GROUP_RUNS_CONVERSATION_STARTED_AT_INDEX_SQL`、
  `GROUP_RUNS_STATUS_INDEX_SQL`、`MEMORY_CHUNKS_ADD_ROLE_ID_SQL`、
  `MEMORY_CHUNKS_ROLE_ID_INDEX_SQL`），并断言「条数 = 6」。
- 新结构比对：`…/AppDatabase/31.json`，按 Room `TableInfo` 同口径逐字段比。
- **触发器 DDL 的唯一来源**：
  `app/src/main/java/heizige/kk/khatkit/app/core/data/db/AppDatabaseFactory.kt`——
  虚表 `memory_chunk_fts` `:59-67`、`memory_chunks_ai` `:68-74`、
  `memory_chunks_ad` `:75-82`、`memory_chunks_au` `:83-91`，由 `onOpen` 运行时
  `execSQL` 建。**Room schema JSON 里没有任何 ftsVersion 实体**（脚本第 0 组两条 NOTE
  明说了这一点），所以只能从该文件抠原文。这也解释了为什么这段迁移必须手写。

**断言总数 85 条，0 失败**（`RESULT: 全部断言通过（共 85 条）`），逐组分布：

| 组 | 覆盖 | 条数 |
|---|---|---:|
| 0 | 环境与工具自检（SQLite/FTS5 能力 + SQL 拆分器 4 例 + 抠 DDL） | 7 |
| 1 | 建 30 版真库（版本号、无 `group_runs`、3 触发器在位且与 `onOpen` 逐字节相同、存量已进 FTS 索引） | 7 |
| 2 | 执行迁移 SQL（6 条、多语句支持、无语句被静默丢弃） | 3 |
| 3 | 迁移后结构 + 未重建表判据 + `group_runs` 13 列/默认值/3 个索引 | 27 |
| 4 | **FTS5 触发器迁移后逐字节存活 + INSERT/UPDATE/DELETE 三向真同步** | 10 |
| 5 | **DROP TABLE 对照实验** | 7 |
| 6 | 存量数据不丢（2 行内容、`source_message_id`/`source_ref_id`/`confidence`/5 个时间戳/`embedding` BLOB 全保留、`role_id` 为 NULL、群会话 `type`/`group_config` 保留） | 12 |
| 7 | `role_id` 可写可查（存量 NULL 计数 = 2，新行 `'r1'` 计数 = 1） | 2 |
| 8 | 复合主键与 `run_token` 唯一性（同一 `round_id` 第二条被拒、同一 `run_token` 第二次被拒、只有 1 行、不同群/轮可插） | 4 |
| 9 | 预算字段落库（已用 4096 / 上限 4096 / 未运行 `["r2","r3"]` / 原因 `token_budget_exceeded` / 已提交 `["r1"]`） | 5 |
| 10 | 与 `31.json` 同口径逐字段一致 | 1 |

#### 变异测试有牙齿的证据

只看「全绿」不能证明断言有判别力。在 `/tmp` 的影子仓库里**只改 `Migration_30_31.kt`
的迁移 SQL，跑未改动的脚本**，5 个变异**全部 `EXIT=1`**，未改动的基线副本
`EXIT=0`：

| 变异 | 改法 | EXIT | 失败断言数 |
|---|---|---:|---:|
| `m1_pk_single` | 复合主键改单列 `PRIMARY KEY(conversation_id)` | 1 | 1 |
| `m2_token_index_not_unique` | `run_token` 索引去掉 `UNIQUE` | 1 | 3 |
| `m3_role_id_default` | `role_id` 加 `DEFAULT ''` | 1 | 4 |
| `m4_drop_and_rebuild` | 改成 `CREATE _new_…` + `INSERT…SELECT` + `DROP TABLE` + `RENAME` + 重建 4 个索引 | 1 | 13 |
| `m5_alter_with_destructive_update` | `ALTER` 那条里偷加 `UPDATE … SET source_ref_id=NULL` | 1 | 1 |

影子仓库只含 4 个只读输入副本（`30.json`、`31.json`、`Migration_30_31.kt`、
`AppDatabaseFactory.kt`）加脚本自身；仓库文件全程零改动。

#### ⭐ FTS5 对照实验的核心结论

这是本轮最有价值的部分：**「手写 ALTER 保住触发器」从设计意图上的声明，变成了
对照证实的实测结论。**

拿**同一份 30 库副本**，跑一遍 Room `AutoMigration` 的等价序列
（`CREATE _new_memory_chunks` → `INSERT…SELECT` → `DROP TABLE memory_chunks` →
`ALTER TABLE … RENAME TO` → 重建 4 个索引），结果：

```
PASS  对照：DROP TABLE + 重建路径确实删掉了全部 3 个 FTS5 触发器（正面确认）
      -> 丢失=['memory_chunks_ai', 'memory_chunks_ad', 'memory_chunks_au'] 剩余=[]
PASS  对照：重建路径换了 memory_chunks 的 b-tree（rootpage 变了）  -> before=6 对照=56
PASS  对照：重建后新写入的记忆检索不到（FTS 路静默失效的真实后果）
PASS  对照：那行数据确实写进了 memory_chunks（表本身没坏，坏的是检索路）
```

3 个触发器**全部丢失**（`丢失=[…ai, …ad, …au] 剩余=[]`），b-tree 被换掉
（`rootpage 6 → 56`），而且**新写入的记忆 `MATCH` 查不到**——数据在表里，检索路已经
静默失效。注意最后一条断言同时排除了「表本身坏了」这个更简单的解释。

对照之下，手写 `ALTER TABLE ADD COLUMN` 路径下：

- 3 个触发器的 `sqlite_master.sql` 迁移前后**逐字节不变**；
- **INSERT / UPDATE / DELETE 三个方向都真同步到 FTS 索引**（新插入的行被
  `memory_chunks_ai` 镜像进索引；`UPDATE content` 后旧词查不到、新词查得到；
  `DELETE` 后 FTS 里也删干净）。

两条路径在同一份输入上的差异，就是 `Migration_30_31.kt:79-84` 那段 KDoc 立论的实测
背书。

#### 脚本本次修掉的 4 个弱点

必须如实记录，因为它们说明**这份证据在 `1649f5c7` 之前是不可信的**：

1. **核心动机原本完全未被验证。** 原脚本 `:127` 的注释宣称「模拟 FTS5 触发器」，
   但实际**一条触发器都没建、也没有任何相关断言**——而
   `Migration_30_31.kt:79-84` 的整个立论就是「AutoMigration 的 `DROP TABLE` 会删掉
   3 个 FTS5 触发器」。也就是说：脚本在验证一个它自己没测的理由。现已修——第 1 组建
   真触发器并断言与 `onOpen` 逐字节相同，第 4 组验存活，第 5 组做对照实验。
2. **一条恒真断言。** 原 `:193` 的「rowid 未被改写（未重建表）」**不可能失败**：
   `memory_chunks.id` 是 `INTEGER PRIMARY KEY AUTOINCREMENT`，rowid 恒等于 id，
   哪怕整表完整重建也照样 PASS。现已换成两条能真失败的判据：
   (a) `sqlite_master.sql` 必须**逐字节等于**「原 SQL + 从迁移 SQL 解析出的
   `ADD COLUMN` 列定义」；(b) b-tree `rootpage` 迁移前后不变。
3. **多语句执行能力缺失。** 原 `:135` 用 `conn.execute(s)`，Python 的
   `sqlite3` 不支持一次跑多条语句，所以「重建表」那种写法只能报「执行失败」，
   根本跑不起来。现已加一个 SQL 拆分状态机，正确处理 `BEGIN…END` 触发器体、
   `CASE…END`、三种注释与引号转义，并专门用 4 条断言自测拆分器。
4. **一条硬编码 `True` 的假断言。** 原 `:140` 的
   `check("全部迁移 SQL 执行成功", True)` 无论跑成什么样都 PASS。现已改成真断言
   （逐条执行并收集报错数），另加一条「实际执行语句数 = 拆分出的语句数，没有语句被
   静默丢弃」。

断言数因此从 **45 → 85**。

#### ⚠️ 这次验证仍然不能替代的部分

- **`Migration_30_31_Test` 的 7 条仍然一次没跑过，需设备。**
  `app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/migrations/Migration_30_31_Test.kt`
  是 `MigrationTestHelper` 往返测试，`app/build.gradle.kts` 单测只有 `libs.junit`、
  无 Robolectric / `room-testing`，**JVM 上跑不了**。该文件 `:31` 的注释本身就把本脚本
  定位成仪器侧的补充而非替代。**不要因为这节就把 C1-07 勾掉。**
- **脚本自己的边界（脚本内已写成两条 NOTE）**：`rootpage` 与
  `sqlite_master` 两条判据依赖宿主 SQLite ≥ 3.35 的 `ALTER TABLE ADD COLUMN`
  **原地快路径**（本机 3.51.2 满足）。SQLite 3.35 之前 `ADD COLUMN` 会重写整表、
  3.25 之前会重建——**真机 Android 捆绑的 SQLite 版本未知**；若它是 3.25–3.34，
  `ADD COLUMN` 同样是整表重写/重建，手写 ALTER **同样可能丢触发器**。本脚本只证明
  「本引擎上 `ADD COLUMN` 不动表」与「`DROP TABLE` 一定丢触发器」这两条机制。
- **对照实验是主机侧手写的 AutoMigration 等价序列**，不是 Room 生成的真实产物——
  本轮**没有跑 gradle** 去生成/比对 Room 真正的迁移脚本。
- **`memory_chunks` 上没有外键指向它**，`PRAGMA foreign_keys=ON` 下的级联行为
  **未覆盖**。（⚠️ 这一条正是下面 C1-P 那节要补上的洞：`ConversationEntity` **有**
  外键指向它，级联行为**已覆盖**，见「C1-P」的对照实验。）

### C1-P 角色卡元数据落库与 Room 31→32 迁移（零设备）

登记于 commit `b7025665`（`test(c1-p): 31→32 迁移主机侧 SQLite 重放脚本（49 条断言，
退出码 0）`），实现侧是 `20581bcb..b7025665` 这 8 个 commit。**这一节不改变任何用例
的判定**：契约 `:206` 点名的四类证据（viewer 可见消息 ID / 模型调用序列 / token /
导出 SHA-256）**本节一份都没产出**，十例状态仍全 `unverified`。它补的是「导入的角色卡
真的落库」这一条**代码层 + 迁移正确性**证据。
⚠️ 一处口径订正：本文早前在这里写「四类证据**仍然一份都没有**」，那句话在
`d2b5c05c` 之后已经过期——**「导出 SHA-256」那一类现在有一份零设备证据**（见下方
「C1-P 群聊导出确定性哈希（零设备）」）。但**「这一节没产出」这个判断没变**，本节
（31→32 迁移重放）仍然不产出四类里的任何一类，所以十例状态照样全 `unverified`。

**命令**：

```
python3 tools/verification/c1p_migration_31_32_replay.py
```

| 项 | 实测值 |
|---|---|
| 真实退出码 | **0** |
| 结论行 | `RESULT: 全部断言通过（共 49 条）` |
| 输出规模 | 62 行 / 6,122 字节 |
| 日志位置 | `/tmp/opencode/c1p_run1.log`（**仓库外，未入库**） |
| 脚本规模 | `tools/verification/c1p_migration_31_32_replay.py`，21,613 字节 / 387 行 |
| 宿主环境 | Python 3.14.5 的 `sqlite3` 模块，SQLite **3.51.2**，支持 FTS5 |
| 脚本定位 | 复用 C1-D 脚本的 SQL 拆分器与断言收集器（`import c1d_migration_30_31_replay`），只换迁移文件路径 |

**迁移 DDL 只有一句**（`Migration_31_32.kt:26-27` 的 `CONVERSATION_ADD_GROUP_CARDS_SQL`，
`migrate()` 里只 `execSQL` 一次——脚本第 2 组断言「迁移 SQL 条数 = 1」与「实际执行
语句数 = 拆分出的语句数」各钉一条）：

```
ALTER TABLE `ConversationEntity` ADD COLUMN `group_cards` TEXT NOT NULL DEFAULT ''
```

**`31.json` → `32.json` 差异**（Room `TableInfo` 同口径，脚本第 8 组 + 1 条断言；
`32.json` 由 `:app:kspDebugKotlin` 真实导出，已入库）：

| 项 | 31 | 32 |
|---|---|---|
| `version` | 31 | 32 |
| `identityHash` | `b5fee805907e4754211836b77e78e3c7` | `9f1437f7e85ba5c591a31a3c5cb73de1` |
| 表集合 | 15 张 | 15 张（**同一集合**，只有 `ConversationEntity` 变） |
| `ConversationEntity` 列数 | 15 | 16 |
| 主键 / 索引 / 外键 | — | **三者全部不变** |
| 新增字段 | — | `{fieldPath: groupCards, columnName: group_cards, affinity: TEXT, notNull: true, defaultValue: ''}` |

**断言总数 49 条，0 失败**，逐组分布：

| 组 | 覆盖 | 条数 |
|---|---|---:|
| 0 | 环境与工具自检（SQLite/FTS5 能力 + 从 `AppDatabaseFactory.kt` 抠虚表与 3 个触发器 DDL；1 条 NOTE 说明 Room schema 里没有 ftsVersion 实体） | 3 |
| 1 | 建 31 版真库（31.json 结构 + `onOpen` 的 FTS5 虚表与 3 个触发器 + 存量数据；`message_node` 外键指向 `ConversationEntity`） | 5 |
| 2 | 执行 `Migration_31_32.kt` 里手写的迁移 SQL（条数 = 1、0 条报错、无语句被静默丢弃） | 3 |
| 3 | 原地 ADD COLUMN 的两条判据 + 新列属性（列存在 / `TEXT` / `NOT NULL` / 默认 `''` / 非主键 / 列数 16 / `_new_ConversationEntity` 不存在 / `sqlite_master` 逐字节 == 原 SQL + ALTER 追加段 / **b-tree `rootpage` 前后不变 `2→2`**） | 10 |
| 4 | 旧行数据不丢 + `group_cards` 是**空串而不是 NULL**（2 行仍在、群会话标题/`type`/`group_config` 保留、无一行 IS NULL） | 7 |
| 5 | 角色卡 blob 可写可读回（**含非 ASCII persona 逐字节读回** / `'[]'` 与 `''` 区分得开 / blob 能被 sqlite 读成 JSON） | 3 |
| 6 | FTS5 触发器迁移后逐字节存活（3 个 `sqlite_master.sql` 不变 + 新写入记忆仍镜像进索引）+ **`message_node` 外键迁移后仍指向 `ConversationEntity`** | 9 |
| 7 | **DROP TABLE 对照实验**（见下节） | 8 |
| 8 | 迁移后结构与 `32.json` 逐字段一致（Room `TableInfo` 同口径） | 1 |

#### ⭐ 核心结论：这次 AutoMigration 会把消息删光，不只是丢触发器

C1-D 那节的对照实验结论是「`DROP TABLE memory_chunks` 丢 3 个 FTS5 触发器」。**这一节
的对照实验结论严重得多**，也是本次改动最重要的技术结论：

C1-D 重建的是 `memory_chunks`（无子表指向它）；**31→32 要重建的是父表
`ConversationEntity`**，而 `message_node` 上有

```sql
FOREIGN KEY (conversation_id) REFERENCES ConversationEntity(id) ON DELETE CASCADE
```

（`app/src/main/java/heizige/kk/khatkit/app/core/data/db/migrations/Migration_11_12.kt:32`）
——C1-D 那节末尾「`memory_chunks` 上没有外键指向它，级联行为未覆盖」这条缺口就是这里。
脚本第 7 组拿 `PRAGMA foreign_keys=ON` 实测 AutoMigration 的等价序列
（`CREATE _new_ConversationEntity` → `INSERT…SELECT` → `DROP TABLE` → `RENAME`）：

```
PASS  对照：DROP 父表（foreign_keys=ON）等价于隐式 DELETE，ON DELETE CASCADE 把
      message_node 的存量行全部删掉——重建路径真丢消息  -> before=1 after=0
PASS  对照：重建路径换了 ConversationEntity 的 b-tree（rootpage 变了）  -> before=2 对照=62
PASS  对照：父表数据本身被拷回去了（丢的只是子表行）
PASS  ALTER 路径：message_node 的存量行一行没少
```

也就是说：`DROP TABLE` 父表在开启外键时等价于对父表做一次隐式 `DELETE`，CASCADE 把
`message_node` 的**存量消息行全部删光**，而父表数据又被拷回去——用户看到的是
**「会话还在、消息全没了」**。这是静默数据丢失，不是可恢复的错误。加上 C1-D 已经
实测的触发器丢失（`DROP TABLE` 连带删掉 `memory_chunks_ai/ad/au`），两条合起来就是
`Migration_31_32` 必须手写 `ALTER TABLE ADD COLUMN` 的完整理由。

#### 变异测试有牙齿的证据

只看「全绿」不能证明断言有判别力。在 `/tmp` 的影子仓库里**只改 `Migration_31_32.kt`
的 DDL，跑未改动的脚本**（影子仓库只含只读输入副本 + 脚本自身，仓库文件全程零改动）：

| 变异 | 改法 | EXIT | 失败断言数 |
|---|---|---:|---:|
| 基线（未改动） | — | 0 | 0 |
| `m1_nullable_no_default` | `TEXT NOT NULL DEFAULT ''` → `TEXT`（可空、无默认） | **1** | **6** |

失败的 6 条：`group_cards NOT NULL`、`group_cards 默认 ''`、
`旧行 group_cards 是空串（NOT NULL DEFAULT '' 的语义）`、`单聊旧行 group_cards 也是空串`、
`没有任何行 group_cards IS NULL`、
`全部表/列/主键/索引/默认值与 32.json 一致`（`want="''" got=None` +
`notNull want=True got=False`）。这条变异同时证明脚本确实在读真实的迁移 SQL，而不是
把预期硬编码在脚本里。

#### 本次同时新增的 JVM 测试（14 条，全过）

`:app:testDebugUnitTest` 从 **84 类 664 例** 变成 **86 类 678 例**
（0 失败 / 0 错误 / 0 跳过，逐个 XML `tests` 属性汇总实测）。既有测试文件**零删改**，
`20581bcb~1..b7025665` 的 `git diff --stat` 里测试侧只有 3 个**新增**文件：

| 新增测试类 | 用例数 | 钉住什么 |
|---|---:|---|
| `core.data.model.GroupRoleCardsPersistenceTest` | 7 | 六字段往返、blob 与分享载荷 `cards` 同形、`null` 与空列表可区分、**旧行空串解码回 `null`**、损坏列值降级成 `null` 而不抛、自由文本 persona 不被误判成密钥、角色卡键名全在 `FORBIDDEN_EXPORT_KEYS` 黑名单之外 |
| `core.data.db.migrations.ConversationGroupCardsSchemaTest` | 7 | 31↔32 只有 `ConversationEntity` 变、`version = 32` 且 31→32 走显式迁移、`group_cards` 列与实体声明一致、**DDL 与 Room 自己生成的 `createSql` 逐项一致**、迁移注册在工厂且**不在** `autoMigrations` 里、Repository **只在整行映射**编解码、导入路存 `payload.cards` 而导出路仍现场生成 |

#### 新增仪器用例 6 条——**未跑设备**

`app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/migrations/Migration_31_32_Test.kt`
**6 条 `@Test`**（已 grep 计数确认），全部是 `MigrationTestHelper` 往返测试：
`migrate31To32_opensLegacyDatabaseWithoutCrash`、
`migrate31To32_legacyRowsGetEmptyStringAndKeepEverythingElse`、
`groupCardsBlobRoundTripsThroughRealSqlite`、
`migrate31To32_usesAlterAndDoesNotRebuildTheTable`、
`migrate31To32_keepsMessageNodeForeignKey`、
`migrate31To32_groupCardsIsNotNullWithEmptyDefault`。
**一条都没跑过**：`app/build.gradle.kts` 单测只有 `libs.junit`，无 Robolectric /
`room-testing`，JVM 上跑不了；本机 `adb devices` 为空。**不要因为这节就把任何用例
勾掉。**

#### 四条命令的真实退出码

`:app:testDebugUnitTest --rerun` **0** / `:app:assembleDebug` **0** / `:app:lint` **0** /
`python3 tools/verification/c1p_migration_31_32_replay.py` **0**。lint 全仓仍
**596 条 issue（0 Error**，590 Warning + 6 Hint，app 单模块），15 模块聚合 630；
本次碰过的 11 个 Kotlin 文件命中数**全是 0 → 0**（含 `GroupChatPage.kt`、
`ConversationRepository.kt`、`Migration_31_32.kt`、`ConversationEntity.kt`、
`Conversation.kt`、`GroupChat.kt`、`AppDatabase.kt`、`AppDatabaseFactory.kt` 与 3 个新测试文件）。

#### ⚠️ 这次验证仍然不能替代的部分

- **契约 `:206` 的四类证据本节一份都没产出。** viewer 可见消息 ID 台账、真实模型
  调用序列、真实 token 计数、群聊导出文件 SHA-256，本节全部内容都在这四类**之外**
  （迁移正确性 + 纯函数往返），因此**十例状态仍全 `unverified`**。
  C1-09（Tavern/QR 往返）**仍是 `unverified`**——它的 viewer 可见消息 ID、模型调用
  序列、token 计数三份都零份，「导出 SHA-256」那份是下方「C1-P 群聊导出确定性哈希
  （零设备）」里的**零设备 fixture 哈希**，不是真机 IO 分发的产物，更不是酒馆本体
  打开的证据。⚠️ 早前版本这里写「另四类证据同样零份」，那句已被那份新证据订正，
  **但「C1-09 仍 `unverified`」这个结论没变。**
- **`Migration_31_32_Test` 的 6 条一次没跑过，需设备。** 与 C1-D 那节同样的道理：
  主机侧重放**不能替代**仪器往返。
- **`rootpage` / `sqlite_master` 两条判据依赖宿主 SQLite ≥ 3.35 的
  `ALTER TABLE ADD COLUMN` 原地快路径**（本机 3.51.2 满足；脚本第 3 组自己写了这条
  NOTE）。若真机 Android 捆绑的 SQLite 是 3.25–3.34，`ADD COLUMN` 同样是整表重写 /
  重建，**手写 ALTER 的优势不成立**，甚至可能丢触发器。真机 SQLite 版本未知。
- **对照实验仍是主机侧手写的 AutoMigration 等价序列**，不是 Room 生成的真实产物；
  本轮**没有跑 gradle** 去生成/比对 Room 真正的迁移脚本（`32.json` 是 KSP 导出的
  schema 快照，不含迁移语句）。
- **`ConversationDAO.kt` 零改动，但它的 SQL 在真实 SQLite 上的行为仍未验**
  （`:type = ''` 不筛、`itemCount` 正确性）。抽屉轻量投影**刻意不含**
  `group_cards`（`ConversationRepository.kt:464-474` / `:539-545` 有 KDoc），这条
  「不读不会抹掉已落库的卡片」的判断由子代理核实抽屉侧写路径得出、并由
  `ConversationGroupCardsSchemaTest.repositoryMapsGroupCardsOnTheFullRowOnly`
  做成文本护栏，**但仍不是运行时证据**。
- **UI 一行都没上屏**：导入后刷新页面，「已导入的角色卡」是否真在，仍然零证据。

### C1-P 群聊导出确定性哈希（零设备）

登记于 commit `d2b5c05c`（测试）、`eef6efb3`（跨 JVM 比对脚本）、`bbe2e558`
（golden 清单护栏）。**这一节同样不改变任何用例的判定**：契约 `:206` 点名的四类
证据里，「**酒馆本体打开**」、viewer 可见消息 ID、模型调用序列、token 计数
**仍然一份都没有**；「导出 SHA-256」这一类现在有了一份，但它是**零设备 fixture 的**
哈希，不是真机经 IO 分发导出的那份文件，更不代表酒馆读得进去。**C1-09 状态仍是
`unverified`。**

**为什么这条契约以前一直写「无证据」——那个理由不成立。**
C1-09 行的备注曾写「需要真机导出才能算哈希」。可
`TavernChatCodec.exportGroupJsonl` / `TavernChatCodec.exportGroup` /
`GroupChat.encodeQr` 全是**纯函数**，输入只有 `List<MessageNode>` + `GroupConfig` +
`List<RoleCardMeta>` + 几个字符串，**不碰数据库、不碰 `Context`、不碰设备**。所以
「导出 SHA-256 算不出来」是错的，零设备就能算。真正需要真机的从来不是「算哈希」，
而是「这份文件酒馆认不认」。

**命令**：

```
python3 tools/verification/c1p_group_export_hash.py
```

| 项 | 实测值 |
|---|---|
| 真实退出码 | **0**（默认严格比对模式） |
| 脚本规模 | `tools/verification/c1p_group_export_hash.py`，500 行 |
| golden 清单 | `tools/verification/c1p_group_export_hash.golden.json`，67 行 / 3,016 字节（**入库**） |
| 测试类规模 | `app/src/test/java/heizige/kk/khatkit/app/core/data/ai/tavern/C1pGroupExportHashTest.kt`，829 行 / `tests="5"` |
| 产物目录 | `app/build/c1p-group-export-hash/*.txt`（**构建目录，未入库**） |
| 运行方式 | 脚本连跑 **2 次** `gradlew --offline :app:testDebugUnitTest --rerun --tests …C1pGroupExportHashTest`，每次都是**全新的测试 JVM** |
| 覆盖变体 | 9 个 fixture 变体 + 2 个 `encodeQr` 载荷（按 `mode`） |

#### 9 个变体的字节数与 SHA-256（本机实测，逐条复核过）

| variant | bytes | sha256 |
|---|---:|---|
| `empty_messages_jsonl` | 1518 | `0ae10ea537e112c7f4d98ebd275b26a86e1b30952f1de8274f0cccf670f11e80` |
| `image_part_jsonl` | 1711 | `c07d7e6ead7f06143ceae05fa5082be04af1fabc333edb60125ee337c9cbd9e9` |
| `pipeline_3roles_2rounds_array` | 3617 | `b8d57a6c4f15e87d1a9d0a181022ba28410a36527075e71b0daf77976c1a3653` |
| `pipeline_3roles_2rounds_jsonl` | 3615 | `36e6585f9aa4a028eb8277bc70578fd2c802cdd730e3981f0d52b02430f0c8b9` |
| `pipeline_with_explicit_create_date_jsonl` | 3652 | `92ce04902dc0c8e5bd822020e3df885b30a89adb9fef2fcd0fb712ae9faeee5e` |
| `roundtable_3roles_2rounds_jsonl` | 3616 | `33c51124e5421ae46a002f025a2e61dc5712b73ddc413ca9dea3b9777ccc0fb3` |
| `vote_3roles_2rounds_jsonl` | 3619 | `ad9e3fb119cbc4bea05b7e917eb180aabeb230bbbf89dfe1addcfd0040217c35` |
| `qr_payload_pipeline` | 1348 | `2d65ec04dded8a8fc29d3b7cb2d235bea908ee779b0568a6f644f34670cd2ff5` |
| `qr_payload_vote` | 1352 | `8b49d47b225f32f6ad6032b1ab49eb1d6122a3af3c97652936adf7df13c2e9bf` |

`C1P-QR` 那两条（按 `mode` 命名）是同一份字节的第二次独立打印：
`pipeline` 1348 / `2d65ec04…`、`vote` 1352 / `8b49d47b…`，与上表的
`qr_payload_pipeline` / `qr_payload_vote` **完全相同**——所以 QR 载荷的字节也被
`hashlib` 交叉复算覆盖到了（落盘文件 `qr_pipeline.txt` / `qr_vote.txt` 与
`qr_payload_*.txt` 逐字节同哈希）。

⚠️ 落盘目录里另有 `qr_pipeline.txt` / `qr_vote.txt` 两个文件，是 QR 那条用例自己写的
副本，**不是第 10、第 11 个变体**。

#### fixture 覆盖点

- **3 个角色**（`a`/`b`/`c`，其中 `c` 是 `chair = true`）× **3 种 `mode`**
  （`pipeline` / `roundtable` / `vote`）；**2 个 `round_id`**（`r1`/`r2`）。
- **五种 `turn_kind` 齐全**：`user` / `speaker` / `chair` / `vote_summary` / `error`。
- 两条**不同**的 `mention_role_ids`（`["a","b"]` 与 `["c"]`）。
- 一个 `selectIndex = 1` 的**双 swipe 节点**（契约四件套取选中那条，`swipes` 全量保留）。
- `RoleCardMeta` **六字段**齐全，persona 含非 ASCII 且**必须 JSON 转义**的字符：
  `「」`、`C:\Users\ada`、换行、tab、`🎭`；`card_id` 与 `avatar_ref` **各有「有值」与
  「为 null」两种**（为 null 的必须整个键消失，不能变成字符串 `"null"`）。
- **双层 `extras`**：群配置层（含一个**嵌套对象** `nested.depth`）与角色层（`a` 带
  `tone` + `vendor_note`），往返两层都不许丢。
- **空消息列表**（文件只剩一行表头）、**非文本 part**（图片退化成 `toText()`）、**显式
  `createDate`**（唯一一个时间入口）。

#### 三重确定性验证

| 层 | 做法 | 结论 |
|---|---|---|
| ① 进程内 | 同一 JVM 里对每个变体导出两遍，`assertEquals` 比**字节列表**再比 SHA-256 | 9/9 一致 |
| ② 跨 JVM | 脚本连起**两次全新的测试 JVM**，抓各自 `println` 的 `C1P-HASH` 行比对 | 9/9 + 2/2 一致 |
| ③ 独立复算 | Python `hashlib` 重算落在 `app/build/c1p-group-export-hash/` 的文件，与 JVM 打印值对照 | 9/9 一致 |

第 ③ 层是关键：**JVM 自己算的哈希只跟 JVM 自己算的哈希对上是不够的**，那是自己给自己
作证；用另一套实现（CPython 的 `hashlib`）重算磁盘上那份字节，才能证明「打印出来的
那个哈希确实对应真正落盘的那份文件」。

另外静态核过一遍：`exportGroupJsonl` / `exportGroup` / `encodeQr` 里**未发现非确定性
来源**——不写时间戳、不写消息 `id` / `createdAt`、不写随机值；`createDate` 不显式传
就整个键不存在（`pipeline_with_explicit_create_date_jsonl` 这个变体就是专门钉这条的）。

#### 往返幂等清单（7 个 Tavern 变体逐条断言，全过）

导出 → 导入 → **用导入回来的值**再导出（不是拿原 fixture 重算一遍）：

- `groupName` / `userName` / `characterNames` 相等；
- `GroupConfig` **整体**往返相等；群配置层与角色层 `extras` 都保留（含嵌套对象）；
  `chair` 保留。
- `RoleCardMeta` 列表与**六字段逐个**相等；persona 的非 ASCII / 引号 / 反斜杠 / 换行 /
  emoji **原样回来**；`avatarRef = null` 往返后**仍是 `null`**（不是字符串 `"null"`）；
  `cardId = null` 同理。
- 契约四件套 `role_id` / `round_id` / `turn_kind` / `mention_role_ids` **逐条**相等；
  `mes` / `name` 逐条相等；**每行**都有 `swipes` 与 `swipe_id`，且 `swipe_id` 落在
  `swipes` 范围内。
- 多 swipe 节点**两条 swipe 全量保留**且 `selectIndex = 1`（契约字段取选中那条）。
- 非文本 part 只留 `toText()`，字节里**没有任何图片痕迹**。
- 空消息列表往返后**仍只有表头**。
- **再导出的 SHA-256 与第一次逐字节相同**（9/9，含字节数相等）。

#### 与 SillyTavern 的离线结构对照（⚠️ **不是真机打开**）

依据 `docs/beyond-orit-open-source-references.md` 已登记的 SillyTavern `release` @
`06bde939fb1e9c4c8d8641d810f0a916b5bce127`，以及读过的
`public/scripts/group-chats.js:268,272`（读 `data[0].chat_metadata`，且**只在首行带
这个键**时才 `shift()` 掉表头）。本轮**没有联网重新 clone 上游**。

酒馆**认**的：表头 `spec:"st_chat_v1"` / `user_name` / `character_name` /
`create_date`（**仅显式传时**） / `chat_metadata:{is_group:true}`；每条消息 `name` /
`is_user` / `is_system` / `mes` / `swipes` / `swipe_id`。
酒馆**不解释但原样保留**的：`khatkit_group`、`khatkit_character_names`、`role_id`、
`round_id`、`turn_kind`、`mention_role_ids`（后两个落在 `khatkit_` 私有命名空间）。

测试的判据是**封闭集合**：表头与消息对象的**每一个键**都必须落在「酒馆已知键 ∪ 契约
私有键」集合内，**出现第三类键就失败**。这样「我们没往酒馆的 JSON 里塞它不认的东西」
是被断言出来的，不是被观察出来的。

#### ⭐ golden 清单：把「确定性」升级成「格式没变」的长期护栏

原脚本 `eef6efb3` 版有个**真缺口**：它只比「两次运行之间一致」，**不比对任何期望值**。
后果是——哪天有人悄悄改了 `exportGroupJsonl` 的输出格式（多写一个键、少写一个
`create_date`、换字段顺序、调换行符），两次运行**照样完全一致**、测试**照样全绿**、
导出文件**却已经不兼容了**。「确定性」和「格式没变」是两件完全不同的事，前者推不出
后者。

`bbe2e558` 补上 **golden 清单** `tools/verification/c1p_group_export_hash.golden.json`
（**入库**，与脚本同目录）：

- **默认就是严格比对**。任何变体的 `bytes` 或 `sha256` 对不上即退出码 1，并逐个变体
  打印**变了哪个 / golden 旧值 / 实测新值**。
- **变体集合也参与比对**：清单里有而这次没跑到、这次跑到而清单里没有，各算一条失败——
  否则「顺手加一个变体」就能绕过比对。
- **严格 schema**：只允许 `_comment` / `variants` / `qr_payloads` 三个顶层键；每条必须
  恰好有 `bytes`（非负整数，`bool` 显式排除）与 `sha256`（64 位**小写**十六进制）。
  解析失败或 schema 不符**一律硬失败**，**绝不**降级成「读不出来就只做跨 JVM 比对」——
  那个静默失效正是这道护栏要防的。
- **更新必须是显式动作**：`--update-golden` 是**唯一**的写盘路径，没有自动接受。而且
  它仍然要先跑完两次独立 JVM——跨 JVM 不一致或落盘对不上时**拒绝写盘**，避免把非确定性
  一次性固化成「期望值」。
- **清单里也写了更新流程**，改护栏的人就地能读到，不必去翻脚本。

**变异测试：护栏确实有牙齿。** 只改 `TavernChatCodec.kt` 的导出格式、跑**未改动**的
脚本与清单，两次变异都被抓住（还原用文件备份 + sha256 校验，全程零 git 命令）：

| 变异 | 改法 | 脚本退出码 | 报红变体数 | 谁没报红（正确地没报红） |
|---|---:|---:|---:|---|
| 基线（未改动） | — | **0** | 0 | — |
| `mutA` 窄 | 把 `create_date` 这个 `put` **真正挪**到 `chat_metadata` 之后（只改键序、不改键值） | **1** | **1** | 其余 8 个变体 + 2 个 QR 载荷 |
| `mutB` 宽 | 消息对象里 `mes` 提到 `name` 之前 | **1** | **6** | `empty_messages_jsonl`（无消息行）+ 2 个 QR 载荷（走 `GroupChat.encodeQr`） |

两次变异里 **Gradle 都退出码 0**（既有 82 条用例的结构断言**全绿**）——也就是说
**既有测试没抓住这两处漂移，是 golden 清单抓住的**。这正是加这道护栏的全部理由。
`mutA` 的失败信息原文：

```
失败 1 项：
  - 导出字节变了 variants.pipeline_with_explicit_create_date_jsonl：SHA-256
    92ce04902dc0c8e5bd822020e3df885b30a89adb9fef2fcd0fb712ae9faeee5e ->
    f4a6687bea2048a962ee7d95e9c69b75ddb7bb38a2d203dad8489a445232b85b（golden 旧值
    bytes=3652 sha256=92ce0490… / 实测新值 bytes=3652 sha256=f4a6687b…）
```

⚠️ 诚实记一笔：**第一次跑 `mutA` 时退出码是 0**，因为那个「变异」只在那行加了句
Kotlin 行尾注释、**没真挪**（注释不改行为，等于没变异）。重做成真挪之后才报红。所以
「变异 A 抓到了」这句话的前提是**变异本身真的改了行为**——这也是为什么变异测试表
必须连「怎么改的」一起记，只记 EXIT 码没有意义。

#### ⚠️ 这一节仍然不能证明什么（5 条，逐条照记）

1. **酒馆真机 / 桌面端能不能打开这个文件——零证据。** 上面那份对照是**离线结构比对**，
   依据的是已核实并登记的上游源码行为，**不是**真机打开。真机打开需设备。
2. **文件没有经过真实 IO 分发。** `writeExportTempFile` + `ACTION_SEND`（分享面板）
   一次没跑过；测试路径是**内存 → 字节 → `java.io.File.writeBytes`**，**不是** Android
   `ContentResolver` / `MediaStore`。真机上经 Uri 分享出去的字节是否逐字节相同，
   **未验**。
3. **扫码链路零证据。** `QrScannerSheet` 要相机。QR 侧只证明了 `encodeQr` 的**载荷
   确定**（2 条哈希 + golden 护栏），**完全没涉及**二维码图像的生成与识别（MLKit 侧）。
4. **fixture 与真实用户数据不同分布。** persona、群名、消息文本都是为覆盖边界条件
   手写的（含转义字符、emoji、双 swipe），**不代表**真实群聊的字符分布或长度分布。
   「9 个变体哈希稳定」**推不出**「真实导出的文件哈希稳定」。
5. **跨机器 / 跨 JDK 版本一致性未验。** 本机只有一个 JDK（脚本两次独立 JVM 是**同一台
   机器同一个 JDK**）。`kotlinx.serialization` 的输出在别的 JDK / 别的版本上是否逐字节
   相同，**没测过**。真机上的 ART 与本机 JDK 也不是同一个运行时。

#### 本次同时新增的 JVM 测试（5 条，全过）

`:app:testDebugUnitTest` 从 **86 类 678 例** 变成 **87 类 683 例**（0 失败 / 0 错误 /
0 跳过，逐个 XML `tests` 属性汇总实测）。既有测试文件**零删改**：
`f444a714..eef6efb3` 的 `git diff --numstat` 只有两行、都是纯新增（`829 0` 测试文件 +
`245 0` 脚本），**没有任何删除或修改**。C1 相关测试类因此 **24 类 291 例 → 25 类
296 例**。仪器测试**仍是 25 条不变**（本节没加任何 `androidTest`）。

#### 三条命令的真实退出码

`:app:testDebugUnitTest --rerun` **0**（87 类 / 683 例 / 0 失败 0 错误 0 跳过）/
`:app:assembleDebug` **0** / `:app:lintAnalyzeDebug --rerun :app:lintReportDebug --rerun
:app:lintDebug --rerun` **0**（app 单模块仍 **596 条 issue、0 Error**，590 Warning +
6 Hint；`TavernChatCodec.kt` 与本次新增的 `C1pGroupExportHashTest.kt` 命中数都是 **0**）/
`python3 tools/verification/c1p_group_export_hash.py` **0**。

## 仪器测试状态

- **C1 相关仪器测试 25 个注解，执行结果为零，需设备。**（C1-D 之后新增了
  `Migration_31_32_Test` 6 条，早前版本记的 19 已过期。）
  `app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/dao/GroupRunDAOTest.kt`
  **12 条 `@Test`**；
  `app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/migrations/Migration_30_31_Test.kt`
  **7 条 `@Test`**；
  `app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/migrations/Migration_31_32_Test.kt`
  **6 条 `@Test`**。源码在库，但**没有任何执行证据**。
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
- `tools/verification/c1d_migration_30_31_replay.py`（39,231 字节 / 776 行，已入库）
  能用**真实 SQLite** 重放 30→31 迁移 SQL。**2026-10-05 已在本机跑通两次，退出码都是
  0，输出逐字节相同**，登记见上方「C1-D 迁移 30→31 主机侧重放（零设备）」。但它
  **不能替代 `Migration_30_31_Test` 那 7 条仪器用例**，那 7 条仍然一次没跑过。
- ⚠️ 更正一处旧表述：早前版本把本脚本记成「从未运行过，全仓 `find` 只命中脚本自身」。
  **后半句本来就是错的**——除脚本自身外全仓还有多处引用：
  `docs/beyond-operit-implementation-status.md` 2 处（`:146`/`:189`）、
  `app/src/androidTest/.../migrations/Migration_30_31_Test.kt:31` 的注释 1 处，
  本文件多处。正确表述是「**没有任何代码或 CI 执行它**」：`.github/workflows/` 的三个
  工作流（`cards-ci.yml`、`close-blank-issues.yml`、`daily-build.yml`）里既没有
  `python3` 也没有 `tools/`，它是一个纯手工命令。

## C1 commit 台账

统计区间 `d45ebd10~1..1b0e04a9`（`--no-merges`），**共 78 个 commit**。

口径**锚定在固定 SHA `1b0e04a9`**，不是「截至本文件当前 commit」。
**为什么锚在这一个 SHA**：早前几版台账拿「本文件当前 commit」当区间右端，而每改
一次文档本身就产生一个新 commit，右端跟着右移、数字当场过期，已经反复了好几轮
（71→72→74→78→79）。`1b0e04a9`
（`docs(c1): 登记 C1-D 迁移重放证据并订正过期引用与台账数字`）之后本文件只做文字
订正，不再改动功能代码的 commit 构成，所以把它当右端，下面**所有计数在后续文档提交
中保持不变**，不必再追数字。核验命令（换任何 SHA 都能复现）：

```
git log --oneline d45ebd10~1..1b0e04a9 | wc -l          # 78
git log --oneline --merges d45ebd10~1..1b0e04a9 | wc -l # 0，区间内无 merge
```

区间内已含 `32c9e8f8 fix(c1-p): ChatScaffold KDoc 注入缝数量与默认实参清单订正为五个`
（改 `ChatPage.kt` KDoc）。**锚点之后的提交不计入本台账任何计数**——包括本文件
这次提交自己。所以下文的 78 / 6 / 17 都不是「当前 commit 数」，而是「截至
`1b0e04a9` 的数」，改文档不会让它变。
⚠️ 早前这里写的是「锚点之后的**纯文档**提交不计入」，那句话**已过期**：锚点之后
确实又出现了 8 个**功能/测试**提交（`20581bcb..b7025665`，C1-P 角色卡元数据落库 +
Room 31→32 迁移）。它们**同样不计入**——理由不是「纯文档」，而是**不改统计基线**，
逐条列在下面「锚点之后的后续提交」小节。台账口径（78 / 6 / 17 / 前缀分布 / 子包
分布）**一个数都没动**。

**类型前缀分布**：`feat` 25、`fix` 20、`test` 14、`coder` 7、`refactor` 5、
`docs` 5、`chore` 1、`build` 1 = 78。

**子包标签分布**（`type(tag): subject` 里的 `tag`）：

| 标签 | commit 数 | 契约 `:214-221` 是否定义 |
|---|---:|---|
| `c1-p` | 16 | ❌ 未定义 |
| `c1-d` | 11 | ✅ C1-D 数据与迁移 |
| `c1-s` | 9 | ❌ 未定义 |
| `c1-r` | 8 | ✅ C1-R 视角与协作内核 |
| `c1-m` | 8 | ✅ C1-M 记忆与工具接线 |
| `c1`（裸标签） | 11 | ❌ 未指定子包 |
| `c1-q` | 6 | ❌ 未定义 |
| `c1-u` | 6 | ✅ C1-U 会话 UI |
| `c1-x` | 3 | ✅ C1-X 导出与恢复 |
| **合计** | **78** | |

⚠️ **`c1-p` / `c1-s` / `c1-q` 共 31 个 commit 用了 `client-changes.md:214-221`
未定义的标签**（`c1-p` 16 + `c1-s` 9 + `c1-q` 6）。口径：同一区间
`d45ebd10~1..1b0e04a9` 内、只数这三个标签之和，**裸 `c1` 标签不计入这 31**（另算）。
契约只定义了 C1-D / C1-R /
C1-M / C1-U / C1-X / C1-E 六个子包；这三个标签的实际含义（`c1-p` 群聊页与
模型选型、`c1-s` 说话者显示与抽屉筛选、`c1-q` QR 导入导出接线）是实施过程中
长出来的，**没有回填到契约表**。另加 11 个裸 `c1` 标签 commit 未标子包（这 11 个
才是 78 减去 67 个带子标签 commit 的余数）。下次
整理台账时应把这四个标签正式并入契约表，或重新归并到既有六个子包。

**改 `docs/` 的 commit**：6 个。
- `d45ebd10 chore(c1): 群聊内核与 UI 草稿落基线` —— 同时改代码与文档。
- `d45cdda4 docs(c1): 补 C1 多角色群聊的开源对照记录` —— **纯文档**，
  唯一只改 `docs/beyond-operit-open-source-references.md` 的 commit。
- `d28218ae docs(c1): 重写 C1 验收记录，登记本轮硬证据并保持十例 unverified`
  —— 纯文档，只改本文件。
- `01922572 docs(c1): 定点订正验收记录台账的过期 SHA 与 commit 统计` —— 纯文档，
  只改本文件（上一版台账记的 74 就是那次统计的结果，现已过期）。
- `5a66837f docs(c1): 重写 C1 交接小节，如实反映代码已落地但验收证据为零`
  —— 纯文档，只改 `docs/beyond-operit-implementation-status.md`（把「C1 交给下一位」
  重写成 2026-10-05 版，也就是本文 `:19` 与 `client-changes.md:195` 现在引用的那版）。
- `1b0e04a9 docs(c1): 登记 C1-D 迁移重放证据并订正过期引用与台账数字` —— 纯文档，
  只改本文 + `client-changes.md:195` 的日期 + `implementation-status.md:226` 的方位词。
  **它同时就是本台账锚定的那个 SHA**，也是区间内最后一个改本文件的 commit。锚点之后
  本文件只做文字订正，所以「改 `docs/` 的 commit」固定为 **6 个**，不再逐次追加。
  ⚠️ 「锚点之后本文件只做文字订正」这句现在需要加限定：锚点之后本文件**又**被改过
  （20581bcb、1e20f6ce、4efee787、f444a714、399a8f1d、5794e5cb，以及本次订正测试数与
  台账的提交），但那些提交**同样不计入**本台账任何计数——理由见下面
  「锚点之后的后续提交」。**6 个这个数不变。**

**只改测试 / 验证工具、不动任何 main 源码的 commit**：17 个，拆开看：

- 13 个 `test(...)` 前缀：其中 12 个严格只碰 `app/src/test/` + `app/src/androidTest/`；
  `14335e00` 与 `1649f5c7`（两个 `test(c1-d)`）额外带了
  `tools/verification/c1d_migration_30_31_replay.py`，仍不含 main 源码。
- 2 个 `coder(c1-d)`：`cc01d78d` 只改 androidTest 两个文件，`d24ef508` 只改重放脚本。
- 2 个 `fix(c1)`：`f3167d3e`、`3c9b63ce`，都只改 `GroupChatTest.kt`。

唯一的「`test(...)` 前缀但同时改 main 源码」是 `58b129c7`（`test(c1-m)`，除 4 个
测试文件外还改了 `core/data/repository/MemoryExtractor.kt`），它不算在上述 17 个里。

### 锚点之后的后续提交（不计入本台账任何计数）

上面所有数字的右端**仍然是固定的 `1b0e04a9`**，一个都没动。锚点之后又出现了提交，
它们**不计入**本台账——**理由不是「纯文档提交」（那个说法已过期），而是它们不改
统计基线**：不改统计区间、不改 `--no-merges` 口径、不引入新的类型前缀或子包标签，
所以 78 / 6 / 17、前缀分布、子包分布全部保持不变。下次重整台账时，若要把这些 commit
并进区间，只需改右端一个 SHA，但**必须同时重算所有计数**，不能只改总数。

核验命令（`20581bcb..b7025665` = 本次角色卡落库这批的 8 个）：

```
git log --oneline 1b0e04a9..b7025665   # 10 个（含下面两个纯文档提交）
git log --oneline 20581bcb..b7025665   # 8 个，就是这一批功能/测试提交
```

⚠️ 两个区间**差 2 个 commit**，别混用：`1b0e04a9..b7025665` 是 10 个（多出
`20581bcb`——就是「把台账口径锚定到 `1b0e04a9` 并订正四类证据措辞」那次——与
`1e20f6ce`「定点订正四处过期表述」，这两个都是纯文档）；本次要登记的是
`20581bcb..b7025665` 那 **8** 个。

`20581bcb..b7025665` 实测输出（`git log --oneline 20581bcb..b7025665`，倒序）：

| SHA | 标题 | 性质 |
|---|---|---|
| `79d95af7` | `feat(c1-p): GroupChat 增加角色卡元数据落库编解码（encodeRoleCards/decodeRoleCards）` | 功能（main 源码） |
| `14a0f384` | `feat(c1-p): Conversation/ConversationEntity 增加 groupCards 与 group_cards 列` | 功能（main 源码） |
| `99b046e5` | `feat(c1-p): Room 31→32 显式迁移（ConversationEntity 增 group_cards）+ 工厂注册 + 32.json 入库` | 功能（main 源码 + schema 快照） |
| `fe6cd530` | `feat(c1-p): ConversationRepository 整行映射带上 group_cards，抽屉轻量投影刻意不读` | 功能（main 源码） |
| `4594d462` | `feat(c1-p): 群聊导入时把 payload.cards 落库，并在面板显示已落库的导入快照` | 功能（main 源码） |
| `62b62744` | `test(c1-p): 角色卡元数据落库往返 + 31→32 schema 一致性（14 条）` | 测试（2 个新增 JVM 类） |
| `b92e3323` | `test(c1-p): 新增 Migration_31_32_Test 仪器用例（6 条，未跑设备）` | 测试（1 个新增仪器类） |
| `b7025665` | `test(c1-p): 31→32 迁移主机侧 SQLite 重放脚本（49 条断言，退出码 0）` | 测试（1 个新增验证脚本） |

这 8 个的净效果：`app` 模块 JVM 测试 **84 类 664 例 → 86 类 678 例**（+2 类 / +14 例，
0 失败 0 错误 0 跳过）；C1 相关仪器测试 **19 → 25 条**（+6，未跑）；Room
**31 → 32**；`ConversationDAO.kt` **零改动**；既有测试文件**零删改**。证据登记见上方
「C1-P 角色卡元数据落库与 Room 31→32 迁移（零设备）」。
⚠️ 这批提交**没有产生任何契约 `:206` 意义上的验收证据**，所以**十例状态仍全
`unverified`**。

#### 再往后一批：C1-09 导出哈希证据（`d2b5c05c` / `eef6efb3` / `bbe2e558`）

锚点之后又出现了这批（连同本文档自己的两个登记提交，见下表）。**同样不计入本台账**——
理由同上：不改统计区间、不改 `--no-merges` 口径、不引入新的类型前缀或子包标签
（这批全是 `coder(c1-09)` 裸 `c1` 标签，但那 11 个裸 `c1` 是**截至 `1b0e04a9`** 的
数，本台账不动）。所以 **78 / 6 / 17、前缀分布、子包分布一个数都没动**。

| SHA | 标题 | 性质 |
|---|---|---|
| `d2b5c05c` | `coder(c1-09): 新增群聊导出哈希证据测试（9 变体 + 往返幂等 + SillyTavern 结构对照）` | 测试（1 个新增 JVM 类，829 行 / 5 例） |
| `eef6efb3` | `coder(c1-09): 加跨两次独立 JVM 的导出哈希比对脚本（含 hashlib 交叉复算）` | 测试（1 个新增验证脚本，245 行） |
| `bbe2e558` | `coder(c1-09): 给群聊导出哈希加 golden 清单护栏（默认严格比对 + 显式更新）` | 测试（脚本扩到 500 行 + 1 个新增入库 golden 清单） |
| `399a8f1d` | `docs(c1-09): 导出 SHA-256 列填入 9 个实测哈希，状态列保持 unverified` | 纯文档（只改本文） |
| `5794e5cb` | `docs(c1-09): 新起 C1-P 群聊导出确定性哈希一节（哈希表 + 三重验证 + golden 护栏 + 5 条未证明）` | 纯文档（只改本文） |

⚠️ 本文件在 `5794e5cb` 之后还有一次提交（订正测试数与台账，即下表末行；SHA 用
`git log --oneline -1 -- docs/eval/c1-group-chat.md` 可查），同样**不计入**本台账任何
计数——本台账的「改 `docs/` 的 commit：6 个」固定不变的理由仍然是「锚点之后的提交不改
统计基线」，不是「锚点之后只改文字」。这里故意不写自己的 SHA：本文件每次被自己提交都会
产生一个新 commit，写进去就得再改一次、永远差一个，与本台账刻意锚死右端是同一个道理。

这批的净效果：`app` 模块 JVM 测试 **86 类 678 例 → 87 类 683 例**（+1 类 / +5 例，
0 失败 0 错误 0 跳过）；C1 相关 JVM 测试 **24 类 291 例 → 25 类 296 例**；
**仪器测试仍是 25 条不变**（没加任何 `androidTest`）；Room 版本不变；
**既有 Kotlin 生产代码零改动，既有测试文件零删改**；新增 1 个入库的 golden 清单
把「导出字节确定」从一次性证据升级成长期护栏。证据登记见上方
「C1-P 群聊导出确定性哈希（零设备）」。

⚠️ **这批与前一批的性质不同，必须说清楚**：前一批 8 个 commit 是纯迁移/落库侧，
**四类证据一份都没有**；这一批**确实**产出了契约 `:206` 第四类「导出 SHA-256」的
一份**零设备**证据。**但它仍不改变任何判定**——契约点名的「**酒馆本体打开**」零份、
viewer 可见消息 ID 零份、模型调用序列零份、token 计数零份，而那份哈希来自 fixture
而非真机 IO 分发的产物。换句话说：**四类里三类零份、第四类只有 JVM 层一份**。
**十例状态仍全 `unverified`，C1-09 也是。**

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
   ⚠️ **零成本的那一半已经做完了**：JVM 里 9 个 fixture 变体的字节数 + SHA-256 已
   实测落定并由 golden 清单长期护栏（见「C1-P 群聊导出确定性哈希（零设备）」）。
   **剩下的不是「再算一遍哈希」，而是换一份文件**：真机经 `writeExportTempFile` +
   `ACTION_SEND` 真实分发出来的那一份、酒馆本体能不能打开、以及扫码那条链路。
   哈希可复算 ≠ 文件被验证过，这两件事别混。
5. **仪器测试 25 个注解**：接上设备后
   `./gradlew --offline :app:connectedDebugAndroidTest`，
   产物会落到 `app/build/outputs/androidTest-results/connected/debug/`。
   零成本的前置那一步（`python3 tools/verification/c1d_migration_30_31_replay.py`）
   **已完成**，输出与结论见上方「C1-D 迁移 30→31 主机侧重放（零设备）」。
   它把 C1-07 的**迁移侧**钉住了，但 `Migration_30_31_Test` 那 7 条仪器用例
   仍需设备，**别拿它把仪器那条勾掉**。
   C1-P 的第二个零成本前置（`python3 tools/verification/c1p_migration_31_32_replay.py`）
   也**已完成**，输出与结论见「C1-P 角色卡元数据落库与 Room 31→32 迁移（零设备）」；
   同样**不能替代** `Migration_31_32_Test` 那 6 条仪器用例。
   ⚠️ 顺带一条设备侧专属动作：拿一个**从 Room 31 升上来**的旧库装上去（不是全新
   安装），确认 31→32 升级不崩、旧行 `group_cards` 是空串、群聊页刷新后导入的角色卡
   还在。这是 B1 剩下的最后一块。

### 逐例要补什么

| 用例 | 缺什么 | 怎么采 |
|---|---|---|
| C1-01 | viewer 可见消息 ID、模型调用序列 | 3 角色群，正文显式 `@角色B`，跑一轮。台账里必须出现：角色 B 收到该条、A 与 C 不可见；同时贴出这一轮只有 B 被调用的一次请求 |
| C1-02 | 模型调用序列（顺序要与日志一致）、token | 同群**不带 @**，`mode=pipeline`，`roles[]` 顺序 A→B→C。贴出 3 次调用的顺序 + 每轮 prompt/completion token，并与 `group_runs` 的 `round_id` 对上 |
| C1-03 | viewer 可见消息 ID（议长视角）、模型调用序列 | `mode=roundtable`，`chair_role_id=C`。台账要能证明 A/B 视角里没有彼此未完成的输出，只有 C 的汇总 prompt 里含全员内容；贴两段调用（全员轮 + 议长汇总轮） |
| C1-04 | 模型调用序列、平票证据 | `mode=vote`，给 3 个结构化候选。贴出各角色选票原文与多数决结果；**平票按配置失败的路径要单独跑一次**并贴错误节点 |
| C1-05 | token 计数（日志含已用/上限/未运行角色） | 把 `token_budget_per_round` 设成让第 2 个角色被截断的值。贴真实 token 累计值 + `group_runs` 里「已用 / 上限 / 未运行角色」三个字段的库内取值 |
| C1-06 | 模型调用序列（部分响应） | 边流式边点停止，再跑一次触发超时。贴：没有空气泡助手消息落库、已生成消息与错误节点都在、已消耗 token 数 |
| C1-07 | 仪器 25 条的结果 + 幂等台账 | 先 `connectedDebugAndroidTest` 拿 25 条结果；再真机让第 2 个角色失败后重试同一 `round_id`，贴库内 `committed_role_ids` 与实际消息条数对得上（不重复） |
| C1-08 | 三个空间的检索结果可见性 + 来源消息 id | 三角色各发一轮后，逐角色检索记忆。贴每个空间的命中列表（消息 id / 角色）与 `source_message_id` 归因，确认互不串、无全局/助手回退 |
| C1-09 | **真机导出的**那份文件 SHA-256 + Tavern 本体互操作 | JVM 层 9 个 fixture 变体的哈希已落定并有 golden 护栏（零设备，见「C1-P 群聊导出确定性哈希（零设备）」），**但那不是真机导出、没走过 IO 分发**。要补的是：真机导出 → `adb pull` → `sha256sum`（拿真机那份，别复用 fixture 的值）；用 Tavern 本体打开该文件截图/录屏；再用 QR 扫码导回另一台设备，贴往返前后 `role_id/round_id/turn_kind/群配置/角色卡` 的逐字段 diff 与两端哈希；**单独验证不含密钥/记忆/授权 token** |
| C1-10 | 筛选只过滤 + 切换不丢数据的真机记录 | 同一助手下混建单聊与群聊，录屏走「全部 → 群聊 → 单聊 → 全部」四个 chip 切换，贴切换前后两侧会话的消息数与最后一条消息 id |

每补完一项，在证据登记表**追加一行**（不覆盖历史行），把四列缺失证据填上，
再把用例矩阵的状态改成 `verified`。**四类证据缺任何一类，仍保持 `unverified`。**

## 已知遗留与风险

1. ~~**角色卡 `RoleCardMeta` 只在导入后显示、不落库。**~~ **这条已作废——现在真落库。**
   （2026-10-05，`20581bcb..b7025665` 8 个 commit。登记见上方「C1-P 角色卡元数据落库与
   Room 31→32 迁移（零设备）」。）早前描述的四点现状全部变了：`Conversation` 除了
   `groupConfig`（`core/data/model/Conversation.kt:35`）现在还有
   `groupCards: List<RoleCardMeta>? = null`（同文件 `:48`）；
   `ConversationEntity.groupConfig` 那条**单列 String**
   （`core/data/db/entity/ConversationEntity.kt:37-38`）**旁边多了第二列**
   `groupCards`（`:52-53`，`@ColumnInfo("group_cards", defaultValue = "")`）；
   导入成功后的当场回执仍在 `GroupChatPage.kt:852-868`（⚠️ 早前版本这里引的
   `:799-803` 已随本次改动**行号漂移**，现在那几行是一个通用 `TextField` 组合函数），
   而面板里另有 `ImportedRoleCardsView`（`:731`）专门显示**从库里读回来的**导入快照；
   `GroupChatPage.kt:438-443` 那段「导入绝不在校验失败时落库」的 KDoc 仍然有效，
   `:445-460` 则已由实现改写成「导入的 cards 落库，与『现场生成的 cards』是两件事」。**剩下的尾巴全是零设备验不到的**：真机 31→32 升级、
   `Migration_31_32_Test` 6 条仪器用例、`ConversationDAO` 真实 SQLite 行为、
   UI 刷新后卡片是否真在。真要盯的是「导入快照 vs 现场生成」的分工——
   **导出永远用现场生成的**（`groupExportRoleCards`），导入快照只落库 + 展示，
   **不合并、不静默替换**，理由见 `beyond-operit-implementation-status.md` 的 B1。
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
   这四个标签的子包归属需要回填或重新归并，实测合计 **42 个 commit**
   （`c1-p` 16 + `c1-s` 9 + `c1-q` 6 = 31，加 11 个裸 `c1` 标签 = 42；
   再前一版这里写的 36 是漏算了裸 `c1`，已订正）。口径同「C1 commit 台账」：
   统计区间锚定在固定 SHA `d45ebd10~1..1b0e04a9`，`--no-merges`。**42 是截至
   `1b0e04a9` 的数**——锚点之后的提交（含那 8 个 C1-P 功能/测试提交与本文件此次提交）
   都不计入，因为它们不改统计基线，逐条见「锚点之后的后续提交」，所以不需要
   再预告「提交后会变成几」。⚠️ 顺带提醒：`c1-p` 的 16 这个数也是截至 `1b0e04a9`
   的数。C1-P 又追加了 **8 个 `c1-p` 标签**提交（`git log --format=%s
   20581bcb..b7025665` 实测：`feat(c1-p)` 5 + `test(c1-p)` 3），但按上面的口径
   **不计入**，所以子包分布表与前缀分布表都不变。

## 判定规则

- JVM 纯函数测试可证明过滤、路由、预算和幂等逻辑；不能替代真机 UI、备份恢复或
  Tavern 本体互操作证据。
- `unverified`、缺命令退出码、缺可见消息集合、缺调用序列或缺哈希的行均不算通过。
- 任一安全约束失败（越权消息、回退到全局记忆、导出密钥、重复提交）直接阻断该
  子包交付，先修复再重新记录。