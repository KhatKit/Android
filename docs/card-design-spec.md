# 工具台卡片设计规范（Mochi 版式）

> 适用：`KhatKitCards/moonbit/toolbox/cards/` 下的所有卡片。
> 基线实现：[text_stats/card.mbt](../Android/KhatKitCards/moonbit/toolbox/cards/text_stats/card.mbt)。
> 共享工具：`toolbox/sdk/mochi.mbt`（`@sdk.hero_card` / `grouped_card` / `section_header` 等 15 个函数）。

## 0. 一句话

**一张卡片 = 顶部介绍大卡片 + 若干 iOS inset grouped 分组；表单页和结果页分开成两层，返回只退层不关卡片。**

## 1. 页面骨架

```
┌─────────────────────────────┐
│  [48–64dp 图标块]            │  ← hero_card：图标块 + 34sp 粗体标题 + 17sp 简介
│  大标题（34sp Bold）          │     卡片底 surface_container / 32dp 圆角 / 24dp 内边距
│  一行简介（17sp 次要色）       │
└─────────────────────────────┘
   分组小标题（17sp semibold 次要色，左缩进 16dp）   ← section_header
┌─────────────────────────────┐
│  分组卡片（surface_container / 20dp / 16dp）      │  ← grouped_card
│   · 输入行 / 按钮 / 结果行                          │
└─────────────────────────────┘
   分组脚注（13sp 次要色，左缩进 16dp）              ← section_footer
```

**硬规则**

| 规则 | 原因 |
|---|---|
| 页面**第一屏顶部必须是 hero_card** | 对齐 Mochi 示例二级页；不要再从「输入文本」这种小标题开场 |
| 分组之间留 10dp，分组标题与卡片间留 8dp | 和 Mochi `MochiListDefaults` 一致 |
| 每张卡片最多 **3 个分组** | 再多就说明该拆卡了 |
| 主按钮**全宽**（`fill_width()`） | Mochi 表单页的主操作都是通栏 |
| 说明文字一律用 `section_footer`，不要塞在卡片里 | 卡片内只放交互控件 |
| 颜色只用**主题角色**（`primary` / `surface_container` / `on_surface_variant` 等），不写 hex | 明暗/换肤要跟着主题走 |

## 2. 两层导航（表单页 → 结果页）

照 `card_codex_resets` 的压层方案：

```moonbit
// 根屏：表单
match @sdk.screen(CAP_UI, "卡片名", input_tree(...), @sdk.root_options("card_close")) {
  Some(resp) => { /* event == "submit" → on_result = true */ }
  None => return @sdk.fail_result("已取消")
}

// 子页：结果（压一层）
match @sdk.screen(CAP_UI, "结果", result_tree(...), @sdk.pushed_options("result_close")) {
  Some(_) => ()      // 返回 → 退回表单页，输入内容还在
  None => ()
}
```

**必须保留输入状态**：表单页的输入、选中项放在循环外的 `FixedArray` 里，每次重画时回填，
用户从结果页返回不会丢内容。

**AI 入口不走 UI**：`payload` 里有完整参数时直接算、直接返回 JSON，不弹层。

## 3. 选择控件：ChipRow / Dropdown

三选一，按语义挑：

| 场景 | 用 | 写法 |
|---|---|---|
| 2–5 个互斥短选项、要等宽分段 | `Selector` | 已有，不用改 |
| 多个可多选/单选的筛选条件、标签 | **`ChipRow`** | `@sdk.chip_row(id, options, value, action)`（单选）<br>`@sdk.chip_row_multi(id, options, values, action)`（多选） |
| 5 个以上选项、需要原地选、不需要说明 | **`Dropdown`** | `@sdk.dropdown(id, label, options, value, action)` |
| 需要图标/说明/分组的选择 | 一层新页 + `ListRow` | 见 §2 |

**ChipRow 语义**：芯片 = 筛选条件，**默认一个都不选 = 不过滤（全部）**。
不要默认全选——Mochi 的选中态是饱和 `primary`，一屏全亮很花。

**不要给多选 chip 配图标**：选中时文字前会画出对勾，再叠 17dp 图标会挤。

## 4. 结果页

| 结果类型 | 用 |
|---|---|
| 单个核心数字 | `@sdk.metric_card(value, unit, hint)`（34sp 主色 + 滚动计数） |
| 2–5 项明细 | `@sdk.grouped_card([...list_row...])` 或 `Group` 直排 |
| 长文本/代码 | `@sdk.text_style(x, "code", "primary")` 放在 `grouped_card` 里 |
| 空态 | `@sdk.empty_card(icon, title, hint)` |

## 5. 代码形状（照抄这段当模板）

```moonbit
// ---------- 卡片页导航 ----------
fn root_options() -> Json { @sdk.root_options("card_close") }
fn result_options() -> Json { @sdk.pushed_options("result_close") }

fn input_tree(text : String) -> Json {
  @sdk.column_spacing(10, [
    @sdk.hero_card("text_fields", "文本统计", "粘贴或输入文本后点「开始统计」。"),
    @sdk.section_header("输入"),
    @sdk.grouped_card([
      @sdk.node("TextField", [("id", @sdk.js("text")), ("label", @sdk.js("文本内容")), ("value", @sdk.js(text))], []),
      @sdk.node("Button", [
        ("label", @sdk.js("开始统计")), ("action", @sdk.js("submit")),
        ("tone", @sdk.js("primary")), ("modifier", @sdk.fill_width()),
      ], []),
    ]),
    @sdk.section_footer("说明文字写在这里，不塞进卡片。"),
  ])
}

fn result_tree(stats : Json) -> Json {
  @sdk.column_spacing(10, [
    @sdk.hero_card("text_fields", "文本统计", "统计完成，明细见下方分组。"),
    @sdk.section_header("结果"),
    @sdk.metric_card("20", "字符", "汉字 7 · 行 2 · 字节 34"),
    @sdk.grouped_card([@sdk.list_row("汉字", "7", "text_fields", "按 UTF-8 前导字节判定")]),
  ])
}
```

## 6. 验收（每张卡都要过）

```bash
cd Android/KhatKitCards/moonbit/toolbox
export PATH=../.toolchain/moon/bin:$PATH
moon build --target wasm --release                      # 0 error
bash tools/build.sh                                     # dist/<card>/card.wasm
docscard run --card dist/<card>/card.wasm \
  --manifest dist/<card>/manifest.json \
  --event '{"type":"run","payload":{}}' \
  --ui reply.json --mock-host mock.json --verbose
# 期望：[ui] ui.screen title="..." nodes=N，退出码 0
```

真机：工具台开卡片 → 顶部大卡片 → 分组卡片 → 提交后压入结果页 → 返回输入页内容还在。

## 7. 不要做的事

- ❌ 手动复制 hero_card 的 90 行 JSON——直接用 `@sdk.hero_card`
- ❌ 卡片顶层直接 `@sdk.column_spacing` 就发 `ui.form`（不再用 `ui.form`，改用 `ui.screen` + 两层）
- ❌ 自造颜色 hex / 自造字号（34/17/13 是规范值）
- ❌ 把说明文字、帮助段落塞进分组卡片
