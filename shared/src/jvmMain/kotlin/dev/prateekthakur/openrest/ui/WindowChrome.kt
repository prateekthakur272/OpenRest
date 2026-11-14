package dev.prateekthakur.openrest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What the app's own top bar has to do because the operating system's title bar is replaced by it.
 *  - macOS keeps its native traffic-light buttons floating over the bar: [leftInset] leaves room for them.
 *  - Windows / Linux draw nothing, so the app supplies its own [controls] (minimize / maximize / close),
 *    a [dragArea] to move the window, and [onDoubleClick] (maximize / restore) like a normal title bar.
 */
class WindowChrome(
    val leftInset: Dp = 0.dp,
    val controls: (@Composable () -> Unit)? = null,
    val dragArea: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
    val onDoubleClick: (() -> Unit)? = null,
    /** Draw the menu bar inside the title bar (Windows / Linux), instead of the operating system's. */
    val inlineMenus: Boolean = false,
    val onQuit: () -> Unit = {},
    val onMinimize: () -> Unit = {},
) {
    companion object { val None = WindowChrome() }
}

@Composable
private fun CaptionButton(icon: ImageVector, tag: String, danger: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Box(
        Modifier.testTag(tag).width(46.dp).fillMaxHeight().hoverable(src)
            .background(if (hov) (if (danger) Color(0xFFE81123) else p.hover) else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, tint = if (hov && danger) Color.White else p.text, size = 15.dp) }
}

/** Minimize / maximize (or restore) / close, for platforms where the app draws its own title area. */
@Composable
fun WindowControls(maximized: Boolean, onMinimize: () -> Unit, onToggleMaximize: () -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxHeight()) {
        CaptionButton(Icons.Rounded.Remove, "window-minimize", onClick = onMinimize)
        CaptionButton(if (maximized) Icons.Rounded.FilterNone else Icons.Rounded.CropSquare, "window-maximize", onClick = onToggleMaximize)
        CaptionButton(Icons.Rounded.Close, "window-close", danger = true, onClick = onClose)
    }
}

/** Double-clicking empty space in the bar maximizes / restores the window (when the platform asks for it). */
fun Modifier.titleBarDoubleClick(chrome: WindowChrome): Modifier =
    chrome.onDoubleClick?.let { f -> this.pointerInput(f) { detectTapGestures(onDoubleTap = { f() }) } } ?: this

data class Bounds(val x: Int, val y: Int, val w: Int, val h: Int)
enum class ResizeEdge { N, S, E, W, NE, NW, SE, SW }

/** New window bounds when an edge or corner is dragged by ([dx], [dy]) screen pixels; never smaller than the minimum, and the opposite edge stays put. */
fun resizeBounds(edge: ResizeEdge, start: Bounds, dx: Int, dy: Int, minW: Int, minH: Int): Bounds {
    var (x, y, w, h) = start
    val east = edge == ResizeEdge.E || edge == ResizeEdge.NE || edge == ResizeEdge.SE
    val west = edge == ResizeEdge.W || edge == ResizeEdge.NW || edge == ResizeEdge.SW
    val south = edge == ResizeEdge.S || edge == ResizeEdge.SE || edge == ResizeEdge.SW
    val north = edge == ResizeEdge.N || edge == ResizeEdge.NE || edge == ResizeEdge.NW
    if (east) w = maxOf(minW, start.w + dx)
    if (west) { w = maxOf(minW, start.w - dx); x = start.x + (start.w - w) }
    if (south) h = maxOf(minH, start.h + dy)
    if (north) { h = maxOf(minH, start.h - dy); y = start.y + (start.h - h) }
    return Bounds(x, y, w, h)
}

/** Invisible grab handles around an undecorated window so it can still be resized. */
@Composable
fun ResizeEdges(window: java.awt.Window, minWidth: Int = 760, minHeight: Int = 480, thickness: Dp = 5.dp) {
    @Composable
    fun Handle(edge: ResizeEdge, cursor: Int, modifier: Modifier) {
        var start by remember { mutableStateOf<Bounds?>(null) }
        var origin by remember { mutableStateOf(java.awt.Point()) }
        Box(
            modifier.pointerHoverIcon(PointerIcon(java.awt.Cursor.getPredefinedCursor(cursor))).pointerInput(edge) {
                detectDragGestures(
                    onDragStart = { start = window.bounds.let { Bounds(it.x, it.y, it.width, it.height) }; origin = java.awt.MouseInfo.getPointerInfo().location },
                    onDragEnd = { start = null }, onDragCancel = { start = null },
                ) { change, _ ->
                    change.consume()
                    val s = start ?: return@detectDragGestures
                    val now = java.awt.MouseInfo.getPointerInfo().location          // screen coordinates: stable while the window itself moves
                    val b = resizeBounds(edge, s, now.x - origin.x, now.y - origin.y, minWidth, minHeight)
                    window.setBounds(b.x, b.y, b.w, b.h)
                }
            },
        )
    }
    val t = thickness
    Box(Modifier.fillMaxSize()) {
        Handle(ResizeEdge.N, java.awt.Cursor.N_RESIZE_CURSOR, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(t))
        Handle(ResizeEdge.S, java.awt.Cursor.S_RESIZE_CURSOR, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(t))
        Handle(ResizeEdge.W, java.awt.Cursor.W_RESIZE_CURSOR, Modifier.align(Alignment.CenterStart).fillMaxHeight().width(t))
        Handle(ResizeEdge.E, java.awt.Cursor.E_RESIZE_CURSOR, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(t))
        Handle(ResizeEdge.NW, java.awt.Cursor.NW_RESIZE_CURSOR, Modifier.align(Alignment.TopStart).size(t * 2))
        Handle(ResizeEdge.NE, java.awt.Cursor.NE_RESIZE_CURSOR, Modifier.align(Alignment.TopEnd).size(t * 2))
        Handle(ResizeEdge.SW, java.awt.Cursor.SW_RESIZE_CURSOR, Modifier.align(Alignment.BottomStart).size(t * 2))
        Handle(ResizeEdge.SE, java.awt.Cursor.SE_RESIZE_CURSOR, Modifier.align(Alignment.BottomEnd).size(t * 2))
    }
}
