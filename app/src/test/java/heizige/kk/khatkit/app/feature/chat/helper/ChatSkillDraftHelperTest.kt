package heizige.kk.khatkit.app.feature.chat.helper

import heizige.kk.khatkit.app.core.data.files.SkillMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatSkillDraftHelperTest {
    @get:Rule val temp = TemporaryFolder()

    private fun skill(body: String, name: String = "demo"): SkillMetadata {
        val dir = temp.newFolder()
        dir.resolve("SKILL.md").writeText("---\r\nname: $name\r\ndescription: test\r\n---\r\n$body")
        return SkillMetadata("demo", "test", null, dir)
    }

    @Test fun loadsOnlyTheSelectedBody() {
        val selected = skill("第一行\nSecond line")
        selected.skillDir.resolve("secret.txt").writeText("not part of the skill body")
        assertEquals("技能：demo\n\n第一行\nSecond line", ChatSkillDraftHelper.read(selected, temp.root))
    }

    @Test fun rejectsEmptyChangedOversizedAndOutsideEntries() {
        for (selected in listOf(skill(" "), skill("body", "renamed"), skill("a".repeat(32_769)))) {
            assertThrows(IllegalArgumentException::class.java) { ChatSkillDraftHelper.read(selected, temp.root) }
        }
        val selected = skill("hello")
        assertThrows(IllegalArgumentException::class.java) { ChatSkillDraftHelper.read(selected, temp.newFolder()) }
    }
}
