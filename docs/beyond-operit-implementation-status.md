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
  统计区间 `d45ebd10~1..1b0e04a9`（**锚定在固定 SHA**，不随本文后续提交变动）
  共 **78 个 commit**：`git log --oneline d45ebd10~1..1b0e04a9 | wc -l` = 78，
  区间内 `--merges` 命中 0。锚点选在「C1-D 迁移重放证据登记」那次提交。早前用
  「本文当前 commit」当右端，每改一次文档数字就自己过期一次（71→72→74→78→79），
  所以改成固定右端。
  ⚠️ **锚点之后已经有功能提交了**（早前这里写的「锚点之后只做文字订正」已过期）：
  `20581bcb..b7025665` 共 8 个 commit 做了 C1-P 角色卡元数据落库 + Room 31→32 迁移。
   **78 这个数刻意不动**——它是「截至 `1b0e04a9` 的数」，改文档不该让它变；那 8 个
   commit 不改台账口径（纯功能/测试提交、不动统计基线），逐条列在
   `docs/eval/c1-group-chat.md` 台账的「锚点之后的后续提交」小节。
   前缀分布、子包标签与各子计数见 `docs/eval/c1-group-chat.md` 台账「C1 commit 台账」。
   ⚠️ **锚点之后又追加了三批**（本轮 2026-10-05 是第三次）：`366b3fe8..0db807d2` 那 10 个
   （滚动条 + 抽屉 `folder_id` + 通知标题）、`cac0c58a` / `1c8ca4c8` 两个纯文档提交、
   以及 **`215296f1..6982869b` 那 8 个 C1-S 提交**（抽屉 type 筛选主机侧重放 +
   **修掉两个真实缺陷**）。**三批同样不计入**：78 / 6 / 17、前缀分布、子包分布
   **一个数都没动**，`d45ebd10~1..1b0e04a9` **没有被重算**。逐条见那三个小节。
   ⚠️⚠️ **锚点之后现在累计到第五批（本轮 2026-10-05 晚是第四次追加）**：
   `074e0e20..a9d2077e` 那 6 个（真实 HTTP 证据采集 + 三处测试修正）、
   **`af104850..6ba95422` 那 6 个**（真实公网网关证据 + P0/P1/P2 三批补测 +
   端点群聊门禁 + 平票脚手架回收）。**同样不计入**：78 / 6 / 17 与两张分布表
   **一个数都没动**——**锚点 `1b0e04a9` 没有被重算**。
   ⚠️ **锚点之后现在累计到第六批（本轮 2026-10-06 是第五次追加）**：本轮 **`a8fa1cb8` /
   `b96912cb` / `df560180` 那 3 个**（vote 选票注入缺口：先写红测试 → `collectBallots` 补
   `turn_kind` 门禁 → 补 `parseCandidates` 零覆盖分支与两处现状钉桩；详见「第十批」一节）。
   ⚠️ **本批是「锚点之后第一批改运行时判定逻辑的 vote 提交」**，所以**同样不计入**：
   **78 / 6 / 17 与两张分布表一个数都没动，锚点 `1b0e04a9` 没有被重算**，`git diff` 里
   **没有任何一行删除了 78 / 6 / 17**。⚠️ 但本批**确实改变一个数**——`:app:testDebugUnitTest`
   全量由 `110 类 / 881 例` 变为 **`110 类 / 886 例`**（`+5`：+1 注入缺口红测试、
   +4 `parseCandidates`/fence 覆盖），类数不变。
   「已实现」只描述代码写到哪一步，不等于任何用例通过。

- **构建与 JVM 测试层全绿，有硬证据。** 三条离线命令都强制重跑、退出码 0：
  `:app:testDebugUnitTest`（86 类 678 例 0 失败 0 错误 0 跳过；C1-P 之前是 84 类 664 例，
  本次新增 `GroupRoleCardsPersistenceTest` 7 + `ConversationGroupCardsSchemaTest` 7）、
  lint 三连（全仓 0 Error）、`:app:packageDebug` + `:app:assembleDebug`
  （3 个 APK，连跑两次字节完全相同）。命令原文、退出码、产物哈希与
  「`--rerun` 只挂紧邻其前那一个 task」这个方法论坑，见
  `docs/eval/c1-group-chat.md` 的「构建与验证证据」，本文不重复。
  ⚠️ **这是 2026-10-05 复核窗口的实测值，按当时窗口保留、不覆写。** 后续两个窗口的
  当前值分别是 **87 / 683** → **91 / 710** → **92 / 722**（详见 `c1-group-chat.md`
  「测试结果」段逐批的 ⚠️ 段）。**本轮实测的当前值是 `92 类 / 722 例 / 0 失败 0 错误
  0 跳过`**（`./gradlew --offline :app:testDebugUnitTest --rerun` 退出码 **0**），
  `./gradlew --offline assembleDebug` **0**，`./gradlew --offline lint` **0**
  （`0 errors, 587 warnings, 6 hints`，**与基线逐位相同**；本轮碰过的
  `ConversationDAO.kt` / `ConversationRepository.kt` / `ChatDrawerViewModel.kt` /
  `ConversationSearchLikePattern.kt` **四个文件全部 0 命中**）。
  ⚠️⚠️⚠️ **再往后一批（`af104850..6ba95422` 那 6 个 commit：真实公网网关证据 +
  P0/P1/P2 三批补测 + 端点群聊门禁 + 平票脚手架回收）之后，上面那两个数都过期了。
  当前的实测值是**（本轮纯文档任务，按要求没跑 gradle，这是逐个 XML 报告的求和实测）**：
  - **`:app:testDebugUnitTest` = 97 个测试类 / 754 用例 / 0 failures / 0 errors /
    0 skipped**（`glob` 命中 **97 个** `TEST-*.xml`；相对上一窗口的 92 / 723 是
    **`+5 类 / +31 例`**，两个老类同时更新：`GroupTurnCoordinatorTest` 63 → 72、
    `GroupChatTest` 18 → 22）。
  - **lint 磁盘实测 = app `584 warnings + 6 hints` = 590 条、0 error；15 模块聚合
    `617 warnings + 7 hints` = 624 条、0 error。** 上面那句「587 warnings / 6 hints」
    与「与基线逐位相同」**按当时窗口保留、不覆写**——**差值 -3 全部落在 `app` 单模块**，
    `app` 之外 14 个模块逐个复算仍是 Warning 33 / Hint 1（584+33 = 617、6+1 = 7）。
    ⚠️ 这一轮**没有跑 `./gradlew lint`**，数的是磁盘上现有报告文件，**报告是哪一次
    lint 生成的无法从 XML 里读出**。
  - **合计那一行（15 模块 / 180 类 / 1291 tests）同样没重跑全仓 `test`、不更新**——
    `app` 之外的 14 个模块本轮一个测试都没重跑。
  逐类增量、逐模块 lint 明细与复算命令见 `c1-group-chat.md` 的「测试结果」与「lint」
  两段。**C1 相关测试类台账**同步从 **30 类 / 335 例 → 35 类 / 366 例**（口径没变，
  详见该小节的复算命令）。
- **验收证据仍然一项都没有。** 契约 `docs/beyond-operit-client-changes.md:206`
  要求每例保存「各 viewer 可见消息集合 / 实际模型调用序列 / token 计数 /
  导出哈希」，`:232-235` 规定缺任一项就标 `unverified`。磁盘上这四类产物
  **零份**，`docs/eval/c1-group-chat.md` 十例仍全 `unverified`。本机
  `adb devices` 为空，一行 Compose 都没在屏幕上跑过。
  唯一的例外是迁移侧多了一条**主机侧部分**证据——C1-D 的 30→31 重放 85 条断言全过、
  C1-P 的 31→32 重放 49 条断言全过（都见下 A 节末两条），它们**不覆盖上述四类产物中
  的任何一类**，因此不改变「0/10」这个结论，也**不把任何用例改成通过**。
  ⚠️ **本轮又多了第三条主机侧部分证据**：C1-S 的抽屉 type 筛选重放
  **438 条断言全过**（`python3 tools/verification/c1s_conversation_type_filter_replay.py`
  两次退出码 0、输出逐字节相同）。它给「**类型筛选只过滤**」钉了真实的 SQLite 执行
  证据（**集合相等而不是数量相等**、筛选后 count 正确、分页无重复无遗漏、切换 `type`
  参数不改查询种类）——**这是 C1-10 迄今最硬的一块证据**。
⚠️ **但它同样不覆盖四类产物中的任何一类**：viewer 可见消息 ID 零份（脚本比的是
    `conversationentity` 的 `id` 集合，`message_node` 一行没碰）、模型调用序列零份、
    token 零份、真机行为零份。**所以「0/10」不变，C1-10 那一行仍 `unverified`**——
    证据登记表填的是「部分证据（零设备，真实 SQLite 执行）」并逐项写清了哪些补上了、
    哪些仍是零份，**状态列没动**。

- ⚠️⚠️ **2026-10-05 第二个窗口：真机接上了，四类产物拿到两类，但 0/10 不变。**
  设备 **OnePlus `PKG110` / Android 16 / API level 36 / `arm64-v8a`**，被测 app
  `heizige.kk.khatkit.debug`（`2.5.5` / `190`），无线调试 `192.168.31.183:38493`。
  这是「设备·Android 版本」列**第一次有值**，也是 C1 相关仪器测试**第一次真跑**。
  - **`adb devices` 不再为空**——上面所有「零设备 / `adb devices` 空 / 需设备」的措辞
    **按惯例保留不覆写**，新数据一律登记在
    `docs/eval/c1-group-chat.md` 的「C1 真机证据采集（2026-10-05，OnePlus PKG110）」，
    本节不重复数字。
  - **拿到两类**：① 各 viewer 可见消息 ID——**真机真 Room 库**（生产同一个
    `AppDatabaseFactory.create(...)`，同一 schema v32，只换独立库名）上跑真实
    `GroupTurnCoordinator.viewerMessages`，3 个 viewer 逐条落定，越权审计 6 个有序对
    `violations: []`；② 导出 SHA-256——**真机**跑 `TavernChatCodec.exportGroupJsonl` →
    真机写文件 → 真机 `MessageDigest`，3 种 `mode` 各一份，`adb pull` 后与本机
    `sha256sum` 独立复算 **6/6 全一致**（字节数也全等）。
  - **仍然缺两类**：**真实 prompt+completion token**（只拿到预算口径
    `spent=4096 / limit=400 / skipped=[b,c] / status=BUDGET_STOPPED`，**但 3000/1096
    是构造输入**）、**真实模型调用序列**（只拿到路由决策，三个角色分别命中
    `resolveGroupTurnModelId` 的三条不同回落通路，**没有任何一次真实 HTTP 调用**）。
    **根因是设备上没有配 API key**——`files/datastore/settings.preferences_pb`
    只有 165 字节，`strings` 里没有 `providers`、没有 `models`。
  - **仪器测试 25/25 全绿**（`GroupRunDAOTest` 12 + `Migration_30_31_Test` 7 +
    `Migration_31_32_Test` 6），另有一轮合跑 **45/45 全绿、exit 0**。
    ⚠️ 全量 56 条因 Coil 单例 `ImageLoader` 初始化顺序冲突（`BrowserRuntimeTest`）
    **跑不完，未修**。
  - ⚠️ **同时修掉了三处测试代码缺陷**（定性为**测试自身缺陷，不是生产代码 bug**）：
    `GroupRunDAOTest` 两处（fixture 状态写死 + 期望列表排序方向反了，**断言强度未变**）、
    `Migration_31_32_Test.insertConversation`（`query()` 不执行写操作，导致一条
    **假断言**——它曾是「31→32 能往返承载 `group_cards`」的唯一证据位）、
    `ExampleInstrumentedTest` 硬编码包名。这五个 commit
    （`ca717f39` / `7519ee7f` / `8151de89` / `43d6ea7e` / `2c1d7632`）
    **零 main 源码改动**，`git diff --name-only 4a4def40..2c1d7632 -- app/src/main
    | wc -l` = **0**。
  - ⚠️ **本轮在真机上还撞到一个 pre-existing 真 bug 并修掉了**：`Screen.Assistant`
    缺 `@Serializable`（`Screen` 57 个成员里唯一漏的），导致**每次进助手页必崩**
    （真机崩 3 次）。`d122d6882`（9-12）引入时漏了，`e7c0b2a3`（10-03）新增
    `persist()` 把「静默不恢复」升级成「必崩」。漏过 CI 的原因是
    `NavBackStackSerializationTest` 只点名测了 **5 个成员**，`Screen.Assistant`
    从未被测过。修法是 `0a289af6` 补注解 + `c3909047` 加
    `everyScreenMemberHasAWorkingSerializer`（**遍历全部 `sealedSubclasses`**，
    「少一个成员就红」，不再是点名测）。
    另有 `18baa930` / `1f560f88` / `1e9d5607` 删掉圆形揭幕动画试验页
    （`SettingAnimPlayPage.kt` 273 行 + 设置项 + 导航 + 三个字符串 key，
    净 5 files / 293 deletions / 0 insertions）——理由是动画已应用到正式代码。
  - **结论不变：按 `client-changes.md:232-235`「缺任一项就标 `unverified`」，
    十例仍全部 `unverified`，C1 不能标成已完成。** 四类里两类齐了 ≠ 契约达成；
    `viewer 可见消息 ID` 那一列还**只覆盖 pipeline**，roundtable / vote 没采。
- ⚠️⚠️⚠️ **2026-10-05 第三个窗口（本文件最新的实测窗口）：上面那个窗口的两条限制都被
  解掉了——四类产物全部有真机内容，但 0/10 仍然不变。**
  - **「设备上没有 API key → 第三、四类在原理上采不到」这条已被推翻。** 解法不需要
    key：往生产单例 `SettingsRepository` 里装一个 base URL 指向
    `http://127.0.0.1:8765/v1` 的**自定义 OpenAI 兼容 provider**，设备侧
    `adb reverse tcp:8765 tcp:8765` 打到开发机上的 mock 服务，**没改生产代码、没加
    测试专用后门**，走 `ChatManager.sendMessage → GenerationLoop → ProviderManager →
    OpenAIProvider → ChatCompletionsAPI.streamText → Ktor CIO OkHttpClient`
    **完整生产链路**（`User-Agent: ktor-client`、`accept: text/event-stream`、
    `stream_options.include_usage: true`、三个请求全 `stream=true`）。
  - **「viewer 集合只覆盖 pipeline」也已被推翻**：夹具按**三种 mode 各采一遍**
    （`a9d2077e`，改的是夹具循环不是生产逻辑），越权审计三 mode 全部
    `passed=true` / `checked_pairs=6` / `violations=[]`，**议长 `chairRound=true`
    的放行分支第一次被触发**。
  - **四类产物现状**：viewer 可见消息 ID（三 mode 台账 + pipeline 真实 HTTP 台账）、
    实际模型调用序列（`mock-model-a → mock-model-b → mock-model-c`，真请求）、
    prompt+completion token（main **773** / budget **236**，与落库
    `group_runs.spent_tokens` **逐条相等**）、导出 SHA-256（3 mode 真机文件三重一致）。
  - **仪器测试**：`connectedDebugAndroidTest` 类过滤 **28/28 全绿、exit 0**（3+12+7+6）；
    `C1LiveModelSequenceTest` 走**手动 `am instrument`**，不挂住、**3.6s 完成**。
    ⚠️ `connectedAndroidTest` **跑完会卸载 app 并删掉外置目录里的证据文件**——采证据
    必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull`。
    ⚠️ 无线调试的 **mDNS 会把同一台设备自动注册两条**，AGP 对两个 serial 各跑一遍
    安装/卸载/启动互相踩（`DELETE_FAILED_INTERNAL_ERROR` + `Unable to find
    instrumentation target package` + `Error: -99` + **0 用例 exit 1**）；
    **跑之前必须 `adb devices -l` 确认只有一条**。⚠️ **端口每次开无线调试都会变**，
    必须用 `adb mdns services` 找当前端口。这**不是 OEM 兼容性问题**，是同一台机器被
    登记两次。
  - ⚠️ **结论仍然是 0/10 不变。** 上面那个窗口的两条限制虽然解了，但**另外五条仍然缺**：
    **roundtable / vote 的真实 LLM 调用记录**（只有夹具层台账，真实 HTTP 零份）、
    **酒馆（SillyTavern）本体打开群聊导出文件**（零证据）、**真机 UI 端到端**
    （一行 Compose 没上过屏）、**相机扫码真机链路**（CameraX+MLKit 没在设备上跑过）、
    **`BrowserRuntimeTest` 的 Coil 单例崩溃**（`RouteActivity.kt:202`，来自 `b5c5ebcb`，
    与 C1 无关，**未修**，全量 56 条仪器测试跑不完；已用类过滤覆盖 28 条）。
    外加两条诚实边界：**mock 不是真实模型**（是「真实 provider 代码路径 + 真实 HTTP +
    真实 SSE + 真实 usage 报文」，**不是「真实 LLM 推理」**）；**显式 @ 的投递收窄
    真机零份**（夹具的 `@` 落在用户消息上，用户消息对每个 viewer 都可见）。
  - ⚠️ **这一轮撞出并修掉了锚点之后第一个由真机证据定位的 main 源码 bug**
    （`858c11d0`，`core/data/ai/GenerationLoop.kt`）：后续角色的产出被**并进上一位
    已提交的发言**，该角色被误判「本轮没有产出内容」写成 `role_failed`。
  - ⚠️ **同时登记三处测试代码自身缺陷**（**不是生产代码 bug**）：`bff7b0c6` 视角隔离
    断言口径漏了「自己」（`seenByA.isEmpty()` → `seenByA.all { it == "a" }`，
    **改后更严**，mock 侧独立记录的 seq=1 请求是这条修正的旁证）、
    `a9d2077e` 读回 JSON 的三处类型断言（`JsonLiteral` / `JsonNull` 不能直接和
    `Boolean` / `null` 比）、`47693445` 的 `openGroupSession()` 漏装载（**整轮退化成
    单聊**，是「证据差点采成别的形状」的根因）。逐条见
    `docs/eval/c1-group-chat.md` 的「C1 真机证据采集第二轮」。
  - **诚实结论一句话：四类产物已全部有真机内容，但十例的验证矩阵仍不完整，
    0/10 不变。** 「每一列都填上了东西」和「这一行该判通过」是两件事。
- ⚠️⚠️⚠️⚠️ **2026-10-05 第四个窗口（本文件最新的实测窗口，HEAD `6ba95422`）：
  「真实 token 与真实调用序列」第一次不是 mock——用的是项目自带的免费公网网关，
  设备直连公网；0/10 仍然不变。**
  - **怎么不用 mock 也拿到真实 usage**：生产 `DEFAULT_PROVIDERS` 里本来就内置了一个
    OpenAI 兼容网关（`core/data/datastore/DefaultProviders.kt:281-318`，
    `name = "极客猫"`、`baseUrl = https://api.zenneko.top/v1`、`enabled = true`、
    `builtIn = true`，**apiKey 已预填在源码 `:285`，本文只记行号不落明文**），
    模型表带 `deepseek-v4-flash`（uuid `5a86b2d6…`）与 `glm-5.2`（uuid `8b6bf21c…`）。
    **没改生产代码、没加测试专用后门**，设备**不经 `adb reverse`、不经本机 mock**
    （上一轮那条路是 mock + `adb reverse`，这一轮两层都去掉了）。
    ⚠️ 顺带纠正一处认知：内置 provider **没开** `useResponseApi`，实际走
    `/chat/completions`，usage 取自 SSE 的 `stream_options.include_usage` 收尾块。
  - **真实 usage（provider 返回的数字，不是任何估算）**：a `deepseek-v4-flash`
    prompt **6803** / completion **159**；b `glm-5.2` **6667 / 68**；
    c `deepseek-v4-flash` **6880 / 102**。
    **Σ(prompt+completion) = 6962+6735+6982 = 20679**，落库
    `group_runs.spent_tokens = 20679`，**精确相等**；`status=COMPLETED` /
    `committed=[a,b,c]` / `skipped=[]` / `ended_at` 非空。
    **实际模型调用序列 = `deepseek-v4-flash → glm-5.2 → deepseek-v4-flash`**，
    与按角色绑定**逐位一致** → `resolveGroupTurnModelId` + `TaskRoutes.resolve` 的
    按角色选型**在真实调用下成立**。
  - ⚠️ **prompt 6800 量级的来源已定位**：请求体 **25.7KB 里 24675 字节是 23 个工具的
    定义**（按同一 wire body 本机复现得 6800/6526/6814，与真机同量级）。
    ⚠️ **两个模型都是推理模型**：`max_tokens` 给小了会 `finish_reason=length`、
    `content` 为空、token 全被 `reasoning_tokens` 吃掉，**给足配额才吐正文**。
  - ⚠️⚠️⚠️ **但 0/10 仍然不变**，三条硬理由：
    **① 这个用例没有一次全绿记录**——那一跑成功产出了上面全部数字，随后被设备 OEM
    回收策略**杀掉进程**，`connectedAndroidTest` 又按预期**卸载 app、清空外置目录**，
    **JSON 落盘文件没拉回来**，数字来自同一次成功生成的**内存快照**；
    **② `actual_model_call_sequence` 里的模型名不是 wire 级抓包**——它由
    `message.modelId` 的 uuid 经 provider 模型表**反查**（**app 不保存响应的 `model`
    字段**），JSON 里已用 `wire_model_name_provenance` 字段显式标明；
    ✅ **订正（2026-10-06，HEAD `74d82476`）：这一条从「缺能力」变成「已具备 + 待真机复核」**
    ——「app 不保存响应的 `model` 字段」**已不再成立**，成因是 `StreamChunkHandler`
    收到 `StreamChunk.Finish` 时把 `chunk.model` **丢弃**了（**不是解码器没给**；
    `ChatCompletionsStreamDecoder.finish(reason, responseId, model)` 一直正确发出）。
    已修：wire 名现落 `UIMessage.wireModelName`（`@Serializable`，随对话 JSON 落库，
    **无需 Room 迁移**）。**零设备两条证据**：JVM `WireModelNameProvenanceTest`
    **9 例 / 27 处断言 / 退出码 0**（逐字透传 + 反查隔离）、真实网关探测两个 wire 名
    **均 HTTP 200**。⚠️ **但硬理由本身仍未消**：**仪器测试一行没跑**（设备离线，
    新增断言 / 新 JSON 字段 / wire 优先判定**全部只有编译验证**），
    **设备上那份证据文件记的仍是 UUID 反查值**——**这里没有任何真机数字**。
    ⚠️⚠️ **诚实要点**：wire 名与请求名一致 ⇒ **原先反查出的名字恰好是对的**，
    **这不是「反查错了被纠正」，是「取得途径从反查变成 wire 直取」**，
    **不构成对既有证据的追溯性升级**。
    📍 **来源指针**：`docs/eval/c1-group-chat.md`「已知遗留与风险」**第 18 / 18b 条**
    （另见该文「C1 真机证据采集第四轮」、「为什么十例仍然 0/10」第 2 条、
    「wire 级模型名的两条零设备证据」整节，以及「判定规则」第四/六/八条）；
    ✅ **它能证明的与不能证明的**（`d00880fc` 已把这句写进
    `androidTest` 侧 `C1LiveModelSequenceTest` 的 KDoc）：能证明「**按角色选型结果这一层**」，
    **不能**证明「网关实际接受并按此执行」。
    **③ 真机 UI 端到端仍零份**（一行 Compose 没上过屏）。
    按 `client-changes.md:232-235` 与本文的判定规则，**C1-03 / C1-04 / C1-09 /
    C1-10 直接踩「真机或自动化证据」那条**；另外六行这一轮跑的是**无 @ 的 pipeline**，
    各自点名的那条路径（显式 @ 收窄 / 取消 / 超时 / 预算截断 / 失败续跑 / 记忆空间）
    **仍然没被真实调用过**。**十行状态列一个格都没动。**
  - ⚠️ **一个只在真网关上现形的坑（mock 永远不会暴露）**：首版 persona 没写
    「历史里别人的代号不是你的」，而 pipeline 本来就要把上一位发言放进下一位上下文，
    **真模型照抄眼前那条的格式**——b 学走 a 的 `ROLECODE:A`、c 学走 b 的、
    c 干脆零产出（`role_failed`）。已加第 3 条禁令，并按**真实 pipeline 链**
    （a 的输出进 b、b 的进 c）本机跑 **2 轮 × 3 角色 = 6/6 通过**。
    ⚠️ **但修完之后的真机全绿没跑到**（OEM 杀进程）。
    ✅ **订正（2026-10-06，HEAD `d96d64e1`）**：**已跑到，且真机全绿**——
    `realProviderRoundRecordsGenuineTokenUsage` `exit 0` / `OK (1 test)`，
    a/b/c 三条正文各自只含自己的 `ROLECODE`（`A` / `B` / `C`，无串码），
    落盘 JSON 拉回并记了 SHA-256。⚠️ **十行状态列仍一个格都没动**（理由②③ 与
    四行路径缺口未被这一轮覆盖）。详见 `docs/eval/c1-group-chat.md`
    「C1 真机证据采集第四轮」。
  - ⚠️⚠️ **新踩的设备侧限制：OnePlus OEM 回收策略会在 app 进程存活约 34-44 秒时杀
    进程**（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占
    313MB；设备当时被用户自己的应用占满——游戏 1.7GB、抖音 1.4GB，
    `MemAvailable` 3.4GB，**不是真 OOM**）。⚠️ **既有 mock 用例同样被杀**，
    所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报
    `Process crashed`、**0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。
    按「OOM 立即停止重试」共试 **11 次**后停止。**这是设备环境的限制，不是代码缺陷。**
  - ⚠️ **顺带补全上一轮那条 mDNS 坑的「具体怎么断」**：
    `adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；
    要断的是 mDNS serial 本身 ——
    `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），
    因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。
    ⚠️ **它会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。
  - ⚠️ **本批还落地了四批代码层修复**（P0 保留值拒绝 / P1 覆盖盲区 ×2 /
    端点群聊门禁 / `failGroupTurn` 回收平票脚手架），逐条见下一段。
    **它们产出的是代码层覆盖与修复，不是契约 `:206` 点名的验收证据**，
    所以**十例状态一个都没变**。
  - ⚠️⚠️ **再往后一批（2026-10-06，`e72793e6..8ccc0264`，HEAD `361c7cf6`）：
    平票脚手架两处漏回收 + 残留旧 job 两道守卫**，逐条见「第五批」那一段。
    ⚠️ **那一批修的是三个当时真的还在漏的缺陷**（不是补覆盖），
    **但同样不改任何判定**——零设备，四类产物一份未增。
  - ⚠️⚠️ **再往后一批（`4336831f`，2026-10-06）：真实公网网关那条用例首次真机全绿**——
    `realProviderRoundRecordsGenuineTokenUsage` `am instrument` **退出码 0** /
    `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、
    b `6645/58`、c `6904/242`，**Σ(prompt+completion) = 20780 与落库
    `group_runs.spent_tokens` 精确相等**；三份产物 `adb pull` 回主机并记 SHA-256。
    ⚠️ **十行状态列仍一个格都没动**（模型名非 wire 抓包 / 真机 UI 端到端零份 /
    ~~其余 4 条 mock 用例仍未真机全绿~~ ✅ **该子项已被第十二批消掉**：那 4 条现已真机全绿），
    逐条见下面「第六批」与「第十二批」。
  - ⚠️⚠️⚠️ **本轮（`33eb801e..a74c1820`，2026-10-06）：两个真机 Compose 必崩已修并真机
    验证 + Coil 单例崩溃结构性修复 + 四项代码债收口。十例仍全部 `unverified`。**
    逐条见下面新增的「第六批」那一节。**操作清单在
    `docs/eval/c1-group-chat.md` 的「下一位怎么把剩下的做完（第六个窗口的操作清单）」**
    ——按「需要设备 / 需要外部环境 / 零设备可做」三组分组，每项都带具体命令或步骤。

### ⚠️⚠️ 第八批（`d00880fc` / `3d04c82c` / `5e4582ed` / `5435e403` / `4ee1ad5c` / `8111d178` / `72a5e548`，2026-10-06）：**一条真的群聊泄漏被修掉 + 一条错误的遗留被证伪**

⚠️ **本节讲的是 7 个 commit**；另外 4 个（`de05a908` 修证据登记表裸竖线 /
`5be31807` 新增复算脚本 / `e88d15fd` + `69d88214` 那两次文档登记）的
**完整 11 条清单**在 `docs/eval/c1-group-chat.md` 台账的
「⚠️⚠️ 补登：上一批之后到 HEAD `72a5e548` 的 11 个 commit」小节
（核验命令：`git log --oneline db4cdd77..72a5e548 | wc -l` = **11**）。
⚠️ **已登记过的不重复登记**——`a74c1820..72a5e548` 实测 17 个，多出的 6 个
（`2d1f08db` / `7f3d80e5` / `8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）
在本文「第六批 / 第七批」与那篇台账里已经逐条列过。

⚠️ **这一批同时做了两件相反的事**：**修掉一条真泄漏**，**并把一条**不存在**的遗留
证伪**。两件都必须写进对外材料，否则会出现「修了个寂寞」与「漏了个真的」两种误读。

#### ① 修掉的真泄漏：标题 / 摘要绕过 viewer 过滤（`4ee1ad5c`）

⚠️ **契约 `docs/beyond-operit-client-changes.md:200` 明写**：「上下文过滤必须发生在
**prompt 组装层**……工具调用、检索与记忆注入均使用同一 viewer 过滤结果，
**禁止在 UI 层『隐藏但仍发送』**」。而 `feature/chat/ChatManager.kt` 的
**`generateTitle`**（送 `currentMessages.takeLast(4)`）与 **`compressConversation`**
（送**全量**）**直接送未过滤的 `conversation.currentMessages` 给 TITLE / SUMMARY 模型**。

⚠️⚠️ **触发面比早前判断的大得多：不只长按菜单**。`ChatManager.kt` 在
`handleMessageComplete` 的 `onSuccess` **末尾**（**`:1070`**）对**单聊与群聊走同一行**
自动调 `generateTitle` ⇒ **群里每跑完一轮，其他角色最近 4 条就自动进 TITLE 模型，
用户一个菜单都不用点**。长按菜单那条同样成立：`ConversationList.kt` 的菜单项
（`:617-624`）**不判会话 type**，而 `ChatDrawer.kt:548` 的 `TYPE_GROUP` 筛选项保证
群会话会进列表。⚠️ `compressConversation` 确认**潜伏**——压缩对话框只在 `ChatPage.kt`
（`compress` 命中 6 次），`GroupChatPage.kt` 里命中 **0** 次。

| commit | 改了什么 | 结论 / 边界 |
|---|---|---|
| `4ee1ad5c` | 新增纯函数 `feature/chat/SummaryViewerScope.kt`（91 行）+ `ChatManager.kt` +44 | ✅ **转调已有的 `GroupTurnCoordinator.viewerMessages`，没有新写一套过滤**。口径：`roundtable` → 议长（`chairRound = true`）；`pipeline`/`vote`/未知 → `roles` 第一个。**fail-closed 分两档**：`roles` 为空 → **空列表**（不是全量）；议长 id **悬空但名单完好** → 退回第一位且 `chairRound = false`（否则 `chairRound` 分支会**真越权**）。⚠️ `compressConversation` **只过滤送给模型的那份副本**（`compressScope`），落库的 `allMessages`/`messagesToKeep`/`newMessageNodes` **完全没动**（符合「不落第二套消息库」硬约束），`compressScope` 为空时抛 `IllegalStateException`。**单聊逐字不变**（第一行 `if (!isGroupConversation) return currentMessages`，**返回同一 List 实例**） |
| `8111d178` | 修 `ChatService.kt` 的 fail-loud 注释（+17 / −3，**零实现改动**） | 这就是下面 ② 那条错误遗留的**源头修正** |
| `72a5e548` | `SummaryViewerScopeTest` **12 条真单测** + `SummaryViewerScopeWiringSourceGuardTest` **5 条源码护栏** | 含 fail-closed、议长悬空、幂等、**反向越权**；⚠️ 有一条护栏专门**断言 KDoc 里那句「⚠️ 待产品确认」不许被删**。**变异 4 次全红**（换回裸 `currentMessages` / viewer 选错 `roundtable`→`vote` / fail-closed 退化成 pass-through / `splitMessages(compressScope)` 改回） |

⚠️⚠️ **这条必须作为遗留登记：「标题/摘要按谁的视角取」是判断，不是实证。**
契约只规定「**必须过滤**」，**没规定「按谁过滤」**——标题/摘要**没有轮次**。
当前口径已列在 `SummaryViewerScope` 的 KDoc 里并标了「⚠️ 待产品确认」+ 替代口径，
**改动只许动 `SummaryViewerStep`**。完整登记见 `docs/eval/c1-group-chat.md`
「已知遗留与风险」**第 33 条**。

⚠️ **`d00880fc` 是这一串里唯一动了生产代码的**：Edit 卡片此前确实没有
`if (!groupChat)` 门禁（另外三个动作都有），群聊长按角色发言会「点了没反应」；
**但照抄那个门禁会连带关掉群聊里合法的「改用户提问」**，所以改成**按消息收口**
`!groupChat || <消息>.role == MessageRole.USER`（展示层 + 触发层各一处，单聊逐字不变），
配 `GroupEditActionVisibilitySourceGuardTest`。同一 commit 还**纯注释**订正了
`TavernChatCodec` 的 `config == null` 成因（两种 → 四种）。
⚠️ **`3d04c82c` / `5e4582ed` / `5435e403` 三条是「已有测试恒绿」的修复**（各改 1 个
既有文件、**净增 0 条 `@Test`**）：切分器在锚点缺失时退化成「扫全文」而断言仍绿，
修法是补 `assertTrue(end > start)` 前置检查 + 反空跑断言。**改的是测试不是生产代码。**

#### ② 证伪的错误遗留：「`ChatService.finishInterruptedPendingTools` 里三处 `generateText`」

⚠️⚠️⚠️ **这一条整条是错的，它指认的群聊泄漏根本不存在**。原文逐字保留在
`docs/eval/c1-group-chat.md`「已知遗留与风险」**第 28 条**（**不删——它是错误如何发生的
证据**）。四条证伪依据（逐条实读核实，行号按 `72a5e548`）：

1. **`stopGeneration` 到不了 `ChatService`。** 命名陷阱在
   `feature/chat/ChatViewModel.kt:63`——属性名叫 `chatService`，**类型是 `ChatManager`**。
   另两个停止入口也都是 `ChatManager`：HTTP `core/network/routes/ConversationRoutes.kt:367`
   （形参在 `:48` 就声明成 `ChatManager`）、FGS 超时走
   `AndroidManifest.xml:148-151` 注册的 `feature/chat/ChatGenerationForegroundService.kt:75`。
2. **`finishInterruptedPendingTools`（`core/service/ChatService.kt:912-931`）里零
   `generateText`**，只给最后一条消息里未执行的 tool part 塞一段
   `{"status":"cancelled",…}` 然后 `saveConversation`。全文件 `generateText` 只有 4 处：
   `:744` 与 `:952` / `:1004` / `:1089`（后者三处分别在 `generateTitle` `:933` /
   `generateSuggestion` `:985` / `compressConversation` `:1038` 的函数体里）。
3. **错误源头是仓库注释**：`ChatService.kt:653` 那句「另三处
   `providerHandler.generateText` 属于 `finishInterruptedPendingTools`」本身就写错了，
   **由 `8111d178` 改掉**。⚠️ **错误从注释扩散进了验收文档**——教训是别拿注释当实现读。
4. **`ChatService` 只有 1 个注入点且不可达**：`feature/history/HistoryVM.kt:28`（只用
   `toggleConversationPinned`），而 `HistoryPage` 零路由引用。⚠️ 附带发现**第 4 份重复类**
   `core/service/ChatGenerationForegroundService.kt`（175 行）**没进 manifest**，是死副本。

#### ⚠️⚠️ 订正（2026-10-06，本轮，纯文档）：上面第 4 条的「不可达」**措辞要精确化**

第 4 条的**结论正确**（不可达），但「1 个注入点 + 零路由引用」这个描述容易让人以为
「那它是死代码，直接 `rm` 即可」。⚠️ **不是。** 精确判定是**活接线 / 运行时不可达**：

| 事实 | 来源 |
|---|---|
| `@Inject constructor` ⇒ Hilt JIT 绑定**确实存在** | `core/service/ChatService.kt:177` |
| 有一个真实的注入点，且真被调用 | `HistoryVM.kt:28` 注入、`:55` 调 `toggleConversationPinned` |
| 零**构造**点 | `HistoryPage.kt:69` 全仓零路由引用 |
| ⚠️ **`AppHiltModule.kt:51` 那行不是绑定，是未使用的 import** | 该文件里 `ChatService` 只出现在 `:51` 一行（`:36` import 的是 `feature.chat.ChatManager`）；`grep -rn "ChatService" --include=*.kt . \| grep -E "@Provides\|@Binds"` 命中的 2 处都是**注释**与**断言字符串字面量**，真绑定 0 处；`@Provides` 方法是 `:191 fun provideChatManager`、返回 `ChatManager`（`:211`） |

⇒ **⚠️ 所以「删 `ChatService.kt`」会炸编译，不是纯清理。** 删之前**必须**先拆三处
依赖（`forkConversationTitle` 被活的 `ChatManagerTest.kt:18` 跨包 import + 5 条断言；
两个护栏测试 `File(...).readText()` 硬编码了它的绝对路径；`HistoryVM`/`HistoryPage` 孤儿）。
完整拆解顺序见 **`docs/architecture-map.md` §9.2.1**。

⚠️⚠️⚠️ **本轮查实的根因（之前记的是「重复代码待清理」，实际是「删过又被复活」）**：

```
2026-09-25  f7463f814  refactor(structure): move chat pages and conversation services to feature/chat
                        ← 项目主动把 5 个文件从 core/service/ 搬到 feature/chat/
2026-10-01  8cf9bec2d  merge: 同步上游 RikkaHub master(2.5.5 + 29 个提交)
                        ← 上游那侧路径没变，5 个文件被当成新文件整包搬回来
```

