# 卡片真机验证记录

2026-11-01（手机日期），PKG110 / Android 16 / arm64-v8a。
被测包为 `heizige.kk.khatkit.debug`，版本 2.5.5（190）。
覆盖安装保留原有数据，测试结束后已回到应用主界面。

## 结果

8 个场景均取得通过记录，无跳过。首轮 5 项通过，另 3 项因测试 JavaScript
漏写 `return` 导致返回值断言失败；修正测试脚本后，3 项复测全部通过。
随后补充“第二张卡片确实进入队列”的断言，并接入正式主题，再次执行这 3 项，全部通过（5.24 秒）。

| 场景 | 真机断言 | 结果 |
|---|---|---|
| 原生 Lua/JS UI 并发 | 计算名额设为 1；A 显示确认框、B 确实已排队时，第三张原生卡片返回 42；依次点击后 A/B 都收到 true | 通过 |
| 独立取消 | A 显示、B 排队时取消 A，B 的真实弹层继续显示并成功回传 | 通过 |
| WASM UI 等待 | 实际 MoonBit 二进制等待操作屏输入时，后台原生 JS 卡片返回 42；输入便笺并点击保存后 WASM 收到内容 | 通过 |
| ARM64 WASM JNI 与小组件 | 实际 daily_note 二进制执行 run/widget，测试 Store 保持一致，ui.show 返回规范化 Column | 通过 |
| Lua 沙箱及原生循环取消 | 无 io/os/文件加载器；取消 Lua、JS 纯循环后，同一计算名额能执行下一张卡片 | 通过 |
| 手机直接访问线上 Hub | 通过手机网络读取索引，下载线上卡片到独立缓存，验证 SHA-256、清单名称/版本和 UI 声明 | 通过 |
| 原生 Lua 节点树注入 | 既有 NodeTreeSmokeTest 的 JNI 树结构与字段验证 | 通过 |
| Lua/JS 小组件刷新 | 既有 WelcomeWidgetFrameTest 的刷新事件往返验证 | 通过 |

测试类位于
[CardRuntimeDeviceTest.kt](../app/src/androidTest/java/heizige/kk/khatkit/app/core/data/ai/tools/CardRuntimeDeviceTest.kt)。
UI 请求使用应用真实的 UiBridgeHost 和 KhatKitUiHost 渲染器；测试仅读取私有队列来确认 B 已经排队，
没有为测试增加生产 API。WASM 使用独立测试 Store，未写入用户便笺。

## 构建范围

主工作区同时存在进行中的界面改动，使用 `14842154188ffa922ff1ab5b1476f82be26bfa31`
快照在独立工作区构建 APK，保留主工作区的改动。
旧 C1 群聊仪器测试仍调用旧 UsageRecorder API，无法编译；
独立工作区把这些测试移动到保留目录，测试 APK 仅编译本次专项测试和两个相关既有 UI 测试类。
本记录不代表整个 App 仪器测试套件通过。

线上索引在检查时仍未返回新增的 `hasUi` 字段；客户端已通过下载后的 manifest
正确识别 UI 声明。本次没有部署服务端，因此不代表新服务端协议已上线。
WASM fixture 由本地 MoonBit 产物推送到手机，线上下载测试验证的是线上现有卡片包。

## 原始证据

本机独立工作区：`/home/heizige/.cache/khatkit-device-verify.TEKLk7`。

- [首轮 8 项日志](/home/heizige/.cache/khatkit-device-verify.TEKLk7/device-results.log)
- [修正 JS 返回值后的 3 项日志](/home/heizige/.cache/khatkit-device-verify.TEKLk7/device-retest-results.log)
- [补充实际排队断言后的最终 3 项日志](/home/heizige/.cache/khatkit-device-verify.TEKLk7/device-final-ui-results.log)
- [A/B 等待时后台已完成](/home/heizige/.cache/khatkit-device-verify.TEKLk7/evidence/card-runtime-device-evidence/background_finished_while_A_and_B_wait.png)
- [回答 A 后显示 B](/home/heizige/.cache/khatkit-device-verify.TEKLk7/evidence/card-runtime-device-evidence/B_visible_after_A_answer.png)
- [取消 A 后 B 保持可用](/home/heizige/.cache/khatkit-device-verify.TEKLk7/evidence/card-runtime-device-evidence/B_survives_A_cancellation.png)
- [WASM 操作屏等待时后台已完成](/home/heizige/.cache/khatkit-device-verify.TEKLk7/evidence/card-runtime-device-evidence/WASM_screen_waits_while_native_background_finishes.png)

测试使用 AndroidJUnitRunner，通过 ADB 指定上述类或方法执行；测试 APK 为
`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`，
主 APK 为 `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`。
