# Mochi 组件化重设计（探索市场 / 用量统计 / 权限 / 角色子页）

> 日期：2026-11-01。范围：把「探索市场」「用量统计」「权限」「角色（助手）各子页」的界面
> 组件从 MD3 / Kk 壳换成 **mochi-compose 的真组件**（脚手架、分组列表、行、chip、轮播、
> 空状态、搜索框、分段控件）。颜色/字阶/圆角本来就来自 `MochiTheme`，这一轮动的是**组件**。
> 全局验证：`./gradlew :app:compileDebugKotlin` exit 0。

## 0. 一句话结论

- 页面骨架：`KedgeSettingsPageScaffold`（40+ 页共用）内部已改成 **MochiTitleBar + Mochi
  分组页底色**，角色子页、设置页一次性拿到 Mochi 顶栏。
- 四个点名区域：权限页、用量统计页、角色详情页、探索市场页已改用 `MochiListSection` /
  `MochiListRow` / `MochiChip` / `MochiCarousel` / `MochiEmptyState` / `MochiSearchBar` /
  `MochiSegmentedControl` / `MochiLargeTitleScaffold` / `MochiLazyLargeTitleScaffold`。
- 仍保留 MD3 的**只有**：业务自绘可视化（统计页面积图/热力图 Canvas）、代码编辑器
  （自定义请求体的 JSON 高亮框）、媒体选择器等「不是组件选型问题」的地方。

## 1. 权限

文件：

- `app/.../core/ui/components/permission/PermissionChecklist.kt`
  - 三组权限从「逐项圆角卡片 / `KkAnimatedRadioItem`」改成一张 **`MochiListSection`**
    一组：顶部「一键授权 / 全部就绪」一行、常规权限、系统级权限（footer 说明）、高危权限。
  - 行统一 `MochiListRow`：已授权 = 行尾对勾（`checked`），未授权 = 「›」进授权流程
    （`chevron`）；高危项 `destructive = true`（标题警示色）。
  - 审批策略面板：`KkSingleChoiceSegmentedRow`（MD3 SegmentedButton）→
    **`MochiSegmentedControl`**，每个「归类」一块，放在 Mochi 卡片里。
  - 新增 `contentPadding` 参数：嵌进 Mochi 脚手架（已带 16dp 边距）时传零值。
- `app/.../feature/settings/SettingPermissionsPage.kt`
  - `KedgeSettingsPageScaffold + LazyColumn` → **`MochiLazyLargeTitleScaffold`**
    （大标题「权限」+ 返回按钮 + 列表正文）。

## 2. 用量统计

`app/.../feature/stats/StatsPage.kt`：

- 骨架 `KedgeSettingsPageScaffold` → **`MochiLazyLargeTitleScaffold`**。
- 四块内容从「手绘 28dp 圆角渐变卡」改成 **`MochiListSection`**：
  1. 概览（会话/消息 Mochi 计数器 + 2×2 次要指标）；
  2. 聊天热力（连续圆角小格 + 5 档主色深浅，连续天数进 section footer）；
  3. 用量（`MochiSegmentedControl` 7/30/90/全部 + 面积趋势图 + `MochiEmptyState`/骨架）；
  4. 任务路由（标准 `MochiListRow`）。
- 模型 / 来源排行拆成两张独立分组卡（`MochiListSection`，行尾 token 数），
  不再塞在用量卡里做进度条。
- 面积图 `TrendAreaChart` 保留（数据可视化，不是组件选型问题）。

## 3. 角色（助手）

- `feature/assistant/detail/AssistantDetailPage.kt`：`KedgeSettingsPageScaffold + CardGroup`
  → **`MochiLargeTitleScaffold`** + 一张 `MochiListSection`，七个子页入口全部
  `MochiListRow(leading=图标, subtitle=说明, chevron=true)`；头像区文字用
  `MochiTheme.textStyles`。
- `feature/assistant/AssistantPage.kt`（角色列表）：顶栏 `KedgePageLargeTopBar` →
  **`MochiTitleBar`**（与设置类页面同一个顶栏组件）。列表行仍是 `KkGroupedColumn`
  + `MochiOptionItem`：长按拖拽排序依赖「每项一个 lazy item」，换成 `MochiListRow`
  会丢 `ReorderableItem`，因此保留。
- 子页外壳统一：`KedgeSettingsPageScaffold` 内部改成 Mochi 顶栏后，
  **基本 / 提示词 / 记忆 / 扩展 / MCP / 请求参数 / 本地工具** 七个页面一次性拿到
  Mochi 顶栏与分组页底色（`Basic`、`Prompt`、`Request`、`LocalTool`、`RoleMemoryPanel`、
  `Mcp` 都走这个骨架）。
- `AssistantMemoryPage` 内部是 `RoleMemoryPanel`，同骨架。

### 3.1 角色子页里仍然 MD3 的部分

- 表单字段仍是 `Md3OutlinedTextFieldWithSlots` / `Md3TextFieldWithSlots`：
  Mochi 的 `MochiTextField` 没有 `visualTransformation` 槽，自定义请求体的 JSON 高亮会丢，
  所以这一处保留 MD3 输入框（`detail/PropertyEditor.kt`）。
- 扩展页（`AssistantExtensionsPage`）的顶部 tab 是 `SecondaryTabRow`（`KedgeTabPageScaffold`）：
  Mochi 只有底部 `MochiTabBar` 与 `MochiSegmentedControl`，没有等价的顶部 tab 条，
  自行拼一套会与「不重复造组件」冲突，留待定。

## 4. 探索市场

`feature/explore/ExploreMarketPage.kt`（含二级「卡片市场」页）：

