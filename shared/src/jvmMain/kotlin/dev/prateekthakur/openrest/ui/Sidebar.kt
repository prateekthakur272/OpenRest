package dev.prateekthakur.openrest.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.*
import dev.prateekthakur.openrest.core.Collection
import java.nio.file.Path

private sealed interface SRow {
    data class Col(val c: Collection) : SRow
    data class Folder(val col: Path, val f: FolderNode, val depth: Int, val siblings: List<Node>, val index: Int) : SRow
    data class Req(val col: Path, val r: RequestNode, val depth: Int, val siblings: List<Node>, val index: Int) : SRow
    data class Wf(val col: Path, val w: Workflow) : SRow
}

private fun buildRows(state: AppState): List<SRow> {
    val f = state.sidebarFilter.trim().lowercase()
    val out = mutableListOf<SRow>()
    fun matches(n: Node): Boolean = when (n) {
        is RequestNode -> n.name.lowercase().contains(f) || n.request.url.lowercase().contains(f)
        is FolderNode -> n.children.any { matches(it) }
    }
    fun walk(col: Path, nodes: List<Node>, depth: Int) {
        nodes.forEachIndexed { i, n ->
            if (f.isNotEmpty() && !matches(n)) return@forEachIndexed
            when (n) {
                is FolderNode -> {
                    out += SRow.Folder(col, n, depth, nodes, i)
                    if (f.isNotEmpty() || state.expanded[n.path.toString()] == true) walk(col, n.children, depth + 1)
                }
                is RequestNode -> out += SRow.Req(col, n, depth, nodes, i)
            }
        }
    }
    state.collections.forEach { c ->
        out += SRow.Col(c)
        if (f.isNotEmpty() || state.expanded[c.path.toString()] == true) {
            walk(c.path, c.children, 1)
            c.workflows.filter { f.isEmpty() || it.name.lowercase().contains(f) }.forEach { out += SRow.Wf(c.path, it) }
        }
    }
    return out
}

private fun menuIcon(l: String) = when {
    l.startsWith("New Request") || l.startsWith("New Collection") -> Ic.NewFile
    l.startsWith("New Folder") -> Ic.NewFolder
    l.startsWith("Open") -> Ic.Open
    l.startsWith("Import") -> Ic.Import
    l.startsWith("Export") -> Ic.Export
    l.startsWith("New Workflow") -> Ic.Workflow
    l.startsWith("Run") -> Ic.Play
    l.startsWith("Environments") -> Ic.Environments
    l.startsWith("Settings") -> Ic.Settings
    l.startsWith("Variables") -> Ic.Variables
    l.startsWith("Rename") -> Ic.Rename
    l.startsWith("Clone") -> Ic.Clone
    l.startsWith("Show in Folder") -> Ic.Reveal
    l.startsWith("Reload") -> Ic.Refresh
    l.startsWith("Close Collection") || l.startsWith("Close") -> Ic.Close
    l.startsWith("Delete") -> Ic.Delete
    l.startsWith("Move Up") -> Ic.MoveUp
    l.startsWith("Move Down") -> Ic.MoveDown
    l.startsWith("Send") -> Ic.Send
    l.startsWith("Save") -> Ic.Save
    l.startsWith("Copy") -> Ic.Copy
    l.startsWith("Generate") -> Ic.Code
    l.startsWith("Decode") || l.startsWith("Encode") -> Ic.Codec
    else -> Ic.Command
}

class MenuItem(val label: String?, val danger: Boolean = false, val action: () -> Unit = {})

@Composable
fun ContextMenu(open: Boolean, onDismiss: () -> Unit, items: List<MenuItem>) {
    val p = LocalPal.current
    DropdownMenu(expanded = open, onDismissRequest = onDismiss, containerColor = p.panel) {
        items.forEach { it0 ->
            if (it0.label == null) Box(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(1.dp).background(p.border))
            else DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Ico(menuIcon(it0.label), tint = if (it0.danger) p.err else p.muted, size = 16.dp)
                        T(it0.label, color = if (it0.danger) p.err else p.text)
                    }
                },
                onClick = { onDismiss(); it0.action() },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
            )
        }
    }
}

private fun countReqs(nodes: List<Node>): Int = nodes.sumOf { if (it is RequestNode) 1 else countReqs((it as FolderNode).children) }

private fun shortMethod(m: String) = when (m.uppercase()) { "DELETE" -> "DEL"; "OPTIONS" -> "OPT"; "PATCH" -> "PATCH"; else -> m.uppercase() }

