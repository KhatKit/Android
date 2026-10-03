# KhatKitHub 后端对接（套餐 / 网关 / 定价 / 发布）

本文档记录 KhatKit 客户端与 KhatKitHub（`heizige.top`，可自定义）的对接方式。
客户端在 Hub 不可达时全部优雅降级：**只有服务端明确拒绝才拦截**，网络异常不阻止本地免费卡片运行。

## 1. 激活令牌与账户

- 入口：`设置 → 套餐 / 激活`（`SettingPackagePage`）
- 调用：`POST /api/activate { token, device_id, device_name }`
  - `device_id`：优先 `Settings.Secure.ANDROID_ID`，异常时退回持久化随机 UUID
  - `device_name`：`Build.MANUFACTURER + Build.MODEL`
- 保存：激活令牌与会话密钥（`session_key`）写入 **Keystore AES/GCM 加密的 store 密钥通道**
  （`HubAccountStore` → `FileStoreBridge.secretSet`），明文不落盘；额度缓存进普通 SharedPreferences
- 查询：`GET /api/me`（`Authorization: Bearer <激活令牌或 session_key>`），展示
  AI 令牌剩余、工具调用剩余、套餐名与到期时间；失败时展示缓存额度并给出中文错误
- 清除：仅清除本机令牌与缓存，不影响服务端账户

## 2. AI 网关供应商

- 端点：`<Hub 地址>/v1`，OpenAI 兼容（`/v1/chat/completions`、`/v1/models`），网关按 `usage` 扣减 AI 令牌
- 创建方式（默认不启用、不自动选中）：
  1. `设置 → 套餐 / 激活` 激活后点「创建 / 更新网关供应商」；
     `baseUrl = <Hub>/v1`，`apiKey = 激活令牌`，`enabled = false`
  2. 或在「服务商 → 右上角推荐」中手动添加预设 **KhatKit 套餐网关**（同样默认不启用）
- 使用前需在「服务商」里为它添加模型（如上游支持的模型名）并手动开启

## 3. 卡片定价字段（card.json）

```json
{
  "name": "paid_tool",
  "pricing": { "price": 0.2, "currency": "CNY" }
}
```

- `pricing` 可选，缺省 = 免费；`price` 单位是**元**，必须 >= 0；`currency` 不能为空
- 校验器错误码：`PRICING_PRICE_INVALID`、`PRICING_CURRENCY_INVALID`
- 客户端请求 Hub 时换算成**分**（`price * 100`），与后端 `price_per_call` 一致

## 4. 工具调用计量（失败开放）

卡片运行统一入口 `runCardWithStatus`：

1. 本机存在激活令牌时，运行前调用 `POST /api/tools/authorize { card_name, price }`（限时 4s）
   - 服务端 `allow=false` / HTTP 401 / 402 / 403 / 429 → **明确拒绝**：
     返回中文错误「套餐工具次数不足/令牌无效：…」且不执行卡片
   - 网络错误 / 超时 / 404 / 5xx / 无法解析 → **失败开放**：照常本地运行并打日志
2. 运行结束后 best-effort 上报 `POST /api/tools/report { card_name, ok }`，失败只记日志

## 5. 发布 / fork / 投票

| 操作 | 入口 | 接口 |
| --- | --- | --- |
| 发布 | 市场卡片「发布到 Hub」（需勾选开源协议确认 + 每次调用价格/元 + 更新说明） | `POST /api/cards/publish` |
| 提交改进 | 市场卡片「提交改进」（父版本 + 新版本号，默认补丁位 +1 + 更新说明必填） | `POST /api/cards/{name}/fork` |
| 投票 | 市场卡片「投票」（点亮 upvote 优选评分） | `POST /api/cards/{name}/vote` |

- 未在 Hub 上架的本地卡片（含「会话存成卡片」生成的卡片）会出现在市场「本机卡片」区，同样可发布 / 提交改进
- 市场详情通过 `GET /api/cards` 展示价格（分 → 元）、开源协议、优选与票数徽标
- 发布上传字段：`name/version/description/script/manifest_json/price_per_call/license/changelog`；
  `license` 必须是后端认可的开源协议（MIT、Apache-2.0、GPL-3.0、open-source 等）
