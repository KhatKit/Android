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
    其余 4 条 mock 用例仍未真机全绿），逐条见下面「第六批」。
  - ⚠️⚠️⚠️ **本轮（`33eb801e..a74c1820`，2026-10-06）：两个真机 Compose 必崩已修并真机
    验证 + Coil 单例崩溃结构性修复 + 四项代码债收口。十例仍全部 `unverified`。**
    逐条见下面新增的「第六批」那一节。**操作清单在
    `docs/eval/c1-group-chat.md` 的「下一位怎么把剩下的做完（第六个窗口的操作清单）」**
    ——按「需要设备 / 需要外部环境 / 零设备可做」三组分组，每项都带具体命令或步骤。

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
| 4 vote 结构化 | 数据结构层已实现，传输层仍是文本约定（见遗留 B5）。⚠️ **第五批（`e72793e6` / `8a5f89d7`）把平票裁决脚手架在 `cancelActiveGroupRun` / `abandonDanglingGroupRuns` 两条终态出口上的漏回收补上了** | `GroupTurnCoordinatorTest` 63 的投票/平票组<br>⚠️ **（第五批后是 84；`GroupTieBreakScaffoldingDropSourceGuardTest` 3 → 7）** | 无 |
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
      - **A2 `C1LiveModelSequenceTest` 其余 4 条**（现在只有第 5 条绿了）：
        手动 `adb install -r -t` + `am instrument -e class '…#方法名'`，
        ⚠️ **`#` 必须加引号**。
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
   7. ❌ **仍未做**：装一个从 Room 31 升上来的旧库，确认升级不崩、`group_cards` 是空串、
      群聊页刷新后「已导入的角色卡」仍在——这三件是 B1 剩下的全部尾巴。
      ⚠️ 真机库里 `rikka_hub` 的 `PRAGMA user_version = 32` /
      `PRAGMA integrity_check = ok` 已读到，**但那不等于跑过一次 31→32 升级**。
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
| C1 群聊 | 内核和一版 UI 已写入，**未验收**。详见上方「C1 交给下一位」。`docs/eval/c1-group-chat.md` 十例全部 `unverified`。⚠️ **2026-10-06 第六批之后**：真实公网网关目标用例**首次真机全绿**（真实 usage + Σ 与落库精确相等 + 产物 SHA-256 已登记），两个真机 UI 必崩已修并真机复验，Coil 崩溃已结构性修复，**十例状态仍全部 `unverified`** | 按该节缺口续写；补证据前不得把 C1 标成已完成。⚠️ **权威操作清单 = `c1-group-chat.md` 的「下一位怎么把剩下的做完（第六个窗口的操作清单）」**（三组：A 需要设备 / B 需要外部环境 / C 零设备可做） |
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
