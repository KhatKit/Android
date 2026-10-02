# Bridge 能力扩展规格（交由 AI 实现）

> 状态：**已实现（2026-10-02）**。P0 / P1 / P2 全部任务落地，接口清单以
> [`script-api-reference.md`](script-api-reference.md) 为唯一权威。
> 本文保留为施工记录：设计取舍、决策点结论与「落地记录」见文末 §9。

## 0. 给 AI 的执行协议

1. **严格按阶段推进**（P0 → P1 → P2）。P0 是地基，没落地前不要碰 P1/P2。
2. 每个任务结束前跑该任务的「验收」小节的命令，全绿才进下一个任务。
3. **一次只做一个任务**。不要在同一批改动里顺手重构无关代码、不要升级依赖、不要格式化全仓。
4. 不许改的东西见 §1.3，踩了直接算失败。
5. 遇到 §7 里的决策点，不要自己拍板，停下来把问题列出来问人。
6. 提交前逐条走 §6 的自测清单。

## 1. 背景

### 1.1 现状

卡片脚本通过 bridge 调宿主能力。声明源是 `khatkit/src/main/java/heizige/kk/khatkit/bridge/Bridges.kt`，当前 11 个接口：`ToolBridge`（L0，文件/网络/剪贴板/OCR/压缩 22 个方法混在一起）、`UiBridge`、`WebBridge`、`DownloadBridge`、`StoreBridge`、`ImageToolboxBridge`（随依赖包注入）、`ShizukuBridge`（L1）、`AccessibilityBridge`（L1）、`RootBridge`（L2）。

### 1.2 要解决的问题

| 问题 | 证据 |
|---|---|
| `network.allow` 运行时完全不生效，只有 `card-validator` 做静态扫描 | `docs/lua-card-development.md:209` |
| `tool.*` 的文件方法没有作用域，卡片拿着全局「所有文件访问」权限可读写任意路径 | `AndroidToolBridge.readText` 只调 `requireSharedStorageAccess`，不做路径约束 |
| 审批粒度只到「桥」：`requires.bridges` 声明了整包注入，没有方法级白名单 | `CardManifest.Requires`（`CardManifest.kt:62-76`） |
| `ApprovalCategory.FILE_DELETE` / `APP_MANAGE` 已定义但从未使用 | `AutomationBus.kt:39-53` |
| 返回类型不一致：`listFiles` / `ocrBoxes` / `findImage` / `findColor` 返回 **JSON 字符串**让脚本自己 parse，`httpGet` 返回裸 body | `Bridges.kt:49, 75, 404, 417` |
| 卡片不能调模型，只能当「手」不能当「脑」 | `ai` 模块未被任何 bridge 暴露 |
| `ui.form` 的 `file_picker` / `dir_picker` 只是文本框；`button` 无回调 | `docs/lua-card-development.md:227-239` |
| 无全局卡片超时；`ui.form` 阻塞 300s | `docs/lua-card-development.md:398, 422` |

### 1.3 不许改的

- 已发布卡片的脚本行为。Hub 上已有卡片在用 `network.allow`、`tool.*`、`store.*` 的现有语义。**旧方法保留、标注废弃、不改行为**；新能力一律新增。
- `docs/script-api-reference.md` 的既有条目（只允许追加，不允许删改既有行）。
- `engine: command` 的白名单转义（`CardExecutor.renderCommand`）。
- 原生依赖包的 sha256 + ECDSA 签名校验链路（`khatkit/.../dependency/`）。
- `RustBridgeDispatcher` 的同步返回契约（见 §1.4，这是所有设计的前提）。
- 模块依赖方向：`khatkit` / `card-validator` / `khatkit-ui` 不依赖 `app`。任何需要 `app` 能力的新 bridge，一律「接口定义在 `khatkit`，实现在 `app`，由 `BridgeFactory.create` 注入」——和现有 `UiBridge` 完全一样的模式。

### 1.4 动手前必须知道的 8 条硬约束

这些是反射派发与文档同步测试决定的，改代码时踩中任何一条都会静默出错或测试失败。

1. **Rust 侧只有同步契约**。`RustBridgeDispatcher.dispatch(bridge, method, argsJson): String` 是同步阻塞、返回一段 JSON 字符串（`RustBridgeDispatcher.kt:13-22`）。**没有回调、没有流式、没有 Promise**。所以 `ai` / `net` 只能阻塞到拿到完整结果；增量输出做不到，需要就靠 `ui.progress` 汇报进度。
2. **按名字反射，不认 Kotlin 默认参数**。`ReflectiveInvoker.invoke` 用 `javaClass.methods.firstOrNull { it.name == methodName }`（`ReflectiveInvoker.kt:18-24`）。由此：
   - 脚本侧必须位置传全参数，可选值也要显式传（如 `net.get(url, {})`）。
   - **禁止同名重载**——`firstOrNull` 只会取到其中一个。
   - 缺参会被补成 `null` / `0` / `false` / `null`（`ReflectiveInvoker.kt:26-41`），不会抛错。
