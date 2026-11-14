package dev.prateekthakur.openrest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.*
import dev.prateekthakur.openrest.core.Collection

/** Everything a block card needs; passed down instead of a dozen parameters. */
private class Wf(val state: AppState, val tab: RequestTab, val col: Collection) {
    val ws = tab.workflow!!
    val steps get() = ws.draft.steps
    /** The block open in the side pane, or null when the pane is closed. */
    val selectedId: String? get() = ws.selected?.takeIf { steps.findStep(it) != null }
    fun summary(st: WorkflowStep): String {
        fun conds() = st.conditions.joinToString(if (st.matchAll) " and " else " or ") { "${it.left.ifBlank { "…" }} ${it.op.label}" + (if (it.op.needsRight) " ${it.right.ifBlank { "…" }}" else "") }.ifBlank { "(no conditions yet)" }
        return when (st.kind) {
            StepKind.Request -> ""
            StepKind.If -> conds()
            StepKind.Loop -> when (st.loop) {
                LoopMode.ForEach -> "for each ${st.itemVar.ifBlank { "item" }} in ${st.list.ifBlank { "…" }}"
                LoopMode.Repeat -> "repeat ${st.count.ifBlank { "…" }} times"
                LoopMode.While -> "while ${conds()}"
            }
            StepKind.Set -> (st.varName.ifBlank { "…" }) + " = " + st.setOp.label.lowercase() + (if (st.setOp.needsList) " ${st.list.ifBlank { "…" }}" else " ${st.value}")
            StepKind.Wait -> "wait ${st.value.ifBlank { "…" }} ms"
            StepKind.Stop -> st.stopScope.label + (if (st.value.isNotBlank()) " — ${st.value}" else "")
        }
    }
    val numbers: Map<String, Int> get() = steps.walk().filter { it.kind == StepKind.Request }.mapIndexed { i, s -> s.id to i + 1 }.toMap()
    fun name(st: WorkflowStep): String = st.title.ifBlank { if (st.kind == StepKind.Request) (col.requestByRef(st.request)?.name ?: st.request.substringAfterLast('/').removeSuffix(".yaml")) else st.kind.label }
    fun update(id: String, f: (WorkflowStep) -> WorkflowStep) = ws.updateStep(id, f)
    /** Things a block can read data from: earlier requests and the variables made before it. */
    fun sources(id: String): Pair<List<WorkflowStep>, List<String>> = steps.requestsBefore(id) to steps.variablesBefore(id)

    /** Values available by `{name}` to block [forId]: passed on by earlier steps, or made by loops / Set blocks. */
    data class Avail(val name: String, val value: String?, val from: String, val variable: Boolean)

    fun available(forId: String): List<Avail> {
        val out = LinkedHashMap<String, Avail>()
        val (reqs, vars) = sources(forId)
        reqs.forEach { st ->
            val res = ws.runs[st.id]?.result
            st.outputs.filter { it.name.isNotBlank() }.forEach { o ->
                out[o.name.trim()] = Avail(o.name.trim(), res?.let { o.valueIn(it).first }, "from ${name(st)}", false)   // a later step's value replaces an earlier one
            }
        }
        vars.forEach { v -> out[v] = Avail(v, null, "loop / Set variable", true) }
        return out.values.toList()
    }

    /** The recorded execution behind log entry [index] (the n-th time that request block ran), if it still exists. */
    fun runFor(index: Int): StepRun? {
        val all = ws.events
        val e = all.getOrNull(index)?.takeIf { it.kind == StepKind.Request && !it.sub } ?: return null
        val hist = ws.history[e.stepId] ?: return null
        val same = { x: RunEvent -> x.stepId == e.stepId && x.kind == StepKind.Request && !x.sub }
        val nth = all.take(index).count(same)
        return hist.getOrNull(nth - (all.count(same) - hist.size))
    }

    /** The passed-on values (with where they come from) that [text] uses, as known at block [forId]. */
    fun usedIn(text: String, forId: String): List<Avail> {
        if (!text.contains("{")) return emptyList()
        val av = available(forId).associateBy { it.name }
        return FlowRefs.names(text).mapNotNull { av[it] }
    }

    /** Everything a request step takes from earlier steps: in the request itself and in this workflow's edits of it. */
    fun usedBy(st: WorkflowStep): List<Avail> {
        val req = col.requestByRef(st.request)?.request
        val text = buildString {
            req?.let { r ->
                append(r.url).append('\n'); r.headers.forEach { append(it.value).append('\n') }; r.params.forEach { append(it.value).append('\n') }
                r.pathParams.forEach { append(it.value).append('\n') }; append(r.bodyJson).append(r.bodyText).append(r.bodyXml).append(r.auth.token)
            }
            st.inputs.forEach { append('\n').append(it.value) }
        }
        return usedIn(text, st.id)
    }

    /** `{name}` / `{name.field}` in [text] replaced by the values known from the last run; null if nothing could be filled in. */
    fun preview(text: String, forId: String): String? {
        if (!text.contains("{")) return null
        val known = available(forId).filter { it.value != null }.associateBy { it.name }
        var hit = false
        val r = Regex("(?<!\\{)\\{([A-Za-z_][A-Za-z0-9_]*)((?:\\.[^{}\\s]*)?)}|\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)((?:\\.[^{}\\s]*)?)\\s*}}").replace(text) { m ->
            val nm = m.groupValues[1].ifEmpty { m.groupValues[3] }; val dotted = m.groupValues[2].ifEmpty { m.groupValues[4] }
            val a = known[nm] ?: return@replace m.value
            val rest = dotted.removePrefix(".")
            val v = if (rest.isEmpty()) a.value!! else WorkflowRunner.field(a.value!!, rest) ?: return@replace m.value
            hit = true; v
        }
        return if (hit) r.replace('\n', ' ') else null
    }

    /** What the two kinds of braces complete to inside block [forId]. */
    class Vars(val env: Set<String>, val envInfo: Map<String, VarInfo>, val flow: Set<String>, val flowInfo: Map<String, VarInfo>)

