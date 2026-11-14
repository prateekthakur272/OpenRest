package dev.prateekthakur.openrest.components

import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun Btn(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed && enabled) 0.96f else 1f, androidx.compose.animation.core.tween(80))
    val base = if (primary) p.accent else p.hover
    val bg by androidx.compose.animation.animateColorAsState(
        when { !enabled -> base.copy(alpha = 0.4f); hovered && primary -> androidx.compose.ui.graphics.lerp(base, Color.White, 0.18f); hovered -> p.selected; else -> base },
        androidx.compose.animation.core.tween(100),
    )
    val fg = when { primary -> Color.Black; danger -> p.err; else -> p.text }
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .then(if (!primary) Modifier.border(1.dp, if (hovered && enabled) p.muted.copy(alpha = 0.6f) else p.border, RoundedCornerShape(5.dp)) else Modifier)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .height(CONTROL_H).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val c = if (enabled) fg else fg.copy(alpha = 0.5f)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Ico(icon, tint = c, size = 15.dp)
            if (label.isNotEmpty()) T(label, color = c, size = 13, weight = if (primary) FontWeight.SemiBold else null)
        }
    }
}

@Composable
fun IconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = LocalPal.current.muted, size: Int = 16) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Box(
        modifier.size(26.dp).clip(RoundedCornerShape(5.dp)).background(if (hovered) p.hover else Color.Transparent)
            .hoverable(src).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, tint = if (hovered) p.text else color, size = size.dp) }
}

@Composable
fun Check(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, partial: Boolean = false) {
    val p = LocalPal.current
    val on = checked || partial
    Box(
        modifier.size(16.dp).clip(RoundedCornerShape(3.dp))
            .background(if (on) p.accent else Color.Transparent)
            .border(1.dp, if (on) p.accent else p.muted, RoundedCornerShape(3.dp))
            .clickable { onChange(!checked) }.pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) { if (partial) Ico(Ic.Dash, tint = Color.Black, size = 13.dp) else if (checked) Ico(Ic.Check, tint = Color.Black, size = 13.dp) }
}

@Composable
fun LabeledCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.clickable { onChange(!checked) }.padding(vertical = 2.dp)) {
        Check(checked, onChange)
        T(label)
    }
}
