# 浅/深色主题配色：根因、改动与手动检查点（T1）

> 口径：真机验证由主人手动做；本文件是「按这份清单点一遍」的验收入口。
> 代码侧证据：`./gradlew :app:assembleDebug` 与 `./gradlew :app:test` 均绿（2026-10-30 23:15）。

## 1. 根因（3 个，都能单独造成「浅/深适配不好」）

| # | 位置 | 问题 | 处理 |
|---|---|---|---|
| A | `app/core/ui/theme/Theme.kt:101`（改前） | `MochiTheme(...)` 用的是默认 `ThemeController()` = `ColorSchemeMode.System`，**只看 `isSystemInDarkTheme()`**；而本仓 `ColorMode`（LIGHT/DARK）只喂给了 `LocalDarkMode`／扩展色／系统栏图标。⇒ 设置选「浅色」+ 系统深色时，配色是深色、图标/扩展色/代码高亮是浅色，整套错位。 | `Theme.kt:28` 新增 `ColorMode.toMochiColorSchemeMode()`，`Theme.kt:72` `remember(colorMode){ ThemeController(colorSchemeMode = ...) }` 传给 `MochiTheme`。明暗唯一真相 = 本 app `ColorMode`。 |
| B | `app/themecompat/Color.kt:217,222`（改前） | `cardColorsOnSurfaceContainer` 与 `listItemColors` 取 `surfaceBright`。该角色浅色 = N98（**与 surface 同色**）、深色 = N24（比 surface 的 N6 亮 18 个 tone）——是「明暗各跑极端」的一对，不构成阶梯。⇒ 浅色下分组/卡片/列表行**整片消失**（白上白），深色下过亮发灰，看起来像「配色反了」。全仓 21 处调用点（settings/log/stats/workspace-detail/search/BackButton 等）。 | 改成 `surfaceContainerHigh` / `surfaceContainer`，形成三级阶梯：页面 `surface` → 组 `surfaceContainer` → 行/嵌套卡 `surfaceContainerHigh`。`Color.kt:205-223` 已写清「为什么不用 surfaceBright」。 |
| C | `khatkit-ui/.../MochiMaterialBridge.kt:37-38`（改前） | AMOLED 只把 `background/surface` 打黑：卡片仍是 N12 灰（黑底上突然浮一层灰块）、顶栏取 `surfaceContainer` ⇒ 黑页上一条灰带；且 `amoledDark` 是独立开关，浅色配色下也会被打黑。 | `withAmoledBackground` 改为**整条表面阶梯重锚到纯黑**（Low/Container/High/Highest 依次 55%/40%/25%/10% 混黑，仍单调变亮），并加 `!isDark` 守卫：浅色下开 AMOLED 不变黑。 |
| D | `app/themecompat/CompatMaterial3.kt:24`（改前） | `@Suppress("UNUSED_PARAMETER") dark: Boolean`——明暗参数被字面忽略，造出「第二个真相」。 | 删掉该参数（唯一调用点 `Theme.kt:110` 同步）。 |

## 2. Bridge 映射为什么这么映射

- **37 角色仍然逐域拷贝、不做派生计算**：Mochi 是唯一 token 源，任何 `derived`（例如拿 `surface` 去插值出容器色）都会在换种子色/换对比度时与 Mochi 组件漂移。
- AMOLED 是唯一的例外，理由见上表 C：它不是在「派生语义」，而是**同一条阶梯换锚点**（纯黑为 0 点），档位相对顺序不变。
- `surfaceBright/surfaceDim` **保留 1:1**（它们是 Mochi/MD3 的合法角色，DebugPage 要读），只禁止页面把它们当卡片底色用——这条纪律落在 `themecompat/Color.kt` 的唯一出口上，不逐页打补丁。

## 3. 硬编码颜色盘点（`grep -rn 'Color(0x' --include=*.kt app/src/main | wc -l` = **548 → 545**）

（改后 545 = 548 − JsonTree 3 行）

