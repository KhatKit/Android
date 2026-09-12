package heizige.kk.khatkit.card.cli

import heizige.kk.khatkit.card.CardParser
import heizige.kk.khatkit.card.CardValidator
import heizige.kk.khatkit.card.Severity
import java.io.File
import kotlin.system.exitProcess

/**
 * 卡片仓库 CI 用的独立校验器。
 *
 * 用法：`./gradlew :card-validator:run --args="/path/to/cards"`
 * 目录结构：`cards/<card-dir>/card.json` + entry 脚本。
 * 有任何 ERROR 退出码为 1。
 */
fun main(args: Array<String>) {
    val root = File(args.firstOrNull() ?: "cards")
    if (!root.isDirectory) {
        System.err.println("cards directory not found: ${root.absolutePath}")
        exitProcess(2)
    }

    val cardDirs = root.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }
    var errorCount = 0
    var checked = 0

    cardDirs.forEach { dir ->
        val manifestFile = File(dir, "card.json")
        if (!manifestFile.exists()) {
            System.err.println("[ERROR] ${dir.name}: missing card.json")
            errorCount++
            return@forEach
        }

        val parseResult = CardParser.parse(manifestFile.readText())
        val manifest = parseResult.getOrNull()
        if (manifest == null) {
            System.err.println("[ERROR] ${dir.name}: card.json 解析失败: ${parseResult.exceptionOrNull()?.message}")
            errorCount++
            return@forEach
        }

        val scripts = listOfNotNull(
            manifest.entry.lua?.let { it to File(dir, it) },
            manifest.entry.js?.let { it to File(dir, it) },
        ).filter { (_, file) -> file.exists() }
            .associate { (name, file) -> name to file.readText() }

        val issues = CardValidator.validate(manifest, scripts)
        val errors = issues.filter { it.severity == Severity.ERROR }
        val warnings = issues.filter { it.severity == Severity.WARNING }

        checked++
        warnings.forEach { println("[WARN ] ${dir.name}: ${it.code} ${it.message}") }
        errors.forEach { System.err.println("[ERROR] ${dir.name}: ${it.code} ${it.message}") }
        errorCount += errors.size
        if (errors.isEmpty()) {
            println("[OK    ] ${dir.name} (${manifest.engine}, ${manifest.tags?.domain}/${manifest.tags?.action})")
        }
    }

    println()
    println("checked $checked card(s), $errorCount error(s)")
    if (errorCount > 0) exitProcess(1)
}
