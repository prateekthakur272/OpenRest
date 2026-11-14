package dev.prateekthakur.openrest.features.shell

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.http.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.platform.*
import dev.prateekthakur.openrest.theme.*

import dev.prateekthakur.openrest.BuildConfig

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object AppInfo {
    const val NAME = "OpenRest"
    const val TAGLINE = "An open, file-based API client — requests, environments and workflows that live in plain folders."
}

/** A keyboard shortcut. [primary] is ⌘ on a Mac and Ctrl elsewhere; [control] is the literal Control key (used for tab switching). */
data class Shortcut(val key: Key, val primary: Boolean = true, val shift: Boolean = false, val control: Boolean = false) {
    fun toKeyShortcut(isMac: Boolean) = KeyShortcut(key, ctrl = control || (primary && !isMac), meta = primary && isMac, shift = shift)

    /** As shown in a menu: ⌘⇧S on a Mac, Ctrl+Shift+S elsewhere. */
    fun label(isMac: Boolean): String {
        val k = when (key) {
            Key.Enter -> if (isMac) "↵" else "Enter"
            Key.Comma -> ","; Key.Equals -> if (isMac) "+" else "="; Key.Minus -> "−"; Key.Zero -> "0"
            Key.Tab -> if (isMac) "⇥" else "Tab"
            else -> LETTERS[key] ?: "?"
        }
        return if (isMac) (if (control) "⌃" else "") + (if (shift) "⇧" else "") + (if (primary) "⌘" else "") + k
        else listOfNotNull(if (primary || control) "Ctrl" else null, if (shift) "Shift" else null, k).joinToString("+")
    }

    private companion object {
        val LETTERS: Map<Key, String> = listOf(
            Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
            Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
        ).mapIndexed { i, k -> k to ('A' + i).toString() }.toMap()
    }
}

sealed interface MenuEntry
data object MenuSeparator : MenuEntry
/** [checked] != null makes it a check item. */
class MenuItemSpec(val label: String, val shortcut: Shortcut? = null, val enabled: Boolean = true, val checked: Boolean? = null, val action: () -> Unit) : MenuEntry
class SubMenuSpec(val label: String, val items: List<MenuEntry>, val enabled: Boolean = true) : MenuEntry
class MenuSpec(val label: String, val items: List<MenuEntry>)

/**
 * The application menu bar as plain data, so it can be tested and rendered by the platform's menu bar.
 *
 * macOS: About, Preferences and Quit live in the system's application menu (wired in `installMacAppMenu`), so they are left out here.
 * Windows / Linux: Preferences and Exit are in File, About is in Help; shortcuts use Ctrl instead of ⌘.
 */
