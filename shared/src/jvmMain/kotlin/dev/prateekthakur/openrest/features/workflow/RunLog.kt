package dev.prateekthakur.openrest.features.workflow

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.platform.*
import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal fun elapsed(ms: Long): String = if (ms < 1000) "+$ms ms" else "+%.1f s".format(ms / 1000.0)

/**
 * The run log, docked along the bottom of the editor like a console: a header bar that is always there (click to fold or unfold),
 * and below it every step of the last run. Drag the top edge to resize it.
 */
@Composable
internal fun DockedLog(c: Wf) {
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
