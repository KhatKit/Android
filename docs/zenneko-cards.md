# 极客猫（ZenNeko）开放平台卡片

本仓库内置了一组调用 [极客猫开放平台](https://platform.zenneko.top) 的 Lua 卡片，接口全部为中文场景，宿主侧通过
`tool.httpMultipart` / `tool.readBase64` / `tool.saveBase64` 等 bridge 完成 multipart 上传与图片落盘。

## 卡片清单

| 卡片 | 接口 | 参数（doc 字段名） | 费用 |
|---|---|---|---|
| `zenneko_kuaidi` | 快递查询 | `nu` | 免费 |
| `zenneko_caipu` | 菜谱大全 | `type` `rankType` `keyword` `id` `page` | 免费 |
| `zenneko_chineseconverter` | 简繁转换 | `data` `type`(s2t/t2s) | 免费 |
| `zenneko_translate` | AI 翻译（SSE） | `originText` `targetLang` `sourceLang` `aiEngineName` | 免费 |
| `zenneko_pan` | 网盘解析 | `operation` `url` `passcode` `file_id` `parent_id` `cookie` `access_token` `token` | identify/open/list 免费；direct_link 成功 ¥0.02 |
| `zenneko_aggregate` | 聚合解析 | `action` `channel` `url` | getChannels 免费；parse 成功 ¥0.02 |
| `zenneko_video` | 视频聚合搜索 | `action` `keyword` `site` `page` `url` `vid` `cid` `dmid` `text` `time` `color` `type` `size` | search/episodes/danmaku 免费；play 成功 ¥0.02 |
| `zenneko_cutout` | 智能抠图 | `image`（本地路径/data URL）`model` | 成功 ¥0.02，输出 PNG |
| `zenneko_upscale` | 图像放大 | `image` `scale` | 成功 ¥0.02，返回远程图片链接 |
| `zenneko_removeobject` | 移除对象 | `image` `mask` | 成功 ¥0.02，输出 WebP |
| `zenneko_face_gender` | 人脸性别转换 | `image` `gender` | 成功 ¥0.02，输出 JPG |
| `zenneko_set_key` | 设置 API Key | `key` | 免费 |

> 付费接口仅**成功**调用时扣费（失败不扣费）。图片类卡片默认把结果保存到
> `/sdcard/Download/zenneko_<接口>_<时间戳>.<扩展名>`；`zenneko_upscale` 返回远程链接，未自动下载。

## API Key 配置

1. 在极客猫开放平台控制台「API Keys」页创建 Key（`sk-` 开头）。
2. 运行卡片 `zenneko_set_key`，填入 Key；卡片会用免费快递查询接口验证后保存。
3. 所有 `zenneko_*` 卡片读取同一个 `zenneko_key`：
   优先读取卡片共享区（`store.sharedRead`），其次本卡片 Keystore 密钥（`store.secretGet`），
   最后回退到内置默认 Key。内置默认 Key 为计次类型，开箱即用，建议换成自己的 Key。

鉴权统一通过请求头 `Authorization: Bearer <key>` 与 `X-Open-Key: <key>` 携带，不使用表单字段 `open_key`。

> 注意：卡片共享区（`filesDir/khatkit/shared/` 下）为应用私有目录，但以明文保存 Key；
> 密钥同时写入 `zenneko_set_key` 卡片的 Keystore 加密存储。

## 常见错误

- `API Key 无效或未授权，请运行 zenneko-set-key 更新密钥`：Key 错误/过期，或未携带。
- `账户余额不足，请在极客猫开放平台充值`：付费接口需要余额。
- 图片类卡片读取本地文件需要「所有文件访问」权限：在 KhatKit 卡片市场 → 设置中授予。

## 实现备注

- `zenneko_pan` 暂不支持 doc 中的 `headers` 自定义请求头对象参数。
- `zenneko_translate` 的 SSE 响应由 `tool.httpPost` 整体读回后拼接 `text`/`content` 片段，非真流式展示。
- `zenneko_upscale` 只返回 CDN 图片链接（接口不返回 base64），如需落盘可自行打开链接下载。
- 文档与实际服务的差异（2026-09 实测）：视频接口文档写 `video_search.php`，线上实际为 `video_aggregate.php`（卡片已按线上调整）；`face_gender.php` 线上返回 404，卡片保留文档地址并在 404 时给出中文提示。
