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
- 🖥️ 内置 Web API 与 MCP 服务（供 vFlow / 卡片自动化 / 外部客户端调用）、Markdown 渲染（代码高亮 / LaTeX / 表格 / Mermaid）
- 🪾 消息分支、搜索（Exa / Tavily / Zhipu / Brave 等）、TTS / ASR、AI 翻译、自定义请求头与请求体、快捷消息
- ⬆️ **应用更新**：走 KodeHeadServer 的 KhatKit 独立更新通道（stable / beta / force_min）
- 📝 **更新日志**：见 [CHANGELOG.md](CHANGELOG.md)

## ⬆️ 应用更新

客户端在启动时请求 Hub 上的更新接口，版本号为 Android `versionCode`：

```http
GET https://heizige.top/api/khatkit/app/update?currentVersionCode=186&currentVersion=186&channel=stable
```

```json
{
  "hasUpdate": true,
  "forceUpdate": false,
  "latestVersion": "187",
  "downloadUrl": "/media/khatkit-app-updates/KhatKit-xxx-arm64-v8a.apk",
  "description": "更新说明（Markdown）",
  "packageMismatch": false
}
```

`channel` 支持 `stable` / `beta`，`force_min` 通道满足时返回强制更新。客户端 Hub 地址可在设置里修改（默认 `https://heizige.top`）。

### 按 ABI 分发 + 同包名校验

客户端会上报本机首选 ABI 与自身包名（老客户端不传这两个参数，行为与以前完全一致）：

```http
GET https://heizige.top/api/khatkit/app/update?currentVersionCode=186&currentVersion=186&channel=stable&abi=arm64-v8a&applicationId=heizige.kk.khatkit.debug
```

| 查询参数 | 必填 | 说明 |
| --- | --- | --- |
| `abi` | 否 | 本机首选 ABI（`Build.SUPPORTED_ABIS.first()`）。为空则不发送该参数 |
| `applicationId` | 否 | 本机包名（debug 包为 `heizige.kk.khatkit.debug`，release 为 `heizige.kk.khatkit`） |

服务端选产物规则：`downloadUrls` 命中同名 ABI → 用它；否则有 `universal` → 用它；否则回落该版本原有的 `downloadUrl`。**绝不会把别的 ABI 的产物发出去**（宁可不下发）。

包名校验：已发布版本声明了 `applicationId` 时，只有**同包名**的客户端才会拿到更新；包名不符则返回 `hasUpdate=false`、`downloadUrl=""`、`packageMismatch=true`（`latestVersion` 仍是渠道版本号），`force_min` 通道同样不会强制下发。这样 debug 客户端不会把 release 包当成「新版」装上，导致两个 App 并存。

⚠️ **包名不符是本通道最难察觉的失败模式**：不符时返回的是「没有更新」而**不是错误** —— `hasUpdate=false`、`downloadUrl=""`、UI 上没有任何提示，表现与「新版本就是还没发布」完全一样，`force_min` 也不会强制下发。首次给版本填 `applicationId` 时尤其容易踩：开发机上日常安装的往往是 debug 包（`heizige.kk.khatkit.debug`），只要按直觉填成 release 包名 `heizige.kk.khatkit`，结果就是**你自己这台设备静默收不到更新**（本节的发布示例此前正是这么写的，现已改为留空）。

✅ **按 ABI 分发与包名绑定是相互独立的**：服务端选产物只看 `downloadUrls` + `abi`（见上文规则），`applicationId` 只参与那道包名守卫。所以**过渡期把 `applicationId` 留空，ABI 分发照常生效**，debug 客户端也照常收到更新；等确实需要「防止 debug 客户端把 release 包当新版装上」时再填。

**发布前自检**：

1. **先确认你自己设备上实际安装的包名**：`adb shell pm list packages | grep khatkit`（看装了哪些），再用 `adb shell dumpsys package <包名> | grep -E 'versionCode|versionName'` 核对版本。debug 变体是 `heizige.kk.khatkit.debug`，release 是 `heizige.kk.khatkit` —— 两者是不同的 App，数据也不互通。
2. **只想按 ABI 分发** ⇒ `applicationId` **留空**（等价于老的发布方式，行为与以前完全一致）。
3. **一旦填写**，必须与服务目标设备上**实际安装的包名逐字一致**；填错不会报错，只会对该客户端静默不下发。
4. **推荐顺序**：**先把设备换成 release 包，再开始绑定 `applicationId`** —— 这样两侧包名天然一致，顺带拿到 release 包更小的体积（见 [docs/apk-size-reduction.md](docs/apk-size-reduction.md)）。

发布新版本（管理端需 admin / developer JWT）：

```bash
# 1. 上传 APK，返回 downloadUrl（每个 ABI 各传一次）
curl -X POST https://heizige.top/admin/khatkit/releases/upload \
  -H "Authorization: Bearer <JWT>" \
  -F "file=@app-arm64-v8a-release.apk"

# 2. 发布到通道（version 必须是递增的 Android versionCode）
#    downloadUrls 可选：key 只能是 arm64-v8a / armeabi-v7a / x86 / x86_64 / universal，非法 key 返回 400
#    applicationId 可选：**留空 = 不做包名绑定**（按 ABI 分发照常生效，推荐先用这个）
#      ⚠️ JSON 里写不了注释，所以这里就是唯一的提醒：填写前**必须**先确认自己设备上
#         实际安装的包名（debug 包 = heizige.kk.khatkit.debug，release = heizige.kk.khatkit）。
#         填错不报错，而是该客户端**静默收不到更新** —— 详见上面的「发布前自检」。
curl -X PUT https://heizige.top/admin/khatkit/versions/stable \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
        "version": "187",
        "downloadUrl": "/media/khatkit-app-updates/KhatKit-187-universal.apk",
        "downloadUrls": {
          "arm64-v8a": "/media/khatkit-app-updates/KhatKit-187-arm64-v8a.apk",
          "universal": "/media/khatkit-app-updates/KhatKit-187-universal.apk"
        },
        "applicationId": "",
        "description": "更新说明"
      }'
```

| 发布字段 | 必填 | 说明 |
| --- | --- | --- |
| `downloadUrls` | 否 | 按 ABI 的产物地址表，key ∈ `arm64-v8a` / `armeabi-v7a` / `x86` / `x86_64` / `universal`；出现非法 key 时整次发布返回 400 并点明是哪个 key |
| `applicationId` | 否 | 该产物所属包名；非空时**只对**该包名的客户端下发，**留空则不做包名绑定**（推荐先用留空）。⚠️ 填错不报错，而是该客户端静默收不到更新 —— 填之前先看上面的「发布前自检」 |

| 响应字段 | 说明 |
| --- | --- |
| `packageMismatch` | 发布产物包名与本机包名不符 ⇒ `true`，同时 `hasUpdate=false`、`downloadUrl=""`；老客户端会忽略该字段。⚠️ 客户端当前**不展示**这个字段 ⇒ 从用户视角看就是「没有更新」，排查时请直接看这里的响应体 |

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
- [vFlow](https://github.com/ChaoMixian/vFlow)：卡片对接的自动化能力（本地 Web API）
- Kedge / Khromia：双风格 UI 组件桥
- [Miuix](https://github.com/compose-miuix-ui/miuix)：Miuix 组件库

## 📄 License

本项目基于 [GNU Affero General Public License v3.0](LICENSE)（AGPL-3.0）开源，二次分发需同样开源。
