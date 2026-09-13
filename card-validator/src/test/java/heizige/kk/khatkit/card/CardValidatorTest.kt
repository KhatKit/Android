package heizige.kk.khatkit.card

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardValidatorTest {

    private fun manifest(
        name: String = "pdf_merge",
        engine: String = "lua",
        privilege: String = "none",
        bridges: List<String> = listOf("tool", "ui"),
        tags: CardManifest.Tags? = CardManifest.Tags("file", "convert", "work"),
        compliance: CardManifest.Compliance? = null,
        network: List<String> = emptyList(),
        command: String? = null,
        triggers: List<String> = CardManifest.DEFAULT_TRIGGERS,
    ) = CardManifest(
        name = name,
        version = "1.2.0",
        engine = engine,
        entry = CardManifest.Entry(lua = "main.lua"),
        privilege = privilege,
        requires = CardManifest.Requires(bridges = bridges),
        network = CardManifest.Network(allow = network),
        parameters = JsonObject(emptyMap()),
        triggers = triggers,
        tags = tags,
        compliance = compliance,
        command = command,
    )

    @Test
    fun validCardPasses() {
        assertTrue(CardValidator.isValid(manifest()))
    }

    @Test
    fun invalidNameRejected() {
        val issues = CardValidator.validate(manifest(name = "Bad-Name"))
        assertTrue(issues.any { it.code == "NAME_INVALID" && it.severity == Severity.ERROR })
    }

    @Test
    fun gameDomainRequiresCompliance() {
        val issues = CardValidator.validate(
            manifest(tags = CardManifest.Tags("game", "control"))
        )
        assertTrue(issues.any { it.code == "GAME_COMPLIANCE_MISSING" })
    }

    @Test
    fun gameDomainWithCompliancePasses() {
        assertTrue(
            CardValidator.isValid(
                manifest(
                    tags = CardManifest.Tags("game", "control"),
                    compliance = CardManifest.Compliance("high", "注意用户协议"),
                )
            )
        )
    }

    @Test
    fun unknownTagRejected() {
        val issues = CardValidator.validate(manifest(tags = CardManifest.Tags("pdf", "convert")))
        assertTrue(issues.any { it.code == "TAG_DOMAIN_INVALID" })
    }

    @Test
    fun undeclaredBridgeInScriptRejected() {
        val issues = CardValidator.validate(
            manifest(),
            scripts = mapOf("main.lua" to "root.shell('id')"),
        )
        assertTrue(issues.any { it.code == "BRIDGE_UNDECLARED" })
    }

    @Test
    fun undeclaredDomainInScriptRejected() {
        val issues = CardValidator.validate(
            manifest(),
            scripts = mapOf("main.lua" to "tool.httpGet('https://evil.example.com/x')"),
        )
        assertTrue(issues.any { it.code == "NETWORK_UNDECLARED" })
    }

    @Test
    fun declaredDomainAllowed() {
        val issues = CardValidator.validate(
            manifest(network = listOf("example.com")),
            scripts = mapOf("main.lua" to "tool.httpGet('https://api.example.com/x')"),
        )
        assertFalse(issues.any { it.code == "NETWORK_UNDECLARED" })
    }

    @Test
    fun commandEngineNeedsCommandField() {
        val issues = CardValidator.validate(manifest(engine = "command"))
        assertTrue(issues.any { it.code == "COMMAND_MISSING" })
    }

    @Test
    fun unknownTriggerRejected() {
        val issues = CardValidator.validate(manifest(triggers = listOf("timer")))
        assertTrue(issues.any { it.code == "TRIGGER_UNKNOWN" && it.severity == Severity.ERROR })
    }

    @Test
    fun duplicateTriggerRejected() {
        val issues = CardValidator.validate(manifest(triggers = listOf("ai", "ai")))
        assertTrue(issues.any { it.code == "TRIGGER_DUPLICATE" && it.severity == Severity.ERROR })
    }

    @Test
    fun emptyTriggersRejected() {
        val issues = CardValidator.validate(manifest(triggers = emptyList()))
        assertTrue(issues.any { it.code == "TRIGGER_EMPTY" && it.severity == Severity.ERROR })
    }

    @Test
    fun defaultTriggersSupportAiAndUser() {
        val parsed = manifest()
        assertTrue(parsed.supportsAi())
        assertTrue(parsed.supportsUser())
    }

    @Test
    fun explicitUserOnlyTriggerParses() {
        val parsed = CardParser.parse(
            """
            {
              "name": "user_only",
              "version": "1.0.0",
              "engine": "lua",
              "entry": { "lua": "main.lua" },
              "requires": { "bridges": ["ui"] },
              "network": { "allow": [] },
              "triggers": ["user"],
              "tags": { "domain": "file", "action": "convert" }
            }
            """.trimIndent()
        ).getOrThrow()
        assertFalse(parsed.supportsAi())
        assertTrue(parsed.supportsUser())
        assertTrue(CardValidator.isValid(parsed))
    }

    @Test
    fun jsonParsingRoundTrip() {
        val json = """
            {
              "name": "pdf_merge",
              "version": "1.2.0",
              "description": "合并多个 PDF 为一个",
              "engine": "auto",
              "entry": { "lua": "main.lua", "js": "main.js" },
              "privilege": "none",
              "requires": { "bridges": ["tool", "ui"], "libs": [], "bins": [], "env": [] },
              "network": { "allow": [] },
              "parameters": { "type": "object", "properties": {} },
              "tags": { "domain": "file", "action": "convert", "scene": "work" },
              "store": { "quota_mb": 50, "secret": false }
            }
        """.trimIndent()
        val parsed = CardParser.parse(json).getOrThrow()
        assertEquals("pdf_merge", parsed.name)
        assertEquals("work", parsed.tags?.scene)
        assertEquals(50, parsed.store.quotaMb)
        assertEquals(listOf("ai", "user"), parsed.triggers)
        assertTrue(CardValidator.isValid(parsed))
    }
}
