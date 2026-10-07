package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.app.core.data.model.AssistantMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C1-08 结构性串扰判据的**离线判别力证明**（纯 JVM，零仪器）。
 *
 * ## 为什么要这份镜像
 *
 * 判据本体 `memoryIsolationViolations` 定义在 `androidTest` 的
 * `C1LiveModelSequenceTest.kt`（设备上跑、操作真实 Room 产物）。`test` 与 `androidTest`
 * 两个 source set 无法互相 import，所以这里保留一份**逐字镜像**，用来离线复算：
 * 把「检索结果混入另一角色 chunk」的输入喂进去证明它**会红**，正常输入证明它**会绿**。
 * 镜像与本体函数体完全一致；本文件不 import 仪器运行时的任何东西。
 *
 * ## 背景（被替换的空泛断言）
 *
 * 真实网关会重写正文、丢掉 `C1RGWA/B/C` 这类哨兵串，于是旧判据
 * `roles.none { other -> chunk.content.contains(tokens[other]) }` 对三角色恒真。
 * [vacuousOldTokenPredicateIsBlindToRewrittenForeignChunk] 用一条「正文已被改写、
 * 不含任何哨兵、但确属他人」的 chunk 直接演示旧判据看不见它，而新判据抓得住。
 */
class C1MemoryIsolationPredicateTest {

    private val spaceA = "group:conv-1:role:a"
    private val spaceB = "group:conv-1:role:b"
    private val userMsg = "msg-user"
    private val aMsg = "msg-a"
    private val bMsg = "msg-b"

    /** viewer a 的可见窗口：触发消息 + 自己的消息（pipeline 下别人的发言不可见）。 */
    private val aViewerMessages = setOf(userMsg, aMsg)
    private val ownMessageIdByRole = mapOf("a" to aMsg, "b" to bMsg)

    private fun aOwnChunk() = AssistantMemory(
        id = 1,
        content = "角色甲最喜欢的城市是杭州，他计划明年搬去那里长期居住。", // 已被改写、不含 C1RGWA
        spaceId = spaceA,
        sourceMessageId = aMsg,
        roleId = "a",
    )

    private fun foreignChunkRewritten() = AssistantMemory(
        id = 2,
        content = "角色乙养了一只名叫团子的橘猫，已经三岁了。", // 已被改写、不含 C1RGWB
        spaceId = spaceA, // 若泄漏进 a 的空间
        sourceMessageId = bMsg,
        roleId = "b",
    )

    // ------------------------------------------------------------------
    // 绿：正常输入
    // ------------------------------------------------------------------

    @Test
    fun normalHitsPassGreen() {
        val violations = memoryIsolationViolations(
            viewerRoleId = "a",
            expectedSpaceId = spaceA,
            viewerMessageIds = aViewerMessages,
            hits = listOf(aOwnChunk()),
            ownMessageIdByRole = ownMessageIdByRole,
            foreignSpaceHitCount = 0,
        )
        assertTrue("正常输入必须无违规，实际=$violations", violations.isEmpty())
    }

    // ------------------------------------------------------------------
    // 红：变异输入
    // ------------------------------------------------------------------

    @Test
    fun foreignRoleChunkInHitsFailsRed() {
        val hit = foreignChunkRewritten()
        val violations = memoryIsolationViolations(
            viewerRoleId = "a",
            expectedSpaceId = spaceA,
            viewerMessageIds = aViewerMessages,
            hits = listOf(aOwnChunk(), hit),
            ownMessageIdByRole = ownMessageIdByRole,
        )
        assertFalse("混入 role_id=b 的 chunk 必须判红", violations.isEmpty())
        assertTrue(
            "必须点名 role_id 跨角色：$violations",
            violations.any { it.contains("role_id=b") && it.contains("跨角色串扰") },
        )
        assertTrue(
            "必须点名 source 指向角色 b 自己的消息：$violations",
            violations.any { it.contains("角色 b 自己的消息") },
        )
        assertTrue(
            "必须点名 source 不在 a 的可见窗口内：$violations",
            violations.any { it.contains("不在 viewer=a 的可见窗口内") },
        )
    }

    @Test
    fun wrongSpaceIdFailsRed() {
        val violations = memoryIsolationViolations(
            viewerRoleId = "a",
            expectedSpaceId = spaceA,
            viewerMessageIds = aViewerMessages,
            hits = listOf(aOwnChunk().copy(spaceId = spaceB)),
            ownMessageIdByRole = ownMessageIdByRole,
        )
        assertTrue(
            "空间键不符必须判红：$violations",
            violations.any { it.contains("空间键=$spaceB") },
        )
    }

