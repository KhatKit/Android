# 前端改造任务规格（交由 AI 实现）

> 状态：**待实现**。本文是给 AI 编码 agent 的施工图，不是已完成功能的说明。
> 涉及三个仓库：`KhatKit`（主应用）、`Kedge`（双风格组件库）、`Khromia`（MD3 组件库）。

---

## 0. 给 AI 的执行协议

1. **一次只做一个任务**，做完立刻提交（见 §5），再做下一个。
2. 每个任务开始前先读「现状核实」小节——**本项目里 4 个任务有 2 个的前提描述与代码实际状态不符**，照着字面改会做错方向。
3. 不许改的东西见 §0.3。
4. 每个任务结束前跑该任务的「验收」小节。
5. 遇到 §6 的决策点，停下来问人，不要自行拍板。

### 0.1 仓库依赖机制（最重要的一节）

三个仓库**不是**独立 gradle 项目，是 composite build：

- `KhatKit/settings.gradle.kts:64-95`：`includeBuild(../Kedge)` + `dependencySubstitution { substitute(module("heizige.kk:kedge")).using(project(":kedge")) }`，Khromia 同理。
- **硬前提**：`<workspace>/{KhatKit, Kedge, Khromia}` 必须是同级目录。同级目录不存在时构建会退化到 `maven.pkg.github.com/heizigelovecode/Khromia` 拉远端包（`settings.gradle.kts:50-60`），改源码不会生效。
- **Kedge 自己也 `includeBuild(../Khromia)`**（`Kedge/settings.gradle.kts:37-44`），所以 KhatKit 里 `heizige.kk:khromia` 会经 Kedge 解析到 `Khromia/:khromia`。
- 好处：**改 Kedge / Khromia 源码后 KhatKit 立刻生效，不需要发版、不需要改版本号**。
- 消费点：`khatkit-ui/build.gradle.kts:30-31` 是 `api(libs.kedge)` + `api(libs.khromia)`；`mediapicker/build.gradle.kts:37` 是 `implementation(libs.kedge)`。

### 0.2 三边版本必须对齐（踩了就运行期崩）

| 项 | KhatKit | Kedge | Khromia |
|---|---|---|---|
| material3 | `1.5.0-alpha29` | `1.5.0-alpha29` | `1.5.0-alpha29` |
| khromia | `1.6.5` | `1.6.2` | `1.6.5` |
| kedge | `0.1.2` | — | — |

- **material3 三边锁死 `1.5.0-alpha29`**（Khromia `khromia/build.gradle.kts:56-59` 有明确注释：必须与 KhatKit 一致，否则运行期 `NoSuchMethodError`）。**任何任务都不得升级 material3。**
- Kedge 内部 pin 的 khromia 是 `1.6.2`，KhatKit 是 `1.6.5`——composite build 下走源码所以无害，但**如果新增了 Khromia 的 API 而 Kedge 侧版本号没跟着可见，就是编译过、运行崩**。本轮任务若新增 Khromia API，要么保证 Kedge 能看到（composite 下天然可见），要么同步 `Kedge/gradle/libs.versions.toml:12`。

### 0.3 不许改的

- 三个仓库的 `material3` 版本（见 §0.2）。
- KhatKit 的包名 `heizige.kk.khatkit`、模块划分、`khatkit` / `card-validator` 的依赖方向。
- `KedgeOptionItem` 在 Miuix 分支的 shape 契约：**调用点必须按 index/count 算出 shape 传下去**（`KedgeOptionItem.kt:57-103` 的 KDoc 明确写了，不传会退回全圆角，同组选项散成独立卡片）。
- 已发布卡片的脚本行为（`docs/bridge-expansion-spec.md` §1.3 同样适用）。
- 顺手重构无关代码、顺手升级无关依赖、格式化全仓。

### 0.4 双风格系统（读任何 UI 代码前必须懂）

`KedgeStyle` = `MD3Exp` | `Miuix`，通过 `LocalKedgeStyle` 下发（`Kedge/theme/KedgeTheme.kt:25`，`staticCompositionLocalOf`，在 `KedgeTheme.kt:36` 提供一次）。

三种既有写法，**改动时必须沿用所在文件已有的那种**：

| 写法 | 形态 | 出现在 |
|---|---|---|
| A. 整文件分流 | `if (LocalKedgeStyle.current == KedgeStyle.Miuix) { XxxPageMiuix(); return }` | 18 个设置页，如 `SettingPage.kt:94-99` |
| B. 样式无关骨架 | `KedgeSettingsPageScaffold { }`，风格交给共享组件 | `SettingWebPage.kt:135`、`SettingTriggersPage.kt:148`、`HistoryPage.kt:78` |
| C. 组件内 `when` | `when (LocalKedgeStyle.current) { MD3Exp -> KhromiaXxx(); Miuix -> MiuixXxx() }` | `KedgeOptionItem.kt:40`、`KedgeIconButton.kt:159`、`KedgePageBars.kt:40/96/134` |

