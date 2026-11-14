package dev.prateekthakur.openrest.components

import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.theme.*
import dev.prateekthakur.openrest.utils.*

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import dev.prateekthakur.openrest.core.model.KV
import dev.prateekthakur.openrest.utils.UrlCodec
import java.awt.Cursor

@Composable
private fun DecodeChip(onClick: () -> Unit) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Row(
        Modifier.padding(end = 6.dp).clip(RoundedCornerShape(10.dp)).background(p.accent.copy(alpha = if (hov) 0.3f else 0.16f)).hoverable(src)
            .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Ico(Ic.Codec, tint = p.accent, size = 12.dp)
        T("Decode", color = p.accent, size = 10, weight = FontWeight.SemiBold)
    }
}

private val ROW_H = 34.dp

private val HANDLE_W = 22.dp

private val CHECK_W = 26.dp

/** A cell with a focus underline; content is a borderless Field. */
@Composable
private fun TableCell(modifier: Modifier, content: @Composable () -> Unit) {
    val p = LocalPal.current
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.fillMaxHeight().onFocusChanged { focused = it.hasFocus }.drawBehind {
            if (focused) {
                drawRect(p.accent.copy(alpha = 0.07f))
                drawRect(p.accent, androidx.compose.ui.geometry.Offset(0f, size.height - 2.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx()))
            }
            drawLine(p.border, androidx.compose.ui.geometry.Offset(size.width - 0.5f, 0f), androidx.compose.ui.geometry.Offset(size.width - 0.5f, size.height), 1f)
        },
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

@Composable
fun KVTable(
    items: List<KV>,
    onChange: (List<KV>) -> Unit,
    modifier: Modifier = Modifier,
    keyHint: String = "Name",
    valueHint: String = "Value",
    allowFile: Boolean = false,
    onPickFile: ((KV, (String) -> Unit) -> Unit)? = null,
    keySuggestions: List<String> = emptyList(),
    valueSuggestions: (String) -> List<String> = { emptyList() },
    keyHints: (String) -> String? = { null },
    encodeTools: Boolean = false,
    title: String? = null,
    extra: (@Composable RowScope.(Int, KV) -> Unit)? = null,
) {
    val p = LocalPal.current
    val density = LocalDensity.current
    var bulk by remember { mutableStateOf(false) }
    var bulkText by remember { mutableStateOf("") }
    var keyFrac by remember { mutableStateOf(0.4f) }
    var tableW by remember { mutableStateOf(1f) }
    var dragIdx by remember { mutableStateOf(-1) }
    val press = remember { floatArrayOf(0f, 0f) }
    val itemsRef by rememberUpdatedState(items)
    val changeRef by rememberUpdatedState(onChange)
    val rowPx = with(density) { ROW_H.toPx() }
    val handlePx = with(density) { HANDLE_W.toPx() }
    val extraW = if (extra != null) 74.dp else 0.dp
    val actionsW = (if (allowFile) 60.dp else 34.dp) + (if (encodeTools) 26.dp else 0.dp)
    val fixedPx = with(density) { (HANDLE_W + CHECK_W + extraW + actionsW).toPx() }

    fun toggleBulk() {
        if (!bulk) bulkText = items.joinToString("\n") { (if (it.enabled) "" else "//") + it.name + ": " + it.value }
        bulk = !bulk
    }
    fun update(i: Int, f: (KV) -> KV) = onChange(if (i >= items.size) items + f(KV()) else items.toMutableList().also { l -> l[i] = f(l[i]) })

    Column(modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ---- toolbar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) SectionLabel(title.uppercase())
            if (items.isNotEmpty()) {
                val on = items.count { it.enabled }
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(p.accent.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    T(if (on == items.size) "${items.size}" else "$on/${items.size}", color = p.accent, size = 10, weight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            if (encodeTools && items.isNotEmpty() && !bulk) {
                var codecMenu by remember { mutableStateOf(false) }
                Box {
                    IconBtn(Ic.Codec, { codecMenu = true })
                    ContextMenu(codecMenu, { codecMenu = false }, listOf(
                        MenuItem("Decode all (URL)") { onChange(items.map { it.copy(name = UrlCodec.decode(it.name), value = UrlCodec.decode(it.value)) }) },
                        MenuItem("Encode all (URL)") { onChange(items.map { it.copy(name = UrlCodec.encode(it.name), value = UrlCodec.encode(it.value)) }) },
                    ))
                }
            }
            if (!allowFile) IconBtn(Ic.Edit, { toggleBulk() }, color = if (bulk) p.accent else p.muted)
            if (!bulk) Btn("Add", { onChange(items + KV()) }, icon = Ic.Add)
        }
        if (bulk) {
            T("One  name: value  per line · prefix a line with // to disable it", color = p.muted, size = 11)
            AreaField(
                bulkText,
                { t ->
                    bulkText = t
                    onChange(t.split("\n").filter { it.isNotBlank() }.map { line ->
                        val off = line.trimStart().startsWith("//")
                        val body = line.trimStart().removePrefix("//").trim()
                        val i = body.indexOf(':')
                        if (i < 0) KV(body, "", !off) else KV(body.substring(0, i).trim(), body.substring(i + 1).trim(), !off)
                    })
                },
                Modifier.fillMaxWidth().heightIn(min = 160.dp), "Name: value",
            )
            return@Column
        }

        // ---- the grid
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(8.dp))
                .onSizeChanged { tableW = it.width.toFloat() },
        ) {
            // header
            Row(Modifier.fillMaxWidth().height(30.dp).background(p.hover.copy(alpha = 0.5f)).drawBottomBorder(p.border), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(HANDLE_W))
                Box(Modifier.width(CHECK_W), contentAlignment = Alignment.Center) {
                    val all = items.isNotEmpty() && items.all { it.enabled }
                    val some = items.any { it.enabled } && !all
                    Check(all, { v -> onChange(items.map { it.copy(enabled = v || some) }) }, Modifier.alpha(if (items.isEmpty()) 0.3f else 1f), partial = some)
                }
                Row(Modifier.weight(keyFrac).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    T(keyHint.uppercase(), color = p.muted, size = 10, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    // column resize handle
                    Box(
                        Modifier.width(7.dp).fillMaxHeight().pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
                            .pointerInput(Unit) { detectDragGestures { c, d -> c.consume(); keyFrac = (keyFrac + d.x / (tableW - fixedPx).coerceAtLeast(1f)).coerceIn(0.15f, 0.85f) } },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.width(1.dp).fillMaxHeight(0.55f).background(p.border)) }
                }
                T(valueHint.uppercase(), color = p.muted, size = 10, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f - keyFrac).padding(start = 10.dp))
                Spacer(Modifier.width(extraW + actionsW))
            }

            // rows (+ one always-ready empty row); drag a row's handle to reorder
            Column(
                Modifier.testTag("kv-rows")
                    .pointerInput(Unit) { awaitEachGesture { val d = awaitFirstDown(requireUnconsumed = false); press[0] = d.position.x; press[1] = d.position.y } }
                    .pointerInput(Unit) {
                        var acc = 0f
                        detectDragGestures(
                            onDragStart = {
                                val i = (press[1] / rowPx).toInt()
                                if (press[0] < handlePx && i < itemsRef.size) { dragIdx = i; acc = 0f } else dragIdx = -1
                            },
                            onDragEnd = { dragIdx = -1 }, onDragCancel = { dragIdx = -1 },
                        ) { c, d ->
                            if (dragIdx >= 0) {
                                c.consume()
                                acc += d.y
                                val l = itemsRef.toMutableList()
                                var moved = false
                                fun swap(a: Int, b: Int) { val t = l[a]; l[a] = l[b]; l[b] = t; moved = true }
                                while (acc > rowPx / 2 && dragIdx < l.size - 1) { swap(dragIdx, dragIdx + 1); dragIdx++; acc -= rowPx }
                                while (acc < -rowPx / 2 && dragIdx > 0) { swap(dragIdx, dragIdx - 1); dragIdx--; acc += rowPx }
                                if (moved) changeRef(l)
                            }
                        }
                    },
            ) {
                for (i in 0..items.size) key(i) {
                    val ghost = i >= items.size
                    val kv = items.getOrNull(i) ?: KV()
                    val src = remember { MutableInteractionSource() }
                    val hov by src.collectIsHoveredAsState()
                    val dragging = dragIdx == i
                    Row(
                        Modifier.fillMaxWidth().height(ROW_H).hoverable(src)
                            .background(if (dragging) p.selected else if (hov && !ghost) p.hover.copy(alpha = 0.35f) else Color.Transparent)
                            .drawBehind {
                                drawLine(p.border.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset(0f, size.height - 0.5f), androidx.compose.ui.geometry.Offset(size.width, size.height - 0.5f), 1f)
                                if (dragging) drawRect(p.accent, androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(HANDLE_W), contentAlignment = Alignment.Center) {
                            if (!ghost && (hov || dragging)) Ico(Ic.Drag, tint = p.muted, size = 16.dp, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand))
                        }
                        Box(Modifier.width(CHECK_W), contentAlignment = Alignment.Center) {
                            Check(kv.enabled && !ghost, { update(i) { k -> k.copy(enabled = it) } }, Modifier.alpha(if (ghost) 0.25f else 1f))
                        }
                        val dim = Modifier.alpha(if (kv.enabled || ghost) 1f else 0.45f)
                        TableCell(Modifier.weight(keyFrac)) {
                            Row(Modifier.fillMaxWidth().then(dim), verticalAlignment = Alignment.CenterVertically) {
                                Field(kv.name, { n -> update(i) { k -> k.copy(name = n) } }, Modifier.weight(1f), keyHint, mono = true, bordered = false, suggestions = keySuggestions, hints = keyHints)
                                if (encodeTools && !ghost && UrlCodec.looksEncoded(kv.name)) DecodeChip { update(i) { k -> k.copy(name = UrlCodec.decode(k.name)) } }
                            }
                        }
                        TableCell(Modifier.weight(1f - keyFrac)) {
                            if (kv.isFile) Row(Modifier.fillMaxWidth().then(dim), verticalAlignment = Alignment.CenterVertically) {
                                Field(kv.value, { v -> update(i) { k -> k.copy(value = v) } }, Modifier.weight(1f), "File path", mono = true, bordered = false, highlightVars = false)
                                IconBtn(Ic.Folder, { onPickFile?.invoke(kv) { path -> update(i) { k -> k.copy(value = path) } } })
                            } else Row(Modifier.fillMaxWidth().then(dim), verticalAlignment = Alignment.CenterVertically) {
                                Field(kv.value, { v -> update(i) { k -> k.copy(value = v) } }, Modifier.weight(1f), valueHint, mono = true, bordered = false, suggestions = valueSuggestions(kv.name))
                                if (encodeTools && !ghost && UrlCodec.looksEncoded(kv.value)) DecodeChip { update(i) { k -> k.copy(value = UrlCodec.decode(k.value)) } }
                            }
                        }
                        if (extra != null) Row(Modifier.width(extraW).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) { if (!ghost) extra.invoke(this, i, kv) }
                        Row(Modifier.width(actionsW), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            if (!ghost) {
                                if (allowFile) IconBtn(if (kv.isFile) Ic.Attach else Ic.Text, { update(i) { k -> k.copy(isFile = !k.isFile, value = "") } }, color = if (kv.isFile) p.accent else p.muted, size = 15)
                                if (encodeTools && hov) {
                                    var rowMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconBtn(Ic.Codec, { rowMenu = true }, size = 15)
                                        ContextMenu(rowMenu, { rowMenu = false }, listOf(
                                            MenuItem("Decode value") { update(i) { k -> k.copy(value = UrlCodec.decode(k.value)) } },
                                            MenuItem("Encode value") { update(i) { k -> k.copy(value = UrlCodec.encode(k.value)) } },
                                            MenuItem("Decode name") { update(i) { k -> k.copy(name = UrlCodec.decode(k.name)) } },
                                            MenuItem("Encode name") { update(i) { k -> k.copy(name = UrlCodec.encode(k.name)) } },
                                        ))
                                    }
                                }
                                if (hov) IconBtn(Ic.Delete, { onChange(items.toMutableList().also { l -> l.removeAt(i) }) }, color = p.err, size = 16)
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }
            }
        }
    }
}

