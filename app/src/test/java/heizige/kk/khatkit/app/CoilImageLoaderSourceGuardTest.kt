package heizige.kk.khatkit.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 全 app 的 Coil `ImageLoader` **只允许有一个定义点**，且那个定义点**不许在 Activity 里**。
 *
 * ## 这个 bug 是什么
 *
 * `connectedDebugAndroidTest` 全量跑，第 10 条左右整个 instrumentation 进程死掉：
 *
 * ```
 * java.lang.IllegalStateException: The singleton image loader has already been created...
 *   at coil3.SingletonImageLoader.setSafe(SingletonImageLoader.kt:41)
 *   at heizige.kk.khatkit.app.RouteActivity.onCreate$lambda$0$0(RouteActivity.kt:202)
 * ```
 *
 * 反编译 `coil3.SingletonImageLoader`（3.6.3）后，语义是这样的（`reference` 是进程级
 * `AtomicReference`，值可能是 `null` / `Factory` / `ImageLoader`）：
 *
 * ```kotlin
 * fun get(context: PlatformContext): ImageLoader {          // 所有 Coil 用法的唯一入口
 *     return reference.get() as? ImageLoader ?: newImageLoader(context)
 * }
 *
 * fun setSafe(factory: Factory) {                          // 即 setSingletonImageLoaderFactory
 *     val value = reference.get()
 *     if (value is ImageLoader && value.isDefault) {       // isDefault = 带内置默认标记
 *         error("The singleton image loader has already been created. ...")
 *     }
 *     reference.compareAndSet(value, factory)
 * }
 * ```
 *
 * 也就是：**进程里只要有一处 Coil 用法先跑过**（`AsyncImage` / `rememberAsyncImagePainter` /
 * `SubcomposeAsyncImage` … 都会走 `get()`），内置默认 loader 就被钉死在 `reference` 里，
 * 之后再 `setSafe` 必崩。而 `coil3.compose.setSingletonImageLoaderFactory` 是在**组合过程中
 * 同步**调 `setSafe` 的（`SingletonImageLoadersKt.setSingletonImageLoaderFactory`，
 * `singletonImageLoaders.kt:17`，字节码里没有 `SideEffect`/`LaunchedEffect` 兜底），
 * 所以「谁先跑」完全由进程内第一个碰到 Coil 的地方决定，跟这个 Activity 有多早起来无关。
 *
 * 崩溃的具体路径（同一进程内）：`NodeTreeSmokeTest` 用 `createComposeRule()` 渲染
 * `AsyncImage`（`NodeTreeSmokeTest.kt:66`）→ 默认 loader 建立；随后
 * `BrowserRuntimeTest` 的 `ActivityScenarioRule(RouteActivity::class.java)` 拉起
 * `RouteActivity.onCreate` → `setSingletonImageLoaderFactory` 当场抛，
 * 而它挂在 `super.onCreate()` 之后、`setContent` 装内容之前，整个进程死。
 *
 * `b5c5ebcb`（浏览器卡片化）没有碰 Coil，它是**加了 2 个 androidTest 类**才把执行顺序
 * 挪成这样、把这个一直存在的隐患顶出来的。
 *
 * ## 为什么用条件判断绕过是不对的
 *
 * `if (单例还没建) setSingletonImageLoaderFactory { ... }` 在**正常启动**下是对的，
 * 在**测试进程**里却是错的：那里先建的是内置默认 loader，于是自定义配置
 * （Ktor 网络栈 + GIF/SVG 解码）被静默跳过，图片加载在测试里跑的是另一套配置，
 * 而 `AsyncImage` 依然「不崩了」—— 把配置不一致换成了更难查的问题。
 * 正确做法是换一个**必然早于所有 `get()`** 的位置：`Application`。
 *
 * ## 现在的形状
 *
 * Coil 3 里 `ImageLoaderFactory` 已被 `SingletonImageLoader.Factory` 取代，
 * `KhatKitApp : Application(), SingletonImageLoader.Factory` 就是官方位置：
 * `newImageLoader()` 由 Coil 惰性调用一次，且 `get()` 在单例为空时的解析顺序是
 * 「已存 Factory → `applicationContext as? Factory` → 内置默认工厂」，
 * 所以 Application 实现 Factory 之后，进程内拿到的**永远是**这份自定义 loader。
 * Hilt 字段注入早于 `Application.onCreate`，而 `newImageLoader()` 惰性调用，必已就绪。
 *
 * ## 为什么扫源码文本而不是 Robolectric
 *
 * 仓库 `testImplementation` 只有 junit（见 `BottomSheetScrollSourceGuardTest` 的类注释），
 * 组合函数与进程级单例在 JVM 上跑不起来。而且这次的判据本来就是**结构性的**：
 * 「定义点有几处、在不在 Activity 里」——这正是崩溃的唯一变量。
 *
 * ## 这条规则覆盖不到的地方（诚实说明）
 *
 * - 扫不到 `settings.gradle.kts` 里 `includeBuild` 进来的 **Kedge / Khromia** 独立构建
 *   （同级目录，不在本仓）。它们目前不含 Coil loader 定义，但本测试看不见它们。
 * - 文本扫判不了「某个 `AsyncImage` 运行时会不会先跑」。它钉的是「晚于任何 `get()` 的
 *   设置点不许存在」，而 Activity 不是唯一晚于 `get()` 的位置（Service / Receiver /
 *   `Application` 之外的 provider 也可能先碰 Coil）。所以下面额外钉了
 *   「定义点必须在 Application 上」这条更强的正面事实，而不是只钉反面。
 */
