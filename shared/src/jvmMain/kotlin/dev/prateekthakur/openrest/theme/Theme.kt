package dev.prateekthakur.openrest.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
class Pal(
    val dark: Boolean,
    val bg: Color,
    val panel: Color,
    val field: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val ok: Color,
    val err: Color,
    val warn: Color,
    val hover: Color,
    val selected: Color,
    val syntaxKey: Color,
    val syntaxString: Color,
    val syntaxNumber: Color,
    val syntaxKeyword: Color,
    val syntaxComment: Color,
)

val DarkPal = Pal(
    dark = true,
    bg = Color(0xFF1B1D21), panel = Color(0xFF23262B), field = Color(0xFF181A1E), border = Color(0xFF34383F),
    text = Color(0xFFE4E6EA), muted = Color(0xFF8B929C), accent = Color(0xFFF5A623), ok = Color(0xFF4CC38A),
    err = Color(0xFFEF5B5B), warn = Color(0xFFF0A04B), hover = Color(0xFF2C3036), selected = Color(0xFF30343B),
    syntaxKey = Color(0xFF7CB7FF), syntaxString = Color(0xFF9FD98F), syntaxNumber = Color(0xFFF2A65A),
    syntaxKeyword = Color(0xFFC792EA), syntaxComment = Color(0xFF6B7380),
)

val LightPal = Pal(
    dark = false,
    bg = Color(0xFFF6F7F9), panel = Color(0xFFFFFFFF), field = Color(0xFFFFFFFF), border = Color(0xFFD9DDE3),
    text = Color(0xFF1F2328), muted = Color(0xFF6B7280), accent = Color(0xFFD97706), ok = Color(0xFF15935B),
    err = Color(0xFFD13438), warn = Color(0xFFC2650A), hover = Color(0xFFEEF0F3), selected = Color(0xFFE4E8EE),
    syntaxKey = Color(0xFF0B63CE), syntaxString = Color(0xFF1A7F37), syntaxNumber = Color(0xFFB45309),
    syntaxKeyword = Color(0xFF8250DF), syntaxComment = Color(0xFF8C959F),
)

val LocalPal = staticCompositionLocalOf { DarkPal }

fun methodColor(m: String, p: Pal): Color = when (m.uppercase()) {
    "GET" -> if (p.dark) Color(0xFF4CC38A) else Color(0xFF15935B)
    "POST" -> if (p.dark) Color(0xFFF5C451) else Color(0xFFB7791F)
    "PUT" -> if (p.dark) Color(0xFF6BA6FF) else Color(0xFF1E6BD6)
    "PATCH" -> if (p.dark) Color(0xFFB794F6) else Color(0xFF7C3AED)
    "DELETE" -> if (p.dark) Color(0xFFEF5B5B) else Color(0xFFD13438)
    "HEAD", "OPTIONS", "TRACE" -> p.muted
    else -> p.muted
}

fun statusColor(code: Int, p: Pal): Color = when (code) {
    in 200..299 -> p.ok
    in 300..399 -> p.accent
    in 400..499 -> p.warn
    else -> p.err
}

@Composable
fun OpenRestTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPal else LightPal
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = Color.Black, background = p.bg, surface = p.panel, onSurface = p.text,
            onBackground = p.text, surfaceVariant = p.hover, onSurfaceVariant = p.muted, outline = p.border,
            surfaceContainer = p.panel, surfaceContainerHigh = p.panel, surfaceContainerHighest = p.panel,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = Color.White, background = p.bg, surface = p.panel, onSurface = p.text,
            onBackground = p.text, surfaceVariant = p.hover, onSurfaceVariant = p.muted, outline = p.border,
            surfaceContainer = p.panel, surfaceContainerHigh = p.panel, surfaceContainerHighest = p.panel,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalPal provides p, content = content)
    }
}
