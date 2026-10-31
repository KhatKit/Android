# 上游同步矩阵 · 区间 b（2.5.5 → 3.1.1，第 63–124 行 / 共 62 个提交）

> 任务：t108（SYNC0 b）。只读产出，未改任何源码、未跑 gradle。
> 输入：`/tmp/sync-commits.txt` 第 63–124 行；每条的 `git show --numstat` 已实测（模块列来自真实改动路径）。

## 口径

**类型**：`bugfix` / `feature` / `refactor` / `build-size`（构建·依赖·体积·格式化门禁）/ `perf` / `style` / `docs` / `chore`。
本区间没有纯 i18n 提交——多语言字符串是跟着 feature 一起改的（`res:7` 那几行的 `values*/strings.xml`），所以 `i18n` 这一列在本批为空。

**可移植性**：
- **A｜直接移植**：非 UI 逻辑、构建/依赖、资源、数据层、ai/search/workspace 模块内部改动。路径能一一对应，风险主要是符号改名。
- **B｜需适配**：碰 UI 层（含结构/API/路由），逻辑可借鉴但组件要换成 Mochi/MD3，或与 fork 现有实现/弹层台账冲突。
- **C｜跳过**：已被本 fork 实现，或纯外观（MD3 Expressive 换肤、配色、动效、图标配色、格式化噪音、版本号）。

**路径映射（上游 → 本 fork）**：`app/src/main/java/me/rerere/rikkahub/ui/pages/*` → `app/feature/*`；`app/.../ui/components/*` → `app/core/ui/components/*`；`app/.../data/*` → `app/core/data/*`；`app/.../utils/*` → `app/core/util/*`；上游 `ui` 模块（`me.rerere.ui`）→ 本 fork `khatkit-ui`；上游 `web-ui` → 本 fork 已无前端资源（web 模块只留 Ktor 服务），相关改动按 C 处理。包名整体 `me.rerere.rikkahub` → `heizige.kk.khatkit.app`。

## 矩阵

