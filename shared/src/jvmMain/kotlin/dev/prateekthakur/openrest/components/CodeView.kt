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

/** Read-only, virtualised viewer for large text such as response bodies. */
@Composable
fun CodeView(text: String, lang: String, modifier: Modifier = Modifier, wrap: Boolean = true, query: String = "", focusLine: Int = -1) {
    val p = LocalPal.current
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, color = p.text)
    val lines = remember(text) { codeLines(text) }
    val state = rememberLazyListState()
    LaunchedEffect(focusLine, text) { if (focusLine >= 0) state.scrollToItem((focusLine - 3).coerceAtLeast(0)) }
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(5.dp))) {
        SelectionContainer {
            LazyColumn(Modifier.fillMaxSize().padding(vertical = 6.dp), state = state) {
                itemsIndexed(lines) { i, l ->
                    Row(Modifier.background(if (i == focusLine) p.accent.copy(alpha = 0.12f) else Color.Transparent)) {
                        // the gutter is not part of the content: keep it out of selections and copies
                        androidx.compose.foundation.text.selection.DisableSelection {
                            Text((i + 1).toString(), style = style.copy(color = p.muted.copy(alpha = 0.6f), textAlign = androidx.compose.ui.text.style.TextAlign.End), modifier = Modifier.width(46.dp).padding(end = 8.dp))
                        }
                        Text(if (l.length < 20000) highlight(l, lang, p, query = query) else AnnotatedString(l), style = style, softWrap = wrap, modifier = Modifier.weight(1f).padding(end = 8.dp))
                    }
                }
            }
        }
        VerticalScrollbar(androidx.compose.foundation.rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}
