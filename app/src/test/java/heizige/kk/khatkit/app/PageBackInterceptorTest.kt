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
}
