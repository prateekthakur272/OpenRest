package dev.prateekthakur.openrest.components

import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.platform.*
import dev.prateekthakur.openrest.theme.*
import dev.prateekthakur.openrest.utils.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.prateekthakur.openrest.platform.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage

val TEXT_FORMATS = listOf("JSON", "HTML", "XML", "JavaScript")
val BINARY_FORMATS = listOf("Raw", "Hex", "Base64")

fun formatIcon(f: String) = when (f) {
    "JSON" -> Ic.FmtJson; "HTML" -> Ic.FmtHtml; "XML" -> Ic.FmtXml; "JavaScript" -> Ic.FmtJs
    "Hex" -> Ic.FmtHex; "Base64" -> Ic.FmtB64; else -> Ic.FmtRaw
}

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val p = LocalPal.current
    val x by androidx.compose.animation.core.animateDpAsState(if (checked) 16.dp else 2.dp, androidx.compose.animation.core.tween(120))
    Box(
        Modifier.size(32.dp, 18.dp).clip(RoundedCornerShape(9.dp))
            .background(if (!enabled) p.muted.copy(alpha = 0.25f) else if (checked) p.accent else p.muted.copy(alpha = 0.55f))
            .clickable(enabled = enabled) { onChange(!checked) },
    ) {
        Box(Modifier.offset(x, 2.dp).size(14.dp).clip(RoundedCornerShape(7.dp)).background(Color.White))
    }
}

@Composable
fun MenuRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Row(
        Modifier.padding(horizontal = 4.dp).fillMaxWidth().height(30.dp).clip(RoundedCornerShape(6.dp))
            .background(if (selected) p.accent.copy(alpha = 0.14f) else if (hov) p.hover else Color.Transparent)
            .hoverable(src).clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Ico(icon, tint = if (selected) p.accent else p.muted, size = 16.dp)
        T(label, color = if (selected) p.accent else p.text, size = 13, weight = if (selected) FontWeight.Medium else null, modifier = Modifier.weight(1f))
        if (selected) Ico(Ic.Check, tint = p.accent, size = 15.dp)
    }
}

@Composable
private fun FormatRow(label: String, selected: Boolean, onClick: () -> Unit) = MenuRow(label, formatIcon(label), selected, onClick)

class MenuOpt(val id: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

/** Compact dropdown with grouped, icon-led options (groups are separated by dividers). */
@Composable
fun OptionMenu(selected: String, groups: List<List<MenuOpt>>, onSelect: (String) -> Unit, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp = 196.dp) {
    val p = LocalPal.current
    var open by remember { mutableStateOf(false) }
    val cur = groups.flatten().firstOrNull { it.id == selected } ?: groups.first().first()
    Box(modifier) {
        Row(
            Modifier.clip(RoundedCornerShape(5.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(5.dp))
                .clickable { open = true }.pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon.Hand).height(CONTROL_H).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Ico(cur.icon, tint = p.accent, size = 16.dp)
            T(cur.label, weight = FontWeight.Medium)
            Ico(Ic.DropDown, tint = p.muted, size = 16.dp)
        }
        androidx.compose.material3.DropdownMenu(
            expanded = open, onDismissRequest = { open = false }, containerColor = p.panel, shape = RoundedCornerShape(10.dp),
            modifier = Modifier.width(width), offset = androidx.compose.ui.unit.DpOffset(0.dp, 4.dp),
        ) {
            groups.forEachIndexed { gi, g ->
                if (gi > 0) Box(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(1.dp).background(p.border))
                g.forEach { o -> MenuRow(o.label, o.icon, o.id == selected) { open = false; onSelect(o.id) } }
            }
        }
    }
}

/** Switches between the rendered view (image, JSON tree, HTML) and the plain text. Independent of the format menu; dimmed when the response has no preview. */
@Composable
fun PreviewToggle(on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val active = on && enabled
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Row(
        modifier.clip(RoundedCornerShape(5.dp))
            .background(if (active) p.accent.copy(alpha = 0.16f) else if (hov && enabled) p.hover else p.field)
            .border(1.dp, if (active) p.accent.copy(alpha = 0.6f) else p.border, RoundedCornerShape(5.dp))
            .hoverable(src).clickable(enabled = enabled) { onChange(!on) }
            .pointerHoverIcon(if (enabled) androidx.compose.ui.input.pointer.PointerIcon.Hand else androidx.compose.ui.input.pointer.PointerIcon.Default)
            .height(CONTROL_H).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Ico(if (active) Ic.Eye else Ic.EyeOff, tint = if (active) p.accent else p.muted.copy(alpha = if (enabled) 1f else 0.5f), size = 16.dp)
        T("Preview", color = if (active) p.accent else if (enabled) p.text else p.muted.copy(alpha = 0.6f), weight = FontWeight.Medium)
    }
}

@Composable
fun FormatMenu(
    current: String,
    onFormat: (String) -> Unit,
    textFormats: List<String> = TEXT_FORMATS,
    rawFormats: List<String> = BINARY_FORMATS,
    modifier: Modifier = Modifier,
) {
    val p = LocalPal.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.clip(RoundedCornerShape(5.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(5.dp))
                .clickable { open = true }.pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon.Hand).height(CONTROL_H).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Ico(formatIcon(current), tint = p.accent, size = 16.dp)
            T(current, weight = FontWeight.Medium)
            Ico(Ic.DropDown, tint = p.muted, size = 16.dp)
        }
        androidx.compose.material3.DropdownMenu(
            expanded = open, onDismissRequest = { open = false }, containerColor = p.panel, shape = RoundedCornerShape(10.dp),
            modifier = Modifier.width(176.dp), offset = androidx.compose.ui.unit.DpOffset(0.dp, 4.dp),
        ) {
            textFormats.forEach { f -> FormatRow(f, f == current) { open = false; onFormat(f) } }
            if (textFormats.isNotEmpty() && rawFormats.isNotEmpty()) Box(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(1.dp).background(p.border))
            rawFormats.forEach { f -> FormatRow(f, f == current) { open = false; onFormat(f) } }
        }
    }
}

fun hexDump(b: ByteArray, max: Int = 96 * 1024): String {
    val n = minOf(b.size, max)
    val sb = StringBuilder()
    var i = 0
    while (i < n) {
        sb.append("%08x  ".format(i))
        for (j in 0 until 16) {
            if (i + j < n) sb.append("%02x ".format(b[i + j])) else sb.append("   ")
            if (j == 7) sb.append(' ')
        }
        sb.append(" |")
        for (j in 0 until 16) if (i + j < n) { val c = b[i + j].toInt() and 0xff; sb.append(if (c in 32..126) c.toChar() else '.') }
        sb.append("|\n")
        i += 16
    }
    if (b.size > n) sb.append("… ${b.size - n} more bytes not shown")
    return sb.toString()
}

fun base64Wrapped(b: ByteArray, width: Int = 100): String =
    java.util.Base64.getEncoder().encodeToString(b).chunked(width).joinToString("\n")
