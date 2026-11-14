package dev.prateekthakur.openrest.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.prateekthakur.openrest.core.KV
import dev.prateekthakur.openrest.core.UrlCodec
import dev.prateekthakur.openrest.core.Variables
import java.awt.Cursor

/** Height shared by buttons, dropdowns and single-line text fields. */
val CONTROL_H = 32.dp

val LocalVars = compositionLocalOf<Set<String>> { emptySet() }

@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPal.current.text,
    size: Int = 13,
    weight: FontWeight? = null,
    mono: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) = Text(
    text, modifier, color = color, fontSize = size.sp, fontWeight = weight,
    fontFamily = if (mono) FontFamily.Monospace else null, maxLines = maxLines, overflow = overflow,
)

@Composable
fun Btn(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed && enabled) 0.96f else 1f, androidx.compose.animation.core.tween(80))
    val base = if (primary) p.accent else p.hover
    val bg by androidx.compose.animation.animateColorAsState(
        when { !enabled -> base.copy(alpha = 0.4f); hovered && primary -> androidx.compose.ui.graphics.lerp(base, Color.White, 0.18f); hovered -> p.selected; else -> base },
        androidx.compose.animation.core.tween(100),
    )
    val fg = when { primary -> Color.Black; danger -> p.err; else -> p.text }
    Box(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .then(if (!primary) Modifier.border(1.dp, if (hovered && enabled) p.muted.copy(alpha = 0.6f) else p.border, RoundedCornerShape(5.dp)) else Modifier)
            .hoverable(src)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .height(CONTROL_H).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val c = if (enabled) fg else fg.copy(alpha = 0.5f)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Ico(icon, tint = c, size = 15.dp)
            if (label.isNotEmpty()) T(label, color = c, size = 13, weight = if (primary) FontWeight.SemiBold else null)
        }
    }
}

@Composable
fun IconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = LocalPal.current.muted, size: Int = 16) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Box(
        modifier.size(26.dp).clip(RoundedCornerShape(5.dp)).background(if (hovered) p.hover else Color.Transparent)
            .hoverable(src).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, tint = if (hovered) p.text else color, size = size.dp) }
}

@Composable
fun Check(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, partial: Boolean = false) {
    val p = LocalPal.current
    val on = checked || partial
    Box(
        modifier.size(16.dp).clip(RoundedCornerShape(3.dp))
            .background(if (on) p.accent else Color.Transparent)
            .border(1.dp, if (on) p.accent else p.muted, RoundedCornerShape(3.dp))
            .clickable { onChange(!checked) }.pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) { if (partial) Ico(Ic.Dash, tint = Color.Black, size = 13.dp) else if (checked) Ico(Ic.Check, tint = Color.Black, size = 13.dp) }
}

@Composable
fun LabeledCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.clickable { onChange(!checked) }.padding(vertical = 2.dp)) {
        Check(checked, onChange)
        T(label)
    }
}

