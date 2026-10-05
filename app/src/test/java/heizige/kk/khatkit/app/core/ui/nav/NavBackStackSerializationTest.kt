package heizige.kk.khatkit.app.core.ui.nav

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import heizige.kk.khatkit.app.Screen
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializerOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

/**
 * 导航栈持久化的序列化往返测试。
 *
 * `NavViewModel` 把整条栈以 JSON 字符串存进 `SavedStateHandle`，进程被杀后靠它恢复。
 * 这里用到的 `NavKeySerializer` 是靠 `Class.forName` 反射定位具体 `Screen` 的
 * serializer，所以「某个 Screen 能不能被正确写出/读回」是这套方案唯一容易出错的地方，
 * 而且一旦漏掉就是**静默**退回起始页，所以单独钉一个测试。
 */
class NavBackStackSerializationTest {

    private val serializer = NavBackStackSerializer(elementSerializer = NavKeySerializer<NavKey>())

    @Test
    fun roundTripsMixedScreenEntries() {
        val original = NavBackStack<NavKey>(
            Screen.Greeting,
            Screen.Chat(id = "conv-1", text = "你好", files = listOf("a.png", "b.pdf"), nodeId = "n7"),
            Screen.SettingProviderDetail(providerId = "provider-9"),
            Screen.WorkspaceFileEditor(id = "ws-1", area = "main", path = "/tmp/a.txt"),
        )

        val encoded = Json.encodeToString(serializer, original)
        val decoded = Json.decodeFromString(serializer, encoded)

        assertEquals(original.toList(), decoded.toList())
    }

    @Test
    fun roundTripsSingleEntry() {
        val original = NavBackStack<NavKey>(Screen.Setting)

        val encoded = Json.encodeToString(serializer, original)
        val decoded = Json.decodeFromString(serializer, encoded)

        assertEquals(listOf<NavKey>(Screen.Setting), decoded.toList())
    }

    @Test
    fun roundTripsDeepStackOfSameType() {
        // 连续压同一个 Screen 的多个实例：键重复但参数不同，用来确认不是按「类型+单例」
        // 去重，而是每个实例的参数都进了 JSON。
        val original = NavBackStack<NavKey>(
            Screen.Chat(id = "1"),
            Screen.Chat(id = "2"),
            Screen.Chat(id = "3"),
        )

        val encoded = Json.encodeToString(serializer, original)
        val decoded = Json.decodeFromString(serializer, encoded)

        assertEquals(listOf<NavKey>(Screen.Chat("1"), Screen.Chat("2"), Screen.Chat("3")), decoded.toList())
    }

    @Test
    fun optionalArgsUseDefaultsAfterRestore() {
        // Chat 的 text / files / nodeId 都是带默认值的可选参数。恢复后必须回到默认值形态，
        // 而不是 null —— Model 里这几个字段参与相等判断和序列化。
        val original = NavBackStack<NavKey>(Screen.Chat(id = "only-id"))
        val encoded = Json.encodeToString(serializer, original)
        val decoded = Json.decodeFromString(serializer, encoded)

        val restored = decoded.last()
        assertTrue(restored is Screen.Chat)
        val chat = restored as Screen.Chat
        assertEquals("only-id", chat.id)
        assertEquals(null, chat.text)
        assertEquals(emptyList<String>(), chat.files)
        assertEquals(null, chat.nodeId)
    }

    /**
     * 防漏注解：`Screen` 的**每一个**成员都必须自带 serializer。
     *
     * 背景：`NavKey` 没标 `@Serializable`，`Screen` 也没有，所以继承不到任何兜底 ——
     * 少标一个 `@Serializable` 的成员，`persist()` 就会在 `NavKeySerializer` 里找不到它的
     * serializer，抛 `SerializationException`。这条异常发生在 `snapshotFlow` 回调里、
     * 主线程且无人接，于是「进那个页面必崩」，而单测一片绿（之前只覆盖了 5 个成员）。
     *
     * 所以这里不写死成员清单，直接反射遍历 `sealedSubclasses`，新增成员自动纳入检查。
     */
    @OptIn(kotlinx.serialization.InternalSerializationApi::class)
    @Test
    fun everyScreenMemberHasAWorkingSerializer() {
        val members = collectScreenMembers(Screen::class)
        assertTrue(
            "Screen 的成员遍历结果为空，反射没生效，测试形同虚设",
            members.size >= MIN_EXPECTED_SCREEN_MEMBERS
        )

        val missing = mutableListOf<String>()
        val broken = mutableListOf<String>()

        for (member in members) {
            val name = member.simpleName ?: member.java.name

            @Suppress("UNCHECKED_CAST")
            val serializerOrNull = member.serializerOrNull() as kotlinx.serialization.KSerializer<Any>?
            if (serializerOrNull == null) {
                missing += "$name（缺 @Serializable，会在 persist() 时抛 SerializationException）"
                continue
            }

            val instance = sampleInstance(member) ?: continue

            try {
                val encoded = Json.encodeToString(serializerOrNull, instance)
                val decoded = Json.decodeFromString(serializerOrNull, encoded)
                if (decoded != instance) {
                    broken += "$name（往返不相等：$instance → $encoded → $decoded）"
                }
            } catch (t: Throwable) {
                broken += "$name（${t::class.java.simpleName}: ${t.message}）"
            }
        }

        assertEquals(
            "以下 Screen 成员缺少 @Serializable：\n" + missing.joinToString("\n"),
            emptyList<String>(),
            missing
        )
        assertEquals(
            "以下 Screen 成员序列化往返失败：\n" + broken.joinToString("\n"),
            emptyList<String>(),
            broken
        )
    }

    /** 递归收集 sealed 子类：成员本身也可能是 sealed 的。 */
    private fun collectScreenMembers(root: KClass<*>): List<KClass<*>> {
        val result = mutableListOf<KClass<*>>()
        val queue = ArrayDeque<KClass<*>>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (sub in current.sealedSubclasses) {
                if (result.none { it == sub }) {
                    result += sub
                    queue.add(sub)
                }
            }
        }
        return result
    }

    /**
     * 造一个用于往返测试的实例：`data object` 直接取单例，`data class` 按构造参数填样本值。
     * 参数类型超出 [sampleValue] 覆盖范围时返回 null —— 只跳过往返，不跳过 serializer 存在性检查。
     */
    private fun sampleInstance(member: KClass<*>): Any? {
        member.objectInstance?.let { return it }
        val constructor = member.primaryConstructor ?: return null
        val args = constructor.parameters.associate { parameter ->
            parameter to sampleValue(parameter.type, parameter.name ?: "arg")
        }
        if (args.values.any { it == null }) return null
        return try {
            constructor.callBy(args)
        } catch (t: Throwable) {
            null
        }
    }

    private fun sampleValue(type: KType, name: String): Any? {
        val classifier = type.classifier
        return when (classifier) {
            String::class -> "sample-$name"
            Int::class -> 1
            Long::class -> 1L
            Boolean::class -> true
            Double::class -> 1.0
            Float::class -> 1.0f
            List::class -> {
                val element = type.arguments.firstOrNull()?.type
                if (element == null) emptyList<String>() else listOf(sampleValue(element, name))
            }
            else -> null
        }
    }

    private companion object {
        /** Screen 当前 57 个成员；只做下限断言，新增成员不会误报。 */
        const val MIN_EXPECTED_SCREEN_MEMBERS = 50
    }
}