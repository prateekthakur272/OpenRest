package dev.prateekthakur.openrest.app

import dev.prateekthakur.openrest.core.codegen.*
import dev.prateekthakur.openrest.core.interop.*
import dev.prateekthakur.openrest.core.logging.Log
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.core.storage.*
import dev.prateekthakur.openrest.platform.*
import dev.prateekthakur.openrest.utils.*

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

private val SIDEBAR_TABS = setOf("Collections", "History")

class AppState(val scope: CoroutineScope) {
    val titleMenu = TitleMenuState()
    var settings by mutableStateOf(AppStore.loadSettings())
    var collections by mutableStateOf<List<Collection>>(emptyList())
    val tabs = mutableStateListOf<RequestTab>()
    var activeTabId by mutableStateOf<Int?>(null)
    val activeEnv = mutableStateMapOf<String, String>()
    /** Global environments are shared by every collection; one can be active at a time. */
    var globalEnvs by mutableStateOf<List<Environment>>(emptyList())
    var activeGlobalEnv by mutableStateOf<String?>(null)
    private val runtimes = HashMap<String, SnapshotStateMap<String, Any?>>()
    var modal by mutableStateOf<Modal?>(null)
    var toast by mutableStateOf<String?>(null)
    val expanded = mutableStateMapOf<String, Boolean>()
    var selectedCollection by mutableStateOf<Path?>(null)
    var sidebarFilter by mutableStateOf("")
    var sidebarWidth by mutableStateOf(0.22f)
    var splitFraction by mutableStateOf(0.45f)
    val history = mutableStateListOf<HistoryEntry>()
    var sidebarTab by mutableStateOf("Collections")
    var renaming by mutableStateOf<Path?>(null)
    var cursor by mutableStateOf<String?>(null)
    var cursorVisible by mutableStateOf(false)
    var sidebarVisible by mutableStateOf(true)
    /** Which section Preferences should open on (set by menu items such as Help ▸ Keyboard Shortcuts). */
    var prefsSection by mutableStateOf<String?>(null)
    val urlFocus = androidx.compose.ui.focus.FocusRequester()
    internal var nextId = 1
    private var toastJob: Job? = null

    init {
        val saved = AppStore.loadState()
        saved.collections.map { Paths.get(it) }.filter { Collections.isCollection(it) }.forEach { path -> Log.orElse("app", "Cannot open saved collection $path", Unit) { addCollection(path, false) } }
        saved.envs.forEach { (k, v) -> activeEnv[k] = v }
        globalEnvs = AppStore.loadGlobalEnvs()
        activeGlobalEnv = saved.globalEnv
        sidebarWidth = saved.sidebarWidth.coerceIn(0.12f, 0.45f); splitFraction = saved.splitFraction.coerceIn(0.12f, 0.88f); sidebarVisible = saved.sidebarVisible
        restoreSidebar(saved)
        saved.tabs.forEach { f ->
            val path = Paths.get(f)
            collections.firstOrNull { path.startsWith(it.path) }?.let { c -> c.findRequest(path)?.let { openRequest(c.path, it) } }
        }
        saved.activeTab?.let { a -> tabs.firstOrNull { it.filePath.toString() == a }?.let { activeTabId = it.id } }
    }

    val activeTab: RequestTab? get() = tabs.firstOrNull { it.id == activeTabId }

    val currentCollection: Collection?
        get() = activeTab?.let { collection(it.collectionPath) } ?: selectedCollection?.let { collection(it) } ?: collections.firstOrNull()

    fun collection(p: Path) = collections.firstOrNull { it.path == p }

    fun runtime(col: Path): SnapshotStateMap<String, Any?> = runtimes.getOrPut(col.toString()) { mutableStateMapOf() }

    /** The active global environment, or null when none is selected (or the selection no longer exists). */
    fun globalEnv(): Environment? = globalEnvs.firstOrNull { it.name == activeGlobalEnv }
    fun globalEnvName(): String? = globalEnv()?.name

    fun runCtx(col: Collection) = RunCtx(col, envName(col.path), runtime(col.path), settings, globalEnv())

