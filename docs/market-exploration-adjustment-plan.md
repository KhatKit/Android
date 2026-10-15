# 消息市场探索页调整方案

状态：实施基线（v2，已按两端代码逐条核对）
版本：v1「规划稿」2026-10-15 → v2「实施基线」2026-10-15
适用项目：

- Android 客户端：`/home/heizige/文档/Android/Project/KhatKit`（核对基线 commit `8495e7b95`，2026-10-14）
- 市场服务端：`/home/heizige/文档/IDEA/Project/KodeHeapServer`（核对基线 commit `b1b6d4c`，2026-10-14）

这份文档是两端共同的实施基线。实现时以服务端 API 契约、客户端安装器和市场页面验收为三个独立交付面。

**核对来源与标注约定**

- 判定与行号来自两项只读实勘：T1 = 服务端核对（KodeHeapServer 现状），T2 = 客户端核对（KhatKit 现状）。
- 行号相对各自仓库根；代码会漂移，行号用于定位，允许 ±少量行的偏差，`grep` 关键词才是稳定锚点。
- `已有` / `部分` / `缺失` 是本文件对每条要求在**两端当前代码**中的落地程度判定；`缺失` = 两端尚不存在，一律视为待做（任务见 §8），不得当作已具备。
- `路径/文件.kt:行` = 已存在的证据或改动落点；`new: 路径` = 需要新建的文件。
- 本文件只写契约与任务，不含业务代码变更（本轮不写代码）。

## 1. 背景与现状

客户端已经有统一的探索市场页面 `ExploreMarketPage`，可以展示卡片、技能合集、MCP 服务、合集包和模型能力。卡片通过 `KhatKitToolProvider` 安装，技能和 MCP 资源通过 `HubResourcePackage` 下载并写入本地设置。

当前实现有五个结构性问题（原文标题写「四个」但正文列了 5 条，此处已修正为五个，与正文编号一致）：

1. **资源条目只有语义图标名。** 判定：`部分`。服务端只有 `icon` 语义名（`src/model/KhatKitResourceDto.kt:23`），客户端把它映射成本地 `ImageVector`（`khatkit/src/main/java/heizige/kk/khatkit/hub/HubResourceModels.kt:26`；映射点 `app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt:2314` 的 `resourceIcon(entry.icon, entry.kind)`）。`icon_url` / `icon_sha256` / `icon_mime` / `icon_alt` 在两端都不存在（服务端 `src/model/KhatKitHubDto.kt:274-338` 无这些字段；客户端 `khatkit/src/main/java/heizige/kk/khatkit/hub/HubModels.kt:16-51` 的 `CardIndexEntry` 无这些字段）。
2. **中文硬编码与本地化形态不齐。** 判定：`部分`。服务端统一市场的 `display_name` 已是语言映射（`JsonElement`，`src/model/KhatKitHubDto.kt:279`；DB 列 `display_name text`，`src/table/KhatKitHubTables.kt:116`），但旧资源索引的 `name`（`src/model/KhatKitResourceDto.kt:18`）与 `description`（同文件 `:19`）仍是纯字符串；客户端探索页（`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt`，共 3623 行）中文硬编码 ≥174 行 / 约 201 处中文字面量（计数口径：含中文字面量的行 `grep -cP` 计 174；中文字面量 `grep -oP` 计 200，t4 独立复核 201，差异来自正则口径；未逐字符复核，含中文的注释行另有 489 行不计入）、`stringResource` 仅 10 处，`values/strings.xml` 只有 3 个 `explore_market_*` 键（T1/T2 实测；该文件共 1530 个 string 条目）。
3. **函数契约只有半条。** 判定：`部分`。带 UI 的卡片已有 `parameters` 和 `ui`（`card-validator/src/main/java/heizige/kk/khatkit/card/CardManifest.kt:36-39`，白名单校验 `card-validator/src/main/java/heizige/kk/khatkit/card/CardValidator.kt:347-355`）；用户手动运行与 AI Tool 已经走同一执行器（`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tools/KhatKitTools.kt:670`、`:1664-1678` → 同文件 `runCardWithStatus:426`）。缺的是**宿主侧 schema 驱动表单**：表单仍由脚本自弹 `ui.form`（`khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/KhatKitForm.kt:60`，调用点 `app/src/main/java/heizige/kk/khatkit/app/core/ui/components/khatkit/KhatKitUiHost.kt:143`）。
4. **MCP 安装即启用、无配置入口。** 判定：`缺失`。安装时直接 `enable = true` 并写入全局设置与当前助手（`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketViewModel.kt:236-263`、`:284-293`），市场页只有区块级入口跳设置页（`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt:791`），没有必填配置识别，也没有条目级预填。
5. **没有来源绑定。** 判定：`缺失`。服务端没有任何 GitHub 抓取或来源字段/接口（`src/model/KhatKitHubDto.kt:109-125` 的发布请求里没有 `source`；`src/routing/KhatKitHubRouting.kt:600-800` 没有 import/sync/webhook 路由；服务端全仓 `grep -rni github --include=*.kt src/` 命中 5 行，即全仓 5 处文案提及、无抓取代码：`src/service/KmdBackendService.kt:486`、`:504`、`:553`、`src/service/BuiltinResources.kt:72`、`:277`）。

补充的现状事实（影响后续改造切分）：

- 客户端探索页只有两个文件（`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt` 与 `ExploreMarketViewModel.kt`）。**不存在独立的 Miuix 探索页文件**：Miuix 是同一文件内的样式分支（`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt:388` 的 `isMiuixStyle`，同文件另外 17 处 Miuix 引用）。原文按独立页面描述的说法已按实测改写为「同文件两条样式分支」（见 §8 P0-4）。
- 客户端目前**没有任何** `/api/items` 消费者：`khatkit/src/main/java/heizige/kk/khatkit/hub/HubClient.kt` 只调用 `/api/khatkit/search`（:79）、`/api/khatkit/resources/index.json`（:99）、`/api/cards`（:243）。§7.1 的字段对客户端全部是新增解析。
- 服务端已有 Hub 条目、版本、投票、来源系列表与统一 `/api/items` 路由（`src/table/KhatKitHubTables.kt:112-156`、`src/routing/KhatKitHubRouting.kt:600-624`），本文件的扩展是**加列加表加路由**，不推翻现有结构。
- 客户端工作区有 3 个未提交文件（`ExploreMarketPage.kt` 视觉重构 345+/320-、`values/strings.xml`、`values-zh/strings.xml`）。T2 判定其与 P0 验收正交（P0 六条完成度 0；§8 现列 P0-1…P0-6）；P0-4 会改同一文件，建议先落这 3 个文件或在其之上继续，不要回退。

## 2. 目标与边界

### 2.1 目标

- 每个市场 item 具有独立、可缓存、可校验的云端 SVG 图标，并保留无图标时的稳定回退。
- 卡片、Skill、MCP、合集和 ToolPkg 都能用同一套多语言展示与搜索规则。
- 带 UI 的能力只有一个规范函数入口，AI 调用和用户填写表单最终走同一个执行器、授权和审计路径。
- MCP 条目在市场内明确区分“可直接连接”“需要配置”“配置后可连接”，并提供“配置”入口。
- 支持输入 GitHub 仓库地址导入并生成待审核版本；支持 webhook 或轮询发现更新，更新经过校验后再发布。
- 安装、更新、卸载、回滚、撤销和错误提示都有可追踪状态。

目标条目与现状的差距一律以 §3–§7 的判定为准，任务化拆分见 §8。

### 2.2 边界

- “自动上线”定义为：自动读取来源、生成候选版本、执行校验、进入审核队列，并在满足明确的可信策略时自动批准。默认不把任意仓库的最新提交直接推送给所有用户。
- MCP Registry 是元数据注册表，不是代码和二进制仓库。实际运行包仍来自 npm、PyPI、Docker、GitHub Release 或一个可访问的远程 MCP 地址。
- 首期不允许市场条目携带任意 APK、Dex 或宿主级权限；原生依赖继续走现有签名依赖通道。
- 首期不引入私有仓库抓取（需 GitHub App/OAuth 与组织授权，见 §10 决策 1）；公开仓库 + 可选 PAT 为唯一抓取面。

## 3. 页面调整

### 3.1 探索页信息架构

保留现有一级探索页和二级列表页，但统一所有条目的卡片组件。卡片分成三个区域：

| 区域 | 内容 |
| --- | --- |
| 身份区 | 云端 SVG 图标、条目名称、本地化类型标签、作者 |
| 能力区 | 一行摘要、标签、支持的语言/版本、权限和配置状态 |
| 操作区 | 安装/更新/已安装、配置、详情；危险操作进入详情页 |

卡片操作根据条目类型决定：

- 卡片：安装、更新、运行、卸载、查看权限。
- Skill：安装、更新、启用范围、查看文件。
- MCP：安装、配置、连接状态、启用范围。
- 合集：查看包含项、一次安装、逐项配置。
- 不兼容或被撤销的版本：显示原因和详情，不显示可执行安装按钮。

现状判定与改动落点：