class CoilImageLoaderSourceGuardTest {

    private companion object {
        /** 唯一允许持有 `ImageLoader` 定义的文件。 */
        const val APP_FILE = "app/src/main/java/heizige/kk/khatkit/app/KhatKitApp.kt"

        /** 唯一允许持有 Activity 的文件（本次崩溃点，单独钉住）。 */
        const val ROUTE_ACTIVITY_FILE = "app/src/main/java/heizige/kk/khatkit/app/RouteActivity.kt"

        /**
         * 「往进程级单例里塞 loader」的全部入口。`setSafe` 系列在第一次 `get()` 之后就可能抛，
         * `setUnsafe` / `reset` 则是绕过单例契约的强行改写，一律不许出现在业务源码里。
         *
         * 注意两件事（都是写这条护栏时被变异测试逼出来的）：
         *
         * 1. 用 `\b` 而不是 `(?<![\w.])`：写法可能是 `setSingletonImageLoaderFactory(...)`，
         *    也可能是全限定的 `coil3.compose.setSingletonImageLoaderFactory(...)`——
         *    负向后行断言会把后者整条漏掉。
         * 2. 后面跟的必须是 `(` **或 `{`**：`setSingletonImageLoaderFactory` 是
         *    `@Composable`，调用形式是 `setSingletonImageLoaderFactory { context -> ... }`
         *    —— 尾随 lambda，名字后面根本没有左括号。只认 `(` 的版本对「搬回 Activity」
         *    这个变异是绿的。
         */
        const val SETTER_PATTERN =
            """\b(?:setSingletonImageLoaderFactory|setSingletonImageLoader)\s*[\({]""" +
                """|\bSingletonImageLoader\s*\.\s*(?:setUnsafe|reset)\s*[\({]"""

        /** 实现 Coil 工厂接口（`ImageLoaderFactory` 的 Coil 3 替代品）。 */
        const val FACTORY_IMPL_PATTERN = """\bSingletonImageLoader\s*\.\s*Factory\b"""

        /** 直接 `new` 一个 loader：`ImageLoader.Builder(...)`。 */
        const val BUILDER_PATTERN = """\bImageLoader\s*\.\s*Builder\s*\("""

        /** 三种「碰了单例定义」的手段合起来，用于「一处都不许多」类的断言。 */
        const val ANY_DEFINITION_PATTERN = "$SETTER_PATTERN|$FACTORY_IMPL_PATTERN|$BUILDER_PATTERN"

        val SETTER_REGEX = Regex(SETTER_PATTERN)
        val FACTORY_IMPL_REGEX = Regex(FACTORY_IMPL_PATTERN)
        val BUILDER_REGEX = Regex(BUILDER_PATTERN)
        val ANY_DEFINITION_REGEX = Regex(ANY_DEFINITION_PATTERN)

        /** 任何 Activity 子类声明：`class X : ComponentActivity()` / `: AppCompatActivity(` … */
        val ACTIVITY_DECL_REGEX =
            Regex("""(?m)^\s*(?:@\w[\w.]*(?:\([^)]*\))?\s*)*(?:public\s+|internal\s+|private\s+)?class\s+\w+[^\n:{(]*:\s*[^\n{]*?(?:\b\w*Activity|ComponentActivity)\s*\(""")

        /** 每个模块要扫的源码集：main 是定义可能出现的地方，test/androidTest 是「别在测试里另配一套」。 */
        val SOURCE_SETS = listOf("src/main/java", "src/test/java", "src/androidTest/java")

        /** 把注释与字符串字面量替换成等长空白（保留换行），避免注释里的字样被当成代码。 */
        fun stripCommentsAndStrings(src: String): String {
            val out = StringBuilder(src)
            val n = src.length
            fun blank(from: Int, to: Int) {
                for (k in from until minOf(to, n)) if (src[k] != '\n') out.setCharAt(k, ' ')
            }
            var i = 0
            while (i < n) {
                when {
                    src.startsWith("//", i) -> {
                        val end = src.indexOf('\n', i).let { if (it < 0) n else it }
                        blank(i, end); i = end
                    }
                    src.startsWith("/*", i) -> {
                        var depth = 1
                        var j = i + 2
                        while (j < n && depth > 0) {
                            when {
                                src.startsWith("/*", j) -> { depth++; j += 2 }
                                src.startsWith("*/", j) -> { depth--; j += 2 }
                                else -> j++
                            }
                        }
                        blank(i, j); i = j
                    }
                    src.startsWith("\"\"\"", i) -> {
                        val end = src.indexOf("\"\"\"", i + 3).let { if (it < 0) n else it + 3 }
                        blank(i, end); i = end
                    }
                    src[i] == '"' -> {
                        var j = i + 1
                        while (j < n) {
                            when {
                                src[j] == '\\' -> j += 2
                                src[j] == '"' -> { j++; break }
                                src[j] == '\n' -> break
                                else -> j++
                            }
                        }
                        blank(i, j); i = j
                    }
                    else -> i++
                }
            }
            return out.toString()
        }

        data class Hit(val file: String, val line: Int, val token: String)

        /** 所有 Kotlin 源码文件（app 模块自身 + 所有兄弟模块的 main/test/androidTest）。 */
        fun allSources(): List<File> {
            val root = repoRoot()
            // app 模块自身 + 所有同级模块（以「有 src/main/java」为准）。
            val moduleDirs = buildList {
                add(root)
                root.listFiles()
                    ?.filter { it.isDirectory && File(it, "src/main/java").isDirectory }
                    ?.sortedBy { it.name }
                    ?.forEach { add(it) }
            }
            val files = mutableListOf<File>()
            for (module in moduleDirs) {
                for (set in SOURCE_SETS) {
                    val src = File(module, set)
                    if (!src.isDirectory) continue
                    src.walkTopDown()
                        .filter { it.isFile && it.extension == "kt" }
                        .forEach { files += it.toPath().normalize().toFile() }
                }
            }
            // app 模块会以 root 和 root/app 两种身份各命中一次，按绝对路径去重。
            return files.distinct().sortedBy { it.path }
        }

        /** 仓库根（测试的工作目录是 `app/` 模块目录，所以根是它的上一级）。 */
        fun repoRoot(): File = File("").absoluteFile.parentFile ?: File("..")

        /** 归一成仓库相对路径，保证断言消息稳定。 */
        fun repoRelative(file: File): String =
            file.toPath().normalize().toAbsolutePath()
                .let { p ->
                    val base = repoRoot().toPath().toAbsolutePath().normalize()
                    p.startsWith(base).let { if (it) base.relativize(p) else p }
                }
                .toString()
                .replace('\\', '/')

        fun hits(pattern: Regex, sources: List<File> = allSources()): List<Hit> {
            val out = mutableListOf<Hit>()
            for (file in sources) {
                val clean = stripCommentsAndStrings(file.readText())
                for (m in pattern.findAll(clean)) {
                    out += Hit(
                        file = repoRelative(file),
                        line = clean.substring(0, m.range.first).count { ch -> ch == '\n' } + 1,
                        token = m.value.trim(),
                    )
                }
            }
            return out
        }

        fun locate(suffix: String): File = allSources().firstOrNull { repoRelative(it).endsWith(suffix) }
            ?: error("没找到 $suffix（扫描范围失效？）")

        fun describe(list: List<Hit>): String =
            list.joinToString("\n") { "  ${it.file}:${it.line}  ${it.token}" }.ifEmpty { "  （空）" }
    }

