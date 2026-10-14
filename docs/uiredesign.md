CHatPage Topappbar(miuix)点击没有md3的弹窗
侧边栏 新建文件夹按钮(md3)与topappbar之间加上适当边距
chatpage 底部输入框没有光标
chatpage 必读输入框上滑的拉起editor动画不对(editor来自kodehead项目在khathit同级目录下), 上滑收拾改成打开editor 动画和kodehead的帖子详情页面的FLB的上滑手势一样 系统返回无效
自动化操作toast 软件内效果 和软件外效果需要优化, tosat来自imagetoolbox https://github.com/T8RIN/ImageToolbox 动画 颜色 样式一模一样 软件内的khat已经有自实现现在的问题是toast的入场是弹簧动画会溢出悬浮窗大小从而显示不全,软件内的应当用hkromia的 修改khromia的效果和imagettolbox的一样 现在的我不知道是怎么实现的
bottomsheet(miuix)内容距离边界边距过大
关于页面md3ui下不应该有动态流光背景

---

# 修复记账（团队集成收口，2026-10-14）

> 标题日期已按外部权威时间核定；原先写的 `2026-10-07` 是当时本机挂钟的读数，
> 见文末「门禁与产物」一节的**勘误**。

> ## ⚠️ 验证分工声明
>
> **本页 7 项的视觉/功能验证由用户本人完成**——请自行安装本次产出的 debug APK 走查
> （路径见本页末尾「门禁与产物」）。
> **团队未做真机测试**：不安装 APK、不 screencap、不录屏、不在设备上走查界面。
>
> 团队侧实际做过的验证只有三类，本页每条结论都限定在这三类之内：
> 1. **构建/测试门禁**：三仓改动共存下四条命令全部 `--rerun` 真实执行、exit 0
>    （含 `:app:testDebugUnitTest` 118 suites / 961 tests / 0 failures）——见本页末尾。
> 2. **源码级复算**：逐调用点 dp 账、Miuix 库与 MD3/Khromia 两层容器的内缩叠加计算、
>    手势阈值与 KodeHeap 上游逐式对照。
> 3. **独立评审与抽查**：每项改动另有独立评审任务复算关键断言（不改代码）。
>
> ⇒ 凡本页写「已验证」的，都只意味着**上述三类通过**；「观感对不对、动画顺不顺、
> 手势跟不跟手」一律属于**未验证**，只能由用户实机确认。
>
> **计数口径**：`--rerun` 表示强制真实执行，不复用缓存产物（不加会在树损坏时静默 exit 0）。
> 基线 = **118 suites / 961 tests / 0 failures / 0 errors / 0 skipped**（由
> `app/build/test-results/testDebugUnitTest/*.xml` 聚合得出；合同里写的「954 例」是文本误差）。
>
> **切换 UI 风格的统一入口**：设置 → 偏好设置 → 界面 → **界面风格**
> （`app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingPreferencesUIPage.kt:403`，
> `R.string.greeting_settings_ui_style`），在 **Material 3 Expressive** 与 **Miuix** 之间切换，即时生效。
> 下面每条「用户自查路径」里的「切 Miuix / 切 MD3」都指这一步。

## 汇总表

| # | 缺陷 | 状态 | 主要改动 |
|---|---|---|---|
| 1 | ChatPage Miuix 顶栏标题点击无 MD3 弹窗 | **已修** | `KedgeMiuixMorphingTitleBar.kt`、`ChatPage.kt` |
| 2 | 侧边栏新建文件夹按钮与顶栏无间距 | **已修** | `ChatDrawer.kt` |
| 3 | chatpage 底部输入框没有光标 | **已修** | `ChatInput.kt` |
| 4 | 上滑拉起 editor 动画不对 + 系统返回无效 | **已修** | `AiChatGenieEffect.kt`、`ChatInput.kt`、`RouteActivity.kt` |
| 5 | 自动化操作 toast 软件内被裁切 / 软件外入场可能被裁切 | **已修** | `AutomationInAppHost.kt`、`AutomationOverlayService.kt`、Khromia `Toast.kt` / `ToastData.kt` / `ColorHelp.kt` / 新增 `IconShapeContainer.kt` |
| 6 | bottomsheet(miuix) 内容距边界边距过大 | **已修** | `KedgeSheets.kt`、`GreetingPage.kt` |
| 7 | 关于页面 MD3 有动态流光背景 | **已修** | `SettingAboutPage.kt` |

「已修」= 代码改动落地 + 源码级复算 + 评审通过 + 门禁通过；**不等于**视觉/功能已被验证。
全部 `git status` 见本页末尾；本次**未执行任何 git commit / push**。

---

## #1 ChatPage 顶栏（Miuix）点击没有 MD3 的弹窗 —— 已修

**根因（两条，缺一不可）**

1. `ChatPage.TopBar` 在 `if (isMiuix) { ... return }` 之前没有任何标题点击语义；Miuix 的
   `TopAppBar.title` 只是 `String`，没有 MD3 `titleContent` 那样的可点槽位 —— 所以点击
   标题符号上不存在回调。
2. 重命名弹窗 `titleState.EditStateContent { … }` 原先写在 `TopBar` 函数**最末尾**，
   位于 `if (isMiuix) { … return }` **之后**：Miuix 分支早退 ⇒ 这条 dialog 在 Miuix 下
   **永不参与组合**，长按改名即使改了 state 也不会弹窗。

**改动**

- `app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/miuix/KedgeMiuixMorphingTitleBar.kt`
  - `:146-147` 新增参数 `onTitleClick: (() -> Unit)? = null` / `onTitleLongClick: (() -> Unit)? = null`
    （为 null 时不叠加任何层，行为与改前逐像素一致）。
  - `:195-199` 命中层横向 inset 取 **逐侧并集**：
    `WindowInsets.displayCutout.only(Horizontal).union(WindowInsets.navigationBars.only(Horizontal))`
    —— 复刻 Miuix `TopAppBar.kt:769-778` 两次链式 `windowInsetsPadding` 的合成语义
    （Compose 的 `consumedInsets = ancestor.union(insets)`，`UnionInsets` 逐侧 `maxOf`）。
  - `:223-224` / `:305` / `:313` 用 `onSizeChanged` 实测导航槽/尾部操作槽内容宽（px）。
  - `:230-232` 折叠态判定 `smallTitleVisible = collapsedFraction * 3f >= 1f`（与 miuix-ui
    `TopAppBarLayout` 内部阈值完全一致，保证「小标题可见」与「大标题可见」互补、无空窗）。
  - `:417-460` 非搜索态（进度 `p <= 0.001f`）下叠两段**互补**的透明命中矩形：
    ① 折叠行中间段（`y` 从 `topInset` 起算，`x ∈ [startInsetTotal + NavigationIconPadding + navW,
    宽 − endInsetTotal − ActionIconPadding − actW]`）；② 大标题整段（仅 `!smallTitleVisible` 时存在，
    `y` 从 `topInset + CollapsedHeight` 起算）。两段都挂 `combinedClickable(indication = null)`。
  - 搜索展开态（`p > 0.001f`）两段都不存在。
- `app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatPage.kt`
  - `:859` 提示文案提到分支外共用；`:865-903` `titleState.EditStateContent { … AppAlertDialog }`
    **上移到 `if (isMiuix) { … }`（:905）之前**，Miuix 下才会组合出弹窗。
  - `:931-938` Miuix 分支传 `onTitleClick = onModelClick`（点标题 → 模型选择 sheet，与 MD3 同义）、
    `onTitleLongClick = { 有消息才 titleState.open(标题)，无消息只 Toast 提示 }`。

**关键规格数值**

- 命中层只在**非搜索态**存在；折叠态命中区纵向自状态栏顶 `topInset` 起算（覆盖滚动后可见的小标题那一行），
  横向边界扣掉 `16dp` 槽内边距 + 槽实测内容宽。
- 长按改名门控：`conversation.messageNodes.isNotEmpty()`；空会话只弹 `chat_page_edit_title_warning` Toast。
- 点击标题的 MD3 对位行为：`onModelClick` → `modelListState.open()` → `ModelListSheet`（MD3 侧由
  `titleContent` 的 `combinedClickable` 触发，见同一文件 MD3 分支）。

