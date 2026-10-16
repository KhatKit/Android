# 设置模块 Kedge 化盘点（只读）

> 生成时间：2026-10-15T21:20+08:00
> 基线 commit：`5346ede23`（工作树当时有 27 个文件处于未提交改动，见 §2.3 并发说明）
> 真源：`Android/Project/Kedge/kedge/src/main/java/heizige/kk/kedge/**`（:kedge）、
> `Android/Project/Khromia/khromia/src/main/java/heizige/kk/khromia/components/`（MD3 侧 OptionItem / SegmentedRow / PressBounce）、
> `Android/Project/KhatKit/app/src/main/java/heizige/kk/khatkit/app/core/ui/components/**`（app 自建跨风格封装）
> 本文件为只读盘点产物，未修改任何 `.kt`。

## 统一前提

`:kedge` 的跨风格机制是 `LocalKedgeStyle.current`（`KedgeStyle.MD3Exp` / `KedgeStyle.Miuix`，`theme/KedgeStyle.kt:3`）。
除 `KedgeFloatingToolbar` 外，§1 表格里**每一个** Kedge 组件都在内部 `when (LocalKedgeStyle.current)` 分流：

- **MD3Exp 分支**：调真正的 `androidx.compose.material3.*`（少数几个调 Khromia 的 MD3 侧实现，如 `KedgeSwitch` → `OptionSwitch`、`KedgeOptionItem` → `OptionItem`、`KedgeAlertDialog` → `AnimatedAlertDialog`、`KedgeTabRow` → 胶囊分段）。因此 **MD3Exp 下替换通常等价或仅小差异**。
- **Miuix 分支**：调 `top.yukonga.miuix.kmp.*`。凡是「M3 组件在 Miuix 下会漏出 MD3 形态」的地方，换过去必然是**观感变化**（变好），也就是「必须上机看」的那一类。

计数口径：`调用点` = 非 Miuix 源文件里该组件的实际调用行（`Name(` 形式）；`死导入` = 只 import 无调用。

---

## 1) M3 组件 → :kedge 组件对照表

图例：**drop-in** = 签名/语义可直接换、观感不变；**可换** = 需改少量参数或包一层，观感不变；**改观感** = 语义等价但视觉变（通常正是目的）；**非 drop-in** = 数据驱动/插槽收窄，必须重写调用点。

