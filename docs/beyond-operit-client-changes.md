# beyond-operit 升级 — 不需要动服务器的改动（纯客户端）

> **范围**：本文档只收录**完全不触碰 KhatKitHub 服务端**的工作——KhatKit 宿主 App、`khatkit`/`khatkit-core`/`workspace` 等模块、本地文档与测试。任务的详细规格（目标/技术方案/验收标准）见 `docs/beyond-operit-upgrade.md` §4（下称总路线，编号沿用 P0-x/P1-x…），本文档是**执行编排**：拆工作包、标依赖、划边界。
>
> **需要服务端配合的**（统一市场协议、ToolPkg 发布面、审计报告、`min_app_version` 过滤）见同目录 `beyond-operit-server-changes.md`（下称服务端文档）。判断口径：改的是 KhatKit 仓库 Kotlin/Rust/C++ 与本机数据 = 客户端；改的是 `heizige.top` 的 API/库表/审核流水线 = 服务端。
>
> **总原则**（沿用总路线 §3）：遵守 `AGENTS.md`；新增逻辑带单测；`./gradlew assembleDebug test lint` 全绿；安全红线与许可审查不豁免。

---

## 1. 工作包总览

| 包 | 对应总路线 | 服务端依赖 | 独立可交付 |
|---|---|---|---|
| **A1 浏览器 Agent** | P0-1（G2） | 无 | ✅ |
| **A2 记忆系统升级** | P0-2（G4） | 无 | ✅ |
| **A4 视觉 GUI 自动化** | P0-4（G3） | 无 | ✅ |
| **A5 酒馆生态兼容** | P1-4（G14） | 无（L3 并入 B2 市场；L5 明确不做） | ✅ |
| **B1 可视化工作流编辑器** | P1-1（G5） | 无 | ✅ |
| **B2 ToolPkg 运行时 + 市场 UI（客户端侧）** | P1-2（G7/G8） | **依赖服务端 S1–S4** | ❌（本地开发可先行，功能开关关闭） |
| **B3 Key 池 + 任务级模型路由** | P1-3（G10） | 无 | ✅ |
| **C1 多角色群聊** | P2-1（G6） | 无（依赖 A2；酒馆群聊语义在此实现） | ✅ |
| **C2 工作区开发闭环** | P2-2（G9） | 无（发布模板到 Hub 属服务端文档 S5 路线 A） | ✅ |
| **C3 语音 Agent + 入口形态** | P2-3（G11/G12） | 无（TTS 资源包走依赖包=路线 A） | ✅ |
| **C4 虚拟形象运行时** | P3-1（G13） | 无（资源包走路线 A） | ✅ |
| **D0 卡片 UI DSL（附带项）** | 总路线 P1-2 相邻 | 无（规格见 `KhatKitCards/UI_DSL_UPGRADE.md`） | ✅ |

> **已裁剪**：A3 端侧推理（总路线 P0-3/G1）整体不做（产品决策），本地推理/llama.cpp/MNN/Ollama 接入均不排期；成本目标由 B3 承担。
>
> 关键结论：**只有 B2 与服务端有硬耦合**，其余包可全程并行、离线交付。B2 在服务端 S1–S4 落地前只做运行时与本地安装，市场新 kind 入口用功能开关隐藏（服务端文档 §4 红线）。

---

## 2. 各包执行要点

（细节以总路线 §4 对应任务为准，此处只列执行顺序要点与客户端边界）

### A1 浏览器 Agent（P0-1）

- 新增 `browser/` 运行时（多标签 `BrowserSessionManager`、JS DOM 通道、历史 Room 持久化）；仅通过卡片 `BrowserBridge` 暴露给 AI，不增加设置页或 `ChatToolFactory` 内置浏览器工具（同步 `script-api-reference.md` 与 KhatKitCards）
- 验收快照体积（>100 元素页面 <20KB）、3 会话并发、示例卡片 2 个（网页签到、比价抓取）

### A2 记忆系统升级（P0-2）

- `MemoryRepository` 重写：空间/分块/边（轻量图谱）/来源引用；检索 = 向量（sqlite-vector）+ FTS5 + 图谱扩展 + 时间衰减（RRF 融合）
- 工具 `memory_search/add/link/forget`；`<memories>` 注入从全量改检索式
- 验收：20 问评测命中 ≥80%、100% 可溯源、检索 P95 <300ms（5k 规模）

### A4 视觉 GUI 自动化（P0-4）

