# beyond-operit 开源参考

核对日期：2026-10-03。此表记录用户指定的参考方向与实际核验结果。
README 的功能描述不作为本项目验收证据；复制或改编源码前须记录固定提交、
具体文件及许可证，保留必要声明。当前尚未复制这些项目的源码。

| 包 | 第一方入口与核验状态 | 参考目标 / Android 边界 |
| --- | --- | --- |
| A1 | [vercel-labs/agent-browser](https://github.com/vercel-labs/agent-browser)，归属已确认，仓库标注 Apache-2.0 | 已阅读 README 的引用选择器、快照差异、显式 tab、状态等待约定。结合 WebView 重写；桌面 Chrome/CDP 的输入、cookie、渲染语义不能直接当作 Android 已实现。 |
| A2 | [elaranjo/clawdiney](https://github.com/elaranjo/clawdiney)（MIT，2026-10-03 抓取成功）+ [mem0ai/mem0](https://github.com/mem0ai/mem0)（Apache-2.0）+ [sqliteai/sqlite-vector](https://github.com/sqliteai/sqlite-vector)（Apache-2.0） | clawdiney 的 `storage.py`/`query_engine.py` 提供单 SQLite 混合检索架构（sqlite-vec + FTS5 + 图谱表 + RRF k=60）；mem0 提供 ADD-only 实体链接抽取与多信号融合思路；sqlite-vector 是项目已有依赖 `ai.sqlite:vector`（BLOB 向量 + `vector_init`/`vector_full_scan`）。本地为 Kotlin/Room 重写，未复制源码。 |
| A4 | [screen2prompt](https://pypi.org/project/screen2prompt/)，搜索找到 0.0.2，页面抓取失败（JS challenge），源码及许可证仍待核实，**不作为已采用参考** | 元素标注、动作与观测闭环；Android 仍用本机无障碍与截图。本地 A4 为自研双通道定位（视觉 bbox → 无障碍节点），未复制任何上游源码。 |
| A5 | [m-doughty/LLM-Character](https://github.com/m-doughty/LLM-Character)，仓库标注 Artistic-2.0 | CCv3 JSON/PNG、lorebook 匹配与回归样本；Raku 实现需转成 Kotlin 并做差异测试。 |
| B1 | [xiaoqiang-cheng/QGraph](https://github.com/xiaoqiang-cheng/QGraph)，由 PyPI Repository 跳转确认，README/PyPI 标注 MIT | DAG 调度、运行历史、失败恢复。Compose 编辑器与现有 FlowSpec 仍需本地整合。 |
| B2 | [Operit ToolPkg 格式](https://github.com/AAswordman/Operit/blob/main/docs/TOOLPKG_FORMAT_GUIDE.md)、[示例](https://github.com/AAswordman/Operit/tree/main/examples)；源码许可证待逐包核实 | 固定 API 版本、依赖、hooks、Provider 与设置 UI；topic 只用于发现具体包，不能当成统一规范。 |
| B3 | [Sparrived/auto-model-key-router](https://github.com/Sparrived/auto-model-key-router)，PyPI Repository 与发布证明指向此仓库，标注 MIT | Key 选择、冷却/恢复、错误分类；移植到客户端请求管线，不额外引入本地 Python 代理。PyPI README 中仍有旧用户名 sparr68，优先采用发布证明地址。 |
| C1 | 用户尚未指定可核实仓库 | 后续从真实多角色上下文/调度实现中选择；仍按现有群聊规格实施。 |
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