**验证方式与结论**

- `:app:compileDebugKotlin` exit 0（含本文件）、`:app:testDebugUnitTest` 961 例 0 失败、`:app:assembleDebug` exit 0。
- 源码级复算：两段命中矩形的 `x/y` 区间与 Miuix 原版槽位布局（`TopAppBar` 的
  `navigationIcon` / `actionIcons` 两个 `layoutId`）逐项对齐；两态阈值互补，无「看不见却能点」的空带。
- **观感/手感未验证**（团队不跑真机）。

**用户自查路径（Miuix 风格）**

1. 切 **Miuix**，进任意**聊天页**（会话里要有至少 1 条消息）。
2. **未滚动**（大标题展开）：点顶栏大标题 → 应弹出**模型选择 sheet**（与 MD3 相同）。
3. **把 Miuix 顶栏折叠后（滚动聊天列表，直到大标题收敛成折叠行里的小标题）再点那行小标题**
   → 同样应弹出模型选择 sheet。这是本次新增命中区 ① 的关键场景，请重点确认该处**可点**、
   且左右两侧的**返回/菜单图标与右侧操作图标仍各自可点**（不被透明层抢走）。
4. **长按**标题（两态都可）→ 有消息时应弹出**重命名标题弹窗**；空会话应只弹一行提示 Toast。
5. 再切回 **MD3** 重复 2/4 步，确认两风格行为一致。

**残余问题（未验证 / 未跟进）**

1. **首帧窄窗**：导航槽/尾部操作槽的实测宽在**首帧为 0**（`onSizeChanged` 尚未回调），那**一帧**里命中区
   会临时向两侧扩展到图标槽上。人手点不了这么快，但**自动化点击脚本要注意**。
2. **非竖屏常见摆放**：命中区 ②（大标题整段）与既有搜索输入框仍沿用**改动前的单侧 inset 口径**
   （左取 `displayCutout.left`、右取 `navigationBars.right`）。在「侧边刘海 / 侧边导航栏」配置下这单侧口径
   不完整；② 纵向 `y ≥ topInset + CollapsedHeight`（约 52dp 以下）碰不到折叠行图标，搜索输入框那处偏移是
   **本次工作之前就存在**的。命中区 ① 已改成逐侧并集，不受此影响。
3. **`defaultWindowInsetsPadding = false`** 这条**非生产路径**的横向口径不统一（全仓无调用者，仅记录）。
4. **横屏某旋转 + 侧边刘海/侧边导航栏**下的实际命中边界**未实机验证**。

---

## #2 侧边栏「新建文件夹」按钮与顶栏无间距 —— 已修

**根因**：抽屉顶栏是**覆盖式**（`KedgeOverlayScaffold` 的 `contentInsetTop = 0`，列表用 top spacer 顶开栏高）。
常态下 `UpdateCard` 不渲染、`BackupReminderCard` 早退，**文件夹组就是 header 里第一个可见子项**，
`FolderSection` 的 modifier 原先只有 `padding(bottom = 8.dp)` ⇒ 组顶直接贴着顶栏最后一个像素。

**改动**

- `app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatDrawer.kt`
  - `:561` `FolderSection(..., modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))`（原先仅 `bottom = 8.dp`）。
  - `:532-547` 注释写明：**不能**用放大 `ConversationList` 的 `topSpacerHeight` 来做这件事
    （那是列表内首部 Spacer，会连带推下 header 里所有子项并改变滚动几何）；这里只作用于文件夹组自身。
  - `:957-961` `FolderSection` KDoc 补充「组顶/组底各 8dp，组内零间距只约束组内」。

**关键规格数值**

- 组顶 **8dp**，与抽屉既有节奏一致（外层 `Column` 的 `spacedBy(8.dp)`、组底原有 8dp）。
- **两种风格共用同一条 modifier**：抽屉顶栏两风格都是同一个裸 MD3 `TopAppBar`（只有颜色分流），
  栏高都是 `DrawerTopBarHeight(64dp) + statusBarTop`，故 Miuix 风格抽屉同样多出这 8dp，间距一致。

**验证方式与结论**

- 门禁三条 KhatKit 命令 exit 0；dp 值与既有间距节奏一致（源码级复算）。
- **实际观感未验证**（团队不跑真机）。

**用户自查路径**

1. 打开**侧边栏抽屉**（聊天页左侧滑出 / 顶栏菜单图标）。
2. 确认顶部第一个可见块（文件夹组，无文件夹时就是「新建文件夹」那一行）与顶栏之间**有约 8dp 间距**。
3. 分别在 **MD3** 与 **Miuix** 风格下各看一次（两风格都 +8dp，是有意统一）。
4. 再确认组**内**（文件夹行之间）仍是紧凑的 6dp，没有被这次 padding 破坏。

**残余/说明**：Miuix 风格也一起变（共用 modifier）；若将来只想改 MD3，把 `top` 拆进 MD3 分支即可
（已在注释里写明）。

---

## #3 chatpage 底部输入框没有光标 —— 已修

**根因**：底部输入框在 Miuix 风格下走 Miuix `TextField`。Miuix 的 `cursorBrush` **默认值**是
`SolidColor(colors.borderColor)`（miuix-ui 0.9.3 `basic/TextField.kt:101`），而该处为了透明外观把
`borderColor` 显式设成了 `Color.Transparent` ⇒ 光标 brush 解析成 `Transparent`，聚焦后**看不见光标**。

**改动**

- `app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ai/ChatInput.kt`
  - `:1118` 显式传 `cursorBrush = SolidColor(MiuixTheme.colorScheme.primary)`。
  - `:1113-1116` 注释记录根因与上游行号（`miuix-ui basic/TextField.kt:101`）。
  - MD3 侧输入框原本已有 `cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)`（`:1456`），未改。

**关键规格数值**：光标颜色 = `MiuixTheme.colorScheme.primary`（与聚焦描边同色域）；
`borderColor` 仍保持 `Color.Transparent`（外观不变，只补光标）。

**验证方式与结论**：`:app:compileDebugKotlin` exit 0；根因有库源码行号支撑（评审已核）。
**光标是否可见属观感，未验证。**

**用户自查路径**

1. 切 **Miuix**，进**聊天页**，点底部输入框使其获得焦点。
2. 光标应在正文行内**可见**并闪烁（改前完全不可见）。
3. 切 **MD3** 确认行为一致（MD3 侧本来就是好的）。

**残余问题**：无已知残余；这也是「切风格后行为突变」类问题的一个样例，建议同屏对照两风格。

---

## #4 chatpage 输入框上滑拉起 editor：动画/手势对齐 KodeHeap 的 FLB + 系统返回 —— 已修

**根因（三块）**

1. **手势模型不对**：原实现是「面板全开（`progress >= 0.999`）后继续上滑累计 `overscrollPx` 到 110dp 阈值」
   才进入全屏编辑，且由 `Modifier.draggable`（Vertical）驱动 —— 与 KodeHeap 帖子详情页 FLB
   「收起态一次上滑即拉起」的模型不同。
2. **落定动画不对**：原用 `spring(dampingRatio = NoBouncy, stiffness = MediumLow)`；KodeHeap FLB 用的是
   `tween(时长按距离缩放) + CubicBezierEasing(0.16, 0.82, 0.18, 1)`。
3. **系统返回无效**：全屏 editor 是**同一 window 内的页内浮层**，它的返回处理与 `NavDisplay` 注册在同一个
   页面 dispatcher 上；只依赖「后注册者优先」的隐含顺序 ⇒ 返回会穿透到导航栈把 ChatPage pop 掉。

**改动**

