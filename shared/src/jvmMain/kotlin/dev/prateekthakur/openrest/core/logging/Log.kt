package dev.prateekthakur.openrest.core.logging

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class LogLevel { DEBUG, INFO, WARNING, ERROR }

/**
 * The one place the app records what happened. Every line carries the level, the feature that logged it and the
 * operation, e.g. `WARNING [storage] Cannot read settings.json: ...`, and failures keep their stack trace.
 *
 * Never pass passwords, tokens, secrets or request / response bodies: log identifiers and file names only.
 */
object Log {
    private const val MAX_LOG_BYTES = 1_000_000L
    private val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    @Volatile var minimumLevel: LogLevel = LogLevel.INFO
    @Volatile private var file: Path? = null

    /** Also writes to [path] (kept below 1 MB: the previous file is moved to `<name>.old` when it fills up). */
    fun writeTo(path: Path) { file = path }

    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message, null)
    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message, null)
    fun warning(tag: String, message: String, cause: Throwable? = null) = log(LogLevel.WARNING, tag, message, cause)
    fun error(tag: String, message: String, cause: Throwable? = null) = log(LogLevel.ERROR, tag, message, cause)

    /** Runs [block]; on failure logs [message] at WARNING and returns [fallback] (for work that is allowed to fail, such as optional files). */
    inline fun <T> orElse(tag: String, message: String, fallback: T, block: () -> T): T =
        try { block() } catch (e: Exception) { warning(tag, "$message: ${e.message ?: e::class.simpleName}", e); fallback }

    fun log(level: LogLevel, tag: String, message: String, cause: Throwable?) {
        if (level < minimumLevel) return
        val line = buildString {
            append(LocalDateTime.now().format(timestamp)).append(' ').append(level.name).append(" [").append(tag).append("] ").append(message)
            if (cause != null) append('\n').append(cause.stackTraceToString().trimEnd())
        }
        System.err.println(line)
        appendToFile(line)
    }

    private fun appendToFile(line: String) {
        val target = file ?: return
        try {
            if (Files.exists(target) && Files.size(target) > MAX_LOG_BYTES) Files.move(target, target.resolveSibling(target.fileName.toString() + ".old"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            Files.writeString(target, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        } catch (_: Exception) { /* the log itself must never fail the app, and there is nowhere else to report it */ }
    }
}
