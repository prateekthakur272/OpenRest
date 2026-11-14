package dev.prateekthakur.openrest.features.sidebar

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.http.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.platform.*
import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CollectionRow(state: AppState, c: Collection, drag: DragCtx, cursor: Boolean) {
    val p = LocalPal.current
    val key = c.path.toString()
    val open = state.expanded[key] == true
    val env = state.envName(c.path)
    TreeRow(
        "c:" + c.path, 0, false,
        { state.expanded[key] = !open; state.selectedCollection = c.path },
        listOf(
            MenuItem("New Request") { state.modal = Modal.NewRequest(c.path, c.path) },
            MenuItem("New Folder") { state.promptNewFolder(c.path, c.path) },
            MenuItem("New Workflow") { state.promptNewWorkflow(c.path) },
            MenuItem(null),
            MenuItem("Run Collection") { state.openRunner(c.path, null) },
            MenuItem("Environments") { state.modal = Modal.Envs(c.path) },
            MenuItem("Settings") { state.modal = Modal.NodeSettings(c.path, c.path) },
            MenuItem("Variables") { state.modal = Modal.Vars(c.path) },
            MenuItem(null),
            MenuItem("Rename") { state.promptRename(c.path, null) },
            MenuItem("Export…") { state.modal = Modal.Export(c.path) },
            MenuItem("Show in Folder") { Platform.reveal(c.path) },
            MenuItem("Reload") { state.reload(c.path) },
            MenuItem("Close Collection", danger = true) { state.closeCollection(c.path) },
        ),
        drag = drag, cursor = cursor, height = 34, quickAdd = { state.modal = Modal.NewRequest(c.path, c.path) },
    ) { hovered ->
        Chevron(open)
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(p.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Text(
                c.name.firstOrNull()?.uppercase() ?: "?", color = p.accent, fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 11.sp, lineHeight = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both),
                ),
            )
        }
        T(c.name, weight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (!hovered) {
            if (env != null) Box(Modifier.clip(RoundedCornerShape(4.dp)).background(p.ok.copy(alpha = 0.15f)).padding(horizontal = 5.dp, vertical = 1.dp)) { T(env, color = p.ok, size = 10, maxLines = 1) }
            T(countReqs(c.children).toString(), color = p.muted, size = 11)
        }
    }
}

@Composable
internal fun FolderRow(state: AppState, row: SRow.Folder, drag: DragCtx, cursor: Boolean) {
    val p = LocalPal.current
    val f = row.f
    val key = f.path.toString()
    val open = state.expanded[key] == true || state.sidebarFilter.isNotBlank()
    TreeRow(
        "f:" + f.path, row.depth, false, { state.expanded[key] = !(state.expanded[key] == true) },
        listOf(
            MenuItem("New Request") { state.modal = Modal.NewRequest(row.col, f.path) },
            MenuItem("New Folder") { state.promptNewFolder(row.col, f.path) },
            MenuItem(null),
            MenuItem("Run Folder") { state.openRunner(row.col, f.path) },
            MenuItem("Settings") { state.modal = Modal.NodeSettings(row.col, f.path) },
            MenuItem(null),
            MenuItem("Rename") { state.promptRename(row.col, f) },
            MenuItem("Clone") { state.promptClone(row.col, f) },
            MenuItem("Show in Folder") { Platform.reveal(f.path) },
            MenuItem("Delete", danger = true) { state.promptDelete(row.col, f) },
        ),
        drag = drag, cursor = cursor, quickAdd = { state.modal = Modal.NewRequest(row.col, f.path) },
    ) { hovered ->
        Chevron(open)
        FolderIcon(open)
        if (state.renaming == f.path) InlineRename(f.name, Modifier.weight(1f)) { n -> state.renaming = null; if (n != null && n != f.name) state.renameNode(row.col, f, n) }
        else T(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (!hovered) T(countReqs(f.children).toString(), color = p.muted, size = 11)
    }
}

@Composable
internal fun WorkflowRow(state: AppState, row: SRow.Wf, cursor: Boolean) {
    val p = LocalPal.current
    val tab = state.workflowTab(row.col, row.w.name)
    TreeRow(
        "w:" + row.col + "|" + row.w.name, 1, state.activeTab?.let { it === tab } == true, { state.openWorkflow(row.col, row.w.name) },
        listOf(
            MenuItem("Open") { state.openWorkflow(row.col, row.w.name) },
            MenuItem("Run") { state.openWorkflow(row.col, row.w.name); state.workflowTab(row.col, row.w.name)?.let { state.runWorkflow(it) } },
            MenuItem(null),
            MenuItem("Rename") { state.promptRenameWorkflow(row.col, row.w.name) },
            MenuItem("Clone") { state.cloneWorkflow(row.col, row.w.name) },
            MenuItem("Delete", danger = true) { state.promptDeleteWorkflow(row.col, row.w.name) },
        ),
        drag = DragCtx({}, {}, {}, null, false), cursor = cursor,
    ) { _ ->
        Spacer(Modifier.width(6.dp))
        Box(Modifier.width(METHOD_SLOT), contentAlignment = Alignment.CenterStart) { Ico(Ic.Workflow, tint = p.accent, size = 15.dp) }
        T(row.w.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (tab?.workflow?.running == true) androidx.compose.material3.CircularProgressIndicator(Modifier.size(11.dp), color = p.accent, strokeWidth = 1.5.dp)
        else if (tab?.dirty == true) Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(p.accent))
    }
}

@Composable
internal fun RequestRow(state: AppState, row: SRow.Req, drag: DragCtx, cursor: Boolean) {
    val p = LocalPal.current
    val r = row.r
    val reqs = row.siblings.filterIsInstance<RequestNode>()
    val ri = reqs.indexOf(r)
    val tab = state.tabs.firstOrNull { it.filePath == r.path }
    TreeRow(
        "r:" + r.path, row.depth, state.activeTab?.filePath == r.path, { state.openRequest(row.col, r) },
        listOf(
            MenuItem("Open") { state.openRequest(row.col, r) },
            MenuItem("Rename") { state.promptRename(row.col, r) },
            MenuItem("Clone") { state.promptClone(row.col, r) },
            MenuItem("Move Up") { state.move(row.col, row.siblings, row.index, -1) }.takeIf { ri > 0 } ?: MenuItem(null),
            MenuItem("Move Down") { state.move(row.col, row.siblings, row.index, 1) }.takeIf { ri in 0 until reqs.size - 1 } ?: MenuItem(null),
            MenuItem("Show in Folder") { Platform.reveal(r.path) },
            MenuItem("Delete", danger = true) { state.promptDelete(row.col, r) },
        ).let { l -> l.filterIndexed { i, m -> !(m.label == null && (i == 0 || l[i - 1].label == null)) } },
        drag = drag, cursor = cursor,
    ) { hovered ->
        Spacer(Modifier.width(6.dp))
        MethodPill(r.request.method)
        if (state.renaming == r.path) InlineRename(r.name, Modifier.weight(1f)) { n -> state.renaming = null; if (n != null && n != r.name) state.renameNode(row.col, r, n) }
        else T(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (!hovered && tab != null) {
            when {
                tab.sending -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(11.dp), color = p.accent, strokeWidth = 1.5.dp)
                tab.dirty -> Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(p.accent))
                else -> tab.result?.response?.takeIf { it.error == null }?.let { Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(statusColor(it.status, p))) }
            }
            Spacer(Modifier.width(2.dp))
        }
    }
}
