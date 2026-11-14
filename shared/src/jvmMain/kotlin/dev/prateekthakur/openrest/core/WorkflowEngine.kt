package dev.prateekthakur.openrest.core

object WorkflowRunner {
    fun isStepRef(name: String) = Regex("^s\\d+\\.").containsMatchIn(name.trim())

    /** Dot/bracket path into parsed JSON; tolerates a leading `res.body.`, `body.` or `$.`. */
    fun field(text: String, path: String): String? {
        val root = Json.tryParse(text) ?: return null
        val p = path.trim().removePrefix("res.body.").removePrefix("body.")
        return JsonPath.query(root, p)?.let(::asString)
    }

    fun asString(v: Any?): String = when (v) {
        null -> ""
        is String, is Number, is Boolean -> Json.str(v) ?: v.toString()
        else -> Json.stringify(v)
    }

    /**
     * Resolves `s1.body.data.id`-style references against finished steps.
     * Returns the value and an optional warning (value is "" when something is missing).
     */
    fun resolve(name: String, src: ExecResult?): Pair<String, String?> {
        val parts = name.trim().split('.', limit = 3)
        val id = parts[0]
        val what = parts.getOrElse(1) { "" }
        val path = parts.getOrElse(2) { "" }
        val r = src?.response ?: return "" to "{{$name}}: step $id has no response"
        return when (what) {
            "body" -> if (path.isEmpty()) r.text to null else field(r.text, path).let { (it ?: "") to (if (it == null) "{{$name}}: '$path' not found in response body" else null) }
            "status" -> r.status.toString() to null
            "time" -> r.timeMs.toString() to null
            "header" -> r.headers.firstOrNull { it.first.equals(path.trim(), true) }?.second.let { (it ?: "") to (if (it == null) "{{$name}}: header '$path' not found" else null) }
            "response" -> Json.stringify(
                linkedMapOf(
                    "status" to r.status.toLong(), "statusText" to r.statusText, "timeMs" to r.timeMs,
                    "headers" to LinkedHashMap<String, Any?>().also { m -> r.headers.forEach { (k, v) -> m[k] = v } },
                    "body" to (Json.tryParse(r.text) ?: r.text),
                ),
            ) to null
            else -> "" to "{{$name}}: unknown reference '$what' (use body, status, header, time or response)"
        }
    }

    private fun upsert(list: List<KV>, key: String, value: String, ci: Boolean = false): List<KV> {
        val i = list.indexOfFirst { it.name.equals(key, ci) }
        return if (i >= 0) list.toMutableList().also { it[i] = it[i].copy(value = value, enabled = true) } else list + KV(key, value)
    }

