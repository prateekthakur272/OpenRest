package dev.prateekthakur.openrest.features.sidebar

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.features.workflow.*
import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.nio.file.Path

@Composable
private fun SegTab(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, badge: String?, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(if (selected) p.panel else Color.Transparent)
            .then(if (selected) Modifier.border(1.dp, p.border, RoundedCornerShape(6.dp)) else Modifier)
            .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, tint = if (selected) p.accent else p.muted, size = 15.dp)
        Spacer(Modifier.width(6.dp))
        T(label, color = if (selected) p.text else p.muted, size = 12, weight = if (selected) FontWeight.SemiBold else null)
        if (badge != null) { Spacer(Modifier.width(5.dp)); Box(Modifier.clip(RoundedCornerShape(8.dp)).background(p.accent.copy(alpha = 0.2f)).padding(horizontal = 5.dp)) { T(badge, color = p.accent, size = 10, weight = FontWeight.Bold) } }
    }
}

@Composable
fun Sidebar(state: AppState, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    val rows = buildRows(state)
    val rowsRef by rememberUpdatedState(rows)
    var dragKey by remember { mutableStateOf<String?>(null) }
    var drop by remember { mutableStateOf<Pair<Int, DropMode>?>(null) }
    var startMid by remember { mutableStateOf(0f) }
    var acc by remember { mutableStateOf(0f) }

    fun performDrop() {
        val list = rowsRef
        val src = list.firstOrNull { rowKey(it) == dragKey }
        val d = drop
        val tgt = d?.let { list.getOrNull(it.first) }
        if (src == null || tgt == null || src is SRow.Col || src is SRow.Wf || tgt is SRow.Wf) return
        val srcCol: Path; val srcNode: Node
        when (src) { is SRow.Req -> { srcCol = src.col; srcNode = src.r }; is SRow.Folder -> { srcCol = src.col; srcNode = src.f }; else -> return }
        val tgtCol = when (tgt) { is SRow.Col -> tgt.c.path; is SRow.Folder -> tgt.col; is SRow.Req -> tgt.col; is SRow.Wf -> return }
        if (tgtCol != srcCol) { state.showToast("Can't move between collections"); return }
        when (tgt) {
            is SRow.Wf -> {}
            is SRow.Col -> state.moveNode(srcCol, srcNode.path, tgt.c.path)
            is SRow.Folder -> if (!tgt.f.path.startsWith(srcNode.path)) state.moveNode(srcCol, srcNode.path, tgt.f.path)
            is SRow.Req -> if (tgt.r.path != srcNode.path) {
                if (srcNode is RequestNode) state.dropRequest(srcCol, srcNode, tgt.r, d.second == DropMode.AFTER)
                else if (!tgt.r.path.parent.startsWith(srcNode.path)) state.moveNode(srcCol, srcNode.path, tgt.r.path.parent)
            }
        }
    }

    fun dragCtx(index: Int, row: SRow): DragCtx {
        val key = rowKey(row)
        return DragCtx(
            onStart = {
                if (row !is SRow.Col) listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { dragKey = key; startMid = it.offset + it.size / 2f; acc = 0f }
            },
            onMove = { dy ->
                if (dragKey != null) {
                    acc += dy
                    val y = startMid + acc
                    val hit = listState.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }
                    drop = hit?.let { h ->
                        val idx = rowsRef.indexOfFirst { rowKey(it) == h.key }
                        if (idx < 0) null else idx to (if (rowsRef[idx] is SRow.Req) (if ((y - h.offset) / h.size < 0.5f) DropMode.BEFORE else DropMode.AFTER) else DropMode.INTO)
                    }
                }
            },
            onEnd = { if (dragKey != null) performDrop(); dragKey = null; drop = null },
            mode = if (dragKey != null) drop?.takeIf { it.first == index }?.second else null,
            dragging = dragKey == key,
        )
    }

    fun toggle(r: SRow, open: Boolean) {
        val k = when (r) { is SRow.Col -> r.c.path.toString(); is SRow.Folder -> r.f.path.toString(); else -> return }
        state.expanded[k] = open
    }

    LaunchedEffect(state.cursor) {
        val i = rows.indexOfFirst { rowKey(it) == state.cursor }
        val vis = listState.layoutInfo.visibleItemsInfo
        if (i >= 0 && vis.isNotEmpty() && (i < vis.first().index || i > vis.last().index)) listState.scrollToItem((i - 2).coerceAtLeast(0))
    }

    CompositionLocalProvider(LocalTreeNav provides { k -> state.cursor = k; state.cursorVisible = false; runCatching { fr.requestFocus() } }) {
    Column(
        modifier.background(p.panel).focusRequester(fr).focusable().onKeyEvent { e ->
            if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown || state.sidebarTab != "Collections" || state.renaming != null) return@onKeyEvent false
            val keys = rows.map { rowKey(it) }
            if (e.key in listOf(Key.DirectionDown, Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight, Key.Enter, Key.F2)) state.cursorVisible = true
            val i = keys.indexOf(state.cursor)
            val cur = rows.getOrNull(i)
            when (e.key) {
                androidx.compose.ui.input.key.Key.DirectionDown -> { state.cursor = keys.getOrNull(i + 1) ?: keys.firstOrNull(); true }
                androidx.compose.ui.input.key.Key.DirectionUp -> { state.cursor = keys.getOrNull(i - 1) ?: keys.lastOrNull(); true }
                androidx.compose.ui.input.key.Key.DirectionRight -> {
                    if (cur != null && cur !is SRow.Req && cur !is SRow.Wf) { val open = state.expanded[(if (cur is SRow.Col) cur.c.path else (cur as SRow.Folder).f.path).toString()] == true; if (!open) toggle(cur, true) else state.cursor = keys.getOrNull(i + 1) }
                    true
                }
                androidx.compose.ui.input.key.Key.DirectionLeft -> {
                    if (cur != null && cur !is SRow.Req && cur !is SRow.Wf && state.expanded[(if (cur is SRow.Col) cur.c.path else (cur as SRow.Folder).f.path).toString()] == true) toggle(cur, false)
                    else if (cur != null) {
                        val depth = when (cur) { is SRow.Col -> 0; is SRow.Folder -> cur.depth; is SRow.Req -> cur.depth; is SRow.Wf -> 1 }
                        val parent = (i - 1 downTo 0).firstOrNull { j -> rows[j].let { r -> (if (r is SRow.Col) 0 else if (r is SRow.Folder) r.depth else Int.MAX_VALUE) < depth } }
                        if (parent != null) state.cursor = keys[parent]
                    }
                    true
                }
                androidx.compose.ui.input.key.Key.Enter -> {
                    when (cur) {
                        is SRow.Req -> state.openRequest(cur.col, cur.r)
                        is SRow.Wf -> state.openWorkflow(cur.col, cur.w.name)
                        is SRow.Folder -> state.expanded[cur.f.path.toString()] = state.expanded[cur.f.path.toString()] != true
                        is SRow.Col -> state.expanded[cur.c.path.toString()] = state.expanded[cur.c.path.toString()] != true
                        null -> {}
                    }
                    cur != null
                }
                androidx.compose.ui.input.key.Key.F2 -> {
                    when (cur) { is SRow.Req -> state.renaming = cur.r.path; is SRow.Folder -> state.renaming = cur.f.path; is SRow.Col -> state.promptRename(cur.c.path, null); is SRow.Wf -> state.promptRenameWorkflow(cur.col, cur.w.name); null -> {} }
                    cur != null
                }
                androidx.compose.ui.input.key.Key.Delete, androidx.compose.ui.input.key.Key.Backspace -> {
                    when (cur) { is SRow.Req -> state.promptDelete(cur.col, cur.r); is SRow.Folder -> state.promptDelete(cur.col, cur.f); is SRow.Wf -> state.promptDeleteWorkflow(cur.col, cur.w.name); else -> {} }
                    cur != null && cur !is SRow.Col
                }
                else -> false
            }
        },
    ) {
        // segmented switch
        Row(Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(8.dp)).background(p.field).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            SegTab("Collections", Ic.Folder, state.sidebarTab == "Collections", null, Modifier.weight(1f)) { state.sidebarTab = "Collections" }
            SegTab("History", Ic.History, state.sidebarTab == "History", state.history.size.takeIf { it > 0 }?.toString(), Modifier.weight(1f)) { state.sidebarTab = "History" }
        }
        if (state.sidebarTab == "History") { HistoryView(state); return@Column }

        // search + tools
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(6.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(6.dp)).padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(Ic.Search, tint = p.muted, size = 15.dp)
                Field(state.sidebarFilter, { state.sidebarFilter = it }, Modifier.weight(1f), "Filter requests…", bordered = false, highlightVars = false)
                if (state.sidebarFilter.isNotEmpty()) IconBtn(Ic.Close, { state.sidebarFilter = "" }, size = 13)
            }
            IconBtn(Ic.CollapseAll, { state.collapseAll() }, size = 17)
            IconBtn(Ic.Refresh, { state.reloadAll(); state.showToast("Collections reloaded") }, size = 17)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))

        if (state.collections.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(20.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Ico(Ic.Folder, tint = p.muted.copy(alpha = 0.5f), size = 44.dp)
                Spacer(Modifier.height(10.dp))
                T("No collections yet", weight = FontWeight.SemiBold)
                T("Create one, open a folder, or import from Postman, Bruno or OpenAPI.", color = p.muted, size = 12, modifier = Modifier.padding(vertical = 6.dp))
                Btn("New Collection", { state.modal = Modal.NewCollection }, Modifier.fillMaxWidth(), primary = true, icon = Ic.NewFolder)
                Spacer(Modifier.height(6.dp))
                Btn("Open Collection", { state.modal = Modal.OpenCollection }, Modifier.fillMaxWidth(), icon = Ic.FolderOpen)
                Spacer(Modifier.height(6.dp))
                Btn("Import", { state.modal = Modal.Import }, Modifier.fillMaxWidth(), icon = Ic.Import)
            }
        } else {
            Box(Modifier.weight(1f)) {
                if (rows.isEmpty() || (state.sidebarFilter.isNotBlank() && rows.none { it !is SRow.Col })) {
                    T("No requests match “${state.sidebarFilter}”", color = p.muted, size = 12, modifier = Modifier.padding(16.dp))
                }
                LazyColumn(Modifier.fillMaxSize().padding(vertical = 4.dp), state = listState) {
                    itemsIndexed(rows, key = { _, r -> rowKey(r) }) { index, row ->
                        Box(Modifier.animateItem()) {
                            val cursor = state.cursorVisible && state.cursor == rowKey(row)
                            when (row) {
                                is SRow.Col -> CollectionRow(state, row.c, dragCtx(index, row), cursor)
                                is SRow.Folder -> FolderRow(state, row, dragCtx(index, row), cursor)
                                is SRow.Req -> RequestRow(state, row, dragCtx(index, row), cursor)
                                is SRow.Wf -> WorkflowRow(state, row, cursor)
                            }
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                if (dragKey != null) Box(Modifier.align(Alignment.BottomCenter).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(p.panel).border(1.dp, p.border, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    T("Drop on a folder to move · between requests to reorder", color = p.muted, size = 11)
                }
            }
        }

        // footer quick actions
        Row(Modifier.fillMaxWidth().drawBottomBorderTop(p.border).padding(horizontal = 6.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            FooterBtn("New", Ic.NewFolder, Modifier.weight(1f)) { state.modal = Modal.NewCollection }
            FooterBtn("Open", Ic.FolderOpen, Modifier.weight(1f)) { state.modal = Modal.OpenCollection }
            FooterBtn("Import", Ic.Import, Modifier.weight(1f)) { state.modal = Modal.Import }
        }
    }
    }
}

private fun Modifier.drawBottomBorderTop(c: Color): Modifier = this.drawBehind {
    drawLine(c, androidx.compose.ui.geometry.Offset(0f, 0.5f), androidx.compose.ui.geometry.Offset(size.width, 0.5f), 1f)
}

@Composable
private fun FooterBtn(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(if (hov) p.hover else Color.Transparent).hoverable(src).clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand).padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, tint = if (hov) p.accent else p.muted, size = 15.dp); Spacer(Modifier.width(5.dp)); T(label, color = if (hov) p.text else p.muted, size = 12)
    }
}
