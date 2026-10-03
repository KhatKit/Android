package heizige.kk.khatkit.app.feature.workflow

/**
 * 调度形状对照 QGraph（MIT，xiaoqiang-cheng/QGraph v0.1.5）
 * `src/qgraph/engine/executor.py` 的 PipelineExecutor：
 * Kahn 入度队列、失败节点的下游标 skipped、skip_nodes 把已成功节点记为 success 后不再执行。
 * 这里用 Kotlin 重写行为，不复制 Python。节点真正落到卡片时仍编译成 FlowSpec，
 * 由既有 `khatkit__run_flow` 执行。
 */
object WorkflowEngine {
    fun execute(
        workflow: VisualWorkflow,
        input: Map<String, String> = emptyMap(),
        skipSuccess: Set<String> = emptySet(),
        failAt: String? = null,
        cancelAfter: String? = null,
    ): WorkflowExecution {
        validate(workflow)?.let { return it }
        val nodes = workflow.nodes.filter { it.enabled }
        val nodeMap = nodes.associateBy { it.id }
        val adj = mutableMapOf<String, MutableList<String>>()
        val inDegree = nodes.associate { it.id to 0 }.toMutableMap()
        nodes.forEach { node ->
            node.dependsOn.forEach { dep ->
                adj.getOrPut(dep) { mutableListOf() }.add(node.id)
                inDegree[node.id] = (inDegree[node.id] ?: 0) + 1
            }
        }

        val steps = mutableListOf<StepTrace>()
        val outputs = mutableMapOf<String, Map<String, String>>()
        val done = mutableSetOf<String>()
        val failed = mutableSetOf<String>()
        var cancelled = false

        if (skipSuccess.isNotEmpty()) {
            val pending = skipSuccess.toMutableList()
            while (pending.isNotEmpty()) {
                val id = pending.removeAt(0)
                val node = nodeMap[id] ?: continue
                if (id in done) continue
                done += id
                val resumed = mapOf("resumed" to "true")
                outputs[id] = resumed
                steps += StepTrace(id, node.kind, "success", node.params, resumed)
                for (neighbor in adj[id].orEmpty()) {
                    inDegree[neighbor] = (inDegree[neighbor] ?: 0) - 1
                    if ((inDegree[neighbor] ?: 0) == 0 && neighbor in skipSuccess) pending += neighbor
                }
            }
        }

        val queue = ArrayDeque(
            nodes.map { it.id }.filter { (inDegree[it] ?: 0) == 0 && it !in done },
        )
        val consumedBodies = mutableSetOf<String>()

        while (queue.isNotEmpty() && !cancelled) {
            val id = queue.removeFirst()
            val node = nodeMap[id] ?: continue
            if (id in done) continue
            if (id in consumedBodies) {
                unlock(id, adj, inDegree, done, failed, queue, nodeMap, steps)
                continue
            }
            if (blocked(node, outputs)) {
                done += id
                steps += StepTrace(id, node.kind, "skipped", node.params, emptyMap())
                skipDependents(id, nodeMap, adj, inDegree, done, failed, steps, countAsFailure = false)
                continue
            }
            if (failAt == id) {
                steps += StepTrace(id, node.kind, "failed", node.params, mapOf("error" to "failed"))
                done += id
                failed += id
                skipDependents(id, nodeMap, adj, inDegree, done, failed, steps, countAsFailure = true)
                continue
            }

            val output = outputOf(node, input, outputs, loopIndex = 0)
            outputs[id] = output
            steps += StepTrace(id, node.kind, "success", node.params, output)
            done += id
            if (cancelAfter == id) {
                cancelled = true
                cancelRemaining(nodes, done, steps)
                break
            }
            if (node.kind == "loop") {
                val count = (node.params["count"]?.toIntOrNull() ?: 1).coerceIn(0, WORKFLOW_FLOW_STEP_LIMIT)
                val bodies = nodes.filter { it.params["when"] == "body" && node.id in it.dependsOn }
                consumedBodies += bodies.map { it.id }
                for (index in 1..count) {
                    bodies.forEach { body ->
                        val bodyOut = outputOf(body, input, outputs, loopIndex = index)
                        steps += StepTrace(body.id, body.kind, "success", body.params, bodyOut)
                    }
                }
            }
            unlock(id, adj, inDegree, done, failed, queue, nodeMap, steps)
        }

        val status = when {
            cancelled -> "cancelled"
            failed.isNotEmpty() -> "failed"
            else -> "completed"
        }
        return WorkflowExecution(steps, status)
    }

