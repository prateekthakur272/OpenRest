package dev.prateekthakur.openrest.app

import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.run.*

import androidx.compose.runtime.*
import kotlinx.coroutines.Job
import java.nio.file.Path
import kotlin.io.path.*

class HistoryEntry(
    val time: Long, val collection: Path, val file: Path, val name: String, val method: String,
    val url: String, val status: Int, val timeMs: Long, val error: String?, val result: ExecResult,
)

/** Editable state of a Runner tab; lives on the tab so it survives switching between tabs. */
class RunnerSession(val folder: Path?, requests: List<RequestNode>) {
    val order = mutableStateListOf<RequestNode>().also { it.addAll(requests) }
    val selected = mutableStateMapOf<Path, Boolean>().also { m -> requests.forEach { m[it.path] = true } }
    val results = mutableStateListOf<Runner.Item>()
    var running by mutableStateOf(false)
    var current by mutableStateOf("")
    var stopOnFail by mutableStateOf(false)
    var delay by mutableStateOf("")
    var iterations by mutableStateOf("1")
    var cancelled by mutableStateOf(false)
    var expanded by mutableStateOf<Int?>(null)

    val chosen get() = order.filter { selected[it.path] == true }
    val iters get() = (iterations.toIntOrNull() ?: 1).coerceIn(1, 100)

    fun resetOrder(requests: List<RequestNode>) {
        order.clear(); order.addAll(requests); requests.forEach { selected[it.path] = true }
    }
}

/** Editable state + last run of a workflow tab. */
class WorkflowSession(initial: Workflow) {
    var draft by mutableStateOf(initial)
    var saved by mutableStateOf(initial)
    /** Last execution of each request block (a block in a loop runs many times; [counts] says how many). */
    val runs = mutableStateMapOf<String, StepRun>()
    val counts = mutableStateMapOf<String, Int>()
    /** Every execution of a request block in the last run, oldest first (a loop runs its blocks many times). */
    val history = mutableStateMapOf<String, List<StepRun>>()
    /** Everything that happened in the last run, in order, for the run log. */
    val events = mutableStateListOf<RunEvent>()
    var running by mutableStateOf(false)
    var current by mutableStateOf<String?>(null)
    var cancelled by mutableStateOf(false)
    /** Run log: show only the entries that went wrong. */
    var logProblemsOnly by mutableStateOf(false)
    /** The run log is a bottom-docked panel: expanded or just its header bar, and how tall it is (dp). */
    var logExpanded by mutableStateOf(false)
    var logHeight by mutableStateOf(280f)
    /** Run log entries (by position) that are expanded to show details. */
    val logOpen = mutableStateMapOf<Int, Boolean>()
    var expanded by mutableStateOf<String?>(null)
    /** The block whose configuration is open in the side pane (null = the first one). */
    var selected by mutableStateOf<String?>(null)
    val dirty get() = draft != saved
    fun update(f: (Workflow) -> Workflow) { draft = f(draft) }
    fun updateStep(id: String, f: (WorkflowStep) -> WorkflowStep) = update { w -> w.copy(steps = w.steps.mapStep(id, f)) }
}

class RequestTab(
    val id: Int, val collectionPath: Path, filePath: Path, req: ApiRequest,
    val runner: RunnerSession? = null, val workflow: WorkflowSession? = null,
) {
    val isRequest get() = runner == null && workflow == null
    val title: String get() = workflow?.draft?.name ?: draft.name
    var filePath by mutableStateOf(filePath)
    var draft by mutableStateOf(req)
    var saved by mutableStateOf(req)
    var result by mutableStateOf<ExecResult?>(null)
    var sending by mutableStateOf(false)
    var reqTab by mutableStateOf("Params")
    var resTab by mutableStateOf("Response")
    var resView by mutableStateOf("Auto")
    var resPreview by mutableStateOf<Boolean?>(null)
    var filter by mutableStateOf("")
    var scriptTab by mutableStateOf("Pre Request")
    var varsTab by mutableStateOf("Pre Request")
    val responses = mutableStateListOf<ExecResult>()
    var viewing by mutableStateOf(0)
    var diff by mutableStateOf(false)
    var find by mutableStateOf("")
    var job: Job? = null
    fun push(r: ExecResult) {
        responses.add(0, r)
        while (responses.size > 10) responses.removeAt(responses.size - 1)
        viewing = 0
        result = r
    }
    val dirty get() = if (workflow != null) workflow.dirty else draft != saved
}

/** Which menu of the in-window menu bar is open, and where things are on screen (so a click elsewhere can close it). */
class TitleMenuState {
    var open by mutableStateOf<String?>(null)
    val anchors = HashMap<String, androidx.compose.ui.geometry.Rect>()
    var panel: androidx.compose.ui.geometry.Rect? = null
    fun close() { open = null }
}