- 截图 → 多模态 bbox → 与无障碍节点树双通道校验；动作原语对齐 `RecordingSession`；轨迹落盘可回放
- 验收：10 任务成功率 >85%、有节点树时误差 0、录制/回放/Agent 生成同构

### A5 酒馆生态兼容（P1-4；原 A3 端侧推理已裁剪）

- **L1**：角色卡导出（PNG tEXt + JSON，round-trip 零丢失）+ 宏系统（`{{user}}/{{char}}/{{random}}/{{roll}}` 等，未识别宏保留原文）+ **lorebook/world info** 关键词触发引擎（AND/OR/NOT/正则、递归、概率、插入位置；挂 `PromptInjectionTransformer` 管线）
- **L2**：ST 聊天 JSON 导入/导出（swipe→`MessageNode` 分支映射）、preset/instruct 模板（提示顺序/系统提示格式）
- 模块：`AssistantImporter.kt`（V2/V3 双向化）、新增 `core/data/ai/lorebook/`、`ExportSerializer.kt`、PNG 元数据写（`ImageUtils` 已有读）
- 红线：兼容子集写死进测试（超集字段只透传）；不碰酒馆 prompt 模板/扩展体系；**L5（对接用户自建 ST 服务器）明确不做**；L3 卡片市聚合归 B2 市场、L4 群聊语义归 C1
- 验收：20 卡 round-trip 零丢失（自动化 diff）；PNG 可被酒馆本体识别；lorebook 对照 ≥15 条；聊天导出可被酒馆打开；「带工具的酒馆角色」完成设备任务演示

### B1 可视化工作流编辑器（P1-1）

- `feature/workflow/`（Compose Canvas 节点编辑器）+ Room（workflows/runs/steps）+ 执行引擎扩 `FlowSpec`（`khatkit__run_flow` 后端）
- 节点 v1：trigger/llm/card_tool/condition/loop/delay/data_extract/http/notify/sub_flow；触发器节点直接消费 `TriggerEngine` 18 事件源
- 三态互转（可视化 JSON ⇄ 自然语言 ⇄ 卡片脚本）；运行日志走既有 Web API SSE（本机内嵌 Ktor，不涉 Hub）
- 验收：UI 搭通「通知→分类→分支→回复」；导出脚本等价性 ≥3 例

### B2 ToolPkg 运行时 + 市场 UI（P1-2 客户端侧）

- **依赖服务端 S1–S4**（协议/发布面/审计字段/兼容过滤）；本地先行部分：
  - ToolPkg 载体：扩展 `khatkit/.../dependency/` 的 DexClassLoader 机制承载 manifest+dex/脚本+资源；sha256 校验沿用
  - 运行时 hooks：`pre_tool/post_tool/pre_stream/post_stream/on_error`（`GenerationLoop`、工具执行点、`ProviderManager` 预留），hooks 可被用户全局禁用（审批模型覆盖）
  - **Provider 插件**：模型 Provider 热安装（示例：自定义鉴权兼容端点）进 `ProviderManager`
  - 市场 UI：`feature/explore/` 统一 kind 索引/安装/卸载/更新；安装前展示**权限审计报告**（`card-validator` 扩展静态校验产出，规则版本 `audit_rules_version`）
- 功能开关：服务端未就绪时隐藏新 kind 入口；kind=card 主流程不受影响
- 验收（服务端就绪后）：≥6 种 kind 全流程；Provider 插件热安装生效；3 个样例 ToolPkg 审计标注准确

### B3 Key 池 + 任务级模型路由（P1-3）

- `ModelBinding(taskType, candidates[], policy)`：chat/记忆抽取/摘要/标题/OCR/翻译/UI 控制各自候选与 policy（cost/quality/latency）
- Key 池轮换 + 429/5xx 熔断退避 + 每 key 用量；预算（日/月）超限降级或只读；`feature/stats/` 埋点展示
- **纯客户端**（价格表本地可配），不调用 Hub 新接口
- 验收：429 自动切换无感；记忆与聊天模型分离单测；熔断可触发可恢复

### C1 多角色群聊（P2-1，依赖 A2）

- 群聊消息带 `role_id`，按角色视角过滤上下文；协作模式 v1：pipeline/roundtable/vote，每轮 Token 预算
- Tavern 导出 + QR 分享补齐；备份走既有 sync 管线
- 验收：3 角色 @ 提及不串上下文；独立记忆空间生效；导出/恢复可用

### C2 工作区开发闭环（P2-2）