**Khromia 没有任何 Miuix 代码，也没有 Miuix 依赖**（全仓 `grep -i "miuix\|yukonga"` 零命中）。职责划分：`Kedge` = 双风格分发层，`Khromia` = 纯 MD3 层。这条是任务 4 的设计基础。

两座桥让 Miuix 对遗留 MD3 代码透明：

- `MiuixMaterialThemeBridge`（`Kedge/theme/MiuixMaterialBridge.kt:116-136`）在 Miuix 主题内重新 provide `MaterialTheme(colorScheme = miuixToMaterialScheme(dark), typography = miuixTypography())`，所以任何 `MaterialTheme.colorScheme.*` / `typography.*` 读取自动得到 Miuix 值。
- `MiuixTextStyleScope(style) { … }`（`Kedge/theme/MiuixTextStyle.kt:22`）覆盖 `LocalTextStyle`，让插槽里**没写 style 的裸 `Text(...)`** 也拿到 Miuix 字阶。

**但桥只覆盖颜色和字号，不覆盖形状和圆角**。硬编码 `RoundedCornerShape(20.dp)` / `(28.dp)` 的组件在 Miuix 下会露出 MD3 形状，这是任务 4 要处理的。

---

## 任务 1 — Web 服务器页 / 自动化触发器页的行组件

### 1.1 现状核实（⚠️ 任务描述的前提与代码不符）

**这两个页面已经用了 Kedge 的 `KedgeOptionItem`，不是自己写的。**

调用链：

```
SettingWebPage.kt:194-199      CardGroup { item(...) }          ← 本地 DSL
  └─ app/.../core/ui/components/ui/CardGroup.kt:94-111  CardGroupListItem()
       └─ KedgeOptionItem(modifier, onClick = item.onClick ?: {}, shape = cards.indexedShape(...))
                                                              ← 已经是 Kedge 的
```

`settingItem(SwitchSetting(...))` 同样收敛到 `item(...)`（`SettingItem.kt:106` → `:169-212` `setItem`），所以也已经是 Kedge。

全仓直接调用 `KedgeOptionItem` 的只有 6 个文件：`CardGroup.kt:101`、`PermissionChecklist.kt:597,634`、`ThemePickers.kt:97`、`SettingPage.kt:424,477`、`SettingThemePage.kt:348`、`GreetingPage.kt:693,772,916,1087`——**两个目标页都不在里面，因为它们走的是 DSL**。

顺带查到一个真问题：`SettingWebPage.kt:22` `import heizige.kk.khromia.components.OptionSwitch` **是死导入**，文件正文从未使用（唯一命中就是 import 那行）。而且 `OptionSwitch` 是纯 MD3 组件，在 Miuix 下会露馅。

**已修（不用再做）**：原先 `setItem` 只给 `NavSetting` 挂行级 onClick（`onClick = (item as? NavSetting)?.onClick`），`SwitchSetting` 转型失败拿到 `null`、再被 `CardGroup.kt:103` 兜成空 lambda，导致**开关行整行可点、有按压回弹但状态不变**，必须点开关本体。全应用 29 处 `SwitchSetting(...)` 都中招。已改为按类型分发（导航行 + 开关行给整行点击，带 `enabled` 门控），`SwitchRow`（`ProviderConfigure.kt` 8 处）也补了 `clickable`。本次任务只需保证迁移后**行级点击行为不回退**。

### 1.2 真正还手写的是什么

| 手写物 | 位置 | Kedge 已有对应物 |
|---|---|---|
| `CardGroup` + `CardGroupScope` + `CardGroupListItem`（187 行 DSL 包装） | `app/.../core/ui/components/ui/CardGroup.kt` | `KedgeSegmentedList` / `KedgeSegmentedListScope` / `KedgeSegmentedListItem`（`Kedge/components/KedgeSegmentedList.kt:52, :86`）——**概念完全一致**：分组容器 + item + 首末项圆角 + 0dp(Miuix)/2dp(MD3) 间距 |
| `SettingItem` 家族（`SwitchSetting`/`SliderSetting`/`InfoSetting`/`NavSetting` + `setItem`） | `app/.../core/ui/components/ui/SettingItem.kt` | `KedgeSegmentedSwitchItem`（`:122`）/ `KedgeSegmentedCheckboxItem`（`:143`）/ `KedgeSegmentedRadioItem`（`:164`）/ `KedgeSegmentedTextField`（`:185`） |
| `SwitchRow` / `SliderRow`（裸 `Row` + `Text`，**无图标、无摘要、无风格分支**） | `SettingItem.kt:118-137` / `:141-167` | `KedgeSegmentedSwitchItem` / `KedgeSegmentedListItem` |
| `ListCardStyle`（形状与间距策略） | `app/.../core/ui/theme/ListCardStyle.kt` | ⚠️ **无对应物，且不可删**，见 §1.4 风险 |

### 1.3 目标

