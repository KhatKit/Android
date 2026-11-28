# Mochi 赞助页

更新：2026-11-28。客户端支持从 Hub 读取赞助资料；后端代码需要单独部署。渠道固定为微信、支付宝、爱发电、Ko-fi。维护者已确认 Ko-fi 审核通过；爱发电 https://afdian.com/a/heizige、Ko-fi https://ko-fi.com/heizige 均启用。Ko-fi 当前为默认 5 美元、金额可自定义的一次性赞助；应用入口仅打开其主页，没有月订阅或自动扣款文案。邮箱及公开鸣谢记录尚未提供，不填样例。

## 数据与行为

- 设置 → 赞助，保留赞助、鸣谢榜、项目主页三个 Mochi 标签。
- 微信、支付宝使用原有本地黑白二维码；二维码可放大、保存 PNG、复制从真实二维码解析的收款链接。保存取操作发起时的渠道，取消不报成功。
- 金额芯片只是建议，付款应用中自行填写；没有订单、支付回调、自动到账统计，也不把金额选择或打开渠道计为赞助。
- DonationRepository 使用现有 Hub 地址，公开 GET 不携带账户令牌，连接超时 5 秒、请求超时 8 秒。
- 首次进入与重新进入页面刷新资料；失败显示重试。微信、支付宝在未部署、404、断网或响应不合法时仍可使用。
- 按 Hub 地址隔离缓存，有效期 24 小时；只在完整响应解析和校验成功后更新。失败时若有有效缓存，明确显示上次保存的信息；未加载成功时不显示“暂无赞助记录”。缓存写入失败不丢弃已获得的远端资料。
- 远端可配置展示名称、简介、头像、项目链接、邮箱，以及 id=afdian/kofi 的平台主页；客户端不展示其他平台。首次离线时使用维护者确认的两个主页兜底；已经成功加载的远端列表具有优先权，管理员删除/禁用某个平台后不会被本地兜底重新打开。没有配置的入口显示“主页待配置”，不可点击。不使用上游 RikkaHub 或 Mochi 作者的赞助地址。
- 主页只预览前 3 条公开鸣谢；完整榜每次加载 50 条，以独立懒加载行呈现，支持加载更多与失败重试；即使首页配置接口失败，也可独立请求鸣谢榜。完整榜不缓存；主页的缓存鸣谢最多保留 24 小时，因此撤回同意后的离线副本不保证立即消失。

## 后端协议与维护

实现位于 KodeHeapServer，完整协议、鉴权和管理示例见 [后端说明](../../../../IDEA/Project/KodeHeapServer/docs/sponsors.md)。

| 接口 | 用途 |
| --- | --- |
| GET /api/sponsors | 返回 profile、channels、acknowledgements（最多 3 条）、acknowledgementTotal |
| GET /api/sponsors/acknowledgements?limit=50&offset=0 | 返回 items、total、limit、offset |
| GET /api/admin/sponsors | 管理员查看完整配置，含未启用与未同意公开项 |
| PUT /api/admin/sponsors/profile | 替换项目资料 |
| PUT /api/admin/sponsors/channels | 原子替换渠道列表 |
| PUT /api/admin/sponsors/acknowledgements | 原子替换鸣谢列表 |

管理接口沿用 auth-jwt 与 admin/developer 角色。公开渠道只取 enabled=true，公开鸣谢还必须 consent=true；sortOrder/id 确定顺序。没有接入平台 webhook，管理员依据真实付款记录及公开同意手动维护鸣谢。

## 待补真实资料

| 项目 | 需要提供 |
| --- | --- |
| 爱发电、Ko-fi | 已确认并接入上述真实主页；后台新建配置预置为启用 |
| 企业赞助 | 联系邮箱 |
| 作者资料 | 展示昵称、头像 URL、简介及主页链接；未提供时展示 KhatKit 项目资料 |
| 鸣谢榜 | 经同意公开的昵称、金额（可选）、留言（可选），支持撤回公开 |

爱发电订阅建议与 Ko-fi 一次性赞助设置见 [赞助金额与订阅方案](sponsor-pricing.md)，尚未代创建或发布任何平台套餐。

## 手动验收

未操作手机；编译和 JVM 测试不能代替以下验收。

| 段落 | 内容 |
| --- | --- |
| 改动点 | SettingDonatePage/DonationViewModel：数据刷新与分页；DonationCommunityCards/DonationRemoteStatus：远端资料、错误与空态；原二维码保存、复制链路保留。 |
| 复现路径 | KhatKit → 设置 → 赞助；分别连接已部署且有数据、已部署且为空、404/断网的 Hub。无需激活账户。 |
| 预期结果 | 配置成功后显示真实渠道与资料；只展示同意公开的记录；完整榜分页无重复；断网明确报错并能重试；缓存标注旧资料；二维码始终可放大和保存；取消导出不报成功；无平台配置不出现假链接。 |
| 边界 | 浅/深色、Mochi/iOS/Nothing、玻璃开关、减弱动画、大字号与窄屏；旋转中导出、快速切渠道后保存；切换 Hub 不串缓存；服务端取消公开后刷新移除；首屏/后续分页失败重试；返回先关闭二维码面板。 |

## 自动验证

本地仅编译与 JVM 测试，不组装 APK。隔离检出基线 f3d65977e，叠加赞助相关文件，运行：

```bash
ANDROID_HOME=/home/heizige/Android/Sdk ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests '*Donation*Test'
```

DonationPaymentLinkHelperTest 覆盖 3 例真实链接保真/无码；DonationRepositoryTest 覆盖缓存隔离与过期、失败保留缓存、不完整协议、危险链接、分页、取消、缓存写入失败，以及真实平台兜底不能重新启用远端已禁用渠道。DonationLoadManagerTest 另覆盖首页失败后的独立加载、切换 Hub 拒绝迟到结果、刷新取消分页、快速连点与分页失败保留游标。最新运行结果见交接记录。

Mochi 接入：沿用根主题/导航、共享顶栏与常驻二维码面板；分组与长榜使用库列表、按压和状态组件；新增状态文字均有英文/中文资源。搜索、多页面板、HDR 不适用，不新增。