3. **参数类型只有 String / Int / Long / Double / Float / Boolean 会被转换**，其余原样透传，所以 `Map<String, Any?>` / `List<Map<String, Any?>>` 能直接收到 JSON 对象与数组。但**不会深转换**：`Map<String,String>` 收到含数字的 JSON 对象会在用的时候 `ClassCastException` → `{"__error":...}`。参数一律声明成 `Map<String, Any?>` / `List<Any?>`，在实现里自己做转换。
4. **异常不进脚本**，统一编码为 `{"__error": "中文说明"}`（`RustBridgeDispatcher.kt:19-21`）。中文错误文案是产品的一部分，仿照 `AndroidToolBridge` 现有风格（带「请在…里开启…」的可操作指引）。
5. **返回值自动 JSON 化**：对象 → Lua table / JS 对象，数组 → 列表，`String` → 字符串，`null` → `nil`。实现新接口时优先返回结构化 `Map` / `List`，别再返回「JSON 字符串让脚本 parse」。新增前先确认 `JsonValues.encode` 对 `null` 的编码行为并在文档里写清。
6. **`Bridges.kt` 是唯一声明源，且被文本正则解析**。`BridgeApiDocTest.declaredMethods` 用 `\n    fun (\w+)\s*\(` 抠方法名（`BridgeApiDocTest.kt:39-45`）。因此：接口必须写在 `Bridges.kt` 顶层、方法必须 4 空格缩进、接口之间用行首 `interface ` 分隔。**新接口还要在 `BridgeApiDocTest.kt:22-33` 的 `bridges` map 里登记**，否则文档守卫形同虚设。
7. **文档双向同步是硬门禁**。`BridgeApiDocTest` 两个用例：代码里每个方法必须在 `docs/script-api-reference.md` 里以 `` `bridge.method(` `` 出现；文档里每个 `` `x.y(` `` 都必须在代码里存在。命令：
   ```bash
   ./gradlew :khatkit:testDebugUnitTest --tests "heizige.kk.khatkit.bridge.BridgeApiDocTest"
   ```
8. **bridge 实现里阻塞是既有模式**。`AndroidToolBridge` 已经 import 并使用 `runBlocking`。所以在 bridge 内 `runBlocking` 等协程是允许的，但**必须确认调用线程不是主线程**（`CardExecutor` 在 `Dispatchers.Default` 上执行，见 `CardExecutor.kt:33-45`；新增实现时加一行日志断言线程名，别猜）。

---

## 2. 架构决策：BridgeContext + 装饰器

**所有 P0/P1 能力的地基，必须先做。**

问题：`BridgeFactory.create` 构造的 bridge 实现是**进程级单例**，构造时拿不到「现在是哪张卡片、它的 manifest 声明了什么」。没有这个上下文，就没法做 per-card 的网络域白名单、文件根目录、方法级审批。

**方案：在 `BridgeRegistry.inject` 里用装饰器包一层单例实现，把本次运行的上下文带进去。**

```
CardExecutor.executeScript(card, args)          ← 拿到 manifest
  └─ registry.inject(engine, manifest, script)  ← 签名变更点
       ├─ 构造 BridgeContext(cardName, quotaMb, networkAllow, fsRoots, approvalGate, deadline)
       ├─ for each required bridge:
       │    ScopedXBridge(context, delegate = 单例 impl)   ← Kotlin 接口委托
       │    engine.define(name, scoped)
       └─ engine.eval(...)
```

要点：

- 用 Kotlin 接口委托（`class ScopedNetBridge(private val ctx: BridgeContext, private val d: NetBridge) : NetBridge by d`）。委托类自己生成方法，`javaClass.methods` 能正常反射到，无需改 Rust 侧。
- `dispatch` 的准入顺序固定为：
  1. 超时检查（超过 deadline → `{"__error":"卡片运行超时，已终止"}`）
  2. 方法级权限策略（`permissions` 声明 → allow / deny / ask）
  3. `ask` 时过 `ApprovalGate`（拒绝 → `{"__error":"用户拒绝了操作：<中文>"}`）
  4. 反射调 delegate。
- 策略命中 `allow` 或已记住时，第 3 步是纯内存判断，不阻塞。
- **超时与取消统一在这里落地**：bridge 被拒绝或超时后，后续所有 dispatch 都返回 `{"__error":...}`，`ui.isCancelled()` 同时返回 `true`。

新增文件（`khatkit/src/main/java/heizige/kk/khatkit/bridge/`）：

```kotlin
/** 一次卡片运行的策略上下文，由 BridgeRegistry.inject 按 manifest 构造。 */
data class BridgeContext(
    val cardName: String,
    val engine: String,
    val quotaMb: Int,
    /** net 域白名单（小写 host，无端口）。空 = 禁止一切出网。 */
    val networkAllow: Set<String>,
    /** fs 可访问根目录（已 canonicalize）。空 = 只允许应用私有目录。 */
    val fsRoots: Set<String>,
    /** 方法级权限策略：key 形如 "fs.delete" / "net.get" / "ai.chat"。 */
    val permissions: Map<String, String>,
    /** 同步审批闸门；命中 ask 时调用。宿主未提供（桌面等）时按拒绝处理。 */
    val approvalGate: ApprovalGate?,
    /** 运行硬超时（epoch ms）；0 = 不限。 */
    val deadlineAt: Long,
)

/** 同步审批闸门。khatkit 不依赖 app，由 app 侧实现并注入。 */
fun interface ApprovalGate {
    /** @return true 放行；false 表示用户拒绝或不可审批。实现里必须能阻塞等待。 */
    fun request(title: String, detail: String, category: String): Boolean
}
```

`BridgeRegistry` 改动：`inject` 的 `required: Collection<String>` 参数换成 `manifest: CardManifest`（或一个 `BridgePolicy` 编译产物），`storeProvider` 的调用挪进装饰器；`availableBridges()` / `supports()` 保持按桥名过滤的语义不变（`HubFilters.byCapability` 依赖它）。

