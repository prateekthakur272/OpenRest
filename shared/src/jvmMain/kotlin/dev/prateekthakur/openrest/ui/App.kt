package dev.prateekthakur.openrest.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.*

/** Esc closes an open title-bar menu; returns false (leaving Esc to others) when none is open. */
fun closeTitleMenu(state: AppState): Boolean {
    if (state.titleMenu.open == null) return false
    state.titleMenu.close(); return true
}

fun handleKey(state: AppState, e: KeyEvent): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    if (e.key == Key.Escape && closeTitleMenu(state)) return true
    val mod = e.isMetaPressed || e.isCtrlPressed
    val anyTab = state.activeTab
    val tab = anyTab?.takeIf { it.isRequest }
    if (e.key == Key.Tab && e.isCtrlPressed) { state.nextTab(if (e.isShiftPressed) -1 else 1); return true }
    if (!mod) return false
    val digits = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
    digits.indexOf(e.key).takeIf { it >= 0 }?.let { state.gotoTab(if (it == 8) state.tabs.size - 1 else it); return true }
    return when (e.key) {
        Key.K, Key.P -> { state.modal = if (state.modal == Modal.Palette) null else Modal.Palette; true }
        Key.Enter -> { tab?.let { state.send(it) }; tab != null }
        Key.S -> { if (e.isShiftPressed) state.saveAll() else anyTab?.let { state.save(it) }; true }
        Key.W -> { anyTab?.let { state.closeTab(it.id) }; anyTab != null }
        Key.N -> { state.currentCollection?.let { state.modal = Modal.NewRequest(it.path, it.path) } ?: run { state.modal = Modal.NewCollection }; true }
        Key.B -> { state.sidebarVisible = !state.sidebarVisible; true }
        Key.L -> if (e.isShiftPressed) { state.toggleLayout(); true } else { runCatching { state.urlFocus.requestFocus() }; tab != null }
        Key.C -> if (e.isShiftPressed && tab != null) { state.copyCurl(tab); true } else false
        Key.LeftBracket -> if (e.isShiftPressed) { state.nextTab(-1); true } else false
        Key.RightBracket -> if (e.isShiftPressed) { state.nextTab(1); true } else false
        Key.Comma -> { state.modal = Modal.Settings; true }
        Key.Equals, Key.Plus -> { state.zoom(1); true }
        Key.Minus -> { state.zoom(-1); true }
        Key.Zero -> { state.zoom(0); true }
        else -> false
    }
}

@Composable
fun OpenRestApp(state: AppState, chrome: WindowChrome = WindowChrome.None) {
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(base.density, state.settings.fontSize / 13f)) {
    OpenRestTheme(state.settings.theme.isDark(androidx.compose.foundation.isSystemInDarkTheme())) {
        val p = LocalPal.current
        val focused = LocalWindowInfo.current.isWindowFocused
        var wasFocused by remember { mutableStateOf(true) }
        LaunchedEffect(focused) { if (focused && !wasFocused) state.reloadAll(); wasFocused = focused }
        val menuOverlay = remember { MenuOverlay() }
        CompositionLocalProvider(LocalMenuOverlay provides menuOverlay) {
        Box(
            Modifier.fillMaxSize().background(p.bg).pointerInput(Unit) {
                // a press anywhere outside the open title-bar menu (and its titles) closes it; the press itself is not consumed
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val tm = state.titleMenu
                        if (tm.open != null && e.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                            val pos = e.changes.first().position
                            if (tm.panel?.contains(pos) != true && tm.anchors.values.none { it.contains(pos) }) tm.close()
                        }
                    }
                }
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                TitleBar(state, chrome)
                TopBar(state)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val density = LocalDensity.current
                    val totalPx = with(density) { maxWidth.toPx() }
                    val sidebarDp = (maxWidth * state.sidebarWidth).coerceIn(210.dp, 480.dp)
                    Row(Modifier.fillMaxSize()) {
                        if (state.sidebarVisible) Sidebar(state, Modifier.width(sidebarDp).fillMaxHeight())
                        if (state.sidebarVisible) Box(
                            Modifier.width(5.dp).fillMaxHeight().background(p.border)
                                .pointerHoverIcon(PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.E_RESIZE_CURSOR)))
                                .pointerInput(Unit) { detectDragGestures { c, d -> c.consume(); state.sidebarWidth = (state.sidebarWidth + d.x / totalPx).coerceIn(0.12f, 0.45f) } },
                        )
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            if (state.tabs.isNotEmpty()) TabStrip(state)
                            val tab = state.activeTab
                            if (tab?.workflow != null) key(tab.id) { WorkflowView(state, tab) }
                            else if (tab?.runner != null) key(tab.id) { RunnerView(state, tab) }
                            else if (tab != null) key(tab.id) { RequestView(state, tab) } else Welcome(state)
                        }
                    }
                }
                StatusBar(state)
            }
            Modals(state)
            MenuOverlayHost(menuOverlay)
            state.toast?.let { msg ->
                Box(Modifier.align(Alignment.BottomCenter).padding(24.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (p.dark) Color(0xFF3A3F47) else Color(0xFF1F2328)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                        T(msg, color = Color.White)
                    }
                }
            }
        }
        }
    }
    }
}

