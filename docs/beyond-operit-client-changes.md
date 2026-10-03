# beyond-operit 升级 — 不需要动服务器的改动（纯客户端）

> **范围**：本文档只收录**完全不触碰 KhatKitHub 服务端**的工作——KhatKit 宿主 App、`khatkit`/`khatkit-core`/`workspace` 等模块、本地文档与测试。任务的详细规格（目标/技术方案/验收标准）见 `docs/beyond-operit-upgrade.md` §4（下称总路线，编号沿用 P0-x/P1-x…），本文档是**执行编排**：拆工作包、标依赖、划边界。
>
> **需要服务端配合的**（统一市场协议、ToolPkg 发布面、审计报告、`min_app_version` 过滤）见同目录 `beyond-operit-server-changes.md`（下称服务端文档）。判断口径：改的是 KhatKit 仓库 Kotlin/Rust/C++ 与本机数据 = 客户端；改的是 `heizige.top` 的 API/库表/审核流水线 = 服务端。
>
> **总原则**（沿用总路线 §3）：遵守 `AGENTS.md`；新增逻辑带单测；`./gradlew assembleDebug test lint` 全绿；安全红线与许可审查不豁免。

> **实现方硬约束（每次开工前复述）**：① **C4 排最后**，其余包验收通过前不得开工；② 每包交付必须执行 `./gradlew --offline assembleDebug test lint` 并保持全绿（以 `AGENTS.md` 为准）；③ **B2 功能开关默认关闭**，服务端 S1–S4 未上线前不得向用户暴露新 `kind` 入口（本地运行时/安装校验可先行）。

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
- **桥面与权限口径**（与总路线 P0-1 的差异以此为准——按 Card-only 决策收口）：
  - 桥方法 `open(url) / act(action…) / snapshot(scope?, since?) / wait / close`；AI 只经 `khatkit__<card-name>` 卡片工具间接触达
  - 域名白名单来自卡片 manifest 不可变的 `network.allow`（非 Assistant 配置）；方法权限来自 `permissions.methods`；审批 = 卡片级授权 + 每次运行默认 Ask
  - 每次运行的标签归调用卡片所有，退出统一销毁；跨标签操作 mutex 串行，不同标签独立
- **验收口径**：
  - 快照体积：选取 100+ 元素静态页（测试 fixture 与真实页各一），`snapshot()` UTF-8 字节数 <20KB（超限自动截断/分页，`next_offset` 可续读）
  - 3 会话并发：3 张卡片各开 1 标签同时导航/输入/读取，断言 DOM 状态互不串
  - 示例卡片 2 个：`browser_checkin`（签到）、`browser_compare_prices`（2–3 标签比价），域名/选择器可配置，真实站点验收单列

### A2 记忆系统升级（P0-2）

- `MemoryRepository` 重写：空间/分块/边（轻量图谱）/来源引用；检索 = 向量（sqlite-vector）+ FTS5 + 图谱扩展 + 时间衰减（RRF 融合）
  - **空间三级**：`global` / `per-assistant` / `per-conversation`（空间可绑不同 Embedding 模型，抽取可走便宜模型=接 B3 路由）
  - **可解释性**：每 chunk 存 `source_message_id / extracted_at / confidence / last_hit_at`；检索结果带 id，UI 可展开「来源」**点击跳转原消息**
- 工具 `memory_search/add/link/forget`；`<memories>` 注入从全量改检索式
- **验收口径**：
  - 命中评测：导入 50 篇长文档 → 自建 20 条问答集（实体归属/事实查证类），**正确 chunk 出现在检索 top-5 即算命中**，命中率 ≥80%；评测脚本入 `docs/eval/`，与来源 id 断言一起跑
  - 可溯源：20 条答案引用的 chunk 100% 能回溯到 source_message_id（脚本断言，非人工）
  - P95 <300ms：注入 5k chunk 后在真机测 20 次检索耗时取 P95（JVM 数据不作数）
- **本轮不做**（记入状态文档，非范围蔓延）：bi-temporal 冲突标记、记忆图谱 Mermaid 可视化

### A4 视觉 GUI 自动化（P0-4）

