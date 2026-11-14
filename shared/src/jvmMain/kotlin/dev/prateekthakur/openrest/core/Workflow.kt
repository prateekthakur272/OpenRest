package dev.prateekthakur.openrest.core

import java.nio.file.Path
import kotlin.io.path.*

/** What part of an earlier step's response a `{{sN.…}}` reference points at. */
enum class SourceKind(val id: String, val label: String, val needsPath: Boolean, val pathHint: String) {
    Response("response", "Whole response (status, headers, body)", false, ""),
    Body("body", "Full body", false, ""),
    Field("body", "Body field", true, "e.g. data.token or items[0].id"),
    Status("status", "Status code", false, ""),
    Header("header", "Response header", true, "header name, e.g. Location"),
    Time("time", "Response time (ms)", false, "");

    /** The `{{…}}` reference for this selection, e.g. `{{s1.body.data.id}}`. */
    fun token(stepId: String, path: String = ""): String {
        val p = path.trim()
        return "{{$stepId.$id" + (if (needsPath && p.isNotEmpty()) ".$p" else "") + "}}"
    }
}

/** Where a value is written into a request step. */
enum class InputTarget(val id: String, val label: String, val needsKey: Boolean, val keyHint: String) {
    PathParam("path", "URL path parameter", true, "the :name in the URL"),
    Query("query", "Query parameter", true, "parameter name"),
    Header("header", "Header", true, "header name"),
    Body("body", "JSON body field", true, "e.g. user.id"),
    BodyRaw("body_raw", "Whole body", false, ""),
    Bearer("bearer", "Auth bearer token", false, ""),
    Url("url", "Whole URL", false, ""),
    Method("method", "HTTP method", false, ""),
    PathAppend("path_append", "Add to end of URL path", false, ""),
    Variable("variable", "Variable {{name}}", true, "variable name");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Variable
    }
}

/**
 * Writes [value] into a request step at [target]/[name]. The value is a template: plain text, `{{vars}}`,
 * loop variables (`{{item.id}}`) and references to earlier steps' responses (`{{s1.body.data.id}}`,
 * `{{s2.header.X-Id}}`, `{{s1.status}}`), mixed freely.
 */
data class StepInput(val target: InputTarget = InputTarget.Variable, val name: String = "", val value: String = "")

/**
 * A named value a request step passes on to later steps, e.g. `userId` ← body field `data.id`.
 * Later steps use it as `{{s1.out.userId}}` (shown as `[Create user › userId]`).
 */
data class StepOutput(val name: String = "", val kind: SourceKind = SourceKind.Field, val path: String = "") {
    /** The `sN.what[.path]` reference that reads this output straight from the response. */
    fun reference(stepId: String) = "$stepId.${kind.id}" + (if (kind.needsPath && path.isNotBlank()) ".${path.trim()}" else "")

    companion object {
        /** Restores the kind from a file: "body" with a path is a field, without a path the whole body. */
        fun of(name: String, from: String, path: String): StepOutput {
            val k = when (from) {
                "body" -> if (path.isBlank()) SourceKind.Body else SourceKind.Field
                "status" -> SourceKind.Status; "header" -> SourceKind.Header; "time" -> SourceKind.Time; "response" -> SourceKind.Response
                else -> SourceKind.Field
            }
            return StepOutput(name, k, path)
        }
    }
}

enum class StepKind(val id: String, val label: String, val hint: String) {
    Request("request", "Request", "Call an API"),
    If("if", "If / else", "Run different steps depending on a condition"),
    Loop("loop", "Loop", "Repeat steps for every item in a list, N times, or while something is true"),
    Set("set", "Set variable", "Store a value, or count / add up / pick items from a list"),
    Wait("wait", "Wait", "Pause before the next step"),
    Stop("stop", "Stop", "End the loop or the whole workflow");

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Request }
}

