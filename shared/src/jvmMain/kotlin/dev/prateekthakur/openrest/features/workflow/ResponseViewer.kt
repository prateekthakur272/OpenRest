package dev.prateekthakur.openrest.features.workflow

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.platform.*
import dev.prateekthakur.openrest.theme.*
import dev.prateekthakur.openrest.utils.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The complete last response of one step, in its own dialog (the side pane is for configuration only). */
@Composable
fun ResponseDialog(state: AppState, m: Modal.ViewResponse) {
    val tab = state.tabs.firstOrNull { it.id == m.tabId }
    val col = tab?.let { state.collection(it.collectionPath) }
    val c = if (tab != null && col != null) remember(tab, col) { Wf(state, tab, col) } else null
    val st = c?.steps?.findStep(m.stepId)
    if (c == null || st == null) {
        // never close silently: say what is wrong
        ModalBox("Response", { state.modal = null }, 460.dp, actions = { Btn("Close", { state.modal = null }, primary = true) }) {
            T("This step is no longer in the workflow, so there is no response to show.", color = LocalPal.current.muted)
        }
        return
    }
    ModalBox("Response — ${c.name(st)}", { state.modal = null }, 940.dp, 720.dp, actions = {
        Btn("Close", { state.modal = null }, primary = true)
    }) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ResponsePanel(c, st)
        }
    }
}

/** The complete response of a step: summary, body, fields to pass on, headers, the request as sent, and tests. */
@Composable
private fun ResponsePanel(c: Wf, st: WorkflowStep) {
    val p = LocalPal.current
    val ws = c.ws
    val all = ws.history[st.id] ?: emptyList()
    var sub by remember(st.id) { mutableStateOf("Body") }
    var round by remember(st.id) { mutableStateOf(-1) }   // -1 follows the latest execution
    var pretty by remember(st.id) { mutableStateOf(true) }
    if (all.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T(if (ws.running) "Running…" else "No response yet", weight = FontWeight.SemiBold)
            T("Run the workflow (or just up to this step) to see the complete response here.", color = p.muted, size = 12)
            if (!ws.running && st.enabled) Btn("Run to here", { c.state.runWorkflow(c.tab, st.id) }, icon = Ic.Play, primary = true)
        }
        return
    }
    val idx = if (round in all.indices) round else all.lastIndex
    val run = all[idx]
    val r = run.result
    val resp = r?.response?.takeIf { it.error == null }
    val text = resp?.text.orEmpty()
    val ct = resp?.contentType.orEmpty().lowercase()
    val isJson = ct.contains("json") || Json.isValid(text) && text.isNotBlank()
    val lang = when { isJson -> "json"; ct.contains("xml") || ct.contains("html") -> "xml"; else -> "text" }

    // summary
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (resp != null) {
            Chip("${resp.status}${resp.statusText.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""}", statusColor(resp.status, p))
            T("${resp.timeMs} ms", color = p.muted, size = 12)
            T(formatSize(resp.size), color = p.muted, size = 12)
        } else T(r?.error ?: r?.response?.error ?: if (run.node == null) "The request no longer exists" else "No response", color = p.err, size = 13, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
        if (all.size > 1) {
            val labels = all.mapIndexed { i, x -> "Round ${if (x.iteration > 0) x.iteration else i + 1} of ${all.size}".let { l -> if (all.map { it.iteration }.distinct().size < all.size) "Run ${i + 1} of ${all.size}" else l } }
            Dropdown(labels[idx], labels, { l -> round = labels.indexOf(l) })
        }
        Spacer(Modifier.weight(1f))
        if (resp != null) Btn("Copy body", { Platform.copy(if (isJson && pretty) Json.prettyText(text) ?: text else text); c.state.showToast("Response body copied") }, icon = Ic.Copy)
    }
    if (resp != null) resp.finalUrl.takeIf { it.isNotBlank() }?.let { T(it, color = p.muted, size = 11, mono = true, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    r?.error?.takeIf { resp == null }?.let { T(it, color = p.err, mono = true, size = 12) }
    run.notes.forEach { T("⚠ $it", color = p.err, size = 12) }

    Tabs(listOf("Body", "Fields", "Headers", "Sent", "Tests"), sub, { sub = it }, badge = { t ->
        when (t) { "Headers" -> resp?.headers?.size?.takeIf { it > 0 }?.toString(); "Tests" -> r?.tests?.size?.takeIf { it > 0 }?.let { n -> if (r.tests.all { it.passed }) "$n" else "${r.tests.count { !it.passed }} failed" }; else -> null }
    })
    when (sub) {
        "Body" -> when {
            resp == null -> T("There is no response body.", color = p.muted, size = 12)
            text.isEmpty() -> T("The response body is empty.", color = p.muted, size = 12)
            else -> {
                if (isJson) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Btn("Pretty", { pretty = true }, primary = pretty); Btn("Raw", { pretty = false }, primary = !pretty)
                    T("${text.length} characters", color = p.muted, size = 11)
                }
                val shown = if (isJson && pretty) Json.prettyText(text) ?: text else text
                val cut = shown.length > 1_500_000
                CodeEditor(if (cut) shown.take(1_500_000) else shown, {}, Modifier.fillMaxWidth().height(420.dp), lang, readOnly = true)
                if (cut) T("Showing the first 1,500,000 of ${shown.length} characters — use Copy body for everything.", color = p.muted, size = 11)
            }
        }
        "Fields" -> if (resp == null) T("There is no response to take values from.", color = p.muted, size = 12) else FieldsView(c, st, r)
        "Headers" -> if (resp == null || resp.headers.isEmpty()) T("No response headers.", color = p.muted, size = 12) else
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(p.bg).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                resp.headers.forEach { (k, v) -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { T(k, mono = true, size = 12, color = p.syntaxKey, modifier = Modifier.width(180.dp)); T(v, mono = true, size = 12, modifier = Modifier.weight(1f)) } }
            }
        "Sent" -> SentView(r?.request)
        else -> if (r == null || r.tests.isEmpty()) T("This request has no tests or assertions.", color = p.muted, size = 12) else TestsList(r.tests)
    }
    if (r != null && r.logs.isNotEmpty() && sub == "Body") {
        SectionLabel("SCRIPT LOGS")
        T(r.logs.joinToString("\n"), mono = true, size = 12, color = p.muted)
    }
}