| 要求 | 现状判定 | 落点 |
| --- | --- | --- |
| 统一卡片组件 | `部分`：已有统一卡壳 `PressableRow`（定义 `app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt:1744`，首次调用 `:1553`；T2 记为 `:1650`，经复核该行实为 `TextStyle.tabular()`、不是卡片壳，实测漂移）与基础行 `ResourceRow`（定义同文件 `:2306`），但行内只有图标+名称+一行简介，没有三区结构 | 在 `ExploreMarketPage.kt` 内抽出 `new: app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketCard.kt`（身份/能力/操作三区），`ResourceRow`、卡片行、合集行全部收敛到它 |
| 身份区云端品牌图标 + 作者 + 本地化类型标签 | `缺失`：图标来自 `extensionIcon(slug) + resourceIcon(entry.icon, entry.kind)`（`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt:2314`，T2 记为 :2318，同一 `ResourceRow` 内），作者字段在 UI 未展示 | 依赖 §4 的 `icon_url` 字段在客户端落地（`khatkit/src/main/java/heizige/kk/khatkit/hub/HubModels.kt:16-51`）；类型标签走 §3.3 本地化键；作者显示在 `ExploreMarketCard` 身份区（同 P0-3/P0-4） |
| 首屏只显示决策字段，完整元数据放详情页 | `部分`：二级列表/详情已存在（`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt:2306-2360` 的 `ResourceRow` 与分组列表），但 §7.1 的 source/compatibility/config_schema 客户端尚无模型 | 随 P0-3 解析新字段后，把来源提交、依赖、审计、更新记录放进详情页 |

### 3.2 MCP 配置入口

MCP 详情页增加主操作“配置”。入口统一跳转到现有 MCP 配置表单，但由市场详情预填：

- 服务器名称、传输类型、URL。
- 非敏感默认请求头。
- 配置字段及字段说明。
- 当前连接状态和最近一次错误。

现状判定与改动落点：

| 要求 | 现状判定 | 落点 |
| --- | --- | --- |
| 条目级“配置”入口 | `部分`：只有区块级动作跳 `Screen.SettingMcp`（`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt:791`、`:3548`、`:3555`），没有任何条目级预填 | `app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt:791` 附近改为条目级动作；`new: app/src/main/java/heizige/kk/khatkit/app/feature/explore/McpInstallConfigSheet.kt` 承载预填 |
| 复用现有配置表单 | `已有`（实名已按源码更正）：弹窗实名 **`McpServerConfigModal`**（`internal fun`，`app/src/main/java/heizige/kk/khatkit/app/feature/settings/mcp/McpConfigModal.kt:113`），调用点 `app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingMcpPage.kt:237-238` 与 `app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingMcpPageMiuix.kt:24`（import，实际调用同文件 `:94-95`）；`McpServerConfig`（`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/mcp/McpConfig.kt:63`）是数据模型，与弹窗函数 `McpServerConfigModal` 不是同一个东西（数据模型 ≠ 弹窗组件名） | 市场详情传参进 `McpConfigModal.kt`；不新建第二套表单 |
| 缺必填配置 → 显示“需要配置”，默认不自动启用 | `缺失`：安装分支直接 `McpCommonOptions(enable = true)` 并写当前助手（`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketViewModel.kt:284-293`；写全局与助手在 `:236-263`） | `app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketViewModel.kt:236-293` 改为：缺必填 → 只落 `McpServerConfig`、`enable = false`、不写 `assistant.mcpServers`；状态文案键进 `values/strings.xml` |

配置完成后返回市场详情，状态变为“已配置”或“连接失败”。安装动作只保存候选配置，不应在缺少必填密钥时伪装成“已连接”。用户可以选择“安装但暂不启用”。

MCP manifest 不保存用户秘密。API key、OAuth token 和自定义请求头的 secret 值只存客户端安全存储（现有 `khatkit/src/main/java/heizige/kk/khatkit/bridge/impl/SecretRepository.kt`），并在导出、日志、错误和市场详情中脱敏。现状判定：`部分`——secret 存储机制已存在，但市场详情侧的脱敏尚未接入（随 P0-5 一并验收）。

### 3.3 多语言显示规则

所有服务端可见文案采用以下结构之一：

```json
{
  "display_name": {
    "zh-CN": "深度研究",
    "en": "Deep Research"
  },
  "description": {
    "zh-CN": "联网检索和引用整理",
    "en": "Research and citation workflow"
  }
}
```

兼容旧数据时允许纯字符串。客户端按如下顺序选择：

1. 当前 `LocaleList` 的完整语言标签，例如 `zh-CN`。
2. 当前语言，例如 `zh`。
3. `en`。
4. 服务端默认值或第一个非空值。

现状判定与改动落点：

| 要求 | 现状判定 | 落点 |
| --- | --- | --- |
| 服务端文案支持语言映射 | `部分`：`display_name` 已是 `JsonElement`（`src/model/KhatKitHubDto.kt:279`、`:313`；DB `display_name text`，`src/table/KhatKitHubTables.kt:116`；映射 `src/service/KhatKitCommerceService.kt:1648`、`:1676`）；`description` 仍是 `String`（`src/model/KhatKitHubDto.kt:280`、`:314`），`tags` 仍由 `manifest_json` 派生（`src/service/KhatKitCommerceService.kt:965`、`:1449-1454`） | 服务端按 §7.1 扩展 `description` 与 `tags_json`；旧纯字符串继续可读 |
| 客户端按 4 级顺序选值 | `缺失`：`CardIndexEntry` 无 `display_name` 字段（`khatkit/src/main/java/heizige/kk/khatkit/hub/HubModels.kt:16-51`），客户端 Hub/市场路径 0 处消费（全仓 20 处都在非 Hub 路径：`app/src/main/java/heizige/kk/khatkit/app/core/data/db/entity/ManagedFileEntity.kt:22` 的 `@ColumnInfo("display_name")`、`values*/strings.xml` 的 `setting_provider_page_model_display_name` 键、`ai/src/main/java/heizige/kk/khatkit/ai/provider/providers/claude/ClaudeProvider.kt:270` 的模型响应解析；`grep -rn` 计 21 行） | `khatkit/src/main/java/heizige/kk/khatkit/hub/HubModels.kt` 新增字段；`new: khatkit/src/main/java/heizige/kk/khatkit/hub/LocaleMapResolver.kt`（纯函数：完整标签 → 语言 → `en` → 首个非空） |
| 页面文案全部进 `strings.xml` | `缺失`：`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt` 中文硬编码 ≥174 行 / 约 201 处中文字面量（口径同 §1 第 2 条，未逐字符复核）、`stringResource` 仅 10 处；`values/strings.xml` 只有 3 个 `explore_market_*` 键 | 键落到 `app/src/main/res/values/strings.xml` 与 `app/src/main/res/values-zh/strings.xml`（其余 locale 缺键时回退默认值）；复数用 `<plurals>`，价格/版本用格式化参数 |
| 搜索由服务端匹配所有本地化名称/描述/标签 | `部分`：服务端当前用 `name + displayName + description + tags` 直接字符串匹配（`src/service/KhatKitCommerceService.kt:895`） | 改为先展开 locale map 再匹配（同 §7.1 扩展），客户端只展示当前语言结果 |

## 4. 云端 SVG 图标

### 4.1 API 字段

在 `/api/items` 和旧资源索引中增加可选字段：

```json
{
  "icon_url": "https://cdn.example.com/market/icons/io.github.owner.tool/1.2.0.svg",
  "icon_sha256": "64 位小写 sha256",
  "icon_mime": "image/svg+xml",
  "icon_alt": {
    "zh-CN": "深度研究图标",
    "en": "Deep Research icon"
  }
}
```

保留现有 `icon` 语义名称作为兼容回退。新的客户端优先使用 `icon_url`，下载失败、校验失败或 SVG 无效时使用本地图标；禁止因图标失败阻塞列表加载和安装。

- 现状判定：`部分`（`icon` 语义名已有，四个新字段两端都没有）。
- 落点：服务端 DTO `src/model/KhatKitHubDto.kt` 的 `KhatKitHubItemSummary`（:274-301）与 `KhatKitHubItemDetail`（:308-338）各加 4 个可选字段；映射补在 `src/service/KhatKitCommerceService.kt:1643` 与 `:1671`；列补在 `src/table/KhatKitHubTables.kt:112-142`（建表路径 `src/service/KhatKitCommerceService.kt:218`）。旧索引侧同名可选字段补在 `src/model/KhatKitResourceDto.kt:15-33`。客户端字段补在 `khatkit/src/main/java/heizige/kk/khatkit/hub/HubModels.kt:16-51`。

### 4.2 服务端约束

- 只允许 HTTPS、同源 CDN 或配置的可信图标域名。
- 上传后服务端解析并清理 SVG，删除 `<script>`、事件属性、外部资源引用、动画和危险 URL；限制文件大小和节点数量。
- 服务端重新计算 `icon_sha256`，不能信任发布请求中的哈希。
- 图标随版本不可变；更新图标必须随新版本发布。
- CDN 使用长期缓存，URL 至少包含 item 名称和版本；撤销版本时客户端仍可使用本地缓存，但不再出现于市场。
- `icon_alt` 只用于无障碍，不把 SVG 内文本当作可翻译 UI。

现状判定：`缺失`（服务端只有 MIME 相关处理，`src/service/MediaService.kt:28`、`:45`；全仓无图标上传/托管/清理代码）。改动落点：

