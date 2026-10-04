# beyond-operit 实施与验收状态

更新：2026-10-04。目标仍为 `beyond-operit-client-changes.md` 与
`beyond-operit-server-changes.md` 的完整范围；本文件不替代总路线规格。
“已有代码”不等于验收通过，未有直接证据的项目都保持未完成。

## 本次落地的代码

- 客户端：`BrowserRuntime` 隐藏 WebView与卡片 `BrowserBridge` 接入、会话状态同步保护。
  不注册 `ChatToolFactory` 内置浏览器工具。浏览器 A1 仍未完成真实站点验收。
- 服务端：新增 items/series/votes 表、统一条目发布/审核/列表/详情/下载/投票、
  旧卡片只读适配、ZIP 内容与路径限制、哈希、manifest 依赖/API 元数据、
  版本绑定审计报告、SemVer 过滤。没有改网关、账户、计量或更新协议。
- 文档：客户端 `docs/hub-backend.md` 与服务端 `docs/extension-market.md`。

服务端本地测试：

```sh
./kotlin test --include-classes='*Market*Test'
./kotlin test
```

市场定向测试覆盖六类新条目、审核授权、公开下载哈希、归档攻击输入、依赖与
SemVer、审计版本绑定、幂等投票和优选、旧卡片发布/fork/投票协议。
全量测试的 9 个既有失败已在未修改的 `f6d3585` 独立工作区复现，涉及
AdminRouting 测试依赖、外部 `/app/cards` 种子/发布资源与 ImageToolbox Lua
脚本。不能宣称服务端全量测试全绿。

2026-10-04 已按 `KodeHeapServer/deploy.sh` 部署到 https://heizige.top。
就绪探针与 `/api/items`、`/api/cards` 冒烟通过。客户端 `marketNewKinds` 仍默认关闭。

## C1 交给下一位（2026-10-05）

现状一句话：**代码层七项缺口全部落地，验收证据 0/10。**

- **代码已实现。** 上节（2026-10-04）列的七条缺口已在 `d45ebd10` 之后补进仓库，
  基线附近 74 个 commit（子包标签与分布见 `docs/eval/c1-group-chat.md` 的
  「C1 commit 台账」）。「已实现」只描述代码写到哪一步，不等于任何用例通过。
- **构建与 JVM 测试层全绿，有硬证据。** 三条离线命令都强制重跑、退出码 0：
  `:app:testDebugUnitTest`（84 类 664 例 0 失败 0 错误 0 跳过）、
  lint 三连（全仓 0 Error）、`:app:packageDebug` + `:app:assembleDebug`
  （3 个 APK，连跑两次字节完全相同）。命令原文、退出码、产物哈希与
  「`--rerun` 只挂紧邻其前那一个 task」这个方法论坑，见
  `docs/eval/c1-group-chat.md` 的「构建与验证证据」，本文不重复。
- **验收证据仍然一项都没有。** 契约 `docs/beyond-operit-client-changes.md:206`
  要求每例保存「各 viewer 可见消息集合 / 实际模型调用序列 / token 计数 /
  导出哈希」，`:232-235` 规定缺任一项就标 `unverified`。磁盘上这四类产物
  **零份**，`docs/eval/c1-group-chat.md` 十例仍全 `unverified`。本机
  `adb devices` 为空，一行 Compose 都没在屏幕上跑过。

这不是保守，是契约自己定的规则。**验收证据只认 `docs/eval/c1-group-chat.md`**；
本节只描述现状与下一步，不代替证据、不改判定。

规格仍以 `beyond-operit-client-changes.md` 的「C1 实施契约」（`:193-206`）与
「C1 执行分包与交付闸门」（`:208-240`）为准，冲突时以那两段为准。

硬约束（沿用上一版，另补 AGPL 边界一条）：

- 不开工 C4。
- 每包结束跑 `./gradlew --offline assembleDebug test lint`，全绿再写已完成。
  注意打 `--rerun` 时要落到 `packageDebug` / `lintAnalyzeDebug` 等真实 task 上。
- `marketNewKinds` 保持默认 false。C1 不依赖市场新 kind。
- 不落第二套消息库。过滤只发生在发给模型的副本上，库存消息保持完整。
- 记忆键必须是 `group:<conversationId>:role:<roleId>`，失败不得回退到全局或助手空间。
- AGPL 边界：SillyTavern（`public/scripts/group-chats.js`，AGPL-3.0，release）与本仓
  `LICENSE`（同为 AGPL-3.0）之间只借鉴**名单顺序**和**整词 `@` 边界**两件事。
  不要照搬它的共享历史设计，也不要把它的伪 `@` 行为引进来。

### 不要推倒重写（函数名已随重构变化）

上一版点名的六个函数**在代码里已经不存在了**，按旧名去 grep 会找不到。对照表：

| 旧名（2026-10-04 版） | 现在的落点 |
|---|---|
| `speakersAfterBudget` | `GroupChat.pendingSpeakers`（`core/data/model/GroupChat.kt:581`）+ `GroupTurnCoordinator` 里的预算判定 |
| `majority` | `GroupChat.tally`（`GroupChat.kt:620`），已是结构化计票 |
| `takeGroupSpeaker` | `ChatManager.takeGroupTurn` |
| `continueGroupTurn` | `ChatManager.handleMessageComplete` 的自我递归 |
| `stampGroupRole` | `ChatManager.stampGroupTurn` |
| `appendVoteSummary` | `GroupTurnCoordinator.voteSummaryMessage`（`feature/chat/GroupTurnCoordinator.kt:511`） |

仍在、不要动的核心：

