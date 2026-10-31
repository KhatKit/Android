# KhatKit UI 方案：MD3 骨架 + Mochi token/交互 + Kk 壳

> 状态：**U5b 收尾（2026-10-30）**。原「C 方案：全量迁移到 mochi-compose」在 P4 被判过度自绘，
> 改成本方案并已收尾：**Material3 负责骨架与组件，mochi-compose 只提供主题 token 与交互设施，
> 旧组件库以 `khatkit-ui/uikit/mochi/` 的 Kk 壳承接调用形态**。
> Kedge / Miuix / Khromia 三库已从依赖树与源码 import 中彻底删除。
> 真机批量回归（U5c）在设备恢复后执行，本文件不含真机结论。

## 1. 目标与非目标

**目标**

1. 全应用只剩一棵主题树：`MochiTheme` 提供颜色/字阶/圆角/系统栏图标对比度；
2. 骨架、列表、表单、弹层、反馈用 **material3**；交互设施（overscroll、触感、返回语义）用 Mochi；
3. 删除 Kedge / Miuix / Khromia，不再随上游 UI 漂移维护三套外观；
4. 行为等价优先：替换以状态矩阵（enabled/error/disabled/contentDescription/steps…）为验收。

**非目标**

- 不追求「零 material3」；MD3 是本方案的骨架层，不是过渡层；
- 不再把页面组件批量改写成业务自绘件（P4 教训见 §6）；
- 卡片运行时（Lua API/节点词表/上限）不动。

## 2. 现状快照（可复算，HEAD 6bd06fdde）

```bash
cd /home/heizige/文档/Android/Project/KhatKit
find app/src/main/java -name '*.kt' | wc -l                                            # 749
grep -rl 'import androidx.compose.material3' --include=*.kt app/src/main | wc -l       # 210
grep -rl 'import heizige.kk.kedge'   --include=*.kt . --exclude-dir=build | wc -l      # 0
grep -rl 'import top.yukonga.miuix'  --include=*.kt . --exclude-dir=build | wc -l      # 0
grep -rl 'import heizige.kk.khromia' --include=*.kt . --exclude-dir=build | wc -l      # 0
grep -n 'kedge\|khromia\|miuix' settings.gradle.kts gradle/libs.versions.toml | wc -l # 0
ls khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/mochi/*.kt | wc -l                # 13（Kk 壳）
```

APK（U5a 之后 debug，未压缩对比口径 = 同机同任务）：

| 产物 | 体积 |
|---|---:|
| app-arm64-v8a-debug.apk | **90,953,670 B** |
| app-universal-debug.apk | 107,760,833 B |
| app-x86_64-debug.apk | 91,713,927 B |

P0 基线 arm64 为 **94,756,786 B**，差值 **−3,803,116 B（−4.01%）**（U5c lint 修复 c1afc0136 之后复测）。

## 3. 依赖与版本矩阵

| 依赖 | 坐标 | 说明 |
|---|---|---|
| Compose BOM | `2026.09.00`（foundation/ui 解析 1.13.0-alpha01） | 骨架层 |
| mochi-compose / mochi-ios / mochi-icons | `io.github.feice11:*:0.2.0-khatkit` | **mavenLocal 本地源码产物**，见 §12 |
| kedge / khromia / miuix | — | **已删除**（U5a 删坐标与 includeBuild） |

`settings.gradle.kts` 不再有 `includeBuild(../Kedge|../Khromia)` 与 substitution；
`khatkit-ui`/`mediapicker` 的模块依赖同步删除。换机器/CI 需要先按 §12 发布本地 Mochi。

## 4. 分层与目录

1. **骨架层（material3）**：`Scaffold`/`TopAppBar`/`LargeFlexibleTopAppBar`/`ModalBottomSheet`/
   `AlertDialog`/`Switch`/`Slider`/`SegmentedButton`/`TextField`/`PermanentNavigationDrawer` 等。