实测 `git diff --stat 8cf9bec2d^1 8cf9bec2d -- app/src/main/java/heizige/kk/khatkit/app/core/service/`
⇒ **`5 files changed, 1994 insertions(+)`，纯新增 0 删除**；复活后
`git log --oneline 8cf9bec2d..HEAD -- core/service/` 恰好 **7** 个 commit 且全中
`ChatService.kt`，另外 4 个一动没动。

⇒ **这不是「有人忘了删」，是「已经决定删过、被 vendor merge 复活」。**
⚠️ **不写下来，下一个人会再删一次、再被复活一次。** 根因时间线、5 个文件逐个判定、
以及「每次上游同步后必查」的对策见 **`docs/architecture-map.md` §9**。

⚠️⚠️⚠️ **顺带订正「重复约 86.7%」这个旧数字 —— 口径必须写清，否则不可复现**：
实测 `ChatService.kt`（1506 行）× `ChatManager.kt`（2362 行）：

| 口径 | A 覆盖率 | Jaccard |
|---|---:|---:|
| RAW（全部行） | **86.06%** | **67.06%** |
| CODE（剥空行 + `//` + `*` + `/*`） | **88.97%** | **72.30%** |

**86.7% ≈ RAW 口径的 A 覆盖率 86.06%**，口径是「`ChatService` 的行里有多少行在
`ChatManager` 里逐字不变」（`diff` 的 `<` 侧取反），**不是 Jaccard**。
⚠️ 用 Jaccard 只有 67.06% —— 差近 19 个百分点，因为 `ChatManager` 有 **412** 行注释
vs `ChatService` **142** 行，且 `ChatManager` 在共同代码**中间插了 1066 行**，
两文件行号完全错位 ⇒ **单看 RAW 会被「块插入」骗**。
⚠️ **代码级差异只有 132 行**在 `ChatService` 一侧（CODE 口径），**不是 RAW 口径的 210 行**。
可复现命令见 `docs/architecture-map.md` §10.2。

⚠️⚠️⚠️ **本轮最实质的发现 —— 「删 `ChatService.kt`」不是纯清理**：
它会**永久删掉一个聊天建议 / 追问推荐功能的全部实现**。详见下一条待决策项
**D1**（**需要主人拍板，agent 不得自行决定**）。

⚠️ **所以「C5 那个产品决策」也不存在**（没有泄漏就没有决策要定）。⚠️ **真正该做的是删掉
`ChatService` 这份重复实现 + 那份死 FGS**——纯清理、零行为改动、零设备可做，
**不在本轮授权内**（本轮不碰 Kotlin 源码），已作为遗留尾巴挂在第 28 条下面。

⚠️⚠️ **本批同样不改变任何判定**：**零设备、零 `adb`**，契约 `:206` 点名的四类产物
**一份未增**；`4ee1ad5c` 修掉的是**真实存在**的缺陷，但按判定规则第五条
**「修掉了真缺陷」不等于「这一行该判通过」**，且泄漏本身**零真机观测**。
**十行状态列一个格都没动，仍是 10/10 `unverified`。**

#### ⚠️⚠️⚠️ D1【待决策 · 2026-10-06 本轮新增】：`generateSuggestion`（聊天建议 / 追问推荐）删还是留

> 🚩🚩🚩 **这条需要主人拍板，agent 不得自行决定。**
> 它是**产品决策**（这个功能要不要存在），不是清理决策。
> ⚠️ 在拍板之前，**不要删 `core/service/ChatService.kt`**。

**为什么它挡在删除前面**：早前把这事记成「`ChatService` 是第二份重复实现，删掉是纯清理、
零行为改动」。⚠️ **本轮查实后这个前提不成立** —— 它不是纯清理。

**实测证据（四条，每条都有命令）**：

| # | 事实 | 实测 |
|---|---|---|
| 1 | ⚠️ **全 app 唯一填充非空 `chatSuggestions` 的地方** | `grep -rn "chatSuggestions" --include=*.kt app/src/main/` ⇒ **`core/service/ChatService.kt:1026` 的 `chatSuggestions = suggestions.take(10)` 是唯一的非空写入** |
| 2 | ⚠️ **活管线 `ChatManager` 只会清空、从不填充** | `ChatService.kt:704/999/1114` 与 **`ChatManager.kt:758/1324` 全都只会 `= emptyList()`**。⇒ 活的那条管线**永远产不出建议** |
| 3 | ⚠️ **没有任何 UI 读 `chatSuggestions`** | 7 个命中文件全是数据层 / 网络层（`ConversationEntity` / `Conversation` / `ConversationRepository` / `WebDto` / `ConversationDiff`）+ 两个服务类；按 UI 文件名（`Page.kt` / `VM.kt` / `Screen` / `Composable`）过滤 ⇒ **0 命中（EXIT=1）** |
| 4 | ⚠️ **没有 UI 开关入口** | `enableSuggestion` flag 存在（`SettingsRepository.kt:208/280/579`，默认 `true`），但 `grep -rn "enableSuggestion" --include=*.kt app/src/main/` ⇒ **只有 `ChatService.kt:991` 读它**；`grep -rn "setting_model_page_enable_suggestion" --include=*.kt .` ⇒ **空** |

⚠️ **第 4 条的一处订正（别照抄上一轮的转述）**：上一轮转述说「7 种语言里连这个字符串
都没有」。⚠️ **实测是反的** ——
`grep -rln "setting_model_page_enable_suggestion" app/src/main/res/` ⇒ **7 个文件全部命中**
（`values/` / `values-ar` / `values-ja` / `values-ko-rKR` / `values-ru` / `values-zh` /
`values-zh-rTW`），基线值在 `values/strings.xml:817`
= `Enable Chat Suggestions`。**Android Lint 也独立确认它是未使用资源**
（`app/build/reports/lint-results-debug.txt:1143`：
`The resource R.string.setting_model_page_enable_suggestion appears to be unused [UnusedResources]`）。

⇒ 订正后的结论更精确、且**更强**：**字符串资源在 7 种语言里都齐、但没有任何 `.kt`
引用它**，所以**没有 UI 开关绑定**（上一轮「字符串不存在」的说法错，但「没有开关入口」
的结论成立，且现在有 lint 的 `UnusedResources` 作独立旁证）。

**⇒ 所以「删 `ChatService.kt`」不是纯清理 —— 它会永久删掉一个（当前已不可见的）
聊天建议 / 追问推荐功能实现。**

**两个选项**：

| 选项 | 内容 | 代价 |
|---|---|---|
| **(a) 认定已废弃** | 连同 `enableSuggestion` flag（`SettingsRepository.kt:127/208/280/579`）、`suggestionPrompt`（`:213/285/580`）、以及 7 个 locale 里的 `setting_model_page_enable_suggestion` 字符串**一起清理** | 功能彻底消失（**但它现在就已不可见**） |
| **(b) 认定要复活** | 把 `generateSuggestion`（`ChatService.kt:985-1036`）port 进 `feature/chat/ChatManager.kt` **并补 UI**（渲染 `chatSuggestions` + 一个绑定到 `enableSuggestion` 的开关） | ⚠️ **要新增 UI、要真机验证**，且要决定 `ChatManager` 的哪条路径触发它 |

**倾向 (a) 的理由**（**仅供参考，不是结论**）：无 UI 消费方（证据 3）+ 无开关入口
（证据 4）⇒ **产品层面早已退役**，现在只是一份没人收拾的死副本；且 **git 历史完整**
（`8cf9bec2d` 就是它被搬回来的那次提交，2026-10-01），**随时可取回** ——
⚠️ **若上游日后把该功能带回，下次同步会自然恢复**（届时按
`docs/architecture-map.md` §9.3 的对策复查一次即可）。

**待主人拍板。** 拍板前不动 `core/service/` 任何一个文件（`MessageQueue.kt` /
`ConversationSessionManager.kt` / `ConversationSession.kt` / `ChatGenerationForegroundService.kt`
这 4 个**判定不受本条影响**，可以独立处置；`ChatService.kt` 必须等这条定了）。

#### ✅✅ D1【**已决策 · 2026-10-06**】：主人选 (a)，`core/service/` **已按 D1 清理完毕**

> 🚩 **主人原话：「不要那个消息建议了」** ⇒ **选项 (a)：认定「聊天建议（追问推荐）」功能
> 已废弃，连同它一起清理。**
> ⇒ 「🚩🚩🚩 待主人拍板，agent 不得自行决定」**已解除**，`core/service/` 处置授权同时下达。
> ⚠️ 上面那张两个选项的表、以及「待主人拍板」的原文**全部保留**（仅追加本块）。

**「已按 D1 清理完毕」—— 逐条落实**：

| 项 | 结果 |
|---|---|
| `core/service/` 5 个文件 | ✅ 全删（commit `86da59fa0`，**2083 行**），空目录一并删（`ARCHITECTURE.md` §1.1「删功能 = 删文件夹」） |
| 选项 (a) 点名的 `enableSuggestion` flag | ✅ 删 **4 处**：`ENABLE_SUGGESTION` key 声明 + getter/setter 对 + `Settings` 默认值（commit `0040463b1`） |
| 选项 (a) 点名的 7 个 locale 字符串 | ✅ 删 **7 种语言**：`values` / `values-ar` / `values-ja` / `values-ko-rKR` / `values-ru` / `values-zh` / `values-zh-rTW`，全部 `res/values*/strings.xml`（commit `0040463b1`）。⚠️ **删前用不带 `--include` 的全类型 grep 复核过**：`.kt` **0 引用**（唯一命中在 `docs/` 的散文里），排除了上一轮 `--include=*.kt` 造成的假阴性 |
| `generateSuggestion` 本体 | ✅ 随 `core/service/ChatService.kt` 删除（commit `86da59fa0`），无需单独动作 |
| ⚠️ **`chatSuggestions` 字段本身** | ✅ **保留**（`Conversation.chatSuggestions` 是持久化字段，删它改数据形状、影响老库反序列化）。⚠️ **它现在恒为空** —— 证据 1 那条唯一的非空写入随文件删除，活管线 `ChatManager.kt:770/1336` 只 `= emptyList()`。证据 3 已证无 UI 消费方 ⇒ **用户可见行为零变化** |
| ⚠️ **选项 (a) 里还点名的 `suggestionPrompt`** | ⚠️ **有意未删** —— 本轮授权只覆盖 `enableSuggestion` 这一个 flag 与那一个字符串，**不擅自扩大**。⇒ 现在 `suggestionPrompt` / `SUGGESTION_PROMPT`（`SettingsRepository.kt` 4 处）也是孤儿，**留给主人** |

**为什么能删干净（§9.2.1 三处依赖的落点，细节见 `docs/architecture-map.md` §9.4.2）**：
`forkConversationTitle` 搬到 `feature/chat/ChatManager.kt`（跨包 import 消除，**5 条断言
一字未改、仍绿**）；`HistoryVM.kt:28` 注入**改指 `ChatManager`**（`:1358` 有逐字同签名的
`toggleConversationPinned`），**`HistoryPage` 与 `HistoryVM` 都保留**；两个护栏测试里
依赖已删文件的方法删除（**4 → 1**、**3 → 1**），留下的那两条覆盖面与删前**相同**。

⚠️ **本任务零设备、零 androidTest 运行** ⇒ **十行状态列一个格都没动，仍是 10/10
`unverified`**（`docs/eval/c1-group-chat.md` 全文 **20 个 `unverified`** 同样一个没动）。

#### ③ 本批的统计口径（⚠️ 上一批那些数字按惯例保留不覆写）

⚠️ **本批之前是 `107 类 / 855 例`，本批之后是 `110 类 / 881 例`**
（`+3 类 / +26 例`，**0 失败 / 0 错误 / 0 跳过**）。⚠️ **这是
`python3 tools/verification/c1_doc_stats.py` 的实测输出，不是手抄**（复算命令见
`docs/eval/c1-group-chat.md` 的「统计口径复算脚本」小节）。
逐 commit 的类/例实测：`d00880fc` 之后 **108 / 864**（`+1 类 / +9 例`）；
`3d04c82c` / `5e4582ed` / `5435e403` / `4ee1ad5c` / `8111d178` **一路 108 / 864**
（三条切分修复净增 0 条 `@Test`、`4ee1ad5c` 是功能修复、`8111d178` 只改注释）；
`72a5e548` **110 / 881**（`+2 类 / +17 例`，正好 12 + 5）。
lint **0 error**、app **584W+6H=590**、15 模块 **617W+7H=624**（**与上一窗口逐位相同**），
`app/src/main` 那几个改动文件 **lint 命中 0**；`app/src/androidTest` 跟踪口径仍是 **61**
（含未入库的 `C1GroupUiE2EFixtureTest.kt` 是 64）。
⚠️ **台账锚点 `1b0e04a9` 的 78 个 commit 仍然没有被重算**，这 11 个 commit 全部追加进
`docs/eval/c1-group-chat.md` 台账的「补登：上一批之后到 HEAD `72a5e548` 的 11 个
commit」小节。

⚠️⚠️ **上一轮那两处「账实不符」已在本轮（`1052cd25`）修完**——上一轮的硬约束
（「`git diff` 里不允许出现删除 78 / 17 / 6 / 455 的行」）**本轮已解除**，结论是那条约束
**绑错了对象**：台账的「声明例数」是**事实陈述**（「这个类现在有 N 条测试」），**必须随代码
更新**；它与「状态列按窗口追加不覆写」是**两回事**，把两者混为一谈才导致上一轮不敢动这一个数。
逐条实测与改动（`python3 tools/verification/c1_doc_stats.py --only ledger`）：
① 两个在册类的声明例数改成实测值——`GroupStaleJobCommitSourceGuardTest` **9 → 10**、
`GroupStaleJobFailureSourceGuardTest` **6 → 7**（成因是 `d00880fc` 各加了 1 条 `@Test`）；
② 5 个类里**3 个并进台账**——`SummaryViewerScopeTest` 12 + `SummaryViewerScopeWiringSourceGuardTest` 5
+ `GroupEditActionVisibilitySourceGuardTest` 5（`+3 类 / +22 例`）；
③ 合计**新起一行**（历史那几行按惯例**保留不覆写**），实测 **45 类 / 479 例**
（`455 + 22 + 2 = 479` ✅），脚本现报 **`45 行 / 479 例 / 逐行核对 45 行全部相等`，0 FAIL**。
⚠️ **`GroupEditActionVisibilitySourceGuardTest` 上一轮被记成「1 条」，实测是 5 条**
（`git log --diff-filter=AM` 只有 `d00880fc` 一个 commit，即它自建立起就是 5 条），已订正。
⚠️ **另外 2 个类逐个复核后仍然不进表**：`BottomSheetScrollSourceGuardTest`（实测 **4** 条，
`d00880fc` 从 3 扩到 4）与 `CoilImageLoaderSourceGuardTest`（**6** 条）**整类不属于 C1 验收
范围**——口径是「**整类**是否属于 C1 验收范围」，不是「有没有一条用例碰到 C1 的文件」。
⚠️ **台账例数「取自 XML」这条口径与本轮改动不冲突**：本轮逐个核实这两个类的
**XML `tests` 属性与源码 `@Test` 计数一致**（10 / 7），7 个候选类全部如此。

⚠️⚠️⚠️ **「XML 口径不可用、110/881 只是源码 `@Test` 口径」这个说法本轮实测证伪，已订正**：
上一轮交接时确认过「`testDebugUnitTest/` 里只剩 1 个 XML，`unit` 报 `FAIL 1 类 / 5 例`」，
**本轮实测该状态已不存在**：`ls app/build/test-results/testDebugUnitTest/TEST-*.xml | wc -l`
= **110**，全量 `python3 tools/verification/c1_doc_stats.py` 的 `unit` 四条**全 OK**
（XML `110 类 / 881 例`、`0 / 0 / 0`、源码 `110 / 881`、`交叉验证 差 0 ✅ 一致`）。
⚠️ **所以 110 / 881 现在有 XML 与源码两个口径同时作数，不是「只有源码口径」。**
⚠️ **本轮没有为了让脚本变绿去删 XML、也没有改脚本判据，更没有跑 gradle**——现有 XML 已经
比一次重跑更硬：逐类比对过，**XML 类集合与源码含 `@Test` 的类集合完全相同**（各 110，
两侧差集都是 `[]`），**110 个类逐个 `tests` 属性与源码 `@Test` 计数无一不等**，合计同为 **881**。
✅ **脚本那条判据核实是对的、保留不动**：`c1_doc_stats.py:303` 的 `stale = xml_classes < 20`
判的是**文件数**，且 `--self-test` 有一条专门喂「只含 1 个 XML 的目录」并断言它**确实报红**
（`:602`）——**只有 1 个 XML 时报红是正确行为**，把阈值调大才会造出下一个会骗人的工具。

### ⚠️ 第九批（`cae27138` / `40cc8e89` / `1052cd25`，2026-10-06）：**三条纯文档 + 收掉上一轮自己造成的那处台账不一致**

⚠️ **本批 3 个 commit 全是纯文档**：**零 Kotlin、零测试、零脚本**
（`tools/verification/c1_doc_stats.py` 本批**一个字都没改**）。所以本批**不改变任何一个
测试类名或例数**，唯一改变的是**台账的声明值**——而台账声明值是**事实陈述**，必须随代码走。

| commit | 改了什么 | 结论 / 边界 |
|---|---|---|
| `cae27138` | 证伪遗留第 28 条（`ChatService.finishInterruptedPendingTools` 里零 `generateText`），补登真泄漏第 33 条 + 补登 11 个 commit + C9 五条边界来源指针 | ⚠️ **证伪的方向是对的**（错误源头是 `ChatService.kt` 注释，由 `8111d178` 改掉；错误从注释扩散进验收文档）。**但它顺手留下了本批要收的那处不一致**——见下面 ② |
| `40cc8e89` | 实施状态补第八批 + 五条诚实边界的逐条来源指针 + 统计口径改 110 类 / 881 例 | ⚠️ **110 / 881 当时是以「源码 `@Test` 口径」写的**，本批核实后确认 **XML 口径同样成立**（见下面 ③） |
| `1052cd25` | 台账订正（只改 `docs/eval/c1-group-chat.md`）+ 本节（只改本文） | ✅ **`--only ledger` 由 FAIL 转 OK**：`45 行 / 479 例 / 逐行核对 45 行全部相等` |

⚠️⚠️ **本批唯一实质改动 = 收掉「上一轮自己造成」的那处不一致**，不是新增功能。三件事：

**① 两个在册类的声明例数改成实测值**：`GroupStaleJobCommitSourceGuardTest` **9 → 10**、
`GroupStaleJobFailureSourceGuardTest` **6 → 7**（成因是 `d00880fc` 各加了 1 条 `@Test`）。
⚠️ **上一轮之所以没改，是把两条规矩混为一谈**：台账「声明例数」是**事实陈述**，必须随代码更新；
「状态列按窗口追加不覆写」管的是**另一个东西**。本批按前者办。

**② 5 个候选类里 3 个并进台账、2 个明确不并**（判据逐个写明，不硬塞）：
`SummaryViewerScopeTest` **12**（纯函数真单测，fail-closed / 议长悬空 / 反向越权）、
`SummaryViewerScopeWiringSourceGuardTest` **5**（接线源码护栏，含一条断言 KDoc 里
「⚠️ 待产品确认」不许被删）、`GroupEditActionVisibilitySourceGuardTest` **5**（群聊长按
Edit 按消息收口）——三者**都满足**「正文点名引用 + XML 对得上」且**整类属 C1 验收范围**；
`BottomSheetScrollSourceGuardTest`（实测 **4** 条）与 `CoilImageLoaderSourceGuardTest`
（**6** 条）**不并**，因为按本文既有口径「**整类**是否属于 C1 验收范围」，它们是
UI / 浏览器侧护栏，**不因「有一条用例碰到群配置面板」就整类进表**。
⚠️ **顺手订正上一轮一处笔误**：`GroupEditActionVisibilitySourceGuardTest` 被记成「1 条」，
实测 **5** 条（`git log --diff-filter=AM` 只有 `d00880fc` 一个 commit，自建立起就是 5 条）。
✅ 合计**新起一行** = **45 类 / 479 例**（`455 + 22 + 2 = 479` ✅），历史各窗口的
`366 / 35 类`、`395 / 37 类`、`408 / 40 类` 与第七窗口的 `42 类 / 455 例` **按惯例全部保留
不覆写**。

**③ 「XML 口径不可用」那条告警已不成立，如实订正**（详见上面第八批末尾那段）：
`testDebugUnitTest/` 里现在有 **110 个 XML**，全量脚本 `unit` 四条**全 OK**。
⚠️ **本批没有为让它变绿去删 XML、没有改脚本判据、也没有跑 gradle**——逐类比对证明现有 XML
已是全量且与源码**逐类相等**（类集合两侧差集都是 `[]`，110 个类的 `tests` 无一不等，
合计同为 **881**），**一次重跑只能复现这个结论、不会产生更强的新证据**。
✅ 脚本 `stale = xml_classes < 20` 判据核实正确、**保留不动**（`:602` 的自检专门喂
「只含 1 个 XML」并断言报红）。

⚠️ **本批同样不改变任何判定**：**零设备、零 `adb`、零 gradle**；契约 `:206` 点名的四类产物
**一份未增**。⚠️ **十例状态列一个格都没动，仍是 10/10 `unverified`**——审计方式是 Python
按**未转义 `|`** 逐行切单元格（`\|` 不切），**两张十例矩阵共 20 个数据行逐行判定，首判定
全部 `unverified`，非 unverified 的 0 行**。⚠️ **台账锚点 `1b0e04a9` 的 78 个 commit 仍然
没有被重算**（本批 3 个一个都不计入），**78 / 6 / 17、前缀分布、子包分布一个数都没动**；
`git diff` 里**没有任何一行被删除**。⚠️ **工作区那两处不属于本批的未提交改动
（`TavernMacroExpander.kt` modified、`C1GroupUiE2EFixtureTest.kt` untracked）全程未 add、
未 commit、未修改**。

### ⚠️ 第十批（`a8fa1cb8` / `b96912cb` / `df560180`，2026-10-06）：**堵住 vote 选票注入缺口 + 补两处覆盖缺口**

⚠️ **本批只有 3 个 commit、动了 3 个文件**（`GroupTurnCoordinator.kt` 一个 filter、
两个测试文件），外加本文。⚠️ **零设备、零 `adb`、`connectedAndroidTest` 未跑**，
**十例仍 10/10 `unverified`**，契约 `:206` 点名四类产物**一份未增**。

| commit | 改了什么 | 结论 / 边界 |
|---|---|---|
| `a8fa1cb8` | **先写红测试**：`an error node is not a ballot even when the provider echoes the upstream body`，构造 `errorNode(role=ASSISTANT, roleId=真实成员, turnKind=TURN_ERROR)` + 正文含一行 `VOTE: opt-a` | ✅ **红是实测出来的**，失败消息原文见下面 ②。既有那条用例（`GroupTurnCoordinatorTest.kt:436`）的 `assertFalse(text.contains(BALLOT_PREFIX))` **只对 `detail = "…boom"` 成立**，钉的是模板不是注入面 |
| `b96912cb` | `collectBallots`（`GroupTurnCoordinator.kt:548`）的 filter 追加 `&& it.turnKind != GroupChat.TURN_ERROR` | ✅ **逐字沿用既有约定**：`ChatManager.kt:2349` 的 `roundOutputPresent`（判定式在 `:2359`）已经是同一个负向判据、同一个 null-safe 口径。⚠️ 净增 12 行，**`GroupTurnCoordinator.kt` 里 `resolveVote` / `voteSummaryMessage` 的行号后移**（已登记在 B5 的 ④） |
| `df560180` | `GroupChatTest` **+4 条**：`parseCandidates` 显式形态 / 大小写 / 空文本 / 格式错误 / Markdown 修饰 trim / **`(?im)` 逐行声明现状** / **fence 假阳性现状** | ⚠️ **两处都只钉现状、不改行为**。`parseCandidates` 此前**零直接 JVM 用例**（所有投票用例都显式给 `config.voteCandidates`）。**已知假阳性（fence 内示例 `VOTE:` 被当真票）未修**，因为契约没要求、修它要先定义整套 markdown 感知规则 |

**① 为什么这一批存在：审计把原判据驳倒了**。`逐项状态` 第 4 行原本判「部分」，理由是
「传输层是模型自由文本 + `VOTE:` 正则，不是 tool-call 强约束」。**两条依据都经核实剔除**：
① **契约 C1 段（`client-changes.md:198-206`）grep `tool` / `tool_call` / `toolCall` 零命中**
（全文 326 行同样零命中）——契约从没要求 tool call，拿它当判据是自造判据；② **`tally`
签名没有正文参数**（`GroupChat.kt:856`，`tally(ballots, candidates, tiePolicy)`），
`:863` 过滤 / `:864` 按 `roleId` 去重 / `:868` 计数，自由文本**没有任何路径**能进裁决。
✅ 所以判定改成**达成**，同时把审计挖出的**真缺口**修掉——**「判定达成」与「有注入面」不矛盾**。

**② 缺口真实存在的唯一证据（红测试失败消息原文）**：

```
java.lang.AssertionError: 失败角色的错误节点必须被 collectBallots 拒收（否则陈旧注入行能顶替它这一轮的一票） expected:<[alice, carol]> but was:<[alice, bob, carol]>
```

（`java.lang.AssertionError at GroupTurnCoordinatorTest.kt:2196`，`85 tests completed, 1 failed`，
`./gradlew --offline :app:testDebugUnitTest --rerun` 退出码 **1**。）修复后同一条用例转绿。

**③ 最小性论证：`turnKind` 全部取值逐个过一遍**（`GroupChat.kt:225-229` 共 5 个常量 +
`GroupTurnCoordinator.kt:48` 的一个私有别名 + 可空）。
⚠️ `collectBallots` 修复**前**的 filter 只有 `it.role == MessageRole.ASSISTANT &&
it.roleId in roleIds`：

| `turnKind` | 生产者 | 在修复前 `collectBallots` 里是什么形状 | 需要额外门禁吗 |
|---|---|---|---|
| `TURN_USER`（`"user"`） | 用户消息 | `role = USER`，**被 `role == ASSISTANT` 挡掉** | ❌ 不需要 |
| `TURN_SPEAKER`（`"speaker"`） | `assistant(...)` 正常发言 | **这正是要收的票** | ❌ **必须保留**（挡住它就废功能） |
| `TURN_CHAIR`（`"chair"`） | 议长发言；`TURN_TIE_BREAK` 是它的**私有别名**（`GroupTurnCoordinator.kt:48`） | 议长在 vote 模式下的 `VOTE:` **是真票**（既有 `GroupTurnCoordinatorTest` 就有 `assistant("VOTE: opt-z", "chair", turnKind = TURN_CHAIR)`） | ❌ 不需要，**而且不能挡** |
| `TURN_VOTE_SUMMARY`（`"vote_summary"`） | `voteSummaryMessage`（`:665`） | `roleId = GroupChat.SUMMARY_ID`，**不是成员 id**，被 `roleId in roleIds` 挡掉；且 `validate`（`:444`）拒绝任何 `role_id == SUMMARY_ID` 的配置 | ❌ 不需要，**按纪律没再加一道** |
| `TURN_ERROR`（`"error"`） | 两个生产者：① `errorNode`（`:510`，`roleId = failedRoleId` **真实成员 id**）② `ChatManager.voteFailureNode`（`roleId = SUMMARY_ID`） | ① **穿过两道旧 filter，被收成票 ← 就是本批的缺口**；② 被 `SUMMARY_ID` 挡掉 | ✅ **就是这一条，本批已挡** |
| `null`（可空） | 旧消息 / 未标注 | 旧数据仍可能是真发言 | ❌ 不需要——负向判据 `null != TURN_ERROR` 为真，**旧消息仍算票**，与 `roundOutputPresent` 同口径（不制造 fail-open） |

✅ **结论：只挡 `TURN_ERROR` 就够，没有发现第二种 `turnKind` 也会被收成票**，所以不存在
「修法不够」的情况。⚠️ 另外逐个确认了 `TURN_TIE_BREAK` 那条脚手架（`role = SYSTEM`，
`withoutTieBreakInstruction` 按 `turnKind == TURN_TIE_BREAK` 回收）——`role = SYSTEM` 同样被
`role == ASSISTANT` 挡掉，**不需要**门禁。

**④ 本批改了什么数**（唯一的汇总值变化）：`:app:testDebugUnitTest` 全量 **110 类 / 881 例 →
110 类 / 886 例**（`+5`：+1 注入缺口红测试、+4 `parseCandidates` / fence 覆盖；**类数不变**）。
`GroupTurnCoordinatorTest` **84 → 85**，`GroupChatTest` **22 → 26**。
⚠️ **台账锚点 `1b0e04a9` 的 78 个 commit 仍然没有被重算**，本批 3 个一个都不计入，
**78 / 6 / 17 与两张分布表一个数都没动**，`git diff` 里**没有任何一行删除了 78 / 6 / 17**。
⚠️ **工作区那两处不属于本批的未提交改动（`TavernMacroExpander.kt` modified、
`C1GroupUiE2EFixtureTest.kt` untracked）全程未 add、未 commit、未修改**。
⚠️⚠️ **本批遗留一处已知红灯（本轮按硬约束不能自行消掉）**：
`tools/verification/c1_doc_stats.py` 现在 **1 条 FAIL**——`ledger` 那项
「台账逐行核对（声明 vs 源码 @Test）」报 **`2` 行与源码不符**：
`GroupTurnCoordinatorTest` **声明 84 / 源码 85**、`GroupChatTest` **声明 22 / 源码 26**。
⚠️ **这两行的台账在 `docs/eval/c1-group-chat.md:785` 与 `:788`，而本轮硬约束明确不许改那个
文件**，所以本批**没有为让它变绿去动台账、也没有为让它变绿去删用例**。
✅ **消掉它是机械的两格改动**：`:785` 的 `**84**` → `**85**`、`:788` 的 `**22**` → `**26**`
（该脚本的「声明例数合计」是从行里**求和**算出来的、不与表内 `479` 那行文本对拍，所以改完
两格即 FAIL → OK；⚠️ 但表里 `**合计 479**` 那行会变成 `484`，那是**给人看的**、脚本不查，
**要一并改就更好**）。⚠️ **本条是「台账事实陈述必须随代码走」与「本轮不许改台账文件」两条
规矩的正面冲突**，留给下一位按授权处理。
⚠️ **没按硬约束动的**：`GroupChat.kt`（`(?im)` 逐行声明那条注入面**核实成立但未修**，
等确认）、`ChatManager.kt`、`c1-group-chat.md`、`client-changes.md`——**四个文件一个字都没动**。

### ⚠️ 第十一批（`f1bf516e..74d82476`，2026-10-06）：**wire 级模型名从「缺能力」变成「已具备 + 待真机复核」**

⚠️ **本批 8 个 commit、零设备、零 `adb`、`connectedAndroidTest` 未跑**，
**十例仍 10/10 `unverified`**，契约 `:206` 点名四类产物**一份未增**。
⚠️ **但这是锚点之后第一批真的改到 `ai/` 生产代码去消一条硬理由的批次**——
前面那些批改的要么是 `app/`、要么是纯测试/纯文档。

**① 缺陷是什么（一句话：不是拿不到，是拿到了被丢掉）**：

| 环节 | 本批之前的实际状态 |
|---|---|
| `ChatCompletionsStreamDecoder.finish(reason, responseId, model)` | ✅ **一直正确**发出 wire 上的模型名（`StreamChunk.Finish` 第三个参数） |
| `StreamChunkHandler` 收到 `Finish` | ⚠️ **只取 `finishedAt`，把 `chunk.model` 直接丢弃** |
| 后果 | 助手消息上留不下网关自报的名字 ⇒ 验收证据里的 `deepseek-v4-flash` / `glm-5.2` 是拿 `message.modelId`（本地 `Model` 的 **UUID**）**回查本地 provider 模型表反查**出来的 |

⚠️ **这作废了一条旧记载**：早前说「要变成真抓包需要在 `OpenAIProvider` 侧记录响应
`model`（属于加日志，不属于本轮范围）」——**是错的**，**不需要在 provider 侧加日志**。
已在 `c1-group-chat.md`「已知遗留与风险」第 18 条作废。

**② 改了什么（逐 commit）**：

| commit | 改了什么 | 结论 / 边界 |
|---|---|---|
| `f1bf516e` | `UIMessage` 加 `val wireModelName: String? = null`（`ai/…/ui/Message.kt`） | ✅ **nullable + 默认值，插在 `turnKind` 后、`@Transient isSynthetic` 前**；`UIMessage` 是 `@Serializable`，随对话 JSON 落库 ⇒ **无需 Room 迁移** |
| `0c239788` | `StreamChunkHandler` 的 `Finish` 分支落盘 wire 名；非流式 `handleTextGenerationResult` **对称** | ✅ **不回退 `modelId`**（那是本地配置 UUID，由它反查出来的名字不是 wire 级证据）；缺失或全空白时**不覆盖**已有值，允许 `null` 传播 |
| `ae285843` | 新增 `WireModelNameProvenanceTest`（`ai/src/test/`，268 行 / **9 条 `@Test`**） | ✅ **9 例 / 27 处断言 / 退出码 0**。⚠️ **只覆盖 OpenAI chat-completions 一条路径**，`Claude` / `Google` **一行都没测** |
| `35b90d67` | 新增 `tools/verification/c1_wire_model_probe.py`（259 行） | ✅ 真实网关实测拿到两个 wire 名，**均 HTTP 200**。⚠️ **它直接打网关，完全不经过 app** ⇒ 证明的是「网关会自报」，不是「app 落得下来」；⚠️ **零测试用例**，仓库里没有 task 会自动跑它，**不是回归护栏** |
| `ab2f9b65` | `ChatList.kt` 注释里 `StreamChunkHandler` 的行号锚点 | 纯注释（`73`/`329` → `77`/`341`，逐行核对过），**零实现改动** |
| `d7971ba1` | `C1LiveModelSequenceTest` 模型名改 **wire 优先 + uuid 反查回退** | ⚠️ **仪器测试一行没跑过 ⇒ 只有编译验证** |
| `63d0a504` | 证据 JSON 记两个模型名并逐条标 `wire_model_name_provenance` | ⚠️ 同上，只有编译验证 |
| `74d82476` | 修 KDoc 里指向不存在 JSON 字面量的引用 | 纯 KDoc，**零实现改动** |

**③ 零设备证据（两条，逐条照记边界）**：

- **JVM：`WireModelNameProvenanceTest` 9 例 / 27 处断言 / 退出码 0**。含
  ①SSE 帧里刻意刁钻的字面量（`deepseek-v4-flash-250528`、`zhengyimeng/GLM-5.2-preview`）
  **逐字透传**；②**反查隔离**（构造 `Model(modelId="local-cfg-alias-9f3c")`，断言 wire 名
  既不等于 `model.id` 也不等于 `modelId`/`displayName` ⇒ 两条路径互斥）；
  ③无 `model` 帧 ⇒ `null`（**不是空串、不回退 `modelId`**）；④非流式对称；
  ⑤非流式空串不当作有效名；⑥序列化往返不丢；⑦旧 JSON 无该键 ⇒ `null` 且不抛。
  **非空验证**：临时删掉 `Finish` 分支里那一行 `wireModelName = …` 重跑 ⇒
  **`9 tests completed, 3 failed, EXIT=1`**，恰好是 3 个流式来源用例；随后还原并
  `diff` 确认与 HEAD 一致。⚠️ **它只证明那 3 条依赖这一行**，非流式两条仍绿。
- **真实网关探测**（key 只走命令行 / 环境变量，脚本内 **0 处硬编码**）：

  | requested | wire_model | status | finish_reason | usage |
  |---|---|---:|---|---|
  | `deepseek-v4-flash` | `deepseek-v4-flash` | 200 | `stop` | prompt 15 / completion 39（reasoning 16） |
  | `glm-5.2` | `glm-5.2` | 200 | `stop` | prompt 23 / completion 533（reasoning 502） |

  非流式（`--no-stream`）也验过 `deepseek-v4-flash` → `wire_model: "deepseek-v4-flash"`，200。
  ⚠️ **密钥卫生**：实测后该 key 在 `git diff ea6b7c4c..HEAD` 与**全部 commit message**
  里出现 **0** 次。

**④ 测试口径已改（但只有编译验证）**：`C1LiveModelSequenceTest` 的模型名现在是
**`UIMessage.wireModelName` 优先、`modelId` uuid 反查回退**，并加了
「**不允许两者皆空**」的**防退化断言**；证据 JSON 逐条记
`wire_model_name_provenance`（取值 `wire_response_model` /
`uuid_reverse_lookup_fallback`）、`wire_model_name_reconciliation`、
`wire_model_name_provenance_counts`、`wire_and_reverse_lookup_agree`。
⚠️⚠️ **这四类新字段与 wire 优先判定逻辑全部只有编译验证**——设备离线。

**⑤ ⚠️⚠️ 诚实要点（这一条最容易被拔高）**：**wire 名与请求名一致**，
说明**原先 UUID 反查出的名字恰好是对的**。所以这**不是「反查错了被纠正」**，
是「**取得途径从反查变成 wire 直取**」——⚠️ **不构成对既有证据的追溯性升级**：
设备上那份既有证据里的两个名字**本来就写对了**，只是**来源不同**。

**⑥ 剩余的设备阻塞项（一条都没解除，照记）**：

| 阻塞项 | 现状 |
|---|---|
| **仪器测试跑不起来** | ⚠️ **一行都没跑过**。设备离线（`192.168.31.183` 上不是手机，全端口 65535 扫描无 adbd） |
| **设备上的证据文件** | ⚠️ **现在记的仍是 UUID 反查值**，要等真机重跑才变。**这里没有任何真机数字** |
| 旧 Room 库消息树反序列化 | ⚠️ **没对真实数据库跑过**（只靠 nullable+默认值 + JVM 序列化往返） |
| `wireModelName` 的 UI 呈现 | ⚠️ **没做，也没打算做** —— 所以不构成任何「用户看得见模型名」的证据 |

