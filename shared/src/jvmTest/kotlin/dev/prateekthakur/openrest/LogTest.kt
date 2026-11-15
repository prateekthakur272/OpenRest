package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.core.logging.Log
import dev.prateekthakur.openrest.core.logging.LogLevel
import kotlin.io.path.createTempFile
import kotlin.io.path.readText
import kotlin.test.*

class LogTest {
    @Test fun writesLevelTagMessageAndStackTrace() {
        val file = createTempFile("log", ".txt")
        Log.writeTo(file)
        Log.warning("storage", "Cannot read settings.json", IllegalStateException("boom"))
        val text = file.readText()
        assertTrue("WARNING [storage] Cannot read settings.json" in text, text)
        assertTrue("IllegalStateException: boom" in text, text)
    }

    @Test fun belowMinimumLevelIsSkipped() {
        val file = createTempFile("log", ".txt")
        Log.writeTo(file)
        Log.debug("x", "hidden")
        assertFalse(file.readText().contains("hidden"))
        assertEquals(LogLevel.INFO, Log.minimumLevel)
    }

    @Test fun orElseReturnsFallbackAndLogs() {
        val file = createTempFile("log", ".txt")
        Log.writeTo(file)
        val v = Log.orElse("t", "Cannot parse", -1) { error("bad") }
        assertEquals(-1, v)
        assertTrue("Cannot parse: bad" in file.readText())
    }
}
