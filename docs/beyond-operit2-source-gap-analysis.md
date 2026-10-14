# 与 Operit2 的增量差距分析（源码级盘点）

> 目标：回答用户「看看和 operit 差什么」。本文件只补 **Operit2**，不改动
> `beyond-operit-open-source-references.md`（核的是 Operit v1）、`beyond-operit-upgrade.md`
> （G1–G14 矩阵）与 `beyond-operit-implementation-status.md`（实施/验收台账）的任何既有内容。
>
> - 对照对象：`AAswordman/Operit2`，`git rev-parse HEAD` =
>   **`9ae1e558a66f96d81ff95a499bd39bda7da6c122`**（`git log -1` = 2026-10-14 11:09:13 +0800，
>   `fix: 迁移原生 JSON 偏好存储，统一配对与会话删除`）。
> - 只读工作副本：`/tmp/operit2`（`git clone --depth 1 --single-branch`；未加入本仓、未加 remote）。
> - 口径：**只做对照，不复制、不改编任何源码**；本文件只含路径 + 行号 + 极短摘录。
> - 证据纪律（沿用 `beyond-operit-open-source-references.md` 开头口径）：README / 技术白皮书 /
>   docs/*.md **不能**作为「Operit2 有 X」的证据，只能用来定位关键词；每条结论必须落到源码
>   file:line。查不到就写「未找到」，不推测。

---

## 0. 结论速览

1. **Operit2 不是 Operit v1 的 Android App 续作，而是跨设备 Agent 平台**：Rust core（`core/crates/`
   下 11 个 crate、约 850 个 `.rs`）+ Flutter app（`apps/flutter/`，1181 个 `.dart`）+ 9 个平台
   host（`hosts/{android,ios,macos,linux,windows,ohos,web,apple,common}`）+ 一套插件体系
   （TS / Compose DSL / Kotlin·Dart·Rust SDK）。它的核心叙事是 `CoreNode` / `Space` / `Binding`
   的**多设备协同与任务接管**——这条轴在 G1–G14 矩阵里完全没有。
2. **许可与任务前提不符（重要）**：Operit2 仓库根 `LICENSE` 是 **AGPL-3.0**，不是 LGPL-3.0
   （`/tmp/operit2/LICENSE:1` = `GNU AFFERO GENERAL PUBLIC LICENSE Version 3`）；只有
   `plugins/types-v1/LICENSE:1` 另附 LGPL-3.0。AGPL 带网络 copyleft，对闭源/未定许可证的 app
   更不可接受 ⇒ 本次所有条目一律按「**只能借鉴机制、独立重写**」或「**不可用**」处理，
   不存在「可移植并保留声明」的条目。
   （**口径性质**：这是本项目自定的更严政策，不是「AGPL 与 AGPL 许可不兼容」——本仓自身根 `LICENSE` 也是 AGPL-3.0；
   完整表述与 AGPL §13 网络 copyleft 对本项目 App + `heizige.top` 两个交付面的含义见
   `docs/beyond-operit-open-source-references.md` 的 `## A6` 一节。t27 仅补指针，未改此事实。）
3. 相对 G1–G14 的增量判定（14 项，逐项证据见 §3；**t27 已按本地证据系统复核，见 §0.5**）：
   **Operit2 有而我们缺 2 项**（G9 工作区工程化【模板/文件同步】、G11 唤醒词）、
   **双方都有 9 项**（G2 / G3 / G4 / G5 / G7 / G8 / G10 / G12 / G14）、
   **我方领先 1 项**（G6 群聊：本地有实现与端到端用例，Operit2 侧源码 0 命中）、
   **双方都没有 1 项**（G13 虚拟形象）、
   **不适用 1 项**（G1 端侧推理：我方产品决策裁剪；端侧只有 ASR，LLM 全云端）。
   ⚠️ **记忆体系（G4）不是差距**——我方已有记忆空间/分块/图谱边/向量索引/自动抽取（详见 §0.5 勘误）。

---

## 0.5 勘误与系统性复核（2026-10-07，t27）

**一句话**：§0 与 §3 里**我方侧**的判定此前只查到最上层 API、漏了实现层；本轮把「我们缺 / 我们已超越 / 双方都没有」逐条重核并补上本地 file:line。**Operit2 侧的源码证据链一字未动**（仍钉在 `AAswordman/Operit2 @ 9ae1e558a66f96d81ff95a499bd39bda7da6c122`）。

| 项 | 原判定（t21） | 现判定（t27） | 依据（本地 file:line） |
|---|---|---|---|
| G4 长期记忆 | **Operit2 有而我们缺**、P0「最大实质差距」 | **双方都有**，优先级 P2 | `db/entity/MemorySpaceEntity.kt:17`、`db/entity/MemoryChunkEntity.kt:25`、`db/entity/MemoryEdgeEntity.kt`、`db/entity/MemoryMentionEntity.kt`、`db/MemoryVectorIndex.kt`、`db/dao/MemoryGraphDAO.kt:14`、`db/dao/MemorySpaceDAO.kt`、`db/dao/MemoryChunkDAO.kt`、`core/data/repository/MemoryRepository.kt:25`、`MemoryRetrievalEngine.kt`、`MemoryExtractor.kt:32`、`core/data/ai/tools/MemoryTools.kt:27`（onAdd 带 `sourceMessageId` + `confidence`）、UI `feature/assistant/detail/AssistantMemoryPage.kt` |
| G6 群聊 | 我方已超越（证据偏弱，只引了台账） | 我方领先（**证据补齐**） | `core/data/model/GroupChat.kt`、`feature/chat/GroupTurnCoordinator.kt`、`feature/chat/GroupChatPage.kt`、`feature/chat/GroupPerspectiveTransformer.kt:10`、`core/data/db/dao/GroupRunDAO.kt`、`core/data/db/entity/GroupRunEntity.kt`、`androidTest/.../db/C1GroupUiE2EFixtureTest.kt`、台账 `:36-41` |
| G8 运行时扩展 | 对方强项（我方「只有 dex 依赖包」） | 双方都有、对方更宽 | 我方另有 QuickJS：`core/data/ai/tools/local/JavascriptTool.kt:3`（`com.dokar.quickjs`）、`feature/explore/ExploreMarketPage.kt:146`（「JavaScript 引擎」）；dex 依赖包 `khatkit/.../dependency/DependencyManager.kt:55` |
| G12 入口形态 | 我方已超越 | **双方都有**（我方磁贴/浮窗/软件内看板更强；对方全局悬浮窗+通知监听更宽） | `AndroidManifest.xml:170`、`:180`、`:190`、`feature/automation/TriggerTileService.kt:18`、`AutomationOverlayService.kt`、`AutomationInAppHost.kt`；ASSIST/Widget 我方 0 命中 |
| G9 工作区 | 对方有而我们缺 | **保留「缺」**，补足证据并写明边界 | 我方有 `feature/workspace/WorkspaceTerminalSession.kt:114`、`AndroidManifest.xml:242`（`WorkspaceDocumentsProvider`）、**应用级**备份 `core/data/sync/BackupManager.kt`；**工作区级**模板/同步/回滚**未找到** |
| G11 唤醒词 | 对方有而我们缺 | **保留「缺」** | 我方 `wakeword` / `唤醒词` 0 命中；有 `core/ui/components/ai/VoiceModeRow.kt`、`feature/chat/VoiceSessionController.kt`、`tools/local/TextToSpeechTool.kt` |
| G13 虚拟形象 | 双方都没有 | **保留** | `core/data/model/Avatar.kt:7`（`sealed class Avatar`）、`:10` `Dummy`、`:14` `Emoji`、`:18` `Image`；`live2d` / 桌面宠物 0 命中 |
| §4 多设备 | 我方「无」对位物 | 降级为**「未找到」**（未做穷尽证明） | `app/`、`khatkit/`、`common/` 内未见节点/空间/任务接管实现；现有网络面只有 `:web` 模块与 `hub/HubAccountRepository.kt:19` |

**错因（写下来防止再犯）**：t21 对我方侧的核查只到 `core/data/ai/tools/MemoryTools.kt` 这一层（工具 API），没往下看 `db/entity` → `db/dao` → `db/MemoryVectorIndex.kt` → `core/data/repository` 这条**实现链**；G8 同样漏了 `core/data/ai/tools/local/JavascriptTool.kt`（QuickJS）。
**规则（本文件后续自查用）**：判断「我方有没有 X」必须至少覆盖**四层**——工具/入口 → 服务/仓库 → 实体/存储（含向量索引） → DI/装配；任一层命中都要写进证据。只查一层得出的「缺」一律降级为「**未找到**」。

**对 §5 优先级的影响**：原 P0（记忆体系）**撤销**——该差距不存在，剩余是「跨角色共享记忆库 + 检索评分可视化 + 用户资料注入」这类**增量项**（P2）。重选后的顺序见 §5：**P0 无强制项（先做产品决策）**，建议先做 G9 的模板 + 备份（独立、低风险），唤醒词与工作区同步列 P1。

---

## 1. Operit2 的结构证据（顶层，来自钉死 SHA 的工作副本）

| 目录 | 内容 | 证据 |
|---|---|---|
| `core/crates/` | Rust 内核，11 个 crate：`foundation(146 .rs)`、`runtime(139)`、`provider(105)`、`plugin(88)`、`tool(84)`、`node(75)`、`persistence(48)`、`proxy(25)`、`command(25)`、`rslink(6)`、`application(3)` | `core/crates/CRATE_BOUNDARIES.md`；各 crate 目录 |
| `apps/flutter/` | 主 UI（Flutter/Dart，1181 个 `.dart`），Android/iOS/macOS/Linux/Windows/OHOS 目标 | `apps/flutter/app/lib/{core,data,ui,util}` |
| `apps/server`、`apps/cli`、`apps/esp32`、`apps/pb_sbc01_h3` | 服务端、CLI、ESP32、开发板固件形态 | 目录 |
| `hosts/` | 每平台能力宿主；`hosts/android/src` 24 个 `.rs`（`terminal.rs`、`web_visit.rs`、`tts_synthesis.rs`、`system_operation.rs`、`local_inference.rs` …） | `ls hosts/android/src` |
| `plugins/` | 插件体系：`sdk/`（clients: dart/kotlin/rust/typescript）、`types/`（`*.d.ts` 生成契约）、`packages/buildin/`（内置包）、`skills/` | `plugins/packages/buildin/*` |
| `tools/` | 构建/测试/跨平台运行时脚本（`tools/tests/*.mjs`、`tools/esp32-editor` 等） | 目录 |

内置插件包（`plugins/packages/buildin/`）本身就是一份能力清单：
`browser.ts`、`character_cards/`、`workflow/`、`goal_mode/`、`plan_mode/`、`daily_life/`、
`thinking_guidance/`、`message_translation/`、`operit_editor.ts`、`system_tools.ts`、
`super_admin.ts`、`ai_chat.ts`、`time.ts`、`12306.ts`、`crossref.ts`。

---

## 2. 证据与许可口径

- **证据等级**：本文件里所有「Operit2 有 X」都给出 `路径:行号`；未找到的条目直接写「未找到」并
  列出所用关键词。G 矩阵与台账的说法只作为**待核对线索**，不作为我方现状的证据。
- **许可三级口径**：
  - `不可用`：AGPL-3.0 代码不可进入本仓任何构建产物（含直接复制、改编、粘贴片段）。
  - `只能借鉴机制、独立重写`：可读其设计与契约，用我们自己的架构与命名重写；实现不得逐行照搬。
  - `可移植并保留声明`：仅当来源目录明确为宽松/LGPL 且与本项目许可证相容——**本次无条目落入
    此类**（根 AGPL；`plugins/types-v1` 虽为 LGPL-3.0，但类型契约与运行实现强耦合）。
- **结论**：§3 的每一条「优先级」都只针对**能力**，不针对代码；任何落地都必须独立实现。

---

## 3. 逐项对照 G1–G14

判定记号：`O`＝Operit2 有、`O-`＝部分/机制不同、`O?`＝未找到证据、`K`＝我方有、`K+`＝我方更强。

\1- **t27 本地复核**：端侧 **LLM** 在我方 0 命中（`grep -rn -i "llama_cpp|llama\.cpp|GGUF|MNN|onnxruntime|tflite" --include=*.kt app/src/main ai/src/main` 无命中）；
  端侧非零的部分只有 **ASR**：`app/src/main/java/heizige/kk/khatkit/app/core/data/datastore/SettingsRepository.kt:805`、`:822`、`app/src/main/java/heizige/kk/khatkit/app/core/ui/hooks/ASR.kt:147`、`app/src/main/java/heizige/kk/khatkit/app/feature/settings/components/ASRProviderConfigure.kt:68`（「本地识别 (sherpa-onnx)」）。⇒「不做端侧 LLM」成立，但不可说成「端侧能力为零」。
- **O**：本地模型是正式子系统，不只推理：`core/crates/provider/local-model/src/LocalModelHub.rs:22`
  （`pub struct LocalModelHubRepoSummary`，含 `downloads: u64`）、同目录
  `LocalModelRegistry.rs` / `LocalModelManifest.rs` / `LocalModelStorage.rs`；
  Android 侧 `apps/flutter/app/android/app/src/main/kotlin/app/operit/AndroidLocalInference.kt`；
  OHOS 侧 `hosts/ohos/src/local_inference.rs`。
- **K**：按产品决策不做端侧推理（`beyond-operit-upgrade.md:47` 记录为「🚫 已裁剪」）。
- **判定**：`O` / 我方不适用。**口径**：不可用（AGPL）。**优先级**：无（产品决策，除非用户改主意）。

\1- **t27 本地复核**：运行时存在（`app/src/main/java/heizige/kk/khatkit/app/core/data/browser/BrowserRuntime.kt:36`）；`browser_` 前缀在 `app/src/main` 只命中 DB 表名（`core/data/browser/BrowserArchive.kt:26`、`:33`、`:40`）⇒ **未找到** `browser_*` 内置工具注册；台账 `docs/beyond-operit-implementation-status.md:2593-2604` 记「浏览器界面已撤销、运行时只由卡片桥创建」。
- **O**：内置浏览器插件 + 宿主级网页访问：`plugins/packages/buildin/browser.ts:1`（METADATA 头）；
  `hosts/android/src/web_visit.rs`；Flutter 侧 WebView 使用见
  `apps/flutter/app/lib/ui/main/OperitApp.dart`。
- **K**：浏览器运行时存在（`app/src/main/java/heizige/kk/khatkit/app/core/data/browser/BrowserRuntime.kt:36`、
  `BrowserSessionManager.kt`、`BrowserWebViewBridge.kt`），但 UI 已按用户决定撤销，且**不注册**为
  聊天内置 `browser_*` 工具——只由声明 `requires.bridges: ["browser"]` 的卡片注入
  （台账 `docs/beyond-operit-implementation-status.md:2593-2604`，标题即「A1 浏览器界面（已撤销）」）。
- **判定**：`O`（面向产品的浏览器能力）/ 我方是「卡片桥 + 无 UI」形态。**口径**：只能借鉴机制。
- **优先级**：P2（若要重开，先解决「有运行时无入口」的产品缺口，而不是照搬 Operit2 的插件形态）。

\1- **t27 本地复核**：无障碍服务 `app/src/main/java/heizige/kk/khatkit/app/feature/automation/KhatKitAccessibilityService.kt:16` + 清单 `app/src/main/AndroidManifest.xml:262`-`:270`；工具与轨迹 `app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tools/KhatKitTools.kt:209`（`device_screen` 懒加载）、`:212`（`device_act` 轨迹落盘）、`app/src/main/java/heizige/kk/khatkit/app/feature/automation/TracePlayer.kt:14`（回放）；`MediaProjection` 在我方 0 命中 ⇒ 无截屏通道。
- **O**：走**截屏 + 视觉/OCR + 内置 UI 自动化 subagent**，没有无障碍服务：
  `apps/flutter/app/android/app/src/main/kotlin/app/operit/core/tools/system/ScreenCaptureService.kt:15`
  （`class ScreenCaptureService : Service()`）、`MediaProjectionCaptureManager.kt`、
  `app/operit/util/OCRUtils.kt:26`（`object OCRUtils`）；
  插件契约 `plugins/types/ui.d.ts:132-138` 暴露 `runSubAgent(intent, maxSteps?, agentId?, targetApp?)`。
  `grep -rn "AccessibilityService"`（`*.kt/*.java/*.xml`）**0 命中** ⇒ 通道是 MediaProjection + 视觉。
- **K**：走**无障碍节点树**：`app/.../feature/automation/KhatKitAccessibilityService.kt:16`、
  清单 `app/src/main/AndroidManifest.xml:262-270`；工具 `device_screen` / `device_act`
  （`app/.../core/data/ai/tools/KhatKitTools.kt:209`），带轨迹落盘与回放
  （`KhatKitTools.kt:212`、`feature/automation/TracePlayer.kt:14`）。
- **判定**：`O-`（能力重叠但通道互补）。**我方优势**：轨迹可回放审计；**对方优势**：视觉模型定位
  （不依赖目标 App 的可访问性）。**口径**：只能借鉴机制。**优先级**：P1（截屏通道可作无障碍失败的兜底，
  但需评估 MediaProjection 前台服务与权限成本）。

### G4 长期记忆（**t27 勘误：从「对方有而我们缺」重分类为「双方都有」**）
- **O**：多记忆库 + 图谱 + 自动抽取 + 混合检索评分，全部有源码：
  `plugins/packages/buildin/character_cards/web/features/memory/library-view.ts:14`（共享记忆库/角色绑定）、
  `:22`（全局记忆设置：自动更新、向模型提供用户资料）、
  `controls-view.ts:9`（检索评分表列头：关键词/标签/**向量**/关系 + 阈值 `relevanceThreshold`）、
  `controls-view.ts:45`（候选队列：至少 5 个候选后自动提取）、
  `library-actions.ts:24-33`（创建/编辑/删除共享记忆库）。
