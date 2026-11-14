package dev.prateekthakur.openrest.features.sidebar

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.features.workflow.*
import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import java.nio.file.Path

internal sealed interface SRow {
    data class Col(val c: Collection) : SRow
    data class Folder(val col: Path, val f: FolderNode, val depth: Int, val siblings: List<Node>, val index: Int) : SRow
    data class Req(val col: Path, val r: RequestNode, val depth: Int, val siblings: List<Node>, val index: Int) : SRow
    data class Wf(val col: Path, val w: Workflow) : SRow
}

internal fun buildRows(state: AppState): List<SRow> {
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

internal fun countReqs(nodes: List<Node>): Int = nodes.sumOf { if (it is RequestNode) 1 else countReqs((it as FolderNode).children) }

internal enum class DropMode { INTO, BEFORE, AFTER }

internal class DragCtx(val onStart: () -> Unit, val onMove: (Float) -> Unit, val onEnd: () -> Unit, val mode: DropMode?, val dragging: Boolean)

internal val LocalTreeNav = staticCompositionLocalOf<(String) -> Unit> { {} }

internal fun rowKey(r: SRow) = when (r) { is SRow.Col -> "c:" + r.c.path; is SRow.Folder -> "f:" + r.f.path; is SRow.Req -> "r:" + r.r.path; is SRow.Wf -> "w:" + r.col + "|" + r.w.name }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TreeRow(
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
