# Repository Guidelines

> ⚠️ **本仓是 KhatKit（RikkaHub fork），不是上游原文。动手前先读 §「本地约定」和
> §「UI 库：mochi-compose」——上面这段上游描述里的包路径、UI 栈、Mochi 之外的组件库都已过期。**
> 架构规范来源是 `../ARCHITECTURE.md` + `docs/architecture-map.md`；UI 规范来源是
> `../Mochi/docs/zh_CN/guide/`（接入规范 + 设计规范），示例 demo 在 `../Mochi/example/`。

## 项目介绍（本仓现状）

**KhatKit**（`applicationId = heizige.kk.khatkit`，`:34`）是 [RikkaHub](https://github.com/rikkahub/rikkahub)
的深度定制 fork：**原生 Android LLM 聊天客户端 + 卡片（Tool）运行时宿主**。上游给「多服务商对话」底座，
本仓加了卡片系统、Hub 商业化（套餐 / 计费 / 市场）、无障碍自动化、Mochi UI 与自更新通道。

### 四个仓库的分工

| 仓库 | 本地路径 | 角色 |
|---|---|---|
| **KhatKit / Android**（本仓） | `~/文档/Android/Project/KhatKit` | 客户端宿主：UI、卡片运行时、AI 接入、自动化、内嵌服务 |
| **KhatKitCards / Crads** | `~/文档/Android/KhatKitCards` | 卡片仓库：80 个卡目录（77 个已产出 `card.wasm`）+ `moonbit/` 迁移产物 |
| **KodeHeapServer / KServer**（Hub） | `~/文档/IDEA/Project/KodeHeapServer` | 服务端（Ktor + Exposed + Postgres，默认 `https://heizige.top`）：账户 / 套餐 / 计费 / 市场 / 更新 / AI 网关 |
| **Mochi**（mochi-compose） | `~/文档/Android/Project/Mochi` | UI 组件库（token / 交互 / 设计规范）；本地补丁版 `*-khatkit` 发布到 mavenLocal |

### 核心能力

- **多服务商对话**：OpenAI / Google / Anthropic 及兼容端点；Assistant 隔离、消息分支、transformers、
  TTS / ASR、搜索（Exa / Tavily / Zhipu / …）、翻译。
- **卡片系统**：Lua / JS / WASM 三引擎；`CardRunManager` 并发运行与交互排队；bridge 能力
  `tool` / `ui` / `download` / `store` / `shizuku` / `root` / `accessibility`；从 Hub 下载、装包哈希与越界校验。
- **无障碍自动化**：真实 `AccessibilityService` + 触发器 / 工作流，能力暴露给卡片。
- **内嵌服务**：`:web` 启 Ktor，对外 `/api/**` 与 MCP `/mcp`（vFlow / 外部客户端 / Claude Desktop）。
- **工作区与插件**：proot 沙箱（`:workspace`）、`:plugins:ssh` / `:terminal` / `:ocr-mlkit`。
- **UI**：material3 骨架 + Mochi token/组件（唯一外观源，见下节「UI 库」）。
- **自更新**：`GET /api/khatkit/app/update`，按 ABI + 包名校验分发，支持 stable / beta / force_min。

### 模块地图（`settings.gradle.kts` 21 个 include）

- `:app` —— 业务主体：`feature/` 29 个功能包 + `core/` 5 个子包（见「包放置」）。
- `:khatkit` —— 卡片运行时 / UI DSL 模型与 bridge 类型（app 与 khatkit-ui 共用）；
  `:khatkit-ui` —— 卡片 UI 宿主与 Kk 壳，`api(libs.mochi.compose)` 暴露 Mochi。
- `:ai` SDK 抽象；`:search` / `:speech` / `:document` / `:highlight` / `:common` / `:material3` 基础库。
- `:web` 内嵌 Ktor；`:workspace` proot 沙箱；`:oauth` 认证。
- `:mediapicker` 自研媒体选择器；`:card-validator` 卡片校验；`:image-toolbox-dependency` 原生依赖 dex；
  `:yunx-parser`；`:plugins:*`；`:app:baselineprofile`。

### 一条数据流（卡片）

Hub 市场（`GET /api/items`）→ 客户端下载 / 校验（哈希、名称、版本、WASM 头、8 MiB 限制）
→ `CardRunManager` 取计算名额 → Lua / JS / WASM 引擎执行 → bridge（工具 / UI / 网络 / 无障碍）
→ 结果回 AI function-call 或卡片 UI；计费走 Hub `POST /api/tools/authorize`（**失败开放**，只有明确拒绝才拦截）。

## 提交与推送（每次改完都提交）

**规则：一次可独立说明的改动 = 一次提交并推送到 `origin/main`。** 不要攒在本地工作区。

- **只暂存本次文件**：`git add <具体路径>`；**禁止** `git add -A` / `git commit -a` —— 本仓常驻几十个
  别人未完成的 WIP 文件，一次扫进会把无关改动打包进提交。
- 提交前 `git status --short` 确认暂存区只有本次文件；改代码时先跑对应测试 / 构建（见「UI 库」§7）。
- message 照历史风格：`feat(scope): …` / `fix(scope): …` / `docs: …` / `build(mochi): …`，正文中文。
- 推送 `git push origin main`（远端 `ssh://git@ssh.github.com:443/KhatKit/Android.git`）。
  推送被拒时**不要**强推（`--force`），先报告冲突与原因。
- 上游同步走 `upstream`（rikkahub）与 `upstream-ssh`，不要混进 `origin` 的提交历史。

## 验机与 UI 验收（不代操作手机）

**默认不动设备**，除非主人明确说「你来测」。即：不 `adb install` / `adb shell input` / `screencap` /
`screenrecord`、不启模拟器、不跑 `connectedAndroidTest` / `:app:androidTest`、不折腾投屏或 Waydroid。

**交付只做两件事**：

1. **自动能做的**（不碰设备）：`./gradlew :app:assembleDebug`、`:app:compileDebugKotlin`、
   `:app:testDebugUnitTest`（JVM 单测）。
2. **手动测试清单**：每次 UI 改动都按下面四段写，**不写「已真机验证」**。

| 段落 | 内容 |
|---|---|
| 改动点 | `file:line` + 一句话说明改了什么（组件 / 交互 / 样式） |
| 复现路径 | 从哪进：启动页 → 哪个 tab → 哪个设置项 / 页面；需要的前置状态（已装卡 / 已授权 / 有会话） |
| 预期结果 | 逐条可勾选，一条 = 一个可看 / 可点的现象（不要「界面正常」这类空话） |
| 边界 | 深色 / 浅色、玻璃开 / 关、动效减弱、系统大字号、返回手势 / 预测性返回、窄屏 / 宽屏（涉及哪个测哪个） |

清单体例照 `docs/manual-verification.md`；**只编译通过 ≠ 视觉/交互验证**，
不要把「已构建成功」写成「已验证」，也不要把未测项静默略过。

---

## Project Overview（上游原文，路径已过期）

RikkaHub is a native Android LLM chat client that supports switching between different AI providers
for conversations.
Built with Jetpack Compose, Kotlin, and follows Material Design 3 principles.

## Build, Test, and Development Commands

```bash
./gradlew assembleDebug          # 构建 Debug APK
./gradlew test                   # 运行所有模块的 JVM 单元测试
./gradlew lint                   # 运行 Android Lint
```

## Module Structure

- **app**: Main application module with UI, ViewModels, and core logic
- **ai**: AI SDK abstraction layer for different providers (OpenAI, Google, Anthropic)
- **common**: Common utilities and extensions
- **document**: Document parsing module for handling PDF, DOCX, PPTX, and EPUB files
- **highlight**: Code syntax highlighting implementation
- **material3**: Material color utility extensions used by the app UI
- **mediapicker**: Self-implemented media picker (port of `T8RIN/ImageToolbox`'s
  `feature/media-picker`): MediaStore query + album grouping + MD3 grid, drag-select, search/sort
  sheet, fullscreen preview. Replaces the system `GetMultipleContents` picker for chat attachments,
  assistant background, avatar and image-gen reference images. Single entry point
  `KhatKitMediaPicker`; returns `List<Uri>`. UI 栈（U5b 后）：Material3 骨架
  （Scaffold/TopAppBar/Button/ModalBottomSheet）+ Mochi token（MochiTheme 颜色/字阶/圆角），
  mediapicker 只引 material3 与自身；Kedge/Miuix/Khromia 已从依赖树删除。
  See [docs/image-toolbox.md](docs/image-toolbox.md) for the upstream mapping.
- **search**: Search functionality SDK for multiple providers (Exa, Tavily, Zhipu, Bing, Brave, SearXNG, and others)
- **speech**: Speech module for TTS and ASR implementations
- **web**: Embedded web server module that provides Ktor server startup function. Hosts the JSON API (`/api/**`)
  and the MCP endpoint (`/mcp`) used by external clients (vFlow, card automation, Claude Desktop / Cursor).
  Does **not** bundle any web frontend.
- **workspace**: Sandboxed per-workspace file system and shell execution environment exposed to the AI as tools.

## Concepts

- **Assistant**: An assistant configuration with system prompts, model parameters, and conversation isolation. Each
  assistant maintains its own settings including temperature, context size, custom headers, tools, memory options, regex
  transformations, and prompt injections (mode/lorebook). Assistants provide isolated chat environments with specific
  behaviors and capabilities. (app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt)

- **Conversation**: A persistent conversation thread between the user and an assistant. Each conversation maintains a
  list of MessageNodes in a tree structure to support message branching, along with metadata like title, creation time,
  update time, pin status, chat suggestions, optional conversation-level system prompt, and prompt injection bindings. (
  app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **UIMessage**: A platform-agnostic message abstraction that encapsulates chat messages with different types of content
  parts (text, images, documents, reasoning, tool calls/results, etc.). Each message has a role (USER, ASSISTANT,
  SYSTEM, TOOL), creation timestamp, model ID, token usage information, and optional annotations. UIMessages support
  streaming updates through chunk merging. (ai/src/main/java/me/rerere/ai/ui/Message.kt)

- **MessageNode**: A container holding one or more UIMessages to implement message branching functionality. Each node
  maintains a list of alternative messages and tracks which message is currently selected (selectIndex). This enables
  users to regenerate responses and switch between different conversation branches, creating a tree-like conversation
  structure. (app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **Message Transformer**: A pipeline mechanism for transforming messages before sending to AI providers (
  InputMessageTransformer) or after receiving responses (OutputMessageTransformer). Transformers can modify message
  content, add metadata, apply templates, handle special tags, convert formats, and perform OCR. Common transformers
  include:
  - TemplateTransformer: Apply Pebble templates to user messages with variables like time/date
  - ThinkTagTransformer: Extract `<think>` tags and convert to reasoning parts
  - RegexOutputTransformer: Apply regex replacements to assistant responses
  - DocumentAsPromptTransformer: Convert document attachments to text prompts
  - Base64ImageToLocalFileTransformer: Convert base64 images to local file references
  - OcrTransformer: Perform OCR on images to extract text

  Output transformers support `visualTransform()` for UI display during streaming and `onGenerationFinish()` for final
  processing after generation completes.
  (app/src/main/java/me/rerere/rikkahub/data/ai/transformers/Transformer.kt)

## Internationalization

- String resources are usually located in `app/src/main/res/values*/strings.xml`; feature modules such as `search`
  may also maintain their own `values*/strings.xml`
- Use `stringResource(R.string.key_name)` in Compose
- Page-specific strings should use page prefix (e.g., `setting_page_`)
- If the user does not explicitly request localization, prioritize implementing functionality without considering
  localization. (e.g `Text("Hello world")`)
- For `locale-tui` operations, use the `locale-tui-localization` skill.

---

# 本地约定：包结构与命名

> ⚠️ **上面整份文档来自上游 RikkaHub，其中所有 `app/src/main/java/me/rerere/rikkahub/...`
> 与 `ai/src/main/java/me/rerere/ai/...` 路径都已过期。** 本 fork 做过整树包名重构：
> **`me/rerere/rikkahub/*` → `heizige/kk/khatkit/app/*`**，`me/rerere/ai/*` →
> `heizige/kk/khatkit/ai/*`。按上面的路径找不到文件时，改成 `heizige/kk/khatkit/...`
> 再找。其余构建命令、模块说明、Concepts、i18n 约定仍然有效。
>
> 本节是本仓库自己的约定，**只约束新写的代码**。完整的现状记账、每一条数字的来源命令、
> 以及 Nav3 核对结论见 **[docs/architecture-map.md](docs/architecture-map.md)**。

**规范来源（三份，各管一段）**

| 文档 | 管什么 | 本仓落地 |
|---|---|---|
| `../ARCHITECTURE.md`（KodeHead 仓根） | 包结构 / 命名 / 图标 / 版本目录 / Nav3 铁律 | 照做；落地见下方「包放置」「命名」「快速决策卡」「版本目录」「Nav3 三条铁律」 |
| `../Mochi/docs/zh_CN/guide/application-integration.md` | UI 怎么接（主题 / 导航 / 顶栏 / 列表 / 文案） | **UI 唯一规范**，见「UI 库：mochi-compose」§4 |
| `../Mochi/docs/zh_CN/guide/design-principles.md` | 形状 / 颜色 / 文字 / 动效 / 触感 / 内容变换 | 见同节 §3；规则没写到的按 `philosophy.md` 推 |

KhatKit 相对 `ARCHITECTURE.md` 的四处显式差异（适配，不是偏离）：

1. 功能包是**源码包**（`app/feature/xxx/`），不是 Gradle 模块；模块清单才动 `settings.gradle.kts`。
2. Android 框架语义组件保留原名（`*Service` / `*Activity` / `*Receiver` / `*Provider`），见下节例外。
3. 技术栈不按模板 §6 对齐（模板写的是 Cynic 的版本表）；版本以本仓 `libs.versions.toml` 为准，
   理由见 `docs/architecture-map.md` §6。
4. 导航已从 Nav3 `NavDisplay` 换成 Mochi 导航；§8 三条铁律仍是原理，落地在 `MochiNavHost`。

## 包放置

真实路径前缀是 `app/src/main/java/heizige/kk/khatkit/app/`（下面简写为 `app/`）。

| 规则 | 说明 |
|---|---|
| 一个新功能 = 新增一个 `app/feature/xxx/` 包 | 功能包是**源码包，不是 Gradle 模块**。真要加 Gradle 模块才动 `settings.gradle.kts` |
| 被 **2+ 个功能**用到的代码才提升到 `app/core/` | 先内聚，后提升 |
| feature 内按 `helper/`、数据、Screen、ViewModel 就近内聚 | 不为了"分层好看"把一个功能拆到 core |
| 删功能 = 删文件夹 | 不留孤儿代码 |

`app/core/` 现有五个子包（2026-11-28 实测，命令见下）：`data`(216 kt) / `network`(17) /
`ui`(318，含 `icons/` 153) / `util`(25) / `di`(4)。`service` 已删（commit `86da59fa0`）。
`auth` 不在这里（认证已切成独立模块 `:oauth`），`files` 在 `app/core/data/files/`。
`app/feature/` 下现有 29 个功能包、241 kt；`app/src` 共 1035 kt。

```bash
for s in data network ui util di; do
  find app/src/main/java/heizige/kk/khatkit/app/core/$s -name '*.kt' | wc -l
done
ls -d app/src/main/java/heizige/kk/khatkit/app/feature/*/ | wc -l          # 29
find app/src/main/java/heizige/kk/khatkit/app/feature -name '*.kt' | wc -l  # 241
```

**已按 `helper/` + `data/` 细分的功能包（2026-10-17 整理，Page/ViewModel 仍留 feature 根）**：

| 功能包 | `data/` | `helper/` | 说明 |
|---|---:|---:|---|
| `chat` | 9 | 5 | 会话/群回合/队列模型进 data；通知与视角变换进 helper |
| `automation` | 9 | 7 | 触发器模型/日志/队列进 data；tracer/bus gate/调度器进 helper |
| `workspace` | 4 | — | 终端会话、文件类型 |
| `settings` | 3 | — | 搜索服务顺序、工作流偏好、代理测试状态 |

判定口径与 `ARCHITECTURE.md` §1.2 一致：**数据模型/仓库/持久化 → `data/`，无状态打杂 → `helper/`，
Composable 页面与 ViewModel 留 feature 根**。新增功能包按同一口径建目录，不要再往 feature 根堆逻辑文件。

## 命名

3 个词 + 1 个豁免，覆盖所有场景：

| 命名 | 职责判断 |
|---|---|
| `Helper` | **打杂**。有依赖/副作用但**无状态** |
| `Manager` | **管资源/流程**。有状态、有生命周期、要协调多个东西 |
| `Repository` | **管数据**。页面拿数据的唯一入口，外部数据源 |
| `Extensions` | **纯函数/扩展**。无依赖、无状态、无副作用。需要时再建 |

### ⚠️ KhatKit 专属例外：Android 框架语义不在改名范围内

上表**不适用于**这些名字，它们是 Android 平台词汇，改名要同步改
`AndroidManifest.xml` 与 `PendingIntent` 跳转目标：

- `*Service`：继承 `android.app.Service` / `TileService` / `AccessibilityService` /
  `NotificationListenerService` 的类，如 `app/core/network/WebServerService.kt`、
  `app/feature/automation/*Service.kt`。
- `*Activity` / `*Receiver` / `*Provider`：同理（`RouteActivity`、
  `TriggerBootReceiver`、`WorkspaceDocumentsProvider`）。

⇒ 写新代码时，若一个类不是框架组件，就**不要**叫 `XxxService`。

### 存量改名结果（2026-10-17 全量执行完毕）

**B/C/D 类已按 `ARCHITECTURE.md` §2 全部改名，本次共动 47 个 kt 文件。**
2026-10-06 那次"只做 A 类"的决定已被本次覆盖（覆盖理由见本节末尾）。现存基线：

| 后缀 | 现存 | 构成 |
|---|---:|---|
| `*Service` | **9** | 全部继承 `android.app.Service` / `TileService` / `AccessibilityService` / `NotificationListenerService`（框架语义，见上节例外） |
| `*Util` | **0** | — |
| `*Utils` | **0** | — |
| `*Store` | **1** | `app/core/ui/hooks/PlayStore.kt`（Google Play 专有名词，与存储无关） |

⇒ 非合规 **1 个**（仅专有名词），合规率 **89/90 = 98.9%**。

本次改名清单（按类别）：

| 类别 | 数量 | 做法 |
|---|---:|---|
| 纯函数文件 `*Util/*Utils` | 13 | → `*Extensions`：`Chat` / `Clipboard` / `Collection` / `Context`（app + common 各一个）/ `Coroutine` / `Diff` / `Markdown` / `String` / `Time` / `Route` / `Migration` / `ProviderMessage` |
| `object` 打杂 | 6 | `FileUtils`→`FileHelper`、`DatabaseUtil`→`DatabaseHelper`、`EmojiUtils`→`EmojiHelper`、`ImageUtils`→`ImageHelper`、`NotificationUtil`→`NotificationHelper`、`PlayStoreUtil`→`PlayStoreHelper` |
| 文件名含 `Util` 但内容是类 | 1 | `CacheUtil.kt`→`SimpleCache.kt`（按类名命名，类名本就合规范） |
| `common/cache/*Store` | 3 | → `CacheRepository` / `PerKeyFileCacheRepository` / `SingleFileCacheRepository`（有存取状态，归 `Repository`） |
| 非文件级 `*Store` 类 | 2 | `SherpaModelStore`→`SherpaModelManager`、`McpStatusStore`→`McpStatusManager`（有状态） |
| `:search` provider 家族 | 21 + 3 测试 | `interface SearchService<T>`→`SearchRepository<T>`；18 个 `*SearchService` 与 `LinkUpService`/`SearXNGService` → `*Repository`；`SearchServiceOptions`→`SearchOptions`；`getService()`→`get()`；3 个 `*ServiceTest` 同步改名 |

**持久化契约已核对**：`SettingsRepository.kt:221` 序列化的 `searchServices` 字段名未变，
`SearchOptions` 的 19 个 `@SerialName` 判别串（`"bing_local"` / `"zhipu"` / … / `"custom_js"`）未变 ——
本次只改类型名，**不碰 wire 名**，已落盘的 DataStore 设置仍可反序列化。

> **复算口径（必须照抄，否则数字会漂）**：全仓 kt 文件、**按文件名后缀**计数、
> **排除 vendor 进来的 `material3/material-color-utilities/`**、排除所有 `build/`：
>
> ```bash
> for s in Service Util Utils Store; do
>   find . -path ./.git -prune -o \
>     -path '*/material3/material-color-utilities/*' -prune -o \
>     -path '*/build/*' -prune -o \
>     -name "*$s.kt" -print | wc -l
> done
> ```
>
> 2026-10-17 改名后的实测输出：**9 / 0 / 0 / 1**。两个关键点：
> **按文件名后缀**而非 grep 类名 —— 否则 `*Util` 会命中 `*Utils` 内部；
> **排除 material3** —— `material3/material-color-utilities` 是 **git submodule**
> （`.gitmodules`，mode `160000`），里面 3 个 `*Utils.kt`（`ColorUtils` / `MathUtils` /
> `StringUtils`）不是本 fork 的代码。
>
> ⚠️ **这就是历史上同一件事给出 11 / 13 / 14 三个数的根因**：`material3/…` 是 submodule，
> 所以 `git ls-tree -r --name-only main`（**不递归进 submodule**）得 **11**，
> 而 `find .`（**看得见已 checkout 的 submodule 文件**）得 **14**。
> `docs/architecture-map.md` §2.4 记的是改名前的旧账（29 / 9 / 11 / 4），**已过期**。
>
> `*Service` 的变迁：32 → 30（`86da59fa0`）→ 29（`5972207dd`）→ **9**（2026-10-17 本次）。

为什么覆盖 2026-10-06 的"B/C/D 不改"：

1. 主人 2026-10-17 明确要求按 `ARCHITECTURE.md` 执行；命名规范优先级高于上游同步成本。
2. 上次的核心硬理由（`SearchServiceOptions` 的 `@SerialName` 是已落盘判别串）只对 wire 名成立 ——
   实测本次改名没碰任何 `@SerialName` 值与字段名，**改类型名不破坏持久化**。
3. 实际爆炸半径比预估小：`:search` 的 20 个 provider `object` 全仓只有 `SearchRepository.get()`
   一处分发 + provider 选项页 + 测试直接引用，不是"整树散布符号"。

**明码代价**：47 个文件在上游有对应物，以后同步上游时这些文件的比对要"新名 ↔ 旧名"做一次映射；
每个文件都是独立 rename，`git log --follow` 可追。

## 快速决策卡

```
放哪?
  新功能 → app/feature/xxx/        被 2+ 功能用 → app/core/

叫什么?
  有状态/生命周期 → Manager    有数据缓存/读写 → Repository
  打杂无状态 → Helper          纯函数 → Extensions(否则不建类)
  ⚠️ 继承 Android Service/Activity/Receiver → 保留框架名，不套上表

图标? → app/core/ui/icons/ 按语义(153 个 kt，跨功能共享，不拆进 feature)
版本? → gradle/libs.versions.toml（唯一版本来源，模块 build 里不写死版本）
```

## 版本目录

`gradle/libs.versions.toml` 是**唯一**版本来源，模块 `build.gradle.kts` 里禁止写死版本号。
依赖分组用 `[bundles]`（目前只有 `androidTest` 一项）；
**只有「同组 ≥2 个 artifact」且「各模块 configuration 语义一致」才建 bundle** ——
`api(...)` 与 `implementation(...)` 混用时打包会悄悄改模块 API surface。
被否掉的候选见 `docs/architecture-map.md` §6.2。

## Nav3 预测性返回三条铁律

1. **松手续播不写代码** —— 跟手 `seekTo(progress)` 与 commit 后续播都由 `NavDisplay`
   内部处理，业务层只定义动画形状。
2. **跟手效果挂过渡树** —— 页面内进出场效果注册在
   `LocalNavAnimatedContentScope.current.transition` 的子动画上，
   **禁止**用 `NavigationEvent.progress` 驱动（松手即停，commit 时必跳变）。
3. **上限 = 终态幅度** —— progress 拖满时画面恰好停在动画终态。

commit/cancel 由系统阈值判定：业务只在 `onBackCompleted` 里 pop，取消路径不写代码。

**落地已经换成 Mochi 导航（U8）**：`RouteActivity` 不再用 `NavDisplay`，改为
`rememberMochiNavigator` + `MochiNavHost`（`RouteActivity.kt:327`，`predictiveBack` 参数开预测性返回）；
`ScreenRoutes.routeNames` 74 条由 `KkNavController` / `MochiBackedNavStack` 包进 Mochi 栈。
预测性返回的动画形状由 Mochi 内部给；本仓仍**没有**挂在 Nav 过渡树上的圆角/位移子动画
（`transition.animateXxx` 只有弹层/闪烁/关于页 4 处，与返回无关）。

## UI 栈最终形态（2026-11，U8 后）

- **主题**：`MochiTheme` 是唯一 token 源（挂载点 `app/core/ui/theme/Theme.kt:269`），经
  `ProvideMaterial3Theme(amoled)`（`app/themecompat/CompatMaterial3.kt:27`）单一 Bridge 注入；
  MD3 骨架组件直接用即可拿到 Mochi 外观。颜色 37 角色 / 字阶 15 角色 / AMOLED 表面阶梯重锚 /
  `fixed*` 12 角色推导，详见 `docs/mochi-migration-plan.md` §14.4；
  明暗唯一真相是 `ColorMode`（SYSTEM / LIGHT / DARK）。
- **外观三档**：`MochiAppearance.MOCHI / IOS / NOTHING`（`SettingsRepository.kt:822`；
  `MOCHI + mochiNothingPalette` 归一成 NOTHING）。三档只换配色/字体/控件配色，几何与交互不变。
- **导航**：全部路由由 `MochiNavHost` 渲染（`RouteActivity.kt:327`，`ScreenRoutes.routeNames` 74 条）；
  `KkNavController` + `MochiBackedNavStack` 把 route 包成 Mochi 栈；返回单点是 `MochiNavHost(predictiveBack=…)`；
  入口 morph 用 `mochiNavSource`（11 处）+ `LocalKkNavController`。
- **弹层**：`PrimaryBottomSheet` 走 `rememberMochiSheetState` 的 `show()/hide()`（59 处），
  组合树里没有 `if (visible)`；短提示/确认用 `MochiDialog`（25 处）；多字段/列表/网格/长文本保留 sheet。
- **交互**：Kk 壳统一 `KkHaptics` / `kkPressState` / `KkMotionSpecs` / `fadingEdge` /
  `rememberKkReduceMotion`，页面不要手写第二套 spring/scale。
- **三库**（Kedge/Miuix/Khromia）import = 0，**不要重新引入**；`LegacyImportGuardTest` 白名单为空，
  新增 import 直接测试失败。

## UI 库：mochi-compose（唯一 UI 组件与设计规范来源）

> 本仓**用 Mochi 写界面**，不自己发明样式。写任何 Compose 界面前按顺序读：
> `../Mochi/llms.txt`（AI 入口）→ `docs/zh_CN/guide/application-integration.md`（接入规范 +
> 「场景 → 组件对照」）→ `docs/zh_CN/guide/design-principles.md`（设计规范）。
> 规则没写到的情况按 `docs/zh_CN/guide/philosophy.md` 的判断顺序推。

> 上游 Mochi 的 `AGENTS.md` 自己写明「只作用于读取它的工作区，消费项目要在自己的开发说明里引用」——
> 本节的 §3/§4 就是那份引用：**别假设依赖里的 AGENTS 会自动生效**。

### 1. 依赖与版本（本地源码库）

- 坐标：`io.github.feice11:mochi-compose`（基础）/ `mochi-ios`（iOS 外观 + 液态玻璃材质）/
  `mochi-nothing`（Nothing 外观）/ `mochi-icons`（全套 Ionicons）。
- 版本唯一来源：`gradle/libs.versions.toml` 的 `mochi = "0.4.0-khatkit.2"`（`:78`）。
  `:khatkit-ui` 用 `api(libs.mochi.compose)` 向上暴露；`:app` 再引 ios / nothing / icons。
- 后缀 `-khatkit.N` = 上游 pin commit（`.github/scripts/build-mochi.sh` 里 `revision=70636705c…`）
  + 本仓补丁。构建流程：`build-mochi.sh` 按 toml 版本号 fetch 该 commit → 跑
  `.github/scripts/patch-mochi.py` → `publishToMavenLocal`。
- **改库行为的唯一路径**：改 `patch-mochi.py`（每个补丁锚点断言 `count == 1`，不中就退出）
  → bump toml 版本号 → 跑 `build-mochi.sh`。**不要**手改 `~/.m2` 产物，不要把 Mochi 源码
  vendored 进本仓，也不要绕过脚本手改依赖。
- 当前补丁 5 组：overscroll lookahead 视口、`MochiChip` label/trailing 内容槽、
  `MochiDropdown` 条目 `enabled`、菜单/弹窗 IME 避让、`MochiSheet` 改用可视视口高度
  （KhatKit 的列表与内嵌导航靠它）。
- 库侧环境要求：compileSdk 36+、Kotlin 2.3+。换机器/CI **必须先跑** `build-mochi.sh`，
  否则依赖解析直接失败。

### 2. 分层：什么用 Mochi、什么留 material3

| 需求 | 用什么 |
|---|---|
| 主题 / 颜色 / 字阶 / 形状 / 材质 | `MochiTheme`（唯一挂载点 `Theme.kt:269`）+ `MochiDefaults` |
| 页面骨架 / 顶栏 / 大标题 | `MochiLargeTitleScaffold` / `MochiCompactTitleScaffold` / `MochiLazy*Scaffold` / `MochiTitleBar` |
| 长页面 | 必须懒加载：每个分组一项 `item { MochiListSection(…) }`；行多的组用 `mochiListSection` |
| 列表 / 设置项 | `MochiListSection` + `MochiListRow`（当前 31 个文件） |
| 导航 | `rememberMochiNavigator` + `MochiNavHost`；底部 `MochiTabScaffold` / `MochiNavigationBar` |
| 弹层 | `MochiSheet`（state 驱动）/ `MochiDialog`（短提示、确认）/ `MochiDropdown`、`MochiIconDropdown`、`MochiPopoverButton`（按钮弹出） |
| 控件 | `MochiButton` / `MochiIconButton` / `MochiChip` / `MochiSwitch` / `MochiSlider` / `MochiSegmented` / `MochiTextField` / `MochiListTextFieldRow` |
| 图标 | `mochi-icons` 的 `MochiIonicon` |
| 内容变换 | `MochiBlurText` / `MochiMorphIcon` / `MochiCounterText` / `MochiBlurVisibility` |
| 反馈 | 全局 Mochi 胶囊提示（`KkToast` 宿主），不要另引 Sonner 或自绘 Snackbar |
| 滚动 | 根 `ProvideMochiOverscroll`（`RouteActivity.kt:215`）；自研滚动容器用 `MochiScrollDefaults.flingBehavior()`；横滑两头渐隐用 `mochiScrollFadeMask`（`MochiChipGroup` 自带） |
| 卡片 UI DSL 节点 | `khatkit-ui` 侧与页面同源（同一套 Mochi 组件与 token） |

**允许留 material3 的只有两类**：① Mochi 没有语义等价组件的骨架（JSON 高亮编辑框、顶部 tab、
少数自绘统计块）；② `Scaffold` / `LazyColumn` 这类纯骨架。允许的是**骨架**，不是第二套外观。

`khatkit-ui/uikit/mochi/`（21 个 kt，Kk 壳）只在「调用形态要兼容旧页面」或「Mochi 缺等价 API」
时承接；新增壳必须转发到 Mochi 实现，不许在壳里复制一套样式/动画。

### 3. 设计规范要点（写界面前过一遍）

1. 圆角用 `MochiRoundedRectangle` / `MochiCapsule` / `MochiSuperellipse`，**不用 `RoundedCornerShape`**；
   半径从 `MochiTheme.shapes`（卡片/面板 28dp `extraLarge`）。存量 82 个文件带 `RoundedCornerShape`，只减不增。
2. 同心：圆角容器内贴边元素的圆角 = 容器圆角 − 内边距（`MochiConcentric`）；纯文字行从 16dp 起；
   靠边元素不戳进圆角。
3. 颜色只从 `MochiTheme.colorScheme` 与 `MochiDefaults`；页面底 `groupedBackground`、卡片 `groupedContainer`；
   禁用统一 `MochiDefaults.DisabledAlpha`。
4. 字号只从 `MochiTheme.textStyles`；长标题先缩（`minFontScale`）再省略；大号粗体用 `mochiOpticalLeading`。
5. 动效以弹簧为主，**从哪来回哪去**，被打断时从当前状态接着走；系统关动画时弹性效果全关。
6. 内容不瞬切：图标换图标 `MochiMorphIcon`、文字 `MochiBlurText`、出现/消失 `MochiBlurVisibility`、
   数字 `MochiCounterText`；拖动中逐帧读数直接显示。
7. 卡片高度**只有一套动画**（`mochiAnimateHeight`），不要再叠 `animateContentSize`。
8. 按钮弹出的东西一律走菜单引擎（`MochiDropdown` / `MochiPopoverButton` / `MochiConfirmationDialogBox`），
   不要「普通按钮 + 另画浮层」。
9. 按压走 `mochiPressGlow` / `mochiPressDeform`（滚动优先，90ms 后才亮），不要自己 `awaitFirstDown`。
10. 触感少而准：只在状态真的变了才震；用组合原语，不用 `vibrate(ms)`。
11. 文案说事实：不用「·」把标题和数量拼一行、不写口号副标题（沉浸式/极致/丝滑）、不用 emoji 当图标、
    不加渐变底色。

### 4. 接入硬性要求（改 UI 时逐项核对）

- **主题只在根部接一次**：`MochiTheme`（颜色/字阶/形状/材质）+ `ProvideMaterial3Theme`；
  系统栏图标对比度由 `MochiTheme(systemBarAppearance=…)` 接管，页面**禁止**写 `isAppearanceLightStatusBars`
  （当前全仓 0 处，保持）。
- **外观开关必须在主题里**：三档 `MochiAppearance` + 玻璃四开关 `effectsEnabled` / `glassTint` /
  `glassBlurOnly` / `glassButtons` / `mochiGlassStyle`，都在 `Theme.kt` 的 `mochiAppearanceMaterials()`
  汇成 `MochiMaterials`。页面**不写** `if (glass) / if (ios)` 分支，也不按外观给组件手传颜色。
- **导航**：单宿主 `MochiNavHost`；侧推页顶栏用库控件 + 稳定语义 `navId`，图标给稳定 `contentKey`；
  来源打开用 `mochiNavSource` + `push(route, from = sourceKey)` 整页 morph；
  `mochiSharedElement` **已废弃且无效果**，新代码不要用。
- **返回单点**：`RouteActivity` 的 `MochiNavHost(predictiveBack = …)`；页内浮层走 `PageBackInterceptor` 先消费。
  页面不挂 `BackHandler` / `PredictiveBackHandler`。
- **标题**：正文有卡片外分组小标题时用 `MochiCompactTitleScaffold`（已有滚动容器用 `MochiTitleBar`），
  删独立大标题；没有分组小标题才保留大标题。
- **列表**：`MochiListSection` / `MochiListRow`；行高最小 52dp、左右 16dp、卡片圆角 26/28；
  首末行与中间行同一规则，卡片不给首末行另加白；不用「通用卡片 + 裸 Row + 固定 padding」。
- **弹层**：`rememberMochiSheetState` 驱动 `show()/hide()`，组合树里不写 `if (visible)`；
  多页面板用独立导航器，先退子页再关面板。
- **边缘与遮挡**：优先 `mochiEnableEdgeToEdge()`（当前 `RouteActivity.kt:191` 仍是 `enableEdgeToEdge()`，
  属待改项）；自定义浮栏用 `mochiNavOccluder` 登记。
- **动效减弱 / 无障碍**：`rememberKkReduceMotion()`；可点区域 ≥44dp；系统字号放大不裁字。
- **HDR**：可选，`DisplaySetting.mochiHdrHighlights` 默认关，别默认开。

### 5. 参考实例 demo

- `../Mochi/example/`：53 个 kt 的可跑示例。最小骨架
  `example/src/main/java/ui/feice/mochi/starter/MochiAppStarter.kt`（Activity + 主题 + 首页/列表/详情 +
  来源 morph + 共享顶栏，不依赖 demo 私有组件），**新页面直接照它抄结构**。
- 真实组件用法：`example/.../catalog/Catalog.kt`（组件目录）、`DemoAppearance.kt`（外观切换）、
  `common/DemoWidgets.kt`（列表行 / 设置分组）。
- 文档链：`../Mochi/llms.txt` → `docs/zh_CN/guide/application-integration.md` →
  `design-principles.md` → 组件页 `docs/zh_CN/components/*.md`。
- 本仓已落地的样板：`ExploreMarketPage.kt` / `ToolCataloguePage.kt`（列表 + 顶栏 + overscroll）、
  `SettingThemeSubPages.kt`（外观/导航栏设置）、`ChatPage.kt`（玻璃顶栏 backdrop）、
  `KhatKitBottomSheet.kt`（卡片自绘弹层的顶栏/圆角）、`MochiControls.kt`（Kk 壳控件形态）。

### 6. 禁止事项（评审直接打回）

- 引入 Kedge / Miuix / Khromia（`LegacyImportGuardTest` 白名单为空，新增 import 测试直接失败）。
- P4 式过度自绘：为「符号归零」把有状态组件换成自绘件（`docs/mochi-migration-plan.md` §6）。
- 页面里的外观/玻璃分支、按外观手传颜色、自己算明暗。
- 第二套 spring/scale/按压/触感（统一 `KkMotionSpecs` / `kkPressState` / `KkHaptics`）。
- 新代码用 `RoundedCornerShape`；Material Icons / 手绘 Path / emoji 当图标。
- 改 `~/.m2` 产物、把 Mochi 源码 vendored 进本仓、绕过 `patch-mochi.py` 手改依赖。
- `mochiSharedElement`；页面自挂 `BackHandler` / `PredictiveBackHandler`。

### 7. 提交前检查

```bash
cd /home/heizige/文档/Android/Project/KhatKit
./gradlew :app:assembleDebug                 # 0
./gradlew test                               # 0
grep -rn 'isAppearanceLightStatusBars' --include=*.kt app/src/main | wc -l        # 0
grep -rl 'import heizige.kk.kedge'   --include=*.kt . --exclude-dir=build | wc -l # 0
grep -rl 'import top.yukonga.miuix'  --include=*.kt . --exclude-dir=build | wc -l # 0
grep -rl 'import heizige.kk.khromia' --include=*.kt . --exclude-dir=build | wc -l # 0
grep -rn 'RoundedCornerShape' --include=*.kt app/src/main | wc -l                 # 只减不增（存量 82 文件）
```

UI 改动交付按「验机与 UI 验收」写：改动点 + 复现路径 + 预期结果 + 边界；**不要代操作手机**。