- **K（t27 复核后，逐条本地 file:line）**：我方已有一整套记忆体系，不是「纯工具」：
  - 领域/存储：`app/src/main/java/heizige/kk/khatkit/app/core/data/db/entity/MemorySpaceEntity.kt:17`（记忆空间）、
    `db/entity/MemoryChunkEntity.kt:25`（分块）、`db/entity/MemoryEdgeEntity.kt` + `db/entity/MemoryMentionEntity.kt`（图谱边/提及）；
  - 索引与 DAO：`db/MemoryVectorIndex.kt`（向量索引）、`db/dao/MemoryGraphDAO.kt:14`（`getEdgesOfSpace`）、
    `db/dao/MemorySpaceDAO.kt`、`db/dao/MemoryChunkDAO.kt`；
  - 服务层：`core/data/repository/MemoryRepository.kt:25`、`MemoryRetrievalEngine.kt`（检索引擎）、
    `MemoryExtractor.kt:32`（自动抽取）；
  - 工具层：`core/data/ai/tools/MemoryTools.kt:27`（`buildMemorySearchTool`）、`:62`（`buildMemoryAddTool`，onAdd 带
    `sourceMessageId` + `confidence`）；UI：`feature/assistant/detail/AssistantMemoryPage.kt`。
  - 复算口径（防止数字漂移）：`grep -rl "MemorySpace\|MemoryChunk\|MemoryGraph\|MemoryVector" --include=*.kt app khatkit common`（排除 `build/`）
    命中 **23** 个文件；其中 `app/src/main` 生产文件 **16** 个（与 captain 独立核实的 16 一致），其余为测试/迁移测试。
