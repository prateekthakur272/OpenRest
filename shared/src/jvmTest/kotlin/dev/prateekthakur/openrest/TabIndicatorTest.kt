package dev.prateekthakur.openrest

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.features.shell.OpenRestApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.io.path.createTempDirectory
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
/** The selected request tab and the selected Params / Body tab must show the accent underline. */
class TabIndicatorTest {
    @Test fun selectedTabsShowAccentUnderline() = runDesktopComposeUiTest(1100, 600) {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val dir = Collections.create(createTempDirectory("ti"), "P")
        listOf("one", "two").forEach { Collections.createRequest(dir, ApiRequest(name = it, url = "http://127.0.0.1:1/$it")) }
        val state = AppState(CoroutineScope(Dispatchers.Default))
        state.openCollection(dir)
        state.collection(dir)!!.allRequests().forEach { state.openRequest(dir, it) }
        setContent { OpenRestApp(state) }
        waitForIdle(); Thread.sleep(500); waitForIdle()
        val img = onAllNodes(isRoot()).onFirst().captureToImage()
        val bmp = img.asSkiaBitmap()
        fun accentIn(x: IntRange, y: IntRange) = y.any { yy -> x.any { xx -> bmp.getColor(xx, yy) == 0xFFD97706.toInt() } }
        assertTrue(accentIn(350..440, 100..112), "no underline under the selected request tab")
        assertTrue(accentIn(262..312, 232..250), "no underline under the selected Params tab")
    }
}
