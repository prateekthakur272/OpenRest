package dev.prateekthakur.openrest.core.run

import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.http.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.utils.*

import java.nio.file.Path
import kotlin.io.path.*

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
