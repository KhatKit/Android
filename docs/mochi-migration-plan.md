# C 方案：全应用迁移到 Mochi Compose（去掉 MD3 与 Miuix）

> 状态：**计划（未开工）**。写于勘察之后，供新会话直接执行。
> 目标一句话：KhatKit 的 UI 外壳与全部页面组件改由 `mochi-compose` 提供，
> `androidx.compose.material3`、`Miuix`、`Kedge`（现双风格层）从 app 运行期依赖里消失，
> 视觉统一到「MD3 Expressive 配色 + iOS 26 交互 + G2 连续圆角」。
>
> **本文件不需要再讨论"要不要做"。** 下面 §2 列了可复算的数字，§11 列了不能重新商量的决定。

---

## 1. 目标与非目标

**做**

1. app 全程只走一棵 UI 树（无 `KedgeStyle.MD3Exp` / `Miuix` 分支）。
2. 依赖收口到 `io.github.feice11:mochi-compose`（+ 按需 `mochi-ios` / `mochi-icons`），
   删除 `:kedge`、`:khromia` 的 composite build 与 `top.yukonga.miuix`。
3. 页面骨架、列表、表单、弹层、反馈、导航全部用 Mochi 组件；我们只保留业务自绘件
   （聊天气泡/输入区/Markdown/代码块/图片形状/进度条等）。
4. 状态栏、insets、返回键、overscroll、haptics 交给 Mochi 的根部设施统一接管。

**不做**

- 不改卡片运行时（`ui.screen`/`ui.show` 的 Lua 面、节点词表、512 节点上限）——只允许换渲染器皮肤。
- 不改 `:khatkit`、`:khatkit-ui` 的公共 API 契约（渲染器内部可换实现）。
- 不引 `material3` 作为过渡依赖（第三方库内部自带的不管）。
- 不为 Mochi 做 KMP/iOS 适配（它是 Android-only，跟这个仓库无关）。

---

## 2. 现状基线（可复算，开工前重跑一遍并写进提交说明）

```bash
cd /home/heizige/文档/Android/Project/KhatKit

find app/src/main/java -name '*.kt' | wc -l                                   # 780
grep -rl 'import androidx.compose.material3' --include=*.kt app/src/main | wc -l   # 236
grep -rl 'import top.yukonga.miuix' --include=*.kt . --exclude-dir=build | wc -l   # 36
grep -rl 'import heizige.kk.kedge' --include=*.kt app/src/main khatkit-ui/src/main | wc -l  # 203
grep -rl 'import heizige.kk.khromia' --include=*.kt app/src/main khatkit-ui/src/main | wc -l # 79
grep -rn 'Miuix' --include=*.kt app/src/main | wc -l                          # 849
grep -rn 'KedgeStyle' --include=*.kt app/src/main | wc -l                     # 212
```

236 个 material3 文件的分布（决定 P5 的顺序）：
`core/ui 107` / `feature/settings 49` / `feature/assistant 12` / `feature/chat 10` /
`feature/backup 7` / `feature/extensions 5` / `feature/workspace 4` / 其余 ≤3。

外壳调用点（决定 P3/P4 的顺序，`app/src/main` + `khatkit-ui/src/main`）：

| 组件 | 文件数 |
|---|---:|
| `KedgePageScaffold` | 10 |
| `KedgeSearchBar` | 9 |
| `KedgeListItem` | 8 |
| `KedgeBottomBar` | 6 |
| `KedgeTopAppBar` / `KedgeLargeTopAppBar` | 2 / 2 |
| `KedgeModalBottomSheet` | 2 |
| `KedgePrimaryBottomSheet` / `KedgeDialog` / `KedgeNavigationBar` | 1 / 1 / 1 |

Kedge 库本体：`44` 个 kt、**105 个 `@Composable`（104 个不重名）**（`../Kedge/kedge/src/main/java/heizige/kk/kedge`）。
Mochi 官方文档：**27 个组件** + 15 篇 guide（`MochiTheme`/`ProvideMochiOverscroll`/`MochiNavHost`/scaffold/tab/glass/overscroll/back）。

---

## 3. 依赖与版本矩阵（勘察结论，已核实）