把两个页面从「KhatKit 自研 `CardGroup` DSL」迁到「Kedge `KedgeSegmentedList` 家族」，让行的渲染、形状、间距、风格分支全部由组件库负责；KhatKit 只保留**策略**（`ListCardStyle`）与**页面专属内容**（`KedgeTextField`、`KedgeFilterChip`、日志条目等）。

### 1.4 风险（先读，会决定怎么做）

- **`ListCardStyle` 是 load-bearing，不能删**。`KedgeSegmentedList` 自带形状策略，但 `KedgeOptionItem` 的 KDoc（`KedgeOptionItem.kt:88-96`）要求**调用点按 index/count 算好 shape 传下去**。所以正确形态是「用 Kedge 的容器与 item，shape 仍由 `listCardStyle().indexedShape(index, count)` 提供」，不是「整个换成 KedgeSegmentedList 就完事」。
- `CardGroup.kt:103` 的 `onClick = item.onClick ?: {}`：**不可点的行也会吃 `pressBounce` 按压回弹**（开关行的行级点击已修，见 §1.1；这条针对纯信息行）。迁移时把 `onClick` 做成可空并区分，避免纯信息行有按压反馈。参照物是 Kedge 的做法：`KedgeSegmentedListItem` 用 `clickable(enabled = enabled && onClick != null)`（`KedgeSegmentedList.kt:96`），`KedgeSegmentedSwitchItem` 的 `onClick = { onCheckedChange(!checked) }` 因此不需要自己再判 enabled。
- `CardGroup.kt` 的 `CardGroupItem.colors` 字段目前**被静默丢弃**（`CardGroupListItem` 没有传给 `KedgeOptionItem`）。迁移时要么接上（KedgeOptionItem 无 colors 参数，需另走 `KedgeCard`/`KedgeSurface`），要么删掉这个字段，别留假的。
- `SettingWebPage` 里 4 行的 `trailingContent = { KedgeTextField(...) }` 是行内控件，迁移后要确认 `KedgeSegmentedListItem` 的 trailing 插槽布局不会把输入框压扁。
- 两个页面**没有 Miuix 变体文件**（写法 B），所以迁移后不需要新建 `XxxPageMiuix.kt`——风格由 Kedge 组件内部处理。
- `SettingPage.kt:264-275` 是导航到这两个页面的入口行，`SettingPageMiuix.kt:163,169` 是 Miuix 版入口。**如果只改这两个页面、不改 `CardGroup`，会出现同一份设置里两种行样式**。要么一并迁 `CardGroup`（影响 8+ 文件），要么先只迁这两个页面并在文档里标注不一致待后续统一。→ 这是决策点 D1。

### 1.5 步骤

1. 删 `SettingWebPage.kt:22` 的死导入 `OptionSwitch`。
2. 在 Kedge 侧先确认 `KedgeSegmentedListItem` 的 trailing 插槽能承载 `KedgeTextField` / `KedgeFilterChip`；不够用就在 Kedge 补一个缺口（补 Kedge → 单独提交 → 再改 KhatKit）。
3. 迁 `SettingWebPage`：`CardGroup { item(...) }` → `KedgeSegmentedList { segmentedListItem(...) }`，形状仍传 `listCardStyle().indexedShape(index, count)`。
4. 迁 `SettingTriggersPage`：同上，注意它的 5 个 section 有 `title`、每个 section 内有 `cards.forEach` 动态行，index/count 要在 section 内重新计数。
5. `SettingItem.kt` 里 `SwitchRow` / `SliderRow` 两个裸 Row 的迁移**另开一次改动，勿夹带**：`SwitchRow` 有 8 处在 `ProviderConfigure.kt` 用，`SliderRow` 用于表单中间，都不在本次两个页面的路径上。
6. 每页一个提交。

### 1.6 验收

```bash
cd ../Kedge && ./gradlew :kedge:assembleDebug   # 若改了 Kedge
cd ../KhatKit && ./gradlew assembleDebug && ./gradlew lint
```

**人工检查（必须两种风格各过一遍）**：设置里打开「Web 服务器」和「自动化触发器」，在 `MD3 Exp` 与 `Miuix` 两种风格下分别确认——行高/圆角/间距与迁移前一致（同组选项仍是"连成一片"而非散成独立卡片）、Miuix 下不漏出 MD3 圆角、开关与输入框仍能交互、Miuix 下 `KedgeTextField` 的字号字重走 Miuix 字阶。

---

## 任务 2 — ChatPage 搜索动画对齐侧边栏

### 2.1 现状核实

**两边的动画引擎已经共用同一个 hook**，搜索展开状态机、预测返回、时长都一致：

- `app/.../core/ui/hooks/PredictiveSearchBack.kt`：`rememberSearchExpandState(expanded, onCollapse, animationSpec = tween(220))`（`:28`），内部 `Animatable` + `PredictiveBackHandler(enabled = expanded)`（`:42`），手势 `snapTo(1f - event.progress)`、提交 `animateTo(0f)`、取消 `animateTo(1f)` 弹回。全项目仅 3 处用 `PredictiveBackHandler`，这里就是参考实现。
- 调用点恰好两处：`ChatDrawer.kt:241`、`ChatPage.kt:683`。