/** Width of the method label in tree rows; workflow rows use the same slot for their icon so the titles line up. */
val METHOD_SLOT = 33.dp

@Composable
fun MethodPill(method: String, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Box(modifier.width(METHOD_SLOT), contentAlignment = Alignment.CenterStart) {
        T(shortMethod(method), color = methodColor(method, p), size = 10, weight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Visible)
    }
}

private enum class DropMode { INTO, BEFORE, AFTER }

private class DragCtx(val onStart: () -> Unit, val onMove: (Float) -> Unit, val onEnd: () -> Unit, val mode: DropMode?, val dragging: Boolean)

private val LocalTreeNav = staticCompositionLocalOf<(String) -> Unit> { {} }

private fun rowKey(r: SRow) = when (r) { is SRow.Col -> "c:" + r.c.path; is SRow.Folder -> "f:" + r.f.path; is SRow.Req -> "r:" + r.r.path; is SRow.Wf -> "w:" + r.col + "|" + r.w.name }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeRow(
    key: String,
    depth: Int,
    selected: Boolean,
    onClick: () -> Unit,
    items: List<MenuItem>,
    drag: DragCtx,
    cursor: Boolean,
    height: Int = 30,
    quickAdd: (() -> Unit)? = null,
    content: @Composable RowScope.(hovered: Boolean) -> Unit,
) {
    val p = LocalPal.current
    var menu by remember { mutableStateOf(false) }
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val nav = LocalTreeNav.current
    val dragRef by rememberUpdatedState(drag)
    val bg by androidx.compose.animation.animateColorAsState(
        if (selected) p.selected else if (hovered) p.hover else Color.Transparent, androidx.compose.animation.core.tween(110),
    )
    Box {
        Row(
            Modifier.fillMaxWidth().height(height.dp)
                .alpha(if (drag.dragging) 0.4f else 1f)
                .background(bg)
                .drawBehind {
                    for (i in 0 until depth) {
                        val x = (15 + i * 14).dp.toPx()
                        drawLine(p.border.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1f)
                    }
                    if (selected) {
                        // sit on the indentation guide of the parent level
                        val gx = if (depth > 0) (15 + (depth - 1) * 14).dp.toPx() else 1.25.dp.toPx()
                        drawRect(p.accent, androidx.compose.ui.geometry.Offset(gx - 1.25.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(2.5.dp.toPx(), size.height))
                    }
                    if (cursor && !selected) drawRect(p.accent.copy(alpha = 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    when (drag.mode) {
                        DropMode.INTO -> { drawRect(p.accent.copy(alpha = 0.18f)); drawRect(p.accent, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())) }
                        DropMode.BEFORE -> drawRect(p.accent, androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx()))
                        DropMode.AFTER -> drawRect(p.accent, androidx.compose.ui.geometry.Offset(0f, size.height - 2.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx()))
                        null -> {}
                    }
                }
                .hoverable(src)
                .pointerInput(Unit) {
                    detectDragGestures(onDragStart = { dragRef.onStart() }, onDragEnd = { dragRef.onEnd() }, onDragCancel = { dragRef.onEnd() }) { c, d -> c.consume(); dragRef.onMove(d.y) }
                }
                .onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary)) { nav(key); menu = true }
                .clickable { nav(key); onClick() }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(start = (8 + depth * 14).dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content(hovered || menu)
            if ((hovered || menu) && quickAdd != null) IconBtn(Ic.Add, quickAdd, size = 15)
            if (hovered || menu) IconBtn(Ic.More, { menu = true }, size = 15)
        }
        Box(Modifier.align(Alignment.TopEnd)) { ContextMenu(menu, { menu = false }, items) }
    }
}

@Composable
fun InlineRename(initial: String, modifier: Modifier = Modifier, onDone: (String?) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    var gotFocus by remember { mutableStateOf(false) }
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    Field(
        v, { v = it },
        modifier.onPreviewKeyEvent { e -> if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key == androidx.compose.ui.input.key.Key.Escape) { onDone(null); true } else false }
            .onFocusChanged { if (it.isFocused) gotFocus = true else if (gotFocus) onDone(null) },
        onEnter = { onDone(v.trim().takeIf { it.isNotEmpty() }) }, focusRequester = fr, highlightVars = false,
    )
}

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

