package dev.prateekthakur.openrest.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.prateekthakur.openrest.app.TitleMenuState

/**
 * The menu bar drawn inside the title bar (Windows / Linux), from the same [AppMenus] data the native macOS bar uses.
 * Click a title to open its menu; while one is open, moving over another title switches to it, like a native menu bar.
 * Clicking anywhere else, or pressing Esc, closes it. The open menu is drawn in the app's overlay layer, so it never blocks the bar.
 */
@Composable
fun TitleMenus(menus: List<MenuSpec>, tm: TitleMenuState) {
    val p = LocalPal.current
    val overlay = LocalMenuOverlay.current
    val token = remember { Any() }
    DisposableEffect(Unit) { onDispose { overlay?.hide(token); tm.close() } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        menus.forEach { m ->
            val src = remember { MutableInteractionSource() }
            val hov by src.collectIsHoveredAsState()
            LaunchedEffect(hov) { if (hov && tm.open != null && tm.open != m.label) tm.open = m.label }
            val on = tm.open == m.label
            Box(
                Modifier.testTag("menu-${m.label}").onGloballyPositioned { tm.anchors[m.label] = it.boundsInWindow() }
                    .clip(RoundedCornerShape(4.dp)).background(if (on || hov) p.hover else Color.Transparent)
                    .hoverable(src).clickable { tm.open = if (on) null else m.label }.pointerHoverIcon(PointerIcon.Default).padding(horizontal = 9.dp, vertical = 4.dp),
            ) { T(m.label, size = 12, weight = if (on) FontWeight.SemiBold else null) }
        }
    }
    // draw the open menu in the overlay layer, under its title
    val openMenu = menus.firstOrNull { it.label == tm.open }
    val anchor = openMenu?.let { tm.anchors[it.label] }
    SideEffect {
        if (openMenu != null && anchor != null && overlay != null) {
            val rows = openMenu.items.sumOf { e -> if (e is SubMenuSpec) e.items.size + 1 else 1 }
            overlay.show(token, anchor.left, anchor.top, anchor.bottom, rows * 30f + 16f) { MenuPanel(openMenu, tm) }
        } else overlay?.hide(token)
    }
}

@Composable
private fun MenuPanel(menu: MenuSpec, tm: TitleMenuState) {
    val p = LocalPal.current
    androidx.compose.material3.Surface(
        modifier = Modifier.width(350.dp).testTag("menu-panel-${menu.label}").onGloballyPositioned { tm.panel = it.boundsInWindow() },
        shape = RoundedCornerShape(8.dp), color = p.panel, shadowElevation = 12.dp, border = BorderStroke(1.dp, p.border),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { MenuEntries(menu.items, indent = false, tm = tm) }
    }
}

@Composable
private fun MenuEntries(items: List<MenuEntry>, indent: Boolean, tm: TitleMenuState) {
    val p = LocalPal.current
    items.forEach { e ->
        when (e) {
            MenuSeparator -> Box(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(1.dp).background(p.border))
            is SubMenuSpec -> {
                T(e.label.uppercase(), color = p.muted, size = 10, weight = FontWeight.SemiBold, modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 2.dp))
                MenuEntries(e.items.map { if (!e.enabled && it is MenuItemSpec) MenuItemSpec(it.label, it.shortcut, false, it.checked, it.action) else it }, indent = true, tm = tm)
            }
            is MenuItemSpec -> {
                val src = remember { MutableInteractionSource() }
                val hov by src.collectIsHoveredAsState()
                Row(
                    Modifier.testTag("menuitem-${e.label}").fillMaxWidth().height(28.dp).background(if (hov && e.enabled) p.accent.copy(alpha = 0.14f) else Color.Transparent)
                        .hoverable(src).clickable(enabled = e.enabled) { tm.close(); e.action() }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.width(if (indent) 30.dp else 16.dp), contentAlignment = Alignment.CenterEnd) { if (e.checked == true) Ico(Ic.Check, tint = p.accent, size = 14.dp) }
                    T(e.label, color = if (e.enabled) p.text else p.muted.copy(alpha = 0.6f), modifier = Modifier.weight(1f), maxLines = 1)
                    e.shortcut?.let { T(it.label(isMac = false), color = p.muted, size = 11, maxLines = 1) }
                }
            }
        }
    }
}
