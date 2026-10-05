package heizige.kk.khatkit.app.core.network.routes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 五个会话操作端点的群聊门禁**源码文本护栏**。
 *
 * ⚠️ **这是文本护栏，不是行为断言。** 仓库 `app` 的 testImplementation 只有 junit，
 * 没有 Ktor test host / Robolectric，`conversationRoutes(...)` 要在真机或集成环境里跑，
 * 所以 JVM 里钉不住「群聊请求真的拿到 409」。这里能钉的只有一件结构事实：
 * **每个端点的代码块里都真的写了群聊判定**。判定逻辑本身的正确性由
 * `GroupConversationOperationGuardTest` 覆盖。
 *
 * 同时钉住两条实施约束，它们都是踩过才知道的坑：
 * 1. 守卫必须排在 `initializeConversation(uuid)` **之后**。`ChatManager` 的
 *    `getConversationFlow` 走 `ConversationSessionManager.getOrCreate`，未加载的会话
 *    拿到的是 `Conversation.ofId(...)` 这个**空的单聊占位**（group_config = null）。
 *    守卫排在 initialize 之前就会读到占位对象、判定为非群聊、直接放行 —— 门禁形同虚设。
 * 2. `regenerate` 端点**不能**新增 `initializeConversation`。它今天是靠
 *    `getConversationFlow` 未命中时抛 `NotFoundException` 结束请求的；补上 initialize
 *    会让「会话存在但未加载」的单聊 regenerate 从 404 变成成功，属于改非群聊路径的行为。
 */
class GroupOperationApiGuardSourceGuardTest {

    private val source by lazy {
        File("src/main/java/heizige/kk/khatkit/app/core/network/routes/ConversationRoutes.kt").readText()
    }

    private val lines by lazy { source.split("\n") }

    /** 取某个路由声明行开始、到同缩进闭合大括号为止的整块源码。 */
    private fun routeBlock(routeLiteral: String): String {
        val start = lines.indexOfFirst { it.trimStart().startsWith(routeLiteral) }
        assertTrue("路由声明没找到：$routeLiteral", start >= 0)
        val end = lines.indexOfFirstFrom(start + 1) { it == "        }" }
        assertTrue("路由块闭合大括号没找到：$routeLiteral", end > start)
        return lines.subList(start, end + 1).joinToString("\n")
    }

    private inline fun List<String>.indexOfFirstFrom(from: Int, predicate: (String) -> Boolean): Int {
        for (index in from until size) {
            if (predicate(this[index])) return index
        }
        return -1
    }

    private val guardedEndpoints = mapOf(
        "post(\"/{id}/messages/{messageId}/edit\")" to ConversationOperation.EditMessage,
        "post(\"/{id}/fork\")" to ConversationOperation.Fork,
        "delete(\"/{id}/messages/{messageId}\")" to ConversationOperation.DeleteMessage,
        "post(\"/{id}/nodes/{nodeId}/select\")" to ConversationOperation.SelectNode,
        "post(\"/{id}/regenerate\")" to ConversationOperation.Regenerate,
    )

    @Test
    fun `every blocked endpoint guards group conversations`() {
        guardedEndpoints.forEach { (routeLiteral, operation) ->
            val block = routeBlock(routeLiteral)
            assertTrue(
                "$routeLiteral 缺少群聊门禁（群聊会话上会破坏轮次账目）",
                block.contains("requireConversationOperationAllowed("),
            )
            assertTrue(
                "$routeLiteral 的门禁没有指名是哪个操作",
                block.contains(operation.name),
            )
        }
    }

    @Test
    fun `no other endpoint gained a group guard`() {
        // 门禁是「点状」的：只有这五个端点该有。多出来的说明有人把守卫撒到了
        // messages / title / stop 这类不破坏轮次账目的端点上，那是在改非群聊路径的行为。
        val guardedRouteCount = lines.count { it.contains("requireConversationOperationAllowed(") }
        assertEquals(
            "只有五个端点该有群聊门禁",
            guardedEndpoints.size,
            guardedRouteCount,
        )
    }

    @Test
    fun `guard runs after initializeConversation so an unloaded session cannot bypass it`() {
        // regenerate 例外：它今天没有 initializeConversation（见本文件 KDoc 第 2 条），
        // 它复用的是自己已经读出来的 conversation。
        val withoutInitialize = setOf(ConversationOperation.Regenerate)
        guardedEndpoints.forEach { (routeLiteral, operation) ->
            if (operation in withoutInitialize) return@forEach
            val block = routeBlock(routeLiteral)
            val initAt = block.indexOf("initializeConversation(")
            val guardAt = block.indexOf("requireConversationOperationAllowed(")
            assertTrue("$routeLiteral 应保留 initializeConversation", initAt >= 0)
            assertTrue(
                "$routeLiteral 的群聊门禁必须排在 initializeConversation 之后，" +
                    "否则读到的是空单聊占位对象，门禁会被绕过",
                guardAt > initAt,
            )
        }
    }

    @Test
    fun `regenerate keeps guarding without gaining initializeConversation`() {
        val block = routeBlock("post(\"/{id}/regenerate\")")
        assertTrue(
            "regenerate 端点不能新增 initializeConversation：那会把「未加载会话」的单聊 " +
                "regenerate 从 404 变成成功，属于改非群聊路径行为",
            !block.contains("initializeConversation("),
        )
        // 它必须复用已经读出来的那个 conversation 变量，不能另起一次读取。
        assertEquals(
            "regenerate 只能读一次会话",
            1,
            block.split("getConversationFlow(").size - 1,
        )
    }
}