- 截图 → 多模态 bbox → 与无障碍节点树双通道校验；动作原语对齐 `RecordingSession`；轨迹落盘可回放
  - **同构定义**：录制 `RecordedStep` ⇄ `UiAction`（+bbox）⇄ Agent 生成动作三者共用同一数据模型，双向转换 round-trip 断言类型/参数零丢失
  - **误差 0 定义**：视觉 bbox 命中无障碍节点（中心包含优先，IoU≥0.25 兜底）时取**节点中心**执行，几何误差=0（纯函数 JVM 测试覆盖）；未命中走 bbox 中心，声明误差 ≤8dp
  - **轨迹**：JSONL 每步 = 截图帧路径 + 动作 + 目标 + 结果 + 命中方式；回放用同一无障碍桥执行
- **验收口径**：10 个设置页任务成功率 >85%（评测集入 `docs/eval/`，建议：开 WiFi、调亮度、连 WiFi、开手电筒、加闹钟、静音、清通知、开勿扰、截图、查看版本号）；3 台设备矩阵（至少 1 台无节点树的兜底路径）；有节点树时误差 0 由 JVM 用例 + 真机抽验双重确认

### A5 酒馆生态兼容（P1-4；原 A3 端侧推理已裁剪）

- **L1**：角色卡导出（PNG tEXt + JSON，round-trip 零丢失）+ 宏系统 + **lorebook/world info** 关键词触发引擎（挂 `PromptInjectionTransformer` 管线）
  - **宏 v1 清单（写死进测试）**：`{{user}}/{{char}}`（会话角色名）、`{{random:a|b}}`、`{{roll:1d6}}`、`{{lastMessage}}`、`{{time}}`；
    未识别宏**保留原文**；展开时机 = 系统提示 / lorebook 内容 / 首条消息组装时统一处理（独立于 Pebble 模板，不混用）
  - **lorebook 条目字段**：触发键（AND/OR/NOT/正则）+ 内容 + 插入位置（角色卡前/后、@深度）+ 递归开关 + 概率 + 优先级；
    上下文装配时按当前对话扫描触发；递归/概率行为对照酒馆语义（≥15 条对照测试）
- **L2**：ST 聊天 JSON 导入/导出（swipe→`MessageNode` 分支映射）、preset/instruct 模板（提示顺序/系统提示格式）
- 模块：`AssistantImporter.kt`（V2/V3 双向化）、新增 `core/data/ai/lorebook/`、`ExportSerializer.kt`、PNG 元数据写（`ImageUtils` 已有读）
- 红线：**兼容子集 = chara_card_v2/v3 全字段 + 上述 lorebook 字段 + 宏清单 + ST 聊天 JSON v1**（写死进测试；超集字段只透传不解释）；不碰酒馆 prompt 模板/扩展体系；**L5（对接用户自建 ST 服务器）明确不做**；L3 卡片市聚合归 B2 市场、L4 群聊语义归 C1
- 验收：20 卡 round-trip 零丢失（自动化 diff）；PNG 可被酒馆本体识别；lorebook 对照 ≥15 条；聊天导出可被酒馆打开；「带工具的酒馆角色」完成设备任务演示

### B1 可视化工作流编辑器（P1-1）

- `feature/workflow/`（Compose Canvas 节点编辑器）+ Room（workflows/runs/steps）+ 执行引擎扩 `FlowSpec`（`khatkit__run_flow` 后端）
  - **执行后端口径**：`FlowSpec` 是既有数据结构（`FlowSpecTest` 已有），可视化编辑器**产出 FlowSpec JSON**，执行仍走现有 flow 引擎——不写第二套执行器
- 节点 v1：trigger/llm/card_tool/condition/loop/delay/data_extract/http/notify/sub_flow；触发器节点直接消费 `TriggerEngine` 18 事件源（不重复实现调度）
  - 各节点语义/字段以总路线 P1-1 与 `docs/flow.md` 为准；节点输出统一进运行日志（每节点记录输入 JSON、输出 JSON、耗时、状态）
- 三态互转（可视化 JSON ⇄ 自然语言 ⇄ 卡片脚本）：
  - **规范形态 = 可视化 JSON**（另两态由它导出/导回）；自然语言 ⇄ JSON 走既有 GenerationLoop 生成 `FlowSpec`
  - 等价性测试口径：同一触发输入分别跑「可视化流程」与「导出的卡片脚本」，断言各节点输出序列一致（≥3 例）
