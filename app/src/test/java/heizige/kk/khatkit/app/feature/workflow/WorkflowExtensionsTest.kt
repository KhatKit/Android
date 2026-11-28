package heizige.kk.khatkit.app.feature.workflow

import heizige.kk.khatkit.app.core.data.ai.tools.parseFlowSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import heizige.kk.khatkit.app.feature.workflow.data.VisualWorkflow
import heizige.kk.khatkit.app.feature.workflow.data.VisualNode
import heizige.kk.khatkit.app.feature.workflow.data.WORKFLOW_TRIGGER_EVENTS
import heizige.kk.khatkit.app.feature.workflow.data.WORKFLOW_FLOW_STEP_LIMIT
import heizige.kk.khatkit.app.feature.workflow.helper.executeWorkflow
import heizige.kk.khatkit.app.feature.workflow.helper.exportWorkflowLua
import heizige.kk.khatkit.app.feature.workflow.helper.interpretWorkflowScript
import heizige.kk.khatkit.app.feature.workflow.helper.workflowFlowSpecJson
import heizige.kk.khatkit.app.feature.workflow.helper.workflowCardName
import heizige.kk.khatkit.app.feature.workflow.helper.notificationClassifySample
import heizige.kk.khatkit.app.feature.workflow.helper.linearClassifySample
import heizige.kk.khatkit.app.feature.workflow.helper.repeatNotifySample
import heizige.kk.khatkit.app.core.data.ai.tools.FlowSpec



class WorkflowExtensionsTest {
    @Test
    fun `trigger events match the existing 18 sources`() {
        assertEquals(18, WORKFLOW_TRIGGER_EVENTS.size)
        assertEquals(18, WORKFLOW_TRIGGER_EVENTS.toSet().size)
        assertTrue("manual" !in WORKFLOW_TRIGGER_EVENTS)
    }

    @Test
    fun `linear flow matches exported script and FlowSpec`() {
        assertEquivalent(linearClassifySample(), mapOf("classification" to "urgent", "event" to "notification"))
    }

    @Test
    fun `notification branch takes reply when urgent`() {
        val graph = notificationClassifySample()
        val input = mapOf("classification" to "urgent", "event" to "notification")
        assertEquivalent(graph, input)
        val ran = executeWorkflow(graph, input).successes().map { it.nodeId }
        assertEquals(listOf("t1", "llm1", "c1", "n1"), ran)
    }

    @Test
    fun `notification branch writes a file otherwise`() {
        val graph = notificationClassifySample()
        val input = mapOf("classification" to "other", "event" to "notification")
        assertEquivalent(graph, input)
        val ran = executeWorkflow(graph, input).successes().map { it.nodeId }
        assertEquals(listOf("t1", "llm1", "c1", "f1"), ran)
    }

    @Test
    fun `loop repeats the body three times in both representations`() {
        val graph = repeatNotifySample()
        assertEquivalent(graph, mapOf("event" to "manual"))
        val bodies = executeWorkflow(graph).successes().filter { it.nodeId == "body1" }
        assertEquals(listOf("1", "2", "3"), bodies.map { it.output["i"] })
    }

    @Test
    fun `cycle is rejected and a failed node skips downstream`() {
        val cycle = VisualWorkflow(
            id = "cycle",
            name = "cycle",
            nodes = listOf(
                VisualNode("a", "delay", params = mapOf("ms" to "1"), dependsOn = listOf("b")),
                VisualNode("b", "delay", params = mapOf("ms" to "1"), dependsOn = listOf("a")),
            ),
        )
        assertEquals("failed", executeWorkflow(cycle).status)
        assertEquals("cycle", executeWorkflow(cycle).error)

        val linear = linearClassifySample()
        val failed = executeWorkflow(linear, failAt = "llm1")
        assertEquals("failed", failed.steps.first { it.nodeId == "llm1" }.status)
        assertEquals("skipped", failed.steps.first { it.nodeId == "n1" }.status)
        assertEquals("success", failed.steps.first { it.nodeId == "t1" }.status)
    }

    @Test
    fun `resume skips nodes that already succeeded and cancel stops the rest`() {
        val linear = linearClassifySample()
        val resumed = executeWorkflow(linear, skipSuccess = setOf("t1"))
        assertEquals("true", resumed.steps.first { it.nodeId == "t1" }.output["resumed"])
        assertEquals("success", resumed.steps.first { it.nodeId == "llm1" }.status)
        assertTrue(resumed.successes().none { it.nodeId == "t1" })

        val cancelled = executeWorkflow(linear, cancelAfter = "t1")
        assertEquals("cancelled", cancelled.status)
        assertEquals("cancelled", cancelled.steps.first { it.nodeId == "n1" }.status)
    }

    @Test
    fun `unknown trigger event is rejected`() {
        val graph = VisualWorkflow(
            id = "bad",
            name = "bad",
            nodes = listOf(VisualNode("t1", "trigger", params = mapOf("event" to "nope"))),
        )
        assertEquals("failed", executeWorkflow(graph).status)
    }

    private fun assertEquivalent(graph: VisualWorkflow, input: Map<String, String>) {
        val executed = executeWorkflow(graph, input)
        val interpreted = interpretWorkflowScript(exportWorkflowLua(graph), input)
        assertEquals("completed", executed.status)
        assertEquals(
            executed.successes().map { it.nodeId to it.output },
            interpreted.map { it.nodeId to it.output },
        )
        val spec = parseFlowSpec(workflowFlowSpecJson(executed))
        assertNotNull(spec)
        assertTrue(spec!!.steps.size <= WORKFLOW_FLOW_STEP_LIMIT)
        assertEquals(
            executed.successes().mapNotNull { workflowCardName(it) },
            spec.steps.map { it.card },
        )
    }
}
