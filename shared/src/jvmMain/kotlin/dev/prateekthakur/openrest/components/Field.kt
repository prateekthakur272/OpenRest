package dev.prateekthakur.openrest.components

import dev.prateekthakur.openrest.core.http.*
import dev.prateekthakur.openrest.theme.*

import androidx.compose.foundation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.prateekthakur.openrest.core.http.Variables

val LocalVars = compositionLocalOf<Set<String>> { emptySet() }

class VarTransform(private val pal: Pal, private val known: Set<String>, private val flow: Set<String> = emptySet()) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (!text.text.contains("{{") && (flow.isEmpty() || !text.text.contains('{'))) return TransformedText(text, OffsetMapping.Identity)
        val ab = AnnotatedString.Builder(text)
        if (flow.isNotEmpty()) Regex("(?<!\\{)\\{([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_\\[\\]-]+)*)}").findAll(text.text).forEach { m ->
            if (m.groupValues[1].substringBefore('.') in flow) ab.addStyle(SpanStyle(color = pal.accent, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
        }
        Regex("\\{\\{\\s*([^{}]+?)\\s*}}").findAll(text.text).forEach { m ->
            val n = m.groupValues[1]
            val ok = n in known || n.startsWith("\$") || n.startsWith("process.env.") || dev.prateekthakur.openrest.core.run.WorkflowRunner.isStepRef(n) || (n.contains('.') && n.substringBefore('.') in known) || Variables.dynamic(n) != null
            ab.addStyle(SpanStyle(color = if (ok) pal.ok else pal.err, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
        }
        return TransformedText(ab.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private val DYNAMIC_VARS = listOf("\$guid", "\$timestamp", "\$isoTimestamp", "\$randomInt", "\$randomUUID", "\$randomEmail", "\$randomBoolean", "\$randomFirstName", "\$randomLastName")

private val varPrefixRe = Regex("\\{\\{\\s*([\\w.\\$-]*)$")

private val flowPrefixRe = Regex("(?<!\\{)\\{([\\w.]*)$")

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    mono: Boolean = false,
    password: Boolean = false,
    bordered: Boolean = true,
    onEnter: (() -> Unit)? = null,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    highlightVars: Boolean = true,
    suggestions: List<String> = emptyList(),
    hints: (String) -> String? = { null },
) {
    val p = LocalPal.current
    val known = LocalVars.current
    val varInfo = LocalVarInfo.current
    val flowNames = LocalFlowVars.current
    val flowInfo = LocalFlowInfo.current
    val style = TextStyle(color = p.text, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = if (mono) FontFamily.Monospace else null)
    var tfv by remember { mutableStateOf(TextFieldValue(value)) }
    val cur = if (tfv.text == value) tfv else TextFieldValue(value, TextRange(value.length))
    var focused by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var anchorSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val overlay = LocalMenuOverlay.current
    val token = remember { Any() }
    DisposableEffect(Unit) { onDispose { overlay?.hide(token) } }
    var selIdx by remember { mutableStateOf(0) }
    var dismissedAt by remember { mutableStateOf(-1) }
    val cursor = cur.selection.end.coerceIn(0, cur.text.length)
    val match = if (highlightVars && !password && focused && cur.selection.collapsed && dismissedAt != cursor) varPrefixRe.find(cur.text.substring(0, cursor)) else null
    val varSugg = match?.let { m ->
        val q = m.groupValues[1]
        (known + DYNAMIC_VARS).filter { it.startsWith(q, true) && it != q }.sortedWith(compareBy({ it.contains('.') }, { it })).take(12)
    } ?: emptyList()
    val isVar = varSugg.isNotEmpty()
    // single brace: values a workflow passed on, completed from `{`
    val fmatch = if (!isVar && match == null && flowNames.isNotEmpty() && highlightVars && !password && focused && cur.selection.collapsed && dismissedAt != cursor) flowPrefixRe.find(cur.text.substring(0, cursor)) else null
    val flowSugg = fmatch?.let { m -> flowNames.filter { it.startsWith(m.groupValues[1], true) && it != m.groupValues[1] }.sortedWith(compareBy({ it.contains('.') }, { it })).take(12) } ?: emptyList()
    val isFlow = flowSugg.isNotEmpty()
    val textSugg = if (!isVar && !isFlow && suggestions.isNotEmpty() && focused && !password && dismissedAt != cursor) {
        val q = cur.text.trim()
        suggestions.filter { it.contains(q, true) && !it.equals(q, false) }.take(8)
    } else emptyList()
    val sugg = if (isVar) varSugg else if (isFlow) flowSugg else textSugg
    fun accept(name: String) {
        if (isFlow) {
            val m = fmatch ?: return
            var rest = cur.text.substring(cursor)
            if (rest.startsWith("}")) rest = rest.removePrefix("}")
            val nt = cur.text.substring(0, m.range.first) + "{" + name + "}" + rest
            tfv = TextFieldValue(nt, TextRange(m.range.first + name.length + 2))
            onChange(nt)
            return
        }
        if (!isVar) { tfv = TextFieldValue(name, TextRange(name.length)); onChange(name); dismissedAt = name.length; return }
        val m = match ?: return
        var rest = cur.text.substring(cursor)
        if (rest.startsWith("}}")) rest = rest.removePrefix("}}")
        val nt = cur.text.substring(0, m.range.first) + "{{" + name + "}}" + rest
        tfv = TextFieldValue(nt, TextRange(m.range.first + name.length + 4))
        onChange(nt)
    }
    var m = modifier
        .onGloballyPositioned { anchor = it.positionInWindow(); anchorSize = it.size }
        .clip(RoundedCornerShape(5.dp))
        .background(if (bordered) p.field else Color.Transparent)
        .then(if (bordered) Modifier.border(1.dp, p.border, RoundedCornerShape(5.dp)) else Modifier)
    if (focusRequester != null) m = m.focusRequester(focusRequester)
    m = m.onFocusChanged { focused = it.isFocused }.onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        if (sugg.isNotEmpty()) when (e.key) {
            Key.DirectionDown -> { selIdx = (selIdx + 1) % sugg.size; return@onPreviewKeyEvent true }
            Key.DirectionUp -> { selIdx = (selIdx - 1 + sugg.size) % sugg.size; return@onPreviewKeyEvent true }
            Key.Enter, Key.Tab -> { accept(sugg[selIdx.coerceIn(0, sugg.size - 1)]); return@onPreviewKeyEvent true }
            Key.Escape -> { dismissedAt = cursor; return@onPreviewKeyEvent true }
            else -> {}
        }
        if (onEnter != null && e.key == Key.Enter && !e.isMetaPressed && !e.isCtrlPressed) { onEnter(); true } else false
    }
    BasicTextField(
        value = cur, onValueChange = { tfv = it; selIdx = 0; if (it.text != value) onChange(it.text) }, modifier = m, singleLine = true, textStyle = style,
        cursorBrush = SolidColor(p.accent),
        visualTransformation = when {
            password -> androidx.compose.ui.text.input.PasswordVisualTransformation()
            highlightVars -> remember(p, known, flowNames) { VarTransform(p, known, flowNames) }
            else -> VisualTransformation.None
        },
        decorationBox = { inner ->
            Box(Modifier.heightIn(min = CONTROL_H).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = p.muted.copy(alpha = 0.7f)), maxLines = 1)
                inner()
                val selNow = selIdx.coerceIn(0, maxOf(0, sugg.size - 1))  // read during composition so arrow keys repaint the menu
                SideEffect {
                    if (sugg.isNotEmpty() && overlay != null) {
                        val q = if (isVar) (match?.groupValues?.get(1) ?: "") else if (isFlow) (fmatch?.groupValues?.get(1) ?: "") else cur.text.trim()
                        val menuItems = sugg.map { n ->
                            if (isFlow) Sug(n, flowInfo[n]?.value?.take(70), flowInfo[n]?.source, true)
                            else if (isVar) {
                                val vi = varInfo[n]
                                Sug(n, DYNAMIC_HINTS[n] ?: vi?.value?.take(70), if (n.startsWith("$")) "dynamic" else vi?.source, true)
                            } else Sug(n, hints(n), null, false)
                        }
                        val sel = selNow
                        val est = minOf(sugg.size, 7) * 40f * 1f + 64f
                        overlay.show(token, anchor.x, anchor.y, anchor.y + anchorSize.height, est) {
                            SuggestionMenu(menuItems, sel, q, if (isFlow) "Passed-on values" else if (isVar) "Variables" else "Suggestions", { accept(sugg[it]) }, { selIdx = it })
                        }
                    } else overlay?.hide(token)
                }
            }
        },
    )
}

data class VarInfo(val value: String, val source: String)

val LocalVarInfo = compositionLocalOf<Map<String, VarInfo>> { emptyMap() }

/** Names usable as single-brace `{name}` (values a workflow passes on), with what to show beside each. */
val LocalFlowVars = compositionLocalOf<Set<String>> { emptySet() }

val LocalFlowInfo = compositionLocalOf<Map<String, VarInfo>> { emptyMap() }

private val DYNAMIC_HINTS = mapOf(
    "\$guid" to "Random UUID", "\$randomUUID" to "Random UUID", "\$timestamp" to "Unix time in seconds", "\$isoTimestamp" to "ISO-8601 date-time",
    "\$randomInt" to "Random number 0–999", "\$randomEmail" to "Random email address", "\$randomBoolean" to "true or false",
    "\$randomFirstName" to "Random first name", "\$randomLastName" to "Random last name",
)

class Sug(val text: String, val detail: String?, val tag: String?, val isVar: Boolean)

@Composable
private fun SuggestionMenu(items: List<Sug>, selected: Int, query: String, title: String, onPick: (Int) -> Unit, onHover: (Int) -> Unit) {
    val p = LocalPal.current
    androidx.compose.material3.Surface(
        modifier = Modifier.width(380.dp).testTag("suggestion-menu"),
        shape = RoundedCornerShape(10.dp), color = p.panel, shadowElevation = 12.dp, border = BorderStroke(1.dp, p.border),
    ) {
        Column {
            Row(Modifier.fillMaxWidth().background(p.hover.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                T(title.uppercase(), color = p.muted, size = 10, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                T("${items.size}", color = p.muted, size = 10)
            }
            val scroll = rememberScrollState()
            val rowPx = with(LocalDensity.current) { 40.dp.toPx() }
            LaunchedEffect(selected) { scroll.animateScrollTo(((selected - 3) * rowPx).toInt().coerceAtLeast(0)) }
            Column(Modifier.heightIn(max = 288.dp).verticalScroll(scroll)) {
                items.forEachIndexed { i, it0 ->
                    val src = remember { MutableInteractionSource() }
                    val hov by src.collectIsHoveredAsState()
                    LaunchedEffect(hov) { if (hov) onHover(i) }
                    val sel = i == selected
                    Row(
                        Modifier.fillMaxWidth().height(40.dp).background(if (sel) p.accent.copy(alpha = 0.14f) else Color.Transparent).hoverable(src)
                            .pointerInput(Unit) { detectTapGestures { onPick(i) } }.pointerHoverIcon(PointerIcon.Hand)
                            .drawBehind { if (sel) drawRect(p.accent, androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Ico(if (it0.isVar) Ic.Variables else Ic.Key, tint = if (it0.isVar) (if (it0.text.startsWith("$")) p.syntaxKeyword else p.ok) else p.accent, size = 17.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                            val label = androidx.compose.ui.text.buildAnnotatedString {
                                append(it0.text)
                                if (query.isNotEmpty()) {
                                    val at = it0.text.indexOf(query, 0, true)
                                    if (at >= 0) addStyle(SpanStyle(color = p.accent, fontWeight = FontWeight.Bold), at, at + query.length)
                                }
                            }
                            Text(label, color = p.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(lineHeight = 17.sp))
                            if (!it0.detail.isNullOrEmpty()) Text(it0.detail, color = p.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(lineHeight = 14.sp))
                        }
                        if (it0.tag != null) Box(Modifier.clip(RoundedCornerShape(4.dp)).background(p.muted.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                            T(it0.tag, color = p.muted, size = 10)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                T("↑↓ move", color = p.muted, size = 10); T("↵ / Tab insert", color = p.muted, size = 10); T("esc dismiss", color = p.muted, size = 10)
            }
        }
    }
}

@Composable
fun AreaField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "", mono: Boolean = true) {
    val p = LocalPal.current
    BasicTextField(
        value = value, onValueChange = onChange,
        modifier = modifier.clip(RoundedCornerShape(5.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(5.dp)),
        textStyle = TextStyle(color = p.text, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = if (mono) FontFamily.Monospace else null),
        cursorBrush = SolidColor(p.accent),
        decorationBox = { inner ->
            Box(Modifier.padding(8.dp)) {
                if (value.isEmpty()) Text(placeholder, style = TextStyle(color = p.muted.copy(alpha = 0.7f), fontSize = 13.sp, lineHeight = 18.sp, fontFamily = if (mono) FontFamily.Monospace else null))
                inner()
            }
        },
    )
}
