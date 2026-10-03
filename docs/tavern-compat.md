# 酒馆兼容子集（A5 / P1-4）

兼容范围写死为下面四项。超出字段导入保留、导出原样回写，不解释。不对接用户自建 SillyTavern 服务器（L5）。群聊语义归 C1，角色卡市场归 B2。

## 角色卡

- `chara_card_v2` / `chara_card_v3`
- 原文存在 `Assistant.tavernCardJson`，导出 JSON / PNG 时回写
- PNG：`chara` 保留原文；`ccv3` 是同一份 JSON，但 `spec` 写成 `chara_card_v3`、`spec_version` 写成 `3.0`。读取优先 `ccv3`。这是 SillyTavern `character-card-parser.js` 的布局，本地重写，不复制源码
- 解释字段：name、description、personality、scenario、system_prompt、first_mes

## 宏 v1

`{{user}}` `{{char}}` `{{random:a|b}}` `{{roll:1d6}}` `{{lastMessage}}` `{{time}}`

另外接受 SillyTavern 写法：`{{random:a,b}}`、`{{random::a::b}}`、`{{roll:d6}}`。

未识别宏保留原文。展开发生在系统提示、lorebook 内容和消息组装，不走 Pebble。

`{{lastMessage}}` 取最近一条非系统消息之前的那条聊天文本。

## lorebook

主关键字 OR。`selective` 开启后次关键字按 SillyTavern `selectiveLogic`：

| 值 | 逻辑 |
|---|---|
| 0 | AND_ANY |
| 1 | NOT_ALL |
| 2 | NOT_ANY |
| 3 | AND_ALL |

插入位置：`before_char` / `0`、`after_char` / `1`、`at_depth` / `4`。酒馆把概率、深度、位置和递归开关放在条目的 `extensions` 里，解析时两边都读。递归、概率、优先级参与激活。概率为 0–99 的掷骰，小于百分比才命中，所以 0 永不触发、100 必定触发。

## 聊天与模板

- 聊天：SillyTavern 磁盘格式是 JSONL，首行无 `mes` 为元数据，之后每行一条消息。JSON 数组同样可读。`swipes` → `MessageNode` 分支，`swipe_id` 为选中下标。`alternate_greetings` 是首条消息的 swipe，不是额外轮次
- instruct：`system/input/output` 的 sequence + suffix，`wrap` 控制是否包裹；顺序为系统、用户、助手
- sampler：`temperature` / `top_p` / `top_k` / `max_tokens` 写入助手，其余字段透传
