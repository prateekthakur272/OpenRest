package dev.prateekthakur.openrest

import androidx.compose.ui.test.*
import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.*
import dev.prateekthakur.openrest.ui.OpenRestApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.io.path.*
import androidx.compose.ui.semantics.getOrNull
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class FieldSizeTest {
    private fun widths(t: DesktopComposeUiTest, label: String) {
        t.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().drop(2).let { ns -> val hs = ns.map { it.size.height }.filter { h -> h < 60 }.toSet(); kotlin.test.assertTrue(hs.size == 1 || label == "nodesettings", "$label: field heights differ $hs") }
        val nodes = t.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        println("PROBE $label: " + nodes.joinToString(" | ") { n -> "${n.size.width}x${n.size.height}[${(n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.EditableText)?.text ?: "").take(8)}]" })
    }

    @Test fun controlsShareHeight() = runDesktopComposeUiTest(1360, 860) {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val dir = Collections.create(createTempDirectory("ch"), "C")
        val srv = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/") { ex -> val b = "{\"a\":1}".toByteArray(); ex.responseHeaders.add("Content-Type", "application/json"); ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.write(b); ex.close() }
        srv.start()
        try {
        Collections.createRequest(dir, ApiRequest(name = "R", url = "http://127.0.0.1:${srv.address.port}/"))
        val state = AppState(CoroutineScope(Dispatchers.Default))
        state.openCollection(dir)
        setContent { OpenRestApp(state) }
        state.openRequest(dir, state.collection(dir)!!.allRequests()[0]); waitForIdle()
        state.send(state.activeTab!!); waitUntil(timeoutMillis = 15000) { state.activeTab!!.result != null }; waitForIdle()
        fun h(n: androidx.compose.ui.test.SemanticsNodeInteraction) = n.fetchSemanticsNode().size.height
        // response toolbar: "Pretty" dropdown, Filter field, Find field
        val dd = h(onNodeWithText("JSON"))
        val filter = h(onAllNodes(hasSetTextAction() and hasText("Filter", substring = true)).onFirst())
        val find = h(onNode(hasSetTextAction() and hasText("Find", substring = true)))
        println("HEIGHTS dropdown-text=$dd filter=$filter find=$find")
        kotlin.test.assertEquals(filter, find, "filter/find fields differ")
        kotlin.test.assertEquals(filter, dd, "dropdown and field heights differ")
        val send = h(onNodeWithText("Copy"))
        kotlin.test.assertEquals(filter, send, "button and field heights differ")
        } finally { srv.stop(0) }
    }

    @Test fun probe() = runDesktopComposeUiTest(1360, 860) {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val dir = Collections.create(createTempDirectory("wp"), "W")
        Collections.createRequest(dir, ApiRequest(name = "R", url = "http://x", auth = Auth(AuthMode.Bearer, token = "")))
        val state = AppState(CoroutineScope(Dispatchers.Default))
        state.openCollection(dir)
        setContent { OpenRestApp(state) }
        state.openRequest(dir, state.collection(dir)!!.allRequests()[0]); waitForIdle()
        val tab = state.activeTab!!
        tab.reqTab = "Auth"; waitForIdle(); widths(this, "auth-bearer-empty")
        state.update(tab) { it.copy(auth = it.auth.copy(token = "abcdefghijklmnopqrstuvwxyz")) }; waitForIdle(); widths(this, "auth-bearer-filled")
        state.update(tab) { it.copy(auth = Auth(AuthMode.Basic)) }; waitForIdle(); widths(this, "auth-basic-empty")
        tab.reqTab = "Params"; waitForIdle(); widths(this, "params-empty")
        state.update(tab) { it.copy(params = listOf(KV("a", "1"))) }; waitForIdle(); widths(this, "params-1")
        tab.reqTab = "Headers"; waitForIdle(); widths(this, "headers-empty")
        tab.reqTab = "Settings"; waitForIdle(); widths(this, "settings")
        state.modal = Modal.Settings; waitForIdle(); widths(this, "prefs")
        state.modal = Modal.Envs(dir); waitForIdle(); widths(this, "envs-none")
        state.modal = Modal.NewRequest(dir, dir); waitForIdle(); widths(this, "newreq")
        state.modal = Modal.NewCollection; waitForIdle(); widths(this, "newcol")
        state.modal = Modal.Import; waitForIdle(); widths(this, "import")
        state.modal = Modal.NodeSettings(dir, dir); waitForIdle(); widths(this, "nodesettings")
    }
}