class TRow(val name: String, val value: String, val note: String = "")

/** Read-only version of the key/value grid (same look): response headers, inherited headers, etc. */
@Composable
fun ReadOnlyTable(
    rows: List<TRow>,
    modifier: Modifier = Modifier,
    keyHint: String = "Name",
    valueHint: String = "Value",
    noteHint: String? = null,
    title: String? = null,
    onCopy: (String) -> Unit = {},
) {
    val p = LocalPal.current
    val density = LocalDensity.current
    var keyFrac by remember { mutableStateOf(0.32f) }
    var tableW by remember { mutableStateOf(1f) }
    val noteW = if (noteHint != null) 110.dp else 0.dp
    val fixedPx = with(density) { (34.dp + noteW).toPx() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(title.uppercase())
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(p.accent.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                T("${rows.size}", color = p.accent, size = 10, weight = FontWeight.Bold)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(8.dp)).onSizeChanged { tableW = it.width.toFloat() }) {
            Row(Modifier.fillMaxWidth().height(30.dp).background(p.hover.copy(alpha = 0.5f)).drawBottomBorder(p.border), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(keyFrac).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    T(keyHint.uppercase(), color = p.muted, size = 10, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Box(
                        Modifier.width(7.dp).fillMaxHeight().pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
                            .pointerInput(Unit) { detectDragGestures { c, d -> c.consume(); keyFrac = (keyFrac + d.x / (tableW - fixedPx).coerceAtLeast(1f)).coerceIn(0.15f, 0.85f) } },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.width(1.dp).fillMaxHeight(0.55f).background(p.border)) }
                }
                T(valueHint.uppercase(), color = p.muted, size = 10, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f - keyFrac).padding(start = 10.dp))
                if (noteHint != null) T(noteHint.uppercase(), color = p.muted, size = 10, weight = FontWeight.SemiBold, modifier = Modifier.width(noteW).padding(start = 10.dp))
                Spacer(Modifier.width(34.dp))
            }
            SelectionContainer {
                Column {
                    rows.forEach { r ->
                        val src = remember { MutableInteractionSource() }
                        val hov by src.collectIsHoveredAsState()
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = ROW_H).hoverable(src).background(if (hov) p.hover.copy(alpha = 0.35f) else Color.Transparent)
                                .drawBottomBorder(p.border.copy(alpha = 0.6f)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            T(r.name, color = p.syntaxKey, mono = true, modifier = Modifier.weight(keyFrac).padding(horizontal = 12.dp, vertical = 8.dp))
                            T(r.value, mono = true, modifier = Modifier.weight(1f - keyFrac).padding(horizontal = 10.dp, vertical = 8.dp))
                            if (noteHint != null) T(r.note, color = p.muted, size = 11, modifier = Modifier.width(noteW).padding(start = 10.dp), maxLines = 1)
                            Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                                if (hov) IconBtn(Ic.Copy, { onCopy(r.value) }, size = 15)
                            }
                        }
                    }
                }
            }
        }
    }
}
