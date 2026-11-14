package dev.prateekthakur.openrest.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.core.*
import kotlin.io.path.writeBytes

private fun fmtSize(n: Int): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "%.1f KB".format(n / 1024.0)
    else -> "%.2f MB".format(n / 1024.0 / 1024.0)
}

fun prettyXml(s: String): String {
    val sb = StringBuilder()
    var depth = 0
    val tokens = Regex("<[^>]+>|[^<]+").findAll(s.trim().replace(Regex(">\\s+<"), "><")).map { it.value }.toList()
    for (t in tokens) {
        if (t.startsWith("</")) { depth = maxOf(0, depth - 1); sb.append("  ".repeat(depth)).append(t).append('\n') }
        else if (t.startsWith("<") && !t.startsWith("<?") && !t.startsWith("<!") && !t.endsWith("/>")) { sb.append("  ".repeat(depth)).append(t).append('\n'); depth++ }
        else sb.append("  ".repeat(depth)).append(t.trim()).append('\n')
    }
    // join "<a>text</a>" onto one line
    return sb.toString().replace(Regex("(<[^/!?][^>]*[^/]>)\\n\\s*([^<\\n]+)\\n\\s*(</[^>]+>)"), "$1$2$3").trimEnd()
}

private fun hintFor(msg: String): String? = when {
    msg.contains("Could not connect", true) || msg.contains("Connection refused", true) -> "Is the server running? Check the host and port, and that the environment is the one you expect."
    msg.contains("timed out", true) -> "The server did not answer in time. Raise the timeout in Preferences or in this request's Settings tab."
    msg.contains("PKIX", true) || msg.contains("SSLHandshake", true) || msg.contains("certificate", true) -> "Self-signed or untrusted certificate? Turn off 'Verify SSL certificates' in Preferences."
    msg.contains("Invalid URL", true) -> "A URL needs a scheme and host, e.g. https://api.example.com/users. Unresolved {{variables}} also cause this."
    msg.contains("UnknownHost", true) || msg.contains("nodename", true) -> "The host name could not be resolved. Check for typos, VPN or proxy settings."
    else -> null
}

@Composable
fun ResponsePane(state: AppState, tab: RequestTab) {
    val p = LocalPal.current
    val result = tab.responses.getOrNull(tab.viewing) ?: tab.result
    Column(Modifier.fillMaxSize()) {
        when {
            tab.sending -> Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(28.dp), color = p.accent, strokeWidth = 3.dp)
                Spacer(Modifier.height(10.dp))
                T("Sending request…", color = p.muted)
                Spacer(Modifier.height(10.dp))
                Btn("Cancel", { state.cancel(tab) }, danger = true, icon = Ic.Stop)
            }
            result == null -> Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                T("Response", size = 16, weight = FontWeight.SemiBold, color = p.muted)
                Spacer(Modifier.height(6.dp))
                T("Press Send or ⌘/Ctrl + Enter to execute the request", color = p.muted, size = 12)
            }
            result.response == null || result.response.error != null || result.error != null -> {
                val msg = result.error ?: result.response?.error ?: "Request did not run"
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(if (result.skipped) "SKIPPED" else "ERROR", p.err)
                    T(msg, color = p.err, mono = true)
                    hintFor(msg)?.let { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Ico(Ic.Bulb, tint = p.accent, size = 16.dp); T(it, color = p.muted, size = 12) } }
                    if (result.tests.isNotEmpty()) TestsList(result.tests)
                    if (result.logs.isNotEmpty()) T(result.logs.joinToString("\n"), mono = true, color = p.muted)
                }
            }
            else -> ResponseBody(state, tab, result, result.response)
        }
    }
}

