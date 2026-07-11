package io.github.sanitised.st.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// P0 公共表单组件 — 由设计稿 screens/P0Shared.jsx 翻译为 Compose。
// 多为有内部 state 的交互组件（原型用），所有颜色走 MaterialTheme.colorScheme。
// ─────────────────────────────────────────────────────────────────────────────

enum class P0Tone { Neutral, Tertiary, Error }

/** 带返回 + 标题 + 动作的 app bar，外加可滚动内容区。 */
@Composable
fun P0Scaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    closeIcon: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    PrototypeIconButton(
                        if (closeIcon) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                        "返回", onBack
                    )
                } else {
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                actions()
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
                content = content
            )
            if (bottomBar != null) bottomBar()
        }
    }
}

/** Token 计数小胶囊（app bar 用）。 */
@Composable
fun P0TokenChip(text: String) {
    Row(
        modifier = Modifier
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(Icons.Filled.Token, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** MD3 outlined 文本字段（受控）。 */
@Composable
fun P0Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    multiline: Boolean = false,
    minLines: Int = 4,
    tokens: Int? = null,
    hint: String? = null,
    mono: Boolean = false,
    readOnly: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            singleLine = !multiline,
            minLines = if (multiline) minLines else 1,
            readOnly = readOnly,
            shape = RoundedCornerShape(12.dp),
            textStyle = if (mono) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
            trailingIcon = trailing,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            )
        )
        if (tokens != null || hint != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(hint ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (tokens != null) {
                    Text("$tokens tokens", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** MD3 outlined 文本字段（有内部 state，纯展示原型用）。 */
@Composable
fun P0Field(
    label: String,
    initial: String = "",
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    multiline: Boolean = false,
    minLines: Int = 4,
    tokens: Int? = null,
    hint: String? = null,
    mono: Boolean = false,
    readOnly: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    var value by remember { mutableStateOf(initial) }
    P0Field(
        label = label, value = value, onValueChange = { value = it }, modifier = modifier,
        placeholder = placeholder, multiline = multiline, minLines = minLines, tokens = tokens,
        hint = hint, mono = mono, readOnly = readOnly, trailing = trailing
    )
}

/** 受控下拉选择（点击展开菜单选项）。 */
@Composable
fun P0Dropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(modifier.fillMaxWidth()) {
        P0Field(
            label = label,
            value = options.getOrNull(selectedIndex) ?: "",
            onValueChange = {},
            readOnly = true,
            hint = hint,
            trailing = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        )
        androidx.compose.foundation.layout.Box(
            Modifier.matchParentSize().clickable { expanded = true }
        )
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, opt ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelect(i); expanded = false }
                )
            }
        }
    }
}

/** 下拉选择样字段（只读 + 下拉箭头，可点开选择器）。 */
@Composable
fun P0Select(label: String, value: String, modifier: Modifier = Modifier, hint: String? = null, onClick: (() -> Unit)? = null) {
    androidx.compose.foundation.layout.Box(modifier.fillMaxWidth()) {
        P0Field(
            label = label, value = value, onValueChange = {}, modifier = Modifier, hint = hint, readOnly = true,
            trailing = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        )
        if (onClick != null) {
            androidx.compose.foundation.layout.Box(
                Modifier.matchParentSize().clickable { onClick() }
            )
        }
    }
}

/** 数字步进器（受控，− 值 +）。 */
@Composable
fun P0Stepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    hint: String? = null,
    step: Int = 1,
    min: Int = Int.MIN_VALUE,
    max: Int = Int.MAX_VALUE,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepButton(Icons.Filled.Remove) { onValueChange((value - step).coerceAtLeast(min)) }
            Text(
                buildString { append(value); if (suffix != null) append(suffix) },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 40.dp)
            )
            StepButton(Icons.Filled.Add) { onValueChange((value + step).coerceAtMost(max)) }
        }
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 数字步进器（有内部 state，纯展示原型用）。 */
@Composable
fun P0Stepper(
    label: String,
    initial: Int,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    hint: String? = null,
    step: Int = 1,
) {
    var v by remember { mutableIntStateOf(initial) }
    P0Stepper(label = label, value = v, onValueChange = { v = it }, modifier = modifier, suffix = suffix, hint = hint, step = step)
}