enum class CondOp(val id: String, val label: String, val needsRight: Boolean) {
    Eq("eq", "equals", true), Ne("ne", "does not equal", true),
    Contains("contains", "contains", true), NotContains("not_contains", "does not contain", true),
    StartsWith("starts", "starts with", true),
    Gt("gt", "is greater than", true), Gte("gte", "is at least", true), Lt("lt", "is less than", true), Lte("lte", "is at most", true),
    Empty("empty", "is empty", false), NotEmpty("not_empty", "is not empty", false),
    Matches("matches", "matches pattern", true);

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Eq }
}

/** `left op right`; both sides are templates (`{{s1.status}}`, `{{count}}`, `{{item.id}}`, plain text). */
data class Condition(val left: String = "", val op: CondOp = CondOp.Eq, val right: String = "")

enum class LoopMode(val id: String, val label: String) {
    ForEach("each", "For each item in a list"), Repeat("repeat", "Repeat a number of times"), While("while", "Repeat while a condition is true");
    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: ForEach }
}

enum class SetOp(val id: String, val label: String, val needsList: Boolean) {
    Value("value", "Set to a value", false), Count("count", "Count items in a list", true), Sum("sum", "Add up numbers in a list", true),
    First("first", "First item of a list", true), Last("last", "Last item of a list", true);
    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Value }
}

enum class StopScope(val id: String, val label: String) {
    Loop("loop", "Stop this loop"), Workflow("workflow", "Stop the workflow");
    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Workflow }
}

/**
 * One block of a workflow. [kind] decides which fields matter:
 *  - Request: [request] (file relative to the collection root, '/'-separated) customised by [inputs].
 *  - If: [conditions] / [matchAll]; [children] run when true, [otherwise] when false.
 *  - Loop: [loop] with [list] / [count] / [conditions]; [children] is the body; [itemVar] / [indexVar] are set per round.
 *  - Set: stores [varName] using [setOp] on [value] (a template) or [list] (a template that yields a JSON list).
 *  - Wait: [value] is milliseconds.  Stop: [stopScope], [stopFail], [value] is the message.
 */
data class WorkflowStep(
    val id: String,
    val request: String = "",
    val inputs: List<StepInput> = emptyList(),
    val enabled: Boolean = true,
    val kind: StepKind = StepKind.Request,
    /** Request steps: named values this step hands on to the steps after it. */
    val outputs: List<StepOutput> = emptyList(),
    val title: String = "",
    val continueOnFail: Boolean = false,
    val conditions: List<Condition> = emptyList(),
    val matchAll: Boolean = true,
    val children: List<WorkflowStep> = emptyList(),
    val otherwise: List<WorkflowStep> = emptyList(),
    val loop: LoopMode = LoopMode.ForEach,
    val list: String = "",
    val count: String = "",
    val itemVar: String = "item",
    val indexVar: String = "index",
    val maxIterations: Int = 100,
    val setOp: SetOp = SetOp.Value,
    val varName: String = "",
    val value: String = "",
    val path: String = "",
    val stopScope: StopScope = StopScope.Workflow,
    val stopFail: Boolean = false,
)

data class Workflow(
    val name: String,
    val steps: List<WorkflowStep> = emptyList(),
    val stopOnFail: Boolean = true,
    val description: String = "",
)

fun Collection.requestRef(node: RequestNode): String = path.relativize(node.path).joinToString("/")
fun Collection.requestByRef(ref: String): RequestNode? = findRequest(path.resolve(ref).normalize())

// ---------------- tree helpers ----------------

/** Depth-first, in execution order (a block, then its children, then its else branch). */
fun List<WorkflowStep>.walk(): List<WorkflowStep> = flatMap { listOf(it) + it.children.walk() + it.otherwise.walk() }

fun List<WorkflowStep>.findStep(id: String): WorkflowStep? = walk().firstOrNull { it.id == id }

fun List<WorkflowStep>.mapStep(id: String, f: (WorkflowStep) -> WorkflowStep): List<WorkflowStep> =
    map { if (it.id == id) f(it) else it.copy(children = it.children.mapStep(id, f), otherwise = it.otherwise.mapStep(id, f)) }

