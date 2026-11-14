package dev.prateekthakur.openrest.features.onboarding

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.features.shell.*
import dev.prateekthakur.openrest.platform.*
import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.prateekthakur.openrest.core.model.ThemeMode
import dev.prateekthakur.openrest.features.shell.AppInfo
import dev.prateekthakur.openrest.platform.Platform

private enum class Step(val title: String) { Welcome("Welcome"), Look("Make it yours"), Tips("Quick tour"), Start("Get started") }

/** First-run walkthrough: welcome, look and feel, a few tips, then how to start. Skipping or closing it counts as finished. */
@Composable
fun OnboardingDialog(state: AppState) {
    val p = LocalPal.current
    var index by remember { mutableStateOf(0) }
    val steps = Step.entries
    val step = steps[index]
    val last = index == steps.lastIndex
    ModalBox("Welcome to ${AppInfo.NAME}", { state.finishOnboarding() }, 640.dp, 520.dp, actions = {
        if (!last) Btn("Skip", { state.finishOnboarding() }, modifier = Modifier.testTag("onb-skip"))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) { Dots(index, steps.size) }
        if (index > 0) Btn("Back", { index-- }, modifier = Modifier.testTag("onb-back"))
        if (!last) Btn("Next", { index++ }, primary = true, modifier = Modifier.testTag("onb-next"))
        else Btn("Start with an empty workspace", { state.finishOnboarding() }, modifier = Modifier.testTag("onb-done"))
    }) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (step) {
                Step.Welcome -> Welcome()
                Step.Look -> Look(state)
                Step.Tips -> Tips()
                Step.Start -> Start(state)
            }
        }
    }
}

@Composable
private fun Dots(current: Int, count: Int) {
    val p = LocalPal.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { Box(Modifier.size(if (it == current) 18.dp else 7.dp, 7.dp).clip(RoundedCornerShape(4.dp)).background(if (it == current) p.accent else p.border)) }
    }
}

@Composable
private fun Heading(title: String, sub: String) {
    val p = LocalPal.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        T(title, size = 20, weight = FontWeight.Bold)
        T(sub, color = p.muted)
    }
}

@Composable
private fun Welcome() {
    val p = LocalPal.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
        Ico(Ic.Logo, tint = p.accent, size = 64.dp)
        T(AppInfo.NAME, size = 28, weight = FontWeight.Bold)
        T(AppInfo.TAGLINE, color = p.muted, modifier = Modifier.padding(horizontal = 24.dp), size = 14)
        Spacer(Modifier.height(10.dp))
        listOf(
            Ic.Folder to "Requests are plain YAML files in folders you choose. Keep them in git.",
            Ic.Import to "Bring your work from Postman, Insomnia, Bruno, OpenAPI or cURL.",
            Ic.Workflow to "Chain requests into workflows with loops, conditions and passed-on values.",
        ).forEach { (icon, text) ->
            Row(Modifier.widthIn(max = 460.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(icon, tint = p.accent, size = 20.dp); T(text)
            }
        }
    }
}

@Composable
private fun Look(state: AppState) {
    val p = LocalPal.current
    Heading("Make it yours", "You can change all of this later in Preferences.")
    SectionLabel("THEME")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { m -> Pill(m.label, state.settings.theme == m, { state.updateSettings(state.settings.copy(theme = m)) }, Modifier.testTag("onb-theme-${m.label}")) }
    }
    SectionLabel("LAYOUT")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Request above response", state.settings.verticalLayout, { state.updateSettings(state.settings.copy(verticalLayout = true)) }, Modifier.testTag("onb-layout-stacked"))
        Pill("Side by side", !state.settings.verticalLayout, { state.updateSettings(state.settings.copy(verticalLayout = false)) }, Modifier.testTag("onb-layout-side"))
    }
    SectionLabel("TEXT SIZE")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Btn("−", { state.zoom(-1) }); T("${state.settings.fontSize * 100 / 13}%", mono = true); Btn("+", { state.zoom(1) })
    }
}

@Composable
private fun Tips() {
    val p = LocalPal.current
    val cmd = if (Platform.isMac) "⌘" else "Ctrl+"
    Heading("A quick tour", "The few things worth knowing on day one.")
    listOf(
        "${cmd}K" to "Command palette: search every request and run any command.",
        "${cmd}↵".replace("↵", if (Platform.isMac) "↵" else "Enter") to "Send the request you are editing.",
        "{{name}}" to "Use an environment or collection variable in a URL, header or body.",
        "${cmd}S" to "Save the current request or workflow.",
        "Help ▸ Shortcuts" to "See every keyboard shortcut.",
    ).forEach { (k, d) ->
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(130.dp)) { T(k, mono = true, size = 12, modifier = Modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, p.border, RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) }
            T(d, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Start(state: AppState) {
    Heading("Get started", "Pick how you want to begin. You can do the others any time from the sidebar.")
    StartCard(Ic.Add, "Create a collection", "A new folder for your requests.", "onb-create") { state.finishOnboarding(Modal.NewCollection) }
    StartCard(Ic.FolderOpen, "Open a folder", "Use a collection you already have on disk.", "onb-open") { state.finishOnboarding(Modal.OpenCollection) }
    StartCard(Ic.Import, "Import", "Postman, Insomnia, Bruno, OpenAPI or a cURL command.", "onb-import") { state.finishOnboarding(Modal.Import) }
}

@Composable
private fun StartCard(icon: ImageVector, title: String, sub: String, tag: String, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().testTag(tag).clip(RoundedCornerShape(8.dp)).border(1.dp, p.border, RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, tint = p.accent, size = 24.dp)
        Column(Modifier.weight(1f)) { T(title, weight = FontWeight.SemiBold); T(sub, color = p.muted, size = 12) }
        T("›", color = p.muted, size = 18)
    }
}