| 项 | Mochi 0.2.0 | 本仓库 | 结论 |
|---|---|---|---|
| 坐标 | `io.github.feice11:mochi-compose:0.2.0`（另有 `mochi-icons`/`mochi-ios`/`mochi-nothing` 同版本，均 Apache-2.0） | — | 可直接依赖，无需 JitPack（0.1.x 才在 JitPack） |
| 发布时间 | 2026-10-28（Maven Central `latest=0.2.0`，`0.2.0` 是唯一发布版） | — | 0.x，API 会变；升级按次评估 |
| minSdk / compileSdk | 23 / 36 | 26 / 37 | 兼容 |
| JVM target | 17 | app 21 / 库 17 | 兼容 |
| Kotlin / AGP | 2.3.0 / 8.13.2 | 2.4.20 / 9.3.1 | 读旧 metadata OK；**运行时未验证 → P1 先验** |
| Compose | BOM 2025.04.01 | BOM 2026.09.00（material3 1.5.0-alpha29） | 最大不确定项，P1 关 |
| 传递依赖 | `kyant capsule 2.1.3`(api)、`kotlinx-collections-immutable 0.5.2`(api)、`kyant backdrop 1.0.6`、`materialkolor 4.1.1`、`activity-compose 1.10.1` | 现无 kyant 依赖 | 新增 2 个 api 传递依赖，无类冲突（Miuix 走自己的 shader） |
| 设计前提 | 只依赖 compose-ui/foundation，**不依赖 material3、不依赖 Miuix** | 现用 Kedge(44 kt / 105 composable) + Miuix 0.9.3 + Khromia | 能共存，但 C 方案是**替换**不是共存 |

Mochi 官方强制项（`docs/guide/application-integration.md`，接入前必须读）：
`MochiTheme` 放根 → `ProvideMochiOverscroll` → `rememberMochiNavigator` + `MochiNavHost` 管页面；
**禁止在页面里写 `if (nothing)/if (ios)/if (glass)` 外观分支**；玻璃/毛玻璃只覆盖 bars/menus/sheets。

---

## 4. 目标架构（C 方案选型，已定）

```
RouteActivity
└── MochiTheme(controller)                 ← 替换 CustomTheme/KhatKitTheme 的色彩+字阶
    └── ProvideMochiOverscroll
        └── MochiNavHost(rememberMochiNavigator())   ← 替换 Nav3 的 NavDisplay 外壳
            ├── MochiTabScaffold             ← 根页 tab（聊天/探索/设置…）
            │   ├── MochiLargeTitleScaffold / MochiLazyLargeTitleScaffold
            │   └── MochiListSection + MochiListRow
            ├── MochiSheet + 子导航           ← 卡片弹层、表单
            └── MochiDialog / MochiToastHost / MochiBarGroup
```

- **Nav3 去留**：Mochi 自带 navigator/back/toolbar 共享，和 Nav3 的 `NavDisplay` 是两套语义。
  C 方案按 Mochi 为主：把现有 route 收敛成 Mochi 的 `push(route)`；
  仍需要 Nav3 的场景（预测性返回铁律、多栈）单独保留在 `app/RouteActivity.kt` 一处，并写注释说明边界。
  **不允许**两套导航同时接管返回键（P3 的验收项）。
- **适配层**：只保留 `app/core/ui/mochi/` 一层薄封装（`AppBar` 组合、`FeedCard`、`Section`、
  `EmptyState` 等），**不要**再复刻一个 105 个 composable 的 Kedge 克隆；封装里禁止出现风格分支。
- **卡片渲染器**：`khatkit-ui/.../NodeTreeRenderer.kt` 换 Mochi 皮肤；
  `ui.screen` 的 pager/nav 语义（`UiScreenPage`、`load_more`）行为不变，只换绘制件。

---

## 5. 组件映射表（105 → 27+，用于 P4 批量替换）