| M3 组件 | :kedge 对应（文件:行） | 精确签名（必填加 **粗**） | MD3Exp / Miuix 行为差异 | drop-in? |
|---|---|---|---|---|
| `Scaffold` | `KedgePageScaffold` `adaptive/KedgeBlur.kt:136`；页壳场景另有 app 侧 `KedgeSettingsPageScaffold` `core/ui/components/ui/miuix/KedgeSettingsPageScaffold.kt:43` | `KedgePageScaffold(modifier=Modifier, topBar: @Composable ()->Unit = {}, bottomBar= {}, snackbarHost= {}, floatingActionButton= {}, floatingActionButtonPosition: FabPosition = End, containerColor = MaterialTheme.colorScheme.background, contentColor = contentColorFor(...), contentWindowInsets = ScaffoldDefaults.contentWindowInsets, md3ScrollBehavior: TopAppBarScrollBehavior? = null, **content: @Composable (PaddingValues) -> Unit**)` | MD3：M3 `Scaffold`；Miuix：Miuix `Scaffold` + 页面级 backdrop（`KedgeBlurSurface`）+ 本屏唯一的折叠行为（`rememberKedgeExitUntilCollapsedScrollBehavior`，`KedgeNavigation.kt:278`） | **可换**：`content` 必填；`topBar` 无默认值，需把原来的 `KedgePageLargeTopBar` 搬进去；MD3 下原本手写的 `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` 必须改传 `md3ScrollBehavior`（`KedgeSettingsPageScaffold` 已在 MD3 分支自动补，Miuix 下补了会抢滚动增量） |
| `Card` / `OutlinedCard` / `ElevatedCard` | `KedgeCard` `components/KedgeContainers.kt:143` | `KedgeCard(modifier=Modifier, contentPadding: PaddingValues = PaddingValues(16.dp), color: Color = Unspecified, contentColor: Color = Unspecified, shape: Shape? = null, onClick: (()->Unit)? = null, colors: CardColors? = null, border: BorderStroke? = null, **content: @Composable ColumnScope.()->Unit**)` | MD3：M3 `Card`；Miuix：`MiuixCard(cornerRadius, insideMargin = contentPadding)`，非均匀圆角时内部把背景画直角再由外层 `clip` 裁 | **可换**：① `border` **只在 MD3 生效**，`OutlinedCard` 直接换到 Miuix 下**丢描边**（`provider/ProviderModelCard.kt:165` 已有注释记录这个坑）；② `color/contentColor` 保持 `Color.Unspecified` 才会用各风格默认色（Miuix 默认 `surfaceContainer/onSurfaceContainer`，覆盖会在深色模式出问题）；③ `colors` 用 M3 `CardColors` |
| `Surface` | `KedgeSurface` `components/KedgeContainers.kt:58` | `KedgeSurface(modifier=Modifier, onClick: (()->Unit)? = null, enabled=true, color=Unspecified, contentColor=Unspecified, shape: Shape? = null, shadowElevation=0.dp, tonalElevation=0.dp, border: BorderStroke? = null, **content: @Composable ()->Unit**)` | MD3：`MdSurface`（点击重载分开调用）；Miuix：`MiuixSurface` + 手动 `Modifier.clickable`，shape 缺省 `RoundedCornerShape(16.dp)`（MD3 缺省 `MaterialTheme.shapes.medium`），并同步 MD3 的 `LocalContentColor` | **drop-in**（Miuix 下 `enabled=false` 只是去掉 clickable，无 M3 的 disabled 语义） |
| `HorizontalDivider` / `VerticalDivider` | `KedgeHorizontalDivider` `components/KedgeDividers.kt:23` / `KedgeVerticalDivider` `:47` | `KedgeHorizontalDivider(modifier=Modifier, thickness: Dp? = null, color: Color? = null)` | MD3：`MdDividerDefaults`；Miuix：`MiuixDividerDefaults.Thickness` + `MiuixTheme.colorScheme.outline` | **drop-in**（`thickness/color` 为 null 时取各风格默认） |
| `Button` | `KedgeButton` `components/KedgeButton.kt:68` | `KedgeButton(**onClick**, modifier=Modifier, enabled=true, variant: KedgeButtonVariant = Primary, miuixColors: MiuixButtonColors? = null, contentPadding = KedgeButtonDefaults.ContentPadding, interactionSource: MutableInteractionSource? = null, shapes: ButtonShapes? = null, colors: ButtonColors? = null, miuixCornerRadius = KedgeButtonDefaults.MiuixPillCornerRadius, **content: @Composable RowScope.()->Unit**)`；`KedgeButtonVariant = Primary \| Secondary \| Text`（`KedgeButton.kt:37`） | MD3：M3 `Button`（按 variant 选色/形状，`md3ButtonShapes()` `:59`）；Miuix：`MiuixButton`（默认胶囊圆角） | **可换**：`Button(colors = ButtonDefaults.buttonColors(...))` 需映射到 `variant` + `shapes` |
| `IconButton` / `FilledTonalIconButton` / `FilledIconButton` | `KedgeIconButton` `components/KedgeButton.kt:148` | `KedgeIconButton(**onClick**, modifier=Modifier, enabled=true, selected=false, variant: KedgeIconButtonVariant = Standard, shapes: IconButtonShapes? = null, colors: IconButtonColors? = null, **content: @Composable ()->Unit**)`；`KedgeIconButtonVariant = Standard \| Filled \| Tonal`（`:43`） | MD3：`IconButton` / `FilledIconButton` / `FilledTonalIconButton`；Miuix：`MiuixIconButton`（无 tonal 底色） | **drop-in**：`FilledTonalIconButton` → `variant = KedgeIconButtonVariant.Tonal`；`IconButtonDefaults.shapes()` 可直接喂 `shapes`。**Miuix 下 tonal 底色形态变化** |
| `TextButton` | `KedgeTextButton` `components/KedgeButton.kt:124` | `KedgeTextButton(**onClick**, modifier=Modifier, enabled=true, contentPadding=..., interactionSource=null, shapes: ButtonShapes? = null, colors: ButtonColors? = null, **content: @Composable RowScope.()->Unit**)` | MD3：M3 `TextButton`；Miuix：`MiuixButton` 文本形态 | **drop-in** |
| `Tab` + `SecondaryTabRow` / `ScrollableTabRow` | ⚠️ **两个同名不同物**：`:kedge` `KedgeTabRow<T>` `components/KedgeTabs.kt:36`（胶囊分段，MD3 下也不是下划线页签）；app 侧 `KedgeTabRow` `core/ui/components/ui/miuix/KedgeTabRow.kt:33`（MD3 分支就是 `SecondaryTabRow`/`SecondaryScrollableTabRow` + `Tab`） | app 版：`KedgeTabRow(**titles: List<String>**, **selectedTabIndex: Int**, **onTabSelected: (Int)->Unit**, modifier=Modifier, scrollable=false, containerColor=Transparent, edgePadding=0.dp)`；:kedge 版：`KedgeTabRow<T>(**selectedItem: T**, **onItemSelected: (T)->Unit**, **items: List<KedgeTabItem<T>>**, modifier=Modifier)`，`KedgeTabItem(key, label: String, icon: ImageVector? = null, enabled=true)` | app 版 Miuix：`MiuixTabRow`（≤3 项且非 scrollable）/ `MiuixTabRowWithContour`；MD3：原 `SecondaryTabRow` 行为不变。:kedge 版两种风格都是胶囊分段 | app 版**drop-in**（推荐目标）；:kedge 版**改观感**（MD3Exp 下也会变成胶囊分段）。⚠️ 名字冲突，同文件同时用需 import alias |
| `BadgedBox` (+`Badge`) | `KedgeBadgedBox` `components/KedgeBadges.kt:77` (+ `KedgeBadge` `:42`) | `KedgeBadgedBox(**badge: @Composable ()->Unit**, modifier=Modifier, **content: @Composable ()->Unit**)`；`KedgeBadge(modifier=Modifier, containerColor: Color? = null, contentColor: Color? = null, content: (@Composable RowScope.()->Unit)? = null)` | MD3：`MdBadgedBox`/`MdBadge`；Miuix：`MiuixBadgedBox`/`MiuixBadge` | **drop-in** |
| `NavigationBar` + `NavigationBarItem` | `KedgeBottomBar` `adaptive/KedgeBottomBar.kt:37` | `KedgeBottomBar(**items: List<KedgeNavItem>**, modifier=Modifier)`；`KedgeNavItem(**selected: Boolean**, **icon: ImageVector**, **label: String**, **onClick: ()->Unit**)`（`:22`） | MD3：原生 `NavigationBar`+`NavigationBarItem`；Miuix：`MiuixNavigationBar`+`MiuixNavigationBarItem`（自带指示条/圆角/字阶） | **非 drop-in**：数据驱动，不收 `@Composable` 插槽；`icon` 是 `ImageVector` 而非 `@Composable ()->Unit`（Painter / 自绘图标搬不过去）；label 只能是 `String` |
| `BottomAppBar` | ❌ **:kedge 无**（全仓 grep `BottomAppBar` 命中 0；:kedge 的 `KedgeBottomBar` 是 NavigationBar 替代，不是 BottomAppBar） | — | — | **缺件**（唯一现存用户：`feature/onboarding/GreetingPage.kt:452`） |
| `HorizontalFloatingToolbar` | `KedgeFloatingToolbar` `containers/KedgeFloatingToolbar.kt:26` | `KedgeFloatingToolbar(expanded=true, modifier=Modifier, leadingContent: (@Composable ()->Unit)? = null, **content: @Composable ()->Unit**)` | MD3：`MdHorizontalFloatingToolbar`（M3 的 `RowScope.() -> Unit` 被收窄成无接收者，内部包一层适配）；Miuix：Miuix 实现 | **可换**：`content` 里若依赖 `RowScope` 需改；M3 的 `FloatingToolbarDefaults.ScreenOffset` / `floatingToolbarVerticalNestedScroll` 未被封装，调用点要自己保留 |
| `SingleChoiceSegmentedButtonRow` + `SegmentedButton` | `KedgeSingleChoiceSegmentedRow` `components/KedgeSegmentedRow.kt:48` / `KedgeMultiChoiceSegmentedRow` `:72`；单按钮 `KedgeSegmentedButton` `components/KedgeTabs.kt:86` | `KedgeSingleChoiceSegmentedRow(**items: List<SegmentedItem>**, modifier=Modifier, fillWidth=true)`；`SegmentedItem(**label: String**, selected=false, onClick={}, leadingIcon: ImageVector? = null, enabled=true)`（Khromia `components/SegmentedRow.kt:54`）；`KedgeSegmentedButton(**selected**, **onClick**, modifier=Modifier, enabled=true, leadingIcon: ImageVector? = null, **content: @Composable RowScope.()->Unit**)` | MD3：M3 / Khromia `SingleChoiceSegmentedRow`；Miuix：`MiuixSingleChoiceSegmentedRow` | Row 版**非 drop-in**（数据驱动，label 只能 String，不能塞任意 `@Composable`、无 `shapes`/`icon` 插槽）；单按钮版 `KedgeSegmentedButton` 可自拼一行，`content` 支持任意内容 |
| `Switch` | `KedgeSwitch` `components/KedgeSelectionControls.kt:17` | `KedgeSwitch(**checked**, **onCheckedChange: ((Boolean)->Unit)?**, modifier=Modifier, enabled=true)` | MD3：**Khromia `OptionSwitch`**（不是 M3 `Switch`）；Miuix：`MiuixSwitch` | **drop-in**，但 **MD3 下观感也会变**（Khromia 版有 pressBounce/形状） |
| `Checkbox` | `KedgeCheckbox` `components/KedgeSelectionControls.kt:41` | `KedgeCheckbox(**checked**, **onCheckedChange: ((Boolean)->Unit)?**, modifier=Modifier, enabled=true)` | MD3：`MdCheckbox`；Miuix：`MiuixCheckbox` | **drop-in** |
| `RadioButton` | `KedgeRadioButton` `components/KedgeSelectionControls.kt:65` | `KedgeRadioButton(**selected**, **onClick: (()->Unit)?**, modifier=Modifier, enabled=true)` | MD3：`MdRadioButton`；Miuix：`MiuixRadioButton` | **drop-in** |
| `Slider` / `RangeSlider` | `KedgeSlider` `components/KedgeSliders.kt:13` / `KedgeRangeSlider` `:46` | `KedgeSlider(**value: Float**, **onValueChange: (Float)->Unit**, modifier=Modifier, enabled=true, valueRange=0f..1f, steps=0, onValueChangeFinished: (()->Unit)? = null)` | MD3：`MdSlider`/`MdRangeSlider`；Miuix：`MiuixSlider`/`MiuixRangeSlider` | **drop-in** |
| `OutlinedTextField` | `KedgeOutlinedTextField` `components/KedgeTextField.kt:161`；插槽版 `KedgeOutlinedTextFieldWithSlots` `:226` | `KedgeOutlinedTextField(**value: String**, **onValueChange: (String)->Unit**, modifier=Modifier, label: String? = null, placeholder: String? = null, enabled=true, readOnly=false, isError=false, supportingText: String? = null, leadingIcon: (@Composable ()->Unit)? = null, trailingIcon: (@Composable ()->Unit)? = null, singleLine=false, minLines=1, maxLines=..., keyboardOptions, keyboardActions, visualTransformation, interactionSource=null, shape=KedgeTextFieldDefaults.Md3Shape, textStyle: TextStyle? = null, colors: TextFieldColors? = null)` | MD3：M3 `OutlinedTextField`；Miuix：Miuix 输入框（形态差异明显，多行/边框尤其） | **可换**：`label/placeholder/supportingText` 是 **String 不是插槽**，原来是 `@Composable` 的要用 `KedgeOutlinedTextFieldWithSlots`；`colors` 仍是 M3 `TextFieldColors` |
| `AlertDialog` | `KedgeAlertDialog` `overlays/KedgeDialogs.kt:63`；插槽版 `KedgeAlertDialogSlots` `:124`；通用壳 `KedgeDialog` `:30` | `KedgeAlertDialog(**show: Boolean**, **onDismissRequest**, **title: String**, text: String? = null, confirmText: String = "OK", **onConfirm: ()->Unit**, dismissText: String? = null, onDismiss: (()->Unit)? = null)` | MD3：Khromia `AnimatedAlertDialog`/`AnimatedDialogWindow`；Miuix：`MiuixWindowDialog` + 自拼按钮行 | **非 drop-in**：多了 `show` 状态、`title/text` 变 String；插槽需求走 `KedgeAlertDialogSlots(onDismissRequest, confirmButton, modifier, dismissButton?, icon?, title?, text?)` |
| `ModalBottomSheet` | `KedgeModalBottomSheet` `overlays/KedgeSheets.kt:89`；另有 `KedgeBottomSheet` `:133`、`KedgePrimaryBottomSheet` `:166` | `KedgeModalBottomSheet(**show: Boolean**, **onDismissRequest**, modifier=Modifier, title: String? = null, miuixInsideMarginWidth = MiuixModalSheetInsideMarginWidth, **content: @Composable ColumnScope.()->Unit**)` | MD3：M3 `ModalBottomSheet`（内部 `rememberModalBottomSheetState(skipPartiallyExpanded = true)`，且**多包一层 `KedgeCard`**）；Miuix：`MiuixWindowBottomSheet(title, insideMargin=...)` | **非 drop-in**：`show=false` 时直接 return（没有 `sheetState`/`dragHandle` 可自定义）；Miuix 的内缩进要按调用点调 `miuixInsideMarginWidth` 抵消库的 24dp |
| `DropdownMenu` + `DropdownMenuItem` | `KedgeDropdownMenu` `overlays/KedgeMenus.kt:37` / `KedgeDropdownMenuSlots` `:138` + `KedgeDropdownItemSlot` `:177` / `KedgeListPopup` `:76` | `KedgeDropdownMenu(**expanded**, **onDismissRequest**, **items: List<KedgeMenuItem>**, modifier=Modifier, offset: DpOffset = Zero)`；`KedgeMenuItem(label: String, icon: ImageVector? = null, enabled=true, destructive=false, onClick)`（`:28`）；`KedgeDropdownItemSlot(**text: @Composable ()->Unit**, **onClick**, modifier=Modifier, leadingIcon: (@Composable ()->Unit)? = null, trailingIcon: (@Composable ()->Unit)? = null, enabled=true)` | MD3：M3 `DropdownMenu`/`DropdownMenuItem`（Popup，无遮罩）；Miuix：`OverlayListPopup(enableWindowDim = true)` + `ListPopupColumn`，item 用 `clickable` Row 手拼 | items 版**非 drop-in**（数据驱动）；`KedgeDropdownMenuSlots` + `KedgeDropdownItemSlot` 与 M3 签名对齐，**可换**。**Miuix 多一层背景压深**（观感变化） |
| `SnackbarHost` + `SnackbarHostState` | `rememberKedgeSnackbarHostState()` `overlays/KedgeSnackbar.kt:113` + `KedgeSnackbarHost` `:126` | `KedgeSnackbarHost(**state: KedgeSnackbarHostState**, modifier=Modifier)`；state 由 `rememberKedgeSnackbarHostState()` 提供（跟随风格、切换风格自动换实现），`showSnackbar` 返回 `KedgeSnackbarResult(Dismissed/ActionPerformed)` | MD3：`MdSnackbarHost(hostState = state.mdState)`；Miuix：`MiuixSnackbarHost(state = state.miuixState) { MiuixSnackbar(it) }` | **非 drop-in**：`SnackbarHostState` 类型换成 `KedgeSnackbarHostState`；`duration` 枚举换 `KedgeSnackbarDuration(Short/Long/Indefinite)` |
| `CircularProgressIndicator` / `LinearProgressIndicator` | `KedgeProgressIndicator` `overlays/KedgeFeedback.kt:226`（`progress: ()->Float` 重载）与 `:255`（`progress: Float?` 重载） | `KedgeProgressIndicator(modifier=Modifier, progress: Float? = null, type: KedgeProgressIndicatorType = Circular, color: Color? = null, trackColor: Color? = null, strokeWidth: Dp? = null)`；`KedgeProgressIndicatorType = Linear \| Circular \| Infinite \| Wavy`（`:45`） | MD3：`MdCircularProgressIndicator`/`MdLinearProgressIndicator`/`MdLinearWavyProgressIndicator`；Miuix：`MiuixCircularProgressIndicator`/`MiuixInfiniteProgressIndicator`/`MiuixLinearProgressIndicator` | **可换**：`strokeCap` 等 M3 专有参数无对应物，删掉即可 |
| `PullToRefreshBox` | `KedgePullToRefreshBox` `overlays/KedgePullToRefresh.kt:25` | `KedgePullToRefreshBox(**isRefreshing: Boolean**, **onRefresh: ()->Unit**, modifier=Modifier, **content: @Composable ()->Unit**)` | MD3：`MdPullToRefreshBox(state = rememberPullToRefreshState())`；Miuix：`MiuixPullToRefresh(pullToRefreshState = miuixRememberPullToRefreshState())` | **drop-in**（不用自己传 state；M3 的 `indicator`/`state` 参数没有对应物） |
| `ListItem` | `KedgeListItem` `components/KedgeListItem.kt:38` | `KedgeListItem(**headlineContent: @Composable ()->Unit**, modifier=Modifier, overlineContent: (@Composable ()->Unit)? = null, supportingContent: (@Composable ()->Unit)? = null, leadingContent: (@Composable ()->Unit)? = null, trailingContent: (@Composable ()->Unit)? = null, colors: ListItemColors? = null, tonalElevation: Dp? = null, shape: Shape? = null)` | MD3：`MdListItem`；Miuix：**不用 Miuix 的 ListItem**，而是 `KedgeSurface(surfaceContainer, RoundedCornerShape(20.dp))` + Row/Column 手拼（变成一张卡片） | **drop-in**（签名兼容）；**Miuix 下观感变化**（平铺项 → 卡片）；`colors`/`tonalElevation` 在 Miuix 分支被忽略 |

