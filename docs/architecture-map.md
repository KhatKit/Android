# KhatKit 架构记账表

> 这份文档记录 **KhatKit 自己** 的包结构、命名、上游继承面与 Nav3 核对结论。
> 体例参照 `../ARCHITECTURE.md` §4 的「现状 → 目标」对照表。
>
> ⚠️ **`../ARCHITECTURE.md` 不是给 KhatKit 写的。** 那 217 行里 §1 的目录树写的是
> `heizige/kk/kodehead/`，§6 的版本表逐行等于 Cynic 的 `libs.versions.toml`。本文只把它
> 当「规范来源」，**所有现状数字一律实测**，来源命令写在每节开头。
>
> 复算基准：`main` @ `0d2f6838a`（`git rev-parse HEAD`）。

---

## 0. 一句话结论

**KhatKit 已经是 feature-first，不需要包结构迁移。** `ARCHITECTURE.md` §1 要求的
`core/{network,data,auth,files,ui,util}` 里 KhatKit 有 4 个原样存在，多出 `di`/`service`
两个，缺的 `auth`/`files` 只是**落点不同**而不是没有（见 §1.3）。技术栈也比 §6 新，
把栈「对齐」到 §6 是倒退。真正欠账的只有三件：本文（记账）、`[bundles]`、`AGENTS.md`
的本地约定。

---

## 1. 包结构现状

**来源命令**

```bash
find app/src/main/java/heizige/kk/khatkit/app/core/*/ -name '*.kt' | wc -l   # 按子包分组
find app/src/main/java/heizige/kk/khatkit/app/feature/*/ -name '*.kt' | wc -l
```

### 1.1 `core/` 六个子包

| `core/` 子包 | kt 数 | `ARCHITECTURE.md` §1 是否要求 | 判定 |
|---|---:|---|---|
| `data` | 175 | ✅ 要求 | ✅ 原样存在 |
| `network` | 17 | ✅ 要求 | ✅ 原样存在 |
| `ui` | 315 | ✅ 要求 | ✅ 原样存在（内含 `icons/` 150 个 kt，满足 §3） |
| `util` | 24 | ✅ 要求 | ✅ 原样存在 |
| `di` | 4 | ❌ 未要求 | **多出来的**，合理：Hilt module 集中地 |
| `service` | 5 | ❌ 未要求 | **多出来的**，见 §1.4 的重复类问题 |
| — | **540** | | |

（`core/` 下没有游离于子包之外的 kt。）

### 1.2 `core/data/` 二级子包

| 子包 | kt | 子包 | kt | 子包 | kt |
|---|---:|---|---:|---|---:|
| `ai` | 64 | `browser` | 11 | `event` | 2 |
| `db` | 45 | `datastore` | 9 | `export` | 3 |
| `model` | 9 | `repository` | 10 | `favorite` | 2 |
| `sync` | 11 | `files` | 7 | `api` | 1 |
| `network` | 1 | | | | |

合计 175，与 §1.1 的 `data` 一致。

### 1.3 §1 要求的六个包 → KhatKit 落点对照

| §1 要求 | KhatKit 实际落点 | kt | 结论 |
|---|---|---:|---|
| `core/network` | `core/network/` | 17 | ✅ 位置一致 |
| `core/data` | `core/data/` | 175 | ✅ 位置一致 |
| `core/ui` | `core/ui/` | 315 | ✅ 位置一致 |
| `core/util` | `core/util/` | 24 | ✅ 位置一致 |
| `core/auth` | **不存在**。认证能力落在两处：独立 Gradle 模块 `:oauth`（`OAuthHttpClient.kt` / `OAuthAuthorizationLauncher.kt` / `OAuthLoopbackCallbackServer.kt` / `OAuthCallbackForegroundService.kt`），以及 `core/data/datastore/SettingsRepository.kt`（令牌与配置的存取入口） | 4 + 1 | ⚠️ **缺的**，见下 |
| `core/files` | **不在 `core/` 下**，而是 `core/data/files/` | 7 | ⚠️ **缺的**，见下 |

**`core/auth` 为什么不补**：认证已经被 `:oauth` 模块切出去了，它有自己的
`build.gradle.kts` 和两个 JVM 测试类，物理上不可能再塞进 `core/` 下的一个包里。
在 `app` 里再造一个 `core/auth/` 只放转发代码，是纯粹的空壳。

**`core/files` 为什么不提为兄弟**：`core/data/files/` 的 7 个文件全部围绕**文件的读写、
导入与路径解析**（`FilesManager` / `FileUtils` / `SkillManager` / `SkillPaths` /
`SkillFrontmatterParser` / `BuiltinSkills` / `CardMediaImporter`），是"数据的文件形态"。
`FileUtils.kt` 走 `ContentResolver` / `DocumentsContract` / `MimeTypeMap`，
`CardMediaImporter` 走图片存储，都是围绕文件而不是围绕业务实体。
为了对齐目录图把它搬到 `core/files/`，下一次上游同步要在这两个路径之间做人工三方合并
（见 §3），收益是目录树好看一格。

### 1.4 `feature/` 22 个功能包

> ⚠️ 路径实况：功能包在 **`app/src/main/java/heizige/kk/khatkit/app/feature/`**，
> 不是 Gradle 模块级的 `app/feature/`。`app/feature/` 这个目录**不存在**
> （`find app/feature -name '*.kt'` → `No such file or directory`）。`app/` 下每个
> 功能是源码包，不是独立 Gradle 模块 —— 真正的模块清单在 `settings.gradle.kts`
> 的 `include(...)` 里（`:app :khatkit :khatkit-ui :card-validator :highlight :ai
> :search :speech :common :document :web :material3 :workspace :oauth :mediapicker
> :image-toolbox-dependency`）。

| 功能包 | kt | 功能包 | kt |
|---|---:|---|---:|
| `settings` | 65 | `backup` | 7 |
| `chat` | 35 | `record` | 5 |
| `automation` | 27 | `workflow` | 5 |
| `assistant` | 14 | `share` | 3 |
| `workspace` | 10 | `debug` / `download` / `explore` | 2 each |
| `extensions` | 9 | `favorite` / `history` / `imggen` / `search` / `stats` / `translator` | 2 each |
| `log` / `onboarding` / `webview` | 1 each | | |

合计 **201**。加上 `core/` 的 540 与 `app/` 根下的 2 个（`KhatKitApp.kt`、
`RouteActivity.kt`）= **743** 个 kt，与
`find app/src/main/java -name '*.kt' | wc -l` 一致。

**规模分布的判断**：`settings`(65) 与 `chat`(35) 是唯二的大包，其余 20 个都在 27
以下。这符合 §1.1「先内聚，后提升」。**不建议**现在拆 `settings` —— 拆它要动 65 个文件
的上游对应物（`ui/` 与 `data/` 下都有 Settings 相关文件），代价见 §3。