- 运行日志走既有 Web API SSE（本机内嵌 Ktor `WebApiModule` events，不涉 Hub）；支持取消、失败重试、次数/耗时/成本统计
- 验收：UI 搭通「通知→分类→分支→回复」；导出脚本等价性 ≥3 例；日志可逐步展开每节点输入/输出

### B2 ToolPkg 运行时 + 市场 UI（P1-2 客户端侧）

- **依赖服务端 S1–S4**（协议/发布面/审计字段/兼容过滤）；本地先行部分：
  - ToolPkg 载体：扩展 `khatkit/.../dependency/` 的 DexClassLoader 机制承载 manifest+dex/脚本+资源；sha256 校验沿用
  - 运行时 hooks：`pre_tool/post_tool/pre_stream/post_stream/on_error`（`GenerationLoop`、工具执行点、`ProviderManager` 预留）
    - **能力边界（每个 hook 只能做这些）**：
      | hook | 触发点 | 可做 | 不可做 |
      |---|---|---|---|
      | `pre_tool` | 工具执行前 | 改参数、拒绝执行（返回 block + 原因） | 改工具名、直接执行工具 |
      | `post_tool` | 工具返回后 | 改结果、追加内容、标记结果不可信 | 删除原始结果字段 |
      | `pre_stream` | 请求发出前 | 改消息体/插入提示、追加请求头（域名须在 `network.allow`） | 换 Provider/模型 |
      | `post_stream` | 流式增量 | 改写/丢弃增量（敏感词等） | 中断生成流程 |
      | `on_error` | 任何异常 | 吞掉错误转为提示文案 | 隐藏审批/权限错误（安全错误不可被 hook 吞） |
    - 执行约束：hook = 插件脚本（Lua/JS 沙箱，dex 需审批）；复用 `RunDeadline` 有超时；**hook 内不得再触发 hook**（嵌套深度 1，防死循环）；
      设置页提供**全局禁用开关**，禁用后所有插件 hooks 不执行（审批模型覆盖）
  - **Provider 插件**：模型 Provider 热安装进 `ProviderManager`
    - 接口 = manifest 声明 `provider: { auth_scheme, base_url, header_builder, request_rewrite? }` 数据式描述（宿主按声明构造请求），
      或实现 `ProviderAdapter` 接口（鉴权签名/请求改写/响应解析，走 DexClassLoader + sha256 + 审批）
    - 示例场景：自定义鉴权兼容端点（如第三方 OpenAI 兼容站的特殊 header 签名）
    - 网络域名必须列在 manifest `network.allow`；安装属 elevated 权限，进三级审批
  - 市场 UI：`feature/explore/` 统一 kind 索引/安装/卸载/更新；安装前展示**权限审计报告**（`card-validator` 扩展静态校验产出，规则版本 `audit_rules_version`）
  - **插件结构建议（怎么写）**：插件 = **带生命周期的卡片**，最大化复用既有基建——
    ① `manifest.json` 声明式：id/version/entry/permissions/network.allow/hooks/settings_schema，策略由宿主执行，
    插件不自带权限；② 载荷分层：纯 Lua/JS 脚本优先（跑卡片同款沙箱引擎），仅需系统能力（自定义鉴权 Provider、
    native 库）才带 dex（DexClassLoader + sha256 + 审批），禁止内嵌 UI 代码；③ 能力一律走 `BridgeRegistry`
    既有桥（media/browser/workspace…），与卡片同审批面；④ 生命周期五类 hooks + `on_config_change`；
    ⑤ 配置读 `plugin.config(id)` 只读桥，写仅宿主。**不新造插件框架，插件与卡片的差别只在「有 hooks、有配置面、可带 dex」**
  - **插件配置面（拓展管理 → 插件页，纯本地先行）**：
    - 「探索市场 · 已安装」为拓展管理视图；**入口与跳转页面均由插件声明决定**：manifest 新增 `settings_schema`，结构示例：
      ```json
      { "entries": [ { "id": "conn", "label": "连接设置",
          "page": { "type": "Section", "title": "端点", "children": [
            { "type": "TextField", "id": "base_url", "label": "Base URL", "required": true },
            { "type": "TextField", "id": "api_key",  "label": "密钥", "secret": true },
            { "type": "Switch",    "id": "verbose", "label": "详细日志", "value": false } ] } } ] }
      ```
      - 每个 entry 渲染一个 `KedgeOptionItem`（label / 权限徽标 / 版本），点击跳 `Screen.PluginDetail(pluginId, entryId)`；
        **无声明则不出现入口，不做「安装即展示」的默认行**（卸载/详情走市场卡片既有流程）
      - 单入口可简写为一棵 page 树；字段 id 全局唯一（validator 装前校验，冲突即拒）
      - schema 与页面复用 D0 NodeTree **字段/展示子集**（TextField/NumberField/Switch/Checkbox/Select/Slider/
        RadioGroup/FilePicker/DirPicker + Section/Text/Markdown/Badge），**不含 action Button**（v1 不开放插件页面发事件）；
        页面含字段时宿主追加「保存/重置」底栏
    - **页面本体 = 插件声明的 NodeTree 节点树**：分组、字段、说明文案由插件自己排布；复用 D0 解析器与渲染器（详见上条子集清单）
    - **页面零 nav，导航全走宿主 nav3**：插件页作为 destination 压入宿主 `NavViewModel` 导航栈
      （`Screen.PluginDetail(pluginId, entryId)`），标题/返回/转场由宿主 nav3 统一提供；NodeTree 页面
      及一切 Lua UI **不含任何导航结构**（无自带导航栏、无路由/跳转 API），与现有卡片 `ui.form/ui.screen`
      模态 sheet 同原则——页面只是内容体，「往哪跳」的决定权只在宿主
    - 宿主固定壳（插件不可删改）：顶栏导航 + 安全信息头（版本、来源、sha256、权限清单、审计报告入口）+
      操作区（启用/停用、卸载、清空配置）——安全信息必须由宿主呈现，插件页面不得遮蔽
    - 配置存储：按插件命名空间落 DataStore（`plugin_prefs:<entryId>`）；`secret = true` 字段加密落库并在 UI 打码；
      保存时按字段 schema 校验（类型/范围/必填），未知键丢弃、缺键补默认值
    - 脚本侧只读访问桥（Lua/JS 一致）：`plugin.config()` 返回全部值 JSON（`{entryId: {fieldId: value}}`），
      `plugin.config("field_id")` 返回单字段值（字段 id 全局唯一保证无歧义）；**写入仅限宿主 UI**；
      保存成功触发 `on_config_change` hook，插件据此热更新运行参数（如自定义鉴权端点、轮询间隔）
    - 反方案（v1 明确不做）：允许 dex 内嵌 Compose 配置 UI——任意宿主进程 UI 代码绕过审批与风格体系，
      页面一律由宿主按声明的 NodeTree 渲染