### 1.1 补充：设置模块实际在用的「更高层」跨风格组件

这些不是 M3 的 1:1 映射，但下一批改造会直接碰到：

| 组件 | 位置 | 签名要点 | 说明 |
|---|---|---|---|
| `KedgeSettingsPageScaffold` | app `core/ui/components/ui/miuix/KedgeSettingsPageScaffold.kt:43` | `(**title: String**, modifier=Modifier, navigationIcon = { BackButton() }, actions: RowScope.()->Unit = {}, bottomBar = {}, floatingActionButton = {}, floatingActionButtonPosition = FabPosition.End, largeTitle=true, scrollBehavior: TopAppBarScrollBehavior? = null, **content: @Composable (PaddingValues)->Unit**)` | 内部 `KedgePageScaffold` + `KedgePageLargeTopBar`/`KedgePageTopBar`，MD3 下自动补 `nestedScroll`，`containerColor = CustomColors.pageContainerColor`、`colors = CustomColors.topBarColors` 写死 |
| `KedgeArrowPreference` | :kedge `preferences/KedgePreferences.kt:211` | `(**title: String**, modifier=Modifier, summary: String? = null, icon: ImageVector? = null, **onClick: ()->Unit**)` | **缺 `enabled` / `startAction` / `endAction`**，app 侧 `PreferenceArrow` 有（见 §3） |
| `KedgeSwitchPreference` / `KedgeTextPreference` / `KedgePreferenceCategory` | :kedge `preferences/KedgePreferences.kt:106 / :69 / :50` | 分别对齐 M3 列表项 + Miuix preference | 设置项直用 |
| `KedgeOptionItem` | :kedge `components/KedgeOptionItem.kt:39` | `(**onClick: (()->Unit)?**, modifier=Modifier, shape=RoundedCornerShape(20.dp), backgroundColor=Unspecified, leadingContent?, overlineContent?, **titleContent: @Composable ()->Unit**, supportingContent?, trailingContent?)` | MD3：Khromia `OptionItem`；Miuix：`KedgeCard` + Row（insideMargin 16dp / minHeight 56dp） |
| `KedgeSegmentedList` / `KedgeSegmentedListItem` / `...SwitchItem` / `...CheckboxItem` / `...RadioItem` / `...TextField` | :kedge `components/KedgeSegmentedList.kt:115 / :149 / :185 / :206 / :227 / :248` | `KedgeSegmentedList(modifier, title: String? = null, **content: KedgeSegmentedListScope.()->Unit**)` | app 的 `CardGroup` 建在 `KedgeSegmentedListSlots`（`:96`）之上 |