- `new: src/helper/SvgSanitizer.kt`（清理与节点/大小限制；纯函数，符合 `src/helper/` 现有命名风格，如 `src/helper/Http.kt`）
- `new: src/service/KhatKitIconService.kt`（URL 生成、哈希重算、域名白名单、落 `icon_*` 列）
- `new: src/routing/KhatKitIconRouting.kt`（上传与不可变下发路由；与 `src/routing/*Routing.kt` 风格一致）

### 4.3 客户端实现

- 在 KhatKit 的 Coil `ImageLoader` 中启用 SVG 解码器，限制最大字节数和并发下载数。
- 使用 `icon_url + icon_sha256` 作为缓存键；下载后校验哈希再写入磁盘。
- 不把远端 SVG 直接转成可执行 HTML，也不允许加载 SVG 内部外链。
- 列表使用固定图标容器尺寸，加载中显示占位，错误显示类型默认图标。
- 图标无障碍描述来自 `icon_alt` 或本地化条目名称，装饰性图标传空描述。

现状判定与改动落点：

| 要求 | 现状判定 | 落点 |
| --- | --- | --- |
| Coil 启用 SVG 解码 | `已有`：`app/src/main/java/heizige/kk/khatkit/app/KhatKitApp.kt:191` 的 `add(SvgDecoder.Factory(scaleToDensity = true))` | 无需改动 |
| `icon_url + icon_sha256` 缓存键、下载后校验、字节/并发上限 | `缺失`：`app/src/main/java/heizige/kk/khatkit/app/KhatKitApp.kt:181-193` 只有 crossfade + Ktor fetcher，无 cache key 与上限 | `app/src/main/java/heizige/kk/khatkit/app/KhatKitApp.kt:181-193` 加自定义 `Keyer`/限流；`new: khatkit/src/main/java/heizige/kk/khatkit/hub/HubIconFetcher.kt`（下载 → sha256 校验 → 落盘，校验失败返回 null 触发回退） |
| 固定图标容器 + 占位 + 类型默认图标回退 | `部分`：`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt:2314` 已有本地 `resourceIcon` 回退，容器尺寸统一由未提交改动引入（`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt:1600` 附近 `ExploreShape.listTileSize`） | 收敛进 §3.1 的 `ExploreMarketCard`（P1-3） |
| 不加载 SVG 内部外链、`icon_alt` 无障碍描述 | `缺失` | 服务端清理兜底（§4.2）+ 客户端 `HubIconFetcher` 不做 HTML 化；`icon_alt` 经 §3.3 解析器取值 |

## 5. “带 UI 的能力”统一函数契约

### 5.1 设计原则

一个能力只定义一个规范函数。用户在聊天中说出需求、AI 生成工具调用、用户点击“运行”、UI 表单收集参数，最后都进入同一个 `FunctionInvocation`：

```json
{
  "name": "khatkit__image_resize",
  "description": "调整图片尺寸",
  "input_schema": { "type": "object", "properties": {} },
  "ui_schema": {},
  "approval": { "mode": "required", "reason": "写入文件" },
  "output_schema": {}
}
```

已有卡片的 `parameters` 作为 `input_schema`，已有 `ui` 作为 `ui_schema`。不另造一套只供页面使用的参数表单。

现状判定：`部分`（见 §1 第 3 条）。已有：`parameters`/`ui` 定义在 `card-validator/src/main/java/heizige/kk/khatkit/card/CardManifest.kt:36-39`，校验白名单在 `card-validator/src/main/java/heizige/kk/khatkit/card/CardValidator.kt:347-355`，统一执行器在 `app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tools/KhatKitTools.kt:426`。缺失：宿主侧 schema 表单（脚本自弹 `khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/KhatKitForm.kt:60`，调用点 `app/src/main/java/heizige/kk/khatkit/app/core/ui/components/khatkit/KhatKitUiHost.kt:143`），`function` / `output_schema` / `approval` 没有任何服务端下发形态。

改动落点：

- 服务端条目级 `function_json`（字段名 `function`，见 §7.1）承载 `input_schema`/`ui_schema`/`approval`/`output_schema`；客户端模型落 `khatkit/src/main/java/heizige/kk/khatkit/hub/HubModels.kt:16-51`。
- 宿主侧声明式表单：`new: app/src/main/java/heizige/kk/khatkit/app/core/ui/components/khatkit/KhatKitSchemaForm.kt`，在 `app/src/main/java/heizige/kk/khatkit/app/core/ui/components/khatkit/KhatKitUiHost.kt:143` 的调用点接管“缺参数”分支。
- `ui.form` / `ui.screen` 保留为展示与补参渠道，不承载业务逻辑。**取舍结论（原文含糊处）**：参数表单的权威来源是 `input_schema`/`ui_schema`，宿主表单是唯一交互实现；脚本自弹的 `ui.form` 只用于展示结果或补齐宿主无法渲染的字段，不得另起一套参数收集逻辑（验收见 P0-6）。

### 5.2 调用流程

1. 用户或 AI 产生函数调用。→ `已有`：`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tools/KhatKitTools.kt:670`、`:1664-1678`。
2. 根据 `input_schema` 校验参数。→ `部分`：仅 `parameters` 存在，无宿主侧校验统一入口（随 §5.1 落地）。
3. 对缺失且标记为 `ui` 的字段显示声明式表单；AI 已提供的字段不重复询问。→ `缺失`：现由脚本自弹表单（`app/src/main/java/heizige/kk/khatkit/app/core/ui/components/khatkit/KhatKitUiHost.kt:143`）。
4. 进入同一个权限审批、付费授权、超时、取消和执行器。→ `已有`：`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tools/KhatKitTools.kt:426` `runCardWithStatus`（含审批/计量/看板）。
5. 以结构化结果返回：`ok`、`output`、`error_code`、`artifacts`、`ui_events`。→ `部分`：`EngineResult` 已有 ok/err 形态（`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tools/KhatKitTools.kt:426` 附近），`artifacts`/`ui_events` 无统一 schema（随 `function_json.output_schema` 落地）。

### 5.3 MCP 的处理

MCP 服务器的远端 tool schema 是函数定义来源。客户端将 MCP tool 映射为：

```text
mcp__{serverName}__{toolName}
```

现状判定：`已有`——`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tools/ChatToolFactory.kt:188` 已按该命名注册 MCP tool。

市场条目可以额外提供 `ui_hints`，例如字段控件、是否敏感、默认值和帮助文案；不能覆盖远端 schema 的类型、必填和权限语义。用户手动运行 MCP tool 时也复用同一个调用函数和审批流程。现状判定：`缺失`——两端 `grep -rn 'ui_hints'` 命中 0（服务端 `src/` 与客户端 `app/`、`khatkit/`、`khatkit-ui/` 均无该字段、无消费点），当前无既有落点可指。改动落点：`ui_hints` 作为条目 manifest 内的可选 JSON 下发，不新增独立接口（字段加在既有 `src/model/KhatKitHubDto.kt`，见 §7.1）；客户端消费点在参数补充逻辑（`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tools/ChatToolFactory.kt:188` 附近，该处已按 `mcp__{server}__{tool}` 命名注册 MCP tool），并入 P1-3 的卡片组件收尾，不单列接口。

## 6. GitHub 仓库导入与自动更新

整体现状判定：`缺失`。服务端没有任何 GitHub 抓取代码、来源字段、来源表或 webhook 路由（见 §1 第 5 条与 §2.1 的核对事实）。本节全部是新建工程，接口/字段命名按 §7.3 的对齐规则落地。

### 6.1 推荐模型

增加“来源绑定（Source Binding）”，而不是把 GitHub URL 当成一次性下载地址：

```json
{
  "source": {
    "type": "github",
    "repository": "owner/repo",
    "ref": "main",
    "path": "skills/research",
    "release_mode": "release_or_commit",
    "sync_mode": "webhook_then_poll",
    "auto_publish": false
  }
}
```

支持的来源类型（识别器，现状全部 `缺失`）：

- `skill`：目录内必须有 `SKILL.md`，读取 frontmatter 和附属文件。
- `mcp`：优先读取 `server.json`；没有时只允许明确的 KhatKit MCP manifest，不根据 README 猜 URL。
- `card`：读取 `card.json` 和入口脚本，仍走现有卡片校验和发布接口（`src/routing/KhatKitHubRouting.kt:763` `/api/cards/publish`、`:785` `/api/items/publish`）。
- `toolpkg/provider_plugin`：读取根目录 `manifest.json`，使用现有 ToolPkg 归档规则。

落点：DTO 加在 `src/model/KhatKitHubDto.kt`（`KhatKitHubItemSourceSummary`，与现有 `KhatKitHubItemSummary`/`KhatKitHubItemDetail` 同文件同风格）；识别器 `new: src/service/KhatKitSourceDetector.kt`。

### 6.2 首次导入

首次输入 GitHub URL 后，服务端执行以下步骤：

1. 规范化 URL，解析 owner、repo、ref 和可选子目录；拒绝非 GitHub 域名、路径穿越和不明确的动态分支配置。
2. 读取仓库元数据和默认分支，使用 Contents/Tree API 获取文件清单；大仓库或二进制使用 Release asset 或归档，不递归执行仓库代码。
3. 识别 manifest，生成标准市场 manifest、图标、展示文案和来源提交 SHA。
4. 生成不可变归档并计算 SHA-256，执行格式、许可证、依赖、权限、网络域名、ZIP 路径和图标安全校验。
5. 创建 `pending` 版本和审核报告，展示“待审核”，等待审核或可信规则批准。

