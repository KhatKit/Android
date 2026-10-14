# 唤醒词（Wake Word）设计与选型（t32，需求/设计文档）

> **任务边界**：本文件是**唯一**交付物。不引入依赖、不改 Kotlin/资源/gradle/manifest、不 commit、**不跑 gradle**。
> **取证口径**：沿用 `docs/beyond-operit2-source-gap-analysis.md` 的纪律——每条断言给 `file:line`，可用文中的命令独立复核；
> 估算值一律标注「**估算**」并给出依据，不写成实测。
> **许可口径（已按 captain 修订）**：**本仓自身就是 AGPL-3.0**（`LICENSE:1` = `GNU AFFERO GENERAL PUBLIC LICENSE Version 3`；
> `README.md:87` 标注 AGPL-3.0）⇒ **AGPL / GPL / Apache-2.0 / MIT 的引擎一律可接受**（同家族或宽松许可，保留声明即可）。
> 真正要**单列并逐条评估**的是**专有或条款受限的 SDK**：商业许可、按量计费、需要账号/AccessKey 联网校验、或隐私条款要求上传音频流的方案。
> **署名义务（AGPL §5 + gap 分析 §8 第 6 条）**：若**直接复用或改写** Operit2 的代码，必须逐处注明「来源仓库 + SHA
> `9ae1e558a66f96d81ff95a499bd39bda7da6c122` + 是否改写」；O 侧技术栈是 Rust + Flutter，而本仓是 Kotlin/Compose，
> 因此本次实际只能是**机制参照**（其 `WakeWordPreferences.rs` 只作「唤醒词做成设置项 + 常驻服务」这一思路的先例，不搬运代码）。
> 上游来源登记在 `docs/beyond-operit-open-source-references.md` 的 `## A6` 一节。

---

## 0. 结论与建议（先看这一页）

1. **推荐第一步做「B 档：只在充电 / 亮屏 / 前台时监听」**，而不是一上来做「A 档常驻监听」：
   代价最低、可随时关掉、不产生「永久麦克风指示 + 第二条常驻通知」，且能满足「手机放桌上、充着电、说一句就进语音会话」这个主场景。
2. **引擎不用新找**：本仓已经在 `speech` 模块里带着 **sherpa-onnx 1.13.8**，而且那个 jar **本身就有 `KeywordSpotter` 类**
   （证据：`unzip -l speech/libs/sherpa-onnx-arm64-nolib-1.13.8.jar` 列表里有 `com/k2fsa/sherpa/onnx/KeywordSpotter.class` /
   `KeywordSpotterConfig.class` / `KeywordSpotterResult.class`）。⇒ 唤醒词可以**零新增第三方依赖**实现，许可为上游 Apache-2.0，
   离线、支持自定义中文关键词、模型走本仓已有的 `filesDir/sherpa-models/<id>` 下载管线（`speech/.../sherpa/SherpaModels.kt:82`、`:139`）。
3. **接线点已经存在**：语音会话的启动入口是 `app/.../feature/chat/VoiceMode.kt:44` 的 `rememberVoiceModeStarter(vm, settings): () -> Unit`，
   它内部调 `VoiceSessionController.start(createAsr, speak, stopSpeaking)`（`feature/chat/VoiceSessionController.kt:54`）。
   但它是 **Composable 作用域**的函数（依赖 `LocalASRState` / `LocalTTSState` / 运行时权限 / lifecycle，见 `VoiceMode.kt:45-66`）
   ⇒ **前台唤醒可以直接复用**；后台唤醒必须先拉起 Activity/通知（需要新增 intent 约定，属第 3 步）。
4. **不做**：全双工打断（gap 分析 §8 第 4 条明确要求与唤醒词分开）、云端唤醒词服务（隐私 + 费用，且违背离线初衷）。
5. **第一步能独立验证到什么程度**：第 1 步「无 UI、无服务、无 manifest 改动」——在 `speech` 模块加一个 KWS 封装 + JVM 单测，
   用一段固定音频样本断言「命中/不命中」，**可以独立编译并跑 `:speech:testDebugUnitTest` 验证**，不需要用户先拍板常驻与否。

---

## 1. 现状盘点（逐条 file:line）

### 1.1 语音会话与会话流

