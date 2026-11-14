package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.http.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.features.shell.*

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.io.path.createTempDirectory
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class MenuTest {
    private fun state(): AppState {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))        // a new AppState reloads what earlier tests saved
        AppStore.saveSettings(AppSettings())
        return AppState(CoroutineScope(Dispatchers.Default))
    }
    private fun flat(m: List<MenuEntry>): List<MenuItemSpec> = m.flatMap { when (it) { is MenuItemSpec -> listOf(it); is SubMenuSpec -> flat(it.items); else -> emptyList() } }
    private fun List<MenuSpec>.item(label: String): MenuItemSpec = flatMap { flat(it.items) }.first { it.label == label }
    private fun List<MenuSpec>.menu(name: String) = first { it.label == name }
    private fun List<MenuSpec>.labels() = flatMap { flat(it.items) }.map { it.label }

    private fun withCollection(): Pair<AppState, java.nio.file.Path> {
        val s = state()
        val dir = Collections.create(createTempDirectory("menu"), "M")
        Collections.createRequest(dir, ApiRequest(name = "R", url = "http://127.0.0.1:1/"))
        s.openCollection(dir)
        return s to dir
    }

    @Test fun macAndWindowsGetTheirOwnMenus() {
        val s = state()
        val mac = AppMenus.build(s, isMac = true)
        val win = AppMenus.build(s, isMac = false)
        assertEquals(listOf("File", "View", "Request", "Workflow", "Window", "Help"), mac.map { it.label })
        assertEquals(mac.map { it.label }, win.map { it.label })
        // macOS keeps About / Preferences / Quit in the system's application menu, Windows and Linux in File and Help
        assertFalse("Preferences…" in mac.labels() || "Exit" in mac.labels() || "About OpenRest" in mac.labels())
        assertTrue("Preferences…" in win.labels() && "Exit" in win.labels() && "About OpenRest" in win.labels())
        assertEquals("Exit", win.menu("File").items.filterIsInstance<MenuItemSpec>().last().label)
        assertEquals("About OpenRest", win.menu("Help").items.filterIsInstance<MenuItemSpec>().last().label)
        // ⌘ on a Mac, Ctrl elsewhere: the same logical shortcut, turned into the right modifier for the platform
        val newRequest = mac.item("New Request").shortcut!!
        assertEquals(Shortcut(Key.N), newRequest); assertEquals(newRequest, win.item("New Request").shortcut)
        assertEquals(androidx.compose.ui.input.key.KeyShortcut(Key.N, meta = true), newRequest.toKeyShortcut(isMac = true))
        assertEquals(androidx.compose.ui.input.key.KeyShortcut(Key.N, ctrl = true), newRequest.toKeyShortcut(isMac = false))
        assertEquals(androidx.compose.ui.input.key.KeyShortcut(Key.S, meta = true, shift = true), mac.item("Save All").shortcut!!.toKeyShortcut(true))
        assertEquals(androidx.compose.ui.input.key.KeyShortcut(Key.Tab, ctrl = true), mac.item("Next Tab").shortcut!!.toKeyShortcut(true))     // tab switching is Control on every platform
        assertEquals(Key.Comma, win.item("Preferences…").shortcut!!.key)
        assertNotNull(mac.item("Minimize").shortcut); assertNull(win.item("Minimize").shortcut)
        // no two items share a shortcut, so nothing fires twice
        for (menus in listOf(mac, win)) {
            val keys = menus.flatMap { flat(it.items) }.mapNotNull { it.shortcut }
            assertEquals(keys.size, keys.toSet().size, "duplicate shortcuts: " + keys.groupBy { it }.filter { it.value.size > 1 }.keys)
        }
        // items that have a keyboard shortcut in the app use the same keys
        assertEquals(setOf(Key.K, Key.S, Key.W, Key.N, Key.B, Key.L, Key.C, Key.Enter, Key.Comma, Key.Equals, Key.Minus, Key.Zero),
            win.flatMap { flat(it.items) }.mapNotNull { it.shortcut?.key }.filter { it in setOf(Key.K, Key.S, Key.W, Key.N, Key.B, Key.L, Key.C, Key.Enter, Key.Comma, Key.Equals, Key.Minus, Key.Zero) }.toSet())
    }

    @Test fun itemsAreEnabledOnlyWhenTheyCanDoSomething() {
        val empty = AppMenus.build(state(), isMac = false)
        assertTrue(empty.item("New Request").enabled)                      // no collection yet: it starts one
        assertTrue(empty.item("New Collection…").enabled && empty.item("Open Collection…").enabled && empty.item("Import…").enabled)
        for (l in listOf("New Folder", "New Workflow", "Export Collection…", "Save", "Save All", "Close Tab", "Send", "Cancel Request", "Copy as cURL", "Generate Code…", "Run", "Stop", "Add Step…", "Variables…", "Next Tab"))
            assertFalse(empty.item(l).enabled, "$l should be disabled with nothing open")

        val (s, dir) = withCollection()
        s.openRequest(dir, s.collection(dir)!!.allRequests().first())
        val withRequest = AppMenus.build(s, isMac = false)
        for (l in listOf("New Folder", "New Workflow", "Export Collection…", "Close Tab", "Send", "Focus URL Bar", "Copy as cURL", "Generate Code…", "Variables…")) assertTrue(withRequest.item(l).enabled, "$l should be enabled")
        assertFalse(withRequest.item("Save").enabled)                      // nothing to save yet
        assertFalse(withRequest.item("Run").enabled)                       // a request tab is not a workflow
        s.update(s.activeTab!!) { it.copy(name = "Changed") }
        assertTrue(AppMenus.build(s, isMac = false).item("Save").enabled && AppMenus.build(s, isMac = false).item("Save All").enabled)

        // a workflow tab turns the Workflow menu on and the request items off
        WorkflowStore.save(dir, Workflow("W")); s.reload(dir); s.openWorkflow(dir, "W")
        val onWorkflow = AppMenus.build(s, isMac = false)
        assertTrue(onWorkflow.item("Run").enabled && onWorkflow.item("Add Step…").enabled)
        assertFalse(onWorkflow.item("Send").enabled || onWorkflow.item("Copy as cURL").enabled)
    }

    @Test fun menuActionsDriveTheApp() {
        val (s, dir) = withCollection()
        fun menus() = AppMenus.build(s, isMac = true, quit = { s.showToast("quit") })
        menus().item("New Request").action(); assertEquals(Modal.NewRequest(dir, dir), s.modal)
        menus().item("Import…").action(); assertEquals(Modal.Import, s.modal)
        menus().item("Command Palette…").action(); assertEquals(Modal.Palette, s.modal)
        menus().item("Command Palette…").action(); assertNull(s.modal)                        // the same item closes it again
        menus().item("Cookies…").action(); assertEquals(Modal.Cookies, s.modal)
        s.modal = null

        // theme: a check item per mode, the current one checked
        assertEquals(ThemeMode.System, s.settings.theme)
        assertTrue(menus().item("System").checked == true && menus().item("Dark").checked == false)
        menus().item("Dark").action(); assertEquals(ThemeMode.Dark, s.settings.theme)
        assertTrue(menus().item("Dark").checked == true && menus().item("System").checked == false)
        menus().item("Light").action(); assertEquals(ThemeMode.Light, s.settings.theme)

        // view toggles reflect and change state
        assertTrue(menus().item("Show Sidebar").checked == true)
        menus().item("Show Sidebar").action(); assertFalse(s.sidebarVisible); assertTrue(menus().item("Show Sidebar").checked == false)
        val layout = s.settings.verticalLayout
        menus().item("Stack Request Above Response").action(); assertEquals(!layout, s.settings.verticalLayout)
        val size = s.settings.fontSize
        menus().item("Zoom In").action(); assertEquals(size + 1, s.settings.fontSize)
        menus().item("Actual Size").action(); assertEquals(13, s.settings.fontSize)

        // environments: pick one from the menu
        Collections.saveEnvironment(dir, Environment("Dev", listOf(KV("a", "1")))); s.reload(dir)
        assertTrue(menus().item("No Environment").checked == true)
        menus().item("Dev").action(); assertEquals("Dev", s.envName(dir))
        assertTrue(menus().item("Dev").checked == true && menus().item("No Environment").checked == false)
        AppStore.saveGlobalEnv(Environment("Prod")); s.globalEnvs = AppStore.loadGlobalEnvs()
        menus().item("Prod").action(); assertEquals("Prod", s.globalEnvName())
        menus().item("None").action(); assertNull(s.globalEnvName())

        // workflow menu
        WorkflowStore.save(dir, Workflow("Flow")); s.reload(dir); s.openWorkflow(dir, "Flow")
        val ws = s.activeTab!!.workflow!!
        assertTrue(menus().item("Show Run Log").checked == false)
        menus().item("Show Run Log").action(); assertTrue(ws.logExpanded)
        menus().item("Add Step…").action(); assertEquals(Modal.AddStep(s.activeTab!!.id), s.modal)
        s.modal = null

        // tabs
        s.openRequest(dir, s.collection(dir)!!.allRequests().first())
        val first = s.activeTabId
        menus().item("Next Tab").action(); assertNotEquals(first, s.activeTabId)
        menus().item("Close Tab").action(); assertEquals(1, s.tabs.size)

        // help
        menus().item("Keyboard Shortcuts").action()
        assertEquals(Modal.Settings, s.modal); assertEquals("Shortcuts", s.prefsSection)
    }

    @Test fun windowsMenuItemsReachPreferencesAboutAndExit() {
        val s = state(); var quit = 0
        val win = AppMenus.build(s, isMac = false, quit = { quit++ })
        win.item("Preferences…").action(); assertEquals(Modal.Settings, s.modal)
        win.item("About OpenRest").action(); assertEquals(Modal.About, s.modal)
        win.item("Exit").action(); assertEquals(1, quit)
    }

    @Test fun helpOpensPreferencesOnTheShortcutsSectionAndAboutShowsTheVersion() = runDesktopComposeUiTest(1100, 760) {
        AppStore.saveState(AppStore.SavedState(emptyList(), emptyMap()))
        val s = state()
        setContent { OpenRestApp(s) }
        AppMenus.build(s, isMac = false).item("Keyboard Shortcuts").action(); waitForIdle()
        onNodeWithText("Command palette").assertExists()                                        // opened straight on Shortcuts
        assertNull(s.prefsSection)
        s.modal = null; waitForIdle()
        AppMenus.build(s, isMac = false).item("About OpenRest").action(); waitForIdle()
        onNodeWithText("Version ${AppInfo.VERSION}").assertExists()
    }

    @Test fun aboutVersionMatchesThePackageVersion() {
        val gradle = java.io.File("../desktopApp/build.gradle.kts").takeIf { it.exists() } ?: java.io.File("desktopApp/build.gradle.kts")
        if (gradle.exists()) assertTrue(gradle.readText().contains("packageVersion = \"${AppInfo.VERSION}\""), "AppInfo.VERSION is out of step with desktopApp/build.gradle.kts")
    }
}
