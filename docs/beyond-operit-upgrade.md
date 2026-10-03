# KhatKit 升级超越计划（喂给 AI 开发者的执行文档）

> **文档用途**：本文档是面向 AI 开发 Agent 的任务规格书。目标是把 KhatKit 从「功能齐备的 LLM 客户端 + 卡片运行时」升级为**全面超越 Operit** 的 Android Agent 平台。
>
> **阅读顺序**：先读 `AGENTS.md`（领域概念与代码规范）→ 本文档 §1-2（基线与差距）→ 按 §4 路线图领取任务。
>
> **工作方式**：每次领取一个 P 级任务包（Task），按其中的「涉及模块 / 技术方案 / 验收标准」实现；完成后跑 `./gradlew test lint`，把验收结果写进 `docs/progress.md`（自建）。禁止跳过验收标准直接宣称完成。
>
> **实施拆分**：按是否触碰 KhatKitHub 服务端拆成两份执行文档——`beyond-operit-server-changes.md`（统一市场协议/ToolPkg 发布面/审计报告/兼容过滤，服务端 S1–S7）与 `beyond-operit-client-changes.md`（纯客户端工作包 A1–D0）。本文档是规格基线，冲突时以本文档为准。

---

## 1. 现状基线（不要重复造轮子）

KhatKit = RikkaHub（AGPL-3.0）深度 fork + 自研卡片/自动化体系。技术栈：Kotlin 2.4 + Compose（M3 Expressive ↔ Miuix 双风格）、Hilt、Room（FTS5 + sqlite-vector）、Ktor 嵌入式服务、Rust（khatkit-core：mlua + rquickjs）、MCP Kotlin SDK（双向）。

**已具备、可直接复用的资产：**

| 资产 | 位置 | 备注 |
|---|---|---|
| 工具调用 Agent 循环 | `app/src/main/java/heizige/kk/khatkit/app/core/data/ai/GenerationLoop.kt` | 已有 HITL 审批、failover、上下文裁剪 |
| 工具装配点 | `app/.../core/data/ai/tools/ChatToolFactory.kt` | 新增工具一律在此注册 |
| 工具抽象 | `ai/src/main/java/heizige/kk/khatkit/ai/core/Tool.kt` | `InputSchema` + `execute` + `needsApproval` |
| Provider 层 | `ai/src/main/java/heizige/kk/khatkit/ai/provider/` | OpenAI/Google/Claude/Vertex/自定义兼容端点 |
| MCP 客户端 + OAuth | `app/.../core/data/ai/mcp/` | SSE / streamable HTTP |
| MCP 服务端 + REST API | `app/.../core/network/`（`McpModule.kt`、`WebApiModule.kt`） | 已暴露卡片为 MCP 工具 |
| 卡片脚本运行时 | `khatkit/`（桥）+ `khatkit-core/`（Rust：Lua 5.4 / QuickJS） | 20 个桥接口，见 `docs/script-api-reference.md` |
| 无障碍自动化 | `app/.../feature/automation/` + `KhatKitAccessibilityService` | 节点查找/点击/输入/滚动/手势 + 截图 |
| 触发器系统 | `khatkit/.../trigger/TriggerEngine.kt`，见 `docs/triggers.md` | 18 种事件源、WorkManager、精确闹钟 |
| proot Linux 终端 | `workspace/`（Kotlin + C++/JNI） | shell 工具已接入（600s 上限） |
| 向量检索基础 | `app/.../core/data/ai/CardEmbeddingEngine.kt` + sqlite-vector | 目前仅卡片 store 用 |
| Web 搜索 SDK | `search/`（~20 provider） | 含 Exa/Tavily/Jina/Firecrawl 等 |
| 语音 | `speech/`（TTS + ASR，含 Sherpa 端侧） | 有 VoiceMode |
| Skills（Agent Skills 规范） | `app/.../core/data/ai/tools/SkillsTools.kt` + `assets/builtin_skills/` | 仅本地安装 |
| Hub 卡片市场 | `khatkit/.../hub/HubClient.kt` | sha256 校验、自动更新、付费卡片 |

**核心数据流**：ChatViewModel → ConversationSession/ChatManager → transformers（输入）→ `ProviderManager.stream` → `GenerationLoop`（工具分发：local/MCP/card/workspace）→ transformers（输出）→ Room 持久化 → Compose 渲染。

