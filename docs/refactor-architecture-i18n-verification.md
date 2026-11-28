# 最终验证闸门报告（t13 attempt 2）

> 结论：**不通过（未放行）**。真实构建证据取自 t48（verification-round-2）本轮日志
> `/tmp/t48v2_cmd.log`：4 条 Gradle 命令全部 BUILD FAILED；因此 t13 不得作为绿灯，
> 也不替 t45/t48 出具通过结论。本任务**未运行 Gradle**（合同要求不重复 t48 全量构建、
> 不与源码并发），只做证据复核 + 静态独立复算。

## 1. 证据来源与稳定树

| 项 | 值 |
|---|---|
| 证据报告（t48 round-2，本轮唯一有效） | `docs/refactor-final-verification.md`（20:20:38 写入，结论未通过） |
| 证据日志 | `/tmp/t48v2_cmd.log`、`/tmp/t48v2_results.txt`（compile/appTest/test/lint/gitDiffCheck 五段） |
| HEAD（两次采样一致） | `d0982ab1a` |
| 工作树 dirty 文件数（两次采样一致） | 784 |
| `git status --porcelain \| sha256sum` | `0485f3bf…12f2c`（开始/结束相同） |
| `git diff --check` | **exit 0**（本任务独立执行，无空白错误） |

稳定树条件满足：t48 冻结窗口 20:16:55→20:18:43 与本次复核前后 HEAD/status 哈希一致；
但 t48 的结论是**未通过**，故“同一稳定树 + 通过”不成立，t48 未作废但结果为失败。

## 2. t48 真实退出码（引用其日志，非本任务执行）

| 命令 | 结果 | 失败点（日志行） |
|---|---|---|
| `./gradlew :app:compileDebugKotlin` | **BUILD FAILED**（24s，:473） | `:app:kspDebugKotlin`（:458）KSP PROCESSING_ERROR |
| `./gradlew :app:testDebugUnitTest` | **BUILD FAILED**（12s，:1066） | 同一 `:app:kspDebugKotlin`（:1051），未进入测试 |
| `./gradlew test` | **BUILD FAILED**（22s，:1810） | `:common:testDebugUnitTest` 1 个用例失败（:1490/1495）+ `:ai:compileDebugUnitTestKotlin` UNRESOLVED_REFERENCE（:1507/1508）+ `:app:kspDebugKotlin`（:1510） |
| `./gradlew :app:lintDebug` | **BUILD FAILED**（47s，:2683） | 同一 `:app:kspDebugKotlin`（:2662），未进入 lint 分析 |

资源合并已恢复：t48 的 compile 段中 `:app:packageDebugResources` / `:app:generateDebugRFile`
已执行成功（`/tmp/t48_cmd.log:94` 起），说明 t45 §3 的重复键阻断已被修复。

## 3. 阻断缺陷（需源码修复，t13 不代修）

### 3.1 app KSP：`ChatFloatingWindows` 无法解析（124 条同名错误）

```
e: [ksp] ... because 'heizige.kk.khatkit.app.feature.chat.ChatFloatingWindows' could not be resolved
```

| 文件:行 | 现状 | 目标 |
|---|---|---|
| `.../chat/di/ChatAssemblyModule.kt:115` | 返回类型 `feature.chat.ChatFloatingWindows` | `feature.chat.helper.ChatFloatingManager` |
| `.../chat/di/ChatAssemblyModule.kt:118` | 构造 `feature.chat.ChatFloatingWindows(` | `ChatFloatingManager(`（构造参数 context/appScope/tracker/lazyChatManager 完全对应） |
| `.../chat/di/ChatAssemblyModule.kt:123` | `ChatFloatingTracker(`（该类已不存在，全仓仅此 1 处） | `ChatFloatingWindowManager(`（`chat/helper`，构造参数 appScope/lazyChatManager/enabled 一致） |
| `app/src/main/java/heizige/kk/khatkit/app/KhatKitApp.kt:84` | `lateinit var chatFloatingWindows: feature.chat.ChatFloatingWindows` | 字段类型改 `feature.chat.helper.ChatFloatingManager`（字段名可留） |

`ChatFloatingManager.kt:36` 的 TAG 常量与 `KhatKitApp.kt:81` 注释只是文本，无需改。

