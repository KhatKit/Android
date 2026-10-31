# 上游 2.5.5→3.1.1 提交分类矩阵（#125–#186）

- 区间：上游 2.5.5..3.1.1 的第 125–186 条，共 **62** 个提交（清单 /tmp/sync-commits.txt）。
- 判定口径：
  - **A 直接移植**：非 UI 逻辑 / 构建 / 资源 / 数据层 / 文档，改包名（heizige.kk.khatkit.*）即可，尽量带单测；
  - **B 需适配**：逻辑可借鉴，但触碰 UI（本 fork 已是 Mochi x MD3，页面/组件重写过），要按本 fork 页面重接；
  - **C 跳过**：UI 外观/结构已重做、本 fork 已实现、或版本号自管。
- 模块按改动路径归属（app:feat:* / app:core:* / ai / common / search / speech / web / workspace / document …；app:res = 资源与文案）。
- 统计：A **29** / B **20** / C **13**（合计 62）。

| 序号 | hash | 标题 | 类型 | 可移植 | 模块 | 建议 |
|---:|---|---|---|---|---|---|
| 125 | 6fa164050 | fix(memory): 超过 48KB 的记忆文件允许写小，只拒绝让文件变大的超限写入，避免从旧记忆迁移来的超大 profile 无法删改 | bugfix | A | app:core:data | 直接移植：MemoryRepository 的 48KB 写入判定 + 单测可一起搬 |
| 126 | bbe3001d9 | feat(compress): 压缩对话框恢复「保留最近的消息数」，最近 N 条不参与压缩、留在摘要之后，对话超出上下文时可缩小压缩请求 (#2171) | feature | B | app:feat:chat, app:res, app:other | 压缩对话框按 Mochi 重写；「保留最近 N 条」参数与请求裁剪逻辑可照搬 |
| 127 | 222bc4ad1 | fix(mediagen): MiniMax 取回视频结果时，结果地址与 baseUrl 同源则携带 API 密钥，修复中转站鉴权结果地址固定 401 (#2177) | bugfix | A | app:other | 直接移植：mediagen MiniMax 同源才带 key（纯逻辑） |
| 128 | 96a9bed4d | fix(ui): 图片预览保存时「正在保存」提示在保存结束后收起，不再和保存结果提示叠在一起；Toaster 新增 showProgress 返回可收起的句柄，保存提示文案本地化 (#2167) | bugfix | C | ui, app:res, app:ui:component | 跳过：图片预览保存提示属 UI/Toaster，本 fork 已用 Mochi Toaster |
| 129 | bc4a6f47e | feat(chat): 输入框里已添加的图片点开可预览，多张图片左右滑动切换，预览里可进入画板在当前图片上绘制；ImagePreviewDialog 新增 initialPage 和底部操作按钮槽位 (#2152) | feature | C | app:ui:component | 跳过：输入框图片预览/画板是 UI 组件，按 Mochi 另做 |
| 130 | 9b458a67c | fix(chat): 消息气泡改为只裁剪绘制，修复超过 8192px 的长消息（如展开的长代码块）下半部分无法横向滚动、点击折叠无响应 | bugfix | B | app:ui:component | 可借鉴：长消息裁剪只影响绘制，本 fork 气泡已重写 |
| 131 | 7fffc68c8 | fix(chat): 用户气泡左侧留白从 48dp 减到 24dp，气泡内水平边距从 12dp 收到 10dp，长段用户消息每行能多放约 28dp，不再显得拥挤 (#2173) | bugfix | C | app:ui:component | 跳过：气泡留白属 UI 外观 |
| 132 | 42896e5ad | fix(mcp): MCP 选择列表的 supporting 只放单个状态文本，工具数量标签移到标题行，绕开 Compose UI 1.12 测量期读取基线导致的 LayoutNode not found in RectList 崩溃，修复翻动 MCP 列表闪退 (#2179) | bugfix | B | app:ui:component | 可借鉴：MCP 列表测量期读基线崩溃是 Compose 通用坑，照搬到本 fork 行组件 |
| 133 | 318cffef6 | feat: oauth订阅登录支持 (#2186) | feature | B | app:feat:setting, app:core:data, app:core:misc | 逻辑照搬（oauth 订阅提供方 + 登录流），登录页等 UI 按 Mochi 重接 |
| 134 | 43d9e30e1 | feat(chat): 思考内容正文跟随聊天字体大小设置缩放，和消息正文保持一致 | feature | B | app:ui:component | 只取字体缩放接入点，UI 用本 fork 聊天正文 |
| 135 | bdb2ddc73 | fix(mediagen): OpenAI 图像响应里空字符串的 b64_json 视为没有、改用 url，修复中转同时返回 url 和空 b64_json 时写出 0 字节图片、生成结果显示为灰色；下载结果时记录地址和字节数 | bugfix | A | app:other | 直接移植：OpenAI 图响应空 b64_json 回退 url（纯解析） |
| 136 | 183a7da70 | fix(sketch): 画笔落笔时主动让窗口重画一帧，修复部分机型（Android 13 的 OPPO）上用默认画笔画的内容不显示、撤销不可用，直到换粗细/颜色或切前后台才一起出现 (#2193) | bugfix | B | ui | 画板重绘触发只影响自绘 View，按本 fork Sketch 适配 |
| 137 | eeef60b22 | chore: bump version to 3.1.0 | chore | C | build | 跳过：版本号由本 fork 自管 |
| 138 | e5bb70730 | chore(skill): 更新日志每条只写核心功能，功能条目中文不超过 20 字，不再附带次要行为和提示类改动 | chore | C | docs | 跳过：上游 changelog 与发布节奏不同 |
| 139 | fe55bff7f | feat(provider): 添加提供商的类型列表改用单独的类型名，和可改的默认名分开；ChatGPT、Grok 显示为「订阅」类型并本地化 | feature | C | app:feat:setting, app:res | 跳过：提供商类型列表属 UI，本 fork 设置页已重做 |
| 140 | 17d06d26f | fix(provider): 新建、导入或添加推荐提供商后，等新项写入列表再自动滚动到它并清空搜索词，修复新提供商插在顶部却被挤出视口、用户看不见 | bugfix | B | app:feat:setting | 可借鉴：新增后滚动到新项，按 Mochi 列表重接 |
| 141 | 8bb488e3a | feat(chat): 工具调用记录本地执行的起止时间（startedAt/finishedAt），工具步骤右侧显示执行耗时，执行中实时计时；拒绝和问答不计时，中途停止的工具耗时算到取消为止 | feature | B | app:core:data, ai, app:ui:component | 数据层 startedAt/finishedAt 可直接搬，耗时展示按 Mochi 工具步骤重做 |
| 142 | 5203601f7 | feat(ui): 全局禁用滚动容器的 overscroll 拉伸回弹效果 | feature | C | app:core:ui | 跳过：本 fork 滚动回弹由 Mochi overscroll 统一接管 |
| 143 | 2b633370f | feat: 添加远程ssh工作区支持 (#2211) | feature | B | app:nav, app:feat:extensions, app:core:data | 大特性：取 ssh 工作区数据/执行层，UI 与工作区页按 Mochi 重接 |
| 144 | 2aafd708f | fix(skills): 删除技能按实际目录定位，修复目录名和 name 不一致的技能删不掉；安装时校验 SKILL.md，确保目录名与 name 一致；技能页列出空目录等无法识别的目录供删除；技能页面和导入提示本地化 | bugfix | B | app:feat:extensions, app:core:data, app:res | 技能目录/校验逻辑可搬，技能页 UI 已重做 |
| 145 | 17f10ca1f | refactor(i18n): 翻译工具改用 Bun + TypeScript 重写到 tools/i18n，用 Vercel AI SDK 调用翻译、citty 解析命令，去掉 TUI；strings.xml 只改动涉及的条目、保留注释和其他元素，所有翻译都校验占位符/换行/标签，新增 remove、list、unused 命令，未引用检测改为全项目扫描；模型换成 deepseek-flash，技能改名为 i18n-localization | refactor | A | tools, docs, app:other | 可移植：tools/i18n 工具链（Bun+TS），按本 fork 语言集调整 |
| 146 | 298bd65e4 | feat(i18n): 译文和 --raw 输入用 fast-xml-parser 的 XMLValidator 校验是否为合法 XML 片段，并拒绝 aapt 不认识的命名实体（如 &nbsp;），避免写入后资源编译失败 | feature | A | tools | 可移植：i18n XML 片段校验（纯脚本） |
| 147 | 0ff609f93 | chore(i18n): 配置 oxfmt 格式化（默认规则，与 web-ui 一致），新增 fmt / fmt:check 脚本并格式化现有代码 | chore | A | tools | 可移植：oxfmt 配置与脚本（仅工具目录） |
| 148 | db93fa11a | chore(trace): trace-cli 移到 tools/trace，包名和命令名改为 trace，输出目录等相对路径随层级调整 | chore | A | tools | 可移植：trace-cli 移到 tools/trace 的脚本调整 |
| 149 | 26ca06cb5 | feat(provider): 新增赞助商钠API：三语 README 赞助商表格加入钠API 及图标，推荐提供商加入钠API 并移除 AiHubMix，提供商图标匹配 naapi/钠API | feature | B | app:core:data, app:res, docs | README 赞助商表可直接搬；提供商图标按本 fork 图标集重做 |
| 150 | 481833116 | feat(build): 新增 distribution 渠道 flavor（github 默认 / play），共用同一 applicationId；用 BuildConfig.IS_PLAY_STORE 替代运行时安装来源检测，Play 版不请求更新、不显示更新卡片和更新提醒设置；baseline profile 合并到 src/main 两个渠道共用，生成时只跑 github 渠道；每日构建、buildAll、发布技能和 AGENTS.md 改用带渠道的任务和输出路径 | feature | B | app:feat:chat, app:feat:setting, build | distribution flavor/IS_PLAY_STORE 可搬，设置页与更新卡片 UI 已重做 |
| 151 | 97cbb5999 | chore(app): 删除未使用的 banner 图片，占位图 placeholder / placeholder_dark 改为 WebP，打包时排除 BouncyCastle Picnic 查找表；arm64 APK 从 43.9MB 降到 35.4MB | chore | A | build, app:res | 直接移植：删未用 banner、placeholder 转 WebP、R8 排除 Picnic 表 |
| 152 | 815e9c20b | refactor(document): 用纯 Kotlin 实现的 PDF 文本提取替换 mupdf，删除 JNI 绑定和 libmupdf_java.so；支持 xref 流/对象流/增量更新/损坏文件修复、常用过滤器、标准安全处理器（RC4/AES）、简单字体/Type3/CID 字体与预定义 CJK CMap、Adobe CID→Unicode 表、TeX 字形名、ActualText、可选内容、注释外观流、矩形与表单 BBox 裁剪、RTL 重排；arm64 APK 从 35.4MB 降到 30.9MB | refactor | A | document | 可移植但大：PDF 纯 Kotlin 提取替换 mupdf、去 .so；单独排期 |
| 153 | dbccc68c7 | chore(build): 缩减 APK 体积；resources.arsc 只保留 app 已翻译的语言，排除 kotlin-reflect 并用工厂表替代反射创建搜索配置，Web 端 JWT 改为自实现 HS256 并去掉 ktor-server-auth-jwt / auth0 / Jackson；web-ui 代码高亮由 shiki 换成 highlight.js（语言与 App 端 highlight 模块一致，配色共用 Atom One），KaTeX 只打包 woff2；移除未使用的 sqlite-vector 依赖声明，忽略按渠道导出的签名包目录；arm64 APK 从 30.9MB 降到 25.8MB | chore | A | app:feat:setting, web, search | 直接移植 build-size：arsc 语言裁剪、去 kotlin-reflect、JWT 自实现；逐项验证 |
| 154 | 80845dd9b | refactor(search): 全文搜索去掉 jieba 分词和词典；查询改为在 Kotlin 中拼接，英文数字仍交给 simple_query（保留拼音、首字母、前缀匹配），中文片段拆成单字用 NEAR 限定相邻（中间最多多夹 2 个字），各段 AND 连接；数据库打开时不再解压加载词典，并删除老版本解压到 files/simple_dict 的词典；arm64 APK 从 25.8MB 降到 20.8MB | refactor | A | app:core:data, app:res | 直接移植：search 去 jieba、Kotlin 拼查询；重跑搜索单测 |
| 155 | d8ba9cf67 | refactor(provider): 扫码导入提供商由 quickie + ML Kit 换成 CameraX + zxing；新增全屏 QRCodeScannerDialog（camera-compose 预览、720p 只保留最新帧分析、识别后只回调一次、无权限时申请并在拒绝时提示），解码逻辑拆为 QRCodeDecoder 并补充带行填充帧的单元测试；去掉 quickie-bundled / barcode-scanning 依赖，CameraX 只引入 camera2 / lifecycle / compose，显式声明原先由 quickie 间接引入的 exifinterface；删除不再使用的 setting_provider_page_scan_error 文案；arm64 APK 从 20.8MB 降到 17.6MB | refactor | B | app:feat:setting, build, app:res | QR 解码层（CameraX+zxing）可搬，全屏扫码 UI 按 Mochi 重做 |
| 156 | 26454cc4d | chore(app): 窗口主题改用系统 Theme.Material（亮色/暗色分别配置），不再继承 Material Components 的 Theme.Material3.DayNight，R8 可裁掉 MDC 的样式和属性资源；窗口背景沿用原 Material3 background 色；arm64 APK 约减少 0.43MB | chore | B | app:res | 窗口主题改系统 Theme.Material 可搬；与 MochiTheme 系统栏策略对齐 |
| 157 | e3d11e15f | build: 构建上传mapping txt | build-size | A | docs, app:other | 直接移植：上传 mapping 的 CI 步骤 |
| 158 | 070ff683c | feat(i18n): 新增德语支持；i18n 配置加入 values-de 及术语表（用 du 称呼用户，Prompt/Tool/Skill/Reasoning/Lorebook 保留英文，memory 译为 Erinnerung），app/search/oauth/ui 四个模块全部文案机翻为德语；localeFilters 加入 de 使德语资源随包打出并进入自动生成的 locale-config | feature | A | search, ui, build | 可移植：values-de 语言包与 i18n 配置（核对本 fork 已有语言） |
| 159 | 4e99510be | docs: add jetpack compose perf skill | docs | A | docs | 可移植：Compose 性能 skill 文档 |
| 160 | 2a0a831e6 | refactor(ui): 图片裁剪由 uCrop 换成 ui 模块的 Compose 实现 ImageCropDialog；支持拖动/双指缩放图片、拖框的边角调整范围（松手后框连同图片放大回中）、旋转 90°、重置，未固定比例时可选自由/原图/1:1/4:3/3:4/16:9/9:16，头像锁定 1:1；ImageDecoder 解码（自带 EXIF 方向与 HEIF，失败回退 BitmapFactory），原图长边限 4096、屏幕预览缩到 2048；把手区域排除系统返回手势，画布裁剪绘制范围；useCropLauncher 改为直接返回打开函数，去掉为 uCrop 做的临时拷贝和 HEIF 转 JPEG；删除 uCrop 依赖与 UCropActivity，以及各模块未使用的 appcompat / material 声明；新增文案并翻译到全部语言，补充旋转坐标换算单测；arm64 APK 从 17.1MB 降到 16.1MB | refactor | C | web, common, workspace | 跳过：图片裁剪 Compose 实现属 UI 组件 |
| 161 | 2b522f990 | chore(search): libsimple.so 换成不带 jieba 的版本；按上游 wangfenjin/simple v0.6.1 的 Android workflow 参数（NDK 29.0.14206865、android-21、Release、静态 libc++）加 -DSIMPLE_WITH_JIEBA=OFF 重编 arm64-v8a / x86_64，去掉已不再调用的 jieba_query / jieba_dict 及 cppjieba 代码，simple 分词器和 simple_query 不变，已有索引无需重建；调试信息中的本机路径用 -ffile-prefix-map 映射为中性路径；arm64 APK 减少约 41KB | chore | A | app:other | 直接移植：libsimple.so 重打包（去 jieba），与 154 同批 |
| 162 | 71dd98f88 | refactor(chat): 压缩上下文对话框的「保留最近消息数」由数字输入框改为步进器；左侧标题、右侧 −/+ 两个 FilledTonalIconButton 夹着当前数值，默认 0，到 0 时减号禁用，不再弹出键盘，也避免了原输入框负数显示与实际值不一致、清空后沿用旧值、输入小数被忽略等问题 | refactor | B | app:ui:component | 可借鉴：保留条数改步进器属 UI，数值语义修复可照搬 |
| 163 | 90e48420f | perf(search): 消息索引按会话增删不再扫描全表 | perf | A | app:core:data, app:other | 直接移植：消息索引按会话增删（perf，数据层，配索引单测） |
| 164 | 43f1a5df2 | feat(media): 创作页点顶栏标题重命名会话 | feature | B | app:feat:mediacreation | 可借鉴：重命名会话逻辑可搬，顶栏交互按 Mochi |
| 165 | 9dcb4fa44 | fix(ai): 模型匹配不再把参数量当成版本号；token 序列匹配时跳过紧跟 b/k 的数字（如 8b、a3b、32k），qwen3-8b 不再误中 QWEN_3_8 被判为支持图片输入，qwen2.5-3b 不再误中 QWEN_3；新增 QWEN_3_VL 保持 Qwen3-VL 的视觉输入；resolveModels 只分词一次，去掉未使用的 matchScore(modelId)；补充单测 | bugfix | A | ai | 直接移植：模型名 token 跳过 b/k 数字 + QWEN_3_VL（纯逻辑，配单测） |
| 166 | 41ce12f66 | feat(ai): 模型能力识别改用 models.dev 数据 | feature | A | app:core:data, app:core:misc, ai | 直接移植：models.dev 能力数据源（网络+缓存层，UI 不动） |
| 167 | fcb4bfe4e | feat(search): Tavily 可关闭生成答案，完善 query 参数描述 | feature | A | app:feat:setting, search | 直接移植：Tavily generate_answer 开关（选项 + 请求参数） |
| 168 | 2b4694ba7 | fix(ui): 悬浮窗不再操作已被 cancel 的 FxControl | bugfix | B | app:ui:component | 可借鉴：悬浮窗 FxControl cancel 判断，按本 fork 悬浮窗适配 |
| 169 | 487e229f7 | chore: 共享ask user tool name为公共常量 | chore | A | app:core:data, app:ui:component | 直接移植：ask_user 工具名常量化 |
| 170 | 5dd7b764c | refactor(ui): 抽出应用 logo 的绘制和变形轮廓 | refactor | C | app:feat:setting, app:ui:component | 跳过：logo 绘制/变形属 UI 图形 |
| 171 | f048bed65 | feat(chat): 后台生成时显示悬浮窗，可查看进度和快速回复 | feature | B | app:nav, app:feat:setting, app:core:data | 大特性：后台生成悬浮窗，取通知/进度数据层，窗口 UI 按 Mochi 重做 |
| 172 | e48db7bc7 | fix(memory): 编辑页允许保存删小后仍超限的记忆文件 | bugfix | A | app:feat:memory | 直接移植：记忆编辑页允许删小超限文件（与 125 同源） |
| 173 | 537f3b0ed | build: Gradle daemon 堆内存调到 8g 并开启并行构建 | build-size | A | app:other | 直接移植：gradle 堆/并行（本 fork 按机器规格取参数） |
| 174 | f267e8478 | build: JLatexMath 改为只 keep 反射用到的类，不再整库保留 | build-size | A | app:other | 直接移植：JLatexMath R8 keep 收窄 |
| 175 | 281c9525a | fix(chat): 停止后立即发送的消息不再卡在暂停的队列里 | bugfix | A | app:other | 直接移植：停止后立即发送不再卡暂停队列（纯状态机，配单测） |
| 176 | fa5d5f3f6 | build: 开启 Gradle 并行 IDE 同步 | build-size | A | app:other | 直接移植：IDE 同步并行开关 |
| 177 | 20c24e7d3 | chore: update deps | chore | A | build | 可移植：依赖版本更新（本 fork 版本目录已分叉，逐项评估） |
| 178 | eb530b0ff | fix(markdown): 修复公式里的 < 被当成 HTML 标签导致渲染失败 | bugfix | A | ui, app:ui:component | 直接移植：Markdown 公式里 < 被当 HTML 的转义修复 |
| 179 | 102be19d3 | refactor(chat): 联网搜索入口移到加号 sheet | refactor | C | app:feat:chat, app:res, app:ui:component | 跳过：搜索入口移到加号 sheet 属 UI/交互 |
| 180 | c2a64ff4b | refactor(ui): 抽出公共的 SwipeToDelete 组件 | refactor | B | app:feat:favorite, app:feat:history, app:feat:translator | 可借鉴：SwipeToDelete 组件，本 fork 已有 Mochi 列表手势，按需包一层 |
| 181 | 4af510624 | fix(nav): 修复同一帧连按返回把导航栈弹空导致闪退 | bugfix | A | app:nav | 已在本 fork 等价落地（NAV3 提交 944b44114：MochiBackedNavStack.removeAt 加 size<=1 兜底） |
| 182 | 395a0ee03 | fix(model-list): 修复收藏模型拖动排序时来回抽搐 | bugfix | B | app:ui:component | 可借鉴：收藏模型拖动排序抖动，需在本 fork 复现后再改 |
| 183 | db4982283 | fix(tts): 修复朗读时把 HTML 标签一并读出 | bugfix | A | app:feat:chat, speech, app:other | 直接移植：TTS 朗读剥离 HTML 标签（纯文本清洗 + 单测） |
| 184 | b68760d92 | refactor(media): 媒体提供商配置改为独立详情页 | refactor | C | app:nav, app:feat:setting, app:res | 跳过：媒体提供商独立详情页属 UI 结构，本 fork 页面体系不同 |
| 185 | 72d419171 | feat(memory): 新增记忆整理模型设置 | feature | C | app:feat:setting, app:core:data, app:res | 跳过：记忆整理模型设置是设置页 UI，本 fork 设置页已重做 |
| 186 | ad2c4430d | chore: bump version to 3.1.1 | chore | C | build | 跳过：版本号由本 fork 自管 |

## 本区间最值得先做的 5 项

1. **#183 db4982283 fix(tts)：朗读把 HTML 标签一起读出（A）** — 纯文本清洗 + 单测，风险最低、用户可感知；与 #178（公式 < 被当 HTML）同属「字符串/解析」类，一次做掉。
2. **#175 281c9525a fix(chat)：停止后立即发送的消息卡在暂停队列（A）** — 纯状态机 bug，影响核心聊天链路，本 fork 队列层可对照移植并配单测。
3. **#165 9dcb4fa44 fix(ai)：模型匹配把参数量当版本号（A）** — 纯字符串匹配 + 新增 QWEN_3_VL，直接影响「模型是否被判支持图片」；与 #166 models.dev 能力数据可同批评估。
4. **#163 90e48420f perf(search)：消息索引按会话增删不再扫全表（A）** — 数据库层性能修复，改动集中、可单测，本 fork 索引表仍在。
5. **#154 80845dd9b 去 jieba + #161 2b522f990 libsimple 重打包（A）** — 必须同批（查询语法与 .so 匹配），收益是启动不再解压词典、包变小；作为搜索专项排在前面几项之后。

> #181 4af510624（同一帧连按返回弹空栈）已在本 fork 等价落地（NAV3 提交 944b44114：MochiBackedNavStack.removeAt 加 size <= 1 兜底、KkNavController.popBackStack 记录根页不变量），矩阵保留该行便于对账。