- 功能开关：**本地设置项 `Settings.featureFlags.marketNewKinds`（默认 false，无远端下发）**——服务端 S1–S4 上线后由用户/发版开启；
  kind=card 主流程不受影响；插件配置面为纯本地功能，不受开关影响
- 验收（服务端就绪后）：≥6 种 kind 全流程；Provider 插件热安装生效；3 个样例 ToolPkg 审计标注准确

### B3 Key 池 + 任务级模型路由（P1-3）

- `ModelBinding(taskType, candidates[], policy)`：chat/记忆抽取/摘要/标题/OCR/翻译/UI 控制 **7 个消费点**各自候选与 policy
  - `taskType` 枚举即上述 7 类；candidates 有序（`ModelRouteCandidate(providerId, modelId, priority)`，已有 `ModelTaskRouter`）
  - **policy ∈ `cost_first / quality_first / latency_first`**（沿用总路线枚举）：按候选在该维度的排序权重选第一个未熔断者
  - 消费点接线：GenerationLoop（chat）、MemoryExtractor、标题/摘要/翻译/OCR 管线——**每处必须真正调用 `taskBinding()`，不接线不算完成**
- Key 池数据模型：每 Provider 支持多 key（`ProviderAccount{apiKey, priority, weight}`），池内**优先级分组 + 组内 round-robin** 轮换
- 熔断：429/5xx 记失败 → 该 key 进入**指数退避 5s→10s→20s→…上限 5min**，冷却期内路由跳过；恢复 = 退避到期后**半开探测 1 次**，成功即回池
- 预算：日/月双限（Token 与费用估算，价格表本地可配 JSON）；超限行为——**先降级到 candidates 中最便宜者 → 无便宜候选则只读模式**（拒绝生成并提示）；恢复 = 次日/次月额度重置或手动重置
- 埋点：每任务记录实际用模型/耗时/Token，`feature/stats/`（现有 StatsPage）按 key/任务维度展示
- **纯客户端**，不调用 Hub 新接口
- 验收：429 自动切换 key/候选模型对话无感；记忆与聊天模型分离**单测覆盖路由决策**；熔断可触发可恢复（单测模拟 429→退避→半开→恢复）

