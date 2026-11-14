package dev.prateekthakur.openrest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.nio.file.Paths

private val defaultDir: String get() = Paths.get(System.getProperty("user.home"), "OpenRest").toString()

@Composable
fun Modals(state: AppState) {
    when (val m = state.modal) {
        null -> {}
        is Modal.Prompt -> PromptDialog(state, m)
        is Modal.Confirm -> ModalBox(m.title, { state.modal = null }, 420.dp, actions = {
            Btn("Cancel", { state.modal = null })
            Btn(m.confirm, { state.modal = null; m.onOk() }, primary = true)
        }) { T(m.message) }
        is Modal.AddStep -> AddStepDialog(state, m)
        is Modal.ViewResponse -> ResponseDialog(state, m)
        is Modal.NewRequest -> NewRequestDialog(state, m)
        Modal.NewCollection -> NewCollectionDialog(state)
        Modal.OpenCollection -> OpenCollectionDialog(state)
        is Modal.Envs -> EnvsDialog(state, m)
        is Modal.Codegen -> CodegenDialog(state, m)
        Modal.Import -> ImportDialog(state)
        Modal.Settings -> PreferencesDialog(state)
        Modal.About -> AboutDialog(state)
        Modal.Cookies -> CookiesDialog(state)
        is Modal.NodeSettings -> NodeSettingsDialog(state, m)
        is Modal.Vars -> VarsDialog(state, m)
        is Modal.Export -> ExportDialog(state, m)
        Modal.Palette -> Palette(state)
    }
}

@Composable
private fun PromptDialog(state: AppState, m: Modal.Prompt) {
    var v by remember { mutableStateOf(m.initial) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    fun ok() { if (v.isNotBlank()) { state.modal = null; m.onOk(v.trim()) } }
    ModalBox(m.title, { state.modal = null }, 420.dp, actions = {
        Btn("Cancel", { state.modal = null })
        Btn(m.confirm, ::ok, primary = true, enabled = v.isNotBlank())
    }) {
        SectionLabel(m.label.uppercase())
        Field(v, { v = it }, Modifier.fillMaxWidth(), onEnter = ::ok, focusRequester = fr, highlightVars = false)
    }
}

@Composable
private fun NewRequestDialog(state: AppState, m: Modal.NewRequest) {
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
private fun NewCollectionDialog(state: AppState) {
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
private fun OpenCollectionDialog(state: AppState) {
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

// ---------------- environments ----------------

@Composable
private fun EnvsDialog(state: AppState, m: Modal.Envs) {
    val p = LocalPal.current
    val col = m.collection?.let { c -> state.collection(c) ?: run { state.modal = null; return } }
    val isGlobal = col == null
    val envs = if (col == null) state.globalEnvs else col.environments
    val activeName = if (col == null) state.globalEnvName() else state.envName(col.path)
    fun activate(n: String?) = if (col == null) state.setGlobalEnv(n) else state.setEnv(col.path, n)
    fun save(e: Environment, old: String?) = if (col == null) state.saveGlobalEnvironment(e, old) else state.saveEnvironment(col.path, e, old)
    fun delete(n: String) = if (col == null) state.deleteGlobalEnvironment(n) else state.deleteEnvironment(col.path, n)
    fun uniqueName(base: String): String {
        var n = base; var i = 2
        while (envs.any { it.name == n }) n = "$base ${i++}"
        return n
    }

    var selName by remember(m.collection) { mutableStateOf(activeName ?: envs.firstOrNull()?.name) }
    val env = envs.firstOrNull { it.name == selName }
    var name by remember(selName, m.collection) { mutableStateOf(env?.name ?: "") }
    var vars by remember(selName, m.collection) { mutableStateOf(env?.vars ?: emptyList()) }
    var secrets by remember(selName, m.collection) { mutableStateOf(env?.secrets ?: emptySet()) }
    val changed = env != null && (name.trim() != env.name || vars.filter { it.name.isNotBlank() } != env.vars || secrets.filter { s -> vars.any { it.name == s } }.toSet() != env.secrets)

    ModalBox(if (col == null) "Environments — Global" else "Environments — ${col.name}", { state.modal = null }, 860.dp, 560.dp, actions = {
        Btn("Close", { state.modal = null })
        if (env != null) Btn("Save", {
            save(Environment(name.trim(), vars.filter { it.name.isNotBlank() }, secrets.filter { s -> vars.any { it.name == s } }.toSet()), env.name)
            selName = name.trim()
            state.showToast("Environment saved")
        }, primary = true)
    }) {
        // scope switch: global environments apply to every collection, collection ones only to that collection
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val current = state.currentCollection
            Btn("Global", { state.modal = Modal.Envs(null) }, primary = isGlobal)
            if (current != null) Btn("Collection: ${current.name}", { state.modal = Modal.Envs(current.path) }, primary = !isGlobal)
            T(
                if (isGlobal) "Shared by every collection. Lowest precedence — collection, folder and request variables override them."
                else "Only for this collection. Overrides global environment variables with the same name.",
                color = p.muted, size = 12, modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.width(200.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    envs.forEach { e ->
                        val on = e.name == activeName
                        Row(Modifier.fillMaxWidth().background(if (e.name == selName) p.selected else androidx.compose.ui.graphics.Color.Transparent).clickable { selName = e.name }.padding(horizontal = 10.dp, vertical = 7.dp)) {
                            T(e.name, modifier = Modifier.weight(1f), weight = if (on) FontWeight.Bold else null, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            if (on) T("●", color = p.ok, size = 10)
                        }
                    }
                    if (envs.isEmpty()) T("No ${if (isGlobal) "global " else ""}environments yet.", color = p.muted, size = 12, modifier = Modifier.padding(10.dp))
                }
                Btn("+ New Environment", {
                    val n = uniqueName("New Environment")
                    save(Environment(n), null); selName = n
                }, Modifier.fillMaxWidth())
            }
            if (env == null) T("No environment selected. Create one to define variables like {{baseUrl}}.", color = p.muted)
            else Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field(name, { name = it }, Modifier.weight(1f), "Environment name", highlightVars = false)
                    if (env.name == activeName) Btn("Deactivate", { activate(null) }) else Btn("Activate", { activate(env.name) })
                    Btn("Clone", {
                        val n = uniqueName(env.name + " copy")
                        save(env.copy(name = n), null); selName = n
                    })
                    Btn("Delete", { delete(env.name); selName = envs.firstOrNull { it.name != env.name }?.name }, danger = true)
                }
                if (changed) T("Unsaved changes", color = p.accent, size = 11)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    KVTable(
                        vars, { vars = it }, keyHint = "Variable", valueHint = "Value",
                        extra = { _, kv ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Check(kv.name in secrets && kv.name.isNotBlank(), { on -> secrets = if (on) secrets + kv.name else secrets - kv.name })
                                T("secret", size = 11, color = p.muted)
                            }
                        },
                    )
                }
            }
        }
        T(
            (if (isGlobal) "Global environments are stored in ~/.openrest/environments. " else "") +
                "Secret values are kept out of the YAML file (stored in ~/.openrest). Use {{name}} anywhere: URL, headers, body, auth.",
            color = p.muted, size = 12,
        )
    }
}