**验收**：不改任何对外行为，空跑现有卡片全部正常；`inject` 的新签名有单元测试覆盖（声明了未提供 bridge 时返回 false）。

---

## 3. manifest 新增 `permissions` 字段

`card-validator/src/main/java/heizige/kk/khatkit/card/CardManifest.kt`：

```kotlin
/** 方法级权限声明。缺省为空 = 全部按 bridge 级默认策略处理。 */
val permissions: Permissions = Permissions(),

@Serializable
data class Permissions(
    /** 方法白名单：key 为 "<bridge>.<method>"，value 为 "allow" / "ask" / "deny"。 */
    val methods: Map<String, String> = emptyMap(),
    /** fs 读白名单（路径前缀）。空 = 仅应用私有目录。 */
    val fsRead: List<String> = emptyList(),
    /** fs 写白名单（路径前缀）。空 = 仅应用私有目录。 */
    val fsWrite: List<String> = emptyList(),
    /** 卡片希望宿主执行的预检（如申请文件选择器返回 URI 授权）。留空忽略。 */
    val grants: List<String> = emptyList(),
)
```

**向后兼容**：`network.allow` 保持可用，实现时视为 `permissions.net` 的旧写法——把 `network.allow` 合并进 `networkAllow` 集合。两者同时出现时 `card-validator` 给 `PERMISSIONS_NETWORK_LEGACY` 警告而不是报错（Hub 上已有卡片在用）。

新增校验码（接进 `CardValidator`）：

| 码 | 触发 |
|---|---|
| `PERMISSION_METHOD_UNKNOWN` | `permissions.methods` 的 key 不是已注册的 `<bridge>.<method>` |
| `PERMISSION_VALUE_INVALID` | value 不是 `allow` / `ask` / `deny` |
| `PERMISSIONS_NETWORK_LEGACY` | 同时写了 `network.allow` 和 `permissions`（警告级） |
| `PERMISSION_UNDECLARED_METHOD` | `permissions.methods` 里的方法所属 bridge 不在 `requires.bridges` |

**文档同步**：`docs/lua-card-development.md` 补 `permissions` 字段说明；`script-api-reference.md` §2 的能力表加一列「权限粒度」。

---

## 4. 任务清单

### P0 — 地基（不做完后面全是白干）

#### T0.1 BridgeContext + 装饰器（§2）

改动文件：`khatkit/.../bridge/BridgeContext.kt`（新）、`BridgeContextDecorator.kt`（新）、`BridgeRegistry.kt`、`exec/CardExecutor.kt`。

#### T0.2 ApprovalGate 实现与 `AutomationBus` 扩展

`ApprovalCategory` 新增 `AI_INVOKE("ai_invoke")`（卡片调模型必须单独一类），`FILE_DELETE` / `APP_MANAGE` 正式启用。`AutomationBus.requestApproval` 已经是 `suspend` 且带 60s 超时和 mutex，直接在 app 侧包一层同步实现：

```kotlin
// app/.../core/data/ai/tools/ 或 app/.../feature/automation/
internal class BusApprovalGate(private val bus: AutomationBus) : ApprovalGate {
    override fun request(title: String, detail: String, category: String): Boolean {
        val cat = ApprovalCategory.fromId(category) ?: ApprovalCategory.CARD_RUN
        // 注意：必须在非主线程；超时 60s 后 requestApproval 内部按拒绝处理
        return runBlocking { bus.requestApproval(title, detail, cat) }
    }
}
```

`detail` 文案要带上「卡片名 + 方法 + 目标（域名或路径）」，这是用户唯一能判断该不该点允许的依据。

同步补齐：`PermissionChecklist` 的策略设置页要列出新增类别（对照 `app/.../core/ui/components/permission/PermissionChecklist.kt` 现有的分类渲染逻辑），以及 `values*/strings.xml` 的 `approval_category_*`。

改动文件：`app/.../automation/AutomationBus.kt`、`PermissionChecklist.kt`、`app/src/main/res/values*/strings.xml`。

#### T0.3 `net`（从 `tool.http*` 拆出，带强制域白名单 + SSE 缓冲）

```kotlin
interface NetBridge {
    fun get(url: String, headers: Map<String, Any?> = emptyMap()): String
    fun post(url: String, body: String, headers: Map<String, Any?> = emptyMap()): String
    fun put(url: String, body: String, headers: Map<String, Any?> = emptyMap()): String
    fun delete(url: String, headers: Map<String, Any?> = emptyMap()): String
    fun multipart(
        url: String,
        fields: Map<String, Any?>,
        fileField: String? = null,
        filePath: String? = null,
        headers: Map<String, Any?> = emptyMap(),
        saveBinary: Boolean = true,
    ): String

    /** 跟随 SSE / chunked 流直到结束，返回拼接后的文本。超过 timeoutSeconds 视为失败。 */
    fun streamText(url: String, method: String, body: String, headers: Map<String, Any?>, timeoutSeconds: Int = 60): String
    /** 下载二进制到 outputPath，返回文件路径。 */
    fun toFile(url: String, outputPath: String, headers: Map<String, Any?> = emptyMap()): String
    /** 响应元信息（不含 body）：{ status, headers, contentType, length } */
    fun head(url: String, headers: Map<String, Any?> = emptyMap()): Map<String, Any?>
}
```

