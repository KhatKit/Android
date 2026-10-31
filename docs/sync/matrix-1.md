# SYNC0-a：上游 2.5.5 → 3.1.1 移植矩阵（#1–#62）

范围：`/tmp/sync-commits.txt` 第 1–62 行（上游 186 个提交的前 1/3）。
判定口径：A=直接移植（非 UI 逻辑/构建/资源/数据层）；B=需适配（触碰 UI 但逻辑可借鉴）；C=跳过（fork 已实现或 UI 外观已用 Mochi×MD3 重做）。
fork 事实：包名 `heizige.kk.khatkit.*`；Kedge/Miuix/Khromia 已移除；外观层统一 Mochi×MD3。

| # | hash | 标题 | 类型 | 可移植 | 模块 | 建议 |
|---:|---|---|---|---|---|---|
| 1 | `eaa003add` | feat(shortcut): 新增图片生成快捷方式 | feature | C | app | 跳过：UI 外观/布局类，fork 已用 Mochi×MD3 重做 |
| 2 | `280a039cf` | chore: 统一换行符为 LF | refactor | C | app | 跳过：UI 外观/布局类，fork 已用 Mochi×MD3 重做 |
| 3 | `00c8d53a5` | feat(tools): 新增 chart_display 本地工具，支持在聊天中展示折线图、柱状图和散点图 | feature | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 4 | `95fed05e9` | fix(chat): fork 对话标题递增已有序号，避免叠加成 xxx(1)(1) | bugfix | B | app | 读 diff 取逻辑，按 fork 的 Mochi×MD3 组件重写 UI 触点 |
| 5 | `d21265cb4` | chore(deps): MCP SDK 切换为 rikkahub fork 0.15.0-rikka.2 | build-size | A | build | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 6 | `b2525e7be` | feat(model-list): 模型列表支持按供应商折叠，折叠状态持久化 | feature | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 7 | `b2d73a657` | feat(export): 导出对话支持不包含思考过程 (#1966) | feature | B | app | 读 diff 取逻辑，按 fork 的 Mochi×MD3 组件重写 UI 触点 |
| 8 | `3a9ae690b` | feat(model-registry): 注册 Gemini 4，推理参数使用 thinkingLevel | feature | A | ai | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 9 | `3443dd455` | feat(model-registry): 注册 Claude Opus/Sonnet 5.5，移除 Claude 3 | feature | A | ai | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 10 | `9435b8a2e` | docs: update skills | docs | B | other | 读 diff 取逻辑，按 fork 的 Mochi×MD3 组件重写 UI 触点 |
| 11 | `2cd62ad20` | feat(google): 支持 Gemini Interactions API | feature | A | ai/app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 12 | `620e38ccb` | fix(log): 修复请求日志中 JSON 字符串弹窗长按闪退 (#1977) | bugfix | B | app | 读 diff 取逻辑，按 fork 的 Mochi×MD3 组件重写 UI 触点 |
| 13 | `d5f0039ba` | feat(chat): 生成标题时找不到快速模型给出错误提示 | feature | B | app | 读 diff 取逻辑，按 fork 的 Mochi×MD3 组件重写 UI 触点 |
| 14 | `cf79246bf` | fix(mcp): 内联展开工具 inputSchema 的 $ref，修复 $defs 丢失导致请求 400 (#1974) | bugfix | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 15 | `7a53065a8` | fix(mcp): OAuth 回调地址改用 localhost，修复动态客户端注册被 WAF 拦截 403 (#1972) | bugfix | A | app/oauth | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 16 | `282b85f2d` | feat(assistant): 复制助手时支持同时复制记忆 | feature | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 17 | `654a5e1f7` | feat(provider): 提供商新增高级设置 tab，支持自定义请求头 (#1952) | feature | A | ai/app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 18 | `447bb7e89` | chore: bump version to 2.5.6 | refactor | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 19 | `4ba5d79f2` | fix(mcp): 受保护资源元数据缺失时退回服务器 origin 作为授权服务器，并在首次 401 时触发 OAuth (#1979) | bugfix | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 20 | `b8e0fec4b` | feat(mcp): 支持 Client ID Metadata Document 作为无动态注册时的 client_id，resource 根路径不带尾部斜杠 (#1979) | feature | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 21 | `2d5c51bd5` | fix(log): 修复复制过大的请求体导致剪贴板事务过大闪退 (#1980) | bugfix | B | app | 读 diff 取逻辑，按 fork 的 Mochi×MD3 组件重写 UI 触点 |
| 22 | `4e4bfa62b` | fix(imggen): 修复 OpenAI 图片生成自定义分辨率被误丢弃 (#1978) | bugfix | A | ai | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 23 | `a26d1d55f` | feat(mediagen): videogen 重构为图像与视频统一的 mediagen 模块，接入 OpenAI / 火山 / 阿里图像生成 | feature | A | app/build | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 24 | `2267943a3` | feat(mediagen): 图像与视频统一的媒体生成模块，S3 客户端支持签发临时下载地址 (#1984) | feature | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 25 | `a6dbb8cd2` | fix(openai): Response API 函数工具显式关闭 strict，修复可选字段被强制填充导致工具参数校验失败 (#1990) | bugfix | A | ai | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 26 | `298e2771e` | feat(workspace): 支持 Alpine rootfs，系统提示告知实际 shell 与发行版 (#1994) | feature | A | app/workspace | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 27 | `85af5b910` | fix(imggen): 修复 OpenRouter 图片编辑 404，以及模型名含斜杠时相册图片丢失 (#1993) | bugfix | A | ai/app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 28 | `0450c4c19` | feat: 媒体生成功能 (#1996) | feature | B | app | 读 diff 取逻辑，按 fork 的 Mochi×MD3 组件重写 UI 触点 |
| 29 | `80c6b07de` | feat(reasoning): 思考深度选择器改为 MD3 Expressive 风格 | feature | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 30 | `645a5d889` | feat(search): 搜索选择器改为 MD3 Expressive 风格 | feature | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 31 | `97060dd51` | fix(chat): 输入栏底部加渐变遮罩，避免消息透到系统导航栏 | bugfix | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 32 | `c211c0d44` | fix(search): 搜索结果不再被工具输出截断，修复开启工作区后结果显示为空 (#2000) | bugfix | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 33 | `bf614d89a` | feat(chat): 压缩上下文改为插入摘要检查点，不再删除历史消息 | feature | A | ai/app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 34 | `baa4f8364` | feat(chat): 加号面板改为 MD3 Expressive 风格，MCP 与扩展改为面板内切换 | feature | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 35 | `af28eaed4` | feat(mediacreation): 媒体创作界面改为 MD3 Expressive 风格，会话列表显示封面 | feature | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 36 | `77d37c7d6` | feat(chat): 聊天消息改为 MD3 Expressive 风格 | feature | B | app | 读 diff 取逻辑，按 fork 的 Mochi×MD3 组件重写 UI 触点 |
| 37 | `2b0d882d5` | feat(chat): 搜索与思考深度选择器改为更紧凑的布局 | feature | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 38 | `db81bb6c0` | feat(theme): 自定义主题改用 2025 色彩规范生成配色，更新 material-color-utilities | feature | C | app/material3 | 跳过：UI 外观/布局类，fork 已用 Mochi×MD3 重做 |
| 39 | `d1c81bf99` | perf(settings): LocalSettings 改用 compositionLocalOf，设置变化时不再重组整棵界面树 | build-size | B | app | 读 diff 取逻辑，按 fork 的 Mochi×MD3 组件重写 UI 触点 |
| 40 | `8c1b7d949` | feat(chat): 扩展选择面板改为 MD3 Expressive 风格 - 标签栏换成可横向滚动的切换按钮，选中项带图标，滑动翻页时自动滚到选中项 - 列表换成分段列表，整行可点击切换，描述最多显示 3 行 - 空状态换成带形状的图标和 tonal 按钮，管理按钮改为居中的 tonal 按钮 - 快捷消息页补上管理按钮 - 助手设置的扩展页共用这套列表，补上边距和容器色 | feature | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 41 | `9be5e5aa6` | feat(sketch): 新增画板，聊天和媒体创作可以手绘草图或在图片上圈画 | feature | A | app/build | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 42 | `d7f240d7e` | feat(sketch): 画板接入 androidx.ink，支持压感、低延迟落笔和多种笔刷 | feature | B | build | 读 diff 取逻辑，按 fork 的 Mochi×MD3 组件重写 UI 触点 |
| 43 | `19a5e2310` | feat(sketch): 画板新增文字工具和选择工具 | feature | B | build | 读 diff 取逻辑，按 fork 的 Mochi×MD3 组件重写 UI 触点 |
| 44 | `7c0113173` | refactor(ui): 把不依赖 app 逻辑的通用组件下沉到 ui 模块 | refactor | A | app/build | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 45 | `1c8c3fd37` | feat(imggen): 图片生成页显示即将废弃的警告，推荐改用媒体创作 | feature | B | app | 读 diff 取逻辑，按 fork 的 Mochi×MD3 组件重写 UI 触点 |
| 46 | `b64beeef6` | feat(chat): 会话开始后把模型、思考级别、搜索等配置固定在会话上 | feature | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 47 | `35fbf61d5` | feat(chat): 会话列表支持按创建时间排序 | feature | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 48 | `4a7a39c47` | fix(chat): 删除模型或编辑消息后，AI 消息仍显示由哪个模型生成 | bugfix | A | ai/app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 49 | `640496127` | perf(settings): 设置解码移出主线程，启动后不再整页重组 | build-size | A | app/search | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 50 | `735ade8a8` | refactor(settings): 把 PreferencesStore.kt 按职责拆成多个文件 | refactor | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 51 | `3ae5c1612` | ci: 每日构建改用 JetBrains Runtime 21，不再依赖 foojay 下载 JDK | refactor | B | other | 读 diff 取逻辑，按 fork 的 Mochi×MD3 组件重写 UI 触点 |
| 52 | `9f5b5b8ea` | chore(build): 移除 Gradle Daemon JVM 配置和 foojay 插件 | refactor | B | build | 读 diff 取逻辑，按 fork 的 Mochi×MD3 组件重写 UI 触点 |
| 53 | `3462d9ee1` | refactor(settings): 设置写入改为以内存为准，修复快速输入和拖动 slider 时丢更新 | refactor | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 54 | `4dbf303f8` | refactor(settings): 修改设置一律基于最新值，不再整份写回界面手里的快照 | refactor | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 55 | `535e25e80` | feat(mediagen): 各厂商的预设模型换成最新一代 | feature | A | ai/app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 56 | `3c1580ea6` | docs: update deps | docs | C | app/build | 跳过：UI 外观/布局类，fork 已用 Mochi×MD3 重做 |
| 57 | `3ff1a7b66` | feat(assistant): 助手列表和配置页改为 MD3 Expressive 风格 | feature | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 58 | `eebdea0bd` | docs: AGENTS.md 设计语言改为 MD3 Expressive | docs | A | other | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 59 | `c907608bc` | fix(mcp): 升级 MCP SDK 到 0.15.0-rikka.4，修复非标准工具 schema 导致整个工具列表加载失败 | bugfix | A | app/build | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |
| 60 | `c9a159a5c` | feat(memory): 记忆改为按文件组织的 Markdown 记忆库，对话后自动整理 (#2130) | feature | B | app/build | 读 diff 取逻辑，按 fork 的 Mochi×MD3 组件重写 UI 触点 |
| 61 | `cb66ba9ed` | feat(chat): 聊天页标题栏改为带渐变遮罩的透明样式，消息可以滚动到标题栏下面 | feature | B | app | 读 diff 取逻辑，按 fork 的 Mochi×MD3 组件重写 UI 触点 |
| 62 | `0f34229ae` | fix(chat): 输入框玻璃效果按应用主题选明暗参数，修复系统浅色、应用深色时发白 | bugfix | A | app | 可直接 cherry-pick，注意包名 heizige.kk.khatkit.* 与 fork 既有改动冲突 |

## 本区间最值得先做（按收益/风险排序）

1. **MCP 一族 5 个修复（#14 $ref 内联、#15 OAuth 回调改 localhost、#19 授权服务器回退、#20 Client ID Metadata Document、#59 SDK 0.15.0-rikka.4）** —— 全部 A、全部在 `ai/` + `app/core/data` 逻辑层，与 UI 无关；这几个是上游对 MCP 兼容性的连续修，凑在一起 cherry-pick 冲突最小，能直接解决第三方 MCP server 的 400/403 报错。风险：SDK 版本号要与 fork 的 `gradle/libs.versions.toml` 对齐。
2. **#25 OpenAI Response API 函数工具 strict=false** —— A 级、近乎一行，修「可选字段被强制填充导致工具参数报错」，直接影响工具调用成功率，几乎零适配。
3. **#32 搜索结果被工具输出截断 / 开启工作区后为空** —— A 级，`search/` + 工具输出链路，属于用户可见的数据丢失，改动面小、无 UI 外观变更。
4. **#48 删除模型或编辑消息后模型名丢失** —— A 级、`app/core/data` 链路修复；改动集中在模型元数据解析，修的是「历史消息展示错」这类长期困扰。
5. **#33 压缩上下文改为插入摘要检查点（不再删历史）** —— A 但体量最大（数据模型 + 迁移），收益是压缩后还能回看原文。建议前四项做完再单独排一棒，并配一次数据库/DataStore 迁移验证。

## 说明

- 本表由脚本按「提交主题关键词 + 触碰路径」自动初判后人工校正：类型取 subject 前缀；可移植按「是否触碰 `core/ui` / `feature/*Page.kt` / `RouteActivity` / theme / 组件壳」判定 —— 纯 UI 外观（Expressive 风格、面板布局）一律 C；数据层 / 第三方 SDK / 构建 / 资源一律 A。
- 与 fork 已实现功能重叠（如 #29/#30/#34/#36/#40/#57 的 MD3 Expressive 风格改造）判 C：fork 外观层已统一到 Mochi×MD3，重做会覆盖现有 Mochi 组件。
- 模块列只列顶层（app / ai / common / search / web / workspace / speech / document / highlight / mediapicker / material3 / oauth / build）。
