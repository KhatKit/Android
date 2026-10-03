# 浏览器设备验证

日期：2026-10-03。设备 PKG110，Android API 36，Debug application ID
`heizige.kk.khatkit.debug`，版本 2.5.5 / 190。

| 测试类 | 通过项数 | 实际检查 |
| --- | --- | --- |
| BrowserArchiveTest | 4 | 旧 JSON 迁移、非法 URL/重复数据、损坏源保留、数据库关闭重开、书签时间、30 次并发写入与保留上限 |
| CardBrowserBridgeTest（JVM） | 4 | 卡片运行标签归属、审批、deadline、域名限制与退出清理 |
| BrowserRuntimeTest | 1 | 三标签并行操作、本地 HTML 模拟异步登录反馈、150 元素快照 <20KB UTF-8、真实 PNG 非空、宿主转移、关闭、导航后旧引用/增量基线失效 |

Room 4 项在整包运行中通过；修正 HTML fixture 的 history URL 后，
`BrowserRuntimeTest` 通过。当前保留的 5 项设备测试均有实际设备通过结果。

```sh
./gradlew --offline :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r \
  -e package heizige.kk.khatkit.app.core.data.browser \
  heizige.kk.khatkit.debug.test/androidx.test.runner.AndroidJUnitRunner
```

WebView 用例通过 ActivityScenario 启动应用保持前台。该手机曾把后台
instrumentation 及 renderer 以 `Cached(nirvana)` 原因结束，不能把此情况计作通过。
测试页使用 `loadDataWithBaseURL`，base URL 与 history URL 一致；缺少 history URL
时该设备上 `view.url` 为 `about:blank`，与页面完成回调不一致，会触发加载超时。

边界：未验证真实网站的认证 Cookie、网络重定向、第三方登录、普通/认证下载，
浏览器按设计没有设置页。资源拦截测试不是网络沙箱穿透测试。截图是隐藏视口的非空检查，
不是网页像素精确对照。没有使用用户账号密码，也未操作生产服务。
