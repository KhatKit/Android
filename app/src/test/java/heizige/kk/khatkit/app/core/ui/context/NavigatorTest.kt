package heizige.kk.khatkit.app.core.ui.context

import androidx.navigation3.runtime.NavKey
import heizige.kk.khatkit.app.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigatorTest {
    @Test
    fun reopeningChatFromHistoryUsesHostReplacementEvenForIdenticalArguments() {
        val chat = Screen.Chat("chat-1", nodeId = "node", messageId = "message")
        val previousStack = mutableListOf<NavKey>(chat, Screen.History)
        val selections = mutableListOf<Screen>()
        val navigator = Navigator(previousStack) { selections.add(it) }

        repeat(2) { navigator.clearAndNavigate(chat) }

        assertEquals(listOf(chat, chat), selections)
        assertEquals(listOf(chat, Screen.History), previousStack)
    }

    @Test
    fun repeatedNavigationToTopDoesNotAddEntries() {
        val stack = mutableListOf<NavKey>(Screen.Chat("chat-1"))
        val navigator = Navigator(stack)

        repeat(5) { navigator.navigate(Screen.Setting) }

        assertEquals(listOf(Screen.Chat("chat-1"), Screen.Setting), stack)
    }

    @Test
    fun sameScreenWithDifferentArgumentsStillNavigates() {
        val stack = mutableListOf<NavKey>(Screen.Chat("chat-1"))
        val navigator = Navigator(stack)

        navigator.navigate(Screen.Chat("chat-2"))
        navigator.navigate(Screen.Chat("chat-2"))

        assertEquals(listOf(Screen.Chat("chat-1"), Screen.Chat("chat-2")), stack)
    }

    @Test
    fun navigationToEarlierEntryStillPushes() {
        val stack = mutableListOf<NavKey>(Screen.Setting, Screen.SettingProvider)
        val navigator = Navigator(stack)

        navigator.navigate(Screen.Setting)

        assertEquals(listOf(Screen.Setting, Screen.SettingProvider, Screen.Setting), stack)
    }

    @Test
    fun popUpToDoesNotDuplicateTheRevealedDestination() {
        val stack = mutableListOf<NavKey>(
            Screen.Chat("chat-1"), Screen.Setting, Screen.SettingProvider,
        )
        val navigator = Navigator(stack)

        navigator.navigate(Screen.Setting) {
            popUpTo(Screen.Setting)
        }

        assertEquals(listOf(Screen.Chat("chat-1"), Screen.Setting), stack)
    }

    @Test
    fun inclusivePopUpToRunsEvenWhenDestinationIsAlreadyOnTop() {
        val stack = mutableListOf<NavKey>(
            Screen.Chat("chat-1"), Screen.Setting, Screen.SettingProvider,
        )
        val navigator = Navigator(stack)

        navigator.navigate(Screen.SettingProvider) {
            popUpTo(Screen.Setting) { inclusive = true }
        }

        assertEquals(listOf(Screen.Chat("chat-1"), Screen.SettingProvider), stack)
    }

    @Test
    fun destinationCanBeOpenedAgainAfterGoingBack() {
        val stack = mutableListOf<NavKey>(Screen.Chat("chat-1"))
        val navigator = Navigator(stack)

        navigator.navigate(Screen.Setting)
        navigator.popBackStack()
        navigator.navigate(Screen.Setting)

        assertEquals(listOf(Screen.Chat("chat-1"), Screen.Setting), stack)
    }
}