- **顶栏**：自定义 `ExploreSearchTopBar`（MD3 large flexible bar + 标题槽位 morph 搜索框 +
  悬浮筛选栏钉线算法）整段删除，换成 **`MochiTitleBar`**：
  - 大标题 + 一行说明 + **`MochiSearchBar`** 作为列表首项（`page_header`）；
  - 顶栏标题读 `MochiScrollEdgeDefaults.progress(listState)` 只在滚上去后渐显，
    滚回顶部让位给正文大标题；
  - 钉线相关状态（`filterPinLine` / `chipsDocked` / `chipDockedSurface`）随 sticky 栏一起删除。
- **筛选 chip**：`KkRichFilterChip`（MD3 FilterChip 壳）→ **`MochiChip`**。
- **分区**：`GroupContainer`（`InlineCardGroup` + 逐项 `indexedShape`）→ **`MochiListSection`**；
  分区标题 `MarketSectionHeader` → **`MochiListSectionHeader`**。
- **行**：`MochiOptionItem` → **`MochiListRow`**，覆盖
  `MarketMoreRow` / `GroupActionRow` / `ToolRow` / `SkillRow` / `McpRow` / `ResourceRow` /
  `ModelRow` / `LocalCardRow` / `DependencyRow`（行尾动作仍是 Mochi 图标按钮）。
- **精选轮播**：`HorizontalMultiBrowseCarousel`（MD3 carousel）→ **`MochiCarousel`**。
- **空状态**：`ExploreEmptyState` 手绘 → **`MochiEmptyState`**（图标 + 说明 + 动作按钮）。
- **二级页**（`ExploreCardsPage`）同款顶栏与 chip 栏改动。
- 详情浮层 / 发布 / fork / 卸载确认仍是原来的浮层宿主（`MochiModalBottomSheet`、
  `RikkaConfirmDialog`），未改交互。

## 5. 共用骨架（一处改动，多页受益）

`app/.../core/ui/components/ui/miuix/KedgeSettingsPageScaffold.kt`：

- 原来：`MochiPageScaffold` + MD3 `LargeFlexibleTopAppBar`（`KedgePageLargeTopBar`）。
- 现在：`MochiPageScaffold` + **`MochiTitleBar`**（Mochi 顶栏：状态栏内边距、栏内按钮、
  滚动边缘渐隐），页面底色 `MochiDefaults.groupedBackground()`。
- 签名不变（`largeTitle` / `scrollBehavior` 保留但不再参与渲染），40+ 调用点零改动。
- 新增 `MochiPageContentPadding`（与 Mochi 分组卡片一致的左右 16dp）。

## 6. 复算命令

实测（本轮收尾，本机）：

```bash
cd /home/heizige/文档/Android/Project/KhatKit
./gradlew :app:compileDebugKotlin                  # BUILD SUCCESSFUL，^e: 计数 0
./gradlew :app:testDebugUnitTest                   # BUILD SUCCESSFUL（16s）
./gradlew :app:lintDebug                           # FAILED：5 errors，全部是存量
#   ImagePreviewDialog.kt:72,79 / SettingProviderPage.kt:504,512,517
#   （LocalContextGetResourceValueCall，本轮没碰这两个文件）

grep -rn "MochiListRow("      --include=*.kt app/src/main | wc -l   # 17
grep -rn "MochiListSection("  --include=*.kt app/src/main | wc -l   # 13
grep -rn "MochiChip("         --include=*.kt app/src/main | wc -l   # 5
grep -rn "MochiCarousel("     --include=*.kt app/src/main | wc -l   # 1
grep -rn "MochiTitleBar("     --include=*.kt app/src/main | wc -l   # 6
grep -rn "ExploreSearchTopBar"           --include=*.kt app/src/main | wc -l  # 0（已删）
grep -rn "HorizontalMultiBrowseCarousel" --include=*.kt app/src/main | wc -l  # 0（已换）
```

顺带修掉一个**存量编译阻塞**：`khatkit/.../hub/HubClient.kt` 用 `ByteReadChannel.readAvailable`
但没有 import，`:app:compileDebugKotlin` 在动手前就是红的 —— 补 `import io.ktor.utils.io.readAvailable`。

## 7. 还没 Mochi 化的地方（下一轮清单）

1. **角色子页表单**：`Md3OutlinedTextFieldWithSlots` 系列 → 需要 Mochi 的
   `MochiListTextFieldRow` / `MochiTextField`（JSON 高亮那一处要保留 MD3）。
2. **扩展页顶部 tab**：`KedgeTabPageScaffold`（`SecondaryTabRow`）。Mochi 无顶部 tab 组件。
3. **其余页面的卡片栈**：仍有 `MochiOptionItem`（Kk 壳）作为列表行，例如
   `CardGroup` 的 30+ 调用点；它们是「槽位式行」，Mochi 的字符串式 `MochiListRow` 需要逐页改写。
4. **探索页浮层**：`MochiModalBottomSheet` → `MochiSheet`（state 驱动）。
5. **统计页骨架**：仍是自绘脉冲块，可换 `MochiSkeleton`。
6. **列表卡片样式开关**：`listCardStyle()` 仍在探索页样式常量里被引用（`ExploreShape`），
   分组已换成 Mochi 分区后这些常量大多只剩骨架条在用。

## 8. 不要重犯

- 不为了「符号归零」把有状态的组件换成自绘件（P4 教训，见
  `docs/mochi-migration-plan.md` §6）：这一轮只替换**Mochi 库里语义等价**的组件，
  圆角/按压/动画/无障碍由组件给。
- 页面里不写外观分支；主题只有 `MochiTheme` 一棵树。
