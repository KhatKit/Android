# NAV3：预测性返回「按两次才退一层」审计（2026-10-31）

范围：U7a-6 之后的 `RouteActivity.kt` 返回接管 + `PageBackInterceptor` 各登记点。
只读审计，未改源码。

## 结论（按可能性排序）

**① PageBackGuard 与 MochiNavHost 同时注册了两类返回处理器，且 guard 的预测性处理器不给预览（最可能）**

- `RouteActivity.kt:537-552` 的 `PageBackGuard` 在 `PageBackInterceptor.hasHandlers == true` 时同时注册
  `BackHandler`(:539) 和 `PredictiveBackHandler`(:542)；
- `MochiNavHost.kt:155` 同时注册 `PredictiveBackHandler(enabled = predictiveBack && navigator.canPop)`
  （`predictiveBack` 默认取 `LocalMochiPredictiveBack` = true，全仓无覆盖），即正常页面也常驻；
- 组合顺序上 guard 在 `MochiNavHost` 的 content 里，注册更晚 ⇒ 只要 `hasOverlay` 为 true，
  手势/返回键先落到 guard。guard 的预测性处理器只 `events.collect {}`，**不驱动任何跟手预览**，
  cancel 分支 `throw CancellationException` 且不 pop ⇒ 用户看到「第一次返回没反应（或只是缩一下）」，
  第二次再按才 pop。这就是「按两次返回才退一层」的直接观感。
- `hasOverlay` 为 true 的常见来源：WebView `canGoBack`、探索/聊天搜索 `expanded`、
  ChatInput 全屏编辑器、ImgGen `isGenerating`、Workspace 多选/子目录、RoleMemory 未保存。
  这些页面只要处于上述状态，返回就会先被 guard 吃一次。

**② 页面级拦截器泄漏 —— 已排除**

- 全仓 9 处 `PageBackInterceptor.push` 都改成了 `DisposableEffect(条件)`：条件真才 push、假即 pop、
  退出组合再 pop（ChatInput:313、GlassPopup:118、SearchPicker:188、PredictiveSearchBack:46、
  RoleMemoryPanel:87、ImgGen:135/567、WebView:83、WorkspaceDetail:159/639、Greeting:213）。
  条件为假时不会残留，`hasHandlers` 不会常驻 true。

**③ 两条返回路径不同步 —— 已排除**

- `mochiBackedNavigator = Navigator(MochiBackedNavStack(kkNav))`，页面 `navController.popBackStack()`
  → `MochiBackedNavStack.removeAt` → `kkNav.popBackStack()` → `MochiNavigator.pop()`；
- guard 的 `onPop` 也是 `kkNav.popBackStack()`，与 Mochi 内部 `navigator.pop()` 是同一个
  `MochiNavigator` 实例，仓库里没有第二条 Nav3 栈（`NavViewModel` 已删）。不存在两条栈各退一半。

**④ 先 cancel 再重放 —— 部分成立**

- guard 的预测性处理器 cancel 时 rethrow 且不 pop；系统把这次手势当作「取消」，页面上什么都没有发生。
  若之后不再重放（同一手势不会重放），就是①里描述的「第一次白按」；
- 另外 `ChatInput.kt:326/1372` 两条页内面板跟手手势在面板展开时会先收手势（不 pop 导航栈），
  在 Chat 页表现为「第一次返回只收起面板」，属设计行为；若面板已收起但 `progress/reveal > 0.01`
  尚有尾帧，会多吃一次。

## 修改点（建议的最小修复，待派单后实施）

1. `RouteActivity.kt`：给 `MochiNavHost` 传 `predictiveBack = !PageBackInterceptor.hasHandlers`
   —— 有浮层登记时让 Mochi 退回普通 `BackHandler`，无浮层时它独占预测性返回；
2. `PageBackGuard` 只保留一条 `BackHandler(enabled = hasOverlay)`，删掉它的
   `PredictiveBackHandler`（现在两条是重复接管；浮层打开时也不需要页面预览）；
3. 这样任一时刻只有一条处理器接返回：无浮层 → Mochi 预测动画；有浮层 → guard 单击收浮层，
   浮层关掉后 `hasOverlay` 自动 false，下一次返回恢复预测动画；
4. 复核 ChatInput:326/1372：两条页内手势在 `editorOpen/reveal` 尾帧时是否会多吃一次，
   可把 enabled 改成「稳定态才启用」（`reveal > 0.02f && !editorOpen`）。

## 手动复现

1. 设置 → 通知（二级页）→ 系统手势右滑：应跟手预览并退出；若「第一次无动画、第二次才退」即①；
2. 打开任一网页页（WebView）→ 页面内前进一次使 `canGoBack = true` → 手势返回：第一次应收网页（设计），
   网页退到根后再手势：应恢复 Mochi 预测动画；
3. 聊天页上滑打开输入面板/全屏编辑器 → 返回：第一次应只收面板/编辑器，收完再返回才退页（设计），
   若面板已关但需要两次才退页，即①的 guard 抢占或 ChatInput 尾帧多吃；
4. 以上任一场景录屏时看「松手前页面是否跟手」——不跟手就是 guard 抢走了预测手势。
