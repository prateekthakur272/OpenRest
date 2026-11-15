package dev.prateekthakur.openrest.core.run

import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.http.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.utils.*

import java.nio.file.Path
import kotlin.io.path.*

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
