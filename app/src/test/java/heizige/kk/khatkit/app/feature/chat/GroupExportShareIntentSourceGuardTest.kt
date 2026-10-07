package heizige.kk.khatkit.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * C1-09 ①/导出的**分享 Intent 构造护栏**（源码文本断言，非行为断言）。
 *
 * 为什么不写成行为单测：导出/分享的两处 `ACTION_SEND` 都直接在函数体里
 * `context.startActivity(Intent.createChooser(...))`，需要一个真实的 `Context` 才能执行。
 * 本仓 `app/build.gradle.kts:343` 的 `testImplementation` **只有 junit**（无 Robolectric），
 * JVM 侧 `android.content.Intent` / `startActivity` 要么抛 `Stub!` 要么只能返回默认值，
 * 断言不出 action / type / extra / flag。真机分享面板又需要用户点选目标 app，不可自动化
 * （`C1GroupExportDeviceEvidenceTest` 也把这条列为未覆盖）。
 *
 * ⇒ 这里守的是**构造文本本身**：关键构造各**恰一次**且语义正确。风格照抄仓库既有
 * `*SourceGuardTest`（见 `GroupStaleJobCommitSourceGuardTest`）。
 *
 * 覆盖的两个构造点：
 * - [shareFile]：`feature/chat/ConversationExport.kt` —— 群聊导出卡经
 *   `GroupExportCard.kt:218` 调它分享 `.jsonl`。
 * - [ExporterState.exportAndShare]：`core/data/export/ExportHooks.kt` —— 通用 JSON 导出分享。
 *
 * 漏掉守卫为什么严重：少写 `FLAG_GRANT_READ_URI_PERMISSION` 会让接收方拿到
 * `content://` uri 却**无读权限**，分享面板里点了目标 app 也打不开文件；
 * 少写 `EXTRA_STREAM` 则对方收到一个没有附件的空分享。两者都是编译通过、行为错误。
 */
class GroupExportShareIntentSourceGuardTest {

    private val conversationExportPath =
        "src/main/java/heizige/kk/khatkit/app/feature/chat/ConversationExport.kt"
    private val exportHooksPath =
        "src/main/java/heizige/kk/khatkit/app/core/data/export/ExportHooks.kt"

    private fun countOccurrences(haystack: String, needle: String): Int {
        var count = 0
        var index = haystack.indexOf(needle)
        while (index >= 0) {
            count++
            index = haystack.indexOf(needle, index + needle.length)
        }
        return count
    }

    /**
     * 按大括号配平从 [signature] 处截出整个函数体。
     *
     * 退化必须显式拦掉：截不出（签名 / 大括号没找到）直接 fail，而不是返回空串 ——
     * 空串会让下面所有 `contains` / 计数断言变成「谁都不违规而恒绿」的空检查。
     */
    private fun functionBody(path: String, signature: String): String {
        val text = File(path).readText()
        val start = text.indexOf(signature)
        assertTrue("函数签名没找到：$signature（$path）", start >= 0)
        val open = text.indexOf('{', start)
        assertTrue("函数左大括号没找到：$signature", open >= 0)
        var depth = 0
        for (index in open until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(open, index + 1)
                }
            }
        }
        throw AssertionError("函数右大括号没找到：$signature（$path）")
    }

    // ------------------------------------------------------------------
    // ConversationExport.shareFile —— 群聊导出的分享入口
    // ------------------------------------------------------------------

    /** 反空跑：先证明真的截到了 shareFile 的函数体，而不是退化空壳。 */
    @Test
    fun `shareFile body resolves to real content`() {
        val body = functionBody(conversationExportPath, "internal fun shareFile(")
        assertTrue(
            "shareFile 函数体必须含有它自己的正面内容（createChooser），否则是退化空壳",
            body.contains("Intent.createChooser("),
        )
        assertTrue("shareFile 函数体行数异常，截取可能出错：\n$body", body.lines().size > 5)
    }

    @Test
    fun `shareFile builds exactly one ACTION_SEND intent with type stream and read grant`() {
        val body = functionBody(conversationExportPath, "internal fun shareFile(")

        assertEquals(
            "shareFile 必须恰好构造一个 ACTION_SEND",
            1,
            countOccurrences(body, "android.content.Intent.ACTION_SEND"),
        )
        assertEquals(
            "shareFile 必须恰好设置一次 MIME type",
            1,
            countOccurrences(body, "type = mimeType"),
        )
        assertEquals(
            "shareFile 必须恰好塞一次 EXTRA_STREAM（缺了对方收到空分享）",
            1,
            countOccurrences(body, "putExtra(android.content.Intent.EXTRA_STREAM, uri)"),
        )
        assertEquals(
            "shareFile 必须恰好加一次 FLAG_GRANT_READ_URI_PERMISSION（缺了接收方无读权限）",
            1,
            countOccurrences(body, "addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)"),
        )
        assertTrue(
            "分享必须包一层 createChooser 弹系统面板",
            body.contains("android.content.Intent.createChooser("),
        )
        assertTrue(
            "分享面板标题必须走字符串资源 chat_page_export_share_via",
            body.contains("context.getString(R.string.chat_page_export_share_via)"),
        )
    }

    @Test
    fun `writeExportTempFile exposes the share uri through a package-scoped FileProvider`() {
        val body = functionBody(conversationExportPath, "internal fun writeExportTempFile(")

        assertEquals(
            "必须恰好调一次 FileProvider.getUriForFile",
            1,
            countOccurrences(body, "FileProvider.getUriForFile("),
        )
        assertEquals(
            "FileProvider authority 必须恰好一处，且由包名派生（不许写死）",
            1,
            countOccurrences(body, "\"\${context.packageName}.fileprovider\""),
        )
    }

    // ------------------------------------------------------------------
    // ExportHooks.ExporterState.exportAndShare —— 通用 JSON 导出的分享入口
    // ------------------------------------------------------------------

    @Test
    fun `exportAndShare body resolves to real content`() {
        val body = functionBody(exportHooksPath, "fun exportAndShare(")
        assertTrue(
            "exportAndShare 函数体必须含有它自己的正面内容（createChooser）",
            body.contains("Intent.createChooser("),
        )
    }

    @Test
    fun `exportAndShare builds exactly one ACTION_SEND json intent with stream and read grant`() {
        val body = functionBody(exportHooksPath, "fun exportAndShare(")

        assertEquals(
            "exportAndShare 必须恰好构造一个 ACTION_SEND",
            1,
            countOccurrences(body, "Intent(Intent.ACTION_SEND)"),
        )
        assertEquals(
            "exportAndShare 必须恰好设置一次 application/json",
            1,
            countOccurrences(body, "type = \"application/json\""),
        )
        assertEquals(
            "exportAndShare 必须恰好塞一次 EXTRA_STREAM",
            1,
            countOccurrences(body, "putExtra(Intent.EXTRA_STREAM, uri)"),
        )
        assertEquals(
            "exportAndShare 必须恰好加一次 FLAG_GRANT_READ_URI_PERMISSION",
            1,
            countOccurrences(body, "addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)"),
        )
        assertEquals(
            "exportAndShare 必须恰好一处按包名派生的 FileProvider authority",
            1,
            countOccurrences(body, "\"\${context.packageName}.fileprovider\""),
        )
        assertTrue("分享必须包一层 createChooser", body.contains("Intent.createChooser(intent, null)"))
    }
}