- `app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ai/AiChatGenieEffect.kt` —— 新增 5 个纯函数，
  逐式对齐 KodeHeap `Post.kt`：
  - `:276` `aiWindowSettleDurationMillis(start, target, full = 760ms, min = 90ms)`：满行程固定 760ms、
    按实际距离**线性缩放**，clamp 到 `[90ms, 760ms]`；距离 ≤ 0.001 返回 0ms
    （KodeHeap `Post.kt:3181-3193`）。
  - `:294` `aiWindowMorphProgress(revealProgress, morphStart = 0.88f)`：揭示进度过 0.88 后才开始形变，
    末段 `smoothstep`（`Post.kt:3209-3217`）。
  - `:308` `aiWindowCornerRadiusPx(...)`：起始半径 = FLB 较短边一半（圆形），末段 morph 到屏幕物理圆角
    （`Post.kt:3196-3206`）。
  - `:324` `composerAiRevealProgressFromDrag(rawY, commitDistance, maxOvershoot = 1.12f)`：线性到 1 后
    橡皮筋过冲，上限 1.12（`Post.kt:3132-3143`）。
  - `:341` `composerAiRevealCommitOnRelease(progress, commitThreshold = 0.35f)`：松手进度 ≥ 0.35 即提交
    （过冲 >1 也算提交，`Post.kt:3223-3226`）。
- `app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ai/ChatInput.kt`
  - `:413-500` 用 `pointerInput + awaitEachGesture`（`PointerEventPass.Initial` 抢在按钮/文本框之前并
    `consume()`）替换 `draggable`：过 `touchSlop`（`hypot`）后**锁轴** —— 竖向驱动 FLB、横向全部丢弃；
    上滑用 `composerAiRevealProgressFromDrag` 映射进度，下滑时若手风琴面板已展开则跟手收起（保留原行为）。
  - `:1665-1668` `flbSettleSpec(start, target) = tween(aiWindowSettleDurationMillis(start, target),
    CubicBezierEasing(0.16f, 0.82f, 0.18f, 1f))`；替换所有原 `spring(NoBouncy, MediumLow)` 调用
    （提示：`:316` / `:321` / `:498` / `:681` / `:711` / `:1326` 等）。
  - `:273` 揭示行程 `editorRevealDistancePx = 160.dp`（→ 提交阈值 0.35 ⇒ 约 **56dp** 上滑即生效）。
  - `:276-290` `settleEditor(open)`：单 job 收敛到 0/1，关闭时 `editorOpen = false`。
  - `:656-664` 输入胶囊 `onGloballyPositioned { localToWindow(...) }` 实测窗口坐标写入 `flbBounds`
    （对齐 KodeHeap `Post.kt:3925` 的 `boundsInRoot()`），`:1367-1382` 作为 genie 的 `collapsedBounds`
    （不再硬编码 60×56dp）。
  - `:294-305` `PageBackInterceptor.push(token) { if (editorOpen) { settleEditor(false); true } else false }`
    （`DisposableEffect` 配 `onDispose { pop(token) }`）。
  - editor 载体改为 `FullscreenPopup`（Khromia，MATCH_PARENT 的 AbstractComposeView 直接挂 Activity
    decorView，同一 window、无独立 dialog window）。
- `app/src/main/java/heizige/kk/khatkit/app/RouteActivity.kt`
  - `:351-356` `NavDisplay.onBack` 改为**先问拦截器**：`if (!PageBackInterceptor.intercept()) navViewModel.popBackStack()`。
  - `:861` 新增 `internal object PageBackInterceptor`（`push/pop/intercept`，`intercept()` 从最新登记者往回问，
    任一消费即返回 true；无登记时返回 false ⇒ 不改变原行为）。

**关键规格数值（对齐 KodeHeap FLB）**

| 项 | 数值 |
|---|---|
| 落定时长 | `760ms × 行程`，clamp `[90ms, 760ms]`；距离 ≤ 0.001 → 0ms |
| 落定曲线 | `CubicBezierEasing(0.16, 0.82, 0.18, 1)`（+ `tween`，不再是 spring） |
| 圆角 morph 起点 | 揭示进度 `0.88`，smoothstep 到屏幕物理圆角 |
| 上滑揭示行程 | `160dp`（线性到 1，之后橡皮筋过冲到 1.12） |
| 松手提交阈值 | `0.35`（≈ 56dp 上滑） |
| 锁轴 | `hypot(dx,dy) > touchSlop` 后 `abs(dy) >= abs(dx)` 走竖向，否则横向丢弃 |
| 返回 | 页内浮层已登记且打开时**先关编辑器**，不 pop 导航栈 |

**验证方式与结论**

- `:app:compileDebugKotlin` exit 0、`:app:testDebugUnitTest` 961/0、`:app:assembleDebug` exit 0。
- 源码级逐式对照：上述 5 个函数与 KodeHeap `Post.kt` 对应函数公式一致（评审已在纯函数层面复核）。
- **手势跟手性、动画观感、系统返回手感一律未验证**（团队不跑真机）——这是本页需求「视觉验证由用户完成」的核心项。

**用户自查路径**

1. 进**聊天页**（列表里要有历史消息，方便上滑；**两种风格都试**，Miuix 与 MD3 都应工作）。
2. **收起态上滑**：在底部输入胶囊上**向上滑动**（约 160dp 行程 = 拉起；滑到大约 1/3 行程松手即应提交）
   → 应打开**全屏编辑器**，且动画为「曲线型落定 + 圆角从胶囊圆角 morph 到屏幕圆角」，
   **不再是旧版的弹性弹簧**；中途松手若不到阈值应**收回**到胶囊。
3. **系统返回**：编辑器打开时按**系统返回手势/返回键** → **只关闭编辑器**，**不应**把聊天页 pop 掉；
   连按两次返回才应退出聊天页。
4. **侧滑跟手**：编辑器打开过程中**手指横向拖动**应被忽略（锁轴为竖向），不应抖成横向位移。
5. **滑动冲突**：确认上滑手势不影响在输入框内**移动光标/选词**（竖向拖动超过 `touchSlop` 才被输入胶囊接管）。

**残余问题（未验证 / 未跟进）**

1. **无单元测试**：新增的 5 个纯函数（`aiWindowSettleDurationMillis` / `aiWindowMorphProgress` /
   `aiWindowCornerRadiusPx` / `composerAiRevealProgressFromDrag` / `composerAiRevealCommitOnRelease`）与
   `PageBackInterceptor` 都是 `internal`，本次**未补单元测试**（`app/src/test` 下无引用）——它们本可纯函数测试。
2. **屏幕物理圆角取不到时退化为 0**：`rememberScreenCornerRadiusPx()` 只在 **API 31+** 且窗口 insets 带圆角时有值，
   否则按 KodeHeap 同款默认值 0 处理（老设备/模拟器上末段 morph 终点会是直角，需实机对照）。
3. **手势竞态未实机验证**：`PointerEventPass.Initial` 抢事件与文本框/按钮的手势优先级、以及「下滑收起手风琴」
   与「上滑拉起编辑器」在临界点的判定，均只有源码级推理，**未做真机对照**。

---

## #5 自动化操作 toast：软件内被悬浮窗裁切、软件外入场同样可能被裁切 —— 已修

**根因**

1. **软件内**：自建 `Popup`（`PopupProperties(focusable = false)`，**WRAP_CONTENT 大小的独立 window**）承载看板，
   入场 `slideInVertically(initialOffsetY = { it / 2 })` 的位移是**节点高度的一半**，超出这个窗口的内容被窗口边界**硬裁**。
2. **软件外**：`AutomationOverlayService` 的悬浮窗**只能包住看板**（做成全屏会在 Android 12+ 触发触摸遮挡，
   `getTouchOcclusionMode` 对 `TYPE_APPLICATION_OVERLAY` 返回 `USE_OPACITY`），窗口底边就是硬裁切边界；
   旧实现卡片下方只有 48dp 留白，而 `it / 2` 在 411dp 宽屏上 ≈ 107dp ⇒ 入场时卡片底部被裁约 59dp。
3. 两条路径各自**硬编码副本**的卡片规格，与 Khromia/上游 ImageToolbox 的 toast 规格会漂移。

**改动（三方统一到同一份规格）**

