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

// ---------------- syntax highlighting ----------------

private val jsonRe = Regex("\"(?:\\\\.|[^\"\\\\])*\"(\\s*:)?|-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?|\\b(?:true|false|null)\\b")

private val xmlRe = Regex("<!--.*?-->|</?[A-Za-z_][\\w:.-]*|/?>|\\s[\\w:.-]+(?==)|\"[^\"]*\"|'[^']*'")

private val jsRe = Regex("//.*|/\\*.*?\\*/|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`[^`]*`|\\b\\d+(?:\\.\\d+)?\\b|\\b(?:const|let|var|function|return|if|else|for|while|await|async|new|try|catch|throw|true|false|null|undefined|typeof|of|in|class|import|from|switch|case|break|default)\\b")

internal val varRe = Regex("\\{\\{\\s*([^{}]+?)\\s*}}")

private class LangSpec(
    val kw: String, val line: List<String> = listOf("//"), val block: Boolean = true, val triple: Boolean = false, val backtick: Boolean = false,
    val dollarVar: Boolean = false, val flags: Boolean = false, val cmdlet: Boolean = false, val types: Boolean = true,
)

private val LANGS: Map<String, Regex> by lazy {
    val specs = mapOf(
        "py" to LangSpec("import|from|as|def|class|return|if|else|elif|for|while|in|not|and|or|is|None|True|False|with|try|except|finally|raise|lambda|yield|pass|print", listOf("#"), block = false, triple = true),
        "java" to LangSpec("import|package|public|private|protected|static|final|class|new|void|return|if|else|for|while|try|catch|throw|throws|var|int|long|boolean|null|true|false|extends|implements|interface|this"),
        "kt" to LangSpec("val|var|fun|class|object|import|package|return|if|else|for|while|try|catch|throw|null|true|false|when|is|in|as|it|override|private|public|internal|data|use", triple = true),
        "go" to LangSpec("package|import|func|var|const|type|struct|interface|return|if|else|for|range|go|defer|nil|true|false|map|chan|select|switch|case|default", backtick = true),
        "php" to LangSpec("function|return|echo|if|else|foreach|as|new|class|true|false|null|array|use|curl_init|curl_exec", listOf("//", "#"), dollarVar = true),
        "cs" to LangSpec("using|namespace|var|new|await|async|return|if|else|foreach|in|class|public|private|static|void|string|int|bool|null|true|false|try|catch|throw"),
        "rb" to LangSpec("require|def|end|class|module|return|if|else|elsif|unless|do|while|puts|nil|true|false|begin|rescue", listOf("#"), block = false),
        "swift" to LangSpec("import|let|var|func|class|struct|return|if|else|guard|for|in|while|nil|true|false|try|catch|throw|as", triple = true),
        "rs" to LangSpec("let|mut|fn|use|mod|struct|enum|impl|pub|return|if|else|match|for|in|while|loop|async|await|true|false|self|Self"),
        "dart" to LangSpec("import|void|main|async|await|final|var|const|class|return|if|else|for|while|null|true|false|new"),
        "ps" to LangSpec("function|if|else|foreach|in|return|param|true|false|null", listOf("#"), block = false, dollarVar = true, flags = true, cmdlet = true, types = false),
        "sh" to LangSpec("curl|wget|http|https|echo|export", listOf("#"), block = false, dollarVar = true, flags = true, types = false),
    )
    specs.mapValues { (_, sp) ->
        val comment = (sp.line.map { Regex.escape(it) + ".*" } + (if (sp.block) listOf("/\\*[\\s\\S]*?\\*/") else emptyList())).joinToString("|")
        val str = buildList {
            if (sp.triple) { add("\"\"\"[\\s\\S]*?\"\"\""); add("'''[\\s\\S]*?'''") }
            add("\"(?:\\\\.|[^\"\\\\])*\""); add("'(?:\\\\.|[^'\\\\])*'")
            if (sp.backtick) add("`[^`]*`")
        }.joinToString("|")
        val parts = mutableListOf("(?<c>$comment)", "(?<s>$str)")
        if (sp.dollarVar) parts += "(?<v>\\$\\{[^}]*}|\\$[A-Za-z_][\\w]*)"
        parts += "(?<n>\\b\\d+(?:\\.\\d+)?\\b)"
        if (sp.cmdlet) parts += "(?<k2>\\b[A-Z][a-z]+-[A-Z][A-Za-z]+\\b)"
        parts += "(?<k>\\b(?:${sp.kw})\\b)"
        if (sp.flags) parts += "(?<f>(?<=\\s)--?[A-Za-z][\\w-]*)"
        if (sp.types) parts += "(?<t>\\b[A-Z][A-Za-z0-9_]*\\b)"
        Regex(parts.joinToString("|"))
    }
}

