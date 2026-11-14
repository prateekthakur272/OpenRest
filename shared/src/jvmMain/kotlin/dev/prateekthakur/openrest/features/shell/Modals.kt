package dev.prateekthakur.openrest.features.shell

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.features.dialogs.*
import dev.prateekthakur.openrest.features.onboarding.*
import dev.prateekthakur.openrest.features.preferences.*
import dev.prateekthakur.openrest.features.workflow.*

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp

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
        Modal.Onboarding -> OnboardingDialog(state)
    }
}