- **Khromia（规格来源）**
  - `khromia/src/main/java/heizige/kk/khromia/components/Toast.kt`
    - `:86-216` 新增 `object ToastDefaults`：`Alpha = 0.95f`（上游 `:271`）、
      `BottomScreenFraction = 0.2f`（`:176`）、`MaxWidthScreenFraction = 0.7f`（`:260`）、
      `MinHeight = 48.dp`（`:259`）、`ShadowElevation = 6.dp`（`:266-269`）、
      `TopPadding = 24.dp` / `SidePadding = 12.dp`（`:177-179`）、`ContentPadding = 16.dp`（`:275`）、
      `textStyle = MaterialTheme.typography.bodySmall`（`:294`）、
      `enterTransition/exitTransition`（`:117-152`，参数化 `slideOffsetY`，缺省即上游 `it / 2`）、
      `screenSizeMin()`（`:212-215`）、`cardContainerColor/cardContentColor/iconContainerColor`（`:178-201`）。
    - `:226-262` 新增 `fun ToastCardContainer(...)`：形状 `AutoCornersShape(32.dp)`、`padding(bottom = 屏短边 × 0.2f)`
      + `TopPadding 24dp` + `SidePadding 12dp`、`imePadding().systemBarsPadding()`、`heightIn(min = 48.dp)`、
      `widthIn(max = 屏短边 × 0.7f)`、`graphicsLayer { shadowElevation = 6.dp; ambient/spot = Black; clip = true }`、`alpha(0.95f)`。
    - `:277-294` 图标改为套一层 `IconShapeContainer`（squircle 底片 + 4dp 内边距 + 24dp 图标 + 0.65dp 阴影 +
      混色底 + 卡片内容色）**之后**才是 `Spacer(Modifier.width(8.dp))`（对齐上游 `ToastHost.kt:279-292`）。
  - `khromia/src/main/java/heizige/kk/khromia/data/ToastData.kt:59-68`（常量在 `:61` / `:64` / `:67`）
    新增 `object ToastDuration`：`Short = 3_500L` / `Medium = 5_000L` / `Long = 6_500L`，
    逐项对齐上游 `ToastHost.kt:442-446`（commit `a084948d441e2f61823df6b6a6a511a6cd271f2c`）。
  - `khromia/src/main/java/heizige/kk/khromia/data/ColorHelp.kt:47-50` 新增 `fun Color.blend(color, fraction = 0.2f)`，
    复现上游 `ToastHost.kt:281-283` 的 `containerColor.blend(secondary, 0.5f).blend(primaryContainer, 0.05f)`
    （`:199-201` 使用）。
  - **新增** `khromia/src/main/java/heizige/kk/khromia/components/IconShapeContainer.kt`：`IconShape(shape, padding = 4.dp,
    iconSize = 24.dp)` + `IconShapeDefaults`（默认 squircle 底片，对齐上游 `IconShape.kt:50-53,73` 与
    `IconShapeContainer.kt:131` 的 `autoShadowElevation = 0.65.dp`）。
- **软件内**：`app/src/main/java/heizige/kk/khatkit/app/feature/automation/AutomationInAppHost.kt`
  - `:55` 由自建 `Popup` 改为 **`FullscreenPopup(passThroughTouches = true)`**（挂 Activity decorView 的全屏宿主，
    同 window、**无独立窗口边界** ⇒ 入场动画不再被裁）；`:59-60` 用 `ToastDefaults.enter/exit`；
    `:66` 卡片改用 `ToastCardContainer`（不再自建 `KedgeSurface` + 手写 spring 动画）。
  - 保留「看板不排队进 toast 队列」的设计（常驻项会永久占住单槽队列，且全局 dismiss 会误伤他人），
    状态仍由 `AutomationBus` 实时驱动 —— KDoc `:28-41` 写明。
- **软件外**：`app/src/main/java/heizige/kk/khatkit/app/feature/automation/AutomationOverlayService.kt`
  - `:133` `HostRoom = 16.dp`（原 `ToastShadowRoom`）：窗口只包看板，上/左/右留 16dp 给 `scaleIn` 弹簧
    （`dampingRatio 0.65`，约 6.8% 过冲）与 6dp 阴影；**底部不留白**，窗口底边贴屏幕物理底部。
  - `:557-563` 新增 `bottomRoomPx = ToastDefaults.screenSizeMin() * BottomScreenFraction`（≈ 82dp @411dp）
    与 `slideOffset = { fullHeight -> minOf(half, bottomRoomPx) }`：**滑位移被 clamp 到这块留白内** ⇒
    位移 ≤ 留白 ≤ 卡片下方窗口内实际空闲，入场/退场全程卡片都不越过窗口底边。
  - `:570-571` 进入/退场改为 `ToastDefaults.enterTransition(slideOffset)` / `exitTransition(slideOffset)`
    （缺省即上游 `it / 2`；只有这条非全屏窗口路径传 clamp 版本）。
  - `:636-690` 看板容器改用 `ToastCardContainer`（`:668`）+ `ToastDefaults.ContentPadding`（`:681`）+
    `ToastDefaults.textStyle`（`:690`），本文件**不再硬编码**卡片视觉。

**关键规格数值（= 上游 ImageToolbox / Khromia toast 规格）**

| 项 | 数值 |
|---|---|
| 卡片透明度 | `0.95` |
| 卡片形状 | `AutoCornersShape(32.dp)` |
| 最小高度 / 最大宽度 | `48.dp` / 屏短边 × `0.7f` |
| 阴影 | `6.dp`，`ambient/spot = Color.Black`，`clip = true` |
| 宿主留白 | 底部 屏短边 × `0.2f`；顶部 `24.dp`；左右 `12.dp`；`imePadding + systemBarsPadding` |
| 内容内边距 / 文字 | `16.dp` / `MaterialTheme.typography.bodySmall` |
| 入场 | `fadeIn(tween(300)) + scaleIn(spring(0.65f, MediumLow), 底部中点) + slideInVertically(spring(StiffnessHigh)) { it / 2 }` |
| 退场 | `fadeOut(tween(250)) + slideOutVertically(tween(500)) { it / 2 } + scaleOut(spring(MediumBouncy, MediumLow), 底部中点)` |
| 图标底片 | squircle + `4.dp` 内边距 + `24.dp` 图标 + `0.65.dp` 阴影，图标后再 `8.dp` |
| 普通 toast 时长 | `Short 3500ms` / `Medium 5000ms` / `Long 6500ms`（无障碍可延长） |

**⚠️ 两处会影响你日常使用的行为变化（请重点确认）**

1. **Khromia 普通 toast 的显示时长由 1200ms 变为 3500 / 5000 / 6500ms**：`ToastDuration` 改成上游数值后，
   原先 1200ms 的默认时长不复存在；**全仓显式传时长的调用点 0 处**，所以这是**全局行为变化**（所有
   普通 toast 都变长了）。若觉得太长，只能改 Khromia 常量或给调用点显式传时长。
2. **软件内自动化看板现在与软件外看板共用同一份卡片规格**（`ToastCardContainer`）：外观（形状/透明度/宽度上限/
   内边距/文字样式）会与改前有差异，这是「与 Khromia/ImageToolbox 规格一致」的必然结果。

**验证方式与结论**

- 门禁：三条 KhatKit 命令 exit 0（`AutomationInAppHost.kt` / `AutomationOverlayService.kt` 参与编译、打包成功）、
  Kedge 编译 exit 0（Khromia 为 composite build 成员，参与 KhatKit 的 `:app:*` 与 Kedge 的 `:kedge:*` 构建）。
- 源码级复算：卡片规格、进入/退场动画、图标底片与 `8.dp` 间距逐项对齐上游 `ToastHost.kt`（评审已抽查上游 commit
  `a084948d` 的 `ToastHost.kt:442-446` 等行确认为真）；窗口裁切问题按几何不等式证明（`slideOffset ≤ bottomRoom ≤ 卡片下方窗口内空闲`）。
- **裁切是否真的消失、动画观感是否与 ImageToolbox 一致，属未验证**（只能实机走查）。

**用户自查路径**

1. **软件内（应用前台）**：切到 KhatKit 前台，让一张卡片调用
   **`ui.automationStatus(label, detail)`**（Lua/JS 卡片 API；实现 `khatkit/src/main/java/heizige/kk/khatkit/bridge/Bridges.kt:398`
   → `khatkit/src/main/java/heizige/kk/khatkit/ui/UiBridgeHost.kt:303`；文档
   `docs/script-api-reference.md:298` / `docs/lua-card-development.md:583`）。
   例：`ui.automationStatus("等待弹窗出现", "3s")`，隔一两秒再调一次换 label。
   → 应用内应弹出**贴底居中的 toast 卡片**：确认**入场动画全程卡片完整可见、没有被裁掉**
   （改前 `it / 2` 的位移会被自建 Popup 窗口裁），且点击卡片能隐藏提示（不取消脚本）。
