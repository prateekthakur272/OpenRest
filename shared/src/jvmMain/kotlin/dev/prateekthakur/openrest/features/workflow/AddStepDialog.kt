package dev.prateekthakur.openrest.features.workflow

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// =====================================================================================================
// add-step dialog
// =====================================================================================================

@Composable
fun AddStepDialog(state: AppState, m: Modal.AddStep) {
    val p = LocalPal.current
    val tab = state.tabs.firstOrNull { it.id == m.tabId } ?: run { state.modal = null; return }
    val col = state.collection(tab.collectionPath) ?: run { state.modal = null; return }
    var mode by remember { mutableStateOf("Existing request") }
    var filter by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("GET") }
    var url by remember { mutableStateOf("{{baseUrl}}/") }
    val reqs = remember(col) { col.allRequests() }
    val shown = reqs.filter { r ->
        val f = filter.trim().lowercase()
        f.isEmpty() || r.name.lowercase().contains(f) || r.request.url.lowercase().contains(f)
    }
    fun done(f: () -> Unit) { f(); state.modal = null }
    ModalBox("Add to workflow", { state.modal = null }, 680.dp, 560.dp, actions = {
        Btn("Cancel", { state.modal = null })
        if (mode == "New request") Btn("Create & add", { state.addNewWorkflowStep(tab, name, method, url, m.parent, m.branch); state.modal = null }, primary = true, enabled = name.isNotBlank())
    }) {
        Tabs(listOf("Existing request", "New request", "Logic"), mode, { mode = it })
        when (mode) {
            "Existing request" -> {
                Field(filter, { filter = it }, Modifier.fillMaxWidth(), "Filter requests…", highlightVars = false)
                if (reqs.isEmpty()) T("This collection has no requests yet. Use “New request”.", color = p.muted)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(shown.size) { i ->
                        val r = shown[i]
                        val folder = col.ancestorFolders(r.path).joinToString(" / ") { it.name }
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { done { state.addWorkflowStep(tab, r, m.parent, m.branch) } }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 8.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            MethodPill(r.request.method)
                            T(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            T(listOf(folder, r.request.url).filter { it.isNotEmpty() }.joinToString("  ·  "), color = p.muted, size = 11, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            "New request" -> {
                SectionLabel("NAME")
                Field(name, { name = it }, Modifier.fillMaxWidth(), "e.g. Create order", highlightVars = false)
                SectionLabel("METHOD AND URL")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dropdown(method, HTTP_METHODS, { method = it })
                    Field(url, { url = it }, Modifier.weight(1f), "https://…", mono = true)
                }
                T("The request is saved in the collection, so you can refine its headers, body and scripts from the request tab.", color = p.muted, size = 12)
                Spacer(Modifier.weight(1f))
            }
            else -> {
                data class Tool(val title: String, val hint: String, val add: () -> Unit)
                val tools = listOf(
                    Tool("If / else", "Run different steps depending on a condition, e.g. status is 200, or count is greater than 0.") { state.addWorkflowLogic(tab, StepKind.If, m.parent, m.branch) },
                    Tool("Loop over a list", "Repeat steps for every item of a list from a response — e.g. create an order line for each product.") { state.addWorkflowLogic(tab, StepKind.Loop, m.parent, m.branch, LoopMode.ForEach) },
                    Tool("Repeat N times", "Run steps a fixed number of times.") { state.addWorkflowLogic(tab, StepKind.Loop, m.parent, m.branch, LoopMode.Repeat) },
                    Tool("Repeat while…", "Keep going while a condition is true (with a safety limit) — e.g. poll until a status says “done”.") { state.addWorkflowLogic(tab, StepKind.Loop, m.parent, m.branch, LoopMode.While) },
                    Tool("Count items in a list", "Store how many items a list has, to use in a condition or a request.") { state.addWorkflowLogic(tab, StepKind.Set, m.parent, m.branch, setOp = SetOp.Count) },
                    Tool("Add up numbers", "Store the total of a number field across a list, e.g. the sum of all prices.") { state.addWorkflowLogic(tab, StepKind.Set, m.parent, m.branch, setOp = SetOp.Sum) },
                    Tool("Set a variable", "Store a value, or the first / last item of a list, for later steps.") { state.addWorkflowLogic(tab, StepKind.Set, m.parent, m.branch, setOp = SetOp.Value) },
                    Tool("Wait", "Pause for a while, e.g. before checking a job's status again.") { state.addWorkflowLogic(tab, StepKind.Wait, m.parent, m.branch) },
                    Tool("Stop", "End the loop or the whole workflow, optionally marking it as failed.") { state.addWorkflowLogic(tab, StepKind.Stop, m.parent, m.branch) },
                )
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    tools.forEach { t ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { done(t.add) }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 10.dp, vertical = 8.dp)) {
                            T(t.title, weight = FontWeight.SemiBold)
                            T(t.hint, color = p.muted, size = 12)
                        }
                    }
                }
            }
        }
    }
}
