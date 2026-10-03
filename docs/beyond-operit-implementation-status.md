# beyond-operit 实施与验收状态

更新：2026-10-03。目标仍为 `beyond-operit-client-changes.md` 与
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

用户目前开着代理，要求需要线上操作时先发消息；尚未连接/部署服务端，
未执行生产迁移。客户端市场新 kind 入口不得因为本地测试通过就开启。

## 客户端包验收清单

| 包 | 代码现状/证据 | 必须继续实现或验证 |
| --- | --- | --- |
| A1 浏览器 | 无设置页；WebView 会话与动作、语义/增量快照、截图、Room 历史、卡片脚本桥已写入；签到/比价卡已移至 KhatKitCards，AI 通过卡片工具调用；JVM 与设备本地 HTML 有定向证据 | 独立 cookie；真实网站登录/重定向/两张卡验收；更多 WebView/设备版本验证 |
| A2 记忆 | 空间/chunks/edges/mentions 四表 + 迁移（旧 KV 保留溯源）；FTS5 外部内容+触发器；sqlite-vector 接线 + Kotlin 暴力余弦兜底；RRF 四路融合（FTS/向量/图谱/时间衰减）；memory_search/add/link/forget 四工具；LLM 自动抽取+开关；检索式注入带 id/来源/置信度；UI 置信度/来源标注 | 50 文档/20 问 ≥80% 与 5k P95 <300ms 实测；记忆图谱 Mermaid UI；来源跳转到原消息（当前只显示 messageId）；bi-temporal 冲突标记 |
| A4 视觉 GUI | UiAction 同构动作模型（RecordedStep 超集+bbox）+ 双向转换；VisualGrounding 双通道（中心包含/IoU→节点中心误差 0，bbox 兜底≤8dp）；AutomationTracer JSONL 轨迹（截图+动作+目标+结果）；TracePlayer 回放；device_act bbox 参数+双通道解析+全动作轨迹；device_screen 返回 image_width/height 坐标空间 | 10 任务成功率 >85% 与 3 台设备矩阵实测；有节点树时误差 0 的真机验证；轨迹回放 UI（当前只有引擎）；可选虚拟显示回退；录制卡导出（复用 RecordingCardFactory 待接） |
| A5 酒馆 | 宏与 CCv3 lorebook 字段导入已有代码 | 完整无损 V2/V3/PNG 双向；15 项 lorebook 语义对照；ST 聊天 swipe 分支互通；preset/instruct 子集；20 张真实卡回归；酒馆本体导入验证；角色带工具设备任务演示 |
| B1 工作流 | 节点编辑页/WorkflowStore 已有基础 | Room workflows/runs/steps；10 类节点执行端到端；18 触发源连接；自然语言/JSON/脚本三态；SSE 日志/取消/重试/成本；通知分类分支案例；3 个脚本等价性用例 |
| B2 ToolPkg | 服务端本地协议已推进 | 本地 ZIP/dex/脚本安装与卸载更新；依赖解析/sha256；工具/设置/Provider 热安装；五类 hooks 与全局禁用/审批；静态报告生成及三样例；市场 UI 与功能开关；六 kind 安装全流程；服务端上线后联调 |
| B3 路由 | 已有 task route 与 cooldown 基础 | 全任务候选/三策略消费点；Key 池轮换/429/5xx 熔断恢复；日/月预算降级或只读；每 key/任务统计 UI；模型分离与故障切换测试 |
| C1 群聊 | 尚未完成审计 | role_id/关联表；角色视角过滤与独立记忆；@ 路由 UI；pipeline/roundtable/vote 与轮次预算；Tavern 导出、QR、备份恢复；三角色隔离验证 |
| C2 工作区 | 现有 workspace/proot 与文件工具可复用 | 五种模板与项目规则；preview；内容寻址 diff/revert 与聊天重发回滚；SSH/SFTP 许可评估和读写后端；APK/HTML 打包；端到端及路径穿越测试 |
| C3 语音入口 | 现有 VoiceSessionController 可复用 | Sherpa 唤醒/前台服务/VAD/流式 ASR；全双工打断与草稿；ASSIST 面板、Widget、气泡、取词悬浮球；VITS 依赖包；真机误触/保活/500ms 打断/1s ASSIST 验证 |
| C4 虚拟形象 | 尚未完成审计 | glTF 模块、五状态、口型/视线/情绪事件；桌面宠物；dependency 安装卸载；骁龙 7 系 60fps 真机验证 |
| D0 UI DSL | 尚未读取并完成独立规格审计 | 按卡片仓库 `UI_DSL_UPGRADE.md` 与 `UI_DSL_CLIENT_CHANGES.md` 全量实现/验收 |

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