| 序号 | hash | 标题 | 类型 | 可移植 | 模块 | 建议 |
|---:|---|---|---|---|---|---|
| 63 | `942e09453` | fix(build): 为 SnakeYAML 的 java.beans 补 R8 放行，修 release 构建失败 | bugfix | A | build / app keepRules | 整文件照搬；fork 若仍引 SnakeYAML 立即生效 |
| 64 | `ebbcf6377` | fix(provider): 连接测试推理等级改 AUTO，与聊天默认一致 | bugfix | A | app/feature/settings | 单行请求参数改动；先核对 fork 的 reasoning 枚举名 |
| 65 | `c04365d6f` | refactor(markdown): Markdown 渲染收敛到 ui 模块单一实现 | refactor | B | khatkit-ui / core/ui/components | 先确认 fork 的 richtext 是否已单一实现，是则降为 C |
| 66 | `e2de6b8cd` | feat(assistant): 记忆页拆「管理/设置」两 tab，抽通用 PagerTabs | feature | B | app/feature/assistant / core/ui/components | 只借 PagerTabs 结构；记忆页外观按 Mochi 重做 |
| 67 | `4c1a668f1` | feat(settings): 设置页/关于页改 MD3 Expressive | feature | C | app/feature/settings | 纯外观，fork 已换 Mochi×MD3 |
| 68 | `9c599ec6d` | feat(settings): 偏好设置各页/扩展页改 MD3 Expressive | feature | C | app/feature/settings | 纯外观 |
| 69 | `39b986dbc` | feat(extensions): 提示词注入页改 MD3 Expressive | feature | C | app/feature/extensions | 纯外观 |
| 70 | `4a70dd130` | feat(provider): 服务商列表/详情改 MD3 Expressive，新建只选类型 | feature | C | app/feature/settings | 外观 C；「新建只选类型」的流程可当参考 |
| 71 | `0966502c4` | feat(settings): 默认模型页改 MD3 Expressive | feature | C | app/feature/settings | 纯外观 |
| 72 | `05b655bb3` | feat(provider): 模型新增上下文长度配置（默认 1M，预设/自定义） | feature | A | app/core/data + ai | 数据字段与预设常量可直搬；选择控件换 Mochi |
| 73 | `c72fa6853` | feat(backup): 备份页/文件管理页改 MD3 Expressive | feature | C | app/feature/backup | 纯外观 |
| 74 | `68826514d` | feat(settings): 媒体设置页/语音设置页改 MD3 Expressive | feature | C | app/feature/settings | 纯外观 |
| 75 | `2e572c204` | fix(chat): 标题栏遮罩不再盖住背景图与动态渐变 | bugfix | C | app/feature/chat | 纯外观（fork 顶栏已 Mochi 化） |
| 76 | `06d59cc8c` | feat(extensions): 工作区/技能/快捷消息页改 MD3 Expressive | feature | C | app/feature/extensions | 纯外观 |
| 77 | `c94237eaa` | chore(deps): 移除不再使用的 Lucide 图标依赖 | build-size | A | build / libs.versions.toml | fork 已用 mochi-icons，确认无 lucide 引用即可 |
| 78 | `93f231149` | feat(translator): 翻译页改 MD3 Expressive，新增翻译历史 | feature | B | feature(translator) + app/core/data | 翻译历史的表结构/写入点可借鉴，页面重做 |
| 79 | `660571449` | feat(chat): 侧边栏搜索入口并入聊天历史一行 | feature | C | app/feature/chat | 布局外观，fork 侧栏已重写 |
| 80 | `923ad3d91` | fix(conversation): 加载消息节点不再吞协程取消异常 | bugfix | A | app/core/data | 单文件，直接搬；能防事务内无限循环 |
| 81 | `f6e3e49a4` | feat(settings): 新增自动生成标题开关 | feature | A | app/core/data(datastore) + service | 开关 + 判定逻辑直搬，界面只加一项设置 |
| 82 | `a6abed3be` | fix(markdown): 搜索引用角标改实色背景，修暗色看不清 | bugfix | C | core/ui/components | 纯配色/字重 |
| 83 | `b69331473` | feat(markdown): 引号内文字高亮开关 | feature | B | khatkit-ui 解析器 + app/core/data | 解析改动可搬；设置项与配色换 Mochi token |
| 84 | `927fdd51f` | feat(workspace): 工作区文件菜单新增复制 Rootfs 绝对路径 | feature | B | app/feature/workspace | 路径拼接逻辑可搬；菜单项换 Mochi 菜单 |
| 85 | `4061537da` | refactor(toast): 自研 Toaster 替换 sonner，修 toast 阻断输入 | refactor | C | khatkit-ui(Toast) | fork 已有 khatkit-ui Toast 体系，属已实现 |
| 86 | `7e2ec76c2` | refactor(ui): PagerTab 的 label 改 Composable getter，调试页用 PagerTabs | refactor | B | core/ui/components / khatkit-ui | API 形状可借鉴；与 66 同源，合批看 |
| 87 | `b8d309e66` | feat(mcp): MCP 配置从底部弹窗改独立详情页 + PagerTabs | feature | B | app/feature/settings + 路由 | 结构与路由可借鉴；与 fork 弹层台账冲突，需产品决策 |
| 88 | `fffa6e82e` | refactor(data): data 包重整（拆 skills、合并 remote） | refactor | B | app/core/data | 大规模搬移，建议等 chat 架构批之后 |
| 89 | `240deb5fe` | fix(openai): 流式响应 null 行/元素导致解码崩溃 | bugfix | A | ai | 自带单测，直接移植；本区间首选 |
| 90 | `9f4a651a5` | feat(icon): Wan 系列模型匹配 Qwen 图标 | feature | A | app/core/util | 纯匹配表小改 |
| 91 | `72747fc9e` | fix(service): 前台服务未及时 startForeground 崩溃 | bugfix | A | app/service | 并发时序修复，直接搬 |
| 92 | `ec48d0621` | feat(icon): 更新应用/通知图标，移除 DEV 角标与兔子动画 | feature | B | res + core/ui/components | 资源可换；兔子动画 fork 仍在用（RabbitLoadingIndicator），别一起删 |
| 93 | `d2dd1ed6a` | docs(readme): 移除 DeepWiki/zread 徽章，捐赠改 Ko-fi | docs | A | README | 与 fork 品牌无关，按需 |
| 94 | `cfb503d8c` | docs(readme): 换宣传海报、更新功能简介与赞助表 | docs | A | README + docs | 按需 |
| 95 | `bf8ff5a27` | fix(workspace): proot 升级 5.1.107.96（修 Ubuntu 26.04 基础命令） | bugfix | A | workspace(jniLibs) | 二进制整包替换；需核对 abi/哈希并跑 workspace 冒烟 |
| 96 | `90f91ce40` | feat(media-creation): 媒体创作支持多选/批量保存与删除 | feature | B | feature(media creation) | 批量选择逻辑可借鉴，列表 UI 重做 |
| 97 | `c91d21463` | fix(markdown): CRLF 换行时代码块结尾围栏识别 | bugfix | A | khatkit-ui 解析器 | 自带单测，直接移植；本区间首选 |
| 98 | `893e83f84` | feat(search): 新增 DeepSeek 搜索服务（Search+Fetch） | feature | A | search | 新 provider + 设置项，纯新增能力 |
| 99 | `e7f2c04f7` | fix(usage): 多轮工具调用 token 被覆盖、TPS 计入等待 | bugfix | A | ai + app/core/data/ai | 统计口径修复，跨模块但逻辑独立 |
| 100 | `7cceda23e` | feat(compress): 压缩改为末尾追加结构化总结指令 + 面板显示用量 | feature | B | app/feature/chat + core | 提示词策略可搬(A)；面板展示按 Mochi 重做 |
| 101 | `e10e10433` | refactor(ai): 多步生成循环/工具审批拆到 ai agent 包 | refactor | B | ai + app/di | 架构级，风险高；需整包同步再评估 |
| 102 | `9c5331d4a` | refactor(chat): 内存会话为唯一权威，删 GenerationLoop，拆 ChatRequestFactory | refactor | B | app/feature/chat + core/data | 架构级大改，和 101/107 同批 |
| 103 | `aae986e55` | feat(search): 搜索选择器对 Claude 显示内置搜索 | feature | B | core/ui/components(ai) | 选择器逻辑小改；控件换 Mochi |
| 104 | `aaa07fd52` | refactor(provider): 创建后不再允许切换类型 | refactor | B | app/feature/settings | 校验逻辑可搬 |
| 105 | `0ee642ff8` | fix(folder): 首条消息归入当前文件夹（App+Web 双端） | bugfix | A | app/service + web | 逻辑修复，双端一起搬 |
| 106 | `4ed5f38d9` | fix(folder): 移动未打开会话时同步内存中的文件夹 | bugfix | A | app/service | 单文件 |
| 107 | `e342909ac` | refactor(chat): 从 ChatService 拆出纯变换与标题/建议生成 | refactor | B | app/feature/chat + core/data | 架构级，跟 101/102 一批 |
| 108 | `83b1f3e9e` | feat(chat): 可编辑思考内容（带签名/加密的禁改） | feature | B | core/ui/components + hooks | 判定逻辑可搬；编辑器 UI 重做 |
| 109 | `c5ea8f419` | feat(media): 会话项容器变换展开、会话改独立卡片 | feature | C | feature(media creation) | 纯外观/动效 |
| 110 | `2825a4dd4` | chore: update deps | build-size | A | gradle | fork 版本已分叉，逐条挑选，不可整体合并 |
| 111 | `06eb60a7e` | perf(baselineprofile): 生成器接本地 mock 走完整生成 | perf | A | baselineprofile | 构建性能独立可搬 |
| 112 | `55a108a44` | chore: bump version to 3.0.0 | chore | C | app/build.gradle.kts | fork 版本号自管 |
| 113 | `9ce1ae153` | fix(chat): 矮屏上侧边栏顶部随列表滚动 | bugfix | B | app/feature/chat | 布局约束修复可借鉴；侧栏已重写 |
| 114 | `76e25d952` | chore(build): 接入 Spotless + ktlint | build-size | A | build-logic + AGENTS.md | 可评估；fork 目前没有格式化门禁 |
| 115 | `07b1238a8` | feat(voice): 语音模式新增「回复时继续聆听」 | feature | B | feature(speech/settings) | 状态机逻辑可搬；开关 UI 换 Mochi |
| 116 | `101d12721` | feat(memory): 新增「自动做梦」开关（默认开启） | feature | A | app/core/data + 记忆整理路径 | 开关 + 调度逻辑直搬 |
| 117 | `cf3305f62` | style: 用 Spotless 格式化 Message.kt 与 AgentLoopTest.kt | style | C | ai | 纯格式噪音 |
| 118 | `2862d6111` | build: 更新 spotless 配置 | build-size | C | build.gradle.kts | 依赖 114，一并不做 |
| 119 | `660b5d65f` | feat(chat): 统计行改显示步数/速度/上下文占用 | feature | B | ai(usage) + core/ui | 统计计算可搬；展示行重做 |
| 120 | `5a90c0548` | feat(stats): 新增 usage_record 流水表与统计页 | feature | B | app/core/data(db) + 统计页 | 表结构与写入点可搬(A)；统计页 UI 重做 |
| 121 | `bf04cde90` | feat(icon): 应用图标改深色背景浅色兔子 | feature | C | res | fork 图标已自管 |
| 122 | `45e9f8db6` | feat(chat): Markdown 图片支持引用工作区文件 | feature | B | core/ui/components + workspace | 路径解析/解码逻辑可搬(A) |
| 123 | `675e59f0d` | fix(ai): Responses API 解析 function_call_output 挂回工具 | bugfix | A | ai | 直接移植，避免重复执行服务端工具 |
| 124 | `b7b0a9253` | feat(memory): 记忆列表空占位与关闭提示 | feature | B | app/feature/assistant | 文案/跳转逻辑可搬；UI 换 Mochi |