| 能力 | 位置 | 说明 |
|---|---|---|
| 语音模式启动入口 | `app/src/main/java/heizige/kk/khatkit/app/feature/chat/VoiceMode.kt:44` | `rememberVoiceModeStarter(vm, settings): () -> Unit`；内部：取 `LocalASRState`(`:47`)/`LocalTTSState`(`:48`)/`LocalToaster`(`:49`)、`rememberPermissionState(PermissionRecordAudio)`(`:50`)、`vm.voiceSession`(`:52`)、`settings.getSelectedASRProvider()`(`:54`)、lifecycle 观察(`:58` 起)、`val start: () -> Unit`(`:68`)、权限/模型前置判定 `val blocked = when {…}`(`:69-83`)，最后 `voice.start(createAsr = { createVoiceAsr(...) }, speak = …, stopSpeaking = …)`(`:84` 起，`createAsr` 在 `:85`) |
| ASR 控制器工厂 | `app/.../feature/chat/VoiceMode.kt:123` | `private fun createVoiceAsr(context, client, provider: ASRProviderSetting): ASRController` |
| 会话控制器 | `app/.../feature/chat/VoiceSessionController.kt:39` | `class VoiceSessionController(scope, getString, enqueueMessage)` |
| 状态模型 | 同文件 `:29-36` | `data class VoiceSessionState(phase, transcript, error, pendingReplies)`，`isActive`（`:35`） |
| 会话状态暴露 | 同文件 `:44-45` | `private val mutableState = MutableStateFlow(VoiceSessionState())` / `val state = mutableState.asStateFlow()` |
| 启动/停止 | 同文件 `:54` / `:186` | `fun start(createAsr: () -> ASRController, speak: (suspend (String) -> Unit)?, stopSpeaking: () -> Unit)`；`fun stop()`；`:38` 注释明确「capture 与 generation 独立运行，只有 TTS 独占麦克风暂停」 |
| 语音模式 UI 行 | `app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ai/VoiceModeRow.kt:28` | `internal fun VoiceModeRow(...)`；重试/结束按钮走 `KedgeTextButton`（`:60-61`）⇒ **双风格感知**（Kedge 组件在 MD3/Miuix 下自动分流） |
| ASR 状态注入 | `app/.../core/ui/context/LocalASRState.kt`、`app/.../core/ui/hooks/ASR.kt` | `LocalASRState` 提供全局 ASR 状态；`hooks/ASR.kt:147` 有 `is ASRProviderSetting.SherpaLocal ->` 分支 |

### 1.2 ASR/TTS 实现与设置

| 能力 | 位置 | 说明 |
|---|---|---|
| ASR 设置模型 | `speech/src/main/java/heizige/kk/khatkit/asr/ASRProviderSetting.kt:8` | `sealed class ASRProviderSetting`；变体：`OpenAIRealtime:23`、`DashScope:49`、`Volcengine:73`、`MiMo:106`、`Step:148`、`OpenAITranscribe:185`、`GeminiTranscribe:213`、**`SherpaLocal:239`**（`modelId:243`、`modelType:245` 默认 `paraformer`） |
| 「识别热词」≠ 唤醒词 | `app/.../feature/settings/components/ASRProviderConfigure.kt:727-737` | `setting.hotwords`：**把词表喂给 ASR 提升识别率**的偏置项，不是「检测到关键词才唤醒」；两者不要混为一谈（本仓库与此最接近的东西就是它） |
| Sherpa 本地 ASR 控制器 | `speech/src/main/java/heizige/kk/khatkit/asr/providers/SherpaASRController.kt` | 本地推理实现 |
| Sherpa 模型/引擎管理 | `speech/src/main/java/heizige/kk/khatkit/asr/sherpa/SherpaModels.kt` | 预设见 `:43`/`:51`/`:58`/`:65`/`:72`（`streaming-zipformer-zh-14M`、`-en-20M`、`paraformer-zh-small`、`whisper-tiny`、`whisper-tiny.en`）；`PRESETS`/`find(id)`(`:76`)；`SherpaModelManager`：`rootDir = filesDir/sherpa-models`(`:82`)、`modelDir(id)`(`:84`)、`isDownloaded(id)`(`:86`)、`delete(id)`(`:94`)、`engineDir = filesDir/sherpa-engine`(`:100`)、`isEngineReady()`(`:104`)、`downloadEngine(onProgress)`(`:110`)、`download(...)`(`:139`)、`downloadFile(...)`(`:172`) |
| 本地库装载 | `speech/src/main/java/heizige/kk/khatkit/asr/sherpa/NativeLibLoader.kt:6`-`:19` | 注释说明该 jar 是**被补丁过的 classes.jar**；`load(name)` 先 `System.load(path)` 再回退 `System.loadLibrary(name)` |
| 云端 ASR 控制器 | `speech/.../asr/providers/` | `DashScopeASRController`、`GeminiTranscribeASRController`、`MiMoASRController`、`OpenAIRealtimeASRController`、`OpenAITranscribeASRController`、`StepASRController`、`VolcengineASRController`（+ `VolcengineASRProtocol`） |
| TTS | `app/.../core/data/ai/tools/local/TextToSpeechTool.kt`（工具面）；`speech/.../tts/controller/{TtsController.kt, AudioPlayer.kt, TextChunker.kt}` | 语音模式下 TTS 由 `voice.start(..., speak = …)` 注入 |
| ASR 设置页（**双风格两套**） | `app/.../feature/settings/SettingSpeechPage.kt` 与 `app/.../feature/settings/SettingSpeechPageMiuix.kt` | 任何新开关都要**同时**进这两个页面（本文 §5.3） |
| 设置组件风格 | `app/.../feature/settings/components/ASRProviderConfigure.kt:13/25-28` | 用的是 `KedgeTextField`/`KedgeCard`/`KedgeTextButton`/`KedgeProgressIndicator` ⇒ 双风格自动分流，新增 UI 应沿用它 |

