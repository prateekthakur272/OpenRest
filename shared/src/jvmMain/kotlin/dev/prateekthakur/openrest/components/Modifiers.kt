package dev.prateekthakur.openrest.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/** Clickable with no ripple or hover indication: for backdrops and panels that only swallow or dismiss clicks. */
fun Modifier.clickableNoRipple(onClick: () -> Unit = {}): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}
