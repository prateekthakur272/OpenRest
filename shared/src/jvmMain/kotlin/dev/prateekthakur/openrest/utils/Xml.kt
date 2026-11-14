package dev.prateekthakur.openrest.utils

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*

fun prettyXml(s: String): String {
    val sb = StringBuilder()
    var depth = 0
    val tokens = Regex("<[^>]+>|[^<]+").findAll(s.trim().replace(Regex(">\\s+<"), "><")).map { it.value }.toList()
    for (t in tokens) {
        if (t.startsWith("</")) { depth = maxOf(0, depth - 1); sb.append("  ".repeat(depth)).append(t).append('\n') }
        else if (t.startsWith("<") && !t.startsWith("<?") && !t.startsWith("<!") && !t.endsWith("/>")) { sb.append("  ".repeat(depth)).append(t).append('\n'); depth++ }
        else sb.append("  ".repeat(depth)).append(t.trim()).append('\n')
    }
    // join "<a>text</a>" onto one line
    return sb.toString().replace(Regex("(<[^/!?][^>]*[^/]>)\\n\\s*([^<\\n]+)\\n\\s*(</[^>]+>)"), "$1$2$3").trimEnd()
}
