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



### 14.2.1 退场动画覆盖范围（U7b-6 收口，实测 HEAD 4a975e8de）

> 结论：**退场动画只在「宿主接 state / visible=状态、且常驻组合」的弹层上生效**；
> 纯 `visible = true`、或仍用条件组合的调用点关闭即卸载，等效**即时消失**。
> 本轮验收只对下列页面检查退场动画（手动清单 §3 的 S1/S2/S3/S4/S5）。

1. **直连 `MochiSheet`（11 处，宿主自带 state）**（行号 HEAD 4a975e8de 实测）：
   `core/ui/components/ai/WorkspaceCwdPicker.kt:84`、`WorkspaceSelectSheet.kt:47`、
   `McpPicker.kt:231`、`AssistantPicker.kt:169`（core 版；SafeMode 私有版另有直连）、
   `FilesPicker.kt:423`、`core/ui/components/message/ChatMessageCopySheet.kt:43`、
   `ChatMessageActions.kt:323`、`ChatMessageEditedFiles.kt:158`、`ChatMessageTranslation.kt:100`、
   `core/ui/components/permission/PermissionChecklist.kt:537`、
   `core/ui/activity/SafeModeActivity.kt:212`（私有 AssistantPickerSheet 同处直连）。
   命令：`grep -rn 'MochiSheet(state' --include=*.kt app/src/main | grep -v PrimaryBottomSheet.kt | wc -l` = 11。
2. **if 门控改 visible=状态（7 处，t93 / commit 6a1031302，动画生效）**：
   `feature/backup/tabs/S3Tab.kt:341`（showBackupFiles）、`WebDavTab.kt:321`（showBackupFiles）、
   `feature/chat/ConversationExport.kt:138`（visible）、`feature/settings/SettingModelPromptPage.kt:138`（showEditor）、
   `feature/settings/SettingProviderPage.kt:287`（showSheet）、`settings/provider/ProviderModelSelectors.kt:65`（showModal）、
   `feature/webview/WebViewPage.kt:145`（showConsoleSheet）。
   这些点去掉了 `if (x)` 包裹、改为 `visible = x` 常驻组合，因此退场动画生效。
3. **`PrimaryBottomSheet(state = …)` 重载（17 个调用点 / 14 文件，动画生效）**：
   `AssistantPage.kt:499`、`AssistantBasicPage.kt:683`、`ChatWelcome.kt:241`、`ChatDrawer.kt:608`、
   `ChatPage.kt:710`、`SettingFilesPage.kt:132`、`SettingPage.kt:487`、`TriggerCardEditor.kt:124`、
   `SettingSearchPage.kt:331`、`SettingSpeechPage.kt:179/228/442/512`、`ThemePickers.kt:358`、
   `ProviderModelList.kt:576`、`ProviderModelCard.kt:97/411`。
   另有 `EditStateSheetHost`（`UseEditState.kt:84`）→ `AssistantPage.kt:494` 的「新建助手」面板，
   也是 state 快照驱动、收起动画期间继续渲染。
4. **保留的 visible 兼容层（25 处：15 处恒定 true + 10 处动态布尔）**，即时消失属预期；
   代表性逐条原因：
   - `KhatKitUiHost.kt:183`：Lua 卡片运行时 `UiRequest.Confirm` 分支，可见性由卡片 JSON 驱动。
   - `SafeModeActivity` 其余面板、`KhatKitPermissionSheet.kt:46`（静态内容）、`QrScannerSheet.kt:293`（相机回调）。
   - `AssistantPage.kt:618`、`PromptPage.kt:404/819`、`SkillsPage.kt:388`、`ImgGenPage.kt:802`、
     `LogPage.kt:129`、`SettingProviderPage.kt:514`、`SettingPreferencesThemePage.kt:550`、
     `ProviderConnectionTester.kt:288`、`McpConfigModal.kt:119`、`McpToolsConfigure.kt:302`、
     `GreetingPage.kt:1028`：多入口/多字段/长文本/流程回调等，改 state 牵连面大且收益低。