- **判定**：**「双方都有」**（此前误判为「`O`，我方明显缺」）。真实缺口收窄为三件事，且都需要「跨角色/跨库」这个维度：
  ① **跨角色共享记忆库**（对方 `library-view.ts:14` 的「共享记忆库 + 角色绑定」；我方记忆空间挂在助理/群聊，未见跨角色共享库的 UI/服务）；
  ② **检索评分可解释可视化**（对方 `controls-view.ts:9` 的「关键词/标签/向量/关系 + 阈值 + 命中表」；我方 `MemoryRetrievalEngine.kt` 有打分但**未找到**同类可视化的本地证据）；
  ③ **用户资料（USER.md 那类）注入开关**（对方 `library-view.ts:22` 的 `userDescriptionEnabled`；我方**未找到**对位物）。
  凡写「未找到」的，均为本次 grep 未命中，不等于不存在（见 §7.1）。
- **优先级（t27 更正）**：**P2**（原判 P0 撤销）。理由：核心链路（空间/分块/图谱/向量/抽取/来源+置信度）已存在，剩余是**增量打磨**，不再是与 Operit2 的最大实质差距。

\1- **t27 本地复核**：触发器/流水线：`app/src/main/java/heizige/kk/khatkit/app/feature/automation/{TriggerSettings,TriggerRunQueue,TriggerLogRepository,TriggerTileService}.kt`、卡片引擎 `app/src/main/java/heizige/kk/khatkit/app/core/data/ai/CardAiEngine.kt:41`；卡片在独立仓 `Android/KhatKitCards`（台账 `:2604` 记 17 张 0 错误）；编排 UI 未升（台账 `:41`）。⇒「未包化」成立；原矩阵的「18 触发器」这个**数字**本次未复核，仅作既有记录引用。
- **O**：编排以**内置插件包**形式提供：`plugins/packages/buildin/workflow/{manifest.json,dist,resources}`、
  `goal_mode/`、`plan_mode/`、`daily_life/`、`thinking_guidance/`；
  其 UI 由宿主渲染 Compose DSL：`apps/flutter/app/lib/ui/features/packages/screens/compose_dsl/renderer.dart:7`
  （`buildComposeDslLayoutForTest`）与同目录 `render_models.dart`。
