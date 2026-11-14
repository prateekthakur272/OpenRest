package dev.prateekthakur.openrest.app

import androidx.compose.runtime.*
import java.nio.file.Path
import kotlin.io.path.*

sealed interface Modal {
    data class Prompt(val title: String, val initial: String, val label: String, val confirm: String, val onOk: (String) -> Unit) : Modal
    data class Confirm(val title: String, val message: String, val confirm: String, val onOk: () -> Unit) : Modal
    data class NewRequest(val collection: Path, val dir: Path) : Modal
    data object NewCollection : Modal
    data object Onboarding : Modal
    data object OpenCollection : Modal
    /** collection == null opens the global environments. */
    data class Envs(val collection: Path?) : Modal
    data class Codegen(val tabId: Int) : Modal
    data object Import : Modal
    data object Settings : Modal
    data object About : Modal
    data object Developer : Modal
    data object Cookies : Modal
    data class NodeSettings(val collection: Path, val node: Path) : Modal
    data class Vars(val collection: Path) : Modal
    data class Export(val collection: Path) : Modal
    /** parent == null adds to the top level; otherwise to that block's main (branch 0) or else (branch 1) list. */
    data class AddStep(val tabId: Int, val parent: String? = null, val branch: Int = 0) : Modal
    /** The complete last response of a workflow step. */
    data class ViewResponse(val tabId: Int, val stepId: String) : Modal
    data object Palette : Modal
}
