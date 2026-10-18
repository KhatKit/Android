package heizige.kk.khatkit.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiIconsTest {
    @Test fun luaValidatorKnowsTheSameIconNames() {
        val text = javaClass.getResource("/ui_kit.lua")!!.readText()
        val block = Regex("\\[\\[(account_circle[^\\]]*)\\]\\]", RegexOption.DOT_MATCHES_ALL)
            .find(text)!!.groupValues[1]
        val luaNames = block.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        assertEquals(UiIcons.names, luaNames)
    }
}
