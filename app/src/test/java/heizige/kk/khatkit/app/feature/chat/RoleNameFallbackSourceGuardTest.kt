package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 角色名为空时的兜底文案，全仓只允许一个来源：`R.string.assistant_page_default_assistant`。
 *
 * 回归现场（真机截图，2026-10）：同一个空名角色，抽屉角色行写死 `defaultAssistantName = "角色"`，
 * 而聊天页顶栏用资源（中文 `"默认角色"`），于是顶栏写「默认角色」、侧边栏勾着「角色」；
 * 看起来就是「顶栏角色 ≠ 侧边栏已选角色」，其实是同一个角色的两种兜底文案。
 *
 * 仓库 testImplementation 只有 junit，组合函数跑不起来，所以这里断言的是**源码文本**：
 * 所有 `defaultAssistantName =` 都必须是 `stringResource(...)`，不能是字面量。
 */
class RoleNameFallbackSourceGuardTest {

    private val literalFallback = Regex("""defaultAssistantName\s*=\s*"""")

    private val sourceRoots = listOf(
        File("src/main/java/heizige/kk/khatkit/app/core/ui"),
        File("src/main/java/heizige/kk/khatkit/app/feature"),
    )

    @Test
    fun `role name fallbacks never use string literals`() {
        val offenders = sourceRoots
            .onEach { assertTrue("源码目录不存在：${it.path}（测试的工作目录应是 app 模块）", it.isDirectory) }
            .flatMap { root ->
                root.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .sortedBy { it.path }
            }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = line.trim()
                    if (code.startsWith("//") || code.startsWith("*")) return@mapIndexedNotNull null
                    if (literalFallback.containsMatchIn(code)) "${file.path}:${index + 1}: $code" else null
                }.asSequence()
            }
            .toList()

        assertTrue(
            "角色名兜底文案写成了字面量，必须改用 " +
                "stringResource(R.string.assistant_page_default_assistant)：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
