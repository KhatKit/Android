# KhatKit UI 方案：MD3 骨架 + Mochi token/交互 + Kk 壳

> 状态：**U5b 收尾（2026-10-30）**。原「C 方案：全量迁移到 mochi-compose」在 P4 被判过度自绘，
> 改成本方案并已收尾：**Material3 负责骨架与组件，mochi-compose 只提供主题 token 与交互设施，
> 旧组件库以 `khatkit-ui/uikit/mochi/` 的 Kk 壳承接调用形态**。
> Kedge / Miuix / Khromia 三库已从依赖树与源码 import 中彻底删除。
> 真机批量回归（U5c）在设备恢复后执行，本文件不含真机结论。

## 1. 目标与非目标

**目标**

1. 全应用只剩一棵主题树：`MochiTheme` 提供颜色/字阶/圆角/系统栏图标对比度；
2. 骨架、列表、表单、弹层、反馈用 **material3**；交互设施（overscroll、触感、返回语义）用 Mochi；
3. 删除 Kedge / Miuix / Khromia，不再随上游 UI 漂移维护三套外观；
4. 行为等价优先：替换以状态矩阵（enabled/error/disabled/contentDescription/steps…）为验收。

**非目标**

- 不追求「零 material3」；MD3 是本方案的骨架层，不是过渡层；
- 不再把页面组件批量改写成业务自绘件（P4 教训见 §6）；
- 卡片运行时（Lua API/节点词表/上限）不动。

## 2. 现状快照（可复算，HEAD 6bd06fdde）

```bash
cd /home/heizige/文档/Android/Project/KhatKit
find app/src/main/java -name '*.kt' | wc -l                                            # 749
grep -rl 'import androidx.compose.material3' --include=*.kt app/src/main | wc -l       # 210
grep -rl 'import heizige.kk.kedge'   --include=*.kt . --exclude-dir=build | wc -l      # 0
grep -rl 'import top.yukonga.miuix'  --include=*.kt . --exclude-dir=build | wc -l      # 0
grep -rl 'import heizige.kk.khromia' --include=*.kt . --exclude-dir=build | wc -l      # 0
grep -n 'kedge\|khromia\|miuix' settings.gradle.kts gradle/libs.versions.toml | wc -l # 0
ls khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/mochi/*.kt | wc -l                # 13（Kk 壳）
```

APK（U5a 之后 debug，未压缩对比口径 = 同机同任务）：

| 产物 | 体积 |
|---|---:|
| app-arm64-v8a-debug.apk | **90,953,670 B** |
| app-universal-debug.apk | 107,760,833 B |
| app-x86_64-debug.apk | 91,713,927 B |

P0 基线 arm64 为 **94,756,786 B**，差值 **−3,803,116 B（−4.01%）**（U5c lint 修复 c1afc0136 之后复测）。

## 3. 依赖与版本矩阵

| 依赖 | 坐标 | 说明 |
|---|---|---|
| Compose BOM | `2026.09.00`（foundation/ui 解析 1.13.0-alpha01） | 骨架层 |
| mochi-compose / mochi-ios / mochi-icons | `io.github.feice11:*:0.2.0-khatkit` | **mavenLocal 本地源码产物**，见 §12 |
| kedge / khromia / miuix | — | **已删除**（U5a 删坐标与 includeBuild） |

`settings.gradle.kts` 不再有 `includeBuild(../Kedge|../Khromia)` 与 substitution；
`khatkit-ui`/`mediapicker` 的模块依赖同步删除。换机器/CI 需要先按 §12 发布本地 Mochi。

## 4. 分层与目录

1. **骨架层（material3）**：`Scaffold`/`TopAppBar`/`LargeFlexibleTopAppBar`/`ModalBottomSheet`/
   `AlertDialog`/`Switch`/`Slider`/`SegmentedButton`/`TextField`/`PermanentNavigationDrawer` 等。
2. **Token/交互层（mochi-compose）**：`MochiTheme`（colors/textStyles/shapes/systemBarAppearance）、
   `ProvideMochiOverscroll`、`MochiLargeTitleScaffold`、`MochiListSection`/`MochiListRow`、
   `MochiToastHost`/`MochiSheet`/`MochiPopover` 等。