object AppMenus {
    fun build(state: AppState, isMac: Boolean, quit: () -> Unit = {}, minimize: () -> Unit = {}): List<MenuSpec> {
        fun key(k: Key, shift: Boolean = false) = Shortcut(k, shift = shift)
        fun item(label: String, shortcut: Shortcut? = null, enabled: Boolean = true, action: () -> Unit) = MenuItemSpec(label, shortcut, enabled, null, action)
        fun check(label: String, checked: Boolean, shortcut: Shortcut? = null, enabled: Boolean = true, action: () -> Unit) = MenuItemSpec(label, shortcut, enabled, checked, action)

        val col = state.currentCollection
        val tab = state.activeTab
        val request = tab?.takeIf { it.isRequest }
        val wf = tab?.workflow
        val anyDirty = state.tabs.any { it.dirty }
        val s = state.settings

        val file = MenuSpec("File", buildList {
            add(item("New Request", key(Key.N)) { col?.let { state.modal = Modal.NewRequest(it.path, it.path) } ?: run { state.modal = Modal.NewCollection } })
            add(item("New Folder", enabled = col != null) { col?.let { state.promptNewFolder(it.path, it.path) } })
            add(item("New Workflow", enabled = col != null) { col?.let { state.promptNewWorkflow(it.path) } })
            add(item("New Collection…") { state.modal = Modal.NewCollection })
            add(MenuSeparator)
            add(item("Open Collection…") { state.modal = Modal.OpenCollection })
            add(item("Import…") { state.modal = Modal.Import })
            add(item("Export Collection…", enabled = col != null) { col?.let { state.modal = Modal.Export(it.path) } })
            add(MenuSeparator)
            add(item("Save", key(Key.S), tab != null && tab.dirty) { tab?.let { state.save(it) } })
            add(item("Save All", key(Key.S, shift = true), anyDirty) { state.saveAll() })
            add(MenuSeparator)
            add(item("Close Tab", key(Key.W), tab != null) { tab?.let { state.closeTab(it.id) } })
            add(item("Close Other Tabs", enabled = state.tabs.size > 1) { tab?.let { state.closeOthers(it.id) } })
            add(item("Close All Saved Tabs", enabled = state.tabs.any { !it.dirty }) { state.closeAll() })
            if (!isMac) {
                add(MenuSeparator)
                add(item("Preferences…", key(Key.Comma)) { state.modal = Modal.Settings })
                add(MenuSeparator)
                add(item("Exit", key(Key.Q)) { quit() })
            }
        })

        val view = MenuSpec("View", listOf(
            item("Command Palette…", key(Key.K)) { state.modal = if (state.modal == Modal.Palette) null else Modal.Palette },
            MenuSeparator,
            check("Show Sidebar", state.sidebarVisible, key(Key.B)) { state.sidebarVisible = !state.sidebarVisible },
            check("Stack Request Above Response", s.verticalLayout, key(Key.L, shift = true)) { state.toggleLayout() },
            SubMenuSpec("Theme", ThemeMode.entries.map { m -> check(m.label, s.theme == m) { state.updateSettings(s.copy(theme = m)) } }),
            MenuSeparator,
            item("Zoom In", key(Key.Equals), s.fontSize < 20) { state.zoom(1) },
            item("Zoom Out", key(Key.Minus), s.fontSize > 10) { state.zoom(-1) },
            item("Actual Size", key(Key.Zero), s.fontSize != 13) { state.zoom(0) },
            MenuSeparator,
            item("Cookies…") { state.modal = Modal.Cookies },
            item("Variables…", enabled = col != null) { col?.let { state.modal = Modal.Vars(it.path) } },
        ))

        val envs = col?.environments ?: emptyList()
        val activeEnv = col?.let { state.envName(it.path) }
        val request_ = MenuSpec("Request", listOf(
            item("Send", key(Key.Enter), request != null && !request.sending) { request?.let { state.send(it) } },
            item("Cancel Request", enabled = request?.sending == true) { request?.let { state.cancel(it) } },
            MenuSeparator,
            item("Focus URL Bar", key(Key.L), request != null) { runCatching { state.urlFocus.requestFocus() } },
            item("Copy as cURL", key(Key.C, shift = true), request != null) { request?.let { state.copyCurl(it) } },
            item("Generate Code…", enabled = request != null) { request?.let { state.modal = Modal.Codegen(it.id) } },
            MenuSeparator,
            SubMenuSpec("Environment", buildList {
                add(check("No Environment", activeEnv == null) { col?.let { state.setEnv(it.path, null) } })
                envs.forEach { e -> add(check(e.name, activeEnv == e.name) { col?.let { state.setEnv(it.path, e.name) } }) }
                add(MenuSeparator)
                add(item("Manage Environments…", enabled = col != null) { col?.let { state.modal = Modal.Envs(it.path) } })
            }, enabled = col != null),
            SubMenuSpec("Global Environment", buildList {
                add(check("None", state.globalEnvName() == null) { state.setGlobalEnv(null) })
                state.globalEnvs.forEach { e -> add(check(e.name, state.globalEnvName() == e.name) { state.setGlobalEnv(e.name) }) }
                add(MenuSeparator)
                add(item("Manage Global Environments…") { state.modal = Modal.Envs(null) })
            }),
            MenuSeparator,
            item("Run Collection…", enabled = col != null) { col?.let { state.openRunner(it.path, null) } },
        ))

        val workflow = MenuSpec("Workflow", listOf(
            item("New Workflow", enabled = col != null) { col?.let { state.promptNewWorkflow(it.path) } },
            MenuSeparator,
            item("Run", enabled = wf != null && !wf.running) { tab?.let { state.runWorkflow(it) } },
            item("Stop", enabled = wf?.running == true) { wf?.cancelled = true },
            item("Add Step…", enabled = wf != null) { tab?.let { state.modal = Modal.AddStep(it.id) } },
            MenuSeparator,
            item("Save Workflow", enabled = wf != null && wf.dirty) { tab?.let { state.saveWorkflow(it) } },
            check("Show Run Log", wf?.logExpanded == true, enabled = wf != null) { wf?.let { it.logExpanded = !it.logExpanded } },
        ))

        val window = MenuSpec("Window", listOf(
            item("Minimize", if (isMac) key(Key.M) else null) { minimize() },
            MenuSeparator,
            item("Next Tab", Shortcut(Key.Tab, primary = false, control = true), state.tabs.size > 1) { state.nextTab(1) },
            item("Previous Tab", Shortcut(Key.Tab, primary = false, control = true, shift = true), state.tabs.size > 1) { state.nextTab(-1) },
        ))

        val help = MenuSpec("Help", buildList {
            add(item("Welcome Tour") { state.modal = Modal.Onboarding })
            add(item("Keyboard Shortcuts") { state.prefsSection = "Shortcuts"; state.modal = Modal.Settings })
            add(item("Developer…") { state.modal = Modal.Developer })
            add(item("Show Data Folder") { Platform.reveal(dev.prateekthakur.openrest.core.storage.AppStore.dir) })
            if (!isMac) { add(MenuSeparator); add(item("About ${AppInfo.NAME}") { state.modal = Modal.About }) }
        })

        return listOf(file, view, request_, workflow, window, help)
    }
}