    fun envName(col: Path): String? = activeEnv[col.toString()]?.takeIf { n -> collection(col)?.environments?.any { it.name == n } == true }

    fun showToast(msg: String) {
        toast = msg
        toastJob?.cancel()
        toastJob = scope.launch { kotlinx.coroutines.delay(3500); toast = null }
    }

    internal inline fun guard(block: () -> Unit) {
        try { block() } catch (e: Exception) { Log.warning("app", "Operation failed: ${e.message}", e); showToast(e.message ?: e.toString()) }
    }

    /** Re-applies the side pane: its tab, which folders were open and the selected collection (entries for paths that no longer exist are dropped). */
    private fun restoreSidebar(saved: AppStore.SavedState) {
        saved.sidebarTab?.takeIf { it in SIDEBAR_TABS }?.let { sidebarTab = it }
        saved.expandedFolders?.let { open ->
            expanded.clear()
            open.filter { Paths.get(it).exists() }.forEach { expanded[it] = true }
        }
        saved.selectedCollection?.let { Paths.get(it) }?.takeIf { p -> collections.any { it.path == p } }?.let { selectedCollection = it }
    }

    /** Everything the side pane shows or the layout remembers; a change to any of it is saved shortly after. */
    internal fun layoutSnapshot() = listOf(sidebarWidth, splitFraction, sidebarVisible, sidebarTab, selectedCollection, expanded.filterValues { it }.keys)

    fun persist() {
        AppStore.saveState(AppStore.SavedState(collections.map { it.path.toString() }, activeEnv.toMap(), tabs.filter { it.isRequest }.map { it.filePath.toString() }, activeTab?.takeIf { it.isRequest }?.filePath?.toString(), sidebarWidth, splitFraction, sidebarVisible, activeGlobalEnv,
            sidebarTab, expanded.filterValues { it }.keys.toList(), selectedCollection?.toString()))
    }

    fun updateSettings(s: AppSettings) { settings = s; AppStore.saveSettings(s) }

    /** Closes the first-run walkthrough, remembers that it was seen (the settings file now exists) and optionally opens what the user picked next. */
    fun finishOnboarding(next: Modal? = null) { AppStore.saveSettings(settings); modal = next }

    // ---------------- collections ----------------

    fun addCollection(path: Path, persist: Boolean = true) {
        if (collections.any { it.path == path }) { showToast("Collection already open"); return }
        collections = collections + Collections.load(path)
        expanded[path.toString()] = true
        selectedCollection = path
        if (persist) persist()
    }

    fun openCollection(path: Path) = guard {
        require(Collections.isCollection(path)) { "Not a collection (no openrest.yaml found in ${path.name})" }
        addCollection(path)
    }

    fun createCollection(parent: Path, name: String) = guard {
        require(name.isNotBlank()) { "Name required" }
        val dir = Collections.create(parent, name)
        addCollection(dir)
    }

    fun closeCollection(path: Path) {
        tabs.removeAll { it.collectionPath == path }
        if (activeTab == null) activeTabId = tabs.lastOrNull()?.id
        collections = collections.filter { it.path != path }
        persist()
    }

    fun reload(path: Path) {
        val i = collections.indexOfFirst { it.path == path }
        if (i >= 0) collections = collections.toMutableList().also { it[i] = Collections.load(path) }
    }

    fun reloadAll() = collections.map { it.path }.forEach { path -> Log.orElse("app", "Cannot reload collection $path", Unit) { reload(path) } }

    // ---------------- tabs ----------------

    fun openRequest(col: Path, node: RequestNode) {
        tabs.firstOrNull { it.filePath == node.path }?.let { activeTabId = it.id; return }
        val t = RequestTab(nextId++, col, node.path, node.request)
        tabs += t
        activeTabId = t.id
        selectedCollection = col
        persist()
    }