### 1.3 本地推理库与版本（`gradle/libs.versions.toml` + 模块 build）

- **sherpa-onnx**：**本地文件依赖**，不在版本目录里 —— `speech/build.gradle.kts:38`
  `implementation(files("libs/sherpa-onnx-arm64-nolib-1.13.8.jar"))` ⇒ 版本号来自文件名 **1.13.8**（arm64-only，"nolib" 表示只有 classes，
  原生 `.so` 由 `SherpaModelManager.downloadEngine` 下到 `filesDir/sherpa-engine`，见 `SherpaModels.kt:100/:110`）。
- `gradle/libs.versions.toml`：`kotlin = "2.4.20"`(`:5`)、`media3 = "1.11.1"`(`:15`)、`quickjs = "1.0.15"`(`:25`)、`commonsCompress = "1.27.1"`(`:26`)、
  `hilt = "2.60.1"`(`:29`)。`speech` 模块用到 `commons-compress`(`speech/build.gradle.kts:39`，用于解 `.tar.bz2` 模型) 与 media3 三件套(`:41-43`)。
- **KWS 能力已在依赖里**（本设计的关键事实）：`unzip -l speech/libs/sherpa-onnx-arm64-nolib-1.13.8.jar` ⇒ 122 个 class，其中
  `com/k2fsa/sherpa/onnx/KeywordSpotter.class`、`KeywordSpotterConfig.class`、`KeywordSpotterResult.class`、`KeywordSpotterKt.class`
  全部存在（同批还有 `OfflineRecognizer`/`OnlineRecognizer`）。
- ⚠️ **待实施时确认**：`speech/libs/` 目录下**只有那个 jar**（无 LICENSE/README 副本）⇒ 引入/使用 KWS 时应在仓库内补一份
  上游许可引用说明（sherpa-onnx 上游为 **Apache-2.0**，与 AGPL-3.0 主仓兼容）。

### 1.4 独立复核「本机没有唤醒词」（用我自己的命令）

```bash
# 在 KhatKit 根目录
grep -rn -i 'wakeword\|wake_word\|唤醒词' --include=*.kt --include=*.xml app ai speech khatkit common | wc -l   # => 0
grep -rn -i 'wakeword\|wake_word\|唤醒词' --include=*.kt --include=*.xml --include=*.kts --include=*.toml \
     app ai speech khatkit common web workspace gradle | grep -v /build/ | wc -l                              # => 0
grep -rn -i 'wakeword\|唤醒词' --include=*.kt ../Kedge ../Khromia | wc -l                                     # => 0
```
**结论：0 命中**（唯一相近的是 `hotwords`/`hotwords_desc` 字符串，位于 `ASRProviderConfigure.kt:727-738` 与各语言 `strings.xml`，
语义是 ASR **识别偏置词表**，不是唤醒词）。⇒ 本设计确为**从零起步**。

---

## 2. 「要不要常驻监听」——三个选项与推荐

> 三个选项都能做到「唤醒后进入现有语音会话」，差别在**代价**与**失败模式**。电量列全部为**估算**。