### C1 多角色群聊（P2-1，依赖 A2）

- 群聊消息带 `role_id`，按角色视角过滤上下文；协作模式 v1：pipeline/roundtable/vote，每轮 Token 预算
  - **视角过滤规则（prompt 组装层）**：每个角色可见 = 自己发出的消息 + 用户消息 + **@自己**的消息 + 轮次摘要；
    其余角色的对话默认不可见（这是「不串上下文」的实现口径）
  - **@ 提及**：输入框 @ 从成员列表选角色，目标 `role_id` 记入消息；未 @ 具体角色时按当前协作模式路由
  - **协作模式语义**：
    | 模式 | 行为 |
    |---|---|
    | `pipeline` | 成员按 `group_config.roles[]` 顺序接力，上一角色输出作为下一角色输入 |
    | `roundtable` | 每人发言一轮后，由指定议长角色汇总 |
    | `vote` | 各角色对候选选项投票，多数决输出 |
  - **每轮 Token 预算**（`group_config` 配置）：超限即终止本轮剩余角色发言，已生成内容保留，下一轮重置
- **与会话系统共存（不二选一，也无模式切换）**：群聊 = `Conversation.type = GROUP` 的一种会话，
  与单聊**同库同列表同管线**——消息仍存 `MessageNode`（追加 `role_id`），sync/备份/搜索/Tavern 导出全复用；
  会话列表统一混排（群条目 = 成员头像组 +「群」徽标），顶部仅提供**类型筛选 chip（全部/单聊/群聊）**——
  筛选是过滤不是互斥模式，任一侧都不隐藏另一侧；打开后视图分流：单聊走现有 `ChatPage`，群聊走 `GroupChatPage`，
  二者共享同一份消息数据，角色视角过滤发生在 prompt 组装层（transformer 按当前视角裁剪上下文），**不落第二套存储**；
  群配置（成员 `roles[]`、协作模式、每轮 Token 预算）挂 `Conversation.group_config`；独立记忆复用 A2
  `memory_spaces` 按「群 × 角色」开空间；存量会话 `type` 默认 `DIRECT`，零迁移
- Tavern 导出 + QR 分享补齐；备份走既有 sync 管线
  - **QR 内容** = 群配置 JSON（成员角色卡数据 + 协作模式 + 预算），扫码导入即建同构群聊；导出复用 Tavern 角色卡格式
- 验收：3 角色 @ 提及不串上下文；独立记忆空间生效；导出/恢复可用；同一列表中群聊与单聊混排/筛选互切不丢数据

#### C1 实施契约（先定数据与边界，再写 UI）

> 2026-10-04 进度与缺口写在 `beyond-operit-implementation-status.md` 的「C1 交给下一位」。
> 验收证据只认 `docs/eval/c1-group-chat.md`。那里仍全部是 `unverified`。