// ---------------- code generation ----------------

@Composable
private fun CodegenDialog(state: AppState, m: Modal.Codegen) {
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
private fun ImportDialog(state: AppState) {
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
                "Bruno Collection (folder)" -> "Converts a Bruno collection (bruno.json + .bru files) into a new OpenRest YAML collection."
                "OpenRest Collection (folder)" -> "Opens an existing OpenRest collection folder (contains openrest.yaml)."
                "OpenRest Bundle (YAML)" -> "Creates a collection from a single-file bundle exported by OpenRest."
                "OpenAPI / Swagger" -> "JSON or YAML. Requests are grouped by tag; the server URL becomes {{baseUrl}} in a Default environment."
                else -> "Scripts using pm.* are converted to the bru/expect API where possible."
            },
            color = p.muted, size = 12,
        )
    }
}

// ---------------- settings / cookies / variables ----------------

@Composable
private fun CookiesDialog(state: AppState) {
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

@Composable
private fun VarsDialog(state: AppState, m: Modal.Vars) {
    val p = LocalPal.current
    val col = state.collection(m.collection) ?: run { state.modal = null; return }
    val rt = state.runtime(col.path)
    val envN = state.envName(col.path)
    val env = col.environments.firstOrNull { it.name == envN }
    ModalBox("Variables — ${col.name}", { state.modal = null }, 640.dp, 520.dp, actions = {
        Btn("Clear Runtime Variables", { rt.clear() }, danger = true)
        Btn("Close", { state.modal = null }, primary = true)
    }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel("RUNTIME (set by scripts / post-response vars)")
            if (rt.isEmpty()) T("none", color = p.muted)
            rt.entries.sortedBy { it.key }.forEach { (k, v) -> Row { T(k, mono = true, color = p.syntaxKey, modifier = Modifier.width(200.dp)); T(Json.str(v) ?: "null", mono = true, modifier = Modifier.weight(1f)) } }
            Spacer(Modifier.height(8.dp))
            val genv = state.globalEnv()
            SectionLabel("GLOBAL ENVIRONMENT: ${genv?.name ?: "None"}")
            genv?.vars?.forEach { v -> Row { T(v.name, mono = true, color = p.syntaxKey, modifier = Modifier.width(200.dp)); T(if (v.name in genv.secrets) "••••••" else v.value, mono = true, modifier = Modifier.weight(1f)) } }
            Spacer(Modifier.height(8.dp))
            SectionLabel("ENVIRONMENT: ${envN ?: "No Environment"}")
            env?.vars?.forEach { v -> Row { T(v.name, mono = true, color = p.syntaxKey, modifier = Modifier.width(200.dp)); T(if (v.name in env.secrets) "••••••" else v.value, mono = true, modifier = Modifier.weight(1f)) } }
            Spacer(Modifier.height(8.dp))
            SectionLabel("COLLECTION")
            col.config.preVars.forEach { v -> Row { T(v.name, mono = true, color = p.syntaxKey, modifier = Modifier.width(200.dp)); T(v.value, mono = true, modifier = Modifier.weight(1f)) } }
        }
    }
}

// ---------------- collection / folder settings ----------------

@Composable
private fun NodeSettingsDialog(state: AppState, m: Modal.NodeSettings) {
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

// ---------------- export ----------------

@Composable
private fun ExportDialog(state: AppState, m: Modal.Export) {
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
