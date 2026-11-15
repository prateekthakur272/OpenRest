package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.features.shell.*
import dev.prateekthakur.openrest.platform.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.prateekthakur.openrest.app.AppState
import dev.prateekthakur.openrest.platform.Platform
import dev.prateekthakur.openrest.features.shell.AppInfo
import dev.prateekthakur.openrest.features.shell.AppMetadata
import dev.prateekthakur.openrest.features.shell.AppMenus
import dev.prateekthakur.openrest.features.shell.MenuEntry
import dev.prateekthakur.openrest.features.shell.MenuItemSpec
import dev.prateekthakur.openrest.features.shell.MenuSeparator
import dev.prateekthakur.openrest.features.shell.OpenRestApp
import dev.prateekthakur.openrest.features.shell.ResizeEdges
import dev.prateekthakur.openrest.features.shell.WindowChrome
import dev.prateekthakur.openrest.features.shell.WindowControls
import dev.prateekthakur.openrest.features.shell.SubMenuSpec
import dev.prateekthakur.openrest.features.shell.handleKey
import dev.prateekthakur.openrest.features.shell.installMacAppMenu

/** True when the saved window would still show a usable part of a connected screen. */
private fun onScreen(x: Float, y: Float, w: Float, h: Float): Boolean = runCatching {
    val r = java.awt.Rectangle(x.toInt(), y.toInt(), w.toInt(), h.toInt())
    java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any { d ->
        val i = d.defaultConfiguration.bounds.intersection(r)
        i.width >= 120 && i.height >= 80 && y >= d.defaultConfiguration.bounds.y - 1
    }
}.getOrDefault(false)

@OptIn(FlowPreview::class)
fun main() {
    AppInfo.meta = AppMetadata(BuildConfig.APP_NAME, BuildConfig.BUNDLE_ID, BuildConfig.VERSION, BuildConfig.VENDOR, BuildConfig.DESCRIPTION, BuildConfig.HOMEPAGE, BuildConfig.DEVELOPER_URL)
    // macOS shows this in the menu bar and the Dock instead of the Java class name
    System.setProperty("apple.awt.application.name", AppInfo.NAME)
    application {
        val scope = rememberCoroutineScope()
        val state = remember {
            val firstRun = AppStore.isFirstRun()                      // decided before anything is saved
            AppState(scope).also { if (firstRun) it.modal = Modal.Onboarding }
        }
        val isMac = Platform.isMac
        // reopen where the user left it; a saved position that is no longer on any screen (unplugged monitor) is dropped
        val saved = remember { AppStore.loadWindow() }
        val windowState = rememberWindowState(
            size = saved?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(1360.dp, 860.dp),
            position = saved?.takeIf { it.x != null && it.y != null && onScreen(it.x!!, it.y!!, it.width, it.height) }?.let { WindowPosition(it.x!!.dp, it.y!!.dp) } ?: WindowPosition.PlatformDefault,
            placement = if (saved?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
        )
        // the floating size / position is kept while maximized, so un-maximizing returns to it
        var floating by remember { mutableStateOf(saved) }
        LaunchedEffect(windowState) {
            snapshotFlow { Triple(windowState.size, windowState.position, windowState.placement) }
                .debounce(400)
                .collect { (size, pos, placement) ->
                    val max = placement == WindowPlacement.Maximized
                    if (!max && size.isSpecified && !windowState.isMinimized) {
                        floating = AppStore.WindowGeometry(size.width.value, size.height.value, pos.takeIf { it.isSpecified }?.x?.value, pos.takeIf { it.isSpecified }?.y?.value)
                    }
                    floating?.let { AppStore.saveWindow(it.copy(maximized = max)) }
                }
        }
        val quit = {
            state.persist()
            floating?.let { f ->
                val max = windowState.placement == WindowPlacement.Maximized
                val live = if (!max && windowState.size.isSpecified) AppStore.WindowGeometry(windowState.size.width.value, windowState.size.height.value, windowState.position.takeIf { it.isSpecified }?.x?.value, windowState.position.takeIf { it.isSpecified }?.y?.value) else f
                AppStore.saveWindow(live.copy(maximized = max))
            }
            exitApplication()
        }
        // The app's own top bar is the title bar. macOS keeps its native traffic-light buttons, floating over that bar;
        // Windows / Linux draw nothing, so the app supplies its own window buttons, drag area and resize edges.
        Window(
            onCloseRequest = quit,
            title = AppInfo.NAME,
            state = windowState,
            undecorated = !isMac,
            onPreviewKeyEvent = {
                if (!isMac && it.type == KeyEventType.KeyDown && it.isCtrlPressed && it.key == Key.Q) { quit(); true } else handleKey(state, it)
            },
        ) {
            if (isMac) SideEffect {
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            }
            val maximized = windowState.placement == WindowPlacement.Maximized
            val toggleMaximize = {
                if (!maximized && !isMac) {
                    // an undecorated window would otherwise maximize over the taskbar: keep to the usable part of this screen
                    val gc = window.graphicsConfiguration
                    val b = gc.bounds
                    val i = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc)
                    window.maximizedBounds = java.awt.Rectangle(b.x + i.left, b.y + i.top, b.width - i.left - i.right, b.height - i.top - i.bottom)
                }
                windowState.placement = if (maximized) WindowPlacement.Floating else WindowPlacement.Maximized
            }
            val chrome = if (isMac) WindowChrome(leftInset = 72.dp) else WindowChrome(
                controls = { WindowControls(maximized = maximized, onMinimize = { windowState.isMinimized = true }, onToggleMaximize = toggleMaximize, onClose = quit) },
                dragArea = { content -> WindowDraggableArea { content() } },
                onDoubleClick = toggleMaximize,
                inlineMenus = true,
                onQuit = quit,
                onMinimize = { windowState.isMinimized = true },
            )
            if (isMac) OpenRestMenuBar(state, quit)                                    // Windows / Linux: the menus are in the title bar
            Box(Modifier.fillMaxSize()) {
                OpenRestApp(state, chrome)
                if (!isMac && !maximized) ResizeEdges(window)
            }
        }
    }
}

/**
 * The native menu bar: in the system bar on macOS, in the window on Windows and Linux.
 * What goes in it is decided by [AppMenus] (and tested there); this only hands it to the platform.
 */
@Composable
private fun FrameWindowScope.OpenRestMenuBar(state: AppState, quit: () -> Unit) {
    val isMac = Platform.isMac
    LaunchedEffect(Unit) { if (isMac) installMacAppMenu(state, quit) }
    val menus = AppMenus.build(state, isMac, quit, minimize = { window.extendedState = java.awt.Frame.ICONIFIED })
    MenuBar { menus.forEach { m -> Menu(m.label) { render(m.items) } } }
}

@Composable
private fun androidx.compose.ui.window.MenuScope.render(items: List<MenuEntry>) {
    items.forEach { e ->
        when (e) {
            MenuSeparator -> Separator()
            is SubMenuSpec -> Menu(e.label, e.enabled) { render(e.items) }
            is MenuItemSpec -> {
                val checked = e.checked
                if (checked != null) CheckboxItem(text = e.label, checked = checked, onCheckedChange = { e.action() }, enabled = e.enabled, shortcut = e.shortcut?.toKeyShortcut(Platform.isMac))
                else Item(text = e.label, enabled = e.enabled, shortcut = e.shortcut?.toKeyShortcut(Platform.isMac), onClick = e.action)
            }
        }
    }
}