2. **Token/交互层（mochi-compose）**：`MochiTheme`（colors/textStyles/shapes/systemBarAppearance）、
   `ProvideMochiOverscroll`、`MochiLargeTitleScaffold`、`MochiListSection`/`MochiListRow`、
   `MochiToastHost`/`MochiSheet`/`MochiPopover` 等。
3. **Kk 壳层（`khatkit-ui/uikit/mochi/`，13 文件 / 2469 行）**：保留旧调用形态、内部换 material3 或
   自写实现，避免全仓调用点重写：
   `KkCompat.kt` / `KkCompat2.kt` / `KkControls2.kt` / `KkEditDialog.kt` / `KkModifiers.kt`（fadingEdge）/
   `KkSwipeToDismiss.kt` / `KkToast.kt` + Mochi 组件封装 6 个（Containers/Controls/Lists/MaterialBridge/
   SearchAndBar/TopBars）。
4. **业务自绘层**：聊天气泡/输入区、Markdown/代码高亮、卡片运行时等保持既有实现。

## 5. 三库删除过程（提交可查）

| 库 | 做法 | 结果 |
|---|---|---|
| **Miuix** | U1a 删 28 个 `*Miuix*.kt` 变体与 `if (LocalKedgeStyle==Miuix)` 死分支；U1b 5 个共用 helper 迁 MD3；U1c 剩余 8 文件清零；U1d/U1e 删「界面风格」开关与 uiStyle 运行期分支 | import 36→0；设置页不再有风格开关 |
| **Kedge** | U4a 容器/顶栏/导航/搜索 → MD3；U4b 按钮/表单/选择控件 → MD3；U4c 列表/弹层/菜单/反馈 → MD3+Kk；U4d/U4e app/khatkit-ui/mediapicker 收尾 | import 203→0 |
| **Khromia** | U4f1 Toast 37 文件→KkToast、fadingEdge→自写 Modifier、FullscreenPopup→MD3 Dialog；U4f2 Segmented/开关/滑块/单选/弹层→Kk 壳/MD3 | import 72→0 |
| **依赖收口** | U5a `15829cab4`+`6bd06fdde`：删 includeBuild/substitution、libs 坐标、模块 api/implementation，清最后非 import 引用；`clean :app:assembleDebug` 通过 | 三库不再进入构建 |

## 6. 教训：P4 过度自绘（不要重犯）

P4 阶段把 Kedge 符号机械替换为自绘 Mochi 组件，符号是归零了，但**行为退化**：

- `MochiCard` 的 `colors` 参数「只收不用」，CardColors 语义丢失（卡片底色不再随调用方变化）；
- `MochiSlider` 无 contentDescription，且接收 `steps` 但不做吸附（SettingItem 旧行为有吸附）；
- `MochiTextField` 错误态/密码显隐/多行覆盖不全，只有基础输入路径；
- 卡片弹层 pager / 内部 nav / load_more / 预测性返回在 P4 验证时无证据（t19 failed）。

**处理**：U 系列回退到「material3 直替 + Kk 壳保留旧调用形态」，按行为等价验收；
后续迁移先列状态矩阵（enabled/error/disabled/contentDescription/steps/动画），
**不以「符号消失」作为阶段通过条件**。

## 7. 已知缺口与中间态

1. **MochiItem 能力缺口**：Mochi 的行组件没有 `enabled`/`trailing`/长按菜单，所以菜单类行仍保留
   `MochiMenuPopup`/`MochiMenuRow`（当前 16 文件 / 75 处引用；任务书写的「14 处」是更早快照）。
2. **抽屉模糊退回 MD3**：`ChatDrawer.kt:252-257` 把 backdrop 恒置 null —— `MochiGlassSurface` 的
   backdrop 采样在 NavDisplay 重建时不稳定，退化为不透明底色；视觉差异 = 顶栏/底栏没有背景模糊，
   只靠 `surfaceContainer` 层次色区分。
