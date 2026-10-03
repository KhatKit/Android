# C1 群聊验收记录

本文件是 C1 的证据登记表，不是测试计划。没有可复现的自动化或真机原始证据时，
状态必须保持 `unverified`，不能因 UI 截图或代码存在而改为通过。记录格式遵循
`beyond-operit-client-changes.md` 的 C1 实施契约。

2026-10-04 交接：内核和一版 UI 已在仓库里，十例仍全部 `unverified`。
下一位从 `beyond-operit-implementation-status.md` 的「C1 交给下一位」接着写，
不要把已有 `GroupChat.kt` / `GroupChatPage` 当成验收通过。

## 用例矩阵

| ID | 用例 | 必须断言 | 状态 |
|---|---|---|---|
| C1-01 | 三角色显式 @ | 只被 @ 角色收到该消息；其他角色不可见 | `unverified` |
| C1-02 | 无 @ 的 pipeline | 按 `roles[]` 顺序串接；实际模型调用序列与日志一致 | `unverified` |
| C1-03 | roundtable | 全员完成后仅 `chair_role_id` 汇总；议长前看不到未完成输出 | `unverified` |
| C1-04 | vote | 仅接受结构化候选/票；多数决可复算，平票按配置失败 | `unverified` |
| C1-05 | 轮次预算 | prompt+completion 达上限后停止剩余角色；日志含已用/上限/未运行角色 | `unverified` |
| C1-06 | 取消与超时 | 不写入未生成消息；已生成消息和错误节点保留 | `unverified` |
| C1-07 | 失败续跑/幂等 | 同 `round_id` 重试跳过已提交 turn；不重复消息 | `unverified` |
| C1-08 | 记忆隔离 | 三个 `group:<conversationId>:role:<roleId>` 空间互不串；来源带消息 id | `unverified` |
| C1-09 | Tavern/QR 往返 | 群配置、角色卡、role/轮次/分支哈希一致；不含密钥、记忆、授权 token | `unverified` |
| C1-10 | 单聊/群聊共存 | 同一列表混排；类型筛选只过滤；切换后消息与会话数据不丢 | `unverified` |

## 证据登记

每个用例至少填写一行；多设备或多实现结果追加行，不覆盖历史证据。

| 用例 | commit | 命令/退出码 | 设备/Android | 输入或 fixture | viewer 可见消息 ID | 模型调用序列 | token (prompt+completion) | 导出 SHA-256 | 证据路径 | 状态 |
|---|---|---|---|---|---|---|---:|---|---|---|
| C1-01…C1-10 | — | — | — | — | — | — | — | — | — | `unverified` |

原始日志、JSONL 运行记录、截图/录屏和导出文件放在未提交的大文件目录或 CI artifact；
此表只登记稳定路径与哈希，避免把隐私消息、密钥、记忆内容提交进仓库。提交前执行
脱敏检查，并确认 QR payload 不含工具授权 token。

## 判定规则

- JVM 纯函数测试可证明过滤、路由、预算和幂等逻辑；不能替代真机 UI、备份恢复或
  Tavern 本体互操作证据。
- `unverified`、缺命令退出码、缺可见消息集合、缺调用序列或缺哈希的行均不算通过。
- 任一安全约束失败（越权消息、回退到全局记忆、导出密钥、重复提交）直接阻断该
  子包交付，先修复再重新记录。