@Composable
private fun CollectionRow(state: AppState, c: Collection, drag: DragCtx, cursor: Boolean) {
    val p = LocalPal.current
    val key = c.path.toString()
    val open = state.expanded[key] == true
    val env = state.envName(c.path)
    TreeRow(
        "c:" + c.path, 0, false,
        { state.expanded[key] = !open; state.selectedCollection = c.path },
        listOf(
            MenuItem("New Request") { state.modal = Modal.NewRequest(c.path, c.path) },
            MenuItem("New Folder") { state.promptNewFolder(c.path, c.path) },
            MenuItem("New Workflow") { state.promptNewWorkflow(c.path) },
            MenuItem(null),
            MenuItem("Run Collection") { state.openRunner(c.path, null) },
            MenuItem("Environments") { state.modal = Modal.Envs(c.path) },
            MenuItem("Settings") { state.modal = Modal.NodeSettings(c.path, c.path) },
            MenuItem("Variables") { state.modal = Modal.Vars(c.path) },
            MenuItem(null),
            MenuItem("Rename") { state.promptRename(c.path, null) },
            MenuItem("Export…") { state.modal = Modal.Export(c.path) },
            MenuItem("Show in Folder") { Platform.reveal(c.path) },
            MenuItem("Reload") { state.reload(c.path) },
            MenuItem("Close Collection", danger = true) { state.closeCollection(c.path) },
        ),
        drag = drag, cursor = cursor, height = 34, quickAdd = { state.modal = Modal.NewRequest(c.path, c.path) },
    ) { hovered ->
        Chevron(open)
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(p.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Text(
                c.name.firstOrNull()?.uppercase() ?: "?", color = p.accent, fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 11.sp, lineHeight = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both),
                ),
            )
        }
        T(c.name, weight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (!hovered) {
            if (env != null) Box(Modifier.clip(RoundedCornerShape(4.dp)).background(p.ok.copy(alpha = 0.15f)).padding(horizontal = 5.dp, vertical = 1.dp)) { T(env, color = p.ok, size = 10, maxLines = 1) }
            T(countReqs(c.children).toString(), color = p.muted, size = 11)
        }
    }
}

