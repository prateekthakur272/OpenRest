package dev.prateekthakur.openrest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.*

private enum class Section(val label: String, val blurb: String) {
    General("General", "How OpenRest behaves day to day."),
    Appearance("Appearance", "Theme, size and layout."),
    Requests("Requests", "Defaults applied to every request you send."),
    Network("Network", "Certificates and cookies."),
    Proxy("Proxy", "Send requests through an HTTP proxy."),
    Shortcuts("Shortcuts", "Keyboard shortcuts. Use Ctrl instead of ⌘ on Windows and Linux."),
    Data("Data", "Where OpenRest keeps your files.");
}

/** One setting: what it is, a line of explanation, and the control that changes it. */
private class Pref(val section: Section, val title: String, val desc: String = "", val descColor: Color? = null, val control: @Composable () -> Unit)

@Composable
private fun Toggle(on: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier.testTag(tag).width(38.dp).height(22.dp).clip(RoundedCornerShape(11.dp)).background(if (on) p.accent else p.border)
            .clickable { onChange(!on) }.pointerHoverIcon(PointerIcon.Hand).padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(16.dp).clip(CircleShape).background(Color.White)) }
}

@Composable
private fun Segmented(options: List<String>, selected: String, tag: String, onSelect: (String) -> Unit) {
    val p = LocalPal.current
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(p.bg).border(1.dp, p.border, RoundedCornerShape(8.dp)).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier.testTag("$tag-$o").clip(RoundedCornerShape(6.dp)).background(if (on) p.accent else Color.Transparent)
                    .clickable { onSelect(o) }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 12.dp, vertical = 5.dp),
            ) { T(o, color = if (on) Color.Black else p.text, size = 12, weight = if (on) FontWeight.SemiBold else null) }
        }
    }
}

