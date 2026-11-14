package dev.prateekthakur.openrest.features.dialogs

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import java.nio.file.Paths

internal val defaultDir: String get() = Paths.get(System.getProperty("user.home"), "OpenRest").toString()

@Composable
fun PromptDialog(state: AppState, m: Modal.Prompt) {
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
