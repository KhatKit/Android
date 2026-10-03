package heizige.kk.khatkit.app.core.data.ai.toolpkg

import heizige.kk.khatkit.app.core.data.datastore.FeatureFlags
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.datastore.visibleMarketKinds
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ToolPkgRuntimeTest {
    @Test
    fun `market new kinds stay hidden by default`() {
        val flags = Settings().featureFlags
        assertFalse(flags.marketNewKinds)
        assertEquals(listOf("card"), flags.visibleMarketKinds())
        assertTrue(FeatureFlags(marketNewKinds = true).visibleMarketKinds().contains("provider_plugin"))
    }

    @Test
    fun `archive rejects path traversal and checks sha256`() {
        val zip = zipOf("manifest.json" to manifest(), "../evil.txt" to "no")
        assertThrows(ToolPkgException::class.java) {
            ToolPkgArchive.read(zip, ToolPkgArchive.sha256(zip))
        }
        val good = zipOf("manifest.json" to manifest())
        assertThrows(ToolPkgException::class.java) {
            ToolPkgArchive.read(good, "0".repeat(64))
        }
        val parsed = ToolPkgArchive.read(good, ToolPkgArchive.sha256(good))
        assertEquals("sample", parsed.first.id)
    }

    @Test
    fun `hooks enforce the capability table`() {
        val runtime = HookRuntime()
        val args = buildJsonObject { put("q", "1") }
        val blocked = runtime.apply(
            HookCall(HookPoint.PRE_TOOL, toolName = "search", arguments = args),
            HookPatch(block = true, reason = "no"),
        )
        assertTrue(blocked.blocked)
        assertThrows(ToolPkgException::class.java) {
            runtime.apply(
                HookCall(HookPoint.PRE_TOOL, toolName = "search", arguments = args),
                HookPatch(toolName = "other"),
            )
        }
        val original = buildJsonObject { put("value", "keep") }
        assertThrows(ToolPkgException::class.java) {
            runtime.apply(
                HookCall(HookPoint.POST_TOOL, result = original),
                HookPatch(result = buildJsonObject { put("extra", "x") }),
            )
        }
        val kept = runtime.apply(
            HookCall(HookPoint.POST_TOOL, result = original),
            HookPatch(result = buildJsonObject { put("value", "keep"); put("extra", "x") }, untrusted = true),
        )
        assertEquals("keep", kept.result["value"]!!.toString().trim('"'))
        assertTrue(kept.untrusted)
        assertThrows(ToolPkgException::class.java) {
            runtime.apply(HookCall(HookPoint.PRE_STREAM), HookPatch(providerId = "other"))
        }
        assertThrows(ToolPkgException::class.java) {
            runtime.apply(
                HookCall(HookPoint.PRE_STREAM),
                HookPatch(headers = mapOf("X-Sign" to "https://evil.example")),
                networkAllow = listOf("api.example.com"),
            )
        }
        assertThrows(ToolPkgException::class.java) {
            runtime.apply(HookCall(HookPoint.POST_STREAM, delta = "hi"), HookPatch(abort = true))
        }
        assertThrows(ToolPkgException::class.java) {
            runtime.apply(
                HookCall(HookPoint.ON_ERROR, error = "denied", securityError = true),
                HookPatch(swallowError = true, message = "ok"),
            )
        }
    }

    @Test
    fun `disabled hooks and nested hooks do not run`() {
        val runtime = HookRuntime()
        runtime.enabled = false
        val outcome = runtime.apply(
            HookCall(HookPoint.PRE_TOOL, toolName = "search"),
            HookPatch(block = true),
        )
        assertFalse(outcome.blocked)
        runtime.enabled = true
        assertThrows(ToolPkgException::class.java) {
            runtime.apply(HookCall(HookPoint.PRE_TOOL), HookPatch()) {
                runtime.apply(HookCall(HookPoint.PRE_TOOL), HookPatch())
            }
        }
    }

    @Test
    fun `plugin settings reject buttons and duplicate ids`() {
        assertThrows(ToolPkgException::class.java) {
            validateSettingsSchema(
                """{"entries":[{"id":"a","page":{"type":"Button","label":"Go"}}]}""",
            )
        }
        assertThrows(ToolPkgException::class.java) {
            validateSettingsSchema(
                """{"entries":[{"page":{"type":"Section","children":[
                  {"type":"TextField","id":"base_url"},
                  {"type":"TextField","id":"base_url"}
                ]}}]}""",
            )
        }
        validateSettingsSchema(
            """{"entries":[{"page":{"type":"Section","children":[
              {"type":"TextField","id":"base_url"},
              {"type":"Switch","id":"verbose"}
            ]}}]}""",
        )
    }

    @Test
    fun `provider plugin requires network allow`() {
        assertThrows(ToolPkgException::class.java) {
            parseProviderPlugin("""{"provider":{"auth_scheme":"bearer","base_url":"https://example.com"}}""")
        }
        val spec = parseProviderPlugin(
            """{"network":{"allow":["example.com"]},"provider":{"auth_scheme":"bearer","base_url":"https://example.com"}}""",
        )
        assertEquals("bearer", spec.authScheme)
    }

    private fun manifest(): String = """
        {"id":"sample","version":"1.0.0","entry":"main.js","api_version":"1.0.0","hooks":["pre_tool"]}
    """.trimIndent()

    private fun zipOf(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
