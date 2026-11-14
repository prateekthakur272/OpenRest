package dev.prateekthakur.openrest.features.dialogs

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.http.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.model.Collection
import dev.prateekthakur.openrest.theme.*
import dev.prateekthakur.openrest.utils.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ---------------- environments ----------------

@Composable
internal fun EnvsDialog(state: AppState, m: Modal.Envs) {
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

@Composable
internal fun VarsDialog(state: AppState, m: Modal.Vars) {
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
