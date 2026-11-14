package dev.prateekthakur.openrest.app

import dev.prateekthakur.openrest.core.codegen.*
import dev.prateekthakur.openrest.core.interop.*
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
    private var nextId = 1
    private var toastJob: Job? = null

    init {
        val saved = AppStore.loadState()
        saved.collections.map { Paths.get(it) }.filter { Collections.isCollection(it) }.forEach { runCatching { addCollection(it, false) } }
        saved.envs.forEach { (k, v) -> activeEnv[k] = v }
        globalEnvs = AppStore.loadGlobalEnvs()
        activeGlobalEnv = saved.globalEnv
        sidebarWidth = saved.sidebarWidth.coerceIn(0.12f, 0.45f); splitFraction = saved.splitFraction.coerceIn(0.12f, 0.88f); sidebarVisible = saved.sidebarVisible
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

    private inline fun guard(block: () -> Unit) {
        try { block() } catch (e: Exception) { showToast(e.message ?: e.toString()) }
    }

    fun persist() {
        AppStore.saveState(AppStore.SavedState(collections.map { it.path.toString() }, activeEnv.toMap(), tabs.filter { it.isRequest }.map { it.filePath.toString() }, activeTab?.takeIf { it.isRequest }?.filePath?.toString(), sidebarWidth, splitFraction, sidebarVisible, activeGlobalEnv))
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

    fun reloadAll() = collections.map { it.path }.forEach { runCatching { reload(it) } }

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

    // ---------------- workflows ----------------

    fun workflowTab(col: Path, name: String): RequestTab? = tabs.firstOrNull { it.workflow != null && it.collectionPath == col && it.workflow.saved.name == name }

    fun openWorkflow(col: Path, name: String) {
        workflowTab(col, name)?.let { activeTabId = it.id; return }
        val wf = collection(col)?.workflows?.firstOrNull { it.name == name } ?: return
        val t = RequestTab(nextId++, col, WorkflowStore.file(col, name), ApiRequest(name = name), workflow = WorkflowSession(wf))
        tabs += t
        activeTabId = t.id
        selectedCollection = col
    }

    fun promptNewWorkflow(col: Path) {
        modal = Modal.Prompt("New Workflow", "", "Workflow name", "Create") { n ->
            guard {
                val name = n.trim()
                require(name.isNotEmpty()) { "Name required" }
                require(!WorkflowStore.file(col, name).exists()) { "A workflow named '$name' already exists" }
                WorkflowStore.save(col, Workflow(name))
                reload(col); expanded[col.toString()] = true
                openWorkflow(col, name)
            }
        }
    }

    fun saveWorkflow(tab: RequestTab) = guard {
        val ws = tab.workflow ?: return@guard
        val w = ws.draft.copy(name = ws.draft.name.trim())
        require(w.name.isNotEmpty()) { "Workflow name required" }
        WorkflowStore.save(tab.collectionPath, w, ws.saved.name)
        ws.draft = w; ws.saved = w
        tab.filePath = WorkflowStore.file(tab.collectionPath, w.name)
        reload(tab.collectionPath)
    }

    fun promptRenameWorkflow(col: Path, name: String) {
        modal = Modal.Prompt("Rename Workflow", name, "Workflow name", "Rename") { n ->
            guard {
                val new = n.trim()
                require(new.isNotEmpty()) { "Name required" }
                val wf = collection(col)?.workflows?.firstOrNull { it.name == name } ?: return@guard
                WorkflowStore.save(col, wf.copy(name = new), name)
                workflowTab(col, name)?.let { t -> t.workflow!!.let { s -> s.draft = s.draft.copy(name = new); s.saved = s.saved.copy(name = new) }; t.filePath = WorkflowStore.file(col, new) }
                reload(col)
            }
        }
    }

    fun cloneWorkflow(col: Path, name: String) = guard {
        val wf = collection(col)?.workflows?.firstOrNull { it.name == name } ?: return@guard
        var n = "$name copy"; var i = 2
        while (WorkflowStore.file(col, n).exists()) n = "$name copy ${i++}"
        WorkflowStore.save(col, wf.copy(name = n))
        reload(col)
        openWorkflow(col, n)
    }

    fun promptDeleteWorkflow(col: Path, name: String) {
        modal = Modal.Confirm("Delete workflow", "Permanently delete workflow \"$name\"? The requests it uses are not deleted.", "Delete") {
            guard {
                workflowTab(col, name)?.let { closeTab(it.id, true) }
                WorkflowStore.delete(col, name)
                reload(col)
            }
        }
    }

    private fun addBlock(tab: RequestTab, parent: String?, branch: Int, make: (String) -> WorkflowStep) {
        val ws = tab.workflow ?: return
        val id = newStepId(ws.draft.steps)
        ws.update { w -> w.copy(steps = w.steps.addStep(parent, branch, make(id))) }
        ws.selected = id
    }

    /** Adds an existing request of the collection at the end of a branch (top level when [parent] is null). */
    fun addWorkflowStep(tab: RequestTab, request: RequestNode, parent: String? = null, branch: Int = 0) {
        val col = collection(tab.collectionPath) ?: return
        addBlock(tab, parent, branch) { WorkflowStep(it, col.requestRef(request)) }
    }

    /** Creates a brand-new request in the collection root and adds it as a step. */
    fun addNewWorkflowStep(tab: RequestTab, name: String, method: String, url: String, parent: String? = null, branch: Int = 0) = guard {
        require(name.isNotBlank()) { "Request name required" }
        val col = tab.collectionPath
        val file = Collections.createRequest(col, ApiRequest(name = name.trim(), method = method, url = url.trim()))
        reload(col)
        collection(col)?.findRequest(file)?.let { addWorkflowStep(tab, it, parent, branch) }
    }

    /** Adds a logic block (if/else, loop, set variable, wait, stop) with sensible starting values. */
    fun addWorkflowLogic(tab: RequestTab, kind: StepKind, parent: String? = null, branch: Int = 0, loop: LoopMode = LoopMode.ForEach, setOp: SetOp = SetOp.Value) {
        addBlock(tab, parent, branch) { id ->
            when (kind) {
                StepKind.If -> WorkflowStep(id, kind = kind, conditions = listOf(Condition()))
                StepKind.Loop -> WorkflowStep(id, kind = kind, loop = loop, conditions = if (loop == LoopMode.While) listOf(Condition()) else emptyList(), count = if (loop == LoopMode.Repeat) "3" else "")
                StepKind.Set -> WorkflowStep(id, kind = kind, setOp = setOp, varName = when (setOp) { SetOp.Count -> "count"; SetOp.Sum -> "total"; SetOp.First -> "first"; SetOp.Last -> "last"; else -> "" })
                StepKind.Wait -> WorkflowStep(id, kind = kind, value = "1000")
                else -> WorkflowStep(id, kind = kind)
            }
        }
    }

    fun removeWorkflowStep(tab: RequestTab, id: String) {
        val ws = tab.workflow ?: return
        // references to the removed block are left in place; the next run flags them
        val gone = ws.draft.steps.findStep(id)?.let { listOf(it) + it.children.walk() + it.otherwise.walk() }?.map { it.id } ?: listOf(id)
        ws.update { w -> w.copy(steps = w.steps.removeStep(id)) }
        gone.forEach { ws.runs.remove(it); ws.counts.remove(it); ws.history.remove(it) }
        if (ws.selected in gone) ws.selected = null
    }

    fun moveWorkflowStep(tab: RequestTab, id: String, delta: Int) {
        val ws = tab.workflow ?: return
        ws.update { w -> w.copy(steps = w.steps.moveStep(id, delta)) }
    }

    /** Runs the workflow; with [upTo] it ends once that block has run (handy while building). */
    fun runWorkflow(tab: RequestTab, upTo: String? = null) {
        val ws = tab.workflow ?: return
        if (ws.running) return
        val col = collection(tab.collectionPath) ?: return
        if (settings.autoSave && ws.dirty) saveWorkflow(tab)
        ws.runs.clear(); ws.counts.clear(); ws.history.clear(); ws.events.clear(); ws.logOpen.clear(); ws.cancelled = false; ws.running = true; ws.expanded = null
        val rc = runCtx(col)
        val wf = ws.draft
        scope.launch(Dispatchers.IO) {
            try {
                WorkflowRunner.run(
                    rc, wf, { ws.cancelled }, { ws.current = it.id },
                    { r -> ws.runs[r.step.id] = r; ws.counts[r.step.id] = (ws.counts[r.step.id] ?: 0) + 1; ws.history[r.step.id] = ((ws.history[r.step.id] ?: emptyList()) + r).takeLast(100) },
                    { e -> ws.events.add(e) }, upTo,
                )
            } catch (e: Throwable) {
                showToast(e.message ?: e.toString())
            } finally { ws.running = false; ws.current = null }
        }
    }

    fun startRunner(tab: RequestTab) {
        val rs = tab.runner ?: return
        if (rs.running) return
        val col = collection(tab.collectionPath) ?: return
        rs.results.clear(); rs.cancelled = false; rs.running = true; rs.expanded = null
        val rc = runCtx(col)
        val queue = List(rs.iters) { rs.chosen }.flatten()
        val delay = rs.delay.toLongOrNull() ?: 0L
        val stop = rs.stopOnFail
        scope.launch(Dispatchers.IO) {
            try {
                Runner.runAll(rc, queue, stop, delay, { rs.cancelled }, { rs.current = it.name }, { rs.results.add(it) })
            } finally { rs.running = false; rs.current = "" }
        }
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

    // ---------------- sidebar operations ----------------

    private fun remapTabs(old: Path, new: Path) {
        tabs.forEach { t -> if (t.filePath.startsWith(old)) t.filePath = new.resolve(old.relativize(t.filePath).toString()) }
    }

    private fun dropTabs(under: Path) {
        val gone = tabs.filter { it.filePath.startsWith(under) }
        gone.forEach { it.job?.cancel() }
        tabs.removeAll(gone)
        if (activeTab == null) activeTabId = tabs.lastOrNull()?.id
    }

    fun createRequest(col: Path, dir: Path, name: String, method: String, url: String) = guard {
        require(name.isNotBlank()) { "Name required" }
        val r = ApiRequest(name = name.trim(), method = method, url = url.trim(), params = UrlSync.paramsFromUrl(url.trim(), emptyList()))
        val f = Collections.createRequest(dir, r)
        reload(col)
        expanded[dir.toString()] = true
        collection(col)?.findRequest(f)?.let { openRequest(col, it) }
    }

    fun createRequestFromCurl(col: Path, dir: Path, curl: String) = guard {
        val r = Importers.parseCurl(curl) ?: error("Could not parse cURL command")
        val f = Collections.createRequest(dir, r)
        reload(col)
        expanded[dir.toString()] = true
        collection(col)?.findRequest(f)?.let { openRequest(col, it) }
    }

    fun promptNewFolder(col: Path, dir: Path) {
        modal = Modal.Prompt("New Folder", "", "Folder name", "Create") { n ->
            guard { Collections.createFolder(dir, n); reload(col); expanded[dir.toString()] = true }
        }
    }

    fun promptRename(col: Path, node: Node?) {
        if (node == null) {
            val c = collection(col) ?: return
            modal = Modal.Prompt("Rename Collection", c.name, "Collection name", "Rename") { n -> guard { Collections.renameCollection(col, n); reload(col) } }
            return
        }
        renaming = node.path
    }

    fun renameNode(col: Path, node: Node, newName: String) = guard {
        require(newName.isNotBlank()) { "Name required" }
        val new = if (node is FolderNode) Collections.renameFolder(node.path, newName) else Collections.renameRequest(node.path, newName)
        remapTabs(node.path, new)
        if (node is RequestNode) tabs.firstOrNull { it.filePath == new }?.let { t -> t.draft = t.draft.copy(name = newName); t.saved = t.saved.copy(name = newName) }
        reload(col)
    }

    fun moveNode(col: Path, src: Path, destDir: Path) = guard {
        if (src.parent == destDir) return@guard
        val new = Collections.move(src, destDir)
        remapTabs(src, new)
        expanded[destDir.toString()] = true
        reload(col)
        showToast("Moved ${src.name} → ${destDir.name}")
    }

    /** Drop a request next to [target] (before / after), moving it into target's folder if needed. */
    fun dropRequest(col: Path, src: RequestNode, target: RequestNode, after: Boolean) = guard {
        val destDir = target.path.parent
        var srcPath = src.path
        if (src.path.parent != destDir) { srcPath = Collections.move(src.path, destDir); remapTabs(src.path, srcPath) }
        val files = Collections.requestFiles(destDir).mapNotNull { f -> Collections.readRequest(f)?.let { f to it.seq } }.sortedBy { it.second }.map { it.first }.toMutableList()
        files.remove(srcPath)
        val ti = files.indexOf(target.path).coerceAtLeast(0)
        files.add(if (after) ti + 1 else ti, srcPath)
        files.forEachIndexed { i, f ->
            Collections.readRequest(f)?.let { Collections.saveRequest(f, it.copy(seq = i + 1)) }
            tabs.firstOrNull { it.filePath == f }?.let { t -> t.draft = t.draft.copy(seq = i + 1); t.saved = t.saved.copy(seq = i + 1) }
        }
        reload(col)
    }

    fun promptClone(col: Path, node: Node) {
        modal = Modal.Prompt("Clone", node.name + " copy", "Name", "Clone") { n ->
            guard {
                if (node is FolderNode) Collections.cloneFolder(node.path, n) else Collections.clone(node.path, n)
                reload(col)
            }
        }
    }

    fun promptDelete(col: Path, node: Node) {
        modal = Modal.Confirm("Delete ${if (node is FolderNode) "folder" else "request"}", "Permanently delete \"${node.name}\" from disk?", "Delete") {
            guard { dropTabs(node.path); Collections.delete(node.path); reload(col) }
        }
    }

    fun move(col: Path, siblings: List<Node>, index: Int, delta: Int) = guard {
        Collections.reorder(siblings, index, delta)
        // refresh open tabs' seq
        reload(col)
    }

    // ---------------- environments ----------------

    fun setEnv(col: Path, name: String?) {
        if (name == null) activeEnv.remove(col.toString()) else activeEnv[col.toString()] = name
        persist()
    }

    fun saveEnvironment(col: Path, env: Environment, old: String?) = guard {
        require(env.name.isNotBlank()) { "Environment name required" }
        Collections.saveEnvironment(col, env, old)
        reload(col)
        if (old != null && old != env.name && activeEnv[col.toString()] == old) setEnv(col, env.name)
    }

    fun deleteEnvironment(col: Path, name: String) = guard {
        Collections.deleteEnvironment(col, name)
        if (activeEnv[col.toString()] == name) setEnv(col, null)
        reload(col)
    }

    // global environments

    fun setGlobalEnv(name: String?) { activeGlobalEnv = name; persist() }

    fun saveGlobalEnvironment(env: Environment, old: String?) = guard {
        require(env.name.isNotBlank()) { "Environment name required" }
        AppStore.saveGlobalEnv(env, old)
        globalEnvs = AppStore.loadGlobalEnvs()
        if (old != null && old != env.name && activeGlobalEnv == old) setGlobalEnv(env.name)
    }

    fun deleteGlobalEnvironment(name: String) = guard {
        AppStore.deleteGlobalEnv(name)
        globalEnvs = AppStore.loadGlobalEnvs()
        if (activeGlobalEnv == name) setGlobalEnv(null)
    }

    fun saveNodeConfig(col: Path, node: Path, cfg: NodeConfig) = guard {
        if (node == col) Collections.saveCollectionConfig(col, cfg) else Collections.saveFolderConfig(node, cfg)
        reload(col)
        showToast("Settings saved")
    }

    // ---------------- import / export ----------------

    fun importFrom(kind: String, file: Path?, text: String, parent: Path) = guard {
        parent.createDirectories()
        when (kind) {
            "Postman Collection" -> addCollection(Importers.importPostman(file?.readText() ?: text, parent))
            "Insomnia" -> addCollection(Importers.importInsomnia(file?.readText() ?: text, parent))
            "OpenAPI / Swagger" -> addCollection(Importers.importOpenApi(file?.readText() ?: text, parent))
            "Bruno Collection (folder)" -> { requireNotNull(file) { "Choose a Bruno collection folder" }; addCollection(Importers.importBruno(file, parent)) }
            "OpenRest Bundle (YAML)" -> addCollection(Importers.importBundle(file?.readText() ?: text, parent))
            "OpenRest Collection (folder)" -> { requireNotNull(file) { "Choose a collection folder" }; openCollection(file) }
            "cURL" -> {
                val col = currentCollection ?: error("Open or create a collection first")
                createRequestFromCurl(col.path, col.path, text)
            }
            "Postman Environment (Global)" -> {
                val env = Importers.importPostmanEnv(file?.readText() ?: text) ?: error("Not a Postman environment file")
                AppStore.saveGlobalEnv(env)
                globalEnvs = AppStore.loadGlobalEnvs()
            }
            "Postman Environment" -> {
                val col = currentCollection ?: error("Open or create a collection first")
                val env = Importers.importPostmanEnv(file?.readText() ?: text) ?: error("Not a Postman environment file")
                Collections.saveEnvironment(col.path, env)
                reload(col.path)
            }
            else -> error("Unknown import type")
        }
        modal = null
        showToast("Import complete")
    }

    fun exportCollection(col: Path, format: String, dest: Path) = guard {
        val c = collection(col) ?: error("Collection not found")
        val out = Exporters.export(format, c, dest)
        showToast("Exported to ${out.fileName}")
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
