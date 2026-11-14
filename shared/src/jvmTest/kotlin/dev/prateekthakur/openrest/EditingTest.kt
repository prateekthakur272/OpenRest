package dev.prateekthakur.openrest

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.prateekthakur.openrest.ui.lineCol
import dev.prateekthakur.openrest.ui.smartEdit
import kotlin.test.*

class EditingTest {
    private fun tv(s: String, at: Int = s.length) = TextFieldValue(s, TextRange(at))
    /** simulate typing [c] at the cursor of [old] */
    private fun type(old: TextFieldValue, c: Char, lang: String = "json"): TextFieldValue {
        val p = old.selection.start
        return smartEdit(old, TextFieldValue(old.text.substring(0, p) + c + old.text.substring(p), TextRange(p + 1)), lang)
    }
    private fun backspace(old: TextFieldValue, lang: String = "json"): TextFieldValue {
        val p = old.selection.start
        return smartEdit(old, TextFieldValue(old.text.removeRange(p - 1, p), TextRange(p - 1)), lang)
    }

    @Test fun autoCloseBrace() { val r = type(tv(""), '{'); assertEquals("{}", r.text); assertEquals(1, r.selection.start) }
    @Test fun autoCloseQuoteAndTypeOver() {
        var r = type(tv("{\"a\": ", 6), '"'); assertEquals("{\"a\": \"\"}".replace("}", "") + "", r.text.substring(0, 8))
        r = type(r, 'x'); r = type(r, '"')
        assertEquals("{\"a\": \"x\"", r.text.take(9)); assertEquals(9, r.selection.start)
    }
    @Test fun noAutoCloseBeforeWord() { assertEquals("{a", type(tv("a", 0), '{').text) }
    @Test fun enterIndentsInsideBraces() {
        val old = tv("{}", 1)
        val r = smartEdit(old, TextFieldValue("{\n}", TextRange(2)), "json")
        assertEquals("{\n  \n}", r.text); assertEquals(4, r.selection.start)
    }
    @Test fun enterKeepsIndent() {
        val old = tv("  a", 3)
        val r = smartEdit(old, TextFieldValue("  a\n", TextRange(4)), "json")
        assertEquals("  a\n  ", r.text)
    }
    @Test fun pairedBackspace() { assertEquals("", backspace(tv("{}", 1)).text) }
    @Test fun plainTextUntouched() { assertEquals("{", type(tv(""), '{', "text").text) }
    @Test fun lineColumn() { assertEquals(1 to 1, lineCol("ab\ncd", 0)); assertEquals(2 to 2, lineCol("ab\ncd", 4)) }
}