“自动上线”必须以 manifest 可识别、归档可复现、来源提交可追溯为前提。README 里的安装命令、任意 shell 脚本和未声明的运行时依赖不能自动变成市场能力。

- 现状判定：`缺失`（步骤 4 的归档限制**已有**可复用：`src/service/KhatKitMarketValidation.kt:13-14` 与 `:130-135`；步骤 5 的 `pending` 状态**已有**：`src/table/KhatKitHubTables.kt:133-134`）。
- 落点：`new: src/service/KhatKitGithubImportService.kt`（URL 规范化 → Contents/Tree → 识别 → 归档 → 审计），复用 `src/service/KhatKitMarketValidation.kt` 与 `src/service/KhatKitHubRules.kt`。

### 6.3 更新策略

优先级从高到低：

1. 仓库所有者授权后，由 KodeHeapServer 创建 repository webhook，监听 `push`、`release` 和必要的 `workflow_run`；验证 `X-Hub-Signature-256`，事件只入队不在 webhook 请求内执行完整构建。
2. 没有 webhook 权限时，后台任务按来源策略轮询。使用 ETag、最后提交 SHA 和指数退避，避免触发 GitHub 限流。
3. MCP 元数据同步可以消费官方 MCP Registry 的 `updated_since` 增量接口，但仍要把版本、来源和校验结果保存到 KhatKit 自己的市场记录。→ 现状 `缺失`（服务端 `grep -rn 'updated_since' src/` 命中 0）。落点：`new: src/service/KhatKitMcpRegistrySyncService.kt`（`updated_since` 游标与 `sync_status` 落 `khatkit_hub_item_sources`，见 §7.3），复用 §6.3-2 的后台轮询与退避；接口依据见 §11 的 MCP Registry API 链接。

发现变化后：

- `release` 模式只接受新的正式 Release 或明确 tag。（首期默认，见 §10 决策 2）
- `commit` 模式允许跟踪分支，但每个 commit 生成独立候选版本；不能覆盖已发布版本。
- 内容未变化时不创建新版本。
- 新候选版本重新生成审计报告并进入 `pending`；旧版本保持可回滚。
- 默认只自动同步和校验，不自动批准。可信仓库可以由管理员开启 `auto_publish=true`（见 §10 决策 3），仍必须通过所有规则和版本兼容检查。
- 连续失败、删除 manifest、许可证改变、权限扩大、来源仓库转为 archived 或被撤销时暂停同步并通知维护者。

- 现状判定：`缺失`（无 webhook 路由、无后台轮询任务、无来源状态字段；全仓无 `webhook` 相关代码）。
- 落点：`new: src/service/KhatKitHubSyncService.kt`、`new: src/routing/KhatKitHubSourceRouting.kt`；表与字段见 §7.3。

### 6.4 安全要求

- 服务端抓取 GitHub 内容时禁止跟随到内网地址，禁止执行仓库中的代码、构建脚本和 Docker。→ 现状 `缺失`（无抓取代码）。
- GitHub Token 使用最小 `Contents: read` 权限；公开仓库首期不要求用户交 Token。→ 现状 `缺失`（无 token 存储/使用点）。
- webhook secret 只存服务端密钥存储，日志不打印完整请求体和 token。→ 现状 `缺失`（无 webhook）。
- 归档继续执行现有 8 MiB 压缩、32 MiB 展开、1024 文件、重复路径和路径穿越限制。→ 现状 `已有`：`src/service/KhatKitMarketValidation.kt:13-14`、`:130-135`。
- 来源提交 SHA、归档 SHA-256、manifest SHA-256 和审计规则版本必须绑定在版本记录中。→ 现状 `部分`：`sha256`、`audit_report_json`、`audit_rules_version` 已在 `src/table/KhatKitHubTables.kt:127`、`:128`、`:130`；commit/release 绑定缺失（随 §7.3）。
- 权限、网络域名、依赖和 UI schema 发生变化时，必须视为需要重新审核的重大更新。→ 现状 `缺失`（服务端 `grep -rni 'isBreakingUpdate' src/` 命中 0，无任何前后版本对比代码）。落点：`new: src/service/KhatKitHubRules.kt` 的 `isBreakingUpdate(prev, next)` 纯函数，比较 `requires_json` / `config_schema_json` / `ui_schema_json` / `function_json`（字段见 §7.1，DTO 在 `src/model/KhatKitHubDto.kt:274-338`）与来源域名/权限位变化；客户端消费侧落 `new: khatkit/src/main/java/heizige/kk/khatkit/hub/UpdateDiffResolver.kt`（供 P2-7 的版本差异展示）。
- 客户端侧归档限制（8 MiB/32 MiB/1024 文件/重复路径）：现状 `缺失`——`khatkit/src/main/java/heizige/kk/khatkit/hub/CardCache.kt:101-119` 只做 `canonicalPath` 越界检查；`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/toolpkg/ToolPkgRuntime.kt:43-51` 无大小/条数限制。落点：`CardCache.kt` 与 `ToolPkgRuntime.kt` 补同等上限（P1-4）。

## 7. API 和数据模型调整

### 7.1 `/api/items` 响应增加字段

在现有 `KhatKitHubItemSummary` / `KhatKitHubItemDetail` 上增加可选字段。命名沿用服务端现有风格：DTO 字段 snake_case，JSON 不透明体进 `*_json text` 列并用 `JsonElement`/`JsonObject` 序列化（对齐 `display_name`、`pricing`、`requires`、`audit_report` 的既有写法）。

| 字段 | 现状判定 | 落点 |
| --- | --- | --- |
| `display_name` `String \| LocaleMap` | `已有`（`JsonElement`，`src/model/KhatKitHubDto.kt:279`、`:313`；DB `display_name text`，`src/table/KhatKitHubTables.kt:116`） | 不改；客户端新增解析（`khatkit/src/main/java/heizige/kk/khatkit/hub/HubModels.kt:16-51`） |
| `description` `String \| LocaleMap` | `部分`（现为纯 `String`，`src/model/KhatKitHubDto.kt:280`、`:314`；DB `description text`，`src/table/KhatKitHubTables.kt:117`） | DTO 类型改 `JsonElement`（与 `display_name` 一致，列名不变）；客户端按 §3.3 顺序解析 |
| `tags` `List<String> \| LocaleMap` | `部分`（现由 `manifest_json` 派生 `List<String>`，`src/service/KhatKitCommerceService.kt:965`、`:1449-1454`；DTO 字段 `src/model/KhatKitHubDto.kt:297`、`:334`） | 新增列 `tags_json text default '[]'`（与 `requires_json`/`files_json` 同风格），保留旧 List 兼容 |
| `icon_url` / `icon_sha256` / `icon_mime` | `缺失` | `khatkit_hub_items` 加 `icon_url varchar(512) default ''`、`icon_sha256 varchar(64) default ''`、`icon_mime varchar(64) default ''` |
| `icon_alt` `String \| LocaleMap` | `缺失` | 加列 `icon_alt_json text default '{}'`（对齐 `pricing_json` 写法） |
| `config_schema` `JsonObject?` | `缺失` | 加列 `config_schema_json text default '{}'`；DTO `config_schema: JsonObject = JsonObject(emptyMap())` |
| `ui_schema` `JsonObject?` | `缺失` | 加列 `ui_schema_json text default '{}'` |
| `function` `JsonObject?` | `缺失` | 加列 `function_json text default '{}'`；承载 §5.1 的 input/ui/approval/output schema |
| `source` `Object?` | `缺失` | 独立表 + DTO `source: KhatKitHubItemSourceSummary? = null`（见 §7.2/§7.3） |
| `compatibility` `JsonObject?` | `缺失` | 加列 `compatibility_json text default '{}'`（承载 min/max app 版本、abi、权限差异） |
| `slug`（跨源去重键） | `缺失` | 加列 `slug varchar(120) default ''` + 在 `uniqueIndex(name, kind, version)`（`src/table/KhatKitHubTables.kt:139`）之外新增 `index(false, kind, slug)`；旧资源索引的 `KhatKitResourceEntry.slug`（`src/model/KhatKitResourceDto.kt:16`）即同义键 |
| `install_state` | `已有设计`（服务端不落库，符合） | 客户端根据本地安装版本、配置、启用范围和连接状态计算；**服务端不新增列、响应中不出现该字段** |

`/api/items` 合并逻辑（`src/routing/KhatKitHubRouting.kt:600-618`）需按 `(kind, slug)` 去重：同一 slug 同时来自 legacy 资源索引与统一市场时，只保留统一市场版本（hub item 优先）。现状：`缺失`（当前只是 `extensions + cards` 拼接后排序，无去重键；T1 证据同处）。

### 7.2 GitHub 来源接口

新增（现状 `缺失`，`src/routing/KhatKitHubRouting.kt:600-800` 无这些路由）：

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `POST` | `/api/items/import/github` | 校验 URL、创建来源绑定并触发首次导入 |
| `GET` | `/api/items/{id}/source` | 查看来源、ref、commit、同步状态和最近错误 |
| `POST` | `/api/items/{id}/sync` | 管理员或来源拥有者立即同步 |
| `PATCH` | `/api/items/{id}/source` | 修改 ref、路径、同步模式和自动发布策略（`auto_publish` 仅管理员，见 §10 决策 3） |
| `POST` | `/api/webhooks/github/{binding}` | 接收 GitHub webhook，验签后入队 |