fun highlight(text: String, lang: String, p: Pal, known: Set<String> = emptySet(), query: String = ""): AnnotatedString {
    val b = AnnotatedString.Builder(text)
    fun span(r: IntRange, c: Color, bold: Boolean = false) = b.addStyle(SpanStyle(color = c, fontWeight = if (bold) FontWeight.SemiBold else null), r.first, r.last + 1)
    LANGS[lang]?.let { re ->
        re.findAll(text).forEach { m ->
            fun has(n: String) = try { m.groups[n] != null } catch (_: IllegalArgumentException) { false }   // group absent in this language
            when {
                has("c") -> span(m.range, p.syntaxComment)
                has("s") -> span(m.range, p.syntaxString)
                has("v") || has("n") -> span(m.range, p.syntaxNumber)
                has("k") || has("k2") -> span(m.range, p.syntaxKeyword)
                has("f") || has("t") -> span(m.range, p.syntaxKey)
            }
        }
    }
    when (lang) {
        "json" -> jsonRe.findAll(text).forEach { m ->
            val s = m.value
            when {
                s.startsWith("\"") -> if (m.groups[1] != null) span(m.range.first..(m.range.first + s.trimEnd(':', ' ').length - 1), p.syntaxKey) else span(m.range, p.syntaxString)
                s[0].isDigit() || s[0] == '-' -> span(m.range, p.syntaxNumber)
                else -> span(m.range, p.syntaxKeyword)
            }
        }
        "xml", "html" -> xmlRe.findAll(text).forEach { m ->
            val s = m.value
            when {
                s.startsWith("<!--") -> span(m.range, p.syntaxComment)
                s.startsWith("<") || s.endsWith(">") -> span(m.range, p.syntaxKey)
                s.startsWith("\"") || s.startsWith("'") -> span(m.range, p.syntaxString)
                else -> span(m.range, p.syntaxNumber)
            }
        }
        "js" -> jsRe.findAll(text).forEach { m ->
            val s = m.value
            when {
                s.startsWith("//") || s.startsWith("/*") -> span(m.range, p.syntaxComment)
                s[0] == '"' || s[0] == '\'' || s[0] == '`' -> span(m.range, p.syntaxString)
                s[0].isDigit() -> span(m.range, p.syntaxNumber)
                else -> span(m.range, p.syntaxKeyword)
            }
        }
    }
    if (text.contains("{{")) varRe.findAll(text).forEach { m ->
        val n = m.groupValues[1]
        val ok = n in known || n.startsWith("\$") || n.startsWith("process.env.") || dev.prateekthakur.openrest.core.run.WorkflowRunner.isStepRef(n) || (n.contains('.') && n.substringBefore('.') in known)
        span(m.range, if (ok) p.ok else p.err, true)
    }
    if (query.isNotEmpty()) {
        var i = text.indexOf(query, 0, true)
        while (i >= 0) {
            b.addStyle(SpanStyle(background = p.accent.copy(alpha = 0.45f), color = p.text), i, i + query.length)
            i = text.indexOf(query, i + query.length, true)
        }
    }
    return b.toAnnotatedString()
}

fun codeLines(text: String): List<String> {
    val out = ArrayList<String>()
    for (l in text.split("\n")) if (l.length <= 3000) out.add(l) else l.chunked(3000).forEach { out.add(it) }
    return out
}

private class HighlightTransform(val lang: String, val p: Pal, val known: Set<String>) : VisualTransformation {
    override fun filter(text: AnnotatedString) = TransformedText(highlight(text.text, lang, p, known), OffsetMapping.Identity)
}

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
                        Text((i + 1).toString(), style = style.copy(color = p.muted.copy(alpha = 0.6f), textAlign = androidx.compose.ui.text.style.TextAlign.End), modifier = Modifier.width(46.dp).padding(end = 8.dp))
                        Text(if (l.length < 20000) highlight(l, lang, p, query = query) else AnnotatedString(l), style = style, softWrap = wrap, modifier = Modifier.weight(1f).padding(end = 8.dp))
                    }
                }
            }
        }
        VerticalScrollbar(androidx.compose.foundation.rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

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
