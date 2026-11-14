package dev.prateekthakur.openrest.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import dev.prateekthakur.openrest.app.Platform
import dev.prateekthakur.openrest.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage

// ======================= JSON tree =======================

private class TreeNode(val id: String, val key: String?, val value: Any?, val depth: Int, val path: String) {
    val container get() = value is Map<*, *> || value is List<*>
    val size get() = (value as? Map<*, *>)?.size ?: (value as? List<*>)?.size ?: 0
}

private fun childPath(parent: String, key: Any?, isIndex: Boolean) =
    if (isIndex) "$parent[$key]" else if (Regex("[A-Za-z_][A-Za-z0-9_]*").matches(key.toString())) "$parent.$key" else "$parent['$key']"

private fun flatten(root: Any?, expanded: Set<String>): List<TreeNode> {
    val out = ArrayList<TreeNode>()
    fun walk(key: String?, v: Any?, depth: Int, path: String) {
        out += TreeNode(path, key, v, depth, path)
        if (path in expanded) when (v) {
            is Map<*, *> -> v.forEach { (k, x) -> walk(k.toString(), x, depth + 1, childPath(path, k, false)) }
            is List<*> -> v.forEachIndexed { i, x -> walk("$i", x, depth + 1, childPath(path, i, true)) }
        }
    }
    walk(null, root, 0, "$")
    return out
}

private fun allContainers(root: Any?, limit: Int = 5000): Set<String> {
    val ids = HashSet<String>()
    fun walk(v: Any?, path: String) {
        if (ids.size > limit) return
        when (v) {
            is Map<*, *> -> { ids += path; v.forEach { (k, x) -> walk(x, childPath(path, k, false)) } }
            is List<*> -> { ids += path; v.forEachIndexed { i, x -> walk(x, childPath(path, i, true)) } }
        }
    }
    walk(root, "$")
    return ids
}

