package dev.prateekthakur.openrest.components

import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor

/** Height shared by buttons, dropdowns and single-line text fields. */
val CONTROL_H = 32.dp

@Composable
fun Tabs(
    items: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    badge: (String) -> String? = { null },
) {
    val p = LocalPal.current
    Row(modifier.horizontalScroll(rememberScrollState()).drawBottomBorder(p.border), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { t ->
            val sel = t == selected
            Column(
                Modifier.clickable { onSelect(t) }.pointerHoverIcon(PointerIcon.Hand)
                    // the underline is painted by the tab itself (its last 2dp), so nothing can cover it
                    .drawBehind { if (sel) drawRect(p.accent, androidx.compose.ui.geometry.Offset(0f, size.height - 2.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx())) }
                    .padding(horizontal = 10.dp).padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    T(t, color = if (sel) p.text else p.muted, weight = if (sel) FontWeight.SemiBold else null)
                    badge(t)?.let { b -> T(b, color = p.accent, size = 11, weight = FontWeight.SemiBold) }
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.height(2.dp).fillMaxWidth())
            }
        }
    }
}

fun Modifier.drawBottomBorder(c: Color): Modifier = this.drawBehind {
    drawLine(c, androidx.compose.ui.geometry.Offset(0f, size.height - 0.5f), androidx.compose.ui.geometry.Offset(size.width, size.height - 0.5f), 1f)
}

@Composable
fun ModalBox(title: String, onClose: () -> Unit, width: Dp = 560.dp, height: Dp? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickableNoRipple { onClose() }
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onClose(); true } else false },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(width).then(if (height != null) Modifier.height(height) else Modifier.heightIn(max = 760.dp))
                .clip(RoundedCornerShape(10.dp)).background(p.panel).border(1.dp, p.border, RoundedCornerShape(10.dp))
                .clickableNoRipple(),
        ) {
            Row(Modifier.fillMaxWidth().drawBottomBorder(p.border).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                T(title, size = 15, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconBtn(Ic.Close, onClose)
            }
            Column(Modifier.weight(1f, fill = height != null).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

@Composable
fun SplitPane(
    vertical: Boolean,
    fraction: Float,
    onFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    val p = LocalPal.current
    val density = LocalDensity.current
    var total by remember { mutableStateOf(1f) }
    val frac by rememberUpdatedState(fraction)
    val cb by rememberUpdatedState(onFraction)
    Box(modifier.fillMaxSize().onSizeChanged { total = (if (vertical) it.height else it.width).toFloat().coerceAtLeast(1f) }) {
        val firstDp = with(density) { (total * fraction).toDp() }
        val handle = 5.dp
        if (vertical) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(firstDp)) { first() }
                Box(
                    Modifier.fillMaxWidth().height(handle).background(p.border).pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)))
                        .pointerInput(vertical) { detectDragGestures { c, d -> c.consume(); cb((frac + d.y / total).coerceIn(0.12f, 0.88f)) } },
                )
                Box(Modifier.fillMaxWidth().weight(1f)) { second() }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxHeight().width(firstDp)) { first() }
                Box(
                    Modifier.fillMaxHeight().width(handle).background(p.border).pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
                        .pointerInput(vertical) { detectDragGestures { c, d -> c.consume(); cb((frac + d.x / total).coerceIn(0.12f, 0.88f)) } },
                )
                Box(Modifier.fillMaxHeight().weight(1f)) { second() }
            }
        }
    }
}

@Composable
fun ScrollBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), content = content)
}

/** Animated disclosure chevron: points right when collapsed, rotates to point down when open. */
@Composable
fun Chevron(open: Boolean, modifier: Modifier = Modifier, color: Color = LocalPal.current.muted) {
    val angle by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, androidx.compose.animation.core.tween(140))
    androidx.compose.foundation.Canvas(modifier.size(14.dp).rotate(angle)) {
        val w = size.width; val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.36f, h * 0.22f); lineTo(w * 0.66f, h * 0.5f); lineTo(w * 0.36f, h * 0.78f)
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
fun FolderIcon(open: Boolean, modifier: Modifier = Modifier, color: Color = LocalPal.current.accent) =
    Ico(if (open) Ic.FolderOpen else Ic.Folder, modifier, color, 18.dp)