3. **预设主题中间态**：`PresetTheme` 仍有 48 处引用，设置入口已删、运行期不注入；后续 slice 统一清理。
4. **大屏适配缺口**：全仓仅 `ChatPage.kt:52,169` 一处 `currentWindowDpSize()`，Mochi 无
   WindowSizeClass/adaptive。建议后续 slice 引 `material3-adaptive` 的 `AdaptiveScaffold`：
   **≥840dp 侧栏 + 双栏 / 600–840dp 导航轨 / <600dp `MochiTabBar`**，先做设置与探索两页。
5. **真机批量回归未跑**：设备离线，清单见 §11，U5c 一次跑完并出三屏截图。

## 8. 视觉与交互硬约束

- 只允许一棵主题树：`MochiTheme`；页面里禁止 `if (ios)/if (glass)/if (miuix)` 外观分支；
- 系统栏图标对比度只由 `MochiTheme(systemBarAppearance=…)` 管，页面禁止 `isAppearanceLightStatusBars`；
- 返回单点接管：`RouteActivity` 的 `NavDisplay.onBack`；页面不挂 `BackHandler`/`PredictiveBackHandler`；
  页内浮层经 `PageBackInterceptor` 先消费；
- 列表/卡片层次用 material3 `surfaceContainer*`；不引入 Kedge/Miuix/Khromia；
- 新组件优先 material3；只有「Mochi 有且语义等价」时才用 Mochi，缺口进 §7 清单。

## 9. 测试与守卫

- `LegacyImportGuardTest`：白名单文件 `app/src/test/resources/legacy-import-whitelist.txt` 当前
  **kedge 0 / miuix 0 / khromia 0（条目 0）**；新增三库 import 直接失败；
- 源码护栏：`PrimaryBottomSheetIconSourceGuardTest` / `BottomSheetScrollSourceGuardTest` 等沿用；
- `:app:test` U5a 绿；**U5b lint：exit 0**（修复提交 `c1afc0136` 清零 8 个 error：
  SuspiciousIndentation ×3 / LocalContextGetResourceValueCall ×4 / UnusedContentLambdaTargetStateParameter ×1；
  独立 `:app:lintDebug --rerun-tasks` 5m42s 复跑通过）。基线 **929 warnings** 保留，属存量口径，不阻塞回归。

## 10. 验证命令

```bash
cd /home/heizige/文档/Android/Project/KhatKit
./gradlew clean :app:assembleDebug          # 0
./gradlew test                              # 0
./gradlew lint                              # 0（基线 warning 见报告）
grep -rl 'import heizige.kk.kedge'   --include=*.kt . --exclude-dir=build | wc -l   # 0
grep -rl 'import top.yukonga.miuix'  --include=*.kt . --exclude-dir=build | wc -l   # 0
grep -rl 'import heizige.kk.khromia' --include=*.kt . --exclude-dir=build | wc -l   # 0
grep -n 'kedge\|khromia\|miuix' settings.gradle.kts gradle/libs.versions.toml      # 空
stat -c '%s' app/build/outputs/apk/debug/app-arm64-v8a-debug.apk                    # 90,953,670
```

## 11. 待批量真机清单（U5c，设备恢复后一次跑完）

- **A U1/U5 复审**：设置→主题无「界面风格」行；MochiSwitch 动画正常；旧「渐变/流光」配置不崩且无入口；
  首页/聊天/设置/探索 4 页冒烟。
- **B 组件行为**：卡片弹层 pager / 内部 nav / load_more / 预测性返回；MochiSlider 拖动/点击定位/边界/
  吸附&contentDescription；MochiTextField 错误/禁用/密码显隐/多行。
- **C 未覆盖页面**：AssistantExtensions / AssistantRequest / Extensions / Prompt / ImgGen / Greeting /
  SettingProviderDetail（另 Speech / WebView / WorkspaceDetail）；ExploreMarket 卡片底色 vs P3 基线。
- **D P7 记账**：三屏（聊天/探索/设置）浅+深截图入库；APK 体积数字；删 `MochiSpikeActivity`（若仍在）。
- **E 回归**：深链 PROCESS_TEXT / ACTION_SEND；VIEW `rikkahub://shortcut` 偏差项；返回不双 pop。

