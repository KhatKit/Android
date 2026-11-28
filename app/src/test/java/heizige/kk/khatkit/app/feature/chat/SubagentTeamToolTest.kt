package heizige.kk.khatkit.app.feature.chat

import heizige.kk.khatkit.ai.provider.Model
import heizige.kk.khatkit.ai.provider.ProviderSetting
import heizige.kk.khatkit.ai.ui.UIMessagePart
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.feature.chat.helper.createSubagentTeamTool
import heizige.kk.khatkit.app.feature.chat.helper.subagentMessages
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentTeamToolTest {
    private val lead = Assistant(name = "Lead", enableSubagentTeam = true)
    private val writer = Assistant(name = "Writer")
    private val reviewer = Assistant(name = "Reviewer")
    private val researcher = Assistant(name = "Researcher")
    private val settings = Settings.dummy().copy(assistants = listOf(lead, writer, reviewer, researcher))

    @Test
    fun approvalAndPartialResults() = runBlocking {
        val tool = createSubagentTeamTool(lead.id, { settings }, Semaphore(2)) { (role, text), _ ->
            if (role == reviewer) error("internal exception text")
            "result for $text"
        }
        val input = input(writer to "draft", reviewer to "review")
        assertTrue(tool.needsApproval(input))
        val output = tool.execute(input).single() as UIMessagePart.Text
        val results = Json.parseToJsonElement(output.text).jsonObject.getValue("results").jsonArray
        assertEquals("result for draft", results[0].jsonObject.getValue("text").jsonPrimitive.content)
        assertEquals(reviewer.id.toString(), results[1].jsonObject.getValue("role_uid").jsonPrimitive.content)
        assertTrue(results[1].jsonObject.containsKey("error"))
        assertTrue(!output.text.contains("internal exception text"))
    }

    @Test
    fun invalidBatchesAndRevokedPermissionNeverRun() = runBlocking {
        var current = settings
        val tool = createSubagentTeamTool(lead.id, { current }, Semaphore(2)) { _, _ ->
            throw AssertionError("invalid invocation reached worker")
        }
        suspend fun rejects(vararg tasks: Pair<Assistant?, String>) {
            val error = runCatching { tool.execute(input(*tasks)) }.exceptionOrNull()
            assertTrue("Expected validation failure, got $error", error is IllegalArgumentException)
        }
        rejects(lead to "self")
        rejects(writer to "draft", writer to "again")
        rejects(writer to "  ")
        rejects(Assistant() to "missing")
        rejects(null to "valid", Assistant() to "missing")
        rejects(*Array(4) { null to "extra" })
        current = settings.copy(assistants = settings.assistants.map {
            if (it.id == lead.id) it.copy(enableSubagentTeam = false) else it
        })
        rejects(reviewer to "review")
        rejects(null to "draft")
    }

    @Test
    fun temporaryWorkersNeedOnlyOneConfiguredRoleAndReceiveSeparatePrompts() = runBlocking {
        val solo = settings.copy(assistants = listOf(lead.copy(systemPrompt = "private supervisor prompt")))
        val tool = createSubagentTeamTool(lead.id, { solo }, Semaphore(2)) { (role, task, temporary), snapshot ->
            assertTrue(temporary)
            assertTrue(snapshot.assistants.none { it.id == role.id })
            val messages = subagentMessages(role, task)
            assertEquals(2, messages.size)
            assertTrue(messages.first().toText().contains(role.name))
            assertTrue(messages.first().toText().contains(role.description))
            assertTrue(!messages.first().toText().contains("private supervisor prompt"))
            messages.last().toText()
        }
        val tasks = batch(
            temporary("Accountant", "Estimate costs", "list the costs"),
            temporary("Reviewer", "Evaluate impact", "assess the benefits"),
        )
        assertTrue(tool.needsApproval(tasks))
        val output = tool.execute(tasks).single() as UIMessagePart.Text
        val results = Json.parseToJsonElement(output.text).jsonObject.getValue("results").jsonArray
        assertEquals(listOf("list the costs", "assess the benefits"), results.map { it.jsonObject.getValue("text").jsonPrimitive.content })
        assertEquals(listOf("Accountant", "Reviewer"), results.map { it.jsonObject.getValue("role").jsonPrimitive.content })
        assertEquals(listOf(lead.id), solo.assistants.map { it.id })
    }

    @Test
    fun mixesExistingAndTemporaryRolesAndResumesOldRequests() = runBlocking {
        val tool = createSubagentTeamTool(lead.id, { settings }, Semaphore(2)) { assignment, _ ->
            if (assignment.temporary) assertTrue(assignment.role.id !in settings.assistants.map { it.id })
            else assertEquals(writer, assignment.role)
            assignment.role.name
        }
        val tasks = batch(
            named(writer, "draft"),
            temporary("Fact checker", "Check dates", "check the timeline"),
            named(null, "legacy pending task"),
        )
        val output = tool.execute(tasks).single() as UIMessagePart.Text
        val results = Json.parseToJsonElement(output.text).jsonObject.getValue("results").jsonArray
        assertEquals(listOf("Writer", "Fact checker", "临时子代理 3"), results.map { it.jsonObject.getValue("text").jsonPrimitive.content })
        assertEquals(listOf(writer.id.toString(), "temporary", "temporary"), results.map { it.jsonObject.getValue("role_uid").jsonPrimitive.content })
    }

    @Test
    fun validatesTemporaryProfilesBeforeStartingAnyWorker() = runBlocking {
        val tool = createSubagentTeamTool(lead.id, { settings }, Semaphore(2)) { _, _ ->
            throw AssertionError("Invalid batch reached worker")
        }
        val ambiguous = JsonObject(temporary("Editor", "Edit prose", "edit") + named(writer, "edit"))
        for (invalid in listOf(
            ambiguous, temporary(" ", "Edit prose", "edit"), temporary("Editor", "", "edit"),
            temporary("n".repeat(101), "Edit prose", "edit"), buildJsonObject { put("task", "edit") },
        )) {
            val error = runCatching { tool.execute(batch(named(reviewer, "valid"), invalid)) }.exceptionOrNull()
            assertTrue("Expected validation failure, got $error", error is IllegalArgumentException)
        }
    }

    @Test
    fun newlyAvailableExactMatchNeedsFreshApprovalAndDisabledRoleAllowsFallback() = runBlocking {
        val model = Model()
        val provider = ProviderSetting.OpenAI(models = listOf(model))
        val specialist = writer.copy(description = "Edit prose", chatModelId = model.id)
        var current = settings.copy(assistants = listOf(lead), providers = listOf(provider))
        var calls = 0
        val tool = createSubagentTeamTool(lead.id, { current }, Semaphore(2)) { assignment, _ ->
            calls++
            assignment.role.name
        }
        val proposal = batch(temporary("Copy editor", "Edit prose", "edit"))
        assertTrue(tool.needsApproval(proposal))
        current = current.copy(assistants = listOf(lead, specialist))
        val error = runCatching { tool.execute(proposal) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException && error.message.orEmpty().contains(specialist.id.toString()))
        assertEquals(0, calls)
        current = current.copy(providers = listOf(provider.copy(enabled = false)))
        tool.execute(proposal)
        assertEquals(1, calls)
    }

    @Test
    fun limitsParallelWorkAndCancelsEveryStartedChild() = runBlocking {
        val started = Channel<String>(Channel.UNLIMITED)
        val stopped = Channel<String>(Channel.UNLIMITED)
        val tool = createSubagentTeamTool(lead.id, { settings }, Semaphore(2)) { (_, task), _ ->
            started.send(task)
            try {
                awaitCancellation()
            } finally {
                stopped.trySend(task)
            }
        }
        withTimeout(5_000) {
            val parent = async { tool.execute(input(null to "one", reviewer to "two", null to "three")) }
            val active = setOf(started.receive(), started.receive())
            assertTrue(started.tryReceive().isFailure)
            parent.cancelAndJoin()
            assertEquals(active, setOf(stopped.receive(), stopped.receive()))
            assertTrue(started.tryReceive().isFailure)
        }
    }

    private fun input(vararg tasks: Pair<Assistant?, String>) =
        batch(*tasks.map { (role, task) -> named(role, task) }.toTypedArray())

    private fun batch(vararg tasks: JsonObject) = buildJsonObject {
        put("tasks", buildJsonArray { tasks.forEach { add(it) } })
    }

    private fun named(role: Assistant?, task: String) = buildJsonObject {
        put("role_uid", role?.id?.toString() ?: "temporary")
        put("task", task)
    }

    private fun temporary(name: String, duties: String, task: String) = buildJsonObject {
        put("temporary_role", buildJsonObject { put("name", name); put("description", duties) })
        put("task", task)
    }
}
