# Mochi 取色器统一与手动验收

日期：2026-11-28。源码、自动检查和设备验收分别记账。

## 改动点

- 采用上游 `70636705c25b170d9424c9ea6669752ab8f6ece1`，依赖版本 `0.4.0-khatkit.4`。
- [ThemePickers.kt:186](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/theme/ThemePickers.kt#L186)：转发到 `MochiColorPickerSheet`，删除自绘 HSV 方块、色相条及历史色块。
- [SettingPreferencesThemePage.kt:527](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingPreferencesThemePage.kt#L527)：主色、辅色、第三色共 3 个入口采用 demo 的 `MochiListRow + MochiColorWell`；点行精确输入 HEX，点彩虹色环打开原生面板。
- [KkCompat2.kt:168](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/mochi/KkCompat2.kt#L168)：`SquareColorPicker` 转发到原生面板，透传透明度开关与关闭回调。
- [patch-mochi.py:127](../.github/scripts/patch-mochi.py#L127)：为原生取色器添加可选 header/footer 插槽，保留头像取消、使用、重置、HEX 输入；取色逻辑、材质、形状、手势仍来自库。
- 设置配色页和首次引导的 2 个原生 `MochiSeedPalette` 继续沿用；只读色样、头像预览、图片/屏幕取色 API 保持原有职责。

## 复现路径

1. 设置 → 外观 → 自定义主题 → 新建或编辑，分别操作主色、辅色、第三色。
2. 管理角色 → 任一角色 → 基本 → 头像底色。
3. 设置 → 外观 → 配色 → 自定义色；首次启动引导 → 主题色板。

## 预期结果（设备未执行）

- [ ] 各面板提供网格、色谱、RGB 滑块，色谱的色彩空间与上游 demo 一致。
- [ ] 主题点彩虹色环打开面板，三种颜色分别回显，预览随草稿改变。
- [ ] 点主题色值可输入 6/8 位 HEX；非法输入阻止确认并显示当前语言的错误。
- [ ] 单个取色面板关闭保留编辑草稿；外层取消不保存，外层保存后重进仍保留。
- [ ] 头像输入 `#80FF6600` 后拖动色谱/RGB，不透明度保持约 50%，确认写回 `#AARRGGBB`。
- [ ] 头像取消、下滑、点遮罩或系统返回均丢弃未确认草稿，再次打开显示保存值。
- [ ] 头像点使用保存；点重置恢复名称派生色，不写入黑色或空字符串代替默认值。
- [ ] HEX 子弹窗先消费返回；关闭子弹窗后仍能继续操作取色器。
- [ ] 莫奈自定义色在面板关闭后提交；预设和已有历史颜色继续可用。

## 边界（设备未执行）

- 浅色/深色；Mochi/iOS/Nothing；玻璃开/关。配色与材质由根主题决定。
- 动效减弱、系统大字号、窄屏/横屏；面板可滚动，按钮、HEX 和重置行不被遮挡。
- 半屏/全屏、键盘显示/隐藏、连续开关、预测性返回；确认及取消各只执行一次。
- 主题色不开放不透明度；头像开放不透明度，透明色由原生棋盘格展示。

## 自动检查

- 补丁版 Mochi 四个模块发布到 mavenLocal：已通过，退出码 0，未组装 APK。
- `:khatkit-ui:compileDebugKotlin :khatkit-ui:testDebugUnitTest`：退出码 0，4 个套件、13 个测试、0 失败。
- 英文/中文资源 XML、新增 key 唯一性及 `git diff --check`：已通过。
- 独立静态复核：0 阻断；确认/取消/重置/Alpha/HEX/主题草稿保存/外观继承均已核对。旧 HSL 纯解析辅助函数已无调用，未改动原有重构文件。
- 应用检查：独立应用输出目录下 `:app:compileDebugKotlin` 完成，取色相关文件无编译错误；同次后续 `:app:hiltJavaCompileDebug` 失败（Hilt 无法发现应用入口 `heizige.kk.khatkit.app.KhatKitApp`），完整命令退出码 1，应用 JVM 测试未能执行完。不能将此记录为应用整体构建或完整测试通过。
- 默认输出目录的首次复核在 KSP 生成目录缺失处失败，因此使用独立应用输出目录复核；未清理或重建工作区已有的应用输出目录。
- 未操作设备、未运行设备测试、未本地打包 APK；以上勾选项尚待手动验收。
