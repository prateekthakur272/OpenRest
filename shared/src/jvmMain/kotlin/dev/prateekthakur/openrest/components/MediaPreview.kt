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

@Composable
fun ImagePreview(res: HttpResult, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val density = LocalDensity.current
    val bmp = remember(res) { runCatching { org.jetbrains.skia.Image.makeFromEncoded(res.body).toComposeImageBitmap() }.getOrNull() }
    var fit by remember { mutableStateOf(true) }
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(5.dp))) {
        if (bmp == null) { T("Cannot render this image format", color = p.muted, modifier = Modifier.align(Alignment.Center)); return@Box }
        if (fit) Image(bmp, null, Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit)
        else Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
            Image(bmp, null, Modifier.size(with(density) { bmp.width.toDp() }, with(density) { bmp.height.toDp() }))
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(p.panel.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 5.dp)) {
                T("${bmp.width} × ${bmp.height}  ·  ${res.contentType.substringBefore(';').substringAfter('/').uppercase()}", color = p.muted, size = 11)
            }
            Btn(if (fit) "Actual size" else "Fit", { fit = !fit })
        }
    }
}

// ======================= pdf / audio / video / binary =======================

@Composable
fun MediaPreview(res: HttpResult, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val ct = res.contentType.substringBefore(';').lowercase()
    val (icon, label, ext) = when {
        ct.contains("pdf") -> Triple(Ic.Pdf, "PDF document", ".pdf")
        ct.startsWith("audio/") -> Triple(Ic.Audio, "Audio", "." + ct.substringAfter('/').substringBefore('+').ifEmpty { "bin" })
        ct.startsWith("video/") -> Triple(Ic.Video, "Video", "." + ct.substringAfter('/').substringBefore('+').ifEmpty { "bin" })
        else -> Triple(Ic.FileGeneric, "Binary data", ".bin")
    }
    Column(modifier.clip(RoundedCornerShape(5.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(5.dp)), Arrangement.Center, Alignment.CenterHorizontally) {
        Ico(icon, tint = p.accent, size = 56.dp)
        Spacer(Modifier.height(10.dp))
        T(label, size = 15, weight = FontWeight.SemiBold)
        T("${ct.ifEmpty { "unknown type" }}  ·  ${if (res.size < 1024) "${res.size} B" else if (res.size < 1048576) "%.1f KB".format(res.size / 1024.0) else "%.2f MB".format(res.size / 1048576.0)}", color = p.muted, size = 12, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
        Btn("Open with default app", { Platform.openBytes(res.body, ext) }, primary = true, icon = Ic.Open)
        T("This type can't be rendered inline. Use Save in the header to keep a copy.", color = p.muted, size = 11, modifier = Modifier.padding(top = 10.dp))
    }
}
