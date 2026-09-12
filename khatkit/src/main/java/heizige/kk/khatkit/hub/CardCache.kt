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
        if (force && dir.exists()) dir.deleteRecursively()

        val downloadDir = File(rootDir, "downloads").apply { mkdirs() }
        val zip = hub.downloadCard(entry, downloadDir)
        unzip(zip, dir)
        zip.delete()
        // 同一卡片只保留当前版本，避免旧版本被重复加载成重复 tool
        dir.parentFile?.listFiles()?.forEach { sibling ->
            if (sibling.isDirectory && sibling.name != dir.name) sibling.deleteRecursively()
        }
        dir
    }

    /** 卸载卡片：删掉它的所有版本目录（其他卡片不受影响）。 */
    fun uninstall(name: String): Boolean {
        val cardRoot = File(rootDir, "cards/$name")
        return if (cardRoot.exists()) cardRoot.deleteRecursively() else false
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
