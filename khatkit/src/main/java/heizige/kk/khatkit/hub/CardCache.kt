package heizige.kk.khatkit.hub

import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.card.CardParser
import heizige.kk.khatkit.card.Semver
import heizige.kk.khatkit.engine.EngineFactory
import heizige.kk.khatkit.engine.EngineKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/** 已落地的卡片：manifest + 脚本正文。 */
data class LoadedCard(
    val manifest: CardManifest,
    val dir: File,
    val scriptText: String,
    val engineKind: EngineKind,
)

/**
 * 本地卡片缓存：下载 zip -> 校验 -> 解压到 cacheDir/cards/<name>/<version>/。
 */
class CardCache(private val rootDir: File) {

    fun cardDir(name: String, version: String): File = File(rootDir, "cards/$name/$version")

    fun libDir(name: String, version: String): File = File(rootDir, "libs/$name/$version")

    fun isCached(entry: CardIndexEntry): Boolean =
        File(cardDir(entry.name, entry.version), "card.json").exists()

    /** 按需下载并解压；已缓存且非强制更新则直接返回。 */
    suspend fun ensure(
        entry: CardIndexEntry,
        hub: HubClient,
        force: Boolean = false,
    ): File = withContext(Dispatchers.IO) {
        val dir = cardDir(entry.name, entry.version)
        if (!force && File(dir, "card.json").exists()) return@withContext dir

        val downloadDir = File(rootDir, "downloads").apply { mkdirs() }
        val zip = hub.downloadCard(entry, downloadDir)
        val stagingDir = File(rootDir, "staging/${entry.name}-${entry.version}-${System.nanoTime()}")
        try {
            unzip(zip, stagingDir)
            require(File(stagingDir, "card.json").isFile) { "卡片包缺少 card.json：${entry.name}" }
            dir.parentFile?.mkdirs()
            if (dir.exists()) require(dir.deleteRecursively()) { "无法替换卡片缓存：${entry.name}" }
            require(stagingDir.renameTo(dir)) { "无法提交卡片缓存：${entry.name}" }
        } finally {
            zip.delete()
            stagingDir.deleteRecursively()
        }
        // 同一卡片只保留当前版本，避免旧版本被重复加载成重复 tool
        dir.parentFile?.listFiles()?.forEach { sibling ->
            if (sibling.isDirectory && sibling.name != dir.name) sibling.deleteRecursively()
        }
        // 只有真正装进来的才记顺序；already-cached 走上面的早退分支，由 installOrder() 补记录。
        recordInstall(entry.name)
        dir
    }

    /** 卸载卡片：删掉它的所有版本目录（其他卡片不受影响）。 */
    fun uninstall(name: String): Boolean {
        val cardRoot = File(rootDir, "cards/$name")
        val removed = if (cardRoot.exists()) cardRoot.deleteRecursively() else false
        // 记录表里也要摘掉这个名字，否则重装会继承旧位置、记录表也会无限长。
        if (removed) writeOrder(readOrder().filterNot { it == name })
        return removed
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
    fun installOrder(): List<String> {
        val recorded = readOrder()
        val missing = installedVersions().keys
            .filterNot { it in recorded }
            .sortedBy { name -> File(rootDir, "cards/$name").lastModified() }
        if (missing.isEmpty()) return recorded
        return (recorded + missing).also(::writeOrder)
    }

    /** 记录一次成功安装；同一个名字（= 更新）保持原来的位置。 */
    fun recordInstall(name: String) {
        val recorded = readOrder()
        if (name in recorded) return
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
        val manifest = CardParser.parse(manifestFile.readText()).getOrThrow()
        val hasLua = manifest.entry.lua?.let { File(dir, it).exists() } == true
        val hasJs = manifest.entry.js?.let { File(dir, it).exists() } == true
        val kind = EngineFactory.resolve(manifest.engine, hasLua, hasJs)
        val scriptFile = when (kind) {
            EngineKind.LUA -> File(dir, manifest.entry.lua ?: "main.lua")
            EngineKind.JS -> File(dir, manifest.entry.js ?: "main.js")
            EngineKind.COMMAND -> null
        }
        val scriptText = scriptFile?.takeIf { it.exists() }?.readText().orEmpty()
        return LoadedCard(manifest, dir, scriptText, kind)
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
        destDir.mkdirs()
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                val target = File(destDir, entry.name)
                val canonical = target.canonicalPath
                require(canonical.startsWith(destDir.canonicalPath)) { "非法 zip 路径：${entry.name}" }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { input.copyTo(it) }
                }
                input.closeEntry()
                entry = input.nextEntry
            }
        }
    }
}