---

## 2) 设置模块 `feature/settings/` 未 kedge 化调用点逐条清单

目录：`app/src/main/java/heizige/kk/khatkit/app/feature/settings/`（含 `mcp/ provider/ search/ components/` 子目录；`*Miuix.kt` 副本不计入本节，见 §3）。

### 2.0 计数总览（基线 `5346ede23`，非 Miuix 文件）

| 分组 | 未 kedge 化调用点 | 涉及文件 | 死导入（只 import 无调用） |
|---|---|---|---|
| 页壳 | **19** | 19 | 9 |
| 卡片 | 0 | 0 | 6 |
| 按钮 | **3** | 2 | 5 |
| 页签 | 0 | 0 | 14 |
| 底部栏 | 0 | 0 | 8 |
| 弹层 | 0 | 0 | 0 |
| 其它 | 0 | 0 | 11 |
| **合计** | **22** | 20 | **53** |

> 死导入分组口径：`FilledIconButton` 计入「按钮」、`HorizontalFloatingToolbar` 计入「其它」；
> 校验脚本见 §2.1 末的复现命令（`LIVE`/`DEAD` 逐条输出）。「调用点」按 `Name(` 计，
> 故 `SettingMcpPage.kt:34` 的 `FilledTonalIconButton` 一行算 2 个调用点（`:308`、`:316`）。

复现命令：
```bash
cd Android/Project/KhatKit/app/src/main/java/heizige/kk/khatkit/app/feature/settings
grep -rnE "(^|[^A-Za-z0-9_.])(Scaffold|Card|OutlinedCard|Surface|HorizontalDivider|Button|IconButton|FilledTonalIconButton|FilledIconButton|TextButton|Tab|SecondaryTabRow|BadgedBox|Badge|NavigationBar|NavigationBarItem|BottomAppBar|HorizontalFloatingToolbar|SingleChoiceSegmentedButtonRow|SegmentedButton|Switch|Checkbox|RadioButton|Slider|OutlinedTextField|AlertDialog|ModalBottomSheet|DropdownMenu|SnackbarHost|CircularProgressIndicator|PullToRefreshBox|ListItem)\(" \
  --include=*.kt . | grep -v Miuix.kt | grep -v ':import ' | grep -v 'Kedge'
```

### 2.1 逐条清单

**分组：页壳（19 调用点 / 19 文件）** — 全部为同一形态：
`Scaffold(topBar = { KedgePageLargeTopBar(title, navigationIcon = { BackButton() }, scrollBehavior, colors = CustomColors.topBarColors) }, modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), containerColor = CustomColors.pageContainerColor) { padding -> ... }`