class VarTransform(private val pal: Pal, private val known: Set<String>, private val flow: Set<String> = emptySet()) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (!text.text.contains("{{") && (flow.isEmpty() || !text.text.contains('{'))) return TransformedText(text, OffsetMapping.Identity)
        val ab = AnnotatedString.Builder(text)
        if (flow.isNotEmpty()) Regex("(?<!\\{)\\{([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_\\[\\]-]+)*)}").findAll(text.text).forEach { m ->
            if (m.groupValues[1].substringBefore('.') in flow) ab.addStyle(SpanStyle(color = pal.accent, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
        }
        Regex("\\{\\{\\s*([^{}]+?)\\s*}}").findAll(text.text).forEach { m ->
            val n = m.groupValues[1]
            val ok = n in known || n.startsWith("\$") || n.startsWith("process.env.") || dev.prateekthakur.openrest.core.WorkflowRunner.isStepRef(n) || (n.contains('.') && n.substringBefore('.') in known) || Variables.dynamic(n) != null
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

/** Root-level host for transient menus (autocomplete). Lives in the same composition, never blocks input outside the menu. */
class MenuOverlay {
    var owner: Any? by mutableStateOf(null)
    var x by mutableStateOf(0f)
    var yTop by mutableStateOf(0f)
    var yBottom by mutableStateOf(0f)
    var estHeight by mutableStateOf(0f)
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
    fun show(token: Any, x: Float, yTop: Float, yBottom: Float, estHeight: Float, content: @Composable () -> Unit) {
        owner = token; this.x = x; this.yTop = yTop; this.yBottom = yBottom; this.estHeight = estHeight; this.content = content
    }
    fun hide(token: Any) { if (owner === token) { owner = null; content = null } }
}

val LocalMenuOverlay = staticCompositionLocalOf<MenuOverlay?> { null }

@Composable
fun MenuOverlayHost(overlay: MenuOverlay) {
    val c = overlay.content ?: return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxW = constraints.maxWidth.toFloat(); val maxH = constraints.maxHeight.toFloat()
        val menuW = with(LocalDensity.current) { 380.dp.toPx() }
        val flip = overlay.yBottom + overlay.estHeight > maxH && overlay.yTop - overlay.estHeight > 0
        val x = overlay.x.coerceAtMost((maxW - menuW - 8f).coerceAtLeast(0f)).coerceAtLeast(0f)
        val y = if (flip) overlay.yTop - overlay.estHeight - 4f else overlay.yBottom + 4f
        Box(Modifier.offset { IntOffset(x.toInt(), y.toInt()) }) { c() }
    }
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

@Composable
fun Tabs(
    items: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    badge: (String) -> String? = { null },
) {
    val p = LocalPal.current
    Row(modifier.horizontalScroll(rememberScrollState()).drawBottomBorder(p.border), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { t ->
            val sel = t == selected
            Column(
                Modifier.clickable { onSelect(t) }.pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 10.dp).padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    T(t, color = if (sel) p.text else p.muted, weight = if (sel) FontWeight.SemiBold else null)
                    badge(t)?.let { b -> T(b, color = p.accent, size = 11, weight = FontWeight.SemiBold) }
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.height(2.dp).fillMaxWidth().background(if (sel) p.accent else Color.Transparent))
            }
        }
    }
}

fun Modifier.drawBottomBorder(c: Color): Modifier = this.drawBehind {
    drawLine(c, androidx.compose.ui.geometry.Offset(0f, size.height - 0.5f), androidx.compose.ui.geometry.Offset(size.width, size.height - 0.5f), 1f)
}

@Composable
fun Dropdown(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = LocalPal.current.text,
    weight: FontWeight? = null,
    optionColor: (String) -> Color = { Color.Unspecified },
    bordered: Boolean = true,
) {
    val p = LocalPal.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.clip(RoundedCornerShape(5.dp)).then(if (bordered) Modifier.background(p.field).border(1.dp, p.border, RoundedCornerShape(5.dp)) else Modifier)
                .clickable { open = true }.pointerHoverIcon(PointerIcon.Hand).height(CONTROL_H).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            T(selected, color = color, weight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Ico(Ic.DropDown, tint = p.muted, size = 16.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = p.panel) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { T(o, color = optionColor(o).let { if (it == Color.Unspecified) p.text else it }, weight = if (o == selected) FontWeight.SemiBold else null) },
                    onClick = { open = false; onSelect(o) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
fun ModalBox(title: String, onClose: () -> Unit, width: Dp = 560.dp, height: Dp? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onClose(); true } else false },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(width).then(if (height != null) Modifier.height(height) else Modifier.heightIn(max = 760.dp))
                .clip(RoundedCornerShape(10.dp)).background(p.panel).border(1.dp, p.border, RoundedCornerShape(10.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Row(Modifier.fillMaxWidth().drawBottomBorder(p.border).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                T(title, size = 15, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconBtn(Ic.Close, onClose)
            }
            Column(Modifier.weight(1f, fill = height != null).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

@Composable
fun SplitPane(
    vertical: Boolean,
    fraction: Float,
    onFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    val p = LocalPal.current
    val density = LocalDensity.current
    var total by remember { mutableStateOf(1f) }
    val frac by rememberUpdatedState(fraction)
    val cb by rememberUpdatedState(onFraction)
    Box(modifier.fillMaxSize().onSizeChanged { total = (if (vertical) it.height else it.width).toFloat().coerceAtLeast(1f) }) {
        val firstDp = with(density) { (total * fraction).toDp() }
        val handle = 5.dp
        if (vertical) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(firstDp)) { first() }
                Box(
                    Modifier.fillMaxWidth().height(handle).background(p.border).pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)))
                        .pointerInput(vertical) { detectDragGestures { c, d -> c.consume(); cb((frac + d.y / total).coerceIn(0.12f, 0.88f)) } },
                )
                Box(Modifier.fillMaxWidth().weight(1f)) { second() }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxHeight().width(firstDp)) { first() }
                Box(
                    Modifier.fillMaxHeight().width(handle).background(p.border).pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
                        .pointerInput(vertical) { detectDragGestures { c, d -> c.consume(); cb((frac + d.x / total).coerceIn(0.12f, 0.88f)) } },
                )
                Box(Modifier.fillMaxHeight().weight(1f)) { second() }
            }
        }
    }
}

@Composable
private fun DecodeChip(onClick: () -> Unit) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Row(
        Modifier.padding(end = 6.dp).clip(RoundedCornerShape(10.dp)).background(p.accent.copy(alpha = if (hov) 0.3f else 0.16f)).hoverable(src)
            .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Ico(Ic.Codec, tint = p.accent, size = 12.dp)
        T("Decode", color = p.accent, size = 10, weight = FontWeight.SemiBold)
    }
}

private val ROW_H = 34.dp
private val HANDLE_W = 22.dp
private val CHECK_W = 26.dp