- **K**：触发器与流水线引擎齐备（`feature/automation/{TriggerSettings,TriggerRunQueue,TriggerLogRepository,TriggerTileService}.kt`），
  卡片流水线在独立仓 `/home/heizige/文档/Android/KhatKitCards`（台账 `:2604` 记录「统一校验共 17 张、0 错误」），
  但**可视化编排 UI 仍未升格**（台账 `:41` 明确列出「B1 工作流可视化」属未升旁支）。
- **判定**：`O-`（双方都有编排内核，对方有宿主渲染的插件 UI 与 goal/plan 模式包）。**口径**：只能借鉴机制。
- **优先级**：P1（`goal_mode` / `plan_mode` 这种「模式即包」的切分方式值得借鉴，但需要先定 DSL/渲染边界）。

### G6 多角色 / 群聊
- **O?**：**未找到**群聊/多角色对话的源码证据（关键词：`群聊`、`多角色`、`roleplay`、`agent_group`、
  `group_chat`，命中都落在 UI 自动化 subagent 或设置项）。只找到角色卡插件里的「角色组/群组预览」
  口述（`plugins/packages/buildin/character_cards/README.md:262`，README 不作证据）与共享记忆库（见 G4）。
- **K（t27 复核后，逐条本地 file:line）**：群聊是既有实现 + 有端到端验收：
  `app/src/main/java/heizige/kk/khatkit/app/core/data/model/GroupChat.kt`（领域模型）、
  `app/src/main/java/heizige/kk/khatkit/app/feature/chat/GroupTurnCoordinator.kt`（轮次协调）、
  `feature/chat/GroupChatPage.kt`（页面）、`feature/chat/GroupPerspectiveTransformer.kt:10`（视角隔离）、
  `core/data/db/dao/GroupRunDAO.kt` + `core/data/db/entity/GroupRunEntity.kt`（运行记录）；
  端到端验收 `app/src/androidTest/java/heizige/kk/khatkit/app/core/data/db/C1GroupUiE2EFixtureTest.kt`；
  台账 `docs/beyond-operit-implementation-status.md:36-41`：**C1 十例全部 `verified`**（2026-10-07，HEAD `4f4c19565`）。
  ⚠️ 同一项目内存在记录冲突：`docs/beyond-operit-open-source-references.md` 的 C1 节尾部（`:1592-1595`，2026-10-04）
  仍写「C1-01…C1-10 十例目前仍全部 `unverified`」。两者是不同日期的快照，本次不改那份文件，仅在此标注待统一。