| 文件:行 | 现用组件 | 建议替换 | 改观感 | 风险 |
|---|---|---|---|---|
| `SettingAboutPage.kt:140` | `Scaffold` + `KedgeSmallTopBar(titleAlpha, barColorAlpha)` + `containerColor = MaterialTheme.colorScheme.background` | 不能直接上 `KedgeSettingsPageScaffold`（它不暴露 `titleAlpha/barColorAlpha`，且写死 containerColor/topBarColors）→ 保持 `Scaffold`，或 `KedgePageScaffold` + `KedgeSmallTopBar` 手工组装 | 否 | **高**（本屏唯一的例外，见 §4） |
| `SettingDonatePage.kt:34` | `Scaffold` + `KedgePageLargeTopBar` | `KedgeSettingsPageScaffold(title, scrollBehavior) { … }` | 否 | **低**（机械） |
| `SettingFilesPage.kt:160` | 同上 + `actions` | `KedgeSettingsPageScaffold(…, actions)` | 否 | 低 |
| `SettingMcpPage.kt:146` | 同上 + `actions` | 同上 | 否 | 低 |
| `SettingModelPage.kt:67` | 同上 + `bottomBar` | `KedgeSettingsPageScaffold(…, bottomBar)` | 否 | 中（`bottomBar` 槽透传，需确认 `NavigationBar` 参数齐） |
| `SettingPackagePage.kt:97` | 同上 | 同上 | 否 | 低 |
| `SettingPage.kt:105` | 同上 | 同上 | 否 | 低 |
| `SettingPermissionsPage.kt:40` | 同上 | 同上 | 否 | 低 |
| `SettingPreferencesGeneralPage.kt:65` | 同上 | 同上 | 否 | 低 |
| `SettingPreferencesNetworkPage.kt:275` | 同上 | 同上 | 否 | 低 |
| `SettingPreferencesNotificationPage.kt:83` | 同上 | 同上 | 否 | 低 |
| `SettingPreferencesThemePage.kt:134` | 同上 | 同上 | 否 | 低 |
| `SettingPreferencesUICodePage.kt:50` | 同上 | 同上 | 否 | 低 |
| `SettingPreferencesUIFontPage.kt:108` | 同上 | 同上 | 否 | 低 |
| `SettingPreferencesUIMessagePage.kt:56` | 同上 | 同上 | 否 | 低 |
| `SettingPreferencesUIPage.kt:51` | 同上 | 同上 | 否 | 低 |
| `SettingSearchDetailPage.kt:112` | 同上 + `actions` | 同上 | 否 | 低 |
| `SettingSearchPage.kt:87` | 同上 + `actions` | 同上 | 否 | 低 |
| `SettingSpeechPage.kt:103` | 同上 + `actions` + `bottomBar` | 同上 | 否 | 中（`bottomBar` 槽） |

**分组：按钮（3 调用点 / 2 文件；另有 5 条死导入）** — 死导入：`FilledTonalIconButton` `mcp/McpCommonOptions.kt:34`、`mcp/McpConfigModal.kt:34`、`mcp/McpToolsConfigure.kt:34`；`FilledIconButton` `SettingProviderDetailPage.kt:32`、`provider/ProviderModelList.kt:32`。无裸 `IconButton` 调用点（设置页已全走 `KedgeIconButton`，Miuix 副本内另有 19 处）。

| 文件:行 | 现用组件 | 建议替换 | 改观感 | 风险 |
|---|---|---|---|---|
| `SettingMcpPage.kt:308` | `FilledTonalIconButton` | `KedgeIconButton(onClick, variant = KedgeIconButtonVariant.Tonal, shapes = IconButtonDefaults.shapes())` | 否（MD3 等价；Miuix 下变成 Miuix 图标按钮） | 低 |
| `SettingMcpPage.kt:316` | `FilledTonalIconButton` | 同上 | 否 | 低 |
| `SettingTriggersPage.kt:201` | `TextButton` | `KedgeTextButton` | 否 | 低 |

**分组：卡片（0 调用点 / 6 死导入）** — `OutlinedCard` `ProviderModelCard.kt:19`、`provider/ProviderModelList.kt:41`、`SettingProviderDetailPage.kt:41`；`Surface` `components/BalanceOption.kt:12`、`provider/ProviderModelList.kt:44`、`SettingProviderDetailPage.kt:44`。均为**残留死导入**，无调用点。

**分组：页签（0 调用点 / 14 死导入）** — `Tab` ×5、`SecondaryTabRow` ×5、`SingleChoiceSegmentedButtonRow` ×4（涉 `SettingMcpPage.kt:41,43,44`、`mcp/McpToolsConfigure.kt:41,43,44`、`mcp/McpCommonOptions.kt:40,41`、`mcp/McpConfigModal.kt:42`、`SettingSearchDetailPage.kt:23`、`SettingProviderDetailPage.kt:43,47`、`provider/ProviderModelList.kt:43,47`）。页签实际已走 app 侧跨风格 `KedgeTabRow`（`provider/ProviderModelList.kt:125`、`mcp/McpConfigModal.kt:106` 已 import）。

**分组：底部栏（0 调用点 / 8 死导入）** — `NavigationBar` ×3、`NavigationBarItem` ×4、`BottomAppBar` ×1（`SettingModelPage.kt:13,17`、`SettingSpeechPage.kt:26,27`、`SettingProviderDetailPage.kt:39,40`、`provider/ProviderModelList.kt:39,40`）。同类活调用点不在 settings 而在 `feature/onboarding/GreetingPage.kt:452`（`BottomAppBar`）。

**分组：弹层（0 调用点 / 0 死导入）** — 设置模块已全部走 app 封装/`:kedge`（`AppAlertDialog`、`KedgeAlertDialog`、`KedgeModalBottomSheet`）。同类活调用点：`core/ui/components/ui/TextArea.kt:203`（`BasicAlertDialog`）、`feature/automation/AutomationOverlayService.kt:878,884,900`（`DropdownMenu`/`DropdownMenuItem`）。

**分组：其它（0 调用点 / 11 死导入）** — `HorizontalDivider` ×3（`SettingMcpPage.kt:35`、`mcp/McpToolsConfigure.kt:35`、`mcp/McpConfigModal.kt:35`）、`PullToRefreshBox` ×4（`SettingMcpPage.kt:47`、`mcp/McpCommonOptions.kt:44`、`mcp/McpConfigModal.kt:45`、`mcp/McpToolsConfigure.kt:47`）、`HorizontalFloatingToolbar` ×2（`SettingProviderDetailPage.kt:35`、`provider/ProviderModelList.kt:35`）、`BadgedBox` ×2（`SettingProviderDetailPage.kt:30`、`provider/ProviderModelList.kt:30`）。活调用点已全部走 `KedgePullToRefreshBox`（`SettingMcpPage.kt:184`）等。

> ⚠️ **重要修正**：任务简报里的 ②「HorizontalDivider / IconButton / TextButton / BadgedBox / SecondaryTabRow+Tab 换成 :kedge」——按**实际调用点**核，设置模块在基线 commit 上只有 `TextButton` 1 处（`SettingTriggersPage.kt:201`）+ `FilledTonalIconButton` 2 处是活的；`HorizontalDivider`/`IconButton`/`BadgedBox`/`SecondaryTabRow`/`Tab`/`Surface`/`OutlinedCard`/`NavigationBar*`/`BottomAppBar`/`HorizontalFloatingToolbar`/`SingleChoiceSegmentedButtonRow`/`PullToRefreshBox`/`FilledIconButton` 在 settings 里**全是死导入，没有调用点**。简报里的数字来自「按 import 行计数」——那个口径会被上一轮迁移残留的死导入污染（本仓 53 条）。真正的下一批工作 = ① 剩余页壳 + 3 个按钮调用点 + 清 53 条死导入。

### 2.2 HEAD 死导入全量（53 条，可直接机械删除）