导入接口必须返回任务 ID 和 `pending` 状态，不同步阻塞 HTTP 请求。客户端市场页显示“导入中 / 待审核 / 可安装 / 同步失败”。

落点：`new: src/routing/KhatKitHubSourceRouting.kt`（挂在现有 hub route 树，与 `src/routing/KhatKitHubRouting.kt` 并列）。

### 7.3 数据库字段

在 `khatkit_hub_items` 增加或拆分来源表，建议字段：

```text
source_type, source_url, source_owner, source_repo, source_ref,
source_path, source_commit, source_release_id, sync_mode,
auto_publish, sync_status, last_sync_at, next_sync_at, last_sync_error,
icon_url, icon_sha256, icon_mime, icon_alt_json, config_schema_json,
ui_schema_json, function_json
```

推荐独立 `khatkit_hub_item_sources` 表（现状 `缺失`；T1 证据：`src/table/KhatKitHubTables.kt:112-142` 只有 item/vote/series 表，无 sources 表），使一个市场条目可以保留来源变更记录；版本表保存每个发布版本的 commit、归档 hash 和审计结果，不覆盖历史值。

命名对齐规则（新增字段必须遵守，不许臆造）：

- Exposed 表对象：`KhatKitHubItemSourceTable : Table("khatkit_hub_item_sources")`，对象与列命名沿用 `src/table/KhatKitHubTables.kt` 现有写法（`val sourceType = varchar("source_type", 32)` 等）。
- 不透明 JSON 一律 `<name>_json text default '{}'`（或 `'[]'`），列名 snake_case，DTO 侧用 `JsonObject`/`JsonArray`。
- 状态枚举小写字符串，复用服务端既有取值：`pending | approved | rejected`（`src/table/KhatKitHubTables.kt:133-134`）+ 新增 `revoked`（先例：`src/service/KhatKitDependencyStore.kt:300` 的 `STATUS_REVOKED`、`src/model/KhatKitDto.kt:60`）。
- 建表/加列走现有路径：`src/service/KhatKitCommerceService.kt:212-220` 的 `SchemaUtils.createMissingTablesAndColumns(...)`（把 `KhatKitHubItemSourceTable` 加进清单）。

`revoked` 语义：条目被撤销后 `/api/items` 与 `/api/items/{id}` 不再返回（现状 `部分`：市场只放 `status eq "approved"`，`src/service/KhatKitCommerceService.kt:887-889`；`/api/items/{id}` 同样只认 approved，`src/routing/KhatKitHubRouting.kt:620`），但已安装客户端可凭本地缓存继续使用，并在详情页显示撤销原因。

### 7.4 旧接口迁移

迁移期间保留三条兼容路径：

- `/api/cards`：继续服务旧卡片客户端和已有卡片计量，不改响应字段。→ 现状 `已有`：`src/routing/KhatKitHubRouting.kt:592`。
- `/api/khatkit/resources/index.json`：保留旧的内置技能合集 / MCP / 合集索引，新增字段只做可选扩展。→ 现状 `已有`：`src/routing/KhatKitRouting.kt:124`。
- `/api/items`：作为新市场的唯一目录来源，逐步承接 `skill`、`mcp`、`prompt`、`workflow` 和 ToolPkg。→ 现状 `部分`（路由已有，`src/routing/KhatKitHubRouting.kt:600`；去重键与撤销态缺失，见 §7.1/§7.3）。

客户端按能力开关切换到 `/api/items`。同一个条目如果同时出现在旧资源索引和统一市场，使用稳定的 `(kind, slug)` 去重，优先显示统一市场版本。旧接口下载的资源安装成功后，应该补写本地来源元数据，便于后续迁移到统一更新器（客户端现状 `缺失`：`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tools/KhatKitTools.kt:1642` 只落 `name → version`（`installedCardVersions`）、`:1658-1661` 只落 `name → triggers`（`installedCardTriggers`））。

在以下条件满足前，不要删除旧资源接口：旧客户端仍在使用、统一市场安装器尚未覆盖 Skill/MCP/合集、或统一接口无法提供兼容的回滚和撤销状态。

## 8. 落地任务清单（P0 / P1 / P2）

每条任务都可独立派工、独立验收：`objective`（做什么）＋ `acceptance`（判据）＋ `验证`（自动命令优先，UI 类给人工步骤）。命令中 `服务端根` = `/home/heizige/文档/IDEA/Project/KodeHeapServer`，`客户端根` = `/home/heizige/文档/Android/Project/KhatKit`。行号仅作定位，改后以 grep 关键词为准。

**服务端测试基线留档（2026-10-15 实测，本清单所有服务端验证共用）**：服务端干净工作树（HEAD `b1b6d4c`）执行 `./kotlin test` 退出码为 **1**：70 个用例中 61 通过、9 失败。基线失败集（6 个测试类，逐类计数 1+2+1+1+2+2 = 9 例）：`AdminRoutingTest` 1、`BuiltinCardsSeedTest` 2、`ImageToolboxLuaTest` 1、`ImageToolboxReleaseTest` 1、`ImageToolboxSeedTest` 2、`KhatKitDependencyRoutingTest` 2（逐类分布仅供定位参考，测试重命名后不构成误判依据；判据以总量 9 为准）。复现：`cd /home/heizige/文档/IDEA/Project/KodeHeapServer && ./kotlin test`。故下文所有服务端验证判据统一为「**失败集不得超出基线 9 个，且本任务新增/更新的契约测试必须全绿**」，不再以「整套测试全绿」作为服务端判据。约束：**禁止改动与本任务无关的既有测试（跳过、删除或放宽断言）来让套件变绿**；基线失败集若要收敛，须单独立项登记。

### P0：契约与可用性（不依赖 §10 决策，可立即开工）

#### P0-1 服务端 `/api/items` 契约扩展与去重键
- objective：按 §7.1 给 `KhatKitHubItemSummary`/`KhatKitHubItemDetail` 补齐 `icon_*`、`config_schema`、`ui_schema`、`function`、`compatibility`、`source`、`slug`，`description` 类型改 `JsonElement`，新增 `tags_json`、`compatibility_json`、`config_schema_json`、`ui_schema_json`、`function_json`、`icon_*`、`slug` 列；`/api/items` 合并时按 `(kind, slug)` 去重（hub 优先）。
- acceptance：新字段全部可缺省（空对象/空数组），旧客户端解析不受影响；`install_state` 不出现在任何响应；同一 `(kind, slug)` 在 `/api/items` 只出现一次；`/api/cards` 与 `/api/khatkit/resources/index.json` 响应字段零变化。
- 落点：`src/model/KhatKitHubDto.kt:274-338`、`src/table/KhatKitHubTables.kt:112-142`、`src/service/KhatKitCommerceService.kt:1643`、`:1671`、`:887-889`、`src/routing/KhatKitHubRouting.kt:600-624`、`src/model/KhatKitResourceDto.kt:15-33`。
- 验证：`服务端根 && ./kotlin test`（失败集不得超出基线 9 个，且本任务新增契约测试必须全绿，见 §8 开头基线留档）；新增 `test/KhatKitHubItemsContractTest.kt` 断言字段缺省与去重；`grep -n "icon_url\|config_schema_json\|slug" src/model/KhatKitHubDto.kt src/table/KhatKitHubTables.kt`；`grep -n "install_state" -r src/ | wc -l` 应为 0。

#### P0-2 服务端条目撤销态
- objective：`khatkit_hub_items.status` 增加 `revoked`（注释同步更新），撤销后台条目在 `/api/items`、`/api/items/{id}`、`/api/items/{id}/download` 全部不可见；保留管理端查询撤销项的只读入口。
- acceptance：撤销后三处均 404/不出现在列表；未撤销条目行为不变；撤销原因可回显（管理端）。
- 落点：`src/table/KhatKitHubTables.kt:133-134`、`src/service/KhatKitCommerceService.kt:887-889`、`src/routing/KhatKitHubRouting.kt:600-643`；管理端路由 `src/routing/KhatKitHubRouting.kt:994-1010`（`/items/pending` 一类）。
- 验证：`服务端根 && ./kotlin test`（失败集不得超出基线 9 个，见 §8 开头基线留档）；新增用例覆盖 approved → revoked 后列表/详情/下载三处断言；`grep -n "revoked" src/table/KhatKitHubTables.kt src/service/KhatKitCommerceService.kt`。

#### P0-3 客户端 Hub 模型与 `/api/items` 解析
- objective：`CardIndexEntry`/条目模型补 `display_name`、`description`(LocaleMap)、`tags`、`icon_*`、`config_schema`、`ui_schema`、`function`、`source`、`compatibility`、`slug`；`HubClient` 新增 `/api/items` 拉取与解析；`LocaleMapResolver` 实现 §3.3 的四级选择顺序。
- acceptance：新字段全 `@Serializable` 默认值，老服务端（无新字段）仍能反序列化；0 处 `install_state` 落盘；locale 选择顺序有单测覆盖（`zh-CN → zh → en → 首个非空`）。
- 落点：`khatkit/src/main/java/heizige/kk/khatkit/hub/HubModels.kt:16-51`、`khatkit/src/main/java/heizige/kk/khatkit/hub/HubClient.kt:79-125`、`new: khatkit/src/main/java/heizige/kk/khatkit/hub/LocaleMapResolver.kt`。
- 验证：`客户端根 && ./gradlew :khatkit:test`；新增 `khatkit/src/test/java/heizige/kk/khatkit/hub/LocaleMapResolverTest.kt`、`.../HubItemsParsingTest.kt`（含缺字段 JSON fixture）；`grep -rn "/api/items" khatkit/src/main/java/ | wc -l` ≥1。