---

## 2. 差距矩阵（对照 Operit，标注超越点）

图例：✅ 已有等价物 ｜ ⚠️ 部分 ｜ ❌ 缺失

| # | 能力域 | 状态 | KhatKit 现状 | Operit 对标 | **超越点（必须做到比它更好）** |
|---|---|---|---|---|---|
| G1 | ~~端侧推理~~ | 🚫 | 仅云端 Provider | MNN + llama.cpp GGUF + Ollama/LM Studio | **已裁剪（产品决策）**：不做端侧推理；成本/隐私目标改由 G10 任务级路由（便宜模型/只读降级）承担 |
| G2 | 浏览器 Agent | ❌ | 仅 WebView 渲染 + 登录 Cookie 桥 | 内置多标签浏览器 + DOM Agent | **可编程浏览器**：浏览器操作可写成卡片脚本（桥接口），并支持并行多实例 |
| G3 | 视觉 GUI 自动化 | ⚠️ | 无障碍节点树 + `khatkit__device_screen/device_act` | PhoneAgent/AutoGLM 视觉模型 + 虚拟显示器 | **视觉 + 无障碍双通道融合**（视觉定位 → 无障碍精确执行），带可回放轨迹 |
| G4 | 长期记忆 | ⚠️ | `MemoryTools` 纯 KV CRUD | 多记忆空间、文档分块、图谱、时序/语义/关系检索、自动抽取 | **可解释记忆**：每条记忆带来源引用（消息 ID）、置信度、衰减，检索结果可审计 |
| G5 | 可视化工作流 | ⚠️ | flow（AI 拼卡片 pipeline）+ 18 触发器，无编排 UI | 节点式编辑器 + 运行日志/统计/批量管理 | **双向**：可视化 ⇄ 自然语言 ⇄ 卡片脚本三态互转 |
| G6 | 多角色/群聊 | ⚠️ | Assistant + Tavern 导入 | 群聊 @ 提及、独立历史、按角色绑模型/记忆/工具包 | 角色间**协作协议**（议长/投票/流水线模式），带成本预算 |
| G7 | 统一扩展市场 | ⚠️ | Hub 只有卡片 + 依赖包 | scripts/ToolPkg/Skills/MCP/prompt/Artifact 统一市场 | 市场内置**沙箱评分 + 权限审计报告**，安装前可见风险 |
| G8 | 运行时扩展（ToolPkg） | ⚠️ | 依赖包（dex）+ 卡片 | 扩展工具/UI/模型 Provider/hooks | 热插拔**模型 Provider 插件** + 生命周期 hooks（pre/post tool、pre/post stream） |
| G9 | 工作区/开发环境 | ⚠️ | proot 终端 + 文件编辑器 + DocumentsProvider | 项目模板、实时预览、改动回滚、SSH/SFTP、Git/APKTool | **端到端移动开发闭环**：模板→编码→预览→调试→打包→发布到 Hub，全程 Agent 可驱动 |
| G10 | Key 池 + 任务级模型 | ⚠️ | per-assistant 模型绑定 | Key 池、chat/记忆/摘要/UI 各自模型 | **成本/延迟/质量三目标自动选路** + 预算熔断 |
| G11 | 语音 Agent | ⚠️ | TTS/ASR/VoiceMode | 唤醒词、后台常驻、音乐队列、本地 VITS | **全双工打断式对话**（边听边说、随时打断） |
| G12 | 入口形态 | ⚠️ | Quick Settings 磁贴、浮窗组件、前台服务 | 悬浮窗/气泡/Widget/系统默认助手（ASSIST） | **系统级 Agent 入口**：ASSIST + 无障碍全局取词悬浮球 + 分享面板全链路 |
| G13 | 虚拟形象 | ❌ | 仅静态 Avatar（emoji/图片） | DragonBones/glTF/FBX/MMD/MP4 桌面宠物 | 轻量 **Live2D 式状态机**（口型、视线跟随、情绪与工具执行状态联动） |
| G14 | 酒馆（SillyTavern）生态 | ⚠️ | 角色卡 V2/V3 导入（`AssistantImporter`） | 完整角色生态：卡片/lorebook/预设/聊天互通、群聊、角色市场 | **会干活的纸片人**：角色卡可绑定工具/记忆/自动化/工作区（酒馆做不到） |

