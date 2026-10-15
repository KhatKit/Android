# 浏览器示例卡片

这里保留开发期示例；正式维护位置已迁移到
`/home/heizige/文档/Android/KhatKitCards/browser_checkin` 与
`browser_compare_prices`（这两张正式卡片已发布到 Hub）。本目录的示例本身
不随 APK 内置，也不发布到 Hub。
每个子目录为标准 `card.json` + `main.js`，打包该目录内容后通过本地卡片导入使用。

这两份开发期示例仍只允许测试域 `browser-fixture.test`，本地导入前把
`network.allow` 改成你实际要访问的站点域名。而正式维护位置里那两张已发布到
Hub 的卡片（1.0.0）访问目标由调用者在运行时用参数给出，因此 `network.allow`
取 `["*"]`，安全边界交给 `browser.open` / `browser.act` 的逐次审批。
浏览器方法保持逐次审批。示例未在真实站点验收。

## 网页签到

`browser_checkin` 参数：

```json
{
  "url": "https://browser-fixture.test/checkin",
  "button_selector": "#checkin",
  "result_selector": "#result",
  "success_text": "签到成功"
}
```

卡片等签到按钮可见后点击，再等待结果区域出现成功文本。超时或页面报错均失败，
不会把“点击成功”当作“签到成功”。需要登录的站点应先由用户在 WebView 登录；
当前 WebView 共用 cookie，卡片不接收密码。验证码、第三方登录和风控不在示例中绕过。

## 比价抓取

`browser_compare_prices` 参数：

```json
{
  "urls": [
    "https://browser-fixture.test/product-a",
    "https://browser-fixture.test/product-b",
    "https://browser-fixture.test/product-c"
  ],
  "price_selector": "#price",
  "currency": "CNY"
}
```

持有 2–3 个独立标签，通过明确 ID 读取每页价格，最后统一关闭。价格元素须为
`CNY 19.90` 这类“币种 空格 无千分位小数”；范围价、逗号小数、其他币种、
零价及非价格文本均拒绝，避免误比价。结果保留原始文本和 URL，并注明不含运费。
实际站点应选价格专用元素或按站点规则调整解析；不会下单。

JVM QuickJS 测试执行原样脚本，校验审批桥错误处理、签到等待、比价与标签清理。
这些测试验证脚本控制流，不代表 Android WebView、真实登录或站点成功率。
