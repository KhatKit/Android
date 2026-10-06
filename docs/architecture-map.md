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

**`core/files` 为什么不提为兄弟**：`core/data/files/` 的 7 个文件全部只碰 DataStore 与
本地文件系统（`FilesManager` / `FileUtils` / `SkillManager` / `SkillPaths` /
`SkillFrontmatterParser` / `BuiltinSkills` / `CardMediaImporter`），是"数据的文件形态"。
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

**为什么记下来**：`core/service/ChatService.kt` 是活代码（`core/di/AppHiltModule.kt:51`
绑定，`feature/history/HistoryVM.kt:28` 注入），它的 `launchGenerationJob` 在
`keepAliveInBackground = true` 时会走到 `core.service` 那一份，而那一份在
`AndroidManifest.xml` 里**没有 `<service>` 声明**。按 Android 的组件解析规则，
`startForegroundService` 指向未声明组件应当抛异常。**⚠️ 待验（需真机）** —— 我没有
设备，无法确认这条路径是否真被走到、以及是否被上层 try/catch 兜住。

**两种可能的解释，本次都不做判定**：（1）`core/service/` 那份是 layer-first 时代
（上游 `me.rerere.rikkahub/service/` 有 7 个 kt）的残留，应删；
（2）反过来，manifest 漏了 `.core.service.ChatGenerationForegroundService` 的声明。
**两种改法都会动代码或 manifest，都超出本任务范围。**

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

| # | 事项 | 为什么待验 |
|---|---|---|
| 1 | `core/service/ChatGenerationForegroundService.kt` 指向未声明组件（§2.3） | 需真机跑一次后台生成 |
| 2 | `heroAnimation` 的 7 个调用点是否都在 `NavEntry.Content` 内（§5 §8.4-4） | 作用域由组合位置决定，静态不可判 |
| 3 | `predictivePopTransitionSpec` 跑 spring 的**实际手感影响**（§5 §8.4-2） | 需要人拿设备对比；改法本身超出记账范围 |