2. **软件外（应用退到后台 / 悬浮窗权限路径）**：同一次自动化运行中把应用退到后台（或直接后台触发脚本）
   → 屏幕上出现**系统悬浮看板**：确认**入场与退场全程卡片完整、不贴屏幕底边被裁**，
   点击可隐藏，审批态（允许/拒绝）按钮可用且不取消脚本。
3. **两种卡片外观**：分别用**带图标**与**不带图标**的 `ui.automationStatus`/toast 调用各看一次
   —— 带图标时应能看到 squircle 底片 + 图标 + 8dp 间距（与 ImageToolbox 一致）。
4. **时长**：随便触发一个**普通 toast**（例如空会话里长按聊天标题那条提示），
   确认它现在**明显更久**（约 3.5s 起）；无无障碍延长的情况下这是新基线。
5. **对照 Khromia 原生 toast**：任意 MD3 页面里触发一个普通 Khromia toast，与自动化看板**并排看**
   形状/透明度/内边距/文字是否一致。

**残余问题（未验证 / 未跟进 / 已知例外）**

1. **无法做 ImageToolbox 字节级比对**：上游 ImageToolbox 未在本机 checkout，本次只能按其 **commit
   `a084948d441e2f61823df6b6a6a511a6cd271f2c`** 的源码逐项对齐（形状/尺寸/透明度/阴影/留白/动画曲线/时长），
   **不是**渲染结果比对；上游的 `container()`/`autoElevatedBorder` 等渲染分支与 Khromia 的实现路径不同。
2. **上游 `enableSwipes`（横向拖动消除 toast）未移植** —— **未跟进**：本页需求未要求，属交互项，留给你决定是否要。
3. **`0.65dp` 图标底片阴影的渲染细节未实机验证**：上游「出厂设置下确实会画」（`drawContainerShadows = true`），
   但两条实现的渲染分支不同，**最终像素是否完全一致未验证**。
4. **卡片的无障碍时长延长**：`ToastDuration` 只保留数值常量（Khromia 的 `ToastEntry.duration` 契约是 `Long`），
   上游 `ToastDuration.toMillis(accessibilityManager)` 的**延长能力**未在本页改动范围内（只对齐数值）。
5. **软件内看板不排队进 toast 队列**（有意）：常驻看板与普通 toast 可能同屏共存；这是为了避免常驻项永久占住单槽队列。

---

## #6 bottomsheet(miuix) 内容距离边界边距过大 —— 已修

**根因**：Miuix `WindowBottomSheet` → `BottomSheetContentLayout` 的 `insideMargin` **库默认**是
`DpSize(24.dp, 0.dp)`（miuix-ui 0.9.3 `BottomSheetContentLayout.kt:890`），并在 `squircleSurface`
**内部**对 `[DragHandleArea → TitleAndActionsRow → content()]` 整列施加 `.padding(horizontal = insideMargin.width)`
（`:637-638`）。MD3 侧 Khromia `BottomSheet.kt:152` / `:222` 的内容容器是 `Column(Modifier.fillMaxWidth())`
（零内缩）；`KedgeCard` 默认 `contentPadding = PaddingValues(16.dp)`（`KedgeContainers.kt:145`、`:169`）。
⇒ Miuix 侧凭空多 24dp，与 MD3 对不齐。

**改动**

- `Kedge/kedge/src/main/java/heizige/kk/kedge/overlays/KedgeSheets.kt`
  - `:64` `MiuixModalSheetInsideMarginWidth = 16.dp`（`KedgeModalBottomSheet` 的缺省值，与该函数 MD3 分支那层
    `KedgeCard` 的 16dp **对齐**）；`:73` `MiuixPrimarySheetInsideMarginWidth = 0.dp`
    （`KedgePrimaryBottomSheet` 横向归零，与 Khromia `PrimaryBottomSheet` 的零内缩对齐）。
  - `:94` `KedgeModalBottomSheet(..., miuixInsideMarginWidth: Dp = MiuixModalSheetInsideMarginWidth, content)`
    —— 新增**按调用点可覆盖**的横向内缩参数（放在 trailing lambda 之前；所有现有调用点都具名传参 ⇒ 源兼容）。
  - `:120-126` Miuix 分支 `insideMargin = DpSize(width = miuixInsideMarginWidth,
    height = BottomSheetDefaults.insideMargin.height)`：**横向按调用点取值，纵向恒取库默认**（参数永不控制纵向）。
  - `:234-236` `KedgePrimaryBottomSheet` 的 Miuix 分支横向恒 `0.dp`。
  - `.imePadding().navigationBarsPadding().padding(bottom = 12.dp)` 纵向链**一字未改**。
- `app/src/main/java/heizige/kk/khatkit/app/feature/onboarding/GreetingPage.kt:1061-1071`
  - Miuix 入口显式传 `miuixInsideMarginWidth = 0.dp`（+4 行注释）：**因为这一处的 MD3 对位物不是 `KedgeCard`
    而是 Khromia `PrimaryBottomSheet`（内部 0）**，缺省 16dp 会让该屏 Miuix 比 MD3 多 16dp。

**关键规格数值（360dp 宽手机、sheet 全宽；「内部」= 风格容器自己那层，不含调用方 padding）**

| 路径 / 调用点 | MD3 贴边 | Miuix 贴边（改前 → 改后） |
|---|---|---|
| `KedgePrimaryBottomSheet` 路径（KhatKit 66+ 处 `PrimaryBottomSheet(`） | 容器内部 0 + 调用方 | 前 24 + 调用方 → 后 **0 + 调用方**（16→16、24→24、8→8、20→20） |
| `KedgeModalBottomSheet` / `ExploreMarketPage.kt:1204`（调用方 24dp） | 16(KedgeCard) + 24 = **40** | 前 24+24=48 → 后 **16 + 24 = 40** ✅ |
| `KedgeModalBottomSheet` / `MediaFilterSheet.kt:64`（调用方 16dp） | 16 + 16 = **32** | 前 40 → 后 **16 + 16 = 32** ✅ |
| `KedgeModalBottomSheet` / `GreetingPage.kt:1062`（显式传 0） | 0(Khromia) + 16 = **16** | 前 16+16=32 → 后 **0 + 16 = 16** ✅ |
| 委派型入口（`ChatPage.kt:747` → FilesPicker 16dp、`KhatKitUiHost.kt:161`、`ChatInput.kt:706`、`GreetingPage`） | 16 | 前 24+16=40 → 后 **0 + 16 = 16** |

- 纵向：两入口的 `insideMargin.height` **恒取库默认 `0.dp`**；底部仍是
  `.imePadding().navigationBarsPadding().padding(bottom = 12.dp)`（未改）。
- 把手/标题**仍居中**：Miuix 侧 `fillMaxWidth()` + `contentAlignment = Center`，两侧对称内缩
  ⇒ 45dp 拖拽把手中心仍在该宽度中点（360dp 宽时 180dp），逐像素不变；Miuix 可拖拽区 360 → 328dp
  （仅可拖拽区变窄，视觉不变）。`startAction`/`endAction` 是唯一会移动的元素，**全仓无调用点**。

**验证方式与结论**

- 门禁：`:Kedge:kedge:compileDebugKotlin --rerun` exit 0（14 tasks executed）、`:app:compileDebugKotlin` exit 0、
  `:app:assembleDebug` exit 0。
- 源码级复算：由独立评审按**两层源码**（Miuix 库 `BottomSheetContentLayout` + Khromia `BottomSheet` +
  `KedgeCard`）逐调用点算过，上表数字与实现者自述一致；缺省路径（未传参的 ExploreMarketPage / MediaFilterSheet）
  与 t10 行为逐点相同，未回归。
- **实际观感未验证**（团队不跑真机）。

**用户自查路径**

1. 切 **Miuix**，进与 bottomsheet 相关的页面，例如：
   - **聊天页附件选择**（输入栏「+」→ 文件/图片选择 sheet，调用方 16dp）；
   - **聊天页推理强度选择**（调用方 24dp）；
   - **模型/供应商选择**（调用方 8dp）；
   - **录音卡片 sheet**（调用方 20dp）；
   - **市场页 sheet**（调用方 24dp）、**筛选 sheet**（调用方 16dp）、**问候/引导页 sheet**。