- **持久化模型**：复用 `Conversation`、`MessageNode` 与现有 sync/搜索管线；仅新增 `Conversation.type = GROUP`、`Conversation.group_config`（版本化 JSON）以及消息 `role_id`、`mention_role_ids`、`round_id`、`turn_kind` 字段。`type` 缺失或未知值按 `DIRECT` 读取，旧数据库零迁移；群配置未知字段必须保留，导出/恢复不得丢失。
- **群配置最小 schema（v1）**：`roles[]`（稳定 `role_id`、assistant/角色卡引用、显示名、模型绑定、记忆空间 id、工具包/Skill/MCP 绑定）、`mode`（仅 `pipeline|roundtable|vote`）、`chair_role_id`（roundtable 必填）、`token_budget_per_round`（正整数上限）、`revision`。保存时校验角色 id 唯一、引用存在、预算范围；非法配置拒绝写入并返回字段级错误。
- **上下文过滤必须发生在 prompt 组装层**：`buildContext(viewerRoleId, conversationId)` 只返回该角色自己发送的消息、用户消息、`mention_role_ids` 包含自己的消息和轮次摘要；工具调用、检索与记忆注入均使用同一 viewer 过滤结果，禁止在 UI 层“隐藏但仍发送”。过滤器做 JVM 纯函数测试，断言三角色任意组合下不存在越权消息。
- **路由与失败语义**：显式 `@角色` 只投递到被提及角色；无 @ 时按协作模式展开；pipeline 按配置顺序串联上一角色输出，roundtable 全员完成后仅议长汇总，vote 只接受结构化候选/票并按多数决输出。单角色失败记录错误节点并停止该轮（不伪造回复）；已完成角色输出保留，可从失败角色续跑；取消/超时不得写入未生成的消息。
- **预算口径**：每轮累计 prompt + completion token，达到 `token_budget_per_round` 后停止剩余角色；超预算原因、已用/上限与未运行角色写入运行日志。下一轮重新计数，不挪用其他轮预算；预算为 0、负数或超过宿主上限时配置保存失败。
- **记忆隔离**：记忆空间键固定为 `group:<conversationId>:role:<roleId>`，首次发言懒创建；不得回退到全局/助手空间。群消息写入记忆时带 `source_message_id` 与 `role_id`，检索结果再次经过 viewer 过滤。
- **并发与幂等**：同一群同一 `round_id` 只允许一个运行实例（持久化 run token + mutex）；重试使用同一 `round_id` 并跳过已提交 turn，避免重复消息。不同群可并行，不同角色不得共享可变 prompt buffer。
- **导出/恢复**：Tavern 导出保留成员角色卡、群配置、`role_id`/轮次/分支；QR 仅携带群配置与角色卡最小元数据，不携带密钥、隐私记忆或工具授权 token。导入先 schema 校验与去重，再创建新 conversation；恢复失败不留下半成品会话。
- **验收证据（写入 `docs/eval/c1-group-chat.md`）**：至少 3 角色、3 种模式、显式/隐式路由、预算截断、取消/重试、记忆隔离、Tavern/QR 往返、单聊/群聊筛选共 10 个用例；每例保存输入、各角色可见消息集合、实际模型调用序列、token 计数和导出哈希。未有真机或自动化证据的条目保持未完成，不得仅凭 UI 截图勾选。

#### C1 执行分包与交付闸门

C1 只能在 A2 的空间键、检索过滤和 `source_message_id` 证据通过后开工；不得用临时
全局记忆或第二套消息存储“先把 UI 做出来”。实现按以下顺序拆包，每个子包都必须
单独可回滚、补测试并在包末执行 `./gradlew --offline assembleDebug test lint`：

| 子包 | 主要改动/边界 | 必须先有的证据 | 交付证据 |
|---|---|---|---|
| C1-D 数据与迁移 | `Conversation` 的 `GROUP`/`group_config`、消息字段、角色关联表；未知字段保留，旧行按 `DIRECT` 读取 | Room schema/export 现状 | migration/round-trip 单测；旧库打开不崩 |
| C1-R 视角与协作内核 | `buildContext(viewerRoleId, …)`、三种模式、显式 @ 路由、预算/取消/重试/幂等；不新增执行器 | C1-D；A2 `memory_spaces` | 纯 JVM 测试覆盖可见集合、调用序列、token 截断和失败续跑 |
| C1-M 记忆与工具接线 | 固定 `group:<conversationId>:role:<roleId>` 空间；检索、工具调用、记忆注入统一走 viewer 过滤 | C1-R；A2 检索 API | 越权消息断言；三角色各自写入/检索隔离 |
| C1-U 会话 UI | 同一会话列表混排；类型筛选 chip；成员/颜色、@ 选择器、模式与预算设置；群页复用消息管线 | C1-R | Compose/UI 测试 + 真机手动记录；无第二套会话列表 |
| C1-X 导出与恢复 | Tavern 导出、QR 配置导入、sync/备份；导入先校验去重，失败无半成品 | C1-D；A5 角色卡格式 | JSON/PNG/QR 哈希往返；敏感 token/记忆不出包 |
| C1-E 验收集 | 建立 `docs/eval/c1-group-chat.md` 的 10 用例及原始结果（输入、可见集合、模型序列、token、导出哈希） | C1-D/R/M/U/X | 自动化与真机证据逐条勾选；缺证据保持未完成 |

