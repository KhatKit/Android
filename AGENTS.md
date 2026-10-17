# Repository Guidelines

## Project Overview

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
  `KhatKitMediaPicker`; returns `List<Uri>`. 双风格已接（2026-xx 核实）：骨架/顶栏/按钮/底部弹层
  走 `:kedge` 的 KedgePageScaffold / KedgeTopAppBar / KedgeButton / KedgeIconButton /
  KedgeModalBottomSheet，顶栏取色另有 `barSurfaceColor` 显式双支；仅剩
  `MediaAlbumRow`/`MediaImagePager` 的徽标底色用 `material3.Surface`（待换 KedgeSurface）。
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

## 包放置

真实路径前缀是 `app/src/main/java/heizige/kk/khatkit/app/`（下面简写为 `app/`）。

| 规则 | 说明 |
|---|---|
| 一个新功能 = 新增一个 `app/feature/xxx/` 包 | 功能包是**源码包，不是 Gradle 模块**。真要加 Gradle 模块才动 `settings.gradle.kts` |
| 被 **2+ 个功能**用到的代码才提升到 `app/core/` | 先内聚，后提升 |
| feature 内按 `helper/`、数据、Screen、ViewModel 就近内聚 | 不为了"分层好看"把一个功能拆到 core |
| 删功能 = 删文件夹 | 不留孤儿代码 |

`app/core/` 现有五个子包：`data`(190 kt) / `network`(17) / `ui`(324，含 `icons/` 152) /
`util`(24) / `di`(4)。`service` 已删（commit `86da59fa0`）。`auth` 不在这里（认证已切成独立模块 `:oauth`），
`files` 在 `app/core/data/files/`。`app/feature/` 下现有 23 个功能包、215 kt。

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

图标? → app/core/ui/icons/ 按语义(150 个 kt，跨功能共享，不拆进 feature)
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

⚠️ 当前 `app/RouteActivity.kt` 的 `predictivePopTransitionSpec` 用的是 Compose 默认
spec（**spring**，不是 tween），且全仓**没有**任何 `transition.animateXxx` 子动画。
详见 `docs/architecture-map.md` §5，**只记录未改**。
