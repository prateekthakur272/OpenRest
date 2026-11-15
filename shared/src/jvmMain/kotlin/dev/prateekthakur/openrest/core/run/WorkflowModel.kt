package dev.prateekthakur.openrest.core.run

import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.http.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.utils.*

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