    fun exportLua(workflow: VisualWorkflow): String {
        val order = topo(workflow.nodes.filter { it.enabled })
        val gated = workflow.nodes
            .filter { it.params["when"] in setOf("then", "else", "body") }
            .map { it.id }
            .toSet()
        val lines = mutableListOf(
            "-- khatkit-workflow v1",
            "local env = args or {}",
            "local outputs = {}",
            "local function record(id, kind, value) outputs[#outputs + 1] = { id = id, kind = kind, value = value } return value end",
        )
        for (node in order) {
            if (node.id in gated) continue
            lines += recordLine(node, inLoop = false)
            if (node.kind == "condition") {
                lines += "if v_${safe(node.id)}[\"branch\"] == \"then\" then"
                children(workflow, node.id, "then").forEach { lines += recordLine(it, inLoop = false) }
                lines += "else"
                children(workflow, node.id, "else").forEach { lines += recordLine(it, inLoop = false) }
                lines += "end"
            }
            if (node.kind == "loop") {
                val count = (node.params["count"]?.toIntOrNull() ?: 1).coerceAtLeast(0)
                lines += "for _i = 1, $count do"
                children(workflow, node.id, "body").forEach { lines += recordLine(it, inLoop = true) }
                lines += "end"
            }
        }
        lines += "return outputs"
        return lines.joinToString("\n")
    }

    fun interpret(script: String, input: Map<String, String> = emptyMap()): List<StepTrace> =
        ScriptRunner(script, input).run()

    /** 把一次执行的成功步骤编译成 `parseFlowSpec` 能读的 steps JSON。控制节点不进流。 */
    fun flowSpecJson(execution: WorkflowExecution): String {
        val steps = execution.successes().mapNotNull { step ->
            val card = cardName(step) ?: return@mapNotNull null
            val args = step.output.entries.joinToString(",") { (key, value) ->
                "\"${escape(key)}\":\"${escape(value)}\""
            }
            """{"card":"$card","args":{$args}}"""
        }
        return "[${steps.joinToString(",")}]"
    }

    fun cardName(step: StepTrace): String? = when (step.kind) {
        "llm" -> "workflow_llm"
        "card_tool" -> step.output["card"]?.takeIf { it.isNotBlank() } ?: "card_tool"
        "delay" -> "workflow_delay"
        "data_extract" -> "workflow_extract"
        "http" -> "workflow_http"
        "notify" -> "workflow_notify"
        "sub_flow" -> "workflow_sub_flow"
        else -> null
    }

    private fun validate(workflow: VisualWorkflow): WorkflowExecution? {
        val ids = workflow.nodes.map { it.id }
        if (ids.size != ids.toSet().size) {
            return WorkflowExecution(emptyList(), "failed", "duplicate node id")
        }
        workflow.nodes.forEach { node ->
            if (node.kind !in WORKFLOW_NODE_KINDS) {
                return WorkflowExecution(emptyList(), "failed", "unknown kind ${node.kind}")
            }
            if (node.kind == "trigger") {
                val event = node.params["event"] ?: "manual"
                if (event != "manual" && event !in WORKFLOW_TRIGGER_EVENTS) {
                    return WorkflowExecution(emptyList(), "failed", "unknown trigger $event")
                }
            }
            node.dependsOn.forEach { dep ->
                if (workflow.nodes.none { it.id == dep }) {
                    return WorkflowExecution(emptyList(), "failed", "missing dependency $dep")
                }
            }
        }
        if (topoOrNull(workflow.nodes) == null) {
            return WorkflowExecution(emptyList(), "failed", "cycle")
        }
        return null
    }

    private fun topo(nodes: List<VisualNode>): List<VisualNode> =
        topoOrNull(nodes) ?: error("cycle")