5. **护栏与计数（实测 HEAD 4a975e8de）**：
   - `PrimaryBottomSheet` 调用点：**49**（`PrimaryBottomSheetIconSourceGuardTest.kt:288`、
     `BottomSheetScrollSourceGuardTest.kt:329` 两处钉住）；`grep -rn 'PrimaryBottomSheet(' app/src/main | wc -l` = **51**（含 2 个函数定义）。
   - 全仓 `visible = true`：**18**（`grep -rn 'visible = true' --include=*.kt app/src/main | wc -l`），
     其中属于 PrimaryBottomSheet 调用点 **15** 处。
   - 分类：state 重载 17 / visible 兼容层 32 / 合计 49；`MochiSheetState` 引用 **97** 处。
6. **验收口径**：退场动画只在「直连 11 处 + if→visible 状态 7 处 + state 重载 17 处 + EditStateSheetHost」检查；
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


### 14.5 对话框：AppDialog 宿主 + MochiDialog 直连（DG1/DG2 收口）

> 形态：**Mochi 警告框（MochiDialog）统一渲染**；保留一层 `AppDialog` 兼容宿主承接旧签名。
> 数字实测 HEAD 98ac25256。

1. **`AppAlertDialog` → `AppDialog`**（`core/ui/components/ui/AppDialog.kt`）：
   签名保留、**38 个调用点零逻辑改动**（`grep -rn 'AppDialog(' --include=*.kt app/src/main | wc -l` = 39，含 1 个函数定义）；
   内部改为 `MochiDialog`（`AppDialog.kt:56`）。`shape/containerColor/tonalElevation/properties` 只收不用
   （圆角/底色/阴影/窗口属性由 Mochi 主题统一给）。按钮仍右下横排、取消在左、确认在右。
2. **全仓 material3 `AlertDialog` 41 → 1**：仅剩 `core/ui/components/ui/TextArea.kt:201` 的
   `BasicAlertDialog`——全屏纯文本编辑器需要平台全宽 + IME 直接控制，不适合 Mochi 警告框；
   这是**有意保留**，不是遗漏。
3. **直连 `MochiDialog`（12 处）**：
   `ChatInput.kt:850`（ASR 未配置）、`ReasoningPicker.kt:144`（思考强度）、
   `CompressContextDialog.kt:65`（压缩上下文）、`MessageQueuePanel.kt:166`（队列编辑）、
   `ChatMessageTools.kt:464`（工具拒绝理由）、`PermissionRationaleDialog.kt:50`（权限说明，限高滚动）、
   `ConfirmDialog.kt:28`（标准确认）、`Export.kt:33`（导出选项）、`TagList.kt:116`（标签重名留框）、
   `AppDialog.kt:56`（兼容宿主内部）、`ChatSizeChecker.kt:66`、`ChatRolePanelPage.kt:146`。
   `MochiConfirmationDialog` 1 处：`MessageQueuePanel.kt:203`。
4. **行为与视觉差异（验收要点）**：
   - 遮罩/圆角/投影/弹出退场动效由 Mochi 提供；点外部关闭由 `dismissOnOutsideTap` 控制（各调用点不同）；
   - **压缩中不可关**：`CompressContextDialog.kt` 在 `currentJob.isActive` 时点外/返回都不关，任务结束后
     `LaunchedEffect` 收口 `onDismiss()`；
   - **拒绝理由是危险色**：`ChatMessageTools.kt` 的「拒绝」用 `MochiDialogActionRole.Destructive`；
   - **TagList 重名留框**：`TagList.kt:55` 注释与实现——MochiDialog 点按钮必调 `onDismissRequest`，
     重名校验失败时靠「不调 dismiss」留在原地继续改；
   - **actions 为空**：`AppDialog.kt:60` 传 `persistentListOf()`，纯内容对话框底部可能多出一段留白
     （**34dp 留白候选**，见 §15 第 8 条，待视觉确认）；
   - 长内容（权限说明/导出/压缩预览）限高滚动，按钮不随内容滚走。
5. **复算命令**：
   ```bash
   grep -rn 'AppDialog(' --include=*.kt app/src/main | wc -l          # 39（38 调用点 + 定义）
   grep -rn 'MochiDialog(' --include=*.kt app/src/main | wc -l        # 12
   grep -rn 'MochiConfirmationDialog(' --include=*.kt app/src/main | wc -l  # 1
   grep -rn 'AlertDialog(' --include=*.kt app/src/main | grep -v Mochi | wc -l  # 1（TextArea BasicAlertDialog）
   ```