#### P0-4 探索页文案进 `strings.xml`
- objective：`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt` 的中文硬编码（≥174 行 / 约 201 处中文字面量，口径见 §1 第 2 条）与数量/价格/版本格式化迁到 `values/strings.xml` + `values-zh/strings.xml`；覆盖同文件内的 MD3 与 Miuix 两条样式分支（**不存在第二套 Miuix 探索页文件**）。
- acceptance：`ExploreMarketPage.kt` 内中文字面量 0 处（注释除外）；`stringResource`/`pluralStringResource` 覆盖所有用户可见文案；`values-zh` 与 `values` 键集合一致；无障碍 `contentDescription` 走资源。
- 落点：`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt:1006-1076` 及全文、`app/src/main/res/values/strings.xml`、`app/src/main/res/values-zh/strings.xml`。
- 验证：`grep -cP '"[^"]*[\x{4e00}-\x{9fff}]' app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt` 应仅剩注释/日志类（人工确认）；`客户端根 && ./gradlew :app:assembleDebug` 退出码 0；人工核对：切换系统语言 zh/en/ja，探索页按钮、状态、错误、数量单位无中文残留。

#### P0-5 MCP 条目级配置入口与“需要配置”状态
- objective：条目级“配置”入口（预填名称/传输/URL/头），复用 `McpConfigModal.kt`；安装时识别必填配置，缺失则只保存候选配置、`enable = false`、不写入当前助手，卡片显示“需要配置”。
- acceptance：缺必填时不出现“已连接”；`assistant.mcpServers` 不被隐式追加；市场详情返回后状态为“已配置/连接失败/需要配置”之一；secret 字段在详情与错误文案中脱敏。
- 落点：`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt:791`、`:2310-2365`、`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketViewModel.kt:236-293`、`app/src/main/java/heizige/kk/khatkit/app/feature/settings/mcp/McpConfigModal.kt`、`new: app/src/main/java/heizige/kk/khatkit/app/feature/explore/McpInstallConfigSheet.kt`。
- 验证：`客户端根 && ./gradlew :app:test`；新增 VM 单测断言“缺必填 → enable=false 且助手 mcpServers 不变”；人工核对：安装一个带必填 key 的 MCP 条目 → 卡片显示“需要配置”→ 点配置 → 表单已预填 URL/传输 → 不填 key 保存后状态非“已连接”。

- 端到端可声明性缺口（归服务端 P1 **与客户端 `planMcpInstall` 调用点两侧**；本注记不新增任务、不改编号）：资源市场的「必填」目前只能靠资源包 `headers` 的**值约定**识别（占位值 `${API_KEY}` / `<your-key>` / `YOUR_XXX` 等，见 `app/src/main/java/heizige/kk/khatkit/app/feature/explore/McpInstallPlan.kt:22-23`、`:54-62`）：服务端 `IDEA/Project/KodeHeapServer/src/model/KhatKitResourceDto.kt:50-56` 的 `KhatKitResourceMcpServer`（`name`/`transport`/`url`/`headers`）与客户端 `khatkit/src/main/java/heizige/kk/khatkit/hub/HubResourceModels.kt:63-70` 的 `HubResourceMcpServer` 都没有 `config_schema`/`required` 字段，且 `src/service/BuiltinResources.kt` 现无任何资源声明 `headers`（全文仅 `:207` 一处遍历）。**第二个前置条件在客户端**：`planMcpInstall` 的 `schema` 参数默认空（`app/src/main/java/heizige/kk/khatkit/app/feature/explore/McpInstallPlan.kt:109-113` 的 `schema: JsonObject = JsonObject(emptyMap())`），而两个调用点都只传 `server`、从不传导条目自身的 `config_schema`（t23 核验值 `app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketViewModel.kt:244` 与 `ExploreMarketViewModel.kt:309`；当前工作树因未提交改动漂移到 `ExploreMarketViewModel.kt:252`、`ExploreMarketViewModel.kt:339`，以 `grep -n 'planMcpInstall'` 为准）。故 resource 侧 `headers` 空 + item 侧 `schema` 恒空，**两侧合起来才使线上暂无「需要配置」条目**。要端到端声明「必填」需两侧并列：①服务端给资源包 MCP 服务补 `config_schema` 字段（并让种子/内置资源真正声明 `headers` 或 schema）；②客户端在 `planMcpInstall` 调用点把条目 `config_schema` 传导进 `schema` 参数——只做①不做②仍不可达。

#### P0-6 宿主侧 schema 驱动表单
- objective：宿主按 `ui_schema`/`input_schema` 渲染声明式表单，AI 已提供字段不重复询问；`ui.form` 退化为展示与补参渠道。
- acceptance：缺参时宿主表单出现且参数回灌进同一 `runCardWithStatus` 调用；AI 全参数调用不弹表单；表单渲染失败不影响卡片执行。
- 落点：`new: app/src/main/java/heizige/kk/khatkit/app/core/ui/components/khatkit/KhatKitSchemaForm.kt`、`app/src/main/java/heizige/kk/khatkit/app/core/ui/components/khatkit/KhatKitUiHost.kt:143`、`khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/KhatKitForm.kt:60`。
- 验证：`客户端根 && ./gradlew :app:test`；人工核对：用一张声明了必填 UI 字段的卡片，分别走“AI 调用”和“用户点运行”，确认两条路径都落到同一执行器且审批/计量记录一致（`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tools/KhatKitTools.kt:426`）。

### P1：图标与统一市场（P1-1/P1-2 彼此独立；P1-3 依赖 P0-3；P1-4 依赖 P0-2）

#### P1-1 服务端 SVG 上传/清理/下发
- objective：图标上传 → SVG 清理（去 `<script>`、事件属性、外链、动画）→ 服务端重算 `icon_sha256` → 按版本不可变 URL + 长期缓存；域名白名单；`icon_alt_json` 只作无障碍。
- acceptance：恶意 SVGPayload（script/onload/外链/动画）清理后不含危险节点；请求里的 `icon_sha256` 一律被服务端重算覆盖；同版本第二次上传被拒；URL 含 `<name>/<version>`。
- 落点：`new: src/helper/SvgSanitizer.kt`、`new: src/service/KhatKitIconService.kt`、`new: src/routing/KhatKitIconRouting.kt`、`src/table/KhatKitHubTables.kt:112-142`（4 列）、`src/service/KhatKitCommerceService.kt:218`。
- 验证：`服务端根 && ./kotlin test`（失败集不得超出基线 9 个，见 §8 开头基线留档）；新增 `test/SvgSanitizerTest.kt`（至少 6 类危险样本 + 哈希重算 + 白名单拒绝）。

#### P1-2 客户端图标缓存键与哈希校验回退
- objective：Coil 用 `icon_url + icon_sha256` 作缓存键；下载后校验哈希再落盘；限制最大字节数与并发；失败回退本地类型图标。
- acceptance：篡改字节的图标不被缓存/显示，回退本地图标且列表不阻塞；同一 URL 不同 sha256 命中不同缓存条目；断网时列表正常渲染。
- 落点：`app/src/main/java/heizige/kk/khatkit/app/KhatKitApp.kt:181-193`、`new: khatkit/src/main/java/heizige/kk/khatkit/hub/HubIconFetcher.kt`、`khatkit/src/main/java/heizige/kk/khatkit/hub/HubModels.kt:16-51`。
- 验证：`客户端根 && ./gradlew :khatkit:test`；新增 `HubIconFetcherTest`（哈希不匹配 → null；超限 → 拒绝）；人工核对：断网进入探索页，图标位显示默认图标且列表可滚动、可安装。

#### P1-3 统一卡片组件与详情页状态
- objective：抽出 `ExploreMarketCard`（身份/能力/操作三区），卡片/Skill/MCP/合集共用；详情页显示权限、依赖、来源提交、更新记录；不兼容/撤销版本不显示安装按钮。
- acceptance：四类条目在列表使用同一组件；撤销/不兼容条目无安装按钮且有原因；详情页字段来自 P0-3 模型（不新增假数据）。
- 落点：`new: app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketCard.kt`、`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt:1744`（卡片壳 `PressableRow` 定义）、`:1553`（首次调用）、`:2310-2365`（`ResourceRow` 与分组列表）。
- 验证：`客户端根 && ./gradlew :app:assembleDebug`；人工核对：逐一打开卡片/Skill/MCP/合集四类条目，确认三区结构、操作按钮集合、撤销态无安装按钮。