    private fun topoOrNull(nodes: List<VisualNode>): List<VisualNode>? {
        val map = nodes.associateBy { it.id }
        val adj = mutableMapOf<String, MutableList<String>>()
        val degree = nodes.associate { it.id to 0 }.toMutableMap()
        nodes.forEach { node ->
            node.dependsOn.forEach { dep ->
                if (dep !in map) return null
                adj.getOrPut(dep) { mutableListOf() }.add(node.id)
                degree[node.id] = (degree[node.id] ?: 0) + 1
            }
        }
        val queue = ArrayDeque(nodes.map { it.id }.filter { (degree[it] ?: 0) == 0 })
        val ordered = mutableListOf<VisualNode>()
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            ordered += map.getValue(id)
            for (next in adj[id].orEmpty()) {
                degree[next] = (degree[next] ?: 0) - 1
                if (degree[next] == 0) queue += next
            }
        }
        return ordered.takeIf { it.size == nodes.size }
    }

    private fun blocked(node: VisualNode, outputs: Map<String, Map<String, String>>): Boolean {
        val gate = node.params["when"].orEmpty()
        if (gate.isEmpty() || gate == "body") return gate == "body"
        val source = node.dependsOn.lastOrNull { outputs[it]?.get("branch") != null }
            ?: node.params["source"].orEmpty()
        return outputs[source]?.get("branch") != gate
    }

    private fun outputOf(
        node: VisualNode,
        input: Map<String, String>,
        outputs: Map<String, Map<String, String>>,
        loopIndex: Int,
    ): Map<String, String> = when (node.kind) {
        "trigger" -> mapOf("event" to (input["event"] ?: node.params["event"] ?: "manual"))
        "llm" -> mapOf("text" to (input["classification"] ?: node.params["fallback"] ?: "other"))
        "condition" -> {
            val source = node.params["source"]?.takeIf { it.isNotBlank() } ?: node.dependsOn.lastOrNull().orEmpty()
            val field = node.params["field"] ?: "text"
            val equals = node.params["equals"].orEmpty()
            val actual = outputs[source]?.get(field).orEmpty()
            mapOf("branch" to if (actual == equals) "then" else "else")
        }
        "notify" -> buildMap {
            put("sent", node.params["title"] ?: node.title)
            if (loopIndex > 0 && node.params["when"] == "body") put("i", loopIndex.toString())
        }
        "card_tool" -> buildMap {
            put("card", node.params["card"] ?: "card_tool")
            node.params["path"]?.takeIf { it.isNotBlank() }?.let { put("path", it) }
        }
        "delay" -> mapOf("waited" to (node.params["ms"] ?: "0"))
        "http" -> mapOf("url" to node.params["url"].orEmpty(), "status" to "200")
        "data_extract" -> mapOf("value" to input[node.params["field"] ?: "text"].orEmpty())
        "sub_flow" -> mapOf("workflow" to node.params["workflow"].orEmpty())
        "loop" -> mapOf("count" to (node.params["count"] ?: "1"))
        else -> emptyMap()
    }

    private fun unlock(
        id: String,
        adj: Map<String, List<String>>,
        inDegree: MutableMap<String, Int>,
        done: MutableSet<String>,
        failed: MutableSet<String>,
        queue: ArrayDeque<String>,
        nodeMap: Map<String, VisualNode>,
        steps: MutableList<StepTrace>,
    ) {
        for (neighbor in adj[id].orEmpty()) {
            inDegree[neighbor] = (inDegree[neighbor] ?: 0) - 1
            if ((inDegree[neighbor] ?: 0) != 0 || neighbor in done) continue
            val parentsFailed = nodeMap[neighbor]?.dependsOn?.any { it in failed } == true
            if (parentsFailed) {
                if (neighbor !in done) {
                    done += neighbor
                    steps += StepTrace(
                        neighbor,
                        nodeMap.getValue(neighbor).kind,
                        "skipped",
                        nodeMap.getValue(neighbor).params,
                        emptyMap(),
                    )
                }
                skipDependents(neighbor, nodeMap, adj, inDegree, done, failed, steps, countAsFailure = true)
            } else {
                queue += neighbor
            }
        }
    }

    private fun skipDependents(
        start: String,
        nodeMap: Map<String, VisualNode>,
        adj: Map<String, List<String>>,
        inDegree: MutableMap<String, Int>,
        done: MutableSet<String>,
        failed: MutableSet<String>,
        steps: MutableList<StepTrace>,
        countAsFailure: Boolean,
    ) {
        val pending = ArrayDeque<String>()
        for (next in adj[start].orEmpty()) {
            inDegree[next] = (inDegree[next] ?: 0) - 1
            if ((inDegree[next] ?: 0) == 0) pending += next
        }
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (id in done || id !in nodeMap) continue
            done += id
            if (countAsFailure) failed += id
            steps += StepTrace(id, nodeMap.getValue(id).kind, "skipped", nodeMap.getValue(id).params, emptyMap())
            for (next in adj[id].orEmpty()) {
                inDegree[next] = (inDegree[next] ?: 0) - 1
                if ((inDegree[next] ?: 0) == 0) pending += next
            }
        }
    }

    private fun cancelRemaining(nodes: List<VisualNode>, done: Set<String>, steps: MutableList<StepTrace>) {
        nodes.filter { it.id !in done }.forEach { node ->
            steps += StepTrace(node.id, node.kind, "cancelled", node.params, emptyMap())
        }
    }

    private fun children(workflow: VisualWorkflow, parentId: String, gate: String): List<VisualNode> =
        workflow.nodes.filter { it.enabled && it.params["when"] == gate && parentId in it.dependsOn }

    private fun recordLine(node: VisualNode, inLoop: Boolean): String {
        val call = "record(\"${node.id}\", \"${node.kind}\", {${fields(node, inLoop)}})"
        return "local v_${safe(node.id)} = $call"
    }

    private fun fields(node: VisualNode, inLoop: Boolean): String = when (node.kind) {
        "trigger" -> "event=env[\"event\"] or \"${escape(node.params["event"] ?: "manual")}\""
        "llm" -> "text=env[\"classification\"] or \"${escape(node.params["fallback"] ?: "other")}\""
        "condition" -> {
            val source = node.params["source"]?.takeIf { it.isNotBlank() } ?: node.dependsOn.lastOrNull().orEmpty()
            val field = node.params["field"] ?: "text"
            val equals = escape(node.params["equals"].orEmpty())
            "branch=v_${safe(source)}[\"$field\"] == \"$equals\" and \"then\" or \"else\""
        }
        "notify" -> buildString {
            append("sent=\"${escape(node.params["title"] ?: node.title)}\"")
            if (inLoop && node.params["when"] == "body") append(", i=tostring(_i)")
        }
        "card_tool" -> buildString {
            append("card=\"${escape(node.params["card"] ?: "card_tool")}\"")
            node.params["path"]?.takeIf { it.isNotBlank() }?.let { append(", path=\"${escape(it)}\"") }
        }
        "delay" -> "waited=\"${escape(node.params["ms"] ?: "0")}\""
        "http" -> "url=\"${escape(node.params["url"].orEmpty())}\", status=\"200\""
        "data_extract" -> "value=env[\"${escape(node.params["field"] ?: "text")}\"] or \"\""
        "sub_flow" -> "workflow=\"${escape(node.params["workflow"].orEmpty())}\""
        "loop" -> "count=\"${escape(node.params["count"] ?: "1")}\""
        else -> ""
    }

    private fun safe(id: String): String = id.replace(Regex("[^A-Za-z0-9_]"), "_")

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}