- `app/src/main/java/heizige/kk/khatkit/app/core/data/model/GroupChat.kt`：
  `buildContext` / `visibleMessages`、`plan`、`parseMentions`、`pendingSpeakers`、
  `validate`（`:404`，13 条字段级校验）、`parseBallot`（`:605`）、`tally`（`:620`）、
  `encodeQr` / `encodeConfig`、`memorySpaceId`、`importShare`（`:728`）、
  `FORBIDDEN_EXPORT_KEYS`（`:276`）+ `findForbiddenKeys`（`:785`）。
- `ai/src/main/java/heizige/kk/khatkit/ai/ui/Message.kt`：契约四字段
  `roleId`（`:29`）/ `mentionRoleIds`（`:35`）/ `roundId`（`:37`）/ `turnKind`（`:39`）。
  ⚠️ `mentionRoleIds` 的 `@SerialName("mentions")`（`:34`）是**有意**保留的旧键，
  KDoc `:30-33` 有说明：C1 之前的落库行存的是 `mentions`，换成 snake_case 会让旧行
  读不出 `mention_role_ids`，而契约 `:198` 要求旧库零迁移。这不是「没做完」。
- `core/data/db/entity/GroupRunEntity.kt`：复合主键 `(conversation_id, round_id)`（`:37`）、
  `run_token` UNIQUE 索引（`:40`）、`run_token` 列（`:55`）、
  `committed_role_ids`（`:73`）。Room 已从 30 到 **31**，迁移是
  **显式手写**的 `core/data/db/migrations/Migration_30_31.kt`（不是 AutoMigration，
  理由在 `:79-84`：AutoMigration 的 `DROP TABLE` 会删掉
  `memory_chunks_ai/ad/au` 三个 FTS5 触发器），schema
  `app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/31.json` 已导出。
- `core/data/repository/MemoryRepository.kt` 的 `MemorySpaceGate`（`:492`）
  与 `GroupMemorySpacePolicy`（`:527`）；
  `core/data/ai/tools/ChatToolFactory.kt` 的 `MemoryToolScopeResolver`（`:57`，无全局/
  助手回退）；`feature/chat/ChatManager.kt` 的 `viewerScopedTools`（`:2049`，
  调用点 `:778`）。
- 新出现的主要文件与角色：`feature/chat/GroupTurnCoordinator.kt`（轮次判定内核）、
  `GroupSpeakerResolver.kt`（消息 → 纯函数身份）、`GroupRoleCompletionProvider.kt`
  （防伪 `@` 选择器）、`GroupMemberBar.kt`（成员头像组）、
  `GroupExportCard.kt`（导出面板卡片）、`GroupRoleCards.kt`（角色卡元数据展示）、
  `GroupTurnModel.kt`（按角色绑定选模型）。
- ⚠️ `TavernChatCodec`（`core/data/ai/tavern/TavernChatCodec.kt`）上一版描述为
  「有往返内核但没有入口」——**现在 `exportGroupJsonl`（`:251`）有生产调用点**
  `feature/chat/GroupExportCard.kt:206`。这类「写好了但没人调」的判断要先 grep
  确认再下结论。

### 逐项状态

| 缺口（2026-10-04 版） | 代码状态 | JVM 证据（类 / 用例数） | 设备证据 |
|---|---|---|---|
| 1 契约字段 + 幂等续跑 | 代码层已实现 | `GroupTurnCoordinatorTest` 63、`GroupRunSchemaTest` 10、`UngeneratedMessageFilterTest` 18 | 无（`adb devices` 空）；仪器 `GroupRunDAOTest` 12 条从未跑过 |
| 2 版本化 config + 字段级校验 | 代码层已实现 | `GroupChatTest` 18（含 extras 往返、未知 schema 拒收） | 无 |
| 3 记忆接线 | 代码层已实现 | `MemorySpaceGateTest` 9 / `MemoryToolScopeTest` 7 / `MemoryAttributionTest` 9 / `GroupMemorySpacePolicyTest` 6 / `MemoryExtractorParseTest` 7 / `MemoryRoleIdMappingTest` 2 | 无 |
| 4 vote 结构化 | 数据结构层已实现，传输层仍是文本约定（见遗留 B5） | `GroupTurnCoordinatorTest` 63 的投票/平票组 | 无 |
| 5 导出 / 恢复 | 代码层已实现（含相机扫码入口） | `GroupTavernExportTest` 22 / `TavernCompatTest` 21 / `QrScannerSheetTest` 16 / `GroupChatTest` 18 | 无；酒馆本体打开 `.jsonl`、真机相机扫码、导出文件 SHA-256 三项全未验 |
| 6 UI 复用管线 + 头像组 + 筛选 | 代码层已实现，一行 Compose 未上屏 | `ConversationListQueryPlanTest` 7 / `ConversationTypeFilterSourceGuardTest` 2 / `GroupSpeakerResolverTest` 8 / `GroupRoleCompletionProviderTest` 8 / `GroupChatTest` 18 | 无；`ConversationDAO` 的 SQL 在真实 SQLite 上的行为也未验 |
| 7 构建与退出码 | **已拿到硬证据** | 见 `docs/eval/c1-group-chat.md`「构建与验证证据」（三条命令退出码全 0） | 不适用 |

### 真正没做完的事

**A. 需要设备的（拿不到就无法验收）**

- 契约 `:206` 点名的四类硬证据，磁盘上**一项都没有**：各 viewer 可见消息 ID 台账、
  真实模型调用序列、真实 token（prompt+completion）计数、群聊导出文件 SHA-256。
  采集手段见 `docs/eval/c1-group-chat.md` 的「下一位怎么把 unverified 变成 verified」。