/** A cell with a focus underline; content is a borderless Field. */
@Composable
private fun TableCell(modifier: Modifier, content: @Composable () -> Unit) {
    val p = LocalPal.current
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.fillMaxHeight().onFocusChanged { focused = it.hasFocus }.drawBehind {
            if (focused) {
                drawRect(p.accent.copy(alpha = 0.07f))
                drawRect(p.accent, androidx.compose.ui.geometry.Offset(0f, size.height - 2.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx()))
            }
            drawLine(p.border, androidx.compose.ui.geometry.Offset(size.width - 0.5f, 0f), androidx.compose.ui.geometry.Offset(size.width - 0.5f, size.height), 1f)
        },
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

@Composable
fun KVTable(
    items: List<KV>,
    onChange: (List<KV>) -> Unit,
    modifier: Modifier = Modifier,
    keyHint: String = "Name",
    valueHint: String = "Value",
    allowFile: Boolean = false,
    onPickFile: ((KV, (String) -> Unit) -> Unit)? = null,
    keySuggestions: List<String> = emptyList(),
    valueSuggestions: (String) -> List<String> = { emptyList() },
    keyHints: (String) -> String? = { null },
    encodeTools: Boolean = false,
    title: String? = null,
    extra: (@Composable RowScope.(Int, KV) -> Unit)? = null,
) {
    val p = LocalPal.current
    val density = LocalDensity.current
    var bulk by remember { mutableStateOf(false) }
    var bulkText by remember { mutableStateOf("") }
    var keyFrac by remember { mutableStateOf(0.4f) }
    var tableW by remember { mutableStateOf(1f) }
    var dragIdx by remember { mutableStateOf(-1) }
    val press = remember { floatArrayOf(0f, 0f) }
    val itemsRef by rememberUpdatedState(items)
    val changeRef by rememberUpdatedState(onChange)
    val rowPx = with(density) { ROW_H.toPx() }
    val handlePx = with(density) { HANDLE_W.toPx() }
    val extraW = if (extra != null) 74.dp else 0.dp
    val actionsW = (if (allowFile) 60.dp else 34.dp) + (if (encodeTools) 26.dp else 0.dp)
    val fixedPx = with(density) { (HANDLE_W + CHECK_W + extraW + actionsW).toPx() }

    fun toggleBulk() {
        if (!bulk) bulkText = items.joinToString("\n") { (if (it.enabled) "" else "//") + it.name + ": " + it.value }
        bulk = !bulk
    }
    fun update(i: Int, f: (KV) -> KV) = onChange(if (i >= items.size) items + f(KV()) else items.toMutableList().also { l -> l[i] = f(l[i]) })

    Column(modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ---- toolbar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) SectionLabel(title.uppercase())
            if (items.isNotEmpty()) {
                val on = items.count { it.enabled }
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(p.accent.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    T(if (on == items.size) "${items.size}" else "$on/${items.size}", color = p.accent, size = 10, weight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            if (encodeTools && items.isNotEmpty() && !bulk) {
                var codecMenu by remember { mutableStateOf(false) }
                Box {
                    IconBtn(Ic.Codec, { codecMenu = true })
                    ContextMenu(codecMenu, { codecMenu = false }, listOf(
                        MenuItem("Decode all (URL)") { onChange(items.map { it.copy(name = UrlCodec.decode(it.name), value = UrlCodec.decode(it.value)) }) },
                        MenuItem("Encode all (URL)") { onChange(items.map { it.copy(name = UrlCodec.encode(it.name), value = UrlCodec.encode(it.value)) }) },
                    ))
                }
            }
            if (!allowFile) IconBtn(Ic.Edit, { toggleBulk() }, color = if (bulk) p.accent else p.muted)
            if (!bulk) Btn("Add", { onChange(items + KV()) }, icon = Ic.Add)
        }
        if (bulk) {
            T("One  name: value  per line · prefix a line with // to disable it", color = p.muted, size = 11)
            AreaField(
                bulkText,
                { t ->
                    bulkText = t
                    onChange(t.split("\n").filter { it.isNotBlank() }.map { line ->
                        val off = line.trimStart().startsWith("//")
                        val body = line.trimStart().removePrefix("//").trim()
                        val i = body.indexOf(':')
                        if (i < 0) KV(body, "", !off) else KV(body.substring(0, i).trim(), body.substring(i + 1).trim(), !off)
                    })
                },
                Modifier.fillMaxWidth().heightIn(min = 160.dp), "Name: value",
            )
            return@Column
        }

        // ---- the grid
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(8.dp))
                .onSizeChanged { tableW = it.width.toFloat() },
        ) {
            // header
            Row(Modifier.fillMaxWidth().height(30.dp).background(p.hover.copy(alpha = 0.5f)).drawBottomBorder(p.border), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(HANDLE_W))
                Box(Modifier.width(CHECK_W), contentAlignment = Alignment.Center) {
                    val all = items.isNotEmpty() && items.all { it.enabled }
                    val some = items.any { it.enabled } && !all
                    Check(all, { v -> onChange(items.map { it.copy(enabled = v || some) }) }, Modifier.alpha(if (items.isEmpty()) 0.3f else 1f), partial = some)
                }
                Row(Modifier.weight(keyFrac).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    T(keyHint.uppercase(), color = p.muted, size = 10, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    // column resize handle
                    Box(
                        Modifier.width(7.dp).fillMaxHeight().pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
                            .pointerInput(Unit) { detectDragGestures { c, d -> c.consume(); keyFrac = (keyFrac + d.x / (tableW - fixedPx).coerceAtLeast(1f)).coerceIn(0.15f, 0.85f) } },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.width(1.dp).fillMaxHeight(0.55f).background(p.border)) }
                }
                T(valueHint.uppercase(), color = p.muted, size = 10, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f - keyFrac).padding(start = 10.dp))
                Spacer(Modifier.width(extraW + actionsW))
            }

            // rows (+ one always-ready empty row); drag a row's handle to reorder
            Column(
                Modifier.testTag("kv-rows")
                    .pointerInput(Unit) { awaitEachGesture { val d = awaitFirstDown(requireUnconsumed = false); press[0] = d.position.x; press[1] = d.position.y } }
                    .pointerInput(Unit) {
                        var acc = 0f
                        detectDragGestures(
                            onDragStart = {
                                val i = (press[1] / rowPx).toInt()
                                if (press[0] < handlePx && i < itemsRef.size) { dragIdx = i; acc = 0f } else dragIdx = -1
                            },
                            onDragEnd = { dragIdx = -1 }, onDragCancel = { dragIdx = -1 },
                        ) { c, d ->
                            if (dragIdx >= 0) {
                                c.consume()
                                acc += d.y
                                val l = itemsRef.toMutableList()
                                var moved = false
                                fun swap(a: Int, b: Int) { val t = l[a]; l[a] = l[b]; l[b] = t; moved = true }
                                while (acc > rowPx / 2 && dragIdx < l.size - 1) { swap(dragIdx, dragIdx + 1); dragIdx++; acc -= rowPx }
                                while (acc < -rowPx / 2 && dragIdx > 0) { swap(dragIdx, dragIdx - 1); dragIdx--; acc += rowPx }
                                if (moved) changeRef(l)
                            }
                        }
                    },
            ) {
                for (i in 0..items.size) key(i) {
                    val ghost = i >= items.size
                    val kv = items.getOrNull(i) ?: KV()
                    val src = remember { MutableInteractionSource() }
                    val hov by src.collectIsHoveredAsState()
                    val dragging = dragIdx == i
                    Row(
                        Modifier.fillMaxWidth().height(ROW_H).hoverable(src)
                            .background(if (dragging) p.selected else if (hov && !ghost) p.hover.copy(alpha = 0.35f) else Color.Transparent)
                            .drawBehind {
                                drawLine(p.border.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset(0f, size.height - 0.5f), androidx.compose.ui.geometry.Offset(size.width, size.height - 0.5f), 1f)
                                if (dragging) drawRect(p.accent, androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(HANDLE_W), contentAlignment = Alignment.Center) {
                            if (!ghost && (hov || dragging)) Ico(Ic.Drag, tint = p.muted, size = 16.dp, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand))
                        }
                        Box(Modifier.width(CHECK_W), contentAlignment = Alignment.Center) {
                            Check(kv.enabled && !ghost, { update(i) { k -> k.copy(enabled = it) } }, Modifier.alpha(if (ghost) 0.25f else 1f))
                        }
                        val dim = Modifier.alpha(if (kv.enabled || ghost) 1f else 0.45f)
                        TableCell(Modifier.weight(keyFrac)) {
                            Row(Modifier.fillMaxWidth().then(dim), verticalAlignment = Alignment.CenterVertically) {
                                Field(kv.name, { n -> update(i) { k -> k.copy(name = n) } }, Modifier.weight(1f), keyHint, mono = true, bordered = false, suggestions = keySuggestions, hints = keyHints)
                                if (encodeTools && !ghost && UrlCodec.looksEncoded(kv.name)) DecodeChip { update(i) { k -> k.copy(name = UrlCodec.decode(k.name)) } }
                            }
                        }
                        TableCell(Modifier.weight(1f - keyFrac)) {
                            if (kv.isFile) Row(Modifier.fillMaxWidth().then(dim), verticalAlignment = Alignment.CenterVertically) {
                                Field(kv.value, { v -> update(i) { k -> k.copy(value = v) } }, Modifier.weight(1f), "File path", mono = true, bordered = false, highlightVars = false)
                                IconBtn(Ic.Folder, { onPickFile?.invoke(kv) { path -> update(i) { k -> k.copy(value = path) } } })
                            } else Row(Modifier.fillMaxWidth().then(dim), verticalAlignment = Alignment.CenterVertically) {
                                Field(kv.value, { v -> update(i) { k -> k.copy(value = v) } }, Modifier.weight(1f), valueHint, mono = true, bordered = false, suggestions = valueSuggestions(kv.name))
                                if (encodeTools && !ghost && UrlCodec.looksEncoded(kv.value)) DecodeChip { update(i) { k -> k.copy(value = UrlCodec.decode(k.value)) } }
                            }
                        }
                        if (extra != null) Row(Modifier.width(extraW).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) { if (!ghost) extra.invoke(this, i, kv) }
                        Row(Modifier.width(actionsW), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            if (!ghost) {
                                if (allowFile) IconBtn(if (kv.isFile) Ic.Attach else Ic.Text, { update(i) { k -> k.copy(isFile = !k.isFile, value = "") } }, color = if (kv.isFile) p.accent else p.muted, size = 15)
                                if (encodeTools && hov) {
                                    var rowMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconBtn(Ic.Codec, { rowMenu = true }, size = 15)
                                        ContextMenu(rowMenu, { rowMenu = false }, listOf(
                                            MenuItem("Decode value") { update(i) { k -> k.copy(value = UrlCodec.decode(k.value)) } },
                                            MenuItem("Encode value") { update(i) { k -> k.copy(value = UrlCodec.encode(k.value)) } },
                                            MenuItem("Decode name") { update(i) { k -> k.copy(name = UrlCodec.decode(k.name)) } },
                                            MenuItem("Encode name") { update(i) { k -> k.copy(name = UrlCodec.encode(k.name)) } },
                                        ))
                                    }
                                }
                                if (hov) IconBtn(Ic.Delete, { onChange(items.toMutableList().also { l -> l.removeAt(i) }) }, color = p.err, size = 16)
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }
            }
        }
    }
}