## 12. 本地 Mochi 源码库（mavenLocal `0.2.0-khatkit`）

- 源码：`/home/heizige/文档/Android/Project/Mochi`（tag `v0.2.0` / commit `3f4eda8`）
- 发布：`cd ../Mochi && ./gradlew publishToMavenLocal -PVERSION_NAME=0.2.0-khatkit`
- 产物：`~/.m2/repository/io/github/feice11/mochi-{compose,ios,icons}/0.2.0-khatkit`
- 改库循环：改源码 → 重新发布 → `./gradlew :app:assembleDebug --refresh-dependencies`
  （同版本号 m2 产物会被 Gradle 缓存，必须 refresh）
- 不用 composite build：Mochi 的 AGP 8.13.2 只支持 Gradle 8.x，与本仓 Gradle 9.7.1 不兼容。
- 换机器/CI：`0.2.0-khatkit` 只在本地 m2，必须先发布或换成已发布坐标。

## 13. 不要再重新讨论的决定

1. 不回退到全量 Mochi；material3 是骨架层，不是过渡层；
2. 不重新引入 Kedge/Miuix/Khromia（含「只引一个组件」）；
3. 主题只接一次 `MochiTheme`；页面不写外观分支；
4. 卡片运行时（Lua API/节点词表/上限）不动；
5. 迁移验收以行为等价为准，不以符号归零为准（§6）。

## 14. 最终形态（U7b 收口后，数字可复算）

> 本节记录「这一轮结束后真实长什么样」，下个会话不要按 §1–§13 的历史计划重新推演。
> 复算统一在仓库根执行；行号可能漂移，**命令输出**是准的。

### 14.1 导航：60 条路由全走 MochiNavHost

- 路由表：`app/src/main/java/.../app/navigation/ScreenRoutes.kt` 的 `routeNames` = **60** 条
  （`Screen` ⇄ 确定性字符串；`ScreenRoutesTest` 守互逆；参数百分号编码、null 不写）。
- 渲染：60 条全部由 **`MochiNavHost`** 渲染（`RouteActivity.kt:276/327`），Nav3 外壳已删除。
- 调用点零改动：`MochiBackedNavStack` 把 Mochi 栈包成 `MutableList<NavKey>`，
  `navController.navigate(Screen.X)` 原样可用；根页不可 pop（`clear()` 退到根页为止）。
  形如 `navController.navigate(Screen…` 的调用 = **68 处**。
- source 锚点 morph：`LocalKkNavController`（`RouteActivity.kt:481`）+ 入口元素 `mochiNavSource`
  上报锚点；`kkNav.navigate(screen, source = anchor)` 走共享元素 morph，无宿主时退回普通侧推。
  `mochiNavSource` 使用点 **30 处**。
- 返回单点：`RouteActivity` 的 `onBack` 唯一接管；页内浮层经 `PageBackInterceptor` 先消费；
  页面不挂 `BackHandler`/`PredictiveBackHandler`（§8）。

```bash
python3 - <<'EOF'
import re
s=open('app/src/main/java/heizige/kk/khatkit/app/navigation/ScreenRoutes.kt',encoding='utf-8').read()
m=re.search(r'routeNames: Set<String> = setOf\((.*?)\n\s*\)', s, re.S)
print('routes =', len(re.findall(r'"[^"]+"', m.group(1))))
EOF
grep -rn 'mochiNavSource' --include=*.kt app/src/main | wc -l              # 30
grep -rn 'navController.navigate(Screen' --include=*.kt app/src/main | wc -l  # 68
```

### 14.2 弹层：PrimaryBottomSheet → MochiSheet（state 驱动 + 台账）

- `PrimaryBottomSheet` 已换成 **Mochi 真 sheet**：`rememberMochiSheetState` 驱动
  `state.show()`/`state.hide()`，组合树里**没有 `if (visible)`**（`PrimaryBottomSheet.kt:32-57`）；
  Boolean 入参只是兼容薄层；内部多页用内层 `MochiNavHost`（有子页先退页，退回根页才关面板）。