所以**不是"补一个动画"，而是消除两处实现的差异**。

### 2.2 差异清单（这就是要改的）

| # | 差异 | 侧边栏（参考） | ChatPage | 严重度 |
|---|---|---|---|---|
| **D1** | **搜索框字号与标题不一致** | 标题与输入框**共用 `LocalTextStyle.current`**（`ChatDrawer.kt:297` 无 style，`:325` 显式取同一个），两边都是 `titleLarge` 22sp | 标题用 `KedgeTextStyles.displayTitle()`（`ChatPage.kt:834`），输入框用 `LocalTextStyle.current`（`:861,869`）。**MD3 下 `displayTitle()` = `headlineMedium` 28sp，而输入框继承 `titleLarge` 22sp → 28 vs 22，肉眼可见** | 🔴 **这就是用户描述的问题** |
| **D2** | 搜索框不自动聚焦 | 有：`ChatDrawer.kt:193-199`，`FocusRequester` + `delay(100)` 后 `requestFocus()` | **无**：全文件没有 `FocusRequester`，点开后要手动点输入框 | 🔴 |
| **D3** | 输入框状态不参与配置变更 | `rememberSaveable`（`ChatDrawer.kt:192`） | `remember`（`ChatPage.kt:300` `previewSearchQuery`） | 🟡 |
| **D4** | actions 图标切换方式 | 硬切 `if (searchVisible && …) close else search`（`ChatDrawer.kt:348`），**无动画** | 同槽交叉淡化 + 0.15 缩放冲量（`ChatPage.kt:886-907`） | 🟡 两边都不理想，见 D6 |
| **D5** | Miuix 分支预测返回不跟手 | 不适用（抽屉顶栏始终是 MD3 `TopAppBar`，`ChatDrawer.kt:263`） | `ChatPage.kt:697-749` 传了 `dragProgress = searchProgress.value` 却**没传 `dragging`**，而 `KedgeMiuixMorphingTitleBar.kt:88-90` 的 `snapTo` 被 `if (dragging)` 门控，`dragging` 默认 `false`（`:74`）→ 手势拖动完全无反馈，只剩内部 tween | 🟡 |
| **D6** | 导航图标 morph 手法 | alpha + `translationX`（`+24dp → 0`） | alpha + `scale`（0.85→1.0）+ 大屏时 width morph（`ChatPage.kt:756-800`） | 🟡 |

### 2.3 用户需求逐条对照

> 「把 chatpage 的搜索动画逻辑弄得和侧边栏的逻辑演绎；点击搜索是左边图标变成返回，title 变成输入框（字体大小和 title 一样，动画、预测返回一样）」

- 左图标变返回 ✅ 已有（`ChatPage.kt:784-797`），但手法与侧边栏不同（D6）。
- title 变输入框 ✅ 已有（`:849-876`）。
- **字体大小和 title 一样** ❌ **D1 就是这个 bug**。
- 动画一样 ❌ D4/D6 不一致。
- 预测返回一样 ⚠️ MD3 一致，Miuix 分支不跟手（D5）。

### 2.4 目标与做法

**核心目标只有一个：让标题和输入框的字号在两种风格下都严格相等。**

1. **修 D1（必做）**。`ChatPage.kt` 的输入框与占位符改用和标题完全同一个 token：`KedgeTextStyles.displayTitle()`。这样 MD3 下是 28↔28、Miuix 下是 `title3` 20↔20，一次改对两边。
   - 注意 `KedgeTextStyles.displayTitle()` 在 MD3 侧是 `headlineMedium` 28sp，比原来的 `titleLarge` 22sp 大一号。如果视觉上觉得过大，**正确做法是给 `KedgeTextStyles` 加一个 `topBarTitle()` token**（MD3 → `titleLarge`，Miuix → `title3`）并让标题与输入框都用它，而不是在两个地方各写一个字号。
   - 侧边栏要不要一起对齐到同一 token，由 D2 决策点决定。
2. **修 D2**：给 `ChatPage` 的搜索框加 `FocusRequester`，复制 `ChatDrawer.kt:193-199` 的 `LaunchedEffect + delay(100) + requestFocus()`。`delay(100)` 是为了等动画起手，直接 `requestFocus()` 会顶掉入场动画。
3. **修 D3**：`previewSearchQuery` 改 `rememberSaveable`。
4. **修 D5**：给 `KedgeMiuixMorphingTitleBar` 调用点补 `dragging = searchExpand.dragging.value`（若该 composable 还没有 `dragging` 参数，先在 `KedgeMiuixMorphingTitleBar.kt` 的参数表加上，默认 `false`，保持既有调用兼容）。
5. **统一 D4/D6（建议）**：两边的 morph 手法要一致，推荐**以侧边栏为准**（alpha + `translationX`，无缩放），把 `ChatPage.kt:756-800` 的 `scaleX/scaleY` 冲量与 `:886-907` 的 actions 交叉淡化统一成侧边栏的写法。理由：`scale` 冲量会让导航图标在 0.5 附近突然"跳一下"，因为两个图标靠 `enabled = progress < 0.5f / >= 0.5f`（`:770,786`）互斥切换，缩放叠加会放大跳变感；`translationX` 方案没有这个突变点。
   - 若改成统一，需要接受侧边栏 actions 图标变成无动画硬切，或两边都做成交叉淡化——两边都做交叉淡化更好，但那是新增行为，不是"对齐"，需 D3 决策点确认。