    /**
     * 主规则：全仓**不允许**再出现「往 Coil 进程级单例里塞 loader」的调用。
     *
     * `setSingletonImageLoaderFactory` 的语义是「第一次 `get()` 之前设，之后设就抛」，
     * 而它自己没法保证这一点，所以除了 Coil 自己的封装，任何业务代码都不该直接调。
     */
    @Test
    fun noSourceConfiguresTheSingletonImageLoaderLate() {
        val found = hits(SETTER_REGEX)
        assertEquals(
            buildString {
                appendLine("以下位置在往 Coil 进程级单例里塞 ImageLoader：")
                appendLine(describe(found))
                appendLine("setSingletonImageLoaderFactory/setSingletonImageLoader 走的是")
                appendLine("coil3.SingletonImageLoader.setSafe，只要进程里有任何一处 Coil 用法先跑过")
                appendLine("（AsyncImage / rememberAsyncImagePainter / SubcomposeAsyncImage），它就会抛")
                appendLine("IllegalStateException: The singleton image loader has already been created...，")
                appendLine("而它是在组合过程中同步调的，来不及补救。")
                appendLine("自定义 loader 只有一个正确位置：KhatKitApp : SingletonImageLoader.Factory。")
            },
            0,
            found.size,
        )
    }

    /**
     * 反面事实：定义点不得出现在任何 Activity 文件里。
     *
     * 这条比上一条更贴近崩溃现场（`RouteActivity.kt:202`），且能在「有人把
     * `setSingletonImageLoaderFactory` 换个名字/换套封装绕过去」时继续兜住。
     */
    @Test
    fun noActivityDeclaresAClassThatConfiguresImageLoading() {
        val offenders = hits(ANY_DEFINITION_REGEX)
            .filter { hit ->
                val text = stripCommentsAndStrings(locate(hit.file).readText())
                ACTIVITY_DECL_REGEX.containsMatchIn(text)
            }

        assertEquals(
            buildString {
                appendLine("以下 Activity 文件里出现了 Coil ImageLoader 的配置/定义：")
                appendLine(describe(offenders))
                appendLine("进程级单例的配置必须早于所有 Coil 用法，而 Activity 的 onCreate 不具备这个保证")
                appendLine("（同进程的任意测试/服务/卡片渲染都可能先把内置默认 loader 建出来）。")
            },
            0,
            offenders.size,
        )
    }