3. **Kk 壳层（`khatkit-ui/uikit/mochi/`，13 文件 / 2469 行）**：保留旧调用形态、内部换 material3 或
   自写实现，避免全仓调用点重写：
   `KkCompat.kt` / `KkCompat2.kt` / `KkControls2.kt` / `KkEditDialog.kt` / `KkModifiers.kt`（fadingEdge）/
   `KkSwipeToDismiss.kt` / `KkToast.kt` + Mochi 组件封装 6 个（Containers/Controls/Lists/MaterialBridge/
   SearchAndBar/TopBars）。
4. **业务自绘层**：聊天气泡/输入区、Markdown/代码高亮、卡片运行时等保持既有实现。

## 5. 三库删除过程（提交可查）

| 库 | 做法 | 结果 |
|---|---|---|
| **Miuix** | U1a 删 28 个 `*Miuix*.kt` 变体与 `if (LocalKedgeStyle==Miuix)` 死分支；U1b 5 个共用 helper 迁 MD3；U1c 剩余 8 文件清零；U1d/U1e 删「界面风格」开关与 uiStyle 运行期分支 | import 36→0；设置页不再有风格开关 |
| **Kedge** | U4a 容器/顶栏/导航/搜索 → MD3；U4b 按钮/表单/选择控件 → MD3；U4c 列表/弹层/菜单/反馈 → MD3+Kk；U4d/U4e app/khatkit-ui/mediapicker 收尾 | import 203→0 |
| **Khromia** | U4f1 Toast 37 文件→KkToast、fadingEdge→自写 Modifier、FullscreenPopup→MD3 Dialog；U4f2 Segmented/开关/滑块/单选/弹层→Kk 壳/MD3 | import 72→0 |
| **依赖收口** | U5a `15829cab4`+`6bd06fdde`：删 includeBuild/substitution、libs 坐标、模块 api/implementation，清最后非 import 引用；`clean :app:assembleDebug` 通过 | 三库不再进入构建 |

## 6. 教训：P4 过度自绘（不要重犯）

P4 阶段把 Kedge 符号机械替换为自绘 Mochi 组件，符号是归零了，但**行为退化**：

- `MochiCard` 的 `colors` 参数「只收不用」，CardColors 语义丢失（卡片底色不再随调用方变化）；
- `MochiSlider` 无 contentDescription，且接收 `steps` 但不做吸附（SettingItem 旧行为有吸附）；
- `MochiTextField` 错误态/密码显隐/多行覆盖不全，只有基础输入路径；
- 卡片弹层 pager / 内部 nav / load_more / 预测性返回在 P4 验证时无证据（t19 failed）。

**处理**：U 系列回退到「material3 直替 + Kk 壳保留旧调用形态」，按行为等价验收；
后续迁移先列状态矩阵（enabled/error/disabled/contentDescription/steps/动画），
**不以「符号消失」作为阶段通过条件**。

## 7. 已知缺口与中间态

1. **MochiItem 能力缺口**：Mochi 的行组件没有 `enabled`/`trailing`/长按菜单，所以菜单类行仍保留
   `MochiMenuPopup`/`MochiMenuRow`（当前 16 文件 / 75 处引用；任务书写的「14 处」是更早快照）。
2. **抽屉模糊退回 MD3**：`ChatDrawer.kt:252-257` 把 backdrop 恒置 null —— `MochiGlassSurface` 的
   backdrop 采样在 NavDisplay 重建时不稳定，退化为不透明底色；视觉差异 = 顶栏/底栏没有背景模糊，
   只靠 `surfaceContainer` 层次色区分。
3. **预设主题中间态**：`PresetTheme` 仍有 48 处引用，设置入口已删、运行期不注入；后续 slice 统一清理。
4. **大屏适配缺口**：全仓仅 `ChatPage.kt:52,169` 一处 `currentWindowDpSize()`，Mochi 无
   WindowSizeClass/adaptive。建议后续 slice 引 `material3-adaptive` 的 `AdaptiveScaffold`：
   **≥840dp 侧栏 + 双栏 / 600–840dp 导航轨 / <600dp `MochiTabBar`**，先做设置与探索两页。
5. **真机批量回归未跑**：设备离线，清单见 §11，U5c 一次跑完并出三屏截图。

## 8. 视觉与交互硬约束

- 只允许一棵主题树：`MochiTheme`；页面里禁止 `if (ios)/if (glass)/if (miuix)` 外观分支；
- 系统栏图标对比度只由 `MochiTheme(systemBarAppearance=…)` 管，页面禁止 `isAppearanceLightStatusBars`；
- 返回单点接管：`RouteActivity` 的 `NavDisplay.onBack`；页面不挂 `BackHandler`/`PredictiveBackHandler`；
  页内浮层经 `PageBackInterceptor` 先消费；