6. **顺带**：`ChatPage.kt:849` 是 `if (previewMode || searchVisible)`（**OR**），`:805` 是 `if (titleVisible)`。`previewMode` 为 true 但 progress 还是 0 的那一帧，输入框会以 `alpha = 0` 挂载，无害；但退出时 `previewMode` 先变 false、`progress` 还在动画，会提前卸载输入框导致退场动画被切断。改成 `if (searchVisible)` 与标题的 `titleVisible` 对称。→ D4 决策点。

### 2.5 验收

```bash
cd ../KhatKit && ./gradlew assembleDebug && ./gradlew lint
```

**人工检查**：
- MD3 与 Miuix 两种风格下，打开搜索后**输入框与展开前的标题肉眼同字号**（可用截图并排比对，别只凭感觉）。
- 点击搜索后自动获得焦点、键盘弹出。
- 从左边缘右滑（预测返回）能跟手拖动，且**两种风格都跟手**。
- 退出搜索时输入框不消失、标题淡入无闪烁（针对 §2.4 第 6 点）。
- 侧边栏搜索的现有行为不回退（若 D2 选择不一起改）。

---

## 任务 3 — 设置页底部留白对齐顶部

### 3.1 现状

三个页面的 `LazyColumn` 都是同一写法：

```kotlin
contentPadding = innerPadding + PaddingValues(8.dp),
```

| 文件 | 行 |
|---|---|
| `app/.../feature/settings/SettingPage.kt` | **149** |
| `app/.../feature/settings/SettingWebPage.kt` | **191** |
| `app/.../feature/settings/SettingTriggersPage.kt` | **154** |

顶部留白 = `innerPadding` 的 top（`LargeFlexibleTopAppBar` 展开高度 + 状态栏 inset，见 `KedgePageLargeTopBar`）+ 8dp。

底部 = `innerPadding` 的 bottom + 8dp。而这两个页面都**没有 `bottomBar`**，所以 `innerPadding.bottom` 只剩系统手势条那一小条 → 最后一张卡几乎贴着屏幕底。

### 3.2 关键坑

**Miuix 风格下 `innerPadding.bottom` 恰好是 `0.dp`**——`MiuixPageScaffold` 的 `contentWindowInsets` 只保留 `Horizontal`（`core/ui/components/ui/miuix/KedgeSettingsPageScaffold.kt:275-277` 委托链的终点）。所以 Miuix 下底部只有 8dp，比 MD3 更空。修复必须两种风格都成立。

### 3.3 做法

三处统一改成对称的显式四边：

```kotlin
contentPadding = innerPadding + PaddingValues(8.dp, 8.dp, 8.dp, 8.dp + <extra>)
```

`extra` 的取值：

- `SettingPage.kt`：`0.dp`。用户要求「和上面一样」即可。
- `SettingTriggersPage.kt`：`0.dp`。
- `SettingWebPage.kt`：这个页面有 FAB（`KedgeExtendedFloatingActionButton`，`SettingWebPage.kt:138-187`），留白会被 FAB 压住。**先在真机上确认末项是否被 FAB 遮挡**，需要则给 `extra` 加到 64.dp 量级，或把 FAB 的 `bottom` padding 与此项统一。

现成的同形写法可直接抄（都在本仓）：
- `SkillsPage.kt:107-113` — `innerPadding + PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + 72.dp)`
- `ExploreMarketPage.kt:247` — `bottom = 32.dp`
- `ChatList.kt:310` — `PaddingValues(16.dp) + PaddingValues(bottom = 32.dp + innerPadding.calculateBottomPadding())`

### 3.4 范围问题（决策点 D5）

「设置页面」是指：

- (a) 只改 `SettingPage.kt`（设置首页）
- (b) 改设置首页 + 本次任务 1 涉及的两个页面（`SettingWebPage`、`SettingTriggersPage`）
- (c) 所有 18 个设置子页

推荐 **(b)**：与任务 1 同批改动，视觉一致，且避免出现"首页改了子页没改"的割裂。(c) 涉及 18 个文件，建议单独一次改动。

### 3.5 验收

```bash
cd ../KhatKit && ./gradlew assembleDebug
```

**人工检查**：设置首页（及选定范围的其他页）滚到底，末项与屏幕底边的距离 ≥ 顶栏到首项的距离；**MD3 与 Miuix 两种风格都要看**；`SettingWebPage` 额外确认 FAB 不遮末项文字。