**区间分布（实测计数）**：可移植 **A 22 / B 23 / C 17**（共 62）；类型 **feature 31 / bugfix 14 / refactor 8 / build-size 4 / docs 2 / perf 1 / chore 1 / style 1**。

**A 类 22 项（可直接开工的清单）**：63、64、72、77、80、81、89、90、91、93、94、95、97、98、99、105、106、110、111、114、116、123。

## 本区间最值得先做的 5 项（按性价比排序）

1. **`240deb5fe` fix(openai) 流式 null 行/元素解码崩溃**（A｜ai）
   纯 ai 模块单点修复、上游自带 `ChatCompletionsStreamDecoderNullTest`，与 fork 的 UI 迁移零冲突；现在 fork 只要遇到服务商返回 `data: null` 就会整段解码崩掉，属于线上稳定性缺口，照搬即可。

2. **`c91d21463` fix(markdown) CRLF 代码块围栏识别**（A｜khatkit-ui 解析器）
   纯 parser 逻辑 + 单测，改的是 `MarkdownParser`；fork 的 Markdown 渲染已经收敛到 khatkit-ui/core-ui 一侧，Windows 来源文本（CRLF）现在会漏识别结尾围栏、把后续正文吞进代码块，体感明显且修复面极小。

3. **`675e59f0d` fix(ai) Responses API 解析 function_call_output**（A｜ai）
   服务端已执行的工具结果没挂回对应 tool_call，客户端会重复执行同一工具（重复扣费/重复写文件）。ai 模块内闭环，带测试，风险低收益高。