---

## 2. 命名合规表

### 2.1 实测基线

**来源命令**

```bash
git ls-tree -r --name-only main | grep '\.kt$' | xargs -n1 basename \
  | grep -cE 'Service\.kt$'      # 其余词同理
```

| 后缀 | 全仓 kt 数 | §2 判定 |
|---|---:|---|
| `*Service.kt` | 32 | ❌ 规范要弃用（见 §2.2，且调研给的「AIDL/Binder」理由不成立） |
| `*Manager.kt` | 19 | ✅ 合规 |
| `*Util.kt` | 9 | ❌ 规范要改成 `Extensions` |
| `*Utils.kt` | 11 | ❌ 同上 |
| `*Store.kt` | 11 | ❌ 规范要改成 `Repository` |
| `*Repository.kt` | 11 | ✅ 合规 |
| `*Helper.kt` | 0 | ✅ 合规（本仓库一个都没用） |
| `*Extensions.kt` | 0 | ✅ 合规 |

**合规率 = (19 + 11 + 0 + 0) / (32+19+9+11+11+11+0) = 30 / 93 = 32.3%。**

⚠️ **与调研基线的出入**：任务书给的 `Utils=14` 我复算不出。同一条命令给的是 **11**；
若改成"文件名任意位置含 `Utils`"则是 13（多出 `DiffUtilsTest.kt` 与
`StringUtilsTest.kt` 两个测试类）。**两个口径都不是 14**，本文一律用 11。

`grep -cE 'Extension.*\.kt$'` 会命中 5 个文件，但它们是
`ExtensionContent.kt` / `ExtensionSelector.kt` / `icons/Extension.kt` /
`AssistantExtensionsPage.kt` / `ExtensionsPage.kt` —— 是**功能名**（扩展/插件市场），
不是 §2 意义上的 `Extensions` 命名，所以合规表里记 0。

### 2.2 逐类「不改」的理由（每条带路径）

#### `*Service.kt` = 32，分三类

**（a）真·Android 框架组件，8 个 —— 不改，因为改名要同步改 `AndroidManifest.xml`**

| 文件 | manifest 声明 |
|---|---|
| `app/…/core/network/WebServerService.kt` | `AndroidManifest.xml:153` |
| `app/…/feature/chat/ChatGenerationForegroundService.kt` | `AndroidManifest.xml:149` |
| `app/…/feature/automation/TriggerService.kt` | `AndroidManifest.xml:161` |
| `app/…/feature/automation/TriggerTileService.kt` | `AndroidManifest.xml:170`（`TriggerTileService2`/`3` 同文件 `:59`/`:64`，声明在 `:180`/`:190`） |
| `app/…/feature/automation/AutomationOverlayService.kt` | `AndroidManifest.xml:200` |
| `app/…/feature/automation/KhatKitNotificationListenerService.kt` | `AndroidManifest.xml:208` |
| `app/…/feature/automation/KhatKitAccessibilityService.kt` | `AndroidManifest.xml:262` |
| `app/…/core/service/ChatGenerationForegroundService.kt` | **无声明**，见 §4.1 |

这些类继承 `android.app.Service` / `TileService` / `AccessibilityService` /
`NotificationListenerService`。Android 里 `Service` 就是组件名，改类名要同时改
manifest 的 `android:name`、`foregroundServiceType`、以及 `RouteActivity.kt` 里
`PendingIntent` 的跳转目标。

**（b）`:search` 模块 20 个 —— 调研给的「AIDL/Binder 框架语义」理由不成立**

⚠️ 实测否证：

```bash
grep -rn 'android\.app\.Service|IBinder|IInterface|\.Stub|RemoteException' search/src/main/ | wc -l
# → 0
```

`search/src/main/java/heizige/kk/khatkit/search/SearchService.kt` 就是一个普通 Kotlin
接口 `interface SearchService<T : SearchServiceOptions>`，里面是
`suspend fun search(...)` / `suspend fun scrape(...)`。**整个 `:search` 模块没有一行
AIDL、没有 `IBinder`、没有 `.aidl` 文件**，`Service` 在这里是"搜索服务商"的领域词，
不是 Android 组件。

真正的不改理由是另外两条：

1. **100% 上游继承**。上游 20 个同名文件在
   `git ls-tree -r --name-only upstream-ssh/master` 里逐个对得上，位于
   `search/src/main/java/me/rerere/search/`（注意上游包名是 `me.rerere.search`，
   **不在** `me/rerere/rikkahub/` 下，所以不计入 §3 的 405）。改名的收益要靠每次上游
   同步的人工三方合并来付（§3）。
2. **改名要连带改持久化契约**。`SearchService.kt` 里有
   - `companion object getService(options)` 的 20 个 `when` 分支；
   - 20 个 `SearchServiceOptions.*Options` 各自带 `@SerialName`（`"bing_local"`、
     `"rikkahub"` 等），这些字符串是**已落盘的设置项 key**，改了会导致用户已有配置读不出来。

> ⚠️ 这一点必须写清楚，因为它推翻了一条会被后续 agent 直接引用的结论：
> **「`search` 的 20 个 `*Service` 不能改，因为 Android 里 Service 是组件」是错的**，
> 改成 `*Provider` 在 Kotlin 层面完全可行，只是上面两条代价让它不划算。

**（c）沿用上游命名，3 个 —— 不改**

- `app/…/core/service/ChatService.kt`（不继承 `android.app.Service`，是 Hilt 单例）
- `app/…/feature/chat/AILiveNotificationService.kt`
- `khatkit/src/main/java/heizige/kk/khatkit/bridge/impl/KhatKitDownloadService.kt`
- `oauth/src/main/java/heizige/kk/khatkit/oauth/OAuthCallbackForegroundService.kt`
  （这条其实继承 `android.app.Service`，与 (a) 同理）

#### `*Util.kt` = 9 / `*Utils.kt` = 11 —— 不改，因为收益是"看着整齐"

`core/util/` 下 7 个 `*Utils` + 7 个 `*Util` 占了大头：

```
app/…/core/util/CollectionUtils.kt    app/…/core/util/CacheUtil.kt
app/…/core/util/CoroutineUtils.kt     app/…/core/util/ChatUtil.kt
app/…/core/util/DiffUtils.kt          app/…/core/util/ClipboardUtil.kt
app/…/core/util/EmojiUtils.kt         app/…/core/util/ContextUtil.kt
app/…/core/util/ImageUtils.kt         app/…/core/util/DatabaseUtil.kt
app/…/core/util/MarkdownUtils.kt      app/…/core/util/NotificationUtil.kt
app/…/core/util/StringUtils.kt        app/…/core/util/PlayStoreUtil.kt
                                     app/…/core/util/TimeUtil.kt
```

