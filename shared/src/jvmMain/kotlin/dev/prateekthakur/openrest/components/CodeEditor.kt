package dev.prateekthakur.openrest.components

import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CodeEditor(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    lang: String = "text",
    readOnly: Boolean = false,
    placeholder: String = "",
    showStatus: Boolean = false,
    lineNumbers: Boolean = true,
    errorLine: Int = -1,
) {
    val p = LocalPal.current
    val known = LocalVars.current
    val vs = rememberScrollState()
    val measurer = rememberTextMeasurer(200)
    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, color = p.text)
    var tfv by remember { mutableStateOf(TextFieldValue(value)) }
    val cur = if (tfv.text == value) tfv else TextFieldValue(value, TextRange(value.length))
    var focused by remember { mutableStateOf(false) }
    val lineStarts = remember(cur.text) { ArrayList<Int>().also { l -> l.add(0); cur.text.forEachIndexed { i, c -> if (c == '\n') l.add(i + 1) } } }
    val curLine = lineCol(cur.text, cur.selection.end).first
    val gutterW = if (lineNumbers) (maxOf(2, lineStarts.size.toString().length) * 8 + 20).dp else 0.dp
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(5.dp)).background(p.field)
            .border(1.dp, if (focused) p.accent.copy(alpha = 0.7f) else p.border, RoundedCornerShape(5.dp)),
    ) {
        val minH = maxHeight
        Box(Modifier.fillMaxSize().verticalScroll(vs)) {
            BasicTextField(
                value = cur,
                onValueChange = { nv ->
                    val ed = if (readOnly) nv else smartEdit(cur, nv, lang)
                    tfv = ed
                    if (ed.text != value) onChange(ed.text)
                },
                readOnly = readOnly, textStyle = style,
                cursorBrush = SolidColor(p.accent),
                visualTransformation = remember(lang, p, known) { HighlightTransform(lang, p, known) },
                onTextLayout = { layout = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = minH)
                    .drawBehind {
                        if (!lineNumbers) return@drawBehind
                        val top = 10.dp.toPx(); val gw = gutterW.toPx()
                        val numStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 19.sp)
                        drawRect(p.hover.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Size(gw, size.height))
                        drawLine(p.border, androidx.compose.ui.geometry.Offset(gw, 0f), androidx.compose.ui.geometry.Offset(gw, size.height), 1f)
                        val lay = layout
                        val maxOff = lay?.layoutInput?.text?.length ?: 0
                        val viewTop = vs.value.toFloat(); val viewBottom = viewTop + minH.toPx()
                        val lineH = 19.dp.toPx()
                        for (i in lineStarts.indices) {
                            val vl = lay?.getLineForOffset(lineStarts[i].coerceIn(0, maxOff))
                            val y = if (lay != null && vl != null) top + lay.getLineTop(vl) else top + i * lineH
                            if (y + lineH < viewTop - lineH || y > viewBottom + lineH) continue
                            val isErr = i + 1 == errorLine
                            if (isErr) {
                                val endVl = if (lay != null && i + 1 < lineStarts.size) lay.getLineForOffset((lineStarts[i + 1] - 1).coerceIn(0, maxOff)) else vl
                                val h = if (lay != null && vl != null && endVl != null) lay.getLineBottom(endVl) - lay.getLineTop(vl) else lineH
                                drawRect(p.err.copy(alpha = 0.14f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, h))
                            }
                            val color = if (isErr) p.err else if (i + 1 == curLine && focused) p.text else p.muted.copy(alpha = 0.65f)
                            val tl = measurer.measure((i + 1).toString(), numStyle.copy(color = color))
                            drawText(tl, topLeft = androidx.compose.ui.geometry.Offset(gw - 10.dp.toPx() - tl.size.width, y))
                        }
                    }
                    .padding(start = gutterW + 10.dp, end = 10.dp, top = 10.dp, bottom = if (showStatus) 30.dp else 10.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { e ->
                        if (readOnly || e.type != KeyEventType.KeyDown || e.key != Key.Tab) return@onPreviewKeyEvent false
                        if (!e.isShiftPressed) {
                            val sel = cur.selection
                            val nt = cur.text.replaceRange(sel.min, sel.max, "  ")
                            tfv = TextFieldValue(nt, TextRange(sel.min + 2)); onChange(nt)
                        }
                        true
                    },
                decorationBox = { inner ->
                    Box { if (value.isEmpty()) Text(placeholder, style = style.copy(color = p.muted.copy(alpha = 0.6f))); inner() }
                },
            )
        }
        if (showStatus) {
            val (ln, col) = lineCol(cur.text, cur.selection.end)
            Row(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(4.dp)).background(p.panel.copy(alpha = 0.92f)).padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                T("Ln $ln, Col $col", color = p.muted, size = 11, mono = true)
                T("${cur.text.length} chars", color = p.muted, size = 11)
            }
        }
    }
}