---

## 3. 通用工程约束（每个任务都必须遵守）

1. **规范先行**：改代码前读 `AGENTS.md`；领域概念（Assistant、Conversation、UIMessage、MessageNode、Transformer）不得另起炉灶。
2. **代码风格**：Kotlin 惯用写法，无多余注释；不引未在 `gradle/libs.versions.toml` 出现过的新依赖，若必须引入，先在任务 PR 描述中说明理由并更新 version catalog。
3. **测试**：新增逻辑必须带单元测试（项目已有 `app/src/test`、`khatkit/src/test` 覆盖先例）。涉及桥接口的改动要同步 `docs/script-api-reference.md`（`BridgeApiDocTest` 会校验）。
4. **构建验证**：`./gradlew assembleDebug`、`./gradlew test`、`./gradlew lint` 三者全绿才算完成。构建依赖兄弟工程 `../Kedge`、`../Khromia`（composite build，缺失会失败，属环境问题而非代码问题）。
5. **安全红线**：任何新权限/新网络域必须进审批模型（`ToolApprovalState`、manifest `network.allow` 模式）；不得把密钥/Token 写入日志；卡片侧能力必须走既有 L0 权限与 `RunDeadline`/`SqlGuard`/`StoreQuota`。
6. **隐私**：记忆、浏览器 Cookie/历史一律本地存储；任何上云行为需要显式开关。
7. **兼容**：minSdk 26 / targetSdk 37；Room 迁移不得丢数据；已有卡片与 Hub 协议不得破坏（`hub-backend.md`）。
8. **许可注意**：项目是 AGPL-3.0（RikkaHub 衍生）。引入 LGPL（如 Operit 的部分组件）或其他许可代码前先做许可相容性检查并在 `docs/` 记录。

---

## 4. 路线图任务包

优先级：P0 = 核心 Agent 能力（缺了就称不上超越）→ P3 = 差异化锦上添花。任务可并行领取，但同编号子任务串行。

### P0-1 浏览器 Agent（差距 G2）

**目标**：内置可编程浏览器；Agent 能读页面结构、点击、输入、滚动、截图、抓取文本/链接；浏览器操作可被卡片脚本调用。

**涉及模块**
- 新增 `browser/` 模块（WebView 封装 + DOM 通道），或扩展 `app/.../core/ui/components/webview/`
- 工具注册：不增加 `ChatToolFactory` 内置浏览器工具；AI 仅通过安装卡片获得
  `khatkit__<card-name>` 工具，宿主向卡片注入 `BrowserBridge`
- 桥扩展：`khatkit/.../bridge/Bridges.kt`（扩展 `WebBridge` 或新增 `BrowserBridge`），同步 `docs/script-api-reference.md`
- UI：复用 `app/.../feature/webview/WebViewPage.kt` 改造为多标签浏览器页面（历史/书签/下载）

**技术方案**
1. `BrowserSessionManager`：多标签会话（每标签一个 WebView + 唯一 id），状态持久化（历史/书签进 Room）。
2. DOM 通道：注入 JS 桥（`evaluateJavascript`），提供：
   - `page_snapshot(selector?)` → 语义化元素树（tag、text、role、bbox、可交互性），大页面做**增量快照 + 引用 id**，避免 token 爆炸
   - `click / type / scroll / press_key / hover`（按元素 id 或 selector）
   - `extract_text / extract_links / screenshot`（截图走既有 `khatkit__device_screen` 的编码管线）
3. 工具审批：默认 `Ask every time`（对齐项目 HITL 模式）；`browser_navigate` 域名白名单可按 Assistant 配置。
4. 与卡片打通：`BrowserBridge.open/act/snapshot`，使自动化脚本可驱动浏览器。

**验收标准**
- [ ] Agent 单轮可完成「打开登录页 → 输入账号密码（用户手动授权后）→ 点击登录 → 抓取登录后文本」全流程
- [ ] 100 元素以上页面快照 < 20KB（截断/分页可读）
- [ ] 多标签并发 3 个会话互不串状态；进程被杀后历史/书签可恢复
- [ ] 3 个以上单元测试（快照序列化、选择器解析、工具 schema）

**超越点落实**：提供 `browser_*` 卡片桥 + 并行多实例（每标签独立 Cookie jar 可选共享），并给出 2 个示例卡片（网页签到、比价抓取）。

