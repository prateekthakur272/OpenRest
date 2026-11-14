package dev.prateekthakur.openrest.features.dialogs

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.codegen.*
import dev.prateekthakur.openrest.core.interop.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.platform.*
import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.nio.file.Paths

// ---------------- code generation ----------------

@Composable
internal fun CodegenDialog(state: AppState, m: Modal.Codegen) {
    val tab = state.tabs.firstOrNull { it.id == m.tabId } ?: run { state.modal = null; return }
    var lang by remember { mutableStateOf(CodeGen.languages.first()) }
    val prepared = remember(tab.draft, lang) { state.prepareFor(tab) }
    val code = prepared.fold({ CodeGen.generate(lang, it) }, { "// ${it.message}" })
    ModalBox("Generate Code", { state.modal = null }, 760.dp, 560.dp, actions = {
        Btn("Close", { state.modal = null })
        Btn("Copy", { Platform.copy(code); state.showToast("Code copied") }, primary = true, icon = Ic.Copy)
    }) {
        Dropdown(lang, CodeGen.languages, { lang = it })
        CodeEditor(code, {}, Modifier.weight(1f).fillMaxWidth(), CodeGen.langId(lang), readOnly = true)
    }
}

// ---------------- import ----------------

@Composable
internal fun ImportDialog(state: AppState) {
    val p = LocalPal.current
    val kinds = listOf("Postman Collection", "Insomnia", "OpenAPI / Swagger", "Bruno Collection (folder)", "OpenRest Bundle (YAML)", "OpenRest Collection (folder)", "cURL", "Postman Environment", "Postman Environment (Global)")
    var kind by remember { mutableStateOf(kinds[0]) }
    var file by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var loc by remember { mutableStateOf(defaultDir) }
    val needsFile = kind != "cURL"
    val usesLocation = kind in listOf("Postman Collection", "Insomnia", "OpenAPI / Swagger", "Bruno Collection (folder)", "OpenRest Bundle (YAML)")
    ModalBox("Import", { state.modal = null }, 640.dp, actions = {
        Btn("Cancel", { state.modal = null })
        Btn("Import", icon = Ic.Import, onClick = {
            state.importFrom(kind, file.takeIf { it.isNotBlank() }?.let { Paths.get(it.trim()) }, text, Paths.get(loc.trim()))
        }, primary = true, enabled = if (kind == "cURL") text.isNotBlank() else file.isNotBlank() || text.isNotBlank())
    }) {
        Dropdown(kind, kinds, { kind = it; file = "" }, Modifier.fillMaxWidth())
        if (needsFile) {
            SectionLabel(if (kind.contains("folder")) "COLLECTION FOLDER" else "FILE")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(file, { file = it }, Modifier.weight(1f), mono = true, highlightVars = false)
                Btn("Browse…", icon = Ic.Folder, onClick = { (if (kind.contains("folder")) Platform.pickDirectory("Choose folder") else Platform.pickFile("Choose file"))?.let { file = it.toString() } })
            }
        }
        if (!kind.contains("folder")) {
            SectionLabel(if (kind == "cURL") "CURL COMMAND" else "OR PASTE CONTENT")
            AreaField(text, { text = it }, Modifier.fillMaxWidth().height(if (kind == "cURL") 160.dp else 90.dp), "curl -X POST https://… -H 'Content-Type: application/json' -d '{…}'")
        }
        if (usesLocation) {
            SectionLabel("CREATE COLLECTION IN")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(loc, { loc = it }, Modifier.weight(1f), mono = true, highlightVars = false)
                Btn("Browse…", icon = Ic.Folder, onClick = { Platform.pickDirectory("Choose location")?.let { loc = it.toString() } })
            }
        }
        T(
            when (kind) {
                "cURL" -> "Adds the request to the currently selected collection."
                "Postman Environment" -> "Adds the environment to the currently selected collection."
                "Postman Environment (Global)" -> "Adds a global environment, available to every collection."
                "Bruno Collection (folder)" -> "Converts a Bruno collection (.bru files with bruno.json, or the newer YAML layout with opencollection.yml) into a new OpenRest collection."
                "OpenRest Collection (folder)" -> "Opens an existing OpenRest collection folder (contains openrest.yaml)."
                "OpenRest Bundle (YAML)" -> "Creates a collection from a single-file bundle exported by OpenRest."
                "OpenAPI / Swagger" -> "JSON or YAML. Requests are grouped by tag; the server URL becomes {{baseUrl}} in a Default environment."
                else -> "Scripts using pm.* are converted to the bru/expect API where possible."
            },
            color = p.muted, size = 12,
        )
    }
}

// ---------------- export ----------------

@Composable
internal fun ExportDialog(state: AppState, m: Modal.Export) {
    val p = LocalPal.current
    val col = state.collection(m.collection) ?: run { state.modal = null; return }
    var fmt by remember { mutableStateOf(Exporters.formats.first()) }
    var dest by remember { mutableStateOf("") }
    val suggested = Collections.sanitize(col.name) + fmt.ext
    fun browse() {
        (if (fmt.isDirectory) Platform.pickDirectory("Choose destination folder") else Platform.saveFile("Export ${col.name}", suggested))?.let { dest = it.toString() }
    }
    ModalBox("Export — ${col.name}", { state.modal = null }, 640.dp, actions = {
        Btn("Cancel", { state.modal = null })
        Btn("Export", icon = Ic.Export, onClick = { state.modal = null; state.exportCollection(col.path, fmt.id, Paths.get(dest.trim())) }, primary = true, enabled = dest.isNotBlank())
    }) {
        SectionLabel("FORMAT")
        Dropdown(fmt.label, Exporters.formats.map { it.label }, { l -> fmt = Exporters.formats.first { it.label == l }; dest = "" }, Modifier.fillMaxWidth())
        T(fmt.hint, color = p.muted, size = 12)
        SectionLabel(if (fmt.isDirectory) "DESTINATION FOLDER" else "DESTINATION FILE")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(dest, { dest = it }, Modifier.weight(1f), if (fmt.isDirectory) "/path/to/folder" else suggested, mono = true, highlightVars = false)
            Btn("Browse…", ::browse, icon = Ic.Folder)
        }
    }
}
