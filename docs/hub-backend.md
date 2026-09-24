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