另外 4 个 `*Utils` 在 `core/` 之外：
`ai/…/ai/provider/providers/ProviderMessageUtils.kt`、
`app/…/core/data/db/migrations/MigrationUtils.kt`、
`app/…/core/data/files/FileUtils.kt`、`app/…/core/network/routes/RouteUtils.kt`。
第 9 个 `*Util` 在 `common/…/common/android/ContextUtil.kt`。

**不改的理由**：

1. 其中 10 个在上游有同名对应物（`core/util/` 的 7 个 `*Utils` 与
   `common/android/ContextUtil.kt` 全是；见 §3 的人工三方合并）。改名的收益要靠每次
   上游同步额外做一遍人工改名比对来付。
2. §2 自己就写了 `Extensions` 这一档「项目里几乎用不到，需要时再建」。为一个已经
   稳定、且被 743 个 kt 大量引用的工具类集合做批量改名，得到的是**零功能收益**。

#### `*Store.kt` = 11 —— 其中 1 个已经改过，其余不改

**⚠️ 一条重要订正**：`ARCHITECTURE.md` §2.2 的豁免条款「`PreferencesDataStore` 是官方
类名所以保留」**对 KhatKit 已经不适用**。

```bash
grep -rln 'PreferencesDataStore' --include=*.kt . | wc -l    # → 0
```

上游的 `SettingsStore` 在 2026-10-01 的同步（commit `8cf9bec2d`）里已按 §2 改名成
`app/src/main/java/heizige/kk/khatkit/app/core/data/datastore/SettingsRepository.kt`
（40119 字节）。**这条豁免不用再写进任何规范文档。**

剩余 11 个 `*Store`，全部是"确实只做存取/缓存"的类，`Repository` 反而会误导：

```
app/…/core/data/browser/BrowserHistoryStore.kt      common/…/common/cache/CacheStore.kt
app/…/core/ui/hooks/PlayStore.kt                     common/…/common/cache/PerKeyFileCacheStore.kt
app/…/feature/automation/CardScheduleStore.kt        common/…/common/cache/SingleFileCacheStore.kt
app/…/feature/settings/WorkflowStore.kt              khatkit/…/bridge/impl/CardSqlStore.kt
                                                     khatkit/…/bridge/impl/SecretStore.kt
                                                     khatkit/…/bridge/impl/SharedStore.kt
                                                     khatkit/…/dependency/DependencyArtifactStore.kt
```

其中 `common/cache/` 的 3 个与 `khatkit/bridge/impl/` 的 3 个都是**磁盘缓存实现**，
`CacheStore` 改成 `CacheRepository` 是纯退化。

### 2.3 一条实测出来的存量不一致（**只记录，未改**）

`ChatGenerationForegroundService` 在仓库里有**两份**：

| 文件 | 行数 | package | manifest 声明 | 被谁引用 |
|---|---:|---|---|---|
| `app/…/core/service/ChatGenerationForegroundService.kt` | 175 | `…app.core.service` | ❌ 无 | `core/service/ChatService.kt:298,307`（同包简名引用） |
| `app/…/feature/chat/ChatGenerationForegroundService.kt` | 179 | `…app.feature.chat` | ✅ `:149` | `feature/chat/ChatManager.kt:364,373` |

两份都执行 `Intent(context, ChatGenerationForegroundService::class.java)`（`:40`/`:42`
与 `:54`/`:56`），即各自 start 自己。

### ⚠️ 订正（2026-10-06）：上一轮这条结论是**错的**，原文保留在下面

**⚠️ 上一轮原文（已证伪，勿照抄）**：「`core/service/ChatService.kt` 是活代码
（`core/di/AppHiltModule.kt:51` 绑定，`feature/history/HistoryVM.kt:28` 注入）」——
**`AppHiltModule.kt:51` 那行不是绑定，是一条未使用的 import。**

实测依据（命令与退出码见 §2.3.1）：

```bash
# ① 没有任何 @Provides / @Binds 产出 ChatService —— 命中的 2 处都不是绑定
grep -rn "ChatService" --include=*.kt . | grep -E "@Provides|@Binds"
#   → core/service/ChatService.kt:153（**KDoc 里的一句注释**）
#   → …/ChatServiceGroupChatFailLoudGuardTest.kt:133（**断言字符串字面量**）
#   ⇒ 真绑定 0 处

# ② 没有任何直接 new
grep -rnE "ChatService\s*\(" --include=*.kt . | grep -vE "fun |class |//|\*"
#   ⇒ 空（EXIT=1）

# ③ AppHiltModule 里 ChatService 只出现在 import 行
grep -n "ChatService\|ChatManager" core/di/AppHiltModule.kt
#   → :36 import …feature.chat.ChatManager
#   → :51 import …core.service.ChatService   ← 全文再无第二次出现 ⇒ 未使用 import
#   → :189 @Provides / :191 fun provideChatManager( / :211 ): ChatManager = ChatManager(
```

⇒ **`ChatService.kt` 的正确判定是「活接线 / 运行时不可达」，既不是活代码也不是死代码。**
措辞与 §2.3.1、与 `docs/beyond-operit-implementation-status.md:1500`（「已核实当前不可达」）
统一，三处现在说的是同一件事。⚠️ **这个区别决定处置方式**：直接 `rm` 会炸编译。

### 2.3.1 因此 core 那份 FGS 是**静态可判死**，已判死 —— 原「需真机验」是多余的

上一轮把「指向未声明组件」列进 §8 待验清单第 1 条（**需真机跑一次后台生成**）。
⚠️ **那条真机验证是多余的**：`keepAliveInBackground` 那条路径**根本走不到**
`startForegroundService`。两条**各自独立、任一即足**的理由：

| # | 理由 | 实测 |
|---|---|---|
| 1 | manifest 只注册 feature 版 | `AndroidManifest.xml:149` = `.feature.chat.ChatGenerationForegroundService`；`grep -n "core\.service" AndroidManifest.xml` ⇒ **空（EXIT=1）** |
| 2 | core 那份**缺 `@AndroidEntryPoint`** ⇒ `@Inject` 字段永不注入，**即使被拉起也必 NPE** | `grep -n AndroidEntryPoint core/service/ChatGenerationForegroundService.kt` ⇒ **空（EXIT=1）**；feature 版在 `:19` import、`:31` 标注 |

再加上第三层封口：**唯一调用方 `core/service/ChatService.kt:298`（`acquire`）与 `:307`
（`release`）自身不可达**（§2.3.1 的零构造点结论）。

⇒ **静态可判死，已判死。不需要真机。** §8 待验清单里那条已移除（见 §8 的订正说明）。
⚠️ 注意这**只是**「core 那份 FGS 判死」，**不等于**「`ChatService.kt` 可以直接删」——
后者的三处依赖见 §9.2。

