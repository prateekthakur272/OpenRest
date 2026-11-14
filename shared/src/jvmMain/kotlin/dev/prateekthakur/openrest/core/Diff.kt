package dev.prateekthakur.openrest.core

object Diff {
    data class Line(val op: Char, val text: String)

    /** Line diff (old -> new). Returns null when inputs are too large to diff cheaply. */
    fun lines(a: List<String>, b: List<String>): List<Line>? {
        var s = 0
        while (s < a.size && s < b.size && a[s] == b[s]) s++
        var ea = a.size; var eb = b.size
        while (ea > s && eb > s && a[ea - 1] == b[eb - 1]) { ea--; eb-- }
        val x = a.subList(s, ea); val y = b.subList(s, eb)
        if (x.size > 2500 || y.size > 2500) return null
        val n = x.size; val m = y.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) dp[i][j] = if (x[i] == y[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        val out = ArrayList<Line>()
        for (i in 0 until s) out += Line(' ', a[i])
        var i = 0; var j = 0
        while (i < n && j < m) {
            if (x[i] == y[j]) { out += Line(' ', x[i]); i++; j++ }
            else if (dp[i + 1][j] >= dp[i][j + 1]) { out += Line('-', x[i]); i++ }
            else { out += Line('+', y[j]); j++ }
        }
        while (i < n) out += Line('-', x[i++])
        while (j < m) out += Line('+', y[j++])
        for (k in ea until a.size) out += Line(' ', a[k])
        return out
    }
}
