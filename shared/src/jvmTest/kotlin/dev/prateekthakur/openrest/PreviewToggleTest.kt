package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.features.shell.*

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PreviewToggleTest {
    @Test fun previewIsItsOwnControlNextToTheFormatMenu() = runDesktopComposeUiTest(1360, 860) {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val srv = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/") { ex ->
            val b = """{"id":1,"name":"Ada","tags":["a","b"]}""".toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json"); ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.write(b); ex.close()
        }
        srv.start()
        try {
            val dir = Collections.create(createTempDirectory("pt"), "Demo")
            Collections.createRequest(dir, ApiRequest(name = "Get", url = "http://127.0.0.1:${srv.address.port}/x"))
            val state = AppState(CoroutineScope(Dispatchers.Default)); state.updateSettings(AppSettings())
            state.openCollection(dir)
            setContent { OpenRestApp(state) }; waitForIdle()
            state.openRequest(dir, state.collection(dir)!!.allRequests().single()); waitForIdle()
            state.send(state.activeTab!!)
            waitUntil(timeoutMillis = 15000) { state.activeTab?.result?.response != null }; waitForIdle()
            val tab = state.activeTab!!
            onNodeWithTag("preview-toggle").assertExists()
            val before = tab.resPreview
            onNodeWithTag("preview-toggle").performClick(); waitForIdle()
            assertEquals(!(before ?: false), tab.resPreview ?: false)                       // one click flips it, no menu involved
            val on = tab.resPreview ?: false
            onNodeWithText("JSON").performClick(); waitForIdle()                           // the format menu now only lists formats
            onNodeWithText("Preview", useUnmergedTree = true).assertExists()               // (the toggle itself) ...
            assertEquals(1, onAllNodesWithText("Preview", useUnmergedTree = true).fetchSemanticsNodes().size)   // ...and no second "Preview" row inside the menu
            onAllNodesWithText("JSON", useUnmergedTree = true).onLast().performClick(); waitForIdle()   // choose the format again from the menu
            assertEquals(on, tab.resPreview ?: false)                                       // picking a format leaves the toggle alone
            File(System.getProperty("shots") ?: "build/shots").also { it.mkdirs() }.resolve("preview-toggle.png").writeBytes(
                org.jetbrains.skia.Image.makeFromBitmap(onAllNodes(isRoot()).onFirst().captureToImage().asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
        } finally { srv.stop(0) }
    }
}
