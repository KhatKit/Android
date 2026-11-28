# 最终验证报告（t53 round-3，/tmp 隔离副本）

> 结论：**通过（绑定快照 5cb59c1da）**。四条 Gradle 命令在隔离副本内串行执行全部 exit 0；资源类与非资源类
> lint Error 均为 0。**注意**：验证结束后源树被并行提交推进到 `23ad0c14b` 且出现 1 处未提交空白
> （`AssistantPage.kt:643`），交付前必须在最终树复跑 `git diff --check`；本报告的通过结论仅绑定下述副本。

## 1. 快照与副本（FROZEN=yes）

| 项 | 值 |
|---|---|
| 复制源 HEAD | `5cb59c1da8ba07f816ceec6bcef36833c244eb8d`（fix(lint): Composable 内资源取值改用 LocalResources/LocalConfiguration） |
| 复制源 dirty | `1`（仅未跟踪 `.tmp-anchor-check.py`；无 tracked 修改） |
| 复制源 status sha256 | `f0c8b4701f31cea40b3db15d0da52aaf43761300b81bcdef509cbce0a87bafc6` |
| 快照时间 | 2026-11-28T22:29:12+08:00 |
| 副本目录 | `/tmp/khatkit-verify-20261128-222912`（190M，创建于 22:29:13） |
| rsync 排除 | `.git`、`build`/`*/build`、`.gradle`、`.kotlin`、`target`/`*/target`、`.idea`、`.agents` |
| 副本源文件校验（sha256sum 汇总） | `e1a6190352774979665aa66e1ca6c8195712dea46b91027864805bf785a0a090`（**构建前后一致**；构建只新增 `.gradle/*.properties` 缓存，已从校验口径排除） |
| 副本内 src 文件 mtime | 构建窗口内无变化（`find */src/* -newermt 22:29:15` 为空） |

验证期间源树前进了 `5cb59c1da → 23ad0c14b`（docs(sponsors)）并出现未提交改动；因所有构建都在副本内执行，
结论不受源树后续写入影响（这也是要求复制的原因）。副本保留供复核。

## 2. 命令与真实退出码（副本内串行，22:29:19→22:41:04）

日志目录 `/tmp/t53a4/`。

| 命令 | exit | 耗时 | 摘要 |
|---|---:|---:|---|
| `./gradlew --console=plain :app:compileDebugKotlin` | **0** | 132s | BUILD SUCCESSFUL，143 tasks executed |
| `./gradlew --console=plain :app:testDebugUnitTest` | **0** | 34s | BUILD SUCCESSFUL，265 tasks（app 侧 1193 tests） |
| `./gradlew --console=plain test` | **0** | 16s | BUILD SUCCESSFUL，388 tasks；全仓 **1992 tests / 0 failed / 21 skipped**（299 个结果文件） |
| `./gradlew --console=plain :app:lintDebug` | **0** | 523s | BUILD SUCCESSFUL；**0 errors**、2496 warnings、6 hints |
| `git diff --check` | 见 §3 | — | 快照状态无 tracked diff ⇒ 0（副本按合同无 `.git`，无法在副本内跑） |

总耗时 705s（含 lint 8m42s）。

## 3. git diff --check 的口径与当前树尾巴

- 副本按要求排除 `.git`，`git diff --check` 无法在副本内执行；快照状态 = commit `5cb59c1da` 的全树 +
  1 个未跟踪 scratch 文件，**没有任何 tracked 修改**，因此该状态的 `git diff --check` 恒为 0（由构造保证），
  与构建结果一一对应。
- **交付前必须在最终树复跑**：当前源树 HEAD `23ad0c14b` 有未提交修改
  `app/src/main/java/heizige/kk/khatkit/app/feature/assistant/AssistantPage.kt:643: new blank line at EOF`，
  `git diff --check` 现为 **exit 2**。这是快照之后的并行编辑，不属本副本内容，但会让最终交付的 diff 检查失败；
  由该文件所属批次作者删掉行尾空行后复跑即可（1 行，不涉及逻辑）。

## 4. 资源统计（与合同口径一致）

| 检查 | 结果 |
|---|---|
| app `values/strings.xml` | **3037** 条目（3018 string + 17 plurals + 2 其它），唯一名 3036 |
| app `values-zh/strings.xml` | **3037** 条目，唯一名 3036 |
| en↔zh 键差集 | **0 / 0** |
| 同类型重复键 | **0**（string/plurals 各自无重复） |
| 同名跨类型 | 仅 `setting_trigger_workflow_nodes`（string + plurals，aapt2 合法，`packageDebugResources` 通过） |
| 第一方 res XML 解析 | **87** 个文件，0 失败 |
| 资源类 lint | ExtraTranslation 0、StringFormatMatches 0、MissingQuantity 0、MissingDefaultResource 0 |

