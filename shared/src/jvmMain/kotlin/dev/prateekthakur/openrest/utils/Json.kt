package dev.prateekthakur.openrest.utils

/** Minimal dependency-free JSON parser / writer. Objects -> LinkedHashMap, arrays -> List, numbers -> Long/Double. */
object Json {
    class JsonException(msg: String) : Exception(msg)

    fun parse(s: String): Any? = Parser(s).top()

    fun tryParse(s: String): Any? = try { parse(s) } catch (_: Exception) { null }

    fun isValid(s: String): Boolean = try { parse(s); true } catch (_: Exception) { false }

    @Suppress("UNCHECKED_CAST")
    fun obj(v: Any?): Map<String, Any?>? = v as? Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun arr(v: Any?): List<Any?>? = v as? List<Any?>

    fun str(v: Any?): String? = when (v) {
        null -> null
        is String -> v
        is Double -> if (v % 1.0 == 0.0 && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
        else -> v.toString()
    }

    fun stringify(v: Any?, pretty: Boolean = false, indent: String = "  "): String {
        val sb = StringBuilder()
        write(sb, v, pretty, indent, 0)
        return sb.toString()
    }

    fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
        }
        return sb.append('"').toString()
    }

    private fun write(sb: StringBuilder, v: Any?, pretty: Boolean, ind: String, depth: Int) {
        when (v) {
            null -> sb.append("null")
            is String -> sb.append(quote(v))
            is Boolean, is Long, is Int -> sb.append(v.toString())
            is Double -> sb.append(str(v))
            is Number -> sb.append(v.toString())
            is Map<*, *> -> {
                if (v.isEmpty()) { sb.append("{}"); return }
                sb.append('{')
                var first = true
                for ((k, x) in v) {
                    if (!first) sb.append(',')
                    first = false
                    if (pretty) sb.append('\n').append(ind.repeat(depth + 1))
                    sb.append(quote(k.toString())).append(if (pretty) ": " else ":")
                    write(sb, x, pretty, ind, depth + 1)
                }
                if (pretty) sb.append('\n').append(ind.repeat(depth))
                sb.append('}')
            }
            is List<*> -> {
                if (v.isEmpty()) { sb.append("[]"); return }
                sb.append('[')
                var first = true
                for (x in v) {
                    if (!first) sb.append(',')
                    first = false
                    if (pretty) sb.append('\n').append(ind.repeat(depth + 1))
                    write(sb, x, pretty, ind, depth + 1)
                }
                if (pretty) sb.append('\n').append(ind.repeat(depth))
                sb.append(']')
            }
            else -> sb.append(quote(v.toString()))
        }
    }

    /**
     * Re-indent JSON text without altering string/number literals. Returns null if the text is not valid JSON.
     */
    fun prettyText(text: String, indent: String = "  "): String? {
        if (!isValid(text)) return null
        val sb = StringBuilder()
        var depth = 0
        var i = 0
        val n = text.length
        fun nl() { sb.append('\n').append(indent.repeat(depth)) }
        while (i < n) {
            val c = text[i]
            when {
                c == '"' -> {
                    val st = i
                    i++
                    while (i < n && text[i] != '"') { if (text[i] == '\\') i++; i++ }
                    sb.append(text, st, minOf(i + 1, n))
                }
                c == '{' || c == '[' -> {
                    var j = i + 1
                    while (j < n && text[j].isWhitespace()) j++
                    val close = if (c == '{') '}' else ']'
                    if (j < n && text[j] == close) { sb.append(c).append(close); i = j }
                    else { sb.append(c); depth++; nl() }
                }
                c == '}' || c == ']' -> { depth--; nl(); sb.append(c) }
                c == ',' -> { sb.append(c); nl() }
                c == ':' -> sb.append(": ")
                c.isWhitespace() -> {}
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private class Parser(val s: String) {
        var i = 0
        fun err(m: String = "Unexpected token"): Nothing = throw JsonException("$m at position $i")
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun top(): Any? {
            ws()
            val v = value()
            ws()
            if (i < s.length) err()
            return v
        }
        fun value(): Any? {
            ws()
            if (i >= s.length) err("Unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> string()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) number() else err()
            }
        }
        fun lit(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) err()
            i += w.length
            return v
        }
        fun number(): Any {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(st, i)
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: err("Bad number")
        }
        fun string(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) err("Unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) err()
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun obj(): Map<String, Any?> {
            i++
            val m = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') err("Expected key")
                val k = string()
                ws()
                if (i >= s.length || s[i] != ':') err("Expected ':'")
                i++
                m[k] = value()
                ws()
                if (i >= s.length) err("Unexpected end")
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; return m }
                err()
            }
        }
        fun arr(): List<Any?> {
            i++
            val l = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                l.add(value())
                ws()
                if (i >= s.length) err("Unexpected end")
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; return l }
                err()
            }
        }
    }
}

/** Tiny path query used by response filter and post-response vars: `$.a.b[0].c`, `a.b`, `[0].x`. */
object JsonPath {
    fun query(root: Any?, path: String): Any? {
        var p = path.trim()
        if (p.startsWith("$")) p = p.removePrefix("$")
        var cur: Any? = root
        val re = Regex("""\.?([^.\[\]]+)|\[(\d+|\"[^\"]*\"|'[^']*')]""")
        for (m in re.findAll(p)) {
            val key = m.groupValues[1].ifEmpty { m.groupValues[2] }.trim('"', '\'')
            cur = when (cur) {
                is Map<*, *> -> cur[key]
                is List<*> -> if (key == "length") cur.size.toLong() else key.toIntOrNull()?.let { cur.getOrNull(it) }
                else -> return null
            }
        }
        return cur
    }
}
