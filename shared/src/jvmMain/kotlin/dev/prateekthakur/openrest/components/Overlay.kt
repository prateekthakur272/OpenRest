package dev.prateekthakur.openrest.components

import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Root-level host for transient menus (autocomplete). Lives in the same composition, never blocks input outside the menu. */
class MenuOverlay {
    var owner: Any? by mutableStateOf(null)
    var x by mutableStateOf(0f)
    var yTop by mutableStateOf(0f)
    var yBottom by mutableStateOf(0f)
    var estHeight by mutableStateOf(0f)
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
    fun show(token: Any, x: Float, yTop: Float, yBottom: Float, estHeight: Float, content: @Composable () -> Unit) {
        owner = token; this.x = x; this.yTop = yTop; this.yBottom = yBottom; this.estHeight = estHeight; this.content = content
    }
    fun hide(token: Any) { if (owner === token) { owner = null; content = null } }
}

val LocalMenuOverlay = staticCompositionLocalOf<MenuOverlay?> { null }

@Composable
fun MenuOverlayHost(overlay: MenuOverlay) {
    val c = overlay.content ?: return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxW = constraints.maxWidth.toFloat(); val maxH = constraints.maxHeight.toFloat()
        val menuW = with(LocalDensity.current) { 380.dp.toPx() }
        val flip = overlay.yBottom + overlay.estHeight > maxH && overlay.yTop - overlay.estHeight > 0
        val x = overlay.x.coerceAtMost((maxW - menuW - 8f).coerceAtLeast(0f)).coerceAtLeast(0f)
        val y = if (flip) overlay.yTop - overlay.estHeight - 4f else overlay.yBottom + 4f
        Box(Modifier.offset { IntOffset(x.toInt(), y.toInt()) }) { c() }
    }
}

@Composable
fun Dropdown(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalPal.current.text,
    weight: FontWeight? = null,
    optionColor: (String) -> Color = { Color.Unspecified },
    bordered: Boolean = true,
) {
    val p = LocalPal.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.clip(RoundedCornerShape(5.dp)).then(if (bordered) Modifier.background(p.field).border(1.dp, p.border, RoundedCornerShape(5.dp)) else Modifier)
                .clickable { open = true }.pointerHoverIcon(PointerIcon.Hand).height(CONTROL_H).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            T(selected, color = color, weight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Ico(Ic.DropDown, tint = p.muted, size = 16.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = p.panel) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { T(o, color = optionColor(o).let { if (it == Color.Unspecified) p.text else it }, weight = if (o == selected) FontWeight.SemiBold else null) },
                    onClick = { open = false; onSelect(o) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                )
            }
        }
    }
}