```
SettingMcpPage.kt:35 HorizontalDivider
SettingMcpPage.kt:41 SecondaryTabRow
SettingMcpPage.kt:43 SingleChoiceSegmentedButtonRow
SettingMcpPage.kt:44 Tab
SettingMcpPage.kt:47 PullToRefreshBox
SettingModelPage.kt:13 BottomAppBar
SettingModelPage.kt:17 NavigationBarItem
SettingProviderDetailPage.kt:30 BadgedBox
SettingProviderDetailPage.kt:32 FilledIconButton
SettingProviderDetailPage.kt:35 HorizontalFloatingToolbar
SettingProviderDetailPage.kt:39 NavigationBar
SettingProviderDetailPage.kt:40 NavigationBarItem
SettingProviderDetailPage.kt:41 OutlinedCard
SettingProviderDetailPage.kt:42 Scaffold
SettingProviderDetailPage.kt:43 SecondaryTabRow
SettingProviderDetailPage.kt:44 Surface
SettingProviderDetailPage.kt:47 Tab
SettingProviderPage.kt:33 Scaffold
SettingSearchDetailPage.kt:23 SingleChoiceSegmentedButtonRow
SettingSpeechPage.kt:26 NavigationBar
SettingSpeechPage.kt:27 NavigationBarItem
SettingTriggersPage.kt:21 Scaffold
SettingWebPage.kt:21 Scaffold
search/SearchProviderOptions.kt:20 Scaffold
provider/ProviderModelCard.kt:19 OutlinedCard
provider/ProviderModelList.kt:30 BadgedBox
provider/ProviderModelList.kt:32 FilledIconButton
provider/ProviderModelList.kt:35 HorizontalFloatingToolbar
provider/ProviderModelList.kt:39 NavigationBar
provider/ProviderModelList.kt:40 NavigationBarItem
provider/ProviderModelList.kt:41 OutlinedCard
provider/ProviderModelList.kt:42 Scaffold
provider/ProviderModelList.kt:43 SecondaryTabRow
provider/ProviderModelList.kt:44 Surface
provider/ProviderModelList.kt:47 Tab
mcp/McpCommonOptions.kt:34 FilledTonalIconButton
mcp/McpCommonOptions.kt:39 Scaffold
mcp/McpCommonOptions.kt:40 SecondaryTabRow
mcp/McpCommonOptions.kt:41 Tab
mcp/McpCommonOptions.kt:44 PullToRefreshBox
mcp/McpConfigModal.kt:34 FilledTonalIconButton
mcp/McpConfigModal.kt:35 HorizontalDivider
mcp/McpConfigModal.kt:40 Scaffold
mcp/McpConfigModal.kt:42 SingleChoiceSegmentedButtonRow
mcp/McpConfigModal.kt:45 PullToRefreshBox
mcp/McpToolsConfigure.kt:34 FilledTonalIconButton
mcp/McpToolsConfigure.kt:35 HorizontalDivider
mcp/McpToolsConfigure.kt:40 Scaffold
mcp/McpToolsConfigure.kt:41 SecondaryTabRow
mcp/McpToolsConfigure.kt:43 SingleChoiceSegmentedButtonRow
mcp/McpToolsConfigure.kt:44 Tab
mcp/McpToolsConfigure.kt:47 PullToRefreshBox
components/BalanceOption.kt:12 Surface
```

### 2.3 并发说明（重要）

盘点期间有另一个批次在并行改同一批文件，**工作树在变动**：

- 基线 `5346ede23`：19 个页壳 + 3 个按钮调用点未迁移。
- 快照 `21:14+08:00`（工作树 27 文件未提交）：页壳剩 1 个（`SettingAboutPage.kt:140`），`FilledTonalIconButton`/`TextButton` 已替换，活调用点剩 **1**。
- 快照 `21:20+08:00`：同上，`Scaffold` 活调用点仍只有 `SettingAboutPage.kt:140`。

**给下一批的建议**：以「工作树当前状态」为起点，先跑上文复现命令（`grep -v 'Kedge'` 过滤），不要以本文件的基线行号为准；行号已随并发编辑漂移（例如 `SettingTriggersPage.kt` 的 `TextButton` 基线在 201 行，迁移后为 196 行）。

---

## 3) Miuix 侧：23 个 `*Miuix.kt` 副本与 app 自建封装的职责

### 3.0 现实计数（先修正简报数字）

| 简报数字 | 实测（`5346ede23`，含工作树复核） | 复现命令 |
|---|---|---|
| MiuixSettingsPage **22 处** | ✅ **22 个调用点 / 22 个文件**（23 个副本中唯一不用它的是 `SettingModelPromptPageMiuix.kt`） | `grep -rn "MiuixSettingsPage(" --include=*Miuix.kt . \| wc -l` |
| miuixGroup **17 处** | ❌ **30 个调用点 / 11 个文件**。注意调用是尾随 lambda（`miuixGroup {`），用 `miuixGroup(` 会数成 0 | `grep -rn "miuixGroup *{" --include=*Miuix.kt . \| wc -l` |
| PreferenceArrow **11 处** | 45 个调用点 / 10 个文件（settings 内）；11 = **文件数**（加上定义文件 `PreferenceItems.kt` 共 12 个文件；app 全域 50 调用点） | `grep -rn "PreferenceArrow(" --include=*.kt . \| wc -l` |

其余高频封装（Miuix 副本内，`Name(` 或 `Name{` 计）：

| 封装 | 调用点 | 文件 |
|---|---|---|
| `MiuixSettingsPage` | 22 | 22 |
| `miuixGroup` | 30 | 11 |
| `PreferenceArrow` | 45 | 10 |
| `PreferenceSwitch` | 34 | 8 |
| `KedgeIconButton` | 19 | 9 |
| `KedgeTextButton` | 13 | 6 |
| `PreferenceSlider` | 8 | 4 |
| `KedgeCard` | 7 | 4 |
| `KedgeTextField` | 6 | 2 |
| `PreferenceInfo` / `PreferenceDropdown` | 4 / 3 | 1 / 3 |
| `KedgeSlider` / `KedgeOutlinedTextFieldWithSlots` / `KedgeProgressIndicator` | 3 / 3 / 2 | 1 / 1 / 2 |

Miuix 副本对 MD3 的依赖极薄：全部 23 个文件里 `androidx.compose.material3` 只剩 `Text`(6)、`MaterialTheme`(1)、`LocalTextStyle`(1)，即**几乎纯 Miuix**。总行数 **4782**，其 MD3 对应文件合计 **7864** 行（23 对文件，全部一一对应，无孤儿副本）。

### 3.1 三个封装承担的职责 → :kedge 是否已有等价

**(a) `MiuixSettingsPage`** — `core/ui/components/ui/miuix/MiuixSettingsPage.kt:56`
签名：`MiuixSettingsPage(title, modifier=Modifier, bottomInnerPadding=0.dp, enableBlur=true, navigationIcon: (@Composable ()->Unit)? = null, bottomBar: (@Composable ()->Unit)? = null, actions: RowScope.()->Unit = {}, containerColor = MiuixTheme.colorScheme.surface, titleAlpha=1f, smallTitleBar=false, barColorAlpha: Float? = null, lazyListState: LazyListState? = null, overlay: (@Composable BoxScope.()->Unit)? = null, content: LazyListScope.()->Unit)`

职责拆解与 :kedge 对应：