class TRow(val name: String, val value: String, val note: String = "")

/** Read-only version of the key/value grid (same look): response headers, inherited headers, etc. */
@Composable
fun ReadOnlyTable(
    rows: List<TRow>,
    modifier: Modifier = Modifier,
    keyHint: String = "Name",
    valueHint: String = "Value",
    noteHint: String? = null,
    title: String? = null,
    onCopy: (String) -> Unit = {},
) {
    val p = LocalPal.current
    val density = LocalDensity.current
    var keyFrac by remember { mutableStateOf(0.32f) }
    var tableW by remember { mutableStateOf(1f) }
    val noteW = if (noteHint != null) 110.dp else 0.dp
    val fixedPx = with(density) { (34.dp + noteW).toPx() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(title.uppercase())
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(p.accent.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                T("${rows.size}", color = p.accent, size = 10, weight = FontWeight.Bold)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(8.dp)).onSizeChanged { tableW = it.width.toFloat() }) {
            Row(Modifier.fillMaxWidth().height(30.dp).background(p.hover.copy(alpha = 0.5f)).drawBottomBorder(p.border), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(keyFrac).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    T(keyHint.uppercase(), color = p.muted, size = 10, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Box(
                        Modifier.width(7.dp).fillMaxHeight().pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
                            .pointerInput(Unit) { detectDragGestures { c, d -> c.consume(); keyFrac = (keyFrac + d.x / (tableW - fixedPx).coerceAtLeast(1f)).coerceIn(0.15f, 0.85f) } },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.width(1.dp).fillMaxHeight(0.55f).background(p.border)) }
                }
                T(valueHint.uppercase(), color = p.muted, size = 10, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f - keyFrac).padding(start = 10.dp))
                if (noteHint != null) T(noteHint.uppercase(), color = p.muted, size = 10, weight = FontWeight.SemiBold, modifier = Modifier.width(noteW).padding(start = 10.dp))
                Spacer(Modifier.width(34.dp))
            }
            SelectionContainer {
                Column {
                    rows.forEach { r ->
                        val src = remember { MutableInteractionSource() }
                        val hov by src.collectIsHoveredAsState()
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = ROW_H).hoverable(src).background(if (hov) p.hover.copy(alpha = 0.35f) else Color.Transparent)
                                .drawBottomBorder(p.border.copy(alpha = 0.6f)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            T(r.name, color = p.syntaxKey, mono = true, modifier = Modifier.weight(keyFrac).padding(horizontal = 12.dp, vertical = 8.dp))
                            T(r.value, mono = true, modifier = Modifier.weight(1f - keyFrac).padding(horizontal = 10.dp, vertical = 8.dp))
                            if (noteHint != null) T(r.note, color = p.muted, size = 11, modifier = Modifier.width(noteW).padding(start = 10.dp), maxLines = 1)
                            Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                                if (hov) IconBtn(Ic.Copy, { onCopy(r.value) }, size = 15)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- syntax highlighting ----------------

private val jsonRe = Regex("\"(?:\\\\.|[^\"\\\\])*\"(\\s*:)?|-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?|\\b(?:true|false|null)\\b")
private val xmlRe = Regex("<!--.*?-->|</?[A-Za-z_][\\w:.-]*|/?>|\\s[\\w:.-]+(?==)|\"[^\"]*\"|'[^']*'")
private val jsRe = Regex("//.*|/\\*.*?\\*/|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`[^`]*`|\\b\\d+(?:\\.\\d+)?\\b|\\b(?:const|let|var|function|return|if|else|for|while|await|async|new|try|catch|throw|true|false|null|undefined|typeof|of|in|class|import|from|switch|case|break|default)\\b")
private val varRe = Regex("\\{\\{\\s*([^{}]+?)\\s*}}")

private class LangSpec(
    val kw: String, val line: List<String> = listOf("//"), val block: Boolean = true, val triple: Boolean = false, val backtick: Boolean = false,
    val dollarVar: Boolean = false, val flags: Boolean = false, val cmdlet: Boolean = false, val types: Boolean = true,
)

private val LANGS: Map<String, Regex> by lazy {
    val specs = mapOf(
        "py" to LangSpec("import|from|as|def|class|return|if|else|elif|for|while|in|not|and|or|is|None|True|False|with|try|except|finally|raise|lambda|yield|pass|print", listOf("#"), block = false, triple = true),
        "java" to LangSpec("import|package|public|private|protected|static|final|class|new|void|return|if|else|for|while|try|catch|throw|throws|var|int|long|boolean|null|true|false|extends|implements|interface|this"),
        "kt" to LangSpec("val|var|fun|class|object|import|package|return|if|else|for|while|try|catch|throw|null|true|false|when|is|in|as|it|override|private|public|internal|data|use", triple = true),
        "go" to LangSpec("package|import|func|var|const|type|struct|interface|return|if|else|for|range|go|defer|nil|true|false|map|chan|select|switch|case|default", backtick = true),
        "php" to LangSpec("function|return|echo|if|else|foreach|as|new|class|true|false|null|array|use|curl_init|curl_exec", listOf("//", "#"), dollarVar = true),
        "cs" to LangSpec("using|namespace|var|new|await|async|return|if|else|foreach|in|class|public|private|static|void|string|int|bool|null|true|false|try|catch|throw"),
        "rb" to LangSpec("require|def|end|class|module|return|if|else|elsif|unless|do|while|puts|nil|true|false|begin|rescue", listOf("#"), block = false),
        "swift" to LangSpec("import|let|var|func|class|struct|return|if|else|guard|for|in|while|nil|true|false|try|catch|throw|as", triple = true),
        "rs" to LangSpec("let|mut|fn|use|mod|struct|enum|impl|pub|return|if|else|match|for|in|while|loop|async|await|true|false|self|Self"),
        "dart" to LangSpec("import|void|main|async|await|final|var|const|class|return|if|else|for|while|null|true|false|new"),
        "ps" to LangSpec("function|if|else|foreach|in|return|param|true|false|null", listOf("#"), block = false, dollarVar = true, flags = true, cmdlet = true, types = false),
        "sh" to LangSpec("curl|wget|http|https|echo|export", listOf("#"), block = false, dollarVar = true, flags = true, types = false),
    )
    specs.mapValues { (_, sp) ->
        val comment = (sp.line.map { Regex.escape(it) + ".*" } + (if (sp.block) listOf("/\\*[\\s\\S]*?\\*/") else emptyList())).joinToString("|")
        val str = buildList {
            if (sp.triple) { add("\"\"\"[\\s\\S]*?\"\"\""); add("'''[\\s\\S]*?'''") }
            add("\"(?:\\\\.|[^\"\\\\])*\""); add("'(?:\\\\.|[^'\\\\])*'")
            if (sp.backtick) add("`[^`]*`")
        }.joinToString("|")
        val parts = mutableListOf("(?<c>$comment)", "(?<s>$str)")
        if (sp.dollarVar) parts += "(?<v>\\$\\{[^}]*}|\\$[A-Za-z_][\\w]*)"
        parts += "(?<n>\\b\\d+(?:\\.\\d+)?\\b)"
        if (sp.cmdlet) parts += "(?<k2>\\b[A-Z][a-z]+-[A-Z][A-Za-z]+\\b)"
        parts += "(?<k>\\b(?:${sp.kw})\\b)"
        if (sp.flags) parts += "(?<f>(?<=\\s)--?[A-Za-z][\\w-]*)"
        if (sp.types) parts += "(?<t>\\b[A-Z][A-Za-z0-9_]*\\b)"
        Regex(parts.joinToString("|"))
    }
}

fun highlight(text: String, lang: String, p: Pal, known: Set<String> = emptySet(), query: String = ""): AnnotatedString {
    val b = AnnotatedString.Builder(text)
    fun span(r: IntRange, c: Color, bold: Boolean = false) = b.addStyle(SpanStyle(color = c, fontWeight = if (bold) FontWeight.SemiBold else null), r.first, r.last + 1)
    LANGS[lang]?.let { re ->
        re.findAll(text).forEach { m ->
            fun has(n: String) = try { m.groups[n] != null } catch (_: IllegalArgumentException) { false }   // group absent in this language
            when {
                has("c") -> span(m.range, p.syntaxComment)
                has("s") -> span(m.range, p.syntaxString)
                has("v") || has("n") -> span(m.range, p.syntaxNumber)
                has("k") || has("k2") -> span(m.range, p.syntaxKeyword)
                has("f") || has("t") -> span(m.range, p.syntaxKey)
            }
        }
    }
    when (lang) {
        "json" -> jsonRe.findAll(text).forEach { m ->
            val s = m.value
            when {
                s.startsWith("\"") -> if (m.groups[1] != null) span(m.range.first..(m.range.first + s.trimEnd(':', ' ').length - 1), p.syntaxKey) else span(m.range, p.syntaxString)
                s[0].isDigit() || s[0] == '-' -> span(m.range, p.syntaxNumber)
                else -> span(m.range, p.syntaxKeyword)
            }
        }
        "xml", "html" -> xmlRe.findAll(text).forEach { m ->
            val s = m.value
            when {
                s.startsWith("<!--") -> span(m.range, p.syntaxComment)
                s.startsWith("<") || s.endsWith(">") -> span(m.range, p.syntaxKey)
                s.startsWith("\"") || s.startsWith("'") -> span(m.range, p.syntaxString)
                else -> span(m.range, p.syntaxNumber)
            }
        }
        "js" -> jsRe.findAll(text).forEach { m ->
            val s = m.value
            when {
                s.startsWith("//") || s.startsWith("/*") -> span(m.range, p.syntaxComment)
                s[0] == '"' || s[0] == '\'' || s[0] == '`' -> span(m.range, p.syntaxString)
                s[0].isDigit() -> span(m.range, p.syntaxNumber)
                else -> span(m.range, p.syntaxKeyword)
            }
        }
    }
    if (text.contains("{{")) varRe.findAll(text).forEach { m ->
        val n = m.groupValues[1]
        val ok = n in known || n.startsWith("\$") || n.startsWith("process.env.") || dev.prateekthakur.openrest.core.WorkflowRunner.isStepRef(n) || (n.contains('.') && n.substringBefore('.') in known)
        span(m.range, if (ok) p.ok else p.err, true)
    }
    if (query.isNotEmpty()) {
        var i = text.indexOf(query, 0, true)
        while (i >= 0) {
            b.addStyle(SpanStyle(background = p.accent.copy(alpha = 0.45f), color = p.text), i, i + query.length)
            i = text.indexOf(query, i + query.length, true)
        }
    }
    return b.toAnnotatedString()
}

fun codeLines(text: String): List<String> {
    val out = ArrayList<String>()
    for (l in text.split("\n")) if (l.length <= 3000) out.add(l) else l.chunked(3000).forEach { out.add(it) }
    return out
}

private class HighlightTransform(val lang: String, val p: Pal, val known: Set<String>) : VisualTransformation {
    override fun filter(text: AnnotatedString) = TransformedText(highlight(text.text, lang, p, known), OffsetMapping.Identity)
}

@Composable
fun CodeEditor(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    lang: String = "text",
    readOnly: Boolean = false,
    placeholder: String = "",
    showStatus: Boolean = false,
    lineNumbers: Boolean = true,
    errorLine: Int = -1,
) {
    val p = LocalPal.current
    val known = LocalVars.current
    val vs = rememberScrollState()
    val measurer = rememberTextMeasurer(200)
    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, color = p.text)
    var tfv by remember { mutableStateOf(TextFieldValue(value)) }
    val cur = if (tfv.text == value) tfv else TextFieldValue(value, TextRange(value.length))
    var focused by remember { mutableStateOf(false) }
    val lineStarts = remember(cur.text) { ArrayList<Int>().also { l -> l.add(0); cur.text.forEachIndexed { i, c -> if (c == '\n') l.add(i + 1) } } }
    val curLine = lineCol(cur.text, cur.selection.end).first
    val gutterW = if (lineNumbers) (maxOf(2, lineStarts.size.toString().length) * 8 + 20).dp else 0.dp
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(5.dp)).background(p.field)
            .border(1.dp, if (focused) p.accent.copy(alpha = 0.7f) else p.border, RoundedCornerShape(5.dp)),
    ) {
        val minH = maxHeight
        Box(Modifier.fillMaxSize().verticalScroll(vs)) {
            BasicTextField(
                value = cur,
                onValueChange = { nv ->
                    val ed = if (readOnly) nv else smartEdit(cur, nv, lang)
                    tfv = ed
                    if (ed.text != value) onChange(ed.text)
                },
                readOnly = readOnly, textStyle = style,
                cursorBrush = SolidColor(p.accent),
                visualTransformation = remember(lang, p, known) { HighlightTransform(lang, p, known) },
                onTextLayout = { layout = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = minH)
                    .drawBehind {
                        if (!lineNumbers) return@drawBehind
                        val top = 10.dp.toPx(); val gw = gutterW.toPx()
                        val numStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 19.sp)
                        drawRect(p.hover.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Size(gw, size.height))
                        drawLine(p.border, androidx.compose.ui.geometry.Offset(gw, 0f), androidx.compose.ui.geometry.Offset(gw, size.height), 1f)
                        val lay = layout
                        val maxOff = lay?.layoutInput?.text?.length ?: 0
                        val viewTop = vs.value.toFloat(); val viewBottom = viewTop + minH.toPx()
                        val lineH = 19.dp.toPx()
                        for (i in lineStarts.indices) {
                            val vl = lay?.getLineForOffset(lineStarts[i].coerceIn(0, maxOff))
                            val y = if (lay != null && vl != null) top + lay.getLineTop(vl) else top + i * lineH
                            if (y + lineH < viewTop - lineH || y > viewBottom + lineH) continue
                            val isErr = i + 1 == errorLine
                            if (isErr) {
                                val endVl = if (lay != null && i + 1 < lineStarts.size) lay.getLineForOffset((lineStarts[i + 1] - 1).coerceIn(0, maxOff)) else vl
                                val h = if (lay != null && vl != null && endVl != null) lay.getLineBottom(endVl) - lay.getLineTop(vl) else lineH
                                drawRect(p.err.copy(alpha = 0.14f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, h))
                            }
                            val color = if (isErr) p.err else if (i + 1 == curLine && focused) p.text else p.muted.copy(alpha = 0.65f)
                            val tl = measurer.measure((i + 1).toString(), numStyle.copy(color = color))
                            drawText(tl, topLeft = androidx.compose.ui.geometry.Offset(gw - 10.dp.toPx() - tl.size.width, y))
                        }
                    }
                    .padding(start = gutterW + 10.dp, end = 10.dp, top = 10.dp, bottom = if (showStatus) 30.dp else 10.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { e ->
                        if (readOnly || e.type != KeyEventType.KeyDown || e.key != Key.Tab) return@onPreviewKeyEvent false
                        if (!e.isShiftPressed) {
                            val sel = cur.selection
                            val nt = cur.text.replaceRange(sel.min, sel.max, "  ")
                            tfv = TextFieldValue(nt, TextRange(sel.min + 2)); onChange(nt)
                        }
                        true
                    },
                decorationBox = { inner ->
                    Box { if (value.isEmpty()) Text(placeholder, style = style.copy(color = p.muted.copy(alpha = 0.6f))); inner() }
                },
            )
        }
        if (showStatus) {
            val (ln, col) = lineCol(cur.text, cur.selection.end)
            Row(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(4.dp)).background(p.panel.copy(alpha = 0.92f)).padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                T("Ln $ln, Col $col", color = p.muted, size = 11, mono = true)
                T("${cur.text.length} chars", color = p.muted, size = 11)
            }
        }
    }
}

