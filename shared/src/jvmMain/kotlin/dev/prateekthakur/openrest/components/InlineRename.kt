package dev.prateekthakur.openrest.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*

@Composable
fun InlineRename(initial: String, modifier: Modifier = Modifier, onDone: (String?) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    var gotFocus by remember { mutableStateOf(false) }
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    Field(
        v, { v = it },
        modifier.onPreviewKeyEvent { e -> if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key == androidx.compose.ui.input.key.Key.Escape) { onDone(null); true } else false }
            .onFocusChanged { if (it.isFocused) gotFocus = true else if (gotFocus) onDone(null) },
        onEnter = { onDone(v.trim().takeIf { it.isNotEmpty() }) }, focusRequester = fr, highlightVars = false,
    )
}