⚠️ **另外三条硬阻塞本批同样一条都没解除**（设备 / UI 端到端 / 酒馆本体 / 相机扫码四类）。
**十行状态列一个格都没动，仍是 10/10 `unverified`**。

**⑦ 本批改了什么数**：`:app:testDebugUnitTest` 全量 **110 类 / 893 例 / 0F 0E 0S**。
⚠️ **这 4 条增量（881 → 893）全部来自上一批的 `5186349f`**（`GroupChatTest` `29 → 33`，
围栏修复），**与 wire 模型名无关**——本批**一行都没改 `app/src/test`**。
⚠️ **台账锚点 `1b0e04a9` 的 78 个 commit 仍然没有被重算**，本批 8 个一个都不计入。
⚠️ **C1 相关测试类台账仍是 45 类 / 491 例**：新增的类在 **`ai/src/test/`**，
而台账口径是「能在 `app/build/test-results/testDebugUnitTest/*.xml` 对上」，
所以它**不并入台账**；`d7971ba1` / `63d0a504` 改的是**仪器测试**，台账本来就不收。
⚠️ **工作区那两处不属于任何 agent 的未提交改动（`TavernMacroExpander.kt` modified、
`C1GroupUiE2EFixtureTest.kt` untracked）全程未 add、未 commit、未修改。**

**⑧ 下一位要做的一件事**：真机重跑 `realProviderRoundRecordsGenuineTokenUsage`，
把落盘 JSON 拉回来，确认 `wire_model_name_provenance` 变成 `wire_response_model`
而不是 `uuid_reverse_lookup_fallback`。📍 判据不是「代码里现在能不能拿到 wire 名」，
而是「**设备上那份落盘 JSON 里的 provenance 取值是什么**」。

### ⚠️ 第十三批（`be7952a83` / `19545e076`，2026-10-06）：**`GroupChat` 行切分统一到 6 个 Unicode 行终止符**

⚠️⚠️ **先说清性质：这是锚点之后第一次为「补一条 `GroupChat` 护栏」而真的去动
`app/src/main` 的生产代码**（前面 `5186349f` / `8a24b5d0` 那两批也动过 `GroupChat.kt`，
本批是同一条线的第三批）。⚠️ **它同样一条验收证据都没产出**——零设备、零 `adb`、
零 `androidTest`。

**① 缺陷：切分行与正则行终止符口径不对称 ⇒ 静默丢候选 / 静默丢票**

`parseCandidates` 与 `linesOutsideCodeFences` 原先**两处都**用 Kotlin 的
`text.lineSequence()`，它**只在** `\n` / `\r` 处断行；而 Java `Pattern` 的行终止符是
**6 个**（`\n` / `\r\n` / `\r` / `\u0085`NEL / `\u2028`LS / `\u2029`PS）。⇒ 模型一旦输出
`U+2028` / `U+2029` / `U+0085`，声明行正则的 `.` 跨不过行终止符、`$` 又只锚行尾，
**必然失配** ⇒ **静默丢候选 / 静默丢票**。

**② 修法**：新增**私有** helper `GroupChat.unicodeLines(text)`（`GroupChat.kt:994`），
`parseCandidates`（`:807`）与 `linesOutsideCodeFences`（`:1047`）**两处共用**它。
✅ **「两处必须共用」有可观察证据**：只把 `parseCandidates` 单独换成 `text.lines()`
⇒ **恰好 2 条红，且恰好是两条 `parseCandidates` 用例**，票面 / 围栏那 4 条**仍绿**。

**③ 探针实测（离线 `kotlinc` 探针，输出在仓库外 `/tmp/opencode/probe/probe-out.txt`）**

| 探针 | 实测 | 结论 |
|---|---|---|
| `lineSequence()` | LF / CRLF / CR **各 2 行**；NEL / LS / PS **各 1 行** | 缺的正好是 **NEL / LS / PS** |
| `Pattern` 反证 | `(?s)候选：a.b` 跨 LS / NEL / PS **全命中**；`(?m)^b$` 在 `a<sep>b` 上五个**全 true** | 三者**本来就是** `Pattern` 行终止符 |
| `String.lines()` | NEL / LS / PS **各 1 行** | 与 `lineSequence()` **口径完全一样**，⚠️ **不能蒙混** |
| `trim()` | `isWhitespace('\u2028')`/`('\u2029')` = **true** ⇒ **会剥**；`isWhitespace('\u0085')` = **false**（类别 15 = CONTROL）⇒ **不剥** | ⚠️ **NEL 才是那个真洞** |

⚠️⚠️ **两条被实测推翻的说法（按实测记，不要照抄旧说法）**：
① 「前置分隔符的票修后仍能解析」——**错**，实测 `parseBallot("VOTE: \u2028opt-a")` /
`\u2029` / 前置 `\u0085` **三者修后都是 `null`**；
② 「`trim()` 会剥三个分隔符」——**错**，只剥 LS / PS，**NEL 不剥**。
**真正修掉的洞是尾随 NEL**：`parseBallot("VOTE: opt-a\u0085", …)` 修**前 `null`**
（id 变成 `opt-a\u0085` 配不上候选 ⇒ 静默丢票）、修**后 `opt-a`** ✅，
护栏用例 `a trailing NEL no longer corrupts the candidate id`（**改前红、改后绿的真洞**）。

**④ ⚠️ 一处有意的行为收紧（改变了可观察行为）**：`parseBallot("VOTE: \u2028opt-a")`
改**前能**解析出 `opt-a`（靠 `trim()` 剥 LS），改**后 `null`**（LS 成行终止符 ⇒
`VOTE: ` 成空 body 票行 ⇒ 集外）。**裁决**：`trim()` 剥而切分不剥**纯属两个 JDK API
口径不一致的巧合，不是设计**；统一后「空 body 判无票」与本文件既定立场
**「宁可让畸形 id 配不上票，也不静默改写用户写的东西」**一致。
✅ 护栏用例 `a ballot with a leading unicode separator is no longer accepted` 钉住它。

**⑤ 测试**：`GroupChatTest` **33 → 39**（`19545e076`，**+229 / −7**）——改 **2** 条既有
断言的期望值（`:264` / `:269`，`emptyList()` → `[a, b]`）+ 新增 **6** 个用例方法；
`:273` / `:274` / `:276` / `:277` **四条一字未动**（有意：证明**方向没变**、仍然失败关闭、
仍然不泄漏）。反向验证 **3 处**：helper 退回只切 `\n`/`\r` ⇒ **6 条红**；换成
`lineSequence()` ⇒ **6 条红**；只换 `parseCandidates` ⇒ **2 条红**。

**⑥ 本批唯一改变数字的地方**：C1 相关 JVM 测试类台账 **45 类 / 491 例 → 45 类 / 497 例**
（`GroupChatTest` `33 → 39`；✅ 复算实测 `45 行全部相等` / 声明合计 **497**）；
`:app:testDebugUnitTest` **111 类 / 915 例 → 111 类 / 921 例**（`+6` **全部**来自
`GroupChatTest`，**类数 111 未变**，因为本批**没新增测试类**）；lint app
**0 error / 584W / 6H = 590**（与基线**逐字一致**）。
⚠️ 台账锚点 `1b0e04a9` 的 **78 个 commit 仍然没有被重算**，本批一个都不计入。
📍 完整证据、逐条边界见 `docs/eval/c1-group-chat.md` 的
「Unicode 行终止符统一切分（零设备，2026-10-06，HEAD `19545e076`）」那一节。

⚠️⚠️⚠️ **取证局限（逐条如实记）**：
- **零设备**：本轮**没跑任何 `androidTest`**，全部只有 JVM 验证；
  `parseBallot` / `parseCandidates` 的**真实调用零份**。
- **探针是离线探针**，**不是真机 / 真实网关的模型输出**——输入全是**手写**字符串。
- ⚠️⚠️ **真实模型是否真的会输出 `U+2028` / `U+0085`，本次没有任何实证** ⇒ 修的是
  「**一旦出现就静默丢票**」这个**失败模式**，**不是**「模型经常这么输出」。
- **「假阳性率下降」无实测依据**，只是失败关闭方向；
  **`unicodeLines` 的大文本性能未测**。
⚠️ **十行状态列仍是 10/10 `unverified`**——硬理由①未被本批触及，②③④
（真机 UI 端到端 / 酒馆本体 / 相机扫码）**全部原样**。
⚠️ **工作区那两处不属于任何 agent 的未提交改动（`TavernMacroExpander.kt` modified、
`C1GroupUiE2EFixtureTest.kt` untracked）全程未 add、未 commit、未修改。**

### ⚠️ 第十四批（真机采集 + 注入点 `08702d88b`，2026-10-06，HEAD `f507ebc9a`）：**真实 token 下的预算截断第一次采到库内七值——但这是一次失败跑，状态仍 10/10 `unverified`**

⚠️⚠️ **先说清性质**：这是**一次真机采集 + 一个测试注入点 commit**（`08702d88b`），
不是一个「通过」——`am instrument` 实测 **`Tests run: 1, Failures: 1`** /
**`INSTRUMENTATION_CODE: -1`** / `Time: 585.298`。⚠️ **失败是注入截断的预期后果**：
用例写死「期望 3 条助手消息」，预算截断只产 2 条 ⇒ `awaitAssistantMessages` 先超时
（断言原文与行号见 c1 新证据节 ⑤）。⚠️ **十行状态列仍是 10/10 `unverified`**。

**① 之前为什么零份 / 这轮补的注入点**：mock 截断用例用 `token_budget_per_round=1`
（**构造入参，不是 provider 返回的真实 token**）；真实网关用例预算写死
`mainBudget=100_000`（`C1LiveModelSequenceTest.kt:183`），整轮真实 token（约 2.0 万）
**从不触发截断**。`08702d88b` 加 instrumentation 注入点
`-e c1TokenBudgetPerRound <Int>`（参数名 `:185-186`、getter `:204-212`，缺省/解析失败
回落 `mainBudget`；**只作用于真实网关用例**），本轮注入 **9000**。

**② 七值（主机侧逐值复核；跨两个库）**：

| # | 字段 | 值 | 出处 |
|---|---|---|---|
| 1 | `spent_tokens` | **13631** | `db-poll/s2/rikka_hub` → `group_runs`（生产库；`group_runs` 无法隔离） |
| 2 | `token_limit` | **9000** | 同上 |
| 3 | `skipped_role_ids` | **`["c"]`** | 同上 |
| 4 | `committed_role_ids` | **`["a","b"]`** | 同上 |
| 5 | `status` | **`BUDGET_STOPPED`** | 同上 |
| 6 | `reason` | **`token_budget_exceeded`** | 同上 |
| 7 | 库内助手发言条数 | **2 条**（a、b；+user 共 3 个 `message_node`） | `db-poll/e1/c1-live-evidence.db`（会话/消息走独立证据库） |

⚠️ conversation `8e060471-…8cf2` / round `round-4db4c9d7-…e7d1`；**s2 是前六值的唯一存证**
（更晚 `s5` 快照里该行已被 teardown 删除、行数 0）；⚠️ `rikka_hub` 的 `message_node`
**没有**该会话的行——第 7 值只能从证据库取（主机复核实测）。

**③ 用量自洽**：a（wire `deepseek-v4-flash`）`prompt 6829 + completion 101 = 6930`
（cached 6144）、b（`glm-5.2`）`6643 + 58 = 6701`，**Σ = 13631 = `spent_tokens`** ✅。
a 后 `6930 < 9000` ⇒ b 执行；b 后 `13631 ≥ 9000` ⇒ c 跳过（库里 c 零痕迹；
⚠️「c 从未被调用」是 DB 痕迹上的推断，无独立抓包）。

**④ 四条限制（照 c1 新证据节，不许美化）**：

1. **`c1-real-raw-dump.json` 本轮没写出，且代码位置证明它在截断路径下永远写不出**——
   `writeEvidence`（`:1338-1339`）在两个 `await`（`:1321-1325` / `:1329`）之后；失败跑
   拉回的 dump 是更早那次跑的（sha `e50bf524…`、conversation `166e3e5b…`，与更早
   `files/` 那份逐字节相同）⇒ 七值只能来自运行中 DB 快照。
2. **`am instrument` 的 shell 退出码无法提供**：`setsid nohup` 脱离运行、退出码无人回收；
   权威结果是 `INSTRUMENTATION_CODE: -1` + `Tests run: 1, Failures: 1`。
3. **`wire_model_name_provenance_counts` / `wire_model_name_reconciliation` / 正式
   `actual_model_call_sequence` 仍零份**（只在测试通过时才写的那份 JSON 从未产出）；
   DB 快照里的 wire 名 a `deepseek-v4-flash` / b `glm-5.2`，provider 表名对得上
   （`DefaultProviders.kt:307-308` / `:312-313`）——⚠️ **provenance=`wire_response_model`
   是派生推断，不是落盘证据**。
4. **设备副作用**：熄屏 30s 后 ColorOS 冻结整进程（两次；`Time: 585.298` 含约 9 分钟冻结）、
   `am start` 解冻 + `svc power stayon true`（**原值未记录**）、首轮残留一条 `RUNNING` 行
   （`2b6c129f-…9480` / `round-a8d28946-…ca53`，spent=0，**未删**）、未 `pm clear` /
   未用 `connectedAndroidTest`、未 kill gradle/Kotlin daemon。

**⑤ 为什么状态不动**：契约 `:206` 要求的「实际模型调用序列」正式 JSON 与 viewer 可见消息
ID 台账本轮**一份未增**，且这次跑**不是通过** ⇒ 只登记证据，**十行状态列一个格都没动**。
📍 七值表、失败断言原文、trace 关键行、SHA-256 清单与全部边界见
`docs/eval/c1-group-chat.md` 的「真实 token 下的预算截断（零 mock，真机，2026-10-06，
注入点 `08702d88b`）」一节（本批两份主存证实测 SHA-256：`s2/rikka_hub` =
`f693176a2bf26006a9111cb103aad3cc559024dc3835810caf1b7eaa8899332f`、
`e1/c1-live-evidence.db` = `df0cb26a748d42f742778f083d2593ae2338365f7a202decaa88d1faa2a817d5`；
均在仓库外 `/tmp/opencode/c1-budget-real2/`）。

⚠️ **本轮只提交本文与 `c1-group-chat.md` 两个文档，未 add / commit / 修改工作区里任何
其他未提交改动。**

### ⚠️ 第十五批（真机采集第八轮 + `43d607bdd` / `31b6b5a52`，2026-10-06，HEAD `31b6b5a52`）：**真实网关正式证据历史首次产出——硬理由①（真实网关路径）已消；十例仍全 `unverified`**

⚠️⚠️ **先说性质**：一次真机采集窗口 + 两个测试侧 commit（`43d607bdd` 静默失败 → fail-loud / `31b6b5a52` raw dump 无条件落盘），**生产代码零改动**。设备 = 第七轮同一台 OnePlus `PKG110`（`OP5D2BL1`）/ Android 16 / API 36 / `arm64-v8a`，无线调试 `192.168.31.183:37773`。⚠️ **十行状态列仍是 10/10 `unverified`**，20 个状态格一个判定都没改（① 句的订正已逐格追加）。

**① 历史首次产出 `c1-live-evidence-real-provider.json`（最大成果）**：真实网关用例（`am instrument` 退出码 0 / `Time: 14.017` / `OK (1 test)`）在 wire 优先口径下重跑，**这份「全部断言通过才写」的正式证据历史以来第一次被写出**——`wire_model_name_provenance_counts = {wire_response_model: 3, uuid_reverse_lookup_fallback: 0}`、reconciliation 3/0/0、`wire_and_reverse_lookup_agree=true`；真实 usage a `6829/151/6980`、b `6692/60/6752`、c `6895/104/6999`，**Σ=20731 = `spent_tokens`**；序列 `deepseek-v4-flash → glm-5.2 → deepseek-v4-flash`。文件 10441 B / sha256 `3b02140b216be4b684927d59a990726ba231d461dc665cf504559814c147b8b1`（仓库外 `/tmp/opencode/c1-device-round3/`）。
⇒ **硬理由①（模型名不是 wire 级抓包）对真实网关那条路径已消**（判据 = c1 文档「判定规则」第八条自己给出的操作化形式：设备落盘 JSON 的 provenance 是否为 `wire_response_model`；mock 一侧按第七轮第二套判据也成立）。⚠️ **但这不是「C1-02 可以判通过」**——该行清单项仍未全齐，逐行依据见 c1 文档第八轮⑨。
⚠️ **诚实点**：角色 c 上一轮那次的静默失败**本轮没有复现**（两次默认预算运行都成功，执行者报告；可核产物是 16:58 一跑）⇒ 定性为**偶发**；**`43d607bdd` 没有加重试、不构成「c 现在能成功」的原因**；本轮**没有拿到任何 HTTP 状态码**。

**② 预算截断（9000）finally 落盘修复真机验证通过**：raw dump 首次在截断路径下写出（5039 B / sha256 `e29046fa8d3dfe6033716ef1a50a083ea834d6bcb9094c20f390d4ca40d9c729`；`BUDGET_STOPPED / spent=13571 / limit=9000 / committed=[a,b] / skipped=[c]`；`block_exception` = 300s await 超时 AssertionError 原文）；正式证据文件**未被改写**（SHA 与 `real-default/` 那份逐字节相同）。⚠️ 两次跑 `13571` vs `13631` 是 completion token 波动，**不是矛盾**；本轮这次 `am instrument` stdout 丢失（**无退出码 / 无 `Tests run`**）。

**③ mock 4/4 + 全量 64/1 + UI 部分覆盖**：4 条 mock 全 `OK (1 test)`（退出码 0；四份 SHA 与「UUID+13 位时间戳规范化后逐字节相同」复核见 c1 文档）；⚠️ **mock 脚本磁盘本体已丢失，现存 20061 B 是从 opencode.db 工具调用记录逐字节恢复的**（write+edit 记录按序重放与恢复件逐字节相同、`py_compile` 通过）。全量 `Tests run: 64, Failures: 1`（唯一失败 = `parseMentionsFromUiText` 缺 `-e uiText`，设计如此）、Coil 单例异常 **0 次**、真实网关那条全量内通过。UI：@ 选择器覆盖较好（真实文本回读 + 生产解析通过）；成员头像组**仅截图**；筛选 chip 三态点击有截图 + 切换前后 DB 计数不变，但**「真的按类型过滤」未验证**（设备助手被测试助手顶替、列表恒空）。
⚠️ **设备卫生遗留**：全量后主 DataStore 助手 = 测试助手「角色甲/乙/丙」，默认 `0950e2dc…` 消失（`settings.pb` 22365 B / sha256 `924cda89ec5b8140e71540456c4f1b8b16569b05c85730a413305292468845bd6`，`0950e2dc` 0 次）；夹具会话归属 0950e2dc ⇒ 列表恒空。**下次用 UI 夹具前先恢复 settings**（已登记 c1 文档「已知遗留与风险」第 34 条；另有第 35 条：mock 恢复件在仓库外 `/tmp`）。

**④ 为什么十例状态不动**：真实网关这一跑覆盖的只是 **pipeline（C1-02 的路径）**，其余各行点名的路径（显式 @ / roundtable 真实调用 / vote 真实调用 / 取消超时 / 幂等续跑 / 记忆空间 / 酒馆扫码 / UI 筛选）都没被它调用；C1-05 的 raw dump 是诊断文件、正式序列 JSON 仍未产出。逐行依据见 c1 文档「C1 真机证据采集第八轮」⑨。
📍 全部数字、SHA-256、限制与逐行判定见 `docs/eval/c1-group-chat.md` 的「C1 真机证据采集第八轮」节；台账第十批 22 个 commit 已追加（锚点 `1b0e04a9` 不重算，声明值 46 类 / 501 例未动）。

⚠️ **本批只提交本文与 `c1-group-chat.md` 两个文档，未 add / commit / 修改工作区里任何其他未提交改动。**

### ⚠️ 第十六批（真机采集第九轮 + 酒馆解析器验证 + 设置恢复，2026-10-06，采集父 HEAD `4d73a26b4`）：**筛选 chip 两半真机通过；设置污染已恢复（pb 手术）；酒馆解析器级接受 16/16；新缺陷「删最后一个助手必崩」；十例仍全 `unverified`**

⚠️⚠️ **先说性质**：一次真机采集窗口（OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`，无线 `192.168.31.183:37773`）+ 一次仓库外酒馆解析器验证（本机 node），**生产代码零改动**；⚠️ **20 个状态格一个判定都没改，仍是 10/10 `unverified`**（② 句第九轮订正已逐格追加，C1-09 / C1-10 两行另加注记）。

**① 筛选 chip：「真的按类型过滤」+「不丢数据」两半都通过（真机 UI 级）**：全部 4+2=6 / 单聊 4+0=4 / 群聊 0+2=2 / 切回 4+2=6（「群」徽标 2/0/2/2，登记时按 XML `text=` 独立复算）；切回 XML 与切走前**逐字节相同**（sha256 `f4c2b017…fbb56`）；切换前后主库 6 行（DIRECT 4 / GROUP 2，全归 `0950e2dc`）与 `rikka_hub` / `-wal` / `-shm` 三文件 SHA **完全不变**（Python sqlite3 + sha256sum 复核）。⚠️ **这是 uiautomator/截图级证据，仓内没有对应 androidTest**；契约 `:232-235` 明写「只看截图或只看 UI 状态 → `unverified`」⇒ **C1-10 仍不升级**。

**② 用户设置污染已恢复（pb 字节手术，涉用户真实数据）**：污染态 `6a0423c4…d4883`（22365 B；`0950e2dc`=0 / `0c1c11ae`=4）；根因 = `C1LiveModelSequenceTest.kt:517` 纯内存快照 + `:643-645` `runCatching` 吞异常 + `SettingsRepository.update()` 在 `init==true` 时 no-op（`SettingsRepository.kt:442-446`，真实路径 `core/data/datastore/`）。手术（`assistants`→`[]` + `select_assistant` 等长替换，自研解析器断言其余 45 键逐字节不变）后由 app 自己落盘默认助手；SHA 链 `fadfe1ff…`（19047）→ `9129ebb6…`（19047）→ `a2fa5fa3…`（17386）→ `9b6478ab…`（18440）→ 终态 **`37baf83f…`（18440；`0950e2dc`=2 / `0c1c11ae`=0）**；UI 抽屉恢复 6 条会话、force-stop 重启后仍 6 条；主库 6 个会话全程未动。⚠️ **测试代码根因未修，下一轮全量仪器测试会再次污染**（c1 文档遗留第 34 条续项）。

**③ 酒馆解析器级接受 16/16（硬理由③的部分推进，未消）**：导出物 = 生产编解码器 `TavernChatCodec.exportGroupJsonl` 经 `C1pGroupExportHashTest`（gradle 退出码 0 / `tests="5"`）产出的 3615 B / `36e6585f…c8b9` 文件；SillyTavern 钉死 commit `06bde939fb1e9c4c8d8641d810f0a916b5bce127`（blob SHA 与 `docs/beyond-operit-open-source-references.md:221` 登记一致）；harness **16/16、退出码 0（登记时自跑复核）**。⚠️ **只是解析器层**：没起服务 / 没跑 UI / 没走写盘、**没有群注册**（野生 `.jsonl` 不会出现在某群下）；`open→save` 丢私有表头块 ⇒ 重存后回导得 `NoConfig`；**相机扫码仍零份**。**不许写成「酒馆已能打开」。**
<br>⚠️⚠️ **订正（2026-10-07，第二十七批 / 真机第十七轮，HEAD `e2bfce4ad`，原文保留）：上面「重存后回导得 `NoConfig`」是错的——实际是 `Unsupported`。** `TavernChatCodec.importGroup` **`:298`** `document.header[GROUP_FIELD] as? JsonObject ?: return null`（表头无 `khatkit_group` **直接 `return null`**）→ 调用方 `GroupTavernImport.kt:176-180` 映射 `TavernGroupImportOutcome.Unsupported`（`isError=true`）；**`NoConfig` 只在「`khatkit_group` 键在、但没 `config`」的畸形文件上可达**。而酒馆侧已从「解析器级 16/16」推进到**应用级**：起真服务 @`06bde939`/AGPL、免登录+CSRF、`/api/chats/group/import` 接受（**字节级 copy**）+ `/group/get` 服务端解析正确（条数/顺序/`name`/`is_user`/`is_system`/`chat_items`）+ 私有顶层键保留 + 野生 jsonl 行为 + `open→save` 丢私有块。⚠️ **但真实相机扫码仍零份、系统分享面板 UI 零份、`NoConfig` 真实分支未验** ⇒「打开并回导」闭环仍不成立，**仍不许写成「酒馆已能打开」**。详见 `docs/eval/c1-group-chat.md`「C1 真机证据采集第十七轮」与遗留第 52 / 53 条。

**④ 新缺陷（未修，只报告）**：只有 1 个助手时删除会崩——`AssistantViewModel.removeAssistant`（`AssistantViewModel.kt:48-61`）置空 settingsFlow → `SettingsRepository.getCurrentAssistant()`（`SettingsRepository.kt:745`）的 `assistants.first()` 抛 `NoSuchElementException`（`ChatViewModel` 的 main 收集器）⇒ 删不掉、进 SafeMode（崩溃栈 `ui/r4-12-after-3.xml`）。另：成员头像组仍只有弱证据（截图有、无断言、点按未证实）；上一轮的 `RUNNING` 残留行仍在、未清。详见 c1 文档「C1 真机证据采集第九轮」与遗留第 36 / 37 条。
✅ **第十八批订正（2026-10-06，HEAD `81147639a`）：本条的「未修、只报告」已过期**——`85f708169`（production 2 文件 +29/−9）与 `81147639a`（6 例单测）已修：`normalizeAssistants` 读写路径共用、`getCurrentAssistant()` 变全函数、`AssistantViewModel.removeAssistant` 改走 `Settings.removeAssistant`。⚠️ **真机 SafeMode 路径未复测**（零设备，只有单测 + 探针证据）。见本文件「第十八批」与 c1 文档「生成失败健壮性与助手空列表修复」节 ② / ④。

⚠️ **本批只提交 `docs/` 两个文件（`c1-group-chat.md` + 本文），未 add / commit / 修改工作区里任何其他未提交改动。**

### ⚠️ 第十七批（页面入口合并 + UI 三条 androidTest 纳入 + 一次失败收尾的真机采集，2026-10-06，HEAD `d133b400b`）：**群聊单聊合并为 `ChatPage` 唯一入口；成员头像组首条 androidTest 断言真机通过；chip/@ 未跑完；一次 `kill -3` 操作失误；十例仍全 `unverified`**

⚠️⚠️ **先说性质**：本批 = **3 个代码 / 测试 commit**（`6b4a56d9f` / `a4b8be9da` / `d133b400b`）+ **一次未收尾的真机采集窗口**（OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`，无线 `192.168.31.183:37773`）。⚠️ **20 个状态格一个都没升级，仍是 10/10 `unverified`**（只有 C1-10 两处状态格就地追加第十轮订正）。

**① 页面入口合并（`6b4a56d9f` / `a4b8be9da`）**：用户指令「现在先把群聊和单聊页面合并到一起 服用同一个页面」。合并前有分派点 `GroupOrDirectPage`（按 `type` 选页）+ 单聊 `ChatPage` + 群聊 `GroupChatPage`；`ChatScaffold` 及 `GroupTopBar` / `GroupInfoChip` / `GroupConfigSheet` / `GroupMemberBar` **早已是共享组件**。`6b4a56d9f` 把 `GroupTopBar` / `GroupInfoChip` / `GroupConfigSheet` 由 `private fun` 升 `internal fun`、抽出顶层 `groupConfigSave` 与 `internal val groupCanEditMessage`（**val lambda 非 fun**）。`a4b8be9da` 让 `ChatPage.kt` 按 `isGroup = isGroupConversation(conversation)`（定于 `ChatManager.kt:2352`）条件注入 5 槽（`topBar` / `listOverlay` / `bottomBarAboveInput` / `extraCompletionProviders` / `canEditMessage`），删掉 `GroupOrDirectPage` 与 `GroupChatPage` 两个函数 + KDoc + 19 个 import；`RouteActivity.kt` 直接调 `ChatPage(`。**`ChatPage` 现是唯一页面入口**。⚠️ **编译陷阱**：`if (isGroup) { { … } } else {}` 不能编译（外层 `{}` 是代码块、块值非 lambda），必须写 `if (isGroup) ({ … }) else ({})`。⚠️ **行为变化（知情接受）**：删掉 `type == null -> Unit` 门控后所有会话早一帧挂载 `ChatPage`；单聊侧注入参数显式传但全等于 `ChatScaffold` 默认值。

**② UI 三条 androidTest 纳入（`d133b400b`）**：新增 `app/src/androidTest/.../C1GroupFilterAndAvatarUiTest.kt`（**569 行 / 3 `@Test`**，SHA-256 `a1ffffd6…9fc84e`），引用 testTag `chat_input` / `group_member_bar` / `drawer_conversation_list`（生产代码里本就存在；`d133b400b` 本身生产代码零改动）。⚠️ 后两个 testTag 是**更早的两个独立 commit**（`15ce2f251` GroupMemberBar、`467f67ad9` ChatDrawer，只加语义不改行为）加的。

**③ 真机结果（如实）**：两个 APK `install -r -t` 均 `Success`；**三条里只有测 2 `groupMemberBarRendersOneAvatarPerRole` 通过**（`round5-instrument.log` 里 `INSTRUMENTATION_STATUS_CODE: 0`）——**这是硬理由②的第一条 androidTest 级自动化断言**；测 1 `typeFilterChipsFilterWithoutLosingData` 刚启动（`STATUS_CODE: 1`）即 `INSTRUMENTATION_RESULT: shortMsg=Process crashed.`，测 3 `atMentionPickerInsertsParseableMention` **从未出现**（`numtests=3` 只跑到 2）。

**④ `kill -3` 操作失误（不是测试失败）**：18:59:50 执行者为看线程栈对测试进程执行 `run-as … kill -3 28072`，ColorOS 记 **`reason=13 OTHER KILLS BY SYSTEM … o-stop(40)`**，把 instrumentation 一并杀停 ⇒ 上面那句 `Process crashed.`；此后设备无线调试端口 `37773`→`38493`→全部 `Connection refused`，`adb devices` 持续为空。

**⑤ 非空验证未执行 + 设备设置未恢复**：三条断言的「非空」只是**按构造推断**（**三次破坏未做**）；唯一证据 `round5-instrument.log`（SHA-256 `1e43d6c4…d348a04`）+ `logcat-full.txt`（`e5e66471…46b1d3f`）在仓库外 `/tmp/opencode/c1-ui-assert/`，**设备侧未能 pull**；`screen_off_timeout` 现为 **`600000`（原值 `30000`）**、`stayon true` 未恢复，设备已不可达 **无法改回**（已登记 c1 文档遗留第 38 条）。

**⑥ 为什么十例仍全 `unverified`**：契约 `:206` 四类产物本行仍零份；契约 `:232-235`「测试未运行 / 只看截图或只看 UI 状态 → `unverified`」；本次通过的那条断言对象是**成员头像组渲染（`:184-185` 那半句的前半）**，**不是 C1-10 点名的筛选 / 混排 / 不丢数据路径**，且同批 2 条未跑完、非空未验。⇒ **C1-10 不升级**（逐行依据见 `c1-group-chat.md`「C1 真机证据采集第十轮」⑨）。

⚠️ **本批只提交 `docs/`（`c1-group-chat.md` + 本文），未 add / commit / 修改工作区里任何其他未提交改动。**

### ⚠️ 第十八批（两处静默 `close(null)` 修复 + 助手删空崩溃修复，2026-10-06，HEAD `81147639a`）：**都不是 C1 验收项 ⇒ 20 状态格仍 10/10 `unverified`**

⚠️⚠️ **先说性质**：本批 = **4 个真缺陷修复 commit**（`e628d9d82` / `8b05fde70` / `85f708169` / `81147639a`），**零设备、零 `adb`、零仪器测试**；验证全是 JVM 单测（AI 侧用真实本地 HTTP + 真实 `EventSources`，助手侧是纯函数）。⚠️ **都不是 C1 验收项**：C1 走 **chat-completions** 路径，本批动的是 **responses API / Claude** 与**助手删除**，与 C1 十例均无关 ⇒ **20 个状态格一个升级都没有**（仍是 10/10 `unverified`）。

**① 两处「非 2xx 空/乱码 body 被静默当零产出」修复（`e628d9d82` / `8b05fde70`）**：根因在 `common/.../http/okhttp/sse/EventSource.kt:85-99`——非 2xx 时 `KtorEventSource` **固定以 `t = null` 回调** `onFailure`，错误信息只能来自响应体；调用方 `var exception = t` + 仅当 body 非空才解析 ⇒ 非 2xx + 空 body（或乱码 body）时 `close(null)`，`callbackFlow` 当正常完成收场，上游只能报「本轮没有产出内容」。这是 `43d607bdd`（上一批修好的 **chat-completions**）的**同构副本**。改法：内联 listener 机械搬为顶层 `internal fun`（`responseApiStreamListener` / `claudeStreamListener`），兜底行改 `closeFlow(exception ?: HttpException("Failed to get response: HTTP <code> <message>"))`；Claude 额外在 catch 补 `exception = e`。**同构第三处扫描结论：全仓无第三处**（Google / Interactions 已有 `?: Exception(...)` 兜底；`SSE.kt:78` 先发 `SseEvent.Failure` 且三个 TTS 消费方都有兜底）。新增 2 类 × 6 例 = 12（`@Test` 实测 6/6）。

**② 助手删空崩溃修复（`85f708169` production + `81147639a` 测试）**：真机（OnePlus `PKG110` / Android 16）只剩 1 个助手时删除它即崩进 SafeMode。崩溃链：`AssistantViewModel.removeAssistant` 过滤成空列表 → `SettingsRepository.update()` 先赋值 `settingsFlow` 再落盘 → 直接暴露的 `MutableStateFlow` 在 `Main.immediate` 上同步唤醒 `ChatViewModel` 收集器 → `getCurrentAssistant()` 的 `assistants.first()` 抛 `NoSuchElementException`（崩在 persist 之前）。**关键事实**：`SettingsRepository.kt:365` 的 `ifEmpty` 是 `dataStore.data.map{...}` **读时兜底**，空列表**确实会落盘**，保护不了 `settingsFlow` 中间态。方案采用「允许删空 + 立即回落默认」：新增 `normalizeAssistants`（读写路径共用）、`Settings.removeAssistant`，`getCurrentAssistant()` 变全函数。production 2 文件 **+29/−9**；新增 `AssistantRemovalInvariantTest` **6 例**（未直测 VM，测它调用的纯函数/扩展）。

**③ 新基线（改后实测）**：`:app:testDebugUnitTest` **113 类 / 931 例 / 0F0E0S**（原 112/925）；`:ai:test` **30 类 / 220 例 / 0F0E0S**（原 28/208）；lint app `0 / 581 / 6 = 587`（**未变**，未加字符串）、全模块 621（未变）；`c1_doc_stats.py` **OK 18 / WARN 0 / FAIL 0 exit 0**，台账仍 **46 行 / 501 例**（本批新增测试类不在册）；脚本 sha256 `238bb45f…4321` **未改**。

**④ 诚实边界**：真机行为未复测（修复效果零真机证据）；`AssistantViewModel` 未直测、DataStore 落盘与 `Main.immediate` 时序未端到端；Claude 的 `exception = e` 在 JVM 因 `Log.w` 先抛而不可达 ⇒ 靠代码审查。逐条见 `c1-group-chat.md`「生成失败健壮性与助手空列表修复」节 ④。

📍 全部源码行号、测试用例表、SHA-256 与四条限制见 `docs/eval/c1-group-chat.md` 的「生成失败健壮性与助手空列表修复」节；台账第十三批 10 个 commit 已追加（锚点 `1b0e04a9` 不重算，声明值 46 行 / 501 例未动）。

⚠️ **本批只提交 `docs/` 三个文件（`c1-group-chat.md`、`lua-card-development.md`、本文），未 add / commit / 修改工作区里任何其他未提交改动（`TavernMacroExpander.kt` / `architecture-map.md` / `C1GroupUiE2EFixtureTest.kt` 三个在途改动未碰）。**

### ⚠️ 第十九批（全项目门禁首次整项目跑通，零设备，2026-10-06，HEAD `0e312cbb2`）：**`./gradlew --offline assembleDebug test lint` 首次整项目 exit 0；`--rerun` 作用域陷阱；十例仍 0/10**

⚠️⚠️ **先说性质**：本批 = **一次全项目门禁日志（零设备、零 `adb`、零 `androidTest`）**，⚠️ **零源码 / 零测试改动、零新 commit**（HEAD 仍 `0e312cbb2`）。⚠️ **门禁绿 ≠ 用例级证据**：它是**包级 / 项目级门禁证据**，20 个状态格一个判定都没改，仍是 **10/10 `unverified`**。详细证据节见 `docs/eval/c1-group-chat.md`「全项目总闸门（零设备，2026-10-06，HEAD `0e312cbb2`）」；台账见该文件「第十四批」。

**① 命令一（全项目总闸门首跑）**：`nice -n 19 ./gradlew --offline assembleDebug test lint` ⇒ **exit 0**，`BUILD SUCCESSFUL in 44s`，`839 actionable tasks: 173 executed, 666 up-to-date`。日志 `/tmp/opencode/full-gate/run.log`（1120 行，sha256 `b0257e0c…3128`）。此前的验证全是**按模块**跑的，**这是首次整项目一条命令**。