#### P1-4 归档上限与安装来源元数据
- objective：客户端归档校验补齐 8 MiB 压缩 / 32 MiB 展开 / 1024 文件 / 重复路径 / 路径穿越；安装记录持久化来源版本、来源 commit、归档 hash、上次检查时间；本地记录能识别 `revoked`（服务端 P0-2）。
- acceptance：超限或重复路径归档被拒绝且提示明确；安装记录含 4 个字段且可回滚到上一版本；撤销条目不显示安装按钮。
- 落点：`khatkit/src/main/java/heizige/kk/khatkit/hub/CardCache.kt:101-119`、`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/toolpkg/ToolPkgRuntime.kt:43-51`、`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tools/KhatKitTools.kt:1642`、`:1658-1661`。
- 验证：`客户端根 && ./gradlew :khatkit:test :app:test`；新增 `CardCacheLimitsTest`（超大小/超文件数/重复路径/穿越四类用例）；`grep -n "sourceCommit\|archiveHash" $(客户端根)/app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tools/KhatKitTools.kt`。

### P2：GitHub 同步（P2-1 → P2-2/P2-3 → P2-4/P2-5/P2-6 → P2-7/P2-8；首期只做公开仓库，见 §10 决策 1）

#### P2-1 来源表与字段
- objective：新增 `KhatKitHubItemSourceTable("khatkit_hub_item_sources")` 与 §7.3 全部 `source_*` 列；`khatkit_hub_items` 补 `slug`/`tags_json` 等 P0-1 未含列；建表进 `SchemaUtils.createMissingTablesAndColumns`。
- acceptance：全新库与已有库都能启动（加列不删列）；一个条目多条来源变更记录可追溯，版本表不被覆盖写。
- 落点：`src/table/KhatKitHubTables.kt:112-156`、`src/service/KhatKitCommerceService.kt:212-220`、`src/model/KhatKitHubDto.kt`。
- 验证：`服务端根 && ./kotlin test`（失败集不得超出基线 9 个，见 §8 开头基线留档）；`grep -n "khatkit_hub_item_sources" src/table/KhatKitHubTables.kt`；人工核对：`./kotlin run` 启动日志无建表错误。

#### P2-2 五条来源接口
- objective：实现 §7.2 的 import/source/sync/PATCH/webhook 五条路由；导入返回任务 ID + `pending`，不在 HTTP 请求内同步完成。
- acceptance：导入接口 <1s 返回任务 ID；未登录返回 401；`PATCH` 改 `auto_publish` 时非管理员被拒（§10 决策 3）；响应/字段名与 `src/model/KhatKitHubDto.kt` 现有 snake_case 风格一致。
- 落点：`new: src/routing/KhatKitHubSourceRouting.kt`、`new: src/service/KhatKitGithubImportService.kt`、`src/model/KhatKitHubDto.kt`。
- 验证：`服务端根 && ./kotlin test`（失败集不得超出基线 9 个，见 §8 开头基线留档）；新增 `test/KhatKitHubSourceRoutingTest.kt`（鉴权、任务 ID、pending 状态、非管理员 PATCH 拒绝）。

#### P2-3 四类来源识别器
- objective：`skill`（`SKILL.md` 必需）、`mcp`（优先 `server.json`，不猜 README）、`card`（`card.json` + 现有校验）、`toolpkg/provider_plugin`（根 `manifest.json` + 现有归档规则）。
- acceptance：四类正样本各生成标准 manifest；缺 manifest 的仓库返回可读拒绝理由且不创建版本；README 里的安装命令/shell 不进市场能力。
- 落点：`new: src/service/KhatKitSourceDetector.kt`，复用 `src/service/KhatKitCardValidator.kt`、`src/service/KhatKitMarketValidation.kt`。
- 验证：`服务端根 && ./kotlin test`（失败集不得超出基线 9 个，见 §8 开头基线留档）；新增 `test/KhatKitSourceDetectorTest.kt`（4 类正样本 + 3 类负样本：无 manifest、README-only、路径穿越）。

#### P2-4 webhook 验签与入队
- objective：实现 `POST /api/webhooks/github/{binding}`，校验 `X-Hub-Signature-256`，事件只入队不在请求内构建。
- acceptance：签名错/缺失返回 401/403 且不产生任务；正确签名在 <200ms 内返回并入队；请求体与 token 不进日志。
- 落点：`new: src/routing/KhatKitHubSourceRouting.kt`、`new: src/service/KhatKitHubSyncService.kt`。
- 验证：`服务端根 && ./kotlin test`（失败集不得超出基线 9 个，见 §8 开头基线留档）；新增 `test/KhatKitGithubWebhookTest.kt`（正确签名/错误签名/缺头/超大请求体）；`grep -rn "X-Hub-Signature-256" src/` 命中且日志无明文 body。

#### P2-5 轮询与失败暂停
- objective：ETag + 最后 commit SHA + 指数退避轮询；内容未变化不建版本；每 commit 独立候选（非默认模式）；连续失败/许可证变化/权限扩大/archived → 暂停同步并记录 `sync_status`、`last_sync_error`。
- acceptance：相同 commit 不重复发布；连续 N 次失败后 `next_sync_at` 递增退避且 `sync_status=paused`；恢复后可从上次 commit 继续。
- 落点：`new: src/service/KhatKitHubSyncService.kt`、`src/table/KhatKitHubTables.kt`（`sync_status`/`last_sync_at`/`next_sync_at`/`last_sync_error`）。
- 验证：`服务端根 && ./kotlin test`（失败集不得超出基线 9 个，见 §8 开头基线留档）；新增 `test/KhatKitHubSyncScheduleTest.kt`（重复 commit 抑制、退避序列、暂停条件）。

#### P2-6 抓取侧安全
- objective：抓取禁止跟随内网地址（DNS 解析后校验，含重定向）；GitHub Token 仅 `Contents: read`；webhook secret 只存服务端密钥存储。
- acceptance：指向 `127.0.0.1`/`10.`/`169.254.`/`::1` 与重定向到内网的 URL 被拒；拒绝日志不打印 token；secret 不出现在任何 API 响应。
- 落点：`new: src/service/KhatKitGithubFetchGuard.kt`（纯函数 + 解析校验）、`new: src/service/KhatKitGithubImportService.kt`。
- 验证：`服务端根 && ./kotlin test`（失败集不得超出基线 9 个，见 §8 开头基线留档）；新增 `test/KhatKitGithubFetchGuardTest.kt`（不少于 8 个 SSRF 样本 + 重定向绕过样本）。

#### P2-7 客户端同步状态 UI
- objective：市场页显示“导入中/待审核/可安装/同步失败”、可用更新、更新说明、回滚版本。
- acceptance：四种同步状态可渲染；有更新时展示版本差异说明；回滚入口可用且回滚后版本号正确。
- 落点：`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt`、`ExploreMarketViewModel.kt`、`new: app/src/main/java/heizige/kk/khatkit/app/feature/explore/SyncStatusBadge.kt`。
- 验证：`客户端根 && ./gradlew :app:assembleDebug`；人工核对（需一个已绑定来源的条目）：制造同步失败 → 显示“同步失败”与最近错误；恢复 → 显示可用更新；执行一次回滚 → 版本号回退。

#### P2-8 导入侧测试与审计闭环
- objective：补 §9 工程项要求服务端覆盖的 URL 解析、manifest 识别、重复 commit、webhook 验签、ETag 轮询、审计失败与回滚测试；`auto_publish` 审批复用既有审计表并记录规则版本与操作者（`src/table/PlatformDashboardTables.kt:67-72` 的 `actor_id`/`action`/`detail`，`detail` 上限 300 字；写入走 `src/routing/KhatKitHubRouting.kt:1143-1145` 的 `auditCall` → `principalUserId()`）。
- acceptance：上述 7 类新增用例各有断言且全绿（按 §8 开头基线口径：新增用例必须全绿，既有失败集不得超出基线 9 个）；每次自动批准落一条含规则版本 + 操作者的记录；管理员可全局关闭自动发布。
- 落点：`test/`（新增 5 个测试文件）、`src/service/KhatKitHubRules.kt`、`src/table/KhatKitHubTables.kt`（复用 `PlatformAuditLogTable`，不新建审批表；来源表补操作者列）。
- 验证：`服务端根 && ./kotlin test`（失败集不得超出基线 9 个，见 §8 开头基线留档）；`ls test/ | grep -c "KhatKit.*Source\|KhatKitGithub\|KhatKitHubSync"`；人工核对管理端关闭自动发布后新候选只入 `pending`。

## 9. 验收标准

标注说明：【自动】= 有可脚本化命令/测试；【人工】= 需人眼或真机核对。任务号见 §8。

### 功能

- 每种 item 都能显示独立 SVG；断网、坏图标和旧数据仍有稳定回退。【人工 + 自动】P1-1、P1-2。
- 切换中文、英文和系统其他语言时，名称、摘要、标签、按钮、状态和无障碍描述不混用硬编码中文。【人工】P0-4、P0-3。
- 一个带 UI 的卡片既能由用户点击运行，也能由 AI 以同一个函数名调用，参数校验、审批和结果结构一致。【人工 + 自动】P0-6（`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tools/KhatKitTools.kt:426` 单点执行器）。
- MCP 缺少密钥时显示“需要配置”，点击配置可进入现有配置表单；配置后才能连接和启用。【人工 + 自动】P0-5。
- 输入一个公开 GitHub Skill/MCP 仓库可以得到可追溯的待审核候选版本；相同 commit 不重复发布。【自动】P2-2、P2-3、P2-5。
- 新 Release 或 webhook push 能生成新的候选版本；旧版本仍可安装或回滚。【自动 + 人工】P2-4、P2-5、P2-7。

