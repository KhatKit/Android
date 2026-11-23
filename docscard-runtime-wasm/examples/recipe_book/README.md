# 家常菜谱

`recipe_book.kkcard` 是独立 ABI v1 WASM 卡包，不编入 APK。内置 10 道家常菜；不请求网络、账号、存储或 AI 权限。搜索可用菜名、别名或食材，用空格、逗号或顿号分开的词须同时匹配。详情支持 1–4 人份量，返回列表保留搜索词。小组件只显示条目数，工具台卡片打开交互界面。

无界面调用传 `{"query":"番茄 鸡蛋"}`，从返回的 `recipes[].id` 取 ID；再传 `{"recipe_id":"tomato-egg","servings":4}` 获取材料、用量、单位和步骤。人数不影响预计时间；空搜索返回全目录，未匹配返回空数组。

在本目录运行：

```sh
cargo test --locked
python ../package_card.py
docscard run --package recipe_book.kkcard --event '{"type":"run","payload":{"query":"番茄 鸡蛋"}}'
```

最后一条应返回 `count: 1` 和 `tomato-egg`。安装/发布与其他独立卡相同，见 [卡片运行与发布](../../../docs/card-runtime.md)。仓库中的卡包不等于正式市场已上架。