fun List<WorkflowStep>.removeStep(id: String): List<WorkflowStep> =
    filter { it.id != id }.map { it.copy(children = it.children.removeStep(id), otherwise = it.otherwise.removeStep(id)) }

/** Appends [step] to the top level ([parentId] null) or to a block's main branch ([branch] 0) / else branch (1). */
fun List<WorkflowStep>.addStep(parentId: String?, branch: Int, step: WorkflowStep): List<WorkflowStep> =
    if (parentId == null) this + step
    else mapStep(parentId) { if (branch == 1) it.copy(otherwise = it.otherwise + step) else it.copy(children = it.children + step) }

/** Moves a block up/down among its siblings. */
fun List<WorkflowStep>.moveStep(id: String, delta: Int): List<WorkflowStep> {
    val i = indexOfFirst { it.id == id }
    if (i >= 0) {
        val j = i + delta
        return if (j !in indices) this else toMutableList().also { it.add(j, it.removeAt(i)) }
    }
    return map { it.copy(children = it.children.moveStep(id, delta), otherwise = it.otherwise.moveStep(id, delta)) }
}

fun newStepId(existing: List<WorkflowStep>): String {
    val all = existing.walk()
    var i = all.size + 1
    while (all.any { it.id == "s$i" }) i++
    return "s$i"
}

/** Request blocks that run before [id] (anything else can read their responses). */
fun List<WorkflowStep>.requestsBefore(id: String): List<WorkflowStep> =
    walk().takeWhile { it.id != id }.filter { it.kind == StepKind.Request }

/** Names of variables made by loops and Set blocks that run before [id]. */
fun List<WorkflowStep>.variablesBefore(id: String): List<String> {
    val out = linkedSetOf<String>()
    for (s in walk()) {
        if (s.id == id) break
        if (s.kind == StepKind.Loop) { if (s.itemVar.isNotBlank() && s.loop == LoopMode.ForEach) out += s.itemVar.trim(); if (s.indexVar.isNotBlank()) out += s.indexVar.trim() }
        if (s.kind == StepKind.Set && s.varName.isNotBlank()) out += s.varName.trim()
    }
    // a block can also use the variables of the loops that contain it
    fun enclosing(list: List<WorkflowStep>, trail: List<WorkflowStep>): List<WorkflowStep>? {
        for (s in list) {
            if (s.id == id) return trail
            enclosing(s.children, trail + s)?.let { return it }
            enclosing(s.otherwise, trail + s)?.let { return it }
        }
        return null
    }
    enclosing(this, emptyList())?.filter { it.kind == StepKind.Loop }?.forEach { l ->
        if (l.itemVar.isNotBlank() && l.loop == LoopMode.ForEach) out += l.itemVar.trim(); if (l.indexVar.isNotBlank()) out += l.indexVar.trim()
    }
    return out.toList()
}

// ---------------- file format ----------------

