package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.features.shell.*
import dev.prateekthakur.openrest.platform.*

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DeveloperLinkTest {
    @Test fun githubButtonSitsInTheStatusBarAndPointsAtTheDeveloperProfile() = runDesktopComposeUiTest(1360, 860) {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val state = AppState(CoroutineScope(Dispatchers.Default)); state.updateSettings(AppSettings())
        setContent { OpenRestApp(state) }; waitForIdle()
        onNodeWithTag("developer-github").assertExists().assertHasClickAction()
        assertTrue(AppInfo.DEVELOPER_URL.startsWith("https://github.com/") && AppInfo.REPO_URL.startsWith(AppInfo.DEVELOPER_URL + "/"))
        val help = AppMenus.build(state, isMac = true, quit = {}, minimize = {}).first { it.label == "Help" }
        (help.items.filterIsInstance<MenuItemSpec>().first { it.label == "Developer…" }).action(); waitForIdle()
        assertEquals(Modal.Developer, state.modal); state.modal = null; waitForIdle()
        onNodeWithTag("developer-github").performClick(); waitForIdle()                    // the button opens the dialog, not the browser
        assertEquals(Modal.Developer, state.modal)
        onNodeWithText(AppInfo.DEVELOPER_NAME).assertExists()
        onNodeWithText(AppInfo.DEVELOPER_URL).assertExists(); onNodeWithText(AppInfo.REPO_URL).assertExists()
        onNodeWithTag("developer-repo-open").assertHasClickAction(); onNodeWithTag("developer-profile-open").assertHasClickAction()
        onNodeWithTag("developer-repo-copy").performClick(); waitForIdle()
        assertEquals(AppInfo.REPO_URL, Platform.paste())                                    // copy puts the link on the clipboard
        val img = onAllNodes(isRoot()).onFirst().captureToImage()
        File(System.getProperty("shots") ?: "build/shots").also { it.mkdirs() }.resolve("developer-dialog.png").writeBytes(org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
    }
}
