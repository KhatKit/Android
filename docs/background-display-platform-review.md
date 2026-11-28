# 后台应用会话：平台核查与人工验收（2026-11-29）

## 结论

当前会话接线使用的 `cmd display create-virtual-display` /
`destroy-virtual-display` **不是 Android 13–16 AOSP 标准命令**。本轮仅交付带能力探测的实验入口：
系统没有同时声明创建与销毁命令时，返回“不支持”，不创建显示、不启动目标应用。
不能据此宣称“不占主屏操作 App”已经实现。

`cmd display help` 探测只检查命令名称，**不证明**厂商命令的参数、输出格式、存活机制或焦点隔离兼容。
即使探测通过，也必须在对应系统上完成下述人工验收，才能认定该设备可用。

## 固定源码证据

逐项检查官方 `DisplayManagerShellCommand.onCommand()` 的完整分派，均无上述两条命令：

| 分支 | 固定 commit | 源码位置 |
|---|---|---|
| android13-release | `d27f9960456403533af0a0d5d9f14cf71c9c8cf6` | [DisplayManagerShellCommand.java:39](https://android.googlesource.com/platform/frameworks/base/+/d27f9960456403533af0a0d5d9f14cf71c9c8cf6/services/core/java/com/android/server/display/DisplayManagerShellCommand.java#39) |
| android14-release | `6e47c7075b91983ae501114425ea25e6df7690c8` | [DisplayManagerShellCommand.java:39](https://android.googlesource.com/platform/frameworks/base/+/6e47c7075b91983ae501114425ea25e6df7690c8/services/core/java/com/android/server/display/DisplayManagerShellCommand.java#39) |
| android15-release | `cc8bb19595c661f5cf42f330457000e643c67d1b` | [DisplayManagerShellCommand.java:55](https://android.googlesource.com/platform/frameworks/base/+/cc8bb19595c661f5cf42f330457000e643c67d1b/services/core/java/com/android/server/display/DisplayManagerShellCommand.java#55) |
| android16-release | `99b01a65cc4c104933788b3143285ab6bae65827` | [DisplayManagerShellCommand.java:55](https://android.googlesource.com/platform/frameworks/base/+/99b01a65cc4c104933788b3143285ab6bae65827/services/core/java/com/android/server/display/DisplayManagerShellCommand.java#55) |

源码可通过 Gitiles `?format=TEXT` 获取并 base64 解码复核。分支名只是检索起点，结论绑定表中 commit。

### 不采用 overlay 回退

`settings put global overlay_display_devices ...` 创建的是主屏上的模拟副屏窗口：

- [OverlayDisplayWindow.java:110](https://android.googlesource.com/platform/frameworks/base/+/cc8bb19595c661f5cf42f330457000e643c67d1b/services/core/java/com/android/server/display/OverlayDisplayWindow.java#110) 获取默认显示的 WindowManager，`show()` 调用 `addView()`。
- [OverlayDisplayWindow.java:219](https://android.googlesource.com/platform/frameworks/base/+/cc8bb19595c661f5cf42f330457000e643c67d1b/services/core/java/com/android/server/display/OverlayDisplayWindow.java#219) 使用 `TYPE_DISPLAY_OVERLAY`；窗口有移动/缩放交互，仍占用主屏显示和部分触摸区域。
- [OverlayDisplayAdapter.java:182](https://android.googlesource.com/platform/frameworks/base/+/cc8bb19595c661f5cf42f330457000e643c67d1b/services/core/java/com/android/server/display/OverlayDisplayAdapter.java#182) 设置变化先销毁既有模拟屏；修改该全局值还会影响用户已有配置。

因此不将此设置作为“不占主屏”的替代后端，本轮也不修改它。

## 本轮代码边界

- 单会话；创建前检查系统命令能力。
- 创建/销毁/按键 shell 保留明确退出码；超时、非零退出、错误文本、歧义显示 ID 均拒绝成功判定。
- 节点、手势、截图均绑定目标显示；虚拟屏滚动使用目标显示尺寸。
- 启动请求后等待目标显示出现应用节点，不能仅凭 `startActivity()` 没抛异常宣称成功。
- 销毁失败保留会话和显示绑定，允许再次 `background_stop`；无障碍服务断开后的清理在 IO 线程执行。
- 后台会话仅允许显示定向的返回键；桌面、最近任务、通知栏动作拒绝，避免触发全局系统界面。
- 进程被杀后的回收、厂商命令生命周期、目标 App 的多屏兼容性仍未验证。

## 人工验收（未执行）

按仓库约定，本轮没有连接或操作设备，也没有组装 APK。

### 改动点

`BackgroundAppSessionManager` 管理实验会话和资源化反馈；
`AccessibilityBridgeImpl` 将卡片操作绑定到目标显示；
`KhatKitAccessibilityService` 在断开/重连时通知会话管理器。

### 复现路径

安装通过 GitHub Actions 构建的 APK → 开启无障碍 → 授权 Shizuku 或 root →
在允许自动化工具的会话中调用 `khatkit__device_act`。
先用系统设置应用测试，随后再验收具体目标应用。

### 预期结果

- [ ] 原生 AOSP 或没有对应命令的系统：`background_start` 返回不支持，目标 App 没有启动，主屏不变。
- [ ] 用户拒绝启动授权：不执行创建或启动命令。
- [ ] 支持相应厂商命令的系统：启动成功结果包含非零 `display_id`，目标显示确实有目标 App 节点。
- [ ] `a11y_screen` 返回目标显示内容；点击、输入、滑动落在目标显示。
- [ ] 用户同时操作主屏，应用、焦点和触摸不被后台会话接管。
- [ ] 后台返回只作用于目标显示；桌面、最近任务、通知栏返回不支持。
- [ ] `background_stop` 成功后显示消失；失败则 `cleanup_pending=true`，再次停止可重试。
- [ ] 关闭或重连无障碍时没有主线程卡顿；回收失败仍保持目标显示绑定。

### 边界

- Android 13–16、具体厂商 ROM；需分别核对帮助输出和实际命令参数。
- 前台已有同包任务、`singleTask`、应用主动跳回主屏、输入法、多屏权限。
- 创建/启动/清理过程中撤销 Shizuku 或无障碍权限、服务重连、进程被杀。
- 窄屏/宽屏和不同显示密度的节点坐标、截图及滚动范围。
- 本轮不改变页面外观；浅深色、玻璃与字号沿用既有人工 UI 清单。