| 职责 | :kedge 等价物 | 齐备? |
|---|---|---|
| Miuix `Scaffold` + 页面级 backdrop（只录列表，顶栏做兄弟节点） | `KedgePageScaffold`（内部已是 Miuix Scaffold + `KedgeBlurSurface`） | ✅ |
| 大标题 / 小标题栏 | `KedgeLargeTopAppBar`（`adaptive/KedgeNavigation.kt:293`）/ `KedgeSmallTopBar`（`:371`） | ✅ |
| 标题/顶栏底色随滚动淡入（`titleAlpha` / `barColorAlpha`） | `KedgeSmallTopBar(titleAlpha, barColorAlpha)`、`KedgeLargeTopAppBar(titleAlpha)`（无 `barColorAlpha`） | ⚠️ 大标题栏缺 `barColorAlpha` |
| `overScrollVertical` + `scrollEndHaptic`（Miuix 过滚动/触感） | ❌ 全仓无 | ❌ **缺件** |
| `bottomInnerPadding`、`smallTitleBar` 开关 | 无（`KedgeSettingsPageScaffold` 有 `largeTitle` 可代 `smallTitleBar`） | ⚠️ 部分 |
| `lazyListState`（外部驱动的视差/折叠）、`overlay`（浮层与列表同级） | ❌ 无 | ❌ **缺件**（仅关于页在用） |
| LazyColumn 内容 + 12dp 水平边距 / 12dp 组间距（`MiuixPageMetrics`） | 无（间距常量在 app） | ❌ 未收进 :kedge |

**(b) `miuixGroup`** — `MiuixSettingsPage.kt:173`
`LazyListScope.miuixGroup(modifier=Modifier, key: Any? = null, content: @Composable ColumnScope.()->Unit)`，实现 = `item { KedgeCard(fillMaxWidth + padding(top = 12.dp), contentPadding = PaddingValues(0.dp), content) }`。

| 职责 | :kedge 等价物 | 齐备? |
|---|---|---|
| LazyListScope 扩展 + 分组卡片 + 12dp 上间距 | ❌ `:kedge` 没有 `LazyListScope` 级封装；app 侧 `CardGroup`（`core/ui/components/ui/CardGroup.kt:109`）建在 `KedgeSegmentedListSlots` 上，是另一套 | ❌ **缺件**（建议 :kedge 加 `KedgeCardGroup`，即把 `miuixGroup` 与 `MiuixPageMetrics.GroupSpacing` 下沉） |

**(c) `PreferenceArrow`** — `core/ui/components/ui/miuix/PreferenceItems.kt:184`
`PreferenceArrow(title, onClick, modifier=Modifier, summary: String? = null, icon: ImageVector? = null, enabled=true, startAction: (@Composable ()->Unit)? = null, endAction: (@Composable RowScope.()->Unit)? = null)` → Miuix 走 `ArrowPreference`，MD3 走 `KedgeListItem` + 手搓 `Icons.AutoMirrored.Rounded.KeyboardArrowRight`。

| 职责 | :kedge 等价物 | 齐备? |
|---|---|---|
| 跨风格「跳转项」 | `KedgeArrowPreference(title, modifier, summary, icon, onClick)` | ⚠️ **缺 `enabled` / `startAction` / `endAction`**，且 MD3 分支实现不同（Kedge 版 MD3 走 `KedgePreferenceRow`+Khromia `ButtonOption`/`OptionItem`，app 版走 `KedgeListItem`）→ 换过去 MD3 观感会变 |

### 3.2 「每页合并成一份跨风格文件」改造点与工作量

**目标形态**：删掉 `XxxMiuix.kt`，在 `Xxx.kt` 内按 `LocalKedgeStyle` 分流（沿用 `KedgeSettingsPageScaffold` + `CardGroup` + `KedgeArrowPreference`），做到「一份页面文件，两种风格」。

改造点：
1. **页壳**：`MiuixSettingsPage(...) { item { … } }` → `KedgeSettingsPageScaffold(title, scrollBehavior, ...) { padding -> LazyColumn { … } }`。**注意语义差异**：`MiuixSettingsPage` 的 `content` 是 `LazyListScope`（页面内容在它自己的 LazyColumn 里），`KedgeSettingsPageScaffold` 的 `content` 是 `PaddingValues -> Unit`（页面自己放滚动容器）。19 个 MD3 页里 16 个自己套 `LazyColumn`——合并时以 MD3 形态为准，否则会出现 LazyColumn 嵌套（内层 maxHeight=Infinity 直接崩，`KedgeSettingsPageScaffold` 的 KDoc 已记录这个坑）。
2. **分组**：`miuixGroup { … }`（30 处）→ 统一到 `CardGroup { item(...) }`（app 已有）或未来 :kedge 的 `KedgeCardGroup`。Miuix 分组卡片内边距必须保 `PaddingValues(0.dp)`（Miuix preference 自带 16dp insideMargin）。
3. **行项**：`PreferenceArrow`(45) → `KedgeArrowPreference`（**需先给它补 `enabled`/`startAction`/`endAction`**）；`PreferenceSwitch`(34) → `KedgeSwitchPreference`；`PreferenceSlider`(8) → `KedgeSlider` 行。
4. **保留分支的地方**：`SettingAboutPage`（流光/`titleAlpha`/`barColorAlpha`/`overlay`/`lazyListState`）、`SettingWebPageMiuix`（5 处 `miuixGroup`，WebView 工具页小标题栏）、`SettingPreferencesThemePage`（904/747 行，含自定义主题卡片列表）——这三页建议**最后做**，且先给 :kedge 补 `barColorAlpha`/`overlay`/`lazyListState`。
5. **`SettingModelPromptPageMiuix.kt`**（不传 `MiuixSettingsPage`）先单独确认它用什么页壳。

**工作量估计**（页数 × 影响行数；「影响行数」= 该页 Miuix 副本 + MD3 文件的正文行数，即合并时最小审阅面）：

| 页 | Miuix 行 | MD3 行 | 影响行数 | 备注 |
|---|---|---|---|---|
| SettingAboutPage | 321 | 294 | 615 | 高风险：流光/alpha/overlay |
| SettingDonatePage | 33 | 40 | 73 | 易 |
| SettingFilesPage | 181 | 377 | 558 | 中 |
| SettingMcpPage | 109 | 434 | 543 | Miuix 副本很薄，需按 MD3 重组 |
| SettingModelPage | 202 | 220 | 422 | 含 bottomBar |
| SettingModelPromptPage | 156 | 177 | 333 | 页壳不同 |
| SettingPackagePage | 155 | 217 | 372 | 中 |
| SettingPage | 284 | 485 | 769 | 大 |
| SettingPermissionsPage | 33 | 60 | 93 | 易 |
| SettingPreferencesGeneralPage | 204 | 310 | 514 | 中 |
| SettingPreferencesNetworkPage | 214 | 418 | 632 | 大 |
| SettingPreferencesNotificationPage | 158 | 205 | 363 | 中 |
| SettingPreferencesThemePage | 747 | 904 | 1651 | **最大** |
| SettingPreferencesUICodePage | 57 | 92 | 149 | 易 |
| SettingPreferencesUIFontPage | 271 | 331 | 602 | 大 |
| SettingPreferencesUIMessagePage | 103 | 167 | 270 | 中 |
| SettingPreferencesUIPage | 60 | 93 | 153 | 易 |
| SettingProviderDetailPage | 152 | 435 | 587 | 中 |
| SettingProviderPage | 573 | 615 | 1188 | **大** |
| SettingSearchDetailPage | 107 | 364 | 471 | 中 |
| SettingSearchPage | 142 | 405 | 547 | 中 |
| SettingSpeechPage | 182 | 829 | 1011 | **大**（829 行 MD3） |
| SettingWebPage | 338 | 392 | 730 | 高风险：miuixGroup×5 + 小标题栏 |
| **合计** | **4782** | **7864** | **12646** | 23 页 |