    fun fieldVars(forId: String): Vars {
        val flow = LinkedHashSet<String>(); val flowInfo = LinkedHashMap<String, VarInfo>()
        available(forId).forEach { a ->
            flow += a.name
            flowInfo[a.name] = VarInfo(a.value?.replace('\n', ' ')?.ifEmpty { "(empty)" } ?: if (a.variable) "" else "passed on — run to see the value", a.from)
        }
        val env = LinkedHashSet<String>(state.knownVars(tab))
        val envInfo = LinkedHashMap<String, VarInfo>()
        state.varInfo(tab).forEach { (k, v) -> envInfo[k] = VarInfo(v.first, v.second) }
        return Vars(env, envInfo, flow, flowInfo)
    }
}

private fun setOverride(s: WorkflowStep, t: InputTarget, name: String, v: String): WorkflowStep {
    val i = s.inputs.indexOfFirst { it.target == t && it.name.trim() == name }
    return s.copy(inputs = when {
        v.isBlank() && i >= 0 -> s.inputs.filterIndexed { k, _ -> k != i }
        i >= 0 -> s.inputs.mapIndexed { k, x -> if (k == i) x.copy(value = v) else x }
        v.isBlank() -> s.inputs
        else -> s.inputs + StepInput(t, name, v)
    })
}

/** Workflow builder + runner, shown as an editor tab. */
@Composable
fun WorkflowView(state: AppState, tab: RequestTab) {
    val p = LocalPal.current
    val ws = tab.workflow ?: return
    val col = state.collection(tab.collectionPath) ?: return
    val c = remember(tab, col) { Wf(state, tab, col) }
    val wf = ws.draft
    val failed = ws.events.count { !it.ok }
    Column(Modifier.fillMaxSize().background(p.bg)) {
        Row(Modifier.fillMaxWidth().background(p.panel).drawBottomBorder(p.border).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(Ic.Workflow, tint = p.accent, size = 20.dp)
            Field(wf.name, { n -> ws.update { it.copy(name = n) } }, Modifier.widthIn(min = 160.dp, max = 320.dp), "Workflow name", highlightVars = false)
            LabeledCheck("Stop when a request fails", wf.stopOnFail) { v -> ws.update { it.copy(stopOnFail = v) } }
            Spacer(Modifier.weight(1f))
            if (ws.events.isNotEmpty()) {
                Chip("${ws.events.size} step${if (ws.events.size == 1) "" else "s"} ran", if (failed == 0) p.ok else p.muted)
                if (failed > 0) Chip("$failed problem${if (failed == 1) "" else "s"}", p.err)
                T("${ws.runs.values.sumOf { it.result?.response?.timeMs ?: 0L }} ms in requests", color = p.muted, size = 12)
            }
            Btn("Save", { state.saveWorkflow(tab) }, icon = Ic.Save, primary = ws.dirty && !ws.running, enabled = ws.dirty)
            if (ws.running) Btn("Stop", { ws.cancelled = true }, danger = true, icon = Ic.Stop)
            else Btn("Run", { state.runWorkflow(tab) }, primary = !ws.dirty, icon = Ic.Play, enabled = wf.steps.walk().any { it.enabled })
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // ---- left: the flow as a compact outline ----
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp)) {
                if (wf.steps.isEmpty()) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, p.border, RoundedCornerShape(10.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Ico(Ic.Workflow, tint = p.muted, size = 34.dp)
                        T("Build a business process from API calls and logic", weight = FontWeight.SemiBold, size = 15)
                        T("Add requests from this collection, then click a step to configure it. Add If / else, loops, counters and variables to control the flow.", color = p.muted, size = 12)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                BlockList(c, null, 0, wf.steps)
                Spacer(Modifier.height(40.dp))
            }
            // ---- right: configuration of the selected block, only while one is selected ----
            if (c.selectedId != null) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(p.border))
                ConfigPane(c, Modifier.width(560.dp).fillMaxHeight().background(p.panel))
            }
        }
        DockedLog(c)
    }
}

private fun elapsed(ms: Long): String = if (ms < 1000) "+$ms ms" else "+%.1f s".format(ms / 1000.0)

/**
 * The run log, docked along the bottom of the editor like a console: a header bar that is always there (click to fold or unfold),
 * and below it every step of the last run. Drag the top edge to resize it.
 */
