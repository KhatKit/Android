package heizige.kk.khatkit.card

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardStringsTest {
    private val card = CardManifest(
        name = "test_card", description = "Legacy description", defaultLocale = "en",
        i18n = mapOf(
            "en" to mapOf("name" to "Test", "greet" to "Hello, {name}", "fallback" to "Default"),
            "zh-Hant" to mapOf("name" to "測試"),
            "pt" to mapOf("name" to "Teste"),
            "sr-Latn" to mapOf("name" to "Primer"),
            "ar" to mapOf("name" to "اختبار"),
            "ja" to mapOf("greet" to "こんにちは"),
        ),
    )

    @Test fun regionAndScriptFallbacksAreNotRestrictedToAppLanguages() {
        assertEquals("Teste", CardStrings(card, listOf("pt-BR")).name)
        assertEquals("Primer", CardStrings(card, listOf("sr-Latn-RS")).name)
        assertEquals("測試", CardStrings(card, listOf("zh-Hant-TW")).name)
        assertEquals("اختبار", CardStrings(card, listOf("ar-EG")).name)
    }

    @Test fun missingKeysTryOtherPreferredLanguagesThenDefault() {
        val strings = CardStrings(card, listOf("de-DE", "ja-JP"))
        assertEquals("こんにちは", strings.text("greet"))
        assertEquals("Default", strings.text("fallback"))
        assertEquals("missing", strings.text("missing"))
        assertEquals("Legacy description", strings.description)
    }

    @Test fun replacementNeverChangesUnknownPlaceholdersOrUnicode() {
        val strings = CardStrings(card, listOf("en"))
        assertEquals("Hello, 用户", strings.text("greet", mapOf("name" to "用户")))
        assertEquals("Hello, {name}", strings.text("greet"))
        assertEquals("@other/value", strings.resolve("@other/value"))
    }

    @Test fun schemaValuesAreLocalizedWithoutChangingIdsOrNumbers() {
        val input = Json.parseToJsonElement("""{"id":"name","label":"@string/name","value":5,"required":true}""")
        val output = CardStrings(card, listOf("pt-PT")).resolveJson(input) as JsonObject
        assertEquals("\"Teste\"", output["label"].toString())
        assertEquals("5", output["value"].toString())
        assertEquals("true", output["required"].toString())
        assertEquals("\"name\"", output["id"].toString())
    }

    @Test fun oldManifestsStillRoundTripAndKeepPlainDescriptions() {
        val legacy = CardParser.parse("""{"name":"legacy","description":"Original","engine":"lua","entry":{"lua":"main.lua"}}""").getOrThrow()
        assertTrue(legacy.i18n.isEmpty())
        assertEquals(null, legacy.widget)
        assertEquals("Original", CardStrings(legacy, listOf("fr-FR")).description)
        assertEquals(legacy, CardParser.parse(CardParser.serialize(legacy)).getOrThrow())
    }

    @Test fun localeAliasesAreCaseInsensitiveAndNoUnrelatedScriptIsChosen() {
        val aliases = card.copy(i18n = card.i18n + ("pt_BR" to mapOf("name" to "Brasil")))
        assertEquals("Brasil", CardStrings(aliases, listOf("pt-br")).name)
        assertFalse(CardStrings(card, listOf("zh-Hans-CN")).name == "測試")
    }
}