    /** Runner nodes for a collection (folder == null) or one folder, in tree order. */
    fun runnerRequests(col: Collection, folder: Path?): List<RequestNode> {
        fun find(nodes: List<Node>): FolderNode? = nodes.firstNotNullOfOrNull { n -> if (n is FolderNode) (if (n.path == folder) n else find(n.children)) else null }
        return if (folder == null) Runner.flatten(col.children) else Runner.flatten(find(col.children)?.children ?: emptyList())
    }

    fun openRunner(col: Path, folder: Path?) {
        val c = collection(col) ?: return
        tabs.firstOrNull { it.runner != null && it.collectionPath == col && it.runner.folder == folder }?.let { activeTabId = it.id; return }
        val label = if (folder == null) c.name else folder.fileName.toString()
        val t = RequestTab(nextId++, col, folder ?: col, ApiRequest(name = "Runner: $label"), RunnerSession(folder, runnerRequests(c, folder)))
        tabs += t
        activeTabId = t.id
        selectedCollection = col
    }


    fun closeTab(id: Int, force: Boolean = false) {
        val t = tabs.firstOrNull { it.id == id } ?: return
        t.runner?.cancelled = true
        t.workflow?.cancelled = true
        if (t.dirty && !force) {
            modal = Modal.Confirm("Unsaved changes", "\"${t.title}\" has unsaved changes.", "Discard") { closeTab(id, true) }
            return
        }
        t.job?.cancel()
        val idx = tabs.indexOf(t)
        tabs.remove(t)
        if (activeTabId == id) activeTabId = tabs.getOrNull(minOf(idx, tabs.size - 1))?.id
        persist()
    }

    fun update(tab: RequestTab, f: (ApiRequest) -> ApiRequest) { tab.draft = f(tab.draft) }

    fun save(tab: RequestTab) = guard {
        if (tab.workflow != null) { saveWorkflow(tab); return@guard }
        if (tab.runner != null) return@guard
        Collections.saveRequest(tab.filePath, tab.draft)
        tab.saved = tab.draft
        reload(tab.collectionPath)
    }

    fun saveAll() = tabs.filter { it.dirty }.forEach { save(it) }

    // ---------------- sending ----------------

    fun send(tab: RequestTab) {
        if (tab.sending || !tab.isRequest) return
        val col = collection(tab.collectionPath) ?: return
        if (settings.autoSave && tab.dirty) save(tab)
        tab.sending = true
        tab.result = null
        tab.resTab = "Response"
        tab.job = scope.launch(Dispatchers.IO) {
            val node = RequestNode(tab.filePath, tab.draft.name, tab.draft)
            val rc = runCtx(col)
            val r = try {
                runInterruptible { Execution(rc, node).run() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                ExecResult(error = e.message ?: e.toString())
            }
            if (isActive) {
                tab.push(r); tab.sending = false
                history.add(0, HistoryEntry(System.currentTimeMillis(), col.path, tab.filePath, tab.draft.name, tab.draft.method, r.request?.url ?: tab.draft.url, r.response?.status ?: 0, r.response?.timeMs ?: 0, r.error ?: r.response?.error, r))
                while (history.size > 200) history.removeAt(history.size - 1)
            }
        }
    }

    fun cancel(tab: RequestTab) {
        tab.job?.cancel()
        tab.sending = false
        tab.push(ExecResult(error = "Request cancelled"))
    }

    /** Build the prepared request for code generation without running scripts. */
    fun prepareFor(tab: RequestTab): Result<PreparedRequest> = runCatching {
        require(tab.isRequest) { "Not a request tab" }
        val col = collection(tab.collectionPath) ?: error("Collection not found")
        val rc = runCtx(col)
        Execution(rc, RequestNode(tab.filePath, tab.draft.name, tab.draft)).prepare()
    }

    /** name -> (value, source) for the autocomplete menu; later puts win, matching variable precedence. */
    fun varInfo(tab: RequestTab?): Map<String, Pair<String, String>> {
        val col = (tab?.let { collection(it.collectionPath) } ?: currentCollection) ?: return emptyMap()
        val out = LinkedHashMap<String, Pair<String, String>>()
        globalEnv()?.let { e -> e.vars.filter { it.enabled }.forEach { v -> out[v.name] = (if (v.name in e.secrets) "••••••" else v.value) to "global" } }
        col.config.preVars.filter { it.enabled }.forEach { out[it.name] = it.value to "collection" }
        envName(col.path)?.let { n -> col.environments.firstOrNull { it.name == n }?.let { e -> e.vars.filter { it.enabled }.forEach { v -> out[v.name] = (if (v.name in e.secrets) "••••••" else v.value) to "env" } } }
        if (tab != null) {
            col.ancestorFolders(tab.filePath).forEach { f -> f.config.preVars.filter { it.enabled }.forEach { out[it.name] = it.value to "folder" } }
            tab.draft.preVars.filter { it.enabled }.forEach { out[it.name] = it.value to "request" }
            tab.draft.pathParams.forEach { out[it.name] = it.value to "path" }
        }
        runtime(col.path).forEach { (k, v) -> out[k] = (if (v is String) v else Json.stringify(v)) to "runtime" }
        return out
    }

    fun knownVars(tab: RequestTab?): Set<String> {
        val col = (tab?.let { collection(it.collectionPath) } ?: currentCollection) ?: return emptySet()
        val out = HashSet<String>()
        globalEnv()?.vars?.forEach { out += it.name }
        envName(col.path)?.let { n -> col.environments.firstOrNull { it.name == n }?.vars?.forEach { out += it.name } }
        col.config.preVars.forEach { out += it.name }
        if (tab != null) {
            col.ancestorFolders(tab.filePath).forEach { f -> f.config.preVars.forEach { out += it.name } }
            tab.draft.preVars.forEach { out += it.name }
            tab.draft.pathParams.forEach { out += it.name }
        }
        out += runtime(col.path).keys
        return out
    }

    // ---------------- developer conveniences ----------------

    fun nextTab(delta: Int) {
        if (tabs.isEmpty()) return
        val i = tabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)
        activeTabId = tabs[(i + delta + tabs.size) % tabs.size].id
    }

