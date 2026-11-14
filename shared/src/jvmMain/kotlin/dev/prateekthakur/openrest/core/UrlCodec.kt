package dev.prateekthakur.openrest.core

import java.net.URLDecoder
import java.net.URLEncoder

/** encodeURIComponent / decodeURIComponent equivalents that leave `{{variables}}` alone. */
object UrlCodec {
    private val varRe = Regex("\\{\\{[^{}]*}}")
    private val pctRe = Regex("%[0-9A-Fa-f]{2}")

    fun looksEncoded(s: String) = pctRe.containsMatchIn(s)

    fun decode(s: String): String = try {
        // '+' stays '+' (decodeURIComponent semantics); only %XX sequences are decoded
        URLDecoder.decode(s.replace("+", "%2B"), Charsets.UTF_8)
    } catch (_: Exception) { s }

    fun encode(s: String): String {
        val sb = StringBuilder()
        var last = 0
        for (m in varRe.findAll(s)) {
            sb.append(encodePart(s.substring(last, m.range.first))).append(m.value)
            last = m.range.last + 1
        }
        return sb.append(encodePart(s.substring(last))).toString()
    }

    private fun encodePart(s: String) = URLEncoder.encode(s, Charsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~")
}