---

## 任务 4 — Khromia 的 Dialog / EditDialog 加 Miuix 风格

### 4.1 现状核实（⚠️ 任务描述的前提与代码不符）

**Khromia 里没有任何 Miuix 代码，也没有 Miuix 依赖。** 全仓 `grep -i "miuix\|yukonga"` 对 `*.kt` / `*.kts` / `*.toml` / `*.md` / `*.xml` **零命中**。

而且最近那个 commit `1dbd595 "feat(components): Miuix 风格对话框与分段控件"` 的标题有误导性。看 `git show --stat 1dbd595`（3 文件 +377/−40）：

| 文件 | 实际改了什么 | 有 Miuix 吗 |
|---|---|---|
| `AnimatedAlertDialog.kt` (+157) | 纯 MD3 保真重构：`Surface` 外包 `Box(propagateMinConstraints)`、按槽位 padding token 替代散落 `Spacer(16.dp)`、引入 `ProvideContentColorTextStyle` 让 icon/title/text/button 各自拿到 `headlineSmall`/`bodyMedium`/`labelLarge`、有 icon 时标题居中、`Row(SpaceBetween)` 换成 `FlowRow` + `LocalLayoutDirection.flip()` | ❌ 零 `Miuix` 零 `yukonga` |
| `AnimatedDialogWindow.kt` (+12) | 性能修复：`AtomicBoolean` 保证 `window.attributes`（scrim / 窗口动画置零）只设一次而不是每次重组都设（`:118-129`） | ❌ |
| `SegmentedRow.kt` (+248, 新文件) | 真正的新功能：`SegmentedItem` / `SingleChoiceSegmentedRow` / `MultiChoiceSegmentedRow` / `SegmentedRowDefaults` | ❌ 纯 MD3 |

commit message 里"供设置页在 Miuix 下替换 MD3 SegmentedButton"指的是**为 Kedge 的 Miuix 分流提供依赖**，Miuix 分支本体在 `Kedge/components/KedgeSegmentedRow.kt:54`（`MiuixSingleChoiceSegmentedRow` 在 `:84`），不在 Khromia。

### 4.2 现有实现

`AnimatedAlertDialog` — `Khromia/khromia/src/main/java/heizige/kk/khromia/components/AnimatedAlertDialog.kt:38-78`

```
visible, onDismissRequest, confirmButton, modifier, dismissButton?, icon?, title?, text?,
shape = AlertDialogDefaults.shape, containerColor, iconContentColor, titleContentColor,
textContentColor, tonalElevation, properties = DialogProperties(), enablePredictiveBack = true
```

- 窗口：`AnimatedDialogWindow.kt:113` 用裸 `androidx.compose.ui.window.Dialog`；主体是手抄 MD3 `AlertDialogContent`，建在 `androidx.compose.material3.Surface` 上（`:101-106`），默认值取 `AlertDialogDefaults.*`。
- **不是** `BasicAlertDialog`，**不是** `AlertDialog`。
- 私有 token（`:211-216`）：`IconPadding`/`TitlePadding`/`TextPadding` = 16/16/24dp、`DialogPadding` = 24dp、按钮间距 8dp。

`EditDialog` + `EditFieldConfig` — `components/EditDialog.kt:54` / `:66-75`

```
EditFieldConfig(label, initialValue, placeholder, keyboardType, range, maxLength, onValidate)
EditDialog(visible, title, fields, onDismiss, onConfirm, modifier, confirmText?, dismissText?)
```

- `:76-226`。`:77` `if (!visible) return` —— **直接卸载，无退场动画**。
- 校验：数字解析 / `range` / `maxLength` / 自定义 `onValidate`，逐字段错误文案，`isAllValid` 门控确认按钮（`:216`）。
- 窗口：`FullscreenPopup`（Khromia 自有 `AbstractComposeView` 挂 decorView，`:139`），**和 `AnimatedAlertDialog` 不是同一套宿主**。
- 自绘 scrim `Color.Black@animateFloatAsState(0.6f)` + `bouncyClickable` 外部关闭（`:144-150`）；`PredictiveBackHandler` + `graphicsLayer` scale `1-0.1p` / alpha `1-0.3p`（`:152-171`）。
- MD3 依赖：`Surface(RoundedCornerShape(28.dp), tonalElevation = 6.dp)`、`OutlinedTextField(RoundedCornerShape(16.dp))`、`TextButton`、`Button(primary@0.87f)`、`ButtonDefaults.shapes()`、`MaterialTheme.typography.titleLarge`。
- 顺带发现：`components/SliderItem.kt:210-234` 用的是**原生** `androidx.compose.material3.AlertDialog`，绕开了 `AnimatedAlertDialog`——这是个漏网的 MD3 对话框，Miuix 下会露馅。

### 4.3 落点决策（决策点 D6 —— 这条最重要）

两条路，**强烈推荐 (A)**：