/** Collapsible JSON viewer. [expandSignal] > 0 expands everything, < 0 collapses (changes force a re-apply). */
@Composable
fun JsonTree(root: Any?, modifier: Modifier = Modifier, expandSignal: Int = 0, onCopy: (String, String) -> Unit) {
    val p = LocalPal.current
    val expanded = remember(root) { mutableStateListOf<String>().also { l -> l.addAll(allContainers(root, 400).filter { it.count { c -> c == '.' || c == '[' } <= 1 }) } }
    LaunchedEffect(expandSignal) {
        if (expandSignal > 0) { expanded.clear(); expanded.addAll(allContainers(root)) }
        else if (expandSignal < 0) { expanded.clear(); expanded.add("$") }
    }
    val rows = flatten(root, expanded.toSet())
    val state = rememberLazyListState()
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(5.dp))) {
        LazyColumn(Modifier.fillMaxSize().padding(vertical = 6.dp), state = state) {
            items(rows, key = { it.id }) { n ->
                val src = remember { MutableInteractionSource() }
                val hov by src.collectIsHoveredAsState()
                val open = n.id in expanded
                Row(
                    Modifier.fillMaxWidth().height(24.dp).background(if (hov) p.hover.copy(alpha = 0.5f) else Color.Transparent).hoverable(src)
                        .clickable(enabled = n.container) { if (open) expanded.remove(n.id) else expanded.add(n.id) }
                        .padding(start = (8 + n.depth * 16).dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (n.container) Chevron(open) else Spacer(Modifier.width(14.dp))
                    if (n.key != null) {
                        T(n.key, color = p.syntaxKey, mono = true, size = 12, maxLines = 1)
                        T(":", color = p.muted, mono = true, size = 12)
                    }
                    when (val v = n.value) {
                        is Map<*, *> -> T(if (open) "{" else "{ … }  ${n.size} ${if (n.size == 1) "key" else "keys"}", color = p.muted, mono = true, size = 12)
                        is List<*> -> T(if (open) "[" else "[ … ]  ${n.size} ${if (n.size == 1) "item" else "items"}", color = p.muted, mono = true, size = 12)
                        is String -> T("\"" + v.replace("\n", "⏎") + "\"", color = p.syntaxString, mono = true, size = 12, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        is Boolean, null -> T(v?.toString() ?: "null", color = p.syntaxKeyword, mono = true, size = 12)
                        else -> T(Json.str(v) ?: v.toString(), color = p.syntaxNumber, mono = true, size = 12)
                    }
                    Spacer(Modifier.weight(1f))
                    if (hov) {
                        T(n.path, color = p.muted.copy(alpha = 0.7f), mono = true, size = 10, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp))
                        IconBtn(Ic.Link, { onCopy(n.path, "Copied path") }, size = 13)
                        IconBtn(Ic.Copy, { onCopy(if (n.value is String) n.value else Json.stringify(n.value, true), "Copied value") }, size = 13)
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

// ======================= HTML preview =======================

/** Renders HTML with Swing's HTML engine into an image (HTML 3.2 + basic CSS). Full fidelity: use "Open in browser". */
fun renderHtml(html: String, widthPx: Int): BufferedImage? = try {
    val ed = javax.swing.JEditorPane()
    ed.contentType = "text/html"
    ed.isEditable = false
    ed.text = html
    ed.setSize(widthPx, Short.MAX_VALUE.toInt())
    val h = ed.preferredSize.height.coerceIn(40, 20000)
    ed.setSize(widthPx, h)
    val img = BufferedImage(widthPx, h, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.color = java.awt.Color.WHITE
    g.fillRect(0, 0, widthPx, h)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    ed.paint(g)
    g.dispose()
    img
} catch (_: Throwable) { null }

@Composable
fun HtmlPreview(html: String, bytes: ByteArray, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    BoxWithConstraints(modifier.clip(RoundedCornerShape(5.dp)).background(Color.White).border(1.dp, p.border, RoundedCornerShape(5.dp))) {
        val wPx = constraints.maxWidth
        val bmp by produceState<ImageBitmap?>(null, html, wPx) {
            value = withContext(Dispatchers.Default) { renderHtml(html.take(2_000_000), wPx.coerceAtLeast(200))?.toComposeImageBitmap() }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            bmp?.let { Image(it, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) } ?: Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) { T("Rendering…", color = Color.Gray) }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Btn("Open in browser", { Platform.openBytes(bytes, ".html", browser = true) }, icon = Ic.Open)
        }
        Box(Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
            T("Simplified render (HTML 3.2 / basic CSS, no scripts)", color = Color.White, size = 10)
        }
    }
}

// ======================= image =======================

@Composable
fun ImagePreview(res: HttpResult, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val density = LocalDensity.current
    val bmp = remember(res) { runCatching { org.jetbrains.skia.Image.makeFromEncoded(res.body).toComposeImageBitmap() }.getOrNull() }
    var fit by remember { mutableStateOf(true) }
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(5.dp))) {
        if (bmp == null) { T("Cannot render this image format", color = p.muted, modifier = Modifier.align(Alignment.Center)); return@Box }
        if (fit) Image(bmp, null, Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit)
        else Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
            Image(bmp, null, Modifier.size(with(density) { bmp.width.toDp() }, with(density) { bmp.height.toDp() }))
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(p.panel.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 5.dp)) {
                T("${bmp.width} × ${bmp.height}  ·  ${res.contentType.substringBefore(';').substringAfter('/').uppercase()}", color = p.muted, size = 11)
            }
            Btn(if (fit) "Actual size" else "Fit", { fit = !fit })
        }
    }
}

// ======================= pdf / audio / video / binary =======================

@Composable
fun MediaPreview(res: HttpResult, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val ct = res.contentType.substringBefore(';').lowercase()
    val (icon, label, ext) = when {
        ct.contains("pdf") -> Triple(Ic.Pdf, "PDF document", ".pdf")
        ct.startsWith("audio/") -> Triple(Ic.Audio, "Audio", "." + ct.substringAfter('/').substringBefore('+').ifEmpty { "bin" })
        ct.startsWith("video/") -> Triple(Ic.Video, "Video", "." + ct.substringAfter('/').substringBefore('+').ifEmpty { "bin" })
        else -> Triple(Ic.FileGeneric, "Binary data", ".bin")
    }
    Column(modifier.clip(RoundedCornerShape(5.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(5.dp)), Arrangement.Center, Alignment.CenterHorizontally) {
        Ico(icon, tint = p.accent, size = 56.dp)
        Spacer(Modifier.height(10.dp))
        T(label, size = 15, weight = FontWeight.SemiBold)
        T("${ct.ifEmpty { "unknown type" }}  ·  ${if (res.size < 1024) "${res.size} B" else if (res.size < 1048576) "%.1f KB".format(res.size / 1024.0) else "%.2f MB".format(res.size / 1048576.0)}", color = p.muted, size = 12, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
        Btn("Open with default app", { Platform.openBytes(res.body, ext) }, primary = true, icon = Ic.Open)
        T("This type can't be rendered inline. Use Save in the header to keep a copy.", color = p.muted, size = 11, modifier = Modifier.padding(top = 10.dp))
    }
}

// ======================= response format menu (Bruno style) =======================

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

@Composable
fun FormatMenu(
    current: String,
    preview: Boolean,
    previewEnabled: Boolean,
    onFormat: (String) -> Unit,
    onPreview: (Boolean) -> Unit,
    formats: List<String> = TEXT_FORMATS,
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
            if (preview) Box(Modifier.clip(RoundedCornerShape(4.dp)).background(p.accent.copy(alpha = 0.18f)).padding(horizontal = 5.dp, vertical = 1.dp)) { T("Preview", color = p.accent, size = 10, weight = FontWeight.SemiBold) }
            Ico(Ic.DropDown, tint = p.muted, size = 16.dp)
        }
        androidx.compose.material3.DropdownMenu(
            expanded = open, onDismissRequest = { open = false }, containerColor = p.panel, shape = RoundedCornerShape(10.dp),
            modifier = Modifier.width(176.dp), offset = androidx.compose.ui.unit.DpOffset(0.dp, 4.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp).height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                T("Preview", color = if (previewEnabled) p.text else p.muted, size = 13, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Toggle(preview && previewEnabled, onPreview, previewEnabled)
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(1.dp).background(p.border))
            formats.forEach { f -> FormatRow(f, f == current) { open = false; onFormat(f) } }
            Box(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(1.dp).background(p.border))
            BINARY_FORMATS.forEach { f -> FormatRow(f, f == current) { open = false; onFormat(f) } }
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
