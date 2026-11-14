package dev.prateekthakur.openrest.features.developer

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.features.shell.*
import dev.prateekthakur.openrest.platform.*
import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.prateekthakur.openrest.features.shell.AppInfo
import dev.prateekthakur.openrest.platform.Platform

/** Who made OpenRest, and where to find the developer and the project on GitHub. */
@Composable
fun DeveloperDialog(state: AppState) {
    val p = LocalPal.current
    ModalBox("Developer", { state.modal = null }, 560.dp, actions = { Btn("Close", { state.modal = null }, primary = true) }) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(p.field).border(1.dp, p.border, CircleShape), contentAlignment = Alignment.Center) {
                Ico(Ic.GitHub, tint = p.text, size = 32.dp)
            }
            Column {
                T(AppInfo.VENDOR, size = 18, weight = FontWeight.Bold, modifier = Modifier.testTag("developer-name"))
                T("Developer of ${AppInfo.NAME}", color = p.muted)
            }
        }
        if (AppInfo.DEVELOPER_URL.isNotBlank()) LinkRow("Profile", AppInfo.DEVELOPER_URL, "developer-profile")
        if (AppInfo.HOMEPAGE.isNotBlank()) LinkRow("Project repository", AppInfo.HOMEPAGE, "developer-repo")
        T("OpenRest is built in the open. Bug reports, ideas and pull requests are welcome on GitHub.", color = p.muted, size = 12)
    }
}

@Composable
private fun LinkRow(label: String, url: String, tag: String) {
    val p = LocalPal.current
    var copied by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(label.uppercase())
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(6.dp)).padding(start = 10.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            T(url, mono = true, size = 12, modifier = Modifier.weight(1f))
            Btn(if (copied) "Copied" else "Copy", { Platform.copy(url); copied = true }, modifier = Modifier.testTag("$tag-copy"))
            Btn("Open", { Platform.openUrl(url) }, primary = true, icon = Ic.Open, modifier = Modifier.testTag("$tag-open"))
        }
    }
}
