package heizige.kk.khatkit.hub

import android.content.Context
import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.card.Semver
import heizige.kk.khatkit.engine.EngineKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** 解析好的脚本库：源码文本 + 版本。 */
data class ResolvedLib(
    val name: String,
    val lang: String,
    val version: String,
    val source: String,
)

/** 库来源（宿主内置 / CDN 共享），按顺序查找。 */
interface LibSourceProvider {
    suspend fun resolve(name: String, lang: String, versionRange: String): ResolvedLib?
}

/**
 * 脚本库解析器（设计文档 8.1/8.2）。
 *
 * 只解析与当前引擎同语言的库；命中内置层就不联网，否则落到 CDN 层。
 */
class LibResolver(private val providers: List<LibSourceProvider>) {

    suspend fun resolve(
        requirements: List<CardManifest.LibRequirement>,
        engine: EngineKind,
    ): List<ResolvedLib> {
        val lang = when (engine) {
            EngineKind.LUA -> "lua"
            EngineKind.JS -> "js"
            EngineKind.COMMAND -> return emptyList()
        }
        return requirements
            .filter { it.lang.equals(lang, ignoreCase = true) }
            .mapNotNull { requirement ->
                providers.firstNotNullOfOrNull { provider ->
                    runCatching { provider.resolve(requirement.name, lang, requirement.version) }.getOrNull()
                }
            }
    }
}

/** 宿主内置层：assets/libs/<dir>/<version>/{lib.json, main.lua, main.js}。 */
class BundledLibProvider(private val context: Context) : LibSourceProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun resolve(name: String, lang: String, versionRange: String): ResolvedLib? =
        withContext(Dispatchers.IO) {
            val root = "libs"
            for (dir in context.assets.list(root).orEmpty()) {
                for (version in context.assets.list("$root/$dir").orEmpty()) {
                    val meta = runCatching {
                        context.assets.open("$root/$dir/$version/lib.json")
                            .bufferedReader().use { it.readText() }
                    }.getOrNull() ?: continue
                    val libName = runCatching {
                        json.parseToJsonElement(meta).jsonObject["name"]?.jsonPrimitive?.content
                    }.getOrNull() ?: continue
                    if (libName != name || !Semver.satisfies(version, versionRange)) continue
                    val source = runCatching {
                        context.assets.open("$root/$dir/$version/main.$lang")
                            .bufferedReader().use { it.readText() }
                    }.getOrNull() ?: continue
                    return@withContext ResolvedLib(libName, lang, version, source)
                }
            }
            null
        }
}

/** CDN 共享层：从 Hub lib registry 按需下载并缓存到 cacheDir/libs。 */
class HubLibProvider(
    private val hub: HubClient,
    private val cache: CardCache,
) : LibSourceProvider {

    override suspend fun resolve(name: String, lang: String, versionRange: String): ResolvedLib? =
        withContext(Dispatchers.IO) {
            runCatching {
                val index = hub.fetchLibIndex()
                val candidate = index.libs
                    .filter {
                        it.name == name &&
                            it.lang.equals(lang, ignoreCase = true) &&
                            Semver.satisfies(it.version, versionRange)
                    }
                    .maxByOrNull { Semver.parse(it.version) ?: Semver.Version(0, 0, 0) }
                    ?: return@runCatching null

                val dir = cache.libDir(name.replace('/', '_'), candidate.version)
                val target = File(dir, "main.$lang")
                if (!target.exists() || target.length() == 0L) {
                    dir.mkdirs()
                    val downloaded = hub.downloadLib(candidate, dir)
                    downloaded.copyTo(target, overwrite = true)
                    downloaded.delete()
                }
                ResolvedLib(name, lang, candidate.version, target.readText())
            }.getOrNull()
        }
}