实现要点（`khatkit/.../bridge/impl/AndroidNetBridge.kt` 新建，从 `AndroidToolBridge` 搬 `httpGet/Post/Multipart`）：

- **域校验在每次请求前做**：解析 URL host（小写、去端口）→ 命中 `context.networkAllow` 才放行；不命中抛 `SecurityException("访问被卡片 network 白名单拒绝：<host>…")`。
- 白名单支持前缀语义：`api.example.com` 放行 `api.example.com` 与其子域；`*` 单独放行全部（并在 manifest 校验里对 `*` 要求 `privilege = elevated`）。
- `streamText` 内部逐块 append，但**只能在结束时返回**（§1.4 约束 1）。实现时用 `ui.progress` 汇报字节进度，并在文档里写明「不增量返回」。
- `tool.httpGet/httpPost/httpMultipart` 改为 `@Deprecated` 并委托给 `net`，行为与错误文案保持一致，文档标注「已废弃，请改用 `net.`」。

改动文件：`Bridges.kt`、`bridge/impl/AndroidNetBridge.kt`（新）、`AndroidToolBridge.kt`、`BridgeFactory.kt`、`BridgeRegistry.kt`、`BridgeApiDocTest.kt`（登记新接口）、`docs/script-api-reference.md`（新增 §`net`，并在 §3 `tool` 表标注废弃）、`docs/lua-card-development.md`。

#### T0.4 `fs`（沙箱化文件组，取代 `tool.*` 文件方法）

```kotlin
interface FsBridge {
    fun read(path: String): String
    fun write(path: String, content: String, append: Boolean = false)
    fun exists(path: String): Boolean
    fun stat(path: String): Map<String, Any?>
    /** 结构化列表，取代 tool.listFiles 返回的 JSON 字符串。 */
    fun list(path: String, recursive: Boolean = false, limit: Int = 500): List<Map<String, Any?>>
    fun copy(src: String, dst: String)
    fun move(src: String, dst: String)
    fun mkdir(path: String)
    /** 递归删除，受 permissions.methods["fs.delete"] 与 ApprovalCategory.FILE_DELETE 约束。 */
    fun delete(path: String, recursive: Boolean = false): Boolean
    fun zip(paths: List<String>, output: String): String
    fun unzip(zipPath: String, outputDir: String): String
    fun readBase64(path: String): String
    fun saveBase64(data: String, outputPath: String): String
    fun openDir(path: String)
}
```

实现要点（`bridge/impl/ScopedFsBridge.kt`）：

- **路径归一化三连**：`File(path).canonicalPath` → 拒绝符号链接逃逸（比较 `canonicalPath` 与 `Files.readSymbolicLink` 结果不一致的项）→ 判断是否落在 `fsRoots` 之内。
- 默认根目录：`<filesDir>/cards/<cardName>/`。**未声明 `permissions.fsRead/fsWrite` 时不允许访问共享存储**，这是本次改动最重要的行为收紧。
- 需要共享存储时先过 `requireSharedStorageAccess`（`bridge/impl/AllFilesAccess.kt`），未授权仍抛带指引的中文错误。
- `delete` 走审批：`permissions.methods["fs.delete"]` 为 `ask` 时过 `ApprovalGate`（category `file_delete`）；`deny` 直接拒；未声明按 `ask`。
- `tool.readText/writeText/listFiles/copyPath/deletePath/mkdir/renamePath/zip/unzip/readBase64/saveBase64/openDir` 全部 `@Deprecated` 委托给 `fs`，保持行为。
  - **注意**：`tool.deletePath` 目前不审批，新旧路径行为会不一致。这是**有意为之**（收紧），要在 CHANGELOG 与文档里显式说明。

改动文件同 T0.3，另加 `card-validator/.../CardValidator.kt` + `CardManifest.kt`（`permissions` 字段）。

**验收**：
```bash
./gradlew :khatkit:testDebugUnitTest --tests "heizige.kk.khatkit.bridge.BridgeApiDocTest"
./gradlew :card-validator:test
./gradlew :khatkit:testDebugUnitTest   # 含新增的路径归一化单元测试
```
新增单测：canonicalize 逃逸（`/a/../../etc/passwd`）、符号链接、空白根目录只放私有目录、删除审批分支。

---

### P1 — 高价值能力

#### T1.1 `ai`（卡片调模型）

```kotlin
interface AiBridge {
    /**
     * 阻塞调用当前配置好的供应商，**非流式**（Rust 侧同步契约限制）。
     * @param imagePaths 多模态输入的图片路径
     * @return 模型输出文本
     */
    fun chat(
        prompt: String,
        system: String = "",
        provider: String = "",
        model: String = "",
        imagePaths: List<String> = emptyList(),
        maxTokens: Int = 0,
        temperature: Double = 0.0,
        timeoutSeconds: Int = 120,
    ): String

    /** 一次性补全（无 system）。 */
    fun complete(prompt: String, maxTokens: Int = 0): String
}
```

实现要点：