### 安全

- 未审核、撤销、许可证不合规、哈希不匹配和归档越界的版本不可安装。【自动】P0-2、P1-2、P1-4、P2-3。
- 图标 SVG 含危险内容（`<script>`、事件属性、外链、动画）时在下发前被清理，清理结果不含危险节点。【自动】P1-1（逐条对应 §8 P1-1 acceptance 与 `test/SvgSanitizerTest.kt` 的 ≥6 类危险样本断言）。
- 服务端不执行导入仓库代码，不把 GitHub 内容中的 URL 当作内部服务请求地址。【自动】P2-6。
- secret 不进入市场 manifest、日志、导出包和错误文本。【自动 + 人工】P0-5、P2-4、P2-6。
- 自动发布策略可被管理员关闭，并记录每次批准的规则版本和操作者。【自动 + 人工】P2-8（复用 `PlatformAuditLogTable`：`src/table/PlatformDashboardTables.kt:67-72`）。

### 工程

- 服务端覆盖 URL 解析、manifest 识别、重复 commit、webhook 验签、ETag 轮询、审计失败和回滚测试。【自动】P2-8（当前 `test/` 只有 `KhatKitMarketTest.kt`、`KhatKitMarketRoutingTest.kt`、`MarketSemverTest.kt` 涉及市场，导入侧为 0）。
- Android 覆盖多语言回退、SVG 错误回退、配置状态、MCP 配置跳转、函数调用一致性和安装更新状态。【自动 + 人工】P0-3、P0-5、P0-6、P1-2、P1-4。
- 兼容旧 `/api/cards`、旧 `/resources/index.json` 和没有新字段的服务器。【自动】P0-1、P0-3（fixture 断言缺字段可解析）。

## 10. 产品决策（已给出唯一推荐，待产品确认）

五个决策此前开放；下表是本次修订的推荐结论，依据全部来自 T1/T2 代码实勘，影响面用条目数/文件数表达。决策 1–4 决定 §7.3 的表结构与 §7.2 的接口形状，**建议先拍板 1–4 再开工 P2/P1**。

### 决策 1：首期是否只开放公开 GitHub 仓库（私有仓库延后到 GitHub App/OAuth）？
- 推荐：**首期只开放公开仓库**；私有仓库延后，不阻塞 P2。
- 依据：T1 判定服务端「无任何 GitHub 抓取代码」（来源绑定字段、来源表、import/sync/webhook 路由、token 存储全部缺失；`src/model/KhatKitHubDto.kt:109-125` 无 `source`，`src/routing/KhatKitHubRouting.kt:600-800` 无相关路由）。私有仓库需要额外的 App/OAuth 装置授权流程与装置级 token 存储，是独立交付面；公开仓库只需一个服务端 PAT（`Contents: read`，或匿名 API）。
- 影响面：首期省掉服务端约 2 个新文件（`KhatKitGithubAuthService.kt`、`KhatKitGithubAuthRouting.kt`）与客户端 1 个授权页；P2 由约 9 个文件降到约 7 个文件（`src/service/` 3 + `src/routing/` 1 + `src/helper/` 1 + 表/模型 2）。代价：私有技能仓库用户需等二期。

### 决策 2：默认更新策略 `release` 还是 `release_or_commit`？
- 推荐：**默认 `release`**；`release_or_commit` 只作为来源级可选值。
- 依据：来源字段与同步策略当前全部缺失（`src/model/KhatKitHubDto.kt:109-125`），没有历史包袱，可以按最严默认落地；`khatkit_hub_items` 现有唯一索引是 `(name, kind, version)`（`src/table/KhatKitHubTables.kt:139`），commit 级跟踪会产生大量候选版本行；客户端缺的是消费侧（安装/更新提示）的版本差异与更新日志展示，同步侧完全空白——现有 `changelog` 输入框只在发布表单里（`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt:3277`、`:3345` 的「更新说明」`KedgeOutlinedTextFieldWithSlots`），没有任何比较/展示已安装版本差异的 UI。
- 影响面：`release` 默认下服务端只需读 GitHub Releases API + 1 个枚举解析（`release_mode`）；`release_or_commit` 额外需要 commit→version 生成规则、每 commit 独立候选队列、客户端更新说明页，约 +2 服务端文件 +1 客户端页。

### 决策 3：可信仓库“自动发布”由作者自开还是仅管理员？
- 推荐：**首期仅管理员可开**（`auto_publish` 只能经管理员接口写，且每次自动批准记录规则版本 + 操作者）。
- 依据：服务端已有管理端审核路由（`src/routing/KhatKitHubRouting.kt:994-1010` 的 `/items/pending` 一类），且 `khatkit_hub_items.status` 只有 `pending | approved | rejected`（`src/table/KhatKitHubTables.kt:133-134`）；审批记录可直接复用既有通用审计表 `PlatformAuditLogTable`（`src/table/PlatformDashboardTables.kt:67-72`，含 `actor_id`、`action`、`detail`，`detail` 上限 300 字；据此，审批审计能记：操作者（`actor_id`）、动作（`action` = approved/rejected）、规则版本与 ≤300 字摘要/理由；不能记：完整 diff、长正文与附件，超长内容只能截断或另存他表），管理端 approve/reject 已经由 `auditCall` 记录 `principalUserId()`（`src/routing/KhatKitHubRouting.kt:1143-1145`；`:1014` approved、`:1025` rejected）；`audit_rules_version` 列已存在（`src/table/KhatKitHubTables.kt:130`）可复用。作者自开会让“自动上线”绕过人工审核且无法追责。
- 影响面：管理员方案 = §7.2 的 `PATCH .../source` 一个鉴权分支 + `khatkit_hub_item_sources` 补 1 个操作者列，审批记录复用现有 `PlatformAuditLogTable`（不新建审批表）；作者自助方案额外引入权限矩阵、作者端开关页与滥用处置流程（客户端 +1 页、服务端 +1 权限校验点）。

### 决策 4：SVG 图标允许作者提交，还是统一由服务端从仓库 `icon.svg` 生成？
- 推荐：**允许作者提交，但必须服务端清理 + 重算 `icon_sha256` + 进审核**；仓库 `icon.svg` 仅作为导入时的默认生成源。
- 依据：T1 判定服务端 SVG 清理/托管/白名单「缺失」（`src/service/MediaService.kt:28`、`:45` 只有 MIME 痕迹，全仓无图标上传/托管代码）；而客户端 Coil 的 SVG 解码**已启用**（`app/src/main/java/heizige/kk/khatkit/app/KhatKitApp.kt:191` 的 `add(SvgDecoder.Factory(scaleToDensity = true))`）。作者提交是唯一能让 P1 独立于 P2 落地的方式；“服务端从 `icon.svg` 生成”会依赖 P2 的仓库读取管线，等于把 P1 挂到 P2 后面。
- 影响面：作者提交方案 = 服务端 3 个新文件（`src/helper/SvgSanitizer.kt`、`src/service/KhatKitIconService.kt`、`src/routing/KhatKitIconRouting.kt`）+ `khatkit_hub_items` 4 列 + 客户端 2 个文件（`HubIconFetcher.kt` 新增、`KhatKitApp.kt` 改动）。服务端生成方案还需额外 SVG 栅格化/渲染依赖，并让 P1 被 P2 阻塞。

### 决策 5：MCP 配置完成后是否自动启用当前助手？
- 推荐：**默认“仅保存配置”**（`enable = false`，不写 `assistant.mcpServers`）；用户显式点“启用”后再写入，并保留“安装但暂不启用”。
- 依据：实勘判定现状为安装即写入全局设置与当前助手且 `enable = true`（`app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketViewModel.kt:236-263`、`:284-293`），没有任何必填配置校验；这与 §2.1「缺配置不得伪装成已连接」直接冲突。默认不启用同时关掉该缺口。
- 影响面：客户端 2 个文件（`ExploreMarketViewModel.kt` 安装分支、`ExploreMarketPage.kt` 条目级入口）+ 1 个前端配置状态枚举（`not_configured | configured | connected | failed`）+ 约 8 个 `strings.xml` 键；服务端 0 改动（`install_state` 明确不落库，§7.1）。

## 11. 外部规范依据

- [MCP Registry overview](https://modelcontextprotocol.io/docs/registry)：官方 Registry 主要保存 MCP 元数据，包和二进制仍来自 npm、PyPI、Docker、GitHub Release 等来源。
- [MCP Registry server.json](https://github.com/modelcontextprotocol/registry/blob/main/docs/reference/server-json/generic-server-json.md)：服务器身份、仓库、包、运行时和配置的标准描述。
- [MCP Registry API](https://github.com/modelcontextprotocol/registry/blob/main/docs/reference/api/official-registry-api.md)：包含 `updated_since` 的增量列表能力。
- [GitHub repository contents API](https://docs.github.com/en/rest/repos/contents)：读取仓库文件和目录；大目录应使用 Git Tree API，下载 URL 需要按请求重新获取。
- [GitHub releases API](https://docs.github.com/en/rest/releases)：读取正式 Release、tag 和 Release asset。
- [GitHub webhooks](https://docs.github.com/en/webhooks/webhook-events-and-payloads)：push/release 事件和签名校验的官方依据。