- 列表/卡片层次用 material3 `surfaceContainer*`；不引入 Kedge/Miuix/Khromia；
- 新组件优先 material3；只有「Mochi 有且语义等价」时才用 Mochi，缺口进 §7 清单。

## 9. 测试与守卫

- `LegacyImportGuardTest`：白名单文件 `app/src/test/resources/legacy-import-whitelist.txt` 当前
  **kedge 0 / miuix 0 / khromia 0（条目 0）**；新增三库 import 直接失败；
- 源码护栏：`PrimaryBottomSheetIconSourceGuardTest` / `BottomSheetScrollSourceGuardTest` 等沿用；
- `:app:test` U5a 绿；**U5b lint：exit 0**（修复提交 `c1afc0136` 清零 8 个 error：
  SuspiciousIndentation ×3 / LocalContextGetResourceValueCall ×4 / UnusedContentLambdaTargetStateParameter ×1；
  独立 `:app:lintDebug --rerun-tasks` 5m42s 复跑通过）。基线 **929 warnings** 保留，属存量口径，不阻塞回归。

## 10. 验证命令

```bash
cd /home/heizige/文档/Android/Project/KhatKit
./gradlew clean :app:assembleDebug          # 0
./gradlew test                              # 0
./gradlew lint                              # 0（基线 warning 见报告）
grep -rl 'import heizige.kk.kedge'   --include=*.kt . --exclude-dir=build | wc -l   # 0
grep -rl 'import top.yukonga.miuix'  --include=*.kt . --exclude-dir=build | wc -l   # 0
grep -rl 'import heizige.kk.khromia' --include=*.kt . --exclude-dir=build | wc -l   # 0
grep -n 'kedge\|khromia\|miuix' settings.gradle.kts gradle/libs.versions.toml      # 空
stat -c '%s' app/build/outputs/apk/debug/app-arm64-v8a-debug.apk                    # 90,953,670
```

## 11. 待批量真机清单（U5c，设备恢复后一次跑完）

- **A U1/U5 复审**：设置→主题无「界面风格」行；MochiSwitch 动画正常；旧「渐变/流光」配置不崩且无入口；
  首页/聊天/设置/探索 4 页冒烟。
- **B 组件行为**：卡片弹层 pager / 内部 nav / load_more / 预测性返回；MochiSlider 拖动/点击定位/边界/
  吸附&contentDescription；MochiTextField 错误/禁用/密码显隐/多行。
- **C 未覆盖页面**：AssistantExtensions / AssistantRequest / Extensions / Prompt / ImgGen / Greeting /
  SettingProviderDetail（另 Speech / WebView / WorkspaceDetail）；ExploreMarket 卡片底色 vs P3 基线。
- **D P7 记账**：三屏（聊天/探索/设置）浅+深截图入库；APK 体积数字；删 `MochiSpikeActivity`（若仍在）。
- **E 回归**：深链 PROCESS_TEXT / ACTION_SEND；VIEW `rikkahub://shortcut` 偏差项；返回不双 pop。

## 12. 本地 Mochi 源码库（mavenLocal `0.2.0-khatkit`）

- 源码：`/home/heizige/文档/Android/Project/Mochi`（tag `v0.2.0` / commit `3f4eda8`）
- 发布：`cd ../Mochi && ./gradlew publishToMavenLocal -PVERSION_NAME=0.2.0-khatkit`
- 产物：`~/.m2/repository/io/github/feice11/mochi-{compose,ios,icons}/0.2.0-khatkit`
- 改库循环：改源码 → 重新发布 → `./gradlew :app:assembleDebug --refresh-dependencies`
  （同版本号 m2 产物会被 Gradle 缓存，必须 refresh）
- 不用 composite build：Mochi 的 AGP 8.13.2 只支持 Gradle 8.x，与本仓 Gradle 9.7.1 不兼容。
- 换机器/CI：`0.2.0-khatkit` 只在本地 m2，必须先发布或换成已发布坐标。

## 13. 不要再重新讨论的决定

1. 不回退到全量 Mochi；material3 是骨架层，不是过渡层；
2. 不重新引入 Kedge/Miuix/Khromia（含「只引一个组件」）；
3. 主题只接一次 `MochiTheme`；页面不写外观分支；
4. 卡片运行时（Lua API/节点词表/上限）不动；
5. 迁移验收以行为等价为准，不以符号归零为准（§6）。