| | **A 始终监听**（常驻 FGS） | **B 半常驻**（充电 / 亮屏 / 前台时监听）**← 推荐第一步** | **C 伪唤醒**（无声监听，靠磁贴/快捷键/手势触发） |
|---|---|---|---|
| 实现复杂度 | 中（常驻服务 + 通知 + 电池优化引导 + 后台被杀恢复） | 低-中（监听逻辑挂在已有生命周期上，可随时关） | 最低（复用现有入口：磁贴/快捷方式/无障碍悬浮球） |
| 电量量级（**估算**） | 常开麦克风 + 每帧 KWS 推理：**估算** 1–3% 单核 CPU 占用、夜间 8h 待机额外 **3–8%/8h**（依据：16kHz 单声道采样、KWS zipformer int8 模型 3–10MB 量级、每 10ms 一帧的 ONNX 前向；**无实测**） | 只在充电/亮屏/前台时跑：日常耗电**接近 0**（充电场景电量不是约束；亮屏时本就在耗电） | 0（不采音频） |
| 隐私与用户可见性 | 最差：Android 12+ 右上角**绿点常亮**、状态栏常驻通知「正在监听」；需向用户明示 | 中：仅充电/亮屏时出现绿点 + 通知；可在设置里一键关闭 | 最好：不申请麦克风常驻；唤醒即「用户主动触发」 |
| 系统限制与失败模式 | Android 14+ `FOREGROUND_SERVICE_MICROPHONE` 必须声明；**麦克风类型 FGS 不允许从后台启动**（含 `BOOT_COMPLETED`、`RECEIVE_BOOT_COMPLETED:32` 那条路走不通）⇒ 只能由用户点通知/进 App/磁贴启动；被 OEM ROM 杀后台后不会自恢复；忽略电池优化需引导（`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS:25` 已声明） | 同样受 FGS 启动限制，但因为监听只在前台/亮屏触发，不依赖后台自启 ⇒ 失败模式最少 | 无 FGS 限制（磁贴点击属前台交互） |
| 唤醒后能否可靠进入现有流程 | 需先「拉起 Activity/通知」再进 `rememberVoiceModeStarter`（Composable 作用域，见 §4.1）⇒ 中等复杂度 | 可靠：App 在前台 ⇒ 直接复用 `VoiceMode.kt:44` 的 lambda | 可靠：本来就是用户点击流程，走现有 `VoiceModeRow.kt:28` 入口 |
| 建议 | 等 B 稳定 + 用户明确要「锁屏也喊得醒」后再做 | **第一步就做这个** | 与 B 并行、零成本增量（见 §7 步 4） |

**推荐路径**：`C（立刻可得） → B（第一步正式方案） → A（用户拍板后）`。
理由：唤醒词的真实价值是「免手点」，B 已经覆盖「手机在桌上充电/亮屏」这一最常见场景；A 的收益（锁屏也响应）远小于它带来的隐私可见性、
电量与 OEM 兼容风险。

---

## 3. 引擎选型

### 3.1 候选与判定

| 候选 | 许可 | APK 增量（**估算**） | 离线 | 中文 / 自定义唤醒词 | 维护活跃度 | 模型下载与体积 | 与现有依赖复用 | 判定 |
|---|---|---|---|---|---|---|---|---|
| **sherpa-onnx KWS（`KeywordSpotter`）** | **Apache-2.0**（上游 k2-fsa/sherpa-onnx） | **0**（jar 已在仓库：`speech/libs/sherpa-onnx-arm64-nolib-1.13.8.jar`，`speech/build.gradle.kts:38`） | ✅ | ✅ 自定义关键词（文本 token 形式，中文模型可选）；模型 kws-zipformer 系列 | 活跃（本仓已跟到 1.13.8） | 走现有 `SherpaModelManager`（`SherpaModels.kt:139`）；**模型体积需实施时确认**（KWS zipformer int8 通常 **3–10MB** 量级，**估算**）；无需必然新增引擎下载（`downloadEngine` 已在用） | ✅ **已有可复用件**：类已存在 + 下载/解压管线已存在（`commons-compress 1.27.1`） | **首选** |
| Vosk（vosk-api / vosk-android） | Apache-2.0 | 新 native 依赖，**估算 +15–30MB**（引擎 + 小模型） | ✅ | ✅ 有中文小模型；自定义唤醒词靠识别后匹配，误触发率高于专用 KWS | 活跃度一般 | 模型 40MB+（小中文模型） | ❌ 新依赖 | 备选（仅在 KWS 失手时评估） |
| openWakeWord | Apache-2.0 | 需把 Python 训练/推理移植到 TFLite，**自研工程量中等** | ✅ | ⚠️ 官方预置为英文；中文需自训 | 活跃（社区） | 自训模型 ~1MB 级 | ❌ 需新增 TFLite 依赖与自训流程 | 备选（愿意自训才考虑） |
| Porcupine（Picovoice） | **商业许可**（免费层有限制 + 需 AccessKey 联网校验） | 小（~1–2MB/语言） | ⚠️ 免费层与云端校验条款需看清 | ✅ 支持中文自定义词（.ppn 需在其控制台生成） | 活跃 | 模型随 SDK | ❌ 新依赖 + 账号/许可 | **默认不选**（专有条款：免费层限制 + 需 AccessKey 在线校验 —— 不是许可家族问题） |
| Snowboy | 停止维护（2019），原始许可与第三方分支混杂 | — | — | — | ☠️ 已停维护 | — | ❌ | **排除**（维护与许可都不清楚） |
| TensorFlow Lite + 自训 KWS（如 Google KWS 示例） | Apache-2.0（模型自训） | TFLite 依赖已可复用？**未确认**（本仓未见 tflite 依赖：`grep -rn "tflite" gradle app ai speech khatkit` 无命中） | ✅ | 需自训中文唤醒词（数据/训练成本高） | 高 | 模型 <1MB | ❌ 需新增 tflite 运行时 | 备选（长期） |
| 商用 SDK：讯飞 / 百度 / 火山 / 腾讯 | 商业（需账号、按量计费、离线版另议） | 各 SDK 数 MB | 视版本 | ✅ 中文强 | 活跃 | 云端或离线包 | ❌ 新依赖 + 商务 | 备选（用户要求中文强鲁棒时再评估） |
| **云端唤醒词服务** | 商业 | 0（流式上传音频） | ❌ | ✅ | — | — | — | **明确不做**（§6） |

