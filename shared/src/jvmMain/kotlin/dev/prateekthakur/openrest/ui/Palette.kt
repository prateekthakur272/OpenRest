package dev.prateekthakur.openrest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.*

private class Cmd(val title: String, val sub: String = "", val badge: String? = null, val badgeColor: Color? = null, val group: String, val run: () -> Unit)

private fun fuzzy(q: String, t: String): Int {
    if (q.isEmpty()) return 0
    val ql = q.lowercase(); val tl = t.lowercase()
    var qi = 0; var score = 0; var last = -2
    for (i in tl.indices) {
        if (qi < ql.length && tl[i] == ql[qi]) {
            score += if (i == last + 1) 6 else 1
            if (i == 0 || tl[i - 1] in " /-_.:") score += 4
            last = i; qi++
        }
    }
    return if (qi == ql.length) score - t.length / 25 else -1
}

@Composable
fun Palette(state: AppState) {
    val p = LocalPal.current
    var q by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(0) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    fun close() { state.modal = null }
    fun go(f: () -> Unit): () -> Unit = { close(); f() }

    val all = mutableListOf<Cmd>()
    state.collections.forEach { c ->
        c.allRequests().forEach { r ->
            val folder = c.ancestorFolders(r.path).joinToString(" / ") { it.name }
            all += Cmd(r.name, listOf(c.name, folder).filter { it.isNotEmpty() }.joinToString(" / ") + "  ·  " + r.request.url.take(60), r.request.method, methodColor(r.request.method, p), "Requests", go { state.openRequest(c.path, r) })
        }
    }
    val tab = state.activeTab?.takeIf { it.isRequest }
    val col = state.currentCollection
    fun act(t: String, sub: String = "", f: () -> Unit) { all += Cmd(t, sub, "›", p.muted, "Commands", go(f)) }
    if (tab != null) {
        act("Send request", "⌘↵") { state.send(tab) }
        act("Save request", "⌘S") { state.save(tab) }
        act("Generate code…", "") { state.modal = Modal.Codegen(tab.id) }
        act("Copy as cURL", "⌘⇧C") { state.copyCurl(tab) }
        act("Close tab", "⌘W") { state.closeTab(tab.id) }
        act("Focus URL bar", "⌘L") { runCatching { state.urlFocus.requestFocus() } }
    }
    act("Save all", "⌘⇧S") { state.saveAll() }
    act("Environments: Global") { state.modal = Modal.Envs(null) }
    act("Switch global environment: None") { state.setGlobalEnv(null) }
    state.globalEnvs.forEach { e -> act("Switch global environment: ${e.name}") { state.setGlobalEnv(e.name) } }
    if (col != null) {
        act("New request", "⌘N") { state.modal = Modal.NewRequest(col.path, col.path) }
        act("New folder") { state.promptNewFolder(col.path, col.path) }
        act("Run collection: ${col.name}") { state.openRunner(col.path, null) }
        act("New workflow") { state.promptNewWorkflow(col.path) }
        col.workflows.forEach { w -> act("Open workflow: ${w.name}", col.name) { state.openWorkflow(col.path, w.name) } }
        act("Environments: ${col.name}") { state.modal = Modal.Envs(col.path) }
        act("Variables inspector") { state.modal = Modal.Vars(col.path) }
        act("Collection settings") { state.modal = Modal.NodeSettings(col.path, col.path) }
        act("Export collection…") { state.modal = Modal.Export(col.path) }
        act("Switch environment: None") { state.setEnv(col.path, null) }
        col.environments.forEach { e -> act("Switch environment: ${e.name}") { state.setEnv(col.path, e.name) } }
    }
    act("New collection…") { state.modal = Modal.NewCollection }
    act("Open collection…") { state.modal = Modal.OpenCollection }
    act("Import…") { state.modal = Modal.Import }
    act("Cookies") { state.modal = Modal.Cookies }
    act("Preferences", "⌘,") { state.modal = Modal.Settings }
    ThemeMode.entries.filter { it != state.settings.theme }.forEach { m -> act("Theme: ${m.label}") { state.updateSettings(state.settings.copy(theme = m)) } }
    act("Toggle layout (stacked / side by side)", "⌘⇧L") { state.toggleLayout() }
    act("Zoom in", "⌘+") { state.zoom(1) }
    act("Zoom out", "⌘−") { state.zoom(-1) }
    act("Reset zoom", "⌘0") { state.zoom(0) }
    act("Toggle sidebar", "⌘B") { state.sidebarVisible = !state.sidebarVisible }
    act("Show request history") { state.sidebarVisible = true; state.sidebarTab = "History" }
    act("Collapse all folders") { state.collapseAll() }
    act("Expand all folders") { state.expandAll() }
    act("Reload collections from disk") { state.reloadAll() }

    val results = if (q.isBlank()) all.filter { it.group == "Commands" }.take(0) + all.take(40) else
        all.map { it to fuzzy(q.trim(), it.title + " " + it.sub) }.filter { it.second >= 0 }.sortedByDescending { it.second + if (it.first.group == "Requests") 2 else 0 }.map { it.first }.take(60)
    val index = sel.coerceIn(0, maxOf(0, results.size - 1))
    val list = rememberLazyListState()
    LaunchedEffect(index) { if (results.isNotEmpty()) list.animateScrollToItem((index - 3).coerceAtLeast(0)) }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.padding(top = 80.dp).width(680.dp).clip(RoundedCornerShape(12.dp)).background(p.panel).border(1.dp, p.border, RoundedCornerShape(12.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> { sel = (index + 1).coerceAtMost(results.size - 1); true }
                        Key.DirectionUp -> { sel = (index - 1).coerceAtLeast(0); true }
                        Key.Enter -> { results.getOrNull(index)?.run?.invoke(); true }
                        Key.Escape -> { close(); true }
                        else -> false
                    }
                },
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T("⌘K", color = p.accent, mono = true, weight = FontWeight.Bold)
                Field(q, { q = it; sel = 0 }, Modifier.weight(1f), "Search requests, run commands, switch environments…", bordered = false, focusRequester = fr, highlightVars = false)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
            if (results.isEmpty()) T("No matches", color = p.muted, modifier = Modifier.padding(16.dp))
            else LazyColumn(Modifier.heightIn(max = 420.dp), state = list) {
                itemsIndexed(results) { i, c ->
                    val prevGroup = results.getOrNull(i - 1)?.group
                    if (c.group != prevGroup) SectionLabel(c.group.uppercase(), Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp))
                    Row(
                        Modifier.fillMaxWidth().background(if (i == index) p.selected else Color.Transparent).clickable { c.run() }.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        c.badge?.let { T(it, color = c.badgeColor ?: p.muted, size = 11, weight = FontWeight.Bold, modifier = Modifier.width(44.dp)) }
                        T(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (c.sub.isNotEmpty()) T(c.sub, color = p.muted, size = 12, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                T("↑↓ navigate", color = p.muted, size = 11); T("↵ run", color = p.muted, size = 11); T("esc close", color = p.muted, size = 11)
                Spacer(Modifier.weight(1f)); T("${results.size} results", color = p.muted, size = 11)
            }
        }
    }
}
