# beyond-operit 升级 — 需要动服务器的改动（KhatKitHub 后端）

> **范围**：本文档只收录**必须改 KhatKitHub 服务端**（`heizige.top`，现协议见 `docs/hub-backend.md`）的变更，服务于 `docs/beyond-operit-upgrade.md`（下称总路线）中的差距项，重点是 **P1-2 统一扩展市场**（差距 G7/G8）与资源分发类需求。
>
> **纯客户端改动**（P0-1 浏览器、P0-2 记忆、P0-4 视觉 GUI、P1-1 工作流、P1-4 酒馆兼容、P1-3 路由、P2 全部、P3-1 运行时等；原 P0-3 端侧推理已裁剪）见同目录 `beyond-operit-client-changes.md`。两份文档均以总路线为规格基线，冲突时以总路线为准。
>
> **边界原则**：Hub 现有能力（激活/套餐、AI 网关、工具计量、卡片发布/fork/投票、自更新通道）保持兼容不破坏；本组改动全部是**新增协议面**或**新增元数据**，旧客户端零感。

---

## 1. 为什么总路线里只有这些要动服务器

总路线差距项（G1–G14）绝大多数是端上能力（浏览器 Agent、记忆、视觉自动化、工作流、语音、群聊、酒馆兼容……），KhatKitHub 只在**分发与账户**层面介入。真正需要服务端扩展的只有：