上一轮原文的另两条「两种可能的解释都不做判定」也随之失效：判据 1 与 2 都是实测，
manifest **没有漏声明**（漏声明会让 lint / `adb install` 阶段报错，而 lint 实测
`error 0`），所以解释 (2)「manifest 漏了声明」已被排除。解释 (1)「layer-first 残留」
成立，**但真正的原因是 vendor merge 复活**，见 §9。

---

## 3. 上游继承面（**这节是全文最不能忽略的**）

### 3.1 实测数据

```bash
git remote -v
# origin        ssh://git@ssh.github.com:443/KhatKit/Android.git
# upstream      https://github.com/rikkahub/rikkahub
# upstream-ssh  git@github.com:rikkahub/rikkahub.git

git ls-tree -r --name-only upstream-ssh/master -- app/src/main/java/me/rerere/rikkahub | wc -l
# → 405   （其中 404 个 .kt）
git ls-tree -r --name-only main -- app/src/main/java/heizige/kk/khatkit/app | wc -l
# → 744   （其中 743 个 .kt + 1 个 core/ui/components/ui/permission/README.md）

git rev-list --count upstream-ssh/master..main
# → 3349
```

⇒ **`app` 模块内 405 / 744 = 54.4% 的文件路径来自上游。**

（任务书给的是 405/742 = 55%；`742` 这个数我复算不出 —— `ls-tree` 给 744，
`find` 给 743。本文用 744 与 54.4%。）

上游 `app` 侧的目录是 **layer-first**，与本 fork 的 feature-first **完全相反**：

```
me/rerere/rikkahub/          （git ls-tree 实测，共 404 .kt）
├── ui/        233
├── data/      123
├── utils/      22
├── web/        13
├── service/     7
├── di/          4
├── RouteActivity.kt      1
└── RikkaHubApp.kt        1
```

对照 KhatKit 的 `core/{data,di,network,service,ui,util}` + `feature/`×22
（§1.1 / §1.4）。**同名不同层**：上游 `ui/` 是一个 233 文件的巨包，本 fork 把它拆成
`core/ui`(315) + 22 个 `feature/*`；上游 `utils/`(22) 对应本 fork 的 `core/util/`(24)。

### 3.2 ⚠️ `git merge` 早已不可用

commit `8cf9bec2d38fb22b9cb0be0f25d41484ec19eee5`（2026-10-01 17:38:51 +0800，
`merge: 同步上游 RikkaHub master(2.5.5 + 29 个提交)`）的 message 原文：

> 上游从 7263dd36(2.5.4)推进到 9435b8a2,共 29 个提交、173 个文件。
> 本地已领先上游 2964 个提交(含整树包名重构 me.rerere.* -> heizige.kk.*),
> **直接 merge 会产生大量伪冲突,因此逐文件三方合并**,策略如下。

> 包名与依赖
> - me.rerere.* -> heizige.kk.khatkit.* 全仓改写;SettingsStore -> SettingsRepository
> - 上游用 koin,我们用 hilt:FavoriteVM/HistoryVM/StatsVM 改 @HiltViewModel +
>   @Inject constructor,页面侧 koinViewModel() -> hiltViewModel();
>   ChatGenerationForegroundService 的 by inject() 改字段注入
> - 网络层保持 Ktor,未引入上游的 OkHttp/Retrofit

**这就是"为什么不能随便动包路径"的答案。** 整棵树做过一次
`me.rerere.* -> heizige.kk.khatkit.*` 的重命名，本地领先上游 2900+ 个提交。Git 的
merge base 已经找不到有意义的共同祖先，任何 `git merge upstream/master` 都会把
**每一个**上游文件判成"双方都改过"的伪冲突，然后需要人工把 744 个路径逐个对回去。

### 3.3 人工合并的实际策略（上一次同步的做法，可复用）

1. **包名与依赖**：上游 `me.rerere.*` 一律映射到 `heizige.kk.khatkit.*`；
   上游的 `SettingsStore` 在本 fork 叫 `SettingsRepository`（§2.2），
   合并时按**语义**对位而不是按名字对位。
2. **DI**：上游 koin → 本 fork Hilt。三个 VM（`FavoriteVM`/`HistoryVM`/`StatsVM`）
   要改 `@HiltViewModel` + `@Inject constructor`，页面侧 `koinViewModel()` →
   `hiltViewModel()`；`ChatGenerationForegroundService` 的 `by inject()` 改字段注入。
3. **网络层**：保持 Ktor。**不要**接受上游的 OkHttp/Retrofit —— `:common` 的
   `common/http/okhttp/` 是本 fork 自研包（`SearchService.kt` 直接 import
   `heizige.kk.khatkit.common.http.okhttp.OkHttpClient`），不是上游那个库。
4. **图标**：上游 2.5.5 起改用 HugeIcons/Lucide，本 fork 走自绘图标
   `core/ui/icons/`（150 个 kt，小驼峰符号名），**不引入** Maven 坐标。
5. **Miuix / Kedge**：页面组件以 Kedge 组件库为准，上游的 MD3 组件要落到 Kedge 上
   （`ItemActionMenu` → `KedgeIconButton` + `KedgeDropdownMenuSlots` 等）。

---

## 4. 上游同步检查清单

```bash
# ① 先 fetch 三个 ref —— 不 fetch 就拿旧 ref 做判断，见 ④
git fetch upstream upstream-ssh origin --prune

# ② 上游现在到哪了
git log -1 --format='%H %ad %s' --date=short upstream/master
git log -1 --format='%H %ad %s' --date=short upstream-ssh/master

# ③ 本地已经同步到哪了（找最近一次同步提交）
git log --oneline -20 --grep='同步上游'

# ④ ⚠️ 必查：两个 tracking ref 是否已经落后于 main 里的同步提交
git log -1 --format='%H %ad %s' --date=short upstream-ssh/master
git log --oneline --all --grep='2\.5\.5'

# ⑤ 规模对照（§3.1 的两个数）
git ls-tree -r --name-only upstream-ssh/master -- app/src/main/java/me/rerere/rikkahub | wc -l
git ls-tree -r --name-only main -- app/src/main/java/heizige/kk/khatkit/app | wc -l

# ⑥ 合并前后必跑
./gradlew --offline :app:assembleDebug
./gradlew test
```

### ④ 是一条已经踩过的坑，必须每次查

**本次复算时的实况（`main` @ `0d2f6838a`）**：

