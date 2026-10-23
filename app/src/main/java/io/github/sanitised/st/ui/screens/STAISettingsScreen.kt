package io.github.sanitised.st.ui.screens

import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.PresetLibrary
import io.github.sanitised.st.data.repository.DefaultPersonaPresetRepository
import io.github.sanitised.st.data.repository.PersonaPresetRepository
import io.github.sanitised.st.data.tavernApi
import kotlinx.coroutines.launch

@Composable
fun STAISettingsScreen(
    status: NodeStatus,
    baseUrl: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    onSettingsChanged: () -> Unit = {},
    modifier: Modifier = Modifier,
    personaPresetRepository: PersonaPresetRepository = remember(baseUrl) { DefaultPersonaPresetRepository { tavernApi(baseUrl) } }
) {
    val scope = rememberCoroutineScope()
    var presetLibrary by remember { mutableStateOf<PresetLibrary?>(null) }
    val running = status.state == NodeState.RUNNING
    var settings by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }

    var temp by remember { mutableFloatStateOf(1.0f) }
    var topP by remember { mutableFloatStateOf(1.0f) }
    var topK by remember { mutableFloatStateOf(0f) }
    var minP by remember { mutableFloatStateOf(0f) }
    var freqPenalty by remember { mutableFloatStateOf(0f) }
    var presPenalty by remember { mutableFloatStateOf(0f) }
    var streamingEnabled by remember { mutableStateOf(true) }
    var showThoughts by remember { mutableStateOf(false) }
    var useSysprompt by remember { mutableStateOf(true) }

    fun loadFromSettings(s: Map<String, Any?>) {
        val oai = (s["oai_settings"] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()
        temp = oai.floatValue("temp_openai", oai.floatValue("temp", 1.0f))
        topP = oai.floatValue("top_p_openai", 1.0f)
        topK = oai.floatValue("top_k_openai", 0f).coerceIn(0f, 100f)
        minP = oai.floatValue("min_p_openai", 0f)
        freqPenalty = oai.floatValue("freq_pen_openai", oai.floatValue("frequency_penalty_openai", 0f))
        presPenalty = oai.floatValue("pres_pen_openai", oai.floatValue("presence_penalty_openai", 0f))
        streamingEnabled = when (val v = oai["stream_openai"]) {
            is Boolean -> v
            else -> true
        }
        showThoughts = when (val v = oai["show_thoughts"]) {
            is Boolean -> v
            else -> false
        }
        useSysprompt = when (val v = oai["use_sysprompt"]) {
            is Boolean -> v
            else -> true
        }
    }

    fun loadPresets() {
        if (running) {
            scope.launch {
                try {
                    val result = personaPresetRepository.getPresetLibrary()
                    presetLibrary = result
                } catch (e: Exception) {
                    onShowMessage(e.message ?: "加载采样预设失败")
                }
            }
        }
    }

    LaunchedEffect(running, baseUrl, personaPresetRepository) {
        loadPresets()
        if (running) {
            runCatching {
                val s = personaPresetRepository.getSettings()
                settings = s
                loadFromSettings(s)
            }.onFailure { onShowMessage(it.message ?: "加载设置失败") }
        }
    }

    STBackRoot(title = "AI 设置", onBack = onBack, modifier = modifier, actions = {
        STIconButton(Icons.Filled.RestartAlt, "重置", {
            if (running) {
                scope.launch {
                    runCatching {
                        val s = personaPresetRepository.getSettings()
                        settings = s
                        loadFromSettings(s)
                        onShowMessage("已从服务器重新加载设置")
                    }.onFailure { onShowMessage("重置失败：${it.message}") }
                }
            } else {
                onShowMessage("服务未运行，无法重置")
            }
        })
        STIconButton(Icons.Filled.Save, "保存", {
            if (running) {
                scope.launch {
                    runCatching {
                        val updated = settings.toMutableMap()
                        val oai = ((updated["oai_settings"] as? Map<*, *>)
                            ?.entries?.associate { (k, v) -> k.toString() to v }
                            ?: emptyMap()).toMutableMap()
                        oai["temp_openai"] = temp
                        oai["temp"] = temp
                        oai["top_p_openai"] = topP
                        oai["top_k_openai"] = topK.toInt()
                        oai["min_p_openai"] = minP
                        oai["freq_pen_openai"] = freqPenalty
                        oai["frequency_penalty_openai"] = freqPenalty
                        oai["pres_pen_openai"] = presPenalty
                        oai["presence_penalty_openai"] = presPenalty
                        oai["stream_openai"] = streamingEnabled
                        oai["show_thoughts"] = showThoughts
                        oai["use_sysprompt"] = useSysprompt
                        updated["oai_settings"] = oai
                        personaPresetRepository.saveSettings(updated)
                        settings = updated
                        onSettingsChanged()
                        onShowMessage("采样参数已保存至后端")
                    }.onFailure { onShowMessage("保存失败：${it.message}") }
                }
            } else {
                onShowMessage("服务未运行，无法保存")
            }
        })
    }) {
        STPresetCard(
            presetLibrary = presetLibrary,
            onSelectPreset = { apiId, name ->
                scope.launch {
                    try {
                        personaPresetRepository.selectPreset(apiId, name)
                        onShowMessage("已切换预设：$name")
                        loadPresets()
                        runCatching {
                            val s = personaPresetRepository.getSettings()
                            settings = s
                            loadFromSettings(s)
                        }
                    } catch (e: Exception) {
                        onShowMessage(e.message ?: "切换预设失败")
                    }
                }
            }
        )
        STSectionHeader("提示模板", trailing = {
            Text("仅文本补全", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        })
        val instructName = presetLibrary?.categories?.firstOrNull { it.apiId == "instruct" }
            ?.presets?.firstOrNull { it.selected }?.name ?: "—"
        val contextName = presetLibrary?.categories?.firstOrNull { it.apiId == "context" }
            ?.presets?.firstOrNull { it.selected }?.name ?: "—"
        val syspromptName = presetLibrary?.categories?.firstOrNull { it.apiId == "sysprompt" }
            ?.presets?.firstOrNull { it.selected }?.name ?: "—"
        STTemplateRow(Icons.Filled.Code, "Instruct 模板", instructName, "角色名 / 系统提示遵循模型格式", toggle = true)
        STTemplateRow(Icons.Filled.Bookmarks, "上下文模板", contextName, "角色描述 + 场景 + 历史的组织方式")
        STTemplateRow(Icons.Filled.Tune, "系统提示", syspromptName, "注入到对话最前", toggle = true, checked = useSysprompt)
        STTemplateRow(Icons.AutoMirrored.Filled.StickyNote2, "作者注 / 深度笔记", "未设置", "按需注入到指定深度")
        STSectionHeader("核心采样")
        STStatefulSlider("温度 Temperature", temp, 0f..2f) { temp = it }
        STStatefulSlider("Top P", topP, 0f..1f) { topP = it }
        STStatefulSlider("Top K", topK, 0f..100f) { topK = it }
        STStatefulSlider("Min P", minP, 0f..1f) { minP = it }
        STSectionHeader("重复抑制")
        STStatefulSlider("频率惩罚", freqPenalty, -2f..2f) { freqPenalty = it }
        STStatefulSlider("存在惩罚", presPenalty, -2f..2f) { presPenalty = it }
        STSectionHeader("高级 — 极少改动")
        STSwitchRow("启用流式输出", "边生成边显示", streamingEnabled) { streamingEnabled = it }
        STSwitchRow("包含推理思考链 (Include Reasoning)", "请求模型返回思考过程", showThoughts) { showThoughts = it }
        STSwitchRow("启用原生 System Prompt", "Google Gemini / Claude 转换 systemInstruction", useSysprompt) { useSysprompt = it }
    }
}

@Composable
private fun STPresetCard(
    presetLibrary: PresetLibrary?,
    onSelectPreset: (String, String) -> Unit
) {
    if (presetLibrary == null || presetLibrary.categories.isEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("采样预设", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("未连接服务 / 无可用预设", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    } else {
        presetLibrary.categories.forEach { category ->
            val selectedPreset = category.presets.firstOrNull { it.selected }
            var expanded by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(category.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(selectedPreset?.name ?: "未选择", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = if (expanded) Icons.Filled.RestartAlt else Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (expanded) {
                    Spacer(modifier = Modifier.height(4.dp))
                    category.presets.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectPreset(category.apiId, preset.name)
                                    expanded = false
                                }
                                .padding(horizontal = 36.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (preset.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (preset.selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (preset.selected) {
                                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun STTemplateRow(icon: ImageVector, label: String, value: String, hint: String, toggle: Boolean = false, checked: Boolean = false) {
    STListItem(
        headline = "$label · $value",
        supporting = hint,
        leading = { STTileIcon(icon) },
        trailing = { if (toggle) Switch(checked = checked, onCheckedChange = null) },
        onClick = {}
    )
}

@Composable
private fun STSliderSection(title: String, values: List<Pair<String, Float>>) {
    STSectionHeader(title)
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        values.forEach { (label, initial) ->
            val range = remember(label) {
                when {
                    label.contains("温度") || label.contains("Temperature") -> 0f..2f
                    label.contains("惩罚") && !label.contains("范围") -> -2f..2f
                    else -> 0f..1f
                }
            }
            var value by remember(label) { mutableFloatStateOf(initial.coerceIn(range.start, range.endInclusive)) }
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f", value),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = range,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun STStatefulSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = String.format(Locale.US, "%.2f", value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValueChange, valueRange = range, modifier = Modifier.fillMaxWidth())
    }
}