    fun moveTab(from: Int, to: Int) {
        if (from !in tabs.indices || to !in tabs.indices || from == to) return
        tabs.add(to, tabs.removeAt(from))
        persist()
    }

    fun gotoTab(index: Int) { tabs.getOrNull(index)?.let { activeTabId = it.id } }

    fun closeOthers(id: Int) = tabs.filter { it.id != id && !it.dirty }.forEach { closeTab(it.id, true) }

    fun closeAll() = tabs.toList().filter { !it.dirty }.forEach { closeTab(it.id, true) }

    fun copyCurl(tab: RequestTab) {
        prepareFor(tab).fold({ Platform.copy(CodeGen.generate("cURL", it)); showToast("Copied as cURL") }, { showToast(it.message ?: "Could not build request") })
    }

    fun collapseAll() { expanded.clear() }

    fun expandAll() {
        fun walk(nodes: List<Node>): Unit = nodes.forEach { if (it is FolderNode) { expanded[it.path.toString()] = true; walk(it.children) } }
        collections.forEach { expanded[it.path.toString()] = true; walk(it.children) }
    }

    fun openHistory(e: HistoryEntry) {
        val req = collection(e.collection)?.findRequest(e.file)
        if (req == null) { showToast("Request no longer exists"); return }
        openRequest(e.collection, req)
        activeTab?.let { it.push(e.result); it.resTab = "Response" }
    }

    fun zoom(delta: Int) {
        val n = if (delta == 0) 13 else (settings.fontSize + delta).coerceIn(10, 20)
        updateSettings(settings.copy(fontSize = n)); showToast("Zoom ${(n * 100 / 13)}%")
    }

    fun resolveUrl(tab: RequestTab): String? = runCatching {
        val col = collection(tab.collectionPath) ?: return null
        Execution(runCtx(col), RequestNode(tab.filePath, tab.draft.name, tab.draft)).buildUrl(tab.draft.url)
    }.getOrNull()

    fun toggleLayout(): Unit = updateSettings(settings.copy(verticalLayout = !settings.verticalLayout))
}
