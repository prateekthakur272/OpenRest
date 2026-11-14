package dev.prateekthakur.openrest.features.request

import dev.prateekthakur.openrest.app.*
import dev.prateekthakur.openrest.components.*
import dev.prateekthakur.openrest.core.http.*
import dev.prateekthakur.openrest.core.interop.*
import dev.prateekthakur.openrest.core.model.*
import dev.prateekthakur.openrest.core.run.*
import dev.prateekthakur.openrest.features.response.*
import dev.prateekthakur.openrest.platform.*
import dev.prateekthakur.openrest.theme.*
import dev.prateekthakur.openrest.utils.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val REQ_TABS = listOf("Params", "Body", "Headers", "Auth", "Vars", "Script", "Assert", "Tests", "Docs", "Settings")

private fun syncPathParams(url: String, old: List<KV>): List<KV> {
    val base = url.substringBefore('?')
    val names = Regex("(?<=/):([A-Za-z_][\\w-]*)").findAll(base).map { it.groupValues[1] }.distinct().toList()
    return names.map { n -> old.firstOrNull { it.name == n } ?: KV(n, "") }
}

@Composable
fun RequestView(state: AppState, tab: RequestTab) {
    val known = remember(tab.draft.preVars, tab.draft.pathParams, state.collections, state.activeEnv.toMap(), state.activeTabId) { state.knownVars(tab) } + state.runtime(tab.collectionPath).keys
    val info = remember(known, tab.draft.preVars, tab.draft.pathParams, state.collections, state.activeEnv.toMap(), state.activeTabId) { state.varInfo(tab).mapValues { VarInfo(it.value.first, it.value.second) } }
    CompositionLocalProvider(LocalVars provides known, LocalVarInfo provides info) {
        Column(Modifier.fillMaxSize()) {
            TitleBar(state, tab)
            UrlBar(state, tab)
            ResolvedBar(state, tab, known)
            SplitPane(
                vertical = state.settings.verticalLayout, fraction = state.splitFraction, onFraction = { state.splitFraction = it },
                first = { RequestPane(state, tab) },
                second = { ResponsePane(state, tab) },
            )
        }
    }
}

@Composable
private fun ResolvedBar(state: AppState, tab: RequestTab, known: Set<String>) {
    val p = LocalPal.current
    val r = tab.draft
    val text = listOf(r.url, r.headers.filter { it.enabled }.joinToString(" ") { it.name + " " + it.value }, r.bodyJson, r.bodyText, r.bodyXml, r.bodyGraphql)
        .joinToString(" ") + r.formUrlEncoded.filter { it.enabled }.joinToString(" ") { it.value }
    val undefined = Variables.names(text).distinct().filter { n -> n !in known && !n.startsWith("\$") && !n.startsWith("process.env.") && !WorkflowRunner.isStepRef(n) }
    val hasVars = r.url.contains("{{") || r.url.contains("/:")
    if (!hasVars && undefined.isEmpty()) return
    val resolved = if (hasVars) state.resolveUrl(tab) else null
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (resolved != null && resolved != r.url) {
            T("→", color = p.muted, size = 12)
            T(resolved, color = p.muted, size = 12, mono = true, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).clickable { Platform.copy(resolved); state.showToast("Resolved URL copied") })
        }
        if (undefined.isNotEmpty()) Chip("undefined: " + undefined.take(4).joinToString(", ") + if (undefined.size > 4) " +${undefined.size - 4}" else "", p.err)
    }
}

@Composable
private fun TitleBar(state: AppState, tab: RequestTab) {
    val p = LocalPal.current
    val col = state.collection(tab.collectionPath)
    val crumbs = listOfNotNull(col?.name) + (col?.ancestorFolders(tab.filePath)?.map { it.name } ?: emptyList())
    var renaming by remember(tab.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        crumbs.forEach { c -> T(c, color = p.muted, size = 12, maxLines = 1); T("/", color = p.muted.copy(alpha = 0.6f), size = 12) }
        if (renaming) InlineRename(tab.draft.name, Modifier.width(240.dp)) { n ->
            renaming = false
            val node = col?.findRequest(tab.filePath)
            if (n != null && n != tab.draft.name && col != null && node != null) state.renameNode(col.path, node, n)
        } else Row(
            Modifier.clip(RoundedCornerShape(4.dp)).clickable { renaming = true }.pointerHoverIcon(PointerIcon.Text).padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            T(tab.draft.name, size = 14, weight = FontWeight.SemiBold, maxLines = 1)
            if (tab.dirty) Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(p.accent))
        }
        Spacer(Modifier.weight(1f))
        IconBtn(Ic.Copy, { state.copyCurl(tab) })
        IconBtn(Ic.Code, { state.modal = Modal.Codegen(tab.id) })
        IconBtn(if (state.settings.verticalLayout) Ic.SplitV else Ic.SplitH, { state.toggleLayout() })
        Btn("Save", { state.save(tab) }, enabled = tab.dirty, icon = Ic.Save)
    }
}