**代码边界（领取任务时写入变更说明）**：数据层只负责版本化序列化与约束校验；
协作内核只接收不可变消息快照并返回运行日志；UI 不做上下文裁剪、不决定路由；导出器
不得读取密钥、隐私记忆或工具授权 token。任何跨包改动须在任务说明中列出原因与回滚点。

**运行与安全闸门**：同一 `conversationId + roundId` 的 run token 必须持久化后才可执行，
重试沿用原 round 并跳过已提交 turn；取消/超时只写运行日志，不写虚构消息。未知
`mode`、重复 `role_id`、非法预算、缺失议长引用一律拒绝保存；读取未知版本按兼容
字段保留策略处理，不静默丢弃。任何失败不得回退到全局记忆空间。

**验收记录格式**：每个子包在 `docs/eval/c1-group-chat.md` 增加一行，至少包含
`commit`、测试命令及退出码、设备/Android 版本（如适用）、用例输入、各 viewer 的
可见消息 ID、实际模型调用序列、prompt+completion token、导出 SHA-256。测试未运行、
只看截图或只看 UI 状态均标记 `unverified`，不得勾选完成。

**开工/收尾硬约束**：C4 仍排最后；C1 交付前不得开始 C4。每包必须离线
`assembleDebug test lint` 全绿；B2 的 `marketNewKinds` 默认关闭，服务端 S1–S4
未上线前不得把新 `kind` 入口暴露给用户。C1 不依赖 B2/S1–S4，但若复用 ToolPkg/
Skill/MCP 绑定，只保存引用并在未上线时隐藏不可用入口。

### C2 工作区开发闭环（P2-2）

- 项目模板（`assets/workspace_templates/`：Web/Vite、Node、Python、Kotlin-Android、Flutter 骨架 + Agent 项目规则文件）
  - 模板 = 目录骨架 + 初始化脚本 + 工作区版 `AGENTS.md`（Agent 读取项目约定）
- 实时预览：proot 内起 dev server → 本机 WebView
  - **端口发现口径**：`workspace_preview` 返回 `preview_url`（约定 `127.0.0.1:<port>`）；端口取自 dev server 启动日志
    正则 `listening on|Local:.*(\d{4,5})`，取不到时回退扫描工作区 `*.port` 文件；失败返回日志尾部 2KB
- 改动追踪：内容寻址 snapshot + `workspace_diff/revert`（聊天重发回滚）
  - **快照时机**：`workspace_write_file / workspace_edit_file / workspace_shell` 写操作**成功前**自动打点
    （文件路径 → 内容 sha256 存内容寻址库）；`workspace_revert [path]` 回到最近打点；diff 为工作区态 vs 最近打点的统一 diff
- SSH/SFTP 工作区后端：选库先做许可评估——**候选 sshj（Apache-2.0）优先，JSch（已停更）不用**，评估结论入 `docs/`；
  SAF 的 `WorkspaceDocumentsProvider` 保留
- `workspace_package`（debug APK 签名 + HTML zip 两个最小闭环），产物落工作区 `dist/`
- 工具面新增：`workspace_preview/diff/revert/package`
- 验收：模板→改码→预览→回滚全程工具驱动；diff/回滚单测含路径穿越防护；SSH 工作区可读写且被工具访问

### C3 语音 Agent + 入口形态（P2-3）

- 唤醒：Sherpa 关键词检测 → 前台服务常驻 → VAD → 流式 ASR
  - 唤醒词可配置（默认词产品定稿，模型随依赖包下发）；误触统计方式 = 真机静音环境放置 1 小时计数
  - **全双工打断语义**：TTS 播放中检测用户开口（**能量门限 + ASR partial 双条件**）→ 停播 + 取消剩余合成 +
    **已生成未播文本保留为输入框草稿**；打断延迟 = 从用户开口到 TTS 停播 <500ms
- 入口：`ACTION_ASSIST` 悬浮面板（manifest 加 intent-filter）、Widget（最近会话快捷输入）、气泡、悬浮球取词
  - **悬浮球取词语义**：复用 `AutomationOverlayService` 模式——球体拖到目标文本/屏幕 OCR → 文本进当前输入框