    /**
     * 正面事实：定义点有且只有一处，且就在 `KhatKitApp`（Application）上。
     *
     * 和「不许有第二处」互补：只钉反面的话，把定义删干净会让本测试恒绿，
     * 那样自定义 loader（Ktor 网络栈 + GIF/SVG）就静默退化成内置默认那套了。
     */
    @Test
    fun theOnlyImageLoaderDefinitionLivesOnTheApplication() {
        val definitions = hits(BUILDER_REGEX) + hits(FACTORY_IMPL_REGEX)

        assertEquals(
            buildString {
                appendLine("全仓应该只有一处 ImageLoader 定义（ImageLoader.Builder / SingletonImageLoader.Factory），")
                appendLine("实际找到 ${definitions.size} 处：")
                appendLine(describe(definitions))
            },
            2, // KhatKitApp.kt 里各一次：`class ... : SingletonImageLoader.Factory` + `ImageLoader.Builder(`
            definitions.size,
        )

        val files = definitions.map { it.file }.distinct()
        assertEquals(
            "ImageLoader 的定义必须集中在同一个文件里，实际散落在：${files.joinToString()}",
            listOf(APP_FILE),
            files,
        )

        val app = stripCommentsAndStrings(locate(APP_FILE).readText())
        assertTrue(
            "$APP_FILE 必须实现 SingletonImageLoader.Factory（Coil 3 里 ImageLoaderFactory 的替代品），" +
                "这样 Coil 的 get() 在单例为空时会走 applicationContext 这条路拿到自定义 loader",
            Regex("""class\s+KhatKitApp[^\n:{(]*:\s*[^\n{]*\bApplication\s*\(\s*\)[^\n{]*\bSingletonImageLoader\s*\.\s*Factory""")
                .containsMatchIn(app),
        )
        assertTrue(
            "$APP_FILE 必须 override newImageLoader（Coil 通过它惰性建 loader）",
            Regex("""override\s+fun\s+newImageLoader\s*\(""").containsMatchIn(app),
        )
    }