@Composable
private fun TopBtn(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val p = LocalPal.current
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Row(
        Modifier.clip(RoundedCornerShape(5.dp)).background(if (hov) p.hover else Color.Transparent).hoverable(src).clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Ico(icon, tint = if (hov) p.text else p.muted, size = 15.dp)
        T(label, color = if (hov) p.text else p.muted, size = 12)
    }
}

/** The slim title bar above the header: it moves the window and (on Windows / Linux) carries the window buttons. */
val TITLE_BAR_HEIGHT = 30.dp

/**
 * The window's own title bar. macOS keeps its native traffic-light buttons floating over its left end ([WindowChrome.leftInset]);
 * Windows / Linux get our buttons on the right. In the middle: which collection and tab you are working in.
 */
@Composable
private fun TitleBar(state: AppState, chrome: WindowChrome) {
    val p = LocalPal.current
    val col = state.currentCollection
    val tab = state.activeTab
    chrome.dragArea {
        BoxWithConstraints(Modifier.fillMaxWidth().height(TITLE_BAR_HEIGHT).background(p.panel).drawBottomBorder(p.border).titleBarDoubleClick(chrome)) {
            Row(Modifier.fillMaxSize().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (chrome.leftInset > 0.dp) Spacer(Modifier.width(chrome.leftInset))
                if (chrome.inlineMenus) {
                    Ico(Ic.Logo, tint = p.accent, size = 16.dp)
                    TitleMenus(AppMenus.build(state, isMac = false, quit = chrome.onQuit, minimize = chrome.onMinimize), state.titleMenu)
                }
                Spacer(Modifier.weight(1f))
                chrome.controls?.invoke()
            }
            // like a native window title: what you are working on, centred; the app's name when nothing is open.
            // With the menus in the bar there is no room for it in a narrow window.
            val where = listOfNotNull(col?.name, tab?.title).joinToString("  ›  ").ifEmpty { AppInfo.NAME }
            if (!chrome.inlineMenus || maxWidth >= 1000.dp) T(where, color = p.muted, size = 12, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.Center).widthIn(max = 380.dp).testTag("title-context"))
        }
    }
}

