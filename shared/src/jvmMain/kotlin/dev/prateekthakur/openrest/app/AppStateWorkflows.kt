package dev.prateekthakur.openrest.app

import dev.prateekthakur.openrest.core.codegen.*
import dev.prateekthakur.openrest.core.interop.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.platform.*
import dev.prateekthakur.openrest.utils.*

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

// Workflow tabs: creating, editing, running and saving workflows. Extensions of [AppState] so that class stays the single owner of state.

fun AppState.workflowTab(col: Path, name: String): RequestTab? = tabs.firstOrNull { it.workflow != null && it.collectionPath == col && it.workflow.saved.name == name }

fun AppState.openWorkflow(col: Path, name: String) {
    workflowTab(col, name)?.let { activeTabId = it.id; return }
    val wf = collection(col)?.workflows?.firstOrNull { it.name == name } ?: return
    val t = RequestTab(nextId++, col, WorkflowStore.file(col, name), ApiRequest(name = name), workflow = WorkflowSession(wf))
    tabs += t
    activeTabId = t.id
    selectedCollection = col
}

fun AppState.promptNewWorkflow(col: Path) {
    modal = Modal.Prompt("New Workflow", "", "Workflow name", "Create") { n ->
        guard {
            val name = n.trim()
            require(name.isNotEmpty()) { "Name required" }
            require(!WorkflowStore.file(col, name).exists()) { "A workflow named '$name' already exists" }
            WorkflowStore.save(col, Workflow(name))
            reload(col); expanded[col.toString()] = true
            openWorkflow(col, name)
        }
    }
}

fun AppState.saveWorkflow(tab: RequestTab) = guard {
    val ws = tab.workflow ?: return@guard
    val w = ws.draft.copy(name = ws.draft.name.trim())
    require(w.name.isNotEmpty()) { "Workflow name required" }
    WorkflowStore.save(tab.collectionPath, w, ws.saved.name)
    ws.draft = w; ws.saved = w
    tab.filePath = WorkflowStore.file(tab.collectionPath, w.name)
    reload(tab.collectionPath)
}

fun AppState.promptRenameWorkflow(col: Path, name: String) {
    modal = Modal.Prompt("Rename Workflow", name, "Workflow name", "Rename") { n ->
        guard {
            val new = n.trim()
            require(new.isNotEmpty()) { "Name required" }
            val wf = collection(col)?.workflows?.firstOrNull { it.name == name } ?: return@guard
            WorkflowStore.save(col, wf.copy(name = new), name)
            workflowTab(col, name)?.let { t -> t.workflow!!.let { s -> s.draft = s.draft.copy(name = new); s.saved = s.saved.copy(name = new) }; t.filePath = WorkflowStore.file(col, new) }
            reload(col)
        }
    }
}

fun AppState.cloneWorkflow(col: Path, name: String) = guard {
    val wf = collection(col)?.workflows?.firstOrNull { it.name == name } ?: return@guard
    var n = "$name copy"; var i = 2
    while (WorkflowStore.file(col, n).exists()) n = "$name copy ${i++}"
    WorkflowStore.save(col, wf.copy(name = n))
    reload(col)
    openWorkflow(col, n)
}

fun AppState.promptDeleteWorkflow(col: Path, name: String) {
    modal = Modal.Confirm("Delete workflow", "Permanently delete workflow \"$name\"? The requests it uses are not deleted.", "Delete") {
        guard {
            workflowTab(col, name)?.let { closeTab(it.id, true) }
            WorkflowStore.delete(col, name)
            reload(col)
        }
    }
}

private fun AppState.addBlock(tab: RequestTab, parent: String?, branch: Int, make: (String) -> WorkflowStep) {
    val ws = tab.workflow ?: return
    val id = newStepId(ws.draft.steps)
    ws.update { w -> w.copy(steps = w.steps.addStep(parent, branch, make(id))) }
    ws.selected = id
}

/** Adds an existing request of the collection at the end of a branch (top level when [parent] is null). */
fun AppState.addWorkflowStep(tab: RequestTab, request: RequestNode, parent: String? = null, branch: Int = 0) {
    val col = collection(tab.collectionPath) ?: return
    addBlock(tab, parent, branch) { WorkflowStep(it, col.requestRef(request)) }
}

