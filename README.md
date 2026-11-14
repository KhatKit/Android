<div align="center">
  <img src="app/src/main/assets/icons/khatkit.svg" alt="KhatKit" width="100" />
  <h1>KhatKit</h1>
  <p>基于 <a href="https://github.com/rikkahub/rikkahub">RikkaHub</a> 深度定制的原生 Android LLM 聊天客户端</p>
</div>

## ✨ 主要能力

- 🔄 **多服务商**：OpenAI / Google / Anthropic 及任意兼容端点，支持自定义服务商类型与默认模型绑定
- 🧩 **卡片系统**：工具以「卡片」形式从 Hub 按需下载，AI 直接调用；引擎支持 **WASM（默认）/ Lua / JS / command**，见下文「卡片运行时」
- 🛒 **扩展市场**：卡片 / 技能 / MCP / 提示词 / 工作流 / 依赖包 / 服务商插件七类条目统一检索、安装与评分，卡片源在 [KhatKit/Crads](https://github.com/KhatKit/Crads)
- 🎨 **UI 栈**：Material 3 负责骨架、Mochi 提供色彩 / 字阶 / 圆角 token 与按压、回弹、液态玻璃等交互设施；旧的三套外观库（Kedge / Miuix / Khromia）已从依赖树删除
- 👋 **首次启动引导**：危险权限（Root / Shizuku / 无障碍）、AI 服务商配置、主题与配色一气呵成
- ♿ **无障碍自动化**：真实 `AccessibilityService`，支持查找节点、点击、输入、滚动与手势；可**录制人工操作直接存成卡片**，也支持触发器 + 可视化工作流编排
- 📦 **工作区**：基于 proot 的 Linux 沙箱，可执行命令、读写文件，并作为 AI 工具暴露
- 🖥️ 内置 Web API（`/api/**`）与 MCP 服务（`POST /mcp`，Claude Desktop / Cursor 等可直接调用本机卡片）、Markdown 渲染（代码高亮 / LaTeX / 表格 / Mermaid）
- 🪾 消息分支、探索页、搜索（Exa / Tavily / Zhipu / Brave 等）、TTS / ASR、AI 翻译、图片生成、下载中心、用量统计、自定义请求头与请求体、快捷消息
- ⬆️ **应用更新**：走 KodeHeadServer 的 KhatKit 独立更新通道（stable / beta / force_min）
- 📝 **更新日志**：见 [CHANGELOG.md](CHANGELOG.md)

> 本仓是 [RikkaHub](https://github.com/rikkahub/rikkahub) 的深度定制 fork：做过整树包名重构
> （`me.rerere.rikkahub` → `heizige.kk.khatkit`），并持续合并上游提交。
> 卡片、Hub 服务端与 UI 组件库分别在同级仓库 `Android/KhatKitCards`、`IDEA/Project/KodeHeapServer`、`Android/Project/Mochi`。

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

Gradle 模块全部在 `settings.gradle.kts` 的 `include(...)` 里；`app/feature/*` 是**源码包**，不是模块。
下面标 ☁ 的两项是 Cargo crate（`docscard-runtime-wasm` 是库，被 `khatkit-core` 以 path 依赖引用），不进 Gradle 模块表。

| 模块 | 说明 |
| --- | --- |
| `app` | 主应用：`feature/` 27 个功能包 + `core/{data,network,ui,util,di}`；`applicationId = heizige.kk.khatkit`，minSdk 26 / targetSdk 37，仅 `arm64-v8a` + `x86_64` |
| `ai` | 服务商抽象层（OpenAI / Google / Claude / 兼容端点），`UIMessage` 等平台无关消息模型 |
| `khatkit` | 卡片运行时：引擎分发（`EngineKind` = LUA / JS / WASM / COMMAND）、bridge 注册与实现、Hub 客户端、卡片包安装、触发器、录制 |
| ☁ `khatkit-core` | 卡片脚本的 Rust native 核心（JNI）：`mlua`(Lua 5.4) + `rquickjs`(QuickJS) + `docscard-runtime-wasm`，产出 `libkhatkit_core.so` |
| ☁ `docscard-runtime-wasm` | 纯 Rust 的 WASM 运行时（wasmi 0.44，ABI v1），规格与 ABI 契约见其 `docs/` |
| `khatkit-ui` | 卡片表单 / 弹层宿主 + `uikit/mochi/` 的 Kk 壳（按压、回弹、Toast、栏按钮等） |
| `card-validator` | 卡片发布前静态校验（纯 JVM，卡片仓库 CI 复用） |
| `plugins/{ocr-mlkit,ssh,terminal}` | 原生插件。不随基础包编译，各自打成 dex 的 `*.zip` 经 Hub 按需下发 |
| `web` | Ktor(CIO) 内嵌服务器外壳（`startWebServer`），不含前端；路由在 `app/.../core/network/routes/` |
| `workspace` | proot Linux 沙箱：rootfs 安装、shell 执行、沙箱文件系统，作为 AI 工具暴露 |
| `oauth` / `mediapicker` / `image-toolbox-dependency` / `yunx-parser` | OAuth 登录、自研媒体选择器、ImageToolbox dex 依赖包、YunX 网盘协议解析 |
| `search` / `speech` / `document` / `highlight` / `material3` / `common` | 搜索、TTS/ASR、文档解析、代码高亮、Material 色彩工具（含 submodule `material-color-utilities`）、通用扩展 |
| `app:baselineprofile` | Baseline Profile 生成模块 |

`locale-tui`（语言文件翻译 TUI）与 `scripts/`、`tools/` 是开发期工具，不参与 APK 构建。

## 🧩 卡片运行时

- **入口**：卡片由 Hub 下发（标准 WASM 包 = `manifest.json` + `card.wasm`），安装时生成 `card.json`；AI 工具与 MCP 都能调用
- **引擎优先级**：`manifest.runtime` 优先，缺省视为 `wasm`；存量卡（无 `runtime` 且存在 `main.js`）自动识别为 `js`；`command` 卡经 shizuku / root 执行
- **WASM 卡**：走 ABI v1（导出 `kk_alloc/kk_free/kk_handle/kk_init/kk_shutdown/kk_last_error`），脚本内 `host_call` 全部转成既有 Kotlin bridge，因此 `ui` / 无障碍 / 网络 / 图像能力与 Lua/JS 卡共用同一实现
- **bridge 全表**：见 [docs/script-api-reference.md](docs/script-api-reference.md)（`tool` / `net` / `fs` / `json` / `crypto` / `time` / `host` / `system` / `ai` / `mediaPicker` / `ui` / `web` / `browser` / `download` / `store` / `schedule` / `imageToolbox` / `shizuku` / `accessibility` / `root`）；该文件与 `Bridges.kt` 由 `BridgeApiDocTest` 双向校验，改接口必须同步改文档
- **限额**：Lua/JS 引擎内存 64 MiB、默认 120 秒计算预算（等用户交互的时间不计）；WASM 用 fuel 限额；包体积上限 8 MiB、解压 32 MiB
- 细节见 [docs/card-runtime.md](docs/card-runtime.md)、[docs/card-runtime-wasm.md](docs/card-runtime-wasm.md)、[docs/lua-card-development.md](docs/lua-card-development.md)

源码仓在 **KhatKit/Crads**（`Android/KhatKitCards`），与本仓分开维护、独立发版。

## 🔨 构建

- **JDK 21** + Android SDK（compileSdk 37）+ Gradle 9.7.1（wrapper 自带）
- 改卡片 native 侧还需要 **NDK 28.2.13676358** 与 Rust：`khatkit-core/build-android.sh` 交叉编译 arm64-v8a / x86_64 并把 `libkhatkit_core.so` 拷进 `khatkit/src/main/jniLibs/`（仓库里已有构建好的 .so，只改 Kotlin 不用重编）
- UI 依赖 `io.github.feice11:mochi-compose` / `mochi-ios` / `mochi-icons` 的 `*-khatkit` 版本（上游 Maven Central 没有这些 API）。本地要先克隆 `feice11/mochi-compose`、跑 `.github/scripts/patch-mochi.py` 再用 `publishToMavenLocal -PVERSION_NAME=<libs.versions.toml 里的 mochi 版本>` 发布；CI（`build-apk.yml` / `daily-build.yml`）在构建前自动完成这一步

```bash
./gradlew assembleDebug                    # 构建 Debug APK
./gradlew test                             # 全部模块 JVM 单元测试
./gradlew lint                             # Android Lint
./gradlew :khatkit:testDebugUnitTest       # 卡片运行时（含 bridge 文档一致性校验）
```

## 🙏 致谢

- [RikkaHub](https://github.com/rikkahub/rikkahub)：本项目基于其深度定制
- [Mochi Compose](https://github.com/feice11/mochi-compose)：UI token 与交互设施（本仓通过 patch 脚本打 `-khatkit` 版本）
- [vFlow](https://github.com/ChaoMixian/vFlow)：卡片对接的自动化能力（本地 Web API）
- [ImageToolbox](https://github.com/T8RIN/ImageToolbox)：图像工具箱依赖包与媒体选择器参考
- [YunX](https://github.com/CYQawa/YunX)：网盘协议解析（`:yunx-parser`）
- [wasmi](https://github.com/wasmi-labs/wasmi) / [mlua](https://github.com/mlua-rs/mlua) / [rquickjs](https://github.com/DelSkayn/rquickjs)：卡片脚本运行时

## 📄 License

本项目基于 [GNU Affero General Public License v3.0](LICENSE)（AGPL-3.0）开源，二次分发需同样开源。