**主题/基础**
`KedgeTheme`→`MochiTheme` · `KedgeColors/KedgeTextStyles`→`MochiTheme.colors/textStyles`（我们的
`GoogleSans`/`ChatFont`/`CodeColor` 通过 `textStyles` 注入）· `KedgeStyle`/`MiuixMaterialBridge`/
`MiuixTextStyle`/`MiuixTextStyleScope`→**删除** · `KedgeLiquidGlassBackdrop`/`ProvideKedgeBlur`/
`KedgeBlurSurface`/`KedgeBlurredBar`→`mochiBackdropSource` + `MochiGlassSurface`（mochi-ios）

**容器/骨架**
`KedgePageScaffold`→`MochiLargeTitleScaffold`/`MochiCompactTitleScaffold`/`MochiLazy*`
`KedgeOverlayScaffold`→`MochiTabScaffold`/`MochiSheet`+独立 nav
`KedgeSurface`→`MochiGlassSurface`（bars）或 `MochiListCard`（内容）
`KedgeCard`/`KedgeWarningCard`→`MochiListCard`/`MochiListSection`(+`MochiListSectionFooter`)
`KedgeAdaptiveScaffold`/`KedgeFloatingToolbar`/`KedgeBreadcrumbBar`→**无对应，P6 自建**
`KedgeHorizontalDivider`/`KedgeVerticalDivider`→`MochiListDefaults` 分隔或自建

**顶栏/搜索**
`KedgeTopAppBar`/`KedgeSmallTopBar`/`KedgeLargeTopAppBar`/`KedgeTwoRowsTopAppBar`→`MochiTitleBar`
+ 对应 scaffold · `KedgeTopBarBackButton`→`MochiBackButton`
`KedgeSearchAppBar`/`KedgeSearchTopAppBar`/`KedgeSearchBar`/`KedgeSearchSheetHost`/
`KedgeSearchOverlayLayer`/`KedgeSearchLocalLayer`→`MochiSearchBar`（+`MochiGlassSurface`）

**导航**
`KedgeNavigationBar`/`KedgeBottomBar`/`KedgeLiquidGlassNavigationBar`→`MochiTabBar`/`MochiTabScaffold`

**按钮/标签**
`KedgeButton`→`MochiButton`(Filled/Tonal/ProminentGlass…) · `KedgeTextButton`→`MochiButton(style=Clear)`
或 `MochiBarButton` · `KedgeIconButton`→`MochiBarIconButton` · `KedgeBarGroup` 场景→`MochiBarGroup`
`KedgeFloatingActionButton`/`KedgeExtendedFloatingActionButton`→**无对应，P6 自建**
`KedgeAssistChip`/`KedgeInputChip`/`KedgeFilterChip`/`KedgeTag`→`MochiChip`/`MochiChipGroup`
`KedgeStatusTag`→`MochiListStatusChip` · `KedgeBadge`/`KedgeBadgedBox`→Mochi badge
`KedgeToggleButton`/`KedgeSegmentedButton`→`MochiSegmentedControl`

**表单**
`KedgeTextField`/`KedgeOutlinedTextField`/`KedgeTextFieldWithSlots`/`KedgeTextFieldWithState`/
`KedgeSegmentedTextField`→`MochiTextField`(+`secure=true` 自带显隐)
`KedgeSwitch`→`MochiSwitch` · `KedgeCheckbox`/`KedgeRadioButton`→`MochiSwitch`/`MochiListPickerRow`/
`MochiChip(showCheck)`（Mochi 无独立 checkbox/radio，**语义要重定**）
`KedgeSlider`→`MochiSlider` · `KedgeRangeSlider`→**无对应，P6 自建**
`KedgeSegmentedList*`/`KedgeOptionItem`/`KedgeExpandableOptionItem`/`KedgePreference*`
→`MochiListSection`/`MochiListRow`/`MochiListPickerRow`/`MochiListDisclosure`

**列表**
`KedgeListItem`→`MochiListRow` · `KedgePullToRefreshBox`→`MochiRefresh`(suspend `onRefresh`)
`KedgeSwipeToDismissBox`→**无对应，P6 自建**

