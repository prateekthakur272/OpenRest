package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.features.shell.*

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class WindowChromeTest {
    private val out = File(System.getProperty("shots") ?: "/private/tmp/claude-501/-Users-ent-0554-IdeaProjects-OpenRest/e2e29356-c9b9-40cc-a6f8-baf7c71fe55c/scratchpad/shots").also { it.mkdirs() }
    private fun shot(t: DesktopComposeUiTest, name: String) {
        val img = t.onAllNodes(isRoot()).onFirst().captureToImage()
        File(out, "$name.png").writeBytes(org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
    }
    private fun newState(): AppState { AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap())); return AppState(CoroutineScope(Dispatchers.Default)) }

    @Test fun draggingAnEdgeResizesTheWindowAndNeverBelowTheMinimum() {
        val b = Bounds(100, 100, 1000, 700)
        assertEquals(Bounds(100, 100, 1150, 700), resizeBounds(ResizeEdge.E, b, 150, 99, 760, 480))        // right edge: only the width changes
        assertEquals(Bounds(100, 100, 1000, 640), resizeBounds(ResizeEdge.S, b, 80, -60, 760, 480))
        assertEquals(Bounds(60, 100, 1040, 700), resizeBounds(ResizeEdge.W, b, -40, 0, 760, 480))          // left edge: the right side stays put
        assertEquals(Bounds(100, 130, 1000, 670), resizeBounds(ResizeEdge.N, b, 0, 30, 760, 480))          // top edge: the bottom stays put
        assertEquals(Bounds(60, 130, 1040, 670), resizeBounds(ResizeEdge.NW, b, -40, 30, 760, 480))        // corners move two edges
        assertEquals(Bounds(100, 100, 1200, 800), resizeBounds(ResizeEdge.SE, b, 200, 100, 760, 480))
        // dragging far past the minimum stops at the minimum, with the opposite edge still fixed
        assertEquals(Bounds(340, 100, 760, 700), resizeBounds(ResizeEdge.W, b, 900, 0, 760, 480))
        assertEquals(Bounds(100, 320, 1000, 480), resizeBounds(ResizeEdge.N, b, 0, 900, 760, 480))
        assertEquals(760, resizeBounds(ResizeEdge.E, b, -900, 0, 760, 480).w)
    }

    @Test fun macOsKeepsItsNativeButtons() = runDesktopComposeUiTest(1200, 700) {
        val state = newState()
        setContent { OpenRestApp(state, WindowChrome(leftInset = 72.dp)) }
        waitForIdle()
        onNodeWithTag("window-close").assertDoesNotExist()                                // macOS draws its own buttons: ours never show
        onNodeWithTag("window-minimize").assertDoesNotExist(); onNodeWithTag("window-maximize").assertDoesNotExist()
        // the title is centred like a native one, and the header below keeps its logo and name
        val c = onNodeWithTag("title-context").getBoundsInRoot()
        assertTrue(kotlin.math.abs((c.left + c.right).value / 2 - 600f) < 4f, "title is centred, was $c")
        val name = onAllNodesWithText("OpenRest").onLast().getBoundsInRoot()
        assertTrue(name.top >= TITLE_BAR_HEIGHT, "the header's name sits below the title bar: $name")
        shot(this, "chrome-mac")
    }

    @Test fun windowsAndLinuxGetTheirOwnWindowButtonsDragAreaAndDoubleClick() = runDesktopComposeUiTest(1200, 700) {
        val state = newState()
        var minimized = 0; var toggled = 0; var closed = 0; var dragWrapped = 0; var doubleClicks = 0
        val maximized = androidx.compose.runtime.mutableStateOf(false)
        setContent {
            val chrome = WindowChrome(
                controls = { WindowControls(maximized.value, { minimized++ }, { toggled++; maximized.value = !maximized.value }, { closed++ }) },
                dragArea = { content -> dragWrapped++; content() },
                onDoubleClick = { doubleClicks++ },
            )
            OpenRestApp(state, chrome)
        }
        waitForIdle()
        assertTrue(dragWrapped > 0, "the top bar sits inside the window's drag area")
        onNodeWithTag("window-minimize").performClick(); assertEquals(1, minimized)
        onNodeWithTag("window-maximize").performClick(); waitForIdle(); assertEquals(1, toggled); assertTrue(maximized.value)
        onNodeWithTag("window-maximize").performClick(); waitForIdle(); assertFalse(maximized.value)    // the same button restores
        onNodeWithTag("window-close").performClick(); assertEquals(1, closed)
        // double-clicking the bar's empty space maximizes / restores, like a normal title bar
        onNodeWithTag("title-context").performMouseInput { doubleClick() }; waitForIdle()
        assertEquals(1, doubleClicks)
        // the buttons span the whole bar, flush with the window's right edge, and the bar is slim
        onNodeWithTag("window-close").getBoundsInRoot().let { assertEquals(TITLE_BAR_HEIGHT, it.bottom - it.top); assertTrue(it.right >= 1199.dp, "close button is at the right edge, was ${it.right}") }
        assertTrue(TITLE_BAR_HEIGHT <= 32.dp)
        shot(this, "chrome-windows")
    }

    @Test fun aThinTitleBarSitsAboveTheHeaderAndNamesWhatYouAreWorkingOn() = runDesktopComposeUiTest(1200, 700) {
        val state = newState()
        val dir = Collections.create(kotlin.io.path.createTempDirectory("tb"), "Shop")
        Collections.createRequest(dir, ApiRequest(name = "List orders", url = "http://127.0.0.1:1/"))
        state.openCollection(dir)
        setContent { OpenRestApp(state, WindowChrome(leftInset = 72.dp)) }
        waitForIdle()
        onNodeWithTag("title-context").assertTextEquals("Shop")                                  // nothing open yet: just the collection
        state.openRequest(dir, state.collection(dir)!!.allRequests().first()); waitForIdle()
        onNodeWithTag("title-context").assertTextEquals("Shop  ›  List orders")
        WorkflowStore.save(dir, Workflow("Ship")); state.reload(dir); state.openWorkflow(dir, "Ship"); waitForIdle()
        onNodeWithTag("title-context").assertTextEquals("Shop  ›  Ship")                         // follows the active tab, workflows included
        // the thin title bar is its own strip above the header, which keeps its logo and name as before
        onAllNodesWithText("OpenRest").assertCountEquals(1)
        val header = onAllNodesWithText("OpenRest").onFirst().getBoundsInRoot()
        assertTrue(header.top >= TITLE_BAR_HEIGHT && header.bottom <= TITLE_BAR_HEIGHT + 44.dp, "the name is in the 44dp header under the title bar: $header")
        val titleBounds = onNodeWithTag("title-context").getBoundsInRoot()
        assertTrue(titleBounds.bottom <= TITLE_BAR_HEIGHT, "the context is inside the title bar: $titleBounds")
        shot(this, "chrome-titlebar-above-header")
    }
}
