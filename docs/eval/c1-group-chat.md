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
| C1-01 | 三角色显式 @ | 只被 @ 角色收到该消息；其他角色不可见 | `GroupTurnCoordinatorTest` 72<br>`GroupChatTest` 22<br>`GroupRoleCompletionProviderTest` 8 | `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**显式 @ 的投递收窄**——夹具的 `@` 仍落在用户消息上（用户消息对每个 viewer 都可见），「其他角色可见」这半句在真机证据里仍然读不出来。详见「已知遗留与风险」第 15 条。** |
| C1-02 | 无 @ 的 pipeline | 按 `roles[]` 顺序串接；实际模型调用序列与日志一致 | `GroupTurnCoordinatorTest` 72<br>`GroupChatTest` 22<br>`GroupTurnModelTest` 14 | `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**无 @ pipeline 的真实调用序列与日志一致**——真实网关那轮确实跑了 pipeline，但「一致」只做到「落库/内存里的 `modelId` 与绑定一致」，**没有拿运行日志做对照**，而模型名本身又不是 wire 抓包（硬理由①）。** |
| C1-03 | roundtable | 全员完成后仅 `chair_role_id` 汇总；议长前看不到未完成输出 | `GroupTurnCoordinatorTest` 72<br>`GroupChatTest` 22<br>`GroupSpeakerResolverTest` 8 | `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**roundtable 的议长汇总轮**——真实网关只跑了 pipeline，议长汇总轮的真实调用、议长前看不到未完成输出这两半**零份**。** |
| C1-04 | vote | 仅接受结构化候选/票；多数决可复算，平票按配置失败 | `GroupTurnCoordinatorTest` 72<br>`GroupChatTest` 22 | `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**vote 的三张选票、`parseBallot → __summary__`、以及平票按配置失败那条路**——**真实调用零份**（真实网关那轮跑的 mode 是 pipeline）。** |
| C1-05 | 轮次预算 | prompt+completion 达上限后停止剩余角色；日志含已用/上限/未运行角色 | `GroupTurnCoordinatorTest` 72<br>`GroupChatTest` 22<br>`GroupRunSchemaTest` 10 | `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**预算截断路径**——真实网关那轮 `token_limit=100000`，**整轮没触发截断**，「已用 / 上限 / 未运行角色」三个数在真实调用下仍然零份。** |
| C1-06 | 取消与超时 | 不写入未生成消息；已生成消息和错误节点保留 | `UngeneratedMessageFilterTest` 18<br>`GroupTurnCoordinatorTest` 72 | `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**取消与超时**——真实网关那轮是正常跑完的（`status=COMPLETED`、无错误节点），取消/超时路径仍然零份。** |
| C1-07 | 失败续跑/幂等 | 同 `round_id` 重试跳过已提交 turn；不重复消息 | `GroupTurnCoordinatorTest` 72<br>`GroupRunSchemaTest` 10<br>仪器 `GroupRunDAOTest` 12 条**未跑** | `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**同 `round_id` 重试跳过的幂等台账**——`C1LiveModelSequenceTest` 其余 4 条（含续跑那条）**仍未在真机全绿过**（硬理由⑤）。** |
| C1-08 | 记忆隔离 | 三个 `group:<conversationId>:role:<roleId>` 空间互不串；来源带消息 id | `MemorySpaceGateTest` 9<br>`MemoryToolScopeTest` 7<br>`MemoryAttributionTest` 9<br>`GroupMemorySpacePolicyTest` 6<br>`MemoryExtractorParseTest` 7<br>`MemoryRoleIdMappingTest` 2<br>`GroupRunSchemaTest` 10 | `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**三个 `group:<conv>:role:<role>` 空间的检索可见性 + `source_message_id` 归因**——**零份**，本轮这三项一条都没碰。** |
| C1-09 | Tavern/QR 往返 | 群配置、角色卡、role/轮次/分支哈希一致；不含密钥、记忆、授权 token | `GroupTavernExportTest` 22<br>`TavernCompatTest` 21<br>`QrScannerSheetTest` 16<br>`GroupChatTest` 22<br>`C1pGroupExportHashTest` 5 | `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**酒馆本体打开群聊导出文件 + 相机扫码链路**——**两份都零**；⚠️ 本轮只验了群配置面板的**渲染与滚动**，面板里的**保存 / 二维码 / 扫码这三个业务动作一个都没点过**。** |
| C1-10 | 单聊/群聊共存 | 同一列表混排；类型筛选只过滤；切换后消息与会话数据不丢 | `ConversationListQueryPlanTest` 7<br>`ConversationTypeFilterSourceGuardTest` 2<br>**`ConversationSearchLikePatternTest` 12**<br>`GroupChatTest` 22 | `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**成员头像组 / @ 选择器 / 抽屉筛选 chip 的真机 UI 端到端**——**一项都没做**（硬理由②）；主机侧那 438 条断言是 SQL 语义，替代不了 UI。** |

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
| C1-01 三角色显式 @ | `4534f02a` 新增 GroupTurnCoordinator 纯判定内核<br>`be41fdc9` 补 63 条内核用例（含视角隔离）<br>`6c8d4932` 新增 GroupRoleCompletionProvider<br>`e5dde832` 补 8 条防伪 @ 边界用例<br>（4 个 SHA 均在锚定区间 `d45ebd10~1..1b0e04a9` 内，已 `git cat-file -t` 逐个核过） | `CMD-1` 退出码 **0**<br>2026-10-04 23:48 强制 `--rerun`，84 个 XML 全部重写<br>`CMD-2` 退出码 **0**（该例无 lint 命中，见下）<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**。`adb devices` 输出为空列表；磁盘上唯一 app 仪器测试记录是 2026-10-03 12:58:29 的 `Process crashed`（`tests="0"`），早于基线 13.5 小时；Android 版本无记录<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。| 输入 = JVM 测试类：<br>`feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>`feature.chat.GroupRoleCompletionProviderTest`（8）<br>fixture 为 Kotlin 内联构造的 `GroupConfig`/`UIMessage` 列表，无外部文件 | **无证据（需真机）**。JVM 只断言 `viewerMessages(...)` 返回的 `UIMessage` 列表内容，没有把断言里的消息落成带真实 `message.id` 的台账，磁盘上没有「角色 A 可见 = [id1,id3]」这种记录<br>**2026-10-05 夹具层台账（部分证据）**：真机真 Room 库，夹具 **7 条消息**（作者依次 user / a / user / a / b / c / `__summary__`），`a9d2077e` 起**三种 mode 各采一遍**，越权审计三 mode 全部 `passed=true` / `checked_pairs=6` / `violations=[]`。pipeline：`a` 见 user,user,a,a,`__summary__`；`b` 见 user,user,a,b,`__summary__`；`c` 见 user,user,b,c,`__summary__`。roundtable：`c`（chair，`chairRound=true`）见 user,user,**a,b**,c,`__summary__`（议长放开本轮全部）。vote：收窄成 user,user,自己,`__summary__`。<br>**2026-10-05 真实 HTTP 台账**：`round-7098bf9e-4bcb-41f2-a0e7-e05d0395fcc9`，`a`→`7098bf9e…`(user)+`cc3d451c…`(a)；`b`→`+1f69a5bc…`(b)；`c`→`7098bf9e…`+`1f69a5bc…`(b)+`49e3ffcb…`(c)。<br>⚠️ **「只被 @ 角色收到、其他角色不可见」这一句仍未在真机上单独观测到**：夹具的 `@阿尔法` 落在**用户消息**上（`mentionRoleIds` 含 a），而用户消息对**每个 viewer 都可见**，所以 @ 放行分支的独立效果仍只有 JVM 纯函数断言 <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。| **无证据（需真机）**。仓库里被 git 跟踪的 `*.jsonl` 全部在 `ai/src/test/resources/stream-traces/generated/`（10 个文件），是 AI SDK 的桩事件流，与 C1 无关；没有真实 provider 的调用序列<br>**2026-10-05 真实 HTTP（第一次有真请求）**：`C1LiveModelSequenceTest` 经 `adb reverse tcp:8765 tcp:8765` 接本机 mock OpenAI，走 `ChatManager.sendMessage → GenerationLoop → ProviderManager → OpenAIProvider → ChatCompletionsAPI.streamText → Ktor CIO OkHttpClient` **完整生产链路**；请求特征 `User-Agent: ktor-client`、`accept: text/event-stream`、`stream_options.include_usage: true`、`stream=true`。**seq=1 `mock-model-a` / case main / 发言者 A / 2 条消息 / 201+34 / 只含 `ROLECODE:A`——零个他人输出。**mock 侧把每个请求 body 原样落盘 `requests.jsonl`，**与应用代码无关**，构成第三方旁证。⚠️ 显式 @ 的**单角色投递**没实跑（本轮是无 @ 的 pipeline），C1-01 要的「这一轮只有 B 被调用」仍零份 <br>**2026-10-05 第四个窗口：仍然是零份**——真实公网网关那轮跑的是**无 @ 的 pipeline**，`mode` 只有 pipeline 一种，本例要的那条路径**没有被真实调用过**。| **无证据（需真实模型）**。`GroupTurnCoordinatorTest` 的预算断言喂的是构造出来的 token 数，不是真实 completion 计数<br>**2026-10-05 真实 usage（第一次不是构造值）**：seq1 prompt **201** + completion **34** = **235**，落 `c1-live-evidence-main.json`，`evidence_kind=real-http-capture-via-adb-reverse`。⚠️ **本例要的是「只 @ 到一个角色」那一轮的 token 累计，那一轮没实跑**；且 usage 是 mock 按 `ceil(bytes/4)` 估的（**原始字节数已落盘可手算复核**），**不是真 tokenizer 的结果** <br>**2026-10-05 第四个窗口：仍然是零份**——本例要的是「只 @ 到一个角色那一轮」的累计，真实网关那轮是无 @ 的 pipeline。⚠️ 顺带纠正一处旧认知：`GroupConfigSheet` 的 `role_id` 是**只读 `Text`**（`GroupChatPage.kt:754`，在 `RoleEditor` 里），**UI 手输路径不存在**，所以「构造一个 `role_id = __summary__` 的角色」只能走外部 JSON 导入（`importShare` 第 5 道闸门，`GroupChat.kt:832-838`）。| **无证据**。C1-01 不产出群聊导出文件；`app/build/outputs/apk/debug/*.apk` 的 SHA-256 是安装包哈希，与「群聊导出文件哈希」不是一回事，不能填进本列 | `app/build/test-results/testDebugUnitTest/TEST-heizige.kk.khatkit.app.feature.chat.GroupTurnCoordinatorTest.xml`<br>`…GroupChatTest.xml`<br>`…GroupRoleCompletionProviderTest.xml`<br>主源码：`app/src/main/java/heizige/kk/khatkit/app/core/data/model/GroupChat.kt` <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件现为 **1982 行 / 5 个 `@Test`**）；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。| `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**显式 @ 的投递收窄**——夹具的 `@` 仍落在用户消息上（用户消息对每个 viewer 都可见），「其他角色可见」这半句在真机证据里仍然读不出来。详见「已知遗留与风险」第 15 条。** |
| C1-02 无 @ 的 pipeline | `4534f02a` 新增 GroupTurnCoordinator<br>`dd7b3c79` ChatManager 接上协调器 + `group_runs` 运行日志<br>`b752a310` 更新 ChatScaffold KDoc（modelId 已参与解析）<br>`139a91ba` 图片导出的模型名与气泡同口径 | `CMD-1` 退出码 **0**（同上，强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`GroupTurnModel.kt` lint 零命中）<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**，同 C1-01（`adb devices` 空；唯一仪器记录 2026-10-03 崩溃且 `tests="0"`）<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。| 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>`feature.chat.GroupTurnModelTest`（14） | **无证据（需真机）**。同 C1-01：只有纯函数返回集合的内容断言，没有真实 `message.id` 可见台账<br>**2026-10-05 夹具层台账**：`a9d2077e` 三 mode 各采一遍，越权审计三 mode 全 `passed=true` / `checked_pairs=6` / `violations=[]`；pipeline 下 `a`=pred=null、`b`=pred=a、`c`=pred=b，`c` 见 user,user,b,c,`__summary__`。<br>**2026-10-05 真实 HTTP 台账（本例最硬的一块）**：pipeline 真跑完，`assistant_role_order=["a","b","c"]`，每个角色**恰好只多看到上一位的输出**——`a`→`7098bf9e…`(user)+`cc3d451c…`(a)；`b`→`+1f69a5bc…`(b)；`c`→`7098bf9e…`+`1f69a5bc…`(b)+`49e3ffcb…`(c)，**c 看不见 a** <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。| **无证据（需真实 provider）**。契约要求「实际模型调用序列与日志一致」，JVM 侧只能断言 `SpeakerStep` 顺序，没有一次真实请求的 provider/model/顺序记录可导出<br>**2026-10-05 真实 HTTP 序列（契约点名的那一列，第一次有真请求）**：<br>seq1 `mock-model-a` / main / A / 2 msgs / 201+34 / `ROLECODE:A`<br>seq2 `mock-model-b` / main / B / 3 msgs / 235+34 / `ROLECODE:B`,**`A`**<br>seq3 `mock-model-c` / main / C / 3 msgs / 235+34 / `ROLECODE:C`,**`B`**<br>seq4 `mock-model-a` / budget / A / 2 msgs / 201+35 / `ROLECODE:A`<br>**契约要的序列 = `mock-model-a` → `mock-model-b` → `mock-model-c`**，与 `bindings` 声明的 wire 串**逐字一致**；`round_id=round-7098bf9e-4bcb-41f2-a0e7-e05d0395fcc9`、`assistant_role_order=["a","b","c"]`。<br>**逐请求视角隔离审计（真实网络层，不是纯函数判定）**：`a` 只有 system(`ROLECODE:A`)+user，**无 b 无 c**；`b` = system(`ROLECODE:B`)+user+**一条带 `ROLECODE:A` 的 assistant**，**无 `ROLECODE:C`**；`c` = system(`ROLECODE:C`)+user+**一条带 `ROLECODE:B` 的 assistant**，**无 `ROLECODE:A`**。**pipeline「只串联上一位」在真实 HTTP 层成立，不只在 `buildContext` 的单元判定上成立。**⚠️ **mock 不是真实模型**——证据等级是「真实 provider 代码路径 + 真实 HTTP 请求 + 真实 SSE 流 + 真实 usage 报文」，**不是「真实 LLM 推理」** <br>**2026-10-05 第四个窗口（真实公网网关，本例最硬的一块）**：**实际模型调用序列 = `deepseek-v4-flash` → `glm-5.2` → `deepseek-v4-flash`**，与按角色绑定（a/c = `deepseek-v4-flash`、b = `glm-5.2`）**逐位一致**；三个请求都**真的打到公网**（设备直连 `https://api.zenneko.top/v1`，**不经 `adb reverse`、不经本机 mock**）。这直接证明 `resolveGroupTurnModelId` + `TaskRoutes.resolve` 的按角色选型在**真实调用**下成立。⚠️ **模型名不是 wire 级抓包**：序列里的名字是由 `message.modelId` 的 uuid（`5a86b2d6…` / `8b6bf21c…`）经 provider 模型表**反查**出来的，**app 不保存响应的 `model` 字段**；JSON 里已用 `wire_model_name_provenance` 字段显式标明这一点。⚠️ **该用例没有一次全绿记录**（进程被 OEM 回收策略杀掉、落盘文件被卸载清空），数字来自同一次成功生成的内存快照。| **无证据**。pipeline 的 prompt+completion 实际计数未采集<br>**2026-10-05 真实 usage，与落库对账相等**：main 轮各请求 235 + 269 + 269 = **773**，落库 `group_runs.spent_tokens` = **773**，✅ 逐条相等；`status=COMPLETED`、`token_limit=100000`、`committed_role_ids=["a","b","c"]`、`skipped_role_ids=[]`、`reason=""`、`endedAt` 非空。⚠️ 口径是 **prompt + completion 累计**（`GroupTurnCoordinator.usageOf`），**不是** `totalTokens`、也不是最后一条消息的用量。mock usage = `ceil(bytes/4)`，字节数已落盘；seq1 `Content-Length: 24786`（绝大部分是 tools 定义）而 prompt token 只 **201** <br>**2026-10-05 第四个窗口（真实 usage，不是任何估算）**：a `deepseek-v4-flash` prompt **6803** + completion **159** = **6962**；b `glm-5.2` **6667 + 68 = 6735**；c `deepseek-v4-flash` **6880 + 102 = 6982**；**Σ(prompt+completion) = 20679**，落库 `group_runs.spent_tokens = 20679`，**精确相等**；`status=COMPLETED` / `committed=[a,b,c]` / `skipped=[]` / `ended_at` 非空。⚠️ **prompt 6800 量级的来源已定位**：请求体 **25.7KB 里 24675 字节是 23 个工具的定义**（按同一 wire body 本机复现得 6800/6526/6814，与真机同量级）。⚠️ **两个模型都是推理模型**：`max_tokens` 给小了会 `finish_reason=length`、`content` 为空、token 全被 `reasoning_tokens` 吃掉，**给足配额才吐正文**。⚠️ 同上，**没有全绿记录**，也不是 wire 抓包。| **无证据**。pipeline 路径本轮不导出群聊文件 | `…TEST-heizige.kk.khatkit.app.feature.chat.GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>`…TEST-…GroupTurnModelTest.xml`<br>接线点：`app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatManager.kt:705-741`（群分支取 `GroupTurnEntry.Speak` 并按 `resolveGroupTurnModelId` 选模型） <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件现为 **1982 行 / 5 个 `@Test`**）；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。| `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**无 @ pipeline 的真实调用序列与日志一致**——真实网关那轮确实跑了 pipeline，但「一致」只做到「落库/内存里的 `modelId` 与绑定一致」，**没有拿运行日志做对照**，而模型名本身又不是 wire 抓包（硬理由①）。** |
| C1-03 roundtable | `4534f02a` 新增 GroupTurnCoordinator<br>`be41fdc9` 补 63 条内核用例<br>`ab5cddb9` 新增 GroupSpeakerResolver（说话者解成纯函数）<br>`d934c4d2` 气泡上方显示说话者 + 关掉群聊不适用的三个动作 | `CMD-1` 退出码 **0**（强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`GroupSpeakerResolver.kt` / `ChatMessage.kt` lint 零命中）<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**，同 C1-01<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。| 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>`feature.chat.GroupSpeakerResolverTest`（8） | **无证据（需真机）**。「议长前看不到未完成输出」只有集合内容断言，没有逐 viewer 的真实消息 ID 清单<br>**2026-10-05 夹具层台账（议长视角第一次有值）**：真机真 Room 库，三 mode 各采一遍。roundtable 下 `a`=pred=null、`b`=pred=null、**`c`=chairRound=true**，议长可见集合放宽成 user,user,**a,b,c**,`__summary__`（议长放开本轮全部）；非议长仍是 user,user,自己,`__summary__`；越权审计 `passed=true` / `checked_pairs=6` / `violations=[]`。<br>⚠️ **但这只是夹具层**：`chairRound=true` 那个放行分支**在真实 HTTP 层一次都没被触发**——真实调用只跑了 pipeline，**议长汇总轮的真实 prompt 组装仍零份** <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。| **无证据**。roundtable 的「全员完成 → 仅议长汇总」两段调用序列未实跑<br>**2026-10-05 部分证据（只有 pipeline）**：真实 HTTP 跑了 pipeline 那四条请求（见 C1-02 行）。**roundtable 的两段序列——「全员轮」与「议长汇总轮」——仍然零份**，因为真实 provider 只绑定了 `mode=pipeline` 一种配置。⚠️ 议长 `chairRound=true` 的**实际 prompt 组装**没有任何请求记录，mock 也**没返回过任何候选或汇总文本** <br>**2026-10-05 第四个窗口：仍然是零份**——真实公网网关那轮跑的是**无 @ 的 pipeline**，`mode` 只有 pipeline 一种，本例要的那条路径**没有被真实调用过**。| **无证据**<br>**2026-10-05 仍缺（只跑了 pipeline）**：pipeline 轮 773 已落库（见 C1-02 行），但 **roundtable 议长汇总轮的真实 usage 零份**——议长那次调用根本没发生 <br>**2026-10-05 第四个窗口：仍然是零份**——议长 `chairRound=true` 那一轮的真实调用没有发生（真实网关只跑了 pipeline），真实 usage 也只有 pipeline 那三个数。| **无证据** | `…TEST-…GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>`…TEST-…GroupSpeakerResolverTest.xml`<br>主源码：`app/src/main/java/heizige/kk/khatkit/app/feature/chat/GroupSpeakerResolver.kt` <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件现为 **1982 行 / 5 个 `@Test`**）；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。| `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**roundtable 的议长汇总轮**——真实网关只跑了 pipeline，议长汇总轮的真实调用、议长前看不到未完成输出这两半**零份**。** |
| C1-04 vote | `4534f02a` 新增 GroupTurnCoordinator<br>`aa771637` `parseBallot` 截断选票前缀改忽略大小写（修小写 `vote:` 被静默丢票）<br>`be41fdc9` 补 63 条内核用例（含投票平票） | `CMD-1` 退出码 **0**（强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`GroupChat.kt` lint 零命中）<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**，同 C1-01<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。| 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>选票文本是测试内联的 `vote:` 前缀字符串 | **无证据（需真机）**<br>**2026-10-05 夹具层台账**：vote 下三个角色的 `predecessorId` 与 `chairRound` **都为 null**，可见集合收窄成 user,user,自己,`__summary__`（隔离最紧）；越权审计 `passed=true` / `checked_pairs=6` / `violations=[]`。<br>⚠️ **只是夹具层**：vote 的真实调用零份——mock 目前返回 `[mock] CASE:…` 文本，`parseBallot` **认不出 `VOTE:` 前缀**，所以 `parseBallot → __summary__` 分支**根本没被真实请求走过** <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。| **无证据**。三角色各自投票请求的真实调用序列未采集<br>**2026-10-05 仍缺**：三角色各自投票请求的真实序列零份；平票按配置失败那条路径也零份。真实 HTTP 那轮 mock 返回 `[mock] CASE:…` 文本，**`parseBallot` 认不出 `VOTE:` 前缀**，所以投票分支**根本没被真实请求触发过** <br>**2026-10-05 第四个窗口：仍然是零份**——真实公网网关那轮跑的是**无 @ 的 pipeline**，`mode` 只有 pipeline 一种，本例要的那条路径**没有被真实调用过**。| **无证据**<br>**2026-10-05 仍缺**：vote 轮真实 usage 零份。⚠️ 上一轮那个 `spent=4096`（prompt **3000** + completion **1096**）/ `limit=400` 的数字**是构造输入**，测试 JSON 的 `token_source` 自己写着 `budget-accounting-only, no live LLM call`——**不能当真实用量读** <br>**2026-10-05 第四个窗口：仍然是零份**——vote 的三张选票与 `parseBallot → __summary__` 分支**仍然没被真实请求触发过**。⚠️ 旧理由（「mock 返回 `[mock] CASE:…`、`parseBallot` 认不出 `VOTE:` 前缀」）到这一轮已经换成「**真实网关那轮压根没跑 vote**」。| **无证据** | `…TEST-…GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>判定入口：`app/src/main/java/heizige/kk/khatkit/app/core/data/model/GroupChat.kt:605`（`parseBallot`） <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件现为 **1982 行 / 5 个 `@Test`**）；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。| `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**vote 的三张选票、`parseBallot → __summary__`、以及平票按配置失败那条路**——**真实调用零份**（真实网关那轮跑的 mode 是 pipeline）。** |
| C1-05 轮次预算 | `4534f02a` 新增 GroupTurnCoordinator<br>`dd7b3c79` ChatManager 接上协调器 + `group_runs`<br>`be41fdc9` 补 63 条内核用例（预算截断） | `CMD-1` 退出码 **0**（强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`GroupTurnModel.kt` / `GroupRunDAO.kt` lint 零命中）<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**，同 C1-01<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` | 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.model.GroupChatTest`（18）<br>`core.data.db.migrations.GroupRunSchemaTest`（10）<br>预算值是构造入参，不是真实 token | **无证据（需真机）**。预算截断时点「谁被停掉」只有角色 id 顺序断言，没有消息 ID 台账<br>**2026-10-05**：`a9d2077e` 三 mode 各采一遍，越权审计三 mode 全 `passed=true` / `checked_pairs=6` / `violations=[]`；budget 轮真机**只落 `a` 一条**（`cc3d451c…`），`b`/`c` **库里没有发言**——「谁被停掉」在消息层面可直接观测（那一轮 `case=budget`，见 token 列） <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。| **无证据**。「达到上限后停止剩余角色」要求日志与真实请求序列对齐，未实跑<br>**2026-10-05 真实 HTTP（部分证据）**：budget 轮 `token_limit=1` 真跑，**实际只发出 1 个请求**（seq4 `mock-model-a` / case budget / 发言者 A），与「达到上限后停止剩余角色」一致；`b`/`c` **没有发出任何请求**（不是发了被拒，是没发）。⚠️ 截断由 `limit=1` 这个**最小正值**触发，「跑满 N 个角色再截断」那条更接近生产的路径仍零份 | **无证据（本例是四条缺失里最硬的一条）**。契约 `:202` 的预算口径是「每轮累计 prompt + completion token」，`GroupTurnCoordinatorTest` 里的预算是**判定逻辑**的输入，不是真实 provider 返回的 token 数；`--rerun` 那轮 XML 里也没有任何 token 断言字段<br>**2026-10-05 真实 usage，与落库对账相等（本例最硬的一块）**：budget 轮 `token_limit=1`，**只发出一个请求**，prompt **201** + completion **35** = **236**，落库 `group_runs.spent_tokens` = **236**，✅ 相等；`status=BUDGET_STOPPED`、`reason=token_budget_exceeded`、`skipped_role_ids=["b","c"]`、`committed_role_ids=["a"]`、`token_limit=1`。**「已用 / 上限 / 未运行角色」三个字段全部有库内取值。**⚠️ `limit=1` 是「第一个角色之后必定停跑」的最小正值（`GroupChat.budgetDecision` 的口径是 `limit <= 0 <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。|<br>**2026-10-05 第四个窗口：仍然是零份**——真实公网网关那轮跑的是**无 @ 的 pipeline**，`mode` 只有 pipeline 一种，本例要的那条路径**没有被真实调用过**。| spent < limit` 才继续）；`run_token_persisted_before_call=true` 也在同一份证据里 <br>**2026-10-05 第四个窗口**：真实网关那轮 `token_limit` 足够大、**没有触发预算截断**，所以「达到上限后停止剩余角色」在**真实 usage 下仍然是零份**（mock 那轮的 `token_limit=1` 仍是唯一的截断实测，见上一窗口）。| **无证据** | `…TEST-…GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.data.db.migrations.GroupRunSchemaTest.xml`<br>运行日志表：`group_runs`（`app/src/main/java/heizige/kk/khatkit/app/core/data/db/entity/GroupRunEntity.kt`） <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件现为 **1982 行 / 5 个 `@Test`**）；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。| `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**预算截断路径**——真实网关那轮 `token_limit=100000`，**整轮没触发截断**，「已用 / 上限 / 未运行角色」三个数在真实调用下仍然零份。** |
| C1-06 取消与超时 | `60e9f055` 新增未产出助手消息过滤纯函数<br>`97b0fa1c` `finishGeneration` 落库前丢弃本次新增空气泡<br>`adc8a0ff` 补 18 条用例（空气泡丢弃/部分产出保留/存量不清洗）<br>`be41fdc9` 补 63 条内核用例（失败取消超时） | `CMD-1` 退出码 **0**（强制 `--rerun`）<br>`CMD-2` 退出码 **0**（`ChatManager.kt` lint 零命中）<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**，同 C1-01。这是**最需要真机**的一条：取消与超时只能在真实协程取消 + 真实流式响应下复现，JVM 只能测纯函数<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。| 输入 = `feature.chat.UngeneratedMessageFilterTest`（18）<br>`feature.chat.GroupTurnCoordinatorTest`（63） | **无证据（需真机）**<br>**2026-10-05 仍缺**：两轮真实 HTTP（main / budget）都是**正常跑完**的，**没有一次取消或超时**——取消/超时要在流式响应中途打断，本轮没做。所以本列**仍是零份**；JVM 那 18 条纯函数断言（`dropUngeneratedAssistantMessages`）**不能替代**真实流式响应下的取消 <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。| **无证据**。取消/超时发生在流式响应中途，没有真实 provider 的部分响应记录<br>**2026-10-05 仍缺**：两轮真实 HTTP 都**正常跑完**，**没有一次中途取消或超时**——「流式响应中途打断」这条链路（部分响应、已消耗 token、错误节点保留）仍零份 <br>**2026-10-05 第四个窗口：仍然是零份**——真实网关那轮**正常跑完**，**没有一次中途取消或超时**。| **无证据**。取消时已消耗的 token 无采集<br>**2026-10-05 仍缺**：两轮都正常跑完，**取消/超时时的已消耗 token 没采**（没有中途打断） <br>**2026-10-05 第四个窗口：仍然是零份**——没有中途打断，「取消/超时时的部分响应与已消耗 token」仍无采集。| **无证据** | `…TEST-heizige.kk.khatkit.app.feature.chat.UngeneratedMessageFilterTest.xml`<br>`…TEST-…GroupTurnCoordinatorTest.xml`<br>纯函数：`app/src/main/java/heizige/kk/khatkit/app/feature/chat/UngeneratedMessageFilter.kt:78`（`dropUngeneratedAssistantMessages`）<br>落库前调用点：`app/src/main/java/heizige/kk/khatkit/app/feature/chat/ConversationSession.kt:108`（`finishGeneration` 内） <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件现为 **1982 行 / 5 个 `@Test`**）；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。| `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**取消与超时**——真实网关那轮是正常跑完的（`status=COMPLETED`、无错误节点），取消/超时路径仍然零份。** |
| C1-07 失败续跑/幂等 | `d976aa61` 新增 `group_runs` 表与运行 token 幂等<br>`007e4173` 改复合主键 `(conversation_id, round_id)` + `run_token/updated_at`<br>`70ed043d` `GroupRunDAO` 按 `(conversationId, roundId)` 定位 + `upsertRun` 事务<br>`892577a3` 30→31 改显式 `Migration_30_31` 并在工厂注册<br>`cc01d78d` androidTest 迁移到复合主键/`run_token` 并补幂等与 upsert 用例<br>`be41fdc9` 补 63 条内核用例（幂等/续跑）<br>`1649f5c7` 迁移重放脚本补 FTS5 触发器存活证据与对照实验 | `CMD-1` 退出码 **0**（强制 `--rerun`，JVM 侧覆盖 app 模块全部 664 个用例，含本例相关的 `GroupTurnCoordinatorTest` 63 + `GroupRunSchemaTest` 10）<br>**仪器侧未跑**：`adb devices` 空，`connectedDebugAndroidTest` 无法执行<br>**C1-D 主机侧重放已跑**：`python3 tools/verification/c1d_migration_30_31_replay.py` 退出码 **0**（连跑两次输出逐字节相同）<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**。C1 相关仪器测试源码共 **19 个注解**（`GroupRunDAOTest` 12 + `Migration_30_31_Test` 7），**执行结果为零**。唯一 app 仪器记录 2026-10-03 12:58:29 崩溃且 `tests="0"`，早于基线<br>**部分证据（仅迁移侧）**：零设备主机侧重放已把 C1-D 的 30→31 迁移钉住 85 条断言 0 失败，覆盖 `group_runs` 复合主键 `(conversation_id, round_id)`、`run_token` UNIQUE 索引、同 `round_id` 第二条被主键拒绝、同 `runToken` 第二次被唯一索引拒绝、`group_runs` 仍只有 1 行（见「C1-D 迁移 30→31 主机侧重放」）。**这只是部分证据，不改状态**<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。| 输入 = `feature.chat.GroupTurnCoordinatorTest`（63）<br>`core.data.db.migrations.GroupRunSchemaTest`（10）<br>仪器侧 fixture 未采集：`app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/dao/GroupRunDAOTest.kt`、`…/core/data/db/migrations/Migration_30_31_Test.kt`（源码在库，执行证据不在库） | **无证据（需真机）**<br>**2026-10-05**：`a9d2077e` 三 mode 夹具台账与越权审计全过（三 mode `checked_pairs=6` / `violations=[]`）；仪器侧 `GroupRunDAOTest` 12 条**真机全绿**。<br>⚠️ **但「同 `round_id` 重试跳过已提交 turn」这条真机交互仍然零份**：本轮两轮真实调用都是**一次跑完**，没有制造「第 2 个角色失败后重试同一 `round_id`」的场景。⚠️ 反而是这轮真实调用**撞出一个真缺陷**（`858c11d0`），见「模型调用序列」列——**它证明的是「产出归属会断」，不是「续跑幂等已验」** <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。| **无证据**<br>**2026-10-05 部分证据**：`GroupRunDAOTest` 12 条真机全绿（覆盖 `run_token` 持久化、同 `round_id` 第二条被复合主键拒绝、同 `runToken` 被唯一索引拒绝、`upsertRun` 事务）。⚠️ 但那些是 **DAO 层**断言，**没有一次「失败后重试同一 `round_id`」的真实链路**。<br>⚠️ 反而这轮真实调用**撞出一个真缺陷并修掉**（`858c11d0`，`core/data/ai/GenerationLoop.kt`）：`role_id` 已提交时末尾助手消息被当成「本次生成自己的」而复用，导致后续角色的产出**并进上一位那条消息**，`stampGroupTurn` 随后找不到 `roleId == null` 的消息，把该角色误判成「本轮没有产出内容」写成 `role_failed`。真机证据：`role_id="a"` 的那条消息有 A、B 两段正文而 usage 是 B 的；`group_runs` 是 `status=FAILED` / `committed=["a"]` / `skipped=["c"]` / `reason=role_failed` / `error_message=本轮没有产出内容`。**这是锚点之后第一个由真机证据定位的 main 源码 bug** <br>**2026-10-05 第四个窗口：仍然是零份**——真实公网网关那轮跑的是**无 @ 的 pipeline**，`mode` 只有 pipeline 一种，本例要的那条路径**没有被真实调用过**。| **无证据**<br>**2026-10-05**：pipeline 773 / budget 236 两条 run 已落库（见 C1-02、C1-05 行），⚠️ 但**「重试同一 `round_id` 时 token 是否只算一次」这条零份**——本轮没有重试场景 <br>**2026-10-05 第四个窗口**：那一轮 `status=COMPLETED`、**一次跑完**，**没有制造「第 2 个角色失败后重试同一 `round_id`」的场景** → 本列**仍然是零份**。⚠️ 反而撞出**另一个真模型特有的坑**（首版 persona 没写「历史里别人的代号不是你的」，pipeline 把上一位发言放进下一位上下文后，**真模型照抄眼前那条的格式**，b 学走 a 的 `ROLECODE:A`、c 学走 b 的、c 干脆零产出 `role_failed`）：那是**产出格式归属**问题，**不是**幂等路径的证据。| **无证据** | `…TEST-…GroupTurnCoordinatorTest.xml`<br>`…TEST-…GroupRunSchemaTest.xml`<br>仪器记录（崩溃，早于基线）：`app/build/outputs/androidTest-results/connected/debug/TEST-PKG110 - 16-_app-.xml`<br>DAO：`app/src/main/java/heizige/kk/khatkit/app/core/data/db/dao/GroupRunDAO.kt`<br>主机侧重放脚本：`tools/verification/c1d_migration_30_31_replay.py`（sha256 `7cd9c3fb…8199c6`）<br>重放日志（仓库外）：`/tmp/opencode/c1-replay2/run1.log`、`run2.log`（sha256 `093981c4…4d2e7d5`）<br>变异测试驱动（仓库外）：`/tmp/opencode/c1-replay2/mutate.py` <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件现为 **1982 行 / 5 个 `@Test`**）；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。| `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**同 `round_id` 重试跳过的幂等台账**——`C1LiveModelSequenceTest` 其余 4 条（含续跑那条）**仍未在真机全绿过**（硬理由⑤）。** |
| C1-08 记忆隔离 | `966792d6` `memory_chunks` 加 `role_id` + 注册 `group_runs`（Room 30→31）<br>`63502610` `addMemory` 透传 `roleId` 且 `toModel` 带出发言角色<br>`8e5457dc` `getMentionsOfEntity` 改带 `spaceId` 的 JOIN<br>`e7606912` 检索层加 `MemorySpaceGate` 空间闸门（FTS/向量/图谱三路）<br>`68b92146` `forgetMemory/linkMemories` 加 `expectedSpaceId` 归属校验<br>`6504f023` 记忆工具接群空间（无全局/助手回退），群聊不下发 `recent_chats`<br>`2031c409` `MemoryExtractor` 写入带 `roleId`，`sourceMessageId` 按 `source_line` 归因<br>`495b5e3a` `ChatManager` 接群记忆作用域（懒建空间、只读 viewer 可见消息）<br>`58b129c7` 补 31 条用例（空间闸门/工具空间/抽取归属/懒建/跨空间泄漏回归） | `CMD-1` 退出码 **0**（强制 `--rerun`，覆盖本例 7 个类的 50 个用例）<br>`CMD-2` 退出码 **0**（`MemoryRepository.kt` / `MemoryExtractor.kt` lint 零命中）<br>**C1-D 主机侧重放已跑**：`python3 tools/verification/c1d_migration_30_31_replay.py` 退出码 **0**（连跑两次输出逐字节相同）<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**，同 C1-01。三空间互不串需要真实检索栈 + 真实写入，JVM 只能测纯函数闸门<br>**部分证据（仅迁移与 FTS 侧）**：主机侧重放已钉住 `memory_chunks` 加可空 `role_id`（存量 2 行 `role_id IS NULL` 不被改写、新行可写 `'r1'`）、`role_id` 索引存在、存量 `content`/`source_message_id`/`source_ref_id`/`confidence`/时间戳/`embedding` BLOB 全保留，且 3 个 FTS5 触发器在迁移后逐字节存活、INSERT/UPDATE/DELETE 三向真同步到索引（见「C1-D 迁移 30→31 主机侧重放」）。**这不覆盖三空间互不串本身，也不改状态**<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。| 输入 = `core.data.repository.MemorySpaceGateTest`（9）<br>`core.data.ai.tools.MemoryToolScopeTest`（7）<br>`core.data.repository.MemoryAttributionTest`（9）<br>`core.data.repository.GroupMemorySpacePolicyTest`（6）<br>`core.data.repository.MemoryExtractorParseTest`（7）<br>`core.data.db.MemoryRoleIdMappingTest`（2）<br>`core.data.db.migrations.GroupRunSchemaTest`（10） | **无证据（需真机）**。三个 `group:<conv>:role:<role>` 空间的真实检索结果可见性没有跨 viewer 的实跑记录<br>**2026-10-05 仍缺（这一列对本例不适用）**：viewer 可见消息台账量的是**对话消息**，而 C1-08 要的是**三个 `group:<conv>:role:<role>` 记忆空间的检索结果可见性**——本轮真实 HTTP 的 assistant 把记忆**全部关掉**（`enableMemory=false` / `useGlobalMemory=false` / `autoExtractMemory=false`），**一次记忆写入与检索都没发生**。所以本列**仍是零份** <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。| **无证据**。「检索 query 只用 viewer 过滤结果」要对照真实请求的 query 文本，未采集<br>**2026-10-05 仍缺（这一列对本例不适用）**：C1-08 要对照**真实请求的 query 文本**与三个空间的检索结果，而本轮真实 HTTP 的 assistant 把记忆**全部关掉**（`enableMemory=false` / `useGlobalMemory=false` / `autoExtractMemory=false`），**没有一次检索发生**，query 文本也无从对照。⚠️ 视角隔离那一半**已被真实 HTTP 审计钉住**（请求里只含 viewer 过滤后的 messages，见 C1-01 / C1-02 行），但**「检索 query 只用 viewer 过滤结果」这条仍然零份** <br>**2026-10-05 第四个窗口：仍然是零份**——真实网关那轮没有检索记忆，三空间互不串在真实调用下仍无观测。| **无证据**<br>**2026-10-05 仍缺（这一列对本例不适用）**：本轮真实 HTTP 把记忆全关，**没有一次记忆写入或检索**，「带 `source_message_id` 归因」无从采集 <br>**2026-10-05 第四个窗口**：pipeline 那三个数是**群聊轮次**的用量，**不覆盖**记忆空间隔离；本列**仍然是零份**。| **无证据**。契约 `:203` 要求记忆内容不进包，但没有导出文件可算哈希 | `…TEST-heizige.kk.khatkit.app.core.data.repository.MemorySpaceGateTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.data.ai.tools.MemoryToolScopeTest.xml`<br>`…TEST-…MemoryAttributionTest.xml`<br>`…TEST-…GroupMemorySpacePolicyTest.xml`<br>`…TEST-…MemoryExtractorParseTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.data.db.MemoryRoleIdMappingTest.xml`<br>`…TEST-…GroupRunSchemaTest.xml`<br>主机侧重放脚本：`tools/verification/c1d_migration_30_31_replay.py`（sha256 `7cd9c3fb…8199c6`），日志 `/tmp/opencode/c1-replay2/run1.log`、`run2.log`（仓库外） <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件现为 **1982 行 / 5 个 `@Test`**）；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。| `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**三个 `group:<conv>:role:<role>` 空间的检索可见性 + `source_message_id` 归因**——**零份**，本轮这三项一条都没碰。** |
| C1-09 Tavern/QR 往返 | `f921a02f` `GroupRole` 补 `extras` 无损往返 + `validate` 校 `schemaVersion`<br>`b3d9bf44` `TavernChatCodec` 加群聊导出/导入（保留 `role_id/round_id/turn_kind/群配置/角色卡`）<br>`efa1c27c` `encodeConfig` 写库路径补跑密钥黑名单检查<br>`53563c8c` 补 Tavern 群聊往返与密钥过滤用例<br>`a1616b6b` 群聊页接上生成二维码（载荷与文本分享共用一份）<br>`7d4a6596` 群聊页接上扫码导入，统一走 `importShare`<br>`f5212df2` 角色卡解码走 null 安全取值<br>`b548025e` 补 `importShare` 五道闸门与 `cards` 保留用例<br>`88c850ef` 群聊导出面板接入 Tavern 群聊导出卡片，打通 `exportGroupJsonl`<br>`2c9aa2e4` 补群聊导出入口纯逻辑用例（面板到可回导文件的往返）<br>`aeab5550` 新增 CameraX + MLKit 扫码弹层与入口决策单测<br>`d2b5c05c` 新增导出哈希证据测试（9 变体 + 往返幂等 + SillyTavern 结构对照）<br>`eef6efb3` 新增跨两次独立 JVM 的哈希比对脚本<br>`bbe2e558` 加 golden 清单护栏（把「确定性」升级成「格式没变」） | `CMD-1` 退出码 **0**（强制 `--rerun`，覆盖 82 个用例：22+21+16+18+5）<br>`CMD-3` 退出码 **0**（打出了 3 个可安装 APK，但那是安装包不是群聊导出文件）<br>`CMD-2` 退出码 **0**（`TavernChatCodec.kt` lint 零命中）<br>**C1-P 哈希校验已跑**：`python3 tools/verification/c1p_group_export_hash.py` 退出码 **0**（两次独立 JVM + 落盘 `hashlib` 复算 + golden 清单，三重全一致）<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**，同 C1-01。相机扫码（`QrScannerSheet`）与 Tavern 本体打开文件**都必须真机/桌面端**，JVM 的 16 条只测入口决策<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。| 输入 = `feature.chat.GroupTavernExportTest`（22）<br>`core.data.ai.tavern.TavernCompatTest`（21）<br>`core.ui.components.ui.QrScannerSheetTest`（16）<br>`core.data.model.GroupChatTest`（18）<br>**`core.data.ai.tavern.C1pGroupExportHashTest`（5）**<br>前四个类的往返在 JVM 里是内存对象 → JSON → 内存对象；新类把文件**真的落盘**（`build/c1p-group-export-hash/*.txt`）再算 SHA-256 | **无证据（需真机）**<br>**2026-10-05**：viewer 台账按三种 mode 各采了一遍（越权审计全过），但**本例要的「往返后 `role_id`/轮次/分支逐字段相等」靠的是导出列那 7 条消息的逐字段对账**，不是 viewer 台账。⚠️ **本例的实质缺口在导出与互操作**：酒馆（SillyTavern）本体打开群聊导出文件**零证据**、相机扫码真机链路**零证据**（`QrScannerSheet` 只有编译 + 16 条 JVM 单测，CameraX+MLKit 没在设备上跑过） <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。| **无证据**<br>**2026-10-05 部分证据**：导出/QR 往返走的是**真机** `TavernChatCodec.exportGroupJsonl` + `GroupChat.encodeQr`，三种 `mode` 各一份，越权审计三 mode 全过。⚠️ **这一列对本例不适用**（导出不发起模型请求），且酒馆本体互操作与相机扫码**真机链路零份** <br>**2026-10-05 第四个窗口：仍然是零份**——真实网关那轮不产出群聊导出文件。| **无证据**<br>**2026-10-05 仍缺（这一列对本例不适用）**：导出不消耗 token。⚠️ 契约 `:203` 的「记忆内容不进包」只能靠**逐字段扫导出文件**验，本轮没做机器扫描，只有零设备的密钥黑名单单测 <br>**2026-10-05 第四个窗口：仍然是零份**——同列，本轮只跑生成侧。| **部分证据（零设备，JVM 层确定性）**——契约 `:206` 这一类现在有真实内容了。9 个 fixture 变体的字节数 + SHA-256 已实测落定（`d2b5c05c` / `eef6efb3` / `bbe2e558`，逐条证据见下方「C1-P 群聊导出确定性哈希（零设备）」）：<br>`pipeline_3roles_2rounds_jsonl` 3615 / `36e6585f9aa4a028eb8277bc70578fd2c802cdd730e3981f0d52b02430f0c8b9`<br>`roundtable_3roles_2rounds_jsonl` 3616 / `33c51124e5421ae46a002f025a2e61dc5712b73ddc413ca9dea3b9777ccc0fb3`<br>`vote_3roles_2rounds_jsonl` 3619 / `ad9e3fb119cbc4bea05b7e917eb180aabeb230bbbf89dfe1addcfd0040217c35`<br>`pipeline_3roles_2rounds_array` 3617 / `b8d57a6c4f15e87d1a9d0a181022ba28410a36527075e71b0daf77976c1a3653`<br>`pipeline_with_explicit_create_date_jsonl` 3652 / `92ce04902dc0c8e5bd822020e3df885b30a89adb9fef2fcd0fb712ae9faeee5e`<br>`empty_messages_jsonl` 1518 / `0ae10ea537e112c7f4d98ebd275b26a86e1b30952f1de8274f0cccf670f11e80`<br>`image_part_jsonl` 1711 / `c07d7e6ead7f06143ceae05fa5082be04af1fabc333edb60125ee337c9cbd9e9`<br>`qr_payload_pipeline` 1348 / `2d65ec04dded8a8fc29d3b7cb2d235bea908ee779b0568a6f644f34670cd2ff5`<br>`qr_payload_vote` 1352 / `8b49d47b225f32f6ad6032b1ab49eb1d6122a3af3c97652936adf7df13c2e9bf`<br>⚠️ **但这是零设备 fixture 的哈希，不是真机导出的文件哈希**：落盘走 `java.io.File.writeBytes`，`writeExportTempFile` + `ACTION_SEND` 真实 IO 分发**一次没跑过**；**酒馆本体打开、viewer 可见消息 ID、模型调用序列、token 计数仍零份**。**四类证据缺三类半，所以状态不变。**<br>① APK 的 SHA-256 已有（见构建段），但**安装包哈希不是群聊导出文件哈希**，不能填本列；<br>② `/tmp/opencode/sample-group.json` / `.jsonl`（各约 1.7 KB）**被仓库零引用**（`git grep sample-group` 无结果）、无 SHA-256、来源不明，**不作为 fixture** | `…TEST-heizige.kk.khatkit.app.feature.chat.GroupTavernExportTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.data.ai.tavern.TavernCompatTest.xml`<br>`…TEST-heizige.kk.khatkit.app.core.ui.components.ui.QrScannerSheetTest.xml`<br>`…TEST-…GroupChatTest.xml`<br>**`…TEST-heizige.kk.khatkit.app.core.data.ai.tavern.C1pGroupExportHashTest.xml`**（`tests="5"`）<br>哈希校验脚本：`tools/verification/c1p_group_export_hash.py`<br>**golden 清单（入库，护栏本体）：`tools/verification/c1p_group_export_hash.golden.json`**<br>落盘产物（构建目录，未入库）：`app/build/c1p-group-export-hash/*.txt`<br>编解码：`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tavern/TavernChatCodec.kt`<br>C1-P 补的「QR 携带角色卡最小元数据」落库证据见下方「C1-P 角色卡元数据落库与 Room 31→32 迁移（零设备）」 <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件现为 **1982 行 / 5 个 `@Test`**）；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。| `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**酒馆本体打开群聊导出文件 + 相机扫码链路**——**两份都零**；⚠️ 本轮只验了群配置面板的**渲染与滚动**，面板里的**保存 / 二维码 / 扫码这三个业务动作一个都没点过**。** |
| C1-10 单聊/群聊共存 | `2f1d04a2` 搜索路新增按 `type` 的 DAO 查询，空串语义与未归档路对齐<br>`c84256a1` 抽屉类型筛选下沉到 SQL，删掉只作用于已加载页的内存过滤<br>`35ea0762` `type` 谓词抽成两条查询共用的常量<br>`34493507` 补抽屉列表查询判定与「内存过滤已删」的护栏用例<br>`8528ecda` 抽出 `ChatScaffold` 共用消息区骨架<br>`4e97ff57` 群聊页复用 `ChatScaffold`，接成员头像组、@ 选择器与群配置面板<br>**`215296f1..6982869b` 8 个 C1-S 提交**：抽屉两条 `@Query` 的主机侧重放（`215296f1`，起手 335 条断言 / `aa1862eb` 扩到 **438 条**）、**修两个真实缺陷**（`58faa90b` 5 条 LIKE 加 `ESCAPE` + 抽出转义纯函数、`e8607171` 5 个转发点统一过转义、`a252884e` 全部 14 条 `ORDER BY` 追加 `id ASC`）、`79b13080` 转义纯函数 12 条单测 + 2 条源码护栏、`eb29abff` ESCAPE 常量去尾随空格、`6982869b` type 筛选护栏的期望串改为引用 ESCAPE 常量标识符 | `CMD-1` 退出码 **0**（强制 `--rerun`，覆盖 27 个用例：7+2+18）<br>**本轮追加**：`./gradlew --offline :app:testDebugUnitTest --rerun` 退出码 **0**，**92 类 / 722 例 / 0 失败 0 错误 0 跳过**（改前 91 类 / 710 例，净增 `ConversationSearchLikePatternTest` 12 例）<br>**C1-S 主机侧重放已跑**：`python3 tools/verification/c1s_conversation_type_filter_replay.py` 退出码 **0**（两次独立运行，输出逐字节相同，输出 SHA-256 `5645fa13…a40073`）<br>`CMD-2` 退出码 **0**（`ChatList.kt` 侧现在零命中——曾有的 3 条 `FrequentlyChangingValue` 已由 `366b3fe8` 搬进 `derivedStateOf`/draw 期清掉，见「lint」段；`ChatScaffold` 侧零命中）<br>本轮 `./gradlew --offline lint` 退出码 **0**，`0 errors, 587 warnings, 6 hints`，与基线**逐位相同**；`ConversationDAO.kt` / `ConversationRepository.kt` / `ChatDrawerViewModel.kt` / `ConversationSearchLikePattern.kt` **四个全部 0 命中**<br>**2026-10-05 真机窗口**：`./gradlew --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<C1DeviceEvidenceTest,GroupRunDAOTest,Migration_30_31_Test,Migration_31_32_Test>` → **exit 0**，**28 例全绿**（3+12+7+6），PKG110 真机；`C1LiveModelSequenceTest` 改**手动 `am instrument`** 跑（不挂住，**3.6s 完成**），因为 `connectedAndroidTest` 会卸载 app | **无设备**，同 C1-01（`adb devices` 仍为空输出）。「筛选只过滤、来回切换不丢数据」是交互行为，JVM 的用例只钉住查询判定<br><br>**部分证据（零设备，真实 SQLite 执行）**：C1-S 重放脚本给「**类型筛选只过滤**」这一条钉了 **438 条断言 0 失败**，覆盖 C1-10 用例里点名的那半句——**集合相等（不是数量相等）**、筛选后 `count` 正确、`LIMIT/OFFSET` 分页无重复无遗漏、切换 `type` 参数**不改变查询种类**。**这是 C1-10 迄今最硬的一块证据，但它不改变本行判定**：契约 `:206` 点名的四类产物——**viewer 可见消息 ID、实际模型调用序列、prompt+completion token、真机行为**——**本轮一份都没补上**（`adb devices` 仍为空）。逐项交代：<br>· **viewer 可见消息 ID**：**仍是零份**。脚本比对的是 `conversationentity` 的 `id` 集合，不是消息表；`message_node` 一行都没碰。<br>· **模型调用序列**：**仍是零份**。脚本不发起任何模型请求。<br>· **token (prompt+completion)**：**仍是零份**。同上。<br>· **导出 SHA-256**：**仍是零份**。C1-10 不产出群聊导出文件（那一份在 C1-09 行）。<br>· **真机行为**：**仍是零份**，抽屉 UI 端到端（搜索框输入 → 列表刷新）一次没跑过。<br>**所以本行状态仍是 `unverified`。**<br>**2026-10-05 真机窗口（有值）**：**OnePlus `PKG110` / Android 16 / API level `36` / `arm64-v8a`**，无线调试 `192.168.31.183:<port>`（⚠️ **端口每次开无线调试都会变**，本轮历史值 `38493`/`37957`/`40879`/`46888`，**必须用 `adb mdns services` 找当前端口**）；app `heizige.kk.khatkit.debug`，`versionName 2.5.5`/`versionCode 190`，launcher `RouteActivity`；库 `rikka_hub`，`PRAGMA user_version=32`、`integrity_check=ok`；fingerprint `OnePlus/PKG110/OP5D2BL1:16/UKQ1.231108.001/V.50213d4-2c63a59-2c63a56:user/release-keys`。⚠️ 跑前 `adb devices -l` 必须**只有一条**（mDNS 把同一台机器登记两条，互相踩）；⚠️ `connectedAndroidTest` 跑完**卸载 app，会把外置目录里的证据文件一起删掉**——采证据必须手动 `install -r -t` + 手动 `am instrument`，跑完再 `adb pull` <br>**2026-10-05 第四个窗口（真实公网网关，HEAD `6ba95422`）**：设备与 app 与上一窗口同（OnePlus `PKG110` / Android 16 / API level 36 / `heizige.kk.khatkit.debug`）。⚠️ **这一轮仪器进程普遍起不来**：OnePlus OEM 回收策略（`OsenseKillAction` / `NirvanaLowFree`，`appcareThreshold=79`、app 占 313MB）在 app 进程存活约 **34-44 秒**时杀进程；**既有 mock 用例同样被杀**（单跑最快的 `budgetTruncation…` 也是 `Process crashed`），所以**与真实网关、与本轮改动都无关**；`:app:connectedDebugAndroidTest` 全量报 `Process crashed` 且 **0 个测试启动**（连已知的 `BrowserRuntimeTest` Coil 崩溃都没碰到）。按约束「OOM 立即停止重试」共试 **11 次**后停止——**这是设备环境的限制，不是代码缺陷**。<br>⚠️ **mDNS 双注册怎么正确断（补上一轮那条「要 disconnect 掉 (2) 那条」）**：`adb disconnect 192.168.31.183:<端口>` → `no such device`（**错**）；要断的是 mDNS serial 本身 —— `adb disconnect 'adb-3B6F5ME910B6H059-Sqr0AX (2)._adb-tls-connect._tcp'`（**对**），因为 `adb devices` 里那两条的 serial 是 mDNS 名而不是 IP:端口。⚠️ 它**会反复自己注册回来**，每次 instrument 前都要重新确认只剩 1 条。| 输入 = `feature.chat.ConversationListQueryPlanTest`（7）<br>`feature.chat.ConversationTypeFilterSourceGuardTest`（2）<br>**`core.data.repository.ConversationSearchLikePatternTest`（12）**<br>`core.data.model.GroupChatTest`（18）<br>fixture 之一是 SQL 谓词常量 `CONVERSATION_TYPE_PREDICATE_SQL`<br>**重放脚本的 fixture**：25 行（助手 A1 23 行 / 助手 A2 2 行），`type×folder` **6 种组合全覆盖**（DIRECT×`{'',f1,f2}` = 14/2/1，GROUP×`{'',f1,f2}` = 5/2/1），21 未置顶 / 4 置顶，标题覆盖普通 / 置顶 / 文件夹 / 「共享词」矩阵 / 字面 `%` / 字面 `_` / ASCII 大小写对 / 繁简对 / 空串 / 纯空格，外加**故意 4 行 `(is_pinned, update_at)` 全同且插入顺序与 id 升序相反**（用来抓「去掉 id 兜底」）；参数矩阵 60 组 | **无证据（需真机）**。⚠️ C1-S 重放给的是 `conversationentity` 行集合的相等判定，**不是消息 ID 台账**；`adb devices` 仍为空<br>**2026-10-05 仍缺**：viewer 台账量的是**群聊消息**，C1-10 要的是**抽屉列表层**的「同一列表混排、类型筛选只过滤、切换后消息与会话数据不丢」。C1-S 那 438 条主机侧重放比的是 `conversationentity` 的 `id` 集合，**不是消息 ID 台账**；真实 HTTP 两轮也没构造混排会话。所以本列**仍是零份** <br>**2026-10-05 第四个窗口**：三视角可见消息 ID 台账由同一个用例一起生成，⚠️ **但那一跑随后被 OEM 回收策略杀进程，`connectedAndroidTest` 又按预期卸载 app 清空了外置目录，落盘 JSON 没拉回来** → **本列不填新值**（仓库里没有可核的台账文件，只有同一次成功生成的内存快照）。| **无证据**。脚本不发起任何 provider 请求，没有可导出的调用序列<br>**2026-10-05 仍缺**：C1-10 不发起任何模型请求（主机侧重放与真机两轮都没构造混排会话），调用序列仍零份 <br>**2026-10-05 第四个窗口：仍然是零份**——真机 UI 端到端仍然零份，**一行 Compose 没上过屏**；真实网关只跑了后台的轮次生成。| **无证据**<br>**2026-10-05 仍缺（这一列对本例不适用）**：抽屉筛选不消耗 token，零份 <br>**2026-10-05 第四个窗口**：pipeline 那三个数是**生成侧**的，**不覆盖**「切换会话后消息与会话数据不丢」；本列**仍然是零份**。| **无证据**。C1-10 不产出群聊导出文件 | `…TEST-heizige.kk.khatkit.app.feature.chat.ConversationListQueryPlanTest.xml`<br>`…TEST-heizige.kk.khatkit.app.feature.chat.ConversationTypeFilterSourceGuardTest.xml`<br>**`…TEST-heizige.kk.khatkit.app.core.data.repository.ConversationSearchLikePatternTest.xml`**<br>`…TEST-…GroupChatTest.xml`<br>DAO：`app/src/main/java/heizige/kk/khatkit/app/core/data/db/dao/ConversationDAO.kt:76`（未归档路 `@Query`）/ `:108`（搜索路 `@Query`，本轮改过）<br>⚠️ 早前记的 `ConversationDAO.kt:19/38/66` **已随本轮 DAO 改动漂移**（新增 KDoc + 14 条 `ORDER BY` 就地追加），现值见上<br>仓储：`app/src/main/java/heizige/kk/khatkit/app/core/data/repository/ConversationRepository.kt:92/281`<br>转义纯函数：`app/src/main/java/heizige/kk/khatkit/app/core/data/repository/ConversationSearchLikePattern.kt:30/41/64`<br>**重放脚本：`tools/verification/c1s_conversation_type_filter_replay.py`（83,955 字节 / 1,382 行，sha256 `3b3e12be5d685670ae4121188e4fcd1b4de6a81a31c3317f3b6e7dbd8fc00ce0`，已入库）**<br>输出日志（仓库外）：`/tmp/opencode/c1s_check.log`（478 行 / 66,933 字节，sha256 `5645fa1350caf88afb5e33d3e52c9ebbab0b64975a041715559b9cac76a40073`） <br>**2026-10-05 第四个窗口**：`app/src/androidTest/java/heizige/kk/khatkit/app/feature/chat/C1LiveModelSequenceTest.kt`（`6ba95422` **+539 行 / 生产代码零改动**，该文件现为 **1982 行 / 5 个 `@Test`**）；证据文件 `c1-real-raw-dump.json`（⚠️ **未入库、也没 pull 回来**，见设备列）。| `unverified`<br>**新窗口（HEAD `361c7cf6`，2026-10-06）：仍是 `unverified`。** 本轮三批改的全是**发现并堵住的真缺陷**（平票脚手架泄漏 / 残留旧 job 跨轮盖戳 / 跨轮写 FAILED），⚠️ **不构成任何用例的验收证据**——②③ 针对的残留旧 job 路径**从未在真机复现过**（本轮零设备、零 `adb`）。契约 `:206` 点名的四类产物本轮**一份未增** <br>**新窗口（HEAD `a74c1820`，2026-10-06）：仍是 `unverified`。** 判定规则不变——`client-changes.md:232-235`「测试未运行、只看截图或只看 UI 状态均标记 `unverified`」+ 本文「判定规则」第 2 条（缺可见消息集合 / 调用序列 / 哈希的行不算通过）。本轮**新增三类东西，但没有一类改变任何一行的判定**：**① 真实公网网关目标用例首次真机全绿**（`am instrument` `exit 0` / `numtests=1` / `Time: 16.002` / `OK (1 test)`；真实 usage a `6839/92`、b `6645/58`、c `6904/242`，**Σ(prompt+completion)=20780 与落库 `group_runs.spent_tokens` 精确相等**，三份产物已 `adb pull` 回主机并记 SHA-256）；**② 群配置面板两个真机必崩已修并真机验证**（`logcat -d -b crash` 为空、面板全部字段 `uiautomator dump` 在、5 次滚到底 + 3 次反向不崩）；**③ Coil 单例崩溃结构性搬家 + 四项代码债 + 6 个新护栏类（另 1 个在册护栏扩写 +2 例）**（`:app:testDebugUnitTest` **105 类 / 808 例**）。⚠️ **真实网关那轮虽然拿到了真实 token 与真实调用序列而且已经全绿，下面这五条硬理由仍然一条都没被消掉**：① **模型名不是 wire 级抓包**（由 `message.modelId` 的 uuid 经 provider 模型表反查，app 不保存响应 `model` 字段，JSON 里已用 `wire_model_name_provenance` 标明）② **真机 UI 端到端仍然零份**——只验了群配置面板的**渲染与滚动**，**成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做** ③ **酒馆（SillyTavern）本体零份** ④ **相机扫码零份** ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未在真机全绿过**（只跑了目标用例那一条）。⚠️ 本行点名要断言的那条路径仍未被真实调用：**成员头像组 / @ 选择器 / 抽屉筛选 chip 的真机 UI 端到端**——**一项都没做**（硬理由②）；主机侧那 438 条断言是 SQL 语义，替代不了 UI。** |

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

⚠️ **为什么不直接用「正文里点名过就计入」那种更短的命令**：那种口径会把
`docs/` 两个文件里为**别的目的**被提到的类也算进来（本轮实测会算成 **39 类 / 399 例**，
多出来的 4 类是 `LorebookEngineTest` 22、`AutomationTracerTest` 5、
`NavBackStackSerializationTest` 5、`ExampleUnitTest` 1——它们分别在酒馆 lorebook、
自动化、导航序列化与模板用例里被提到，**与 C1 无关**）。**本表是按语义相关性人工维护的**，
所以复算方式是「逐行核对表内 35 行」，不是「扫正文」。
⚠️ 早前正文另一处（「用例矩阵」段下方那份清单）记的是「25 类 / 304 例」与「合计 296」
两个**互相矛盾**的旧数，**以本表为准**（订正记录见该段末尾的 ⚠️⚠️⚠️）。

| 测试类 | 例数 | 钉住什么 |
|---|---:|---|
| `GroupTurnCoordinatorTest` | **72**<br>**84**（HEAD `361c7cf6` 实测；旧值 72 是第四个窗口的） | 群聊内核判定（@ / pipeline / roundtable / vote / 预算 / 幂等 / 视角隔离 / **残留旧 job 的归属与终态守卫**） |
| `GroupTavernExportTest` | 22 | Tavern 群聊编解码往返 + 密钥过滤 |
| `TavernCompatTest` | 21 | 酒馆结构兼容 |
| `GroupChatTest` | **22** | `GroupChat` 数据模型 / `parseBallot` |
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
| **`ChatServiceSenderNameGuardTest`** | **3** | **第二生成入口通知标题护栏**（本轮 `8bc28105` / `c940813b`） |
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
| **`GroupStaleJobCommitSourceGuardTest`** | **7**<br>**9**（HEAD `a74c1820` 实测；旧值 7 是 `2fdee352` 那版的） | **源码护栏：提交路——`commitGroupTurn` 必须在 `stampGroupTurn` 之前调 `checkCommitAdmission`、`Denied` 分支只 Logging 不碰 `groupRunsInFlight`、令牌必须从 `takeGroupTurn` 返回值捕获（不得反查镜像）、`Halted` 分支不得 `persistRoundState`**（`2fdee352`）**＋（`bed09118` +2 例）禁「按 key 无条件删」的旧形状 `groupRunsInFlight.remove(conversationId)`（右括号前不许出现逗号）、`null ->` 分支必须走 `clearGroupRunMirrorIfMine` 按 runToken 清** |
| **`GroupStaleJobFailureSourceGuardTest`** | **6** | **源码护栏：失败路——`failGroupTurn` 必须在四个副作用（`appendGroupMessages` / `persistRoundState` / `dropTieBreakScaffolding` / `groupRunsInFlight.remove`）全部之前判 `checkFailureAdmission`，拒收分支只有 Logging + return**（`8ccc0264`） |
| **`ChatServiceGroupChatFailLoudGuardTest`** | **4** | **源码护栏：`ChatService` 里零群聊逻辑（不许复制 `viewerMessages` 那套）、`handleMessageComplete` 顶部必须 `require(!isGroupConversation(...))` 且排在一切副作用之前、它是 `ChatService` 里唯一的生成漏斗（3 处 `finishInterruptedPendingTools` 除外且已登记）、DI provider 名 `provideChatManager` 必须与产物一致**（`bed09118`） |
| **`GroupRunAbandonDrainSourceGuardTest`** | **5** | **源码护栏：`abandonDanglingGroupRuns` 必须分批循环读到清空（不是单页 `limit = 8`）、循环必须有具名收敛上限、页大小必须是具名常量而不是内联字面量、每行判死前必须 `findByRound` 重读并 `isTerminal` 跳过**（`bed09118`） |
| **`GroupForkDisableReasonSourceGuardTest`** | **2** | **源码护栏：`ChatMessage.groupChat` KDoc 的 fork 段落禁因是「静默换会话类型」而不是账目错位；姊妹例要求重新生成 / 删除那两半必须保留它们自己的账目错位理由（防止整段被一刀切删掉）**（`bed09118`，**只加护栏、KDoc 一个字没改**） |
| **合计（新窗口，HEAD `361c7cf6`）** | **395** | **37 类**（上两行是本轮新增的 2 类 13 例；另有两个在册类的用例数同时更新：`GroupTurnCoordinatorTest` **72 → 84**（`+12`）、`GroupTieBreakScaffoldingDropSourceGuardTest` **3 → 7**（`+4`），合计 `+16`，所以整表是 `366 + 13 + 16 = 395` ✅。⚠️ 上面的 **366 / 35 类**那一行是第四个窗口的实测值，按惯例**保留不覆写**） |
| **合计（第六个窗口，HEAD `a74c1820`）** | **408** | **40 类**（新增 3 类 11 例：`ChatServiceGroupChatFailLoudGuardTest` 4 + `GroupRunAbandonDrainSourceGuardTest` 5 + `GroupForkDisableReasonSourceGuardTest` 2；另有一个在册类 `GroupStaleJobCommitSourceGuardTest` **7 → 9**（`+2`），合计 `+13`，所以整表是 `395 + 13 = 408` ✅。⚠️ **本窗口 `:app:testDebugUnitTest` 那 `+6 类 / +25 例` 里有 3 个类（12 例）不属于 C1、不进本表**——`CoilImageLoaderSourceGuardTest` 6 / `PrimaryBottomSheetIconSourceGuardTest` 3 / `BottomSheetScrollSourceGuardTest` 3，理由见「测试结果」段那个 ⚠️⚠️） |

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
| 用例 | `C1LiveModelSequenceTest.realProviderRoundRecordsGenuineTokenUsage`（`6ba95422` 新增，文件现为 **1982 行 / 5 个 `@Test`**） |
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
2. **模型名不是 wire 级抓包。** `actual_model_call_sequence` 里的模型名是由
   `message.modelId` 的 uuid 经 provider 模型表**反查**出来的——**app 不保存响应的
   `model` 字段**，所以拿不到服务端在响应里回的那个名字。JSON 里已用
   **`wire_model_name_provenance`** 字段显式标明这一点，**文档这里也照记**。
   ⚠️ 所以「序列与按角色绑定一致」这句话的准确表述是「**落库/内存里的 `modelId` 与绑定
   一致**」，不是「抓包看到客户端按这个 model 串发出去」。
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
  ⚠️ **其余 4 条仍未在真机全绿过。**

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
见「C1 真机证据采集第四轮」；⚠️ **其余 4 条仍未全绿**，且本轮 `:app:connectedDebugAndroidTest`
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

**A2. `C1LiveModelSequenceTest` 其余 4 条真机全绿**（现在只有第 5 条绿了）

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

**C2. 修「证据登记」表里 C1-05 那一行的一个未转义 `|`**：该行有个裸 `|` 落在
「token」列的文本里，导致**按列数切单元格的任何脚本都会把它数成 13 列**
（其余 9 行是 11 列、用例矩阵是 5 列）。本轮的状态列审计就是被它绊了一次。
把它改成 `\|` 或换成 `/` 即可。

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
18. **⚠️ `actual_model_call_sequence` 里的模型名不是 wire 级抓包（结构性，改不掉）。**
    app **不保存响应里的 `model` 字段**，所以序列中的模型名是由 `message.modelId` 的
    uuid（`5a86b2d6…` / `8b6bf21c…`）经 provider 模型表**反查**出来的。
    JSON 证据里已用 **`wire_model_name_provenance`** 字段显式标明这一点。
    ⚠️ 要变成真抓包，需要在 `OpenAIProvider` 侧记录响应 `model`（**属于加日志，不属于
    本轮范围**），或者在设备侧抓 TLS 之外的那一段（`adb reverse` 到本机自签代理）。
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
28. **⚠️ `ChatService.finishInterruptedPendingTools` 里三处 `generateText` 同样不群聊感知，
    而且从 `stopGeneration` 可达（本轮新发现，未动手）。**
    `fa36c65c` 的 fail-loud 闸门只在 `handleMessageComplete`——它是 `ChatService` 里
    `generationLoop.generateText` 的唯一调用点，**但 `finishInterruptedPendingTools`
    里的三处 `providerHandler.generateText` 是另一条旁路**（取消后续跑工具），
    **`stopGeneration` 能走到它**。⚠️ **修它要先定产品行为**：群聊下**跳过**（工具调用
    在群聊轮次里没有正确语义）vs **做成群聊感知**（等于要在这个类里重建一套）。**两者
    都改运行时行为、都需要真机**，所以只登记不动（见操作清单 C5）。
    ⚠️ 别把它读成「`fa36c65c` 的闸门没覆盖全」——**那个闸门本来就不是为这条路径设计的**，
    类 KDoc 里也写了「另三处 `providerHandler.generateText` 属于
    `finishInterruptedPendingTools`，那是取消后续跑工具的旁路」。
29. **⚠️ 四项代码债的运行时行为全是零证据（本轮唯一的 gradle 证据只到「编译 + 单测」）。**
    `require` 是不是真抛、分批循环在真机上的实际行为、`remove(key, value)` 的真实并发
    效果、以及「`produced == null` 时 `failGroupTurn` 提前 return」的具体触发条件，
    **一条都没在真机上观测过**。⚠️ **三条 gradle 命令（编译 / 单测绿）不构成任何契约验收
    证据**，护栏也全是**源码文本级**的。运行时验证步骤见操作清单 A8。

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
| 5. 仪器测试 | ⚠️ **不变**。本轮**一行 `androidTest` 都没动**（全量 `@Test` 仍是 **61**）；上一次全绿仍是 `28/28、exit 0`，`C1LiveModelSequenceTest` 那 5 条**在本窗口内没有一条在真机全绿过**<br>✅ **第六个窗口订正（HEAD `d96d64e1`，2026-10-06）**：那 5 条里的 **`realProviderRoundRecordsGenuineTokenUsage` 已在真机全绿**（`exit 0`、`OK (1 test)`、15.6s 跑完、OEM 未触发），落盘 JSON 拉回并记了 SHA-256；⚠️ **其余 4 条仍未全绿**，且 `:app:connectedDebugAndroidTest` 仍因 `BrowserRuntimeTest` 的 Coil 单例崩溃**在 9 过 1 挂后中止**（`exit 1`）。详见「C1 真机证据采集第四轮」 |

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
  反查，app 不保存响应 `model`）；③ **这一行点名的那条路径这一轮没被真实调用过**
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
  ① **模型名不是 wire 级抓包**（`message.modelId` 的 uuid 反查，app 不保存响应 `model`）；
  ② **真机 UI 端到端零份**：那轮只验了**群配置面板的渲染与滚动**，
  **成员头像组 / @ 选择器 / 筛选 chip 三项一项都没做**，面板里的
  **保存 / 二维码 / 扫码三个业务动作一个都没点**；
  ③ **酒馆本体零份**；④ **相机扫码零份**；
  ⑤ **`C1LiveModelSequenceTest` 其余 4 条仍未真机全绿**（只跑了目标用例那一条）。
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