---

### P0-2 记忆系统升级（差距 G4）

**目标**：从 KV 升级为多空间 + 分块 + 混合检索 + 自动抽取的长期记忆，并保留**可解释性**（来源引用、置信度）。

**涉及模块**
- `app/.../core/data/repository/MemoryRepository.kt`（重写/扩展）
- `app/.../core/data/ai/tools/MemoryTools.kt`（工具 schema 升级）
- `app/.../core/data/ai/CardEmbeddingEngine.kt`（复用/抽为通用 EmbeddingEngine）
- Room：新表 `memory_spaces / memory_chunks / memory_edges / memory_mentions`（FTS5 + sqlite-vector 已在依赖里）
- 检索入口：`GenerationLoop` 上下文装配处注入 `<memories>`（已有注入点，扩展为「检索式注入」而非全量）

**技术方案**
1. **空间（Space）**：global / per-assistant / per-conversation 三级；空间可绑定 Embedding 模型（走 Provider 层，允许用便宜模型做记忆抽取——见 P1-3 任务级模型分配）。
2. **分块与抽取**：对话/文档 → 分块 → LLM 抽取三元组（实体、关系、事件，带时间戳）→ 写入 `memory_edges`（轻量图谱：节点=实体，边=关系/共现）。
3. **混合检索**：向量（sqlite-vector）+ FTS5 + 图谱扩展（1-2 跳邻居）+ 时间衰减；RRF 或加权融合排序。
4. **可解释性**：每条记忆存 `source_message_id / extracted_at / confidence / last_hit_at`；检索结果在 UI 可展开「来源」跳转原消息。
5. **工具**：`memory_search / memory_add / memory_link / memory_forget`（遗忘=软删 + 影响溯源）；`memory_search` 结果带 id 与来源。

**验收标准**
- [ ] 导入 50 篇长文档后，检索「XXX 的负责人是谁」类问题命中率 > 80%（自建 20 条评测集）
- [ ] 每条注入上下文的记忆可溯源到消息/文档块
- [ ] 自动抽取可开关，抽取失败不影响主对话
- [ ] 检索 P95 延迟 < 300ms（本地库，5k 记忆规模）

**超越点落实**：来源溯源 + 置信度 + 衰减，UI 上可视化记忆图谱（Mermaid 已内置 `assets/html/mermaid.min.js`，可复用）。

---

### P0-3 端侧推理（差距 G1）— 🚫 已裁剪（产品决策）

本任务整体移除，不做端侧/本地推理（llama.cpp GGUF、MNN、Ollama/LM Studio 接入均不排期）。连带裁剪：
- 混合路由中的端侧分流（privacy/cost/fallback 指端侧的部分）——成本目标改由 **P1-3 任务级模型路由**的便宜模型候选与只读降级承担
- 模型下载/本地资源治理（`feature/download/` 不为模型扩展）
- P1-3 的 `local_first` 策略、预算降级端侧选项一并移除

保留的相邻能力（**不**属于端侧推理，照常做）：Provider 层对自定义 OpenAI 兼容端点的支持（云端任意兼容服务）。

---

### P0-4 视觉 GUI 自动化（差距 G3）

**目标**：在无障碍节点树之外增加「视觉理解 → 动作」通道，双通道融合执行，轨迹可回放。

**涉及模块**
- `app/.../feature/automation/`（AutomationBus、AccessibilityService、`RecordingSession`）
- 工具：`khatkit__device_screen` / `khatkit__device_act`（`app/.../core/data/ai/tools/KhatKitTools.kt`）升级
- 新增 `vision/` 或并入 automation：区域检测、坐标映射

**技术方案**
1. 截图 → 多模态模型（已有图片输入能力）输出**带 bbox 的元素描述** → 坐标映射到无障碍节点树做**双通道校验**（视觉给候选，无障碍给精确节点；不一致时以无障碍为准并上报）。
2. 动作原语对齐 `RecordingSession` 的录制格式，保证「录制 → 回放 → Agent 生成」三者同构。
3. 轨迹日志：每次执行落盘（截图帧 + 动作 + 目标 id），可在 UI 回放审计。
4. 虚拟显示器（Root/Shizuku 可用时）作为可选能力，失败自动回退真实屏幕。

