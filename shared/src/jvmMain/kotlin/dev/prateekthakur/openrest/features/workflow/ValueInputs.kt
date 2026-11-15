package dev.prateekthakur.openrest.features.workflow

import dev.prateekthakur.openrest.app.runWorkflow
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

/** A text field for a template value; `{` completes passed-on values, `{{` environment variables, and a button opens the list of available values. */
@Composable
internal fun ValueInput(c: Wf, forId: String, value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, mono: Boolean = true) {
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
internal fun ValuePicker(c: Wf, forId: String, onInsert: (String) -> Unit, targetLabel: String? = null, onClose: () -> Unit) {
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
internal fun suggestName(f: ResponseField): String {
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
internal fun ValueCell(
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
internal fun FieldRow(c: Wf, st: WorkflowStep, target: InputTarget, name: String, current: String, label: String, needsValue: Boolean = false) {
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
internal fun AddedRows(c: Wf, st: WorkflowStep, target: InputTarget, slots: List<Slot>, nameHint: String) {
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
internal fun AddRowButton(c: Wf, st: WorkflowStep, target: InputTarget, label: String) {
    Row { Btn(label, { c.update(st.id) { it.copy(inputs = it.inputs + StepInput(target, "", "")) } }, icon = Ic.Add) }
}

internal fun uniqueName(existing: List<StepOutput>, base: String): String {
    var n = base; var i = 2
    while (existing.any { it.name == n }) n = "$base${i++}"
    return n
}
