# KhatKit 全仓架构与宿主文案迁移计划

## 0. 基线、边界与恢复点

本计划以实际开工快照为准，不用既有台账的数量替代现状，不把原有改动计入本任务交付。规范源：../ARCHITECTURE.md §1–§8；仓库适配与 UI 源：AGENTS.md（包结构/命名/本地化/Mochi/Nav3）；本轮已读 ../Mochi/llms.txt、../Mochi/docs/zh_CN/guide/application-integration.md 与 design-principles.md。Compose 源码未修改。

| 项目 | 实测结果 |
|---|---|
| HEAD | 98a436b0f456f562f06b1b9002409b08012171fa |
| git status | 35 个 tracked 文件为 unstaged 修改（状态均为 M），1 个未跟踪文件；索引 diff 为空。与任务简述“31 个、staged”不符，后续必须保持此实际快照，不要清理、stash、重置或把 WIP 混进计划提交。 |
| 原有差异规模 | git diff --stat：35 files changed, 213 insertions(+), 248 deletions(-)；git diff --cached --stat 无输出。 |
| 未跟踪 | .tmp-anchor-check.py；基线副本与 SHA-256 单独保存，不暂存。 |
| 可恢复快照 | /tmp/khatkit-t1-baseline-XXwzTStn/：HEAD、status.z、unstaged.patch、staged.patch、untracked.tar、tracked.sha256、untracked.sha256、index.txt、inventory.json、compile.log、compile.exit。SHA-256 以实际工作树内容为准；untracked.tar 保存原文件。 |
| 基线构建 | ./gradlew :app:compileDebugKotlin，退出码 0；BUILD SUCCESSFUL，139 actionable tasks 均 UP-TO-DATE（compile.log:206–210）。未发现编译阻塞；这是初始 HEAD 的开工基线，不代表后续共享分支变化后的验证。 |

共享工作区在本次审计进行中继续前进：最终核对时 HEAD 为 985043835dab6a5df683d02e50a9219fcb81cc6d（origin/main），其前一提交为 fdf228ade；tracked 工作树现无修改。初始跟踪文件快照中有 4 个 hash 与现状态不一致：MochiSettingsPageScaffold.kt、MochiShell.kt、MochiTopBars.kt 三个原有 WIP 后续随共享提交变化；Theme.kt 不在开工 status 中，是快照后新进入提交的文件。初始 status/patch/hash 已保存在上述 /tmp 目录；本任务没有 reset/revert/reapply，也没有改动其余源文件。当前新增的只有本计划文档（原有 .tmp-anchor-check.py 仍未跟踪）。如后续要检查可重现构建，需在最新 HEAD 重跑 compile，不能把开工的 exit 0 当作新 HEAD 证据。

开工时的 35 个已修改文件（下列均是原有 WIP，不归本计划所有）：
- app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/MochiPageBars.kt
- app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/mochi/MochiSettingsPageScaffold.kt
- app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/mochi/MochiTabPageScaffold.kt
- app/src/main/java/heizige/kk/khatkit/app/core/ui/components/webview/WebView.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/assistant/AssistantPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/assistant/detail/AssistantDetailPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatList.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/download/DownloadCenterPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreCardDetailPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/extensions/PromptPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/extensions/skills/SkillsPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/history/HistoryPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/imggen/ImgGenPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/search/SearchPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingFilesPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingModelPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingPermissionsPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingProviderDetailPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingSearchPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingSpeechPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/settings/provider/ProviderModelList.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/stats/StatsPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/tools/ToolCataloguePage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/ui/shell/MochiShell.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/webview/WebViewPage.kt
- app/src/main/java/heizige/kk/khatkit/app/feature/workspace/WorkspaceFileEditorPage.kt
- khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/KhatKitPage.kt
- khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/PagedNodeTreePage.kt
- khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/mochi/MochiTitleScroll.kt
- khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/mochi/MochiTopBars.kt
- mediapicker/src/main/java/heizige/kk/khatkit/mediapicker/ui/KhatKitMediaPicker.kt
- mediapicker/src/main/java/heizige/kk/khatkit/mediapicker/ui/components/MediaPickerGrid.kt
- mediapicker/src/main/java/heizige/kk/khatkit/mediapicker/ui/components/MediaPickerGridWithOverlays.kt

## 1. §1 包结构：按 feature 内聚，共享再提升

现状由当前跟踪源码重算：:app 下 app/core 有 data=216、di=4、network=17、ui=319、util=25，共 581 Kotlin 文件；app/feature 有 29 个功能包、242 Kotlin 文件；:app 共有 841 个 main Kotlin 文件。Settings 仍在 app feature 中，不是 Gradle 子模块。:oauth 是独立 Android 库；文件操作在 app/core/data/files；本仓不存在 core/auth/core/files 兄弟目录。21 个 Gradle include 见表 0 与 settings.gradle.kts:68–91。