- 仪器测试 **19 条从未跑过**：`app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/dao/GroupRunDAOTest.kt`
  12 条 + `.../core/data/db/migrations/Migration_30_31_Test.kt` 7 条（已 grep 计数确认）。
  磁盘上唯一的 app 仪器记录是 2026-10-03 12:58:29 的一次 `Process crashed`
  （`tests="0"`，退出码 1，OnePlus `PKG110`），**早于 C1 基线 `d45ebd10` 13.5 小时**，
  不能当 C1 证据。
- 零设备环境事实：一行 Compose 都没在屏幕上跑过。群聊页整页渲染、成员头像点击手感、
  `@` 弹层与 workspace 补全的互斥、导出面板真机分享、扫码弹层的相机权限，
  全是零证据。`ConversationDAO` 的 `:type = ''` 不筛与 `itemCount` 正确性也未在真实
  SQLite 上验过——`app/build.gradle.kts` 单测只有 `libs.junit`（无 Robolectric /
  room-testing / coroutines-test），`ConversationRepository` 是 class 直接依赖 DAO，
  没法注入 fake。
- **有一份零成本动作现在就能做**：`tools/verification/c1d_migration_30_31_replay.py`
  （16,005 字节，已 grep 确认大小；用真实 SQLite 重放 30→31 迁移 SQL）
  **从未运行过**，磁盘上找不到它的输出产物。它不需要设备就能给 C1-D 补一条主机侧
  证据，但它自己的 KDoc 写明**不能替代 `Migration_30_31_Test`**——别拿它把仪器那条勾掉。

**B. 已知遗留与风险（代码层，需要产品/架构决策）**

- B1 角色卡只显示不落库。`GroupSharePayload.cards` 里的 `RoleCardMeta`
  （`core/data/model/GroupChat.kt:95`）扫码/粘贴后只显示（`GroupChatPage.kt:799-803`），
  不入库：`Conversation` 只有 `groupConfig` 一个字段，数据库 `conversationentity`
  只有 `group_config` 一列，`GroupConfigSerializer` 里没有 cards 的位置。要落库得动
  `Conversation` + `ConversationEntity` + `ConversationDAO` + Room 31→32，
  且**必须手写显式 `Migration_31_32`**（理由同 `Migration_30_31.kt:79-84`，
  AutoMigration 的 `DROP TABLE` 会删掉三个 FTS5 触发器）。
- B2 抽屉搜索路缺 `folder_id = ''`：`core/data/db/dao/ConversationDAO.kt:38`
  的未归档路有，`:66` 的搜索路没有。效果是无搜索词时只显示未归档、一搜就把文件夹内
  会话混进来。**这是产品决策，尚未定**，本轮未擅自改。
- B3 `GroupRole.modelId` 显示侧与生成侧现在同口径（`ChatManager.kt:726` 的
  `resolveGroupTurnModelId`，定义在 `feature/chat/GroupTurnModel.kt:54`），但
  **本轮之前生成的历史消息** `message.modelId` 记的是助手绑的模型，与现在气泡显示的
  不一致。要不要写数据迁移回填是独立决定。
- B4 `ChatManager.kt:681` / `:686` 的 `senderName` 与 `useExternalWebSearch` 仍是
  会话级模型（都在群分支 `:705` 之前算好，群聊分支只重算了 `model` 本身）。
  后台通知标题可能显示错模型。
- B5 vote 选票仍靠 `VOTE:` 前缀正则匹配模型自由文本（`GroupChat.kt:605-617`），
  不是 tool-call 强约束。契约 `:201` 说「只接受结构化候选/票」——**数据结构层面
  已满足**（`VoteBallot` / `VoteOutcome` 在 `GroupChat.kt:133` / `:141`，
  `GroupTurnCoordinator.kt:438` 的 `resolveVote` 只从 `plan.candidates` 取候选、
  不猜自由文本），**传输层面仍是文本约定**。这是已知弱点，不是「已完成」。
- B6 `card-validator` 模块没有 lint 报告：`card-validator/build/reports/` 里只有
  `tests/`，找不到任何 `lint-results*`。这是「没跑」不是「0 命中」，台账里别给它记 0。
- B7 抽屉筛选 chip 来回切换、群聊页整页渲染等 UI 行为无设备证据。
- B8 `GroupChatPage` 的 `onEdit` 只放行 USER（`GroupChatPage.kt:292`），
  `feature/chat/GroupMessageActions.kt` 侧再用 `if (!groupChat)` 关掉重新生成 /
  创建分支 / 删除 / 分支切换（`:126` / `:217`）。理由是这些动作会改写消息，使
  `group_runs.committed_role_ids` 与实际消息错位。**要放开必须同步改写
  `committed_role_ids`**，不是纯 UI 改动。

### 下一位的行动顺序

按「零设备就能做 → 需要设备才能做」排。前三项今天就能落。

1. **零设备，立刻可做**
   1. 跑 `python3 tools/verification/c1d_migration_30_31_replay.py`，把命令、退出码、
      输出路径登记进 `docs/eval/c1-group-chat.md` 的 C1-D / C1-07 行。
      注明它不能替代仪器测试。
   2. 给 `ConversationDAO` 的 SQL 补仪器测试源码（`androidTest`，用已有的
      `androidx.room.testing`）：`:type = ''` 时不筛、`itemCount` 正确、
      搜索路与未归档路口径一致。写好先不跑，等接设备。
   3. 定 B2 的抽屉搜索 `folder_id` 口径（产品决策），定了再改 DAO 或在验收表里
      记成已知缺口。
