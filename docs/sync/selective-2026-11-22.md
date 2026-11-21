# 上游选择性同步：2026-11-22

## 比对范围

- 上游仓库：rikkahub/rikkahub，master 当前为 a2479770a3db9b5c092c549e62e046129f2d7628（2026-11-21）。
- 本仓库基线：9ebccfbef（导航防重复修复）；上次集中同步为 724156e57（2026-11-13，3.2.0 改进）。
- 本次逐项移植功能逻辑并适配本 fork 的包结构、设置和 Mochi UI，没有合并上游分支。

## 本批落地

| 功能 | 上游来源 | 本地适配 |
|---|---|---|
| DeepSeek 导出 ZIP 导入 | f93764e6a；附件文件名修复 a8508e232 | 只导入当前分支，保留思考、搜索引用和附件；归入当前助手，稳定会话 ID 跳过重复导入；缺失附件保留文件名并在完成对话框报告数量。 |
| 助手回复按段落分气泡 | cfb7d9613 | 消息显示设置新增开关，默认关闭；开启后绕过整条回复的外层卡片，用现有 KkSurface 绘制各段；代码围栏、块公式和列表缩进续段保留。设置搜索可定位该开关。 |
| 商汤日日新和 NVIDIA 模型图标 | 1ed0a77cc | 新增 SenseNova/SenseTime/SenseChat/商汤/日日新匹配及资源；Nemotron/英伟达复用本仓库现有 nvidia.svg。 |

DeepSeek 的导入逻辑放在 feature/backup/helper/DeepSeekImportHelper.kt，结果与附件模型放在 feature/backup/data/。附件使用现有 FilesManager，聊天写入 ConversationRepository；不添加数据库表或依赖。

段落拆分只改变显示，不重写消息树、编辑/复制/重新生成的数据。流式生成只对最后一个段落开启新增字符动画，继续沿用本仓库 Kk 动画和颜色。

新增导入入口复用 Mochi CardGroup，段落开关复用现有 Mochi 设置行，各段气泡复用 KkSurface。导入结果使用原生 MochiDialog + Primary 动作按钮，visible 驱动显隐并保留结果内容至退场结束。

本批只增加默认语言的功能文案，没有批量翻译，也没有调整应用版本。

## 其余近期功能的取舍

| 上游改动 | 本批决定及原因 |
|---|---|
| 工作区挂载共享存储目录（fab0c077d） | 暂缓。涉及数据库、存储权限和终端路径；本 fork 的终端/插件结构不同，应单独设计迁移及真机测试。 |
| DeepSeek TTS/ASR（f361bbc11） | 暂缓。需核对 speech 配置持久化、提供商表单及实际服务鉴权；本批不扩大到语音协议。 |
| 快速入门页（41d77f1ed） | 暂缓。本 fork 已有自定义引导和首页，避免新增一套重叠入口。 |
| 消息概览标记上下文压缩点（63a5192d8） | 暂缓。需要接入本 fork 的对话概览与群聊分组，单独核对检查点索引后再做。 |
| 允许删除内置默认助手（ce0e3607a） | 暂缓。助手初始化、角色隔离和默认助手引用已分叉，不能只放开删除按钮。 |
| Gemini 独立 thoughtSignature（2898c3dea）及 Vertex Interactions（90ef90b76） | 暂缓。属于请求协议变更，需单独导入上游 trace 回放测试，避免和展示/迁移功能混合。 |
| 自动压缩默认开启（66149047e） | 暂缓。会改变既有聊天的压缩行为与请求成本，保留本 fork 的配置。 |
| safe mode 大设置清理（27976e4da）、message_node 复合索引（c1c3c4d12） | 暂缓。应与本 fork 当前设置恢复和数据库迁移版本单独核对。 |
| BetterText 依赖、应用版本和上游发布工作流 | 不同步。本 fork 的文本渲染、版本号和 CI 自行维护。 |

## 验证

新增 DeepSeek 合成导出测试覆盖当前分支选择、思考/服务端工具/引用、图片和文档、含 # 文件名、缺失附件、非法分支图、空会话和重复导入。使用合成 ZIP，无真实聊天数据。

新增 Markdown 段落测试覆盖普通空行、代码围栏、较长围栏、块公式、列表续段及流式未闭合代码块；图标测试覆盖新别名和既有映射。

验证结果：

- 定向 JVM 回归测试 **34/34 通过，0 跳过**：DeepSeek 7、Markdown 段落 12、图标 3、Chatbox 既有导入 1、导航 6、LegacyImportGuard 5。
- 最终版本执行 :app:assembleDebug，构建成功；段落纵向容器调整后已重新构建。
- git diff --check 通过。
- ADB 真机回归通过：vivo V2166BA / Android 13，独立测试包 heizige.kk.khatkit.debug.navverify；未卸载或清理原应用。
- 合成 DeepSeek v3 ZIP 首次导入为 1 / 0 / 0 / 1（导入 / 已存在 / 空会话 / 缺失附件），重复导入为 0 / 1 / 0 / 0。
- 真机会话显示当前分支正文，未出现旧分支标记；思考可展开，搜索工具及引用条目正常，含 # 的文档附件与缺失附件占位正常显示。
- 分段开关开启后各段独立成泡，无重叠；代码块内空行未拆分；重启后设置保留；关闭助手气泡时分段开关不可点。测试后恢复默认关闭分段。
- 原生 Mochi 结果弹窗、主色胶囊确认按钮及关闭交互已在最终测试 APK 上复查。测试包 crash 缓冲无匹配记录，当前进程日志无 FATAL EXCEPTION / ANR。
- 真机证据位于忽略目录 artifacts/upstream-device/（包括 13-import-result.xml、15-repeat-result.xml、20-chat-single-bubble.png、25-chat-split.png、29-split-persisted.xml、30-bubbles-disabled.xml、36-mochi-result.png、37-dialog-dismissed.xml）；最终 Mochi 构建及 34 项定向回归日志为 artifacts/upstream-device/mochi-validation.log。
- 本次真机使用离线合成数据，未调用真实 AI 提供商接口。
- 构建和测试日志保存在忽略目录 artifacts/upstream-selective-validation.log 与 artifacts/upstream-selective-final-build.log。