**验收标准**
- [ ] 「打开设置 → 关闭某开关」类任务成功率 > 85%（10 机型矩阵自测 3 台以上）
- [ ] 视觉坐标点击误差 ≤ 8dp（有节点树时直接用节点中心，误差为 0）
- [ ] 轨迹可回放；录制文件可被 Agent 复用为脚本

---

### P1-1 可视化工作流编辑器（差距 G5）

**目标**：节点式工作流（触发/执行/条件/逻辑/数据抽取节点）+ 运行日志/统计/批量管理；与 flow（`docs/flow.md`）和 triggers（`docs/triggers.md`）双向打通。

**涉及模块**
- 新增 `app/.../feature/workflow/`（Compose Canvas 节点编辑器）
- 持久化：Room 新表 `workflows / workflow_runs / workflow_run_steps`
- 执行引擎：扩展现有 `khatkit__run_flow`（`FlowSpec` 已有，见 `app/src/test/.../FlowSpecTest.kt`）为可视化定义的执行后端

**技术方案**
1. 节点类型 v1：`trigger / llm / card_tool / condition / loop / delay / data_extract / http / notify / sub_flow`。
2. **三态互转（超越点）**：可视化 JSON ⇄ 自然语言描述 ⇄ 卡片脚本（Lua/JS）；自然语言入口走已有 GenerationLoop 生成 `FlowSpec`。
3. 运行管理：日志流（复用 Web API SSE 通道 `WebApiModule` 的 events）、失败重试、取消、统计（次数/耗时/成本）。
4. 触发器节点直接消费 `TriggerEngine` 的 18 种事件源，不重复实现调度。

**验收标准**
- [ ] 可在 UI 中搭出「收到特定通知 → LLM 分类 → 条件分支 → 发送回复/写文件」流程并保存
- [ ] 同一流程可导出为卡片脚本且行为等价（等价性测试 ≥ 3 例）
- [ ] 运行日志可逐步展开查看每节点输入/输出

---

### P1-2 统一扩展市场 + ToolPkg 运行时（差距 G7/G8）

**目标**：Hub 升级为覆盖卡片、Skills、MCP 配置、prompt、工作流、依赖包、**模型 Provider 插件**的统一市场；安装前可见沙箱评分与权限审计。

**涉及模块**
- `khatkit/.../hub/HubClient.kt` + `docs/hub-backend.md`（协议扩展）
- `khatkit/.../dependency/`（DexClassLoader 加载机制，扩展为 ToolPkg 载体）
- `app/.../feature/explore/`（市场 UI）
- 生命周期 hooks：`GenerationLoop`、工具执行点、ProviderManager 预留 hook 接口

**技术方案**
1. 市场条目统一 schema：`kind: card | skill | mcp | prompt | workflow | dependency | provider_plugin`，索引懒加载沿用现有设计。
2. ToolPkg = manifest + dex/脚本 + 资源；能力面：工具注册、设置面板、**Provider 实现**、hooks（`pre_tool / post_tool / pre_stream / post_stream / on_error`）。
3. **沙箱评分（超越点）**：`card-validator/` 静态校验扩展到 ToolPkg，产出「权限审计报告」（申请权限、网络域、危险 API 调用点），市场页展示评分。
4. hooks 必须可被审批模型覆盖（用户可全局禁用某 hook）。

**验收标准**
- [ ] 一个 Provider 插件（示例：自定义鉴权的兼容端点）可热安装并在设置页生效，无需更新 App
- [ ] 市场内 5 种以上 kind 可搜索/安装/卸载/更新（sha256 校验沿用）
- [ ] 权限审计报告对 3 个样例 ToolPkg 给出准确风险标注

---

### P1-3 Key 池 + 任务级模型路由（差距 G10）

**目标**：Key 池轮换/故障转移；chat、记忆抽取、摘要、标题、OCR、翻译、UI 控制各自指定模型与优先级；成本预算熔断。

**涉及模块**
- `ai/.../provider/ProviderManager.kt`、`app/.../core/data/datastore/`（设置模型）
- 消费点：`app/.../core/data/ai/prompts/`（标题/摘要/翻译/OCR/压缩等管线）
- UI：`app/.../feature/settings/provider/`