2. **需要接真机**
   1. 真机建一个 3 角色群聊跑一轮 `mode=pipeline`，从库里按 `role_id` 分组导出每个
      viewer 的可见消息 ID 台账（用真实 `message.id`，不是测试构造值）。
   2. 同一群跑 `mode=roundtable` 与 `mode=vote`，vote 的平票路径单独跑一次。
   3. 采集真实模型调用序列与 prompt+completion token；预算用例要让第 2 个角色被截断，
      贴出「已用 / 上限 / 未运行角色」三个数。
   4. 导出 `.jsonl` 后 `adb pull` 立刻 `sha256sum`；用 SillyTavern 本体打开该文件。
   5. 相机扫码导回另一台设备，逐字段 diff `role_id` / `round_id` / `turn_kind` /
      群配置 / 角色卡，并单独验证不含密钥、记忆、授权 token。
   6. 跑 `./gradlew --offline :app:connectedDebugAndroidTest` 拿那 19 条仪器结果。
3. **登记规则**
   每补齐一项，在 `docs/eval/c1-group-chat.md` 的「证据登记」表**追加一行**
   （不覆盖历史行），四类证据列齐才把用例矩阵状态改成 `verified`。
   判定规则在该文件末尾「判定规则」，本文不重复抄。

最后一句口径：**代码已实现 ≠ 用例通过；`docs/eval/c1-group-chat.md` 十例仍全
`unverified`，C1 不能标成已完成。**

## 客户端包验收清单

| 包 | 代码现状/证据 | 必须继续实现或验证 |
| --- | --- | --- |
| A1 浏览器 | 无设置页；WebView 会话与动作、语义/增量快照、截图、Room 历史、卡片脚本桥已写入；签到/比价卡已移至 KhatKitCards，AI 通过卡片工具调用；JVM 与设备本地 HTML 有定向证据 | 独立 cookie；真实网站登录/重定向/两张卡验收；更多 WebView/设备版本验证 |
| A2 记忆 | 空间/检索/工具/注入已落地；来源可点击跳到原消息节点和对应分支 | 50 文档/20 问 ≥80% 与 5k P95 <300ms 实测；记忆图谱 Mermaid UI；bi-temporal 冲突标记 |
| A4 视觉 GUI | 同构动作、双通道定位、JSONL 轨迹、TracePlayer、设置页轨迹审计（逐步查看动作/结果/命中/截图路径） | 10 任务成功率 >85% 与 3 台设备矩阵实测；有节点树时误差 0 的真机验证；截图缩略图；可选虚拟显示回退；录制卡导出 |
| A5 酒馆 | 宏 v1、角色卡原文保留与 JSON/PNG 双向、lorebook AND/OR/NOT/递归/概率、ST 聊天 swipe、instruct/sampler 子集已有代码与 JVM 对照测试；子集说明见 `docs/tavern-compat.md` | 酒馆本体打开 PNG/聊天文件；20 张第三方真实卡；带工具角色的真机设备任务演示 |
| B1 工作流 | 规范 JSON、10 类节点、FlowSpec/Lua 导出、3 个等价性用例、Room 三表、失败跳过/续跑/取消、现有 `/api/events` 的 `workflow_run`、通知分类示例与逐步日志。对照见开源参考 B1。`docs/flow.md` §6 已完成 | 自由画布；自然语言经 GenerationLoop 生成；真机把 FlowSpec 交给 `khatkit__run_flow` 跑通卡片；成本统计接真实 Token |
| B2 ToolPkg | 本地包校验、hooks 能力边界、插件设置 schema、Provider 声明解析、静态审计报告三样例；`marketNewKinds` 默认 false | dex 热加载、市场 UI、服务端上线后联调 |
| B3 路由 | 三策略、Key 池指数退避/半开、预算降级或只读、7 个消费点调用 `taskBinding()`、统计页路由计数；对照见开源参考 B3 | 设置页策略编辑；价格表 JSON；真机 429 对话无感 |
| C1 群聊 | 内核和一版 UI 已写入，**未验收**。详见下方「C1 交给下一位」。`docs/eval/c1-group-chat.md` 十例全部 `unverified` | 按该节缺口续写；补证据前不得把 C1 标成已完成 |
| C2 工作区 | 现有 workspace/proot 与文件工具可复用 | 五种模板与项目规则；preview；内容寻址 diff/revert 与聊天重发回滚；SSH/SFTP 许可评估和读写后端；APK/HTML 打包；端到端及路径穿越测试 |
| C3 语音入口 | 现有 VoiceSessionController 可复用 | Sherpa 唤醒/前台服务/VAD/流式 ASR；全双工打断与草稿；ASSIST 面板、Widget、气泡、取词悬浮球；VITS 依赖包；真机误触/保活/500ms 打断/1s ASSIST 验证 |
| C4 虚拟形象 | 尚未完成审计 | glTF 模块、五状态、口型/视线/情绪事件；桌面宠物；dependency 安装卸载；骁龙 7 系 60fps 真机验证 |
| D0 UI DSL | 宿主 NodeTree/22 节点/事件回传/旧表单 deprecated 兼容；12/15 卡迁移（3 卡无 UI）；后建 6 卡均已是新 DSL 或无 UI；validator 21 卡 0 错误；2026-10-03 全量 build/test/lint 绿 | 真机交互与人工业务验收；`UI_DSL_CLIENT_CHANGES.md` 拆分文档未创建（内容由卡片仓库 `MIGRATION_PROGRESS.md` 覆盖） |

排除项保持原决策：不做 A3 端侧推理、不做酒馆 L5、不引入 RemoteCompose，
不新增 resource 品类、不启用 S6 扩展计量。

## 服务端包与剩余验收