- 接口定义在 `Bridges.kt`（`khatkit`），**实现在 `app`**——复用 `app` 内已有的供应商调用入口（`me.rerere.ai` 那层的 provider/chat 调用；具体入口由实现者定位，`khatkit` 不能反向依赖 `app`），通过 `BridgeFactory.create(...)` 新增参数注入，与 `ui` 同一模式。
- 未配置任何供应商 → 抛中文错误引导去设置页。
- 每次调用默认走审批（category `ai_invoke`，`permissions.methods["ai.chat"]` 可改为 `allow`）。
- 计费上报：复用 `KhatKitTools` 里已有的 `hub().reportTool(...)`（`KhatKitTools.kt:400` 一带），保证卡片内的模型调用也进 Hub 统计。
- `ai.chat` 必须能被 `deadlineAt` 打断（用 `withTimeout` 包协程，别让模型超时把引擎线程永久占住）。

#### T1.2 `json`（统一结构化数据）

```kotlin
interface JsonBridge {
    /** 解码为 table / object；失败抛中文错误。 */
    fun decode(text: String): Any?
    /** value 为已编码的对象 / 数组 / 标量字符串。 */
    fun encode(value: Any?): String
    /** 简化 JSONPath：a.b[0].c；未命中返回 default。 */
    fun query(text: String, path: String, default: String? = null): String?
    fun merge(base: String, patch: String): String
    fun pluck(text: String, keys: List<String>): Map<String, Any?>
    fun pretty(text: String): String
}
```

- 实现时先确认 `JsonValues` 对 `null` 的编码并写进文档（§1.4 约束 5）。
- 不新增依赖，`JsonValues.json` 已够。
- 顺带修一致性：`ocrBoxes` / `findImage` / `findColor` 保持原样（不破兼容），但文档里明确标注「返回 JSON 字符串，需 `json.decode`」，并在示例里演示。

#### T1.3 `crypto`

```kotlin
interface CryptoBridge {
    fun sha256(text: String): String
    fun sha256File(path: String): String
    fun md5(text: String): String
    fun hmacSha256(text: String, key: String, encoding: String = "hex"): String
    fun uuid(): String
    fun randomInt(min: Int, max: Int): Int
    fun randomToken(bytes: Int = 32): String          // base64url，无填充
    fun urlEncode(text: String): String
    fun urlDecode(text: String): String
    fun base64(text: String): String
    fun base64Decode(text: String): String
    fun hexEncode(data: String): String
    fun hexDecode(text: String): String
    /** AES/CBC/PKCS7 + base64；key / iv 均按 UTF-8 取字节，key 长度 16/24/32。 */
    fun aesEncrypt(text: String, key: String, iv: String = ""): String
    fun aesDecrypt(text: String, key: String, iv: String = ""): String
}
```

纯 JVM 实现（`MessageDigest` / `Mac` / `Base64` / `SecretKeySpec`），不引依赖。`hexEncode` 收文本不收字节——如果卡片要哈希文件用 `sha256File`。

#### T1.4 `time` + `locale`

```kotlin
interface TimeBridge {
    fun now(): Long
    fun nowIso(): String                                   // UTC ISO-8601
    fun format(pattern: String, timestampMs: Long = 0, timeZone: String = ""): String
    fun parse(text: String, pattern: String = ""): Long     // pattern 空 = ISO-8601
    fun timeZone(): String
    fun locale(): String                                    // BCP-47
}
```

`pattern` 用 `SimpleDateFormat` 语义（`java.text`），文档里给 2 个例子。`format` 里 `timestampMs = 0` 表示「现在」。

#### T1.5 `system`

```kotlin
interface SystemBridge {
    fun openUrl(url: String): Boolean
    fun share(text: String, title: String = ""): Boolean
    fun toast(text: String)
    fun vibrate(durationMs: Int = 200)
    fun launchApp(packageName: String, activity: String = ""): Boolean
    fun postNotification(
        channelId: String,
        channelName: String,
        title: String,
        text: String,
        actions: List<String> = emptyList(),
    ): Boolean
    fun screenState(): String        // on | off | unlocked | locked
    fun battery(): Map<String, Any?> // { level, charging, temperature }
    fun storage(): Map<String, Any?> // { total, available }
    fun connectivity(): Map<String, Any?> // { online, type, metered }
}
```

- 实现在 `app`，通过 `BridgeFactory` 注入（需要 `Context` 与宿主设置）。
- `openUrl` 只允许 `http` / `https`，其余拒绝（防止 `file://` / `intent://` 越权）。
- `launchApp` 的 `packageName` 走 `permissions.methods["system.launchApp"]`，默认 `ask`（category `app_manage`）。
- `postNotification` 无 `POST_NOTIFICATIONS` 时返回 false 而不是抛错，并在文档里说明 `false` 的含义。

#### T1.6 `host`（诊断 + 超时）

```kotlin
interface HostBridge {
    fun info(): Map<String, Any?>      // { appVersion, osVersion, sdkInt, engine }
    fun card(): Map<String, Any?>      // { name, version, author, tags }
    /** 设备能力体检，卡片据此优雅降级。{ root, shizuku, accessibility, allFiles, overlay, notifications } */
    fun health(): Map<String, Any?>
    fun capabilities(): List<String>   // = BridgeRegistry.availableBridges()
    fun log(level: String, message: String)
    /** 设置本次运行硬超时；到点后所有 bridge 调用返回错误，ui.isCancelled() 变 true。 */
    fun setTimeout(ms: Int)
    fun elapsedMs(): Long
}
```

`setTimeout` 靠装饰器在 dispatch 前比对 `deadlineAt` 实现（§2 要点 4）。`health()` 是把「卡片拿到中文错误串后瞎猜」变成「主动问一句」，成本极低、收益高。

#### T1.7 `ui.pickMedia` + 表单选择器落地

