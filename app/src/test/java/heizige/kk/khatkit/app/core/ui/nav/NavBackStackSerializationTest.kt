package heizige.kk.khatkit.app.core.ui.nav

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import heizige.kk.khatkit.app.Screen
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}