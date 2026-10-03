package heizige.kk.khatkit.app.core.ui.nav

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import heizige.kk.khatkit.app.core.ui.hooks.readBooleanPreference
import heizige.kk.khatkit.app.core.ui.hooks.readStringPreference
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

/**
 * 全局导航栈的所有者。
 *
 * ## 为什么要从 `rememberNavBackStack` 挪进 ViewModel
 *
 * 原来栈是在 [heizige.kk.khatkit.app.RouteActivity.AppRoutes] 里 `rememberNavBackStack`
 * 建的，Activity 侧要碰它只能靠一个裸字段 + `SideEffect` 回填 + 一个 `pendingIntents`
 * 队列（Intent 早于 Compose 就绪时排队等栈建好）。而且 `startScreen` 每次重组都会重算
 * 一个新的随机 UUID，只是碰巧没被消费才没出事。
 *
 * 挪进 ViewModel 之后：
 * - 栈的归属变成 Activity 级（`RouteActivity` 的 `onCreate` / `onNewIntent` 早于组合
 *   就能拿到它，Intent 分发不再需要排队）；
 * - 只剩一个真正的来源，Activity 字段和 `SideEffect` 都能删掉。
 *
 * ## 进程被杀后的恢复
 *
 * 栈会以 JSON 字符串存进 [SavedStateHandle]，所以进程死亡后能恢复，不会退回起始页。
 * 这里刻意复用 nav3 在 Android 上的同一套反射序列化器（`NavKeySerializer`），也就是
 * `rememberNavBackStack(vararg)` 内部用的那个 —— 好处是不用把 50 多个 `Screen` 子类逐个
 * 注册进 `SerializersModule`（漏注册一个就会静默丢恢复）。代价是这套序列化只在 Android
 * 上有效，本项目本来就只发 Android。
 */
@HiltViewModel
class NavViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val backStack = NavBackStack<NavKey>()

    /** 整条导航栈。内容是 `Screen`，但对外是 [NavKey] 以匹配 `NavDisplay`。 */
    val stack: NavBackStack<NavKey> get() = backStack

    /** 当前页（栈顶）。空栈时为 `null`。 */
    val currentPage: NavKey? get() = backStack.lastOrNull()

    init {
        restoreOrSeed()
        // 栈的任何变动都立刻落盘，进程被杀时能拿到最新的一条路径。
        viewModelScope.launch {
            snapshotFlow { backStack.toList() }
                .collect { persist(it) }
        }
    }

    private fun restoreOrSeed() {
        val restored = savedStateHandle.get<String>(KEY_BACK_STACK)
            ?.let { raw -> runCatching { json.decodeFromString(serializer, raw) }.getOrNull() }
        if (!restored.isNullOrEmpty()) {
            backStack.addAll(restored)
        } else {
            backStack.add(startScreen())
        }
    }

    private fun persist(screens: List<NavKey>) {
        savedStateHandle[KEY_BACK_STACK] = json.encodeToString(serializer, NavBackStack(*screens.toTypedArray()))
    }

    /** 起始页：没走过引导页就走引导页，否则进上次那个会话（或新建一个）。 */
    private fun startScreen(): NavKey {
        if (!context.readBooleanPreference("greeting_completed", false)) {
            return heizige.kk.khatkit.app.Screen.Greeting
        }
        val newId = if (context.readBooleanPreference("create_new_conversation_on_start", true)) {
            UUID.randomUUID().toString()
        } else {
            context.readStringPreference("lastConversationId", UUID.randomUUID().toString())
                ?: UUID.randomUUID().toString()
        }
        return heizige.kk.khatkit.app.Screen.Chat(id = newId)
    }

    /**
     * 压栈。已经是栈顶就不重复压 —— `ACTION_SEND` 之类的 Intent 可能被重复分发，
     * 重复压栈会让返回键多按好几次。
     */
    fun navigateTo(key: NavKey) {
        if (backStack.lastOrNull() != key) {
            backStack.add(key)
        }
    }

    fun popBackStack() {
        backStack.removeLastOrNull()
    }

    /** 引导页结束：把引导页换成会话页。 */
    fun onGreetingFinished(chatPage: NavKey) {
        backStack.add(chatPage)
        backStack.remove(heizige.kk.khatkit.app.Screen.Greeting)
    }

    /** 清空并只保留 [key]。用于「跳回首页」这类明确的替换语义。 */
    fun replaceAll(key: NavKey) {
        backStack.clear()
        backStack.add(key)
    }

    private companion object {
        const val KEY_BACK_STACK = "khatkit.nav.backStack"

        /**
         * 和 nav3 的 `rememberNavBackStack(vararg)`（Android 重载）完全一致的序列化器：
         * `NavKeySerializer` 按具体类的全限定名反射定位 serializer，所以不用手工注册
         * `Screen` 的 50 多个子类。
         */
        val serializer = NavBackStackSerializer(elementSerializer = NavKeySerializer<NavKey>())
        val json = Json
    }
}