| 项 | 本地证据 | 仍未完成 |
| --- | --- | --- |
| S1 | 新协议、六新 kind、旧 card 适配、搜索/分页、归档下载、投票与优选；路由测试 | 六类客户端安装、生产迁移/部署；大规模索引性能；线上旧客户端冒烟 |
| S2 | ZIP manifest 一致性、API 版本/入口/依赖范围、pending 审核 | 客户端安装依赖解析；实际 ToolPkg 与 Provider 插件运行联调 |
| S3 | 按不可变归档哈希绑定报告/规则版本/分数；旧版报告保留；接口对象返回 | card-validator 报告生成与三样例风险对照；安装前审计 UI；服务端复检为可选增强 |
| S4 | SemVer 数字段/prerelease/build/非法值测试；app_version 过滤 | 生产 PostgreSQL 和客户端最低版本提示联调 |
| S5 | 遵循默认路线 A | 形象/TTS 资源包与客户端运行时验收 |
| S6 | 默认不做 | 无新增任务 |
| S7 | 两仓库协议文档已同步本地实现 | 部署后补实际兼容性/安装验收记录 |

## 最终总验收仍全部待证

- 20 个端到端任务（浏览器/文件/设备/开发各 5）与基线 +10pp 的评测脚本。
- 酒馆 20 卡无损、15 条 lorebook 对照与设备任务演示。
- 记忆命中率、全部来源可追溯与 P95 性能。
- 三级工具审批与轨迹审计。
- 每包要求的测试，以及 Android `assembleDebug test lint` 全绿。
- HubClient 只在 B2 范围扩展；旧市场、收费、网关兼容。
- 六种新 kind 从发布到安装/卸载/更新的完整验收。

下一实施入口：继续补 A1 的实际页面状态与完整动作/桥/UI，同时推进 B2 与
当前服务端契约衔接。生产验证须先通知用户；等待线上条件不阻塞本地包实现。

## A1 后续进展（2026-10-03）

已将八项基础工具扩到十二项：补 extract_text/scroll/press_key/hover，
交互支持显式 `session_id`，同标签操作用 mutex 串行、不同标签独立。
快照每次读取实际 DOM，含语义元素、bbox、可交互性、稳定 `@b` 引用；
支持 selector 根节点和 next_offset 分页。快照对象限制约 16KB UTF-8，
旧 HTML 快照也修复了截断标记超字节上限的问题。初始导航失败销毁会话，
导航/JS 执行有超时，所有 WebView 调用在主线程。

浏览器包 Gradle 定向单测通过。新增 QuickJS 执行测试覆盖特殊字符作为参数、
输入事件、500 元素 Unicode 页面、动态 DOM、分页引用与失效引用、按键取消、
滚动/悬停；另有显式会话路由和 UTF-8 限额测试。
这些是 DOM fixture/JVM 证据，不能替代三标签真机并发、登录流程等验收。
当前行为与限制详见 `docs/browser-agent.md`。

A1 后续补充：Room 历史/书签、旧 JSON 一次性导入与异常文件保留、
viewport 截图（多模态工具返回）、卡片 `BrowserBridge.open/act/snapshot/close`
与 API 文档已写入。卡片运行隔离标签所有权、默认审批、deadline 与退出清理；
导航域名检查已接入，WebView 网络拦截不构成完整网络沙箱。
新增数据库恢复/并发/损坏数据及截图真机测试代码。上次 instrumentation 在启动阶段
崩溃、0 测试；本次环境 ADB 无法监听，尚不能宣称真机通过。

开源参考按用户补充记录到 `beyond-operit-open-source-references.md`；
已确认 agent-browser 归属以及 QGraph/Key Router 源码入口，未复制上游源码。

A1 尚未完成：独立 cookie、真实网站登录/重定向、两张卡片真实站点验收。

本轮检查边界：

- Room/截图阶段：Gradle 浏览器 JVM 17 项通过，AndroidTest Kotlin 编译通过，
  已生成独立 Room v1 schema；没有执行成功的 Room/截图真机测试。
- 后续脚本桥阶段：Gradle 在启动时因 `Could not determine a usable wildcard IP`
  失败，未到编译。使用缓存 Kotlin 2.4.20 编译器检查了运行时、脚本桥、
  registry/decorator/factory、执行器和校验器；局部检查不能替代 Hilt/全量构建。
- 新桥的审批、deadline、标签归属、域名检查、资源登记与脚本 API 文档测试
  独立运行通过。卡片声明校验也纳入定向检查。

## A1 增量快照与示例（2026-10-03，后续）

- 元素引用增加文档标识，避免导航后旧 `@b1` 误匹配新文档；失效引用有回归测试。
- `since=snapshot_id` 请求单页增量，返回 added/changed/removed/order；
  不同 scope、已淘汰基线和导航后均回退 full，最多保留 4 份基线；
  测试用增量重建结果与完整快照逐项比较。
- `browser_wait` 与卡片 wait 动作等待可见 selector/目标文本，最多 30 秒；
  用于异步登录/签到反馈，避免固定等待后误报成功。
- `docs/examples/` 包含两张标准 JS 卡片：签到、2–3 标签同币种价格对比；
  已测试原样脚本成功/失败/关闭标签、manifest 声明、币种拒绝。
  真实站点域名和选择器需要配置，未发布到 Hub。
- 最新浏览器 JVM 28 项均通过；app Kotlin/Hilt、AndroidTest Kotlin 编译通过。
  本轮 `assembleDebug test lint :app:compileDebugAndroidTestKotlin` 整条命令
  `BUILD SUCCESSFUL`（6m34s，849 tasks）。这是构建/JVM/lint 证据，不是设备验收。
  `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` 已生成；
  ADB 启动监听仍报 Operation not permitted，未安装手机。

此前“增量差异快照、示例卡片代码尚缺”的状态由本节更新；完整 UI、独立 cookie、
Assistant 域名配置和真机/真实站点验收依然未完成。

## A1 浏览器界面（已撤销）

曾实现设置入口和共享浏览器页，后按用户决定全部移除。URL WebView 路由继续使用
原 `WebViewPage`；浏览器运行时仅由卡片桥创建，不提供普通下载 UI。