- 本地 ONNX VITS 资源包：走依赖包分发（服务端文档 S5 路线 A，不新增协议）
- 验收：唤醒误触 <1 次/小时（真机自测）+ 保活清单文档（**产出 `docs/voice-keepalive.md`**：厂商后台白名单/前台服务/通知渠道逐项列）；ASSIST 打开到可输入 <1s（真机计时）；打断 <500ms（录屏逐帧或日志时间戳断言）

### C4 虚拟形象运行时（P3-1）——⛔ 排最后实现（其余包验收后开工）

- `avatar/` 模块（glTF 起步）：状态机 idle/listening/thinking/speaking/error；口型用音量包络；视线跟随；表情订阅 `GenerationLoop` 事件总线
  - 渲染器选型（Filament / 自研 GL）开工时定并记入 `docs/`；事件总线事件名沿用 `generation.start / tool.exec / generation.error / tts.level`
  - 口型 = TTS PCM 音量包络（无需音素级）；视线跟随手指/滚动
- 形象资源包走依赖包机制（路线 A）；桌面悬浮宠物 = `TYPE_APPLICATION_OVERLAY` 悬浮窗（复用 AutomationOverlay 窗口管理）
- 验收：骁龙 7 系 60fps（帧率打点）；资源包市场安装/卸载

### D0 卡片 UI DSL（附带项，与 B2 相邻但独立）

- 规格与任务清单见 `/home/heizige/文档/Android/KhatKitCards/UI_DSL_UPGRADE.md`；
  逐卡实施记录见同目录 `MIGRATION_PROGRESS.md`（T1–T8 任务、12 卡迁移、验证记录）——
  规格中提到的 `UI_DSL_CLIENT_CHANGES.md` 拆分文档**未单独创建**，其内容由 `MIGRATION_PROGRESS.md` 覆盖
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
并行组1（无依赖）: A1 浏览器 | A2 记忆 | A4 视觉GUI | A5 酒馆 | B1 工作流 | B3 路由 | C2 工作区 | C3 语音 | D0 卡片UI
                  （A2 完成 → C1 群聊；A5 的 L4 群聊语义归 C1）
                  （A1/A2/A4 稳定 → B2 本地运行时先行；A5 的 L3 卡片市归 B2）
服务端组（独立进程）: S1→S2→S3→S4（见服务端文档）
B2 收尾: 服务端就绪 → 打开市场新 kind 开关 → 内容上架联调
C4 形象（P3-1/G13）: ⛔ 排最后——其余全部包（含 C1–C3）完成验收后再开工（用户决定 2026-10-03）
里程碑: A 组全齐 = 核心 Agent 能力达标；B/C 组齐 = 全面超越（总路线 §5）
（原 A3 端侧推理已裁剪，不在图中）
```

建议节奏（与总路线 §5 一致）：P0 包（A1/A2/A4）+ A5 优先 → P1（B1–B3）→ P2（C1–C3）→ **C4 最后**；B2 跟随服务端节奏，不卡其他包。

---

## 5. 验收（客户端可度量部分，总路线 §6 对齐）

- [ ] 自建 20 任务评测集（浏览器 5/文件 5/设备自动化 5/开发 5）端到端成功率 ≥ 基线 +10pp（评测脚本入 `docs/eval/`）
  - **基线定义**：同一批任务在同一台真机上跑「改动前版本」（评测脚本 `--baseline` 分支跑旧 APK）的成功率；
    pp = 百分点；两组各跑 1 次记录，失败任务附日志
- [ ] 酒馆生态：20 卡 round-trip 零丢失 + lorebook 对照 ≥15 条 + 「带工具的酒馆角色」演示（总路线 §6 指标）
- [ ] 记忆检索命中 ≥80%（top-5 口径，见 A2 节）且 100% 可溯源（脚本断言）
- [ ] 工具审批三级 + 轨迹审计（B2 的市场审计报告待服务端就绪后补）
  - **三级定义（沿用 `ToolApprovalState`）**：L0 自动放行 / L1 每次询问 / L2 会话内授权；
    轨迹审计 = AutomationTracer JSONL 可回放、权限变更与工具调用可追溯
- [ ] 每包交付跑 `./gradlew test lint` 全绿 + 对应验收标准逐条勾选（格式沿用总路线 §8 任务领取模板）
- [ ] 全程未修改 Hub 协议客户端调用面（`HubClient` 除 B2 市场读写新 kind 外 diff 为空）
