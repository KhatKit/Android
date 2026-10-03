package heizige.kk.khatkit.app.feature.workflow

import heizige.kk.khatkit.app.core.data.ai.tools.parseFlowSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowEngineTest {
    @Test
    fun `trigger events match the existing 18 sources`() {
        assertEquals(18, WORKFLOW_TRIGGER_EVENTS.size)
        assertEquals(18, WORKFLOW_TRIGGER_EVENTS.toSet().size)
        assertTrue("manual" !in WORKFLOW_TRIGGER_EVENTS)
    }

    @Test
    fun `linear flow matches exported script and FlowSpec`() {
        assertEquivalent(WorkflowSamples.linearClassify(), mapOf("classification" to "urgent", "event" to "notification"))
    }

    @Test
    fun `notification branch takes reply when urgent`() {
        val graph = WorkflowSamples.notificationClassify()
        val input = mapOf("classification" to "urgent", "event" to "notification")
        assertEquivalent(graph, input)
        val ran = WorkflowEngine.execute(graph, input).successes().map { it.nodeId }
        assertEquals(listOf("t1", "llm1", "c1", "n1"), ran)
    }

    @Test
    fun `notification branch writes a file otherwise`() {
        val graph = WorkflowSamples.notificationClassify()
        val input = mapOf("classification" to "other", "event" to "notification")
        assertEquivalent(graph, input)
        val ran = WorkflowEngine.execute(graph, input).successes().map { it.nodeId }
        assertEquals(listOf("t1", "llm1", "c1", "f1"), ran)
    }

    @Test
    fun `loop repeats the body three times in both representations`() {
        val graph = WorkflowSamples.repeatNotify()
        assertEquivalent(graph, mapOf("event" to "manual"))
        val bodies = WorkflowEngine.execute(graph).successes().filter { it.nodeId == "body1" }
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
        assertEquals("failed", WorkflowEngine.execute(cycle).status)
        assertEquals("cycle", WorkflowEngine.execute(cycle).error)

        val linear = WorkflowSamples.linearClassify()
        val failed = WorkflowEngine.execute(linear, failAt = "llm1")
        assertEquals("failed", failed.steps.first { it.nodeId == "llm1" }.status)
        assertEquals("skipped", failed.steps.first { it.nodeId == "n1" }.status)
        assertEquals("success", failed.steps.first { it.nodeId == "t1" }.status)
    }

    @Test
    fun `resume skips nodes that already succeeded and cancel stops the rest`() {
        val linear = WorkflowSamples.linearClassify()
        val resumed = WorkflowEngine.execute(linear, skipSuccess = setOf("t1"))
        assertEquals("true", resumed.steps.first { it.nodeId == "t1" }.output["resumed"])
        assertEquals("success", resumed.steps.first { it.nodeId == "llm1" }.status)
        assertTrue(resumed.successes().none { it.nodeId == "t1" })

        val cancelled = WorkflowEngine.execute(linear, cancelAfter = "t1")
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
        assertEquals("failed", WorkflowEngine.execute(graph).status)
    }

    private fun assertEquivalent(graph: VisualWorkflow, input: Map<String, String>) {
        val executed = WorkflowEngine.execute(graph, input)
        val interpreted = WorkflowEngine.interpret(WorkflowEngine.exportLua(graph), input)
        assertEquals("completed", executed.status)
        assertEquals(
            executed.successes().map { it.nodeId to it.output },
            interpreted.map { it.nodeId to it.output },
        )
        val spec = parseFlowSpec(WorkflowEngine.flowSpecJson(executed))
        assertNotNull(spec)
        assertTrue(spec!!.steps.size <= WORKFLOW_FLOW_STEP_LIMIT)
        assertEquals(
            executed.successes().mapNotNull { WorkflowEngine.cardName(it) },
            spec.steps.map { it.card },
        )
    }
}
