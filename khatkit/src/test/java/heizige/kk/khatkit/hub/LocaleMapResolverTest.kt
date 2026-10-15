package heizige.kk.khatkit.hub

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §3.3 四级选择顺序：完整标签（zh-CN）→ 语言（zh）→ en → 首个非空。
 * 四条路径各一个用例，外加纯字符串形态与大小写/下划线宽容。
 */
class LocaleMapResolverTest {

    /** 路径 1：完整语言标签命中。 */
    @Test
    fun `level1 full tag wins`() {
        val map = LocaleMap(
            linkedMapOf(
                "zh-CN" to "深度研究",
                "zh" to "简体研究",
                "en" to "Deep Research",
            ),
        )
        assertEquals("深度研究", LocaleMapResolver.resolve(map, listOf("zh-CN", "zh")))
    }

    /** 路径 2：完整标签缺失时按语言命中（zh-CN 请求命中 zh 键）。 */
    @Test
    fun `level2 language wins when full tag missing`() {
        val map = LocaleMap(linkedMapOf("zh" to "简体研究", "en" to "Deep Research"))
        assertEquals("简体研究", LocaleMapResolver.resolve(map, listOf("zh-CN", "zh")))
        // 服务端写 zh-Hans 时同样按语言前缀命中
        val variants = LocaleMap(linkedMapOf("zh-Hans" to "简体", "en" to "Deep Research"))
        assertEquals("简体", LocaleMapResolver.resolve(variants, listOf("zh-CN")))
    }

    /** 路径 3：当前语言没有时回退 en。 */
    @Test
    fun `level3 english fallback`() {
        val map = LocaleMap(linkedMapOf("ja" to "深い研究", "en" to "Deep Research"))
        assertEquals("Deep Research", LocaleMapResolver.resolve(map, listOf("fr-FR", "fr")))
        // en-US 这类更长键也算 en
        val variants = LocaleMap(linkedMapOf("ja" to "深い研究", "en-US" to "Deep Research"))
        assertEquals("Deep Research", LocaleMapResolver.resolve(variants, listOf("fr-FR")))
    }

    /** 路径 4：既没有当前语言也没有 en 时取第一个非空值（服务端默认值）。 */
    @Test
    fun `level4 first non blank wins`() {
        val map = LocaleMap(linkedMapOf("ja" to "深い研究", "ko" to "심층 연구"))
        assertEquals("深い研究", LocaleMapResolver.resolve(map, listOf("fr-FR", "fr")))
        // 空值 / 纯空白值不参与命中
        val withBlanks = LocaleMap(linkedMapOf("fr" to "  ", "ja" to "深い研究"))
        assertEquals("深い研究", LocaleMapResolver.resolve(withBlanks, listOf("fr")))
    }

    /** 旧纯字符串形态（`String | LocaleMap`）：只能被第 4 级兜底选中。 */
    @Test
    fun `plain string falls back to unkeyed value`() {
        val json = Json { ignoreUnknownKeys = true }
        val map = json.decodeFromString(LocaleMap.serializer(), "\"联网检索和引用整理\"")
        assertEquals("联网检索和引用整理", LocaleMapResolver.resolve(map, listOf("zh-CN", "zh")))
        assertEquals("联网检索和引用整理", LocaleMapResolver.resolveJson(
            json.parseToJsonElement("\"联网检索和引用整理\""),
            listOf("zh-CN"),
        ))
    }

    /** 大小写 / 下划线宽容，以及空映射。 */
    @Test
    fun `tag matching is tolerant and empty map resolves blank`() {
        val map = LocaleMap(linkedMapOf("zh_cn" to "深度研究"))
        assertEquals("深度研究", LocaleMapResolver.resolve(map, listOf("zh-CN")))
        assertEquals("", LocaleMapResolver.resolve(null, listOf("zh-CN")))
        assertEquals("", LocaleMapResolver.resolve(LocaleMap(linkedMapOf("en" to " ")), listOf("zh-CN")))
        assertTrue(LocaleMap.Empty.isEmpty)
    }

    /** 序列化往返：单一纯字符串写回字符串，多语言写对象。 */
    @Test
    fun `serialization keeps string and map shapes`() {
        val json = Json { ignoreUnknownKeys = true }
        val plain = LocaleMap.of("hello")
        assertEquals("\"hello\"", json.encodeToString(LocaleMap.serializer(), plain))
        val multi = LocaleMap(linkedMapOf("zh-CN" to "你好", "en" to "hello"))
        assertEquals("{\"zh-CN\":\"你好\",\"en\":\"hello\"}", json.encodeToString(LocaleMap.serializer(), multi))
        assertEquals("你好", LocaleMapResolver.resolve(
            json.decodeFromString(LocaleMap.serializer(), "{\"zh-CN\":\"你好\"}"),
            listOf("zh-CN"),
        ))
    }
}