    private fun setPath(cur: Any?, tokens: List<String>, v: Any?): Any? {
        if (tokens.isEmpty()) return v
        val k = tokens[0]
        val idx = k.toIntOrNull()
        return if (idx != null && (cur is List<*> || cur == null)) {
            val l = ((cur as? List<*>) ?: emptyList<Any?>()).toMutableList<Any?>()
            while (l.size <= idx) l.add(null)
            l[idx] = setPath(l[idx], tokens.drop(1), v); l
        } else {
            val m = LinkedHashMap<String, Any?>((cur as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap())
            m[k] = setPath(m[k], tokens.drop(1), v); m
        }
    }

    /** Applies one customisation to [req]. Returns the changed request, or the same request with a warning when it can't be applied. */
    fun apply(req: ApiRequest, inp: StepInput, value: String): Pair<ApiRequest, String?> = when (inp.target) {
        InputTarget.Variable -> req to null
        InputTarget.PathParam -> req.copy(pathParams = upsert(req.pathParams, inp.name.trim(), value)) to null
        InputTarget.Url -> req.copy(url = value) to null
        InputTarget.Method -> req.copy(method = value.trim().uppercase().ifEmpty { req.method }) to null
        InputTarget.Query -> {
            val key = inp.name.trim()
            val q = req.url.indexOf('?')
            if (q < 0) req.copy(params = upsert(req.params, key, value)) to null
            else {
                // the URL already carries a query string (the params table is ignored then), so edit it in place
                val pair = HttpExecutor.urlEncode(key) + "=" + HttpExecutor.urlEncode(value)
                val parts = req.url.substring(q + 1).split('&').filter { it.isNotEmpty() }.toMutableList()
                val at = parts.indexOfFirst { it.substringBefore('=') == key || it.substringBefore('=') == HttpExecutor.urlEncode(key) }
                if (at >= 0) parts[at] = pair else parts += pair
                req.copy(url = req.url.substring(0, q) + "?" + parts.joinToString("&")) to null
            }
        }
        InputTarget.Header -> req.copy(headers = upsert(req.headers, inp.name.trim(), value, ci = true)) to null
        InputTarget.Bearer -> req.copy(auth = req.auth.copy(mode = AuthMode.Bearer, token = value)) to null
        InputTarget.PathAppend -> {
            val q = req.url.indexOf('?')
            val base = (if (q >= 0) req.url.substring(0, q) else req.url).trimEnd('/')
            req.copy(url = base + "/" + value.trimStart('/') + (if (q >= 0) req.url.substring(q) else "")) to null
        }
        InputTarget.BodyRaw -> when (req.bodyMode) {
            BodyMode.Text -> req.copy(bodyText = value)
            BodyMode.Xml -> req.copy(bodyXml = value)
            BodyMode.Graphql -> req.copy(bodyGraphql = value)
            else -> req.copy(bodyMode = BodyMode.Json, bodyJson = value)
        } to null
        InputTarget.Body -> {
            val usable = req.bodyMode == BodyMode.Json || req.bodyMode == BodyMode.None
            if (!usable) req to "body field '${inp.name}': the request body is not JSON"
            else {
                val existing = req.bodyJson.takeIf { it.isNotBlank() }?.let { Json.tryParse(it) }
                if (req.bodyJson.isNotBlank() && existing == null) req to "body field '${inp.name}': the request's JSON body is not valid JSON"
                else {
                    val typed = value.takeIf { it.isNotBlank() }?.let { Json.tryParse(it) } ?: value
                    val tokens = JsonPathTokens.split(inp.name)
                    req.copy(bodyMode = BodyMode.Json, bodyJson = Json.stringify(setPath(existing, tokens, typed), true)) to null
                }
            }
        }
    }

    // ---------------- conditions ----------------

    private fun num(s: String) = s.trim().toDoubleOrNull()

    fun test(l: String, op: CondOp, r: String): Boolean {
        val a = num(l); val b = num(r)
        val numeric = a != null && b != null
        return when (op) {
            CondOp.Eq -> if (numeric) a == b else l.trim() == r.trim()
            CondOp.Ne -> if (numeric) a != b else l.trim() != r.trim()
            CondOp.Contains -> l.contains(r, ignoreCase = true)
            CondOp.NotContains -> !l.contains(r, ignoreCase = true)
            CondOp.StartsWith -> l.startsWith(r, ignoreCase = true)
            CondOp.Gt -> if (numeric) a!! > b!! else l > r
            CondOp.Gte -> if (numeric) a!! >= b!! else l >= r
            CondOp.Lt -> if (numeric) a!! < b!! else l < r
            CondOp.Lte -> if (numeric) a!! <= b!! else l <= r
            CondOp.Empty -> l.isBlank() || l.trim() in setOf("[]", "{}", "null")
            CondOp.NotEmpty -> !(l.isBlank() || l.trim() in setOf("[]", "{}", "null"))
            CondOp.Matches -> runCatching { Regex(r).containsMatchIn(l) }.getOrDefault(false)
        }
    }

    private fun short(s: String, n: Int = 40) = s.replace('\n', ' ').let { if (it.length > n) it.take(n) + "…" else it }

    private fun items(text: String): List<Any?>? = when (val v = Json.tryParse(text.trim())) {
        is List<*> -> v
        is Map<*, *> -> v.values.toList()
        else -> null
    }

    private fun number(d: Double): Any = if (d % 1.0 == 0.0 && kotlin.math.abs(d) < 1e15) d.toLong() else d

    private enum class Flow { Next, BreakLoop, StopAll }

    /**
     * Runs the workflow's blocks in order. Responses of finished requests are reachable as `{{sN.…}}`; loops and
     * Set blocks create variables (`{{item.id}}`, `{{count}}`). Everything happens on a private copy of the
     * collection's runtime variables.
     *
     * [stopAfter] ends the run once that block has finished (the "run to here" button).
     */
    fun run(
        rc: RunCtx,
        wf: Workflow,
        cancelled: () -> Boolean,
        onStart: (WorkflowStep) -> Unit,
        onStep: (StepRun) -> Unit,
        onEvent: (RunEvent) -> Unit = {},
        stopAfter: String? = null,
    ) {
        val col = rc.collection
        val runtime: MutableMap<String, Any?> = HashMap(rc.runtime)
        val results = HashMap<String, ExecResult?>()
        /** step id → output name → value, as defined on each request step. */
        val outputs = HashMap<String, Map<String, String>>()
        val ids = wf.steps.walk().map { it.id }.toSet()
        var notes = mutableListOf<String>()
        var iteration = 0
        var executed = 0
        val refs: (String) -> String? = { name ->
            val id = name.substringBefore('.')
            when {
                id !in ids -> { notes += "{{$name}}: there is no step '$id'"; "" }
                !results.containsKey(id) -> { notes += "{{$name}}: that step has not run yet"; "" }
                name.split('.', limit = 3).getOrNull(1) == "out" -> {
                    val out = name.split('.', limit = 3).getOrElse(2) { "" }
                    outputs[id]?.get(out) ?: run { notes += "{{$name}}: step '$id' does not pass on an output called '$out'"; "" }
                }
                else -> resolve(name, results[id]).also { (_, w) -> w?.let { notes += it } }.first
            }
        }
        /** Names that hold workflow data (passed-on values, loop and Set variables): the ones `{name}` may refer to. */
        val flowNames = HashSet<String>()
        val flow: (String) -> String? = { n ->
            val root = n.substringBefore('.')
            when {
                root !in flowNames -> null
                !n.contains('.') -> asString(runtime[root])
                else -> asString(JsonPath.query(runtime[root], n.substringAfter('.')))
            }
        }
        val ctx = RunCtx(col, rc.envName, runtime, rc.settings, rc.globalEnv, refs, flow)
        val resolver = Execution(ctx, RequestNode(col.path, "workflow", ApiRequest()))
        fun text(t: String) = resolver.interp(t)

        val started = System.currentTimeMillis()
        fun event(st: WorkflowStep, depth: Int, msg: String, ok: Boolean = true, status: Int? = null, ms: Long? = null, label: String? = null, sub: Boolean = false, fields: List<Pair<String, String>> = emptyList()) =
            onEvent(RunEvent(st.id, depth, st.kind, msg, ok, System.currentTimeMillis() - started, status, ms, label, sub, fields))
        fun label(st: WorkflowStep, fallback: String) = st.title.ifBlank { fallback }

        fun check(conds: List<Condition>, all: Boolean): Pair<Boolean, String> {
            if (conds.isEmpty()) return false to "no conditions"
            val parts = conds.map { c ->
                val l = text(c.left); val r = if (c.op.needsRight) text(c.right) else ""
                test(l, c.op, r) to "${short(l)} ${c.op.label}" + (if (c.op.needsRight) " ${short(r)}" else "")
            }
            val ok = if (all) parts.all { it.first } else parts.any { it.first }
            return ok to parts.joinToString(if (all) " and " else " or ") { it.second }
        }

        lateinit var execList: (List<WorkflowStep>, Int) -> Flow

        fun execRequest(st: WorkflowStep, depth: Int): Flow {
            val node = col.requestByRef(st.request)
            if (node == null) {
                val msg = "Request '${st.request}' no longer exists in this collection"
                onStep(StepRun(st, null, null, notes = listOf(msg), depth = depth, iteration = iteration))
                event(st, depth, msg, ok = false)
                return if (wf.stopOnFail && !st.continueOnFail) Flow.StopAll else Flow.Next
            }
            var req = node.request
            val shown = mutableListOf<Pair<String, String>>()
            for (inp in st.inputs.sortedBy { if (it.target == InputTarget.BodyRaw) 0 else 1 }) {
                if (inp.value.isBlank()) continue
                if (inp.target.needsKey && inp.name.isBlank()) { notes += "${inp.target.label}: name is empty"; continue }
                val value = text(inp.value)   // step references, loop / Set variables and {{vars}}
                if (inp.target == InputTarget.Variable) { runtime[inp.name.trim()] = value; flowNames += inp.name.trim() }
                else req = apply(req, inp, value).let { (r, w) -> w?.let { notes += it }; r }
                shown += (if (inp.target.needsKey) "${inp.target.label} ${inp.name.trim()}" else inp.target.label) to value
            }
            val res = try { Execution(ctx, node.copy(request = req)).run() } catch (e: Exception) { ExecResult(error = e.message ?: e.toString()) }
            results[st.id] = res
            // what this step passes on to the steps after it
            val passed = LinkedHashMap<String, String>()
            for (o in st.outputs) {
                if (o.name.isBlank()) continue
                val (v, warn) = resolve(o.reference(st.id), res)
                passed[o.name.trim()] = v
                if (warn != null && res.response?.error == null && res.error == null) notes += "output '${o.name}': " + warn.substringAfter(": ")
            }
            outputs[st.id] = passed
            // ...and plain variables: {{token}}, or {{user.id}} when the value is JSON
            flowNames += passed.keys
            passed.forEach { (k, v) -> runtime[k] = v.trim().let { t -> if (t.startsWith("{") || t.startsWith("[")) Json.tryParse(t) ?: v else v } }
            val run = StepRun(st, node, res, shown, notes.distinct(), depth, iteration)
            onStep(run)
            val r = res.response
            event(st, depth, "${label(st, node.name)} · " + (r?.takeIf { it.error == null }?.let { "${it.status} · ${it.timeMs} ms" } ?: (res.error ?: r?.error ?: "failed")) +
                (if (res.tests.any { !it.passed }) " · ${res.tests.count { !it.passed }} test(s) failed" else ""), ok = !run.failed,
                status = r?.takeIf { it.error == null }?.status, ms = r?.takeIf { it.error == null }?.timeMs, label = label(st, node.name))
            if (passed.isNotEmpty()) event(st, depth, "passes on " + passed.entries.joinToString(", ") { (k, v) -> "$k = ${short(v.replace('\n', ' '), 40)}" }, sub = true, fields = passed.entries.map { it.key to it.value })
            return if (res.stop || (wf.stopOnFail && !st.continueOnFail && run.failed)) Flow.StopAll else Flow.Next
        }

        fun execIf(st: WorkflowStep, depth: Int): Flow {
            val (yes, why) = check(st.conditions, st.matchAll)
            event(st, depth, "${label(st, "If")}: $why → ${if (yes) "then" else "else"}")
            notes.distinct().forEach { event(st, depth, "⚠ $it", ok = false) }
            return execList(if (yes) st.children else st.otherwise, depth + 1)
        }

        fun runBody(st: WorkflowStep, depth: Int, index: Int): Flow {
            runtime[st.indexVar.ifBlank { "index" }.trim()] = index.toLong()
            flowNames += st.indexVar.ifBlank { "index" }.trim()
            iteration = index + 1
            return execList(st.children, depth + 1)
        }

        fun execLoop(st: WorkflowStep, depth: Int): Flow {
            val cap = st.maxIterations.coerceIn(1, 1000)
            val saved = iteration
            var flow = Flow.Next
            when (st.loop) {
                LoopMode.ForEach -> {
                    val raw = text(st.list)
                    val all = items(raw)
                    notes.distinct().forEach { event(st, depth, "⚠ $it", ok = false) }
                    if (all == null) { event(st, depth, "${label(st, "Loop")}: “${short(raw)}” is not a list — nothing to repeat", ok = false); return Flow.Next }
                    event(st, depth, "${label(st, "Loop")}: for each of ${all.size} item${if (all.size == 1) "" else "s"}" + (if (all.size > cap) " (limited to $cap)" else ""))
                    for ((i, it) in all.take(cap).withIndex()) {
                        if (cancelled()) { flow = Flow.StopAll; break }
                        runtime[st.itemVar.ifBlank { "item" }.trim()] = it
                        flowNames += st.itemVar.ifBlank { "item" }.trim()
                        flow = runBody(st, depth, i)
                        if (flow != Flow.Next) break
                    }
                }
                LoopMode.Repeat -> {
                    val n = (text(st.count).trim().toDoubleOrNull() ?: 0.0).toInt().coerceIn(0, cap)
                    event(st, depth, "${label(st, "Loop")}: repeat $n time${if (n == 1) "" else "s"}")
                    for (i in 0 until n) {
                        if (cancelled()) { flow = Flow.StopAll; break }
                        flow = runBody(st, depth, i)
                        if (flow != Flow.Next) break
                    }
                }
                LoopMode.While -> {
                    event(st, depth, "${label(st, "Loop")}: repeat while the condition holds (at most $cap times)")
                    var i = 0
                    while (i < cap) {
                        if (cancelled()) { flow = Flow.StopAll; break }
                        runtime[st.indexVar.ifBlank { "index" }.trim()] = i.toLong()
                        flowNames += st.indexVar.ifBlank { "index" }.trim()
                        if (!check(st.conditions, st.matchAll).first) break
                        flow = runBody(st, depth, i)
                        if (flow != Flow.Next) break
                        i++
                    }
                    if (i >= cap) event(st, depth, "Stopped after $cap rounds (the limit)", ok = false)
                }
            }
            iteration = saved
            return if (flow == Flow.BreakLoop) Flow.Next else flow
        }

        fun execSet(st: WorkflowStep, depth: Int): Flow {
            val name = st.varName.trim()
            if (name.isEmpty()) { event(st, depth, "${label(st, "Set variable")}: no variable name", ok = false); return Flow.Next }
            val raw = if (st.setOp.needsList) text(st.list) else text(st.value)
            val all = if (st.setOp.needsList) items(raw) else null
            if (st.setOp.needsList && all == null) { flowNames += name; runtime[name] = if (st.setOp == SetOp.Count || st.setOp == SetOp.Sum) 0L else ""; event(st, depth, "$name = ${if (st.setOp == SetOp.Count || st.setOp == SetOp.Sum) 0 else "(empty)"}  (“${short(raw)}” is not a list)", ok = false); return Flow.Next }
            val v: Any? = when (st.setOp) {
                SetOp.Value -> raw.trim().let { t -> if (t.startsWith("[") || t.startsWith("{")) Json.tryParse(t) ?: raw else raw }
                SetOp.Count -> all!!.size.toLong()
                SetOp.Sum -> number(all!!.sumOf { itm -> num(asString(if (st.path.isBlank()) itm else JsonPath.query(itm, st.path))) ?: 0.0 })
                SetOp.First -> all!!.firstOrNull() ?: ""
                SetOp.Last -> all!!.lastOrNull() ?: ""
            }
            runtime[name] = v
            flowNames += name
            event(st, depth, "${label(st, "Set")}: $name = ${short(asString(v), 80)}")
            return Flow.Next
        }

        fun execStep(st: WorkflowStep, depth: Int): Flow {
            if (++executed > 2000) { event(st, depth, "Stopped: more than 2000 blocks ran (safety limit)", ok = false); return Flow.StopAll }
            notes = mutableListOf()
            onStart(st)
            val flow = when (st.kind) {
                StepKind.Request -> execRequest(st, depth)
                StepKind.If -> execIf(st, depth)
                StepKind.Loop -> execLoop(st, depth)
                StepKind.Set -> execSet(st, depth)
                StepKind.Wait -> {
                    val ms = (text(st.value).trim().toDoubleOrNull() ?: 0.0).toLong().coerceIn(0, 60_000)
                    event(st, depth, "${label(st, "Wait")}: $ms ms")
                    var left = ms
                    while (left > 0 && !cancelled()) { Thread.sleep(minOf(left, 50)); left -= 50 }
                    Flow.Next
                }
                StepKind.Stop -> {
                    val msg = text(st.value).ifBlank { if (st.stopFail) "failed" else "done" }
                    event(st, depth, "${label(st, "Stop")}: ${st.stopScope.label.lowercase()} — $msg", ok = !st.stopFail)
                    if (st.stopScope == StopScope.Loop) Flow.BreakLoop else Flow.StopAll
                }
            }
            if (stopAfter != null && st.id == stopAfter) return Flow.StopAll
            return flow
        }

        execList = { list, depth ->
            var flow = Flow.Next
            for (st in list) {
                if (cancelled()) { flow = Flow.StopAll; break }
                if (!st.enabled) continue
                flow = execStep(st, depth)
                if (flow != Flow.Next) break
            }
            flow
        }
        execList(wf.steps, 0)
    }
}

/** Splits `a.b[0].c` into path tokens. */
object JsonPathTokens {
    fun split(path: String): List<String> =
        Regex("""\.?([^.\[\]]+)|\[(\d+|"[^"]*"|'[^']*')]""").findAll(path.trim().removePrefix("$")).map { it.groupValues[1].ifEmpty { it.groupValues[2] }.trim('"', '\'') }.toList()
}