/** The request exactly as it went out: method, URL, headers and body after overrides and substitution. */
@Composable
private fun SentView(sent: PreparedRequest?) {
    val p = LocalPal.current
    if (sent == null) { T("Nothing was sent (the step failed before the request was built).", color = p.muted, size = 12); return }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        MethodPill(sent.method)
        T(sent.url, mono = true, size = 12, modifier = Modifier.weight(1f))
    }
    SectionLabel("HEADERS")
    if (sent.headers.isEmpty()) T("none", color = p.muted, size = 12)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(p.bg).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        sent.headers.forEach { (k, v) -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { T(k, mono = true, size = 12, color = p.syntaxKey, modifier = Modifier.width(180.dp)); T(v, mono = true, size = 12, modifier = Modifier.weight(1f)) } }
    }
    SectionLabel("BODY")
    when (val b = sent.body) {
        Body.None -> T("no body", color = p.muted, size = 12)
        is Body.Raw -> CodeEditor(Json.prettyText(b.text)?.takeIf { b.contentType.contains("json") } ?: b.text, {}, Modifier.fillMaxWidth().height(220.dp), if (b.contentType.contains("json")) "json" else if (b.contentType.contains("xml")) "xml" else "text", readOnly = true)
        is Body.Form -> b.fields.forEach { (k, v) -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { T(k, mono = true, size = 12, color = p.syntaxKey, modifier = Modifier.width(180.dp)); T(v, mono = true, size = 12) } }
        is Body.Multipart -> b.parts.forEach { part -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { T(part.name, mono = true, size = 12, color = p.syntaxKey, modifier = Modifier.width(180.dp)); T(if (part.isFile) "file: ${part.value}" else part.value, mono = true, size = 12) } }
        is Body.FileBody -> T("file: ${b.path}", mono = true, size = 12)
    }
}

/** Every field of the response, with one-click "Pass on". */
@Composable
private fun FieldsView(c: Wf, st: WorkflowStep, r: ExecResult?) {
    val p = LocalPal.current
    val fields = remember(r) { ResponseFields.of(r) }
    T("Click “Pass on” to hand a value to the steps after this one.", color = p.muted, size = 11)
    Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(6.dp)).background(p.bg).padding(6.dp).verticalScroll(rememberScrollState())) {
        fields.forEach { f ->
            val existing = st.outputs.firstOrNull { it.kind == f.kind && it.path == f.path && it.name.isNotBlank() }
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                T(f.label, mono = true, size = 12, color = p.syntaxKey, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                T(f.preview, mono = true, size = 12, color = p.muted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (existing != null) T("✓ ${existing.name}", color = p.ok, size = 12, modifier = Modifier.width(150.dp))
                else Box(Modifier.width(150.dp)) { Btn("Pass on", { c.update(st.id) { s -> s.copy(outputs = s.outputs + StepOutput(uniqueName(s.outputs, suggestName(f)), f.kind, f.path)) } }, icon = Ic.Add) }
            }
        }
    }
}
