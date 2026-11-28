package heizige.kk.khatkit.app

import androidx.activity.BackEventCompat
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageBackInterceptorTest {
    @Test
    fun predictiveOverlayIsScopedAndUsesTheRegisteredHandler() = runBlocking {
        val token = Any()
        var predictiveCalled = false
        try {
            PageBackInterceptor.pushPredictive(
                token = token,
                ownerRoute = "chat",
                handler = { true },
                predictiveHandler = { events ->
                    events.collect {}
                    predictiveCalled = true
                },
            )

            assertTrue(PageBackInterceptor.hasHandlersFor("chat"))
            assertTrue(PageBackInterceptor.hasPredictiveHandlersFor("chat"))
            assertFalse(PageBackInterceptor.hasPredictiveHandlersFor("settings"))
            assertTrue(PageBackInterceptor.interceptFor("chat"))
            assertTrue(
                PageBackInterceptor.interceptPredictiveFor(
                    route = "chat",
                    events = emptyFlow<BackEventCompat>(),
                ),
            )
            assertTrue(predictiveCalled)
        } finally {
            PageBackInterceptor.pop(token)
        }

        assertFalse(PageBackInterceptor.hasPredictiveHandlersFor("chat"))
    }

    @Test
    fun mostRecentlyRegisteredPredictiveOverlayWins() = runBlocking {
        val first = Any()
        val second = Any()
        var firstCalled = false
        var secondCalled = false
        try {
            PageBackInterceptor.pushPredictive(
                token = first,
                ownerRoute = "chat",
                handler = { true },
                predictiveHandler = { events -> events.collect {}; firstCalled = true },
            )
            PageBackInterceptor.pushPredictive(
                token = second,
                ownerRoute = "chat",
                handler = { true },
                predictiveHandler = { events -> events.collect {}; secondCalled = true },
            )

            assertTrue(PageBackInterceptor.interceptPredictiveFor("chat", emptyFlow()))
            assertTrue(secondCalled)
            assertFalse(firstCalled)
        } finally {
            PageBackInterceptor.pop(first)
            PageBackInterceptor.pop(second)
        }
    }

    @Test
    fun overlaysRegisteredOnAnotherRouteAreNotIntercepted() = runBlocking {
        val token = Any()
        var called = false
        try {
            PageBackInterceptor.pushPredictive(
                token = token,
                ownerRoute = "settings",
                handler = { true },
                predictiveHandler = { events -> events.collect {}; called = true },
            )

            assertFalse(PageBackInterceptor.hasHandlersFor("chat"))
            assertFalse(PageBackInterceptor.hasPredictiveHandlersFor("chat"))
            assertFalse(PageBackInterceptor.interceptPredictiveFor("chat", emptyFlow()))
            assertFalse(PageBackInterceptor.interceptFor("chat"))
            assertFalse(called)
        } finally {
            PageBackInterceptor.pop(token)
        }
    }
}