/** Read-only, virtualised viewer for large text such as response bodies. */
@Composable
fun CodeView(text: String, lang: String, modifier: Modifier = Modifier, wrap: Boolean = true, query: String = "", focusLine: Int = -1) {
    val p = LocalPal.current
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, color = p.text)
    val lines = remember(text) { codeLines(text) }
    val state = rememberLazyListState()
    LaunchedEffect(focusLine, text) { if (focusLine >= 0) state.scrollToItem((focusLine - 3).coerceAtLeast(0)) }
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(5.dp))) {
        SelectionContainer {
            LazyColumn(Modifier.fillMaxSize().padding(vertical = 6.dp), state = state) {
                itemsIndexed(lines) { i, l ->
                    Row(Modifier.background(if (i == focusLine) p.accent.copy(alpha = 0.12f) else Color.Transparent)) {
                        Text((i + 1).toString(), style = style.copy(color = p.muted.copy(alpha = 0.6f), textAlign = androidx.compose.ui.text.style.TextAlign.End), modifier = Modifier.width(46.dp).padding(end = 8.dp))
                        Text(if (l.length < 20000) highlight(l, lang, p, query = query) else AnnotatedString(l), style = style, softWrap = wrap, modifier = Modifier.weight(1f).padding(end = 8.dp))
                    }
                }
            }
        }
        VerticalScrollbar(androidx.compose.foundation.rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/** Unified line diff with context collapsing. */
@Composable
fun DiffView(old: String, new: String, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, color = p.text)
    val diff = remember(old, new) { dev.prateekthakur.openrest.core.Diff.lines(old.split("\n"), new.split("\n")) }
    Box(modifier.clip(RoundedCornerShape(5.dp)).background(p.field).border(1.dp, p.border, RoundedCornerShape(5.dp))) {
        if (diff == null) { T("Responses are too large to diff.", color = p.muted, modifier = Modifier.padding(12.dp)); return@Box }
        val added = diff.count { it.op == '+' }; val removed = diff.count { it.op == '-' }
        if (added + removed == 0) { T("✓ Responses are identical", color = p.ok, modifier = Modifier.padding(12.dp)); return@Box }
        val keep = BooleanArray(diff.size)
        diff.forEachIndexed { i, l -> if (l.op != ' ') for (k in (i - 3).coerceAtLeast(0)..(i + 3).coerceAtMost(diff.size - 1)) keep[k] = true }
        val rows = ArrayList<Pair<Int, dev.prateekthakur.openrest.core.Diff.Line?>>()
        var gap = false
        diff.forEachIndexed { i, l -> if (keep[i]) { rows.add(i to l); gap = false } else if (!gap) { rows.add(i to null); gap = true } }
        LazyColumn(Modifier.fillMaxSize().padding(vertical = 6.dp)) {
            item { T("+$added  −$removed", color = p.muted, size = 11, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) }
            items(rows.size) { r ->
                val l = rows[r].second
                if (l == null) T("⋯", color = p.muted, mono = true, modifier = Modifier.padding(start = 10.dp))
                else Row(Modifier.fillMaxWidth().background(when (l.op) { '+' -> p.ok.copy(alpha = 0.16f); '-' -> p.err.copy(alpha = 0.16f); else -> Color.Transparent })) {
                    Text(l.op.toString(), style = style.copy(color = when (l.op) { '+' -> p.ok; '-' -> p.err; else -> p.muted }), modifier = Modifier.width(22.dp).padding(start = 8.dp))
                    Text(l.text, style = style, modifier = Modifier.weight(1f).padding(end = 8.dp))
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) = T(text, color = LocalPal.current.muted, size = 11, weight = FontWeight.SemiBold, modifier = modifier)

@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        T(text, color = color, size = 12, weight = FontWeight.SemiBold)
    }
}

@Composable
fun ScrollBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()), content = content)
}

/** Animated disclosure chevron: points right when collapsed, rotates to point down when open. */
@Composable
fun Chevron(open: Boolean, modifier: Modifier = Modifier, color: Color = LocalPal.current.muted) {
    val angle by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, androidx.compose.animation.core.tween(140))
    androidx.compose.foundation.Canvas(modifier.size(14.dp).rotate(angle)) {
        val w = size.width; val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.36f, h * 0.22f); lineTo(w * 0.66f, h * 0.5f); lineTo(w * 0.36f, h * 0.78f)
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
fun FolderIcon(open: Boolean, modifier: Modifier = Modifier, color: Color = LocalPal.current.accent) =
    Ico(if (open) Ic.FolderOpen else Ic.Folder, modifier, color, 18.dp)

@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val hov by src.collectIsHoveredAsState()
    Box(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) p.accent.copy(alpha = 0.18f) else if (hov) p.hover else Color.Transparent)
            .border(1.dp, if (selected) p.accent.copy(alpha = 0.6f) else p.border, RoundedCornerShape(14.dp))
            .hoverable(src).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 11.dp, vertical = 4.dp),
    ) { T(label, color = if (selected) p.accent else p.text, size = 12, weight = if (selected) FontWeight.SemiBold else null) }
}