- **判定**：**`K+` 我方领先**——前提是采用较新的台账快照（10/10 verified）；即便只按「代码存在」这一更弱的口径，
  `K+` 也成立（对方侧群聊/多角色源码 0 命中）。**优先级**：无（保持）。
\1- **t27 本地复核**：`khatkit/src/main/java/heizige/kk/khatkit/hub/HubModels.kt:16`（索引项即卡片）、`:44`（旧字段名 `plugins` 兼容别名）、`hub/BuiltinCards.kt`、`hub/LibResolver.kt`、账号 `hub/HubAccountRepository.kt:19` ⇒「Hub 只有卡片 + 依赖包」**复核仍成立**（与 t24 的结论一致，未编造订正）。
- **O**：市场安装与统计是 Core 的一等能力：`core/crates/runtime/application/src/core/application/MarketInstallSupport.rs:1`
  （注释即「Marketplace installation markers are owned by Core」）、`:7` 引入
  `operit_providers::market::MarketStatsApiService`；包形态 = 插件包（`plugins/packages/buildin/*` 全部走同一 manifest 约定）。
- **K**：Hub 以账号/卡片为主：`app/.../core/data/ai/hub/HubAccountRepository.kt:19`；G 矩阵 `:53` 记录
  「Hub 只有卡片 + 依赖包」。
- **判定**：`O-`（都能分发，对方的**统一包形态**覆盖更宽）。**口径**：只能借鉴机制。
- **优先级**：P2（先做包形态统一，再谈市场；否则又是 N 套分发）。

### G8 运行时扩展 / ToolPkg
- **O**：有 hooks、JS 运行时、权限系统与多语言 SDK：
  `core/crates/tool/services/src/tools/AIToolHook.rs`（工具 hook）、`ToolJsRuntime.rs:1`（JS 工具运行时）、
  `ToolPermissionSystem.rs`、`ToolRegistration.rs`、`PackageToolExecutor.rs`、`skill_runtime/`、`mcp_runtime/`；
  SDK 客户端四语言：`plugins/sdk/clients/{kotlin,dart,rust,typescript}`；契约 `plugins/types/*.d.ts`。
- **K（t27 复核后）**：我方扩展面比原描述宽：**JS 工具运行时**（QuickJS：`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tools/local/JavascriptTool.kt:3`-`:4` 引用 `com.dokar.quickjs`；
  可在设置里启停 `feature/explore/ExploreMarketPage.kt:146` 的「JavaScript 引擎」本地工具）+ **dex 依赖包**
  （`khatkit/src/main/java/heizige/kk/khatkit/dependency/DependencyManager.kt:55`、`DependencyArtifactHelper.kt`）+ 卡片工具注入
  （`khatkit__<card>`，台账 `:2598-2604`）。
- **判定**：**「双方都有、对方更宽」**（原判「`O`，对方强项」保留但补上我方 QuickJS 这一半）。真实缺口：**生命周期 hooks**
  （pre/post tool、pre/post stream）、**权限审计系统**、**插件自带 UI（DSL）**、**热插拔模型 Provider**——这四项在我方**未找到**对位物。
  **口径**：只能借鉴机制（尤其 hook 挂载点设计）。**优先级**：P2。
\1- **t27 本地复核**：工作区能力属实：`app/src/main/java/heizige/kk/khatkit/app/feature/workspace/WorkspaceTerminalSession.kt:114`、`feature/workspace/WorkspaceFileEditorPage.kt`、`feature/workspace/WorkspaceDetailPage.kt`、`app/src/main/AndroidManifest.xml:242`（`WorkspaceDocumentsProvider`）；**应用级**备份已有：`app/src/main/java/heizige/kk/khatkit/app/core/data/sync/BackupManager.kt`。`feature/workspace/` 目录下**未找到** template / sync / 回滚 类文件 ⇒「工作区级模板/备份/同步缺」成立，但必须同时写明「应用级备份已有」，两者不是一回事。
- **O**：工程化程度更高：`core/crates/runtime/application/src/services/WorkspaceService.rs:1`、
  `.../ui/features/chat/webview/workspace/WorkspaceTemplateAssets.rs`（模板资产）、
  `WorkspaceBackupManager.rs`（备份）、`persistence/store/src/WorkspaceFileSyncStore.rs`（文件同步）；
  终端侧 `hosts/android/src/terminal.rs:51`（`pub struct AndroidTerminalHost`）、
  `:113`（`startPtySession`），提权 `apps/flutter/app/android/app/src/main/kotlin/app/operit/core/tools/system/AndroidRootShell.kt:11`。
- **K**：有工作区沙箱与终端：`app/.../feature/workspace/WorkspaceTerminalSession.kt:114`
  （`internal class WorkspaceTerminalSessionClient`）、`app/.../core/data/ai/transformers/WorkspaceReminderTransformer.kt`；
  无模板/备份/文件同步的对位物（本次未在源码中找到）。
- **判定**：`O`（我方缺**模板 / 备份 / 同步**三件套）。**口径**：只能借鉴机制。
- **优先级**：P1（备份+模板是低风险收益，同步留到有多设备需求时）。

\1- **t27 本地复核**：Key 池 + 任务级路由均已落地：`ai/src/main/java/heizige/kk/khatkit/ai/util/KeyPool.kt:10`、`:55`（`failLast` 降级）、`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/ModelTaskRouter.kt:43`、`TaskRoutes.kt`、`GenerationLoop.kt:175`（调 `KeyPool.shared.failLast`）。
- **O**：Key 轮换是配置项：`core/crates/runtime/application/src/data/preferences/ModelConfigManager.rs:691`
  （`keyRotationMode: provider.keyRotationMode.clone()`）；provider/模型列表在 `core/crates/provider/services/src/chat/llmprovider/model_list/`。
