# C1 群聊验收记录

本文件是 C1 的证据登记表，不是测试计划。没有可复现的自动化或真机原始证据时，
状态必须保持 `unverified`，不能因 UI 截图或代码存在而改为通过。记录格式遵循
`beyond-operit-client-changes.md` 的 C1 实施契约（该文件 `:193-206` 是规格原文，
`:232-235` 是「验收记录格式」）。

## 现状（2026-10-05 复核，基线 `d45ebd10`，C1-D 重放证据登记于锚点 commit `1b0e04a9`，
C1-P 角色卡落库 + Room 31→32 证据登记于 `b7025665`；
⚠️ **真机证据窗口见「C1 真机证据采集（2026-10-05，OnePlus PKG110）」——设备已接上，
但十例状态仍全 `unverified`**）

- ⚠️⚠️ **2026-10-05 第二个窗口（本文件最新的实测窗口）：真机已接上，四类产物拿到了
  两类。** 设备 **OnePlus `PKG110` / Android 16 / API level 36 / `arm64-v8a`**，app
  `heizige.kk.khatkit.debug`（`versionName 2.5.5` / `versionCode 190`），无线调试
  `192.168.31.183:38493`。契约 `:206` 点名的四类里——
  - **「各角色可见消息集合」有了：真机真 Room 库**（生产同一个
    `AppDatabaseFactory.create(context, "c1-device-evidence.db")`，同套 SQLite 扩展、
    同一个 `onOpen`、同一 schema v32，只换独立库名）上跑真实
    `GroupTurnCoordinator.viewerMessages`，3 个 viewer 的可见消息 ID 台账逐条落定，
    越权审计 `checked_pairs=6 / violations=[] / passed=true`。
  - **「导出 SHA-256」有了：真机**跑 `TavernChatCodec.exportGroupJsonl` → 真机写文件
    → 真机 `java.security.MessageDigest` 算哈希，3 种 `mode` 各一份，`adb pull` 回本机后
    与 GNU coreutils `sha256sum` 独立复算 **6/6 全一致**（字节数也全等）。
  - ⚠️ **「实际模型调用序列」仍然缺**——只拿到**路由决策**证据（三条不同回落通路 +
    `TaskRoutes.resolve` 返回的真 `Model`）。
  - ⚠️ **「token 计数」仍然缺**——只拿到**预算口径**证据（`spent=4096` /
    `limit=400` / `skipped=[b,c]` / `status=BUDGET_STOPPED`），
    **但 3000/1096 是构造的输入，不是任何模型吐出来的真实用量**。
  - **根因：设备上没有配 API key。** 只读检查了
    `files/datastore/settings.preferences_pb`（165 字节），`strings` 只有
    `mcp_servers / data_version / assistants / quick_messages / launch_count /
    select_assistant / 0950e2dc-…`——没有 `providers`、没有 `models`、没有 API key。
  - **所以按本文「判定规则」小节（与 `beyond-operit-client-changes.md:232-235`
    「验收记录格式」同源：「缺调用序列或缺哈希的行均不算通过」），十例仍然全部
    `unverified`，0/10 不变。** 四类里两类齐了不等于契约达成；`viewer 可见消息 ID`
    只覆盖了 **pipeline**，roundtable / vote 的 viewer 集合**没采**。
- ⚠️⚠️⚠️ **2026-10-05 第三个窗口（本文件最新的实测窗口）：四类产物全部有真机内容，
  但十例状态仍全 `unverified`。** 上面那个窗口的两条限制**都被这一轮解掉了**：
  - **「没有 API key 所以第三、四类在原理上采不到」——解法是 `adb reverse`。** 往生产
    单例 `SettingsRepository` 里装一个 base URL 指向 `http://127.0.0.1:8765/v1` 的
    **自定义 OpenAI 兼容 provider**，设备侧 `adb reverse tcp:8765 tcp:8765` 打到开发机
    上的 mock 服务，**没改生产代码、没加测试专用后门**，走的是
    `ChatManager.sendMessage → GenerationLoop → ProviderManager → OpenAIProvider →
    ChatCompletionsAPI.streamText → Ktor CIO OkHttpClient` **完整生产链路**。
    请求特征 `User-Agent: ktor-client`、`accept: text/event-stream`、
    `stream_options.include_usage: true`、**三个请求全 `stream=true`**。
  - **「viewer 集合只覆盖 pipeline」——解法是夹具按三种 mode 各采一遍**（`a9d2077e`，
    改的是夹具循环不是生产逻辑），越权审计三 mode 全部 `passed=true` /
    `checked_pairs=6` / `violations=[]`。**roundtable 议长 `chairRound=true` 的
    放行分支第一次被触发**。
  - **四类产物现状**：**viewer 可见消息 ID**（三 mode 夹具层台账 + pipeline 真实 HTTP
    台账）、**实际模型调用序列**（`mock-model-a → mock-model-b → mock-model-c`，
    真请求）、**prompt+completion token**（main 773、budget 236，**与落库
    `group_runs.spent_tokens` 逐条相等**）、**导出 SHA-256**（3 mode 真机文件 + 真机
    `MessageDigest` + 本机 `sha256sum` 三重一致）。
  - ⚠️⚠️ **但诚实结论是「四类产物已全部有真机内容，十例的验证矩阵仍不完整」，
    0/10 不变。** 五项仍然缺（逐条见「C1 真机证据采集第二轮」的「仍然缺的」）：
    **roundtable / vote 的真实 LLM 调用记录**（只有夹具层台账，真实 HTTP 零份）、
    **酒馆（SillyTavern）本体打开群聊导出文件**、**真机 UI 端到端**（一行 Compose
    没上过屏）、**相机扫码真机链路**（CameraX+MLKit 没在设备上跑过）、
    **`BrowserRuntimeTest` 的 Coil 单例崩溃**（未修，全量 56 条仪器测试跑不完）。
    另加两条诚实边界：**mock 不是真实模型**（是「真实 provider 代码路径 + 真实 HTTP +
    真实 SSE + 真实 usage 报文」，不是「真实 LLM 推理」）；**显式 @ 的投递收窄
    仍未在真机上单独观测到**（夹具的 `@` 落在用户消息上，用户消息对每个 viewer 都可见）。
  - **仪器测试**：`connectedDebugAndroidTest` 类过滤跑 **28 例全绿、exit 0**
    （3+12+7+6）；`C1LiveModelSequenceTest` 走手动 `am instrument`，**不挂住、3.6s 完成**。
    ⚠️ `connectedAndroidTest` **跑完会卸载 app 并删掉外置目录里的证据文件**，采证据必须
    手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull`。
  - ⚠️ **这一轮还撞出并修掉了锚点之后第一个由真机证据定位的 main 源码 bug**
    （`858c11d0`，`core/data/ai/GenerationLoop.kt`）：后续角色的产出被**并进上一位
    已提交的发言**，导致该角色被误判「本轮没有产出内容」写成 `role_failed`。
  - **同时登记两处新的测试断言修正**（`bff7b0c6` / `a9d2077e`）与一处更早的
    `openGroupSession()` 修正（`47693445`）——详见「C1 真机证据采集第二轮」的
    「敏感项」。全部定性为**测试代码自身缺陷，不是生产代码 bug**。
- ⚠️⚠️⚠️⚠️ **2026-10-05 第四个窗口（本文件最新的实测窗口，HEAD `6ba95422`）：
  「真实 token 与真实调用序列」这一类第一次不是 mock——用的是项目**自带的免费公网网关**，
  设备直连公网，十例状态仍全 `unverified`。**
  - **怎么绕开「设备上没有 API key」的第二条路（上一轮是 `adb reverse` + 本机 mock）：
    生产 `DEFAULT_PROVIDERS` 里本来就内置了一个 OpenAI 兼容网关**
    （`core/data/datastore/DefaultProviders.kt:281-318`，`name = "极客猫"`、
    `baseUrl = https://api.zenneko.top/v1`、`enabled = true`、`builtIn = true`，
    **apiKey 已预填在源码 `:285`——本文只记位置，不落明文**），模型表里带
    `deepseek-v4-flash`（uuid `5a86b2d6…`）与 `glm-5.2`（uuid `8b6bf21c…`）。
    测试按 id 从 `DEFAULT_PROVIDERS` 取这条定义，**没改生产代码、没加测试后门、
    设备不经 `adb reverse`、不经本机 mock**。
  - **真实 usage（provider 返回的数字，不是任何估算）**：
    a = `deepseek-v4-flash` prompt **6803** / completion **159** / total **6962**；
    b = `glm-5.2` **6667 / 68 / 6735**；c = `deepseek-v4-flash` **6880 / 102 / 6982**。
    **Σ(prompt+completion) = 6962+6735+6982 = 20679**，落库
    `group_runs.spent_tokens = 20679`，**精确相等**；`status=COMPLETED`、
    `committed=[a,b,c]`、`skipped=[]`、`ended_at` 非空。
  - **实际调用序列 = `deepseek-v4-flash → glm-5.2 → deepseek-v4-flash`**，与按角色
    绑定**逐位一致** → `resolveGroupTurnModelId` + `TaskRoutes.resolve` 的按角色选型
    在**真实调用**下成立（这一条以前只有 mock 与纯函数两级证据）。
  - ⚠️⚠️⚠️ **但诚实结论仍是 0/10 不变**，三条边界必须连着上面的数字一起读：
    **① 这个用例没有一次全绿记录**——那一跑成功产出了上述数据，随后被设备 OEM 回收
    策略杀进程（见下），`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，
    **JSON 落盘文件没拉回来**，数字来自同一次成功生成的内存快照
    （`c1-real-raw-dump.json` 现场）；**② `actual_model_call_sequence` 里的模型名是由
    `message.modelId` 的 uuid 经 provider 模型表反查出来的，不是 wire 级抓包**
    （app 不保存响应的 `model` 字段），JSON 里已用 `wire_model_name_provenance`
    字段显式标明这一点；**③ 真机 UI 端到端仍然零份**。按本文「判定规则」小节与
    `client-changes.md:232-235`，C1-03 / C1-04 / C1-09 / C1-10 直接踩「真机或自动化
    证据」那条，**一条都不能改成通过**。完整登记见「C1 真机证据采集第三轮」。
  - ⚠️ **本轮还撞出一个新的设备侧限制：OnePlus 的 OEM 回收策略会在 app 进程存活
    约 34-44 秒时杀进程**（`OsenseKillAction` / `NirvanaLowFree`，
    `appcareThreshold=79`、app 占 313MB）。**既有 mock 用例同样被杀**，所以与真实
    网关、与本轮改动都无关；`:app:connectedDebugAndroidTest` 全量报 `Process crashed`
    且**0 个测试启动**。按「OOM 立即停止重试」的约束共试 **11 次**后停止。
    **这是设备环境的限制，不是代码缺陷。**
  - ⚠️ **本批同时落地 P0/P1/P2 三批修复**（`af104850` / `9844cbc4` / `e7ad2a77`、
    端点群聊门禁 `87f9c209`、`failGroupTurn` 回收平票脚手架 `e6764293`），
    `:app:testDebugUnitTest` 实测 **97 个测试类 / 754 用例 / 0 failures / 0 errors /
    0 skipped**。逐条见「C1 内核四批补测与修复」与「HTTP 端点群聊门禁与
    `failGroupTurn` 平票脚手架回收」两节。
- ⚠️⚠️⚠️⚠️⚠️ **2026-10-06 第五个窗口（本文件最新的实测窗口，HEAD `361c7cf6`）：
  三批**代码缺陷修复**，十例状态仍全 `unverified`。** 本窗口**没有真机、没有一次
  `adb` 调用**，交付物是三批「发现并堵住真缺陷」的代码改动 + 对应的 JVM 侧证据。
  - **① 平票脚手架三处漏回收**（`e72793e6` + `8a5f89d7`）：
    `failGroupTurn` 那处已由 `e6764293` 修过，同批发现**还有两处完全相同的失败模式**
    未修——`cancelActiveGroupRun`（用户中途取消议长裁决那一轮）与
    `abandonDanglingGroupRuns`（发新消息时上一轮还挂 RUNNING 被判死），两处原来都不调
    `dropTieBreakScaffolding`，后果与 `failGroupTurn` 相同：**平票 SYSTEM 脚手架永久留在
    会话里**。
  - **② 残留旧 job 不得推进状态、不得跨轮盖戳**（`2fdee352`）——⚠️ **本批最严重的一个**。
  - **③ `onFailure → failGroupTurn` 补同款守卫**（`ef716dd3` + `8ccc0264`）：②只堵了
    `onSuccess` 那条路，失败那条路没堵。
  - ⚠️ **本窗口的性质必须说清**：三批修的都是**发现并堵住的真缺陷**，
    **不构成任何用例的验收证据**——尤其 ②③ 针对的「残留旧 job」那两条路径
    **从未在真机上复现过**（本窗口零设备、零 `adb`）。所以**十例状态一个都没变**。
  - **测试数（本轮重新实测，逐个 `TEST-*.xml` 用 Python + ElementTree 求和，
    `glob` 命中 99 个 XML）**：`:app:testDebugUnitTest` = **99 个测试类 / 783 用例 /
    0 failures / 0 errors / 0 skipped**。逐批增量见「测试结果（实测 XML 汇总）」段。
  - **lint**：**0 error**（app **584 Warning / 6 Hint = 590**、15 模块
    **617 Warning / 7 Hint = 624**），与上一窗口**逐位相同**；本轮改的
    `ChatManager.kt` / `GroupTurnCoordinator.kt` / 两个新增护栏测试文件，
    lint 命中数**逐个点算全部为 0**。
  - ⚠️ **台账锚点 `1b0e04a9` 的 78 个 commit 没有被重算**（本批 5 个代码/测试 commit
    全部追加进「锚点之后的后续提交」，见该小节末尾的新批次）。
  - 完整登记见「平票脚手架三处漏回收 + 残留旧 job 两道守卫（零设备）」一节。
- ⚠️⚠️⚠️⚠️⚠️⚠️ **2026-10-06 第六个窗口（本文件最新的实测窗口，HEAD `a74c1820`）：
  「十例 0/10」的最后一道锁已破，但**十例状态仍然全部 `unverified`**。**
  这一窗口做了三件性质完全不同的事，**没有一件构成任何用例的验收证据**：
  - **① 真实公网网关那条用例第一次真机全绿（`4336831f`）**：`am instrument` **退出码 0**、
    `numtests=1`、`Time: 16.002`、`OK (1 test)`；真实 usage a `deepseek-v4-flash`
    **6839 / 92 / 6931**、b `glm-5.2` **6645 / 58 / 6703**、c `deepseek-v4-flash`
    **6904 / 242 / 7146**，**Σ(prompt+completion) = 20780 = 落库 `spent_tokens` 20780**
    （逐位相等，逐条 `total == prompt + completion` 独立复算通过），
    `status=COMPLETED`、`committed=[a,b,c]`、`skipped=[]`，实际调用序列
    `deepseek-v4-flash → glm-5.2 → deepseek-v4-flash`（按角色绑定成立），**persona 第三条
    禁令第一次在真模型上被验证**（三条正文各只含自己的 `ROLECODE` A/B/C、无串码）。
    三份产物已 `adb pull` 回主机并记 SHA-256（`c1-live-evidence-real-provider.json`
    7995 字节 / `55d12977…dbfd40`）。**class 过滤必须用单方法并加引号**
    （`-e class '…C1LiveModelSequenceTest#realProviderRoundRecordsGenuineTokenUsage'`）。
    ⚠️ **三条诚实边界**：模型名是 uuid 反查、**不是 wire 级抓包**；其余 **4 条仍未真机
    全绿**；落盘 JSON 按约定留在仓库外，**只有 SHA-256 可核**。
    完整登记见「C1 真机证据采集第四轮」——⚠️ **那一节的正文其实已经在文件里了**
    （它随 `4336831f` 落地，小节头部写的 HEAD `d96d64e1` 是文档提交链的上一环），
    **本窗口不重复抄那三张表**，只补记它的落地 commit 与本节的汇总口径。
  - **② 两个真机必崩已修，并在真机上验证过（MD3Exp 风格）**：群配置面板的
    `IllegalArgumentException`（漏传图标，`33eb801e`）与
    `IllegalStateException`（滚动容器套两层，`71d1439e`），各配一条源码护栏
    （`5d58f923` / `54c196ca`）。真机复验：`logcat -c -b crash` 后开面板 + 滚动，
    `logcat -d -b crash -t 200` **为空**，全量 logcat grep 两个异常 **0 命中**，
    `uiautomator dump` 确认 15 个字段全渲染出来，面板内 5 次滚到底 + 3 次反向不崩。
    ⚠️ **Miuix 风格完全没验、其余 62 个调用点没逐个上真机、面板里的保存/二维码/扫码
    业务动作一个都没点、也没有在旧 APK 上先复现再对比。**
  - **③ Coil 单例崩溃做了结构性修复（`ea482935` + `2766afc7`）**：把 `ImageLoader`
    的定义点从 `RouteActivity` 的组合搬进 `KhatKitApp : SingletonImageLoader.Factory`。
    **根因不是 `b5c5ebcb`**（它完全没碰 Coil），而是同一 instrumentation 进程里
    `NodeTreeSmokeTest` 用 `createComposeRule()` 渲染 `AsyncImage` 先把内置默认 loader
    钉死，`BrowserRuntimeTest` 随后拉起 Activity 时 `setSafe` 当场抛。
    ⚠️ **本轮按约束没碰设备，所以这次修复的真机效果完全未验证。**
  - **④ 四项代码债收口**（`fa36c65c` / `06cf6181` / `bed09118` / `a74c1820`）：
    `ChatService` 第二份生成管线加 fail-loud 闸门、`abandonDanglingGroupRuns` 改成
    分批读到清空、`onSuccess` 的 `null` 分支改成按 `runToken` 清镜像、以及给已订正的
    `ChatMessage` KDoc 加防回退护栏。⚠️ **①②③ 三条的运行时行为全是纯运行时、零证据**，
    三条 gradle 命令只证明「编译过 + 单测绿」，**不构成任何契约验收证据**。
  - **测试数（本轮重新实测，逐个 `TEST-*.xml` 的 `tests` / `failures` / `errors` /
    `skipped` 属性用 Python + ElementTree 求和，`glob` 命中 **105 个 XML**）**：
    `:app:testDebugUnitTest` = **105 个测试类 / 808 用例 / 0 failures / 0 errors /
    0 skipped**（上一窗口 99 / 783，净 **+6 类 / +25 例**）。逐类增量见「测试结果
    （实测 XML 汇总）」段。**C1 相关 JVM 测试类台账 37 类 / 395 例 → 40 类 / 408 例**。
  - **lint**：**0 error**、app **584 Warning / 6 Hint = 590**、15 模块
    **617 Warning / 7 Hint = 624**（与上一窗口**逐位相同**；本轮碰过的文件命中数逐个
    点算全部为 0）。
  - ⚠️ **`app/src/androidTest` 全量 `@Test` 逐文件求和实测是 64 条，不是 61**——
    差额是那个**未入库的半成品** `C1GroupUiE2EFixtureTest.kt`（3 条，见「已知遗留与
    风险」第 27 条）。**只看 git 跟踪的文件才是 61**（61 + 3 = 64 算术对得上）。
  - ⚠️ **台账锚点 `1b0e04a9` 的 78 个 commit 仍然没有被重算**，本批 10 个功能/测试
    commit 全部追加进「锚点之后的后续提交」，见该小节末尾的新批次。
  - **三处已按约定保留不覆写**：① `docs/beyond-operit-client-changes.md` **本轮一字未动**；
    ② 工作区里那两处**不是本文档改动**的未提交内容（`TavernMacroExpander.kt` modified、
    `C1GroupUiE2EFixtureTest.kt` untracked）**已登记但未 add、未修改**；
    ③ 已入库的 `4336831f` 那次真机记录按惯例不再重抄。
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
  ⚠️ 同一天又补了第三条**主机侧**证据：C1-S 的抽屉 type 筛选重放 **438 条断言全过**
  （两次退出码 0、输出逐字节相同），它给「**类型筛选只过滤**」钉了真实的 SQLite
  执行证据（集合相等而不是数量相等、筛选后 count 正确、分页无重复无遗漏、切换 `type`
  参数不改查询种类）——**这是 C1-10 迄今最硬的一块证据**。但契约 `:206` 点名的
  **viewer 可见消息 ID / 模型调用序列 / token / 真机行为，本轮一份都没补上**
  （`adb devices` 仍为空输出）。**所以十例状态仍全 `unverified`，C1-10 也是。**
  那一节同时登记了本轮**修掉的两个真实缺陷**，以及**改过的一行既有测试断言**
  （期望串跟着生产代码的修法走，见「C1-S 抽屉 type 筛选主机侧重放与两个真实缺陷修复
  （零设备）」）。
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
| C1-01 | 三角色显式 @ | 只被 @ 角色收到该消息；其他角色不可见 | `GroupTurnCoordinatorTest` 72<br>`GroupChatTest` 22<br>`GroupRoleCompletionProviderTest` 8 | `verified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**显式 @ 的投递收窄**——夹具的 `@` 仍落在用户消息上（用户消息对每个 viewer 都可见），「其他角色可见」这半句在真机证据里仍然读不出来。详见「已知遗留与风险」第 15 条。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️ 本行要的三视角可见消息 ID 台账仍零份；本批**没有触碰 @ 投递路径**。**<br>**第十一轮订正（HEAD `a8302e463`，2026-10-06）：仍是 `unverified`，但本行第一次拿到真实网关的「投递收窄」证据。** 真机真网关 `mode=pipeline`、触发 `@角色乙 请只由你发言一次。`：`trigger_mention_role_ids=[b]`、`plan_selected_role_ids=[b]`、**实际只调用 b**（wire `glm-5.2`、provenance `wire_response_model`）、b 的 usage `6546+54=6600` 与落库 `spent_tokens` 相等；viewer 台账（真库 UUID）：`a=[c707437f…]`、`b=[c707437f…, ca2631a7…]`、`c=[c707437f…]`。⚠️ **口径写清**：触发是 **USER 消息**，生产口径对每个 viewer 放行——A/C「看不见 b 的本轮输出」成立，**A/C 仍看得见那条 @ 触发消息**（测试 `C1LiveModelSequenceTest.kt:1954-1973` 显式断言该口径）；本行「只被 @ 角色收到该消息、其他角色不可见」的**字面那半仍未真机观测到**（要「@ 一条角色发言」或「@ 不指代任何角色的输入」，遗留第 15 条）。仍缺：退出码未落盘、单次通过未做重复稳定性、`:206` 五要素缺导出哈希一类 ⇒ **不升级**。**<br>**第十二轮订正（HEAD `cddaa9959`）：与本行无关（C1-09 导出 / C1-06 取消），状态不变。**<br>**第十三轮订正（HEAD `18d730465`，2026-10-06）：仍是 `unverified`，但「重复稳定性」这项首次满足**——`realProviderMentionNarrowsSpeakersToMentionedRole` 真网关连续 3 次全绿（Time 6.674 / 7.707 / 8.249，日志均 `OK (1 test)`；⚠️ `INSTRUMENT_EXIT` 全 0 不可当判据，见遗留第 41 条）。三次都是 `plan_selected_role_ids=[b]`、只调用 b（wire `glm-5.2`、`wire_response_model`）、viewer 台账三份、usage `6546+86=6632` 与落库相等。⚠️ **仍缺两项**：① 「只被 @ 角色收到该消息、其他角色不可见」的字面半仍未观测（触发是 USER 消息，生产口径 A/C 仍可见它，遗留第 15 条不变）；② 契约 `:206` 的「导出哈希」一格（本例结构性不产出导出文件）。按判定规则第 2 条不升级。详见「C1 真机证据采集第十三轮」①。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：仍是 `unverified`。** 本批 #3 mention 真实网关通过（Time 6.567 / 日志 `OK (1 test)`；只调用 b / wire `glm-5.2` / usage 6546+98=6644 / viewer 台账 / 导出 `6e2ec8ce…` 1747 B）——**契约 8 项首次全齐**；但契约 `:201`「其他角色不可见」的字面半仍未观测（触发是 USER 消息、A/C 仍可见它，遗留第 15 条不变）⇒ 本行点名的必须断言未完整满足，按判定规则第 2 条不升级。见第十四轮①/⑤。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：仍是 `unverified`。** 契约 `:201`「只被 @ 角色收到该消息；其他角色不可见」的**字面半**仍未观测（触发是 USER 消息、A/C 仍可见它，遗留第 15 条）；本批零设备、四类产物零份，见第十五轮（零设备）⑦。<br>**第十六轮订正（文档/契约复核，HEAD `00bcdd4e9`，2026-10-07）：升为 `verified`。** 前两轮把契约 `:201` 读成「被 @ 的那条消息对其他角色不可见」，这是**误读**。逐字复核 `docs/beyond-operit-client-changes.md`：`:201` 挂在「**路由与失败语义**」标题下，原句「显式 `@角色` 只投递到被提及角色」的主语是**路由/投递**（本轮哪些角色被调用、参与展开），**不是消息可见性**；可见性由上一行 `:200` 单独且明确地规定——`:200` 原文：`buildContext(viewerRoleId, conversationId)` 只返回「该角色自己发送的消息、**用户消息**、`mention_role_ids` 包含自己的消息和轮次摘要」。⇒ **USER 消息对每个 viewer 放行是契约明文规定的正常行为，不是缺口**；C1-01 的「其他角色不可见」只能读作「@ 收窄后的本轮产出与其他角色无关」。现有真机证据逐条满足 `:201` 的投递语义：`trigger.mentionRoleIds=[b]`、`plan=[b]`（收窄投递）、实际只调用 b、`viewer_visibility` 里 a/c 的 `visible_assistant_role_ids=[]`（看不到 b 的本轮输出）、`status=COMPLETED`/`committed=[b]`/`skipped=[]`、b 正文只含 `ROLECODE:B`（未串他人上下文）、`buildContext` 对 a/c 返回那条 USER 触发消息恰是 `:200` 明文规定。契约 `:206`/`:232-235` 八项齐备（见证据登记表同行）。⇒ 升 `verified`（遗留第 15 条的字面读法应据此改判，契约依据即本块）。**<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：维持 `verified`。** 本批零设备、四类产物零份，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（逐格见「零设备修复批次（2026-10-07，HEAD `5c362851a`）」⑥。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：维持 `verified`。** 本批真机窗口只跑 C1-07 与 C1-10 两条路径，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（见「C1 真机证据采集第十六轮」⑤）。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：维持 `verified`（本批未碰显式 @ 路径）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：维持 `verified`（本批未碰显式 @ 路径）。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：维持 `verified`（本批未碰显式 @ 路径）。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持原判**（本批新增 C1-09 同源往返用例 `C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`，只触及 C1-09 点名的导出/QR 路径；本用例点名的路径未被触碰）。|
| C1-02 | 无 @ 的 pipeline | 按 `roles[]` 顺序串接；实际模型调用序列与日志一致 | `GroupTurnCoordinatorTest` 72<br>`GroupChatTest` 22<br>`GroupTurnModelTest` 14 | `verified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**无 @ pipeline 的真实调用序列与日志一致**——真实网关那轮确实跑了 pipeline，但「一致」只做到「落库/内存里的 `modelId` 与绑定一致」，**没有拿运行日志做对照**，而模型名本身又不是 wire 抓包（硬理由①）。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️ 本批新增的「`mode` 未知一律拒绝」是**导入期**的判定，与本行要的**真实调用序列与日志一致**无关；那一行仍然零份。**<br>**第十一轮订正（HEAD `a8302e463`，2026-10-06）：仍是 `unverified`——本行是离升级最近的一行之一。** pipeline 真实网关复跑第 4 次通过（`c73f46bf…`，Time 10.409）：wire 级序列 `deepseek-v4-flash → glm-5.2 → deepseek-v4-flash`（测试 `:1569-1573` 断言级）、`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`、usage `6829/136 + 6680/42 + 6876/57`、**Σ=20620=spent_tokens**、viewer 台账（a 不见 b/c、b 见 a 不见 c、c 见 b 不见 a；测试 `:1645-1654` 断言级）、`status=COMPLETED / committed=[a,b,c]`。⚠️ 第八轮⑨列的三项里 ① 序列对照 ② viewer 独立断言**已补**；**③ `am instrument` 退出码未随日志落盘**（`:232-235` 要求「测试命令及退出码」），另契约 `:206` 五要素仍缺「导出哈希」一类，且**单次通过、未做重复稳定性验证** ⇒ **不升级**。逐条见第十一轮 ⑥/⑨。**<br>**第十二轮订正（HEAD `cddaa9959`）：阶段 C 稳定性复跑因设备丢失未执行，单次通过的边界不变 ⇒ 状态不变。**<br>**第十三轮订正（HEAD `18d730465`，2026-10-06）：仍是 `unverified`——本行现在有「一次真网关通过 + 一组明确的间歇失败」双记录。** 通过：rp2-run3（Time 18.265，`f98f053d…`，`run=COMPLETED`、spent 20687、序列 deepseek→glm→deepseek、viewer/序列均为断言级）。失败：rp-run1/2/3 三次 300s 超时（`AssertionError: 等待助手消息超时（300000ms）：期望 3 条，实际 1 条…`，raw dump 里角色 a 是 `turn_kind=error`「本轮没有产出内容」/run=FAILED）；rp2-run1/2 又各挂一次（角色 c 零内容）；合计 6 跑 1 通过。⇒「间歇性零内容」是 app 流式路径的未定性行为（网关/设备侧已独立对账健康），稳定性不成立；导出哈希一格仍缺。详见第十三轮④。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：仍是 `unverified`。** 本批 pipeline 3/3 全失败（Time 27.627/25.884/19.129 / `FAILURES!!!`）：第 3 位角色 c 零产出（`turn_kind=error` / `model_id=null` / usage 全 -1 / `group_run=FAILED/role_failed/committed=[a,b]`），失败原文「角色 c 的 modelId 必须是 deepseek-v4-flash expected:<5a86b2d6-…> but was:<null>」。**根因 = 网关 HTTP 200 但 SSE 无 content delta（空白完成），6/6 稳定复现、非既有非 2xx 缺陷** ⇒ 不升级。见第十四轮②/③。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：仍是 `unverified`。** 本批未跑真实网关 pipeline；`d6494e49a` 的续跑入口与本行「调用序列与日志一致」无关；契约 `:206` 四类产物零份（第十四轮零产出根因未消），见第十五轮（零设备）⑦。<br>**真机第十五轮订正（HEAD `37630adb4`，2026-10-07）：升 `verified`。** 真实网关 `realProviderRoundRecordsGenuineTokenUsage` 真机 `OK (1 test)`（Time 15.294）：pipeline `actual_model_call_sequence=[a deepseek-v4-flash 6829+65, b glm-5.2 6609+54, c deepseek-v4-flash 6891+55]`、`provenance={wire_response_model:3, uuid_reverse_lookup_fallback:0}`、`Σ(prompt+completion)=20503==spent_tokens`、`group_run=COMPLETED/committed=[a,b,c]`、viewer 台账 a/b/c 与导出 `5f7914d1…`（2357 B/5 行）均齐；契约 `:206` + `:232-235` 8 项齐备且必须断言成立 ⇒ 升。详见「C1 真机证据采集第十五轮」①。<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：维持 `verified`。** 本批零设备、四类产物零份，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（逐格见「零设备修复批次（2026-10-07，HEAD `5c362851a`）」⑥。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：维持 `verified`。** 本批真机窗口只跑 C1-07 与 C1-10 两条路径，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（见「C1 真机证据采集第十六轮」⑤）。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：维持 `verified`（本批未碰真实网关 pipeline）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：维持 `verified`（本批未碰 pipeline）。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：维持 `verified`（本批未碰无 @ pipeline）。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持原判**（本批新增 C1-09 同源往返用例 `C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`，只触及 C1-09 点名的导出/QR 路径；本用例点名的路径未被触碰）。|
| C1-03 | roundtable | 全员完成后仅 `chair_role_id` 汇总；议长前看不到未完成输出 | `GroupTurnCoordinatorTest` 72<br>`GroupChatTest` 22<br>`GroupSpeakerResolverTest` 8 | `verified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**roundtable 的议长汇总轮**——真实网关只跑了 pipeline，议长汇总轮的真实调用、议长前看不到未完成输出这两半**零份**。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️ 议长汇总轮的真实调用与「议长前看不到未完成输出」这两半仍零份。**<br>**第十一轮订正（HEAD `a8302e463`，2026-10-06）：仍是 `unverified`，但真实网关 roundtable 首绿。** 重跑（Time 11.634，`c6389e80…`）：序列 `a(speaker) → b(speaker) → c(chair)`、wire `deepseek-v4-flash → glm-5.2 → deepseek-v4-flash`；usage `6831/96 + 6546/64 + 6998/188`、**Σ=20723=spent**；`c(chairRound=true)` 可见 `[6c8ca5e5…, 77485ecf…, f1fed06e…, f01e8f98…]`、a/b 都看不到彼此（测试 `:1765-1777` 断言级）；provenance counts `{3,0}`。首跑 FAIL 原文如实登记（c 被记为 `turn_kind=error`，已核实无冻结断档）。仍缺：导出哈希、退出码未落盘、单次通过；另 c 的 chair 正文不是归纳文本（机制已验、文本语义未验）⇒ **不升级**。**<br>**第十二轮订正（HEAD `cddaa9959`）：与本行无关，状态不变。**<br>**第十三轮订正（HEAD `18d730465`，2026-10-06）：仍是 `unverified`——但本轮把两次 FAIL 归因到测试自身竞态（产品侧正常）。** rt 3 跑：FAIL/FAIL/PASS。两次 FAIL 原文 `expected:<[a, b, c]> but was:<[a, b, null]>`——`awaitAssistantMessages` 只等条数，议长消息「先落库后盖章」被快照到 null；同轮 finally 的 raw dump 显示 `run=COMPLETED / committed=[a,b,c]`（rt-run1 spent 20708 / rt-run2 20781）⇒ **测试 bug，另一个代理正在修**（遗留第 42 条），修完须重跑才可再议。另：议长正文仍不是归纳文本（机制已验、语义未验），导出哈希一格仍缺。详见第十三轮④。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：仍是 `unverified`。** 本批 roundtable 3/3 全失败（Time 301.583/300.969/301.756）：第 3 位角色 c（议长位）零产出；失败原文「等待盖章 + run=COMPLETED 超时（300000ms）…最后状态=FAILED，committed=[a, b] spent=13766；app 错误=[]」，与 pipeline 同根因（HTTP 200 空内容、6/6 稳定）；第十三轮归因的测试竞态不解释本批失败。⇒ 不升级。见第十四轮②/③。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：仍是 `unverified`。** 本批未跑 roundtable（第十四轮 3/3 全失败、零产出根因未消）；契约 `:206` 零份，见第十五轮（零设备）⑦。<br>**真机第十五轮订正（HEAD `37630adb4`，2026-10-07）：升 `verified`。** 真实网关 `realProviderRoundtableRecordsChairSummaryCallSequence` 真机 `OK (1 test)`（Time 22.201）：`mode=roundtable/chair_role_id=c`、`speaker_order_and_turn_kind={a:speaker,b:speaker,c:chair}`、seq3 `role=c/turn_kind=chair/deepseek-v4-flash 7011+146`、`Σ=20707==spent_tokens`、`group_run=COMPLETED/committed=[a,b,c]`；viewer 台账 a/b 各见 2 条、议长 c 的 `chair_round=true` 见全部 4 条（a/b 未见他人未完成输出）；导出 `75ca8ea9…`（2371 B/5 行）齐；契约 8 项齐备且必须断言成立 ⇒ 升。详见「C1 真机证据采集第十五轮」①。<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：维持 `verified`。** 本批零设备、四类产物零份，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（逐格见「零设备修复批次（2026-10-07，HEAD `5c362851a`）」⑥。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：维持 `verified`。** 本批真机窗口只跑 C1-07 与 C1-10 两条路径，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（见「C1 真机证据采集第十六轮」⑤）。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：维持 `verified`（本批未碰 roundtable）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：维持 `verified`（本批未碰 roundtable）。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：维持 `verified`（本批未碰 roundtable）。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持原判**（本批新增 C1-09 同源往返用例 `C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`，只触及 C1-09 点名的导出/QR 路径；本用例点名的路径未被触碰）。|
| C1-04 | vote | 仅接受结构化候选/票；多数决可复算，平票按配置失败 | `GroupTurnCoordinatorTest` 72<br>`GroupChatTest` 22 | `verified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**vote 的三张选票、`parseBallot → __summary__`、以及平票按配置失败那条路**——**真实调用零份**（真实网关那轮跑的 mode 是 pipeline）。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️ 本批拒绝 `mode` 未知的理由正是「默认 pipeline 会**静默丢 `vote_candidates`**」，但那是**导入期**的拒绝；**真实 vote 调用序列仍然零份**。**<br>**第十一轮订正（HEAD `a8302e463`，2026-10-06）：仍是 `unverified`，但 vote 的两条路径都真机跑通了。** ① 多数决（Time 14.595，`1b7f0be7…`）：三张有效票 `a→opt-a、b→opt-a、c→opt-b`，`tally` 得 `Decided / opt-a 2:1`，`__summary__` 落 `vote_summary` 且无 usage；usage `6827/63 + 6545/41 + 6827/39`、**Σ=20342=spent**。② 平票（Time 11.967，`623745f6…`）：三票 `opt-a/opt-b/opt-c` 各 1、`VoteOutcome.Tie`，落 `status=FAILED / reason=vote_no_decision`，失败节点 `turn_kind=error` 且**断言不写 `vote_summary`**；`Σ=20361=spent`。两跑均 `provenance counts {3,0}`、viewer 台账断言级（每视角只见自己票面 + summary）。仍缺：导出哈希、退出码未落盘、单次通过（前两跑被 ColorOS 冻结作废）⇒ **不升级**。**<br>**第十二轮订正（HEAD `cddaa9959`）：与本行无关，状态不变。**<br>**第十三轮订正（HEAD `18d730465`，2026-10-06）：仍是 `unverified`——「重复稳定性」首次满足，本行两条路径都 3/3 全绿。** 多数决 vt-run1/2/3（Time 11.137/12.434/10.859；Σ 与 spent 相等、`Decided/opt-a 2:1`、`__summary__` 无 usage）与平票 vtt-run1/2/3（Time 11.256/14.35/18.165；`Tie`→`FAILED/vote_no_decision`、错误节点、不写 summary）；六份证据 SHA `5c17960a…`/`13144d44…`/`3421fff8…`/`a7a20299…`/`e4d67be6…`/`ff9139f6…`，日志均 `OK (1 test)`。⚠️ **仍不升级的硬项**：契约 `:206` 五要素的「导出哈希」一格仍无内容（本例结构性不产出导出文件）——按判定规则第 2 条「缺…哈希的行均不算通过」。详见第十三轮④。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：升为 `verified`。** 本批两条路径再次真机通过且**契约 8 项首次全齐**——多数决（Time 17.917 / `exitcode.txt=0` / `OK (1 test)`；序列 a/b/c = deepseek 6877 / glm 6609 / deepseek 6882；Σ=20368=spent；`Decided/opt-a 2:1`；导出 `8e42c5b9…` 2672 B/6 行）与平票（Time 16.506；Σ=20302=spent；`Tie → FAILED/vote_no_decision`；错误节点 `__summary__/error`；导出 `65f76608…` 2695 B/6 行）；viewer 台账三角色各见「自己 + `__summary__`」；provenance `{wire:3, fallback:0}`。**「导出哈希结构性不产出」的旧结论被本批直接推翻**（两跑都产出导出）。结合第十三轮 vote 3/3 稳定性 ⇒ 按契约 `:206`/`:232-235` 8 项齐备升 `verified`。见第十四轮①/④/⑤。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：维持 `verified`。** 本批零设备、无新真机证据；第十四轮「契约 `:206`/`:232-235` 8 项齐备」的结论未被本批触及，见第十五轮（零设备）⑦。<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：维持 `verified`。** 本批零设备、四类产物零份，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（逐格见「零设备修复批次（2026-10-07，HEAD `5c362851a`）」⑥。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：维持 `verified`。** 本批真机窗口只跑 C1-07 与 C1-10 两条路径，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（见「C1 真机证据采集第十六轮」⑤）。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：维持 `verified`（本批未碰 vote）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：维持 `verified`（本批未碰 vote）。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：维持 `verified`（本批未碰 vote）。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持原判**（本批新增 C1-09 同源往返用例 `C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`，只触及 C1-09 点名的导出/QR 路径；本用例点名的路径未被触碰）。|
| C1-05 | 轮次预算 | prompt+completion 达上限后停止剩余角色；日志含已用/上限/未运行角色 | `GroupTurnCoordinatorTest` 72<br>`GroupChatTest` 22<br>`GroupRunSchemaTest` 10 | `verified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**预算截断路径**——真实网关那轮 `token_limit=100000`，**整轮没触发截断**，「已用 / 上限 / 未运行角色」三个数在真实调用下仍然零份。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️ 本批对「预算越界」一律拒绝（理由是**不存在安全的缺省值**），但那是**导入期**的判定；**真实 usage 下的截断仍零份**。**<br>**第十一轮订正（HEAD `a8302e463`，2026-10-06）：仍是 `unverified`——本批没有跑预算截断。** 真实网关三条与 pipeline 复跑全部 `token_limit=100000`、整轮未触发截断；上轮 raw dump（13571/9000/BUDGET_STOPPED）仍不是正式序列 JSON。**<br>**第十二轮订正（HEAD `cddaa9959`）：C1-09/C1-06 均不覆盖本行 ⇒ 状态不变。**<br>**第十三轮订正（HEAD `18d730465`，2026-10-06）：仍是 `unverified`——本批未跑截断，且发现「正式证据结构性不可达」。** 预算截断测试方法写死「等 3 条助手消息」，而截断只产 2 条 ⇒ 必然超时失败 ⇒ 正式 JSON 永不写出（遗留第 43 条；可能需拆出独立的、期望 2 条的测试方法）。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：升为 `verified`（遗留第 43 条已消）。** 本批专用用例 `realProviderBudgetTruncationRecordsRunLogAndExport` 真机通过（Time 17.793 / `exitcode.txt=0` / `OK (1 test)`）：`BUDGET_STOPPED/token_budget_exceeded/spent=13594/token_limit=9000/committed=[a,b]/skipped=[c]`；序列 a deepseek 6934、b glm 6660、Σ=13594=spent；**库内 `messages.count=3`（USER+a+b）、`assistant_role_order=[a,b]` 直读断言**（`b60aa80c5` 补）；viewer 台账 a/b/c；导出 `da0f81c5…` 2044 B/4 行；`budget_effective=9000`。契约 `:202`/`:206`/`:232-235` 8 项齐备 ⇒ 升 `verified`。见第十四轮①/④/⑤。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：维持 `verified`。** 本批零设备、无新真机证据；第十四轮「契约 `:206`/`:232-235` 8 项齐备」的结论未被本批触及，见第十五轮（零设备）⑦。<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：维持 `verified`。** 本批零设备、四类产物零份，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（逐格见「零设备修复批次（2026-10-07，HEAD `5c362851a`）」⑥。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：维持 `verified`。** 本批真机窗口只跑 C1-07 与 C1-10 两条路径，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（见「C1 真机证据采集第十六轮」⑤）。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：维持 `verified`（本批未碰预算截断）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：维持 `verified`（本批未碰预算截断）。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：维持 `verified`（本批未碰预算截断）。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持原判**（本批新增 C1-09 同源往返用例 `C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`，只触及 C1-09 点名的导出/QR 路径；本用例点名的路径未被触碰）。|
| C1-06 | 取消与超时 | 不写入未生成消息；已生成消息和错误节点保留 | `UngeneratedMessageFilterTest` 18<br>`GroupTurnCoordinatorTest` 72 | `verified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**取消与超时**——真实网关那轮是正常跑完的（`status=COMPLETED`、无错误节点），取消/超时路径仍然零份。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️ 导入侧不涉及取消与超时；**取消/超时路径仍零份**。**<br>**第十二轮订正（HEAD `cddaa9959`，2026-10-06）：仍是 `unverified`——取消路径三次尝试全部不成立、四项断言零测量、超时半未跑。** attempt 1 测试 7.903s 后在「第 1 轮必须由生产失败路径写出 b 的错误节点」处失败；attempt 2 设备中途掉线；attempt 3 设备在 `am instrument` 生效前掉线（日志 0 字节）。⚠️ 另登记一个未定性现象：mock 对 B 注入 HTTP 500 后整轮仍 `COMPLETED`、B 是 speaker 消息且正文来源不明（遗留第 40 条）。完整现场见「C1 真机证据采集第十二轮」②。**<br>**第十三轮订正（HEAD `18d730465`，2026-10-06）：仍是 `unverified`——但取消路径首次拿到完整真机证据（先前三次尝试的全部断言零测量）。** `C1GroupCancelDeviceTest#cancelMidStreamKeepsGeneratedMessagesAndErrorNodeWithoutEmptyBubbles` 真机通过（Time 2.246 / `OK (1 test)`）：空气泡 **0**；第二轮 a 完整保留（id `95f302c5…`、usage 6236+22=6258）；被取消 b 的半截产出保留（id `5f97ad23…`、恰 24 字符=1 chunk，其余 18 块丢失）；第一轮 b 的错误节点保留（id `7e91c213…`、`turn_kind=error`）；`group_runs` `CANCELLED/cancelled/spent=6258/committed=[a]`；`b2_partial_message_count=1`（非空强断言；旧断言已证为空断言——见第十三轮②）。⚠️ **仍缺四项**：① 超时半（`GROUP_ROUND_STEP_TIMEOUT_MS` 15 分钟）未跑；② 各 viewer 可见集合未按契约落盘（只有完整消息表）；③ 导出哈希一格；④ 单次通过未做重复稳定性。按判定规则第 2/4 条不升级。详见第十三轮①。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：仍是 `unverified`。** 本批未跑取消/超时（第十三轮取消半通过仍为孤证，超时半 `GROUP_ROUND_STEP_TIMEOUT_MS` 从未跑）；契约 `:206` 的 viewer 集合 / 导出哈希 / 重复性仍缺 ⇒ 不升级。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：仍是 `unverified`。** 超时半**结构性做不到**（`GROUP_ROUND_STEP_TIMEOUT_MS` 不可注入，`ChatManager.kt:116`/`:1005`；测试构造的 `AppScope` 无参 `KhatKitApp.kt:344-352`）；`9d7429216` 只补证据字段、无实际值；契约 `:206` 四类产物零份，见第十五轮（零设备）④/⑦。<br>**真机第十五轮订正（HEAD `37630adb4`，2026-10-07）：仍 `unverified`。** 取消半首次补齐全部契约字段（`c1-device-cancel-report-vfy1.json`：viewer 台账 a/b/c + `actual_model_call_sequence` 4 条全 mock + `export_sha256=853033a8…` 2977 B/7 行；`empty_bubble_count=0` / 第一轮错误节点保留 / `new_error_nodes_in_round2=0` / b 半截 1 条保留；Time 4.292 `OK (1 test)`）；⚠️ **超时半仍结构性做不到**（`ChatManager.kt:116` `private const` + `:1005` `withTimeout`、`KhatKitApp.kt:344-352` 无参 `AppScope`；`timeout_half_verified=false`），且**通过项是单次通过、未做重复稳定性** ⇒ 契约 `:201`「取消/超时」不能只算一半 ⇒ 不升。详见「C1 真机证据采集第十五轮」①/④。<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：仍 `unverified`。** 本批未碰取消/超时；超时半结构性做不到（`ChatManager.kt:116`/`:1005`），契约 `:201` 两半不齐 ⇒ 不升。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：仍 `unverified`。** 本批未碰取消/超时；超时半结构性做不到（`ChatManager.kt:116`/`:1005`、`KhatKitApp.kt:344-352`），契约 `:201` 两半不齐 ⇒ 不升。见「C1 真机证据采集第十六轮」⑤。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：维持 `unverified`（本批未碰取消/超时；超时半结构性做不到，`ChatManager.kt:116`/`:1005`）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：**升 `verified`** —— 超时半条真机通过（注入 5000ms、实测 elapsed 6634ms、status=TIMEOUT/reason=timeout、空气泡 0、committed=[a]、b 错误节点、spent=6212=6190+22），补上此前唯一缺口；与取消半条合并看契约 `:201` 两半齐 + `:206`/`:232-235` 8 字段齐（viewer 台账/导出哈希在取消半）。见「第十八轮」①。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：维持 `verified`（本批未碰取消/超时）。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持原判**（本批新增 C1-09 同源往返用例 `C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`，只触及 C1-09 点名的导出/QR 路径；本用例点名的路径未被触碰）。|
| C1-07 | 失败续跑/幂等 | 同 `round_id` 重试跳过已提交 turn；不重复消息 | `GroupTurnCoordinatorTest` 72<br>`GroupRunSchemaTest` 10<br>仪器 `GroupRunDAOTest` 12 条**未跑** | `verified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**同 `round_id` 重试跳过的幂等台账**——`C1LiveModelSequenceTest` 其余 4 条（含续跑那条）**仍未在真机全绿过**（硬理由⑤）。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️ 导入侧不涉及续跑幂等；「同 `round_id` 重试跳过已提交 turn」仍零份。**<br>**第十一轮订正（HEAD `a8302e463`，2026-10-06）：仍是 `unverified`——本批未跑同 `round_id` 重试/幂等路径。** 三条新用例与 pipeline 复跑都没有制造「失败后重试」场景。**<br>**第十二轮订正（HEAD `cddaa9959`）：C1-06 的失败尝试没有形成可用的续跑现场 ⇒ 状态不变。**<br>**第十三轮订正（HEAD `18d730465`，2026-10-06）：仍是 `unverified`——本批未跑同 `round_id` 重试/幂等路径（C1-06 的两轮设计不构成续跑现场）。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：仍是 `unverified`。** 本批未碰同 `round_id` 重试/幂等路径；该路径的仪器测试**仍未写**（矩阵 JVM 列已记 `GroupRunDAOTest` 12 条未跑）⇒ 不升级。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：仍是 `unverified`。** 新增设备侧测试 `34145bf49`（`C1GroupRetryResumeDeviceTest.kt` 968 行）但**未真机跑**；`d6494e49a` 补上群聊 UI 续跑入口（契约 `:204`/`:227-228` 的落点；该测试自述「群聊 UI 无重试入口」已过期），但无真机证据，见第十五轮（零设备）③/⑤。<br>**真机第十五轮订正（HEAD `37630adb4`，2026-10-07）：仍 `unverified`。** 设备侧用例 `C1GroupRetryResumeDeviceTest` **2/2 稳定 `FAILURES!!!`**，失败在 `:378`「阶段 1 的 b 错误节点必须原样保留」（Time 15.843 / 13.337）；raw phase1 `FAILED/committed=[a]/skipped=[c]/spent=1741/run_token 604b0069-…`、phase2 `COMPLETED/committed=[a,b,c]/spent=14819/同 run_token/同 started_at`、a 消息 id 不变、b/c 各 1 条、`spent==Σusage`。⚠️ **是测试期望错还是生产缺陷，待另一任务判定**；本轮只登记现状 ⇒ 不升。详见「C1 真机证据采集第十五轮」②。<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：仍 `unverified`。** `5c362851a` 把设备侧断言 `:378` 改成「同一 `MessageNode` 内候选替换 + 切 `selectIndex`」的**测试期望修正**（判定：测试期望写错，非生产缺陷），但**真机未复跑**；契约 `:204` 幂等台账真机实证仍零份 ⇒ 不升。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：升 `verified`，8 项就地补值。** commit=HEAD `bd88aaff2`（设备轮零 commit）；命令退出码=`:app:assembleDebug :app:assembleDebugAndroidTest` exit 0 + 两 APK `install -r -t` Success + `am instrument` 日志 `OK (1 test)`/Time 13.108（shell 退出码不可判，遗留 41）；设备=PKG110/Android 16/API 36/arm64-v8a；输入=`mode=pipeline`、conversation `a0e965f1-…`、`round_id=round-832cd2cc-…`、USER「请三位依次发言，每位一句话。」；viewer=a `[832cd2cc-…, 9298d95f-…]`、b `[832cd2cc-…, 9298d95f-…, b55251a7-…]`、c `[832cd2cc-…, b55251a7-…, f7d97c05-…]`；序列=a `mock-retry-a 6186+22`、b `mock-retry-b 6217+322`、c `mock-retry-c 6517+22`（主机 mock `requests.jsonl` 独立记录一致）；token=phase1 `6208` / phase2 `19286==a+b+c`、`run_token_reused=true`、`group_runs rows=1`；导出=`c1-retry-export-x.jsonl` 5063 B/5 行 `be3709800…`（本机复算一致）。契约 `:204` 幂等成立 ⇒ 升。详见「C1 真机证据采集第十六轮」①。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：维持 `verified`（本批未碰续跑幂等）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：维持 `verified`（本批未碰续跑幂等）。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：维持 `verified`（本批未碰续跑幂等）。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持原判**（本批新增 C1-09 同源往返用例 `C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`，只触及 C1-09 点名的导出/QR 路径；本用例点名的路径未被触碰）。|
| C1-08 | 记忆隔离 | 三个 `group:<conversationId>:role:<roleId>` 空间互不串；来源带消息 id | `MemorySpaceGateTest` 9<br>`MemoryToolScopeTest` 7<br>`MemoryAttributionTest` 9<br>`GroupMemorySpacePolicyTest` 6<br>`MemoryExtractorParseTest` 7<br>`MemoryRoleIdMappingTest` 2<br>`GroupRunSchemaTest` 10 | `verified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**三个 `group:<conv>:role:<role>` 空间的检索可见性 + `source_message_id` 归因**——**零份**，本轮这三项一条都没碰。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️⚠️ 本批**恰好给本行添了一条新的未验证义务**：`memory_space_id` 在导入场景**无解**（新会话 id 导入时未知），所以 `importGroup` 放过它是必然的，**调用方建完会话后必须补跑一次 `validate(config, newId)`**，否则会造出**记忆空间键错位**的群——⚠️ **「调用方是否补做」无任何证据**（没有调用方）。已登记为「已知遗留与风险」第 31 条。**<br>**第十一轮订正（HEAD `a8302e463`，2026-10-06）：仍是 `unverified`——本批未碰记忆空间。** 新用例只跑群聊上下文过滤，没有种入/检索三个 `group:<conv>:role:<role>` 空间、也没有查 `source_message_id` 归因。**<br>**第十二轮订正（HEAD `cddaa9959`）：阶段 A 用生产 `MemoryRepository` 种入 canary 记忆并确认不入导出包（安全侧），但那不是本行要的「空间隔离 + 来源归因」⇒ 状态不变。**<br>**第十三轮订正（HEAD `18d730465`，2026-10-06）：仍是 `unverified`——本批未碰记忆空间（C1-06 群聊不用记忆，未种入/检索三个 `group:<conv>:role:<role>` 空间，也未查 `source_message_id`）。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：仍是 `unverified`（尽管本批 #7 通过）。** `realProviderGroupMemoryIsolationRecordsPerSpaceHits` 真机通过（Time 0.647）：三空间 `group:<conv>:role:a` / `…:role:b` / `…:role:c` 各命中 1 条 + `role_id` + `source_message_id`（a→`…a008`/b→`…b008`/c→`…c008`）；`foreign_query_hit_counts` 全 `[0,0]`；viewer 重过滤 a 的记忆对 a 可见 1、**对 b 可见 0**；全局/助手空间金丝雀命中 0、无回退。⚠️ **但契约 8 项缺 3 项**：① 实际模型调用序列 ② prompt+completion token ③ 导出 SHA-256——`evidence_kind=device-instrumentation-memory-isolation-no-gateway`，**本例设计上不走网关、不产出群导出**（提取用 canned 确定性 JSON）。契约 `:206`「每例保存…模型调用序列、token 计数、导出哈希」逐字要求 ⇒ 按判定规则第 2 条不升级，缺项如实记。见第十四轮①/⑥。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：仍是 `unverified`。** 契约 `:206` 缺三项（模型序列 / token / 导出哈希）；本轮评估结论为**不产出**（`extractFromTurn` 丢 usage ⇒ 语义正交；记忆隔离不产出群导出 ⇒ 不适用），仍缺，见第十五轮（零设备）⑥。<br>**真机第十五轮订正（HEAD `37630adb4`，2026-10-07）：仍 `unverified`。** 本批未碰记忆隔离路径；契约 `:206` 仍缺 3 项（模型调用序列 / token / 导出哈希），评估结论为**语义正交 / 不适用**、**待用户认可**（见第十五轮（零设备）⑥）⇒ 不升。<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：仍 `unverified`。** 本批未碰记忆隔离；契约 `:206` 仍缺模型序列 / token / 导出哈希三项 ⇒ 不升。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：仍 `unverified`。** 本批未碰记忆隔离；契约 `:206` 仍缺模型序列 / token / 导出哈希三项（语义正交 / 不适用，待用户认可）⇒ 不升。见「C1 真机证据采集第十六轮」⑤。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：维持 `unverified`（本批未碰记忆隔离；契约 `:206` 三类产物结构性不产出待用户认可）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：**升 `verified`** —— 旧串扰断言恒真已就地标注，替换为结构性判据 `memoryIsolationViolations`（只看 space_id/role_id/source_message_id/无回退）；真机重跑 `OK (1 test)`/Time 18.577（证据 `8944b371…`），JVM 镜像 7 例变异实测 6 failed 证判别力（`2a0610705`）；契约 `:203` 空间键/归因/无回退 + `:206` 8 字段齐。见「第十八轮」②。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：维持 `verified`（本批未碰记忆隔离）。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持原判**（本批新增 C1-09 同源往返用例 `C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`，只触及 C1-09 点名的导出/QR 路径；本用例点名的路径未被触碰）。|
| C1-09 | Tavern/QR 往返 | 群配置、角色卡、role/轮次/分支哈希一致；不含密钥、记忆、授权 token | `GroupTavernExportTest` 22<br>`TavernCompatTest` 21<br>`QrScannerSheetTest` 16<br>`GroupChatTest` 22<br>`C1pGroupExportHashTest` 5 | `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**酒馆本体打开群聊导出文件 + 相机扫码链路**——**两份都零**；⚠️ 本轮只验了群配置面板的**渲染与滚动**，面板里的**保存 / 二维码 / 扫码这三个业务动作一个都没点过**。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️ 本行是本批**唯一被直接触及**的一行：`importGroup` 补上了密钥黑名单、角色卡去重与配置校验。但 ⚠️ **运行时证据仍为零**（`importGroup` 无调用方），且 **`importShare` 一行未动**（它仍是全拒口径，与 `importGroup` 的「结构性拒 + 两项归一化」**有意不同**，依据在 KDoc 但属单方面判断）——所以**酒馆本体打开群聊导出文件**与**相机扫码**这两份**仍然是零**。**<br>**第九轮（HEAD `4d73a26b4`，2026-10-06）：仍是 `unverified`（但酒馆一侧从「零份」推进到「解析器级接受」）。** 用**生产编解码器产出的真实导出文件**（`C1pGroupExportHashTest` → `pipeline_3roles_2rounds_jsonl`，3615 B / `36e6585f…c8b9`）+ **SillyTavern 钉死 commit `06bde939` 的解析器片段** harness，**16/16 断言通过、退出码 0**（登记时自跑复核）；六条断言与 5 条边界见「C1 真机证据采集第九轮」⑤。⚠️ **不升级**：解析器级 ≠ 完整酒馆 app（无群注册 / 无写盘 / 无 UI）；相机扫码仍零份；`open→save` 会丢私有表头块。**<br>**第十二轮订正（HEAD `cddaa9959`，2026-10-06）：仍是 `unverified`，但「真机导出的 SHA-256」一类首次有值。** `C1GroupExportDeviceEvidenceTest` 真机通过（Time 0.382 / `OK (1 test)`）：生产 `TavernChatCodec.exportGroupJsonl` + 生产 IO 助手 `writeExportTempFile` 落盘 2938 B 真文件，设备侧 `MessageDigest` 与 `adb pull` 后本机 `sha256sum` **双值一致**（`4945b854…6f4b`）；导出含 3 角色 config + 3 张角色卡、5 种 `turn_kind` 全覆盖（8 行）；**四项安全检查全 true**（API key / 隐私记忆 / 工具授权 token 不入包、37 个黑名单键 0 命中，金丝雀先证实真的落进 store）。⚠️ **仍缺**：`ACTION_SEND` 系统分享面板、SillyTavern 本体打开、相机扫码、QR/导入往返；仅 pipeline 一种 mode、单次通过 ⇒ 按 `:205`/`:206` **不升级**。详见「C1 真机证据采集第十二轮」①。**<br>**第十三轮订正（HEAD `18d730465`，2026-10-06）：与本行无关（本批为 C1-06 取消 + 真实网关 5 条稳定性）⇒ 状态不变（仍 `unverified`）。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：与本行无关 ⇒ 状态不变（仍 `unverified`）。** 本批 5 条全部不涉及 Tavern/QR 往返；酒馆本体 / `ACTION_SEND` / 相机扫码仍零份。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：仍是 `unverified`。** 本批未碰；酒馆本体 / `ACTION_SEND` / 相机扫码仍零份，见第十五轮（零设备）⑦。<br>**真机第十五轮订正（HEAD `37630adb4`，2026-10-07）：仍 `unverified`。** 本批未碰 Tavern/QR 往返；酒馆本体 / `ACTION_SEND` / 相机扫码仍零份 ⇒ 不升。<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：仍 `unverified`。** 本批未碰；酒馆本体 / `ACTION_SEND` / 相机扫码仍零份 ⇒ 不升。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：仍 `unverified`。** 本批未碰；酒馆本体 / `ACTION_SEND` / 相机扫码仍零份 ⇒ 不升。见「C1 真机证据采集第十六轮」⑤。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：C1-09：**维持 `unverified`**。本批拿到 C1-09 迄今最强的一批证据（真 SillyTavern **应用级**导入：字节级 copy + 服务端解析正确 + 私有键保留 + 野生 jsonl 行为 + open→save 丢私有块；二维码位图**端到端真机 6/6**：生产 `encodeQr`→`encodeQrBitmap`→MLKit 解码→逐字段→`importShare`；导出 FileProvider URI 字节 == 生产 JSONL；ACTION_SEND 源码护栏 5 例；⑤ 中文有损修复后真机复跑 + 非空验证），**但仍不升**：① 契约 `:206` 三类产物（各 viewer 可见消息 ID / 实际模型调用序列 / prompt+completion token）本例**结构性不产出**、未获用户认可（同 C1-08/C1-10 处置，缺失不等于满足）；② 契约 `:205` App 侧**真实相机扫码链路**（`QrScannerSheet` 的 CameraX `analyzeFrame` 的 `mediaImage`）**从未在设备上跑过**（位图路径只差图片来源却恰绕过它）；③ 契约 `:232-235` + 判定规则第九条：ACTION_SEND 是**源码护栏 + 同形 Intent**，**真实系统分享面板 UI 交互零份**（该用例自述「不弹分享面板」）；④ 酒馆 `open→save` 丢私有块、重存文件回 KhatKit 得 `Unsupported`（⚠️ **订正：不是 `NoConfig`**，`TavernChatCodec.kt:298` 表头无 `khatkit_group` 直接 `return null`）⇒ **打开并回导闭环不成立**；SillyTavern 多用户 / `--listen` 未验。**升格还差**：相机扫码真机链路 + 系统分享面板真机交互 + 用户对「三类产物结构性不适用」的明确认可（逐条见「C1 真机证据采集第十七轮」⑦）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：维持 `unverified`（本批未碰 Tavern/QR）。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：维持 `unverified`（本批未碰 Tavern/QR）。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持 `unverified`。** 本批新增同源往返用例首次把契约 `:206` 的三类产物（viewer 可见消息 ID / 调用序列 / token）与同群 QR 载荷**硬连接**（同一真机一轮：导出哈希来自该会话、QR config 强制 == 存储 config == 导出内嵌 config，非空验证真红），**消掉「三类产物结构性不适用」这条旧理由**；相机扫码与酒馆应用级亦闭环。**但真实系统分享面板（`ACTION_SEND` chooser）仍零份**——契约 `:219`（C1-U 交付 = Compose/UI 测试 + 真机手动记录）许可的「真机手动记录」缺失、判定规则第九条下源码护栏 + 同形 Intent 不能顶替，`ACTION_SEND` 这一步只有代码级证据 ⇒ `:206`/`:232-235` 未整体满足，**维持**。见「C1 真机证据采集第二十轮」③。|
| C1-10 | 单聊/群聊共存 | 同一列表混排；类型筛选只过滤；切换后消息与会话数据不丢 | `ConversationListQueryPlanTest` 7<br>`ConversationTypeFilterSourceGuardTest` 2<br>**`ConversationSearchLikePatternTest` 12**<br>`GroupChatTest` 22 | `verified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**成员头像组 / @ 选择器 / 抽屉筛选 chip 的真机 UI 端到端**——**一项都没做**（硬理由②）；主机侧那 438 条断言是 SQL 语义，替代不了 UI。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️ 导入侧不涉及；成员头像组 / @ 选择器 / 抽屉筛选 chip 的**真机 UI 端到端仍一项都没做**（硬理由②）。**<br>**第九轮（HEAD `4d73a26b4`，2026-10-06）：仍是 `unverified`。** chip 的「真的按类型过滤」+「不丢数据」两半首次拿到**真机 UI 级证据**（单聊下 0 群、群聊下 0 单聊；切回 XML 与切走前逐字节相同；切换前后主库 6 行与 `rikka_hub`/`-wal`/`-shm` 三文件 SHA 完全不变——见「C1 真机证据采集第九轮」①）。⚠️ **不升级**：① 契约 `:232-235` 明写「只看截图或只看 UI 状态均标记 `unverified`」，且仓内无对应 androidTest；② 成员头像组仍只有弱证据（无断言、点按未证实）；③ 契约四类产物本行仍零份。**<br>**第十轮订正（HEAD `d133b400b`，2026-10-06）：② 句里「成员头像组（无断言）」已过期**——本批纳入 `C1GroupFilterAndAvatarUiTest.groupMemberBarRendersOneAvatarPerRole` 并**真机通过**（`INSTRUMENTATION_STATUS_CODE: 0`），成员头像组首次有 **androidTest 级自动化断言**；但同批 chip / @ 两条**未跑完**（`Process crashed`，一次 `kill -3` 操作失误）、**非空验证未做**，且该断言对象是头像组渲染、**不是 C1-10 点名的筛选/混排路径**；① 句里「仓内无对应 androidTest」仅对头像组这半句失效（chip 仍无 androidTest）。⇒ **判定仍不升级，仍 `unverified`**（逐行依据见「C1 真机证据采集第十轮」⑨）。**<br>**第十一轮订正（HEAD `a8302e463`，2026-10-06）：仍是 `unverified`——但两条 UI 测试首次真机通过。** `typeFilterChipsFilterWithoutLosingData`（Time 4.026；单聊过滤下群条目不可达、切回后 4 条 DB 行原样）与 `atMentionPickerInsertsParseableMention`（Time 4.365；`@Alice Johnson`→`[alice]`、`@阿尔法`→`[alpha]`、反伪 `@johnson`→`[]`）都是**真机 androidTest（Compose 语义树断言）**不是截图；成员头像组本轮未重跑（复用第十轮证据）。⚠️ **不升级**：契约 `:206` 末句「不得仅凭 UI 截图勾选」与 `:232-235`「只看截图或只看 UI 状态均标记 `unverified`」；本行契约四类产物仍零份；筛选测试不是四 chip 全流程、`PagingSource` 并发/快照/回滚未验；非空破坏验证未做。**<br>**第十二轮订正（HEAD `cddaa9959`）：C1-09/C1-06 与本行无关 ⇒ 状态不变。**<br>**第十三轮订正（HEAD `18d730465`，2026-10-06）：与本行无关（本批未碰筛选/混排/UI）⇒ 状态不变（仍 `unverified`）。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：与本行无关 ⇒ 状态不变（仍 `unverified`）。** 本批 5 条全部不涉及筛选/混排/真机 UI。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：仍是 `unverified`。** 新增设备侧测试 `1c48b1701`（`C1GroupPagingAndFilterDeviceTest.kt` 813 行 / 8 `@Test`）但**未真机跑**；契约 `:232-235` 不认仅 UI 级证据、四类产物零份，见第十五轮（零设备）⑤/⑦。<br>**真机第十五轮订正（HEAD `37630adb4`，2026-10-07）：仍 `unverified`。** 设备侧用例 `C1GroupPagingAndFilterDeviceTest` 8 方法 **3 通过 / 5 失败**：通过 `typeFilter_allChipsFilterIndividuallyAndRestoreExactly` / `typeFilter_chipSwitchNeverLosesData_dbCountsStable` / `mixedList_groupBadgeRendersOnlyOnGroupRows`；失败 5 条均 `pagingSource_*`（`:357` 15≠20、`:392` 55≠60、`:435` 25≠40、`:493` 55≠60、`:527` 25≠40）。真机 DB 实测 55 行（GROUP=25/DIRECT=30），生产 `PAGE_SIZE=20`/`INITIAL_LOAD_SIZE=40` 但累计取回 60/40 ⇒ 重叠/超取，**另一任务在修** ⇒ 不升。详见「C1 真机证据采集第十五轮」②。<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：仍 `unverified`。** `0ae9f570a`（测试驱动改首屏 Refresh / 后续 Append）+ `5ca9ecbaf`（生产 `ConversationRepository` 四个 offset 分页 API 改 `Append`）已修 5 条 `pagingSource_*` 失败，但**真机未复跑**、生产修复只影响 HTTP 分页 API、契约 `:232-235` 不认仅 UI/逻辑级证据、四类产物零份 ⇒ 不升。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：仍 `unverified`（但 8/8 全绿）。** `0ae9f570a`+`5ca9ecbaf` 修复后本批真机 `C1GroupPagingAndFilterDeviceTest` **8/8 `OK (1 test)`（两遍都 8/8）**，上一批 5 条 `pagingSource_*` 失败全部转绿（#4 `second_size=15`、#5 `[20,20,15]/55/55/55`、#6 `group=[20,5]/25`、#7 `rows=55`、#8 `25/55`）。但 **契约 `:232-235`「只看截图或只看 UI 状态均标记 `unverified`」**（#1/#2/#3 落 Compose 语义树 / badge 节点）+ **契约 `:206` 逐例四类产物（viewer 可见消息 ID / 模型调用序列 / token / 导出哈希）本例结构性不产出**（不启发模型、不导出群文件）⇒ 维持 `unverified`。见「C1 真机证据采集第十六轮」②/⑤。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：维持 `unverified`（本批未碰抽屉分页/筛选）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：维持 `unverified` —— 新增的真实网关轮（`realGatewayRoundInFixtureGroupProducesContractArtifacts`）跑在既有夹具群上，**与 C1-10 点名的「单聊/群聊筛选/混排」路径正交**，其 8 字段不由该路径产出；筛选/混排本身仍只有 UI/分页/SQL 级证据，契约 `:232-235` 不认仅 UI 状态 ⇒ 不升。见「第十八轮」③。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：**升 `verified`** —— 第 9 条改造为从「群聊」筛选路径内抵达群聊并真跑一轮（点 chip → 生产 DAO 取筛选首屏 → 抽屉点击该行 → 反射读 `NavViewModel.currentPage` 断言导航 → 在该群跑真网关轮），8 字段首次由**筛选路径打开的群**产出；契约 `:232-235` 不再是「只看 UI 状态」、`:206` 8 字段齐（commit/命令退出码/设备/输入/viewer 台账/调用序列/token/导出哈希）⇒ 由 `unverified` 改 `verified`。见「C1 真机证据采集第十九轮」②。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持原判**（本批新增 C1-09 同源往返用例 `C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`，只触及 C1-09 点名的导出/QR 路径；本用例点名的路径未被触碰）。|

⚠️ **本轮（HEAD `6ba95422`）只动了两个类的用例数，其余一个数没动**：
`GroupTurnCoordinatorTest` **63 → 72**（`9844cbc4` 加 3 条 `mention_role_ids`
放行分支覆盖 + `e7ad2a77` 加 6 条视角过滤盲区覆盖）、
`GroupChatTest` **18 → 22**（`af104850` 加 4 条 `role_id == SUMMARY_ID` 拒绝用例）。
两个数都取自 `app/build/test-results/testDebugUnitTest/*.xml` 的 `tests` 属性
（本轮用 Python 逐个 XML 求和实测）。
⚠️ **状态列一个格都没动，仍是 10 行全 `unverified`**——新增的是**代码层覆盖**，
不是契约 `:206` 点名的验收证据；理由见「C1 真机证据采集第三轮」的
「⚠️⚠️⚠️ 为什么十例仍然 0/10」。

⚠️⚠️⚠️ **再往后一批（HEAD `361c7cf6`）：`GroupTurnCoordinatorTest` 72 → 84，
另加两个源码文本护栏类。⚠️ 状态列仍然一个格都没动。**
- `GroupTurnCoordinatorTest` **72 → 84**（`2fdee352` 加 6 条真单测 → 78、
  `8ccc0264` 再加 6 条真单测 → 84），⚠️ **这两笔加的全是纯函数真单测**：
  提交路的归属判定 `checkCommitAdmission`、内核 `advance` 的终态守卫
  `Advance.Halted`、失败路的 `checkFailureAdmission`。
- **新增两个源码文本护栏类**：`GroupStaleJobCommitSourceGuardTest` 7（②）、
  `GroupStaleJobFailureSourceGuardTest` 6（③）；`GroupTieBreakScaffoldingDropSourceGuardTest`
  **3 → 7**（①，`:8a5f89d7`）。
- ⚠️ **为什么这批连一条 `verified` 都不该给**：三批改的全是**发现并堵住的真缺陷**
  （平票脚手架泄漏、残留旧 job 跨轮盖戳 / 跨轮写 FAILED），**它们证明的是「代码不再
  有这条错路」，不是「契约 `:206` 点名的四类产物被采到了」**。尤其 ②③ 针对的残留旧
  job 路径**从未在真机复现过**——本轮零设备。
- 「JVM 证据类」列**本轮一格未改**（它引的是各测试类用例数，本轮改的三个类里有
  `GroupTurnCoordinatorTest` 出现在 C1-01/02/03/04/05/06/07 七行的 JVM 证据格里，
  那些格子里的 `72` 是**第四个窗口的实测值，按惯例保留不覆写**；当前值 84 见下方台账）。

C1 相关共 **25 个测试类 / 304 个用例**，全类名带包名前缀为
`heizige.kk.khatkit.app.`。逐类用例数（`tests` 属性实测）：
`feature.chat.GroupTurnCoordinatorTest` 63、`feature.chat.GroupTavernExportTest` 22、
`core.data.ai.tavern.TavernCompatTest` 21、`core.data.model.GroupChatTest` 18、
`feature.chat.UngeneratedMessageFilterTest` 18、
`core.ui.components.ui.QrScannerSheetTest` 16、`feature.chat.GroupTurnModelTest` 14、
`feature.chat.GroupMessageModelTest` 18、`core.data.db.migrations.GroupRunSchemaTest` 10、
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
⚠️⚠️ **上面这句已被 2026-10-05 的真机窗口订正（按惯例保留不覆写）**：那 25 条现在
**25/25 全绿**，而且**又多了 3 条**——`C1DeviceEvidenceTest`（`2c1d7632`，1144 行），
所以「C1 相关仪器测试」当前是 **28 条**。新数据见「C1 真机证据采集（2026-10-05，
OnePlus PKG110）」。

⚠️⚠️⚠️ **本段自己跟自己矛盾，先记下来（本轮订正，按惯例不覆写原文）**：这段开头写
「25 个测试类 / 304 个用例」，段尾写「合计 296」，**两个数互相对不上**（304 ≠ 296），
它们是**两批不同窗口各自心算的旧口径**。正文另一处（「现状」段的「不要把已有
`GroupChat.kt` / `GroupChatPage` / 296 条 JVM 用例当成验收通过」）引的是 296 那一个，
所以同一份文档里出现了 296 / 304 两个「C1 相关用例数」。**权威口径只有一个**：
下方「C1 相关 JVM 测试类台账」那张表，口径是「**本文件正文点名引用过、且能在
`app/build/test-results/testDebugUnitTest/*.xml` 里对上的类**」，可复算。
⚠️ **本轮（HEAD `6ba95422`）按该口径重算的实测值是 35 个测试类 / 366 个用例**
（台账表已同步更新）。复算命令见「C1 相关 JVM 测试类台账」小节。
⚠️ 上面那个 25 类 / 304 例 / 296 例的清单**不要再当现行值引用**。

## 证据登记

每个用例至少填写一行；多设备或多实现结果追加行，不覆盖历史证据。

命令编号见下方「构建与验证证据」：`CMD-1` = `:app:testDebugUnitTest --rerun`、
`CMD-2` = lint 三连 `--rerun`、`CMD-3` = `packageDebug/assembleDebug --rerun`，
三条退出码均为 0。XML 路径前缀统一为
`app/build/test-results/testDebugUnitTest/TEST-`，下表省略包名前缀
`heizige.kk.khatkit.app.`。

| 用例 | commit | 命令·退出码 | 设备·Android | 输入或 fixture | viewer 可见消息 ID | 模型调用序列 | token (prompt+completion) | 导出 SHA-256 | 证据路径 | 状态 |
|---|---|---|---|---|---|---|---:|---|---|---|
| C1-01 三角色显式 @ | `4534f02a` 新增 GroupTurnCoordinator 纯判定内核<br>`be41fdc9` 补 63 条内核用例（含视角隔离）<br>`6c8d4932` 新增 GroupRoleCompletionProvider<br>`e5dde832` 补 8 条防伪 @ 边界用例<br>（4 个 SHA 均在锚定区间 `d45ebd10~1..1b0e04a9` 内，已 `git cat-file -t` 逐个核过） | `CMD-1` 退出码 **0**<br>2026-10-04 23:48 强制 `--rerun`，84 个 XML 全部重写<br>`CMD-2` 退出码 **0**（该例无 lint 命中，见下）<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**。`adb devices` 输出为空列表；磁盘上唯一 app 仪器测试记录是 2026-10-03 12:58:29 的 `Process crashed`（`tests="0"`），早于基线 13.5 小时；Android 版本无记录<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。 | 输入 = JVM 测试类：<br>`feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>`feature.chat.GroupRoleCompletionProviderTest`（8）<br>fixture 为 Kotlin 内联构造的 `GroupConfig`/`UIMessage` 列表，无外部文件 | **无证据（需真机）**。JVM 只断言 `viewerMessages(...)` 返回的 `UIMessage` 列表内容，没有把断言里的消息落成带真实 `message.id` 的台账，磁盘上没有「角色 A 可见 = [id1,id3]」这种记录<br>**2026-10-05 夹具层台账（部分证据）**：真机真 Room 库，夹具 **7 条消息**（作者依次 user / a / user / a / b / c / `__summary__`），`a9d2077e` 起**三种 mode 各采一遍**，越权审计三 mode 全部 `passed=true` / `checked_pairs=6` / `violations=[]`。pipeline：`a` 见 user,user,a,a,`__summary__`；`b` 见 user,user,a,b,`__summary__`；`c` 见 user,user,b,c,`__summary__`。roundtable：`c`（chair，`chairRound=true`）见 user,user,**a,b**,c,`__summary__`（议长放开本轮全部）。vote：收窄成 user,user,自己,`__summary__`。<br>**2026-10-05 真实 HTTP 台账**：`round-7098bf9e-4bcb-41f2-a0e7-e05d0395fcc9`，`a`→`7098bf9e…`(user)+`cc3d451c…`(a)；`b`→`+1f69a5bc…`(b)；`c`→`7098bf9e…`+`1f69a5bc…`(b)+`49e3ffcb…`(c)。<br>⚠️ **「只被 @ 角色收到、其他角色不可见」这一句仍未在真机上单独观测到**：夹具的 `@阿尔法` 落在**用户消息**上（`mentionRoleIds` 含 a），而用户消息对**每个 viewer 都可见**，所以 @ 放行分支的独立效果仍只有 JVM 纯函数断言 <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。 | **无证据（需真机）**。仓库里被 git 跟踪的 `*.jsonl` 全部在 `ai/src/test/resources/stream-traces/generated/`（10 个文件），是 AI SDK 的桩事件流，与 C1 无关；没有真实 provider 的调用序列<br>**2026-10-05 真实 HTTP（第一次有真请求）**：`C1LiveModelSequenceTest` 经 `adb reverse tcp:8765 tcp:8765` 接本机 mock OpenAI，走 `ChatManager.sendMessage → GenerationLoop → ProviderManager → OpenAIProvider → ChatCompletionsAPI.streamText → Ktor CIO OkHttpClient` **完整生产链路**；请求特征 `User-Agent: ktor-client`、`accept: text/event-stream`、`stream_options.include_usage: true`、`stream=true`。**seq=1 `mock-model-a` / case main / 发言者 A / 2 条消息 / 201+34 / 只含 `ROLECODE:A`——零个他人输出。**mock 侧把每个请求 body 原样落盘 `requests.jsonl`，**与应用代码无关**，构成第三方旁证。⚠️ 显式 @ 的**单角色投递**没实跑（本轮是无 @ 的 pipeline），C1-01 要的「这一轮只有 B 被调用」仍零份 <br>**2026-10-05 第四个窗口：仍然是零份**——真实公网网关那轮跑的是**无 @ 的 pipeline**，`mode` 只有 pipeline 一种，本例要的那条路径**没有被真实调用过**。 | **无证据（需真实模型）**。`GroupTurnCoordinatorTest` 的预算断言喂的是构造出来的 token 数，不是真实 completion 计数<br>**2026-10-05 真实 usage（第一次不是构造值）**：seq1 prompt **201** + completion **34** = **235**，落 `c1-live-evidence-main.json`，`evidence_kind=real-http-capture-via-adb-reverse`。⚠️ **本例要的是「只 @ 到一个角色」那一轮的 token 累计，那一轮没实跑**；且 usage 是 mock 按 `ceil(bytes/4)` 估的（**原始字节数已落盘可手算复核**），**不是真 tokenizer 的结果** <br>**2026-10-05 第四个窗口：仍然是零份**——本例要的是「只 @ 到一个角色那一轮」的累计，真实网关那轮是无 @ 的 pipeline。⚠️ 顺带纠正一处旧认知：`GroupConfigSheet` 的 `role_id` 是**只读 `Text`**（`GroupChatPage.kt:754`，在 `RoleEditor` 里），**UI 手输路径不存在**，所以「构造一个 `role_id = __summary__` 的角色」只能走外部 JSON 导入（`importShare` 第 5 道闸门，`GroupChat.kt:832-838`）。 | **无证据**。C1-01 不产出群聊导出文件；`app/build/outputs/apk/debug/*.apk` 的 SHA-256 是安装包哈希，与「群聊导出文件哈希」不是一回事，不能填进本列 | `app/build/test-results/testDebugUnitTest/TEST-heizige.kk.khatkit.app.feature.chat.GroupTurnCoordinatorTest.xml`<br>`…GroupChatTest.xml`<br>`…GroupRoleCompletionProviderTest.xml`<br>主源码：`app/src/main/java/heizige/kk/khatkit/app/core/data/model/GroupChat.kt` <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件在 `6ba95422` 那版为 **1982 行 / 5 个 `@Test`**，HEAD `74d82476` 已增至 **2221 行 / 5 个 `@Test`**（`@Test` 个数未变））；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。 | `verified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**显式 @ 的投递收窄**——夹具的 `@` 仍落在用户消息上（用户消息对每个 viewer 都可见），「其他角色可见」这半句在真机证据里仍然读不出来。详见「已知遗留与风险」第 15 条。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️ 本行要的三视角可见消息 ID 台账仍零份；本批**没有触碰 @ 投递路径**。**<br>**第十一轮订正（2026-10-06）：真实网关投递收窄首次有值——只调用 b（`glm-5.2`）、A/C 看不到 b 的本轮输出（viewer 台账与断言见第十一轮②）；⚠️ 触发是 USER 消息、A/C 仍可见该条（生产口径，测试 `:1954-1973`）；仍缺导出哈希 + 退出码 + 重复稳定性 ⇒ `unverified` 不变。**<br>**第十三轮订正（2026-10-06）：mention 真网关 3/3 全绿（Time 6.674/7.707/8.249）⇒ 重复稳定性从「未做」变「已做」；字面「其他角色不可见」半 + 导出哈希一格仍缺 ⇒ `unverified` 不变。见第十三轮①。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：仍是 `unverified`。** 本批 #3 mention 真实网关通过（Time 6.567 / 日志 `OK (1 test)`；只调用 b / wire `glm-5.2` / usage 6546+98=6644 / viewer 台账 / 导出 `6e2ec8ce…` 1747 B）——**契约 8 项首次全齐**；但契约 `:201`「其他角色不可见」的字面半仍未观测（触发是 USER 消息、A/C 仍可见它，遗留第 15 条不变）⇒ 本行点名的必须断言未完整满足，按判定规则第 2 条不升级。见第十四轮①/⑤。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：仍是 `unverified`。** 契约 `:201`「只被 @ 角色收到该消息；其他角色不可见」的**字面半**仍未观测（触发是 USER 消息、A/C 仍可见它，遗留第 15 条）；本批零设备、四类产物零份，见第十五轮（零设备）⑦。<br>**第十六轮订正（文档/契约复核，HEAD `00bcdd4e9`，2026-10-07）：升为 `verified`（契约 `:201` 的「投递收窄」口径；契约依据见用例矩阵 C1-01 同轮订正——`:200` 明文规定 USER 消息对所有 viewer 放行，故 A/C 仍可见触发 USER 消息不构成缺口）。按契约 `:232-235` 八项逐项填齐（证据 = 真机 `c1-live-evidence-real-mention.json`）：① commit=`a9df57c96`（写本用例所在文件（逐例导出 + viewer 台账）那次，`git cat-file -t` 核为 commit；采集时 HEAD=`b60aa80c5`）；② 测试命令及退出码=`am instrument` 单跑 `heizige.kk.khatkit.app.feature.chat.C1LiveModelSequenceTest#realProviderMentionNarrowsSpeakersToMentionedRole`，日志正文 `OK (1 test)` / `Time: 6.567`（⚠️ 本 run 未落 `exitcode.txt`，按遗留第 41 条 `am instrument` 的 shell 退出码不可当判据，仅以日志正文为准）；③ 设备/Android 版本=OnePlus PKG110 / Android 16 / API level 36 / arm64-v8a（证据 JSON `device.sdk=36`）；④ 用例输入=`@角色乙 请只由你发言一次。`（mode=pipeline、三角色 a/b/c、`token_budget_per_round=100000`）；⑤ 各 viewer 的可见消息 ID：a=[`23043da1-efd2-4cdf-aba3-07f221f3bb8f`]、b=[`23043da1-efd2-4cdf-aba3-07f221f3bb8f`, `0cae6bb4-7f26-497c-a68e-e63f6f585b58`]、c=[`23043da1-efd2-4cdf-aba3-07f221f3bb8f`]；⑥ 实际模型调用序列=seq1 role b / wire `glm-5.2` / provenance `wire_response_model`（`only_mentioned_role_invoked=true`）；⑦ prompt+completion token=6546+98=6644（`spent_tokens=6644` 与落库 `group_runs` 相等）；⑧ 导出 SHA-256=`6e2ec8ceff572bf2ba3af0376907347ebed83768cd7ac0d08293bcb6dff7fcdd`（`c1-export-real-mention.jsonl`，1747 B / 3 行）。证据 JSON 本体 SHA-256=`56efdb48298ab1c6d24401edbaf9f0d2081ece52bc1c56dbf4b3af01d36aea0a`（10270 B，登记子代理本机 `sha256sum` 复算）。⚠️ 本行「导出 SHA-256」列此前记「无证据（C1-01 不产出群聊导出文件）」，该旧结论已被第十四轮真实网关跑直接推翻（本用例同时写出群导出）。**<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：维持 `verified`。** 本批零设备、四类产物零份，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（逐格见「零设备修复批次（2026-10-07，HEAD `5c362851a`）」⑥。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：维持 `verified`。** 本批真机窗口只跑 C1-07 与 C1-10 两条路径，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（见「C1 真机证据采集第十六轮」⑤）。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：维持 `verified`（本批未碰显式 @ 路径）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：维持 `verified`（本批未碰显式 @ 路径）。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：维持 `verified`（本批未碰显式 @ 路径）。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持原判**（本批新增 C1-09 同源往返用例 `C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`，只触及 C1-09 点名的导出/QR 路径；本用例点名的路径未被触碰）。|
| C1-02 无 @ 的 pipeline | `4534f02a` 新增 GroupTurnCoordinator<br>`dd7b3c79` ChatManager 接上协调器 + `group_runs` 运行日志<br>`b752a310` 更新 ChatScaffold KDoc（modelId 已参与解析）<br>`139a91ba` 图片导出的模型名与气泡同口径 | `CMD-1` 退出码 **0**（同上，强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`GroupTurnModel.kt` lint 零命中）<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**，同 C1-01（`adb devices` 空；唯一仪器记录 2026-10-03 崩溃且 `tests="0"`）<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。 | 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>`feature.chat.GroupTurnModelTest`（14） | **无证据（需真机）**。同 C1-01：只有纯函数返回集合的内容断言，没有真实 `message.id` 可见台账<br>**2026-10-05 夹具层台账**：`a9d2077e` 三 mode 各采一遍，越权审计三 mode 全 `passed=true` / `checked_pairs=6` / `violations=[]`；pipeline 下 `a`=pred=null、`b`=pred=a、`c`=pred=b，`c` 见 user,user,b,c,`__summary__`。<br>**2026-10-05 真实 HTTP 台账（本例最硬的一块）**：pipeline 真跑完，`assistant_role_order=["a","b","c"]`，每个角色**恰好只多看到上一位的输出**——`a`→`7098bf9e…`(user)+`cc3d451c…`(a)；`b`→`+1f69a5bc…`(b)；`c`→`7098bf9e…`+`1f69a5bc…`(b)+`49e3ffcb…`(c)，**c 看不见 a** <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。 | **无证据（需真实 provider）**。契约要求「实际模型调用序列与日志一致」，JVM 侧只能断言 `SpeakerStep` 顺序，没有一次真实请求的 provider/model/顺序记录可导出<br>**2026-10-05 真实 HTTP 序列（契约点名的那一列，第一次有真请求）**：<br>seq1 `mock-model-a` / main / A / 2 msgs / 201+34 / `ROLECODE:A`<br>seq2 `mock-model-b` / main / B / 3 msgs / 235+34 / `ROLECODE:B`,**`A`**<br>seq3 `mock-model-c` / main / C / 3 msgs / 235+34 / `ROLECODE:C`,**`B`**<br>seq4 `mock-model-a` / budget / A / 2 msgs / 201+35 / `ROLECODE:A`<br>**契约要的序列 = `mock-model-a` → `mock-model-b` → `mock-model-c`**，与 `bindings` 声明的 wire 串**逐字一致**；`round_id=round-7098bf9e-4bcb-41f2-a0e7-e05d0395fcc9`、`assistant_role_order=["a","b","c"]`。<br>**逐请求视角隔离审计（真实网络层，不是纯函数判定）**：`a` 只有 system(`ROLECODE:A`)+user，**无 b 无 c**；`b` = system(`ROLECODE:B`)+user+**一条带 `ROLECODE:A` 的 assistant**，**无 `ROLECODE:C`**；`c` = system(`ROLECODE:C`)+user+**一条带 `ROLECODE:B` 的 assistant**，**无 `ROLECODE:A`**。**pipeline「只串联上一位」在真实 HTTP 层成立，不只在 `buildContext` 的单元判定上成立。**⚠️ **mock 不是真实模型**——证据等级是「真实 provider 代码路径 + 真实 HTTP 请求 + 真实 SSE 流 + 真实 usage 报文」，**不是「真实 LLM 推理」** <br>**2026-10-05 第四个窗口（真实公网网关，本例最硬的一块）**：**实际模型调用序列 = `deepseek-v4-flash` → `glm-5.2` → `deepseek-v4-flash`**，与按角色绑定（a/c = `deepseek-v4-flash`、b = `glm-5.2`）**逐位一致**；三个请求都**真的打到公网**（设备直连 `https://api.zenneko.top/v1`，**不经 `adb reverse`、不经本机 mock**）。这直接证明 `resolveGroupTurnModelId` + `TaskRoutes.resolve` 的按角色选型在**真实调用**下成立。⚠️ **模型名不是 wire 级抓包**：序列里的名字是由 `message.modelId` 的 uuid（`5a86b2d6…` / `8b6bf21c…`）经 provider 模型表**反查**出来的，**app 不保存响应的 `model` 字段**；JSON 里已用 `wire_model_name_provenance` 字段显式标明这一点。✅ **订正（HEAD `74d82476`，零设备）**：上面那句「app 不保存响应的 `model` 字段」**已不再成立**——不是解码器没给，而是 `StreamChunkHandler` 收到 `Finish` 时把 `chunk.model` **丢弃**了；该缺陷已修，wire 名现落 `UIMessage.wireModelName`（见「为什么十例仍然 0/10」第 2 条的完整证据与取证局限）。⚠️ **但本列这一格记的仍是反查值**：仪器测试一行没跑，设备上那份证据文件要等真机重跑才会变成 wire 值。⚠️ **该用例没有一次全绿记录**（进程被 OEM 回收策略杀掉、落盘文件被卸载清空），数字来自同一次成功生成的内存快照。<br>**2026-10-06 第八轮（HEAD `31b6b5a52`）：wire 级序列第一次真机落盘。** 真实网关用例重跑并首次产出正式证据（a/b/c 三条 `wire_model_name_provenance` 全为 `wire_response_model`，counts `{wire_response_model:3, fallback:0}`、reconciliation 3/0/0；序列 `deepseek-v4-flash → glm-5.2 → deepseek-v4-flash` 与角色绑定逐位一致；本跑 a `6829+151=6980`、b `6692+60=6752`、c `6895+104=6999`，Σ=20731=spent）。⚠️ **本格旧句「这一格记的仍是反查值 / 要等真机重跑才会变成 wire 值」至此作废**（原文保留）；⚠️ **但本行状态未改**——三处仍未锁死，见「C1 真机证据采集第八轮」⑨。 | **无证据**。pipeline 的 prompt+completion 实际计数未采集<br>**2026-10-05 真实 usage，与落库对账相等**：main 轮各请求 235 + 269 + 269 = **773**，落库 `group_runs.spent_tokens` = **773**，✅ 逐条相等；`status=COMPLETED`、`token_limit=100000`、`committed_role_ids=["a","b","c"]`、`skipped_role_ids=[]`、`reason=""`、`endedAt` 非空。⚠️ 口径是 **prompt + completion 累计**（`GroupTurnCoordinator.usageOf`），**不是** `totalTokens`、也不是最后一条消息的用量。mock usage = `ceil(bytes/4)`，字节数已落盘；seq1 `Content-Length: 24786`（绝大部分是 tools 定义）而 prompt token 只 **201** <br>**2026-10-05 第四个窗口（真实 usage，不是任何估算）**：a `deepseek-v4-flash` prompt **6803** + completion **159** = **6962**；b `glm-5.2` **6667 + 68 = 6735**；c `deepseek-v4-flash` **6880 + 102 = 6982**；**Σ(prompt+completion) = 20679**，落库 `group_runs.spent_tokens = 20679`，**精确相等**；`status=COMPLETED` / `committed=[a,b,c]` / `skipped=[]` / `ended_at` 非空。⚠️ **prompt 6800 量级的来源已定位**：请求体 **25.7KB 里 24675 字节是 23 个工具的定义**（按同一 wire body 本机复现得 6800/6526/6814，与真机同量级）。⚠️ **两个模型都是推理模型**：`max_tokens` 给小了会 `finish_reason=length`、`content` 为空、token 全被 `reasoning_tokens` 吃掉，**给足配额才吐正文**。⚠️ 同上，**没有全绿记录**，也不是 wire 抓包。 | **无证据**。pipeline 路径本轮不导出群聊文件 | `…TEST-heizige.kk.khatkit.app.feature.chat.GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>`…TEST-…GroupTurnModelTest.xml`<br>接线点：`app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatManager.kt:705-741`（群分支取 `GroupTurnEntry.Speak` 并按 `resolveGroupTurnModelId` 选模型） <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件在 `6ba95422` 那版为 **1982 行 / 5 个 `@Test`**，HEAD `74d82476` 已增至 **2221 行 / 5 个 `@Test`**（`@Test` 个数未变））；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。 | `verified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**无 @ pipeline 的真实调用序列与日志一致**——真实网关那轮确实跑了 pipeline，但「一致」只做到「落库/内存里的 `modelId` 与绑定一致」，**没有拿运行日志做对照**，而模型名本身又不是 wire 抓包（硬理由①）。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️ 本批新增的「`mode` 未知一律拒绝」是**导入期**的判定，与本行要的**真实调用序列与日志一致**无关；那一行仍然零份。**<br>**第十一轮订正（2026-10-06）：wire 级序列 + `Σ=20620=spent` + provenance `{3,0}` + viewer 断言 + 全绿记录已齐（第十一轮⑥）；仍缺导出哈希一类、退出码未落盘、单次通过 ⇒ `unverified` 不变。**<br>**第十三轮订正（2026-10-06）：rp 3 次 300s 超时 + rp2 FAIL/FAIL/PASS（唯一通过 `f98f053d…`）⇒ 间歇失败有记录；导出哈希一格仍缺 ⇒ `unverified` 不变。见第十三轮④。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：仍是 `unverified`。** 本批 pipeline 3/3 全失败（Time 27.627/25.884/19.129 / `FAILURES!!!`）：第 3 位角色 c 零产出（`turn_kind=error` / `model_id=null` / usage 全 -1 / `group_run=FAILED/role_failed/committed=[a,b]`），失败原文「角色 c 的 modelId 必须是 deepseek-v4-flash expected:<5a86b2d6-…> but was:<null>」。**根因 = 网关 HTTP 200 但 SSE 无 content delta（空白完成），6/6 稳定复现、非既有非 2xx 缺陷** ⇒ 不升级。见第十四轮②/③。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：仍是 `unverified`。** 本批未跑真实网关 pipeline；`d6494e49a` 的续跑入口与本行「调用序列与日志一致」无关；契约 `:206` 四类产物零份（第十四轮零产出根因未消），见第十五轮（零设备）⑦。<br>**真机第十五轮订正（HEAD `37630adb4`，2026-10-07）：升 `verified`，8 项就地补值。** commit=HEAD `37630adb4`（设备轮零 commit）；命令退出码=`:app:assembleDebug :app:assembleDebugAndroidTest` exit 0 + 两 APK `install -r -t` Success + `am instrument` 日志 `OK (1 test)`/Time 15.294（shell 退出码不可判，遗留41）；设备=PKG110/Android 16/API 36/arm64-v8a；输入=`mode=pipeline`、conversation `51c16875-…`、USER「请三位依次发言，每位一句话。」；viewer=a `[d2d54852-…, 4f0a5585-…]`、b `[d2d54852-…, 4f0a5585-…, 98eea0d4-…]`、c `[d2d54852-…, 98eea0d4-…, 0be40a2d-…]`；序列=a `deepseek-v4-flash 6829+65`、b `glm-5.2 6609+54`、c `deepseek-v4-flash 6891+55`（`provenance={wire:3,fallback:0}`）；token=`Σ=20503==spent_tokens`；导出=`c1-export-real-provider.jsonl` 2357 B/5 行 `5f7914d1…`；证据文件 `c1-live-evidence-real-provider.json` 12674 B `fdd5fc23…`、raw `c1-real-raw-dump-C102.json` 8247 B `a2054681…`。详见「C1 真机证据采集第十五轮」①。<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：维持 `verified`。** 本批零设备、四类产物零份，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（逐格见「零设备修复批次（2026-10-07，HEAD `5c362851a`）」⑥。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：维持 `verified`。** 本批真机窗口只跑 C1-07 与 C1-10 两条路径，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（见「C1 真机证据采集第十六轮」⑤）。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：维持 `verified`（本批未碰真实网关 pipeline）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：维持 `verified`（本批未碰 pipeline）。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：维持 `verified`（本批未碰无 @ pipeline）。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持原判**（本批新增 C1-09 同源往返用例 `C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`，只触及 C1-09 点名的导出/QR 路径；本用例点名的路径未被触碰）。|
| C1-03 roundtable | `4534f02a` 新增 GroupTurnCoordinator<br>`be41fdc9` 补 63 条内核用例<br>`ab5cddb9` 新增 GroupSpeakerResolver（说话者解成纯函数）<br>`d934c4d2` 气泡上方显示说话者 + 关掉群聊不适用的三个动作 | `CMD-1` 退出码 **0**（强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`GroupSpeakerResolver.kt` / `ChatMessage.kt` lint 零命中）<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**，同 C1-01<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。 | 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>`feature.chat.GroupSpeakerResolverTest`（8） | **无证据（需真机）**。「议长前看不到未完成输出」只有集合内容断言，没有逐 viewer 的真实消息 ID 清单<br>**2026-10-05 夹具层台账（议长视角第一次有值）**：真机真 Room 库，三 mode 各采一遍。roundtable 下 `a`=pred=null、`b`=pred=null、**`c`=chairRound=true**，议长可见集合放宽成 user,user,**a,b,c**,`__summary__`（议长放开本轮全部）；非议长仍是 user,user,自己,`__summary__`；越权审计 `passed=true` / `checked_pairs=6` / `violations=[]`。<br>⚠️ **但这只是夹具层**：`chairRound=true` 那个放行分支**在真实 HTTP 层一次都没被触发**——真实调用只跑了 pipeline，**议长汇总轮的真实 prompt 组装仍零份** <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。 | **无证据**。roundtable 的「全员完成 → 仅议长汇总」两段调用序列未实跑<br>**2026-10-05 部分证据（只有 pipeline）**：真实 HTTP 跑了 pipeline 那四条请求（见 C1-02 行）。**roundtable 的两段序列——「全员轮」与「议长汇总轮」——仍然零份**，因为真实 provider 只绑定了 `mode=pipeline` 一种配置。⚠️ 议长 `chairRound=true` 的**实际 prompt 组装**没有任何请求记录，mock 也**没返回过任何候选或汇总文本** <br>**2026-10-05 第四个窗口：仍然是零份**——真实公网网关那轮跑的是**无 @ 的 pipeline**，`mode` 只有 pipeline 一种，本例要的那条路径**没有被真实调用过**。 | **无证据**<br>**2026-10-05 仍缺（只跑了 pipeline）**：pipeline 轮 773 已落库（见 C1-02 行），但 **roundtable 议长汇总轮的真实 usage 零份**——议长那次调用根本没发生 <br>**2026-10-05 第四个窗口：仍然是零份**——议长 `chairRound=true` 那一轮的真实调用没有发生（真实网关只跑了 pipeline），真实 usage 也只有 pipeline 那三个数。 | **无证据** | `…TEST-…GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>`…TEST-…GroupSpeakerResolverTest.xml`<br>主源码：`app/src/main/java/heizige/kk/khatkit/app/feature/chat/GroupSpeakerResolver.kt` <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件在 `6ba95422` 那版为 **1982 行 / 5 个 `@Test`**，HEAD `74d82476` 已增至 **2221 行 / 5 个 `@Test`**（`@Test` 个数未变））；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。 | `verified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**roundtable 的议长汇总轮**——真实网关只跑了 pipeline，议长汇总轮的真实调用、议长前看不到未完成输出这两半**零份**。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️ 议长汇总轮的真实调用与「议长前看不到未完成输出」这两半仍零份。**<br>**第十一轮订正（2026-10-06）：真实网关 roundtable 首绿（a/b speaker + c chair、议长放开分支、`Σ=20723=spent`，第十一轮③）；仍缺导出哈希 + 退出码 + 重复稳定性 ⇒ `unverified` 不变。**<br>**第十三轮订正（2026-10-06）：rt 2 FAIL（测试自身竞态；产品侧 raw dump `run=COMPLETED/committed=[a,b,c]`）+ 1 PASS ⇒ 测试 bug 待修；议长正文语义 + 导出哈希仍缺 ⇒ `unverified` 不变。见第十三轮④。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：仍是 `unverified`。** 本批 roundtable 3/3 全失败（Time 301.583/300.969/301.756）：第 3 位角色 c（议长位）零产出；失败原文「等待盖章 + run=COMPLETED 超时（300000ms）…最后状态=FAILED，committed=[a, b] spent=13766；app 错误=[]」，与 pipeline 同根因（HTTP 200 空内容、6/6 稳定）；第十三轮归因的测试竞态不解释本批失败。⇒ 不升级。见第十四轮②/③。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：仍是 `unverified`。** 本批未跑 roundtable（第十四轮 3/3 全失败、零产出根因未消）；契约 `:206` 零份，见第十五轮（零设备）⑦。<br>**真机第十五轮订正（HEAD `37630adb4`，2026-10-07）：升 `verified`，8 项就地补值。** commit=HEAD `37630adb4`；命令退出码同 C1-02（`OK (1 test)`/Time 22.201）；设备=PKG110/Android 16/API 36/arm64-v8a；输入=`mode=roundtable`/`chair_role_id=c`/`tie_policy=fail`、USER「请三位依次发言，议长最后汇总。」；viewer=a 2 条、b 2 条、议长 c（`chair_round=true`）4 条；序列=a `deepseek-v4-flash 6831+93`、b `glm-5.2 6546+80`、c `deepseek-v4-flash 7011+146`（`turn_kind=chair`，`provenance={wire:3,fallback:0}`）；token=`Σ=20707==spent_tokens`；导出=`c1-export-real-roundtable.jsonl` 2371 B/5 行 `75ca8ea9…`；证据文件 `c1-live-evidence-real-roundtable.json` 13423 B `d18c4441…`、raw `c1-real-raw-dump-roundtable-C103.json` 8295 B `822f2fc9…`。详见「C1 真机证据采集第十五轮」①。<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：维持 `verified`。** 本批零设备、四类产物零份，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（逐格见「零设备修复批次（2026-10-07，HEAD `5c362851a`）」⑥。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：维持 `verified`。** 本批真机窗口只跑 C1-07 与 C1-10 两条路径，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（见「C1 真机证据采集第十六轮」⑤）。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：维持 `verified`（本批未碰 roundtable）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：维持 `verified`（本批未碰 roundtable）。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：维持 `verified`（本批未碰 roundtable）。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持原判**（本批新增 C1-09 同源往返用例 `C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`，只触及 C1-09 点名的导出/QR 路径；本用例点名的路径未被触碰）。|
| C1-04 vote | `4534f02a` 新增 GroupTurnCoordinator<br>`aa771637` `parseBallot` 截断选票前缀改忽略大小写（修小写 `vote:` 被静默丢票）<br>`be41fdc9` 补 63 条内核用例（含投票平票） | `CMD-1` 退出码 **0**（强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`GroupChat.kt` lint 零命中）<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**，同 C1-01<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。 | 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>选票文本是测试内联的 `vote:` 前缀字符串 | **无证据（需真机）**<br>**2026-10-05 夹具层台账**：vote 下三个角色的 `predecessorId` 与 `chairRound` **都为 null**，可见集合收窄成 user,user,自己,`__summary__`（隔离最紧）；越权审计 `passed=true` / `checked_pairs=6` / `violations=[]`。<br>⚠️ **只是夹具层**：vote 的真实调用零份——mock 目前返回 `[mock] CASE:…` 文本，`parseBallot` **认不出 `VOTE:` 前缀**，所以 `parseBallot → __summary__` 分支**根本没被真实请求走过** <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。 | **无证据**。三角色各自投票请求的真实调用序列未采集<br>**2026-10-05 仍缺**：三角色各自投票请求的真实序列零份；平票按配置失败那条路径也零份。真实 HTTP 那轮 mock 返回 `[mock] CASE:…` 文本，**`parseBallot` 认不出 `VOTE:` 前缀**，所以投票分支**根本没被真实请求触发过** <br>**2026-10-05 第四个窗口：仍然是零份**——真实公网网关那轮跑的是**无 @ 的 pipeline**，`mode` 只有 pipeline 一种，本例要的那条路径**没有被真实调用过**。 | **无证据**<br>**2026-10-05 仍缺**：vote 轮真实 usage 零份。⚠️ 上一轮那个 `spent=4096`（prompt **3000** + completion **1096**）/ `limit=400` 的数字**是构造输入**，测试 JSON 的 `token_source` 自己写着 `budget-accounting-only, no live LLM call`——**不能当真实用量读** <br>**2026-10-05 第四个窗口：仍然是零份**——vote 的三张选票与 `parseBallot → __summary__` 分支**仍然没被真实请求触发过**。⚠️ 旧理由（「mock 返回 `[mock] CASE:…`、`parseBallot` 认不出 `VOTE:` 前缀」）到这一轮已经换成「**真实网关那轮压根没跑 vote**」。 | **无证据** | `…TEST-…GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>判定入口：`app/src/main/java/heizige/kk/khatkit/app/core/data/model/GroupChat.kt:605`（`parseBallot`） <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件在 `6ba95422` 那版为 **1982 行 / 5 个 `@Test`**，HEAD `74d82476` 已增至 **2221 行 / 5 个 `@Test`**（`@Test` 个数未变））；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。 | `verified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**vote 的三张选票、`parseBallot → __summary__`、以及平票按配置失败那条路**——**真实调用零份**（真实网关那轮跑的 mode 是 pipeline）。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️ 本批拒绝 `mode` 未知的理由正是「默认 pipeline 会**静默丢 `vote_candidates`**」，但那是**导入期**的拒绝；**真实 vote 调用序列仍然零份**。**<br>**第十一轮订正（2026-10-06）：vote 多数决与平票失败两条路径均真机通过（选票 / `tally` / 错误节点 / usage 全有值，第十一轮④⑤）；仍缺导出哈希 + 退出码 + 重复稳定性 ⇒ `unverified` 不变。**<br>**第十三轮订正（2026-10-06）：vote 多数决 3/3 + 平票 3/3 全绿（重复稳定性首次满足，SHA 见第十三轮④）；契约 `:206` 的「导出哈希」一格仍无内容（本例结构性不产出导出文件）⇒ `unverified` 不变。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：升为 `verified`。** 本批两条路径再次真机通过且**契约 8 项首次全齐**——多数决（Time 17.917 / `exitcode.txt=0` / `OK (1 test)`；序列 a/b/c = deepseek 6877 / glm 6609 / deepseek 6882；Σ=20368=spent；`Decided/opt-a 2:1`；导出 `8e42c5b9…` 2672 B/6 行）与平票（Time 16.506；Σ=20302=spent；`Tie → FAILED/vote_no_decision`；错误节点 `__summary__/error`；导出 `65f76608…` 2695 B/6 行）；viewer 台账三角色各见「自己 + `__summary__`」；provenance `{wire:3, fallback:0}`。**「导出哈希结构性不产出」的旧结论被本批直接推翻**（两跑都产出导出）。结合第十三轮 vote 3/3 稳定性 ⇒ 按契约 `:206`/`:232-235` 8 项齐备升 `verified`。见第十四轮①/④/⑤。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：维持 `verified`。** 本批零设备、无新真机证据；第十四轮「契约 `:206`/`:232-235` 8 项齐备」的结论未被本批触及，见第十五轮（零设备）⑦。<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：维持 `verified`。** 本批零设备、四类产物零份，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（逐格见「零设备修复批次（2026-10-07，HEAD `5c362851a`）」⑥。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：维持 `verified`。** 本批真机窗口只跑 C1-07 与 C1-10 两条路径，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（见「C1 真机证据采集第十六轮」⑤）。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：维持 `verified`（本批未碰 vote）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：维持 `verified`（本批未碰 vote）。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：维持 `verified`（本批未碰 vote）。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持原判**（本批新增 C1-09 同源往返用例 `C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`，只触及 C1-09 点名的导出/QR 路径；本用例点名的路径未被触碰）。|
| C1-05 轮次预算 | `4534f02a` 新增 GroupTurnCoordinator<br>`dd7b3c79` ChatManager 接上协调器 + `group_runs`<br>`be41fdc9` 补 63 条内核用例（预算截断） | `CMD-1` 退出码 **0**（强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`GroupTurnModel.kt` / `GroupRunDAO.kt` lint 零命中）<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**，同 C1-01<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` | 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>`core.data.db.migrations.GroupRunSchemaTest`（10）<br>预算值是构造入参，不是真实 token | **无证据（需真机）**。预算截断时点「谁被停掉」只有角色 id 顺序断言，没有消息 ID 台账<br>**2026-10-05**：`a9d2077e` 三 mode 各采一遍，越权审计三 mode 全 `passed=true` / `checked_pairs=6` / `violations=[]`；budget 轮真机**只落 `a` 一条**（`cc3d451c…`），`b`/`c` **库里没有发言**——「谁被停掉」在消息层面可直接观测（那一轮 `case=budget`，见 token 列） <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。 | **无证据**。「达到上限后停止剩余角色」要求日志与真实请求序列对齐，未实跑<br>**2026-10-05 真实 HTTP（部分证据）**：budget 轮 `token_limit=1` 真跑，**实际只发出 1 个请求**（seq4 `mock-model-a` / case budget / 发言者 A），与「达到上限后停止剩余角色」一致；`b`/`c` **没有发出任何请求**（不是发了被拒，是没发）。⚠️ 截断由 `limit=1` 这个**最小正值**触发，「跑满 N 个角色再截断」那条更接近生产的路径仍零份<br>**订正（2026-10-06，HEAD `f507ebc9a`，真机）：该路径的真实 token 截断已采到（库内七值）**——`-e c1TokenBudgetPerRound 9000` 真机真实网关注入后，整轮在 b 之后按预算停跑：`spent_tokens=13631 ≥ token_limit=9000`、`skipped_role_ids=["c"]`、`committed_role_ids=["a","b"]`（见「真实 token 下的预算截断（零 mock，真机）」节）。⚠️ 该次跑 `Tests run: 1, Failures: 1`（用例写死期望 3 条助手消息、截断只产 2 条），且正式调用序列 JSON 仍未落盘——状态列仍 `unverified`<br>**2026-10-06 第八轮（HEAD `31b6b5a52`）：预算截断的 raw dump 第一次真的落盘。** 设备端 mtime 前进到 17:10（正式证据仍 16:58 ⇒ 未被改写）；内容 `BUDGET_STOPPED / spent=13571 / limit=9000 / committed=[a,b] / skipped=[c] / reason=token_budget_exceeded`；两条调用 a/b 的 `wire_model_name_provenance` 均为 `wire_response_model`（counts 2/0）。⚠️ raw dump 是**排查文件不是正式证据**，正式调用序列 JSON 仍未产出；⚠️ 本跑 stdout 丢失（无退出码）。SHA 见「C1 真机证据采集第八轮」②。 | **无证据（本例是四条缺失里最硬的一条）**。契约 `:202` 的预算口径是「每轮累计 prompt + completion token」，`GroupTurnCoordinatorTest` 里的预算是**判定逻辑**的输入，不是真实 provider 返回的 token 数；`--rerun` 那轮 XML 里也没有任何 token 断言字段<br>**2026-10-05 真实 usage，与落库对账相等（本例最硬的一块）**：budget 轮 `token_limit=1`，**只发出一个请求**，prompt **201** + completion **35** = **236**，落库 `group_runs.spent_tokens` = **236**，✅ 相等；`status=BUDGET_STOPPED`、`reason=token_budget_exceeded`、`skipped_role_ids=["b","c"]`、`committed_role_ids=["a"]`、`token_limit=1`。**「已用 / 上限 / 未运行角色」三个字段全部有库内取值。**⚠️ `limit=1` 是「第一个角色之后必定停跑」的最小正值（`GroupChat.budgetDecision` 的口径是 `limit <= 0 <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。\|<br>**2026-10-05 第四个窗口：仍然是零份**——真实公网网关那轮跑的是**无 @ 的 pipeline**，`mode` 只有 pipeline 一种，本例要的那条路径**没有被真实调用过**。\| spent < limit` 才继续）；`run_token_persisted_before_call=true` 也在同一份证据里 <br>**2026-10-05 第四个窗口**：真实网关那轮 `token_limit` 足够大、**没有触发预算截断**，所以「达到上限后停止剩余角色」在**真实 usage 下仍然是零份**（mock 那轮的 `token_limit=1` 仍是唯一的截断实测，见上一窗口）。<br>**订正（2026-10-06，HEAD `f507ebc9a`，真机）：真实 token 下已采到（库内七值）**——a `prompt 6829 + completion 101 = 6930`、b `6643 + 58 = 6701`，**Σ=13631 与库内 `spent_tokens` 精确相等**；`token_limit=9000`（`-e c1TokenBudgetPerRound 9000` 注入）、`skipped_role_ids=["c"]`、`committed_role_ids=["a","b"]`、`status=BUDGET_STOPPED`、`reason=token_budget_exceeded`。⚠️ 七值来自**运行中 DB 快照**（`rikka_hub` 的 `group_runs` + `c1-live-evidence.db` 的 `message_node`），不是测试落盘 JSON；见「真实 token 下的预算截断（零 mock，真机）」节。<br>**2026-10-06 第八轮（HEAD `31b6b5a52`）：13571 是同一路径的第二次独立运行**（raw dump 里 a `prompt 6829 + completion 88 = 6917`、b `6632 + 22 = 6654`，Σ=13571），与上一轮 `13631` 的差异是 completion token 的真实波动，**不是矛盾**；来源也不同（上一轮=运行中 DB 快照；本轮=raw dump 落盘）。两轮都保留。 | **无证据** | `…TEST-…GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.data.db.migrations.GroupRunSchemaTest.xml`<br>运行日志表：`group_runs`（`app/src/main/java/heizige/kk/khatkit/app/core/data/db/entity/GroupRunEntity.kt`） <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件在 `6ba95422` 那版为 **1982 行 / 5 个 `@Test`**，HEAD `74d82476` 已增至 **2221 行 / 5 个 `@Test`**（`@Test` 个数未变））；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。 | `verified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**预算截断路径**——真实网关那轮 `token_limit=100000`，**整轮没触发截断**，「已用 / 上限 / 未运行角色」三个数在真实调用下仍然零份。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️ 本批对「预算越界」一律拒绝（理由是**不存在安全的缺省值**），但那是**导入期**的判定；**真实 usage 下的截断仍零份**。**<br>**第十一轮订正（2026-10-06）：本批未跑预算截断（三条新用例与 pipeline 复跑均 `token_limit=100000` 未截断）⇒ `unverified` 不变。**<br>**第十三轮订正（2026-10-06）：未跑截断；发现正式证据结构性不可达（测试等 3 条而截断只产 2 条 ⇒ 必然超时）⇒ `unverified` 不变，遗留第 43 条。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：升为 `verified`（遗留第 43 条已消）。** 本批专用用例 `realProviderBudgetTruncationRecordsRunLogAndExport` 真机通过（Time 17.793 / `exitcode.txt=0` / `OK (1 test)`）：`BUDGET_STOPPED/token_budget_exceeded/spent=13594/token_limit=9000/committed=[a,b]/skipped=[c]`；序列 a deepseek 6934、b glm 6660、Σ=13594=spent；**库内 `messages.count=3`（USER+a+b）、`assistant_role_order=[a,b]` 直读断言**（`b60aa80c5` 补）；viewer 台账 a/b/c；导出 `da0f81c5…` 2044 B/4 行；`budget_effective=9000`。契约 `:202`/`:206`/`:232-235` 8 项齐备 ⇒ 升 `verified`。见第十四轮①/④/⑤。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：维持 `verified`。** 本批零设备、无新真机证据；第十四轮「契约 `:206`/`:232-235` 8 项齐备」的结论未被本批触及，见第十五轮（零设备）⑦。<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：维持 `verified`。** 本批零设备、四类产物零份，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（逐格见「零设备修复批次（2026-10-07，HEAD `5c362851a`）」⑥。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：维持 `verified`。** 本批真机窗口只跑 C1-07 与 C1-10 两条路径，未触碰本用例点名的路径；此前建立的 `verified` 结论未被本批触及（见「C1 真机证据采集第十六轮」⑤）。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：维持 `verified`（本批未碰预算截断）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：维持 `verified`（本批未碰预算截断）。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：维持 `verified`（本批未碰预算截断）。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持原判**（本批新增 C1-09 同源往返用例 `C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`，只触及 C1-09 点名的导出/QR 路径；本用例点名的路径未被触碰）。|
| C1-06 取消与超时 | `60e9f055` 新增未产出助手消息过滤纯函数<br>`97b0fa1c` `finishGeneration` 落库前丢弃本次新增空气泡<br>`adc8a0ff` 补 18 条用例（空气泡丢弃/部分产出保留/存量不清洗）<br>`be41fdc9` 补 63 条内核用例（失败取消超时） | `CMD-1` 退出码 **0**（强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`ChatManager.kt` lint 零命中）<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**，同 C1-01。这是**最需要真机**的一条：取消与超时只能在真实协程取消 + 真实流式响应下复现，JVM 只能测纯函数<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。 | 输入 = `feature.chat.UngeneratedMessageFilterTest`（18）<br>`feature.chat.GroupTurnCoordinatorTest`（63） | **无证据（需真机）**<br>**2026-10-05 仍缺**：两轮真实 HTTP（main / budget）都是**正常跑完**的，**没有一次取消或超时**——取消/超时要在流式响应中途打断，本轮没做。所以本列**仍是零份**；JVM 那 18 条纯函数断言（`dropUngeneratedAssistantMessages`）**不能替代**真实流式响应下的取消 <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。 | **无证据**。取消/超时发生在流式响应中途，没有真实 provider 的部分响应记录<br>**2026-10-05 仍缺**：两轮真实 HTTP 都**正常跑完**，**没有一次中途取消或超时**——「流式响应中途打断」这条链路（部分响应、已消耗 token、错误节点保留）仍零份 <br>**2026-10-05 第四个窗口：仍然是零份**——真实网关那轮**正常跑完**，**没有一次中途取消或超时**。 | **无证据**。取消时已消耗的 token 无采集<br>**2026-10-05 仍缺**：两轮都正常跑完，**取消/超时时的已消耗 token 没采**（没有中途打断） <br>**2026-10-05 第四个窗口：仍然是零份**——没有中途打断，「取消/超时时的部分响应与已消耗 token」仍无采集。 | **无证据** | `…TEST-heizige.kk.khatkit.app.feature.chat.UngeneratedMessageFilterTest.xml`<br>`…TEST-…GroupTurnCoordinatorTest.xml`<br>纯函数：`app/src/main/java/heizige/kk/khatkit/app/feature/chat/UngeneratedMessageFilter.kt:78`（`dropUngeneratedAssistantMessages`）<br>落库前调用点：`app/src/main/java/heizige/kk/khatkit/app/feature/chat/ConversationSession.kt:108`（`finishGeneration` 内） <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件在 `6ba95422` 那版为 **1982 行 / 5 个 `@Test`**，HEAD `74d82476` 已增至 **2221 行 / 5 个 `@Test`**（`@Test` 个数未变））；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。 | `verified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**取消与超时**——真实网关那轮是正常跑完的（`status=COMPLETED`、无错误节点），取消/超时路径仍然零份。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️ 导入侧不涉及取消与超时；**取消/超时路径仍零份**。**<br>**第十二轮订正（2026-10-06）：取消三次尝试全部不成立、四项断言零测量、超时半未跑（第十二轮②）；另见遗留第 40 条 ⇒ `unverified` 不变。**<br>**第十三轮订正（2026-10-06）：取消路径首次真机通过（Time 2.246 / `OK (1 test)`）；空气泡 0 / a 完整保留（usage 6258）/ b 半截 24 字符保留 / 第一轮错误节点保留 / `CANCELLED+cancelled+committed=[a]`；⚠️ 超时半未跑、viewer 集合未按契约落盘、导出哈希一格缺、单次通过 ⇒ `unverified` 不变。见第十三轮①。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：仍是 `unverified`。** 本批未跑取消/超时（第十三轮取消半通过仍为孤证，超时半 `GROUP_ROUND_STEP_TIMEOUT_MS` 从未跑）；契约 `:206` 的 viewer 集合 / 导出哈希 / 重复性仍缺 ⇒ 不升级。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：仍是 `unverified`。** 超时半**结构性做不到**（`GROUP_ROUND_STEP_TIMEOUT_MS` 不可注入，`ChatManager.kt:116`/`:1005`；测试构造的 `AppScope` 无参 `KhatKitApp.kt:344-352`）；`9d7429216` 只补证据字段、无实际值；契约 `:206` 四类产物零份，见第十五轮（零设备）④/⑦。<br>**真机第十五轮订正（HEAD `37630adb4`，2026-10-07）：仍 `unverified`。** 取消半首次给齐 8 项字段（viewer 台账 a/b/c + `actual_model_call_sequence` 4 条全 mock + `export_sha256` 2977 B/7 行，见「第十五轮」①#3），但超时半结构性做不到（`timeout_half_verified=false`）且单次通过 ⇒ 契约 `:201` 两半不齐 ⇒ 不升。<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：仍 `unverified`。** 本批未碰取消/超时；超时半结构性做不到（`ChatManager.kt:116`/`:1005`），契约 `:201` 两半不齐 ⇒ 不升。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：仍 `unverified`。** 本批未碰取消/超时；超时半结构性做不到（`ChatManager.kt:116`/`:1005`、`KhatKitApp.kt:344-352`），契约 `:201` 两半不齐 ⇒ 不升。见「C1 真机证据采集第十六轮」⑤。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：维持 `unverified`（本批未碰取消/超时；超时半结构性做不到，`ChatManager.kt:116`/`:1005`）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：**升 `verified`** —— 超时半条真机通过（注入 5000ms、实测 elapsed 6634ms、status=TIMEOUT/reason=timeout、空气泡 0、committed=[a]、b 错误节点、spent=6212=6190+22），补上此前唯一缺口；与取消半条合并看契约 `:201` 两半齐 + `:206`/`:232-235` 8 字段齐（viewer 台账/导出哈希在取消半）。见「第十八轮」①。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：维持 `verified`（本批未碰取消/超时）。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持原判**（本批新增 C1-09 同源往返用例 `C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`，只触及 C1-09 点名的导出/QR 路径；本用例点名的路径未被触碰）。|
| C1-07 失败续跑/幂等 | `d976aa61` 新增 `group_runs` 表与运行 token 幂等<br>`007e4173` 改复合主键 `(conversation_id, round_id)` + `run_token/updated_at`<br>`70ed043d` `GroupRunDAO` 按 `(conversationId, roundId)` 定位 + `upsertRun` 事务<br>`892577a3` 30→31 改显式 `Migration_30_31` 并在工厂注册<br>`cc01d78d` androidTest 迁移到复合主键/`run_token` 并补幂等与 upsert 用例<br>`be41fdc9` 补 63 条内核用例（幂等/续跑）<br>`1649f5c7` 迁移重放脚本补 FTS5 触发器存活证据与对照实验 | `CMD-1` 退出码 **0**（强制 `--rerun`，JVM 侧覆盖 app 模块全部 664 个用例，含本例相关的 `GroupTurnCoordinatorTest` 63 + `GroupRunSchemaTest` 10）<br>**仪器侧未跑**：`adb devices` 空，`connectedDebugAndroidTest` 无法执行<br>**C1-D 主机侧重放已跑**：`python3 tools/verification/c1d_migration_30_31_replay.py` 退出码 **0**（连跑两次输出逐字节相同）<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**。C1 相关仪器测试源码共 **19 个注解**（`GroupRunDAOTest` 12 + `Migration_30_31_Test` 7），**执行结果为零**。唯一 app 仪器记录 2026-10-03 12:58:29 崩溃且 `tests="0"`，早于基线<br>**部分证据（仅迁移侧）**：零设备主机侧重放已把 C1-D 的 30→31 迁移钉住 85 条断言 0 失败，覆盖 `group_runs` 复合主键 `(conversation_id, round_id)`、`run_token` UNIQUE 索引、同 `round_id` 第二条被主键拒绝、同 `runToken` 第二次被唯一索引拒绝、`group_runs` 仍只有 1 行（见「C1-D 迁移 30→31 主机侧重放」）。**这只是部分证据，不改状态**<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。 | 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.db.migrations.GroupRunSchemaTest`（10）<br>仪器侧 fixture 未采集：`app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/dao/GroupRunDAOTest.kt`、`…/core/data/db/migrations/Migration_30_31_Test.kt`（源码在库，执行证据不在库） | **无证据（需真机）**<br>**2026-10-05**：`a9d2077e` 三 mode 夹具台账与越权审计全过（三 mode `checked_pairs=6` / `violations=[]`）；仪器侧 `GroupRunDAOTest` 12 条**真机全绿**。<br>⚠️ **但「同 `round_id` 重试跳过已提交 turn」这条真机交互仍然零份**：本轮两轮真实调用都是**一次跑完**，没有制造「第 2 个角色失败后重试同一 `round_id`」的场景。⚠️ 反而是这轮真实调用**撞出一个真缺陷**（`858c11d0`），见「模型调用序列」列——**它证明的是「产出归属会断」，不是「续跑幂等已验」** <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。 | **无证据**<br>**2026-10-05 部分证据**：`GroupRunDAOTest` 12 条真机全绿（覆盖 `run_token` 持久化、同 `round_id` 第二条被复合主键拒绝、同 `runToken` 被唯一索引拒绝、`upsertRun` 事务）。⚠️ 但那些是 **DAO 层**断言，**没有一次「失败后重试同一 `round_id`」的真实链路**。<br>⚠️ 反而这轮真实调用**撞出一个真缺陷并修掉**（`858c11d0`，`core/data/ai/GenerationLoop.kt`）：`role_id` 已提交时末尾助手消息被当成「本次生成自己的」而复用，导致后续角色的产出**并进上一位那条消息**，`stampGroupTurn` 随后找不到 `roleId == null` 的消息，把该角色误判成「本轮没有产出内容」写成 `role_failed`。真机证据：`role_id="a"` 的那条消息有 A、B 两段正文而 usage 是 B 的；`group_runs` 是 `status=FAILED` / `committed=["a"]` / `skipped=["c"]` / `reason=role_failed` / `error_message=本轮没有产出内容`。**这是锚点之后第一个由真机证据定位的 main 源码 bug** <br>**2026-10-05 第四个窗口：仍然是零份**——真实公网网关那轮跑的是**无 @ 的 pipeline**，`mode` 只有 pipeline 一种，本例要的那条路径**没有被真实调用过**。 | **无证据**<br>**2026-10-05**：pipeline 773 / budget 236 两条 run 已落库（见 C1-02、C1-05 行），⚠️ 但**「重试同一 `round_id` 时 token 是否只算一次」这条零份**——本轮没有重试场景 <br>**2026-10-05 第四个窗口**：那一轮 `status=COMPLETED`、**一次跑完**，**没有制造「第 2 个角色失败后重试同一 `round_id`」的场景** → 本列**仍然是零份**。⚠️ 反而撞出**另一个真模型特有的坑**（首版 persona 没写「历史里别人的代号不是你的」，pipeline 把上一位发言放进下一位上下文后，**真模型照抄眼前那条的格式**，b 学走 a 的 `ROLECODE:A`、c 学走 b 的、c 干脆零产出 `role_failed`）：那是**产出格式归属**问题，**不是**幂等路径的证据。 | **无证据** | `…TEST-…GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupRunSchemaTest.xml`<br>仪器记录（崩溃，早于基线）：`app/build/outputs/androidTest-results/connected/debug/TEST-PKG110 - 16-_app-.xml`<br>DAO：`app/src/main/java/heizige/kk/khatkit/app/core/data/db/dao/GroupRunDAO.kt`<br>主机侧重放脚本：`tools/verification/c1d_migration_30_31_replay.py`（sha256 `7cd9c3fb…8199c6`）<br>重放日志（仓库外）：`/tmp/opencode/c1-replay2/run1.log`、`run2.log`（sha256 `093981c4…4d2e7d5`）<br>变异测试驱动（仓库外）：`/tmp/opencode/c1-replay2/mutate.py` <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件在 `6ba95422` 那版为 **1982 行 / 5 个 `@Test`**，HEAD `74d82476` 已增至 **2221 行 / 5 个 `@Test`**（`@Test` 个数未变））；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。 | `verified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**同 `round_id` 重试跳过的幂等台账**——`C1LiveModelSequenceTest` 其余 4 条（含续跑那条）**仍未在真机全绿过**（硬理由⑤）。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️ 导入侧不涉及续跑幂等；「同 `round_id` 重试跳过已提交 turn」仍零份。**<br>**第十二轮订正（2026-10-06）：本批未跑同 `round_id` 重试/幂等路径 ⇒ `unverified` 不变。**<br>**第十三轮订正（2026-10-06）：本批未跑续跑/幂等路径 ⇒ `unverified` 不变。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：仍是 `unverified`。** 本批未碰同 `round_id` 重试/幂等路径；该路径的仪器测试**仍未写**（矩阵 JVM 列已记 `GroupRunDAOTest` 12 条未跑）⇒ 不升级。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：仍是 `unverified`。** 新增设备侧测试 `34145bf49`（`C1GroupRetryResumeDeviceTest.kt` 968 行）但**未真机跑**；`d6494e49a` 补上群聊 UI 续跑入口（契约 `:204`/`:227-228` 的落点；该测试自述「群聊 UI 无重试入口」已过期），但无真机证据，见第十五轮（零设备）③/⑤。<br>**真机第十五轮订正（HEAD `37630adb4`，2026-10-07）：仍 `unverified`。** 本批该用例 2/2 `FAILURES!!!`（`:378`），当前是未解决的失败；待另一任务判测试期望错 / 生产缺陷 ⇒ 不升。见「第十五轮」②#4。<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：仍 `unverified`。** `5c362851a` 把设备侧断言 `:378` 改成「同一 `MessageNode` 内候选替换 + 切 `selectIndex`」的**测试期望修正**（判定：测试期望写错，非生产缺陷），但**真机未复跑**；契约 `:204` 幂等台账真机实证仍零份 ⇒ 不升。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：升 `verified`，8 项就地补值。** commit=HEAD `bd88aaff2`（设备轮零 commit）；命令退出码=`:app:assembleDebug :app:assembleDebugAndroidTest` exit 0 + 两 APK `install -r -t` Success + `am instrument` 日志 `OK (1 test)`/Time 13.108（shell 退出码不可判，遗留 41）；设备=PKG110/Android 16/API 36/arm64-v8a；输入=`mode=pipeline`、conversation `a0e965f1-…`、`round_id=round-832cd2cc-…`、USER「请三位依次发言，每位一句话。」；viewer=a `[832cd2cc-…, 9298d95f-…]`、b `[832cd2cc-…, 9298d95f-…, b55251a7-…]`、c `[832cd2cc-…, b55251a7-…, f7d97c05-…]`；序列=a `mock-retry-a 6186+22`、b `mock-retry-b 6217+322`、c `mock-retry-c 6517+22`（主机 mock `requests.jsonl` 独立记录一致）；token=phase1 `6208` / phase2 `19286==a+b+c`、`run_token_reused=true`、`group_runs rows=1`；导出=`c1-retry-export-x.jsonl` 5063 B/5 行 `be3709800…`（本机复算一致）。契约 `:204` 幂等成立 ⇒ 升。详见「C1 真机证据采集第十六轮」①。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：维持 `verified`（本批未碰续跑幂等）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：维持 `verified`（本批未碰续跑幂等）。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：维持 `verified`（本批未碰续跑幂等）。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持原判**（本批新增 C1-09 同源往返用例 `C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`，只触及 C1-09 点名的导出/QR 路径；本用例点名的路径未被触碰）。|
| C1-08 记忆隔离 | `966792d6` `memory_chunks` 加 `role_id` + 注册 `group_runs`（Room 30→31）<br>`63502610` `addMemory` 透传 `roleId` 且 `toModel` 带出发言角色<br>`8e5457dc` `getMentionsOfEntity` 改带 `spaceId` 的 JOIN<br>`e7606912` 检索层加 `MemorySpaceGate` 空间闸门（FTS/向量/图谱三路）<br>`68b92146` `forgetMemory/linkMemories` 加 `expectedSpaceId` 归属校验<br>`6504f023` 记忆工具接群空间（无全局/助手回退），群聊不下发 `recent_chats`<br>`2031c409` `MemoryExtractor` 写入带 `roleId`，`sourceMessageId` 按 `source_line` 归因<br>`495b5e3a` `ChatManager` 接群记忆作用域（懒建空间、只读 viewer 可见消息）<br>`58b129c7` 补 31 条用例（空间闸门/工具空间/抽取归属/懒建/跨空间泄漏回归） | `CMD-1` 退出码 **0**（强制 `--rerun`，覆盖本例 7 个类的 50 个用例）<br>`CMD-2` 退出码 **0**（`MemoryRepository.kt` / `MemoryExtractor.kt` lint 零命中）<br>**C1-D 主机侧重放已跑**：`python3 tools/verification/c1d_migration_30_31_replay.py` 退出码 **0**（连跑两次输出逐字节相同）<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**，同 C1-01。三空间互不串需要真实检索栈 + 真实写入，JVM 只能测纯函数闸门<br>**部分证据（仅迁移与 FTS 侧）**：主机侧重放已钉住 `memory_chunks` 加可空 `role_id`（存量 2 行 `role_id IS NULL` 不被改写、新行可写 `'r1'`）、`role_id` 索引存在、存量 `content`/`source_message_id`/`source_ref_id`/`confidence`/时间戳/`embedding` BLOB 全保留，且 3 个 FTS5 触发器在迁移后逐字节存活、INSERT/UPDATE/DELETE 三向真同步到索引（见「C1-D 迁移 30→31 主机侧重放」）。**这不覆盖三空间互不串本身，也不改状态**<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。 | 输入 = `core.data.repository.MemorySpaceGateTest`（9）<br>`core.data.ai.tools.MemoryToolScopeTest`（7）<br>`core.data.repository.MemoryAttributionTest`（9）<br>`core.data.repository.GroupMemorySpacePolicyTest`（6）<br>`core.data.repository.MemoryExtractorParseTest`（7）<br>`core.data.db.MemoryRoleIdMappingTest`（2）<br>`core.data.db.migrations.GroupRunSchemaTest`（10） | **无证据（需真机）**。三个 `group:<conv>:role:<role>` 空间的真实检索结果可见性没有跨 viewer 的实跑记录<br>**2026-10-05 仍缺（这一列对本例不适用）**：viewer 可见消息台账量的是**对话消息**，而 C1-08 要的是**三个 `group:<conv>:role:<role>` 记忆空间的检索结果可见性**——本轮真实 HTTP 的 assistant 把记忆**全部关掉**（`enableMemory=false` / `useGlobalMemory=false` / `autoExtractMemory=false`），**一次记忆写入与检索都没发生**。所以本列**仍是零份** <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。 | **无证据**。「检索 query 只用 viewer 过滤结果」要对照真实请求的 query 文本，未采集<br>**2026-10-05 仍缺（这一列对本例不适用）**：C1-08 要对照**真实请求的 query 文本**与三个空间的检索结果，而本轮真实 HTTP 的 assistant 把记忆**全部关掉**（`enableMemory=false` / `useGlobalMemory=false` / `autoExtractMemory=false`），**没有一次检索发生**，query 文本也无从对照。⚠️ 视角隔离那一半**已被真实 HTTP 审计钉住**（请求里只含 viewer 过滤后的 messages，见 C1-01 / C1-02 行），但**「检索 query 只用 viewer 过滤结果」这条仍然零份** <br>**2026-10-05 第四个窗口：仍然是零份**——真实网关那轮没有检索记忆，三空间互不串在真实调用下仍无观测。 | **无证据**<br>**2026-10-05 仍缺（这一列对本例不适用）**：本轮真实 HTTP 把记忆全关，**没有一次记忆写入或检索**，「带 `source_message_id` 归因」无从采集 <br>**2026-10-05 第四个窗口**：pipeline 那三个数是**群聊轮次**的用量，**不覆盖**记忆空间隔离；本列**仍然是零份**。 | **无证据**。契约 `:203` 要求记忆内容不进包，但没有导出文件可算哈希 | `…TEST-heizige.kk.khatkit.app.core.data.repository.MemorySpaceGateTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.data.ai.tools.MemoryToolScopeTest.xml`<br>`…TEST-…MemoryAttributionTest.xml`<br>`…TEST-…GroupMemorySpacePolicyTest.xml`<br>`…TEST-…MemoryExtractorParseTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.data.db.MemoryRoleIdMappingTest.xml`<br>`…TEST-…GroupRunSchemaTest.xml`<br>主机侧重放脚本：`tools/verification/c1d_migration_30_31_replay.py`（sha256 `7cd9c3fb…8199c6`），日志 `/tmp/opencode/c1-replay2/run1.log`、`run2.log`（仓库外） <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件在 `6ba95422` 那版为 **1982 行 / 5 个 `@Test`**，HEAD `74d82476` 已增至 **2221 行 / 5 个 `@Test`**（`@Test` 个数未变））；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。 | `verified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**三个 `group:<conv>:role:<role>` 空间的检索可见性 + `source_message_id` 归因**——**零份**，本轮这三项一条都没碰。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️⚠️ 本批**恰好给本行添了一条新的未验证义务**：`memory_space_id` 在导入场景**无解**（新会话 id 导入时未知），所以 `importGroup` 放过它是必然的，**调用方建完会话后必须补跑一次 `validate(config, newId)`**，否则会造出**记忆空间键错位**的群——⚠️ **「调用方是否补做」无任何证据**（没有调用方）。已登记为「已知遗留与风险」第 31 条。**<br>**第十二轮订正（2026-10-06）：canary 记忆不入导出包（安全侧）不等于空间隔离 + `source_message_id` 归因 ⇒ `unverified` 不变。**<br>**第十三轮订正（2026-10-06）：本批未碰记忆空间 ⇒ `unverified` 不变。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：仍是 `unverified`（尽管本批 #7 通过）。** `realProviderGroupMemoryIsolationRecordsPerSpaceHits` 真机通过（Time 0.647）：三空间 `group:<conv>:role:a` / `…:role:b` / `…:role:c` 各命中 1 条 + `role_id` + `source_message_id`（a→`…a008`/b→`…b008`/c→`…c008`）；`foreign_query_hit_counts` 全 `[0,0]`；viewer 重过滤 a 的记忆对 a 可见 1、**对 b 可见 0**；全局/助手空间金丝雀命中 0、无回退。⚠️ **但契约 8 项缺 3 项**：① 实际模型调用序列 ② prompt+completion token ③ 导出 SHA-256——`evidence_kind=device-instrumentation-memory-isolation-no-gateway`，**本例设计上不走网关、不产出群导出**（提取用 canned 确定性 JSON）。契约 `:206`「每例保存…模型调用序列、token 计数、导出哈希」逐字要求 ⇒ 按判定规则第 2 条不升级，缺项如实记。见第十四轮①/⑥。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：仍是 `unverified`。** 契约 `:206` 缺三项（模型序列 / token / 导出哈希）；本轮评估结论为**不产出**（`extractFromTurn` 丢 usage ⇒ 语义正交；记忆隔离不产出群导出 ⇒ 不适用），仍缺，见第十五轮（零设备）⑥。<br>**真机第十五轮订正（HEAD `37630adb4`，2026-10-07）：仍 `unverified`。** 本批未碰；契约 `:206` 仍缺 3 项，评估语义正交 / 不适用、待用户认可。<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：仍 `unverified`。** 本批未碰记忆隔离；契约 `:206` 仍缺模型序列 / token / 导出哈希三项 ⇒ 不升。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：仍 `unverified`。** 本批未碰记忆隔离；契约 `:206` 仍缺模型序列 / token / 导出哈希三项（语义正交 / 不适用，待用户认可）⇒ 不升。见「C1 真机证据采集第十六轮」⑤。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：维持 `unverified`（本批未碰记忆隔离；契约 `:206` 三类产物结构性不产出待用户认可）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：**升 `verified`** —— 旧串扰断言恒真已就地标注，替换为结构性判据 `memoryIsolationViolations`（只看 space_id/role_id/source_message_id/无回退）；真机重跑 `OK (1 test)`/Time 18.577（证据 `8944b371…`），JVM 镜像 7 例变异实测 6 failed 证判别力（`2a0610705`）；契约 `:203` 空间键/归因/无回退 + `:206` 8 字段齐。见「第十八轮」②。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：维持 `verified`（本批未碰记忆隔离）。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持原判**（本批新增 C1-09 同源往返用例 `C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`，只触及 C1-09 点名的导出/QR 路径；本用例点名的路径未被触碰）。|
| C1-09 Tavern/QR 往返 | `f921a02f` `GroupRole` 补 `extras` 无损往返 + `validate` 校 `schemaVersion`<br>`b3d9bf44` `TavernChatCodec` 加群聊导出/导入（保留 `role_id/round_id/turn_kind/群配置/角色卡`）<br>`efa1c27c` `encodeConfig` 写库路径补跑密钥黑名单检查<br>`53563c8c` 补 Tavern 群聊往返与密钥过滤用例<br>`a1616b6b` 群聊页接上生成二维码（载荷与文本分享共用一份）<br>`7d4a6596` 群聊页接上扫码导入，统一走 `importShare`<br>`f5212df2` 角色卡解码走 null 安全取值<br>`b548025e` 补 `importShare` 五道闸门与 `cards` 保留用例<br>`88c850ef` 群聊导出面板接入 Tavern 群聊导出卡片，打通 `exportGroupJsonl`<br>`2c9aa2e4` 补群聊导出入口纯逻辑用例（面板到可回导文件的往返）<br>`aeab5550` 新增 CameraX + MLKit 扫码弹层与入口决策单测<br>`d2b5c05c` 新增导出哈希证据测试（9 变体 + 往返幂等 + SillyTavern 结构对照）<br>`eef6efb3` 新增跨两次独立 JVM 的哈希比对脚本<br>`bbe2e558` 加 golden 清单护栏（把「确定性」升级成「格式没变」） | `CMD-1` 退出码 **0**（强制 `--rerun`，覆盖 82 个用例：22+21+16+18+5）<br>`CMD-3` 退出码 **0**（打出了 3 个可安装 APK，但那是安装包不是群聊导出文件）<br>`CMD-2` 退出码 **0**（`TavernChatCodec.kt` lint 零命中）<br>**C1-P 哈希校验已跑**：`python3 tools/verification/c1p_group_export_hash.py` 退出码 **0**（两次独立 JVM + 落盘 `hashlib` 复算 + golden 清单，三重全一致）<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**，同 C1-01。相机扫码（`QrScannerSheet`）与 Tavern 本体打开文件**都必须真机/桌面端**，JVM 的 16 条只测入口决策<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。 | 输入 = `feature.chat.GroupTavernExportTest`（22）<br>`core.data.ai.tavern.TavernCompatTest`（21）<br>`core.ui.components.ui.QrScannerSheetTest`（16）<br>`core.data.model.GroupChatTest`（18）<br>**`core.data.ai.tavern.C1pGroupExportHashTest`（5）**<br>前四个类的往返在 JVM 里是内存对象 → JSON → 内存对象；新类把文件**真的落盘**（`build/c1p-group-export-hash/*.txt`）再算 SHA-256 | **无证据（需真机）**<br>**2026-10-05**：viewer 台账按三种 mode 各采了一遍（越权审计全过），但**本例要的「往返后 `role_id`/轮次/分支逐字段相等」靠的是导出列那 7 条消息的逐字段对账**，不是 viewer 台账。⚠️ **本例的实质缺口在导出与互操作**：酒馆（SillyTavern）本体打开群聊导出文件**零证据**、相机扫码真机链路**零证据**（`QrScannerSheet` 只有编译 + 16 条 JVM 单测，CameraX+MLKit 没在设备上跑过） <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。 | **无证据**<br>**2026-10-05 部分证据**：导出/QR 往返走的是**真机** `TavernChatCodec.exportGroupJsonl` + `GroupChat.encodeQr`，三种 `mode` 各一份，越权审计三 mode 全过。⚠️ **这一列对本例不适用**（导出不发起模型请求），且酒馆本体互操作与相机扫码**真机链路零份** <br>**2026-10-05 第四个窗口：仍然是零份**——真实网关那轮不产出群聊导出文件。 | **无证据**<br>**2026-10-05 仍缺（这一列对本例不适用）**：导出不消耗 token。⚠️ 契约 `:203` 的「记忆内容不进包」只能靠**逐字段扫导出文件**验，本轮没做机器扫描，只有零设备的密钥黑名单单测 <br>**2026-10-05 第四个窗口：仍然是零份**——同列，本轮只跑生成侧。 | **部分证据（零设备，JVM 层确定性）**——契约 `:206` 这一类现在有真实内容了。9 个 fixture 变体的字节数 + SHA-256 已实测落定（`d2b5c05c` / `eef6efb3` / `bbe2e558`，逐条证据见下方「C1-P 群聊导出确定性哈希（零设备）」）：<br>`pipeline_3roles_2rounds_jsonl` 3615 / `36e6585f9aa4a028eb8277bc70578fd2c802cdd730e3981f0d52b02430f0c8b9`<br>`roundtable_3roles_2rounds_jsonl` 3616 / `33c51124e5421ae46a002f025a2e61dc5712b73ddc413ca9dea3b9777ccc0fb3`<br>`vote_3roles_2rounds_jsonl` 3619 / `ad9e3fb119cbc4bea05b7e917eb180aabeb230bbbf89dfe1addcfd0040217c35`<br>`pipeline_3roles_2rounds_array` 3617 / `b8d57a6c4f15e87d1a9d0a181022ba28410a36527075e71b0daf77976c1a3653`<br>`pipeline_with_explicit_create_date_jsonl` 3652 / `92ce04902dc0c8e5bd822020e3df885b30a89adb9fef2fcd0fb712ae9faeee5e`<br>`empty_messages_jsonl` 1518 / `0ae10ea537e112c7f4d98ebd275b26a86e1b30952f1de8274f0cccf670f11e80`<br>`image_part_jsonl` 1711 / `c07d7e6ead7f06143ceae05fa5082be04af1fabc333edb60125ee337c9cbd9e9`<br>`qr_payload_pipeline` 1348 / `2d65ec04dded8a8fc29d3b7cb2d235bea908ee779b0568a6f644f34670cd2ff5`<br>`qr_payload_vote` 1352 / `8b49d47b225f32f6ad6032b1ab49eb1d6122a3af3c97652936adf7df13c2e9bf`<br>⚠️ **但这是零设备 fixture 的哈希，不是真机导出的文件哈希**：落盘走 `java.io.File.writeBytes`，`writeExportTempFile` + `ACTION_SEND` 真实 IO 分发**一次没跑过**；**酒馆本体打开、viewer 可见消息 ID、模型调用序列、token 计数仍零份**。**四类证据缺三类半，所以状态不变。**<br>① APK 的 SHA-256 已有（见构建段），但**安装包哈希不是群聊导出文件哈希**，不能填本列；<br>② `/tmp/opencode/sample-group.json` / `.jsonl`（各约 1.7 KB）**被仓库零引用**（`git grep sample-group` 无结果）、无 SHA-256、来源不明，**不作为 fixture** | `…TEST-heizige.kk.khatkit.app.feature.chat.GroupTavernExportTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.data.ai.tavern.TavernCompatTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.ui.components.ui.QrScannerSheetTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>**`…TEST-heizige.kk.khatkit.app.core.data.ai.tavern.C1pGroupExportHashTest.xml`**（`tests="5"`）<br>哈希校验脚本：`tools/verification/c1p_group_export_hash.py`<br>**golden 清单（入库，护栏本体）：`tools/verification/c1p_group_export_hash.golden.json`**<br>落盘产物（构建目录，未入库）：`app/build/c1p-group-export-hash/*.txt`<br>编解码：`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tavern/TavernChatCodec.kt`<br>C1-P 补的「QR 携带角色卡最小元数据」落库证据见下方「C1-P 角色卡元数据落库与 Room 31→32 迁移（零设备）」 <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件在 `6ba95422` 那版为 **1982 行 / 5 个 `@Test`**，HEAD `74d82476` 已增至 **2221 行 / 5 个 `@Test`**（`@Test` 个数未变））；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。 | `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**酒馆本体打开群聊导出文件 + 相机扫码链路**——**两份都零**；⚠️ 本轮只验了群配置面板的**渲染与滚动**，面板里的**保存 / 二维码 / 扫码这三个业务动作一个都没点过**。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️ 本行是本批**唯一被直接触及**的一行：`importGroup` 补上了密钥黑名单、角色卡去重与配置校验。但 ⚠️ **运行时证据仍为零**（`importGroup` 无调用方），且 **`importShare` 一行未动**（它仍是全拒口径，与 `importGroup` 的「结构性拒 + 两项归一化」**有意不同**，依据在 KDoc 但属单方面判断）——所以**酒馆本体打开群聊导出文件**与**相机扫码**这两份**仍然是零**。**<br>**第九轮（HEAD `4d73a26b4`，2026-10-06）：仍是 `unverified`（但酒馆一侧从「零份」推进到「解析器级接受」）。** 用**生产编解码器产出的真实导出文件**（`C1pGroupExportHashTest` → `pipeline_3roles_2rounds_jsonl`，3615 B / `36e6585f…c8b9`）+ **SillyTavern 钉死 commit `06bde939` 的解析器片段** harness，**16/16 断言通过、退出码 0**（登记时自跑复核）；六条断言与 5 条边界见「C1 真机证据采集第九轮」⑤。⚠️ **不升级**：解析器级 ≠ 完整酒馆 app（无群注册 / 无写盘 / 无 UI）；相机扫码仍零份；`open→save` 会丢私有表头块。**<br>**第十二轮订正（2026-10-06）：真机导出双哈希一致（`4945b854…6f4b` / 2938 B）+ 四项安全检查全 true（第十二轮①）；仍缺酒馆本体打开 / `ACTION_SEND` / 相机扫码 / QR 往返 ⇒ `unverified` 不变。**<br>**第十三轮订正（2026-10-06）：与本行无关 ⇒ 状态不变。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：与本行无关 ⇒ 状态不变（仍 `unverified`）。** 本批 5 条全部不涉及 Tavern/QR 往返；酒馆本体 / `ACTION_SEND` / 相机扫码仍零份。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：仍是 `unverified`。** 本批未碰；酒馆本体 / `ACTION_SEND` / 相机扫码仍零份，见第十五轮（零设备）⑦。<br>**真机第十五轮订正（HEAD `37630adb4`，2026-10-07）：仍 `unverified`。** 本批未碰 Tavern/QR 往返。<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：仍 `unverified`。** 本批未碰；酒馆本体 / `ACTION_SEND` / 相机扫码仍零份 ⇒ 不升。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：仍 `unverified`。** 本批未碰；酒馆本体 / `ACTION_SEND` / 相机扫码仍零份 ⇒ 不升。见「C1 真机证据采集第十六轮」⑤。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：C1-09：**维持 `unverified`**。本批拿到 C1-09 迄今最强的一批证据（真 SillyTavern **应用级**导入：字节级 copy + 服务端解析正确 + 私有键保留 + 野生 jsonl 行为 + open→save 丢私有块；二维码位图**端到端真机 6/6**：生产 `encodeQr`→`encodeQrBitmap`→MLKit 解码→逐字段→`importShare`；导出 FileProvider URI 字节 == 生产 JSONL；ACTION_SEND 源码护栏 5 例；⑤ 中文有损修复后真机复跑 + 非空验证），**但仍不升**：① 契约 `:206` 三类产物（各 viewer 可见消息 ID / 实际模型调用序列 / prompt+completion token）本例**结构性不产出**、未获用户认可（同 C1-08/C1-10 处置，缺失不等于满足）；② 契约 `:205` App 侧**真实相机扫码链路**（`QrScannerSheet` 的 CameraX `analyzeFrame` 的 `mediaImage`）**从未在设备上跑过**（位图路径只差图片来源却恰绕过它）；③ 契约 `:232-235` + 判定规则第九条：ACTION_SEND 是**源码护栏 + 同形 Intent**，**真实系统分享面板 UI 交互零份**（该用例自述「不弹分享面板」）；④ 酒馆 `open→save` 丢私有块、重存文件回 KhatKit 得 `Unsupported`（⚠️ **订正：不是 `NoConfig`**，`TavernChatCodec.kt:298` 表头无 `khatkit_group` 直接 `return null`）⇒ **打开并回导闭环不成立**；SillyTavern 多用户 / `--listen` 未验。**升格还差**：相机扫码真机链路 + 系统分享面板真机交互 + 用户对「三类产物结构性不适用」的明确认可（逐条见「C1 真机证据采集第十七轮」⑦）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：维持 `unverified`（本批未碰 Tavern/QR）。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：维持 `unverified`（本批未碰 Tavern/QR）。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持 `unverified`。** 本批新增同源往返用例首次把契约 `:206` 的三类产物（viewer 可见消息 ID / 调用序列 / token）与同群 QR 载荷**硬连接**（同一真机一轮：导出哈希来自该会话、QR config 强制 == 存储 config == 导出内嵌 config，非空验证真红），**消掉「三类产物结构性不适用」这条旧理由**；相机扫码与酒馆应用级亦闭环。**但真实系统分享面板（`ACTION_SEND` chooser）仍零份**——契约 `:219`（C1-U 交付 = Compose/UI 测试 + 真机手动记录）许可的「真机手动记录」缺失、判定规则第九条下源码护栏 + 同形 Intent 不能顶替，`ACTION_SEND` 这一步只有代码级证据 ⇒ `:206`/`:232-235` 未整体满足，**维持**。见「C1 真机证据采集第二十轮」③。|
| C1-10 单聊/群聊共存 | `2f1d04a2` 搜索路新增按 `type` 的 DAO 查询，空串语义与未归档路对齐<br>`c84256a1` 抽屉类型筛选下沉到 SQL，删掉只作用于已加载页的内存过滤<br>`35ea0762` `type` 谓词抽成两条查询共用的常量<br>`34493507` 补抽屉列表查询判定与「内存过滤已删」的护栏用例<br>`8528ecda` 抽出 `ChatScaffold` 共用消息区骨架<br>`4e97ff57` 群聊页复用 `ChatScaffold`，接成员头像组、@ 选择器与群配置面板<br>**`215296f1..6982869b` 8 个 C1-S 提交**：抽屉两条 `@Query` 的主机侧重放（`215296f1`，起手 335 条断言 / `aa1862eb` 扩到 **438 条**）、**修两个真实缺陷**（`58faa90b` 5 条 LIKE 加 `ESCAPE` + 抽出转义纯函数、`e8607171` 5 个转发点统一过转义、`a252884e` 全部 14 条 `ORDER BY` 追加 `id ASC`）、`79b13080` 转义纯函数 12 条单测 + 2 条源码护栏、`eb29abff` ESCAPE 常量去尾随空格、`6982869b` type 筛选护栏的期望串改为引用 ESCAPE 常量标识符 | `CMD-1` 退出码 **0**（强制 `--rerun`，覆盖 27 个用例：7+2+18）<br>**本轮追加**：`./gradlew --offline :app:testDebugUnitTest --rerun` 退出码 **0**，**92 类 / 722 例 / 0 失败 0 错误 0 跳过**（改前 91 类 / 710 例，净增 `ConversationSearchLikePatternTest` 12 例）<br>**C1-S 主机侧重放已跑**：`python3 tools/verification/c1s_conversation_type_filter_replay.py` 退出码 **0**（两次独立运行，输出逐字节相同，输出 SHA-256 `5645fa13…a40073`）<br>`CMD-2` 退出码 **0**（`ChatList.kt` 侧现在零命中——曾有的 3 条 `FrequentlyChangingValue` 已由 `366b3fe8` 搬进 `derivedStateOf`/draw 期清掉，见「lint」段；`ChatScaffold` 侧零命中）<br>本轮 `./gradlew --offline lint` 退出码 **0**，`0 errors, 587 warnings, 6 hints`，与基线**逐位相同**；`ConversationDAO.kt` / `ConversationRepository.kt` / `ChatDrawerViewModel.kt` / `ConversationSearchLikePattern.kt` **四个全部 0 命中**<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**，同 C1-01（`adb devices` 仍为空输出）。「筛选只过滤、来回切换不丢数据」是交互行为，JVM 的用例只钉住查询判定<br><br>**部分证据（零设备，真实 SQLite 执行）**：C1-S 重放脚本给「**类型筛选只过滤**」这一条钉了 **438 条断言 0 失败**，覆盖 C1-10 用例里点名的那半句——**集合相等（不是数量相等）**、筛选后 `count` 正确、`LIMIT/OFFSET` 分页无重复无遗漏、切换 `type` 参数**不改变查询种类**。**这是 C1-10 迄今最硬的一块证据，但它不改变本行判定**：契约 `:206` 点名的四类产物——**viewer 可见消息 ID、实际模型调用序列、prompt+completion token、真机行为**——**本轮一份都没补上**（`adb devices` 仍为空）。逐项交代：<br>· **viewer 可见消息 ID**：**仍是零份**。脚本比对的是 `conversationentity` 的 `id` 集合，不是消息表；`message_node` 一行都没碰。<br>· **模型调用序列**：**仍是零份**。脚本不发起任何模型请求。<br>· **token (prompt+completion)**：**仍是零份**。同上。<br>· **导出 SHA-256**：**仍是零份**。C1-10 不产出群聊导出文件（那一份在 C1-09 行）。<br>· **真机行为**：**仍是零份**，抽屉 UI 端到端（搜索框输入 → 列表刷新）一次没跑过。<br>**所以本行状态仍是 `unverified`。**<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。 | 输入 = `feature.chat.ConversationListQueryPlanTest`（7）<br>`feature.chat.ConversationTypeFilterSourceGuardTest`（2）<br>**`core.data.repository.ConversationSearchLikePatternTest`（12）**<br>`core.data.model.GroupChatTest`（18）<br>fixture 之一是 SQL 谓词常量 `CONVERSATION_TYPE_PREDICATE_SQL`<br>**重放脚本的 fixture**：25 行（助手 A1 23 行 / 助手 A2 2 行），`type×folder` **6 种组合全覆盖**（DIRECT×`{'',f1,f2}` = 14/2/1，GROUP×`{'',f1,f2}` = 5/2/1），21 未置顶 / 4 置顶，标题覆盖普通 / 置顶 / 文件夹 / 「共享词」矩阵 / 字面 `%` / 字面 `_` / ASCII 大小写对 / 繁简对 / 空串 / 纯空格，外加**故意 4 行 `(is_pinned, update_at)` 全同且插入顺序与 id 升序相反**（用来抓「去掉 id 兜底」）；参数矩阵 60 组 | **无证据（需真机）**。⚠️ C1-S 重放给的是 `conversationentity` 行集合的相等判定，**不是消息 ID 台账**；`adb devices` 仍为空<br>**2026-10-05 仍缺**：viewer 台账量的是**群聊消息**，C1-10 要的是**抽屉列表层**的「同一列表混排、类型筛选只过滤、切换后消息与会话数据不丢」。C1-S 那 438 条主机侧重放比的是 `conversationentity` 的 `id` 集合，**不是消息 ID 台账**；真实 HTTP 两轮也没构造混排会话。所以本列**仍是零份** <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。 | **无证据**。脚本不发起任何 provider 请求，没有可导出的调用序列<br>**2026-10-05 仍缺**：C1-10 不发起任何模型请求（主机侧重放与真机两轮都没构造混排会话），调用序列仍零份 <br>**2026-10-05 第四个窗口：仍然是零份**——真机 UI 端到端仍然零份，**一行 Compose 没上过屏**；真实网关只跑了后台的轮次生成。 | **无证据**<br>**2026-10-05 仍缺（这一列对本例不适用）**：抽屉筛选不消耗 token，零份 <br>**2026-10-05 第四个窗口**：pipeline 那三个数是**生成侧**的，**不覆盖**「切换会话后消息与会话数据不丢」；本列**仍然是零份**。 | **无证据**。C1-10 不产出群聊导出文件 | `…TEST-heizige.kk.khatkit.app.feature.chat.ConversationListQueryPlanTest.xml`<br>`…TEST-heizige.kk.khatkit.app.feature.chat.ConversationTypeFilterSourceGuardTest.xml`<br>**`…TEST-heizige.kk.khatkit.app.core.data.repository.ConversationSearchLikePatternTest.xml`**<br>`…TEST-…GroupChatTest.xml`<br>DAO：`app/src/main/java/heizige/kk/khatkit/app/core/data/db/dao/ConversationDAO.kt:76`（未归档路 `@Query`）/ `:108`（搜索路 `@Query`，本轮改过）<br>⚠️ 早前记的 `ConversationDAO.kt:19/38/66` **已随本轮 DAO 改动漂移**（新增 KDoc + 14 条 `ORDER BY` 就地追加），现值见上<br>仓储：`app/src/main/java/heizige/kk/khatkit/app/core/data/repository/ConversationRepository.kt:92/281`<br>转义纯函数：`app/src/main/java/heizige/kk/khatkit/app/core/data/repository/ConversationSearchLikePattern.kt:30/41/64`<br>**重放脚本：`tools/verification/c1s_conversation_type_filter_replay.py`（83,955 字节 / 1,382 行，sha256 `3b3e12be5d685670ae4121188e4fcd1b4de6a81a31c3317f3b6e7dbd8fc00ce0`，已入库）**<br>输出日志（仓库外）：`/tmp/opencode/c1s_check.log`（478 行 / 66,933 字节，sha256 `5645fa1350caf88afb5e33d3e52c9ebbab0b64975a041715559b9cac76a40073`） <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件在 `6ba95422` 那版为 **1982 行 / 5 个 `@Test`**，HEAD `74d82476` 已增至 **2221 行 / 5 个 `@Test`**（`@Test` 个数未变））；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。 | `verified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名的取得途径已从「uuid 反查」改成 wire 直取：mock 下已在真机复核，真实网关下仍未复核**（`StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修，wire 名现落 `UIMessage.wireModelName`；零设备已有两条证据：JVM 9 例 / 真实网关 2 个模型 HTTP 200；✅ **新增真机侧一条**：t1/t3 两份 pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报的串**，不是本地 `Model` 的 UUID 反查值 ⇒ wire 优先逻辑在真机上生效。⚠️ **但硬理由①仍不消**：t1–t4 走的是 **mock provider**（`adb reverse` + 本机 `mock_openai_v2.py`），**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑过**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**）⚠️ **订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并首次产出正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ 硬理由①对真实网关那条路径已消（判据 = 「判定规则」第八条）；本行状态仍是 `unverified`（逐行依据见第八轮⑨）。**② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**（⚠️ 第九轮订正：chip 两半已拿到真机 UI 证据、@ 选择器已有覆盖，头像组仍弱——见「C1 真机证据采集第九轮」①/④；**仍不升级**） ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。~~**✅ 已消（2026-10-06 第七批，HEAD `86e88970d`）**：那 4 条在 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` 上一条一条单跑，`am instrument` **四条退出码全是 `0`**、输出全是 **`OK (1 test)`**（Time 2.142 / 1.746 / 3.896 / 1.842）；三份证据 JSON 已 `adb pull` 回主机并记 SHA-256。⚠️ **第四条的 app 侧 JSON 未 pull**（只有 `am instrument` 原始日志 + mock 服务端独立记录作旁证）——逐条边界见「C1 真机证据采集第七轮」。⚠️ **这四条跑的是 mock provider**，「真实网关那次是否在 wire 优先逻辑下重跑过」仍零份（硬理由①的部分子理由，见上）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**成员头像组 / @ 选择器 / 抽屉筛选 chip 的真机 UI 端到端**——**一项都没做**（硬理由②）；主机侧那 438 条断言是 SQL 语义，替代不了 UI。**<br>**新窗口（HEAD `db4cdd77`，2026-10-06）：仍是 `unverified`。** 本批四个 commit（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）改的全是 `TavernChatCodec.importGroup` 的**导入侧契约缺口**（密钥黑名单 / 角色卡去重 / 配置校验 / 四态 `importReport`），⚠️ **零设备、零 `adb`**，且 `importGroup` 在 `app/src/main` 里**仍然没有调用方**——所以它**不构成本文件任何用例的验收证据**，契约 `:206` 点名的四类产物**一份未增**。单测口径由 `105 类 / 808 例` 变为 **107 类 / 855 例**（新增 47 条，全在导入侧；`importShare` 一行未动、导出路径一行未动、golden 哈希未变） —— ⚠️ 导入侧不涉及；成员头像组 / @ 选择器 / 抽屉筛选 chip 的**真机 UI 端到端仍一项都没做**（硬理由②）。**<br>**第九轮（HEAD `4d73a26b4`，2026-10-06）：仍是 `unverified`。** chip 的「真的按类型过滤」+「不丢数据」两半首次拿到**真机 UI 级证据**（单聊下 0 群、群聊下 0 单聊；切回 XML 与切走前逐字节相同；切换前后主库 6 行与 `rikka_hub`/`-wal`/`-shm` 三文件 SHA 完全不变——见「C1 真机证据采集第九轮」①）。⚠️ **不升级**：① 契约 `:232-235` 明写「只看截图或只看 UI 状态均标记 `unverified`」，且仓内无对应 androidTest；② 成员头像组仍只有弱证据（无断言、点按未证实）；③ 契约四类产物本行仍零份。**<br>**第十轮订正（HEAD `d133b400b`，2026-10-06）：② 句里「成员头像组（无断言）」已过期**——本批纳入 `C1GroupFilterAndAvatarUiTest.groupMemberBarRendersOneAvatarPerRole` 并**真机通过**（`INSTRUMENTATION_STATUS_CODE: 0`），成员头像组首次有 **androidTest 级自动化断言**；但同批 chip / @ 两条**未跑完**（`Process crashed`，一次 `kill -3` 操作失误）、**非空验证未做**，且该断言对象是头像组渲染、**不是 C1-10 点名的筛选/混排路径**；① 句里「仓内无对应 androidTest」仅对头像组这半句失效（chip 仍无 androidTest）。⇒ **判定仍不升级，仍 `unverified`**（逐行依据见「C1 真机证据采集第十轮」⑨）。**<br>**第十一轮订正（2026-10-06）：筛选 chip 与 @ 选择器两条真机 androidTest 通过（第十一轮⑦）；契约 `:206`/`:232-235` 不认仅 UI 级证据、四类产物仍零份 ⇒ `unverified` 不变。**<br>**第十三轮订正（2026-10-06）：与本行无关 ⇒ 状态不变。**<br>**第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：与本行无关 ⇒ 状态不变（仍 `unverified`）。** 本批 5 条全部不涉及筛选/混排/真机 UI。<br>**第十五轮订正（零设备，HEAD `d6494e49a`，2026-10-07）：仍是 `unverified`。** 新增设备侧测试 `1c48b1701`（`C1GroupPagingAndFilterDeviceTest.kt` 813 行 / 8 `@Test`）但**未真机跑**；契约 `:232-235` 不认仅 UI 级证据、四类产物零份，见第十五轮（零设备）⑤/⑦。<br>**真机第十五轮订正（HEAD `37630adb4`，2026-10-07）：仍 `unverified`。** 设备侧 8 方法 3 通过 / 5 失败（`pagingSource_*` 累计 60/40 vs DB 55/25），另一任务在修；契约 `:232-235` 四类产物仍零份 ⇒ 不升。见「第十五轮」②#5。<br>**第二十批订正（零设备，HEAD `5c362851a`，2026-10-07）：仍 `unverified`。** `0ae9f570a`（测试驱动改首屏 Refresh / 后续 Append）+ `5ca9ecbaf`（生产 `ConversationRepository` 四个 offset 分页 API 改 `Append`）已修 5 条 `pagingSource_*` 失败，但**真机未复跑**、生产修复只影响 HTTP 分页 API、契约 `:232-235` 不认仅 UI/逻辑级证据、四类产物零份 ⇒ 不升。<br>**真机第十六轮订正（HEAD `bd88aaff2`，2026-10-07）：仍 `unverified`（但 8/8 全绿）。** `0ae9f570a`+`5ca9ecbaf` 修复后本批真机 `C1GroupPagingAndFilterDeviceTest` **8/8 `OK (1 test)`（两遍都 8/8）**，上一批 5 条 `pagingSource_*` 失败全部转绿（#4 `second_size=15`、#5 `[20,20,15]/55/55/55`、#6 `group=[20,5]/25`、#7 `rows=55`、#8 `25/55`）。但 **契约 `:232-235`「只看截图或只看 UI 状态均标记 `unverified`」**（#1/#2/#3 落 Compose 语义树 / badge 节点）+ **契约 `:206` 逐例四类产物（viewer 可见消息 ID / 模型调用序列 / token / 导出哈希）本例结构性不产出**（不启发模型、不导出群文件）⇒ 维持 `unverified`。见「C1 真机证据采集第十六轮」②/⑤。<br>**第二十二批订正（真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，2026-10-07）：维持 `unverified`（本批未碰抽屉分页/筛选）。**<br>**第二十三批订正（真机第十八轮，HEAD `2a0610705`，2026-10-07）：维持 `unverified` —— 新增的真实网关轮（`realGatewayRoundInFixtureGroupProducesContractArtifacts`）跑在既有夹具群上，**与 C1-10 点名的「单聊/群聊筛选/混排」路径正交**，其 8 字段不由该路径产出；筛选/混排本身仍只有 UI/分页/SQL 级证据，契约 `:232-235` 不认仅 UI 状态 ⇒ 不升。见「第十八轮」③。**<br>**第二十四批订正（真机第十九轮，HEAD `1758e2109`，2026-10-07）：**升 `verified`** —— 第 9 条改造为从「群聊」筛选路径内抵达群聊并真跑一轮（点 chip → 生产 DAO 取筛选首屏 → 抽屉点击该行 → 反射读 `NavViewModel.currentPage` 断言导航 → 在该群跑真网关轮），8 字段首次由**筛选路径打开的群**产出；契约 `:232-235` 不再是「只看 UI 状态」、`:206` 8 字段齐（commit/命令退出码/设备/输入/viewer 台账/调用序列/token/导出哈希）⇒ 由 `unverified` 改 `verified`。见「C1 真机证据采集第十九轮」②。**<br>**第二十五批订正（真机第二十轮，HEAD `83dbf014f`，2026-10-07）：维持原判**（本批新增 C1-09 同源往返用例 `C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`，只触及 C1-09 点名的导出/QR 路径；本用例点名的路径未被触碰）。|

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

⚠️ **再往后一批（`c7535ca8` 把气泡显示改成 `message.modelId` 优先）之后，`app`
实测是 87 个测试类 / 691 tests / 0 failures / 0 errors / 0 skipped**
（`./gradlew --offline :app:testDebugUnitTest --rerun` 退出码 **0**，87 个 XML 全部重写，
逐个 `tests` 属性汇总实测：87 / 683 → **87 / 691**，即 `+0 类 / +8 例`，全部落在
`feature.chat.GroupMessageModelTest` 10 → 18）。`feature.chat.GroupTurnModelTest` 仍是
14（改的是第 14 条断言的内容，用例数不变）。**合计那一行同样没重跑全仓 `test`、
不更新。**

⚠️ **再往后一批（`366b3fe8` 滚动条 / `b43f7e9f` 抽屉 folder_id / `ed21db6e` 通知标题
/ 本轮 `88ba63c2` + `8bc28105` + `c940813b` 护栏）之后，`app` 实测是 91 个测试类 /
710 tests / 0 failures / 0 errors / 0 skipped**（`./gradlew --offline
:app:testDebugUnitTest --rerun` 退出码 **0**，逐个 XML 的 `tests` 属性汇总实测：
**87 / 691 → 91 / 710**，即 `+4 类 / +19 例`）。`+4 类` 是
`ChatListScrollbarTest`（7）、`ConversationDrawerFolderScopeTest`（4）、
`ChatManagerNotificationSenderNameTest`（5）、`ChatServiceSenderNameGuardTest`（3），
合计 `7+4+5+3 = 19`。`GroupMessageModelTest` 仍是 18、`GroupTurnModelTest` 仍是 14。
**合计那一行同样没重跑全仓 `test`、不更新**（`app` 之外的 14 个模块本轮一个测试都没
重跑，任何外推都不是实测值）。

⚠️ **再往后一批（C1-S：抽屉 type 筛选重放 + LIKE 转义 + `id` 兜底，`215296f1..6982869b`
8 个 commit）之后，`app` 实测是 92 个测试类 / 722 tests / 0 failures / 0 errors /
0 skipped**（`./gradlew --offline :app:testDebugUnitTest --rerun` 退出码 **0**，
92 个 XML 全部重写，逐个 `tests` 属性汇总实测：**91 / 710 → 92 / 722**，即
`+1 类 / +12 例`，全部落在**新增**的
`core.data.repository.ConversationSearchLikePatternTest` 12）。
`ConversationTypeFilterSourceGuardTest` 的 `tests` 属性仍是 **2**（**用例数不变不等于
断言没变**——它第 3 条断言的**期望串被改了一行**，见下一条与「C1-S」那节的显眼登记）。
**合计那一行同样没重跑全仓 `test`、不更新**（`app` 之外的 14 个模块本轮一个测试都没重跑）。

⚠️ **再往后一批（真机证据采集 `ca717f39..2c1d7632` + 助手页必崩修复 `0a289af6` /
`c3909047` + 试验页删除 `18baa930..1e9d5607`，共 10 个 commit）之后，`app` 实测是
**92 个测试类 / 723 tests / 0 failures / 0 errors / 0 skipped**（逐个 XML 的 `tests` 属性
汇总实测，最大 `timestamp` = `2026-10-05T03:48:20.578Z`）：**类数不变，例数 722 → 723，
即 `+0 类 / +1 例`**，那一例来自 `c3909047` 往
`NavBackStackSerializationTest.kt` 里**新增**的 `everyScreenMemberHasAWorkingSerializer`
（+118 行；该类的 `tests` 属性 **4 → 5**，`4a4def40` 那版只有 4 条 `@Test`）。
⚠️ **这只是逐个 XML 汇总的结果，不是本轮重跑出来的**——本次任务只改文档、
**按要求没有跑 gradle**。上面 `92 / 722` 那个窗口的实测值**保留不覆写**，
本段是新窗口的汇总值，**两者不冲突**。**合计那一行同样没重跑全仓 `test`、不更新**。

⚠️⚠️⚠️ **再往后一批（真实公网网关证据 `6ba95422` + P0/P1/P2 三批补测与修复
`af104850` / `9844cbc4` / `e7ad2a77` / `87f9c209` / `e6764293`，共 6 个 commit）之后，
`app` 实测是 97 个测试类 / 754 tests / 0 failures / 0 errors / 0 skipped**
（**本轮同样没有跑 gradle**，这是逐个 `TEST-*.xml` 的 `tests` / `failures` / `errors` /
`skipped` 属性用 Python 求和实测，`glob` 命中 **97 个 XML**）：
**92 / 723 → 97 / 754，即 `+5 类 / +31 例`**。逐类增量：

| 类 | 前 | 后 | 来自 |
|---|---:|---:|---|
| `feature.chat.GroupTurnCoordinatorTest` | 63 | **72** | `9844cbc4`（+3 `mention_role_ids` 放行分支）+ `e7ad2a77`（+6 视角过滤盲区） |
| `core.data.model.GroupChatTest` | 18 | **22** | `af104850`（+4 `role_id == SUMMARY_ID` 拒绝） |
| `core.data.ai.tavern.TavernChatMessageDecodeGuardTest` | — | **2** | `074e0e20`（上一批，**台账表此前漏登**） |
| `feature.chat.GroupChatPageDisplayLineSourceGuardTest` | — | **2** | `12dcdfdb`（上一批，**台账表此前漏登**） |
| `core.network.routes.GroupConversationOperationGuardTest` | — | **7** | `87f9c209` |
| `core.network.routes.GroupOperationApiGuardSourceGuardTest` | — | **4** | `87f9c209` |
| `feature.chat.GroupTieBreakScaffoldingDropSourceGuardTest` | — | **3** | `e6764293` |

⚠️ **`+31 例` 的算法**：+3 +6 +4 +2 +2 +7 +4 +3 = **31** ✅，类数 92 + 5 = **97** ✅
（`ConversationSearchLikePatternTest` 12 是上一批 C1-S 加的，**本批没动它**；
其余 90 个类的 `tests` 属性逐行未变）。
⚠️ **合计那一行（15 模块 / 180 类 / 1291 tests）本轮同样没重跑全仓 `test`、不更新**
——`app` 之外的 14 个模块一个测试都没重跑，任何外推都不是实测值。

⚠️⚠️⚠️⚠️ **再往后一批（平票脚手架三处漏回收 + 残留旧 job 两道守卫，
`e72793e6..8ccc0264` 5 个 commit）之后，`app` 实测是 99 个测试类 / 783 tests /
0 failures / 0 errors / 0 skipped**（**本轮同样没有跑 gradle**，这是逐个
`TEST-*.xml` 的 `tests` / `failures` / `errors` / `skipped` 属性用 Python +
`xml.etree.ElementTree` 求和实测，`glob` 命中 **99 个 XML**；`<skipped/>` 是
`<testcase>` 的子节点，逐个 `<testcase>` 树求和确认 **0**）：
**97 / 754 → 99 / 783，即 `+2 类 / +29 例`**。逐批增量（三批**分开**记，因为类数是
`①` **不**涨的那一批）：

| 批次 | commit | 类 | tests | 逐类变化 |
|---|---|---:|---:|---|
| 基线 | `ca3af416` 之后 | 97 | 754 | — |
| ① 平票脚手架三处漏回收 | `e72793e6` + `8a5f89d7` | **97**（+0） | **758**（+4） | `GroupTieBreakScaffoldingDropSourceGuardTest` **3 → 7** |
| ② 残留旧 job 不推进/不跨轮盖戳 | `2fdee352` | **98**（+1） | **771**（+13） | `GroupTurnCoordinatorTest` **72 → 78**；**新增** `GroupStaleJobCommitSourceGuardTest` 7 |
| ③ `onFailure` 补同款守卫 | `ef716dd3` + `8ccc0264` | **99**（+1） | **783**（+12） | `GroupTurnCoordinatorTest` **78 → 84**；**新增** `GroupStaleJobFailureSourceGuardTest` 6 |

⚠️ **`+29 例` 的算法**：+4 + 13 + 12 = **29** ✅，类数 97 + 0 + 1 + 1 = **99** ✅
（`git diff --stat ca3af416..HEAD -- 'app/src/test/**'` 实测**只有 4 个测试文件**被改：
`GroupTurnCoordinatorTest.kt`、`GroupStaleJobCommitSourceGuardTest.kt`（新增）、
`GroupStaleJobFailureSourceGuardTest.kt`（新增）、
`GroupTieBreakScaffoldingDropSourceGuardTest.kt`；其余 95 个类的 `tests` 属性逐行未变）。
⚠️ **本轮新增的两类 + 改的两类之外，`+29` 里没有别的东西**——`GroupTurnCoordinatorTest`
的 72 → 84 与两个护栏类的 3 → 7 也都是**加用例、零删改**。
⚠️ **合计那一行（15 模块 / 180 类 / 1291 tests）本轮同样没重跑全仓 `test`、不更新**
——`app` 之外的 14 个模块一个测试都没重跑，任何外推都不是实测值。

⚠️⚠️⚠️⚠️⚠️ **再往后一批（群配置面板两个必崩 `33eb801e` / `5d58f923` / `71d1439e` /
`54c196ca` + Coil 结构性修复 `ea482935` / `2766afc7` + 四项代码债
`fa36c65c` / `06cf6181` / `bed09118`，HEAD `a74c1820`，共 10 个 commit）之后，`app`
实测是 **105 个测试类 / 808 tests / 0 failures / 0 errors / 0 skipped**（**本轮同样没有
跑 gradle**——本轮是纯文档任务，按约束不跑；这是逐个 `TEST-*.xml` 的 `tests` /
`failures` / `errors` / `skipped` 属性用 Python + `xml.etree.ElementTree` 求和实测，
`glob` 命中 **105 个 XML**）：
**99 / 783 → 105 / 808，即 `+6 类 / +25 例`**。逐类增量：

| 类 | 前 | 后 | 来自 |
|---|---:|---:|---|
| `app.CoilImageLoaderSourceGuardTest` | — | **6** | `2766afc7` |
| `feature.chat.GroupRunAbandonDrainSourceGuardTest` | — | **5** | `bed09118` |
| `feature.chat.ChatServiceGroupChatFailLoudGuardTest` | — | **4** | `bed09118` |
| `core.ui.components.ui.BottomSheetScrollSourceGuardTest` | — | **3** | `54c196ca` |
| `core.ui.components.ui.PrimaryBottomSheetIconSourceGuardTest` | — | **3** | `5d58f923` |
| `core.ui.components.message.GroupForkDisableReasonSourceGuardTest` | — | **2** | `bed09118` |
| `feature.chat.GroupStaleJobCommitSourceGuardTest` | 7 | **9** | `bed09118`（+2 例：禁「按 key 无条件删」的旧形状 + 钉 `null ->` 分支走按令牌清） |

⚠️ **`+25 例` 的算法**：6 + 5 + 4 + 3 + 3 + 2 = **23**（6 个新类）+ 2（在册类
`GroupStaleJobCommitSourceGuardTest` 7 → 9）= **25** ✅；类数 99 + 6 = **105** ✅
（`git diff --stat 361c7cf6..a74c1820 -- 'app/src/test/**'` 实测**只有 7 个测试文件**被改：
6 个新增 + `GroupStaleJobCommitSourceGuardTest.kt` 扩写；其余 99 个类的 `tests` 属性逐行
未变，**零删除既有用例**）。
⚠️⚠️ **这 7 个新类里有 3 个与 C1 无关**（`CoilImageLoaderSourceGuardTest` 6 /
`PrimaryBottomSheetIconSourceGuardTest` 3 / `BottomSheetScrollSourceGuardTest` 3，共 **12 例**），
它们是 UI / 浏览器侧的护栏，**不进下面那张「C1 相关 JVM 测试类台账」**（口径见该小节）。
⚠️ **但 `BottomSheetScrollSourceGuardTest` 与 `PrimaryBottomSheetIconSourceGuardTest`
各含一条「群配置面板定点回归」用例**，所以它们与 C1 有交集——**本台账的判定口径是
「整类是否属于 C1 验收范围」，不是「有没有一条用例碰到 C1 的文件」**；这两类整体归到
C1 之外，逐条用例的归属留给下一位按需复核。
⚠️ **合计那一行（15 模块 / 180 类 / 1291 tests）本轮同样没重跑全仓 `test`、不更新**。

⚠️ **本轮改了一行既有测试断言的期望串——先说清它是不是第一次：**
**改既存测试文件这件事，锚点之后不是第一次**——`09b4764b` 重设计过 `GroupTurnModelTest`
第 14 条跨侧断言（51 insertions / 13 deletions）、`e4fc4644` 把 `GroupMessageModelTest`
从 10 条扩到 18 条、`90d1dbac` 与 `c940813b` 也都动过既存测试文件。
**但「因为修生产代码的缺陷、被迫把断言的期望字面串跟着改」这是第一次**：那几次改的是
断言的**设计或强度**，这一次改的是期望的**字面内容**。所以单独立一条显眼记录。
`ConversationTypeFilterSourceGuardTest.kt` 第 68-73 行那条断言的**期望串**从

```
"title LIKE '%' || :searchText || '%'\" + CONVERSATION_TYPE_PREDICATE_SQL"
```

改成

```
"title LIKE '%' || :searchText || '%'\" + CONVERSATION_LIKE_ESCAPE_SQL + CONVERSATION_TYPE_PREDICATE_SQL"
```

原因、结构上为什么合法、以及**改完还有没有牙齿**，写在「C1-S 抽屉 type 筛选主机侧重放
与两个真实缺陷修复（零设备）」那节（见该节「⚠️⚠️ 敏感项」小节）。一句话版：
SQLite 文法要求 `ESCAPE` 必须紧跟 LIKE 的右操作数之后，所以 `ESCAPE` 只能插在 LIKE
片段与 type 谓词常量之间，而那条断言用**逐字节相邻**表达「type 谓词不允许出现第二份
副本」的意图——**断言的结构合法地变了，意图没变**。该文件**只改了这一行**
（`git diff --stat` = 1 insertion / 1 deletion，单 hunk），**三条断言一条没被削弱或删除**。

### C1 相关 JVM 测试类台账

口径：**本文件正文点名引用过、且能在 `app/build/test-results/testDebugUnitTest/*.xml`
里对上的测试类**（每类的 `tests` 取自该次实测 XML，不是数 `@Test` 注解）。仪器测试
不在此表（另见「仪器测试状态」）。下面 **35 类 / 366 例**里，**本轮新增 5 类 18 例**
与更早各窗口新增的 8 类 43 例都用**加粗**标出，其余 22 类 305 例更早就在册。

⚠️ 台账口径**没有变**，只是本轮实测值从 **30 类 / 335 例** 变成 **35 类 / 366 例**
（`+5 类 / +18 例`；两个老类的用例数同时更新：`GroupTurnCoordinatorTest` **63 → 72**、
`GroupChatTest` **18 → 22**，合计 `+13`，所以整表是 `335 + 13 + 18 = 366` ✅）。
判定标准仍是「正文点名引用 + XML 对得上」这**一条**，没有另立一套。

⚠️⚠️ **本轮新增的 5 类里有 2 类是「上一批就加了、但台账表漏登」的**
（`TavernChatMessageDecodeGuardTest` 2 来自 `074e0e20`、
`GroupChatPageDisplayLineSourceGuardTest` 2 来自 `12dcdfdb`）——它们本来就满足
「正文点名 + XML 对得上」，是上一轮**忘了往表里加**，本轮补上，与口径变化无关。
另外 3 类是本批真新增：`GroupConversationOperationGuardTest` 7 /
`GroupOperationApiGuardSourceGuardTest` 4（`87f9c209`）、
`GroupTieBreakScaffoldingDropSourceGuardTest` 3（`e6764293`）。
⚠️ `GroupConversationOperationGuardTest` 的**全名带包名前缀是
`heizige.kk.khatkit.app.core.network.routes.GroupConversationOperationGuardTest`**——
早前口头简写成「`ConversationOperationGuardTest`」，按那个短名在 XML 里**找不到文件**。

复算命令（逐行核对本表的每一类，**本轮实测输出 `rows 35 / declared sum 366 /
mismatch vs XML: []`**，即表里 35 行的例数与 XML **逐行相等、无一例外**）：

```
python3 - <<'EOF'
import glob, re, xml.etree.ElementTree as ET
src = open('docs/eval/c1-group-chat.md', encoding='utf-8').read().split('\n')
i = next(k for k, l in enumerate(src) if l.startswith('### C1 相关 JVM 测试类台账'))
rows = []
for l in src[i:]:
    if l.startswith('### ') and l != src[i]: break
    m = re.match(r'^\|\s*\*{0,2}`([A-Za-z0-9_]+)`\*{0,2}\s*\|\s*\*{0,2}(\d+)\*{0,2}\s*\|', l)
    if m and m.group(1) != '测试类': rows.append((m.group(1), int(m.group(2))))
xml = {}
for p in glob.glob('app/build/test-results/testDebugUnitTest/TEST-*.xml'):
    r = ET.parse(p).getroot(); xml[r.get('name').split('.')[-1]] = int(r.get('tests'))
print('rows', len(rows), 'declared sum', sum(n for _, n in rows))
print('mismatch vs XML:', [(c, n, xml.get(c)) for c, n in rows if xml.get(c) != n])
EOF
```

⚠️ **新窗口（HEAD `361c7cf6`）按同一条口径重算**：上面那条脚本**本轮已经不能直接用**
——它用 `\|\s*\*{0,2}(\d+)\*{0,2}\s*\|` 抓例数，而本轮有两个在册类的例数格按惯例**追加**
成了 `**72**<br>**84**（…）` 这种形式，于是那两行**被旧正则整行跳过**，实测输出变成
`rows 35 / declared sum 304`（两个新类仍被算进去，掉的正是那两行）——**这不是数据错，
是正则没跟上格式**。改用「取格子里最后一个纯数字粗体span」的口径，实测输出：

```
rows 37 / declared sum 395 / mismatch vs XML: []
```

即表里 **37** 行的例数与 XML **逐行相等、无一例外**（与上一窗口 `35 / 366` 的差值
`+2 类 / +29 例`，与「测试结果」段那个 `+29` 对得上）。新口径脚本：

```
python3 - <<'EOF'
import glob, re, xml.etree.ElementTree as ET
src = open('docs/eval/c1-group-chat.md', encoding='utf-8').read().split('\n')
i = next(k for k, l in enumerate(src) if l.startswith('### C1 相关 JVM 测试类台账'))
rows = []
for l in src[i:]:
    if l.startswith('### ') and l != src[i]: break
    c = [x.strip() for x in l.strip().strip('|').split('|')]
    if len(c) < 2: continue
    m = re.match(r'^\*{0,2}`([A-Za-z0-9_]+)`\*{0,2}$', c[0])
    if not m or m.group(1) == '测试类': continue
    b = re.findall(r'\*\*(\d+)\*\*', c[1])          # 例数列里最后一个纯数字粗体
    if b: rows.append((m.group(1), int(b[-1])))
xml = {}
for p in glob.glob('app/build/test-results/testDebugUnitTest/TEST-*.xml'):
    r = ET.parse(p).getroot(); xml[r.get('name').split('.')[-1]] = int(r.get('tests'))
print('rows', len(rows), 'declared sum', sum(n for _, n in rows))
print('mismatch vs XML:', [(c, n, xml.get(c)) for c, n in rows if xml.get(c) != n])
EOF
```

⚠️ **旧的 `366 / 35 类` 那一行按惯例保留不覆写**，它是第四个窗口的实测值；
两行合计**不要相加**（`395` 已经包含了那两个在册类的新值）。

⚠️⚠️⚠️ **第六个窗口（HEAD `a74c1820`）订正一处「复算脚本本身不可信」的坑**：
上面那条「新口径脚本」**跑不出它自己声称的 `rows 37 / declared sum 395`**——
本轮原样跑它，实测输出是 **`rows 14 / declared sum 172`**。原因是它用
`re.findall(r'\*\*(\d+)\*\*', c[1])` 抓例数、后面又 `if b:`，所以**例数格不是粗体的行
（表里 23 行是裸数字）整行被跳过**，只剩 14 行粗体行。**这不是数据错，是脚本的筛选条件
与表里的两种写法不兼容**——而更糟的是它输出里那个 `mismatch vs XML` **看起来仍然像
「无异常」**（因为只剩 14 行可比），⚠️ **一个只报 14 行的复算脚本会给人「已核对过」的
错觉**。
✅ **本轮用下面这条（只多了一行 `elif re.fullmatch(r'\d+', c[1])`）重算，实测输出**：

```
rows 40 / declared sum 408 / mismatch vs XML: []
```

即表里 **40** 行的例数与 XML **逐行相等、无一例外**。⚠️ **再提醒一次那个反例**：早前实测
「扫正文里点名过的类」会算成 39 类 / 399 例，多出来的 4 类（`LorebookEngineTest` /
`AutomationTracerTest` / `NavBackStackSerializationTest` / `ExampleUnitTest`）与 C1 无关
——**本表是按语义相关性人工维护的，复算方式是「逐行核对表内 40 行」，不是「扫正文」**。

```
python3 - <<'EOF'
import glob, re, xml.etree.ElementTree as ET
src = open('docs/eval/c1-group-chat.md', encoding='utf-8').read().split('\n')
i = next(k for k, l in enumerate(src) if l.startswith('### C1 相关 JVM 测试类台账'))
rows = []
for l in src[i:]:
    if l.startswith('### ') and l != src[i]: break
    c = [x.strip() for x in l.strip().strip('|').split('|')]
    if len(c) < 2: continue
    m = re.match(r'^\*{0,2}`([A-Za-z0-9_]+)`\*{0,2}$', c[0])
    if not m or m.group(1) == '测试类': continue
    b = re.findall(r'\*\*(\d+)\*\*', c[1])          # 先抓例数列里最后一个纯数字粗体
    if b: rows.append((m.group(1), int(b[-1])))    # ⚠️ 追加形态（如 **72**<br>**84**（…））
    elif re.fullmatch(r'\d+', c[1]):               # ⚠️ 这一支是本轮补的：裸数字形态
        rows.append((m.group(1), int(c[1])))
xml = {}
for p in glob.glob('app/build/test-results/testDebugUnitTest/TEST-*.xml'):
    r = ET.parse(p).getroot(); xml[r.get('name').split('.')[-1]] = int(r.get('tests'))
print('rows', len(rows), 'declared sum', sum(n for _, n in rows))
print('mismatch vs XML:', [(c, n, xml.get(c)) for c, n in rows if xml.get(c) != n])
EOF
```

✅✅ **第七个窗口起，上面那三条内联「复算脚本」全部作废，改用
`tools/verification/c1_doc_stats.py`。** 三条脚本按惯例**保留在本文里不删**
（它们是「口径怎么一步步走偏」的历史证据），但**不要再照抄运行**。
⚠️ **本轮原样跑第三条的实测输出**（不是它自称的 `mismatch vs XML: []`）：

```
rows 40 declared sum 408
mismatch vs XML: [('GroupTurnCoordinatorTest', 84, None), ('GroupTavernExportTest', 22, None), … 共 40 项全部 None]
```

原因：`app/build/test-results/testDebugUnitTest/` 里**只剩 1 个 XML**（最后一次
gradle 跑的是带 `--tests` 过滤的），`xml.get(c)` 全部取不到。⚠️ **这比「骗人」更
隐蔽**：旧脚本**没有「陈旧 XML」这个概念**，它分不清「表里的数错了」与「XML 不是
全量的」——两种完全不同的情况，它给出的是同一串输出。


⚠️ **为什么不直接用「正文里点名过就计入」那种更短的命令**：那种口径会把
`docs/` 两个文件里为**别的目的**被提到的类也算进来（本轮实测会算成 **39 类 / 399 例**，
多出来的 4 类是 `LorebookEngineTest` 22、`AutomationTracerTest` 5、
`NavBackStackSerializationTest` 5、`ExampleUnitTest` 1——它们分别在酒馆 lorebook、
自动化、导航序列化与模板用例里被提到，**与 C1 无关**）。**本表是按语义相关性人工维护的**，
所以复算方式是「逐行核对表内 35 行」，不是「扫正文」。
⚠️ 早前正文另一处（「用例矩阵」段下方那份清单）记的是「25 类 / 304 例」与「合计 296」
两个**互相矛盾**的旧数，**以本表为准**（订正记录见该段末尾的 ⚠️⚠️⚠️）。

⚠️ **第七个窗口（HEAD `db4cdd77`）新增 2 类 47 例 → 42 类 / 455 例**：
`GroupImportScreeningTest` **25**（筛查与去重纯函数，含「放行的必然过 `validate`」
与「归一化只许动 `revision`/`tie_policy`」两条结构性不变式）、
`TavernGroupImportGateTest` **22**（端到端，含逐字节往返不变式与
`NoConfig`/`Rejected` 分流）。**口径没变**（仍是「正文点名 + 对得上」），
只是本批把这两个类**写进了正文**，所以它们够格进表。⚠️ `455 = 408 + 25 + 22`。
⚠️ 上面的 `35 类 / 366 例`、`37 类 / 395 例`、`40 类 / 408 例` 三个数**按惯例保留
不覆写**，它们是各自窗口的实测值，**不要相加**。
✅ 复算：`python3 tools/verification/c1_doc_stats.py --only ledger`，本轮实测
`台账行数 42 行 / 声明合计 455 例 / 逐行核对 42 行全部相等`（逐行核对用的是
**源码 `@Test` 计数**，不是文档文本解析——见「统计口径复算脚本」小节）。

| 测试类 | 例数 | 钉住什么 |
|---|---:|---|
| `GroupImportScreeningTest` | **25**（HEAD `db4cdd77` 实测） | `importGroup` 的 `screenImportedConfig` 分流 + 角色卡去重（纯函数） |
| `TavernGroupImportGateTest` | **22**（HEAD `db4cdd77` 实测） | `importGroup` 端到端闸门：密钥黑名单 / 去重 / 四态 `importReport` / 往返字节不变 |
| `GroupTurnCoordinatorTest` | **72**<br>**84**<br>**85**（HEAD `0e9d9b25` 实测；旧值 84 是 `361c7cf6` 那版的，72 是第四个窗口的） | 群聊内核判定（@ / pipeline / roundtable / vote / 预算 / 幂等 / 视角隔离 / **残留旧 job 的归属与终态守卫**） |
| `GroupTavernExportTest` | 22 | Tavern 群聊编解码往返 + 密钥过滤 |
| `TavernCompatTest` | 21 | 酒馆结构兼容 |
| `GroupChatTest` | **22**<br>**26**<br>**29**<br>**33**<br>**39**（HEAD `19545e076` 实测；旧值 33 是 `8a24b5d0` 之后那版的，29 是 `8a24b5d0` 那版的，26 是 `0e9d9b25` 那版的，22 是 `af104850` 那版的。⚠️ **39 这一跳是 `19545e076`**：改 2 条既有断言的期望值 + **新增 6 个用例方法**，逐条见「Unicode 行终止符统一切分（零设备）」那一节） | `GroupChat` 数据模型 / `parseBallot`<br>**`parseCandidates` / `parseBallot` 的行切分口径**（6 个 Unicode 行终止符，`be7952a83`；含「有意收紧」与「尾随 NEL 是真洞」两条护栏，见该节） |
| `GroupMessageModelTest` | 18 | 气泡模型显示（`message.modelId` 优先） |
| `UngeneratedMessageFilterTest` | 18 | 取消/失败时丢弃空气泡 |
| `QrScannerSheetTest` | 16 | 扫码弹层入口决策 |
| `GroupTurnModelTest` | 14 | `resolveGroupTurnModelId` 角色模型选型 |
| `GroupRunSchemaTest` | 10 | `group_runs` 表 / 复合主键 / `run_token` 幂等 |
| `MemorySpaceGateTest` | 9 | 群记忆空间闸门 |
| `MemoryAttributionTest` | 9 | 记忆按 `roleId` 归因 |
| `MemoryRetrievalEngineTest` | 9 | 检索引擎 |
| `GroupSpeakerResolverTest` | 8 | 说话者解算 |
| `GroupRoleCompletionProviderTest` | 8 | 群聊 @ 补全 |
| `ChatManagerTest` | 8 | `ChatManager` 杂项 |
| `GroupRoleCardsPersistenceTest` | 7 | 角色卡落库往返 |
| `ConversationGroupCardsSchemaTest` | 7 | Room 31→32 schema 一致性 |
| `ConversationListQueryPlanTest` | 7 | 抽屉列表查询判定（`type` 下沉 SQL） |
| `MemoryExtractorParseTest` | 7 | 抽取解析 |
| `MemoryToolScopeTest` | 7 | 记忆工具空间作用域 |
| **`ChatListScrollbarTest`** | **7** | **滚动条进度算式端点语义**（本轮 `31e0a39d`） |
| `C1pGroupExportHashTest` | 5 | 群聊导出确定性哈希 + golden 清单 |
| **`ChatManagerNotificationSenderNameTest`** | **5** | **通知标题公式 + 群聊分支必须重算**（`67f49726`） |
| **`ConversationDrawerFolderScopeTest`** | **4** | **抽屉两条查询的 `folder_id` 口径现状**（`b43f7e9f`） |
| **`ChatServiceSenderNameGuardTest`** | **1** | **通知标题公式全仓唯一性护栏**：扫全仓 `src/main` 的 `.kt`，`if (assistant.useAssistantAvatar)` 只允许出现在 `ChatManager.kt` 一处（⚠️ 原 3 例，**4 → 1**：`bothGenerationEntryPoints_consumeTheSharedFunction`、`chatService_gainsGroupPath_onlyIfItRecomputesSenderName` 两条依赖已删的 `core/service/ChatService.kt`，随 D1 清理删除；剩下这条覆盖面与删前**完全相同**）**（`8bc28105` / `c940813b` / D1 清理）** |
| **`ConversationSearchLikePatternTest`** | **12** | **LIKE 转义纯函数 + 两条源码护栏（5 条 DAO 查询带 ESCAPE / 5 个 Repository 转发点都过转义）（本轮 `79b13080`）** |
| `GroupMemorySpacePolicyTest` | 6 | 群记忆空间懒建策略 |
| `ConversationTypeFilterSourceGuardTest` | 2 | 抽屉类型筛选源码护栏（**第 3 条断言的期望串本轮改了一行**，见「C1-S」那节的显眼登记） |
| `MemoryRoleIdMappingTest` | 2 | `role_id` 映射 |
| `MemoryToolsSearchTest` | 3 | 记忆工具搜索 |
| **`GroupConversationOperationGuardTest`** | **7** | **群聊会话五个会话操作端点的门禁纯函数（放行 / 拒绝文案逐条不同 / 409 语义）（本轮 `87f9c209`）** |
| **`GroupOperationApiGuardSourceGuardTest`** | **4** | **源码护栏：五个端点的守卫必须排在 `initializeConversation` **之后**，`regenerate` 不得出现 `initializeConversation`**（本轮 `87f9c209`） |
| **`GroupTieBreakScaffoldingDropSourceGuardTest`** | **3**<br>**7**（HEAD `361c7cf6` 实测；旧值 3 是 `e6764293` 那版的） | **源码护栏：`failGroupTurn` 必须回收平票裁决脚手架，`completeGroupRound` 的 Decided/Undecided 各回收一次、NeedsChairTieBreak 故意不回收（`e6764293`）+ `cancelActiveGroupRun` / `abandonDanglingGroupRuns` 各含一次调用、drop 排在 `persistRoundState` 之后、无条件且恰好一次（`8a5f89d7`）** |
| **`TavernChatMessageDecodeGuardTest`** | **2** | **`TavernChatCodec` import 数组分支必须走 `documentFrom`（消掉第二份消息解码）**（`074e0e20`，**上一批漏登，本轮补进台账**） |
| **`GroupChatPageDisplayLineSourceGuardTest`** | **2** | **`GroupConfigErrorLine` / `RoleCardLine` 两对展示行的复用护栏**（`12dcdfdb`，**上一批漏登，本轮补进台账**） |
| **合计** | **366** | **35 类** |
| **`GroupStaleJobCommitSourceGuardTest`** | **7**<br>**9**<br>**10**（HEAD `d00880fc` 实测；旧值 9 是 `a74c1820` 那版的，7 是 `2fdee352` 那版的） | **源码护栏：提交路——`commitGroupTurn` 必须在 `stampGroupTurn` 之前调 `checkCommitAdmission`、`Denied` 分支只 Logging 不碰 `groupRunsInFlight`、令牌必须从 `takeGroupTurn` 返回值捕获（不得反查镜像）、`Halted` 分支不得 `persistRoundState`**（`2fdee352`）**＋（`bed09118` +2 例）禁「按 key 无条件删」的旧形状 `groupRunsInFlight.remove(conversationId)`（右括号前不许出现逗号）、`null ->` 分支必须走 `clearGroupRunMirrorIfMine` 按 runToken 清** |
| **`GroupStaleJobFailureSourceGuardTest`** | **6**<br>**7**（HEAD `d00880fc` 实测；旧值 6 是 `8ccc0264` 那版的） | **源码护栏：失败路——`failGroupTurn` 必须在四个副作用（`appendGroupMessages` / `persistRoundState` / `dropTieBreakScaffolding` / `groupRunsInFlight.remove`）全部之前判 `checkFailureAdmission`，拒收分支只有 Logging + return**（`8ccc0264`） |
| **`ChatServiceGroupChatFailLoudGuardTest`** | **1** | **DI provider 名 `provideChatManager` 必须与产物一致**（⚠️ 原 4 例，**4 → 1**：`ChatService` 里零群聊逻辑 / `handleMessageComplete` 顶部 `require(!isGroupConversation(...))` / `ChatService` 里唯一生成漏斗这三条保护对象 `core/service/ChatService.kt` 已被 D1 清理删除，`File(CHAT_SERVICE_FILE).readText()` 会抛 `FileNotFoundException`，故一并删除；**剩下一条护的是 `AppHiltModule`，与被删文件无关，必须保留**。⚠️ `ChatManager` 侧**不需要** fail-loud 闸门 —— 它原生支持群聊（`ChatManager.kt:787` `takeGroupTurn`），「拒绝群聊会话」才是错的契约）「群聊经 `ChatService` 生成」现在由**文件根本不存在**保证，比文本护栏更强**（`bed09118` / D1 清理）** |
| **`GroupRunAbandonDrainSourceGuardTest`** | **5** | **源码护栏：`abandonDanglingGroupRuns` 必须分批循环读到清空（不是单页 `limit = 8`）、循环必须有具名收敛上限、页大小必须是具名常量而不是内联字面量、每行判死前必须 `findByRound` 重读并 `isTerminal` 跳过**（`bed09118`） |
| **`GroupForkDisableReasonSourceGuardTest`** | **2** | **源码护栏：`ChatMessage.groupChat` KDoc 的 fork 段落禁因是「静默换会话类型」而不是账目错位；姊妹例要求重新生成 / 删除那两半必须保留它们自己的账目错位理由（防止整段被一刀切删掉）**（`bed09118`，**只加护栏、KDoc 一个字没改**） |
| **合计（新窗口，HEAD `361c7cf6`）** | **395** | **37 类**（上两行是本轮新增的 2 类 13 例；另有两个在册类的用例数同时更新：`GroupTurnCoordinatorTest` **72 → 84**（`+12`）、`GroupTieBreakScaffoldingDropSourceGuardTest` **3 → 7**（`+4`），合计 `+16`，所以整表是 `366 + 13 + 16 = 395` ✅。⚠️ 上面的 **366 / 35 类**那一行是第四个窗口的实测值，按惯例**保留不覆写**） |
| **合计（第六个窗口，HEAD `a74c1820`）** | **408** | **40 类**（新增 3 类 11 例：`ChatServiceGroupChatFailLoudGuardTest` 4 + `GroupRunAbandonDrainSourceGuardTest` 5 + `GroupForkDisableReasonSourceGuardTest` 2；另有一个在册类 `GroupStaleJobCommitSourceGuardTest` **7 → 9**（`+2`），合计 `+13`，所以整表是 `395 + 13 = 408` ✅。⚠️ **本窗口 `:app:testDebugUnitTest` 那 `+6 类 / +25 例` 里有 3 个类（12 例）不属于 C1、不进本表**——`CoilImageLoaderSourceGuardTest` 6 / `PrimaryBottomSheetIconSourceGuardTest` 3 / `BottomSheetScrollSourceGuardTest` 3，理由见「测试结果」段那个 ⚠️⚠️） |
| **`SummaryViewerScopeTest`** | **12** | **纯函数真单测：群聊「标题 / 摘要按谁的视角过滤」**——fail-closed 空名单（`roles` 为空返回**空列表**不是全量）/ 议长 id 悬空但名单完好时退回第一位且 `chairRound = false` / 幂等 / **反向越权**（别的角色发言不可见）/ 议长在 `roundtable` 下看得到本轮全部（`72a5e548`；⚠️ 「单聊逐字不变、返回同一 List 实例」这一分支也在这 12 条里） |
| **`SummaryViewerScopeWiringSourceGuardTest`** | **5** | **源码护栏：`ChatManager.generateTitle` / `compressConversation` 必须转调 `SummaryViewerScope` 而不是裸送 `conversation.currentMessages`，`splitMessages(compressScope)` 的接线不许改回 `splitMessages(messagesToCompress)`**；含一条**断言 KDoc 里那句「⚠️ 待产品确认」不许被删**（`72a5e548`） |
| **`GroupEditActionVisibilitySourceGuardTest`** | **5** | **源码护栏：群聊长按「角色发言」时 Edit 动作必须**按消息**收口 `!groupChat \|\| <消息>.role == MessageRole.USER`——不能照抄 `if (!groupChat)`，那会把群聊里合法的「改用户提问」一起关掉；展示层与触发层各一处，`groupChat = false`（单聊）时逐字不变（`d00880fc`。⚠️ **本文上一轮把它记成「1 条」，实测是 5 条**——`git log --diff-filter=AM -- *该类.kt` 只有 `d00880fc` 一个 commit，即它**自建立起就是 5 条**，上一轮那个数是笔误，已订正） |
| **`GroupViewerExhaustiveMatrixTest`** | **9** | **可见性穷举矩阵**——契约 `:200`「过滤器做 JVM 纯函数测试，断言三角色任意组合下不存在越权消息」的**唯一**可执行证据：`3 角色 × 3 模式 × 全 6 排列 × 2 轮（轮次间同序/逆序）× 全部 viewer` = **216 个 viewer case**。每个 case 断言**完整有序可见消息 id 列表 == 契约算出的列表**（不是「不含 X」），外加三条通用不变量（自可见 / 越权为零用七桶并集**集合相等** / `chairRound` 标志单调且保序不重排）。另含枚举空间自身的可数护栏（216 case、模式分布 36/36/144、6 排列两两不同）、两个 viewer 入口（`viewerMessages` / `visibleMessages` / `buildContext`）逐格相等、以及「过滤器不读 `config.mode`」。**变异验证 6 处全红**（自可见分支 / `roundStart` 守卫恒真 / `chairRound` 反向 / `SUMMARY_ID` 放行去掉 / `USER` 放行去掉 / `mention` 放行去掉），逐处 `git checkout --` 还原并 `diff` 确认逐字节相同；⚠️ **零设备、零模型输出、零 UI**——只证明过滤判定本身（`ddda36186`） |
| **`GroupRetryEntryTest`** | **8** | **群聊「失败续跑」入口的纯判定**：`GroupRetryEntry.canResume(isGroup, isLastMessage, message)` = 群聊 && 是最后一个节点 && `MessageRole.ASSISTANT` && `turnKind == GroupChat.TURN_ERROR` && `roleId != GroupChat.SUMMARY_ID`（`d6494e49a`，`app/src/test/java/heizige/kk/khatkit/app/feature/chat/GroupRetryEntryTest.kt`，8 个 `@Test`，XML `tests="8"`） |
| **合计（本轮，零设备，HEAD `d6494e49a`）** | **509** | **47 类**（新增 **1 类 8 例**：`GroupRetryEntryTest` 8；`git log --name-only b60aa80c5..HEAD -- 'app/src/test/*'` 实测只有这一个新文件。⚠️ `c1_doc_stats.py` 改前实测 **46 行 / 501 例**、改后 **47 行 / 509 例**——本行声明的 `509` 由脚本 `declared sum` 裁判。⚠️ 表内既有那条 `合计` 行的 `**506**` 与逐行求和的 `501` 差 `5`，属历史声明笔误，按惯例**保留不覆写**） |
| **合计（本轮，HEAD `40cc8e89` 之后）** | **479**<br>**484**<br>**487**<br>**491**<br>**497**<br>**506**（**506** 是 `ddda36186` 之后实测的；**497** 是 `19545e076` 之后实测的；旧值 491 是 `8a24b5d0` 之后那版的，487 是 `8a24b5d0` 那版的，484 是 `0e9d9b25` 那版的，479 是上一窗口的声明值） | **46 类**（新增 **3 类 22 例**：`SummaryViewerScopeTest` 12 + `SummaryViewerScopeWiringSourceGuardTest` 5 + `GroupEditActionVisibilitySourceGuardTest` 5；另有两个在册类的**声明例数随代码更新**：`GroupStaleJobCommitSourceGuardTest` **9 → 10**（`+1`）、`GroupStaleJobFailureSourceGuardTest` **6 → 7**（`+1`），合计 `+2`，所以整表是 `455 + 22 + 2 = 479` ✅。⚠️ **另外 2 个类本轮实测后仍然不进本表**——`BottomSheetScrollSourceGuardTest` **4** / `CoilImageLoaderSourceGuardTest` **6**（共 10 例），口径与「第六个窗口」那个 ⚠️⚠️ 逐字相同：整类不属于 C1 验收范围，`BottomSheetScrollSourceGuardTest` 那一条「群配置面板定点回归」**按惯例仍不进表**。⚠️ **本轮（HEAD `0e9d9b25`）另有两处**在册类的**声明例数随代码更新**：`GroupTurnCoordinatorTest` **84 → 85**（`a8fa1cb8` +1）、`GroupChatTest` **22 → 26**（`df560180` +4），合计 `+5`，所以整表是 `479 + 5 = 484` ✅（⚠️ **类数仍是 45**：这两条是**既有类的新增 `@Test`**，不新增类）。⚠️ 上面的 `366 / 35 类`、`395 / 37 类`、`408 / 40 类` 三个数，以及本行里那个**旧值 479**，按惯例**保留不覆写**。⚠️ **本轮（HEAD `8a24b5d0`）另有一处**在册类的**声明例数随代码更新**：`GroupChatTest` **26 → 29**（`8a24b5d0` **+3**：那批提交把 `df560180` 钉「代码块假阳性现状」的两条用例改写成修后行为，另新加 `leading blank…` / `only the first non blank line…` / `line separators…` / `unrelated inputs…` 四条，净增 `+3`），所以整表是 `484 + 3 = 487` ✅（⚠️ **类数仍是 45**：这条是**既有类的新增 `@Test`**，不新增类；⚠️ **本轮两处改动的方向都是往台账里追加**，上面 `0e9d9b25` 那段的 `GroupChatTest` **22 → 26**（`df560180` +4）与 `479 + 5 = 484` 说的是**那个窗口**的实测，按惯例**保留不覆写**，本轮的当前口径以 **487 / HEAD `8a24b5d0`** 为准）。⚠️ **本轮（`8a24b5d0` 之后，`parseBallot` 围栏假阳性）另有一处**在册类的**声明例数随代码更新**：`GroupChatTest` **29 → 33**（**+4**：改写 `a vote line inside a code fence…` 那条钉桩为修后行为，新增 `an unclosed code fence swallows the rest of the message fail closed` / `votes before and after a closed code fence still count` / `a candidate declaration can never sit inside a code fence` / `votes outside code fences keep exactly the behaviour they had before` 四条），所以整表是 `487 + 4 = 491` ✅（⚠️ **类数仍是 45**：这一条是**既有类的新增 `@Test`**，不新增类）。⚠️ **同轮 `parseCandidates` 只加了一条窄守卫**（声明行自己不能是围栏标记，堵 `~~~ 候选：a,b ~~~` 这一个真漏进来的形状），**没有**上围栏状态机——理由是「只认首个非空行」使「首行落在围栏内部」**结构上不可达**，详见 `GroupChat.parseCandidates` 的 KDoc 与那条 `a candidate declaration can never sit inside a code fence` 用例。⚠️ 本轮两处台账改动**都是往表里追加**，前面 `0e9d9b25` 那段的 `22 → 26` 与 `479 + 5 = 484`、以及 `8a24b5d0` 那段的 `26 → 29` 与 `484 + 3 = 487`，说的都是**那些窗口**的实测，按惯例**保留不覆写**，**那个窗口**的当前口径以 **491** 为准）。⚠️ **本轮（HEAD `19545e076`，Unicode 行终止符统一切分）另有一处**在册类的**声明例数随代码更新**：`GroupChatTest` **33 → 39**（**+6**：`19545e076` 改 **2** 条既有断言的期望值 + 新增 **6** 个用例方法），所以整表是 **`491 + 6 = 497`** ✅（⚠️ **类数仍是 45**：这一条是**既有类的新增 `@Test`**，不新增类；✅ 复算已实测：`--only ledger` 输出 `台账行数 45 行` / `声明合计 497 例` / `逐行核对 45 行全部相等`）。⚠️⚠️ **口径澄清——「\`491\` 会不会连带影响」这一步是实测判出来的，不是猜的**：**\`491\` / \`497\` 都不是「app 全模块单测总数」**，那个数是 **111 类 / 921 例**（口径 ① XML 与口径 ② 源码 `@Test` **两个口径一致**）。所以本表这一列**必须**跟着 `GroupChatTest` 一起动：`GroupChatTest` 是在册类，它的 `+6` 直接进这一列；而 **921 那一列在同一批里也 `+6`**（**915 → 921**，同样全部来自 `GroupChatTest`）。⇒ **两个口径同源但不同义，都指向同一批 6 条新用例**，**两列都要更新，不能只更新一列**。⚠️ 本轮两处台账改动**都是往表里追加**，前面 `0e9d9b25` 那段的 `22 → 26` 与 `479 + 5 = 484`、`8a24b5d0` 那段的 `26 → 29` 与 `484 + 3 = 487`、以及 `5186349f` 那段的 `29 → 33` 与 `487 + 4 = 491`，说的都是**那些窗口**的实测，按惯例**保留不覆写**，**那个窗口**的当前口径以 497 / HEAD `19545e076` 为准）。⚠️ **本轮（HEAD `ddda36186`，可见性穷举矩阵）新增 1 类 9 例 → 46 类 / 506 例**：`GroupViewerExhaustiveMatrixTest` **9**（新增类，无既有类扩写），所以整表是 **`497 + 9 = 506`** ✅。✅ 复算已实测：`python3 tools/verification/c1_doc_stats.py` 退出码 **0**，`台账行数 46 行` / `声明合计 506 例` / `逐行核对 46 行全部相等`。⚠️⚠️ **两个口径同源但不同义，本轮两个都变了**：app 全模块单测由 **111 类 / 921 例** 变为 **112 类 / 930 例**（`+1 类 / +9 例`，两个口径——XML `testsuite` 求和与源码 `@Test` 计数——**逐位一致**），台账这一列由 **497 → 506**（`+9`，同一个 `GroupViewerExhaustiveMatrixTest`）。⇒ **两列都要更新，不能只更新一列**。⚠️ **这一类不进「测试结果」段的任何一列的可信度判断**：它只测 `GroupChat.visibleMessages` / `GroupTurnCoordinator.viewerMessages` 两个**纯函数**，**零设备、零 `adb`、零模型输出、零 UI 渲染**——契约 `:206` 点名的「实际模型调用序列」「真机行为」「真机 viewer 可见消息 ID 台账」三类产物**一份都没有新增**，所以 C1 各用例行的状态**一律不变**（本轮**没有**任何一个 `unverified` 变成 `verified`）。⚠️ **本轮观察到但刻意未断言、也未修的一个口径缺口**：`role_id` **不是配置成员**的消息（例如 `role_id = "mallory"`）在**议长步**上会被 `chairRound` 分支放行（`GroupChat.kt:743` 只判 `role == ASSISTANT && index >= roundStart`，不看 `role_id` 是否是成员）。**它没有被排除进枚举空间，而是被排除在断言之外**——理由是它**在生产路径上不可达**（盖戳用 `SpeakerStep.role.id`，一定来自 `config.roles`）**且契约没有给出它该有的答案**（「放开本轮全员发言」是 `:179` 明确许可的行为）。**这是观察，不是扫出来的越权 bug，也没有改任何生产代码。**） |
| **`ConversationPageLoadParamsTest`** | **5** | **`ConversationRepository` 四个 offset 分页 API 的 `LoadParams` 类型缺陷的纯 JVM 证据**：反射真实调用 Room 2.8.5 `RoomPagingUtil.getOffset`，钉住 `Refresh` 会「刷新窗口夹取」而 `Append` 恒为 `key`（`refreshLoadParams_isClampedIntoLastWindow` / `appendLoadParams_preservesOffsetEvenPastClampBoundary` / `refreshAndAppend_agreeInsideWindowAndDivergePastIt`）+ 两条源码护栏（`repositorySource_usesAppendOnly` 要求 `LoadParams.Append(` 恰 3 处且不含 `LoadParams.Refresh`；`repositorySource_delegatesNextKeyToRoom` 要求 `nextOffset = result.nextKey` 恰 3 处）（`5ca9ecbaf`，`app/src/test/java/heizige/kk/khatkit/app/core/data/repository/ConversationPageLoadParamsTest.kt`，5 个 `@Test`，XML `tests="5"`） |
| **合计（本批，零设备，HEAD `5c362851a`）** | **514** | **48 类**（新增 **1 类 5 例**：`ConversationPageLoadParamsTest` 5；`git log --name-only 37630adb4..HEAD -- 'app/src/test/*'` 实测只有这一个新文件。⚠️ `c1_doc_stats.py` 改前实测 **47 行 / 509 例**、改后 **48 行 / 514 例**——本行声明的 `514` 由脚本 `declared sum` 裁判。⚠️ 表内既有的 `506`（差 5）与 `509` 两条 `合计` 行属历史声明，按惯例**保留不覆写**） |
| **`C1GroupQrPayloadCodecRoundTripTest`** | **5** | **C1-09③ 二维码载荷「字符串 ↔ 配置」往返**：`encodeQr -> importShare` 逐字段（`schema_version` / `mode=roundtable` / `chair_role_id=r3` / `token_budget_per_round=1234` / **roles 顺序**（刻意乱序 `r2,r1,r3`）/ chair 位 / 整体相等）、角色卡最小元数据往返（显式断言中间卡 `cardId==null` / `avatarRef==null` 仍是 null 而非字符串 `"null"`）、`decodeSharePayload` 逐字段、`decodeQr == decodeSharePayload().config`、二次往返 `encode->decode->encode` **字节相等**（`28c05f2db`，`app/src/test/java/heizige/kk/khatkit/app/core/data/model/C1GroupQrPayloadCodecRoundTripTest.kt`，5 个 `@Test`，XML `tests="5"`） |
| **`GroupExportShareIntentSourceGuardTest`** | **5** | **源码护栏：导出分享 `ACTION_SEND` 构造**——`shareFile`（`ConversationExport.kt:860-872`）/ `writeExportTempFile`（`:836-852`）/ `ExportHooks.exportAndShare`（`:42-63`）的 `ACTION_SEND` / `type = mimeType` / `EXTRA_STREAM` / `FLAG_GRANT_READ_URI_PERMISSION` 各恰一次 + `createChooser` + `chat_page_export_share_via`、`FileProvider.getUriForFile` 恰一次、authority 按包名派生恰一次；含**反空跑保护**（`28c05f2db`，`app/src/test/java/heizige/kk/khatkit/app/feature/chat/GroupExportShareIntentSourceGuardTest.kt`，5 个 `@Test`，XML `tests="5"`） |
| **合计（本批，真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`）** | **524** | **50 类**（新增 **2 类 10 例**：`C1GroupQrPayloadCodecRoundTripTest` 5 + `GroupExportShareIntentSourceGuardTest` 5；`git log --name-only ae08f2525..HEAD -- 'app/src/test/*'` 实测只有这两个新文件。⚠️ `c1_doc_stats.py` 改前实测 **48 行 / 514 例**、改后 **50 行 / 524 例**——本行声明的 `524` 由脚本 `declared sum` 裁判） |
| **`C1MemoryIsolationPredicateTest`** | **7** | **C1-08 串扰判据 `memoryIsolationViolations` 的离线判别力镜像**（空间键 / `role_id` / `source_message_id` / 无回退；变异输入 → 6 failed；本批 `2a0610705`） |
| **合计（本批，真机第十八轮，HEAD `2a0610705`）** | **531** | **51 类**（新增 **1 类 7 例**：`C1MemoryIsolationPredicateTest` 7；`git log --name-only e2bfce4ad..2a0610705 -- 'app/src/test/*'` 实测只有此一文件。⚠️ `c1_doc_stats.py` 改前实测 **50 行 / 524 例**、改后 **51 行 / 531 例**——本行声明的 `531` 由脚本 `declared sum` 裁判） |

⚠️ **有四个测试类里共 11 条断言是**源码文本护栏**而非行为测试，读表时要记这件事**（早前这里写的是「三个测试类里有两个」，与它自己列出的三个类对不上——三个类**全都**
读 `.kt` 源码文本；本轮把第四个类与它的 2 条也并进来，措辞一并改成可数的那一句）：
`ChatManagerNotificationSenderNameTest` 的第 5 条（1 条）、
`ConversationDrawerFolderScopeTest` 的 4 条、
`ChatServiceSenderNameGuardTest` 的 3 条，
以及 **`ConversationSearchLikePatternTest` 的 12 条里有 2 条是源码护栏**
（`daoSource_everySearchTextQueryCarriesEscapeClause` 逐行扫全部 5 条 `:searchText`
查询、`repositorySource_everyDaoSearchCallGoesThroughTheEscapeFunction` 扫 5 个转发点；
另 10 条是转义纯函数的行为测试）。原因是这条路径
（`handleMessageComplete` / DAO 的 `WHERE`）要么是 `private suspend` + 一堆 Hilt 协作者，
要么需要真实 SQLite，JVM 单测（仓库 `testImplementation` 只有 junit，没有 Robolectric
/ room-testing）构造不出来。**文本护栏能钉住「代码没被改回去」，钉不住「运行时行为
正确」**——后者仍需真机。

⚠️⚠️⚠️ **新窗口（HEAD `361c7cf6`）之后这个数是 24 条 / 六个类**（上一句的
「四个类 11 条」按惯例保留不覆写）：本轮又新增两个**纯源码文本护栏**类——
`GroupStaleJobCommitSourceGuardTest` **7 条**、`GroupStaleJobFailureSourceGuardTest`
**6 条**（`11 + 13 = 24` ✅）；`GroupTieBreakScaffoldingDropSourceGuardTest` 本来就
整类都是文本护栏，用例数 **3 → 7**，它的四条增量仍是文本护栏（所以**不**再加进 11 那个
数，它是另一个类）。⚠️ **为什么这三批里文本护栏这么多**：`commitGroupTurn` /
`failGroupTurn` 都是 `private suspend` + 一堆 Hilt 协作者（`getConversationFlow` /
`groupRunDAO` / `saveConversation`），JVM 单测**构造不出来**——所以「函数体内必须有
那处调用」「调用必须排在某个副作用之前」这类**顺序与存在性**不变量只能读源码文本。
⚠️ **同一批里能抽成纯函数的部分子代理确实抽了**：`GroupTurnCoordinator.
checkCommitAdmission` / `checkFailureAdmission` / `advance` 三个都是纯判定内核，
配了 **12 条真单测**（② 6 条 + ③ 6 条）——**所以「两层判据的逻辑」是有行为断言的，
只有「调用点在哪、排在哪」是文本护栏**。这个分工是范式内的诚实取舍，不是偷懒。

⚠️ 有测试结果和有 lint 报告的是**两批不同的 15 个模块**：`card-validator` 有测试
结果（4 类 77 例）但**没有 lint 报告**（见下）；`image-toolbox-dependency` 有 lint
报告（5 Warning）但**没有测试结果目录**。

### 统计口径复算脚本（`tools/verification/c1_doc_stats.py`）

**每个数字的独立来源**（⚠️ **一律不靠解析文档文本**；台账那一处是例外，因为表本身
是人工维护的，但它的**数值由独立来源裁判**）：

| 数字 | 来源 | 脚本口径名 |
|---|---|---|
| app 单测 类数/例数/failures/errors/skipped | `app/build/test-results/testDebugUnitTest/TEST-*.xml` 的 `testsuite` 属性 | `unit` |
| app 单测 类数/例数（**第二来源**） | `app/src/test/**/*.kt` 里 `@Test` **出现次数**（不是行数） | `unit` |
| C1 台账 类数/例数 + **逐行核对** | 本文台账表 × **上面第二来源的逐类计数** | `ledger` |
| lint error/warning/hint | `*/build/reports/lint-results-debug.xml` 的 `severity` 属性 | `lint` |
| 仪器 `@Test` 总数 | `app/src/androidTest/**/*.kt`，**区分 tracked / 含 untracked** | `androidtest` |
| 台账锚点区间 commit 数 | `git log --oneline d45ebd10~1..1b0e04a9` | `git` |
| 本文所有表格的列数一致性 | 全文逐块逐行切单元格（`\|` 感知） | `tables` |

⚠️ **lint 的严重度分布只能读 XML，不能读 SARIF 的 `level`**：实测 app 模块 590 条
结果里 **390 条 `level` 是空的**，按 `level` 统计会把 390 条算成「未知」。两个文件
的**总数一致**（590 = 590），所以总数可交叉验证、分布只信 XML——脚本两个都读并断言
总数相等。

⚠️ **台账锚点 `1b0e04a9` 的 `78` 是锚定值**：脚本**只读打印、永不写回**。本轮实测
`git log --oneline d45ebd10~1..1b0e04a9 | wc -l` = **78**、`--merges` = **0**，
与本文既有记载**一致，未被改动**。

⚠️⚠️⚠️ **「XML 口径不可用」那条告警本轮已不成立——如实订正，别再照抄上一轮的说法**：
上一轮交接时口头确认过「`app/build/test-results/testDebugUnitTest/` 里只剩 1 个 XML，
`unit` 报 `FAIL 单测 XML 类数/例数 1 类 / 5 例`」，**本轮实测该状态已经不存在**：

```
ls app/build/test-results/testDebugUnitTest/TEST-*.xml | wc -l   # 110
```

`python3 tools/verification/c1_doc_stats.py`（**全量，不加 `--only`**）本轮实测
`unit` 四条全 **OK**：`单测 XML 类数/例数 110 类 / 881 例`、
`failures/errors/skipped 0 / 0 / 0`、`源码 @Test 110 个 / 881 例`、
`交叉验证 XML×源码 XML 110 / 源码 110（差 0，容差 5）✅ 一致`。

⚠️ **按惯例追加、不覆写上面那个 `881`**：`881` 是 `cae27138`/`1052cd25` 当时的实测值（`git grep -c @Test` 逐文件复算那两点的 `app/src/test` 确为 **881**，类数 **110**），历史记录按窗口保留。**但它已经不是当前 HEAD 的数**：⚠️⚠️ **`5186349f`（`parseBallot` 跳过 markdown 围栏）给 `GroupChatTest` 净增 4 条 `@Test`**，所以 HEAD `74d82476` 实测是 **110 类 / 893 例**（XML 与源码两个口径都是 893，`git grep` 逐点复算一致；`failure/errors/skipped` 仍 **0 / 0 / 0**）。**这 12 条差额全部来自 `GroupChatTest`**（`29 → 33`），与 wire 模型名那批无关——那批只碰了 `ai/` 与 `androidTest/`，**没有一行改 `app/src/test`**。

⚠️ **按惯例追加、不覆写上面那个 `893`**：`893` 是 HEAD `74d82476` 当时的实测值，历史按窗口保留。**当前 HEAD `19545e076` 实测是 `111 类 / 921 例`**（`111 类 / 921 例` / `failures/errors/skipped 0 / 0 / 0`；⚠️ **类数从 110 涨到 111**），两个口径一致：

| 口径 | 命令 | 实测 |
|---|---|---:|
| ① XML | `ls app/build/test-results/testDebugUnitTest/TEST-*.xml \| wc -l` | **111** |
| ① XML 例数 | 逐个 `testsuite` 的 `tests` 属性求和 | **921** |
| ② 源码 `@Test` | `app/src/test/**/*.kt` 里数 `@Test` **出现次数** | **111 个文件 / 921 例** |

⚠️⚠️ **`915 → 921` 的 `+6` 全部来自 `GroupChatTest`（`33 → 39`），这一条是本轮实测的**：`git archive be7952a83~1 app/src/test` 逐文件数 `@Test`，**批前实测 `111 类 / 915 例`**（`GroupChatTest` 33），批后 **111 类 / 921 例**（`GroupChatTest` 39）——**类数 `111` 一个没动**（这两个 commit **没有新增测试文件**，只扩写既有的 `GroupChatTest.kt`）。⚠️ **与 wire 模型名那批无关**：那批只碰 `ai/` 与 `androidTest/`。
⚠️ **XML 是批后真跑出来的、不是陈旧残留**（这是本条敢直接引用的理由）：111 个 XML 的 mtime **全部落在 `2026-10-06 13:03:18` 这一个窗口内**（`.614`–`.618`），而两个 commit 的提交时间是 `be7952a83` **12:58:32** / `19545e076` **13:01:14** ⇒ **XML 晚于提交约 2 分钟**，且**同一时刻写出 111 个文件**（不是 `--tests` 过滤跑留下的子集）。`TEST-…GroupChatTest.xml` 自身 `tests="39" skipped="0" failures="0" errors="0"`，与源码口径 39 **相等**。
⚠️⚠️ **口径与台账那一列的关系**（别把两个数混着用）：**921 是 app 全模块单测总数**（111 个类），**497 是「本文台账 45 个在册类的声明例数之和」**。本轮 `GroupChatTest` `+6` 同时进了这两列（`491 → 497` 与 `915 → 921`），**同源但不同义**；台账那一列只统计在册类，`CoilImageLoaderSourceGuardTest` 之类不进去。

⚠️ **按惯例追加、不覆写上面那个 `921`**：`921` / `111 类` 是 HEAD `19545e076` 当时的实测值，历史按窗口保留。**当前 HEAD `ddda36186` 实测是 `112 类 / 930 例`**（`112 类 / 930 例` / `failures/errors/skipped 0 / 0 / 0`），两个口径**逐位一致**；`+1 类 / +9 例` 全部来自新增的 `GroupViewerExhaustiveMatrixTest`（9 个 `@Test`，**没有改动任何既有测试类**）。台账那一列同期由 **497 → 506**（`45 类 → 46 类`）——**同源但不同义，两列都要动**，见台账表那个 `合计` 行。
⚠️ **本轮 `:app:lintDebug` 退出码 `0`，app 报告 `error 0 / warning 584 / hint 6`（合计 590）**，与上一窗口**逐位相同**（新增的是 `app/src/test` 下一个纯 JVM 测试文件，**不进 lint 口径**）。
⚠️ **110 / 881 这个数现在有两个口径可用**（不是「只有源码口径」）——上面「统计口径」表里
口径 1（XML）与口径 2（源码 `@Test`）**同时成立且互为佐证**。
⚠️ **本轮没有为了让它变绿去删 XML、也没有改脚本的判据**，更**没有再跑 gradle**——
理由是**现有 XML 已经比一次重跑更硬**：本轮逐类比对过，**XML 的类集合与源码含 `@Test`
的类集合完全相同**（只在 XML 里 = `[]`，只在源码里 = `[]`，各 110 个），
**110 个类逐个的 `tests` 属性与源码 `@Test` 计数无一不等**（不等清单 = `[]`），
两个合计都是 **881**。一次不带过滤的重跑只能复现这个结论，**不会产生更强的新证据**。
⚠️ **110 个 XML 的 mtime 全部是 `2026-10-06 06:02`**，而 `4ee1ad5c` / `8111d178` /
`72a5e548` 的**提交**时间是 `06:06`——时间上看 XML 早于提交，但**测试文件在提交之前
就已经在工作树里**（先写文件、跑测试、再提交），所以这个 XML 确实覆盖了那 17 条新增用例
（`SummaryViewerScopeTest` 12 + `SummaryViewerScopeWiringSourceGuardTest` 5），
**逐类比对已经证实了这一点**，不是靠时间戳猜的。
✅ **脚本那条判据本轮核实是对的，保留不动**：`c1_doc_stats.py:303` 的
`stale = xml_classes < 20` 判的是**文件数**（不是例数），而 `--self-test` 里有一条
专门喂「只含 1 个 XML 的目录」并断言它**确实报红**（`:602`）。**只有 1 个 XML 时报红是正确
行为**，把阈值调大才会造出下一个会骗人的工具。
⚠️ **上面「反空跑自检」第 2 条里那个「实测就撞上：只剩 1 个 XML」按惯例保留不覆写**——
它是**脚本为什么要有这条下限**的历史证据（`:896-898`），与「本轮 XML 已是全量 110 个」
两件事不冲突：**下限留着，现状已经达标**。

#### ⚠️ 反空跑自检（本脚本存在的核心理由）

本流水线在源码护栏上**连续踩过两次**同一个坑：① 一处 `content` 尾随 lambda 的大括号
写在右括号**外**，整段源码扫描被**静默跳过**；② 一条负向后行断言漏掉全限定名，同样
静默跳过。两次的共同特征都是**「扫到 0 条」被当成了「没问题」**。所以脚本规定：

1. **非零下限**：0 个文件 / 0 行 / 0 条 issue 一律 `FAIL`。
2. **可疑下限**：非零但偏少也 `FAIL`。⚠️ 实测就撞上：`test-results` 目录只剩
   **1 个** XML，若只拦 0 就会静默输出「1 类 / 5 例」而不报红——**那就是下一个
   会骗人的工具**。
3. **交叉验证**：XML × 源码、lint XML × SARIF，不一致即 `FAIL`。
4. **`--self-test`**：把每个探测器喂上**人造坏输入**（空目录 / 只含 1 个 XML 的目录 /
   0 行的表 / 带裸 `|` 的表 / 0 条 issue 的 lint），断言它们**确实报红**——
   让「探测器是活的」成为可验证事实而非声明。

```bash
python3 tools/verification/c1_doc_stats.py                 # 全部口径
python3 tools/verification/c1_doc_stats.py --only ledger    # 只算台账
python3 tools/verification/c1_doc_stats.py --only tables    # 只算表格列数（Task C2）
python3 tools/verification/c1_doc_stats.py --self-test      # 只跑探测器自检
python3 tools/verification/c1_doc_stats.py --check-doc      # 额外对账文档里声称的数字
```

退出码：`0` = 全通过；`1` = 有 `FAIL`（含自检失败）；`2` = 参数错误。
⚠️ **一条口径都没产出时（`--only` 全部落空）拒绝报告「通过」**，直接返回 1。
⚠️ **`--check-doc` 本轮实测输出**：本文共出现 **15 种**不同的「N 类 / M 例」写法、
**52 处**（其中 `105 类 / 808 例` 独占 21 处，且**已过期**——那是第六个窗口的值，
本轮实算是 `107 类 / 855 例`）。**这 52 处就是「手抄数字返工五次」的量化形态**：
一个数字在正文里散落二十几遍，任何一次更新漏改一处就产生一处自相矛盾。

### lint

**统计口径**：用脚本遍历全仓 `*/build/reports/lint-results-*.xml`（共 **15** 个
文件，对应 15 个有 lint 报告的模块），逐个 `<issue>` 元素数它的 `severity` 属性。
以本次实测为准：

| 范围 | Error | Warning | Hint | 合计 |
|---|---:|---:|---:|---:|
| **app 单模块**（`app/build/reports/lint-results-debug.xml`） | **0** | **587** | **6** | **593** |
| **15 模块聚合** | **0** | **620** | **7** | **627** |

⚠️⚠️ **上表那两个数已被本轮实测订正，当前值是 app 584W+6H = 590、15 模块 617W+7H =
624**（上表按惯例保留不覆写，它们是 2026-10-05 复核窗口的值）。本轮同样是
**逐个 `*/build/reports/lint-results-*.xml` 数 `<issue severity>` 属性**，
实测（`glob` 命中 **15 个**报告文件）：

| 范围 | Error | Warning | Hint | 合计 |
|---|---:|---:|---:|---:|
| **app 单模块**（本轮实测） | **0** | **584** | **6** | **590** |
| **15 模块聚合**（本轮实测） | **0** | **617** | **7** | **624** |

逐模块实测：app **584W+6H**、khatkit 17W、image-toolbox-dependency 5W、ai 2W、
workspace 2W、common 1W、oauth 1W、speech 1W、khatkit-ui 4W+1H；
document / highlight / material3 / mediapicker / search / web 六个模块 0 条。
即 app 之外 Warning **33** 条、Hint **1** 条：584+33 = **617**、6+1 = **7**，
与上表那句「app 之外 14 个模块逐个复算，Warning 33、Hint 1，一个数都没动」**仍然一致**——
**差值 -3 全部来自 `app` 单模块**（593 → 590）。
⚠️ **本轮没有跑 `./gradlew lint`**（纯文档任务，按要求不跑 gradle），这两个数是
**对磁盘上现有报告文件的逐条计数**，不是新跑出来的报告；报告本身是哪一次 lint 生成的
**无法从 XML 里读出**，所以只能记「当前磁盘值」。
⚠️ 上一轮记的「`app/build/reports/lint-results-debug.xml`（739,750 字节）」那个字节数
**本轮未复核**，别拿它跟现在的文件大小对账。

⚠️ **C1-S 这一批（`215296f1..6982869b`）之后，lint 数字与基线逐位相同**：
`./gradlew --offline lint` 退出码 **0**，app 单模块仍是 **587 Warning + 6 Hint =
593 条、0 Error**，`app/build/reports/lint-results-debug.xml`（739,750 字节）实测
`<issue severity>` 计数即 587/6。**本轮碰过的四个文件逐个点算全部 0 命中**：
`core/data/db/dao/ConversationDAO.kt`、`core/data/repository/ConversationRepository.kt`、
`feature/chat/ChatDrawerViewModel.kt`、
**新增的 `core/data/repository/ConversationSearchLikePattern.kt`**。
**上表那两个数一个都没改**（它们是 2026-10-05 复核窗口的实测值），「与基线逐位相同」
本身就是本轮要登记的事实：加了 5 处 `ESCAPE`、14 处 `ORDER BY` 与一个 72 行的新文件，
**没有引入任何新命中**。
⚠️ **零命中 ≠ 零问题**（这一条对 C1-S 尤其要紧）：它只说明这四个文件在 Android Lint 的
既定规则集下没有命中，**完全不覆盖**「这个 `ESCAPE` 子句在真机 Android 捆绑的 SQLite
上语义是否一致」——lint 是静态规则，跑的是 lint 自己的分析器，不是 SQLite。见「C1-S」
那节「这一节仍然不能证明什么」。

⚠️ 上表两个数是 **2026-10-05 复核窗口**的实测值；前一窗口记的是 app 590W+6H=596、
聚合 623W+7H=630，那两个数**按各自窗口原样保留在上面这段历史里**，别拿 `app` 的差值
去改聚合（`app` 之外 14 个模块本轮逐个复算，Warning 33、Hint 1，一个数都没动：
590+33=623 → **587+33=620**，6+1=7 不变）。差值 **-3** 全部来自 `app` 单模块的
`FrequentlyChangingValue`（见下面「C1 内核 18 个文件」）。

⚠️⚠️ **新窗口（HEAD `361c7cf6`）：lint 数字与上一窗口逐位相同，本轮零改动。**
**0 error**；app 单模块仍是 **584 Warning + 6 Hint = 590**、15 模块聚合仍是
**617 Warning + 7 Hint = 624**（`glob('*/build/reports/lint-results-*.xml')` 命中
**15 个**报告文件，逐个 `<issue severity>` 属性计数实测）。
⚠️ **本轮同样没有跑 `./gradlew lint`**（纯文档任务，按要求不跑 gradle），这两个数是
**对磁盘上现有报告文件的逐条计数**，不是新跑出来的报告。
⚠️ **本轮碰过的文件逐个点算全部 0 命中**（按 `location/@file` 绝对路径逐文件数）：
`feature/chat/ChatManager.kt` **0**、`feature/chat/GroupTurnCoordinator.kt` **0**、
两个新增护栏测试文件 `feature/chat/GroupStaleJobCommitSourceGuardTest.kt` **0** /
`feature/chat/GroupStaleJobFailureSourceGuardTest.kt` **0**，
以及一并改过的 `feature/chat/GroupTieBreakScaffoldingDropSourceGuardTest.kt` **0**。
⚠️ **零命中 ≠ 零问题**：这五个文件本轮新增了 4 个纯函数
（`checkCommitAdmission` / `checkFailureAdmission` / `Advance.Halted` / `advance` 的终态
守卫），**Android Lint 一条都没命中不等于它们的行为被验证过**——它们的证据是
12 条 JVM 真单测 + 13 条源码文本护栏 + 13 次变异检验，见「平票脚手架三处漏回收 +
残留旧 job 两道守卫（零设备）」。

15 个模块逐个（`<issue severity>` 计数实测）：app 587W+6H、khatkit 17W、
image-toolbox-dependency 5W、ai 2W、workspace 2W、common 1W、oauth 1W、speech 1W、
khatkit-ui 4W+1H；document / highlight / material3 / mediapicker / search / web
六个模块 0 条。即 app 之外 Warning 33 条、Hint 1 条，587+33=620、6+1=7。

即验证代理报的「app 模块 0 error / 52 warning / 6 hint」与「593 issues = 587
Warning + 6 Hint / 0 Error」互相矛盾，**后者与本文一致，前者的 52 无法复现**：
app 报告里 `severity` 属性只出现过 `Warning` 和 `Hint` 两种值，distinct `id` 是 28
个、distinct `file` 是 61 个，都不是 52。本文采信 587+6=593（app）与 620+7=627
（15 模块聚合）这两个可复算的数字。**全仓 0 Error。**

⚠️ **`card-validator` 模块没有 lint 报告**：`card-validator/build/reports/` 目录
存在但里面只有 `tests/`，全模块 `find` 不到任何 `lint-results*` 文件。这是
**「没跑」不是「0 命中」**，不要在上表里给它记 0。

**C1 内核 18 个文件的 lint 命中**（按 `location/@file` 绝对路径前缀
`app/src/main/java/heizige/kk/khatkit/app/` 匹配，逐文件点算）：

- ⭐ **18 个文件全部零命中，合计 0 条**（2026-10-05 复核实测，逐个文件数过）。
  上一窗口记的是「`feature/chat/ChatList.kt` **3 条** `FrequentlyChangingValue`
  （行 `584:32` / `587:36` / `590:41`）+ 其余 17 个文件零命中」，**那 3 条现在是 0**，
  合计从 3 降到 0。
- **这 3 条是怎么没的（`366b3fe8`，真实性能改进，不是消 warning）**：
  `layoutInfo` / `firstVisibleItemIndex` / `firstVisibleItemScrollOffset` 三个读数
  都带 `@FrequentlyChangingValue`。原代码在**组合期**（`ChatListNormal` 的 composable
  体内）直读它们算 `liveTotal` / `liveVisible` / `liveProgress`，后果是**每滚一帧整个
  `ChatListNormal` 重组一次**——滚动条的几何量被提升成了列表级状态。
  改法是「**组合期求值 → draw 期求值**」：三份读数收成一个不可变快照
  `ScrollbarMetrics`（`internal data class`），由
  `remember(state) { derivedStateOf { state.scrollbarMetrics() } }` 缓存，只在两个
  **非组合**作用域读——`LaunchedEffect(loading)` 体与 `Canvas` 的 `draw` lambda。
  于是滚动只重绘那一个 Canvas，不再重组列表。相等判定也才有意义：三个 Int/Float 组成
  值对象后，滚动期间只在真变化时产生新实例，不会因为「每帧重算了一遍同样的数」把下游
  叫醒。`derivedStateOf` 顺带把 `FrequentlyChangingValue` 的三个命中一起消掉了 ——
  **warning 消失是结果，不是目的**。
- **`resolveScrollbarProgress` 的算法逐字未变**（纯索引比例，首项按像素高度折算成
  0f..1f 偏移分数，端点由 `canScrollForward` / `canScrollBackward` 强制贴边），只是被
  抽成纯函数以便在 JVM 里钉住（`31e0a39d` 加 `ChatListScrollbarTest` 7 条）。
  「端点贴边」这半边尤其不能靠肉眼：它决定滑块画在哪，`ChatListScrollbarTest` 把
  `total == 1`（无滚动时 progress = 0f）、已到底（= 1f）、已到顶（= 0f）三个端点
  逐条断言住。
- 18 个零命中文件（实测逐个点名）：`ChatList.kt`、`GroupChatPage.kt`、
  `GroupSpeakerResolver.kt`、`GroupRoleCompletionProvider.kt`、`GroupMemberBar.kt`、
  `GroupExportCard.kt`、`GroupRoleCards.kt`、`GroupTurnModel.kt`、`ChatPage.kt`、
  `ChatManager.kt`、`core/data/model/GroupChat.kt`、
  `core/data/ai/tavern/TavernChatCodec.kt`、`core/data/repository/MemoryRepository.kt`、
  `core/data/repository/MemoryExtractor.kt`、`core/data/db/dao/ConversationDAO.kt`、
  `core/data/db/dao/GroupRunDAO.kt`、`core/ui/components/message/ChatMessage.kt`、
  `core/ui/components/message/ChatMessageActions.kt`。
- **零命中 ≠ 零问题**：它只说明这 18 个文件在 Android Lint 的既定规则集下没有命中，
  不说明行为正确、不说明 Compose 重组行为已实测、不覆盖 KMP / iOS 侧。
  `ChatList.kt` 那一处尤其要这么读——**真机上滚动是否真的只重绘不重组，本轮零证据**，
  依据是 Compose 的求值语义与 lint 规则，不是帧率实测。
- 上面那条 `584:32` / `587:36` / `590:41` 的历史行号与归属（`git blame` → `d2be1c3fe`，
  2026-09-13，早于 C1 基线 `d45ebd10` 三个星期，**不是 C1 引入**）按各窗口原样保留，
  仅作「这 3 条曾经存在过」的证据。
- ⚠️⚠️ **第六个窗口（HEAD `a74c1820`）复算：与上一窗口逐位相同，一个数都没变。**
  口径同前：遍历全仓 `*/build/reports/lint-results-debug.xml`（**15 个文件**）逐个
  `<issue>` 数 `severity`。实测：**app = `584` Warning + `6` Hint = `590` 条、0 Error**；
  **15 模块聚合 = `617` Warning + `7` Hint = `624` 条、0 Error**（app 之外 14 个模块
  逐个复算：`ai` 2W / `common` 1W / `document` 0 / `highlight` 0 /
  `image-toolbox-dependency` 5W / `khatkit` 17W / `khatkit-ui` 4W+1H / `material3` 0 /
  `mediapicker` 0 / `oauth` 1W / `search` 0 / `speech` 1W / `web` 0 / `workspace` 2W）。
  ⚠️ **本轮同样没有跑 `./gradlew lint`**（纯文档任务），数的是磁盘上现有报告文件，
  **报告是哪一次 lint 生成的无法从 XML 里读出**——这一句照旧要跟着这两个数一起读。
  ⚠️ **`card-validator` 仍然没有任何 lint 报告**（只有 `tests/`），所以「15 个模块」这个
  口径指的是**有报告的 15 个**，不是仓库里的全部模块（见「已知遗留与风险」第 6 条）。
  ⚠️ 本轮碰过的文件（`GroupChatPage.kt` / `RouteActivity.kt` / `KhatKitApp.kt` /
  `ChatManager.kt` / `ChatService.kt` / `AppHiltModule.kt` + 7 个新增/扩写的测试文件）
  **命中数逐个点算全部为 0**。

⚠️⚠️ **真机第十七轮 / C1-09 收尾（2026-10-07，HEAD `e2bfce4ad`）订正：app 单模块 lint 由旧基线
`581W + 6H = 587` 涨到 `617W + 6H = 623`（`+36 warning`），全模块由 `617W + 7H = 624`
涨到 `650W + 7H = 657`。** 本批**没有跑 `./gradlew lint`**（纯文档窗口），数字取自
`c1_doc_stats.py` 的 `lint` 口径对磁盘上现有报告的逐条计数（`<issue severity>` 属性）：

```
app     error 0 / warning 617 / hint 6（合计 623）
全模块   error 0 / warning 650 / hint 7（合计 657）
```

⚠️ **+36 全部是 `gradle/libs.versions.toml` 的依赖版本检查**（`NewerVersionAvailable` 29 +
`AndroidGradlePluginVersion` 4 + `GradleDependency` 3），**属环境 / 联网相关**（依赖仓库
可见性变化触发），**不是 C1 代码引入**。⚠️ **本批改动的两个文件 lint 命中 0 条**
（`docs/eval/c1-group-chat.md` / `docs/beyond-operit-implementation-status.md`
都是文档，不进 lint 口径）。⇒ **不许把 587 写成 623，也不许写「lint 未变」**。
详见「已知遗留与风险」第 54 条。

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

⚠️ **C1-S 这一批之后的三个 APK 字节数**（`ls -l app/build/outputs/apk/debug/` 实测，
mtime `2026-10-05 09:53`）：

| 文件 | 字节 |
|---|---:|
| `app-arm64-v8a-debug.apk` | 91,666,774 |
| `app-x86_64-debug.apk` | 92,427,035 |
| `app-universal-debug.apk` | 108,473,937 |

⚠️ **上表的字节数与 SHA-256 那一块按各自窗口原样保留，本轮没有覆写、也不拿它做对比。**
**不要用 SHA-256 做 APK 的可复现性对比**：构建不可复现，zip 内的条目时间戳每次构建都
变，同一份源码两次 `assembleDebug` 出来的 APK 字节级也会不同。上面那三行字节数**只是
本轮实测的体积记录**，**不是**「变大了多少 / 变小了多少」的证据——和上一窗口那三个数字
（107,448,104 / 91,401,202 / 90,640,941）**不可比**。真要比，只能比 `output-metadata.json`
里的 `versionCode` / `versionName` / `minSdkVersionForDexing` 这类语义字段。

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

### C1-P 第二生成入口的通知标题护栏（零设备）

登记于 `88ba63c2` / `8bc28105` / `c940813b`。**这一节不产生任何契约 `:206` 意义上的
验收证据，十例状态仍全 `unverified`。**

#### 定时炸弹长什么样

仓库里有**两个** `handleMessageComplete` 都发
`AppEvent.ChatGenerationEnded(conversationId, senderName, null)`，而该事件的
`senderName` 会被后台通知当**标题**显示（`ChatNotificationManager.kt:121`
`title = senderName`）：

| 入口 | 文件 | 群聊路径 | `senderName` 怎么求 |
|---|---|---|---|
| 主入口 | `feature/chat/ChatManager.kt` | 有（`takeGroupTurn`，`:748`） | 会话级初值 + 群聊分支**重算**（`ed21db6e`） |
| 次入口 | `core/service/ChatService.kt` | **无**（`grep -c groupConfig` = 0） | 只求一次 |

次入口原先（`ChatService.kt:624` 附近）**逐字抄了第二份**内联
`if (assistant.useAssistantAvatar) …`。同一个公式两份实现、两份各自漂移——已经发生过
的漂移就是 B4（通知标题显示会话级模型），`ed21db6e` 在主入口修掉了，但**不会自动覆盖
次入口那份副本**。当下无害只因为次入口没有群聊路径；一旦有人把群聊能力搬进 `ChatService`
（或给它加一个群聊分支），`assistant` / `model` 会在群聊分支里被换成**本轮发言角色**
那一套，而在这之前求出的 `senderName` 不会跟着重算——**同一个 bug 在第二个入口复现，
且没有任何编译期信号**。

#### 改法：优先抽共享纯函数，不靠「断言重复还在」

`resolveNotificationSenderName` 已经是 `internal` 纯函数，而
`ChatManager.kt`（`feature.chat` 包）与 `ChatService.kt`（`core.service` 包）**同属
`:app` 这一个 Gradle 模块**，所以 `internal` 跨包可见、无需改可见性或移动文件。
次入口因此直接改成调它（`88ba63c2`，`+13 / -5`），**公式全仓只剩一份实现**。
理由与任务口径一致：「测试断言重复还在」不如「根本不允许重复」——前者只保证有人会
记得更新断言，后者让第二份实现无法存在。

⚠️ **但抽共享函数本身不足以拆掉这颗炸弹**：如果有人后来给 `ChatService` 加群聊分支，
他照样会重算 `assistant` / `model` 而忘了重算已求出的 `senderName`——抽函数拦不住
这个方向。所以护栏分两层（`ChatServiceSenderNameGuardTest`，3 条）：

1. `senderNameFormula_isImplementedExactlyOnce_inAllMainSources` —— **结构层**：
   遍历全仓所有 `src/main/**/*.kt`（约 1476 个文件），断言「按 `useAssistantAvatar`
   分支」这个形状**只在 `ChatManager.kt` 一处出现**。任何地方再抄一份内联公式
   （包括抄回 `ChatService`）立刻红。
2. `bothGenerationEntryPoints_consumeTheSharedFunction` —— 两个入口都必须调
   `resolveNotificationSenderName(`，不许自己求值。
3. `chatService_gainsGroupPath_onlyIfItRecomputesSenderName` —— **触发器**：一旦
   `ChatService.kt` 里出现任一群聊上下文标记（`groupConfig` / `takeGroupTurn` /
   `GroupTurnEntry` / `SpeakerStep` / `resolveGroupTurnModelId` / `groupStep`），
   就要求 `senderName` 改成 `var` 且**至少被重算两次**（初值 + 群聊分支），且重算位置
   在群聊标记之后。

第 3 条的形状是刻意选的：它拦的是「搬了群聊却忘了重算」这个**具体错法**，
而不是「不许搬群聊」。一次**正确**的搬运（补上重算）能过，一次漏了重算的搬运必然被拦。
今天 `ChatService` 里没有任何群聊标记，所以这个分支空转——它的牙齿靠下面第 2 节的
变异检验证明，不靠「今天就已经在拦」。

⚠️ **护栏本身踩过的两个坑，记在这里免得重犯**：
- 匹配必须**先剥注释**。抽函数那笔 commit 自己在 `ChatService.kt` 留了注释复述旧写法
  `if (assistant.useAssistantAvatar) …`，没剥注释时护栏把它当成真命中、当场变红。
  逼着人删解释文的护栏会被绕着走，所以现在 `code()` 统一剥 `/* */` 与 `//`。
- 第 3 条最初把「现状锚点」断言放在「重算义务」之前，于是现状断言先红、**重算那条
  永远执行不到**（死代码）。已改成重算义务先判；顺带发现「标记之后存在一次赋值」能被
  **初值本身**满足（群聊标记排在初值之前时），所以补了「至少两次赋值」这一条。

#### ⭐ 变异检验有牙齿的证据（3 次变异，全部 FAIL 且退出码 1）

还原方式：**文件备份 + `sha256sum -c` 校验**，**没有用任何 git 命令还原**（守约束）。
备份 `/tmp/opencode/mut/ChatService.kt.bak` 与工作副本
`db6449b39cb57ff244379247f2f6a73cf65f3e1c095a6701b80880f6c0b295d6` 一致；每轮变异
后 `cp` 回备份并 `sha256sum -c` 输出「成功」，`git status --porcelain` 确认
`ChatService.kt` 已无改动。仓库无 `allWarningsAsErrors`，所以三次变异都是**编译通过、
测试变红**（不是编译失败冒充的失败）。

| # | 变异内容 | 结果 | 退出码 | 失败用例 | 命中的断言 |
|---|---|---|---:|---|---|
| 变异 1 | 给 `handleMessageComplete` 注入一个**能编译**的群聊分支（`val groupConfig = initialConversation.groupConfig` + `if (groupConfig != null) { Logging… }`），**不动** `senderName` | **FAIL** | **1** | `chatService_gainsGroupPath_onlyIfItRecomputesSenderName` | 「不该有群聊上下文…命中 `[groupConfig]`」 |
| 变异 2 | 变异 1 之上再把 `val senderName` 改成 `var senderName`（模拟「我知道要重算」但仍**没写**重算） | **FAIL** | **1** | 同上 | 「有群聊上下文就必须至少重算一次…**现在 1 处**」 |
| 变异 3 | 还原群聊标记，改把共享函数调用**换回内联公式副本**（即「有人把公式抄回别处」/ 出现第 3 个计算点） | **FAIL**（2 条同时红） | **1** | `senderNameFormula_isImplementedExactlyOnce_inAllMainSources` + `bothGenerationEntryPoints_consumeTheSharedFunction` | 「只允许在 `ChatManager.kt` 一处实现…实际命中 `[…ChatService.kt, …ChatManager.kt]`」+「必须调用共享纯函数」 |

变异 2 是本轮最有价值的一次：它证明白天那版护栏**确实漏了**这个错法（重算分支被现状
断言短路成死代码、且单次赋值能满足位置检查），是护栏自己把缺口暴露出来后补的。
变异 1 与 2 的差别只有 `val` → `var` 一个词，却从「被现状断言拦下」变成「被重算义务
精确指出 `现在 1 处`」——这说明第 3 条的两半断言各司其职，不是冗余。

#### 三条命令的真实退出码（本轮）

`:app:testDebugUnitTest --rerun` **0**（**91 类 / 710 例 / 0 失败 / 0 错误 / 0 跳过**）/
`:app:assembleDebug` **0** / `lint` **0**（app 单模块 **593 条 issue、0 Error**，
**587 Warning + 6 Hint**；15 模块聚合 620W+7H = 627，`FrequentlyChangingValue` 全仓
**0**）。本轮**没有新增仪器测试**，`Migration_30_31_Test` 7 条、
`Migration_31_32_Test` 6 条、`GroupRunDAOTest` 12 条仍全部未跑设备。

#### 这一节仍然不能证明什么

1. **真机通知标题零证据。** 护栏钉的是「公式只有一份」「搬运时必须重算」，钉不住
   「通知栏里真的显示对了」。`ChatManager` / `ChatService` 两条
   `handleMessageComplete` 都是 `private suspend` + Hilt 协作者，JVM 单测构造不出来。
2. **护栏是源码文本护栏，会被绕过。** 改文件名、改写法（把公式塞进
   `when` 而不是 `if`）、或把判定挪进另一个模块，都可能让正则失配。它防的是
   「顺手抄一份」这个最可能的错法，不是「证明不存在第二份实现」。
3. **`ChatService` 到底该不该留一个生成入口，本轮没有结论。** 只消掉了重复公式，
   没有评估两个入口是否该合并——那是未授权的架构变更。
4. **`FrequentlyChangingValue` 3 → 0 与本节护栏无关**，是两件事（滚动条，
   `366b3fe8`），只是同一批提交里做的，见「lint」段。

### C1-S 抽屉 type 筛选主机侧重放与两个真实缺陷修复（零设备）

登记于 commit `215296f1..6982869b` 这 8 个 commit（逐条列在「锚点之后的后续提交」）。
**这一节不改变任何用例的判定**：契约 `:206` 点名的四类产物——**viewer 可见消息 ID /
实际模型调用序列 / prompt+completion token / 真机行为**——**本轮一份都没产出**
（`adb devices` 仍为空输出）。**C1-10 仍是 `unverified`。** 它补的是「抽屉那两条
`@Query` 在真实 SQLite 上真的只按 `type` 收窄」这一条执行证据，外加**修掉两个真实
缺陷**。

#### ⚠️⚠️ 敏感项：本轮改了既有测试的一行断言（先读这一段）

**`ConversationTypeFilterSourceGuardTest.kt` 第 68-73 行那条断言的期望串**，从

```kotlin
"title LIKE '%' || :searchText || '%'\" + CONVERSATION_TYPE_PREDICATE_SQL"
```

改成

```kotlin
"title LIKE '%' || :searchText || '%'\" + CONVERSATION_LIKE_ESCAPE_SQL + CONVERSATION_TYPE_PREDICATE_SQL"
```

**为什么必须改**：SQLite 文法要求 `ESCAPE` 子句**必须紧跟 LIKE 的右操作数之后**，
而缺陷 1 的修法就是给这 5 条搜索查询加 `ESCAPE` 子句。所以 DAO 源码里那条未折叠的拼接
变成了 `LIKE 片段 + CONVERSATION_LIKE_ESCAPE_SQL + type 谓词常量` 三段。而这条断言
原本是**逐字节相邻**地表达「type 谓词不允许出现第二份副本」的意图——ESCAPE 只能插在
LIKE 片段与 type 谓词之间，两者必然被它隔开，所以期望串**必须**跟着改。
**断言的结构合法地变了，意图没变。**

**改完还有没有牙齿：有，且比原来更严。** 变异检验（基线 92 类 / 722 例全绿）：

| 变异 | 改法 | 抓它的护栏 | 结果 |
|---|---|---|---|
| M1 | 在 DAO 里塞第二份 type 谓词字面量 | **本护栏第 1 条** | FAIL「type 谓词不允许出现第二份字面量 expected:<1> but was:<2>」 |
| M2 | 抽掉搜索路的 `ESCAPE` 子句 | **本护栏第 3 条**（新期望串生效） | FAIL「搜索路查询应保留关键字模糊匹配并接上共用谓词」 |
| M4 | 漏掉 `\|\| '%'` 包夹 | 本护栏 + 转义护栏 + folder scope 护栏 | FAIL 3 处 |
| **M3** | **把转义字符 `~` 改成 `%`** | ⚠️ **本护栏原理上抓不到，判绿**（见下） | 本护栏**未报红** |

**判分归属必须如实写**：M3 改的是 `ConversationSearchLikePattern.kt`
（`CONVERSATION_LIKE_ESCAPE_SQL` 那个常量），而 `ConversationTypeFilterSourceGuardTest`
**只读 `ConversationDAO.kt` 与 `ChatDrawerViewModel.kt`** 两个源文件，
`const` 的值永远不会被折叠进源码文本——所以它**原理上抓不到** M3，**判绿**。
M3 由 `ConversationSearchLikePatternTest.escapeSqlLiteral_matchesTheEscapeCharacterConstant`
抓住（另有重放脚本 G7 判红）。**新期望串里没有转义字符的字面值，这一点是刻意的**：
把字面量 `ESCAPE '~'` 写进这条 DAO 源码护栏会造成**假的覆盖**——它让人以为
「DAO 护栏钉住了转义字符」，实际它只钉住了 ESCAPE 子句的**存在 + 位置 + 与谓词相邻**。

**第一次尝试改错了，如实记**：最初想写内联字面量 `ESCAPE '~'`，但 DAO 里 ESCAPE 是
**常量引用**（`CONVERSATION_LIKE_ESCAPE_SQL`，定义在
`core/data/repository/ConversationSearchLikePattern.kt:41`），而这条断言读的是
**原始源码文本**——`const` 值永远折叠不进来，所以那个期望串**仍然 MISS、测试仍红**。
改为**引用常量标识符**才对。

**该文件只改了这一行**：`6982869b` 的 `git diff --stat` = **1 insertion / 1 deletion，
单 hunk**，`git show 6982869b -- app/src/test` 只有那一个 `-`/`+` 对。
**三条断言一条没被削弱或删除**，`tests` 属性仍是 2。

**顺带纠正一个错误认知（这条比改动本身更重要）**：把字面量 `ESCAPE '~'` 写进这条
DAO 源码护栏会造成**假的覆盖**——转义字符写错改的是 `ConversationSearchLikePattern.kt`，
DAO 源码护栏原理上抓不到。现在期望串里没有转义字符字面值，**这个假覆盖不存在了**。

**新方案的额外收益（但不要说成「从没人抓过」）**：
`ConversationSearchLikePatternTest.daoSource_everySearchTextQueryCarriesEscapeClause`
**早就**逐行扫全部 5 条 `:searchText` 查询，所以缺陷 2 的变异本来就会被它抓住，
「这个洞以前没人管」是错的。新期望串的价值是**给 type 筛选那一条查询加一道独立的
双重保障**——一次同时钉住 ESCAPE 子句的**存在 + 位置**与 type 谓词的**相邻**。
覆盖边界现在是有据可查的：**新断言只覆盖搜索路那一条**，其余 4 条由转义护栏全覆盖。

⚠️⚠️ **别把「敏感项」这个词只留给本轮那一行：2026-10-05 真机窗口又改了两处既有测试
文件，同样是敏感项，但性质不同。** `ca717f39` 补的是**fixture 的状态字段**
（`old-done` / `new-done` 两条本该是 `STATUS_COMPLETED`，helper 却写死成
`STATUS_RUNNING`），`7519ee7f` 改的是**期望列表的排序方向**（`.sortedBy { it.runToken }`
是升序，`'n'`=110 < `'o'`=111）。**断言本体一个字没动**——仍是 `assertEquals` 精确列表
相等，存活集合 `{old-live, new-done}` 一个不多一个不少，**没改成 `containsAny`**。
⚠️ `7519ee7f` 那条**无论 DAO 行为如何都不可能通过**（期望顺序与它自己那行的 `.sortedBy`
相反），所以它是**测试自身写错了**，不是生产缺陷。
⚠️ 另有两条改的是**「假的通过」而不是排序**：`8151de89`（`db.query("INSERT ...")`
根本不执行写操作，行没插进去，后面的 `assertTrue(c.moveToFirst())` 是一条**假断言**）与
`43d6ea7e`（硬编码 `heizige.kk.khatkit.app`，但 debug `applicationId` 是
`heizige.kk.khatkit.debug`，那条用例在 debug 变体上**必然失败**）。修完之后断言**更严**。
详见「C1 真机证据采集」那节的「敏感项」。

#### 一、主机侧重放脚本 `tools/verification/c1s_conversation_type_filter_replay.py`

**命令**：

```
python3 tools/verification/c1s_conversation_type_filter_replay.py
```

| 项 | 实测值 |
|---|---|
| 真实退出码 | **两次都是 0** |
| 两次输出 | **逐字节相同** |
| 输出 SHA-256 | `5645fa1350caf88afb5e33d3e52c9ebbab0b64975a041715559b9cac76a40073`（两次相同），478 行 / 66,933 字节 |
| 结论行 | `RESULT: 全部断言通过（共 438 条）` |
| 脚本规模 | `tools/verification/c1s_conversation_type_filter_replay.py`，**已入库**，83,955 字节 / 1,382 行，脚本自身 sha256 `3b3e12be5d685670ae4121188e4fcd1b4de6a81a31c3317f3b6e7dbd8fc00ce0` |
| 日志位置 | `/tmp/opencode/c1s_check.log`（**仓库外，未入库**） |
| SQL 与谓词常量的来源 | **用正则从 `ConversationDAO.kt` 源码里抽取**，不手抄（与另两台迁移脚本同源做法） |

**断言总数 438 条，0 失败**，逐组分布：

| 组 | 覆盖 | 条数 |
|---|---|---:|
| G0 | 抽取自检（SQL / 谓词常量从源码抠出来的对不对） | 56 |
| G1 | `type` 空串不筛 | 6 |
| G2 | `type` 正确筛选 | 15 |
| G3 | 两路 `type` 口径一致 | 16 |
| G4 | 排序 | 39 |
| G5 | 分页 | 199 |
| G6 | `folder_id` 口径观测 | 4 |
| G7 | **LIKE 转义** | 60 |
| G8 | LIKE 大小写 | 10 |
| G9 | 绑定顺序 | 12 |
| G10 | 空结果集 | 10 |
| G11 | **转义调用方覆盖** | 11 |
| **合计** | | **438** |

**fixture 数据集**：25 行（A1 助手 23 行 / A2 助手 2 行），`type×folder` **6 种组合
全覆盖**——DIRECT×`{'', f1, f2}` = 14/2/1，GROUP×`{'', f1, f2}` = 5/2/1；
21 未置顶 / 4 置顶；标题覆盖普通 / 置顶 / 文件夹 / 「共享词」矩阵 / 字面 `%` / 字面 `_` /
ASCII 大小写对 / 繁简对 / 空串 / 纯空格；**另有故意 4 行 `(is_pinned, update_at)` 全同，
且插入顺序刻意与 id 升序相反**——所以「去掉 id 兜底」这个变异是被**行为**抓到的，
不是被实现细节猜到的。参数矩阵 **60 组**。

#### 变异检验：12 种全部退出码非 0

| 变异 | 改法 | EXIT |
|---|---|---:|
| M1 | 复制第二份谓词字面量 | ≠0 |
| M2 | 抽掉搜索路的 `ESCAPE` | ≠0 |
| M2b | 抽掉 `searchConversations` 的 `ESCAPE` | ≠0 |
| M3 | **转义字符 `~` → `%`** | ≠0（**但由 `ConversationSearchLikePatternTest` 判红，DAO 源码护栏判绿——见上面「敏感项」的判分归属**） |
| M4 | 漏掉 `\|\| '%'` | ≠0 |
| M5 | `ESCAPE '!~'`（**2 个字符**）——脚本报出**真实引擎错误** `ESCAPE expression must be a single character` | ≠0 |
| N1 | 去掉 `ESCAPE` 子句 | ≠0 |
| N2 | 去掉 `ORDER BY` 的 `id` 兜底 | ≠0 |
| N3 | 转义字符选错 | ≠0 |
| N4 | 去掉某调用方转义 | ≠0 |
| N5 | 漏掉抽屉调用方 | ≠0 |
| N6 | 漏掉 `searchConversations` 调用方 | ≠0 |

#### 二、两个真实缺陷（本轮修掉的）

**缺陷 1：抽屉搜索的 LIKE 通配符未转义（真 bug）。** 改前是
`title LIKE '%' || :searchText || '%'` 且**无 `ESCAPE` 子句**。

**实测症状（改之前）**：搜 `100%` 会额外命中**不含 `%`** 的会话；搜 `a_b` 会额外命中
`axbxc`；搜 `%` 命中**全部 23 行**。**只多命中、不漏命中**——所以这是用户**看得见**的错，
不是「理论上不干净」。

**修法**：LIKE 加 `ESCAPE` 子句；新增纯函数
`core/data/repository/ConversationSearchLikePattern.kt`（**72 行**），含
`CONVERSATION_LIKE_ESCAPE_CHAR: Char = '~'`（`:30`）、
`CONVERSATION_LIKE_ESCAPE_SQL: String = " ESCAPE '~'"`（`:41`）、
`escapeConversationLikePattern()`（`:64`，**单趟遍历**）；
`ConversationRepository.kt` 的 **5 个转发点全部走转义**（`:164` / `:243` / `:258` /
`:270` / `:300`）。

**为什么选 `~`**（不是随手挑的，四条都排除了别的候选）：

- **不能是 `%` / `_`**——包夹用的 `'%' … '%'` 自己就会被当成转义符，整条查询不是
  「语义变了」而是**彻底坏掉**。
- **不能是 `\`**——SQL / Kotlin / JSON / 正则**四层各有各的反斜杠规矩**，而且 MySQL
  读者会误读（MySQL 里反斜杠另有含义）。
- **不能是标题常见标点**——模式串会被撑成两倍长。
- `~` **同时躲开三类**：非通配符、非任何一层的转义约定、中英文标题里几乎不出现；
  且是**单字节 ASCII**，**ASCII 大小写折叠免疫**。

**受影响的调用路径（全仓 grep 确认过）**：

| 路径 | 链路 | 说明 |
|---|---|---|
| 抽屉搜索框 | `ChatDrawerViewModel.kt:77` → `searchConversationsOfAssistantPaging(assistantId, keyword, type)` → DAO `searchConversationsOfAssistantByType` | 改前 `query` 里带 `%`/`_` 会多命中 |
| **HTTP API** | `ConversationRoutes.kt:85` `GET /api/conversations/paged?query=…` → `searchConversationsOfAssistantPage(titleKeyword=query)` → DAO `searchConversationsOfAssistantPaging` | ⚠️ **对 API 消费者是「可感知的行为变化」**：`query` 里带 `%`/`_` 的调用方，改动前会拿到**多出来的**会话，改动后只返回标题里**真含该字符**的会话 |
| 死路径（也一并转义） | `ConversationRepository.searchConversations` / `searchConversationsPaging` / `searchConversationsOfAssistant` | ⚠️ 这三个方法**仓内目前无外部调用者**，一并转义是**防将来漏**，不是修了现在的错 |
| **不在范围内** | `MessageNodeDAO.kt:19` 的 `messages LIKE '%'\|\|:messageId\|\|'%'` | `:messageId` 是 **UUID，不是自由文本**，用户碰不到通配符 |
| **不在范围内** | `FavoriteDAO.kt:24` 的 `ref_key LIKE 'node:'\|\|:conversationId\|\|':%'` | 同上 |

⚠️ **收敛点只有一个**：`ConversationRepository` 的 5 个搜索方法里**各过一次**
`escapeConversationLikePattern`，**调用方传原始用户输入**（抽屉
`ChatDrawerViewModel`、HTTP API `ConversationRoutes`）。**谁都不许自己转**——两边都转
会把 `a%b` 变成 `a~~~%b`。这是 G11 那 11 条断言钉的东西。

**缺陷 2：`ORDER BY` 缺 tiebreaker（真实风险）。** 改前是
`ORDER BY is_pinned DESC, update_at DESC`，**没有 `id` 兜底**。`update_at` 是**毫秒
精度**，同一毫秒里更新两个会话时，`LIMIT/OFFSET` 分页**理论上可能重复或漏行**。

**修法**：**14 处 `ORDER BY` 末尾追加 `id ASC`**（`getPinnedConversations` 那条
`ORDER BY update_at DESC` 也补了，落在 `ConversationDAO.kt:145`）。`id` 是**主键**
（`app/schemas/…/32.json` 的 `PRIMARY KEY(id)`），投影里本来就有。**当 `update_at`
不同时行为逐字不变**——这是纯改善，不是取舍。

⚠️ **脚本造了 4 行全同 fixture 实测过**：改前**本机稳定**，**但那是引擎行为，不是 SQL
契约**——SQL 只保证结果集本身，分页顺序的确定性得靠 `ORDER BY` 自己写出来。所以
**仍按风险修**，不拿「本机跑得稳」当验收。

#### 三、四条命令的真实退出码（本轮）

| 命令 | 退出码 | 结果 |
|---|---:|---|
| `./gradlew --offline :app:testDebugUnitTest --rerun` | **0** | **92 类 / 722 例 / 0 失败 0 错误 0 跳过**（改前 91 类 / 710 例，净增 1 类 / 12 例） |
| `./gradlew --offline assembleDebug` | **0** | `BUILD SUCCESSFUL` |
| `./gradlew --offline lint` | **0** | `0 errors, 587 warnings, 6 hints`（**与基线逐位相同**） |
| `python3 tools/verification/c1s_conversation_type_filter_replay.py` ×2 | **0 / 0** | 输出**逐字节相同**，sha256 `5645fa13…a40073` |

lint 逐文件命中：`ConversationDAO.kt` / `ConversationRepository.kt` /
`ChatDrawerViewModel.kt` / `ConversationSearchLikePattern.kt`——**四个全部 0 命中**。
APK 字节数见「三个 APK」段的本轮记录（**不要用 SHA-256 做对比**，见该段的警告）。

#### 四、⚠️ 这一节仍然不能证明什么（6 条，逐条照记）

1. **真机 Android SQLite 的 LIKE / ESCAPE 行为——零证据。** 本节**全部** LIKE 证据
   来自 **CPython 内置的 SQLite**（3.51.2），是**主机侧**引擎，**不是 Android 的
   `SQLiteDatabase` / `android.database.sqlite`**。本节所有「转义后只匹配字面量」的
   结论，**不能直接搬到真机**。
2. **Room `LimitOffsetPagingSource` 的真实行为——零证据。** 脚本是**自己拼
   `LIMIT`/`OFFSET` 机械模拟**分页的；`PagingSource` 的**并发失效 / 快照一致性 /
   回滚**（`invalidate()`、事务中途翻页）**一条都没验**。
3. **`~` 在不同 Android / SQLite 版本上的语义差异，以及 `ESCAPE` 与大小写折叠
   （`PRAGMA case_sensitive_like`）的交互——零证据。** 本机只有一个 SQLite 版本。
4. **Room / KSP **不在编译期**拒绝多字符 `ESCAPE`。** 变异 M5 带 `ESCAPE '!~'`
   **编译通过**，脚本跑起来才报引擎错误 `ESCAPE expression must be a single
   character`。也就是说「长度必须为 1」**只是引擎运行期检查**，编译器不拦——这是**唯一
   一条被本节自己实测出来的「运行期检查」事实**。
5. **脚本靠正则抠 SQL，抠取规则本身有解析上限**；而且**「正则抠出来的 SQL == Room 实际
   编译出来的 SQL」这一步没有独立证据**。抠对了不代表 Room 生成的 `@Query` 与它一致。
6. **抽屉 UI 端到端没验**：搜索框输入 → 列表刷新的整条链路，一次没跑过。

⚠️ **最后一句必须自己说清楚**：本节给了 C1-10「类型筛选只过滤」一条**真实的执行
证据**——**集合相等（不是数量相等）**、筛选后 `count` 正确、分页无重复无遗漏、切换
`type` 参数**不改变查询种类**。**这是 C1-10 迄今最硬的一块证据。** 但契约 `:206`
点名的四类产物**一份都没补上**，**所以 C1-10 的状态列必须仍是 `unverified`**，
另外 9 个用例状态一律不动。证据登记表 C1-10 行填的是「部分证据（零设备，真实 SQLite
执行）」并逐项写清了哪些补上了、哪些仍是零份——**状态列没动**。

### C1 真机证据采集（2026-10-05，OnePlus PKG110）

登记于 commit `ca717f39` / `7519ee7f` / `8151de89` / `43d6ea7e` / `2c1d7632`
（详见「锚点之后的后续提交」小节）。**这一节同样不改变任何用例的判定**：
契约 `:206` 点名的四类产物**拿到了两类**（viewer 可见消息 ID、导出 SHA-256），
**仍缺两类**（真实 prompt+completion token、真实模型调用序列），
所以**十例仍全 `unverified`，0/10 不变**。

⚠️ **四类里两类齐了 ≠ 契约达成。** 别把「有内容了」读成「可以勾掉了」。

#### 设备信息（这是「设备·Android 版本」列第一次有值）

| 项 | 实测值 |
|---|---|
| 设备 / Android | **OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`** |
| 分辨率 | Physical **1264x2780**，Override **1080x2376** |
| density | Physical **560**，Override **480** |
| fingerprint | `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys` |
| adb 连接 | `192.168.31.183:<port>`（**无线调试**）⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493` / `37957` / `40879` / `46888`；**必须用 `adb mdns services` 找当前端口**，别照抄任何一次记下来的端口 |
| 被测 app | `heizige.kk.khatkit.debug`，`versionName 2.5.5` / `versionCode 190`，launcher Activity 是 `RouteActivity` |
| 生产库 | `rikka_hub`，`PRAGMA user_version = 32`，`PRAGMA integrity_check = ok`，`ConversationEntity.group_cards = TEXT NOT NULL DEFAULT ''` |
| 证据采集用库 | `c1-device-evidence.db`（**独立库名，生产同一个 `AppDatabaseFactory.create(...)`**，同套 SQLite 扩展、同一个 `onOpen`、同一 schema v32；不碰 `rikka_hub`、不碰用户数据，测试结束删掉自己那个库） |

#### 仪器测试：C1 相关 25 条首次跑通（25/25 全绿）

新增仪器测试类
`app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/C1DeviceEvidenceTest.kt`
（**1144 行 / 3 个用例**，`2c1d7632`）。

| 类 | tests | failures |
|---|---:|---:|
| `GroupRunDAOTest` | 12 | **0** |
| `Migration_30_31_Test` | 7 | **0** |
| `Migration_31_32_Test` | 6 | **0** |
| **合计** | **25** | **0** |

**命令与退出码**：

```
./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=heizige.kk.khatkit.app.core.data.db.dao.GroupRunDAOTest,heizige.kk.khatkit.app.core.data.db.migrations.Migration_30_31_Test,heizige.kk.khatkit.app.core.data.db.migrations.Migration_31_32_Test
→ exit 0
  JUnit XML: tests=25 failures=0 errors=0 skipped=0
  日志行：Starting 25 tests on PKG110 - 16 / Finished 25 tests on PKG110 - 16 / BUILD SUCCESSFUL in 33s
```

另一轮把 DB / sync / browser 存档那批合跑（产物落
`app/build/outputs/androidTest-results/connected/debug/TEST-PKG110 - 16-_app-.xml`）：
**45/45 全绿，exit 0**。逐类 `tests` 属性实测：
`C1DeviceEvidenceTest` 3、`GroupRunDAOTest` 12、`Migration_11_12_Test` 7、
`Migration_30_31_Test` 7、`Migration_31_32_Test` 6、`BrowserArchiveTest` 4、
`DatabaseBackupTest` 3、`BackupManagerTest` 3 = **45**，
`testsuites tests="45" failures="0" errors="0" skipped="0"`，
`test-result-exit-code.txt` = `0`。

⚠️ **这一节只把「仪器测试跑没跑过」这件事了结了，它不产生契约 `:206` 意义上的
四类产物里的任何一类。** 所以 C1-07 那一行的迁移侧证据从「零设备主机侧重放」升级成
「真机仪器 12 条 + 7 条 + 6 条全绿」，**但状态列仍是 `unverified`**——C1-07 要的
「让第 2 个角色失败后重试同一 `round_id`，贴库内 `committed_role_ids` 与实际消息条数
对得上」那条真机交互**没做**。

#### ⚠️⚠️ 踩坑一：无线调试开着时 adb mDNS 会把同一台设备自动注册两条

⚠️ **这不是 OEM 兼容性问题，是同一台机器被登记了两次。** 无线调试开着时，adb 的
mDNS 发现会把**同一台物理设备自动注册成两条 serial**——一条是 IP 形式
`192.168.31.183:<port>`，另一条是 `adb-3B6F5ME910B6H059-Sqr0AX._adb-tls-connect._tcp`。
AGP 拿到两个 serial 就**对每个各跑一遍**安装 / 卸载 / 启动，两边**互相踩**。
实测报错组合：

```
[Failure [DELETE_FAILED_INTERNAL_ERROR]]
Finished 0 tests on PKG110 - 16-192.168.31.183:38493
Finished 0 tests on PKG110 - 16-adb-3B6F5ME910B6H059-Sqr0AX._adb-tls-connect._tcp
Test run failed to complete. Unable to find instrumentation target package: heizige.kk.khatkit.debug.
→ 0 用例、exit 1
```

伴随 `Error: -99` 安装失败（`-99` = `INSTALL_FAILED_INTERNAL_ERROR`）。

**处置**：`adb disconnect` 掉 mDNS 那条，只留一条；或用环境变量 `ANDROID_SERIAL`
把 serial 钉死。**跑之前必须 `adb devices -l` 确认只有一条**——不看就开跑，
你会花好几轮去追一个根本不是代码问题的失败。
⚠️ 找当前端口用 `adb mdns services`（端口每次开无线调试都会变）。

⚠️ 别把它读成「测试挂了」：同一份代码在只剩一条 serial 时是 `BUILD SUCCESSFUL in 33s`。
⚠️ 也别把它读成「这台 OnePlus 兼容性差」——**它跟 OEM 无关**，换成任何一台开无线调试
的机器都会复现。

#### ⚠️⚠️⚠️ 踩坑二：`./gradlew connectedAndroidTest` 跑完会**把 app 卸载掉**

**这一条会直接毁掉证据文件。** 产物写在
`/storage/emulated/0/Android/data/heizige.kk.khatkit.debug/files/`，
而 `connectedAndroidTest` 结束时 AGP 会**卸载 app**——**外置目录里的证据文件一起被删掉**。
`pull` 没来得及跑就什么都没了，而且**看起来像「测试没写出文件」**。

**所以采证据必须绕开 `connectedAndroidTest` 的收尾流程**：

```
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb shell am instrument -w -e class <FQN> heizige.kk.khatkit.debug.test/androidx.test.runner.AndroidJUnitRunner
adb pull /storage/emulated/0/Android/data/heizige.kk.khatkit.debug/files/<name>
```

⚠️ **手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull`。** 顺序不能反：
先 pull 再让任何会触发卸载的东西跑。

#### ⚠️⚠️⚠️ 敏感项：三处测试缺陷修复（**定性为测试代码自身缺陷，不是生产代码 bug**）

上一轮那 25 条里有 **2 条失败**。逐条定性如下。

**① `GroupRunDAOTest.archivingByAgeKeepsRunningRecords`（两处缺陷，`ca717f39` + `7519ee7f`）**

- **缺陷 A**：`GroupRunDAOTest.kt:57` 的 `run()` helper 把状态**写死成
  `STATUS_RUNNING`**，所以 `:269-271` 插入的 `old-done` / `new-done` 两条**名字叫 done、
  实际状态是 RUNNING**。而生产 `GroupRunDAO.deleteFinishedBefore` 的口径是
  `started_at < :before AND status != 'RUNNING'`——**按设计就不该删 RUNNING 行**，
  实测三行全留。**修法**：参照同文件 `archivingKeepsRunningRecords`（`:259`）的既有
  写法，给两个 `-done` 行补 `.copy(status = GroupRunEntity.STATUS_COMPLETED)`。
- **缺陷 B（被 A 掩盖，修完 A 才暴露）**：期望列表 `[old-live, new-done]` 与该行自己的
  `.sortedBy { it.runToken }` **排序方向相反**（`'n'` = 110 < `'o'` = 111，所以升序后应是
  `[new-done, old-live]`）→ **无论 DAO 行为如何都不可能通过**。**修法**：只把期望列表
  重排为 `[new-done, old-live]`。
- ⚠️ **断言强度未变**：仍是 `assertEquals` 精确列表相等，存活集合 `{old-live, new-done}`
  一个不多一个不少，**没有改成 `containsAny`** 或任何弱化形式。

**② `Migration_31_32_Test.insertConversation`（`8151de89`）——这条曾是证据缺口**

- **缺陷**：helper 用 `db.query("INSERT INTO ...")`，但 `SupportSQLiteDatabase.query()`
  是 **SELECT 接口**，只 prepare 返回 Cursor，**不执行写操作** → 行**根本没插进去**，
  后面的 `assertTrue(c.moveToFirst())` 变成一条「有没有真写进去」的**假断言**（它永远在
  问一个没发生过的操作）。
- **修法**：改用 `db.execSQL(sql, bindArgs)`。签名是 `Array<out Any?>`，现有调用点元素是
  `String` / `Long`，**类型合法，无需再改**。
- ⚠️ **为什么这条重要**：它是**唯一**真正往真 SQLite 写入并读回**非空 `group_cards`
  blob** 的用例。修好之后，「31→32 迁移后 `group_cards` 能往返承载角色卡」这件事
  才有真机证据——**修之前那一格是空的**。

**③ `ExampleInstrumentedTest.useAppContext`（`43d6ea7e`）**

- **缺陷**：硬编码 `heizige.kk.khatkit.app`，但 debug 变体的 `applicationId` 是
  `heizige.kk.khatkit.debug` → 这条用例**在 debug 变体上必然失败**，它是 45/45 那一轮
  之前 53 条里的 3 个 failure 之一。
- **修法**：**不硬编码任何字符串**，改成两个**独立来源**对账：
  `assertFalse(appContext.packageName.isBlank())` +
  `assertEquals(appContext.applicationInfo.packageName, appContext.packageName)`。
  后者比原来那条更强：它同时钉住「`packageName` 与 `applicationInfo.packageName` 一致」。

**⚠️ 「生产代码零改动」的独立核实**：这五个 commit（`ca717f39` / `7519ee7f` /
`8151de89` / `43d6ea7e` / `2c1d7632`）逐个 `git diff --name-only <sha>~1..<sha>` 核过，
**全部只碰 `app/src/androidTest/`**，一个 main 源文件都没动。合并区间
`git diff --name-only 4a4def40..2c1d7632 -- app/src/main | wc -l` = **0**。
⚠️ **注意别把区间取到 `HEAD`**：`4a4def40..HEAD -- app/src/main` 是 **5**，
那 5 个文件全部来自**后面两批与 C1 证据无关的改动**（`0a289af6` 给
`Screen.Assistant` 补 `@Serializable`、`18baa930` 删圆形揭幕动画试验页），
不是证据采集或测试修复带来的。

#### viewer 可见消息 ID（契约第一类产物，第一次有真机硬证据）

`C1DeviceEvidenceTest` 在**真机真实 Room 库**上跑真实
`GroupTurnCoordinator.viewerMessages`。

**fixture**：3 角色 `a` / `b` / `c`（`c` 是 chair），`mode = pipeline`，**2 个 round 共 7 条
消息**，每条带真实 `roleId` / `roundId` / `turnKind` / `mentionRoleIds`。
会话 id `0c1c0de5-0000-0000-0000-000000000001`。

| viewer | predecessor | 可见消息 ID |
|---|---|---|
| `role_a` | `null` | `11111111-…-0001`、`22222222-…-000a`、`11111111-…-0002`、`22222222-…-000b`、`ba5fbc34-509a-498e-bc2d-314afbc06476` |
| `role_b` | `a` | `11111111-…-0001`、`11111111-…-0002`、`22222222-…-000b`、`22222222-…-000c`、`ba5fbc34-…` |
| `role_c` | `b` | `11111111-…-0001`、`11111111-…-0002`、`22222222-…-000c`、`22222222-…-000d`、`ba5fbc34-…` |

**消息作者对照**：`…-0001` = user、`…-000a` = a、`…-0002` = user、`…-000b` = a、
`…-000c` = b、`…-000d` = c、`ba5fbc34-…` = `__summary__`。

**越权审计**：**6 个 `(viewer, author)` 有序对逐条审计**，
`violations: []`、`passed=true`。审计方式**不重写过滤逻辑、只审计它的输出**——viewer
看到的每条**他人**消息都必须能被生产规则的三条放行分支之一解释：
`mentionRoleIds` 含 viewer / `step.predecessorId == author` / `step.chairRound`。
**正向断言**：用户消息与 `__summary__` 轮次摘要**对每个视角都必须可见**。

⚠️ **这一列只覆盖了 pipeline 的 viewer 集合。** roundtable / vote 只做了导出与 QR 往返，
它们的 viewer 集合**没采**；`chairRound = true` 那个放行分支与 `__summary__` 那个放行分支
**在这条夹具里没被触发**（pipeline 无议长汇总轮）。C1-03（roundtable 议长视角）与
C1-04（vote）要的**议长视角可见集合**因此仍然零份。

#### 导出 SHA-256（契约第四类产物，第一次有真机硬证据）

真机上跑 `TavernChatCodec.exportGroupJsonl(...)` → **写真机文件** → 真机
`java.security.MessageDigest` 算 SHA-256：

| mode | bytes | SHA-256 |
|---|---:|---|
| pipeline | 3588 | `6f8efc6c03c2ced5436e1f00a103a9a06a505c63a2bda8246c6b396270cea5d2` |
| roundtable | 3590 | `1535fe2796e7af128816e60a6985dbd40db326a5b5a6c87b54ba25fc36527514` |
| vote | 3591 | `36397b7a10fc6db92861680104b12d1cbc56c104b7ab7d1919ad7b5b790c4e26` |

**QR 载荷**（`GroupChat.encodeQr`）：

| mode | bytes | SHA-256 |
|---|---:|---|
| pipeline | 1453 | `5dc229e1bbdcd7fdfb5db3fb5646992de49ffe50cd617bb86709ef2037cbc7e2` |
| roundtable | 1455 | `9bbd19354f49b5c5fe1b345cdbd859101540936499b785da2937cbbd834a3d09` |
| vote | 1456 | `1e61336f882a055ee8593486565e029f9db36b7301ddecce9bfb97e3702ef708` |

**文件路径格式**：
`/storage/emulated/0/Android/data/heizige.kk.khatkit.debug/files/c1-evidence-<mode>.jsonl`，
已 `adb pull` 回本机。

**交叉验证（三重）**：

1. 真机 `MessageDigest` 与本机 GNU coreutils `sha256sum` 对**同一份拉回的文件**独立重算
   → **6/6 全一致**（3 个 mode × 2 轮），**字节数也全等**：真机
   `file_length_on_disk` == JSON `bytes` == 本机 `wc -c`。
2. 真机**同一个 JVM 内** `MessageDigest` 算两遍一致（JSON 字段 `sha256_second_pass`）。
3. 两个**独立进程 + 独立重装**跑出来的文件，`cmp` **逐字节相同**（本机实测：两次拉回的
   `c1-evidence-*.jsonl` 六个文件两两同哈希）。

**往返断言**：3 种 `mode` 各过 `GroupChat.isValid`；**首行必须带 `chat_metadata`**；
`exportGroupJsonl` → `importGroup` 后群名 / 用户名 / 成员名单 / `GroupConfig`
（`decode(encode(x)) == x`）/ `RoleCardMeta` 全等；**7 条消息逐条**
`role_id` / `round_id` / `turn_kind` / `mention_role_ids` 相等；并额外断言契约字段同时
写回了 `node.messages[selectIndex]`。

⚠️⚠️ **哈希可复现性只在固定夹具下成立。** 所有 `UIMessage.id` 被**钉死**，因为 `roundId`
由触发消息 id 派生——id 一变，派生出来的 `round_id` 就变，字节就变。所以
**真实用户导出的 SHA 不可复现**。生产宣称的「确定性」是「**同一输入字节相同**」，
这条被钉住了；**「跨设备 / 跨夹具可比」没验证**。
⚠️ 另外这是**同一个独立库名下的产物**，不是经 UI「导出面板 → `writeExportTempFile` →
`ACTION_SEND`」真实分发出来的那一份——`docs/browser-device-validation.md` 那类
分发链路没被覆盖。

#### ⚠️⚠️ 仍然缺的两类（如实写「缺」，不许粉饰）

**token (prompt+completion)：仍然缺。** 只拿到**预算口径**的真机证据
（`budgetDecision` / `advance` → `group_runs` **真落库再读回**）：

| 字段 | 实测值 |
|---|---|
| `spent` | **4096**（= prompt **3000** + completion **1096**） |
| `limit` | **400** |
| `skipped` | `[b, c]` |
| `status` | `BUDGET_STOPPED` |
| `reason` | `token_budget_exceeded` |
| `committed` | `[a]` |
| `run_token_persisted_before_call` | **true** |

⚠️ **但 3000 / 1096 是构造的输入，不是任何模型吐出来的真实用量。** 测试 JSON 里
`token_source` 字段已明写 **`budget-accounting-only, no live LLM call`**。读成
「token 计数已验」是误读。

**实际模型调用序列：仍然缺。** 只拿到**路由决策**的真机证据——三个角色分别命中
`resolveGroupTurnModelId` 的**三条不同通路**：

| role | 走的通路 | 判据 |
|---|---|---|
| `a` | 三关全过走 `role.model_id` | 非空白 ✓ / 可解析为 Uuid ✓ / 在模型库里 ✓ |
| `b` | **空白串第一关就挂**，回落 `assistant.chat_model_id` | `gate_non_blank=false` |
| `c` | **合法 Uuid 但不在模型库**，卡在第三关回落 | `gate_is_known_model=false` |

每条还记了 `TaskRoutes.resolve` 返回的**真 `Model`**（uuid + name）与候选列表，
加上**真库里**每条消息记录的 `modelId` 供对照。
⚠️ **没有任何一次真实 HTTP 调用发生过。** JSON 里 `model_sequence_source` =
**`routing-decision + recorded-message-modelId, no live LLM call`**。

**根因：设备上没有配 API key。** 只读检查了
`files/datastore/settings.preferences_pb`（**165 字节**），`strings` 只有
`mcp_servers` / `data_version` / `assistants` / `quick_messages` / `launch_count` /
`select_assistant` / `0950e2dc-…`——**没有 `providers`、没有 `models`、没有 API key**。

#### ⚠️ 这一节仍然不能证明什么（6 条，逐条照记）

1. **十例状态仍全 `unverified`。** 四类缺两类，按本文「判定规则」小节（与
   `beyond-operit-client-changes.md:232-235` 同源）不算通过。
2. **viewer 可见集合只覆盖 pipeline。** roundtable / vote 的 viewer 集合没采，
   `chairRound = true` 与 `__summary__` 两条放行分支在这条夹具里没被触发。
3. **token 与模型调用序列仍然是「口径证据」，不是「真实调用证据」。** 两份 JSON 自己
   的 `token_source` / `model_sequence_source` 字段就写着 `no live LLM call`。
4. **哈希只在固定夹具下可复现。** 真实用户导出的 SHA 不可复现（id 钉死才有这个性质）；
   「跨设备 / 跨夹具可比」没验证。
5. **没走过真实 IO 分发链路。** 产物是测试直接写 `getExternalFilesDir`，
   不是 UI 导出面板 + `ACTION_SEND`；**酒馆本体能不能打开这份文件，仍然零证据**
   （SillyTavern 侧那部分只有零设备结构对照，见「C1-P 群聊导出确定性哈希（零设备）」）。
6. **C1-10 的 UI 那半仍然是零证据。** 真机接上了不等于抽屉筛选录屏跑过了；
   `PagingSource` 并发失效 / 快照一致性 / 回滚一条没验。

⚠️⚠️⚠️ **上面 6 条是第一个真机窗口的结论，按惯例保留不覆写。第二个窗口已经推翻了
其中第 2、3 条**（详见下一节「C1 真机证据采集第二轮」）：

| 原第几条 | 第一个窗口的结论 | 第二个窗口之后 |
|---|---|---|
| 2 | viewer 可见集合只覆盖 pipeline | ✅ **已推翻**：三 mode 各采一遍，越权审计三 mode 全 `violations=[]`，议长 `chairRound=true` 分支第一次被触发 |
| 3 | token 与调用序列是「口径证据」不是「真实调用证据」 | ✅ **已推翻（部分）**：真实 HTTP 4 个请求 + `evidence_kind=real-http-capture-via-adb-reverse`，usage 与落库 `spent_tokens` 逐条相等。⚠️ 但 **roundtable / vote 仍零份**，且 **mock 不是真实模型** |
| 5 | 没走过真实 IO 分发链路 | ⚠️ **仍然成立**：产物还是测试直接写 `getExternalFilesDir`，**酒馆本体能不能打开仍零证据** |

⚠️ 第 1、4、6 条**至今仍然成立**：十例状态仍全 `unverified`、哈希仍只在固定夹具下可复现、
C1-10 的 UI 那半仍零证据。

### C1 真机证据采集第二轮（2026-10-05，真实 HTTP：模型调用序列 + token 计数）

登记于 commit `47693445` / `858c11d0` / `bff7b0c6` / `a9d2077e`
（详见「锚点之后的后续提交」小节）。**这一节同样不改变任何用例的判定**：
上一节那四类产物**四类都有了真机内容**，但 §⑦ 列的 5 项仍然缺，
所以**十例仍全 `unverified`，0/10 不变**。

⚠️ **四类全有内容 ≠ 契约达成，也 ≠ 十例验证矩阵完整。** 「每一列都填上了东西」和
「这一行该判通过」是两件事——契约判定的是**每行**是否同时具备命令退出码、可见消息
集合、调用序列与哈希，而且实测值必须来自真实链路。本节提供的是**列的填充**，
不是**行的通过**。

#### 怎么绕开「设备上没有 API key」：真 provider 代码路径 + 真实 HTTP

上一节记的根因是「设备上没有配 API key，所以 `:206` 第三、四类**在原理上就采不到**」。
这一轮绕开了它，**没改生产代码、没加测试专用后门**：往生产单例 `SettingsRepository`
里装一个 **base URL 指向 `http://127.0.0.1:8765/v1` 的自定义 OpenAI 兼容 provider**，
设备侧用 `adb reverse tcp:8765 tcp:8765` 把它打到开发机上的 mock 服务。于是走的是
**完整的生产调用链**：

```
ChatManager.sendMessage
  → GenerationLoop
  → ProviderManager
  → OpenAIProvider
  → ChatCompletionsAPI.streamText      （真的构造 OpenAI wire body）
  → Ktor CIO OkHttpClient             （真的发 TCP 到 127.0.0.1:8765）
```

实测抓到的请求特征（**这些是 Ktor 客户端自己发的，不是测试伪装的**）：
`User-Agent: ktor-client`、`accept: text/event-stream`、
`stream_options.include_usage: true`，**三个请求全是 `stream=true`**。

| 项 | 实测值 |
|---|---|
| 新增仪器测试类 | `app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（**865 行 / 3 个用例**，`47693445`） |
| mock 服务地址 | `http://127.0.0.1:8765/v1`（`chatCompletionsPath = /chat/completions`） |
| 设备→主机通道 | `adb reverse tcp:8765 tcp:8765` |
| 绑定模型（wire 串） | `mock-model-a` / `mock-model-b` / `mock-model-c`，一对一绑到角色 `a` / `b` / `c` |
| 角色暗号 | 每个角色的 system prompt 里埋 `CASE:<case> ROLECODE:<code>`（`A`/`B`/`C`） |
| 旁证落点 | mock 服务在**开发机侧**把每个请求的完整 body 原样落盘 `requests.jsonl` |
| JSON 证据文件 | `c1-live-evidence-main.json` / `c1-live-evidence-budget.json`，`evidence_kind` = **`real-http-capture-via-adb-reverse`** |

⚠️ **为什么 mock 侧那份记录算第三方旁证**：它由**开发机上的 mock 服务**写，
**与应用代码无关**——被测代码无法伪造它，也无法阻止它记录。所以「某个角色实际收到了
什么」这件事有了一份**独立于 app 的记录**，这比只看库里的消息 ID 强得多，也是视角隔离
最硬的形式。**上一轮那种「`token_source` / `model_sequence_source` 自己写着
`no live LLM call`」的情况，这一轮两份 JSON 都换成了 `real-http-capture-via-adb-reverse`。**

#### 契约点名的「实际模型调用序列」（第三类产物，第一次有真实 HTTP 记录）

| seq | model | case | speaker | msgs 条数 | prompt | completion | 出现的 ROLECODE |
|---|---|---|---|---:|---:|---:|---|
| 1 | `mock-model-a` | main | A | 2 | **201** | 34 | `A` |
| 2 | `mock-model-b` | main | B | 3 | **235** | 34 | `B`, **`A`** |
| 3 | `mock-model-c` | main | C | 3 | **235** | 34 | `C`, **`B`** |
| 4 | `mock-model-a` | budget | A | 2 | **201** | 35 | `A` |

**契约要的「实际模型调用序列」= `mock-model-a` → `mock-model-b` → `mock-model-c`**，
与 `bindings` 里声明的 wire 字符串**逐字一致**（不是「测试里写的顺序」，是**真的发出去的
四个请求**）。`round_id` =
`round-7098bf9e-4bcb-41f2-a0e7-e05d0395fcc9`，
`assistant_role_order = ["a","b","c"]`。

pipeline 下每个角色**恰好只多看到「上一位的输出」**，在**请求层**就成立：

| viewer | 真实收到的消息 ID（角色） |
|---|---|
| `a` | `7098bf9e…`(user)、`cc3d451c…`(a) |
| `b` | `7098bf9e…`(user)、`cc3d451c…`(a)、`1f69a5bc…`(b) |
| `c` | `7098bf9e…`(user)、`1f69a5bc…`(b)、`49e3ffcb…`(c) |

#### ⚠️⚠️ 逐请求视角隔离审计（契约 `:200` 最硬的证据形式）

**这是「真实网络层」的判定，不只是 `buildContext` 的纯函数判定**——审计对象是
**真的发到 TCP 上的请求 body**：

- **`a`（seq1）**：只有自己的 system prompt（`ROLECODE:A`）+ 用户那条。
  **没有 b、没有 c。**
- **`b`（seq2）**：自己的 system（`ROLECODE:B`）+ 用户 + **一条 assistant 带
  `ROLECODE:A`**（a 的输出）。**没有 `ROLECODE:C`。**
- **`c`（seq3）**：自己的 system（`ROLECODE:C`）+ 用户 + **一条 assistant 带
  `ROLECODE:B`**（b 的输出）。**没有 `ROLECODE:A`。**
- **budget 轮（seq4）**：只有 a，符合 `limit=1` 截断。

**结论：pipeline「只串联上一位」这条契约在真实 HTTP 层成立，不只在 `buildContext`
的单元判定上成立。** ⚠️ 注意「只多看到上一位」与「看得见自己那条」不矛盾——
`GroupChat.visibleMessages` 有一条 `message.roleId == viewerId -> true` 的放行分支，
所以每个 viewer 的请求里**必然有自己那条**，那**不是越权**。

#### 契约点名的「prompt+completion token」（第四类产物，第一次有真实 usage）

**真实 usage 数字，且与落库 `group_runs.spent_tokens` 对账相等**：

| 轮 | 各请求 (prompt+completion) | 合计 | 落库 `group_runs.spent_tokens` | 是否相等 |
|---|---|---:|---:|---|
| main（pipeline，3 角色全跑完） | 235 + 269 + 269 | **773** | **773** | ✅ 逐条相等 |
| budget（`token_limit=1`） | 236 | **236** | **236** | ✅ |

main 轮 `group_run` 落库字段：`status=COMPLETED`、`spent_tokens=773`、
`token_limit=100000`、`committed_role_ids=["a","b","c"]`、`skipped_role_ids=[]`、
`reason=""`、`endedAt` 非空（已收尾）。
budget 轮 `group_run` 落库字段：`status=BUDGET_STOPPED`、`spent_tokens=236`、
`token_limit=1`、`reason=token_budget_exceeded`、`skipped_role_ids=["b","c"]`、
`committed_role_ids=["a"]`。

**口径说明**：预算是 **prompt + completion 累计**（`GroupTurnCoordinator.usageOf`），
**不是** `TokenUsage.totalTokens`，也不是「最后一条消息的用量」——测试里那条
`sumOf { promptTokens + completionTokens }` 与 `run.spentTokens` 的等值断言就是钉这个的。

⚠️ **mock 的 usage 是 `ceil(bytes/4)`，这是估算不是真 tokenizer 的结果。** 但
**原始字节数也一起落盘，可手算复核**——所以「201」「235」这些数不是凭空来的。
⚠️ 另外 seq1 那个请求 `Content-Length: 24786`（**绝大部分是 tools 定义**），
但 prompt token 只有 **201**——这个差值本身就是「**tools 定义占了绝大部分请求体，
但 token 计数远小于字节数**」的证据。

#### ⚠️⚠️ 诚实边界（三条，必须连着上面的数字一起读）

1. **mock 不是真实模型。** 这一轮拿到的证据等级是「**真实 provider 代码路径 +
   真实 HTTP 请求 + 真实 SSE 流 + 真实 usage 报文**」，**不是**「真实 LLM 推理」。
   `usage` 是 mock 按字节数算的估算值。**别把它读成「已在真实模型上验过」。**
2. **roundtable / vote 没有真实 LLM 调用记录。** 只有**夹具层**的 viewer 台账；
   议长 `chairRound=true` 的**实际 prompt 组装**、vote 的 **`parseBallot →
   __summary__` 分支**，**仍无任何真机 HTTP 记录**——mock 目前返回 `[mock] CASE:…`
   文本，`parseBallot` **认不出 `VOTE:` 前缀**，所以投票分支根本没被真实调用过。
3. **本节采的是 pipeline 一种 mode 的真实链路。** 上一节那个「三种 mode 各采一遍」的
   扩展（`a9d2077e`）是**夹具层**的 viewer 台账扩展，**不是**真实 HTTP 扩展。

#### ⚠️⚠️⚠️ 敏感项：两处新测试断言修正（定性为**测试代码自身缺陷**）

这两处和上一节那三处是**同一类**：**测试自己写错了，生产代码是对的**。
⚠️ 但**性质更敏感**——上一节三处改的是 fixture 字段与硬编码串，这两处**改的是断言本体**，
所以逐条交代「改前 / 改后 / 为什么原断言错」。

**A. `C1LiveModelSequenceTest` 的视角隔离断言（`bff7b0c6`）——断言口径漏了「自己」**

- **改前**：`assertTrue(seenByA.isEmpty())` —— 断言角色 a **看不到任何**他人发言，
  但写成了「**看不到任何发言**」。
- **改后**：`assertTrue(seenByA.all { it == "a" })` —— 断言 a 看到的**全部**发言
  **都来自 a 自己**。
- **原因**：`GroupChat.visibleMessages` 有一条 `message.roleId == viewerId -> true`
  的放行分支，所以 a **本来就该看得见自己那条发言**（实测 a 的可见集合是 `[a]`
  而不是 `[]`，真机首跑就炸在这里）。**原断言把「不越权」写成了「看不到任何发言」，
  是断言口径漏了「自己」，不是行为有问题。**
- ⚠️ **判定依据不是测试自己的说法**：mock 侧独立记录的 **seq=1** 请求里**只有
  system + user 两条消息、零个他人输出**——mock 与被测代码**互相独立**，这条旁证证明
  **行为本来就是正确的**。改动后语义变成**真正的越权判定**（不是放宽，是修正判据）：
  把自己那条排除掉反而**才是**越权。
- **断言强度**：改后**更严**。`isEmpty()` 对「多出一条 a 自己的」会误报，
  `all { it == "a" }` 则**同时**拒掉「出现 b」和「出现 c」，并且仍然拒掉空可见集合
  之外的一切异常项。**没有改成 `containsAny` 或任何弱化形式。**

**B. `C1DeviceEvidenceTest` 读回 JSON 的三处类型断言（`a9d2077e`）**

- **缺陷**：读回来的是 `JsonElement`（`JsonLiteral` / `JsonNull`），
  **不能直接和 Kotlin 的 `Boolean` / `null` 比较**。三处断言因此写错。
- **改法**：`JsonNull` 用 `JsonNull` 比、`true/false` 用 `JsonPrimitive(true)` /
  `JsonPrimitive(false)` 比、`String?` 用 `JsonPrimitive("a")` 比。三处分别是：
  1. roundtable 议长必须落在 `chairRound=true` 的发言位 →
     `assertEquals(JsonPrimitive(true), (roundtableSteps["role_c"] as JsonObject)["chair_round"])`
  2. vote 模式各角色的 `predecessor_id` 必须是 `JsonNull`（因为 `put("predecessor_id", null)`
     写成**显式 null**，读回来是 `JsonNull` 而**不是缺失键**）
  3. pipeline 里 b 的 `predecessor_id` 必须是 `JsonPrimitive("a")`、a 的必须是 `JsonNull`
- ⚠️ **同一笔还加了防「抄三遍」的断言**：`assertEquals(3, (reparsedObject["viewer_ledger_by_mode"]
  as JsonObject).size)` 之外，逐 mode 断言发言位形状**真的不同**（pipeline 有
  `predecessorId`、roundtable 议长 `chairRound=true`、vote 两者都 null）——
  **否则「三种 mode 各采一遍」只是把同一份结果抄三遍，测试照样绿。** 这一条把
  「各采一遍」从声明变成了**可判定的断言**。

**C. ⚠️ 更早的一处修正（`47693445`，上一轮）：`openGroupSession()` 让群配置丢失当场炸**

- **根因**：测试装载群会话时**没走 `openGroupSession()`**，导致**群配置丢失**，
  整轮**退化成单聊**。**这是「测试自己写错导致证据退化成另一种东西」的根因**——
  如果不修，后面所有「pipeline 三角色依次发言」的断言都是在**单聊**上通过的，
  **那份证据整个不成立**。
- ⚠️ **所以这一条必须登记**：它不是「断言放宽」，是「**证据差点采成了别的形状**」。
  凡是「跑出来的结果和契约要的不一样」的测试修正，都要按这个规格登记。

#### ⚠️⚠️⚠️ 仍然缺的（如实写「缺」，不许粉饰）

| # | 缺什么 | 到哪一步了 |
|---|---|---|
| 1 | **roundtable / vote 的真实 LLM 调用记录** | 只有**夹具层** viewer 台账（三 mode 各 6 对审计全过），**真实 HTTP 零份**。议长 `chairRound=true` 的实际 prompt 组装、vote 的 `parseBallot → __summary__` 分支都没有请求记录——mock 返回 `[mock] CASE:…`，`parseBallot` 认不出 `VOTE:` 前缀 |
| 2 | **酒馆（SillyTavern）本体打开群聊导出文件** | **零证据**。SillyTavern 侧只有零设备的结构对照（AGPL 边界下只借鉴名单顺序与整词 `@`） |
| 3 | **真机 UI 端到端** | **零证据**。群聊页整页渲染、抽屉筛选 chip 来回切换、成员头像组点击插话、@ 弹窗、扫码弹层——**一行 Compose 没上过屏** |
| 4 | **相机扫码真机链路** | `QrScannerSheet` **编译过 + 16 条 JVM 单测**，但 **CameraX + MLKit 没在设备上跑过** |
| 5 | **`BrowserRuntimeTest` 的 Coil 单例崩溃** | **未修**，全量 56 条仪器测试**跑不完**，**稳定复现**（`RouteActivity.kt:202` 的 `setSingletonImageLoaderFactory`，来自 `b5c5ebcb`，**与 C1 无关**）。已用**类过滤**覆盖 **28 条** |

⚠️ **第 3、4 项是 C1-09 与 C1-10 的实质缺口**，不是「锦上添花」：
C1-09 的「Tavern/QR 往返」缺的正是第 2、4 项，C1-10 的「切换后消息与会话数据不丢」
缺的正是第 3 项。**所以这两行尤其不能改成通过。**

### C1 真机证据采集第三轮（2026-10-05，真实公网网关：真实 token + 真实调用序列）

登记于 commit `6ba95422`（**只改 `C1LiveModelSequenceTest.kt`，+539 行 / 539 insertions、
生产代码零改动**）。⚠️ **这一节同样不改变任何用例的判定**：见下面
「⚠️⚠️⚠️ 为什么十例仍然 0/10」。

#### 怎么不用 mock 也拿到真实 usage：生产自带的免费公网网关

上一轮那节的证据等级是「**真实 provider 代码路径 + 真实 HTTP + 真实 SSE + 真实 usage
报文**」，但 usage 是 mock 按 `ceil(bytes/4)` 算的，**不是真实 tokenizer 的结果**。
这一轮把「mock」这一层也去掉，用的是**生产代码里本来就有的那个免费 OpenAI 兼容网关**
（`app/src/main/java/heizige/kk/khatkit/app/core/data/datastore/DefaultProviders.kt:281-318`）：

| 项 | 实测值 |
|---|---|
| provider 定义 | `ProviderSetting.OpenAI`，`name = "极客猫"`，`id = 5197b3ae…`，`enabled = true`、`builtIn = true` |
| baseUrl | `https://api.zenneko.top/v1`（**公网**，不是 `127.0.0.1`） |
| apiKey | ⚠️ **已预填在源码 `:285`**（`sk-…`）。**本文只记行号，不落明文**；测试也是按 id 从 `DEFAULT_PROVIDERS` 取这条定义，**不把密钥抄进测试文件** |
| 模型表 | `deepseek-v4-flash`（uuid `5a86b2d6-9c3c-4c58-9b27-f9295ba39201`）、`glm-5.2`（uuid `8b6bf21c-56d8-40fd-93c8-6d657cac71a4`，abilities 含 `TOOL` + `REASONING`） |
| 用例 | `C1LiveModelSequenceTest.realProviderRoundRecordsGenuineTokenUsage`（`6ba95422` 新增，文件在 `6ba95422` 那版为 **1982 行 / 5 个 `@Test`**，HEAD `74d82476` 已增至 **2221 行 / 5 个 `@Test`**（`@Test` 个数未变）） |
| 设备→网关 | **设备直连公网**。**不经 `adb reverse`、不经本机 mock**，测试前置自检就断言 `baseUrl.startsWith("https://") && !contains("127.0.0.1")` |
| 走的链路 | 与上一轮同一条生产链路：`ChatManager.sendMessage → GenerationLoop → ProviderManager → OpenAIProvider → ChatCompletionsAPI.streamText → Ktor CIO OkHttpClient` |

⚠️ **内置 provider 并没有开 `useResponseApi`**（默认 `false`），所以实际打的是
`/chat/completions`，usage 取自 SSE 的 `stream_options.include_usage` **收尾块**——
这一点是**真机首跑纠正的认知**（写错了会以为走的是 `/responses`）。

#### 真实 usage 与落库对账（本轮最硬的一块数字）

| 角色 | modelId uuid | 模型名 | prompt | completion | total |
|---|---|---|---:|---:|---:|
| a | `5a86b2d6…` | `deepseek-v4-flash` | **6803** | **159** | **6962** |
| b | `8b6bf21c…` | `glm-5.2` | **6667** | **68** | **6735** |
| c | `5a86b2d6…` | `deepseek-v4-flash` | **6880** | **102** | **6982** |

- **Σ(prompt+completion) = 6962 + 6735 + 6982 = 20679**，落库
  `group_runs.spent_tokens = 20679` —— **精确相等**（不是「约等于」，是逐位相等）。
- `status=COMPLETED`、`committed_role_ids=[a,b,c]`、`skipped_role_ids=[]`、
  `ended_at` 非空。
- **实际模型调用序列 = `deepseek-v4-flash` → `glm-5.2` → `deepseek-v4-flash`**，
  与「a/c 绑 `deepseek-v4-flash`、b 绑 `glm-5.2`」的按角色绑定**逐位一致**。
  这直接证明 `resolveGroupTurnModelId` + `TaskRoutes.resolve` 的**按角色选型在真实调用下
  成立**——以前这条只有 mock 请求顺序与纯函数两级证据。
  测试还**反向断言**序列里真的出现了两个不同取值，否则「有区分度」这个前提不成立、
  断言会沦为同义反复。

#### ⚠️ prompt 6800 量级的来源已定位（不是凭空一个大数）

请求体 **25.7KB** 里 **24675 字节是 23 个工具的定义**。按**同一条 wire body** 在本机
复现（同一份 tools + 同一段 system/persona）得到的 prompt 是
**6800 / 6526 / 6814**，与真机的 6803 / 6667 / 6880 **同量级**。
⚠️ 这条复现是**本机**做的、**不是 wire 抓包**，它只用来解释「为什么 prompt 是 6800 量级
而不是 200 量级」。

#### ⚠️⚠️ 两个模型都是推理模型——`max_tokens` 给小了正文就是空串

真机首跑撞出来的：`max_tokens` 给小了会 **`finish_reason=length`、`content` 为空、
token 全被 `reasoning_tokens` 吃掉**，于是 usage 断言全部失真（看起来像「模型没产出」）。
**给足配额才吐正文**，上面那张表才是给足配额后的数。
⚠️ 具体每个请求里 `reasoning_tokens` 占 `completion_tokens` 多少，**本轮没有单独落盘**，
所以别把「completion = 159 / 68 / 102」读成「正文就那么长」——`completionTokens` 是
**含推理**的口径。

#### ⚠️⚠️⚠️ 一个只在真网关上现形的坑：真模型会照抄上一位的输出格式

**mock 永远不会暴露这个坑**，因为 mock 按 system prompt 拼字符串；**真模型会**。

- **现象**：首版 persona 只埋了 `CASE:<case> ROLECODE:<code>`，**没写「历史里别人的代号
  不是你的」**。pipeline 本来就要把**上一位的发言放进下一位的上下文**，于是**真模型
  照抄眼前那条的格式**——b 学走 a 的 `ROLECODE:A`、c 学走 b 的，c 最后干脆零产出
  （`reason=role_failed`）。
- **判定**：这不是生产代码的 bug，是**测试夹具（persona）写得不够**。生产侧的
  「只把上一位输出交给下一位」是**契约本身要求的行为**（`predecessorId` 分支），不能
  为了让模型听话而改掉。
- **修法**：persona 加第 3 条禁令，显式写死「历史里别人的代号不是你的」。
- **验证到什么程度**：按**真实 pipeline 链**（a 的输出进 b 的上下文、b 的进 c 的）本机
    跑 **2 轮 × 3 角色 = 6/6 通过**。
    ⚠️⚠️ **但修完之后的真机全绿没跑到**——原因见下一节的 OEM 杀进程。
    ✅ **这条已由下一节补上**（2026-10-06 真机 `exit 0`，persona 第 3 条禁令在真模型上生效）。

#### ⚠️⚠️⚠️ 为什么十例仍然 0/10（三条硬理由 + 契约条款）

⚠️ **本节第 1 条（缺全绿记录 / 缺可核落盘产物）已在下一节「C1 真机证据采集第四轮」
被逐条消掉**；但**十行的状态列一个格仍然没动**，因为剩下两条硬理由与四条路径缺口
**一条都没被那一轮覆盖**。

1. **这个用例没有一次全绿记录。** ✅ **第四轮已消**：`exit 0`、`OK (1 test)`，三份 JSON
   拉回并记了 SHA-256。⚠️ **但这只证明 `realProviderRoundRecordsGenuineTokenUsage` 这一条
   用例绿了**，⚠️ **不等于十行里任何一行的判定改变**——下面第 2、3 条与四行路径缺口仍在。
2. **模型名的取得途径：缺陷已定位并修复，wire 名已可直接拿到；但真机未复核。**
   ✅ **订正（HEAD `74d82476`，零设备）**：这条硬理由**原先的成因已经被找到并修掉了**，
   而且修的不是「拿不到 wire 名」这件事本身，是**「拿到了却被丢弃」**：
   - `ChatCompletionsStreamDecoder.kt` 的 `finish(reason, responseId, model)` **一直正确**
     发出 wire 上的模型名（`StreamChunk.Finish` 的第三个参数）；
   - ⚠️ **缺陷在 `StreamChunkHandler`**：收到 `Finish` 时**只取 `finishedAt`**，
     把 `chunk.model` **直接丢弃**。于是助手消息上留不下网关自报的名字，
     验收证据里的 `deepseek-v4-flash` / `glm-5.2` 才是拿 `message.modelId`
     （本地 `Model` 的 **UUID**）**回查本地 provider 模型表反查**出来的。
   - 修复：`UIMessage` 加 `wireModelName: String? = null`（`f1bf516e`），
     `StreamChunkHandler` 的 `Finish` 分支落盘 wire 名、非流式路径对称
     （`0c239788`）。`UIMessage` 是 `@Serializable`，随对话 JSON 落库，**无需 Room 迁移**。
   - ✅ **零设备证据之一（JVM，`WireModelNameProvenanceTest` 9 例 / 27 处断言 / 退出码 0）**：
     SSE 帧里刻意刁钻的字面量（`deepseek-v4-flash-250528`、`zhengyimeng/GLM-5.2-preview`）
     **逐字透传**；**反查隔离**（断言 wire 名既不等于 `model.id` 也不等于
     `modelId`/`displayName`，证明两条路径互斥）；无 `model` 帧 ⇒ `null`
     （**不是空串、也不回退 `modelId`**）；非流式对称；序列化往返不丢；
     旧 JSON 无该键 ⇒ `null` 且不抛。**非空验证**：临时删掉那一行 `wireModelName = …`
     重跑 ⇒ `9 tests completed, 3 failed, EXIT=1`，恰好是 3 个流式来源用例。
   - ✅ **零设备证据之二（真实公网网关，`tools/verification/c1_wire_model_probe.py`）**：
     `deepseek-v4-flash` → wire_model `deepseek-v4-flash`（HTTP 200 / `stop` /
     prompt 15 / completion 39，其中 reasoning 16）；`glm-5.2` → wire_model `glm-5.2`
     （HTTP 200 / `stop` / prompt 23 / completion 533，其中 reasoning 502）。
     非流式（`--no-stream`）也验过 `deepseek-v4-flash` → `wire_model: "deepseek-v4-flash"`，200。
     ⚠️ **key 只走命令行/环境变量，脚本内 0 处硬编码**；实测后该 key 在
     `git diff ea6b7c4c..HEAD` 与全部 commit message 里出现 **0** 次。
   - ✅ **测试口径已改成 wire 优先**（`d7971ba1` / `63d0a504`）：
     `C1LiveModelSequenceTest` 的模型名改为 **`UIMessage.wireModelName` 优先、
     uuid 反查回退**，并加了「**不允许两者皆空**」的防退化断言；
     证据 JSON 逐条记 `wire_model_name_provenance`
     （取值 `wire_response_model` / `uuid_reverse_lookup_fallback`）、两个模型名与
     `wire_model_name_provenance_counts` / `wire_and_reverse_lookup_agree`。
   ⚠️⚠️ **但这一条仍然是「未消」，三条原因必须连着上面一起读**：
   ① **仪器测试一行都没跑过**——设备离线，新增断言、新 JSON 字段、wire 优先判定逻辑
   **全部只有编译验证**；② **设备上那份 `c1-live-evidence-real-provider.json` 里现在记的
   仍是 UUID 反查值**，要等真机重跑才会变成 wire 值——**这里没有任何真机数字**；
   ③ ⚠️ **诚实要点**：wire 名与请求名一致，说明**原先 UUID 反查出的名字恰好是对的**，
   但现在**可直接从 wire 拿到**。**这不是「反查错了被纠正」，是「取得途径从反查变成
   wire 直取」**——所以它**不构成对既有证据的追溯性升级**，只构成「下一次真机重跑可以
   采到 wire 值」的能力。
   ⚠️ **因此「序列与按角色绑定一致」这句话的准确表述仍然是**：设备上那份既有证据里，
   它证明的是「**落库/内存里的 `modelId` 与绑定一致**」，**不是**「wire 级抓到客户端
   按这个 model 串发出去」。JSON 里已用 `wire_model_name_provenance` 显式标明这一点，
   **文档这里也照记**。⚠️ 另两条取证局限同样照记：`wireModelName` **是否在任何 UI 上
   显示：没做，也没打算做**；**旧 Room 库里既有消息树（含新字段）的反序列化没对真实
   数据库跑过**（只靠 nullable+默认值 + JVM 序列化往返保证）。
3. **真机 UI 端到端仍然零份。** 一行 Compose 没上过屏，抽屉 chip、成员头像组、@ 弹窗、
   扫码弹层**全都没在屏幕上跑过**。

⚠️ **按 `beyond-operit-client-changes.md:232-235`**（「测试未运行、只看截图或只看 UI 状态
均标记 `unverified`」）与本文「判定规则」小节第 2 条（**缺可见消息集合 / 调用序列 /
哈希的行不算通过**）：

| 行 | 踩的是哪一条 |
|---|---|
| **C1-03 roundtable** | **真机或自动化证据**——议长汇总轮的真实调用这一轮仍然零份 |
| **C1-04 vote** | **真机或自动化证据**——三张选票与 `parseBallot → __summary__` 仍然零份 |
| **C1-09 Tavern/QR** | **真机或自动化证据**——酒馆本体打开、相机扫码链路零份 |
| **C1-10 单聊/群聊共存** | **真机或自动化证据**——抽屉筛选录屏零份 |

⚠️ 另外六行（C1-01 / 02 / 05 / 06 / 07 / 08）虽然这一轮拿到了真实 token，但那些行
**各自要断言的那条路径这一轮没被真实调用过**（显式 @ 收窄、取消/超时、预算截断、
失败续跑、记忆空间），所以同样**一条都不能改成通过**。
**十行状态列一个格都没动，仍是 10/10 `unverified`。**
⚠️ **第八轮订正（HEAD `31b6b5a52`，2026-10-06）：上面第 2 条的最后一组子理由（「设备上那份 JSON 仍是反查值 / 真实网关还没在 wire 优先逻辑下重跑过」）已消。** 真实网关用例已按 wire 优先口径重跑并首次产出正式证据（provenance counts `{wire_response_model:3, fallback:0}`；Σ=20731=spent），**硬理由①对真实网关路径据此划掉**（判据 = 「判定规则」第八条）。⚠️ **十行状态列仍然一格未改**——逐行依据见「C1 真机证据采集第八轮」⑨。

### C1 真机证据采集第四轮（2026-10-06，真实公网网关：**目标用例首次真机全绿 + JSON 拉回入库外**）

⚠️⚠️ **本节的落地 commit 是 `4336831f`**
（`docs(c1): 补第四轮真机记录——真实网关目标用例首次全绿，落盘 JSON 已拉回并记 SHA-256`，
1 file `implementation-status.md` +6 / `c1-group-chat.md` +124 / −12），
**不是**下面写的 `d96d64e1`——那个 SHA 是文档提交链的上一环（同一批里另一个 commit），
**这里订正，免得按 `d96d64e1` 去找第四轮的记录找不到。**

HEAD `d96d64e1`。⚠️ **生产代码零改动**（`git status` 只有另一条并行分支的
`TavernMacroExpander.kt`，与本轮无关）。本轮**只做两件事**：把
`realProviderRoundRecordsGenuineTokenUsage` 在真机上跑到全绿，并把落盘 JSON 拉回主机。

#### ✅ 全绿记录（本节是本轮唯一新增的可核产物）

| 项 | 实测值 |
|---|---|
| 设备 | **OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**（`OP5D2BL1`） |
| 指纹 | `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys` |
| 端口怎么找到的 | ⚠️ **本轮 `adb mdns services` 首查是空的**（`List of discovered mdns services` 下一条都没有），所以改用 `nmap -p 30000-49999 --open 192.168.31.183` → **`33753` 与 `46888` 两个 open**；`adb connect` 试下来 **`33753` 通、`46888` 报 `failed to connect`**。⚠️ **`46888` 正是上一轮历史端口之一**——它现在只是个**占着端口但已经不是 adb 的残留**，**端口复用会让 `connect` 静默连到错误服务**，所以「扫到 open 就当 adb」是错的，必须逐个 `connect` 验证 |
| mDNS 双注册怎么处理 | ⚠️ **再次双注册**，且这次 **`(2)` 那条指向的是 `38493`**：`adb-…-Sqr0AX (2)._adb-tls-connect._tcp` → `192.168.31.183:38493`。按上一轮记的正确写法用**完整 mDNS serial** 断：`adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'` → 这次报 **`error: no such device …:5555`**。⚠️ **这是「预期内的良性结果、不是断不掉」**：`(2)` 那条**从未成功建立过 adb 连接**（它只是 mDNS 广播），`adb devices` 里没有它，所以没有可断的 transport；真正要断的 `ip:port` 两条用 `adb disconnect 192.168.31.183:<port>` 正常断掉。**最终 `adb devices -l` 只剩 1 条**：`adb-3B6F5ME910B6H059-Sqr0AX._adb-tls-connect._tcp` |
| 内存 | `MemTotal 15568996 kB` / `MemFree 440040 kB` / `MemAvailable 5861948 kB`（⚠️ `MemFree` 只有 440MB，`MemAvailable` 5.8GB——**判 OEM 回收风险要看 `MemAvailable`，不是 `MemFree`**） |
| **跑法** | `./gradlew --offline :app:assembleDebug :app:assembleDebugAndroidTest` → `BUILD SUCCESSFUL`；⚠️ **androidTest APK 没有 per-abi 变体**，真实路径是 **`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`**（不是 `app-arm64-v8a-debug-androidTest.apk`，那个文件不存在）；两条 `adb install -r -t` 均 `Success` |
| OEM 预防 | `am set-inactive heizige.kk.khatkit.debug false`（无输出）、`dumpsys deviceidle whitelist +heizige.kk.khatkit.debug` → `Added: heizige.kk.khatkit.debug`；⚠️ **`deviceidle whitelist +…` 这条 shell 命令在 Android 16 上不存在**（`/system/bin/sh: deviceidle: inaccessible or not found`），**走 `dumpsys deviceidle whitelist` 才是可用的** |
| **class 过滤写法** | ⚠️ **用了单方法过滤**（不是整类）：`-e class 'heizige.kk.khatkit.app.feature.chat.C1LiveModelSequenceTest#realProviderRoundRecordsGenuineTokenUsage'`。**引号是必须的**——`#` 与 `$` 在未加引号的 `sh -c` 里会被当注释/变量展开 |
| **`am instrument` 真实退出码** | **0**。`numtests=1`、`INSTRUMENTATION_CODE: -1`（正常收尾）、`Time: 16.002`、`OK (1 test)` |
| **本轮 OEM 回收有没有发生** | ⚠️ **没有观察到发生**。`c1-live-trace.txt` 显示 `real:preflight-begin 1791217815753` → `real:await-messages-done 1791217831513`，**整轮存活 15.6 秒**就正常走完，比上一轮记的「34-44 秒被杀」**短得多**；且 `c1-live-evidence-real-provider.json` 与 `c1-real-raw-dump.json` **都在断言之前就写完了**（前者是全部断言通过后才写的），**进程被杀写不出这两份**。⚠️ **诚实边界**：本轮**没有单独 dump `logcat -b crash`**，所以「无崩溃」这句的依据是**产物齐全 + 退出码 0**，**不是**崩溃缓冲区的直接读数 |
| 三条产物是否拉回 | ✅ **全部 3 个拉回**（见下表）。⚠️ 本轮**跑完 instrument 立刻 `adb pull`**，没有再碰 `connectedAndroidTest`，所以**外置目录没被卸载清空** |

#### ✅ 落盘 JSON 已拉回主机（带 SHA-256，可核）

⚠️ 按「产物写 `/tmp/opencode/final-real/`」的约束，**这三份留在仓库外**，不入库；
**SHA-256 记在这里，仓库外这份才有可核性**。

| 文件 | 字节 | SHA-256 |
|---|---:|---|
| `c1-live-evidence-real-provider.json` | 7995 | `55d1297733838de2dd6b1fd0908b72dd2b9069a2edbd4c896fd4069edfdbfd40` |
| `c1-real-raw-dump.json` | 2875 | `e6f97d441e9014f8bdde1e8d8d73b6947000c7a5dc24ffcedaa3689ad490ddbe` |
| `c1-live-trace.txt` | 48195 | `7d7bb85f82a14b733464c0981b5d471a9791e630b15d8caea715c616a6ebc462` |

路径 `/tmp/opencode/final-real/pull/`（另有一份 `pull-backup/` 副本）。⚠️
**上一轮记的「`c1-real-raw-dump.json` 未入库、也没 pull 回来」到此作废**。

#### ✅ 真实 usage 与落库对账（本轮这一跑的数字，与上一轮**不是**同一组）

| 角色 | modelId uuid | 模型名 | prompt | completion | total |
|---|---|---|---:|---:|---:|
| a | `5a86b2d6…` | `deepseek-v4-flash` | **6839** | **92** | **6931** |
| b | `8b6bf21c…` | `glm-5.2` | **6645** | **58** | **6703** |
| c | `5a86b2d6…` | `deepseek-v4-flash` | **6904** | **242** | **7146** |

- **Σ(prompt+completion) = 6931 + 6703 + 7146 = 20780**，落库
  `group_runs.spent_tokens = 20780` —— **精确相等**。逐条 `total == prompt + completion`
  也已独立复算通过（`6839+92`、`6645+58`、`6904+242` 三条全对）。
- ⚠️ **这组数与上一节那张表（20679）是两次独立真实调用，天然不同**——**不要把两组的数字
  混着引**，也不要把「两轮数字不一样」当成不稳定：变的是**网关每次返回的 token 计数**，
  不变的是**Σ 与 `spent_tokens` 逐位相等**这条对账关系。
- `status=COMPLETED`、`committed_role_ids=[a,b,c]`、`skipped_role_ids=[]`、
  `token_limit=100000`、`ended_at=1791217831462`、`reason=""`。
- **实际模型调用序列 = `deepseek-v4-flash` → `glm-5.2` → `deepseek-v4-flash`**，
  与「a/c 绑 `deepseek-v4-flash`、b 绑 `glm-5.2`」**逐位一致**。
- ⚠️ **模型名仍不是 wire 级抓包**，理由与上一节第 2 条**完全相同**（app 不保存响应的
  `model` 字段，`wire_model_name_provenance` 字段照旧标明「由 uuid 反查」）。**这一条
  没有因为全绿而改变。**

#### ✅ persona 第 3 条禁令在真模型上**第一次**被验证

上一节记的那个坑（「真模型会照抄上一位的输出格式」）的首版修法是 **persona 加第 3 条禁令**。
本轮是**修完之后第一次真机跑**，三份正文分别是：

| 角色 | 正文（原文） | 抽出的 ROLECODE |
|---|---|---|
| a | `ROLECODE:A 我已就位，等待指令。` | `["A"]` |
| b | `ROLECODE:B 我已就位，准备就绪。` | `["B"]` |
| c | `ROLECODE:C 轮到我，已确认就位。` | `["C"]` |

**每个角色只出现自己的代号，没有一个学走上一位的**——断言 `codes == listOf(ownCode)`
（不只是 `ownCode in codes`）在真模型上**通过**。⚠️ 这**只证明这一个 pipeline 顺序下
没有串码**，**不等于**「换顺序/换模型/多轮」也一定不串；那仍需另外的实跑。

#### ⚠️ 本轮回归四条的诚实交代

| 命令 | 退出码 | 结果 |
|---|---:|---|
| `:app:testDebugUnitTest --rerun` | **0** | **99 类 / 783 例 / 0 失败 0 错误 0 跳过** |
| `:app:assembleDebug` | **0** | `BUILD SUCCESSFUL` |
| `:app:lintDebug` | **0** | `BUILD SUCCESSFUL` |
| `:app:connectedDebugAndroidTest` | **1** | ⚠️ **9 过 1 挂后中止**，挂的是**已知的 `BrowserRuntimeTest`**（`threeTabsCanTypeClickAndReadIndependentLiveSnapshots`），`java.lang.IllegalStateException: The singleton image loader has already been created.` → `coil3.compose.SingletonImageLoadersKt.setSingletonImageLoaderFactory(singletonImageLoaders.kt:17)` ← `heizige.kk.khatkit.app.RouteActivity.onCreate$lambda$0$0(RouteActivity.kt:202)`。⚠️ **来自 `b5c5ebcb`、不是本轮改动**（本轮生产代码零改动，`git status` 可核），**未修**，如实登记 |

⚠️ `connectedDebugAndroidTest` 那次**只产出 1 个 XML**（`tests=10 failures=1`），因为
Coil 崩溃把整轮**中途**打断了——所以 ⚠️ **「全量仪器用例数」这一轮拿不到**，
**不要把 `10` 当成全量条数**。

### ⚠️⚠️ 设备 OEM 回收策略会杀 instrumentation 进程（本轮新踩的坑）

| 项 | 实测 / 观察 |
|---|---|
| 现象 | `heizige.kk.khatkit.debug` 进程存活约 **34-44 秒**时被系统杀掉，instrumentation 报 `Process crashed` |
| 触发方 | OnePlus 的 **OsenseKillAction / NirvanaLowFree**（`appcareThreshold=79`），**不是** Linux OOM killer |
| 当时的内存账 | app 占 **313MB**，设备 `MemAvailable` **3.4GB**——设备当时被**用户自己的应用占满**（游戏 1.7GB、抖音 1.4GB）。⚠️ **所以这不是真 OOM**，是 OEM 的后台回收策略 |
| 波及范围 | ⚠️ **既有 mock 用例同样被杀**——单跑最快的 `budgetTruncation…` 也是 `Process crashed`。**所以与真实网关、与本轮改动都无关** |
| 全量后果 | `:app:connectedDebugAndroidTest` 全量报 `Process crashed`、**0 个测试启动**（`tests="0"`），**连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到** |
| 处置 | 按约束「**OOM 立即停止重试**」共试 **11 次**后停止。⚠️ **这是设备环境的限制，不是代码缺陷**；要复跑必须先把设备上那些应用清掉，或换一台/换一个用户 |

⚠️ **顺带把上一轮那条 mDNS 坑补全——「具体怎么断」**（上一轮只说了「要 disconnect 掉
`(2)` 那条」）：

```
# 错：adb disconnect 192.168.31.183:<端口>   →  no such device
# 对：用完整的 mDNS serial
adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'
```

**为什么**：上面那条错命令用的是 `IP:端口`，而 `adb devices` 里那两条的 serial **是 mDNS
名**（`adb-<序列号>._adb-tls-connect._tcp` 形式），`adb disconnect` 只认 serial。
⚠️⚠️ **而且它会反复自己注册回来**——disconnect 之后过一会儿同一个 `(2)` 又出现，
所以**每次 instrument 之前都要重新确认 `adb devices -l` 只剩 1 条**。

### 群配置面板两个真机必崩已修并真机验证（`33eb801e` / `5d58f923` / `71d1439e` / `54c196ca`）

⚠️ **这一节的两个崩溃栈是用户实测给的原始栈**，不是本文自己在旧 APK 上复现出来的
（见下面「诚实边界」第 ③ 条）。修法与真机复验都做了，**但它们是 UI 缺陷修复，不是契约
`:206` 点名的任何一类产物**，所以**十例状态一个都没变**。

#### 崩溃①：`IllegalArgumentException: PrimaryBottomSheet 需要 painter 或 imageVector 之一`

- **现场**：`PrimaryBottomSheet.kt:76` 的
  `imageVector = requireNotNull(imageVector) { "PrimaryBottomSheet 需要 painter 或 imageVector 之一" }`
  ← `GroupChatPage.kt` 里的 `GroupConfigSheet`。触发条件是**两个参数都不传**。
- **为什么会崩**：`PrimaryBottomSheet` 按 `LocalKedgeStyle.current` 分派，`KedgeStyle.MD3Exp`
  分支下 Khromia 有 `painter` 与 `imageVector` **两个重载、二选一必填**，转发层对后者用
  `requireNotNull`（`PrimaryBottomSheet.kt:57-77`）。`GroupConfigSheet` 一个都没传。
  ⚠️ **`KedgeStyle.Miuix` 分支不走这条 `requireNotNull`**——它把可空的 `imageVector`
  原样转给 `KedgePrimaryBottomSheet`（`PrimaryBottomSheet.kt:41-53`），所以这个崩溃
  **只在 MD3Exp 风格下存在**。这是读代码得出的，不是两种风格都实测过（见诚实边界①）。
- **修法（`33eb801e`）**：补 `imageVector = tune`。**为什么选 `tune` 而不是 `painter`**：
  与顶栏触发按钮 `GroupTopBar` 里的 `Icon(tune, "群配置")` 同款，触发按钮与它打开的弹层
  图标一致、语义闭合；走 `imageVector` 这一侧是因为**全仓 62 个调用点全都用它**
  （`grep -rn "PrimaryBottomSheet(" app/src/main` = 66 处命中，减去定义文件里那 4 处）。
- **护栏（`5d58f923`）**：`PrimaryBottomSheetIconSourceGuardTest` 3 例——① 全仓每个调用点
  都必须传图标 ② **扫描器自身仍覆盖全仓**（防止把扫描范围改小而恒绿）③ 群配置面板定点
  回归（必须传 `tune`）。

#### 崩溃②：`IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`

- **根因**：**两层纵向滚动容器叠加**。`GroupConfigSheet` 自己套了
  `Column(Modifier.verticalScroll(...))`，却漏了 `PrimaryBottomSheet` 的 `scrollable = false`。
  而 `scrollable = true`（默认值）时 Khromia `BasicBottomSheet` 自己就给内容套了一层
  `verticalScroll`（`Column(Modifier.weight(1f, fill = false).then(if (scrollable) Modifier.verticalScroll(...) else Modifier))`），
  `verticalScroll` 是拿 `maxHeight = Infinity` 去量内容的，于是内层 `ScrollNode` 直接抛。
- **修法（`71d1439e`）**：**只加一行 `scrollable = false`，不删内容的滚动**。
  **为什么保留内容那层滚动、而不是反过来删掉它、把滚动交给弹层**（这是本节最需要写下来
  的一个判断）：面板内容本来就长（九个配置字段 + 每个角色一个 `RoleEditor` + 导入导出
  两段），**整块表单应该一起滚**；而 `scrollable = true` 时 Khromia 那层带
  `weight(1f, fill = false)`，滚动语义归弹层会让内容高度受权重影响。两种组合二选一，
  **两个都留着必崩、少一个滚不动**——注释里把这条写死，免得下一个人「顺手」改回去。
- **护栏（`54c196ca`）**：`BottomSheetScrollSourceGuardTest` 3 例——① 全仓没有任何调用点
  在 `scrollable` 默认为 true 的弹层里再套自己的 `verticalScroll` ② 扫描器仍能找到
  「自带纵向滚动的调用点」（反空跑）③ 群配置面板定点回归（自带滚动且 `scrollable = false`）。

#### 真机复验（`KedgeStyle.MD3Exp`，逐项照记）

| 项 | 结果 |
|---|---|
| 崩溃缓冲区 | `adb logcat -c -b crash` 清空 → 开面板 + 滚动 → `adb logcat -d -b crash -t 200` **输出为空** |
| 全量 logcat grep | 两个异常串在完整 logcat 里 **0 命中** |
| 面板是否真渲染出来 | `uiautomator dump` 确认 15 项全在：协作模式 / pipeline·roundtable·vote / 每轮预算 / revision / schema_version / 平票策略 / 投票候选 / 议长 / model_id / card_id / 移除 #0 / 添加成员 / 保存群配置 / 导出分享生成二维码 / 导入恢复扫码导入 |
| 滚动稳定性 | 面板内 **5 次滚到底 + 3 次反向**不崩；pid 恒定、focus 恒为本 app |
| 截图 SHA-256 | 打开态 `4762148de29b9719…`、滚动态 `3ff28637bb6e0546…` |

⚠️ **这一节的四条诚实边界，逐条照记**：
① **Miuix 风格完全没验**——两个崩溃都只在 `KedgeStyle.MD3Exp` 触发（这一条是**读代码**
得出的：Miuix 分支不走 `requireNotNull`、且 `PrimaryBottomSheet` 的 KDoc 写明 Kedge 版
「靠内容自身滚动，忽略该参数」），**Miuix 下这两个面板是不是也崩过、或者行为不同，
零证据**；② 其余 **62 个 `PrimaryBottomSheet` 调用点**（`app/src/main` 里 `grep -rn "PrimaryBottomSheet("` = **66** 处命中，减去定义文件 `core/ui/components/ui/PrimaryBottomSheet.kt` 里那 **4** 处（1 个声明 + 3 个委派调用）= **62**；⚠️ **把 `src/test` / `src/androidTest` 也算进去是 68**。⚠️ **「64」这个数在两种口径下都复现不出来，本轮按实测的 62 登记**）靠源码扫描 + 既有单测，**没有逐个
上真机**；③ **没有在旧 APK 上先复现崩溃再对比修复**——原始栈是用户实测给的，所以
「修完不崩」与「修的就是这条栈」之间靠的是**栈帧行号对得上**（`PrimaryBottomSheet.kt:76`），
不是受控对比；④ 面板里的**保存 / 生成二维码 / 扫码导入这三个业务动作一个都没点过**，
只验了渲染与滚动。

⚠️⚠️ **护栏自己踩的坑，值得单独记一条（它是可复用的教训）**：
`PrimaryBottomSheet` 的 `content` 是**尾随 lambda**——**大括号在右括号外面**。第一版
护栏用「括号配平」切出实参区间，于是**整段内容体被漏扫**，`content` 里那个
`verticalScroll` 根本没进扫描范围，**主断言因为「谁都不违规」而恒绿**。
⚠️ **是靠同一批里另一条「反空跑」断言抓出来的**（先确认扫描器还能找到违规样本），
不是靠主断言自己。**教训：源码文本护栏必须自带一条「扫描器仍能找到东西」的断言**，
否则扫描范围一旦被改窄，整批断言会静默变绿。

### Coil 单例崩溃的结构性修复（`ea482935` / `2766afc7`）

⚠️ **本轮按约束没碰设备，所以这一节的修复效果在真机上是零证据。** 它修的是「已知遗留与
风险」第 12 条那条**稳定复现**、让 `:app:connectedDebugAndroidTest` 全量跑不完的崩溃。

#### 现场（磁盘上那份 XML 是可核的）

`app/build/outputs/androidTest-results/connected/debug/TEST-PKG110 - 16-_app-.xml`
（mtime `2026-10-06 00:33`）：`tests="10" failures="1" errors="0" skipped="0"
time="24.996"`。挂的是 `BrowserRuntimeTest`：

```
java.lang.IllegalStateException: The singleton image loader has already been created.
  at coil3.SingletonImageLoader.setSafe(SingletonImageLoader.kt:41)
  at heizige.kk.khatkit.app.RouteActivity.onCreate$lambda$0$0(RouteActivity.kt:202)
```

#### ⚠️ 根因不是 `b5c5ebcb`——这一点必须纠正

`git show --name-only b5c5ebcb | grep -i coil` **零命中**：那个 commit 一行 Coil 都没碰。
全仓配置 Coil 的地方**只有 `RouteActivity` 一处**。真正的因果链是**执行顺序**：

1. 先创建 loader 的是 **`NodeTreeSmokeTest`**（`androidTest/.../NodeTreeSmokeTest.kt`
   第 24 行 `@get:Rule val compose = createComposeRule()`，**不带 Activity** 渲染
   `AsyncImage`）。它在**同一个 instrumentation 进程**里先跑 `get()`，把**内置默认**
   loader 钉死进那个进程级 `AtomicReference`。
2. 随后 `BrowserRuntimeTest`（`b5c5ebcb` 加进来的，第 26 行
   `@get:Rule val activity = ActivityScenarioRule(RouteActivity::class.java)`）拉起
   Activity，`onCreate` 的组合里同步调 `setSingletonImageLoaderFactory` → `setSafe` 当场抛。
3. 它挂在 `super.onCreate()` 之后、`setContent` 装内容之前 → **整个 instrumentation
   进程死掉**，Gradle 只收到前 10 条结果。

⚠️ **所以 `b5c5ebcb` 只是加了 2 个 androidTest 类、改变了执行顺序，把一个一直存在的
隐患顶出来了。** 早前把它记成「来自 `b5c5ebcb`」是不准确的，本节订正。

Coil 版本 **3.6.3**（`gradle/libs.versions.toml:24`）。`setSafe`（反编译确认）：
`if (value is ImageLoader && value.isDefault) error(...)`；`newImageLoader` 的解析顺序是
「已存 Factory → `applicationContext as? Factory` → 内置默认」。

#### 修法：定义点从 Activity 搬到 Application

`ImageLoaderFactory` 从 `RouteActivity.onCreate` 的组合里搬到
**`KhatKitApp : Application(), SingletonImageLoader.Factory`**（Coil 3 里
`ImageLoaderFactory` 的替代品），组件清单**原样保留**：`crossfade(true)` /
`KtorNetworkFetcherFactory(httpClient = { ktorHttpClient })` / `SDK_INT >= P` 用
`AnimatedImageDecoder.Factory()`、否则 `GifDecoder.Factory()` /
`SvgDecoder.Factory(scaleToDensity = true)`。`RouteActivity` 里删掉
`setSingletonImageLoaderFactory`，以及**随之变成死代码**的
`@Inject lateinit var ktorHttpClient` 和 7 个 coil import。

⚠️ **为什么不用「条件判断绕过」那种更省事的写法**
（`if (还没建) setSingletonImageLoaderFactory { ... }`）：正常启动下那样确实不崩，但
**在测试进程里语义是错的**——先建的是**内置默认** loader，于是自定义配置（Ktor 网络栈 +
GIF/SVG 解码）被**静默跳过**，`AsyncImage` 不崩，跑的却是另一套配置。
**等于把崩溃换成更难查的行为不一致。** 搬到 Application 之后「谁先跑」这个问题不成立：
`get()` 一定拿到这份自定义 loader。

Hilt 时序已核实：`inject()` 早于 `Application.onCreate`；merged manifest 的
`InitializationProvider` 只初始化 EmojiCompat / ProcessLifecycle / ProfileInstaller，
**无一碰 Coil**。

#### 护栏（`2766afc7`）：`CoilImageLoaderSourceGuardTest` 6 例

扫 app + **所有兄弟模块**的 main/test/androidTest，钉六条：① 全仓 **0 处**晚设 singleton
② Activity 文件不得碰定义 ③ 定义**恰好 2 处**（接口声明 + `newImageLoader` 实现）且同在
`KhatKitApp.kt` ④ **崩溃现场那个文件（`RouteActivity.kt`）0 命中** ⑤ 搬家**没丢组件**
（crossfade / Ktor / GIF / SVG 四项逐个断言在 `KhatKitApp` 里）⑥ **把因果链本身钉成
断言**——`NodeTreeSmokeTest` 仍用 `createComposeRule` + `AsyncImage`、`BrowserRuntimeTest`
仍用 `ActivityScenarioRule`。第 ⑥ 条是关键：没有它，将来有人把其中一个测试改掉，
这条崩溃就会「不再复现」而没人知道因果链断了。

⚠️ **护栏踩的坑（第一版护栏对「搬回 Activity」这个变异是绿的，是变异检验逼出来的）**：
- (a) 正则里的 `(?<![\w.])` 负向后行断言**把全限定写法
  `coil3.compose.setSingletonImageLoaderFactory` 整条漏掉**——变异因此判绿；
- (b) 只认 `(` 会漏掉 `@Composable` 的**尾随 lambda** 写法
  `setSingletonImageLoaderFactory { context -> … }`。
- ⚠️ **(b) 与上一节那个「尾随 lambda 大括号在右括号外面」的坑是同一类**，
  两处都记在这里，方便下一个人当成一条通用规则：**扫 Compose 调用点不能靠括号配平**。

⚠️ **本节最重要的一句：本轮零设备。** 要验证这一节的修复，只需要跑一条命令：

```
./gradlew --offline :app:connectedDebugAndroidTest
```

预期：`app/build/outputs/androidTest-results/connected/debug/` 里那份 XML 的
`tests` 落在 **≈61**（当前被跟踪的 `androidTest` `@Test` 数）、`failures="0"`、
**不再出现上面那个异常**。⚠️ **在跑之前，上面所有关于「修好了」的表述都只是代码层结论。**

### 四项代码债收口（`fa36c65c` / `06cf6181` / `bed09118` / `a74c1820`）

⚠️ **四项里有三项的运行时行为是纯运行时、零证据。** 三条 gradle 命令（编译 + 单测绿）
只证明「能编译、单测通过」，**不构成任何契约验收证据**——护栏全是**源码文本级**的。

| # | commit | 改了什么 | 边界 |
|---|---|---|---|
| ① | `fa36c65c` | `ChatService.handleMessageComplete` 顶部 `require(!isGroupConversation(...))`（**fail-loud**，抛错前置于一切副作用）；provider `provideChatService` → `provideChatManager`（按产物改名） | ⚠️ `require` 是否真抛属运行时，零设备 |
| ② | `06cf6181`（前半） | `abandonDanglingGroupRuns` 从 `limit = 8` 改成**分批循环读到清空** | ⚠️ 分批循环的真机行为零证据 |
| ③ | `06cf6181`（后半） | `onSuccess` 的 `null ->` 分支改成 `clearGroupRunMirrorIfMine(conversationId, groupRunToken)`，内部用 `ConcurrentHashMap.remove(key, value)` 两参重载 | ⚠️ 真实并发效果零证据 |
| ④ | `bed09118`（`GroupForkDisableReasonSourceGuardTest`） | 给 `ChatMessage.groupChat` 那段 KDoc 加 **2 例防回退护栏**，**KDoc 一个字没改** | 见下 |

#### ① `ChatService` 第二份无群聊感知的生成管线 → fail-loud

- **可达性核实结论比原判断更彻底**：`core/service/ChatService.kt`（`fa36c65c` 之前
  **1444 行**，之后 **1492 行**）全仓只有 **2 处注入**
  （`HistoryVM.kt:28` 只调 `toggleConversationPinned`、
  `core/service/ChatGenerationForegroundService.kt:71` 只调 `stopGeneration`），而**这两个
  注入点本身也不可达**——`HistoryPage(vm = hiltViewModel())` 没被任何路由引用，
  `ChatGenerationForegroundService` **不在 `AndroidManifest.xml`** 里（清单只注册了
  `.feature.chat` 那个，它注入的是 `ChatManager`）。所以 `ChatService` 当前**零活跃构造点**。
- **闸门为什么放在 `handleMessageComplete`**：`generationLoop.generateText` 在
  `ChatService` 里只有 **1 个调用点**（在 `handleMessageComplete` 内），闸门放在这里就让
  「群聊经 `ChatService` 生成」在**结构上**不可能，而不是只靠调用方自觉。
- **为什么既不复制群聊逻辑、也不委托**：
  - **复制 = 第二个真相来源**，而且漂移是**无声**的——所有现有群聊用例都**直接调
    `viewerMessages`、从不经生成入口**，所以一旦泄漏，**零测试会红**；
  - **委托不可行**：`ChatService` 是 `@Inject` 单独构造的、**拿不到那个
    `@Singleton ChatManager`**，硬接等于让两个各自持有 job 账本的类并存，比现在更糟。
- **命名陷阱是真的**：标识符 `chatService` 在仓库里指向**两个不同的类**。provider 按产物
  改名消掉这一半，`ChatManager.kt:249` 的 KDoc 也同步更新。

#### ② `abandonDanglingGroupRuns` 的 `limit = 8`

- **问题形状**：`listByConversationAndStatus` 的 SQL 是
  `ORDER BY started_at ASC LIMIT :limit`（`GroupRunDAO.kt:51`）——那是**一页**，却被当成
  「总量上限」用，于是**第 9 条起永远停在 `RUNNING` 占位**。
- **修法**：**分批循环读到清空**。`ABANDON_BATCH_SIZE = 8` 只作**分页大小**，
  `ABANDON_MAX_BATCHES = 64` 只作**收敛上限**（最多 512 行）；每行判死前
  **用 `findByRound` 重读一次**并 `isTerminal` 跳过（分批循环期间那一行可能已被别的路径
  收尾，`terminal()` 不判终态，盲目写会把别人的终态抹成 `CANCELLED`）。
- **论证**：契约要的是**清空**不是「处理一部分」；**留硬上限的唯一理由是保证收敛**——
  同会话在本函数执行期间又抢占成功一轮时（残留旧 job 走到 `takeGroupTurn`）行数不单调
  减少，无界循环会自旋，**那比留占位更糟**。撞上限的残余会在下一次发消息时再被扫掉。
- ⚠️ **顺带订正原注释一处误导**：早前注释写「同样的常量也出现在 `:853`」，
  **实际 `:853`（`fa36c65c` 之后行号漂到 `:872`）是 `searchMemories(limit = 8)` 的记忆
  条数上限，语义无关**——只是碰巧同值。

#### ③ `onSuccess` 里 `null -> groupRunsInFlight.remove(...)`

- **为什么它危险**：镜像装别人令牌的窗口只有一个——那条路里 `failGroupTurn`
  **因归属拒收而提前 return、不清镜像**，此时镜像装的是**新轮令牌** → 无条件 remove
  抹掉它 → 新轮取消不掉（`cancelActiveGroupRun` 第一行就 `remove` 不到东西、直接 return）、
  `takeGroupTurn` 拿不到 `expectedRunToken` → **整轮卡死**。
- **修法**：`clearGroupRunMirrorIfMine` 内部用 `ConcurrentHashMap.remove(key, value)`
  **两参重载**（原子、值相等才删）。
- **既有兜底为什么没被破坏**：核实 `commitGroupTurn` 返回 `null` **只有一种形状**
  （`produced == null`），其余都返回 `Advance.Halted`。`produced == null` 那条路里
  `failGroupTurn` 在**归属成立**时自己已清镜像，剩下的兜底只为「提前 return」而存在——
  而那两种提前 return 里镜像装的**正是本轮令牌**，按令牌比对照清不误；令牌为 null
  （理论不可达）时**宁可不删**。

#### ④ `ChatMessage.kt` 的 KDoc：**文档一个字没改，只加了护栏**

⚠️ **本文此前给的描述已过期，所以这次没改一个字。** 提交 `361c7cf6`（已是 HEAD 祖先）
就已把它改成正确的写法：先讲**前两个动作**（重新生成 / 删除）的账目错位，再写
「『创建分支』的**理由不一样**，别顺着上一句一起理解」+「对源会话只读…**破坏不了**
群运行日志的账面」+ 真实禁因「**静默换会话类型**」。本轮实读确认
`createForkConversation`（`ChatManager.kt:172` 附近）确实是：新 `Uuid`、**不复制**
`groupConfig` / `type`、只 `saveConversation(新 id)`。
**这次只加护栏防止回退**：`GroupForkDisableReasonSourceGuardTest` **2 例**——
① fork 段落不许把账目错位算到 fork 头上 ② **前两个动作必须保留它们自己的账目错位
理由**（姊妹例，防止有人把整段删成一刀切）。⚠️ 护栏钉的是**禁因的归属**不是逐字文案，
措辞可以改、归属不许回退；实测 M5 把假的账目错位写回 KDoc 时**变红**，姊妹例保持绿。

#### 本批护栏（照 `*SourceGuardTest` 范式，源码文本断言——仓库 `testImplementation`
只有 junit，没有 Robolectric / room-testing，这些路径构造不出来）

| 护栏类 | 例数 | commit |
|---|---:|---|
| `ChatServiceGroupChatFailLoudGuardTest` | **4** | `bed09118` |
| `GroupRunAbandonDrainSourceGuardTest` | **5** | `bed09118` |
| `GroupStaleJobCommitSourceGuardTest` | 7 → **9**（+2 例） | `bed09118` |
| `GroupForkDisableReasonSourceGuardTest` | **2** | `bed09118` |

⚠️ **变异 M1–M5 全部 `EXIT=1`**，每次都用 `cp` 备份 + `sha256sum -c` 双向校验还原，
**全程未用 git checkout / stash / reset**。

⚠️ **本批的诚实边界，照记**：**①②③ 的运行时行为全是零证据**——`require` 是否真抛、
分批循环在真机上的实际行为、`remove(key, value)` 的真实并发效果、以及
「`produced == null` 时 `failGroupTurn` 提前 return」的具体触发条件，**一条都没在真机上
观测过**。**这三条 gradle 命令不构成任何契约验收证据。**
⚠️ **本轮新发现、未动手**：`ChatService.finishInterruptedPendingTools` 里**三处**
`providerHandler.generateText` 同样不群聊感知，而且**从 `stopGeneration` 可达**。
修它要先定产品行为（群聊下**跳过** vs **做成群聊感知**），**两者都改运行时行为、都需要
真机**，所以只登记不动（已写进「已知遗留与风险」第 28 条）。
⚠️⚠️ **订正（2026-10-06，第七个窗口，HEAD `72a5e548`）：上面这一段本身是错的**——
`finishInterruptedPendingTools` 里**零 `generateText`**，而 `stopGeneration` 到不了
`ChatService`（`ChatViewModel.kt:63` 的 `chatService` **类型是 `ChatManager`**）。
**根因是 `ChatService.kt:653` 那句注释写错了**（已由 `8111d178` 修），错误从这里扩散
进了验收文档。证伪依据逐条见「已知遗留与风险」**第 28 条**；**真正**的标题/摘要群聊
泄漏在 `ChatManager` 上，已由 `4ee1ad5c` 修、登记为**第 33 条**。**原文照留不删。**

### C1 内核四批补测与修复（2026-10-05：P0 保留值 / P1 覆盖盲区 ×2 / P2 探针实测不可达）

这四批的共同点是**先把「真缺」核实清楚再动手**，而且**结论包含「不改」**。
⚠️ **它们产出的是代码层覆盖与修复，不是契约 `:206` 点名的验收证据**，
所以**十例状态一个都没变**。

#### P0（`af104850`）：`validate` 拒绝 `role_id == SUMMARY_ID`

- **漏洞**：`GroupChat.visibleMessages`（`core/data/model/GroupChat.kt:557-577`）有一条
  `message.roleId == SUMMARY_ID -> true` 的放行分支。`SUMMARY_ID = "__summary__"`
  （`:211`）是合成节点的保留 `role_id`，**任何 viewer 都看得见**。如果用户把某个角色的
  `role_id` 填成 `__summary__`，**那个角色的所有发言就会被无条件放行给所有其他角色**——
  一条完整的伪造路径。
- **修法**：在 `validate`（`:444`）加**字段级错误**拒绝它，field 用
  **`roles[].role_id`**（与下面「重复值」那条同口径），错误文案
  `role_id 不能是保留值 __summary__`。新增 4 条 JVM 用例（`GroupChatTest` **18 → 22**）：
  `validate` 直接拒绝、`importShare` 走第 5 道闸门也拒绝、**普通 role_id 仍通过且常量
  没被动过**、**两种合成小结对每个 viewer 仍然可见**（护栏，防修过头）。
- ⚠️⚠️ **只收口在 `validate`、没动 `visibleMessages`，这是刻意的**（结论已写进 KDoc
  `:541-556`）：查证过——若给 `SUMMARY_ID` 分支加上 `&& turnKind == TURN_VOTE_SUMMARY`，
  **投票失败摘要**（`ChatManager.voteFailureNode`（`:2035`）造的是 `TURN_ERROR`，
  正文是**用户可见的**「[投票] 本轮未能得出结论：…」）会**对所有视角一起消失**。
  **那是拿一个可见性回归去换一条已经堵死的路径**，不划算。
- ⚠️ **可达性核实（评审说「可手输」不准确，已订正）**：`GroupConfigSheet` 的 `RoleEditor`
  （`feature/chat/GroupChatPage.kt:746`，`:754`）把 `role_id` 渲染成**只读 `Text`**，
  整个组件**只有 4 个输入框 + 1 个 chip**：name（`:759`）/ assistant_id（`:764`）/
  model_id（`:769`）/ card_id（`:774`），加一个「议长」`KedgeFilterChip`（`:779-783`）
  与一个「移除」按钮；
  「添加成员」自动生成的是 **`"role-${draft.roles.size + 1}"`**（`:637`），**不是 UUID**。
  **所以 UI 手输路径根本不存在。**
  **唯一真实入口是外部 JSON 导入**：`importShare` 的**第 5 道闸门**确实调 `validate`
  （`GroupChat.kt:832-838`，`validate(config, conversationId)` 在 `:835`）。
- ⚠️⚠️ **已知遗留（本轮未修）**：`TavernChatCodec.importGroup`
  （`core/data/ai/tavern/TavernChatCodec.kt:257`）调 `decodeConfigObject`（`:268`）
  但**完全不 validate**。⚠️ **main 源码里当前没有调用方，所以暂不可达**——
  这是「已堵住但入口没堵严」的形状，登记为遗留而不是缺陷。

#### P1（`9844cbc4`）：`mention_role_ids` 放行分支的 JVM 覆盖

- **先核实「原测试真没覆盖」**（不是先写用例再找说法）：既有两处都把 `@` 挂在
  **USER** 消息上——`GroupChatTest:25-41`
  （`UIMessage.user("@Bob 看一下").copy(mentionRoleIds = mentions)`，`:30`）与
  `GroupTurnCoordinatorTest` 的对应用例。⚠️ **USER 消息本来就无条件放行**
  （`visibleMessages` 的 `message.role == MessageRole.USER -> true` 分支，`:568`），
  **根本碰不到 `viewerId in message.mentionRoleIds` 那一支**（`:571`）。
- **结构性原因**：该文件的 `assistant()` helper **没有 `mentionRoleIds` 参数**，
  所以写不出「一条**助手**消息被 @」的夹具。
- **修法**：**新增** `speaking()` helper（带 `mentionRoleIds`），**`assistant()` 一个
  参数都没加**（不改动既有 helper，避免影响其它用例）。新增 **3 条**用例
  （`GroupTurnCoordinatorTest` **63 → 66**）：**三向全断**（作者看得到 /
  被 @ 的看得到 / 无关者看不到）+ **反向对照**（把 `mentionRoleIds` 去掉，
  被 @ 的那位立刻又看不到了——证明这条分支真的是它放行的）。

#### P1（`e7ad2a77`）：视角过滤的四个覆盖盲区（全部确认**真缺**）

`GroupChat.visibleMessages` 只有 7 个分支，盲区有四个，前三个都是**先核实再写**：

| # | 盲区 | 为什么真缺 | 补法 |
|---|---|---|---|
| ① | **议长没进遍历** | 既有 roundtable 用例是**四人配置**（chair + 3 人）但 `memberIds` **硬编码三人**（`GroupTurnCoordinatorTest:935`），议长那条 `chairRound` 分支从没作为 viewer 被断言过 | 议长也进双重循环，并断言「议长多看到的那些是 `chairRound` 带来的，不是身份带来的」 |
| ② | **跨轮负向断言缺失** | 既有全是**单轮**，`roundStart` 恒为 0 | 加两轮夹具，断言「议长看得到本轮、看不到上一轮」 |
| ③ | **同角色多条消息零覆盖** | 既有每个角色恰好一条 | 一个角色发多条，断言**全部**可见且**顺序不变**，且「上一位发了两条」时**两条都**被交给下一位 |
| ④ | **pipeline 只断 4 对非 9 对** | 既有 pipeline 用例**只断了 4 个负向对**（`alice→{bob,carol}`、`bob→{carol}`、`carol→{alice}`） | 按 `config.roles` 嵌套 forEach 把 **9 个有序对**（3×3，含自己对自己）全遍历，**每一对双向都断言**：该看的（自己 / 上一位）断「看得见」，不该看的断「看不见」，并断言 `9 == roles.size * roles.size` |

⚠️ **顺带纠正一处容易混的说法：证据登记表里那些 `checked_pairs=6` 与这里的「9 对」不是
同一个东西，别拿 9 去改 6。**
- **`checked_pairs=6`** 是 `C1DeviceEvidenceTest` **落盘 JSON 里的字段**
  （`a9d2077e` 那个夹具层越权审计，每种 mode 3 个 viewer × 2 个他人 = 6 个有序对）。
  **本批没有改那个夹具**，所以它**仍然是 6**，C1-01/02/03/04/05/07 行里那些
  `checked_pairs=6 / violations=[]` **一个字都不用改**。
- **「9 对」** 是 `e7ad2a77` 在 `GroupTurnCoordinatorTest` 里加的 **JVM 断言**
  （3 角色 × 3 角色含自己对己），**不落盘、不进 JSON、不改夹具**。

净效果：`GroupTurnCoordinatorTest` **66 → 72**（本批 `e7ad2a77` **+6**）。
⚠️ **`GroupChatTest` 18 → 22 不是本批造成的**——那 **+4** 来自 **P0 的 `af104850`**
（`role_id == SUMMARY_ID` 那组用例）；`e7ad2a77` **只动了 `GroupTurnCoordinatorTest.kt`
一个文件**（`git show --stat` 可核）。
⚠️⚠️ **盲区③拆成三条是被变异检验逼出来的，这点必须记**：先只写两条时，
**删掉 `predecessorId` 分支的 `index >= roundStart` 守卫，没有任何一条用例会失败**
（既有用例全是单轮，`roundStart` 恒为 0，**那个守卫在单轮下是死代码**）。
补上第三条**跨轮**用例之后，那个变异才被抓住。
⚠️ **这是「断言数量」与「断言强度」两回事**——多写一条用例不是为了凑数，
是因为**少了它，一个真实的守卫删掉后测试照样全绿**。

#### P2：`coerceAtLeast(0)` 的 fail-open —— 探针实测**不可达**，未改

- **疑点**：`visibleMessages` 的 `roundStart =
  messages.indexOfLast { it.role == MessageRole.USER }.coerceAtLeast(0)`（`:564`）
  在「列表里没有 USER 消息」时会取 **0**，于是 `index >= roundStart` **恒真**，
  `predecessorId` / `chairRound` 两个分支等于**对本轮之外的消息也放行**——看着像
  fail-open。
- **探针实测（本轮做的，不是推理）**：`userlessWindows=36 crossRoundLeaks=0`
  （**6 种轮数 × 13 种 limit** 组合）、`emptyPrefixes=6 userlessNonEmptyPrefixes=0`。
  另外**枚举了全部 4 个 `visibleMessages` 调用方**，逐个确认生产路径不会构造出
  「无 USER 消息且列表非空」的输入。
- **结论**：**不可达，未改**。理由与口径写进了 KDoc（`:553-555`）：它与
  `GroupTurnCoordinator.roundMessages` 的 `lastUser < 0 -> messages` 口径**一致**，
  把它改成 `Int.MAX_VALUE` 在生产可达路径上**没有收益**。
  ⚠️ **「探针实测不可达」不等于「结构上不可能」**——将来新增调用方时这条前提会失效，
  所以结论进 KDoc 而不是留在提交信息里。

### HTTP 端点群聊门禁 + `failGroupTurn` 回收平票脚手架（`87f9c209` / `e6764293`）

⚠️ **先纠正一个容易搞错的路径**：这五个端点**在 `app` 模块里**，**不在 `web`**——
`web` 只是 Ktor 壳模块（`AGENTS.md` 里写的是「Hosts the JSON API」）。
真实路径是
`app/src/main/java/heizige/kk/khatkit/app/core/network/routes/ConversationRoutes.kt`。

#### 门禁本体：五个端点原本**零** `isGroupConversation` 判断

| 端点 | 路由声明行 | 守卫调用行 |
|---|---:|---:|
| `POST /api/conversations/{id}/messages/{messageId}/edit` | `:283` | `:289` |
| `POST /api/conversations/{id}/fork` | `:299` | `:305` |
| `DELETE /api/conversations/{id}/messages/{messageId}` | `:315` | `:320` |
| `POST /api/conversations/{id}/nodes/{nodeId}/select` | `:330` | `:336` |
| `POST /api/conversations/{id}/regenerate` | `:346` | `:355` |

⚠️ 上面这些是 **`87f9c209` 之后的现行行号**；早前记录的 `:295` / `:307` / `:318` / `:330`
是**该 commit 之前**的（守卫插在 `initializeConversation` 之后，所以它后面的每个路由都
往后挪了 4 行）。

**破坏链逐环实读，全部成立**（拿 edit 举例，其余四个同构）：

```
editMessage 追加一条 roleId = null 的消息
  → roundOutputPresent(:2135) 按 roleId in committedRoleIds 匹配，null 永不命中 → false
  → takeGroupTurn 走 deleteByRound，删掉整行 group_runs
  → claimRound 重新抢占返回 Acquired(spentTokens = 0, committedRoleIds = emptyList(), freshRow = true)
  → pendingSpeakers = 全员
  → 整轮作废、全员重跑、本轮预算被无声归零
```

⚠️ **这不是理论风险**：UI 侧早就用 `if (!groupChat)` 挡了这五个入口
（`ChatMessageActions.kt`），**但 HTTP 侧没挡**；而 `WebApiModule` 的 `jwtEnabled`
还可以关掉，关掉之后整个 `/api/` 前缀**没有任何鉴权**。

**实现**（`core/network/routes/GroupConversationOperationGuard.kt`，91 行）：
纯函数 `groupConversationOperationRejection(conversation, operation)` +
`requireConversationOperationAllowed(conversation, operation)`，
`ConversationOperation` 枚举 **5** 个值，判定直接用 `isGroupConversation`
（`ChatManager.kt:2126`，同模块 `internal` 直接可见）——**不在门禁里另立一套口径**，
理由是老数据可能残留 `group_config` 却已被改回单聊，这时必须当单聊放行。

⚠️ **错误码用 409 Conflict**，照 `FolderRoutes.kt:69` 的既有范式（那里是
`ConflictException("Folder has a generating conversation")`）。
**不用 400 的理由**：请求本身不畸形（400 在本仓库表示「参数没填对」），挡住它的是
**资源当前状态**；而且**原样重试 409 永远不会成功**，400 反而会诱导调用方进重试循环。

#### ⚠️ fork 的拒绝理由与评审说的**不一样**（据实写，不谎报）

评审给的拒绝理由是「fork 会让账面错位」。**实读不成立**：
`createForkConversation`（`ChatManager.kt:172-188`）**既不复制 `group_config` 也不复制
`type`**，产物是一条 **`groupConfig = null` 的单聊**，**对源会话只读**——
**它破坏不了账目**。

**仍然拒绝**，但理由是另一码事：`ForkConversationResponse` **只回 `conversationId`**，
调用方**无从分辨**自己拿到的已经是一条单聊——**这正是要避免的「静默改数据」**。
所以文案据实写成「**静默换了会话类型**」，**没有谎报成账目错位**。

⚠️⚠️ **顺带发现一处 KDoc 错误（本轮未修，登记为遗留）**：
`app/src/main/java/heizige/kk/khatkit/app/core/ui/components/message/ChatMessage.kt:134-140`
（`groupChat: Boolean` 那个参数的 KDoc）声称「重新生成 / 删除 / 创建分支这三个回调
**都不是群聊感知的，会让群运行日志的账面和实际轮次错位**」——按上面 fork 的实读，
**fork 那一半不成立**（它连源会话都不写）。**这处 KDoc 尚未修。**

#### ⚠️ 一个绕门禁的坑：守卫的**位置**决定它有没有用（已堵）

`getConversationFlow` 走 `ConversationSessionManager.getOrCreate`：**会话未加载时，
拿到的是 `Conversation.ofId(...)` 这个空单聊占位对象**（`groupConfig = null`）。
所以守卫如果排在 `initializeConversation` **之前** → 判成「非群聊」→
**门禁形同虚设**。

- **修法**：四个端点的守卫**排在 `initializeConversation` 之后**，并加**源码护栏**
  把这个顺序**钉死**（`GroupOperationApiGuardSourceGuardTest` 4 条）。
- ⚠️ **`regenerate` 是唯一例外**：它**本来就没有** `initializeConversation`
  （`:346-355`），补上会把「会话存在但未加载」的**单聊** regenerate 从 404 变成成功
  ——那是**改非群聊路径的行为**，不能顺手改。所以它**复用已经读出来的 `conversation`
  变量**，护栏里另外**断言它不得出现 `initializeConversation`**。

**测试**：纯函数 `requireConversationOperationAllowed` + `ConversationOperation` 枚举共
**7 条 JVM 用例**（`GroupConversationOperationGuardTest`，5 个操作各一条 + 放行/拒绝
边界），源码护栏 `GroupOperationApiGuardSourceGuardTest` **4** 条。
⚠️ **变异检验两次都真红**（删守卫 → **3 红**；把守卫挪到 `initializeConversation`
**之前** → **红**，其中一条断言的失败信息就是「**必须排在 `initializeConversation` 之后，
否则读到的是空单聊占位对象**」）。

#### `e6764293`：`failGroupTurn` 补回收平票裁决脚手架

- **症状**：`failGroupTurn`（`ChatManager.kt:1957`，KDoc `:1951-1956`）**不调**
  `dropTieBreakScaffolding`（`:2028`）。议长裁决那一轮生成失败时，那条 SYSTEM 指令
  （「你是本群议长…本轮投票出现平票，由你裁决」）**永久留在会话里**——因为它是
  `role = SYSTEM` + `isSynthetic`，`visibleMessages` 对 SYSTEM / 合成消息**一律放行**
  （`:567`），所以**之后每一轮的所有角色都会读到那段裁决指令**。
- **修法**：在 `persistRoundState(failed)` 之后补一次 `dropTieBreakScaffolding(conversationId)`
  （`:1984`），与 `completeGroupRound` **同序**。
- **对照用例**：`GroupTieBreakScaffoldingDropSourceGuardTest` **3 条**——`completeGroupRound`
  的 **Decided / Undecided 各回收一次**，**NeedsChairTieBreak 故意不回收**
  （那一轮确实还需要议长裁决）。⚠️ **这三条在修复前就已经是绿的**（它们钉的是
  `completeGroupRound` 那一侧没被改坏），**所以它们不是这次修复的证据**，
  这次修复的证据是新增的那条「`failGroupTurn` 必须调」的断言。

### ⚠️ 本轮两处已订正的认知错误（子代理纠正评审与我的说法）

按「订正也要留痕」的惯例登记，**原文不删**：

1. **「`role_id` 可以手输」不成立。** `GroupConfigSheet` 的 `RoleEditor`
   （`GroupChatPage.kt:746`）把 `role_id` 渲染成**只读 `Text`**（`:754`），
   只有 name / assistant_id / model_id / card_id **四个输入框**加一个「议长」chip；
   「添加成员」生成的是 **`"role-N"`**（`:637`），**不是 UUID**。
   **真实入口只有外部 JSON 导入。**
2. **`tally` 那条机制描述反了。** 评审说裁决基于「**过时立场**」，实际是
   `associateBy` **保留靠后（最新）的那条**，所以裁决实际基于**最新立场**。
   ⚠️ **但问题本身是真的**：旧票与新票**一起进候选池**，**票池被污染**——
   **问题成立、机制描述反了**，别把结论一起丢掉。

### 平票脚手架三处漏回收 + 残留旧 job 两道守卫（零设备，2026-10-06，HEAD `361c7cf6`）

⚠️⚠️ **本节的性质与前面所有批次都不同，先说清**：前面几批补的是**覆盖**，
**这一批修的是三个真缺陷**（不是「发现已堵住但入口没堵严」，是**当时真的还在漏**）。
但**修缺陷不等于拿到验收证据**——本轮**零设备、零 `adb`、零 gradle**，
②③ 针对的残留旧 job 路径**从未在真机复现过**。所以**十例状态一个都没变**。

三批分别是：

| 批 | commit | 修的是什么 | 测试 |
|---|---|---|---|
| ① | `e72793e6` + `8a5f89d7` | 平票裁决脚手架在 **`cancelActiveGroupRun`** 与 **`abandonDanglingGroupRuns`** 两条轮次终态出口上**漏回收** | `GroupTieBreakScaffoldingDropSourceGuardTest` **3 → 7** |
| ② | `2fdee352` | ⚠️ **残留旧 job 不得推进状态、不得跨轮盖戳** | `GroupTurnCoordinatorTest` **+6**（真单测）+ 新类 `GroupStaleJobCommitSourceGuardTest` **7**（源码护栏） |
| ③ | `ef716dd3` + `8ccc0264` | `onFailure → failGroupTurn` 那条路**补同款守卫** | `GroupTurnCoordinatorTest` **+6**（真单测）+ 新类 `GroupStaleJobFailureSourceGuardTest` **6**（源码护栏） |

#### ① 平票脚手架还有两处漏回收（`e72793e6` / `8a5f89d7`）

`failGroupTurn` 那一处已由 `e6764293` 修过。**同一批修完时发现还有两处完全相同的
失败模式没修**，这次一并修了：

- **`cancelActiveGroupRun`**（`ChatManager.kt:2065`）——用户中途取消议长裁决那一轮。
- **`abandonDanglingGroupRuns`**（`:2092`）——发新消息时上一轮还挂 `RUNNING` 被判死。

两处原来都不调 `dropTieBreakScaffolding`（`:2115`），后果与 `failGroupTurn` 相同：
**平票 SYSTEM 脚手架永久留在会话里**，之后每轮所有角色都看到
「你是本群议长…本轮投票出现平票…」。

**为什么确定该修（不是「看着像漏」）**：`completeGroupRound`（`:1931`）全仓
**只有一个调用点**（`onSuccess` → `commitGroupTurn` 返回 `Advance.Finished`）。
这两条路径落终态 + 清 `groupRunsInFlight` 之后，**再无路径为该轮调
`completeGroupRound`** → 脚手架必然孤立。

**为什么可以无条件补**（这四条是子代理逐条核实过的，**无条件不等于「想当然」**）：

- 保留脚手架的唯一状态是 `NeedsChairTieBreak`（议长还没裁决），而**只有
  `completeGroupRound` 自己能进入这个状态**，它从不调这两条路径。
- `cancelActiveGroupRun` 第一行是
  `groupRunsInFlight.remove(conversationId)?.runToken ?: return`（`:2066`）——
  **没有活跃轮次时直接返回、什么都不做**。
- `abandonDanglingGroupRuns` 同理，只在把 `RUNNING` 行判死之后才回收。
- 时序上 `abandonDanglingGroupRuns` **严格早于**存新 `USER` 消息与新一轮
  `handleMessageComplete`；新轮若再平票会**自己 append 一条新指令**，不会因为旧指令
  被删而丢指令。

**脚手架的真实形态（可观察，所以这不是「内部状态泄漏」）**：
`GroupTurnCoordinator.tieBreakInstruction()` 生成一条 `UIMessage`，
`role = SYSTEM` / `roleId = chairRoleId` / `roundId = state.roundId` /
`turnKind = TURN_TIE_BREAK`（= `GroupChat.TURN_CHAIR` = `"chair"`）/
`isSynthetic = true`，经 `appendGroupMessages` 追加成新的 `MessageNode` ——
**在数据库里可观察**。回收口径 `withoutTieBreakInstruction` 是
`filterNot { role == SYSTEM && turnKind == TURN_TIE_BREAK }`，
⚠️ **不看 `roundId`、不看 `isSynthetic`，属全会话范围清扫**——这也是 ②③ 里
`failGroupTurn` 的四个副作用之一被标成「不可逆**且跨轮**」的原因。

⚠️⚠️ **泄漏机制（根因，必须连着这条一起记）**：
`GroupChat.visibleMessages`（`core/data/model/GroupChat.kt:557`）的 `when` 里
**第一分支** `message.role == SYSTEM || message.isSynthetic -> true`
（`:567`）排在**所有 `index >= roundStart` 判断之前**，
对**任何 viewer、任何轮次**放行。**这就是「每轮所有角色都看到」的根因**——
不是脚手架没回收那么简单，是**只要它在库里就一定被看见**。

**测试**：`GroupTieBreakScaffoldingDropSourceGuardTest` **3 → 7** 条，新增「cancel /
abandon 各含调用」「drop 排在 `persistRoundState` 之后」「无条件且恰好一次」。
⚠️ **抽不出纯函数判据**：该不该回收取决于 **DAO 读**
（`groupRunsInFlight` 有没有 `runToken`、`getByRunToken` 的 `status`），
而真正要守的不变量「**这个函数体内必须有那处调用**」本身是**源码属性**。
KDoc 里已写明**这是文本护栏不是行为断言**。
⚠️ **变异检验两次都真红**（删掉 cancel 那处 → **3 红**；删掉 abandon 那处 → **3 红**）；
**还原方式是文件备份 + `sha256sum -c`，未用任何 git 命令**。

#### ② 残留旧 job 不得推进状态、不得跨轮盖戳（`2fdee352`）——⚠️ 本批最严重的一个

**触发路径**：`abandonDanglingGroupRuns` **不取消任何 job**（会话里唯一的取消机制
`ConversationSession.cancelJobs()` 只被 `stopGeneration` 和 `cleanup()` 调用）。
所以：旧轮 alice 的 job 仍在飞 → 用户发新消息 → 旧轮被判死成 `CANCELLED`、
触发消息变了 → **旧 job 随后仍然跑完并进 `onSuccess`**。

**问题一：`advance` 不检查终态**——拿到已 `CANCELLED` 的 `state` 照样推进。
⚠️ 原来只有**巧合式安全**：`findByRound(key, plan.roundId)` 用的是**现算的新轮
`roundId`**，新轮还没被抢占时返回 null → `return null` 止损。但**新轮已被抢占时**
（竞态，**完全现实**）返回**新轮自己那一行 `RUNNING`** → `advance` 照常推进 →
**上一轮的角色被追加进新轮 `committed_role_ids`**。

**问题二：`stampGroupTurn` 跨轮盖戳**——⚠️ **比「盖错戳」更重**：`stampGroupTurn`
（`:1892`）排在所有 `return null` **之前**，且内部 `saveConversation(...)` 是
**不可逆的写**。那份上一轮的产出被**永久标上 `roleId` / `roundId` / `turnKind`**，
`return null` 只是止损，**追不回来**。

**修法两处**：

1. `GroupTurnCoordinator.advance`（`:320`）加终态守卫，返回新增的
   `Advance.Halted(state, detail)`（`:298`），不推进。
   ⚠️ **为什么选 `advance` 而不是 `commitGroupTurn` 入口守卫**：入口守卫按「行是不是
   终态」判会**全部放行**（因为 `findByRound` 命中的是**新轮那行 `RUNNING`**，不是终态）；
   按「令牌是不是本 job 的」判就变成了第二处，**两处判据重复**。而且 `advance` 是
   **纯判定内核** → **能拿到真单测，而不是只能写文本护栏**。
2. 新增纯函数 `GroupTurnCoordinator.checkCommitAdmission(jobRunToken, row,
   stampRoundId)` → `CommitAdmission.Admitted / Denied`（`:390`），在 `commitGroupTurn`
   里**盖戳之前**拦下；`Denied` 走 `Halted` 分支，而那个分支**只 `Logging.log`、
   不碰 `groupRunsInFlight`**。
   ⚠️ **为什么不碰镜像**：镜像里装的可能是**新轮的令牌**，清掉会让新轮**再也取消不掉**、
   续跑拿不到 `expectedRunToken`、**整轮卡死**。
3. 令牌**必须从 `takeGroupTurn` 的返回值捕获**（`GroupTurnEntry.Speak(...).runToken`，
   `ChatManager.kt:761`），**不能事后从 `groupRunsInFlight` 反查**——判死路径都把镜像
   清掉了，反查到的**要么是 null**（把正常路径也打死）**要么是新轮的令牌**（恰好放行
   残留 job）。**这条单独有护栏钉死。**

⚠️ **「拿回旧轮 `roundId`」做不到，所以按许可在盖戳前挡住**：`SpeakerStep` 不带轮次信息，
从消息邻接反推归属要重写归属推导，**脆且改动面远超范围**。

⚠️ **两层判据分工不重叠（这是设计要点，别当重复代码删掉）**：
`checkCommitAdmission` 认**身份**（这是谁的产出），`advance` 判**生死**（这一轮还活着吗）。
同行同令牌但**已终态**时**归属是对的**（产出确实属于那一轮），该拦的是「不推进」→
所以归属判定里**故意不判终态**，`advance` 里**故意不判令牌**。

**正常路径七种形态逐条论证不受影响**：单聊 / 正常群聊轮次推进 / 取消重跑 /
失败续跑 / 超时重跑 / 重新生成删消息切分支 / 议长平票裁决 / 进程被杀后重启续跑 /
用户取消（**根本进不了 `failGroupTurn`**）。
关键依据：`claimRound` 的三条出口（`Acquired(freshRow)` / `Continued` /
`Acquired(reclaimed)`）产出的 `state.runToken` 与写进 `group_runs` 的令牌
**是同一个**（`persistClaim` 的 `insert` / `updateStatus` **都不碰 `run_token`**）；
`reclaimed` **只把 status 推回 RUNNING，不改 `roundId` 也不改 `runToken`**。
⚠️ **这七条是「逐条读代码论证 + 真单测覆盖三种死法」，不是「真机各跑一遍」**。

**测试**：新增 **12 条**（`GroupTurnCoordinatorTest` **+6 条真单测**、
新类 `GroupStaleJobCommitSourceGuardTest` **+7 条源码文本护栏**）。
真单测里 `resuming a cancelled or failed round still admits and advances` 用
`CANCELLED` / `TIMEOUT` / `FAILED` **三种死法**把
`cancelRound → claimRound → checkCommitAdmission → advance` **整条链跑通**。

**变异检验 6 个（M1–M6）全部 EXIT=1 逐个真红**。⚠️ 其中 **M2 特意把
`stampGroupTurn` 恢复成原始 bug 的真实顺序**（排在 `checkCommitAdmission` **之前**），
打红证明**顺序守卫确实在守那个具体次序**。
⚠️ **M6 只能被源码护栏抓住**（`commitGroupTurn` 在 JVM 里跑不起来），
**这是范式内的诚实分工，不是「护栏没用的遮羞布」**——同一个变异里，
`checkCommitAdmission` 的**逻辑**由真单测守，**调用位置**由护栏守。

⚠️⚠️ **附带发现的连带损伤（本轮刻意没改，作为遗留登记，编号见「已知遗留与风险」）**：
`onSuccess` 里 `null -> groupRunsInFlight.remove(conversationId)`
（`ChatManager.kt:1007`）——残留旧 job 走到这里时**镜像里装的是新轮的令牌**，
这一 remove 让**新轮再也取消不掉**、续跑拿不到 `expectedRunToken`、**整轮卡死**。
⚠️ **刻意没改**：改它会**动正常路径**（`produced == null` 时 `failGroupTurn` 可能提前
return，**镜像靠这一行兜底清掉**）。改为让拒绝走 **`Halted` 而非 `null`** 来**绕开**它。

#### ③ `onFailure → failGroupTurn` 补同款守卫（`ef716dd3` / `8ccc0264`）

②只堵了 `onSuccess` 那条路，**失败那条路没堵**：超时 / 单角色失败时，
残留旧 job 仍会往新轮塞一条 `errorNode(roundId = 新轮 roundId)` 并把新轮写成 `FAILED`。

⚠️ **两条路的结构不对称，这决定了修法**：`failGroupTurn` 的签名里
**根本没有 `plan` 形参**，它自己在函数体第一屏
`roundPlanFor(config, conversation.currentMessages)` **现算**（`:2018`），
所以判死后走进来时 `plan.roundId` **已是新轮**。因此**守卫只能放在 `failGroupTurn`
内部**，不能像 `commitGroupTurn` 那样由 `onSuccess` 现算好传进来。
令牌来源则**完全同源**（`onFailure` 闭包能读外层的 `groupRunToken`，`:716`），
只需 **1 跳透传**，**没有新的捕获点**。

**新函数 `GroupTurnCoordinator.checkFailureAdmission`**（`:444`）：
**不另立判据**，直接调 `checkCommitAdmission` 拿身份，再补一条终态。
⚠️ **为什么两条路判据不能完全相同**：提交路 `advance` **独占**终态判定并返回
`Advance.Halted`，所以 `checkCommitAdmission` 刻意**只认身份、不判生死**；
失败路**没有 `advance` 这一层**（`fail` / `timeoutRound` 是**无条件写终态的纯函数**，
执行层拿到 state 就直接写节点 + 落库 + 回收 + 清镜像）。
**把终态判定塞进 `checkCommitAdmission` 会把提交路一起打死。**

⚠️ **两条判据的主次（子代理的诚实标注，别当成等价条款）**：
**身份条款是决定性的那一条**（新轮已被抢占 → 行是 `RUNNING` 但**令牌不同** → 拒）；
**终态条款**在「判死之后用户**没再发消息**、`plan.roundId` **仍是旧轮**」这一形状下
才是决定性的（同行同令牌、`status` 已 `CANCELLED`）。它同时兜住 `stopGeneration` 里
`session == null`（**jobs 空、未取消任何在飞 job**）就写 `CANCELLED` 的竞态。

⚠️ **`failGroupTurn` 的四个副作用（逐条列全，并标出不可逆）**：

| # | 副作用 | 可逆性 |
|---|---|---|
| 1 | `appendGroupMessages(errorNode(...))` → `saveConversation` | ⚠️ **不可逆** |
| 2 | `persistRoundState(failed)` | ⚠️ **不可逆** |
| 3 | `dropTieBreakScaffolding` | ⚠️ **不可逆且跨轮**（**全会话范围**清扫） |
| 4 | `groupRunsInFlight.remove` | ⚠️ **不可逆，且装的可能是新轮的令牌** |

守卫插在这四个**全部之前**（`ChatManager.kt:2021`）；**拒收分支内只有**
`Logging.log` + `return`，**不碰 `groupRunsInFlight`**。

**`onFailure` 三个分支（子代理核实的条件，`ChatManager.kt:975-990`）**：

- **A**：`step != null && it is TimeoutCancellationException` → `failGroupTurn(timedOut = true)`
  → 写 `TIMEOUT`。
- **B**：`step != null && it !is CancellationException` → `failGroupTurn(timedOut = false)`
  → 写 `FAILED` / `role_failed`。
- **C**：`if (it is CancellationException) throw it` → ⚠️ **用户取消走这里，
  两个 `failGroupTurn` 都不进**。
  ⚠️ `TimeoutCancellationException` 是 `CancellationException` 的子类，
  **但 A 分支在前**，所以**超时不会被 C 吞掉**——这个顺序是承重的。

⚠️⚠️ **一条必须如实登记的证据缺口**：**路径 B（`cancelActiveGroupRun` 让残留 job 进
`onSuccess`）没找到确证**——`stopGeneration` 会先 `cancelJobs()` + `join()`，
被取消的协程走 `onFailure` 的 **C 分支 rethrow**，`onSuccess` **不会执行**。
**改动对两条路径都有效**（两个守卫是同款判据、同一个不变量），但
⚠️ **只有路径 A（`abandonDanglingGroupRuns`）有实测支撑**，
而且**连路径 A 也只在 JVM 单测层面成立，从未在真机上复现过**。

**测试**：新增 **11 条**（`GroupTurnCoordinatorTest` **78 → 84**，其中 **6 条真单测**；
新文件 `GroupStaleJobFailureSourceGuardTest` **6 条源码文本护栏**）。
⚠️ **正常放行必须也有回归断言**（`a normal role failure is admitted and still writes
its own round` / `a normal timeout is admitted…` / `resuming a dead round still admits
its failure`）——**不能只测「被拒」**，否则「把守卫写成永远拒绝」也会全绿。

**变异检验 7 个（M1–M7）全部 EXIT=1**。⚠️ 其中 **M3 暴露了子代理自己的测试 bug**：
第一版顺序断言**锚错了**（锚 `checkCommitAdmission(` 调用而不是**拒收早退那一行**），
据此改成锚 `DENIED_MARKER` 后才真正抓住。**这条留痕是因为「护栏自己写错」也是
护栏体系的一部分**——不记的话，下一个维护者会以为顺序断言一直是对的。

### `importGroup` 契约 `:205` 缺口修复（零设备，2026-10-06，HEAD `db4cdd77`）

commit：`8622bf19`（筛查与去重纯函数）/ `aee20f85`（25 条纯函数用例）/
`88777898`（接上密钥闸门 + 去重 + 筛查）/ `db4cdd77`（22 条端到端用例）。
⚠️ **`importShare` 一行未动；导出路径一行未动；golden 哈希没变，也没跑
`--update-golden`**（改动全在导入侧）。

#### ⚠️ 先订正一处前提：它不是「读别人的酒馆文件」，是「读回自家导出」

早前口头说法是「`importGroup` 的定位是读别人的酒馆文件」。**错**。它的**第一道门**是
`TavernChatCodec.kt:285`：

```
val payload = document.header[GROUP_FIELD] as? JsonObject ?: return null
```

纯 SillyTavern 群聊文件**根本没有 `khatkit_group` 表头**，**当场返回 `null`**。
所以 `importGroup` 实际是「**读回自家导出**」。⚠️ **读别人分享载荷的路径是
`GroupChat.importShare`**（`GroupChatPage.kt:492` 生产侧在用）。这条订正是子代理纠正的，
已采纳。

#### 改动摘要

| | 改前 | 改后 |
|---|---|---|
| 密钥黑名单 | 无 | 原始 payload 全量扫，命中即 `Rejected` |
| 角色卡去重 | 无 | 按 `role_id` 折叠保留首条；丢空 `role_id`；记账 |
| 配置校验 | 无 | `screenImportedConfig` 跑 `validate` 分流 |
| 非法配置去向 | 原样交给调用方 | `config = null` + 字段级错误，**消息/群名/角色卡照常返回** |
| 校验结论可见性 | 无 | `TavernGroupChatDocument.importReport`（4 态） |
| 去重记录可见性 | 无 | `TavernGroupChatDocument.cardFixes` |

「拒绝」的形态**不是整份返回 `null`**，而是 `config = null` + 字段级错误——这样
「别造出半个群」有了返回值层面的保证，但**聊天记录不丢**。

⚠️ **`config == null` 有四种成因，不是两种**：① 密钥黑名单命中 ② 文件里没有配置块
（`NoConfig`，**不是错误**）③ 键在但解不出来（坏文件）④ 配置被判掉（坏文件）。
②③ 曾经都被报成 `NoConfig`，**被测试当场抓红**后改的——**这是本轮唯一一次
「测试在实现之前抓到 bug」**。

#### 「拒绝 vs 降级」的判断（三处，采纳子代理的判断）

| 项 | 评审的倾向 | 采纳的判断 | 依据 |
|---|---|---|---|
| `schema_version` 未知 | 降级 | **拒绝** | `GroupChat.kt:504-510` 明文「版本闸门是**唯一**判定口径，不允许两套判断各判一次」；契约写「保留 ≠ 改写版本号」 |
| 预算越界 | 降级 | **拒绝** | 契约两处写死「超上限保存失败」；且**不存在安全的缺省值**（夹到 1 发不出话，夹到上限是成本事故） |
| `mode` 未知 | 未提 | **拒绝** | 默认 pipeline 会**静默丢 `vote_candidates`** |
| `revision` / `tie_policy` 越界 | 降级 | **降级** | 判据是「只有它们各自已有一个**确定无疑的合法缺省值**」——`decodeConfigObject` 读缺失的 `revision` 就是 1、缺失/未知的 `tie_policy` 就是 `TIE_FAIL` |

#### ⚠️ 核实中发现的额外真 bug（已随去重一并修掉）

`importGroup` 用 `cardJson.decodeFromJsonElement` 解角色卡，而 `GroupChat.decodeCards`
**会丢掉缺 `role_id` 的条目**（`GroupChat.kt:922` KDoc 明写「不猜它属于谁」）。
`importGroup` 这条路**不丢**——`{"role_id":""}` 能解出 `roleId = ""` 的**孤儿卡**。
同一语义两条路径的又一处漂移。

⚠️ 另有一条**核实结论**：`decodeConfigObject` 的 `extras` 往返**本来就是好的**
（`GroupConfig.extras` `GroupChat.kt:71`、`roles[].extras` `:91`），**不是本批修的**。

#### 测试

新增 **47 条**（808 → **855**）：`GroupImportScreeningTest` **25** 条（纯函数）+
`TavernGroupImportGateTest` **22** 条（端到端）。✅ 现有测试断言**一条都没删没改**
（`TavernCompatTest` 21、`GroupTavernExportTest` 22、`GroupChatTest` 22、
`C1pGroupExportHashTest` 5）。

两条结构性不变式（一次扫 10 个配置）：① `screenImportedConfig` 放行的配置
`validate` **必须也判合法**（「降级不会放行保存路径会拒绝的东西」）；② 归一化
**只许动** `revision` / `tie_policy`。

#### ⚠️ 变异检验有一项存活，如实登记

⚠️ **变异 5（弱化 residual 兜底）存活。** 原因是实现有**双层保险**：「结构性错误
一律拒绝」和「归一化后 residual 复查」**互相独立地**都能拦住同一批错误，弱化任一层
行为不变。**结论：residual 兜底当前无法被任何变异杀死**——它是「为将来有人往白名单
加字段却忘了写归一化」埋的绊线，⚠️ **现在没有测试护着**。子代理明确「**不声称证明了
它**」，照记。变异 4（预算改走降级夹取）第一次也存活（同因），换了个变异体才抓住。

#### 三条命令退出码

`:app:testDebugUnitTest --rerun` **0**（**107 类 / 855 例 / 0F 0E 0S**）、
`assembleDebug` **0**、`lintDebug` **0**（590 results，**0 error**，改的 4 文件命中 0）、
`c1p_group_export_hash.py` **0**（9 变体 + 2 QR 全一致）。

⚠️ **本节所有数字的复算命令见「统计口径复算脚本」小节**；本轮实测
`c1_doc_stats.py` 输出 `107 类 / 855 例`、`40 类 / 408 例 → 42 类 / 455 例`、
app lint `0 error / 584 warning / 6 hint`、全模块 `0 / 617 / 7`、仪器 `61`（含
untracked `64`）、锚点 `78`。

⚠️ **十例判定一个都没变，仍是 10/10 `unverified`**：本批改的全是**导入侧**，
而十例要的四类产物（可见消息集合 / 模型调用序列 / 导出哈希 / 真机 UI）**一份未增**，
且本批**零设备、零 `adb`**。

### wire 级模型名的两条零设备证据（`f1bf516e..74d82476`，2026-10-06，**零设备**）

⚠️ **本节是硬理由①的两条零设备证据的正式落点**。**先说清它不是什么**：
**它不是验收证据**——契约 `:206` 点名的四类产物**一份未增**，十例**仍是 10/10
`unverified`**。它记录的是「**能力已具备 + 取得途径已改成 wire 直取**」，
以及**这些结论各自能被证明到什么程度为止**。

#### 缺陷是什么（为什么原先只能反查）

- `ChatCompletionsStreamDecoder.kt` 的 `finish(reason, responseId, model)` **一直正确**
  发出 wire 上的模型名——`StreamChunk.Finish(reason, responseId, model)`。
- ⚠️ **缺陷在下游**：`StreamChunkHandler` 收到 `Finish` 时**只取 `finishedAt`**，
  把 `chunk.model` **直接丢弃**。
- 后果：助手消息上留不下网关自报的名字，于是验收证据里的 `deepseek-v4-flash` /
  `glm-5.2` 是拿 `message.modelId`（本地 `Model` 的 **UUID**）**回查本地 provider 模型表
  反查**出来的。

⚠️ **这意味着修的不是「拿不到 wire 名」，是「拿到了却被丢弃」**——原先文档里那句
「需要在 `OpenAIProvider` 侧记录响应 `model`（属于加日志）」**是错的**，已在
「已知遗留与风险」第 18 条作废。

#### 证据①：JVM 侧 `WireModelNameProvenanceTest`（9 例 / 27 处断言 / 退出码 0）

落点：`ai/src/test/java/heizige/kk/khatkit/ai/provider/providers/openai/WireModelNameProvenanceTest.kt`
（**268 行 / 9 条 `@Test`**）。逐例（断言数按源码逐条数）：

| 用例 | 断言 | 钉住什么 |
|---|---:|---|
| `raw sse model literal should land on wireModelName verbatim` | 3 | SSE 帧里的 wire 名**逐字**落到 `wireModelName`（含不 trim 的那一次比较） |
| `wire model name should survive dotted slashed and uppercase literal unchanged` | 2 | **刁钻字面量逐字透传**：`deepseek-v4-flash-250528`、`zhengyimeng/GLM-5.2-preview`（点、斜杠、大写都不被归一化） |
| `wire model name should not be derived from local model configuration` | 5 | **反查隔离**：构造 `Model(modelId="local-cfg-alias-9f3c")`，断言 wire 名**既不等于** `model.id`、**也不等于** `modelId` / `displayName` ⇒ 两条路径互斥 |
| `sse frames without model field should leave wireModelName null` | 4 | 无 `model` 帧 ⇒ `null`——**不是空串，也不回退 `modelId`** |
| `absent model in sse should not overwrite an existing wire model name` | 2 | 中途缺 `model` 的帧**不覆盖**已落盘的值 |
| `non streaming result should set wireModelName symmetrically` | 4 | 非流式 `handleTextGenerationResult` **对称**（含 `zhengyimeng/GLM-5.2-preview` 逐字与反查隔离） |
| `non streaming empty model string should not be treated as a valid model name` | 1 | 非流式**空串不当作有效名** |
| `wireModelName should survive kotlinx serialization round trip` | 3 | 编码结果**含 `wireModelName` 键**，往返后逐字相等 |
| `legacy json without wireModelName key should decode to null without throwing` | 3 | 旧版本序列化器写的数据（删掉该键）⇒ 解码成 `null` 且**不抛** |

**非空验证（本轮做过，如实登记）**：临时删掉 `Finish` 分支里那一行
`wireModelName = …` 重跑 ⇒ **`9 tests completed, 3 failed, EXIT=1`**，
失败的**恰好是 3 个流式来源用例**；随后从备份还原并 `diff` 确认与 HEAD 一致。
⚠️ **这条只能证明「那 3 条真的依赖这一行」**，非流式那两条仍绿——
所以它**不是**「删掉任意一行都会红」的证明。

#### 证据②：真实公网网关 wire 名探测（`tools/verification/c1_wire_model_probe.py`）

⚠️⚠️ **必须先说清这条证明的边界**：脚本**直接打网关**，**完全不经过 `ChatManager` /
`StreamChunkHandler` / `UIMessage`**。所以它证明的是「**网关会自报这个名字**」，
**不是**「app 把它落下来了」。后者只有证据①覆盖。

实测输出（key 只走命令行 / 环境变量，脚本内 **0 处硬编码**）：

| requested | wire_model | status | finish_reason | usage |
|---|---|---:|---|---|
| `deepseek-v4-flash` | `deepseek-v4-flash` | 200 | `stop` | prompt 15 / completion 39（reasoning 16） |
| `glm-5.2` | `glm-5.2` | 200 | `stop` | prompt 23 / completion 533（reasoning 502） |

非流式（`--no-stream`）也验过：`deepseek-v4-flash` → `wire_model: "deepseek-v4-flash"`，
200。⚠️ **密钥卫生**：实测后该 key 在 `git diff ea6b7c4c..HEAD` 与**全部 commit message**
里出现 **0** 次（本轮复核过，见台账那一批的记述）。

⚠️⚠️ **诚实要点（这一条最容易被拔高，照记）**：**wire 名与请求名一致**，
说明**原先 UUID 反查出的名字恰好是对的**。所以这**不是「反查错了被纠正」**，
是「**取得途径从反查变成 wire 直取**」。⚠️ 它**不构成对既有证据的追溯性升级**——
设备上那份既有证据里的两个名字**本来就写对了**，只是**来源不同**。

#### 取证局限（本节的边界，逐条照记）

① **fixture 级**：证据①是**人造 SSE 帧**喂给真实解码器 + 真实 `StreamChunkHandler`，
**中间没有替身**（这是它比多数护栏强的地方），但**帧是我们自己写的**——
**真实网关收尾帧的形状没有在这里被覆盖**。
② **只覆盖 OpenAI chat-completions 一条路径**：`Claude` / `Google` 两条 provider 路径
**一行都没测**，`wireModelName` 在那两条上是否落盘**零证据**。
③ **零设备**：`ai/` 的两处生产改动**没上过真机**；证据②验的是网关不是 app；
证据①是 JVM。⚠️ **仪器测试一行都没跑过**——
`C1LiveModelSequenceTest` 里新增的 wire 优先判定、`wire_model_name_provenance`
取值、`wire_model_name_reconciliation`、`wire_model_name_provenance_counts`、
`wire_and_reverse_lookup_agree`、以及「**不允许两者皆空**」那条防退化断言，
**全部只有编译验证**。
④ **设备上那份 `c1-live-evidence-real-provider.json` 里现在记的仍是 UUID 反查值**。
要等真机重跑才会变成 wire 值。⚠️ **这里没有任何真机数字。**
⑤ **旧 Room 库既有消息树（含新字段）的反序列化没对真实数据库跑过**——
只靠 nullable + 默认值 + JVM 序列化往返保证（`UIMessage` 是 `@Serializable`，
随对话 JSON 落库，**无需 Room 迁移**，所以风险面确实小，但**没在真实库上验过**）。
⑥ **`wireModelName` 不在任何 UI 上显示**：**没做，也没打算做**。
所以本节**不构成任何「用户看得见模型名」的证据**。

#### 三条命令的真实退出码

`:ai:test` **0**（含本类 **9 例 / 0F 0E 0S**）、
`:app:testDebugUnitTest --rerun` **0**（**110 类 / 893 例 / 0F 0E 0S**）、
`:app:assembleDebug` **0**、`:app:lintDebug` **0**、
`:app:compileDebugAndroidTestKotlin` **0**（263/263 executed，含一次 `--rerun-tasks`）、
`python3 tools/verification/c1_doc_stats.py` **0**（**18 条：OK 18 / WARN 0 / FAIL 0**）。
⚠️ **`c1_wire_model_probe.py` 的两次 200 不是退出码口径**——它是**要真 key 才能跑**的
网络探测，**本节只把它当取证手段记录，不当回归护栏**（它**零测试用例**，
仓库里**没有**任何 task 会自动跑它）。

⚠️ **十例判定一个都没变，仍是 10/10 `unverified`**：本批**零设备、零 `adb`**，
四类产物**一份未增**。⚠️ **硬理由①本身也仍未消**——
成因已修、能力已具备，但**真机没跑**（局限 ③/④）。

### C1 真机证据采集第七轮（2026-10-06，HEAD `86e88970d`：**`C1LiveModelSequenceTest` 前 4 条真机全绿**）

⚠️ **本节只登记这一轮新采到的内容，不改任何一行的判定**。**十例状态仍是 10/10
`unverified`**，契约 `:206` 点名四类产物**只是把前 4 条补齐了**，而**UI 端到端 /
酒馆本体 / 相机扫码仍然零份**——见本节末尾「这一轮消掉了什么 / 没消掉什么」。

#### 环境与跑法（逐条照记，与第四轮同一台机器）

| 项 | 实测值 |
|---|---|
| 设备 | OnePlus **`PKG110`** / Android **16** / API level **36** / **`arm64-v8a`** |
| serial | `adb-3B6F5ME910B6H059-Sqr0AX._adb-tls-connect._tcp`（**无线 TLS**，不是 `ip:port`） |
| 构建 | `./gradlew --offline :app:assembleDebug :app:assembleDebugAndroidTest` ⇒ **`BUILD SUCCESSFUL in 5s`**，退出码 **0**（`394 actionable tasks: 12 executed, 382 up-to-date`） |
| ⚠️ **APK 是 per-abi 的** | 主 APK 真实路径 **`app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`**（arm64 设备上装的是这个）；androidTest 是**无后缀单 APK** |
| 安装 | 两次 `adb install -r -t` 均 **`Success`** |
| 端口转发 | `adb reverse tcp:8765 tcp:8765` 退出码 **0** |
| mock 服务 | `/tmp/opencode/roundtable-vote/mock_openai_v2.py`（`mock_server_version` 字段已落进 JSON） |
| ⚠️ **必须用 v2** | `/tmp/opencode/mock-llm/mock_openai.py` 是 **v1，没有 `BALLOT:` 逻辑**，用它会让第 4 条（vote）**按设计失败** |

⚠️⚠️ **没有用 `:app:connectedAndroidTest`**——它**会卸载 app 并删掉外置目录里的证据文件**。
采集一律手动 `install -r -t` + 手动 `am instrument -w -e class '…#$方法名'`，
跑完立刻 `adb pull`。

#### 四条仪器测试的执行结果（退出码 / `OK` 原文 / Time 逐条照抄原始日志）

| # | 方法名 | 退出码 | `OK` 原文 | Time |
|---|---|---:|---|---:|
| 1 | `pipelineRoundRecordsRealModelSequenceAndRealTokenUsage` | **0** | **`OK (1 test)`** | **2.142** |
| 2 | `budgetTruncationSkipsRemainingRolesAndRecordsRunLog` | **0** | **`OK (1 test)`** | **1.746** |
| 3 | `roundtableRoundRecordsChairSummaryCallSequence` | **0** | **`OK (1 test)`** | **3.896** |
| 4 | `voteRoundRecordsBallotCallsAndSummarySequence` | **0** | **`OK (1 test)`** | **1.842** |

四条原始日志各自都有 `INSTRUMENTATION_STATUS: numtests=1`、
`INSTRUMENTATION_STATUS_CODE: 0`、`INSTRUMENTATION_CODE: -1`、`INSTRUMENT_EXIT=0`。
⚠️ **这四轮 OEM 回收策略都没有触发**（4 轮的整轮存活分别 6 / 9 / 9 / 5 秒，
比第四轮那轮 15.6 秒更短，**远低于记过的「34-44 秒被杀」窗口**）。

#### 落盘 JSON 拉回情况（**第四份未 pull——这是本节最重要的一句**）

| # | 证据文件名 | 字节 | SHA-256 |
|---|---|---:|---|
| 1 | `c1-live-evidence-main.json` | **5919** | `5641c7f3144dd5bc895a08d0b03ff54a1919039f4e4fd1c40e26b10075a363e4` |
| 2 | `c1-live-evidence-budget.json` | **3824** | `890a1f89036092b651cf505d10c722e15443ba1a4440ad627ae2d4026158d46b` |
| 3 | `c1-live-evidence-roundtable.json` | **6399** | `49d437f58ffd282f8a855ed18c6ca7af23f8ace9927cf3b28432d412ec8bcda2` |
| 4 | `c1-live-evidence-vote.json` | ⚠️ **未 pull** | — |

⚠️⚠️⚠️ **第 4 条（vote）只有旁证，没有 app 侧主证据，必须说清区别**：

- **有的**：`am instrument` 原始日志（退出码 `0`、`OK (1 test)`、`Time: 1.842`）
  ＋ **mock 服务端独立记录**（`t4-mock-requests.jsonl`，3 条）。
- **没有的**：**app 自己写出的 `c1-live-evidence-vote.json`**——**本轮没 pull**。
  app **未被卸载**，那份 JSON **按理应仍在设备**
  `/sdcard/Android/data/heizige.kk.khatkit.debug/files/`，**随时可以补拉**。
- ⚠️ **旁证 ≠ 主证据**：服务端日志能证明「**请求发出去了、服务端按预期回了**」，
  **不能**证明「**app 落库了什么、app 侧解析出了什么、app 的多数决选出了什么**」。
  本节**不把服务端记录写成 app 写出的证据**，第 4 条的 app 侧数字**一律不填**。

#### 前 3 条的实际数字（来自 pulled JSON 与 mock 服务端独立记录）

**① pipeline（`c1-live-evidence-main.json`）**

- **3 次真实 HTTP 调用**，wire 模型名依次 **`mock-model-a` → `mock-model-b` → `mock-model-c`**，
  `stream=true`、`include_usage=true`（`User-Agent: ktor-client`）。
- 真实 usage：a `201/34`、b `235/34`、c `235/34`（prompt/completion）
  ⇒ **`Σ(prompt+completion) = 235 + 269 + 269 = 773`**，落库
  `group_runs.spent_tokens = 773`，**逐条相等**。
- ✅ **这个 773 与本文档 C1-02 早已登记的 `773`（以及 `235 + 269 + 269` 的逐项拆分）
  逐字节一致**——两次独立运行**逐位复现**，说明 token 口径这条对账关系是稳定的。

**② 预算截断（`c1-live-evidence-budget.json`）**

- `token_budget_per_round = 1` ⇒ **恰好 1 次调用**（服务端独立记录确认**只收到 1 个请求**，
  `model=mock-model-a`），角色 **B/C 被 skip**。
- `msg_count = 2`（1 条 USER + 1 条 ASSISTANT），
  `group_run`：`status=BUDGET_STOPPED`、`spent_tokens=236`、`token_limit=1`、
  `reason=token_budget_exceeded`、`committed_role_ids=["a"]`、`skipped_role_ids=["b","c"]`。
  ⇒ **截断行为正确**。

**③ roundtable（`c1-live-evidence-roundtable.json`）**

- **议长 C `prompt_tokens = 274` vs A/B 各 `203`** ⇒ **`chairRound` 的可见性放宽
  在真实 prompt 字节里可见**（议长那条的 prompt 确实多带了两位角色的发言）。
  `turn_kind`：a=`speaker`、b=`speaker`、c=`chair`；`chair_role_id = c`。
- **`Σ(prompt+completion) = 239 + 239 + 310 = 788`**，落库 `spent_tokens = 788`，
  **逐条相等**。
- ⚠️ **788 这个数此前没在本文件登记过**（旧登记只有 pipeline 773 / budget 236）。
  ⚠️ 另外，**t3 的 `Σ=788` 是 mock 估算口径**（`ceil(bytes/4)`），**不是真实模型推理**。

#### ⚠️ wire 级模型名：新拿到的真机数字，以及**必须说清的边界**

- ✅ **pulled JSON 里每条发言的 `wire_model_name` 是 `mock-model-a/b/c`**，
  而同一行的 `model_id` 是本地 UUID（`0c1c11ae-…000a` 等）。
  JSON 的 `bindings[].model_string_sent_on_wire` 字段也逐条记着这三个串。
  ⇒ **新增的 wire 优先逻辑在真机上生效了**：这个名字**不是**从 UUID 反查出来的
  （反查出来会是本地配置里的别名，不会恰好等于服务端自报的串）。
- ⚠️⚠️ **但有一处与本文档既有记载不一致，必须订正，不能照抄**：
  t1/t2/t3 这三份 **mock** 证据 JSON 里**根本没有 `wire_model_name_provenance` 字段**
  （`grep -c provenance` = **0**），**不是** `wire_response_model` 也不是别的值。
  原因在源码里能核到：该字段**只有真实网关那份证据的写出器才落**
  （`C1LiveModelSequenceTest.kt:1933`），而 mock 那两个写出器
  （`:1325` / `:1540`）**只落 `wire_model_name`**。
  ⇒ **判定规则第八条那条操作化判据（「看 `wire_model_name_provenance` 是不是
  `wire_response_model`」）对 mock 证据不适用**，得改成看 `wire_model_name` 非空
  ＋ `model_string_sent_on_wire` 对得上。本节按后者判。

#### 这一轮消掉了什么 / 没消掉什么（**逐条，不含糊**）

| 硬理由 | 本轮之后 | 依据 |
|---|---|---|
| ⑤ `C1LiveModelSequenceTest` 其余 4 条未全绿 | ✅ **已消** | 四条退出码全 `0`、全 `OK (1 test)`；⚠️ 第 4 条只有旁证 |
| ① wire 优先逻辑未在真机复核 | ⚠️ **仍不消，但进了两步** | ✅ mock 下已复核（`wire_model_name` = wire 串）；⚠️ **真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑**，设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值** |
| ② 真机 UI 端到端 | ❌ **零份不变** | 本轮零 UI 动作 |
| ③ 酒馆本体 | ❌ **零份不变** | 本轮零酒馆动作 |
| ④ 相机扫码 | ❌ **零份不变** | 本轮零扫码动作 |

⚠️ **所以「硬理由①里『仪器测试一行都没跑过』『设备上那份 JSON 仍是反查值』这两条子理由
在 mock 下已消，但整条硬理由①仍不消**——因为真实网关那次还没在 wire 优先逻辑下重跑过。
⚠️ 同理，**这四条跑的是 mock provider**，所以它们**不能**替代真实网关那一轮；
**「5 条都在真机绿过一次」成立，「10 行里有 10 行的路径被真机调用过」不成立。**

### Unicode 行终止符统一切分（零设备，2026-10-06，HEAD `19545e076`）

⚠️⚠️ **先说清它不是验收证据**：契约 `:206` 点名的四类产物**一份未增**，十行状态列
**仍是 10/10 `unverified`**（硬理由②③④ 一条没被消掉，见下面「取证局限」）。它记录的是
「**一个真实的静默丢票缺陷被定位并堵上 + 口径规则被定下来**」，以及**这些结论各自能被
证明到什么程度为止**。

两笔 commit：

| SHA | 标题 | 改了什么 |
|---|---|---|
| `be7952a83` | `coder: GroupChat 切分统一到 6 个 Unicode 行终止符，修 parseCandidates/parseBallot 静默丢票` | `GroupChat.kt` **+77 / −4**：新增**私有** helper `unicodeLines(text)`（`:994`），改 `parseCandidates`（`:807`）与 `linesOutsideCodeFences`（`:1047`）**两处共用**它 |
| `19545e076` | `coder: GroupChatTest 补 Unicode 行终止符用例，钉住 2 条改值 + 4 条新增护栏` | `GroupChatTest.kt` **+229 / −7**：改 **2** 条既有断言的期望值 + 新增 **6** 个用例方法（`33 → 39`） |

#### 缺陷是什么（口径不对称）

- 原先 `parseCandidates` 与 `linesOutsideCodeFences` **两处都**用 Kotlin 的
  `text.lineSequence()`，它**只在** `\n` / `\r` 处断行。
- 而 Java `Pattern` 的行终止符是 **6 个**：`\n` / `\r\n` / `\r` / `\u0085`(NEL) /
  `\u2028`(LS) / `\u2029`(PS)。
- ⇒ **「切分行」与「正则行终止符」两个口径不对称**：声明行正则
  `^(?:候选|候选项|CANDIDATES?)\s*[:：]\s*(.+)$` 的 `.` 跨不过行终止符、`$` 又只锚行尾，
  所以模型一旦输出 `候选：a\u2028b`，整条正则**必然失配** ⇒ **静默丢候选**；
  票面同理 ⇒ **静默丢票**。方向是**静默丢失**（票配不上候选、候选集为空），
  不是泄漏。

#### 探针实测（`kotlinc` 离线探针，输出在**仓库外** `/tmp/opencode/probe/probe-out.txt`，81 行）

⚠️ **这是离线探针，不是真机 / 真实网关的模型输出**——见「取证局限」。

| 探针 | 实测 | 结论 |
|---|---|---|
| `String.lineSequence()` 切哪些 | LF **2 行** / CRLF **2 行** / CR **2 行**；NEL **1 行** / LS **1 行** / PS **1 行** | 只切 3 个，缺的正好是 **NEL / LS / PS** |
| Java `Pattern` 行终止符反证 | `(?s)候选：a.b` 跨 LS / NEL / PS **全部命中**；`(?m)^b$` 在 `a<sep>b` 上 LF/CR/NEL/LS/PS **全 true** | 三者**本来就是** `Pattern` 的行终止符 |
| 现成 API 对照 | `String.lines()`：NEL **1 行** / LS **1 行** / PS **1 行** | 与 `lineSequence()` **口径完全一样**，⚠️ **不能蒙混过去** |
| `trim()` 与切分的已知不一致 | `isWhitespace('\u2028')` = **true**、`'\u2029'` = **true**（Unicode 类别 13/14 = SPACE_SEPARATOR）⇒ `trim()` **会**剥；`isWhitespace('\u0085')` = **false**（类别 15 = CONTROL）⇒ `trim()` **不剥** | ⇒ **NEL 才是那个真洞**，LS/PS 方向相反 |

⚠️⚠️ **上一轮口头给的两条判断已被实测推翻，这里按实测记**：
① 「前置分隔符的票修后仍能解析」——**错**：实测 `parseBallot("VOTE: \u2028opt-a")` /
`\u2029` / 前置 `\u0085` **三者修后都是 `null`**（同构）。
② 「`trim()` 会剥掉三个分隔符」——**错**：只剥 LS / PS，**NEL 不剥**。
**真正修掉的洞是尾随 NEL**：`parseBallot("VOTE: opt-a\u0085", …)` 修**前是 `null`**
（`trim()` 不剥 NEL ⇒ id 变成 `opt-a\u0085`，配不上任何候选 ⇒ **静默丢票**），
修**后是 `opt-a`** ✅。护栏用例名：`a trailing NEL no longer corrupts the candidate id`
（**这条是「改前红、改后绿」的真洞护栏**）。

#### ⚠️ 一处**有意的行为收紧**（改变了可观察行为，必须登记）

`parseBallot("VOTE: \u2028opt-a", …)`：改**前能**解析出 `opt-a`（靠 `trim()` 剥掉 LS），
改**后返回 `null`**——LS 成了行终止符，`VOTE: ` 成了「一条空 body 的票行」⇒ 集外。
`\u2029`、前置 `\u0085` 三者同构，一律 `null`。

**裁决理由**：`trim()` 剥而切分不剥，**纯属两个 JDK API 口径不一致的巧合，不是设计**；
统一之后「空 body 判无票」，与该文件既定立场**「宁可让畸形 id 配不上票，也不静默改写
用户写的东西」**一致（与 `normalizeCandidateId` 对不成对引号的处理同一口径）。
✅ **新增一条护栏用例把它钉住**：`a ballot with a leading unicode separator is no longer
accepted`。

#### 6 条既有断言：只 2 条变了（行号是**批前** `be7952a83~1` 的实测行号）

| 行 | 输入 | 改前 | 改后 | 处理 |
|---|---|---|---|---|
| `:264` | `候选：a,b<LS>引用：<LS>> 候选：second` | `emptyList()` | `[a, b]` | **改期望值** |
| `:269` | 同上，U+2029 | `emptyList()` | `[a, b]` | **改期望值** |
| `:273` / `:274` | `请大家讨论<LS>候选：second` | `emptyList()` | `emptyList()` | **一字未动** |
| `:276` / `:277` | `<LS>候选：a` | `[a]` | `[a]` | **一字未动** |

⚠️ 后 4 条**一字未动**是**有意**的：它们证明**方向没变**——声明仍在第二个非空行、首行
没声明 ⇒ 仍判 `emptyList()`，**仍然失败关闭，仍然不泄漏**；前置分隔符切出的空行被跳过，
声明仍落在第一个非空行上。

#### 新增 6 个用例方法（`+2` 条改期望，`GroupChatTest` **33 → 39**）

| 用例方法名 | 钉住什么 |
|---|---|
| `unicode line separators cut candidate declarations the same way the regex does` | `parseCandidates` 对 **6 个**分隔符**逐个**断言与正则同口径 |
| `unicode separated VOTE lines are found and the first one still wins` | Unicode 分隔的 `VOTE:` 行**找得到**，且**第一条仍然赢** |
| `code fences are still skipped when the fence lines are unicode separated` | 围栏行本身被 Unicode 分隔时，**围栏内的票仍然被跳过**（围栏判定本身一字未改，只改了「行从哪来」） |
| `a ballot with a leading unicode separator is no longer accepted` | **钉住上面那处有意收紧**（三个分隔符一律 `null`） |
| `a trailing NEL no longer corrupts the candidate id` | **钉住真洞**：尾随 NEL 修前 `null`、修后 `opt-a` |
| `out of set and malformed ballots are still rejected under every separator` | 集外 / 畸形票在**每个**分隔符下**仍然**被拒（防止收紧把闸门放松） |

⚠️ 台账那一列随之 `33 → 39`、合计 `491 → 497`，**类数仍是 45**；app 全模块单测
`915 → 921`、**类数 111 未变**（这两个 commit **没新增测试文件**）。

#### 反向验证：3 处破坏，真实失败数（⚠️ 由**实施子代理**实测，本轮登记时**未重跑**）

| 破坏方式 | 实测失败数 | 说明 |
|---|---:|---|
| helper 退回**只切** `\n` / `\r` | **6 条红** | 缺口被用例咬住 |
| helper 换成 `text.lineSequence()` | **6 条红** | 同上（两个现成 API 口径相同，**都**被挡） |
| **只**把 `parseCandidates` 单独换成 `text.lines()` | **2 条红，且恰好是两条 `parseCandidates` 用例** | 票面 / 围栏那 4 条**仍绿**（`linesOutsideCodeFences` 还在走 helper）⇒ 这是「**两处必须共用**」的**可观察**证据 |

每处破坏后都 `git checkout --` 还原，并以 `git diff HEAD` **输出为空**确认还原。
⚠️ **本轮登记时没有重跑这 3 处**（重跑必须改 `app/src/main`，按硬约束不允许）——
这三个数字是**实施子代理的实测记录**，如实标注归属。

#### ⚠️ 取证局限（本节全部结论的边界，逐条如实记）

- ⚠️⚠️ **零设备**：本轮**没跑任何 `androidTest`**，**一条都没有**。全部证据只有 JVM 层
  （`:app:testDebugUnitTest` **111 类 / 921 例 / 0 失败 0 错误 0 跳过**）。
  `parseBallot` / `parseCandidates` 的真实调用（真实网关、真实模型输出）**零份**。
- ⚠️⚠️ **探针是离线探针，不是真机 / 真实网关的模型输出**：输入是**手写的**含
  `\u0085` / `\u2028` / `\u2029` 的字符串，**没有一条来自真实模型**。
- ⚠️⚠️⚠️ **真实模型是否真的会输出 U+2028 / U+0085 这几个字符，本次没有任何实证**。
  ⇒ 本轮修的是「**一旦出现就静默丢票**」这个**失败模式**，**不是**「模型经常这么输出」。
  **谁要把它读成「这条路径在生产里高频出问题」，那是把「已消除的失败模式」误当成
  「已观测的故障率」。**
- ⚠️ **「假阳性率下降」无实测依据**：那处收紧（前置分隔符 → `null`）方向上是**失败关闭**，
  但**「假阳性率下降了多少」本轮没有任何测量**，只是方向性判断。
- ⚠️ **`unicodeLines` 的大文本性能未测**：helper 是逐字符 `StringBuilder` 扫描，
  **大文本下的耗时 / 分配未测**（与 `lineSequence()` 的对比也没有）。
- ⚠️ **helper 是私有 + 手写扫描**：`Pattern` / `String` 都有各自的口径，这里选手写是
  为了让「6 个分隔符」这条规则**显式可读**。⚠️ **顺带一条本轮实测到的、与源码 KDoc
  说法不一致的地方（如实登记，不改源码）**：KDoc 给出的**不**用 `Regex("\\R")` 的理由是
  「`\R` 会把连续行终止符折叠成空串元素混进行列（`"a\n\nb"` → 3 段含一个空串），还得再
  `filter` 一次才能回到『空行存在』的语义」——⚠️ **本轮实测（`Pattern.compile("\\R").split`，
  JDK 21）该理由不成立**：`"a\n\nb"` → **3 段 `[a][][b]`**，`"a\u2028\u2028b"` → 同样
  **3 段 `[a][][b]`**，即 **`\R` 本来就产生那个空串元素，正是「空行存在」要的语义，
  不需要 `filter`**（`filter` 反而会把它删掉）。`\R` 与手写实现的**真实**差别在两个边缘形状：
  `""` → `\R` 给 **1 段空串**、`unicodeLines` 给 **空列表**；`"\n"` → `\R` 给 **0 段**、
  `unicodeLines` 给 **1 段空串**。⚠️ 本轮**只登记这个差别，不改 `GroupChat.kt`**（硬约束），
  也**没有**据此判定手写实现更好——两条路线的取舍**尚无定论**。

#### 判定影响：十行状态列**一个格都没动**

⚠️ **仍是 10/10 `unverified`**。本轮是**零设备**改动，硬理由**②③④**（真机 UI 端到端 /
酒馆本体 / 相机扫码）**全部原样**，硬理由**①**（wire 级模型名在真实网关下复核）也**未被
本轮触及**。⚠️ **本轮既没有新增任何一类契约 `:206` 产物，也没有消掉任何一条硬理由**——
详见「判定规则」第九条。

### 真实 token 下的预算截断（零 mock，真机，2026-10-06，注入点 `08702d88b`）

⚠️⚠️ **先把性质说清：这是一次真机采集，不是一条「通过」**。`am instrument` 实测
**`Tests run: 1, Failures: 1`** / **`INSTRUMENTATION_CODE: -1`**——失败是**注入截断的预期后果**
（用例写死「期望 3 条助手消息」，截断只产 2 条，见 ⑤），**不是生产缺陷的判据**。
本轮采到的是契约 `:202` 点的「**已用 / 上限 / 未运行角色**」三个数在**真实 token** 下的
库内取值；⚠️ 契约 `:206` 点名的「**实际模型调用序列**」正式 JSON 与 **viewer 可见消息 ID
台账**仍零份 ⇒ **C1-05 仍 `unverified`，十行状态列一个格都没动**（依据见 ⑨）。

**① 之前为什么零份 / 这轮补的是什么**

| 之前的两个口径 | 实测事实 |
|---|---|
| mock 截断用例 `budgetTruncationSkipsRemainingRolesAndRecordsRunLog` | 用 `token_budget_per_round=1`（`C1LiveModelSequenceTest.kt:220` `budgetLimit`）——是**构造入参，不是 provider 返回的真实 token** |
| 真实网关用例 `realProviderRoundRecordsGenuineTokenUsage` | 预算写死 `mainBudget = 100_000`（`:183`），整轮真实 token（约 2.0 万）**够不到停跑线 ⇒ 从不触发截断** |

`08702d88b` 给真实网关那条用例加了 instrumentation 注入点
`-e c1TokenBudgetPerRound <Int>`（参数名 `:185-186`、getter `:204-212`：读
`InstrumentationRegistry.getArguments()`，**缺省 / 空串 / 非数字 / 溢出一律回落
`mainBudget`=100_000**；**只作用于真实网关那条用例**——mock 三模式与 `budgetLimit=1`
基线一字未动）。本轮注入 **9000**。

**② 环境与命令（逐条照记）**

| 项 | 实测值 | 来源 |
|---|---|---|
| 设备 | OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`；无线 TLS `192.168.31.183`（采集报告记端口 `37773`） | 采集子代理报告；⚠️ **证据文件内未回显设备指纹与端口**（与第七轮同一台） |
| 注入点 commit | `08702d88b`（该测试文件最后一次改动，`git log` 实测；HEAD `f507ebc9a` 的测试文件与它一致） | git |
| 构建 | `./gradlew --offline :app:assembleDebug :app:assembleDebugAndroidTest` ⇒ `BUILD SUCCESSFUL in 1m 17s`、`394 actionable tasks: 33 executed, 361 up-to-date` | `build.log` 尾部（退出码 **0** 来自采集报告；日志本身只记 `BUILD SUCCESSFUL`） |
| 安装 | 两次 `adb install -r -t`（主 APK + androidTest APK）均 `Success` | `install-main.log` / `install-test.log` |
| 跑法 | **手动** `am instrument`（未用 `connectedAndroidTest`）；用 `setsid nohup` 脱离运行 | 采集报告；⚠️ 因脱离运行 shell 退出码无人回收（见 ⑧-2） |
| 结果 | `Tests run: 1, Failures: 1`、`INSTRUMENTATION_CODE: -1`、`Time: 585.298` | `instrument-9000-rerun.txt` |

命令原文（⚠️ 命令行未在证据文件内回显；`-e … 9000` 由结果里 `token_limit=9000` 佐证）：

```bash
adb shell am instrument -w -r -e c1TokenBudgetPerRound 9000 \
  -e class 'heizige.kk.khatkit.app.feature.chat.C1LiveModelSequenceTest#realProviderRoundRecordsGenuineTokenUsage' \
  heizige.kk.khatkit.debug.test/androidx.test.runner.AndroidJUnitRunner
```

**③ 七个值（主机侧逐值复核；跨两个库）**

会话与消息走**独立证据库** `c1-live-evidence.db`（测试类 KDoc `:129-132`、`EVIDENCE_DB` `:2226`）；
`group_runs` **无法隔离**、留在生产库 `rikka_hub`（同 KDoc，`ChatManager.kt:231` 的注释）。
所以 1–6 的值在 `rikka_hub`，第 7 值在证据库：

| # | 字段 | 值 | 出处（实测） |
|---|---|---|---|
| 1 | `spent_tokens` | **13631** | `db-poll/s2/rikka_hub` → `group_runs` |
| 2 | `token_limit` | **9000** | 同上 |
| 3 | `skipped_role_ids` | **`["c"]`** | 同上 |
| 4 | `committed_role_ids` | **`["a","b"]`** | 同上 |
| 5 | `status` | **`BUDGET_STOPPED`** | 同上 |
| 6 | `reason` | **`token_budget_exceeded`** | 同上 |
| 7 | 库内助手发言条数 | **2 条**（a、b；加 user 共 **3** 个 `message_node`） | `db-poll/e1/c1-live-evidence.db` → `message_node`（node0=user / node1=a / node2=b） |

- conversation = `8e060471-a124-48cb-a213-429bd9ab8cf2`，round = `round-4db4c9d7-5eef-42f5-a5e2-66a0c1d3e7d1`。
- ⚠️ **s2 是前六个值的唯一存证**：更晚的 `s5` 快照里该行已被 `@After` teardown 删除（行数 0）；
  `s2check` 与 s2 逐值相同。
- ⚠️ **`rikka_hub` 的 `message_node` 里没有该会话的任何行**——主机复核实测；第 7 值只能从证据库取，
  别写成「七个值同库」。

**④ 用量自洽（与库内 `spent_tokens` 精确相等）**

| 角色 | wire 名 | prompt | completion | cached | total | 正文原文（均有 reasoning part） |
|---|---|---:|---:|---:|---:|---|
| a | `deepseek-v4-flash` | 6829 | 101 | 6144 | **6930** | `ROLECODE:A 我是A，已就位。` |
| b | `glm-5.2` | 6643 | 58 | 0 | **6701** | `ROLECODE:B 收到，B已就位。` |

**6930 + 6701 = 13631 = `spent_tokens`** ✅。截断算术：a 后累计 **6930 < 9000** ⇒ b 执行；
b 后累计 **13631 ≥ 9000** ⇒ c 跳过。⚠️ 「**c 从未被调用**」的库侧依据是：无 c 的消息、
无 c 的 usage，与 `skipped_role_ids=["c"]` 一致——**这是 DB 痕迹上的推断**，没有独立
wire 抓包直接证明「c 没被调用」。

**⑤ 失败断言原文（`awaitAssistantMessages` 先炸）**

```
java.lang.AssertionError: 等待助手消息超时（300000ms）：期望 3 条，实际 2 条。最后看到的消息=[USER/null, ASSISTANT/a, ASSISTANT/b]；app 错误=[]
	at heizige.kk.khatkit.app.feature.chat.C1LiveModelSequenceTest.awaitAssistantMessages(C1LiveModelSequenceTest.kt:2126)
	at heizige.kk.khatkit.app.feature.chat.C1LiveModelSequenceTest.realProviderRoundRecordsGenuineTokenUsage(C1LiveModelSequenceTest.kt:1265)
```

`realTimeoutMillis = 300_000L`（`:234`）；用例写死 `expected = 3`（`:1323`），而注入截断
只产 2 条 ⇒ **该 await 必然先超时**。`Time: 585.298` 含熄屏冻结（见 ⑧-4）。
⚠️ **这也意味着这条用例在「预算截断」路径上必然红**——这不是本轮引入的缺陷，
而是「用例期望值 vs 注入截断」的正面冲突；要让它变绿得改用例断言，不在本轮授权内。

**⑥ 现场 trace（`files-rerun2/c1-live-trace.txt`，跨跑追加；9000 那轮的关键行）**

`real:preflight-ok baseUrl=https://api.zenneko.top/v1` → `insertGroup:inserted id=8e060471-…8cf2`
→ `real:conversation-inserted` → `real:sendMessage-returned` → 轮询到
`await:msgs poll=40 assistants=2 all=[USER/null, ASSISTANT/a, ASSISTANT/b]` →
**没有 `real:await-messages-done`**（与 ⑤ 吻合）→ 以 watchdog dump 收尾。
⚠️ trace 是三次 pull（`files/`、`files-rerun/`、`files-rerun2/`）里**唯一变化**的文件；
另 16 份 app 侧 JSON 三次**逐字节相同**（这本身就是 ⑧-1 的旁证）。

**⑦ 证据文件 SHA-256（本机 `sha256sum` 实测；全部在仓库外 `/tmp/opencode/c1-budget-real2/`）**

| 文件 | 字节 | SHA-256 |
|---|---:|---|
| `db-poll/s2/rikka_hub` | 154624 | `f693176a2bf26006a9111cb103aad3cc559024dc3835810caf1b7eaa8899332f` |
| `db-poll/e1/c1-live-evidence.db` | 95232 | `df0cb26a748d42f742778f083d2593ae2338365f7a202decaa88d1faa2a817d5` |
| `db-poll/e1/node0.json` | 359 | `46f0a778915c6991bd1110ebba28425a5925586d0b7dfea4070bef95a9017b96` |
| `db-poll/e1/node1.json` | 1101 | `5e85158c6a5784115108f96890ff88e5b3fdff7d9acc37933160c9261778dd5e` |
| `db-poll/e1/node2.json` | 910 | `197124e7d27564f8c9692c47d6a19b218863c46050f20c935457af3c64920290` |
| `instrument-9000-rerun.txt` | 5557 | `9659362cbdd0c6bc9f56ea181c6c697af4c68ad1afe2d81765495939fac00bfc` |
| `logcat-rerun.txt` | 1985109 | `fd75d9c8c393768a03109e1a25c719756d2c0edffcf1a92731233f8d1aaecd14` |
| `files-rerun2/c1-live-trace.txt` | 281520 | `9542cefc41d974855e80fc60fa0e585ddaa00981d47d74524d92af2f1ab153d3` |
| `files-rerun2/c1-real-raw-dump.json`（⚠️ 陈旧，见 ⑧-1） | 2919 | `e50bf524f1ebbdc4abe8b3666de57ec157dfcab477484b2cfb06f96a905e919e` |
| `build.log` | 34280 | `838b43cd5450153392eb58a8d1eb5307d87e8a06c7f4d151519071fdb0d4ce61` |
| `install-main.log` / `install-test.log` | 36 / 36 | `781a31c82430c97897af612ece5e0ca2205e301cb5ff121ce2660541ea5182c7`（两份相同） |

⚠️ `files-rerun2/` 共 17 个文件；除 trace 外其余 16 份三次 pull 逐字节相同，
**不逐条登哈希**（避免把陈旧文件冒充本轮产物）。

**⑧ 四条限制（如实登记，不美化）**

1. **`c1-real-raw-dump.json` 本轮没有被写出，且证明它在截断路径下永远写不出来**：
   dump 的 `writeEvidence("c1-real-raw-dump.json", …)`（`:1338-1339`）在
   `awaitAssistantMessages`（`:1321-1325`）与 `awaitTerminalRun`（`:1329`）**之后**；
   截断只产 2 条助手消息 ⇒ 该 await 必然先超时炸 ⇒ **永远执行不到写入**。
   ⇒ 七值只能从**运行中 DB 快照**获取。
   ✅ **主机侧旁证**：`files-rerun2/c1-real-raw-dump.json` 与更早 `files/` 那份**逐字节相同**
   （sha `e50bf524…`），内容是**另一次跑**的 conversation `166e3e5b…`（a completion **139**、
   正文 `ROLECODE:A 三人依次，我先来。`）——**不是本轮 9000 跑**。
2. **`am instrument` 的 shell 退出码无法提供**：`setsid nohup` 脱离运行，退出码无人回收。
   权威结果是 `INSTRUMENTATION_CODE: -1`、`Tests run: 1, Failures: 1`
   （`INSTRUMENTATION_STATUS_CODE: -2`）。
3. **`wire_model_name_provenance_counts` / `wire_model_name_reconciliation` / 正式
   `actual_model_call_sequence` 仍然零份**——它们只写在 `c1-live-evidence-real-provider.json`
   （`:1535-1536`，测试**通过**才写），该文件历史以来从未产出过。DB 快照里的原始事实是：
   a `wireModelName=deepseek-v4-flash`、b `wireModelName=glm-5.2`；两 uuid 对应的 provider
   表名（`DefaultProviders.kt:307-308`、`:312-313`，实测行号）与 wire 名一致。
   ⚠️ 「两条调用的 provenance 是 `wire_response_model`」是**派生推断**，不是测试落盘证据。
4. **设备侧副作用（如实登记）**：
   - 手机在**熄屏约 30 秒后被 ColorOS 冻结整进程**（`screen_off_timeout=30000`），两次导致
     测试卡住（`Time: 585.298` 含约 9 分钟冻结）。执行者用 `am start` 拉回前台解冻，并设了
     `svc power stayon true`（**原值未记录**）。
   - 首轮冻死留下**一条 `RUNNING` 残留行**（conversation `2b6c129f-…9480`，round
     `round-a8d28946-…ca53`，spent=0），**未删除**——`s5` 快照实测仍在。
   - 未 `pm clear` / `pm uninstall`，未用 `connectedAndroidTest`，未 kill gradle / Kotlin daemon。
   ⚠️ 本条的设备细节（`screen_off_timeout` / `svc power` / `am start`）来自采集报告，
   **证据文件内无对应命令输出**。

**⑨ 判定影响：状态列一个格都没动（仍是 10/10 `unverified`）**

按契约 `:206` 的验收清单逐类对：

| 契约 `:206` 清单 | 本轮状态 |
|---|---|
| 输入 | ⚠️ 部分（用户消息文本在证据库 node0；无独立落盘 report） |
| 各角色可见消息集合（viewer 台账） | ❌ **一份未增** |
| 实际模型调用序列 | ❌ 正式 JSON **仍零份**（见 ⑧-3） |
| token 计数 | ⚠️ 有库内七值，但**没有测试落盘的 token report**（那条 JSON 也没写出来） |
| 导出哈希 | — 本例不适用 |

⇒ **C1-05 不足以升级为 `verified`**，本文件只登记证据。
⚠️ **状态单元格里旧有的「三个数在真实调用下仍然零份」「真实 usage 下的截断仍零份」两句按
本轮硬约束（状态列一个格都不许动）未改**——其订正以本节为准；`模型调用序列` 列与
`token (prompt+completion)` 列已就地追加订正块。

### C1 真机证据采集第八轮（2026-10-06，HEAD `31b6b5a52`：**真实网关正式证据历史首次产出 + 预算截断 finally 落盘真机验证 + mock 4/4 + 全量 64/1 + UI 部分覆盖**）

⚠️⚠️ **先说性质：本轮 = 一次真机采集窗口 + 两个测试侧 commit（`43d607bdd` / `31b6b5a52`）**，生产代码零改动。设备与第七轮同一台：OnePlus **PKG110**（`OP5D2BL1`）/ Android **16** / API **36** / `arm64-v8a`，无线调试 `192.168.31.183:37773`。⚠️ **20 个状态格一个判定都没改，仍是 10/10 `unverified`**——① 句的订正逐格追加（见 ⑦），逐行不升级依据见 ⑨。

**① 历史首次产出 `c1-live-evidence-real-provider.json`（本轮最大成果）**

这份「**全部断言通过才写**」的正式证据**历史以来从未被写出过**。本轮 `real-default/` 跑第一次产出它并把目录拉回主机（产物留在仓库外；本机目录 `/tmp/opencode/c1-device-round3/real-default/files/`）。

| 文件 | 字节 | SHA-256（本机 `sha256sum` 实测） |
|---|---:|---|
| `c1-live-evidence-real-provider.json` | 10441 | `3b02140b216be4b684927d59a990726ba231d461dc665cf504559814c147b8b1` |
| `c1-real-raw-dump.json`（finally 落盘，非正式证据） | 6017 | `182449760d43e3dad28e105715fc1551dfaaebea6a127ef765bb35f070d462a3` |
| `c1-live-trace.txt` | 309049 | `2a8bbefd191cdfa71ae112315a7fc5ce94eb9a04fd1fe8a7073a1d61ef1ee219` |
| `ui-e2e-evidence.txt`（夹具证据，见 ⑤） | 6493 | `0bdc12d8805d706864a871fc1a4166ac4d006bbdc09499846fde28d9af042314` |

跑法与结果：

```bash
adb shell am instrument -w -r \
  -e class 'heizige.kk.khatkit.app.feature.chat.C1LiveModelSequenceTest#realProviderRoundRecordsGenuineTokenUsage' \
  heizige.kk.khatkit.debug.test/androidx.test.runner.AndroidJUnitRunner
```

- **退出码 0**（执行者报告；⚠️ 证据文件里只有 stdout：`Time: 14.017`、`OK (1 test)`，**shell 退出码本身未落盘**）。
- 设备块（JSON `device`）：`sdk=36`、`model=PKG110`、`fingerprint=OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`、`package_name=heizige.kk.khatkit.debug`、`evidence_db=c1-live-evidence.db`。
- provider：`极客猫` / `https://api.zenneko.top/v1` / `provider_uses_response_api=false`（走 `POST /chat/completions`）。
- **模型名 provenance（硬理由①的消点）**：`wire_model_name_provenance_counts = {"wire_response_model": 3, "uuid_reverse_lookup_fallback": 0}`；`wire_model_name_reconciliation = {calls_with_wire_name: 3, calls_where_both_names_present_and_differ: 0, calls_with_no_name_at_all: 0}`；每条调用 `wire_and_reverse_lookup_agree=true`。**三条调用的模型名全部来自网关响应自报的 `model` 字段，没有一条走 uuid 反查回退。**按本文件「判定规则」第八条的操作化判据，**硬理由①对真实网关这条路径已消**。

**三角色真实 usage（逐条从 JSON 核对；Σ = 20731 = `group_run.spent_tokens`）**：

| 角色 | wire 模型名 | prompt | completion | total | 正文 |
|---|---|---:|---:|---:|---|
| a | `deepseek-v4-flash` | 6829 | 151 | 6980 | `ROLECODE:A 我是A，已就位。` |
| b | `glm-5.2` | 6692 | 60 | 6752 | `ROLECODE:B 收到，我已就位准备开始。` |
| c | `deepseek-v4-flash` | 6895 | 104 | 6999 | `ROLECODE:C 我是C，发言完毕。` |

`sum_prompt_plus_completion = 20731 = spent_tokens`、`spent_tokens_equals_sum_prompt_plus_completion = true`；`group_run.status=COMPLETED`、`token_limit=100000`、`committed=["a","b","c"]`、`skipped=[]`、`reason=""`、`ended_at` 非空。
raw dump 现场：`block_exception_pending=false`、`block_exception=null`、`assistant_message_count=3`、`group_run_status=COMPLETED`、`spent=20731`、`limit=100000`、`committed=["a","b","c"]`、`skipped=[]`。
trace 关键行：`1791277101097 real:preflight-ok baseUrl=https://api.zenneko.top/v1` → `1791277114827 real:await-messages-done count=4` → `1791277114924 real:raw-dump-written messages=4 assistants=3 run=COMPLETED`；运行中轮询 `poll=20 assistants=1 [USER,a]` → `poll=40 assistants=2 [USER,a,b]` → done 4（user+a+b+c），**逐段出现顺序 a→b→c 与序列一致**（⚠️ 角色粒度对照；trace 里**没有逐次调用的模型名**）。

⚠️⚠️ **三条必须连着读的诚实点**：
1. **角色 c 上一轮那次的失败本轮没有复现**——两次默认预算运行都成功（执行者报告；可核产物是 16:58 这一跑）。⇒ 上一轮那个「非 2xx + 空 body 被静默当零产出成功」的现场是**偶发**，不是稳定缺陷。**必须说清因果**：`43d607bdd` 只是把静默失败**变响亮**，**没有加任何重试**，它**不构成「c 现在能成功」的原因**。

   > ⚠️⚠️ **第十四轮订正（HEAD `b60aa80c5`，2026-10-06）——本段的「偶发、不是稳定缺陷」结论被本批推翻（原文保留）。** 本批 **6/6 稳定复现**了**零产出**症状：roundtable 3 跑 + pipeline 3 跑，第 3 位顺序发言者「角色 c」一律落为 `turn_kind=error` / 正文「[角色丙] 本轮生成失败：本轮没有产出内容」/ `model_id=null` / usage 全 `-1` / `group_run=FAILED/role_failed/committed=[a,b]` / `app_errors=[]`。⚠️ **两处的触发形态必须分清**：本段原说的「非 2xx + 空 body 静默当零产出」是**上一轮**那个现场；本批复现的是**同一下游症状（零产出）的另一触发——网关 HTTP 200 但 SSE 流里没有任何 content delta**。所以「偶发」这一表述**对零产出症状不再成立**（6/6 稳定），但**不能反推**「上一轮那个非 2xx 现场也稳定」——本批没抓到任何非 2xx 现场。真因、`app_errors` 采集盲区与 refute 掉的假设见「C1 真机证据采集第十四轮」③，遗留见第 45/46 条。**判定规则第十条的正文亦据此加订正块。**
3. raw dump 在 `finally` 无条件写（`31b6b5a52`）；**正式报告仍然只在全部断言通过时写**（语义未变——预算截断那一跑就没写出它，见 ②）。

**② 预算截断（9000，真机）：`31b6b5a52` 的 finally 落盘修复真机验证通过**

在 `real-budget9000/`：同一方法 + 注入 `-e c1TokenBudgetPerRound 9000`（`08702d88b` 的注入点）。

| 文件 | 字节 | SHA-256（本机实测） | 与 `real-default/` 对比 |
|---|---:|---|---|
| `c1-real-raw-dump.json` | 5039 | `e29046fa8d3dfe6033716ef1a50a083ea834d6bcb9094c20f390d4ca40d9c729` | **不同（本轮新写）** |
| `c1-live-trace.txt` | 357828 | `a3647f8cba9954790b650e73a7558277bd3a9595f134b651ccf003fac6ac612e1` | **不同（追加了本轮行）** |
| `c1-live-evidence-real-provider.json` | 10441 | `3b02140b216be4b684927d59a990726ba231d461dc665cf504559814c147b8b1` | **逐字节相同 ⇒ 正式通过证据未被改写** |
| 其余 16 份 | — | （未逐条登哈希） | 逐字节相同 |

- ✅ **落盘通道打通**：设备端 `c1-real-raw-dump.json` 的 mtime 前进到 **17:10**（正式证据仍停在 16:58 默认跑）——上一轮「截断路径下永远写不出来」的问题已修好（`31b6b5a52` 把写入挪进 `finally` 并重读现场）。⚠️ **两者 mtime 为执行者设备侧实测（本机无法直接复核设备 mtime）**；本机 pull 副本的 mtime 分别是两次 pull 的时刻（`real-default/` 16:58、`real-budget9000/` 17:10，**不能当设备 mtime 引用**），可核的是 **SHA 对比**：raw dump 两次不同（本轮新写）、正式证据两次逐字节相同（未被改写）。
- raw dump 内容：`block_exception_pending=true`；`block_exception="AssertionError: 等待助手消息超时（300000ms）：期望 3 条，实际 2 条。最后看到的消息=[USER/null, ASSISTANT/a, ASSISTANT/b]；app 错误=[]"`；`assistant_message_count=2`；`group_run_status=BUDGET_STOPPED`、`spent=13571`、`limit=9000`、`committed=["a","b"]`、`skipped=["c"]`、`reason="token_budget_exceeded"`。
- `wire_name_resolution` 2 条（a/b，均 `wire_response_model`、`wire_and_reverse_lookup_agree=true`）；counts `{wire_response_model:2, uuid_reverse_lookup_fallback:0}`；reconciliation `{calls_with_wire_name:2, differ:0, no_name:0}`。⚠️ **raw dump 是排查文件、不是正式证据**（文件自带 `pass_evidence_note`），这次它记录的是**失败跑的诊断现场**。
- trace 关键行：`1791277823809 real:raw-dump-written messages=3 assistants=2 run=BUDGET_STOPPED`。
- ⚠️ **这次单跑的 stdout 丢失**（本地 adb 客户端在 600s 超时被杀，恰逢设备被 ColorOS 冻结）⇒ **shell 退出码、`Tests run`、`Time` 全都没有**；权威结果 = 设备侧证据文件 + trace；`logcat-budget9000.txt` 只有一条 `ActivityManager: Failure reporting to instrumentation watcher`。
- ⚠️ **`spent=13571` 与上一轮 `13631` 不同**——completion token 的真实波动（同一路径两次独立运行），**不是矛盾**。来源也不同：上一轮是**运行中 DB 快照**（第九批/第十四批已登记），本轮是 **raw dump 落盘**。**两轮都保留；本轮的意义 = 「落盘通道打通」。**

**③ 4 条 mock 仪器测试 4/4 通过（`mock-t1..t4/`）**

| # | 方法 | 退出码 | `OK` 原文 | Time | 证据文件 | 字节 | SHA-256（本机实测） |
|---|---|---:|---|---:|---|---:|---|
| 1 | `pipelineRoundRecordsRealModelSequenceAndRealTokenUsage` | 0 | `OK (1 test)` | 2.997 | `c1-live-evidence-main.json` | 5919 | `a5f32571623fb699c0033de578bceaabe1e9c9cb3cad2262cf0e3922d46d6e21` |
| 2 | `budgetTruncationSkipsRemainingRolesAndRecordsRunLog` | 0 | `OK (1 test)` | 2.832 | `c1-live-evidence-budget.json` | 3824 | `e87ae84c9b28cc09fe4ed0ff08cef78ef6fdf34ed4dc5d2ab1ce089eccf7640f` |
| 3 | `roundtableRoundRecordsChairSummaryCallSequence` | 0 | `OK (1 test)` | 2.997 | `c1-live-evidence-roundtable.json` | 6399 | `a10d24389a7680d96ab4af6e66e0e33f4cc7f229bd3d929649c95c0b238d4890` |
| 4 | `voteRoundRecordsBallotCallsAndSummarySequence` | 0 | `OK (1 test)` | 3.022 | `c1-live-evidence-vote.json` | 7709 | `999b251cd177321f16fef5d6b45b6c62b4d2acade1f6cd627ca207aab9b8235a` |

数字（逐条从 JSON 核对）：t1 **Σ=773** / `COMPLETED` / committed=[a,b,c] / `wire_model_name`=`mock-model-a/b/c` 且 `bindings.roles[].model_string_sent_on_wire` 与之逐条相等；t2 `spent=236 / limit=1 / BUDGET_STOPPED / committed=[a] / skipped=[b,c] / reason=token_budget_exceeded`，恰好 1 次调用；t3 **Σ=788** / a/b=`speaker`、c=`chair` / `viewer_visible_message_ids` 含 `c(chairRound=true)` 全员可见；t4 **Σ=780** / `tally={winner:opt-a, counts:{opt-a:2, opt-b:1}, outcome_type:VoteOutcome.Decided, tie_branch_taken:false}` / 三张 `VOTE:` 选票由 `parseBallot` 解析。

✅ **「SHA 每轮都不同、把 UUID 与 13 位时间戳规范化后逐字节相同」——既有结论本轮再次确认**（我自己复算）：本轮四份与上一轮 `/tmp/opencode/c1-budget-real2/files-rerun2/` 逐份规范化（UUID→`<UUID>`、13 位 epoch-ms→`<TS13>`）后，**四对规范化 SHA-256 逐对相等**（main `4b1b8338…`、budget `53754697…`、roundtable `dee72310…`、vote `3fb9c957…`），原始 SHA 全不同。⚠️ 这是**后处理复核**，不代表原始文件可互换。

⚠️⚠️ **透明说明（必须记）**：mock 脚本 `/tmp/opencode/roundtable-vote/mock_openai_v2.py` 的磁盘本体曾丢失，执行者从 opencode.db 的工具调用记录里**逐字节恢复**。我复核了这条：恢复件 **20061 B** / sha256 `d5c5d9a4c4b09917b8e31379484f783bef17059b13dfb7ab0a96041daae3f8a0` / `py_compile` 通过；用 `parts/prt_10b67ba…`（write 记录）+ `parts/prt_10b6ab4e…`（edit 记录）**按序重放后与恢复件逐字节相同**。⇒ **脚本本体已不在原磁盘路径，复现依据 = 工具调用记录恢复件**；4/4 通过 + token 数与文档基线逐项吻合是它的正确性反证（不是「原件仍在原处」）。

**④ 全量仪器测试（`full-suite/`）**

- `Tests run: 64, Failures: 1`、`Time: 48.269`、**15 个类**（清单 `full-suite/full-suite-classes.txt`，实测 15 行）、`INSTRUMENTATION_CODE: -1`。
- 唯一失败 = `parseMentionsFromUiText` **缺 `-e uiText` 参数**——断言原文「必须用 -e uiText 传入从 uiautomator dump 读回的输入框文本」，**设计如此、非回归**（带参单跑 `OK (1 test)`，见 ⑤）。
- `IllegalStateException: The singleton image loader has already been created` 全文 **0 命中** ⇒ Coil 单例崩溃未再出现、无 Coil 崩溃。
- **真实网关那条在全量内也通过**（`realProviderRoundRecordsGenuineTokenUsage`：`INSTRUMENTATION_STATUS_CODE: 0`），并重新产出了全套证据文件（SHA 与单跑不同——UUID/时间戳变了，未逐条登记）。

**⑤ UI 端到端（`ui/`）：部分覆盖，不许拔高**

untracked fixture `C1GroupUiE2EFixtureTest.kt`（实测 **461 行 / 3 个 `@Test`**）：① `seedGroupConversationIntoMainDb`（无参，**先删后插**的幂等写入群会话 + 2 条单聊到主库 `rikka_hub`）；② `readBackMainDbState`（无参，SQL 读回节点数 / 按 type 分组）；③ `parseMentionsFromUiText`（必须 `-e uiText`）。

- seed/readback 在全量中通过；收尾重跑 readback：`conversation_entity_rows=6`、`DIRECT=4 | GROUP=2`（**4+2=6，分组和==总数 ✅**）、群节点 **7**（7 个 id 全为夹具 id）。⚠️ **中间态如实登记**：16:58 那次设备文件里第一次读回是 `group_conv_message_node_rows=15`（多出 8 条非夹具节点，模型全为 `5a86b2d6`，来源未核实；夹具 seed 为「先删后插」）——**最终口径以 7 为准**。
- 真机打开群聊（`am start --es conversationId <夹具id>`）：截图 `ui-06`（标题「C1 UI E2E 群聊」/ 副标题「pipeline · 3 个角色」/ 三条角色消息 / **左下叠放成员头像组**——我逐张看过截图）；点输入框输入 `@` 弹出选择器（`ui-07`，uiautomator dump 里同时有 `@` 与 `Alice Johnson`/`alice`）；点选后输入框文本回读 = `@Alice Johnson `（`ui-08` / `ui8.xml`），用它跑 `parseMentionsFromUiText` → `OK (1 test)`（`Time: 0.026`），`parsed_role_ids=[alice]`、`anti_spoof_at_johnson=[]`、`anti_spoof_at_john=[]`、`anti_spoof_email=[]`、`positive_at_alice=[alice]`、`positive_at_gamma=[gamma]`。
- 筛选 chip：真实点击 群聊/单聊/全部（截图 `ui-11/12/13`）——**我逐张看过：蓝色勾选态分别落在群聊/单聊/全部**；⚠️ uiautomator dump 里三态 `selected` 全为 false（Compose 不导出），所以**选中态只有截图目视**。

**三件覆盖度（如实标注）**：
- **@ 选择器**：**覆盖较好**（UI 弹出 + 输入框真实文本回读 + 生产解析函数真机通过）。
- **成员头像组**：**无自动化断言**——仅截图目视（消息模型头像 + 输入区左侧叠放头像）；**未验证交互**。
- **筛选 chip**：「切换不丢数据」有 DB 硬证据（切换前后行数/分组计数不变）；**「真的按类型过滤出/滤掉会话」未验证**——三种 chip 下列表都显示「没有对话记录」，原因见下。

⚠️⚠️ **设备卫生问题（必须登记）**：全量测试后主 DataStore 里的助手被替换成测试助手「角色甲/乙/丙」（id `0c1c11ae-…a1/b1/c1`），默认助手 `0950e2dc…` 消失——我核了 17:18 拉回的 `settings.pb`（**22365 B / sha256 `924cda89ec5b8140e71540456c4f1b8b16569b05c85730a413305292468845bd6`**）：只有三个测试助手，`0950e2dc` 出现 **0 次**；17:21/17:22 截图底部当前助手 =「角色甲」。夹具会话归属 `0950e2dc` ⇒ **列表恒空**（与三种 chip 全空吻合）。`C1LiveModelSequenceTest` 的 `@After` 恢复（`runCatching` 吞异常）显然未生效或被污染快照覆盖。**执行者没有改动设备设置去修。** ⇒ 登记为「已知遗留与风险」**第 34 条**（下次用 UI 夹具前需先恢复 settings）。

**⑥ 设备副作用（如实登记）**

- `screen_off_timeout` 原值 **30000**（system 命名空间；global 为 null）。执行中临时调到 **1800000**，**收尾已恢复为 30000（已确认）**。
- `svc power stayon true` 已设；实测 **ColorOS 会把它重置**（中途 `mStayOn=false`），本次靠 `input keyevent KEYCODE_WAKEUP` + `monkey` 拉回前台解冻。
- 未 `pm clear` / `pm uninstall`，未用 `connectedAndroidTest`。
- ⚠️ 上一轮留下的 `RUNNING` 残留行仍在（`conversation 2b6c129f-…9480`、`round round-a8d28946-…ca53`、`spent=0`）——**本轮未清理**（trace 里仍能看到它）。
- ⚠️ 本条的设备细节（timeout 原值/恢复、`svc power`、`input keyevent`、`monkey`）**部分来自执行者转述**；可核的是 `settings.pb` 与两份 trace。

**⑦ 硬理由①的判定（本轮最重要的一条）**

**已消——但只针对「真实网关那条路径」。** 依据就是「判定规则」第八条自己的操作化判据：
- **真实网关**：设备落盘正式 JSON 的 provenance counts = `{wire_response_model: 3, fallback: 0}`（见 ①）。
- **mock 一侧**：按第七轮订正的第二套判据核——t1–t4 的 `wire_model_name` 是服务端自报串（`mock-model-a/b/c`）且 `bindings.roles[].model_string_sent_on_wire` 与之逐条相等（见 ③）。

⇒ **「模型名不是 wire 级抓包」这条子理由在两类证据里都不再成立。**

⚠️ **20 个状态格的处理**：对 20 格里那一句「真实网关还没在 wire 优先逻辑下重跑过 / 设备上那份 JSON 仍是反查值」**逐格就地追加订正段**（原文一字未删）：

> ⚠️ 订正（HEAD `31b6b5a52`，2026-10-06）：本句已过期——真实网关用例已在 wire 优先口径下重跑并**首次产出**正式证据 `c1-live-evidence-real-provider.json`（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`，SHA/字节见「C1 真机证据采集第八轮」）⇒ **硬理由①对真实网关那条路径已消**（判据 = 「判定规则」第八条）；⚠️ **本行状态仍是 `unverified`**（逐行依据见第八轮⑨）。

⚠️ **但这不等于任何一行的「四类产物」齐了**：真实网关这一跑覆盖的只是 **pipeline（C1-02 的路径）**；其余各行点名的路径没有被它调用（C1-05 见 ②：raw dump 是诊断文件、正式序列 JSON 未产出）。**逐行判定见 ⑨。**

**⑧ 本批新增/刷新的限制（不许美化）**

1. 真实网关正式 JSON 的 **shell 退出码未落盘**（只有 `OK (1 test)` stdout；退出码 0 为执行者转述）。
2. 预算截断跑 **stdout 丢失**：无退出码、无 `Tests run`、无 `Time`。
3. **角色 c 的偶发失败未复现、本轮无任何 HTTP 状态码**；`43d607bdd` 只负责「变响亮」，不构成修复因果（见 ①）。
4. **mock 脚本本体已不在磁盘**（恢复件 + 工具记录重放见 ③）。
5. **UI 三件覆盖度不齐**（见 ⑤）。
6. 全量里真实网关那一跑的产物 SHA 未逐条登记（UUID/时间戳每轮都变，属既有口径）。

**⑨ 判定影响：20 个状态格一个判定都没改（仍是 10/10 `unverified`），逐行依据**

| 行 | 本批新增了哪类产物 | 仍然缺哪一项（不升级的理由） |
|---|---|---|
| C1-01 | —（真实网关跑的是无 @ pipeline） | **显式 @ 的投递收窄**无真机证据（夹具的 @ 仍落在用户消息上） |
| C1-02 | **最接近**：wire 级序列 + 真实 token（Σ=20731）+ viewer 台账 + 输入 + 全绿记录 | ① 「顺序与运行日志一致」的**逐调用运行日志对照没做过**（trace 只有角色粒度轮询；`group_runs` 无逐次调用记录）；② viewer 台账写进通过证据但**不是独立断言**（测试只断言角色级隔离）；③ shell 退出码未落盘。**三项都补上再升级** |
| C1-03 | mock t3 议长序列（真机 mock，wire 名） | **真实网关 roundtable 议长汇总轮**从未跑过；议长前可见性只有夹具层 |
| C1-04 | mock t4 的 3 票 + tally（真机 mock，wire 名） | **真实网关 vote** 从未跑过；平票按配置失败那条路真机零份 |
| C1-05 | 预算截断 raw dump 首次落盘（wire 2/0；13571/9000/BUDGET_STOPPED） | raw dump **不是正式证据**；正式 `actual_model_call_sequence` JSON 未产出；viewer 台账缺；这次跑不是通过 |
| C1-06 | — | 取消/超时路径真机零份 |
| C1-07 | — | 同 `round_id` 重试幂等的真机台账零份 |
| C1-08 | — | 三个记忆空间检索可见性 + `source_message_id` 归因零份 |
| C1-09 | — | 真机导出文件 SHA / 酒馆本体 / 相机扫码零份 |
| C1-10 | UI chip 真机三态点击（截图）+ 切换前后 DB 计数不变 | **「真的按类型过滤」未验证**（列表恒空，见 ⑤ 卫生问题）；成员头像组无断言 |

⇒ **一个 `verified` 都没有**：按「判定规则」第 2/6/8 条逐行核完，没有任何一行「四类产物在本行点名路径上全齐」。⚠️ **C1-02 是本批离升级最近的一行，本批选择不升级**——理由列在上表；要升级得先把那三项补齐并在证据登记表追加一行。

### C1 真机证据采集第九轮（2026-10-06，HEAD `4d73a26b4`：**筛选 chip 两半真机通过 + 设置污染 pb 手术恢复 + 酒馆解析器级接受 16/16 + 新缺陷「删最后一个助手必崩」**）

⚠️⚠️ **先说性质：本轮 = 一次真机采集窗口（OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`，无线调试 `192.168.31.183:37773`）+ 一次仓库外酒馆解析器验证（本机 node）**，生产代码零改动、仓库零源码改动（`tools/` 一个字节未动），工作区三处他人的未提交改动一个未碰。⚠️ **20 个状态格一个判定都没改，仍是 10/10 `unverified`**——逐行依据见 ⑧。⚠️ 本节所有 SHA-256 与字节数均为**登记时本机重新计算**（`sha256sum` / `stat -c%s` / Python `bytes.count`），与执行者报告不一致处已逐条标注。

**① 筛选 chip：「真的按类型过滤」+「不丢数据」两半都拿到真机 UI 证据（C1-10 的关键一半）**

上一轮（第八轮）只验到「切换前后 DB 计数不变」，且三种 chip 下列表恒空（settings 被测试助手顶替，见 ②）。本轮污染修复后重跑，`uiautomator dump` + 截图实测（`ui/r4-17…r4-20`，XML 与 PNG 都有）：

| chip | DIRECT 可见 | GROUP 可见 | 合计 | 「群」徽标 |
|---|---:|---:|---:|---:|
| 全部 | 4 | 2 | 6 | 2 |
| 单聊 | 4 | 0 | 4 | 0 |
| 群聊 | 0 | 2 | 2 | 2 |
| 切回全部 | 4 | 2 | 6 | 2 |

- 上表由我**独立按 XML `text=` 逐节点复算**（6 个已知会话标题 `C1 UI E2E 单聊甲/单聊乙/群聊` + `yunx工具测试` + `打招呼` + 标题就叫「群聊」的群会话，扣除 chip 自身标签「群聊」的 1 次出现，另数「群」徽标节点数），与执行者报告逐格一致。
- **过滤真实生效**：单聊下 GROUP 可见 **0** 条、群聊下 DIRECT 可见 **0** 条；「群」徽标只出现在含群会话的视图（2/0/2/2）。
- **切回精确复原**：`r4-20-chip-all-after.xml` 与 `r4-17-chip-all-before.xml` **逐字节相同**（均 25851 B / sha256 `f4c2b0170dd2e73d24b462f62df3daecafcd8cb140a549ce5c5eafa6357fbb56`，我 `sha256sum` 复核）。顺带发现同一 SHA 的还有 `r4-14`（修复后首次打开抽屉）与 `r4-28`（force-stop 重启后）——即**重启后的抽屉树与切回后逐字节相同**。另两份 chip XML 的 SHA：单聊 `28963ad55f2fb0270270c90b6606594b272efd3fe4a748e346d6afb697f48c72`（22981 B）、群聊 `433580313d3d48e5498f781aad0cb9da65aeb3bcaa2c9217d53feae3b789c729`（21529 B）。
- **不丢数据**：切换前后主库行数 **6**（DIRECT 4 / GROUP 2，全部归属 `0950e2dc-9bd5-4801-afa3-aa887aa36b4e`）不变——我用 Python `sqlite3` 只读直查两份 pull 回的库（`db-before/rikka_hub` 与 `db-after-chips/rikka_hub`）复算；`rikka_hub` / `-wal` / `-shm` 三个文件的 SHA-256 **前后完全相同**（`copy.db` 也一样）：

| 文件 | 字节 | SHA-256（切换前 = 切换后） |
|---|---:|---|
| `rikka_hub` | 152576 | `2b160acdeda089e34733c4330b48697951d07ea053d4ed22860684909eb5db72` |
| `rikka_hub-wal` | 740968 | `cc076dd756c034e6a53e44f0740a838ab899e4102b8279989e0d773fea5668d5` |
| `rikka_hub-shm` | 32768 | `d3d1501fd413e34272d7078dbbded3956a26aef3882ff84eca339f171adabfdc` |
| `copy.db` | 156672 | `8c17c4c935c3a53a1a521c91c2f3d7ee39165fb75927a0583caf4cbebb44a8ac` |

⚠️⚠️ **必须如实标注的边界**：这是**真机 UI（uiautomator 无障碍树 + 截图）级别的证据，不是仪器测试的自动化断言——仓库里没有任何对应的 `androidTest` 用例**（`ui/` 与 `avatar/` 全部文件都是 adb 手工交互的 dump/截图，未入库）。按契约 `:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ `:206`「不得仅凭 UI 截图勾选」，**本行不能升级**。另：chip 的「选中态」在 uiautomator dump 里读不到（Compose `selected` 不导出，第八轮已登记同一坑），选中态只有截图目视。

**② 用户 app 设置被测试污染：取证 + pb 字节手术恢复（涉及用户真实数据，全过程如实登记）**

（a）污染态取证（本机复核）

- 污染文件 = 主 DataStore `settings.preferences_pb` 的 17:40 pull 副本 `settings-before/settings.preferences_pb`：**22365 B / sha256 `6a0423c42c093cfccb25f3c858b4d395227f97ceb3ffd620a85a2288e85d4883`**（与第八轮 17:18 拉回那份 `924cda89…845bd6` SHA 不同——两次 pull 时刻不同，**都是污染态**）；无 `.bak`、无 backup 文件、无 `.corrupt-*`。
- 字节级复核（我 Python `bytes.count`）：`0950e2dc` 出现 **0** 次、`0c1c11ae` 出现 **4** 次。
- 污染内容（pb 解析，47 键）：`assistants`=[角色甲 a1 / 角色乙 b1 / 角色丙 c1]（real-provider 用例签名：真实模型 id `5a86b2d6`=deepseek-v4-flash 等）、`select_assistant`=a1（后一度变 c1，见下）。**其余 45 个键是用户真实数据**（极客猫 provider、kode_purple 主题等），**未被 mock 覆盖**。
- 污染前用户原状证据：`ui-e2e-evidence.txt`（11202 B / `37067526f3fe2d045a30dec84bafcc8cb39c06ee0a2ba53c1676a11a23a54be0`）三次夹具运行均记 `device_assistant=0950e2dc-9bd5-4801-afa3-aa887aa36b4e name= useAssistantAvatar=false` ⇒ 原状就是**唯一的内置默认助手**（空名、Dummy 头像）。

（b）根因（读测试代码得出，行号我自己核过）

- `C1LiveModelSequenceTest.kt` `setUp`（`:511`）：`:517` `originalSettings = settingsStore.settingsFlow.value` —— **纯内存快照，无磁盘备份**。
- `tearDown`（`:639`）：`:643-645` `originalSettings?.let { runCatching { runBlocking { settingsStore.update(original) } } }` —— `runCatching` 吞掉的正是这条 update 的异常；且 `SettingsRepository.update(settings)`（真实路径 `app/src/main/java/heizige/kk/khatkit/app/core/data/datastore/SettingsRepository.kt:442-446`；⚠️ 仓库里**没有** `core/data/repository/SettingsRepository.kt`，引用时注意子包是 `datastore`）在 `init == true` 时直接 no-op。
- trace（`c1-live-trace.txt`，714149 B / `be9c0f48ea4d7604b1e34282649e7812f449951bc14be4643821336ced3c45ec`）：28 次 setUp 全 `init=false`，最后一次 settings 写入 17:15:46（real-provider 用例）；**首次污染后，后续快照本身就是污染值** ⇒ tearDown 等于写回污染。
- 无任何磁盘备份、无历史备份（round3 的 `settings.pb` 也已污染）、`bmgr list sets` 无恢复集（执行者报告）。

（c）路径选择（代码核实后的死路/活路）

- 路径 1（用测试自己的备份）**死路**：只有内存快照；路径 2（重跑 seed 测试）**死路**：夹具只写会话、完全不碰 settings；路径 4（UI 重加助手）走到一半**发现 app 自身 bug（见 ③）**，且无法产生固定 id。最终走**路径 3：pb 字节手术**。

（d）手术与安全保证（执行者报告 + 本机可核 SHA）

- `assistants` 值 → `[]`（1681 B→20 B，重算两层长度前缀）+ `select_assistant` 36 B 等长替换为 `0950e2dc-…bb4e`；app 先 `force-stop`；两级备份；自研解析器（`pb_tool.py`，6031 B / `e5633ba8b6cc5e023edebc8e4e2ed9cefad8f7679f07d71e1a739ab352e2f6ea`）改后重解析并**断言其余 45 键 value 逐字节不变、key 顺序不变**；`.new` 远程 SHA 比对 + `chmod 600` + 同目录 `mv` 原子替换。
- **SHA 链（我逐文件 `sha256sum` + `stat -c%s` 复核；文件均在 `/tmp/opencode/c1-device-round4/`）**：

| 阶段 | 文件 | 字节 | SHA-256 | `0950e2dc` / `0c1c11ae` 计数（我复算） |
|---|---|---:|---|---|
| 污染态 | `settings-before/settings.preferences_pb` | 22365 | `6a0423c42c093cfccb25f3c858b4d395227f97ceb3ffd620a85a2288e85d4883` | 0 / 4 |
| 删 2 个测试助手后 | `settings-afterdelete3.pb` | 19047 | `fadfe1ffafb06ac7868c9808fec952d16e62b7d779dd0e2afda20b0cbbe8029e` | 0 / 2 |
| 手术前 pull（删第 3 个失败后） | `settings-pre-edit.pb` | 19047 | `9129ebb6865038cc99235bd58a5fd524a1f6f082b805df33bb12c8d3676fda5c` | 0 / 2 |
| 手术版（assistants=[]，select 替换） | `settings-fixed.pb` | 17386 | `a2fa5fa3de6b7b2a652285b22f62760d6af401bc4a8a48d23f7a98d3e46faf45` | 1 / 0 |
| app 选中默认助手后 | `settings-after-select.pb` | 18440 | `9b6478abc7b274af7ebd9a774cb8857d48d11f81f718947631fe7282d2e41d11` | 2 / 0 |
| 重启后终态 | `settings-final.pb` | 18440 | `37baf83f8566e90fd1c79646144d04b87d3fefcdcaf875f6dc7f34368088e324` | 2 / 0 |

- 之后启动 app 触发 `ifEmpty { DEFAULT_ASSISTANTS }`（`SettingsRepository.kt:365`）→ 抽屉里选中「默认助手」→ **app 自己把完整默认助手 JSON 落盘**。恢复内容与代码 `DEFAULT_ASSISTANTS` **逐字段一致**（空名 / Dummy 头像 / 7 个本地工具 / 四个 `enable*=true`）。
- UI 结果：抽屉从「没有对话记录」（`r4-01-drawer.xml`，19037 B / `21cba012…ec17`）→ **6 条会话**（`r4-14-drawer.xml`），**force-stop 重启后仍 6 条**（`r4-28-restart-persist.xml`，与 `r4-14`/`r4-17`/`r4-20` 同 SHA）。
- 主库 6 个会话（全部归属 `0950e2dc`）**全程未动**；删除测试助手前已核实其 0 会话 / 0 memory 引用（执行者报告）。
- ⚠️ **边界**：整个过程是 adb + Python 脚本人手编排（`pb_tool.py` 脚本本体在仓库外），**不是自动化测试**；「其余 45 键逐字节不变」的断言由执行者的自研解析器完成，我**未能独立复现该断言**（我独立可核的是：六个文件的 SHA/字节数、两个 UUID 的出现次数、以及手术版可被 app 自己重新落盘成完整默认助手）。

**③ ⚠️ 新发现 app 真 bug：删除最后一个助手会崩（未修，只报告）**

- `AssistantViewModel.removeAssistant`（真实路径 `app/src/main/java/heizige/kk/khatkit/app/feature/assistant/AssistantViewModel.kt:48-61`，行号我自己核过）把 settingsFlow 临时置空 → `SettingsRepository.getCurrentAssistant()`（`SettingsRepository.kt:745`）的 `assistants.first()` 抛 `NoSuchElementException`（`ChatViewModel` 的 main 收集器）→ **在 persist 之前崩溃 ⇒ 删不掉、进 SafeMode**。
- 崩溃栈自 XML `ui/r4-12-after-3.xml`（15846 B / `c63e14e7386c75d42a1eb365055a931ce60d5a5008f1501cd0329d4166e65677`）读出（我逐帧复核）：`NoSuchElementException: List is empty.` → `CollectionsKt.first(_Collections.kt:222)` → `SettingsRepositoryKt.getCurrentAssistant(SettingsRepository.kt:745)` → `ChatViewModel$special$$inlined$map$1$2.emit(Emitters.kt:218)` → `StateFlowImpl.collect(StateFlow.kt:401)`；同一 XML 里可见 SafeMode 文案「应用上次启动时崩溃了…」「安全模式」「当前助手：角色丙」「切换助手」。
- 触发条件：**只有 1 个助手时删除**（删第 2、3 个用安全模式 UI 成功，见 `r4-04c` / `r4-05c`；删最后一个崩在 `r4-06c` / `r4-12`）。
- ⚠️ **未修，也不在本轮范围**。登记为**已知缺陷**（「已知遗留与风险」第 36 条）。

**④ 成员头像组：仍然只有弱证据（不许拔高）**

- `uiautomator dump` 里**找不到任何头像节点**（`UIAvatar` 无 `content-desc` / 无 semantics；`ui/r4-21-groupchat.xml` 的 `content-desc` 全集只有 返回 / 复制 / 更多选项 / 群配置 / 语音 / 两个模型名 / Input / Output / 翻译 等，**没有任何头像或角色名语义**）。
- 截图可见：消息列表 Alice / 阿尔法 / 小结 3 处头像；输入框上方 **3 个彩色头像**（粉/紫/蓝 = 3 角色）从输入框后探出（`avatar/r4-21-crop-input-area.png`，55471 B / `036ab40cbb1a43d8b8d334061f5f814a239c34431675bfa32df8848fa7f0f246`）。⚠️ **像素级我用 PIL 独立复核**：全图（1080×2376）左下粉色可见区 bbox **x41-112 / y2159-2235**（执行者报告 `y2160-2232 @ x60`，同一区域）；裁剪图左侧饱和色簇含紫/粉/蓝三类。
- ⚠️ **伽马的消息行两帧都没有头像**（`ui/r4-21-groupchat.png` 与 `avatar/r4-27-groupchat-2nd.png`；两帧 dump SHA 相同 `0855e010526d827f6d612cab676575489d5e8acc78cc07f874a53f15e30eab22`，尺寸 37048 B）——**如实记录，原因未查**。
- 额外尝试：在粉色头像可见区点两次（`ui/r4-22-avatar-tap.xml` / `ui/r4-23-avatar-tap2.xml`，两帧 dump 与 `r4-21` 同 SHA），**未**插入 `@Alice Johnson `（输入框仍占位符；`avatar/r4-22-after-avatar-tap.png` 219794 B / `cd304660…307d`）。
- ⇒ 结论只能是「**截图里存在头像组**」，**不是自动化断言**，**点按可用性未证实**。契约要求的「成员头像组」**不能算已验**。

**⑤ ⭐ 酒馆解析器验证：硬理由③在「解析器级」通过（16/16）——不是「用户在真 SillyTavern 里打开过」**

（a）导出物来源（**不是手写样本**）

- 仓库既有 golden 测试 `app/src/test/java/heizige/kk/khatkit/app/core/data/ai/tavern/C1pGroupExportHashTest.kt`（路径我核实）调用生产编解码器 `TavernChatCodec.exportGroupJsonl`（`TavernChatCodec.kt:240`）把导出物写盘到 `app/build/c1p-group-export-hash/pipeline_3roles_2rounds_jsonl.txt`。
- 命令 `./gradlew --offline :app:testDebugUnitTest --rerun --tests '…C1pGroupExportHashTest'` → **退出码 0**、`BUILD SUCCESSFUL in 5s`、XML `tests="5" skipped="0" failures="0" errors="0"`（我读 `gradle-c1p-run.log` 18616 B / `18e40bb1142b365aeaece0db4e8e05dbfc1250a12e62cfb39262ab0e5dab81a2` 与现存 `app/build/test-results/.../TEST-…C1pGroupExportHashTest.xml` 复核；后者就是当前唯一一份单测 XML，即 `c1_doc_stats.py` 报「1 类 / 5 例」的那个子集）。
- 产物 **3615 字节，SHA-256 `36e6585f9aa4a028eb8277bc70578fd2c802cdd730e3981f0d52b02430f0c8b9`**（我 `stat`+`sha256sum` 复核），与 golden `tools/verification/c1p_group_export_hash.golden.json` 一致（golden 我核过 `bytes:3615` / 同 SHA）。
- 官方校验脚本 `python3 tools/verification/c1p_group_export_hash.py` → **退出码 0**（执行者运行；⚠️ **该脚本内部会调 gradle（`tools/verification/c1p_group_export_hash.py:170`），本轮为避构建锁没有重跑**；登记依据 = `c1p-verify.log` 7457 B / `c858abd84959370a0bc098942c81da7f89ad02be0954ac12cd85ed6f19d5ca00` 里 [run1]/[run2] 两个退出码 0、11 个产物逐条「一致」）。

（b）酒馆侧（SillyTavern commit `06bde939fb1e9c4c8d8641d810f0a916b5bce127`，与仓库既有登记 `docs/beyond-operit-open-source-references.md:221` **同一 SHA**，未用浮动 master；**AGPL-3.0**；clone 在 `/tmp/opencode/sillytavern-verify/SillyTavern`）

- **我独立复核**：`git rev-parse HEAD` = 同一 SHA；四个被引用文件在该 commit 的 **blob SHA** 与登记一致（`src/endpoints/chats.js` `390abc3cb2aeefd0edeffe4294186f199a14b3ec`、`src/util.js` `ad5afb1539b59799ede088e883e5a6ab6c117438`、`public/scripts/group-chats.js` `7b82ca84132f2e04ab41ee8ee26a1bf7c1796a1f`、`public/script.js` `777a2d5983a6283ef9b26726e75192da1e3f3cea`）。
- 解析链位置（只给路径 + 行号，不贴代码）：服务端逐行解析 `src/endpoints/chats.js:577-590` `getChatData`（`tryParse` 在 `src/util.js:571-577`；`tryReadFileSync` 在 `src/util.js:1528-1538`）；客户端群聊装载 `public/scripts/group-chats.js:255-320` `getGroupChat`（`:268` 只取 `data[0].chat_metadata`；`:272-274` 首行含 `chat_metadata` 才 `shift()`；`:305-309` 装载消息）；聊天列表扫描 `src/endpoints/chats.js:459-484`；消息消费 `public/script.js:2575-2576`、`:2634-2652`。⚠️ 我逐一 grep 核过 `getChatData:577`、`tryParse:571`、`getGroupChat:255`、`:268` / `:272` 的存在与语义。
- harness `harness.mjs`（18848 B / `97969fa4752dd3ac18debf16bdf15d895f0dbbd066176873453ebb36fcaa87ea`，**只在 /tmp，未进仓库**）。直接 import 被否（`src/endpoints/chats.js` 依赖 express / sanitize-filename / write-file-atomic / lodash / middleware；`src/util.js` 还依赖 yaml / command-exists / yauzl / mime-types / simple-git / chalk / bytes；node_modules 未安装）⇒ 按允许的退路提取最小片段：`tryParse` / `tryReadFileSync` / `getChatData` 为**逐字提取**，群聊表头处理 / `updateChatMetadata` / `getChatInfo` 扫描语义 / 消息渲染取值 / 保存序列化为**同语义转写**（逐条标注文件:行号）。
- **16/16 断言全过，harness 退出码 0**——⚠️ **我自己用 `node harness.mjs` 独立重跑过**：输出 `16/16 assertions passed`、exit code **0**，且重跑后 `harness-report.json` 与重跑前**逐字节相同**（4097 B / `4578185df1c09a0dfe487c9f5d4a8f58892dc790d03ba69bde0da3b92dd834dc`）。

（c）六条断言结果（照记）

| # | 断言 | 结果 | 关键读数 |
|---|---|---|---|
| 1 | 逐行 JSON 解析无坏行 | ✅ | 9 行全解析，0 坏行 |
| 2 | `name` / `is_user` / `is_system` / `mes` 可读 | ✅ | 8 条全部类型正确（如 `阿尔法:false/false`、`阿达:true/false`） |
| 3 | 三个角色名对上 | ✅ | a/b/c → 阿尔法/贝塔/伽马；非用户说话人集合 {阿尔法,贝塔,伽马,多数决} |
| 4 | 顺序一致 | ✅ | `[阿达,阿尔法,贝塔,伽马,阿达,多数决,贝塔,伽马]`；swipe 消息 swipes=2、swipe_id=1、`mes` 取选中 swipe |
| 5 | 扩展字段被忽略还是报错 | ✅ **被忽略、不报错** | 酒馆只抬 `data[0].chat_metadata`（=`{is_group:true}`），`khatkit_group` 随表头行一起被丢弃；消息级契约键原样保留且能过保存序列化；`getChatInfo` 接受（chat_items=8） |
| 6 | Normalized 归一化产物仍可读 | ✅（**代理验证**） | 无真实 Normalized 导出 fixture；用「只改 `khatkit_group` 数值 / 删掉整个私有块」两种改写跑同一解析，结果与原件逐项相同 |

**结论：A —— 格式被酒馆自己的解析器接受。**

（d）⚠️ **必须一并保留的边界（5 条 + AGPL）**

1. **只是解析器层，不是完整 app**：没起 node 服务、没装依赖、没跑 UI、没走写盘。
2. **群注册是另一件事**：酒馆打开群聊要求该群已注册在该用户的 `groups` 里且文件已挂到 `group.chats`（`group-chats.js:2199-2200`）；群名/成员名单存在服务端 `groups/<id>.json`，**不在**聊天文件里。**野生 `.jsonl` 单独存在不会出现在某个群下。**
3. **角色归因靠每条消息的 `name`**；`khatkit_character_names` 酒馆不读。
4. **无 `send_date`**：酒馆显示空时间戳，不报错（导出确定性的代价）。
5. ⚠️⚠️ **酒馆 open→save 会丢掉私有表头块**（它用 `chat_metadata` 重建表头，`group-chats.js:632-636`）⇒ **被酒馆重存过的文件再回 KhatKit 导入会得到 `NoConfig`**（消息仍可导入）。这是设计后果、不是解析拒绝，但**影响「打开并回导」的叙事**，必须写清楚。<br>⚠️⚠️ **订正（2026-10-07，真机第十七轮 / C1-09 收尾，HEAD `e2bfce4ad`，原文保留）：上面「会得到 `NoConfig`」是错的——实际是 `Unsupported`。** `TavernChatCodec.importGroup` **`:298`** `document.header[GROUP_FIELD] as? JsonObject ?: return null`（表头无 `khatkit_group` **直接 return null**），调用方 `GroupTavernImport.kt:176-180` 映射为 `TavernGroupImportOutcome.Unsupported`（`isError=true`）；`NoConfig` 只在「`khatkit_group` 键在、但没 `config`」的畸形文件上可达。JVM 测试级证据：`GroupTavernImportTest` 的 `a file without the khatkit_group block at all is not a config problem`（`GroupTavernImportTest.kt:217`）PASS。详见「已知遗留与风险」第 52 条。
6. ⚠️ **AGPL 边界**：SillyTavern 代码只在 `/tmp`。本登记**只给路径 + 行号 + SHA，不贴大段代码**。

（e）附加发现（也登记）：数组形态 `exportGroup`（仅 golden 测试的第二载体）**不是**酒馆聊天文件（整行一个数组 ⇒ 表头判定不触发；harness A7 实测 `data.length=1 / headerDetected=false / firstIsArray=true`）；但 UI 出货路径只用 `exportGroupJsonl`（`GroupExportCard.kt:206`；扩展名常量 `GroupExportCard.kt:40`），**不构成缺陷**。

（f）harness 相关 SHA（我复核）：`harness.mjs` = `97969fa4…a87ea`；`c1p-verify.log` = `c858abd8…5ca00`；`gradle-c1p-run.log` = `18e40bb1…b81a2`；`SUMMARY.md` 6778 B / `1dd1db361979a539978976f57de13ff23f64b7da2c280d216682fcc24f94426a`；`harness-report.json` = `4578185d…834dc`。

**⑥ 设备副作用与遗留（如实登记）**

- `screen_off_timeout` 原值 **30000**，本轮**从未修改**，收尾 **30000**。
- `svc power stayon`：**原值未能留证**（按任务先设后读）；收尾设 0 时曾观察到被 ColorOS 重置回 15，再次设 0 并确认（执行者报告）。
- 上一轮留下的 `RUNNING` 残留行**仍在**（我直读两份 pull 回的 DB 复核，字段逐项一致）：`conversation_id=2b6c129f-35da-4934-bfa7-6bf8d8099480`、`round_id=round-a8d28946-4685-461c-9ada-20095319ca53`、status=`RUNNING`、`token_limit=9000`、`spent_tokens=0`、`started_at=1791271593923`（≈2026-10-06 15:26:33）、`ended_at=NULL` —— **未删，只报告**（另立遗留第 37 条）。
- 未 `pm clear` / `pm uninstall`，未用 `connectedAndroidTest`；本轮**没有跑任何仪器测试**。

**⑦ 本批的诚实局限（不许美化）**

1. 全部 UI 证据是 adb 手工交互 + uiautomator/截图，**零 androidTest 运行、仓内零对应断言**；
2. pb 手术的「其余 45 键逐字节不变」断言系执行者自研解析器完成，我**未独立复现**；
3. 头像组无断言、点按未证实、伽马头像缺失原因未查；
4. 酒馆侧只有解析器级 16/16，完整 app 未跑（群注册 / 写盘 / UI 全未做）；
5. 设置污染的**测试代码根因未修**（`C1LiveModelSequenceTest` 的内存快照 + `runCatching` 吞异常仍在仓库里）——下一轮全量仪器测试**会再次污染**，登记为遗留第 34 条续项。

**⑧ 判定影响：20 个状态格一个判定都没改（仍是 10/10 `unverified`），逐行依据**

| 行 | 本批新增了哪类产物 | 仍然缺哪一项（不升级的理由） |
|---|---|---|
| C1-01 | — | 显式 @ 的投递收窄仍无真机证据（夹具的 @ 落在用户消息上） |
| C1-02 | — | 上一轮的三项未锁死原样（逐调用运行日志对照 / 独立断言 / shell 退出码落盘）；本批未触碰 |
| C1-03 | — | 真实网关 roundtable 议长汇总轮零份 |
| C1-04 | — | 真实网关 vote 与平票失败路径零份 |
| C1-05 | — | 正式 `actual_model_call_sequence` JSON 未产出（raw dump 是诊断文件、不是正式证据） |
| C1-06 | — | 取消 / 超时路径真机零份 |
| C1-07 | — | 同 `round_id` 重试幂等的真机台账零份 |
| C1-08 | — | 三个记忆空间检索可见性 + `source_message_id` 归因零份 |
| C1-09 | **解析器级接受 16/16（本批最大推进）**；导出物来自生产编解码器（3615 B / `36e6585f…`） | 契约 `:206` 要的「酒馆本体打开」= **完整 app 打开未做**（群注册 / 写盘 / UI 全未跑）；`open→save` 丢私有表头块影响「打开并回导」；**相机扫码仍零份**；真机导出文件 SHA 与回导链路仍不完整 |
| C1-10 | **chip 两半首次拿到真机 UI 证据**（过滤生效 + 切回精确复原 + 三文件 SHA 不变 + 双库 6 行未动） | ① 契约 `:232-235` 明写「只看截图或只看 UI 状态 → `unverified`」，仓内无对应 androidTest；② 成员头像组仍只有弱证据（无断言、点按未证实）；③ 契约四类产物（viewer 集合 / 调用序列 / token / 导出哈希）本行仍零份。**离升级比上一轮更近，但一格不改** |

⇒ **一个 `verified` 都没有**：按「判定规则」第 2/6/8 条逐行核完，没有任何一行「四类产物在本行点名路径上全齐」。⚠️ 20 格里 ② 句的原文「成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做」自本轮起已过期一半（chip 已通过、@ 已有覆盖、头像组仍弱）——**已在 20 格就地追加第九轮订正**，但**不构成任何一格的判定改变**。

**⑨ 四条硬理由现状（本轮更新后）**

- **硬理由①（模型名不是 wire 级抓包）**：上一轮已判定**对真实网关路径已消**（`wire_model_name_provenance_counts={wire_response_model:3, fallback:0}`）；**本轮无变化**，不重复登记。
- **硬理由②（真机 UI 端到端零份）**：**从「@ 一项较好」推进到「chip 两半落实、头像组仍弱」** —— @ 选择器第七 / 八轮已覆盖较好（真实文本回读 + 生产解析函数真机通过）；**chip 本批通过（UI 级）**；头像组仍只有截图与像素证据、无任何断言、点按未证实。⇒ **部分消，未消**。缺的三件：① 头像组的自动化断言（至少 `content-desc` 级）；② 头像点按插入 @ 的可用性；③ 混排列表行为的 androidTest（或契约认可的等价自动化）。
- **硬理由③（酒馆本体零份）**：**从「零份」推进到「解析器级接受 16/16」**，但「**用户在完整 SillyTavern app 里打开过**」仍零份（没起服务、没跑 UI、没走写盘、没有群注册）⇒ **降级为「只剩完整 app 一半」，未消**。措辞必须是「**解析器级接受**」，**不许写成「酒馆已能打开」**。
- **硬理由④（相机扫码）**：**仍零份**，本轮未碰（相机链路需要物理二维码进入视野，需用户配合）。


### C1 真机证据采集第十轮 / 页面入口合并（2026-10-06，HEAD `d133b400b`：单聊群聊合并为 ChatPage 唯一入口 + 成员头像组首条 androidTest 断言真机通过 + chip/@ 未跑完 + 一次 kill -3 操作失误）

⚠️⚠️ **先说性质**：本批 = **3 个代码 / 测试 commit**（`6b4a56d9f` / `a4b8be9da` / `d133b400b`）+ **一次未收尾的真机采集窗口**（OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`，无线 `192.168.31.183:37773`）。⚠️ **20 个状态格一个都没升级，仍是 10/10 `unverified`**（只有 C1-10 两处状态格就地追加了第十轮订正，判据见 ⑨）。⚠️ 本节 SHA / 字节 / 行数 / `@Test` 计数凡标「**实测**」者为**登记时本机重新计算**；标「**执行者报告**」者为**转述执行者**，登记时未独立复现（约束：不跑任何 gradle）。

**① 页面入口合并：单聊群聊合并为 `ChatPage` 唯一入口（`6b4a56d9f` / `a4b8be9da`，2 个 commit）**

用户指令原文：「现在先把群聊和单聊页面合并到一起 服用同一个页面」。

合并两端（`a4b8be9da` 之前）：

| 端 | 内容 |
|---|---|
| 分派点 | `GroupOrDirectPage`（原 `GroupChatPage.kt:80-105`）按 `type` 选页 |
| 单聊页 | 已有的 `ChatPage`（`ChatPage.kt`） |
| 群聊页 | `GroupChatPage`（原 `GroupChatPage.kt:145-368`） |
| **已在共享组件内** | `ChatScaffold` 已抽出（`8528ecda`）且群聊页已复用（`4e97ff57`）；`GroupTopBar` / `GroupInfoChip` / `GroupConfigSheet` 及 7 个子 composable、`GroupMemberBar` 都**已存在于 `GroupChatPage.kt`** |

合并做了三件事：

1. **`6b4a56d9f`（1 file, +57/−22，`GroupChatPage.kt`）**：`GroupTopBar` / `GroupInfoChip` / `GroupConfigSheet` 由 `private fun` → `internal fun`（实测 `:116` / `:155` / `:214`）；抽出顶层 `internal fun groupConfigSave(...)` 留在 `GroupChatPage.kt`（实测 `:68`）；新增顶层 `internal val groupCanEditMessage: (UIMessage) -> Boolean = { it.role == MessageRole.USER }`（实测 `:101`；⚠️ **是 val lambda 不是 fun**——fun 体里写 `it` 无法编译；该字面量在本文件恰 1 次）。
2. **`a4b8be9da`（3 files, +99/−340）**：
   - `ChatPage.kt` 新增（实测行号）：`val config = conversation.groupConfig`（`:225`）、`val isGroup = isGroupConversation(conversation)`（`:226`）、`val roleCompletionProvider = remember(config) { GroupRoleCompletionProvider { config } }`（`:227`）、`var showConfigSheet by rememberSaveable { mutableStateOf(false) }`（`:228`）；两次 `ChatScaffold(...)` 重构成单个 `val scaffold: @Composable (Boolean) -> Unit`（`:270`），条件注入 5 个槽（`:288-292`）。
   - **条件注入 5 槽对照**（实测 `ChatPage.kt:233-268`）：`topBar` = `GroupTopBar`；`listOverlay` = `GroupInfoChip`；`bottomBarAboveInput` = `GroupMemberBar`；`extraCompletionProviders` = `listOf(roleCompletionProvider)`；`canEditMessage` = `groupCanEditMessage`。单聊分支依次落 `null` / `({})` / `({})` / `emptyList()` / `({ true })`。
   - **删除**：`GroupOrDirectPage` 与 `GroupChatPage` 两个函数及其 KDoc + 19 个因此未使用的 import（执行者报告）。`GroupConfigSheet` 及 7 个子 composable、`GroupTopBar`、`GroupInfoChip` **原地未动**（实测仍在 `GroupChatPage.kt`）。
   - `RouteActivity.kt`：删 `import …GroupOrDirectPage`，`entry<Screen.Chat>` 内改调 `ChatPage(`（实测 `RouteActivity.kt:379`），5 个参数原样传。
3. **入口判定**：`isGroupConversation(conversation)` 定义在 `ChatManager.kt:2352`（实测），严格口径「`group_config` 非空**且** `type == GROUP`」。现在 **`ChatPage` 是唯一页面入口**。

**② 5 个源码护栏测试（执行者报告「全绿且未改任何断言」；`@Test` 计数为登记时实测）**

| 测试类 | `@Test`（实测） | 执行者报告 |
|---|---:|---|
| `GroupChatPageDisplayLineSourceGuardTest` | 2 | tests=2 / failures=0 |
| `GroupEditActionVisibilitySourceGuardTest` | 5 | tests=5 / failures=0 |
| `BottomSheetScrollSourceGuardTest` | 4 | tests=4 / failures=0（65 计数未变） |
| `PrimaryBottomSheetIconSourceGuardTest` | 4 | tests=4 / failures=0 |
| `ConversationGroupCardsSchemaTest` | 7 | tests=7 / failures=0 |

⚠️ 上表「执行者报告」列的退出码与 failures 数**由执行者提供**，登记时**未重跑**（约束：不跑任何 gradle，以免重写 test-results XML 使刚登记的 mtime 证据失效）。

**③ 编译陷阱（值得登记）**：`if (isGroup) { { ... } } else {}` **不能编译**——外层 `{}` 被解析成 if/else 的代码块，块值是 `Boolean`/`Unit` 而不是 lambda，类型对不上。必须写 `if (isGroup) ({ ... }) else ({})`。代码里已按后者写并留了注释（实测 `ChatPage.kt:230-232`）。

**④ 知情接受的行为变化**：删掉分派点的 `type == null -> Unit` 门控后，所有会话（含单聊）比改前**早一帧**挂载 `ChatPage`；单聊侧注入参数**全部等于 `ChatScaffold` 默认值**（默认实参实测 `ChatPage.kt:410-414`：`listOverlay = {}`、`bottomBarAboveInput = {}`、`extraCompletionProviders = emptyList()`、`topBar = null`、`canEditMessage = { true }`），但**显式传了一遍**。

**⑤ 三条 androidTest 断言纳入（`d133b400b`，1 file, +569）**

新入库 `app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1GroupFilterAndAvatarUiTest.kt`：**569 行 / 3 个 `@Test`**（实测），SHA-256 `a1ffffd64c594a119f14e875f166184cbfeb7b1557995c1bc0018ab5291fc84e`（实测；与执行者报告一致）。三条用例：

1. `typeFilterChipsFilterWithoutLosingData`（筛选 chip 过滤 + 不丢数据）
2. `groupMemberBarRendersOneAvatarPerRole`（成员头像组）
3. `atMentionPickerInsertsParseableMention`（@ 选择器）

引用的 testTag（生产代码里本就存在，**`d133b400b` 本身生产代码零改动**；行号为登记时实测）：`chat_input`（`app/core/ui/components/ai/ChatInput.kt:931`）、`group_member_bar`（`GroupMemberBar.kt:70`）、`drawer_conversation_list`（`ChatDrawer.kt:571`）。⚠️ 后两个 testTag 是**本批更早的两个独立 commit**（`15ce2f251` / `467f67ad9`，只加语义不改行为）加的，不是 `d133b400b` 加的。

**⑥ 真机结果（如实登记，不美化）**

设备 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a` / 无线 `192.168.31.183:37773`；两个 APK `install -r -t` 均 `Success`（执行者报告）。

- ✅ **三条里只有测 2 `groupMemberBarRendersOneAvatarPerRole` 真机通过**。原始 `round5-instrument.log`（实测 1162 B / SHA-256 `1e43d6c4525bf0f4596d2965065e3dba98614b0148148bb83a8a71b37d348a04`）逐行：`numtests=3`、`test=groupMemberBarRendersOneAvatarPerRole`、**`INSTRUMENTATION_STATUS_CODE: 0`**。该用例断言（执行者报告）：`bar.assertExists()` + `assertEquals("成员头像组里的可点击头像数必须等于群角色数（夹具=3）", fixtureConfig.roles.size, avatarNodes.size)`（用 `hasAnyAncestor(hasTestTag(MEMBER_BAR_TAG)) and hasClickAction()` 取节点）+ `bar.onChildren().assertCountEquals(3)`。⚠️ **这是硬理由②的第一条 androidTest 级自动化断言**（此前成员头像组只有截图 / uiautomator 弱证据）。
- ❌ **测 1 与测 3 未跑完**。同一份日志逐行：测 2 通过后进入 `test=typeFilterChipsFilterWithoutLosingData`（`STATUS_CODE: 1`，已启动），紧接着 **`INSTRUMENTATION_RESULT: shortMsg=Process crashed.`**；**测 3 `atMentionPickerInsertsParseableMention` 从未出现**（`numtests=3` 只跑到第 2 条）。

**⑦ 操作失误（必须原样登记，不是测试失败）**

18:59:50 执行者为看线程栈对测试进程执行了 `run-as … kill -3 28072`（执行者报告原文）。ColorOS 记 **`reason=13 OTHER KILLS BY SYSTEM … o-stop(40)`**，把 instrumentation 一并杀停 ⇒ 上面那句 `INSTRUMENTATION_RESULT: shortMsg=Process crashed.`。**这是操作失误，不是测试失败**。此后设备无线调试端口从 `37773` 变 `38493`（mDNS）继而全部 `Connection refused`，`adb devices` 持续为空。

**⑧ 非空验证未执行 + 证据面 + 设备设置未恢复**

- ⚠️ **非空验证（三次破坏）未执行**——三条断言的「非空」目前只是**按构造推断**，**不是实测**。
- 证据面（均实测）：`round5-instrument.log`（1162 B，SHA-256 见 ⑥）；`logcat-full.txt`（2366823 B，SHA-256 `e5e66471b11868f13be86452f501366a584eee2471c1541c2999483ec46b1d3f`）；同目录另有 `threaddump.txt`（2329885 B）。三者都在 `/tmp/opencode/c1-ui-assert/`（仓库外）。**设备侧证据文件因掉线未能 pull**。
- **设备设置未恢复**：`screen_off_timeout` 现为 `600000`（原值 `30000`），`stayon true` 未恢复——设备已不可达，**无法改回**（执行者报告；已登记「已知遗留与风险」第 38 条）。

**⑨ 判定影响：20 个状态格一个升级都没有，仍是 10/10 `unverified`——C1-10 为什么不升级（逐行理由）**

本次拿到的是**真正的 androidTest 断言**（不是截图），比第九轮的 uiautomator/截图级更硬；但**只有 3 条里的 1 条通过**，且 chip / @ 未跑、非空验证未做。逐条核契约：

- 契约 `:206`：「每例保存输入、各角色可见消息集合、实际模型调用序列、token 计数和导出哈希……**不得仅凭 UI 截图勾选**」——C1-10 行这**四类产物一份未增**；本次通过的那条断言对象是**成员头像组的可点击头像数**，**不是 C1-10 点名的「同一列表混排 / 类型筛选只过滤 / 切换后数据不丢」**那条路径。
- 契约 `:232-235`：「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」——本批属于比「只看 UI 状态」更硬的一档（**真的跑了一条自动化断言且通过**），但**整类 3 条里 2 条没跑完，且通过的那条并不是 C1-10 点名的路径**。
- 契约 `:184-185`：「群条目 = **成员头像组** +「群」徽标」——头像组断言只覆盖「条目里头像数 = 角色数」，**「群」徽标与筛选/混排行为都没断言**。
- 本文「判定规则」第 2 条：缺可见消息集合 / 调用序列 / 哈希的行不算通过。

⇒ **独立判断：C1-10 仍不能升级为 `verified`**，三条理由——① 三条用例里两条（chip / @）**没跑完**，无法构成「本行路径已被自动化覆盖」；② 唯一通过的用例断言的是**头像组渲染**，不是 C1-10 点名的**筛选 / 混排 / 不丢数据**；③ 契约 `:206` 的四类产物本行**仍零份**。已在 C1-10 两处状态格就地追加第十轮订正（保留原文、不覆写）。

**⑩ 四条限制（不许美化）**

1. 唯一通过的一条是 **Compose UI 断言**，且**非空验证未做**——「夹具=3」不足时会不会假绿，**未证**；
2. **截图 / uiautomator 证据一样没入库**，本批只有一份 `round5-instrument.log`（外加 `logcat-full.txt` / `threaddump.txt`），**设备侧重证据未 pull**；
3. 页面合并的**真机 UI 表现零验证**——合并后单聊页是否与改前逐字相同，**没有真机对比截图**；
4. **设备现在不可达**（`adb devices` 空），以上任何一条都**无法在本批内补跑**。

**⑪ `architecture-map.md` 说明（登记时实测）**：`docs/architecture-map.md` 当前**没有任何一处引用 `GroupOrDirectPage` / `GroupChatPage` / `ChatPage`**（`grep` 零命中），其「UI 结构 / 包结构」只到「`feature/chat` 35 个 kt」这一级（本次合并删的是函数、不是文件，`chat` 包 kt 数不变），故本次合并**不需要同步该文件**；⚠️ 该文件另有一处**他人在途未提交改动**（service 命名计数与 suggestion 孤儿清理，与本合并无关），本批**未碰**。


### 生成失败健壮性与助手空列表修复（零设备，2026-10-06，HEAD `81147639a`）

⚠️⚠️ **先说性质**：本批 = **4 个真缺陷修复 commit**（`e628d9d82` / `8b05fde70` / `85f708169` / `81147639a`），**零设备、零 `adb`、零仪器测试**；两处 AI 修复的验证是 JVM 单测（真实本地 HTTP + 真实 `EventSources`），助手那处是纯函数单测。⚠️ **这 4 个 commit 都不构成本文件任何用例的验收证据**——C1 走的是 **chat-completions** 路径（`43d607bdd` 修好的正是它），而本批两处是 **responses API 与 Claude**；助手删除更是与 C1 无关 ⇒ **20 个状态格一格未动，仍是 10/10 `unverified`**。下文的「非空验证」只证明「测试有牙齿」，**不是**验收证据。

**① 三处「非 2xx 空/乱码 body 被静默当零产出」缺陷的全景（`e628d9d82` / `8b05fde70`，对照 `43d607bdd`）**

根因链条（三处完全同构，逐行读码核实）：

1. **`common/.../http/okhttp/sse/EventSource.kt:85-99`**（本 fork 自研 `KtorEventSource`）：响应**非 2xx** 时**无条件硬编码传 `null`**——`:85` 判 `response.status.value !in 200..299`，`:89-99` 调 `listener.onFailure(this@KtorEventSource, null, Response(code, message, body))`（第二个实参就是 `null`）⇒ **错误信息只能来自响应体**。
2. **调用方**若写成 `var exception = t` + **仅当 body 非空才 `parseErrorDetail()`** + 末尾 `close(exception)`，则**非 2xx + 空 body** ⇒ `exception` 保持 `null` ⇒ `close(null)` ⇒ `callbackFlow` 当**正常完成**收场：零 chunk、零异常 ⇒ 上游只能报「本轮没有产出内容」。

三处对照：

| commit | 文件（现位置，实测） | 触发面 | 兜底改法 |
|---|---|---|---|
| `43d607bdd`（上一批，**C1 走的正是它**） | `openai/ChatCompletionsAPI.kt`（`:839-840`） | chat-completions 非 2xx + 空 body | 上一批已修，本批只作对照 |
| `e628d9d82`（本批） | `openai/ResponseAPI.kt`（顶层 `internal fun responseApiStreamListener` `:700`，兜底 `:743-744`） | responses API 非 2xx + 空 body；且它的 catch 分支**只 log 不赋 `exception = e`** ⇒ 非 2xx + **乱码 body** 也会 `close(null)` | 监听器机械搬为顶层 `internal fun responseApiStreamListener(decoder, sendChunks, closeFlow)`；兜底行改 `closeFlow(exception ?: HttpException(response?.let { "Failed to get response: HTTP ${it.code} ${it.message}" } ?: "Stream failed: unknown error"))` |
| `8b05fde70`（本批） | `claude/ClaudeProvider.kt`（顶层 `internal fun claudeStreamListener` `:898`，兜底 `:942-943`） | 形状与上面逐字相同 | 同上；额外在 catch 里补 `exception = e`（`:936`），并把 try 之前的 `Log.e`/`printStackTrace` 移入 try、`exception = parseErrorDetail()` 提到 `Log.i` 之前（**纯位移/顺序微调，Android 上行为不变**） |

⚠️ **为什么要把内联 listener 搬成顶层 `internal` 函数**：JVM 单测里 `android.util.Log` 未 mock 会抛——搬出来才能用**真实 `EventSources` + 真实监听器**驱动失败路径（与 `43d607bdd` 同一手法）。

**同构第三处扫描结论：全仓无第三处（本机 grep 实测）。**
- `google/GoogleProvider.kt:317` 与 `google/InteractionsAPI.kt:153` 已是 `close(exception ?: Exception("Stream failed"))`（带兜底，安全）；
- 各 `*StreamDecoder.kt` 只抛异常、**不 close channel**；
- `common/.../http/SSE.kt:78` 的 `channel.close(t)` 的 `t` 确实可 null，但**先**在 `:77` `trySend(SseEvent.Failure(t, response))`，消费方 `MiniMaxTTSProvider.kt:126-138` / `MiMoTTSProvider.kt:106-117` / `VolcengineTTSProvider.kt:90-95` 都有兜底（安全）；
- **均未改。**

**新增测试（各 6 例，共 12；JUnit4 + JDK `com.sun.net.httpserver.HttpServer` + 真实 `EventSources`，未新引依赖；`@Test` = 6/6 本机实测）**

| # | 用例（方法名） | 断言要点 |
|---|---|---|
| 1 | `non 2xx with blank body must close with non null exception` | 非 2xx + 空 body ⇒ 传给 `closeFlow` 的值**非 null** |
| 2 | `non 2xx blank body fallback message must carry http status code` | 兜底信息含 `HTTP <code>`（测试用 502） |
| 3 | `non 2xx with json body must keep existing parsed detail` | 有 body 且可解析 ⇒ 仍是 `HttpException`、信息 = `upstream exploded`、**不含** `HTTP 500` |
| 4 | `non 2xx with unparseable body must still close with non null exception` | 非 2xx + 乱码 body（`<html>bad gateway</html>`）⇒ **非 null** 且含 `HTTP 500` |
| 5 | `2xx blank stream must stay a normal completion` | 2xx 空流 = **正常完成（null）**，语义不变 |
| 6 | `2xx sse stream still yields text chunks` | 2xx 正常流产出 chunk，正文逐字 `"你好"` |

文件（行数/SHA-256 本机实测）：`ai/src/test/.../openai/ResponseApiStreamFailureTest.kt`（169 行 / `65e6e9ca0c24c0d6e00f8a64b714d5dfee40e4299217bd16140a2abbcdbb19db`）、`ai/src/test/.../claude/ClaudeStreamFailureTest.kt`（174 行 / `998fb6171e9ce1cf050fb846dfe73556a58efa2f511a1607bf8e24ff81ca9f7a`）。

⚠️ **非空验证（执行者报告，登记时未复现——约束：不跑任何 gradle）**：两处各把兜底行改回 `closeFlow(exception)` ⇒ **恰好各 3 条红**（非 2xx 空 body 非 null / 空 body 状态码 / 乱码 body），**exit 1**，其余 3 条绿；随后精确还原，`git diff` 为空。

**② 「删除最后一个助手必崩」修复（`85f708169` production + `81147639a` 测试）**

真机复现（执行者报告；崩溃栈 **本机复核**）：只剩 1 个助手时删除它 ⇒ 崩溃进 SafeMode。崩溃栈留档 `/tmp/opencode/c1-device-round4/ui/r4-12-after-3.xml`（15846 B / SHA-256 `c63e14e7386c75d42a1eb365055a931ce60d5a5008f1501cd0329d4166e65677`，**本机 `sha256sum` 实测与之一致**）。

崩溃链（本机读码核实）：`AssistantViewModel.removeAssistant`（原 `:48-61`）把 `assistants` 过滤成空列表 → `SettingsRepository.update()`（原 `:442-449`）**先 `settingsFlow.value = settings` 再 `persistSettings`** → `settingsFlow` 是直接暴露给全 app 的 `MutableStateFlow`，在 `Main.immediate` 上**同步**唤醒 `ChatViewModel` 的 `settings.map { it.getCurrentAssistant().enableWebSearch }` 收集器 → `getCurrentAssistant()`（原 `:745`）的 `this.assistants.first()` 抛 `NoSuchElementException` → **崩在 persist 之前** ⇒ 删不掉、进 SafeMode。

**关键事实（本机读码核实）**：`SettingsRepository.kt:365` 的读取兜底位于 `settingsFlowRaw = dataStore.data.map { ... }`（`:254` 起，`:348` 是那个 `.map {`）**读取管线内**——是**读时兜底不是写时兜底** ⇒ 空列表**确实会落盘**（`preferences[ASSISTANTS] = "[]"`），只是下次读取被换成默认。**它保护不了 `update()` 里直接赋值 `settingsFlow` 的中间态。**

**选中方案（B 允许删空 + C 兜底）与理由**：允许删空并**立即恢复默认**（而不是加「不许删最后一个」限制），因为与既有读时 `ifEmpty` 设计意图一致、且「删完后立刻的状态」=「重启后的状态」；改动最小、不动 UI、不加字符串。

改动清单（production 2 文件，净 **+29/−9**，本机 `git show --stat` 实测）：

| 文件 | 改动 |
|---|---|
| `app/.../datastore/SettingsRepository.kt` | 新增纯函数 `internal fun normalizeAssistants(assistants) = if (assistants.isEmpty()) DEFAULT_ASSISTANTS else assistants`（`:760`）；读路径 `:365` 改用它；`update()` 写路径先 `val updated = settings.copy(assistants = normalizeAssistants(settings.assistants))`（`:450`）再赋值 + 落盘；新增纯扩展 `fun Settings.removeAssistant(assistant) = copy(assistants = normalizeAssistants(assistants.filterNot { it.id == assistant.id }))`（`:768`）；`getCurrentAssistant()`（`:747`）变全函数 `find { it.id == assistantId } ?: firstOrNull() ?: DEFAULT_ASSISTANTS.first()` |
| `app/.../feature/assistant/AssistantViewModel.kt` | `removeAssistant` 改用 `settingsStore.update(settings.removeAssistant(assistant))` |

**新增测试** `app/src/test/.../core/data/datastore/AssistantRemovalInvariantTest.kt`（81 行 / SHA-256 `8a10723f54567403a59bc4ce5445bbafed6949d23d16ac74ad3e68f5e049a462`，本机实测），**6 例（`@Test` = 6 本机实测）**：

| # | 用例（方法名） | 断言要点 |
|---|---|---|
| 1 | `removing the last assistant does not crash and falls back to defaults` | **核心回归**：删最后助手后列表 = `DEFAULT_ASSISTANTS`，`getCurrentAssistant()` 不抛且 = 默认 |
| 2 | `getCurrentAssistant tolerates empty list from any source` | 直接构造空列表（C 兜底）也有确定返回值 |
| 3 | `removing one of many keeps the rest unchanged` | 删多个之一保持其余不变 |
| 4 | `removing an unknown assistant is a no-op` | 删不存在的助手是 no-op |
| 5 | `normalizeAssistants replaces only empty lists` | 只替换空列表（非空列表 `assertSame`） |
| 6 | `update write path never persists empty assistants` | 写入路径（`copy(assistants = normalizeAssistants(...))`）永不落空列表 |

⚠️ **未测 `AssistantViewModel`**（Hilt + Compose + Main 收集器耦合太重，不为测试重构），改为测 VM 现在调用的纯函数/扩展；第 6 例是**模拟**写路径（不调真实 `SettingsRepository.update()`，那要 DataStore）。

⚠️ **非空验证（执行者报告，登记时未复现）**：还原两个 production 文件 + 移走新单测，跑探针复刻原 `removeAssistant` ⇒ `NoSuchElementException`、`1 test completed, 1 failed`、`PROBE_EXIT=1`（**恰好**核心回归变红）；随后 `git apply` 还原，`diff` 得 `DIFF_IDENTICAL`。

**同类空列表风险扫描结论：全仓 `assistants.(first()|last()|single()|reduce|maxBy|minBy|maxOf|minOf|elementAt|[index])` 扫描 = 0 命中（本机 grep 实测，无匹配退出码 1）。** 修复前唯一的 `first()` 就是 `getCurrentAssistant()`；其余调用形态（`map`×18、`none`×7、`find`×4、`filter`×4、`firstOrNull`×3 等）均安全；`getCurrentChatModel()` 经 `getCurrentAssistant()` 已随之安全。**无其它假设非空的读取点。**

**③ 新基线（改后实测；app/ai 取 XML 属性、lint 取脚本输出）**

| 项 | 值 |
|---|---|
| `:app:testDebugUnitTest` | **113 类 / 931 例 / 0F0E0S**（原 112/925；+1 类 +6 例全部来自 `AssistantRemovalInvariantTest`） |
| `:ai:test` | **30 类 / 220 例 / 0F0E0S**（原 28/208；+2 类 +12 例） |
| lint app | `error 0 / warning 581 / hint 6 = 587`（**未变**，未加字符串）；全模块 621（未变） |
| `c1_doc_stats.py` | **OK 18 / WARN 0 / FAIL 0 exit 0**；台账 **46 行 / 501 例**（未动） |
| 脚本 sha256 | `238bb45f7035852acf3f27013bd5d3d6372191a4543d3297ef7044e3899f4321`（**未改**） |

⚠️ 上表 app/ai 两行是**登记时本机读 `test-results` XML 实测**（app `TEST-*.xml` 113 个、`tests` 求和 931；ai `TEST-*.xml` 30 个、求和 220）；lint 与脚本行是**本机跑 `c1_doc_stats.py` 实测**。⚠️ **本批新增测试类在 `app/src/test/.../datastore/` 目录、不在台账那 46 个 C1 在册类名单里** ⇒ 台账声明值 `46 行 / 501 例` **不动**（本机实测：`台账行数 46 行` / `声明合计 501 例` / `逐行核对 46 行全部相等`）。

**④ 四条限制（不许美化）**

1. **真机行为未复测**：两处 AI 修复与助手删除修复**都没有在真机上复跑过**（零设备）；「删最后一个助手不再崩」只有**单测 + 探针**，**真机 SafeMode 复现路径未再走一遍**。
2. **`AssistantViewModel` 未直测**：6 例测的是它现在调用的纯函数/扩展，**不是 VM 本身**（`Main.immediate` 时序、Hilt 注入、UI 层均未覆盖）。
3. **DataStore 落盘与 `Main.immediate` 时序未端到端**：修复断言的是「列表非空」这个不变量，**没测**「同步收集器在写入过程中读 `settingsFlow`」的真实并发时序（第 6 例是模拟）。
4. **Claude 的 `exception = e` 在 JVM 单测里因 `Log.w` 先抛而不可达** ⇒ 该分支的运行时行为**靠代码审查**（不是实测）；乱码 body 用例覆盖的是「`closeFlow` 收尾必非 null」（走的是 `?: HttpException` 兜底），**两条分支的实际日志路径未在单测中观测**。

⚠️ **本节标「执行者报告」者均未被登记代理独立复现**（约束：不跑任何 gradle，以免重写 test-results XML）；其余标「本机实测 / 本机读码核实」者均为登记代理当场执行。

### 全项目总闸门（零设备，2026-10-06，HEAD `0e312cbb2`：assembleDebug test lint 首次整项目 exit 0 + `--rerun` 作用域陷阱）

⚠️⚠️ **先说性质：这是包级 / 项目级门禁证据，不是用例级证据。它证明的是「整项目一条命令 `assembleDebug test lint` 全绿」，不改变本文件任何用例的状态列**——20 个状态格一个字都没动（仍是 10/10 `unverified`；其阻塞项见本文件既有清册）。⚠️ 本节的退出码、日志原文、逐模块数字均为**子代理执行报告转述**（约束：登记代理**不跑任何 gradle**，以免重写 `test-results` XML）；凡标「本机实测」者为登记代理当场执行。

**⓪ 目标依据**

C1 目标里有一条硬性验收标准：「每包末 `./gradlew --offline assembleDebug test lint` 全绿（退出码 0）才写已完成」，并且第 7 条明写「跑通离线 `./gradlew --offline assembleDebug test lint` 全绿，把**命令与退出码写进 `docs/eval/c1-group-chat.md`**」。此前所有验证都是**按模块**跑的，**从未跑过全项目一条命令**；本次补上了，故登记。

**① 命令一：全项目总闸门（首次整项目一条命令）**

```bash
nice -n 19 ./gradlew --offline assembleDebug test lint
```

| 项 | 值 |
|---|---|
| 输出关键行 | `BUILD SUCCESSFUL in 44s` / `839 actionable tasks: 173 executed, 666 up-to-date` / `Configuration cache entry stored.` |
| 退出码 | **0** |
| 日志 | `/tmp/opencode/full-gate/run.log`（1120 行），sha256 **`b0257e0c83a564a7444a2b10a2d82bb224b454d31486c3642d7098a21d1a3128`** |
| 调用形态 | **单次全项目调用**、无 `--tests` 过滤、无 `connectedAndroidTest`、脱离进程组运行 |

**逐模块 `test` 结果**（解析 `*/build/test-results/*/TEST-*.xml`）：

| 模块 | 类 | 用例 | failures | errors | skipped |
|---|---:|---:|---:|---:|---:|
| ai | 30 | 220 | 0 | 0 | 0 |
| app | 113 | 931 | 0 | 0 | 0 |
| card-validator | 4 | 77 | 0 | 0 | 0 |
| common | 2 | 6 | 0 | 0 | 5 |
| document | 1 | 1 | 0 | 0 | 0 |
| highlight | 5 | 53 | 0 | 0 | 0 |
| khatkit | 33 | 198 | 0 | 0 | 7 |
| khatkit-ui | 2 | 10 | 0 | 0 | 0 |
| material3 | 1 | 1 | 0 | 0 | 0 |
| mediapicker | 1 | 9 | 0 | 0 | 0 |
| oauth | 2 | 2 | 0 | 0 | 0 |
| search | 3 | 12 | 0 | 0 | 0 |
| speech | 12 | 43 | 0 | 0 | 0 |
| web | 1 | 1 | 0 | 0 | 0 |
| workspace | 3 | 20 | 0 | 0 | 0 |
| **合计** | **213** | **1584** | **0** | **0** | **12** |

**全模块 lint**：`error 0 / warning 614 / hint 7 = 621`。app 明细 `error 0 / warning 581 / hint 6 = 587`（app 报告 txt 结尾原文 `0 errors, 581 warnings, 6 hints`）。除 app 外的逐模块 warning：`image-toolbox-dependency` 5、`khatkit` 17、`khatkit-ui` 4（+1 hint）、`ai` 2、`common` 1、`oauth` 1、`speech` 1、`workspace` 2（合计 33）；其余（`document` / `highlight` / `material3` / `mediapicker` / `search` / `web`）均为 0。⇒ `581 + 33 = 614` warning、`6 + 1 = 7` hint，与全模块汇总自洽。

**无模块因失败而缺失 XML**：15 个含 `src/test` 的模块全部产出。无 XML 的仅 2 个，且均**无 `src/test` 目录**：`:image-toolbox-dependency`（日志 `testDebugUnitTest NO-SOURCE`，该模块设计上不随应用编译）、`:app:baselineprofile`（仅 `src/main`，benchmark 模块）。历史坑 `:app:mergeLibDexDebug` 本次为 **UP-TO-DATE**，**未复现**此前「停在这里」的中断。

⚠️ **诚实点（不许美化）**：本次 **666 个任务 UP-TO-DATE、仅 173 个执行**；`:app:testDebugUnitTest` 与 `:ai:testDebugUnitTest` 在**命令一里是 UP-TO-DATE**（未重跑），其 XML 产出于 19:23 / 19:35，**晚于受保护源码文件 mtime（10-05 14:04）**，故数字仍代表当前工作区。其余各模块 `test` 任务本次为**实际执行**。⇒ 下面专门补了命令二来消除这个疑点。

**② 命令二：强制重跑 app + ai 单测（消除 UP-TO-DATE 疑点）**

⚠️ **先用主管原给的写法跑了一次，发现是空跑**：

```bash
./gradlew --offline :app:testDebugUnitTest :ai:test --rerun   # ← 错误写法
```

结果 `EXIT=0` 但 **`287 actionable tasks: 287 up-to-date`**，两个测试任务**全是 UP-TO-DATE，一个都没执行**。

**根因**：`gradle.properties` 里 `org.gradle.configuration-cache=true`；`--rerun` 是**任务级选项**（`./gradlew help --task :app:testDebugUnitTest` 明示 "Causes the task to be re-run even if up-to-date"），**只作用于紧邻其前的那一个任务**。错误写法里 `--rerun` 落在 `:ai:test` 上——而 `:ai:test` 是**无 action 的生命周期聚合任务**，rerun 它**不会**强制其依赖 `:ai:testDebugUnitTest`；`:app:testDebugUnitTest` 则根本没绑上 rerun。

**修正命令（照抄这个）**：

```bash
nice -n 19 ./gradlew --offline :app:testDebugUnitTest --rerun :ai:testDebugUnitTest --rerun
```

| 项 | 值 |
|---|---|
| 结果 | `EXIT=0`，`BUILD SUCCESSFUL in 20s`，**`287 actionable tasks: 2 executed, 285 up-to-date`**（两个测试任务都**没有** UP-TO-DATE） |
| 日志 | `/tmp/opencode/final-rerun/run2.log` |

| 模块 | 类 | 用例 | F | E | S | 旧 XML mtime | 新 XML mtime |
|---|---:|---:|---:|---:|---:|---|---|
| `:app:testDebugUnitTest` | 113 | 931 | 0 | 0 | 0 | 19:35:03 | **19:51:44** |
| `:ai:testDebugUnitTest` | 30 | 220 | 0 | 0 | 0 | 19:23:45 | **19:51:32** |

两个新 mtime **均晚于本轮启动时间 `19:51:24`** ⇒ 确实真实执行过（非 UP-TO-DATE 残留）。与预期逐项相符，**无任何红项**。

**③ 与 `c1_doc_stats.py` 的交叉核对**

重跑后再跑 `python3 tools/verification/c1_doc_stats.py` → **EXIT=0**，`合计 18 条：OK 18 / WARN 0 / FAIL 0`；`台账行数 46 行` / `台账声明例数合计 501 例`；脚本 sha256 跑前跑后都是 `238bb45f7035852acf3f27013bd5d3d6372191a4543d3297ef7044e3899f4321`（**零字节改动**）。

**④ 限制 / 边界（不许美化）**

1. **用了 `--offline`**：**未做联网依赖解析校验**。本门禁只证明「在本地已缓存依赖的前提下 assembleDebug + test + lint 全绿」。
2. **「无该产物」是推断、不是失败**：`card-validator` 无 lint 报告、`app/baselineprofile` 无单测源，依据目录结构 + `NO-SOURCE` / UP-TO-DATE 日志判定为「本就不产出该产物」，**不是**它们跑失败。
3. **门禁全绿 ≠ 十例验收达成**：十例仍 **0/10 `verified`**，其阻塞项见本文件既有清册与「下一位怎么把 unverified 变成 verified」。

⚠️ **本次未新增 / 修改任何源码或测试**；`git status --porcelain` 与开工**逐字节一致**（执行者报告：门禁运行前后工作区三处在途改动 `TavernMacroExpander.kt` / `architecture-map.md` / `C1GroupUiE2EFixtureTest.kt` 一个未碰）。


### C1 真机证据采集第十一轮（2026-10-06，HEAD a8302e463：roundtable/显式@/vote 三条真实网关首跑全绿 + 平票失败路径 + pipeline 复跑 + 两条 UI 测试通过）

⚠️⚠️ **先说性质与总账**：本批 = **一次真机采集窗口**（OnePlus `PKG110` / Android 16 / API `36` / `arm64-v8a`；无线调试 serial `192.168.31.183:34753`——执行者报告，证据 JSON 的 `device` 字段四要素与前述设备一致）。**4 条真实网关用例 + 1 条 pipeline 复跑 + 2 条 UI 测试全部 `OK (1 test)`**，产物全部 `adb pull` 回主机并记 SHA-256（**登记代理逐个复算：下列 15 份文件的字节数与 SHA-256 全部与执行者报告一致**）。

⚠️ **但 20 个状态格一个升级都没有，仍是 10/10 `unverified`**——逐行依据见本节 ⑨。⚠️ **所有通过项均为单次通过，未做重复稳定性验证**（本批没有重复跑任何一条）。⚠️ **哪些数字是登记代理实测、哪些是转述**：JSON 逐字段值、文件字节数 / SHA-256、日志原文、trace 间隔为**登记代理现场读取 `/tmp/opencode/c1-device-r6/`**；构建 exit 0、两条 `install -r -t` `Success`、serial、ColorOS 冻结现场操作为**执行者报告**（本批硬约束不重跑 gradle、不接设备）。

**① 跑法与全部产物（字节 / SHA-256 为登记代理复算）**

| # | 方法（对应用例） | 结果 | Time | 证据文件 | 字节 | SHA-256 |
|---|---|---|---:|---|---:|---|
| 1 | `realProviderRoundtableRecordsChairSummaryCallSequence`（C1-03） | 第 1 跑 FAIL、重跑 **`OK (1 test)`** | 18.396 / **11.634** | `c1-live-evidence-real-roundtable.json` | 11194 | `c6389e80c159660ce3fa9aaf5e634d372f544b7872447c55b4c7863d7777185a` |
| 1b | 同上 raw（通过那跑） | — | — | `c1-real-raw-dump-roundtable.json` | 6068 | `b10aa1178cc1104cd38532ca4f1b84da7beefacf8b8aaf5d6d185e4c3667bb42` |
| 1c | 同上 raw（首跑 FAIL 那版） | — | — | `c1-real-raw-dump-roundtable.json`（早期 pull 快照） | 5765 | `00ce40af935dc0c27f939d16116d01edb3a116571577cbb3f6c28f3790817bf4` |
| 2 | `realProviderMentionNarrowsSpeakersToMentionedRole`（C1-01） | **`OK (1 test)`** | 5.029 | `c1-live-evidence-real-mention.json` | 8384 | `c7df9425941ffcf362bbb850f4e9d896d0ee46f412bc803d0d023a13da6b5c9d` |
| 2b | 同上 raw | — | — | `c1-real-raw-dump-mention.json` | 3691 | `3581de8b0d4701fddaa6625c37fbfee82f9908a161dfc1055ca2135244aca8fd` |
| 3 | `realProviderVoteRoundRecordsBallotCallsAndDecision`（C1-04 多数决） | **`OK (1 test)`** | 14.595 | `c1-live-evidence-real-vote.json` | 12440 | `1b7f0be7d778c7939cfeb5a828a5c2f656e811332e30208c6392bf0d314478ba` |
| 3b | 同上 raw | — | — | `c1-real-raw-dump-vote.json` | 7063 | `68ba9ee503096e36dde7e5a3a10f083330aab09d2fc7563d2b221f2445aeb6eb` |
| 4 | `realProviderVoteTieFailsPerConfiguredPolicy`（C1-04 平票） | 前 2 跑被 ColorOS 冻结作废、第 3 跑 **`OK (1 test)`** | 798.666 / 301.593 / **11.967** | `c1-live-evidence-real-vote-tie.json` | 12715 | `623745f6c52daa220087903d0be3e764dfd68a04a63d7db5ea9ffc8b970c80fc` |
| 4b | 同上 raw（通过那跑） | — | — | `c1-real-raw-dump-vote-tie.json` | 7098 | `58fd0dfe5dc8d94e4337c3b5c50f89c1a72eb80d65563ae6dcf7c676349e550c` |
| 4c | 同上 raw（冻结第 1 跑） | — | — | `c1-real-raw-dump-vote-tie.json`（早期 pull） | 2703 | `edc417b1459c54e1aae8519e985f3c77ef217c19b39075f6188e085a0629ec04` |
| 4d | 同上 raw（冻结第 2 跑） | — | — | `c1-real-raw-dump-vote-tie.json`（中期 pull） | 3757 | `26cd67895544aece7b38d69aaf48d642a2909e180b19e1733095d204000d449e` |
| 5 | `realProviderRoundRecordsGenuineTokenUsage`（C1-02 pipeline 复跑） | 第 4 次尝试 **`OK (1 test)`** | **10.409**（前三次见 ⑥） | `c1-live-evidence-real-provider.json` | 10443 | `c73f46bff87f469aabf594b6a8bd44cd8093472467312b410c13e694ee22a242` |
| 5b | 同上 raw（通过那跑） | — | — | `c1-real-raw-dump.json` | 6028 | `b778da79bed2686b4158bc9129186ff1e777717dc4cec8c328c53c868df20639` |
| 5c | 同上 raw（尝试② modelId 失败那跑） | — | — | `c1-real-raw-dump.json`（早期 pull） | 5870 | `6997cfa5ec7fec2b4d4342e732fc00b137097dbafae88a8bd9d323e4c1d147da` |
| 5d | 同上 raw（尝试③ ROLECODE 失败那跑） | — | — | `c1-real-raw-dump.json`（中期 pull） | 6043 | `df882218bec5828e7d0f7f81248f5c51a044af050c273a6a217008f67a317bff` |
| 6 | `typeFilterChipsFilterWithoutLosingData`（C1-10） | **`OK (1 test)`** | 4.026 | `c1-round5-filter.txt` | 288 | `31bd00987f4d13e64a0c6eef84b784ae7bbcb9a32812e46edf7369154ba9bf81` |
| 7 | `atMentionPickerInsertsParseableMention`（C1-10） | **`OK (1 test)`** | 4.365 | `c1-round5-mention.txt` | 249 | `0a659f31eba3d1a7661283b719ed383241c23b4f7e256dc9fd378c2a60631bae` |
| 8 | `groupMemberBarRendersOneAvatarPerRole`（C1-10） | 本轮**未重跑**（复用第十轮证据） | — | `c1-round5-avatar.txt` | 130 | `33257ac4ad71d07d0313a385185e34885bb5102180fc0884cae78afe7d61f046` |

⚠️ 跑法是手动 `am instrument` 单方法（**不用 `connectedAndroidTest`**——它会卸载 app、清空外置证据目录，本文件已多轮登记该坑）；每条退出后 `adb pull`。⚠️ **shell 退出码没有落进证据日志**：通过的日志以 `OK (1 test)` 收尾、失败的以 `FAILURES!!!` 收尾，`am instrument` 的 shell 退出码**本批仍未落盘**（与第八轮⑧第 1 条同一缺口，本批未改善——但对比第七批「四条退出码全 0」的登记口径，本批的「通过」依据是日志原文而非退出码）。

**② 显式 @（C1-01，真实网关首绿）**

- 触发：真机真库、`mode=pipeline`；用户消息 `@角色乙 请只由你发言一次。`（`id=c707437f-f26a-4d66-8b6f-f39977b84a39`），生产 `GroupChat.parseMentions` 解析出 `trigger_mention_role_ids=[b]`；`GroupChat.plan` 后 `plan_selected_role_ids=[b]`、`only_mentioned_role_invoked=true`。
- **实际调用**：只有 1 次调用，`role_id=b`、`turn_kind=speaker`、wire 名 `glm-5.2`（`wire_response_model`，与 binding `8b6bf21c…` ↔ `glm-5.2` 一致；`wire_and_reverse_lookup_agree=true`）；b 的 usage `prompt 6546 / completion 54 / total 6600`，落库 `spent_tokens=6600`（`spent_tokens_equals_sum_prompt_plus_completion=true`），`status=COMPLETED`、`committed_role_ids=[b]`、`skipped=[]`。
- **viewer 可见消息 ID（真库 UUID）**：`a=[c707437f-f26a-4d66-8b6f-f39977b84a39]`；`b=[c707437f-f26a-4d66-8b6f-f39977b84a39, ca2631a7-6617-4530-98e1-b15a31961730]`；`c=[c707437f-f26a-4d66-8b6f-f39977b84a39]`。
- b 的回复正文 `\nROLECODE:B 已就位，等待指令。`（`rolecodes_found_in_text=["B"]`）。
- ⚠️ **口径必须写清（本行的关键）**：触发消息是 **USER 消息**，生产口径（`GroupChat.visibleMessages` 的 USER 分支）**对每个 viewer 都放行**——所以 A/C「看不见 b 的本轮输出」成立，但 **A/C 仍看得见那条 @ 触发消息**；测试源码 `C1LiveModelSequenceTest.kt:1954-1973` 把「viewer=a/c 不得看见 b 的本轮输出」与「viewer=a/c 仍可见触发的用户消息（生产口径）」**两条都写成了断言**。⇒ 本行断言的「只被 @ 角色收到该消息；其他角色不可见」里，「投递收窄」这半**已真机成立**；「其他角色看不到那条消息」这半**仍未在真机观测到**（要观测需「@ 一条角色发言」或「@ 不指代任何角色的输入」，见「已知遗留与风险」第 15 条）。
- 证据：`c1-live-evidence-real-mention.json`（8384 B / `c7df9425…`）、raw `c1-real-raw-dump-mention.json`（3691 B / `3581de8b…`）；日志 `realProviderMentionNarrowsSpeakersToMentionedRole.log`：`Time: 5.029` / `OK (1 test)`。

**③ roundtable（C1-03，真实网关首绿；首跑 FAIL 的原文如实登记）**

- **首跑 FAIL 原文**（`realProviderRoundtableRecordsChairSummaryCallSequence.log`）：
  `java.lang.AssertionError: 只有议长 c 的发言是 turn_kind=chair，实际=[a:speaker, b:speaker, c:error] expected:<{a=speaker, b=speaker, c=chair}> but was:<{a=speaker, b=speaker, c=error}>`（`:1741`）；`Time: 18.396` / `FAILURES!!! Tests run: 1, Failures: 1`。
  该跑 raw（5765 B / `00ce40af…`）：`status=FAILED / reason=role_failed / spent=13642 / committed=[a,b]`；a `6831/251/7082`、b `6546/14/6560`、c 为 `turn_kind=error`、正文 `[角色丙] 本轮生成失败：本轮没有产出内容`；`wire_model_name_provenance_counts={wire_response_model:2, uuid_reverse_lookup_fallback:1}`。
- ⚠️ **该 FAIL 跑已核实无冻结断档**：登记代理对 `trace-3.txt` 第 8377–8855 行（`real-roundtable:preflight-begin` → `raw-dump-written … run=FAILED`）逐行算时间戳间隔：**144 个带时间戳行，最大间隔 261ms，>3s 的断档 0 处**（执行者报告的口径是「152 条 trace」；两口径差异如实并列，结论一致：不是冻结、是一次真实模型行为导致的 `role_failed`）。
- **重跑 PASS**（`…-retry.log`：`Time: 11.634` / `OK (1 test)`），`c1-live-evidence-real-roundtable.json`：
  - conversation `7cad5e28-4809-4c3e-bedd-7ebfe44356cb`，round `round-6c8ca5e5-19fe-4780-9595-d77f1339135e`。
  - 4 条消息：USER `6c8ca5e5…`（`请三位依次发言，议长最后汇总。`）；a `77485ecf-b436-410e-9bf6-1b8c9a6b3485`（speaker，deepseek-v4-flash，`6831/96/6927`，正文 `\nROLECODE:A 我是真实数据提供方。`）；b `f1fed06e-f344-4fcc-8c94-efba8daf3f6c`（speaker，glm-5.2，`6546/64/6610`，`\nROLECODE:B 收到，请开始发言。`）；c `f01e8f98-74a5-4051-895e-459966dbec0d`（**chair**，deepseek-v4-flash，`6998/188/7186`，`\nROLECODE:C 提供实时数据源，随时可查。`）。
  - **实际调用序列 = `deepseek-v4-flash → glm-5.2 → deepseek-v4-flash`**，与绑定逐位一致；`wire_model_name_provenance_counts={wire_response_model:3, uuid_reverse_lookup_fallback:0}`、reconciliation `calls_with_wire_name=3 / differ=0 / no_name=0`。
  - token 对账：`6831+96 + 6546+64 + 6998+188 = 20723 = spent_tokens`（`spent_tokens_equals_sum_prompt_plus_completion=true`）；`status=COMPLETED / committed=[a,b,c] / skipped=[] / ended_at` 非空。
  - **viewer 可见消息 ID（含议长放开分支）**：`a=[6c8ca5e5…, 77485ecf…]`；`b=[6c8ca5e5…, f1fed06e…]`；`c=[6c8ca5e5…, f01e8f98…]`；**`c(chairRound=true)=[6c8ca5e5…, 77485ecf…, f1fed06e…, f01e8f98…]`**（议长放开本轮全部）。
  - 测试源码把「A/B 看不见彼此未完成输出」与「不传 `chairRound` 时议长也不该看见他人」写成了独立断言（`C1LiveModelSequenceTest.kt:1765-1777`）。
  - ⚠️ **边界照记**：c 的 chair 轮正文是一句角色自述，**不是对 a/b 的归纳文本**——本行验的是「只有 c 是 chair + 议长 prompt 含全员 + 其余视角看不到未完成输出」这个机制；「汇总文本质量」不在本行断言范围。

**④ vote 多数决（C1-04 第一条路径，真实网关首绿）**

- conversation `253882b8-c324-4aee-a7df-7ea4b34e3b2d`，round `round-d907def5-c093-41ab-9c9b-07c2bd29498f`；候选 `["opt-a","opt-b","opt-c"]`。
- 5 条消息：USER `d907def5…`；a `66d8b741-c0e4-4386-9bbf-541bf2635a30`（`\nVOTE: opt-a | 该方案最稳妥。`，`6827/63/6890`）；b `ae51f25e-e7b7-448a-acaf-f6518fe5a1ed`（`\nVOTE: opt-a | 该方案最为稳妥合理。`，`6545/41/6586`）；c `28bae814-8c20-499e-b0e3-d75f929c170a`（`\nVOTE: opt-b | 该方案最稳妥。`，`6827/39/6866`）；`__summary__` `4503072d-471d-43b7-8abc-b5b4b22796dd`（`turn_kind=vote_summary`、`has_usage=false`、正文 `本轮投票结果：opt-a\n票数：opt-a 2 / opt-b 1`）。
- **生产解析可复算**：`GroupChat.parseBallot` 从库内正文解析出 3 张有效票 `a→opt-a、b→opt-a、c→opt-b`，`GroupChat.tally` 得 `VoteOutcome.Decided / winner=opt-a / counts={opt-a:2, opt-b:1}`、`tie_branch_taken=false`。
- token：`6827+63 + 6545+41 + 6827+39 = 20342 = spent_tokens`；`status=COMPLETED / committed=[a,b,c]`；`wire_model_name_provenance_counts={wire_response_model:3, uuid_reverse_lookup_fallback:0}`。
- viewer：`a=[d907def5…, 66d8b741…, 4503072d…]`；`b=[d907def5…, ae51f25e…, 4503072d…]`；`c=[d907def5…, 28bae814…, 4503072d…]`（**每视角只见到自己的票面 + 对所有视角可见的 `__summary__`**，测试 `:2129-2142` 是独立断言）。
- 日志 `realProviderVoteRoundRecordsBallotCallsAndDecision.log`：`Time: 14.595` / `OK (1 test)`。

**⑤ vote 平票按配置失败（C1-04 第二条路径，真实网关首绿；两次冻结的原文如实登记）**

- 三次尝试：① `Time: 798.666` FAIL；② `Time: 301.593` FAIL；③ `Time: 11.967` **`OK (1 test)`**。
- 冻结两跑的异常原文：
  ① `java.lang.AssertionError: 等待助手消息超时（300000ms）：期望 4 条，实际 0 条。最后看到的消息=[USER/null]；app 错误=[]`（`:3356`）；raw（2703 B / `edc417b1…`）`status=RUNNING / spent=0 / committed=[]`。
  ② `java.lang.AssertionError: 等待助手消息超时（300000ms）：期望 4 条，实际 1 条。最后看到的消息=[USER/null, ASSISTANT/a]；app 错误=[]`；raw（3757 B / `26cd6789…`）`status=FAILED / reason=role_failed / spent=0 / committed=[] / skipped=[b,c]`，a 的消息是 `turn_kind=error`、正文 `[角色甲] 本轮生成失败：本轮没有产出内容`。
  （300000ms 超时 = 5 分钟整；两跑分别 `Time: 798.666` / `301.593` —— 即「测试整体被冻结、模型请求根本没发出去」，不是断言失败于业务逻辑。）
- **第 3 跑 PASS**（`c1-live-evidence-real-vote-tie.json`，12715 B）：conversation `efbd1db5-a28f-4f58-8d4c-3ee3e4ba1124`，round `round-5a7c7bc2-4f85-4f6d-bc23-0f5a40df167c`；5 条消息：USER `5a7c7bc2…`；a `fc662863-8298-4c04-96b1-88e553d22522`（`\nVOTE: opt-a | 该方案最稳妥。`，`6827/53/6880`）；b `c3dbdee4-350c-4190-bace-8bec6046adea`（`\nVOTE: opt-b | 方案最为稳妥合理`，`6545/66/6611`）；c `32de4bde-0f05-400f-929f-b1c618cb3124`（`\nVOTE: opt-c | 该方案最符合当前需求。`，`6827/43/6870`）；失败节点 `428cbada-b144-4d89-af8c-36111c07c4a2`（`role_id=__summary__`、**`turn_kind=error`**、正文 `[投票] 本轮未能得出结论：平票：opt-a, opt-b, opt-c`）。
- **平票路径逐项**：生产解析 3 张有效票 `a→opt-a、b→opt-b、c→opt-c`；`tally` 得 `VoteOutcome.Tie / tie_branch_taken=true / tied_candidates=[opt-a,opt-b,opt-c] / counts 1:1:1`；落库 `status=FAILED / reason=vote_no_decision / error_message=平票：opt-a, opt-b, opt-c / spent=20361`（`spent_tokens_equals_sum_prompt_plus_completion=true`）；**测试显式断言不得写 `vote_summary`**（`:2242-2245`）。
- viewer：`a=[5a7c7bc2…, fc662863…, 428cbada…]`；`b=[5a7c7bc2…, c3dbdee4…, 428cbada…]`；`c=[5a7c7bc2…, 32de4bde…, 428cbada…]`（每视角只见到自己的票面 + 失败节点）。
- `wire_model_name_provenance_counts={wire_response_model:3, uuid_reverse_lookup_fallback:0}`。

**⑥ pipeline 复跑（C1-02：前三次失败 + 第 4 次通过）**

- 尝试①：**被 OPPO osense SIGKILL**——日志 `realProviderRoundRecordsGenuineTokenUsage.log` 只有 `INSTRUMENTATION_RESULT: shortMsg=Process crashed.`；`logcat-after-attempt1.txt` 实测（登记代理 grep）：`I am_kill : [0,9594,heizige.kk.khatkit.debug,0,Cached(nirvana)[(instrumentation)]]` → `am_proc_died`，无 Time、无产物。
- 尝试②（`…-retry.log`）：`Time: 13.178`，`java.lang.AssertionError: 角色 c 的 modelId 必须是 deepseek-v4-flash expected:<5a86b2d6-9c3c-4c58-9b27-f9295ba39201> but was:<null>`（`:1527`）；raw（5870 B / `6997cfa5…`）`status=FAILED / reason=role_failed / spent=13705 / committed=[a,b]`，c 正文 `[角色丙] 本轮生成失败：本轮没有产出内容`，provenance counts `{2,1}`。**该跑已核实无冻结断档**：`trace-4r.txt` 第 9862–10180 行 `111 个带时间戳行、最大间隔 256ms、>3s 断档 0 处`。
- 尝试③（`…-retry2.log`）：`Time: 19.415`，`java.lang.AssertionError: 角色 c 的回复里必须出现自己的 ROLECODE:C，实际=[B]；正文=\nROLECODE:B 请A先发言。`（`:1627`）；raw（6043 B / `df882218…`）却是 `status=COMPLETED / spent=20701 / committed=[a,b,c]`、provenance counts `{3,0}`——**模型行为偏差**（c 复读了 B 的格式与内容），不是生产逻辑错。
- 尝试④（`…-retry3.log`）：`Time: 10.409` / **`OK (1 test)`**，`c1-live-evidence-real-provider.json`（10443 B / `c73f46bf…`）：
  - conversation `aea6e22b-2aab-48d6-9792-35bbe38910a4`，round `round-180dc5b5-3aa8-42fd-a4d4-64a2fcdb5b7b`；4 条消息：USER `180dc5b5…`（`请三位依次发言，每位一句话。`）；a `7ff1909a-2d73-4a42-aa60-75a1acf82167`（`6829/136/6965`，`\nROLECODE:A 我已就位。`）；b `0a7c3c5c-ca34-4550-96aa-d8001510e51d`（`6680/42/6722`，`\nROLECODE:B 我已就位，等待指令。`）；c `73318270-22c2-47b1-a364-6c886c6bed4d`（`6876/57/6933`，`\nROLECODE:C 我准备完毕，可以开始。`）。
  - **wire 级序列** `deepseek-v4-flash → glm-5.2 → deepseek-v4-flash`（逐位与绑定一致），`wire_model_name_provenance_counts={wire_response_model:3, uuid_reverse_lookup_fallback:0}`、reconciliation `3 / 0 / 0`；`wire_and_reverse_lookup_agree=true` 逐条成立。
  - **token 对账**：`6829+136 + 6680+42 + 6876+57 = 20620 = spent_tokens`（逐条 `total==prompt+completion`）；`status=COMPLETED / committed=[a,b,c] / skipped=[]`。
  - viewer：`a=[180dc5b5…, 7ff1909a…]`；`b=[180dc5b5…, 7ff1909a…, 0a7c3c5c…]`；`c=[180dc5b5…, 0a7c3c5c…, 73318270…]`（**c 不见 a**，pipeline 只串联上一位）；测试源码 `:1645-1654` 是独立断言。
  - 测试源码 `:1569-1573` 还**显式断言**「期望的模型调用序列（按发言顺序）应为 deepseek-v4-flash → glm-5.2 → deepseek-v4-flash」——这把第八轮⑨给 C1-02 列的「序列对照」缺口补成了**断言级**证据。

**⑦ 两条真机 UI 测试（C1-10 相关；Compose 语义树断言，不是截图）**

- `typeFilterChipsFilterWithoutLosingData`：`OK (1 test)` / `Time: 4.026`；证据 `c1-round5-filter.txt`（288 B）实测内容：
  `case=direct` 下 `group_a_reachable=false / group_b_reachable=false / direct_a_reachable=true / direct_b_reachable=true`；
  `case=switch_back_all` 下 `fixture_rows_in_db=4`，DB 四行原样（`C1-R5 群聊甲=GROUP`、`C1-R5 群聊乙=GROUP`、`C1-R5 单聊甲=DIRECT`、`C1-R5 单聊乙=DIRECT`），`all_titles_reachable=true`。
- `atMentionPickerInsertsParseableMention`：`OK (1 test)` / `Time: 4.365`；证据 `c1-round5-mention.txt`（249 B）实测内容：
  `case=at_picker_open`（输入 `@`、`candidate_visible=Alice Johnson`）；`case=at_picker`：`@Alice Johnson ` → `parsed_role_ids=[alice]`、`@阿尔法 ` → `parsed_role_ids_chinese=[alpha]`、反伪 `@johnson` → `anti_spoof_at_johnson=[]`。
- `groupMemberBarRendersOneAvatarPerRole`：**本轮未重跑**（复用第十轮证据 `c1-round5-avatar.txt`，130 B：`member_bar_exists=true / expected_roles=3 / actual_clickable_avatars=3 / role_names=Alice Johnson|阿尔法|伽马`）；跑 UI 前已把旧文件另存（测试进程会清空该证据文件）。
- ⚠️ **边界**：这三条是**真机 androidTest（Compose 语义树断言）**而非截图——比纯目视强；但契约 `:206` 末句「不得仅凭 UI 截图勾选」与 `:232-235`「只看截图或只看 UI 状态均标记 `unverified`」的约束仍在（详见 ⑨ 的 C1-10 行），且它们不产出契约四类产物。

**⑧ 设备环境坑（本轮新踩两条；都写进遗留）**

1. **ColorOS 冻结后台 instrumentation**：测试启动后 instrumentation 所在 uid 被 cgroup 冻结（执行者观察到 uid 级 `frozen 1`，`am unfreeze` 无效），表现是 `awaitAssistantMessages` 一直等到 300s/798s 超时；**解法 = 测试启动约 20s 后主动 `am start RouteActivity` 把进程前台化，可解冻**（执行者报告；logcat 里可核的是系统 `am_freeze`/`am_unfreeze` 机制本身）。
2. **OPPO osense 会 SIGKILL 无可见 UI 的 FGS instrumentation**：`Cached(nirvana)` → `signal 9`（登记代理在 `logcat-after-attempt1.txt` 实测到 `am_kill … heizige.kk.khatkit.debug … Cached(nirvana)[(instrumentation)]` 与紧随的 `am_proc_died`）。这是第六个窗口 OPPO 回收策略（本文件已有一节）在**安装后未启动 Activity** 场景下的变体。
3. 设备卫生：本轮收尾已把 `screen_off_timeout` 由 600000 改回 **30000** 并复验、`svc power stayon false`（**执行者报告**；第十二轮的 C1-06 尝试又把它改回 600000 且未能恢复，见第十二轮与遗留第 39 条）。

**⑨ 判定影响：20 个状态格一个升级都没有（仍是 10/10 `unverified`）**

| 行 | 本批新增了哪类产物（真实网关） | 仍然缺哪一项（不升级的理由） |
|---|---|---|
| C1-01 | 投递收窄真机成立（只调用 b）+ viewer 台账 + token + wire 名 | 「其他角色看不到那条 @ 消息」读不出来（@ 在 USER 消息上；USER 对所有视角放行，测试把该口径写成断言）；要观测需「@ 一条角色发言」或「@ 不指代任何角色的输入」（遗留第 15 条）。另：退出码未落盘、单次通过、`:206` 五要素缺导出哈希 |
| C1-02 | **最接近**：wire 级序列（断言级）+ Σ=20620=spent + viewer 台账（断言级）+ 全绿记录 | 契约 `:206` 五要素仍缺「导出哈希」一类；`am instrument` 退出码未落盘（`:232-235` 要求「测试命令及退出码」）；单次通过、未做重复稳定性验证。第八轮⑨ 列的三项里，①「序列对照」②「viewer 独立断言」本批已补，③ 退出码仍在 |
| C1-03 | 真实网关 3 调用（a/b speaker + c chair）+ 议长放开分支 + viewer 断言 + Σ=20723=spent | 同 C1-02：缺导出哈希、退出码未落盘、单次通过；另 c 的 chair 正文不是归纳文本（机制已验、文本语义未验） |
| C1-04 | **两条路径齐全**：多数决（2:1 Decided + summary）与平票（Tie → error 节点 + `vote_no_decision`），票面/解析/tally/viewer/token 全部有值 | 同 C1-02：缺导出哈希、退出码未落盘、单次通过 |
| C1-05 | —（本批未跑预算截断） | 真实 usage 下的截断仍零份；上轮 raw dump 不是正式序列 JSON |
| C1-06 | 三次尝试（0/3）：全部不成立，四项断言零测量（详见第十二轮） | 取消/超时路径真机零份；超时半未跑 |
| C1-07 | —（本批未跑） | 同 `round_id` 重试幂等台账零份 |
| C1-08 | —（本批未跑） | 三个记忆空间检索可见性 + `source_message_id` 归因零份 |
| C1-09 | —（本批未跑；第十二轮补了真机导出，见下） | 酒馆本体 / 相机扫码 / QR 往返零份 |
| C1-10 | **两条 UI 测试真机首次通过**（筛选 chip 过滤+切回不丢、@ 选择器插入+反伪），加第十轮头像组 | 契约 `:206`/`:232-235` 不允许仅凭 UI 级证据勾选；本行契约四类产物仍零份；筛选测试非四 chip 全流程、「真的按类型过滤」的 `PagingSource` 并发/快照/回滚未验 |

⇒ **一个 `verified` 都没有**：按「判定规则」第 2 / 6 / 8 条逐行核完，没有任何一行「五类产物在本行点名路径上全齐」。⚠️ **C1-02 / C1-03 / C1-04 是本批离升级最近的三行**——真实网关下各自点名的路径已被真实调用、序列/token/viewer 都是断言级证据；它们**只差「导出哈希 + 退出码 + 单次 vs 重复」这最后一段**。⚠️ 全部通过项**没有做重复稳定性验证**，任何以「单次通过」为基础的升级都必须单独说明这一点。

### C1 真机证据采集第十二轮（2026-10-06，HEAD cddaa9959：C1-09 真机导出双哈希一致 + 四项安全检查全通过；C1-06 取消三次尝试均未成立）

⚠️⚠️ **先说性质**：本批 = **一次真机采集窗口**（设备同第十一轮：OnePlus `PKG110` / Android 16 / API `36` / `arm64-v8a`），**开工 HEAD `a8302e463`、收尾 HEAD `cddaa9959`**，窗口内落地 4 个 commit：`92af89247`（C1-09 真机导出测试，+523 行）/ `d6870f977`（C1-06 取消测试 + 慢速 mock）/ `579dc193a`（修 C1-09 测试 tearDown）/ `cddaa9959`（C1-06 原始转储 + mock 改零内容注入）。**生产代码零改动**（4 个 commit 全部只动 `app/src/androidTest` 与 `tools/verification`，登记代理逐 commit `--stat` 核过）。
⚠️ **结论**：阶段 A（C1-09 真机导出）✅ 完成；阶段 B（C1-06 取消）❌ **三次尝试全部不成立、零测量**；阶段 C（稳定性复跑）❌ 未跑（设备 transport 丢失）。**20 个状态格仍一个升级都没有**（逐行理由见第十一轮 ⑨ 与本节末尾）。⚠️ **所有通过项均为单次通过，未做重复稳定性验证。**

**① 阶段 A：C1-09 真机导出（✅ 完成）**

- 新测试：`C1GroupExportDeviceEvidenceTest#deviceExportedGroupJsonlHashesMatchAndCarryNoSecrets`
  （新文件 `app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1GroupExportDeviceEvidenceTest.kt`，`92af89247` 落地）；
  日志 `phaseA-export.log`：`Time: 0.382` / `OK (1 test)`。
- 流程（全部走**生产**代码）：真 SQLite 落库 → 读回 **7 条消息**（3 角色 / 2 轮 / 五种 `turn_kind` 全覆盖）→ **生产** `TavernChatCodec.exportGroupJsonl` → **生产 IO 助手** `writeExportTempFile`（`ConversationExport.kt:836`，与 `GroupExportCard.kt:201` 同一份实现）写真实文件并拿到 FileProvider URI → 同字节复制到 external files dir → 设备侧 `java.security.MessageDigest` 算哈希 + 四项安全检查。
- 设备侧路径：`/storage/emulated/0/Android/data/heizige.kk.khatkit.debug/files/c1-device-export/c1-device-export-pipeline.jsonl`；
  生产 IO 真文件：`/data/user/0/heizige.kk.khatkit.debug/cache/temp/c1-device-export-pipeline.jsonl`，URI `content://heizige.kk.khatkit.debug.fileprovider/cache/temp/c1-device-export-pipeline.jsonl`。
- **双哈希一致**：`bytes=2938`；设备侧 `adb shell sha256sum` = **`4945b85426def123b7bc07382dbf03977c35908650a114ffac7cf782f9976f4b`**；本机 pull 后 `sha256sum`（登记代理复算 `phaseA-pull/c1-device-export-pipeline.jsonl`）= **`4945b85426def123b7bc07382dbf03977c35908650a114ffac7cf782f9976f4b`**（两值一致；报告文件里 `sha256_of_exported_bytes_device`、`production_io_file.sha256_device`、`pull_file.sha256_device` 三处同值）。
- **内容**：8 行（1 表头 + 7 消息）；表头 `spec=st_chat_v1`、`chat_metadata.is_group=true`、`khatkit_group`（3 角色 config + 3 张角色卡，`vote_candidates=[]`、`tie_policy=fail`、`token_budget_per_round=4096`）；5 种 `turn_kind` 齐全（user / speaker ×2 / chair / vote_summary / error；两个 `user` 分属 r1/r2 轮）。⚠️ **带 Tool/Reasoning part 的那条消息导出 `mes` 只有 Text**（工具 part 与推理正文整体消失——按设计不导出）。
- **四项安全检查全 `true`**（且用金丝雀先证实真的落进了真机 store，`canaries_live_in_stores` 四项全 `true`）：
  1. **provider API key 不入包**：种入 `sk-c1canary-…`，全文无该串、无任何 `sk-` 长串（`no_provider_api_key_sk=true`）；
  2. **隐私记忆不入包**：用生产 `MemoryRepository` 种入 canary 记忆，导出无正文（`no_memory_canary=true`）；
  3. **工具授权 token 不入包**：MCP `Authorization` 头 + OAuth `accessToken` canary 均无（`no_tool_authorization_token=true`）；消息内 Tool.input 的 token / Tool.output + Reasoning 的记忆正文也无；
  4. **生产黑名单扫描逐行** `GroupChat.findForbiddenKeys`：**37 个禁止键 0 命中**（`blacklist_scan_hit_count=0 / blacklist_keys_scanned=37`）。
  另：`tool_part_not_exported = {tool_name: c1_canary_tool_never_exported, input_token: c1-canary-tool-authorization-token-9e7c4a2d, absent: true}`。
- 证据报告 `phaseA-pull/c1-device-export-report.json`：5337 B / `eaaa1baf0e4dcdbcdea72fc20fc2b92e262afcd1bcbc2cbff9eb4b9d39991fc0`（含上述全部字段与导出全文，登记代理复算一致）。
- ⚠️ **未覆盖（必须连着上面一起读）**：`ACTION_SEND` 系统分享面板、SillyTavern 本体打开、相机扫码**三项都没有做**；也没有导回/导入往返。⇒ C1-09 仍 `unverified`。

**② 阶段 B：C1-06 取消（❌ 未验证；3 次尝试全部不成立）**

- 生产取消入口的真实函数名（登记代理读码核实）：**`ChatManager.stopGeneration(conversationId: Uuid)`**（`app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatManager.kt:2277`，`suspend`；内部 `cancelJobs` → `cancelActiveRound` → `cancelRound` 写 `CANCELLED`/`cancelled`）。**未改生产代码。**
- 新测试：`C1GroupCancelDeviceTest#cancelMidStreamKeepsGeneratedMessagesAndErrorNodeWithoutEmptyBubbles`（两轮设计：第一轮造生产错误节点、第二轮边流式边取消）；配套慢速 mock `tools/verification/mock_openai_slow_cancel.py`（已入库，sha256 `0f2af473f94931cf97843fee1d7ea41462b4dc94cfecfe4ab10bfd286d0d3fde`）。
- **attempt 1**（`phaseB-cancel-attempt1.log`，`Time: 7.903`）：测试在第 1 轮错误节点断言失败——
  `java.lang.AssertionError: 第一轮必须由生产失败路径写出 b 的错误节点，实际回合消息=[a:speaker:[mock-slow] CASE:cancel ROLECO, b:speaker:\nB 已收到，本轮取消用例正常执行，无异常。, c:speaker:[mock-slow] CASE:cancel ROLECO]`（`:239`）。
  ⚠️ **异常现象如实登记（可能是 mock 或生产的真问题）**：mock 对角色 B 首个请求**注入了 HTTP 500**（服务端日志 `mock-requests-attempt1.jsonl` seq2：`injected_http_500=true`、无 `reply_bytes`/`usage`——即服务端确实返回了 500），但整轮以 **`COMPLETED`** 收尾（`trace-phaseB-attempt1.txt`：`round1:terminal status=COMPLETED reason=`），库内 B 是一条 **speaker** 消息、正文 `"B 已收到，本轮取消用例正常执行，无异常。"`，C 也被调用（mock seq3 有 C 的请求与 usage）；**mock 未收到第二次 B 请求**（seq 只有 1/2/3）。
  ⚠️ **该文本来源不明**：登记代理在四处实测搜索均 0 命中——仓库源码（`grep -rn 取消用例正常执行 --include=*.kt/…`）、两份 mock 服务端日志、生产库快照 `proddb/rikka_hub`（含 `ConversationEntity.nodes` 与 `message_node`）、attempt 2 遗留库 `attempt2-db/c1-cancel-evidence.db`。⇒「HTTP 500 注入 + `COMPLETED` + 无法溯源的 B 文本」三者组合**原因未定**，已单独登记为遗留第 40 条（**取消根本没执行到**）。
- **attempt 2**（`phaseB-cancel-attempt2.log` 只有类名、`trace-phaseB-attempt2.txt` 三条：setUp:enter / chatManager-constructed 后无 round 终态）：设备在 round 1 中途掉线（`mock-requests-attempt2.jsonl` 只收到 A + B 两个请求，A 有回复、B 注入 500，此后无 C），作废。
- **attempt 3**（`phaseB-cancel-attempt3.log` **0 字节**、mock 零请求）：设备在 `am instrument` 生效前掉线，未运行。
- ⚠️ **四项断言（空气泡数 / 已生成消息 / 错误节点 / `spent_tokens` / `status` / `reason`）一条都没有实测值**（命中 0 次、未命中 3 次）。
- ⚠️ **超时那半未验**：`GROUP_ROUND_STEP_TIMEOUT_MS` 固定 15 分钟，跑不起。
- 设备遗留：`attempt2-db/c1-cancel-evidence.db`（attempt 2 快照，`message_node` 2 行、`group_runs` 0 行；含 conversation `0c3907e5-…` 与 role a 的 `[mock-slow]` 消息）**留在设备 app databases 未删**。

**③ 阶段 C：稳定性复跑（❌ 未跑）**

设备 transport 丢失（`adb devices` 不可达），**4 条真实网关用例一条都没跑**——第十一轮的三条新用例与 pipeline 复跑均**没有做第二次运行**。

**④ 设备遗留（transport 丢失无法收尾）**

- `screen_off_timeout` 现为 **600000**（第十一轮收尾曾改回 30000，第十二轮 C1-06 尝试又调高，收尾时设备已不可达**未改回**）；`svc power stayon` 仍 `true`；设备 app databases 里 `c1-cancel-evidence.db` 未删。⇒ 下一轮接上设备**第一件事**先恢复 `screen_off_timeout=30000` + `stayon false` + 清该库（遗留第 39 条）。
### C1 真机证据采集第十三轮（2026-10-06，HEAD 18d730465：C1-06 取消路径首次真机通过 + 真实网关 5 条稳定性复跑 + 两项关键发现）

🚨🚨 **先说性质**：本批 = **一次真机采集窗口**（设备同第十一轮：OnePlus `PKG110` / Android 16 / API `36` / `arm64-v8a`，无线 serial `192.168.31.183:39345`——两条采集脚本 `p2-run.sh` / `p2-run-v2.sh:4` 实测），**开工 HEAD `cddaa9959`、收尾 HEAD `18d730465`**，窗口内落地 2 个 commit：`610edbf86`（mock 工具）/ `18d730465`（取消测试）；**生产代码零改动**（登记代理逐 commit `git show --stat` 核过：只动 `tools/verification/mock_openai_slow_cancel.py` 与 `app/src/androidTest/.../C1GroupCancelDeviceTest.kt`）。另补登上一批（第十五批）两个登记本体 `e9091ffec` / `bfe1c982c`（均只改 `docs/`）。
🚨 **结论**：① C1-06 取消路径 ✅ **首次真机通过**（首次拿到全部断言实测值）；② 真实网关 5 条 × 3 次稳定性复跑 ✅ 完成（mention 3/3、vote 3/3、vote-tie 3/3、roundtable 1/3（另 2 次为测试竞态）、pipeline 0/3 + 补测 1/3）；③ **两项关键发现**（`INSTRUMENT_EXIT` 不可当判据 / 前台化时机）；④ 设备清理 ✅。**20 个状态格仍一个升级都没有**（逐行理由见下方 ⑤；判定取保守口径）。

**① C1-06 取消路径首次真机通过（本批最重要）**

- 方法：`C1GroupCancelDeviceTest#cancelMidStreamKeepsGeneratedMessagesAndErrorNodeWithoutEmptyBubbles`；日志（`r8a1/c1-cancel-instrument-r8a1.log`，SHA `e7169883…`）：`Time: 2.246` / `OK (1 test)` / `numtests=1`；`exitcode.txt` = `INSTRUMENT_EXIT=0`（⚠️ 不能当判据，见 ③）。
- 证据报告 JSON `r8a1/c1-device-cancel-report-r8a1.json`（4852 B，SHA `c5225560…`）四组实测值（登记代理逐字段读取）：
  1. **空气泡数 = 0**（`observed.empty_bubble_count=0`）。
  2. **保留消息**：第二轮 a 完整消息 id `95f302c5-6703-449f-a590-7f8826ca7769`，modelId `0c1c06ca-…000a`，wire `mock-cancel-a`，正文 `[mock-slow] CASE:cancel ROLECODE:A 第3次请求，正在流式产出用于取消用例。`，usage `6236+22=6258`；被取消 b 的半截产出 id `5f97ad23-6aab-4296-a50b-a608f428710c`，modelId `…000b`，`roleId/roundId=null`（`stampGroupTurn` 只在成功提交时盖章），正文 `[mock-slow] CASE:cancel `（**恰 24 字符 = 1 个 chunk**；mock 服务端该请求共切 19 块，其余 18 块随取消丢失）。
  3. **错误节点**：id `7e91c213-d1eb-4877-8a8b-ca33142fb896`，roleId=b，turnKind=**error**，正文 `[角色乙] 本轮生成失败：本轮没有产出内容`，取消后仍在（`error_node_from_round1_still_present=true`）。
  4. **`group_runs`（第二轮）**：status=**`CANCELLED`**｜reason=**`cancelled`**｜spent_tokens=**6258**（== a 的 6236+22）｜token_limit=100000｜committed_role_ids=**`["a"]`**｜skipped_role_ids=`[]`｜round_id=`round-7adcae6a-9861-4690-9a57-17dc52604761`。第一轮 run：`FAILED / role_failed / 6212 / committed=["a"] / skipped=["c"]`。
  5. `b2_partial_message_count=1`（`b2.isNotEmpty()` 强断言通过）。
  6. **`realGatewayLeaks` 守卫未触发**：mock `requests.jsonl` 4 条请求全部 `mock-cancel-a/b`（seq1 A、seq2 B 零内容注入 http 200 `content chunks=0`、seq3 A、seq4 B 慢速流 19 块 0.5s/块）；raw dump 六条消息的 modelId/wire 名均为 mock 占位值。
- 原始转储与 trace：`c1-cancel-raw-final-r8a1.json`（3924 B，SHA `d70c6379…`）、`c1-cancel-raw-round1-r8a1.json`（2111 B，SHA `4825b59a…`）、`c1-cancel-trace.txt`（SHA `aa773b64…`，含 `round2:stop-returned after 45ms (b streaming observed 5ms before stop)`）；`exitcode.txt` SHA `1f2d78ea…`。六份 SHA 登记代理逐份复算一致。

**② 为什么之前三次都没成立（根因）；含一个真发现**

1. **第一轮前提不可达（最致命）**：`SettingsRepository.kt:349-352`（真实路径 `core/data/datastore/`）读取时对缺 id 的 provider 逐个补回 `DEFAULT_PROVIDERS`；内置「极客猫」（`DefaultProviders.kt:283-318`）`enabled=true` + 硬编码 key + **唯一带 `models` 的默认 provider**（登记代理逐 provider 实测：默认表里只有它有 `models`）⇒ 测试写 `providers=[mock]` 读回来必然是 `[mock]+全部内置`。mock 返回真实 HTTP 500，而 `ProviderFailover.isEligible`（`ProviderFailover.kt:18-26` 关键字表含 **`"500"`**、`:44-48`）判为可切换，`hasNext`（`GenerationLoop.kt:164-166`，只看 `enableAutoRetry`）为真 ⇒ **B 的请求被重放给极客猫**，真模型回了 `"\nB 已收到，本轮取消用例正常执行，无异常。"` ⇒ B 算成功、轮次 `COMPLETED`。**讽刺点：恰恰是 mock 错误消息里的 `"500"` 让它可切换**——这也是第十二轮遗留第 40 条「无法溯源文本」的答案。修复（`18d730465`）：`cancelSettings()` 加 `networkSetting = NetworkSetting(enableAutoRetry = false)`，同时关内层重试与外层 failover；并新增 mock 来源守卫（`modelId ∈ {mock A/B/C}` 且 `wireModelName ∈ {mock-cancel-a/b/c}`）。
2. **第二轮取消窗口两处竞态**：只等 a 落库就 `stopGeneration`，但 B 的生成可能还没开始（B 由 `handleMessageComplete` 发起）；卡缝隙则走 `jobs.isEmpty()` 分支只写 `CANCELLED` **并没真取消**。另一条：`commitGroupTurn` 先存消息后写 `group_runs` ⇒ `spent` 断言可能偶发为 0。修复：第二轮改等两个正向信号（run2 行 `committedRoleIds.contains("a") && spentTokens>0` **且** 内存态出现 `modelId==modelBId` 且正文非空的助手消息）再停；证据新增 `round2_a_committed_observed` / `round2_b_stream_observed` / `b_stream_observed_to_stop_called`（实测 **5ms**）。
3. **断言 2 是空断言**：被取消的 b **永远不会被盖章**（`stampGroupTurn` 只在成功提交时调用）⇒ `roleId/roundId` 恒 null；旧过滤 `roundId==round2Id && roleId=="b"` ⇒ **永远 0 命中**，`b2.all{...}` 恒真。修复：`b2` 改按 `cancelledRolePartials(finalAssistants, modelBId)`（按 modelId 认领）并加 `b2.isNotEmpty()`。
4. **mock 侧四个问题**（`610edbf86` 修）：`failed_once` check-then-add 在锁外、无 reset、日志只记意图不记实际 `http_status`/`body_summary`、所谓「24-byte」实为按**字符**切片、「零内容」实际发一个 `{"content":""}` 块。修后：`/__reset`（清 `failed_once`）、锁内一次性 `claim_injection()`、实际 status/body 入账、`split_chunks()`、「零内容」= 真 0 个 content 事件（去掉 `or [""]`）。**新 sha256 = `0f2af473f94931cf97843fee1d7ea41462b4dc94cfecfe4ab10bfd286d0d3fde`**（旧 `8af6e13d…` 已过期，本文件与实施状态文件已订正）。

**③ 非空验证（JVM 用真实 `UIMessage` 类）**

`/tmp/opencode/c1-cancel-mocktest/FilterProof.kt`（kotlinc 2.3.21 + `ai` 模块真实编译产物）构造五种消息（a 已提交 / b 半截 / 第一轮错误节点 / 真网关顶包回复 / user），**登记代理本轮亲自重跑（`java -cp …`，exit 0）**，输出：

```
old_filter_matches=1 -> [真网关顶包的 B 回复]
new_filter_matches=1 -> [[mock-slow] CASE:cancel ROLECODE:B 半截产出]
head_shape old_b2_size=0 old_assert_holds_vacuously=true
FILTER-PROOF OK
```

⚠️ **必须写清的诚实点**：这是**JVM 侧用真实类做的判据级证明**，**不是仪器运行时执行 androidTest**——它证明「旧断言在真机现场必然空转、新断言能命中半截产出」，不能替代真机运行证据。

**④ 真实网关 5 条 × 3 稳定性复跑**

⚠️ **发现 A（影响全项目取证方法）：`am instrument` 的 shell 退出码不能当判据。** 全部 19 个采集 run（含 FAIL）设备侧 `INSTRUMENT_EXIT` **一律 0**（`exitcode.txt` 逐份实测）；且 `INSTRUMENTATION_CODE: -1` 在通过（vt-run1 等）与失败（rp-run1）日志里**也完全相同**（`Activity.RESULT_OK` 的 shell 约定）⇒ 判据只能是日志正文里的 `OK (1 test)` / `FAILURES!!!`（`Tests run: 1, Failures: 1`）。见遗留第 41 条。

⚠️ **发现 B（执行者推断，非直接证据）：前台化时机是关键变量。** 按「启动后 ~20s 才 `am start`」（`p2-run.sh:15-20`，`i%6==2`）跑 pipeline 连续 3 次全灭（角色 a 零内容）；改成**启动后 4s 即前台化、之后每 10s 反复设置**（`p2-run-v2.sh:14-22`）后，执行者报告「后续 10 个真网关 run 里 9 个有真实产出」——**这是推断，不是受控实验**；登记代理独立可核的是：rp2/rt/mn/vt/vtt 共 15 个 run 的 raw/正式证据里，13 个所有角色都有真实产出（除外 rp2-run1/run2 的角色 c 零内容），两条脚本的差异是实打实的。

| 方法 | run1 | run2 | run3 |
|---|---|---|---|
| `realProviderRoundRecordsGenuineTokenUsage` | FAIL 301.474s | FAIL 301.415s | FAIL 301.582s |
| `realProviderRoundtableRecordsChairSummaryCallSequence` | FAIL 17.516s | FAIL 13.476s | **PASS 18.95s** |
| `realProviderMentionNarrowsSpeakersToMentionedRole` | **PASS 6.674s** | **PASS 7.707s** | **PASS 8.249s** |
| `realProviderVoteRoundRecordsBallotCallsAndDecision` | **PASS 11.137s** | **PASS 12.434s** | **PASS 10.859s** |
| `realProviderVoteTieFailsPerConfiguredPolicy` | **PASS 11.256s** | **PASS 14.35s** | **PASS 18.165s** |

- 通过项证据 SHA（登记代理逐份复算）：roundtable `d9473daf…`；mention `bb4007c4…`/`62c777e3…`/`dffe2f8a…`；vote `5c17960a…`/`13144d44…`/`3421fff8…`；vote-tie `a7a20299…`/`e4d67be6…`/`ff9139f6…`；rp2-run3 `f98f053d…`（内容：run COMPLETED、spent 20687、序列 deepseek-v4-flash→glm-5.2→deepseek-v4-flash、`wire_response_model 3 / fallback 0`）。全部 11 份日志都有 `OK (1 test)`。
- rp×3 失败原文（三份日志一致）：`AssertionError: 等待助手消息超时（300000ms）：期望 3 条，实际 1 条。最后看到的消息=[USER/null, ASSISTANT/a]；app 错误=[]`；raw dump 里角色 a 是 `turn_kind=error`「本轮没有产出内容」，run=FAILED。
- rt run1/run2 失败原文：`roundtable 必须按名单顺序产出 a、b、议长 c 三条发言，实际=[USER/null, ASSISTANT/a, ASSISTANT/b, ASSISTANT/null] expected:<[a, b, c]> but was:<[a, b, null]>` —— **测试自身竞态（发现）**：`awaitAssistantMessages` 只等条数，「先落库后盖章」的议长消息被快照到 null；同轮 finally 的 raw dump 显示 `run=COMPLETED / committed=[a,b,c]`（spent 20708/20781），**产品侧正常**。⇒ **这是测试 bug，另一个代理正在修**（遗留第 42 条）。
- **补充测量（超出基础 3 次，明确标注）**：pipeline 又跑 3 次（tag `rp2`）：FAIL 16.653s（角色 c 零内容）、FAIL 15.055s（角色 c 零内容）、**PASS 18.265s**（`f98f053d…`）。合并两组 6 跑 1 通过。
- 独立对账（执行者报告；登记代理未复现其主机探针）：主机 `c1_wire_model_probe.py` 两模型 HTTP 200 有正文；设备侧 curl 复刻 app 请求（含 `stream_options`）正文正常（`device-sse-full2.txt` 内含完整 `content` 帧与 `[DONE]`）⇒ 网关/设备侧健康，pipeline 的零内容是 **app 流式路径的间歇行为**。

**⑤ 本轮判定小结（20 格）**

- C1-01：重复稳定性首次满足（3/3），但「其他角色不可见」字面半 + 导出哈希一格仍缺 ⇒ 不升。
- C1-02：有通过记录 + 明确间歇失败（角色 a/c 零内容）⇒ 不升。
- C1-03：2 次 FAIL 归因测试竞态（修复中、未验证）+ 议长正文语义未验 + 导出哈希一格 ⇒ 不升。
- C1-04：两条路径 3/3 且稳定，仅「导出哈希」一格（本例结构性不产出导出文件）⇒ 按判定规则第 2 条不升。
- C1-05：未跑，且发现正式证据结构性不可达 ⇒ 不升。
- C1-06：取消路径首次完整通过，但超时半未跑 / viewer 集合未按契约落盘 / 导出哈希一格 / 单次通过 ⇒ 不升。
- C1-07 / C1-08：本批未跑 ⇒ 不升。C1-09 / C1-10：本批未新增 ⇒ 不升。
- 结论：**20 个状态格仍 10/10 `unverified`，只在状态格里就地追加订正（保留原文）**。

**⑥ 设备清理（执行者报告；登记代理以 pull 回本机的库副本独立复核了括号中项）**

- 遗留 `c1-cancel-evidence.db`：**不存在**（tearDown 的 `deleteDatabase` 已删）。
- **`rikka_hub` 两行 RUNNING 已清**：备份 `/tmp/opencode/c1-device-r8/p3-backup/`（MANIFEST.sha256 登记：main `c33e8dae…`、wal `a7809e3b…`、shm `15018d54…`）→ 本机副本 DELETE（`del1_changes=1 / del2_changes=1`）→ `PRAGMA integrity_check=ok` → `wal_checkpoint(TRUNCATE)` → 回推。复验设备库 sha256 **`40d2eb76dc74d9573f2532c39ae04487eef69f5d124f8a9401932042ba9c3b45`**（登记代理对 `p3-verify`/`p3-verify2` 两份 pull 副本复算一致），总行数 **7→5、RUNNING=0**（登记代理逐行对比：保留的 5 行与备份中非 RUNNING 的 5 行逐字段相同）；启动 app 烟测无 DB 报错。
- 收尾还原：`screen_off_timeout` **600000 → 30000**（已复验）、`stayon=false`；`adb reverse --remove-all`；自起的 8766 mock 已停（登记代理本机 `pgrep -f mock_openai_slow` 0 命中、8766 无监听）。
- ⚠️ **登记代理操作透明说明**：为读备份库，登记代理曾用 `sqlite3`（非 immutable）打开 `p3-backup/rikka_hub`，触发了一次**隐式 WAL 恢复**——备份三件套被合并成单一文件（现 sha256 `6466d950…` / 150528 B），`p3-verify2/rikka_hub-wal`（0 B 空文件）被连带清理。**逻辑内容不变**（恢复 = 应用已提交 WAL 帧），MANIFEST.sha256 记录的是恢复前原值；此处如实登记，避免下一位复核时对不上。本报告引用的其余证据文件均未被触碰（SHA 为登记时复算值）。

### C1 真机证据采集第十四轮（2026-10-06，HEAD b60aa80c5：显式@/vote 多数决/vote 平票/预算截断/记忆隔离 五条首次带完整证据通过 + roundtable/pipeline 第 3 位角色零产出根因）

🚨🚨 **先说性质**：本批 = **一次真机采集窗口**（设备 OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`；`fingerprint=OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`；包 `heizige.kk.khatkit.debug`；证据库 `c1-live-evidence.db`——均为逐份 JSON `device` 字段实测值），**开工与收尾 HEAD 均为 `b60aa80c5`**（`git rev-parse HEAD` 实测）。窗口内 3 个 commit 全部只动 `app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`：`a9df57c96`（真实网关逐例导出 SHA-256 + viewer 可见台账 + 修 roundtable 盖章竞态 + 新增预算截断专用用例）、`b1473f5d1`（新增 C1-08 记忆隔离设备侧用例）、`b60aa80c5`（预算截断用例补库内助手发言数 ==2 直读断言）——**生产代码零改动**（逐 commit `git show --stat` 核实）。另补登上一批（第十六批）两个登记本体 `165d30b5a` / `a8449768c`（均只改 `docs/`）。全部原始证据文件的 SHA-256 由**登记子代理本机复算**（见 ⑤）。

🚨 **结论**：① **5 条首次带完整证据通过**（显式@ / vote 多数决 / vote 平票 / 预算截断 / 记忆隔离）；② **2 条 3/3 全失败**（roundtable / pipeline），根因是**第 3 位角色零产出**（网关 HTTP 200 但 SSE 流里没有任何 content delta），**6/6 稳定复现 ⇒ 不是偶发**；③ **状态判定：C1-04 与 C1-05 首次升 `verified`**（契约 8 项齐备且必须断言成立），其余 8 例保持 `unverified`（逐行理由见 ⑦）。

⚠️ **命令模板**（执行者用，逐 run 单跑）：
`adb -s <serial> shell am instrument -w -r -e class 'heizige.kk.khatkit.app.feature.chat.C1LiveModelSequenceTest#<方法>' heizige.kk.khatkit.debug.test/androidx.test.runner.AndroidJUnitRunner`
⚠️ **`am instrument` 的 shell 退出码即使失败也是 0**（遗留第 41 条），**判据只用日志正文的 `OK (1 test)` / `FAILURES!!!`**。本批 `exitcode.txt`（vote / vote-tie / budget / memory 四个 run）= **`0`**；mention run 采于旧脚本、**未落 `exitcode.txt`**（只有 `instrument.log` 的 `OK (1 test)` / `Time: 6.567`）。

**① 通过的 5 条（每条逐列 8 项契约字段：commit / 命令·退出码 / 设备·Android / 用例输入 / viewer 可见消息 ID / 模型调用序列 / prompt+completion token / 导出 SHA-256）**

> ⚠️ 8 项 = 契约 `client-changes.md:206` + `:232-235` 逐例要求的「commit、测试命令及退出码、设备/Android 版本、用例输入、各 viewer 的可见消息 ID、实际模型调用序列、prompt+completion token、导出 SHA-256」。**缺项在下面逐条明写「缺 X」。**

**#3 显式 @（C1-01）——`realProviderMentionNarrowsSpeakersToMentionedRole`，`OK (1 test)`，Time 6.567**

- commit：`a9df57c96`（本用例所在文件已含逐例导出；HEAD `b60aa80c5`）。
- 命令·退出码：上面的命令模板；shell 退出码**未落盘**（旧脚本），日志判据 `OK (1 test)`。
- 设备·Android：OnePlus `PKG110` / Android 16 / API `36` / `arm64-v8a`。
- 用例输入：`mode=pipeline`，`conversation=c78461b4-277e-48cf-9908-a3f685ccc0f5`，USER 消息 `@角色乙 请只由你发言一次。`（`mention_role_ids=["b"]`，`trigger_mention_role_ids=["b"]`，`plan_selected_role_ids=["b"]`）。
- viewer 可见消息 ID：`a`=`[仅 USER 23043da1-efd2-4cdf-aba3-07f221f3bb8f]`、`b`=`[USER + 自己的回复 0cae6bb4-7f26-497c-a68e-e63f6f585b58]`、`c`=`[仅 USER]`；`viewer_visibility` 的 `visible_assistant_role_ids`：a=`[]`、b=`["b"]`、c=`[]`。
- 模型调用序列：`actual_model_call_sequence=[{seq:1, role_id:b, turn_kind:speaker, resolved_model_name:glm-5.2, provenance:wire_response_model, usage 6546/98/6644, cached_tokens:384}]`；`only_mentioned_role_invoked=true`、`expected=["glm-5.2"]`。
- prompt+completion token：`6546+98=6644`；`spent_tokens=6644==sum`（`spent_tokens_equals_sum_prompt_plus_completion=true`）；`group_run=COMPLETED/spent 6644/limit 100000/committed=["b"]`。
- 导出 SHA-256：`c1-export-real-mention.jsonl` **1747 B / 3 行**，`6e2ec8ceff572bf2ba3af0376907347ebed83768cd7ac0d08293bcb6dff7fcdd`。
- 证据文件：`c1-live-evidence-real-mention.json`（10270 B，`56efdb48298ab1c6d24401edbaf9f0d2081ece52bc1c56dbf4b3af01d36aea0a`）；`wire_model_name_provenance_counts={wire_response_model:1, uuid_reverse_lookup_fallback:0}`。

**#4 vote 多数决（C1-04）——`realProviderVoteRoundRecordsBallotCallsAndDecision`，`OK (1 test)`，Time 17.917**

- commit：`a9df57c96`（HEAD `b60aa80c5`）。
- 命令·退出码：命令模板；`exitcode.txt=0`；日志 `OK (1 test)`。
- 设备·Android：同上（`PKG110` / 16 / 36 / arm64）。
- 用例输入：`mode=vote`，`tie_policy=fail`，`vote_candidates=["opt-a","opt-b","opt-c"]`，USER `请三位各投一票，选出你支持的方案。`。
- viewer 可见消息 ID：`a`/`b`/`c` **各见自己那条发言 + `__summary__` 汇总**（`visible_assistant_role_ids`：a=`["a","__summary__"]`、b=`["b","__summary__"]`、c=`["c","__summary__"]`；各 `visible_count=3`）。
- 模型调用序列：`a=deepseek-v4-flash 6827/50=6877`、`b=glm-5.2 6545/64=6609`、`c=deepseek-v4-flash 6827/55=6882`；`provenance={wire:3, fallback:0}`。
- prompt+completion token：`Σ=20368==spent_tokens`；`group_run=COMPLETED/spent 20368/limit 100000/committed=["a","b","c"]`。
- 导出 SHA-256：`c1-export-real-vote.jsonl` **2672 B / 6 行**，`8e42c5b90b858fe7b2b0aa58f51d12caa653a40c4693efef7d66d943c205f5de`。
- 结构化票 / 判定（必须断言核心）：`ballots_parsed_by_production_code`（生产 `GroupChat.parseBallot` 读库回读）a=`opt-a`、b=`opt-a`、c=`opt-b`；`tally={winner:opt-a, counts:{opt-a:2, opt-b:1}, outcome_type:VoteOutcome.Decided, tie_branch_taken:false}`；`summary_node.role_id=__summary__/turn_kind=vote_summary/has_usage=false/正文「本轮投票结果：opt-a\n票数：opt-a 2 / opt-b 1」`。
- 证据文件：`c1-live-evidence-real-vote.json`（14744 B，`efe59105dd57e0fb02bba03b916c7f2f1ef1f4b5e084da132cc9a7d8cddedc73`）。

**#5 vote 平票（C1-04）——`realProviderVoteTieFailsPerConfiguredPolicy`，`OK (1 test)`，Time 16.506**

- commit：`a9df57c96`（HEAD `b60aa80c5`）。
- 命令·退出码：命令模板；`exitcode.txt=0`；日志 `OK (1 test)`。
- 设备·Android：同上。
- 用例输入：`mode=vote`，`tie_policy=fail`，`vote_candidates=["opt-a","opt-b","opt-c"]`，USER 同上。
- viewer 可见消息 ID：a/b/c 各见自己那条 + `__summary__`（同 #4 形状）。
- 模型调用序列：`a=deepseek 6827/13`、`b=glm 6545/44`、`c=deepseek 6827/46`；`provenance={wire:3, fallback:0}`。
- prompt+completion token：`a=6840`、`b=6589`、`c=6873`，`Σ=20302==spent_tokens`；`group_run=FAILED/spent 20302/limit 100000/reason=vote_no_decision/error_message=平票：opt-a, opt-b, opt-c/committed=["a","b","c"]`。
- 导出 SHA-256：`c1-export-real-vote-tie.jsonl` **2695 B / 6 行**，`65f766083cadc4be789b4c90bb5b35fe81c7a3e1e5d20bea7649c9b94e1647a5`。
- 平票失败语义（必须断言核心）：`ballots` a=`opt-a`、b=`opt-b`、c=`opt-c`；`tally={outcome_type:VoteOutcome.Tie, tie_branch_taken:true, tied_candidates:[opt-a,opt-b,opt-c], counts:{opt-a:1,opt-b:1,opt-c:1}}`；`failure_node={role_id:__summary__, turn_kind:error, 正文「[投票] 本轮未能得出结论：平票：opt-a, opt-b, opt-c」}`；**不写 `vote_summary`**。
- 证据文件：`c1-live-evidence-real-vote-tie.json`（15019 B，`d295aac9030342a0120801b1a9d1ac61c6ac47ee2478b292abc30f51627c5373`）。

**#6 预算截断（C1-05）——`realProviderBudgetTruncationRecordsRunLogAndExport`，`OK (1 test)`，Time 17.793**

- commit：`a9df57c96`（新增专用用例）+ `b60aa80c5`（补库内助手发言数 ==2 直读断言）（HEAD `b60aa80c5`）。
- 命令·退出码：命令模板 + 注入 `-e c1TokenBudgetPerRound 9000`（`budget_default=9000`、`budget_override_arg=c1TokenBudgetPerRound`、`budget_effective=9000`）；`exitcode.txt=0`；日志 `OK (1 test)`。
- 设备·Android：同上。
- 用例输入：`mode=pipeline`，USER `请三位依次发言，每位一句话。`，预算上限 `9000`。
- viewer 可见消息 ID：`a`=`["a"]`、`b`=`["a","b"]`（predecessor=a）、`c`=`["b"]`（predecessor=b）。
- 模型调用序列：`a=deepseek-v4-flash 6829/105=6934`、`b=glm-5.2 6648/12=6660`（`c` 被跳过，**无第 3 次调用**）；`provenance={wire:2, fallback:0}`。
- prompt+completion token：`Σ=13594==spent_tokens`（`spent_tokens_equals_sum_prompt_plus_completion=true`）。
- 导出 SHA-256：`c1-export-real-budget.jsonl` **2044 B / 4 行**，`da0f81c534451198e238f19969074df22a17ecb7ee8094a172f65a8f3dedd544`。
- 运行日志（必须断言核心）：`group_run=BUDGET_STOPPED/spent_tokens=13594/token_limit=9000/reason=token_budget_exceeded/committed_role_ids=["a","b"]/skipped_role_ids=["c"]`；**库内直读 `messages.count=3`（USER+a+b）、`assistant_role_order=["a","b"]`**；`c1_05_dedicated_test=true`（`why_dedicated_test`：原 `realProviderRoundRecordsGenuineTokenUsage` 断言 3 条助手消息，截断只产 2 条 ⇒ 专用方法用 2 条）。
- 证据文件：`c1-live-evidence-real-budget.json`（11628 B，`126e6b27cb52038ee236463d4711f0e00367558b6b2d1f68c643584b8eda3175`）。

**#7 记忆隔离（C1-08）——`realProviderGroupMemoryIsolationRecordsPerSpaceHits`，`OK (1 test)`，Time 0.647**

- commit：`b1473f5d1`（新增 C1-08 设备侧用例）（HEAD `b60aa80c5`）。
- 命令·退出码：命令模板；`exitcode.txt=0`；日志 `OK (1 test)`。
- 设备·Android：同上。
- 用例输入：静态 fixture 注入真 Room 库（非模型输出），`round_id=round-0c1c11ae-0000-0000-0000-00000000d008`；触发 USER id `…d008`，三角色消息 id `…a008` / `…b008` / `…c008`。
- viewer 可见消息 ID：三空间 `group:<conversationId>:role:<roleId>`（`space_key_resolver=MemoryToolScopeResolver.forGroupChat`），**每空间命中 1 条**，命中项带 `role_id` 与 `source_message_id`（a→`…a008`、b→`…b008`、c→`…c008`）。
- 模型调用序列：**缺——本例不走网关**（`evidence_kind=device-instrumentation-memory-isolation-no-gateway`；提取用 canned 确定性 JSON fact array）。写入走生产 `MemoryExtractor.parseAndStore`，检索走生产 `MemoryRepository.searchHybridInSpace`，viewer 重过滤走生产 `GroupTurnCoordinator.memoriesForViewer`。
- prompt+completion token：**缺——同上，无网关调用、无 usage**。
- 导出 SHA-256：**缺——本例设计上不产出群导出**（无 `export_*` 字段）。
- 隔离证据（必须断言核心）：`cross_space_search_negative` 三角色对彼此内容的 `foreign_query_hit_counts` **全 `[0,0]`**；`viewer_refilter_negative`：a 的记忆 `visible_to_a_count=1`、**`visible_to_b_count=0`**；`no_global_or_assistant_fallback`：`global_space_canary_hits=0`、`assistant_space_canary_hits=0`，无回退。
- 证据文件：`c1-live-evidence-memory-isolation.json`（5264 B，`356454cff621068a0367ef58e9ca9efcf890e7625811f94c5380348f9e5641a3`）。

**② 失败的 2 条（同一根因，如实登记）**

| # | 方法 | 结果 | Time(s) ×3 | 只有 raw dump | raw bytes / SHA-256 |
|---|---|---|---|---|---|
| 1 | `realProviderRoundtableRecordsChairSummaryCallSequence`（C1-03） | **FAILURES!!! 3/3** | 301.583 / 300.969 / 301.756 | `c1-real-raw-dump-roundtable.json` | 8323 / `10d7ae2fd816418f776650017ec105aa15cb984c8b6212b6b1687b392cb07f89` |
| 2 | `realProviderRoundRecordsGenuineTokenUsage`（C1-02） | **FAILURES!!! 3/3** | 27.627 / 25.884 / 19.129 | `c1-real-raw-dump.json` | 8077 / `64e84d6563cc1a676bd049c00fcb683a531e9795757d75765a73ef7e9660ea6e` |

- ⚠️ **通过证据文件不存在**：`c1-live-evidence-real-roundtable.json` / `c1-live-evidence-real-provider.json` 的语义是「**全部断言通过才写**」，本批只有 `finally` 落的 raw（`c1-real-raw-dump*.json`；`pass_evidence_note` 逐份明写「this file is NOT the pass evidence」）。
- **失败原文（逐 run 一致）**：#1 `AssertionError: 等待盖章 + run=COMPLETED 超时（300000ms）：失败阶段=盖章完成但 group_runs 未到 COMPLETED：最后状态=FAILED，committed=[a, b] spent=13766；app 错误=[]`（三次 spent 分别 13766 / 6596 / 13651）；#2 `AssertionError: 角色 c 的 modelId 必须是 deepseek-v4-flash expected:<5a86b2d6-9c3c-4c58-9b27-f9295ba39201> but was:<null>`。
- **两者共同形态（登记子代理逐字段读 raw dump 核实）**：第 3 位顺序发言者「角色 c」落为 `turn_kind=error` / 正文 `[角色丙] 本轮生成失败：本轮没有产出内容` / `model_id=null` / `wire_model_name=null` / usage 全 `-1`；`group_run=FAILED / reason=role_failed / committed=["a","b"] / skipped=[] / app_errors=[]`。roundtable raw（r9-01c）里 a/b 是正常 speaker（a 6831/209/7040，b 6546/65/6611；a 的 `parts=[Reasoning(""), Text]`）。
- ⚠️ raw dump 的 `export_sha256` 是 **raw dump 自身的导出**（`7bc76016…` / 2409 B / 5 行），**不是通过证据**——raw 的 `export_sha256_source` 与通过证据同源（生产 `TavernChatCodec.exportGroupJsonl`），但它属于失败跑的落盘，**不得当 #1/#2 的契约字段填**。

**③ 失败根因（6 条，本批最重要的新结论——推翻旧结论）**

1. **不是**之前修的那个「非 2xx + 空 body 被静默当零产出」缺陷（`43d607bdd` 的修复**在部署 APK 里**，已由 `git merge-base --is-ancestor` 核实；走那条会让错误正文变成 `HttpException: ...` 且 `app_errors` 非空）。实测错误正文恰是 `本轮没有产出内容`、`app_errors=[]` ⇒ **没走那条**。
2. **真因**：网关返回 **HTTP 200 但 SSE 流里没有任何 content delta**（空白/无内容完成）。应用按既定语义判零产出：`ConversationSession.finishGeneration`（`ConversationSession.kt:97-116`）先丢弃空的占位助手消息 → `stampGroupTurn`（`ChatManager.kt:1980-2003`）找不到 `roleId==null` 的消息返回 null → `commitGroupTurn` 走 `ChatManager.kt:1957` 写 error 节点 + `FAILED/role_failed`，**全程无异常**。
3. **6/6 稳定复现（roundtable 3 + pipeline 3）⇒ 不是偶发。** ⚠️ **`docs/eval/c1-group-chat.md` 里原先在 `:4114`／判定规则第十条写「这是偶发、不是稳定缺陷」的结论被本批推翻**（原文保留，订正块见 ∮）。注意两处的触发形态不同：旧结论说的是「非 2xx + 空 body 静默当零产出」那一现场未复现；本批复现的是**同一下游症状（零产出）的另一触发**（HTTP 200 空内容），故「偶发」这一表述对**零产出症状**不再成立。
4. **`app_errors` 有采集盲区（新发现）**：它只由 `ChatManager.addError`（`:311-321`）写入，而**零产出路径不抛异常**（不写）、**超时**的 `TimeoutCancellationException` 在 `ChatManager.kt:1020` 先 rethrow（不写）、**用户取消**被 `:317` 早期 return（不写）。⇒ **拿 `app_errors=[]` 当「一切正常」的证据是错的**，值得单独留档（遗留第 46 条）。
5. **refute 掉的假设**：任务里「pipeline 里 c 的 prompt 很长所以失败」**不被数据支持**——c 在 pipeline 的 `prompt_tokens` 从未落库；且 vote 里 c 同一模型第 3 次调用**成功**（deepseek 6877/6882）；基线 `prompt_tokens≈6827` 对 ~300 字符的 system prompt 明显失真（网关侧自报含自身开销），**不能用它推断本地 prompt 长度**。
6. 附注：**`reasoning-only` 响应会被保留并盖章成 speaker**（同批 raw 里角色 a 有 `parts=[Reasoning(""), Text]` 但 `turn_kind=speaker` 的实例）⇒ 说明 c 连 reasoning 都没有（`model_id=null`）。

**④ 导出哈希复算表（登记子代理本机 `hashlib.sha256` 逐份复算 == JSON `export_sha256`）**

| # | export 文件 | 实测 bytes | 实测行数 | 实测 SHA-256 | JSON `export_sha256` | 一致 |
|---|---|---:|---:|---|---|---|
| 3 | `c1-export-real-mention.jsonl` | 1747 | 3 | `6e2ec8ceff572bf2ba3af0376907347ebed83768cd7ac0d08293bcb6dff7fcdd` | 同 | ✅ |
| 4 | `c1-export-real-vote.jsonl` | 2672 | 6 | `8e42c5b90b858fe7b2b0aa58f51d12caa653a40c4693efef7d66d943c205f5de` | 同 | ✅ |
| 5 | `c1-export-real-vote-tie.jsonl` | 2695 | 6 | `65f766083cadc4be789b4c90bb5b35fe81c7a3e1e5d20bea7649c9b94e1647a5` | 同 | ✅ |
| 6 | `c1-export-real-budget.jsonl` | 2044 | 4 | `da0f81c534451198e238f19969074df22a17ecb7ee8094a172f65a8f3dedd544` | 同 | ✅ |
| 7 | **无导出** | — | — | — | —（`evidence_kind=…memory-isolation-no-gateway`） | — |

⚠️ 行数口径：文件无尾随换行，`export_line_count` = `\n` 数 + 1（表头 1 行 + 消息数）。`export_sha256_source` 逐份写明「同进程 `MessageDigest("SHA-256")` over 生产 `TavernChatCodec.exportGroupJsonl` 的字节，并经生产 `writeExportTempFile`（`ConversationExport.kt:836`）重写后逐字节比对」。**这四份导出哈希即 C1-04 / C1-05 契约第 8 项的直接取值。**

**⑤ 判定影响**

- **升 `verified`：C1-04（vote 多数决 + 平票两条路径，契约 8 项齐备、必须断言成立）与 C1-05（预算截断，8 项齐备 + 库内 `messages.count=3`/`assistant_role_order=["a","b"]` 直读）。** 依据：契约 `:206`（输入 / viewer 可见集合 / 调用序列 / token / 导出哈希）+ `:232-235`（8 项）+ 判定规则第 2/4/8 条——本批给了真实网关 usage、wire 级 `provenance={wire:n, fallback:0}`、viewer 台账、逐例导出哈希，且 C1-04 的「导出哈希结构性不产出」旧结论被本批**直接推翻**（两跑都产出），C1-05 的遗留第 43 条（正式证据结构性不可达）被专用用例消掉。
- **不升（仍 `unverified`）**：C1-01（契约 `:201`「其他角色不可见」字面半仍未观测——触发是 USER 消息，A/C 仍可见它，遗留第 15 条不变）；C1-02 / C1-03（本批 3/3 全失败，零产出根因未消除）；C1-06（超时半未跑、viewer 集合未按契约落盘、重复性未做）；C1-07（续跑/幂等测试**仍未写**）；C1-08（**缺 3 项：模型调用序列 / token / 导出哈希**，本例设计上不走网关、不导出）；C1-09 / C1-10（本批未新增）。逐行订正见 20 格。

**⑥ 诚实限制**

- **仅 5 条通过、2 类 3/3 失败；通过项均为单次通过**——但 C1-04/C1-05 在**第十三轮已有 3/3 稳定性**记录，本批为再次通过，非孤立单次。
- **mention 的 shell 退出码未落盘**（旧脚本无 `exitcode.txt`），判据为日志 `OK (1 test)`；契约 `:232-235` 的「退出码」以遗留第 41 条口径「日志判据为准」记录。
- **#7 记忆隔离不走网关**：模型调用序列 / token **结构性缺失**（canned 提取），非采集疏漏；导出哈希**设计上不产出**。⇒ 契约 `:206` 的「每例……模型调用序列、token 计数、导出哈希」对**记忆隔离这一例**存在**契约口径与用例设计张力**（该例 legitimately 不调用模型），本文**不擅自放宽契约**，保持 `unverified` 并如实记缺项。
- **失败根因的第 2 条（代码路径）是读源码推断**（`ConversationSession.kt` / `ChatManager.kt` 行号），**不是**抓到了网关响应原文/状态码；本轮 raw dump **没有 HTTP 状态码/响应体**字段（遗留第 10 类缺口延续）。
- **本批所有 SHA-256 为登记子代理本机复算**；设备侧原始文件在 `/tmp/opencode/c1-device-r9/`（仓库外）。**报告里的「第 3 位角色」措辞**：`assistant_role_order` / `wire_name_resolution` 里它按 seq=3 出现，与「角色 c」是同一行。

### C1 续跑入口与设备侧测试补齐（零设备，2026-10-07，HEAD `d6494e49a`：群聊失败续跑入口落地 + C1-07/C1-10 设备侧测试新增 + C1-06 证据字段补齐；20 状态格全不升）

⚠️⚠️ **先说性质**：本批 = **一次纯零设备窗口**（**零 `adb`、零 `am instrument`、零真机证据**），开工与收尾 HEAD 均为 **`d6494e49a`**（`git rev-parse HEAD` 实测）。窗口 `b60aa80c5..d6494e49a` **实测共 10 个 commit**（`git log --oneline b60aa80c5..HEAD`，**不照抄任何转述数**）：其中 **4 个是本批主角**（`34145bf49` / `1c48b1701` / `9d7429216` / `d6494e49a`），**6 个是上一批（第十七批）按惯例留到本批补登的登记本体**（`07cf703ce` / `5f9d92269` / `cdf88856a` / `42a0f29ae` / `5c3086882` / `c3e921a5c`，均只改 `docs/`；逐笔 `git show --name-only` 核实）。

⚠️ **本批 4 个主角逐笔 `git show --stat` 实测**：

| SHA | `--stat` 实测 | 性质 |
|---|---|---|
| `34145bf49` | `C1GroupRetryResumeDeviceTest.kt` **新增 / 968 行** | androidTest（**未真机跑**） |
| `1c48b1701` | `C1GroupPagingAndFilterDeviceTest.kt` **新增 / 813 行**（8 个 `@Test`） | androidTest（**未真机跑**） |
| `9d7429216` | `C1GroupCancelDeviceTest.kt` **M / +283 / −0** | androidTest（补证据字段，无实际值） |
| `d6494e49a` | `GroupRetryEntry.kt` 新增 70 + `GroupRetryEntryTest.kt` 新增 203 + `ChatMessage.kt` +13 + `ChatMessageActions.kt` +27 + `ChatList.kt` +16 = **5 files / +329** | **生产代码（`app/src/main`）+ JVM 测试（`app/src/test`）** |

⚠️ **本批对契约验收证据的影响 = 零**：契约 `:206` / `:232-235` 点名的四类产物（viewer 可见消息 ID / 实际模型调用序列 / prompt+completion token / 导出 SHA-256）**一份未增**（无设备）。⇒ **20 个状态格一个判定都没改**：C1-04 / C1-05 仍 `verified`、其余 18 格仍 `unverified`（逐格理由见 ⑦）。

**① 新基线（`c1_doc_stats.py` 主命令本机实测，本轮零 gradle 重跑）**

- `:app:testDebugUnitTest` = **114 类 / 939 例 / 0F0E0S**（XML 与源码 `@Test` 两口径一致）；较上一基线的 **+1 类 / +8 例全部来自 `GroupRetryEntryTest`**（`d6494e49a`）。
- 交叉验证：XML **114 类** / 源码 **114 类**（差 0，容差 5）**一致**。
- `:ai:test` = **30 类 / 220 例 / 0F0E0S**（本轮未动）。
- lint app = **error 0 / warning 581 / hint 6 = 587**；全模块 = **error 0 / warning 614 / hint 7 = 621**（本轮未动）。
- 仪器 `@Test`：**20 文件 / 84 例**（含 untracked `C1GroupUiE2EFixtureTest.kt` 1 文件 / 3 例；**tracked 19 文件 / 81 例**）。⚠️ 本批新增的两个 androidTest 文件（`34145bf49` / `1c48b1701`）**已入库**，计入 tracked。

**② 台账声明值：本轮必须变动（46 行 / 501 例 → 47 行 / 509 例）**

⚠️ **先纠一处预测偏差**：交接方预期「`c1_doc_stats.py` 的 ledger 会 FAIL（报 `GroupRetryEntryTest` 声明缺失或类数不符）」。**实测不会 FAIL**——脚本 `ledger` 口径**只逐行核对「表内已列类」的声明例数 vs 源码 `@Test`**（`section_ledger`，`tools/verification/c1_doc_stats.py:373-392`），**不检查「源码里有没有未列进表的类」**。所以 `GroupRetryEntryTest` 未进表时脚本**仍报 18 OK / 0 FAIL**（本机改前实测：`台账行数 46` / `声明合计 501` / `逐行核对 46 行全部相等` / `合计 18 条：OK 18`）。
⚠️ **但按台账自身口径**（本文件 `:621-624`「本文件正文点名引用过、且能在 XML 里对上的类」），新类**应当**进表——本批把它写进正文（即本证据节）后，它便满足「正文点名 + XML 对得上」，故**主动补登**（不是被 FAIL 逼出来）。
- 改前：**46 行 / 501 例**（`--only ledger` 实测 `declared sum 501`）。
- 改后：**47 行 / 509 例**（`+1 类 / +8 例`，完全来自 `GroupRetryEntryTest` 的 XML `tests="8"`）。
- ✅ 改后 `--only ledger` 实测：`台账行数 47 行 / 台账声明例数合计 509 例 / 逐行核对 47 行全部相等`，**OK 3 / WARN 0 / FAIL 0**。
- ⚠️ **历史笔误照记（不覆写）**：表内既有那条 `合计` 行声明 `**506**`，而它上方逐行「最后一个粗体数字」求和是 **`501`**（差 `5`，脚本 `declared sum` 也是 501）。本批**新增一条** `合计` 行按脚本口径写 `509`，旧行原文保留。
- ⚠️ `tools/verification/c1_doc_stats.py` **一个字节未改**：跑前 `sha256 = 238bb45f7035852acf3f27013bd5d3d6372191a4543d3297ef7044e3899f4321`，跑后**同值**。

**③ `d6494e49a` = 生产代码变更，按规格登记（填契约 `:204` / `:227-228` / `:201` 承诺的「重试可从失败角色续跑」）**

- **新增纯判定** `GroupRetryEntry.canResume(isGroup, isLastMessage, message)`（`app/src/main/java/heizige/kk/khatkit/app/feature/chat/GroupRetryEntry.kt:61-69`）：条件 = 群聊 && 是最后一个节点 && `MessageRole.ASSISTANT` && `turnKind == GroupChat.TURN_ERROR` && `roleId != GroupChat.SUMMARY_ID`。**只做判定、不读库不写库**（KDoc `:50`），便于 JVM 穷举形状。
- **接通点**：`ChatList.kt` 计算 `onGroupResume`（`index == lastMessageIndex` + `GroupRetryEntry.canResume(...)`）→ `ChatMessageActions.kt:155-169` 新增续跑按钮渲染点（`onGroupResume != null` 才渲染）→ `ChatMessage.kt` 透传 `onGroupResume`（`ChatMessageActionButtons` 入参）。⚠️ **未触碰任何 `if (!groupChat)` 门禁**（实测 diff：`onRegenerate` 仍 `if (!groupChat)`、`ChatMessageBranchSelector` 仍 `ChatMessageActions.kt:244 if (!groupChat)`、Edit 仍 `if (!groupChat || role == USER)`）。
- **点击仍落到同一个 `ChatManager.regenerateAtMessage`**——`d6494e49a` **未改 `ChatManager.kt`、未发明新机制**（`git show --stat` 只列上面 5 个文件）。
- **「账面错位」的机制（本批最重要的技术发现，逐条打开源文件核实行号）**：`regenerateAtMessage` 对**助手消息**走 `handleMessageComplete(conversationId, messageRange = 0..<nodeIndex)`（`ChatManager.kt:638`），产出经 `Conversation.updateCurrentMessages`（`Conversation.kt:74-106`）落在**被点消息所在的 `MessageNode`** 里、成为新候选并切 `selectIndex`（`:82-92`）⇒
  1. **被点角色已提交的发言被顶掉**（新候选进同一节点、`selectIndex` 切过去）；
  2. `regenerateAtMessage` **从不调用 `updateCommittedRoles`**（全仓唯一调用点 `ChatManager.kt:1903`，在群聊提交路的 `persistRoundState` 内，与重新生成无关）⇒ `group_runs.committed_role_ids` **仍声称该角色已提交**；
  3. `pendingSpeakers` 按 committed 过滤（`GroupChat.kt:902-903`）⇒ **该角色永久被跳过**；而 `roundOutputPresent` 是 `any` 判定（`ChatManager.kt:2361-2373`）**抓不到「个别角色产物被顶掉」**；
  4. 若该轮已 `COMPLETED`，`claimRound` 返回 `Rejected(ALREADY_COMPLETED)`（`GroupTurnCoordinator.kt:261-263`）⇒ **静默 no-op**；
  5. **群聊分支选择器也被关**（`ChatMessageActions.kt:244` `if (!groupChat)`）⇒ 用户**无法切回被顶掉的候选**。
- **为什么新入口能避开它**：`canResume` 只认**失败角色的错误节点**（由 `failGroupTurn` 写、该角色**不在** `committedRoleIds` 里，`GroupTurnCoordinator.kt:469-483`）且**必须是最后一个节点**（`nodeIndex == lastIndex` ⇒ `messageRange = 0..<lastIndex` 正是「错误节点之前」的正确上下文，新产出落回**同一失败角色名下**，`role_id` / `round_id` 一致）；并排除 `SUMMARY_ID`（投票未决的失败摘要也是 `TURN_ERROR` + 最后一条，但它是合成节点、没有待发言角色，续跑只会是 no-op）。
- ⚠️ **已知限制（如实登记）**：**取消（`cancelRound`，只写运行日志、无错误节点，`GroupTurnCoordinator.kt:485-491`）与预算中止（`BUDGET_STOPPED`，无错误节点）的轮次没有续跑入口**——它们不是「失败角色错误节点」形状，按契约其续跑需要 UI 读 `group_runs` 状态，属另一子包（新增遗留第 48 条）。
- ⚠️ **本轮未在真机验证**：该入口的**真机行为零份**（无设备）；只有 JVM 纯判定测试 8 条 + 源码接线 + path 推断。**不构成任何用例的验收证据**。

**④ `9d7429216` 的 C1-06 补字段（下次真机跑才产实际值）**

新增证据 JSON 字段：`viewer_visibility`（按 viewer 的 `viewer_role_id` → `visible_message_ids` / `visible_assistant_role_ids`，源生产 `GroupChat.visibleMessages`，a/b/c 各一）+ `viewer_visibility_asserted`；`actual_model_call_sequence[]`（含 `message_id` / `role_id` / `round_id` / `turn_kind` / `model_id` / `wire_model_name` / `stamped_as_committed` / `prompt_tokens` / `completion_tokens`；被取消的 b 部分产出 `role_id` / `round_id` 为 null、token 记 `-1`）；`export_sha256` / `export_bytes` / `export_line_count` / `export_path` / `export_file_name` / `export_sha256_source` / `export_production_io_file` / `export_production_io_uri`；`contract_fields_added_this_round`；`timeout_half_verified=false` + `timeout_half_note`。
⚠️ **C1-06 超时半确认做不到（如实登记，别写成「已验」）**：`ChatManager.kt:116` `private const val GROUP_ROUND_STEP_TIMEOUT_MS = 15*60*1000L` **不可注入**；实际挂在 `withTimeout(...)`（`ChatManager.kt:1005`）；测试构造的 `AppScope`（`KhatKitApp.kt:344-352`）是**无参 concrete class**、固定绑 `Dispatchers.Main`，**无法注入 `TestDispatcher` / 虚拟时间**。虚拟时间路径**必须改生产**，本轮禁止。⇒ `timeout_half_verified=false` 是**诚实缺项**，不是采集疏漏。
⚠️ **这些字段一行实际值都还没有**——`9d7429216` 只是把**写出器补齐**，真机跑（有设备）才会产出 `c1-live-evidence-cancel.json`。⇒ C1-06 **不升**。

**⑤ C1-07 / C1-10 设备侧测试新增，但未真机跑**

- **C1-07**：`34145bf49` 新增 `C1GroupRetryResumeDeviceTest.kt`（968 行），设计两阶段同一 `round_id` 续跑 + 幂等。⚠️ **该测试 KDoc 自述「群聊 UI 目前没有重试入口……所以续跑路径目前只能从 `ChatManager` API 触达」——这句话在 `d6494e49a` 之后已过期**（入口已补，见 ③）。`34145bf49` 早于 `d6494e49a`，登记时按**时序**如实记录：**测试写时 UI 无入口、入口是后一笔补的**。
- **C1-10**：`1c48b1701` 新增 `C1GroupPagingAndFilterDeviceTest.kt`（813 行 / 8 `@Test`）。
- ⚠️ **两条本轮都只验证到编译 + 结构 + 纯逻辑 JVM 探针，真机结果未知**（用户明确要求不动设备）⇒ 均**不构成验收证据**，**不升**。

**⑥ C1-08 缺的三项，评估结论 = 不产出（如实登记）**

C1-08 用例（`realProviderGroupMemoryIsolationRecordsPerSpaceHits`）缺「模型调用序列 / token / 导出哈希」三项，结论**不产出**，理由（逐条核实）：
- **模型调用序列 / token**：`MemoryExtractor.extractFromTurn`（`MemoryExtractor.kt:99-135`）确实会调 provider（`:112` `providerManager.getProviderByType(provider).generateText(...)`），但它**把 usage 丢掉**（只用 `result.message.toText()` 送 `parseAndStore`、只返回写入条数，`:124-134`）——要拿 token 必须**自造记录型 Provider 拦截** ⇒ 会把「确定性隔离用例」改成联网/注入型，对隔离结论**零增益**且引入 flake ⇒ **语义正交**。
- **导出哈希**：记忆隔离**不产生任何群会话导出物**（导出属 C1-09）⇒ **不适用**。
⇒ C1-08 **不升**。

**⑦ 判定影响：20 格逐格判定（本轮全不升）**

⚠️ 20 格 = 用例矩阵 10 格 + 证据登记表 10 格；本轮**每一格都保留原文 + 就地追加「第十五轮订正」块**（不覆写历史）。

| 用例 | 判定 | 不升理由（契约行号） |
|---|---|---|
| C1-01 三角色显式 @ | 不升（仍 `unverified`） | 契约 `:201`「只被 @ 角色收到该消息；其他角色不可见」的**字面半**仍未观测（触发是 USER 消息、A/C 仍可见它，遗留第 15 条）；本批零设备、四类产物零份 |
| C1-02 无 @ 的 pipeline | 不升（仍 `unverified`） | 本批未跑真实网关 pipeline；`d6494e49a` 续跑入口与本行「调用序列与日志一致」无关；契约 `:206` 四类产物零份（第十四轮零产出根因未消） |
| C1-03 roundtable | 不升（仍 `unverified`） | 本批未跑 roundtable（第十四轮 3/3 全失败、零产出根因未消）；契约 `:206` 零份 |
| C1-04 vote | 不升（维持 `verified`） | 本批零设备、无新真机证据；维持第十四轮 `verified`（契约 `:206`/`:232-235` 8 项齐备的结论未被本批触及） |
| C1-05 轮次预算 | 不升（维持 `verified`） | 同上，维持第十四轮 `verified` |
| C1-06 取消与超时 | 不升（仍 `unverified`） | 超时半**结构性做不到**（`GROUP_ROUND_STEP_TIMEOUT_MS` 不可注入，`ChatManager.kt:116`/`:1005`；`AppScope` 无参 `KhatKitApp.kt:344-352`）；`9d7429216` 只补字段、无实际值；契约 `:206` 四类产物零份 |
| C1-07 失败续跑/幂等 | 不升（仍 `unverified`） | 新增设备侧测试 `34145bf49` 但**未真机跑**；`d6494e49a` 补上群聊 UI 续跑入口（契约 `:204`/`:227-228` 的落点），但无真机证据 |
| C1-08 记忆隔离 | 不升（仍 `unverified`） | 契约 `:206` 缺三项（模型序列 / token / 导出哈希）；本轮评估结论为**不产出**（语义正交 / 不适用），仍缺 |
| C1-09 Tavern/QR 往返 | 不升（仍 `unverified`） | 本批未碰；酒馆本体 / `ACTION_SEND` / 相机扫码仍零份 |
| C1-10 单聊/群聊共存 | 不升（仍 `unverified`） | 新增设备侧测试 `1c48b1701`（813 行 / 8 `@Test`）但**未真机跑**；契约 `:232-235` 不认仅 UI 级证据、四类产物零份 |

**⑧ 诚实限制**

- 本批**零设备、零 `adb`、零仪器执行**——全部是「源文件 + JVM + 文档」证据。
- `d6494e49a` 的**真机行为零份**（入口按钮是否真渲染出 `onGroupResume`、点击是否真走 `regenerateAtMessage`）——只有 JVM 纯判定 + 源码接线 + path 推断。
- `9d7429216` 的字段**无实际值**；`34145bf49` / `1c48b1701` 的测试**无真机结果**。
- 本批基线数字**未重跑 gradle**（沿用现有 XML / lint 报告）；`c1_doc_stats.py` 主命令实测见 ①。
- 遗留清单同步：**新增第 47 条**（`d6494e49a` 落地群聊「失败续跑」入口——「群聊 UI 无重试入口」这一缺口**已消**，但范围收窄到失败角色错误节点且真机零份）与**第 48 条**（取消 / 预算中止轮次仍无续跑入口）。

### C1 真机证据采集第十五轮（2026-10-07，HEAD `37630adb4`：pipeline/roundtable 首次即通过 + 取消用例补齐 8 项契约字段 + 零产出探针未复现；C1-07/C1-10 暴露两个测试侧缺陷）

> ⚠️ **标题编号说明**：主管简报给的本节标题写「第十轮」，但本文档「C1 真机证据采集」序列已排到**第十四轮**（见上），简报背景自称「设备第 15 轮」，故按文档既有序列订正为**第十五轮**；简报的副标题文字原样保留。

🚨 **先说性质**：本批 = **一次真机采集窗口**。设备 OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`（`fingerprint=OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`——逐份证据 JSON `device` 字段实测值）。开工 / 收尾 HEAD 均为 **`37630adb4`**（`git rev-parse HEAD` 实测）；**设备轮本身零 commit**（`git log --oneline 37630adb4..HEAD` 实测为空）。构建 `:app:assembleDebug :app:assembleDebugAndroidTest` **exit 0**；两个 APK `install -r -t` 均 `Success`。⚠️ `am instrument` 的 shell 退出码即使失败也是 0，判据只用日志正文 `OK (1 test)` / `FAILURES!!!`（遗留第 41 条）。

🚨 **结论**：① **C1-02 / C1-03 首次真机通过（pipeline / roundtable）**，契约 8 项齐备；② **C1-06 取消半补齐全部契约字段并首次真机通过**，但超时半结构性做不到、且为单次通过；③ **C1-07 2/2 稳定失败**（`:378`）；④ **C1-10 8 方法 3 通过 / 5 失败**（全为 `pagingSource_*`）；⑤ **零产出只读探针 `diagnosis=null`（未复现）** ⇒ 该 bug 改为**间歇性**。⑥ **状态判定：C1-02 与 C1-03 各在两个格子升 `verified`（共 4 格）**，其余不升（逐格见 ⑤）。

⚠️ **证据根目录**：`/tmp/opencode/c1-verify/`（仓库外）。本节所有 SHA-256 / 字节数 / 行数由**登记子代理本机独立复算**（`sha256sum` / `stat`），凡标「执行者报告」者为转述、登记时未独立复现。

**① 通过的 3 条（逐列 8 项契约字段：commit / 命令·退出码 / 设备·Android / 用例输入 / viewer 可见消息 ID / 模型调用序列 / prompt+completion token / 导出 SHA-256）**

> ⚠️ 8 项 = 契约 `client-changes.md:206` + `:232-235` 逐例要求的「commit、测试命令及退出码、设备/Android 版本、用例输入、各 viewer 的可见消息 ID、实际模型调用序列、prompt+completion token、导出 SHA-256」。

**#1 C1-02 无 @ 的 pipeline——`C1LiveModelSequenceTest#realProviderRoundRecordsGenuineTokenUsage`，`OK (1 test)` / Time 15.294**

- commit：run 时 HEAD `37630adb4`（设备轮零 commit）。
- 命令·退出码：`:app:assembleDebug :app:assembleDebugAndroidTest` exit 0，两个 APK `install -r -t` Success，`am instrument -w -r -e class 'heizige.kk.khatkit.app.feature.chat.C1LiveModelSequenceTest#realProviderRoundRecordsGenuineTokenUsage' heizige.kk.khatkit.debug.test/androidx.test.runner.AndroidJUnitRunner`；shell 退出码不可判（遗留 41），日志判据 `OK (1 test)`。
- 设备·Android：OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`。
- 用例输入：`mode=pipeline`，`conversation_id=51c16875-6d1a-4594-b36d-306a0c6a9026`，provider「极客猫」`https://api.zenneko.top/v1`，USER「请三位依次发言，每位一句话。」，`token_budget_per_round=100000`。
- viewer 可见消息 ID（`viewer_visibility`，源生产 `GroupChat.visibleMessages`）：a=`[d2d54852-32ba-480f-8951-12c75c18da2c, 4f0a5585-d1c6-4f5d-a879-b70bc52d1cb2]`；b=`[d2d54852…, 4f0a5585…, 98eea0d4-c559-49bd-bc45-2b5e447c61fc]`；c=`[d2d54852…, 98eea0d4…, 0be40a2d-a00a-440c-90c3-b5b9155a69a7]`（a 见 a、b 见 a+b、c 见 b+c，符合 `pipeline_visibility_expectation`）。
- 模型调用序列（`actual_model_call_sequence`）：seq1 a `deepseek-v4-flash 6829+65`、seq2 b `glm-5.2 6609+54`、seq3 c `deepseek-v4-flash 6891+55`；`wire_and_reverse_lookup_agree=true`、`wire_model_name_provenance_counts={wire_response_model:3, uuid_reverse_lookup_fallback:0}`；`expected=[deepseek-v4-flash, glm-5.2, deepseek-v4-flash]`。
- prompt+completion token：a 6894 / b 6663 / c 6946，`sum_prompt_plus_completion=20503`，`spent_tokens=20503`，`spent_tokens_equals_sum_prompt_plus_completion=true`；`group_run=COMPLETED/committed=[a,b,c]/token_limit=100000`。
- 导出 SHA-256：`c1-export-real-provider.jsonl` **2357 B / 5 行**，`5f7914d13f74c3a3b5ccab7ba1777e281d71a33d7641d7e7e21e8d6ded4e4d05`（与 JSON `export_sha256` 逐字一致）。
- 证据文件：`c1-live-evidence-real-provider.json`（**12674 B**，`fdd5fc23e2a7cc355bb28f9313aa5e2f713045cc3865c6d3c9d9ca44335afb8a`）；raw `c1-real-raw-dump-C102.json`（**8247 B**，`a20546812a777f2350b36d88572221620e3b00e9b2f042835602927e9ea1ddb9`）。

**#2 C1-03 roundtable——`C1LiveModelSequenceTest#realProviderRoundtableRecordsChairSummaryCallSequence`，`OK (1 test)` / Time 22.201**

- commit：run 时 HEAD `37630adb4`。
- 命令·退出码：同 #1；日志 `OK (1 test)`。
- 设备·Android：同上。
- 用例输入：`mode=roundtable`，`chair_role_id=c`，`tie_policy=fail`，`conversation_id=ef2be184-9053-47d6-9a74-3a276094e6e2`，USER「请三位依次发言，议长最后汇总。」，`token_budget_per_round=100000`。
- viewer 可见消息 ID：a=`[8f94828c-6da8-4630-8f79-10b1b540e81d, 6f66ac69-3125-415b-9216-dc1f05ba5f82]`；b=`[8f94828c…, 78086327-09db-4120-b932-0534a44a4cbd]`；议长 c（`chair_round=true`）=`[8f94828c…, 6f66ac69…, 78086327…, 5f3468a5-df35-4c65-bb25-731bd2921398]`（a/b 各见 2 条、互不见对方；议长 c 见全部 4 条）。
- 模型调用序列：seq1 a `deepseek-v4-flash 6831+93`、seq2 b `glm-5.2 6546+80`、seq3 c（`turn_kind=chair`）`deepseek-v4-flash 7011+146`；`speaker_order_and_turn_kind={a:speaker, b:speaker, c:chair}`；`provenance={wire_response_model:3, uuid_reverse_lookup_fallback:0}`。
- prompt+completion token：a 6924 / b 6626 / c 7157，`Σ=20707==spent_tokens`；`group_run=COMPLETED/committed=[a,b,c]/token_limit=100000`。
- 导出 SHA-256：`c1-export-real-roundtable.jsonl` **2371 B / 5 行**，`75ca8ea92c135982ca6aa07d9b778aac7703901cf3174cfa3fbec214c40f8a11`（与 JSON 一致）。
- 证据文件：`c1-live-evidence-real-roundtable.json`（**13423 B**，`d18c4441fbafc970482717f9feabfdfaa6b06ab2a2d9c9b6a9ed3626f69b380d`）；raw `c1-real-raw-dump-roundtable-C103.json`（**8295 B**，`822f2fc9c221e355f5a22741423226d3719b82344e0d2d31e224b53272363d77`）。

**#3 C1-06 取消半——`C1GroupCancelDeviceTest#cancelMidStreamKeepsGeneratedMessagesAndErrorNodeWithoutEmptyBubbles`，`OK (1 test)` / Time 4.292**

- commit：run 时 HEAD `37630adb4`。
- 命令·退出码：同 #1；日志 `OK (1 test)`。
- 设备·Android：同上。
- 用例输入：`mode=pipeline`，conversation `ae8f558f-c4ed-4e3c-aec6-d0f1e9fb74ee`，mock provider `http://127.0.0.1:8766/v1`（`mock_openai_slow_cancel.py`，启动行 `fail_role='' empty_role='B' delays={'A':0.02,'B':0.5,'C':0.1}`；`enableAutoRetry=false` 防 mock 500 被 failover 顶包真网关）；round1 USER「第一轮：请三位依次发言，每位一句话。」，round2 USER「第二轮：继续展开细节。」；round2 等 `a` 已提交且 `b` 流式途中再 `stopGeneration`。
- viewer 可见消息 ID：a=`[9cdbe6bd-f392-4a24-ae22-110a0c0db911, fb85c94e-7d0a-4c8d-bf5d-000bddae10d0, 0ec27875-2180-4c51-856c-cdf576a8a92f, bfad4b64-8f71-4b57-93ec-4c44593e2913]`；b=`[9cdbe6bd…, b5cefb7b-90ea-4085-bda6-7d4757a7ad19, 0ec27875…, bfad4b64…]`；c=`[9cdbe6bd…, 0ec27875…]`（a/b/c 三台账齐全，`viewer_visibility_asserted=true`）。
- 模型调用序列（`actual_model_call_sequence`，**4 条全 mock，无真网关顶包**）：a/round1 `mock-cancel-a 6190+22 stamped`、b/round1 `error/null`、a/round2 `mock-cancel-a 6236+22 stamped`、b 半截（`model_id=0c1c06ca-…000000b`、`role_id/round_id=null`、token `-1`）。
- prompt+completion token：round1 `FAILED/role_failed/committed=[a]/spent=6212`；round2 `CANCELLED/cancelled/committed=[a]/skipped=[]/token_limit=100000/spent=6258`；`a2_prompt_completion=6258`。
- 导出 SHA-256：`c1-export-cancel-vfy1.jsonl` **2977 B / 7 行**，`853033a897eca830c93d57e9a6fcaa704da896ea51b34662840d4bac6184ea71`（与 JSON 逐字一致）。
- 必须断言核心（`observed`）：`empty_bubble_count=0`、`a2_present=true`、`b2_partial_message_count=1`（正文 `[mock-slow] CASE:cancel `）、`c2_message_count=0`、`error_node_from_round1_still_present=true`、`new_error_nodes_in_round2=0`；`timing_millis`：`round2_b_stream_observed→stop_called=3ms`、`stop_call_duration=33ms`。
- 证据文件：`c1-device-cancel-report-vfy1.json`（**9919 B**，`ed4d8b8402911166cb3b4dcc2bc1e675160fbf312a73cc178ebe86c5c9f419d2`）；raw `c1-cancel-raw-round1-vfy1.json` 2111 B `2303561d…`、`c1-cancel-raw-final-vfy1.json` 3924 B `a7723cc6…`；trace `c1-cancel-trace-vfy1.txt` 629 B `581be423…`；mock 本体 `mock_openai_slow_cancel.py` `0f2af473f94931cf97843fee1d7ea41462b4dc94cfecfe4ab10bfd286d0d3fde`。
- ⚠️ **超时半仍结构性做不到（诚实缺项）**：`ChatManager.kt:116` `private const val GROUP_ROUND_STEP_TIMEOUT_MS` 不可注入、`:1005` `withTimeout`；测试构造的 `AppScope`（`KhatKitApp.kt:344-352`）是无参 concrete class。JSON `timeout_half_verified=false`。

**② 未通过的 2 条（如实登记，不是可升级证据）**

**#4 C1-07——`C1GroupRetryResumeDeviceTest#retrySameRoundSkipsCommittedTurnsAndDoesNotDuplicateMessages`：`FAILURES!!!` 2/2，稳定失败**

- 失败原文（两次一致）：`AssertionError: 阶段 1 的 b 错误节点必须原样保留`，位置 `C1GroupRetryResumeDeviceTest.kt:378`；Time 15.843 / 13.337。
- raw 字段（phase1 = `c1-retry-raw-phase1-vfy1.json`，1936 B）：`status=FAILED/reason=role_failed/error_message=本轮没有产出内容/spent=1741/run_token=604b0069-19b8-421f-a5e4-26c561d4f654/committed=[a]/skipped=[c]/started_at=1791336038560`。
- raw 字段（phase2 = `c1-retry-raw-phase2-vfy1.json`，4325 B）：`status=COMPLETED/committed=[a,b,c]/spent=14819/同 run_token/同 started_at`；`extra_run` 即 phase1 的 `FAILED` 块；a 消息 id `cb45d9ef-1e79-4526-961b-fbfda5047a3e` **不变**；b/c 各 1 条；`spent=14819==Σusage(1719+22+6217+322+6517+22)`。
- ⚠️ **待判定**：是**测试期望错**还是**生产缺陷**，由**另一任务**判；本轮只登记现状与 raw 字段 ⇒ C1-07 **保持 `unverified`**。

**#5 C1-10——`C1GroupPagingAndFilterDeviceTest`：8 方法 3 通过 / 5 失败**

- 通过的 3 条（`OK (1 test)`）：`typeFilter_allChipsFilterIndividuallyAndRestoreExactly`（Time 6.41）、`typeFilter_chipSwitchNeverLosesData_dbCountsStable`（Time 6.021）、`mixedList_groupBadgeRendersOnlyOnGroupRows`（Time 4.547）。
- 失败的 5 条（均 `pagingSource_*`）：`pagingSource_firstScreenUsesInitialLoadSize_thenPageSize`（`:357` expected 15 but was 20）、`pagingSource_allPagesSumToDbCount_withoutDuplicates`（`:392` expected 55 but was 60）、`pagingSource_typeFilterNarrowsWithinSql`（`:435` expected 25 but was 40）、`pagingSource_filterAllArgumentIsEmpty_equivalentToSqlNoFilter`（`:493` expected 55 but was 60）、`pagingSource_searchPathCarriesTypeAndPages`（`:527` expected 25 but was 40）。
- 真机 DB 实测 **55 行**（GROUP=25 / DIRECT=30）；生产常量 `PAGE_SIZE=20` / `INITIAL_LOAD_SIZE=40`，但分页 source 累计取回 **60 / 40** ⇒ **重叠 / 超取**。**另一任务在修**；本轮只登记 ⇒ C1-10 **保持 `unverified`**。

**③ 零产出只读根因探针（P1）：未复现**

- `C1GroupZeroOutputProbeTest#pipelineThirdSpeakerZeroOutputProbe`：`OK (1 test)` / Time 14.486，**`diagnosis=null`（零产出未复现）**。
- 证据 `c1-zero-output-probe.json`（**18862 B**，`615e2150096e500e05d4b6aa2bfa9f607039ccdaa28e8969579a41ca12ab75d6`）：`group_run_status=COMPLETED`、`committed=[a,b,c]`、`error_node_role_id=null`、`group_run_spent=20750`、`sampler_poll_count=130`、`sampler_transition_count=12`、`placeholders_observed_count=3`、`vanished_unstamped_count=0`；三个占位（a/b/c）全部「盖章 + 有正文」（`max_text_length` 22 / 21 / 16），无 `vanished_unstamped`。trace `c1-zero-output-probe-trace.txt`（`diagnosis failing=null cause=null vanished=0`）。
- ⇒ **该 bug 是间歇性而非 100% 必现**（此前第十四轮曾 6/6 稳定复现）。遗留第 45 条据此就地加订正（保留原文）。

**④ 导出哈希复算（登记子代理本机 `sha256sum` 逐份复算 == JSON `export_sha256`）**

| # | export 文件 | 实测 bytes | 实测行数 | 实测 SHA-256 | JSON `export_sha256` | 一致 |
|---|---|---:|---:|---|---|---|
| 1 | `c1-export-real-provider.jsonl` | 2357 | 5 | `5f7914d13f74c3a3b5ccab7ba1777e281d71a33d7641d7e7e21e8d6ded4e4d05` | 同 | 是 |
| 2 | `c1-export-real-roundtable.jsonl` | 2371 | 5 | `75ca8ea92c135982ca6aa07d9b778aac7703901cf3174cfa3fbec214c40f8a11` | 同 | 是 |
| 3 | `c1-export-cancel-vfy1.jsonl` | 2977 | 7 | `853033a897eca830c93d57e9a6fcaa704da896ea51b34662840d4bac6184ea71` | 同 | 是 |

⚠️ 行数口径同第十四轮：文件无尾随换行，`export_line_count` = `\n` 数 + 1（表头 1 行 + 消息数）。

**⑤ 判定影响：20 格逐格判定（4 格升级）**

⚠️ 20 格 = 用例矩阵 10 格 + 证据登记表 10 格；**14 格保留原文 + 就地追加「真机第十五轮订正」块**；C1-01 / C1-04 / C1-05 按「已 `verified` 不重开」惯例**不改**（C1-01 的 `verified` 由 HEAD `37630adb4` 本提交建立）。

| 用例 | 判定 | 理由（契约行号） |
|---|---|---|
| C1-02 无 @ 的 pipeline | **升 `verified`**（矩阵格 + 证据表格各 1 格） | 契约 `:206` + `:232-235` 8 项齐备且必须断言成立（真实网关 pipeline、wire 级 provenance、Σ=spent、viewer 台账、导出哈希） |
| C1-03 roundtable | **升 `verified`**（矩阵格 + 证据表格各 1 格） | 契约 `:206` + `:232-235` 8 项齐备；`speaker_order_and_turn_kind` 与「议长前看不到未完成输出」成立 |
| C1-06 取消与超时 | 不升（仍 `unverified`） | 契约 `:201` 要求「取消 / 超时」两半；超时半结构性做不到（`timeout_half_verified=false`），且通过为单次、未做重复稳定性 |
| C1-07 失败续跑/幂等 | 不升（仍 `unverified`） | 契约 `:204` 幂等路径本批 2/2 失败（`:378`），当前是未解决的失败 |
| C1-08 记忆隔离 | 不升（仍 `unverified`） | 契约 `:206` 缺 3 项（模型序列 / token / 导出哈希）；评估语义正交 / 不适用，待用户认可 |
| C1-09 Tavern/QR 往返 | 不升（仍 `unverified`） | 本批未碰 |
| C1-10 单聊/群聊共存 | 不升（仍 `unverified`） | 本批 3 通过 / 5 失败（`pagingSource_*`）；契约 `:232-235` 不认仅 UI 级证据且 `pagingSource` 有真缺陷待修 |

**⑥ 诚实限制**

- 通过的 3 条中，**C1-02 / C1-03 各只有一次真机全绿**；C1-06 亦单次。⚠️ **C1-02 / C1-03 的升级依据是契约 8 项齐备 + 必须断言成立，不是重复稳定性**（第十四轮曾 3/3 失败，本轮首次通过；重复稳定性建议后续补）。
- **C1-06 超时半结构性做不到**（见 ①#3），JSON `timeout_half_verified=false` 是诚实缺项。
- **C1-07 / C1-10 的失败是测试侧现状**，是否测试期望错由另一任务判定 / 修复，本轮不做结论。
- **设备设置已还原**（执行者报告，未独立复现）：`screen_off_timeout=30000`、`stayon=false`。
- 本节所有 SHA-256 / 字节 / 行数为**登记子代理本机独立复算**；设备侧原始文件在 `/tmp/opencode/c1-verify/`（仓库外）。

### 零设备修复批次（2026-10-07，HEAD `5c362851a`，台账第二十批：C1-10 分页测试驱动缺陷 + `ConversationRepository` offset 分页 API 生产缺陷 + C1-07 续跑错误节点断言；20 状态格全不升）

> ⚠️ **轮次命名说明**：本文档「C1 真机证据采集第 X 轮」是**真机采集**序列（已排到第十五轮），**零设备批次不占该序号**——故本节沿用上一个零设备节（「C1 续跑入口与设备侧测试补齐（零设备，2026-10-07，HEAD `d6494e49a`）」）的命名方式；「台账第二十批」按下方「C1 commit 台账」的批次序。

⚠️⚠️ **先说性质**：本批 = **一次纯零设备窗口**（**零 `adb`、零 `am instrument`、零真机证据**），开工与收尾 HEAD 均为 **`5c362851a`**（`git rev-parse HEAD` 实测 = `5c362851acc1adeb2fceb3b9237d48d063dc6760`）。

⚠️ **本批 = 上一批（第十九批，真机第十五轮）按惯例留到本批补登的登记本体 3 个（均只改 `docs/`）+ 本批主角 3 个**。窗口 `37630adb4..HEAD` **实测共 6 个 commit**（`git log --oneline 37630adb4..HEAD | wc -l` = **6**，`--merges` = **0**；逐笔 `git show --name-only` 核实）：

| SHA | 标题 | 性质 |
|---|---|---|
| `c4d015e53` | `coder: 登记C1真机第十五轮证据节——pipeline/roundtable首次通过+C1-06取消补齐8项+零产出探针未复现+C1-07/C1-10现状` | 文档（=第十九批登记本体，本批补登） |
| `486117ee3` | `coder: C1矩阵/证据表4格升verified(C1-02/C1-03)+14格追加真机第十五轮订正+遗留49/50+第45条改间歇性+台账第十九批` | 文档（=第十九批登记本体） |
| `ff9c86359` | `coder: 实施状态补第二十四批——真机第十五轮…` | 文档（status，=第十九批登记本体） |
| `0ae9f570a` | `coder: C1分页设备测试改用生产页类型驱动(首屏Refresh/后续Append)修复5条超取失败` | 测试（`app/src/androidTest/.../C1GroupPagingAndFilterDeviceTest.kt`，**+29 / −2**；**生产代码零改动**） |
| `5ca9ecbaf` | `fix(app): 修正 ConversationRepository 四个 offset 分页 API 的 LoadParams 类型（Refresh→Append…）` | **功能（生产代码 `app/src/main`，`ConversationRepository.kt` +21 / −6）** + JVM 测试（`app/src/test` 新增 `ConversationPageLoadParamsTest.kt` +135 / −0） |
| `5c362851a` | `coder: 修正 C1 续跑设备测试的错误节点断言为同节点候选替换语义` | 测试（`app/src/androidTest/.../C1GroupRetryResumeDeviceTest.kt`，**+119 / −40**；`git show --name-only` 里 `src/main` 命中数 = **0**，**生产代码零改动**） |

⚠️ **本批对契约验收证据的影响 = 零**：契约 `:206` / `:232-235` 点名的四类产物（各 viewer 可见消息 ID / 实际模型调用序列 / prompt+completion token / 导出 SHA-256）**一份未增**（无设备）。⇒ **20 个状态格一个判定都没改**：C1-01 / C1-02 / C1-03 / C1-04 / C1-05 各两格仍 `verified`、C1-06 / C1-07 / C1-08 / C1-09 / C1-10 各两格仍 `unverified`（逐格理由见 ⑥）。

**① 新基线（`c1_doc_stats.py` 主命令本机实测，本轮零 gradle 重跑）**

- `:app:testDebugUnitTest` = **115 类 / 944 例 / 0F0E0S**（XML 与源码 `@Test` 两口径一致）；较上一基线的 **+1 类 / +5 例全部来自 `ConversationPageLoadParamsTest`**（`5ca9ecbaf`）。
- 交叉验证：XML **115 类** / 源码 **115 类**（差 0，容差 5）**一致**。
- `:ai:test` = **30 类 / 220 例**（本轮未动）。
- lint app = **error 0 / warning 581 / hint 6 = 587**；全模块 = **error 0 / warning 614 / hint 7 = 621**（本轮未动）。
- 仪器 `@Test`：**20 文件 / 82 例**（仅 tracked）；含 untracked `C1GroupUiE2EFixtureTest.kt`（1 文件 / 3 例）为 **21 文件 / 85 例**。⚠️ 本批两个设备侧测试改动（`0ae9f570a` / `5c362851a`）都改**既有文件**，仪器 `@Test` 条数不变。

**② 台账声明值：本轮必须变动（47 行 / 509 例 → 48 行 / 514 例）**

- 实测 `git log --name-only 37630adb4..HEAD -- 'app/src/test/*'` = **只有** `ConversationPageLoadParamsTest.kt`（`5ca9ecbaf` 新增）；XML `tests="5"`、源码 `@Test` = **5**（两口径一致）。
- ✅ **按本文件 `:621-624` 台账口径**（「正文点名引用过、且能在 XML 里对上」），本节把它写进正文后它便够格进表 ⇒ **主动补登**（不是被 FAIL 逼出来）。
- 改前：**47 行 / 509 例**；改后：**48 行 / 514 例**（`+1 类 / +5 例`）。
- ✅ 改后 `--only ledger` 实测：`台账行数 48 行 / 台账声明例数合计 514 例 / 逐行核对 48 行全部相等`，**OK 3 / WARN 0 / FAIL 0**。
- ⚠️ **先纠一处预测偏差**：交接方预期「`c1_doc_stats.py` 的 ledger 会 FAIL（报 `ConversationPageLoadParamsTest` 声明缺失）」。**实测不会 FAIL**——脚本 `ledger` 口径**只逐行核对「表内已列类」的声明例数 vs 源码 `@Test`**，**不检查「源码里有没有未列进表的类」**；所以未补登时它仍报 `47 行全部相等`、OK 3 / FAIL 0。补登是**按台账自身口径**主动做的。
- ⚠️ **历史笔误照记（不覆写）**：表内既有两处 `合计` 行分别声明 `506`（与逐行求和 `501` 差 `5`）与 `509`，本批**新增一条** `合计` 行按脚本口径写 `514`，旧行原文保留。
- ⚠️ `tools/verification/c1_doc_stats.py` **一个字节未改**：跑前 `sha256 = 238bb45f7035852acf3f27013bd5d3d6372191a4543d3297ef7044e3899f4321`，跑后**同值**。

**③ `5ca9ecbaf` = 生产缺陷修复：`ConversationRepository` 四个 offset 分页 API 的 `LoadParams` 类型（Refresh → Append）**

- **根因（与 ④ 同源，逐条本机读码核实）**：`ConversationRepository` 的四个 offset 分页 API 都构造 `PagingSource.LoadParams.Refresh(key = if (offset == 0) null else offset, loadSize = limit, placeholdersEnabled = false)` 直连 Room 生成的 `LimitOffsetPagingSource`。Room 2.8.5 的 `androidx.room.paging.util.RoomPagingUtil.getOffset(params, key, itemCount)` 对 `Refresh` 做「刷新窗口夹取」、对 `Append` 恒为 `key`：
  ```
  Prepend -> if (key < loadSize) 0 else key - loadSize
  Append  -> key
  Refresh -> if (key < itemCount - loadSize) key else max(0, itemCount - loadSize)
  ```
  于是只要 `offset >= itemCount - limit`，`Refresh` 就把 offset **静默夹到** `max(0, itemCount - limit)`，返回与上一页重叠的行。
- **改动（本机 `git show 5ca9ecbaf` 逐 hunk 核实）**：`ConversationRepository.kt` **3 个代码块**（`:134-143` `getConversationsOfAssistantPage`、`:174-183` `searchConversationsOfAssistantPage`、`:229-238` 私有 `loadConversationPage`——后者被 `:201-209` `getUnfiledConversationsOfAssistantPage` 与 `:211-219` `getConversationsOfFolderPage` 两个公开方法共用）统一改成 **`PagingSource.LoadParams.Append(key = offset, loadSize = limit, placeholdersEnabled = false)`** + 解释性 KDoc。**3 个代码块对应 4 个公开 offset 分页 API**。
- 新增 `app/src/test/java/heizige/kk/khatkit/app/core/data/repository/ConversationPageLoadParamsTest.kt`（**5 例**，本机核实 5 个 `@Test`）：① `refreshLoadParams_isClampedIntoLastWindow` ② `appendLoadParams_preservesOffsetEvenPastClampBoundary` ③ `refreshAndAppend_agreeInsideWindowAndDivergePastIt` ④ `repositorySource_usesAppendOnly`（源码护栏：`LoadParams.Append(` 恰 3 处、不含 `LoadParams.Refresh`）⑤ `repositorySource_delegatesNextKeyToRoom`（`nextOffset = result.nextKey` 恰 3 处）。⚠️ 该类用**反射真实调用 Room 静态方法** `RoomPagingUtil.getOffset`，因为 `app` 的 `testImplementation` 只有 junit（room-testing 仅在 `androidTestImplementation`），跑不起真实 Room 运行时。
- 调用点 `ConversationRoutes.kt`（`GET /api/conversations/paged?offset=&limit=`，默认 `offset=0` / `limit=20`，校验 `offset>=0`、`limit∈1..100`）**未改**；`nextKey` **未改**——Room `queryDatabase` 的 `nextKey = if (data非空 && size>=limit && offset+size<itemCount) offset+size else null`，改 `Append` 后 `getOffset==key` ⇒ `nextKey` 自然正确。
- **统一 Append 为何安全（转述执行者论证，未独立复现运行时）**：`CommonLimitOffsetImpl.load` 在 `itemCount == -1` 时走 `initialLoad`，**`initialLoad` 不强制 Refresh**；首屏 `offset=0` 时 `Append` 与 `Refresh` 都得 0。
- ⚠️ **影响面**：只影响 **HTTP 分页 API**，**不影响 `Pager` 驱动的 UI 列表**（`Pager` 首请求 Refresh、后续全 Append）。
- ⚠️ **未跑真机 / HTTP 集成测试**（本轮零设备）；「生产缺陷已修」由**源码 + JVM 反射语义 + 源码护栏**支撑，**不是设备实证**。

**④ `0ae9f570a` = C1-10 设备侧分页测试驱动缺陷修复（未真机跑）**

- ⚠️ **以下真机失败事实转述自真机第十五轮执行者报告，登记时未独立复现**：`C1GroupPagingAndFilterDeviceTest` 在 HEAD `37630adb4` 上 8 方法 **3 通过 / 5 失败**。通过 3 条 = `typeFilter_allChipsFilterIndividuallyAndRestoreExactly`（6.41s）、`typeFilter_chipSwitchNeverLosesData_dbCountsStable`（6.021s）、`mixedList_groupBadgeRendersOnlyOnGroupRows`（4.547s）；失败 5 条**均 `pagingSource_*`**：`:357` expected 15 but was 20、`:392` expected 55 but was 60、`:435` expected 25 but was 40、`:493` expected 55 but was 60、`:527` expected 25 but was 40。
- **旁证（转述）**：`setUp` 的 seed 断言（`PAGING_TOTAL == dbUnfiledIds.size`）通过；`run-as` pull 真机 DB 用 sqlite3 实测专属助手未归档 = **55 行**（GROUP=25 / DIRECT=30），全表 69；生产常量 `PAGE_SIZE=20` / `INITIAL_LOAD_SIZE=40`。
- **根因**：`loadPage()` 原来无论首屏还是续读全构造 `LoadParams.Refresh(key, loadSize, false)`；生产 `Pager` 首请求发 Refresh、后续页全发 Append。`Refresh(key=40, loadSize=20, itemCount=55)`：`40 >= 55-20=35` ⇒ offset 夹到 35，第二屏从 35 回读 20 行 ⇒ 重叠 / 超取（Room 算法同 ③ 引的那段）。
- **改法**：`loadPage()`（`C1GroupPagingAndFilterDeviceTest.kt` 内）改为 `key == null` → `Refresh`；否则 → `Append(key, loadSize, false)`（paging 3.5.1 的 `Append` 是三参）。**调用点零改动**（本机核实 `git show 0ae9f570a` 只动 `loadPage` 一个函数）；**关键断言一条未放宽**；3 条通过的方法原样未动。
- **JVM 复算（转述，未独立复现）**：用真实 Room 静态方法反射，5 条失败逐一复现（`pages=[40,20] cumulative=60`、`GROUP OLD pages=[20,20] sum=40` 等）；修正后 `pages=[40,15] cumulative=55`。
- **非空验证（转述）**：把 test4 第二屏期望改成 16、test6 群聊累计改成 26 → 编译通过；用真实 Room 算法求值得 `actual=15 → fixed PASS / mutated FAIL`、`actual=25 → fixed PASS / mutated FAIL` ⇒ 有判别力。还原后 `sha256sum -c` 通过。
- ✅ **本机独立复算一处**：修复版测试文件的 `sha256` 实测 = `3c009ee364cb4fc97e67e964918bbf4b560e184337132ed2b30cdef5a52c3df2`（与执行者报告逐字一致），且 `git diff HEAD -- 该文件` 为空（工作区即修复版）。
- ⚠️ **未跑真机**（无设备）⇒ 真机转绿是 **JVM 复算推断**，**不是设备实证**。

**⑤ `5c362851a` = C1-07 续跑错误节点断言修正（判定：测试期望写错，非生产缺陷）**

- ⚠️ **真机失败原文（2/2 稳定，转述）**：`java.lang.AssertionError: 阶段 1 的 b 错误节点必须原样保留` at `C1GroupRetryResumeDeviceTest.kt:378`。raw 实测（转述）：phase1 b 错误节点 id `8e0f0ff1-70ce-48b4-93c7-9add6fe052d6` 在 phase2 快照里消失，被新 b 真实消息 `b59d894a-8fcb-4024-b80e-07b6423fc3c6` 取代；其余期望全部满足：run1 `FAILED/role_failed/committed=[a]/skipped=[c]/spent=1741`、run2 `COMPLETED/committed=[a,b,c]/skipped=[]/spent=14819`、**同 run_token**（`604b0069-19b8-421f-a5e4-26c561d4f654`）、同 `started_at`、a 消息 id 不变（`cb45d9ef-1e79-4526-961b-fbfda5047a3e`）、b/c 各恰好 1 条、`spent 14819 == Σusage（1741+6539+6539）`。
- **判定依据（转述执行者的逐行读码结论，未独立复现）**：错误节点 `role=ASSISTANT`，走 `ChatManager.kt:625-638` → `messageRange = 0..<nodeIndex`；`messageRange` **唯一**用途是 `ChatManager.kt:906-912` 的 `subList`（只截喂模型的输入，**不删节点**）。产出经 `updateCurrentMessages`（`Conversation.kt:74-106`）按 index 对齐节点：新消息 id 不在 `node.messages` 里 → `add` + `selectIndex=lastIndex`；因 `messageRange` 排除 `nodeIndex`，新消息恰好落回 **index==nodeIndex 的同一个 `MessageNode`** ⇒ **既不是按 id 替换、也不是新节点，而是同一节点内追加候选分支 + 切 `selectIndex`**；旧错误节点没删（`ConversationRepository.kt:557-567` 存整份 `node.messages`），只是 `currentMessages`（`Conversation.kt:61-64` 只返回 `selectIndex` 那条）看不到。生产 KDoc `GroupRetryEntry.kt:18-23`/`:42-44` 明写这就是设计。契约 `:201` 只要求「单角色失败**记录**错误节点并停止该轮」——阶段 1 已记录；契约**没有**要求续跑成功后继续保留 FAILED 语义；`:204` 只要求同 round_id + 跳过已提交 turn，run2 完全满足。`reason` 被 `reclaimed`（`GroupTurnCoordinator.kt:87-95`）清空是正常设计。
- **改动（本机核实）**：**只改** `C1GroupRetryResumeDeviceTest.kt`（**+119 / −40**，`git show --name-only` 里 `src/main` 命中 **0**）：阶段 2 读**整棵树**（新增私有 `loadConversation` helper）；修正 P2-C 为正向断言组（`assertNotNull(keptErrorNode)` / `assertNotNull(errorNodeOwningNode)` / `assertTrue(errorSupersededByNewBInSameNode)` / `assertTrue(!errorNodeStillSelected)`）；**其余断言 P1-A..E、P2-A/B/D/F/G/H 与 viewer 台账断言一条未删未弱**；P2-G 新增前置断言（只增强）；**证据 JSON 改为先落盘再断言**（失败也能出报告）。
- **判别力验证（转述）**：临时 JVM 测试复刻同形状直接打生产 `Conversation.updateCurrentMessages`：修正形状 EXIT 0；反向改回旧形状 `assertTrue(current.any{errorId})` → EXIT 1。
- ⚠️ **未真机复跑**：本批零设备，修正后 **2/2 是否转绿未知**。
- ⚠️ **附带的过期 KDoc（未改，照记）**：`C1GroupRetryResumeDeviceTest.kt:107-110` 仍写「UI 没有重试入口」，但群聊**已有**续跑入口（`GroupRetryEntry.canResume` + `ChatList.kt:529-539`）——**KDoc 未随本批更新**。

**⑥ 判定影响：20 格逐格判定（本批全不升）**

⚠️ 20 格 = 用例矩阵 10 格 + 证据登记表 10 格；本轮**每一格都保留原文 + 就地追加「第二十批订正」块**（不覆写历史）。逐格按契约 `:206` / `:232-235` 核对，**0 格升级**：

| 用例 | 判定 | 不升理由（契约行号） |
|---|---|---|
| C1-01 三角色显式 @ | 不升（维持 `verified`） | 本批未碰 C1-01 路径；零设备、契约 `:206`/`:232-235` 四类产物零份；第十六轮已建立的 `verified` 未被本批触及 |
| C1-02 无 @ 的 pipeline | 不升（维持 `verified`） | 本批未跑真实网关 pipeline；`5ca9ecbaf` 改的是抽屉/HTTP 分页 API，与本行「调用序列与日志一致」无关 |
| C1-03 roundtable | 不升（维持 `verified`） | 本批未跑 roundtable；契约 `:206` 四类产物零份 |
| C1-04 vote | 不升（维持 `verified`） | 本批未碰 vote；维持第十四轮 `verified` |
| C1-05 轮次预算 | 不升（维持 `verified`） | 本批未碰；维持第十四轮 `verified` |
| C1-06 取消与超时 | 不升（仍 `unverified`） | 本批未碰；超时半结构性做不到（`ChatManager.kt:116`/`:1005`、`KhatKitApp.kt:344-352`），契约 `:201` 两半不齐 |
| C1-07 失败续跑/幂等 | 不升（仍 `unverified`） | `5c362851a` 只把 `:378` 断言改成「同节点候选替换 + 切 `selectIndex`」的**测试侧**形状，**未真机复跑**；契约 `:204` 幂等台账的真机实证仍零份 |
| C1-08 记忆隔离 | 不升（仍 `unverified`） | 本批未碰；契约 `:206` 仍缺模型序列 / token / 导出哈希三项 |
| C1-09 Tavern/QR 往返 | 不升（仍 `unverified`） | 本批未碰；酒馆本体 / `ACTION_SEND` / 相机扫码仍零份 |
| C1-10 单聊/群聊共存 | 不升（仍 `unverified`） | `0ae9f570a`（测试驱动）+ `5ca9ecbaf`（生产分页 API）已修 5 条失败，但**真机未复跑**、生产修复只影响 HTTP 分页 API、契约 `:232-235` 不认仅 UI/逻辑级证据、四类产物零份 |

**⑦ 诚实限制**

- 本批**零设备、零 `adb`、零仪器执行**——全部是「源文件 + JVM/文档」证据。
- `5ca9ecbaf` 的生产分页修复**未跑真机 / HTTP 集成测试**——「Room `getOffset` 对 `Append` 恒为 `key`」只有**反射语义 + 源码护栏**，真实 `LimitOffsetPagingSource` 的并发失效 / 快照一致性仍未验。
- `0ae9f570a` / `5c362851a` 两条测试修复**无真机结果**——「5 条转绿」「2/2 转绿」都是 JVM 复算 / 逻辑推断。
- 本批基线数字**未重跑 gradle**（沿用现有 XML / lint 报告）；`c1_doc_stats.py` 主命令实测见 ①。
- ⚠️ **明确区分三档**（本节的证据性质）：
  - **本机实测（登记子代理独立复现）**：`git rev-parse HEAD`、`git log --oneline 37630adb4..HEAD` 计数（6 / 0 merges）、`git show --numstat`（+29/−2、+21/−6、+135/−0、+119/−40）、`git show 5ca9ecbaf` 三处 `Refresh→Append` hunk、四个 API 定义与 `Append` 现状、`ConversationPageLoadParamsTest` 5 个 `@Test`、XML `tests=5`、仪器 `@Test` 计数、台账 48/514 与 doc_stats 三条退出码、修复版测试文件 `sha256` = `3c009ee3…`。
  - **转述执行者（未独立复现）**：真机第十五轮的 5 条失败原文 / 断言值、真机 DB 55 行、JVM 复算 `pages=[40,15]`、非空验证（改 16/改 26 的 PASS-FAIL）、`5c362851a` 的错误节点 raw 字段与逐行判定、测试文件里的 sha256 链。
  - **未复现**：真机转绿（无设备）、HTTP 集成、`5ca9ecbaf` 的运行时行为。

**⑧ 遗留清单同步（本批）**

- **第 50 条**（C1-10 `pagingSource_*` 5 失败 + DB 55 vs 累计 60/40）：**已修**（`0ae9f570a` 测试驱动 + `5ca9ecbaf` 生产 API），⚠️ **真机未复跑**——就地追加「已修 / 待真机复跑」注记。
- **第 49 条**（C1-07 稳定失败待判定）：判定 = **测试期望写错，已修**（`5c362851a`），⚠️ **真机未复跑**——就地追加注记。
- **生产分页缺陷**：`ConversationRepository` 四个 offset 分页 API 的 `LoadParams` 类型**已修**（`5ca9ecbaf`），⚠️ **真机未复跑**。
- **过期 KDoc（未改）**：`C1GroupRetryResumeDeviceTest.kt:107-110`「UI 没有重试入口」已过期（续跑入口已由 `d6494e49a` 落地）——**本批未改，留待后续**。

### C1 真机证据采集第十六轮（2026-10-07，HEAD `bd88aaff2`：C1-07 续跑幂等 + C1-10 分页筛选修复后重跑，8/8 与 1/1 全绿）

> ⚠️ **轮次命名说明**：本文档「C1 真机证据采集第 X 轮」是**真机采集**序列，上一轮为**真机第十五轮**（HEAD `37630adb4`，见上），本节据序为**第十六轮**。

🚨 **先说性质**：本批 = **一次真机采集窗口**。设备 **OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**（逐份证据 JSON `device` 字段实测值）。开工 / 收尾 HEAD 均为 **`bd88aaff2`**（`git rev-parse HEAD` 实测 = `bd88aaff2ae00574cc76badb562f05e3bc13a8df`）；**设备轮本身零 commit**（`git log --oneline bd88aaff2..HEAD` 实测为空）。构建 `:app:assembleDebug :app:assembleDebugAndroidTest` **BUILD SUCCESSFUL / exit 0**（15 executed / 383 up-to-date）；两个 APK `install -r -t` 均 `Success`。⚠️ `am instrument` 的 **shell 退出码即使失败也是 0**，判据只用日志正文 `OK (1 test)` / `FAILURES!!!`（遗留第 41 条）。

🚨 **结论**：① **C1-07 续跑幂等用例真机首次通过**（`C1GroupRetryResumeDeviceTest#retrySameRoundSkipsCommittedTurnsAndDoesNotDuplicateMessages`，日志 `OK (1 test)` / Time **13.108**），契约 `:204` + `:206`/`:232-235` **8 项齐备**；② **C1-10 分页筛选 8/8 全绿**（连跑两遍、每遍都 8/8），上一批的 5 条 `pagingSource_*` 失败**全部转绿**；③ **状态判定：C1-07 在「用例矩阵」与「证据登记表」两格升 `verified`（共 2 格）**，C1-10 仍 `unverified`（逐格见 ⑤）。

⚠️ **证据根目录**：`/tmp/opencode/c1-r10/`（仓库外）。本节所有 SHA-256 / 字节数由**登记子代理本机独立复算**（`sha256sum`），凡标「执行者报告」者为转述、登记时未独立复现。

**① C1-07 失败续跑/幂等——逐列 8 项契约字段**

> ⚠️ 8 项 = 契约 `client-changes.md:206` + `:232-235` 逐例要求的「commit、测试命令及退出码、设备/Android 版本、用例输入、各 viewer 的可见消息 ID、实际模型调用序列、prompt+completion token、导出 SHA-256」。

- **commit**：run 时 HEAD `bd88aaff2`（设备轮零 commit）。
- **命令·退出码**：`:app:assembleDebug :app:assembleDebugAndroidTest` exit 0，两个 APK `install -r -t` Success，`am instrument -w -r -e class 'heizige.kk.khatkit.app.feature.chat.C1GroupRetryResumeDeviceTest#retrySameRoundSkipsCommittedTurnsAndDoesNotDuplicateMessages' heizige.kk.khatkit.debug.test/androidx.test.runner.AndroidJUnitRunner`；shell 退出码不可判（遗留 41），日志判据 **`OK (1 test)` / Time 13.108**。
- **设备·Android**：OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`（报告 `device` 字段实测）。
- **用例输入**：`mode=pipeline`，conversation `a0e965f1-52f4-4103-b99e-604fce1710f8`，`round_id=round-832cd2cc-16a2-4ba1-acc8-3aa41b3bc127`，`token_budget_per_round=100000`，USER「请三位依次发言，每位一句话。」；mock provider `http://127.0.0.1:8766/v1`（`mock_openai_slow_cancel.py`，启动行 `fail_role='' empty_role='B' delays={'A':0.02,'B':0.5,'C':0.1}`；`enableAutoRetry=false`），开测前 `POST /__reset` → `{"reset":true,"cleared":[]}`（**只在 phase1 之前调一次，两 phase 之间不重注入**）。
- **viewer 可见消息 ID**（`viewer_visibility`，源生产 `GroupChat.visibleMessages`）：a=`[832cd2cc-…(USER), 9298d95f-58fd-4d2b-aa07-f7b08548a038]`（count 2）；b=`[832cd2cc-…, 9298d95f-…, b55251a7-1245-45ae-a58a-5ec76cf99d02]`（count 3）；c=`[832cd2cc-…(触发), b55251a7-…, f7d97c05-0079-49d2-a7ae-8f681db1259e]`（count 3）。
- **实际模型调用序列**（`actual_model_call_sequence`）：seq1 a `mock-retry-a 6186+22`（committed）、seq2 b `mock-retry-b 6217+322`（committed）、seq3 c `mock-retry-c 6517+22`（committed），顺序 **A→B→C**。⚠️ 报告内序列声明为**从落库盖章消息推导**（`scope_note`），但**主机侧 mock `requests.jsonl` 独立记录**：seq1 A（chunks=3 / total 6208）、**seq2 B `injected_empty_output:true`（content chunks=0）**、**seq3 B 正常（chunks=19 / total 6539）⇒ phase2 未重注入**、seq4 C（chunks=3 / total 6539）；两来源一致。
- **prompt+completion token**：phase1 `FAILED/role_failed/committed=[a]/skipped=[c]/spent=6208/error_message="本轮没有产出内容"`；phase2 `COMPLETED/reason=""/committed=[a,b,c]/skipped=""/spent=19286`；**`spent 19286 == a(6208)+b(6539)+c(6539)`**；`run_token` 两阶段相同 = `ef78e9f6-79fe-4ba2-9a41-4cbf75d02882`（`run_token_reused=true`）；a 消息 id 两阶段不变 = `9298d95f-58fd-4d2b-aa07-f7b08548a038`。
- **导出 SHA-256**：`c1-retry-export-x.jsonl` **5063 B / 5 行**，`be3709800ca2bcb64648567df415edc82137fa4e54a00422ea371f0ddbd1a2c1`（报告内 `export_sha256`；**本机 `sha256sum` 对 pull 到的文件复算一致**）。
- **证据文件**（`/tmp/opencode/c1-r10/pull/`，本机复算 SHA-256）：`c1-device-retry-resume-report-x.json` 12998 B `ddd52ad46538c4f59e2f43ad3db9b7bb3a7a2a204e9f8632bf9811e12acc13c7`；`c1-retry-raw-phase1-x.json` 1933 B `720a986e402922bae2625a012a1f0a0bbd16142cd304384dc1882a5128947a82`；`c1-retry-raw-phase2-x.json` 4322 B `46ac7fb9a25db1a22427f5a1ff8a361cc9bf30e4a1225b7734ab948ed43fed30`；`c1-retry-trace-x.txt` 1289 B `1e04e963b33d0d4e935f899380437fd6fb944d356df6bb8eb7aa470b1b65d284`；`c1-retry-export-x.jsonl` 5063 B `be3709800ca2bcb64648567df415edc82137fa4e54a00422ea371f0ddbd1a2c1`。
- **必须断言核心**（12 行 `assertions` 全部 `expected==actual`）：`P1 status=FAILED`、`P1 reason=role_failed`、`P1 committed=[a]`、`P1 skipped=[c]`、`P1 real speaker count=1`、`P2 status=COMPLETED`、`P2 committed=[a,b,c]`、**`P2 group_runs rows=1`**、`P2 a id stable`、`P2 spent=Σusage（19286）`、`P2 error node retained as branch=true`、`P2 error node superseded by new b (not selected)=true`。`observed`：`phase1_error_node_id=53cd5a6d-43ae-44c2-b236-f9fe65b7e41c`、`old_error_node_in_message_tree=true`、`old_error_node_selected_in_current=false`、`old_error_node_shares_node_with_new_b=true`、`new_b_message_id=b55251a7-1245-45ae-a58a-5ec76cf99d02`。⇒ **同群同一 `round_id` 只有一个运行实例（`group_runs rows=1`）、重试沿用同一 `run_token`、a 的已提交 turn 被跳过且消息 id 不变、不重复消息**——与契约 `:204` 逐字对应。
- ⚠️ **报告 `scope_note` 末尾那句「Device-side behaviour is NOT verified… (no device)」是报告源码内的历史固定文案，非本次事实**（本次为真机实测；该文件 `device.sdk=36`、`attempt=x` 等均为设备侧落盘）。**登记时不据此改判，也不改测试代码里这句**。

**② C1-10 单聊/群聊共存——`C1GroupPagingAndFilterDeviceTest` 8/8 全绿（两遍都 8/8）**

| # | 方法 | 结果 | Time（第二遍） |
|---:|---|---|---:|
| 1 | `typeFilter_allChipsFilterIndividuallyAndRestoreExactly` | `OK (1 test)` | 5.418s |
| 2 | `typeFilter_chipSwitchNeverLosesData_dbCountsStable` | `OK (1 test)` | 5.374s |
| 3 | `mixedList_groupBadgeRendersOnlyOnGroupRows` | `OK (1 test)` | 4.477s |
| 4 | `pagingSource_firstScreenUsesInitialLoadSize_thenPageSize` | `OK (1 test)` | 3.263s |
| 5 | `pagingSource_allPagesSumToDbCount_withoutDuplicates` | `OK (1 test)` | 3.020s |
| 6 | `pagingSource_typeFilterNarrowsWithinSql` | `OK (1 test)` | 1.223s |
| 7 | `pagingSource_filterAllArgumentIsEmpty_equivalentToSqlNoFilter` | `OK (1 test)` | 2.863s |
| 8 | `pagingSource_searchPathCarriesTypeAndPages` | `OK (1 test)` | 1.283s |

每条实测断言值（设备侧 `c1-round6-*.txt` 原文，`/tmp/opencode/c1-r10/ev-<method>/`）：

- #1 `chips_verified=ALL\|DIRECT\|GROUP\|DIRECT\|ALL`、`restored_equals_initial=true`（本机复算 `9b9ceb02e5d29ad4eccf3ae1965a049cab7efb4f755484f79b7057fd950b0d45`，280 B）。
- #2 `before=[DIRECT=30, GROUP=25, DIRECT=38, GROUP=31]`、`after=` 同值、`stable=true`（本机复算 `366005ee7e0205915d89ea93ff0be0bf24e5bd30ff4b003bfb392a6fa6ecdf15`，242 B）。
- #3 `group_a_badged=true, group_b_badged=true, direct_a_badged=false, direct_b_badged=false, badge_node_count=5`（本机复算 `6bb6cb87210e4cd255171ce5be63f04014550d730f477a39d75ae61778e75fa4`，125 B）。
- #4 `prod_page_size=20, prod_initial_load_size=40, total_rows=55, first_size=40, second_size=15`（**上一批失败点 `expected:<15> but was:<20>` 已消除**；本机复算 `8fbae6c8b614be7adcef21e0cef2a529b09b4ec02def6c666674265826a3a6a9`，110 B）。
- #5 `page_sizes=[20,20,15], loaded=55, distinct_ids=55, db_unfiled=55`（上次 `55 vs 60` 已消；本机复算 `6c5baf3cf8629f2772f7020d5cf827f15e70fa535b8c0fe5a22f13ba813bfb3b`，84 B）。
- #6 `group_page_sizes=[20,5], group_rows=25, direct_page_sizes=[20,10], direct_rows=30`（本机复算 `57a9e6edb1b88b96f95d3b6f0765f96a224c636437bc771af72b1d9bf02eee50`，103 B）。
- #7 `type_arg='', rows=55`（本机复算 `08ad79a460148b323859cfb9bb51e0110b174bb4074765113759edc2dbf02aeb`，41 B）。
- #8 `group_hits=25, group_page_sizes=[20,5], all_hits=55`（上次 `25 vs 40` 已消；本机复算 `361cd4c38fc6c03017e38b1462fb5cb83492ffbf3752db8bb6a5fba2a9b2e9cd`，73 B）。

⚠️ **上一批（第二十批）的修复由本批真机转绿证实**：`0ae9f570a`（设备侧 `loadPage()` 改首屏 `Refresh` / 后续 `Append`）+ `5ca9ecbaf`（生产 `ConversationRepository` 四个 offset 分页 API 改 `Append`）在 HEAD `bd88aaff2` 上使 5 条 `pagingSource_*` 由「真机失败」变「真机通过」。

**③ mock `requests.jsonl`（主机侧独立记录，本机实测 6 行）**

| seq | speaker | 注入 | content chunks | usage total | 说明 |
|---:|---|---|---:|---:|---|
| — | — | `__reset` | — | — | 开测前一次（再前面另有一次 setUp 的） |
| — | — | `__reset` | — | — | 共 2 次 `__reset` |
| 1 | A | 无 | 3 | 6208 | phase1 真实发言并提交 |
| 2 | B | `injected_empty_output:true` | 0 | 6217 | **零产出注入，只此一次** ⇒ 造出 phase1 的 `role_failed` |
| 3 | B | 无 | 19 | 6539 | phase2 未重注入，正常产出并提交 |
| 4 | C | 无 | 3 | 6539 | phase2 最后一位 |

mock 本体 `tools/verification/mock_openai_slow_cancel.py` 本机 `sha256sum` 复算 = `0f2af473f94931cf97843fee1d7ea41462b4dc94cfecfe4ab10bfd286d0d3fde`。

**④ 导出哈希复算（登记子代理本机 `sha256sum` 复算 == 报告 `export_sha256`）**

| 文件 | 实测 bytes | 实测行数 | 实测 SHA-256 | 报告 `export_sha256` | 一致 |
|---|---:|---:|---|---|---|
| `c1-retry-export-x.jsonl` | 5063 | 5 | `be3709800ca2bcb64648567df415edc82137fa4e54a00422ea371f0ddbd1a2c1` | 同 | 是 |

⚠️ 行数口径同第十四 / 十五轮：文件无尾随换行，`export_line_count` = `\n` 数 + 1（表头 1 行 + 消息数）。

**⑤ 判定影响：20 格逐格判定（2 格升级）**

⚠️ 20 格 = 用例矩阵 10 格 + 证据登记表 10 格；**每一格保留原文 + 就地追加「真机第十六轮订正」块**（不覆写历史）。逐格按契约 `:206` / `:232-235` 核对：

| 用例 | 判定 | 理由（契约行号） |
|---|---|---|
| C1-01 三角色显式 @ | 维持 `verified`（本批未碰） | 本批未跑显式 @ 路径；此前 `verified` 结论未被本批触及 |
| C1-02 无 @ 的 pipeline | 维持 `verified`（本批未碰） | 本批未跑真实网关 pipeline；维持第十五轮 `verified` |
| C1-03 roundtable | 维持 `verified`（本批未碰） | 本批未跑 roundtable；维持第十五轮 `verified` |
| C1-04 vote | 维持 `verified`（本批未碰） | 本批未碰 vote；维持第十四轮 `verified` |
| C1-05 轮次预算 | 维持 `verified`（本批未碰） | 本批未碰；维持第十四轮 `verified` |
| C1-06 取消与超时 | 不升（仍 `unverified`） | 本批未碰；超时半结构性做不到（`ChatManager.kt:116`/`:1005`、`KhatKitApp.kt:344-352`），契约 `:201` 两半不齐 |
| C1-07 失败续跑/幂等 | **升 `verified`**（矩阵格 + 证据表格各 1 格） | 契约 `:204`「同一群同一 `round_id` 只允许一个运行实例（持久化 run token + mutex）；重试使用同一 `round_id` 并跳过已提交 turn，避免重复消息」+ `:206`/`:232-235` 8 项齐备（真机 `OK (1 test)` / Time 13.108；`run_token_reused=true`、`group_runs rows=1`、a id 不变、spent==Σusage、导出哈希本机复算一致）；口径与第十五轮 C1-02/C1-03 升级一致（接受「命令日志 `OK (1 test)` + Time」作退出码证据） |
| C1-08 记忆隔离 | 不升（仍 `unverified`） | 本批未碰；契约 `:206` 仍缺模型序列 / token / 导出哈希三项，评估语义正交 / 不适用、待用户认可 |
| C1-09 Tavern/QR 往返 | 不升（仍 `unverified`） | 本批未碰；酒馆本体 / `ACTION_SEND` / 相机扫码仍零份 |
| C1-10 单聊/群聊共存 | 不升（仍 `unverified`） | 契约 `:232-235`「只看截图或**只看 UI 状态**均标记 `unverified`」：本批 #1/#2/#3 断言落在 Compose 语义树可达性与 badge 节点（UI 状态）；#4–#8 虽为 `PagingSource`/SQL/DB 行数（非 UI），但契约 `:206` 逐例点名的「各 viewer 可见消息 ID / 实际模型调用序列 / prompt+completion token / 导出 SHA-256」这条路径**结构性不产出**（语义正交，同 C1-08 处置），缺失不等于满足 ⇒ 维持 `unverified` |

**⑥ 诚实限制**

- **C1-07 只有一次真机全绿**（本批 attempt `x`；trace 里另有 vfy1/vfy2 两次成功记录同形状历史）。⚠️ 升级依据是**契约 8 项齐备 + 必须断言成立**，不是重复稳定性（与第十五轮 C1-02/C1-03 同一口径）。
- **C1-10 的 8/8 是自动化断言，不是真机 UI 录屏**：#1/#2/#3 走 Compose 语义树（UI 状态），#4–#8 走 `PagingSource`/SQL/DB；**契约 `:206` 的四类产物本例不产出**（不启发模型、不导出群聊文件），故不升级。**若用户认可「该路径四类产物不适用」，可另行审议升格**（文件惯例：未经用户认可不擅自升）。
- **C1-10 的 `scope_note`**：无。**C1-07 报告 `scope_note` 的历史固定文案不代表本次事实**（见 ① 末条）。
- 本节所有 SHA-256 / 字节 / 行数为**登记子代理本机独立复算**；设备侧原始文件在仓库外 `/tmp/opencode/c1-r10/`。**未跑 gradle**（数字转述执行者）。

**⑦ 遗留清单同步（本批）**

- **第 49 条**（C1-07 设备侧幂等稳定失败）：**✅ 已修（`5c362851a`）且本批真机重跑通过**——不加新注（就地追加见遗留清单）。
- **第 50 条**（C1-10 `pagingSource_*` 5 失败）：**✅ 已修（`0ae9f570a` + `5ca9ecbaf`）且本批真机 8/8 全绿**。
- **第 45 条**（零产出间歇性）：本批未碰，不动。

### C1 真机证据采集第十七轮 / C1-09 收尾批次（2026-10-07，HEAD `e2bfce4ad`：真 SillyTavern 服务器应用级导入验证 + 二维码位图真机往返 + 二维码中文有损缺陷修复；台账第二十二批）

> ⚠️ **轮次命名说明**：本文档「C1 真机证据采集第 X 轮」是真机采集序列，上一轮为**真机第十六轮**（HEAD `bd88aaff2`，见上）。本批含**真机采集**（二维码位图往返真机 6 条 + 字符集修复真机复跑），故据序为**第十七轮**；同时它也是「C1 commit 台账」的**第二十二批**（实施状态侧第二十七批）。

🚨 **先说性质**：本批 = **9 个 commit 的混合批次**（`git log --oneline ae08f2525..HEAD` 实测 9 个、`--merges` = 0、`git rev-parse HEAD` = `e2bfce4adbf2ca03195c4030b9763a6a71af37aa`）。内容 = ① 纯 JVM 测试（零生产改动）② 真 SillyTavern 服务器**应用级**导入验证（宿主，仓库外）③ **本批唯一生产改动**（`QRCode.kt` 抽出可测函数）④ **二维码位图端到端真机往返**（设备 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`，6 条 `OK`）⑤ **二维码非 Latin-1 有损缺陷修复**（含真机非空验证）。⚠️ **本批不重跑任何 gradle**（所有数字由执行者给出）；登记子代理**独立复算**了仓库内文件的 `sha256`、`@Test` 计数、`git log` 计数（见各条「登记子代理实测」）。

**① C1-09③ 可纯代码部分落测（`28c05f2db`，+372/−0，仅测试、生产零改动）**

- 新增 **2 个 `app/src/test` 类**（`git log --name-only ae08f2525..HEAD -- 'app/src/test/*'` 实测只有这两个新文件）：
  - `app/src/test/java/heizige/kk/khatkit/app/core/data/model/C1GroupQrPayloadCodecRoundTripTest.kt`（**5 例**，登记子代理 `grep -c '@Test'` 实测 5）：二维码**载荷**的字符串 ↔ 配置往返 —— `encodeQr -> importShare` 逐字段（`schema_version` / `mode=roundtable` / `chair_role_id=r3` / `token_budget_per_round=1234` / **roles 顺序**（刻意乱序 `r2,r1,r3`）/ chair 位 / 整体相等）；角色卡最小元数据往返（显式断言中间卡 `cardId==null`、`avatarRef==null` 仍是 null，而非字符串 `"null"`）；`decodeSharePayload` 逐字段；`decodeQr` == `decodeSharePayload().config`；二次往返 `encode->decode->encode` **字节相等**。
  - `app/src/test/java/heizige/kk/khatkit/app/feature/chat/GroupExportShareIntentSourceGuardTest.kt`（**5 例**，实测 5，**源码护栏**）：`shareFile`（`ConversationExport.kt:860-872`）的 `ACTION_SEND` / `type = mimeType` / `EXTRA_STREAM` / `FLAG_GRANT_READ_URI_PERMISSION` 各恰一次 + `createChooser` + `chat_page_export_share_via`；`writeExportTempFile`（`:836-852`）`FileProvider.getUriForFile` 恰一次、authority 按包名派生恰一次；`ExportHooks.exportAndShare`（`:42-63`）四项各恰一次 + authority；含**反空跑保护**（扫描器仍能找到东西）。
- **5 组破坏全真红 + 逐字节还原**（执行者报告）：①`GroupChat.kt:349` `chair_role_id`→`_BROKEN` → 4 failed；②`encodeConfigObject` 的 `config.roles`→`.sortedBy{it.id}` → 3 failed；③`decodeCards` 的 `card.string("card_id")`→裸 `content` → `ComparisonFailure …[null]… but was:…["null"]…`；④`shareFile` 的 `addFlags(FLAG_GRANT_READ_URI_PERMISSION)`→`addFlags(0)` → `expected:<1> but was:<0>`；⑤`ExportHooks` `type="application/json"`→`"text/plain"` → 同上。
- **为什么不做位图往返**（本批前）：编码 `String->Bitmap` 当时内联在 `QRCode.kt:28-40` 的 `@Composable` 里、无可独立调用 API；解码唯一入口是 MLKit（需 Android 运行时）；仓库 `testImplementation` 只有 junit（`app/build.gradle.kts:343`）、无 Robolectric。⇒ 位图往返留给 ④ 的真机 `androidTest`。

**② 真 SillyTavern 服务器应用级导入验证（`5833cbbd9` / `c21cc4028`）**

- **环境**：`git fetch --depth 1` 钉死 commit **`06bde939fb1e9c4c8d8641d810f0a916b5bce127`**（与仓库既有登记同 SHA）、`LICENSE` = **AGPL-3.0**（sha256 `8486a10c4393cee1c25392769ddd3b2d6c242d6ec7928e1414efff7dfb2f07ef`）；`npm install` 829 包 exit 0；`node server.js --port 8123` 起真服务，`GET /version` → `{"gitRevision":"06bde93","pkgVersion":"1.19.0"}`；默认 `enableUserAccounts: false` ⇒ **免登录**，CSRF 开启（无 token `POST /api/ping` = **403**，带 token = 204）。⚠️ **SillyTavern 本体只在 `/tmp/opencode/sillytavern-server/SillyTavern`，未入库**（登记子代理 `git ls-files` 实测：仓库内**没有任何** SillyTavern 源码，只有一个 HTTP harness）。
- **导出文件**（登记子代理实测）：`app/build/c1p-group-export-hash/pipeline_3roles_2rounds_jsonl.txt` **3615 B** `36e6585f9aa4a028eb8277bc70578fd2c802cdd730e3981f0d52b02430f0c8b9`（本机 `ls -la` 实测 **3615 字节**）；`/tmp/opencode/c1-device-r7/phaseA-pull/c1-device-export-pipeline.jsonl` **2938 B** `4945b85426def123b7bc07382dbf03977c35908650a114ffac7cf782f9976f4b`（仓库外）。
- **真实 HTTP 结果**：`POST /api/chats/group/import`（`multipart/form-data`，`file_type=jsonl`）→ **HTTP 200** `{"res":"2026-10-07@10h35m51s091ms"}`（两份各一次）；落盘字节 **3615 / 2938**，与源文件**逐字节相同**（真 copy）；`POST /api/chats/group/get` → 200，golden 9 对象 / device 8 对象（表头+消息）；`POST /api/chats/group/info` → 200，`{"chat_items":8, "mes":"伽马：汇总结论 \"采纳 a\" 🎓", …}`。
- **服务端解析正确**：条数 / 顺序 ✅、`name` 归因（`阿达`/`阿尔法`/`贝塔`/`伽马`/`多数决`）✅、`is_user`（阿达=True）/ `is_system`（全 False）✅、`chat_items` 排除表头（8/7）✅。
- **私有顶层键 `khatkit_group` / `khatkit_character_names` 被保留、不报错**（`/group/get` 原样回传）；无 `send_date` 时 `getChatInfo` 回退文件 mtime 作 `last_mes`。
- **野生 jsonl 行为**：未注册时 `/api/groups/all` = []、`/api/chats/search` 与 `/api/chats/recent` **都查不到**、客户端也打不开（`group-chats.js:2195` `openGroupChat`，注册闸门 `:2199`）；最小注册（`POST /api/groups/create {chats:[id], chat_id:id}`）后群出现、`chat_size=3615`、`/api/chats/search{group_id}` 返回 `message_count=8`。
- **open→save 往返**：`POST /api/chats/group/save` → 200 `{"ok":true}`；文件 **3615 B → 2230 B**；重存首行 = `{"chat_metadata":{"is_group":true,"integrity":"<uuid>"},"user_name":"unused","character_name":"unused"}` ⇒ **确认丢私有表头块**（`khatkit_group` / `khatkit_character_names` / `spec` / 原 `user_name` / `character_name`）。
- ⚠️ **推翻一个预置假设（本批关键订正）**：**重存文件回 KhatKit 不是 `NoConfig` 而是 `Unsupported`** —— `TavernChatCodec.importGroup` **`:298`** `payload = document.header[GROUP_FIELD] as? JsonObject ?: return null`（表头无 `khatkit_group` 直接 `return null`），调用方 `GroupTavernImport.kt:176-180` 映射为 `TavernGroupImportOutcome.Unsupported`（`isError=true`）。`NoConfig` 只在「`khatkit_group` 键在、但里面没 `config`」的畸形文件上可达。**登记子代理实测**：`TavernChatCodec.kt:298` 与 `GroupTavernImport.kt:169/177` 现文逐行核实；JVM 测试级证据 `GroupTavernImportTest` 的 `a file without the khatkit_group block at all is not a config problem`（`app/src/test/java/heizige/kk/khatkit/app/feature/chat/GroupTavernImportTest.kt:217`）**PASS**。⚠️ **未做到**：用 JVM 对「重存后的真实字节」直跑 `importGroup`（离线取 `debugUnitTestRuntimeClasspath` 被 AGP 9.3.1 变体消解挡住）。
- 本 clone 实测行号（执行者报告，登记子代理未逐行复核）：`chats.js` `getChatData`=577、`/group/import`=751、`/group/get`=872、`/group/save`=922、`/search`=949、`/recent`=1054、`getChatInfo`=393；`util.js` `tryParse`=571；`group-chats.js` `getGroupChat`=255（`chat_metadata`:268、`shift`:272、`splice`:305）、`saveGroupChat`=623、`openGroupChat`=2195（闸门:2199）。
- **交付物**：`tools/verification/verify_sillytavern_import.py`（入库，登记子代理实测 337 行 / sha256 `9c0bfcc1403ba23ae8c4370164a3fde7e38f9049406f2a082c85cbfee2d0b672`）—— 脚本头写明钉死 commit 与 AGPL-3.0；**只走 HTTP、不拷贝任何 SillyTavern 源码进仓库**；自助取 CSRF / 存 cookie、导入 / 回读 / 注册 / 往返全流程，**39/39 断言通过、退出码 0**，可重复运行（`c21cc4028` 把野生 jsonl 判定改为成员检测以支持重复运行）。

**③ `QRCode.kt` 抽出可测函数（`f35c8ccf0`，本批唯一生产改动，行为逐字不变）**

- `app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/QRCode.kt`：新增顶层 `internal fun encodeQrBitmap(value, size, foregroundColor, backgroundColor): Bitmap`，把原先内联在 `@Composable` 里的 `QRCodeWriter().encode(...)` + 逐像素写入逻辑**原样搬出**；`@Composable QRCode(...)` 对外签名不变，改为调用点算 `toArgb()` 传入并 `remember(value, size, foregroundArgb, backgroundArgb)` 缓存。调用方（`GroupChatPage.kt` / `GroupExportCard.kt`）**零改动**。唯一语义差异：去掉 `remember { QRCodeWriter() }`（writer 改在函数内新建，`encode` 是纯函数）。**登记子代理实测**（`read` 全文 + `grep`）：`QRCode.kt` 现文 `:73-88` 即 `encodeQrBitmap`，`:33-40` 的 `remember` 键为 `(value, size, foregroundArgb, backgroundArgb)`，逐像素写法与 `createBitmap` 一致。

**④ 二维码位图端到端真机往返（`5f461d9f6` / `16d00e9f4`）**

- 新文件 `app/src/androidTest/java/heizige/kk/khatkit/app/core/ui/components/ui/C1GroupQrBitmapRoundTripDeviceTest.kt`（登记子代理实测 **6 个 `@Test`**），**6 个方法全部真机 `OK (1 test)`**（设备 OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`）：
  1. `qrPayloadSurvivesBitmapRoundTripThroughMlkitAndDecodesFieldByField`（0.705s）—— 生产 `encodeQr` → 生产 `encodeQrBitmap` → **MLKit** `BarcodeScanning.getClient(FORMAT_QR_CODE)` + `InputImage.fromBitmap(bitmap,0)`（`Tasks.await(...,30s)`）→ 解出串**逐字等于** `encodeQr` 输出 → `decodeSharePayload` 逐字段断言（含 roles 顺序、中间卡 `cardId=null`/`avatarRef=null` 仍是 null）→ `importShare` 放行且 `config`/`cards` 相等
  2. `nonLatin1ContentIsLossyUnderCurrentEncoderCharset`（0.744s）—— 当时钉住字符集限制（**后被 ⑤ 改成正面断言**）
  3. `samePayloadDecodesIdenticallyAtDifferentSizes`（1.715s）—— 512/1024/1536 三个 size 解出同一串
  4. `largeRealisticPayloadStillFitsWithinQrCapacityAndRoundTrips`（1.283s）—— 3 张卡各 550 字符、载荷 ~2.7KB
  5. `oversizedPayloadIsRejectedByEncoderInsteadOfSilentlyTruncated`（0.049s）—— 3000 字符夹具断言 zxing 抛 `WriterException`，不静默截断
  6. `exportTempFileUriIsReadableAndItsBytesEqualProductionJsonl`（0.114s）—— 生产 `writeExportTempFile` → `contentResolver.openInputStream(uri)` 真读 → 字节**逐字节等于** `TavernChatCodec.exportGroupJsonl`；authority = `包名.fileprovider`；**不弹分享面板**，只对同形 `Intent` 断言 `action` / `type=application/json` / `EXTRA_STREAM` / `FLAG_GRANT_READ_URI_PERMISSION`
- 真机证据已 pull（执行者报告，仓库外）：`/tmp/opencode/qr_evidence/c1-qr-bitmap/c1-qr-bitmap-roundtrip.json`（`raw_bytes=1159`、`decoded_equals_raw=true`、bitmap 1024×1024 ARGB_8888）与 `…-sizes.json`。
- **4 组破坏全真红 + 逐字节还原**（执行者报告；还原后 sha256 与基线相等）：①`QRCode.kt` `BarcodeFormat.QR_CODE`→`DATA_MATRIX` → `IllegalArgumentException: Can only encode QR_CODE, but got DATA_MATRIX`；②`GroupChat.kt` decode `roles.sortedBy{it.id}` → `expected:<[r2, r1, r3]> but was:<[r1, r2, r3]>`；③`ConversationExport.kt` 写盘后追加 1 字节 → `AssertionError: 通过 FileProvider 读回的字节必须逐字节等于生产 JSONL`；④测试内 Intent 去掉 `addFlags` → `AssertionError: 必须带 FLAG_GRANT_READ_URI_PERMISSION…`。④ 阶段还原后 sha256：`51815ee8b01747eb7ed6da63064dc74e7e0cd3dd5d1b6a5123d6780ba9d2485d`（QRCode.kt，**⑤ 修复前**）/ `0f61ffdd2c63a0ecfcfc670e341db4e87b581bd4bfe2337c65e14895b935c8f9`（GroupChat.kt）/ `b6b821271ddb79dda6c19e68d7784e254a62c11a620c56bcf3e7fb410fa5b775`（ConversationExport.kt）/ `d4382815754d6d03d9af2241788628750409b44c64b849cab73eeacec39f1815`（测试文件，**⑤ 改动前**）。**登记子代理实测**（当前 HEAD）：`GroupChat.kt` = `0f61ffdd…` ✅、`ConversationExport.kt` = `b6b82127…` ✅（两者 ⑤ 未改动，与基线逐字节相等）；`QRCode.kt` 现为 ⑤ 修复版 `ecb78fc6…`（见下）；测试文件现为 ⑤ 版 `134f7763a05a7eb3d26d78a6f9c36839b79a678beb16e517ee376935c38cc85d`（**与 ④ 基线的 `d4382815…` 不同，因 ⑤ 改了它**）。

**⑤ ⭐ 二维码非 Latin-1 有损缺陷已修（`0edc70d67` / `e35f64e39` / `e2bfce4ad`）**

- **缺陷**：`QRCode.kt` 的 `encode` **未设** `EncodeHintType.CHARACTER_SET`；zxing 3.5.4 默认按 **ISO-8859-1**。JVM 探针实测 `{"persona":"热血解说"}` → `{"persona":"????"}`，`EQUAL=false`。
- **修复**：`QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))`（加 `import com.google.zxing.EncodeHintType`，KDoc 记录副作用）。**登记子代理实测**：`QRCode.kt` 现文 `:16` 有 `import com.google.zxing.EncodeHintType`、`:79` `val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")`、`:80` 5 参重载；文件 sha256 = `ecb78fc67438298aedac48bb7d5afd8c65a8c7618ca9fe8b53b816d70bace849`（与执行者给的「修复版 sha256」**逐字节相等**）。
- **实测副作用**：UTF-8 会加一个 **12 bit ECI 头**（`Encoder.appendECI`），恰好吃掉 1 字节字节模式容量 ⇒ **v40-L 有效容量 2953 → 2952 字节**（二分实测：带 hint 时 `2953` 抛 `WriterException`、`2952` 成功且为 v40/185 模块）。中文夹具编码字节 883 → 931。
- **测试改动**：`nonLatin1ContentIsLossy…` → `nonLatin1ContentRoundTripsVerbatimThroughBitmapAfterUtf8Fix`（正面强断言）；容量常数 `qrV40LCapacityBytes` **2953 → 2952**（**收紧不是放宽**）；夹具改为含 `热血解说`。**未新增 `@Test`**（例数不变，仍 6 条）。
- **真机**：6 条全 `OK (1 test)`，全类复跑 **`OK (6 tests)`**；新增证据 `c1-qr-bitmap-charset.json` 实测 `raw_bytes=931`、`decoded_equals_raw=true`、`raw==decoded=true`、`热血解说` 与 `🔥` 均在 raw/decoded、`decoded` 不含 `????`。
- **非空验证（关键发现）**：删掉 hints 那行 → 重装主 APK → 中文用例 **`FAILURES!!!` 连续 4/4**，首个断言 `AssertionError: MLKit 未能从生产位图解出任何二维码`；同期 ASCII 用例在同一回退 APK 下仍 `OK (1 test)`（排除环境噪声）。⇒ **旧编码产出的中文二维码 MLKit 直接扫不出来，比「变问号」更严重**；JVM 探针另证该二维码本身可被 zxing 解出且内容为 `????·??`。`cp` 还原 + `sha256sum -c` 成功 + `git diff` 为空 → 重装 → 用例 `OK (1 test)`（双侧验证）。
- **设备还原**（执行者报告）：`screen_off_timeout=30000`、`stay_on_while_plugged_in=0` ✓。

**⑥ 证据分级（登记时必须保留）**

| 级别 | 内容 |
|---|---|
| **应用级已证** | ST@`06bde939` / AGPL / 起服务 / 免登录+CSRF、`import` 接受（**字节级 copy**）、服务端解析（条数 / 顺序 / `name` / `is_user` / `is_system` / `chat_items`）、私有键保留、野生 jsonl 不出现在 search/recent、最小注册后群出现、open→save 丢私有块 |
| **JVM 测试级 + 源码级** | 重存文件 → `null` → `Unsupported`（`GroupTavernImportTest` 对应用例 PASS + `TavernChatCodec.kt:298` / `GroupTavernImport.kt:176-180` 现文核实） |
| **真机自动化** | 二维码位图端到端往返 6/6（生产 `encodeQr`→`encodeQrBitmap`→MLKit 解码→逐字段→`importShare`；FileProvider URI 字节 == 生产 JSONL）；⑤ 修复后真机复跑 6/6 + 非空验证 4/4 失败 / 还原后通过 |
| **源码级（未在浏览器执行）** | 客户端 `getGroupChat` / `openGroupChat` 闸门 |
| **未验** | `NoConfig` 分支本身（真实服务器）；SillyTavern 多用户 / 开启登录 / `--listen` 模式；**真实相机扫码路径**（`QrScannerSheet` 的 CameraX `analyzeFrame` 的 `mediaImage`，与位图路径只差图片来源）；**真实系统分享面板 UI 交互** |

**⑦ 判定影响：20 格逐格判定（本批 0 格改值）**

⚠️ 20 格 = 用例矩阵 10 格 + 证据登记表 10 格；**每一格保留原文 + 就地追加「第二十二批订正」块**（不覆写历史）。逐格按契约 `:205` / `:206` / `:232-235` 核对（判定结论表见下方「第二十二批订正」块内）。

- **C1-09（本批重点）判定：不升，仍 `unverified`**。理由逐条（引契约）：
  1. 契约 `:206` 逐例要求的 8 项里，本行**结构性不产出**「各 viewer 的可见消息 ID」「实际模型调用序列」「prompt+completion token」三类（Tavern/QR 往返不启发模型、无 viewer 过滤语义）——按本文件对 C1-08 / C1-10 的既有处置（「缺失不等于满足，语义正交需用户认可才另议」），**未经用户认可不得据此升格**。
  2. 契约 `:205`「导入先 schema 校验与去重，再创建新 conversation；恢复失败不留下半成品会话」——**App 侧真实用户链路（相机扫码 → `QrScannerSheet` → `importShare` / `importGroup` → 建会话）从未在设备上跑过**；④ 的位图往返用的是**生产函数**，但绕过了相机 `analyzeFrame`（`mediaImage`）这一段，两者只差图片来源**却恰是未验的那一段**。
  3. 契约 `:232-235`「只看截图或只看 UI 状态均标记 `unverified`」+ 判定规则第九条「源码护栏永远不能顶替验收证据」——ACTION_SEND 的验证是**源码护栏（5 例）+ 同形 `Intent` 断言**，**真实系统分享面板 UI 交互零份**（⑥ 明标「不弹分享面板」）。
  4. 酒馆侧虽已到**应用级**（真服务器 import 接受 + 解析正确 + 私有键保留），但 `open→save` 丢私有块、重存文件回 KhatKit 得 `Unsupported` ⇒ **「打开并回导」的闭环不成立**；且 **SillyTavern 多用户 / 开启登录 / `--listen` 模式未验**。
  5. **升格还差什么（逐条）**：㈠ 真机走一次**相机扫码**（CameraX `analyzeFrame` 的 `mediaImage`）→ `importShare` 的完整链路；㈡ 真机点一次**系统分享面板**并取回产物（或明确用户认可「同形 Intent + 源码护栏」等价）；㈢ 用户对「本行 `:206` 三类产物结构性不适用」作出与 C1-08/C1-10 同口径的**明确认可**；㈣（可选、加强）SillyTavern `--listen` / 多用户下的导入复现。
- **C1-01..08、C1-10：全部维持原判、0 格改值**（本批未触碰各自点名的路径；C1-01/02/03/04/05/07 维持 `verified`、C1-06/08/10 维持 `unverified`）。⚠️ **实际改判定值 = 0 格**（Python 逐格比对结论见下）。

> ⚠️ **改判定值 = 0**：本次未对任何状态格改值，20 格全部为「保留原值 + 就地追加订正块」。用 Python 逐格比对（改前 / 改后各提取状态格首词）实测：20 格状态值**逐格相同**，`changed=[]`。

**⑧ 诚实限制**

- ② 的所有 HTTP 数字（200 / 3615 / 2938 / 2230 B / `message_count=8` 等）与 ④/⑤ 的真机 Time / JSON 字段为**执行者报告**，登记子代理**未复现**（本机无设备、无 `node`）；登记子代理**实测**的是：`git log` 9 commit、两个 `app/src/test` 新文件与计数、`androidTest` 文件 6 `@Test`、`verify_sillytavern_import.py` 存在（337 行 + sha256）、`pipeline_3roles_2rounds_jsonl.txt` 3615 B、`QRCode.kt` / `GroupChat.kt` / `ConversationExport.kt` 三份 sha256 与两条源码行号。
- ④ 的「还原后 sha256 `51815ee8…`/`d4382815…`」是**⑤ 改动前**的历史值，登记子代理**未复现**（当前工作树已被 ⑤ 改写）；**当前** HEAD 三份生产文件 sha256 已实测并逐条列出。
- **本节只登记，不改任何生产代码**。

### C1 真机证据采集第十八轮（2026-10-07，HEAD `2a0610705`：**C1-06 超时半条首次真机通过 + C1-08 串扰断言结构性化并证判别力 + C1-10 真实网关轮与点名路径正交**）

⚠️⚠️ **先说性质**：本批 = **一次真机采集窗口**（OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`）+ 1 处生产改动 + 1 处 mock 改动 + 2 处测试改动。开工 / 收尾 HEAD = **`2a0610705`**（`git rev-parse HEAD` 实测 = `2a0610705f3f71ff2702d656f340155e60fcfc2d`，与登记目标一致）。窗口 `e2bfce4ad..2a0610705` 实测 **17 个 commit**（`--merges` = 0）。**本批三条裁定：C1-06 升 `verified`、C1-08 升 `verified`、C1-10 维持 `unverified`**；状态列 20 格改值 **4 格**（C1-06 / C1-08 各在两表内的状态格，从 `unverified` → `verified`）。

**① C1-06「取消与超时」升 `verified`**

- **生产改动（`4af7a66be`）**：`ChatManager` 步超时改为可注入 —— 构造函数末尾加 `private val groupRoundStepTimeoutMs: Long = GROUP_ROUND_STEP_TIMEOUT_MS`，`withTimeout` 调用点（登记时实测 `ChatManager.kt:1014`）改用 `groupRoundStepTimeoutMs`。**默认值常量一字未改**（`ChatManager.kt:116` `private const val GROUP_ROUND_STEP_TIMEOUT_MS = 15 * 60 * 1000L`）；`AppHiltModule.provideChatManager` 用命名实参且不传该项 ⇒ 生产行为与改前**逐字一致**（登记子代理读码核对）。
- **mock 改动（`4c71b7d80`）**：`tools/verification/mock_openai_slow_cancel.py` 新增 `MOCK_HANG_ROLE` 档位（200 SSE opener 后**不发任何 content**，挂住到客户端取消；`_respond_hang` bounded 到 `MOCK_HANG_SECONDS`，默认 120s）；`MOCK_HANG_ROLE` 默认 `""` = 关闭 ⇒ 既有取消证据逐字节不变。**新 sha256 `8f976a63557c8775e19ab04a3f5f2049f814e99641c90ee5db1a0a165e5da54a`**（登记子代理本机 `sha256sum` 实测 = 该值）。
- **超时半条（`eb614914b`）**：`C1GroupCancelDeviceTest#stepTimeoutRecordsTimeoutWithoutEmptyBubbleAndKeepsGeneratedRole`，真机 `am instrument` **`OK (1 test)` / `Time: 7.606`**（日志 `/tmp/opencode/instr-timeout1.log`；按本文既定口径，`am instrument` 的 shell 退出码即使失败也恒 0，故以日志 `OK`/`Time` 作「命令及退出码」证据 —— C1-01/02/03/04/05 升格时同此口径）。注入 `injected_step_timeout_ms=5000`，精确实测 `elapsed=6634ms`。
- **超时半条断言实测**（逐字段读 JSON，登记子代理本机核对）：`status=TIMEOUT` / `reason=timeout`；**空气泡数 `empty_bubble_count=0`**；库内 **3 条** = `user` + `a`(speaker) + `b`(error)；a 的 speaker 消息保留（`wire_model_name=mock-cancel-a`、usage `6190+22=6212`）；错误节点 `role_id=b` / `turn_kind=error` / 正文含 `TimeoutCancellationException: Coroutine "AppScope" timed out waiting for 5000 ms`；`spent_tokens=6212 == 6190+22`；`committed=["a"]` / `skipped=[]`。
- **证据哈希**（本机 `sha256sum` 复算）：`c1-device-timeout-report-timeout1.json` **3744 B / `b7c68d4aff7607f3c01afcc378e8ace8feac1426759da7bb3b0a6def61bbbade`**；raw `c1-timeout-raw-timeout1.json` **`f465e77c297d97329a7b45f9468ffc0fc74ae028919f289f5804c7f609565586`**。
- **契约 `:206` / `:232-235` 8 字段（取消半 + 超时半合并看）**：见下方「⑤ 裁定表」。⚠️ 超时半本身**不产出** viewer 台账与导出 SHA-256（单角色超时、不导出群文件）；这两格由**取消半条**（`9d7429216`，`c1-device-cancel-report-vfy1.json`：viewer 台账 a/b/c + `actual_model_call_sequence` 4 条 + `export_sha256=853033a8…` 2977 B/7 行，登记于「C1 真机证据采集第十五轮」①#3）提供。契约按「C1-06 这条用例整体」判定 ⇒ **8 字段齐**，且 `:201`「取消/超时不得写入未生成的消息」两半均有真机实测 ⇒ **升 `verified`**。

**② C1-08「记忆隔离」升 `verified`**

- **串扰断言的结构性替换（`2a0610705`）**：旧断言 `roles.filter{…}.none{ chunk.content.contains(tokens[other]) }` 因检索 query 改用 **fact 正文**、真实模型会改写正文并丢掉 `C1RGWA/B/C` 哨兵而**对三角色恒真（空泛）**。本批**保留**它但就地标注「⚠️当前空泛…勿据此判绿」，并新增纯函数 `memoryIsolationViolations(...)`：只看**归因字段** —— 空间键 `== group:<conv>:role:<viewer>`、`role_id == viewer`、`source_message_id ∈ viewer 窗口`、不得指向他人 own message、全局/助手空间零回退。
- **判别力已证非恒真**：JVM 镜像测试 `C1MemoryIsolationPredicateTest`（`app/src/test/.../feature/chat/`，**7 例**）持有判据的**逐字镜像**（`androidTest` 与 `test` 两个 source set 无法互相 import，两处 KDoc 已标注；登记子代理实测两函数体逐字一致、仅差文末换行）。把镜像改成 `return emptyList()` → **`7 tests completed, 6 failed`**（`/tmp/opencode/jvm_mut.log:399`、`BUILD FAILED`）；还原后本机 `sha256sum` = **`703fe46d7143d2faed8ceb93be57e607fde7c410e0da80c7c52852b8e0f56d12`**（与 `C1Mirror.sha` 一致）。
- **真机重跑**：`C1LiveModelSequenceTest#realProviderGroupMemoryIsolationRecordsPerSpaceHits`，`OK (1 test)` / `Time: 18.577`（日志 `/tmp/opencode/instr_p2.log`）。证据 `c1-live-evidence-memory-isolation-real-gateway.json` sha256 **`8944b371abd1f75433288fe050984892ee503d025cb28d88462602c36e2f0b52`**（本机复算）。三角色命中全为本人（a 2 条、b/c 各 1 条）；`global_fallback_source_hits=0` / `assistant_fallback_source_hits=0`。
- **8 字段**：commit `151b45115` + `389f263d6` + `2a0610705`；命令/退出码 `OK (1 test)` / `Time 18.577`；设备 PKG110 / 16 / 36 / arm64-v8a；输入 = fixture（a/b/c 三条带哨兵正文 + 触发消息 `0c1c11ae-…-d018`）；viewer 台账 a/b/c；调用序列 `actual_model_call_sequence` 3 条真实 `glm-5.2`（`all_extraction_attempts` 4 条含 1 条 `EOFException` 失败）；token `sum_prompt_plus_completion=1196`；导出 `export_sha256=7fe07a7c2d30425cb338b9e4146a9e90d039534428cbebf948954a00fae339ab`（2804 B / 5 行）。
- **`389f263d6` 三处放宽的定性（本批重点，逐条）**：
  - **换模型 flash → glm-5.2**：与隔离断言**正交**。契约 `:203` 只约束空间键 / 归因 / 无回退，不约束抽取模型；glm-5.2 与 flash 都是生产表里的真实模型。换模型的理由是**真实可复现的模型行为**（flash 在 `maxTokens=512` 下 reasoning 吃满配额、`finish_reason=length` 返回空 content，抽取写不进 fact），**不是为让断言变绿挑模型**。
  - **检索 query 由哨兵改 fact 文本**：使检索**真的命中**；代价是旧哨兵断言变空泛（已由结构性判据替换）。**未削弱隔离主张**。
  - **≤6 次重试**：**不削弱断言** —— 最终仍强制「每角色成功且写入 ≥1 条」，且**逐次尝试全落盘**（`all_extraction_attempts` 含失败现场）；6 次是**有界**上限。⚠️ 但它**暴露**生产 `MemoryExtractor.extractFromTurn` **静默吞掉**瞬态失败（只 `Log.w`）—— 这是**独立的产线健壮性缺口**，不影响本例判定。
  - ⇒ **结论：三处放宽属夹具层为让真实抽取真的产出内容，不构成「测试被改弱以迁就模型输出」**。⚠️ 残留风险已记：判据是**逐字镜像**（两处需同步、有漂移风险）；上述静默吞异常是独立缺口。
- 结合契约 `:203`（空间键 / 归因 / 无回退）全数满足 + `:206` 8 字段齐 + 判别力已证 ⇒ **升 `verified`**。

**③ C1-10「单聊/群聊共存」维持 `unverified`（真实网关轮与点名路径正交）**

- **新增第 9 条（`7da57ece4`）**：`C1GroupPagingAndFilterDeviceTest#realGatewayRoundInFixtureGroupProducesContractArtifacts` —— 在**既有夹具群 `groupAId`（`0c1c0de5-…-f3a1`）**上真跑一轮三方 pipeline，产出 viewer 台账 / 调用序列 / token / 导出哈希。真机 `OK (1 test)` / `Time: 17.418`（日志 `/tmp/opencode/instr_c110d.log`；证据 `c1-round6-real-gateway-green.json` sha256 **`5618f22ac41463211a47bebbb593fa3e33b0437784e744ff559bbbdf67c4ec33`**；`status=COMPLETED` / `committed=a,b,c` / 3 次真实调用 flash→glm-5.2→flash / `sum_prompt_plus_completion=20540==spent` / `export_sha256=a5c373d5…`）。⚠️ 该方法**前 3 次尝试均受阻于 `createEmptyRule` / 前台化竞态**（`instr_c110.log` `performMeasureAndLayout called during measure layout`；`instr_c110b/c.log` `Process crashed`），**第 4 次**才拿到干净 `OK` ⇒ **超了 ≤3 上限一次**，如实登记。
- **裁定（登记子代理读码后裁）**：登记子代理**逐行读** `C1GroupPagingAndFilterDeviceTest.kt`（1433 行）确认：第 9 条只 `openGroupChat(groupAId)` 后直接 `sendMessage` 跑生成轮，**不打开抽屉、不点 chip、不驱动 PagingSource**；而前 8 条（`typeFilter_*` / `mixedList_*` / `pagingSource_*`）才走「混排 / 类型筛选 / 分页」。⇒ 第 9 条的 8 字段来自**群聊生成路径**，**与 C1-10 点名的「单聊/群聊筛选/混排」路径正交**，是平行挂载的产物，**不能**把筛选/混排断言从 UI 级抬成非 UI 级。筛选/混排本身仍只有 Compose 语义树 / chip 状态 / badge / PagingSource 页大小 / SQL 谓词 / DB 行数 —— 契约 `:232-235` 明写「只看截图或**只看 UI 状态**均标记 `unverified`」。⇒ **维持 `unverified`**。
- **还差什么**：该 8 字段须来自**筛选/混排路径本身**（或用户对「本例 8 字段结构性不适用」的明确认可），而非平行挂载的一轮群聊生成。⚠️ 另：第 9 条 4 次才绿，超出 ≤3 重试上限一次。

**④ 基线（本批实测，旧值作废）**

- `:app:testDebugUnitTest` = **118 类 / 961 例 / 0F0E0S**（`app/build/test-results/testDebugUnitTest/TEST-*.xml` 求和实测；口径 ①② 一致）。
- `:ai:test` = **30 类 / 220 例**。
- lint：app **0 error / 617 warning / 6 hint = 623**；全模块 **0 / 650 / 7 = 657**。
- 仪器 `@Test`：**tracked 22 文件 / 96 例**；含 untracked **23 文件 / 99 例**（untracked = `C1GroupUiE2EFixtureTest.kt` 3 例）。
- `c1_doc_stats.py`：主命令 **18 OK / 0 WARN / 0 FAIL**；`--self-test` / `--only tables` 均 **exit 0**。脚本 sha256 **仍为 `238bb45f7035852acf3f27013bd5d3d6372191a4543d3297ef7044e3899f4321`（一字节未改）**。

**⑤ 裁定表（契约 8 字段逐格）**

| 用例 | 裁定 | commit | 命令·退出码 | 设备·Android | 输入 | viewer 可见消息 ID | 调用序列 | prompt+completion | 导出 SHA-256 |
|---|---|---|---|---|---|---|---|---|---|
| C1-06 | **升 `verified`** | `9d7429216`（取消半）+`eb614914b`（超时半） | 取消 `OK (1 test)`/Time 4.292；超时 `OK (1 test)`/Time 7.606 | PKG110/16/36 | 取消输入 + 超时 `超时用例：请三位依次发言…` | **取消半** a/b/c 台账；超时半结构性不产出 | 取消半 4 条 mock；超时半 a `mock-cancel-a` | 取消 6258；超时 6212=6190+22 | **取消半** `853033a8…` 2977 B/7 行；超时半不导出 |
| C1-08 | **升 `verified`** | `151b45115`+`389f263d6`+`2a0610705` | `OK (1 test)`/Time 18.577 | PKG110/16/36 | fixture a/b/c 正文 + 触发消息 | a/b/c 台账（生产 `visibleMessages`） | 3 条真实 glm-5.2（+1 条 EOF 失败在 `all_extraction_attempts`） | 1196 | `7fe07a7c…` 2804 B/5 行 |
| C1-10 | **维持 `unverified`** | 第 9 条 `7da57ece4` | 第 9 条 `OK (1 test)`/Time 17.418（第 4 次才绿） | PKG110/16/36 | 第 9 条 `请三位依次发言，每位一句话。` | 第 9 条 a/b/c 台账 —— **但与筛选/混排路径正交** | 第 9 条 3 条真实调用 —— **同上正交** | 20540 —— **同上正交** | `a5c373d5…` —— **同上正交** |

### C1 真机证据采集第十九轮（2026-10-07，HEAD `1758e2109`：**C1-10 第 9 条改造为「筛选路径内」真跑一轮，8 字段首次由筛选路径打开的群产出 ⇒ C1-10 升 `verified`**）

⚠️⚠️ **先说性质**：本批 = **一次真机采集窗口**（OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`）+ **2 处 androidTest 改动**（`C1GroupPagingAndFilterDeviceTest.kt` 第 9 条：`df4c70f09` 改造 + `1758e2109` 加固），**生产代码 / 工具 / `app/src/test` 一字未动**。开工 / 收尾 HEAD = **`1758e2109`**（`git rev-parse HEAD` 实测 = `1758e2109b6fef396c361c5c87dc923f16c019e0`，与登记目标一致）。窗口 `2a0610705..1758e2109` 实测 **4 个 commit**（`--merges` = 0；含上一批留到本批补登的登记本体 2 个）。

**① 独立核实（登记子代理本机读码 + 复算，逐条；不采信转述）**

核对对象 `app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1GroupPagingAndFilterDeviceTest.kt`（1612 行，worktree sha256 实测 = `a164cce4cdb36831ae0d27b86861d92dd2e55ce0c59a03b9259eec114f838e09`，与 `/tmp/opencode/c110/pristine_C1GroupPagingAndFilterDeviceTest.kt` 及 `git show HEAD:` 两处**逐字节相同**）：

- **确实点了 chip（不是直接读 DB）**：第 9 条 `:675` `openGroupChat(directAId)`（从**单聊甲**进入，非目标会话）→ `:676` `openDrawer()` → `:687` `clickChip(CHIP_GROUP)`；`:690-695` 等「群在且两个单聊都不在」→ `waitForIdle`；`:698-704` 断言筛选态（单聊不可达 / `countInDrawer`=0 / 两群可达 / 群徽标在）。✅
- **`openedGroupId` 来自「筛选后的首屏」**：`:713-719` 用**生产同一条 DAO 查询** `database.conversationDao().getUnfiledConversationsOfAssistantByType(currentAssistantId, GroupChat.TYPE_GROUP)` 经 `loadPage(prodInitialLoadSize)` 取首屏；`:726-728` `filteredGroupIds = filteredGroups.map{it.id}`、`firstFilteredGroup = filteredGroups.first()`、`openedGroupId = Uuid.parse(firstFilteredGroup.id)`。✅
- **`∈ filteredGroupIds` 断言存在**：`:730-733` `assertTrue("...必须确实在该筛选结果里...", openedGroupId.toString() in filteredGroupIds)`。⚠️ **诚实标注**：因 `openedGroupId` 就是 `filteredGroups.first().id`，此 `∈` 断言**按构造恒真**（弱断言）；真正把「DAO 首屏」缝到「UI 列表」的是 `:742` `assertReachable(list, openedTitle)`（生产 DAO 取到的标题在**抽屉列表**里真能滚到），以及 `:747-758` 的点开 + 导航断言。✅（弱断言如实记）
- **确实是在抽屉里点这一行导航的，且有导航结果断言**：`:747-749` `compose.onNode(hasAnyAncestor(hasTestTag(DRAWER_LIST_TAG)) and hasText(openedTitle)).performClick()`；`:752` `waitUntil { currentChatPageId() == openedGroupId.toString() }`；`:753-758` `navigatedId = currentChatPageId()` 并 `assertEquals(openedGroupId, navigatedId)`；`:759-763` 再断言群信息胶囊可见。`currentChatPageId()`（`:1176-1190`）是**反射读生产 `RouteActivity` 的 `NavViewModel.currentPage`（栈顶）**，不是测试自造状态。✅
- **前 8 条断言未被削弱**：`git diff df4c70f09^ HEAD -- <该文件>` 的 515 行 diff **全部落在 `@@ -619` 之后**（hunk 起点 `619 / 654 / 1013 / 1401`，即第 9 条 KDoc / 体、工具函数、helper 新增），**前 8 条 `@Test` 方法（`:227-587`）与常量（`:145-190`）逐字节未动**。`df4c70f09 → 1758e2109` 的加固（364 行 diff）也不是削弱：唯一改动是把 `runBlocking` 收窄到局部、把「只等单聊不在」加严成「群在且两个单聊都不在」、并新增 `DIRECT_A`/`DIRECT_B` 基线与 `performScrollToNode`。✅

**证据 SHA-256（登记子代理本机 `sha256sum` 复算，`/tmp/opencode/c110/pull/`）**：

- `c1-round6-real-gateway.json`，**4758 B**，sha256 = `335eb60a1c8d6fcfa3c42c4ff4ecf094e015a401983ef8eb674ff2ddb0100480` ✅ 与报告给值**逐字符相同**。
- `c1-export-c1-10-real-gateway.jsonl`，**2331 B**，sha256 = `66d3b9cefb8cfceb0f2e97a49c2f35aa4e63cee6fc175f3cbb9a7838862e2ce5` ✅ 与报告给值**逐字符相同**。
- 两份文件 mtime = 15:03:14；`pristine_...kt` 快照 15:03:27；commit `1758e2109` 时间 15:09:36 ⇒ 证据取自**与最终提交逐字节相同**的测试代码（运行在前、提交在后，属正常顺序）。

**JSON 四者关系（登记子代理逐字段读）**：`opened_from_filtered_list=true`；`opened_group_id = navigated_chat_id = conversation_id = 0c1c0de5-0000-0000-0000-00000000f3a1`（即 `groupAId`）；`filtered_group_ids_before_open` 首条即 `…f3a1` ⇒ **`opened_group_id ∈ filtered_group_ids_before_open` 成立**，且 `opened_via = "drawer:chip(群聊)→row(openedTitle)→navigateToChatPage→currentPage.id"`。四者自洽。✅

**绿日志**：`/tmp/opencode/c110/c1-c110-green-instrument-run2.log`（**880 B**）实测含 **`OK (1 test)`** 与 **`Time: 19.62`**，与 commit `1758e2109` 标题「真机 OK(1 test) 19.62s」一致。⚠️ **命名订正**：绿日志文件实际名为 `c1-c110-green-instrument-run2.log`，**没有**名为 `c1-c110-green-instrument-1758e2109.log` 的文件（同目录另有 `c1-c110-green-instrument.log`，894 B / `Time 17.418`，对应加固前 `df4c70f09` 版）。

**② C1-10 裁定：升 `verified`**

- **契约 `:232-235`**：判 `unverified` 的三种情形是「测试未运行 / 只看截图 / **只看 UI 状态**」。本用例已是「**UI 操作（点 chip、点抽屉行）+ 生产 DAO（PagingSource 同源查询）+ 生产导航状态（`NavViewModel.currentPage` 反射）+ 真网关产物（真实调用序列 / token / 导出哈希）**」的混合体，**不再「只看 UI 状态」** ⇒ **越过该线**。
- **上一批（第二十三批）不升的唯一理由已被本次改造直接消掉**：旧第 9 条 `openGroupChat(groupAId) → sendMessage` **不点 chip、不驱动筛选**，8 字段与筛选路径正交；本次第 9 条先在**筛选路径内**抵达群（点 chip → 生产 DAO 首屏 → 抽屉点击 → 导航断言），**再在这个从筛选结果打开的群上真跑一轮** ⇒ 8 字段**由筛选路径所选中的群产出**，不再是平行挂载。见上方①逐条核实。
- **契约 `:206` 8 字段（逐项出处与值）**：

| # | 字段（`:232-235`） | 值 / 出处 |
|---|---|---|
| 1 | commit | `1758e2109`（加固版）+ `df4c70f09`（改造版）—— **JSON 未内嵌，由登记子代理从 `git log` 补记** |
| 2 | 测试命令及退出码 | `am instrument -w -r -e class 'heizige.kk.khatkit.app.feature.chat.C1GroupPagingAndFilterDeviceTest#realGatewayRoundInFixtureGroupProducesContractArtifacts'` → 输出 **`OK (1 test)`** / **`Time: 19.62`**（`c1-c110-green-instrument-run2.log`；按本文既定口径，`am instrument` 的 shell 退出码即使失败也恒 0，故以日志 `OK`/`Time` 作「命令及退出码」证据，同 C1-01..08 升格口径）—— **JSON 未内嵌，由登记子代理补记** |
| 3 | 设备 / Android 版本 | JSON `device_model=PKG110` / `device_sdk=36` / `device_abi=arm64-v8a` |
| 4 | 用例输入 | JSON `input_user_text="请三位依次发言，每位一句话。"`（`TRIGGER_TEXT`） |
| 5 | 各 viewer 可见消息 ID | JSON `viewer_visibility`：a 2 条、b 3 条、c 3 条；`predecessor_id` a=null / b=a / c=b，`visible_assistant_role_ids` 各为 `[a]` / `[a,b]` / `[b,c]` |
| 6 | 实际模型调用序列 | JSON `actual_model_call_sequence`：a `deepseek-v4-flash`、b `glm-5.2`、c `deepseek-v4-flash`（wire 名与解析名一致，`turn_kind=speaker`） |
| 7 | prompt+completion token | JSON `sum_prompt_plus_completion = 20537`；三发言 `6804+100`、`6619+43`、`6854+117`；`group_run.spent_tokens=20537`（**相等**）、`status=COMPLETED`、`committed_role_ids=a,b,c`、`skipped=[]` |
| 8 | 导出 SHA-256 | JSON `export_sha256=66d3b9cefb8cfceb0f2e97a49c2f35aa4e63cee6fc175f3cbb9a7838862e2ce5`（2331 B / 5 行；`export_sha256_source` = 生产 `TavernChatCodec.exportGroupJsonl` 字节 + 生产 `writeExportTempFile` 落地） |

- **诚实边界（一并记）**：① 8 字段里的 viewer 集合 / 调用序列 / token / 导出哈希在**语义上仍是「群聊生成」的属性**；本次改造保证的是**这一轮跑在「由类型筛选打开的那个群」上**（`opened_via` 明记），而非「筛选算法本身被这些字段验证」。② `∈ filteredGroupIds` 断言按构造恒真（见①弱断言标注）。③ 用例对「筛选结果首条必须是本案夹具群」有依赖（本轮首条 = `f3a1`），见遗留 56。④ 第 9 条 `:735-739` 有防御断言「首条 ∈ {groupAId, groupBId}」，故不会误打用户真实群。
- **⇒ 裁定：升 `verified`**（用例矩阵 + 证据登记表两格 `unverified` → `verified`）。

**③ 统计口径（登记子代理本机实测）**：

```
git rev-parse HEAD                                      # 1758e2109b6fef396c361c5c87dc923f16c019e0
git log --oneline 2a0610705..HEAD | wc -l               # 4
git log --oneline --merges 2a0610705..HEAD | wc -l      # 0
git log --oneline e2bfce4ad..HEAD | wc -l               # 21
git log --name-only 2a0610705..HEAD -- 'app/src/test/*' # （空）
```

- **本批只有 androidTest 改动**（`df4c70f09` / `1758e2109` 均只动 `C1GroupPagingAndFilterDeviceTest.kt`），`app/src/test/*` **无任何新增 / 修改** ⇒ 台账声明值**保持 51 行 / 531 例**（上一批刚由 `C1MemoryIsolationPredicateTest` 7 例推到 51/531）。锚点 `1b0e04a9` **不重算**（仍 `78 / 0 merges`）。
- `c1_doc_stats.py`：主命令 **18 OK / 0 WARN / 0 FAIL**；`--self-test` / `--only tables` 均 **exit 0**；脚本 sha256 **仍为 `238bb45f7035852acf3f27013bd5d3d6372191a4543d3297ef7044e3899f4321`（一字节未改）**。
- 仪器 `@Test`：本轮未新增 / 删除 androidTest 文件与方法（只改第 9 条方法体），tracked **22 文件 / 96 例**、含 untracked **23 文件 / 99 例** 不变。
- 基线（沿用上一批实测，本批零 JVM 改动）：`:app:testDebugUnitTest` **118 类 / 961 例 / 0F0E0S**；`:ai:test` **30 类 / 220 例**；lint app `0 / 617 / 6 = 623`、全模块 `0 / 650 / 7 = 657`。

**④ 遗留（登记子代理本机实测 / 报告，逐条）**

- **设备 `settings.preferences_pb` 仍含历轮测试用的 `realProvider` 与 3 个测试助手**（mtime 14:47，**早于本轮**）—— 属设备侧遗留，**本轮未清**；见遗留 55。
- **第 9 条依赖「筛选结果首条必须是本案夹具群」**（本轮 = `f3a1`）—— 稳健性注意项；见遗留 56。
- **本轮测试首次尝试遇「网关流挂死」**：连接建立后消失、无判定、测试进程 **0% CPU 卡在 `stopGeneration` 的 `jobs.join()`**，**非断言失败**；`/tmp/opencode/c110/instrument-run1.log`（432 B）只有 `INSTRUMENTATION_STATUS_CODE: 1`、无后续 ⇒ 与「挂死」现象一致；见遗留 57。

**⑤ 裁定表（契约 8 字段逐格）**

| 用例 | 裁定 | commit | 命令·退出码 | 设备·Android | 输入 | viewer 可见消息 ID | 调用序列 | prompt+completion | 导出 SHA-256 |
|---|---|---|---|---|---|---|---|---|---|
| C1-10 | **升 `verified`** | `1758e2109`+`df4c70f09` | `OK (1 test)`/Time 19.62 | PKG110/16/36 | `请三位依次发言，每位一句话。` | a/b/c 台账（JSON `viewer_visibility`） | 3 条真实调用 flash→glm-5.2→flash | 20537=Σusage | `66d3b9ce…` 2331 B/5 行 |

### C1 真机证据采集第二十轮（2026-10-07，HEAD `83dbf014f`：**C1-09 同源往返用例首次把「真机一轮」的三个产物与同群 QR 载荷硬连接；但真实系统分享面板仍零份 ⇒ C1-09 维持 `unverified`**）

⚠️⚠️ **先说性质**：本批 = **一次真机采集窗口**（OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`）+ **2 处 androidTest 改动**（`C1LiveModelSequenceTest.kt`：`766c61434` 新增方法 + `83dbf014f` 对偶发 `role_failed` 整轮重试并解析导出 `mes` 字段比对），**生产代码 / 工具 / `app/src/test` 一字未动**（`git log --name-only` 实测两个 commit 均只动该 androidTest 文件）。开工 / 收尾 HEAD = **`83dbf014f`**（`git rev-parse HEAD` 实测 = `83dbf014f95c4da58ee136a124149fcf17c94e25`，与登记目标一致）。窗口 `1758e2109..83dbf014f` 实测 **4 个 commit**（`--merges` = 0；含上一批留到本批补登的登记本体 2 个 `2c304efe3` / `ffc3989e5`）。⚠️ **轮次命名**：含真机采集，故「C1 真机证据采集」据序为**第二十轮**；同时按「C1 commit 台账」批次序记为**第二十五批**（本节即实施状态侧的**第三十批**）。

**① 独立核实（登记子代理本机读 JSON / 复算 sha256，逐条；不采信转述）**

新方法 = `heizige.kk.khatkit.app.feature.chat.C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`（源码 `app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt:3473`）。

证据（`/tmp/opencode/c1-09-evidence-final/`，登记子代理本机 `sha256sum` / `stat -c%s` 复算，**与报告值逐字符相同**）：

| 文件 | 字节 | SHA-256 |
|---|---:|---|
| `c1-live-evidence-real-qr-export.json` | 8711 | `51d42e0f55a5fdb0c389d7bd0393717b0a9528f649a645311ecb6d8270f22f8d` |
| `c1-export-real-qr-export.jsonl` | 2314 | `97f2c2cafc13144c8dffd090808a3ceac1b0729d85e7107013910b9f967f87c0` |
| `c1-real-raw-dump-qr-export.json` | 8240 | `00b56308701176bc2e5d3a347ded2a5a78d1c71d9c92c115b8eb616bca0a3cc6` |

⚠️ `c1-export-real-qr-export.jsonl` **无尾随换行**（`wc -l` = 4，解析后实际 5 行，与 JSON `export_line_count=5` / `expected_line_count=5` 一致）。

契约 8 项（**逐项从 JSON 实读**，非照抄）：

| # | 字段（`:232-235`） | 值 / 出处（登记子代理实读） |
|---|---|---|
| 1 | commit | JSON `commit = 83dbf014f95c4da58ee136a124149fcf17c94e25`（=`git rev-parse HEAD`） |
| 2 | 测试命令及退出码 | JSON `test_command = C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr`；完整命令 `am instrument -w -r -e class 'heizige.kk.khatkit.app.feature.chat.C1LiveModelSequenceTest#realRoundIsBothExportedAsTavernJsonlAndEncodedIntoSameGroupQr' heizige.kk.khatkit.debug.test/androidx.test.runner.AndroidJUnitRunner`；⚠️ JSON `test_command_exit_code="0"` 是 `am instrument` 的 shell 退出码（**本文件既定口径：该退出码失败也恒 0**），真正判据是**日志 `OK (1 test)` / `Time: 11.046`**（⚠️ **转述执行者**：该日志文件未随证据目录提供，登记子代理**未能复算**） |
| 3 | 设备 / Android 版本 | JSON `device`：model `PKG110` / sdk `36` / abi `arm64-v8a`；`device_android = PKG110 / Android 16 / API 36 / arm64-v8a` |
| 4 | 用例输入 | JSON `case_input`：conv `e3b44bc2-2da2-4033-b83a-f1f4bee60796`、round `round-0910af13-b626-4370-9c3d-88e1e5a29078`、触发 user `0910af13-…`「请三位依次发言，每位一句话。」、`mode=pipeline`、`chair_role_id=c`、`token_budget_per_round=100000`、`network_setting_enable_auto_retry=false`；3 角色 wire 名 `deepseek-v4-flash` / `glm-5.2` / `deepseek-v4-flash` |
| 5 | 各 viewer 可见消息 ID | JSON `viewer_visibility`：a `[0910af13…, 620d2ae7…]`、b `[0910af13…, 620d2ae7…, 04a4840c…]`、c `[0910af13…, 04a4840c…, dcf268e6…]`；`predecessor_id` a=null / b=a / c=b；`visible_assistant_role_ids` `[a]` / `[a,b]` / `[b,c]` |
| 6 | 实际模型调用序列 | JSON `actual_model_call_sequence`：`deepseek-v4-flash → glm-5.2 → deepseek-v4-flash`；三条 `wire_model_name_provenance` **均 `wire_response_model`**（raw dump `wire_model_name_provenance_counts={wire_response_model:3, uuid_reverse_lookup_fallback:0}`） |
| 7 | prompt+completion token | JSON `prompt_completion_tokens`：a `6829+54=6883`、b `6597+55=6652`、c `6892+53=6945`；`sum_prompt_plus_completion=20480`（raw dump `group_run.spent_tokens=20480`，**相等**）、`status=COMPLETED`、`committed_role_ids=[a,b,c]`、`skipped=[]`、`reason=""` |
| 8 | 导出 SHA-256 | JSON `export.export_sha256=97f2c2ca…967f87c0`（**2314 B / 5 行**；`export_sha256_source` = 生产 `TavernChatCodec.exportGroupJsonl` 字节 + 生产 `writeExportTempFile`（`ConversationExport.kt:836`）落地并经 FileProvider URI 逐字节比对） |

同源关系（登记子代理实读）：调用序列 / token 来自**被导出同一轮**的助手消息（真库读回、网关真实 usage / wire 响应模型名）；导出哈希来自**该会话**；QR 载荷来自**同一群的 `stored.groupConfig`**。

QR 部分（JSON `qr_same_group`）：`raw_bytes=1018`、`decoded_equals_raw=true`、`payload_config_equals_stored_config=true`、`payload_config_equals_exported_config=true`、`import_share_accepted=true`；解码器 = MLKit `BarcodeScanning FORMAT_QR_CODE via InputImage.fromBitmap`（1024×1024 ARGB_8888）。

**② 这条证据的核心意义（消掉「`:206` 三类产物结构性不适用」这条旧理由）**

此前 C1-09 的不升理由之一是「C1-09 自己的测试不调用模型、不导出群会话 ⇒ 契约 `:206` 的三类产物（各 viewer 可见消息 ID / 实际模型调用序列 / prompt+completion token）结构性不产出」。**新方法与这三个产物是硬连接**：调用序列 / token 取自**被导出的同一轮**助手消息（真库读回、网关真实 usage / wire 名，见 ①#6/#7）；导出哈希取自**该会话**（`exportGroupJsonl` 生产字节，见 ①#8）；QR 载荷取自**同一群的 `groupConfig`**，且**强制断言**「QR 解出的 config == 导出会话的 config == 导出 JSONL 内嵌的 config」（测试 `:3690` / `:3691`）——破坏其中任一即真红。

**非空验证（转述执行者；登记子代理读码确认断言存在）**：把 QR 载荷换成不同 config（`tokenBudgetPerRound + 1`）→ 真机真红 `java.lang.AssertionError: QR 载荷里的群配置必须逐字等于导出会话的群配置 expected:<…100000…> but was:<…100001…>` / `Tests run: 1, Failures: 1` → `git checkout --` + `sha256sum -c` 还原成功 → 重跑 `OK (1 test)`。⚠️ 登记子代理**实测**：该断言在测试现文 `:3690`（`assertEquals("QR 载荷里的群配置必须逐字等于导出会话的群配置", storedConfig, payload.config)`），与所报错误文案**逐字一致**；真机真红现场**未能复算**（无设备）。

**偶发性的如实登记**：公网网关会瞬态返回空内容（`role_failed`），测试加了**最多 6 次整轮重试、只采信首个 COMPLETED 轮**（`MAX_ROUND_ATTEMPTS=6`，测试 `:5625`；与既有 C1-08 真实抽取段同口径）——**不是放宽断言、不伪造**；每次尝试都落 `trace`。登记子代理**实测 trace**（`/tmp/opencode/c1-09-evidence-final/c1-live-trace.txt`）：其中一次调用 `attempt=1 status=FAILED reason=role_failed commited=[a,b]` → `attempt=2 COMPLETED`（同一 trace 另含两次 `attempt=1 COMPLETED`）；**正式证据 JSON 对应 conv `e3b44bc2`**（该 trace 行 `attempt=1 COMPLETED`）。⚠️ 因此「attempt=1 失败、attempt=2 通过」是**该 trace 内可复算的一次现场**，而**正式证据**来自另一次 `attempt=1` 即绿的一跑。

**③ C1-09 裁定：维持 `unverified`（20 格 0 格改值）**

⚠️ **结论：本批不升。** 三条旧理由里 **`:206` 三类产物结构性不适用**（㈡）与**相机扫码 / 酒馆应用级**（㈢）本轮已消，但**真实系统分享面板（`ACTION_SEND` chooser）仍零份**，构成 C1-09 这条「导出/分享」用户链路最后一个未采集点。逐条依据（引契约行号）：

1. **契约 `:219`** 对 C1-U 的交付证据明写「**Compose/UI 测试 + 真机手动记录**；无第二套会话列表」——`ACTION_SEND` 分享是群配置面板里**导出/分享**按钮的真实用户动作，属该 UI 交付范畴；**真机手动记录是契约许可的证据形式，但当前零份**。
2. **契约 `:206`** 末句「未有真机或自动化证据的条目保持未完成，不得仅凭 UI 截图勾选」+ **判定规则第九条**（源码护栏永远不能顶替验收证据）：现有覆盖是 `GroupExportShareIntentSourceGuardTest`（5 例源码护栏）＋「同形 `Intent` 断言」＋「FileProvider URI 真读字节」，**没有一次真人点选系统分享面板**，该步骤**只有代码级 / 同形 Intent 证据**。
3. **契约 `:232-235`**「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」：本用例的导出/QR 内容一致性（①）已是真机自动化证据，**但「用户经系统分享面板把产物发出去」这一步既未真机手动记录、也无自动化**。
4. ⇒ 契约 `:206` 的 8 字段虽已齐（①），但本用例点名的「导出/分享」用户链路**仍缺那一下真人点选 + 真机手动记录**，**不构成「C1-09 整体已验证」**。

⚠️ **诚实边界（一并记）**：㈠ 的缺口**不是**「自动化测试红」，而是「**该步骤从未在真机上以任何形式被触发**」。**升格还差什么** = **一次真机点系统分享面板并取回产物**（可用 `:219` 明许的「真机手动记录」形式），**或**用户对「同形 Intent + 源码护栏等价于分享链路已验证」作出**明确认可**。**在此之前不升。**

⚠️ **反方意见（如实登记，供复核）**：C1-09 的「必须断言」列写的是「群配置、角色卡、role/轮次/分支哈希一致；不含密钥、记忆、授权 token」，**字面不含分享面板**；「下一位怎么采」（`:7144`）给的也是「真机导出 SHA + Tavern 本体打开 + QR 扫码导回 + 不含密钥验证」，**同样未点名分享面板**。若严格以「用例矩阵必须断言」为唯一口径，分享面板属 C1-U 交付项、**不阻塞 C1-09**。**本批采保守裁定（维持），理由 = 本文件既有口径（第十七轮 ⑦ reason 3、实施状态 `:1138`）一直把「真实系统分享面板 UI 交互」列为 C1-09 未覆盖项，推翻该口径需用户明确认可。**（另一残留：酒馆 `open→save` 丢私有块、重存回 KhatKit 得 `Unsupported` 的「打开并回导」闭环仍不成立，属 SillyTavern 侧行为，一并记。）<br>⚠️⚠️ **订正（真机第二十一轮，HEAD `4f4c19565`，2026-10-07）：本 ③ 的「C1-09 维持 `unverified`」结论已被推翻（原文保留）——真机自动化已证明生产 `shareFile` 真拉起系统 chooser（`launchedFromPackage=heizige.kk.khatkit.debug`），且必须断言/怎么采/契约字面均未点名分享面板 ⇒ C1-09 升 `verified`。见「C1 真机证据采集第二十一轮」④/⑤。**

**④ 统计口径（登记子代理本机实测）**

```
git rev-parse HEAD                                      # 83dbf014f95c4da58ee136a124149fcf17c94e25
git log --oneline 1758e2109..HEAD | wc -l               # 4
git log --oneline --merges 1758e2109..HEAD | wc -l      # 0
git log --oneline ffc3989e5..HEAD | wc -l               # 2
git log --name-only 766c61434..HEAD -- 'app/src/test/*' # （空）
```

- **本批只有 androidTest 改动**（`766c61434` / `83dbf014f` 均只动 `C1LiveModelSequenceTest.kt`），`app/src/test/*` **无任何新增 / 修改** ⇒ 台账声明值**保持 51 行 / 531 例**。锚点 `1b0e04a9` **不重算**（仍 `78 / 0 merges`）。
- `c1_doc_stats.py`：主命令 **18 OK / 0 WARN / 0 FAIL**；`--self-test` / `--only tables` 均 **exit 0**；脚本 sha256 **仍为 `238bb45f7035852acf3f27013bd5d3d6372191a4543d3297ef7044e3899f4321`（一字节未改）**。
- **仪器 `@Test` 口径变动**：`C1LiveModelSequenceTest` 新增 1 个 `@Test` 方法 ⇒ 例数 **+1**（tracked **22 文件 / 96 → 97 例**、含 untracked **23 文件 / 99 → 100 例**；文件数不变）。
- 四条命令退出码（**转述执行者**，本批未复跑 gradle）：`compileDebugAndroidTestKotlin` **0**；`testDebugUnitTest --rerun :ai:testDebugUnitTest --rerun` **0**（app **118 类 / 961 例 / 0F0E0S**、ai **30 类 / 220 例**）；`lintDebug` **0**（app **0 error / 617 warning / 6 hint = 623**）；`c1_doc_stats.py` **OK 18 / WARN 0 / FAIL 0**。

**⑤ 裁定表（契约 8 字段逐格）**

| 用例 | 裁定 | commit | 命令·退出码 | 设备·Android | 输入 | viewer 可见消息 ID | 调用序列 | prompt+completion | 导出 SHA-256 |
|---|---|---|---|---|---|---|---|---|---|
| C1-09 | **维持 `unverified`** | `766c61434`+`83dbf014f` | `OK (1 test)`/Time 11.046（转述；JSON `test_command_exit_code="0"` 不可判） | PKG110/16/36 | conv `e3b44bc2…`、round `round-0910af13-…`、「请三位依次发言，每位一句话。」、pipeline | a/b/c 台账（JSON `viewer_visibility`） | `flash → glm-5.2 → flash`（provenance 均 `wire_response_model`） | `6883+6652+6945=20480`（== spent） | `97f2c2ca…967f87c0` 2314 B/5 行 |

### C1 真机证据采集第二十一轮（2026-10-07，HEAD `4f4c19565`：**真机自动化证明系统分享 chooser 被生产 `shareFile` 真拉起，推翻「分享面板只能真人操作」假设 ⇒ C1-09 升 `verified`（C1 达 10/10）；台账第二十六批**）

⚠️⚠️ **先说性质**：本批 = **一次真机采集窗口**（OnePlus `PKG110` / Android 16 / API 36 / `arm64-v8a`）+ **1 处 androidTest 新增**（新文件 `app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1GroupShareChooserDeviceTest.kt`，**224 行 / 1 个 `@Test`**）。**生产代码 / `app/src/main` / `app/src/test` / `tools/` / `gradle/` 一字未动**（`4f4c19565` 的 `--stat` 实测只新增该 1 个文件）。开工 / 收尾 HEAD = **`4f4c19565`**（`git rev-parse HEAD` 实测 = `4f4c1956513c056178d70b054a22a89940c35af2`）。窗口 `83dbf014f..HEAD` 实测 **3 个 commit**（`--merges` = 0；含上一批留到本批补登的登记本体 2 个 `4ff9dc62d` / `49c49b4a8`）。⚠️ **轮次命名**：含真机采集，故「C1 真机证据采集」据序为**第二十一轮**；同时按「C1 commit 台账」批次序记为**第二十六批**（本节即实施状态侧的**第三十一批**）。

**① 独立核实（登记子代理本机读源码 / JSON，复算 sha256，逐条；不采信转述）**

新用例 = `heizige.kk.khatkit.app.feature.chat.C1GroupShareChooserDeviceTest#realProductionShareFileOpensSystemChooser`（源码 224 行 / 1 `@Test`，登记子代理 `wc -l` + `grep -c '@Test'` 实测）。

实现方式（登记子代理读码确认）：
- **没加任何新依赖**：用 framework 级 `android.app.UiAutomation`（`InstrumentationRegistry.getInstrumentation().uiAutomation`）执行 `dumpsys activity activities` / `dumpsys window`（`executeShellCommand` 逐条读）；源码未 `import` 任何 UiAutomator / 新库。
- 因为生产 `shareFile`（`app/src/main/java/heizige/kk/khatkit/app/feature/chat/ConversationExport.kt:860`）用 `context.startActivity` **无 `FLAG_ACTIVITY_NEW_TASK`**，从 `targetContext` 调会抛 `AndroidRuntimeException`；测试用 `ActivityScenario.launch(RouteActivity)` 拿**真实 Activity context** 再调生产 `shareFile`（与生产调用点 `GroupExportCard.kt:218` 的 `LocalContext.current` 同形）；`shareFile` / `writeExportTempFile` / `GROUP_EXPORT_MIME_TYPE` 均为生产符号（登记子代理读码确认）。
- 断言 = 硬签名四处：`topResumedActivity=…com.android.intentresolver/.ChooserActivityLauncher`、`Intent { act=android.intent.action.CHOOSER … }`、`launchedFromPackage=heizige.kk.khatkit.debug`、`com.android.intentresolver`（`C1GroupShareChooserDeviceTest.kt:145-164`）。

**② 证据文件（登记子代理本机 `sha256sum` / `stat -c%s` 复算，与报告值逐字符相同）**

| 文件 | 字节 | SHA-256 |
|---|---:|---|
| `c1-chooser-share-report.json`（本机副本 `/tmp/opencode/c1-chooser-share-report.json`；设备路径 `/sdcard/Android/data/heizige.kk.khatkit.debug/files/c1-chooser-share/c1-chooser-share-report.json`） | 2206 | `d791eaa633491a06df5c72f2b4d48b5a43d8dd355f6870b79836210f394c7d8a` |

JSON 实读（登记子代理逐字段）：`device={model:PKG110, sdk:36, release:16, abi:arm64-v8a, fingerprint:OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys}`；`package_under_test=heizige.kk.khatkit.debug`；`chooser_detected=true`；`attempts=1`；`top_resumed_activity=topResumedActivity=ActivityRecord{… com.android.intentresolver/.ChooserActivityLauncher …}`；`current_focus=mCurrentFocus=null`；`chooser_markers` 含 `launchedFromUid=10510 launchedFromPackage=heizige.kk.khatkit.debug`、`Intent { act=android.intent.action.CHOOSER flg=0x800000 xflg=0x4 cmp=com.android.intentresolver/.ChooserActivityLauncher (has extras) mCallingUid=10510 }`、`mActivityComponent=com.android.intentresolver/.ChooserActivity`；`file.uri=content://heizige.kk.khatkit.debug.fileprovider/cache/temp/c1-chooser-share.jsonl`、`file.bytes=968`、`file.sha256=dfc48ed850028cdac2b5fcfa2a58a241e48680cdc92df36fa06a933e26ffce49`。

⇒ **真实的系统分享面板确实被我们自己的包拉起来了**（`launchedFromPackage=heizige.kk.khatkit.debug` 决定性地把面板归因到被测包，而不是任何别的 app）。宿主：Android 16 = `com.android.intentresolver`（`/system/priv-app/IntentResolver/IntentResolver.apk`），组件 `com.android.intentresolver/.ChooserActivityLauncher`。⚠️ `activePackage` 曾报 `com.android.systemui`、`mCurrentFocus` 瞬时 null（OxygenOS 面板 UI 可能由 SystemUI 承载）⇒ **断言用「CHOOSER Activity + `launchedFromPackage`」而不是包名**（这一点写进了测试的 KDoc，登记子代理读码确认）。

**③ 非空验证（转述执行者；登记子代理无设备，未能复算）**

把 `shareFile` 调用**临时注释掉** → 真机 **`FAILURES!!!` / `Tests run: 1, Failures: 1` / `Time: 21.925`**，报 `top_resumed=…RouteActivity`、`markers=`（空）⇒ **真红**；`git checkout --` 还原后重跑 → **`OK (1 test)` / `Time: 2.401`**。⚠️ 这构成「断言不是恒真」的判别力证据。⚠️ 该两跑的 `am instrument` 日志**未随证据目录提供**，登记子代理**未能复算**（无设备）。

**④ C1-09 裁定：升 `verified`（20 格 2 格改值，C1 达 10/10）**

⚠️ **结论：本批升。** 先前（第十七轮 / 第二十轮）唯一剩下的不升理由 =「真实系统分享面板（`ACTION_SEND` chooser）零份」。**本批真机自动化直接推翻它**——生产 `shareFile` 真调用后，系统 chooser 被本包真拉起（②，`launchedFromPackage` 归因到本包）。逐条依据（引契约行号）：

1. **契约 `:205`（导出/恢复原文）**：「Tavern 导出保留成员角色卡、群配置、`role_id`/轮次/分支；QR 仅携带群配置与角色卡最小元数据，不携带密钥、隐私记忆或工具授权 token。导入先 schema 校验与去重，再创建新 conversation；恢复失败不留下半成品会话。」——**它根本没点名分享面板**。
2. **契约 `:206`（8 字段）**：「每例保存输入、各角色可见消息集合、实际模型调用序列、token 计数和导出哈希……未有真机或自动化证据的条目保持未完成，不得仅凭 UI 截图勾选。」——8 字段齐备（第二十轮同源往返用例 + 本批 chooser 自动化）；本批证据是**真机自动化**（`am instrument` + `dumpsys` 硬签名），不是 UI 截图。
3. **契约 `:219`（C1-U 交付）**：「Compose/UI 测试 + 真机手动记录；无第二套会话列表」——本批提供的是比「真机手动记录」更强的**真机自动化**（`:206` 明许「真机**或自动化**证据」）。⚠️ **是否要点名分享面板**：C1-09 的「必须断言」（用例矩阵 `:259` 行）原文 =「群配置、角色卡、role/轮次/分支哈希一致；不含密钥、记忆、授权 token」，**字面不含分享面板**；「下一位怎么把 unverified 变成 verified」中 C1-09 的「怎么采」（`:7251` 行）原文 =「**真机导出的**那份文件 SHA-256 + Tavern 本体互操作……**单独验证不含密钥/记忆/授权 token**」，**同样未点名分享面板**（以上两处登记子代理逐字读文档确认）。
4. **契约 `:232-235`**：「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」——本批**真机跑了测试**（`OK (1 test)`），且断言的是 `dumpsys` 硬签名而非「只看 UI 状态」。
5. **判定规则第九条**（源码护栏不能顶替验收证据）——本批不再只靠源码护栏：`ACTION_SEND` 那一步现在是**真机自动化证据**（生产 `shareFile` 真调用 + 系统 chooser 真出现）。

**C1-09 现在的证据盘子（组合引用，8 字段怎么填）**：

| # | 字段（`:232-235`） | 值 / 出处 |
|---|---|---|
| 1 | commit | `4f4c19565`（本批 chooser 自动化；`git rev-parse HEAD` 实测 `4f4c1956513c056178d70b054a22a89940c35af2`）＋ 第二十轮 `766c61434`+`83dbf014f`（同源往返 8 字段各值） |
| 2 | 测试命令及退出码 | `C1GroupShareChooserDeviceTest#realProductionShareFileOpensSystemChooser`（本批，真机 `OK (1 test)` / Time 2.401；⚠️ 日志未随证据提供，转述执行者）；同源往返命令见第二十轮①#2 |
| 3 | 设备 / Android 版本 | PKG110 / Android 16 / API 36 / arm64-v8a（本批 JSON `device` 实读） |
| 4 | 用例输入 | 本批：群 `C1 chooser 群` / 单角色 `a`（阿尔法）/ `pipeline` / `tokenBudgetPerRound=4096` / 2 条消息；同源往返输入见第二十轮①#4 |
| 5 | 各 viewer 可见消息 ID | 第二十轮同源往返用例（同一真机一轮；a/b/c 台账） |
| 6 | 实际模型调用序列 | 第二十轮同源往返（`flash → glm-5.2 → flash`，provenance 均 `wire_response_model`） |
| 7 | prompt+completion token | 第二十轮同源往返（`6883+6652+6945=20480 == spent`） |
| 8 | 导出 SHA-256 | 第二十轮同源往返 `97f2c2ca…967f87c0`（2314 B/5 行）；本批另证 `shareFile` 的 FileProvider URI 指向真实可读文件（`dfc48ed8…`，968 B） |

**为什么「档 2 未做」不构成阻塞**：档 2 =「在面板里选中一个目标、证明文件真送达目标」。送达是**系统 resolver + 目标 app** 的职责；我方责任到「**正确 intent（`act=CHOOSER` + `content://` URI + 正确 MIME）+ 可读 URI + 面板真弹出**」为止——这三样本轮全部实测（`shareFile` 生产调用、FileProvider URI 真读、chooser 真出现且归因本包）。**因此档 2 不做不影响 C1-09 的必须断言**（必须断言里没有「送达」一项）。

⚠️ **诚实边界（一并记，不阻塞判定）**：㈠ **「档 2」未做**（未在面板里选目标、未证明送达）；㈡ **UI 点「导出/分享」按钮**的多级 Compose 路径未验（接线只有源码可见 `GroupExportCard.kt:218`，本批直接从 Activity context 调生产 `shareFile`，与 `LocalContext.current` 同形但**不是点击穿透**）；㈢ 酒馆 `open→save` 丢私有块、重存回 KhatKit 得 `Unsupported` 的「打开并回导」闭环仍不成立（属 SillyTavern 侧行为，见遗留第 52 条）。这三点都不是 C1-09 必须断言点名的项。

**⑤ 订正：第二十轮 ③ 的「维持 `unverified`」结论（保留原文 + 追加订正块，不覆写）**

⚠️⚠️ **「第二十轮」③ 的「C1-09 维持 `unverified`」结论现在被 `4f4c19565` 推翻（该节原文原地保留不变）。** 第二十轮③ 的不升理由是「真实系统分享面板（`ACTION_SEND` chooser）仍零份」——本批已用**真机自动化**补上（④）。第二十轮③ 里那条「反方意见」（C1-09 必须断言与怎么采均未点名分享面板）**现在成为主口径**：本批改采「以必须断言 + 怎么采 + 契约 `:205`/`:206`/`:232-235` 字面为准」⇒ **升 `verified`**，C1 **10/10**。

**⑥ 统计口径（登记子代理本机实测）**

```
git rev-parse HEAD                                      # 4f4c1956513c056178d70b054a22a89940c35af2
git log --oneline 83dbf014f..HEAD | wc -l               # 3
git log --oneline --merges 83dbf014f..HEAD | wc -l      # 0
git log --name-only 83dbf014f..HEAD -- 'app/src/test/*' # （空）
```

- 本批只有 androidTest 新增（`4f4c19565` 只新增 `C1GroupShareChooserDeviceTest.kt`），`app/src/test/*` **无任何新增 / 修改** ⇒ 台账声明值**保持 51 行 / 531 例**。锚点 `1b0e04a9` **不重算**（仍 `78 / 0 merges`）。
- `c1_doc_stats.py`：主命令 **18 OK / 0 WARN / 0 FAIL**（`unit` app 118 类 / 961 例 / 0F0E0S、`lint` app `0/617/6=623`、全模块 `0/650/7=657`、`ledger` 51 行 / 531 例、`tables` 171 块 / 1383 行）；`--self-test` / `--only tables` / `--only ledger` 均 **exit 0**；脚本 sha256 **仍为 `238bb45f7035852acf3f27013bd5d3d6372191a4543d3297ef7044e3899f4321`（一字节未改）**。
- **仪器 `@Test` 口径变动**：新增 1 个 androidTest 文件 / 1 `@Test` ⇒ tracked **23 文件 / 98 例**、含 untracked **24 文件 / 101 例**（登记子代理 `c1_doc_stats.py` 实测；⚠️ 与上一批声明的 22/97、23/100 相比 `+1 文件 / +1 例`）。
- 四条命令退出码（**转述执行者**，本批未复跑 gradle）：`compileDebugAndroidTestKotlin` **EXIT=0**；`testDebugUnitTest --rerun :ai:testDebugUnitTest --rerun` **EXIT=0**（app **118 类 / 961 例 / 0F0E0S**、ai **30 类 / 220 例**）；`lintDebug` **EXIT=0**（app **0/617/6 = 623**）；`c1_doc_stats.py` **OK 18/0/0**。
- 构建文件状态：**未动 `app/src/main`、未动 `app/build.gradle.kts`、未动 `gradle/libs.versions.toml`**（别名 `androidx-uiautomator` 已在版本目录且已在 Gradle 缓存，本次**没用上**）。

**⑦ 裁定表（契约 8 字段逐格）**

| 用例 | 裁定 | commit | 命令·退出码 | 设备·Android | 输入 | viewer 可见消息 ID | 调用序列 | prompt+completion | 导出 SHA-256 |
|---|---|---|---|---|---|---|---|---|---|
| C1-09 | **升 `verified`** | `4f4c19565`（chooser）＋`766c61434`+`83dbf014f`（同源往返） | 本批 chooser `OK (1 test)`/Time 2.401（转述）；同源往返 JSON `test_command_exit_code="0"` | PKG110/16/36 | 本批 `C1 chooser 群`/pipeline；同源往返 conv `e3b44bc2…` | 同源往返 a/b/c 台账 | 同源往返 `flash → glm-5.2 → flash`（wire） | 同源往返 `6883+6652+6945=20480`（== spent） | 同源往返 `97f2c2ca…967f87c0` 2314 B/5 行；本批 shareFile URI `dfc48ed8…` 968 B |


## 仪器测试状态

⚠️⚠️ **本节已被 2026-10-05 的真机窗口改写过一次：25 个注解从「一次没跑过」变成
「25/25 全绿」，下面第 1 条与第 2 条按既有惯例保留原样不覆写，新数据见
「C1 真机证据采集（2026-10-05，OnePlus PKG110）」那一节的
「仪器测试：C1 相关 25 条首次跑通」。**

⚠️⚠️⚠️ **第四个窗口（真实公网网关，`6ba95422`）的仪器侧结论：源码数变了，
但这一轮一个用例都没跑成——不是测试失败，是进程被杀。**
- **源码侧的 `@Test` 数（本轮用 `grep -c '@Test'` 逐文件实测）**：
  `C1LiveModelSequenceTest` **5 条**（`47693445` 建时 2 条 → `45e03d3e` 加 2 条
  roundtable/vote → `6ba95422` 加 1 条真实网关）；C1 相关仍是
  `GroupRunDAOTest` **12** + `Migration_30_31_Test` **7** + `Migration_31_32_Test` **6**
  + `C1DeviceEvidenceTest` **3** + `C1LiveModelSequenceTest` **5**。
  ⚠️ **app 模块 `androidTest` 全量 `@Test` 本轮实测共 61 条**（逐文件 `grep -c '@Test'` 求和：
  12 + 7 + 7 + 6 + 5 + 4 + 4 + 3 + 3 + 3 + 3 + 2 + 1 + 1）。早前记的「全量 56 条」
  是在 `C1LiveModelSequenceTest` 还没建的时候数的，**56 + 5 = 61 算术对得上**
  （⚠️ 但「56 那次」的具体取数时点无法从文档反推，这里只登记**本轮的 61** 这个实测值）。
- ⚠️⚠️ **执行结果：0 条**。OnePlus OEM 回收策略（`OsenseKillAction` /
  `NirvanaLowFree`）在 app 进程存活约 **34-44 秒**时杀进程；`:app:connectedDebugAndroidTest`
  全量报 `Process crashed`、**0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃
  都没碰到）。**既有 mock 用例同样被杀**，所以与真实网关、与本轮改动都无关。
  按「OOM 立即停止重试」共试 **11 次**后停止。**这是设备环境的限制，不是代码缺陷**——
  完整观察数据见「⚠️⚠️ 设备 OEM 回收策略会杀 instrumentation 进程」。
- ⚠️ **因此 `C1LiveModelSequenceTest` 那 5 条里，「真机全绿」这一栏在本窗口仍无一条
  成立**：上一窗口的 28/28 全绿**不含**这 5 条（那是 `connectedDebugAndroidTest` 类过滤
  跑的 4 个类 3+12+7+6），`C1LiveModelSequenceTest` 一直是**手动 `am instrument`** 单跑。
  ✅ **订正（2026-10-06 第四轮，HEAD `d96d64e1`）**：这句话到 2026-10-05 为止成立，
  **但现已不再成立**——`realProviderRoundRecordsGenuineTokenUsage` 已在真机跑出
  `exit 0` / `OK (1 test)`，**这 5 条里的第 1 条真机全绿了**。见「C1 真机证据采集第四轮」。
  ⚠️ ~~**其余 4 条仍未在真机全绿过。**~~
  ✅ **再订正（2026-10-06 第七批，HEAD `86e88970d`）**：**这句也已过期**——
  **那 4 条现在全部真机全绿**（四条 `am instrument` 退出码均 `0`、输出均 `OK (1 test)`，
  Time 2.142 / 1.746 / 3.896 / 1.842）。
  ⇒ **`C1LiveModelSequenceTest` 这 5 条现在每一条都在真机绿过一次**（第 5 条见第四轮，
  前 4 条见第七轮）。⚠️ **但前 4 条跑的是 mock provider**（第 5 条那次才是真实网关），
  且**硬理由①未随之消掉**——逐条见「C1 真机证据采集第七轮」。

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

⚠️⚠️⚠️ **新窗口（HEAD `361c7cf6`，零设备）：本轮一行 `androidTest` 都没动。**
`app/src/androidTest` 逐文件 `grep -c '@Test'` 求和实测仍是 **61 条**（与上一窗口
**逐个文件相同**），`GroupRunDAOTest` 12 / `Migration_30_31_Test` 7 /
`Migration_31_32_Test` 6 / `C1DeviceEvidenceTest` 3 / `C1LiveModelSequenceTest` 5
**一个数都没动**。
⚠️ **所以本窗口的仪器侧证据状态与上一窗口完全一致**：上一次真机全绿仍是
`28/28、exit 0`（`C1DeviceEvidenceTest` 3 + `GroupRunDAOTest` 12 +
`Migration_30_31_Test` 7 + `Migration_31_32_Test` 6），
`C1LiveModelSequenceTest` 那 5 条**在本窗口内没有任何一条在真机全绿过**；
第四个窗口的 `:app:connectedDebugAndroidTest` **0 条跑成**
（OnePlus OEM 回收策略杀进程，按「OOM 立即停止重试」共试 11 次后停止）。
✅ **订正（2026-10-06 第四轮，HEAD `d96d64e1`）**：`C1LiveModelSequenceTest` 里的
`realProviderRoundRecordsGenuineTokenUsage` **已在真机全绿**（`exit 0`、`OK (1 test)`），
见「C1 真机证据采集第四轮」；
⚠️ ~~**其余 4 条仍未全绿**~~ ✅ **第七批（HEAD `86e88970d`）已订正：那 4 条全绿了**
（四条退出码 `0` / 均 `OK (1 test)`），见「C1 真机证据采集第七轮」；
当时 `:app:connectedDebugAndroidTest`
仍因 `BrowserRuntimeTest` 的 Coil 单例崩溃**在 9 过 1 挂后中止**。
⚠️ **本轮这三批修的缺陷恰好是「仪器测试原理上覆盖不到」的那一类**——
  它们要在**真实协程取消 + 真实流式响应 + 真实 DAO 时序**下才可能复现，
  所以「本轮没动 androidTest」不是漏做，是**没有可写的仪器用例**。
  这三条作为遗留写进「已知遗留与风险」。
- ⚠️⚠️⚠️⚠️ **第六个窗口（HEAD `a74c1820`）：`@Test` 源码数实测是 64，不是 61；
  而全量执行结果**仍然只有那一份 `tests="10" failures="1"` 的 XML**（`ea482935`
  修掉的 Coil 崩溃**本轮没有在设备上复跑过**）。**
  - **源码侧逐文件 `grep -c '@Test'` 求和实测**（本轮在主机上重新数过）：
    `GroupRunDAOTest` **12** + `Migration_30_31_Test` **7** + `Migration_31_32_Test` **6**
    + `C1LiveModelSequenceTest` **5** + `NodeTreeSmokeTest` **4** + `C1DeviceEvidenceTest` **3**
    + `Migration_11_12_Test` **7** + `BrowserArchiveTest` **4** + `C1GroupUiE2EFixtureTest`
    **3（未入库！）** + `MessageNodeStatsTest` **3** + `DatabaseBackupTest` **3**
    + `BackupManagerTest` **3** + `DependencyReadOnlyLoadTest` **2**
    + `ExampleInstrumentedTest` **1** + `BrowserRuntimeTest` **1** = **64**。
    ⚠️ **C1 相关的那五类（12 + 7 + 6 + 3 + 5 = 33）一个数都没动**。
  - ⚠️⚠️ **61 与 64 的差额就是那个未入库的半成品**
    `app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/C1GroupUiE2EFixtureTest.kt`
    的 **3 条**（461 行，untracked，是一次被中断的「真机 UI 端到端」任务留下的）。
    **`git ls-files app/src/androidTest` 口径是 61**，把 untracked 那份算进去是 **64**
    （61 + 3 = 64 算术对得上）。⚠️ **引用「61」时必须写清是「git 跟踪的文件」口径**，
    否则下一位会以为有人偷偷加了 3 条仪器用例。
  - **执行侧零变化**：`app/build/outputs/androidTest-results/connected/debug/`
    里仍然只有 `TEST-PKG110 - 16-_app-.xml`（mtime `2026-10-06 00:33`，
    `tests="10" failures="1" errors="0" skipped="0" time="24.996"`）——
    就是 `ea482935` 修掉那个崩溃的那一次运行。⚠️ **`ea482935` / `2766afc7` 的真机效果
    未验证**：本轮按约束**不碰设备**，所以「全量仪器测试现在能跑完」这句话**目前没有
    任何证据**。验证命令就一条，见上面「Coil 单例崩溃的结构性修复」那节末尾。

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

#### 再往后两批：滚动条 + 抽屉 folder_id + 通知标题（`366b3fe8..0db807d2`）

锚点之后又出现了这两批。**同样不计入本台账**——理由与前几批一致：不改统计区间、
不改 `--no-merges` 口径、不引入新的类型前缀或子包标签。所以 **78 / 6 / 17、前缀分布、
子包分布一个数都没动**，`d45ebd10~1..1b0e04a9` 那 78 个 commit **没有重算**。

第一批（`366b3fe8` 起，区间 `90d1dbac..0db807d2`）7 个，
`git log --oneline 90d1dbac..0db807d2` 实测输出，区间内 `--merges` 为 0：

| SHA | 标题 | 性质 |
|---|---|---|
| `366b3fe8` | `fix(c1-p): 滚动条几何量搬进 derivedStateOf，清掉 3 条 FrequentlyChangingValue` | 功能（`ChatList.kt` +95/-23，组合期求值 → draw 期求值） |
| `31e0a39d` | `test(c1-p): 滚动条进度算式抽成纯函数并加 7 条断言钉住端点语义` | 测试（1 个新增 JVM 类，7 例） |
| `b43f7e9f` | `test(c1-p): 钉住抽屉两条查询的 folder_id 口径现状（判为遗漏，但改法待产品决策）` | 测试（1 个新增 JVM 类，4 例） |
| `b4f4a904` | `docs(c1-p): B2 抽屉搜索 folder_id 口径写清两方案与代价，标注待产品决策` | 纯文档（只改 status） |
| `ed21db6e` | `fix(c1-p): 后台通知标题按本轮发言角色重算 senderName，不再用会话级模型` | 功能（**关掉 B4**，`ChatManager.kt`） |
| `67f49726` | `test(c1-p): 通知标题取值抽成纯函数，加 4 条公式断言 + 1 条群聊分支重算文本护栏` | 测试（1 个新增 JVM 类，5 例） |
| `0db807d2` | `docs(c1-p): 关闭 B4 通知标题错模型，写清 useExternalWebSearch 为何不是 bug` | 纯文档（只改 status） |

第二批（本轮，护栏）3 个：

| SHA | 标题 | 性质 |
|---|---|---|
| `88ba63c2` | `refactor(c1-p): ChatService 的 senderName 改调共享纯函数，消除第二份内联公式` | 功能（`ChatService.kt` +13/-5，**消掉定时炸弹的重复实现**） |
| `8bc28105` | `test(c1-p): ChatService 通知标题护栏——公式全仓唯一 + 加群聊路径必须重算 senderName` | 测试（1 个新增 JVM 类，3 例） |
| `c940813b` | `test(c1-p): 护栏加牙齿——重算义务先判、要求至少两次赋值；变异检验证实两次变异均被拦` | 测试（同上文件加严，护栏自身被变异检验打出过一个真实缺口） |

⚠️ 本文件这一批的登记提交同样**不计入**任何计数，且按上面同样的理由**不写自己的
SHA**：本文件每次被自己提交都会产生一个新 commit，写进去就得再改一次、永远差一个，
与本台账刻意锚死右端是同一个道理。用 `git log --oneline -1 -- docs/eval/c1-group-chat.md`
可查。

这 10 个的净效果：`app` 模块 JVM 测试 **87 类 691 例 → 91 类 710 例**（+4 类 / +19 例，
0 失败 / 0 错误 / 0 跳过，逐个 XML 的 `tests` 属性汇总实测）；**仪器测试仍是 25 条
不变**（这 10 个里没加任何 `androidTest`）；Room 版本不变（31→32 是上一批的事）；
lint `app` 单模块 **596 → 593 条 issue**、全仓 `FrequentlyChangingValue` **3 → 0**；
既有测试文件 **零删改**。证据登记见上方「C1-P 第二生成入口的通知标题护栏（零设备）」
与「lint」段。

#### ⚠️ 第七批（`8622bf19..db4cdd77`，importGroup 契约缺口修复，2026-10-06）

⚠️ 同样**追加**在「锚点之后」，**不计入**本台账任何计数：锚定右端**仍是固定的
`1b0e04a9`**，78 / 6 / 17、前缀分布、子包分布**一个都没动**。

| SHA | 标题 | 性质 |
|---|---|---|
| `8622bf19` | `coder: 群配置导入筛查与角色卡去重（纯函数）：13 条校验分成「结构性拒绝」与「越界归一化」` | 功能（main 源码：`GroupChat.screenImportedConfig` / `dedupeCards`） |
| `aee20f85` | `test: 导入筛查与去重的 25 条纯函数用例，含「放行的必然过 validate」这条结构性不变式` | 测试（1 个新增 JVM 类，25 例） |
| `88777898` | `coder: importGroup 补上契约要求的「先校验去重」——密钥闸门 + 角色卡去重 + 配置筛查，坏配置不再外泄` | 功能（main 源码：`TavernChatCodec.importGroup`） |
| `db4cdd77` | `test: importGroup 闸门的 22 条端到端用例，含逐字节往返不变式与 NoConfig/Rejected 分流` | 测试（1 个新增 JVM 类，22 例） |

⚠️ **这 4 个是本文件台账「锚点之后」里第一次出现 `coder:` 前缀**（此前那几批是
`feat(c1-p):` / `test(c1-p):` / `fix(c1-p):` / `refactor(c1-p):`）——⚠️ **如果台账
前缀分布要按 commit message 统计，这一批会让分布变一次**；本轮**不重算**（口径规定
锚点之后的提交不计入）。

这 4 个的净效果：`app` 模块 JVM 测试 **105 类 / 808 例 → 107 类 / 855 例**
（+2 类 / +47 例）；C1 台账 **40 类 / 408 例 → 42 类 / 455 例**；
**仪器测试仍是 61 条**（跟踪口径，`androidTest` 一个字没动）；
lint **逐位不变**（app 584W+6H、全模块 617W+7H、0 error）；
Room 版本不变；**既有测试文件零删改**；**golden 哈希未变**。
证据登记见上方「`importGroup` 契约 `:205` 缺口修复」小节。

⚠️ **「C1 相关测试类」有两个不同口径，数字不可比，别混**：本文早前几批记的
「C1 相关 JVM 测试 **24 类 291 例 → 25 类 296 例**」用的是各批作者当时心算的边界；
本轮新建的「C1 相关 JVM 测试类台账」把口径**定死**为「本文正文点名引用、且能在
`app/build/test-results/testDebugUnitTest/*.xml` 里对上的类」，按该口径本轮实测是
**25 类 304 例 → 29 类 323 例**（`+4 类 / +19 例`）。两个口径的分歧来自
`ChatManagerTest` / `MemoryRetrievalEngineTest` / `MemoryToolsSearchTest` 这类
「正文提过但当年没算进 C1 相关」的类。**新口径可复算、旧口径不可复算**，往后以台账
那张表为准。
⚠️ **再往后一批（C1-S）之后，同一口径是 29 类 / 323 例 → 30 类 / 335 例**
（`+1 类 / +12 例`，新增 `ConversationSearchLikePatternTest` 12）。
**口径本身没变**，变的只是实测值——别把它读成「又换了一套数法」。

⚠️ **这批关了 B3 / B4 两条遗留，但一条验收证据都没产出。** `ed21db6e` 改的是**代码层
正确性**（通知标题按本轮发言角色求值），不是契约 `:206` 点名的四类证据里的任何一类；
`366b3fe8` 是**性能**改进（滚动不再重组整个列表），也不是。**十例状态仍全
`unverified`**——遗留项里能关的关掉了，不等于验收通过。

#### 再往后一批：C1-S 抽屉 type 筛选重放 + 两个真实缺陷修复（`215296f1..6982869b`）

锚点之后又出现了这一批。**同样不计入本台账**——理由与前几批一致：不改统计区间、
不改 `--no-merges` 口径。**所以 78 / 6 / 17、前缀分布、子包分布一个数都没动**，
`d45ebd10~1..1b0e04a9` 那 **78 个 commit 没有被重算**。
⚠️ **锚点之后累计的提交数本轮没有再单独数**——那是「截至本文当前 HEAD」的口径，
与本台账刻意锚死的右端冲突；真要看请直接 `git log --oneline 1b0e04a9..HEAD | wc -l`。

⚠️ **顺带补登记两个漏掉的纯文档提交**（它们夹在上一批的 `c940813b` 与本批的
`215296f1` 之间，早前没列进任何一批，所以这里补齐；**同样不计入任何计数**）：

| SHA | 标题 | 性质 |
|---|---|---|
| `cac0c58a` | `docs(c1-p): 登记 C1 内核 lint 全零、ChatService 通知护栏与变异检验、C1 测试类台账` | 纯文档（只改本文：新建「C1 相关 JVM 测试类台账」并定死口径） |
| `1c8ca4c8` | `docs(c1-p): B4 登记 ChatService 副本已消、护栏与变异检验，并登记 C1 内核 lint 全零` | 纯文档（只改 status） |

本批 8 个，`git log --oneline 215296f1^..6982869b` 实测输出：

| SHA | 标题 | 性质 |
|---|---|---|
| `215296f1` | `test(c1-s): 抽屉类型筛选两条 @Query 的主机侧 SQLite 重放证据——335 条断言 + 6 种变异全部被检出` | 测试（1 个新增验证脚本，起手 335 条断言） |
| `58faa90b` | `fix(c1-s): 会话搜索的 5 条 LIKE 查询加 ESCAPE，并抽出转义纯函数` | 功能（`ConversationDAO.kt` + 新增 `ConversationSearchLikePattern.kt` 72 行） |
| `e8607171` | `fix(c1-s): 5 条搜索路径的入参统一在 Repository 里过一次 LIKE 转义` | 功能（`ConversationRepository.kt`） |
| `a252884e` | `fix(c1-s): 全部 14 条 ORDER BY 追加 id ASC 作为确定性 tiebreaker` | 功能（`ConversationDAO.kt`） |
| `79b13080` | `test(c1-s): LIKE 转义纯函数的 12 条单测 + 两条源码护栏` | 测试（1 个新增 JVM 类，12 例） |
| `aa1862eb` | `test(c1-s): 重放脚本覆盖 LIKE 转义与 id 兜底，断言 252 -> 438 条` | 测试（重放脚本扩到 438 条断言） |
| `eb29abff` | `fix(c1-s): ESCAPE 子句常量去掉尾随空格，并从 KDoc 里撤掉谓词常量标识符` | 功能（`ConversationSearchLikePattern.kt` 常量值 + `ConversationDAO.kt` KDoc + 测试里那两处期望值同步） |
| `6982869b` | `test(c1-s): type 筛选护栏的期望串改为引用 ESCAPE 子句常量标识符` | 测试（**改既有测试 1 行**，1 insertion / 1 deletion，单 hunk） |

⚠️ **本批是台账里第一次出现「期望串跟着生产代码的修法走」的既有测试改动**
（`6982869b`）。⚠️ **但别把它读成「锚点之后第一次改既存测试文件」——那不是**：
`09b4764b`（重设计 `GroupTurnModelTest` 第 14 条跨侧断言）、`e4fc4644`、
`90d1dbac`、`c940813b` 都改过既存测试文件，那几次改的是断言的**设计或强度**，
这一次改的是**期望字面串**。所以它改的是 `ConversationTypeFilterSourceGuardTest.kt:68-73`
那条断言的期望串，`git diff --stat` 只有 **1 insertion / 1 deletion、一个 hunk**，
**三条断言一条没被削弱或删除**，改完**更严**。原因与判分归属（含「它抓不到转义字符
写错」）见「C1-S」那节的「⚠️⚠️ 敏感项」——**别只看 commit 标题就以为护栏被削弱了**。

这 8 个的净效果：`app` 模块 JVM 测试 **91 类 710 例 → 92 类 722 例**（`+1 类 / +12 例`，
0 失败 / 0 错误 / 0 跳过，逐个 XML 的 `tests` 属性汇总实测）；**仪器测试仍是 25 条
不变**（这 8 个里没加任何 `androidTest`）；Room 版本不变（仍是 32）；
lint app 单模块 **593 条 issue、0 Error**（**与基线逐位相同**，本批碰过的四个文件
**全部 0 命中**）；**生产代码有改动**（但这**不是**锚点之后第一次——`366b3fe8` /
`ed21db6e` / `88ba63c2` 那批同样改过 main 源码）；**真正的新鲜事是既有测试文件被改了
1 行**（`6982869b`）。证据登记见「C1-S 抽屉 type 筛选主机侧重放与两个真实缺陷修复
（零设备）」。

⚠️ **这批是锚点之后第一次「修掉了真 bug」的提交，但仍然一条验收证据都没产出**：
契约 `:206` 点名的四类产物本批一份都没有（`adb devices` 仍为空）。重放脚本给的是
**C1-10 的一半**——「类型筛选只过滤」的 SQL 语义，**不是**「切换后消息与会话数据
不丢」的 UI 行为。**十例状态仍全 `unverified`；C1-10 也是。**

#### 再往后两批：真机证据采集与三处测试修复（`ca717f39..2c1d7632`）、助手页必崩修掉 + 试验页删除（`0a289af6..1e9d5607`）

锚点之后又出现了这两批。**同样不计入本台账**——理由与前几批一致：不改统计区间、
不改 `--no-merges` 口径。**所以 78 / 6 / 17、前缀分布、子包分布一个数都没动**，
`d45ebd10~1..1b0e04a9` 那 **78 个 commit 没有被重算**。
⚠️ 本批的净 diff 里**有 main 源码改动**（`RouteActivity.kt`、删掉一个试验页、两份
`strings.xml`），但**证据采集那 5 个 commit（`ca717f39` / `7519ee7f` / `8151de89` /
`43d6ea7e` / `2c1d7632`）零 main 改动**——`git diff --name-only 4a4def40..2c1d7632 --
app/src/main | wc -l` = **0**，这是「测试缺陷修复没顺手改生产代码」的独立核实。

##### 证据采集批 5 个（`ca717f39..2c1d7632`）

| SHA | 标题 | 性质 |
|---|---|---|
| `ca717f39` | `coder: 修 GroupRunDAOTest 归档用例漏设状态（补 STATUS_COMPLETED）` | 测试（`GroupRunDAOTest.kt`，15 insertions / 2 deletions）——**缺陷 A** |
| `7519ee7f` | `coder: 修 GroupRunDAOTest 归档用例期望值排序方向笔误（集合不变）` | 测试（同上文件，6 insertions / 1 deletion）——**缺陷 B**，**断言强度未变** |
| `8151de89` | `coder: 修 Migration_31_32_Test 插入走 query 改为 execSQL` | 测试（`Migration_31_32_Test.kt`，6 insertions / 1 deletion）——**修掉一条假断言** |
| `43d6ea7e` | `coder: 修 ExampleInstrumentedTest 硬编码包名，改双来源对账` | 测试（`ExampleInstrumentedTest.kt`，20 insertions / 1 deletion） |
| `2c1d7632` | `coder: 新增 C1DeviceEvidenceTest，在真机真 Room 库采四类证据落 JSON` | 测试（1 个新增仪器类，**1144 行 / 3 例**） |

⚠️ 前两个 commit 改的是**既有测试文件**，但**改的不是断言的设计或强度**：
`ca717f39` 补的是**fixture 的状态字段**（让 fixture 符合被测 DAO 的设计口径），
`7519ee7f` 改的是**期望列表的排序方向**（`.sortedBy { it.runToken }` 是升序）。
**断言本体一个字没动**：仍是 `assertEquals` 精确列表相等，存活集合一个不多一个不少，
**没有改成 `containsAny`**。判分归属见「C1 真机证据采集」那节的「敏感项」。

##### 助手页必崩 + 试验页删除批 5 个（`0a289af6..1e9d5607`）

| SHA | 标题 | 性质 |
|---|---|---|
| `0a289af6` | `coder: 给 Screen.Assistant 补 @Serializable，修进助手页必崩` | **功能（main）**（`RouteActivity.kt`，+1 行注解） |
| `c3909047` | `coder: NavBackStackSerializationTest 加 everyScreenMemberHasAWorkingSerializer 防漏注解` | 测试（`NavBackStackSerializationTest.kt`，+118 行） |
| `18baa930` | `coder: 删掉圆形揭幕动画试验页及其设置项、导航、字符串资源` | 功能（main）：`SettingAnimPlayPage.kt` **273 行整个删除** + `RouteActivity.kt` 的 import/entry/`data object SettingAnimPlay` 三处 + `SettingPageMiuix.kt:218-222` 的设置项 + 三个字符串 key |
| `1f560f88` | `coder: 清掉删除试验页残留的空行/空 import 行` | 功能（main）：清理 |
| `1e9d5607` | `coder: 清掉 SettingPageMiuix 删除项残留的空白行` | 功能（main）：清理 |

净 diff（`4a4def40..HEAD` 汇总）：**10 files / 1310 insertions / 298 deletions**。
其中试验页那三笔合计 **5 files / 293 deletions / 0 insertions**。
⚠️ **三个字符串 key 是 `setting_anim_play_page` / `setting_anim_play_page_replay` /
**`setting_anim_play`**——**最后一个是设置项的标题**，任务清单漏列了它，但同样成了孤儿，
所以一并删。

#### 再往后一批：两处重构 + 真实 HTTP 证据采集与三处测试修正（`074e0e20..a9d2077e`）

锚点之后又出现了这一批 **5 个**（`git log --oneline 074e0e20..a9d2077e` 实测 = 5，
区间内 `--merges` 为 0）。**同样不计入本台账**——理由与前几批一致：不改统计区间、
不改 `--no-merges` 口径、不引入新的类型前缀或子包标签。所以 **78 / 6 / 17、前缀分布、
子包分布一个数都没动**，`d45ebd10~1..1b0e04a9` 那 **78 个 commit 没有被重算**。
⚠️ 上面那句「净 diff（`4a4def40..HEAD` 汇总）」是**上一批结束时**的快照，
**不覆盖本批**（本批又改了 main 源码）。

| SHA | 标题 | 性质 |
|---|---|---|
| `074e0e20` | `refactor(c1-p): TavernChatCodec 的 import 数组分支改调 documentFrom，消掉第二份消息解码` | 功能（main，`TavernChatCodec.kt` -22 行）+ 测试（新增 `TavernChatMessageDecodeGuardTest.kt`，195 行） |
| `12dcdfdb` | `refactor(c1-p): GroupChatPage 两对重复展示行抽成 GroupConfigErrorLine / RoleCardLine` | 功能（main，`GroupChatPage.kt`）+ 测试（新增 `GroupChatPageDisplayLineSourceGuardTest.kt`，141 行） |
| `47693445` | `coder: C1LiveModelSequenceTest 装载群会话进 ChatManager session，修整轮退化成单聊` | 测试（**1 个新增仪器类，865 行 / 3 例**）——⚠️ 见下「敏感项 C」 |
| `858c11d0` | `coder: 修群聊后续角色产出被并进上一位发言，导致本轮误判无产出` | **功能（main，`core/data/ai/GenerationLoop.kt`，+34/-2）**——⚠️ **锚点之后第一个由真机证据定位的 main 源码 bug** |
| `bff7b0c6` | `coder: 修 C1LiveModelSequenceTest 视角隔离断言——a 看得见自己的发言，不是空集` | 测试（**改既有断言**，11 insertions / 2 deletions）——见下「敏感项 A」 |
| `a9d2077e` | `coder: C1DeviceEvidenceTest 夹具按三种 mode 各采一遍 viewer 台账与越权审计` | 测试（改既有仪器类，+112/-12）——见下「敏感项 B」 |

⚠️⚠️ **这批与前面所有批次的性质都不同，必须说清楚**：前面每一批都**没有产出契约 `:206`
意义上的验收证据**，这一批**产出了第三、四类（真实模型调用序列 + prompt+completion
token）各一份真机内容**——`adb reverse` + 本机 mock 走真实 provider 代码路径，
`round-7098bf9e-4bcb-41f2-a0e7-e05d0395fcc9` 一轮 4 个真实请求，
`spent_tokens` 773 / 236 与落库逐条相等。**证据登记见「C1 真机证据采集第二轮」。**

⚠️⚠️⚠️ **但它仍然不改变任何判定**：**mock 不是真实模型**（是「真实 provider 代码路径 +
真实 HTTP + 真实 SSE + 真实 usage 报文」，不是「真实 LLM 推理」）；**roundtable / vote
的真实调用零份**；**酒馆本体、真机 UI、相机扫码真机链路仍零份**。所以
**十例状态仍全 `unverified`，0/10 不变。**

⚠️⚠️⚠️ **敏感项：`858c11d0` 是第一个「真机证据 → 定位生产 bug → 改 main 源码」的
闭环**，逐条登记（完整版见「C1 真机证据采集第二轮」的证据登记与「已知遗留与风险」）：

- **症状**：落库的那条助手消息 `role_id="a"` 却有 **A、B 两段正文**，而 usage 是 **B 的**；
  `group_runs` 是 `status=FAILED` / `committed=["a"]` / `skipped=["c"]` /
  `reason=role_failed` / `error_message=本轮没有产出内容`。**请求本身是发的，断的是
  产出归属，不是模型调用。**
- **根因**：`GenerationLoop` 复用末尾助手消息的条件只看
  `messages.lastOrNull()?.role == ASSISTANT`。但群聊里**上一位角色的发言同样是末尾
  ASSISTANT 消息**，而那条已经 `roleId != null`（已提交、已计入 `committed_role_ids`），
  复用它就把本角色的回复**并进上一位那条消息**（同一个 `UIMessage` 多出一个 Text part）；
  随后 `stampGroupTurn` 因为找不到 `roleId == null` 的助手消息而返回 null，该角色被误判
  「本轮没有产出内容」。`handleTextGenerationResult` 那条非流式分支有**同一个坑**，
  一并修了。
- **修法**：复用条件加一条 `roleId == null`（`reusableTrailingAssistant`），
  非流式分支先把「已提交的末尾助手消息」摘掉再落到追加新消息的分支。
  ⚠️ **单聊不受影响**：那里 `roleId` 恒为 null，复用条件与改动前**逐字相同**。
- **定性**：这是**生产代码的真 bug**，不是测试缺陷——与本批 `bff7b0c6` / `a9d2077e`
  那两处「测试自己写错了」的修正**不是同一类，别混着记**。

⚠️ 本文件的登记提交同样**不计入**任何计数，且按上面同样的理由**不写自己的 SHA**
（本文件每次被自己提交都会产生一个新 commit，写进去就得再改一次、永远差一个，
与本台账刻意锚死右端是同一个道理）。用
`git log --oneline -1 -- docs/eval/c1-group-chat.md` 可查。

#### 再往后一批：真实公网网关证据 + P0/P1/P2 三批补测与两个端点/脚手架修复（`af104850..6ba95422`）

锚点之后又出现了这批 **6 个**（`git log --oneline af104850^..6ba95422` 实测 = 6，
区间内 `--merges` 为 **0**）。**同样不计入本台账**——理由与前几批一致：不改统计区间、
不改 `--no-merges` 口径、不引入新的类型前缀或子包标签。所以 **78 / 6 / 17、前缀分布、
子包分布一个数都没动**，`d45ebd10~1..1b0e04a9` 那 **78 个 commit 没有被重算**。
⚠️ 上面的「类型前缀分布」（`feat` 25 / `fix` 20 / `test` 14 / `coder` 7 / `refactor` 5 /
`docs` 5 / `chore` 1 / `build` 1 = 78）与「子包标签分布」那张表**都是截至 `1b0e04a9`
的数**，本批新增的 `coder` / `test` 前缀**不进那两张表**。

| SHA | 标题 | 性质 |
|---|---|---|
| `af104850` | `coder: validate 拒绝 role_id 取保留值 __summary__，堵住合成节点冒名伪造路径` | **功能（main，`core/data/model/GroupChat.kt` +26）** + 测试（`GroupChatTest.kt` +102，18 → 22） |
| `9844cbc4` | `coder: 补 mention_role_ids 放行分支的 JVM 覆盖（作者/被@/无关三向 + 反向对照）` | 测试（`GroupTurnCoordinatorTest.kt` +118，63 → 66） |
| `e7ad2a77` | `coder: 补视角过滤四个覆盖盲区（议长遍历/跨轮负向/同角色多条/9 对全遍历）` | 测试（`GroupTurnCoordinatorTest.kt` +265，66 → 72） |
| `87f9c209` | `coder: 五个会话操作端点加群聊门禁，外部调用不再破坏轮次账目` | **功能（main，`core/network/routes/ConversationRoutes.kt` +20 + 新增 `GroupConversationOperationGuard.kt` 91 行）** + 测试（新增 2 个 JVM 类：`GroupConversationOperationGuardTest` 148 行 / 7 例、`GroupOperationApiGuardSourceGuardTest` 119 行 / 4 例） |
| `e6764293` | `coder: failGroupTurn 补回收平票裁决脚手架，议长裁决失败不再永久残留 SYSTEM 指令` | **功能（main，`ChatManager.kt` +7）** + 测试（新增 `GroupTieBreakScaffoldingDropSourceGuardTest` 116 行 / 3 例） |
| `6ba95422` | `test(c1): 补真实网关（内置免费 provider）整轮调用序列与 token 证据` | 测试（`C1LiveModelSequenceTest.kt` **+539 insertions，生产代码零改动**） |

净 diff（`git diff --stat af104850^..6ba95422`）：**10 files / 1551 insertions /
0 deletions**。⚠️ **零删除**是这批的一个特点——**没有删任何一行既有代码或断言**，
6 个 commit 全是「加函数 / 加用例 / 加注释」。

**净效果**：`:app:testDebugUnitTest` **92 类 / 723 例 → 97 类 / 754 例**
（`+5 类 / +31 例`，0 失败 / 0 错误 / 0 跳过，逐个 XML 的 `tests` 属性汇总实测）；
C1 相关 JVM 测试类台账 **30 类 / 335 例 → 35 类 / 366 例**（`+5 类 / +18 例`，
另有两个老类用例数更新：`GroupTurnCoordinatorTest` 63 → 72、`GroupChatTest` 18 → 22）；
**app `androidTest` 全量 `@Test` 本轮实测 61 条**（早前记的 56 条是
`C1LiveModelSequenceTest` 建之前数的，56 + 5 = 61）；
Room 版本不变（仍是 32）；lint 磁盘实测 app **584W+6H = 590**、15 模块
**617W+7H = 624**（比上一窗口的 587/6/593、620/7/627 **各少 3 条，差值全在 `app`**）。
证据登记见「C1 真机证据采集第三轮」「C1 内核四批补测与修复」「HTTP 端点群聊门禁 +
`failGroupTurn` 回收平票脚手架」三节。

⚠️⚠️ **这批与前面所有批次的性质都不同，必须说清楚**：前面每一批都**没有产出契约 `:206`
意义上的验收证据**，这一批**第一次让第三类（实际模型调用序列）与第四类（token 计数）
同时脱离 mock**——用的是生产自带的免费公网网关（`DefaultProviders.kt:281-318`），
**设备直连公网**，真实 usage 6803+159 / 6667+68 / 6880+102，
**Σ = 20679 与落库 `group_runs.spent_tokens` 精确相等**，真实调用序列
`deepseek-v4-flash → glm-5.2 → deepseek-v4-flash` 与按角色绑定一致。

⚠️⚠️⚠️ **但它仍然不改变任何判定**，三条硬理由（详见「C1 真机证据采集第三轮」的
「⚠️⚠️⚠️ 为什么十例仍然 0/10」）：**① 那个用例没有一次全绿记录**（成功后被 OEM 回收
策略杀进程、落盘文件被卸载清空，数字来自内存快照）；**② 模型名不是 wire 级抓包**
（由 `message.modelId` 的 uuid 反查，JSON 里已用 `wire_model_name_provenance` 标明）；
**③ 真机 UI 端到端仍零份**。C1-03 / C1-04 / C1-09 / C1-10 直接踩「真机或自动化证据」
那条。**十例状态仍全 `unverified`，0/10 不变。**
✅ **订正（2026-10-06 第四轮）**：理由 **① 已消**——该用例已在真机 `exit 0` 全绿、落盘
JSON 拉回并记了 SHA-256；**② ③ 仍在**，所以 **0/10 不变**。
✅ **再订正（HEAD `74d82476`，零设备）**：理由 **② 的成因已定位并修复**——「app 不保存
响应的 `model` 字段」不再成立（是 `StreamChunkHandler` 把 `chunk.model` 丢弃了，
已修；wire 名现落 `UIMessage.wireModelName`）。⚠️ **但理由 ② 本身仍未消**：
**仪器测试一行没跑**，设备上那份证据文件**仍是反查值**，要等真机重跑才会变成 wire 值。
所以 **0/10 依旧不变**。证据与取证局限见「为什么十例仍然 0/10」第 2 条。

⚠️ **这批同时也是锚点之后第一次「一批里既有 main 源码修复、又有新增仪器用例、
又有新增源码护栏」的混合批**（前几批要么纯测试、要么纯功能）——所以它同时提供了
「变异检验真红」与「本机 6/6 通过」两种证据，而**真机全绿在那个时点仍然是零份**。
✅ **订正（2026-10-06）**：该用例的真机全绿见「C1 真机证据采集第四轮」。

⚠️ 本文件的登记提交同样**不计入**任何计数，且按上面同样的理由**不写自己的 SHA**
（本文件每次被自己提交都会产生一个新 commit，写进去就得再改一次、永远差一个，
与本台账刻意锚死右端是同一个道理）。用
`git log --oneline -1 -- docs/eval/c1-group-chat.md` 可查。

#### 再往后一批：平票脚手架三处漏回收 + 残留旧 job 两道守卫（`e72793e6..8ccc0264`）

锚点之后又出现了这批。**同样不计入本台账**——理由与前几批一致：不改统计区间、
不改 `--no-merges` 口径、不引入新的类型前缀或子包标签。所以 **78 / 6 / 17、前缀分布、
子包分布一个数都没动**，`d45ebd10~1..1b0e04a9` 那 **78 个 commit 没有被重算**。
⚠️ 上面「类型前缀分布」（`feat` 25 / `fix` 20 / `test` 14 / `coder` 7 / `refactor` 5 /
`docs` 5 / `chore` 1 / `build` 1 = 78）与「子包标签分布」那张表**都是截至 `1b0e04a9`
的数**，本批新增的 `coder` / `test` / `docs` 前缀**不进那两张表**。

⚠️⚠️ **`361c7cf6` 不是 `8ccc0264`，如实登记**：本批的最后一个代码 commit 是
**`8ccc0264`**，而仓库当前 HEAD 是 **`361c7cf6`**。
`git log --oneline -14` 实测，`361c7cf6` 排在 `8ccc0264` **之后**，是**另一个人**的
提交：

```
361c7cf6 docs: ChatMessage groupChat 的 KDoc 改正 fork 的禁因（静默换会话类型，非账目错位）
```

- `git show --stat 361c7cf6` = **1 file changed / 14 insertions / 3 deletions**，
  改的是 `core/ui/components/message/ChatMessage.kt` 的 **KDoc**。
- ⚠️ **它是代码文件改动，不是 docs-only**（提交标题里的 `docs:` 前缀指的是「改的是
  文档性内容」，`git show --stat` 显示它碰的是 `ChatMessage.kt` 这个 main 源文件）。
- ⚠️ **它不属于 C1**：它订正的是「已知遗留与风险」第 20 条那条 KDoc 里 **fork 那一半
  的禁因措辞**（原来说「会让群运行日志的账面和实际轮次错位」，改成「静默换了会话
  类型」——与 `87f9c209` 的拒绝文案对齐）。⚠️ **它没有动任何测试、没有动台账数字、
  没有改任何 C1 判定**，所以 `:app:testDebugUnitTest` 的 99 / 783 与它无关。
- **如实标注**：本节下面那张表里的 6 行是 `e72793e6..8ccc0264` 加上 `361c7cf6`；
其中**前 5 行属于本批 C1 修复**，**第 6 行不属于**。

| SHA | 标题 | 性质 | 属 C1？ |
|---|---|---|---|
| `e72793e6` | `coder: cancelActiveGroupRun 与 abandonDanglingGroupRuns 补回收平票裁决脚手架` | **功能（main，`feature/chat/ChatManager.kt` +22 / −1）** | ✅ |
| `8a5f89d7` | `test: 平票脚手架回收护栏补 cancel/abandon 两条调用点与顺序、条件断言` | 测试（`GroupTieBreakScaffoldingDropSourceGuardTest.kt` +92 / −5，**3 → 7 条**） | ✅ |
| `2fdee352` | `coder: 群轮次判死后残留 job 不得推进状态、不得把产出盖到新轮` | **功能（main，`ChatManager.kt` +54 / −、`GroupTurnCoordinator.kt` +100 新增 `checkCommitAdmission` / `Advance.Halted`）** + 测试（新增 `GroupStaleJobCommitSourceGuardTest.kt` 267 行 / 7 条；`GroupTurnCoordinatorTest.kt` 72 → **78**） | ✅ |
| `ef716dd3` | `coder: onFailure→failGroupTurn 补归属与生死守卫，残留旧 job 不得改写新轮` | **功能（main，`ChatManager.kt` +38 / −4、`GroupTurnCoordinator.kt` +42 新增 `checkFailureAdmission`）** | ✅ |
| `8ccc0264` | `test: 失败收尾归属守卫补内核真单测（终态拒收/正常失败与续跑放行）与源码文本护栏` | 测试（新增 `GroupStaleJobFailureSourceGuardTest.kt` 292 行 / 6 条；`GroupTurnCoordinatorTest.kt` 78 → **84**） | ✅ |
| `361c7cf6` | `docs: ChatMessage groupChat 的 KDoc 改正 fork 的禁因（静默换会话类型，非账目错位）` | KDoc（`core/ui/components/message/ChatMessage.kt` +14 / −3，**零测试改动**） | ❌ **不属于 C1**（见上） |

⚠️ **前缀分布本批新增 2 个 `coder` + 2 个 `test` + 1 个 `docs`，但都不进台账**——
`coder` 那两个（`e72793e6` / `2fdee352` / `ef716dd3` 实为 **3 个** `coder`，
`8a5f89d7` / `8ccc0264` **2 个** `test`，`361c7cf6` **1 个** `docs`）
同理不进：它们都在锚点之后、都不改统计基线。

**净效果**：`:app:testDebugUnitTest` **97 类 / 754 例 → 99 类 / 783 例**
（`+2 类 / +29 例`，0 失败 / 0 错误 / 0 跳过，逐个 XML 的 `tests` 属性用 Python +
ElementTree 汇总实测；逐批增量见「测试结果（实测 XML 汇总）」段那张三行表）；
C1 相关 JVM 测试类台账 **35 类 / 366 例 → 37 类 / 395 例**（`+2 类 / +29 例`，
口径未变，新口径复算输出 `rows 37 / declared sum 395 / mismatch vs XML: []`）；
lint **0 error**、app **584W+6H=590**、15 模块 **617W+7H=624**（与上一窗口**逐位相同**，
本轮碰过的 5 个文件命中数全部为 0）；`app/src/androidTest` 全量 `@Test` **仍是 61 条**；
Room 版本不变（仍是 32）。
⚠️ **净 diff 是「零删除既有断言」吗**：②③ 两个 commit 里
`ChatManager.kt` 与 `GroupTurnCoordinator.kt` 有少量 −行（替换守卫位置），
`GroupTurnCoordinatorTest.kt` 的 −6 行是**改既有断言的措辞**（见下）；
⚠️ **但没有删任何一条既有用例**——三个在册类的 `tests` 属性只增不减
（72 → 78 → 84、3 → 7、+7 新类、+6 新类）。

⚠️⚠️⚠️ **本批同样不改变任何判定**，硬理由两条：
**① 这三批是「发现并堵住真缺陷」，不是契约 `:206` 点名的四类产物**——本轮零设备、
零 `adb`、零 gradle，**四类产物一份未增**；**② ②③ 针对的残留旧 job 路径从未在真机上
复现过**（`abandonDanglingGroupRuns` 不取消任何 job 这一前提是**读代码 + 子代理核实的
`cancelJobs()` 调用点**，不是**真机观测**；路径 B 连这个级别都没有，见该节的诚实标注）。
**十例状态仍全 `unverified`，0/10 不变。**

⚠️ **一处测试改动值得单独记（不是「改既存测试文件」的第一次，但形状不同）**：
`2fdee352` / `8ccc0264` 改了 `GroupTurnCoordinatorTest.kt` 里**既有用例的断言措辞**
（`git diff --stat` 里那 −6 行），目的是让新加的守卫用例与既有用例**共用同一批
helper / 断言辅助**。⚠️ **断言本体没有被削弱**：既有用例的 `tests` 条数与断言语义
逐条未变，只是把重复的构造代码提到了共用位置。
⚠️ **别把它与「因为修生产代码的缺陷、被迫把断言的期望字面串跟着改」混为一谈**
（那个形状见「已知遗留与风险」第 9 条，`ConversationTypeFilterSourceGuardTest`
第 68-73 行那一次）——**本批没有出现那个形状**：`Advance` / `CommitAdmission` 是
**新增的 sealed 分支**，既有 `More` / `BudgetStopped` / `Finished` 三条断言一字未动。

⚠️ 本文件的登记提交同样**不计入**任何计数，且按上面同样的理由**不写自己的 SHA**
（本文件每次被自己提交都会产生一个新 commit，写进去就得再改一次、永远差一个，
与本台账刻意锚死右端是同一个道理）。用
`git log --oneline -1 -- docs/eval/c1-group-chat.md` 可查。

#### 再往后一批：真机两个 Compose 必崩 + Coil 结构性修复 + 四项代码债（`33eb801e..bed09118`）

⚠️⚠️ **锚点 `1b0e04a9` 仍然没有被重算**——本批 10 个 commit
（`git log --oneline 361c7cf6..a74c1820` 实测 **13** 个，其中 `4336831f` / `d96d64e1` /
`dee6cf67` 三个是文档 commit、不属本批；本表逐条列的就是剩下那 **10** 个）
**一个都不计入**本台账，理由与前几批逐字相同：不改统计区间、
不改 `--no-merges` 口径、不引入新的类型前缀或子包标签。所以 **78 / 6 / 17、前缀分布、
子包分布一个数都没动**，`d45ebd10~1..1b0e04a9` 那 **78 个 commit 没有被重算**。

| SHA | 标题 | 性质 |
|---|---|---|
| `33eb801e` | `coder: 修群配置面板必崩——补上漏传的 imageVector = tune` | **功能（main，`GroupChatPage.kt` +6）** |
| `5d58f923` | `coder: 加 PrimaryBottomSheet 调用点图标参数源码护栏（3 例，含群配置面板定点回归）` | 测试（`PrimaryBottomSheetIconSourceGuardTest.kt` 271 行 / 3 条） |
| `71d1439e` | `coder: 修群配置面板滚动必崩——补 scrollable=false 去掉嵌套滚动层` | **功能（main，`GroupChatPage.kt` +24 / −1）** |
| `54c196ca` | `coder: 加底部弹层滚动嵌套源码护栏（3 例，含群配置面板定点回归）` | 测试（`BottomSheetScrollSourceGuardTest.kt` 327 行 / 3 条） |
| `ea482935` | `coder: 修 Coil 单例崩溃——把 ImageLoader 定义从 RouteActivity 搬到 KhatKitApp` | **功能（main，`KhatKitApp.kt` +63 / −、`RouteActivity.kt` −34）** |
| `2766afc7` | `coder: 加 Coil ImageLoader 定义点唯一性的源码护栏（6 例）` | 测试（`CoilImageLoaderSourceGuardTest.kt` 427 行 / 6 条） |
| `fa36c65c` | `coder: ChatService 群聊 fail-loud 闸门 + provider 按产物改名（消除命名陷阱）` | **功能（main，`ChatService.kt` +48、`AppHiltModule.kt`、`ChatManager.kt` KDoc）** |
| `06cf6181` | `coder: 群聊轮次判死改为分批清空；onSuccess 的 null 分支改为按 runToken 清镜像` | **功能（main，`ChatManager.kt` +105 / −）** |
| `bed09118` | `test: 四条源码护栏（ChatService fail-loud / 判死清空 / 按令牌清镜像 / fork 禁因）` | 测试（4 个文件 / +621 / −13：3 个新类 4 + 5 + 2 例 + `GroupStaleJobCommitSourceGuardTest` **+2** 例） |
| `a74c1820` | `docs: 遗留清单 B9/B10 改记已修，新增 B9b（ChatService 第二份生成管线）` | 文档（只改 `docs/beyond-operit-implementation-status.md`） |

⚠️ **前缀分布本批新增 6 个 `coder` + 3 个 `test` + 1 个 `docs`，但都不进台账**——
⚠️ **「类型前缀分布」（`feat` 25 / `fix` 20 / `test` 14 / `coder` 7 / `refactor` 5 /
`docs` 5 / `chore` 1 / `build` 1 = 78）与「子包标签分布」那张表都还是截至 `1b0e04a9`
的数**，本批这 10 个里的 `coder` / `test` / `docs` **不进那两张表**。

**净效果**：`:app:testDebugUnitTest` **99 类 / 783 例 → 105 类 / 808 例**
（`+6 类 / +25 例`，0 失败 / 0 错误 / 0 跳过，逐个 XML 的 `tests` 属性用 Python +
ElementTree 汇总实测；逐类增量见「测试结果（实测 XML 汇总）」段那张七行表）；
C1 相关 JVM 测试类台账 **37 类 / 395 例 → 40 类 / 408 例**（`+3 类 / +13 例`，
口径未变，新口径复算输出 `rows 40 / declared sum 408 / mismatch vs XML: []`）；
lint **0 error**、app **584W+6H=590**、15 模块 **617W+7H=624**（与上一窗口**逐位相同**）；
`app/src/androidTest` **git 跟踪口径仍是 61 条**（把未入库的
`C1GroupUiE2EFixtureTest.kt` 那 3 条算进去是 **64**）；Room 版本不变（仍是 32）。

⚠️⚠️⚠️ **本批同样不改变任何判定**，三条硬理由：
① **两个 UI 崩溃的修复与 Coil 修复都不产出契约 `:206` 点名的任何一类产物**；
② **四项代码债的运行时行为全是零证据**——`require` 真不真抛、分批循环的真机行为、
`remove(key, value)` 的真实并发效果、`produced == null` 时 `failGroupTurn` 提前 return
的具体触发，**一条都没观测过**；三条 gradle 命令只证明「编译过 + 单测绿」，
**不构成任何契约验收证据**；③ **真机 UI 端到端仍然零份**——本轮只验了**群配置面板的
渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**，面板里的
**保存 / 二维码 / 扫码三个业务动作一个都没点**。
**十例状态仍全 `unverified`，0/10 不变。**

⚠️ **一个必须同时记的边界**：`ea482935` 修掉的 Coil 崩溃是**稳定复现**过的（磁盘上那份
`tests="10" failures="1"` 的 XML 就是它的现场），但**修复本身本轮没有在设备上复跑**——
所以「全量仪器测试能跑完了」这句话**目前零证据**，验证命令就一条：
`./gradlew --offline :app:connectedDebugAndroidTest`。

#### ⚠️⚠️ 补登：上一批之后到 HEAD `72a5e548` 的 11 个 commit（2026-10-06）

⚠️ **本小节是补登，不是新批次**——上一批（`33eb801e..bed09118`）的表格**漏登了**
`de05a908` 起的 11 个 commit。核验命令与实测输出：

```
git log --oneline db4cdd77..72a5e548 | wc -l   # 11
git log --oneline db4cdd77..72a5e548 | tail -1 # de05a908（就是最早那一个）
```

⚠️⚠️ **区间下界为什么是 `db4cdd77` 而不是 `a74c1820`**：`a74c1820..72a5e548` 实测是
**17** 个，多出来的 6 个是**已经登记过的**——`2d1f08db` / `7f3d80e5`（第六批那两次文档
登记）与 `8622bf19` / `aee20f85` / `88777898` / `db4cdd77`（**「⚠️ 第七批」那一节**
逐条列过）。**已登记的不重复登记**，所以下表从 `de05a908` 起。

⚠️ **锚点 `1b0e04a9` 仍然没有被重算**：这 11 个**一个都不计入**本台账，理由与前几批
逐字相同——不改统计区间、不改 `--no-merges` 口径、不引入新的类型前缀或子包标签。
所以 **78 / 6 / 17、前缀分布、子包分布一个数都没动**。

| SHA | 标题 | 性质 |
|---|---|---|
| `de05a908` | `docs(c1): 修证据登记表 C1-05 行的两处裸竖线（13 列 → 11 列），C2 待办转已修` | 文档（只改本文，+18 / −5） |
| `5be31807` | `test(c1-s): 新增 c1_doc_stats.py，把五处统计口径固化成可复算脚本（自带反空跑自检）` | 工具（`tools/verification/c1_doc_stats.py` 新增 **786 行**；⚠️ **零测试用例**） |
| `e88d15fd` | `docs(c1): 登记 db4cdd77 那批（importGroup 契约缺口修复）+ 台账 42 类/455 例 + 复算脚本文档；十例仍 10/10 unverified` | 文档（只改本文，+249 / −21） |
| `69d88214` | `docs(c1): 实施状态补第七批（importGroup 契约 :205 缺口修复）与三条遗留缺口` | 文档（只改 `docs/beyond-operit-implementation-status.md`，+57） |
| `d00880fc` | `test: C6/C7/C8 三处口径显式化 + 补 onEdit 群聊门禁与顺序不变量护栏` | ⚠️ **含 3 个 `app/src/main` 文件**（见下） + 4 个测试文件（+662 / −43） |
| `3d04c82c` | `test: 修 addMigrations 切分退化（锚点缺失退化成扫全文恒绿）+ 加反空跑断言` | 测试（**只改 1 个既有文件** `ConversationGroupCardsSchemaTest.kt`，+51 / −1，**净增 0 条 `@Test`**） |
| `5e4582ed` | `test: 修 WHERE 子句切分退化 + 三处调用点加反空跑护栏` | 测试（**只改 1 个既有文件** `ConversationDrawerFolderScopeTest.kt`，+65 / −7，**净增 0 条 `@Test`**） |
| `5435e403` | `test: 修 description 切分退化（退化后 x 计数仍对得上而恒绿）+ 加反空跑断言` | 测试（**只改 1 个既有文件** `SkillsToolsTest.kt`，+61 / −1，**净增 0 条 `@Test`**） |
| `4ee1ad5c` | `fix: 标题生成接上 viewer 过滤（群聊下其他角色发言不再进 TITLE 模型）` | **功能（main 2 文件，+133 / −2：`ChatManager.kt` +44、新增 `SummaryViewerScope.kt` 91 行）** |
| `8111d178` | `docs: 修正 ChatService fail-loud 注释里三处 generateText 的归属` | 文档（⚠️ **改的是 Kotlin 源码里的注释**：`ChatService.kt` +17 / −3，**零实现改动**） |
| `72a5e548` | `test: SummaryViewerScope 纯函数真单测 12 条 + 接线源码护栏 5 条（自带反空跑断言）` | 测试（**2 个新类 / +616**：`SummaryViewerScopeTest.kt` 344 行 12 条 + `SummaryViewerScopeWiringSourceGuardTest.kt` 272 行 5 条） |

⚠️ **`d00880fc` 里的 3 个 `app/src/main` 文件要单独说清**（它是这一串里唯一一个
**动了生产代码**的）：① `core/ui/components/message/ChatMessageActions.kt`（+62 / −）
——**Edit 卡片此前确实没有 `if (!groupChat)` 门禁**（另外三个动作都有），群聊长按角色
发言会先 dismiss 再被 `canEditMessage` 静默丢弃（就是「点了没反应」）；
**但照抄 `if (!groupChat)` 会连带关掉群聊里合法的「改用户提问」**，所以改成**按消息收口**
`!groupChat || <消息>.role == MessageRole.USER`（展示层 + 触发层各一处，单聊
`groupChat = false` 时**逐字不变**）；② `feature/chat/ChatList.kt`（+12 / −）——同一个
裸 wiring；③ `core/data/ai/tavern/TavernChatCodec.kt`（+21 / −）——**纯注释**，
把 `config == null` 的成因从「两种」改成「四种」（见遗留第 32 条）。
⚠️ 另外它还改了 `androidTest` 侧的 `C1LiveModelSequenceTest.kt`（+38，**纯 KDoc**，
把「模型名由 `modelId` uuid 反查、不是 wire 级抓包」这条口径写进用例文档）。

⚠️⚠️ **`3d04c82c` / `5e4582ed` / `5435e403` 三条是「已有测试恒绿」的修复，不是补用例**：
它们各改 1 个既有文件、加 `assertTrue(end > start)` 这类**前置检查**与**反空跑断言**，
**净增 0 条 `@Test`**（所以台账与总例数一个数都没动）。三处的退化形状各不相同，
共同点是**切分器在锚点缺失时退化成「扫全文」而断言仍然绿**——
这正是 C8 那条「护栏必须自带反空跑断言」的续集。⚠️ **改的是测试代码不是生产代码。**

**净效果**（⚠️ 全部是 `python3 tools/verification/c1_doc_stats.py` 的实测输出，
**不是手抄**）：`:app:testDebugUnitTest` **107 类 / 855 例 → 110 类 / 881 例**
（`+3 类 / +26 例`，**0 失败 / 0 错误 / 0 跳过**；逐 commit 的类/例实测：
`db4cdd77..d00880fc` 之前一路 **107 / 855** → `d00880fc` **108 / 864**（`+1 类 / +9 例`）
→ `3d04c82c` / `5e4582ed` / `5435e403` / `4ee1ad5c` / `8111d178` **一路 108 / 864**
（`4ee1ad5c` 是功能修复、`8111d178` 只改注释，所以都是 0 增量）
→ `72a5e548` **110 / 881**（`+2 类 / +17 例`，正好是 12 + 5））。
lint **0 error**、app **584W+6H=590**、15 模块 **617W+7H=624**（**与上一窗口逐位相同**）；
`app/src/main` 那 5 个改动文件（`ChatManager.kt` / `SummaryViewerScope.kt` /
`ChatService.kt` + 两个新测试文件）**lint 命中数逐个实测为 0**；
`app/src/androidTest` **git 跟踪口径仍是 61 条**（含未入库的
`C1GroupUiE2EFixtureTest.kt` 是 **64**）；Room 版本不变（仍是 32）；
`tools/verification/c1_doc_stats.py` 五个分组共 **18 条**。

⚠️⚠️⚠️ **本串 11 个 commit 同样不改变任何判定**，三条硬理由：
① **零设备、零 `adb`**——契约 `:206` 点名的四类产物**一份未增**；
② `4ee1ad5c` 修的是**真实存在**的群聊泄漏（标题/摘要绕过 viewer 过滤），
但**修掉缺陷不是验收证据**（判定规则第五条），且泄漏本身**零真机观测**；
③ **十例状态列一个格都没动，仍是 10/10 `unverified`**（`4ee1ad5c` / `8111d178` 改的是
标题与摘要这条**独立于十例点名路径**的链路）。

⚠️ **本串暴露的两处台账账实不符，上一轮如实登记而未修；本轮（约束放开后）已修完**：
`c1_doc_stats.py` 的 `ledger` 逐行核对曾报 **FAIL**，两行与源码不符——
`GroupStaleJobCommitSourceGuardTest` **声明 9 / 源码 10**、
`GroupStaleJobFailureSourceGuardTest` **声明 6 / 源码 7**（成因是 `d00880fc` 给这两个
文件各加了 1 条 `@Test`）。
✅ **本轮已把这两行的声明例数改成实测值**（**9 → 10** / **6 → 7**）。⚠️ **口径澄清**：
台账的「声明例数」是**事实陈述**（「这个类现在有 N 条测试」），**必须随代码更新**；
它与「状态列按窗口追加不覆写」是**两回事**，上一轮把两者混为一谈才没敢动这一个数。
⚠️ 实测同时确认：这两个类的 **XML `tests` 属性与源码 `@Test` 计数一致**
（10 / 7 / 10 / 7 逐个对得上），所以**「计数取自 XML」这条台账口径与本轮改动不冲突**。

⚠️ **本串涉及的 5 个测试类，已在本轮并进「C1 相关 JVM 测试类台账」的 3 个**——
`GroupEditActionVisibilitySourceGuardTest` **5** 条 +
`SummaryViewerScopeTest` **12** 条 + `SummaryViewerScopeWiringSourceGuardTest` **5** 条，
台账随之 **42 类 / 455 例 → 45 类 / 479 例**（新增 3 类 22 例 + 两个在册类各 +1）。
⚠️ **另外 2 个仍然不进表**，本轮逐个复核过：
`BottomSheetScrollSourceGuardTest`（实测 **4** 条，`d00880fc` 从 3 条扩到 4）与
`CoilImageLoaderSourceGuardTest`（**6** 条）**整类不属于 C1 验收范围**——口径就是本文
「测试结果」段那个 ⚠️⚠️ 写明的「**整类**是否属于 C1 验收范围」，不是「有没有一条用例碰到
C1 的文件」。⚠️ **本轮也订正上一轮的一处笔误**：`GroupEditActionVisibilitySourceGuardTest`
被记成「1 条」，实测是 **5** 条（`git log --diff-filter=AM` 只有 `d00880fc` 一个 commit，
即它**自建立起就是 5 条**）。
⚠️ **上一轮「不代填那个合计数（填错比不填更糟）」的判断本轮不再适用**：合计已按实测重算，
`python3 tools/verification/c1_doc_stats.py --only ledger` 现报
**`45 行 / 479 例 / 逐行核对 45 行全部相等`，0 FAIL**。

#### ⚠️⚠️ 补登：`72a5e548` 之后的 commit（2026-10-06，含台账订正这一轮）

⚠️ **本小节是补登 + 本轮自己的台账订正，不是新批次**。核验命令与实测输出：

```
git log --oneline 72a5e548..HEAD | wc -l
git log --oneline 72a5e548..HEAD
```

⚠️ **锚点 `1b0e04a9` 仍然没有被重算**：下面每一个 commit **一个都不计入**本台账，
理由与前几批逐字相同——不改统计区间、不改 `--no-merges` 口径、不引入新的类型前缀或
子包标签。所以 **78 / 6 / 17、前缀分布、子包分布一个数都没动**（脚本本轮实测仍是
`78` / `--merges` = `0`）。

| SHA | 标题 | 性质 |
|---|---|---|
| `cae27138` | `docs(c1): 证伪遗留第 28 条（ChatService.finishInterruptedPendingTools 零 generateText），补登真泄漏第 33 条 + 补登 11 个 commit + C9 五条边界来源指针` | 文档（只改 `docs/beyond-operit-implementation-status.md`，**纯文档**） |
| `40cc8e89` | `docs(c1): 实施状态补第八批（标题/摘要群聊泄漏已修 + 遗留第 28 条已证伪）+ 五条诚实边界的逐条来源指针 + 统计口径改 110 类/881 例` | 文档（只改 `docs/beyond-operit-implementation-status.md`，**纯文档**） |
| （本文件此次提交） | `docs(c1): 台账订正——两个在册类声明例数 9→10 / 6→7、3 个新类并入、合计 42 类/455 例 → 45 类/479 例` | 文档（**只改本文与实施状态那篇**；⚠️ **SHA 见 `git log --oneline -1`，本文无法预先写死自己的 SHA**） |

⚠️⚠️ **这三个 commit 全部是纯文档，零 Kotlin / 零测试 / 零脚本改动**
（`c1_doc_stats.py` 本轮**一个字都没改**，它的 `--self-test` 也照跑通过），
所以**它们不改变任何一个测试类名或例数**。⚠️ 唯一被它们改变的是**台账的声明值**——
而台账声明值是**事实陈述**，必须随代码走，这也是它们该做的事。

⚠️ **本轮唯一实质改动就是「收掉上一轮自己造成的那处不一致」**，逐条：
① `GroupStaleJobCommitSourceGuardTest` 声明 **9 → 10**、
`GroupStaleJobFailureSourceGuardTest` 声明 **6 → 7**（成因是 `d00880fc` 各加了 1 条
`@Test`，上一轮如实登记但**没改**）；② 3 个新类并入台账（合计 **+3 类 / +22 例**）；
③ 合计行**新起一行**（`455` / `42 类` 那几处按惯例**保留不覆写**），实测值
**45 类 / 479 例**。⚠️ **`git diff` 里没有任何一行被删除**——锚点那 **78 / 6 / 17**
与各窗口的历史合计**一个数都没动**。

#### ⚠️⚠️ 再往后一批：wire 级模型名（`f1bf516e..74d82476`，2026-10-06，**零设备**）

核验命令与实测输出：

```
git log --oneline 40cc8e89..74d82476 | wc -l   # 19（含本文自己的两次文档提交）
git log --oneline ea6b7c4c..74d82476 | wc -l   # 8，就是下面这 8 个
```

⚠️ **锚点 `1b0e04a9` 仍然没有被重算**：下面每一个 commit **一个都不计入**本台账，
理由与前几批逐字相同——不改统计区间、不改 `--no-merges` 口径、不引入新的类型前缀或
子包标签。所以 **78 / 6 / 17、前缀分布、子包分布一个数都没动**（脚本本轮实测仍是
`78` / `--merges` = `0`）。

| SHA | 标题 | 性质 |
|---|---|---|
| `5186349f` | `fix(c1): parseBallot 跳过 markdown 围栏代码块，未闭合围栏失败关闭` | 测试 + 功能（`GroupChat.kt` + `GroupChatTest.kt`，**净增 4 条 `@Test`**）——⚠️ **这 4 条是 app 单测 881 → 893 差额的全部来源** |
| `bd31c6cf` | `docs(c1): 台账 GroupChatTest 26→29、合计 484→487（HEAD 8a24b5d0 实测，追加不覆写）` | 文档（纯文档） |
| `8a24b5d0` | `coder: 收紧 parseCandidates：声明只认首个非空行 + 候选 id 逐个规范化` | 功能 + 测试 |
| `ea6b7c4c` | `docs(c1): 台账 GroupChatTest 29→33、合计 487→491（围栏假阳性那批，追加不覆写）` | 文档（纯文档） |
| `f1bf516e` | `feat(ai): UIMessage 增加 wireModelName 字段` | **功能（`ai/`，`Message.kt` +6 / −0）**——⚠️ **硬理由①的修复第一块** |
| `0c239788` | `fix(ai): Finish 落盘 wire 模型名，补上非流式对称路径` | **功能（`ai/`，`StreamChunkHandler.kt` +16 / −2）**——硬理由①的修复第二块 |
| `ae285843` | `test(ai): 新增 WireModelNameProvenanceTest，钉死 wire 级模型名` | 测试（`ai/src/test/`，**新增 1 个类 / 268 行 / 9 条 `@Test`**） |
| `35b90d67` | `feat(tools): 新增 c1_wire_model_probe.py，抓网关自报的 wire 模型名` | 工具（`tools/verification/`，**新增 259 行**；⚠️ **零测试用例**，且**必须真给 `--api-key` 才能跑**） |
| `ab2f9b65` | `docs(app): 修正 ChatList 注释里 StreamChunkHandler 的行号锚点` | ⚠️ **改的是 Kotlin 源码里的注释**（`ChatList.kt` +1 / −1，**零实现改动**）——把行号锚点 `73`/`329` 改成 `77`/`341` |
| `d7971ba1` | `test(app): 真实网关用例的模型名改 wire 优先取，新增「不允许两者皆空」断言` | 测试（`androidTest/`，`C1LiveModelSequenceTest.kt` +149 / −34） |
| `63d0a504` | `test(app): 证据 JSON 记两个模型名并逐条标 provenance，硬理由①的采集口径对得上落盘字段` | 测试（同上，+96 / −11） |
| `74d82476` | `docs(app): 修掉 KDoc 里指向不存在 JSON 字面量的引用，改为实际两个字段名` | ⚠️ **纯 KDoc**（同上，+2 / −1，**零实现改动**） |

⚠️⚠️ **这 8 个（`f1bf516e`..`74d82476`）的性质必须照实说**：它们是**第一批真正动到生产
代码（`ai/`）去修硬理由①的提交**，不是纯测试、也不是纯文档。**但它们同样一条验收证据
都没产出**——契约 `:206` 点名的四类产物**一份未增**，**零设备、零 `adb`**。
⚠️ 逐条交代它们各自**没有**证明什么：
- `f1bf516e` / `0c239788`：**只证明了编译过**。`ai/src/main` 的两处改动**没上真机**；
  `StreamChunkHandler` 的 `Finish` 分支在真实 SSE 收尾帧上的行为**只有 JVM fixture 级证据**。
- `ae285843`：**9 条 `@Test` 全过**（退出码 0，含非空验证：删掉那一行 ⇒ 3 failed），
  但⚠️ **它只覆盖 OpenAI chat-completions 一条路径**，且是 **fixture 级**——
  **不覆盖 Claude / Google 那两条 provider 路径**，也**不覆盖设备上的真实收尾帧**。
- `35b90d67`：**真实公网网关探测实测成功**（两个 wire 名、均 HTTP 200），
  但⚠️ **它验的是网关，不是 app**——脚本直接打网关、**完全不经过 `ChatManager` /
  `StreamChunkHandler`**，所以**它证明的是「网关会自报这个名字」，不是「app 落得下来」**。
- `d7971ba1` / `63d0a504`：**改的是仪器测试**，而⚠️ **仪器测试一行都没跑过**（设备离线），
  所以「wire 优先判定逻辑」「`wire_model_name_provenance` 逐条取值」
  「不允许两者皆空」这些**全部只有编译验证**。

⚠️⚠️ **本批对台账声明值的影响：零**。`ae285843` 新增的类在 **`ai/src/test/`**，
而本文台账的口径是「**能在 `app/build/test-results/testDebugUnitTest/*.xml` 对上**」
（见台账小节开头），所以它**不并入台账**；`d7971ba1` / `63d0a504` 改的是
**仪器测试**，台账本来就不收仪器测试。⚠️ 因此 **`45 类 / 491 例` 一个数都没动**
（`c1_doc_stats.py` 本轮实测 `ledger` 仍是 `45 行全部相等` / 声明合计 **491**）。
⚠️ **本批唯一影响 app 单测总数的是 `5186349f` 那 4 条**（`GroupChatTest` `29 → 33`），
它与 wire 模型名无关，是上一批围栏修复带来的。

⚠️ **本批同样不改变任何判定**：**零设备、零 `adb`**，**十例状态列一个格都没动，
仍是 10/10 `unverified`**（本轮审计方式同上一轮：Python 按**未转义 `|`** 逐行切单元格，
**两张十例矩阵共 20 个数据行逐行判定，首判定全部是 `unverified`，非 unverified 的 0 行**）。
⚠️ **硬理由①本身也仍未消**——成因已修、能力已具备，但**真机没跑**，
详见「为什么十例仍然 0/10」第 2 条与「已知遗留与风险」第 18b 条。


#### ⚠️⚠️ 再往后一批（第七批，HEAD `86e88970d` 那一轮采集）：**4 条 mock 仪器测试真机全绿**

⚠️⚠️ **本小节记的是「采集」不是「代码提交」**：这一轮**一行代码都没改**（`git diff`
只有 `docs/`），做的是在**真机上把 `C1LiveModelSequenceTest` 前 4 条各跑一遍**并把
产物拉回主机。核验命令与实测输出：

```
git log --oneline ea6b7c4c..86e88970d | wc -l   # 13，就是下面这 13 个
git log --oneline d45ebd10~1..1b0e04a9 | wc -l  # 78，锚点不重算
git log --oneline --merges d45ebd10~1..1b0e04a9 | wc -l # 0
```

⚠️ **台账声明值 `45 类 / 491 例` 一个数都没动**，理由与上一批**逐字相同**：
`ae285843` 新增的类在 **`ai/src/test/`**，而本文台账的口径是「**能在
`app/build/test-results/testDebugUnitTest/*.xml` 对上**」；
`d7971ba1` / `63d0a504` / `74d82476` 改的是**仪器测试 / KDoc**，
**台账本来就不收仪器测试**。

| SHA | 标题 | 性质 |
|---|---|---|
| `f1bf516e` | `feat(ai): UIMessage 增加 wireModelName 字段` | 功能（`ai/`）——硬理由①修复第一块 |
| `0c239788` | `fix(ai): Finish 落盘 wire 模型名，补上非流式对称路径` | 功能（`ai/`）——第二块 |
| `ae285843` | `test(ai): 新增 WireModelNameProvenanceTest，钉死 wire 级模型名` | 测试（`ai/src/test/`，**不并入台账**） |
| `35b90d67` | `feat(tools): 新增 c1_wire_model_probe.py，抓网关自报的 wire 模型名` | 工具（**零测试用例**） |
| `ab2f9b65` | `docs(app): 修正 ChatList 注释里 StreamChunkHandler 的行号锚点` | ⚠️ 改的是 Kotlin **注释**，零实现改动 |
| `d7971ba1` | `test(app): 真实网关用例的模型名改 wire 优先取，新增「不允许两者皆空」断言` | 测试（`androidTest/`，**台账不收**） |
| `63d0a504` | `test(app): 证据 JSON 记两个模型名并逐条标 provenance，硬理由①的采集口径对得上落盘字段` | 测试（同上） |
| `74d82476` | `docs(app): 修掉 KDoc 里指向不存在 JSON 字面量的引用，改为实际两个字段名` | 纯 KDoc |
| `f41807c3e` | `docs(c1): 订正两处过期数字——C1LiveModelSequenceTest 现为 2221 行、app 单测 HEAD 实测 893 例` | 文档 |
| `53d376f9a` | `docs(c1): 硬理由①如实改写——缺陷已定位并修复、wire 名可直接拿到，但真机未跑故仍不划掉` | 文档 |
| `f2d12e930` | `docs(c1): 台账补登 wire 级模型名那 8 个 commit（锚点 1b0e04a9 不重算，声明值 45 类/491 例未动）` | 文档 |
| `ff8cadf44` | `docs(c1): 新增 wire 级模型名的两条零设备证据节（JVM 9 例 + 真实网关探测），逐条标注取证局限` | 文档 |
| `86e88970d` | `docs(status): wire 级模型名从「缺能力」改为「已具备 + 待真机复核」，补第十一批与下一位行动项` | 文档 |

⚠️ **上面这 13 个一个都不计入本台账任何计数**——不改统计区间、不改 `--no-merges`
口径、不引入新的类型前缀或子包标签。**`78 / 6 / 17` 与声明值 `45 类 / 491 例`
一个数都没动**（`c1_doc_stats.py` 本轮实测 `ledger` 仍 `45 行全部相等` / 合计 **491**，
`git` 锚点区间仍 **78** / `--merges` **0**）。

⚠️⚠️ **这一批与上一批的性质完全不同，必须说清**：
上一批（`f1bf516e..74d82476`）是「**能力已具备 + 零设备**」，
这一批是「**能力在真机上被采到了**」——
t1/t3 两份 pulled JSON 里 `wire_model_name` 是 `mock-model-a/b/c` 这种**服务端自报串**
（同行 `model_id` 是本地 UUID）⇒ **wire 优先逻辑在真机上生效**，
这正是上一批缺的那份真机数字。
⚠️ **但硬理由①仍不消**：这一批跑的是 **mock provider**，
**真实网关那次 `realProviderRoundRecordsGenuineTokenUsage` 还没在 wire 优先逻辑下重跑**，
设备上那份 `c1-live-evidence-real-provider.json` **仍是反查值**。
⚠️ **硬理由⑤已消**（那 4 条真机全绿，四条退出码 `0`、全 `OK (1 test)`）。
⚠️ **十行状态列仍然是 10/10 `unverified`**——本轮**零 UI 动作、零酒馆动作、零扫码动作**，
UI 端到端 / 酒馆本体 / 相机扫码**仍然零份**。
逐条边界与全部数字见「C1 真机证据采集第七轮」。

#### ⚠️⚠️ 再往后一批（第八批，HEAD `19545e076`）：`GroupChat` 行切分统一到 6 个 Unicode 行终止符

⚠️ **锚点 `1b0e04a9` 仍然没有被重算**：本批每一个 commit **一个都不计入**本台账，
理由与前几批逐字相同——不改统计区间、不改 `--no-merges` 口径、不引入新的类型前缀或
子包标签。所以 **`78 / 6 / 17`、前缀分布、子包分布一个数都没动**（脚本本轮实测仍是
`78` / `--merges` = `0`）。

⚠️⚠️ **但这一批与前七批性质不同：它是「锚点之后第一次真的动了 `app/src/main` 的生产代码
去修一个静默丢票缺陷」**（前七批动过 `ai/` 的生产代码，但那是硬理由①的取得途径；
`GroupChat.kt` 这条线从 `5186349f` 起就一直在补护栏）。⚠️ **它同样一条验收证据都没产出**。

| SHA | 标题 | 性质 |
|---|---|---|
| `3e0051f92` | `coder: c1-group-chat 第28条订正「删 ChatService 是纯清理」的前提失效` | 文档 |
| `be7952a83` | `coder: GroupChat 切分统一到 6 个 Unicode 行终止符，修 parseCandidates/parseBallot 静默丢票` | **功能 + 测试准备**（`GroupChat.kt` **+77 / −4**：新增**私有** helper `unicodeLines`，`parseCandidates` 与 `linesOutsideCodeFences` **共用**）——⚠️ **零验收证据** |
| `19545e076` | `coder: GroupChatTest 补 Unicode 行终止符用例，钉住 2 条改值 + 4 条新增护栏` | 测试（`GroupChatTest.kt` **+229 / −7**，**净增 6 条 `@Test`**：`33 → 39`）——⚠️ **零设备、零 `androidTest`** |
| `6e9e83642` | `docs(c1): 台账 GroupChatTest 33→39、合计 491→497（Unicode 行终止符那批，追加不覆写）` | 文档（纯文档） |
| `bed2a5ea6` | `docs(c1): 登记 app 单测新口径 111 类/921 例（915→+6 全来自 GroupChatTest，XML 晚于提交 2 分钟）` | 文档（纯文档） |
| `f02522749` | `docs(c1): 新增 Unicode 行终止符统一切分的证据节（6 个分隔符 / 有意收紧 / 尾随 NEL 真洞 / 取证局限）` | 文档（纯文档） |

⚠️⚠️ **本批对台账声明值的影响：变了，这是本批唯一动了计数的地方**——
`GroupChatTest` 是**在册类**，它的声明例数 `33 → 39`，所以**台账合计 `491 → 497`**
（**类数仍是 45**：这两个 commit **没有新增测试类**，只扩写既有的 `GroupChatTest.kt`）。
✅ 复算实测：`--only ledger` 输出 `台账行数 45 行` / `声明合计 497 例` /
`逐行核对 45 行全部相等`。
⚠️ **别把 `497` 与 app 全模块的 `921` 混用**：`497` 是本文台账 **45 个在册类**的声明例数
之和，`921` 是 **app 全部 111 个类**的单测总数。两个口径**同源但不同义**——
本批 `+6` 同时进了两列（`491 → 497` 与 `915 → 921`），详见「统计口径复算脚本」小节里
新登记的那张口径表。

⚠️⚠️ **本批同样不改变任何判定**：**零设备、零 `adb`、零 `androidTest`**，
**十行状态列一个格都没动，仍是 10/10 `unverified`**（本轮审计方式同前几批：Python 按
**未转义 `|`** 逐行切单元格，两张十例矩阵共 20 个数据行逐行判定，首判定全部是
`unverified`，非 `unverified` 的 **0 行**）。
⚠️ **硬理由①未被本批触及**（wire 名那条线本批一行没碰），**②③④**（真机 UI 端到端 /
酒馆本体 / 相机扫码）**全部原样**。
⚠️⚠️ **并且本批还额外踩中「判定规则」第九条那个坑**：真实模型**是否会**输出
`U+2028` / `U+0085` 这几个字符**本次零实证**，所以这是「消除了一个失败模式」，
**不是**「观测到并修掉了一个高频故障」。全部边界见「Unicode 行终止符统一切分」那一节。

#### ⚠️⚠️ 再往后一批（第九批，2026-10-06，到本轮注入点 `08702d88b` 为止）：真实 token 预算截断真机采证 + `GroupViewerExhaustiveMatrixTest`

⚠️⚠️ **本小节覆盖 `06a62c676..08702d88b` 这 7 个 commit**（`git log --oneline 06a62c676^..08702d88b | wc -l` 实测 **7**；含第八批批次的登记 commit 本体）。⚠️ **锚点 `1b0e04a9` 仍然没有被重算**：7 个一个都不计入本台账任何计数——不改统计区间、不改 `--no-merges` 口径、不引入新的类型前缀或子包标签 ⇒ **`78 / 6 / 17` 与两张分布表一个数都没动**。

| SHA | 标题 | 性质 |
|---|---|---|
| `06a62c676` | `docs(c1): 台账补登第八批两个 commit（be7952a83/19545e076，锚点 1b0e04a9 不重算，声明值 491→497）` | 文档（=第八批批次的登记 commit 本体） |
| `9ad49a213` | `docs(c1): 判定规则补第九条——「消除失败模式」不等于「观测到故障」，零设备+零模型输出实证` | 文档（纯文档） |
| `587b5d023` | `docs(status): 补第十三批摘要 + 订正块（U+2028/9 规则已定 6 个分隔符；trim 字符集与 fence 假阳性两条只钉现状记述已修）` | 文档（status） |
| `14ff966e4` | `docs(status): C1 群聊行补第十三批摘要与一项未验证义务（纯追加，原文未改）` | 文档（status） |
| `ddda36186` | `test(c1): 新增 GroupViewerExhaustiveMatrixTest——三角色×三模式×全排列×两轮×全部 viewer 的可见性穷举矩阵（216 个 case，精确有序 id 列表断言 + 3 条通用不变量）` | 测试（`app/src/androidTest/`，**台账不收仪器测试**） |
| `fcd700229` | `docs(c1): 台账登记 GroupViewerExhaustiveMatrixTest 9 例——46 类 / 506 例，并追加 112 类/930 例与 lint 590 的实测记录` | 文档（台账类数 45→46；⚠️ 506 是**当时**的合计，现值见下） |
| `08702d88b` | `test(c1): 真实网关轮预算加 -e c1TokenBudgetPerRound 注入点（只作用于真机轮，mock 三模式与 budgetLimit=1 基线一字未动）` | 测试（`app/src/androidTest/`，**本轮真机跑用的注入点**）——⚠️ **它本身零验收证据**，证据见「真实 token 下的预算截断（零 mock，真机）」节 |

⚠️ **本批对台账声明值的影响（已实测复算）**：`GroupViewerExhaustiveMatrixTest` 是在册类，新增 **1 类 9 例** ⇒ 台账由 `45 类 / 497 例` 变为 **`46 类 / 506 例`**（`fcd700229` 当时实测）；随后 **D1 清理批**（`3521ccfae`，**不属于本批**）按删除后的源码把两个在册类的声明例数订正——`ChatServiceSenderNameGuardTest` **3 → 1**、`ChatServiceGroupChatFailLoudGuardTest` **4 → 1**（合计 `−5`）⇒ 现值为 **`46 类 / 501 例`**（`python3 tools/verification/c1_doc_stats.py` 本轮实测：`台账行数 46 行` / `声明合计 501 例` / `逐行核对 46 行全部相等`）。⚠️ **不要把 506 当现值，也不要把这次 D1 订正算进本批。**

⚠️⚠️ **本批同样不改变任何判定**：除 `08702d88b` 的注入点被真机跑用上（新证据节，`Tests run: 1, Failures: 1`）外，其余 6 个 commit 全是文档 / 仪器测试源；**十行状态列一个格都没动，仍是 10/10 `unverified`**——本轮真机采到的只是库内七值，契约 `:206` 的「实际模型调用序列」正式 JSON 与 viewer 台账**仍零份**。

#### ⚠️⚠️ 再往后一批（第十批，2026-10-06，到 HEAD `31b6b5a52` 为止）：**真实网关正式证据首次产出 + finally 落盘验证 + mock/全量/UI 采集**

⚠️⚠️ **本小节覆盖 `08702d88b..HEAD` 这 22 个 commit**（`git log --oneline 08702d88b..HEAD | wc -l` 实测 **22**、`--merges` = **0**）。⚠️ **锚点 `1b0e04a9` 仍然没有被重算**：22 个一个都不计入本台账任何计数——不改统计区间、不改 `--no-merges` 口径、不引入新的类型前缀或子包标签 ⇒ **`78 / 6 / 17` 与两张分布表一个数都没动**。

| SHA | 标题 | 性质 |
|---|---|---|
| `2f94b72d8` | `coder: 把 forkConversationTitle 搬到 feature/chat/ChatManager.kt 并去掉跨包 import` | 重构 |
| `41e29ab20` | `coder: HistoryVM 注入改指 ChatManager，删 AppHiltModule 未使用的 ChatService import` | 重构 |
| `86da59fa0` | `coder: 删除 core/service/ 5 个被 vendor merge 复活的死副本及空目录` | 清理（D1） |
| `0040463b1` | `coder: 清理聊天建议偏好 flag enableSuggestion 与 7 个 locale 的字符串资源` | 清理（D1） |
| `f957af14f` | `coder: 删掉两个护栏测试里依赖已删 ChatService.kt 的方法` | 测试清理（D1；台账两行 −5 例已在下一行订正） |
| `3521ccfae` | `coder: 文档登记 D1 拍板结果与 core/service 清理，台账例数 506 -> 501` | 文档（**台账订正**） |
| `0ab2b8ea8` | `coder: 移除已废弃聊天建议的 suggestionPrompt/SUGGESTION_PROMPT 4 处孤儿` | 清理 |
| `b432a69a4` | `coder: 删除聊天建议的 3 个孤儿字符串资源(7 语言共 21 行)` | 清理 |
| `f1fdcc6f3` | `docs: 修正 AGENTS.md 包结构与命名基线(5 子包/535kt, Service 30+Util 9+Utils 11+Store 11=61)并写明复算口径` | 文档 |
| `f507ebc9a` | `docs: 点明 material3/material-color-utilities 是 submodule，即 11/13/14 三个数的根因` | 文档 |
| `54ef5752f` | `coder: 登记真实 token 预算截断真机证据（七值+SHA-256+四条限制），C1-05 追加订正块、状态列一字未动` | 文档（c1） |
| `ffe0cebd8` | `coder: 实施状态补第十四批——真实 token 预算截断真机七值与注入点 08702d88b（失败跑，状态不动）` | 文档（status） |
| `5972207dd` | `coder: 命名合规 A 类——AILiveNotificationService 改名 AILiveNotificationManager（有状态通知生命周期）` | 改名 |
| `27b14335f` | `coder: 命名合规 A 类——BrowserHistoryStore 改名 BrowserHistoryRepository（数据读写）` | 改名 |
| `563c716d2` | `coder: 命名合规 A 类——CardScheduleStore 改名 CardScheduleRepository（定时任务持久化）` | 改名 |
| `6d2aad0e8` | `coder: 命名合规 A 类——WorkflowStore 改名 WorkflowPreferencesRepository（避开既有 WorkflowRepository 撞名）` | 改名 |
| `e3cbf926b` | `coder: 命名合规 A 类——khatkit bridge 三个 Store 改名 Repository（SQLite/密钥/共享文件均属数据层）` | 改名 |
| `367a5ebf1` | `coder: 命名合规 A 类——DependencyArtifactStore 改名 DependencyArtifactHelper（无状态打杂有副作用）` | 改名 |
| `e8a92453d` | `docs(agents): 命名 A 类改名完成登记——基线更新为 Service 29/Util 9/Utils 11/Store 4=53，B/C/D 理由与新复现口径` | 文档 |
| `a1d0a307b` | `docs(architecture-map): §2 命名合规——A 类 8 个改名登记 + B/C/D 逐文件判定表；基线更新为 非合规53/合规率41.1%` | 文档 |
| `43d607bdd` | `coder: 修复 chat-completions 非 2xx 空 body 被静默当零产出成功` | **功能（静默失败 → fail-loud；非重试、非本轮 c 成功的原因）** |
| `31b6b5a52` | `coder: C1真机用例 raw dump 改为 finally 无条件落盘，预算截断也留现场` | 测试（`androidTest`，**本轮采集的落点**） |

⚠️ **本批对台账声明值的影响：一个数都没动**——`f957af14f` 减掉的 D1 两行例数已由 `3521ccfae` 在批内订正为 **46 类 / 501 例**（第九批已记录该订正），本批**没有任何 `app/src/test` 在册类的新增/扩写** ⇒ 声明值仍 **46 行 / 501 例**（`python3 tools/verification/c1_doc_stats.py` 实测 `台账行数 46 行` / `声明合计 501 例` / `逐行核对 46 行全部相等`）。⚠️ **别把 `f957af14f` 的 `−5` 再算一遍。**

⚠️⚠️ **本批同样不改变任何判定**：除 `31b6b5a52` 让预算截断的 raw dump 首次真正落盘、`43d607bdd` 让静默失败变响亮之外，其余 20 个 commit 是重构/清理/改名/文档；**十行状态列一个格都没改判定，仍是 10/10 `unverified`**——⚠️ **① 句的订正已逐格追加**（真实网关已 wire 级重跑并首次产出正式证据），**但没有任何一行升级**（逐行依据见「C1 真机证据采集第八轮」⑨）。新证据节与全部数字/SHA 见该节。

#### ⚠️⚠️ 再往后一批（第十一批，2026-10-06，`31b6b5a52..4d73a26b4`）：3 个 commit，全部是文档

⚠️⚠️ **本小节覆盖 `31b6b5a52..4d73a26b4` 这 3 个 commit**（`git log --oneline 31b6b5a52..4d73a26b4 | wc -l` 实测 **3**、`--merges` = **0**；逐条 `git show --stat` 核实全部只改 `docs/`）。⚠️ **锚点 `1b0e04a9` 仍然没有被重算**：3 个一个都不计入本台账任何计数——不改统计区间、不改 `--no-merges` 口径、不引入新的类型前缀或子包标签 ⇒ **`78 / 6 / 17` 与两张分布表一个数都没动**。

| SHA | 标题 | 性质 |
|---|---|---|
| `0364cba12` | `coder: 登记C1真机第八轮证据（真实网关正式证据首次产出+预算finally落盘验证+mock4/4+全量64/1+UI部分覆盖），20状态格①句订正、台账第十批22个commit` | 文档（=第十批批次的登记 commit 本体，按惯例在本批补登） |
| `76217977f` | `coder: 实施状态补第十五批——真实网关正式证据首次产出（硬理由①真实网关路径已消）、预算落盘验证、mock/全量/UI 与设备卫生遗留` | 文档（status） |
| `4d73a26b4` | `coder: 订正第八轮证据节一处表述——本机 pull 副本 mtime 是 pull 时刻，不能当设备 mtime 引用` | 文档（订正） |

⚠️ **本批（第十一批）的登记 commit 本体 = 第九轮证据节 + 20 状态格订正 + 本节 + 遗留 36/37 + 实施状态第十六批**（分几个 commit 提交，SHA 提交后才知道）——**按惯例留到下一批补登，本表不编造**。
⚠️ **本批对台账声明值的影响：一个数都没动**——三个 commit 全是文档，本批没有任何 `app/src/test` 在册类的新增/扩写 ⇒ 声明值仍 **46 行 / 501 例**（`python3 tools/verification/c1_doc_stats.py` 实测 `台账行数 46 行` / `声明合计 501 例` / `逐行核对 46 行全部相等`）。
⚠️ **本批同样不改变任何判定**：新证据（筛选 chip 真机 UI 通过 / 设置污染 pb 手术恢复 / 酒馆解析器级 16/16 / 新缺陷「删最后助手必崩」）**20 个状态格一个升级都没有**——逐行依据见「C1 真机证据采集第九轮」⑧；② 句第九轮订正已逐格追加、C1-09 / C1-10 两行另加第九轮注记。⚠️ **别把「酒馆解析器级接受」读成「硬理由③已消」**——完整 app 打开仍零份。

#### ⚠️⚠️ 再往后一批（第十二批，2026-10-06，`4d73a26b4..d133b400b`）：**页面入口合并 + UI 三条 androidTest 纳入 + 第十一批登记本体补登**

⚠️⚠️ **本小节覆盖 `4d73a26b4..d133b400b` 这 10 个 commit**（`git log --oneline 4d73a26b4..d133b400b | wc -l` 实测 **10**、`--merges` = **0**）。它 = **上一批（第十一批）按惯例留到本批补登的登记本体 5 个** + **本批更早的两个 testTag commit 2 个** + **本批主角 3 个**。⚠️ **锚点 `1b0e04a9` 仍然没有被重算**：10 个一个都不计入本台账任何计数——不改统计区间、不改 `--no-merges` 口径、不引入新的类型前缀或子包标签 ⇒ **`78 / 6 / 17` 与两张分布表一个数都没动**。

| SHA | 标题 | 性质 |
|---|---|---|
| `3e5596f9b` | `coder: 登记C1真机第九轮证据节——筛选chip两半真机UI通过+设置污染pb手术恢复+酒馆解析器级16/16+新缺陷删最后助手必崩` | 文档（=第十一批登记本体，按惯例本批补登） |
| `69e02f949` | `coder: 20状态格②句就地订正（chip已有真机UI证据）+C1-09/C1-10状态格追加第九轮注记，判定一律未改` | 文档（=第十一批登记本体） |
| `b91737bff` | `coder: 第九轮遗留补第36条（删最后助手必崩）与第37条（RUNNING残留未清）、台账追加第十一批3个文档commit` | 文档（=第十一批登记本体） |
| `f34f007f4` | `coder: 实施状态补第十六批——筛选chip真机通过、设置污染pb手术恢复、酒馆解析器级16/16、新缺陷删最后助手必崩、十例仍全unverified` | 文档（status，=第十一批登记本体） |
| `a6a46face` | `coder: 订正第九轮证据节一处归属表述——SettingsRepository 真实子包是 datastore，去掉对上一轮转述的错误指认` | 文档（=第十一批登记本体） |
| `15ce2f251` | `coder: GroupMemberBar 容器补 testTag group_member_bar（C1 真机 UI 测试定位锚点，仅加语义不改行为）` | 功能（main 源码，仅加语义；`GroupMemberBar.kt +5`） |
| `467f67ad9` | `coder: 抽屉会话列表补 testTag drawer_conversation_list（C1 chip 过滤真机测试需滚动定位，仅加语义不改行为）` | 功能（main 源码，仅加语义；`ChatDrawer.kt +7/−1`） |
| `6b4a56d9f` | `coder: 群聊页复用前置重构——提升 GroupTopBar/GroupInfoChip/GroupConfigSheet 可见性，抽出 groupConfigSave 与 groupCanEditMessage（零行为变化）` | 功能（main 源码，`GroupChatPage.kt +57/−22`） |
| `a4b8be9da` | `coder: 群聊与单聊合并为同一 ChatPage——按 isGroupConversation 条件注入群聊 6 样专属件，删除 GroupOrDirectPage/GroupChatPage；RouteActivity 直接进入 ChatPage` | 功能（main 源码，3 files `+99/−340`） |
| `d133b400b` | `coder: 纳入 C1 群聊 androidTest 三条真断言（筛选chip/成员头像组/@选择器）——编译通过，头像组用例真机通过，chip与@因设备掉线未跑` | 测试（`androidTest`，`+569`；**台账不收仪器测试**） |

⚠️ **本批对台账声明值的影响：一个数都没动**——10 个 commit 里没有任何 `app/src/test` 在册类的新增/扩写（`d133b400b` 加的是 `androidTest`，**不进台账口径**；其余是 main 源码 / 文档）⇒ 声明值仍 **46 行 / 501 例**（`python3 tools/verification/c1_doc_stats.py` 实测 `台账行数 46 行` / `声明合计 501 例` / `逐行核对 46 行全部相等`）。⚠️ **别把 `d133b400b` 的 3 条仪器用例算进 501。**
⚠️ **本批同样不改变任何判定**：页面合并是**入口重构 + 零行为变化的重构**，UI 三条 androidTest 只有 1 条真机通过、2 条未跑完，**20 个状态格一个升级都没有**（C1-10 两处仅就地追加第十轮订正）——逐行依据见「C1 真机证据采集第十轮」⑨。

#### ⚠️⚠️ 再往后一批（第十三批，2026-10-06，`d133b400b..81147639a`）：**两处静默 `close(null)` 缺陷修复 + 助手删空崩溃修复 + 第十二批登记本体补登**

⚠️⚠️ **本小节覆盖 `d133b400b..81147639a` 这 10 个 commit**（`git log --oneline d133b400b..81147639a | wc -l` 实测 **10**、`--merges` = **0**；逐条 `git show --stat` 核实）。它 = **上一批（第十二批）按惯例留到本批补登的登记本体 6 个** + **本批主角 4 个**（`e628d9d82` / `8b05fde70` / `85f708169` / `81147639a`）。⚠️ **锚点 `1b0e04a9` 仍然没有被重算**：10 个一个都不计入本台账任何计数——不改统计区间、不改 `--no-merges` 口径、不引入新的类型前缀或子包标签 ⇒ **`78 / 6 / 17` 与两张分布表一个数都没动**。

| SHA | 标题 | 性质 |
|---|---|---|
| `16dbddeae` | `coder: 登记C1真机第十轮证据节——群聊单聊合并为ChatPage唯一入口、成员头像组首条androidTest断言真机通过、chip/@未跑完、kill -3操作失误，判定未升级` | 文档（=第十二批登记本体，按惯例本批补登） |
| `165e86b29` | `coder: 补回被上一提交误删的「## 仪器测试状态」标题（标题在替换旧文本时被一并吃掉）` | 文档（=第十二批登记本体） |
| `3041ef27a` | `coder: C1-10 两处状态格就地追加第十轮订正（头像组已有androidTest断言但chip/@未跑、非空未验，判定仍不升级）` | 文档（=第十二批登记本体） |
| `64980db61` | `coder: 台账追加第十二批10个commit（页面合并3个+testTag2个+第十一批登记本体5个，锚点1b0e04a9不重算，声明值46行501例未动）` | 文档（=第十二批登记本体） |
| `7b1d03991` | `coder: 遗留补第38条——设备screen_off_timeout未恢复600000、stayon未恢复、kill -3招致ColorOS杀 instrumentation 的操作教训` | 文档（=第十二批登记本体） |
| `7782607d5` | `coder: 实施状态补第十七批——页面入口合并为ChatPage唯一入口、UI三条androidTest纳入（头像组真机通过/chip和@未跑）、kill -3失误、设备设置未恢复；行动顺序第2.6条追加订正` | 文档（status，=第十二批登记本体） |
| `e628d9d82` | `coder: 修复 responses API 非 2xx 空/乱码 body 被静默当零产出` | **功能（静默失败 → fail-loud；`ai/` 生产代码 + 1 新测试类）** |
| `8b05fde70` | `coder: 修复 Claude 非 2xx 空/乱码 body 被静默当零产出` | **功能（同上，Claude provider）** |
| `85f708169` | `coder: 删除最后一个助手时回落默认助手，避免空列表令 getCurrentAssistant 崩溃` | **功能（`app/` 生产代码 2 文件，+29/−9）** |
| `81147639a` | `coder: 补充助手删除空列表不变量单测（含删除最后一个助手回归护栏）` | 测试（`app/src/test`，+1 类 6 例） |

⚠️ **本批对台账声明值的影响：一个数都没动**——本批没有任何**已在册**的 `app/src/test` 类被新增/扩写；`81147639a` 新增的 `AssistantRemovalInvariantTest` 在 `app/src/test/.../datastore/` 目录、**不在台账那 46 个 C1 在册类名单里**，`e628d9d82` / `8b05fde70` 的新测试类在 `ai/src/test`（**台账口径根本不含 `ai` 模块**）⇒ 声明值仍 **46 行 / 501 例**（`python3 tools/verification/c1_doc_stats.py` 本机实测：`台账行数 46 行` / `声明合计 501 例` / `逐行核对 46 行全部相等`）。

⚠️ **本批同样不改变任何判定**：两处 AI 修复走的是 **responses API / Claude** 路径（C1 走 chat-completions），助手删除与 C1 完全无关，**20 个状态格一个升级都没有，仍是 10/10 `unverified`**——逐行依据见「生成失败健壮性与助手空列表修复」节。

#### ⚠️⚠️ 再往后一批（第十四批，2026-10-06，`81147639a..0e312cbb2`）：**本次全项目门禁零新 commit + 第十三批登记本体 3 个补登**

⚠️⚠️ **先说本批与门禁的关系**：本次补跑的**全项目总闸门**（`./gradlew --offline assembleDebug test lint`，详见上方「全项目总闸门」节）**没有产生任何 commit**——`git log --oneline 0e312cbb2..HEAD` 实测**为空**，HEAD **仍是 `0e312cbb2`**。所以本批**没有新的功能 / 测试 commit 可登**；本小节登记的是**上一批（第十三批）按本文件既有惯例留到本批补登的登记本体 3 个**（`dc7720f90` / `97c9979ca` / `0e312cbb2`，逐条 `git show --stat` 核实全部只改 `docs/`）。⚠️ **锚点 `1b0e04a9` 仍然没有被重算**：这 3 个一个都不计入本台账任何计数——不改统计区间、不改 `--no-merges` 口径、不引入新的类型前缀或子包标签 ⇒ **`78 / 6 / 17` 与两张分布表一个数都没动**。

核验命令：

```
git log --oneline 81147639a..0e312cbb2 | wc -l   # 3（本批补登的第十三批登记本体）
git log --oneline 0e312cbb2..HEAD | wc -l        # 0（本次门禁零 commit，HEAD 未变）
```

| SHA | 标题 | 性质 |
|---|---|---|
| `dc7720f90` | `coder: 修正 lua-card-development.md 过期类名 SecretStore → SecretRepository` | 文档（=第十三批登记本体，按惯例本批补登） |
| `97c9979ca` | `coder: 登记生成失败健壮性与助手空列表修复证据节，台账第十三批，遗留第36条订正` | 文档（=第十三批登记本体） |
| `0e312cbb2` | `coder: 实施状态补第十八批——两处静默close(null)修复与助手删空崩溃修复，均非C1验收项，20状态格未动` | 文档（status，=第十三批登记本体） |

⚠️ **本批对台账声明值的影响：一个数都没动**——本批 3 个全是文档登记本体，且本次门禁零 commit、无任何 `app/src/test` 在册类的新增/扩写 ⇒ 声明值仍 **46 行 / 501 例**（`python3 tools/verification/c1_doc_stats.py` 本机实测：`台账行数 46 行` / `声明合计 501 例` / `逐行核对 46 行全部相等`）。

⚠️ **本批同样不改变任何判定**：本次全项目门禁（`assembleDebug test lint`）**exit 0 全绿**只是**包级 / 项目级门禁证据，不是用例级证据**——20 个状态格一个升级都没有，仍是 **10/10 `unverified`**。⚠️ **别把「门禁全绿」读成「十例已验收」**：其阻塞项见上方「全项目总闸门」节 ④ 与本文件既有清册。

#### ⚠️⚠️ 再往后一批（第十五批，2026-10-06，`0e312cbb2..cddaa9959`）：**真机第十一轮（4 条真实网关 + pipeline 复跑 + 2 条 UI）+ 第十二轮（C1-09 真机导出 / C1-06 三次不成立），共 8 个 commit；声明值 46 行 / 501 例未动**

⚠️ **核验命令（登记代理本机实测，原文）**：

```
git log --oneline 0e312cbb2..HEAD            # 8 行（b20de6805 / 46ba5c8a7 / 129403b38 / a8302e463 / 92af89247 / d6870f977 / 579dc193a / cddaa9959）
git log --oneline 129403b38..cddaa9959 | wc -l   # 5
git log --name-only 0e312cbb2..cddaa9959 -- 'app/src/test/*'   # 空输出（本批无 app/src/test 在册类变化）
```

| SHA | 标题 | 性质 |
|---|---|---|
| `b20de6805` | `coder: 登记全项目总闸门证据节（首次整项目 assembleDebug test lint exit 0、--rerun 作用域陷阱），20 状态格一字未动` | 文档（=第十四批登记本体，本批补登） |
| `46ba5c8a7` | `coder: 台账追加第十四批——本次全项目门禁零新 commit（HEAD 仍 0e312cbb2）+ 第十三批登记本体 3 个补登，锚点不重算、声明值未动` | 文档（=第十四批登记本体，本批补登） |
| `129403b38` | `coder: 实施状态补第十九批——全项目门禁首跑通过、--rerun 作用域陷阱、基线数字、十例仍 0/10、设备侧仍待` | 文档（status，=第十四批登记本体，本批补登） |
| `a8302e463` | `coder: C1 真实网关新增 roundtable/显式@/vote 三条变体（含平票失败路径）` | 测试（`C1LiveModelSequenceTest.kt` **+1454/−190**，生产代码零改动） |
| `92af89247` | `coder: C1-09 新增真机导出证据测试（生产 IO 落盘 + 四项泄露检查 + 设备侧 SHA-256）` | 测试（新增 `C1GroupExportDeviceEvidenceTest.kt` 523 行） |
| `d6870f977` | `coder: C1-06 新增真机取消证据测试 + 慢速/注入500的 mock 服务脚本` | 测试 + 工具（`C1GroupCancelDeviceTest.kt` 616 行 + `tools/verification/mock_openai_slow_cancel.py` 284 行） |
| `579dc193a` | `coder: 修 C1-09 真机导出测试 tearDown 表达式体非 void 导致 JUnit 初始化拒绝` | 测试（修 `92af89247` 的测试自身缺陷） |
| `cddaa9959` | `coder: C1-06 取消测试加原始转储；mock 改零内容注入（生产 role_failed 路径，避免 500 解析歧义）` | 测试 + 工具（`C1GroupCancelDeviceTest.kt` +109/−8、mock +50） |

⚠️ **本批对台账声明值的影响：一个数都没动**——本批没有任何**已在册**的 `app/src/test` 类被新增/扩写；三个测试 commit 新增的类全在 `app/src/androidTest`，两个脚本在 `tools/verification/` ⇒ 声明值仍 **46 行 / 501 例**（本机实测 `c1_doc_stats.py`：`台账行数 46 行` / `声明合计 501 例` / `逐行核对 46 行全部相等`）。

⚠️ **本批对判定的影响：20 个状态格一个升级都没有，仍是 10/10 `unverified`**——第十一轮把 C1-02 的「序列对照 / viewer 断言」两项补成断言级、把 C1-01 的投递收窄与 C1-04 的两条路径真机坐实，但契约 `:206` 五要素仍缺「导出哈希」一类、`am instrument` 退出码未落盘（`:232-235` 要求「测试命令及退出码」）、且全部通过项**单次通过未做重复稳定性验证**；第十二轮 C1-09 拿到真机导出双哈希 + 四项安全检查，但酒馆本体 / `ACTION_SEND` / 相机扫码 / 导入往返仍零份，C1-06 三次尝试全部不成立。逐行依据见「C1 真机证据采集第十一轮」⑨ 与「第十二轮」②。

#### ⚠️⚠️ 再往后一批（第十六批，2026-10-06，`cddaa9959..18d730465`）：**C1-06 取消首次真机通过 + 真实网关 5 条×3 稳定性复跑 + 两项关键发现；补登第十五批登记本体 2 个；声明值 46 行 / 501 例未动**

⚠️ **核验命令（登记代理本机实测，原文）**：

```
git log --oneline bfe1c982c..18d730465            # 2 行（18d730465 / 610edbf86）
git log --oneline cddaa9959..18d730465            # 4 行（上一批两个登记本体 + 本批 2 个）
git log --name-only bfe1c982c..18d730465 -- 'app/src/test/*'   # 空输出（本批无 app/src/test 在册类变化）
git show --stat e9091ffec                          # 1 file changed：docs/eval/c1-group-chat.md（+225/−20）
git show --stat bfe1c982c                          # 1 file changed：docs/beyond-operit-implementation-status.md（+39）
```

| SHA | 标题 | 性质 |
|---|---|---|
| `e9091ffec` | `coder: c1 文档登记真机第十一/十二轮证据节…台账第十五批8commit声明值46/501未动` | 文档（=第十五批登记本体，本批补登） |
| `bfe1c982c` | `coder: 实施状态补第二十批…设备遗留待下一轮` | 文档（status，=第十五批登记本体，本批补登） |
| `610edbf86` | `coder: C1-06 mock 补 /__reset、锁内一次性注入、实际 status/body 入账，零内容改为真正零 content 事件` | 工具（`tools/verification/mock_openai_slow_cancel.py` +151/−55；新 sha256 `0f2af473…`） |
| `18d730465` | `coder: C1-06 测试关 autoRetry 防真网关顶包、等 b 真流式再取消、b2 改按 modelId 认领（修空断言）` | 测试（`C1GroupCancelDeviceTest.kt` +155/−31，生产代码零改动） |

⚠️ **本批对台账声明值的影响：一个数都没动**——`git log --name-only bfe1c982c..18d730465 -- 'app/src/test/*'` **空输出**；两个测试/工具 commit 一个改 `tools/verification/`、一个改 `app/src/androidTest/`（不进台账口径）⇒ 声明值仍 **46 行 / 501 例**（`python3 tools/verification/c1_doc_stats.py` 本机实测见下）。

⚠️ **本批对判定的影响：20 个状态格一个升级都没有，仍是 10/10 `unverified`**——C1-06 取消路径首次拿到完整真机证据（先前三次全部零测量），但超时半未跑、viewer 集合未按契约落盘、单次通过、导出哈希一格仍缺；mention / vote / vote-tie 的重复稳定性首次满足，但「导出哈希」一格仍无内容（按判定规则第 2 条不升级）；roundtable 两次 FAIL 归因测试自身竞态（修复未验证）。逐行依据见「C1 真机证据采集第十三轮」⑤，新增遗留见第四十一~四十四条。


#### ⚠️⚠️ 再往后一批（第十七批，2026-10-06，`18d730465..b60aa80c5`）：**真机第十四轮（显式@/vote 多数决/vote 平票/预算截断/记忆隔离 五条带完整证据通过；roundtable/pipeline 第 3 位角色零产出 6/6）；C1-04/C1-05 首次升 `verified`；补登第十六批登记本体 2 个；声明值 46 行 / 501 例未动**

⚠️ **核验命令（登记子代理本机实测，原文）**：

```
git log --oneline 18d730465..HEAD                              # 5 行
git log --oneline 18d730465..HEAD | wc -l                      # 5
git log --oneline --merges 18d730465..HEAD | wc -l             # 0
git log --name-only 18d730465..HEAD -- 'app/src/test/*'        # 空输出（无 app/src/test 在册类变化）
git rev-parse HEAD                                             # b60aa80c5fd1082fa8a32c1aa9c718db22607354
```

⚠️ **本批 = 上一批（第十六批）按惯例留到本批补登的登记本体 2 个（`165d30b5a` / `a8449768c`，均只改 `docs/`）+ 本批主角 3 个（`a9df57c96` / `b1473f5d1` / `b60aa80c5`，逐条 `git show --stat` 核实全部只改 `app/src/androidTest/.../C1LiveModelSequenceTest.kt`）。**

| SHA | 标题 | 性质 |
|---|---|---|
| `165d30b5a` | `coder: 登记C1真机第十三轮证据节…20状态格就地订正仍全unverified，台账第十六批，新增遗留41-44并复核36/37/39/40` | 文档（=第十六批登记本体，本批补登；`c1-group-chat.md` +128/−25） |
| `a8449768c` | `coder: 实施状态补第二十一批…20状态格仍10/10 unverified，mock旧sha256订正` | 文档（status，=第十六批登记本体，本批补登） |
| `a9df57c96` | `coder: C1真实网关逐例导出SHA-256+viewer可见台账，修roundtable盖章竞态，新增预算截断专用用例` | 测试（`C1LiveModelSequenceTest.kt` +707/−9；生产代码零改动） |
| `b1473f5d1` | `coder: 新增C1-08记忆隔离设备侧用例——生产空间键/归因/检索/ viewer过滤四段证据` | 测试（`C1LiveModelSequenceTest.kt` +351；生产代码零改动） |
| `b60aa80c5` | `coder: 预算截断用例补库内助手发言数==2直读断言` | 测试（`C1LiveModelSequenceTest.kt` +11；生产代码零改动） |

⚠️ **本批对台账声明值的影响：一个数都没动**——`git log --name-only 18d730465..HEAD -- 'app/src/test/*'` **空输出**；本批三个主角全改 `app/src/androidTest/`（不进台账口径）⇒ 声明值仍 **46 行 / 501 例**（`python3 tools/verification/c1_doc_stats.py` 本机实测见下）。⚠️ **锚点 `1b0e04a9` 仍然没有被重算**：本批 5 个一个都不计入本台账任何计数——不改统计区间、不改 `--no-merges` 口径、不引入新的类型前缀或子包标签 ⇒ **`78 / 6 / 17` 与两张分布表一个数都没动**。

⚠️ **本批对判定的影响（本文件首次出现状态升级）：20 格里 4 格从 `unverified` 升 `verified`**——**C1-04（vote 多数决 + 平票两条路径，契约 8 项齐备、必须断言成立）** 与 **C1-05（预算截断，8 项齐备 + 库内 `messages.count=3`/`assistant_role_order=["a","b"]` 直读）**，各自在**用例矩阵表**与**证据登记表**两个格子里改值（共 4 格）。其余 16 格保持 `unverified` 并就地追加第十四轮订正：C1-01（契约 `:201`「其他角色不可见」字面半仍未观测）/ C1-02、C1-03（本批 3/3 全失败）/ C1-06 / C1-07（续跑测试仍未写）/ C1-08（缺模型序列·token·导出哈希三项）/ C1-09 / C1-10。逐行依据见「C1 真机证据采集第十四轮」⑤/⑦ 与 20 格订正；新增遗留见第 45/46 条，并对第 40 条做同源复核（不同来源）。

#### ⚠️⚠️ 再往后一批（第十八批，零设备，2026-10-07，`b60aa80c5..d6494e49a`）：**群聊失败续跑入口落地（生产代码）+ C1-07/C1-10 设备侧测试 + C1-06 证据字段补齐；20 状态格全不升；台账声明值 46 行 / 501 例 → 47 行 / 509 例（补登 `GroupRetryEntryTest` 8）**

⚠️ **核验命令（登记子代理本机实测，原文）**：

```
git log --oneline b60aa80c5..HEAD | wc -l              # 10
git log --oneline --merges b60aa80c5..HEAD | wc -l     # 0
git log --name-only b60aa80c5..HEAD -- 'app/src/test/*' # 只有 .../feature/chat/GroupRetryEntryTest.kt
git rev-parse HEAD                                     # d6494e49aa589f0527bdf87b64416f87d6292b68
```

⚠️ **本批 = 上一批（第十七批）按惯例留到本批补登的登记本体 6 个（`07cf703ce` / `5f9d92269` / `cdf88856a` / `42a0f29ae` / `5c3086882` / `c3e921a5c`，均只改 `docs/`）+ 本批主角 4 个（逐笔 `git show --stat` 核实）。**

| SHA | 标题 | 性质 |
|---|---|---|
| `07cf703ce` | `coder: 登记C1真机第十四轮证据节…` | 文档（=第十七批登记本体，本批补登） |
| `5f9d92269` | `coder: C1矩阵/证据表20格追加第十四轮订正（C1-04/05升verified）` | 文档（=第十七批登记本体） |
| `cdf88856a` | `coder: 对:4114与判定规则第十条的「偶发」结论加第十四轮订正块` | 文档（=第十七批登记本体） |
| `42a0f29ae` | `coder: c1文档台账追加第十七批5commit…声明值46行501例未动` | 文档（=第十七批登记本体） |
| `5c3086882` | `coder: 实施状态补第二十二批…C1-04/C1-05首次升verified` | 文档（status，=第十七批登记本体） |
| `c3e921a5c` | `coder: 修C1-08订正块里误入的字面竖线…doc_stats恢复18 OK/0 FAIL` | 文档（=第十七批登记本体） |
| `34145bf49` | `coder: 新增 C1-07 失败续跑/幂等设备侧测试（两阶段同一 round_id）` | 测试（`C1GroupRetryResumeDeviceTest.kt` 新增 968 行；生产代码零改动） |
| `1c48b1701` | `coder: 新增 C1-10 抽屉分页与筛选设备侧测试` | 测试（`C1GroupPagingAndFilterDeviceTest.kt` 新增 813 行 / 8 `@Test`；生产代码零改动） |
| `9d7429216` | `coder: C1-06 取消用例补 viewer 可见台账与导出 SHA-256 证据字段` | 测试（`C1GroupCancelDeviceTest.kt` +283/−0；生产代码零改动） |
| `d6494e49a` | `coder: 群聊失败续跑入口（复用 regenerateAtMessage，仅限失败角色错误节点）` | **功能（生产代码 `app/src/main` 5 files / +329）+ JVM 测试（`app/src/test` 新增 `GroupRetryEntryTest.kt`）** |

⚠️ **本批对台账声明值的影响：46 行 / 501 例 → 47 行 / 509 例**——`git log --name-only b60aa80c5..HEAD -- 'app/src/test/*'` 实测**只有 `GroupRetryEntryTest.kt`**（XML `tests="8"`，8 个 `@Test`）⇒ 新增 1 类 8 例。⚠️ **交接方原预测「脚本 ledger 会 FAIL」实测不成立**：脚本只逐行核对表内已列类、**不检查漏列**——本批是**按台账自身口径主动补登**（不是被 FAIL 逼出来）。改前 `--only ledger` 实测 `46 行 / 501 例`，改后 `47 行 / 509 例`、`逐行核对 47 行全部相等`。⚠️ 表内既有的旧 `合计` 行声明 `506` 与逐行求和 `501` 差 `5`，属历史笔误，**保留不覆写**。⚠️ **锚点 `1b0e04a9` 不重算**（`78 / 0 merges` 一个数没动）。

⚠️ **本批对判定的影响：20 格一个升级都没有**——C1-04 / C1-05 仍 `verified`（本批零设备、无新真机证据），其余 18 格仍 `unverified`（逐格理由见「第十五轮（零设备）」⑦）。新增遗留第 47/48 条。

#### ⚠️⚠️ 再往后一批（第十九批，真机第十五轮，2026-10-07，`d6494e49a..37630adb4`）：**零产出只读探针 + C1-01 两状态格升 `verified` + 第十五轮证据节登记；台账声明值 47 行 / 509 例未动**

⚠️ **核验命令（登记子代理本机实测，原文）**：

```
git log --oneline 37630adb4..HEAD | wc -l                 # 0（设备轮本身零 commit）
git log --oneline d6494e49a..37630adb4 | wc -l            # 7
git log --oneline --merges d6494e49a..37630adb4 | wc -l   # 0
git log --name-only d6494e49a..37630adb4 -- 'app/src/test/*'   # 空
git rev-parse HEAD                                        # 37630adb4aecf3a8e0fa3d71d2fb1db8d4005fcb
```

⚠️ **本批 = 上一批（第十八批）按惯例留到本批补登的登记本体 5 个（均只改 `docs/`）+ 本批主角 2 个。**

| SHA | 标题 | 性质 |
|---|---|---|
| `67331e5ff` | `coder: C1台账补登 GroupRetryEntryTest(8例)…` | 文档（=第十八批登记本体，本批补登） |
| `6e4a15ec4` | `coder: 登记C1第十五轮零设备证据节…20状态格就地追加第十五轮订正` | 文档（=第十八批登记本体） |
| `a2eb51d02` | `coder: C1遗留清单同步——新增47/48` | 文档（=第十八批登记本体） |
| `f1ff493d2` | `coder: c1文档台账追加第十八批10commit…` | 文档（=第十八批登记本体） |
| `57cd82ebc` | `coder: 实施状态补第二十三批…` | 文档（status，=第十八批登记本体） |
| `00bcdd4e9` | `coder: 新增 C1 群聊零产出只读探针，采样 session 内存态区分成因①②③` | **测试（`C1GroupZeroOutputProbeTest.kt` 新增；生产代码零改动）** |
| `37630adb4` | `coder: C1-01两状态格升verified…证据登记表补8项` | 文档（C1-01 矩阵格 + 证据表格各升 `verified`） |

⚠️ **本批对台账声明值的影响：无**——`git log --name-only d6494e49a..37630adb4 -- 'app/src/test/*'` 实测**为空** ⇒ 台账声明值仍 **47 行 / 509 例**（`--only ledger` 实测 `台账行数 47` / `声明例数合计 509` / `逐行核对 47 行全部相等`，OK 3 / WARN 0 / FAIL 0）。⚠️ **锚点 `1b0e04a9` 不重算**（`78 / 0 merges` 一个数没动）。

⚠️ **本批对仪器测试口径的影响**：`00bcdd4e9` 新增 tracked androidTest 文件 `C1GroupZeroOutputProbeTest.kt` ⇒ `c1_doc_stats.py` 的「仪器 @Test（仅 tracked）」由上一批的 19 文件 / 81 例变为 **20 文件 / 82 例**，「含 untracked」为 **21 文件 / 85 例**（untracked 仍为 `C1GroupUiE2EFixtureTest.kt` 1 文件 / 3 例）。

⚠️ **本批对判定的影响：20 格里 4 格从 `unverified` 升 `verified`**——**C1-02（真实网关 pipeline，契约 8 项齐备）** 与 **C1-03（真实网关 roundtable，契约 8 项齐备）**，各在**用例矩阵表**与**证据登记表**两个格子改值（共 4 格）。其余 14 格保留原文 + 就地追加「真机第十五轮订正」：C1-06（超时半结构性做不到、单次通过）/ C1-07（2/2 失败待判定）/ C1-08（缺 3 项、待用户认可）/ C1-09（本批未碰）/ C1-10（3 通过 / 5 失败）；C1-01 / C1-04 / C1-05 按「已 `verified` 不重开」惯例不改。逐格依据见「C1 真机证据采集第十五轮」⑤。新增遗留第 49 条（C1-07 稳定失败待判定）与第 50 条（C1-10 `pagingSource_*` 5 失败 + DB 55 vs 累计 60/40）；第 45 条就地把「6/6 稳定复现」订正为「间歇性」；第 39 条补「设备设置已还原」。

#### ⚠️⚠️ 再往后一批（第二十一批，真机第十六轮，2026-10-07，`5c362851a..bd88aaff2`）：**C1-07 续跑幂等真机首次通过（两状态格升 `verified`）+ C1-10 分页筛选 8/8 修复后重跑全绿；补登第二十批登记本体 3 个；台账声明值 48 行 / 514 例未动**

⚠️ **核验命令（登记子代理本机实测，原文）**：

```
git log --oneline 5c362851a..bd88aaff2 | wc -l                 # 3（第二十批登记本体；设备轮本身零 commit）
git log --oneline --merges 5c362851a..bd88aaff2 | wc -l        # 0
git log --name-only 5c362851a..bd88aaff2 -- 'app/src/test/*'   # 空
git rev-parse HEAD                                        # bd88aaff2ae00574cc76badb562f05e3bc13a8df
```

⚠️ **本批 = 上一批（第二十批，零设备修复批次）按惯例留到本批补登的登记本体 3 个（均只改 `docs/`）+ 本批主角 = 真机采集窗口（设备轮零 commit）。** ⚠️ 第二十批的**台账声明值变动**（`ConversationPageLoadParamsTest` 补登 + 新增 `合计` 行）已由 `8bece56f0` 直接写进上方「C1 相关 JVM 测试类台账」表，**未另起台账小节**；本小节一并登记其登记本体。

| SHA | 标题 | 性质 |
|---|---|---|
| `5e13626c9` | `coder: 登记C1零设备第二十批证据节——C1-10分页测试驱动+ConversationRepository分页API生产修复+C1-07续跑断言` | 文档（=第二十批登记本体，本批补登） |
| `8bece56f0` | `coder: C1矩阵/证据表20格追加第二十批订正(全不升)+台账补登ConversationPageLoadParamsTest(5例,48行514例)+遗留49/50标已修` | 文档（=第二十批登记本体） |
| `bd88aaff2` | `coder: 实施状态补第二十五批——零设备修复批次(分页测试驱动+分页API生产缺陷+C1-07续跑断言)，20状态格全不升，台账48行514例` | 文档（status，=第二十批登记本体） |

⚠️ **本批对台账声明值的影响：无**——`git log --name-only 5c362851a..bd88aaff2 -- 'app/src/test/*'` 实测**为空**（本批只改 `androidTest` 与 `docs`）⇒ 台账声明值仍 **48 行 / 514 例**（`--only ledger` 实测 `台账行数 48` / `声明例数合计 514` / `逐行核对 48 行全部相等`，OK 3 / WARN 0 / FAIL 0）。⚠️ **锚点 `1b0e04a9` 不重算**（`78 / 0 merges` 一个数没动）。

⚠️ **本批对仪器测试口径的影响：无**——本批两个设备侧改动（`0ae9f570a` / `5c362851a`）都改**既有文件**，仪器 `@Test` 条数不变（`c1_doc_stats.py` 的「仪器 @Test（仅 tracked）」仍 **20 文件 / 82 例**，「含 untracked」仍 **21 文件 / 85 例**）。

⚠️ **本批对判定的影响：20 格里 2 格从 `unverified` 升 `verified`**——**C1-07（失败续跑/幂等，契约 `:204` 8 项齐备）** 在**用例矩阵表**与**证据登记表**两个格子改值（共 2 格）。其余 18 格保留原文 + 就地追加「真机第十六轮订正」：C1-06（超时半结构性做不到）/ C1-08（缺 3 项、语义正交待用户认可）/ C1-09（本批未碰）/ C1-10（8/8 全绿但契约 `:232-235` 不认仅 UI 状态、`:206` 四类产物本例不产出）；C1-01 / C1-02 / C1-03 / C1-04 / C1-05 按「已 `verified` 不重开」惯例维持 `verified`、各就地追加维持块。遗留第 49/50 条就地追加「✅ 已修 + 真机重跑通过」；第 45 条本批未碰、不动。逐格依据见「C1 真机证据采集第十六轮」⑤。

⚠️ **本批登记本体 = 真机第十六轮证据节 + 20 状态格订正 + 本台账小节 + 遗留 49/50 补注 + 实施状态第二十六批**（分几个 commit 提交，SHA 提交后才知道）——**按惯例留到下一批补登，本表不编造**。

#### ⚠️⚠️ 再往后一批（第二十二批，真机第十七轮 / C1-09 收尾，2026-10-07，`bd88aaff2..e2bfce4ad`，共 15 个 commit）：**真 SillyTavern 应用级导入验证 + 二维码位图真机往返 + 二维码中文有损缺陷修复；C1-09 仍未升；补登第二十一批登记本体 6 个；台账声明值 48 行 / 514 例 → 50 行 / 524 例（补登 2 个新测试类 10 例）**

⚠️ **核验命令（登记子代理本机实测，原文）**：

```
git rev-parse HEAD                                      # e2bfce4adbf2ca03195c4030b9763a6a71af37aa
git log --oneline ae08f2525..HEAD | wc -l               # 9（本批主角 9 个）
git log --oneline --merges ae08f2525..HEAD | wc -l      # 0
git log --oneline bd88aaff2..e2bfce4ad | wc -l          # 15 = 上一批登记本体 6 + 本批主角 9
git log --name-only ae08f2525..HEAD -- 'app/src/test/*' # 2 个新文件（见下）
git log --name-only ae08f2525..HEAD -- 'docs/*'         # 空（本批主角零文档）
```

⚠️ **窗口记法**：本批窗口用**具体端点** `bd88aaff2..e2bfce4ad`（沿用 `ae08f2525` 那条「避免 HEAD 漂移」的记法），不用 `..HEAD`。

**本批 = 上一批（第二十一批，真机第十六轮）按惯例留到本批补登的登记本体 6 个（`bd88aaff2..ae08f2525`，均只改 `docs/`）+ 本批主角 9 个（`ae08f2525..e2bfce4ad`）。**

| SHA | 标题 | 性质 |
|---|---|---|
| `fa1498718` | `coder: 登记C1真机第十六轮证据节——C1-07续跑幂等真机首次通过(8项齐备)+C1-10分页筛选修复后8/8全绿+20格逐格判定` | 文档（=第二十一批登记本体，本批补登） |
| `1dcf89dbc` | `coder: C1矩阵/证据表C1-07两格升verified+20格追加真机第十六轮订正(其余维持/不升)` | 文档（=第二十一批登记本体，本批补登） |
| `d3d558de1` | `coder: c1文档台账追加第二十一批——补登第二十批登记本体3个，声明值48行514例未动，锚点不重算` | 文档（=第二十一批登记本体，本批补登） |
| `b79b27b8c` | `coder: 遗留49/50就地追加✅已修+真机重跑通过(C1-07 Time13.108、C1-10 8/8)，原文保留` | 文档（=第二十一批登记本体，本批补登） |
| `dc8af34af` | `coder: 实施状态补第二十六批——真机第十六轮C1-07两格升verified、C1-10修复后8/8仍unverified，台账48行514例未动` | 文档（status，=第二十一批登记本体，本批补登） |
| `ae08f2525` | `coder: 台账/实施状态窗口记法改具体端点(5c362851a..bd88aaff2)，避免HEAD漂移歧义` | 文档（=第二十一批登记本体，本批补登） |
| `28c05f2db` | `coder: C1-09③新增QR载荷编解码往返+导出分享ACTION_SEND护栏两测试(仅测试,生产零改动)` | 测试（新增 2 个 JVM 类 10 例） |
| `5833cbbd9` | `coder: 新增 SillyTavern 应用级导入验证 harness` | 工具/测试（`tools/verification/verify_sillytavern_import.py`） |
| `c21cc4028` | `coder: harness 野生 jsonl 判定改为成员检测，支持重复运行` | 工具（同上） |
| `f35c8ccf0` | `coder: QRCode.kt 提取 String->Bitmap 为顶层 internal fun encodeQrBitmap，渲染不变` | **生产**（`QRCode.kt`） |
| `5f461d9f6` | `coder: 新增 C1-09 二维码位图端到端真机往返测试(encodeQrBitmap+MLKit+FileProvider+Intent)` | 测试（androidTest） |
| `16d00e9f4` | `coder: 位图往返测试补字符集限制用例、夹具改 Latin-1、修正长度边界` | 测试（androidTest） |
| `0edc70d67` | `coder: 二维码编码显式传 CHARACTER_SET=UTF-8，修复中文等非 Latin-1 内容被有损替换成 ?` | **生产**（`QRCode.kt`） |
| `e35f64e39` | `coder: 二维码中文/emoji 往返测试改为正面强断言，容量常数按 ECI 实测 2953->2952` | 测试（androidTest） |
| `e2bfce4ad` | `coder: 中文二维码用例夹具含 热血解说 并落盘 charset 往返证据` | 测试（androidTest） |

⚠️ **本批对台账声明值的影响：48 行 / 514 例 → 50 行 / 524 例**（`+2 类 / +10 例`）——实测依据：

```
git log --name-only ae08f2525..HEAD -- 'app/src/test/*'
# app/src/test/.../core/data/model/C1GroupQrPayloadCodecRoundTripTest.kt      （5 @Test）
# app/src/test/.../feature/chat/GroupExportShareIntentSourceGuardTest.kt      （5 @Test）
```

⇒ 补登 2 个新类（各 5 例）。✅ `c1_doc_stats.py --only ledger` 改后实测：`台账行数 50 行 / 台账声明例数合计 524 例 / 逐行核对 50 行全部相等`，**OK 3 / WARN 0 / FAIL 0**。⚠️ **锚点 `1b0e04a9` 不重算**（仍是 `78 / 0 merges`）。

⚠️ **本批对仪器测试口径的影响**：新增 1 个 `androidTest` 文件 `C1GroupQrBitmapRoundTripDeviceTest.kt`（6 `@Test`）⇒ `c1_doc_stats.py` 的「仪器 @Test（仅 tracked）」由 **20 文件 / 82 例 → 21 文件 / 88 例**，「含 untracked」由 **21 文件 / 85 例 → 22 文件 / 91 例**（±6，同一新增类）。

⚠️ **本批对判定的影响：20 格 0 格改值**——**C1-09** 列为本批重点，按契约 `:205` / `:206` / `:232-235` 逐条核对后**仍维持 `unverified`**（虽拿到真 SillyTavern 应用级导入 + 二维码位图真机往返 + 中文有损修复 + 导出 URI 字节 == 生产 JSONL + `ACTION_SEND` 源码护栏；但缺口未消：`·206` 三类产物结构性不产出且未获用户认可、真实相机扫码链路零份、真实系统分享面板 UI 零份、酒馆 `open→save` 丢私有块且重存回 KhatKit 得 `Unsupported`）。其余 19 格维持原判。逐格依据见「C1 真机证据采集第十七轮」⑦。

⚠️ **本批登记本体 = 真机第十七轮 / C1-09 收尾证据节 + 20 状态格订正 + 本台账小节 + 遗留清单同步 + 实施状态第二十七批**（分几个 commit 提交，SHA 提交后才知道）——**按惯例留到下一批补登，本表不编造**。

#### ⚠️⚠️ 再往后一批（第二十三批，真机第十八轮，2026-10-07，`e2bfce4ad..2a0610705`，共 17 个 commit）：**C1-06 超时半条真机通过（C1-06 升 `verified`）+ C1-08 串扰断言结构性化并证判别力（C1-08 升 `verified`）+ C1-10 真实网关轮与点名路径正交（维持 `unverified`）；台账声明值 50 行 / 524 例 → 51 行 / 531 例（补登 1 个新测试类 7 例）**

⚠️ **核验命令（登记子代理本机实测，原文）**：

```
git rev-parse HEAD                                      # 2a0610705f3f71ff2702d656f340155e60fcfc2d
git log --oneline e2bfce4ad..HEAD | wc -l               # 17
git log --oneline --merges e2bfce4ad..HEAD | wc -l      # 0
git log --name-only e2bfce4ad..HEAD -- 'app/src/test/*' # 只有 .../feature/chat/C1MemoryIsolationPredicateTest.kt
```

⚠️ **窗口记法**：用**具体端点** `e2bfce4ad..2a0610705`（沿用「避免 HEAD 漂移」的记法），不用 `..HEAD`。

**本批 = 本批主角（`4af7a66be`..`2a0610705` 等）+ 上一批（第二十二批）按惯例留到本批补登的登记本体 6 个 + 同窗口内的 C1-09 二维码相机链路 / C1-08·C1-10 真实网关抽取等 commit。**

| SHA | 标题 | 性质 |
|---|---|---|
| `2a0610705` | `coder: C1-08 串扰断言改结构性（role_id/source/space/无回退）+ JVM 变异镜像证明判别力` | 测试（androidTest + test） |
| `eb614914b` | `coder: C1-06 超时半条真机用例（注入 5s 步超时 + mock 挂住 B，断言 TIMEOUT/零空气泡/错误节点）` | 测试（androidTest） |
| `4c71b7d80` | `coder: mock 新增 MOCK_HANG_ROLE 档位（只发 opener 后挂住，默认关闭不改现有行为）` | 工具（mock） |
| `4af7a66be` | `coder: C1-06 群聊步超时改为可注入（默认值不变，生产装配零改动）` | **生产**（`ChatManager.kt`） |
| `389f263d6` | `coder: C1-08 真实抽取段：捕获失败+有界重试+改用 glm-5.2/MEMORY fastModel，检索用 fact 文本（真机 OK 1 test）` | 测试（androidTest） |
| `7da57ece4` | `coder: C1-10 补真实网关群聊轮测 9：既有夹具群真跑一轮，产出调用序列/token/导出哈希/viewer台账（原 8 断言不动）` | 测试（androidTest） |
| `151b45115` | `coder: C1-08 补真实网关记忆抽取段：真模型调用序列/token/导出哈希/viewer台账（canned 断言一条不动）` | 测试（androidTest） |
| `2aeb31ed3` | `coder: 真机测试改用 YUV_420_888 合成帧，钉住 rotation 传递与多码取首` | 测试（androidTest） |
| `22451c842` | `coder: 拆出 toQrInputImage seam，令 rotationDegrees 传递可观测（processQrFrame 复用）` | 测试/生产 seam |
| `d3d3f4c78` | `coder: 新增 C1 真机相机扫码链路 androidTest（ImageReader 合成 mediaImage -> 生产 seam 解码）` | 测试（androidTest） |
| `616bc7aff` | `coder: 抽取 QrScannerSheet 的 mediaImage->MLKit 解码 seam（processQrFrame/buildQrScanner/firstQrValue）` | **生产**（`QrScannerSheet.kt`） |
| `d420dbb78` | `coder: 实施状态补第二十七批(真机第十七轮/C1-09收尾...)` | 文档（=第二十二批登记本体，本批补登） |
| `d1b2537f8` | `coder: C1遗留清单新增51-54(...)+lint段订正+第4轮NoConfig就地订正` | 文档（=第二十二批登记本体，本批补登） |
| `5d555c0f8` | `coder: c1文档台账追加第二十二批(真机第十七轮/C1-09收尾)...` | 文档（=第二十二批登记本体，本批补登） |
| `f7620d253` | `coder: C1台账补登C1GroupQrPayloadCodecRoundTripTest/GroupExportShareIntentSourceGuardTest...` | 文档（=第二十二批登记本体，本批补登） |
| `b0c517535` | `coder: C1矩阵/证据表20格就地追加第二十二批订正(维持原判,改值0格...)` | 文档（=第二十二批登记本体，本批补登） |
| `123b73b5a` | `coder: 登记C1真机第十七轮/C1-09收尾——真酒馆应用级导入+二维码位图真机往返+中文有损修复` | 文档（=第二十二批登记本体，本批补登） |

⚠️ **本批对台账声明值的影响：50 行 / 524 例 → 51 行 / 531 例**（`+1 类 / +7 例`）——实测依据：

```
git log --name-only e2bfce4ad..HEAD -- 'app/src/test/*'
# app/src/test/.../feature/chat/C1MemoryIsolationPredicateTest.kt   （7 @Test）
```

⇒ 补登 1 个新类（7 例）。✅ `c1_doc_stats.py` 改后实测：`台账行数 51 行` / `台账声明例数合计 531 例` / `逐行核对 51 行全部相等`。⚠️ **锚点 `1b0e04a9` 不重算**（仍是 `78 / 0 merges`）。

⚠️ **本批对仪器测试口径的影响**：tracked **21 文件 / 88 例 → 22 文件 / 96 例**；含 untracked **22 文件 / 91 例 → 23 文件 / 99 例**（本窗口新增 `C1GroupQrCameraPathDeviceTest` 等文件 + `C1GroupCancelDeviceTest` / `C1GroupPagingAndFilterDeviceTest` 各增 `@Test`）。

⚠️ **本批对判定的影响：20 格 4 格改值** —— **C1-06 / C1-08 各在两表内的状态格升 `verified`**（从 `unverified` → `verified`）；**C1-10 维持 `unverified`**；其余 7 例维持原判。逐格依据见「C1 真机证据采集第十八轮」⑤。

⚠️ **本批登记本体 = 真机第十八轮证据节 + 20 状态格订正 + 本台账小节 + 实施状态第二十八批**（分几个 commit 提交，SHA 提交后才知道）——**按惯例留到下一批补登，本表不编造**。

#### ⚠️⚠️ 再往后一批（第二十四批，真机第十九轮，2026-10-07，`2a0610705..1758e2109`，共 4 个 commit）：**C1-10 第 9 条改造为「筛选路径内」真跑一轮（C1-10 升 `verified`）；台账声明值 51 行 / 531 例不变（本批零 JVM 改动）**

⚠️ **核验命令（登记子代理本机实测，原文）**：

```
git rev-parse HEAD                                      # 1758e2109b6fef396c361c5c87dc923f16c019e0
git log --oneline 2a0610705..HEAD | wc -l               # 4
git log --oneline --merges 2a0610705..HEAD | wc -l      # 0
git log --name-only 2a0610705..HEAD -- 'app/src/test/*' # （空）
```

⚠️ **窗口记法**：用**具体端点** `2a0610705..1758e2109`（沿用「避免 HEAD 漂移」的记法），不用 `..HEAD`。

**本批 = 本批主角 2 个（`df4c70f09` / `1758e2109`，均只改 androidTest）+ 上一批（第二十三批）按惯例留到本批补登的登记本体 2 个。**

| SHA | 标题 | 性质 |
|---|---|---|
| `1758e2109` | `coder: C1-10 第9条筛选路径改造加固（收窄 runBlocking 作用域/筛选态断言加严/导航缝证据），真机 OK(1 test) 19.62s` | 测试（androidTest） |
| `df4c70f09` | `coder: C1-10 第9条改造为从筛选路径内部抵达群聊并跑真实一轮（记录 opened_from_filtered_list/opened_group_id）` | 测试（androidTest） |
| `bf5c4d91b` | `coder: 实施状态补第二十八批——C1-06/C1-08首次升verified、C1-10维持、台账51行531例、基线118类961例` | 文档（=第二十三批登记本体，本批补登） |
| `179994155` | `coder: c1文档登记真机第十八轮——C1-06/C1-08升verified(20格改4格)、C1-10维持、台账51行531例、基线118类961例` | 文档（=第二十三批登记本体，本批补登） |

⚠️ **本批对台账声明值的影响：不变（51 行 / 531 例）**——实测依据：

```
git log --name-only 2a0610705..HEAD -- 'app/src/test/*'
# （空输出：本窗口 app/src/test 无任何新增 / 修改）
```

⚠️ 本批只改 androidTest，**没有**新增 JVM 测试类 / 用例 ⇒ 声明值保持 **51 行 / 531 例**（上一批刚由 `C1MemoryIsolationPredicateTest` 7 例推到 51/531）。⚠️ **锚点 `1b0e04a9` 不重算**（仍 `78 / 0 merges`）。✅ `c1_doc_stats.py` 改后实测：`台账行数 51 行` / `台账声明例数合计 531 例` / `逐行核对 51 行全部相等`。

⚠️ **本批对仪器测试口径的影响：无**——只改 `C1GroupPagingAndFilterDeviceTest.kt` 第 9 条**方法体**，不增删文件 / `@Test` ⇒ tracked **22 文件 / 96 例**、含 untracked **23 文件 / 99 例** 不变。

⚠️ **本批对判定的影响：20 格 2 格改值** —— **C1-10 在两表内的状态格升 `verified`**（从 `unverified` → `verified`）；其余 9 例维持原判。逐格依据见「C1 真机证据采集第十九轮」②。

⚠️ **本批登记本体 = 真机第十九轮证据节 + 20 状态格订正 + 本台账小节 + 遗留 55-57 + 实施状态第二十九批**（分几个 commit 提交，SHA 提交后才知道）——**按惯例留到下一批补登，本表不编造**。

#### ⚠️⚠️ 再往后一批（第二十五批，真机第二十轮，2026-10-07，`1758e2109..83dbf014f`，共 4 个 commit）：**C1-09 同源往返用例首次把真机一轮的三类产物与同群 QR 载荷硬连接；真实系统分享面板仍零份 ⇒ C1-09 维持 `unverified`（20 格 0 格改值）；台账声明值 51 行 / 531 例不变（本批只改 androidTest）**

⚠️ **核验命令（登记子代理本机实测，原文）**：

```
git rev-parse HEAD                                      # 83dbf014f95c4da58ee136a124149fcf17c94e25
git log --oneline 1758e2109..83dbf014f | wc -l          # 4
git log --oneline --merges 1758e2109..83dbf014f | wc -l # 0
git log --name-only 766c61434..HEAD -- 'app/src/test/*' # （空）
```

⚠️ **窗口记法**：用具体端点 `1758e2109..83dbf014f`（避免 HEAD 漂移），不用 `..HEAD`。

**本批 = 本批主角 2 个（`766c61434` / `83dbf014f`，均只改 androidTest）+ 上一批（第二十四批）按惯例留到本批补登的登记本体 2 个（`2c304efe3` / `ffc3989e5`）。**

| SHA | 标题 | 性质 |
|---|---|---|
| `83dbf014f` | `coder: C1-09 真网关往返用例对偶发 role_failed 整轮重试并解析导出 mes 字段比对` | 测试（androidTest） |
| `766c61434` | `coder: 新增 C1-09 真网关一轮同源导出 Tavern JSONL 与同群二维码位图往返测试` | 测试（androidTest） |
| `ffc3989e5` | `coder: 实施状态补第二十九批——C1-10第9条筛选路径内真跑一轮升verified、20格改2格、台账51行531例不变` | 文档（=第二十四批登记本体，本批补登） |
| `2c304efe3` | `coder: c1文档登记真机第十九轮——C1-10第9条筛选路径内真跑一轮、8字段首次由筛选群产出、C1-10升verified(20格改2格)` | 文档（=第二十四批登记本体，本批补登） |

⚠️ **本批对台账声明值的影响：不变（51 行 / 531 例）**——`git log --name-only 766c61434..HEAD -- 'app/src/test/*'` 实测**空输出**（本窗口只改 androidTest）。⚠️ **锚点 `1b0e04a9` 不重算**（仍 `78 / 0 merges`）。✅ `c1_doc_stats.py` 改后实测：`台账行数 51 行` / `台账声明例数合计 531 例` / `逐行核对 51 行全部相等`。

⚠️ **本批对仪器测试口径的影响：`C1LiveModelSequenceTest` 新增 1 个 `@Test` 方法**（`766c61434`）⇒ tracked **22 文件 / 96 → 97 例**、含 untracked **23 文件 / 99 → 100 例**；文件数不变。

⚠️ **本批对判定的影响：20 格 0 格改值** —— **C1-09 维持 `unverified`**（真实系统分享面板零份，逐条依据见「C1 真机证据采集第二十轮」③）；其余 9 例维持原判。20 格每格就地追加「第二十五批订正」（保留原文）。

⚠️ **本批登记本体 = 真机第二十轮证据节 + 20 状态格订正 + 本台账小节 + 实施状态第三十批**（分几个 commit 提交，SHA 提交后才知道）——**按惯例留到下一批补登，本表不编造**。

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
   ⚠️ **第三个零成本前置也已做完**：`python3
   tools/verification/c1s_conversation_type_filter_replay.py`（**438 条断言全过**，两次
   退出码 0 / 输出逐字节相同），登记见「C1-S 抽屉 type 筛选主机侧重放与两个真实缺陷修复
   （零设备）」。它把「类型筛选只过滤」的**主机侧 SQL 语义**钉住了，**但替代不了 UI
   端到端**——第 6 条里 C1-10 要的「录屏走 chip 切换」仍然是零设备证据。
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
| C1-10 | 筛选只过滤 + 切换不丢数据的真机记录 | 同一助手下混建单聊与群聊，录屏走「全部 → 群聊 → 单聊 → 全部」四个 chip 切换，贴切换前后两侧会话的消息数与最后一条消息 id。⚠️ **零成本的那一半已做完**：主机侧重放 438 条断言钉住了「类型筛选只过滤」的 SQL 语义（集合相等、count 正确、分页无重复无遗漏、切 type 不改查询种类），见「C1-S」那节。**剩下的是真机那半**：`LIMIT/OFFSET` 是脚本机械模拟的，`PagingSource` 的并发失效 / 快照一致性 / 回滚**一条都没验**，抽屉 UI 端到端也一次没跑过 |

每补完一项，在证据登记表**追加一行**（不覆盖历史行），把四列缺失证据填上，
再把用例矩阵的状态改成 `verified`。**四类证据缺任何一类，仍保持 `unverified`。**

## 下一位怎么把剩下的做完（第六个窗口的操作清单，2026-10-06）

⚠️ **这是本轮最重要的产出**，按「**需要设备 / 需要外部环境 / 零设备可做**」分三组。
每项都写了具体命令或具体步骤。⚠️ **上面那些「还剩什么」的表格是描述缺口，这一节是
可以照着敲的动作**——两者不要互相替代。

⚠️ **三组的前置硬约束（照旧）**：**① 只改 `docs/` 之外的东西之前先确认本节的
分组归属**（设备侧动作要真机、外部环境动作要桌面端 / 第二台设备）；**② 任何一项做完
都只往证据登记表追加行 + 状态列追加 `<br>**新窗口…**`，不许覆写历史**；
**③ 不要把「设备侧跑完了」当成「用例通过了」**——还要看那一行点名的四类产物齐不齐。

### A. 需要设备（一台 Android 真机，`adb devices` 能看到 serial）

⚠️ **通用前置，按顺序做完再跑后面的**（每一条都是踩过的坑）：

```
adb mdns services                      # 找当前端口；⚠️ 无线调试的端口每次都变
adb devices -l                         # ⚠️ 必须只剩 1 条（mDNS 会把同一台机器注册两条）
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
am set-inactive heizige.kk.khatkit.debug false      # 压 OEM 回收
dumpsys deviceidle whitelist | grep khatkit
```

⚠️ **`connectedDebugAndroidTest` 跑完会卸载 app 并删掉外置目录里的证据文件**——采证据
一律用**手动 `install -r -t` + 手动 `am instrument`**，跑完立刻 `adb pull`。

**A1. 全量仪器测试（验证 `ea482935` 的 Coil 修复——本轮唯一一条「跑一条命令就能验」的项）**

```
./gradlew --offline :app:connectedDebugAndroidTest
python3 - <<'EOF'
import glob, xml.etree.ElementTree as ET
for p in glob.glob('app/build/outputs/androidTest-results/connected/**/*.xml', recursive=True):
    print(p, dict(ET.parse(p).getroot().attrib))
EOF
```

**通过判据**：那份 XML 的 `tests` 落在 **≈61**（git 跟踪口径的 `androidTest` `@Test`
总数）、**`failures="0"`**、**不再出现** `The singleton image loader has already been
created`。⚠️ **当前磁盘上那份是 `tests="10" failures="1"`——那是修复前的现场，不是结果。**

**A2. ~~`C1LiveModelSequenceTest` 其余 4 条真机全绿~~** ✅ **第七批（HEAD `86e88970d`）已做完**：
四条退出码全 `0`、全 `OK (1 test)`。⚠️ **第四条的 app 侧 JSON 未 pull**，只剩旁证——
要补的就是把那份 `c1-live-evidence-vote.json` 从设备
`/sdcard/Android/data/heizige.kk.khatkit.debug/files/` 拉回来。
（下面这段 `adb` 命令即当时用的那套，仍然有效。）

```
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
for m in pipelineRoundRecordsRealModelSequenceAndRealTokenUsage \
         budgetTruncationSkipsRemainingRolesAndRecordsRunLog \
         roundtableRoundRecordsChairSummaryCallSequence \
         voteRoundRecordsBallotCallsAndSummarySequence; do
  adb shell am instrument -w \
    -e class "heizige.kk.khatkit.app.feature.chat.C1LiveModelSequenceTest#$m" \
    heizige.kk.khatkit.debug.test/androidx.test.runner.AndroidJUnitRunner
done
```

⚠️ **单方法过滤的 `#` 必须加引号**（`sh -c` 里未加引号会被当注释）。这 4 条跑完才谈得上
C1-02 / C1-03 / C1-04 / C1-05 / C1-07 的「那条路径真的被调用过」。

**A3. 真机 UI 端到端三件（本轮一项都没做，按优先级排）**

1. **筛选 chip 来回切换**（C1-10 的实质缺口）：录屏走「全部 → 群聊 → 单聊 → 全部」，
   贴切换前后两侧会话的消息数与最后一条消息 id。
2. **成员头像组**：`GroupMemberBar` 的渲染 + 点击切视角（`uiautomator dump` 取节点文本
   即可，别只看截图）。
3. **@ 选择器**：`GroupRoleCompletionProvider` 的补全列表在**真机上**弹出来，且**选完之后
   投递确实收窄**——这是 C1-01「其他角色不可见」那半句唯一的取证机会
   （夹具的 `@` 落在用户消息上，方向相反，见遗留第 15 条）。
   ⚠️ **第八轮进展（HEAD `31b6b5a52`，部分完成）**：**@ 选择器**已真机弹出 + 输入框真实文本回读 + `parseMentionsFromUiText` 真机通过（覆盖较好）；**筛选 chip** 三态点击有截图 + 切换前后 DB 计数不变，但**「真的按类型过滤」未验证**（设备助手被测试助手顶替导致列表恒空，见遗留第 34 条）；**成员头像组**仅截图目视、**无自动化断言**。详见「C1 真机证据采集第八轮」⑤。

**A4. 群配置面板的三个业务动作**（本轮只验了渲染与滚动）：保存群配置 → 重开面板确认
字段回填；导出分享生成二维码（贴二维码载荷的 SHA-256）；导入恢复扫码导入。⚠️ **扫码那条
同时覆盖 C1-09 的相机链路**（CameraX + MLKit 至今没在设备上跑过）。

**A5. Miuix 风格复验**（两个崩溃都只在 `KedgeStyle.MD3Exp` 触发过）：把 Kedge 风格切到
Miuix 再开群配置面板 + 滚动，确认① 图标缺失不再崩（Miuix 分支本就不走 `requireNotNull`，
**预期是不崩但也没有图标**——把这个差异记下来）② 面板能滚 ③ **另外 62 个
`PrimaryBottomSheet` 调用点至少抽查几个**（它们的图标/滚动参数同样没人上过真机）。

**A6. 迁移侧设备动作**（B1 剩下的全部尾巴）：拿一个**从 Room 31 升上来的旧库**装上去
（不是全新安装），确认 31→32 升级不崩、旧行 `group_cards` 是空串、群聊页刷新后导入的
角色卡还在。⚠️ 真机库里 `user_version = 32` **不等于**跑过一次 31→32 升级。

**A7. 残留旧 job 的人工编排时序**（遗留第 24 条，本类缺陷零观测）：同一会话里一条长
生成在飞 → 期间发新消息把它判死 → 等旧 job 跑完（或反过来先超时）。⚠️ **这不是「跑一遍
测试」能覆盖的**，必须人工编排。

**A8. 四项代码债的运行时验证**（`fa36c65c` / `06cf6181` 的运行时全是零证据）：
- `require(!isGroupConversation(...))`：需要构造一个 `ChatService` 的活跃注入点才能触发
  （当前零活跃构造点，见该节），**所以这条更像「先定要不要留这个类」的产品/架构决策**，
  见 C 组 C5；
- 判死分批清空：造 **>8 条**并发 `RUNNING` 行（同一会话连续 9 次让轮次不落终态），
  确认第 9 条起也被判死；
- 按令牌清镜像：同一会话「新轮在飞 + 旧 job 跑完」的时序下，新轮仍能被取消。

### B. 需要外部环境（设备之外的东西：桌面端、第二台设备、本机代理）

**B1. 酒馆（SillyTavern）本体打开群聊导出文件**（C1-09 的实质缺口，**零证据**）：
真机导出 → `adb pull` → 桌面端 SillyTavern 打开 → 截图/录屏。
⚠️ AGPL 边界照旧：只借鉴**名单顺序**与**整词 `@`**，不照搬共享历史设计。
⚠️ 这一项**从 2026-10-05 起四个窗口一次都没动过**，是最老的一块缺口。

**B2. wire 级模型名**（硬理由①，唯一能消掉它的办法）：二选一——
(a) 在 `OpenAIProvider` 侧记录响应的 `model` 字段（**属于加日志**，要单独评估隐私）；
(b) 设备侧经**自签代理**抓包（`adb reverse` 到本机代理，或网关侧开 access log）。
⚠️ 只做 (a) 的话记得同步更新 JSON 里的 `wire_model_name_provenance` 字段说明。
✅ **第八轮（HEAD `31b6b5a52`）已消，不用再做了**：不是靠 (b) 抓包，而是 (a) 的落地版——`UIMessage.wireModelName` 由 `StreamChunkHandler` 落盘（`f1bf516e` / `0c239788`），真实网关重跑后正式证据的 `wire_model_name_provenance_counts` = `{wire_response_model:3, fallback:0}` ⇒ **硬理由①对真实网关路径已消**。剩余缺口以「C1 真机证据采集第八轮」⑨ 的逐行表为准。

**B3. QR 往返的第二台设备**：另一台 Android 扫码导入，逐字段 diff
（`role_id` / `round_id` / `turn_kind` / 群配置 / 角色卡）+ 两端哈希，并**单独验证不含
密钥 / 记忆 / 授权 token**（`FORBIDDEN_EXPORT_KEYS` 已在 JVM 层钉过，真机那一份要再钉一次）。

**B4. 若要换真实 provider 做稳定性验证**：⚠️ **不要把 apiKey 写进任何文档、测试文件或
提交**——内置那条公共网关的密钥已预填在源码里（位置见「C1 真机证据采集第三轮」），
测试按 id 从 `DEFAULT_PROVIDERS` 取定义即可。真要用自己的 key，走
`adb shell` 写进设备上的 `SettingsRepository`，**别落到仓库里**。

### C. 零设备可做（主机上就能做完，不需要任何人接设备）

**C1. 先收拾工作区里那两处「不是本轮改动」的未提交内容**（这是最容易踩的坑：
下一位 `git add -A` 会把它们卷进自己的提交）：

```
git status --short
# M app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tavern/TavernMacroExpander.kt
# ?? app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/C1GroupUiE2EFixtureTest.kt
```

`C1GroupUiE2EFixtureTest.kt`（**461 行 / 3 条 `@Test`**）是一次**被中断的「真机 UI
端到端」任务**留下的半成品。**两条路，别放着**：① 补完并单独提交（它正是 A3 需要的
夹具）；② 删掉。⚠️ **在删之前先读一遍**——它是目前唯一一份「UI 端到端夹具」的成果。
`TavernMacroExpander.kt` 那 1 行改动要**找它的作者确认归属**再决定提交与否。

✅ **C2（本轮已修完，原为待办）**——修「证据登记」表里 C1-05 那一行未转义的 `|`。
⚠️ **早前这里写的是「一个裸 `|`」，是错的：实际是两个。** 根因是「token」列里那句
条件式 `` `limit <= 0 | spent < limit` `` 被一段后插入的
`<br>**2026-10-05 第四个窗口：仍然是零份**…` 笔记**从中间劈开**，于是那个代码段
被拆成三段、中间两处留下裸 `|`。列数是 `13` 而不是 `12`，正是因为**两处**多切。
后果很实际：**任何按列切分的脚本（包括审计状态列的那个）都会在这一行整体错位**——
状态列审计当初要靠「打印 cells 数量」才能发现异常，根就在这里。
✅ 修法：两处都按**本文既有约定**（第 1675 / 1773 / 1816 / 1817 行那批表格单元格，
全文 `\|` 共 7 处、`&#124;` 0 处）转义成 `\|`，**未改动任何正文措辞**。
⚠️ `&#124;` 在这里**不可用**：那个 `|` 落在**代码段内**，而代码段会把 HTML 实体
原样显示成字面量 `&#124;`；GFM 的规则是**代码段内也要用 `\|`**。
复算：`python3 tools/verification/c1_doc_stats.py --only tables`，全表 0 处列数不符
（83 个表格块、证据登记表 12 行 × 11 列、用例矩阵 12 行 × 5 列）。
⚠️ **遗留（本轮未改，属措辞而非结构）**：那段插入的笔记仍然夹在条件式
`` `limit <= 0 \|` `` 与 `\| spent < limit` `` 之间，所以渲染出来仍是一个
**内含 `<br>**…**` 的长代码段**。表格结构已正确，但这一格的显示效果不理想；
彻底修好要移动反引号（会改动正文结构），超出本轮授权，留给下一位决定。

**C3. 一条命令重出全部数字**（把本文三处统计口径固化成脚本，避免每次手抄）：
本轮已实测的三个口径是 ① `:app:testDebugUnitTest` 逐 XML 求和（105 / 808）
② lint 逐 `<issue>` 数 `severity`（app 584W+6H、15 模块 617W+7H）
③ `app/src/androidTest` 逐文件 `grep -c '@Test'` 求和（跟踪口径 61 / 含 untracked 64）。
⚠️ 记的时候**必须同时写清口径**（哪个含 untracked、lint 报告是哪一次生成的无法从 XML 读出）。

**C4. `TavernChatCodec.importGroup` 补 `validate`**（遗留第 19 条，零设备可做）：
当前**无调用方、暂不可达**，但 `af104850` 那条「`role_id` 不能是 `__summary__`」在这个
入口上不生效。补一次 `GroupChat.validate(...)` + 几条 JVM 用例即可（纯函数、
`testImplementation` 只有 junit 也够）。

**C5. `ChatService.finishInterruptedPendingTools` 的产品决策**（本轮新发现、零证据）：
那三处 `providerHandler.generateText` 从 `stopGeneration` 可达且不群聊感知。**先把
「群聊下是跳过还是做成群聊感知」写成一条决策记录**再动手——两者都改运行时行为、
都要真机，**没有决策就不该改**。顺带把「`ChatService` 这个类要不要留」也一起定了
（它当前零活跃构造点，见该节）。
⚠️⚠️ **订正（2026-10-06，第七个窗口，HEAD `72a5e548`）：C5 整个前提已被证伪，别照着做。**
`finishInterruptedPendingTools`（`core/service/ChatService.kt:912-931`）里**零
`generateText`**、零 `currentMessages`、零请求；`stopGeneration` 的三个入口
（UI / `ConversationRoutes.kt:367` / `AndroidManifest.xml:148-151` 注册的
`feature/chat/ChatGenerationForegroundService.kt:75`）**全部指向 `ChatManager`**。
**没有泄漏，所以没有产品决策要定。** 四条证伪依据见「已知遗留与风险」**第 28 条**。
✅ **C5 替换成的真问题**：**删掉 `ChatService` 这份重复实现**（外加那份没进 manifest
的 `core/service/ChatGenerationForegroundService.kt` 死副本）——纯清理、零行为改动、
零设备可做，但**不在本轮授权内**（本轮不碰 Kotlin 源码）。
⚠️ **本轮真正修掉的标题/摘要泄漏在 `ChatManager.generateTitle` /
`compressConversation` 上**（`4ee1ad5c`），遗留见**第 33 条**。

**C6. `GroupChatPage.onEdit` 只放行 USER 的口径显式化**（遗留第 8 条 / B8）：
要放开必须同步改写 `group_runs.committed_role_ids`。**先写决策，不先改代码**。

**C7. B2 抽屉搜索 `folder_id` 口径的 KDoc 显式化**（已定稿为方案 B，只差文档化）：
把「搜索跨文件夹」这条**已认可**的口径写进 `ChatDrawerViewModel` 的 KDoc，
并在 B2 遗留项里记成已知且认可。**那不是口径变更，是文档化工作。**

**C8. 给已改过但零护栏的顺序不变量补护栏**：`GroupConfigSheet` 那两个修复已经补了
（`5d58f923` / `54c196ca`），⚠️ **同类的还有别处**——凡「调用必须排在某个副作用之前」
的形状目前只有 `commitGroupTurn` / `failGroupTurn` 两处有护栏。
⚠️ **写这类护栏必须自带一条「扫描器仍能找到东西」的反空跑断言**（本轮踩过：
尾随 lambda 的大括号在右括号外面，括号配平法会整段漏扫内容体，主断言因此恒绿）。

**C9. 把本轮的诚实边界同步进任何对外材料**：**Miuix 未验 / 62 个调用点未逐个上真机 /
面板业务动作未点 / Coil 修复未在设备上复跑 / 四项代码债运行时零证据**——
这五条**一条都不许在总结里被省略**。

⚠️⚠️ **第七个窗口补进来的五条（同属 C9，正文全部已落在
`docs/beyond-operit-implementation-status.md`，这里补的是**来源指针**，
让人能自己核回本文的哪一节/第几条）**：

| # | 诚实边界 | 正文在哪 | 📍 来源指针（本文） |
|---|---|---|---|
| ① | **真实网关那轮的模型名不是 wire 级抓包**——由 `message.modelId` 的 uuid 反查，能证明「**按角色选型结果这一层**」，**不能**证明「网关实际接受并按此执行」（app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）。✅ **订正（HEAD `74d82476`）**：成因已修（`StreamChunkHandler` 曾丢弃 `chunk.model`），wire 名现可直接拿到；但**真机未跑**，设备上那份证据**仍是反查值**，所以这条边界**一条都不许在总结里被省略** | status「第四个窗口」段的三条硬理由② + 「为什么十例仍然 0/10」第 2 条 | **「已知遗留与风险」第 18 条**；另见「C1 真机证据采集第四轮」与「判定规则」第四条原因② |
| ② | **`importGroup` 运行时零证据**——`app/src/main` 里仍无调用方；契约 `:205`「恢复失败不留下半成品会话」**只在返回值层面**有保证 | status「第七批」三条缺口① | **第 30 条** |
| ③ | **`memory_space_id` 校验在导入场景无解**——调用方建完会话后**必须补一次 `validate(config, newId)`**，但「**是否补做**」无任何证据 | status「第七批」三条缺口② | **第 31 条** |
| ④ | **`importShare` 全拒 vs `importGroup` 结构性拒 + 两项归一化**，是**有意的**口径差异，但属**单方面设计判断**，值得复核 | status「第七批」三条缺口③ | **第 32 条** |
| ⑤ | **`importGroup` 的 residual 兜底当前无法被任何变异杀死**（变异 5 存活，变异 4 第一次也存活），**现在没有测试护着**，子代理明确「不声称证明了它」 | status「第七批」的「变异检验有一项存活」段 | **「`importGroup` 契约 `:205` 缺口修复（零设备，HEAD `db4cdd77`）」那一节里的「⚠️ 变异检验有一项存活，如实登记」四级小节** |

⚠️ **核实结论：五条的正文本来就在 `beyond-operit-implementation-status.md` 里，
一条都不缺**——缺的只是**逐条指回本文哪个位置的指针**（①②③④ 此前只被合并写成
「第 30–32 条」，⑤ 完全没有指针）。所以本轮**只加指针、不补内容**，
上表四列都是本轮实读核实的结果。⚠️ **行号与「第 N 条」编号都可能随本文增长漂移**，
指针写的是**小节名 + 条号**，不是行号。
⚠️ **外加第六条（本轮新增，同属「一条都不许在总结里被省略」）**：
**「标题/摘要按谁的视角取」是判断不是实证**——正文在 status「第八批」①，
本文指针是**第 33 条**。

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
   `ConversationDAO.kt` 的 `getUnfiledConversationsOfAssistantByType` 带
   `AND folder_id = ''`（`@Query` 现在在 `:76`），而 `searchConversationsOfAssistantByType`
   **没有**（`@Query` 现在在 `:108`）。⚠️ 早前记的 `:38` 与 `:66` **已随 C1-S 那一批的
   DAO 改动漂移**（新增 KDoc + 14 条 `ORDER BY` 就地追加），现值见上。
   结果：无搜索词时只显示未归档会话，一搜就把文件夹内的会话也混进来
   （调用点 `core/data/repository/ConversationRepository.kt:92` 与 `:281`）。
   DAO 层已把 type 过滤下沉（`ConversationListQueryPlanTest` 7 条钉住），但
   **folder 口径要不要对齐是产品决策**，本文不擅自改。
   ⚠️ **本轮唯一与之相关的新事实**：C1-S 重放的 G6 组 4 条断言在**主机侧**观测过
   `folder_id` 口径（两条路确实不一致），这**只把现状钉成实测**、**不改变结论**。
   ⚠️⚠️ **「待产品决策」已于 2026-10-05 由用户拍板收口：选方案 B（保持现状）**。
   口径现在**是**「搜索不排除文件夹内会话」这条**已认可的口径**，不再是悬着的待决项。
   **`ConversationDAO.kt` 一行未动**，`ConversationDrawerFolderScopeTest` 那 4 条测试
   钉住的现状**就是最终口径**——所以「改口径会先让测试变红」这条仍然成立，那是
   **预期信号**，不是回归。
   ⚠️ **方案 A 的代价说明必须继续保留**（别因为定稿就把它删掉）：方案 A（搜索也收窄成
   `folder_id = ''`）会让**文件夹里的会话彻底搜不到**，而二级页
   （`FolderDetailPage` / `FolderDetailViewModel`）**目前没有搜索框**（只读
   `getConversationsOfFolderPaging`），收窄后这些会话唯一的查找入口是逐个文件夹翻。
   选 B 的理由就是这个功能回退不能接受。
   ⚠️ **真机 Android SQLite 上的行为仍未验**——这一条**没有被 B 方案定稿改变**。
   ⚠️ 定稿后**遗留的认知不一致**照旧存在：主列表与搜索结果口径不同只存在于开发者
   脑子里，UI 上没有任何提示。按拍板结论，**「把语义写进代码与文档」这一步还没做**
   （`ChatDrawerViewModel` 的 KDoc 没动）——那是文档化工作，不是口径变更。
4. ~~**`GroupRole.modelId`：生成侧已接，历史消息未回填。**~~ **已由 `c7535ca8` 关闭：
   不做数据迁移，改显示侧口径。** 原文记的是「生成侧按角色绑定选模型、显示侧也按
   角色绑定显示，两边同口径（`139a91ba`）」，并把「历史消息未回填」列为遗留。
   那个同口径结论本身是错的：`GroupRole.modelId` 是**用户可改的配置**，今天的绑定
   不等于当初那次调用；`41642ecd` 之前生成的群聊消息 `message.modelId` 记的是
   **助手**绑的模型（那才是当时的真相），按角色绑定显示就是给这批历史消息显示一个
   当时没被调用过的模型。
   数据迁移**不可行**且**无信息增量**：`GroupRole.modelId` 会被用户改，用它回填
   等于把配置猜成历史。正确解法是把两侧拆成两个问题——生成侧 `resolveGroupTurnModelId`
   （`GroupTurnModel.kt:54`，调用点 `ChatManager.kt:726`）答「**现在要用哪个**」，
   显示侧 `resolveMessageModel`（`ChatList.kt:215`）答「**当时用了哪个**」，后者改成
   `message.modelId` 优先、缺失时才回落角色绑定。
   **两侧判据从此故意不一致**，`GroupTurnModelTest` 第 14 条原来那条「两侧结果必须
   相等」的断言已随之重设计（14 条数量不变）。
   仍未验的部分：真机上「老消息显示老模型」需要造一批改过绑定的群聊会话看气泡，
   **零设备证据**。
5. ~~**`ChatManager.kt:681` / `:686` 仍是会话级模型口径。**~~ **已关闭（`ed21db6e`）：
   只有 `senderName` 那一半是真 bug，`useExternalWebSearch` 不是。**
   - **`senderName` 确实是 bug，已修**：原文记的是「`val senderName = if
     (assistant.useAssistantAvatar) {...} else { model.displayName }`（`:681`）和
     `val useExternalWebSearch = shouldUseExternalWebSearch(assistant, model)`（`:686`）
     都在群分支之前按**会话**算好，群聊分支只重算了 `model`、没回填这两个值，后台通知
     标题可能显示错模型」。现已把取值抽成纯函数 `resolveNotificationSenderName`
     （`ChatManager.kt:162`，`internal`，公式逐字来自 `d61eefde`，与气泡头像区
     `ChatMessageAvatar.kt:86-131` 同一口径），并在群聊分支重算完 `assistant` / `model`
     **之后**重新赋值；`senderName` 因此是 `var`，群聊分支里被 `.onCompletion{}` /
     `.onFailure{}` 捕获所以挪不动、只能覆盖。单聊走不到那个分支，取值与 C1 之前逐字
     相同。**位置**用一条源码文本护栏钉住（`ChatManagerNotificationSenderNameTest.
     groupBranch_recomputesSenderName_afterResolvingModel`，已做变异检验：删掉重算即 FAIL）。
   - **`useExternalWebSearch` 不是 bug，不改**：它唯一的消费点是群聊分支**之前**的
     「工具不可用」告警，那里的 `model` 同为会话级，两者自洽。真正决定「本轮要不要下发
     外部搜索工具」的是 `chatToolFactory.createTools(...)` 内部按传入的
     `(assistant, model)` 再算一次（`ChatToolFactory.kt:151`），而那个调用点在群聊分支
     **之后**、拿的是本轮真实的模型——工具下发本身没有错位。要改只能把告警整体挪到群聊
     分支之后，那会改变「群聊但本轮无发言者时是否还弹这条告警」的行为，属未授权变更。
   - **第三个入口的同款副本也一并消掉了（`88ba63c2`）**：`ChatService.kt`（第二个
     `handleMessageComplete`，同样发 `AppEvent.ChatGenerationEnded`）原先**逐字抄了第二份**
     内联公式。它当下无害只因为那个类的 `handleMessageComplete` 里没有任何群聊分支
     （`grep -c groupConfig` = 0）；现已改成调同一个 `resolveNotificationSenderName`
     （同 `:app` 模块，`internal` 跨包可见）。**公式现在全仓只有一份实现。**
   - **仍然未验证（别当已完成）**：真机后台通知标题在群聊下是否正确显示，**零设备证据**；
     `ChatManager` / `ChatService` 两条 `handleMessageComplete` 都是 `private suspend` +
     一堆 Hilt 协作者，JVM 单测构造不出来，所以纯函数只能证明**公式**，证明不了运行时
     的实际渲染。护栏见「C1-P 第二生成入口的通知标题护栏（零设备）」。
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
8. **C1-S 那批（`215296f1..6982869b`）6 条零设备验不到的东西**，逐条照记，细节见
   「C1-S」那节第四小节：
   1. **真机 Android SQLite 的 LIKE / ESCAPE 行为**——本批**全部** LIKE 证据来自
      CPython 内置的 SQLite（3.51.2），是**主机侧**引擎，不是 Android 的 SQLite。
   2. **Room `LimitOffsetPagingSource` 的真实行为**——脚本是**自己拼 `LIMIT`/`OFFSET`
      机械模拟**的；`PagingSource` 的并发失效 / 快照一致性 / 回滚**没验**。
   3. **`~` 在不同 Android / SQLite 版本上的语义差异**，以及 **`ESCAPE` 与大小写折叠
      （`PRAGMA case_sensitive_like`）的交互**。
   4. **Room / KSP 不在编译期拒绝多字符 `ESCAPE`**——变异 M5 带 `ESCAPE '!~'`
      **编译通过**，只在引擎运行期报 `ESCAPE expression must be a single character`。
      「长度必须为 1」**只是运行期检查**。
   5. **脚本靠正则抠 SQL，抠取规则本身有解析上限**；且**「正则抠出来的 SQL == Room 实际
      编译出来的 SQL」这一步没有独立证据**。
   6. **抽屉 UI 端到端没验**（搜索框输入 → 列表刷新）。
9. **⚠️ 本轮改过既有测试的一行断言的期望串——显眼记下来。**
   `ConversationTypeFilterSourceGuardTest.kt:68-73` 的期望串插入了
   `CONVERSATION_LIKE_ESCAPE_SQL` 标识符。`git diff --stat` = 1 insertion /
   1 deletion（单 hunk），**三条断言一条没被削弱或删除**，改完**更严**（一次同时钉住
   ESCAPE 的存在 + 位置 + 谓词相邻）。⚠️ **它抓不到转义字符写错**（M3）：那要改
`ConversationSearchLikePattern.kt`，由 `ConversationSearchLikePatternTest` 负责。
    ⚠️ **也别把它读成「锚点之后第一次改既存测试文件」**——`09b4764b`（重设计
    `GroupTurnModelTest` 第 14 条跨侧断言）、`e4fc4644` / `90d1dbac` / `c940813b`
    都改过；那几次改的是断言的**设计或强度**，这一次改的是**期望字面串**。
    完整来龙去脉（含「第一次尝试写成内联字面量、必然 MISS」那次错改）见「C1-S」那节的
    「⚠️⚠️ 敏感项」。
    ⚠️⚠️ **而「期望串跟着生产代码走」这个形状锚点之后又出现了两次，但不是同一形状**：
    2026-10-05 真机窗口修的两处**都不是期望串跟着生产代码走**——
    `ca717f39` 改的是 **fixture 的状态字段**（helper 把状态写死成 `STATUS_RUNNING`，
    导致 `old-done` / `new-done` 两条名不副实），`7519ee7f` 改的是**期望列表的排序方向**
    （`.sortedBy { it.runToken }` 是升序，`'n'`=110 < `'o'`=111，所以**无论 DAO 行为如何
    都不可能通过**）。**断言本体一个字没动**：仍是 `assertEquals` 精确列表相等，存活集合
    `{old-live, new-done}` 一个不多一个不少，**没改成 `containsAny`**。
    另有 `8151de89` 修的是一条**假断言**（`SupportSQLiteDatabase.query()` 是 SELECT 接口，
    `db.query("INSERT ...")` **只 prepare 返回 Cursor、不执行写操作**，行根本没插进去），
    `43d6ea7e` 修的是硬编码包名（debug `applicationId` 是 `heizige.kk.khatkit.debug`，
    原来那条用例**在 debug 变体上必然失败**），修完之后断言**更严**。
    这四条与「敏感项」那条**不是同一类**：那一条是「生产代码改了所以断言必须跟着改」，
    这四条是「**测试自己写错了**，生产代码是对的」。**定性差别很大，别混着记。**
10. **⚠️ 一个对外可感知的行为变化，登记在这里免得以后当成回归。**
   `GET /api/conversations/paged?query=…`（`ConversationRoutes.kt:85`）的 `query` 里
   带 `%` 或 `_` 时，改动前会返回**多出来的**会话（用户没输的通配符被当模式匹配），
   改动后**只返回标题里真含该字符的**会话。**这是修 bug，不是改契约**——但外部消费者
   如果依赖了旧的宽松行为，会看到结果集变小。
11. **⚠️ 助手页必崩，已修（`0a289af6`）——这是本轮在真机上撞到的第一个真 bug。**
   - **根因**：`app/src/main/java/heizige/kk/khatkit/app/RouteActivity.kt:715`
     `data object Assistant : Screen` **缺 `@Serializable`**。`Screen` 共 **57 个成员**，
     `Assistant` 是**唯一**漏的那个（补注解后 `@Serializable` 计数 56 + 新增 1 = 57）。
     nav3 的 `NavKey` 是**普通 interface**、没标 `@Serializable`，**没有继承兜底** →
     `Navigator.navigate(Screen.Assistant)` → `NavViewModel.kt:62` 的 `snapshotFlow`
     侦测到栈变 → `NavViewModel.kt:78` `persist()` 把**整条栈**
     `encodeToString` → `NavKeySerializer` **找不到 serializer** → 主线程抛
     `SerializationException` **无人接** → **硬崩**。
   - **崩溃日志实证**（真机 logcat）：`kotlinx.serialization.SerializationException:
     Serializer for class 'Assistant' is not found.`，栈帧
     `NavKeySerializer.serialize(NavKeySerializer.android.kt:93)` →
     `NavViewModel.persist(NavViewModel.kt:78)` → `NavViewModel$1$2.emit(NavViewModel.kt:63)`。
   - ⚠️ **这是 pre-existing bug，不是本轮引入的**：`d122d6882`（9-12）引入
     `Screen.Assistant` 时就漏了；`e7c0b2a3`（10-03）新增 `persist()`，把原来的
     「**静默不恢复**」升级成「**每次进助手页必崩**」。
   - **崩了 3 次**：10-05 `10:58:22` pid 4785 / `10:58:30` pid 4945 /
     `11:13:41` pid 25519。**只在真正进助手页时崩**——`am start` 单独启动 launcher
     **不崩**（因为栈里没有 `Screen.Assistant`）。
   - ⚠️ **测试缺口就是它一路逃过 CI 的原因**：
     `app/src/test/java/heizige/kk/khatkit/app/core/ui/nav/NavBackStackSerializationTest.kt`
     **正好测这个序列化**，但只覆盖 **5 个成员**（`Screen.Greeting` / `Chat` / `Setting` /
     `SettingProviderDetail` / `WorkspaceFileEditor`），**`Screen.Assistant` 从未被测过**。
   - **修法**：`0a289af6` 补注解（**main 源码 +1 行**）+ `c3909047` 加**防复发测试**
     `everyScreenMemberHasAWorkingSerializer`——遍历全部 `sealedSubclasses`，断言每个成员
     `serializerOrNull() != null` 且**能往返**。这次是「少一个成员就红」的形状，
     不是「点名测某几个」。
12. **⚠️ 既有缺陷：全量仪器测试跑不完。**
    ✅ **已改记（`ea482935` + `2766afc7`，2026-10-06）：根因已定位、修法已落地，
    ⚠️ 但真机效果未验证。** 原文按惯例保留不覆写：
    `:app:connectedDebugAndroidTest` **全量 56 条**，第 10 条
    `BrowserRuntimeTest.threeTabsCanTypeClickAndReadIndependentLiveSnapshots` 失败后
    **整个 instrumentation 进程 crash**：

    ```
    java.lang.IllegalStateException: The singleton image loader has already been created.
      at coil3.SingletonImageLoader.setSafe(SingletonImageLoader.kt:41)
      at coil3.compose.SingletonImageLoadersKt.setSingletonImageLoaderFactory(singletonImageLoaders.kt:17)
      at heizige.kk.khatkit.app.RouteActivity.onCreate$lambda$0$0(RouteActivity.kt:202)
    ```

    Coil 单例 `ImageLoader` **初始化顺序冲突**，来自
    `b5c5ebcb feat(browser): 浏览器运行时卡片化`，**与 C1 无关**，**稳定复现**
    （重跑 2 次都是同样「3/56 时挂、10/56 后 crash」）。**未修。**
    - **后果**：Gradle 只收集到**前 10 条**结果，**其余 46 条状态未知**——不是失败，是
      **没跑到**。
    - **绕法**：改用**按类过滤 / 合跑**。已用合跑覆盖 **45 条**（56 减去
      `ExampleInstrumentedTest` 1 / `NodeTreeSmokeTest` 4 /
      `DependencyReadOnlyLoadTest` 2 / `BrowserRuntimeTest` 1 中挑的）。
      **未单独跑过的是**：`NodeTreeSmokeTest`(4)、`DependencyReadOnlyLoadTest`(2)、
      `ExampleInstrumentedTest`(1)。
    - ⚠️ 「45/45 全绿」**不等于「全量 56 条全绿」**，别把两个数混着说。
    - ✅⚠️⚠️ **订正与现状（2026-10-06，HEAD `a74c1820`）**：
      - **「来自 `b5c5ebcb`」这个归因是错的**：`git show --name-only b5c5ebcb | grep -i coil`
        **零命中**，它一行 Coil 都没碰；全仓配置 Coil 的地方**只有 `RouteActivity` 一处**。
        真正的因果链是**同一 instrumentation 进程里的执行顺序**：先创建 loader 的是
        **`NodeTreeSmokeTest`**（`createComposeRule()` 不带 Activity 渲染 `AsyncImage`，
        先跑 `get()` 把**内置默认** loader 钉死），随后 `BrowserRuntimeTest`（`b5c5ebcb`
        加进来的类）的 `ActivityScenarioRule(RouteActivity::class.java)` 拉起 Activity，
        `setSafe` 当场抛，挂在 `super.onCreate()` 之后、`setContent` 之前 →
        **整进程死**。**`b5c5ebcb` 只是加了 2 个 androidTest 类、改变了执行顺序，把一个
        一直存在的隐患顶出来了。**
      - **修法（`ea482935`）**：`ImageLoaderFactory` 从 `RouteActivity.onCreate` 的组合搬到
        **`KhatKitApp : Application(), SingletonImageLoader.Factory`**，组件清单原样保留
        （crossfade / `KtorNetworkFetcherFactory` / P 以上 `AnimatedImageDecoder` 否则
        `GifDecoder` / `SvgDecoder`）。**不用条件判断绕过**，因为那会在测试进程里**静默
        跳过自定义配置**（Ktor 网络栈 + GIF/SVG），把崩溃换成更难查的行为不一致。
      - **护栏（`2766afc7`）**：`CoilImageLoaderSourceGuardTest` **6 例**，含一条
        **把因果链本身钉成断言**的（`NodeTreeSmokeTest` 仍用 `createComposeRule` +
        `AsyncImage`、`BrowserRuntimeTest` 仍用 `ActivityScenarioRule`）——没有它，
        将来谁改掉其中一个测试，这条崩溃就会「不再复现」而没人知道因果链断了。
      - ⚠️⚠️ **真机效果未验证（本轮按约束不碰设备）**：磁盘上那份 `tests="10" failures="1"`
        的 XML 是**修复前**的现场。验证只需一条
        `./gradlew --offline :app:connectedDebugAndroidTest`，判据见上面 A1。
13. **⚠️ 顺手修掉的另一处 KDoc / 实现不一致。**
    `SettingAnimPlayPage.kt` 的 KDoc 写「用 `[PathFillEvenOdd]`」，但实现其实用的是
    `Brush.radialGradient`——**KDoc 与实现不一致**。随该文件删除（`18baa930`）一并消失，
    没有单独登记为缺陷。
14. **⚠️⚠️ 「复用末尾助手消息」的同类坑是否只有这一处，仍未排查（`858c11d0` 修的是
    群聊路径那一处）。**
    `GenerationLoop` 里「末尾那条 ASSISTANT 是本次生成自己的」这个假设，判定条件原本只
    看 `role == ASSISTANT`。群聊里上一位角色的发言同样是末尾 ASSISTANT 且已
    `roleId != null`，于是被误当成可复用对象，把本角色的回复**并进上一位那条消息**，
    `stampGroupTurn` 随后找不到 `roleId == null` 的消息，把该角色误判成
    「本轮没有产出内容」写成 `role_failed`。`858c11d0` 给复用条件加了 `roleId == null`，
    并修了非流式分支的**同一个坑**。
    ⚠️ **仍然没排查的部分**：还有哪些路径会把「已提交的助手消息」放到列表末尾再交给
    `GenerationLoop`——**只修了 `GenerationLoop` 这一个入口**。单聊路径不受影响
    （`roleId` 恒为 null，复用条件与改动前逐字相同），但**多分支 / 重新生成 / 记忆注入**
    这些会改写消息列表的场景**一条都没在真机上验过**。
15. **⚠️⚠️ 显式 @ 的「只投递到被提及角色」这条契约，真机上仍然零份。**
    契约 `:201` 写的是「显式 `@角色` 只投递到被提及角色；其他角色不可见」。本轮真机
    夹具里 `@阿尔法` 落在**用户消息**上（`mentionRoleIds` 含 a），而**用户消息对每个
    viewer 都可见**——所以「其他角色不可见」这半句在真机证据里**无法从这条夹具读出来**。
    已采到的三 mode 越权审计（三 mode 全 `violations=[]`）证明的是
    **「没有多出来的可见消息」**，**不等于**「@ 让不该看到的人看不到」——
    方向相反，别互相顶替。**唯一能验证它的是「@ 一条角色发言」或
    「@ 不指代任何角色的那条只有被点名者收到」**，这两种输入本轮都没跑。
     ⚠️ **第十一轮订正（HEAD `a8302e463`，2026-10-06）**：C1-01 现在有了真实网关证据——
     触发 `@角色乙 请只由你发言一次。` 解析出 `mentionRoleIds=[b]`、`plan` 只留 b、**实际只调用 b**、
     b 的回复对 a/c 不可见（viewer 台账与断言见「C1 真机证据采集第十一轮」②）。
     **但本条结论不变**：触发仍是 **USER 消息**，生产口径下 a/c **仍可见那条触发消息**（测试
     `C1LiveModelSequenceTest.kt:1954-1973` 把「a/c 不得见 b 的本轮输出」与「a/c 仍可见触发 USER
     消息」两条都写成断言）；要观测「@ 让不该看到的人看不到」，仍然需要「**@ 一条角色发言**」或
     「**@ 不指代任何角色的输入**」这两种本轮仍未跑的输入。
    ⚠️ **第十六轮订正（文档/契约复核，HEAD `00bcdd4e9`，2026-10-07）：本条的字面读法系误读，C1-01 据此判为可验收（用例矩阵与证据登记表的两个状态格已升 `verified`）。** 契约 `:201` 属「路由与失败语义」，原文「显式 `@角色` 只投递到被提及角色」约束的是**投递（哪些角色被调用/参与本轮）**，不是「那条 @ 消息对其他 viewer 是否可见」；可见性由上一行 `:200` 单独规定，`buildContext` 只返回「自己发送的、**用户消息**、`mention_role_ids` 含自己的、轮次摘要」——**USER 消息对每个 viewer 放行是契约明文行为**。因此「@ 落在 USER 消息上、A/C 仍可见该条」不是缺口；`@` 落在角色发言上才会触发 `:200` 的 mention 放行分支，但那与本条要求无关。详见 C1-01 两格第十六轮订正。
    ⚠️ 同理 **C1-03 的「议长前看不到未完成输出」也只在夹具层成立**：真实 HTTP 只跑了
    pipeline，**议长汇总轮的真实 prompt 组装零份**。
16. **⚠️⚠️ 真实网关那批的证据没有落盘产物（`6ba95422`，本轮新增）。**
    `C1LiveModelSequenceTest.realProviderRoundRecordsGenuineTokenUsage` 在真机上
    **成功产出**过一份完整数据（真实 usage 6803+159 / 6667+68 / 6880+102，
    Σ = 20679 与落库 `group_runs.spent_tokens` 精确相等，真实序列
    `deepseek-v4-flash → glm-5.2 → deepseek-v4-flash`），**但随后进程被设备 OEM 回收
    策略杀掉**（见第 17 条），`connectedAndroidTest` 又按预期**卸载 app、清空外置目录**
    → **`c1-real-raw-dump.json` 等落盘文件没拉回来**。**仓库里没有任何可复算的产物**，
    数字全部来自同一次成功生成的**内存快照**。
    ⚠️ 叠加第 18 条（模型名非 wire 抓包），这一批**不足以把任何用例改成通过**。
17. **⚠️⚠️ 设备 OEM 回收策略会杀 instrumentation 进程（本轮新踩，未解决）。**
    OnePlus `OsenseKillAction` / `NirvanaLowFree`（`appcareThreshold=79`）在 app 进程
    存活约 **34-44 秒**时杀 `heizige.kk.khatkit.debug`（app 占 313MB，设备当时
    `MemAvailable` 3.4GB，被用户自己的游戏 1.7GB + 抖音 1.4GB 占满）。
    ⚠️ **不是 Linux OOM killer，既有 mock 用例同样被杀**，所以与本轮改动无关；
    `:app:connectedDebugAndroidTest` 全量 `Process crashed`、**0 个测试启动**。
    按「OOM 立即停止重试」共试 **11 次**后停止。
    **这是设备环境的限制，不是代码缺陷**——要复跑必须先清掉设备上那些应用，
    或换设备/换用户。⚠️ 顺带仍未解决的是 `BrowserRuntimeTest` 的 Coil 单例崩溃（第 12 条）。
18. ✅ **~~`actual_model_call_sequence` 里的模型名不是 wire 级抓包（结构性，改不掉）。~~
    这条本轮已作废——「结构性、改不掉」是错的，成因已定位并修复；但硬理由本身仍未消，
    见下面接的续条。**
    ⚠️ **原记载（保留以便追溯）**：app **不保存响应里的 `model` 字段**，所以序列中的模型名
    是由 `message.modelId` 的 uuid（`5a86b2d6…` / `8b6bf21c…`）经 provider 模型表
    **反查**出来的；JSON 证据里已用 **`wire_model_name_provenance`** 字段显式标明这一点。
    ⚠️ **原记载里那句「要变成真抓包需要在 `OpenAIProvider` 侧记录响应 `model`（属于加日志，
    不属于本轮范围）」也作废**——**不需要在 provider 侧加日志**：
    `ChatCompletionsStreamDecoder` 的 `finish(reason, responseId, model)` **一直把 wire 上
    的模型名发出来了**，缺陷只在上游 `StreamChunkHandler` 收到 `Finish` 时**把它丢掉了**。
18b. **⚠️ 硬理由①续条：能力已具备，缺的是真机复核（HEAD `74d82476`，零设备）。**
    - **已修**：`UIMessage.wireModelName`（`f1bf516e`）+ `StreamChunkHandler` 的 `Finish`
      分支落盘与非流式对称（`0c239788`）。`@Serializable`，随对话 JSON 落库，**无需 Room 迁移**。
    - **零设备证据**：`WireModelNameProvenanceTest` **9 例 / 27 处断言 / 退出码 0**
      （逐字透传 / 反查隔离 / 无 `model` 帧⇒`null` / 非流式对称 / 序列化往返 / 旧 JSON 兼容）；
      真实网关探测 `c1_wire_model_probe.py` 拿到 `deepseek-v4-flash` 与 `glm-5.2` 两个
      wire 名，**均 HTTP 200**。详见「为什么十例仍然 0/10」第 2 条。
    - **测试口径**：`C1LiveModelSequenceTest` 已改 **wire 优先 + uuid 反查回退**，
      并有「**不允许两者皆空**」的防退化断言；证据 JSON 逐条标 `wire_model_name_provenance`。
    - ⚠️⚠️ **为什么这条还不能划掉**（三条，照记）：① **仪器测试一行都没跑过**，设备离线，
      新增断言 / 新 JSON 字段 / wire 优先判定逻辑**全部只有编译验证**；
      ② **设备上那份 `c1-live-evidence-real-provider.json` 里记的仍是 UUID 反查值**，
      要等真机重跑才变——**这里没有任何真机数字**；
      ③ ⚠️ **诚实要点**：wire 名与请求名一致 ⇒ **原先反查出的名字恰好是对的**，
      现在只是**可直接从 wire 拿到**。**这是「取得途径改变」，不是「反查错了被纠正」**，
      因此**不构成对既有证据的追溯性升级**。
    - ⚠️ **另两条取证局限**：`wireModelName` **不在任何 UI 上显示**（没做，也不打算做）；
      **旧 Room 库既有消息树的反序列化没对真实数据库跑过**（只靠 nullable+默认值 +
      JVM 序列化往返保证）。
    - **下一位要做的就一件事**：真机重跑 `realProviderRoundRecordsGenuineTokenUsage`，
      把落盘 JSON 拉回来，确认 `wire_model_name_provenance` 变成
      `wire_response_model` 而不是 `uuid_reverse_lookup_fallback`。
    - ✅ **第七批（HEAD `86e88970d`）部分推进，但这件事本身没做完**：
      `C1LiveModelSequenceTest` **前 4 条已在真机全绿**（四条退出码 `0` / 全 `OK (1 test)`），
      且 t1/t3 两份 pulled JSON 里 `wire_model_name` 是 `mock-model-a/b/c` 这种
      **服务端自报串**（同行 `model_id` 是本地 UUID）⇒ **wire 优先逻辑在真机上确实生效**。
      ⚠️ **但那 4 条跑的是 mock provider，不是真实网关** ⇒ 上面这三条子理由里
      ①② 只在 **mock 下**消掉了，**真实网关那次仍然一行没重跑**、
      `c1-live-evidence-real-provider.json` 仍然是反查值 ⇒ **整条硬理由①仍不消**。
      ⚠️ **另有一处订正**：上面说「证据 JSON 逐条标 `wire_model_name_provenance`」
      **只对真实网关那份成立**——mock 三份 JSON 里 grep `provenance` = **0**，
      该字段只有真实网关写出器才落（`:1933`），mock 写出器只落 `wire_model_name`。
      详见「C1 真机证据采集第七轮」。
19. **⚠️ `TavernChatCodec.importGroup` 不做 `validate`（`af104850` 之后仍是已知遗留）。**
    `core/data/ai/tavern/TavernChatCodec.kt:257` 调 `decodeConfigObject`（`:268`）
    但**完全不 validate**，所以 `af104850` 在 `GroupChat.validate` 收口的那条
    「`role_id` 不能是 `__summary__`」**在这个入口上不生效**。
    ⚠️ **当前 main 源码里没有调用方，所以暂不可达**——但这是一个「已堵住但入口没堵严」
    的形状，将来谁接上调用方就会重新打开。修法很轻：在 `importGroup` 里补一次
    `GroupChat.validate(...)`。
20. **⚠️ `ChatMessage.kt:134-140` 的 KDoc 关于 fork 的说法不成立（本轮发现，未修）。**
    那段 KDoc 说「重新生成 / 删除 / 创建分支这三个回调都不是群聊感知的，会让群运行日志
    的账面和实际轮次错位」。**按 `createForkConversation`（`ChatManager.kt:172-188`）
    实读，fork 既不复制 `group_config` 也不复制 `type`，产物是一条单聊、对源会话只读，
    破坏不了账目**——`87f9c209` 的拒绝文案也是据实写成「静默换了会话类型」而不是
    「账目错位」。**所以这条 KDoc 的 fork 那一半是错的，尚未修。**
    ⚠️ 同一条 KDoc 里 regenerate / delete 那两半**仍然成立**，别因为 fork 这一半错
    就把整段删掉。
21. **⚠️ P2 的 `coerceAtLeast(0)` 结论依赖「没有新的 `visibleMessages` 调用方」这个前提。**
    探针实测 `userlessWindows=36 crossRoundLeaks=0`、`emptyPrefixes=6
    userlessNonEmptyPrefixes=0`，4 个调用方逐个枚举过，结论是**不可达、未改**
    （理由写进 `GroupChat.kt:553-555` 的 KDoc）。⚠️ **这是「当前不可达」不是「结构上
    不可能」**——将来新增调用方若能构造出「无 USER 消息且列表非空」的输入，
    `index >= roundStart` 会重新变成 fail-open。**新增调用方时必须重跑那两个探针。**
22. **⚠️⚠️ `onSuccess` 里 `null -> groupRunsInFlight.remove(conversationId)` 的连带损伤
    （`2fdee352` 附带发现；✅ `06cf6181` 已修，⚠️ 运行时仍未验证）。**
    `ChatManager.kt:1007` 那行 `null -> groupRunsInFlight.remove(conversationId)`，
    在残留旧 job 走到这里时**镜像里装的是新轮的令牌**，这一 remove 让
    **新轮再也取消不掉**（`cancelActiveGroupRun` 第一行就 `remove` 不到东西、直接
    return）、续跑拿不到 `expectedRunToken`、**整轮卡死**。
    ⚠️ **上一批刻意不改，理由两条**：① 改它会**动正常路径**——`produced == null` 时
    `failGroupTurn` 可能提前 `return`（配置没了 / plan 算不出 / 守卫拒收），
    **那一次 remove 是镜像唯一的兜底清账**；② 本轮 ②③ 的修法是**让拒绝走
    `Advance.Halted` 而不是 `null`**，从而**绕开**这一行——⚠️ **这不是修好了这行，
    是让这条路不再走到它**。⚠️ **将来任何新增的「返回 null」分支都会重新踩到它**，
    所以新分支一律走 `Halted`。
    ✅ **第六个窗口已修（`06cf6181`）**：`null ->` 分支改成
    `clearGroupRunMirrorIfMine(conversationId, groupRunToken)`，内部用
    `ConcurrentHashMap.remove(key, value)` **两参重载**（原子、值相等才删）。
    **既有兜底为什么没被破坏**：核实 `commitGroupTurn` 返回 `null` **只有一种形状**
    （`produced == null`），那条路里 `failGroupTurn` 在归属成立时自己已清镜像；
    剩下的兜底只为「提前 return」而存在——而那两种提前 return 里镜像装的**正是本轮
    令牌**，按令牌比对照清不误；令牌为 null（理论不可达）时**宁可不删**。
    ⚠️ **护栏是文本级的**（`GroupStaleJobCommitSourceGuardTest` **+2 例**：禁「按 key
    无条件删」的旧形状 + 钉 `null ->` 分支走按令牌清，变异检验两次真红）。
23. **⚠️ `abandonDanglingGroupRuns` 的 `limit = 8` 与契约有张力（✅ `06cf6181` 已修，
    ⚠️ 运行时仍未验证）。**
    `ChatManager.kt:2098` 取「挂着 `RUNNING` 的行」时写了 `limit = 8`
    （同样的常量也出现在 `:853` 的另一处调用）。契约说「**任何群聊轮次都不会永久
    悬挂**」，但**超过 8 条**时**第 9 条起不会被判死**，仍是**永久 `RUNNING` 占位**。
    ⚠️ **未修**；⚠️ **也未核实单会话能不能真的堆到 9 条并发 `RUNNING`**——
    `claimRound` 按 `(conversation_id, round_id)` 复合主键抢占、`RUNNING` 行又是每轮
    一条，理论上「连续 9 次发消息、每次都有一条轮次没落终态」能堆出来，但那要求
    **9 次判死路径全部失效**，本轮**零证据**。所以这条按「**未修 + 后果未核实**」
    登记，⚠️ **不要写成「已确认会永久悬挂」**。
    ✅ **第六个窗口已修（`06cf6181`，前半）**：改成**分批循环读到清空**——
    `ABANDON_BATCH_SIZE = 8` 只作**分页**、`ABANDON_MAX_BATCHES = 64` 只作**收敛上限**；
    每行判死前 `findByRound` 重读 + `isTerminal` 跳过（`terminal()` 不判终态，盲目写会
    把别人的终态抹成 `CANCELLED`）。**为什么留硬上限**：同会话在本函数执行期间又抢占
    成功一轮时行数不单调减少，无界循环会自旋，**那比留占位更糟**。
    ⚠️ **顺带订正原注一处误导**：早前写「同样的常量也出现在 `:853`」——**实际 `:853`
    （`fa36c65c` 之后漂到 `:872`）是 `searchMemories(limit = 8)` 的记忆条数上限，
    语义无关**，只是碰巧同值。
    ⚠️ **仍未验证**：① 分批循环的真机行为（零设备，要造 >8 条并发 `RUNNING` 才能验，
    见操作清单 A8）；② 单会话能否真的堆到 9 条并发 `RUNNING` 这一点**本轮仍未核实**——
    修法是按契约的「清空」来做的，**不代表那个前提已被证实**。
24. **⚠️⚠️ ②③ 修的那两条残留旧 job 路径，从未在真机上复现过（零设备）。**
    触发前提「`abandonDanglingGroupRuns` 不取消任何 job」是**读代码 + 核实
    `ConversationSession.cancelJobs()` 只被 `stopGeneration` / `cleanup()` 调用**
    得到的，**不是真机观测**。⚠️ 路径 A（超时 / 角色失败 → `failGroupTurn`）有
    **JVM 单测层面**的实测支撑（`resuming a cancelled or failed round still admits…`
    跑通 `cancelRound → claimRound → checkCommitAdmission → advance` 整条链），
    ⚠️ **路径 B（用户取消 → 残留 job 进 `onSuccess`）连这个级别都没有**——
    `stopGeneration` 先 `cancelJobs()` + `join()`，被取消的协程走 `onFailure` 的
    C 分支 rethrow，`onSuccess` 不执行。
    ⚠️ **真机复现需要的条件**：同一会话里一条长生成在飞、期间发新消息把它判死、
    然后等旧 job 跑完（或者反过来先超时）——这要在真机上**人工编排时序**才造得出来，
    **不是「跑一遍测试」能覆盖的**。本轮零设备，所以这一整类缺陷**只有代码层证据**。
25. **⚠️⚠️ 遗留第 20 条那条 KDoc 的 fork 那一半已订正措辞，但整段仍未处理。**
    `361c7cf6` 改了那一段的**措辞**（禁因从「账目错位」改成「静默换会话类型」，
    与 `87f9c209` 的拒绝文案对齐），⚠️ **但 regenerate / delete 那两半仍然成立、
    也仍然没堵**（`GroupChatPage` 的 `onEdit` 门禁与 `GroupMessageActions` 的四处
    `if (!groupChat)` 是现状，不是修复）。
    ⚠️ 顺带说明：**`361c7cf6` 不属于 C1**（`git show --stat` = 1 file /
    14 insertions / 3 deletions，只改 `ChatMessage.kt` 的 KDoc、**零测试改动**），
    它被记在这里是因为第 20 条本身是 C1 的遗留清单之一。
    ✅ **第六个窗口补了防回退护栏（`bed09118`，`GroupForkDisableReasonSourceGuardTest`
    2 例，KDoc 一个字没改）**：① fork 段落不许把账目错位算到 fork 头上 ② **姊妹例**
    要求重新生成 / 删除那两半必须保留它们自己的账目错位理由（防止有人把整段一刀切
    删掉）。⚠️ **护栏钉的是禁因的归属、不是逐字文案**——措辞可以改，归属不许回退。
    实测变异 M5 把假的账目错位写回 KDoc 时**变红**，姊妹例保持绿。
26. **⚠️⚠️ 群配置面板两个真机必崩已修（`33eb801e` / `71d1439e`），但四条边界没消。**
    **① Miuix 风格完全没验**——两个崩溃都只在 `KedgeStyle.MD3Exp` 触发（这是**读代码**
    得出的：Miuix 分支不走 `requireNotNull`，且 `PrimaryBottomSheet` 的 KDoc 写明
    Kedge 版「靠内容自身滚动，忽略该参数」），**Miuix 下这个面板是不是也崩过、行为是否
    不同，零证据**；② 其余 **62 个 `PrimaryBottomSheet` 调用点**（`app/src/main` 里 `grep -rn "PrimaryBottomSheet("` = **66** 处命中，减去定义文件 `core/ui/components/ui/PrimaryBottomSheet.kt` 里那 **4** 处（1 个声明 + 3 个委派调用）= **62**；⚠️ **把 `src/test` / `src/androidTest` 也算进去是 68**。⚠️ **「64」这个数在两种口径下都复现不出来，本轮按实测的 62 登记**）靠源码扫描 + 既有单测，
    **没逐个上真机**（只有群配置面板这一个真的开过）；③ **没有在旧 APK 上先复现崩溃再
    对比修复**——原始栈是用户实测给的，所以「修完不崩」与「修的就是这条栈」靠的是
    **栈帧行号对得上**，不是受控对比；④ 面板里的**保存 / 生成二维码 / 扫码导入三个业务
    动作一个都没点过**，只验了渲染与滚动。完整登记见「群配置面板两个真机必崩已修并真机
    验证」那一节。
27. **⚠️⚠️ 工作区里有两处「不是 C1 文档改动」的未提交内容——下一个人务必先看清楚。**
    `git status --short` 当前是：
    - `M app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tavern/TavernMacroExpander.kt`
      （**1 insertion / 1 deletion**，内容照记：`expandTavernMacros` 里那条宏正则的结尾从
      `}}` 改成 `\}` + `}` 的转义写法 `Regex("…\\s*\\}\\}")`——**正则语义等价、只是把
      「两个闭括号」写成显式转义**。⚠️ **谁改的、为什么，本轮不知道**，所以只登记不处置）
    - `?? app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/C1GroupUiE2EFixtureTest.kt`
      （**461 行 / 3 条 `@Test`**，一次**被中断的「真机 UI 端到端」任务**留下的半成品）
    ⚠️ **本轮既没有 add 也没有修改它们**（两处的 mtime 都早于本轮），⚠️ **下一位
    `git add -A` 会把两处一起卷进自己的提交**。⚠️ **`C1GroupUiE2EFixtureTest.kt` 让
    `app/src/androidTest` 的 `@Test` 数从 61 变成 64**——引用「61」时必须写清是
    **git 跟踪口径**。处置见操作清单 C1。
28. ~~**⚠️ `ChatService.finishInterruptedPendingTools` 里三处 `generateText` 同样不群聊感知，
    而且从 `stopGeneration` 可达（本轮新发现，未动手）。**
    `fa36c65c` 的 fail-loud 闸门只在 `handleMessageComplete`——它是 `ChatService` 里
    `generationLoop.generateText` 的唯一调用点，**但 `finishInterruptedPendingTools`
    里的三处 `providerHandler.generateText` 是另一条旁路**（取消后续跑工具），
    **`stopGeneration` 能走到它**。⚠️ **修它要先定产品行为**：群聊下**跳过**（工具调用
    在群聊轮次里没有正确语义）vs **做成群聊感知**（等于要在这个类里重建一套）。**两者
    都改运行时行为、都需要真机**，所以只登记不动（见操作清单 C5）。
    ⚠️ 别把它读成「`fa36c65c` 的闸门没覆盖全」——**那个闸门本来就不是为这条路径设计的**，
    类 KDoc 里也写了「另三处 `providerHandler.generateText` 属于
    `finishInterruptedPendingTools`，那是取消后续跑工具的旁路」。~~

    **⚠️⚠️ 已证伪（2026-10-06，第七个窗口，HEAD `72a5e548`）：这条整条是错的，它指认的
    群聊泄漏根本不存在。原文逐字保留在上面不删——它是「错误如何发生」的证据。**

    ⚠️ **证伪依据（四条，逐条都是本轮实读核实，行号按 `72a5e548` 记）**：
    - **① `stopGeneration` 到不了 `ChatService`——用户按停止走的是 `ChatManager`。**
      **命名陷阱在 `feature/chat/ChatViewModel.kt:63`：属性名叫 `chatService`，
      类型却是 `ChatManager`。** 另两个「停止」入口也都指向 `ChatManager`：
      **① HTTP** `POST /api/conversations/{id}/stop` → `chatService.stopGeneration(uuid)`
      在 `core/network/routes/ConversationRoutes.kt:367`（该路由的 `chatService` 形参在
      **`:48`** 就声明成 `ChatManager`）；**② FGS 超时** → `AndroidManifest.xml:148-151`
      注册的是 **`.feature.chat.ChatGenerationForegroundService`**，它的
      `lateinit var chatService: ChatManager` 在
      `feature/chat/ChatGenerationForegroundService.kt:75`。
      ⚠️ 早前把 `core/service/ChatGenerationForegroundService.kt:71`
      （那份的 `chatService` **确实是** `ChatService`）当成注册项，是同一个命名陷阱的第二次发作。
    - **② `finishInterruptedPendingTools` 里零 `generateText`。** 该函数在
      `core/service/ChatService.kt:912-931`，**只做一件事**：给最后一条消息里所有未执行的
      tool part 塞一段 `{"status":"cancelled",…}`（`cancelToolByUser`，`:902-911`），
      然后 `saveConversation`。**不发起任何请求、不读 `currentMessages`、不碰记忆空间。**
      全文件 `generateText` 只有 **4 处**：`:744`（`generationLoop`，在
      `handleMessageComplete` 里）与 `:952` / `:1004` / `:1089` 三处 `providerHandler`
      （分别在 `generateTitle` `:933` / `generateSuggestion` `:985` /
      `compressConversation` `:1038` 三个函数体里）——**没有一处在 `finishInterruptedPendingTools` 里**。
    - **③ 错误源头是仓库注释，不是实现。** `ChatService.kt:653` 那段注释早前写的是
      「另三处 `providerHandler.generateText` 属于 `finishInterruptedPendingTools`，
      那是取消后续跑工具的旁路」——**这句注释本身写错了**（真实归属是上面 ② 那三个函数）。
      **错误从注释扩散进了本文这一条验收登记**，所以「先读注释再写验收」的顺序在这里
      制造了一条不存在的产品缺陷。已由 **`8111d178`** 改掉（只改注释，**17 insertions /
      3 deletions，一个字实现都没动**）。
    - **④ `ChatService` 只有 1 个注入点且不可达。** `feature/history/HistoryVM.kt:28`
      （它只调 `toggleConversationPinned`），而 `HistoryPage` 在 `app/src` 里**零路由引用**。
      ⚠️ **附带发现第 4 份重复类**：`core/service/ChatGenerationForegroundService.kt`
      （**175 行**）**没有进 `AndroidManifest.xml`**（`:148-151` 注册的是 `feature/chat`
      那份，179 行），它唯一的引用者是**同包那个不可达的** `ChatService.kt:298/307`
      （静态 `acquire` / `release`）——**是死副本**。

    ⚠️ **所以「C5 那个产品决策」也不存在**：既然没有泄漏，就没有「群聊下跳过 vs 做成群聊
    感知」要定。⚠️ **真正该做的是删掉这份重复实现**（`ChatService` + 那份死 FGS），
    那是**独立的清理项，不在本轮授权内**，已并入本条的遗留尾巴。

    #### ⚠️⚠️⚠️ 订正（2026-10-06，本轮，纯文档）：上面「删掉是独立清理项」的前提**已失效**

    ⚠️ **「纯清理、零行为改动」这个前提被本轮实测推翻**。三处订正（详见
    `docs/beyond-operit-implementation-status.md` 第 393 行起「订正」小节）：

    1. ⚠️ **`ChatService.kt` 不是死代码，是「活接线 / 运行时不可达」** ——
       `@Inject constructor`（`:177`）⇒ Hilt JIT 绑定**存在**，
       `HistoryVM.kt:28` 注入、`:55` 真调 `toggleConversationPinned`。
       ⚠️ **⇒ 直接删会炸编译**，必须先拆三处依赖（`forkConversationTitle` 被活的
       `ChatManagerTest.kt:18` 跨包 import + 5 条断言；两个护栏测试
       `File(...).readText()` 硬编码了它的绝对路径；`HistoryVM`/`HistoryPage` 孤儿）。
       ⚠️ 顺带订正一处更早的错误说法：早前把 `AppHiltModule.kt:51` 当成 `ChatService`
       的**绑定**（真相比「活代码」还靠前一步），⚠️ **那是一条未使用的 import** ——
       真绑定 0 处，`@Provides` 方法是 `:191 fun provideChatManager` 返回 `ChatManager`。
       完整拆解顺序见 `docs/architecture-map.md` §9.2.1。
    2. ⚠️ **根因是 vendor merge 复活，不是「有人忘了删」** ——
       `f7463f814`（2026-09-25）主动搬走，`8cf9bec2d`（2026-10-01）把 5 个文件
       当新文件搬回（`git diff --stat 8cf9bec2d^1 8cf9bec2d -- …/core/service/`
       ⇒ **`5 files changed, 1994 insertions(+)`，0 删除**）。
       ⚠️ **⇒ 下一个人会再删一次、再被复活一次**，除非按
       `docs/architecture-map.md` §9.3 的对策每次同步后复查。
    3. ⚠️🚩🚩🚩 **「删 `ChatService.kt`」会永久删掉一个功能实现** ——
       `ChatService.kt:1026` 是**全 app 唯一填充非空 `chatSuggestions` 的地方**
       （活管线 `ChatManager.kt:758/1324` 只会 `= emptyList()`）；无 UI 消费方、
       无开关入口。⇒ **这已经是产品决策，不是清理决策**。
       **🚩 待主人拍板，agent 不得自行决定** ——
       见 `docs/beyond-operit-implementation-status.md` 的**待决策项 D1**
       （选项 (a) 认定废弃 / 选项 (b) port 进 `ChatManager` 并补 UI）。
       ⚠️ **D1 没拍板前不要动 `core/service/` 任何一个文件。**

    #### ✅ 处置结果（2026-10-06，第八个窗口）：**主人已拍板，D1 走选项 (a)，清理完毕**

    🚩 **主人原话：「不要那个消息建议了」** ⇒ 认定「聊天建议（追问推荐）」功能**已废弃**，
    连同它一起清理。上面第 3 条那个「🚩 待主人拍板，agent 不得自行决定」**已解除**。
    原文全部保留在上（仅追加本块）。

    **实际执行（每个逻辑步骤一个 commit）**：

    | commit | 动作 |
    |---|---|
    | `2f94b72d8` | 把 `forkConversationTitle` + `forkTitleSuffixRegex` 搬到 `feature/chat/ChatManager.kt`（紧挨同族的 `createForkConversation`），删掉 `ChatManagerTest.kt:18` 那行**跨包 import**（同包免 import）。**5 条断言一字未改，仍绿**（`ChatManagerTest` XML `tests=8 failures=0 errors=0`）。⚠️ `core/service/ChatService.kt` 自己的 `createForkConversation` 原本也调它，已在死文件内联成与 feature 版逐字相同的写法 —— 不改活代码行为 |
    | `41e29ab20` | `HistoryVM.kt:28` 注入类型 `ChatService` → **`ChatManager`**（`ChatManager.kt:1358` 有**逐字同签名**的 `suspend fun toggleConversationPinned(conversationId: Uuid)`），`:55` 调用照旧；**`HistoryPage` 与 `HistoryVM` 都保留**（删页面是产品决定）。删 `AppHiltModule.kt:51` 那行**复核后确认仍未使用**的 import |
    | `86da59fa0` | `git rm` 5 个文件（`ChatService.kt` / `ChatGenerationForegroundService.kt` / `MessageQueue.kt` / `ConversationSessionManager.kt` / `ConversationSession.kt`，共 **2083 行删除**），空目录一并删 |
    | `0040463b1` | 删 `enableSuggestion` flag（4 处：`ENABLE_SUGGESTION` key 声明 + getter/setter 对 + `Settings` 默认值）+ 7 个 locale 的 `setting_model_page_enable_suggestion` 字符串 |
    | `f957af14f` | 删两个护栏测试里依赖已删文件的方法 |

    ⚠️⚠️ **本轮新增的诚实后果**：`chatSuggestions` 现在**恒为空**。
    `ChatService.kt:1026` 的 `chatSuggestions = suggestions.take(10)` 是全 app 唯一的非空写入，
    随文件删除；活管线 `ChatManager.kt:770/1336` **全都只 `= emptyList()`**。
    查无 UI 消费方、无开关入口，所以**今天就不可见**、删了也不改变任何用户可见行为。
    ⚠️ **`Conversation.chatSuggestions` 字段本身保留** —— 它是持久化字段
    （`ConversationEntity.kt:22` / `ConversationRepository.kt:413,437`），
    删它会改数据形状、影响老库反序列化。

    ⚠️ **护栏侧诚实结论**：`ChatManager` 侧**没有** fail-loud 闸门的等价物 ——
    因为**它不需要**：`ChatManager.kt:787` 原生 `takeGroupTurn`，「拒绝群聊会话」才是错的契约。
    「群聊经 `ChatService` 生成在结构上不可能」现在由**文件根本不存在**保证，比任何文本护栏都强。
    ⚠️ sender name 侧**有**等价护栏，证据见台账那两行的订正。
    ⚠️ **本任务零设备、零 androidTest 运行**：状态列 **20 个 `unverified` 一个都没动**。

    ⚠️ 上面的「零行为改动」结论**只对那 4 个不受 D1 影响的文件成立**
    （`ChatGenerationForegroundService.kt` / `MessageQueue.kt` /
    `ConversationSessionManager.kt` / `ConversationSession.kt`，逐个判定见
    `docs/architecture-map.md` §9.2），**不包含 `ChatService.kt`**。

    ⚠️⚠️ **教训（别再犯第二次）**：`ChatService` 这条线上**真正**的群聊泄漏**存在**，
    但**不在 `ChatService` 里**——在 `feature/chat/ChatManager.kt` 的
    `generateTitle` / `compressConversation` 上（它们**直接送未过滤的
    `conversation.currentMessages` 给 TITLE/SUMMARY 模型**）。
    已由 `4ee1ad5c` 修掉，登记在「已知遗留与风险」**第 33 条**。
29. **⚠️ 四项代码债的运行时行为全是零证据（本轮唯一的 gradle 证据只到「编译 + 单测」）。**
    `require` 是不是真抛、分批循环在真机上的实际行为、`remove(key, value)` 的真实并发
    效果、以及「`produced == null` 时 `failGroupTurn` 提前 return」的具体触发条件，
    **一条都没在真机上观测过**。⚠️ **三条 gradle 命令（编译 / 单测绿）不构成任何契约验收
    证据**，护栏也全是**源码文本级**的。运行时验证步骤见操作清单 A8.
30. **⚠️⚠️ `importGroup` 这批（`8622bf19` / `aee20f85` / `88777898` / `db4cdd77`）
    运行时证据为零——因为它**在 `app/src/main` 里仍然没有调用方**。**
    契约 `:205` 的「恢复失败不留下半成品会话」现在只在**返回值层面**有了保证
    （坏配置 `config = null`），但 ⚠️ **没有任何证据表明「建会话」那段代码读了
    `importReport`**——因为那段代码**不存在**。本批是**为将来接线做准备**，
    不是「已修好一个正在用的功能」。核实依据：`importGroup` 在 `app/src/main` 里的
    全部出现都是 KDoc 与注释（`TavernChatCodec.kt`、`GroupExportCard.kt`），
    真实调用点只有 `C1DeviceEvidenceTest.kt` 与两个 JVM 测试类。
31. **⚠️⚠️ 导入场景下 `memory_space_id` 那条校验无解，`importGroup` 放过它是必然的；
    「调用方补没补做」未验证。**
    导入进来的配置里 `memory_space_id` 要么是 `null`、要么是**别的会话的键**，而新会话
    的 id 在导入时**还不知道**。所以这条校验在 `importGroup` 里**不可能通过**，放过它
    是对的。⚠️ **代价是：调用方建完会话后必须补跑一次 `validate(config, newId)`**，
    否则会造出**记忆空间键错位**的群。⚠️ **「调用方是否补做」目前无任何证据**
    ——因为没有调用方。子代理**没有**在导入时改写它（在导入时改会破坏
    「`exportGroup → importGroup → exportGroup` 逐字节不变」这条往返不变式，
    且它本来就是建会话**之后**的事）。
32. **⚠️⚠️ `importShare` 与 `importGroup` 的口径是**有意不同**的，但那是单方面设计判断。**
    `importShare`（`GroupChatPage.kt:492` 生产侧在用）**全拒**；`importGroup`
    **结构性拒 + 两项归一化**（`revision` / `tie_policy`）。依据写在
    `screenImportedConfig` 的 KDoc 里，但**只有作者一方的理由**，**值得复核**。
    顺带记一条本轮核实到的**文档与实现不一致**：`TavernChatCodec.kt:539` 的 KDoc 写
    「`config == null` 有**两种**原因」（NoConfig / Rejected），而实现里实际是
    **四种成因**——① 密钥黑名单命中、② 无 `khatkit_group.config` 块（`NoConfig`，
    非错误）、③ 键在但解不出来（`Rejected`「无法解析」）、④ 校验判掉（`Rejected`）。
    ⚠️ 该文件 `:297-299` 的行内注释**已经**把 ②③ 拆开说了，所以这是
    `:539` 那段 KDoc 措辞偏松，**不是实现缺陷**；本轮**未改**（不碰 Kotlin 源码）。
    ✅ **第七个窗口已订正（`d00880fc`，纯注释改动）**：那段 KDoc 现在逐字写成**四种成因**，
    与 `:297-299` 的行内注释对齐。⚠️ **「口径有意不同但属单方面判断」那半句仍未复核**，
    仍是遗留。
33. **⚠️⚠️⚠️ 「标题 / 摘要按**谁的**视角取」是判断不是实证——`4ee1ad5c` 修掉的是**真泄漏**，
    但**口径本身欠产品确认**（`SummaryViewerScope`，零设备，2026-10-06，HEAD `72a5e548`）。**
    - **先说清被修掉的是什么（这条是本轮唯一的真泄漏，已修）**：契约
      `client-changes.md:200` 明写「上下文过滤必须发生在 **prompt 组装层**……
      禁止在 UI 层『隐藏但仍发送』」，而 `feature/chat/ChatManager.kt` 的
      **`generateTitle`**（送 `currentMessages.takeLast(4)`）与
      **`compressConversation`**（送**全量**）**直接把未过滤的
      `conversation.currentMessages` 交给 TITLE / SUMMARY 模型**。
      ⚠️ **比早前判断更严重——不只长按菜单触发**：`ChatManager.kt` 在
      `handleMessageComplete` 的 `onSuccess` **末尾**（**`:1070`**）对**单聊与群聊走同一行**
      自动调 `generateTitle`，所以**群里每跑完一轮，其他角色最近 4 条就自动进 TITLE 模型，
      用户一个菜单都不用点**。（`:1020` 那次是 `groupStep == null` 的**单聊**分支。）
      长按菜单那条同样成立：`ConversationList.kt` 的长按菜单（`onRegenerateTitle` 在
      **`:617-624`**）**不判会话 type**，而 `ChatDrawer.kt:548` 的 `TYPE_GROUP` 筛选项
      保证群会话会进列表。
      ⚠️ `compressConversation` 确认**潜伏**：压缩对话框只在 `ChatPage.kt`
      （`compress` 命中 **6** 次），`GroupChatPage.kt` 里 `compress` 命中 **0** 次——
      **一旦 UI 接线就是真泄漏**，所以不等接线就先堵。
    - **修法（`4ee1ad5c`）**：新增
      `app/src/main/java/heizige/kk/khatkit/app/feature/chat/SummaryViewerScope.kt`
      （**91 行**），**转调已有的 `GroupTurnCoordinator.viewerMessages`**——
      **没有新写一套过滤**，所以与聊天轮次那份 `GroupChat.visibleMessages` 判定同源。
      口径：`roundtable` → 议长（`chairRoleId`，`chairRound = true`，与 `GroupChat.plan`
      给议长那一步同源）；`pipeline` / `vote` / 未知 mode → **`roles` 列表第一个**
      （名单顺序即 `GroupChat.plan` 的发言顺序）。
      **fail-closed 分两档**：① `roles` 为空 → **空列表**（**不是全量**）；
      ② 议长 id **悬空但名单完好** → 退回第一位且 **`chairRound = false`**
      （⚠️ 不置 false 的话 `chairRound` 分支会**真越权**，这条单独有用例钉住）。
      ⚠️ `compressConversation` 的处理是特殊的：**过滤只作用于送给 SUMMARY 模型的那份副本**
      （`compressScope`），**落库那份完全没动**——`allMessages` / `messagesToKeep` /
      `newMessageNodes` 仍来自完整 `currentMessages`，**符合「不落第二套消息库」这条硬约束**；
      `compressScope` 为空且 `messagesToCompress` 非空时抛 `IllegalStateException`
      fail-closed（宁可失败也不让模型凭空编一段摘要再替换真实历史）。
      **单聊逐字不变**：`messages()` 第一行
      `if (!isGroupConversation(conversation)) return conversation.currentMessages`
      （**返回同一个 List 实例**，不是拷贝）。
    - **⚠️⚠️ 这条必须作为遗留登记的原因：「按谁的视角取」是判断，不是实证。**
      契约只规定「**必须过滤**」，**没有规定「按谁过滤」**——标题/摘要**没有轮次**，
      「该按谁看」是产品口径问题。当前实现按上表取值，**KDoc 已标「⚠️ 待产品确认」并
      列了替代口径**（「议长优先存在于任何模式」/「用户点的是哪个角色就按谁」），
      **结论会不同**。⚠️ **改动只许动 `SummaryViewerStep`，并同步改
      `SummaryViewerScopeTest`**。
    - **⚠️ 剩下的证据边界（本条仍然零设备）**：① 「roundtable 下议长视角看到的东西**符合
      产品预期**」零份证据——纯函数只证明**实现自洽**；② 真机上群里跑完一轮后
      **标题实际长什么样**零份；③ **压缩路径至今没被任何 UI 触发过**，所以它的 fail-closed
      抛错在真机上是什么表现零份；④ 4 次变异检验是**JVM 级**的，不是真机观测。
    - **测试（`72a5e548`）**：`SummaryViewerScopeTest` **12 条真单测**
      （含 fail-closed 空名单、议长 id 悬空、幂等、**反向越权**断言「别的角色发言不可见」
      与「议长在 roundtable 下看得到本轮全部」）；`SummaryViewerScopeWiringSourceGuardTest`
      **5 条源码护栏**（含一条**断言 KDoc 里那句「⚠️ 待产品确认」不许被删**）。
      ⚠️ **变异 4 次全红**：换回裸 `currentMessages` / viewer 选错（`roundtable`→`vote`）
      / fail-closed 退化成 pass-through / `splitMessages(compressScope)` 改回
      `splitMessages(messagesToCompress)`。
      四条命令退出码全 **0**，**110 类 / 881 例 / 0F 0E 0S**，
      **5 个改动文件 lint 命中 0**。
    - **⚠️ 与第 28 条的关系**：第 28 条（「`ChatService.finishInterruptedPendingTools` 里的
      三处 `generateText`」）**整条已证伪**，它把 `ChatService` 的**命名陷阱**当成了泄漏。
      **真正被修的是 `ChatManager` 这两处**——本条。⚠️ `core/service/ChatService.kt`
      里那三处**同名函数仍然送未过滤的 `currentMessages`**，但结构上到不了群聊会话
      （`generateTitle` / `generateSuggestion` 在 `handleMessageComplete` 的 `onSuccess`
      里、位于那道 `require(!isGroupConversation(...))` **之后**；`compressConversation`
      全 app 无调用方）——**这是「不可达」不是「已堵」**，处置见第 28 条尾巴。
34. **⚠️⚠️ 设备卫生：「全量测试后主 DataStore 的助手被测试助手顶替」导致 UI 夹具会话列表恒空——执行者未修，下次接设备先查。**（2026-10-06，第八轮）证据：17:18 拉回的 `settings.pb`（22365 B / sha256 `924cda89ec5b8140e71540456c4f1b8b16569b05c85730a413305292468845bd6`）里助手只剩「角色甲/乙/丙」（`0c1c11ae-…a1/b1/c1`），默认助手 `0950e2dc…` 出现 **0 次**；17:21/17:22 两张截图底部当前助手 =「角色甲」，三种筛选 chip 下列表全空。夹具会话归属 `0950e2dc` ⇒ 列表恒空是必然。根因：`C1LiveModelSequenceTest` 的 `@After` 恢复（`runCatching` 吞异常）未生效或被污染快照覆盖。⚠️ **下次用 UI 夹具前必须先恢复 settings**（或把夹具会话改挂测试助手），否则「筛选真的按类型过滤」永远看不到东西。完整现场见「C1 真机证据采集第八轮」⑤。 ✅ **已恢复（第九轮，2026-10-06，采集父 HEAD `4d73a26b4` / 登记 commit `3e5596f9b`）**：走的是**pb 字节手术**（`assistants` → `[]` 重算长度前缀 + `select_assistant` 36 B 等长替换；两级备份 + 自研解析器断言其余 45 键逐字节不变 + `.new` 远程 SHA 比对 + `chmod 600` + 原子 `mv`），随后 app 自己的 `ifEmpty { DEFAULT_ASSISTANTS }`（`SettingsRepository.kt:365`）把完整默认助手重新落盘。SHA 链（我逐文件 `sha256sum`/`stat` 复核）：污染 `6a0423c4…d4883`（22365 B）→ 删 2 个助手 `fadfe1ff…`（19047）→ `9129ebb6…`（19047）→ 手术版 `a2fa5fa3…`（17386）→ app 选中后 `9b6478ab…`（18440）→ **重启后终态 `37baf83f…`（18440；`0950e2dc` 出现 2 次、`0c1c11ae` 0 次）**；UI 侧抽屉从「没有对话记录」恢复到 6 条会话、force-stop 重启后仍 6 条；主库 6 个会话（全部归属 `0950e2dc`）全程未动。⚠️ **但测试代码根因未修**：`C1LiveModelSequenceTest.kt:517` 的内存快照 + `:643-645` 的 `runCatching` 吞异常仍在仓库里，`update()` 在 `init==true` 时仍是 no-op（`SettingsRepository.kt:442-446`）——**下一轮全量仪器测试会再次污染**。完整现场见「C1 真机证据采集第九轮」②。
35. **⚠️ mock 脚本本体已不在磁盘：`/tmp/opencode/roundtable-vote/mock_openai_v2.py` 现存的 20061 B 是从 opencode.db 工具调用记录逐字节恢复的**（write 记录 `prt_10b67ba…` + edit 记录 `prt_10b6ab4e…` 按序重放，与恢复件逐字节相同；`py_compile` 通过；sha256 `d5c5d9a4c4b09917b8e31379484f783bef17059b13dfb7ab0a96041daae3f8a0`）。⇒ **复现依据是工具记录，不是原始文件**；下一轮若还要跑 mock 四连，**先确认这个恢复件还在**（在仓库外 `/tmp`，随时可能被清）。4/4 通过 + token 与文档基线吻合是其正确性反证。见「C1 真机证据采集第八轮」③。
36. **⚠️ 新缺陷：只有 1 个助手时删除会崩（SafeMode），未修、只报告（2026-10-06，第九轮）。**
    `AssistantViewModel.removeAssistant`（`app/src/main/java/heizige/kk/khatkit/app/feature/assistant/AssistantViewModel.kt:48-61`）
    先把 settingsFlow 更新为空列表，`SettingsRepository.getCurrentAssistant()`
    （`app/src/main/java/heizige/kk/khatkit/app/core/data/datastore/SettingsRepository.kt:745`）的
    `assistants.first()` 随即抛 `NoSuchElementException`（`ChatViewModel` 的 main 收集器），
    发生在持久化与后续清理之前 ⇒ **最后一个助手删不掉、app 进 SafeModeActivity**。
    崩溃栈证据 `ui/r4-12-after-3.xml`（15846 B / sha256 `c63e14e7…6677`，含
    `SettingsRepositoryKt.getCurrentAssistant(SettingsRepository.kt:745)` 与 SafeMode 文案
    「当前助手：角色丙」）。触发条件：**删除剩余最后一个助手**。⚠️ 本轮只取证未修；
    修复方向（删最后一个助手时不置空 settingsFlow / `getCurrentAssistant()` 对空列表回退默认）
    **不在本轮授权内**。完整现场见「C1 真机证据采集第九轮」③。
    ✅ **已修（2026-10-06，第十三批，HEAD `81147639a`）**：见「生成失败健壮性与助手空列表修复」节 ②——`85f708169` 让 `update()` 写路径与读路径共用 `normalizeAssistants`（空列表回落 `DEFAULT_ASSISTANTS`），`getCurrentAssistant()` 变全函数（`firstOrNull()` 兜底），`AssistantViewModel.removeAssistant` 改走 `Settings.removeAssistant`；`81147639a` 补 6 例单测（含「删最后助手」核心回归）。⚠️ **但真机 SafeMode 路径未复测**（零设备），修复的运行时行为只有单测 + 探针证据（详见该节 ④ 的四条限制）。 ⚠️ **第十六批复核（2026-10-06，HEAD `18d730465`）：仍维持「已修」判定**——本批未碰该路径（C1-06 取消测试不改助手列表），未获新的真机 SafeMode 复测证据；「修复未在真机复测」的边界照旧。
37. **⚠️ 上一轮留下的 `RUNNING` 残留行仍未清理（第九轮复查仍在）。**
    我直读两份 pull 回的库（`db-before` / `db-after-chips`）复核：`group_runs` 里
    status=`RUNNING` 的行 `conversation_id=2b6c129f-35da-4934-bfa7-6bf8d8099480`、
    `round_id=round-a8d28946-4685-461c-9ada-20095319ca53`、`token_limit=9000`、
    `spent_tokens=0`、`started_at=1791271593923`（≈2026-10-06 15:26:33）、`ended_at=NULL`。
    **未删，只报告**（不影响本轮任何判定，但会一直出现在随机轮询里）。 ✅ **已消（第十六批，2026-10-06，HEAD `18d730465`）**：备份 → DELETE → `integrity_check` → checkpoint → 回推后，设备库总行数 7→5、**RUNNING=0**（登记代理对两份 pull 副本复算 sha256 `40d2eb76…` 并逐行对比：保留 5 行与备份非 RUNNING 行逐字段相同）。详见「C1 真机证据采集第十三轮」⑥。
38. **⚠️⚠️ 设备设置未恢复 + 一次 `kill -3` 操作失误招致 ColorOS 杀进程（2026-10-06，第十轮）。**
    ① **设置未恢复**：`screen_off_timeout` 现为 **`600000`**（原值 **`30000`**），
    `stayon true` 未恢复——**设备已不可达**（`adb devices` 空），**无法改回**（执行者报告；
    本次登记时同样无法连上设备复核，故此处只能转述）。② **操作教训**：18:59:50 执行者
    为看线程栈对测试进程执行了 `run-as … kill -3 28072`，ColorOS 记
    **`reason=13 OTHER KILLS BY SYSTEM … o-stop(40)`**，把 instrumentation 一并杀停 ⇒
    `INSTRUMENTATION_RESULT: shortMsg=Process crashed.`（`round5-instrument.log` 实测）。
    ⇒ **对仪器测试进程发信号会被 ColorOS 连带杀测试**，下次不要为看栈直接 `kill -3`，
    改用 `logcat` / 设备侧 dump 取栈。此条**不影响任何用例判定**，但设备遗留会持续到
    下一位接上设备。
    ⚠️ 本条与第 37 条（`RUNNING` 残留未清）**是两个独立遗留**：37 是库内数据残留，
    38 是设备环境设置残留 + 一次操作失误的教训。

39. **⚠️⚠️ 设备侧遗留：`screen_off_timeout` 又回到 `600000`、`stayon true`、`c1-cancel-evidence.db` 未删——下一轮接上设备第一件事处理（2026-10-06，第十二轮）。** ✅ **已还原（真机第十五轮，HEAD `37630adb4`，2026-10-07，执行者报告、登记时未独立复现）**：`screen_off_timeout=30000`、`stayon=false`；`c1-cancel-evidence.db` 的清理状态见「C1 真机证据采集第十五轮」⑥。
     第十一轮收尾曾把 `screen_off_timeout` 从 `600000` 改回 **`30000`** 并复验、`svc power stayon false`（执行者报告）；第十二轮 C1-06 取消尝试又把 `screen_off_timeout` 调高到 **`600000`**，收尾时设备 transport 丢失（`adb devices` 不可达）**未能改回**，`stayon` 仍 `true`；设备 app databases 里还留着 attempt 2 的 `c1-cancel-evidence.db`（登记代理读快照：`message_node` 2 行、`group_runs` 0 行，conversation `0c3907e5-…`）。⇒ **下一轮接设备先执行：`settings put system screen_off_timeout 30000`（并复验）、`svc power stayon false`、删设备上的 `c1-cancel-evidence.db`**，再谈采集。⚠️ 本条与第 38 条是同一设备侧问题的两次复发；第 38 条里那次「设备不可达」的教训仍然有效。 ✅ **已消（第十六批，2026-10-06，HEAD `18d730465`）**：`screen_off_timeout` 已由 600000 改回 **30000**（已复验）、`stayon=false`；设备上 `c1-cancel-evidence.db` **不存在**（tearDown `deleteDatabase` 已删）。详见第十三轮⑥。
40. **⚠️⚠️ C1-06 取消三次尝试全部不成立 + 一个无法解释的现象：mock 注入 HTTP 500 整轮却 `COMPLETED`（2026-10-06，第十二轮；可能是 mock 或生产的真问题，未定性）。**
     三次尝试（attempt 1 测试 7.903s 后失败 / attempt 2 设备中途掉线 / attempt 3 设备在 `am instrument` 生效前掉线）**一条取消断言都没有实测值**，超时半（`GROUP_ROUND_STEP_TIMEOUT_MS` 15 分钟）未跑。⚠️ **attempt 1 的现场**：mock `mock-requests-attempt1.jsonl` seq2 对角色 B 注入 `injected_http_500=true`（无 `reply_bytes`/`usage`，服务端确实返回 500），但 `trace-phaseB-attempt1.txt` 显示 `round1:terminal status=COMPLETED reason=`，库内 B 是一条 **speaker** 消息、正文 `"B 已收到，本轮取消用例正常执行，无异常。"`，C 也被调用，且 **mock 未收到第二次 B 请求**。该文本在**仓库源码 / 两份 mock 日志 / 生产库快照 `proddb/rikka_hub` / attempt 2 遗留库**四处**均 0 命中**（登记代理逐处实测）。⇒ 现象未定性：下一轮要带 `adb logcat` 抓这个 500 的客户端处理路径与重试来源，并确认「取消根本没执行到」是否与 mock 注入方式有关。完整现场见「C1 真机证据采集第十二轮」②。 ✅ **已定性（第十六批，2026-10-06，HEAD `18d730465`）**：根因 = 测试 settings 的 provider 列表被 `SettingsRepository.kt:349-352` 补回全部 `DEFAULT_PROVIDERS`，mock 的 HTTP 500 错误消息含 `"500"` ⇒ `ProviderFailover.isEligible` 判可切换 + `enableAutoRetry=true` ⇒ B 的请求被 failover **重放给内置「极客猫」真网关**，那段文本就是真模型的回复（所以四处 0 命中）。`18d730465` 以 `enableAutoRetry=false` + mock 来源守卫堵住；`610edbf86` 把零内容注入改成真 0 content 事件。⚠️ 生产 `ProviderFailover` 行为本身**未改**，此现象只影响测试夹具。详见第十三轮②。
41. **⚠️⚠️ 全项目取证方法订正：`am instrument` 的 shell 退出码不能当判据（2026-10-06，第十三轮）。** 本轮 19 个采集 run 里**含全部 FAIL 在内，设备侧 `INSTRUMENT_EXIT` 一律 0**（`exitcode.txt` 逐份实测）；且 `INSTRUMENTATION_CODE: -1` 在通过日志（vt-run1）与失败日志（rp-run1）里**完全相同**（`Activity.RESULT_OK` 的 shell 约定，同样不可区分）。⇒ 判据必须读日志正文里的 `OK (1 test)` / `FAILURES!!!`（`Tests run: 1, Failures: 1`）。契约 `:232-235`「测试命令及退出码」的「退出码」应同时记 `INSTRUMENT_EXIT` 与日志判据，且**以日志判据为准**。这影响本文件所有「`exit 0` ⇒ 通过」旧表述的解释方式。
42. **⚠️ roundtable 测试自身竞态（议长消息「先落库后盖章」被快照到 null）——已转出修复，未验证（2026-10-06，第十三轮）。** rt-run1/run2 的 FAIL 原文是测试侧 `awaitAssistantMessages` 只等条数；同轮 finally raw dump `run=COMPLETED / committed=[a,b,c]` 证明**产品侧正常**。修复（另一代理）完成并重跑通过前，roundtable 的稳定性记录按「2 次测试故障 + 1 次通过」读。
43. **⚠️ C1-05 正式证据结构性不可达（2026-10-06，第十三轮）。** 预算截断用的测试方法写死「等 3 条助手消息」，而截断只产 2 条 ⇒ **必然超时失败 ⇒ 正式 JSON 永不写出**。可能需拆出独立的、期望 2 条的测试方法（或把期望按 `skipped_role_ids` 参数化）。 ✅ **已消（第十四轮，2026-10-06，HEAD `b60aa80c5`）**：`a9df57c96` 新增专用方法 `realProviderBudgetTruncationRecordsRunLogAndExport`（期望 2 条助手消息），真机通过并产出正式证据 `c1-live-evidence-real-budget.json`；`b60aa80c5` 再补库内 `messages.count==3`/`assistant_role_order==["a","b"]` 直读断言。C1-05 据此升 `verified`。详见「C1 真机证据采集第十四轮」①。
44. **⚠️ `settingsStore.update` 只能追加 provider、不能减少（2026-10-06，第十三轮）。** `SettingsRepository.kt:349-352` 读取时对缺 id 的 provider 逐个补回 `DEFAULT_PROVIDERS`（内置「极客猫」是默认表里唯一带 `models` 的 provider，`enabled=true` + 硬编码 key）⇒ **任何「只留某一家 provider」的测试都会被这条坑**——按 `providers=[mock]` 保存，读回必然变成 `[mock]+全部内置`。测试若要隔离 provider，必须走 `enableAutoRetry=false`（或改读取路径的预期），不要指望「列表里只剩 mock」。
45. **⚠️⚠️ pipeline / roundtable 第 3 位顺序发言者零产出（网关 HTTP 200 空内容），6/6 稳定复现；与本条无关的历史「偶发」结论冲突（2026-10-06，第十四轮）。** 真实网关下第 3 位角色「c」一律落为 `turn_kind=error` / 正文「[角色丙] 本轮生成失败：本轮没有产出内容」/ `model_id=null` / `wire_model_name=null` / usage 全 `-1`；`group_run=FAILED / reason=role_failed / committed=[a,b] / app_errors=[]`。roundtable 3 跑（Time 301.583/300.969/301.756）+ pipeline 3 跑（Time 27.627/25.884/19.129）**全失败**。**真因 = 网关返回 HTTP 200 但 SSE 流里没有任何 content delta（空白完成）**——`ConversationSession.finishGeneration`（`ConversationSession.kt:97-116`）丢弃空占位助手消息 → `stampGroupTurn`（`ChatManager.kt:1980-2003`）返回 null → `commitGroupTurn`（`ChatManager.kt:1957`）写 error 节点 + `FAILED/role_failed`，**全程无异常**。⚠️ **不是** `43d607bdd` 修的那个「非 2xx + 空 body 静默当零产出」（走那条错误正文会是 `HttpException: …` 且 `app_errors` 非空；实测恰是「本轮没有产出内容」+ `app_errors=[]`）。⚠️ **refute 掉的假设**：「c 的 prompt 很长所以失败」不被数据支持——c 在 pipeline 的 `prompt_tokens` 从未落库；vote 里 c 同一模型第 3 次调用成功（6877/6882）；基线 `prompt_tokens≈6827` 对 ~300 字符 system prompt 明显失真（网关侧自报含自身开销）。**判定影响**：C1-02 / C1-03 本批 3/3 全失败 ⇒ 不升；本文件 `:4114` 与判定规则第十条的「偶发」结论已加订正块（保留原文）。完整现场见「C1 真机证据采集第十四轮」②/③。 ⚠️⚠️ **真机第十五轮订正（HEAD `37630adb4`，2026-10-07）：本条的「6/6 稳定复现 ⇒ 不是偶发」结论被本批推翻（原文保留）。** 本批只读根因探针 `C1GroupZeroOutputProbeTest#pipelineThirdSpeakerZeroOutputProbe` 真机 `OK (1 test)`（Time 14.486），**`diagnosis=null`（零产出未复现）**：`group_run=COMPLETED/committed=[a,b,c]`、`error_node_role_id=null`、`spent=20750`、`sampler_poll_count=130`、`sampler_transition_count=12`、`placeholders_observed_count=3`、`vanished_unstamped_count=0`，三个占位全部「盖章 + 有正文」。⇒ 正确表述从「6/6 稳定复现」改为「**间歇性、非 100% 必现**」——第十四轮曾 6/6 复现，本轮未复现；触发条件仍与第十四轮同源（网关 HTTP 200 空内容），本轮未抓到失败现场。⚠️ 本条的操作化判据（有没有 HTTP 状态码 / 响应体）本轮**仍然零份**，那半不动。详见「C1 真机证据采集第十五轮」③。
46. **⚠️ `app_errors` 有采集盲区，不能当「一切正常」的证据（2026-10-06，第十四轮·新发现）。** `app_errors` 只由 `ChatManager.addError`（`:311-321`）写入，而**零产出路径不抛异常**（不写）、**超时**的 `TimeoutCancellationException` 在 `ChatManager.kt:1020` 先 rethrow（不写）、**用户取消**被 `:317` 早期 return（不写）。⇒ **拿 `app_errors=[]` 推断「没有错误」是错的**——本批 roundtable/pipeline 的失败跑全部 `app_errors=[]`，而它们其实稳定失败（见第 45 条）。下一轮采集若要靠 `app_errors` 判健康，必须先补这三条路径的写入点，否则应改用 `logcat` / `group_run.status` / 消息 `turn_kind` 作判据。⚠️ 相关但**不同源**的第 40 条（failover 顶包极客猫）是**测试夹具**问题（mock 500 + `enableAutoRetry`），本批的第 45 条是**生产路径**问题（真实网关 200 空内容）——**两者来源不同，不要互相解释**。
47. **✅ 群聊「失败续跑」入口已落地（零设备，2026-10-07，`d6494e49a`）：「群聊 UI 无重试入口」这一缺口已消——但它是「收窄入口」，不是「放开『重新生成』」。** 新增纯判定 `GroupRetryEntry.canResume(isGroup, isLastMessage, message)`（`feature/chat/GroupRetryEntry.kt:61-69`）= 群聊 && 最后一个节点 && `MessageRole.ASSISTANT` && `turnKind == GroupChat.TURN_ERROR` && `roleId != GroupChat.SUMMARY_ID`；接通 `ChatList.kt`（计算 `onGroupResume`）→ `ChatMessageActions.kt:155-169`（按钮）→ `ChatMessage.kt`（透传），**点击仍落到同一个 `ChatManager.regenerateAtMessage`，未改 `ChatManager.kt`、未发明新机制、未触碰任何 `if (!groupChat)` 门禁**。它填的是契约 `:204` / `:227-228` / `:201` 承诺的「重试可从失败角色续跑」（失败角色不在 `committedRoleIds` 里，续跑补上它和其后未运行角色、跳过已提交角色）。⚠️ **真机行为零份**（本轮零设备），只有 JVM 纯判定 8 条 + 源码接线；⚠️ **「重新生成」在群聊下仍整条关闭**——因为它会顶掉被点角色已提交的发言而不修订 `committed_role_ids`（机制五条见「第十五轮（零设备）」③）。
48. **⚠️ 取消 / 预算中止的轮次仍无续跑入口（零设备，2026-10-07，`d6494e49a` 附带发现，未做）。** `cancelRound`（`GroupTurnCoordinator.kt:485-491`）与预算中止（`BUDGET_STOPPED`）**只终结运行日志、不写错误节点**，因此不是 `GroupRetryEntry.canResume` 认的「失败角色错误节点」形状 ⇒ **这两类终态轮次没有续跑入口**。按契约其续跑需要 UI 读 `group_runs` 状态（而非消息节点），属另一个子包的工作，本轮**只记不做**。
49. **⚠️⚠️ C1-07 设备侧幂等用例稳定失败（真机，2026-10-07，HEAD `37630adb4`；待判定是测试期望错还是生产缺陷）。** `C1GroupRetryResumeDeviceTest#retrySameRoundSkipsCommittedTurnsAndDoesNotDuplicateMessages` 本批 **2/2 稳定 `FAILURES!!!`**，失败在 `C1GroupRetryResumeDeviceTest.kt:378`「**阶段 1 的 b 错误节点必须原样保留**」（Time 15.843 / 13.337）。raw 现状：phase1 `FAILED/reason=role_failed/error_message=本轮没有产出内容/spent=1741/run_token=604b0069-…/committed=[a]/skipped=[c]/started_at=1791336038560`；phase2 `COMPLETED/committed=[a,b,c]/spent=14819/同 run_token/同 started_at`；a 消息 id `cb45d9ef-…` 不变、b/c 各 1 条、`spent=14819==Σusage`。⚠️ **不是已修复、也不是已定性**——由一个**独立任务**判定是测试期望错还是生产缺陷；在判定/修复前 C1-07 保持 `unverified`。证据在 `/tmp/opencode/c1-verify/c1-retry-raw-phase1-vfy1.json`（1936 B）与 `…phase2-vfy1.json`（4325 B）。 ✅ **已修（2026-10-07，`5c362851a`）：判定 = 测试期望写错（非生产缺陷）。** 修法：只改 `C1GroupRetryResumeDeviceTest.kt`（**+119 / −40**），把阶段 2 的 P2-C 从「旧错误节点必须原样保留」改为正向断言组（同一 `MessageNode` 内候选替换 + 切 `selectIndex`），其余断言一条未删未弱；`app/src/main` 一字未动。⚠️ **真机未复跑**（本轮零设备）——「2/2 转绿」是逻辑 / JVM 推断，**不是设备实证**。见「零设备修复批次（2026-10-07，HEAD `5c362851a`）」⑤。 ✅ **真机重跑通过（真机第十六轮，HEAD `bd88aaff2`，2026-10-07）**：`C1GroupRetryResumeDeviceTest#retrySameRoundSkipsCommittedTurnsAndDoesNotDuplicateMessages` 真机 `OK (1 test)` / Time 13.108，报告 12 行断言全 `expected==actual`（`P2 group_runs rows=1`、`run_token_reused=true`、a 消息 id 不变、`spent=19286==Σusage`、旧错误节点同节点候选替换且未被选中）；C1-07 据此在矩阵格与证据表格各升 `verified`。见「C1 真机证据采集第十六轮」①。
50. **⚠️⚠️ C1-10 抽屉分页 `pagingSource_*` 5 条真机失败 + 一个真机 DB 口径疑点（真机，2026-10-07，HEAD `37630adb4`；另一任务在修）。** `C1GroupPagingAndFilterDeviceTest` 8 方法本批 **3 通过 / 5 失败**：通过 `typeFilter_allChipsFilterIndividuallyAndRestoreExactly`（Time 6.41）/ `typeFilter_chipSwitchNeverLosesData_dbCountsStable`（6.021）/ `mixedList_groupBadgeRendersOnlyOnGroupRows`（4.547）；失败 5 条均 `pagingSource_*`——`:357` expected 15 but was 20、`:392` expected 55 but was 60、`:435` expected 25 but was 40、`:493` expected 55 but was 60、`:527` expected 25 but was 40。**真机 DB 实测 55 行（GROUP=25 / DIRECT=30），生产常量 `PAGE_SIZE=20` / `INITIAL_LOAD_SIZE=40`，但分页 source 累计取回 60 / 40 ⇒ 重叠 / 超取**（Room `LimitOffsetPagingSource` 的页边界口径疑点）。⚠️ **另一任务在修**，本轮只登记、C1-10 保持 `unverified`。见「C1 真机证据采集第十五轮」②#5。 ✅ **已修（2026-10-07，`0ae9f570a` + `5ca9ecbaf`），真机未复跑。** 根因 = Room 2.8.5 `RoomPagingUtil.getOffset` 对 `Refresh` 做「刷新窗口夹取」（`offset >= itemCount - loadSize` 时静默夹到 `max(0, itemCount - loadSize)`），而生产 `Pager` 的后续页一律发 `Append`。修法两处：① `0ae9f570a` 把设备侧 `loadPage()` 改为 `key == null` → `Refresh`、否则 → `Append(key, loadSize, false)`；② `5ca9ecbaf` 把生产 `ConversationRepository` 四个 offset 分页 API 统一改 `LoadParams.Append(key = offset, loadSize = limit, placeholdersEnabled = false)`（**只影响 HTTP 分页 API，不影响 `Pager` 驱动的 UI 列表**）。⚠️ **真机未复跑**，契约 `:206`/`:232-235` 四类产物零份 ⇒ C1-10 仍 `unverified`。见「零设备修复批次（2026-10-07，HEAD `5c362851a`）」③/④/⑥。 ✅ **真机重跑通过（真机第十六轮，HEAD `bd88aaff2`，2026-10-07）**：`C1GroupPagingAndFilterDeviceTest` **8/8 `OK (1 test)`（两遍都 8/8）**，5 条 `pagingSource_*` 全部转绿——`first_size=40/second_size=15`、`page_sizes=[20,20,15] loaded=55 distinct=55 db_unfiled=55`、`group=[20,5]=25 / direct=[20,10]=30`、`type_arg='' rows=55`、`group_hits=25 all_hits=55`；**设备实证了 `0ae9f570a`（测试驱动）+ `5ca9ecbaf`（生产分页 API）的修复**。⚠️ 契约 `:232-235` 不认仅 UI 状态、`:206` 四类产物本例不产出 ⇒ C1-10 仍 `unverified`（8/8 只消掉了「真缺陷待修」这一条不升理由）。见「C1 真机证据采集第十六轮」②/⑤。
51. **⭐ 二维码中文 / 非 Latin-1 内容有损缺陷（已修，2026-10-07，真机第十七轮，`0edc70d67` / `e35f64e39` / `e2bfce4ad`）。** `QRCode.kt` 的 `QRCodeWriter().encode(...)` **4 参重载未设** `EncodeHintType.CHARACTER_SET`，zxing 3.5.4 默认 **ISO-8859-1** ⇒ 中文 / emoji 被有损替换成 `?`（JVM 探针实测 `{"persona":"热血解说"}` → `{"persona":"????"}`，`EQUAL=false`）。修法：5 参重载显式 `mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")`（`QRCode.kt:79`）。⚠️ **实测副作用**：UTF-8 触发 zxing `Encoder.appendECI` 写 **12 bit ECI 头**，恰好吃掉 1 字节字节模式容量 ⇒ **QR v40-L 有效容量 2953 → 2952 字节**（二分实测；测试常数同步 2953→2952，**收紧不是放宽**）。**真机非空验证（关键发现）**：删掉 hints 那行 → 重装主 APK → 中文用例 `FAILURES!!!` **连续 4/4**（`AssertionError: MLKit 未能从生产位图解出任何二维码`），同回退 APK 下 ASCII 用例仍 `OK (1 test)`；⇒ **旧编码的中文二维码 MLKit 直接扫不出来，比「变问号」更严重**。`cp` 还原 + `sha256sum -c` 成功 + `git diff` 为空 → 重装 → `OK (1 test)`（双侧验证）。修复版 `QRCode.kt` sha256 `ecb78fc67438298aedac48bb7d5afd8c65a8c7618ca9fe8b53b816d70bace849`（**登记子代理实测一致**）。见「C1 真机证据采集第十七轮」⑤。
52. **⚠️⚠️ 订正一个预置假设：被酒馆重存过的文件回 KhatKit 是 `Unsupported`，不是 `NoConfig`（2026-10-07，真机第十七轮）。** 本文件旧文（第 4 轮（d）第 5 条与别处）写「酒馆 `open→save` 丢私有表头块 ⇒ 被酒馆重存过的文件再回 KhatKit 导入会得到 `NoConfig`」——**错**。实测：`TavernChatCodec.importGroup` **`:298`** `payload = document.header[GROUP_FIELD] as? JsonObject ?: return null`（表头无 `khatkit_group` **直接 `return null`**），调用方 `GroupTavernImport.kt:176-180` 映射为 `TavernGroupImportOutcome.Unsupported`（`isError=true`）。**`NoConfig` 只在「`khatkit_group` 键在、但里面没 `config`」的畸形文件上可达**。JVM 测试级证据：`GroupTavernImportTest` 的 `a file without the khatkit_group block at all is not a config problem`（`app/src/test/java/heizige/kk/khatkit/app/feature/chat/GroupTavernImportTest.kt:217`）**PASS**。⚠️ **未做到**：用 JVM 对「重存后的真实字节」直跑 `importGroup`（离线取 `debugUnitTestRuntimeClasspath` 被 AGP 9.3.1 变体消解挡住）。原文处已就地追加订正块（保留原文）。
53. **C1-09 Tavern/QR 往返的进展与缺口（真机第十七轮 / C1-09 收尾，2026-10-07，HEAD `e2bfce4ad`）。**
    - ✅ **真 SillyTavern 应用级已证**：起真服务 @`06bde939`/AGPL、免登录+CSRF、`/api/chats/group/import` 接受（**字节级 copy**）、`/group/get` 服务端解析正确（条数 / 顺序 / `name` / `is_user` / `is_system` / `chat_items`）、私有顶层键 `khatkit_group` / `khatkit_character_names` 保留、野生 jsonl 不出现在 search/recent、最小注册后群出现、`open→save` 丢私有块。
    - ✅ **二维码位图端到端真机 6/6**：生产 `encodeQr`→`encodeQrBitmap`→MLKit 解码→逐字段→`importShare`；导出 FileProvider URI 字节 == 生产 JSONL；⑤ 中文修复后真机复跑 + 非空验证。
    - ✅ **`ACTION_SEND` 源码护栏 5 例**（构造正确性，非 UI 交互）。
    - ❌ **未验**：`NoConfig` 分支本身（真实服务器）；SillyTavern 多用户 / 开启登录 / `--listen`；**真实相机扫码链路**（`QrScannerSheet` 的 CameraX `analyzeFrame` 的 `mediaImage`，与位图路径只差图片来源）；**真实系统分享面板 UI 交互**；App 侧「扫码 → 建会话」闭环。
    - **判定**：C1-09 **仍 `unverified`**（逐条依据见「C1 真机证据采集第十七轮」⑦）。
54. **⚠️ lint 基线变动与归因（2026-10-07，真机第十七轮，纯文档窗口、不重跑 gradle）。** 本批实测 app lint = **`0 error / 617 warning / 6 hint = 623`**，比旧基线 **581W + 6H = 587** 多 **36** 条，**全部是 `gradle/libs.versions.toml` 的依赖版本检查**（`NewerVersionAvailable` 29 + `AndroidGradlePluginVersion` 4 + `GradleDependency` 3 = 36），**属环境 / 联网相关**（依赖仓库可见性变化触发），**不是 C1 代码引入**。⚠️ **本批改动的两个文件 lint 命中 0 条**（`docs/eval/c1-group-chat.md`、`docs/beyond-operit-implementation-status.md` 都是文档，本就不进 lint 口径）。⇒ **不要把 587 写成 623、也不要说「lint 未变」**——这 36 条是本批与上一批之间真实存在的差异。全模块实测 **650W + 7H = 657**。由 `c1_doc_stats.py` 的 `lint` 口径独立复算确认（app `error 0 / warning 617 / hint 6（合计 623）`）。见「lint」段。

55. **⚠️ 设备侧 `settings.preferences_pb` 仍含历轮测试用的 `realProvider` 与 3 个测试助手（真机，2026-10-07，未清）。** 本轮登记时实测该 pb 的 mtime = **14:47**（**早于**本轮真机采集 15:03），说明其中的真实网关 provider 与 `roleA/B/C` 类测试助手是**历轮**遗留，**本轮未清**（属设备侧遗留，非仓库代码问题）。⚠️ 影响面：后续真机用例若直接读设备全局 settings，会继承这些 provider / 助手；本轮第 9 条自己在 `finally` 里还原 `originalSettings`，故不改变本轮结论。**处置建议**：跑下一轮前手工清设备 settings 或做 pb 手术（参照第九轮「设置污染 pb 手术恢复」）。**未复现**（登记子代理无设备，仅凭执行者提供的 mtime 记录）。
56. **⚠️ 第 9 条对「筛选结果首条必须是本案夹具群」有依赖（真机，2026-10-07，稳健性注意项）。** `C1GroupPagingAndFilterDeviceTest#realGatewayRoundInFixtureGroupProducesContractArtifacts` 的 `:735-739` 断言「生产 DAO 取到的筛选首条 ∈ {`groupAId`, `groupBId`}」；本轮首条 = `0c1c0de5-…-f3a1`（`groupAId`）。⚠️ **若设备库里有其它群创建时间更早 / 排序更靠前**，首条会落在夹具群之外 ⇒ 用例按防御断言**响亮失败**（不会误打用户真实群），但**会红**。⚠️ 也意味着本轮证据 JSON 里的 `opened_group_id` 具体是哪条群**依赖设备库状态**，换设备 / 换库不保证复现同一 id（但 8 字段的性质不变）。**未复现**（登记子代理无设备）。
57. **⚠️ 本轮测试首次尝试遇「网关流挂死」，非断言失败（真机，2026-10-07）。** 第一次 `am instrument` 在连接建立后**网关流消失、无判定**，测试进程 **0% CPU 卡在 `stopGeneration` 的 `jobs.join()`**；`/tmp/opencode/c110/instrument-run1.log`（432 B）只有 `INSTRUMENTATION_STATUS_CODE: 1`、无终止行，与该现象一致。⚠️ **这是执行者报告 + 日志旁证，不是断言失败**（重跑 `c1-c110-green-instrument-run2.log` 即 `OK (1 test)` / Time 19.62）。⚠️ 与遗留第 45 条（第 3 位角色零产出）/ 第 40 条（failover 顶包）**不是同一现象**：本条是**客户端侧挂死**。**未复现**（登记子代理无设备，未跑 `adb`）。

**⚠️ 历史遗留第 40 条与本批的关系（复核结论：不同来源）**：第 40 条「mock 注入 HTTP 500 整轮却 `COMPLETED`」的根因是**测试夹具**——测试 settings 的 provider 列表被 `SettingsRepository.kt:349-352` 补回全部 `DEFAULT_PROVIDERS`，mock 的 500 错误消息含 `"500"` ⇒ `ProviderFailover.isEligible` 判可切换 + `enableAutoRetry=true` ⇒ B 请求被 **failover 重放给内置「极客猫」真网关**。本批第 45 条零产出的根因是**生产流式路径**——真实网关 HTTP 200 + SSE 无 content delta，**不涉及 failover、不涉及 mock**、`app_errors=[]`。**两者一个在夹具、一个在生产；一个是 5xx + 顶包、一个是 200 + 空内容**，不得拿一个解释另一个。第 40 条已在第十六批定性并修复（`18d730465`），本批不重开。


### ⚠️ 真机窗口之后，「下一位怎么把 unverified 变成 verified」还剩什么

前置那件「一台能装的设备」**已经满足了**（OnePlus `PKG110` / Android 16 / API 36，
2026-10-05）。上面 5 条通用采集手段里：

| 项 | 2026-10-05 之后的状态 |
|---|---|
| 1. 可见消息 ID 台账 | ⚠️ **pipeline 已采**（真机真 Room 库，3 个 viewer 逐条落定 + 6 对越权审计 `violations: []`）。**roundtable / vote 没采**，C1-03 议长视角仍然零份 |
| 2. 实际模型调用序列 | ❌ **仍然缺**。只拿到路由决策，**没有一次真实 HTTP 调用**——设备上没有 API key |
| 3. token 计数 | ❌ **仍然缺**。只拿到预算口径，`3000/1096` 是构造输入 |
| 4. 导出 SHA-256 | ⚠️ **已采**（3 种 mode，真机文件 + 真机 `MessageDigest` + 本机 `sha256sum` 三重一致）。⚠️ 但**没走过 `ACTION_SEND` 真实分发**、**酒馆本体能不能打开仍然零证据**、**哈希只在固定夹具下可复现** |
| 5. 仪器测试 | ✅ **25/25 全绿**（+ `C1DeviceEvidenceTest` 3 条，另有一轮合跑 **45/45**）。⚠️ 全量 56 条因 Coil 单例崩溃**跑不完**（未修） |

⚠️⚠️ **第二个真机窗口之后（新增列，**上面「2026-10-05 之后的状态」那一列按惯例保留
不覆写**）**：

| 项 | 第二个窗口之后（2026-10-05 晚些时候） |
|---|---|
| 1. 可见消息 ID 台账 | ✅ **三 mode 全采**（`a9d2077e` 夹具按 mode 各采一遍，越权审计三 mode 全 `passed=true` / `checked_pairs=6` / `violations=[]`）+ ✅ **pipeline 真实 HTTP 台账**。⚠️ 仍缺：显式 @ 的投递收窄（夹具的 `@` 落在用户消息上）、取消/超时、记忆空间检索可见性 |
| 2. 实际模型调用序列 | ✅ **已采**（`adb reverse` + mock，`mock-model-a → mock-model-b → mock-model-c`，真请求，`stream=true` / `ktor-client` / `include_usage`）。⚠️ **只跑了 pipeline**——roundtable 的议长汇总轮与 vote 的三张选票**真实 HTTP 零份**；且 mock 不是真实模型 |
| 3. token 计数 | ✅ **已采**（main 773、budget 236，与落库 `group_runs.spent_tokens` **逐条相等**）。⚠️ 口径是 prompt+completion 累计；mock usage 是 `ceil(bytes/4)` 估算，**原始字节数已落盘可手算复核** |
| 4. 导出 SHA-256 | ⚠️ **不变**（真机文件 + 真机 `MessageDigest` + 本机 `sha256sum` 三重一致）。⚠️ `ACTION_SEND` 真实分发**没走过**、**酒馆本体零证据**、**哈希只在固定夹具下可复现** |
| 5. 仪器测试 | ✅ **28/28 全绿、exit 0**（`C1DeviceEvidenceTest` 3 + `GroupRunDAOTest` 12 + `Migration_30_31_Test` 7 + `Migration_31_32_Test` 6）；`C1LiveModelSequenceTest` 手动 `am instrument` **3.6s 完成**。⚠️ 全量 56 条仍因 Coil 单例崩溃**跑不完**（未修） |

⚠️⚠️⚠️ **「四类产物都有真机内容」之后，剩下的缺口换成了这五条**——它们不是四类产物
里的，而是**四类产物覆盖不到的那些行**：

| # | 缺什么 | 卡在哪 |
|---|---|---|
| 1 | **roundtable / vote 的真实 LLM 调用记录** | mock 返回 `[mock] CASE:…`，`parseBallot` 认不出 `VOTE:` 前缀 → 要 mock 改成按 case 返回 `VOTE:<id>` / 候选块 |
| 2 | **酒馆（SillyTavern）本体打开群聊导出文件** | 桌面端，**零证据**；AGPL 边界下只借鉴名单顺序与整词 `@` |
| 3 | **真机 UI 端到端**（C1-10 的实质缺口） | 抽屉 chip 来回切换要录屏 + 两侧会话消息数与最后一条消息 id；**一行 Compose 没上过屏** |
| 4 | **相机扫码真机链路**（C1-09 的实质缺口） | `QrScannerSheet` 只有编译 + 16 条 JVM 单测，**CameraX + MLKit 没在设备上跑过** |
| 5 | **`BrowserRuntimeTest` 的 Coil 单例崩溃** | `RouteActivity.kt:202` 的 `setSingletonImageLoaderFactory`（来自 `b5c5ebcb`，**与 C1 无关**），**未修**，稳定复现 |

**原来那句「在拿到 API key 之前，契约 `:206` 的第三、四类在原理上就采不到」已被推翻**：
不需要 API key，`adb reverse` + 本机 mock 就能采到真实 provider 代码路径的请求与 usage。
⚠️ **但别把它读成「拿到 key 之前第三、四类就算验过了」**——mock 不是真实模型，
roundtable / vote 的真实调用仍然零份。**最短的下一步**是第 1 项（改 mock 的返回文本，
让 `parseBallot` 真被触发）+ 第 3 项（真机 UI 录屏）。

⚠️⚠️⚠️⚠️ **第四个窗口之后（真实公网网关 `6ba95422`，**上面两列按惯例保留不覆写**）**：

| 项 | 第四个窗口之后（2026-10-05 晚，HEAD `6ba95422`） |
|---|---|
| 1. 可见消息 ID 台账 | ⚠️ **不变，仍是「pipeline 已采」**。真实网关那轮的三视角台账**没拉回来**（进程被杀 + 卸载清空外置目录），**本轮不新增任何台账** |
| 2. 实际模型调用序列 | ⚠️ **升级了但仍不算通过**：真实公网网关（`DefaultProviders.kt:281-318`，设备直连）跑出 `deepseek-v4-flash → glm-5.2 → deepseek-v4-flash`，与按角色绑定一致。⚠️ **但模型名非 wire 抓包**（uuid 反查，JSON 已标 `wire_model_name_provenance`）+ **用例没有一次全绿记录** + **roundtable / vote 真实调用仍零份** |
| 3. token 计数 | ⚠️ **升级了但仍不算通过**：真实 usage 6803+159 / 6667+68 / 6880+102，**Σ = 20679 与落库 `group_runs.spent_tokens` 精确相等**。⚠️ **没有落盘产物**、**只跑了 pipeline**（预算截断 / 取消 / 超时仍零份） |
| 4. 导出 SHA-256 | ⚠️ **完全不变**（真机文件 + 真机 `MessageDigest` + 本机 `sha256sum` 三重一致）。⚠️ `ACTION_SEND` 真实分发**没走过**、**酒馆本体零证据** |
| 5. 仪器测试 | ❌ **本轮 0 条跑成**。源码侧 `C1LiveModelSequenceTest` 2 → **5** 条 `@Test`（app `androidTest` 全量 **61** 条）；⚠️ **OnePlus OEM 回收策略在进程存活 34-44 秒时杀进程**，`connectedDebugAndroidTest` 全量 `Process crashed`、**0 个测试启动**，按「OOM 立即停止重试」共试 **11 次**后停止 |

⚠️⚠️ **第四个窗口把「剩下的缺口」改成了这六条**（前四条是新的形态，第五、六条是老的）：

| # | 缺什么 | 卡在哪 |
|---|---|---|
| 1 | **真实网关那批的落盘产物** | 用例跑成了但进程随后被杀、`connectedAndroidTest` 卸载 app 清空外置目录 → **JSON 没拉回来**。⚠️ **最短修法**：改用手动 `install -r -t` + 手动 `am instrument`（就像上一窗口那样），跑完立刻 `adb pull`，**别让 `connectedAndroidTest` 介入** |
| 2 | **模型名的 wire 级来源** | app 不保存响应 `model` 字段 → 要么在 `OpenAIProvider` 侧记日志，要么在设备侧经代理抓包 |
| 3 | **roundtable / vote 的真实调用** | 真实网关只跑了 pipeline。⚠️ **好消息**：persona 那个「真模型照抄上一位格式」的坑已经修掉（加第 3 条禁令），**本机按真实 pipeline 链 6/6 通过**——所以现在只差把 roundtable / vote 也用真实网关跑一遍 |
| 4 | **酒馆（SillyTavern）本体打开群聊导出文件** | 桌面端，**零证据**（与前三个窗口完全一样，没动过） |
| 5 | **真机 UI 端到端**（C1-10 的实质缺口） | **一行 Compose 没上过屏**；⚠️ 这一轮连仪器进程都起不来，**先解决第 6 条再谈录屏** |
| 6 | **⚠️ 设备侧两件事：OEM 杀进程 + Coil 单例崩溃** | OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`）与 `BrowserRuntimeTest` 的 `setSingletonImageLoaderFactory`（`RouteActivity.kt:202`）。⚠️ **前者不是代码缺陷**，先把设备上那些游戏/短视频应用清掉再复跑 |

⚠️ **「四类产物都有真机内容」这句话现在要加两个限定**：第三、四类**升级成了真实 provider
的数字**，但**没有落盘产物**、**模型名不是 wire 抓包**。所以
**「每一列都填上了东西」离「这一行该判通过」还差三步**：① 有可复算的落盘产物、
② 这一行点名的那条路径**真的被调用过**、③ 真机 UI / 桌面端那一半有独立证据。
**十例仍全 `unverified`。**

⚠️⚠️⚠️⚠️ **第五个窗口（HEAD `361c7cf6`，**上面那一列按惯例保留不覆写**）**：

| 项 | 第五个窗口之后（2026-10-06） |
|---|---|
| 1. 可见消息 ID 台账 | ⚠️ **完全不变**。本轮**零设备**，三 mode 台账仍是 `a9d2077e` 那份，**一份未增** |
| 2. 实际模型调用序列 | ⚠️ **完全不变**。仍是 `deepseek-v4-flash → glm-5.2 → deepseek-v4-flash`，**模型名仍非 wire 抓包**、**用例仍没有一次全绿记录**、**roundtable / vote 真实调用仍零份** |
| 3. token 计数 | ⚠️ **完全不变**。真实 usage 6803+159 / 6667+68 / 6880+102、Σ = 20679，**仍无落盘产物**、**仍只跑了 pipeline** |
| 4. 导出 SHA-256 | ⚠️ **完全不变**（真机文件 + 真机 `MessageDigest` + 本机 `sha256sum` 三重一致）。`ACTION_SEND` 真实分发**没走过**、**酒馆本体零证据** |
| 5. 仪器测试 | ⚠️ **不变**。本轮**一行 `androidTest` 都没动**（全量 `@Test` 仍是 **61**）；上一次全绿仍是 `28/28、exit 0`，`C1LiveModelSequenceTest` 那 5 条**在本窗口内没有一条在真机全绿过**<br>✅ **第六个窗口订正（HEAD `d96d64e1`，2026-10-06）**：那 5 条里的 **`realProviderRoundRecordsGenuineTokenUsage` 已在真机全绿**（`exit 0`、`OK (1 test)`、15.6s 跑完、OEM 未触发），落盘 JSON 拉回并记了 SHA-256；⚠️ ~~**其余 4 条仍未全绿**~~ ✅ **第七批（HEAD `86e88970d`）已订正：那 4 条全绿了**（四条退出码 `0` / 均 `OK (1 test)` / Time 2.142 / 1.746 / 3.896 / 1.842），见「C1 真机证据采集第七轮」；当时 `:app:connectedDebugAndroidTest` 仍因 `BrowserRuntimeTest` 的 Coil 单例崩溃**在 9 过 1 挂后中止**（`exit 1`）。详见「C1 真机证据采集第四轮」<br>✅ **第七批订正（HEAD `86e88970d`，2026-10-06）**：**这 5 条现在每一条都在真机绿过一次**（第 5 条真实网关 / 前 4 条 mock，四条退出码 `0`、全 `OK (1 test)`）。⚠️ **但 `:app:connectedDebugAndroidTest` 仍未全量跑通**，且**十行状态列仍 10/10 `unverified`** |

⚠️ **第五个窗口唯一新增的缺口形状**（**不属于上面四类，是四类覆盖不到的那些行**）：

| # | 缺什么 | 卡在哪 |
|---|---|---|
| 7 | **「残留旧 job」那两条路径的真机复现** | 要在同一会话里**人工编排时序**：一条长生成在飞 → 期间发新消息把它判死 → 等旧 job 跑完（或反过来先超时）。⚠️ 本轮零设备，**从未复现过**；路径 B（用户取消 → 残留 job 进 `onSuccess`）**连 JVM 单测层面都没有确证**。详见「已知遗留与风险」第 24 条 |
| 8 | **`abandonDanglingGroupRuns` 的 `limit = 8`** | 契约说「任何群聊轮次都不会永久悬挂」，但取挂着的 `RUNNING` 行时写了 `limit = 8`，**第 9 条起不会被判死**。⚠️ **未修，且未核实单会话能否真的堆到 9 条并发 `RUNNING`**——别把它读成「已确认会永久悬挂」，见第 23 条 |

## 判定规则

- JVM 纯函数测试可证明过滤、路由、预算和幂等逻辑；不能替代真机 UI、备份恢复或
  Tavern 本体互操作证据。
- `unverified`、缺命令退出码、缺可见消息集合、缺调用序列或缺哈希的行均不算通过。
- 任一安全约束失败（越权消息、回退到全局记忆、导出密钥、重复提交）直接阻断该
  子包交付，先修复再重新记录。
- ⚠️ **本轮补的第四条（2026-10-05，第四个窗口之后加）**：**「真实 provider」本身不等于
  「这一行该判通过」**。第四个窗口已经拿到了**真实模型**的真实 token 与真实调用序列，
  那十行**仍然全部 `unverified`**，因为同时踩了三条——
  ① **该用例没有一次全绿记录**（跑成功了但进程随后被杀、落盘产物没留下，数字只存在于
  内存快照，**不可复算**）；② **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid
  反查，app 不保存响应 `model`——⚠️ **这句的成因已在 HEAD `74d82476` 修掉，见第八条**）；③ **这一行点名的那条路径这一轮没被真实调用过**
  （显式 @ 收窄 / 取消 / 超时 / 预算截断 / 失败续跑 / 记忆空间 / roundtable / vote
  / Tavern 本体 / 真机 UI）。
  换句话说：**「拿到了真实数字」只填列，「这一行该判通过」要每列都对应到本行点名的
  那条路径，且产物可复算。**
- ⚠️ **本轮补的第五条（2026-10-06，第五个窗口之后加）**：**「修掉了真缺陷」也不等于
  「这一行该判通过」。** 第五个窗口修的是三个**当时真的还在漏**的缺陷（平票脚手架在
  两条终态出口上漏回收、残留旧 job 跨轮盖戳 / 跨轮写 `FAILED`），并配了 **29 条 JVM
  用例**（其中 **12 条是真单测**）、**13 次变异检验全部真红**。那十行**仍然全部
  `unverified`**，因为同时踩了两条——
  ① **「代码不再有这条错路」不是契约 `:206` 点名的四类产物**，本轮**零设备、零
  `adb`**，四类产物**一份未增**；② **那两条残留旧 job 路径从未在真机复现过**，
  触发前提本身只是**读代码 + 核实 `cancelJobs()` 调用点**得到的，不是观测。
  换句话说：**回归护栏证明的是「我们钉住了那条不变量」，验收证明的是「契约那四类产物
  在真机上被采到了」——前者永远不能顶替后者**，哪怕两边的用例数加起来很好看。
- ⚠️ **本轮补的第六条（2026-10-06，第六个窗口之后加——这是本轮最重要的一条）**：
  **「拿到了真实 token、真实调用序列、而且那条用例真机全绿了」仍然不够。**
  第六个窗口把 `realProviderRoundRecordsGenuineTokenUsage` 跑到了
  `am instrument` **退出码 0** / `numtests=1` / `Time: 16.002` / `OK (1 test)`，
  真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ = 20780 与落库 `spent_tokens`
  精确相等**，三份产物拉回主机并记了 SHA-256——**这是十例 0/10 的最后一道锁破了**。
  **十行状态列仍然一个格都没动**，因为同时踩了**五条**——
  ① **模型名不是 wire 级抓包**（`message.modelId` 的 uuid 反查，app 不保存响应 `model`——
  ⚠️ **这句的成因已在 HEAD `74d82476` 修掉，见第八条；硬理由本身仍未消**）；
  ② **真机 UI 端到端零份**：那轮只验了**群配置面板的渲染与滚动**，
  **成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**，面板里的
  **保存 / 二维码 / 扫码三个业务动作一个都没点**；
  ③ **酒馆本体零份**；④ **相机扫码零份**；
  ⑤ ~~**`C1LiveModelSequenceTest` 其余 4 条仍未真机全绿**（只跑了目标用例那一条）。~~
  ✅ **第七批（HEAD `86e88970d`）已消**——四条逐条单跑，退出码全 `0`、输出全 `OK (1 test)`。
  ⚠️ **这一条的操作化形式**：「四类产物有没有」不是判据，**「这一行点名的那条路径，
  在这一类产物里有没有被真的调用过」才是**。第四轮的证据是**整轮 pipeline**，
  而 C1-01 / 03 / 04 / 05 / 06 / 07 / 08 / 09 / 10 要的路径**一条都没被它调用**。
  换句话说：**「全绿」是对「那条用例」的结论，不是对「这一行」的结论。**
- ⚠️ **本轮补的第七条（同一窗口）**：**「修了真机崩溃」也等于零验收证据。**
  第六个窗口修了两个真机必崩（`PrimaryBottomSheet` 漏传图标 / 弹层滚动容器套两层）
  并真机复验过（`logcat -b crash` 为空、15 个字段全渲染、5 滚 3 反不崩），又修了那条
  稳定复现的 Coil 单例崩溃。但**两个 UI 崩溃与 Coil 崩溃都不产出契约 `:206` 点名的
  任何一类产物**，而且四条边界没消：**Miuix 未验 / 62 个调用点未逐个上真机 /
  面板业务动作未点 / Coil 修复未在设备上复跑**。
  ⚠️ **顺带一条方法论**（本轮踩到，值得单独记）：**源码文本护栏扫描 Compose 调用点
  不能靠括号配平**——`content` 这类**尾随 lambda 的大括号在右括号外面**，第一版护栏
  整段漏扫内容体，**主断言因「谁都不违规」而恒绿**。**每批源码护栏都必须自带一条
  「扫描器仍能找到东西」的反空跑断言**，否则扫描范围一旦被改窄，整批断言会静默变绿
  （同一个坑在 Coil 护栏里以另一种形式复发了一次：只认 `(` 会漏掉
  `setSingletonImageLoaderFactory { context -> … }`）。
- ⚠️ **本轮补的第八条（2026-10-06，HEAD `74d82476` 之后加）**：**「缺陷已定位并修复 +
  零设备证据齐全」仍然不等于这条硬理由被消掉。**
  前七条讲的都是「证据不够」；这一条讲的是**另一种不够**：**能力有了，采集没做**。
  `StreamChunkHandler` 丢弃 `chunk.model` 的缺陷已修（wire 名现落
  `UIMessage.wireModelName`），JVM 侧 9 例 + 真实网关 2 个模型 HTTP 200 都有，
  `C1LiveModelSequenceTest` 也已改成 wire 优先并加了「不允许两者皆空」的防退化断言。
  ⚠️ **但仪器测试一行都没跑过**（设备离线），这些断言**只有编译验证**；
  **设备上那份证据文件里记的仍是 UUID 反查值**——**没有任何真机数字**。
  ⚠️ **并且要防一个更容易犯的错**：wire 名与请求名一致，说明**原先反查出的名字恰好是
  对的**。所以这**不是「反查错了被纠正」，是「取得途径从反查变成 wire 直取」**——
  **不构成对既有证据的追溯性升级**。谁要是把它写成「证据已升级成 wire 级」，
  就是把「能力已具备」误当成「证据已采集」。
  **操作化形式**：判据不是「代码里现在能不能拿到 wire 名」，
  而是「**设备上那份落盘 JSON 里的 `wire_model_name_provenance` 是不是
  `wire_response_model`**」。是 ⇒ 可以划掉；还是 `uuid_reverse_lookup_fallback` ⇒ 没划掉。
  ⚠️⚠️ **第七批（HEAD `86e88970d`）订正这条判据的适用范围**：真机把那 4 条跑完之后发现，
  **`wire_model_name_provenance` 这个字段在 mock 证据 JSON 里根本不存在**
  （`grep -c provenance` = **0**）——它**只有真实网关那份证据的写出器才落**
  （`C1LiveModelSequenceTest.kt:1933`），mock 的两个写出器（`:1325` / `:1540`）**只落
  `wire_model_name`**。⇒ **这条判据只对 `c1-live-evidence-real-provider.json` 适用**；
  对 mock 证据要改看两件事：① `wire_model_name` 非空且**不是**本地 UUID 反查值
  （第七轮实测是 `mock-model-a/b/c` 这种服务端自报串）；② `bindings[]` 里的
  `model_string_sent_on_wire` 与之逐条相等。
  **判据要按证据种类分两套，不能拿一套套两处**——这正是本条自己说的「能力 vs 采集」，
  连判据都得按证据种类分。
- ⚠️ **本轮补的第九条（2026-10-06，第八批之后加——「消除了一个失败模式」也不等于
  「观测到并修掉了一个故障」）**：
  第八批（`be7952a83` / `19545e076`）定位并堵上了一个**真的还在漏**的缺陷：
  `parseCandidates` / `parseBallot` 用 `lineSequence()` 切行（**只切 3 个分隔符**），
  而 Java `Pattern` 的行终止符是 **6 个** ⇒ 模型一旦输出 `U+2028` / `U+2029` /
  `U+0085`，声明行正则**必然失配** ⇒ **静默丢候选 / 静默丢票**。零设备下钉得很实：
  **6 条新用例 + 3 处反向验证（6 / 6 / 2 条真红）+ 6 条既有断言只改 2 条**，
  且 `:app:testDebugUnitTest` **111 类 / 921 例 / 0 失败 0 错误 0 跳过**。
  ⚠️ **那十行仍然全部 `unverified`**，而且这一批比前八条更要小心，因为**它同时踩了
  两个新的坑**——
  ① **零设备**：本轮**没跑任何 `androidTest`**，四类产物**一份未增**；
  ② ⚠️⚠️ **「输入里出现过这个字符」不等于「模型会输出这个字符」**：探针的输入是
  **手写**的，**真实模型是否真的会输出 `U+2028` / `U+0085`，本次没有任何实证**。
  ⇒ 这一批修的是「**一旦出现就静默丢票**」这个**失败模式**，
  **不是**「模型经常这么输出」。**把它读成「这条路径在生产里高频出问题」，
  是把「已消除的失败模式」误当成「已观测的故障率」。**
  **操作化形式**：判据不是「探针能不能造出丢票的输入」，而是「**真实模型的输出里有没有
 出现过这类字符**」——那需要一份真实响应原文（真实网关或真机落盘 JSON）。
  ⚠️ 同理，**「假阳性率下降」本轮也没有实测依据**（那处收紧方向上是失败关闭，
  但下降了多少没测）；**`unicodeLines` 的大文本性能也没测**。
  ⚠️ **规则本身照旧适用**：护栏证明的是「我们钉住了那条不变量」，
  验收证明的是「契约那四类产物在真机上被采到了」——**前者永远不能顶替后者**。
- ⚠️ **本轮补的第十条（2026-10-06，第八轮之后加）：「上一轮的失败这一轮不复现」既不是「已修复」，也不是「无此缺陷」；「把静默失败变响亮」更不是修复本身。**
  真实网关角色 c 上一轮那次「非 2xx + 空 body 被静默当零产出成功」的失败，本轮**两次默认预算运行都没有复现**（执行者报告；可核产物是 16:58 的一跑）——正确表述是「**偶发、未复现**」：它**不证明「缺陷已消失」**，也**不证明「缺陷不存在」**。
  ⚠️ **必须防的误读**：`43d607bdd` 只把静默失败改成 fail-loud，**没有加任何重试**；**谁把它写成「c 现在能成功的原因」，就是把「让失败可见」误当成「消除失败」。**
   **操作化形式**：判据不是「这一轮过没过」，而是「**有没有留下失败现场**（HTTP 状态码 / 响应体 / 日志）」——本轮**一个状态码都没有**，所以这条缺陷的触发条件**至今零实证**；下次再遇到要当场抓 `adb logcat` + 失败响应的状态码与 body，再谈「修没修」。
   > ⚠️⚠️ **第十四轮订正（HEAD `b60aa80c5`，2026-10-06）：「不证明缺陷消失」与「触发条件零实证」这两条仍然适用，但「偶发」的结论被本批推翻（原文保留）。** 本批 **6/6 稳定复现**「第 3 位角色零产出」：roundtable 3 + pipeline 3，全部 `turn_kind=error` / 正文「本轮没有产出内容」/ `model_id=null` / usage 全 `-1` / `group_run=FAILED/role_failed` / `app_errors=[]`。⚠️ **判据分两套，别混**：① 本条的操作化判据（有没有 HTTP 状态码 / 响应体）本批**仍然是零份**（raw dump 无状态码字段）⇒ **「上一轮那个非 2xx 现场」的触发条件至今仍零实证**，本订正**不动**这一条；② 但**「零产出」这个下游症状**不再偶发——它由**另一条触发**（网关 HTTP 200 + SSE 无 content delta）稳定产生，6/6。⇒ 正确表述从「偶发、未复现」改成「**上一轮的非 2xx 现场未复现（仍零实证）；但零产出症状另有稳定触发，6/6 复现**」。真因与 `app_errors` 采集盲区见「C1 真机证据采集第十四轮」③。