@Composable
private fun UrlBar(state: AppState, tab: RequestTab) {
    val p = LocalPal.current
    val r = tab.draft
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(8.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Dropdown(
            r.method, HTTP_METHODS, { m -> state.update(tab) { it.copy(method = m) } },
            color = methodColor(r.method, p), weight = FontWeight.Bold, optionColor = { methodColor(it, p) }, bordered = false,
        )
        Box(Modifier.width(1.dp).height(20.dp).background(p.border))
        Field(
            r.url,
            onChange = { u ->
                if (u.trimStart().startsWith("curl ")) {
                    val parsed = Importers.parseCurl(u)
                    if (parsed != null) { state.update(tab) { parsed.copy(name = it.name, seq = it.seq, pathParams = syncPathParams(parsed.url, emptyList())) }; return@Field }
                }
                state.update(tab) { it.copy(url = u, params = UrlSync.paramsFromUrl(u, it.params), pathParams = syncPathParams(u, it.pathParams)) }
            },
            modifier = Modifier.weight(1f), placeholder = "Enter URL or paste a cURL command", mono = true, bordered = false, onEnter = { state.send(tab) }, focusRequester = state.urlFocus,
        )
        if (tab.sending) Btn("Cancel", { state.cancel(tab) }, danger = true, icon = Ic.Stop) else Btn("Send", { state.send(tab) }, primary = true, icon = Ic.Send)
    }
}