/** The header, as it always was: logo, name, collection, environments and the main actions. */
@Composable
private fun TopBar(state: AppState) {
    val p = LocalPal.current
    val col = state.currentCollection
    Row(Modifier.fillMaxWidth().height(44.dp).background(p.panel).drawBottomBorder(p.border).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Ico(Ic.Logo, tint = p.accent, size = 24.dp)
        T("OpenRest", weight = FontWeight.Bold, size = 15, modifier = Modifier.padding(end = 14.dp))
        if (col != null) {
            T(col.name, color = p.muted, size = 12, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
            T("/", color = p.muted, size = 12)
        }
        Spacer(Modifier.weight(1f))
        val globalCur = state.globalEnvName()
        Dropdown(
            globalCur?.let { "Global: $it" } ?: "No Global",
            listOf("No Global") + state.globalEnvs.map { it.name },
            { state.setGlobalEnv(if (it == "No Global") null else it) },
            color = if (globalCur != null) p.ok else p.muted,
        )
        if (col != null) {
            val cur = state.envName(col.path)
            Dropdown(
                cur ?: "No Environment", listOf("No Environment") + col.environments.map { it.name },
                { state.setEnv(col.path, if (it == "No Environment") null else it) },
                color = if (cur != null) p.ok else p.muted,
            )
            TopBtn("Environments", Ic.Environments) { state.modal = Modal.Envs(col.path) }
            TopBtn("Variables", Ic.Variables) { state.modal = Modal.Vars(col.path) }
            TopBtn("Run", Ic.Play) { state.openRunner(col.path, null) }
        }
        if (col == null) TopBtn("Environments", Ic.Environments) { state.modal = Modal.Envs(null) }
        TopBtn("Cookies", Ic.Cookie) { state.modal = Modal.Cookies }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabStrip(state: AppState) {
    val p = LocalPal.current
    // drag-to-reorder: the dragged tab follows the pointer; crossing half of a neighbour's width swaps them
    val widths = remember { mutableStateMapOf<Int, Int>() }
    var dragId by remember { mutableStateOf<Int?>(null) }
    var dragDx by remember { mutableStateOf(0f) }
    Row(Modifier.fillMaxWidth().background(p.panel).drawBottomBorder(p.border).horizontalScroll(rememberScrollState())) {
        state.tabs.toList().forEach { t -> key(t.id) {
            val sel = t.id == state.activeTabId
            val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val hov by src.collectIsHoveredAsState()
            var menu by remember { mutableStateOf(false) }
            Box {
                Row(
                    Modifier.height(36.dp).widthIn(max = 220.dp)
                        .zIndex(if (dragId == t.id) 1f else 0f)
                        .pointerInput(t.id) {
                            detectDragGestures(
                                onDragStart = { dragId = t.id; dragDx = 0f; state.activeTabId = t.id },
                                onDragEnd = { dragId = null; dragDx = 0f },
                                onDragCancel = { dragId = null; dragDx = 0f },
                            ) { change, d ->
                                change.consume()
                                dragDx += d.x
                                val i = state.tabs.indexOfFirst { it.id == t.id }
                                val next = state.tabs.getOrNull(if (dragDx > 0) i + 1 else i - 1) ?: return@detectDragGestures
                                val w = (widths[next.id] ?: 0).toFloat()
                                if (kotlin.math.abs(dragDx) > w / 2) {
                                    state.moveTab(i, state.tabs.indexOf(next))
                                    dragDx -= if (dragDx > 0) w else -w
                                }
                            }
                        }
                        .graphicsLayer { translationX = if (dragId == t.id) dragDx else 0f }
                        .onSizeChanged { widths[t.id] = it.width }
                        .background(if (sel) p.bg else if (hov) p.hover else Color.Transparent).hoverable(src)
                        .onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary)) { menu = true }
                        .onClick(matcher = PointerMatcher.mouse(PointerButton.Tertiary)) { state.closeTab(t.id) }
                        .clickable { state.activeTabId = t.id }.pointerHoverIcon(PointerIcon.Hand).padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (t.sending) androidx.compose.material3.CircularProgressIndicator(Modifier.size(11.dp), color = p.accent, strokeWidth = 1.5.dp)
                    if (t.workflow != null) Ico(Ic.Workflow, tint = if (t.workflow.running) p.accent else p.muted, size = 13.dp)
                    else if (t.runner != null) Ico(Ic.Play, tint = if (t.runner.running) p.accent else p.muted, size = 13.dp)
                    else T(t.draft.method.let { if (it == "DELETE") "DEL" else it }, color = methodColor(t.draft.method, p), size = 10, weight = FontWeight.Bold)
                    T(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (sel) p.text else p.muted, modifier = Modifier.weight(1f, fill = false))
                    t.result?.response?.takeIf { it.error == null }?.let { Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(statusColor(it.status, p))) }
                    if (t.dirty && !hov) Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(p.accent)) }
                    else IconBtn(Ic.Close, { state.closeTab(t.id) }, size = 14)
                }
                if (sel) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(p.accent))
                ContextMenu(
                    menu, { menu = false },
                    (if (!t.isRequest) emptyList() else listOf(
                        MenuItem("Send") { state.send(t) },
                        MenuItem("Save") { state.save(t) },
                        MenuItem("Copy as cURL") { state.copyCurl(t) },
                        MenuItem("Generate Code…") { state.modal = Modal.Codegen(t.id) },
                        MenuItem(null),
                    )) + listOf(
                        MenuItem("Close") { state.closeTab(t.id) },
                        MenuItem("Close Others") { state.closeOthers(t.id) },
                        MenuItem("Close All Saved") { state.closeAll() },
                    ) + (if (!t.isRequest) emptyList() else listOf(MenuItem("Show in Folder") { Platform.reveal(t.filePath) })),
                )
            }
            Box(Modifier.width(1.dp).height(36.dp).background(p.border))
        } }
        IconBtn(Ic.Add, { state.currentCollection?.let { state.modal = Modal.NewRequest(it.path, it.path) } }, Modifier.padding(horizontal = 6.dp, vertical = 6.dp), color = p.text)
    }
}

