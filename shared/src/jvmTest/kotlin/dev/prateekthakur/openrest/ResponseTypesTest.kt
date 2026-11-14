package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.features.shell.*

import androidx.compose.ui.test.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.io.path.*
import kotlin.test.Test

/** The format menu only offers what the response actually is. */
@OptIn(ExperimentalTestApi::class)
class ResponseTypesTest {
    private val all = listOf("JSON", "HTML", "XML", "JavaScript", "Raw", "Hex", "Base64")

    @Test fun formatMenuMatchesTheResponse() = runDesktopComposeUiTest(1360, 860) {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val srv = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        fun route(path: String, type: String, body: ByteArray, vararg extra: Pair<String, String>) = srv.createContext(path) { ex ->
            ex.responseHeaders.add("Content-Type", type); extra.forEach { ex.responseHeaders.add(it.first, it.second) }
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.write(body); ex.close()
        }
        route("/json", "application/json", """{"id":1}""".toByteArray())
        route("/octet", "application/octet-stream", byteArrayOf(1, 2, 3, 65, 66, 67))
        route("/zip", "application/zip", "PK-zip-bytes-here".toByteArray(), "Content-Disposition" to "attachment; filename=a.zip")
        route("/text", "text/plain", "hello there".toByteArray())
        route("/html", "text/html", "<html><body>hi</body></html>".toByteArray())
        route("/nul", "text/plain", byteArrayOf(104, 105, 0, 0, 1))
        srv.start()
        try {
            val dir = Collections.create(createTempDirectory("rt"), "Demo")
            listOf("json", "octet", "zip", "text", "html", "nul").forEach { Collections.createRequest(dir, ApiRequest(name = it, url = "http://127.0.0.1:${srv.address.port}/$it")) }
            val state = AppState(CoroutineScope(Dispatchers.Default)); state.updateSettings(AppSettings()); state.openCollection(dir)
            setContent { OpenRestApp(state) }; waitForIdle()

            fun check(name: String, current: String, offered: List<String>) {
                state.openRequest(dir, state.collection(dir)!!.allRequests().first { it.name == name }); waitForIdle()
                state.send(state.activeTab!!)
                waitUntil(timeoutMillis = 15000) { state.activeTab?.result?.response != null }; waitForIdle()
                onAllNodesWithText(current, useUnmergedTree = true).onFirst().performClick(); waitForIdle()        // open the format menu
                offered.forEach { onAllNodesWithText(it, useUnmergedTree = true).onFirst().assertExists("$name should offer $it") }
                (all - offered.toSet() - current).forEach { onAllNodesWithText(it, useUnmergedTree = true).assertCountEquals(0) }
                state.modal = null; onAllNodesWithText("Hex", useUnmergedTree = true).let { } // menu closes on the next click below
                runCatching { onAllNodesWithText(current, useUnmergedTree = true).onFirst().performClick() }
                waitForIdle()
            }
            check("json", "JSON", listOf("JSON", "Raw", "Hex", "Base64"))
            check("octet", "Hex", listOf("Raw", "Hex", "Base64"))
            check("zip", "Hex", listOf("Raw", "Hex", "Base64"))
            state.activeTab!!.resView = "Raw"; state.activeTab!!.resPreview = false; waitForIdle()                                 // Raw on a file shows the bytes as text
            onAllNodesWithText("PK-zip-bytes-here", substring = true, useUnmergedTree = true).onFirst().assertExists()
            check("nul", "Hex", listOf("Raw", "Hex", "Base64"))
            check("text", "Raw", listOf("Raw", "Hex", "Base64"))
            check("html", "HTML", listOf("HTML", "Raw", "Hex", "Base64"))
        } finally { srv.stop(0) }
    }
}