**② 逐模块 test 基线（合计 213 类 / 1584 例 / 0F0E0S，skip 12）**：ai 30/220/0/0/0、app 113/931/0/0/0、card-validator 4/77、common 2/6/…/5、document 1/1、highlight 5/53、khatkit 33/198/…/7、khatkit-ui 2/10、material3 1/1、mediapicker 1/9、oauth 2/2、search 3/12、speech 12/43、web 1/1、workspace 3/20；**lint 全模块 `0 / 614 / 7 = 621`**（app `0 / 581 / 6 = 587`）。

**③ `--rerun` 作用域陷阱（本轮实测踩中）**：`./gradlew --offline :app:testDebugUnitTest :ai:test --rerun` **是空跑**——`--rerun` 是**任务级选项**，只作用于**紧邻其前的那一个任务**；它落在无语义的聚合任务 `:ai:test` 上，`:ai:testDebugUnitTest` 没被强制、`:app:testDebugUnitTest` 根本没绑上 ⇒ `287/287 up-to-date`、一个测试都没跑。**修正写法**（逐个任务各带 `--rerun`）：`nice -n 19 ./gradlew --offline :app:testDebugUnitTest --rerun :ai:testDebugUnitTest --rerun` ⇒ `287 actionable tasks: 2 executed, 285 up-to-date`，两个测试任务的新 XML mtime（19:51:44 / 19:51:32）**均晚于启动时间 19:51:24**，确认真实执行、无红项。

**④ 诚实点 + 限制**：命令一里 `:app:testDebugUnitTest` 与 `:ai:testDebugUnitTest` 是 **UP-TO-DATE**（未重跑），故补跑命令二消除疑点；门禁用 `--offline`，**未做联网依赖解析校验**；`card-validator` 无 lint 报告、`app/baselineprofile` 无单测源，是「无该产物」的**推断**（目录结构 + `NO-SOURCE` / UP-TO-DATE 日志），**非失败**。⚠️ **门禁全绿不等于十例验收达成**——契约 `:206` 四类产物在十行上仍零份，**十例仍 0/10 `verified`**。

**⑤ 设备侧仍待（截至本批）**：
- **`screen_off_timeout` 未还原**：现为 **`600000`（原值 `30000`）**、`stayon true` 未恢复，设备不可达**无法改回**（遗留第 38 条）。
- **UI 两条 androidTest 未跑完**：`C1GroupFilterAndAvatarUiTest` 的 chip 过滤（测 1）与 @ 选择器（测 3）**未跑完**，只有成员头像组（测 2）真机通过。
- **非空验证未做**：三条 UI 断言的「非空」只是**按构造推断**，**三次破坏未执行**。

⚠️ **本批只提交 `docs/` 两个文件（`c1-group-chat.md` + 本文），未 add / commit / 修改工作区里任何其他未提交改动（`TavernMacroExpander.kt` / `architecture-map.md` / `C1GroupUiE2EFixtureTest.kt` 三个在途改动未碰）。**

### ⚠️ 第十二批（HEAD `86e88970d` 那一轮采集，2026-10-06）：**`C1LiveModelSequenceTest` 前 4 条真机全绿——硬理由⑤已消**

⚠️⚠️ **先说清这一批的性质：它是「采集」不是「代码提交」**——
`git diff` **只有 `docs/`**，一行代码都没改。
做的是在**真机（OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`，无线 TLS
serial `adb-3B6F5ME910B6H059-Sqr0AX._adb-tls-connect._tcp`）**上把
`C1LiveModelSequenceTest` **前 4 条各单跑一遍**，并把产物拉回主机。
⚠️ **十行状态列仍是 10/10 `unverified`**——本轮**零 UI 动作、零酒馆动作、零扫码动作**。

**① 构建与跑法（逐条照记）**：

| 项 | 实测值 |
|---|---|
| 构建 | `./gradlew --offline :app:assembleDebug :app:assembleDebugAndroidTest` ⇒ **`BUILD SUCCESSFUL in 5s`**，退出码 **0**（`394 actionable tasks: 12 executed, 382 up-to-date`） |
| ⚠️ **主 APK 是 per-abi 的** | arm64 设备上真实路径 **`app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`**；androidTest 是**无后缀单 APK** |
| 安装 | 两次 `adb install -r -t` 均 **`Success`** |
| 端口转发 | `adb reverse tcp:8765 tcp:8765` 退出码 **0** |
| ⚠️ **未用 `connectedAndroidTest`** | 它**会卸载 app 并删掉外置目录里的证据文件** ⇒ 采集一律手动 `install -r -t` + 手动 `am instrument` |
| mock 服务 | `/tmp/opencode/roundtable-vote/mock_openai_v2.py`。⚠️ **`/tmp/opencode/mock-llm/mock_openai.py` 是 v1、没有 `BALLOT:` 逻辑**，用它会让第 4 条（vote）**按设计失败** |

**② 四条执行结果（退出码 / `OK` 原文 / Time 逐条照抄原始日志）**：

| # | 方法名 | 退出码 | `OK` 原文 | Time | 证据文件 | 字节 | SHA-256 |
|---|---|---:|---|---:|---|---:|---|
| 1 | `pipelineRoundRecordsRealModelSequenceAndRealTokenUsage` | **0** | **`OK (1 test)`** | 2.142 | `c1-live-evidence-main.json` | 5919 | `5641c7f3144dd5bc895a08d0b03ff54a1919039f4e4fd1c40e26b10075a363e4` |
| 2 | `budgetTruncationSkipsRemainingRolesAndRecordsRunLog` | **0** | **`OK (1 test)`** | 1.746 | `c1-live-evidence-budget.json` | 3824 | `890a1f89036092b651cf505d10c722e15443ba1a4440ad627ae2d4026158d46b` |
| 3 | `roundtableRoundRecordsChairSummaryCallSequence` | **0** | **`OK (1 test)`** | 3.896 | `c1-live-evidence-roundtable.json` | 6399 | `49d437f58ffd282f8a855ed18c6ca7af23f8ace9927cf3b28432d412ec8bcda2` |
| 4 | `voteRoundRecordsBallotCallsAndSummarySequence` | **0** | **`OK (1 test)`** | 1.842 | ⚠️ **未 pull** | — | — |

⚠️⚠️⚠️ **第 4 条只有旁证、没有 app 侧主证据**：
有 `am instrument` 原始日志（退出码 `0`、`OK (1 test)`、`Time: 1.842`）
＋ mock 服务端独立记录（`t4-mock-requests.jsonl`，3 条）；
**没有** app 自己写出的 `c1-live-evidence-vote.json`——**本轮没 pull**。
app **未被卸载**，那份 JSON **按理应仍在设备**
`/sdcard/Android/data/heizige.kk.khatkit.debug/files/`，**可补拉**。
⚠️ **旁证 ≠ 主证据**：服务端日志能证明「请求发出去了、服务端按预期回了」，
**不能**证明「app 落库 / 解析 / 多数决出了什么」。
⚠️ 顺带一个**必须说清的口径**：服务端日志里的
`ballot_in_candidates=true` / `parse_ballot_accepted=true`
**是 mock 服务端自己算的**（`mock_openai_v2.py` 解析自己发出的回复文本），
**不是 app 侧的断言结果**——**不能当成 app 解析成功的证据**。

**③ 前 3 条的实际数字**：

- **t1 pipeline**：**3 次真实 HTTP 调用**，wire 模型名依次 `mock-model-a` →
  `mock-model-b` → `mock-model-c`，`stream=true`；usage a `201/34`、b `235/34`、
  c `235/34` ⇒ **`Σ(prompt+completion) = 773`**，落库 `group_runs.spent_tokens = 773`，
  **逐条相等**。✅ **这个 773 与 `c1-group-chat.md` C1-02 早已登记的 `773`
  （及其 `235 + 269 + 269` 逐项拆分）逐字节一致**。
- **t2 预算**：`token_budget_per_round=1` ⇒ **恰好 1 次调用**（服务端独立记录确认只收到
  1 个请求），B/C 被 skip，`msg_count=2`，`status=BUDGET_STOPPED`、
  `spent_tokens=236`、`token_limit=1`、`reason=token_budget_exceeded` ⇒ **截断正确**。
- **t3 roundtable**：**议长 C `prompt_tokens=274` vs A/B 各 `203`** ⇒
  **`chairRound` 的可见性放宽在真实 prompt 字节里可见**；
  `turn_kind` a/b = `speaker`、c = `chair`，`chair_role_id=c`；
  **`Σ = 239 + 239 + 310 = 788`**，落库 `spent_tokens = 788`，**逐条相等**。
  ⚠️ **788 这个数此前没在文档登记过**（旧登记只有 pipeline 773 / budget 236）；
  ⚠️ 且它是 **mock 估算口径**（`ceil(bytes/4)`），**不是真实模型推理**。

**④ wire 级模型名：真机复核到位了一半（硬理由①的进展与它仍不消的理由）**：

- ✅ **t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c`
  这种服务端自报串**，而同行 `model_id` 是**本地 UUID**
  （`0c1c11ae-…000a` 等）；`bindings[].model_string_sent_on_wire` 也逐条记着这三个串
  ⇒ **新增的 wire 优先逻辑在真机上生效了**（反查出来会是本地别名，不会恰好等于 wire 串）。
- ⚠️⚠️ **一处订正，不能照抄「`wire_model_name_provenance` = `wire_response_model`」**：
  t1/t2/t3 这三份 **mock** 证据 JSON 里**根本没有 `provenance` 字段**
  （`grep -c provenance` = **0**）——该字段**只有真实网关那份证据的写出器才落**
  （`C1LiveModelSequenceTest.kt:1933`），mock 两个写出器（`:1325` / `:1540`）
  **只落 `wire_model_name`**。⇒ **上一批 ⑧ 那条判据只对真实网关那份 JSON 适用**。
- ⚠️ **所以硬理由①仍不消**：硬理由①里「仪器测试一行都没跑过」「设备上那份证据仍是
  反查值」这两条子理由**在 mock 下已消**，但**真实网关那次
  `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，
  设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**。

**⑤ 这一批消掉了什么 / 没消掉什么**：

| 硬理由 | 本批之后 |
|---|---|
| ⑤ `C1LiveModelSequenceTest` 其余 4 条未全绿 | ✅ **已消**（⚠️ 第 4 条只有旁证） |
| ① wire 优先逻辑未在真机复核 | ⚠️ **仍不消**：mock 下已复核，**真实网关下未复核** |
| ② 真机 UI 端到端 | ❌ **零份不变** |
| ③ 酒馆本体 | ❌ **零份不变** |
| ④ 相机扫码 | ❌ **零份不变** |

⚠️⚠️ **这四条跑的是 mock provider，不能替代真实网关那一轮**：
「`C1LiveModelSequenceTest` 5 条都在真机绿过一次」**成立**；
「10 行里有 10 行的路径被真机调用过」**不成立**。
⚠️ **本批对台账声明值影响为零**：`45 类 / 491 例` 与锚点 `78 / 0 merges` **一个数都没动**
（`c1_doc_stats.py` 本轮实测 `ledger` 仍 `45 行全部相等` / 合计 **491**）。
⚠️ **工作区那两处不属于任何 agent 的未提交改动（`TavernMacroExpander.kt` modified、
`C1GroupUiE2EFixtureTest.kt` untracked）全程未 add、未 commit、未修改。**
⚠️ **下一位两件事**：① 把第 4 条的 `c1-live-evidence-vote.json` 从设备补拉回来；
② 真机重跑 `realProviderRoundRecordsGenuineTokenUsage`（真实网关），
把 `wire_model_name_provenance` 拉回来确认取值。


### ⚠️ 第二十批（真机第十一轮 + 第十二轮，2026-10-06，HEAD `cddaa9959`）：**真实网关 4 条新用例 + pipeline 复跑 + 两条 UI 首次通过；C1-09 真机导出双哈希 + 四项安全检查；C1-06 取消三次尝试全部不成立；20 状态格仍 10/10 `unverified`**

⚠️⚠️ **先说性质**：本批 = **两个真机采集窗口**（设备 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`，无线 serial `192.168.31.183:34753`——执行者报告）。第十一轮 HEAD `a8302e463`：4 条真实网关用例 + 1 条 pipeline 复跑 + 2 条 UI 测试；第十二轮 HEAD 收尾 `cddaa9959`：C1-09 真机导出 + C1-06 取消。**生产代码零改动**（窗口内 5 个测试/工具 commit 全部只动 `app/src/androidTest` 与 `tools/verification`）。⚠️ **20 个状态格一个判定都没改，仍是 10/10 `unverified`**——详细逐行依据见 `c1-group-chat.md`「C1 真机证据采集第十一轮」⑨ 与「第十二轮」②；台账见该文件「第十五批」。⚠️ **所有通过项均为单次通过，未做重复稳定性验证。**

**① 第十一轮：4 条真实网关用例 + pipeline 复跑全部 `OK (1 test)`（产物已 pull、SHA-256 登记代理复算一致）**

- **C1-01 显式 @**：真网关 `mode=pipeline`，触发 `@角色乙 请只由你发言一次。` → `mentionRoleIds=[b]`、plan 只留 b、**只调用 b**（wire `glm-5.2`）；b usage `6546+54=6600`=落库 `spent_tokens`；viewer 台账 a/b/c 均为真库 UUID。⚠️ 触发是 **USER 消息**、生产口径下 A/C 仍可见该条（测试把「A/C 看不到 b 的本轮输出」与「A/C 仍可见触发 USER 消息」两条都写成断言）⇒「只被 @ 角色收到该消息」的字面那半仍未观测到（遗留第 15 条不变）。
- **C1-02 pipeline 复跑（第 4 次尝试）**：wire 级序列 `deepseek-v4-flash → glm-5.2 → deepseek-v4-flash`（测试 `:1569-1573` **断言级**）、provenance `{wire_response_model:3, fallback:0}`、usage `6829/136 + 6680/42 + 6876/57`、**Σ=20620=spent_tokens**、viewer 序列断言级（`c` 不见 `a`）。前三次失败原文全部登记：① OPPO osense SIGKILL（`Cached(nirvana)`，`Process crashed`）② 角色 c modelId `null`（raw `6997cfa5…`，已核实无冻结断档）③ 模型行为偏差（c 复读 `ROLECODE:B`，raw `df882218…`）。
- **C1-03 roundtable**：重跑 `OK (1 test)`（首跑 FAIL：c 被记为 `turn_kind=error`，raw `00ce40af…`，已核实无冻结断档）；序列 a/b speaker → c chair、Σ=20723=spent、`c(chairRound=true)` 可见全员、a/b 互相不可见（断言级）。
- **C1-04 vote**：**两条路径都真机跑通**——多数决（`Decided / opt-a 2:1`、`__summary__` 无 usage、Σ=20342=spent）与平票失败（`Tie` → `status=FAILED / reason=vote_no_decision`、error 节点、不写 `vote_summary`、Σ=20361=spent）；两跑 provenance `{3,0}`。
- **两条 UI 测试首次真机通过**：`typeFilterChipsFilterWithoutLosingData`（Time 4.026；单聊过滤下群条目不可达、切回后 4 条 DB 行原样）与 `atMentionPickerInsertsParseableMention`（Time 4.365；`@Alice Johnson`→`[alice]`、`@阿尔法`→`[alpha]`、反伪 `@johnson`→`[]`）；成员头像组未重跑（复用第十轮证据）。
- **设备环境坑（新）**：① ColorOS 会冻结后台 instrumentation（uid 级 cgroup，`am unfreeze` 无效），**测试启动约 20s 后主动 `am start RouteActivity` 前台化可解冻**；② OPPO osense 会 SIGKILL 无可见 UI 的 FGS instrumentation（`Cached(nirvana)`）。设备卫生收尾已改回 `screen_off_timeout=30000` + `stayon false`（执行者报告）。
- **为什么仍全 `unverified`**：C1-02/03/04 的「序列对照 / viewer 断言」已补成断言级，**但契约 `:206` 五要素仍缺「导出哈希」一类、`am instrument` 退出码未落盘（`:232-235` 要求「测试命令及退出码」）、且单次通过未做重复稳定性**；C1-01 差「@ 角色发言」观测；C1-05/07/08 未跑；C1-10 受 `:206`/`:232-235`「不得仅凭 UI 截图勾选 / 只看 UI 状态标记 `unverified`」约束且四类产物仍零份。

**② 第十二轮阶段 A：C1-09 真机导出 ✅ 完成（但 C1-09 仍 `unverified`）**

- `C1GroupExportDeviceEvidenceTest#deviceExportedGroupJsonlHashesMatchAndCarryNoSecrets`：`Time: 0.382` / `OK (1 test)`；走**生产** `TavernChatCodec.exportGroupJsonl` + 生产 IO 助手 `writeExportTempFile`（`ConversationExport.kt:836`，与 `GroupExportCard.kt:201` 同一份）落盘。
- **双哈希一致**：2938 B；设备侧 `sha256sum` = 本机 pull 后 `sha256sum` = `4945b85426def123b7bc07382dbf03977c35908650a114ffac7cf782f9976f4b`。
- **四项安全检查全 `true`**（金丝雀先证实真的落进真机 store）：API key / 隐私记忆 / 工具授权 token 均不入包 + 生产黑名单逐行扫描 37 个禁止键 0 命中；带 Tool/Reasoning part 的消息导出 `mes` 只剩 Text。
- ⚠️ **未覆盖**：`ACTION_SEND` 分享面板 / SillyTavern 本体打开 / 相机扫码 / QR 与导入往返 ⇒ **C1-09 不升级**。

**③ 第十二轮阶段 B：C1-06 取消 ❌ 三次尝试全部不成立（零测量）**

- 生产取消入口 = `ChatManager.stopGeneration(conversationId: Uuid)`（`ChatManager.kt:2277`，未改生产代码）；新测试 `C1GroupCancelDeviceTest` + mock `tools/verification/mock_openai_slow_cancel.py`（sha256 `0f2af473…3fde`）。
- attempt 1：测试 7.903s 后在第 1 轮错误节点断言失败；**异常现象**：mock 对 B 注入 HTTP 500（服务端日志可证）但整轮仍 `COMPLETED`、B 是 speaker 消息且正文 `"B 已收到，本轮取消用例正常执行，无异常。"` 在仓库 / 两份 mock 日志 / 生产库快照 / attempt 2 遗留库**四处均 0 命中**，mock 未收到第二次 B 请求——**原因未定，已登记 c1 文档遗留第 40 条**。attempt 2 设备中途掉线、attempt 3 设备在 `am instrument` 生效前掉线。
- ⚠️ **四项断言（空气泡 / 已生成消息 / 错误节点 / spent / status / reason）一条实测值都没有**；超时半（15 分钟）未跑；阶段 C 稳定性复跑因设备丢失未执行。

**④ 设备遗留（下一轮接上设备第一件事）**

`screen_off_timeout` 现为 **600000**（第十一轮曾恢复 30000，第十二轮又调高且 transport 丢失未改回）、`stayon true`；设备 app databases 里 `c1-cancel-evidence.db` 未删。⇒ **先 `settings put system screen_off_timeout 30000` + 复验、`svc power stayon false`、清该库**（c1 文档遗留第 39 条）。

**⑤ 统计口径（登记代理本机实测）**

`git log --oneline 0e312cbb2..HEAD` = **8 个 commit**（`b20de6805` / `46ba5c8a7` / `129403b38` / `a8302e463` / `92af89247` / `d6870f977` / `579dc193a` / `cddaa9959`）；`git log --name-only 0e312cbb2..cddaa9959 -- 'app/src/test/*'` **空** ⇒ 台账声明值仍 **46 行 / 501 例**；`c1_doc_stats.py` 仍 **OK / exit 0**。锚点 `1b0e04a9` 不重算。

📍 完整数字、SHA-256、逐字段值与限制见 `docs/eval/c1-group-chat.md`「C1 真机证据采集第十一轮」「C1 真机证据采集第十二轮」；逐行判定见第十一轮⑨。

⚠️ **本批只提交 `docs/` 两个文件（`c1-group-chat.md` + 本文），未 add / commit / 修改工作区里任何其他未提交改动（`TavernMacroExpander.kt` / `architecture-map.md` / `C1GroupUiE2EFixtureTest.kt` 三个在途改动未碰）。**

### ⚠️ 第二十一批（真机第十三轮：C1-06 取消首次真机通过 + 真实网关 5 条×3 稳定性复跑 + 两项关键发现，2026-10-06，HEAD `18d730465`）：**20 状态格仍 10/10 `unverified`；设备清理已完成**

⚠️⚠️ **先说性质**：本批 = **一次真机采集窗口**（设备同前：OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`，无线 serial `192.168.31.183:39345`），开工 HEAD `cddaa9959`、收尾 HEAD `18d730465`，窗口内 2 个 commit（`610edbf86` mock 工具 + `18d730465` 取消测试），**生产代码零改动**（登记代理逐 commit `git show --stat` 核过）。另补登上一批（第十五批）两个登记本体 `e9091ffec` / `bfe1c982c`（均只改 `docs/`）。

**① C1-06 取消路径首次真机通过**（`C1GroupCancelDeviceTest#cancelMidStreamKeepsGeneratedMessagesAndErrorNodeWithoutEmptyBubbles`，Time 2.246 / `OK (1 test)`）：空气泡 0；第二轮 a 完整保留（id `95f302c5…`，usage 6236+22=6258）；被取消 b 的半截产出保留（id `5f97ad23…`，恰 24 字符 = 1 chunk，其余 18 块丢失）；第一轮错误节点保留（id `7e91c213…`，`turn_kind=error`）；`group_runs=CANCELLED / cancelled / spent=6258 / committed=[a]`；`b2.isNotEmpty()` 强断言通过（旧断言已证为空断言）。⚠️ **仍缺**：超时半（15 分钟）未跑、各 viewer 可见集合未按契约落盘、导出哈希一格、单次通过 ⇒ **不升级**。证据 SHA（登记代理复算）：report `c5225560…`、raw-final `d70c6379…`、raw-round1 `4825b59a…`、instrument log `e7169883…`、trace `aa773b64…`、exitcode `1f2d78ea…`。

**② 根因（解释前三次为何全不成立）**：① 测试写 `providers=[mock]` 被 `SettingsRepository.kt:349-352` 补回全部 `DEFAULT_PROVIDERS`；mock HTTP 500 的错误消息含 `"500"` ⇒ `ProviderFailover.isEligible` 判可切换 + `enableAutoRetry=true` ⇒ B 请求被 **failover 重放给内置「极客猫」真网关**（这正是第十二轮遗留第 40 条「文本四处 0 命中」的答案）；② 第二轮只等 a 落库就取消 ⇒ B 未必已开始流式 / `jobs.isEmpty()` 分支只写状态没真取消 / `commitGroupTurn` 先存消息后写 run；③ 旧断言按 `roundId+roleId=="b"` 过滤，而被取消角色永不盖章 ⇒ 恒空断言；④ mock `failed_once` 锁外 check-then-add、无 reset、日志不记实际 status/body、「24-byte」实为字符、「零内容」实际发 `{"content":""}`。修复见 `610edbf86` / `18d730465`；mock 新 sha256 `0f2af473…`（旧 `8af6e13d…` 已订正）。JVM 判据级证明（登记代理亲自重跑，用真实 `UIMessage` 类，`FILTER-PROOF OK` / exit 0）：旧过滤在该构造列表里命中的是**真网关顶包回复**，新过滤命中半截产出；`old_assert_holds_vacuously=true`。⚠️ 这是 JVM 侧证明，**不是** androidTest 真机执行。

**③ 真实网关 5 条×3 稳定性**：mention 3/3、vote 3/3、vote-tie 3/3、roundtable 1/3（另 2 次为**测试自身竞态**——议长消息「先落库后盖章」被快照到 null；同轮 raw dump 显示产品侧 `run=COMPLETED / committed=[a,b,c]`，修复中）、pipeline 0/3 + 补测 1/3（角色 a/c 间歇零内容）。通过项 SHA（复算）：`d9473daf…` / `bb4007c4…` / `62c777e3…` / `dffe2f8a…` / `5c17960a…` / `13144d44…` / `3421fff8…` / `a7a20299…` / `e4d67be6…` / `ff9139f6…` / `f98f053d…`。

**④ 两项关键发现**：① **`INSTRUMENT_EXIT` 不能当判据**——全部 19 个 run（含 FAIL）设备侧均 0，且 `INSTRUMENTATION_CODE: -1` 在通过/失败日志里完全相同；判据必须读日志 `OK (1 test)` / `FAILURES!!!`（影响全项目取证方法，c1 文档遗留第 41 条）；② **前台化时机**——启动后 ~20s 才 `am start` 的 3 次 pipeline 全灭；改成 4s 即前台化 + 每 10s 反复后产出恢复（⚠️ **执行者推断，非受控实验**）。独立对账：网关与设备侧 curl 均正常（`device-sse-full2.txt` 有完整 content 帧）⇒ pipeline 零内容为 app 流式路径间歇行为。

**⑤ 设备清理已完成**：`c1-cancel-evidence.db` 不存在；`rikka_hub` RUNNING 两行已清（备份三件套 SHA `c33e8dae…` / `a7809e3b…` / `15018d54…`，回推后设备库 `40d2eb76…`、7→5 行、RUNNING=0，登记代理对 pull 副本逐行复核）；`screen_off_timeout` 改回 30000、`stayon=false`、`adb reverse --remove-all`、8766 mock 已停。⚠️ 登记代理读备份时触发过一次 SQLite 隐式 WAL 恢复（逻辑不变、备份三件套文件形态与 SHA 改变，MANIFEST.sha256 记的是原值）——如实登记。

**⑥ 统计口径（登记代理本机实测）**：`git log --oneline bfe1c982c..18d730465` = **2**（`610edbf86` / `18d730465`）；`git log --name-only bfe1c982c..18d730465 -- 'app/src/test/*'` **空** ⇒ 台账声明值仍 **46 行 / 501 例**；`c1_doc_stats.py` 三条命令（正文 / `--self-test` / `--only tables`）退出码均 0、18 条全 OK、表格列数不符 0 行。锚点 `1b0e04a9` 不重算。

**⑦ 20 状态格一个升级都没有，仍 10/10 `unverified`**——最接近的两行：C1-04（两条 vote 路径 3/3，仅缺「导出哈希」一格）与 C1-06（取消半完整，超时半 / viewer 集合 / 重复性仍缺）。逐行依据见 `c1-group-chat.md`「C1 真机证据采集第十三轮」⑤；新增遗留（退出码判据 / roundtable 测试竞态 / C1-05 结构性不可达 / provider 只能追加）见该文件第四十一~四十四条。

⚠️ **本批只提交 `docs/` 两个文件（`c1-group-chat.md` + 本文），未 add / commit / 修改工作区里任何其他未提交改动（`TavernMacroExpander.kt` / `architecture-map.md` / `C1GroupUiE2EFixtureTest.kt` 三个在途改动未碰）。**

### ⚠️ 第二十二批（真机第十四轮，2026-10-06，HEAD `b60aa80c5`）：**5 条带完整证据通过（显式@/vote 多数决/vote 平票/预算截断/记忆隔离）；roundtable/pipeline 第 3 位角色零产出 6/6；C1-04 与 C1-05 首次升 `verified`（本文件首次出现状态升级）**

⚠️⚠️ **先说性质**：本批 = **一次真机采集窗口**（设备同前：OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`），开工与收尾 HEAD 均为 **`b60aa80c5`**；窗口内 3 个 commit（`a9df57c96` / `b1473f5d1` / `b60aa80c5`）全部只动 `app/src/androidTest/.../C1LiveModelSequenceTest.kt`，**生产代码零改动**（逐 commit `git show --stat` 核过）。另补登上一批（第十六批）两个登记本体 `165d30b5a` / `a8449768c`（均只改 `docs/`）。

**① 5 条带完整证据通过（8 项契约字段齐备）**

- **C1-01 显式 @**：`OK (1 test)` / Time 6.567；只调用 b（wire `glm-5.2` / `provenance=wire_response_model`）、usage `6546+98=6644`=spent、viewer 台账 a/c 仅见 USER、b 见 USER+自己回复、导出 `6e2ec8ce…`（1747 B/3 行）。⇒ **不升**：契约 `:201`「其他角色不可见」的字面半仍未观测（触发是 USER 消息，A/C 仍可见它，遗留第 15 条）。
- **C1-04 vote**：**两条路径都通过**——多数决（`Decided/opt-a 2:1`、Σ=20368=spent、导出 `8e42c5b9…` 2672 B/6 行；Time 17.917）与平票（`Tie → FAILED/vote_no_decision`、错误节点 `__summary__/error`、Σ=20302=spent、导出 `65f76608…` 2695 B/6 行；Time 16.506）；viewer 三角色各见「自己 + `__summary__`」。⇒ **升 `verified`**（契约 8 项齐备；「导出哈希结构性不产出」旧结论被本批推翻）。
- **C1-05 预算截断**：专用用例 `realProviderBudgetTruncationRecordsRunLogAndExport` 通过（Time 17.793）；`BUDGET_STOPPED/token_budget_exceeded/spent=13594/token_limit=9000/committed=[a,b]/skipped=[c]`、序列 a deepseek 6934 / b glm 6660、Σ=13594=spent、**库内 `messages.count=3`/`assistant_role_order=["a","b"]` 直读**、导出 `da0f81c5…` 2044 B/4 行。⇒ **升 `verified`**（遗留第 43 条已消）。
- **C1-08 记忆隔离**：`realProviderGroupMemoryIsolationRecordsPerSpaceHits` 通过（Time 0.647）：三空间 `group:<conv>:role:a|b|c` 各命中 1 条 + `role_id` + `source_message_id`（`…a008`/`…b008`/`…c008`）、外来查询全 `[0,0]`、viewer 重过滤 a 的记忆对 b 可见 **0**、全局/助手空间金丝雀命中 0。⇒ **不升**：**契约 8 项缺 3 项**（模型调用序列 / token / 导出哈希）——本例 `evidence_kind=…no-gateway`，**设计上不走网关、不产出群导出**。

**② 2 条 3/3 全失败（同一根因，六条结论）**

- roundtable 3 跑（Time 301.583/300.969/301.756）+ pipeline 3 跑（Time 27.627/25.884/19.129）**全失败**：第 3 位角色 c 零产出（`turn_kind=error` / 正文「本轮没有产出内容」/ `model_id=null` / usage 全 -1 / `group_run=FAILED/role_failed/committed=[a,b]` / `app_errors=[]`）。只有 raw dump（`10d7ae2f…` / `64e84d65…`），**通过证据文件不存在**（语义是「全断言通过才写」）。
- **六条新结论**：① **不是** `43d607bdd` 修的「非 2xx + 空 body 静默当零产出」（否则错误正文是 `HttpException:` 且 `app_errors` 非空）；② 真因 = **网关 HTTP 200 但 SSE 无 content delta**，`ConversationSession.finishGeneration`（`:97-116`）丢空占位 → `stampGroupTurn`（`ChatManager.kt:1980-2003`）返回 null → `commitGroupTurn`（`:1957`）写 error 节点 + `FAILED/role_failed`，**全程无异常**；③ **6/6 稳定复现 ⇒ 不是偶发**，**推翻** `c1-group-chat.md:4114` 与判定规则第十条的「偶发」结论（已就地加订正块、保留原文）；④ **`app_errors` 有采集盲区**（零产出不写 / 超时 `:1020` rethrow / 取消 `:317` 早退都不写）⇒ 不能拿 `app_errors=[]` 当「一切正常」；⑤ refute 掉「c 的 prompt 很长所以失败」（c 在 pipeline 的 `prompt_tokens` 从未落库；vote 里 c 同模型第 3 次调用成功）；⑥ `reasoning-only` 响应会被保留并盖章成 speaker ⇒ c 连 reasoning 都没有。

**③ 判定影响（本文件首次出现状态升级）**

20 格中 **4 格升 `verified`**：**C1-04** 与 **C1-05**，各自在用例矩阵表 + 证据登记表两个格子改值；其余 16 格保持 `unverified`。详见 `c1-group-chat.md`「C1 真机证据采集第十四轮」⑤/⑦ 与 20 格订正。⚠️ **这是「C1 群聊」从全部 `unverified` 走向部分 `verified` 的第一步**——升的两行均为**后台轮次生成**路径（vote / budget），**不涉及**真机 UI / 酒馆本体 / 相机扫码三项硬阻塞。

**④ 统计口径（登记子代理本机实测）**：`git log --oneline 18d730465..HEAD` = **5**（`165d30b5a` / `a8449768c` / `a9df57c96` / `b1473f5d1` / `b60aa80c5`）；`git log --name-only 18d730465..HEAD -- 'app/src/test/*'` **空** ⇒ 台账声明值仍 **46 行 / 501 例**；锚点 `1b0e04a9` 不重算。新增遗留：`c1-group-chat.md` 第 45 条（第 3 位角色零产出 6/6）与第 46 条（`app_errors` 采集盲区）；第 43 条已消；第 40 条与本批**不同源**（夹具 vs 生产）。

📍 完整数字、SHA-256、逐字段值与限制见 `docs/eval/c1-group-chat.md`「C1 真机证据采集第十四轮」。

⚠️ **本批只提交 `docs/` 两个文件（`c1-group-chat.md` + 本文），未 add / commit / 修改工作区里任何其他未提交改动（`TavernMacroExpander.kt` / `architecture-map.md` / `docs/upstream-sync-2026-10-03.md` / `docs/uiredesign.md` / `C1GroupUiE2EFixtureTest.kt` 等在途改动未碰）。**

### ⚠️ 第二十三批（零设备，2026-10-07，HEAD `d6494e49a`）：**群聊失败续跑入口落地（生产代码）+ C1-07/C1-10 设备侧测试 + C1-06 证据字段补齐；20 状态格全不升；台账 46 行 / 501 例 → 47 行 / 509 例**

⚠️⚠️ **先说性质**：本批 = **一次纯零设备窗口**（**零 `adb`、零仪器执行、零真机证据**），开工与收尾 HEAD 均为 **`d6494e49a`**（`git rev-parse HEAD` 实测）。窗口 `b60aa80c5..d6494e49a` **实测 10 个 commit**（`git log --oneline b60aa80c5..HEAD`）：4 个主角（`34145bf49` / `1c48b1701` / `9d7429216` / `d6494e49a`）+ 6 个补登的第十七批登记本体（`07cf703ce` / `5f9d92269` / `cdf88856a` / `42a0f29ae` / `5c3086882` / `c3e921a5c`，均只改 `docs/`）。⚠️ **20 状态格一个判定都没改**：C1-04 / C1-05 仍 `verified`，其余 18 格仍 `unverified`——逐格依据见 `docs/eval/c1-group-chat.md`「第十五轮（零设备）」⑦。

**① `d6494e49a` = 生产代码变更：群聊「失败续跑」入口（`app/src/main` 5 files / +329）**

新增纯判定 `GroupRetryEntry.canResume(isGroup, isLastMessage, message)`（`app/src/main/java/heizige/kk/khatkit/app/feature/chat/GroupRetryEntry.kt:61-69`）= 群聊 && 最后一个节点 && `MessageRole.ASSISTANT` && `turnKind == GroupChat.TURN_ERROR` && `roleId != GroupChat.SUMMARY_ID`；接通 `ChatList.kt` → `ChatMessageActions.kt:155-169`（按钮）→ `ChatMessage.kt`（透传）；**点击仍落到同一个 `ChatManager.regenerateAtMessage`，未改 `ChatManager.kt`、未发明新机制、未触碰任何 `if (!groupChat)` 门禁**。它填的是契约 `:204` / `:227-228` / `:201` 承诺的「重试可从失败角色续跑」。⚠️ **真机行为零份**（本轮零设备）。

**② 「账面错位」机制（本批最重要的技术发现，逐条核过行号）**

`regenerateAtMessage` 对助手消息走 `handleMessageComplete(..., messageRange = 0..<nodeIndex)`（`ChatManager.kt:638`），产出经 `Conversation.updateCurrentMessages`（`Conversation.kt:74-106`）落在**被点消息所在的 `MessageNode`** 里并切 `selectIndex`（`:82-92`）⇒ ① 被点角色**已提交的发言被顶掉**；② `regenerateAtMessage` **从不调用 `updateCommittedRoles`**（全仓唯一调用点 `ChatManager.kt:1903`，在群聊提交路的 `persistRoundState` 内）⇒ `group_runs.committed_role_ids` 仍声称该角色已提交；③ `pendingSpeakers` 按 committed 过滤（`GroupChat.kt:902-903`）⇒ 该角色**永久被跳过**，而 `roundOutputPresent` 是 `any` 判定（`ChatManager.kt:2361-2373`）**抓不到「个别角色产物被顶掉」**；④ 若该轮已 `COMPLETED`，`claimRound` 返回 `Rejected(ALREADY_COMPLETED)`（`GroupTurnCoordinator.kt:261-263`）⇒ **静默 no-op**；⑤ 群聊分支选择器也被关（`ChatMessageActions.kt:244 if (!groupChat)`）⇒ 用户无法切回被顶掉的候选。**所以「重新生成」在群聊下仍整条关闭**，新入口只是「收窄到失败角色错误节点」的续跑。

**③ 已知限制**：取消（`cancelRound`，只写运行日志、无错误节点，`GroupTurnCoordinator.kt:485-491`）与预算中止（`BUDGET_STOPPED`，无错误节点）的轮次**无续跑入口**——不是「失败角色错误节点」形状，其续跑需 UI 读 `group_runs` 状态，属另一子包（`c1-group-chat.md` 遗留第 48 条）。

**④ C1-07 / C1-10 设备侧测试新增但未真机跑**：`34145bf49` 新增 `C1GroupRetryResumeDeviceTest.kt`（968 行；其 KDoc 自述「群聊 UI 目前没有重试入口」在 `d6494e49a` 之后**已过期**——入口是后一笔补的）；`1c48b1701` 新增 `C1GroupPagingAndFilterDeviceTest.kt`（813 行 / 8 `@Test`）。二者均**只验证到编译 + 结构 + 纯逻辑 JVM 探针，真机结果未知**。

**⑤ C1-06 证据字段补齐（无实际值）+ 超时半做不到**：`9d7429216` 给取消用例补 `viewer_visibility`（a/b/c）/ `actual_model_call_sequence[]` / `export_sha256` 等字段，**下次真机跑才产实际值**；**超时半结构性做不到**——`GROUP_ROUND_STEP_TIMEOUT_MS`（`ChatManager.kt:116`）不可注入、`withTimeout`（`:1005`），测试构造的 `AppScope`（`KhatKitApp.kt:344-352`）**无参 concrete class** 且固定绑 `Dispatchers.Main`，虚拟时间路径须改生产、本轮禁止（`timeout_half_verified=false` 是诚实缺项）。

**⑥ C1-08 缺项评估 = 不产出**：模型调用序列 / token 若要拿须**自造记录型 Provider 拦截**（`MemoryExtractor.extractFromTurn` 调 provider 但丢 usage，只返回写入条数，`MemoryExtractor.kt:99-135`）⇒ 会把确定性隔离用例改成联网/注入型、**语义正交**；导出哈希对记忆隔离**不适用**（导出属 C1-09）。

**⑦ 统计口径（登记子代理本机实测）**：`git log --oneline b60aa80c5..HEAD` = **10**、`--merges` = **0**；`git log --name-only b60aa80c5..HEAD -- 'app/src/test/*'` = **只有 `.../feature/chat/GroupRetryEntryTest.kt`** ⇒ 台账声明值 **46 行 / 501 例 → 47 行 / 509 例**（`+1 类 / +8 例`，XML `tests="8"`）。新基线：`:app:testDebugUnitTest` **114 类 / 939 例 / 0F0E0S**；`:ai:test` **30 类 / 220 例**；lint app `0 / 581 / 6 = 587`、全模块 `0 / 614 / 7 = 621`；仪器 `@Test` **20 文件 / 84 例**（untracked 1 文件 / 3 例：`C1GroupUiE2EFixtureTest.kt`）。`c1_doc_stats.py` 主命令 **18 OK / 0 WARN / 0 FAIL**（`--self-test` / `--only tables` 均 exit 0）。⚠️ `c1_doc_stats.py` sha256 前后**同为 `238bb45f7035852acf3f27013bd5d3d6372191a4543d3297ef7044e3899f4321`**（一个字节未改）；锚点 `1b0e04a9` 不重算。

📍 完整逐格判定、逐笔 `--stat`、验证偏差纠正（脚本 ledger 不检查漏列）与诚实限制见 `docs/eval/c1-group-chat.md`「第十五轮（零设备）」+「第十八批」。

⚠️ **本批只提交 `docs/` 两个文件（`c1-group-chat.md` + 本文），未 add / commit / 修改工作区里任何其他未提交改动（`TavernMacroExpander.kt` / `architecture-map.md` / `docs/upstream-sync-2026-10-03.md` / `docs/uiredesign.md` / `C1GroupUiE2EFixtureTest.kt` 等在途改动未碰）。**

### ⚠️ 第二十四批（真机第十五轮，2026-10-07，HEAD `37630adb4`）：**pipeline/roundtable 首次即通过（C1-02/C1-03 各 2 格升 `verified`，本文件第二次状态升级）；C1-06 取消半补齐 8 项但超时半做不到；C1-07/C1-10 暴露两个测试侧缺陷；零产出改为间歇性；台账 47 行 / 509 例未动**

⚠️⚠️ **先说性质**：本批 = **一次真机采集窗口**（设备同前：OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`），开工与收尾 HEAD 均为 **`37630adb4`**；**设备轮本身零 commit**（`git log --oneline 37630adb4..HEAD` 实测为空）。构建 `:app:assembleDebug :app:assembleDebugAndroidTest` **exit 0**、两个 APK `install -r -t` 均 `Success`。⚠️ `am instrument` 的 shell 退出码即使失败也是 0，判据只用日志正文 `OK (1 test)` / `FAILURES!!!`（遗留第 41 条）。⚠️ 本节标题编号按文档既有「真机证据采集」序列（主管简报写作「第十轮」，但该序列已排到第十四轮，故订正为第十五轮）。全部原始证据在 `/tmp/opencode/c1-verify/`（仓库外）。