@Composable
private fun ResponseBody(state: AppState, tab: RequestTab, result: ExecResult, res: HttpResult) {
    val p = LocalPal.current
    val passed = result.tests.count { it.passed }
    val tabs = listOf("Response", "Headers", "Cookies", "Timeline", "Tests", "Console")
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip("${res.status} ${res.statusText}".trim(), statusColor(res.status, p))
            T("${res.timeMs} ms", color = p.muted, size = 12)
            T(fmtSize(res.size), color = p.muted, size = 12)
            if (result.tests.isNotEmpty()) Box(Modifier.clickable { tab.resTab = "Tests" }.pointerHoverIcon(PointerIcon.Hand)) {
                Chip("${passed}/${result.tests.size} tests", if (passed == result.tests.size) p.ok else p.err)
            }
            Spacer(Modifier.weight(1f))
            if (tab.responses.size > 1) {
                val labels = tab.responses.mapIndexed { i, r -> (if (i == 0) "Latest" else "#$i") + "  " + (r.response?.status?.takeIf { r.response.error == null }?.toString() ?: "ERR") + " · " + (r.response?.timeMs ?: 0) + "ms" }
                Dropdown(labels[tab.viewing.coerceIn(0, labels.size - 1)], labels, { l -> tab.viewing = labels.indexOf(l) })
            }
            val older = tab.responses.getOrNull(tab.viewing + 1)?.response?.takeIf { it.error == null }
            if (older != null) Btn(if (tab.diff) "Hide Diff" else "Diff", { tab.diff = !tab.diff }, icon = Ic.Compare)
            Btn("Copy", { Platform.copy(res.text); state.showToast("Response copied") }, icon = Ic.Copy)
            Btn("Save", icon = Ic.Download, onClick = {
                Platform.saveFile("Save response", "response" + extFor(res.contentType))?.let { f -> runCatching { f.writeBytes(res.body) }.onSuccess { state.showToast("Saved ${f.fileName}") } }
            })
        }
        Tabs(
            tabs, tab.resTab, { tab.resTab = it }, Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            badge = { t ->
                when (t) {
                    "Headers" -> res.headers.size.toString()
                    "Tests" -> if (result.tests.isNotEmpty()) "$passed/${result.tests.size}" else null
                    "Console" -> result.logs.size.takeIf { it > 0 }?.toString()
                    else -> null
                }
            },
        )
        Box(Modifier.fillMaxSize().padding(8.dp)) {
            when (tab.resTab) {
                "Response" -> {
                    val olderRes = tab.responses.getOrNull(tab.viewing + 1)?.response?.takeIf { it.error == null }
                    if (tab.diff && olderRes != null) DiffView(bodyForDiff(olderRes), bodyForDiff(res), Modifier.fillMaxSize()) else BodyView(state, tab, res)
                }
                "Headers" -> ScrollBox {
                    ReadOnlyTable(
                        res.headers.sortedBy { it.first.lowercase() }.map { TRow(it.first, it.second) },
                        Modifier.padding(4.dp), keyHint = "Header", valueHint = "Value", title = "Response Headers",
                        onCopy = { Platform.copy(it); state.showToast("Copied") },
                    )
                }
                "Cookies" -> {
                    val cookies = res.headers.filter { it.first.equals("set-cookie", true) }.map { it.second }
                    ScrollBox {
                        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (cookies.isEmpty()) T("No cookies were set by this response.", color = p.muted)
                            cookies.forEach { c ->
                                val parts = c.split(";").map { it.trim() }
                                Column {
                                    T(parts.first(), mono = true, weight = FontWeight.SemiBold)
                                    T(parts.drop(1).joinToString("  •  "), mono = true, color = p.muted, size = 12)
                                }
                            }
                        }
                    }
                }
                "Timeline" -> ScrollBox {
                    val rq = result.request
                    Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (rq != null) {
                            T("${rq.method} ${rq.url}", mono = true, weight = FontWeight.SemiBold)
                            rq.headers.forEach { (k, v) -> T("$k: $v", mono = true, color = p.muted) }
                            when (val b = rq.body) {
                                is Body.Raw -> { Spacer(Modifier.height(4.dp)); T(b.text, mono = true) }
                                is Body.Form -> T(HttpExecutor.formEncode(b.fields), mono = true)
                                is Body.Multipart -> b.parts.forEach { T("${it.name} = ${if (it.isFile) "@" else ""}${it.value}", mono = true) }
                                is Body.FileBody -> T("<file: ${b.path}>", mono = true)
                                Body.None -> {}
                            }
                            Spacer(Modifier.height(8.dp))
                            T("← ${res.status} ${res.statusText}  (${res.timeMs} ms)", mono = true, color = statusColor(res.status, p), weight = FontWeight.SemiBold)
                            if (res.finalUrl != rq.url) T("Final URL: ${res.finalUrl}", mono = true, color = p.muted)
                        }
                    }
                }
                "Tests" -> ScrollBox { if (result.tests.isEmpty()) T("No tests or assertions ran.", color = p.muted, modifier = Modifier.padding(6.dp)) else TestsList(result.tests) }
                "Console" -> ScrollBox {
                    if (result.logs.isEmpty()) T("Nothing logged. Use console.log() in scripts.", color = p.muted, modifier = Modifier.padding(6.dp))
                    else T(result.logs.joinToString("\n"), mono = true, modifier = Modifier.padding(6.dp))
                }
            }
        }
    }
}

private fun extFor(ct: String) = when {
    ct.contains("json") -> ".json"
    ct.contains("html") -> ".html"
    ct.contains("xml") -> ".xml"
    ct.contains("png") -> ".png"
    ct.contains("jpeg") -> ".jpg"
    ct.contains("pdf") -> ".pdf"
    ct.contains("text") -> ".txt"
    else -> ".bin"
}

private fun bodyForDiff(r: HttpResult): String = r.text.take(300_000).let { Json.prettyText(it) ?: it }

@Composable
fun TestsList(tests: List<TestResult>) {
    val p = LocalPal.current
    Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tests.forEach { t ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ico(if (t.passed) Ic.CheckCircle else Ic.Cancel, tint = if (t.passed) p.ok else p.err, size = 16.dp)
                Column {
                    T(t.name, mono = true)
                    if (!t.passed && t.message.isNotBlank()) T(t.message, color = p.err, mono = true, size = 12)
                }
            }
        }
    }
}

