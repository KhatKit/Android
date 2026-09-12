package heizige.kk.khatkit.card

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class BuiltinCardSampleTest {

    @Test
    fun builtinSamplesAreValid() {
        val root = File("src/main/assets/cards")
        assumeTrue(root.exists())
        val failures = root.listFiles().orEmpty().mapNotNull { dir ->
            val manifestFile = File(dir, "card.json")
            if (!manifestFile.exists()) return@mapNotNull "${dir.name}: 缺少 card.json"
            val parseResult = CardParser.parse(manifestFile.readText())
            val manifest = parseResult.getOrNull()
                ?: return@mapNotNull "${dir.name}: ${parseResult.exceptionOrNull()?.message}"
            val scripts = listOfNotNull(
                manifest.entry.lua?.let { it to File(dir, it) },
                manifest.entry.js?.let { it to File(dir, it) },
            ).filter { (_, file) -> file.exists() }.associate { (name, file) -> name to file.readText() }
            val errors = CardValidator.validate(manifest, scripts)
                .filter { it.severity == Severity.ERROR }
            if (errors.isEmpty()) null else "${dir.name}: $errors"
        }
        assertTrue("内置示例卡片未通过校验：$failures", failures.isEmpty())
    }
}