```kotlin
// 追加到 UiBridge
/** 打开媒体选择器；返回已复制到应用缓存的文件路径列表（用户取消返回空列表）。 */
fun pickMedia(options: Map<String, Any?> = emptyMap()): List<String>
```
`options`：`{ "media": "image|video|any", "multiple": true, "max": 9 }`。

- 实现在 `app`，接到已有的 `KhatKitMediaPicker`（见 `docs/image-toolbox.md`）。
- **必须复制到 `cacheDir/cards/<name>/picked/`** 再返回路径——picker 给的 URI 在卡片运行结束后可能失效。
- 落地 `ui.form` 的 `file_picker` / `dir_picker`：改 `khatkit-ui/.../KhatKitForm.kt` 里目前的文本框分支（`:227-233` 一带），接到 `pickMedia` 与目录选择。`UiWidgetVocabulary.ALLOWED` 若新增控件类型要同步（card-validator 的 `UI_WIDGET_UNKNOWN` 会扫）。

改动文件同 T0.3。

---

### P2 — 中期（先确认决策点 §7.4）

#### T2.1 `store` v2：真 SQLite + FTS5 + 向量

现状 `dbQuery/dbInsert` 是 JSONL 文件（`bridge/impl/FileStoreBridge.kt`）。

```kotlin
// 追加到 StoreBridge
fun sql(query: String, args: List<Any?> = emptyList()): List<Map<String, Any?>>
fun embedInsert(table: String, rowId: String, text: String): Boolean
fun embedSearch(table: String, query: String, topK: Int = 5): List<Map<String, Any?>>
```

- **用 `androidx.sqlite` 的 `FrameworkSQLiteOpenHelperFactory`**，不要用裸 `android.database.sqlite`：前者是 Room 底层同一个引擎，FTS5 可用性已经被 `app/src/androidTest/.../DatabaseBackupTest.kt:65` 的 `CREATE VIRTUAL TABLE search USING fts5(...)` 验证过。若目标设备上 FTS5 建表仍失败，优雅降级到普通表 + `LIKE`，并在返回值里带一个能力位（`host.health().fts5`）。
- **保持 `dbQuery/dbInsert` 签名与行为不变**，新建表用 `store.sql` 自管。本节是「新增能力」，不是替换 store API。
- 每个卡片一个库文件：`databases/cards/<cardName>.db`。卸载卡片 = `deleteDatabase`；`store.quota_mb` 直接按文件大小校验。

##### 为什么不用 Room（决策记录，别改回去）

1. **Room 表达不了运行期表结构**。`@Entity` 是编译期 data class，而卡片要的是运行期 `CREATE TABLE`。用 Room 就只能退化成 `db.openHelper.writableDatabase.execSQL(...)`——注解处理器照付编译成本，SQL 一行还是手写。
2. **FTS5 本来就得手写 SQL**。本项目已有先例：`app/.../core/data/db/fts/MessageFtsManager.kt` 全是 `execSQL` + `simple_snippet(...)`。Room 的 `@Fts4` 映射 FTS4 且要求编译期实体，套不上 `tokenize='simple'` 这套。用 Room 省不下任何 SQL。
3. **爆炸半径**。Hub 上的卡片是第三方代码。共享用户的聊天库意味着恶意/buggy 卡片能建表、`DROP TABLE`、撑爆 WAL；而任何卡片改 schema 都要你发一个 app DB migration——第三方代码决定你的下一个 migration 版本号，`MigrationTestHelper` 那套测试直接失效。
4. **`khatkit` 模块刻意保持轻**，没有 Room 依赖（见 `khatkit/build.gradle.kts`：只有 coroutines / serialization / ktor / shizuku / mlkit / common）。加 Room 等于把 `room-runtime` + `room-compiler`(KSP) 塞进库模块，而且 `StoreBridge` 由 `BridgeFactory.create` 按卡片构造注入，要接 Room 就得把 app 的 DB handle 往下传——卡片引擎与聊天库耦合。

Room 继续留给 app 侧的固定 schema 实体（会话、助手、设置）。
- 向量表 + 暴力余弦相似度起步，规模上来再谈索引。
- **注意**：开 SQLite 会让卡片侧存储从「KV + JSONL」变成「可执行 SQL」，`sql()` 必须只对卡片自己的库文件开放，且 `store.quota_mb` 继续生效。

#### T2.2 `schedule`

18 种事件类型里已经有 `schedule`（`CardManifest.EVENT_SCHEDULE`，支持 `times` / `intervalMinutes` / `days` / `calendar`），卡片再自带定时器会与之重复。**倾向不做**，除非确认有 `events` 覆盖不到的场景。若做：

```kotlin
interface ScheduleBridge {
    fun every(intervalMinutes: Int, jobId: String = "", payloadJson: String = "{}"): String
    fun at(times: List<String>, days: List<Int>, jobId: String = ""): String
    fun cancel(jobId: String): Boolean
    fun list(): List<Map<String, Any?>>
}
```
基于 WorkManager，`jobId` 由卡片名 + 卡片内自定义 id 派生，防止跨卡片撞车。

#### T2.3 文档漂移修复（低成本，该做）