object WorkflowIO {
    private fun s(v: Any?) = v?.toString() ?: ""
    private fun maps(v: Any?) = (v as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()

    private fun stepToMap(st: WorkflowStep): Map<String, Any?> {
        val c = Yml.m(
            "id" to st.id,
            "kind" to (if (st.kind == StepKind.Request) null else st.kind.id),
            "title" to st.title,
            "enabled" to (if (st.enabled) null else false),
        )
        when (st.kind) {
            StepKind.Request -> c.putAll(Yml.m(
                "request" to st.request,
                "continueOnFail" to (if (st.continueOnFail) true else null),
                "inputs" to st.inputs.map { i -> Yml.m("target" to (if (i.target == InputTarget.Variable) null else i.target.id), "name" to i.name, "value" to i.value) },
                "outputs" to st.outputs.map { o -> Yml.m("name" to o.name, "from" to o.kind.id, "path" to o.path) },
            ))
            StepKind.If -> c.putAll(Yml.m(
                "conditions" to st.conditions.map(::condToMap), "match" to (if (st.matchAll) null else "any"),
                "then" to st.children.map(::stepToMap), "else" to st.otherwise.map(::stepToMap),
            ))
            StepKind.Loop -> c.putAll(Yml.m(
                "mode" to st.loop.id, "list" to st.list, "count" to st.count, "item" to st.itemVar, "index" to st.indexVar,
                "max" to (if (st.maxIterations == 100) null else st.maxIterations.toLong()),
                "conditions" to st.conditions.map(::condToMap), "match" to (if (st.matchAll) null else "any"),
                "do" to st.children.map(::stepToMap),
            ))
            StepKind.Set -> c.putAll(Yml.m("op" to st.setOp.id, "name" to st.varName, "list" to st.list, "value" to st.value, "path" to st.path))
            StepKind.Wait -> c.putAll(Yml.m("ms" to st.value))
            StepKind.Stop -> c.putAll(Yml.m("scope" to st.stopScope.id, "fail" to (if (st.stopFail) true else null), "message" to st.value))
        }
        return c
    }

    private fun condToMap(c: Condition) = Yml.m("left" to c.left, "op" to c.op.id, "right" to c.right)
    private fun condFrom(m: Map<*, *>) = Condition(s(m["left"]), CondOp.of(s(m["op"])), s(m["right"]))

    private fun stepFrom(st: Map<*, *>, idx: Int): WorkflowStep {
        val kind = StepKind.of(s(st["kind"]).ifBlank { "request" })
        val base = WorkflowStep(
            id = s(st["id"]).ifBlank { "s${idx + 1}" }, kind = kind, title = s(st["title"]), enabled = st["enabled"] != false,
        )
        fun kids(key: String) = maps(st[key]).mapIndexed { i, m -> stepFrom(m, i) }
        return when (kind) {
            StepKind.Request -> base.copy(
                request = s(st["request"]), continueOnFail = st["continueOnFail"] == true,
                outputs = maps(st["outputs"]).map { o -> StepOutput.of(s(o["name"]), s(o["from"]), s(o["path"])) },
                inputs = maps(st["inputs"]).map { i ->
                    // older files stored (from, source, path); turn those into the equivalent reference
                    val legacy = s(i["from"]).takeIf { it.isNotBlank() }?.let { f ->
                        val src = s(i["source"]).ifBlank { "body" }.let { if (it == "field") "body" else it }; val path = s(i["path"])
                        "{{$f.$src" + (if (path.isNotBlank()) ".$path" else "") + "}}"
                    }
                    StepInput(InputTarget.of(s(i["target"])), s(i["name"]), if (i.containsKey("value")) s(i["value"]) else legacy ?: "")
                },
            )
            StepKind.If -> base.copy(conditions = maps(st["conditions"]).map(::condFrom), matchAll = s(st["match"]) != "any", children = kids("then"), otherwise = kids("else"))
            StepKind.Loop -> base.copy(
                loop = LoopMode.of(s(st["mode"])), list = s(st["list"]), count = s(st["count"]),
                itemVar = s(st["item"]).ifBlank { "item" }, indexVar = s(st["index"]).ifBlank { "index" },
                maxIterations = (st["max"] as? Number)?.toInt() ?: 100,
                conditions = maps(st["conditions"]).map(::condFrom), matchAll = s(st["match"]) != "any", children = kids("do"),
            )
            StepKind.Set -> base.copy(setOp = SetOp.of(s(st["op"])), varName = s(st["name"]), list = s(st["list"]), value = s(st["value"]), path = s(st["path"]))
            StepKind.Wait -> base.copy(value = s(st["ms"]))
            StepKind.Stop -> base.copy(stopScope = StopScope.of(s(st["scope"])), stopFail = st["fail"] == true, value = s(st["message"]))
        }
    }

    fun toMap(w: Workflow): Map<String, Any?> = Yml.m(
        "name" to w.name,
        "description" to w.description,
        "stopOnFail" to (if (w.stopOnFail) null else false),
        "steps" to w.steps.map(::stepToMap),
    )

    fun fromMap(m: Map<*, *>, fallbackName: String): Workflow =
        Workflow(s(m["name"]).ifBlank { fallbackName }, maps(m["steps"]).mapIndexed { i, st -> stepFrom(st, i) }, m["stopOnFail"] != false, s(m["description"]))
}

/** One YAML file per workflow, in the collection's `workflows` folder. */
object WorkflowStore {
    fun dir(col: Path): Path = col.resolve("workflows")
    fun file(col: Path, name: String): Path = dir(col).resolve(Collections.sanitize(name) + ".yaml")

