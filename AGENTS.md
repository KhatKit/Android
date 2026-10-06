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
  `KhatKitMediaPicker`; returns `List<Uri>`. Only the MD3 style is done — the Miuix branch is
  follow-up work. See [docs/image-toolbox.md](docs/image-toolbox.md) for the upstream mapping.
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

`app/core/` 现有五个子包：`data`(175 kt) / `network`(17) / `ui`(315，含 `icons/` 150) /
`util`(24) / `di`(4)。`service` 已删（commit `86da59fa0`）。`auth` 不在这里（认证已切成独立模块 `:oauth`），
`files` 在 `app/core/data/files/`。`app/feature/` 下现有 22 个功能包、201 kt。

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

### 存量不改名（重要）

现存 `*Service` **30** 个 + `*Util` **9** 个 + `*Utils` **11** 个 + `*Store` **11** 个
= **61 个文件**，**不做批量改名**。理由：

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
> 两个关键点：**按文件名后缀**而非 grep 类名 —— 否则 `*Util` 会命中 `*Utils`
> 内部；**排除 material3** —— `material3/material-color-utilities` 是 **git submodule**
> （`.gitmodules`，mode `160000`），里面 3 个 `*Utils.kt`（`ColorUtils` / `MathUtils` /
> `StringUtils`）不是本 fork 的代码。
>
> ⚠️ **这就是历史上同一件事给出 11 / 13 / 14 三个数的根因**：`material3/…` 是 submodule，
> 所以 `git ls-tree -r --name-only main`（**不递归进 submodule**）得 **11**，
> 而 `find .`（**看得见已 checkout 的 submodule 文件**）得 **14**。
> `docs/architecture-map.md` §2.1 用的就是 `git ls-tree` 那条，与此处等价。
>
> `*Service` 从 32 降到 30，是因为 `core/service/` 被删空时带走了
> `ChatService.kt` 与 `ChatGenerationForegroundService.kt` 两个（commit `86da59fa0`）。

1. 本 fork 约 **54% 的 app 文件来自上游**（405 / 744），整树做过
   `me.rerere.* -> heizige.kk.khatkit.*` 重命名，**`git merge` 上游早已不可用** ——
   commit `8cf9bec2d` 明确写了「直接 merge 会产生大量伪冲突, 因此逐文件三方合并」。
   改名等于给每一次上游同步追加一遍人工三方比对。
2. `:search` 的 18 个 `*SearchService.kt` 还带 `@SerialName` 持久化 key
   （`"bing_local"` 等），改名会让用户已有设置读不出来。
3. `common/cache/CacheStore.kt` 这类确实只做存取的类，改成 `Repository` 是退化。

**新代码按上表命名；旧代码保持原样。** 不要在无关 PR 里夹带改名。

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