| ref | HEAD | 日期 | 版本 |
|---|---|---|---|
| `upstream-ssh/master` | `7263dd36194ace478cd82ccd1067590f8fd04fc5` | **2026-09-24** | `chore: bump to 2.5.4` |
| `upstream/master` | `2267943a32d9208769b1ee199f3186d26066d1a3` | 2026-10-02 | `feat(mediagen): …(#1984)` |
| `main` 里的同步提交 | `8cf9bec2d`（2026-10-01） | 2026-10-01 | 已含上游 **2.5.5** |

⇒ **`upstream-ssh/master` 这个本地 ref 停在 2.5.4，而 `main` 里已经有 2.5.5 的同步提交。**
如果不先 `git fetch`，`git log upstream-ssh/master..main` 会把**已经合并过的那 29 个
提交再算成"待同步"**，导致重复劳动或误判。

⚠️ 而且 `upstream`（HTTPS）与 `upstream-ssh`（SSH）**这两个 ref 现在指向不同的提交**
（2026-10-02 vs 2026-09-24），说明它们是各自独立 fetch 的。**必须显式 fetch 两者**，
不要只 fetch 一个。

**永远不要推 `upstream` / `upstream-ssh`。** 它们是 `rikkahub/rikkahub`，本 fork 只往
`origin`（`KhatKit/Android`）推。

---

## 5. Nav3 核对项（`ARCHITECTURE.md` §8.4 四条）

**只读核对，未改任何代码。** KhatKit 已落地 §8 的三条 API：
`RouteActivity.kt:362` 的 `predictivePopTransitionSpec`、
`core/ui/hooks/HeroAnimation.kt:5,21` 的 `LocalNavAnimatedContentScope`、
`feature/onboarding/GreetingPage.kt:216` 的 `rememberNavigationEventState`。

### §8.4-1 子动画 `tween` 时长与 spec 一致 —— **N/A（空真），无不一致**

```bash
grep -rn 'NavAnimatedContentScope|\.transition\.animate|scope\.transition' --include=*.kt app/src/main
```

只命中 `HeroAnimation.kt` 的第 5 行 import 与第 21 行，**全仓没有任何
`scope.transition.animateXxx()` 子动画**。所以"跟手子动画"数量为 0，没有可比对的时长。

> 附带结论：`predictivePopTransitionSpec`（`RouteActivity.kt:361-364`）本身也**没有写
> 任何显式 `tween`**。§8.4-1 与 §8.4-2 之所以都指向这里，是因为同一个原因 —— 见下条。

### §8.4-2 spec 与子动画优先 `tween` 不用 `spring` —— **不满足**

`RouteActivity.kt:361-364` 的 spec 用的全是**不带 `animationSpec` 的重载**：

```kotlin
predictivePopTransitionSpec = {
    slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f) + fadeIn() togetherWith
        slideOutHorizontally { it }
}
```

已从 **Compose 源码**核实这些重载的默认值（`~/.gradle/caches/.../animation-android-1.12.0-sources.jar`
→ `commonMain/androidx/compose/animation/EnterExitTransition.kt`）：

| 函数 | 行 | `animationSpec` 默认值 |
|---|---:|---|
| `fadeIn` | 312 | `spring(stiffness = Spring.StiffnessMediumLow)` |
| `fadeOut` | 330 | `spring(stiffness = Spring.StiffnessMediumLow)` |
| `scaleIn` | 417 | `spring(stiffness = Spring.StiffnessMediumLow)` |
| `scaleOut` | 447 | `spring(stiffness = Spring.StiffnessMediumLow)` |
| `slideInHorizontally` | 763 | `spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = …)` |
| `slideOutHorizontally` | 823 | `spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = …)` |

⇒ **当前预测性返回实际跑的是 spring。** 这正是 §8.1 铁律要避免的情形：规范写明
"剩余时长基于 `totalDurationNanos`，tween 才可控"，而 spring 没有可用的
`totalDurationNanos`，松手续播的对齐无从谈起。

全仓 `app/src/main` 统计：`grep -rn 'spring' --include=*.kt app/src/main | wc -l` → **44**，
`grep -rn 'tween' … | wc -l` → **74**。

⚠️ **只记录，未改。** 改法是把四处重载补上显式 `tween(...)`，但那会改变现有手感，
属于产品决策而非记账。

### §8.4-3 manifest 开 `android:enableOnBackInvokedCallback="true"` —— **满足**

`app/src/main/AndroidManifest.xml:69` 已有该属性。合并产物同样带：
`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml:94`。

### §8.4-4 `LocalNavAnimatedContentScope` 只在 `NavEntry.Content` 内用 —— **部分可查证 + 待验**

**静态可确认的**：

- 全仓**只有 1 个读取点** —— `core/ui/hooks/HeroAnimation.kt:21`
  （`val animatedVisibilityScope = LocalNavAnimatedContentScope.current`），
  被封装在 `@Composable fun Modifier.heroAnimation(key: Any)` 里，
  用途是给 `sharedElement()` 传 `animatedVisibilityScope`。
- 调用点 **7 处**，分布在 3 个文件：
  `core/ui/context/SharedElement.kt`、
  `feature/assistant/AssistantPage.kt`、
  `feature/assistant/detail/AssistantBasicPage.kt`、
  `feature/assistant/detail/AssistantDetailPage.kt`。

**静态判不了的**：`heroAnimation` 是 Modifier 扩展，它落在哪个组合作用域取决于**调用处
的位置**，静态分析无法证明这 7 处运行时都在 `NavEntry.Content` 内。**⇒ 待验。**

**一条降低风险的间接证据**：

```bash
grep -rn 'OverlayScene|overlayScene|sceneState' --include=*.kt app/src/main   # → 0 命中
```

项目**完全没有使用** OverlayScene / hoist scene，所以规范里"OverlayScene 中是 no-op"
那条失效路径当前不会被触发。风险因此低，但不是零（见 `HeroAnimation.kt` 自己的注释：
搜索框全屏层是独立窗口，跨 ViewRoot 配对会崩，那里用
`NoHeroTransition` 把 scope 置空来规避）。

### 附带：§8.4-5（commit/cancel 由系统阈值判定）—— **满足**

`feature/onboarding/GreetingPage.kt:216-221`：

```kotlin
val backEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
NavigationBackHandler(
    state = backEventState,
    isBackEnabled = step != GreetingStep.Welcome,
    onBackCompleted = goBack,          // 只在这里动状态，没有 cancel 分支
)
```

⚠️ 但 `grep -rn 'rememberSceneState' --include=*.kt app/src/main` → **0 命中**，
所以 §8.3 里"附加跟手效果（视差、边缘阴影）"那条 hoist 路线**未被使用**。

---

## 6. 版本目录与 `[bundles]`

`gradle/libs.versions.toml` 是本仓库**唯一**的版本来源（`[versions]` 79 行区段、
`[libraries]` 131 个 alias、`[plugins]` 10 项）。模块 `build.gradle.kts` 里不写死版本。