## A1 卡片扩展边界（后续修订）

按用户决定，浏览器不提供设置页，也不注册为聊天内置 `browser_*` 工具。
宿主只向声明 `requires.bridges: ["browser"]` 的卡片注入运行时；AI 通过普通
`khatkit__<card-name>` 工具调用。每次运行的标签归该卡片所有，退出统一销毁；
域名来自不可变的 `network.allow`，方法权限来自 `permissions.methods`。
签到与比价卡片的正式维护位置为 `/home/heizige/文档/Android/KhatKitCards`。
卡片仓库统一校验共 17 张、0 错误；浏览器 QuickJS 测试直接读取该正式目录。

## A1 设备测试（2026-10-03，PKG110 / API 36）

ADB 恢复连接，Debug APK 安装并启动成功。设备测试首次被系统后台回收；
退出记录为 `OTHER KILLS BY SYSTEM / Cached(nirvana)`。通过 ActivityScenario
保持前台后，4 项 Room 测试通过，2 项 WebView 测试暴露本地 HTML fixture 的
historyUrl 未设置导致 `view.url=about:blank`。修正 fixture 后，这 2 项均通过
（4.714s）；没有删断言或扩大超时。

已验证三标签并发 DOM 输入/点击/等待、150 元素页面 UTF-8 快照 <20KB、
1080×1920 PNG 非空、宿主更换保留 WebView、关闭标签/导航引用失效、
卡片域名回调、Room 导入/损坏数据/恢复/并发。
这是本地 HTML 与实际 WebView/Room 的证据，不代表真实站点认证、重定向链、
浏览器完整 UI 或独立 cookie 已验收。详见 `browser-device-validation.md`。

设备测试修正后，当前工作区再次完成
`./gradlew --offline assembleDebug test lint :app:compileDebugAndroidTestKotlin`，
该记录早于“仅卡片扩展”的最终边界，最新验证结果见后续记录。

## A1 Card-only 最终收口（2026-10-03）

- 已删除设置入口、浏览器页面、Assistant 浏览器配置以及不可注册的
  `browser_*` 直连工具构造器；`ChatToolFactory` 不暴露浏览器工具。
- 宿主仅保留 `CardBrowserBridge` 和内部浏览器运行时，卡片必须通过 manifest
  声明 bridge、方法权限与 `network.allow` 后才能调用。
- `KhatKitCards` 全目录校验 17 张卡、0 错误；浏览器卡 QuickJS 测试直接读取
  `browser_checkin` 与 `browser_compare_prices` 的正式文件。
- `:app:compileDebugKotlin`、浏览器 JVM 定向测试、`:card-validator:test` 和
  `assembleDebug` 同批执行成功（542 tasks）。
- 最新 `app-arm64-v8a-debug.apk` 已覆盖安装到 PKG110 并成功启动。

## A2 记忆系统升级（2026-10-03）

参考 clawdiney（MIT）/ mem0（Apache-2.0）/ sqlite-vector（Apache-2.0），本地 Kotlin/Room 重写，
对照表见 `beyond-operit-open-source-references.md` §A2。

### 数据层

- Room v27→v28（`Migration_27_28`）：新建 `memory_spaces / memory_chunks / memory_edges / memory_mentions`；
  旧 `MemoryEntity`（KV）行迁移为 `memory_chunks`（`__global__` → GLOBAL 空间，其余 → ASSISTANT 空间），随后删旧表。
- `memory_chunks`：content + sourceKind/ sourceMessageId / sourceRefId / confidence / extractedAt /
  lastHitAt / deletedAt（软删）/ embedding BLOB。
- `memory_edges`：source_name / rel_type / target_name（隐式图谱节点）+ evidence_chunk_id + invalidated_at。
- `memory_mentions`：chunk↔entity 与 message_id（来源跳转锚点）。
- FTS5：`memory_chunk_fts` 外部内容 + INSERT/DELETE/UPDATE OF content 触发器（`onOpen` 建，旧 `memory_fts` 废弃）。
- sqlite-vector：`SQLiteConfiguration` 注册 `libvector`/`vector` 扩展（文件存在才注册）；
  `MemoryVectorIndex.ensure` 对 `memory_chunks.embedding` 调 `vector_init`。失败静默。

### 检索

- `MemoryRetrievalEngine`：RRF k=60，四路信号 = FTS5 BM25 / 向量 KNN / 图谱扩展 / 时间衰减（半衰期 30 天）。
- 向量信号：sqlite-vector `vector_full_scan` 优先，不可用时 Kotlin 暴力余弦（JVM 测试走此路）。
- 图谱扩展：top hits → mentions.entity_name + edges → 共享实体的其他 chunk。
- fail-soft per retriever；`searchHybridWithEmbedding` 接外部 query 向量。

### 工具与注入

- `memory_search`（混合检索，结果带 id/sourceMessageId/confidence/extractedAt）
  / `memory_add`（带 confidence）/ `memory_link`（chunk↔chunk 关系）/ `memory_forget`（软删 + 边失效）。
- `<memories>` 注入：ChatService 从全量 dump 改为 `searchMemories`（最近 6 条消息做 query），JSON 含 id/来源/置信度。
- 自动抽取：`MemoryExtractor`（fast model，ADD-only JSON facts + 三元组），`Assistant.autoExtractMemory` 开关，
  在 ChatService/ChatManager 的 onCompletion 触发。

### UI

- `AssistantMemoryPage`：新增「自动记忆抽取」开关；`MemoryItem` 显示来源 messageId / 置信度 / sourceKind。

### 验证

