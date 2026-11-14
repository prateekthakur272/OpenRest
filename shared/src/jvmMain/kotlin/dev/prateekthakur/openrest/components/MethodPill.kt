package dev.prateekthakur.openrest.components

import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private fun shortMethod(m: String) = when (m.uppercase()) { "DELETE" -> "DEL"; "OPTIONS" -> "OPT"; "PATCH" -> "PATCH"; else -> m.uppercase() }

/** Width of the method label in tree rows; workflow rows use the same slot for their icon so the titles line up. */
val METHOD_SLOT = 33.dp

@Composable
fun MethodPill(method: String, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Box(modifier.width(METHOD_SLOT), contentAlignment = Alignment.CenterStart) {
        T(shortMethod(method), color = methodColor(method, p), size = 10, weight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Visible)
    }
}