**覆盖层/反馈**
`KedgeModalBottomSheet`/`KedgePrimaryBottomSheet`→`MochiSheet`(+`MochiSheetHeader`，多页给独立 navigator)
`KedgeDialog`/`KedgeAlertDialog*`/`KedgeDialogHost`/`KedgeEditDialog`/`KedgeLoadingDialog`→`MochiDialog`
`KedgeDropdownMenu`/`KedgeDropdownItem*`/`KedgeListPopup`→`MochiDropdown`/`MochiBarIconDropdown`/`MochiContextMenu`
`KedgeTooltipBox`/`KedgeTextTooltipBox`→`MochiPopover` · `KedgeToast*`/`KedgeSnackbarHost`→`MochiToastHost`
`KedgeProgressIndicator`→**无对应，P6 自建**

自绘清单（P6，Mochi 确实没有）：聊天气泡+输入区、Markdown/代码高亮、图片与头像形状
（`KedgeAvatarImage`/`KedgeAppIconImage`/`KedgeIconImage`）、进度条、FAB、SwipeToDismiss、
面包屑、RangeSlider、自适应 scaffold。

---

## 6. 分阶段计划（每阶段独立可验收，按此顺序）

**P0 清场与基线（~10 步）**
清掉已提交的调试日志 `KKPerf`（`khatkit/src/main/java/heizige/kk/khatkit/ui/NodeTree.kt`、
`UiBridgeHost.kt` 共 5 处）与工作区里那 1 个未提交文件（`KhatKitUiHost.kt`）；
`./gradlew :app:assembleDebug` + `./gradlew test` 全绿；把 §2 的数字记进提交说明。
**退出**：clean build 通过、`git status` 干净、数字与本节一致。

**P1 接入 spike（kill-switch，~20–40 步）**
`gradle/libs.versions.toml` 加 `mochi = "0.2.0"` + 三个库别名；app 加依赖；
写一屏 demo（`MochiTheme` + `MochiTabScaffold` + 一个 `MochiListSection` + 一个 `MochiSheet`），
真机跑通、截图、点一次返回。
**退出**：`:app:assembleDebug` 通过 + 真机 demo 可交互 + 记录 Compose 2026.09 下的运行结论。
**不过就止损**：转 A 方案（只给卡片弹层换 Mochi 皮肤），不要硬推。

**P2 主题接管（~30–50 步）**
`MochiTheme` 编到 `RouteActivity` 根部；`Theme.kt`/`CustomTheme.kt` 的色彩、字阶、形状来源改成
Mochi 的 `colorScheme`/`textStyles`/`MochiDefaults`；状态栏图标交给 MochiTheme（页面里禁止
`WindowCompat.isAppearanceLightStatusBars`）；聊天字阶用 `textStyles` 注入保留。
**退出**：`core/ui/theme` 下不再 import material3；浅色/深色截图与现值差距仅限预期；系统栏图标正常。

**P3 外壳与导航（~80–150 步）**
先替换 §2 表里 10 个 `KedgePageScaffold` 与 6 个 `KedgeBottomBar` 调用点，再换 `RouteActivity`
的导航外壳；返回键只由一处接管（Mochi 的 navigator 或 Nav3，二选一，写注释）；
预测性返回仍遵守「跟手只挂 transition 子树」。
**退出**：10 个 scaffold 调用点全部走 Mochi；三设备（含 API 26）返回手势与深链正常；
`KedgeStyle` 引用从 212 降到 ≤120。

**P4 Kedge 组件族批量替换（~120–200 步）**
按 §5 的组序：容器/顶栏/导航 → 按钮标签 → 表单 → 列表 → 覆盖层反馈。
每组一个提交：删该组 Kedge import、改 Mochi 调用、同步该组守卫/截图测试。
**退出**：`grep -rl 'import heizige.kk.kedge' app/src/main khatkit-ui/src/main` 归零（或只剩 §5 的自建清单）；
`:khatkit-ui` 与 `:app` 均能编；卡片弹层行为回归（pager/nav/load_more/预测返回）。

**P5 material3 残面清理（~100–180 步）**
顺序 `core/ui`(107) → `settings`(49) → 其余。`MaterialTheme.colorScheme`→`MochiTheme.colors`、
`Text`→Mochi 文本件、`Surface/Card/Icon/Divider`→Mochi 对应（缺失的进 P6 自建清单）。
**退出**：`grep -rl 'import androidx.compose.material3' app/src/main` 只剩第三方适配文件（需逐个写明原因）。

