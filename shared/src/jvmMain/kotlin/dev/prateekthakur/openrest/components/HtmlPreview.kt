package dev.prateekthakur.openrest.components

import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.platform.*
import dev.prateekthakur.openrest.theme.*
import dev.prateekthakur.openrest.utils.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.prateekthakur.openrest.platform.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/** Renders HTML with Swing's HTML engine into an image (HTML 3.2 + basic CSS). Full fidelity: use "Open in browser". */
fun renderHtml(html: String, widthPx: Int): BufferedImage? = try {
    val ed = javax.swing.JEditorPane()
    ed.contentType = "text/html"
    ed.isEditable = false
    ed.text = html
    ed.setSize(widthPx, Short.MAX_VALUE.toInt())
    val h = ed.preferredSize.height.coerceIn(40, 20000)
    ed.setSize(widthPx, h)
    val img = BufferedImage(widthPx, h, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.color = java.awt.Color.WHITE
    g.fillRect(0, 0, widthPx, h)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    ed.paint(g)
    g.dispose()
    img
} catch (_: Throwable) { null }

@Composable
fun HtmlPreview(html: String, bytes: ByteArray, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    BoxWithConstraints(modifier.clip(RoundedCornerShape(5.dp)).background(Color.White).border(1.dp, p.border, RoundedCornerShape(5.dp))) {
        val wPx = constraints.maxWidth
        val bmp by produceState<ImageBitmap?>(null, html, wPx) {
            value = withContext(Dispatchers.Default) { renderHtml(html.take(2_000_000), wPx.coerceAtLeast(200))?.toComposeImageBitmap() }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            bmp?.let { Image(it, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) } ?: Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) { T("Rendering…", color = Color.Gray) }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Btn("Open in browser", { Platform.openBytes(bytes, ".html", browser = true) }, icon = Ic.Open)
        }
        Box(Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
            T("Simplified render (HTML 3.2 / basic CSS, no scripts)", color = Color.White, size = 10)
        }
    }
}
