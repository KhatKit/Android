# Mochi 底栏分页：隔离导航改动编译/单测取证

日期：2026-11-28。用途：在主工作树被他人 i18n WIP 阻断时，用只含**本次导航改动 hunk** 的干净 HEAD worktree 做同等编译/单测，证明导航改动本身可编译、且 app 单测失败集仍是既有 9 项基线。仅写文档与隔离 worktree，不改主树产品代码。

## 1. 主树外部 WIP 监控（未修改）

- 起点 19:35:32，按 15s 采样 `values/strings.xml`、`values-zh/strings.xml` 与近期 Kotlin mtime；WIP 编写期间持续变化。
- 期间两次自动尝试 `./gradlew :app:compileDebugKotlin`：19:36:47、19:40:19 均 **exit 1**，均为 `values/values-zh strings.xml` 的 `reasoning_picker_title` 重复定义（同键 2 次），非导航代码错误（/tmp/t15-monitor.log、/tmp/t15-compile-monitor.log）。
- 到 19:44 仍 stable=0（Kotlin 文件持续修改）；t14 已在 19:18–19:32 尝试 6 次，均被同一外部 WIP 阻断。故按 amended 合同转入 §2 隔离取证。
- 全程未改 values/strings.xml、未改任何他人 WIP、未暂存/提交。

## 2. 隔离 worktree（只应用导航 hunk）

- 位置 `/home/heizige/文档/Android/Project/KhatKit-isolated`，detached HEAD `fe3b9996d`（本任务期间 HEAD 从 76921bc04 前进 1 个无关提交）；子模块 `material3/material-color-utilities` 固定 `6fd88eb3e95ba1d457842e2a2bf847d06b3a018a`。
- 应用文件（11 个）：`khatkit-ui/.../PagedNodeTreePage.kt`、`KhatKitPage.kt`；`app/.../ui/shell/MochiNavPages.kt`（新）；ImgGenPage、PromptPage、WorkspaceDetailPage、SettingSpeechPage、SettingModelPage、SettingProviderDetailPage、ProviderModelList；`gradle/libs.versions.toml` 仅 `mochi = 0.4.0-khatkit.3`。
- 按 hunk 过滤：只取 pager→`MochiNavPages`/`MochiNavPagesState`、`MochiNavPageReselectHandler`、可选 `LazyListState` 等导航 hunk；**排除** icon 包重排、OCR `OcrPluginRepository`、i18n `stringResource`、`RouteActivity`/`NodeTreeRenderer`/`UiRegistry`/build.gradle 的他人 WIP。SettingModelPage 仅底栏/Pager 与 reselect，不带 OCR。
- 隔离树唯一适配：PagedNodeTreePage 原调用未跟踪的 i18n 帮助文件 `localizedErrors`（`khatkit/.../UiFieldLocalization.kt`，他人 WIP），隔离树改回 HEAD 的 `errors(values)`；导航结构/Depth 转场调用未改。此适配只存在于隔离树。

## 3. 结果（隔离树，导航改动等价编译/测试）

| 命令 | 退出码 | 结果与证据 |
|---|---|---|
| `./gradlew :app:compileDebugKotlin` | **0** | BUILD SUCCESSFUL（/tmp/t15-iso-compile2.log:209；最终增量编译 7s，139 tasks up-to-date） |
| `./gradlew :app:testDebugUnitTest :khatkit-ui:testDebugUnitTest` | **1** | app **1167 tests / 9 failed / 0 error / 0 skipped**；khatkit-ui **13 / 0 / 0 / 0**（/tmp/t15-iso-tests.log:416,:431） |

9/9 基线比对：与 t13 在无改动 HEAD `76921bc04` 的基线清单（`/tmp/t13-baseline-message-compare.json`）按测试名+消息逐条比较，**新增失败 0、消失失败 0、消息 9/9 一致**；失败仍为外部卡片仓 main.js/limits、AppDatabase 38 vs 37、PrimaryBottomSheet 46 vs 45、sensenova-color.svg 五类基线，与导航/Mochi 依赖/偏好页无关。隔离树 `git status` 仅上述 11 个导航文件。

## 4. 边界

- 主树当前仍无法直接跑同一命令（外部 i18n WIP 未稳定）；本结果证明的是**导航改动 + 固定 .3 依赖**在干净 HEAD 上可编译、失败集无新增/消失，不表示主树已绿。
- 未修改 9 项基线、未修改主树产品代码、未暂存/提交、未跑设备/模拟器/APK。
- 编译/JVM 单测不替代真机 Depth 手势/状态/外观验收；人工清单仍见 [mochi-tab-swipe-verification.md §5](mochi-tab-swipe-verification.md#L77)。
- 隔离 worktree 保留供 t16 复核；主树监控日志 /tmp/t15-monitor.log，隔离日志 /tmp/t15-iso-compile2.log、/tmp/t15-iso-tests.log。