**① 三条首次真机通过（契约 8 项齐备）**

- **C1-02 pipeline**（`realProviderRoundRecordsGenuineTokenUsage`，`OK (1 test)` / Time 15.294）：序列 a `deepseek-v4-flash 6829+65` / b `glm-5.2 6609+54` / c `deepseek-v4-flash 6891+55`，`provenance={wire_response_model:3, fallback:0}`，`Σ=20503==spent_tokens`，viewer a/b/c 台账齐全，导出 `5f7914d1…` 2357 B / 5 行。⇒ **升 `verified`**（矩阵格 + 证据表格各 1 格）。
- **C1-03 roundtable**（`realProviderRoundtableRecordsChairSummaryCallSequence`，`OK (1 test)` / Time 22.201）：`speaker_order_and_turn_kind={a:speaker,b:speaker,c:chair}`，seq3 c（chair）`deepseek-v4-flash 7011+146`，`Σ=20707==spent_tokens`，议长 c 的 `chair_round=true` 见 4 条、a/b 各 2 条（a/b 未见他人未完成输出），导出 `75ca8ea9…` 2371 B / 5 行。⇒ **升 `verified`**（矩阵格 + 证据表格各 1 格）。
- **C1-06 取消半**（`C1GroupCancelDeviceTest`，`OK (1 test)` / Time 4.292）：viewer a/b/c 台账 + `actual_model_call_sequence` 4 条全 mock + `export_sha256=853033a8…` 2977 B / 7 行；`empty_bubble_count=0`、第一轮错误节点保留、`new_error_nodes_in_round2=0`、b 半截 1 条保留；`round2_b_stream_observed→stop_called=3ms`、`stop_call_duration=33ms`。⚠️ **超时半结构性做不到**（`ChatManager.kt:116` `private const` + `:1005` `withTimeout`、`KhatKitApp.kt:344-352` 无参 `AppScope`，`timeout_half_verified=false`），且单次通过 ⇒ **不升**。

**② 两条失败（测试侧现状，待另一任务判定 / 修复）**

- **C1-07**（`C1GroupRetryResumeDeviceTest`）：**2/2 稳定 `FAILURES!!!`**，失败在 `C1GroupRetryResumeDeviceTest.kt:378`「阶段 1 的 b 错误节点必须原样保留」；phase1 `FAILED/committed=[a]/skipped=[c]/spent=1741/run_token 604b0069-…`，phase2 `COMPLETED/committed=[a,b,c]/spent=14819/同 run_token/同 started_at`、a 消息 id 不变、b/c 各 1 条、`spent==Σusage`。**待判定测试期望错 vs 生产缺陷** ⇒ C1-07 保持 `unverified`。
- **C1-10**（`C1GroupPagingAndFilterDeviceTest`）：8 方法 **3 通过 / 5 失败**，失败均 `pagingSource_*`（`:357` 15≠20、`:392` 55≠60、`:435` 25≠40、`:493` 55≠60、`:527` 25≠40）；真机 DB **55 行**（GROUP=25 / DIRECT=30），生产 `PAGE_SIZE=20` / `INITIAL_LOAD_SIZE=40` 但累计取回 **60 / 40** ⇒ 重叠 / 超取。**待修** ⇒ C1-10 保持 `unverified`。

**③ 零产出探针未复现 ⇒ 改「间歇性」**

`C1GroupZeroOutputProbeTest#pipelineThirdSpeakerZeroOutputProbe` `OK (1 test)` / Time 14.486，**`diagnosis=null`**；`group_run=COMPLETED/committed=[a,b,c]`、`error_node_role_id=null`、`spent=20750`、`sampler_poll_count=130`、`sampler_transition_count=12`、`placeholders_observed_count=3`、`vanished_unstamped_count=0`，三个占位全部「盖章 + 有正文」。⇒ 遗留第 45 条从「**6/6 稳定复现**」就地订正为「**间歇性、非 100% 必现**」（第十四轮曾 6/6 复现，本轮未复现；触发条件同源 = 网关 HTTP 200 空内容；「有没有 HTTP 状态码 / 响应体」那半仍零份）。

**④ 判定影响（20 格逐格）**

20 格中 **4 格升 `verified`**：**C1-02** 与 **C1-03**，各在**用例矩阵表**与**证据登记表**两个格子改值；其余 **14 格保留原文 + 就地追加「真机第十五轮订正」**——C1-06（超时半做不到、单次通过）/ C1-07（2/2 失败待判定）/ C1-08（缺 3 项、待用户认可）/ C1-09（本批未碰）/ C1-10（3 通过 / 5 失败）不升；C1-01 / C1-04 / C1-05 按「已 `verified` 不重开」惯例不改。逐格依据见 `docs/eval/c1-group-chat.md`「C1 真机证据采集第十五轮」⑤。

**⑤ 统计口径（登记子代理本机实测）**：`git log --oneline 37630adb4..HEAD` = **0**；`git log --oneline d6494e49a..37630adb4` = **7**（5 个补登的第十八批登记本体 + 探针 `00bcdd4e9` + C1-01 升格 `37630adb4`）；`git log --name-only d6494e49a..37630adb4 -- 'app/src/test/*'` = **空** ⇒ 台账声明值仍 **47 行 / 509 例**；锚点 `1b0e04a9` **不重算**。`c1_doc_stats.py` 主命令 **18 OK / 0 WARN / 0 FAIL**；`--self-test`（0 处失败）/ `--only tables`（153 块 / 1227 行、0 不符）/ `--only ledger`（47 行 / 509 例、逐行相等）**均 exit 0**。⚠️ `c1_doc_stats.py` sha256 前后**同为 `238bb45f7035852acf3f27013bd5d3d6372191a4543d3297ef7044e3899f4321`**（一个字节未改）。设备设置已还原（执行者报告）：`screen_off_timeout=30000`、`stayon=false`。

📍 完整逐字段值、SHA-256、逐格判定与诚实限制见 `docs/eval/c1-group-chat.md`「C1 真机证据采集第十五轮」。

⚠️ **本批只提交 `docs/` 两个文件（`c1-group-chat.md` + 本文），未 add / commit / 修改工作区里任何其他未提交改动（`TavernMacroExpander.kt` / `architecture-map.md` / `docs/upstream-sync-2026-10-03.md` / `docs/uiredesign.md` / `C1GroupUiE2EFixtureTest.kt` 等在途改动未碰）。**

### ⚠️ 第二十五批（零设备修复批次，2026-10-07，HEAD `5c362851a`）：**C1-10 分页测试驱动缺陷 + `ConversationRepository` offset 分页 API 生产缺陷 + C1-07 续跑错误节点断言（测试期望写错）；20 状态格全不升；台账 47 行 / 509 例 → 48 行 / 514 例（补登 `ConversationPageLoadParamsTest` 5）**

⚠️⚠️ **先说性质**：本批 = **一次纯零设备窗口**（**零 `adb`、零 `am instrument`、零真机证据**），开工与收尾 HEAD 均为 **`5c362851a`**。本批 = 上一批（真机第十五轮）按惯例留到本批补登的登记本体 3 个（`c4d015e53` / `486117ee3` / `ff9c86359`，均只改 `docs/`）+ 本批主角 3 个，窗口 `37630adb4..HEAD` 实测 **6 个 commit**（`git log --oneline 37630adb4..HEAD | wc -l` = 6，`--merges` = 0）。⚠️ 轮次命名：本文档「C1 真机证据采集第 X 轮」是真机采集序列（已到第十五轮），零设备批次不占该序号，故按「C1 commit 台账」批次序记为第二十批（本节即实施状态侧的第二十五批）。

**① 三笔修复**

- **`0ae9f570a`（C1-10 设备侧测试驱动缺陷，未真机跑）**：`C1GroupPagingAndFilterDeviceTest.loadPage()` 原来无论首屏 / 续读都构造 `LoadParams.Refresh(key, loadSize, false)`，**调用点零改动、关键断言一条未放宽**，改为 `key == null` → `Refresh`、否则 → `Append(key, loadSize, false)`（+29 / −2，生产代码零改动）。根因 = Room 2.8.5 `RoomPagingUtil.getOffset` 对 `Refresh` 做「刷新窗口夹取」、对 `Append` 恒为 `key`。⚠️ **未真机跑**——「5 条转绿」是 JVM 复算推断，**非设备实证**。
- **`5ca9ecbaf`（生产缺陷，未跑真机 / HTTP 集成）**：`ConversationRepository` 四个 offset 分页 API 的 `LoadParams.Refresh(key = if(offset==0) null else offset, …)` 统一改为 **`LoadParams.Append(key = offset, loadSize = limit, placeholdersEnabled = false)`**（3 个代码块 / 4 个公开 API，+21 / −6）；新增 `ConversationPageLoadParamsTest`（5 例，反射真实调用 Room `getOffset` + 两条源码护栏）。**只影响 HTTP 分页 API，不影响 `Pager` 驱动的 UI 列表**（`Pager` 首请求 Refresh、后续 Append）。
- **`5c362851a`（C1-07 续跑错误节点断言，判定 = 测试期望写错，非生产缺陷）**：错误节点在续跑后被「同一 `MessageNode` 内追加候选分支 + 切 `selectIndex`」取代是**设计**；契约 `:201` 只要求「单角色失败**记录**错误节点并停止该轮」，**没有**要求续跑成功后继续保留 FAILED 语义，`:204` 只要求同 `round_id` + 跳过已提交 turn（run2 完全满足）。只改设备侧测试（+119 / −40），`app/src/main` 一字未动。⚠️ **未真机复跑**。

**② 判定影响（20 格逐格）：0 格升级**——C1-01 / C1-02 / C1-03 / C1-04 / C1-05 各两格维持 `verified`，C1-06 / C1-07 / C1-08 / C1-09 / C1-10 各两格仍 `unverified`；**每格就地追加「第二十批订正」**（保留原文）。理由：本批零设备、契约 `:206`/`:232-235` 四类产物（viewer 可见消息 ID / 实际模型调用序列 / token / 导出哈希）**零份**；两条测试修复与一条生产修复**均无真机复跑**。逐格依据见 `docs/eval/c1-group-chat.md`「零设备修复批次（2026-10-07，HEAD `5c362851a`）」⑥。

**③ 统计口径（登记子代理本机实测）**：`git log --oneline 37630adb4..HEAD` = **6**（`--merges` = **0**）；`git log --name-only 37630adb4..HEAD -- 'app/src/test/*'` = **只有 `ConversationPageLoadParamsTest.kt`**（XML `tests=5`、源码 `@Test`=5）⇒ 台账声明值 **47 行 / 509 例 → 48 行 / 514 例**（主动补登，脚本不检查漏列故不会 FAIL）；锚点 `1b0e04a9` **不重算**（78 / 0 merges）。`c1_doc_stats.py` 主命令 **18 OK / 0 WARN / 0 FAIL**；`--self-test`（0 处失败）/ `--only tables`（155 块 / 1249 行、0 不符）/ `--only ledger`（48 行 / 514 例、逐行相等）**均 exit 0**。⚠️ `c1_doc_stats.py` sha256 前后**同为 `238bb45f7035852acf3f27013bd5d3d6372191a4543d3297ef7044e3899f4321`**（一个字节未改）。

📍 完整三档证据区分（**本机实测 / 转述执行者 / 未复现**）与诚实限制见 `docs/eval/c1-group-chat.md`「零设备修复批次（2026-10-07，HEAD `5c362851a`）」。

⚠️ **本批只提交 `docs/` 两个文件（`c1-group-chat.md` + 本文），未 add / commit / 修改工作区里任何其他未提交改动（`TavernMacroExpander.kt` / `architecture-map.md` / `docs/upstream-sync-2026-10-03.md` / `docs/uiredesign.md` / `C1GroupUiE2EFixtureTest.kt` 等在途改动未碰）。**

### ⚠️ 第二十六批（真机第十六轮，2026-10-07，HEAD `bd88aaff2`）：**C1-07 续跑幂等真机首次通过（两状态格升 `verified`，C1-07 首次升）；C1-10 分页筛选修复后 8/8 全绿但仍 `unverified`；台账 48 行 / 514 例未动**

⚠️⚠️ **先说性质**：本批 = **一次真机采集窗口**（设备 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`），开工与收尾 HEAD 均为 **`bd88aaff2`**（`git rev-parse HEAD` 实测 = `bd88aaff2ae00574cc76badb562f05e3bc13a8df`），**设备轮本身零 commit**。本批 = 上一批（第二十批，零设备修复批次）按惯例留到本批补登的登记本体 3 个（`5e13626c9` / `8bece56f0` / `bd88aaff2`，均只改 `docs/`）+ 本批主角 = 真机窗口。⚠️ 轮次命名：本文档「C1 真机证据采集第 X 轮」是真机采集序列（已到第十六轮），零设备批次不占该序号，本批按「C1 commit 台账」批次序记为第二十一批（本节即实施状态侧的第二十六批）。

**① 两条修复的真机实证**

- **C1-07（失败续跑/幂等）真机首次通过**：`C1GroupRetryResumeDeviceTest#retrySameRoundSkipsCommittedTurnsAndDoesNotDuplicateMessages`，`am instrument` 日志 `OK (1 test)` / Time **13.108**。phase1 `FAILED/role_failed/committed=[a]/skipped=[c]/spent=6208`；phase2 `COMPLETED/reason="" /committed=[a,b,c]/spent=19286`；**`run_token` 两阶段相同**（`ef78e9f6-79fe-4ba2-9a41-4cbf75d02882`，`run_token_reused=true`）、**`group_runs rows=1`**、a 消息 id 不变（`9298d95f-…`）、**`19286==a(6208)+b(6539)+c(6539)`**、旧错误节点「同 `MessageNode` 内候选替换 + 未选中」；导出 `c1-retry-export-x.jsonl` 5063 B/5 行 `be3709800…`（本机复算一致）。契约 `:204`「同一 `round_id` 只允许一个运行实例（持久化 run token + mutex）+ 重试沿用同一 `round_id` 并跳过已提交 turn，避免重复消息」**8 项齐备**。
- **C1-10（单聊/群聊筛选）8/8 全绿**：`C1GroupPagingAndFilterDeviceTest` 连跑两遍都 **8/8 `OK (1 test)`**，上一批 5 条 `pagingSource_*` 失败全部转绿（`first_size=40/second_size=15`、`page_sizes=[20,20,15] loaded=55 distinct=55 db_unfiled=55`、`group=[20,5]=25 / direct=[20,10]=30`、`type_arg='' rows=55`、`group_hits=25 all_hits=55`）⇒ **设备实证**了 `0ae9f570a`（设备侧测试驱动改 `Refresh`→`Append`）+ `5ca9ecbaf`（生产 `ConversationRepository` 四个 offset 分页 API 改 `Append`）。

**② 判定影响（20 格逐格）：2 格升级**——**C1-07** 在**用例矩阵格** + **证据登记表格**各升 `verified`（共 2 格）；**C1-10 仍 `unverified`**（虽 8/8）：契约 `:232-235`「只看截图或**只看 UI 状态**均标记 `unverified`」（本批 #1/#2/#3 断言落 Compose 语义树可达性与 badge 节点）+ 契约 `:206` 逐例四类产物（viewer 可见消息 ID / 模型调用序列 / token / 导出哈希）本例**结构性不产出**（不启发模型、不导出群文件，同 C1-08 语义正交处置）。C1-01..C1-05 按「已 `verified` 不重开」维持；C1-06 / C1-08 / C1-09 不升。20 格每格就地追加「真机第十六轮订正」（保留原文）。逐格依据见 `docs/eval/c1-group-chat.md`「C1 真机证据采集第十六轮」⑤。

**③ 统计口径（登记子代理本机实测）**：`git log --oneline 5c362851a..bd88aaff2` = **3**（第二十批登记本体；设备轮零 commit，`--merges` = **0**）；`git log --name-only 5c362851a..bd88aaff2 -- 'app/src/test/*'` = **空** ⇒ 台账声明值仍 **48 行 / 514 例**（锚点 `1b0e04a9` 不重算：78 / 0 merges）。`c1_doc_stats.py` 主命令 **18 OK / 0 WARN / 0 FAIL**；`--self-test`（**0 处失败**）/ `--only tables`（**160 块 / 1287 行、0 不符**）/ `--only ledger`（48 行 / 514 例、逐行相等）**均 exit 0**。⚠️ `c1_doc_stats.py` sha256 前后**同为 `238bb45f7035852acf3f27013bd5d3d6372191a4543d3297ef7044e3899f4321`**（一个字节未改）。

📍 完整三档证据区分（**本机实测 / 转述执行者 / 未复现**）与诚实限制见 `docs/eval/c1-group-chat.md`「C1 真机证据采集第十六轮」。⚠️ C1-07 报告内 `scope_note` 末句「Device-side behaviour is NOT verified…（no device）」是该报告源码内的**历史固定文案**，非本次事实（本次为真机实测）。

⚠️ **本批只提交 `docs/` 两个文件（`c1-group-chat.md` + 本文），未 add / commit / 修改工作区里任何其他未提交改动（`TavernMacroExpander.kt` / `architecture-map.md` / `docs/upstream-sync-2026-10-03.md` / `docs/uiredesign.md` / `C1GroupUiE2EFixtureTest.kt` 等在途改动未碰）。**

### ⚠️ 第二十七批（真机第十七轮 / C1-09 收尾，2026-10-07，`bd88aaff2..e2bfce4ad`）：**真 SillyTavern 应用级导入验证 + 二维码位图真机往返 + 二维码中文有损缺陷修复；20 状态格 0 格改值（C1-09 仍未升）；台账 48 行 / 514 例 → 50 行 / 524 例（补登 2 个新测试类 10 例）**

⚠️⚠️ **先说性质**：本批 = **混合批次**（`git log --oneline ae08f2525..HEAD` 实测 **9** 个主角 commit；加上上一批按惯例留到本批补登的登记本体 6 个，窗口 `bd88aaff2..e2bfce4ad` = **15** 个）。开工 / 收尾 HEAD = **`e2bfce4ad`**（`git rev-parse HEAD` 实测 = `e2bfce4adbf2ca03195c4030b9763a6a71af37aa`）。⚠️ **轮次命名**：含真机采集（二维码位图往返 6 条 + 字符集修复真机复跑），故「C1 真机证据采集」据序为**第十七轮**；同时按「C1 commit 台账」批次序记为**第二十二批**（本节即实施状态侧的**第二十七批**）。

**① C1-09 收尾的最强一批证据（但判定仍不升）**

- **真 SillyTavern 应用级导入**（`5833cbbd9` / `c21cc4028`）：起真服务 @ 钉死 commit `06bde939fb1e9c4c8d8641d810f0a916b5bce127` / AGPL-3.0 / 免登录+CSRF；`/api/chats/group/import` 接受（**字节级 copy** 3615 / 2938 B）、`/group/get` 服务端解析正确（条数 / 顺序 / `name` / `is_user` / `is_system` / `chat_items`）、私有顶层键保留、野生 jsonl 不出现在 search/recent、最小注册后群出现、`open→save` 丢私有块（3615 → 2230 B）。harness `tools/verification/verify_sillytavern_import.py`（**只走 HTTP，不拷贝任何源码进仓库**）39/39 断言 exit 0；⚠️ **SillyTavern 本体只在 `/tmp`，未入库**。
- **二维码位图端到端真机 6/6**（`5f461d9f6` / `16d00e9f4`，设备 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`）：生产 `encodeQr`→`encodeQrBitmap`→**MLKit** 解码→逐字段→`importShare`；导出 FileProvider URI 字节 **逐字节 ==** 生产 `exportGroupJsonl`；`ACTION_SEND` 源码护栏 5 例。
- **⑤ 二维码中文 / 非 Latin-1 有损缺陷已修**（`0edc70d67` / `e35f64e39` / `e2bfce4ad`）：`QRCode.kt` 显式 `EncodeHintType.CHARACTER_SET = "UTF-8"`；副作用 = ECI 头吃掉 1 字节 ⇒ 容量 **2953 → 2952**；真机非空验证「删 hints → 中文用例连续 **4/4** 失败（`MLKit 未能从生产位图解出任何二维码`，同 APK 下 ASCII 用例仍通过）→ 还原 → 通过」。**登记子代理实测** `QRCode.kt` sha256 = `ecb78fc67438298aedac48bb7d5afd8c65a8c7618ca9fe8b53b816d70bace849`、`GroupChat.kt` = `0f61ffdd2c63a0ecfcfc670e341db4e87b581bd4bfe2337c65e14895b935c8f9`、`ConversationExport.kt` = `b6b821271ddb79dda6c19e68d7784e254a62c11a620c56bcf3e7fb410fa5b775`（与执行者给值**一致**）。
- ❌ **未验**：真实相机扫码链路（`QrScannerSheet` 的 CameraX `analyzeFrame` 的 `mediaImage`，与位图路径只差图片来源）、真实系统分享面板 UI 交互、SillyTavern 多用户 / 开启登录 / `--listen`、`NoConfig` 分支本身。

**② 判定影响（20 格逐格）：0 格改值**——**C1-09 仍 `unverified`**（逐条缺口见 c1 文档「C1 真机证据采集第十七轮」⑦：契约 `:206` 三类产物结构性不产出且未获用户认可 + `:205` 真实相机扫码零份 + `:232-235` 系统分享面板 UI 零份 + 酒馆 `open→save` 丢私有块、重存回 KhatKit 得 `Unsupported`）。其余 19 格维持原判。20 格每格就地追加「第二十二批订正」（保留原文）。

**③ 统计口径（登记子代理本机实测）**：`git log --oneline ae08f2525..HEAD` = **9**（主角；`--merges` = 0）；`git log --oneline bd88aaff2..e2bfce4ad` = **15**（上一批登记本体 6 + 本批主角 9）；`git log --name-only ae08f2525..HEAD -- 'app/src/test/*'` = **2 个新文件**（`C1GroupQrPayloadCodecRoundTripTest` 5 + `GroupExportShareIntentSourceGuardTest` 5）⇒ 台账声明值 **48 行 / 514 例 → 50 行 / 524 例**（锚点 `1b0e04a9` 不重算：78 / 0 merges）。`c1_doc_stats.py` 主命令 **18 OK / 0 WARN / 0 FAIL**；`--only ledger`（**50 行 / 524 例、逐行相等**）/ `--only tables`（**161 块 / 1294 行、0 不符**）**均 exit 0**。⚠️ `c1_doc_stats.py` sha256 前后**同为 `238bb45f7035852acf3f27013bd5d3d6372191a4543d3297ef7044e3899f4321`**（一个字节未改）。⚠️ **仪器 @Test 口径变动**：新增 1 个 androidTest 文件（6 `@Test`）⇒ tracked **20 → 21 文件 / 82 → 88 例**、含 untracked **21 → 22 文件 / 85 → 91 例**。

**④ lint 基线变动**：app **581W+6H=587 → 617W+6H=623**（**+36**，全部 `gradle/libs.versions.toml` 依赖版本检查：`NewerVersionAvailable` 29 + `AndroidGradlePluginVersion` 4 + `GradleDependency` 3，属环境 / 联网相关），全模块 **617W+7H=624 → 650W+7H=657**。**本批改动的两个 doc 文件 lint 命中 0。** 见 c1 文档遗留第 54 条。

⚠️ **本批只提交 `docs/` 两个文件（`c1-group-chat.md` + 本文），未 add / commit / 修改工作区里任何其他未提交改动（`TavernMacroExpander.kt` / `architecture-map.md` / `docs/upstream-sync-2026-10-03.md` / `docs/uiredesign.md` / `C1GroupUiE2EFixtureTest.kt` 等在途改动未碰）。**

### ⚠️ 第二十八批（真机第十八轮，2026-10-07，`e2bfce4ad..2a0610705`）：**C1-06 超时半条真机通过 ⇒ C1-06 首次升 `verified`；C1-08 串扰断言结构性化并证判别力 ⇒ C1-08 首次升 `verified`；C1-10 真实网关轮与点名路径正交 ⇒ 维持 `unverified`；台账 50 行 / 524 例 → 51 行 / 531 例（补登 1 类 7 例）**

⚠️⚠️ **先说性质**：本批 = **一次真机采集窗口**（OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`）+ 1 处生产改动（`ChatManager` 步超时可注入，默认值不变）+ 1 处 mock 改动（`MOCK_HANG_ROLE`，默认关闭）+ 2 处测试改动。开工 / 收尾 HEAD = **`2a0610705`**（`git rev-parse HEAD` 实测 = `2a0610705f3f71ff2702d656f340155e60fcfc2d`）。窗口 `e2bfce4ad..2a0610705` 实测 **17 个 commit**（`--merges` = 0）。⚠️ **轮次命名**：含真机采集（C1-06 超时半 + C1-08 记忆隔离真实网关 + C1-10 真实网关轮），故「C1 真机证据采集」据序为**第十八轮**；同时按「C1 commit 台账」批次序记为**第二十三批**（本节即实施状态侧的**第二十八批**）。

**① 判定影响（20 格逐格）：4 格改值** —— **C1-06 首次升 `verified`**、**C1-08 首次升 `verified`**（各自在「用例矩阵」与「证据登记」两表内的状态格，从 `unverified` → `verified`）；**C1-10 维持 `unverified`**；其余 7 例（C1-01/02/03/04/05/07/09）维持原判。20 格每格就地追加「第二十三批订正」（保留原文）。逐格依据见 `docs/eval/c1-group-chat.md`「C1 真机证据采集第十八轮」⑤。

**② 三条裁定（契约逐条，登记子代理本机读码 + 复算）**：

| 用例 | 裁定 | 关键依据（契约行号） |
|---|---|---|
| C1-06 取消与超时 | **升 `verified`** | 契约 `:201`「取消/超时不得写入未生成的消息」两半均有真机实测（取消半 `empty_bubble_count=0`/a 保留/b 半截保留/错误节点；超时半 `status=TIMEOUT`/`empty_bubble_count=0`/a 保留/b 错误节点、`spent=6212=6190+22`）；`:206`/`:232-235` 8 字段按「用例整体」齐（viewer 台账与导出哈希在取消半）。见「第十八轮」① |
| C1-08 记忆隔离 | **升 `verified`** | 契约 `:203` 空间键 `group:<conv>:role:<id>` / `role_id` 归因 / `source_message_id` / 无全局·助手回退全满足；旧恒真断言被结构性判据 `memoryIsolationViolations` 替换，JVM 镜像 7 例变异实测 6 failed 证判别力；`:206` 8 字段齐。`389f263d6` 三处放宽（换模型 flash→glm-5.2 / 检索 query 改 fact 文本 / ≤6 次重试）经裁**不构成改弱**（见「第十八轮」②） |
| C1-10 单聊/群聊共存 | **维持 `unverified`** | 第 9 条真实网关轮跑在既有夹具群上，**与 C1-10 点名的「混排/类型筛选」路径正交**，其 8 字段不能把筛选/混排断言抬成非 UI 级；筛选/混排仍只有 UI / SQL 级证据，契约 `:232-235`「只看 UI 状态 → `unverified`」 |

**③ 统计口径（登记子代理本机实测）**：`git log --oneline e2bfce4ad..HEAD` = **17**（`--merges` = 0）；`git log --name-only e2bfce4ad..HEAD -- 'app/src/test/*'` = **只有 `.../feature/chat/C1MemoryIsolationPredicateTest.kt`**（7 `@Test`）⇒ 台账声明值 **50 行 / 524 例 → 51 行 / 531 例**（`+1 类 / +7 例`）。新基线：`:app:testDebugUnitTest` **118 类 / 961 例 / 0F0E0S**；`:ai:test` **30 类 / 220 例**；lint app `0 / 617 / 6 = 623`、全模块 `0 / 650 / 7 = 657`；仪器 `@Test` tracked **22 文件 / 96 例**、含 untracked **23 文件 / 99 例**。`c1_doc_stats.py` 主命令 **18 OK / 0 WARN / 0 FAIL**；`--self-test` / `--only tables` 均 exit 0。⚠️ `c1_doc_stats.py` sha256 前后**同为 `238bb45f7035852acf3f27013bd5d3d6372191a4543d3297ef7044e3899f4321`**（一字节未改）；锚点 `1b0e04a9` 不重算。

⚠️ **本批只提交 `docs/` 两个文件（`c1-group-chat.md` + 本文），未 add / commit / 修改工作区里任何其他未提交改动（`ChatDrawer.kt` / `ChatPage.kt` / `SettingAboutPage.kt` / `KedgeMiuixMorphingTitleBar.kt` / `TavernMacroExpander.kt` / `architecture-map.md` / `docs/upstream-sync-2026-10-03.md` / `docs/uiredesign.md` / `C1GroupUiE2EFixtureTest.kt` 等在途改动未碰）。**

### ⚠️ 第二十九批（真机第十九轮，2026-10-07，`2a0610705..1758e2109`）：**C1-10 第 9 条改造为「筛选路径内」真跑一轮 ⇒ C1-10 升 `verified`；台账 51 行 / 531 例不变（本批零 JVM 改动）**

