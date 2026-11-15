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

// Environments (per collection and global) and folder / collection settings. Extensions of [AppState] so that class stays the single owner of state.

fun AppState.setEnv(col: Path, name: String?) {
    if (name == null) activeEnv.remove(col.toString()) else activeEnv[col.toString()] = name
    persist()
}

fun AppState.saveEnvironment(col: Path, env: Environment, old: String?) = guard {
    require(env.name.isNotBlank()) { "Environment name required" }
    Collections.saveEnvironment(col, env, old)
    reload(col)
    if (old != null && old != env.name && activeEnv[col.toString()] == old) setEnv(col, env.name)
}

fun AppState.deleteEnvironment(col: Path, name: String) = guard {
    Collections.deleteEnvironment(col, name)
    if (activeEnv[col.toString()] == name) setEnv(col, null)
    reload(col)
}

// global environments

fun AppState.setGlobalEnv(name: String?) { activeGlobalEnv = name; persist() }

fun AppState.saveGlobalEnvironment(env: Environment, old: String?) = guard {
    require(env.name.isNotBlank()) { "Environment name required" }
    AppStore.saveGlobalEnv(env, old)
    globalEnvs = AppStore.loadGlobalEnvs()
    if (old != null && old != env.name && activeGlobalEnv == old) setGlobalEnv(env.name)
}

fun AppState.deleteGlobalEnvironment(name: String) = guard {
    AppStore.deleteGlobalEnv(name)
    globalEnvs = AppStore.loadGlobalEnvs()
    if (activeGlobalEnv == name) setGlobalEnv(null)
}

fun AppState.saveNodeConfig(col: Path, node: Path, cfg: NodeConfig) = guard {
    if (node == col) Collections.saveCollectionConfig(col, cfg) else Collections.saveFolderConfig(node, cfg)
    reload(col)
    showToast("Settings saved")
}
