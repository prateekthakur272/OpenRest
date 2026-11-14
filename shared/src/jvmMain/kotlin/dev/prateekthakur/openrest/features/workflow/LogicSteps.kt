package dev.prateekthakur.openrest.features.workflow

import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// =====================================================================================================
// logic blocks
// =====================================================================================================

@Composable
internal fun LogicRow(c: Wf, st: WorkflowStep) {
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
internal fun LogicConfig(c: Wf, st: WorkflowStep) {
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