@Composable
private fun FolderRow(state: AppState, row: SRow.Folder, drag: DragCtx, cursor: Boolean) {
    val p = LocalPal.current
    val f = row.f
    val key = f.path.toString()
    val open = state.expanded[key] == true || state.sidebarFilter.isNotBlank()
    TreeRow(
        "f:" + f.path, row.depth, false, { state.expanded[key] = !(state.expanded[key] == true) },
        listOf(
            MenuItem("New Request") { state.modal = Modal.NewRequest(row.col, f.path) },
            MenuItem("New Folder") { state.promptNewFolder(row.col, f.path) },
            MenuItem(null),
            MenuItem("Run Folder") { state.openRunner(row.col, f.path) },
            MenuItem("Settings") { state.modal = Modal.NodeSettings(row.col, f.path) },
            MenuItem(null),
            MenuItem("Rename") { state.promptRename(row.col, f) },
            MenuItem("Clone") { state.promptClone(row.col, f) },
            MenuItem("Show in Folder") { Platform.reveal(f.path) },
            MenuItem("Delete", danger = true) { state.promptDelete(row.col, f) },
        ),
        drag = drag, cursor = cursor, quickAdd = { state.modal = Modal.NewRequest(row.col, f.path) },
    ) { hovered ->
        Chevron(open)
        FolderIcon(open)
        if (state.renaming == f.path) InlineRename(f.name, Modifier.weight(1f)) { n -> state.renaming = null; if (n != null && n != f.name) state.renameNode(row.col, f, n) }
        else T(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (!hovered) T(countReqs(f.children).toString(), color = p.muted, size = 11)
    }
}

@Composable
private fun WorkflowRow(state: AppState, row: SRow.Wf, cursor: Boolean) {
    val p = LocalPal.current
    val tab = state.workflowTab(row.col, row.w.name)
    TreeRow(
        "w:" + row.col + "|" + row.w.name, 1, state.activeTab?.let { it === tab } == true, { state.openWorkflow(row.col, row.w.name) },
        listOf(
            MenuItem("Open") { state.openWorkflow(row.col, row.w.name) },
            MenuItem("Run") { state.openWorkflow(row.col, row.w.name); state.workflowTab(row.col, row.w.name)?.let { state.runWorkflow(it) } },
            MenuItem(null),
            MenuItem("Rename") { state.promptRenameWorkflow(row.col, row.w.name) },
            MenuItem("Clone") { state.cloneWorkflow(row.col, row.w.name) },
            MenuItem("Delete", danger = true) { state.promptDeleteWorkflow(row.col, row.w.name) },
        ),
        drag = DragCtx({}, {}, {}, null, false), cursor = cursor,
    ) { _ ->
        Spacer(Modifier.width(6.dp))
        Box(Modifier.width(METHOD_SLOT), contentAlignment = Alignment.CenterStart) { Ico(Ic.Workflow, tint = p.accent, size = 15.dp) }
        T(row.w.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (tab?.workflow?.running == true) androidx.compose.material3.CircularProgressIndicator(Modifier.size(11.dp), color = p.accent, strokeWidth = 1.5.dp)
        else if (tab?.dirty == true) Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(p.accent))
    }
}

@Composable
private fun RequestRow(state: AppState, row: SRow.Req, drag: DragCtx, cursor: Boolean) {
    val p = LocalPal.current
    val r = row.r
    val reqs = row.siblings.filterIsInstance<RequestNode>()
    val ri = reqs.indexOf(r)
    val tab = state.tabs.firstOrNull { it.filePath == r.path }
    TreeRow(
        "r:" + r.path, row.depth, state.activeTab?.filePath == r.path, { state.openRequest(row.col, r) },
        listOf(
            MenuItem("Open") { state.openRequest(row.col, r) },
            MenuItem("Rename") { state.promptRename(row.col, r) },
            MenuItem("Clone") { state.promptClone(row.col, r) },
            MenuItem("Move Up") { state.move(row.col, row.siblings, row.index, -1) }.takeIf { ri > 0 } ?: MenuItem(null),
            MenuItem("Move Down") { state.move(row.col, row.siblings, row.index, 1) }.takeIf { ri in 0 until reqs.size - 1 } ?: MenuItem(null),
            MenuItem("Show in Folder") { Platform.reveal(r.path) },
            MenuItem("Delete", danger = true) { state.promptDelete(row.col, r) },
        ).let { l -> l.filterIndexed { i, m -> !(m.label == null && (i == 0 || l[i - 1].label == null)) } },
        drag = drag, cursor = cursor,
    ) { hovered ->
        Spacer(Modifier.width(6.dp))
        MethodPill(r.request.method)
        if (state.renaming == r.path) InlineRename(r.name, Modifier.weight(1f)) { n -> state.renaming = null; if (n != null && n != r.name) state.renameNode(row.col, r, n) }
        else T(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (!hovered && tab != null) {
            when {
                tab.sending -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(11.dp), color = p.accent, strokeWidth = 1.5.dp)
                tab.dirty -> Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(p.accent))
                else -> tab.result?.response?.takeIf { it.error == null }?.let { Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(statusColor(it.status, p))) }
            }
            Spacer(Modifier.width(2.dp))
        }
    }
}

private fun ago(t: Long): String {
    val s = (System.currentTimeMillis() - t) / 1000
    return when { s < 5 -> "now"; s < 60 -> "${s}s"; s < 3600 -> "${s / 60}m"; s < 86400 -> "${s / 3600}h"; else -> "${s / 86400}d" }
}

@Composable
private fun ColumnScope.HistoryView(state: AppState) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel("RECENT REQUESTS", Modifier.weight(1f))
        if (state.history.isNotEmpty()) IconBtn(Ic.Delete, { state.history.clear() }, size = 16)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
    if (state.history.isEmpty()) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(20.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Ico(Ic.History, tint = p.muted.copy(alpha = 0.5f), size = 44.dp)
            Spacer(Modifier.height(10.dp))
            T("Nothing sent yet", weight = FontWeight.SemiBold)
            T("Requests you send show up here with their full response.", color = p.muted, size = 12, modifier = Modifier.padding(top = 6.dp))
        }
        return
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
        items(state.history.toList()) { e ->
            val src = remember { MutableInteractionSource() }
            val hov by src.collectIsHoveredAsState()
            Column(
                Modifier.fillMaxWidth().background(if (hov) p.hover else Color.Transparent).hoverable(src).clickable { state.openHistory(e) }
                    .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MethodPill(e.method)
                    T(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(4.dp)).background((if (e.status == 0) p.err else statusColor(e.status, p)).copy(alpha = 0.16f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                        T(if (e.status == 0) "ERR" else e.status.toString(), color = if (e.status == 0) p.err else statusColor(e.status, p), size = 10, weight = FontWeight.Bold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(e.url, color = p.muted, size = 11, mono = true, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    T("${e.timeMs}ms · ${ago(e.time)}", color = p.muted, size = 10)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.border.copy(alpha = 0.5f)))
        }
    }
}