### 3.2 许可与合规小结（选型口径，已按 captain 修订）

- **本仓许可是 AGPL-3.0**（`LICENSE:1`、`README.md:87`）⇒ **AGPL / GPL / Apache-2.0 / MIT 的引擎都可接受**：同家族（AGPL/GPL）或宽松许可（Apache/MIT）
  都能并入，只需**保留原许可与版权声明**（比如 sherpa-onnx 的 Apache-2.0 引用说明——目前 `speech/libs/` 只有 jar 没有 LICENSE 副本，见 §9 待确认项）。
- **真正需要单列评估的是「专有 / 条款受限」的 SDK**，判定维度是四条：① 是否需要付费或按量计费；② 是否要求账号 / AccessKey / 联网校验；
  ③ 是否限制分发或要求单独商务协议；④ 隐私条款是否要求把音频上传/回传。按这四条：
  - `Porcupine（Picovoice）`：**专有条款受限**（免费层有限制 + 需 AccessKey 在线校验）⇒ 默认不选，除非用户接受其条款；
  - 讯飞 / 百度 / 火山 / 腾讯等**商用 SDK**：需账号与计费、部分功能需商务开通 ⇒ 列备选，需用户拍板；
  - **云端唤醒词服务**：除费用外还涉及**麦克风常驻 + 音频上云**的隐私面 ⇒ 明确不做（§6）。
- **署名义务**：若将来复用/改写 Operit2（AGPL-3.0，SHA `9ae1e558a66f96d81ff95a499bd39bda7da6c122`）的代码，逐处注明来源、SHA 与是否改写（AGPL §5）；
  本设计只做机制参照。

---

## 4. 与现有语音栈的接线设计

### 4.1 唤醒命中 → 现有会话流程

```
[KWS 命中] → WakeWordEvent(text, score, atMillis)
    │
    ├─(App 前台/Chat 页存活) → 调用 rememberVoiceModeStarter(vm, settings) 返回的 lambda
    │        （VoiceMode.kt:44；其内部已处理：运行时权限检查(:50 的 permission / :69-83 的 blocked 判定) → provider 选择(:54) → voice.start(...)(:84 起)）
    │        → VoiceSessionController.start(createAsr, speak, stopSpeaking)（VoiceSessionController.kt:54）
    │        → 状态经 state: StateFlow<VoiceSessionState>(:44-45) 回流给 UI（VoiceModeRow.kt:28 显示）
    │
    └─(App 后台/进程被杀) → 不直接 start：发通知（或 Activity intent）拉起 Chat 页并带「auto-start voice」标记
             （这是**新增**的入口约定，属第 3 步；第 1/2 步不涉及）
```

- **状态传递**：唤醒侧**不写** `VoiceSessionState`，只调用入口 lambda；`phase/transcript/error/pendingReplies`（`:29-36`）由既有控制器维护。
- **入口函数**：`rememberVoiceModeStarter` 是 **Composable**（`VoiceMode.kt:44`，内部用了 `LocalASRState:47` / `LocalTTSState:48` /
  `rememberPermissionState:50` / `LocalLifecycleOwner:55`）⇒ **只能在有 composition 的地方调用**。第一步若想「不依赖 UI」验证，就直接用
  `speech` 模块的 KWS 封装 + 单测（§7 步 1），不要过早碰会话流程。
- **与「全双工打断」解耦**：唤醒只做「一次性触发」；`VoiceSessionController.kt:38` 注释已明确 capture 与 generation 独立、只有 TTS 独占麦克风暂停
  ⇒ 唤醒监听必须在**触发前**结束自己的麦克风占用（或与 ASR 共享同一个 `AudioRecord` 的既有设计），**不要**把唤醒监听塞进 `runSession` 内部，
  否则就会变成事实上的「常驻 + 打断」耦合。