- 单测：`MemoryRetrievalEngineTest`（RRF/衰减/FTS 清洗 8 项）、`MemoryExtractorParseTest`（抽取容错 7 项）、
  `MemoryToolsSearchTest`（溯源字段 3 项）。
- `./gradlew --offline assembleDebug test lint` BUILD SUCCESSFUL（含 lint 修复 1 处预存 ExtraTranslation）。
- `:app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL。

### 未完成 / 风险

- 50 文档/20 问 ≥80% 命中率与 5k P95 <300ms **实测未做**（需评测脚本与真机）。
- 记忆图谱 Mermaid UI 未做（`assets/html/mermaid.min.js` 可复用）。
- 来源跳转到原消息：当前只显示 messageId 前 8 位，未做点击跳转。
- sqlite-vector 扩展加载路径在真机上未验证（`/vector` vs `/libvector`）；有 Kotlin 暴力余弦兜底不影响功能。
- bi-temporal 冲突标记（clawdiney 的 is_conflict/CONTRADICTS）未做。
- Room/SQLite 真机迁移测试未跑（JVM 编译与 lint 已过）。

## A4 视觉 GUI 自动化（2026-10-03）

screen2prompt 上游未核实（PyPI 页面 JS challenge），未复制源码；本地自研双通道实现。

### 同构动作模型

- `khatkit/record/UiAction.kt`：`RecordedStep` 超集（+PRESS/GLOBAL +bbox 目标），
  `fromRecordedStep`/`toRecordedStep` 双向转换 → 「录制 → 回放 → Agent 生成」三者同构。
  选择器优先级与 RecordedStep 一致：viewId > text > desc > 坐标/bbox 中心。
- `UiBBox`：含式包围盒，IoU / 中心包含几何原语。

### 双通道定位

- `khatkit/record/VisualGrounding.kt`：视觉 bbox → 无障碍节点匹配（纯函数，JVM 可测）。
  中心包含优先（score 1.0），IoU ≥ 0.25 兜底；命中节点用节点中心（误差 0），否则 bbox 中心（声明 ≤8dp）。
- 宿主侧 `KhatKitToolProvider.resolveVisualTarget`：dumpWindow → NodeCandidate 映射。

### 轨迹落盘与回放

- `AutomationTracer`：JSONL 轨迹（`filesDir/khatkit/traces/<id>.jsonl` + index.json），
  每步记录截图帧路径 + 动作 DTO + 目标 + 结果 + 命中方式。
- `TracePlayer.replay`：按序重放（与 device_act 同一无障碍桥执行面），
  支持步间间隔、取消、`onStep` 回调；`toUiAction`/`toActionDto` 与 UiAction 互转。
- 录制卡导出（`RecordingCardFactory`）复用已有机制，轨迹可转 RecordedStep 列表（待接 UI）。

### 工具升级

- `khatkit__device_act`：新增 `bbox`（视觉包围盒）与 `label` 参数；
  tap/click 类动作带 bbox 时先做双通道解析（命中节点中心误差 0）；全部动作入轨迹。
- `khatkit__device_screen`：结果新增 `image_width`/`image_height`（bbox 坐标空间），
  描述明确 bbox 必须用该坐标系。

### 验证

- 单测：`VisualGroundingTest`（10 项，含中心包含/IoU/零面积/空表）、
  `UiActionTest`（7 项，含类型同构 round-trip）、`AutomationTracerTest`（5 项，含 JSONL 往返）。
- `./gradlew --offline assembleDebug test lint :app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL。

### 未完成 / 风险

- 「10 任务成功率 >85%」与「3 台设备矩阵」实测未做（需真机 + 真实设置页任务）。
- 有节点树时误差 0 的真机验证未做（JVM 单测已覆盖逻辑）。
- 轨迹回放 UI（审计界面展示截图序列）未做；当前只有 TracePlayer 引擎与 JSONL 存储。
- 录制卡导出 UI 未接（RecordingCardFactory 可用，需要 RecordCardSheet 挂轨迹入口）。
- 虚拟显示器（Root/Shizuku）未做；`captureScreen` 仅走真实屏幕。
- screen2prompt 元素标注（截图叠加编号标号）未做；当前依赖模型直接看图输出 bbox。

## device_act / device_screen 卡片化（2026-10-03）

按用户决定，A1 浏览器的卡片化先例推广到 A4 设备控制：

### 分工

| 形态 | 内容 |
|---|---|
| **内置 `khatkit__device_act`**（宿主专用） | `overlay_hide` / `overlay_show`（AutomationBus 看板）、`screen_state`、`unlock`（root KEYCODE_WAKEUP+上滑）。审批会话（三级审批 + 看板实时状态 + 取消）留宿主。 |
| **卡片 `a11y_act`** | click_text/click_id/tap/swipe/press/set_text/back/home/recents/notifications/open_app/wait_text/wake/ui_snapshot。经 `accessibility` 桥，重试 3 次 + 失败候选。 |
| **卡片 `a11y_screen`** | 截图 PNG（随结果附图，模型可看）+ OCR + 节点清单。 |
| **卡片 `a11y_visual_act`** | 视觉 bbox 双通道定位 + 动作 + 轨迹。 |
| **卡片 `a11y_trace_replay`** | 轨迹列表 / 审计 / 重放。 |
| **流步骤**（不变） | `runFlowStep` 的 `device_screen` / `device_act` 仍走宿主 `captureDeviceScreen` / `deviceAct`（全动作），flow 不需改。 |

### 宿主改动

- `KhatKitTools.kt`：删除 `deviceScreenTool`；`deviceActTool` 精简为宿主 4 动作，
  `HOST_DEVICE_ACTIONS` 闸门拒绝其余动作并提示改用 a11y_* 卡片；`deviceAct`/`captureDeviceScreen`
  函数保留供流步骤。AI 工具列表 = 卡片工具 + 宿主 `device_act`。

