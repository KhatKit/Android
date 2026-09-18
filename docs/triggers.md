# KhatKit 自动化触发器（events）

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

校验规则（`card-validator` CI 强制）：

- `type` 只允许 `schedule` / `notification` / `app_launch` / `charging`。
- `schedule`：必须提供 `times`（严格 `HH:mm`）或 `intervalMinutes`（>=1）；`days` 取值 1..7（1=周一），缺省每天。
- `notification`：`package` 可为空 = 任意应用；但 `package` / `titleContains` / `textContains` 不能全是空。
- `app_launch`：`package` 可为空 = 任意应用进入前台。
- `charging`：`state` 必须是 `connected` / `disconnected`。

## 2. 运行参数

宿主把事件负载放在 `args.event`：

| 字段 | 出现于 | 说明 |
|---|---|---|
| `type` | 全部 | 事件类型 |
| `package` | notification / app_launch | 包名 |
| `title` / `text` | notification | 通知标题 / 正文 |
| `state` | charging | connected / disconnected |
| `time` | schedule | 触发时的 `HH:mm` |

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
- 卡片运行走与 AI 调用相同的 `CardExecutor` / `CardRunManager`（同样的 bridge 注入与并发上限）。失败只记录日志并发送「卡片自动触发失败」通知，不会打断宿主。
