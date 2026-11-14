package dev.prateekthakur.openrest.utils

/** Human-readable byte count: `512 B`, `1.5 KB`, `2.25 MB`. */
fun formatSize(bytes: Int): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.2f MB".format(bytes / 1024.0 / 1024.0)
}

/** Compact age of a timestamp: `now`, `12s`, `5m`, `3h`, `2d`. */
fun timeAgo(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val s = (nowMs - timestampMs) / 1000
    return when { s < 5 -> "now"; s < 60 -> "${s}s"; s < 3600 -> "${s / 60}m"; s < 86400 -> "${s / 3600}h"; else -> "${s / 86400}d" }
}