2. 对比**左/右两条内容边与 sheet 边缘的距离**（改前会明显多出约 24dp 的空白）。
3. 切 **MD3** 打开对应入口，确认两风格的左右贴边距离**看起来一致**。
4. 顺带确认：拖拽把手仍**水平居中**、标题仍居中，可正常上下拖动 sheet；键盘弹出时 sheet 仍正常避让（纵向未改）。

**残余问题 / 已知例外**

1. **`GreetingPage` 那处必须显式传 0**：因为它的 MD3 对位物是 Khromia `PrimaryBottomSheet`（内部 0）而不是
   `KedgeCard`（内部 16）。已用参数覆盖解决；**没有改任何业务代码的 padding**。
2. **`KedgeModalBottomSheet` 的缺省值是 16dp 而非 0**：这是为 MD3 分支 `KedgeCard` 的 16dp 对齐；若将来有新的
   调用点其 MD3 对位物是「零内缩容器」（像 GreetingPage 那样），**必须显式传 0**，否则会多 16dp。
3. **Miuix 把手拖拽区变窄**（360 → 328dp）：视觉不变（把手仍居中），但左右各 16dp 不再响应拖拽 —— 已由评审确认
   无调用点使用 `startAction`/`endAction`，故无功能影响。
4. **`KedgePrimaryBottomSheet` 自身 MD3Exp 分支在生产里没有调用者**（生产 MD3 走 KhatKit → Khromia），已在 KDoc 记录。

---

## #7 关于页面 MD3 下不应该有动态流光背景 —— 已修

**根因**：MD3 版关于页（`SettingAboutPage.kt`）最外层包了 `BgEffectBackground(dynamicBackground = true)`，
并把 `Scaffold(containerColor = Color.Transparent)` 用来露出流光 ⇒ MD3 风格也有动态流光。

**改动**

- `app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutPage.kt`
  - 删除最外层 `BgEffectBackground { … }` 包裹与 `import heizige.kk.khatkit.app.core.ui.effect.BgEffectBackground`（:55）。
  - `:144` `Scaffold(containerColor = MaterialTheme.colorScheme.background)`（**不透明**，原为 `Color.Transparent`；
    留透明会直接露出下一层 `NavDisplay` / Activity 根）。
  - `:68-78` / `:133` / `:154` KDoc 与注释订正：本页**不铺流光**是**有意**的 MD3 观感差异；
    顶栏 `titleAlpha = ((animatedProgress - 0.35f) / 0.65f)` 与 `barColorAlpha = animatedProgress`
    的映射**未改**（与 `KedgeNavigation.kt:385` / `:397-400` / `:419` 的既有映射仍未动）。
- **未改** Miuix 版：`feature/settings/SettingAboutPageMiuix.kt:158` 仍保留 `BgEffectBackground`
  （全仓 `BgEffectBackground(` 只剩定义与该 Miuix 页）。

**关键规格数值**

- MD3 关于页底色 = `MaterialTheme.colorScheme.background`（不透明）。
- 滚动三段淡出/缩放/位移映射**未改**（`logoProgress` / `nameProgress` / `versionProgress` / `titleAlpha` 区间不变）。
- Miuix 关于页仍走流光（有意保留）。

**验证方式与结论**

- 门禁三条 KhatKit 命令 exit 0；源码级核对：全仓已无 MD3 页引用 `BgEffectBackground`。
- 评审另核出：本文件除语义改动外有一批**缩进重排**（`git diff` 131/136 行，其中 `git diff -w` 只剩
  13 insertions/18 deletions 的语义改动）——**语义零变化**，现状缩进已规范。
- **观感未验证**（团队不跑真机）。

**用户自查路径**

1. 切 **MD3**，进**设置 → 关于**页。
2. **上下滚动**并观察背景：**不应**出现动态流光/流动色块，应为纯静态的不透明页面底色；
   顶部大 logo 仍按原曲线淡出/缩小/上移，顶栏标题仍在滚过约 35% 后淡入。
3. 切 **Miuix** 进关于页：**应保留**动态流光（有意差异）。
4. 两种风格各切一次快速来回，确认 MD3 页不再「闪一下」透出下层。

**残余问题**：无功能残余；仅记录 t4/t10 期间该文件的大段缩进重排（语义零变化）。

---

## 门禁与产物（三仓改动共存下的最终集成门禁）

> ### ⚠️ 勘误：本页的时间戳不可用挂钟（2026-10-14 补记）
>
> 本页原先整套「`2026-10-07 22:58–23:00`」的记录，是**当时本机挂钟的读数**：收口跑批期间本机
> 挂钟偏慢约 6 天 14 小时（即常说的「落后约 7 天」），所以那批挂钟值整体落在 2026-10-07。
> 用外部权威时间核对（`curl -sI https://www.baidu.com` 的 `Date` 响应头，GMT → +0800）得出该批
> 收口发生在 **2026-10-14 13:12 (+0800) 前后**；写本勘误时（2026-10-14 13:31 +0800）再次核对同一
> 外部源，本机挂钟已与外部一致（差值在秒级网络往返之内）。
>
> ⇒ **实测耗时可信、挂钟不可信**：Gradle 自报的 `BUILD SUCCESSFUL in Xs` 与相邻步骤的差值都是
> 单调的实测值，下表已改为只写实测耗时；正文里保留的 mtime 一律标注为「当时挂钟」，
> 并单独注明该产物此后是否被重写过。
>
> 本勘误只涉及时间戳：7 项技术结论、残余项、自查路径均不受影响；本节之外的挂钟读数
> （例如下文 git 提交日期）本轮未做外部核对，故不改动。

**执行方式**：全部**串行**（本机 16GB，禁止并行 gradle：并行 daemon 曾三次造成内存打满与实际构建失败），
用全队互斥锁 `flock /tmp/kk-gradle.lock`，`--max-workers=1`，
`-Dorg.gradle.daemon.idletimeout=120000`，并**全部带 `--rerun` 强制真实执行**（不加会缓存命中，
在树损坏时静默 exit 0）。收尾已执行 `./gradlew --stop`（两仓各停 1 个 daemon，现无残留 daemon）。

| # | 命令 | exit | 实测耗时 | 是否真实执行 |
|---|---|---|---|---|
| 1 | `cd KhatKit && flock /tmp/kk-gradle.lock ./gradlew --offline --max-workers=1 -Dorg.gradle.daemon.idletimeout=120000 :app:compileDebugKotlin --rerun` | **0** | **66s**（`BUILD SUCCESSFUL in 1m 5s`） | 是（`> Task :app:compileDebugKotlin` 非 UP-TO-DATE；`149 actionable tasks: 1 executed`） |
| 2 | 同上把末段换成 `:app:testDebugUnitTest --rerun` | **0** | **14s**（`BUILD SUCCESSFUL in 14s`） | 是（`> Task :app:testDebugUnitTest` 执行；`278 actionable tasks: 1 executed`） |
| 3 | `cd Kedge && flock /tmp/kk-gradle.lock ./gradlew --offline --max-workers=1 -Dorg.gradle.daemon.idletimeout=120000 :kedge:compileDebugKotlin --rerun` | **0** | **8s**（`BUILD SUCCESSFUL in 7s`） | 是（`> Task :kedge:compileDebugKotlin` 执行；`14 actionable tasks: 14 executed`） |
| 4 | `cd KhatKit && flock /tmp/kk-gradle.lock ./gradlew --offline --max-workers=1 -Dorg.gradle.daemon.idletimeout=120000 :app:assembleDebug --rerun` | **0** | **30s**（`BUILD SUCCESSFUL in 29s`） | 是（`> Task :app:packageDebug` + `> Task :app:assembleDebug`；`362 actionable tasks: 54 executed`） |