**技术方案**
1. `ModelBinding(taskType, candidates[], policy)`：candidates 有序，policy ∈ `cost_first / quality_first / latency_first`。
2. Key 池：多 key 轮换 + 429/5xx 熔断退避 + 每 key 用量统计。
3. 预算：按日/月 Token 与费用估算（模型价格表可配），超限降级到低成本候选模型或只读模式。
4. 验收指标埋点：每任务记录实际用模型/耗时/Token，`feature/stats/` 展示。

**验收标准**
- [ ] 429 时自动切换 key/候选模型，对话无感
- [ ] 记忆抽取与聊天可用不同模型，单测覆盖路由决策
- [ ] 预算熔断可触发且可恢复

---

### P1-4 酒馆生态兼容（差距 G14）

**目标**：成为 SillyTavern 角色卡生态的移动端主场——角色卡 round-trip、lorebook、宏、聊天/预设互通；且角色在 KhatKit 里**能带工具/记忆/自动化**（差异化）。

**涉及模块**
- `app/.../feature/assistant/detail/AssistantImporter.kt`（现有 `CharaCardV2Parser/V3Parser` 扩展为双向）
- 新增 `app/.../core/data/ai/lorebook/`：world info 关键词触发引擎（挂进 `PromptInjectionTransformer` 管线）
- 宏展开：`{{user}}/{{char}}/{{random}}/{{roll}}` 等（在模板层做，勿与 Pebble 混用）
- `app/.../core/data/export/ExportSerializer.kt` + PNG 元数据读写（`ImageUtils.getTavernCharacterMeta` 已有读，补写）
- 聊天互通：ST 聊天 JSON ↔ `MessageNode`（swipe/分支映射）

**范围分层**
| 层 | 内容 | 归属 |
|---|---|---|
| L1 | 角色卡导出（PNG tEXt + JSON，round-trip 不丢字段）+ 宏系统 + lorebook/world info | 本任务（必做） |
| L2 | ST 聊天记录导入/导出（swipe→MessageNode）、preset/instruct 模板（提示顺序/系统提示格式） | 本任务（必做） |
| L3 | Chub/角色卡市搜索下载 | 并入 P1-2 统一市场（kind=card 生态源） |
| L4 | 酒馆群聊语义（多角色 @、独立历史） | 并入 P2-1（同一实现） |
| L5 | 对接用户自建 ST 服务器做数据源 | **明确不做** |

**技术方案**
1. **兼容子集**定义（写死进文档与测试）：chara_card_v2/v3 全字段、lorebook（关键词/逻辑/插入位置/递归/概率）、常用宏、ST 聊天 JSON v1。超出子集字段：导入保留、导出原样回写，不解释。
2. **lorebook 引擎**：条目 = 触发键（AND/OR/NOT/正则）+ 内容 + 插入位置（角色卡前/后、@深度）+ 递归开关 + 概率 + 优先级；上下文装配时按当前对话内容扫描触发，注入走既有 transformer 管线（`app/.../core/data/ai/transformers/`）。
3. **宏**：`{{user}}/{{char}}`（会话角色名）、`{{random:a|b}}`、`{{roll:1d6}}`、`{{lastMessage}}`、`{{time}}`；在系统提示/lorebook/首条消息组装时统一展开；未识别宏保留原文。
4. **round-trip 校验**：导出用同一数据模型写回 PNG tEXt `chara` 字段；建立 20 张真实卡片回归集（v2/v3 混合、含嵌套 lorebook）。
5. **红线**：不追求 100% 酒馆兼容（prompt 模板/扩展体系不碰）；解析层版本化容错（spec 分发结构沿用 `TAVERN_PARSERS`）。

**验收标准**
- [ ] 20 张真实卡片导入→导出→再导入，字段零丢失（自动化 diff 断言）
- [ ] PNG 导出可被 SillyTavern 本体识别导入
- [ ] lorebook 触发/递归/概率/插入位置行为与酒馆语义一致（对照测试集 ≥15 条）
- [ ] 宏全量展开 + 未识别宏保留；聊天 JSON 导出可被酒馆打开且分支完整
- [ ] 角色绑定工具/记忆/自动化后正常工作（差异化演示：一个带工具的酒馆角色完成设备任务）

---

### P2-1 多角色群聊（差距 G6）

**目标**：多角色会话（@ 提及、独立历史、按角色绑定模型/记忆空间/工具包/Skills/MCP），支持协作协议。