- `docs/lua-card-development.md:160,292,307` 写「11 种事件类型」，`docs/triggers.md:30-34` 只列 4 种，实际 `CardManifest.ALL_EVENT_TYPES` 有 18 种。全部对齐到 18 种。
- `requires.bins` / `requires.env`（`CardManifest.kt:69,70`）至今无人消费（`docs/lua-card-development.md:192`）。要么接线，要么标注为预留。
- `docs/dependency-system.md:259-266` 的 TODO（依赖 jar 不自动清理、依赖不能再声明依赖）→ 在 `dependency-system.md` 里补一节写明现状。

---

## 5. 通用实现规范

新增每个 bridge 都遵守：

1. 接口与 KDoc 写在 `Bridges.kt`，4 空格缩进，中文 KDoc，参数与返回语义写清（含 `null` / 空值 / 失败时的含义）。
2. 实现放 `bridge/impl/XxxBridge.kt`，构造参数保持最小（`Context` / `HttpClient` / `CoroutineScope` / `ApprovalGate`），不反向依赖 `app`。
3. 错误一律抛中文异常（消息里带可操作指引），由 `RustBridgeDispatcher` 统一编码。
4. 幂等与超时：任何可能长耗时的方法内部有超时；不吞异常。
5. 单测放 `khatkit/src/test/java/heizige/kk/khatkit/bridge/`（参考现有 `khatkit/src/test/java/heizige/kk/khatkit/card/` 的写法），至少覆盖参数边界与路径/域名白名单。
6. 文档两处同步：`docs/script-api-reference.md`（签名表）+ `docs/lua-card-development.md`（用法与陷阱）。漏一处 `BridgeApiDocTest` 就红。
7. `docs/script-api-reference.md` §2 的能力表同步更新（新增 bridge 行 + `tool`/`store` 的废弃标注）。

---

## 6. 提交前自测清单

```bash
# 文档双向同步门禁（最容易红的一条）
./gradlew :khatkit:testDebugUnitTest --tests "heizige.kk.khatkit.bridge.BridgeApiDocTest"

# 各模块单测（card-validator 是 kotlin-jvm 模块，用 test 而非 testDebugUnitTest）
./gradlew :khatkit:test
./gradlew :card-validator:test

# 构建 + lint
./gradlew assembleDebug
./gradlew lint
```

逐条确认：

- [ ] 新接口已登记进 `BridgeApiDocTest.kt` 的 `bridges` map
- [ ] `docs/script-api-reference.md` 已有新 bridge 的章节，且 §2 能力表更新
- [ ] 没有同名重载方法（§1.4 约束 2）
- [ ] 所有可选参数在文档示例里显式传参
- [ ] 新增方法有中文错误文案且带操作指引
- [ ] 新增 impl 里的 `runBlocking` / 阻塞调用已确认不在主线程
- [ ] 权限相关改动同步了 `values*/strings.xml`
- [ ] `CHANGELOG.md` 加了条目
- [ ] 没有夹带无关重构 / 依赖升级

---

## 7. 决策点（已拍板，结论见每条末尾）

1. **`tool` 的废弃策略** → **永久保留 + 标注废弃**，不设下线时间点。Hub 上有第三方卡片在用
   （`tool.http*` 已委托给 `net`，文档标注「已废弃，请改用 net」）。
2. **`permissions.methods` 的缺省值** → 按建议执行：常规方法默认放行以保兼容，
   高危项默认 `ask`。当前默认 `ask` 的是 `ai.chat` / `ai.complete`（同 `ai.chat` 策略）、
   `fs.delete`、`system.launchApp`；卡片写 `"ai.chat": "allow"` 即可免打扰。
3. **`ai.chat` 的凭据与计费**（2026-10-02 用户拍板）→ **只用用户已配置的供应商**。
   卡片不能自带 key，也不能指定用谁的 key；`provider` / `model` 留空即用当前聊天模型，
   只给 `model` 时在已配置供应商里按模型 ID 匹配。计费：模型调用 `price = 0`，
   每次调用以 `trigger = ai.chat` 上报 Hub 统计，卡片价格仍按卡片调用次数结算。
4. **`ScheduleBridge`** → **做**，但明确定位为「脚本运行期临时排的班」：走 WorkManager
   一次性任务链（**不精确**，省电/休眠会延迟），准点需求仍由 manifest 的 `schedule` 事件承担。
   `store.sql` 是**新增能力**，不替换 JSONL（`dbQuery` / `dbInsert` 行为不变）。
5. **`ui.form` 阻塞 300s** 与硬超时的关系 → **表单等待计入超时预算**。
   `host.setTimeout(ms)` 到点后，下一次 bridge 调用即返回「卡片运行超时，已终止」，
   `ui.isCancelled()` 同时为 true；表单本身不豁免（Rust 侧同步契约下没法中途打断）。

---

## 9. 落地记录（2026-10-02）

按任务逐条记录实际做法与偏离设计稿之处：

