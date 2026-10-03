package heizige.kk.khatkit.app.core.data.ai.lorebook

import heizige.kk.khatkit.ai.ui.UIMessage
import heizige.kk.khatkit.app.core.data.model.InjectionPosition
import heizige.kk.khatkit.app.core.data.model.LorebookKeyLogic
import heizige.kk.khatkit.app.core.data.model.PromptInjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LorebookEngineTest {
    private val chat = listOf(UIMessage.user("The castle holds a dragon"))

    @Test
    fun `or primary key activates on any match`() {
        val hit = activate(entry(keywords = listOf("castle", "ship")))
        assertEquals(listOf("castle"), hit.map { it.name })
    }

    @Test
    fun `no primary match stays inactive`() {
        assertTrue(activate(entry(keywords = listOf("spaceship"))).isEmpty())
    }

    @Test
    fun `case sensitive miss does not activate`() {
        assertTrue(activate(entry(keywords = listOf("CASTLE"), caseSensitive = true)).isEmpty())
    }

    @Test
    fun `case insensitive hit activates`() {
        assertEquals(1, activate(entry(keywords = listOf("CASTLE"))).size)
    }

    @Test
    fun `regex key matches`() {
        assertEquals(1, activate(entry(keywords = listOf("castl."), useRegex = true)).size)
    }

    @Test
    fun `invalid regex does not throw and does not match`() {
        assertTrue(activate(entry(keywords = listOf("["), useRegex = true)).isEmpty())
    }

    @Test
    fun `constant entry activates without keys`() {
        assertEquals(1, activate(entry(keywords = emptyList(), constant = true)).size)
    }

    @Test
    fun `disabled entry never activates`() {
        assertTrue(activate(entry(enabled = false, constant = true)).isEmpty())
    }

    @Test
    fun `and any requires one secondary key`() {
        val entry = entry(
            keywords = listOf("castle"),
            secondary = listOf("dragon", "knight"),
            selective = true,
            logic = LorebookKeyLogic.AND_ANY,
        )
        assertEquals(1, activate(entry, listOf(UIMessage.user("castle dragon"))).size)
        assertTrue(activate(entry, listOf(UIMessage.user("castle"))).isEmpty())
    }

    @Test
    fun `and all requires every secondary key`() {
        val entry = entry(
            keywords = listOf("castle"),
            secondary = listOf("dragon", "knight"),
            selective = true,
            logic = LorebookKeyLogic.AND_ALL,
        )
        assertTrue(activate(entry, listOf(UIMessage.user("castle dragon"))).isEmpty())
        assertEquals(1, activate(entry, listOf(UIMessage.user("castle dragon knight"))).size)
    }

    @Test
    fun `not any blocks when a secondary key is present`() {
        val entry = entry(
            keywords = listOf("castle"),
            secondary = listOf("dragon"),
            selective = true,
            logic = LorebookKeyLogic.NOT_ANY,
        )
        assertTrue(activate(entry).isEmpty())
        assertEquals(1, activate(entry, listOf(UIMessage.user("castle only"))).size)
    }

    @Test
    fun `not all blocks only when every secondary key is present`() {
        val entry = entry(
            keywords = listOf("castle"),
            secondary = listOf("dragon", "knight"),
            selective = true,
            logic = LorebookKeyLogic.NOT_ALL,
        )
        assertEquals(1, activate(entry, listOf(UIMessage.user("castle dragon"))).size)
        assertTrue(activate(entry, listOf(UIMessage.user("castle dragon knight"))).isEmpty())
    }

    @Test
    fun `probability zero never activates`() {
        assertTrue(activate(entry(probability = 0), roll = { 0 }).isEmpty())
    }

    @Test
    fun `probability 100 always activates`() {
        assertEquals(1, activate(entry(probability = 100), roll = { 99 }).size)
    }

    @Test
    fun `probability uses the injected roll`() {
        val entry = entry(probability = 40)
        assertTrue(activate(entry, roll = { 50 }).isEmpty())
        assertEquals(1, activate(entry, roll = { 10 }).size)
    }

    @Test
    fun `constant still respects probability`() {
        assertTrue(activate(entry(keywords = emptyList(), constant = true, probability = 0), roll = { 0 }).isEmpty())
    }

    @Test
    fun `higher priority is returned first`() {
        val low = entry(name = "low", keywords = listOf("castle"), priority = 1)
        val high = entry(name = "high", keywords = listOf("dragon"), priority = 9)
        assertEquals(listOf("high", "low"), activateAll(listOf(low, high)).map { it.name })
    }

    @Test
    fun `recursive scan lets later entries match activated content`() {
        val first = entry(name = "first", keywords = listOf("castle"), content = "secret passage")
        val second = entry(name = "second", keywords = listOf("secret"), content = "opened")
        val hit = activateLorebookEntries(listOf(first, second), chat, recursiveScanning = true) { 0 }
        assertEquals(setOf("first", "second"), hit.map { it.name }.toSet())
    }

    @Test
    fun `recursive scan off does not see activated content`() {
        val first = entry(name = "first", keywords = listOf("castle"), content = "secret passage")
        val second = entry(name = "second", keywords = listOf("secret"), content = "opened")
        val hit = activateLorebookEntries(listOf(first, second), chat, recursiveScanning = false) { 0 }
        assertEquals(listOf("first"), hit.map { it.name })
    }

    @Test
    fun `exclude recursion keeps content out of the buffer`() {
        val first = entry(name = "first", keywords = listOf("castle"), content = "secret passage", include = false)
        val second = entry(name = "second", keywords = listOf("secret"))
        val hit = activateLorebookEntries(listOf(first, second), chat, recursiveScanning = true) { 0 }
        assertEquals(listOf("first"), hit.map { it.name })
    }

    @Test
    fun `prevent recursion ignores activated content`() {
        val first = entry(name = "first", keywords = listOf("castle"), content = "secret passage")
        val second = entry(name = "second", keywords = listOf("secret"), prevent = true)
        val hit = activateLorebookEntries(listOf(first, second), chat, recursiveScanning = true) { 0 }
        assertEquals(listOf("first"), hit.map { it.name })
    }

    @Test
    fun `scan depth ignores older messages`() {
        val messages = listOf(UIMessage.user("castle"), UIMessage.user("unrelated"))
        val entry = entry(keywords = listOf("castle"), scanDepth = 1)
        assertTrue(activate(entry, messages).isEmpty())
    }

    private fun activate(
        entry: PromptInjection.RegexInjection,
        messages: List<UIMessage> = chat,
        roll: () -> Int = { 0 },
    ) = activateLorebookEntries(listOf(entry), messages, recursiveScanning = false, roll = roll)

    private fun activateAll(entries: List<PromptInjection.RegexInjection>) =
        activateLorebookEntries(entries, chat, recursiveScanning = false) { 0 }

    private fun entry(
        name: String = "castle",
        keywords: List<String> = listOf("castle"),
        secondary: List<String> = emptyList(),
        selective: Boolean = false,
        logic: LorebookKeyLogic = LorebookKeyLogic.OR,
        useRegex: Boolean = false,
        caseSensitive: Boolean = false,
        constant: Boolean = false,
        enabled: Boolean = true,
        probability: Int = 100,
        priority: Int = 0,
        content: String = name,
        include: Boolean = true,
        prevent: Boolean = false,
        scanDepth: Int = 4,
    ) = PromptInjection.RegexInjection(
        name = name,
        keywords = keywords,
        secondaryKeywords = secondary,
        selective = selective,
        keyLogic = logic,
        useRegex = useRegex,
        caseSensitive = caseSensitive,
        constantActive = constant,
        enabled = enabled,
        probability = probability,
        priority = priority,
        content = content,
        includeInRecursion = include,
        preventRecursion = prevent,
        scanDepth = scanDepth,
        position = InjectionPosition.AFTER_SYSTEM_PROMPT,
    )
}