- **K**：**双方都在**：Key 池 `ai/src/main/java/heizige/kk/khatkit/ai/util/KeyPool.kt:10`（`:55` `failLast(providerId, status, nowMillis)` 失败降级），
  任务级路由 `app/.../core/data/ai/ModelTaskRouter.kt:43` + `TaskRoutes.kt` + `GenerationLoop.kt:175` 调 `KeyPool.shared.failLast`。
  ⇒ G 矩阵 `:56` 的「仅 per-assistant 模型绑定」已过期。
- **判定**：`O-`（能力对齐，对方多「轮换模式」这一维度）。**优先级**：P2。

\1- **t27 本地复核**：TTS/ASR/VoiceMode 均有：`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/tools/local/TextToSpeechTool.kt`、`app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ai/VoiceModeRow.kt`、`app/src/main/java/heizige/kk/khatkit/app/feature/chat/VoiceSessionController.kt`、ASR 多档配置 `feature/settings/components/ASRProviderConfigure.kt:68`；`wakeword|唤醒词` 在我方 0 命中 ⇒「有 TTS/ASR/VoiceMode、无唤醒词」成立。
- **O**：**有唤醒词**：`core/crates/runtime/application/src/data/preferences/WakeWordPreferences.rs:1`
  （`pub struct WakeWordPreferences;`）；TTS 走宿主：`hosts/android/src/tts_synthesis.rs:3`
  （`TtsSynthesisHost`）、`:5`，播放侧 `hosts/android/src/tts_playback.rs`、`audio_playback.rs`；
  本地语音栈 `apps/flutter/app/android/app/src/main/kotlin/com/k2fsa/sherpa/onnx/SherpaOnnxApi.kt`。
- **K**：TTS/ASR/语音模式都有：`app/.../core/data/ai/tools/local/TextToSpeechTool.kt`、
  `app/.../core/ui/components/ai/VoiceModeRow.kt`、`app/.../feature/chat/VoiceSessionController.kt`；
  **唤醒词未找到**（`grep -i "wakeword|唤醒词"` 于 `app/src/main`、`ai/src/main` 0 命中）。
- **判定**：`O`（缺唤醒词/常驻）。**口径**：只能借鉴机制。**优先级**：P1（唤醒词依赖常驻前台服务与电量策略，
  需先定产品口径；G11「全双工打断」另算）。

### G12 入口形态
- **O**：`SYSTEM_ALERT_WINDOW`（`apps/flutter/app/android/app/src/main/AndroidManifest.xml:22`）+
  通知监听服务（`:60-65` `OperitNotificationListener`）+ 宿主级系统操作
  （`hosts/android/src/system_operation.rs`）+ 内置 `system_tools.ts` / `super_admin.ts` 包。
  ASSIST/VoiceInteraction 未找到证据。
- **K（t27 复核后，逐条本地 file:line）**：磁贴 + 浮窗 + 前台服务齐备：`app/src/main/AndroidManifest.xml:170`、`:180`、`:190`
  （三个 `TriggerTileService`）、`app/src/main/java/heizige/kk/khatkit/app/feature/automation/TriggerTileService.kt:18`、
  `feature/automation/AutomationOverlayService.kt`、`feature/automation/AutomationInAppHost.kt`；
  台账记录了软件内看板与 Khromia toast 的规格对齐（本批 uiredesign #5）。
  ASSIST / Widget / VoiceInteraction **未找到**（`AppWidgetProvider`、`VoiceInteractionService`、`android.intent.action.ASSIST` 在
  `app/src/main` 下 0 命中）。
- **判定（t27 更正）**：**「双方都有」**，不是「我方已超越」——我方在「磁贴 + 浮窗 + 软件内看板」这条线上更成体系，
  对方在「全局悬浮窗（`SYSTEM_ALERT_WINDOW`）+ 通知监听（`AndroidManifest.xml:60-65`）+ 系统操作」上更宽；
  ASSIST / Widget 双方都**未找到**。**优先级**：P2（若要补，优先通知监听而非 ASSIST）。
\1- **t27 本地复核**：`app/src/main/java/heizige/kk/khatkit/app/core/data/model/Avatar.kt:7`（`sealed class Avatar`）、`:10`（`data object Dummy`）、`:14`（`Emoji`）、`:18`（`Image`）⇒「仅静态 Avatar（emoji/图片）」成立；`live2d|桌面宠物|desktop pet` 全仓 0 命中 ⇒「双方都没有」成立。
- **O?**：**未找到**（`grep -i "live2d|桌面宠物|desktop pet"` 全仓 0 命中；`avatar` 命中的都是用户资料头像）。
- **K**：同样只有静态头像（emoji/图片），无 Live2D/宠物。
- **判定**：双方都没有 ⇒ **本项不是与 Operit2 的差距**（仍可作为对 Operit v1 的超越项）。
  **优先级**：P3。

\1- **t27 本地复核**：导入 + 世界书：`app/src/main/java/heizige/kk/khatkit/app/feature/assistant/detail/AssistantImporter.kt:45`、`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/lorebook/LorebookEngine.kt:17`、`app/src/main/java/heizige/kk/khatkit/app/core/data/ai/transformers/PromptInjectionTransformer.kt`、`feature/assistant/detail/AssistantMemoryPage.kt`；台账 `:41` 记「A5 酒馆本体『打开并回导』闭环」仍未升。
- **O**：角色卡是**内置插件包**（含自研 UI 与记忆绑定）：
  `plugins/packages/buildin/character_cards/{manifest.json,package.json,src,dist,resources,docs}`；
  世界书/角色卡相关测试 `tools/tests/worldbook_plugin_boundary.test.mjs`、
  `tools/tests/character_cards_cli_smoke.py`。
- **K**：导入 + 世界书引擎齐备：`app/.../feature/assistant/detail/AssistantImporter.kt:45`、
  `app/.../core/data/ai/lorebook/LorebookEngine.kt:17`（`activateLorebookEntries`）、
  `core/data/ai/transformers/PromptInjectionTransformer.kt`；台账 `:41` 列出「A5 酒馆本体『打开并回导』闭环」仍未升。