⚠️⚠️ **先说性质**：本批 = **一次真机采集窗口**（OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`；绿日志 `OK (1 test)` / `Time: 19.62`）+ **2 处 androidTest 改动**（`C1GroupPagingAndFilterDeviceTest.kt` 第 9 条：`df4c70f09` 改造 + `1758e2109` 加固），**生产代码 / 工具 / `app/src/test` 一字未动**。开工 / 收尾 HEAD = **`1758e2109`**（`git rev-parse HEAD` 实测 = `1758e2109b6fef396c361c5c87dc923f16c019e0`）。窗口 `2a0610705..1758e2109` 实测 **4 个 commit**（`--merges` = 0；含上一批留到本批补登的登记本体 2 个）。⚠️ **轮次命名**：含真机采集，故「C1 真机证据采集」据序为**第十九轮**；同时按「C1 commit 台账」批次序记为**第二十四批**（本节即实施状态侧的**第二十九批**）。

**① 判定影响（20 格逐格）：2 格改值** —— **C1-10 在「用例矩阵」与「证据登记」两表内的状态格从 `unverified` → `verified`**；其余 9 例维持原判。20 格每格就地追加「第二十四批订正」（保留原文）。逐格依据见 `docs/eval/c1-group-chat.md`「C1 真机证据采集第十九轮」①②⑤。

**② C1-10 裁定：升 `verified`（契约逐条）**

| 用例 | 裁定 | 关键依据（契约行号） |
|---|---|---|
| C1-10 单聊/群聊共存 | **升 `verified`** | 契约 `:232-235` 判 `unverified` 的三情形是「测试未运行 / 只看截图 / **只看 UI 状态**」；本用例现为「UI 操作（点 chip + 点抽屉行）+ 生产 DAO（PagingSource 同源查询）+ 生产导航状态（`NavViewModel.currentPage` 反射）+ 真网关产物」的混合体，**不再只看 UI 状态**。上一批（第二十三批）「真实网关轮与筛选路径正交」的唯一不升理由被本次改造消掉：第 9 条先点 chip → 生产 DAO 取筛选首屏 → 抽屉点击该行 → 导航断言，**再在这条从筛选结果打开的群上真跑一轮** ⇒ 8 字段由筛选路径打开的群产出。`:206`/`:232-235` 8 字段齐（commit `1758e2109` + `df4c70f09` / 命令 `OK (1 test)` · Time 19.62 / 设备 PKG110·16·36 / 输入 `请三位依次发言，每位一句话。` / viewer a·b·c 台账 / 序列 flash→glm-5.2→flash / `20537=Σusage` / 导出 `66d3b9ce…` 2331 B·5 行）。见「第十九轮」①②⑤ |

**③ 统计口径（登记子代理本机实测）**：`git log --oneline 2a0610705..HEAD` = **4**（`--merges` = 0）；`git log --name-only 2a0610705..HEAD -- 'app/src/test/*'` = **空**（本批只改 androidTest）⇒ 台账声明值**不变：51 行 / 531 例**（锚点 `1b0e04a9` 不重算）。`c1_doc_stats.py` 主命令 **18 OK / 0 WARN / 0 FAIL**；`--self-test` / `--only tables` 均 **exit 0**；脚本 sha256 前后**同为 `238bb45f7035852acf3f27013bd5d3d6372191a4543d3297ef7044e3899f4321`**（一字节未改）。⚠️ **仪器 @Test 口径不变**：tracked **22 文件 / 96 例**、含 untracked **23 文件 / 99 例**（只改第 9 条方法体）。基线沿用上一批：`:app:testDebugUnitTest` 118 类 / 961 例；`:ai:test` 30 类 / 220 例；lint app `0/617/6=623`、全模块 `0/650/7=657`。

**④ 设备侧遗留（如实登记）**：设备 `settings.preferences_pb` 仍含历轮测试用的 `realProvider` 与 3 个测试助手（mtime 14:47，早于本轮）——**本轮未清**；第 9 条对「筛选结果首条必须是本案夹具群」有依赖（本轮 = `f3a1`）；本轮测试首次尝试遇「网关流挂死」（非断言失败）。三条均见 `docs/eval/c1-group-chat.md` 遗留 55/56/57。

⚠️ **本批只提交 `docs/` 两个文件（`c1-group-chat.md` + 本文），未 add / commit / 修改工作区里任何其他未提交改动（`TavernMacroExpander.kt` / `ChatDrawer.kt` / `ChatPage.kt` / `SettingAboutPage.kt` / `KedgeMiuixMorphingTitleBar.kt` / `architecture-map.md` / `docs/upstream-sync-2026-10-03.md` / `docs/uiredesign.md` / `C1GroupUiE2EFixtureTest.kt` 等在途改动未碰）。**
### ⚠️ 第七批（`8622bf19..db4cdd77`，2026-10-06）：`importGroup` 契约 `:205` 缺口修复

⚠️ **这一批只动 `TavernChatCodec.importGroup`（导入侧），一行运行时代码路径都没被
接上**——因为 `importGroup` 在 `app/src/main` 里**仍然没有调用方**。
⚠️ **`importShare` 一行未动；导出路径一行未动；golden 哈希没变，也没跑
`--update-golden`**。

| commit | 改了什么 | 结论 / 边界 |
|---|---|---|
| `8622bf19` | 群配置导入筛查与角色卡去重（**纯函数**）：13 条校验分成「结构性拒绝」与「越界归一化」 | ✅ 新增 `GroupChat.screenImportedConfig` / `dedupeCards`。⚠️ 判据是「只有 `revision` / `tie_policy` 各自已有一个**确定无疑的合法缺省值**」才允许归一化 |
| `aee20f85` | 25 条纯函数用例 | 含两条**结构性不变式**（一次扫 10 个配置）：① 放行的配置 `validate` 必须也判合法 ② 归一化**只许动** `revision`/`tie_policy` |
| `88777898` | 接上密钥闸门 + 角色卡去重 + 配置筛查，坏配置不再外泄 | ⚠️ 拒绝的形态**不是整份返回 `null`**，而是 `config = null` + 字段级错误，**消息/群名/角色卡照常返回** |
| `db4cdd77` | 22 条端到端用例 | 含逐字节往返不变式与 `NoConfig`/`Rejected` 分流 |

⚠️⚠️ **口径订正（子代理纠正评审，已采纳）**：`importGroup` **不是**「读别人的酒馆文件」。
第一道门 `TavernChatCodec.kt:285` 是
`document.header[GROUP_FIELD] as? JsonObject ?: return null`——纯 SillyTavern 群聊文件
**没有 `khatkit_group` 表头，当场返回 `null`**。它是「**读回自家导出**」。
⚠️ **读别人分享载荷的是 `GroupChat.importShare`**（`GroupChatPage.kt:492` 生产侧在用）。

⚠️ **「拒绝 vs 降级」三处采信了「拒绝」**：`schema_version` 未知（依据
`GroupChat.kt:504-510`「版本闸门是唯一判定口径」）、预算越界（契约写死「超上限保存失败」，
且**不存在安全的缺省值**）、`mode` 未知（默认 pipeline 会**静默丢 `vote_candidates`**）。
只有 `revision` / `tie_policy` 走归一化。

⚠️ **核实中发现的额外真 bug（已随去重一并修掉）**：`importGroup` 用
`cardJson.decodeFromJsonElement` 解角色卡，**不丢**缺 `role_id` 的条目
（`{"role_id":""}` 解出 `roleId = ""` 的孤儿卡），而 `GroupChat.decodeCards`
（`GroupChat.kt:922` KDoc「不猜它属于谁」）**会丢**。同一语义两条路径的漂移。

⚠️⚠️ **变异检验有一项存活，如实登记**：**变异 5（弱化 residual 兜底）存活**。
原因是「结构性错误一律拒绝」与「归一化后 residual 复查」**双层保险互相独立地**
拦住同一批错误。**结论：residual 兜底当前无法被任何变异杀死**——它是绊线，
**现在没有测试护着**。子代理明确「**不声称证明了它**」。变异 4 第一次也存活（同因），
换个变异体才抓住。
📍 **来源指针**：`docs/eval/c1-group-chat.md` 的「`importGroup` 契约 `:205` 缺口修复
（零设备，2026-10-06，HEAD `db4cdd77`）」那一节里，
**「⚠️ 变异检验有一项存活，如实登记」**那个四级小节（逐字照录了双层保险的成因与
「变异 4 第一次也存活（同因）」）。

⚠️⚠️ **本批唯一改变数字的地方**（口径与复算命令见
`docs/eval/c1-group-chat.md` 的「统计口径复算脚本」小节）：
`:app:testDebugUnitTest` **105 类 / 808 例 → 107 类 / 855 例**（新增 47 条）；
C1 相关 JVM 测试类台账 **40 类 / 408 例 → 42 类 / 455 例**
（新增 `GroupImportScreeningTest` 25 + `TavernGroupImportGateTest` 22）；
lint **0 error**、app **584W+6H=590**、15 模块 **617W+7H=624**（与上一窗口**逐位相同**）；
`app/src/androidTest` 跟踪口径 **61**（含未入库的 `C1GroupUiE2EFixtureTest.kt` 是 **64**）。
⚠️ **台账锚点 `1b0e04a9` 的 78 个 commit 仍然没有被重算**，本批 4 个 commit 全部追加进
`docs/eval/c1-group-chat.md` 台账的「锚点之后的后续提交」小节。
⚠️ **十例判定一个都没变，仍是 10/10 `unverified`**（导入侧改动，四类产物一份未增，
零设备）。⚠️ **本轮 `docs/beyond-operit-client-changes.md` 一字未动**（按约束）。
⚠️ **上面 `107 类 / 855 例` 是截至 `db4cdd77` 的值，按惯例保留不覆写**；
**当前口径已是 `110 类 / 881 例`**，见上面「第八批」的 ③ 那一段。

⚠️⚠️ **三条仍然存在的缺口（已登记为 `c1-group-chat.md` 的「已知遗留与风险」第 30–32 条）**：
① **运行时零证据**——契约 `:205` 的「恢复失败不留下半成品会话」只在**返回值层面**有了
保证，但**没有任何证据表明「建会话」那段代码读了 `importReport`**（因为它不存在）；
② ⚠️ **`memory_space_id` 那条校验在导入场景无解**——新会话 id 导入时未知，
`importGroup` 放过它是必然的，**调用方建完会话后必须补跑 `validate(config, newId)`**，
否则会造出**记忆空间键错位**的群，而「**调用方是否补做未验证**」；
③ `importShare`（全拒）与 `importGroup`（结构性拒 + 两项归一化）**口径有意不同**，
依据在 KDoc，但属**单方面设计判断，值得复核**。

📍 **三条各自的来源指针**（⚠️ 上面的「第 30–32 条」是合并写法，容易被当成一条，
所以这里逐条对到具体那条）：
- **① → 「已知遗留与风险」第 30 条**。核实依据逐字照录：`importGroup` 在
  `app/src/main` 里的全部出现都是 KDoc 与注释（`TavernChatCodec.kt`、
  `GroupExportCard.kt`），真实调用点只有 `C1DeviceEvidenceTest.kt` 与两个 JVM 测试类。
- **② → 「已知遗留与风险」第 31 条**。⚠️ 那条还多记了**为什么不能顺手改**：
  在导入时改写 `memory_space_id` 会破坏「`exportGroup → importGroup → exportGroup`
  逐字节不变」这条往返不变式，且它本来就是**建会话之后**的事。
- **③ → 「已知遗留与风险」第 32 条**。⚠️ 那条同时记了一条**当时未改**的
  文档与实现不一致（`TavernChatCodec.kt` 的 KDoc 把 `config == null` 写成「两种成因」，
  实现实际是四种）——✅ **已由 `d00880fc` 纯注释订正**，但「口径有意不同属单方面判断」
  那半句**仍未复核**。

⚠️ **第 ⑤ 条诚实边界（变异检验存活）不在上面那三条里**，它的来源指针在**上一段**
（「变异检验有一项存活，如实登记」那段末尾）。**五条边界合起来的完整清单见
`docs/eval/c1-group-chat.md` 操作清单 C9 那一小节。**

### ⚠️ 第六批（`33eb801e..bed09118`，2026-10-06）：两个真机必崩 + Coil 结构性修复 + 四项代码债

⚠️ **这一批横跨「真机 UI」「浏览器 / Coil」「群聊内核」三块**，所以它分别影响不同的缺口
类别——但**没有一件改变任何用例的判定**。⚠️ **本轮没跑 gradle**（纯文档任务），
下列数字都是**逐个 XML / 逐文件重新统计**的实测值。

| commit | 改了什么 | 结论 / 边界 |
|---|---|---|
| `33eb801e` + `5d58f923` | 群配置面板补 `imageVector = tune`（原来 `painter` / `imageVector` **两个都不传**，MD3Exp 分支 `requireNotNull` 当场抛）+ 3 例源码护栏 | ✅ **真机已复验**：`logcat -c -b crash` 后开面板 + 滚动，`logcat -d -b crash -t 200` **为空**，全量 logcat grep 两个异常 **0 命中**，`uiautomator dump` 确认 15 个字段全渲染，面板内 5 次滚到底 + 3 次反向不崩（pid 恒定、focus 恒为本 app）。选 `tune` 的理由：与顶栏触发按钮 `GroupTopBar` 的 `Icon(tune, "群配置")` 同款、语义闭合；走 `imageVector` 这侧是因为**全仓 62 个调用点全用它**。⚠️ **四条边界**：**Miuix 完全没验** / 其余 **64 个调用点没逐个上真机** / **没在旧 APK 上先复现再对比**（原始栈是用户实测给的）/ **面板里的保存 / 二维码 / 扫码三个业务动作一个都没点** |
| `71d1439e` + `54c196ca` | 同面板补 `scrollable = false`——根因是弹层与内容**两层 `verticalScroll` 叠加**，内层被量到无限高 + 3 例源码护栏 | ⚠️ **修法只加一行、不删内容的滚动**：面板内容长（九个配置字段 + 每角色一个 `RoleEditor` + 导入导出两段），整块表单应一起滚；而 `scrollable = true` 时 Khromia 那层带 `weight(1f, fill = false)`，滚动语义归弹层会让内容高度受权重影响。**两种组合二选一，两个都留着必崩**。⚠️ 同上四条边界 |
| `ea482935` + `2766afc7` | **Coil 单例崩溃结构性修复**：`ImageLoader` 定义点从 `RouteActivity.onCreate` 的组合搬到 **`KhatKitApp : Application(), SingletonImageLoader.Factory`**，组件清单原样保留（crossfade / Ktor 网络栈 / P 以上 GIF、否则 `SvgDecoder`）+ 6 例源码护栏 | ⚠️ **根因不是 `b5c5ebcb`**（`git show --name-only b5c5ebcb` 里 grep `coil` **零命中**），而是**同一 instrumentation 进程的执行顺序**：`NodeTreeSmokeTest`（`createComposeRule()` 不带 Activity 渲染 `AsyncImage`）先把**内置默认** loader 钉死 → `BrowserRuntimeTest` 的 `ActivityScenarioRule` 拉起 Activity → `setSafe` 当场抛 → 整进程死。⚠️ **不用条件判断绕过**：那会在测试进程里**静默跳过自定义配置**，把崩溃换成更难查的行为不一致。⚠️⚠️ **真机效果本轮未验证**（按约束不碰设备）——磁盘上那份 `tests="10" failures="1"` 的 XML 是**修复前**的现场 |
| `fa36c65c` / `06cf6181` / `bed09118` / `a74c1820` | 四项代码债：`ChatService` 第二份生成管线加 fail-loud 闸门（provider 同时按产物改名）、`abandonDanglingGroupRuns` 改分批读到清空、`onSuccess` 的 `null` 分支改按 `runToken` 清镜像、给已订正的 `ChatMessage` KDoc 加 2 例防回退护栏（**KDoc 一个字没改**） | ⚠️ **前三项的运行时行为全是零证据**（`require` 真不真抛、分批循环真机行为、`remove(key, value)` 的真实并发效果都没观测过），三条 gradle 命令只证明「编译过 + 单测绿」，**不构成任何契约验收证据**。逐条见 B9 / B9b / B10 与 B14 / B15 |

⚠️⚠️ **本批唯一改变数字的地方**（都是汇总值，逐条口径登记在 `c1-group-chat.md`）：
`:app:testDebugUnitTest` **99 类 / 783 例 → 105 类 / 808 例**（`+6 类 / +25 例`，
逐个 `TEST-*.xml` 用 Python + ElementTree 求和实测，`glob` 命中 105 个 XML）；
C1 相关 JVM 测试类台账 **37 类 / 395 例 → 40 类 / 408 例**（复算
`rows 40 / declared sum 408 / mismatch vs XML: []`）；lint **0 error**、app
**584W+6H=590**、15 模块 **617W+7H=624**（与上一窗口**逐位相同**）；
`app/src/androidTest` **git 跟踪口径仍是 61**（含那个**未入库**的
`C1GroupUiE2EFixtureTest.kt` 是 **64**）。
⚠️ **台账锚点 `1b0e04a9` 的 78 个 commit 仍然没有被重算**，本批 10 个 commit 全部追加进
`docs/eval/c1-group-chat.md` 台账的「锚点之后的后续提交」小节。
⚠️ **本轮 `docs/beyond-operit-client-changes.md` 一字未动**（按约束）。

### ⚠️ 本轮（`af104850..6ba95422`）四批修复 + 两个门禁，一句话索引

详细版全部在 `docs/eval/c1-group-chat.md`（「C1 内核四批补测与修复」与
「HTTP 端点群聊门禁 + `failGroupTurn` 回收平票脚手架」两节）。这里只列**结论**：

| commit | 改了什么 | 结论 / 边界 |
|---|---|---|
| `af104850`（P0） | `GroupChat.validate` 拒绝 `role_id == SUMMARY_ID`（field 用 `roles[].role_id`），堵住「合成节点冒名 → 该角色全部发言对所有人无条件可见」 | ⚠️ **只收口在 `validate`、没动 `visibleMessages`，这是刻意的**：给 `SUMMARY_ID` 分支加 `&& turnKind == TURN_VOTE_SUMMARY` 会让**投票失败摘要**（用户可见的「[投票] 本轮未能得出结论：…」）**对所有视角一起消失**——拿可见性回归换一条已堵死的路径。⚠️ **UI 手输路径不存在**（`role_id` 在面板里是只读 `Text`），**唯一真实入口是外部 JSON 导入**；`TavernChatCodec.importGroup` 不 validate 但**当前无调用方、暂不可达**（已知遗留） |
| `9844cbc4`（P1） | 补 `mention_role_ids` 放行分支的 JVM 覆盖：新增 `speaking()` helper（`assistant()` **没加参数**），三向全断 + 反向对照 | ⚠️ **先核实过原测试真没覆盖**：既有两处都把 `@` 挂在 **USER** 消息上，USER 本就无条件放行，**碰不到 mention 分支**；结构性原因是 `assistant()` helper **没有 `mentionRoleIds` 参数** |
| `e7ad2a77`（P1） | 补视角过滤四个盲区：议长没进遍历 / 跨轮负向 / 同角色多条 / pipeline 只断 4 对非 9 对 | ⚠️ **盲区③拆成三条是被变异检验逼出来的**：只写两条时，删掉 `predecessorId` 分支的 `index >= roundStart` 守卫**没有任何用例失败**（既有全是单轮，`roundStart` 恒为 0，守卫是死代码）；补第三条跨轮用例后该变异才被抓住 |
| —（P2） | `coerceAtLeast(0)` 的 fail-open：**探针实测不可达，未改** | `userlessWindows=36 crossRoundLeaks=0`（6 种轮数 × 13 种 limit）、`emptyPrefixes=6 userlessNonEmptyPrefixes=0`，并枚举了全部 4 个 `visibleMessages` 调用方。⚠️ 结论写进 KDoc：**「当前不可达」不是「结构上不可能」，新增调用方必须重跑探针** |
| `87f9c209` | **HTTP 五个会话操作端点加群聊门禁**（**路径在 `app` 模块的 `ConversationRoutes.kt`，不在 `web`**；edit `:283` / fork `:299` / delete `:315` / select `:330` / regenerate `:346`），错误码 **409 Conflict**（照 `FolderRoutes.kt:69` 范式） | ⚠️ **五端点原本零 `isGroupConversation` 判断**，破坏链逐环实读成立：外来写入 → `roundOutputPresent` 返 false → 删整行 `group_runs` → 重新抢占 `spentTokens=0` → **整轮作废、全员重跑、预算被无声归零**。⚠️ **fork 的拒绝理由与评审不同**：`createForkConversation` 不复制 `group_config`/`type`，产物是单聊、对源会话只读，**破坏不了账目**；仍拒是因为 `ForkConversationResponse` 只回 `conversationId`、调用方无从分辨——文案据实写成「静默换了会话类型」。⚠️ **绕过门禁的坑已堵**：守卫必须排在 `initializeConversation` **之后**（未加载会话拿到的是 `groupConfig = null` 的空单聊占位，否则门禁形同虚设），源码护栏钉死顺序；`regenerate` 是唯一例外（本来就没有 `initializeConversation`，补上会改非群聊路径）。**变异检验两次真红**：删守卫 → 3 红；挪到 `initializeConversation` 之前 → 红 |
| `e6764293` | `failGroupTurn` 补调 `dropTieBreakScaffolding` | 议长裁决那一轮生成失败时，平票 SYSTEM 脚手架会**永久留在会话里**，之后每轮所有角色都看到。⚠️ `completeGroupRound` 那侧 3 条对照用例**修复前就已经是绿的**，不是这次修复的证据 |

⚠️ **两处已订正的认知错误（子代理纠正评审与我的说法，原文保留）**：
`GroupConfigSheet` 的 `role_id` 是**只读 `Text`**、**UI 手输路径不存在**（「添加成员」
生成的是 `role-N` 不是 UUID）；`tally` 那条**问题真实但机制描述反了**——`associateBy`
保留的是**靠后（最新）**那条，所以裁决基于**最新立场**而非「过时立场」，
但**旧票新票一起进候选池、票池被污染**这个结论仍然成立。

### ⚠️ 第五批（`e72793e6..8ccc0264`）：平票脚手架两处漏回收 + 残留旧 job 两道守卫

详细版全部在 `docs/eval/c1-group-chat.md` 的「平票脚手架三处漏回收 + 残留旧 job 两道守卫
（零设备）」一节。这里只列**结论**。⚠️ **本批与上一批性质不同：上一批补覆盖，
这一批修的是三个当时真的还在漏的缺陷**——但**修缺陷不等于拿到验收证据**，
**十例状态一个都没变**。

| commit | 改了什么 | 结论 / 边界 |
|---|---|---|
| `e72793e6` | `cancelActiveGroupRun` 与 `abandonDanglingGroupRuns` 补调 `dropTieBreakScaffolding` | ⚠️ `failGroupTurn` 那处已由 `e6764293` 修过，这两处是**同款失败模式**。**为什么确定该修**：`completeGroupRound` 全仓**只有一个调用点**（`onSuccess`），这两条路径落终态后再无路径为该轮调它 → 脚手架必然孤立。**为什么可以无条件补**：保留脚手架的唯一状态 `NeedsChairTieBreak` 只有 `completeGroupRound` 自己能进；`cancelActiveGroupRun` 第一行就是 `remove(...)?.runToken ?: return`（无活跃轮次直接返回）；`abandonDanglingGroupRuns` 严格早于存新 USER 消息，新轮再平票会自己 append 一条 ⚠️ **根因不只在漏回收**：`GroupChat.visibleMessages` 第一分支 `role == SYSTEM \|\| isSynthetic -> true` 排在所有 `index >= roundStart` 判断**之前**，对任何 viewer、任何轮次放行——**只要它在库里就一定被看见** |
| `8a5f89d7` | `GroupTieBreakScaffoldingDropSourceGuardTest` **3 → 7** 条 | ⚠️ **抽不出纯函数判据**——「该不该回收」取决于 DAO 读，而要守的「函数体内必须有那处调用」本身是源码属性。**变异两次真红**（删 cancel 那处 → 3 红；删 abandon 那处 → 3 红），还原用**文件备份 + `sha256sum -c`** |
| `2fdee352` | ⚠️ **本批最严重的一个**：残留旧 job 不得推进状态、不得跨轮盖戳。`GroupTurnCoordinator.advance` 加终态守卫返回新增的 `Advance.Halted`；新增纯函数 `checkCommitAdmission` → `CommitAdmission.Admitted/Denied`，在 `commitGroupTurn` 里**盖戳之前**拦下 | ⚠️ **触发路径**：`abandonDanglingGroupRuns` **不取消任何 job**（`cancelJobs()` 只被 `stopGeneration` / `cleanup()` 调用）→ 旧轮判死后旧 job 仍跑完进 `onSuccess`。**问题一**：`advance` 不检查终态，原来只有巧合式安全（新轮未被抢占时 `findByRound` 返回 null），**新轮已被抢占时返回新轮自己那行 RUNNING → 照常推进 → 上一轮角色被追加进新轮 `committed_role_ids`**。**问题二**：`stampGroupTurn` 排在所有 `return null` **之前**且内部是**不可逆的 `saveConversation`**，上一轮的产出被**永久盖上三元组**，`return null` 只是止损、**追不回来**。⚠️ **为什么守卫放 `advance` 而不是 `commitGroupTurn` 入口**：入口按终态判会全放行（命中的是新轮那行 RUNNING），按令牌判就变成第二处、判据重复；且 `advance` 是纯内核 → **能拿真单测**。⚠️ **两层判据分工不重叠**：`checkCommitAdmission` 认**身份**（这是谁的产出），`advance` 判**生死**（这轮还活着吗），所以归属判定**故意不判终态**、`advance` **故意不判令牌**。⚠️ **令牌必须从 `takeGroupTurn` 返回值捕获**（`GroupTurnEntry.Speak(...).runToken`），**不得事后反查 `groupRunsInFlight`**——判死路径都把镜像清了，反查到的要么 null（打死正常路径）要么是新轮令牌（恰好放行残留 job） |
| `ef716dd3` | `onFailure → failGroupTurn` 补**同款**守卫（新增纯函数 `checkFailureAdmission`） | ⚠️ ②只堵了 `onSuccess`，**失败那条路没堵**：超时 / 角色失败时残留旧 job 会往新轮塞 `errorNode(roundId = 新轮)` 并把新轮写成 `FAILED`。⚠️ **两路结构不对称决定了修法**：`failGroupTurn` 签名里**没有 `plan` 形参**，它自己 `roundPlanFor` **现算**，所以守卫**只能放在它内部**；令牌来源同源（`onFailure` 闭包读外层 `groupRunToken`），**1 跳透传、没有新捕获点**。⚠️ **判据不另立**：`checkFailureAdmission` 直接调 `checkCommitAdmission` 拿身份再补一条终态——**因为失败路没有 `advance` 这一层**（`fail`/`timeoutRound` 是无条件写终态的纯函数），把终态塞进 `checkCommitAdmission` 会把提交路一起打死。⚠️ **主次要分清**：**身份条款是决定性的**（新轮已抢占 → RUNNING 但令牌不同 → 拒）；终态条款在「判死后用户没再发消息、`plan.roundId` 仍是旧轮」这一形状下才决定性，它顺带兜住 `stopGeneration` 里 `session == null` 就写 CANCELLED 的竞态。⚠️ **守卫插在 `failGroupTurn` 四个副作用全部之前**：① `appendGroupMessages(errorNode)`→`saveConversation` ② `persistRoundState(failed)` ③ `dropTieBreakScaffolding`（**不可逆且跨轮**，全会话范围清扫）④ `groupRunsInFlight.remove`（**装的可能是新轮令牌**）；**拒收分支只有 `Logging.log` + `return`，不碰镜像** |
| `8ccc0264` | `GroupTurnCoordinatorTest` **78 → 84**（6 条真单测）+ 新类 `GroupStaleJobFailureSourceGuardTest` 6 条源码护栏 | ⚠️ **正常放行必须也有回归断言**（正常失败 / 正常超时 / 续跑死轮三条），**不能只测「被拒」**——否则「守卫写成永远拒绝」也全绿。**变异 7 个（M1–M7）全部 EXIT=1**。⚠️ **M3 暴露了子代理自己的测试 bug**：第一版顺序断言**锚错了**（锚 `checkCommitAdmission(` 调用而不是拒收早退那一行），改成锚 `DENIED_MARKER` 后才真正抓住——**这条留痕是因为「护栏自己写错」也是护栏体系的一部分** |

⚠️⚠️⚠️ **本批的两条诚实边界，必须连着上面的表一起读**：

1. **⚠️ 路径 B（`cancelActiveGroupRun` 让残留 job 进 `onSuccess`）没找到确证。**
   `stopGeneration` 会先 `cancelJobs()` + `join()`，被取消的协程走 `onFailure` 的
   C 分支 rethrow，`onSuccess` **不会执行**。**改动对两条路径都有效**
   （两个守卫是同款判据、同一个不变量），但⚠️ **只有路径 A（`abandonDanglingGroupRuns`）
   有实测支撑**，而且⚠️ **连路径 A 也只在 JVM 单测层面成立，从未在真机上复现过**。
2. **⚠️ 正常路径七种形态「不受影响」是逐条读代码论证 + 真单测，不是真机各跑一遍。**
   关键依据：`claimRound` 三条出口（`Acquired(freshRow)` / `Continued` /
   `Acquired(reclaimed)`）产出的 `state.runToken` 与写进 `group_runs` 的令牌
   **是同一个**（`persistClaim` 的 `insert` / `updateStatus` 都不碰 `run_token`）；
   `reclaimed` **只把 status 推回 RUNNING，不改 `roundId` 也不改 `runToken`**。
   单聊 / 正常推进 / 取消重跑 / 失败续跑 / 超时重跑 / 重新生成删消息切分支 /
   议长平票裁决 / 进程被杀后重启续跑 / 用户取消（**根本进不了 `failGroupTurn`**）。

⚠️ **本批新增三个源码文本护栏类（`GroupStaleJobCommitSourceGuardTest` 7 /
`GroupStaleJobFailureSourceGuardTest` 6 / `GroupTieBreakScaffoldingDropSourceGuardTest`
3 → 7）**——**为什么这么多文本护栏**：`commitGroupTurn` / `failGroupTurn` 都是
`private suspend` + 一堆 Hilt 协作者（`getConversationFlow` / `groupRunDAO` /
`saveConversation`），**JVM 单测构造不出来**，所以「函数体内必须有那处调用」
「调用必须排在某个副作用之前」这类**顺序与存在性**不变量只能读源码文本。
⚠️ **但能抽纯函数的部分子代理确实抽了**：`checkCommitAdmission` /
`checkFailureAdmission` / `advance` 三个都是纯判定内核，配了 **12 条真单测**——
**「判据的逻辑」有行为断言，只有「调用点在哪、排在哪」是文本护栏**。

⚠️⚠️⚠️ **本批的唯一硬结论：不改任何判定。** 三批修的是真缺陷，
但 **① 本轮零设备、零 `adb`、零 gradle**，契约 `:206` 四类产物**一份未增**；
**② ②③ 的触发前提本身只是读代码 + 核实 `cancelJobs()` 调用点**，不是观测。
**回归护栏证明的是「我们钉住了那条不变量」，验收证明的是「契约那四类产物在真机上
被采到了」——前者永远不能顶替后者。** 十例状态仍全 `unverified`。

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
  `committed_role_ids`（`:73`）。Room 已从 30 到 **32**，两级迁移**都是显式手写**的，
  都没用 AutoMigration，schema 已导出到
  `app/schemas/heizige.kk.khatkit.app.core.data.db.AppDatabase/32.json`：
  - **30→31** = `core/data/db/migrations/Migration_30_31.kt`，DDL 6 条（新建 `group_runs`
    + `memory_chunks` 加 `role_id` 与索引）。不用 AutoMigration 的理由在 `:79-84`：
    重建 `memory_chunks` 的 `DROP TABLE` 会删掉 `memory_chunks_ai/ad/au` 三个 FTS5
    触发器。对应 `31.json`。
  - **31→32** = `core/data/db/migrations/Migration_31_32.kt`，DDL **只有一句**
    （`CONVERSATION_ADD_GROUP_CARDS_SQL`，`:26-27`）：
    `ALTER TABLE ConversationEntity ADD COLUMN group_cards TEXT NOT NULL DEFAULT ''`。
    不用 AutoMigration 的理由**比 30→31 更硬**：这次要重建的是**父表**
    `ConversationEntity`，而 `message_node` 上有
    `ON DELETE CASCADE REFERENCES ConversationEntity(id)`（`Migration_11_12.kt:32`），
    主机侧重放实测 `foreign_keys=ON` 下 `DROP TABLE` 父表会把 `message_node` 存量消息
    **删光**（详见 B1）。对应 `32.json`，由 `:app:kspDebugKotlin` 真实导出。
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
- **C1-S 新增的主源文件**：`core/data/repository/ConversationSearchLikePattern.kt`
  （**72 行**）——LIKE 转义的唯一收敛点。含
  `CONVERSATION_LIKE_ESCAPE_CHAR: Char = '~'`（`:30`）、
  `CONVERSATION_LIKE_ESCAPE_SQL: String = " ESCAPE '~'"`（`:41`）、
  `escapeConversationLikePattern()`（`:64`，单趟遍历）。**转义字符为什么选 `~`**：
  不能是 `%` / `_`（包夹用的 `'%' … '%'` 自己会被当转义符，整条查询彻底坏掉）、
  不能是 `\`（SQL / Kotlin / JSON / 正则四层各有各的反斜杠规矩，MySQL 读者还会误读）、
  不能是标题常见标点（模式串被撑成两倍长）；`~` 同时躲开这三类，且是单字节 ASCII、
  ASCII 大小写折叠免疫。**规则：调用方一律传原始用户输入，谁都不许自己转**
  （两边都转会把 `a%b` 变成 `a~~~%b`）。
- ⚠️ `TavernChatCodec`（`core/data/ai/tavern/TavernChatCodec.kt`）上一版描述为
  「有往返内核但没有入口」——**现在 `exportGroupJsonl`（`:251`）有生产调用点**
  `feature/chat/GroupExportCard.kt:206`。这类「写好了但没人调」的判断要先 grep
  确认再下结论。

### 逐项状态

| 缺口（2026-10-04 版） | 代码状态 | JVM 证据（类 / 用例数） | 设备证据 |
|---|---|---|---|
| 1 契约字段 + 幂等续跑 | 代码层已实现。⚠️ **第五批（`2fdee352` / `8ccc0264`）给「续跑」补了两道守卫**：残留旧 job 不得推进已终态的轮次、不得把上一轮的产出盖到新轮 | `GroupTurnCoordinatorTest` 63、`GroupRunSchemaTest` 10、`UngeneratedMessageFilterTest` 18<br>⚠️ **（第五批后 `GroupTurnCoordinatorTest` 是 84；`GroupStaleJobCommitSourceGuardTest` 7 / `GroupStaleJobFailureSourceGuardTest` 6 是新增护栏）** | 无（`adb devices` 空）；仪器 `GroupRunDAOTest` 12 条从未跑过 |
| 2 版本化 config + 字段级校验 | 代码层已实现 | `GroupChatTest` 18（含 extras 往返、未知 schema 拒收） | 无 |
| 3 记忆接线 | 代码层已实现 | `MemorySpaceGateTest` 9 / `MemoryToolScopeTest` 7 / `MemoryAttributionTest` 9 / `GroupMemorySpacePolicyTest` 6 / `MemoryExtractorParseTest` 7 / `MemoryRoleIdMappingTest` 2 | 无 |
| 4 vote 结构化 | 数据结构层已实现，传输层仍是文本约定（见遗留 B5）。⚠️ **第五批（`e72793e6` / `8a5f89d7`）把平票裁决脚手架在 `cancelActiveGroupRun` / `abandonDanglingGroupRuns` 两条终态出口上的漏回收补上了**。<br>⚠️⚠️ **第十批（`a8fa1cb8` / `b96912cb` / `df560180`）把这一格从「部分」改判为「达成」**——原判据（「传输层是自由文本 + \`VOTE:\` 正则，不是 tool-call 强约束」）经对抗性审计**逐条核实后两条都剔除**：<br>① **tool call 根本不是契约要求**：契约 C1 段（`client-changes.md:198-206`）grep `tool` / `tool_call` / `toolCall` **零命中**（全文 326 行同样零命中），拿它当判据是自造判据卡人；<br>② **`tally` 压根不碰自由文本**：签名 `tally(ballots: List<VoteBallot>, candidates: List<String>, tiePolicy: String)`（`GroupChat.kt:856`）**没有任何正文参数**，`:863` 按候选集过滤、`:864` 按 `roleId` 去重（保留最新一票）、`:868` 按 `candidateId` 计数——「拿自由文本 majority 猜」不成立；`collectBallots` 也只把 `parseBallot` 的结构化结果喂进去。<br>✅ 所以契约 `:201`「vote 只接受结构化候选/票并按多数决输出」在**数据结构层与判定层都成立**。<br>⚠️ **但「判定达成」不等于「没有注入面」**：第十批真挖出并**修掉**一处此前没被记录的缺口——`collectBallots` **缺 `turn_kind` 门禁**，`TURN_ERROR` 节点会被收成票（详见下面 B5 追加段与第十批一节）。<br>⚠️ **本批不构成任何验收证据**：零设备、零 `adb`、契约 `:206` 点名四类产物一份未增，十例仍 **10/10 `unverified`** | `GroupTurnCoordinatorTest` 63 的投票/平票组<br>⚠️ **（第五批后是 84；`GroupTieBreakScaffoldingDropSourceGuardTest` 3 → 7）**<br>⚠️ **（第十批后：`GroupTurnCoordinatorTest` **85**（+1，`collectBallots` 拒收 `TURN_ERROR`）、`GroupChatTest` **26**（+4，`parseCandidates` 首次有直接 JVM 用例）；`:app:testDebugUnitTest` 全量 **110 类 / 886 例**（+5））** | 无（`adb devices` 空）；⚠️ **本批零设备**，`parseBallot → __summary__` 与「平票按配置失败」两条路径**真实调用仍然零份** |
| 5 导出 / 恢复 | 代码层已实现（含相机扫码入口），**角色卡元数据已真落库**（见 B1） | `GroupTavernExportTest` 22 / `TavernCompatTest` 21 / `QrScannerSheetTest` 16 / `GroupChatTest` 18 / `GroupRoleCardsPersistenceTest` 7 / `ConversationGroupCardsSchemaTest` 7 | 无；酒馆本体打开 `.jsonl`、真机相机扫码、导出文件 SHA-256 三项全未验；`Migration_31_32_Test` 6 条未跑 |
| 6 UI 复用管线 + 头像组 + 筛选 | 代码层已实现，一行 Compose 未上屏。**本轮还修掉了这条路上的两个真实缺陷**：5 条搜索查询的 LIKE 未转义（已加 `ESCAPE`）、14 处 `ORDER BY` 缺 `id` 兜底（已补）。⚠️ **第六批（`33eb801e` / `71d1439e`）修掉了群配置面板两个真机必崩并在真机上复验过**（面板 15 个字段全渲染 + 5 滚 3 反不崩），⚠️ **但「一行 Compose 未上屏」这句话现在只对「群配置面板以外」成立**——**成员头像组 / @ 选择器 / 抽屉筛选 chip 三项仍一项都没上过屏**，面板里的保存 / 二维码 / 扫码三个业务动作也一个都没点 | `ConversationListQueryPlanTest` 7 / `ConversationTypeFilterSourceGuardTest` 2 / **`ConversationSearchLikePatternTest` 12** / `GroupSpeakerResolverTest` 8 / `GroupRoleCompletionProviderTest` 8 / `GroupChatTest` 22 | ⚠️ **部分有值**：`GroupConfigSheet` 的渲染与滚动已在真机验证（`KedgeStyle.MD3Exp`），⚠️ **Miuix 未验、业务动作未点、头像组 / @ 选择器 / 筛选 chip 零份**；`ConversationDAO` 的 SQL 在**真机 Android SQLite** 上的行为也未验（**主机侧**已由 C1-S 重放钉住 438 条断言，见 B7） |
| 7 构建与退出码 | **已拿到硬证据** | 见 `docs/eval/c1-group-chat.md`「构建与验证证据」（三条命令退出码全 0） | 不适用 |

⚠️ **读上面这张表之前先知道一件事：本轮改了一行既有测试断言的期望串。**
`app/src/test/java/heizige/kk/khatkit/app/feature/chat/ConversationTypeFilterSourceGuardTest.kt`
第 68-73 行那条断言的期望串，从

```kotlin
"title LIKE '%' || :searchText || '%'\" + CONVERSATION_TYPE_PREDICATE_SQL"
```

改成

```kotlin
"title LIKE '%' || :searchText || '%'\" + CONVERSATION_LIKE_ESCAPE_SQL + CONVERSATION_TYPE_PREDICATE_SQL"
```

原因：SQLite 文法要求 `ESCAPE` **必须紧跟 LIKE 的右操作数之后**，所以缺陷 1 的修法
（给搜索查询加 `ESCAPE`）只能把 `ESCAPE` 插在 LIKE 片段与 type 谓词常量**之间**；而
那条断言原本用**逐字节相邻**表达「type 谓词不允许出现第二份副本」的意图。
**断言的结构合法地变了，意图没变，而且改完更严**——一次同时钉住 ESCAPE 子句的
**存在 + 位置**与 type 谓词的**相邻**（变异 M1 塞第二份谓词字面量 → FAIL；M2 抽掉
搜索路 ESCAPE → FAIL）。该文件**只改了这一行**（`6982869b` 的 `git diff --stat` =
**1 insertion / 1 deletion，单 hunk**），**三条断言一条没被削弱或删除**，`tests` 属性
仍是 2。

⚠️ **它抓不到转义字符写错（变异 M3）：那要改
`ConversationSearchLikePattern.kt`，而这条护栏只读 `ConversationDAO.kt` 与
`ChatDrawerViewModel.kt` 两个源文件，`const` 的值永远不会被折叠进源码文本，所以它
原理上抓不到、判绿。** M3 由
`ConversationSearchLikePatternTest.escapeSqlLiteral_matchesTheEscapeCharacterConstant`
抓住。**所以期望串里刻意不写转义字符的字面值**——写了会造成「DAO 护栏钉住了转义
字符」的**假覆盖**（第一次尝试正是这么写的，因为 DAO 里 ESCAPE 是**常量引用**而断言读
的是**原始源码文本**，那个期望串**必然 MISS、测试仍红**，改为引用常量标识符才对）。
⚠️ **也别把这条读成「锚点之后第一次改既存测试文件」**——`09b4764b`（重设计
`GroupTurnModelTest` 第 14 条跨侧断言）、`e4fc4644` / `90d1dbac` / `c940813b` 都改过；
那几次改的是断言的**设计或强度**，这一次改的是**期望字面串**。
完整来龙去脉见 `docs/eval/c1-group-chat.md`「C1-S 抽屉 type 筛选主机侧重放与两个真实
缺陷修复（零设备）」的「⚠️⚠️ 敏感项」。

### 真正没做完的事

**A. 需要设备的（拿不到就无法验收）**

- 契约 `:206` 点名的**那四类**硬证据，磁盘上确实**零份**：各 viewer 可见消息 ID
  台账、真实模型调用序列、真实 token（prompt+completion）计数、群聊导出文件
  SHA-256——**这四类一件都不存在**。采集手段见 `docs/eval/c1-group-chat.md` 的
  「下一位怎么把 unverified 变成 verified」。
  ⚠️ 别把这条读成「C1 全仓零证据」，那也不对：本节末两条另登记了迁移侧的
  **两条主机侧部分**证据（30→31 重放 85 条断言全过、31→32 重放 49 条断言全过）。
  两者不矛盾——
  **四类零份**说的是 `:206` 点名的产物清单，**主机侧部分**说的是迁移正确性，
  后者**不覆盖这四类中的任何一类**，所以十个用例状态仍是全 `unverified`。
- 仪器测试 **25 条从未跑过**：`app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/dao/GroupRunDAOTest.kt`
  12 条 + `.../core/data/db/migrations/Migration_30_31_Test.kt` 7 条 +
  `.../core/data/db/migrations/Migration_31_32_Test.kt` **6 条**（C1-P 新增，
  已 grep `@Test` 计数确认）。磁盘上唯一的 app 仪器记录是 2026-10-03 12:58:29 的一次
  `Process crashed`
  （`tests="0"`，退出码 1，OnePlus `PKG110`），**早于 C1 基线 `d45ebd10` 13.5 小时**，
  不能当 C1 证据。
- 零设备环境事实：一行 Compose 都没在屏幕上跑过。群聊页整页渲染、成员头像点击手感、
  `@` 弹层与 workspace 补全的互斥、导出面板真机分享、扫码弹层的相机权限，
  全是零证据。`ConversationDAO` 的 `:type = ''` 不筛与 `itemCount` 正确性也未在
  **真机 Android SQLite** 上验过——`app/build.gradle.kts` 单测只有 `libs.junit`
  （无 Robolectric / room-testing / coroutines-test），`ConversationRepository` 是 class
  直接依赖 DAO，没法注入 fake。
  ⚠️ **口径订正（2026-10-05，本轮）**：这一条**主机侧已经被钉住了**——C1-S 重放
  438 条断言全过，`:type = ''` 不筛、正确筛选、两路口径一致、筛选后 count 正确、
  分页无重复无遗漏都验过。**但证据全部来自 CPython 内置的 SQLite（3.51.2），不是
  Android 的 SQLite**，且分页是脚本**自己拼 `LIMIT`/`OFFSET` 机械模拟**的，
  `PagingSource` 的并发失效 / 快照一致性 / 回滚没验。所以这条只能从「零证据」收窄成
  「**主机侧有证据、真机侧零证据**」，**不能算已验**。
- **有一份零成本动作已经做完**：`tools/verification/c1d_migration_30_31_replay.py`
  （39,231 字节 / 776 行，sha256 `7cd9c3fbc22c5e8da9248c061feda8fe860bb86c5011eba1c2399b405e8199c6`；
  用真实 SQLite 重放 30→31 迁移 SQL）**已运行两次，退出码都是 0**，两次输出逐字节
  相同（各 9,236 字节 / 104 行，sha256 `093981c461d2533285e3f2a0a635d1b0d4519e6bfcb9be39c8f4f71a74d2e7d5`）。
  脚本在 `1649f5c7` 里从 45 条断言扩到 **85 条并修掉 4 个弱点**（含一条恒真断言、
  一条硬编码 `True` 的假断言），实测 85 条全过：其中 **FTS5 触发器相关 12 条**
  （第 4 组「迁移后逐字节存活 + INSERT/UPDATE/DELETE 三向真同步」10 条，
  加第 1 组迁移前基线 2 条：3 个触发器在 `sqlite_master` 里、且与 `onOpen` 逐字节相同），
  另含第 5 组 **7 条 DROP TABLE 对照实验断言**（证明对照路径确实丢触发器）。
  命令原文、退出码、逐组条数分布与变异测试证据见 `docs/eval/c1-group-chat.md` 的
  「C1-D 迁移 30→31 主机侧重放」。
  ⚠️ 但它**不能替代 `Migration_30_31_Test`** 的 7 条仪器用例（仍需设备）——
  别拿它把仪器那条勾掉。
- **第二份零成本动作也已做完**：`tools/verification/c1p_migration_31_32_replay.py`
  （21,613 字节 / 387 行；复用 C1-D 脚本的 SQL 拆分器与断言收集器）用真实 SQLite
  重放 31→32 迁移 SQL，**退出码 0**、`RESULT: 全部断言通过（共 49 条）`。
  逐组分布：环境自检 3、建 31 版真库 5、执行迁移 3、原地 ADD COLUMN 结构判据 10、
  旧行不丢 7、blob 可写可读回 3、FTS5 触发器存活 + 外键仍在 9、
  **DROP TABLE 对照实验 8**、与 `32.json` 逐字段一致 1。变异检验：把 DDL 改成
  `TEXT`（可空、无默认）后 **6 条 FAIL / 退出码 1**，未改动的基线副本退出码 0。
  命令原文、退出码、逐组条数、变异检验与「对照实验实测 CASCADE 删光 `message_node`」
  的完整输出，见 `docs/eval/c1-group-chat.md` 的
  「C1-P 角色卡元数据落库与 Room 31→32 迁移（零设备）」。
  ⚠️ 同样**不能替代 `Migration_31_32_Test`** 的 6 条仪器用例（仍需设备）。
- ⚠️⚠️ **第五批（`e72793e6..8ccc0264`，2026-10-06，HEAD `361c7cf6`）给 A 段加了一条
  新的设备侧缺口，四类产物仍一份未增。** 本批修的是三个真缺陷（平票脚手架在两条
  终态出口上漏回收、残留旧 job 跨轮盖戳 / 跨轮写 `FAILED`），**零设备、零 `adb`、
  零 gradle**，所以：
  - **契约 `:206` 那四类产物本轮一份未增**，上面每一条的状态**一个字没变**。
  - ⚠️ **新增的设备侧缺口：残留旧 job 那两条路径的真机复现**（见 B11）。
    要在同一会话里**人工编排时序**（长生成在飞 → 中途发新消息把它判死 → 等旧 job
    跑完，或反过来先超时）。⚠️ **这不是「跑一遍测试」能覆盖的**——
    `abandonDanglingGroupRuns` **不取消任何 job** 这个前提本身要靠真机观测确认。
  - ⚠️ **本轮一行 `androidTest` 都没动**（全量 `@Test` 仍是 **61**）：
    这三个缺陷恰好是**仪器测试原理上覆盖不到**的那一类——要在**真实协程取消 +
    真实流式响应 + 真实 DAO 时序**下才可能复现，所以「没动 androidTest」不是漏做，
    是**没有可写的仪器用例**。
  - ⚠️⚠️⚠️ **第六批（`33eb801e..a74c1820`）给 A 段加了几条新的设备侧缺口，
    契约 `:206` 四类产物仍一份未增。** 本批修的是两个真机 UI 必崩 + 一个 Coil 崩溃 +
    四项代码债，**契约那四类产物一份未增**，所以上面每一条的状态**一个字没变**。
    新增的设备侧缺口，逐条：
    1. **Coil 修复的真机效果**（B15）——本轮**按约束不碰设备**，「全量仪器测试能跑完」
       这句话**目前零证据**。验证只需一条 `./gradlew --offline :app:connectedDebugAndroidTest`。
    2. **Miuix 风格下的群配置面板零验证**（B12）——两个崩溃都只在 `KedgeStyle.MD3Exp`
       触发过（读代码结论），**Miuix 下这个面板的行为完全没测**。
    3. **面板的业务动作零点击**（B12）——本轮只验了**渲染与滚动**，**保存 / 生成二维码 /
       扫码导入三个动作一个都没点**；⚠️ **扫码那条同时覆盖 C1-09 的相机链路**
       （CameraX + MLKit 至今没在设备上跑过）。
    4. **四项代码债的运行时零观测**（B16）——`require` 真不真抛 / 分批循环真机行为 /
       `remove(key, value)` 的真实并发效果，都要在真机上造出对应时序才能验。
    5. ⚠️ **「真机 UI 端到端仍然零份」这一条的口径本轮收窄了一点点、但没消失**：
       群配置面板的**渲染 + 滚动**在真机上验过了（`uiautomator dump` 15 个字段全在、
       5 滚 3 反不崩），**但成员头像组 / @ 选择器 / 抽屉筛选 chip 三项一项都没做**，
       面板业务动作也没点。**所以 C1-10 / C1-01 的 UI 那一半仍然是零证据。**

**B. 已知遗留与风险（代码层，需要产品/架构决策）**

- **B1「角色卡只显示不落库」已作废——现在是真落库**（2026-10-05，8 个 commit
  `20581bcb..b7025665`）。早前那条「扫码/粘贴后只显示、不入库」与「要落库得动
  `Conversation` + `ConversationEntity` + `ConversationDAO` + Room 31→32」**都已完成**，
  别再照抄旧描述。
  `GroupSharePayload.cards`（`core/data/model/GroupChat.kt:115`）里的 `RoleCardMeta`
  （同文件 `:96`）现在随群配置一起入库：
  - **数据通路**：导入成功（`GroupImportResult.Accepted`）时
    `GroupChatPage.kt:498` 把 `result.payload.cards` 交给 `onSave`，落到
    `Conversation.groupCards`（`core/data/model/Conversation.kt:48`，
    `List<RoleCardMeta>? = null`）→ `ConversationEntity.groupCards`
    （`core/data/db/entity/ConversationEntity.kt:52-53`，
    `@ColumnInfo("group_cards", defaultValue = "")` 的**单列 String**）；编解码在
    `GroupChat.encodeRoleCards`（`GroupChat.kt:326`）/ `decodeRoleCards`（`:339`）。
    `''`（从没导入过，解码回 `null`）与 `"[]"`（导入过但零张卡）是**两个不同的值**，
    别合并。`ConversationRepository` 只在**整行映射**里编解码（`:405` 编 / `:429` 解）。
  - **Room 31→32，迁移是显式手写**的 `core/data/db/migrations/Migration_31_32.kt`，
    DDL **只有一句**（`CONVERSATION_ADD_GROUP_CARDS_SQL`，`:26-27`）：
    `ALTER TABLE ConversationEntity ADD COLUMN group_cards TEXT NOT NULL DEFAULT ''`。
    注册在 `AppDatabaseFactory.addMigrations`（`:29`），`@Database(version = 32)`
    （`AppDatabase.kt:61`），`32.json` 已由 `:app:kspDebugKotlin` 真实导出。
    `31.json` → `32.json` 差异：version 31→32、identityHash
    `b5fee805907e4754211836b77e78e3c7` → `9f1437f7e85ba5c591a31a3c5cb73de1`、
    表集合不变（15 张里只有 `ConversationEntity` 变）、索引 / 主键 / 外键不变、
    列数 15→16、新增字段 `{fieldPath: groupCards, columnName: group_cards,
    affinity: TEXT, notNull: true, defaultValue: ''}`。
  - ⚠️ **本次最重要的技术结论：这次比 C1-D 那次更不能用 AutoMigration。**
    C1-D 重建的是 `memory_chunks`，`DROP TABLE` 把三个 FTS5 触发器带走；
    **本迁移要重建的是父表 `ConversationEntity`**，而 `message_node` 上有
    `FOREIGN KEY (conversation_id) REFERENCES ConversationEntity(id) ON DELETE CASCADE`
    （`core/data/db/migrations/Migration_11_12.kt:32`）。主机侧重放实验已证实
    （`PRAGMA foreign_keys=ON`）：`DROP TABLE ConversationEntity` 等价于对父表做一次
    隐式 `DELETE`，CASCADE 会把 `message_node` 的**存量消息行全部删光**
    （实测 `before=1 after=0`），而父表数据又被拷回去——结果是
    **「会话还在、消息全没了」**。Room 自动迁移恰好走「建 `_new_` 表 → 拷 →
    `DROP TABLE` → `RENAME`」这条路。`ALTER TABLE ADD COLUMN` 则不动旧表 / rowid /
    索引 / 触发器 / 外键（实测 b-tree `rootpage` 迁移前后都是 `2`）。
  - **「导入的卡片」vs「现场生成的卡片」优先级决策（已定案）**：**导出永远用现场
    生成的**（`groupExportRoleCards`，`feature/chat/GroupRoleCards.kt:43`），导入快照
    **只落库 + 展示**，不参与导出。理由五条：persona 真值在本机（现场生成取
    `role.assistantId` 指向的本机助手 `systemPrompt`，`GroupRoleCards.kt:52`）；
    导入快照的 persona 属于**导出方那台机器**、多半已过期、还可能指向本机不存在的
    助手；`RoleCardMeta.cardId` 全仓**无人解析成角色卡实体**，只是原样透传的 Tavern
    引用（`GroupRoleCards.kt:21-22`）；`avatarRef` 现场生成**恒为 null**（`:28-29`）；
    现场生成**永不缺卡**（`assistantId` 非法或助手被删也照样出一张，`persona` 退成空串，
    `:31-35`）。**不合并、不静默替换**——面板里「已导入的角色卡」
    （`ImportedRoleCardsView`，`GroupChatPage.kt:731`）与现场生成那段**分开呈现、各自
    标注来源**。手动「保存群配置」传 `null`（`:648-650`）= 本次与角色卡无关，
    保留库里已有的一份；非 `null` 才整体替换（空列表也替换）。
  - **一处「刻意不读」是有意的，不是漏了**：抽屉轻量投影
    `conversationSummaryToConversation`（`ConversationRepository.kt:475`，KDoc
    `:464-474`）与 `LightConversationEntity`（`:546`，KDoc `:539-545`）都**不含
    `group_cards`**——抽屉 7 条查询全是手写列名列表，把可能很长的 persona blob 拉进
    每行只是白付内存 / 带宽。子代理核实过抽屉侧写操作（置顶 / 移助手 / 移文件夹）
    全是**单列 UPDATE**（`ConversationDAO.updatePinStatus` / `updateAssistantId` /
    `updateFolderId`），重生成标题也先用 `getConversationById` 取回完整会话，
    **没有一条路径拿轻量对象整行覆盖**，所以不读不会抹掉已落库的卡片（已做成文本护栏
    测试 `ConversationGroupCardsSchemaTest.repositoryMapsGroupCardsOnTheFullRowOnly`）。
    `ConversationDAO.kt` **零改动**；既有测试文件也**零删改**（`20581bcb..b7025665` 的
    `git diff --stat` 只有 3 个新增测试文件）。
  - **仍然未验证（别当已完成）**：① 真机 31→32 升级——仪器 `Migration_31_32_Test`
    6 条**一次没跑**；② `ConversationDAO` 在真实 SQLite 上的行为仍未验（`:type = ''`
    不筛 / `itemCount` 正确性依旧零设备）；③ **UI 刷新后卡片是否真在**——一行 Compose
    都没在屏幕上跑过；④ 迁移脚本的 `rootpage` / `sqlite_master` 两条判据依赖宿主 SQLite
    ≥ 3.35 的 `ADD COLUMN` **原地快路径**（本机 3.51.2 满足），**真机 Android 捆绑的
    SQLite 版本未知**，若它是 3.25–3.34 则 `ADD COLUMN` 同样是整表重写/重建，
    手写 ALTER 的优势不成立。
- B2 抽屉搜索路的 `folder_id` 口径与未归档路不一致 —— ⚠️⚠️ **2026-10-05 已由用户
  拍板定稿：选方案 B（保持现状）。不再是待决项。**
  - **现状（已被测试钉住，`ConversationDrawerFolderScopeTest` 4 条）**：无搜索词走
    `getUnfiledConversationsOfAssistantByType`，`WHERE` 里有 `AND folder_id = ''`；有搜索词走
    `searchConversationsOfAssistantByType`，`WHERE` 里**没有** `folder_id` 条件。所以无搜索词
    时主列表只显示未归类会话，一输入搜索词文件夹内的会话就混进同一份列表。
  - **判断：这是遗漏，不是有意设计**（依据见该测试的 KDoc）：`folder_id` 由 `a92248b2`
    引入，那条 commit 给了**新建的**未归档查询 `folder_id = ''` 却没动**当时已存在的**搜索
    查询；搜索进抽屉是后来 `48c2c091` 那个大杂烩 UI commit 顺手加的（message 无一字提到
    文件夹），且它的 `when` 把 `keyword.isNotBlank()` 放在第一分支、直接压过当时的
    `_selectedFolderId`；`ad7ac808` 把抽屉里的文件夹选择整个删掉（改为二级页）、主列表从此
    只表示未归类会话时，搜索路没跟着收窄，message 同样一字未提。`2f1d04a2`（C1）给搜索路
    补 `type` 谓词时 KDoc 明写「与未归档路完全一致」——第四次把两条路当同一口径维护，
    `folder_id` 恰好漏掉。
  - **方案 A：搜索也收窄成 `folder_id = ''`**（与未归档路完全同口径）。
    - 代价：文件夹里的会话在抽屉里**彻底搜不到**；二级页（`FolderDetailPage` /
      `FolderDetailViewModel`）**目前没有搜索框**（只读 `getConversationsOfFolderPaging`），
      收窄后这些会话唯一的查找入口是逐个文件夹翻，跨文件夹搜一个词做不到。
    - 收益：抽屉主列表语义终于单一（「未归类」），搜索结果不会跳到列表里根本没有的条目。
  - **方案 B：保持现状（搜索跨全部文件夹），但把语义写进代码与文档**。
    - 代价：主列表与搜索结果口径不同这件事只存在于开发者脑子里，UI 上没有任何提示；
      搜出来的文件夹会话在主列表里看起来像「凭空出现」。
    - 收益：搜索是全局的，符合多数用户搜一个词就期望翻遍全部的直觉。
  - **推荐：方案 B + 显式化**。理由：方案 A 会让一批会话**不可检索**，而抽屉里没有任何
    替代入口，这是功能回退；方案 B 的代价只是认知不一致，可以用注释/文档消解。
  - ✅ **已定稿（2026-10-05，用户拍板）：方案 B，抽屉搜索保持跨全部文件夹。**
    **`ConversationDAO.kt` 一行未动**；`ConversationDrawerFolderScopeTest` 那 4 条测试
    钉住的现状**就是最终口径**。所以「改口径会先让测试变红」这条**仍然成立**——
    那是**预期信号**，不是回归。⚠️ **方案 A 的代价说明继续保留**（不要因为定稿就删）：
    收窄会让已归档会话**彻底搜不到**，而二级页（`FolderDetailPage` /
    `FolderDetailViewModel`）**目前没有搜索框**（只读
    `getConversationsOfFolderPaging`），跨文件夹搜一个词做不到。这正是选 B 的理由。
    ⚠️ **B 方案里「显式化」那一步还没做**：`ChatDrawerViewModel` 的 KDoc 尚未补
    「抽屉搜索跨文件夹」这条语义。按拍板结论那是**文档化工作**，不是口径变更，
    可以之后单独排。
    ⚠️ 「这是遗漏，不是有意设计」这个**技术判断不变**——B 方案被选中不等于那个判断
    被推翻，只等于**代价可接受、决定不改**。别把这两件事混成一句。
  - **仍未验证**：DAO 的两条 SQL 在**真机 Android SQLite** 上的行为依旧零设备证据
    （仓库 `testImplementation` 只有 junit，没有 Robolectric / room-testing，见
    `ConversationTypeFilterSourceGuardTest` 的同一说明）。⚠️ 本轮 C1-S 重放的 G6 组
    **4 条断言在主机侧观测过这个 `folder_id` 口径**（两条路确实不一致），
    **那只是把现状钉成实测**：方案 A / B 的取舍**一个字没改**。
    ⚠️ **2026-10-05 之后补一句**：真机（OnePlus `PKG110` / Android 16）虽然已经接上，
    但**这两条 SQL 至今没有在真机上被观测过**——真机窗口采的是 C1 群聊那一侧
    （Room 库、导出、仪器测试），**没有覆盖抽屉查询**。上面所有依据来自源码文本、
    git 历史与主机侧重放，**不是真机运行时行为**。
- B3 ~~`GroupRole.modelId` 显示侧与生成侧现在同口径~~ **已关闭（`c7535ca8`）：两侧本来
  就是两个问题，而「同口径」是错的。** 生成侧 `ChatManager.kt:726` 的
  `resolveGroupTurnModelId`（定义在 `feature/chat/GroupTurnModel.kt:54`）答「现在要用
  哪个模型」，显示侧 `ChatList.kt:215` 的 `resolveMessageModel` 答「这条消息当时是被
  哪个模型答的」。后者已改成 `message.modelId` 优先、缺失时才回落角色绑定：
  `41642ecd` 之前生成的群聊消息记的是**助手**绑的模型（那才是当时的真相），而
  `GroupRole.modelId` 是用户可改的配置，拿它做数据迁移回填**零信息增量**。
  两侧判据从此**故意**不一致。遗留：真机上验证「改过绑定后老消息仍显示老模型」
  **零设备证据**。
- B4 ~~`ChatManager.kt:681` / `:686` 的 `senderName` 与 `useExternalWebSearch` 仍是
  会话级模型~~ **已关闭（`ed21db6e`）：这是真 bug，但只有 `senderName` 那一半。**
  - **`senderName` 确实是 bug，已修**：它唯一的**显示**消费点是后台「生成完成」通知的标题
    （`ChatNotificationManager.kt:121` `title = senderName`，由 `:889` / `:923` 两处
    `AppEvent.ChatGenerationEnded` 带出）。群聊一轮里每个角色的 `assistant_id` /
    `model_id` 都可能不同，会话级的值只说明「这个群属于谁」，不说明「这条回复是谁写的」
    ——三个角色各用一个模型时，通知标题会永远显示同一个名字。现已把取值抽成纯函数
    `resolveNotificationSenderName`（`ChatManager.kt`，公式逐字来自 `d61eefde`，与气泡
    头像区 `ChatMessageAvatar.kt:86-131` 同一口径），并在群聊分支重算完 `assistant` /
    `model` **之后**重新赋值。单聊走不到那个分支，取值与 C1 之前逐字相同。
  - **`useExternalWebSearch` 不是 bug，不改**：它唯一的消费点是群聊分支**之前**的
    「工具不可用」告警，那里的 `model` 同为会话级，两者自洽。真正决定「本轮要不要下发
    外部搜索工具」的是 `chatToolFactory.createTools(...)` 内部按传入的 `(assistant, model)`
    再算一次（`ChatToolFactory.kt:151`），而那个调用点在群聊分支**之后**、拿的是本轮
    真实的模型——工具下发本身没有错位。要改只能把告警整体挪到群聊分支之后，那会改变
    「群聊但本轮无发言者时是否还弹这条告警」的行为，属于未授权变更。
  - **仍未验证**：这条路径（`handleMessageComplete`）是 `private suspend` + 一堆 Hilt 协作者，
    JVM 单测构造不出来，所以纯函数只能证明**公式**，证明不了「调用点在正确位置」——
    位置用一条**源码文本护栏**钉住（`ChatManagerNotificationSenderNameTest.
    groupBranch_recomputesSenderName_afterResolvingModel`，已做变异检验：删掉重算即 FAIL）。
    **真机后台通知标题在群聊下是否正确显示，仍零设备证据。**
  - **第三个入口的同款副本已消掉（`88ba63c2`，2026-10-05）**：早前这里记的是
    「`ChatService.kt:624` 有一份**逐字相同**的 `senderName` 计算，那个类的
    `handleMessageComplete` 里完全没有群聊分支（`grep -c groupConfig` = 0），走不到群聊
    路径，因此不需要改；将来若把群聊能力搬进那个类，这条会重新变成 bug」——
    **前半句仍然成立，后半句的处理方式改了**。那个类（`core/service/ChatService.kt`，
    第二个 `handleMessageComplete`，同样发 `AppEvent.ChatGenerationEnded`）原先逐字抄了
    第二份内联公式，现已改成调同一个 `resolveNotificationSenderName`
    （`feature.chat` 与 `core.service` 同属 `:app`，`internal` 跨包可见，无需改可见性）。
    **公式现在全仓只有一份实现**（`ChatManager.kt:166`）。之所以值得单独做而不是留着：
    那份副本**不会**被 `ed21db6e` 覆盖——主入口修了、副本不修，两份实现各自漂移，正是
    B4 那类 bug 的温床。
  - **护栏（`8bc28105` / `c940813b`）**：`ChatServiceSenderNameGuardTest` 3 条。
    ① 遍历全仓约 1476 个 `src/main/**/*.kt`，断言「按 `useAssistantAvatar` 分支」这个
    形状**只在 `ChatManager.kt` 一处**出现（任何地方再抄一份内联公式立刻红）；
    ② 两个生成入口都必须调共享纯函数；③ 一旦 `ChatService.kt` 出现任一群聊上下文标记
    （`groupConfig` / `takeGroupTurn` / `GroupTurnEntry` / `SpeakerStep` /
    `resolveGroupTurnModelId` / `groupStep`），就要求 `senderName` 是 `var` 且**至少重算
    两次**。第 ③ 条拦的是「搬了群聊却忘了重算」这个具体错法，一次**正确**的搬运能过。
    **变异检验 3 次全部 FAIL 且退出码 1**（注入群聊分支 / 注入群聊分支 + `val`→`var`
    但不重算 / 把公式抄回内联），其中第 2 次是**护栏自己暴露出的真实缺口**（原先重算
    那半条断言被现状断言短路成死代码，且单次赋值能满足位置检查），已补「至少两次赋值」。
    细节、还原方式（**文件备份 + `sha256sum -c`，未用任何 git 命令还原**）与命令退出码见
    `docs/eval/c1-group-chat.md` 的「C1-P 第二生成入口的通知标题护栏（零设备）」。
  - **仍可能出的错**：护栏是**源码文本**护栏。把公式塞进 `when` 而不是 `if`、改判定
    所在文件名、或把判定挪进别的模块，都可能让正则失配。它防的是「顺手抄一份」这个最
    可能的错法，**不是**「证明不存在第二份实现」。
  - **`ChatService` 该不该留一个独立生成入口，本轮没有结论**——只消掉了重复公式，没评估
    两个入口是否该合并（那是未授权的架构变更）。
- B5 vote 选票仍靠 `VOTE:` 前缀正则匹配模型自由文本（`GroupChat.kt:605-617`），
  不是 tool-call 强约束。契约 `:201` 说「只接受结构化候选/票」——**数据结构层面
  已满足**（`VoteBallot` / `VoteOutcome` 在 `GroupChat.kt:133` / `:141`，
  `GroupTurnCoordinator.kt:438` 的 `resolveVote` 只从 `plan.candidates` 取候选、
  不猜自由文本），**传输层面仍是文本约定**。这是已知弱点，不是「已完成」。
  ⚠️⚠️ **第十批（`a8fa1cb8` / `b96912cb` / `df560180`）审计判断（非实证验收）**：
  上面这个判据**被驳倒了**，整条降级为「文本约定」的说法不成立；但同一次审计**挖出一处
  真实的选票注入缺口并已修掉**。逐条如下（**这一整段是审计判断，不是任何用例的验收证据**）。

  **① 原判据两条依据，逐条核实后全部剔除**（所以「逐项状态」第 4 行的判定从「部分」改成
  「达成」）：
  - **「不是 tool-call 强约束」不构成不达标**。契约 C1 段（`client-changes.md:198-206`）
    grep `tool` / `tool_call` / `toolCall` **零命中**（全文 326 行同样零命中）。契约要求的是
    「vote 只接受结构化候选/票并按多数决输出」，**从没要求 tool call**。拿一个契约没写过的
    机制当判据，是自造判据卡人。
  - **「拿自由文本 majority 猜」不成立**。`GroupChat.tally` 签名是
    `tally(ballots: List<VoteBallot>, candidates: List<String>, tiePolicy: String)`
    （`GroupChat.kt:856`）——**没有任何正文参数**，判定全程只碰 `VoteBallot` 结构体。
    `:863` 按 `candidateId in candidates` 过滤，`:864` `associateBy { it.roleId }` 按角色去重
    （保留**最新**一票），`:868` `groupingBy { it.candidateId }.eachCount()` 计数。
    `collectBallots` 也只把 `parseBallot` 的返回值喂进 `tally`。自由文本**没有任何一条路径**
    能进裁决。

  **② 真正剩下的缺口：`collectBallots` 没有 `turn_kind` 门禁**（已修，`b96912cb`）。
  完整链路（每一环都实读过源码）：
  - `GroupTurnCoordinator.errorNode`（`:510-526`）产出的是 `role = ASSISTANT` +
    `roleId = failedRoleId`（**真实成员 id**）+ `turnKind = TURN_ERROR`，正文直接拼
    `"[名字] 本轮生成失败：$detail"`；
  - `detail` 来自 `ChatManager.errorDetailOf`（`ChatManager.kt:2258-2262`）=
    `"$type: ${error.message?.take(200)}"`，而 provider 习惯把**上游原始响应体**塞进异常消息
    （`openai/ChatCompletionsAPI.kt:106`、`claude/ClaudeProvider.kt:315`、
    `google/GoogleProvider.kt:202`、`google/InteractionsAPI.kt:95`）；
  - **可达性（这是它能成为注入面的原因）**：失败角色**不进** `committedRoleIds`
    （`fail`，`:469-483` 的 KDoc 自己写着「失败角色**不**进 committed，再次触发同一 `round_id`
    时从失败角色继续」），所以重试同一 `round_id` 时它会**重跑**，而**陈旧的 `errorNode`
    仍留在 `roundMessages` 里**；若它这次成功却**没吐** `VOTE:` 行，那条注入行就成了这个角色
    **唯一**的一票；
  - 修复前的 filter 只判 `it.role == MessageRole.ASSISTANT && it.roleId in roleIds`，
    `TURN_ERROR` 两个条件**都过**，于是被 `mapNotNull { parseBallot(...) }` 收成票。
    ⚠️ **既有那条用例钉不住它**：`the error node records the failure without inventing assistant
    prose`（`GroupTurnCoordinatorTest.kt:436`）的 `assertFalse(text.contains(BALLOT_PREFIX))`
    **只对 `detail = "…boom"` 成立**——钉的是 `errorNode` 的**模板**，不是**注入面**。
  - ✅ **修法（一行 filter）**：`collectBallots` 的 filter 追加
    `&& it.turnKind != GroupChat.TURN_ERROR`。**逐字沿用既有约定**——`ChatManager.kt:2359`
    的 `roundOutputPresent`（`:2349`）已经是同一个负向判据，同一个 null-safe 口径
    （`turnKind` 可空时 `null != TURN_ERROR` 为真，**旧消息仍算产出**）。
  - ✅ **先写红测试再修**（`a8fa1cb8`）：红测试失败消息原文——
    `java.lang.AssertionError: 失败角色的错误节点必须被 collectBallots 拒收（否则陈旧注入行能顶替它这一轮的一票） expected:<[alice, carol]> but was:<[alice, bob, carol]>`。
  - ⚠️ **最小性**：只挡 `TURN_ERROR` 就够。`turnKind` 全部取值逐个见第十批一节；
    **没有发现第二种 `turnKind` 也会被收成票**，所以没有「修法不够」的情况。
    `TURN_VOTE_SUMMARY` 本来就被 `SUMMARY_ID` 挡掉（`voteSummaryMessage` 用 `roleId =
    SUMMARY_ID`，`validate` 又拒绝任何 `role_id == SUMMARY_ID` 的配置），**没有顺手再加一道**。

  **③ 顺带核实出的两处覆盖缺口**（`df560180`，**本轮只补测试、不改行为**）：
  - **`GroupChat.parseCandidates` 此前零直接 JVM 用例**。所有投票用例都显式给
    `config.voteCandidates`，这条正则分支从没被直接测过。现补 3 条（正常形态 / 大小写 /
    空文本 / 格式错误 / Markdown 修饰 / `(?im)` 逐行声明）。
    ⚠️ **核实结论：审计说的「`(?im)` 的 `m` 会让 `候选：` 在正文任意一行被当声明」——成立**。
    正则是 `(?im)^\s*(?:候选|候选项|CANDIDATES?)\s*[:：]\s*(.+)$`，`m` 让 `^`/`$` **逐行**匹配；
    实证（断言空候选集）时实际拿到 `[evil-a, evil-b]`。**但按真实信任边界读，它不构成越权
    注入**：`newRound` 的 `userText` 只来自 `roundPlanFor` 取的**最后一条 USER 消息**
    （`messages.lastOrNull { it.role == MessageRole.USER }`，`GroupTurnCoordinator.kt:751-758`），
    **模型正文根本到不了这里**。所以只有用户自己能定义候选集——而他本来就能在第一行直接写
    `候选：…` 达成同样效果，**不存在越权提升**，只是比函数 KDoc 写的「显式声明」宽松。
    ⚠️ **按硬约束本轮未改 `GroupChat.kt`**（改它要先经确认）。若要修，正确口径是让 `^` 只锚定
    首行（去掉 `m`，或改 `\A`），**不是**加「排除代码块」之类的启发式。
    另附一条实测到的既有小瑕疵（同样只钉现状）：`.trim('-', '*', '"')` 是**字符集 trim**、
    遇空格就停，所以 `候选：- **a**` 清理不掉开头的 `- `，留下 `" **a"` 这种候选 id，
    于是「按 markdown 列表声明候选」这条路和模型吐的票永远配不上。
  - **`parseBallot` 不感知 markdown fence / 引用**（**已知假阳性，未修**）。代码块里独占一行的
    示例 `VOTE: opt-a` 会被当真票；更糟的形状是 `firstOrNull` 取**第一条**命中，所以模型在
    fence 后文里**真的**投了 `VOTE: opt-b` 也会被前面的示例行顶替。⚠️ **本轮明确不修**：
    契约没要求区分代码块，而修它要先定义一整套 markdown 感知规则（fence 配对、缩进代码块、
    行内引用…），属于超出本轮范围的行为变更。已用 `a vote line inside a code fence is still
    counted as a ballot` 把现状钉住。

  **⑤ ⚠️ 订正块（2026-10-06，第十三批 `be7952a83` / `19545e076` 之后加）——
  上面 ③ 里那三条「只钉现状」的记述按惯例保留原文，但其中三条都已不再是现状，
  逐条订正如下（⚠️ 别再照抄上面那段当现状）：**

  - ⚠️⚠️ **「修它要先经确认，本轮只钉现状」——确认已拿到，规则已定，本段已作废（保留原文）**。
    上面那条「`lineSequence()` 只认 `\n` / `\r\n` / `\r`，**不认** Unicode 行/段分隔符
    U+2028 / U+2029，所以『首行声明 + U+2028/9 + 后续文本』解析不出候选」——
    **这条假阴性已修**。**规则已定：分隔符集合取 6 个**
    （`\n` / `\r\n` / `\r` / `\u0085`NEL / `\u2028`LS / `\u2029`PS），与 Java `Pattern`
    的行终止符集合**逐条对齐**；实现是 `GroupChat.unicodeLines`（**私有**），
    `parseCandidates` 与 `linesOutsideCodeFences` **共用**它。
    **依据（离线探针实测，`kotlinc`，输出在仓库外 `/tmp/opencode/probe/probe-out.txt`）**：
    ① `Pattern` 默认把 `.` 视为不匹配行终止符，而实测 `(?s)候选：a.b` 能跨
    `\u0085`/`\u2028`/`\u2029` 命中、`(?m)^b$` 也能在 `a<sep>b` 上命中 ⇒ 三者**本来就是**
    `Pattern` 的行终止符；② `String.lineSequence()` / `String.lines()` **只切**
    `\n`/`\r\n`/`\r`，那三个**都当普通字符**（实测三者切出来仍是 1 行）⇒
    「切分行」与「正则行终止符」口径不对称，**那就是本缺陷的成因**；③ 两个现成 API
    口径**完全一样**，**都不能拿来蒙混**。
    ⚠️ 上面那句「U+2028/9 在聊天输入里基本不出现（Android `EditText` / IME / 剪贴板都不产生）」
    **仍然成立且没有被推翻**——但它**只说明触发概率低，不说明可以不修**：真实模型输出
    **不经过 `EditText` / IME / 剪贴板**。⚠️ **真实模型是否真的会输出这几个字符，
    本次仍然零实证**。
  - ⚠️ **「`.trim('-', '*', '"')` 是字符集 trim、遇空格就停」这条已修（保留原文）**：
    `8a24b5d0` 已用 `GroupChat.normalizeCandidateId` 取代它——改成**剥成对**的包裹符号
    （`**x**` / `*x*` / `"x"` / `'x'` → `x`）再剥**列表符号**前缀/后缀，
    所以 `候选：- **a**` 现在能规范化成 `a`。⚠️ 成对引号剥不干净时**原样保留**
    （`"a` 不再被静默补成 `a`），这是**刻意的收紧**；`_` / `__` **有意不剥**
    （`_` 在 snake_case id 里远比 markdown 下划线强调频繁）。
  - ⚠️ **「`parseBallot` 不感知 markdown fence / 引用（已知假阳性，未修）」已修（保留原文）**：
    `5186349f` 加了 `linesOutsideCodeFences`（围栏内与开/闭围栏行本身都丢掉，
    **未闭合围栏失败关闭**），`8a24b5d0` 又把声明行收成「只认首个非空行」。
    ⚠️ **`parseCandidates` 侧没有上围栏状态机**，理由是「只认首个非空行」使
    「首行落在围栏内部」**结构上不可达**——⚠️ **这是结构性论证，不是实测**。
    ⚠️ 订正后那条把现状钉住的用例名也变了：`a vote line inside a code fence is still
    counted as a ballot` 已被改写成**钉修后行为**，并新增了
    `an unclosed code fence swallows the rest of the message fail closed` /
    `votes before and after a closed code fence still count` 等四条。
    ⚠️ **缩进代码块与行内引用仍未处理**——上面那句「修它要先定义一整套 markdown 感知规则」
    **对这两个形状仍然成立**。
  - ⚠️ **「正确口径是让 `^` 只锚定首行」这条建议已被采纳，但实现路径不同**：
    `8a24b5d0` **没有**去掉 `m` 或改 `\A`，而是先切行再取
    `unicodeLines(text).firstOrNull { it.isNotBlank() }`——**效果等价**（声明只认首个
    非空行），且顺带把「行从哪来」也统一了。⚠️ 正则里的 `(?im)` **仍保留**。
  📍 完整证据与全部取证局限见本文「⚠️ 第十三批」与
    `docs/eval/c1-group-chat.md` 的「Unicode 行终止符统一切分（零设备）」那一节。

  **④ 指针订正（第十批核实，只登记不改别的文件）**——⚠️ **`docs/eval/c1-group-chat.md` 与
  `docs/beyond-operit-client-changes.md` 本轮一个字都没动**（按硬约束），下面全是**只登记**：
  - **`c1-group-chat.md:348`** 那句「判定入口 `GroupChat.kt:605`（`parseBallot`）」的**行号已过期**，
    实际是 **`GroupChat.kt:841`**（`:605` 那个位置现在早就是别的代码了）。
  - **`c1-group-chat.md:254`**（「仅接受结构化候选/票；多数决可复算，**平票按配置失败**」）与
    **`:4383`**（「**平票按配置失败**的路径要单独跑一次」）里的「平票」是**派生判据**，**不是
    契约原文**：契约 `client-changes.md` **全文 326 行 grep `平票|tie|Tie|TIE` 零命中**，
    `:180` 只写「各角色对候选选项投票，多数决输出」、`:201` 只写「vote 只接受结构化候选/票并按
    多数决输出」——**两处都没有平票字样**。所以「平票按配置失败」是实现侧选的口径
    （`GroupChat.kt:856` 的 `tiePolicy`，默认 `TIE_FAIL`），把它当契约要求去卡验收属于判据错位。
  - **本篇的过期行号**（同样只登记，历史行按惯例保留不覆写）：
    `:673` `GroupChat.pendingSpeakers` **`:581` → `:817`**；`:674` `GroupChat.tally`
    **`:620` → `:856`**；`:678` `GroupTurnCoordinator.voteSummaryMessage` **`:511` → `:665`**
    （⚠️ 原写 `:653` 是**本批修复前**的 `2381c6c4` 位置；本批 `b96912cb` 给
    `GroupTurnCoordinator.kt` **净增 12 行**（`+14 / -2`），所以它和 `resolveVote` 一起后移）；
    `:684` `validate` **`:404` → `:444`**、`parseBallot` **`:605` → `:841`**、`tally`
    **`:620` → `:856`**、`importShare` **`:728` → `:964`**、`findForbiddenKeys`
    **`:785` → `:1021`**；B5 段内 `parseBallot` **`:605-617` → `:841-853`**、`resolveVote`
    **`:438` → `:592`**（⚠️ 同上，修复前是 `:580`）、`VoteOutcome` **`:141` → `:142`**。
- B6 `card-validator` 模块没有 lint 报告：`card-validator/build/reports/` 里只有
  `tests/`，找不到任何 `lint-results*`。这是「没跑」不是「0 命中」，台账里别给它记 0。
- B7 抽屉筛选 chip 来回切换、群聊页整页渲染等 UI 行为无设备证据。
  ⚠️ **本轮在「抽屉筛选」这条路上修掉了两个真实缺陷**，并且给 SQL 语义补了主机侧证据，
  但**UI 那半仍然是零证据**，三件事分开记：
  1. **缺陷 1（真 bug，已修）**：抽屉搜索的 5 条 `LIKE` 查询**没有 `ESCAPE` 子句**，
     用户输入的 `%` / `_` 被当通配符。实测症状（改之前）：搜 `100%` 会额外命中**不含
     `%`** 的会话，搜 `a_b` 会额外命中 `axbxc`，搜 `%` 命中**全部 23 行**——**只多
     命中、不漏命中**，所以是用户**看得见**的错。已加 `ESCAPE`，转义收敛在
     `ConversationRepository` 的 5 个搜索方法里各过一次
     `escapeConversationLikePattern`（`ConversationSearchLikePattern.kt:64`，
     转义字符 `~`，纯函数、单趟遍历）。
  2. **缺陷 2（真实风险，已修）**：`ORDER BY is_pinned DESC, update_at DESC`
     **缺 `id` 兜底**。`update_at` 是毫秒精度，同毫秒更新两个会话时 `LIMIT/OFFSET`
     分页**理论上可能重复或漏行**。14 处 `ORDER BY` 已全部追加 `id ASC`
     （`id` 是主键、投影里本来就有；**`update_at` 不同时行为逐字不变**）。
     ⚠️ 脚本造了 4 行 `(is_pinned, update_at)` 全同的 fixture 实测，改前**本机稳定**
     ——但**那是引擎行为不是 SQL 契约**，所以仍按风险修。
  3. **主机侧证据有了，UI 那半还是没有**：`tools/verification/
     c1s_conversation_type_filter_replay.py`（**已入库**，83,955 字节 / 1,382 行）
     **438 条断言全过**、两次退出码 0 / 输出逐字节相同，SQL 与谓词常量是**用正则从
     `ConversationDAO.kt` 源码里抽取**的。**它把「类型筛选只过滤」钉成了真实的 SQLite
     执行证据**——集合相等（不是数量相等）、筛选后 count 正确、分页无重复无遗漏、
     切换 `type` 不改查询种类。⚠️ **但它替代不了 UI 端到端**：搜索框输入 → 列表刷新
     一次没跑过；`LIMIT/OFFSET` 是脚本**自己拼**的，`PagingSource` 的并发失效 /
     快照一致性 / 回滚没验；证据全部来自 CPython 内置 SQLite，**不是 Android 的
     SQLite**；`~` 在不同 Android / SQLite 版本上的语义差异、以及 `ESCAPE` 与大小写
     折叠（`PRAGMA case_sensitive_like`）的交互也都没验。**6 条完整清单见
     `docs/eval/c1-group-chat.md`「C1-S」那节第四小节。**
  4. **⚠️ 一个对外可感知的行为变化**：`GET /api/conversations/paged?query=…`
     （`ConversationRoutes.kt:85`）的 `query` 里带 `%` / `_` 时，改动前会返回
     **多出来的**会话，改动后只返回标题里**真含该字符**的会话。**这是修 bug，不是改
     契约**——但外部消费者若依赖了旧的宽松行为，会看到结果集变小。
- B8 `GroupChatPage` 的 `onEdit` 只放行 USER（`GroupChatPage.kt:292`），
  `feature/chat/GroupMessageActions.kt` 侧再用 `if (!groupChat)` 关掉重新生成 /
  创建分支 / 删除 / 分支切换（`:126` / `:217`）。理由是这些动作会改写消息，使
  `group_runs.committed_role_ids` 与实际消息错位。**要放开必须同步改写
  `committed_role_ids`**，不是纯 UI 改动。
- ✅ **B9 `onSuccess` 里 `null -> groupRunsInFlight.remove(conversationId)` 的连带损伤
  （`06cf6181` 已修，运行时仍未验证）。**
  **改前**：`ChatManager.kt:1007` 那行在残留旧 job 走到这里时**镜像里装的是新轮的令牌**，
  这一 remove 让**新轮再也取消不掉**（`cancelActiveGroupRun` 第一行就 `remove` 不到
  东西、直接 return）、续跑拿不到 `expectedRunToken`、**整轮卡死**。
  ⚠️ 上一批**刻意没改**的理由是：① 改它会**动正常路径**——`produced == null` 时
  `failGroupTurn` 可能提前 return，**那一次 remove 是镜像唯一的兜底清账**；
  ② 那批的修法是让拒绝走 `Advance.Halted` 而不是 `null`，从而**绕开**这一行。
  **改后**：`null ->` 分支改走 `clearGroupRunMirrorIfMine(conversationId, groupRunToken)`，
  即 `ConcurrentHashMap.remove(key, value)` 的**两参**重载（值相等才删）。
  **既有兜底行为为什么没被破坏**：`commitGroupTurn` 返回 `null` 的**唯一**形状是
  `produced == null`，那条路里 `failGroupTurn` 会在**归属成立**时自己清镜像，所以剩下的
  兜底只为「`failGroupTurn` 提前 return」而存在——而那些形状里镜像装的**正是本轮令牌**，
  按令牌比对照清不误。真正被排除的只有「归属拒收」那一种：`failGroupTurn` 因拒收而**不碰**
  镜像，镜像里是新轮的令牌，此时不删。
  ⚠️ 令牌为 null（理论上不可达）时**宁可不删**：分不清镜像是谁的，新轮被误伤会整轮卡死，
  而留下陈旧镜像只让 `takeGroupTurn` 的 `expectedRunToken` 失配一次。
  ⚠️ **仍未验证**：真机上残留旧 job 的时序（零设备）；护栏是文本级的
  （`GroupStaleJobCommitSourceGuardTest` 新增 2 例，变异检验两次真红）。
- ✅ **B10 `abandonDanglingGroupRuns` 的 `limit = 8` 与契约有张力（`06cf6181` 已修，
  运行时仍未验证）。**
  **改前**：取「挂着 `RUNNING` 的行」时写了 `limit = 8`，于是**超过 8 条**时**第 9 条起
  不会被判死**，仍是 `RUNNING` 占位，与契约「**任何群聊轮次都不会永久悬挂**」冲突。
  （原注里「同样的常量也出现在 `:853`」是**巧合**——那一处是
  `searchMemories(limit = 8)` 的记忆条数上限，语义无关。）
  ⚠️ 上一批**未修**且**未核实单会话能否真的堆到 9 条并发 `RUNNING`**；本批仍未核实这一点，
  但契约要求的是**清空**而不是「处理掉一部分」，所以按清空来做。
  **改后**：**分批循环读到清空**——`ABANDON_BATCH_SIZE = 8` 只作分页大小、
  `ABANDON_MAX_BATCHES = 64` 只作收敛上限；每判死前**重读一次**并跳过已终态的行
  （`terminal()` 不判终态，盲目写会把别人的终态抹成 `CANCELLED`）。
  ⚠️ **为什么必须留硬上限**：同一会话在本函数执行期间又抢占成功一轮时（残留旧 job 走到
  `takeGroupTurn`），`RUNNING` 行数不单调减少，无界循环会自旋。撞上限的残余会在下一次
  发消息时再被扫掉。
  ⚠️ **仍未验证**：分批循环的真机行为（零设备）；护栏是**文本级**的
  （`GroupRunAbandonDrainSourceGuardTest`，5 例，变异检验两次真红）。
- ⚠️ **B9b `ChatService` 是 `ChatManager` 的第二份无群聊感知的生成管线
  （`fa36c65c` 已加 fail-loud 闸门，运行时仍未验证）。**
  `core/service/ChatService.kt` 与 `feature/chat/ChatManager.kt` 大段逐字重复，
  它的 `handleMessageComplete` 四个泄漏点全中（传完整 `currentMessages`、不挂群聊
  transformer、工具不走 `viewerScopedTools`、记忆空间用助手/全局空间）——整轮所有角色
  互相可见全部历史，且**没有任何测试会红**（现有群聊用例全部直接调 `viewerMessages`，
  从不经过生成入口）。
  **已核实它当前不可达**，且比原判断更彻底：`ChatService` 只有两处注入
  （`HistoryVM`、`core/service/ChatGenerationForegroundService`），各自只调
  `toggleConversationPinned` / `stopGeneration`；而 `HistoryPage` **没有被任何路由引用**、
  `core/service/ChatGenerationForegroundService` **不在 `AndroidManifest.xml`** 里
  （清单里只注册了 `.feature.chat` 那个，它注入的是 `ChatManager`）。
  ⚠️ 命名陷阱是真的：`AppHiltModule.provideChatService` 返回的其实是 `ChatManager`。
  **修法**：**不复制群聊逻辑**（那会造出第二个真相来源），改成
  - `handleMessageComplete` 顶部 **fail-loud**：`require(!isGroupConversation(…))`。
    该函数是 `ChatService` 里 `generationLoop.generateText` 的**唯一**调用点，所以闸门放在
    这里就让「群聊经 `ChatService` 生成」在结构上不可能。
  - provider 按产物改名 `provideChatService` → `provideChatManager`（Hilt 只看返回类型）。
  ⚠️ **仍未验证**：`require` 真的抛出属**运行时**行为，零设备；护栏是文本级的
  （`ChatServiceGroupChatFailLoudGuardTest`，4 例，变异检验两次真红）。
  ⚠️ **另有一处本轮未改**：`finishInterruptedPendingTools` 里的三处
  `providerHandler.generateText` 同样不群聊感知（取消后续跑工具的旁路，从
  `stopGeneration` 可达）。修它要先决定群聊下是**跳过**还是**做成群聊感知**，两者都改
  运行时行为且需要真机，故只登记不动。
- ⚠️⚠️ **B11 残留旧 job 那两条路径从未在真机上复现过（零设备）。**
  触发前提「`abandonDanglingGroupRuns` 不取消任何 job」是**读代码 + 核实
  `ConversationSession.cancelJobs()` 只被 `stopGeneration` / `cleanup()` 调用**
  得到的，**不是真机观测**。⚠️ 路径 A（超时 / 角色失败 → `failGroupTurn`）有
  **JVM 单测层面**的实测支撑；⚠️ **路径 B（用户取消 → 残留 job 进 `onSuccess`）
  连这个级别都没有**（`stopGeneration` 先 `cancelJobs()` + `join()`，被取消的协程走
  `onFailure` 的 C 分支 rethrow，`onSuccess` 不执行）。
  ⚠️ **真机复现要人工编排时序**：同一会话里一条长生成在飞 → 期间发新消息把它判死 →
  等旧 job 跑完（或反过来先超时）。这不是「跑一遍测试」能覆盖的，
  所以这一整类缺陷**目前只有代码层证据**。
  ⚠️ 顺带说明：**`361c7cf6` 不属于 C1**——`git show --stat` =
  1 file / 14 insertions / 3 deletions，只改 `core/ui/components/message/ChatMessage.kt`
  的 KDoc（订正遗留第 20 条里 fork 那一半的禁因措辞）、**零测试改动**，
  所以它与本文件的任何测试数字都无关。
  ✅⚠️ **第六批补了防回退护栏（`bed09118`，`GroupForkDisableReasonSourceGuardTest` 2 例，
  KDoc 一个字没改）**：① fork 段落不许把账目错位算到 fork 头上 ② **姊妹例**要求
  重新生成 / 删除那两半必须保留它们自己的账目错位理由（防止整段被一刀切删掉）。
  ⚠️ **护栏钉的是禁因的归属、不是逐字文案**。
- ⚠️ **B12 群配置面板两个真机必崩已修（`33eb801e` / `71d1439e`），四条边界没消。**
  **① Miuix 风格完全没验**——两个崩溃都只在 `KedgeStyle.MD3Exp` 触发（**读代码**结论：
  Miuix 分支不走 `requireNotNull`，且 `PrimaryBottomSheet` 的 KDoc 写明 Kedge 版
  「靠内容自身滚动，忽略该参数」），**Miuix 下这个面板的行为零证据**；
  **② 其余 62 个 `PrimaryBottomSheet` 调用点没逐个上真机**（⚠️ **62 是本轮实测**：`app/src/main` 里 `grep -rn "PrimaryBottomSheet("` = 66 处命中，减去定义文件里 4 处；**把 `src/test` / `src/androidTest` 算进去是 68**。⚠️ **「64」这个数两种口径都复现不出来，本轮按 62 登记**；只有群配置面板真开过）；
  **③ 没在旧 APK 上先复现再对比**（原始栈是用户实测给的，靠**栈帧行号**对得上）；
  **④ 面板里的保存 / 生成二维码 / 扫码导入三个业务动作一个都没点**，只验了渲染与滚动。
  ⚠️ 顺带一条**可复用的教训**：源码文本护栏扫 Compose 调用点**不能靠括号配平**——
  `content` 是**尾随 lambda、大括号在右括号外面**，第一版护栏整段漏扫内容体，
  **主断言因「谁都不违规」而恒绿**，是靠另一条「反空跑」断言抓出来的。
  **每批源码护栏都必须自带一条「扫描器仍能找到东西」的断言。**
- ⚠️ **B13 工作区里有两处「不是文档改动」的未提交内容——下一个人务必先看清楚。**
  `git status --short` 当前是
  `M app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tavern/TavernMacroExpander.kt`
  （1 insertion / 1 deletion：`expandTavernMacros` 的宏正则结尾从 `}}` 改成显式转义
  `\}` + `}`，**正则语义等价**；⚠️ **谁改的、为什么未知**）与
  `?? app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/C1GroupUiE2EFixtureTest.kt`
  （**461 行 / 3 条 `@Test`**，一次**被中断的「真机 UI 端到端」任务**留下的半成品）。
  ⚠️ **本轮既没 add 也没改它们**；⚠️ **下一位 `git add -A` 会把两处一起卷进自己的提交**。
  ⚠️ **`C1GroupUiE2EFixtureTest.kt` 让 `app/src/androidTest` 的 `@Test` 数从 61 变 64**——
  引用「61」时必须写清是**git 跟踪口径**。处置：**要么补完单独提交（它正是 UI 端到端需要的
  夹具），要么删掉**；⚠️ **删之前先读一遍**，那是目前唯一一份 UI 端到端夹具的成果。
- ⚠️ **B14 `ChatService.finishInterruptedPendingTools` 里三处 `generateText` 同样不群聊感知，
  且从 `stopGeneration` 可达（本轮新发现，未动手）。**
  `fa36c65c` 的 fail-loud 闸门只在 `handleMessageComplete`（它是 `generationLoop.generateText`
  的唯一调用点），**这条旁路走的是 `providerHandler.generateText`**，不在闸门覆盖范围内。
  ⚠️ **修它要先定产品行为**：群聊下**跳过** vs **做成群聊感知**（后者等于要在这个类里
  重建一套群聊语义）。**两者都改运行时行为、都需要真机**，所以只登记不动。
  ⚠️ 别把它读成「闸门没覆盖全」——**那个闸门本来就不是为这条路径设计的**。
  ⚠️ 顺带：`ChatService` 当前**零活跃构造点**（`HistoryPage` 没被任何路由引用、
  `ChatGenerationForegroundService` **不在 `AndroidManifest.xml`** 里），
  所以「这个类要不要留」本身就是个待决策项。
- ⚠️⚠️ **B15 Coil 崩溃已修（`ea482935`），但真机效果零证据。**
  修复本身是可核的（`git show --stat` = `KhatKitApp.kt` +63 / `RouteActivity.kt` −34），
  崩溃现场也在磁盘上（`app/build/outputs/androidTest-results/connected/debug/` 那份
  `tests="10" failures="1"` 的 XML）。⚠️ **但本轮按约束不碰设备，没有复跑过。**
  验证只需一条 `./gradlew --offline :app:connectedDebugAndroidTest`，判据是
  **XML 的 `tests ≈ 61`、`failures="0"`、不再出现该异常**。
  ⚠️ **顺带订正一处归因**：早前把这条崩溃记成「来自 `b5c5ebcb`」是**不准确的**——
  `b5c5ebcb` 一行 Coil 都没碰，它只是加了 2 个 androidTest 类**改变了执行顺序**，
  把一直存在的隐患顶出来了（详见第六批那一节）。
- ⚠️⚠️ **B16 四项代码债的运行时行为全是零证据。**
  `require` 是不是真抛、分批循环在真机上的实际行为、`remove(key, value)` 的真实并发效果、
  以及「`produced == null` 时 `failGroupTurn` 提前 return」的具体触发条件，**一条都没在
  真机上观测过**。⚠️ **三条 gradle 命令（编译 / 单测绿）不构成任何契约验收证据**，
  四组护栏也全是**源码文本级**的（仓库 `testImplementation` 只有 junit，
  `commitGroupTurn` / `failGroupTurn` 这类 `private suspend` + 一堆 Hilt 协作者的路径
  JVM 单测构造不出来）。运行时验证步骤见 `docs/eval/c1-group-chat.md` 操作清单 A8。

### 下一位的行动顺序

按「零设备就能做 → 需要设备才能做」排。第 1 项的前两条与第 7 条**已完成**，其余今天就能落。
⚠️ **已完成的三条零成本动作里，没有一条产生契约 `:206` 意义上的验收证据**——
三批都只补了主机侧 / 代码层的部分证据，**十例状态仍全 `unverified`**。

1. **零设备，立刻可做**
   1. ✅ **已完成（2026-10-05）**：`python3 tools/verification/c1d_migration_30_31_replay.py`
      已跑（退出码 0，两次输出逐字节相同），命令、退出码、输出路径与逐组断言分布
      已登记进 `docs/eval/c1-group-chat.md` 的「C1-D 迁移 30→31 主机侧重放」小节，
      C1-D / C1-07 行同步更新。**它不能替代仪器测试**这一句也一并登记了：
      `Migration_30_31_Test` 的 7 条仍需设备，C1-07 仍是 `unverified`。
   2. ✅ **已完成（2026-10-05）**：`python3 tools/verification/c1p_migration_31_32_replay.py`
      已跑（退出码 0，49 条断言全过，变异检验 6 条 FAIL），登记进
      `docs/eval/c1-group-chat.md` 的「C1-P 角色卡元数据落库与 Room 31→32 迁移（零设备）」
      小节。**它不能替代仪器测试**这一句同样登记了：`Migration_31_32_Test` 的 6 条
      仍需设备。
   3. 给 `ConversationDAO` 的 SQL 补仪器测试源码（`androidTest`，用已有的
      `androidx.room.testing`）：`:type = ''` 时不筛、`itemCount` 正确、
      搜索路与未归档路口径一致。写好先不跑，等接设备。
   4. ✅ **已定稿（2026-10-05）：B2 抽屉搜索 `folder_id` 口径 —— 选方案 B（保持现状）**。
      B2 条目里已写清现状、判断（遗漏）与两个方案及各自代价，理由是方案 A 会让文件夹内
      会话不可检索而二级页没有搜索框。**决定：保持现状，`ConversationDAO.kt` 一行未动**，
      `ConversationDrawerFolderScopeTest` 那 4 条测试钉住的现状**就是最终口径**。
      ⚠️ **「改口径会先让测试变红」这条仍然成立**——那是**预期信号**，不是回归；
      要改口径就必须同时翻掉
      `ConversationDrawerFolderScopeTest.searchQuery_currentlyHasNoFolderCondition`
      的断言方向，**不许只改 DAO 不改断言**。
      ⚠️ **方案 A 的代价说明继续保留**（不要因为定稿就把它删掉）：收窄会让已归档会话
      **彻底搜不到**，而二级页（`FolderDetailPage` / `FolderDetailViewModel`）**目前没有
      搜索框**（只读 `getConversationsOfFolderPaging`），跨文件夹搜一个词做不到。
      **剩下的一步是「显式化」**：把抽屉搜索「跨文件夹」的语义补进 `ChatDrawerViewModel`
      的 KDoc，并在 B2 遗留项里记成已知且认可的口径。**那不是口径变更，是文档化工作。**
      ⚠️ **「不要改 DAO」只针对 `folder_id` 这一个口径，不是针对整个 DAO**：C1-S 那批
      （`215296f1..6982869b`）改过 DAO 的 `ESCAPE` 子句与 `ORDER BY` 兜底，那是**另外两
      个已定位的缺陷**，与 B2 无关，也没碰 `folder_id` 那一行 `WHERE`。
   5. ✅ **已完成（2026-10-05）**：**第二生成入口（`ChatService.kt`）的 `senderName`
      定时炸弹**已拆（`88ba63c2`），护栏 3 条已加（`8bc28105` / `c940813b`），变异检验
      3 次全部 FAIL。**B2 之外这一条不必再排期**——但它**没有**产生任何验收证据，
      十例状态仍全 `unverified`。见本文件 B4 条目与 `docs/eval/c1-group-chat.md` 的
      「C1-P 第二生成入口的通知标题护栏（零设备）」。
   6. ✅ **已完成（2026-10-05）**：**C1 内核 18 个文件的 Android Lint 命中现在是 0**
      （上一窗口是 `ChatList.kt` 3 条 `FrequentlyChangingValue`）。改法是
      `366b3fe8`「组合期求值 → draw 期求值」：三份 `LazyListState` 读数收成不可变快照
      `ScrollbarMetrics`，由 `derivedStateOf` 缓存，只在 `LaunchedEffect` 体与 `Canvas`
      的 `draw` lambda 里读——**滚动不再每帧重组整个 `ChatListNormal`，只重绘那一个
      Canvas**。**warning 消失是结果不是目的**，这是真实性能改进。
       ⚠️ 但「真机上滚动确实只重绘不重组」仍是**零设备证据**，依据是 Compose 求值语义与
       lint 规则，不是帧率实测。别把它记成「滚动性能已验证」。
   7. ✅ **已完成（2026-10-05）**：**抽屉筛选这条路上的两个真实缺陷已修 + 主机侧证据已
      补**（`215296f1..6982869b` 8 个 commit）。缺陷 1 = 5 条搜索 `LIKE` 缺 `ESCAPE`
      （用户输入 `%` / `_` 被当通配符，**只多命中不漏命中**）；缺陷 2 = 14 处
      `ORDER BY` 缺 `id` 兜底（`update_at` 毫秒精度下分页可能重复或漏行）。
      重放脚本 **438 条断言全过**、两次退出码 0 / 输出逐字节相同。
      ⚠️ **但它没有产生任何契约 `:206` 意义上的验收证据**，**十例状态仍全
      `unverified`**（C1-10 只是多了一条「部分证据」）。⚠️ **本轮还改了一行既有测试的
      期望串**（`ConversationTypeFilterSourceGuardTest.kt:68-73`），三条断言一条没被
      削弱或删除、改完更严，但**它抓不到转义字符写错**——细节见上面「逐项状态」表后的
      显眼登记，以及 `docs/eval/c1-group-chat.md`「C1-S」那节。
      ⚠️ **B2 已在 2026-10-05 定稿为方案 B**（见上面第 4 项）：G6 那 4 条断言只把
      `folder_id` 口径的现状钉成实测，方案 A / B 与推荐一个字没改；**定稿之后
      「改口径会先红是预期信号」这条仍然成立**。

   8. ⚠️⚠️⚠️ **新增（2026-10-06，第六批之后）：本节下面那份「零设备 / 需要真机」的
      清单已经过期，权威版是 `docs/eval/c1-group-chat.md` 的
      「下一位怎么把剩下的做完（第六个窗口的操作清单）」**——它按
      **「需要设备（A1-A8）/ 需要外部环境（B1-B4）/ 零设备可做（C1-C9）」三组**列了
      每项的**具体命令或具体步骤**。这里只放三条最要紧的指针：
      - **A1 全量仪器测试**（验证 Coil 修复，本轮唯一「跑一条命令就能验」的项）：
        `./gradlew --offline :app:connectedDebugAndroidTest`，判据是 XML 的
        `tests ≈ 61` / `failures="0"` / 不再出现那个 Coil 异常。
      - ~~**A2 `C1LiveModelSequenceTest` 其余 4 条**（现在只有第 5 条绿了）~~
        ✅ **第十二批已完成**：四条退出码全 `0`、全 `OK (1 test)`。
        ⚠️ **剩的尾巴只有一条**：第四条的 app 侧 `c1-live-evidence-vote.json`
        **未 pull**，把它从设备 `/sdcard/Android/data/heizige.kk.khatkit.debug/files/`
        拉回来即可（当时的跑法：手动 `adb install -r -t` +
        `am instrument -e class '…#方法名'`，⚠️ **`#` 必须加引号**）。
      - **C1 先收拾工作区那两处未提交内容**（`TavernMacroExpander.kt` 与
        `C1GroupUiE2EFixtureTest.kt`）——⚠️ **`git add -A` 会把它们卷进下一位的提交**。
      ⚠️ **下面第 2 组（需要真机）的 7 条按惯例保留不覆写**，⚠️ **其中第 3、5、6 条的
      现状已被第六批改变**：第 3 条「设备上没有 API key」**已被内置公共网关解掉**
      （真实 usage 已采，见「C1 真机证据采集第三 / 第四轮」）；第 6 条「全量 56 条因 Coil
      崩溃跑不完」**已修但未在设备上复跑**；第 5 条（扫码）**仍然零份**。

2. **需要接真机**——⚠️ **2026-10-05 更新：真机已接上（OnePlus `PKG110` / Android 16 /
   API 36），第 1 / 4 / 6 条已部分或全部完成，第 3 / 5 条被「设备上没有 API key」挡住。**
   逐条现状：
   1. ⚠️ **部分完成**：`mode=pipeline` 的三个 viewer 可见消息 ID 台账已在**真机真 Room
      库**上采到（真实 `message.id`，不是构造值），越权审计 6 对 `violations: []`。
      **roundtable / vote 的 viewer 集合没采**。
   2. ❌ **仍未做**：roundtable / vote 跑群、vote 平票路径单独跑一次。
   3. ❌ **仍缺，且被环境挡住**：设备上没有配 API key
      （`settings.preferences_pb` 只有 165 字节，没有 `providers` / `models`），
      **没有一次真实 HTTP 调用发生过**。只拿到路由决策与预算口径两份证据。
      **配 API key 是这一整条现在的硬前置。**
   4. ⚠️ **部分完成**：真机 `.jsonl` 已产出、`adb pull` 后 `sha256sum` 与真机
      `MessageDigest` **6/6 一致**；**SillyTavern 本体打开该文件仍然零证据**。
   5. ❌ **仍未做**：相机扫码导回另一台设备、逐字段 diff、单独验证不含密钥/记忆/
      授权 token。
   6. ✅ **已完成**：C1 相关 **25 条全绿**（12 + 7 + 6，exit 0），另有一轮合跑
      **45/45 全绿、exit 0**，新增 `C1DeviceEvidenceTest` 3 条。
       ⚠️ **但全量 56 条跑不完**（`BrowserRuntimeTest` 触发 Coil 单例崩溃，**未修**），
       未单独跑过的是 `NodeTreeSmokeTest`(4) / `DependencyReadOnlyLoadTest`(2) /
       `ExampleInstrumentedTest`(1)。
       ✅ **第十七批订正（HEAD `d133b400b`，2026-10-06）**：仓库**新增一个 androidTest 类**
       `C1GroupFilterAndAvatarUiTest`（3 条，`d133b400b` 入库），**`git ls-files` 口径的
       androidTest `@Test` 总数由 61 → 64**（登记时逐文件 `grep -c` 实测）。这三条里
       **真机只跑了 1 条且通过**（成员头像组），另 2 条因执行者 `kill -3` 招致 ColorOS
       杀 instrumentation **未跑完**（见本批 ④）。⚠️ **这一条尚未达成「C1 相关全绿」**：
       `C1GroupFilterAndAvatarUiTest` 的 chip / @ 两条**没跑完**，成员头像组那条虽通过但
       非空验证未做 ⇒ **别把本项读成「C1 相关又多 3 条全绿」**。
   7. ❌ **仍未做**：装一个从 Room 31 升上来的旧库，确认升级不崩、`group_cards` 是空串、
      群聊页刷新后「已导入的角色卡」仍在——这三件是 B1 剩下的全部尾巴。
      ⚠️ 真机库里 `rikka_hub` 的 `PRAGMA user_version = 32` /
      `PRAGMA integrity_check = ok` 已读到，**但那不等于跑过一次 31→32 升级**。
   8. ⚠️ **新增（2026-10-06，第十一批之后）：wire 级模型名的真机复核**——
      `wireModelName` **已具备**（`f1bf516e` / `0c239788`），零设备两条证据已采
      （JVM 9 例 + 真实网关两个 wire 名均 HTTP 200）。**剩下就一件事**：
      真机重跑 `realProviderRoundRecordsGenuineTokenUsage`，把落盘 JSON 拉回来，
      确认 `wire_model_name_provenance` 是 **`wire_response_model`**
      而不是 `uuid_reverse_lookup_fallback`。
      ⚠️ **当前设备上那份记的仍是反查值**——**这一条没有真机数字**。
      ⚠️⚠️ **第十二批订正（HEAD `86e88970d`）：这条已经部分完成，但还没做完**——
      **mock 下已在真机复核**（t1/t3 pulled JSON 的 `wire_model_name` =
      `mock-model-a/b/c` 服务端自报串，同行 `model_id` 是本地 UUID ⇒ wire 优先生效）；
      ⚠️ **真实网关那次仍未重跑**，设备上那份**仍是反查值** ⇒ **硬理由①不消**。
      ⚠️ **另订正判据适用范围**：`wire_model_name_provenance` **在 mock 证据 JSON 里
      根本不存在**（grep = 0），它只由真实网关的写出器落 ⇒ 上面那句判据**只对
      `c1-live-evidence-real-provider.json` 适用**。详见「第十二批」与
      `docs/eval/c1-group-chat.md`「C1 真机证据采集第七轮」。
      ⚠️ 顺带可一并验「旧 Room 库消息树能否反序列化新字段」（第 7 条那件），
      ⚠️ 但**别把 `wireModelName` 显示到 UI 上当成待办**——**没做，也不打算做**。
      ✅ **第十五批订正（HEAD `31b6b5a52`，2026-10-06）：上面第 8 条已完成**——真实网关用例
      在 wire 优先口径下重跑并**首次产出**正式证据，`wire_model_name_provenance_counts`
      = `{wire_response_model:3, fallback:0}` ⇒ **硬理由①对真实网关路径已消**
      （mock 判据第二套本轮也复核通过）。⚠️ **这不是「十例可以判通过」**——逐行依据见
      c1 文档「C1 真机证据采集第八轮」⑨。
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
| C1 群聊 | 内核和一版 UI 已写入，**未验收**。详见上方「C1 交给下一位」。`docs/eval/c1-group-chat.md` 十例全部 `unverified`。⚠️ **2026-10-06 第六批之后**：真实公网网关目标用例**首次真机全绿**（真实 usage + Σ 与落库精确相等 + 产物 SHA-256 已登记），两个真机 UI 必崩已修并真机复验，Coil 崩溃已结构性修复，**十例状态仍全部 `unverified`**。⚠️ **2026-10-06 第十一批之后（wire 级模型名）**：硬理由①从「缺能力」变成「**已具备 + 待真机复核**」——`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备有两条证据（JVM 9 例 + 真实网关两个 wire 名均 200）。⚠️ **但仪器测试一行没跑**，设备上那份证据**仍是 UUID 反查值**，硬理由①**未消**。⚠️ **这是「能力已具备」，不是「证据已采集」**；另三条硬阻塞（真机 UI 端到端 / 酒馆本体 / 相机扫码）**一条都没解除** | 按该节缺口续写；补证据前不得把 C1 标成已完成。⚠️ **wire 级模型名这一条的下一步就一件事**：真机重跑 `realProviderRoundRecordsGenuineTokenUsage`，确认落盘 JSON 的 `wire_model_name_provenance` 是 `wire_response_model` 而不是 `uuid_reverse_lookup_fallback`。⚠️ **权威操作清单 = `c1-group-chat.md` 的「下一位怎么把剩下的做完（第六个窗口的操作清单）」**（三组：A 需要设备 / B 需要外部环境 / C 零设备可做）<br>⚠️ **2026-10-06 第十三批之后（`GroupChat` 行切分统一到 6 个 Unicode 行终止符）**：修掉了一个**静默丢候选 / 静默丢票**缺陷（切分行只切 3 个分隔符、正则认 6 个 ⇒ 口径不对称），`GroupChatTest` **33 → 39**、台账 **45 类 / 491 例 → 45 类 / 497 例**、app 单测 **111 类 / 915 例 → 111 类 / 921 例**。⚠️ **零设备、零 `androidTest`，且真实模型是否会输出 U+2028/U+0085 本次零实证** ⇒ 这是「消除失败模式」，**不是**「观测到并修掉高频故障」。**十例状态仍全部 `unverified`**，硬理由①未被本批触及、②③④原样<br>⚠️ **第十三批新增的一项未验证义务**：若要主张「这条路径在生产里真的出过问题」，需要一份**真实响应原文**（真实网关或真机落盘 JSON）证明模型输出里出现过这类字符——**探针的输入是手写的，不算**<br>⚠️ **2026-10-06 第十五批（真机第八轮）**：真实网关在 wire 优先口径下重跑并**首次产出**正式证据 `c1-live-evidence-real-provider.json`（`{wire_response_model:3, fallback:0}`、Σ=20731=spent），**硬理由①（真实网关路径）已消**；预算截断 raw dump 首次落盘（13571/9000/BUDGET_STOPPED）；mock 4/4 + 全量 64/1（唯一失败为缺参设计）+ UI 部分覆盖（@ 选择器较好 / 头像组仅截图 / chip 过滤未验）；**十例状态仍全部 `unverified`**（C1-02 最接近，三项未锁死）。详见 `c1-group-chat.md`「C1 真机证据采集第八轮」。<br>⚠️ **2026-10-06 第十六批（真机第九轮）**：筛选 chip 两半首次拿到**真机 UI 级证据**（过滤生效 + 切回逐字节复原 + 主库与 `rikka_hub`/`-wal`/`-shm` SHA 不变——⚠️ 仓内无对应 androidTest、按契约 `:232-235` **不升级**）；设置污染已用 **pb 字节手术恢复**（终态 `37baf83f…`，重启后仍 6 条会话；⚠️ 测试代码根因未修，下次全量仪器测试会再污染）；**酒馆从「零份」推进到「解析器级接受 16/16」**（⚠️ 完整 app 打开仍零份，不许写成「已能打开」）；**新缺陷：只有 1 个助手时删除会崩（未修、只报告）**；成员头像组仍只有弱证据。**十例状态仍全部 `unverified`**（逐行依据见 c1 文档第九轮⑧）。 |
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
