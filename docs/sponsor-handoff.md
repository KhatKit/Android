# 赞助功能交接

更新：2026-11-28。原交接中的“后端完全没改、前端只有常量”已由本轮实现替代。操作与手动验收见 [赞助页说明](sponsor-page.md)，平台价格与介绍见 [订阅档位建议](sponsor-pricing.md)。

## 当前实现

客户端：

- [页面](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingDonatePage.kt) 保留 Mochi 三标签、分享、二维码放大、保存及复制；进入页面刷新资料。
- [数据层](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/data/DonationRepository.kt) 请求当前配置的 Hub，连接/请求超时 5/8 秒；按 Hub 隔离缓存并限制有效期为 24 小时；网络或协议失败保留最后正确缓存，提示重试。
- [协议模型](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/data/DonationModels.kt) 将 profile、channels、acknowledgements、acknowledgementTotal 及项目必要字段设为必填；字段缺失不等于有效空数据。
- [加载管理](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/helper/DonationLoadManager.kt) 由 ViewModel 生命周期管理，切 Hub/刷新撤销旧请求，并用代次阻止迟到结果覆盖。鸣谢分页可在首页配置失败后独立重试。
- [社区区块](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/donation/DonationCommunityCards.kt) 读取远端名称、简介、头像、链接、邮箱、渠道和前三条公开鸣谢；邮箱按 mailto URI 编码。
- [状态与完整榜](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/donation/DonationRemoteStatus.kt) 区分加载失败与真实空列表；每页 50 条，懒加载行，支持重试；记录 key 与分页控制行隔离。
- 英文/中文赞助资源各 42 条，key 集合相同；未新增自绘主题或外观分支。

后端：

- KodeHeapServer 新增模型、三张 Exposed 表、事务服务及赞助路由；应用启动时增量建表并注册。
- 公开 GET /api/sponsors 与 GET /api/sponsors/acknowledgements；管理 GET /api/admin/sponsors 和三个 PUT（profile/channels/acknowledgements）。
- 管理接口沿用 auth-jwt + admin/developer；渠道需启用，鸣谢同时需要 consent=true；先过滤再分页和计数。
- 渠道/鸣谢为原子全量替换，限制文本/列表数量，检查 URL 与重复 ID；默认只有 KhatKit 真实项目资料，没有样例收款人或鸣谢数据。
- 完整协议与维护示例见 [服务端说明](../../../../IDEA/Project/KodeHeapServer/docs/sponsors.md)。

## 真实资料与付款边界

本地二维码保持原资源：支付宝 https://qr.alipay.com/fkx12240cdgdefoj6flf078；微信 wxp://f2f0BYJU7FHYqAPrDQLmsw41Jt4NGKO94p_yrAHiQeUJPJ8。复制仍从实际 drawable 解析，不拿字符串替换二维码。

用户确认仅支持微信、支付宝、爱发电和 Ko-fi；Buy Me a Coffee 与 GitHub Sponsors 已移除。维护者随后确认 Ko-fi 已审核通过；爱发电 https://afdian.com/a/heizige 与 Ko-fi https://ko-fi.com/heizige 均启用并提供首次离线兜底。远端列表成功加载后，禁用/删除具有优先权。企业邮箱、个人资料与公开鸣谢名单仍待提供，没有提供的数据不填样例。金额芯片只是建议，未接支付回调、未统计到账、未自动发放 AI 额度。平台付款与公开同意由管理员核实后录入。

离线缓存的首页鸣谢最多保留 24 小时；撤回同意不会保证立即删除用户设备上的离线副本。完整榜每次在线加载。

## 验证记录

隔离检出基线 f3d65977e，叠加本次 16 个源码/资源/测试文件；确认四个渠道及真实主页后，主工作区与隔离检出均通过 Kotlin 编译与 16 项赞助回归（exit 0）：二维码 3、数据源 9、生命周期/分页 4，failures=0、errors=0。包括真实平台链接兜底、远端禁用优先级、邮箱兼容及 mailto 编码。

主工作区最终 Kotlin 编译与同样 16 项赞助回归通过（exit 0，failures=0、errors=0）。首次定向测试因 Gradle 结果文件 in-progress-results-generic.bin 缺失 exit 1；使用一次性 Gradle 初始化参数复跑后通过，未修改仓库构建配置；AGP 最终仍将 XML 写入默认目录，因此另行归档了通过的 XML，最终批准渠道的两组结果各为 16 项通过。验证日志与归档保留在 /home/heizige/文档/.khatkit-sponsor-validation/。

后端 ./kotlin build 与 ./kotlin test --include-classes='*Sponsor*Test' 均 exit 0，4 项 H2/API 用例全部通过、0 失败：默认真实渠道、不复活禁用项、JWT 角色鉴权、公开同意过滤、分页及非法输入原子保留。后端提交 c4fbbfd 已推送 kServer/master；协议与日志位置见服务端说明。

未组装 APK，未代操作设备，也未进行视觉、扫码或真实支付验证。手动验收四段在赞助页说明中，不能把编译通过写成真机验证。

## 提交与后续操作

只提交赞助相关文件；原工作区已有 30 项无关暂存内容，使用 git commit --only 的精确路径模式保留它们。保存 PNG 的旧辅助文件中另有无关未提交 import，不混入本次提交。

后端已推送现有 kServer/master，客户端推送 origin/main。后端尚未部署；部署后再通过管理员接口填真实渠道，客户端会在重新进入或点击重试时读取。爱发电档位建议为 ¥5/¥15/¥50 每月，主推 ¥15；Ko-fi 按维护者实际设置提供一次性赞助，默认 $5、金额可自定义，不描述为月订阅。平台费率以各自创作者后台为准。
