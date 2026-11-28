# 导航与预测性返回（Mochi 落地核对）

> 来源：ARCHITECTURE.md §8「Nav3 预测性返回与过渡规范」+ ../Mochi/docs/zh_CN/guide/application-integration.md。
> 本仓导航已由 Nav3 NavDisplay 换成 Mochi（U8），§8 的三条铁律按原理落到 Mochi：
> **route 的 pop/转场只由唯一的 MochiNavHost 持有；页面内的浮层由 PageBackGuard 先消费；禁止用 NavigationEvent.progress 单独驱动 route 过渡。**

## 1. 结论（t29）

| 项 | 状态 | 证据 |
|---|---|---|
| 单一 route pop owner | 符合 | `RouteActivity.kt` 只渲染 1 个 `MochiNavHost(`；无 `NavDisplay(`；新增 `NavigationSourceGuardTest` 守门 |
| 浮层先消费 / 后登记优先 | 符合 | `PageBackGuard` + `PageBackInterceptor`（内部对象，见 §3）；`PageBackInterceptorTest` 覆盖 route 隔离与优先级 |
| commit/cancel 不损坏 | 符合 | 各浮层「事件流正常结束=commit，CancellationException=cancel」；逐条见 §4 |
| 不用 progress 驱动 route 过渡 | 符合 | 源码 0 处 `NavigationEvent.progress`；progress 只驱动浮层本地进度 |
| edge-to-edge | 符合 | `RouteActivity`/`SafeModeActivity` 均在 `super.onCreate` 前 `mochiEnableEdgeToEdge()`；`EdgeToEdgeSourceGuardTest` |
| 系统预测返回开关 | 符合 | `AndroidManifest.xml` `android:enableOnBackInvokedCallback="true"`（已有，未改） |

本批实际修正 1 处行为缺口：媒体选择器返回优先级（搜索 → 相册「全部」→ 清空选择 → 关闭），原先选择处理器在相册筛选态也会先清空选择。

## 2. §8 三条铁律 → Mochi 映射

| §8 规则 | Mochi 落地 |
|---|---|
| 松手续播不写代码 | route 过渡/松手续费由 `MochiNavHost(predictiveBack = …)` 内部持有；本仓只在 `PageBackGuard` 里处理「有浮层」时的事件流，浮层自己收尾，route 不 pop |
| 跟手效果挂过渡树 | 浮层的跟手进度写本地 `Animatable/State`（editorReveal、search progress 等）；route 动画形状由 Mochi 库给出，页面不注册 route 子动画 |
| 上限 = 终态幅度 | 浮层把 `BackEventCompat.progress` 归一化到 0..1 再映射自己的终态（编辑器/搜索/选择） |

## 3. 返回 owner 链

1. **Activity**：`RouteActivity` 只提供一个 `MochiNavHost`，route pop 与页面转场由它持有。
2. **页面级**：每个 top route 包一层 `PageBackGuard`（`RouteActivity.kt`）：
   - 有预测浮层：`MochiNavHost.predictiveBack = false`（`!hasOverlay`），由 `PageBackGuard` 的唯一 `PredictiveBackHandler` 把整条事件流交给 `PageBackInterceptor` 选中的浮层；
   - 只有普通浮层：`PageBackGuard` 用 `BackHandler` 先消费（不跟手），无浮层才回退到 Mochi route pop；
   - 非栈顶页：`rememberNavigationEventDispatcherOwner(enabled = isTopPage)` 关掉子分发器，避免下层页 BackHandler 吃掉栈顶返回。
3. **浮层级**：`PageBackInterceptor.push(token, ownerRoute, handler)`，按 route 隔离；`pushPredictive` 额外登记事件流处理器，**后登记者优先**（叠层先消费）。
4. **页面 pop 动作**：`PageBackGuard.onPop = { if (!kkNav.popBackStack()) finish() }`；`MochiNavHost` 自己处理无浮层的预测手势 pop。

> `PageBackInterceptor`/`PageBackGuard` 目前是 `RouteActivity.kt` 内的 `internal object`/私有 Composable：
> 拆成独立文件会牵动 `LocalPageRoute` 的 12 个已接线消费点（含本任务 out-of-scope 文件），
> 故保留单文件，行为边界不变；文件级拆分留给后续集成（t45）决定。

## 4. 全部 PredictiveBackHandler 逐条判定