- 项目模板（`assets/workspace_templates/`：Web/Vite、Node、Python、Kotlin-Android、Flutter 骨架 + Agent 项目规则文件）
- 实时预览（proot dev server → 本机 WebView）；内容寻址 snapshot + `workspace_diff/revert`（聊天重发回滚）；SSH/SFTP 工作区后端（选库先做许可评估）；`workspace_package`（debug APK + HTML 打包）
- 工具面新增：`workspace_preview/diff/revert/package`
- 验收：模板→改码→预览→回滚全程工具驱动；diff/回滚单测含路径穿越防护

### C3 语音 Agent + 入口形态（P2-3）

- 唤醒：Sherpa 关键词检测 → 前台服务常驻 → VAD → 流式 ASR；全双工打断（打断延迟 <500ms）
- 入口：`ACTION_ASSIST` 悬浮面板（manifest 加 intent-filter）、Widget、气泡、悬浮球取词（复用 AutomationOverlay 模式）
- 本地 ONNX VITS 资源包：走依赖包分发（服务端文档 S5 路线 A，不新增协议）
- 验收：唤醒误触 <1 次/小时（真机自测）+ 保活清单文档；ASSIST 打开可输入 <1s

### C4 虚拟形象运行时（P3-1）

- `avatar/` 模块（glTF 起步）：状态机 idle/listening/thinking/speaking/error；口型用音量包络；视线跟随；表情订阅 `GenerationLoop` 事件总线
- 形象资源包走依赖包机制（路线 A）；桌面悬浮宠物模式
- 验收：骁龙 7 系 60fps；资源包市场安装/卸载

### D0 卡片 UI DSL（附带项，与 B2 相邻但独立）

- 规格与任务清单见 `/home/heizige/文档/Android/KhatKitCards/UI_DSL_UPGRADE.md` 及其客户端拆分 `UI_DSL_CLIENT_CHANGES.md`
- 全部为宿主 + 卡片仓库工作；与服务端的唯一交叉（`min_app_version`）在服务端文档另有条目，**不阻塞** D0 本地交付

---

## 3. 明确不归本组（防越界）

| 事项 | 归属 |
|---|---|
| 统一市场协议（新 kind 路由/库表/检索）、ToolPkg 发布面、审计报告存证、`min_app_version`/`api_version` 过滤 | 服务端文档 S1–S4 |
| 市场内容上架、审核流水线、`docs/hub-backend.md` | 服务端文档 |
| 形象/TTS 资源包的新 `kind=resource` 品类（仅当产品确认路线 B） | 服务端文档 S5 |
| ToolPkg 付费计量、云端预算账单 | 服务端文档 S6（默认不做） |
| RemoteCompose 引入 | 总路线风险表已否决（仅留作富显示卡候选） |

---

## 4. 依赖与排期

```
并行组1（无依赖）: A1 浏览器 | A2 记忆 | A4 视觉GUI | A5 酒馆 | B1 工作流 | B3 路由 | C2 工作区 | C3 语音 | C4 形象 | D0 卡片UI
                  （A2 完成 → C1 群聊；A5 的 L4 群聊语义归 C1）
                  （A1/A2/A4 稳定 → B2 本地运行时先行；A5 的 L3 卡片市归 B2）
服务端组（独立进程）: S1→S2→S3→S4（见服务端文档）
B2 收尾: 服务端就绪 → 打开市场新 kind 开关 → 内容上架联调
里程碑: A 组全齐 = 核心 Agent 能力达标；B/C 组齐 = 全面超越（总路线 §5）
（原 A3 端侧推理已裁剪，不在图中）
```

建议节奏（与总路线 §5 一致）：P0 包（A1/A2/A4）+ A5 优先 → P1（B1–B3）→ P2/P3（C1–C4）；B2 跟随服务端节奏，不卡其他包。

---

## 5. 验收（客户端可度量部分，总路线 §6 对齐）

- [ ] 自建 20 任务评测集（浏览器 5/文件 5/设备自动化 5/开发 5）端到端成功率 ≥ 基线 +10pp（评测脚本入 `docs/eval/`）
- [ ] 酒馆生态：20 卡 round-trip 零丢失 + lorebook 对照 ≥15 条 + 「带工具的酒馆角色」演示（总路线 §6 指标）
- [ ] 记忆检索命中 ≥80% 且 100% 可溯源
- [ ] 工具审批三级 + 轨迹审计（B2 的市场审计报告待服务端就绪后补）
- [ ] 每包交付跑 `./gradlew test lint` 全绿 + 对应验收标准逐条勾选（格式沿用总路线 §8 任务领取模板）
- [ ] 全程未修改 Hub 协议客户端调用面（`HubClient` 除 B2 市场读写新 kind 外 diff 为空）
