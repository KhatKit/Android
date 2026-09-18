# 更新日志

## 2.5.1（2026-09-13）

> 应用内更新走 KodeHeadServer 的 KhatKit 独立更新通道（stable / beta / force_min）。
> 发布版本时，`description` 字段可直接取本文件对应条目。

### 🧩 KhatKit 卡片系统（新增）

- 卡片运行时：支持 `lua` / `js` / `command` 三类卡片，AI 可直接调用
- bridge 能力：`tool` / `ui` / `download` / `store` / `shizuku` / `root` / `accessibility`
- 卡片触发器：支持 `ai`（模型触发）与 `user`（用户触发）
- 卡片市场：从 Hub 按索引懒加载、sha256 校验后落缓存；本地内置卡片启动时自动同步
- Rust 单引擎：Lua / JS 共用同一核心，检索候选收窄到 20，Shizuku 直连，UI 白名单与审批兜底
- `card-validator`：卡片发布前静态校验
- 内置 12 张极客猫开放平台卡片（快递 / 菜谱 / 简繁转换 / 翻译 / 网盘解析 / 聚合解析 / 视频 / 抠图 / 放大 / 移除对象 / 人脸性别转换 / Key 设置），详见 [docs/zenneko-cards.md](docs/zenneko-cards.md)
- 新增 vFlow 对接卡片（本地 Web API）与无 vFlow 场景的原生自动化能力补齐
- 文档：新增 [Lua 卡片脚本开发指南](docs/lua-card-development.md)

### ⬆️ 应用更新通道（新增）

- 客户端接入 KodeHeadServer 的 KhatKit 独立更新通道（`GET /api/khatkit/app/update`）
- 支持 `stable` / `beta` 通道与 `force_min` 强制更新，启动时检查并提供 Markdown 更新说明
- 服务端提供 APK 上传（`POST /admin/khatkit/releases/upload`）与版本发布（`PUT /admin/khatkit/versions/{channel}`）

### 🛰️ 配套服务端（KodeHeadServer / KServer）

- 新增卡片 Hub：manifest 注册、全量 / 分片索引、embedding 语义召回、共享库 registry、按需打包下载
- 公开接口：`/api/khatkit/index.json`、`/index/shard/{tag}/{page}.json`、`/search`、`/libs/index.json`、`/cards/{slug}`(`/script`、`/download`)、`/app/update`
- 管理接口：卡片 CRUD / 校验 / 重索引、库管理、更新版本发布，均需 admin / developer JWT

### 🎨 界面与交互

- 双界面风格：Material 3 Expressive 与 Miuix（Kedge / Khromia 组件桥），设置内切换即时生效
- 首次启动引导：危险权限（Root / Shizuku / 无障碍）、AI 服务商、主题与配色
- 聊天列表滚动条对齐 Now in Android DraggableScrollbar（轨道 + 可拖动滑块 + 回答位置点）
- 首页顶栏搜索改为抽屉同款 morph 动画（标题交叉淡入搜索框、返回箭头滑入）
- 输入框胶囊化 + Genie 展开动画与预测返回；底栏 FAB 二合一（语音输入 / 发送按状态切换）
- 设置项与弹层迁移到 Khromia / Kedge 组件，主题选择重构；全面 UI 精简
- 新增全局下载中心

### 🎙️ 语音

- 本地 sherpa-onnx 语音识别，引擎按需下载
- 新增 OpenAI / Gemini 转写 ASR，自动选择最佳 provider

### 📦 工作区

- 基于 proot 的 Linux 沙箱，可执行命令、读写文件
- 新增 Shell 兼容模式与 HTML / SVG 预览

### 🐛 修复

- 修复流式生成期间聊天列表滚动条跳变 / 闪现、回答点溢出等问题
- 修复侧边栏搜索进退场动画闪现
- 修复代码块连字特性、统计页面异常 JSON 崩溃等问题

### 🔧 工程

- 包名迁移至 `heizige.kk.khatkit`，Ktor 迁移，Hub / MCP 接入
- 内置极客猫免费 AI provider（默认启用），注册 DeepSeek V4.1 Flash（多模态）
- 裁剪未使用模块（videogen / trace-cli），升级 Kedge 0.1.1 / Khromia 1.6.5