1. **G7 统一扩展市场**：现 Hub 只有「卡片」一种条目 + 依赖包；要覆盖 Skill/MCP 配置/prompt/工作流/依赖包/**模型 Provider 插件**，必须扩协议、扩存储、扩审核
2. **G8 ToolPkg 运行时**：ToolPkg（多文件包 + 依赖声明 + api_version）需要新的发布/校验/版本约束面
3. **G13/资源类**：虚拟形象资源包、语音 TTS 资源包的市场分发（也可全部走既有依赖包机制，见 S5 决策点）
4. **兼容性元数据**：以上新条目需要 `min_app_version / api_version` 约束（否则旧客户端装新条目即坏），与卡片 UI 升级的兼容需求同构

---

## 2. 服务端改动清单

### S1 统一市场条目协议（P1-2 服务端主项）

| 层 | 改动 |
|---|---|
| 存储 | **已定稿：新表 `items`（+ `series` 分组表、`item_votes` 投票表），不动旧卡片表**；`kind ∈ card \| skill \| mcp \| prompt \| workflow \| dependency \| provider_plugin`；统一字段 `id/name/kind/version/display_name/description/author/license/pricing/changelog/sha256/manifest_json/files/created_at/status`；旧卡片以 `kind=card` **只读适配**进新协议（发布仍可走旧路由） |
| `display_name` 结构 | 本地化 map：`{"zh-CN": "名称", "en": "Name"}`（JSON）；缺该键时列表/详情回退 `name`；发布必填至少一个语言 |
| 发布 | **已定稿：双路由并存**——旧 `POST /api/cards/publish` 原样保留（仅 kind=card 旧协议），新增 `POST /api/items/publish` 承接全部 kind（`kind` 必填，非 card 走新路由） |
| 列表/详情 | `GET /api/items?kind=&q=&app_version=&page=`；旧 `GET /api/cards` **保留不动**（等价 `kind=card` 子集），旧客户端继续可用 |
| 检索 | 统一搜索（名称/描述/标签），沿用现 `/api/cards` 懒加载索引设计；`q` 为空返回默认排序（时间/优选） |
| 下载 | 多文件条目（ToolPkg 为 ZIP）`GET /api/items/{id}/download` + sha256 校验；单文件条目沿用现流程；ZIP 内路径限制（禁 `..`/绝对路径）与内容白名单 |
| 投票/优选 | **已定稿：全 kind 支持**（复用 `item_votes`，幂等投票 + 按 kind 分组优选）；旧 `/api/cards/{id}/vote` 语义不变 |

### S2 ToolPkg 发布与依赖（P1-2 服务端）

| 项 | 改动 |
|---|---|
| 归档上传 | `.toolpkg`（ZIP：manifest + main.js + packages/ + ui/ + modules/ + resources/）整体上传 |
| manifest 必填字段 | `toolpkg_id / schema_version / version / display_name / api_version / entry / requires[] / permissions[] / hooks[]`；服务端解析校验——缺项拒收，`toolpkg_id` 与发布者已有条目查重 |
| 大小限制 | ZIP ≤ 50MB（超限 413；与普通卡片包上限分开定义，写入 `hub-backend.md`） |
| `api_version` | manifest 声明的 **ToolPkg API 版本**（宿主运行时 API，如 `1.0.0`）落库并在列表/详情返回；市场版本信息以此为准，不允许发布后手改 |
| `requires` 依赖 | manifest 的包依赖（id + min/max version）落库；安装解析时校验依赖存在与版本范围（解析可在客户端做，服务端至少提供依赖元数据） |
| 发布压缩 | 若支持 JS AST 压缩（可选），在服务端发布流水线做；保持 ZIP 结构不变 |
| 审核与发布权限 | 新 kind 进入 `pending` 审核流（复用现状态机）；发布权限与卡片一致（登录 + 审核）；ToolPkg 附加 manifest 结构校验 |

### S3 权限审计报告 / 沙箱评分（G7 超越点，市场展示依赖服务端）

| 项 | 改动 |
|---|---|
| 报告来源 | **已定稿：客户端/CI 生成、服务端存证展示**：`card-validator` 静态校验扩展到 ToolPkg 后产出「权限审计报告」JSON（申请权限、网络域、危险 API 调用点、hooks 清单），随发布上传 |
| 报告结构 | `{ audit_rules_version, score, findings: [{rule_id, severity, detail}], permissions[], network_domains[], hooks[] }` |
| `audit_score` 定义 | **0–100 整数 = 风险分（越高越危险）**，派生 `audit_level ∈ low(0–29) / medium(30–69) / high(70–100)`；详情页展示分数 + 等级徽标；`high` 客户端安装前强制二次确认 |
| 危险 API 规则 v1 清单 | ① `network.allow` 之外的外连域名 ② workspace 之外的文件读写路径 ③ 反射/动态类加载（dex 独有） ④ 无障碍/设备控制桥调用 ⑤ 日志/剪贴板读取——每条规则有稳定 `rule_id` |
| 存储与返回 | 条目加 `audit_report` 字段（JSON）+ `audit_score` + `audit_level`；列表/详情返回，市场页安装前展示 |
| 服务端校验（可选加强） | 审核流水线对新提交跑同款静态检查（与客户端 validator 共享规则版本号 `audit_rules_version`，SemVer）；客户端报告版本 < 服务端版本时标记 `stale` 但不拒收；规则不一致时以服务端为准并告警 |
| 报告时效 | 条目新版本必须重出报告；旧版本报告保留可查 |

### S4 版本兼容元数据（全 kind 通用）

| 项 | 改动 |
|---|---|
| `min_app_version` | 条目要求的最低宿主版本（semver，可空=不限）；发布接受、列表返回 |
| `api_version` | 仅 ToolPkg/插件类（见 S2）；普通卡片不需要 |
| 过滤 | `GET /api/items?app_version=` 服务端过滤掉不兼容条目；不传参数返回全部（旧客户端无感） |
| **过滤语义（精确口径）** | 返回条件 = `app_version >= min_app_version`（数字段 major/minor/patch 逐段比较）；`prerelease` 按 SemVer 规范 **`1.0.0-rc.1 < 1.0.0`**；`min_app_version` 非法值 → **发布时拒绝**，读取存量数据时视为不限制（宽松兜底，不 500）；相等版本可返回 |
| 展示 | 「需要 v2.x+」标注字段下发，UI 由客户端渲染（归客户端文档） |

### S5 资源包分发（P3-1 形象 / P2-3 TTS —— 决策点）

两种路线二选一（**默认路线 A**，不新增服务端工作）：

- **路线 A（推荐，零服务端改动）**：形象资源包、ONNX VITS TTS 资源包全部走**既有依赖包机制**分发（dex jar 通道或普通 dependency 条目 + sha256 签名，`docs/dependency-system.md` 已有），市场按 `kind=dependency` 上架
- **路线 B（新 kind）**：若要求「形象包」有独立市场分类/预览图/主题标签，则 S1 增加 `kind = resource`（带 `mime/preview` 字段）——仅在产品上确实需要独立品类时做

### S6 计量/定价扩展（可选，默认不做）

- ToolPkg 工具按次计价：`POST /api/tools/authorize/report` 已按 `card_name` 计量，泛化到 `item_name` 即可；**首期免费不计量**则零改动
- 任务级预算/配额（P1-3 的成本熔断）纯客户端实现（本地价格表 + 熔断），**不需要**服务端配合；若未来要云端账单聚合再立项

### S7 服务端文档

- `docs/hub-backend.md`：补 S1–S4 的协议（路由、字段、semver 过滤语义、审核规则、`audit_rules_version`）
- 若服务端仓库有独立 API 文档，同步

---

## 3. 明确不改的部分

| 接口/能力 | 结论 |
|---|---|
| `POST /api/activate` / `GET /api/me` / AI 网关 `/v1/*` | 不动 |
| `POST /api/tools/authorize` / `report` | 不动（S6 仅在启用计量时泛化名字） |
| `POST /api/cards/publish` / `fork` / `vote` 与 `GET /api/cards` | **保留兼容**；新能力走新路由/新字段，旧字段语义不变 |
| 自更新通道（stable/beta/force_min） | 不动 |
| 卡片包格式（card.json + script） | 不动；新 kind 用各自 manifest |
| 付费卡片 pricing 换算（元→分） | 不动 |

---

## 4. 上线顺序（与客户端的依赖）

```
① 服务端 S1 + S4 部署（新路由 + 兼容元数据，旧客户端无感）
② 服务端 S2（ToolPkg 发布面）+ S3（审计报告字段）
③ 服务端 S7 文档
④ 客户端 P1-2（市场 UI + ToolPkg 运行时 + hooks）发版     → 见客户端文档 **B2** 包
⑤ 各 kind 内容（Skill/MCP/prompt/工作流模板/ToolPkg）上架
⑥ 其余客户端任务（P0/P2/P3）不依赖上述节奏，可全程并行
```

**红线**：①② 未上线前，客户端市场 UI 不得把新 kind 的入口暴露给用户；工具计量与卡片市场主流程始终可用（失败开放原则沿用 `hub-backend.md`：网络异常不拦截本地卡片）。

---

## 5. 验收标准

- [ ] `kind=skill/mcp/prompt/workflow/dependency/provider_plugin` 各至少 1 个样例可发布/列表/详情/下载/安装（sha256 校验通过）
- [ ] 旧 `GET /api/cards`、发布/fork/投票全量回归无变化（旧客户端冒烟）
- [ ] ToolPkg 上传：manifest 校验、`api_version` 落库返回、`requires` 依赖元数据完整
- [ ] 审计报告随发布上传并在详情返回；新版本强制重出报告
- [ ] `app_version` 过滤：`min_app_version` 高于请求版本的条目不出现；不传参数行为不变
- [ ] semver 比较用例与非法值兜底（按 S4/总路线 §7 风险约定）
- [ ] `docs/hub-backend.md` 与实现一致

## 6. 风险与待确认

| 风险/决策点 | 处理 |
|---|---|
| S1 表结构：泛化旧表 vs 新表 | **已定稿：新表 `items` + `kind=card` 只读适配**，不动旧卡片表（本地实现即此方案） |
| 新 kind 是否做投票/优选 | **已定稿：做**（`item_votes` 全 kind 复用，幂等投票 + 分组优选）；产品可再裁剪 |
| S5 路线 A/B | 默认 A（零服务端改动）；需要独立「资源」品类再走 B |
| 审计报告由谁生成 | **已定稿：客户端/CI 生成 + 服务端存证**；服务端复检为增强项（见 S3） |
| 严格 schema 的发布接口拒绝未知字段 | S1 前置检查；必要时先发「忽略未知字段」补丁 |
| 付费 ToolPkg 计量 | 首期不做（S6 零改动）；启用时再泛化 authorize/report |
| ToolPkg ZIP 大小上限 | 默认 50MB（见 S2）；上线前按实际内容体积复核 |