- backdrop/scrim 由 MochiSheet 自带；面板高度/圆角/拖拽动效由 Mochi 管。
- 调用点计数：源码护栏 `PrimaryBottomSheetIconSourceGuardTest` / `…ScrollSourceGuardTest`
  钉在 **60**（U7b-5 由 62 降 60，见测试 :286-288）。
- **A 类（已 state 驱动 / 短提示改 Dialog）**：
  - 助手页「新建助手」编辑面板：`UseEditState.kt:84` 新增 `EditState<T>.EditStateSheetHost`
    （保留最后一份 state 快照 + `LaunchedEffect(isEditing)` 驱动 show/hide，收起动画期间继续渲染快照）；
    `AssistantPage.kt:494/498` 已接 `rememberMochiSheetState`。
  - 两处短提示改 **MochiDialog**：`ReasoningPicker.kt:144`（思考强度：提示+图标+滑块）、
    `ChatInput.kt:850`（ASR 未配置 + 跳转动作）——不到一屏、单一动作，dialog 语义更合适。
- **B 类（冷门保留 sheet，台账）**：多复选/多字段/列表/网格/长文本，换 dialog 会破坏可用性：
  `ConversationExport.kt:139`（多复选）、`WebDavTab.kt:322`、`S3Tab.kt:342`（备份表单）、
  `ImgGenPage.kt:802`（参数多）、`LogPage.kt:129`（长文本，两档可拖全屏）、
  `McpConfigModal.kt:119`、`McpToolsConfigure.kt:302`、`PromptPage.kt:404/819`（编辑表单必须高）、
  `SkillsPage.kt:388`（URL+校验）、`SettingPage.kt:448`（QQ 群列表）、
  `AssistantBasicPage.kt:679`（头像形状网格）、`PermissionChecklist.kt:524`（策略选择）、
  `ChatMessageActions.kt:313`（操作单）、`ChatMessageEditedFiles.kt:144`（文件列表）、
  `ChatPage.kt:703`（附件面板）、`GreetingPage.kt:1028`（引导页内嵌，backdrop 收益低）、
  `WebViewPage.kt:146`（WebView 内弹层，尺寸随内容）。
- 死代码候选：`RecordCardSheet.kt:83` 全仓无调用点，可单独删除（不在本轮范围）。


### 14.2.1 退场动画覆盖范围（U7b-6 收口，实测 HEAD ea40b28da）

> 结论：**退场动画只在「宿主接 state、调用方持 state」的弹层上生效**；
> visible 兼容层（Boolean）虽有 LaunchedEffect(visible) { show()/hide() }，
> 但多数调用点是**条件组合**（if (visible) PrimaryBottomSheet(...)），关闭即整体卸载，
> 等效**即时消失**。本轮只对下列直连/state 页面验收动画。

1. **直连 MochiSheet（9 处，绕过 PrimaryBottomSheet，宿主自带 state）**：
   WorkspaceCwdPicker.kt:84、WorkspaceSelectSheet.kt:47、McpPicker.kt:231、
   FilesPicker.kt:423、ChatMessageCopySheet.kt:43、ChatMessageActions.kt:323、
   ChatMessageEditedFiles.kt:158、ChatMessageTranslation.kt:100、PermissionChecklist.kt:537
   （命令：grep -rn 'MochiSheet(state' app/src/main | grep -v PrimaryBottomSheet.kt | wc -l = 9）。
2. **PrimaryBottomSheet(state = …) 重载（17 个调用点 / 14 文件，动画生效）**：
   AssistantPage.kt:499、AssistantBasicPage.kt:683、ChatWelcome.kt:241、ChatDrawer.kt:608、
   ChatPage.kt:708、SettingFilesPage.kt:132、SettingPage.kt:487、TriggerCardEditor.kt:124、
   SettingSearchPage.kt:331、SettingSpeechPage.kt:179/228/442/512、ThemePickers.kt:358、
   ProviderModelList.kt:576、ProviderModelCard.kt:97/411。
   另有 EditStateSheetHost（UseEditState.kt:84）→ AssistantPage.kt:494 的「新建助手」面板，
   也是 state 快照驱动、收起动画期间继续渲染。
