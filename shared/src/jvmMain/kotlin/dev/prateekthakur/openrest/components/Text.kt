package dev.prateekthakur.openrest.components

import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPal.current.text,
    size: Int = 13,
    weight: FontWeight? = null,
    mono: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) = Text(
    text, modifier, color = color, fontSize = size.sp, fontWeight = weight,
    fontFamily = if (mono) FontFamily.Monospace else null, maxLines = maxLines, overflow = overflow,
)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) = T(text, color = LocalPal.current.muted, size = 11, weight = FontWeight.SemiBold, modifier = modifier)

@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        T(text, color = color, size = 12, weight = FontWeight.SemiBold)
    }
}

@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Box(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) p.accent.copy(alpha = 0.18f) else if (hov) p.hover else Color.Transparent)
            .border(1.dp, if (selected) p.accent.copy(alpha = 0.6f) else p.border, RoundedCornerShape(14.dp))
            .hoverable(src).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 11.dp, vertical = 4.dp),
    ) { T(label, color = if (selected) p.accent else p.text, size = 12, weight = if (selected) FontWeight.SemiBold else null) }
}
