package dev.prateekthakur.openrest.features.workflow

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.features.sidebar.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Everything a block card needs; passed down instead of a dozen parameters. */
internal class Wf(val state: AppState, val tab: RequestTab, val col: Collection) {
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

internal fun setOverride(s: WorkflowStep, t: InputTarget, name: String, v: String): WorkflowStep {
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

/** Thin vertical guide lines, one per nesting level. */
internal fun Modifier.androidx_drawGuides(levels: Int, color: Color): Modifier = this.drawBehind {
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
internal fun BlockList(c: Wf, parent: String?, branch: Int, steps: List<WorkflowStep>) {
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
internal fun Frame(
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
internal fun UsedInfo(used: List<Wf.Avail>) {
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
internal fun WithVars(c: Wf, forId: String, content: @Composable () -> Unit) {
    val v = c.fieldVars(forId)
    CompositionLocalProvider(LocalVars provides v.env, LocalVarInfo provides v.envInfo, LocalFlowVars provides v.flow, LocalFlowInfo provides v.flowInfo) { content() }
}