### 6.1 加了什么

`[bundles]` 此前**不存在**（`grep -n 'bundles' gradle/libs.versions.toml` → 无命中）。
现只加一项：

```toml
[bundles]
androidTest = ["androidx-junit", "androidx-espresso-core"]
```

**替代了 7 个模块里的 14 处声明**：`ai` / `app` / `common` / `document` / `speech` /
`web` / `workspace`，各 2 行 → 各 1 行
`androidTestImplementation(libs.bundles.androidTest)`。

### 6.2 为什么只有这一组

判定标准两条同时成立才建 bundle：**(1) 同一组 ≥2 个 artifact；(2) 各模块的
configuration 名与 `api`/`implementation` 语义完全一致。** 条件 (2) 是硬约束 ——
bundle 会把组内所有成员绑到同一个 configuration 上，只要有一处是 `api(...)` 其余是
`implementation(...)`，打包就会悄悄改动模块的 API surface。

用「把 `libs.*` 访问器链还原成 catalog alias、再统计出现在 2+ 模块的 alias」逐组核对，
候选与结论：

| 候选组 | 出现在 | 结论 |
|---|---|---|
| `androidx-junit` + `androidx-espresso-core` | 7 个模块，全部 `androidTestImplementation` | ✅ **建 bundle**（`app/baselineprofile` 用的是 `implementation(...)`，**不并入**） |
| `junit`（`testImplementation`，15 个模块） | 15 | ❌ 只有 1 个 artifact，bundle 不做任何分组，只是把 `libs.junit` 换成 `libs.bundles.test` 的纯间接层 |
| `ktor-server-core` + `ktor-server-cio` | `oauth`(implementation) / `web`(**api**) | ❌ configuration 语义不一致 |
| `ktor-client-core` + `ktor-client-cio` | `app`(impl) / `common`(**api** for core) / `khatkit`(impl) | ❌ 同上 |
| `androidx-ui` + `androidx-ui-graphics` | `app`/`highlight`/`khatkit-ui`(impl) / `mediapicker`(**api** for ui) | ❌ 同上，且成员不齐（`app`/`highlight` 还带 `ui.tooling.preview`，`khatkit-ui` 不带） |
| `junit` + `androidx-junit` + `espresso` | 混用 `testImplementation` 与 `androidTestImplementation` | ❌ 无法用同一个 configuration |
| `navigation3-runtime` + `navigation3-ui` + `lifecycle-viewmodel-navigation3` + `material3-adaptive-navigation3` | **只有 `app` 一个模块** | ❌ **调研提示的"nav3 一组 4 个 artifact 跨模块重复"不成立** |
| `coil-compose` / `image-viewer` / `commons-text` / `kotlinx-datetime` / `material` / `quickjs` | 各 2–4 个模块 | ❌ 都是单个 artifact，无可分组 |

**关于 nav3 的订正**：调研提示"`navigation3` 一组就有 `nav3Core`/`navigation3Ui`/可能还有
lifecycle 桥接，共 4 个 artifact"。artifact 数量说对了（catalog 里确有这 4 个），
但**跨模块重复不存在** —— `app/build.gradle.kts:199-202` 是唯一引用点：

```
implementation(libs.androidx.navigation3.runtime)
implementation(libs.androidx.navigation3.ui)
implementation(libs.androidx.lifecycle.viewmodel.navigation3)
implementation(libs.androidx.material3.adaptive.navigation3)
```

给它建 bundle 就是 §5 语境下最该避免的**死配置**。

---

## 7. 与 `../ARCHITECTURE.md` §6 技术栈的差异（**不要"对齐"**）

全部取自 `gradle/libs.versions.toml` 的 `[versions]` 区段（行号即该文件行号）：

| 技术 | `ARCHITECTURE.md` §6 | KhatKit `libs.versions.toml` | 判定 |
|---|---|---|---|
| Navigation3 | `1.2.0-alpha07` | `nav3Core = "1.2.0"`（`:17`） | ⚠️ 本 fork 从 alpha 进了正式版，**对齐即倒退** |
| Navigation3（adaptive） | — | `nav3Material = "1.3.0"`（`:20`） | — |
| `navigationevent` | `1.2.0-alpha04` | 见 catalog `androidx-navigationevent-*` 的 version ref | — |
| lifecycle-viewmodel-navigation3 | `2.12.0-alpha01` | `lifecycleViewmodelNav3 = "2.11.0"`（`:19`） | ⚠️ 本 fork 取**稳定版**，§6 取更新的 alpha。**这是取舍不是落后** |
| ksp | `2.2.10-2.0.2` | `ksp = "2.3.12"`（`:35`） | ⚠️ 本 fork **更新**，对齐即倒退 |
| Compose BOM | `composeBom`（未写死） | `composeBom = "2026.09.00"`（`:13`） | ✅ 一致 |
| Ktor | `3.5.2` | `ktor = "3.5.2"`（`:66`） | ✅ 一致 |
| DataStore | `1.3.0-alpha10` | `datastore = "1.2.1"`（`:31`） | ⚠️ 本 fork 取**稳定版**，§6 取更新的 alpha |
| Hilt | `2.60.1` | `hilt = "2.60.1"`（`:29`） | ✅ 一致 |
| kotlinx-serialization | `1.11.0` | `serialization-json = "1.11.0"`（`:32`） | ✅ 一致 |
| AGP | — | `agp = "9.3.1"`（`:2`） | — |
| Kotlin | — | `kotlin = "2.4.20"`（`:5`） | — |

⇒ **没有任何一行是"本 fork 落后、应该升上去"的。**

⚠️ 顺带记录一条不是版本号但更硬的信号：`ARCHITECTURE.md` §6 的 8 行与
Cynic 的 `libs.versions.toml` 逐行相等。**那份表是 Cynic 的版本快照，不是通用基线。**

---

## 8. 待验清单（本轮无法只读判定）

⚠️ **上一轮的第 1 条已删除**（`core/service/ChatGenerationForegroundService.kt` 指向未声明组件）。
**它不是「待验」，是「静态可判死、已判死」**：manifest 只注册 feature 版（`:149`）、
core 那份缺 `@AndroidEntryPoint`（`@Inject` 字段永不注入）、唯一调用方
`ChatService.kt:298/307` 自身不可达 —— 三层封口，零设备可判。完整依据见 §2.3.1。

| # | 事项 | 为什么待验 |
|---|---|---|
| 1 | `heroAnimation` 的 7 个调用点是否都在 `NavEntry.Content` 内（§5 §8.4-4） | 作用域由组合位置决定，静态不可判 |
| 3 | `predictivePopTransitionSpec` 跑 spring 的**实际手感影响**（§5 §8.4-2） | 需要人拿设备对比；改法本身超出记账范围 |
---