**涉及模块**
- `app/.../feature/chat/`（ConversationSession、ChatManager、MessageQueue）
- 数据层：Room 会话-角色关联表；Assistant 扩展绑定项（记忆空间引用来自 P0-2）
- UI：会话内角色列表、@ 输入、气泡分色

**技术方案**
1. 群聊消息带 `role_id`；上下文装配按「角色视角」过滤（每个角色只看自己历史 + 提及自己的消息 + 摘要）。
2. 协作模式 v1：`pipeline`（顺序接力）、`roundtable`（议长汇总）、`vote`（多数决）；每轮有 Token 预算。
3. Tavern 角色卡导入已具备（`assistant_importer_import_tavern_*`），补齐导出 + QR 分享。

**验收标准**
- [ ] 3 角色群聊 @ 提及正确路由，互不串上下文
- [ ] 每角色独立记忆空间生效（P0-2 依赖）
- [ ] 群聊可导出/备份恢复（走既有 sync 管线）

---

### P2-2 工作区开发闭环（差距 G9）

**目标**：项目模板 + 实时预览 + 改动追踪/回滚 + 远程文件系统 + 移动打包，形成「模板→编码→预览→调试→打包→发布」闭环。

**涉及模块**
- `workspace/`（proot）、`app/.../feature/workspace/`（编辑器/终端页）
- 工具：`WorkspaceTools.kt`（新增 `workspace_preview / workspace_diff / workspace_revert / workspace_package`）
- 新增模板资产：`app/src/main/assets/workspace_templates/`（Web/Vite、Node、Python、Kotlin-Android、Flutter 骨架）

**技术方案**
1. 模板 = 目录骨架 + 初始化脚本 + Agent 项目规则文件（如 `AGENTS.md` 工作区版，让 Agent 读取项目约定）。
2. 实时预览：proot 内起 dev server → 本机 WebView 打开（端口映射），失败给日志。
3. 改动追踪：文件级 snapshot（内容寻址存储）+ diff 工具 + 一键回滚（对齐 Operit 的 workspace rollback 语义，聊天重发时也回滚）。
4. 远程 FS：SSH/SFTP 作为工作区后端（选库前做许可评估）；SAF 已有 `WorkspaceDocumentsProvider` 可保留。
5. 打包：APK（debug 签名）与 HTML 打包两个最小闭环，走 `workspace_package` 工具。

**验收标准**
- [ ] Agent 从模板创建项目 → 修改代码 → 预览可见 → 回滚生效，全程工具驱动
- [ ] diff/回滚有单测（内容寻址、路径穿越防护）
- [ ] SSH 工作区可读写文件并被 Agent 工具访问

---

### P2-3 语音 Agent 完整化（差距 G11）+ 入口形态（G12）

**目标**：唤醒词 + 后台常驻 + 全双工可打断对话；补齐 ASSIST 入口、Widget、气泡、悬浮球取词。

**涉及模块**
- `speech/`（ASR/TTS）、`app/.../feature/chat/VoiceMode`、`VoiceSessionController`
- `app/src/main/AndroidManifest.xml`（ASSIST intent-filter、Widget provider）
- `app/.../feature/automation/`（悬浮球复用 AutomationOverlayService 模式）

**技术方案**
1. 唤醒：Sherpa 端侧关键词检测（已有 Sherpa 依赖路径）→ 前台服务常驻 → VAD 分段 → 流式 ASR。
2. 全双工：TTS 播放中检测用户开口（回声消除/能量门限 + ASR partial）→ 打断播报 → 保留已生成未播文本为草稿。
3. ASSIST 入口：`ACTION_ASSIST` → 悬浮对话面板，带上当前屏幕/选中文本上下文。
4. Widget：最近会话快捷输入 + 触发器快捷键。
5. 本地 TTS：可选 ONNX VITS 资源包（走依赖包机制分发，控制 APK 体积）。

**验收标准**
- [ ] 唤醒词误触率 < 1 次/小时（真机自测），后台存活不被厂商杀（给出保活清单文档）
- [ ] 打断延迟 < 500ms
- [ ] ASSIST 打开到可输入 < 1s

---

### P3-1 虚拟形象 + 桌面宠物（差距 G13）

**目标**：可动虚拟形象（口型/视线/情绪），与 Agent 状态（思考中/执行工具/报错）联动；桌面悬浮宠物模式。