    fun load(col: Path): List<Workflow> {
        val d = dir(col)
        if (!d.isDirectory()) return emptyList()
        return d.listDirectoryEntries().filter { it.extension == "yaml" || it.extension == "yml" }.sortedBy { it.name.lowercase() }.mapNotNull { f ->
            val m = runCatching { Yml.parse(f.readText()) as? Map<*, *> }.getOrNull() ?: return@mapNotNull null
            WorkflowIO.fromMap(m, f.nameWithoutExtension)
        }
    }

    fun save(col: Path, w: Workflow, oldName: String? = null) {
        dir(col).createDirectories()
        val target = file(col, w.name)
        if (oldName != null && oldName != w.name) {
            require(!target.exists()) { "A workflow named '${w.name}' already exists" }
            file(col, oldName).deleteIfExists()
        }
        target.writeText(Yml.dump(WorkflowIO.toMap(w)))
    }

    fun delete(col: Path, name: String) { file(col, name).deleteIfExists() }
}

/** One execution of a request block (a block inside a loop produces one per round). */
data class StepRun(
    val step: WorkflowStep,
    val node: RequestNode?,
    val result: ExecResult?,
    /** Values the step's inputs resolved to, in order. */
    val inputs: List<Pair<String, String>> = emptyList(),
    val notes: List<String> = emptyList(),
    val depth: Int = 0,
    val iteration: Int = 0,
) {
    val failed get() = node == null || result == null || result.error != null || result.response?.error != null || result.tests.any { !it.passed }
}

/** The value of [o] in [res] (what a later step would receive) and a warning when it can't be found. */
fun StepOutput.valueIn(res: ExecResult?): Pair<String, String?> =
    WorkflowRunner.resolve(reference("s0"), res).let { (v, w) -> v to (if (w == null) null else "not found in the response") }

/** Finds the names of passed-on values (`{token}`, `{user.id}`, older `{{token}}`) that a piece of text uses. */
object FlowRefs {
    private val single = Regex("(?<!\\{)\\{([A-Za-z_][A-Za-z0-9_]*)(?:\\.[A-Za-z0-9_\\[\\]-]+)*}")
    private val double = Regex("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)(?:\\.[^{}\\s]*)?\\s*}}")

    /** Root names, in order of first use. */
    fun names(text: String): List<String> =
        (single.findAll(text).toList() + double.findAll(text).toList()).sortedBy { it.range.first }.map { it.groupValues[1] }.distinct()
}

/** A line of the run log: requests, branches taken, loop rounds, variables set. */
data class RunEvent(
    val stepId: String, val depth: Int, val kind: StepKind, val text: String, val ok: Boolean = true,
    /** Milliseconds since the run started. */
    val at: Long = 0,
    /** For request executions: the HTTP status and the request's own time, and the name shown for it. */
    val status: Int? = null, val ms: Long? = null, val label: String? = null,
    /** A detail entry under the step above it (what a step passes on). */
    val sub: Boolean = false,
    /** For [sub] entries: the name / value pairs, in full. */
    val fields: List<Pair<String, String>> = emptyList(),
)

// ---------------- builder helpers ----------------

/** One selectable value of a finished step's response, for the "pick a value" list. */
data class ResponseField(val label: String, val kind: SourceKind, val path: String, val preview: String)

object ResponseFields {
    private const val MAX = 120

