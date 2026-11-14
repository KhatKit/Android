# 卡片 UI DSL：Chip 行与下拉选择（ChipRow / Dropdown 节点）设计

> 状态：**已实现**（2026-11-14）。给卡片 DSL 补了两个「可选择」节点，让卡片能用
> **Mochi 同款**的 chips 行和下拉菜单做选择，而不是只能堆一排 RadioGroup 或整段 Select。
> 落地清单见 §7。
>
> 现状：`text_stats` 已按 Mochi 二级页重构（根屏输入页 + push 结果页）。本文只讨论**新增节点**。

## 0. 一句话结论

现在卡片要做「选一个 / 选几个」，只有三条路，都不好看：

| 现有节点 | 渲染 | 问题 |
|---|---|---|
| `Select` / `RadioGroup` | `FormWidget` 里每项一个 `Md3RadioButton` 行 | 竖向铺开，8 个选项吃掉半屏；没有 Mochi 手感 |
| `Selector` | `MochiSegmentedControl` | 等宽分段，2–4 项好用，多了挤成一团 |
| `Button` + action | 卡片自己画一排按钮 | 没有选中态、没有对勾、没有形变反馈 |

补两个节点解决：

- **`ChipRow`**：一行（或折行）可点选的 chips，单选/多选都行，走 Mochi `MochiChip`；
- **`Dropdown`**：一行「当前值 + ⌄」，点开是 Mochi 下拉菜单，走 `MochiDropdown`。

两者都**不引入新的交互模型**：沿用 `id` / `value` / `action` 的字段语义，卡片照旧在
`ui.screen` 循环里读 `values[id]` 和 `event`。

## 1. 为什么不是「把 Select 换个渲染」

`Select` 的 schema 只有 `options`，没有多选、没有图标、没有「未选中」态语义。
把它的渲染换成 chips 会让**已发布的 78 张卡片**外观突变，且多选表达不出来。

⇒ 新增节点，旧节点不动。两条路并存，旧卡片零影响。

## 2. ChipRow

### 2.1 schema

| 属性 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | string | ✅ | 字段 id，回传值放 `values[id]` |
| `options` | string[] | ✅ | 选项文案，2–24 项（超出宿主报错，避免把树撑爆） |
| `value` | string \| string[] | | 单选给字符串；多选给数组。缺省 = 未选 |
| `multiple` | boolean | | 多选。默认 false |
| `action` | string | | 点选后回传的 event（照 `Selector`：320ms 去抖） |
| `icons` | string[] | | 与 `options` 等长的图标名（空字符串 = 该项无图标） |
| `wrap` | boolean | | 折行排布（默认 true）；false 时横向滚动 |
| `disabled` | boolean | | 整行禁用 |

### 2.2 渲染

直接复用 Mochi，不自己画：

```kotlin
MochiChipGroup(wrap = wrap, contentPadding = PaddingValues(horizontal = 16.dp)) {
    options.forEachIndexed { i, label ->
        MochiChip(
            label = label,
            selected = isSelected(i),
            showCheck = true,          // 选中时一笔画出对勾（Mochi 自带）
            onClick = { if (!disabled) toggle(i) },
            leadingIcon = icons.getOrNull(i)?.takeIf { it.isNotBlank() }?.let {
                { Icon(UiIconVectors.vector(it)!!, null, Modifier.size(17.dp)) }
            },
        )
    }
}
```

交互与 `Selector` 一致：**先本地切换**（选中块立刻走 Mochi 的形变/对勾动画），
再 320ms 去抖把 `action` 回传给卡片；卡片下一屏以 `props.value` 为准。

### 2.3 卡片侧用法

```moonbit
fn style_chips(selected : String) -> Json {
  @sdk.node("ChipRow", [
    ("id", @sdk.js("style")),
    ("action", @sdk.js("style_changed")),
    ("options", @sdk.ja(["原文", "标题", "大写", "小写", "蛇形"])),
    ("value", @sdk.js(selected)),
  ], [])
}
```

多选：

```moonbit
@sdk.node("ChipRow", [
  ("id", @sdk.js("flags")),
  ("options", @sdk.ja(["大写", "数字", "符号"])),
  ("value", @sdk.ja([@sdk.js("大写"), @sdk.js("数字")])),
  ("multiple", @sdk.jb(true)),
], [])
```

## 3. Dropdown

### 3.1 schema

| 属性 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | string | ✅ | 字段 id |
| `options` | string[] | ✅ | 菜单项，2–50 项 |
| `value` | string | | 当前选中项；缺省取 `options[0]` |
| `label` | string | | 按钮上的前导文案（「编码」）；为空只显示当前值 |
| `action` | string | | 选中后回传的 event |
| `icon` | string | | 按钮前导图标 |
| `disabled` | boolean | | 禁用 |

### 3.2 渲染

```kotlin
MochiDropdown(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    items = options.map { MochiItem(it) }.toPersistentList(),
    selectedIndex = options.indexOf(value),
    onClick = { expanded = true },
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(UiIconVectors.vector(it)!!, null, Modifier.size(18.dp), tint = style.color("primary")) }
        Column {
            label?.let { Text(it, style = style.textStyle("label"), color = style.color("muted")) }
            Text(value, style = style.textStyle("body"), color = style.color("default"))
        }
        Spacer(Modifier.weight(1f))
        KkChevron()      // 和 ListRow 的箭头同一个
    }
}
```

