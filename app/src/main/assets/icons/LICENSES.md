# 图标来源与许可

本目录下 AI 提供商 / 搜索服务商的图标**绝大多数来自 lobe-icons 的单色版**，少数为本项目自有或
上游没有对应图标时保留的旧文件。渲染层（`AIIcon.kt`）一律把它们画成**中性单色**：
底色取主题 `secondaryContainer`、图形取 `onSecondaryContainer`，并对每个资产统一做
`ColorFilter.tint`（SrcIn）—— 只保留形状的 alpha、把全部 RGB 换成图形色，所以资产内部
即使有逐元素 `fill`、内联 `<style>`、`stroke` 也漏不出第二色。**本目录不再承担品牌配色**。

## lobe-icons

- 包名 / 版本：`@lobehub/icons-static-svg@1.95.1`
- 仓库：<https://github.com/lobehub/lobe-icons>
- 许可：MIT
- 取用规则：只取**没有 `-color` 后缀**的单色版（如 `gemini.svg`，而不是 `gemini-color.svg`），
  文件名为上游原文件名；本目录因此不再保留任何 `*-color.svg`。

版权与许可原文（上游 `LICENSE`，2026-10-14 从
<https://raw.githubusercontent.com/lobehub/lobe-icons/master/LICENSE> 取得）：

```
MIT License

Copyright (c) 2023 LobeHub

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 不属于 lobe-icons 的文件

| 文件 | 来源 | 处理 |
|---|---|---|
| `khatkit.svg` | 本项目自有（应用自己的图标） | 本来就是单色（`fill="currentColor"`），保留 |
| `metaso.svg` | 上游无对应图标，沿用旧文件 | 原为多色（含 `linearGradient` + `#175CD3`）：已**手工改为单色**（删掉渐变 `<defs>` 与全部显式 `fill`，让路径继承由 CSS 设定的 fill） |
| `tokenpony.svg` | 同上 | 原为双色（`#0005de` + `#000`）：已**手工改为单色**（同上处理） |
| `ling.png` | 同上（光栅） | 保留；PNG 无法用 CSS `fill` 改色，渲染时由 `AIIcon` 用 `ColorFilter.tint(前景色)` 统一成单色剪影 |
| `linkup.png` | 同上（光栅） | 同上 |
| `juhenext.png` | 同上（光栅） | 同上 |
