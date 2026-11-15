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

internal class HighlightTransform(val lang: String, val p: Pal, val known: Set<String>) : VisualTransformation {
    override fun filter(text: AnnotatedString) = TransformedText(highlight(text.text, lang, p, known), OffsetMapping.Identity)
}