按 feature source 的显式 import 做了候选筛查：581 个 core 文件中，144 个被至少两个 feature 显式引用，180 个仅被一个 feature 显式引用，257 个未发现直接 feature import。该筛查不等于自动搬迁清单（未计算反射、Hilt/DI、core 间调用、测试、通配符的全部语义）；但“core 必须由 2+ 功能共享”是硬判断，不能据此把单使用者继续无期限留在 core。

目标与文件范围：app/src/main/java/heizige/kk/khatkit/app/core/**、feature/**、根级 RouteActivity.kt/KhatKitApp.kt。按每个符号的生产调用者跨不同 feature 计数；只被一个功能使用且没有公开共享契约的模型、repository、helper、页面状态移入该 feature 的 data/helper/根目录；确认两个以上功能使用后才留 core。先追踪调用链与 Hilt 绑定再移动。

确定的依赖方向问题：app/core/data/terminal/TerminalPluginStore.kt:7 依赖 app/feature/extensions/TerminalPluginInfo；core 不应依赖 feature。迁移时把持久化所需的稳定 ID/record 类型放在数据所有者处，移除 core→feature 依赖；不要改 DataStore name、stringPreferencesKey、序列化字段或持久化内容。含用途不明、无直接导入的 core 项，先搜索全仓类型引用、DI、反射入口与测试；确认单功能后归位；只有找到真实共享或稳定模块 API 证据才留 core，并把证据记入迁移审查表。不能以“不单独大迁移”结束判断。

## 2. §2 命名：名称反映职责，Android 组件名称保留

当前主源码名后缀核对：Service.kt 9 个，均为平台语义服务（Service、TileService、AccessibilityService 或 NotificationListenerService）；不要按通用名规则更名。它们是 app/core/network/WebServerService.kt、app/feature/automation/{AutomationOverlayService,KhatKitAccessibilityService,KhatKitNotificationListenerService,TriggerService,TriggerTileService}.kt、app/feature/chat/ChatGenerationForegroundService.kt、khatkit/.../KhatKitDownloadService.kt、oauth/.../OAuthCallbackForegroundService.kt；manifest/service 启动、Notification 和 PendingIntent 的注册要同步，除非确有平台组件语义不变的必要，否则保留原名。Util.kt / Utils.kt 当前第一方名为 0。

当前 4 个 Store 后缀中，app/core/data/ocr/OcrPluginStore.kt、ssh/SshPluginStore.kt、terminal/TerminalPluginStore.kt 都把安装记录写入/读取 DataStore，应按 §2 的数据职责改名为 Repository（文件、类、调用点、DI、测试同一事务改）；PlayStore.kt 是 Google Play 专名而非数据存储，保留。不得改 installed 字段、DataStore 命名、持久化 schema 或 provider 的 wire 值。其余 Manager/Helper/Repository/Extensions 按有状态流程、无状态副作用、数据访问、纯函数逐个审职责，不做机械全局替换。

目标文件：上述 3 个 Store 源文件与所有引用、注入绑定、测试；9 个 Android 服务及 AndroidManifest.xml/PendingIntent 调用点仅作为保护名单；其他候选从 app、khatkit、search、oauth、common 的 Kotlin 声明、文件名与调用处交叉核查。KhatKit 第一方包名前缀保持 heizige.kk.khatkit；上游导入包和 Android framework 语义不因命名规范误改。

## 3. §3 icons：共享图标集中且按语义分组

当前有 153 个 Kotlin 图标实现全部平铺在 app/core/ui/icons/ 根目录（目录分布实测只有 .，没有语义子目录），位置满足“跨功能资产不放单一 feature”，但组织不满足“按语义分子目录”。不少图标有跨 feature 引用；保留共享资产归属，先从调用处核对使用场景，再按 chat/social/media/utility/animatedicons 等语义分组，维护所有 import 与测试。不可因单个页面使用一次就把图标复制出第二份。

范围：app/src/main/java/heizige/kk/khatkit/app/core/ui/icons/**，以及引用这些图标的 app feature 源码。app/src/main/assets/icons/** 属于服务商/品牌图片资源，不是 Kotlin 图标符号，保留原资产和许可；不得替换成 emoji 或随意移入 feature。material3/material-color-utilities 是 .gitmodules 指定的上游 git submodule，不纳入第一方图标整理。

## 4. §4 helper 去向：按使用者和职责，不按目录好看

现有 app/feature 有 22 个 helper/** Kotlin 文件，主要在 chat、automation、backup、lanshare、tools；局部 data/** 已用于 chat、automation、settings、workspace。它们体现功能内聚；应覆盖全部 29 个 feature，筛出仍放 feature 根部的 helper/data 职责文件，以及 core 中前述 180 个单功能导入候选。

目标文件：app/src/main/java/heizige/kk/khatkit/app/feature/** 与 core/**；需要按“数据模型/持久化→data、无状态且有副作用打杂→helper、screen/ViewModel→feature 根、真正多功能共享→core”逐文件分类。额外检查 TerminalPluginStore 反向依赖、feature 根部多职责文件、孤儿目录。确认删除功能才整目录删；不要把页面、屏幕状态或只服务一个 feature 的 helper 因分层偏好提升到 core。

## 5. §5 版本目录与 bundles：单一版本来源并保留配置语义

gradle/libs.versions.toml 是主版本目录，当前 21 个 include 中现存唯一 bundle 为 androidTest=[androidx-junit, androidx-espresso-core]（libs.versions.toml:221–223），已用于 ai、app、common、document、speech、web、workspace 共 7 个模块，均为 androidTestImplementation。仅在至少两个 artifact 且每个使用者 configuration、api/implementation 语义一致时才加 bundle；不能把 api 与 implementation 包成同组。

检测到模块脚本中仍有直接版本：app/build.gradle.kts:196 的 exifinterface:1.4.1；plugins/ocr-mlkit/build.gradle.kts:17 的 ML Kit 16.0.1；settings.gradle.kts:33 的 Foojay 插件 1.0.0。plugins/terminal/build.gradle.kts、workspace/build.gradle.kts 中 CMake 3.22.1 属原生构建工具链版本，不当作 Maven 库 bundle。将依赖坐标版本集中至 TOML 的 [versions]/[libraries]（插件则使用 [plugins] 或可兼容的 settings 写法）；保留固定 sentinel、构建工具与用户数据版本的语义，不把任意数字归进库版本。任何 TOML 修改前先比对实际 resolution 与模块 API。

目标文件：gradle/libs.versions.toml、app/build.gradle.kts、plugins/ocr-mlkit/build.gradle.kts，以及需要调整的插件解析入口；只有新依赖时再碰模块脚本。现有 androidTest bundle 满足重复与同 configuration 证据；不新增其他 bundle，除非新 census 给出一组可复核的精确配置证据。

## 6. §6 技术栈：以本仓当前 catalog 和兼容验证优先，不按模板倒退

本仓版本目录实测：AGP 9.3.1、Kotlin 2.4.20、Compose BOM 2026.09.00、Material3 1.5.0-alpha29、Navigation3 runtime/ui 1.2.0、lifecycle-viewmodel-navigation3 2.11.0、Ktor 3.5.2、DataStore 1.2.1、Hilt 2.60.1、serialization 1.11.0、coroutines 1.11.0、Mochi 0.4.0-khatkit.2（libs.versions.toml:3–78）。对照 ../ARCHITECTURE.md §5 的“多项目依赖取较新版本，只同步 versions，不混同步依赖”和 §6 的锁定表：Ktor/Hilt/serialization/coroutines 已相同；Navigation3 当前稳定线高于模板 alpha；DataStore 当前稳定 1.2.1 不应为采用 alpha10 版本号而自动回退/升级；lifecycle bridge 需做 API 依赖图比对。先确认 release metadata、Mochi/AGP/Kotlin 兼容、源码和编译，再决定对齐，不能把 KodeHead/Cynic 模板表原样复制到 KhatKit。

兼容基线 ./gradlew :app:compileDebugKotlin 退出码 0，现有状态全部 up-to-date。目标验证至少包含该任务及受影响模块 test/assemble；这是实际版本能在当前缓存构建的证据，不替代依赖锁定/CI 冷构建，也不构成升级许可。KhatKit 实际包名为 heizige.kk.khatkit；Mochi 是实际 Compose UI 与导航依赖，不能按旧上游 README 或旧 Nav3 API 反向替换。

## 7. §7 快速决策卡：把本仓适配变成迁移入口

目标是保留单一决策入口：新功能→app/feature/<name>；只有跨 2+ 功能→app/core；Manager/Repository/Helper/Extensions 按职责；Android framework component 保留平台名；共享图标归 core/ui/icons 但按语义分组；库版本放 catalog；所有宿主用户看见的字串进本 UI owner 的 Android R.string、默认英文与简中并行。该决策需涵盖所有第一方 Gradle 模块，不只 app 源码；功能包是 Kotlin 包不是 Gradle 模块。

目标文档范围：本计划 + 后续集成时更新仓库 AGENTS.md（其存量数量、Mochi 导航说明要跟本次实测同步），不要改 KhatKit applicationId、KhatKit 包名前缀、Android Manifest 组件名或外部协定。计划范围外的源变更须由后续独立 task 合同承接。

## 8. §8 预测性返回：保留原理，按当前 Mochi 路由落实

当前 app/RouteActivity.kt:306–355 用 rememberMochiNavigator、MochiNavHost，并以设置开关及有无 overlay 控制 predictiveBack；app/src/main/AndroidManifest.xml 已启用 enableOnBackInvokedCallback。导航仍走 Mochi，不得重建 NavDisplay。Mochi 接入/设计文档要求单一 MochiNavHost、页面转场跟 transition tree、由库处理 commit/cancel；页面不得自行取回进度来模仿 route pop。

现存适配差距：RouteActivity.kt:191 和 SafeModeActivity.kt:114 仍调用 enableEdgeToEdge()，应与根主题及 Mochi edge-to-edge 能力核对；仓内检索有 6 个 PredictiveBackHandler（app/core/ui/hooks/PredictiveSearchBack.kt、app/core/ui/components/ai/ChatInput.kt 两处、mediapicker 的 KhatKitMediaPicker.kt 两处与 MediaPickerGridWithOverlays.kt），且 event.progress 被 3 处读取（PredictiveSearchBack.kt:55、ChatInput.kt:407、:924）。证据表明这些是搜索收起/输入面板/选择器的局部状态手势，不是 RouteActivity 的 route pop；媒体选择器只在手势结束时提交。后续必须保留 overlay 局部手势的 commit/cancel 体验，并把返回优先级纳入 PageBackInterceptor/PageBackGuard；逐一证明 event.progress 只服务 overlay 内容形状，不驱动 route 的进入/退出形变。Root RouteActivity 的 BackHandler(enabled=hasOverlay) 是 overlay 适配，不可与页面自挂 route handler 混为一谈。

验收：主路由的 MochiNavHost 是唯一 route-pop owner；Android 平台手势开关与 manifest 兼容；取消不 pop、提交才 pop；progress=1 的 overlay 幅度恰为其终态且松手后续播；没有页面级 BackHandler/PredictiveBackHandler 另行争抢 route；页面内容/圆角过渡不由 NavigationEvent.progress 独立驱动。文件范围：RouteActivity.kt、SafeModeActivity.kt、PageBackInterceptor/PageBackGuard、PredictiveSearchBack.kt、ChatInput.kt、mediapicker KhatKitMediaPicker.kt/MediaPickerGridWithOverlays.kt；仅按实际违反项修改。

## 9. 宿主文案资源化：范围与盘点方法

规则优先级：AGENTS.md:321–374；所有用户可见文本进 R.string，不再使用“存量不单独大迁移”豁免。Compose 从所属 Android UI 模块 stringResource；非 Compose Service/Worker/Notification/普通 helper 用 context.getString。资源 key 使用小写 snake_case 与 common/page/dialog/toast/notification/error/a11y 前缀；加字符串先复用现存同义 key，再同步 values/ 英文默认与 values-zh/ 简中；位置占位符使用 %1$s/%1$d 等，数量按语义用 plurals，不拼接句子。

覆盖面不能只扫 Text("中文")。对每个第一方 production Kotlin source、Android manifest/layout/xml 与资源逐批识别：Text/MochiBlurText/标题/设置摘要/列表 DSL label/title/subtitle；间接 enum/provider displayName、screen route 名、状态映射、对象 toString、空态与 validation；placeholder/helperText、TextField 默认提示、contentDescription/stateDescription/semantics、Icon/菜单/导航标签；Toast、Snackbar、AlertDialog/MochiDialog/错误弹窗、Permission/结果提示；Notification channel/title/body、Quick Settings tile、foreground service、Widget/shortcut；宿主卡片 DSL 的按钮/确认/表单默认值、Card UI host 提示；异常消息被 UI 捕获后呈现的包装说明。还要追踪调用者如何把动态字段从 ViewModel、repository、KhatKit DSL、provider enum、异常和 server response传到屏幕。当前未命中 Android XML 直接属性文本；所有 XML 属性和资源引用仍须复查。

为定位工作量，本次保守扫描了 git 跟踪的生产 Kotlin：1,308 个文件、32,904 个字符串字面量；广义 CJK/短语/用户文本候选为 5,393 条、599 个文件，其中 2,449 条含 CJK、255 文件；候选 UI/通知/报错等直接 sink 的 CJK literal 初筛 544 条、123 文件。它是高召回定位表（含日志、异常、协议/模型文本和上游代码），不是 5,393 条都应搬，也不是 544 条就是最终 UI 量；每条必须逐个确认调用上下文。所有无法判清的候选默认先 trace 调用链；确认被宿主渲染则纳入资源化，不确定属于协议/提示词则保持原字节并记录拥有者/消费者/排除理由，未有证据不得仅凭字面像常量而排除。探查生成资产、测试、build 输出 separately。

逐模块的同一 lexical screen 计数（依次为短语/CJK候选、其中含CJK、CJK字面量靠近直接sink、三引号多行、含$插值；均为候选，不替代用途判读）：

| include | 短语/CJK | CJK | direct-sink CJK | 多行 | 插值 |
|---|---:|---:|---:|---:|---:|
| :ai | 142 | 7 | 0 | 0 | 61 |
| :app:baselineprofile | 3 | 1 | 1 | 1 | 0 |
| :app | 3,502 | 1,801 | 484 | 118 | 661 |
| :card-validator | 162 | 79 | 0 | 0 | 65 |
| :common | 29 | 2 | 1 | 1 | 6 |
| :document | 58 | 0 | 0 | 0 | 7 |
| :highlight | 265 | 0 | 0 | 38 | 12 |
| :image-toolbox-dependency | 53 | 53 | 11 | 0 | 35 |
| :khatkit-ui | 101 | 36 | 9 | 0 | 16 |
| :khatkit | 431 | 335 | 19 | 0 | 197 |
| :material3 | 0 | 0 | 0 | 0 | 0（其 3rd-party git submodule 未被 git ls-files 扫描） |
| :mediapicker | 16 | 0 | 0 | 0 | 1 |
| :oauth | 26 | 7 | 1 | 1 | 4 |
| :plugins:ocr-mlkit | 0 | 0 | 0 | 0 | 0 |
| :plugins:ssh | 8 | 8 | 0 | 0 | 2 |
| :plugins:terminal | 0 | 0 | 0 | 0 | 0 |
| :search | 156 | 6 | 1 | 3 | 38 |
| :speech | 186 | 26 | 13 | 1 | 52 |
| :web | 0 | 0 | 0 | 0 | 0 |
| :workspace | 55 | 0 | 0 | 0 | 35 |
| :yunx-parser | 200 | 88 | 4 | 14 | 28 |

### 可复核的现存资源基线

| 模块 | 当前默认 values/ | values-zh/ | 实测差异与动作 |
|---|---:|---:|---|
| app | strings.xml 1,882 | strings.xml 1,866 | 16 个默认 key 缺简中：backup_page_import_deepseek_complete、backup_page_import_deepseek_desc、backup_page_import_deepseek_missing_attachment、backup_page_import_deepseek_read_error、backup_page_import_deepseek_summary、backup_page_import_from_deepseek、chat_message_tool_phone_use、setting_display_page_split_assistant_bubble_desc、setting_display_page_split_assistant_bubble_title、setting_model_page_auto_compress_desc、setting_model_page_auto_compress_threshold、setting_model_page_auto_compress_threshold_desc、setting_model_page_auto_compress_title、setting_provider_page_model_refresh_failed、setting_provider_page_refresh_models、splash_version。逐个补译。 |
| khatkit-ui | strings.xml 81 | strings.xml 81 | key 一致；重复值组 2，先确认同义再复用。 |
| mediapicker | strings.xml 40 | strings.xml 40 | key 一致。 |
| oauth | strings.xml 15 | strings.xml 15 | key 一致。 |
| search | strings.xml 5 | strings.xml 5 | key 一致。 |
| yunx-parser | yunx_strings.xml 442（内容目前为中文） | 无 | 442 条 upstream 字符资源无英文默认/简中限定值；非中文设备回退到 Chinese default。import-upstream.cjs:29–50 会重写该 default 文件，必须先做可再生的 locale/导入适配再翻译，禁止只手改生成结果。 |
| 其他第一方 Android 模块 | 无 values/strings.xml 或无 zh | 无 | 以模块表中真实用户界面 owner 为准，首次有可见 copy 时创建 values/strings.xml 与 values-zh/strings.xml；不为无 UI/仅传递结构化结果的模块空建翻译表。 |

对已有成对 string 资源的占位符签名检查：未发现 % 格式不匹配；app 同值组 150，khatkit-ui 同值组 2，应按语义区分可复用键与独立语境；目前没有 plurals 定义，带数量新文案不得继续拆字符串。资源文件解析无 XML 错误。app/src/debug/res/values/strings.xml 的 app_name 是 debug variant 覆盖（KhatKit Dev），不是同一 source set 重复 key；按产品名/变体规则保留并验证设备显示语言。英文/简中以外 locale 缺项维持 Android 英文回退，但绝不可作为不补 values-zh 的理由。

明确例子：RouteActivity.kt:481–482 的 Text("[开发模式]") 是宿主可见文案，应资源化；app/src/main/res/values/strings.xml 与 values-zh/strings.xml 的上述 16 key 是漏译。app/core/data/ai/tools/KhatKitTools.kt:168–169 为发给模型的工具说明/调用协议（__khatkit_ui/headless/interactive），不是宿主 UI；保持消息字节、键名和协议值，若屏幕另有编辑标题则单独用资源。KhatKit DSL 中由卡片 locale 提供的卡片作者标题/选项值属于用户/卡片数据；宿主控件默认提示、按钮、权限解释及空状态必须用 R.string。

## 10. 纳入模块与第三方/JVM 边界（settings.gradle.kts:68–91 的 21 个 include）

所有第一方 Android user surface 都要盘点；没有用户界面的 SDK 也要搜索字符串是否流入宿主，而不是因“library”跳过。值写入哪个 R 由显示 UI 的 owner 决定；数据/协议模块返回结构化 code，宿主映射成资源。

| Gradle 模块 | 边界与现状 | 文案迁移结论 |
|---|---|---|
| :app | Android app；841 main Kotlin，29 feature；默认/简中资源齐全但缺 16 个 zh。 | 核心宿主 UI、service/notification、manifest 与卡片 host 提示的主资源 owner。 |
| :app:baselineprofile | Android profile/test，仅 2 Kotlin。 | 测试标签、benchmark sample、测试断言不是 app 产品文案；测试若验证产品文案，引用产品 R。 |
| :ai | Android provider SDK/library，46 Kotlin；无模块 UI resource pair。 | provider 协议、request payload 与原始 response 保持；错误若被 app 展示，host 映射 typed error 到 app R。 |
| :card-validator | 唯一纯 JVM 项目模块，Kotlin JVM CLI，9 Kotlin；无 Android R。 | 命令行和校验结果不是 Android UI；暴露 code/result，若 app 显示则在 app 映射。 |
| :common | Android library，23 Kotlin；当前无模块字符串资源。 | 只保留通用接口/结构数据；用户可见字串由消费方 owner 加资源。 |
| :document | Android library，17 Kotlin；无资源表。 | 文档/格式协议值保留；若解析错误呈现给宿主，映射 error code 到 app 资源。 |
| :highlight | Android Compose library，45 Kotlin；无资源表。 | 代码样本、token、源码文本不是 UI copy；检查标注/错误/空态，归实际 host R owner。 |
| :image-toolbox-dependency | Android library，7 Kotlin；产物为单独 Dex 依赖，通过 card bridge 调用，不随基础 APK UI 资源链路打包。 | 本模块 bridge 命令名、输入/输出、卡片可见运行结果属于插件协议/数据；host 自己渲染的确认/等待/错误说明在 app R，禁止把生成 Dex 或第三方库字符串误当资源。 |
| :khatkit | Android library，90 Kotlin；card runtime / model / bridge，没有自有 values 目录。 | 逐一筛 DSL host component 的默认文本；确认被 Compose host 展示的 label/placeholder/empty/error 在此模块新增默认/zh R 资源。card-author 提供的 locale 内容和 bridge wire 参数除外。 |
| :khatkit-ui | Android Compose library，39 Kotlin；资源 81/81。 | module 自己可见的 DSL node host、按钮、可访问性、占位/错误文案用本模块 R；保持 app 与第三方卡片值为动态数据。 |
| :material3 | Android adapter library，1 tracked Kotlin；material-color-utilities 子目录为 .gitmodules 外部上游 Git submodule。 | 只审本仓 glue；子模块是 third-party，不改、不计第一方字符串，不复制/重命名供应商代码。 |
| :mediapicker | Android UI library，30 Kotlin；资源 40/40。 | 选择器 UI 的标题、筛选、空状态、按钮、a11y、权限失败、toasts 均由本模块资源负责。 |
| :oauth | Android library，4 Kotlin；资源 15/15。 | OAuth host-facing UI/通知 copy 归本模块资源；OAuth URI/state/nonce、授权字段、provider 原始响应/协议错误码保持不变。 |
| :plugins:ocr-mlkit | Android 动态插件 library，1 Kotlin；无资源。 | OCR 操作返回结构化状态；host 展示权限/失败提示时由 app 资源映射，不让插件 API 错误文案兼任 UI label。 |
| :plugins:ssh | Android 动态插件 library，1 Kotlin；无资源。 | host 设置/连接失败说明由 app R；SSH protocol output 与 server error payload 不翻译/改 wire。 |
| :plugins:terminal | Android 动态插件 library，1 Kotlin；无资源。 | host 设置、授权和下载提示由 app R；终端输出/命令/异常结构不是普通 UI copy。 |
| :search | Android Compose library，21 Kotlin；资源 5/5。 | 搜索 provider 选择 UI 用本模块 R； provider name/endpoint/JSON/capability id 保留。 |
| :speech | Android Compose/library，39 Kotlin；无资源表。 | TTS/ASR protocol、模型和引擎输出保持数据；设置页面标签/错误/空态由该 screen owner 的 R；若控件在 app，放 app 资源。 |
| :web | Android server library，1 Kotlin；无资源。 | REST/MCP 错误码/JSON/HTTP 文本是外部 wire contract，不进入 Android R；内部宿主设置与运行通知在 app 本地化。 |
| :workspace | Android library，7 Kotlin；无资源。 | shell stdout/stderr、workspace 路径、命令输出是用户/AI 数据；host 生成的提示和错误包装资源化。 |
| :yunx-parser | Android library，81 Kotlin；Pinned upstream CYQawa/YunX commit 2381a529…，AGPL-3.0-or-later；有 upstream importer/许可证/适配器。 | upstream 协议 Kotlin 不做命名/包迁移；AppStrings.kt:5–10 明确通过宿主 Context.getString 跟随 locale。442 个 string key 的语义文案必须使默认资源英文、values-zh 保留简中。先修改第一方 tools/import-upstream.cjs 让再导入不会覆盖本地语言策略，再从 pinned input 生成 Zh 文件/默认英文映射；保持 resource name、占位符与 upstream source ID，不改业务协议。上游 LICENSE/namespace 保留。 |

项目 includeBuild build-logic（settings.gradle.kts:2）是纯 JVM Kotlin Gradle 插件工具，不是产品 UI 模块；构建错误/插件 DSL/日志不入 Android R。测试源码、debug-only assertions、build/**、生成/编译物、依赖 source/JAR/AAR/Dex 不按第一方 UI 字串批量迁移。若第一方向这些输出写 human-facing label，仍由实际宿主资源 owner 本地化。

## 11. 文案执行批次与资源文件串行顺序

共享 strings.xml 必须单写入者、资源文件按以下顺序逐项提交，禁止并行 agent 在同一 XML 上改 key。每项先确认既有 key，再在默认英文 values 文件新增 key，随后立即在对应 values-zh 文件提供简中，之后引用 R、修占位符、编译并做 key/format parity；不能攒英文到最后再补中文。

| 顺序 | 生产源范围 | 资源对（默认英文 → 简中） | 批次重点 |
|---:|---|---|---|
| 0 | 全部第一方模块/界面 sink 的 census 与 owner 标注；保存文件/行号/用途/locale/占位符/排除理由。 | 不写 XML | 先识别宿主 UI、资源入口、dynamic label producer、外部值；保护 WIP。 |
| 1 | app 根、core/ui、29 个 feature、manifest/component、service/notification、error wrapper。 | app/src/main/res/values/strings.xml → app/src/main/res/values-zh/strings.xml | 先修 16 条缺 zh；完成 Text/Compose、列表/设置项、占位/a11y、Toast/dialog/snackbar、service/channel/tile/manifest 等；所有 29 个 feature 目录都要走查，不只 WIP 文件。 |
| 2 | khatkit-ui 所有 DSL host Composable、node/default widgets。 | khatkit-ui/src/main/res/values/strings.xml → khatkit-ui/src/main/res/values-zh/strings.xml | 以现有 81/81 key 为起点补正文漏项并清重复语义。 |
| 3 | khatkit/src/main 下 Card UI DSL/runtime 与 Host defaults。 | 新建 khatkit/src/main/res/values/strings.xml → 新建 khatkit/src/main/res/values-zh/strings.xml（若本批确认 host-visible defaults） | 只迁宿主通用标签；CardManifest/card locales/user-defined titles、bridge key/AI tool prompt 仍是数据。 |
| 4 | mediapicker/src/main/java/**/ui/**。 | mediapicker/src/main/res/values/strings.xml → mediapicker/src/main/res/values-zh/strings.xml | 保持现有 40/40；覆盖网格、相册、搜索、筛选、选择、权限、a11y、错误提示。 |
| 5 | oauth/src/main 的 host UI/notification sinks。 | oauth/src/main/res/values/strings.xml → oauth/src/main/res/values-zh/strings.xml | 维持 15/15 并核查 URI/协议不动。 |
| 6 | search/src/main/java/** 的 UI sink。 | search/src/main/res/values/strings.xml → search/src/main/res/values-zh/strings.xml | 维持现有 5/5；provider id 作为 protocol 保持。 |
| 7 | 非 UI libraries 的 string producer/host boundary；依次 ai → common → document → highlight → speech → web → workspace → plugins/ocr-mlkit → plugins/ssh → plugins/terminal → image-toolbox-dependency。 | 默认在显示 UI 的 owner（通常 app）增一对资源；只有模块自己实际拥有 Android UI 时才新建其 values/values-zh 对；不为纯 protocol 模块生成空 XML。 | 将错误种类/data code 与 user copy 分开；追查 Chat tool result/notification/exception。每个模块独立完成两份资源和引用后才进入下一个模块。card-validator/JVM 没有 Android R，只返回机器可判定结果。 |
| 8 | YunX first-party import adapter 与经确认可展示的 parser errors。 | 先调整 yunx-parser/tools/import-upstream.cjs 的导入策略；再生成/校验 yunx-parser/src/main/res/values/yunx_strings.xml（英文默认）→ 新增 yunx-parser/src/main/res/values-zh/yunx_strings.xml（简中） | 保留 442 R 名称与 % 格式；上游更新必须由 pinned importer 流程重现，禁止仅编辑 importer 会覆盖的 generated snapshot。 |

源文件改动可以按不同模块审查，但资源 XML 的写入严格按表顺序；每批结束执行 XML parse、默认与 zh key diff、占位符多重集及 plurals quantity 检查，确认模块 compile，再进入下一资源对。用户数据/第三方返回文本动态插值；不得从 app 主资源跨模块引用 R。模块没有自身 UI 的异常只返回 typed error，所有自然语言由最靠近屏幕的 UI owner 产生。

## 12. 排除项与有条件纳入项

| 类型 | 判定/理由 | 处理 |
|---|---|---|
| wire/protocol、URI、Intent action/extra、字段名、HTTP/MCP JSON、bridge method、卡片 DSL 参数、DataStore key、Room entity/column、Enum/SerialName、版本号/工具名 | 改文字会破坏网络兼容、持久化与脚本调用。 | 原始 key/value 保持字节级兼容；只把旁边真正显示的说明、标签或错误包装资源化。 |
| 模型提示词/系统 prompt/工具 schema/example/prompt template 与发送给 provider 的文本 | 用户看到的是模型输出不是该程序 UI；迁到 R 会改变模型输入与行为。 | 保留 payload；Prompt 编辑页标题/摘要/按钮/占位符属于 UI，仍迁资源。 |
| Log、println、崩溃 diagnostics、异常 message（未展示） | 仅面向诊断/程序消费；日志内容可作本地排障接口。 | 保留日志/堆栈；若 catch 后以 Text/Toast/notification 呈现，则本地化 host wrapper，不暴露未经净化异常。 |
| third-party/generated | material3/material-color-utilities submodule；yunx-parser 按 pinned upstream 导入的 protocol Kotlin；Mermaid minified asset；外部 artifact AAR/JAR/Dex。 | 保留 source、license、名称、namespace、import pipeline；只做隔离的第一方适配，未有授权不批量重写 vendor。YunX 442 个通过 AppStrings 面向宿主 locale 的 R 字符串按上一节安全处理。 |
| 用户/外部数据与卡片 localization | 用户 prompt、聊天内容、文件名、provider name、服务端/用户配置、Card locales 是运行数据。 | 不拿 R 值覆盖动态数据；宿主 shell labels 用 R。原样保留并作为参数传入格式字符串。 |
| 测试和示例 fixture | 验证夹具/快照不是正式 UI；用于验证真实资源的测试必须读取 R key。 | fixture 可固定输入输出；新增 UI snapshot 使用产品字符串资源，不把断言本身翻译。 |
| 无法判定的静态字面量/間接 displayName | 文本外观不足以判断是否 UI。 | 追至调用者与运行时；渲染到主界面/通知/弹窗即资源化。若证实只作协议/模型/日志则记录 file:line、消费者、证明和保持字节理由；未证实不得标完成。 |

## 13. 后续验收门

1. §1–§8 每项均按“当前证据→要达到的结果→受影响文件→本仓适配”审查；文件路径移动后更新 references、test/DI、resource R，且保留 KhatKit 包名与 Android component 语义。
2. 基线/完成分别记录 HEAD 与原始/最终 git diff；初始 35 M + 1 ?? 与 /tmp patch/hash 必须保留。集成者只暂存自己文件。
3. 全仓 text scanner 的所有候选逐一归类，无法判定按 §12 继续 trace；resource QA 不只跑正则 Text("中文")。验证 app 与每个受影响 Android library 的 compile、unit tests、资源 XML/链接、en/zh key parity、格式参数和 plural 规则；纯 JVM :card-validator 单独跑 JVM 单测并证明 host map。
4. 翻译审阅 default English + values-zh 中文；关键路径覆盖语义无障碍、系统字号、通知/tile、动态 placeholder、异常包装、卡片 host 与用户输入边界。设备验收仍须由用户执行，不自动操作手机。
5. 任何 upstream importer 重新生成、版本/TOML、资源 key、schema 或 build 集成超出本 plan 的任务合同都拆成后续 task，并在开始前依赖此计划与 WIP 差异快照。