## 9. `core/service/` 死代码复活的根因与对策（本轮新增，**下一个人必读**）

⚠️ **这一节存在的唯一理由**：上一轮之前的某个人**已经决定删过**这 5 个文件，
删完 6 天后它们**原样回来了**。不知道这件事的人会**再删一次、再被复活一次**。

### 9.1 根因时间线（实测）

| 日期 | commit | 发生了什么 |
|---|---|---|
| 2026-09-25 | `f7463f814` | `refactor(structure): move chat pages and conversation services to feature/chat` —— **项目主动**把 5 个文件从 `core/service/` 搬到 `feature/chat/`（`git show --stat` 里 5 行 `{service => feature/chat}/…` rename） |
| 2026-10-01 | `8cf9bec2d` | `merge: 同步上游 RikkaHub master(2.5.5 + 29 个提交)` —— **上游那侧路径没变**，5 个文件被当成新文件**整包搬回来** |

决定性的一条实测：

```bash
$ git diff --stat 8cf9bec2d^1 8cf9bec2d -- app/src/main/java/heizige/kk/khatkit/app/core/service/
 …/service/ChatGenerationForegroundService.kt     | 175 +++
 …/kk/khatkit/app/core/service/ChatService.kt     | 1407 ++++++++++++++++++++
 …/app/core/service/ConversationSession.kt        | 180 +++
 …/app/core/service/ConversationSessionManager.kt | 114 ++
 …/kk/khatkit/app/core/service/MessageQueue.kt    | 118 ++
 5 files changed, 1994 insertions(+)
```

**1994 insertions(+)，0 deletions** —— 纯新增。**不是「有人忘了删」，是「已经决定删过、被
vendor merge 复活」。** 复活后只有 `ChatService.kt` 继续被改（`git log --oneline
8cf9bec2d..HEAD -- core/service/` 恰好 **7** 个 commit，全中 `ChatService.kt`），
另外 4 个**一动没动**，说明没人把它们当成有意保留的活代码。

### 9.2 逐个判定（**不要笼统说「core/service 都是死代码」**）

行数用 `wc -l`，diff 用 `diff core/service/X.kt feature/chat/X.kt`。

| 文件 | 行数 | 判定 | 依据 |
|---|---:|---|---|
| `ChatService.kt` | 1506 | ⚠️ **活接线 / 运行时不可达（不可直接删）** | `@Inject constructor`（`:177`）⇒ Hilt JIT 绑定**存在**；`HistoryVM.kt:28` 注入、`:55` 调 `toggleConversationPinned`；**但** `HistoryPage.kt:69` 全仓零路由引用 ⇒ 零构造点。⇒ **直接 `rm` 会炸编译**，必须先拆 §9.2.1 三处依赖 |
| `ChatGenerationForegroundService.kt` | 175 | ✅ **死（无争议）** | manifest 只注册 feature 版（`:149`，`grep -n "core\.service" AndroidManifest.xml` 空）；core 版**缺 `@AndroidEntryPoint`** ⇒ `@Inject` 字段永不注入，即使被拉起也必 NPE；唯一调用方 `ChatService.kt:298/307` 自身不可达 |
| `MessageQueue.kt` | 118 | ✅ **死，且是逐字副本** | 与 `feature/chat/MessageQueue.kt` 的 `diff` **只有 package 一行** |
| `ConversationSessionManager.kt` | 114 | ✅ **死，且是逐字副本** | `diff` **2 行**：package + KDoc 里 `ChatService`→`ChatManager` 一个词 |
| `ConversationSession.kt` | 180 | ✅ **死，且已过期** | `diff` **42 行**，缺 `existingMessageIds` / `dropUngeneratedAssistantMessages` |

#### 9.2.0 ⚠️ `ConversationSession` 的方向必须说清楚，否则会删反

上一轮担心的「`core/service` 里可能藏着力步失败的修复」—— **确实存在，但修复在
`feature/chat` 一侧，不在 `core/service` 一侧**：

- 修复来自 `97b0fa1cc`（2026-10-04，`fix(c1-r): finishGeneration 落库前丢弃本次生成新增的
  空气泡助手消息…`），`git show --stat` = **只改 1 个文件**：
  `feature/chat/ConversationSession.kt`（+36 / −4）。
- `grep -rln "existingMessageIds\|dropUngeneratedAssistantMessages" --include=*.kt app/src/`
  ⇒ `feature/chat/ConversationSession.kt`、`feature/chat/UngeneratedMessageFilter.kt`
  及其测试 —— **`core/service/` 侧零命中**。

⇒ **删掉 `core/service` 那份过期副本是「修正」不是「退化」**：活路径用的 feature 版
带着修复，留着 core 版只会让人误读成「有两份实现，得挑一份」。

#### 9.2.0.1 两份 FGS 的差异只有 **6 行**，其余 169 行逐字相同

`diff` 输出 24 行 = 4 个 hunk，共 **6 处实质差异 + 2 处新增空行**：

| 差异 | core 版 | feature 版 |
|---|---|---|
| package | `…app.core.service` | `…app.feature.chat` |
| import 顺序 | — | 多 `import dagger.hilt.android.AndroidEntryPoint`（`:19`） |
| 类注解 | **无** | `@AndroidEntryPoint`（`:31`） |
| KDoc | `[ChatService]`（`:27`） | `[ChatManager]`（`:28`） |
| 注入字段类型 | `chatService: ChatService`（`:71`） | `chatService: ChatManager`（`:75`） |

175 − 6 = **169 行逐字相同**。⇒ 两份是**同一份代码的两个拷贝**，不是两种实现。

#### 9.2.1 ⚠️ 删 `ChatService.kt` 前**必须**先拆的三处依赖

⚠️ **这就是「不可直接删」的具体含义**。三处都在 `app/src/test/`（JVM 单测，会编译失败）：

1. **`forkConversationTitle` 被跨包 import** — 它是 `core/service/ChatService.kt:111` 的
   `internal` 顶层函数，而 `feature/chat/ChatManagerTest.kt:18` 直接
   `import heizige.kk.khatkit.app.core.service.forkConversationTitle`，
   `:43-47` 有 **5 条断言**。⇒ 删文件 = 删掉这 5 条断言的**被测对象**。
   ⚠️ 处置：把这一个纯函数**搬进 `feature/chat`**（feature 版已有同名等价物或直接搬），
   import 改指同包，断言一行不动。
2. **两个护栏测试硬编码了 `File(...).readText()` 的绝对路径**：
   - `ChatServiceGroupChatFailLoudGuardTest.kt:156-157`
     `CHAT_SERVICE_FILE = "app/src/main/java/heizige/kk/khatkit/app/core/service/ChatService.kt"`
   - `ChatServiceSenderNameGuardTest.kt:152` 同一路径
   两者都 `File(repoRoot(), …).readText()` 读源码做文本断言。⇒ 文件一删，
   `assertTrue("函数没找到…")` / 断言直接红。⚠️ 处置：常量改指
   `feature/chat/ChatManager.kt`（这两个护栏守的就是那份**活**实现的内容）。