@Composable
private fun RequestPane(state: AppState, tab: RequestTab) {
    val r = tab.draft
    val p = LocalPal.current
    fun badge(t: String): String? = when (t) {
        "Params" -> r.params.count { it.enabled }.takeIf { it > 0 }?.toString()
        "Headers" -> r.headers.count { it.enabled }.takeIf { it > 0 }?.toString()
        "Body" -> if (r.bodyMode != BodyMode.None) "●" else null
        "Auth" -> if (r.auth.mode != AuthMode.None && r.auth.mode != AuthMode.Inherit) "●" else null
        "Vars" -> (r.preVars.size + r.postVars.size).takeIf { it > 0 }?.toString()
        "Script" -> if (r.preScript.isNotBlank() || r.postScript.isNotBlank()) "●" else null
        "Assert" -> r.assertions.count { it.enabled }.takeIf { it > 0 }?.toString()
        "Tests" -> if (r.tests.isNotBlank()) "●" else null
        "Docs" -> if (r.docs.isNotBlank()) "●" else null
        else -> null
    }
    Column(Modifier.fillMaxSize()) {
        Tabs(REQ_TABS, tab.reqTab, { tab.reqTab = it }, Modifier.fillMaxWidth().padding(horizontal = 6.dp), ::badge)
        Box(Modifier.fillMaxSize()) {
            when (tab.reqTab) {
                "Params" -> ScrollBox {
                    KVTable(r.params, { np -> state.update(tab) { it.copy(params = np, url = UrlSync.urlWithParams(it.url, np)) } }, title = "Query Parameters", encodeTools = true)
                    if (r.pathParams.isNotEmpty()) {
                        Row(Modifier.padding(start = 10.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("PATH PARAMETERS")
                            T("from  :name  segments in the URL", color = p.muted, size = 11)
                        }
                        Column(Modifier.padding(10.dp).widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            r.pathParams.forEachIndexed { i, kv ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    T(":" + kv.name, mono = true, color = p.accent, modifier = Modifier.weight(1f).padding(start = 22.dp))
                                    Field(kv.value, { v -> state.update(tab) { it.copy(pathParams = it.pathParams.toMutableList().also { l -> l[i] = kv.copy(value = v) }) } }, Modifier.weight(1.4f), "Value", mono = true)
                                    Spacer(Modifier.width(24.dp))
                                }
                            }
                        }
                    }
                }
                "Body" -> BodyPane(state, tab)
                "Headers" -> ScrollBox {
                    KVTable(r.headers, { h -> state.update(tab) { it.copy(headers = h) } }, keyHint = "Header", valueHint = "Value", title = "Request Headers", keySuggestions = HEADER_NAMES, keyHints = { HEADER_HINTS[it] }, valueSuggestions = { n -> HEADER_VALUES[n.lowercase()] ?: emptyList() })
                    InheritedHeaders(state, tab)
                }
                "Auth" -> ScrollBox {
                    AuthEditor(r.auth, { a -> state.update(tab) { it.copy(auth = a) } }, AuthMode.entries, Modifier.padding(14.dp), inheritInfo = inheritedAuthInfo(state, tab))
                }
                "Vars" -> Column(Modifier.fillMaxSize()) {
                    Tabs(listOf("Pre Request", "Post Response"), tab.varsTab, { tab.varsTab = it }, Modifier.padding(horizontal = 6.dp))
                    if (tab.varsTab == "Pre Request") {
                        T("Variables set before the request runs. Use them as {{name}}.", color = p.muted, size = 12, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                        ScrollBox { KVTable(r.preVars, { v -> state.update(tab) { it.copy(preVars = v) } }, keyHint = "Name", valueHint = "Value", title = "Pre-request Variables") }
                    } else {
                        T("Set variables from the response using an expression, e.g. res.body.token", color = p.muted, size = 12, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                        ScrollBox { KVTable(r.postVars, { v -> state.update(tab) { it.copy(postVars = v) } }, keyHint = "Name", valueHint = "Expression  (res.body.id)", title = "Post-response Variables") }
                    }
                }
                "Script" -> Column(Modifier.fillMaxSize()) {
                    Tabs(listOf("Pre Request", "Post Response"), tab.scriptTab, { tab.scriptTab = it }, Modifier.padding(horizontal = 6.dp))
                    SnippetBar(if (tab.scriptTab == "Pre Request") Snippets.pre else Snippets.post) { code ->
                        state.update(tab) { if (tab.scriptTab == "Pre Request") it.copy(preScript = append(it.preScript, code)) else it.copy(postScript = append(it.postScript, code)) }
                    }
                    if (tab.scriptTab == "Pre Request")
                        CodeEditor(r.preScript, { v -> state.update(tab) { it.copy(preScript = v) } }, Modifier.fillMaxSize().padding(10.dp), "js", placeholder = "// req.setHeader('X-Time', Date.now());\n// bru.setVar('token', 'abc');")
                    else
                        CodeEditor(r.postScript, { v -> state.update(tab) { it.copy(postScript = v) } }, Modifier.fillMaxSize().padding(10.dp), "js", placeholder = "// bru.setVar('token', res.body.token);\n// console.log(res.status);")
                }
                "Assert" -> Column(Modifier.fillMaxSize()) {
                    T("Declarative checks, e.g.  res.status  eq 200   •   res.body.name  isString   •   res.headers['content-type']  contains json", color = p.muted, size = 12, modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp))
                    ScrollBox { KVTable(r.assertions, { a -> state.update(tab) { it.copy(assertions = a) } }, keyHint = "Expression", valueHint = "Operator and value", title = "Assertions") }
                }
                "Tests" -> Column(Modifier.fillMaxSize()) {
                    SnippetBar(Snippets.tests) { code -> state.update(tab) { it.copy(tests = append(it.tests, code)) } }
                    CodeEditor(r.tests, { v -> state.update(tab) { it.copy(tests = v) } }, Modifier.fillMaxSize().padding(10.dp), "js", placeholder = "test('status is 200', () => {\n  expect(res.status).to.equal(200);\n});")
                }
                "Docs" -> CodeEditor(r.docs, { v -> state.update(tab) { it.copy(docs = v) } }, Modifier.fillMaxSize().padding(10.dp), "text", placeholder = "Document this request…")
                "Settings" -> Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledCheck("Encode URL (escape unsafe characters before sending)", r.encodeUrl) { v -> state.update(tab) { it.copy(encodeUrl = v) } }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        T("Timeout (ms, 0 = use global)")
                        Field(r.timeoutMs.toString(), { v -> state.update(tab) { it.copy(timeoutMs = v.filter { c -> c.isDigit() }.take(7).toIntOrNull() ?: 0) } }, Modifier.width(100.dp), highlightVars = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyPane(state: AppState, tab: RequestTab) {
    val r = tab.draft
    val p = LocalPal.current
    val autoCt = when (r.bodyMode) {
        BodyMode.Json, BodyMode.Graphql -> "application/json"; BodyMode.Text -> "text/plain"; BodyMode.Xml -> "application/xml"
        BodyMode.Form -> "application/x-www-form-urlencoded"; BodyMode.Multipart -> "multipart/form-data"; BodyMode.File -> "application/octet-stream"; BodyMode.None -> null
    }
    val ctOverride = r.headers.any { it.enabled && it.name.equals("content-type", true) }
    val jsonErr: Pair<String, Int>? = remember(r.bodyJson, r.bodyMode) {
        if (r.bodyMode != BodyMode.Json || r.bodyJson.isBlank()) null
        else try { Json.parse(r.bodyJson.replace(Regex("\\{\\{[^}]*}}"), "0")); null } catch (e: Json.JsonException) {
            val pos = Regex("position (\\d+)").find(e.message ?: "")?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val (ln, col) = lineCol(r.bodyJson, pos)
            "${e.message?.substringBefore(" at position")} — line $ln, col $col" to ln
        } catch (e: Exception) { (e.message ?: "invalid") to -1 }
    }
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ---- toolbar: type menu, validity / content-type, editor actions
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionMenu(r.bodyMode.name, BODY_MENU, { id -> state.update(tab) { it.copy(bodyMode = BodyMode.valueOf(id)) } })
            if (r.bodyMode != BodyMode.None) {
            if (r.bodyMode == BodyMode.Json && r.bodyJson.isNotBlank()) {
                val err = jsonErr?.first
                if (err != null) { Ico(Ic.Cancel, tint = p.err, size = 15.dp); T("Invalid JSON: $err", color = p.err, size = 12, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
                else { Ico(Ic.CheckCircle, tint = p.ok, size = 15.dp); T("Valid JSON", color = p.ok, size = 12) }
            }
            if (autoCt != null) T(
                if (ctOverride) "Content-Type taken from Headers" else "Content-Type: $autoCt (auto)",
                color = p.muted, size = 11, mono = true, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            val current = when (r.bodyMode) { BodyMode.Json -> r.bodyJson; BodyMode.Text -> r.bodyText; BodyMode.Xml -> r.bodyXml; BodyMode.Graphql -> r.bodyGraphql; else -> null }
            if (current != null) {
                if (r.bodyMode == BodyMode.Json || r.bodyMode == BodyMode.Xml) {
                    IconBtn(Ic.Edit, {
                        if (r.bodyMode == BodyMode.Json) Json.prettyText(r.bodyJson)?.let { v -> state.update(tab) { it.copy(bodyJson = v) } } ?: state.showToast("Body is not valid JSON")
                        else state.update(tab) { it.copy(bodyXml = runCatching { prettyXml(r.bodyXml) }.getOrDefault(r.bodyXml)) }
                    })
                    if (r.bodyMode == BodyMode.Json) IconBtn(Ic.CollapseAll, {
                        if (Json.isValid(r.bodyJson)) state.update(tab) { it.copy(bodyJson = Json.stringify(Json.parse(r.bodyJson))) }
                        else state.showToast("Minify needs valid JSON without {{variables}}")
                    })
                }
                IconBtn(Ic.Copy, { Platform.copy(current); state.showToast("Body copied") })
            }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (r.bodyMode) {
                BodyMode.None -> Column(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).border(1.dp, p.border, RoundedCornerShape(8.dp)), Arrangement.Center, Alignment.CenterHorizontally) {
                    Ico(Ic.Code, tint = p.muted.copy(alpha = 0.5f), size = 40.dp)
                    Spacer(Modifier.height(8.dp))
                    T("This request has no body", weight = FontWeight.SemiBold)
                    T("Pick a type above, or start with a template", color = p.muted, size = 12, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("JSON", { state.update(tab) { it.copy(bodyMode = BodyMode.Json, bodyJson = it.bodyJson.ifBlank { "{\n  \n}" }) } }, icon = Ic.FmtJson)
                        Btn("Form", { state.update(tab) { it.copy(bodyMode = BodyMode.Form) } }, icon = Ic.Text)
                        Btn("Text", { state.update(tab) { it.copy(bodyMode = BodyMode.Text) } }, icon = Ic.FmtRaw)
                    }
                }
                BodyMode.Json -> CodeEditor(r.bodyJson, { v -> state.update(tab) { it.copy(bodyJson = v) } }, Modifier.fillMaxSize(), "json", placeholder = "{\n  \"key\": \"value\"\n}", showStatus = true, errorLine = jsonErr?.second ?: -1)
                BodyMode.Text -> CodeEditor(r.bodyText, { v -> state.update(tab) { it.copy(bodyText = v) } }, Modifier.fillMaxSize(), "text", showStatus = true)
                BodyMode.Xml -> CodeEditor(r.bodyXml, { v -> state.update(tab) { it.copy(bodyXml = v) } }, Modifier.fillMaxSize(), "xml", placeholder = "<root></root>", showStatus = true)
                BodyMode.Graphql -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("QUERY")
                    CodeEditor(r.bodyGraphql, { v -> state.update(tab) { it.copy(bodyGraphql = v) } }, Modifier.weight(1.4f).fillMaxWidth(), "js", placeholder = "query {\n  viewer { id }\n}", showStatus = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("VARIABLES (JSON)", Modifier.weight(1f))
                        Btn("Prettify", { Json.prettyText(r.bodyGraphqlVars)?.let { v -> state.update(tab) { it.copy(bodyGraphqlVars = v) } } ?: state.showToast("Variables are not valid JSON") }, icon = Ic.Edit)
                    }
                    CodeEditor(r.bodyGraphqlVars, { v -> state.update(tab) { it.copy(bodyGraphqlVars = v) } }, Modifier.weight(1f).fillMaxWidth(), "json")
                }
                BodyMode.Form -> ScrollBox { KVTable(r.formUrlEncoded, { v -> state.update(tab) { it.copy(formUrlEncoded = v) } }, title = "Form Fields", encodeTools = true) }
                BodyMode.Multipart -> ScrollBox {
                    KVTable(
                        r.multipart, { v -> state.update(tab) { it.copy(multipart = v) } }, allowFile = true,
                        onPickFile = { _, set -> Platform.pickFile("Choose file")?.let { set(it.toString()) } }, title = "Form Data",
                    )
                }
                BodyMode.File -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.widthIn(max = 560.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Field(r.bodyFile, { v -> state.update(tab) { it.copy(bodyFile = v) } }, Modifier.weight(1f), "Path to file", mono = true)
                        Btn("Browse…", icon = Ic.Folder, onClick = { Platform.pickFile("Choose file")?.let { f -> state.update(tab) { it.copy(bodyFile = f.toString()) } } })
                    }
                    val f = remember(r.bodyFile) { runCatching { java.io.File(r.bodyFile) }.getOrNull()?.takeIf { it.isFile } }
                    if (f != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ico(Ic.FileGeneric, tint = p.accent, size = 16.dp); T("${f.name}  ·  ${if (f.length() < 1048576) "%.1f KB".format(f.length() / 1024.0) else "%.2f MB".format(f.length() / 1048576.0)}", color = p.muted, size = 12)
                    } else if (r.bodyFile.isNotBlank()) T("File not found", color = p.err, size = 12)
                }
            }
        }
    }
}

private fun authIcon(m: AuthMode) = when (m) {
    AuthMode.None -> Ic.NoBody; AuthMode.Inherit -> Ic.Link; AuthMode.Basic -> Ic.Person; AuthMode.Bearer -> Ic.Key
    AuthMode.ApiKey -> Ic.Key; AuthMode.Digest -> Ic.Fingerprint; AuthMode.OAuth2 -> Ic.Shield
}

/** Section header with an icon tile; optionally collapsible. */
@Composable
private fun AuthSection(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, collapsible: Boolean = false, initiallyOpen: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    var open by remember { mutableStateOf(initiallyOpen || !collapsible) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.then(if (collapsible) Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = !open }.pointerHoverIcon(PointerIcon.Hand) else Modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(p.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Ico(icon, tint = p.accent, size = 13.dp) }
            T(title, size = 13, weight = FontWeight.SemiBold)
            if (collapsible) Chevron(open, color = p.muted)
        }
        if (open) Column(Modifier.fillMaxWidth().padding(start = 30.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun AuthRow(label: String, content: @Composable () -> Unit) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        T(label, color = p.muted, size = 12, modifier = Modifier.width(120.dp))
        Box(Modifier.weight(1f)) { Box(Modifier.widthIn(max = 480.dp)) { content() } }
    }
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, placeholder: String = "") {
    var show by remember { mutableStateOf(false) }
    val p = LocalPal.current
    Box(Modifier.fillMaxWidth()) {
        Field(value, onChange, Modifier.fillMaxWidth(), placeholder, mono = true, password = !show)
        IconBtn(if (show) Ic.Eye else Ic.EyeOff, { show = !show }, Modifier.align(Alignment.CenterEnd).padding(end = 3.dp), color = p.muted)
    }
}

@Composable
fun AuthEditor(auth: Auth, onChange: (Auth) -> Unit, modes: List<AuthMode>, modifier: Modifier = Modifier, inheritLabel: String = "Inherit", inheritInfo: String? = null) {
    val p = LocalPal.current
    fun label(m: AuthMode) = if (m == AuthMode.Inherit) inheritLabel else m.label
    val simple = modes.filter { it == AuthMode.None || it == AuthMode.Inherit }
    val real = modes.filter { it != AuthMode.None && it != AuthMode.Inherit }
    val groups = listOf(simple, real).filter { it.isNotEmpty() }.map { g -> g.map { MenuOpt(it.name, label(it), authIcon(it)) } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
            OptionMenu(auth.mode.name, groups, { id -> onChange(auth.copy(mode = AuthMode.valueOf(id))) }, width = 190.dp)
        }
        when (auth.mode) {
            AuthMode.None -> InfoCard(Ic.NoBody, "No authentication", "This request is sent without any credentials.")
            AuthMode.Inherit -> InfoCard(Ic.Link, "Inherited", inheritInfo ?: "Uses the authentication configured on the parent folder or collection.")
            AuthMode.Basic, AuthMode.Digest -> AuthSection(Ic.Settings, "Configuration") {
                AuthRow("Username") { Field(auth.username, { onChange(auth.copy(username = it)) }, Modifier.fillMaxWidth(), mono = true) }
                AuthRow("Password") { SecretField(auth.password, { onChange(auth.copy(password = it)) }) }
                if (auth.mode == AuthMode.Digest) T("The nonce is obtained from the server's 401 challenge automatically.", color = p.muted, size = 12)
            }
            AuthMode.Bearer -> AuthSection(Ic.Settings, "Configuration") {
                AuthRow("Token") { SecretField(auth.token, { onChange(auth.copy(token = it)) }, "{{token}}") }
                T("Sent as  Authorization: Bearer <token>", color = p.muted, size = 12, mono = true)
            }
            AuthMode.ApiKey -> AuthSection(Ic.Settings, "Configuration") {
                AuthRow("Key") { Field(auth.keyName, { onChange(auth.copy(keyName = it)) }, Modifier.fillMaxWidth(), "X-API-Key", mono = true) }
                AuthRow("Value") { SecretField(auth.keyValue, { onChange(auth.copy(keyValue = it)) }) }
                AuthRow("Add to") {
                    Dropdown(if (auth.placement == "queryparams") "Query Params" else "Header", listOf("Header", "Query Params"), { onChange(auth.copy(placement = if (it == "Header") "header" else "queryparams")) })
                }
            }
            AuthMode.OAuth2 -> {
                AuthSection(Ic.Settings, "Configuration") {
                    AuthRow("Grant type") { Dropdown(auth.grantType, listOf("client_credentials", "password"), { onChange(auth.copy(grantType = it)) }) }
                    AuthRow("Access token URL") { Field(auth.tokenUrl, { onChange(auth.copy(tokenUrl = it)) }, Modifier.fillMaxWidth(), "https://auth.example.com/oauth/token", mono = true) }
                    AuthRow("Client ID") { Field(auth.clientId, { onChange(auth.copy(clientId = it)) }, Modifier.fillMaxWidth(), mono = true) }
                    AuthRow("Client secret") { SecretField(auth.clientSecret, { onChange(auth.copy(clientSecret = it)) }) }
                    if (auth.grantType == "password") {
                        AuthRow("Username") { Field(auth.username, { onChange(auth.copy(username = it)) }, Modifier.fillMaxWidth(), mono = true) }
                        AuthRow("Password") { SecretField(auth.password, { onChange(auth.copy(password = it)) }) }
                    }
                }
                AuthSection(Ic.Tune, "Advanced", collapsible = true, initiallyOpen = auth.scope.isNotBlank()) {
                    AuthRow("Scope") { Field(auth.scope, { onChange(auth.copy(scope = it)) }, Modifier.fillMaxWidth(), "read write", mono = true) }
                    T("The access token is fetched automatically and cached for this session.", color = p.muted, size = 12)
                }
            }
        }
    }
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(p.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Ico(icon, tint = p.accent, size = 14.dp) }
        Column { T(title, size = 13, weight = FontWeight.SemiBold); T(text, color = p.muted, size = 12, modifier = Modifier.padding(top = 1.dp)) }
    }
}

private val BODY_MENU = listOf(
    listOf(MenuOpt(BodyMode.None.name, "No Body", Ic.NoBody)),
    listOf(
        MenuOpt(BodyMode.Json.name, "JSON", Ic.FmtJson), MenuOpt(BodyMode.Xml.name, "XML", Ic.FmtXml),
        MenuOpt(BodyMode.Text.name, "Text", Ic.FmtRaw), MenuOpt(BodyMode.Graphql.name, "GraphQL", Ic.Graph),
    ),
    listOf(MenuOpt(BodyMode.Form.name, "Form URL Encoded", Ic.FormBody), MenuOpt(BodyMode.Multipart.name, "Multipart Form", Ic.Attach)),
    listOf(MenuOpt(BodyMode.File.name, "Binary File", Ic.FileGeneric)),
)

private val BODY_SHORT = mapOf(
    BodyMode.None to "None", BodyMode.Json to "JSON", BodyMode.Text to "Text", BodyMode.Xml to "XML", BodyMode.Form to "Form",
    BodyMode.Multipart to "Multipart", BodyMode.Graphql to "GraphQL", BodyMode.File to "Binary",
)

private val HEADER_NAMES = listOf(
    "Accept", "Accept-Encoding", "Accept-Language", "Authorization", "Cache-Control", "Connection", "Content-Type", "Cookie", "DNT", "Host",
    "If-Modified-Since", "If-None-Match", "Origin", "Pragma", "Range", "Referer", "User-Agent", "X-API-Key", "X-Correlation-Id",
    "X-Forwarded-For", "X-Request-Id", "X-Requested-With",
)

private val HEADER_HINTS = mapOf(
    "Accept" to "Media types the client accepts", "Accept-Encoding" to "Compression the client supports", "Accept-Language" to "Preferred natural languages",
    "Authorization" to "Credentials for authenticating", "Cache-Control" to "Caching directives", "Connection" to "Connection management",
    "Content-Type" to "Media type of the request body", "Cookie" to "Cookies sent to the server", "DNT" to "Do Not Track preference",
    "Host" to "Target host and port", "If-Modified-Since" to "Conditional request by date", "If-None-Match" to "Conditional request by ETag",
    "Origin" to "Origin of the request (CORS)", "Pragma" to "Legacy cache directive", "Range" to "Request part of a resource",
    "Referer" to "URL of the referring page", "User-Agent" to "Client software identification", "X-API-Key" to "API key credential",
    "X-Correlation-Id" to "Trace id across services", "X-Forwarded-For" to "Original client IP via proxy",
    "X-Request-Id" to "Unique request id", "X-Requested-With" to "Marks AJAX requests",
)

private val HEADER_VALUES = mapOf(
    "content-type" to listOf("application/json", "application/x-www-form-urlencoded", "multipart/form-data", "application/xml", "text/plain", "text/html", "application/octet-stream"),
    "accept" to listOf("application/json", "*/*", "text/html", "application/xml", "text/plain"),
    "authorization" to listOf("Bearer ", "Basic "),
    "cache-control" to listOf("no-cache", "no-store", "max-age=0", "max-age=3600"),
    "accept-encoding" to listOf("gzip, deflate, br", "gzip", "identity"),
    "connection" to listOf("keep-alive", "close"),
    "x-requested-with" to listOf("XMLHttpRequest"),
)

private fun inheritedAuthInfo(state: AppState, tab: RequestTab): String {
    val col = state.collection(tab.collectionPath) ?: return "Inherits from the parent folder or collection."
    for (f in col.ancestorFolders(tab.filePath).reversed()) {
        val m = f.config.auth.mode
        if (m != AuthMode.None && m != AuthMode.Inherit) return "Inherited from folder “${f.name}”: ${m.label}"
    }
    val m = col.config.auth.mode
    return if (m == AuthMode.None || m == AuthMode.Inherit) "Inherited: no authentication is configured on the folder or collection." else "Inherited from collection “${col.name}”: ${m.label}"
}

@Composable
private fun InheritedHeaders(state: AppState, tab: RequestTab) {
    val col = state.collection(tab.collectionPath) ?: return
    val rows = col.config.headers.filter { it.enabled }.map { TRow(it.name, it.value, "collection") } +
        col.ancestorFolders(tab.filePath).flatMap { f -> f.config.headers.filter { it.enabled }.map { TRow(it.name, it.value, f.name) } }
    if (rows.isEmpty()) return
    ReadOnlyTable(
        rows, Modifier.padding(start = 10.dp, end = 10.dp, bottom = 12.dp), keyHint = "Header", valueHint = "Value", noteHint = "From",
        title = "Inherited Headers", onCopy = { Platform.copy(it); state.showToast("Copied") },
    )
}

private fun append(existing: String, code: String) = if (existing.isBlank()) code else existing.trimEnd() + "\n\n" + code

private object Snippets {
    val pre = listOf(
        "Set header" to "req.setHeader('X-Request-Id', Date.now().toString());",
        "Set variable" to "bru.setVar('name', 'value');",
        "Bearer from variable" to "req.setHeader('Authorization', 'Bearer ' + bru.getVar('token'));",
        "Random id" to "bru.setVar('rid', Math.random().toString(36).slice(2, 10));",
        "Modify JSON body" to "const body = req.getBody();\nbody.timestamp = new Date().toISOString();\nreq.setBody(body);",
        "Wait" to "await bru.sleep(500);",
    )
    val post = listOf(
        "Save token from response" to "bru.setVar('token', res.body.token);",
        "Save environment variable" to "bru.setEnvVar('userId', res.body.id);",
        "Log response" to "console.log(res.status, res.body);",
        "Run next request by name" to "bru.setNextRequest('Request name');",
        "Stop collection run" to "bru.runner.stopExecution();",
    )
    val tests = listOf(
        "Status is 200" to "test('status is 200', () => {\n  expect(res.status).to.equal(200);\n});",
        "Response time under 500ms" to "test('fast response', () => {\n  expect(res.responseTime).to.be.below(500);\n});",
        "Body has property" to "test('has id', () => {\n  expect(res.body).to.have.property('id');\n});",
        "Content-Type is JSON" to "test('content type is json', () => {\n  expect(res.getHeader('content-type')).to.include('application/json');\n});",
        "Array is not empty" to "test('returns items', () => {\n  expect(res.body).to.be.an('array');\n  expect(res.body.length).to.be.above(0);\n});",
        "Field types" to "test('field types', () => {\n  expect(res.body.id).to.be.a('number');\n  expect(res.body.name).to.be.a('string');\n});",
    )
}

@Composable
private fun SnippetBar(items: List<Pair<String, String>>, onInsert: (String) -> Unit) {
    val p = LocalPal.current
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 6.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            Btn("Snippets", { open = true }, icon = Ic.Code)
            ContextMenu(open, { open = false }, items.map { (label, code) -> MenuItem(label) { onInsert(code) } })
        }
        T("bru · req · res · expect · test() · console", color = p.muted, size = 11, mono = true)
    }
}
