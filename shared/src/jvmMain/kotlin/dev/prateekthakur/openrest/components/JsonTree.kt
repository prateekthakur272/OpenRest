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