**P6 业务自绘组件重做（~80–140 步）**
聊天气泡/输入区、Markdown、代码高亮、图片与头像、进度条、FAB、SwipeToDismiss、面包屑、RangeSlider。
**退出**：聊天页、探索页、设置页三屏截图对照通过；键盘/long-press/滑动交互无回归。

**P7 收尾与记账（~30–60 步）**
删 `settings.gradle.kts` 里 `:kedge`/`:khromia` 的 includeBuild 与 substitution；
删 `libs.versions.toml` 里 miuix/kedge/khromia 相关项；删 `KedgeStyle` 分支与
`PrimaryBottomSheetIconSourceGuardTest`/`BottomSheetScrollSourceGuardTest`/`ListCardStyleTest` 的双风格断言（改单风格）；
更新 `AGENTS.md` 模块说明与 `docs/architecture-map.md`；重录截图基线；对比 APK 体积。
**退出**：`./gradlew test` + lint 全绿；APK 安装后三屏人工验收；文档数字与实测一致。

预算合计：**400–600 步 / 25–40M token**（token ≈ 步数 × 每步上下文，代码量次要）。
每阶段结束必须留一次真机截图与提交，禁止跨阶段合并提交。

---

## 7. 视觉与交互硬约束（每条都在 Mochi 官方 guide 里有出处）

1. 圆角只用 `MochiCapsule` / `MochiConcentric`；不手写 `RoundedCornerShape`（G2 连续曲率）。
2. 命中区 ≥44dp；禁用只变灰的"假 disabled"。
3. 颜色、字阶、形状、控件色**只从 `MochiTheme` / `LocalMochiControlPalette` 取**；
   外观差异写进 palette，不在页面写分支。
4. 玻璃/毛玻璃只覆盖 bars、menus、sheets、alerts；`Filled`/`Tonal` 按钮在不该玻璃的地方保持实心。
5. **WebView 不得进入 glass source**（Vulkan 设备渲染线程会崩）。卡片弹层的网页登录
   `ActionSheet`（[`KhatKitUiHost.kt`](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/khatkit/KhatKitUiHost.kt)）
   必须把它留在源外，或整块套 `MochiMaterialsScope(MochiMaterials())`。
6. 状态栏/导航栏图标对比度只由 `MochiTheme` 管。
7. 返回：`MochiBackButton` 负责 pop；预测性返回的跟手动画只挂 transition 子树，禁止用
   `NavigationEvent.progress` 驱动（沿用仓库既有铁律）。
8. 长页面用 `MochiLazy*Scaffold` / `LazyColumn` + `MochiTitleBar`；不要 `Column + verticalScroll`。
9. 动效尊重系统「减弱动效」；业务动作不能因降级不可用。
10. 触觉反馈用 Mochi 内置，不额外 `LocalHapticFeedback` 双发。

---

## 8. 测试与守卫改造

| 现有 | 处理 |
|---|---|
| `PrimaryBottomSheetIconSourceGuardTest` | 断言对象从 Kedge/Khromia 换成 Mochi 组件；保留"源码护栏"写法 |
| `BottomSheetScrollSourceGuardTest` | 同上，护栏改为「弹层滚动只走 Mochi sheet 的滚动容器」 |
| `ListCardStyleTest` | 双风格断言改单风格；颜色/字阶改为 Mochi token 断言 |
| `NodeTreePagerInteractionTest`（androidTest） | pager/nav 语义不变，只换渲染件，断言保留 |
| 截图基线（Kedge/Roborazzi 若已存在） | P7 统一重录；每阶段新增关键屏截图 |
| 新增 | P1 起加一条「app/src/main 不得出现 `top.yukonga.miuix` / `heizige.kk.kedge`」的源码护栏测试，灰度推进时用白名单 |

TDD 口径：每阶段先加护栏测试并让它红 → 改到绿；护栏测试本身算交付物。

---

## 9. 风险登记