**(A) 在 Kedge 加 `KedgeEditDialog`，在 Kedge 侧做双风格分流** ✅ 推荐

理由：这就是 Kedge 现存的职责与既有先例。`Kedge/overlays/KedgeDialogs.kt` 已经在做 `KedgeDialog`（`:30`）/ `KedgeAlertDialog`（`:63`）/ `KedgeAlertDialogSlots`（`:124`）→ 委托 Khromia `AnimatedAlertDialog`，Miuix 分支走 `MiuixWindowDialog`（`KedgeDialogs.kt:25` 引入 `top.yukonga.miuix.kmp.window.Dialog`）。缺的只是 `EditDialog` 这一层分发。做法与 `KedgeOptionItem`（`KedgeOptionItem.kt:40-104`，MD3Exp → `KhromiaOptionItem`、Miuix → `KedgeCard` 行）**完全同构**，零新架构。

**(B) 让 Khromia 引入 Miuix 依赖** ❌ 不推荐

推翻 Khromia「零 Miuix 依赖」这一定位，把它变成第二个双风格库；Kedge 的分发层就失去意义；而且 khromia 当前 `minSdk 24`、无 Miuix 依赖，加 `top.yukonga.miuix.kmp:*` 会牵进 KMP 依赖链。

### 4.4 做法（按 A）

**第一步 · Kedge 新增 `KedgeEditDialog`**（新文件 `Kedge/kedge/src/main/java/heizige/kk/kedge/overlays/KedgeEditDialogs.kt`）

```kotlin
/** EditDialog 的双风格分发：MD3Exp 走 Khromia EditDialog，Miuix 走 Miuix 实现。 */
@Composable
fun KedgeEditDialog(
    visible: Boolean,
    title: String,
    fields: List<EditFieldConfig>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String? = null,
    dismissText: String? = null,
) {
    when (LocalKedgeStyle.current) {
        KedgeStyle.MD3Exp -> KhromiaEditDialog(
            visible = visible, title = title, fields = fields, onDismiss = onDismiss,
            onConfirm = onConfirm, modifier = modifier,
            confirmText = confirmText, dismissText = dismissText,
        )
        KedgeStyle.Miuix -> MiuixEditDialog(
            visible = visible, title = title, fields = fields, onDismiss = onDismiss,
            onConfirm = onConfirm, modifier = modifier,
            confirmText = confirmText, dismissText = dismissText,
        )
    }
}
```

- `EditFieldConfig` 从 Khromia 复用（`heizige.kk.khromia.components.EditFieldConfig`），**不要在 Kedge 里重新定义一份**，否则两边类型不通。
- `MiuixEditDialog` 私置于同一文件，实现要点：Miuix `Dialog`/`SuperDialog` 宿主、`MiuixTextField` 或 `SuperTextField`、Miuix `Button`/`TextButton`、Miuix `Text`、Miuix 圆角 token（**不要**沿用 `RoundedCornerShape(28.dp)` / `(16.dp)`）、scrim 与手势用 Miuix 自己的 motion。
- 校验逻辑**不要复制**：从 Khromia 把"数字/range/maxLength/onValidate 逐字段校验 + 错误文案"抽成一个纯函数（无 Composable 依赖），放在 Khromia 侧供两边共用。否则修 bug 要改两处。
- 顺手给 `AnimatedAlertDialog` 加一个 `KedgeAlertDialogSlots` 风格的 Miuix 分支（若 §4.4 范围过大，可拆成两次提交）。

**第二步 · Khromia 侧重构**（独立提交）

- 抽出 `validateEditFields(fields): Map<Int, String?>` 纯函数，`EditDialog.kt` 改为调用它。
- `SliderItem.kt:210-234` 的原生 `AlertDialog` 换成 `AnimatedAlertDialog`（修 §4.2 的漏网 MD3 对话框）。
- `AnimatedAlertDialog` / `EditDialog` 的 KDoc 补一句明确声明："本组件为纯 MD3 实现，Miuix 分支由 `heizige.kk.kedge.overlays.KedgeEditDialog` / `KedgeAlertDialog` 提供"，避免下次又有人往 Khromia 里塞 Miuix。

**第三步 · KhatKit 切换调用点**

- 全仓 `grep -rn "EditDialog"` 找到调用点，逐个改成 `KedgeEditDialog`。改之前先统计调用点数量（可能很多），数量大就单独一次提交。

**第四步 · Khromia 补一个 CHANGELOG.md**（当前没有，`docs/` 也没有）。Kedge 的 `CHANGELOG.md` 顶部停在 `0.1.1` 而 `build.gradle.kts` 已是 `0.1.2`，顺手补上。

### 4.5 验收

```bash
cd ../Khromia && ./gradlew :khromia:assembleDebug
cd ../Kedge && ./gradlew :kedge:assembleDebug
cd ../KhatKit && ./gradlew assembleDebug && ./gradlew lint
```