### 4.2 会不会出现「第二个常驻服务」？（冲突分析）

**现状**（`app/src/main/AndroidManifest.xml`）：本仓已经有一组前台服务：
`ChatGenerationForegroundService`（`foregroundServiceType="dataSync"`，`:148-151`）、`WebServerService`（`specialUse`，`:152-157` 区段）、
`TriggerService`（`specialUse`，`:160-163`）、`AutomationOverlayService`（`specialUse`，`:199-206` 区域）、
三个磁贴 `TriggerTileService/2/3`（`:169-197` 区段，含 `QS_TILE` intent-filter）、`KhatKitNotificationListenerService`（`:207` 起）。

| 方案 | 是否新增常驻服务 | 冲突与建议 |
|---|---|---|
| **B 档（推荐）** | **不新增**：把「亮屏/充电/前台才监听」挂在 `TriggerService`（已有 `specialUse` 常驻 + 通知）的生命周期上，监听期间临时持有麦克风 | ✅ 只有一条常驻通知；需注意 `specialUse` 与 `microphone` 的类型组合：Android 允许 service 声明多类型（`android:foregroundServiceType="specialUse|microphone"`），但**必须同时持有对应权限**（`FOREGROUND_SERVICE_SPECIAL_USE` 已有 `:31`，`FOREGROUND_SERVICE_MICROPHONE` 需新增），且麦克风类型仍受「不允许后台启动」限制 |
| **A 档（常驻）** | 新增独立 `WakeWordService`（`foregroundServiceType="microphone"`） | 会出现**第二条常驻通知**（`TriggerService` 一条 + 唤醒一条）与**常绿麦克风指示**；建议：先在设置页给用户看两条通知的说明，或后续把两者合并成单服务多类型 |
| **C 档（伪唤醒）** | 不新增 | 复用三磁贴（`:169-197`）即可；零权限成本 |

---

## 5. 权限与合规清单

### 5.1 manifest：现状与拟改动

| 项 | 现状（`app/src/main/AndroidManifest.xml`） | 拟改动 |
|---|---|---|
| 麦克风 | `RECORD_AUDIO` 已有（`:10`） | 无需新增；运行时仍需用户授权（`VoiceMode.kt:50` 的 `rememberPermissionState(PermissionRecordAudio)`） |
| 前台服务基础 | `FOREGROUND_SERVICE`（`:29`）、`FOREGROUND_SERVICE_DATA_SYNC`（`:30`）、`FOREGROUND_SERVICE_SPECIAL_USE`（`:31`） | **新增** `android.permission.FOREGROUND_SERVICE_MICROPHONE`（Android 14+ 必需） |
| 服务声明 | `TriggerService`（`:160-163`，`specialUse`） | A 档：新增 `<service android:name=".…WakeWordService" android:foregroundServiceType="microphone" android:exported="false" />`；B 档：把 `TriggerService` 改成 `specialUse\|microphone`（`android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"` 那行 `:164-165` 保留） |
| 唤醒唤醒（开机） | `RECEIVE_BOOT_COMPLETED`（`:32`） | **不要**指望它启动麦克风 FGS（平台禁止）；B 档不依赖它 |
| 电池优化 | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 已声明（`:25`） | A 档需要新增**应用内引导**（属设置页文案，见 §5.3） |
| 通知 | `POST_NOTIFICATIONS`（`:15`） | 常驻通知需要用户授权；MiuiX/MD3 两套文案都要有 |
| 唤醒锁 | `WAKE_LOCK`（`:11`） | 音频采集期间按需持有（可复用） |

### 5.2 Android 版本与厂商限制（逐条列失败模式）

1. **Android 14+（API 34）**：`microphone` 类型 FGS 必须声明 `FOREGROUND_SERVICE_MICROPHONE`；**不允许从后台启动**（含 `BOOT_COMPLETED`、
   `AlarmManager` 广播）；启动时若 `RECORD_AUDIO` 未授权会直接抛 `SecurityException`。⇒ 唤醒服务只能由**用户交互**（点磁贴/进 App/点通知）启动。