按钮槽外观与 `ListRow` 一致（16dp 横内边距、行高 ≥52dp），所以它放在 `Group` 里
和其它行**是一条视觉直线**，不像异类控件。

### 3.3 和 ListRow 的分工

`ListRow` 有 `value` + `chevron`，但点了只回传 `action`，选什么得卡片再开一屏。
`Dropdown` 是**原地选**：不离开当前页，选完立刻把值写回 `values[id]` 并回传 `action`。

⇒ 选项 < 10 且不需要解释文字 → `Dropdown`；需要图标/说明/分组 → 仍走一层新页 + `ListRow`。

## 4. 宿主改动清单

### 4.1 白名单（三处必须同步）

| 文件 | 改动 |
|---|---|
| `khatkit/.../ui/NodeTree.kt` | `schemas` 加 `ChipRow`、`Dropdown`；两者进 `fields` 集合 |
| `docscard-runtime-wasm/src/ui.rs` | `specific_props` 同上；`FIELDS` 常量加两项 |
| `khatkit/src/main/resources/ui_kit.lua` | Lua 卡片镜像名单同步 |

`ChipRow.value` 是「string 或 string[]」，需要给 `checkValue` 加 `string_or_strings` 类型；
`Dropdown.value` 沿用 `string`。

### 4.2 渲染器

`khatkit-ui/.../uikit/NodeTreeRenderer.kt`：

- `"ChipRow" -> ChipRowControl(this, values, readOnly, onAction, m)`
- `"Dropdown" -> DropdownControl(this, values, readOnly, onAction, m)`

两者都要走 `fields` 分支，不能落到 `else` 的 legacy `FormWidget`（它不认识新节点）。

### 4.3 校验

- `ChipRow.options` 2–24 项；`icons` 长度等于 options 或为 0；
- `ChipRow.multiple == false` → `value` 必须是字符串；`true` → 必须是数组；
- `Dropdown.options` 2–50 项，`value`（若有）必须属于 options；
- 两节点 `action` 可为空（纯展示），为空时点选不回传 event。

## 5. 验收

```bash
# 宿主
./gradlew :khatkit:testDebugUnitTest --tests '*NodeTreeParser*'   # 新 schema 正/反例
./gradlew :khatkit-ui:testDebugUnitTest                            # 渲染无 crash

# 卡片
cd Android/KhatKitCards/moonbit/toolbox
export PATH=../.toolchain/moon/bin:$PATH
moon build --target wasm --release
bash tools/build.sh
docscard run --card dist/text_stats/card.wasm   --manifest dist/text_stats/manifest.json   --event '{"type":"run","payload":{}}' --ui reply.json --verbose
# 期望：[ui] ui.screen title="文本统计" nodes=N（含 ChipRow）
```

真机：工具台开卡片 → chips 点一下有选中形变 + 对勾；下拉展开菜单并原地回填。

## 6. 落地清单（2026-11-14）

| 层 | 文件 | 改动 |
|---|---|---|
| 解析 | `khatkit/.../ui/NodeTree.kt` | `ChipRow` / `Dropdown` 进 `fields` + `schemas`；新增 `string_or_strings` 值类型；options 数量、icons 长度、value 归属校验 |
| 校验 | `docscard-runtime-wasm/src/ui.rs` | 同步 schema + `PropKind::StrOrStrings` + 语义校验；`FIELDS`/`components()` 加两项 |
| 渲染 | `khatkit-ui/.../NodeTreeRenderer.kt` | `ChipRowControl`（MochiChipGroup + MochiChip，320ms 去抖）、`DropdownControl`（MochiDropdown，原地回填） |
| Lua 镜像 | `khatkit/src/main/resources/ui_kit.lua` | schema + 字段表 + `string_or_strings` 值校验 |
| SDK | `moonbit/toolbox/sdk/ui.mbt`、`jsonx.mbt` | `chip_row` / `chip_row_multi` / `with_icons` / `dropdown` / `fstrings` |
| 用例 | `text_stats` | 「统计项」多选 chips 决定结果页展示哪些行；「示例文本」下拉直接填充输入框 |
| 测试 | `khatkit/src/test/.../ChipRowParseTest.kt` | 3 例：单选/多选正例、Dropdown 正例、value 越界反例 |

⚠️ **原生库必须跟着重编**：Android 端的节点树校验跑在预编译的
`khatkit/src/main/jniLibs/*/libkhatkit_core.so`（`khatkit-core` → `docscard_runtime_wasm::normalize_tree`）里。
只改 `docscard-runtime-wasm/src/ui.rs` 不重编 `.so`，设备上仍然按旧白名单拒绝新节点，
表现为卡片「一打开就返回已取消」。重编命令：

```bash
ANDROID_HOME=$ANDROID_HOME bash khatkit-core/build-android.sh
./gradlew :app:assembleDebug
```

## 7. 非目标

- 不给 `ChipRow` 做「可移除」（`MochiRemovableChip`）—— 已选项用 chip 自己表达够了；
- 不做 chip 的富内容（`labelContent`）—— DSL 是纯数据，塞不了 Composable；
- 不改 `Selector` 行为，它继续服务 2–4 项的等宽分段场景。