    /** Status, time, headers and every leaf of a JSON body (first few items of arrays). Empty when the step produced no response. */
    fun of(res: ExecResult?): List<ResponseField> {
        val r = res?.response?.takeIf { it.error == null } ?: return emptyList()
        val out = mutableListOf<ResponseField>()
        val root = Json.tryParse(r.text)
        val structured = root is Map<*, *> || root is List<*>
        // what people reach for first: the full body, then single fields of it
        out += ResponseField(if (structured) "Full body" else "Full body (text)", SourceKind.Body, "", r.text.take(70).replace('\n', ' '))
        fun walk(v: Any?, path: String, depth: Int) {
            if (out.size >= MAX || depth > 7) return
            when (v) {
                is Map<*, *> -> v.forEach { (k, c) -> walk(c, if (path.isEmpty()) k.toString() else "$path.$k", depth + 1) }
                is List<*> -> {
                    if (path.isNotEmpty()) out += ResponseField("body.$path  (list)", SourceKind.Field, path, "${v.size} item${if (v.size == 1) "" else "s"}")
                    v.take(3).forEachIndexed { i, c -> walk(c, "$path[$i]", depth + 1) }
                }
                else -> if (path.isNotEmpty()) out += ResponseField("body.$path", SourceKind.Field, path, (Json.str(v) ?: "null").take(70))
            }
        }
        if (structured) walk(root, "", 0)
        out += ResponseField("Status code", SourceKind.Status, "", r.status.toString())
        r.headers.take(15).forEach { (k, v) -> out += ResponseField("header $k", SourceKind.Header, k, v.take(70)) }
        out += ResponseField("Response time (ms)", SourceKind.Time, "", r.timeMs.toString())
        out += ResponseField("Whole response (status, headers, body)", SourceKind.Response, "", "")
        return out
    }
}

/** A place in a request that can be filled from an earlier step. */
data class Slot(val target: InputTarget, val name: String, val current: String) {
    val label get() = Slots.label(target, name)
}

object Slots {
    fun label(target: InputTarget, name: String): String = when (target) {
        InputTarget.PathParam -> "URL path · :$name"
        InputTarget.Query -> "Query · $name"
        InputTarget.Header -> "Header · $name"
        InputTarget.Body -> "Body · $name"
        InputTarget.BodyRaw -> "Body · whole body"
        InputTarget.Bearer -> "Auth · bearer token"
        InputTarget.Url -> "Request · URL"
        InputTarget.Method -> "Request · method"
        InputTarget.PathAppend -> "URL path · add at the end"
        InputTarget.Variable -> "Variable · {{$name}}"
    }

    /** Every fillable place of [req]: path params, query, headers, JSON body fields and the auth token. */
    fun of(req: ApiRequest): List<Slot> {
        val out = mutableListOf<Slot>()
        Regex("(?<=/):([A-Za-z_][\\w-]*)").findAll(req.url.substringBefore('?')).map { it.groupValues[1] }.distinct().forEach { n ->
            out += Slot(InputTarget.PathParam, n, req.pathParams.firstOrNull { it.name == n }?.value ?: "")
        }
        val inline = req.url.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.map { it.substringBefore('=') to it.substringAfter('=', "") }
        (inline + req.params.filter { it.enabled && it.name.isNotBlank() }.map { it.name to it.value }).distinctBy { it.first }.forEach { (k, v) -> out += Slot(InputTarget.Query, k, v) }
        req.headers.filter { it.enabled && it.name.isNotBlank() }.forEach { out += Slot(InputTarget.Header, it.name, it.value) }
        if (req.bodyMode == BodyMode.Json) {
            val root = Json.tryParse(req.bodyJson)
            var n = 0
            fun walk(v: Any?, path: String, depth: Int) {
                if (n >= 30 || depth > 6) return
                when (v) {
                    is Map<*, *> -> v.forEach { (k, c) -> walk(c, if (path.isEmpty()) k.toString() else "$path.$k", depth + 1) }
                    is List<*> -> v.take(3).forEachIndexed { i, c -> walk(c, "$path[$i]", depth + 1) }
                    else -> if (path.isNotEmpty()) { n++; out += Slot(InputTarget.Body, path, Json.str(v) ?: "null") }
                }
            }
            if (root is Map<*, *> || root is List<*>) walk(root, "", 0)
        }
        out += Slot(InputTarget.Bearer, "", if (req.auth.mode == AuthMode.Bearer) req.auth.token else "")
        return out
    }

