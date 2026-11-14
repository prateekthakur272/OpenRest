package dev.prateekthakur.openrest.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Editor niceties applied when exactly one character was typed or deleted:
 * auto-closing brackets/quotes, typing over a closer, smart indentation on Enter, paired backspace.
 */
fun smartEdit(old: TextFieldValue, new: TextFieldValue, lang: String): TextFieldValue {
    if (lang == "text") return new
    val o = old.text
    val n = new.text
    if (!old.selection.collapsed || !new.selection.collapsed) return new
    val oldPos = old.selection.start
    val code = lang == "json" || lang == "js"
    val closers = mapOf('{' to '}', '[' to ']', '(' to ')', '"' to '"')

    // ---- typed one character
    if (n.length == o.length + 1 && new.selection.start == oldPos + 1 && n.substring(0, oldPos) == o.substring(0, oldPos)) {
        val ch = n[oldPos]
        val next = o.getOrNull(oldPos)
        if (code && ch in "}])\"" && next == ch) return TextFieldValue(o, TextRange(oldPos + 1)) // type over the closer
        if (ch == '\n') {
            val lineStart = o.lastIndexOf('\n', oldPos - 1) + 1
            val before = o.substring(lineStart, oldPos)
            val indent = before.takeWhile { it == ' ' || it == '\t' }
            val opens = before.trimEnd().lastOrNull()?.let { it == '{' || it == '[' || it == '(' || (lang == "xml" && it == '>' && false) } == true
            val inner = "\n" + indent + if (opens) "  " else ""
            return if (opens && next != null && next in "}])") {
                TextFieldValue(o.substring(0, oldPos) + inner + "\n" + indent + o.substring(oldPos), TextRange(oldPos + inner.length))
            } else TextFieldValue(o.substring(0, oldPos) + inner + o.substring(oldPos), TextRange(oldPos + inner.length))
        }
        if (code) {
            val closer = closers[ch]
            if (closer != null) {
                if (ch == '"') {
                    val prev = o.getOrNull(oldPos - 1)
                    if (prev?.isLetterOrDigit() == true || prev == '\\' || next?.isLetterOrDigit() == true) return new
                }
                if (next == null || next.isWhitespace() || next in "}]),:") {
                    return TextFieldValue(n.substring(0, oldPos + 1) + closer + n.substring(oldPos + 1), TextRange(oldPos + 1))
                }
            }
        }
    }
    // ---- deleted one character: remove the matching closer of an empty pair
    if (code && n.length == o.length - 1 && new.selection.start == oldPos - 1 && oldPos >= 1 && oldPos < o.length) {
        val deleted = o[oldPos - 1]
        if (closers[deleted] == o[oldPos]) return TextFieldValue(n.substring(0, oldPos - 1) + n.substring(oldPos), TextRange(oldPos - 1))
    }
    return new
}

/** 1-based line and column of [pos] in [text]. */
fun lineCol(text: String, pos: Int): Pair<Int, Int> {
    val p = pos.coerceIn(0, text.length)
    var line = 1
    var last = -1
    for (i in 0 until p) if (text[i] == '\n') { line++; last = i }
    return line to (p - last)
}