/** Creates a brand-new request in the collection root and adds it as a step. */
fun AppState.addNewWorkflowStep(tab: RequestTab, name: String, method: String, url: String, parent: String? = null, branch: Int = 0) = guard {
    require(name.isNotBlank()) { "Request name required" }
    val col = tab.collectionPath
    val file = Collections.createRequest(col, ApiRequest(name = name.trim(), method = method, url = url.trim()))
    reload(col)
    collection(col)?.findRequest(file)?.let { addWorkflowStep(tab, it, parent, branch) }
}

/** Adds a logic block (if/else, loop, set variable, wait, stop) with sensible starting values. */
fun AppState.addWorkflowLogic(tab: RequestTab, kind: StepKind, parent: String? = null, branch: Int = 0, loop: LoopMode = LoopMode.ForEach, setOp: SetOp = SetOp.Value) {
    addBlock(tab, parent, branch) { id ->
        when (kind) {
            StepKind.If -> WorkflowStep(id, kind = kind, conditions = listOf(Condition()))
            StepKind.Loop -> WorkflowStep(id, kind = kind, loop = loop, conditions = if (loop == LoopMode.While) listOf(Condition()) else emptyList(), count = if (loop == LoopMode.Repeat) "3" else "")
            StepKind.Set -> WorkflowStep(id, kind = kind, setOp = setOp, varName = when (setOp) { SetOp.Count -> "count"; SetOp.Sum -> "total"; SetOp.First -> "first"; SetOp.Last -> "last"; else -> "" })
            StepKind.Wait -> WorkflowStep(id, kind = kind, value = "1000")
            else -> WorkflowStep(id, kind = kind)
        }
    }
}

fun AppState.removeWorkflowStep(tab: RequestTab, id: String) {
    val ws = tab.workflow ?: return
    // references to the removed block are left in place; the next run flags them
    val gone = ws.draft.steps.findStep(id)?.let { listOf(it) + it.children.walk() + it.otherwise.walk() }?.map { it.id } ?: listOf(id)
    ws.update { w -> w.copy(steps = w.steps.removeStep(id)) }
    gone.forEach { ws.runs.remove(it); ws.counts.remove(it); ws.history.remove(it) }
    if (ws.selected in gone) ws.selected = null
}

fun AppState.moveWorkflowStep(tab: RequestTab, id: String, delta: Int) {
    val ws = tab.workflow ?: return
    ws.update { w -> w.copy(steps = w.steps.moveStep(id, delta)) }
}

/** Runs the workflow; with [upTo] it ends once that block has run (handy while building). */
fun AppState.runWorkflow(tab: RequestTab, upTo: String? = null) {
    val ws = tab.workflow ?: return
    if (ws.running) return
    val col = collection(tab.collectionPath) ?: return
    if (settings.autoSave && ws.dirty) saveWorkflow(tab)
    ws.runs.clear(); ws.counts.clear(); ws.history.clear(); ws.events.clear(); ws.logOpen.clear(); ws.cancelled = false; ws.running = true; ws.expanded = null
    val rc = runCtx(col)
    val wf = ws.draft
    scope.launch(Dispatchers.IO) {
        try {
            WorkflowRunner.run(
                rc, wf, { ws.cancelled }, { ws.current = it.id },
                { r -> ws.runs[r.step.id] = r; ws.counts[r.step.id] = (ws.counts[r.step.id] ?: 0) + 1; ws.history[r.step.id] = ((ws.history[r.step.id] ?: emptyList()) + r).takeLast(100) },
                { e -> ws.events.add(e) }, upTo,
            )
        } catch (e: Throwable) {
            showToast(e.message ?: e.toString())
        } finally { ws.running = false; ws.current = null }
    }
}

fun AppState.startRunner(tab: RequestTab) {
    val rs = tab.runner ?: return
    if (rs.running) return
    val col = collection(tab.collectionPath) ?: return
    rs.results.clear(); rs.cancelled = false; rs.running = true; rs.expanded = null
    val rc = runCtx(col)
    val queue = List(rs.iters) { rs.chosen }.flatten()
    val delay = rs.delay.toLongOrNull() ?: 0L
    val stop = rs.stopOnFail
    scope.launch(Dispatchers.IO) {
        try {
            Runner.runAll(rc, queue, stop, delay, { rs.cancelled }, { rs.current = it.name }, { rs.results.add(it) })
        } finally { rs.running = false; rs.current = "" }
    }
}