3. **`HistoryVM` / `HistoryPage` 孤儿** — `HistoryVM.kt:28` 的注入点必须先摘掉
   （它只用 `toggleConversationPinned`，`:55`），`HistoryPage.kt:69` 本身零路由引用，
   整对一起处置（要么删、要么接进路由），**不能留一个注入着已删类的 ViewModel**。

⇒ **顺序**：拆 1 → 拆 2 → 处置 3 → 才 `rm ChatService.kt` → 再判另外 4 个。
⚠️ 三处拆完前**一步都别动**。

### 9.3 对策：为什么还会再发生，怎么防

| 风险 | 对策（可执行） |
|---|---|
| **vendor merge 复活按路径删除的文件** —— 上游 `me.rerere.rikkahub/service/` 路径不变，本 fork 搬走后，merge 把它们当新文件带回 | **每次上游同步后必查**：见 §4 ④ 的 tracking ref 检查，**加一条** `git diff --stat <同步前> <同步后> -- app/src/main/java/heizige/kk/khatkit/app/core/service/`，看有没有 `insertions(+)` 而 0 deletions。**有 ⇒ 又是复活，立刻回到本节** |
| 下一个不知道历史的人再删一次 | **删除后必须在本文件登记**（就是这张表）。删完不登记 = 这个坑重新埋一遍 |
| 同一路径下次同步再被搬回来 | 把上游路径差异记在这里：上游侧仍是 `me.rerere.rikkahub/service/{ChatService, ChatGenerationForegroundService, ConversationSession, ConversationSessionManager, MessageQueue}.kt`，**本 fork 侧这些路径应当为空**。⚠️ **只要上游还在那个路径上，同步就一定会再搬回来一次** —— 所以真正的解法是「每次同步后复查」，不是「删一次就完事」 |
| 判成「死代码」直接删，结果炸编译 | 判定必须分三类（§9.2），`ChatService.kt` 那条是**活接线**，删前必拆 §9.2.1 三处 |

⚠️ **一句话**：这个坑不是「有人不仔细」，是**vendor merge 的结构性行为**。
每同步一次上游就要复查一次，登记在册才不会重复劳动。

---

## 10. `ChatService.kt` × `ChatManager.kt` 重复度复算（**口径写清，否则不可复现**）

⚠️ 「86.7%」这个旧数字**没有口径就是废话**，而且换个口径能差 20 个百分点。
下表两种口径都给出来，并写清各自算法。

| 口径 | A 覆盖率 | Jaccard |
|---|---:|---:|
| **RAW**（全部行，1506 vs 2362） | **86.06%** | **67.06%** |
| **CODE**（剥空行 + `//` + `*` + `/*`，1197 vs 1749） | **88.97%** | **72.30%** |

**A 覆盖率 = 共同行数 ÷ ChatService 的行数**（问的是「ChatService 有多少行是抄来的」）。
**Jaccard = 共同行数 ÷ 两边并集行数**（问的是「两个文件整体有多像」）。
⇒ **同一个数字被记成 86.7%，实际是 RAW 口径的 A 覆盖率**，不是 Jaccard。

⚠️ **旧记录的 86.7% ≈ RAW A 覆盖率 86.06%**，口径是「**`ChatService` 的行里有多少行
在 `ChatManager` 里逐字不变**」（`diff` 的 `<` 侧取反），**不是 Jaccard**。
⇒ 用 Jaccard 只有 **67.06%**，两者差近 19 个百分点。

### 10.1 ⚠️ 为什么 Jaccard 差这么多 —— 单看 RAW 会被「块插入」骗

`ChatManager` 有 **412** 行注释（`//` / `*` / `/*`，不含空行）vs `ChatService` **142** 行。
更关键的是：`diff` 显示 `ChatManager` 在**共同代码中间插了 1066 行**
（`>` 侧 1066 行 vs `<` 侧 210 行）。

⇒ **两文件行号完全错位**。任何按行号对齐的口径（以及人眼对 `diff` 的直觉）都会把
「插入块」误读成「不相似」。**必须同时看 RAW 和 CODE 两行**：
CODE 口径下 A 覆盖率反而**升到 88.97%**，说明那 1066 行里大部分是注释/空行，
不是逻辑分歧。

⚠️ **代码级差异只有 132 行**在 `ChatService` 一侧（CODE 口径 `diff` 的 `<` 侧
132 行；RAW 口径是 210 行）—— **不是 210 行**。引用「210」时必须写明是 RAW 口径。

### 10.2 可复现命令

```bash
C=app/src/main/java/heizige/kk/khatkit/app/core/service/ChatService.kt
M=app/src/main/java/heizige/kk/khatkit/app/feature/chat/ChatManager.kt

# A 覆盖率（diff 的 `<` 侧取反），两口径各跑一次
for f in raw code; do
  for x in "$C" "$M"; do
    if [ "$f" = code ]; then
      grep -vE '^\s*$|^\s*//|^\s*\*|^\s*/\*' "$x" > "/tmp/$f.$(basename "$x")"
    else cp "$x" "/tmp/$f.$(basename "$x")"; fi
  done
  n=$(wc -l < "/tmp/$f.ChatService.kt")
  lt=$(diff "/tmp/$f.ChatService.kt" "/tmp/$f.ChatManager.kt" | grep -c '^<')
  gt=$(diff "/tmp/$f.ChatService.kt" "/tmp/$f.ChatManager.kt" | grep -c '^>')
  echo "$f: |A|=$n diff_lt=$lt diff_gt=$gt A-cover=$(python3 -c "print(f'{($n-$lt)/$n*100:.2f}')")%"
done
```

⚠️ **本轮实测值**（`difflib.SequenceMatcher(autojunk=False)`，`2×matched/(|A|+|B|)`）：

| 口径 | \|A\| | \|B\| | `diff` `<` | `diff` `>` | A 覆盖率 | Jaccard |
|---|---:|---:|---:|---:|---:|---:|
| RAW | 1506 | 2362 | 210 | **1066** | **86.06%** | **67.06%** |
| CODE | 1197 | 1749 | **132** | 684 | **88.97%** | **72.30%** |

⇒ RAW `86.06%` = `1296/1506`；CODE `88.97%` = `1065/1197`。
⚠️ 旧记录的 **86.7%** 与实测 **86.06%** 有 0.6pp 落差 —— 引用时**一律用实测值
86.06%**，别再用 86.7%。（86.7% 那个值来源已不可考，疑为旧版行数或不同工具输出。）