| 分类 | 行数 | 位置 | 处理 |
|---|---:|---|---|
| 预设主题 | 490 | `themecompat/presets/*Theme.kt`（7 × 70） | **保留**。`SettingsRepository.kt:299,582` 只把 `PresetThemes[0].id` 当默认 `themeId` 与 picker 展示用，运行期不注入主题（与迁移计划「中间态」一致）。 |
| 语法高亮 | 30 | `core/ui/theme/CodeColor.kt:8-22`（One Dark）/ `:27-41`（One Light） | **保留**：本就是明暗成对的两套，选择由 `LocalDarkMode`（已随根因 A 修正为真实明暗）。 |
| 图表调色板 | 12+1 | `components/charts/ChartScale.kt:15-26`、`ChartSpec.kt:117` | 保留：中明度系列色，深浅底都可读。 |
| Diff/头像/HTML 命名色 | 2+2+4 | `DiffView.kt:24-25`、`RoleAvatarStyle.kt:14-15`、`HtmlStyleParsing.kt:73-76` | 保留：语义色/品牌色，非主题角色（Diff 的 4CAF50/EF5350 为中等明度，深浅底都可读）。 |
| JSON 树着色 **（已改）** | 3 → 0 | `JsonTree.kt:293,297,301`（旧：`Color(0xFF6A8759/0xFFCC7832/0xFF6897BB)`，Darcula 深色三色） | **已改为 theme token**：`JsonTree.kt:292` `if (LocalDarkMode.current) AtomOneDarkPalette else AtomOneLightPalette`，`:300,304,308` 取 `palette.string/keyword/number`。原三色在白底上绿/橙对比度不足，是真实浅色缺陷。 |
| 品牌图标 | 4 | `icons/{Discord,Heart,TencentQQ}Icon.kt` | 保留：`Color(0xFF000000)` 只是 `SolidColor` 的 fill 占位，实际由 tint 上色。 |

`Color.White`/\`Color.Black\` 共 11/182 处：品牌图标、二维码留白、海报遮罩、玻璃高光/暗角、头像文字（压在品牌色或图片上），均非主题角色，保留。

## 4. 手动检查点清单（浅 / 深各过一遍）

主题入口：设置 → 主题（外观）。先做「浅色」，再做「深色」，最后做「跟随系统 + 系统切夜间」。

1. **模式真的切换了**：选「浅色」而系统仍是深色 → 整套配色变浅、状态栏/导航栏图标变深；选「深色」反之。杀掉进程重进仍保持。
2. **跟随系统**：ColorMode = 跟随系统时，系统切夜间，app 整体（含代码块、Mermaid）同步；不会出现「页面深、代码浅」。
3. **三级层次（重点）**：浅色下 **卡片/分组/列表行必须能从页面底色里看出来**（改前这里是一片白）；深色下不能比页面亮出一大截（改前偏亮发灰）。看：设置首页分组卡、设置各子页的卡片行、日志页、统计页、工作区详情页。
4. **正文层级**：正文（onSurface）> 次要说明（onSurfaceVariant）> 分隔线（outlineVariant）三档都能分清；浅色下次要文字不能糊成灰块。
5. **顶栏/页面/弹层**：顶栏（surfaceContainer）比页面（surface）一档；弹层与底部弹窗（surfaceContainer* + scrim）在浅色下能与页面区分。
6. **选中态/强调**：选中 chip、开关、按钮（primary/secondaryContainer）在深浅两套下都读得出，不出现「深色下同色、浅色下过淡」。
7. **AMOLED**：深色 + AMOLED 开 → 页面真纯黑，卡片由暗到亮有阶梯，**顶栏不再是黑页上的一条灰带**；浅色下开 AMOLED → 不应变黑。
8. **代码/富文本**：代码块浅色用 One Light、深色用 One Dark；JSON 树、Diff 均可读。
9. **系统栏**：状态栏/导航栏图标对比度跟随主题（浅色深图标、深色浅图标），下拉通知栏/手势条不错色。

## 5. 遗留（不在本次 in-scope，未改）

- `core/ui/components/ui/CardGroup.kt:203-204` 注释仍写「= `surfaceBright`」，与改后的 `surfaceContainerHigh` 不符，需同步文字。
- MD3 `ColorScheme` 构造器编译期提示 deprecated（缺 `fixed*` 角色）；Mochi `MochiColorScheme` 无对应 token，补全需改 mochi 侧，留待 P 系列后段。