private class ScriptRunner(
    script: String,
    private val input: Map<String, String>,
) {
    private val lines = script.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("--") }
    private var index = 0
    private var loopIndex = 0
    private val vars = mutableMapOf<String, Map<String, String>>()
    private val traces = mutableListOf<StepTrace>()
    private val recordRegex = Regex("""record\("([^"]+)", "([^"]+)", \{([^}]*)\}\)""")

    fun run(): List<StepTrace> {
        execUntil(lines.size)
        return traces.toList()
    }

    private fun execUntil(end: Int) {
        while (index < end && index < lines.size) {
            val line = lines[index]
            when {
                line.startsWith("local env") || line.startsWith("local outputs") ||
                    line.startsWith("local function") || line == "return outputs" -> index++
                line.startsWith("if ") -> execIf()
                line.startsWith("for ") -> execFor()
                line == "else" || line == "end" -> return
                line.contains("record(") -> {
                    execRecord(line)
                    index++
                }
                else -> index++
            }
        }
    }

    private fun execIf() {
        val takeThen = evalIf(lines[index])
        index++
        if (takeThen) {
            execUntil(lines.size)
            if (index < lines.size && lines[index] == "else") {
                index++
                skipBlock()
            } else if (index < lines.size && lines[index] == "end") {
                index++
            }
        } else {
            skipUntilElseOrEnd()
            if (index < lines.size && lines[index] == "else") {
                index++
                execUntil(lines.size)
                if (index < lines.size && lines[index] == "end") index++
            } else if (index < lines.size && lines[index] == "end") {
                index++
            }
        }
    }

    private fun execFor() {
        val match = Regex("""for _i = (\d+), (\d+) do""").find(lines[index]) ?: run {
            index++
            return
        }
        val from = match.groupValues[1].toInt()
        val to = match.groupValues[2].toInt()
        index++
        val bodyStart = index
        var depth = 1
        while (index < lines.size && depth > 0) {
            if (lines[index].startsWith("if ") || lines[index].startsWith("for ")) depth++
            if (lines[index] == "end") depth--
            if (depth > 0) index++
        }
        val bodyEnd = index
        for (n in from..to) {
            loopIndex = n
            index = bodyStart
            execUntil(bodyEnd)
        }
        index = bodyEnd + 1
        loopIndex = 0
    }

    private fun skipUntilElseOrEnd() {
        var depth = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.startsWith("if ") || line.startsWith("for ")) {
                depth++
                index++
                continue
            }
            if (line == "end") {
                if (depth == 0) return
                depth--
                index++
                continue
            }
            if (line == "else" && depth == 0) return
            index++
        }
    }

    private fun skipBlock() {
        var depth = 1
        while (index < lines.size && depth > 0) {
            val line = lines[index]
            if (line.startsWith("if ") || line.startsWith("for ")) depth++
            if (line == "end") depth--
            index++
        }
    }

    private fun evalIf(line: String): Boolean {
        val match = Regex("""if (v_\w+)\["([^"]+)"\] == "([^"]*)" then""").find(line) ?: return false
        val actual = vars[match.groupValues[1]]?.get(match.groupValues[2]).orEmpty()
        return actual == match.groupValues[3]
    }

    private fun execRecord(line: String) {
        val match = recordRegex.find(line) ?: return
        val id = match.groupValues[1]
        val kind = match.groupValues[2]
        val output = parseFields(match.groupValues[3])
        traces += StepTrace(id, kind, "success", emptyMap(), output)
        vars[id] = output
        vars["v_${id.replace(Regex("[^A-Za-z0-9_]"), "_")}"] = output
    }

    private fun parseFields(body: String): Map<String, String> {
        if (body.isBlank()) return emptyMap()
        return splitFields(body).associate { part ->
            val key = part.substringBefore('=').trim()
            val expr = part.substringAfter('=').trim()
            key to evalExpr(expr)
        }
    }

    private fun splitFields(body: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        body.forEach { char ->
            if (char == '"') quoted = !quoted
            if (char == ',' && !quoted) {
                parts += current.toString()
                current.clear()
            } else {
                current.append(char)
            }
        }
        if (current.isNotEmpty()) parts += current.toString()
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun evalExpr(expr: String): String {
        val ternary = Regex("""(.+) == "([^"]*)" and "([^"]*)" or "([^"]*)"""").find(expr)
        if (ternary != null) {
            val left = evalExpr(ternary.groupValues[1].trim())
            return if (left == ternary.groupValues[2]) ternary.groupValues[3] else ternary.groupValues[4]
        }
        val envOr = Regex("""env\["([^"]+)"\] or "([^"]*)"""").matchEntire(expr)
        if (envOr != null) return input[envOr.groupValues[1]] ?: envOr.groupValues[2]
        if (expr == "tostring(_i)") return loopIndex.toString()
        val literal = Regex(""""([^"]*)"""").matchEntire(expr)
        if (literal != null) return literal.groupValues[1]
        val field = Regex("""(v_\w+)\["([^"]+)"\]""").matchEntire(expr)
        if (field != null) return vars[field.groupValues[1]]?.get(field.groupValues[2]).orEmpty()
        return ""
    }
}