    private val token = Regex("\\{\\{\\s*(s\\d+)\\.([a-z]+)(?:\\.([^{}]*?))?\\s*}}")

    /** Shows `{{s1.body.data.id}}` as `[Create user › data.id]` so users never see ids or braces. */
    fun friendly(value: String, steps: List<WorkflowStep>, nameOf: (WorkflowStep) -> String): String =
        token.replace(value) { m ->
            val st = steps.firstOrNull { it.id == m.groupValues[1] }
            val who = st?.let(nameOf) ?: m.groupValues[1]
            val path = m.groupValues[3]
            val what = when (m.groupValues[2]) {
                "out" -> path
                "body" -> if (path.isEmpty()) "whole body" else path
                "status" -> "status code"
                "header" -> "header $path"
                "time" -> "response time"
                "response" -> "whole response"
                else -> m.groupValues[2]
            }
            "[$who › $what]"
        }

    fun hasReference(value: String) = token.containsMatchIn(value)

    private val plain = Regex("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)((?:\\.[^{}\\s]*)?)\\s*}}")

    /** Like [friendly], and also names a plain `{{token}}` / `{{user.id}}` after the step that passes `token` / `user` on. */
    fun friendlyAll(value: String, steps: List<WorkflowStep>, nameOf: (WorkflowStep) -> String): String =
        plain.replace(friendly(value, steps, nameOf)) { m ->
            val owner = steps.lastOrNull { s -> s.kind == StepKind.Request && s.outputs.any { it.name.trim() == m.groupValues[1] } }
            if (owner == null) m.value else "[${nameOf(owner)} › ${m.groupValues[1]}${m.groupValues[2]}]"
        }
}

/** Something a step's request still needs a value for. */
data class StepNeed(val target: InputTarget, val name: String) {
    val label get() = if (target == InputTarget.PathParam) "/:$name" else "{{$name}}"
}

object StepNeeds {
    private val pathParam = Regex("(?<=/):([A-Za-z_][\\w-]*)")

    /**
     * Unfilled path parameters (`/users/:id`) and `{{variables}}` the request uses that nothing defines yet.
     * [known] are variable names already available (collection, environment, runtime, loops, Set blocks).
     */
    fun of(req: ApiRequest, inputs: List<StepInput>, known: Set<String>): List<StepNeed> {
        val out = mutableListOf<StepNeed>()
        val filled = inputs.filter { it.value.isNotBlank() }
        val base = req.url.substringBefore('?')
        pathParam.findAll(base).map { it.groupValues[1] }.distinct().forEach { n ->
            val set = req.pathParams.any { it.name == n && it.value.isNotBlank() } || filled.any { it.target == InputTarget.PathParam && it.name.trim() == n } || filled.any { it.target == InputTarget.Url }
            if (!set) out += StepNeed(InputTarget.PathParam, n)
        }
        val text = buildString {
            append(req.url).append('\n'); req.headers.filter { it.enabled }.forEach { append(it.name).append(' ').append(it.value).append('\n') }
            req.params.filter { it.enabled }.forEach { append(it.value).append('\n') }
            append(req.bodyJson).append(req.bodyText).append(req.bodyXml).append(req.auth.token).append(req.auth.username).append(req.auth.password)
        }
        Variables.names(text).distinct().filter { n ->
            val root = n.substringBefore('.')
            !WorkflowRunner.isStepRef(n) && !n.startsWith("$") && !n.startsWith("process.env.") && n !in known && root !in known &&
                inputs.none { it.target == InputTarget.Variable && it.name.trim() == n } && !n.contains(' ')
        }.forEach { out += StepNeed(InputTarget.Variable, it) }
        return out
    }
}
