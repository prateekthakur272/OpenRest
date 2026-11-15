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

// Import and export of collections. Extensions of [AppState] so that class stays the single owner of state.

fun AppState.importFrom(kind: String, file: Path?, text: String, parent: Path) = guard {
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

fun AppState.exportCollection(col: Path, format: String, dest: Path) = guard {
    val c = collection(col) ?: error("Collection not found")
    val out = Exporters.export(format, c, dest)
    showToast("Exported to ${out.fileName}")
}