## 15. 遗留清单（下一会话按此接）

1. **EditState**：已新增 `EditStateSheetHost`（快照 + state 驱动），`EditState` 接口未改；
   其余 `EditStateContent` 调用点（3 处）行为不变、尚未迁移；全仓 `EditState` 引用 59 处。
2. **弹层动画覆盖**：退场动画仅「直连 MochiSheet 11 处 + if→visible 状态 7 处 + PrimaryBottomSheet(state) 17 处 + EditStateSheetHost」生效；visible 兼容层 25 个调用点（15 恒定 true + 10 动态布尔）仍为即时消失，属保留项（逐条 file:line 与原因见 §14.2.1）。
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
7. **死代码（C2 已清）**：删除 `RecordCardSheet.kt` 与 `KhatKitPermissionSheet.kt`（全仓零调用，含测试）；
   `QrScannerSheet` 仍被 `SettingProviderPage.kt:491` 使用，**保留**。
8. **对话框底部留白（候选）**：`AppDialog.kt:60` 的 actions 为空时，MochiDialog 底部可能多出约 34dp 留白；属视觉差异候选，待主人确认是否需要在宿主层补 padding 处理。
9. **visible 兼容层明细（C2 收尾，实测 26 处 visible = true）**（grep -rn 'visible = true' --include=*.kt app/src/main | wc -l = 26）：
   - core/ui/components/ai/CompressContextDialog.kt:66（MochiDialog，压缩中不可关）
   - core/ui/components/ai/MessageQueuePanel.kt:167 / :204（MochiDialog，队列编辑 / 确认框）
   - core/ui/components/ai/ModelList.kt:132（可见性由调用方布尔驱动）
   - core/ui/components/message/ChatMessageTools.kt:494（MochiDialog，拒绝理由）
   - core/ui/components/ui/permission/PermissionRationaleDialog.kt:51（MochiDialog，限高滚动）
   - core/ui/components/ui/GlassPopup.kt:96（不是弹层：Popup 内部 state 初始化，统计噪声）
   - core/ui/components/ui/Export.kt:34（MochiDialog，导出选项）
   - core/ui/components/ui/AppDialog.kt:57（兼容宿主内部的 MochiDialog）
   - core/ui/components/ui/QrScannerSheet.kt:233（仍在用：SettingProviderPage.kt:491 扫码入口，不能删）
   - core/ui/components/khatkit/KhatKitUiHost.kt:184（Lua 卡片 UiRequest.Confirm 分支）
   - feature/assistant/AssistantPage.kt:626（同页多 sheet 共用 visible，:499 已 state 化）
   - feature/chat/ChatSizeChecker.kt:67、feature/chat/ChatRolePanelPage.kt:147（MochiDialog）
   - feature/extensions/skills/SkillsPage.kt:509（导入技能 URL + 校验流程）
   - feature/extensions/PromptPage.kt:406 / :821（正则 / 注入两个编辑表单）
   - feature/imggen/ImgGenPage.kt:825（生成参数面板）
   - feature/log/LogPage.kt:130（长文本详情，两档可拖全屏）
   - feature/settings/components/ProviderConnectionTester.kt:289（连接测试结果回调驱动）
   - feature/settings/mcp/McpConfigModal.kt:120、McpToolsConfigure.kt:303（MCP 配置/工具多字段表单）
   - feature/settings/SettingPreferencesThemePage.kt:567（预设主题编辑，多入口）
   - feature/settings/SettingProviderPage.kt:580（提供商编辑表单）
   - feature/onboarding/GreetingPage.kt:1037（引导流程内嵌面板）
   - feature/automation/AutomationInAppHost.kt:52（自动化应用内弹层）
   说明：任务书按「删 3 个死文件」预估剩 25 处；实测 QrScannerSheet 仍被设置页扫码入口使用（保留），因此只删了 2 个（见第 7 条），计数 27 减到 26；其中 GlassPopup.kt:96 不是弹层，按弹层口径为 25 处。
