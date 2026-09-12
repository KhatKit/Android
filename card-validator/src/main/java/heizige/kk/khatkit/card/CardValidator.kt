package heizige.kk.khatkit.card

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 校验严重级别。 */
enum class Severity { ERROR, WARNING }

data class CardIssue(
    val code: String,
    val message: String,
    val severity: Severity,
)

/**
 * 卡片审核 CI 的静态校验（设计文档 13.1 + MANIFEST.md 第 5 节）。
 *
 * 只做宿主侧能确定性判断的检查；越权/破坏性副作用仍靠人工。
 * 传入脚本正文后，会扫描脚本里出现的 bridge 调用与域名是否已声明。
 */
object CardValidator {
    val ENGINES = setOf("lua", "js", "auto", "command")
    val PRIVILEGES = setOf("none", "elevated")
    val BRIDGES = setOf("tool", "ui", "download", "store", "shizuku", "root", "accessibility")

    private val NAME_REGEX = Regex("^[a-z][a-z0-9_]{1,63}$")
    private val SEMVER_REGEX = Regex("^\\d+\\.\\d+\\.\\d+([-+].*)?$")
    private val URL_REGEX = Regex("""https?://([A-Za-z0-9.-]+)""")
    private val BRIDGE_CALL_REGEX = Regex("""\b(tool|ui|download|store|shizuku|root|accessibility)\s*[.:]""")

    /**
     * @param manifest 解析后的卡片
     * @param scripts  脚本正文，key 为入口文件名（如 main.lua / main.js）
     */
    fun validate(
        manifest: CardManifest,
        scripts: Map<String, String> = emptyMap(),
    ): List<CardIssue> = buildList {
        if (!NAME_REGEX.matches(manifest.name)) {
            error("NAME_INVALID", "name 必须是小写字母开头的小写下划线：${manifest.name}")
        }
        if (!SEMVER_REGEX.matches(manifest.version)) {
            warning("VERSION_NOT_SEMVER", "version 建议使用 SemVer：${manifest.version}")
        }
        if (manifest.engine !in ENGINES) {
            error("ENGINE_UNKNOWN", "engine 必须是 $ENGINES 之一：${manifest.engine}")
        }
        if (manifest.privilege !in PRIVILEGES) {
            error("PRIVILEGE_UNKNOWN", "privilege 必须是 none|elevated：${manifest.privilege}")
        }

        val declared = manifest.requires.bridges
        val unknown = declared.filterNot { it in BRIDGES }
        if (unknown.isNotEmpty()) {
            error("BRIDGE_UNKNOWN", "声明了未知 bridge：$unknown")
        }

        // engine 与 entry 一致
        when (manifest.engine) {
            "lua" -> if (manifest.entry.lua.isNullOrBlank()) {
                error("ENTRY_MISSING", "engine=lua 需要 entry.lua")
            }
            "js" -> if (manifest.entry.js.isNullOrBlank()) {
                error("ENTRY_MISSING", "engine=js 需要 entry.js")
            }
            "auto" -> if (manifest.entry.lua.isNullOrBlank() && manifest.entry.js.isNullOrBlank()) {
                error("ENTRY_MISSING", "engine=auto 至少需要 entry.lua 或 entry.js 之一")
            }
            "command" -> if (manifest.command.isNullOrBlank()) {
                error("COMMAND_MISSING", "engine=command 需要 command 字段")
            }
        }

        // 标签：受控词表
        val tags = manifest.tags
        if (tags == null) {
            error("TAGS_MISSING", "tags 为必填（domain/action）")
        } else {
            if (!TagVocabulary.isValidDomain(tags.domain)) {
                error("TAG_DOMAIN_INVALID", "domain 不在词表内：${tags.domain}")
            }
            if (!TagVocabulary.isValidAction(tags.action)) {
                error("TAG_ACTION_INVALID", "action 不在词表内：${tags.action}")
            }
            if (!TagVocabulary.isValidScene(tags.scene)) {
                error("TAG_SCENE_INVALID", "scene 不在词表内：${tags.scene}")
            }
        }

        // game 域强制 compliance
        if (tags?.domain == "game" && manifest.compliance == null) {
            error("GAME_COMPLIANCE_MISSING", "domain=game 必须带 compliance（合规风险声明）")
        }

        // UI 声明：widget 必须在宿主白名单内，source 只能是 ai/ui（设计 7.2/13.1）
        manifest.ui.forEach { (param, spec) ->
            val widget = (spec["widget"] as? JsonPrimitive)?.contentOrNull
            if (widget != null && widget !in UiWidgetVocabulary.ALLOWED) {
                error("UI_WIDGET_UNKNOWN", "参数 $param 使用了未实现的组件：$widget")
            }
            val source = (spec["source"] as? JsonPrimitive)?.contentOrNull
            if (source != null && source !in UiWidgetVocabulary.SOURCES) {
                error("UI_SOURCE_INVALID", "参数 $param 的 source 必须是 ai|ui：$source")
            }
        }

        // 脚本正文扫描：bridge 调用 ⊆ 声明；域名 ⊆ network.allow
        if (scripts.isNotEmpty()) {
            val body = scripts.values.joinToString("\n")
            val usedBridges = BRIDGE_CALL_REGEX.findAll(body)
                .map { it.groupValues[1] }
                .toSet()
            val undeclared = usedBridges - declared
            if (undeclared.isNotEmpty()) {
                error("BRIDGE_UNDECLARED", "脚本调用了未声明的 bridge：$undeclared")
            }

            val declaredHosts = manifest.network.allow.map { it.trim().lowercase() }.toSet()
            val usedHosts = URL_REGEX.findAll(body).map { it.groupValues[1].lowercase() }.toSet()
            val undeclaredHosts = usedHosts.filter { host ->
                declaredHosts.none { allowed -> host == allowed || host.endsWith(".$allowed") }
            }
            if (undeclaredHosts.isNotEmpty()) {
                error("NETWORK_UNDECLARED", "脚本访问了未在白名单内的域名：$undeclaredHosts")
            }
        }
    }

    fun isValid(manifest: CardManifest, scripts: Map<String, String> = emptyMap()): Boolean =
        validate(manifest, scripts).none { it.severity == Severity.ERROR }

    private fun MutableList<CardIssue>.error(code: String, message: String) {
        add(CardIssue(code, message, Severity.ERROR))
    }

    private fun MutableList<CardIssue>.warning(code: String, message: String) {
        add(CardIssue(code, message, Severity.WARNING))
    }
}
