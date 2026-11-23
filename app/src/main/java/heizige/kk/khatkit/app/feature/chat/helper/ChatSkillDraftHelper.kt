package heizige.kk.khatkit.app.feature.chat.helper

import heizige.kk.khatkit.app.core.data.files.SkillFrontmatterParser
import heizige.kk.khatkit.app.core.data.files.SkillMetadata
import heizige.kk.khatkit.app.core.data.files.SkillValidator
import java.io.File

internal object ChatSkillDraftHelper {
    private const val MAX_CHARS = 32_768

    /** Read only the selected installed SKILL.md, never links or referenced scripts. */
    fun read(skill: SkillMetadata, skillsRoot: File): String {
        val dir = skill.skillDir.canonicalFile
        val file = skill.skillFile.canonicalFile
        require(dir.parentFile == skillsRoot.canonicalFile && file.parentFile == dir && file.isFile) {
            "所选技能文件已移动或不可用"
        }
        val source = file.bufferedReader().use { reader ->
            val buffer = CharArray(MAX_CHARS + 1)
            var count = 0
            while (count < buffer.size) {
                val n = reader.read(buffer, count, buffer.size - count)
                if (n < 0) break
                count += n
            }
            require(count <= MAX_CHARS) { "技能文件太长，不能直接载入草稿" }
            buffer.concatToString(0, count)
        }
        require(SkillValidator.validate(source).getOrNull() == skill.name) { "技能已更改，请重新选择" }
        val body = SkillFrontmatterParser.extractBody(source).trim()
        require(body.isNotEmpty()) { "技能没有正文" }
        return "技能：${skill.name}\n\n$body"
    }
}
