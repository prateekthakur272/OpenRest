package dev.prateekthakur.openrest.features.sidebar

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.theme.*
import dev.prateekthakur.openrest.utils.*

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ColumnScope.HistoryView(state: AppState) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel("RECENT REQUESTS", Modifier.weight(1f))
        if (state.history.isNotEmpty()) IconBtn(Ic.Delete, { state.history.clear() }, size = 16)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
    if (state.history.isEmpty()) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(20.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Ico(Ic.History, tint = p.muted.copy(alpha = 0.5f), size = 44.dp)
            Spacer(Modifier.height(10.dp))
            T("Nothing sent yet", weight = FontWeight.SemiBold)
            T("Requests you send show up here with their full response.", color = p.muted, size = 12, modifier = Modifier.padding(top = 6.dp))
        }
        return
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
        items(state.history.toList()) { e ->
            val src = remember { MutableInteractionSource() }
            val hov by src.collectIsHoveredAsState()
            Column(
                Modifier.fillMaxWidth().background(if (hov) p.hover else Color.Transparent).hoverable(src).clickable { state.openHistory(e) }
                    .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 12.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MethodPill(e.method)
                    T(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(4.dp)).background((if (e.status == 0) p.err else statusColor(e.status, p)).copy(alpha = 0.16f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                        T(if (e.status == 0) "ERR" else e.status.toString(), color = if (e.status == 0) p.err else statusColor(e.status, p), size = 10, weight = FontWeight.Bold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    T(e.url, color = p.muted, size = 11, mono = true, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    T("${e.timeMs}ms · ${timeAgo(e.time)}", color = p.muted, size = 10)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.border.copy(alpha = 0.5f)))
        }
    }
}
