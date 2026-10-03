package heizige.kk.khatkit.card

import org.junit.Assert.*
import org.junit.Test

class BrowserCardValidationTest {
    @Test
    fun browserNeedsDeclarationAndAcceptsItsMethodPolicies() {
        val manifest = CardManifest(
            name = "browser_example", version = "1.0.0", engine = "lua",
            entry = CardManifest.Entry(lua = "main.lua"),
            requires = CardManifest.Requires(bridges = listOf("browser")),
            network = CardManifest.Network(allow = listOf("example.com")),
            permissions = CardManifest.Permissions(methods = mapOf("browser.open" to "ask")),
            tags = CardManifest.Tags(domain = "net", action = "read"),
        )
        val script = mapOf("main.lua" to """return browser.open("https://example.com")""")
        val issues = CardValidator.validate(manifest, script)
        assertTrue(issues.toString(), issues.none { it.severity == Severity.ERROR })
        assertTrue(CardValidator.validate(manifest.copy(requires = CardManifest.Requires()), script)
            .any { it.severity == Severity.ERROR })
    }
}