@Composable
private fun DockedLog(c: Wf) {
    val p = LocalPal.current
    val ws = c.ws
    val all = ws.events.toList()
    val problems = all.count { !it.ok }
    val requests = all.count { it.kind == StepKind.Request && !it.sub }
    val reqMs = all.sumOf { it.ms ?: 0L }
    /** An entry is a group when the entries after it are nested deeper (an If branch, a loop's rounds). */
    fun isGroup(i: Int): Boolean {
        val next = (i + 1 until all.size).firstOrNull { !all[it].sub } ?: return false
        return all[next].depth > all[i].depth
    }
    // everything starts collapsed: a group hides what is inside it until it is opened; "passed on" lives inside its request
    val shown = if (ws.logProblemsOnly) all.withIndex().filter { !it.value.ok && !it.value.sub } else {
        val out = mutableListOf<IndexedValue<RunEvent>>()
        var hideBelow: Int? = null
        for ((i, e) in all.withIndex()) {
            if (e.sub) continue
            val h = hideBelow
            if (h != null) { if (e.depth > h) continue else hideBelow = null }
            out += IndexedValue(i, e)
            if (isGroup(i) && ws.logOpen[i] != true) hideBelow = e.depth
        }
        out
    }
    val scroll = rememberScrollState()
    LaunchedEffect(all.size, ws.running) { if (ws.running) scroll.scrollTo(scroll.maxValue) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    Column(Modifier.fillMaxWidth().background(p.panel)) {
        // drag handle: resize the panel
        Box(
            Modifier.fillMaxWidth().height(4.dp).background(p.border).testTag("log-resize")
                .pointerHoverIcon(PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.N_RESIZE_CURSOR)))
                .pointerInput(Unit) { detectDragGestures { ch, d -> ch.consume(); ws.logHeight = (ws.logHeight - d.y / density).coerceIn(120f, 640f); ws.logExpanded = true } },
        )
        Row(
            Modifier.fillMaxWidth().height(26.dp).testTag("log-toggle").clickable { ws.logExpanded = !ws.logExpanded }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Chevron(ws.logExpanded)
            SectionLabel("RUN LOG")
            if (ws.running) { androidx.compose.material3.CircularProgressIndicator(Modifier.size(12.dp), color = p.accent, strokeWidth = 1.5.dp); T("running…", color = p.accent, size = 12) }
            else if (all.isEmpty()) T("run the workflow to see every step here", color = p.muted, size = 12)
            else if (problems == 0) T("all ok", color = p.ok, size = 11, weight = FontWeight.SemiBold) else T("$problems problem${if (problems == 1) "" else "s"}", color = p.err, size = 11, weight = FontWeight.SemiBold)
            if (all.isNotEmpty()) T("$requests request${if (requests == 1) "" else "s"}" + (if (reqMs > 0) " · $reqMs ms" else ""), color = p.muted, size = 12)
            Spacer(Modifier.weight(1f))
            if (all.isNotEmpty() && ws.logExpanded) {
                if (problems > 0) T(
                    "Only problems", color = if (ws.logProblemsOnly) Color.Black else p.accent, size = 11, weight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(if (ws.logProblemsOnly) p.accent else Color.Transparent).clickable { ws.logProblemsOnly = !ws.logProblemsOnly }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 6.dp, vertical = 2.dp),
                )
                IconBtn(Ic.Copy, {
                    Platform.copy(all.joinToString("\n") { e ->
                        val pad = "  ".repeat(e.depth)
                        if (e.sub && e.fields.isNotEmpty()) e.fields.joinToString("\n") { (k, v) -> "$pad  → $k = ${v.replace('\n', ' ')}" }
                        else pad + (if (e.sub) "  → " else if (e.ok) "✓ " else "✗ ") + e.text + (if (e.sub) "" else "   (" + elapsed(e.at) + ")")
                    })
                    c.state.showToast("Run log copied")
                }, size = 14)
            }
        }
        if (ws.logExpanded) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
            Column(Modifier.fillMaxWidth().height(ws.logHeight.dp).background(p.field).verticalScroll(scroll).padding(horizontal = 12.dp, vertical = 6.dp)) {
                if (all.isEmpty()) T(if (ws.running) "Waiting for the first step…" else "Nothing has run yet. Press Run — or the ▶ on a step to run up to it.", color = p.muted, size = 12, modifier = Modifier.padding(6.dp))
                else if (shown.isEmpty()) T("No problems.", color = p.muted, size = 12, modifier = Modifier.padding(6.dp))
                shown.forEach { (i, e) -> LogRow(c, e, i, isGroup(i), all.getOrNull(i + 1)?.takeIf { it.sub && it.stepId == e.stepId }, i + 1) }
            }
        }
    }
}

@Composable
private fun LogRow(c: Wf, e: RunEvent, index: Int, group: Boolean, passedOn: RunEvent?, passOnIndex: Int) {
    val run = c.runFor(index)
    val expandable = group || run != null
    val open = expandable && c.ws.logOpen[index] == true
    Column {
        LogLine(c, e, index, expandable, open, passedOn) { c.ws.logOpen[index] = !open }
        if (open && run != null) LogDetails(c, e, run, passedOn, passOnIndex, Modifier.padding(start = (e.depth * 14 + 28).dp, bottom = 6.dp, top = 2.dp))
    }
}

