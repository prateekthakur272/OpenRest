package dev.prateekthakur.openrest.features.workflow

import dev.prateekthakur.openrest.app.runWorkflow
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

@Composable
internal fun RequestRow(c: Wf, st: WorkflowStep) {
    val p = LocalPal.current
    val ws = c.ws
    val node = c.col.requestByRef(st.request)
    val req = node?.request
    val run = ws.runs[st.id]
    val times = ws.counts[st.id] ?: 0
    val active = ws.current == st.id && ws.running
    val badge = when { active -> p.accent; run == null -> p.muted; run.failed -> p.err; else -> p.ok }
    val changes = st.inputs.count { it.value.isNotBlank() }
    val method = st.inputs.firstOrNull { it.target == InputTarget.Method && it.value.isNotBlank() }?.value?.trim()?.uppercase() ?: req?.method.orEmpty()
    val url = st.inputs.firstOrNull { it.target == InputTarget.Url && it.value.isNotBlank() }?.value ?: req?.url.orEmpty()
    Frame(c, st, {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(badge.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            if (active) androidx.compose.material3.CircularProgressIndicator(Modifier.size(14.dp), color = p.accent, strokeWidth = 2.dp)
            else T("${c.numbers[st.id] ?: ""}", color = badge, weight = FontWeight.Bold)
        }
    }, {
        if (node != null) T(c.name(st), weight = FontWeight.SemiBold, color = if (st.enabled) p.text else p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        else T("Missing request: ${st.request}", color = p.err, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }, {}, sub = {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (node != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MethodPill(method); T(url, color = p.muted, size = 11, mono = true, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val named = st.outputs.filter { it.name.isNotBlank() }
            val takes = c.usedBy(st)
            val resp = run?.result?.response?.takeIf { it.error == null }
            if (resp != null || run?.result?.error != null || times > 1 || changes > 0 || named.isNotEmpty() || takes.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                resp?.let { T("${it.status}", color = statusColor(it.status, p), weight = FontWeight.SemiBold, size = 12); T("${it.timeMs} ms", color = p.muted, size = 11) }
                if (run != null && run.result?.error != null) T("error", color = p.err, size = 12)
                if (times > 1) Chip("×$times", p.accent)
                if (changes > 0) T("$changes edited", color = p.accent, size = 11)
                if (takes.isNotEmpty()) T("← " + takes.joinToString(", ") { it.name }, color = p.muted, size = 11, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (named.isNotEmpty()) T("→ " + named.joinToString(", ") { it.name }, color = p.muted, size = 11, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    })
}

/** Everything about a request step, in the side pane: the request itself, what it passes on, and its last result. */
@Composable
internal fun RequestConfig(c: Wf, st: WorkflowStep) {
    val p = LocalPal.current
    val ws = c.ws
    val node = c.col.requestByRef(st.request)
    val req = node?.request
    val run = ws.runs[st.id]
    val times = ws.counts[st.id] ?: 0
    var tab by remember(st.id) { mutableStateOf("Params") }
    var bodyView by remember(st.id) { mutableStateOf("Fields") }
    val (_, vars) = c.sources(st.id)
    val slots = remember(req) { req?.let { Slots.of(it) } ?: emptyList() }
    val known = remember(c.col, ws.draft.steps, c.state.activeEnv.toMap(), c.state.activeGlobalEnv) { c.state.knownVars(c.tab) }
    val needs = if (req == null) emptyList() else StepNeeds.of(req, st.inputs, known + vars)
    fun ov(t: InputTarget) = st.inputs.firstOrNull { it.target == t && it.value.isNotBlank() }?.value
    fun count(vararg t: InputTarget) = st.inputs.count { it.target in t && it.value.isNotBlank() }
    val changes = st.inputs.count { it.value.isNotBlank() }
    val method = ov(InputTarget.Method)?.trim()?.uppercase() ?: req?.method.orEmpty()
    if (node == null || req == null) {
        T("Missing request: ${st.request}", color = p.err, weight = FontWeight.SemiBold)
        T("It was renamed, moved or deleted. Remove this step and add the request again.", color = p.muted, size = 12)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Btn("Open request", { c.state.openRequest(c.col.path, node) }, icon = Ic.Open)
        if (!ws.running && st.enabled) Btn("Run to here", { c.state.runWorkflow(c.tab, st.id) }, icon = Ic.Play, primary = true)
    }
    run?.notes?.forEach { T("⚠ $it", color = p.err, size = 12) }

        // ---- method + URL: the request line ----
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 2.dp)) { Dropdown(method, HTTP_METHODS, { m -> c.update(st.id) { s -> setOverride(s, InputTarget.Method, "", if (m == req.method) "" else m) } }) }
            ValueCell(c, st.id, ov(InputTarget.Url) ?: "", req.url, { v -> c.update(st.id) { s -> setOverride(s, InputTarget.Url, "", v) } }, "Request · URL", Modifier.weight(1f),
                ws.runs[st.id]?.inputs?.firstOrNull { it.first == InputTarget.Url.label }?.second)
        }
        if (method != req.method || ov(InputTarget.Url) != null) T("Edited for this workflow — the saved request still says ${req.method} ${req.url}", color = p.muted, size = 11)

        // ---- values the request needs but nothing provides yet ----
        if (needs.isNotEmpty()) {
            SectionLabel("NEEDS A VALUE")
            needs.forEach { n -> FieldRow(c, st, n.target, n.name, "", Slots.label(n.target, n.name), needsValue = true) }
        }

        // ---- the rest of the request ----
        val tabs = listOf("Params", "Headers", "Body", "Auth")
        Tabs(tabs, tab, { tab = it }, badge = { t ->
            when (t) {
                "Params" -> count(InputTarget.PathParam, InputTarget.Query, InputTarget.PathAppend)
                "Headers" -> count(InputTarget.Header)
                "Body" -> count(InputTarget.Body, InputTarget.BodyRaw)
                else -> count(InputTarget.Bearer)
            }.takeIf { it > 0 }?.toString()
        })
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (tab) {
                "Params" -> {
                    val path = slots.filter { it.target == InputTarget.PathParam }
                    val query = slots.filter { it.target == InputTarget.Query }
                    if (path.isNotEmpty()) { SectionLabel("PATH PARAMETERS"); path.forEach { FieldRow(c, st, it.target, it.name, it.current, ":" + it.name) } }
                    SectionLabel("QUERY PARAMETERS")
                    if (query.isEmpty() && st.inputs.none { it.target == InputTarget.Query }) T("None yet.", color = p.muted, size = 12)
                    query.forEach { FieldRow(c, st, it.target, it.name, it.current, it.name) }
                    AddedRows(c, st, InputTarget.Query, slots, "parameter name")
                    AddRowButton(c, st, InputTarget.Query, "Add query parameter")
                    FieldRow(c, st, InputTarget.PathAppend, "", "", "add to end of path")
                }
                "Headers" -> {
                    val headers = slots.filter { it.target == InputTarget.Header }
                    if (headers.isEmpty() && st.inputs.none { it.target == InputTarget.Header }) T("This request sends no custom headers.", color = p.muted, size = 12)
                    headers.forEach { FieldRow(c, st, it.target, it.name, it.current, it.name) }
                    AddedRows(c, st, InputTarget.Header, slots, "header name")
                    AddRowButton(c, st, InputTarget.Header, "Add header")
                }
                "Body" -> {
                    val rawOv = ov(InputTarget.BodyRaw)
                    val currentBody = when (req.bodyMode) { BodyMode.Text -> req.bodyText; BodyMode.Xml -> req.bodyXml; BodyMode.Graphql -> req.bodyGraphql; else -> req.bodyJson }
                    val mode = if (rawOv != null) "Whole body" else bodyView
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        T("Body type: ${req.bodyMode.name}", color = p.muted, size = 12)
                        Spacer(Modifier.weight(1f))
                        Btn("Fields", { bodyView = "Fields"; if (rawOv != null) c.update(st.id) { s -> setOverride(s, InputTarget.BodyRaw, "", "") } }, primary = mode == "Fields")
                        Btn("Whole body", { bodyView = "Whole body" }, primary = mode == "Whole body")
                    }
                    if (mode == "Fields") {
                        val body = slots.filter { it.target == InputTarget.Body }
                        if (body.isEmpty() && st.inputs.none { it.target == InputTarget.Body }) T(if (req.bodyMode == BodyMode.Json) "No fields found in this JSON body. Add one, or switch to “Whole body”." else "Field editing works with JSON bodies. Use “Whole body” for this request.", color = p.muted, size = 12)
                        body.forEach { FieldRow(c, st, it.target, it.name, it.current, it.name) }
                        AddedRows(c, st, InputTarget.Body, slots, "field, e.g. user.id")
                        AddRowButton(c, st, InputTarget.Body, "Add body field")
                    } else {
                        var bodyText by remember(st.id) { mutableStateOf(rawOv ?: currentBody) }
                        var bodyPick by remember(st.id) { mutableStateOf(false) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("WHOLE BODY SENT BY THIS STEP", Modifier.weight(1f))
                            if (c.available(st.id).isNotEmpty() || c.steps.requestsBefore(st.id).isNotEmpty()) Btn("Use value", { bodyPick = !bodyPick }, primary = bodyPick, icon = Ic.Variables)
                            if (rawOv != null) IconBtn(Ic.Refresh, { bodyText = currentBody; c.update(st.id) { s -> setOverride(s, InputTarget.BodyRaw, "", "") } }, color = p.accent, size = 15)
                        }
                        if (bodyPick) ValuePicker(c, st.id, { tok -> bodyText = tok; c.update(st.id) { s -> setOverride(s, InputTarget.BodyRaw, "", tok) }; bodyPick = false }, "Body · whole body") { bodyPick = false }
                        WithVars(c, st.id) {
                        CodeEditor(bodyText, { v -> bodyText = v; c.update(st.id) { s -> setOverride(s, InputTarget.BodyRaw, "", if (v == currentBody) "" else v.ifBlank { " " }) } }, Modifier.fillMaxWidth().height(170.dp), if (req.bodyMode == BodyMode.Xml) "xml" else if (req.bodyMode == BodyMode.Text) "text" else "json")
                        }
                        (rawOv ?: bodyText).let { b -> c.preview(b, st.id)?.let { T("= " + it.take(160), color = p.ok, size = 11, mono = true, maxLines = 2) } }
                        T("Tip: use {name} for values passed on by earlier steps (or “Use value”). {{name}} is for environment variables.", color = p.muted, size = 11)
                    }
                }
                else -> {
                    FieldRow(c, st, InputTarget.Bearer, "", if (req.auth.mode == AuthMode.Bearer) req.auth.token else "", "bearer token")
                    T("This request's auth: ${req.auth.mode.label}." + (if (req.auth.mode != AuthMode.Bearer) " Entering a token here sends it as a Bearer token for this step." else ""), color = p.muted, size = 11)
                }
            }
        }
        if (changes > 0) Row { T("Reset all edits in this step", color = p.accent, size = 12, modifier = Modifier.clickable { c.update(st.id) { it.copy(inputs = it.inputs.filter { x -> x.target == InputTarget.Variable }) } }.pointerHoverIcon(PointerIcon.Hand).padding(vertical = 2.dp)) }

    // ---- divider, then the second section: what this step passes on ----
    Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(p.border))
    OutputsSection(c, st, run)
}

/** "Pass on to later steps": the named values this step hands over, configured here where the data is produced. */
@Composable
private fun OutputsSection(c: Wf, st: WorkflowStep, run: StepRun?) {
    val p = LocalPal.current
    var pick by remember(st.id) { mutableStateOf<Int?>(null) }
    fun setOut(k: Int, f: (StepOutput) -> StepOutput) = c.update(st.id) { s -> s.copy(outputs = s.outputs.mapIndexed { i, o -> if (i == k) f(o) else o }) }
    val fields = remember(run) { ResponseFields.of(run?.result) }
    SectionLabel("PASS ON TO LATER STEPS")
    if (st.outputs.isEmpty()) T("Choose the values later steps will need — an id, a token, the whole body. Each one stays available to every step after this one: type {name} in any field, or click “Use value”. ({{name}} stays for environment variables.)", color = p.muted, size = 12)
    st.outputs.forEachIndexed { k, o ->
        val value = run?.result?.let { o.valueIn(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(o.name, { n -> setOut(k) { it.copy(name = n.filter { ch -> ch.isLetterOrDigit() || ch == '_' }) } }, Modifier.weight(1f), "name", mono = true, highlightVars = false)
            T("←", color = p.muted)
            Dropdown(o.kind.label, SourceKind.entries.map { it.label }, { l -> setOut(k) { it.copy(kind = SourceKind.entries.first { e -> e.label == l }) } }, Modifier.width(170.dp))
            if (fields.isNotEmpty()) Btn("Pick", { pick = if (pick == k) null else k }, primary = pick == k)
            IconBtn(Ic.Close, { c.update(st.id) { s -> s.copy(outputs = s.outputs.filterIndexed { i, _ -> i != k }) }; pick = null }, size = 14)
        }
        if (o.kind.needsPath) Field(o.path, { v -> setOut(k) { it.copy(path = v) } }, Modifier.fillMaxWidth(), o.kind.pathHint, mono = true, highlightVars = false)
        if (o.name.isNotBlank() && c.steps.requestsBefore(st.id).any { e -> e.outputs.any { it.name.trim() == o.name.trim() } }) T("An earlier step also passes on “${o.name}” — from here on this value replaces it.", color = p.muted, size = 11, modifier = Modifier.padding(start = 4.dp))
        if (value != null && o.name.isNotBlank()) T("= " + (if (value.second != null) "(${value.second})" else value.first.replace('\n', ' ').take(100).ifEmpty { "(empty)" }), color = if (value.second != null) p.err else p.ok, size = 11, mono = true, modifier = Modifier.padding(start = 4.dp))
        if (pick == k) Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(8.dp)).background(p.bg).padding(6.dp).verticalScroll(rememberScrollState())) {
            fields.forEach { f ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable { setOut(k) { cur -> cur.copy(name = cur.name.ifBlank { suggestName(f) }, kind = f.kind, path = f.path) }; pick = null }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    T(f.label, mono = true, size = 12, color = p.syntaxKey, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    T(f.preview, mono = true, size = 12, color = p.muted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Btn("Add output", { c.update(st.id) { it.copy(outputs = it.outputs + StepOutput()) }; pick = if (fields.isNotEmpty()) st.outputs.size else null }, icon = Ic.Add)
        if (fields.isEmpty() && run == null) T("Run this step (▶ Run to here) to pick from its real response.", color = p.muted, size = 11)
    }
}