| 任务 | 提交 | 实际做法 |
|---|---|---|
| T1.7 媒体选择器 | `79462a00` | `MediaPickerHost`（khatkit）+ `KhatKitPickerHost`（app）。选中文件复制进 `cacheDir/cards/<card>/picked/`；表单 `file_picker` 走媒体选择器 / 系统文档选择器，`dir_picker` 走 `OpenDocumentTree` 并由 `TreeUriPaths` 把 tree URI 还原为绝对路径（次级卷用 `MediaStore.getExternalVolumeNames` 探测，API 29 以下只解析主存储）。 |
| T1.5 lint 收尾 | `6efb1d93` | `SystemBridgeImpl` 补 `VIBRATE` / `ACCESS_NETWORK_STATE` 权限声明，`:khatkit:lintDebug` 零错误。 |
| T1.1 `ai` | `5d5ce30b` | khatkit 侧 `CardAiBridge` 只做审批与预算裁剪，真正的模型调用由 app 的 `CardAiEngine` 完成（`khatkit` 不反向依赖 `app`）。顺带发现 `card-validator` 只登记了 `net` / `fs` 权限键，补上 `ai.*` / `system.launchApp` / `mediaPicker.pickMedia`。 |
| T2.3 文档漂移 | `6421dca7` | 事件类型按 `CardManifest.ALL_EVENT_TYPES` 对齐 18 种；`dependency-system.md` 的 TODO 改写为「现状与边界」。 |
| T2.1 `store.sql` | `a3dd81ba` | 引擎选 `androidx.sqlite` 的 `FrameworkSQLiteOpenHelperFactory`（新增 `androidx.sqlite:sqlite-framework` 依赖，无注解处理器）。`SqlGuard` 挡掉 `ATTACH` / `DETACH` / `VACUUM` / `LOAD_EXTENSION` 与多语句批处理——这是必须的：`ATTACH` 能挂上宿主聊天库，`VACUUM INTO` 能落任意路径。FTS5 能力位挂 `host.health().fts5`。 |
| T2.1 向量 | `dc833a42` | 索引表 `__khatkit_embeddings` 与 `store.sql` 同库，暴力余弦；`storeProvider` 签名从 `(cardName, quotaMb)` 改为 `StoreRequest` 才能拿到权限与审批。向量调用**按决策 2 复用 `ai.chat` 审批**（设计稿写「无审批」，此处按花钱能力从严处理）。 |
| T2.2 `schedule` | `67fa7df0` | WorkManager 一次性任务链而非 PeriodicWork（下限 15 分钟挡不住 `every(1)`）；卡片名绑定用 `CardBindable`，避免 `khatkit` 引用 app 的类；卸载卡片连带取消任务并 `deleteDatabase`。 |
| §2 要点 4 超时收口 | `2ab8e72c` | 设计稿要求 dispatch 前比对 deadline，之前没实现（`setTimeout` 写进没人读的字段）。改为 `RunDeadline` 挂在 `BridgeContext` 上，`RustBridgeDispatcher` 统一判定，`ScopedUiBridge.isCancelled()` 同步反映。 |

已知遗留：

- 动态依赖包（`imageToolbox` 等）仍只能走 `call` / `availableMethods`，未做类型化扩展。
- 决策点 5 的取舍副作用：设置了 `host.setTimeout` 的卡片，用户在 `ui.form` 上停留过久会
  直接超时失败。卡片作者应给表单交互留足预算。
- `tool.*` 文件方法只标了废弃、**没有委托给 `fs`**：委托会让 Hub 上已发布卡片的
  `tool.readText('/sdcard/…')` 直接失败（`fs` 有沙箱），与 §1.3 硬冲突。连带
  `tool.deletePath` 仍不审批。要收紧（让删除也走审批）是产品决定，见 §7。
- 方法级权限目前在各 bridge 实现内部判定（`ai.chat` / `store.embed*` / `fs.delete` /
  `system.launchApp`），没有做成 §2 要点 2 说的「dispatch 层统一准入」。行为正确，
  但新增敏感方法时要记得在实现里接审批。

构建与 lint（2026-10-02 另一轮清完，§6 门禁现在能跑通）：

- `:speech` 原本直接依赖本地 `.aar`，AGP 禁止 library 模块这么做 → 抽成 `.jar`，
  `./gradlew assembleDebug` 全量首次可用（`bb4d3d26`）。
- app 的 274 个 lint error 分三类清掉：i18n 220 个（39 个 `media_picker_*` 中文串错放在
  app 的 `values-zh`，搬回模块；`MissingTranslation` 在 `app/lint.xml` 降为 warning，
  翻译走 Crowdin，本地只补 `values/` + `values-zh/`）、真 bug 7 个（5 处权限检查藏在
  helper 里、`BgEffectPainter` 的 `RuntimeShader` 需要 API 33）、Composable 正确性 47 个
  （63 处 `LocalContext` 取文案改 `stringResource`、`Locale.getDefault()` 改
  `LocalConfiguration`、`ChatList` 的 Activity 改 `LocalActivity`）。

---

## 8. 附录：新增接口速查

| 接口 | 权限 | 审批类别 | 状态 |
|---|---|---|---|
| `net`（L0） | `permissions.net` / `network.allow` 域白名单 | 默认 `allow`；`net.*` 可配 `ask` | 待实现 |
| `fs`（L0） | `permissions.fsRead/fsWrite` 路径白名单 | `fs.delete` → `file_delete` | 待实现 |
| `ai`（L0 + 敏感） | 需 `privilege` 或显式 `ai.chat: allow` | `ai_invoke` | 待实现 |
| `json`（L0） | 无 | 无 | 待实现 |
| `crypto`（L0） | 无 | 无 | 待实现 |
| `time`（L0） | 无 | 无 | 待实现 |
| `system`（L0 + 部分敏感） | 无 | `system.launchApp` → `app_manage` | 待实现 |
| `host`（L0） | 无 | 无 | 待实现 |
| `ui.pickMedia`（L0） | 无 | 无 | 待实现 |
| `store.sql` / 向量（P2） | 沿用 `store.quota_mb` | 无 | 决策中 |
| `schedule`（P2） | 无 | 无 | 决策中 |