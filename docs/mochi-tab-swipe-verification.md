# Mochi 底栏分页验证记录

日期：2026-11-28。范围：6 个应用入口（13 个固定标签页）和 1 条卡片公共分页链。这里只将编译、JVM 测试、源码覆盖和设备交互分别记账；**未执行任何设备操作或视觉/手势验收**。

## 1. 首轮正式结果（t4，5 个偏好页 WIP 修复后）

首轮 app 编译被他人 i18n WIP 的 19 个 Compose 资源上下文错误阻止（§2.1）；captain 安排 t11 做最小修复后本轮按合同串行复跑，19 处不再复现，得到下表（具体由哪一修复消除未在本任务逐行核验）。日志与退出码均落在 /tmp/t4-*；app 与卡片 UI 单测同一 Gradle 轮次执行。

| 命令（KhatKit 根目录） | 实际结果 | 证据 |
|---|---|---|
| ./gradlew :app:compileDebugKotlin | **退出 0**，9s | /tmp/t4-compile.log:212 BUILD SUCCESSFUL；:app:compileDebugKotlin 实际执行（非 UP-TO-DATE），末尾只有 1 条既存 Navigation3 opt-in 警告。 |
| ./gradlew :app:testDebugUnitTest :khatkit-ui:testDebugUnitTest | **退出 1**，22s | /tmp/t4-tests.log:372 app `1167 tests completed, 9 failed`；:374 task FAILED。:app 1167/9/0/0，:khatkit-ui 13/0/0/0（app/build/test-results、khatkit-ui/build/test-results XML 汇总）。9 项失败全部是本轮命令之前的既有基线，见 §2.2。 |
| git diff --check | **退出 0** | 全 tracked diff 无空白错误。 |
| :app / :khatkit-ui dependencyInsight（debugCompileClasspath，mochi-compose） | **退出 0** | 两者均解析 `io.github.feice11:mochi-compose:0.4.0-khatkit.3`，BUILD SUCCESSFUL；不以 toml 文本替代实际解析。 |

> 第二轮（t14）复跑状态见 §6：当前主树被并行 i18n WIP 阻碍，编译未过，本节结果仍为最近一次完整通过记录。

卡片 UI JVM 测试为已有套件（CounterButtonStateTest 2、FormPickerRequestTest 8、UiRegistryTest 2、UiIconVectorsTest 1），**13 tests / 0 failures**；其中没有 Depth 转场或手势的自动化测试。

**边界**：app 单测退出 1 是既有基线失败，不是本轮迁移引入，也不是“通过”；编译与 JVM 单测都不构成真机视觉/手势验收（§5 全部未执行）。

## 2. 失败归因

### 2.1 首轮 app 编译失败（本轮不再复现）

5 个文件、19 个位置均报“@Composable invocations can only happen from the context of a @Composable function”：

