package heizige.kk.khatkit.app.core.data.ai.toolpkg

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

private val json = Json { ignoreUnknownKeys = true }

data class ToolPkgManifest(
    val id: String,
    val version: String,
    val entry: String,
    val apiVersion: String,
    val permissions: List<String>,
    val networkAllow: List<String>,
    val hooks: List<String>,
    val raw: JsonObject,
)

data class ProviderPluginSpec(
    val authScheme: String,
    val baseUrl: String,
    val networkAllow: List<String>,
)

class ToolPkgException(message: String) : IllegalArgumentException(message)

object ToolPkgArchive {
    fun read(bytes: ByteArray, expectedSha256: String): Pair<ToolPkgManifest, Map<String, ByteArray>> {
        val actual = sha256(bytes)
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            throw ToolPkgException("sha256 mismatch")
        }
        val files = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                if (entry.isDirectory) continue
                if (name.startsWith("/") || name.split('/').any { it == ".." }) {
                    throw ToolPkgException("illegal path: $name")
                }
                files[name] = zip.readBytes()
            }
        }
        val manifestBytes = files["manifest.json"] ?: throw ToolPkgException("missing manifest.json")
        return parseManifest(manifestBytes.decodeToString()) to files
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

fun parseManifest(raw: String): ToolPkgManifest {
    val root = json.parseToJsonElement(raw).jsonObject
    val id = root.string("toolpkg_id") ?: root.string("id") ?: throw ToolPkgException("missing id")
    val version = root.string("version") ?: throw ToolPkgException("missing version")
    val entry = root.string("entry") ?: throw ToolPkgException("missing entry")
    val apiVersion = root.string("api_version") ?: root.string("schema_version") ?: "1.0.0"
    return ToolPkgManifest(
        id = id,
        version = version,
        entry = entry,
        apiVersion = apiVersion,
        permissions = root.stringList("permissions"),
        networkAllow = root.obj("network")?.stringList("allow").orEmpty(),
        hooks = root.stringList("hooks"),
        raw = root,
    )
}

fun parseProviderPlugin(raw: String): ProviderPluginSpec {
    val root = json.parseToJsonElement(raw).jsonObject
    val provider = root.obj("provider") ?: throw ToolPkgException("missing provider")
    val allow = root.obj("network")?.stringList("allow").orEmpty()
    if (allow.isEmpty()) throw ToolPkgException("provider network.allow is required")
    return ProviderPluginSpec(
        authScheme = provider.string("auth_scheme") ?: throw ToolPkgException("missing auth_scheme"),
        baseUrl = provider.string("base_url") ?: throw ToolPkgException("missing base_url"),
        networkAllow = allow,
    )
}

private val settingsFieldTypes = setOf(
    "TextField", "NumberField", "Switch", "Checkbox", "Select", "Slider",
    "RadioGroup", "FilePicker", "DirPicker",
)
private val settingsDisplayTypes = setOf("Section", "Text", "Markdown", "Badge")

fun validateSettingsSchema(raw: String) {
    val root = json.parseToJsonElement(raw).jsonObject
    val ids = mutableSetOf<String>()
    val entries = root["entries"] as? JsonArray ?: throw ToolPkgException("missing entries")
    entries.forEach { entry ->
        val page = entry.jsonObject.obj("page") ?: throw ToolPkgException("missing page")
        walkSettings(page, ids)
    }
}

private fun walkSettings(node: JsonObject, ids: MutableSet<String>) {
    val type = node.string("type") ?: node.string("__ui") ?: throw ToolPkgException("missing type")
    if (type == "Button") throw ToolPkgException("plugin pages cannot declare Button")
    if (type !in settingsFieldTypes && type !in settingsDisplayTypes) {
        throw ToolPkgException("unsupported settings node $type")
    }
    if (type in settingsFieldTypes) {
        val id = node.string("id") ?: throw ToolPkgException("field missing id")
        if (!ids.add(id)) throw ToolPkgException("duplicate field id $id")
    }
    val children = node["children"] as? JsonArray ?: return
    children.forEach { walkSettings(it.jsonObject, ids) }
}

enum class HookPoint { PRE_TOOL, POST_TOOL, PRE_STREAM, POST_STREAM, ON_ERROR }