### 验证

- `card-validator` 全目录 21 卡 0 错误；4 张 a11y_* Lua 语法通过。
- `./gradlew --offline assembleDebug test lint :app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL。

### 未完成 / 风险

- 卡片动作走卡片级授权（`privilege: elevated`），非 AutomationBus 三级审批——与内置宿主动作的审批模型不同。
- 卡片 `a11y_act` 的重试间隔受 `tool.sleep` 整数秒限制（下限 1s），比内置 400ms 粗。
- `a11y_screen` 未返回 image_width/height（Lua 解 PNG 头未做）；坐标系以节点 bounds 为准。
- 真机 AI 循环（a11y_screen 看图 → a11y_act/a11y_visual_act 操作）未实测。

## D0 卡片 UI DSL 复核（2026-10-03）

- 按卡片仓库 `UI_DSL_UPGRADE.md` 与 `MIGRATION_PROGRESS.md`（T1–T8）复核：宿主与卡片迁移均已完成，
  此前状态表 D0 行「未审计」过时。
- 全仓旧 `ui.form(items 数组)` 语法 **0 命中**（`rg 'type="(input|select|switch|slider)"'` 清零）；
  `card.json` 中的 `items` 为清单字段，与 UI 无关，不计入。
- 后建 6 卡确认无需迁移：`a11y-act`/`a11y-trace-replay`/`a11y-visual-act` 建卡即用新 DSL，
  `a11y-screen` 仅 `ui.progress`，`browser_checkin`/`browser_compare_prices` 为 JS 卡无 UI（DSL 不注入 JS）。
- 验证：card-validator 21 卡 0 错误；`./gradlew --offline assembleDebug test lint`
  BUILD SUCCESSFUL（3m28s，835 tasks）。
- 仍未完成：真机交互与人工业务验收（当前 ADB 无设备）；`UI_DSL_CLIENT_CHANGES.md` 拆分文档未单独创建。

## A5 酒馆生态兼容（2026-10-03）

兼容子集写在 `docs/tavern-compat.md`。不接 L5，不把群聊语义或角色卡市场做进本包。

- 角色卡：`chara_card_v2/v3` 原文存 `Assistant.tavernCardJson`，导出 JSON 回写。PNG 按 SillyTavern `character-card-parser.js`：`chara` 保留原文，`ccv3` 把 spec 改成 v3，读取优先 `ccv3`。助手编辑页在已导入卡片上提供导出。
- 开场白：`alternate_greetings` 作为首条消息的 swipe，不拆成连续轮次。
- 聊天导出增加 JSONL，与酒馆 `chats/` 文件一致。数组格式仍可读。
- 宏 v1：`{{user}}` `{{char}}` `{{random:a|b}}` `{{roll:1d6}}` `{{lastMessage}}` `{{time}}`，未识别宏保留。
- lorebook：主关键字 OR；selective 次键对齐酒馆 0–3；递归、概率、优先级进 `LorebookEngine`，挂在 `PromptInjectionTransformer`。角色卡内嵌 character book 随助手生效，不依赖设置里的 lorebook id。
- 聊天 JSON v1：`swipes` → `MessageNode`，`swipe_id` 为选中下标，超集字段回写。
- instruct / sampler 子集：顺序包裹与温度/top_p/max_tokens 映射，未知字段透传。

### 验证

- `LorebookEngineTest` 覆盖 OR、大小写、正则、常驻、禁用、AND_ANY/AND_ALL/NOT_ANY/NOT_ALL、概率、优先级、递归开/关、exclude/prevent、扫描深度。
- `TavernCompatTest`：20 张规格卡 JSON 往返字段不丢；PNG `chara`/`ccv3` 往返且 metadata-extractor 可见；聊天 swipe 与 extra 回写；instruct 顺序；sampler 透传。
- 20 张卡是按 v2/v3 字段生成的规格夹具，不是第三方角色卡文件。
- `./gradlew --offline assembleDebug test lint` BUILD SUCCESSFUL（3m20s，839 tasks）。

### 未完成 / 风险

- 未用 SillyTavern 本体打开 PNG 或聊天文件。
- 未做 20 张第三方真实卡回归，也未做「带工具的酒馆角色」真机设备任务。
- 聊天导出目前是编解码 API，会话页还没有导出按钮。
- C4 未开工。B2 新 kind 入口仍未暴露。

## B1 可视化工作流（2026-10-03）

已完成：

- 规范 JSON 与 10 类节点。触发器只存 TriggerEngine 已有的 18 种事件名，或本地 `manual`。
- `WorkflowEngine` 按 QGraph 的 Kahn / 失败下游跳过 / 成功节点续跑重写。输出编译成 `parseFlowSpec` 可读的 steps JSON。
- 同一输入下，可视化执行与导出 Lua 的成功节点输出一致。线性、紧急分支、非紧急分支、三次循环四个用例通过。
- Room 28→29：`workflows` / `workflow_runs` / `workflow_run_steps`。旧 SharedPreferences 在表为空时迁入。
- 本机 `/api/events` 增加 `workflow_run`。不新开 Hub 协议。
- 编辑页接到这 10 类节点，可加载「通知分类」示例，预览运行可逐步展开输入/输出。
- `docs/flow.md` §6 已完成。
- `./gradlew --offline assembleDebug test lint` BUILD SUCCESSFUL（3m14s，835 tasks）。这是构建与 JVM 证据，不是真机跑卡片。

未完成：自由画布、自然语言经 GenerationLoop 生成 FlowSpec、真机把编译结果交给 `khatkit__run_flow` 执行已安装卡片、成本接真实 Token。C4 未开工。`marketNewKinds` 仍默认关闭。
