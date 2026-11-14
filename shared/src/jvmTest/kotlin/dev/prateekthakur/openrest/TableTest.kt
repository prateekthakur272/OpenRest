package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.features.shell.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import dev.prateekthakur.openrest.features.shell.OpenRestApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlin.io.path.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class TableTest {
    @Test fun dragReorderAndRowActions() = runDesktopComposeUiTest(1360, 860) {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val dir = Collections.create(createTempDirectory("tbl"), "Tbl")
        Collections.createRequest(dir, ApiRequest(name = "R", url = "http://x/a?a=1&b=2&c=3", params = listOf(KV("a", "1"), KV("b", "2"), KV("c", "3"), KV("d", "off", false))))
        val state = AppState(CoroutineScope(Dispatchers.Default))
        state.openCollection(dir)
        setContent { OpenRestApp(state) }
        state.openRequest(dir, state.collection(dir)!!.allRequests()[0]); waitForIdle()
        val tab = state.activeTab!!
        fun names() = tab.draft.params.map { it.name }
        assertEquals(listOf("a", "b", "c", "d"), names())

        onNodeWithTag("kv-rows").performTouchInput {
            down(Offset(10f, 17f)); moveBy(Offset(0f, 30f)); moveBy(Offset(0f, 30f)); moveBy(Offset(0f, 30f)); up()
        }
        waitForIdle()
        assertEquals(listOf("b", "c", "a", "d"), names())

        // screenshot for a visual check
        val img = onAllNodes(isRoot()).onFirst().captureToImage()
        val data = org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!
        File(System.getProperty("shots") ?: "/private/tmp/claude-501/-Users-ent-0554-IdeaProjects-OpenRest/e2e29356-c9b9-40cc-a6f8-baf7c71fe55c/scratchpad/shots", "16-table.png").also { it.parentFile.mkdirs() }.writeBytes(data.bytes)
    }
}