@Composable
private fun StatusBar(state: AppState) {
    val p = LocalPal.current
    val col = state.currentCollection
    val tab = state.activeTab
    val env = col?.let { state.envName(it.path) }
    Row(
        Modifier.fillMaxWidth().height(24.dp).background(p.panel).padding(horizontal = 10.dp).drawTopBorder(p.border),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.Sidebar, { state.sidebarVisible = !state.sidebarVisible }, color = if (state.sidebarVisible) p.accent else p.muted, size = 15)
            IconBtn(Ic.Settings, { state.modal = Modal.Settings }, color = p.muted, size = 15)
        }
        if (col != null) {
            T(col.name, size = 11, color = p.muted, maxLines = 1)
            Row(Modifier.clickable { state.modal = Modal.Envs(col.path) }.pointerHoverIcon(PointerIcon.Hand), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(if (env != null) p.ok else p.muted))
                T(env ?: "no environment", size = 11, color = if (env != null) p.text else p.muted)
            }
            state.globalEnvName()?.let { g ->
                Row(Modifier.clickable { state.modal = Modal.Envs(null) }.pointerHoverIcon(PointerIcon.Hand), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("global:", size = 11, color = p.muted); T(g, size = 11, color = p.text)
                }
            }
            val rt = state.runtime(col.path).size
            if (rt > 0) T("$rt runtime var${if (rt == 1) "" else "s"}", size = 11, color = p.muted, modifier = Modifier.clickable { state.modal = Modal.Vars(col.path) })
        }
        Spacer(Modifier.weight(1f))
        when {
            tab?.sending == true -> T("sending…", size = 11, color = p.accent)
            tab?.result?.response?.takeIf { it.error == null } != null -> tab!!.result!!.response!!.let { T("${it.status} · ${it.timeMs} ms", size = 11, color = statusColor(it.status, p)) }
            tab?.result?.error != null -> T("error", size = 11, color = p.err)
        }
        tab?.let { t -> if (t.dirty) T("unsaved", size = 11, color = p.accent) }
        Row(Modifier.clickable { state.modal = Modal.Palette }.pointerHoverIcon(PointerIcon.Hand), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            T("⌘K", size = 11, color = p.accent, mono = true); T("commands", size = 11, color = p.muted)
        }
    }
}

private fun Modifier.drawTopBorder(c: Color): Modifier = this.drawBehind {
    drawLine(c, androidx.compose.ui.geometry.Offset(0f, 0.5f), androidx.compose.ui.geometry.Offset(size.width, 0.5f), 1f)
}

@Composable
private fun Welcome(state: AppState) {
    val p = LocalPal.current
    Column(Modifier.fillMaxSize().padding(40.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Ico(Ic.Logo, tint = p.accent, size = 56.dp)
        T("OpenRest", size = 28, weight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        T("Open-source API client — collections are plain files on disk.", color = p.muted)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("New Collection", { state.modal = Modal.NewCollection }, primary = true, icon = Ic.NewFolder)
            Btn("Open Collection", { state.modal = Modal.OpenCollection }, icon = Ic.FolderOpen)
            Btn("Import", { state.modal = Modal.Import }, icon = Ic.Import)
        }
        Spacer(Modifier.height(26.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("⌘/Ctrl + K" to "Command palette: jump to any request or action", "⌘/Ctrl + Enter" to "Send request", "⌘/Ctrl + S" to "Save request", "⌘/Ctrl + L" to "Focus URL bar", "⌘/Ctrl + B" to "Toggle sidebar", "⌘/Ctrl + 1…9" to "Switch tab", "⌘/Ctrl + Shift + C" to "Copy as cURL", "⌘/Ctrl + N" to "New request", "⌘/Ctrl + + / − / 0" to "Zoom UI").forEach { (k, v) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { T(k, mono = true, color = p.accent, size = 12, modifier = Modifier.width(150.dp)); T(v, color = p.muted, size = 12) }
            }
        }
    }
}