@Composable
private fun NumberField(value: Int, suffix: String = "", width: androidx.compose.ui.unit.Dp = 100.dp, onChange: (Int) -> Unit) {
    val p = LocalPal.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Field(value.toString(), { v -> onChange(v.filter { it.isDigit() }.take(7).toIntOrNull() ?: 0) }, Modifier.width(width), highlightVars = false)
        if (suffix.isNotEmpty()) T(suffix, color = p.muted, size = 12)
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Row(
        Modifier.testTag(tag).fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(if (selected) p.accent.copy(alpha = 0.16f) else if (hov) p.hover else Color.Transparent)
            .hoverable(src).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { T(label, color = if (selected) p.accent else p.text, weight = if (selected) FontWeight.SemiBold else null) }
}

/** App-level preferences: sections on the left, settings with a line of explanation each on the right, and a search across all of them. */
@Composable
fun PreferencesDialog(state: AppState) {
    val p = LocalPal.current
    val s = state.settings
    var section by remember { mutableStateOf(Section.entries.firstOrNull { it.label == state.prefsSection } ?: Section.General) }
    LaunchedEffect(Unit) { state.prefsSection = null }
    var query by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    fun set(n: AppSettings) = state.updateSettings(n)

    val prefs = buildList {
        add(Pref(Section.General, "Save automatically", "Saves a request, or a workflow, just before it is sent or run.") { Toggle(s.autoSave, "pref-autosave") { set(s.copy(autoSave = it)) } })

        add(Pref(Section.Appearance, "Theme", if (s.theme == ThemeMode.System) "Follows your operating system's light / dark setting (currently ${if (p.dark) "dark" else "light"})." else "Light or dark interface, or follow the operating system.") {
            Segmented(ThemeMode.entries.map { it.label }, s.theme.label, "pref-theme") { l -> set(s.copy(theme = ThemeMode.entries.first { it.label == l })) }
        })
        add(Pref(Section.Appearance, "Interface size", "Scales text and controls (also ⌘+ and ⌘−).") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Btn("−", { set(s.copy(fontSize = (s.fontSize - 1).coerceIn(10, 20))) }, Modifier.testTag("pref-size-down"), enabled = s.fontSize > 10)
                T("${s.fontSize * 100 / 13}%", mono = true, modifier = Modifier.width(48.dp).testTag("pref-size"), weight = FontWeight.SemiBold)
                Btn("+", { set(s.copy(fontSize = (s.fontSize + 1).coerceIn(10, 20))) }, Modifier.testTag("pref-size-up"), enabled = s.fontSize < 20)
                if (s.fontSize != 13) T("Reset", color = p.accent, size = 12, modifier = Modifier.clickable { set(s.copy(fontSize = 13)) }.pointerHoverIcon(PointerIcon.Hand).padding(4.dp))
            }
        })
        add(Pref(Section.Appearance, "Request layout", "Show the response below the request, or beside it.") {
            Segmented(listOf("Stacked", "Side by side"), if (s.verticalLayout) "Stacked" else "Side by side", "pref-layout") { set(s.copy(verticalLayout = it == "Stacked")) }
        })

        add(Pref(Section.Requests, "Timeout", "How long to wait for a response. 0 means no limit.") { NumberField(s.timeoutMs, "ms") { set(s.copy(timeoutMs = it)) } })
        add(Pref(Section.Requests, "Follow redirects", "Automatically follow 3xx responses to the new location.") { Toggle(s.followRedirects, "pref-redirects") { set(s.copy(followRedirects = it)) } })
        if (s.followRedirects) add(Pref(Section.Requests, "Maximum redirects", "Stop after this many hops, to avoid redirect loops.") { NumberField(s.maxRedirects, "hops", 80.dp) { set(s.copy(maxRedirects = it.coerceAtMost(50))) } })
        add(Pref(Section.Requests, "User-Agent", "Sent with every request unless the request sets its own.") { Field(s.userAgent, { set(s.copy(userAgent = it)) }, Modifier.width(260.dp), highlightVars = false) })

        add(Pref(Section.Network, "Verify SSL certificates", if (s.sslVerify) "Reject servers whose certificate cannot be verified." else "Off: connections are not verified. Use only for servers you trust, such as local development.", if (s.sslVerify) null else p.err) {
            Toggle(s.sslVerify, "pref-ssl") { set(s.copy(sslVerify = it)) }
        })
        add(Pref(Section.Network, "Cookies", "Cookies are kept for this session and sent back to the sites that set them.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Manage…", { state.modal = Modal.Cookies })
                Btn("Clear all", { HttpExecutor.cookies.cookieStore.removeAll(); state.showToast("Cookies cleared") })
            }
        })

        add(Pref(Section.Proxy, "Use an HTTP proxy", "Route every request through the proxy below.") { Toggle(s.proxyEnabled, "pref-proxy") { set(s.copy(proxyEnabled = it)) } })
        if (s.proxyEnabled) {
            add(Pref(Section.Proxy, "Proxy host", "Host name or address of the proxy.") { Field(s.proxyHost, { set(s.copy(proxyHost = it)) }, Modifier.width(260.dp), "proxy.example.com", highlightVars = false) })
            add(Pref(Section.Proxy, "Proxy port") { NumberField(s.proxyPort, "", 90.dp) { set(s.copy(proxyPort = it.coerceAtMost(65535))) } })
        }

        listOf(
            "Command palette" to "⌘K", "Send request" to "⌘↵", "Save" to "⌘S", "Save all" to "⌘⇧S", "Close tab" to "⌘W", "New request" to "⌘N",
            "Show / hide sidebar" to "⌘B", "Focus the URL bar" to "⌘L", "Switch request layout" to "⌘⇧L", "Copy as cURL" to "⌘⇧C",
            "Previous / next tab" to "⌘⇧[  ⌘⇧]", "Zoom in / out / reset" to "⌘+  ⌘−  ⌘0", "Preferences" to "⌘,",
        ).forEach { (what, keys) ->
            add(Pref(Section.Shortcuts, what) { Box(Modifier.clip(RoundedCornerShape(5.dp)).background(p.bg).border(1.dp, p.border, RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) { T(keys, mono = true, size = 12) } })
        }

        add(Pref(Section.Data, "Storage location", AppStore.dir.toString()) { Btn("Show in folder", { Platform.reveal(AppStore.dir) }) })
        add(Pref(Section.Data, "Reset preferences", "Restore every setting to its default. Your collections and environments are not touched.") {
            if (!confirmReset) Btn("Reset…", { confirmReset = true }, Modifier.testTag("pref-reset"))
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                T("Are you sure?", color = p.err, size = 12)
                Btn("Cancel", { confirmReset = false })
                Btn("Reset", { set(AppSettings()); confirmReset = false; state.showToast("Preferences reset to defaults") }, Modifier.testTag("pref-reset-confirm"), danger = true)
            }
        })
    }

    val q = query.trim()
    val searching = q.isNotEmpty()
    val shown = if (searching) prefs.filter { it.title.contains(q, true) || it.desc.contains(q, true) || it.section.label.contains(q, true) } else prefs.filter { it.section == section }

    ModalBox("Preferences", { state.modal = null }, 900.dp, 620.dp, actions = {
        T("Changes are saved as you make them.", color = p.muted, size = 12, modifier = Modifier.weight(1f))
        Btn("Done", { state.modal = null }, primary = true)
    }) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.width(190.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Field(query, { query = it }, Modifier.fillMaxWidth(), "Search settings", highlightVars = false)
                Spacer(Modifier.height(6.dp))
                Section.entries.forEach { sec -> NavItem(sec.label, !searching && sec == section, "nav-${sec.label}") { query = ""; section = sec } }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(p.border))
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!searching) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    T(section.label, size = 18, weight = FontWeight.Bold)
                    T(section.blurb, color = p.muted, size = 12)
                }
                else T(if (shown.isEmpty()) "No settings match “$q”." else "${shown.size} setting${if (shown.size == 1) "" else "s"} matching “$q”", color = p.muted, size = 13)
                val groups = shown.groupBy { it.section }
                groups.forEach { (sec, rows) ->
                    if (searching) SectionLabel(sec.label.uppercase())
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, p.border, RoundedCornerShape(10.dp)).background(p.field.copy(alpha = 0.5f))) {
                        rows.forEachIndexed { i, r ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    T(r.title, weight = FontWeight.SemiBold)
                                    if (r.desc.isNotBlank()) T(r.desc, color = r.descColor ?: p.muted, size = 12, mono = sec == Section.Data && r.title == "Storage location")
                                }
                                r.control()
                            }
                        }
                    }
                }
            }
        }
    }
}