    /**
     * 定点回归：崩溃点那个文件本身不许再碰 Coil 单例，且要留一条说明指向真正的定义点。
     *
     * 为什么单独钉：主规则是「全仓计数」，重构时很容易把它挪个地方而计数不变。
     * 这条把「崩溃现场的那个文件」和「真正的定义点」直接绑在一起。
     */
    @Test
    fun routeActivityDoesNotTouchTheCoilSingleton() {
        val route = locate(ROUTE_ACTIVITY_FILE)
        val clean = stripCommentsAndStrings(route.readText())

        assertTrue(
            "没找到 $ROUTE_ACTIVITY_FILE，扫描范围失效了",
            ACTIVITY_DECL_REGEX.containsMatchIn(clean),
        )
        val routeHits = hits(ANY_DEFINITION_REGEX, listOf(route))
        assertEquals(
            "$ROUTE_ACTIVITY_FILE 不得再出现 Coil 单例配置：" + describe(routeHits),
            0,
            routeHits.size,
        )
        assertTrue(
            "$ROUTE_ACTIVITY_FILE 应留一条注释说明定义点在 KhatKitApp，" +
                "避免后来者以为这里漏配了又把 setSingletonImageLoaderFactory 加回来",
            route.readText().contains("KhatKitApp"),
        )
    }

    /**
     * 迁移不能丢配置：`newImageLoader` 必须保留搬家前那份组件清单。
     *
     * 这条防的是「把定义搬去 Application 时手滑简化」，那不会崩，但远程图 / 动图 / SVG
     * 会静默退化成加载失败。
     */
    @Test
    fun applicationLoaderKeepsEveryComponentTheActivityLoaderHad() {
        val app = locate(APP_FILE).readText()
        for (required in listOf(
            ".crossfade(true)",
            "KtorNetworkFetcherFactory(",
            "AnimatedImageDecoder.Factory()",
            "GifDecoder.Factory()",
            "SvgDecoder.Factory(scaleToDensity = true)",
            "ktorHttpClient",
        )) {
            assertTrue(
                "$APP_FILE 的 newImageLoader 丢了搬家前的配置 `$required`——" +
                    "自定义 loader 被简化不会崩，但远程图/动图/SVG 会静默加载失败",
                app.contains(required),
            )
        }
        assertTrue(
            "KhatKitApp 必须注入 ktorHttpClient 并在 newImageLoader 里复用（Hilt 字段注入早于 onCreate，" +
                "而 newImageLoader 是惰性调用，必已就绪）",
            Regex("""lateinit\s+var\s+ktorHttpClient\s*:\s*HttpClient""").containsMatchIn(app),
        )
        assertTrue(
            "选 GIF 解码器仍需按 SDK_INT 分支（P 以上用 AnimatedImageDecoder）",
            Regex("""Build\.VERSION\.SDK_INT\s*>=\s*Build\.VERSION_CODES\.P""").containsMatchIn(app),
        )
    }

    /**
     * 护栏自身不能腐化：把「谁先创建了 Coil 单例」这个事实钉住。
     *
     * `NodeTreeSmokeTest` 用 `createComposeRule()`（不带 Activity）在同一进程里渲染
     * `AsyncImage`，这就是默认 loader 的建立者，也是本崩溃的触发条件。
     * 万一哪天它不再用 `AsyncImage`，本护栏的因果链描述就失效了 —— 这里让它变成可执行的断言。
     */
    @Test
    fun theProcessWideDefaultLoaderIsStillCreatedByAComposeTestBeforeAnyActivity() {
        val smoke = allSources().firstOrNull {
            repoRelative(it).endsWith("app/src/androidTest/java/heizige/kk/khatkit/app/NodeTreeSmokeTest.kt")
        } ?: error("没找到 NodeTreeSmokeTest.kt，扫描范围失效了")

        val text = smoke.readText()
        assertTrue(
            "NodeTreeSmokeTest 必须仍然用 createComposeRule（不带 Activity）渲染 AsyncImage —— " +
                "它是同进程里第一个建立 Coil 内置默认 loader 的地方，也就是 RouteActivity 之前那次 setSafe 的对象",
            text.contains("createComposeRule()") && Regex("""\bAsyncImage\s*\(""").containsMatchIn(text),
        )

        val browserTest = allSources().firstOrNull {
            repoRelative(it).endsWith("app/src/androidTest/java/heizige/kk/khatkit/app/core/data/browser/BrowserRuntimeTest.kt")
        } ?: error("没找到 BrowserRuntimeTest.kt，扫描范围失效了")
        assertTrue(
            "BrowserRuntimeTest 必须仍然用 ActivityScenarioRule(RouteActivity) —— " +
                "它就是 b5c5ebcb 加进来、把执行顺序挪到崩溃点之后的那条",
            Regex("""ActivityScenarioRule\(\s*RouteActivity\s*::\s*class\s*\.\s*java\s*\)""")
                .containsMatchIn(browserTest.readText()),
        )
    }
}