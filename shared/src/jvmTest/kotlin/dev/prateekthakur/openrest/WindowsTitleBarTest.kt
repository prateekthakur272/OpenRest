package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.features.shell.*

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class WindowsTitleBarTest {
    private val out = File(System.getProperty("shots") ?: "/private/tmp/claude-501/-Users-ent-0554-IdeaProjects-OpenRest/e2e29356-c9b9-40cc-a6f8-baf7c71fe55c/scratchpad/shots").also { it.mkdirs() }
    private fun shot(t: DesktopComposeUiTest, name: String) {
        val img = t.onAllNodes(isRoot()).onFirst().captureToImage()
        File(out, "$name.png").writeBytes(org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
    }
    private fun newState(): AppState { AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap())); AppStore.saveSettings(AppSettings()); return AppState(CoroutineScope(Dispatchers.Default)) }

    @Test fun shortcutsAreShownTheWayEachPlatformWritesThem() {
        assertEquals("Ctrl+N", Shortcut(Key.N).label(isMac = false)); assertEquals("⌘N", Shortcut(Key.N).label(isMac = true))
        assertEquals("Ctrl+Shift+S", Shortcut(Key.S, shift = true).label(false)); assertEquals("⇧⌘S", Shortcut(Key.S, shift = true).label(true))
        assertEquals("Ctrl+Tab", Shortcut(Key.Tab, primary = false, control = true).label(false)); assertEquals("⌃⇥", Shortcut(Key.Tab, primary = false, control = true).label(true))
        assertEquals("Ctrl+Enter", Shortcut(Key.Enter).label(false)); assertEquals("⌘↵", Shortcut(Key.Enter).label(true))
        assertEquals("Ctrl+,", Shortcut(Key.Comma).label(false)); assertEquals("Ctrl+=", Shortcut(Key.Equals).label(false)); assertEquals("Ctrl+0", Shortcut(Key.Zero).label(false))
        // every shortcut in the menus has a readable label
        fun items(l: List<MenuEntry>): List<MenuItemSpec> = l.flatMap { when (it) { is MenuItemSpec -> listOf(it); is SubMenuSpec -> items(it.items); else -> emptyList() } }
        val st = newState()
        for (mac in listOf(true, false)) {
            val shortcuts = AppMenus.build(st, mac).flatMap { items(it.items) }.mapNotNull { it.shortcut }
            assertTrue(shortcuts.isNotEmpty())
            shortcuts.forEach { assertFalse(it.label(mac).contains("?"), "unlabelled shortcut ${it.key}") }
        }
    }

    @Test fun windowsAndLinuxHaveTheMenusInsideTheTitleBar() = runDesktopComposeUiTest(1280, 760) {
        val state = newState()
        var quit = 0
        setContent { OpenRestApp(state, WindowChrome(inlineMenus = true, onQuit = { quit++ }, controls = { WindowControls(false, {}, {}, {}) })) }
        waitForIdle()
        listOf("File", "View", "Request", "Workflow", "Window", "Help").forEach { onNodeWithTag("menu-$it").assertExists() }
        onNodeWithTag("menuitem-New Request").assertDoesNotExist()                          // closed until clicked
        shot(this, "winbar-closed")

        // open File: items, shortcuts, enabled state
        onNodeWithTag("menu-File").performClick(); waitForIdle()
        onNodeWithTag("menuitem-New Request").assertExists()
        onNodeWithText("Ctrl+N").assertExists(); onNodeWithText("Ctrl+Shift+S").assertExists()
        onNodeWithTag("menuitem-Save").assertIsNotEnabled()                                  // nothing to save
        onNodeWithTag("menuitem-Preferences…").assertExists(); onNodeWithTag("menuitem-Exit").assertExists()   // Windows keeps these in File
        shot(this, "winbar-file")

        // moving to another title opens that menu instead, like a native bar
        onNodeWithTag("menu-View").performMouseInput { moveTo(center) }; waitForIdle()
        onNodeWithTag("menuitem-New Request").assertDoesNotExist()
        onNodeWithTag("menuitem-Show Sidebar").assertExists()
        onNodeWithTag("menuitem-Dark").assertExists()                                         // the Theme submenu's items are listed under it
        shot(this, "winbar-view")

        // items run their action and close the menu; check items reflect and change state
        assertTrue(state.sidebarVisible)
        onNodeWithTag("menuitem-Show Sidebar").performClick(); waitForIdle()
        assertFalse(state.sidebarVisible)
        onNodeWithTag("menuitem-Show Sidebar").assertDoesNotExist()
        onNodeWithTag("menu-View").performClick(); waitForIdle()
        onNodeWithTag("menuitem-Light").performClick(); waitForIdle()
        assertEquals(ThemeMode.Light, state.settings.theme)

        onNodeWithTag("menu-File").performClick(); waitForIdle()
        onNodeWithTag("menuitem-Preferences…").performClick(); waitForIdle()
        assertEquals(Modal.Settings, state.modal)
        state.modal = null; waitForIdle()
        onNodeWithTag("menu-File").performClick(); waitForIdle()
        onNodeWithTag("menuitem-Exit").performClick(); waitForIdle()
        assertEquals(1, quit)

        // clicking anywhere outside the open menu closes it, and Esc does too
        onNodeWithTag("menu-File").performClick(); waitForIdle()
        onNodeWithTag("menuitem-New Request").assertExists()
        onNodeWithText("jump to any request", substring = true).performClick(); waitForIdle()
        assertNull(state.titleMenu.open); onNodeWithTag("menuitem-New Request").assertDoesNotExist()
        onNodeWithTag("menu-File").performClick(); waitForIdle()
        assertTrue(closeTitleMenu(state)); waitForIdle()                                      // what Esc does (handleKey calls it)
        assertNull(state.titleMenu.open); onNodeWithTag("menuitem-New Request").assertDoesNotExist()
        assertFalse(closeTitleMenu(state))                                                    // with nothing open, Esc is left to whoever wants it
        // a click on the open menu's own title closes it again
        onNodeWithTag("menu-File").performClick(); waitForIdle(); onNodeWithTag("menu-File").performClick(); waitForIdle()
        assertNull(state.titleMenu.open)

        // Help ▸ About is here too (macOS has it in the application menu)
        onNodeWithTag("menu-Help").performClick(); waitForIdle()
        onNodeWithTag("menuitem-About OpenRest").assertExists()
    }

    @Test fun macOsKeepsTheNativeMenuBarAndHasNoMenusInTheTitleBar() = runDesktopComposeUiTest(1280, 760) {
        val state = newState()
        setContent { OpenRestApp(state, WindowChrome(leftInset = 72.dp)) }
        waitForIdle()
        listOf("File", "View", "Request", "Workflow", "Window", "Help").forEach { onNodeWithTag("menu-$it").assertDoesNotExist() }
    }

    @Test fun theMenusAndTheContextTitleShareANarrowBar() = runDesktopComposeUiTest(900, 600) {
        val state = newState()
        setContent { OpenRestApp(state, WindowChrome(inlineMenus = true, controls = { WindowControls(false, {}, {}, {}) })) }
        waitForIdle()
        onNodeWithTag("menu-Help").assertExists()
        onNodeWithTag("title-context").assertDoesNotExist()                                  // no room for the centred title next to the menus
        shot(this, "winbar-narrow")
    }
}