### 3.2 其它仍未接线的旧符号（app 主源）

| 文件:行 | 旧 | 新 |
|---|---|---|
| `app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tools/KhatKitTools.kt:2020` | `AutomationTracer.BBoxDto` | `BBoxDto`（`feature.automation.data`） |
| `.../KhatKitTools.kt:2022` | `AutomationTracer.UiActionDto` | `UiActionDto`（`feature.automation.data`） |
| `.../KhatKitTools.kt:1846` | `UiAutomationSnapshotProvider.capture()` | `UiAutomationSnapshotHelper.capture()`（`feature.automation.helper`） |
| `app/src/main/java/heizige/kk/khatkit/app/feature/workflow/CreateWorkflowPage.kt:288` | `WorkflowLegacy.fromStored(draft.copy(...))` | `draft.copy(...).toVisualWorkflow()`（`feature.workflow.helper`） |

来源：`docs/refactor-automation-workflow.md` §5（t36 外部接线清单）。KSP 先失败，故 Kotlin
编译尚未暴露其它潜在未解析引用；修完需重跑以发现下一层。

### 3.3 模块测试

| 目标 | 缺陷 |
|---|---|
| `:common:testDebugUnitTest` | `ServerSentEventReaderTest > joins multiline data and ignores comments plus retry` FAILED（10 tests, 1 failed, 5 skipped，:1490/1493） |
| `:ai:compileDebugUnitTestKotlin` | `ai/src/test/java/heizige/kk/khatkit/ai/provider/providers/chatgpt/ChatGPTProviderTest.kt:151:35 Unresolved reference 'contextLength' on receiver of type 'Model'`（:1508） |

### 3.4 测试源（androidTest，非本次阻断但同批遗留）

`app/src/androidTest/java/heizige/kk/khatkit/app/feature/automation/AutomationToastTest.kt:238,239,240,284,288,289,296,297,298,317`
仍用 `AutomationBus`（应为 `AutomationManager`）。不影响 `:app:compileDebugKotlin`，但 `androidTest` 会失败。

## 4. 资源一致性（本任务静态独立复算，与 t48 结论一致方向）

| 检查 | 结果 |
|---|---|
| app `values/strings.xml` vs `values-zh/strings.xml` | 各 **3035** 条目（3018 string + 17 plurals，唯一名 3034）；key 差集 **0/0** |
| 同类型重复键（string/plurals 各自） | **0**（t45 §3 的 6+3 项已修复） |
| 同名跨类型 | 1 处：`setting_trigger_workflow_nodes` 同时是 `<string>`（en:2294/zh:2275）与 `<plurals>`（en:2339/zh:2319）；被两处引用（`SettingTriggersPage.kt:294` 用 plurals、`:313` 用 string）。aapt2 按类型建档，且 t48 的 `:app:packageDebugResources` 已通过 → **合法**，不作为缺陷 |
| 第一方 res XML 解析 | 85 文件，0 失败 |
| 模块 strings（common/khatkit/khatkit-ui/mediapicker/oauth/plugins:ssh/search/speech/workspace） | 无重复键告警（同名跨类型仅上述 1 处） |
| `git diff --check` | exit 0 |

## 5. 放行条件（交 captain/t47 后续）

1. 修 §3.1（3 处）、§3.2（4 处）、§3.3（1 个 common 用例、1 个 ai 测试引用），以及 §3.4 的 androidTest 旧符号。
2. 冻结工作树后重跑 4 条 Gradle 命令 + `git diff --check`，由 verification 任务出具新报告。
3. t13 只有在**新 t48 报告全部 exit 0** 且与同一 HEAD/status 快照绑定时才能完成；本轮不放行。

## 6. 未执行项与边界

- 本任务未运行 Gradle / assemble / androidTest / 设备；未改任何源码；只写本文件。
- 未做设备级 UI 验收（人工清单沿用 `docs/refactor-navigation.md`、t10/t12/t33 交接清单）。
- 逐项“非 UI 中文残留”复核未在本轮重做：t45 §4 已列 802 行/507 文件口径，需在各批次报告基础上抽验，
  本轮以“构建未通过”为由不宣称该项通过。
