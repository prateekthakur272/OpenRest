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

// Sidebar operations: creating, renaming, moving, cloning and deleting requests and folders. Extensions of [AppState] so that class stays the single owner of state.

private fun AppState.remapTabs(old: Path, new: Path) {
    tabs.forEach { t -> if (t.filePath.startsWith(old)) t.filePath = new.resolve(old.relativize(t.filePath).toString()) }
}

private fun AppState.dropTabs(under: Path) {
    val gone = tabs.filter { it.filePath.startsWith(under) }
    gone.forEach { it.job?.cancel() }
    tabs.removeAll(gone)
    if (activeTab == null) activeTabId = tabs.lastOrNull()?.id
}

fun AppState.createRequest(col: Path, dir: Path, name: String, method: String, url: String) = guard {
    require(name.isNotBlank()) { "Name required" }
    val r = ApiRequest(name = name.trim(), method = method, url = url.trim(), params = UrlSync.paramsFromUrl(url.trim(), emptyList()))
    val f = Collections.createRequest(dir, r)
    reload(col)
    expanded[dir.toString()] = true
    collection(col)?.findRequest(f)?.let { openRequest(col, it) }
}

fun AppState.createRequestFromCurl(col: Path, dir: Path, curl: String) = guard {
    val r = Importers.parseCurl(curl) ?: error("Could not parse cURL command")
    val f = Collections.createRequest(dir, r)
    reload(col)
    expanded[dir.toString()] = true
    collection(col)?.findRequest(f)?.let { openRequest(col, it) }
}

fun AppState.promptNewFolder(col: Path, dir: Path) {
    modal = Modal.Prompt("New Folder", "", "Folder name", "Create") { n ->
        guard { Collections.createFolder(dir, n); reload(col); expanded[dir.toString()] = true }
    }
}

fun AppState.promptRename(col: Path, node: Node?) {
    if (node == null) {
        val c = collection(col) ?: return
        modal = Modal.Prompt("Rename Collection", c.name, "Collection name", "Rename") { n -> guard { Collections.renameCollection(col, n); reload(col) } }
        return
    }
    renaming = node.path
}

fun AppState.renameNode(col: Path, node: Node, newName: String) = guard {
    require(newName.isNotBlank()) { "Name required" }
    val new = if (node is FolderNode) Collections.renameFolder(node.path, newName) else Collections.renameRequest(node.path, newName)
    remapTabs(node.path, new)
    if (node is RequestNode) tabs.firstOrNull { it.filePath == new }?.let { t -> t.draft = t.draft.copy(name = newName); t.saved = t.saved.copy(name = newName) }
    reload(col)
}

fun AppState.moveNode(col: Path, src: Path, destDir: Path) = guard {
    if (src.parent == destDir) return@guard
    val new = Collections.move(src, destDir)
    remapTabs(src, new)
    expanded[destDir.toString()] = true
    reload(col)
    showToast("Moved ${src.name} → ${destDir.name}")
}

/** Drop a request next to [target] (before / after), moving it into target's folder if needed. */
fun AppState.dropRequest(col: Path, src: RequestNode, target: RequestNode, after: Boolean) = guard {
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

fun AppState.promptClone(col: Path, node: Node) {
    modal = Modal.Prompt("Clone", node.name + " copy", "Name", "Clone") { n ->
        guard {
            if (node is FolderNode) Collections.cloneFolder(node.path, n) else Collections.clone(node.path, n)
            reload(col)
        }
    }
}

fun AppState.promptDelete(col: Path, node: Node) {
    modal = Modal.Confirm("Delete ${if (node is FolderNode) "folder" else "request"}", "Permanently delete \"${node.name}\" from disk?", "Delete") {
        guard { dropTabs(node.path); Collections.delete(node.path); reload(col) }
    }
}

fun AppState.move(col: Path, siblings: List<Node>, index: Int, delta: Int) = guard {
    Collections.reorder(siblings, index, delta)
    // refresh open tabs' seq
    reload(col)
}
