# Unicode 工具卡片

`unicode_tool.kkcard` 是独立的 ABI v1 WASM 卡包（`manifest.json` + `card.wasm`），不编入 APK，不注册 Android 页面。目录收录可复现的参考源和产物，部署时使用现有 Hub 的卡片包发布流程，见 [卡片运行时](../../../docs/card-runtime.md)。

工具台直接打开表单；带 `text` 的工具调用走无界面入口。`encode` 保留原文（不做 Unicode 归一化），`decode` 接受十六进制码点序列或成对的 UTF-16 转义。最多 512 个 Unicode 标量。两种输入都可以查看字形，每页 24 个，可以逐层返回；为 AI 参数加 `"glyphs":true` 可直接取逐码点的名称、分类和预览。组合序列的整体渲染受设备字体影响，单行明细始终以标量而非字素簇计数。

在本目录运行：

```sh
cargo test --locked
python package.py
docscard run --package unicode_tool.kkcard --event '{"type":"run","payload":{"mode":"decode","text":"U+4E2D U+1F600"}}'
```

期望 `text` 为 `中😀`，`count` 为 `2`。交互路径可用 `docscard run --card target/wasm32-unknown-unknown/release/unicode_tool.wasm --manifest manifest.json --event '{"type":"run","payload":{}}' --ui replies.json --verbose` 回放；`replies.json` 为 `ui.screen` 应答数组。
