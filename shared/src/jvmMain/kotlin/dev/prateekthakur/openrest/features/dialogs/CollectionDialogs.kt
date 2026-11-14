package dev.prateekthakur.openrest.features.dialogs

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.platform.*
import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.nio.file.Paths

@Composable
internal fun NewRequestDialog(state: AppState, m: Modal.NewRequest) {
    var name by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("GET") }
    var url by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    val isCurl = url.trimStart().startsWith("curl ")
    fun ok() {
        state.modal = null
        if (isCurl) state.createRequestFromCurl(m.collection, m.dir, url) else state.createRequest(m.collection, m.dir, name, method, url)
    }
    ModalBox("New Request", { state.modal = null }, 560.dp, actions = {
        Btn("Cancel", { state.modal = null })
        Btn("Create", ::ok, primary = true, enabled = isCurl || name.isNotBlank())
    }) {
        SectionLabel("NAME")
        Field(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "Get users", focusRequester = fr, highlightVars = false)
        SectionLabel("URL  (or paste a cURL command)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dropdown(method, HTTP_METHODS, { method = it }, color = methodColor(method, LocalPal.current), weight = FontWeight.Bold)
            Field(url, { url = it }, Modifier.weight(1f), "https://api.example.com/users", mono = true, onEnter = { if (isCurl || name.isNotBlank()) ok() }, highlightVars = false)
        }
    }
}

@Composable
internal fun NewCollectionDialog(state: AppState) {
    var name by remember { mutableStateOf("") }
    var loc by remember { mutableStateOf(defaultDir) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    fun ok() { state.modal = null; state.createCollection(Paths.get(loc.trim()).also { runCatching { java.nio.file.Files.createDirectories(it) } }, name.trim()) }
    ModalBox("New Collection", { state.modal = null }, 560.dp, actions = {
        Btn("Cancel", { state.modal = null })
        Btn("Create", ::ok, primary = true, enabled = name.isNotBlank() && loc.isNotBlank())
    }) {
        SectionLabel("NAME")
        Field(name, { name = it }, Modifier.fillMaxWidth(), placeholder = "My API", focusRequester = fr, highlightVars = false)
        SectionLabel("LOCATION")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(loc, { loc = it }, Modifier.weight(1f), mono = true, highlightVars = false)
            Btn("Browse…", icon = Ic.Folder, onClick = { Platform.pickDirectory("Choose location")?.let { loc = it.toString() } })
        }
        T("A folder is created at the location above. Requests are stored as plain YAML files that work well with git. Export to Postman, Bruno, OpenAPI and more at any time.", color = LocalPal.current.muted, size = 12)
    }
}

@Composable
internal fun OpenCollectionDialog(state: AppState) {
    var path by remember { mutableStateOf("") }
    ModalBox("Open Collection", { state.modal = null }, 560.dp, actions = {
        Btn("Cancel", { state.modal = null })
        Btn("Open", { state.modal = null; state.openCollection(Paths.get(path.trim())) }, primary = true, enabled = path.isNotBlank())
    }) {
        SectionLabel("COLLECTION FOLDER (contains openrest.yaml)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(path, { path = it }, Modifier.weight(1f), mono = true, highlightVars = false)
            Btn("Browse…", icon = Ic.Folder, onClick = { Platform.pickDirectory("Choose collection folder")?.let { path = it.toString() } })
        }
    }
}
