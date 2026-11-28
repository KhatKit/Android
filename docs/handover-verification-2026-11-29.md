# 交接续接验证（2026-11-29）

**结论：编译、App JVM 测试、全仓 JVM 测试、App lint 和 diff 检查全部通过。**
结论绑定下述代码提交，不包含设备验收。

## 基准

- 代码 commit：`6e7ccfdc6366e13a0505bc6121dd1c2b67818c3f`。
- tree：`f8c978f82c1a8576eb5e8a540e91effc8b017d60`。
- B 独立提交：`7e829e641`；A 独立提交：`6e7ccfdc6`。
- 隔离 worktree：`/tmp/khatkit-handover-verify.ltTPsO`，构建前 `git status --short` 为空。
- 前置：初始化 pinned `material3/material-color-utilities` 子模块并复制 `local.properties`。
- 日志：`/tmp/khatkit-handover-checks/`；每条 Gradle 命令串行执行。

主工作树提交前另外执行 `:app:compileDebugKotlin`，exit 0、42 秒；
日志 `/tmp/khatkit-handover-main-compile.log`。该结果未替代下表的隔离验证。

## 隔离验证结果

所有 Gradle 命令使用 `./gradlew --console=plain`。

| 任务 | 实际退出码 | 耗时 | 结果 |
|---|---:|---:|---|
| `:app:compileDebugKotlin` | 0 | 95s | BUILD SUCCESSFUL，143 tasks executed |
| `:app:testDebugUnitTest` | 0 | 33s | 1201 tests，0 failures / 0 errors / 3 skipped |
| `test` | 0 | 11s | 全仓 2003 tests，0 failures / 0 errors / 21 skipped |
| `:app:lintDebug` | 0 | 501s | 0 errors / 2553 warnings / 6 hints |
| `git diff --check` | 0 | — | 隔离树无修改，HEAD/tree 与构建前一致 |

测试数量从该隔离目录新生成的 `TEST-*.xml` 的 tests/failures/errors/skipped 属性汇总：
App 167 个 suite，全仓 301 个 suite。`VirtualDisplayProtocolTest` 的 8 个用例全部通过，
覆盖明确退出标记、失败/超时、命令能力缺失、非法名称、主屏 ID、歧义/溢出 ID 和退出码包装。
余额测试保留原断言，旧包导入问题已修复。

修改的 app/khatkit 中英文资源 XML 通过 `xmllint --noout`。本轮新增资源为 app 20 对、
khatkit 13 对；其它语言使用默认语言回退。

lint XML 中 `ExtraTranslation` / `StringFormatMatches` / `MissingQuantity` /
`MissingDefaultResource` 的 Error/Fatal 均为 0；仍有 1856 条 `MissingTranslation` 警告，
包含其它语言缺少新键的默认语言回退，未将警告计为已消除。
构建后隔离 worktree 的 `git status --short` 仍为空。
最终文档归档提交仅修改 docs，代码继续绑定 `6e7ccfdc6`。

## 放行范围

此报告只验证代码可编译及 JVM/静态检查，不能证明后台应用操作的平台前提成立。
官方 AOSP 13–16 没有当前实验协议的创建/销毁命令；能力探测通过也不证明厂商后端兼容。
固定源码证据与设备人工清单见 [平台核查](background-display-platform-review.md)。

没有执行 `assembleDebug` / `assembleRelease`、adb、模拟器、connectedAndroidTest 或真机操作。
原有未跟踪 `.tmp-anchor-check.py` 保留在主工作树，未进入本次提交或隔离验证。