> ⚠️ 合同原文把 Kedge 那条写成「在 KhatKit 目录下跑 `:kedge:compileDebugKotlin`」——该地址不可用：
> 实测（由前置验证任务 t15 执行并记录）报
> `Cannot locate tasks that match ':kedge:compileDebugKotlin' as task 'compileDebugKotlin' not found in project ':Kedge'`，
> 因为 `:kedge` 被解析为 included build 的根项目。上表用的是**正确地址**：在 `Kedge/` 目录下跑
> `:kedge:compileDebugKotlin`（= `Kedge` 仓内该模块的路径）。这是命令地址写法问题，不是代码问题。

**测试基线**：`:app:testDebugUnitTest` 由 `app/build/test-results/testDebugUnitTest/*.xml` 聚合得
**118 suites / 961 tests / 0 failures / 0 errors / 0 skipped**（结果目录 mtime 是**当时挂钟** `2026-10-07 23:00:19`，与本次强制重跑一致；该挂钟值不可信，见本节勘误。该目录此后已被后续跑批重写，不再是这一次收口的产物）。

**debug APK（2026-10-14 二次收口重建后的实测值）**

- 目录：`Android/Project/KhatKit/app/build/outputs/apk/debug/`
- 下面三个 ABI 变体是 **2026-10-14 13:45（t28 收口时 `:app:assembleDebug --rerun` 重新构建）** 的产物，
  **替代此前 `2026-10-14 13:10:46` 那一批**（再往前是 `2026-10-07 23:00:55` 那批，挂钟值不可信，见本节勘误）。
  mtime 与体积由 `stat -c '%y %s' app/build/outputs/apk/debug/app-*.apk` 逐条读取：
  - `app-universal-debug.apk` — mtime `2026-10-14 13:45:35.653232498 +0800` — **110,880,336 bytes**（通用包，一般装这个）
  - `app-arm64-v8a-debug.apk` — mtime `2026-10-14 13:45:35.892231059 +0800` — 94,073,173 bytes
  - `app-x86_64-debug.apk` — mtime `2026-10-14 13:45:36.167229404 +0800` — 94,833,434 bytes
- 完整路径（通用包）：
  `/home/heizige/文档/Android/Project/KhatKit/app/build/outputs/apk/debug/app-universal-debug.apk`

#### 本次收口实测（t28，2026-10-14 13:44–13:48）

四条门禁均为 `cd KhatKit && flock /tmp/kk-gradle.lock ./gradlew --offline --max-workers=1
-Dorg.gradle.daemon.idletimeout=120000 <task> --rerun`，**串行**执行（禁并行 daemon）：

| # | 任务 | exit | 实测耗时 | 真实执行证据 |
|---|---|---|---|---|
| 1 | `:app:compileDebugKotlin` | **0** | **58s**（`BUILD SUCCESSFUL in 58s`） | `> Task :app:compileDebugKotlin` 非 UP-TO-DATE；`153 actionable tasks: 1 executed`；**编译输出 0 条 `e:`** |
| 2 | `:app:testDebugUnitTest` | **0** | **15s**（`BUILD SUCCESSFUL in 14s`） | `> Task :app:testDebugUnitTest` 执行；`282 actionable tasks: 1 executed` |
| 3 | `:ai:testDebugUnitTest` | **0** | **2s**（`BUILD SUCCESSFUL in 2s`） | `> Task :ai:testDebugUnitTest` 执行；`25 actionable tasks: 1 executed` |
| 4 | `:app:assembleDebug` | **0** | **14s**（`BUILD SUCCESSFUL in 13s`） | `> Task :app:packageDebug` + `> Task :app:assembleDebug`；`362 actionable tasks: 11 executed` |

测试聚合（`build/test-results/testDebugUnitTest/*.xml`）：
`:app` = **118 suites / 961 tests / 0 failures / 0 errors / 0 skipped**；
`:ai` = **31 suites / 224 tests / 0 failures / 0 errors / 0 skipped**，其中
`TEST-heizige.kk.khatkit.ai.util.RequestHeadersTest.xml` = **tests=4 / 0 failures**
（用例：provider headers when no request headers / request headers override provider headers /
ignore blank header names / custom headers should survive json round-trip）。
⚠️ `:app:testDebugUnitTest` **不含** `:ai` 模块测试，961 是 app 侧的数；新增的 `RequestHeadersTest`
在 `ai` 模块（= 上游位置），由第 3 条命令覆盖。

**安装到真机（本次起改为尝试安装，实测已成功）**：
- 设备：`PKG110`（Android 16），此前配对的无线端点 `192.168.31.182:33729` 已不在列表，
  当前在线端点是 `adb-3B6F5ME910B6H059-Sqr0AX._adb-tls-connect._tcp`（`adb devices` 直接可见，无需重连）。
- 命令与结果：`adb -s <该端点> install -r app/build/outputs/apk/debug/app-universal-debug.apk`
  → `Performing Streamed Install` / **`Success`**，exit 0，耗时 **15s**（2026-10-14 13:45:58 → 13:46:13）。
- 安装后核对：`dumpsys package heizige.kk.khatkit.debug` →
  `versionCode=190` / `versionName=2.5.5` / **`lastUpdateTime=2026-10-14 13:46:12`**；
  启动后 `pidof heizige.kk.khatkit.debug` = **15884**，`dumpsys activity` 里
  `topResumedActivity = heizige.kk.khatkit.debug/heizige.kk.khatkit.app.RouteActivity`，
  `logcat` 无 `FATAL`/`AndroidRuntime` 异常 ⇒ 新包可安装、可启动。
- ⚠️ **界面冒烟未完成（如实记录，未冒充已完成）**：安装/启动那一刻设备正被**另一个应用全屏占用**
  （截屏显示视频播放器全屏，KhatKit 只显示在右上角的小浮窗里），`uiautomator dump` 反复命中的都是
  前台应用的窗口而不是 KhatKit；在别人正在使用的屏幕上盲点坐标会误触其内容，因此没有继续投点。
  **深度界面冒烟（Debug 页 tab 数、搜索弹层返回、GitHub 技能安装）留给你自行完成**，
  逐项的可达性与自查路径见下面「界面冒烟项与『UI 是否可达』实测」段。

**界面冒烟项与「UI 是否可达」实测**（在设备上做之前先核了可达性，结论如下）：

- **① Debug 页只剩 3 个 tab —— 本构建里 UI 不可达，无法冒烟**：
  `Screen.Debug` 在全仓**只有** `RouteActivity.kt:563-565` 的 `entry<Screen.Debug>` 注册，
  **没有任何 `navigate(Screen.Debug)` 调用**（`git grep -n "Screen.Debug" HEAD -- '*.kt'` 同样只有 entry 那一行，
  即 HEAD 也是死入口）；`git log -S "Screen.Debug"` 显示入口是随 commit `48c2c0915`
  「全面 UI 精简」摘掉的（**早于本页 7 项修复，不是本次回归**）。因此这个页面在设备上根本打不开，
  只能用代码级证据：`DebugPage.kt` 现在是 `rememberPagerState { 3 }` + `titles = listOf("Main", "Colors", "Recovery")`
  + 删掉了 `2 -> Box {}`（t23 已改，t26 独立复核 pass）。若你要在设备上亲眼看：
  临时给 `RouteActivity` 的某处加一个 `navController.navigate(Screen.Debug)` 入口再装一次即可（属源码改动，本任务不能做）。
- **② 搜索弹层返回语义 —— 本构建里 UI 不可达，无法冒烟**：`SearchPickerButton`（唯一的公开入口）
  在全仓**零调用者**（`grep -rn "SearchPicker" --include=*.kt` 只命中 `SearchPicker.kt` 自身），
  同一条 `48c2c0915` 提交把它的使用点一起摘掉了。所以 `SearchPicker.kt:183-185` 新增的
  `BackHandler(enabled = selectingProvider)` 目前**没有任何用户可达路径**，只能停留在代码级验证（t26 已复核其作用域：全文仅 1 处 `BackHandler(`，在弹层组合块内）。
- **③ GitHub 技能安装（字节级下载）—— 路径可达但本次未在设备上跑**：
  入口是 **设置 → 扩展 → 技能**（`SettingPage.kt:187` → `ExtensionsPage.kt:65` → `Screen.Skills`；
  抽屉里的设置入口见 `ChatDrawer.kt:430`），技能页右上「从 GitHub 导入」（`SkillsPage.kt:194/370-405`）
  填 `https://github.com/<owner>/<repo>` 确认即可。未跑的原因同上（设备被别的应用全屏占用，
  无法稳定地把 KhatKit 拉到前台完成多步输入）。**这一项也留给你自行完成**。