/** macOS puts About, Preferences and Quit in the application menu; route them to the app. */
fun installMacAppMenu(state: AppState, quit: () -> Unit) {
    runCatching {
        if (!java.awt.Desktop.isDesktopSupported()) return
        val d = java.awt.Desktop.getDesktop()
        if (d.isSupported(java.awt.Desktop.Action.APP_PREFERENCES)) d.setPreferencesHandler { state.modal = Modal.Settings }
        if (d.isSupported(java.awt.Desktop.Action.APP_ABOUT)) d.setAboutHandler { state.modal = Modal.About }
        if (d.isSupported(java.awt.Desktop.Action.APP_QUIT_HANDLER)) d.setQuitHandler { _, response -> state.persist(); response.cancelQuit(); quit() }
    }
}

@Composable
fun AboutDialog(state: AppState) {
    val p = LocalPal.current
    ModalBox("About ${AppInfo.NAME}", { state.modal = null }, 440.dp, actions = { Btn("Close", { state.modal = null }, primary = true) }) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Ico(Ic.Logo, tint = p.accent, size = 56.dp)
            T(AppInfo.NAME, size = 22, weight = FontWeight.Bold)
            T("Version ${BuildConfig.VERSION}", color = p.muted, size = 12)
            if (BuildConfig.VENDOR.isNotBlank()) T("by ${BuildConfig.VENDOR}", color = p.muted, size = 12)
            Spacer(Modifier.height(6.dp))
            T(AppInfo.TAGLINE, color = p.muted, size = 12, modifier = Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(6.dp))
            T("Your data stays on this computer, in ${dev.prateekthakur.openrest.core.storage.AppStore.dir}", color = p.muted, size = 11, mono = true)
        }
    }
}