**涉及模块**：新增 `avatar/` 模块（建议 glTF 起步，比 DragonBones/FBX 生态更可控）；UI 挂件接入聊天页与悬浮窗。

**技术方案**
1. glTF 状态机：`idle / listening / thinking / speaking / error`，口型用音量包络驱动（无需音素级），视线跟随手指/滚动。
2. 表情映射工具执行事件（`GenerationLoop` 发事件总线，avatar 订阅）。
3. 资源走 Hub/依赖包分发，运行时热切换形象。

**验收标准**
- [ ] 60fps 中端机（骁龙 7 系）不掉帧
- [ ] 形象资源包可从市场安装/卸载

---

## 5. 里程碑与依赖关系

```
P0-1 浏览器Agent ──┐
P0-2 记忆升级 ────┼─→ P1-1 可视化工作流（用到工具/记忆/浏览器节点）
P0-4 视觉GUI ─────┘
        │
        ├─→ P1-2 统一市场/ToolPkg（依赖 P0 全部工具面稳定）
        ├─→ P1-3 Key池/任务路由（独立，可与 P0 并行）
        ├─→ P1-4 酒馆生态兼容（独立；L3 依赖 P1-2 市场）
        └─→ P2-1 群聊（依赖 P0-2 记忆空间；酒馆群聊语义在此实现）
                P2-2 工作区闭环（独立）
                P2-3 语音+入口（独立）
                    └─→ P3-1 虚拟形象（依赖事件总线，P2-3 后）

（P0-3 端侧推理已裁剪，不在图中）
```

**建议节奏**：P0（P0-1/2/4）完成 = 「达到 Operit 核心能力」；P1 完成 = 「开始超越」；P2/P3 完成 = 「全面超越并形成差异化」。

---

## 6. 验收与度量（超越的可量化定义）

| 指标 | Operit 水平（参考） | 本项目目标 |
|---|---|---|
| Agent 端到端任务成功率（自建 20 任务评测集：浏览器 5 / 文件 5 / 设备自动化 5 / 开发 5） | 基线记录后对标 | ≥ 基线 + 10pp |
| 酒馆生态互通 | 角色卡导入 | 20 卡 round-trip 零丢失 + lorebook 对照 ≥15 条 + 一个「带工具的酒馆角色」演示 |
| 记忆检索命中（20 问评测） | 图谱+混合检索 | ≥ 80% 且 100% 可溯源 |
| 工具审批安全 | 三级审批 | 三级审批 + 沙箱评分 + 轨迹审计 |
| 扩展市场 kind 数 | 统一市场 | ≥ 6 种 kind + 审计报告 |
| 新用户 10 分钟内跑通首个 Agent 任务 | 引导完善 | 引导完善 + 模板化任务包（一键示例） |

评测集脚本放 `docs/eval/`（任务定义 JSON + 结果记录模板），每次 P 级任务完成跑一遍并记录。

---

## 7. 风险登记

| 风险 | 影响 | 缓解 |
|---|---|---|
| 酒馆格式演进（v2→v3+）与字段兼容 | P1-4 导入/导出损坏 | 兼容子集写死进测试；超集字段只透传不解释；解析容错 |
| WebView JS 注入被页面反制 | P0-1 能力受限 | 提供「无障碍兜底」通道；快照失败自动截图+视觉通道 |
| 记忆抽取增加 Token 成本 | 体验/成本 | 抽取默认用便宜模型（P1-3 路由） |
| AGPL 传染与第三方许可 | 法务 | 新依赖入库前做许可审查清单 |
| 厂商后台杀进程 | 语音/触发器失效 | 保活文档 + 前台服务 + 对齐现有 TriggerService 实践 |
| `../Kedge`、`../Khromia` 依赖导致构建不可移植 | CI/协作 | 环境问题与代码问题区分记录；长期考虑 vendoring 或版本化发布 |

---

## 8. 任务领取模板（AI 开发者每次输出用）

```markdown
## 任务：<P编号-子编号 标题>
### 变更文件
- path/to/file.kt — 做了什么
### 关键决策
- 为什么这样实现（≥2 条备选方案的取舍）
### 测试
- ./gradlew test 结果摘要（用例数/通过数）
- 新增测试文件列表
### 验收对照
- [x] / [ ] 逐条勾选 §4 对应验收标准，未完成项写原因
### 文档同步
- 是否更新 docs/script-api-reference.md / progress.md
```