3. **保留 visible 兼容层（34 个调用点；其中 visible = true 恒定 16 处）**，原因逐条：
   - KhatKitUiHost.kt:183：Lua 卡片运行时 UiRequest.Confirm 分支，可见性由卡片 JSON 驱动，宿主无常驻 state。
   - SafeModeActivity.kt:205：安全模式独立 Activity，面板随 Activity 生命周期，无常驻 Composable state。
   - KhatKitPermissionSheet.kt:46：权限说明为静态内容，调用点传常量 true，state 化无收益。
   - QrScannerSheet.kt:293：扫码结果由相机回调布尔驱动，不属于 UI state。
   - AssistantPicker.kt:155：多入口复用（设置/聊天），调用方直接传布尔。
   - AssistantPage.kt:618：同页还有 3 个 sheet 共用 visible 参数（:499 已 state 化），统一改造牵连面大。
   - PromptPage.kt:404/819：正则/注入两个独立编辑表单，各自由对话框状态驱动。
   - SkillsPage.kt:388：导入技能 URL + 校验流程，临时状态多。
   - ImgGenPage.kt:802：生成参数面板由生成流程状态驱动。
   - LogPage.kt:129：长文本详情（两档可拖全屏），布尔来自列表选中项。
   - SettingProviderPage.kt:514：提供商编辑表单（保存/校验编排）。
   - SettingPreferencesThemePage.kt:550：预设主题编辑（多入口）。
   - ProviderConnectionTester.kt:288：连接测试结果由异步回调驱动。
   - McpConfigModal.kt:119 / McpToolsConfigure.kt:302：MCP 配置/工具多字段表单。
   - GreetingPage.kt:1028：引导流程内嵌面板，根在滚动容器内，backdrop 收益低。
   其余 17 个 visible 调用点传的是**动态布尔**（showSearchPicker / state.visible / showBackupFiles 等），
   同样未纳入动画验收。