- 发布 / fork 的新版本默认进入审核（`pending`），审核通过后可能成为首选版本

## 6. 统一扩展市场（本地已实现，尚未部署）

KodeHeapServer 新增 `/api/items` 协议；旧卡片发布、fork、投票、计量与
`/api/cards` 响应保持兼容。完整服务端契约见其 `docs/extension-market.md`。
客户端新 kind 入口仍须在服务端上线且安装链路验证后启用。

| 接口 | 语义 |
| --- | --- |
| `GET /api/items?kind=&q=&app_version=&limit=&offset=` | 已审核条目，名称/本地化名称/描述/标签检索；默认 50 条，最多 100 条 |
| `GET /api/items/{id}` | 版本详情，包括权限报告、文件哈希、依赖与兼容性字段 |
| `GET /api/items/{id}/download` | 原 ZIP；响应 `X-Content-SHA256`，客户端必须复验 |
| `POST /api/items/publish` | 现有登录/激活凭证；所有新 kind 首发也进入 pending |
| `POST /api/items/{id}/vote` | 现有投票身份；幂等投票与 >20% 分数接管 |
| `GET /api/admin/items/pending` | admin/developer 审核队列 |
| `POST /api/admin/items/{id}/approve`、`reject` | 审核与审计记录；拒绝后不再公开下载 |

kind 为 `card/skill/mcp/prompt/workflow/dependency/provider_plugin`。
ToolPkg 是归档格式，以 manifest 的 `toolpkg_id` 识别；不增加 `resource`
品类。形象/TTS 资源走 dependency。卡片发布继续使用旧接口；统一列表读取旧
卡片首选版本，不迁移或重写旧表。ID 是不透明字符串，新条目暂为十进制 ID，
卡片版本为 `card-<版本ID>`。

发布 JSON：`name/kind/version/display_name/description/license/changelog`，
`manifest_json`、`artifact_base64`（完整 ZIP）、可选 `sha256/min_app_version`，
以及必填 `audit_report_json/audit_score/audit_rules_version`。
`display_name` 支持字符串或语言映射。`pricing_json` 仅支持 `{}` 或
`{"free":true}`，新扩展首期免费。ZIP 根目录的 `manifest.json` 必须与提交值
一致；8MiB 压缩/32MiB 展开/1024 条目上限，拒绝重名与路径穿越。

ToolPkg/Provider 插件声明 `toolpkg_id/schema_version=1/version/api_version`，
入口 `entry`（默认 `main.js`）必须存在。`api_version` 与
`requires:[{id,min_version?,max_version?}]` 从归档 manifest 提取，发布后不可
修改；依赖范围为含边界 SemVer，存在性和递归安装由客户端负责。

审计报告是 JSON 对象，要求 `artifact_sha256/audit_rules_version/score` 与本版
归档和提交元数据一致，并包含 `permissions/network_domains/dangerous_calls/hooks`
数组。hooks 限定 `pre_tool/post_tool/pre_stream/post_stream/on_error`。
每个版本重新提交并保留旧报告；当前为发布者/CI 生成、服务端存证，尚无服务端
静态复检。旧卡片允许没有报告，客户端不能把空报告渲染为“已安全审计”。

列表返回 `{"items":[...]}`，详情含 `id/name/kind/version/display_name/description/
author/license/changelog/sha256/status/created_at/updated_at`，
`pricing` 对象、`files/requires` 数组、`audit_report` 对象、
`audit_score/audit_rules_version/min_app_version/api_version/tags/upvotes/score/preferred`；
详情另含 `manifest_json` 及字符串形式的 `pricing_json/files_json/requires_json`。

`min_app_version` 可空；旧卡片从 manifest 读取。请求 `app_version` 非法返回
400；不传不做兼容过滤，传入则隐藏不兼容项。SemVer 按数字比较 prerelease
段，正式版本高于预发布，忽略 build metadata，拒绝前导零和不完整版本。
有过滤参数时，非法存量最低版本不放行。

本地验证：服务端 `./kotlin test --include-classes='*Market*Test'`，涵盖六种新
kind 的发布审核与下载校验、旧卡片回归、审计时效和 SemVer。尚未完成客户端
六类安装验收、生产 PostgreSQL 验证和部署；本节不表示整个路线已交付。
