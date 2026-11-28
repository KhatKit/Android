# Mochi 底栏分页：HEAD 单测基线取证

日期：2026-11-28。用途：证明 t4 记录的 9 项 app JVM 单测失败在**未改动的 HEAD** 上同样存在，不属于本次 Mochi Depth 迁移引入。本任务只取证，未修改产品代码、未暂存/提交、未跑设备或 APK。

## 1. 基线对象与命令

| 项 | 值 |
|---|---|
| HEAD | `76921bc04`（fix(ui): 修复状态栏系统遮罩影响模糊） |
| 基线 worktree | `/home/heizige/文档/Android/Project/KhatKit-baseline`（detached HEAD；`git status --porcelain` 为空） |
| 子模块 | `material3/material-color-utilities` 检出 `6fd88eb3e95ba1d457842e2a2bf847d06b3a018a`（首次未 init 时 :material3 编译报 unresolved `dynamiccolor`；初始化后重跑，不涉及代码差异） |
| 命令 | `./gradlew :app:testDebugUnitTest :khatkit-ui:testDebugUnitTest` |
| 结果 | **退出 1**；app **1166 tests / 9 failed / 0 error / 0 skipped**；khatkit-ui **13 / 0 / 0 / 0**；BUILD FAILED in 1m 52s |
| 日志 | /tmp/t13-baseline-sibling.log:642（1166 tests completed, 9 failed）、:657（BUILD FAILED） |

说明：HEAD 的 [libs.versions.toml:78](../gradle/libs.versions.toml#L78) 仍是 `0.4.0-khatkit.2`，实现树为 `.3`。9 项失败均与 Mochi 无关，按测试名与消息逐项对照。首次在 /tmp 建 worktree 时 9 个失败**测试名一致**，但 BrowserExampleCardsTest 的 3 项因相对路径 `../../../KhatKitCards` 在 /tmp 下失效而消息不同；改到与主仓同级的路径后重跑，9/9 失败消息逐字一致（/tmp/t13-baseline-message-compare.json）。

## 2. 基线 9 项失败（HEAD 原样）

| # | 测试（class::test） | 基线消息 |
|---|---|---|
| 1 | BrowserExampleCardsTest::price comparison preserves sources sorts prices and closes every tab after failure | FileNotFoundException: ../../../KhatKitCards/browser_compare_prices/main.js |
| 2 | BrowserExampleCardsTest::example manifests and source declarations pass validator | JsonDecodingException: unknown key 'limits'（外部 card.json） |
| 3 | BrowserExampleCardsTest::checkin verifies result and releases tab on success and failure | FileNotFoundException: ../../../KhatKitCards/browser_checkin/main.js |
| 4 | CardRunsSchemaTest::migrationDdlAndRegistrationStayInSync | java.lang.AssertionError |
| 5 | ConversationGroupCardsSchemaTest::migrationIsRegisteredInFactoryAndNotAutoMigrated | AppDatabase 版本必须是 37 |
| 6 | BottomSheetScrollSourceGuardTest::scan_stillFindsSelfScrollingCallSites | PrimaryBottomSheet 调用点 expected:<46> but was:<45> |
| 7 | PrimaryBottomSheetIconSourceGuardTest::callSiteScan_stillCoversTheWholeRepo | PrimaryBottomSheet 调用点 expected:<46> but was:<45> |
| 8 | AIIconAssetsTest::目录下所有 svg 都是单色 | sensenova-color.svg 出现 2 种色值 |
| 9 | AIIconAssetsTest::资源目录里不再有 -color 后缀的文件 | sensenova-color.svg 应已被单色版替换 |

4 类归因见 [mochi-tab-swipe-verification.md §2.2](mochi-tab-swipe-verification.md#L36)：外部卡片仓状态、AppDatabase 38 vs 测试期望 37、PrimaryBottomSheet 46 vs 45、sensenova-color.svg 非单色。本文不重复归因，只核对“基线同一性”。

## 3. 与迁移后实现树对照

对照来源为主工作树 build/test-results 中最近一次完整结果（app XML mtime **2026-11-28 19:06:43**）；当时 t2/t3 源文件均早于该时间（[PagedNodeTreePage.kt](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/PagedNodeTreePage.kt#L221) 18:33、[KhatKitPage.kt](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/KhatKitPage.kt#L92) 17:47、[MochiNavPages.kt](../app/src/main/java/heizige/kk/khatkit/app/feature/ui/shell/MochiNavPages.kt#L64) 16:37），即结果包含全部迁移改动。

| 模块 | 基线 HEAD | 实现树 19:06:43 | 对照结论 |
|---|---|---|---|
| app | 1166 / 9 failed / 0 error | 1167 / 9 failed / 0 error | 新增失败 **0**，消失 **0**；9/9 失败名与消息逐字一致 |
| khatkit-ui | 13 / 0 / 0 | 13 / 0 / 0 | 一致 |

app 总数 1166 → 1167 多出的 1 项是工作树新增且**通过**的 `PageBackInterceptorTest::predictiveOverlayIsScopedAndUsesTheRegisteredHandler`（新文件 `app/src/test/.../PageBackInterceptorTest.kt`，已 staged），不是失败。逐项集合比较见 /tmp/t13-failure-compare.json 与 /tmp/t13-baseline-message-compare.json。

**结论：9 项失败全部在无改动的 HEAD 上复现，本次迁移新增失败 0、消失 0。**

## 4. 当前主工作树状态（给 t14 的边界）

- 19:09 另一路 i18n WIP 修改了 [ProviderConfigure.kt](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/ProviderConfigure.kt#L468) 与 [ProviderAccountSection.kt](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/ProviderAccountSection.kt#L85)（mtime 19:09）；19:13/19:14 复跑 `:app:compileDebugKotlin` **退出 1**（4 处 COMPOSABLE_INVOCATION，日志 /tmp/t13-main-compile-recheck.log:231-234）。
- 该失败不在 t13 范围，本次未修、也未回退；因此当前主树暂时**不能**直接再跑同一单测命令。t14 需等该 WIP 稳定后复跑；19:06:43 那轮已证明迁移代码本身可编译并跑完测试。
- 复跑基线：`cd /home/heizige/文档/Android/Project/KhatKit-baseline && ./gradlew :app:testDebugUnitTest :khatkit-ui:testDebugUnitTest`（worktree 保留未删）。

## 5. 未执行 / 不声明

- 未修改产品代码、测试断言或依赖；未 `git add` / commit / push（除他人已 staged 的新测试外，本任务未动 index）。
- 未跑设备、模拟器、androidTest，未 assemble/APK，未触发发布。
- JVM 单测与本文只证明失败集基线同一，不替代真机上的 Depth 转场、取消手势、状态保留与外观/大字号/宽窄屏验收；相关人工清单仍见 [mochi-tab-swipe-verification.md §5](mochi-tab-swipe-verification.md#L77)，全部未勾选。