4. **护栏与计数（实测）**：
   - PrimaryBottomSheet 调用点：**51**（PrimaryBottomSheetIconSourceGuardTest.kt:288 钉住；
     grep -rn 'PrimaryBottomSheet(' app/src/main khatkit-ui/src/main | wc -l = 53，含 2 个函数定义）。
   - visible = true：全仓 20 处（grep -rn 'visible = true' --include=*.kt app/src/main | wc -l），
     其中属于 PrimaryBottomSheet 调用点 **16** 处（见上）。
   - MochiSheetState 引用 85 处；MochiSheet(state 直连 9 处。
5. **验收口径**：只在手动清单 §3 的 S1/S2/S3/S4/S5 里对「直连/state 页面」检查退场动画；
   其余 visible 兼容层按「即时消失」验收，不算缺陷（见 §15 第 2 条）。

### 14.3 交互：Kk 壳（触感/按压/动效/淡边）

- `khatkit-ui/uikit/mochi/KkInteraction.kt`：`class KkHaptics`（:30）+`rememberKkHaptics()`（:55）、
  `rememberKkReduceMotion()`（:66）、`Modifier.kkPressState(...)`（:84）、`object KkMotionSpecs`（:108）。
- 触感使用点 **5 处**（ModelList:448 / ChatInput:371 / ChatMessage:379 / ReorderableDrag:29,47）。
- 淡边：`Modifier.fadingEdge(top/bottom/left/right/strength)`（`KkModifiers.kt:23`，DstIn 渐变遮罩，
  无第三方），使用文件 6 个。
- 减弱动效：`rememberKkReduceMotion()` 决定按压/过渡是否降级（KkInteraction 内消费）。

### 14.4 主题：Mochi token → MD3 骨架（单一 Bridge）

- 唯一挂载点：`app/themecompat/CompatMaterial3.kt:27` 的 `ProvideMaterial3Theme(amoled)`，
  全 app 只有这一处 `MaterialTheme`。
- token 来源 `khatkit-ui/uikit/mochi/MochiMaterialBridge.kt`：
  - 颜色：`MochiColorScheme` **37 个角色**同名逐域拷贝（无派生计算）；
  - 形状：`MochiShapes`（`MochiCapsule.full` 无 MD3 对应角色，不映射）；
  - 字阶：`MochiTextStyles` 的 **15 个 MD3 角色**（iOS 侧字号不进 MD3）；
  - 动效：`MochiMotionScheme`（当前取 expressive，逐条 spring 映射待 API 稳定）。
- 明暗唯一真相：`ColorMode`（SYSTEM / LIGHT / DARK 三态，`ColorMode.kt`）解析成
  `ThemeController` 的 mochi `ColorSchemeMode`；Bridge **不再收 dark 参数**（避免第二个真相）。
- AMOLED 阶梯：`MochiColorScheme.withAmoledBackground(amoled)` **只对深色**生效：
  `background/surface/surfaceDim/surfaceContainerLowest = #000`，
  `Low ×0.55` / `Container ×0.40` / `High ×0.25` / `Highest ×0.10` 向黑插值（保持单调层级）。
- `fixed*/onFixed*` **12 个 tone-fixed 角色**从 37 角色推导（Bridge 内 `MochiFixedRoles`）：
  90 Fixed←primaryContainer/onPrimaryContainer；80 FixedDim←inversePrimary（sec/tert 由 40→90 插值）/primary；
  10 OnFixed←onPrimaryContainer/向黑插值；30 OnFixedVariant←10→40 插值/primaryContainer。
- 不注入旧色板/旧字阶：extend 调色板与 GoogleSans 只在功能点直接引用，不进主题。

## 15. 遗留清单（下一会话按此接）

1. **EditState**：已新增 `EditStateSheetHost`（快照 + state 驱动），`EditState` 接口未改；
   其余 `EditStateContent` 调用点（3 处）行为不变、尚未迁移；全仓 `EditState` 引用 59 处。
2. **弹层动画覆盖**：退场动画仅「直连 MochiSheet 9 处 + PrimaryBottomSheet(state) 17 处 + EditStateSheetHost」生效；visible 兼容层 34 个调用点（visible=true 恒定 16）仍为即时消失，属保留项（逐条 file:line 与原因见 §14.2.1）。
3. **大屏适配**：全仓仅 `ChatPage.kt:52,169` 一处 `currentWindowDpSize()`；Mochi 无
   WindowSizeClass/adaptive。建议后续 slice 引 `material3-adaptive` `AdaptiveScaffold`：
   ≥840dp 侧栏+双栏 / 600–840dp 导航轨 / <600dp `MochiTabBar`；先做设置与探索。
4. **MochiItem 能力缺口**：无 enabled/trailing/长按菜单；长按/富行/组合语义仍用
   `MochiMenuPopup`/`MochiMenuRow`。复算：`MochiMenuPopup(` = **4**、`MochiMenuRow(` = **4**
   （合计 8 处调用；全量引用 23 处）。⚠️ 任务书「14 处」是更早快照，以命令为准。
5. **预设主题中间态**：`PresetTheme` 仍有 48 处引用，入口已删、运行期不注入；旧数据不崩。
6. **三库删除结论（可复算）**：
   ```bash
   grep -rl 'import heizige.kk.kedge'   --include=*.kt . --exclude-dir=build | wc -l   # 0
   grep -rl 'import top.yukonga.miuix'  --include=*.kt . --exclude-dir=build | wc -l   # 0
   grep -rl 'import heizige.kk.khromia' --include=*.kt . --exclude-dir=build | wc -l   # 0
   ```
7. **死代码**：`RecordCardSheet.kt:83` 无调用点（同 §14.2）。