@Composable
private fun StepButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

/** MD3 分段按钮（受控）。 */
@Composable
fun P0Seg(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
    ) {
        options.forEachIndexed { i, opt ->
            if (i > 0) {
                Box(Modifier.width(1.dp).height(38.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
            val selected = selectedIndex == i
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { onSelect(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selected) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    opt,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** MD3 分段按钮（有内部 state，纯展示原型用）。 */
@Composable
fun P0Seg(options: List<String>, modifier: Modifier = Modifier, initialIndex: Int = 0) {
    var sel by remember { mutableIntStateOf(initialIndex) }
    P0Seg(options = options, selectedIndex = sel, onSelect = { sel = it }, modifier = modifier)
}

/** 开关行（受控）。 */
@Composable
fun P0ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    icon: ImageVector? = null,
    indent: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .heightIn(min = 56.dp)
            .padding(start = if (indent) 32.dp else 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 开关行（有内部 state，纯展示原型用）。 */
@Composable
fun P0ToggleRow(
    title: String,
    initialOn: Boolean,
    modifier: Modifier = Modifier,
    sub: String? = null,
    icon: ImageVector? = null,
    indent: Boolean = false,
) {
    var on by remember { mutableStateOf(initialOn) }
    P0ToggleRow(title = title, checked = on, onCheckedChange = { on = it }, modifier = modifier, sub = sub, icon = icon, indent = indent)
}

/** 浮动 label 的 outlined 容器（关键字 chips 等用）。 */
@Composable
fun P0OutlinedContainer(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(top = 16.dp, bottom = 12.dp, start = 12.dp, end = 12.dp)
        ) {
            content()
        }
        // floating label cutting the border
        Box(
            modifier = Modifier
                .offset(x = 12.dp, y = 2.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 5.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 关键字 chips 字段。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun P0ChipField(label: String, chips: List<String>, modifier: Modifier = Modifier, addLabel: String = "添加") {
    P0OutlinedContainer(label = label, modifier = modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { k ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(addLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 带 label + 当前值的滑块（受控）。 */
@Composable
fun P0Slider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    steps: Int = 0,
    valueText: ((Float) -> String)? = null,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                valueText?.invoke(value) ?: value.toInt().toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

/** 滑块（有内部 state，纯展示原型用）。 */
@Composable
fun P0Slider(
    label: String,
    initial: Float,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    steps: Int = 0,
    valueText: ((Float) -> String)? = null,
) {
    var v by remember { mutableStateOf(initial) }
    P0Slider(label = label, value = v, onValueChange = { v = it }, modifier = modifier, valueRange = valueRange, steps = steps, valueText = valueText)
}

/** 底部操作表条目。 */
@Composable
fun P0SheetItem(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    danger: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .heightIn(min = 52.dp)
            .padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** 信息横幅。 */
@Composable
fun P0InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Info,
    tone: P0Tone = P0Tone.Neutral,
) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        P0Tone.Neutral -> cs.surfaceContainer to cs.onSurfaceVariant
        P0Tone.Tertiary -> cs.tertiaryContainer to cs.onTertiaryContainer
        P0Tone.Error -> cs.errorContainer to cs.onErrorContainer
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = fg)
    }
}

/** Bolt 横幅快捷封装（条目编辑提示用）。 */
@Composable
fun P0BoltBanner(text: String, modifier: Modifier = Modifier) {
    P0InfoBanner(text = text, modifier = modifier, icon = Icons.Filled.Bolt, tone = P0Tone.Neutral)
}

/** 区块小标题（沿用主色，左对齐）。 */
@Composable
fun P0SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        if (trailing != null) trailing()
    }
}
