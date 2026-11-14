package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.features.shell.*

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import dev.prateekthakur.openrest.features.shell.OpenRestApp
import dev.prateekthakur.openrest.components.renderHtml
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class PreviewTest {
    private val out = File("/private/tmp/claude-501/-Users-ent-0554-IdeaProjects-OpenRest/e2e29356-c9b9-40cc-a6f8-baf7c71fe55c/scratchpad/shots")

    @Test fun htmlRenderer() {
        val img = renderHtml("<html><body><h1>Hello</h1><p>World <b>bold</b></p><table border=1><tr><td>a</td><td>b</td></tr></table></body></html>", 600)
        assertNotNull(img); assertTrue(img.height in 40..400, "height ${img.height}")
    }

    @Test fun previews() = runDesktopComposeUiTest(1360, 860) {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val png = ByteArrayOutputStream().also { o ->
            val bi = java.awt.image.BufferedImage(320, 200, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val g = bi.createGraphics(); g.paint = java.awt.GradientPaint(0f, 0f, java.awt.Color(255, 140, 0), 320f, 200f, java.awt.Color(40, 90, 200)); g.fillRect(0, 0, 320, 200); g.dispose()
            ImageIO.write(bi, "png", o)
        }.toByteArray()
        val srv = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        fun route(path: String, ct: String, body: ByteArray) = srv.createContext(path) { ex -> ex.responseHeaders.add("Content-Type", ct); ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.write(body); ex.close() }
        route("/html", "text/html; charset=utf-8", "<html><body style='font-family:sans-serif'><h1>Welcome</h1><p>This is <b>bold</b> and <i>italic</i>.</p><ul><li>One</li><li>Two</li></ul><table border='1'><tr><th>Name</th><th>Age</th></tr><tr><td>Ada</td><td>36</td></tr></table></body></html>".toByteArray())
        route("/json", "application/json", """{"user":{"id":7,"name":"Ada","tags":["a","b"],"address":{"city":"London","zip":null}},"ok":true,"items":[{"id":1},{"id":2}]}""".toByteArray())
        route("/img", "image/png", png)
        route("/pdf", "application/pdf", "%PDF-1.4\n\u0000\u0000binary".toByteArray())
        srv.start()
        try {
            val dir = Collections.create(createTempDirectory("pv"), "P")
            listOf("html", "json", "img", "pdf").forEach { Collections.createRequest(dir, ApiRequest(name = it, url = "http://127.0.0.1:${srv.address.port}/$it")) }
            val state = AppState(CoroutineScope(Dispatchers.Default))
            state.openCollection(dir)
            setContent { OpenRestApp(state) }
            fun shot(name: String) {
                val img = onAllNodes(isRoot()).onFirst().captureToImage()
                out.mkdirs(); File(out, "$name.png").writeBytes(org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
            }
            fun run(req: String, fmt: String, prev: Boolean?, shotName: String) {
                state.openRequest(dir, state.collection(dir)!!.allRequests().first { it.name == req }); waitForIdle()
                val t = state.activeTab!!
                state.send(t); waitUntil(timeoutMillis = 15000) { t.result != null && !t.sending }; waitForIdle()
                t.resView = fmt; t.resPreview = prev; state.splitFraction = 0.2f; waitForIdle(); Thread.sleep(600); waitForIdle()
                shot(shotName)
            }
            run("html", "HTML", true, "17-html-preview")
            run("json", "JSON", true, "18-json-tree")
            run("json", "Base64", false, "18b-json-b64")
            run("img", "Auto", null, "19-image-preview")
            run("pdf", "Auto", null, "20-pdf-preview")
            run("pdf", "Hex", false, "21-pdf-hex")
            run("html", "HTML", false, "22-html-source")
            onAllNodesWithText("HTML").onFirst().performClick(); waitForIdle(); Thread.sleep(300); waitForIdle()
            val roots = onAllNodes(isRoot()).fetchSemanticsNodes()
            println("ROOTS " + roots.map { it.size })
            val menuImg = onAllNodes(isRoot()).let { r -> if (roots.size > 1) r[1] else r[0] }.captureToImage()
            out.mkdirs(); File(out, "23-format-menu.png").writeBytes(org.jetbrains.skia.Image.makeFromBitmap(menuImg.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
            shot("23b-format-menu-full")
        } finally { srv.stop(0) }
    }
}
