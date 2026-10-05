# beyond-operit 开源参考

核对日期：2026-10-03。此表记录用户指定的参考方向与实际核验结果。
README 的功能描述不作为本项目验收证据；复制或改编源码前须记录固定提交、
具体文件及许可证，保留必要声明。当前尚未复制这些项目的源码。

| 包 | 第一方入口与核验状态 | 参考目标 / Android 边界 |
| --- | --- | --- |
| A1 | [vercel-labs/agent-browser](https://github.com/vercel-labs/agent-browser)，归属已确认，仓库标注 Apache-2.0 | 已阅读 README 的引用选择器、快照差异、显式 tab、状态等待约定。结合 WebView 重写；桌面 Chrome/CDP 的输入、cookie、渲染语义不能直接当作 Android 已实现。 |
| A2 | [elaranjo/clawdiney](https://github.com/elaranjo/clawdiney)（MIT，2026-10-03 抓取成功）+ [mem0ai/mem0](https://github.com/mem0ai/mem0)（Apache-2.0）+ [sqliteai/sqlite-vector](https://github.com/sqliteai/sqlite-vector)（Apache-2.0） | clawdiney 的 `storage.py`/`query_engine.py` 提供单 SQLite 混合检索架构（sqlite-vec + FTS5 + 图谱表 + RRF k=60）；mem0 提供 ADD-only 实体链接抽取与多信号融合思路；sqlite-vector 是项目已有依赖 `ai.sqlite:vector`（BLOB 向量 + `vector_init`/`vector_full_scan`）。本地为 Kotlin/Room 重写，未复制源码。 |
| A4 | [screen2prompt](https://pypi.org/project/screen2prompt/)，搜索找到 0.0.2，页面抓取失败（JS challenge），源码及许可证仍待核实，**不作为已采用参考** | 元素标注、动作与观测闭环；Android 仍用本机无障碍与截图。本地 A4 为自研双通道定位（视觉 bbox → 无障碍节点），未复制任何上游源码。 |
| A5 | [SillyTavern](https://github.com/SillyTavern/SillyTavern) AGPL-3.0，`release` 分支已读源码；[character-card-spec-v2](https://github.com/malfoyslastname/character-card-spec-v2) 规格正文；[character-card-spec-v3](https://github.com/kwaroran/character-card-spec-v3) MIT；[RisuAI](https://github.com/kwaroran/RisuAI) GPL-3.0；[agnai](https://github.com/agnaistic/agnai) AGPL-3.0。旧条目 [LLM-Character](https://github.com/m-doughty/LLM-Character) Artistic-2.0 **不复制** | 见下方 A5 对照。本地按行为重写，未复制上述源码。 |
| B1 | [xiaoqiang-cheng/QGraph](https://github.com/xiaoqiang-cheng/QGraph) v0.1.5，README 标注 MIT。2026-10-03 读过 `src/qgraph/engine/executor.py`。GitHub API 403，提交 SHA 未钉死 | Kahn 入度、失败下游 skipped、skip_nodes 续跑。本地 Kotlin 重写，执行仍编译成 FlowSpec。见下方 B1 对照。 |
| B2 | [Operit ToolPkg 格式](https://github.com/AAswordman/Operit/blob/main/docs/TOOLPKG_FORMAT_GUIDE.md)、[示例](https://github.com/AAswordman/Operit/tree/main/examples)；源码许可证待逐包核实 | 固定 API 版本、依赖、hooks、Provider 与设置 UI；topic 只用于发现具体包，不能当成统一规范。 |
| B3 | [Sparrived/auto-model-key-router](https://github.com/Sparrived/auto-model-key-router) MIT，读过 `docs/API.md` 的 routing_mode / Key 冷却说明；[HalfEmptyDrum/Key-Carousel](https://github.com/HalfEmptyDrum/Key-Carousel) MIT，读过 README 的指数退避表。未复制源码 | 本地 Key 池按规格用 5s→10s→20s、上限 5 分钟、半开探测。AMKR 的 round_robin/priority 只作对照，不引入 Python 代理。 |
| C1 | 2026-10-04 完成源码级调研 **12 个开源项目**，全部钉死 40 位 SHA 并登记读过的文件与行号：SillyTavern `06bde939` AGPL-3.0、autogen `027ecf0a` + `v0.2.40` MIT（代码在 `LICENSE-CODE`）、crewAI `738c8e19` MIT、langgraph `9a0394d8` MIT、RisuAI `9f3b589b` GPL-3.0、agnai `fccee00f` AGPL-3.0、MetaGPT `11cdf466` MIT、camel `0106b768` Apache-2.0、consensus-core `f65dec71` MIT、Operit `dbf71916` LGPL-3.0、LianYu-app `688b0d7c` Apache-2.0、Knowe `23411a01` MIT；另有 **5 个仅候选**（只读 README，不进对照表），以及 **1 个已核验并排除**（lyricon `f3854d34` Apache-2.0，Xposed 状态栏歌词模块，与 C1 群聊无直接关系，不进对照表）。未复制任何源码，也未运行上游测试 | 许可按 AGPL-3.0 兼容矩阵分两档：**camel / LianYu-app（Apache-2.0）只能借鉴机制 + 独立重写**；其余十家（AGPL-3.0 SillyTavern·agnai、GPL-3.0 RisuAI、LGPL-3.0 Operit、MIT autogen·crewAI·langgraph·MetaGPT·consensus-core·Knowe）可移植并保留声明。七项需求的横向总表、移植优先级与本机构建基线见文末 `## C1 多角色群聊（2026-10-04）`。本地不共享历史，按规格做视角隔离。 |
| C3 | [demystify-voice](https://pypi.org/project/demystify-voice/)，搜索结果发布方为 demystify-systems/ai-services-tools；完整源码与许可证待读取 | 打断、已播前缀、附和识别；须与本地 VAD/ASR/TTS 生命周期核对。 |
| C4 | [eykicuihb/KuroBlob-AI](https://github.com/eykicuihb/KuroBlob-AI)，README 标注 MIT | 表情/口型状态与动画时间线；Web 渲染示例不能证明 Android glTF 与目标设备 60fps。 |

后续实施记录应填写“上游提交/文件 → 本地改动 → 对照测试”。没有读过源码的项目
仅列为候选；本地已实现的功能也不能追认成上游移植。

## A1 当前对照记录

阅读 agent-browser README 的 snapshot/diff、显式 tab、wait 条件约定后，
本地增加了 `since` 单页增量、文档限定的元素引用和 selector/text 等待。
本地实现使用 WebView DOM 与结构化 added/changed/removed/order，不复制其
Chrome/CDP 实现。进一步阅读了上游
[`diff.rs`](https://github.com/vercel-labs/agent-browser/blob/main/cli/src/native/diff.rs)
和 [`tab_binding.rs`](https://github.com/vercel-labs/agent-browser/blob/main/cli/src/native/tab_binding.rs)：
前者对文本快照做行级差异并快速处理完全相同输入；后者绑定稳定的 CDP target，
避免重启后误用其他会话的活动标签。本地选择结构化元素差异和显式标签 ID，
暂不承诺进程重启恢复活动页面。读取的是 main 分支，尚未固定提交；未复制源码。
`snapshot.rs` 仍待逐文件审查。

本地证据：`BrowserScriptsTest` 验证增量重建与 full 一致、scope/淘汰回退、
导航旧引用失效、异步条件谓词；`BrowserExampleCardsTest` 验证脚本控制流。
这些是本地测试，不标记为通过上游测试集。

助手域名阶段再次核对上游
[README 的 Sessions / Tab pinning / Domain Allowlist](https://github.com/vercel-labs/agent-browser/blob/main/README.md)：
借鉴会话拥有标签、外来活动标签不改变操作目标的原则。本地按助手和规范化域名配置
绑定标签，用例验证外来 ID 拒绝及审批恢复。上游 Chromium 声称额外覆盖
WebSocket/worker/WebRTC，本地 WebView 拦截不能据此声称具备相同网络隔离；
已在浏览器文档明确差距。域名语法延用本地卡片 host+子域名约定，不照搬上游通配符。

## A1 cookie 隔离的下一实现入口

已查官方 [WebViewCompat](https://developer.android.com/reference/androidx/webkit/WebViewCompat)
与 [WebKit 1.9.0 说明](https://developer.android.com/jetpack/androidx/releases/webkit#1.9.0)：
多 profile 可隔离 cookie 等数据，但必须先检查 `MULTI_PROFILE`；
`setProfile` 必须在 WebView 其他操作之前调用。
关闭标签后 profile 清理需遵守
[ProfileStore](https://developer.android.com/reference/androidx/webkit/ProfileStore) 生命周期。
当前项目尚未引入 androidx.webkit，该能力尚未实施。
后续需区分“默认共享登录态”和“明确选择独立 profile”，不可在设备不支持时
悄悄把独立模式降级为共享模式。

## A2 对照记录（2026-10-03）

上游参照与本地对应：

- clawdiney [`storage.py`](https://github.com/elaranjo/clawdiney/blob/main/src/clawdiney/storage.py)：单 SQLite 承载 documents/chunks/chunk_vectors/chunk_fts/entities/relations。
  本地 `memory_spaces/memory_chunks/memory_edges/memory_mentions` 四表 + `memory_chunk_fts`（外部内容 FTS5 + 触发器）+
  `memory_chunks.embedding` BLOB。不复制其 Python 代码；bi-temporal（valid_at/invalidated_at）与 is_conflict 本轮只取
  软删/失效（`deleted_at`/`invalidated_at`），冲突标记未做。
- clawdiney [`query_engine.py`](https://github.com/elaranjo/clawdiney/blob/main/src/clawdiney/query_engine.py)：
  `rrf_fuse`（k=60，`score = Σ 1/(k+rank)`）、BM25+向量 fail-soft、按 note 去重、图谱扩展、adaptive cutoff。
  本地 `MemoryRetrievalEngine.rrfFuse` 同公式；四路信号 = FTS / 向量 / 图谱扩展 / 时间衰减（rankByRecency）。
  adaptive cutoff 未移植；dedupe 按 chunk id 天然去重。
- mem0（April 2026 算法说明）：single-pass ADD-only 抽取、entity linking、multi-signal retrieval、temporal reasoning。
  本地 `MemoryExtractor` 走 ADD-only JSON 抽取（facts + 三元组），写 chunk + edge + mention；不做 UPDATE/DELETE 合并。
- sqlite-vector（`ai.sqlite:vector`，项目已有依赖）：BLOB 存向量、`vector_init('memory_chunks','embedding',...)`、
  `vector_full_scan` KNN。本地 `MemoryVectorIndex` 在 onOpen 注册；扩展不可用时 `bruteForceCosine` 兜底（JVM 测试即走此路）。

本地测试：`MemoryRetrievalEngineTest`（RRF/衰减/FTS 清洗）、`MemoryExtractorParseTest`（JSON 抽取容错）、
`MemoryToolsSearchTest`（溯源字段）。这些是本地单测，不标记为通过上游测试集。

## A4 对照记录（2026-10-03）

screen2prompt（PyPI 0.0.2）页面抓取被 JS challenge 拦截，源码与许可证未核实，
**未采用其代码**。A4 的双通道定位为本地自研，设计动机与「元素标注 → 动作 → 观测」
闭环一致，但实现独立：

- `record/UiAction.kt`：统一动作原语（`RecordedStep` 超集 + bbox 目标），
  `fromRecordedStep`/`toRecordedStep` 双向转换 → 录制 / 回放 / Agent 生成同构。
- `record/VisualGrounding.kt`：视觉 bbox → 无障碍节点匹配。中心包含优先（1.0 分）、
  IoU ≥ 0.25 兜底；命中节点用节点中心（误差 0），否则 bbox 中心（声明 ≤8dp）。
- `feature/automation/AutomationTracer.kt`：JSONL 轨迹（截图帧 + 动作 + 目标 + 结果 + 分辨率）。
- `feature/automation/TracePlayer.kt`：轨迹回放（与 device_act 同一无障碍执行面）。

本地测试：`VisualGroundingTest`（10 项）、`UiActionTest`（7 项）、`AutomationTracerTest`（5 项）。
这些是本地单测，不标记为通过上游测试集。

## A5 对照记录（2026-10-03）

读过的固定文件，均未复制源码：

| 上游 | 许可 | 读过的文件 | 本地对应 |
| --- | --- | --- | --- |
| SillyTavern `release` | AGPL-3.0 | `src/character-card-parser.js` | `PngCharacterCard`：`chara` 保留原文，`ccv3` 把 spec 改成 `chara_card_v3` / `3.0`，读取优先 `ccv3` |
| SillyTavern `release` | AGPL-3.0 | `public/scripts/world-info.js` 的 `world_info_logic`、`originalWIDataKeyMap`、概率掷骰、selective 次键 | `LorebookEngine` + `parseCharacterCardLorebook`。概率/深度/位置/递归开关读 `extensions`，与酒馆角色书导入一致 |
| SillyTavern `release` | AGPL-3.0 | `public/scripts/macros.js` 的 `random` / `roll` | `{{random:a,b}}`、`{{random::a::b}}`、`{{roll:d6}}`。本地规格的 `{{random:a\|b}}` 仍保留 |
| SillyTavern 聊天文件 | AGPL-3.0 实现；样例见 [rarefushion JSONL](https://github.com/rarefushion/SillyTavern.NET-File-Converter/blob/main/Sillytavern_jsonl_Example.json) | 首行元数据、后续每行一条消息，`swipes` / `swipe_id` | `TavernChatCodec.exportJsonl` |
| character-card-spec-v2 | 规格文档，仓库未单列许可文件 | `spec_v2.md` 的 `alternate_greetings`、`character_book` | 开场白做成第一条消息的 swipe，不拆成连续轮次。未知 `extensions` 回写 |
| character-card-spec-v3 | MIT | 只核对许可与范围，未逐段实现 assets / charx | v3 字段透传。`.charx` 未做 |
| RisuAI | GPL-3.0 | 仓库说明中的 `chara` / `ccv3` 分块 | 只核对分块名，不采用其 GPL 解析器 |
| agnai | AGPL-3.0 | 规格注释里的 scan depth / token budget / recursive scanning | 递归开关已接。token budget 未接 |

概率：酒馆源码是 `Math.random() * 100 <= probability`。本地仍用 0–99 的整数掷骰、小于百分比才命中，这样 0 永不触发、100 必定触发，和酒馆文档一致。没有把酒馆源码搬进来。

未读、因此不能当成已对照：酒馆本体打开本仓库导出的 PNG/JSONL、20 张第三方卡、RisuAI 的 `characterCards.ts` 实现细节。

## B1 对照记录（2026-10-03）

读过 QGraph `src/qgraph/engine/executor.py`（MIT，仓库 README 写 v0.1.5）。没有复制源码。

| 上游行为 | 本地对应 |
| --- | --- |
| Kahn 入度队列 | `WorkflowEngine.execute` 按 `dependsOn` 建边，入度为 0 才执行 |
| 节点失败后下游标 skipped，不再执行 | `failAt` 与失败父节点走 `skipDependents` |
| `skip_nodes` 把已成功节点记 success 并跳过执行 | `skipSuccess` 写入 `resumed=true`，不进入成功输出序列 |
| 取消后未执行节点标 cancelled | `cancelAfter` |
| 运行结果含 node_id / status / output / duration | Room `workflow_runs` / `workflow_run_steps`；本机 SSE `workflow_run` |

QGraph 的 Shell/Python 节点、WebSocket 日志和并行 gather 没有搬过来。本地节点编译成既有 FlowSpec，由 `khatkit__run_flow` 执行。自由画布未做。

本地测试：`WorkflowEngineTest`。这是本地单测，不标记为通过上游测试集。

## C1 多角色群聊（2026-10-04）

现状：我们仓库 C1-D / C1-R / C1-M 三个子包已落库（26 个 commit，HEAD `58b129c7`）——
C1-D 是 `group_runs` 表与 `GroupRunDAO`，C1-R 是 `GroupTurnCoordinator` 群聊轮次
纯判定内核，C1-M 是 `MemoryToolScopeResolver` + `MemorySpaceGate` 群记忆空间隔离。
7 项缺口里 3 项已关闭、3 项部分关闭、1 项（eval 证据，`eval/c1-group-chat.md`
尚未创建）完全未做。本节记录的是**上游能给我们什么**，不重复本地实现状态。

### AGPL-3.0 主协议下的兼容矩阵

本仓库自身协议没有选择余地：根 `LICENSE` 是 GNU AGPL-3.0 全文（661 行），
`README.md:85-87` 与 `README_ZH_CN.md:95-97` 都写明「本项目基于 GNU Affero
General Public License v3.0 (AGPL-3.0) 开源」，根目录无 `NOTICE` 文件。
所以 AGPL / GPL / LGPL 家族的上游可以放心并入，宽松 MIT 也可以；唯一需要额外
动作的是 Apache-2.0。

| 上游 | 许可 | 并入 AGPL-3.0 的结论 |
| --- | --- | --- |
| SillyTavern / agnai | AGPL-3.0 | 同协议可移植，保留版权声明 |
| RisuAI | GPL-3.0 | GPLv3 被 AGPLv3 单向覆盖，可并入 |
| Operit | LGPL-3.0 | LGPLv3 可借 GPLv3 条款并入 AGPLv3 |
| autogen `LICENSE-CODE` / crewAI / langgraph / MetaGPT / consensus-core / **Knowe** | MIT | 宽松许可，可移植 |
| **LianYu-app / camel** | **Apache-2.0** | **不可逐行拷贝，只能借鉴机制** |

Apache-2.0 是唯一需要「借鉴机制 + 独立重写」的一档，原因不在版权而在**专利**：
Apache-2.0 §3 给专利授权附了「专利诉讼终止」条款，而 GPLv3 §3 明确禁止
「针对 contributors 的专利主张」，两者语义冲突，FSF 已判定 Apache-2.0 与
GPL/AGPL 家族不兼容。所以对 LianYu-app 与 camel：**算法与行为规格可以照搬**
（思想、方法不受版权保护），**实现必须自己重写**，并在文档里注明思路出处。

以后引入新开源依赖时按这四步过一遍，缺一步就不许合：

| # | 检查项 | 判定要点 |
| --- | --- | --- |
| 1 | 许可证 | 读**代码**的 LICENSE，不要只读根 LICENSE（autogen 就是分离的） |
| 2 | 是否 AGPL 兼容 | AGPL/GPL/LGPL/MIT/BSD 兼容；Apache-2.0 不兼容；SSPL/自定义条款停下 |
| 3 | 拷贝还是重写 | 兼容 → 拷贝 + 保留声明；Apache-2.0 → 借鉴机制 + 独立重写 + 注明出处 |
| 4 | 文档登记 | 把 仓库@40位SHA / 许可 / 读过的文件与行号 / 本地对应 写进本文档对应包号下 |

### 对原假设的纠正

调研推翻了立项时的 5 条前提，逐条记录证据：

1. **autogen 没有 consensus group chat（Conductor + voters）。**
   `ConsensusGroupChat` / `class Conductor` 在 `v0.2.40` tag 与 `main` 全树
   `git grep` 均 **0 命中**。这套代码只活在 0.2 之前的未发布 dev 分支上，
   **从未进任何 tag**，不能作为可引用实现。
2. **autogen v1.x 已删除 `GroupChat` / `GroupChatManager` /
   `speaker_selection_method` / `max_consecutive_auto_reply`。** main 只剩
   `RoundRobinGroupChat` / `SelectorGroupChat` / `SwarmGroupChat` /
   `MagenticOneGroupChat`；老实现只存在于 `v0.2.40` tag 里。移植时必须钉死
   是照新 API 还是照 0.2 语义，不能混写。
3. **camel 是 Apache-2.0 不是 AGPL-3.0**，且**没有** `RolePlaying(max_turns)` /
   `play()` / `ConsensusTermination` —— `git grep "max_turns|termination_condition"`
   全仓 0 命中；`camel/societies/` 下**只有 `workforce/`**；`RolePlaying.step`
   写死 user↔assistant 两方乒乓。「camel 提供多轮终止」的假设不成立，
   且它落在不可拷贝的那一档。
4. **SillyTavern 根本没有 @ 路由。** 它是 `\b\w+\b` 切词后任意单词包含匹配，
   详见下面 SillyTavern 一节的警告。不能拿它当 @ 语义的需求来源。
5. **autogen 的代码许可是 MIT，不是 CC-BY-4.0。** 代码在 `LICENSE-CODE`
   （Copyright (c) Microsoft Corporation），根 `LICENSE` 的 CC-BY-4.0 只管
   文档。误判会把一个宽松许可项目错当成要小心处理的。

第 5 批（Knowe）又推翻 3 条，共 8 条：

6. **「只有 autogen 一家是真 @」要修正为「严格整词只有 autogen 一家；Knowe 是
   第二家有真 @ 路由语义的上游，但刻意对 CJK 放宽了尾边界」。**
   autogen 用双侧**非捕获式**边界 `(?<=\W)` / `(?=\W)`（`_selector_group_chat.py:310-341`），
   所以 `@小林帮我` **不命中** `小林`；Knowe 的尾边界检查**只对 ASCII 尾巴**做
   （`backend/mentions.py:111`，`alias[-1] in _ASCII_ALIAS_TAIL`），CJK 别名不要求
   尾边界，所以 `@小林帮我` **会**命中 `小林`——而且 `mentions.py:83-89` 的
   docstring 把这条写成**刻意取舍**（中文人名常是长名里的前缀，前缀名并存时要靠
   最长匹配收敛，所以宁可放行紧贴的正文）。**两边都有道理，取舍要自己定，
   不要默认抄任一家。**
7. **「每轮 token 预算 0 个有」不只是「没人做」——Knowe 是主动删掉了并写了理由。**
   `BudgetSpec` 是一个**零字段 no-op**（`backend/runtime.py:264-272`：`from_mapping`
   里直接 `del value`，`to_dict` 恒返回 `{}`）；`runtime.py:615` 的 `RuntimeConfig`
   注释写死 `No turn ceiling, tool-error cap, correction budget, or default task clock.`；
   `backend/worker_gateway_runtime.py:326-327` 补充 `those are the LLM's decisions`。
   所以横向总表 d 那一格的 **0 个**含义要改读：**这是上游明确拒绝的形状，不是待填补
   的空白**。我们要做 per-round 闸门**不能引任何上游当依据**——三家（crewAI
   `UsageMetrics` / agnai `getContextLimit` / Knowe `BudgetSpec`）都只给计量或裁剪，
   没有一家给中止闸门。
8. **「多角色群聊 = 共享可见历史 + 发言轮转」不是行业共识。** Knowe 证明另一条路
   跑得通：Worker 的 LLM 消息列表**每个 attempt 从零构造、只有 2 条**
   （`backend/runtime.py:925-974` `_initial_messages()`：system = `worker_prompt.md` +
   自己 worklog 尾部；user = `"Task envelope:\n" + json`），**零共享历史 ⇒ 零泄漏**；
   发言权集中在 Coordinator 派单（`souls/coordinator.txt:46` 明写「一次只派一件」）。
   **这不是要抄**（它换掉的是我们 C1-M 空间闸门要解决的问题），而是提醒：我们的
   视角隔离在评估复杂度时要知道自己在付什么代价——Knowe 压根不存在这个问题。

### SillyTavern — SillyTavern/SillyTavern @ 06bde939fb1e9c4c8d8641d810f0a916b5bce127

读过的文件：`public/scripts/group-chats.js`、`public/scripts/utils.js`、
`public/script.js`。

**许可结论**：可移植（同协议 AGPL-3.0，保留版权声明）

激活策略由 `group_activation_strategy`（`group-chats.js:122`）选择，四种取值：

| 策略 | 值 | 行号 | 行为 |
| --- | --- | --- | --- |
| `NATURAL` | 0 | L1242 | 默认。三段式：提及 → 加权随机 → 保底 |
| `LIST` | 1 | L1180 | 按成员数组顺序依次发言，同一轮去重 |
| `MANUAL` | 2 | — | 用户手动点选发言者，本地不需要 |
| `POOLED` | 3 | L1197 | 倒序扫到用户消息，收集 `spokenSinceUser`，优先在本轮还没说过的人里随机挑 1 个；都说过则从除上一位发言者外的池子里随机挑 1 个 |

- **`LIST`（L1180）就是我们的 pipeline 模式**：成员数组顺序依次发言、
  本轮已发言的去重。行为规格可直接照搬，实现自己写。
- **`NATURAL`（L1242）三段式**：①提及段逐词匹配角色名，先排除 `bannedUser`
  成员，除非 `allow_self_responses`；②加权随机段 `shuffle(members)` 后每人
  独立判定 `talkativeness >= Math.random()`——注意是 `>=`，所以 `talkativeness=1`
  恒中；③保底段 `while` 未选出就从 `talkativeness>0` 的池子重试随机，
  **最多重试 `randomPool.length` 次**。第③点的重试上限比 RisuAI 的死循环好，
  值得抄行为。

⚠️ **它的「@」不是 @ 路由，不能当我们的 @ 语义**：

```js
// utils.js:1356
value.matchAll(/\b\w+\b/gim)   // extractAllWords: 逐词小写
```

`group-chats.js:1263` 的判定是 `extractAllWords(character.name).includes(inputWord)`
——**角色名里任意一个词出现在输入里就激活**。叫 "Alice Johnson" 的角色，
输入含 "alice" 或 "johnson" 都会唤醒，输入 "john" 反而不命中。比整词 @ mention
宽松得多。

**队列与中断**（这半段质量高，值得移植行为）：

| 机制 | 位置 | 行为 |
| --- | --- | --- |
| 单飞互斥 | L958 | `is_group_generating` 布尔量 |
| 串行发言 | L1051-1063 | `for (const chId of activatedMembers)` 逐个 `await Generate(...)` |
| 入口包装 | L945 | `generateGroupWrapper`，闭包内 `throwIfAborted()` |
| 中断点 | L946 | `params.signal.aborted` **每个角色发言前**检查一次 |
| 自动模式 | L1413 | `groupAutoModeAbortController` + `setInterval` 驱动 worker |
| 收尾 | L1077-1089 | `finally` 复位 `is_group_generating=false` 并广播 `GROUP_WRAPPER_FINISHED` |

**取消 = 抛异常，不写半条消息，这正是我们要的语义**——本地轮次取消应照此对齐，
而不是把已经流式吐出来的半条文本留在库里。

⚠️ **零视角隔离，对我们毫无参考价值**：单一 `chat` 数组，所有成员看全部消息；
APPEND 模式下 `getGroupCharacterCards`(L477) 把**所有成员的角色卡拼进同一个
prompt**。唯一的结构性手段是**停止串**：`script.js:3045-3054` 给除当前发言者
外的每个成员注入 `\n<Name>:` 作为 stopping string；`getGroupDepthPrompts`(L427)
允许每角色插不同 depth/role 的提示块。这两个都是 prompt 工程，不是隔离，
不能拿来对照我们已落库的 C1-M 空间闸门。

### autogen — microsoft/autogen @ 027ecf0a379bcc1d09956d46d12d44a3ad9cee14

（legacy 对照用 `v0.2.40` tag @ `3b4c0170`。）

读过的文件：main 的 `teams/_group_chat/_selector_group_chat.py`、
`_base_group_chat_manager.py`、`_round_robin_group_chat.py`、
`_swarm_group_chat.py`、`state/_states.py`；v0.2.40 的
`autogen/agentchat/groupchat.py`（1668 行）。

**许可结论**：可移植（宽松 MIT，保留声明）

★★ **`_mentioned_agents`（`_selector_group_chat.py:310-341`，v0.2 同名函数
L967-1005 一字不差）——全场唯一真正的整词 @ 解析器**，也正是我们 C1 要的语义：

```python
# _selector_group_chat.py:310 附近
re.compile(rf"(?<=\W)({n}|{n.replace('_',' ')}|{n.replace('_','\\_')})(?=\W)", re.IGNORECASE)
```

要点：用**非捕获式词边界**（`(?<=\W)` / `(?=\W)`）而不是 `\b`，把消息**两侧补空格**
再 `findall`；返回 `{name: 命中次数}` 计数表，次数天然可用于「多次 @ 权重更高」。
MIT，可直接直译成 Kotlin `Regex`。

speaker selection：`select_speaker`(L152) 三级降级——①`selector_func` 返回非
null 则直接用（必须在 `_participant_names` 里，否则 `ValueError`）
②`candidate_func` 过滤候选集（空集即报错）③否则用 LLM 选。

- LLM 路径 `_select_speaker`(L232) 有 **`max_selector_attempts` 重试反馈环**
  (L247-300)：把模型的坏回答追加成 `AssistantMessage`，再追加一条
  `UserMessage` 反馈，三种话术分别是「没有提到有效名字，请从…中选」
  /「应恰好一个名字」/「不允许重复发言者」，最多 3 轮。
- 3 轮耗尽后兜底：先回退到上一位发言者，再不行取第一个参与者(L302-308)。
  **不抛异常、不中断整轮**——这个降级链值得照搬。
- `allow_repeated_speaker=False` 时默认把上一位从候选剔除，但**校验 @ 时仍用
  全名单**(L272 注释：模型可能仍选上一位，要抓住)。
- v0.2 的 `speaker_selection_method` 取值 `["auto","manual","random",
  "round_robin"]`(L151)，由 `_prepare_and_select_agents`(L426) 统一分派，
  支持传 callable 做自定义；2 人 + 允许重复会 warn。
- `max_consecutive_auto_reply` **不在 `groupchat.py` 实现**，只是透传给底层
  `ConversableAgent`(v0.2 L1016/L1033)。查资料时别把它当 groupchat 的能力。

终止：`BaseGroupChatManager._apply_termination_condition`(L195) 先问
`termination_condition`，再单独查 `max_turns`（`self._current_turn >=
self._max_turns`），命中就 `_signal_termination(StopMessage)` 并**复位
`_current_turn=0`**。`handle_pause` / `handle_resume` / `handle_reset`(L279-287)
已内建。v0.2 的 `a_run_chat`(L1213) 主循环是 `for i in range(groupchat.max_round)`：
每轮 append → 判终止 → 广播给所有非发言者 → 选下一位 → 生成；
`NoEligibleSpeaker` 优雅 break。

其他三点：

- **嵌套调用**：`Team` 可以直接作为 `SelectorGroupChat` 的 participant
  (L371-374)，即**子群聊当一个成员**。
- **没有投票/共识**：v0.2.40 全树 + main 全树 `git grep` 确认 autogen 任何
  投票/共识机制都不存在。我们 C1-R 的投票平票判定没有上游可对照。
- **状态存档**：`save_state` / `load_state`(L116-131) 把 `message_thread` +
  `current_turn` + `previous_speaker` 序列化进 `SelectorManagerState`，
  `reset()` 一键清空。**这是我们 round 存档的现成形状参考**（对应已落库的
  `group_runs`）。

### crewAI — crewAIInc/crewAI @ 738c8e19e35c2888d8e0663bc5cc45c5acf6ac2d

读过的文件：`crew.py`、`crews/utils.py`、`task.py`、`agent/core.py`、
`agents/crew_agent_executor.py`、`types/usage_metrics.py`、
`state/checkpoint_config.py`、`state/runtime.py`、
`memory/storage/kickoff_task_outputs_storage.py`、
`tools/agent_tools/delegate_work_tool.py`、`flow/conversational_mixin.py`、
`process.py`。许可证据：`LICENSE:3` 起 "Permission is hereby granted, free of
charge…" + `Copyright (c) 2025 crewAI, Inc.`。

**许可结论**：可移植（宽松 MIT，保留声明）

**编排**：只有 `Crew.kickoff` → `_run_sequential_process` /
`_run_hierarchical_process`(`crew.py:1055-1062`)，**完全没有 speaker 选择**，
`for task_index, task in enumerate(tasks)`(L1595) 静态顺序，`_get_agent_to_use`
只是查表。上下文靠 `_get_context`(L1878) 把**所有**前序 `TaskOutput.raw` 用
`"\n\n----------\n\n"` 拼成字符串注入 prompt——**这正是我们 pipeline
「上一角色输出作为下一角色输入」的现成实现思路**。

**上限与预算**：

| 机制 | 位置 | 行为 |
| --- | --- | --- |
| `BaseAgent.max_iter` | 默认 25 | `has_reached_max_iterations` 在每轮循环头检查 |
| 超限处理 | `handle_max_iterations_exceeded`(L366) | **不中止**，追加一条 user 消息「现在是时候给出你最好的最终答案了」再问一次 LLM |
| `max_execution_time` | L1013-1022 | `ThreadPoolExecutor` 超时，且 `TimeoutError` **故意不重试** |
| `UsageMetrics` | `types/usage_metrics.py:32` | `add_usage_metrics` / `delta_since(baseline)` / `from_provider_dict`（归一化 OpenAI/Gemini/Anthropic 三种 usage 字段名） |

⚠️ `UsageMetrics` **全是事后统计，从不用于中止**；`max_tokens` 只是单次请求
输出上限；**全仓无累计 token 预算闸门**。所以我们的「每轮预算」没有上游闸门可抄，
但 **`delta_since(baseline)` 正是我们需要的增量测量原语**，值得移植。

**失败语义**：Agent 级 `max_retry_limit` 默认 2，`_handle_execution_error`
**递归重跑整个 task**——不是续跑半个 task。
⚠️ **`Task._execute_core`(L957) 抛异常 → 发 `TaskFailedEvent` → 直接 re-raise，
整轮结束、无部分保留**；而且**没有 `Task.status` / `FAILED` 常量，
`TaskOutput` 也没有 status 字段**。两点合起来说明 crewAI **无法表达「哪一步失败、
前面成功的结果还在」**，这对我们「错误节点」设计是反面参考。

★ **幂等 / resume（意外收获，强）**：`KickoffTaskOutputsSQLiteStorage` 用
SQLite 表 `latest_kickoff_task_outputs(task_id PK, task_key, output, task_index,
was_replayed, …)` 存每个 task 输出；`Crew.replay(task_id)`(L2101) 找到
`start_index`，**为所有 `i < start_index` 的 task 从库里回填 `tasks[i].output`**，
再 `_execute_tasks(tasks, start_index)`。`Task.key` = `md5(description|expected_output)`
作为身份锚。**这就是「跳过已提交 turn、从失败角色续跑」的完整可移植实现**，
比我们已落库的幂等用例更完整。另有 `CheckpointConfig` / `RuntimeState` /
`Crew.from_checkpoint` / `Crew.fork`（`parent_id` + `branch` 列做 DAG 分支）。

**结构化输出**：`Task.output_pydantic` → `convert_to_model` →
`TaskOutput.pydantic/json_dict`；`build_task_prompt_with_schema` 把 JSON Schema
注入 prompt；guardrail 做「校验-重试」闸门。收束取**最后一个**非空 output(L1932)。

**视角隔离**：Crew 层每个 task `self.messages=[]`，但 context 字符串含**全部**
前序输出 → **无隔离**。唯一有价值的设计在 `flow/conversational_mixin.py`：
公开 `ConversationState.messages` 与私有 `agent_threads: dict[str, list[AgentMessage]]`
分离，`build_agent_context(agent_name)` 只拼「公开历史 + 该 agent 私有草稿」，
`append_agent_result(visibility="private")` 默认不入公开历史。
**这个「公开频道 + 每角色私有草稿」双层结构值得移植**——它是这三个项目里唯一
触及视角隔离的实现，且是 MIT。

**投票**：没有。`Process` 枚举只有 `sequential` / `hierarchical`，
`consensual` 是注释掉的 TODO(`process.py:11`)。

### 本节覆盖范围与许可分档

第 1 批记 SillyTavern / autogen / crewAI 三家，第 2 批（本节下方）补
langgraph / RisuAI / agnai，第 3 批（本节末尾）补 MetaGPT / camel /
consensus-core，第 4 批补 Operit / LianYu-app，第 5 批（本节最末）补 Knowe，
**12 家全覆盖**。本节已完备：12 家项目 + 仅候选 5 个 + 横向总表 + 移植优先级 +
构建基线（横向总表与优先级见本节最末两小节）。

许可档位共两档，不要混用：12 家里只有 **camel** 与 **LianYu-app** 落在
「仅借鉴机制，代码独立重写（Apache-2.0）」，其余十家都是「可移植」
（SillyTavern / agnai 是同协议 AGPL-3.0，RisuAI 是 GPL-3.0，**Operit 是
LGPL-3.0**，autogen / crewAI / langgraph / MetaGPT / consensus-core / Knowe 是
MIT）。Apache-2.0 §3 专利终止条款与 GPLv3 §3 冲突，FSF 判定与 GPL/AGPL
家族不兼容。所有结论都是读源码得出，没有复制任何上游代码，也没有运行上游
测试。

⚠️ 另有 **1 个已核验并排除**：lyricon（Xposed 状态栏歌词模块），见本节最末的
专节。它是「范畴外」**不是**「许可档」问题——七项需求没有一项它有实现，
硬塞进横向总表只会多出一整行「0」，所以不进对照表。

### langgraph — langchain-ai/langgraph @ 9a0394d88b2211f299dcd69df92db3480c69ee61

读过的文件：`graph/state.py`、`graph/message.py`、
`channels/{base,binop,last_value,topic,any_value,ephemeral_value,named_barrier_value,delta}.py`、
`libs/checkpoint/.../base/__init__.py`、`memory/__init__.py`、
`libs/checkpoint-sqlite/.../sqlite/__init__.py`、
`pregel/{main,_loop,_algo,_task_status,_runner,_write,_retry}.py`、`types.py`、
`errors.py`、`runtime.py`。许可证据：`LICENSE:1-3` "MIT License /
Copyright (c) 2024 LangChain, Inc."。

⚠️ **路径纠正**：checkpoint 库在 `libs/checkpoint/`，**不在**
`libs/langgraph/langgraph/checkpoint/`——按后者去找会直接扑空。

**许可结论**：可移植（宽松 MIT，保留声明）

★★ **这是我们「从失败角色续跑」最值得移植的东西，而且核心契约极小，
只有 5 条**（本轮信息量最大的一节）。langgraph 的续跑不靠「逐步快照
状态」这种重手段，而是**位置哈希 + 空 writes 判定**，两个原语就撑起了
全部语义：

| # | 契约 | 位置 | 行为 |
| --- | --- | --- | --- |
| 1 | task 身份 | `pregel/_algo.py:616-623` | `task_id = xxh3_128(checkpoint_id, checkpoint_ns, step, name, "PULL", *triggers)`——**只哈希位置，不哈希输出**，所以同一节点在同一 step 永远得到同一个 id |
| 2 | 完成判据 | `pregel/_task_status.py:29-30, 71, 99` | **一个 task 完成 ⟺ 它至少有一条非控制写**（模块文档 L29-30）；控制写 = `frozenset({ERROR, ERROR_SOURCE_NODE, INTERRUPT, RESUME})`(L71)；无输出而完成时由 `PregelRunner.commit` 补 `NO_WRITES` 标记，保证规则恒成立 |
| 3 | 写回填 | `pregel/_loop.py:767-773` | `_reapply_writes_to_succeeded_nodes` 在 tick 时把**已完成** task 的持久化写回填进新准备的 task；文档 L770-773 明写「未完成（失败或中断）的 task 保持空 writes，因此 runner 会重跑它们」 |
| 4 | 重跑过滤 | `pregel/main.py:2956` | 执行器只挑 `[t for t in loop.tasks.values() if not t.writes]`——**有回填 writes 的 task 永不重跑** |
| 5 | 折叠顺序 | `pregel/_algo.py:256` | `apply_writes` 先按 `task_path_str(path[:3])` **排序**再按 channel 归组，保证 reducer 的折叠顺序可复现 |

两条关键片段：

```python
# pregel/_algo.py:616
task_id = xxh3_128(checkpoint_id, checkpoint_ns, step, name, "PULL", *triggers)
# pregel/main.py:2956
tasks = [t for t in loop.tasks.values() if not t.writes]
```

第 1 条 + 第 4 条合起来就是我们「同一角色在同一轮次内不重跑、失败角色从
断点续跑」的完整判据，而且**不需要我们自建调度表**：位置即身份，
writes 非空即已完成。第 5 条是 autogen / crewAI 都没有的细节——多角色
并行写同一 channel 时，reducer 折叠顺序不固定的话，同一份 checkpoint
重放会得到不同结果，这个坑我们目前没意识到。

**checkpointer 直接映射我们的表结构**：键是三元组
`(thread_id, checkpoint_ns, checkpoint_id)`。SQLite DDL 是
`checkpoints(thread_id, checkpoint_ns, checkpoint_id, parent_checkpoint_id,
type, checkpoint, metadata)` 加
`writes(thread_id, checkpoint_ns, checkpoint_id, task_id, idx, channel, type,
value)`。四个业务维度一一对应：`thread_id→conversationId`、
`checkpoint_ns→roundId + roleId`、`task_id→turnSeq`。
`CheckpointTuple` = `config / checkpoint / metadata / parent_config /
pending_writes`，其中 **`pending_writes` 就是「已提交但还没被 reducer 折叠
的写」**，正好是我们「落库了但还没进下一角色上下文」那批消息。
★ `Checkpoint.id` 是 **UUIDv6（单调可排序）**，所以「取最新一条」不需要
比较 `created_at`，直接 `ORDER BY checkpoint_id DESC LIMIT 1`
(`libs/checkpoint-sqlite/.../sqlite/__init__.py:240`)。这个细节值得抄——
我们 `group_runs` 的 latest 判定目前依赖时间戳，同毫秒并发时会出错。

**resume 入口**：调用侧是 `graph.invoke(None, config)`，内部 `_first()` 判定
`is_resuming`。⚠️ **硬要求 checkpointer**，缺失即抛
`RuntimeError("Cannot use Command(resume=...) without checkpointer")`
(`pregel/_loop.py:930`)——这个前置条件要在 C1 的接口上显式声明，
不能等到运行时报错。
★ `get_state()` 返回 `StateSnapshot`，其 **`next` 字段 = 没有 recorded output
的 task 名**（`main.py:1322`）——**「谁还没跑完」的标准答案就在这里**，
比我们自建的状态查询干净，而且天然与契约 2 同源，不会自相矛盾。

**中断与取消**：

| 机制 | 位置 | 行为 |
| --- | --- | --- |
| `interrupt(value, response_schema=...)` | `types.py:893` | 节点内抛 `GraphInterrupt`，由 `Command(resume=...)` 消费。⚠️ 文档 L908 明说「resume 会**从节点开头重跑**，节点体必须幂等」——这是它给我们的警告，不是便利 |
| `RunControl.request_drain(reason)` | `runtime.py:95` | 置 `status="draining"`，`tick()` 在 superstep 边界返回 False |
| `GraphDrained` | `errors.py:57-60` | `stream()` 抛「检查点已保存，运行稍后可恢复」——**这正是我们「取消只写运行日志、不写虚构消息」想要的语义** |
| `NodeCancelledError` | `pregel/_retry.py:316-339` | 由 `CancelledError` 转换而来，保留任务身份 |

注意 drain 的粒度是 **superstep 边界**，不是 token 边界：已经吐出去的 token
不回收，但也不会被当成一条完整消息落库。这跟我们已定的取消语义一致，
可以照此对齐。

**路由与扇出**：`add_conditional_edges(source, path, path_map)` 就是
「下一个谁发言」；`Send(node, arg)`（`types.py:738`）**允许发给节点的 state
与主图不同** → 天然是「每 agent 私有视角」，比 crewAI 的字符串拼接干净得多，
也正好能承接 agnai 那套可见性子集模型。多起点 join 用 `NamedBarrierValue`
栅栏：`add_edge(["a","b"], "tally")` 表示等两个都触发才跑 → **正是我们
「等所有角色投完票再汇总」的机制**，而
`channels/named_barrier_value.py` 里就是按命名事件计数，同步原语不用我们
自己发明。

**没有的**：token 预算、@ 解析、投票。全仓 grep
`speaker|majority|consensus|mention` 仅 3 条无关注释——C1-R 的投票与平票
判定在这里依然没有上游可对照。

### RisuAI — kwaroran/RisuAI @ 9f3b589b6e74230401a431a00fd977986d212870

读过的文件：`src/ts/process/group.ts`、`src/ts/process/index.svelte.ts`、
`src/ts/storage/database.svelte.ts`、`src/ts/tokenizer.ts`、
`src/lib/SideBars/CharConfig.svelte`。许可证据：`LICENSE:1-4` "GNU GENERAL
PUBLIC LICENSE Version 3 … Copyright (C) 2024 Kwaroran"。

**许可结论**：可移植（GPL-3.0 强传染，与本项目 AGPL-3.0 同族，不增加负担）

理由：GPLv3 的传染义务在 AGPLv3 下已被本仓库根 `LICENSE` 全额履行——我们
分发时本来就必须提供全部源码并保持 AGPL-3.0，再引入 GPLv3 代码不新增任何
义务；相对 MIT 反而更省心（MIT 还要逐处保留声明）。本节唯一需要额外动作的
仍然是 Apache-2.0 那一档（见上文兼容矩阵）。

**发言顺序 `groupOrder(chars, input)`** 三段式，比 SillyTavern 的 `NATURAL`
更简洁：

| 段 | 判据 | 行为 |
| --- | --- | --- |
| ① 提及 | 只取 `input` 的**最后一条消息**，按 `/\n\| /g` 切词并 lower-case；角色名同样切词，**整词相等**即入列 | 出现顺序决定顺序，无加权、无随机 |
| ② 随机准入 | 剩余候选 `shuffle` 后逐个独立判定 `chance >= Math.random()`，`chance = talkness ?? 0.5` | 每人一次独立伯努利试验 |
| ③ 保底 | `while(order.length === 0)` | **无重试上限** |

```ts
// src/ts/process/group.ts — 第②段
shuffle(rest); for (const c of rest) if ((c.chance ?? 0.5) >= Math.random()) order.push(c);
```

⚠️ **第③段是死循环**：`chars` 为空时永不退出。**不要照搬**——SillyTavern 的
「最多重试 `randomPool.length` 次」才是正确形状，我们按后者实现。
①段的「只看最后一条消息」也比 SillyTavern 的整轮扫描窄：多行输入里前几行
@ 别人不会生效。

**反重复发言**：`groupOrder` 之后由调用方剔除 `id === lastMessage.saying`
的成员，即**上一条发言者本轮不再发言**。SillyTavern 的 `LIST` 用
`spokenSinceUser` 做同样的事，RisuAI 更轻，语义上更接近「不连麦」。

★ **确定性模式**：`orderByOrder=true` 时**完全跳过 `groupOrder`**，直接用成员
数组顺序 —— **这就是我们的 pipeline 模式**，连「跳过随机」的分支都现成，
连 SillyTavern 都要靠 `group_activation_strategy` 切策略值。

**调度**：`sendChat(-1)` 在群聊上**不生成**，只算顺序；然后 `for` 串行
`await sendChat(order[i].index)`，**首个 false 就中止整轮**。防重入靠
`abortChat` store + `doingChat` store 两个状态位。首个失败即中止、不补
半条消息，语义与我们一致。

⚠️ **零视角隔离，这是我们文档里「UI 隐藏但仍发送」的反面教材**：
`makeMs()` 只过滤 `msg.disabled`，**所有成员看完整共享记录**；他人消息只是
套一层 `<{{char}}'s Message>` 模板，再靠 `groupOtherBotRole` 把对方重映射
成 `'user'` 角色（默认 `'user'`）。**渲染层隐藏 ≠ 提示词层隔离**——角色名
和正文照样进了每一个 bot 的 prompt。我们已落库的 C1-M 空间闸门在这里找不到
可抄的实现，只能当负面对照记下来。

**token**：两段贪心裁剪——先按 `db.maxResponse` 扣减，再 drop-oldest；模板
替换后**重新 tokenize** 并逐条置空 `removable` 标记。是 **per-request 不是
per-round**，和我们一样，跨轮预算没有上游闸门。

**没有的**：投票、resume。
★ `src/ts/process/templates/jsonSchema.ts` 的 `convertInterfaceToSchema` +
`extractJSON(data, "a.b.c")` 点路径取值思路可参考——**重写，不要抄**：
GPLv3 代码并入 AGPLv3 仓库技术上可行，但点路径取值我们用 Kotlin 的
`JsonElement` 自己实现更干净，也不必携带 GPL 文件头。

### agnai — agnaistic/agnai @ fccee00f5f7628d150760c787c4be87e6b1a652c

读过的文件：`common/prompt.ts`、`common/util.ts`、
`common/guidance/json-schema.ts`、`common/requests/payloads.ts`、
`srv/api/chat/{message,lock}.ts`、`srv/adapter/payloads.ts`、
`web/pages/Chat/util.ts`、`web/store/message.ts`。许可证据：`LICENSE.md:1-4`
"GNU AFFERO GENERAL PUBLIC LICENSE Version 3"。

**许可结论**：可移植（同协议 AGPL-3.0，保留版权声明）

★★★ **三层视角隔离，本轮调研最好的隔离模型**——比 crewAI 的「公开频道 +
私有草稿」更完整，因为它落在**同一份消息列表的不同子集**上，而不是两份
各自漂移的存储。`isMessageInvisible(chat, msg, characterId)`
(`common/prompt.ts:786`) 是**严格优先级**的四级判定，先命中先返回：

| 优先级 | 数据位置 | 语义 |
| --- | --- | --- |
| ① 最高 | `msg.invisible?.[characterId]` | **单条消息 × 单个视角**的覆盖，最细粒度 |
| ② | `chat.invisibleChars?.[viewerId]?.[speakerId ?? 'none']` | 按 (视角, 发言者) 设默认可见性 |
| ③ | `chat.invisible?.[viewerId]` | 按视角的总开关 |
| ④ 兜底 | 以上皆无 | 可见 |

```ts
// common/prompt.ts:786 起，判定链压缩后
msg.invisible?.[characterId] ?? chat.invisibleChars?.[viewerId]?.[speakerId ?? 'none'] ?? chat.invisible?.[viewerId]
```

两个配套让这个模型真正可用。`getLinesForPrompt()`(L717) 用
`opts.replyAs._id` 当 viewer，**每个 bot 的 prompt 是同一份消息列表的不同
子集**（不各存一份，所以不会出现存储漂移）；再叠一层
`getMessageAuthor()`（`common/util.ts:343`）——**只有 `replyAs` 自己的消息
是 `role:'model'`，其他 bot 的消息全部降级为 `role:'user'` 并带 `name`
字段**。这一条尤其关键：它保证「A bot 不会把 B bot 的 assistant 回合当成
自己的上下文续写」，而 A bot 依然能看见 B 说过的话。
★ 整套语义可以整段照搬行为规格，三级优先级也正好覆盖我们 C1-M 的粗粒度
空间闸门（③）与将来可能的细粒度逐条覆盖（①）。

**@ 只在 UI 层**：`InputBar.tsx:405` 按 `@` 弹自动补全，选中后**把 `@` 字符
丢掉**只插纯名字。**服务端无任何解析**——所以 agnai 的「@ 某人」在读到的
代码里**不存在路由语义**，不能拿来对照我们的 @ 解析（我们的是服务端
语义）。

**无自动轮转**：`chat.replyAs` 是人工在「Auto-reply」选择器里设的状态，不是
轮转算法。但它有个很聪明的替代机制值得抄：`getStoppingStrings()`
(`common/requests/payloads.ts:639`) 给**除 replyAs 外的每一个参与者**注入
`"\n<OtherName>:"` 停止串——让模型自己写出多角色对话，由采样器在别人名字
出现时硬停。SillyTavern 是同款（`script.js:3045-3054`）；两家独立撞出同一
手法，说明这是社区共识做法。

**token**：`getContextLimit()` = `maxContextLength − maxTokens`，
**先给生成留额度再算输入上限**（这个顺序值得抄，我们容易写反）；
`fillPromptWithLines()` 自底向上填、**首次溢出即 `break`**，并预先扣掉所有
depth-insert 的成本。仍是 **per-request**，跨轮预算无闸门。

**幂等三件套**（这一段比 langgraph 更「工程」，比前两家更落地）：

| # | 机制 | 位置 | 行为 |
| --- | --- | --- | --- |
| 1 | TTL 互斥 | `srv/api/chat/lock.ts` | `obtainLock(chatId, ttl=10)`；冲突时**多成员会话优雅降级**——仍建用户消息、跳过生成，而不是报错 |
| 2 | 消息树 | `srv/api/chat/message.ts` | `parent` + `chat.treeLeafId`；`kind:'retry'` 带 `body.replacing` **原地改节点**，旧文本压进 `msg.retries[]` |
| 3 | 残缺提交 | `srv/api/chat/message.ts` | **`AbortError` 被当作「成功但残缺」**（`generated = partial; error = false`），已流式输出的部分照常提交 |

第 3 条与 langgraph 的 `GraphDrained`、SillyTavern 的「抛异常不写半条」
**取向相反**：agnai 提交半条，后两家丢弃半条。我们已定的是后者（不写虚构
消息），所以这条只作反例记录；但**第 1 条「锁冲突时降级而不是报错」值得
抄**——多成员同时开聊不该 500，我们的服务端要照此设计。

⚠️ `web/store/message.ts` 的 `processQueue()` **只在 `onSuccess` 里排空
队列**，失败项会**永久卡死整条队列**——这是真 bug，**别复现**。我们的
调度队列必须 `finally` 排空。

**没有投票**。但**结构化输出很强**，值得单列：`ResponseSchema` 有类型化字段表
+ `{{var}}` 模板；`prepareJsonSchema()` 返回 hydrator；
`getJsonSchemaPayload()` **一份字段出 4 种方言**（适配不同供应商的 schema
约束）；`parsePartialJson()` 增量修复流式残缺 JSON；
`ensureSafeSchema()` 在发送前**剥掉 `response` 字段**。
★ 最后这一招很实用：我们让模型「按 schema 回 JSON」时，同样需要把
「自然语言回复」字段从 schema 里摘掉，否则模型会把散文塞进结构化槽。

### MetaGPT — FoundationAgents/MetaGPT @ 11cdf466d042aece04fc6cfd13b28e1a70341b1f

读过的文件：`metagpt/roles/role.py`、`metagpt/team.py`、`metagpt/schema.py`、
`metagpt/environment/base_env.py`、
`metagpt/environment/werewolf/{werewolf_env,werewolf_ext_env,const}.py`、
`metagpt/ext/werewolf/roles/{moderator,base_player}.py`、
`metagpt/ext/werewolf/actions/moderator_actions.py`、
`metagpt/actions/requirement_analysis/evaluate_action.py`、
`metagpt/ext/stanford_town/plan/converse.py`、`metagpt/utils/cost_manager.py`。
许可证据：`LICENSE:1` "MIT License"。

**许可结论**：可移植（宽松 MIT，保留声明）

★★ **双层隔离，本批全场最佳，值得直接移植**。真正的差别在于**过滤发生在读取
时，而不是投递时**：

1. **读取时 ACL**：`WwMessage.restricted_to`（`ext/werewolf/schema.py:28`）在
   `base_player.py:62-67` 与 `moderator.py:145-149` 的 `_observe` 里逐条检查：

```python
# ext/werewolf/roles/base_player.py:62
if len(m.restricted_to) and self.profile not in m.restricted_to and self.name not in m.restricted_to: continue
```

   **投递时不裁剪**，而是每个视角各自渲染自己的可见子集。读时过滤强在**支持
   事后追溯改判**：消息落库后再改 ACL，历史轮次重放时隔离依然成立，不必回填
   重写历史。这正是我们 C1-M 空间闸门想要、但当前只有粗粒度开关的形态。

2. **prompt 脱敏**：`BasePlayer.get_all_memories`（`base_player.py:157-163`）
   渲染记忆时**只输出 `m.sent_from`（玩家名），绝不输出 `m.role`（真实
   身份）**。源码 L160 的注释就是我们的需求定义：「除 Moderator 外，其他
   角色使用 memory，只能用 `sent_from` 不能用 `role`，因为他们不知道说
   话者的身份」。**ACL 管「能不能看到这条消息」，脱敏管「看到了也不能知道
   说话者是谁」——两层解决的是不同问题，只做一层不够。**

**声明式轮转表**（我们要的 pipeline / roundtable 就是它）：`STEP_INSTRUCTIONS`
（`werewolf/const.py:36-121`）是 19 项有序表，每项形如
`{content, send_to, restricted_to}`：

| `send_to` 取值 | 行为 |
| --- | --- |
| `{MESSAGE_ROUTE_TO_ALL}` | 全员按表内顺序依次发言 |
| `{RoleType.X}` | 只有 X 收到 |

`WerewolfEnv.run`(L36-41) 串行驱动这张表。**用数据而不是 LLM 决定顺序**——
确定、可调试、零额外模型调用，正是我们要的形状。

⚠️ 但**默认 `Environment.run`（`base_env.py:197`）不能抄**：它是
`asyncio.gather` 并发扇出 everyone，会把「依次发言」退化成「同时发言」。我们
只借 `WerewolfEnv` 那个串行 override 的形状，不碰基类。

**议长模式**（`team.py` / `team_leader.py`）：`TeamLeader._get_prefix`
(team_leader.py:55) 把所有成员的 `name: profile, goal` 注入 leader 的 prompt；
leader 不直接发言，而是调用工具 `publish_team_message(content, send_to)` 自己
选收件人；且**每次发消息前 `_set_state(-1)` 强制让出**，不许 leader 独白连播。
**选收件人交给模型、发言顺序仍由表驱动**——这个分工值得抄。

**投票：两处实现，都有缺陷，只能抄思路**：

| # | 位置 | 机制 | 缺陷 |
| --- | --- | --- | --- |
| 1 | `evaluate_action.py:58` `_vote` | 同一问题跑 3 次，按 `(bool, 结论)` 分桶，**任一桶到 2 就早退**（2-of-3 自洽） | 三个全不同则掉出函数返回 `None`，**无 tie-break** |
| 2 | `werewolf_ext_env.py:244` `vote_kill_someone` | 收齐所有存活者投票、过滤弃权，`Counter(...).most_common()[0][0]` | **平票明确未解决**，源码 L255 挂着 `# TODO in case of tie vote` |

我们 C1-R 的平票判定必须自己设计，这两处都不能照搬。第 1 处的「3 次投票取
多数」倒是可以作为**单角色自纠错**（而非多角色表决）的候选手段。

⚠️ **反模式警示（本节最该记住的一条）**：MetaGPT 的幂等实现是给消息文本加
轮次前缀（`werewolf_env.py:33`）：

```python
# metagpt/environment/werewolf/werewolf_env.py:33
f"{self.round_cnt} | {msg.content}"
```

而 `role.py:215,229` 留了两行被注释掉的 `HACK`，承认「同一条消息会被自动
去重」破坏了 replay。**幂等键绝不能塞进用户可见文本**——我们的幂等键必须存在
消息之外的字段上。

**token**：`utils/cost_manager.py` 只做成本统计与预算提示，不参与截断决策。
**没有**：@ 解析、跨轮 token 预算闸门。

### camel — camel-ai/camel @ 0106b76830c707effe48cd3da384dda38cbed92e

读过的文件：`camel/societies/role_playing.py`、`camel/types/enums.py`、
`camel/agents/chat_agent.py`、`camel/messages/base.py`、
`camel/societies/workforce/{workforce,task_channel}.py`。许可证据：
`LICENSE:1-2` "Apache License Version 2.0" + `pyproject.toml:12`
`license = "Apache-2.0"`；全仓 grep AGPL/GPL 文本 **0 命中**。

**许可结论**：仅借鉴机制，代码独立重写（Apache-2.0）

理由（与上文兼容矩阵同源）：Apache-2.0 §3 给专利授权附了「专利诉讼终止」
条款，而 GPLv3 §3 明确禁止「针对 contributors 的专利主张」，两者语义冲突，
FSF 已判定 Apache-2.0 与 GPL/AGPL 家族不兼容。所以本节的**算法与行为规格
可以照搬，实现必须自己重写**，并在代码注释里注明思路出处。

⚠️ **纠正原假设**（上文「对原假设的纠正」第 3 条已记过，这里给源码依据）：
立项时以为 camel 提供多轮终止与共识 society，**四样都不存在**：

| 原假设 | 核验结果 |
| --- | --- |
| `RolePlaying(max_turns, termination_conditions)` | 不存在。`git grep "max_turns\|termination_condition"` 全仓 **0 命中** |
| `play()` 方法 | 不存在，只有 `step()` |
| `ConsensusTermination` | 不存在 |
| `camel/societies/` 下多个 society 子目录 | **只有 `workforce/` 一个子目录** |

`RolePlaying.step`(role_playing.py:631) 写死 user→assistant 两方乒乓，没有
多参与者调度。**camel 不能作为我们群聊轮次调度的参考**，只能作为「单 agent
记忆管理 + 多候选收敛」的参考。

仍值得看的四点：

| # | 位置 | 行为 | 对我们的价值 |
| --- | --- | --- | --- |
| 1 | `role_playing.py:516` `_reduce_message_options` | 用**单个 critic** 把 N 个候选收敛成 1 个 | ★ 「多候选 → 单一选择」的现成形状，可对应我们的 @ 解析 / 工具选择 |
| 2 | `chat_agent.py:567-575` | 每个 `ChatAgent` 有独立 `_memory: AgentMemory`，按 `agent_id` 隔离 | 与 crewAI 的私有草稿同思路，但**隔离落在 agent 对象本身**，更干净 |
| 3 | `chat_agent.py:666` `reset()` | 清记录后**恢复 system 记录**，不把系统提示一起清掉 | ★ 我们清群聊轮次上下文时必须照此保住 system 槽 |
| 4 | `chat_agent.py:845` `token_limit` | **只用于决定何时摘要**（调用点 L1038 / L1074），**从不中止生成** | ⚠️ 与我们「超预算即中止」的取向不同，只作对照 |

**resume 只有任务图一级**：`Workforce.resume_from_task(task_id)`
(workforce.py:2502) 要求任务处于 `PAUSED` 状态、状态在内存里、只能从任务图
节点恢复。**没有**按消息 / 按轮次的续跑——langgraph 那一套我们仍得自己写。
`task_channel.py` 的 channel 也只是 workforce 内部的消息传递，不构成隔离。

**没有的**：投票、视角隔离、@ 解析。

### consensus-core — entropyvortex/ai-consensus-core @ f65dec71383830a0ef47bbb83c69d5d703428f18

读过的文件：`src/engine.ts`、`src/stats.ts`、`src/parser.ts`、
`src/types.ts`。许可证据：`LICENSE:1` "MIT License"。

**许可结论**：可移植（宽松 MIT，保留声明）

TypeScript，**全仓仅 9 个源文件，且无 provider 耦合**——只接一个 `ModelCaller`
接口，所以它讨论的「共识」是纯算法，不掺任何模型厂商方言，可以整段对照。

★ **纯算术的共识评分（零 LLM 调用）**：

| # | 位置 | 行为 |
| --- | --- | --- |
| 1 | `stats.ts` `consensusScore` | `round(clamp(avg − 0.5·stddev, 0, 100))`——高均值 + 低离散得高分 |
| 2 | `stats.ts` L19-23 注释 | 用**总体标准差**（除 N 不除 N−1），刻意对齐 roundtable 参考实现 |
| 3 | `detectDisagreements` | 两两配对，置信度差 ≥ 20（可调）即记一条 `Disagreement{severity, label}`；**纯算术，无文本启发式** |

**收敛判定 / 早停**：`|score_k − score_{k−1}| ≤ convergenceDelta`（默认 3，
**仅从第 2 轮起检查**）→ `stopReason="converged"`（`engine.ts:189-200`）。
**这就是我们 roundtable 的收敛判定**——比「固定轮数」和「让 LLM 说自己同意」
都廉价，且阈值是一个可解释的标量。

★★ **盲首轮 = 现成的隔离原语**，两行代码分别给出盲首轮与 seed：

```ts
// src/engine.ts:109
const blind = round === 1 && opts.blindFirstRound;
```
```ts
// src/engine.ts:96
opts.randomSeed !== undefined ? mulberry32(opts.randomSeed) : Math.random
```

- **盲首轮**（默认 `true`）：`#runRound` 里 `if (blind)` 走
  `Promise.all(order.map(p => #callParticipant({ previousResponses: [] })))`
  （L310-328）——首轮**完全并行、互相不可见**。
- **非盲轮走串行**：`const visible = [...previousResponses, ...collected]`
  （L333），即**能看到之前所有轮 + 本轮已发言者**。

这与我们「群聊第一轮各角色独立起手、之后按发言顺序累积可见历史」的语义完全
对齐，**且盲首轮是开关而不是硬编码**——我们应把它做成配置项而不是固定行为。

★ **确定性重放**：`mulberry32(seed)` PRNG + Fisher-Yates `shuffle(input, rng)`
（`stats.ts` 末两函数），seed 未给时才退回 `Math.random`。**同一 seed → 同一
发言顺序 → 可重放**，直接服务我们的幂等与复现需求。

**取消**：`throwIfAborted(signal)` 在**每个角色发言前**检查（L332）——与
SillyTavern 同款，粒度正好对齐我们的轮次取消。

⚠️ **解析层的两个细节值得抄**（`parser.ts`）：

| # | 行为 | 理由 |
| --- | --- | --- |
| 1 | `extractConfidence` 用线性字符串解析，**不用正则** | 避免 ReDoS；模型输出是不可信输入 |
| 2 | 缺 `CONFIDENCE: N` 标记时**默认 50** | 注释 L20-23：缺标记是**模型不合规，不该惩罚** |

第 2 条尤其重要——把「模型没按规定输出」当成低置信度会系统性压低分数，让早停
永不触发。

**没有的**：@ 解析、跨轮 token 预算、resume。

### 仅候选（未读源码，不进对照表）

⚠️ 以下项目**只看过 README / 描述，没有读源码**，因此**不进上面的对照表**，
结论仅供参考，不能作为验收或移植依据。

| 项目 | 许可 | 说明 |
| --- | --- | --- |
| `lkaesberg/decision-protocols` @ `fd6f7c4146c29c620dc2fc56a9305e353262ce2a` | Apache-2.0 | ACL 2025 Findings 论文《Voting or Consensus? Decision-Making in Multi-Agent Debate》的仓库。结论对我们 `vote` 模式的默认值有参考价值：**知识任务共识优于投票，推理任务投票优于共识**。⚠️ 但**仓库只有 5 个 experiment JSON + README，没有任何源码**，无从验证 |
| `raiyanyahya/ensemble` | MIT | 未读源码 |
| `Karma-234/llm-consensus` | MIT（Go） | 未读源码。描述里的 `max_total_tokens` **跨阶段预算** + `strict_unanimity` 看下来最贴近我们的 per-round 预算，追加时优先读它 |
| `zahemen9900/agora` | MIT | 未读源码。`quorum_threshold` + Merkle receipt 值得对照我们的投票记录可验证性 |
| MALLM（EMNLP 2025 demo） | 非代码 | 论文里的协议分类 **Consensus{多数 / 超多数 / 一致} / Voting{Simple / Approval / Ranked / Cumulative} / Judge** 可直接当 `vote` 模式的设计清单——但这是论文不是实现，别当可移植代码 |

### Operit — AAswordman/Operit @ dbf71916fae9750cfdc9f9a774f5a0fee56633fb

**SHA 核实过程**（GitHub REST API 经本机代理返回 403，改走 commit atom feed）：
`github.com/AAswordman/Operit/commits/main.atom` 的首条 entry
`<id>tag:github.com,2008:Grit::Commit/dbf71916fae9750cfdc9f9a774f5a0fee56633fb</id>`，
`<updated>2026-09-18T02:30:36Z</updated>`，标题 `chore(release): sync dev to
main for 1.12.2`；再用网页版 `/commits/main` 交叉验证，最新一条同样是
`dbf71916fae9750cfdc9f9a774f5a0fee56633fb`（页面显示 "Commits on Sep 18,
2026"），两者一致，**SHA 已核实**。

⚠️ **一个日期坑**：调研时看到的「最后 push 2026-10-01」是仓库级 `pushed_at`，
对应 **dev 等其它分支**的推送；**main 的 HEAD 停在 2026-09-18**。本文引用的一律
是 main HEAD，不要把这两个日期混着写。仓库 590MB，**全程未 clone**。

读过的文件：`LICENSE`（全文 165 行）、`README.md`、`README.zh-CN.md`、
`.gitmodules`、`docs/TOOLPKG_FORMAT_GUIDE.md`（1598 行）、
`docs/doc-src/package-dev/ui.md`（202 行）、
`docs/doc-src/package-dev/tool-types.md`、
`docs/doc-src/architecture/RENDERER_ARCH.md`、
`docs/doc-src/architecture/memory_candidate_scoring_formula.md`、
`docs/doc-src/feature-protocol/tool_permissions.md`、
`data/model/CharacterGroupCard.kt`、
`data/preferences/CharacterGroupCardManager.kt`、
`services/core/MessageCoordinationDelegate.kt`（2053 行）、
`core/chat/AIMessageManager.kt`（1454 行）、`core/config/SystemPromptConfig.kt`、
`core/config/FunctionalPrompts.kt`、
`core/tools/packTool/ToolPkgComposeDslParser.kt`（207 行）、
`ui/common/composedsl/ToolPkgComposeDslGeneratedRegistry.kt`（91 行）、
`ui/common/composedsl/ToolPkgComposeDslGeneratedRenderers.kt`（3381 行）、
`ui/common/composedsl/ToolPkgComposeDslScreen.kt`（4733 行）、
`ui/features/chat/components/part/StatusCardHtmlDocument.kt`、
`ui/features/chat/components/part/CustomXmlRenderer.kt`、
`ui/features/settings/screens/TagMarketBilingualData.kt`。

**许可结论**：可移植（LGPL-3.0 库代码可并入 AGPL-3.0 作品，保留声明）

规模：8.4k star / 692 fork，101 open issue，main HEAD 对应版本 1.12.2。

⚠️ **协议四重确认，且 AGPL-3.0 已排除**（这一步不能靠猜）：

| # | 证据 | 指向 |
| --- | --- | --- |
| 1 | `LICENSE:1` "GNU LESSER GENERAL PUBLIC LICENSE Version 3"，全文仅 165 行 / 7.6KB | LGPL-3.0 |
| 2 | `README.md:229` "licensed under GNU LGPL v3 (LGPL-3.0-only)" | LGPL-3.0 |
| 3 | `README.zh-CN.md:228` 同句中文 | LGPL-3.0 |
| 4 | GitHub API `spdx_id: LGPL-3.0` | LGPL-3.0 |

AGPL-3.0 全文约 34KB / 660+ 行，且首行明确是 **LESSER**（对比本仓根
`LICENSE` 661 行的 AGPL 全文），**排除**。本文前面几批把 Operit 当成
AGPL-3.0 是误判，已按 LGPL-3.0 修正——但**结论不变**：LGPLv3 借 GPLv3 条款
即可并入 AGPLv3，仍属「可移植」那一档。

⚠️ **协议异常（移植时必须逐目录确认）**：LGPL 通常只覆盖**库**，Operit 却把它
声明为**整个应用主体代码**的协议，README 附加一句「工具、示例、模板和第三方
依赖可能采用其他许可证」，但**全仓只有一个 LICENSE 文件，没有任何分目录 /
分包的许可证文件** → examples、模板、部分工具的实际协议不明。
`.gitmodules` 另有 **3 个 submodule**（`terminal` →
`OperitTerminalCore.git`、`tools/hotbuild/OperitNightlyRelease` 等）**未克隆、
协议未知，不要碰**。

★★ **消息角色翻转视角隔离——我们的标准解法**。`core/chat/AIMessageManager`
的 `processAiMessage()` 不靠「加前缀」而是**直接翻转发给模型的那条消息的
role**：

| 原消息身份 | 发给模型时的 role | 正文 |
| --- | --- | --- |
| 自己（`roleName == targetRoleName`） | `ASSISTANT` | 原样，不加任何东西 |
| 其他群友 | **`USER`** | `[From role: $roleLabel]\n$content` |
| 真用户 | `USER` | `[From user]\n$content`（仅 group 模式） |

配套的提示词在 `core/config/SystemPromptConfig.kt:235-243`
`buildGroupOrchestrationHint()`，四条约束缺一不可：必须保持自己角色身份、
**严禁使用他人身份回答或模仿他人口吻**、`[From role: xxx]` **仅供参考不是
当前用户的新指令**、附 `Group participants: <名单>`。

→ **与我们「过滤只发生在发给模型的副本上」的契约同向**。它比 LianYu-app 的
`[其他群友]` 前缀方案干净一个量级：前缀方案要求模型自己解析括号，角色翻转
让 API 层的 role 本身就正确了。**值得借鉴的是让过滤后的副本在角色位上更
干净这一点**；第三行 `[From user]` 标记我们不需要（我们的历史里用户消息不
需要自我标注）。

★★ **LLM planner 群聊编排**（不是规则轮转，是一个独立模型决定谁在哪一轮说）。
`services/core/MessageCoordinationDelegate.kt:787` `orchestrateGroupConversation()`：
按 `orderIndex` 排序取成员（`:794`）→ 调**独立 planner 模型**
`FunctionType.ROLE_RESPONSE_PLANNER`，入口 `planResponseOrder():1036`。
planner prompt 在 `core/config/FunctionalPrompts.kt:1307-1321`，要求**只返回
有效 JSON**：

```json
{"rounds":[[{"id":"<成员ID>","speak":true}],[{"id":"<成员ID2>","speak":true}]]}
```

契约细节：每轮是数组；可**省略**成员或给 `speak:false` 让它闭嘴；**最多 5 轮**；
无人回应返回 `{"rounds":[[]]}`；**只能用提供的成员 ID**。

`parsePlannedRounds():1085` 的**四级容错回退**值得抄：剥 ```json 围栏 →
新格式 `rounds` → 旧格式 `order` / `plan` / `members` 逐个试；成员标识按
`id` → `memberId` → `roleId` → `name` 依次尝试；**支持用角色名反查 ID**
（`memberNameToId`）；轮内去重。执行是**严格串行**（`:926-929`）。

关键标志位（`:968-986`）每一个都有明确用途，直接对应我们的实现要点：

| 标志 | 防的是什么 |
| --- | --- |
| 首成员复用已落库的用户消息 `prebuiltMessageContent` + `suppressUserMessageInHistory=true` | **防模型收到两份用户消息**（有对应修复提交 `567f245ad99a17210ba437fd630ef3baa5a88206` "fix(chat): stop duplicate user message delivery in group orchestration"） |
| `forceDisableSummary=true` | 编排轮里禁止触发自动总结 |
| `enableGroupOrchestration=false` | **防递归**（编排内不再套编排） |
| `isGroupOrchestrationTurn=true` | 让下游知道当前是编排轮 |

「planner 只看用户原文 + 成员列表，不看群聊历史」这一点是它**没有** @ 路由的
根因（见下）。

★★ **Compose DSL IR——对 D0 卡片价值最高的一块**。数据结构在
`core/tools/packTool/ToolPkgComposeDslParser.kt:7-18`，非常薄：

- `ToolPkgComposeDslNode{type: String, props: Map<String,Any?>, children: List<Node>, slots: Map<String,List<Node>>}`
- `ToolPkgComposeDslRenderResult{tree, state, memo}`

`slots` 是**具名插槽**，支持 `AdaptiveSidePanel` 的 `side` / 主内容这类双插槽
布局——这正是我们卡片布局最容易写死的地方。共 **91 个组件**，分三层：

| 层 | 文件 | 行数 | 职责 |
| --- | --- | --- | --- |
| 注册表 | `ToolPkgComposeDslGeneratedRegistry.kt` | 91 | 名字 → 渲染函数 |
| 渲染器 | `ToolPkgComposeDslGeneratedRenderers.kt` | 3381 | **代码生成**，文件头写明 `AUTO-GENERATED ... Regenerate via tools/compose_dsl/generate_compose_dsl_artifacts.py` |
| 解释器 | `ToolPkgComposeDslScreen.kt` | 4733 | 薄解释器，只负责递归 + 派发 |

**架构结论：「代码生成 + 薄解释器」比手写 4733 行解释器健康得多**——组件多了
以后解释器不可能手写得住。`ctx` 提供的动作有 `useState` / `useMemo` /
`callTool` / `readResource` / `showToast` / `navigate` / `openFilePicker` 等；
事件绑定用 `extractActionId()`（`ToolPkgComposeDslScreen.kt:31-45`），**同时
接受对象 / Map / 字符串 `__action:<id>` 三种形式**（模型输出不可信，容错要给
足）；`ctx.UI.AiChat()` 可以把宿主的主聊天组件直接嵌进卡片。

**XML 卡片格式（prompt 驱动）**。权威定义在
`ui/features/settings/screens/TagMarketBilingualData.kt:183-253`：
`<html class="status-card|info-card|warning-card|success-card|metric-grid" color="#RRGGBB">`
作外层，内嵌 `<metric label value icon color>`、`<badge type="success|info|warning|error" icon>`、
`<progress value label>`。三条硬约束写进 prompt：**卡片内禁用 h1–h6**、
**用 Material Symbols 不用 emoji**、**metric 的 label 用简短英文**。
渲染管线：`CustomXmlRenderer.applyBuiltInStyles():1494` →
`processInlineComponents():1509+` → `StatusCardHtmlDocument.build():1427`
→ **每张卡片一个独立 WebView**（隔离样式，但也意味着每卡一份 WebView 开销）。

**ToolPkg 格式规范**（`docs/TOOLPKG_FORMAT_GUIDE.md`，1598 行，本批读得最细的
一份规范）。`.toolpkg` 就是 ZIP，`manifest.json` / `manifest.hjson` 唯一必需。
顶层字段：必需 `schema_version` / `toolpkg_id` / `main`；可选 `version` /
`api_version` / `requires` / `author` / `display_name` / `description` /
`logo` / `subpackages` / `resources` / `wasm_modules` /
`workflow_templates` / `workspace_templates`。

★ **`requires[{id,description,min_version,max_version}]` 这个依赖声明设计得
确实好**，五条行为都对我们有用：依赖包**先于**声明方加载（一个字段同时表达
了依赖关系和拓扑顺序）；启用声明方时宿主**自动**加入依赖；被依赖包**不能直接
关闭**；**循环依赖导致相关包加载失败**（不静默降级）；版本范围针对包自身的
`version` 而**不是** `api_version`。API 版本兼容矩阵：Operit `1.12.1+4` 之前只
支持 ToolPkg API `1.0.0`，之后支持 `1.0.0` + `1.0.1`。

⚠️ **hooks 全在 `main.js` 的 `registerToolPkg()` 里注册，不写进 manifest**：
`registerToolPkgUiModule` / `registerUiRoute` / `registerNavigationEntry` /
`registerDesktopWidget` / `registerAppLifecycleHook` /
**`registerMessageProcessingPlugin`**（可接管整条消息流）/
`registerXmlRenderPlugin`（按 tag 拦截）/ `registerInputMenuTogglePlugin` /
`registerChatMessageMenuItem`（`senders:["user","ai"]`，带 `order` / `icon` /
`dialog`）。
⚠️ **注册阶段有陷阱**：`registerToolPkg()` 里只**声明**注册项，
`ToolPkg.readResource(...)` 和 `ToolPkg.wasm.call(...)` 在此阶段**立即抛
异常**（`:390`）——不能在注册回调里读资源或跑 wasm。
⚠️ **没有 Provider 抽象**：工具就是 JS 函数，靠 `METADATA` 注释块声明元数据，
工具名**自动**注册为 `<subpackage_id>:<tool_name>`（`:296`）。这一点对我们是
反面教材——我们已有正式的 Provider 抽象，不要为了对齐它而退化。
权限三级 `ALLOW` / `ASK` / `FORBID`
（`docs/doc-src/feature-protocol/tool_permissions.md`）：全局默认 + 单工具例外
**覆盖**全局默认，且**这是宿主 App 的设置项，不是 manifest 字段**（包本身无权
自我扩权，这条边界值得抄）。

**记忆候选打分公式**（`docs/doc-src/architecture/memory_candidate_scoring_formula.md`，
三段式，是我们 12 家里唯一给出完整可复现公式的）：

1. **本地打分** `S(m) = S_kw + S_rev + S_sem^norm + S_graph`。关键词命中带
   **覆盖率增益** `λ_m = 1 + 0.6·(c_m/F)`（命中字段占比越高越可信），排名衰减
   `Δ_kw = (p_m·W_kw)/(k_0+r)·λ_m`；反向包含 `Δ_rev = (p_m·W_kw)/(k_0+r)`；
   语义分 `Δ_sem = √p_m/(k_0+r_t) + W_sem·sim(m,t)`，且 `sim < τ` 直接不计分；
   **长度归一化** `S_sem^norm = (1/√K)·Σ Δ_sem`——**专门防「关键词多天然高分」**；
   关系传播 `Δ_graph(u→v) = S(u)·w_uv·W_edge + β·W_edge`。阈值 θ 过滤后取前
   **15** 条。
2. **模型裁决**：模型输出 `{main,new,update,merge,links,user}` 结构化决策，
   判断无长期价值就**返回空对象不写入**。
3. **有序写回**：**先 merge → 再 update → 再 main/new → 最后建关系**——先合并
   掉重复项再更新，才能减少重复和冲突；建边硬约束是**源和目标都必须已存在**
   且**需明确证据**。

`data/model/CharacterGroupCard.kt` + `data/preferences/CharacterGroupCardManager.kt`
是群名片（角色卡）的存储与偏好管理。

**它没有的**（这四项恰好是我们必须自己做的）：

| 缺口 | 证据 |
| --- | --- |
| **@ 提及路由** | `MessageCoordinationDelegate` **零 mention 逻辑**；planner 只看用户原文 + 成员列表，所以用户点名某人时 planner 未必让那人开口 |
| **群聊级 token 预算** | 只有 `chatContextSettings.summaryTokenThreshold`（总结阈值）+ `maxTokens`，**无 per-role 配额、无轮次熔断** |
| **结构化投票** | `vote` / `voting` / `ballot` **零命中** |
| **真持久化幂等** | `PendingAutoContinuationRequest:110-120` + `pendingAutoContinuationByChatId` ConcurrentHashMap（`:122`）——**进程死亡即丢，不算真幂等** |

⚠️ **协议坑**：`docs/doc-src/package-dev/ui.md` 名字像「UI 模块开发」，实际是
`Tools.UI` **无障碍树自动化**（`getPageInfo` / `clickElement` / `UINode` 树遍历 /
`bounds` 格式 `"[x1,y1][x2,y2]"`），用于**操控别的 App**，与我们自己的卡片
DSL 无关。真正的 UI DSL 在 `TOOLPKG_FORMAT_GUIDE.md` 第 6 节。
`docs/doc-src/architecture/RENDERER_ARCH.md` 讲的是 XML 渲染架构。

### LianYu-app — Sylvara-Lin/LianYu-app @ 688b0d7ce3b27ce52ebac40c6429d22fd0f5ab08

**SHA 核实过程**：GitHub REST API 403，改用
`github.com/Sylvara-Lin/LianYu-app/commits.atom`，首条 entry 为
`688b0d7ce3b27ce52ebac40c6429d22fd0f5ab08`，`<updated>2026-07-04T17:43:14Z</updated>`，
标题 `docs: clarify placeholder API UI`；且该 40 位 SHA 的前 7 位 `688b0d7` 与
调研时独立记下的短 SHA **一致**，交叉验证通过，**SHA 已核实**。

**许可结论**：仅借鉴机制，代码独立重写（Apache-2.0）

许可三方确认：`LICENSE` 202 行、首行 "Apache License Version 2.0"；
`NOTICE` 写 "LianYu / 恋语 / Copyright 2026 Sylvara-Lin and LianYu contributors"；
GitHub API `spdx_id: Apache-2.0`。Apache-2.0 与 GPL/AGPL 家族不兼容（§3 专利
终止条款 vs GPLv3 §3 禁止专利主张），所以**一行都不能拷**。
⚠️ 但仓库有 **11 位 CONTRIBUTORS**（`CONTRIBUTORS.md`）——将来即使协议变更、
真要拷贝，也必须**保留全部署名**。

**定位：机制灵感来源，不是代码基座。** Kotlin 2.2.10 / Compose /
feature-based **16 个模块**（1 个 `:app` + 9 个 `feature:*` + 6 个 `core:*`）；
minSdk 26 / targetSdk 35。star 206 / fork 69。⚠️ **已停更约 3 个月**：只有
**22 个 commit**，最后一次是 2026-07-04（就是上面那个 SHA），README「开源版声明」
明确这是 **Public Edition**——私有中继、密钥、VMP / 反调试加固都已移除，
`core:security` 只是个 **no-op stub**。⚠️ 仓库里躺着 `fake.jks` /
`fake.keystore` 假签名文件（**签名文件绝不能进任何仓库**，包括我们的）。
不拿它当基座还有两条硬理由：**大量 `Log.d` 调试日志**、**prompt 硬编码成
中文常量**。

读过的文件：`LICENSE`、`NOTICE`、`README.md`、`AGENTS.md`、
`settings.gradle.kts`、`core/database/.../model/ChatGroup.kt`、
`core/database/.../model/CompanionEntity.kt`、
`core/database/.../model/MemoryEntry.kt`、
`core/database/.../model/TokenUsage.kt`、`core/database/.../repository/`、
`core/domain/.../AiTool.kt`、`core/domain/.../MemoryProvider.kt`、
`core/common/.../ChatConstants.kt`、`core/network/.../AiService.kt`、
`feature/groupchat/.../GroupChatViewModel.kt`（1113 行）、
`feature/groupchat/.../GroupChatStrategy.kt`（221 行）、
`feature/groupchat/.../mention/MentionParser.kt`、
`feature/groupchat/.../mention/MentionNormalizer.kt`、
`feature/groupchat/.../mention/MentionEnhancer.kt`、
`feature/groupchat/.../ui/`（3285 行，未逐行读）、
`feature/memory/.../engine/MemoryManager.kt`、
`feature/memory/.../engine/MemoryItem.kt`、
`feature/coffee/.../LuckinCoffeeTools.kt`。

★ **@ 提及路由三段式——这是唯一值得重点借鉴的一块**，
`feature/groupchat/.../mention/` 三个文件各管一段：

| 段 | 文件 / 方法 | 做什么 |
| --- | --- | --- |
| 1. 显式 | `MentionParser.extractMentionedCharacterIds()` | 匹配用户直接写的 @ |
| 2. 隐式归一化 | `MentionNormalizer.normalizeImplicitMentions()` | 隐式句式正则，如「XX你怎么看」「问问XX」「XX呢？」→ 前置成 `@XX ` |
| 3. AI 补全 | `MentionEnhancer.enhanceMentionsForAssistantReply()` | LLM judge 自动补 @，prompt 里直接教模型「善用@功能」 |

**Operit 完全没有这一块**，这是目前见过的**最完整的 Android 实现**。
⚠️ **正因为 Apache-2.0 不可拷贝，我们只能「按机制重写」**：照搬的是三段式
**流程规格**（显式 → 隐式归一化 → AI 补全），Kotlin 实现自己写。

关键取舍：我们**底层整词匹配已经有自己的实现**（`GroupChat.parseMentions`，
`core/data/model/GroupChat.kt:454-457`，整词 `@name` 或 `@roleId`），
所以**真正值得借鉴的只有后两段**——**「隐式句式补 @」**和**「AI 自动补 @」**。
第 1 段不必再看它的。

可参考不照搬：`GroupChatViewModel.buildIsolatedHistorySnapshot():458` 的
**过滤规则**——可见条件是 `msg.companionId == -1L`（即用户消息）**或**等于当前
角色**或**已在 `repliedSoFar` 里；他人消息统一加前缀 `[其他群友] ` 降级。
规则思路可用，**但前缀方案不如 Operit 的消息角色翻转干净**（Operit 那份直接
把 role 翻成 `USER`，见上一节），所以**只取过滤规则，不取前缀**。

`calculateSpeakingPriority():478` 的**启发式打分权重**（一组可直接对照的常量）：

| 条件 | 分值 |
| --- | --- |
| 被用户 @ | **+100** |
| 近 10 条历史里被点名 | **×30** |
| 从未发言 | +20 |
| 超 5 分钟未发言 | +15 |
| 近 8 条未发言 | +10 |
| 发言 ≥ 3 次 | −15 |
| 人设关键词命中 | ×5 |
| 末尾 | **+Random(0,10)** |

最后那个随机项是**故意加的**，用来制造「不像机器人轮流报数」的自然感。
调度策略：**第 1 轮只让被 @ 的角色发言**（按分数降序）；**第 2 轮起全员
`shuffled()` 随机**。同轮内用 `async` 并发 + 按 index 递增的随机 delay
（100~1200ms）模拟真人打字间隔。

`processCompanionReply():342` 的**重复回复抑制**：把回复 strip 掉 `@` 和空白后
取前 **20 字**，与该角色**最近 3 条**做包含关系比对，命中就丢弃。规则简单但
有效。

**记忆**：`feature/memory/engine/MemoryManager.kt` + `MemoryItem`，三域三层
正交——`scope ∈ {GLOBAL, COMPANION, GROUP}` × `tier ∈ {SHORT, MID, LONG}`。

其它有的：多角色（`ChatGroup.companionIds` 用 **CSV 存成员**，取用走
`getCompanionIdList()`）、群聊 UI（`GroupChatScreen.kt` 1037 行纯 Compose
气泡）、工具调用（`core/domain/.../AiTool.kt` 用 OpenAI function-calling JSON
Schema，但**只有 1 个示例模块** `feature/coffee/LuckinCoffeeTools.kt`，6 个工具）。

**其它没有的**：

| 缺口 | 证据 |
| --- | --- |
| token 预算 | `TokenUsage` entity 按日聚合，**纯报表，无预算约束、无截断逻辑** |
| 结构化投票 | 全仓 grep `vote` / `voting` / `ballot` **零命中** |
| 幂等续跑 | 仅进程内 `isLoadingLock`，**无 checkpoint / resume token** |
| 角色卡导入导出 | README 路线图列为 Mid Term **未完成**，全仓无相关代码 |
| 卡片 UI DSL | **无任何卡片 schema**；grep 命中的 `*Card*.kt` 全是 Compose UI 装饰组件（如 `SettingsCardComponents.kt`、`PetalApiCards.kt`），不是声明式卡片格式 |
| Agent 编排 / 多轮工具编排 | **无 agent loop、无 planner、无子代理** |

⚠️⚠️ **陷阱，必须醒目：真实生效的群聊逻辑不在 `GroupChatStrategy.kt` 里。**
`feature/groupchat/.../GroupChatStrategy.kt` 是**死代码、零引用**——全仓 grep
`GroupChatStrategy` 除了它自身文件外**零命中**；而且该文件自己的注释
（`:16-21`）就承认了：「GroupChatViewModel 中仍保留这 4 个方法的内联实现，
当前所有调用点仍走 ViewModel 内部方法」。
`MentionParser.formatHistoryForGroup():73` 同样零引用。
**真实生效的群聊逻辑全部内联在 `GroupChatViewModel.kt` 里**（重复了 Strategy
的那 4 个方法）。**只读 Strategy 会得出完全相反的结论**——读它会以为策略是
可配置可替换的，实际上改它没有任何效果。读这一家必须先 grep 引用再决定信谁。

### Knowe — HirezmingD/Knowe-agent-groupchat @ 23411a014d3ab2f04defbd6221b25f2db932708f

正式名「Knowe 知知智能体」。Electron + React 18 + TS 前端 / Python 3.11 异步后端，
本机 WebSocket `127.0.0.1:8080`；`package.json:4` version `1.0.39`，后端
`backend/engine.py` 单文件 **9612 行**、`server.py` 7379 行、`hub.py` 576 行、
`persist.py` 1014 行，`backend/knowe_harness/completion.py` 3127 行。

**SHA 核实过程**：`github.com/HirezmingD/Knowe-agent-groupchat/commits/main.atom`
首条 entry 为
`23411a014d3ab2f04defbd6221b25f2db932708f`，`<updated>2026-08-26T08:15:30Z</updated>`，
标题 `v1.0.39.3: 多模型预存切换功能（模型路由池）`；网页版 `/commits/main` 与本地
`git rev-parse HEAD` 三方一致，**SHA 已核实**。

读过的文件：`LICENSE`（全文 21 行）、`README.md`、`README.zh-CN.md`、
`package.json`、`CITATION.cff`、`backend/runtime.py`、`backend/server.py`、
`backend/engine.py`、`backend/mentions.py`（188 行）、`backend/hub.py`、
`backend/knowe_harness/completion.py`、`backend/persist.py`、
`backend/worker_gateway_runtime.py`、`backend/seen_speech.py`、`backend/ring.py`、
`backend/tool_ledger.py`、`backend/gate.py`、`backend/souls/coordinator.txt`、
`backend/prompts/zh/worker_prompt.md`（37 行）。

**许可结论**：可移植（宽松 MIT，保留声明）

⚠️ **协议四方确认**：①`LICENSE:1-2` "MIT License / Copyright (c) 2026 Hezhou
Jiang"，全文仅 **21 行**（AGPL-3.0 全文 660+ 行，可直接排除）；②`README.md`
License 节；③`README.zh-CN.md`「本项目采用 MIT 协议」；④`package.json:6`
`"license": "MIT"` + `CITATION.cff` 的 `license: MIT` + 网页版 sidebar 标签。
⚠️ **AGPL / LGPL 排除的准确证据**：源码树（`backend/` + `src/` + 全部 `.md`）
grep `agpl|lesser general|gpl-3|gplv3` = **0 命中**；唯一的命中在
`package-lock.json`（第三方依赖自己的 license 字段，如 `LGPL-3.0-or-later`），
**与本项目协议无关**，别把它读成「Knowe 沾了 LGPL」。

⚠️⚠️ **必须先记的架构纠正：它不是共享可见历史的多角色群聊，是「Coordinator +
Worker 派单」。** 决定性证据是 `backend/runtime.py:925-974` 的 `_initial_messages()`：
Worker 的 LLM 消息列表**每个 attempt 从零构造、只有 2 条**——system =
`worker_prompt.md`（`:957`，`:959-966` 再把自己的 worklog 尾部并进来）；user =
`"Task envelope:\n" + json.dumps(task_payload)`（`:952-953` 拼串、`:958` 落位）。
**没有任何群聊历史。**
`prompts/zh/worker_prompt.md` 全文 37 行只讲工具纪律与交付口径，不讲「别人说了什么」；
`souls/coordinator.txt:46` 明写 propose_next「**一次只派一件**」，同一条的后半要求
「成员交差 → 成果直接呈现给用户，**你不需要读报告**」；`:34` 明写
「**系统没有队列**——别说『先排着』」。

**所以它是 C1 的另一条路线（隔离到零共享 + 单协调者派单），不是同类。** 这不降低
它的价值——**幂等、收束状态机、@ 解析三块是本批最强的补充**，但把它当「多角色群聊
参考实现」会得出反向结论。评估时按「另一条路」读，别按「同一条路做得更好」读。

**a. speaker 调度：零轮转，唯一规则是「@ 命中 Worker 就绕过 Coordinator」。**
`server.py:2800-2824` 是完整决策：命中一个或多个 Worker 则**无条件绕过**项目经理，
逐个 `submit_dm` 直达，**即使同一条里还写了 @ 主管也以 Worker 为准**（注释写明是为
避免项目经理再次转派造成重复执行）；多条命中用 `asyncio.gather` **并发**执行，但
`submit_dm` 的 reply channel 仍传群 `project_id`，所以状态、工具、文件和回复都落在群
时间线上。未知 / 歧义 @ 不报错也不猜人——没有 Worker 命中时自然回落项目经理。

★ **`engine.py:7009-7030` `rewrap_group_mention()` 的别名剥离 + 主语重包值得抄**，
docstring 里记着真实坑（`:7011-7013`）：此前 content 原样透传，Worker 把
「@Fossil 你怎么说繁体字」理解成「**翻译 Fossil 这个词**」——@ 没剥离，它不知道
在叫自己。修法是把命中的 @别名用 `re.sub` 剥掉，再包一层明确的主语/场景。

⚠️ **全仓零发言轮转**：`round_robin|speaker_selection|rotation|轮转|轮流` 的命中
**全是日志 / 事件日志 / 缓存轮转**，没有一处是发言轮转。所以 a 这一格我们**没有任何
可抄的轮转实现**（autogen 的降级链仍是唯一来源）。

**b. @ 解析：第二家有真 @ 路由语义的上游**（`backend/mentions.py`，188 行）：

| 机制 | 位置 | 行为 |
| --- | --- | --- |
| **最长匹配** | `_mentioned_aliases():98-120` | 花名册同时有「小林」「小林子」时 `@小林子` **只投后者**（`candidates` 里按 `max(len(alias))` 收敛） |
| **歧义不猜人** | `resolve_mentions():183-185` | `targets` 收集成 set，**`len(targets)==1` 才投**；同名 / 同角色**一个都不投**，宁可回落 Coordinator |
| **只解析用户自己那段** | `_active_user_text():56-73` | 引用取 `用户说："…"` 之后、转发取 `转发了过来，并配言` 之后——**被转发原文里的 @ 绝不触发路由** |
| **邮箱护栏** | `_EMAIL_LOCAL_CHARS` `:48` / `:101` | `foo@shiloh.com` 不算提及（`@` 前一个字符落在邮箱字符集里就跳过） |
| **拒绝泛化别名** | `_GENERIC_ROLES` `:45` | 「成员 / worker / agent / 总管 / 项目经理 / 主管」当别名会被拒（除非队伍里唯一） |

⚠️ **但它不是严格整词**：尾边界检查 `:111` **只对 ASCII 尾巴**做
（`alias[-1] in _ASCII_ALIAS_TAIL`），CJK 别名不要求尾边界，所以 `@小林帮我`
**会**命中 `小林`；`:83-89` 的 docstring 把这条写成**刻意取舍**（注释原话：
「同时仍允许 `@小林帮我`（没有更长已知名字时，『帮我』就是紧贴的正文）」）。
**autogen 的双侧 `(?<=\W)…(?=\W)` 更严**——取舍要自己定，不要默认抄任一家（见上面
「对原假设的纠正」第 6 条）。

**c. 视角隔离：零共享历史的极端形态。** 既然压根没有共享历史，可借鉴的只剩**花名册
脱敏**——`engine.py:8926-8932` 的 `[v0.10a Issue 1 红线]` 明写「名单里**不再带
id**」：老格式 `名字（id=fe_1，角色：前端）` 导致 Coordinator 复述时把 `fe_1` 报给
用户。**与 MetaGPT「只渲染 `sent_from` 不渲染 `role`」同族**（都是「内部标识不进
提示词」），但粒度只到花名册一层。另两条配套：`engine.py:8968-8984`
`_sync_roster_from_store()` 的注释是「**缓存和真相不一致的时候，永远不要相信缓存**」
（每轮开口前跟磁盘对账），`engine.py:8991-8999` 对账时顺手刷新名字缓存，避免旧的
临时预定名长期盖住持久身份。

**d. 每轮 token 预算：主动删掉了，不是漏做。** `BudgetSpec` 是零字段 no-op
（`runtime.py:264-272`：`from_mapping` 里直接 `del value`，`to_dict` 恒返回 `{}`）；
`runtime.py:615` 的注释写死
`No turn ceiling, tool-error cap, correction budget, or default task clock.`；
`worker_gateway_runtime.py:326-327` 补一句 `those are the LLM's decisions`。
**这一格是上游明确拒绝的形状，不是待填补的空白**（见「对原假设的纠正」第 7 条）。

**e. 幂等 / resume：本批最强的一块**：

| # | 机制 | 位置 | 行为 |
| --- | --- | --- | --- |
| 1 | **事件级幂等 + 载荷冲突检测** | `hub.py:396-411` / `_idempotency_payload():116-121` | 在 `_seq_lock` 内判定：`_idempotency_payload` 排除 `{seq, ts, project_id, project_name}` 后比对；同 `event_id` 同载荷 → **返回存档，不盖新 seq**；同 `event_id` **不同载荷 → 抛 `ContractViolation`**；唯一例外 `event_id.startswith("coordmsg_")` 取首个存档（`:410` 注释：Coordinator 回合在用户消息落盘与确认之间崩过，重跑不得造出第二个气泡） |
| 2 | **缓存未命中回落持久事件日志** | `hub.py:111-114` | 幂等缓存首次查找时**回落到 durable event log**，所以进程重启后 outbox 重放也不产生第二条消息。⚠️ **langgraph 没有这一条** |
| 3 | **位置哈希当幂等键** | `completion.py:2088-2096` | `_stable_id("cmpkey_", task_id, attempt, run_id, status.value, run.version, run.terminal_reason)`，`_stable_id` = `sha256("\0".join(parts))[:24]`（`:763-766`）。**只哈希位置与状态、不哈希输出**——与 langgraph `_algo.py:616` 同族的独立复现 |
| 4 | **不变量交给 DB 的 partial UNIQUE INDEX** | `completion.py:918`、`:936-937`、`:986-987`、`:1049`、`:1069` | `CREATE UNIQUE INDEX idx_completion_active_lineage ON completion_events_v2(task_id, attempt_id) WHERE active=1;`；配套 `idempotency_key TEXT NOT NULL UNIQUE`、`idx_wait_active_lineage ... WHERE status IN ('open','resuming')`、`task_results_v1` 的 `UNIQUE(task_id, attempt_id, completion_id)`、`task_journal_v1` 的 `UNIQUE(task_id, attempt_id, sequence, state, runtime_state)`。**把不变量下沉到 schema，不靠应用层自觉** |
| 5 | **seq 高水位单独持久化** | `persist.py:473-487` | 原子替换 + `fsync` + 单调守卫。注释动机：「没有这本小账，进程重启后 seq 可能回退，前端会把新消息当旧消息丢掉」。**与 langgraph 的 UUIDv6 单调 `checkpoint_id` 解决同一问题**（我们 `group_runs` 的 latest 判定同毫秒并发会出错）——两条路可二选一或叠加 |
| 6 | **append-only JSONL 账本 + 显式 fsync** | `seen_speech.py:100-116` | `visible_id` 幂等（重复返回 `False`）+ 每次 `flush` + `os.fsync`。★⚠️ 且 `seen_speech.py:165-167` 注释明写**账本字段（visible_id / completion_id）不进 LLM 上下文**，投影只给 `speaker` + `text`——**与 MetaGPT 把幂等前缀塞进用户可见文本的反模式正面对立，这条要抄** |

⚠️ **resume 比 langgraph 窄**：只有 `WAITING` 能原地 resume，且 task / attempt /
worker **血缘不得变**（`worker_gateway_runtime.py:283-290` 抛
`RuntimeError("WAITING resume changed task/attempt/worker lineage")`；
`completion.py:3022`、`:3027` 抛 `CompletionConflict`；`:2902`
`archive_run_for_resume()`；`:2136` 附近注释明写 resume **复用同一 Runtime，绝不重建
第二个模型**）。**没有失败轮 / 半轮续跑。**

**f. 投票 / 多数决：零命中。** 全树（`backend/` + `src/`，排除 tests）grep
`vote|voting|ballot|majority|consensus|quorum|共识|投票|表决` = **0 命中**。最接近的
两样**都不是多数决**：`gate.py` 的人审批卡；`seen_speech.py:186` 的决策白名单归一化
（`allowed = {accept, rework, pause, complete, terminate, retry, reject}`，不在集合里
的值**直接丢弃**）。**C1-R 的投票与平票判定在这里依然没有上游可对照。**

**g. 结构化输出与收束：10 态 + 穷举策略表，收束的第二条路线**
（`backend/knowe_harness/completion.py`）：

- `CompletionStatus` 十态（`:46-56`）：`SUCCEEDED / PARTIAL / FAILED / BLOCKED /
  WAITING / CANCELLED / SYSTEM_ERROR / TIMED_OUT / ROLLED_BACK / SUPERSEDED`，
  **只有 `WAITING` 非终态**。
- 每态在 `_COMPLETION_STATUS_POLICIES` 带**六个**字段：`terminal / next_actions /
  owner / user_label / fallback_summary / projection_summary`。例：`PARTIAL` 的
  `next_actions` 是 `("accept_partial","retry","reject_delivery")`、`owner` 是
  `WORKER`；`BLOCKED` 的 owner 是 `COORDINATOR`、动作是
  `("provide_dependency","retry","cancel")`。
- ★★ `:138-141` **模块加载时**断言策略表对枚举**穷尽**，多一个少一个直接
  `RuntimeError`（报 `missing=… extra=…`）。
- 配 `:2497-2499` `worker_idle()` 的注释：「WAITING 仍是开放的任务结果供后续
  resume，但它**不占用 active 席位**」。

**这是「收束」的第二条路线**：不靠算术收敛（无 consensusScore、无 convergenceDelta）、
不靠 LLM 自评，而是把结果归进**有限状态机 + 每态合法后继动作表**，再用**加载期断言**
保证表与枚举永不失配。**共识维度被抽掉后能跑多远，这是本批唯一的实证。**

**它没有的**：@ 之外的整词边界、每轮 token 预算、投票 / 多数决、按轮次续跑、
多角色共享可见历史。

### lyricon — tomakino/Lyricon（fork: kifranei/lyricon）@ f3854d346779dad07be0f2ecd20bea6fd096c02f —— 与 C1 群聊无直接关系，不进对照表

**这一节单独立，是因为排除理由是「范畴外」而不是「许可档」。** 硬约束是不能把它塞进
七项需求的横向总表：七项需求没有一项它有实现，硬填只会多出一整行「0」，反而污染
「哪些格子是上游明确拒绝、哪些是上游压根不涉及」这个区分（参见「对原假设的纠正」
第 7 条对「0 个」含义的辨析）。

**是什么**：Android **Xposed 模块「词幕」（Lyricon）**，做**系统状态栏歌词显示**。
不是群聊、不是多 agent、不是 LLM 编排、不是 Android 群聊 UI 参考。
⚠️ 它是 [tomakino/Lyricon](https://github.com/tomakino/Lyricon) 的**个人增强
Fork**（`README.md:31`），应用包名 `io.github.kifranei.lyricon.fork`
（`README.md:36`；`build.gradle.kts:13` `extra["appPackageName"]` 同值），
但**代码里的 namespace 仍是上游的 `io.github.proify.lyricon.*`**
（`lyricon/build.gradle.kts:9`）——所以 grep 包名会同时命中两套。

**许可结论**：仅借鉴机制，代码独立重写（Apache-2.0）——但**本节没有任何机制值得借鉴**，
这一档只是如实登记它的协议位置，不代表进 C1 移植候选。

⚠️ **协议三方确认**：①`LICENSE:1-3` 首行 "Apache License / Version 2.0, January
2004 / http://www.apache.org/licenses/"，全文 **201 行**；②源文件头，例如
`xposed/src/main/kotlin/io/github/proify/lyricon/xposed/systemui/util/ViewVisibilityController.kt:1-4`
的 `Copyright 2026 Proify, Tomakino / Licensed under the Apache License, Version 2.0`；
③网页版 sidebar 标签。⚠️ 注意**不是** `build.gradle.kts`——那几份构建脚本没有版权头。

⚠️⚠️ **协议文档有缺口，这条比协议本身重要**：README 的**中英文版对 license 零提及**
（`grep -i 'licen|许可|协议|Apache' README.md` = **0 命中**）；仓库**无 `NOTICE`**；
根 `LICENSE` 的附录**仍是未填的样板** `Copyright [yyyy] [name of copyright owner]`。
→ **署名归属是上游原作者 Proify / Tomakino，不是 fork 者 kifranei**，而仓库里
**没有一份可执行的统一署名依据**。我们即使将来要看它的代码，也必须逐文件从源文件头
认署名，不能靠 README。

**决定性反证**（这是排除它的依据，不是印象）：全仓 `.kt/.md/.xml/.txt` grep
`group.?chat|multi.?role` = **0 命中**；grep `agent|Agent` 只命中三处且**没有一处是
多 agent 语义**——`UpdateRelease.kt:59` 与 `UpdateDownloadManager.kt:147` 的 HTTP
`User-Agent` 头（值是 `Lyricon/${BuildConfig.VERSION_NAME}`），以及
`lyric/view/.../LandingSoftAnimator.kt:14` 的 `animatorAgent.playTogether`（一个
变量名）。

唯一有零星参考价值的一处：`app/src/main/kotlin/io/github/proify/lyricon/app/ai/explain/AiExplainCache.kt:30-42`
把**内存 LRU**（`Collections.synchronizedMap` 包 access-order 的 `LinkedHashMap`，
`removeEldestEntry` 里 `size > MAX_MEMORY`）+ **SharedPreferences 持久化** +
**TTL 30 天**（`TTL_MS = 30L * 24 * 60 * 60 * 1000`）三层叠在一起。⚠️ **但那是
单条内容缓存，与群聊七项需求无关**，抄它不构成「C1 借鉴了 lyricon」。

**SHA 核实过程**：⚠️ **默认分支是 `master` 不是 `main`**。取法是
`github.com/kifranei/lyricon/commits/master.atom` 首条 entry + 网页版 `/commits/master`
+ 本地 `git rev-parse HEAD`，三方一致，`f3854d34…`，共 **245 个 commit**。
⚠️ **日期坑**：commit 的 author date 是 `2026-09-17`，atom 的 `<updated>` 是
`2026-10-04`（**push 时间，不是提交时间**）——两个日期别混。

### 七项需求的横向总表

七项需求 × 十二个项目的横向对照（lyricon 已核验并排除，不在本表，见其专节）。
**「可直接移植」= 许可兼容且有可照抄的实现；
「只能借鉴或反面教材」= 许可不允许拷贝、或机制有缺陷、或压根没有。**

| 需求 | 可直接移植（项目 + 文件）| 只能借鉴或反面教材（项目 + 文件）| 结论 |
| --- | --- | --- | --- |
| **a. speaker 选择与轮转** | autogen `select_speaker`(`_selector_group_chat.py:152`) 三级降级 + `max_selector_attempts` 反馈重试环(L247-300) + 耗尽后兜底(L302-308)；MetaGPT 声明式轮转表 `STEP_INSTRUCTIONS`(`werewolf/const.py:36-121`) + `WerewolfEnv.run`(L36-41) 串行驱动；SillyTavern `LIST`(L1180) 与 `NATURAL`(L1242) 三段式；RisuAI `orderByOrder=true` 确定性模式；★ Knowe `rewrap_group_mention()`(`engine.py:7009-7030`) **别名剥离 + 主语重包**（docstring 记着真坑：不剥离时 Worker 把「@Fossil 你怎么说繁体字」理解成「翻译 Fossil 这个词」） | RisuAI `group.ts` 第③段 `while` **空集合死循环**（反面）；SillyTavern `utils.js:1356` 伪 @；LianYu-app `calculateSpeakingPriority():478` 启发式权重表（Apache-2.0，且末项含 `+Random(0,10)` 破坏可重放）；⚠️ **Knowe 全仓零发言轮转**——`round_robin\|rotation\|轮转` 命中全是日志轮转，唯一规则是「@ 命中 Worker 就绕过 Coordinator」(`server.py:2800-2824`)，**只能借鉴那条路由规则，没有轮转可抄** | 轮转表与降级链都有现成实现可抄（**仍以 autogen 为唯一来源**）；**LLM 选人只保留 autogen 的降级语义**，随机权重表不要 |
| **b. @ 整词解析** | ★★ autogen `_mentioned_agents`(`_selector_group_chat.py:310-341`)：非捕获式词边界 `(?<=\W)` / `(?=\W)` + 消息两侧补空格 + 返回命中次数表，**MIT 可直译**；★ Knowe `backend/mentions.py`（188 行，MIT）**第二家有真 @ 路由语义的上游**：`_mentioned_aliases():98-120` **最长匹配**（「小林」/「小林子」并存时只投后者）+ `resolve_mentions():183-185` **歧义不猜人**（`len(targets)==1` 才投，同名一个都不投）+ `_active_user_text():56-73` **只解析用户自己那段**（被转发原文里的 @ 绝不触发路由）+ `_EMAIL_LOCAL_CHARS` `:48,:101` 邮箱护栏 + `_GENERIC_ROLES` `:45` 拒绝「成员/worker/agent/总管」当别名 | SillyTavern `utils.js:1356` `\b\w+\b` 切词后**任意词包含匹配**（"Alice Johnson" 输 "johnson" 也唤醒、输 "john" 反不命中）；RisuAI `group.ts` 只看最后一条消息 + ③ 段死循环；agnai `InputBar.tsx:405` 只在 UI 补全、**服务端零解析**；Operit 无 @（planner 只看用户原文）；⚠️ **Knowe 不是严格整词**：尾边界 `:111` **只对 ASCII 尾巴**做，CJK 别名不要求尾边界，所以 `@小林帮我` **会**命中 `小林`（`:83-89` docstring 写成刻意取舍） | ⚠️ **原结论要修正**：不是「只有 autogen 一家是真 @」，而是「**严格整词只有 autogen；Knowe 是第二家有真 @ 路由语义的上游，但刻意对 CJK 放宽**」。autogen 双侧边界更严、Knowe 更贴中文直觉，**取舍要自己定，不要默认抄任一家**。我们已有 `GroupChat.parseMentions`(`core/data/model/GroupChat.kt:454-457`)，照 autogen 的正则重写并把计数用起来，再考虑是否吸收 Knowe 的最长匹配与歧义不猜人 |
| **c. 视角隔离** | agnai `isMessageInvisible`(`common/prompt.ts:786`) 四级优先级可见性 + `getLinesForPrompt()`(L717)「每个 bot 取同一列表的不同子集」+ `getMessageAuthor()`(`common/util.ts:343`) 他人消息降级 `role:'user'`；MetaGPT `restricted_to`(`ext/werewolf/schema.py:28`) **读时 ACL** + `base_player.py:157-163` 只渲染 `sent_from`；crewAI `flow/conversational_mixin.py` 公开频道 + `agent_threads` 私有草稿；langgraph `Send(node, arg)`(`types.py:738`) 每 agent 私有 state；★ Knowe **花名册脱敏**（`engine.py:8926-8932` `[v0.10a Issue 1 红线]` 明写名单里**不再带 id**，老格式 `名字（id=fe_1，角色：前端）` 让 Coordinator 复述时把 `fe_1` 报给用户）+ `_sync_roster_from_store()`(`engine.py:8968-8984`) 每轮对账、「缓存和真相不一致的时候，永远不要相信缓存」 | Operit 消息角色翻转（LGPL-3.0，需自行重写）；**RisuAI `makeMs()` 与 SillyTavern 单一 `chat` 数组 = 零隔离，纯反面教材**；LianYu-app `[其他群友]` 前缀降级；⚠️ **Knowe 是「零共享历史」的极端形态**——`runtime.py:925-974` Worker 消息每个 attempt 从零构造、只有 2 条，**零历史 ⇒ 零泄漏**，它压根没有这个问题（见「对原假设的纠正」第 8 条） | 读时 ACL + 组装层过滤是唯一正解；agnai 的四级可见性优先级可直接覆盖我们 C1-M 的粗粒度闸门。Knowe 只贡献**花名册脱敏**一条（与 MetaGPT `sent_from` 同族），它的零隔离路线**不是可抄的隔离实现** |
| **d. 每轮 token 预算** | **0 个**。只有两个「半个」：crewAI `UsageMetrics.delta_since()`(`types/usage_metrics.py:32`) 增量测量原语（MIT，**但从不用于中止**）；agnai `getContextLimit()` = `maxContextLength − maxTokens`（**先给生成留额度**，但仍是 per-request） | RisuAI 两段贪心裁剪（`db.maxResponse` 扣减 + drop-oldest，仍是 per-request）；MetaGPT `utils/cost_manager.py` 只统计不截断；LianYu-app `TokenUsage` entity 纯报表无约束；⚠️ **Knowe `BudgetSpec` 是零字段 no-op**——`runtime.py:264-272`（`from_mapping` 直接 `del value`、`to_dict` 恒返回 `{}`）+ `runtime.py:615` `No turn ceiling, tool-error cap, correction budget, or default task clock.` + `worker_gateway_runtime.py:326-327` `those are the LLM's decisions` | ⚠️ **必须自研**。借 crewAI 的 `delta_since()` 做计量、借 agnai 的「先留生成额度」顺序，闸门逻辑自己写。⚠️ 注意「0 个」的含义：**Knowe 是主动删掉并写了理由，不是待填补的空白**——所以**没有任何上游能当 per-round 闸门的依据** |
| **e. 幂等 / resume** | ★★ langgraph 5 条契约（`_algo.py:616` task_id **只哈希位置** / `_task_status.py:29-30` **完成 ⟺ 至少一条非控制写** / `_loop.py:767-773` tick 时写回填 / `main.py:2956` 只跑 `not t.writes` / `_algo.py:256` 折叠前排序）+ **UUIDv6 单调 `checkpoint_id`**；crewAI `Crew.replay(task_id)`(`crew.py:2101`) 逐 task 回填；autogen `save_state/load_state`(L116-131) 存档 JSON 形状；★★ Knowe **本批最强**：`hub.py:396-411` **事件级幂等 + 载荷冲突检测**（同 `event_id` 同载荷→返回存档不盖新 seq；**不同载荷→抛 `ContractViolation`**）+ `hub.py:111-114` **缓存未命中回落持久事件日志**（进程重启后 outbox 重放不产生第二条消息，**langgraph 没有这条**）+ `completion.py:2088-2096` **位置哈希当幂等键**（`_stable_id("cmpkey_", task_id, attempt, run_id, status, version, terminal_reason)`，**只哈希位置与状态、不哈希输出**，与 langgraph `_algo.py:616` 同族的独立复现）+ `completion.py:918,:936-937,:986-987,:1049,:1069` **不变量交给 DB 的 partial UNIQUE INDEX** + `persist.py:473-487` **seq 高水位单独持久化**（原子替换 + `fsync`，与 langgraph UUIDv6 解决同一问题，两条路可二选一或叠加）+ ★ `seen_speech.py:100-116` append-only JSONL 账本（`visible_id` 幂等 + 每次 `os.fsync`）且 `:165-167` **账本字段不进 LLM 上下文** | **MetaGPT 把 `f"{self.round_cnt} \| "` 塞进用户可见文本**（`werewolf_env.py:33`，`role.py:215,229` 两行注释掉的 `HACK` 承认破坏 replay）；agnai 把 `AbortError` 当成功提交半条（与我们已定语义相反）；⚠️ **Knowe resume 比 langgraph 窄**：只有 `WAITING` 能原地 resume 且 task/attempt/worker **血缘不得变**（`worker_gateway_runtime.py:283-290` 抛 `RuntimeError`），**没有失败轮 / 半轮续跑** | **收益最大的一块**。位置哈希 + 空 writes 判定两个原语就够；幂等键必须存在消息之外的字段上——**Knowe 的 JSONL 账本投影（只给 `speaker` + `text`）是这条规则的正面对照**。Knowe 补上 langgraph 没有的「载荷冲突要报错」与「缓存回落到持久日志」两格 |
| **f. 投票 / 多数决** | **0 个** | MetaGPT `evaluate_action.py:58` 3 次投票 2-of-3 早退（三个全不同则返回 `None`，**无 tie-break**）；MetaGPT `werewolf_ext_env.py:244` `Counter.most_common` **平票未解决**（L255 挂着 `# TODO in case of tie vote`）；langgraph `NamedBarrierValue` 只提供「等齐再汇总」的同步原语，**不含计票**；`decision-protocols` 只有 5 个 experiment JSON、**无源码**；⚠️ **Knowe 也是零命中**——全树 grep `vote\|voting\|ballot\|majority\|consensus\|quorum\|共识\|投票\|表决` = 0；最接近的两样**都不是多数决**：`gate.py` 的人审批卡、`seen_speech.py:186` 的决策白名单归一化（不在 `{accept,rework,pause,complete,terminate,retry,reject}` 里的值直接丢弃） | ⚠️ **必须自研**。autogen 的 consensus group chat 是**虚构的**，不能引 |
| **g. 结构化输出与收束** | consensus-core `stats.ts` `consensusScore = round(clamp(avg − 0.5·stddev, 0, 100))` + `detectDisagreements`(阈值 20) + `engine.ts:189-200` `convergenceDelta=3` 早停 + `mulberry32(seed)` 确定性重放 + `engine.ts:109` 盲首轮开关；agnai `getJsonSchemaPayload()` 一字段出 4 方言 + `parsePartialJson()` 增量修复 + `ensureSafeSchema()` 剥 `response` 字段；crewAI `Task.output_pydantic` + guardrail 校验-重试；★★ Knowe **收束的第二条路线**（`backend/knowe_harness/completion.py`）：`CompletionStatus` **十态**（`:46-56`，**只有 `WAITING` 非终态**）+ `_COMPLETION_STATUS_POLICIES` **每态六字段**（`terminal/next_actions/owner/user_label/fallback_summary/projection_summary`）+ ★★ `:138-141` **模块加载时断言策略表对枚举穷尽**，多一个少一个直接 `RuntimeError` | crewAI 收束**只取最后一个非空 output**(L1932)，没有议长汇总；camel Apache-2.0 不可逐行拷贝 | 收敛判定与可重放有现成算术可抄（**节 3 优先**）；**议长汇总没有上游**，要自研。Knowe 给出第三种可选形态：**不靠算术收敛（无 consensusScore / 无 convergenceDelta）、不靠 LLM 自评**，而是把结果归进**有限状态机 + 每态合法后继动作表**，用加载期断言保证表与枚举永不失配 |

⚠️⚠️ **硬结论 1：「每轮 token 预算」和「投票 / 多数决」这两个需求，调研的 12 个
项目里 0 个有可用实现。这两块必须自研。** ⚠️ 但要分清「0 个」的两种含义：**
上游压根不涉及**（vote：全 12 家里 autogen 的 consensus 是虚构的、langgraph 只有
同步原语不含计票、Knowe 全树零命中、consensus-core 有算术但不叫 vote）**，
vs 上游**明确拒绝**（d：Knowe 的 `BudgetSpec` 是零字段 no-op 且写明
`those are the LLM's decisions`）。**后一种更值得警惕——它说明这个形状有人试过并
否决了，不能拿「没人做过」当不存在风险的依据。**

- **autogen 的 consensus group chat 是虚构的。** `ConsensusGroupChat` /
  `class Conductor` 在 `v0.2.40` tag 与 `main` 全树 `git grep` 均 **0 命中**，
  只活在 0.2 之前的**未发布 dev 分支**，**从未进任何 tag**。立项时把它当
  「多数决参考实现」是错的，不要再引。
- 最近的两个替代物都只到一半，**都不构成 per-round 闸门**：

| 替代物 | 位置 | 拿到什么 | 差在哪 |
| --- | --- | --- | --- |
| crewAI `UsageMetrics.delta_since(baseline)` | `types/usage_metrics.py:32` | MIT 的**增量测量原语**——正是「记基线、量增量」缺的那块 | **从不用于中止**；`max_tokens` 只是单次请求输出上限，全仓无累计预算闸门 |
| agnai `getContextLimit()` | `common/util.ts` | `maxContextLength − maxTokens`，**先给生成留额度再算输入上限**（顺序值得抄，我们容易写反） | **仍是 per-request 不是 per-round**；`fillPromptWithLines()` 首次溢出即 `break`，但那是单次请求的裁剪 |

⚠️⚠️ **硬结论 2：Apache-2.0 的两个上游（LianYu-app、camel）是唯一需要「借鉴机制
+ 独立重写」的。** AGPL-3.0（SillyTavern / agnai）/ GPL-3.0（RisuAI）/ LGPL-3.0（Operit）/ MIT（autogen `LICENSE-CODE` / crewAI / langgraph / MetaGPT / consensus-core / Knowe）那**十家都可以直接并入并保留声明**。理由见上面「AGPL-3.0 主协议下的兼容矩阵」：Apache-2.0 §3 的专利终止条款与 GPLv3 §3 冲突，FSF 判定与 GPL/AGPL 家族不兼容。（另有 lyricon 也是 Apache-2.0，但已按范畴外排除，不占这两档的任何一档。）

**反面教材（调研里最该记住的几条）**：

- ⚠️ **RisuAI / SillyTavern 零视角隔离**——所有成员看完整共享记录。RisuAI
  `makeMs()` 只过滤 `msg.disabled`，他人消息只是套一层 `<{{char}}'s Message>`
  模板；SillyTavern 是单一 `chat` 数组，APPEND 模式下把所有成员角色卡拼进同一
  prompt。**渲染层隐藏 ≠ 提示词层隔离**，角色名和正文照样进了每一个 bot 的 prompt。
- ⚠️ **幂等键绝不能塞进用户可见文本**——MetaGPT 的幂等是给消息加
  `f"{self.round_cnt} | "` 前缀，`ext/werewolf/roles/role.py:215,229` 留着两行
  被注释掉的 `HACK`，承认「同一条消息会被自动去重」破坏了 replay。我们的幂等键
  必须存在消息之外的字段上。
- ⚠️ **agnai `web/store/message.ts` 的 `processQueue()` 是真 bug**——只在
  `onSuccess` 里排空队列，失败项**永久卡死整条队列**。别复现；我们的调度队列
  必须 `finally` 排空。
- ⚠️ **MetaGPT 投票平票未解决**——`werewolf_ext_env.py:255` 挂着
  `# TODO in case of tie vote`，`evaluate_action.py:58` 也没有 tie-break。
- ⚠️ **`while` 保底在集合为空时死循环**——RisuAI `group.ts` 第③段、
  agnai 的候选兜底都有这个问题。SillyTavern 的「最多重试 `randomPool.length`
  次」才是正确形状，按后者实现。

**第 5 批（Knowe）补 7 条技术性坑，一条都不许丢：**

- ⚠️⚠️ **prompt 管不住副作用，要用代码硬门禁。** `engine.py:2403-2415`
  `dispatch_frozen()` 的 docstring 记着真实事故：`REJECTION_FOLLOWUP` 里**明明写着**
  「不要重新提案」，线上照样弹了第二张一模一样的审批卡。修法是加**结构化的拒绝态硬
  门禁**（`return self._rejection_pending`）。原话：**「能用代码保证的事，不要用祈使句
  去求。」** → 直接适用于我们 C1-R 的轮次判定：**凡是「不该发生的事」都要有代码闸门，
  不能只写进 prompt。**
- ⚠️ **fire-and-forget 必须记账，且在终态消息前显式 drain。** `engine.py:7858-7874`：
  `_fire()` 用 `create_task` 发 `stream_delta`，不收进 `_fired` 集合的话增量会**排到
  完整 message 之后**——前端先看到完整消息、再看到一串增量，气泡会闪一下甚至重复；
  所以发 message 之前要 `await self._drain()`。**这正是 agnai `processQueue()` 那个坑
  的反面教材**：agnai 是队列卡死、Knowe 是队列乱序，**两者都要显式收口**。
- ⚠️⚠️ **链式调度里 `await` Worker 回合 = 整个项目被冻住。** `engine.py:4659-4697`
  记着完整推理：`_loop` 里 `await self._harness_turn(...)` 会把每个 Worker 回合都
  await 完，Worker 抓网页跑三分钟期间 **inbox 无人取**——用户打的字进了队列没人拿，
  「我说话，没人理」。⚠️ 而且源码明说**在派单处加拦截一行也解决不了**，因为拦截管的是
  「派给忙人」而冻结在**队列**上。修法三条：① Worker 回合改后台 task，**登记
  （`_worker_turns`，`engine.py:1537`）必须先于 `create_task`**（`:4717-4751`，方法
  docstring 就叫 "registration precedes create_task"）；② 忙态计数 `_turns_active`
  **必须跟着搬**（`:4740`），否则引擎会在 Worker 正干活时报告「我闲着」；
  ③ `_harness_turn` 在 `finally` 里 drain（`:4600-4608`），起 task 失败时
  **任务保留待重试**。**本批最值得记的调度坑。**
- ⚠️ **`_dm_pending` 是纯内存队列**（`engine.py:1653` 定义、`:3863` 追加）：用户 @ 一个
  正在忙的 Worker，消息进暂存队列，**进程死亡即丢**。与 Operit 的
  `pendingAutoContinuationByChatId` 同级。真正的幂等在 completion / hub 那一层——
  **别把「有队列」当成「有幂等」**。
- ⚠️ **审计不是判决。** `runtime.py:994`
  `failures: list[ToolResult] = []  # recorded for telemetry only, never a verdict`；
  `tool_ledger.py:217` 的 `"""Audit-only aggregate; it deliberately makes no
  completion judgement."""`。**别拿工具执行证据反推任务完成。**
- ⚠️ **增量回放遇淘汰必须报缺口，不能给残缺历史。** `ring.py:1-12` 的模块 docstring
  记录着旧版 bug：`since_seq == 0` 走特殊分支，**淘汰之后仍把残缺历史当完整历史返回**，
  前端以为拿到全量、实际开头缺一大截。新规则**只有一条、不分支**：
  `oldest > since_seq + 1` ⇒ 中间有洞 ⇒ 返回 `( [], True )`（`ring.py:78-79`）让前端
  走快照重建。**对我们群聊导出 / 增量同步同样适用。**
- ⚠️ **等待令牌与 active 收束的关闭必须与终态同事务。** `completion.py:1575-1605`
  的注释写明理由：否则「终态 commit 与异步清理之间崩溃」会留下**孤儿 open wait
  token**。配套 `completion.py:2497-2499` 的 `worker_idle()` 注释还要保证
  `WAITING` **不占用 active 席位**。

### 移植优先级

按「收益 ÷ 风险」排序。每条写明落到我们仓库哪个文件、改什么、大概工作量。

**第 1 优先 —— langgraph 的幂等内核**（MIT，估 2-3 天）

取上面那 5 条规则，落到 `GroupRunEntity` / `GroupRunDAO`（已落库的 `group_runs`
表）：

| 规则 | 我们的落法 |
| --- | --- |
| 身份即位置 | `task_id = hash(conversationId, roundId, roleId, seq)` —— **不含输出**，所以同一角色在同一 seq 永远得到同一个 id |
| 完成判据 | 一条 run **完成 ⟺ 至少有一条非控制写**；无输出而完成时补 `NO_WRITES` 标记，保证规则恒成立 |
| 写回填 | 每步持久化 writes；tick 时把**已完成** task 的 writes 回填进新准备的 task，未完成（失败/中断）的保持空 writes |
| 重跑过滤 | 执行器 `if (!writes) 才跑` —— 有回填 writes 的永不重跑 |
| 折叠顺序 | 组装可见集时先按 `task_path_str` 语义排序再归组，保证 reducer 折叠顺序可复现 |

**收益最大：幂等 / 续跑 / 取消三个需求一次性落地。** 存档 JSON 的形状参考 autogen
v0.2 的 `save_state` / `load_state`(`groupchat.py:116-131`)：把
`message_thread` + `current_turn` + `previous_speaker` 三字段序列化进
`SelectorManagerState`，`reset()` 一键清空。⚠️ 别漏 langgraph 的两个细节：
**UUIDv6 单调 checkpoint id**（取最新一条不用比时间戳，我们 `group_runs` 的
latest 判定同毫秒并发会出错）；**resume 必须显式声明 checkpointer 前置条件**
（`pregel/_loop.py:930` 缺 checkpointer 直接抛 `RuntimeError`，别等运行时报错）。

**第 2 优先 —— autogen 的 @ 解析 + speaker 降级链**（MIT）

- `_mentioned_agents`（`_selector_group_chat.py:310-341`）的正则直译成 Kotlin，
  **约 1 小时**。
- `select_speaker`(L152) 三级降级 + `max_selector_attempts` 反馈重试环，**1-2 天**。

落到 `core/data/model/GroupChat.kt`：加 `parseMentions(text): Map<roleId, Int>`
（现有的 `parseMentions`(`:454-457`) 返回 `List<String>`，要改成带计数的 map，
次数天然可用于「多次 @ 权重更高」）与 `selectSpeaker(...)`。
**这是唯一能真正复用的「整词 @」实现**——SillyTavern 的 `utils.js:1356` 和
RisuAI 的 `group.ts` 都是伪 @，不能拿来当需求来源。⚠️ Knowe 也是真 @ 路由
（见横向总表 b 行），但**刻意对 CJK 放宽尾边界**（`@小林帮我` 会命中 `小林`），不是严格
整词——**按上面第 6 条的取舍自己定，不要两边混抄**。

**Knowe 落在哪一档（MIT，5 个字：e 项最值得抄，其余各抄一格）**

| 项 | Knowe 给什么 | 我们怎么用 |
| --- | --- | --- |
| **e. 幂等** | ★★ **本批最强**：事件级幂等 + **载荷冲突抛 `ContractViolation`**（langgraph 没有）、缓存未命中**回落持久事件日志**（langgraph 没有）、位置哈希 `_stable_id("cmpkey_", …)` 只哈希位置不哈希输出、不变量下沉到 **partial UNIQUE INDEX**、**seq 高水位单独持久化**、`seen_speech.py` JSONL 账本且**账本字段不进 LLM 上下文** | **与第 1 优先并列，甚至更值得抄**——前三条正好补上 langgraph 5 条契约没覆盖的洞。落到 `GroupRunEntity` / `GroupRunDAO`：加「同幂等键不同载荷要报错」这一条约束（现在只是不重复插入，静默吞掉冲突会掩盖 bug） |
| **a. speaker 调度** | 只有一条规则：「@ 命中 Worker 就**绕过** Coordinator」（`server.py:2800-2824`）+ `rewrap_group_mention()` 的**别名剥离 + 主语重包** | **只借鉴路由规则本身**（@ 命中就别绕一圈让 Coordinator 再转派，会重复执行）。⚠️ **它零轮转，没有轮转可抄**——a 的轮转实现仍以 autogen 为唯一来源 |
| **c. 视角隔离** | 零共享历史（**不是可抄的隔离实现**）；可借鉴的只有**花名册脱敏**（名单不带 id）+ 每轮对账不信缓存 | **只抄花名册脱敏一条**，与 MetaGPT `sent_from` 同族，落到我们的成员清单渲染里。⚠️ **不要试图用它的「零共享历史」替代 C1-M 空间闸门**——我们共享历史是既定规格（见「对原假设的纠正」第 8 条） |
| **g. 收束** | 10 态 + 每态合法后继动作表 + **加载期穷举断言**（`completion.py:138-141`） | **备选形态，不是首选**。我们已有 consensus-core 的算术收敛路线；Knowe 证明「把共识维度抽掉也能跑」，若将来 roundtable 实测不稳，这是现成的退路 |
| **d / f** | d **主动删掉**（零字段 no-op）；f **零命中** | **都不做**。d 的闸门自己写（没有任何上游可引），f 照 MALLM 协议清单自研 |

⚠️ **Knowe 的 resume 比 langgraph 窄**（只有 `WAITING` 能原地 resume 且血缘不得变，
**没有失败轮 / 半轮续跑**），所以**别拿它当「续跑」需求的参考**——那一条仍以 langgraph
为唯一来源。

**第 3 优先 —— MetaGPT 隔离 + consensus-core 收敛 / 确定性**（均 MIT，合计 1-2 天）

- **MetaGPT 的 `restricted_to` 读时 ACL** + **只渲染 `sent_from` 不渲染 `role`**
  （`base_player.py:157-163`）——与我们的「**过滤必须发生在组装层**」完全吻合，
  且支持事后追溯改判。落到 `GroupPerspectiveTransformer` / `GroupChat.visibleMessages`。
- **consensus-core 的收敛与确定性**：`consensusScore = avg − 0.5σ` +
  `detectDisagreements(阈值 20)` + `convergenceDelta=3` 早停 + `mulberry32`
  种子保证重放一致 + 首轮盲读隔离。

几百行 Kotlin，补齐 roundtable 的「议长汇总 + 收敛判定」和「可重放」。注意
consensus-core `parser.ts` 的两个细节值得一起抄：置信度用**线性字符串解析不用
正则**（防 ReDoS），缺 `CONFIDENCE: N` 标记时**默认 50**（缺标记是模型不合规，
不该惩罚）。

**第 4 优先 —— Operit 的角色翻转 + LLM planner**（LGPL-3.0，可移植但需自行重写，估 2-3 天）

- **角色翻转**落到 `GroupChat.visibleMessages` / `GroupPerspectiveTransformer`：
  自己 → `ASSISTANT` 原样；其他群友 → `USER` + `[From role: $roleLabel]\n$content`。
  比 LianYu-app 的 `[其他群友]` 前缀方案干净一个量级（前缀方案要求模型自己解析
  括号）。⚠️ LGPL-3.0 虽可并入，但**仍要自行重写并注明出处**，不要逐行拷贝。
- **planner 的 `parsePlannedRounds():1085` 四级 ID 回退容错**（剥 ```json 围栏 →
  `rounds` → 旧格式 `order`/`plan`/`members`，成员标识按 `id` → `memberId` →
  `roleId` → `name` 依次尝试，支持用角色名反查 ID）落到我们的 **@ 未命中时的
  按 mode 路由**。planner 模型本身暂不移植——我们已有 C1-R 的
  `GroupTurnCoordinator` 轮次内核，不需要第二个模型编排。

**明确不做**

⚠️ **不要为 `vote` 模式移植任何项目——没有可移植的。** 照 MALLM 的协议清单
自己实现：**多数 / 超多数 / 一致** 三档，平票降级到议长（这一条正好补上 MetaGPT
和 autogen 都缺的 tie-break）。token 计量配 crewAI 的 `UsageMetrics.delta_since()`
（MIT 增量原语）——只借这一个函数，**中止逻辑自己写**。

### 本机实测基线

测得环境：HEAD `58b129c7`，openjdk **21.0.12 Temurin**，全部命令带 `--offline`。
三项均退出码 0。

| 命令 | 退出码 | 耗时 | 结果 |
| --- | --- | --- | --- |
| `./gradlew --offline assembleDebug` | 0 | **1m27s** | 产出 **3 个 APK**：universal **102.4 MiB** / x86_64 **87.1 MiB** / arm64-v8a **86.4 MiB** |
| `./gradlew --offline test` | 0 | — | 全仓 **1202 用例 / 0 failures / 0 errors / 12 skipped** |
| `./gradlew --offline lint` | 0 | — | **0 error** / 623 warning / 7 hint |

**12 个 skipped 的构成**：`KtorLiveTest` ×5（需联网）、`RustEngineTest` ×5、
`BuiltinCardSampleTest` ×1、`CardExecutorTest` ×1。
⚠️ 裸 `test` **只跑 debug 变体**，**release 单测 0 覆盖**——别把这条当成
release 也干净的证据。

`app/lint.xml` 唯一一行配置是把 `MissingTranslation` 降级成 **warning**（多语言
文案由 Crowdin 维护，不在仓库内翻译）。除此之外 623 warning / 7 hint 全是
上游依赖与写法风格，没有我们自己的 lint 错误。

**C1 群聊内核 15 个文件 lint 零命中**（0 error 0 warning 0 hint）：
`GroupChat.kt`、`GroupTurnCoordinator.kt`、`GroupChatPage.kt`、
`GroupPerspectiveTransformer.kt`、`UngeneratedMessageFilter.kt`、`GroupRunDAO.kt`、
`GroupRunEntity.kt`、`Migration_30_31.kt`、`MemoryExtractor.kt`、
`MemoryRepository.kt`、`MemoryGraphDAO.kt`、`MemoryChunkEntity.kt`、
`MemorySpaceEntity.kt`、`QrScannerSheet.kt`、`ChatManager.kt`。

⚠️⚠️ **以上只是基线，不代表 C1 验收通过。** 按本仓 `docs/eval/c1-group-chat.md`
的判定规则，**JVM 纯函数测试不能替代真机 UI 证据、备份恢复证据、Tavern 本体互操作
证据**；C1-01…C1-10 十例目前仍全部 `unverified`。构建绿、单测绿、lint 绿只说明
「没把别的东西改坏」，不构成 C1 的验收证据。