2. **电池优化/Doze**：未加白名单时，长时间后台的 FGS 仍可能被限制；`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 已有声明，但要**引导用户**去系统设置
   手动加白名单（各厂商路径不同）。
3. **厂商 ROM（小米/华为/OPPO/vivo 等）**：自启动/后台限制策略不同，浅色/深色提示文案要说明「可能需要在系统设置里允许自启动与后台运行」。
4. **麦克风隐私指示**：Android 12+ 只要有采集就有右上角绿点 ⇒ 常驻监听对用户是**持续可见**的，产品上必须解释清楚（文案位置见 §5.3）。
5. **多服务共存**：Android 允许同时存在多个 FGS，但**每个 FGS 各自一条通知**；且同一时刻只能有一个进程持有麦克风（另一处占用会导致 `AudioRecord` 初始化失败）
   ⇒ 唤醒监听、ASR、TTS 之间必须有明确的「谁在占用麦克风」的编排（现有代码在 `VoiceSessionController.kt:38` 注释里已把 TTS 作为独占暂停点）。

### 5.3 应用内开关与文案（双风格影响面）

| 位置 | 说明 |
|---|---|
| `app/.../feature/settings/SettingSpeechPage.kt` **与** `SettingSpeechPageMiuix.kt` | 「唤醒词」开关/唤醒词文本/是否允许下载模型/是否常驻，**两套风格页面都要加**（这是本仓既有的成对页面模式） |
| `app/.../feature/settings/components/ASRProviderConfigure.kt` | 新设置项应沿用 `KedgeTextField`/`KedgeCard`/`KedgeTextButton`（`:13/:25-28`），保证 MD3 与 Miuix 自动分流 |
| `app/.../core/ui/components/ai/VoiceModeRow.kt:28` | 语音状态显示可加「由唤醒词启动」标识（可选） |
| 通知文案 / 常驻说明 | 需要中英（以及现有其它语言）字符串；走 `res/values*/strings.xml` |

### 5.4 对现有 automation / 磁贴的影响

- `TriggerTileService/2/3`（manifest `:169-197`）：**不受影响**；反而可以把「唤醒一次」做成磁贴即时触发（C 档）。
- `AutomationOverlayService`（`specialUse`，`:199-206` 区域）：**不受影响**（不申请麦克风）；但若将来让 automation 用「语音触发」，要与唤醒服务协调麦克风占用。
- `TriggerService`：B 档会**共享**它的常驻能力 ⇒ 需要评估它的通知文案是否要加「语音待命」状态；这是 B 档唯一的既有代码改动面（仍属新增，不在本任务）。

---

## 6. 明确不做项

1. **全双工打断（barge-in）**：`docs/beyond-operit2-source-gap-analysis.md` §8 第 4 条明确要求与唤醒词分开做；本文只在 §4.1 说明**解耦原则**，不设计打断。
2. **云端唤醒词服务**：不做。理由（二选一即可成立）：① **隐私**——常驻上传音频流，与「本地优先」的项目取向冲突，且在国内合规语境下麦克风常驻+上云的解释成本极高；
   ② **费用**——按量计费（长连接/流式识别）会引入持续性成本，而本地 KWS 的边际成本是 0。
3. **唤醒词自训练 UI**（第一版不做）：自定义唤醒词先只支持「预设候选 + 文本配置」，不做用户上传训练。
4. **不在本任务引入任何依赖**（t32 只出文档）。

---

## 7. 实施顺序（每步都能独立验证）

> 原则：**第 1 步不需要用户拍板**；每步结束都能编译、能自证，且没有「必须一次做完」的耦合。

### 步 1（S 量级）：KWS 能力验证（无 UI / 无服务 / 无 manifest 改动）
- **改动文件**：`speech/src/main/java/heizige/kk/khatkit/asr/kws/`（新增：KWS 封装 + 关键词配置，包装 `com.k2fsa.sherpa.onnx.KeywordSpotter`）；
  `speech/src/test/java/.../KwsSmokeTest.kt`（新增）；可选把 KWS 模型预设加进 `SherpaModels.kt` 的 `PRESETS`。**不动** `app/`，**不动** manifest。
- **自证方式**：`./gradlew :speech:testDebugUnitTest --rerun`（JVM 单测，用一段短音频样本断言命中/不命中；模型可在测试里用固定小文件或跳过下载只测配置解析）。
- **前置条件**：无需真机、无需用户拍板。
- **量级**：S（1 个封装 + 1 个测试）。

### 步 2（M 量级）：B 档「充电/亮屏/前台监听」+ 设置开关
- **改动文件**：`speech` 内监听循环（音频采集 + KWS 判分 + 冷却时间）；`app/.../feature/chat/VoiceMode.kt`（暴露一个非 Composable 触发桥给监听侧，或让监听回调调用既有 starter）；
  `SettingSpeechPage.kt` + `SettingSpeechPageMiuix.kt`（开关/唤醒词/模型下载入口）；`trigger`/服务侧：把监听挂在现有 `TriggerService` 生命周期上（B 档，不新增服务）；
  manifest：仅新增 `FOREGROUND_SERVICE_MICROPHONE`（若走 `specialUse|microphone`）。
- **自证方式**：真机步骤——① 授予麦克风权限；② 打开开关；③ 插电/亮屏说唤醒词 ⇒ 进入语音会话（用 `VoiceModeRow` 的状态显示判断）；
  ④ 关开关后确认不再监听（绿点消失）。**前置条件**：真机 + 已下载 KWS 模型（走 `SherpaModelManager.download`）。
- **量级**：M。

### 步 3（M 量级，用户拍板后）：A 档常驻 + 电池/隐私引导
- **改动文件**：新增 `WakeWordService`（`microphone` FGS）+ manifest 声明 + 通知渠道 + 设置页的电池优化引导/隐私文案。
- **自证方式**：真机 ① 点磁贴/进 App 启动服务 → 锁屏喊唤醒词；② 24h 电量对比（`dumpsys batterystats` / Battery Historian，**需实测**才能替掉本文的估算）；
  ③ 后台被 OEM 杀死后的行为记录（是否只依赖用户重新启动）。
- **前置条件**：用户同意常驻与绿点常亮；真机 + 至少一台非 Pixel 厂商 ROM 更佳。
- **量级**：M。

### 步 4（S 量级，可选）：磁贴/快捷方式伪唤醒
- **改动文件**：`TriggerTileService`（`:169-197` 已存在的三个磁贴中给一个「语音待命」语义）或 `ShortcutHandlerActivity`（`AndroidManifest.xml:124`）。
- **自证方式**：真机点磁贴 → 直接进语音会话（无需唤醒词）。
- **量级**：S。

---

## 8. 必须由用户拍板的问题

1. **是否常驻**：只做 B（充电/亮屏）？还是接受 A（常驻监听 + 绿点常亮 + 第二条通知）？
2. **唤醒词内容**：`嘿 KhatKit` / `小哈` / 其它？中文还是中英双语？要不要多唤醒词？
3. **是否允许下载 KWS 模型**（体积量级 3–10MB，估算；走已有 `sherpa-models` 目录）？是否允许在移动网络下下载？
4. **是否接受常驻麦克风指示**（Android 12+ 绿点）与第二条通知？
5. **是否引导用户加电池优化白名单**（会改变系统设置，需明确同意）。
6. **后台被系统杀死后的期望**：静默失效，还是发一条「语音待命已停止」的通知提示重开？
7. （若做 A 档）是否需要「锁屏也能唤醒」——这直接决定是否值得承担 A 档的全部代价。

---

## 9. 证据清单（reviewer 可逐条复核）

- **本文所有 `file:line`** 均来自当前工作区；关键命令：
  - `grep -rn -i 'wakeword\|wake_word\|唤醒词' --include=*.kt --include=*.xml app ai speech khatkit common | wc -l` ⇒ `0`
  - `unzip -l speech/libs/sherpa-onnx-arm64-nolib-1.13.8.jar | grep -i 'keyword\|spotter'` ⇒ 4 个 `KeywordSpotter*` class
  - `grep -n 'sherpa' gradle/libs.versions.toml` ⇒ 无（版本来自 `speech/build.gradle.kts:38` 的文件名 `1.13.8`）
  - `grep -n 'media3\|quickjs\|commonsCompress\|hilt\|kotlin =' gradle/libs.versions.toml` ⇒ `1.11.1` / `1.0.15` / `1.27.1` / `2.60.1` / `2.4.20`
- **本文标注为「估算」的量**：电量（1–3% 单核、3–8%/8h）、各候选 APK 增量、KWS 模型体积（3–10MB）。**未做任何实测**；步 3 给出实测方法。
- **本文标注为「待实施时确认」**：① 具体 KWS 中文模型的文件名与体积；② `speech/libs/` 缺 LICENSE 副本（应补上游 Apache-2.0 引用说明）；
  ③ `TriggerService` 是否适合叠加 `microphone` 类型（需真机验证 FGS 类型组合行为）。
- **许可口径复核**：本仓 AGPL-3.0（`LICENSE:1`、`README.md:87`）⇒ AGPL/GPL/Apache/MIT 引擎均可接受（保留声明）；sherpa-onnx / Vosk / openWakeWord / TFLite 为 Apache-2.0（可复用/可评估）；
  **专有或条款受限**的是 Porcupine（免费层 + AccessKey 在线校验）与各商用 SDK（费用/账号/商务）；云端唤醒词服务涉及音频上云 ⇒ 不做。
- **署名义务**：凡直接复用或改写 Operit2 代码（AGPL-3.0，SHA `9ae1e558a66f96d81ff95a499bd39bda7da6c122`）都要逐处注明来源 + SHA + 是否改写（AGPL §5）；本次为机制参照，无代码搬运。
- **本文未做**：未改任何 Kotlin/资源/gradle/manifest，未 commit，未跑 gradle。