@Composable
private fun BodyView(state: AppState, tab: RequestTab, res: HttpResult) {
    val p = LocalPal.current
    val ct = res.contentType.lowercase()
    val isImage = ct.startsWith("image/") && !ct.contains("svg")
    val isDoc = ct.contains("pdf") || ct.startsWith("audio/") || ct.startsWith("video/")
    val isBinary = !isImage && !isDoc && res.body.take(2000).any { it.toInt() == 0 }
    val isMedia = isDoc || isBinary
    val text = remember(res) { if (isImage || isMedia) "" else res.text }
    val parsed = remember(res) { if (text.length < 8_000_000 && (ct.contains("json") || text.trimStart().let { it.startsWith("{") || it.startsWith("[") })) Json.tryParse(text) else null }
    val isJson = parsed != null
    val isXml = !isJson && (ct.contains("xml") || text.trimStart().startsWith("<?xml"))
    val isHtml = ct.contains("html") || (!isJson && !isXml && text.trimStart().take(200).lowercase().let { it.startsWith("<!doctype html") || it.startsWith("<html") })
    val isJs = ct.contains("javascript") || ct.contains("ecmascript")

    val allFormats = TEXT_FORMATS + BINARY_FORMATS
    val detected = when { isImage || isMedia -> "Hex"; isJson -> "JSON"; isHtml -> "HTML"; isXml -> "XML"; isJs -> "JavaScript"; else -> "Raw" }
    val fmt = if (tab.resView in allFormats) tab.resView else detected
    val previewable = isImage || isMedia || (fmt == "JSON" && isJson) || (fmt == "HTML" && isHtml)
    val preview = previewable && (tab.resPreview ?: (isImage || isMedia))
    val jsonMode = fmt == "JSON" && isJson
    var expandSignal by remember(res) { mutableStateOf(0) }
    val filtered = remember(parsed, tab.filter) { if (!isJson) null else if (tab.filter.isBlank()) parsed else JsonPath.query(parsed, tab.filter) }
    val shown = remember(res, fmt, tab.filter) {
        when (fmt) {
            "JSON" -> if (isJson) { if (filtered is String && tab.filter.isNotBlank()) filtered else if (text.length > 3_000_000 && tab.filter.isBlank()) text else Json.stringify(filtered, true) } else text
            "XML" -> if (isXml || text.trimStart().startsWith("<")) runCatching { prettyXml(text) }.getOrDefault(text) else text
            "Hex" -> hexDump(res.body)
            "Base64" -> base64Wrapped(res.body.let { if (it.size > 2_000_000) it.copyOf(2_000_000) else it })
            else -> text
        }
    }
    val lang = when (fmt) { "JSON" -> if (isJson) "json" else "text"; "HTML", "XML" -> "xml"; "JavaScript" -> "js"; else -> "text" }
    val textual = !preview
    val matches = remember(shown, tab.find, textual) { if (tab.find.isBlank() || !textual) emptyList() else codeLines(shown).mapIndexedNotNull { i, l -> if (l.contains(tab.find, true)) i else null } }
    var cur by remember(tab.find, shown) { mutableStateOf(0) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormatMenu(
                current = fmt, preview = preview, previewEnabled = previewable,
                onFormat = { tab.resView = it; if (it in BINARY_FORMATS) tab.resPreview = false },
                onPreview = { tab.resPreview = it },
            )
            if (jsonMode && !isMedia) Field(tab.filter, { tab.filter = it }, Modifier.weight(1f), placeholder = "Filter  e.g.  \$.data.items[0].name", mono = true, highlightVars = false)
            else Spacer(Modifier.weight(1f))
            if (preview && jsonMode) {
                IconBtn(Ic.ExpandAll, { expandSignal = if (expandSignal > 0) expandSignal + 1 else 1 })
                IconBtn(Ic.CollapseAll, { expandSignal = if (expandSignal < 0) expandSignal - 1 else -1 })
            }
            if (textual) {
                Field(tab.find, { tab.find = it }, Modifier.width(150.dp), "Find…", highlightVars = false, onEnter = { if (matches.isNotEmpty()) cur = (cur + 1) % matches.size })
                if (tab.find.isNotBlank()) {
                    T(if (matches.isEmpty()) "0" else "${cur + 1}/${matches.size}", color = if (matches.isEmpty()) p.err else p.muted, size = 11)
                    IconBtn(Ic.Up, { if (matches.isNotEmpty()) cur = (cur - 1 + matches.size) % matches.size })
                    IconBtn(Ic.Down, { if (matches.isNotEmpty()) cur = (cur + 1) % matches.size })
                }
            }
        }
        if (res.size == 0) { T("(empty response body)", color = p.muted); return@Column }
        when {
            preview && isImage -> ImagePreview(res, Modifier.fillMaxSize())
            preview && isMedia -> MediaPreview(res, Modifier.fillMaxSize())
            preview && fmt == "HTML" -> HtmlPreview(text, res.body, Modifier.fillMaxSize())
            preview && jsonMode -> JsonTree(filtered, Modifier.fillMaxSize(), expandSignal) { v, msg -> Platform.copy(v); state.showToast(msg) }
            else -> CodeView(shown, lang, Modifier.fillMaxSize(), query = tab.find, focusLine = matches.getOrNull(cur) ?: -1)
        }
    }
}
