package heizige.kk.khatkit.bridge

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 宿主接口文档的同步守卫。
 *
 * `docs/script-api-reference.md` 是「宿主提供给脚本的全部接口」的唯一清单，
 * 本测试把它与 [Bridges.kt] 的真实声明双向比对：
 *
 * 1. 代码里每个 bridge 方法都必须在文档里出现（形如 `tool.readText(`）；
 * 2. 文档里出现的 `bridge.method(` 必须真实存在（防止改了名却没同步文档）。
 *
 * 任何一侧漏改都会让本测试失败，因此文档不会随代码漂移。
 */
class BridgeApiDocTest {

    /** 接口声明 → 脚本侧 bridge 名（动态依赖包注入的 bridge 不在此列）。 */
    private val bridges = mapOf(
        ToolBridge::class.java to "tool",
        NetBridge::class.java to "net",
        FsBridge::class.java to "fs",
        JsonBridge::class.java to "json",
        CryptoBridge::class.java to "crypto",
        TimeBridge::class.java to "time",
        HostBridge::class.java to "host",
        SystemBridge::class.java to "system",
        AiBridge::class.java to "ai",
        MediaPickerBridge::class.java to "mediaPicker",
        ScheduleBridge::class.java to "schedule",
        UiBridge::class.java to "ui",
        WebBridge::class.java to "web",
        ImageToolboxBridge::class.java to "imageToolbox",
        DownloadBridge::class.java to "download",
        DownloadHandle::class.java to "handle",
        StoreBridge::class.java to "store",
        ShizukuBridge::class.java to "shizuku",
        AccessibilityBridge::class.java to "accessibility",
        RootBridge::class.java to "root",
    )

    private val sourceFile = File("src/main/java/heizige/kk/khatkit/bridge/Bridges.kt")
    private val docFile = File("../docs/script-api-reference.md")

    /** 从 Bridges.kt 里抠出 `interface Xxx { ... }` 的方法名。 */
    private fun declaredMethods(source: String, interfaceName: String): List<String> {
        val start = source.indexOf("interface $interfaceName")
        if (start < 0) return emptyList()
        val end = source.indexOf("\ninterface ", start + 1).let { if (it < 0) source.length else it }
        val body = source.substring(start, end)
        return Regex("""\n    fun (\w+)\s*\(""").findAll(body).map { it.groupValues[1] }.toList()
    }

    @Test
    fun everyBridgeMethodIsDocumented() {
        assumeTrue("Bridges.kt 不存在：${sourceFile.absolutePath}", sourceFile.exists())
        assumeTrue("接口文档不存在：${docFile.absolutePath}", docFile.exists())
        val source = sourceFile.readText()
        val doc = docFile.readText()

        val declared = bridges.flatMap { (type, prefix) ->
            declaredMethods(source, type.simpleName).map { "$prefix.$it" }
        }
        assertTrue("未能从 Bridges.kt 解析出任何方法", declared.isNotEmpty())

        val missing = declared.filterNot { "`$it(" in doc }
        assertTrue(
            "以下接口未记录在 ${docFile.name}（补文档后重跑）：\n  " + missing.joinToString("\n  "),
            missing.isEmpty(),
        )
    }

    @Test
    fun documentedApisAllExist() {
        assumeTrue("接口文档不存在：${docFile.absolutePath}", docFile.exists())
        val source = sourceFile.readText()
        val doc = docFile.readText()

        val declared = bridges.flatMap { (type, prefix) ->
            declaredMethods(source, type.simpleName).map { "$prefix.$it" }
        }.toSet()

        // 文档里的 `bridge.method(` 形态必须真实存在；反引号内的普通函数调用不算
        val documented = Regex("""`([A-Za-z]\w*)\.(\w+)\(""").findAll(doc)
            .map { "${it.groupValues[1]}.${it.groupValues[2]}" }
            .toSet()

        val unknown = (documented - declared).sorted()
        assertTrue(
            "文档里记录了代码中不存在的接口（改名后请同步文档）：\n  " + unknown.joinToString("\n  "),
            unknown.isEmpty(),
        )
    }
}