- **判定**：`O-`（都能吃角色卡；对方把角色卡升级成「带 UI 与记忆库的插件包」，我方是「导入 + 世界书」）。
  **口径**：只能借鉴机制。**优先级**：P1（记忆库落地后，「角色卡绑定记忆/工具」自然接上，即 G14 的超越叙事）。

---

## 4. G 矩阵未覆盖的新轴：多设备 Space / CoreNode / Binding

矩阵是做给 Operit v1 的，因此完全没有这条轴，而它是 Operit2 的主线：

- `core/crates/node/runtime/src/SpaceRuntime.rs:1`（`SpaceRuntime`，引用 `operit_link::{CoreCallRequest, CoreEvent, …}`）
- `core/crates/node/runtime/src/{SpacePersistenceSyncService.rs, SpaceDoctor.rs, NodeSpaceService.rs}`
- `core/crates/persistence/store/src/CoreSpaceStore.rs:1`、`CoreNodeBindingStore.rs`（Space 与「哪个节点继续任务」的 Binding 持久化）

**我方对位物**：**未找到**（t27 降级：此前写「无」属未穷尽证明）。已复核 `app/`、`khatkit/`、`common/` 下未见多设备节点/空间/任务接管实现；现有网络面只有 `:web` 模块的服务端 API 与 Hub 账号（`khatkit/src/main/java/heizige/kk/khatkit/hub/HubAccountRepository.kt:19`）。
**判定**：`O`（结构性缺失）。**口径**：只能借鉴机制（概念层：节点平等、Space 不自动继承他机权限）。
**优先级**：P2——它改变产品定位（从「本机 Agent」到「设备空间」），不建议在没有明确产品意愿时开工；
但值得在文档层面承认这条轴存在，避免继续用「Operit = 一个 Android Agent app」的旧框架讨论。

---

## 5. 「Operit2 有而我们缺」清单（建议优先级）

| 优先级 | 能力 | 依据（Operit2 证据） | 我方现状（证据） | 口径 |
|---|---|---|---|---|
| **P0** | **（t27 重选）无强制 P0** —— 原 P0「记忆体系」已勘误为双方都有（§0.5） | 原依据作废 | 记忆链路已具备（`db/entity/MemorySpaceEntity.kt:17`、`db/MemoryVectorIndex.kt`、`core/data/repository/MemoryRepository.kt:25`） | — |
| **P0′（建议先做）** | 工作区**模板 + 备份**（G9 的一半） | `core/crates/runtime/application/src/services/WorkspaceService.rs:1`、`.../webview/workspace/WorkspaceTemplateAssets.rs`、`WorkspaceBackupManager.rs` | `feature/workspace/WorkspaceTerminalSession.kt:114`、`AndroidManifest.xml:242`；应用级备份另有 `core/data/sync/BackupManager.kt` | 只能借鉴机制 |
| **P1** | 语音唤醒词 / 后台常驻（G11） | `core/crates/runtime/application/src/data/preferences/WakeWordPreferences.rs:1` | 我方 0 命中唤醒词；`core/ui/components/ai/VoiceModeRow.kt`、`feature/chat/VoiceSessionController.kt` | 只能借鉴机制 |
| **P1** | 工作区**文件同步 / 改动回滚**（G9 另一半） | `core/crates/persistence/store/src/WorkspaceFileSyncStore.rs` | `feature/workspace/` 下**未找到** sync / 回滚实现 | 只能借鉴机制 |
| **P1** | 「模式即包」（goal / plan / thinking） | `plugins/packages/buildin/{goal_mode,plan_mode,thinking_guidance}` | `feature/automation/TriggerRunQueue.kt`、`core/data/ai/CardAiEngine.kt:41`（未包化） | 只能借鉴机制 |
| **P2** | 跨角色**共享记忆库** + 检索评分可视化 + 用户资料注入（G4 收窄后的真实缺口） | `character_cards/web/features/memory/library-view.ts:14,22`、`controls-view.ts:9,45` | `MemoryRepository.kt:25`、`MemoryRetrievalEngine.kt`（有打分，**未找到**同类可视化） | 只能借鉴机制 |
| **P2** | 工具 hook / 权限审计 / 插件 UI（DSL） | `core/crates/tool/services/src/tools/AIToolHook.rs`、`ToolPermissionSystem.rs` | 我方已有 QuickJS（`tools/local/JavascriptTool.kt:3`）与 dex 依赖包（`khatkit/.../dependency/DependencyManager.kt:55`），上述四项**未找到** | 只能借鉴机制 |
| **P2** | 市场统一包形态 | `core/crates/runtime/application/src/core/application/MarketInstallSupport.rs:1,7` | `khatkit/.../hub/HubModels.kt:16`、`hub/HubAccountRepository.kt:19` | 只能借鉴机制 |
| **P2** | 多设备 Space / CoreNode / Binding | `core/crates/node/runtime/src/SpaceRuntime.rs:1`、`core/crates/persistence/store/src/CoreSpaceStore.rs:1` | **未找到**对位物（§4，已降级） | 只能借鉴机制 |
| **P3** | 端侧本地模型（我方裁剪） | `core/crates/provider/local-model/src/LocalModelHub.rs:22`、`AndroidLocalInference.kt` | 端侧 LLM 0 命中；本地 ASR `feature/settings/components/ASRProviderConfigure.kt:68` | 不可用 / 不做 |
| **P3** | 虚拟形象 | 未找到（对方也没有） | `core/data/model/Avatar.kt:7/:10/:14/:18`（仅静态） | — |
| — | ASSIST / Widget / 桌面宠物 | 未找到（ASSIST、宠物）；找到通知监听 `AndroidManifest.xml:60-65` | ASSIST/Widget 0 命中；磁贴 `AndroidManifest.xml:170/180/190` | — |


## 6. 我们的相对优势（有源码/台账佐证，可直接写进对外叙事）

