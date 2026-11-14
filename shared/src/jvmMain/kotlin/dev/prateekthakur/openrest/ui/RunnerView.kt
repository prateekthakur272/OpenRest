package dev.prateekthakur.openrest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.*

/** Collection / folder runner, shown as a regular editor tab. Settings on the left, requests (then results) on the right. */
@Composable
fun RunnerView(state: AppState, tab: RequestTab) {
    val p = LocalPal.current
    val rs = tab.runner ?: return
    val col = state.collection(tab.collectionPath) ?: return
    val all = remember(col, rs.folder) { state.runnerRequests(col, rs.folder) }
    val chosen = rs.chosen
    val count = chosen.size * rs.iters
    val showResults = rs.results.isNotEmpty() || rs.running
    val passed = rs.results.count { it.result.error == null && it.result.tests.all { t -> t.passed } && !it.result.skipped }
    val total = rs.results.sumOf { it.result.response?.timeMs ?: 0L }

    @Composable fun Link(text: String, enabled: Boolean = true, onClick: () -> Unit) =
        T(text, color = if (enabled) p.accent else p.muted, size = 13, modifier = Modifier.clickable(enabled = enabled) { onClick() }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 6.dp, vertical = 4.dp))

    Row(Modifier.fillMaxSize().background(p.bg)) {
        // ---- left: settings ----
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(Ic.Play, tint = p.text, size = 18.dp)
                T("Runner", size = 16, weight = FontWeight.SemiBold)
            }
            T("You have ${all.size} request${if (all.size == 1) "" else "s"} in this ${if (rs.folder == null) "collection" else "folder"}.", size = 14)
            T("Env: ${state.envName(col.path) ?: "No Environment"}" + (state.globalEnvName()?.let { " · Global: $it" } ?: ""), color = p.muted, size = 12)
            Spacer(Modifier.height(8.dp))
            T("Timings", weight = FontWeight.SemiBold, size = 14)
            T("Delay between requests (ms)", size = 13)
            Field(rs.delay, { rs.delay = it.filter { c -> c.isDigit() }.take(6) }, Modifier.fillMaxWidth(), "e.g. 5", highlightVars = false)
            Spacer(Modifier.height(8.dp))
            T("Execution", weight = FontWeight.SemiBold, size = 14)
            T("Iterations", size = 13)
            Field(rs.iterations, { rs.iterations = it.filter { c -> c.isDigit() }.take(3) }, Modifier.width(160.dp), "1", highlightVars = false)
            LabeledCheck("Stop on first failure", rs.stopOnFail) { rs.stopOnFail = it }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (rs.running) Btn("Stop", { rs.cancelled = true }, danger = true, icon = Ic.Stop)
                else Btn(if (chosen.isEmpty()) "Run" else "Run $count Request${if (count == 1) "" else "s"}", icon = Ic.Play, onClick = { state.startRunner(tab) }, primary = true, enabled = chosen.isNotEmpty())
                Link("Reset", !rs.running) {
                    rs.resetOrder(all); rs.results.clear(); rs.delay = ""; rs.iterations = "1"; rs.stopOnFail = false
                }
            }
            if (showResults) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip("${rs.results.size} run", p.accent)
                    Chip("$passed passed", p.ok)
                    if (rs.results.size - passed > 0) Chip("${rs.results.size - passed} failed", p.err)
                    T("$total ms", color = p.muted, size = 12)
                }
                if (rs.running) T("Running: ${rs.current}…", color = p.muted, size = 12)
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(p.border))
        // ---- right: request selection, or results once a run has started ----
        Column(Modifier.weight(1.3f).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!showResults) {
                Row(Modifier.fillMaxWidth().background(p.field).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("${chosen.size} of ${rs.order.size} selected", color = p.muted, modifier = Modifier.weight(1f))
                    if (chosen.isEmpty()) Link("Select All") { all.forEach { rs.selected[it.path] = true } }
                    else Link("Deselect All") { all.forEach { rs.selected[it.path] = false } }
                    Link("Reset") { rs.resetOrder(all) }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(rs.order.size) { i ->
                        val n = rs.order[i]
                        Row(Modifier.fillMaxWidth().background(p.field).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Check(rs.selected[n.path] == true, { rs.selected[n.path] = it })
                            T(n.request.method, color = methodColor(n.request.method, p), size = 12, weight = FontWeight.Bold, modifier = Modifier.width(54.dp))
                            T(n.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconBtn(Ic.MoveUp, { if (i > 0) { val t = rs.order[i]; rs.order[i] = rs.order[i - 1]; rs.order[i - 1] = t } }, size = 14)
                            IconBtn(Ic.MoveDown, { if (i < rs.order.size - 1) { val t = rs.order[i]; rs.order[i] = rs.order[i + 1]; rs.order[i + 1] = t } }, size = 14)
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().background(p.field).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("Results", color = p.muted, modifier = Modifier.weight(1f))
                    Link("Back to selection", !rs.running) { rs.results.clear() }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(rs.results.size) { i ->
                        val it0 = rs.results[i]
                        val r = it0.result
                        val ok = r.error == null && !r.skipped && r.tests.all { t -> t.passed }
                        Column(Modifier.fillMaxWidth().clickable { rs.expanded = if (rs.expanded == i) null else i }.background(p.field).padding(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Ico(if (r.skipped) Ic.Skip else if (ok) Ic.CheckCircle else Ic.Cancel, tint = if (ok) p.ok else if (r.skipped) p.muted else p.err, size = 16.dp)
                                T(it0.node.request.method, color = methodColor(it0.node.request.method, p), size = 11, weight = FontWeight.Bold, modifier = Modifier.width(52.dp))
                                T(it0.node.name, modifier = Modifier.weight(1f), maxLines = 1)
                                r.response?.takeIf { it.error == null }?.let { T("${it.status}", color = statusColor(it.status, p), weight = FontWeight.SemiBold); T("${it.timeMs} ms", color = p.muted, size = 12) }
                                if (r.tests.isNotEmpty()) T("${r.tests.count { it.passed }}/${r.tests.size} tests", color = if (r.tests.all { it.passed }) p.ok else p.err, size = 12)
                            }
                            if (rs.expanded == i) {
                                r.error?.let { T(it, color = p.err, mono = true, size = 12) }
                                if (r.tests.isNotEmpty()) TestsList(r.tests)
                                if (r.logs.isNotEmpty()) T(r.logs.joinToString("\n"), mono = true, color = p.muted, size = 12)
                                r.response?.takeIf { it.error == null }?.let { T(it.text.take(1500), mono = true, size = 12, color = p.muted) }
                            }
                        }
                    }
                }
            }
        }
    }
}
