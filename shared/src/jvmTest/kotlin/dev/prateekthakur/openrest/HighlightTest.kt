package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.codegen.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.theme.*

import kotlin.test.*

class HighlightTest {
    private val p = DarkPal
    private fun colors(code: String, lang: String) =
        highlight(code, lang, p).spanStyles.map { code.substring(it.start, it.end) to it.item.color }

    @Test fun python() {
        val c = colors("import requests\nurl = \"https://x.io\" # note\nn = 5", "py")
        assertTrue(c.any { it.first == "import" && it.second == p.syntaxKeyword })
        assertTrue(c.any { it.first == "\"https://x.io\"" && it.second == p.syntaxString })
        assertTrue(c.any { it.first == "# note" && it.second == p.syntaxComment })
        assertTrue(c.any { it.first == "5" && it.second == p.syntaxNumber })
    }
    @Test fun shell() {
        val c = colors("curl -X POST 'https://x.io/a#b' \\\n  -H 'A: b' # c", "sh")
        assertTrue(c.any { it.first == "curl" && it.second == p.syntaxKeyword })
        assertTrue(c.any { it.first == "-X" && it.second == p.syntaxKey })
        assertTrue(c.any { it.first == "'https://x.io/a#b'" && it.second == p.syntaxString })   // '#' inside a string is not a comment
        assertTrue(c.any { it.first == "# c" && it.second == p.syntaxComment })
    }
    @Test fun blockCommentAndTypes() {
        val c = colors("/* hi */ val x: String = \"a\"", "kt")
        assertTrue(c.any { it.first == "/* hi */" && it.second == p.syntaxComment })
        assertTrue(c.any { it.first == "val" && it.second == p.syntaxKeyword })
        assertTrue(c.any { it.first == "String" && it.second == p.syntaxKey })
    }
    @Test fun everyGeneratorLanguageHasAGrammar() {
        val req = PreparedRequest("POST", "https://x.io/a", listOf("A" to "b"), Body.Raw("{\"a\":1}", "application/json"))
        CodeGen.languages.forEach { name ->
            val code = CodeGen.generate(name, req)
            assertTrue(colors(code, CodeGen.langId(name)).isNotEmpty(), "no highlighting for $name")
        }
    }
}