1. **群聊是一等公民且有端到端验收**：Operit2 侧群聊实现**未找到**；我方有实现与用例（`core/data/model/GroupChat.kt`、`feature/chat/{GroupTurnCoordinator,GroupChatPage,GroupPerspectiveTransformer.kt:10}`、`core/data/db/dao/GroupRunDAO.kt`、`core/data/db/entity/GroupRunEntity.kt`、`androidTest/.../db/C1GroupUiE2EFixtureTest.kt`），台账 `:36-41` 记 C1 十例 `verified`（⚠️ references 文档 `:1592-1595` 是更早的快照仍写 `unverified`，待统一）。

2. **无障碍执行链更完整**：节点树执行 + `device_act` 轨迹落盘 + `TracePlayer` 回放审计
   （`KhatKitTools.kt:212`、`TracePlayer.kt:14`），Operit2 是截屏+OCR 单通道。
3. **Key 池 + 任务级路由已落地**：`KeyPool.kt:10/:55`、`ModelTaskRouter.kt:43`、`GenerationLoop.kt:175`
   ——G 矩阵里写的「仅 per-assistant」已经不成立。
4. **入口形态成体系**：三个磁贴 + 浮窗服务 + 软件内看板，且软件内/外 toast 规格已与
   Khromia/ImageToolbox 对齐（本批 uiredesign #5 的交付）。
5. **UI 双风格（MD3/Miuix）一致性**是本项目特有工程投入：`settings.gradle.kts:80-90` 的 `includeBuild(../Kedge)` / `includeBuild(../Khromia)`（composite build），`LocalKedgeStyle` 在 `app/src/main` 下被 **52** 个文件引用；Operit2 侧是单一 Flutter UI 栈，不存在对位物。


---

## 7. 覆盖度与不确定性（诚实边界）

- **做到 file:line 级**：§1、§3 全部条目、§4 全部条目、§5 全部条目。
- **只到文件级/未逐行确认**：Operit2 的 `plugins/packages/buildin/character_cards/src/*`（角色卡/群组语义只
  通过测试里引用的 `dist/*.js` 与 README 口述确认，未逐行读 TS 源码）；我方 `speech` 模块内部结构
  （只核到工具与 UI 调用点）。
- **「未找到」而非「不存在」**：G6 群聊、G13 宠物、G12 ASSIST、我方唤醒词，均为 grep 关键词 0 命中；
  若对方用不同命名（例如把群聊做在 `apps/cli` 或某个插件包里），需要第二轮针对性拆解才能定论。
- **未做的事**：没有跑任何构建、没有对比 UI 截图、没有评估性能与二进制体积；本文件是**能力面**对照，
  不是工程可行性结论。

---

### 7.1 t27 我方侧系统性复核（2026-10-07）

- **复核范围**：本文件所有「我们缺 / 我们已超越 / 双方都没有」类判定，逐条补本地 file:line（见 §0.5 表与 §3 各节的 **t27 本地复核** bullet）。
- **被降级的强断言**：① G4「对方有而我们缺 / P0」→「双方都有 / P2」；② G12「我方已超越」→「双方都有（我方磁贴+浮窗更强）」；③ G8「我方只有 dex 依赖包」→「我方还有 QuickJS JS 运行时」；④ §4「我方无对位物」→「未找到」（未穷尽证明）；⑤ G6 的「已超越」保留但把证据从「仅台账」补到「代码 + 用例 + 台账」，并标注 references 文档旧快照冲突。
- **保留的「缺」（核实后确实缺）**：G9 工作区级模板/同步/回滚（我方有终端/DocumentsProvider/应用级备份，但无工作区级三件套）、G11 唤醒词（0 命中）。
- **计数口径（防止数字漂移）**：记忆相关文件用 `grep -rl "MemorySpace\|MemoryChunk\|MemoryGraph\|MemoryVector" --include=*.kt app khatkit common`（排除 `build/`）复算 ⇒ 合计 **23** 个文件，其中 `app/src/main` 生产文件 **16** 个；引用 `LocalKedgeStyle` 的文件用同样口径在 `app/src/main` 复算 ⇒ **52** 个。
- **仍未核实/未穷尽**：① Operit2 侧群聊（只用关键词 grep，未见其插件包内部语义）；② 我方是否真的没有「跨角色共享记忆库」（只查了服务/仓库/UI 一层，不排除藏在群聊或助理设置里）；③ 多设备轴（未做穷尽证明）；④ 原矩阵里的「18 触发器」数字。

## 8. 建议的下一步（不含排期承诺）

1. **P0 需重新选（t27 更正）**：原 P0「记忆体系」已勘误为「双方都有」（§0.5），**不再作为 P0**。建议顺序：先做**工作区模板 + 备份**（即下面第 3 条，独立、低风险、不依赖其他改动），随后是唤醒词与工作区文件同步；记忆侧只剩「跨角色共享记忆库 + 检索评分可视化 + 用户资料注入」这类**增量项**（P2）。

2. **把「模式即包」当作方法论**（P1）：我方已有 18 触发器与卡片流水线，先做**包边界**（一个模式一个包、
   声明式启用）比先做可视化 UI 更划算。
3. **工作区三件套**（P1）：模板 / 备份 / 同步按此顺序做，前两个不需要多设备。
4. **唤醒词**（P1）单独评估电量与常驻形态，别和「全双工打断」混在一个任务里。
5. **多设备 Space（P2）**：只做文档与概念对齐，等产品明确要求再拆架构。
6. **许可（2026-10-14 更正）**：本仓自身也是 **AGPL-3.0**（`LICENSE:1`、`README.md:85-87`），与 Operit2
   同许可 ⇒ 复用其代码/资源**不存在许可障碍**；本条此前写的「必须独立实现、不得复制代码/资源」**作废**
   （用户 2026-10-14 裁定）。唯一保留的硬义务是**署名与标记改动**（AGPL §5）：凡原样复用或改写其代码/资源，
   须逐处注明来源仓库 + SHA `9ae1e558a66f96d81ff95a499bd39bda7da6c122`，并标明是原样复用还是改写；
   引用其**设计思路**时同样按本项目既有习惯注明来源与 SHA（像本文件这样）。
   实务提示：O 侧实现是 Rust/Flutter，落到本仓多数只能改写，故「设计参照 + 自行实现」通常比硬翻译更快。