| # | 位置 | enabled | commit（事件流正常结束） | cancel（CancellationException） | 是否驱动 route |
|---|---|---|---|---|---|
| 1 | `RouteActivity.kt:615` PageBackGuard | `predictiveBackEnabled && hasPredictiveOverlay` | 浮层自己收尾，route 不 pop | 浮层回弹，route 不 pop | 否（只分发浮层） |
| 2 | `RouteActivity.kt:622` PageBackGuard BackHandler | `hasOverlay && (!predictiveBackEnabled || !hasPredictiveOverlay)` | 浮层 handler 返回 true 则消费，否则 onPop | 不适用（按键） | 仅无浮层时 onPop |
| 3 | `ChatInput.kt:456` FLB 手风琴 | `progress > 0.02 && !editorOpen` | `animateTo(0f)` 收起 | `animateTo(1f)` 弹回 | 否（本地 progress） |
| 4 | `ChatInput.kt` 编辑器（经 `pushPredictive`，RouteActivity:615 分发） | 编辑器打开 | 收尾并 `editorOpen=false` | 回弹到 `startReveal` 后保持打开 | 否 |
| 5 | `PredictiveSearchBack.kt:51` | `progress > 0.02` | `onCollapse()` | `animateTo(1f)` | 否 |
| 6 | `KhatKitMediaPicker.kt:158` 搜索 | `isSearching` | `isSearching = false` | 保持 | 否 |
| 7 | `KhatKitMediaPicker.kt:166` 相册 | `!isSearching && album != ALL` | 回 `ALL_ALBUM_ID` | 保持 | 否 |
| 8 | `MediaPickerGridWithOverlays.kt:294` 选择 | `selection 非空 && !isSearching && album == ALL`（**本次补最后一项**） | 清空选择 | 保留选择 | 否 |

非预测型登记（走第 2 行 BackHandler 路径，不跟手）：`SearchPicker` 子页、`GlassPopup`、`RoleMemoryPanel`、`ChatPage` 附件面板、`ImgGenPage`、`WebViewPage`、`WorkspaceDetailPage`、`GreetingPage` 步骤。
其中 `GlassPopup` 关闭了原来的跟手效果（U7a-6 收敛到 PageBackInterceptor 的代价），属已知产品取舍，t45 可评估恢复跟手。

## 5. edge-to-edge 与根主题

- `RouteActivity`、`SafeModeActivity` 都在 `super.onCreate(savedInstanceState)` **之前**调用 `mochiEnableEdgeToEdge()`，且全仓 Activity 无 `androidx.activity.enableEdgeToEdge` import（守门测试见 `EdgeToEdgeSourceGuardTest`）。
- 系统栏图标明暗由 `MochiTheme`/`KhatKitTheme` 接管；源码无 `isAppearanceLightStatusBars`。
- 页面不重复加安全区：顶栏/Insets 由 Mochi 组件消费；本批保留现有顶栏/安全区 WIP，不改样式。

## 6. 验证

已由本任务执行（无 Gradle/无设备）：

- `git diff --check` → exit 0。
- 源码守门断言（`NavigationSourceGuardTest`）：MochiNavHost 1 个、无 NavDisplay、无 `NavigationEvent.progress`、hasHandlersFor(topRoute) 门控、`predictiveBack = … && !hasOverlay`、manifest 开启回调、媒体选择器返回顺序。
- 单元测试（待 t45 运行）：`PageBackInterceptorTest`（route 隔离 / 预测优先级 / 后登记者优先）、`EdgeToEdgeSourceGuardTest`、`NavigationSourceGuardTest`。

人工验收（t45 / 真机，Android 14+ 且开发者选项开启预测返回动画）：

1. 设置 → 子页：无浮层时手势跟手侧推，松手 commit 完成返回，中途取消回弹。
2. 聊天页：编辑器打开时手势由编辑器跟手收起；手风琴展开时由其跟手；编辑器关闭后再手势才返回页面。
3. 搜索展开态：手势跟手收回搜索，取消回弹。
4. 媒体选择器：搜索态 → 相册筛选 → 已选照片 → 关闭选择器，逐级手势返回顺序正确；取消不清状态。
5. 侧边栏/面板等非预测浮层：一次返回先关浮层、再返回退出页面。

## 7. 已知缺口 / 不做项

- `MediaPickerDialog` 的关闭转场由库内 `MochiNavHost` 负责；artifact 默认 `predictiveBack` 值未能在本地源码静态核验（Maven 产物不在仓库），交由 t45 真机确认。
- `PageBackInterceptor` 保留 ownerRoute=null 的旧兼容语义（按「任意页」计）；当前仓内 0 个 null-owner 调用点，后续可收紧删除。
- `GlassPopup` 无跟手（见 §4 备注）。
- 本批不改文案/资源 key、不改协议/序列化、不改 Maven 产物、不重造 NavDisplay。