**人工检查**：`EditDialog` 在 MD3 与 Miuix 下各走一遍，**Miuix 下不出现 MD3 的 28dp/16dp 圆角、不出现 MD3 字体**（`MiuixMaterialThemeBridge` 会自动接管颜色与字号，形状不会，重点看形状）；校验失败时错误文案在两种风格下都在；`SliderItem` 的重置确认弹窗在 Miuix 下不再露 MD3；预测返回手势在两种风格下都跟手。

---

## 5. 提交协议

**每次改动完成后立刻提交并推送**（用户明确要求）。

### 5.1 仓库与分支

| 仓库 | remote | 分支 |
|---|---|---|
| `~/文档/Android/Project/KhatKit` | `ssh://git@ssh.github.com:443/KhatKit/Android.git` | `main` |
| `~/文档/Android/Project/Kedge` | `ssh://git@ssh.github.com:443/heizigelovecode/Kedge.git` | `main` |
| `~/文档/Android/Project/Khromia` | `git@github.com:heizigelovecode/Khromia.git` | `main` |

三边 `main` 都已与 `origin/main` 同步（ahead/behind 均为 0），工作区干净。

### 5.2 跨仓库提交顺序（必须按序）

改 Kedge 或 Khromia 时：

1. 先提交并推送 **Khromia**（若改了）。
2. 再提交并推送 **Kedge**（若改了）。
3. 最后提交并推送 **KhatKit**。

反过来会让 KhatKit 那一版在别人 clone 时构建失败（composite build 找不到新增 API）。**同一批改动里 KhatKit 的提交不能先推。**

### 5.3 提交粒度

- 一个任务一次提交；跨仓库的同一个功能，按上面的顺序拆成 2–3 次提交，commit message 里互相引用（`配合 Kedge 侧 <sha>`）。
- **不要把改动混进一次提交**：任务 3 的 padding 不要和任务 1 的组件迁移混在一起。
- 提交前必须确认 `git status --porcelain` 里没有无关文件（KhatKit 现在有一个未跟踪的 `docs/bridge-expansion-spec.md`，那是另一个任务的产物，**不要顺手带上**）。

### 5.4 commit message 风格

Conventional Commits，**中文正文**，与历史一致：

```
<type>(<scope>): <一句话中文说明>

<可选的换行 + 中文补充：为什么这么改 / 顺手修了什么>
```

type 取值：`feat` / `fix` / `style` / `refactor` / `docs` / `chore`。
scope 用模块或功能名：KhatKit 常见 `settings` / `chat` / `input` / `drawer` / `ui`；Kedge 见 `overlay` / `components`；Khromia 见 `khromia` / `components`。

历史样例（照这个语气写）：

- `style(settings): 关于页改用 KernelSU 结构，Tooltip 接入 Miuix 分流`
- `refactor(settings): 列表卡片样式改回由 CompositionLocal 下发`
- `feat(components): KedgeButton 新增 miuixColors，补上 Miuix 侧无法单独调色的洞`
- `fix(overlay): KedgeOverlayScaffold 在 Miuix 下补弹层宿主，长按下拉不再失效`
- `fix(khromia): material3 对齐 1.5.0-alpha29，修 FancySlider 运行期崩溃`

### 5.5 push

```bash
git -C <repo> push origin main
```

推送前跑一遍对应任务的验收命令。**构建不过不要推。**

---

## 6. 决策点（需要人拍板，不要自行决定）

- **D1（任务 1）**：只迁 `SettingWebPage` + `SettingTriggersPage`（其余页面仍走自研 `CardGroup`，会与迁移后的两页视觉不一致），还是连 `CardGroup` 一起迁（影响 8+ 文件，含 `SettingPreferencesThemePage`、`SettingThemePage`、`AssistantDetailPage`、`GreetingPage`）？
- **D2（任务 2）**：字号统一后，MD3 侧用 28sp（`displayTitle()`）偏大。是否在 `KedgeTextStyles` 新增 `topBarTitle()`（MD3 → `titleLarge`，Miuix → `title3`）？侧边栏的标题是否一并对齐到同一 token（会让抽屉标题从 22sp 变化）？
- **D3（任务 2）**：actions 图标统一成哪种？全部无动画硬切（= 侧边栏现状），还是两边都做成交叉淡化（= ChatPage 现状，但侧边栏要新增行为）？
- **D4（任务 2）**：是否修 `ChatPage.kt:849` 的 `previewMode || searchVisible` 退场截断？这是行为修复，严格说超出"对齐动画"的范围。
- **D5（任务 3）**：「设置页面」的范围取 (a) 首页 / (b) 首页 + 任务 1 的两页（推荐）/ (c) 全部 18 个子页。
- **D6（任务 4）**：落点取 (A) Kedge 加 `KedgeEditDialog`（推荐，符合 Kedge = 分发层的定位）还是 (B) 让 Khromia 引入 Miuix 依赖？
- **D7（任务 4）**：`EditDialog` 的 `if (!visible) return` 导致无退场动画，要不要顺手补？补了会与 `AnimatedAlertDialog` 的行为对齐，但不补就是两种行为。