/** What went out for one request entry (and what it passed on), expanded inside the log. Responses are not shown here: "View response" opens them. */
@Composable
private fun LogDetails(c: Wf, e: RunEvent, run: StepRun, passedOn: RunEvent?, passOnIndex: Int, modifier: Modifier) {
    val p = LocalPal.current
    val r = run.result
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).border(1.dp, p.border, RoundedCornerShape(6.dp)).background(p.bg).padding(10.dp).testTag("log-details-${c.ws.events.indexOf(e)}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        r?.request?.let { req ->
            SectionLabel("SENT")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) { MethodPill(req.method); T(req.url, mono = true, size = 11, modifier = Modifier.weight(1f)) }
        }
        if (run.inputs.isNotEmpty()) {
            SectionLabel("VALUES FILLED IN")
            run.inputs.forEach { (k, v) ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    T(k, mono = true, size = 11, color = p.syntaxKey, modifier = Modifier.weight(0.35f))
                    T(v.replace('\n', ' ').ifEmpty { "(empty)" }, mono = true, size = 11, modifier = Modifier.weight(0.65f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        run.notes.forEach { T("⚠ $it", color = p.err, size = 11) }
        r?.error?.let { T(it, color = p.err, mono = true, size = 11) }
        if (r != null && r.tests.isNotEmpty()) TestsList(r.tests)
        c.usedBy(run.step).takeIf { it.isNotEmpty() }?.let { used ->
            SectionLabel("USED FROM EARLIER STEPS")
            used.forEach { a ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    T("{${a.name}}", mono = true, size = 11, weight = FontWeight.SemiBold, color = p.accent, modifier = Modifier.weight(0.25f), maxLines = 1)
                    T((a.value ?: "").replace('\n', ' ').ifEmpty { "(empty)" }, mono = true, size = 11, modifier = Modifier.weight(0.5f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    T(a.from, size = 11, color = p.muted, modifier = Modifier.weight(0.25f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        passedOn?.takeIf { it.fields.isNotEmpty() }?.let { po ->
            Column(Modifier.fillMaxWidth().testTag("log-passon-$passOnIndex").clip(RoundedCornerShape(6.dp)).border(1.dp, p.border, RoundedCornerShape(6.dp)).background(p.field)) {
                Row(Modifier.fillMaxWidth().background(p.hover.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    T("PASSED ON", color = p.muted, size = 10, weight = FontWeight.SemiBold, modifier = Modifier.weight(0.3f))
                    T("VALUE", color = p.muted, size = 10, weight = FontWeight.SemiBold, modifier = Modifier.weight(0.7f))
                    Spacer(Modifier.width(36.dp))
                }
                po.fields.forEach { (k, v) ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        T(k, mono = true, size = 12, weight = FontWeight.SemiBold, color = p.accent, modifier = Modifier.weight(0.3f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        T(v.replace('\n', ' ').ifEmpty { "(empty)" }, mono = true, size = 12, color = if (v.isEmpty()) p.muted else p.text, modifier = Modifier.weight(0.7f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        T("copy", color = p.accent, size = 10, maxLines = 1, modifier = Modifier.width(36.dp).testTag("passon-copy-value-$k").clickable { Platform.copy(v); c.state.showToast("Copied the value of $k") }.pointerHoverIcon(PointerIcon.Hand))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            T("View full response", color = p.accent, size = 11, modifier = Modifier.clickable { c.state.modal = Modal.ViewResponse(c.tab.id, e.stepId) }.pointerHoverIcon(PointerIcon.Hand))
            T("Configure this step", color = p.accent, size = 11, modifier = Modifier.clickable { c.ws.selected = e.stepId }.pointerHoverIcon(PointerIcon.Hand))
        }
    }
}

@Composable
private fun LogLine(c: Wf, e: RunEvent, index: Int, expandable: Boolean, open: Boolean, passedOn: RunEvent?, onToggle: () -> Unit) {
    val p = LocalPal.current
    val st = c.steps.findStep(e.stepId)
    val isReq = e.kind == StepKind.Request && !e.sub
    val method = st?.let { s -> s.inputs.firstOrNull { it.target == InputTarget.Method && it.value.isNotBlank() }?.value?.trim()?.uppercase() ?: c.col.requestByRef(s.request)?.request?.method }
    val tagColor = when (e.kind) { StepKind.If -> p.syntaxKey; StepKind.Loop -> p.accent; StepKind.Set -> p.ok; StepKind.Wait -> p.muted; StepKind.Stop -> p.err; else -> p.muted }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("log-$index").clip(RoundedCornerShape(5.dp)).clickable(enabled = expandable) { onToggle() }.pointerHoverIcon(if (expandable) PointerIcon.Hand else PointerIcon.Default),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        // indent guides: one thin line per level of nesting, so loop rounds and branches read as groups
        if (e.depth > 0) Box(Modifier.width((e.depth * 14).dp).fillMaxHeight().androidx_drawGuides(e.depth, p.border))
        run {
            if (expandable) Chevron(open) else Spacer(Modifier.width(14.dp))
            Ico(if (e.ok) Ic.CheckCircle else Ic.Cancel, tint = if (e.ok) p.ok else p.err, size = 14.dp)
            if (isReq && method != null) MethodPill(method)
            else Box(Modifier.width(METHOD_SLOT + 4.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(tagColor.copy(alpha = 0.16f)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    T(when (e.kind) { StepKind.If -> "IF"; StepKind.Loop -> "LOOP"; StepKind.Set -> "SET"; StepKind.Wait -> "WAIT"; StepKind.Stop -> "STOP"; else -> "REQ" }, color = tagColor, size = 9, weight = FontWeight.Bold, maxLines = 1)
                }
            }
            Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
                if (isReq && e.label != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(e.label, weight = FontWeight.SemiBold, size = 12, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (e.status != null) { T("${e.status}", color = statusColor(e.status, p), weight = FontWeight.SemiBold, size = 12); T("${e.ms ?: 0} ms", color = p.muted, size = 11) }
                        if (!open && passedOn != null && passedOn.fields.isNotEmpty()) T("→ " + passedOn.fields.joinToString(", ") { it.first }, color = p.muted, size = 11, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                    // what went wrong gets its own line, so it is never squeezed next to the name
                    if (e.status == null || !e.ok) e.text.removePrefix(e.label).removePrefix(" · ").takeIf { it.isNotBlank() && !(e.status != null && e.ok) }?.let {
                        T(it.let { m -> if (e.status != null) m.substringAfter("${e.ms} ms", m).removePrefix(" · ") else m }.ifBlank { "failed" }, color = p.err, size = 11, mono = true)
                    }
                } else T(e.text, size = 12, color = if (e.ok) p.text else p.err)
            }
            T(elapsed(e.at), color = p.muted, size = 11)
            if (isReq && c.ws.history[e.stepId].isNullOrEmpty().not()) T("View response", color = p.accent, size = 11, modifier = Modifier.clickable { c.state.modal = Modal.ViewResponse(c.tab.id, e.stepId) }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 4.dp, vertical = 2.dp))
        }
    }
}

/** Thin vertical guide lines, one per nesting level. */
private fun Modifier.androidx_drawGuides(levels: Int, color: Color): Modifier = this.drawBehind {
    for (i in 0 until levels) {
        val x = (i * 14 + 6).dp.toPx()
        drawLine(color, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1.dp.toPx())
    }
}

/** The side pane: header (what is selected, run to here) and the block's full configuration. */
@Composable
private fun ConfigPane(c: Wf, modifier: Modifier) {
    val p = LocalPal.current
    val st = c.selectedId?.let { c.steps.findStep(it) } ?: return
    Column(modifier) {
        key(st.id) {
            val isReq = st.kind == StepKind.Request
            Row(Modifier.fillMaxWidth().drawBottomBorder(p.border).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isReq) T(c.name(st), weight = FontWeight.SemiBold, size = 15, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                else Field(st.title, { v -> c.update(st.id) { it.copy(title = v) } }, Modifier.weight(1f), st.kind.label + " — name (optional)", bordered = false, highlightVars = false)
                T(if (isReq) "REQUEST STEP" else st.kind.label.uppercase(), color = p.muted, size = 10, weight = FontWeight.SemiBold)
                LabeledCheck("On", st.enabled) { v -> c.update(st.id) { it.copy(enabled = v) } }
                IconBtn(Ic.Close, { c.ws.selected = null }, Modifier.testTag("close-config"), size = 17)
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!st.enabled) T("This block is switched off — it is skipped when the workflow runs.", color = p.muted, size = 12)
                if (isReq) RequestConfig(c, st) else LogicConfig(c, st)
            }
        }
    }
}

/** A list of blocks with the "add" button below; used for the top level and for every branch of If / Loop. */
@Composable
private fun BlockList(c: Wf, parent: String?, branch: Int, steps: List<WorkflowStep>) {
    val p = LocalPal.current
    steps.forEachIndexed { i, st ->
        key(st.id) {
            BlockCard(c, st)
            Box(Modifier.padding(start = 25.dp).width(2.dp).height(14.dp).background(p.border))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Btn(if (steps.isEmpty() && parent == null) "Add first step" else "Add step", { c.state.modal = Modal.AddStep(c.tab.id, parent, branch) }, icon = Ic.Add, primary = steps.isEmpty() && parent == null)
    }
}

@Composable
private fun BlockCard(c: Wf, st: WorkflowStep) {
    when (st.kind) {
        StepKind.Request -> RequestRow(c, st)
        else -> LogicRow(c, st)
    }
}

/** One block in the outline: badge, name, status, actions; clicking it opens its configuration in the side pane. */
@Composable
private fun Frame(
    c: Wf, st: WorkflowStep, badge: @Composable () -> Unit, title: @Composable RowScope.() -> Unit,
    trailing: @Composable RowScope.() -> Unit = {}, sub: (@Composable () -> Unit)? = null, extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val p = LocalPal.current
    val active = c.ws.current == st.id && c.ws.running
    val selected = c.selectedId == st.id
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) p.accent.copy(alpha = 0.09f) else p.field)
            .border(if (selected || active) 2.dp else 1.dp, if (active) p.accent else if (selected) p.accent.copy(alpha = 0.75f) else p.border, RoundedCornerShape(10.dp))
            .clickable { c.ws.selected = st.id }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            badge()
            Check(st.enabled, { v -> c.update(st.id) { it.copy(enabled = v) } })
            title()
            // actions, always at the right end
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                trailing()
                if (!c.ws.running && st.enabled) IconBtn(Ic.Play, { c.state.runWorkflow(c.tab, st.id) }, color = p.accent, size = 15)
                IconBtn(Ic.MoveUp, { c.state.moveWorkflowStep(c.tab, st.id, -1) }, size = 14)
                IconBtn(Ic.MoveDown, { c.state.moveWorkflowStep(c.tab, st.id, 1) }, size = 14)
                IconBtn(Ic.Delete, { c.state.removeWorkflowStep(c.tab, st.id) }, color = p.err, size = 14)
            }
        }
        sub?.let { Box(Modifier.padding(start = 36.dp)) { it() } }
        extra?.invoke(this)
    }
}

// =====================================================================================================
// value input + picker
// =====================================================================================================

/** "Where does this come from": one line per passed-on value a field uses. */
@Composable
private fun UsedInfo(used: List<Wf.Avail>) {
    val p = LocalPal.current
    used.forEach { a ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            T("←", color = p.muted, size = 11)
            T("{${a.name}}", color = p.accent, size = 11, mono = true, weight = FontWeight.SemiBold)
            T(a.from, color = p.muted, size = 11)
            a.value?.let { v -> T("·  " + v.replace('\n', ' ').take(60).ifEmpty { "(empty)" }, color = p.muted, size = 11, mono = true, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Provides the two completion lists (`{{env}}` and `{passed-on}`) for fields of block [forId]. */
@Composable
private fun WithVars(c: Wf, forId: String, content: @Composable () -> Unit) {
    val v = c.fieldVars(forId)
    CompositionLocalProvider(LocalVars provides v.env, LocalVarInfo provides v.envInfo, LocalFlowVars provides v.flow, LocalFlowInfo provides v.flowInfo) { content() }
}

/** A text field for a template value; `{` completes passed-on values, `{{` environment variables, and a button opens the list of available values. */
@Composable
private fun ValueInput(c: Wf, forId: String, value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, mono: Boolean = true) {
    val p = LocalPal.current
    val canLink = c.available(forId).isNotEmpty() || c.steps.requestsBefore(forId).isNotEmpty()
    var picking by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            WithVars(c, forId) { Field(value, onChange, Modifier.weight(1f), placeholder, mono = mono, highlightVars = true) }
            if (canLink) Btn("Use value", { picking = !picking }, primary = picking, icon = Ic.Variables)
        }
        c.preview(value, forId)?.let { T("= $it", color = p.ok, size = 11, mono = true, maxLines = 1) }
        UsedInfo(c.usedIn(value, forId))
        if (picking) ValuePicker(c, forId, { tok -> onChange(value + tok); picking = false }, null) { picking = false }
    }
}

/**
 * The values available at this point, by name. Click one to insert `{name}`. A value that no step passes on yet can be
 * picked straight from a response; that adds it to the producing step's "Pass on" list.
 */
@Composable
private fun ValuePicker(c: Wf, forId: String, onInsert: (String) -> Unit, targetLabel: String? = null, onClose: () -> Unit) {
    val p = LocalPal.current
    val avail = c.available(forId)
    val reqs = c.steps.requestsBefore(forId)
    var filter by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(avail.isEmpty() && reqs.isNotEmpty()) }
    var stepId by remember { mutableStateOf(reqs.lastOrNull { c.ws.runs[it.id] != null }?.id ?: reqs.lastOrNull()?.id ?: "") }
    val shown = avail.filter { filter.isBlank() || it.name.contains(filter.trim(), true) || (it.value ?: "").contains(filter.trim(), true) }
    val nums = c.numbers
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(p.bg).border(1.dp, p.border, RoundedCornerShape(8.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            T(if (targetLabel != null) "Fill “$targetLabel” with" else "Use a value", color = p.muted, size = 12)
            if (avail.size > 6) Field(filter, { filter = it }, Modifier.weight(1f), "Search…", highlightVars = false) else Spacer(Modifier.weight(1f))
            IconBtn(Ic.Close, onClose, size = 14)
        }
        if (avail.isEmpty()) T("Nothing is passed on yet. Add values under “Pass on to later steps” in the step that produces them — or pick one from a response below.", color = p.muted, size = 12)
        Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
            shown.forEach { a ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable { onInsert("{${a.name}}") }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 6.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("{${a.name}}", mono = true, size = 13, weight = FontWeight.SemiBold, color = p.accent, modifier = Modifier.weight(1f), maxLines = 1)
                    T(when { a.value == null -> if (a.variable) "" else "run to see the value"; a.value.isEmpty() -> "(empty)"; else -> a.value.replace('\n', ' ').take(60) }, mono = true, size = 12, color = p.muted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    T(a.from, size = 11, color = p.muted, maxLines = 1)
                }
            }
            if (shown.isEmpty() && avail.isNotEmpty()) T("Nothing matches “$filter”.", color = p.muted, size = 12)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            T("or type a name / field", color = p.muted, size = 12)
            Field(manual, { manual = it }, Modifier.weight(1f), "e.g. item.id or user.name (Enter)", mono = true, highlightVars = false, onEnter = { if (manual.isNotBlank()) onInsert("{${manual.trim()}}") })
        }
        if (reqs.isNotEmpty()) {
            T(if (more) "Hide response fields ▲" else "Pick a field from a response ▼", color = p.accent, size = 12, modifier = Modifier.clickable { more = !more }.pointerHoverIcon(PointerIcon.Hand))
            if (more) {
                val options = reqs.map { "${c.name(it)}  (step ${nums[it.id]})" to it.id }
                val run = c.ws.runs[stepId]
                val fields = remember(run) { ResponseFields.of(run?.result) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("From the response of", color = p.muted, size = 12)
                    Dropdown(options.firstOrNull { it.second == stepId }?.first ?: "", options.map { it.first }, { l -> stepId = options.first { it.first == l }.second })
                }
                if (run == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        T("That step hasn't run yet.", color = p.muted, size = 12)
                        Btn("Run up to it", { c.state.runWorkflow(c.tab, stepId) }, icon = Ic.Play, primary = true, enabled = !c.ws.running)
                    }
                } else if (fields.isEmpty()) T("That step has no usable response.", color = p.err, size = 12)
                else Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                    fields.forEach { f ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable {
                            val taken = c.steps.walk().flatMap { it.outputs }.map { it.name.trim() }.toSet()
                            val existing = c.steps.findStep(stepId)?.outputs?.firstOrNull { it.kind == f.kind && it.path == f.path && it.name.isNotBlank() }
                            val name = existing?.name?.trim() ?: run { var n = suggestName(f); var i = 2; while (n in taken) n = suggestName(f) + i++; n }
                            if (existing == null) c.update(stepId) { s -> s.copy(outputs = s.outputs + StepOutput(name, f.kind, f.path)) }
                            onInsert("{$name}")
                        }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            T(f.label, mono = true, size = 12, color = p.syntaxKey, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            T(f.preview, mono = true, size = 12, color = p.muted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                T("Picking a field adds it to that step's “Pass on to later steps” so it keeps flowing.", color = p.muted, size = 11)
            }
        }
    }
}

/** A short, valid output name for a response field (`data.items[0].id` → `id`, `header Content-Type` → `contentType`). */
private fun suggestName(f: ResponseField): String {
    val raw = when (f.kind) {
        SourceKind.Field -> f.path.substringAfterLast('.').substringBefore('[').ifEmpty { f.path.substringBefore('[') }
        SourceKind.Header -> f.path
        SourceKind.Status -> "status"; SourceKind.Time -> "responseTime"; SourceKind.Body -> "body"; SourceKind.Response -> "response"
    }
    val parts = raw.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
    return parts.mapIndexed { i, w -> if (i == 0) w.replaceFirstChar { it.lowercase() } else w.replaceFirstChar { it.uppercase() } }.joinToString("").ifEmpty { "value" }
}

// =====================================================================================================
// request block: the request itself, editable in place
// =====================================================================================================

/**
 * One value of a request (a header value, a path param, the URL, a body field…).
 * Shows the request's real value; typing changes it for this workflow only. `{name}` uses a value passed on by an earlier step.
 */
@Composable
private fun ValueCell(
    c: Wf, forId: String, value: String, current: String, onSet: (String) -> Unit, targetLabel: String,
    modifier: Modifier = Modifier, resolved: String? = null, needsValue: Boolean = false,
) {
    val p = LocalPal.current
    val canLink = c.available(forId).isNotEmpty() || c.steps.requestsBefore(forId).isNotEmpty()
    val has = value.isNotBlank()
    var picking by remember { mutableStateOf(false) }
    // local text so clearing the field while typing does not snap back to the original value
    var text by remember { mutableStateOf(if (has) value else current) }
    LaunchedEffect(value, current) {
        val want = if (has) value else current
        if (want != text && !(text.isEmpty() && !has)) text = want
    }
    val shownText = if (has) value else text
    val preview = resolved ?: c.preview(shownText, forId)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            WithVars(c, forId) {
                Field(text, { v -> text = v; onSet(if (v == current) "" else v) }, Modifier.weight(1f), if (needsValue) "needs a value — type { to use a passed-on value" else current.ifEmpty { "(empty)" }, mono = true, highlightVars = true)
            }
            if (canLink) Btn("Use value", { picking = !picking }, primary = picking || (needsValue && !has), icon = Ic.Variables)
            if (has) IconBtn(Ic.Refresh, { onSet(""); text = current }, color = p.accent, size = 15)
        }
        if (preview != null) T("= " + preview.replace('\n', ' ').take(120).ifEmpty { "(empty)" }, color = if (preview.isEmpty() && resolved != null) p.err else p.ok, size = 11, mono = true, maxLines = 1)
        UsedInfo(c.usedIn(shownText, forId))
        if (picking) ValuePicker(c, forId, { tok ->
            // a value that is just one {{name}} gets replaced; otherwise the name is added to what was typed (e.g. "Bearer ")
            val single = has && Regex("^\\s*\\{\\{?[^{}]+}}?\\s*$").matches(value)
            onSet(if (has && !single) value + tok else tok); picking = false
        }, targetLabel) { picking = false }
    }
}

/** A labelled [ValueCell] bound to the step's override for ([target], [name]). */
@Composable
private fun FieldRow(c: Wf, st: WorkflowStep, target: InputTarget, name: String, current: String, label: String, needsValue: Boolean = false) {
    val p = LocalPal.current
    val value = st.inputs.firstOrNull { it.target == target && it.name.trim() == name }?.value.orEmpty()
    val resolved = c.ws.runs[st.id]?.inputs?.firstOrNull { it.first == (if (target.needsKey) "${target.label} $name" else target.label) }?.second
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Row(Modifier.width(190.dp).padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (value.isNotBlank()) Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(p.accent))
            T(label, size = 12, color = if (needsValue && value.isBlank()) p.err else p.text, mono = true, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ValueCell(c, st.id, value, current, { v -> c.update(st.id) { s -> setOverride(s, target, name, v) } }, Slots.label(target, name), Modifier.weight(1f), resolved, needsValue)
    }
}

/** Rows added only for this workflow (a header the request doesn't have, an extra query parameter or body field). */
@Composable
private fun AddedRows(c: Wf, st: WorkflowStep, target: InputTarget, slots: List<Slot>, nameHint: String) {
    st.inputs.forEachIndexed { k, inp ->
        if (inp.target == target && slots.none { it.target == target && it.name == inp.name.trim() && inp.name.isNotBlank() }) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Field(inp.name, { n -> c.update(st.id) { s -> s.copy(inputs = s.inputs.mapIndexed { i, x -> if (i == k) x.copy(name = n) else x }) } }, Modifier.width(190.dp), nameHint, mono = true, highlightVars = false)
                ValueCell(c, st.id, inp.value, "", { v -> c.update(st.id) { s -> s.copy(inputs = s.inputs.mapIndexed { i, x -> if (i == k) x.copy(value = v) else x }) } }, Slots.label(target, inp.name.trim().ifEmpty { "…" }), Modifier.weight(1f))
                IconBtn(Ic.Close, { c.update(st.id) { s -> s.copy(inputs = s.inputs.filterIndexed { i, _ -> i != k }) } }, size = 14)
            }
        }
    }
}

@Composable
private fun AddRowButton(c: Wf, st: WorkflowStep, target: InputTarget, label: String) {
    Row { Btn(label, { c.update(st.id) { it.copy(inputs = it.inputs + StepInput(target, "", "")) } }, icon = Ic.Add) }
}

@Composable
private fun RequestRow(c: Wf, st: WorkflowStep) {
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
private fun RequestConfig(c: Wf, st: WorkflowStep) {
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

private fun humanSize(n: Int): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "%.1f KB".format(n / 1024.0)
    else -> "%.1f MB".format(n / 1024.0 / 1024.0)
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
            T(humanSize(resp.size), color = p.muted, size = 12)
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

private fun uniqueName(existing: List<StepOutput>, base: String): String {
    var n = base; var i = 2
    while (existing.any { it.name == n }) n = "$base${i++}"
    return n
}

// =====================================================================================================
// logic blocks
// =====================================================================================================

@Composable
private fun LogicRow(c: Wf, st: WorkflowStep) {
    val p = LocalPal.current
    val tag = when (st.kind) { StepKind.If -> "IF"; StepKind.Loop -> "LOOP"; StepKind.Set -> "SET"; StepKind.Wait -> "WAIT"; else -> "STOP" }
    val tagColor = when (st.kind) { StepKind.If -> p.syntaxKey; StepKind.Loop -> p.accent; StepKind.Set -> p.ok; StepKind.Wait -> p.muted; else -> p.err }
    Frame(c, st, {
        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(tagColor.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 4.dp)) { T(tag, color = tagColor, weight = FontWeight.Bold, size = 11) }
    }, {
        T(st.title.ifBlank { st.kind.label }, weight = FontWeight.SemiBold, color = if (st.enabled) p.text else p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }, {}, sub = {
        T(c.summary(st), color = p.muted, size = 12, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }, extra = {
        when (st.kind) {
            StepKind.If -> { Branch(c, st, "THEN", 0, st.children); Branch(c, st, "ELSE", 1, st.otherwise) }
            StepKind.Loop -> Branch(c, st, "REPEAT THESE STEPS", 0, st.children)
            else -> {}
        }
    })
}

/** Settings of a logic block, in the side pane. (Its nested steps live in the outline.) */
@Composable
private fun LogicConfig(c: Wf, st: WorkflowStep) {
    val p = LocalPal.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    when (st.kind) {
        StepKind.If -> {
            Conditions(c, st)
        }
        StepKind.Loop -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Dropdown(st.loop.label, LoopMode.entries.map { it.label }, { l -> c.update(st.id) { s ->
                    val m = LoopMode.entries.first { it.label == l }
                    s.copy(loop = m, count = if (m == LoopMode.Repeat && s.count.isBlank()) "3" else s.count, conditions = if (m == LoopMode.While && s.conditions.isEmpty()) listOf(Condition()) else s.conditions)
                } })
                T("at most", color = p.muted, size = 12)
                Field(st.maxIterations.toString(), { v -> v.filter { it.isDigit() }.take(4).toIntOrNull()?.let { n -> c.update(st.id) { it.copy(maxIterations = n) } } }, Modifier.width(70.dp), "100", highlightVars = false)
                T("rounds", color = p.muted, size = 12)
            }
            when (st.loop) {
                LoopMode.ForEach -> {
                    T("List to loop over — e.g. Insert data → a response field that is a list (items, orders…)", color = p.muted, size = 12)
                    ValueInput(c, st.id, st.list, { v -> c.update(st.id) { it.copy(list = v) } }, "list, e.g. a response field holding an array")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        T("Call each item", color = p.muted, size = 12)
                        Field(st.itemVar, { v -> c.update(st.id) { it.copy(itemVar = v.filter { ch -> ch.isLetterOrDigit() || ch == '_' }) } }, Modifier.width(110.dp), "item", mono = true, highlightVars = false)
                        T("and its position", color = p.muted, size = 12)
                        Field(st.indexVar, { v -> c.update(st.id) { it.copy(indexVar = v.filter { ch -> ch.isLetterOrDigit() || ch == '_' }) } }, Modifier.width(110.dp), "index", mono = true, highlightVars = false)
                        T("(starts at 0)", color = p.muted, size = 12)
                    }
                    T("Inside the loop use {${st.itemVar.ifBlank { "item" }}} or a field of it like {${st.itemVar.ifBlank { "item" }}.id}, and {${st.indexVar.ifBlank { "index" }}}.", color = p.muted, size = 11)
                }
                LoopMode.Repeat -> {
                    T("How many times", color = p.muted, size = 12)
                    ValueInput(c, st.id, st.count, { v -> c.update(st.id) { it.copy(count = v) } }, "3  (a number, or Insert data e.g. a count)")
                    T("The round number is {${st.indexVar.ifBlank { "index" }}} (starts at 0).", color = p.muted, size = 11)
                }
                LoopMode.While -> {
                    Conditions(c, st)
                    T("The round number is {${st.indexVar.ifBlank { "index" }}} (starts at 0). Checked before every round.", color = p.muted, size = 11)
                }
            }
        }
        StepKind.Set -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Dropdown(st.setOp.label, SetOp.entries.map { it.label }, { l -> c.update(st.id) { s -> val o = SetOp.entries.first { it.label == l }; s.copy(setOp = o, varName = s.varName.ifBlank { when (o) { SetOp.Count -> "count"; SetOp.Sum -> "total"; SetOp.First -> "first"; SetOp.Last -> "last"; else -> "" } }) } })
                T("and call it", color = p.muted, size = 12)
                Field(st.varName, { v -> c.update(st.id) { it.copy(varName = v.filter { ch -> ch.isLetterOrDigit() || ch == '_' }) } }, Modifier.width(150.dp), "variable name", mono = true, highlightVars = false)
            }
            if (st.setOp.needsList) {
                T("List", color = p.muted, size = 12)
                ValueInput(c, st.id, st.list, { v -> c.update(st.id) { it.copy(list = v) } }, "list, e.g. a response field holding an array")
                if (st.setOp == SetOp.Sum) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("Field to add up (leave empty if the list holds plain numbers)", color = p.muted, size = 12)
                    Field(st.path, { v -> c.update(st.id) { it.copy(path = v) } }, Modifier.width(180.dp), "e.g. price", mono = true, highlightVars = false)
                }
            } else {
                T("Value", color = p.muted, size = 12)
                ValueInput(c, st.id, st.value, { v -> c.update(st.id) { it.copy(value = v) } }, "text, a number, or Insert data")
            }
            if (st.varName.isNotBlank()) T("Use it in later steps as {${st.varName}}" + (if (st.setOp == SetOp.First || st.setOp == SetOp.Last) " (or {${st.varName}.field} for one field)" else ""), color = p.muted, size = 11)
        }
        StepKind.Wait -> {
            T("Milliseconds to wait (1000 = 1 second, up to 60000)", color = p.muted, size = 12)
            ValueInput(c, st.id, st.value, { v -> c.update(st.id) { it.copy(value = v) } }, "1000")
        }
        StepKind.Stop -> {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Dropdown(st.stopScope.label, StopScope.entries.map { it.label }, { l -> c.update(st.id) { it.copy(stopScope = StopScope.entries.first { s -> s.label == l }) } })
                LabeledCheck("Mark as failed", st.stopFail) { v -> c.update(st.id) { it.copy(stopFail = v) } }
            }
            T("Message (optional)", color = p.muted, size = 12)
            ValueInput(c, st.id, st.value, { v -> c.update(st.id) { it.copy(value = v) } }, "e.g. out of stock", mono = false)
        }
        else -> {}
    }
    }
}

@Composable
private fun Conditions(c: Wf, st: WorkflowStep) {
    val p = LocalPal.current
    fun setConds(f: (List<Condition>) -> List<Condition>) = c.update(st.id) { it.copy(conditions = f(it.conditions)) }
    st.conditions.forEachIndexed { k, cd ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            T(if (k == 0) "If" else if (st.matchAll) "and" else "or", color = p.muted, size = 12, modifier = Modifier.width(26.dp).padding(top = 10.dp))
            ValueInput(c, st.id, cd.left, { v -> setConds { l -> l.mapIndexed { i, x -> if (i == k) x.copy(left = v) else x } } }, "value, e.g. a response status or a variable", Modifier.weight(1f))
            Box(Modifier.padding(top = 2.dp)) { Dropdown(cd.op.label, CondOp.entries.map { it.label }, { l -> setConds { list -> list.mapIndexed { i, x -> if (i == k) x.copy(op = CondOp.entries.first { o -> o.label == l }) else x } } }) }
            if (cd.op.needsRight) ValueInput(c, st.id, cd.right, { v -> setConds { l -> l.mapIndexed { i, x -> if (i == k) x.copy(right = v) else x } } }, "value", Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            Box(Modifier.padding(top = 6.dp)) { IconBtn(Ic.Close, { setConds { l -> l.filterIndexed { i, _ -> i != k } } }, size = 14) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Btn("Add condition", { setConds { it + Condition() } }, icon = Ic.Add)
        if (st.conditions.size > 1) Dropdown(
            if (st.matchAll) "All must be true" else "Any can be true", listOf("All must be true", "Any can be true"),
            { v -> c.update(st.id) { it.copy(matchAll = v.startsWith("All")) } },
        )
        if (st.conditions.isEmpty()) T("No conditions: this is never true.", color = p.err, size = 12)
    }
}

/** A titled, indented list of nested blocks (Then / Else / loop body). */
@Composable
private fun Branch(c: Wf, st: WorkflowStep, title: String, branch: Int, steps: List<WorkflowStep>) {
    val p = LocalPal.current
    SectionLabel(title)
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Box(Modifier.padding(start = 6.dp, end = 12.dp).width(2.dp).fillMaxHeight().background(p.accent.copy(alpha = 0.5f)))
        Column(Modifier.weight(1f)) {
            if (steps.isEmpty()) T("Empty — nothing happens here.", color = p.muted, size = 12, modifier = Modifier.padding(bottom = 6.dp))
            BlockList(c, st.id, branch, steps)
        }
    }
}

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