粗排：4 个「易」页（合计 468 行）+ 3 个高风险页（`About`/`Web`/`Theme`，合计 2996 行）+ 16 个中/大页（9182 行）。建议先做 :kedge 侧 3 个缺件（`KedgeArrowPreference` 补槽、`KedgeCardGroup`、`barColorAlpha/overlay/lazyListState`），再按「易 → 中 → 高」分批，每批 ≤4 页。

---

## 4) 结论

### 4.1 可机械替换（低风险，不需要上机）
1. **剩余页壳**：18/19 个页面是同一形态（`Scaffold` + `KedgePageLargeTopBar` + `nestedScroll` + `CustomColors.pageContainerColor`）→ 逐个换 `KedgeSettingsPageScaffold(title, scrollBehavior, actions?, bottomBar?)`。唯一例外 `SettingAboutPage.kt:140`。
2. **按钮 3 处**：`SettingMcpPage.kt:308,316` → `KedgeIconButton(variant = Tonal, shapes = IconButtonDefaults.shapes())`；`SettingTriggersPage.kt:201` → `KedgeTextButton`。
3. **死导入 53 条**（§2.2）纯删除，零风险，建议顺手做——不清掉会让下一轮 grep 盘点继续误判（本轮简报的 ② 就是这么错的）。
4. **已经在用、签名 drop-in 的**：`HorizontalDivider`、`Surface`、`BadgedBox`、`Switch/Checkbox/RadioButton/Slider`、`OutlinedTextField`、`PullToRefreshBox`、`ListItem`、`IconButton/TextButton/Button` —— 将来有新增调用点时直接照 §1 换，无需额外验证。

### 4.2 必须上机看（语义等价但观感变）
1. **`OutlinedCard` → `KedgeCard(border=…)`**：Miuix 分支**不画描边**（`components/KedgeContainers.kt:143` 的 `border` 只给 MD3），Miuix 下会丢轮廓 → 需要设计确认替代（描边色块 / `KedgeSurface`）。
2. **`Tab/SecondaryTabRow` → 用哪个 `KedgeTabRow`**：`:kedge` 版是胶囊分段（MD3 下也变），app 版才是原样。二选一要看设计；且两者同名，同文件用需要 alias。
3. **`NavigationBar+NavigationBarItem` → `KedgeBottomBar`**：数据驱动，自定义图标/插槽搬不过去，Miuix 下形态整体变化；`SettingModelPage.kt:77`、`SettingSpeechPage.kt:134` 两个 `bottomBar` 调用点要逐个看。
4. **`ListItem` → `KedgeListItem`**：Miuix 下从平铺项变卡片。
5. **`Switch` → `KedgeSwitch`**：MD3 分支其实走 Khromia `OptionSwitch`，**MD3Exp 下观感也会变**（有 pressBounce/形状差异），需要产品确认。
6. **`AlertDialog`/`ModalBottomSheet`/`DropdownMenu`/`SnackbarHost`**：都是「非 drop-in」重写，且 Miuix 下动画/遮罩/圆角不同（`OverlayListPopup` 带 window dim、`KedgeModalBottomSheet` 会多包一层 Card）。
7. **Miuix 侧三页**：`SettingAboutPage`(流光/alpha)、`SettingWebPageMiuix`(miuixGroup×5 + 小标题栏)、`SettingPreferencesThemePage`(1651 行影响面)。

### 4.3 :kedge 目前缺的组件（需要往 Kedge 加）
| 缺件 | 影响 | 建议 |
|---|---|---|
| `BottomAppBar`（含 `FloatingActionButtonPosition` 语义） | `feature/onboarding/GreetingPage.kt:452` 现用 M3，Miuix 下漏形态 | :kedge 加 `KedgeBottomAppBar`（MD3 `BottomAppBar` / Miuix 等价，没有就用 Surface+Row 兜底） |
| `KedgeArrowPreference` 的 `enabled` / `startAction` / `endAction` | 挡着 `PreferenceArrow`(45 处) 合并 | 扩参数（签名见 §3.1c） |
| `KedgeCardGroup`（`LazyListScope` 分组 + 组间距） | 挡着 `miuixGroup`(30 处) 与 `MiuixPageMetrics` 下沉 | 把 `miuixGroup` + 12dp 组间距搬进 :kedge |
| `KedgePageScaffold` 的 `titleAlpha` / `barColorAlpha` / `overlay` / `lazyListState` / `bottomInnerPadding` | 挡着 `MiuixSettingsPage`(22 处) 全量合并 | 从 `MiuixSettingsPage` 上移参数 |
| Miuix 过滚动/触感（`overScrollVertical` + `scrollEndHaptic`） | Miuix 页手感差异 | 在 `KedgePageScaffold` 的 Miuix 分支内建 |
| `SecondaryTabRow` 语义的跨风格 Tabs（下划线页签） | `:kedge` 只有胶囊版；`WorkspaceTerminalPage.kt:249,263` 与 settings 的 `McpConfigModal`/`ProviderModelList` 都靠 app 侧私有封装 | 把 app 的 `core/ui/components/ui/miuix/KedgeTabRow.kt` 下沉进 :kedge（注意改名/别名） |
| `Scaffold` 的 `snackbarHost` 跨风格（已有 `KedgeSnackbarHost`，但 `KedgeSettingsPageScaffold` 没有 `snackbarHost` 槽） | 需要 snackbar 的页面无法收口 | 给 `KedgeSettingsPageScaffold` 补 `snackbarHost` 透传 |

### 4.4 顺带发现的 :kedge 之外的残留（不在本任务 in-scope，供 captain 决定是否加派）
- `core/ui/components/ui/miuix/KedgeForm.kt:152` `KedgeFormDivider` 仍直接调 M3 `HorizontalDivider` → 可直接换 `KedgeHorizontalDivider`。
- `core/ui/components/nav/BackButton.kt:42` MD3 分支直接 `FilledTonalIconButton` → 可用 `KedgeIconButton(Tonal)`（但会改变 60+ 页顶栏观感，建议单独立项）。
- `core/ui/components/ui/miuix/KedgeTabRow.kt:68,76,83` 是跨风格页签的**实现本体**，属预期内的 M3 使用。
- `core/ui/components/ui/CardGroup.kt:150`、`ui/ChainOfThought.kt:455`、`activity/SafeModeActivity.kt:83` 的 `Scaffold`：前两个在 `@Preview`，第三个是 Activity 根，均非设置页壳。
