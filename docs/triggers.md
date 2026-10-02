# KhatKit 自动化触发器（events）

> 本文是快速说明；完整字段、匹配语义、冷却、重试与自动化审批请以
> [卡片脚本开发文档](lua-card-development.md)（第 2.9、5 节）为准。

卡片除了被 AI 调用（`triggers: ["ai"]`）或用户在卡片市场手动运行（`triggers: ["user"]`）外，还可以声明 `events` 在事件发生时自动运行。总开关在「设置 → 自动化触发器」，支持逐卡启用/停用。

## 1. 声明方式

```json
{
  "name": "sms_hook",
  "version": "1.0.0",
  "engine": "lua",
  "entry": { "lua": "main.lua" },
  "requires": { "bridges": ["tool"] },
  "tags": { "domain": "system", "action": "monitor" },
  "events": [
    { "type": "schedule", "times": ["08:00", "21:30"], "days": [1, 2, 3, 4, 5] },
    { "type": "schedule", "intervalMinutes": 30 },
    { "type": "notification", "package": "com.tencent.mm", "titleContains": "红包", "textContains": "点击领取" },
    { "type": "app_launch", "package": "com.tencent.mm" },
    { "type": "charging", "state": "disconnected" }
  ]
}
```

校验规则（`card-validator` CI 强制，完整 18 种类型见 [卡片脚本开发文档](lua-card-development.md) 2.9）：

- `type` 只允许这 18 种：`schedule` / `notification` / `notification_click` / `notification_reply` / `app_launch` / `app_exit` / `app_install` / `app_uninstall` / `charging` / `wifi` / `network` / `battery` / `screen` / `clipboard` / `bluetooth` / `location` / `shortcut` / `tile`。
- `schedule`：必须提供 `times`（严格 `HH:mm`）或 `intervalMinutes`（>=1）；`days` 取值 1..7（1=周一），缺省每天；`calendar` 可取 `any|workday|weekend|holiday`。
- `notification` / `notification_click`：`package` 可为空 = 任意应用；但 `package` / `titleContains` / `textContains` 不能全是空。
- `notification_reply`：`package` 必填（要监听回复的应用）。
- `app_launch` / `app_install` / `app_uninstall`：`package` 可为空 = 任意应用。
- `app_exit`：`package` 必填（防抖确认后仍未回前台才派发）。
- `shortcut` / `tile`：`name` 必填（显示名称），同一卡片可声明多条，用 `name` 区分。
- `charging`：`state` 必须是 `connected` / `disconnected`。

## 2. 运行参数

宿主把事件负载放在 `args.event`：

| 字段 | 出现于 | 说明 |
|---|---|---|
| `type` | 全部 | 事件类型 |
| `package` | 通知类 / app_* | 包名 |
| `title` / `text` | notification / notification_click / notification_reply | 通知标题 / 正文（回复事件为启发式还原的文本） |
| `state` | charging / wifi / network / battery / screen / bluetooth / location | 状态值，见 2.9 各行 |
| `time` | schedule | 触发时的 `HH:mm` |
| `level` | battery | 触发时的电量百分比 |
| `ssid` | wifi | 触发时的热点名 |
| `device` | bluetooth | 触发时的设备名 |
| `lat` / `lon` | location | 触发时的坐标 |
| `name` | shortcut / tile | 该条事件声明的显示名称 |

```lua
local event = args.event or {}
if event.type == "notification" and event.text then
  -- 处理通知正文
end
return { handled = true }
```

## 3. 宿主行为

- `TriggerService` 前台服务：每 60 秒 tick 一次定时事件（分钟级），每 ~1 秒轮询无障碍前台包名（需要开启 KhatKit 无障碍服务），并监听充电广播。
- `KhatKitNotificationListenerService`：需要用户在系统设置中授予「通知使用权」，否则通知事件不工作。
- 开机 / 应用升级后由 `TriggerBootReceiver` 自动重新拉起服务。
- 冷却：同一卡片同类型事件，通知 3 秒、应用启动 60 秒、充电 5 秒；定时按分钟去重。
- 通知点击 / 回复依赖 `KhatKitNotificationListenerService`；安装 / 卸载依赖系统 `PACKAGE_*` 广播；快捷方式 / 磁贴由宿主为每条声明各发布一枚（设置里可禁用单条事件）。
- 卡片运行走与 AI 调用相同的 `CardExecutor` / `CardRunManager`（同样的 bridge 注入与并发上限）。失败只记录日志并发送「卡片自动触发失败」通知，不会打断宿主。