## 5. 9 项历史单测失败逐条复核（本副本实跑）

| # | 测试类 | 结果 |
|---|---|---|
| 1–3 | `BrowserExampleCardsTest` | tests=3 / failures=0 / errors=0 / **skipped=3**（外部卡片仓路径缺失自跳过；不再失败） |
| 4 | `CardRunsSchemaTest` | tests=2 / failures=0 / errors=0 → 通过 |
| 5 | `ConversationGroupCardsSchemaTest` | tests=7 / failures=0 / errors=0 → 通过 |
| 6 | `BottomSheetScrollSourceGuardTest` | tests=3 / failures=0 / errors=0 → 通过 |
| 7 | `PrimaryBottomSheetIconSourceGuardTest` | tests=3 / failures=0 / errors=0 → 通过 |
| 8–9 | `AIIconAssetsTest` | tests=4 / failures=0 / errors=0 → 通过 |

全仓 1992 tests 的模块分布：app 1193、khatkit 288、ai 250、card-validator 88、highlight 53、speech 43、
workspace 21、search 15、khatkit-ui 13、common 10、mediapicker 9、yunx-parser 4、oauth 2、
document/material3/web 各 1。

## 6. 范围外生产修复复核（t53 只读结论）

| 项 | 位置 | 复核结论 |
|---|---|---|
| `ResponseAPI.kt` reasoning effort 回退 | `ai/src/main/java/heizige/kk/khatkit/ai/provider/providers/openai/ResponseAPI.kt:247-252` | 生产行为变更：有自定义 `reasoningEffort` 映射且返回 null 时不再回退到 `level.effort`（注释说明「null = 明确不发」）。逻辑自洽，但**没有专门单测**覆盖该分支（仅 GrokProviderTest 间接触及）→ 建议补一条用例并纳入独立评审；与本重构目标无关，属顺带修复。 |
| `UiWidgetVocabulary.kt` 新增节点 | `card-validator/src/main/java/heizige/kk/khatkit/card/UiWidgetVocabulary.kt:13,18` | 新增 `Group/ListRow/Stepper/Selector/ChipRow/Dropdown` 到校验白名单；渲染端 `khatkit-ui/.../NodeTreeRenderer.kt:385,393,477-480` 与 `khatkit/.../NodeTree.kt:70-83` 均已实现 → 无「白名单放行但无法渲染」的幻影节点，一致。 |
| `khatkit/build.gradle.kts` 测试配置 | `khatkit/build.gradle.kts:18-23` | 开启 `unitTests.isReturnDefaultValues = true`（android.jar 桩返回默认值）。属测试严格度调整而非断言修改；理由注释充分（Log.i 桩抛 RuntimeException），但会掩盖其它桩异常 → 记录为已知取舍，建议在 CI 说明中保留。 |
| `RouteActivity.kt` PageBack 序号 | `app/src/main/java/heizige/kk/khatkit/app/RouteActivity.kt:907,928,961,979` | `PageBackInterceptor` 增加显式单调序号 `order`，替代 `SnapshotStateMap` 的哈希迭代顺序，保证「后登记优先」契约与 `PageBackInterceptorTest` 稳定；行为契约未变，属测试稳定性修复。 |

## 7. 边界

- 未运行 `assembleDebug/assembleRelease`；未操作设备/模拟器；未 `git add/commit`；未改任何源码（本任务只写本文件）。
- 只跑合同指定的 `:app:lintDebug`；其它模块 lint 未在本次范围内（如需：`:khatkit:lintDebug` 等）。
- 未做设备级 UI 验收；人工清单沿用 `docs/refactor-navigation.md`、t10/t12/t33 交接清单。
- 副本 `/tmp/khatkit-verify-20261128-222912` 与日志 `/tmp/t53a4/` 保留供复核。

## 8. 放行条件与残余项

1. 删除 `AssistantPage.kt:643` 的行尾空行 → 在最终树复跑 `git diff --check`（必须 0）。
2. 最终树若再次变化，需以同一口径新建副本重跑四条命令（本次全套约 705s）。
3. 通过后本报告可作为 t13/t45 的放行证据；本报告绑定 HEAD `5cb59c1da` 的内容快照。
