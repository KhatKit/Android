package heizige.kk.khatkit.hub

import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.card.CardParser
import heizige.kk.khatkit.card.Semver
import heizige.kk.khatkit.engine.EngineFactory
import heizige.kk.khatkit.engine.EngineKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.ZipInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** 已落地的卡片：manifest + 脚本正文。 */
data class LoadedCard(
    val manifest: CardManifest,
    val dir: File,
    val scriptText: String,
    val engineKind: EngineKind,
    val wasmBytes: ByteArray? = null,
    val wasmManifestJson: String? = null,
    val widgetExecution: Boolean = false,
    val bridgeSubset: Set<String>? = null,
)

/**
 * 本地卡片缓存：下载 zip -> 校验 -> 解压到 cacheDir/cards/<name>/<version>/。
 */
class CardCache(private val rootDir: File) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val orderLock = Any()

    fun cardDir(name: String, version: String): File {
        require(name.matches(Regex("[a-z][a-z0-9_-]{0,63}"))) { "CARD_NAME_INVALID" }
        require(version.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,127}"))) { "CARD_VERSION_INVALID" }
        return File(rootDir, "cards/$name/$version")
    }

    fun libDir(name: String, version: String): File = File(rootDir, "libs/$name/$version")

    fun isCached(entry: CardIndexEntry): Boolean =
        File(cardDir(entry.name, entry.version), "card.json").exists()

    /** 按需下载并解压；已缓存且非强制更新则直接返回。 */
    suspend fun ensure(
        entry: CardIndexEntry,
        hub: HubClient,
        force: Boolean = false,
    ): File = withContext(Dispatchers.IO) {
        locks.getOrPut(entry.name) { Mutex() }.withLock {
        val dir = cardDir(entry.name, entry.version)
        if (!force && File(dir, "card.json").exists()) {
            load(dir)
            return@withLock dir
        }

        val downloadDir = File(rootDir, "downloads").apply { mkdirs() }
        val zip = hub.downloadCard(entry, downloadDir)
        val stagingRoot = File(rootDir, "staging").apply { mkdirs() }
        val stagingDir = Files.createTempDirectory(stagingRoot.toPath(), "card-").toFile()
        val backup = File(stagingRoot, "backup-${java.util.UUID.randomUUID()}")
        try {
            unzip(zip, stagingDir)
            if (!File(stagingDir, "card.json").isFile && wasmRuntimeOf(stagingDir) == "wasm") {
                File(stagingDir, "manifest.json").copyTo(File(stagingDir, "card.json"))
            }
            val loaded = load(stagingDir)
            require(loaded.manifest.name == entry.name && loaded.manifest.version == entry.version) {
                "CARD_IDENTITY_MISMATCH"
            }
            dir.parentFile?.mkdirs()
            if (dir.exists()) Files.move(dir.toPath(), backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
            try {
                Files.move(stagingDir.toPath(), dir.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (error: Throwable) {
                if (backup.exists()) Files.move(backup.toPath(), dir.toPath(), StandardCopyOption.ATOMIC_MOVE)
                throw error
            }
        } finally {
            zip.delete()
            stagingDir.deleteRecursively()
            // On a failed rollback retain the backup for recovery.
            if (dir.exists()) backup.deleteRecursively()
        }
        // 同一卡片只保留当前版本，避免旧版本被重复加载成重复 tool
        dir.parentFile?.listFiles()?.forEach { sibling ->
            if (sibling.isDirectory && sibling.name != dir.name) sibling.deleteRecursively()
        }
        // 只有真正装进来的才记顺序；already-cached 走上面的早退分支，由 installOrder() 补记录。
        recordInstall(entry.name)
        dir
        }
    }

    /** 卸载卡片：删掉它的所有版本目录（其他卡片不受影响）。 */
    fun uninstall(name: String): Boolean = runBlocking {
        val cardRoot = cardDir(name, "1").parentFile!!
        locks.getOrPut(name) { Mutex() }.withLock {
        val removed = if (cardRoot.exists()) cardRoot.deleteRecursively() else false
        // 记录表里也要摘掉这个名字，否则重装会继承旧位置、记录表也会无限长。
        if (removed) synchronized(orderLock) { writeOrder(readOrder().filterNot { it == name }) }
        removed
        }
    }

    /**
     * 已安装卡片及其版本，用于市场页判断"更新"和去重加载。
     * 目录里若残留多个版本，取版本号最高的那个。
     */
    fun installedVersions(): Map<String, String> {
        val cardsRoot = File(rootDir, "cards")
        if (!cardsRoot.exists()) return emptyMap()
        return cardsRoot.listFiles().orEmpty().mapNotNull { cardDir ->
            val versionDir = cardDir.listFiles().orEmpty()
                .filter { File(it, "card.json").exists() }
                .maxByOrNull { Semver.parse(it.name) ?: Semver.Version(0, 0, 0) }
                ?: return@mapNotNull null
            cardDir.name to versionDir.name
        }.toMap()
    }

    /**
     * 已安装卡片的「添加顺序」（名字，从早到晚）。
     *
     * 顺序写在 `installed-order.txt`：**安装时追加、更新时不动**。目录列举顺序在
     * ext4 上是哈希序，卡片更新一次换个 inode 就会在欢迎页里跳位，所以不能靠
     * [installedVersions] 的 map 顺序当顺序用。
     *
     * 本次改动之前装过的卡片没有记录，按卡片目录的修改时间补一次（首次安装和
     * 版本升级都会刷新这个时间），补完立刻写回；之后新安装的走 [recordInstall]。
     */
    fun installOrder(): List<String> = synchronized(orderLock) {
        val recorded = readOrder()
        val missing = installedVersions().keys
            .filterNot { it in recorded }
            .sortedBy { name -> File(rootDir, "cards/$name").lastModified() }
        if (missing.isEmpty()) return@synchronized recorded
        (recorded + missing).also(::writeOrder)
    }

    /** 记录一次成功安装；同一个名字（= 更新）保持原来的位置。 */
    fun recordInstall(name: String) = synchronized(orderLock) {
        val recorded = readOrder()
        if (name in recorded) return@synchronized
        writeOrder(recorded + name)
    }

    /** 添加顺序的落盘位置刻意放在 `cards/` 外面：放里面会被 [installedVersions] 当卡片目录扫。 */
    private fun orderFile(): File = File(rootDir, "installed-order.txt")

    private fun readOrder(): List<String> = runCatching {
        orderFile().takeIf { it.isFile }?.readLines().orEmpty()
            .map(String::trim).filter(String::isNotEmpty).distinct()
    }.getOrDefault(emptyList())

    private fun writeOrder(names: List<String>) {
        runCatching { orderFile().writeText(names.joinToString("\n")) }
    }

    /**
     * 已安装卡片：解析成功的那批，**按添加顺序**（[installOrder]）排好。
     *
     * 排序放在 cache 里而不是各个调用点，是因为顺序的事实来源（`installed-order.txt` +
     * 目录 mtime 兜底）也在这里；两处各写一遍迟早会漂。调用方要的就是「装了什么、按什么顺序」。
     */
    fun installedCards(): List<LoadedCard> {
        val order = installOrder().withIndex().associate { (index, name) -> name to index }
        return installedVersions().mapNotNull { (name, version) ->
            runCatching { load(cardDir(name, version)) }.getOrNull()
        }.sortedWith(compareBy({ order[it.manifest.name] ?: Int.MAX_VALUE }, { it.manifest.name }))
    }

    /** 读取本地 card.json + 脚本，交给引擎执行。 */
    fun load(dir: File): LoadedCard {
        val manifestFile = File(dir, "card.json")
        require(manifestFile.exists()) { "card.json 不存在：$dir" }
        val parsed = CardParser.parse(manifestFile.readText()).getOrThrow()
        val abiFile = File(dir, "manifest.json")
        val abiText = abiFile.takeIf { it.isFile }?.readText()
        val abi = abiText?.let { CardParser.parse(it).getOrThrow() }
        if (abi?.isWasm == true) {
            require(parsed.name == abi.name && parsed.version == abi.version) { "WASM_MANIFEST_IDENTITY" }
            require(abi.abiVersion == 1) { "WASM_ABI_UNSUPPORTED" }
        }
        val manifest = if (abi?.isWasm == true) abi.copy(engine = "wasm") else parsed
        val hasLua = manifest.entry.lua?.let { File(dir, it).exists() } == true
        val hasJs = manifest.entry.js?.let { File(dir, it).exists() } == true
        // WASM 卡片用 ABI 的 manifest.json（runtime=wasm + abi_version + limits）判定；
        // card.json 仍供 Kotlin 侧元数据，两者并存。
        val kind = EngineFactory.resolve(manifest.engine, hasLua, hasJs, wasmRuntimeOf(dir))
        fun safeEntry(path: String): File {
            val file = File(dir, path).canonicalFile
            require(!File(path).isAbsolute && '\\' !in path &&
                file.path.startsWith(dir.canonicalPath + File.separator)) { "CARD_ENTRY_PATH" }
            require(file.isFile) { "CARD_SCRIPT_MISSING: $path" }
            return file
        }
        val scriptFile = when (kind) {
            EngineKind.LUA -> safeEntry(manifest.entry.lua ?: "main.lua")
            EngineKind.JS -> safeEntry(manifest.entry.js ?: "main.js")
            EngineKind.WASM, EngineKind.COMMAND -> null
        }
        val scriptText = scriptFile?.takeIf { it.exists() }?.readText().orEmpty()
        val wasm = if (kind == EngineKind.WASM) safeEntry("card.wasm").readBytes() else null
        if (wasm != null) {
            require(abiText != null && manifest.abiVersion == 1) { "WASM_MANIFEST_MISSING" }
            require(wasm.size >= 8 && wasm.take(8) == listOf<Byte>(0, 97, 115, 109, 1, 0, 0, 0)) { "WASM_INVALID" }
        }
        return LoadedCard(manifest, dir, scriptText, kind, wasm, abiText)
    }

    /** 读 manifest.json 的 runtime 字段；不存在或解析失败时按 Lua 卡处理。 */
    private fun wasmRuntimeOf(dir: File): String? {
        val abiManifest = File(dir, "manifest.json")
        if (!abiManifest.isFile) return null
        return runCatching {
            val element = Json.parseToJsonElement(abiManifest.readText())
            (element as? JsonObject)?.get("runtime")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    fun loadWidget(card: LoadedCard): LoadedCard {
        val widget = requireNotNull(card.manifest.widget) { "WIDGET_UNAVAILABLE" }
        require(!card.manifest.isCommand && "ui" in card.manifest.requiredBridges) { "WIDGET_ENGINE" }
        fun script(path: String?): File? = path?.let {
            require(it.isNotBlank() && !File(it).isAbsolute && '\\' !in it) { "WIDGET_PATH" }
            val file = File(card.dir, it).canonicalFile
            require(file.path.startsWith(card.dir.canonicalPath + File.separator)) { "WIDGET_PATH" }
            require(file != card.manifest.entry.lua?.let { main -> File(card.dir, main).canonicalFile } &&
                file != card.manifest.entry.js?.let { main -> File(card.dir, main).canonicalFile }) { "WIDGET_MAIN_ENTRY" }
            file.takeIf(File::isFile)
        }
        if (card.engineKind == EngineKind.WASM) {
            require(widget.entry.wasm == "card.wasm") { "WIDGET_WASM_ENTRY" }
            val bridges = widget.bridges ?: card.manifest.requiredBridges.toList()
            require("ui" in bridges && card.manifest.requiredBridges.containsAll(bridges)) { "WIDGET_BRIDGES" }
            val manifest = card.manifest.copy(requires = card.manifest.requires.copy(bridges = bridges))
            return card.copy(manifest = manifest, widgetExecution = true, bridgeSubset = bridges.toSet())
        }
        val lua = script(widget.entry.lua)
        val js = script(widget.entry.js)
        val kind = EngineFactory.resolve(card.manifest.engine, lua != null, js != null)
        val file = requireNotNull(if (kind == EngineKind.LUA) lua else js) { "WIDGET_SCRIPT_MISSING" }
        val bridges = widget.bridges ?: card.manifest.requires.bridges
        require("ui" in bridges && card.manifest.requiredBridges.containsAll(bridges)) { "WIDGET_BRIDGES" }
        val manifest = card.manifest.copy(requires = card.manifest.requires.copy(
            bridges = bridges,
            dependencies = card.manifest.requires.dependencies.filter { it.name in bridges },
        ))
        return card.copy(manifest = manifest, scriptText = file.readText(), engineKind = kind)
    }

    private fun unzip(zip: File, destDir: File) {
        require(zip.length() <= 8L * 1024 * 1024) { "CARD_ARCHIVE_TOO_LARGE" }
        destDir.mkdirs()
        var total = 0L
        var count = 0
        val names = HashSet<String>()
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                require(++count <= 256) { "CARD_TOO_MANY_ENTRIES" }
                require(names.add(entry.name)) { "CARD_DUPLICATE_ENTRY" }
                require('\\' !in entry.name && !File(entry.name).isAbsolute) { "CARD_ENTRY_PATH" }
                val target = File(destDir, entry.name)
                val canonical = target.canonicalPath
                require(canonical.startsWith(destDir.canonicalPath + File.separator)) { "非法 zip 路径：${entry.name}" }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val size = input.read(buffer)
                            if (size < 0) break
                            total += size
                            require(total <= 32L * 1024 * 1024) { "CARD_UNCOMPRESSED_TOO_LARGE" }
                            output.write(buffer, 0, size)
                        }
                    }
                }
                input.closeEntry()
                entry = input.nextEntry
            }
        }
    }
}