    @Test
    fun sourceOutsideViewerWindowFailsRed() {
        // role_id 仍写成 a，但 source 指向 b 的消息 —— 结构性串扰的隐蔽形态。
        val violations = memoryIsolationViolations(
            viewerRoleId = "a",
            expectedSpaceId = spaceA,
            viewerMessageIds = aViewerMessages,
            hits = listOf(aOwnChunk().copy(sourceMessageId = bMsg)),
            ownMessageIdByRole = ownMessageIdByRole,
        )
        assertTrue(
            "source 越界必须判红：$violations",
            violations.any { it.contains("不在 viewer=a 的可见窗口内") },
        )
    }

    @Test
    fun nullSourceFailsRed() {
        val violations = memoryIsolationViolations(
            viewerRoleId = "a",
            expectedSpaceId = spaceA,
            viewerMessageIds = aViewerMessages,
            hits = listOf(aOwnChunk().copy(sourceMessageId = null)),
            ownMessageIdByRole = ownMessageIdByRole,
        )
        assertTrue("source 缺失必须判红：$violations", violations.any { it.contains("source_message_id=null") })
    }

    @Test
    fun globalFallbackHitFailsRed() {
        val violations = memoryIsolationViolations(
            viewerRoleId = "a",
            expectedSpaceId = spaceA,
            viewerMessageIds = aViewerMessages,
            hits = listOf(aOwnChunk()),
            ownMessageIdByRole = ownMessageIdByRole,
            foreignSpaceHitCount = 1,
        )
        assertTrue("全局/助手空间回退命中必须判红：$violations", violations.any { it.contains("回退命中") })
    }

    // ------------------------------------------------------------------
    // 对照：旧判据为什么空泛
    // ------------------------------------------------------------------

    @Test
    fun vacuousOldTokenPredicateIsBlindToRewrittenForeignChunk() {
        val tokens = mapOf("a" to "C1RGWA", "b" to "C1RGWB", "c" to "C1RGWC")
        val foreign = foreignChunkRewritten()
        // 旧判据：正文里不含**他人**哨兵即「通过」。真实模型已把 C1RGWB 改写掉 ⇒ 恒真。
        val oldPredicateSaysOk = tokens.filterKeys { it != "a" }.values
            .none { token -> foreign.content.contains(token) }
        assertTrue("空泛演示：旧判据对已改写的他人 chunk 判为通过（绿）", oldPredicateSaysOk)
        // 新判据：同一份输入判红。
        val violations = memoryIsolationViolations(
            viewerRoleId = "a",
            expectedSpaceId = spaceA,
            viewerMessageIds = aViewerMessages,
            hits = listOf(foreign),
            ownMessageIdByRole = ownMessageIdByRole,
        )
        assertFalse("结构性判据必须判红（判别力），实际=$violations", violations.isEmpty())
        assertEquals("必须至少报出跨角色 role_id", true, violations.any { it.contains("role_id=b") })
    }
}

/**
 * 与 `androidTest/.../C1LiveModelSequenceTest.kt` 里 `memoryIsolationViolations` **逐字一致**
 * 的镜像（见文件头注释）。函数体若改动，两处必须同步。
 */
internal fun memoryIsolationViolations(
    viewerRoleId: String,
    expectedSpaceId: String,
    viewerMessageIds: Set<String>,
    hits: List<AssistantMemory>,
    ownMessageIdByRole: Map<String, String> = emptyMap(),
    foreignSpaceHitCount: Int = 0,
): List<String> {
    val violations = mutableListOf<String>()
    if (foreignSpaceHitCount != 0) {
        violations += "全局/助手空间出现 $foreignSpaceHitCount 条回退命中（无回退契约）"
    }
    hits.forEachIndexed { index, hit ->
        if (hit.spaceId != expectedSpaceId) {
            violations += "命中[$index] 空间键=${hit.spaceId} 不等于期望=$expectedSpaceId"
        }
        if (hit.roleId != viewerRoleId) {
            violations += "命中[$index] role_id=${hit.roleId} 不等于 viewer=$viewerRoleId（跨角色串扰）"
        }
        val source = hit.sourceMessageId
        if (source == null || source !in viewerMessageIds) {
            violations += "命中[$index] source_message_id=$source 不在 viewer=$viewerRoleId 的可见窗口内"
        }
        ownMessageIdByRole.forEach { (other, mid) ->
            if (other != viewerRoleId && source != null && source == mid) {
                violations += "命中[$index] source_message_id=$source 指向角色 $other 自己的消息（结构性串扰）"
            }
        }
    }
    return violations
}
