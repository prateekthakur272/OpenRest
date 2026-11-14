package dev.prateekthakur.openrest.features.dialogs

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.http.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.features.request.*
import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ---------------- settings / cookies / variables ----------------

@Composable
internal fun CookiesDialog(state: AppState) {
    val p = LocalPal.current
    var tick by remember { mutableStateOf(0) }
    val cookies = remember(tick) { HttpExecutor.cookies.cookieStore.cookies.toList() }
    ModalBox("Cookies", { state.modal = null }, 640.dp, 440.dp, actions = {
        Btn("Clear All", { HttpExecutor.cookies.cookieStore.removeAll(); tick++ }, danger = true)
        Btn("Close", { state.modal = null }, primary = true)
    }) {
        if (cookies.isEmpty()) T("No cookies stored in this session.", color = p.muted)
        else LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cookies) { c ->
                Column {
                    T("${c.name} = ${c.value}", mono = true, weight = FontWeight.SemiBold, maxLines = 2)
                    T("${c.domain ?: ""}  ${c.path ?: "/"}  ${if (c.secure) "Secure " else ""}${if (c.isHttpOnly) "HttpOnly" else ""}", color = p.muted, size = 12, mono = true)
                }
            }
        }
    }
}

// ---------------- collection / folder settings ----------------

@Composable
internal fun NodeSettingsDialog(state: AppState, m: Modal.NodeSettings) {
    val p = LocalPal.current
    val col = state.collection(m.collection) ?: run { state.modal = null; return }
    val isCol = m.node == col.path
    fun findFolder(nodes: List<Node>): FolderNode? = nodes.firstNotNullOfOrNull { n -> if (n is FolderNode) (if (n.path == m.node) n else findFolder(n.children)) else null }
    val initial = if (isCol) col.config else (findFolder(col.children)?.config ?: NodeConfig())
    var cfg by remember { mutableStateOf(initial) }
    var tab by remember { mutableStateOf("Headers") }
    var scriptTab by remember { mutableStateOf("Pre Request") }
    var varsTab by remember { mutableStateOf("Pre Request") }
    val title = if (isCol) "Collection Settings — ${col.name}" else "Folder Settings — ${cfg.name}"
    ModalBox(title, { state.modal = null }, 760.dp, 560.dp, actions = {
        Btn("Cancel", { state.modal = null })
        Btn("Save", { state.saveNodeConfig(col.path, m.node, cfg); state.modal = null }, primary = true)
    }) {
        Tabs(listOf("Headers", "Auth", "Vars", "Script", "Tests", "Docs"), tab, { tab = it })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                "Headers" -> ScrollBox { KVTable(cfg.headers, { cfg = cfg.copy(headers = it) }, keyHint = "Header", valueHint = "Value") }
                "Auth" -> ScrollBox {
                    val modes = if (isCol) AuthMode.entries.filter { it != AuthMode.Inherit } else AuthMode.entries.filter { it != AuthMode.None }
                    // For folders, "Inherit" is stored as no auth so the parent's auth applies.
                    val shown = if (!isCol && cfg.auth.mode == AuthMode.None) cfg.auth.copy(mode = AuthMode.Inherit) else cfg.auth
                    AuthEditor(shown, { a -> cfg = cfg.copy(auth = if (!isCol && a.mode == AuthMode.Inherit) a.copy(mode = AuthMode.None) else a) }, modes, Modifier.padding(8.dp))
                }
                "Vars" -> Column {
                    Tabs(listOf("Pre Request", "Post Response"), varsTab, { varsTab = it })
                    ScrollBox {
                        if (varsTab == "Pre Request") KVTable(cfg.preVars, { cfg = cfg.copy(preVars = it) }, keyHint = "Name", valueHint = "Value")
                        else KVTable(cfg.postVars, { cfg = cfg.copy(postVars = it) }, keyHint = "Name", valueHint = "Expression  (res.body.id)")
                    }
                }
                "Script" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tabs(listOf("Pre Request", "Post Response"), scriptTab, { scriptTab = it })
                    if (scriptTab == "Pre Request") CodeEditor(cfg.preScript, { cfg = cfg.copy(preScript = it) }, Modifier.fillMaxSize(), "js")
                    else CodeEditor(cfg.postScript, { cfg = cfg.copy(postScript = it) }, Modifier.fillMaxSize(), "js")
                }
                "Tests" -> CodeEditor(cfg.tests, { cfg = cfg.copy(tests = it) }, Modifier.fillMaxSize(), "js")
                "Docs" -> CodeEditor(cfg.docs, { cfg = cfg.copy(docs = it) }, Modifier.fillMaxSize(), "text")
            }
        }
        T("Applies to every request ${if (isCol) "in this collection" else "inside this folder"}. Collection → folder → request order.", color = p.muted, size = 12)
    }
}
