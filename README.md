<div align="center">
  <img src="app/src/main/assets/icons/khatkit.svg" alt="KhatKit" width="100" />
  <h1>KhatKit</h1>
  <p>基于 <a href="https://github.com/rikkahub/rikkahub">RikkaHub</a> 深度定制的原生 Android LLM 聊天客户端</p>
</div>

## ✨ 主要能力

- 🔄 **多服务商**：OpenAI / Google / Anthropic 及任意兼容端点，支持自定义服务商类型与默认模型绑定
- 🧩 **KhatKit 卡片系统**：从 Hub 按需下载 `lua` / `js` / `command` 卡片，AI 可直接调用；bridge 能力包括 `tool` / `ui` / `download` / `store` / `shizuku` / `root` / `accessibility`
- 🎨 **双界面风格**：Material 3 Expressive 与 Miuix（基于 Kedge 组件桥），设置内切换即时生效
- 👋 **首次启动引导**：危险权限（Root / Shizuku / 无障碍）、AI 服务商配置、主题与配色一气呵成
- ♿ **无障碍自动化**：真实 `AccessibilityService`，支持查找节点、点击、输入、滚动与手势，能力暴露给卡片
- 📦 **工作区**：基于 proot 的 Linux 沙箱，可执行命令、读写文件
- 🖥️ 内置 Web 访问、MCP、Markdown 渲染（代码高亮 / LaTeX / 表格 / Mermaid）
- 🪾 消息分支、搜索（Exa / Tavily / Zhipu / Brave 等）、TTS / ASR、AI 翻译、自定义请求头与请求体、快捷消息
- ⬆️ **应用更新**：走 KodeHeadServer 的 KhatKit 独立更新通道（stable / beta / force_min）

## ⬆️ 应用更新

客户端在启动时请求 Hub 上的更新接口，版本号为 Android `versionCode`：

```http
GET https://heizige.space/api/khatkit/app/update?currentVersionCode=186&currentVersion=186&channel=stable
```

```json
{
  "hasUpdate": true,
  "forceUpdate": false,
  "latestVersion": "187",
  "downloadUrl": "/media/khatkit-app-updates/KhatKit-xxx.apk",
  "description": "更新说明（Markdown）"
}
```

`channel` 支持 `stable` / `beta`，`force_min` 通道满足时返回强制更新。客户端 Hub 地址可在设置里修改（默认 `https://heizige.space`）。

发布新版本（管理端需 admin / developer JWT）：

```bash
# 1. 上传 APK，返回 downloadUrl
curl -X POST https://heizige.space/admin/khatkit/releases/upload \
  -H "Authorization: Bearer <JWT>" \
  -F "file=@app-universal-release.apk"

# 2. 发布到通道（version 必须是递增的 Android versionCode）
curl -X PUT https://heizige.space/admin/khatkit/versions/stable \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"version":"187","downloadUrl":"/media/khatkit-app-updates/xxx.apk","description":"更新说明"}'
```

## 🧱 模块结构

| 模块 | 说明 |
| --- | --- |
| `app` | 主应用（UI / ViewModel / 数据层） |
| `ai` | 服务商抽象层（OpenAI / Google / Claude） |
| `khatkit` | 卡片运行时、bridge、Rust 引擎接入（Lua/JS 同一核心） |
| `khatkit-ui` | 卡片市场与表单宿主，Kedge 双风格桥接 |
| `card-validator` | 卡片发布前静态校验 |
| `search` / `speech` / `document` / `highlight` / `material3` / `common` / `web` / `workspace` / `oauth` | 功能模块 |
| `khatkit-core` | Rust 脚本引擎（Cargo） |

## 🔨 构建

- JDK 21 + Android SDK
- 同级目录存在 `../Kedge`、`../Khromia` 时走 composite build 用源码构建，否则从 GitHub Packages 解析发布版

```bash
./gradlew assembleDebug   # 构建 Debug APK
./gradlew test            # JVM 单元测试
./gradlew lint            # Android Lint
```

## 🙏 致谢

- [RikkaHub](https://github.com/rikkahub/rikkahub)：本项目基于其深度定制
- Kedge / Khromia：双风格 UI 组件桥
- [Miuix](https://github.com/compose-miuix-ui/miuix)：Miuix 组件库

## 📄 License

本项目基于 [GNU Affero General Public License v3.0](LICENSE)（AGPL-3.0）开源，二次分发需同样开源。