data class HookCall(
    val point: HookPoint,
    val toolName: String? = null,
    val arguments: JsonObject = JsonObject(emptyMap()),
    val result: JsonObject = JsonObject(emptyMap()),
    val headers: Map<String, String> = emptyMap(),
    val delta: String = "",
    val error: String = "",
    val securityError: Boolean = false,
    val providerId: String? = null,
    val modelId: String? = null,
)

data class HookPatch(
    val block: Boolean = false,
    val reason: String = "",
    val arguments: JsonObject? = null,
    val toolName: String? = null,
    val result: JsonObject? = null,
    val append: String = "",
    val untrusted: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val dropDelta: Boolean = false,
    val rewrittenDelta: String? = null,
    val abort: Boolean = false,
    val swallowError: Boolean = false,
    val message: String = "",
    val providerId: String? = null,
    val modelId: String? = null,
)

data class HookOutcome(
    val blocked: Boolean = false,
    val reason: String = "",
    val arguments: JsonObject = JsonObject(emptyMap()),
    val result: JsonObject = JsonObject(emptyMap()),
    val append: String = "",
    val untrusted: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val delta: String = "",
    val dropDelta: Boolean = false,
    val errorMessage: String = "",
)

class HookRuntime {
    var enabled: Boolean = true
    private var depth = 0

    fun apply(
        call: HookCall,
        patch: HookPatch,
        networkAllow: List<String> = emptyList(),
        during: (() -> Unit)? = null,
    ): HookOutcome {
        if (!enabled) return pass(call)
        if (depth >= 1) throw ToolPkgException("nested hook")
        depth++
        try {
            during?.invoke()
            return when (call.point) {
                HookPoint.PRE_TOOL -> {
                    if (patch.toolName != null && patch.toolName != call.toolName) {
                        throw ToolPkgException("pre_tool cannot rename the tool")
                    }
                    HookOutcome(
                        blocked = patch.block,
                        reason = patch.reason,
                        arguments = patch.arguments ?: call.arguments,
                    )
                }
                HookPoint.POST_TOOL -> {
                    val merged = mergeResult(call.result, patch.result)
                    HookOutcome(
                        arguments = call.arguments,
                        result = merged,
                        append = patch.append,
                        untrusted = patch.untrusted,
                    )
                }
                HookPoint.PRE_STREAM -> {
                    if (patch.providerId != null || patch.modelId != null) {
                        throw ToolPkgException("pre_stream cannot change provider or model")
                    }
                    patch.headers.forEach { (_, value) ->
                        if (networkAllow.none { value.contains(it) }) {
                            throw ToolPkgException("header domain is not allowed")
                        }
                    }
                    HookOutcome(arguments = call.arguments, headers = call.headers + patch.headers)
                }
                HookPoint.POST_STREAM -> {
                    if (patch.abort) throw ToolPkgException("post_stream cannot abort generation")
                    HookOutcome(
                        delta = patch.rewrittenDelta ?: call.delta,
                        dropDelta = patch.dropDelta,
                    )
                }
                HookPoint.ON_ERROR -> {
                    if (call.securityError && patch.swallowError) {
                        throw ToolPkgException("security errors cannot be swallowed")
                    }
                    HookOutcome(errorMessage = if (patch.swallowError) patch.message else call.error)
                }
            }
        } finally {
            depth--
        }
    }

    private fun pass(call: HookCall) = HookOutcome(
        arguments = call.arguments,
        result = call.result,
        headers = call.headers,
        delta = call.delta,
        errorMessage = call.error,
    )

    private fun mergeResult(original: JsonObject, patch: JsonObject?): JsonObject {
        if (patch == null) return original
        val missing = original.keys.filter { it !in patch }
        if (missing.isNotEmpty()) throw ToolPkgException("post_tool cannot delete ${missing.first()}")
        return JsonObject(original.toMutableMap().apply { putAll(patch) })
    }
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.stringList(key: String): List<String> = when (val value = this[key]) {
    is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    else -> emptyList()
}

object ToolPkgHooks {
    private val runtime = HookRuntime()

    fun prepareToolArgs(enabled: Boolean, toolName: String, args: JsonElement): JsonElement {
        runtime.enabled = enabled
        if (!enabled) return args
        val call = HookCall(
            point = HookPoint.PRE_TOOL,
            toolName = toolName,
            arguments = args as? JsonObject ?: JsonObject(emptyMap()),
        )
        return runtime.apply(call, HookPatch()).arguments
    }
}
