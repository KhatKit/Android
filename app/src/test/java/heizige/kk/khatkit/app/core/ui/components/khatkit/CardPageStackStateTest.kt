package heizige.kk.khatkit.app.core.ui.components.khatkit

import heizige.kk.khatkit.ui.UiNode
import heizige.kk.khatkit.ui.UiRequest
import heizige.kk.khatkit.ui.UiScreenPage
import heizige.kk.khatkit.ui.UiSheetOptions
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test

class CardPageStackStateTest {
    private fun screen(title: String, text: String, push: Boolean = false, layer: Boolean = false, root: Boolean = false) =
        UiRequest.Screen(
            title, UiNode("Text", mapOf("text" to text), emptyList()),
            UiSheetOptions(fullscreen = true, push = push, layer = layer), CompletableDeferred(),
            if (root) mapOf("home" to UiScreenPage(UiNode("Text", mapOf("text" to text), emptyList()))) else emptyMap(),
        )

    @Test fun pushedPageWithoutLayerFlagStaysInCardStack() {
        assertTrue(screen("详情", "内容", push = true).isCardPage())
    }

    @Test fun rootWithSameTitleDoesNotReplaceExitingPage() {
        val stack = CardPageStackState()
        stack.apply(screen("调色板", "首页", root = true))
        stack.apply(screen("调色板", "详情", push = true, layer = true))
        val leavingId = stack.ids.last()
        stack.apply(screen("调色板", "更新的首页", root = true))
        assertEquals(CardPageMotion.Pop, stack.motion)
        assertEquals("详情", stack.frame(leavingId)?.root?.props?.get("text"))
        assertEquals("更新的首页", stack.frame(stack.ids.first())?.root?.props?.get("text"))
        stack.finishPop()
        assertEquals(1, stack.size)
        assertNull(stack.frame(leavingId))
    }

    @Test fun submittedBackUpdatesParentWhileLeavingContentStaysFrozen() {
        val stack = CardPageStackState()
        stack.apply(screen("调色板", "首页", root = true))
        stack.apply(screen("详情", "旧详情", push = true, layer = true))
        stack.apply(screen("详情", "第三级", push = true, layer = true))
        val leavingId = stack.ids.last()
        stack.backSubmitted = true
        stack.pushPop()
        stack.apply(screen("详情", "新详情", layer = true))
        assertEquals("第三级", stack.frame(leavingId)?.root?.props?.get("text"))
        stack.finishPop()
        assertEquals(2, stack.size)
        assertEquals("新详情", stack.lastOrNull()?.root?.props?.get("text"))
    }

    @Test fun refreshingChildKeepsParentAndLayerIdentity() {
        val stack = CardPageStackState()
        stack.apply(screen("调色板", "首页", root = true))
        val parent = stack.frame(stack.ids.first())
        stack.apply(screen("设置", "原设置", push = true, layer = true))
        stack.motion = null
        val childId = stack.ids.last()
        stack.apply(screen("设置", "新设置", layer = true))
        assertEquals(2, stack.size)
        assertEquals(childId, stack.ids.last())
        assertSame(parent, stack.frame(stack.ids.first()))
        assertNull(stack.motion)
    }

    @Test fun pushDuringPopKeepsLeavingPageUntilAnimationFinishes() {
        val stack = CardPageStackState()
        stack.apply(screen("首页", "首页", root = true))
        stack.apply(screen("二级", "旧二级", push = true))
        val leavingId = stack.ids.last()
        stack.pushPop()
        stack.apply(screen("新二级", "新内容", push = true))
        assertEquals(2, stack.size)
        assertEquals("旧二级", stack.frame(leavingId)?.root?.props?.get("text"))
        stack.finishPop()
        assertEquals(2, stack.size)
        assertEquals("新二级", stack.lastOrNull()?.title)
        assertEquals(CardPageMotion.Push, stack.motion)
    }

    @Test fun repeatedBackSubmissionDoesNotSkipParent() {
        val stack = CardPageStackState()
        stack.apply(screen("首页", "首页", root = true))
        stack.apply(screen("二级", "二级", push = true, layer = true))
        stack.apply(screen("三级", "三级", push = true, layer = true))
        stack.pushPop()
        stack.pushPop()
        stack.finishPop()
        assertEquals(2, stack.size)
        assertEquals("二级", stack.lastOrNull()?.title)
    }
}
