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

/** Unified line diff with context collapsing. */
@Composable
fun DiffView(old: String, new: String, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, color = p.text)
    val diff = remember(old, new) { dev.prateekthakur.openrest.utils.Diff.lines(old.split("\n"), new.split("\n")) }
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(5.dp))) {
        if (diff == null) { T("Responses are too large to diff.", color = p.muted, modifier = Modifier.padding(12.dp)); return@Box }
        val added = diff.count { it.op == '+' }; val removed = diff.count { it.op == '-' }
        if (added + removed == 0) { T("✓ Responses are identical", color = p.ok, modifier = Modifier.padding(12.dp)); return@Box }
        val keep = BooleanArray(diff.size)
        diff.forEachIndexed { i, l -> if (l.op != ' ') for (k in (i - 3).coerceAtLeast(0)..(i + 3).coerceAtMost(diff.size - 1)) keep[k] = true }
        val rows = ArrayList<Pair<Int, dev.prateekthakur.openrest.utils.Diff.Line?>>()
        var gap = false
        diff.forEachIndexed { i, l -> if (keep[i]) { rows.add(i to l); gap = false } else if (!gap) { rows.add(i to null); gap = true } }
        LazyColumn(Modifier.fillMaxSize().padding(vertical = 6.dp)) {
            item { T("+$added  −$removed", color = p.muted, size = 11, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) }
            items(rows.size) { r ->
                val l = rows[r].second
                if (l == null) T("⋯", color = p.muted, mono = true, modifier = Modifier.padding(start = 10.dp))
                else Row(Modifier.fillMaxWidth().background(when (l.op) { '+' -> p.ok.copy(alpha = 0.16f); '-' -> p.err.copy(alpha = 0.16f); else -> Color.Transparent })) {
                    Text(l.op.toString(), style = style.copy(color = when (l.op) { '+' -> p.ok; '-' -> p.err; else -> p.muted }), modifier = Modifier.width(22.dp).padding(start = 8.dp))
                    Text(l.text, style = style, modifier = Modifier.weight(1f).padding(end = 8.dp))
                }
            }
        }
    }
}