| 文件 | 首轮错误行（同一行多列另计） |
|---|---|
| [SettingPreferencesGeneralPage.kt](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingPreferencesGeneralPage.kt#L224) | 224、243（2） |
| [SettingPreferencesThemePage.kt](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingPreferencesThemePage.kt#L153) | 153、155、156、157、190、191 两列（7） |
| [SettingPreferencesUIFontPage.kt](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingPreferencesUIFontPage.kt#L228) | 228（1） |
| [SettingPreferencesUIMessagePage.kt](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingPreferencesUIMessagePage.kt#L105) | 105（1） |
| [SettingThemeSubPages.kt](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingThemeSubPages.kt#L237) | 237、238、329、330、400、414、426、552（8） |

归因证据：/tmp/khatkit-t4-4af3aed2-baseline.diff 是这 5 个文件相对 HEAD 的只读 diff；错误正落在新增的 `stringResource` 调用，属于独立 i18n WIP，不涉及 t2/t3 导航分页。本轮同一命令已 exit 0，证明这 19 处已修复且与迁移无关；不改实现，也不把“首轮失败”遗留成当前结论。

### 2.2 第二轮 9 项 app 单测失败（既有基线，逐条归因）

失败消息与 /tmp/khatkit-t4-4af3aed2-stale-app-failures.json（mtime 1795858096，早于本轮任何命令）**9/9 归一化后逐字一致**；且不涉及本次改动的 9 个源文件。

| 失败用例 | 实际原因 | 基线证据 |
|---|---|---|
| BrowserExampleCardsTest 3 项 | 卡片仓已迁移 WASM：`../../../KhatKitCards/browser_compare_prices/main.js`、`browser_checkin/main.js` 不存在；card.json 新增 `limits` 字段被旧解析器拒绝 | 外部卡片仓状态，与本仓分页无关；本轮未改 app 解析器 |
| CardRunsSchemaTest / ConversationGroupCardsSchemaTest | 断言 `AppDatabase.kt` 版本 37，而 [AppDatabase.kt:70](../app/src/main/java/heizige/kk/khatkit/app/core/data/db/AppDatabase.kt#L70) 已是 38（HEAD 即 38，该文件无工作树改动） | `git show HEAD:…/AppDatabase.kt` 输出 version = 38；测试期望滞后 |
| BottomSheetScrollSourceGuardTest / PrimaryBottomSheetIconSourceGuardTest | 期望 46 个 PrimaryBottomSheet 调用点，实际 45 | 对 app/src/main、khatkit-ui/src/main 全部 tracked 变更逐文件比对，`PrimaryBottomSheet(` 计数**总 delta = 0**（无任何调用点被删）；未跟踪新文件也无该符号 → HEAD 计数同为 45，基线与本次无关 |
| AIIconAssetsTest 2 项 | 要求单色 SVG，但 HEAD 已提交的 `app/src/main/assets/icons/sensenova-color.svg` 含 #06FDB7、#5B2AD8 两种色值 | `git show HEAD:…sensenova-color.svg` 同样两种色值，文件无改动 |

首次读取 app XML 得到旧报告 1166 项 / 9 失败；本轮 1167 项 / **同样 9 项失败**（多出的 1 项为新增且通过）。**不擅自放宽任何断言**，不把基线失败伪报为通过。

## 3. 迁移覆盖与排除核对

需求基线：[覆盖文档](mochi-tab-swipe-coverage.md#L13)。本轮实测：全仓 `HorizontalPager(` 只剩 **7** 处，正是覆盖文档 §3 的排除清单逐项对应（BackupPage、AssistantExtensionsPage、DebugPage、ExtensionSelector、McpConfigModal、ProviderModelList、KhatKitMarketContent），**7/7 未误改**；另有 2 个 Zoomable 图片宿主保持原浏览用途（ImagePreviewDialog、MediaImagePager）。

应用 6/6 入口各调用一个公共 [MochiNavPages.kt:64–86](../app/src/main/java/heizige/kk/khatkit/app/feature/ui/shell/MochiNavPages.kt#L64)，:70–77 直接转发库 `MochiPeerTransition + MochiPeerTransitionSpec.Depth`，无应用手势识别器；单一可保存选择来自库 MochiTabScaffoldState（:27–55）；重复点击回顶登记 :97–113。

| 入口 | 当前转场位置 / 标签页 | 源码核对结论 |
|---|---|---|
| [ImgGenPage.kt:188](../app/src/main/java/heizige/kk/khatkit/app/feature/imggen/ImgGenPage.kt#L188) | 生成 / 图库（2） | 私有 BottomBar 绑定相同 navPages；isActive 跟随 currentPage，原 padding / consumeWindowInsets 保留。 |
| [PromptPage.kt:154](../app/src/main/java/heizige/kk/khatkit/app/feature/extensions/PromptPage.kt#L154) | 模式注入 / 世界书（2） | 同一 navPages，两个独立列表与 FAB 业务保留。 |
| [WorkspaceDetailPage.kt:213](../app/src/main/java/heizige/kk/khatkit/app/feature/workspace/WorkspaceDetailPage.kt#L213) | 基本 / 文件（2） | 文件页 isActive、导入操作、目录返回判断使用同一当前页。 |
| [SettingSpeechPage.kt:131](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingSpeechPage.kt#L131) | TTS / ASR（2） | 两列表及编辑面板保留，新增按钮跟当前页切换。 |
| [SettingModelPage.kt:70](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingModelPage.kt#L70) | 模型 / 提示（2） | 父级 modelList / promptList 保留；OCR Repository 与图标 import 的独立 WIP 未回退。 |
| [SettingProviderDetailPage.kt:214](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingProviderDetailPage.kt#L214) | 配置 / 模型 / 高级（3） | provider 即改即存及模型编辑器不重做；ModelList 仅按已修订范围增加可选父级 LazyListState。 |
| [PagedNodeTreePage.kt:221–241](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/PagedNodeTreePage.kt#L221) | 卡片动态 event ID 页集 | 直接 `MochiPeerTransition(Depth)`；pages 过滤 enabled；稳定 event ID 对应 lists / values / errors / counters；失效 retained key 输出空内容，回调再次校验有效页。 |

卡片复用闭环：[NodeTreeRenderer.kt:89–91](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/NodeTreeRenderer.kt#L89) 的 pages 分支统一进入公共宿主；[CardPageStack.kt:207–212](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/khatkit/CardPageStack.kt#L207) 与 [CardWidgetPage.kt:228–235](../app/src/main/java/heizige/kk/khatkit/app/feature/chat/CardWidgetPage.kt#L228) 分别覆盖页栈 / 内联场景。无 pages、form、readOnly、ActionPage、ShowCardPage 等单页/脚本导航不凭空分页；KhatKitPage 通过 [当前导航选择:92–106](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/KhatKitPage.kt#L92) 保留无 pager 分支兼容。

## 4. 固定依赖与 API 实物（本轮复测）

当前版本：[libs.versions.toml:78](../gradle/libs.versions.toml#L78) 为 **0.4.0-khatkit.3**。固定上游 pin：[build-mochi.sh:6](../.github/scripts/build-mochi.sh#L6) 为 70636705c25b170d9424c9ea6669752ab8f6ece1；四模块统一发布入口 :13–17。t8 记录原脚本真实构建退出 0、五组旧补丁保留、**15 个锚点通过**（HEAD 9 + 新增 6，本轮 `grep -c '^patch(' .github/scripts/patch-mochi.py` = 15）；t4 不重复发布。

本轮直接读取 ~/.m2 的 .3 产物，**4/4 AAR 字节与 SHA-256 与 t8 记录一致**（compose 5174948 / 6cfe4dc2…、ios 26389 / 2df0af04…、icons 253348 / 5534609e…、nothing 1261804 / a394fb08…）；sources.jar 的 MochiTabPageTransition.kt:365 为 `public class MochiPeerTransitionSpec`、:368 `public val Depth`、:382 `public fun <T : Any> MochiPeerTransition`（默认 spec 即 Depth），:396 `check(spec === MochiPeerTransitionSpec.Depth)` 表明公开 facade 只支持 Depth 并委托原 `MochiTabPageTransition`（同文件 :64 原 `internal fun MochiTabPageTransition`）；classes.jar 的 javap 显示 `public static final MochiPeerTransition-1ix9Op4` 与私有原实现，应用无反射/INVISIBLE_MEMBER 绕过。

app 与 khatkit-ui 的实际 debugCompileClasspath 都由 dependencyInsight 解析到 .3（§1），即二者真实编译依赖该固定版本；本轮升级后未出现新的编译错误或新的单测失败（失败集与升级前快照逐条一致）。新 facade 只支持 Depth、保留原 pages 顺序、无连续进度回调、direction 不参与横移，不等于复制新 checkout 的可配置 Slide/spec API。

能力边界：原库没有公开连续进度回调；[卡片标题:167–169](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/PagedNodeTreePage.kt#L167) 跟随 committed activeId 而非旧 Pager offset，连续混合标题的体验须单独观察。取消、速度提交、嵌套横向控件优先仍依赖原库实现，不能由 public facade / JVM 单测证明通过；升级引入的运行时行为回归也须 t5/t6/t9 评审与设备验收，本轮不宣称。

## 5. 人工验收清单（全部未执行）

参照 [manual-verification.md](manual-verification.md#L25) 的按页面/操作清单。构建安装由用户另行安排；本任务未组装 APK、未发起 Actions、未安装、未操作手机或模拟器。**编译通过 ≠ 视觉/交互验证**，下列每一项都仍需真机勾选。

本轮补充：MochiNavPages 显式 `backdrop = background`，Depth 0.94 缩放时四周应显示页面底色而非 surfaceContainerHighest；浅/深色与玻璃下的实际边缘色差仍须真机确认（未执行，不宣称已验证）。

| 改动点 | 复现路径与前置条件 | 预期结果（待勾选） | 边界 |
|---|---|---|---|
| [图像生成:188](../app/src/main/java/heizige/kk/khatkit/app/feature/imggen/ImgGenPage.kt#L188) | 图片生成入口，准备图库和未提交生成参数 | [ ] 点图库/滑回生成走原地 Depth；[ ] 图库位置与参数保留；[ ] 生成中返回仍弹取消确认 | 生成中/空图库、键盘、快速反向点选。 |
| [提示词:154](../app/src/main/java/heizige/kk/khatkit/app/feature/extensions/PromptPage.kt#L154) | 提示词 → 模式注入/世界书，准备多条数据 | [ ] 两页搜索/位置保留；[ ] 排序和 FAB 正常；[ ] 再点当前导航回到所属列表顶部 | 长列表、编辑面板、横向筛选。 |
| [工作区:213](../app/src/main/java/heizige/kk/khatkit/app/feature/workspace/WorkspaceDetailPage.kt#L213) | 工作区详情，准备多级目录及文件 | [ ] 基本/文件切换同步导入按钮；[ ] 返回先回目录；[ ] 位置与所选区域保留 | 文件预览、导入导出、横竖屏/宽屏。 |
| [语音:131](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingSpeechPage.kt#L131) | 设置 → 语音，准备 TTS/ASR 服务商 | [ ] 新增按钮随页正确；[ ] 编辑面板/输入不丢；[ ] 两列表独立回顶 | 空列表、编辑中弹键盘、取消横滑。 |
| [模型:70](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingModelPage.kt#L70) | 设置 → 模型/提示，修改字段但暂不离开页面 | [ ] 页内值及位置保留；[ ] OCR 入口业务保持；[ ] 当前导航回顶只作用所属列表 | 长提示、大字号、滑杆/文本选择。 |
| [服务商:214](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingProviderDetailPage.kt#L214) | 设置 → 服务商 → 某服务商详情 | [ ] 配置/模型/高级三页 Depth；[ ] 跨页快速点选不横移穿过中间页；[ ] 即改即存和分享正常 | 模型子编辑器顶部三 tab 保持原业务、滚动与键盘。 |
| [卡片公共分页:221](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/PagedNodeTreePage.kt#L221) | 打开声明 nav+pages 的卡片，至少 3 页，含同名字段、禁用项及动态页 | [ ] UI 线程切换不误发脚本导航事件；[ ] 字段/校验/位置按 event ID 独立；[ ] 禁用项不可点/不可滑入；[ ] 增删/重排后幸存页状态保留 | 初始/当前被禁用、全禁用、空页、失效 retained key、脚本 selected 更新。 |
| [卡片页栈:212](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/khatkit/CardPageStack.kt#L212) / [内联:235](../app/src/main/java/heizige/kk/khatkit/app/feature/chat/CardWidgetPage.kt#L235) | 同一动态卡片分别在独立交互页、内联卡片及子页栈打开 | [ ] 三种宿主复用相同 Depth；[ ] 不重复顶栏；[ ] 返回先退子页，取消预测性返回不提交数据 | 新层覆盖底栏、外层共享标题、回到根页刷新。 |

通用矩阵（对上述 7 条分页链逐项执行，当前均未勾选）：

- [ ] A1 快速连续点击、跨页点击、转场中反向点选：从当前进度交接，不闪白、不跳回动画起点。
- [ ] A2 左右慢拖/快甩与首尾边界：原地景深、由库提交、不越界。
- [ ] A3 未过阈值松手、拖回原点、系统/子控件取消：恢复原页/原选中，不提交卡片 action，按压反馈释放。
- [ ] A4 点击/横滑/外部 selected：正文、导航、顶栏按钮、文件返回、picker 归属一致；标准重复点击回顶。
- [ ] A5 各页滚动到中部并填写输入，往返多轮：草稿、列表和编辑面板保留；未显示页无额外 load-more/action。
- [ ] A6 卡片两页相同字段名分别填写，刷新/脚本改值后提交：仅收集所属页字段及原 __khatkit_page。
- [ ] A7 禁用中间项、当前项、全部项：不先进入禁用页再弹回，不误发事件。
- [ ] A8 动态新增/删除/重排/当前页移除：幸存 event ID 状态保留，无 getValue 越界崩溃。
- [ ] A9 系统动画倍率 0 / 应用减弱动效及切换中更改：立即完成业务选择，无无限等待、透明页或多余弹性。
- [ ] A10 滑杆、横向滚动、分段控件、文本选择、图片缩放：子控件消费优先；纵向滚动/刷新不误切页。
- [ ] A11 小于600、600–839、840dp及以上布局：底栏/rail/sidebar状态相同；顶栏不跳、页栈 push/pop/预测性返回保留。
- [ ] A12 浅/深色 × 玻璃开/关 × MOCHI/IOS/NOTHING；大字号、键盘：主题和Insets一致，无遮挡、重复留白或黑底。

失败记录需包含：复现入口、上述编号、当前/目标页 ID、点击还是横滑、是否取消、预期/实际、外观/动效/窗口宽度；由用户提供截图/录屏或设备信息。未执行项不能勾成通过。
## 6. 第二轮复跑（t14，2026-11-28 19:18–19:33，未通过）

本轮按 amended 合同复跑 `./gradlew :app:compileDebugKotlin`、`:khatkit-ui:testDebugUnitTest`、`git diff --check`。当前主工作树正被**另一路并行的 i18n WIP**（非本团队、非导航范围）修改：6 次编译尝试（19:18、19:19、19:21、19:25、19:28、19:32）无一通过，错误内容随对方编辑在变（Kotlin 7→10→56→14 处，之后转为资源合并重复键），**始终 0 处落在导航文件**。

| 尝试（/tmp/t14-compile*.log） | 结果 | 主要归因 |
|---|---|---|
| 19:18 compile.log / 19:19 compile2 | exit 1 | RoleMemoryDocumentsProvider.kt:29,79 Context?；ConversationExport.kt:271；AILiveNotificationManager.kt:97；ProviderAccountSection.kt:85；ProviderConfigure.kt:468/481/483 |
| 19:21 compile3 | exit 1，56 处 | 多文件 `Unresolved reference 'R' / 'stringResource'`（strings 资源未补齐）、SettingPermissionsPage.kt:31–33 缺 key、PermissionChecklist.kt:89 |
| 19:25 compile4 | exit 1，14 处 | ApprovalButtons.kt:109–110、OperationRecorderNotifier.kt:79、SettingPermissionsPage.kt:31–33、ProviderConfigure/ProviderAccountSection、ToolCataloguePage.kt:513 |
| 19:28 compile5 / 19:32 compile6 | exit 1 | `:app:packageDebugResources`：values/strings.xml 与 values-zh/strings.xml 重复定义 `reasoning_picker_title`（两文件 19:28:27、19:32:05 仍在被编辑） |

- 导航文件（MochiNavPages、6 个入口、PagedNodeTreePage、KhatKitPage、NodeTreeRenderer、CardPageStack、CardWidgetPage）在 6 轮错误中 **0 次**出现；19:06:43 最近一次完整 app 单测轮（迁移代码全部落地后）编译与测试均跑完，见 §1/§2。
- `:khatkit-ui:testDebugUnitTest` **exit 0，13 tests / 0 failures**（/tmp/t14-khatkitui-tests.log:62；任务 UP-TO-DATE，源文件自 19:06 未变）。
- 覆盖复查：全仓 `HorizontalPager(` 仍为 **7** 处，与排除清单 7 项逐项一致；6 应用入口调用 MochiNavPages:64–86，卡片 PagedNodeTreePage.kt:221 用 Depth。
- `git diff --check` **exit 0**。
- app 失败集同一性本轮**无法重新取证**（编译未过，测试无法执行）：沿用 19:06:43 完整轮 1167/9 与 t13 HEAD worktree 1166/9 的比较——9/9 同名同消息、新增失败 0、消失 0（docs/mochi-tab-swipe-baseline.md §2–§3）。
- **未修改任何他人 WIP、未修 9 项基线、未暂存/提交、未跑设备/模拟器/APK**；设备交互（§5 与 A1–A12）仍全部未验证。

**结论：本轮不能宣称 app 编译或单测回归核验通过；需待该 i18n WIP 稳定后重跑同一命令。**
## 7. 第三轮复核（t16：隔离树复算 + 主树剩余阻塞定位）

本轮独立复算 [隔离取证文档](mochi-tab-swipe-isolated-verification.md) 的结论，不改任何产品代码。

| 对象 | 命令 / 检查 | 本轮实际结果 |
|---|---|---|
| 隔离 worktree `KhatKit-isolated`（detached HEAD `fe3b9996d`，仅含 11 个导航改动 + mochi `0.4.0-khatkit.3`） | `./gradlew :app:compileDebugKotlin` | **exit 0**，BUILD SUCCESSFUL 6s（/tmp/t16-iso-compile.log:209、:212） |
| 同上 | `./gradlew :app:testDebugUnitTest :khatkit-ui:testDebugUnitTest` | **exit 1**：app 1167 / 9 failed / 0 error / 0 skipped；khatkit-ui 13 / 0 / 0 / 0（/tmp/t16-iso-tests.log:372、:385） |
| 基线比对 | 隔离树 9 项失败 vs /tmp/t13-baseline-message-compare.json 的 HEAD `76921bc04` 基线 | 测试名集合相同（9/9）、消息 9/9 逐字一致；新增失败 **0**、消失 **0** |
| 主工作树 | `./gradlew :app:compileDebugKotlin`（19:46:22） | **exit 1**，但 Kotlin `e:` 错误 **0**；唯一失败为 `:app:packageDebugResources`：values/strings.xml:807、:3051 与 values-zh/strings.xml:786、:3024 重复定义 `reasoning_picker_title`（/tmp/t16-main-compile.log:205–214） |
| 主树归因 | 该键在 HEAD 各文件仅 1 条；工作树 diff 新增第二条（values `+ Reasoning`、values-zh `+ 推理`），strings 两文件 mtime 19:33/19:35 且 `M` | 属**他人未提交 i18n WIP**，不在本次导航范围；隔离树（仅导航改动）用 HEAD strings 可编译，证明导航改动不产生该重复 |
| 覆盖复查 | `HorizontalPager(` 全仓 | 7 处 = 排除清单 7/7（BackupPage、AssistantExtensionsPage、DebugPage、ExtensionSelector、McpConfigModal、ProviderModelList、KhatKitMarketContent）；6 应用入口调用 MochiNavPages:64–86，卡片 PagedNodeTreePage.kt:221 Depth |
| 空白检查 | `git diff --check` | **exit 0** |

结论：隔离树证明**导航改动 + 固定 .3 依赖**在干净 HEAD 上编译通过、app 失败集与基线 9/9 完全一致（无新增/消失）、khatkit-ui 全绿；主树唯一剩余阻塞是他人 i18n WIP 的 strings 重复键，导航文件无编译错误。主树恢复绿需该 WIP 自行完成，本任务不代改。

边界：设备交互（§5 与 A1–A12）**仍全部未验证**；未跑模拟器/APK、未暂存/提交、未改他人 WIP、未修 9 项基线。隔离树与主树的唯一适配差异见隔离文档 §2（`localizedErrors` → HEAD `errors()`）。