4. **`923ad3d91` fix(conversation) 加载消息节点不再吞协程取消异常**（A｜app/core/data）
   单文件。取消异常被 `catch (e: Exception)` 吞掉会导致事务内重试循环、页面退不出去；fork 的 `ConversationRepository` 结构一致，可原样搬。

5. **`e7f2c04f7` fix(usage) 多轮工具调用 token/TPS 统计**（A｜ai + app/core/data/ai）
   token 被最后一步覆盖、TPS 把工具执行与审批等待算进生成时间——直接影响用量展示与用户对「慢」的判断。改动跨 ai 与 data/ai 两层但彼此独立，适合紧接着前 3 项做。

**紧随其后（下一批候选）**：`bf8ff5a27` proot 5.1.107.96（A，二进制，需跑 workspace 冒烟）、`893e83f84` DeepSeek 搜索 provider（A，纯新增）、`f6e3e49a4` 自动生成标题开关（A）、`101d12721` 自动做梦开关（A）、`2825a4dd4` 依赖更新（A，逐条挑）。

**明确不建议动**：`101/102/107`（ai agent 包 + chat 内存会话唯一权威）是三段一体的架构重构，与本 fork 已分叉的 ChatService/EditState 结构冲突最大，必须单独开一个大任务先读代码再定；`88`（data 包重整）同理，建议排在这批重构之后。