**遗留与残余（t29 正式收口 · R2 关闭，2026-10-14）**

> 这一段是本次「修复 + 同步」两批工作的**最终口径**。凡此处写「未验证」的，就是**没有**验证过，
> 不要按「已验证」读。

- **① t23 的 ①② 只能做「代码级 + 评审级」验证，实机冒烟在本树上对任何人都无法完成。**
  证据（本任务 t29 重新独立复核，命令与输出逐字如下）：
  - `Screen.Debug` 全仓唯一命中是 `app/src/main/java/heizige/kk/khatkit/app/RouteActivity.kt:563`
    的 `entry<Screen.Debug> { DebugPage() }` 注册（该 entry 块 = :563-565），**零个
    `navigate(Screen.Debug)` 调用**（`git grep -n "Screen\.Debug" -- '*.kt'` 只回这一行）。
  - `SearchPickerButton` 全仓唯一命中是它**自己的定义处**
    `app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ai/SearchPicker.kt:83`
    （`git grep -n "SearchPickerButton" -- '*.kt'` 只回这一行），**零调用者**。
  - 两处入口均由 commit `48c2c0915`（`2026-09-12 21:56:20`，「…输入框胶囊化+Genie 展开动画与预测返回…
    全面 UI 精简（删历史/收藏/统计/助手权限面板等）」）摘掉，**早于本次 7 项修复** ⇒ **不是本次回归**。
  - ⇒ 因此 t23 的 ①（Debug 页删空白 tab）与 ②（搜索弹层返回语义）**正式改为代码级 + 评审级验收**，
    评审结论 **t26 verdict=pass**（被测 hash：`DebugPage.kt 6aebbfe67e8c33720c5b97f2c3923818` /
    `SearchPicker.kt eae999ef3804ae02a15d24cd0ac244c0`）。这两处改动的来源是**上游 RikkaHub 2.5.6 同步尾项**
    （照上游 `db1ce811`、`447bb7e89` 移植），不是用户报的现网缺陷；且此前刚按你的要求**隐藏**群聊入口
    （精简 UI 方向），把 Debug 页入口加回来与之相反 ⇒ **入口保持现状，等有实际需要再说**。
    想亲眼看这两处需临时给 `RouteActivity` 加一个 `navigate(Screen.Debug)`（前者的 3 个 tab）
    或一个 `SearchPickerButton` 调用点（后者的返回语义），**属源码改动，本次任务按合同不能做**。
- **② t23 ③（GitHub 技能安装改字节级下载）本次未做真机验证 —— 待用户自查，不是「已验证」。**
  路径可达（设置 → 扩展 → 技能 → 右上「从 GitHub 导入」），但**没有在设备上跑过**：
  安装 APK 后设备全程被用户本人全屏使用（视频/弹幕全屏，KhatKit 只在角落小浮窗，
  `mCurrentFocus` 在 RouteActivity 与前台别的应用之间反复切换），`uiautomator dump` 命中的是前台他人应用窗口；
  在别人正在使用的屏幕上盲点坐标会误触其内容，故**未注入任何点击，也未把未做的写成已做**。
  **你的自查步骤**：①设置 → 扩展 → 技能 → 右上「从 GitHub 导入」，填 `https://github.com/<owner>/<repo>` 确认；
  找一个**带图片/二进制附件**的技能，装完在技能目录里核对图片仍能正常打开（旧实现按 UTF-8 解码再保存，
  二进制必然损坏）；②连续多次导入触发未登录 GitHub API 的 60 次/小时配额上限，此时应看到
  **「GitHub API 请求次数已达上限，请稍后再试」**（403/429 且 `X-RateLimit-Remaining: 0`），
  而不是此前笼统的「读取失败」。这两条**只有你的实机能给出结论**。
- **③ 未来坑（来源 t26 评审备注）**：`ai/src/test/.../RequestHeadersTest.kt` 的 Json 往返用例用的是**默认 `Json` 实例**。
  当前 `ProviderSetting` **没有** `Instant` 之类需要自定义序列化器的字段，所以默认实例够用；
  **将来若给它加入 `Instant` / `JsonInstant` 类字段**，这条往返用例必须改成**与落盘配置一致的 `Json`**，
  否则会出现「测试用默认 Json 通过、实际 DataStore 落盘走另一套格式」的假绿灯。
- **④ t28 的定位（口径澄清）**：t28 的构建 / 测试 / 打包 / 安装 / 记账全部达标，
  它标 `failed` 的**唯一**原因是「最小冒烟」这条验收在本树上结构上不可执行（即上面的 ①/②/③）。
  它选择**不注入点击、不把未做的事写成做过**，这个判断是对的 —— 本条即按此修正后的口径收口。
  四条门禁证据见上面「本次收口实测（t28）」，本任务**未重跑任何 gradle**（源码 hash 已逐一核对未变）。

**本次改动文件（`git status --short`，三仓）**

- **KhatKit**（`M`=已跟踪改动，`D`=已删除，`??`=未跟踪）
  - `M app/src/main/java/heizige/kk/khatkit/app/RouteActivity.kt`（#4）
  - `M app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ai/AiChatGenieEffect.kt`（#4）
  - `M app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ai/ChatInput.kt`（#3 #4）
  - `M app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/miuix/KedgeMiuixMorphingTitleBar.kt`（#1）
  - `M app/src/main/java/heizige/kk/khatkit/app/feature/automation/AutomationInAppHost.kt`（#5）
  - `M app/src/main/java/heizige/kk/khatkit/app/feature/automation/AutomationOverlayService.kt`（#5）
  - `M app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatDrawer.kt`（#2）
  - `M app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatPage.kt`（#1）
  - `M app/src/main/java/heizige/kk/khatkit/app/feature/onboarding/GreetingPage.kt`（#6）
  - `M app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingAboutPage.kt`（#7）
  - `?? docs/uiredesign.md`（本页，新增记账——文件本身是未跟踪的）
  - `M CHANGELOG.md`（本次修复条目追加在 `## 未发布（自动化 Toast 与图片选择器迁移）` 段内）
  - **改动前的既有未提交改动（非本次工作，见下）**：`M docs/architecture-map.md`（mtime 2026-10-06 16:25）、
    `M app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tavern/TavernMacroExpander.kt`（mtime 2026-10-05 14:04）、
    `D docs/upstream-sync-2026-10-03.md`、`?? app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/C1GroupUiE2EFixtureTest.kt`
    （mtime 2026-10-06 01:34）
- **Kedge**：`M kedge/src/main/java/heizige/kk/kedge/overlays/KedgeSheets.kt`（#6）
- **Khromia**：`M khromia/src/main/java/heizige/kk/khromia/components/Toast.kt`（#5）、
  `M khromia/src/main/java/heizige/kk/khromia/data/ColorHelp.kt`（#5）、
  `M khromia/src/main/java/heizige/kk/khromia/data/ToastData.kt`（#5）、
  `?? khromia/src/main/java/heizige/kk/khromia/components/IconShapeContainer.kt`（#5，新增）、
  `?? .kotlin/errors/errors-*.log` ×4（构建工具产物，非源码）

**越界与遗留检查**

- out of scope 的三个文件**均未被本次工作改动或删除**：
  `docs/architecture-map.md`（仍是改动前的既有 `M`，mtime 2026-10-06 16:25，早于本次全部改动）、
  `TavernMacroExpander.kt`（既有 `M`，mtime 2026-10-05 14:04）、
  `C1GroupUiE2EFixtureTest.kt`（既有未跟踪文件，mtime 2026-10-06 01:34，仍存在）。
- **未执行任何 `git commit` / `git push`**：三仓 HEAD 与本次工作开始前一致
  （KhatKit `2505cf7a1` 2026-10-07 16:38、Kedge `20854d2` 2026-10-03 22:30、Khromia `674be8f` 2026-10-02 15:58），
  `git reflog` 最新条目都早于本次 UI 改动时间。
- 仅 `docs/uiredesign.md` 与 `CHANGELOG.md` 是本任务**写**过的文件（本任务只写文档，未改任何业务代码）。

