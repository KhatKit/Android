# Mochi 底栏分页覆盖与验收契约

核对日期：2026-11-28。结论：**6 个应用入口 + 1 条公共卡片分页链完整覆盖；旧固定依赖 API 缺口已查明，由新增 t8 补齐，t2/t3 消费者等待 t8 完成。** 本文记录需求与源码证据，不代表编译或设备交互已通过。

## 1. 事实来源与同款定义

- 应用规范：[KhatKit AGENTS](../AGENTS.md)、[Mochi AI 入口](../../Mochi/llms.txt)、[接入规范](../../Mochi/docs/zh_CN/guide/application-integration.md)、[设计规范](../../Mochi/docs/zh_CN/guide/design-principles.md)。本任务明确禁止成员提交、推送、assemble/APK、设备操作及触发发布，覆盖仓库通常的提交要求。
- 权威 demo：[DemoApp.kt:202–215](../../Mochi/example/src/main/java/ui/feice/mochi/demo/DemoApp.kt#L202)，直接调用 MochiTabScaffold；[MochiTabScaffold.kt:163–174](../../Mochi/mochi-compose/src/main/java/ui/feice/mochi/navigation/MochiTabScaffold.kt#L163) 明确传入 MochiPeerTransitionSpec.Depth。
- “同款”是上述库原语的 **Depth**，不是默认 spec（默认是 Slide），也不是 HorizontalPager 位移、应用自写 Crossfade/AnimatedContent、缩放公式或第二套 spring。Depth 原地缩放、淡入淡出，无横移、无模糊；参考参数 minScale=0.94、responseSpring(0.42, 1)，只作为识别证据，不在应用复制参数重做动画。见 [MochiPeerTransition.kt:58–89](../../Mochi/mochi-compose/src/main/java/ui/feice/mochi/navigation/MochiPeerTransition.kt#L58)。
- 标准根页布局优先直接使用 MochiTabScaffold。现有自适应 rail/sidebar、独立共享顶栏及卡片公共宿主属于特殊结构，允许薄封装转发同一个公开库转场，保留原来的栏、玻璃 backdrop、安全区与业务状态；不得为了接 scaffold 删除这些能力，也不得在应用整页新增 pointerInput 横拖拦截。
- 公开原语参考签名包含 targetState、pages、onPageChange、direction、background、backdrop、spec、retainPages；默认 retainPages=true，保存已访问页的组合与 saveable 状态，副作用仍可能运行。见 [MochiPeerTransition.kt:108–138](../../Mochi/mochi-compose/src/main/java/ui/feice/mochi/navigation/MochiPeerTransition.kt#L108)。

## 2. 全部底栏消费链

全仓 Kotlin/Java 符号搜索确认：MochiTabBar 实际调用点仅 **2** 个包装器；应用 MochiNavBottomBar 实际消费点 **6** 个；卡片 MochiNavigationBar 实际消费点 **1** 个。没有这些导航符号的 import alias/typealias 隐藏消费；名称相近的 MochiTabRow/MochiTabPageScaffold 是顶部 tab。

### 2.1 应用包装器与六个入口（全部应改）

共同链：以下入口 → [MochiNavBottomBar / MochiShell.kt:108](../app/src/main/java/heizige/kk/khatkit/app/feature/ui/shell/MochiShell.kt#L108) → [MochiTabBar:178](../app/src/main/java/heizige/kk/khatkit/app/feature/ui/shell/MochiShell.kt#L178)。MochiNavTab 的 selected/onClick 当前绑定各自 PagerState。布局宽度小于 600dp 为底栏，600–839dp 为 rail，840dp 起 sidebar（[MochiShell.kt:91–97](../app/src/main/java/heizige/kk/khatkit/app/feature/ui/shell/MochiShell.kt#L91)）；宽屏分支同属这 6 个入口，不另算页面、不遗漏。

| 入口与页数 | 当前底栏 / 分页证据 | 实施判定与必须保持的业务 |
|---|---|---|
| [ImgGenPage.kt](../app/src/main/java/heizige/kk/khatkit/app/feature/imggen/ImgGenPage.kt#L125)，2 页 | 底栏 :228；HorizontalPager :187；实际函数名 ImageGenPage，私有 BottomBar 是本地包装 | 应改；生成 / 图库；保留生成会话、取消生成返回拦截、图库 isActive，以及现有 topBar、innerPadding/Insets。 |
| [PromptPage.kt](../app/src/main/java/heizige/kk/khatkit/app/feature/extensions/PromptPage.kt#L122)，2 页 | 底栏 :134；HorizontalPager :153 | 应改；模式注入 / 世界书；保留独立列表位置、排序、搜索、编辑状态与 FAB 让位。 |
| [WorkspaceDetailPage.kt](../app/src/main/java/heizige/kk/khatkit/app/feature/workspace/WorkspaceDetailPage.kt#L177)，2 页 | 底栏 :193；HorizontalPager :212 | 应改；基本 / 文件；保留目录、区域、文件列表位置、导入导出，当前页决定导入按钮、isActive 及先回上级目录的返回拦截（:164）。 |
| [SettingSpeechPage.kt](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingSpeechPage.kt#L78)，2 页 | 底栏 :115；HorizontalPager :134 | 应改；TTS / ASR；当前页同步新增服务商按钮，保留 editingTTSProvider / editingASRProvider、编辑面板与各自列表状态。 |
| [SettingModelPage.kt](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingModelPage.kt#L52)，2 页 | 底栏 :62；HorizontalPager :72 | 应改；模型 / 提示；复用已提升到父级的 modelList/promptList（:57–58），保留表单与 OCR Repository 的既有 WIP。 |
| [SettingProviderDetailPage.kt](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingProviderDetailPage.kt#L164)，3 页 | 底栏 :203；HorizontalPager :217 | 应改；配置 / 模型 / 高级；保留 provider 编辑即存、账号状态、分享与子页面/弹层。内部模型编辑器的顶部三段控件不在本次替换内。 |

六个入口目前都通过自适应 MochiPageScaffold 或 MochiSettingsPageScaffold 保留独立顶栏，允许共享的薄转场包装器。应说明特殊布局原因；不能只换底栏名字却留下原 HorizontalPager。浮栏的 inset、scrollBehavior、玻璃来源由 [FloatingRailLayout / MochiShell.kt:454–485](../app/src/main/java/heizige/kk/khatkit/app/feature/ui/shell/MochiShell.kt#L454) 负责，禁止重复预留或截短整页转场。

### 2.2 卡片公共宿主与动态消费（复用覆盖，分页路径应改）

公共底栏链：[MochiSearchAndBar.kt:141–171](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/mochi/MochiSearchAndBar.kt#L141) 的 MochiNavigationBar → MochiTabBar → [KhatKitPage.kt:247](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/KhatKitPage.kt#L247)。包装器与宿主均检查 enabled，保留标签样式、悬浮布局及材质；仅修改底栏本身不能带来分页转场。

| 消费路径 | 分页判定与范围 |
|---|---|
| [KhatKitUiHost.kt:229–261](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/khatkit/KhatKitUiHost.kt#L229) 的 UiRequest.Screen → TreePage → [NodeTreePage:85–87](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/NodeTreeRenderer.kt#L85) → [PagedNodeTreePage.kt:41–65](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/PagedNodeTreePage.kt#L41) → KhatKitPage | pages 非空且非 form/readOnly 时唯一公共分页链；当前 HorizontalPager :145，必须替换。导航 event 是稳定页 ID，UI 线程内切页，不向脚本发送一次“导航提交”。 |
| [CardWidgetPage.kt:230–235](../app/src/main/java/heizige/kk/khatkit/app/feature/chat/CardWidgetPage.kt#L230) 内联卡片 → KhatKitTreeRequest | 复用同一公共链；保留外层拥有的顶栏、LocalKhatKitPageInline、页内 Insets，不添加第二个顶栏。 |
| [CardPageStack.kt:194–212](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/khatkit/CardPageStack.kt#L194)，由 [CardInteractionPage.kt:56](../app/src/main/java/heizige/kk/khatkit/app/feature/chat/CardInteractionPage.kt#L56) / [CardWidgetPage.kt:126](../app/src/main/java/heizige/kk/khatkit/app/feature/chat/CardWidgetPage.kt#L126) 承载 | frame.pages → TreePage，复用同一公共链；卡片页栈自身的 push/pop/MochiNavHost 不替换成同层级 Depth。 |
| [NodeTreeRenderer.kt:89–142](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/NodeTreeRenderer.kt#L89) 的无 pages、FormTree、ShowTree 分支 | 不适用横滑分页：单棵树，导航 event 交回脚本重出屏；保留 navigationPending、防重复发送、字段校验与只读语义，不凭空合成页。 |
| [KhatKitForm.kt:82](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/KhatKitForm.kt#L82)、[ActionPage:305](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/khatkit/KhatKitUiHost.kt#L305)、[ShowCardPage:396](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/khatkit/KhatKitUiHost.kt#L396) → KhatKitPage | 可消费 navigation options 但没有页集合/本地分页状态；维持原有单页动作/表单/展示语义，不强行添加横滑。 |

动态声明完整入口：[UiBridgeHost.kt:177–202](../khatkit/src/main/java/heizige/kk/khatkit/ui/UiBridgeHost.kt#L177) 接收 nav / bottom_nav / navigation / tabs；items / entries / list；条目 event / id / action、label / title、enabled / disabled；selected / value / current。有效条目数 2–5。字段通用 snake_case/camelCase/kebab-case 仍由原解析器处理。上述别名全部进入同一 UiSheetNavigation，不是额外宿主。

[UiScreenPage.kt:9–30](../khatkit/src/main/java/heizige/kk/khatkit/ui/UiScreenPage.kt#L9) 已校验 page keys 与导航 events 完全一致、events 唯一、selected 合法、每页树独立解析；宿主更换动画不修改 Lua/JS/WASM 桥接契约或这份校验器。已安装及后续动态卡片都通过公共宿主复用覆盖，不按具体卡名逐张重写。

## 3. 排除清单（保留现有业务）

| 位置 | 排除依据 |
|---|---|
| [BackupPage.kt:46–59](../app/src/main/java/heizige/kk/khatkit/app/feature/backup/BackupPage.kt#L46) | 顶部 MochiTabPageScaffold / MochiTabRow 管理的备份四页，无 Mochi 底栏。 |
| [AssistantExtensionsPage.kt:53–66](../app/src/main/java/heizige/kk/khatkit/app/feature/assistant/detail/AssistantExtensionsPage.kt#L53) | 助手扩展顶部四 tab，无 Mochi 底栏。 |
| [DebugPage.kt:77–83](../app/src/main/java/heizige/kk/khatkit/app/feature/debug/DebugPage.kt#L77) | 调试顶部三 tab，无 Mochi 底栏。 |
| [ExtensionSelector.kt:78–88](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/ExtensionSelector.kt#L78) | 选择器内部顶部四 tab，无 Mochi 底栏。 |
| [McpConfigModal.kt:150–160](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/mcp/McpConfigModal.kt#L150) | MCP 编辑面板顶部基本/工具 tab，无 Mochi 底栏。 |
| [ProviderModelList.kt:385–399](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/provider/ProviderModelList.kt#L385) | 模型编辑器内顶部 MochiSegmentedControl + 三页 Pager；与外层服务商底栏分页不同。 |
| [KhatKitMarketContent.kt:255–267](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/KhatKitMarketContent.kt#L255) | 精选卡片轮播，有 pageSpacing/contentPadding 和指示器，不是导航页。 |
| [ImagePreviewDialog.kt:39–55](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/ImagePreviewDialog.kt#L39)、[MediaImagePager.kt:94–109](../mediapicker/src/main/java/heizige/kk/khatkit/mediapicker/ui/components/MediaImagePager.kt#L94) | Zoomable 图片浏览/媒体预览，须保留缩放、拖动及图片索引。 |
| [WorkspaceTerminalPage.kt:190](../app/src/main/java/heizige/kk/khatkit/app/feature/workspace/WorkspaceTerminalPage.kt#L190) | 终端会话的顶部 SecondaryScrollableTabRow，不属于底栏同层分页。 |
| [StatsPage.kt:107](../app/src/main/java/heizige/kk/khatkit/app/feature/stats/StatsPage.kt#L107)、[SettingPermissionsPage.kt:33](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingPermissionsPage.kt#L33) | 仅借用 MochiTabScaffoldDefaults.contentBottomPadding，无底栏分页；不能按名字误改。 |

全仓实际 HorizontalPager 调用共 **14** 处：本次应替换 **7**（应用 6 + 卡片 1），排除 **7**（顶部/面板分页 6 + 轮播 1）；另有 **2** 个 Zoomable 图片浏览宿主。若实施中变化，按具体调用而非 import 或注释重新计数。

## 4. 行为验收（实施与独立验证共同使用）

| 编号 | 操作 / 边界 | 合格标准 |
|---|---|---|
| A1 点击 | 顺序、跨页、快速连续点击、转场中反向点选 | 点击/程序选页走库 Depth；只在当前可见页与目标页间交接，连续切换接当前进度，不逐个横移经过中间页、不闪白、不叠第二套 spring。标准 scaffold 用其 select/onSelect；特殊宿主 targetState 与导航只用一个真相。 |
| A2 横滑提交 | 左/右拖、慢拖/快速甩、首尾边界 | 正文按手指连续进行原地景深变化；松手由库的位置/速度阈值决定提交；首尾不能越界。禁止为模拟效果再叠 HorizontalPager 或全页手势识别器。 |
| A3 取消 | 小距离拖回、手势被子控件/系统取消、未过阈值松手 | 回到原页；导航选中、顶栏按钮、activeId、picker/action 归属不误提交，页内数据不重置，取消不向脚本发送导航事件。按压高光、计数连发正确释放。 |
| A4 导航同步 | 点击、横滑成功/取消、脚本修改 selected | 点击/程序请求更新同一个目标；横滑仅在 onPageChange 提交后更新导航和业务 active 页，拖动预览不写业务选中。稳定后正文、底栏、顶栏操作、isActive、返回拦截一致。取消恢复原选中；不得独立计时猜测页码。重复点击已选项不提交业务动作；标准 scaffold 保留 reselect 回顶。 |
| A5 状态保留 | 每页滚动到中部、输入未提交内容/选择器/开关，再往返切换 | 各页滚动/列表位置、表单输入、校验、搜索、排序、编辑面板不丢；保留父级状态与稳定 key。保留页面副作用须审计，只让 active 页绑定 picker/交互；未显示页不误发 load-more、提交或改变文件返回处理。 |
| A6 卡片 action | 不同页填写相同字段名、点击 action、刷树后再点 | values/errors/counterHost/scroll 按稳定 event ID 隔离；action 仅携带所属页字段和原 __khatkit_page 包络。数据刷树只覆盖脚本实际修改的字段，不重置用户草稿；刷新等待/触底加载保持原契约。 |
| A7 禁用页 | 禁用中间页、初始 selected 禁用、当前页运行中禁用、全部禁用 | 禁用项继续展示且不可点；可横滑页序列过滤 disabled，不能先进入再弹回。初始/当前不可用时选择可用项并同步；全部禁用时保持安全只读/空态、不横滑、不回调、不越界，具体展示沿用宿主业务。 |
| A8 动态页列表 | 新增、删除、重排、更新树、外部 selected 不变/改变、当前页被删除 | 用稳定 event ID 而非 index 关联状态；幸存页草稿和滚动位置保留，移除页不再渲染/getValue 崩溃。外部明确选页仅选择存在且 enabled 的 ID；删除当前页回退到有效页，导航同步。空列表安全退出；非法重复 ID/不匹配 keys 继续由原解析器拒绝。 |
| A9 减弱动画 | 系统动画倍率 0、应用 reduce motion、切换中改变设置 | 服从系统/库降级；禁用弹性/持续动画，选择与业务仍可完成，不因等待预热/过渡留下透明页；不能在应用用固定 tween 绕开。是否真实符合必须单列设备人工待测，不以编译替代。 |
| A10 嵌套横向控件 | 滑块、分段选择器、LazyRow/横向滚动区、文本选择、图片缩放；纵向列表及刷新 | 子控件已消费的横拖优先；未消费的页面横拖才交库。不得一次拖动同时改滑块与页，纵向滚动/刷新不误切页；子控件点按不触发父页导航。 |
| A11 共享结构 | 窄屏/rail/sidebar、独立顶栏、内联卡片、卡片页栈返回 | 保留 600/840dp 自适应分支、同一状态、共享顶栏身份与玻璃来源；当前页标题折叠不能因目标提交突然闪跳。若旧 Pager 连续进度没有公开替代，须报告库 API 缺口，不能另造过渡时钟。卡片 push/pop 和预测性返回不被 Depth 替换。 |
| A12 外观与布局 | 明暗、玻璃开关、MOCHI/IOS/NOTHING、大字号、键盘 | 用主题同源库组件；正文不被底栏覆盖、不重复 Insets，不缩放文字硬塞；键盘与弹层关闭恢复正常。 |

卡片当前已知风险（本次应处理或明确阻塞）：[PagedNodeTreePage.kt:56–65](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/PagedNodeTreePage.kt#L56) 通过 settledPage 进入禁用页后弹回，且 values 以 remember(ids) 整体重建；动态增删/重排可能重置所有页草稿。迁移不能照搬这些逻辑而声称满足 A7/A8。原顶栏依赖 Pager 连续 offset（:109–124），picker/action 依赖 settled/scrollInProgress（:178–189），迁移需给出相同归属与取消保障。

## 5. 固定依赖 API 核验：阻塞 F1

版本唯一来源：[libs.versions.toml:78](../gradle/libs.versions.toml#L78) 的 **0.4.0-khatkit.2**；[khatkit-ui/build.gradle.kts:32](../khatkit-ui/build.gradle.kts#L32) 通过 api 暴露。构建脚本固定 pin [build-mochi.sh:6](../.github/scripts/build-mochi.sh#L6) 为 70636705c25b170d9424c9ea6669752ab8f6ece1，先应用 [patch-mochi.py](../.github/scripts/patch-mochi.py) 再 publishToMavenLocal。

直接检查本机 Maven 产物的 sources.jar 和 AAR/classes.jar（未修改、未重新发布）：

| 核验项 | 结果 |
|---|---|
| MochiTabScaffold | public，产物源文件 :131；state/onSelect/swipeBetweenTabs/collapseOnScroll 可用。 |
| MochiPeerTransition / MochiPeerTransitionSpec.Depth | **sources.jar 与二进制均不存在**，不能按当前参考源码直接 import。 |
| 产物 scaffold 实际转场 | 源文件 :174 调用 MochiTabPageTransition，而不是参考源码的 Peer + Depth。 |
| MochiTabPageTransition | 产物源文件 :61 是 **internal fun**；虽实现景深，不是应用可直接调用的公开原语。禁止用 suppress INVISIBLE_MEMBER/反射等绕过。 |
| 完整产物识别 | mochi-compose-0.4.0-khatkit.2.aar SHA-256 = 394d9c876dccca907e69a6efb91a65a70df3aa2615d7c4127225f811a338f807。 |

**范围判断：业务入口范围足够；F1 已纳入 captain 修订后的 t8 依赖适配任务，不再阻塞需求文档验收。** t2（卡片）与 t3（应用）均等待 t8 完成；本阶段记录缺口和可复现路线，不声称新产物已经构建。

补齐路线：

1. t8 从明确的上游历史选择可复现 commit；本机已核对参考 demo 源 HEAD 为 6f4787fe70cc4fc68452b780e6809befdc07ec1b，3 个权威参考源文件无工作树修改。它是可核查候选，不把滚动 HEAD 当版本，也不未经补丁兼容性检查直接升级。
2. 唯一修改入口为 [build-mochi.sh:6](../.github/scripts/build-mochi.sh#L6) 的固定 revision、[patch-mochi.py](../.github/scripts/patch-mochi.py) 的 count == 1 断言补丁及 [libs.versions.toml:78](../gradle/libs.versions.toml#L78) 的新 khatkit 后缀版本。保留既有五组补丁语义，禁止覆盖旧 0.4.0-khatkit.2。
3. t8 经授权运行统一构建脚本，构建并 publishToMavenLocal 四模块（compose/ios/icons/nothing），记录完整 pin、新版本、命令退出码及源/二进制公开 MochiPeerTransition、MochiPeerTransitionSpec.Depth、MochiTabScaffold 的证据；CI 继续走同一脚本。
4. t2/t3 只在新依赖就绪后实现；t4 集中验证 app/khatkit-ui 实际编译与单测，t9 独立评审依赖 pin/补丁/可复现性，最终集成等待三路评审通过。

不能把本地 Mochi checkout 当作固定 Maven API，不能私改 ~/.m2、vendor 库源码、绕过管线发布或改用自造动画。本任务仅写本文，不修改构建脚本、版本或库。

## 6. 原有 WIP 基线与保护

首次 status 快照：**14 个 tracked 变更（11 M、3 D）+ 5 个 untracked**。tracked 涉及插件 Repository 迁移：AI tools 2、OCR 3、SSH 3、terminal 2、市场/扩展 VM 2、PluginHostPage 1、SettingModelPage 1；untracked 是 3 个新插件 Repository、临时 anchor 脚本与既有架构/i18n 计划。均为他人工作，不回退、清理、暂存或顺带提交。

- [SettingModelPage.kt:35](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingModelPage.kt#L35) 原有 WIP 将 OcrPluginStore → OcrPluginRepository，并同步注释与读取调用。核对期间另观察到 close/editNote/neurology import 移入图标子包的并行变更；实施必须以实时工作树为底稿，不从 HEAD 覆盖。
- 用户特别保护 [values/strings.xml](../app/src/main/res/values/strings.xml) 与 [values-zh/strings.xml](../app/src/main/res/values-zh/strings.xml)。首次 status 未显示这两文件，专项 git diff 为空；这不构成覆盖/还原许可，后续并行 WIP 同样保留。
- 专项读取时 SHA-256：values = 7ccd7010cfd247b1cab44201c2aaeefdeb5c99e1c0891a33158a2f9965c05c89；values-zh = db59747d8ec3b85a137278e41c553335bfd2a4f142b61f0a28c095cd6ebc60ff。并行工作可以改变哈希，不能将旧哈希作为还原目标。

## 7. 验证记录与后续交付

- 完成全仓 Kotlin/Java 导航符号、aliases/typealias、实际 HorizontalPager 与 Zoomable 宿主检索；逐项读取上表调用附近源码。检索工具返回成功；无另一个遗漏的 Mochi 底栏分页入口。
- git status --short / git diff --stat：退出 0，记录 WIP；git diff -- 两个 strings 资源：退出 0、空输出。
- Python zipfile API 核验：最终断言 public scaffold 存在、Peer 源码缺失、Peer 二进制缺失、实际转场 internal，**4/4 断言通过，退出 0**；证明 F1 不相容，不能记为兼容通过。
- 探索性 javap 使用匿名内存文件未成功识别 class 路径（退出 1），未作为 API 证据；最终以源归档 + AAR 类目录交叉核验。最初归档脚本假设 Peer 存在触发 StopIteration，已改成存在性断言后完成核验。
- 本任务无编译、JVM 测试或设备验证；不运行 assemble/APK、设备命令、Actions、提交、推送或依赖发布。实施阶段应按授权范围跑 app/khatkit-ui 编译与 JVM 测试、源码守卫，并逐条报告 A1–A12 的自动证据与人工待测项。
- 人工复现：分别进入上述 6 个应用入口；打开带 nav + pages 的动态卡片、内联卡片及带子页栈卡片；依次执行 A1–A12。设备交互结果当前全部为 **未执行/待人工验证**，编译通过不能替代。