| 风险 | 触发信号 | 缓解 |
|---|---|---|
| Compose 2026.09 下 Mochi 0.2.0 运行时不兼容 | P1 编译/运行失败、渲染异常 | 先 spike；不可修则止损转 A；必要时给 Mochi 提 issue 或本地 composite build 打补丁 |
| 0.2.0 API 后续破坏 | 升级时大面积编译错误 | 版本锁 `0.2.0`；升级单独一提交；不跟 main 分支 |
| Mochi 缺组件（§5 自建清单） | P4 撞墙 | P6 集中自建；不要临时塞 material3 |
| 玻璃性能/发热 | 真机掉帧 | 玻璃默认关；开关走 `MochiDialog` 警告（官方要求） |
| 预测性返回两套接管 | 返回键失效或双 pop | P3 明确单点接管，写注释 + 三设备验证 |
| 守卫测试大规模失效 | P4/P5 红一片 | 每阶段只放行该阶段白名单；护栏测试同步改 |
| 并行会话改同一仓库 | `git log` 出现非本会话提交、工作区文件被覆盖 | 开工前 `git status`/`git log`；建议单人单会话；每阶段提交后再动手下一阶段 |
| token 超预算 | 步数达计划上限 1.3× | 按阶段止损：P3 未在 150 步内把 10 个 scaffold 换完就退回 B（Kedge 适配层） |

---

## 10. 每阶段验证命令

```bash
# 编译 / 单测 / lint
./gradlew :app:assembleDebug
./gradlew :khatkit:test :khatkit-ui:test :app:test
./gradlew lint

# 计数（阶段退出条件直接照抄）
grep -rl 'import heizige.kk.kedge' --include=*.kt app/src/main khatkit-ui/src/main | wc -l
grep -rl 'import androidx.compose.material3' --include=*.kt app/src/main | wc -l
grep -rl 'import top.yukonga.miuix' --include=*.kt . --exclude-dir=build | wc -l

# 真机（当前测试设备）
adb -s "adb-3B6F5ME910B6H059-Sqr0AX._adb-tls-connect._tcp" install -r \
  app/build/outputs/apk/debug/app-universal-debug.apk
adb -s "adb-3B6F5ME910B6H059-Sqr0AX._adb-tls-connect._tcp" exec-out screencap -p > /tmp/after.png
```

卡片侧（只用现有卡验证弹层没坏）：`codex_resets` 已装在设备
`files/khatkit/cards/codex_resets/1.0.5/`；只推 `main.lua` 即可，不需重装 APK。

---

## 11. 已经拍板、不要在新会话里重新讨论的决定

1. 走 **C 方案全量替换**，不做"Kedge 留双风格 + 只换一处"。
2. 依赖形态：Maven Central `0.2.0`，**不 fork**；必须改源码时用同级目录 composite build 覆盖（`../Mochi`）。
3. 主题只接一次在根部；页面里不写外观分支（官方硬规）。
4. Nav3 与 Mochi navigator 二选一单点接管返回，不允许并存。
5. 卡片运行时（Lua API/节点词表/上限）不动。
6. 先 spike 后铺开：P1 不过就转 A 方案，不硬推。
7. 每阶段独立提交 + 真机截图，禁止跨阶段合并。

---

## 12. 新会话开工清单（照做即可）

```bash
cd /home/heizige/文档/Android/Project/KhatKit
git status --short && git log --oneline -5     # 确认没有被并行会话覆盖
cat docs/mochi-migration-plan.md | head -40    # 本文件
# 读官方接入必读（外网）
#   https://github.com/feice11/mochi-compose/blob/main/docs/guide/application-integration.md
#   .../docs/guide/compatibility.md  .../docs/guide/theme.md  .../docs/guide/navigation.md
./gradlew :app:assembleDebug                   # 先确认 P0 基线可编译
```

然后从 P0 开始，逐阶段交付；每阶段结束更新本文件的「阶段状态」小节（下表）。

| 阶段 | 状态 | 步数 | token |
|---|---|---:|---:|
| P0 清场与基线 | 未开始 | | |
| P1 接入 spike | 未开始 | | |
| P2 主题接管 | 未开始 | | |
| P3 外壳与导航 | 未开始 | | |
| P4 Kedge 组件替换 | 未开始 | | |
| P5 material3 清理 | 未开始 | | |
| P6 自绘组件重做 | 未开始 | | |
| P7 收尾与记账 | 未开始 | | |
