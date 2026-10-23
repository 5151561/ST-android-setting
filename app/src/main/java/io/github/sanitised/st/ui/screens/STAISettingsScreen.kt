package io.github.sanitised.st.ui.screens

import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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

private enum class TemplateDialogType {
    INSTRUCT,
    CONTEXT,
    SYSPROMPT,
    AUTHORS_NOTE,
}

private val REASONING_EFFORT_OPTIONS = listOf(
    "auto" to "自动 (Auto)",
    "minimum" to "最低 (Minimum)",
    "low" to "低 (Low)",
    "medium" to "中 (Medium)",
    "high" to "高 (High)",
    "max" to "最大 (Max)",
)

private val PROMPT_POST_PROCESSING_OPTIONS = listOf(
    "" to "无 (默认)",
    "merge" to "合并连续同角色消息 (Merge)",
    "semi" to "半严格交替 (Semi-strict)",
    "strict" to "严格交替 (Strict - 推荐 DeepSeek/Claude 反代)",
    "single" to "单条用户消息 (Single user message)",
)

@OptIn(ExperimentalLayoutApi::class)
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

    var maxContext by remember { mutableFloatStateOf(32768f) }
    var maxTokens by remember { mutableFloatStateOf(1024f) }
    var temp by remember { mutableFloatStateOf(1.0f) }
    var topP by remember { mutableFloatStateOf(1.0f) }
    var topK by remember { mutableFloatStateOf(0f) }
    var minP by remember { mutableFloatStateOf(0f) }
    var freqPenalty by remember { mutableFloatStateOf(0f) }
    var presPenalty by remember { mutableFloatStateOf(0f) }
    var seedText by remember { mutableStateOf("-1") }
    var streamingEnabled by remember { mutableStateOf(true) }
    var showThoughts by remember { mutableStateOf(false) }
    var reasoningEffort by remember { mutableStateOf("auto") }
    var enableWebSearch by remember { mutableStateOf(false) }
    var promptPostProcessing by remember { mutableStateOf("") }
    var useSysprompt by remember { mutableStateOf(true) }
    var instructEnabled by remember { mutableStateOf(true) }
    var syspromptText by remember { mutableStateOf("") }
    var authorsNoteText by remember { mutableStateOf("") }
    var authorsNoteDepth by remember { mutableIntStateOf(4) }
    var activeDialog by remember { mutableStateOf<TemplateDialogType?>(null) }

    fun loadFromSettings(s: Map<String, Any?>) {
        val oai = (s["oai_settings"] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()
        val powerUser = (s["power_user"] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()
        val instructMap = (powerUser["instruct"] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()

        maxContext = oai.floatValue("openai_max_context", s.floatValue("max_context", 32768f)).coerceIn(2048f, 128000f)
        maxTokens = oai.floatValue("openai_max_tokens", s.floatValue("amount_gen", 1024f)).coerceIn(64f, 16384f)
        temp = oai.floatValue("temp_openai", oai.floatValue("temp", 1.0f))
        topP = oai.floatValue("top_p_openai", oai.floatValue("top_p", 1.0f))
        topK = oai.floatValue("top_k", oai.floatValue("top_k_openai", 0f)).coerceIn(0f, 100f)
        minP = oai.floatValue("min_p", oai.floatValue("min_p_openai", 0f))
        freqPenalty = oai.floatValue("freq_pen_openai", oai.floatValue("frequency_penalty_openai", 0f))
        presPenalty = oai.floatValue("pres_pen_openai", oai.floatValue("presence_penalty_openai", 0f))
        val loadedSeed = when (val v = oai["seed"]) {
            is Number -> v.toInt()
            is String -> v.trim().toIntOrNull() ?: -1
            else -> -1
        }
        seedText = loadedSeed.toString()
        streamingEnabled = when (val v = oai["stream_openai"]) {
            is Boolean -> v
            else -> true
        }
        showThoughts = when (val v = oai["show_thoughts"]) {
            is Boolean -> v
            else -> false
        }
        reasoningEffort = (oai["reasoning_effort"] as? String)?.trim()?.ifBlank { "auto" } ?: "auto"
        enableWebSearch = when (val v = oai["enable_web_search"]) {
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true)
            else -> false
        }
        val rawPostProc = (oai["custom_prompt_post_processing"] as? String)?.trim().orEmpty()
        promptPostProcessing = if (rawPostProc.equals("none", ignoreCase = true)) "" else rawPostProc
        useSysprompt = when (val v = oai["use_sysprompt"]) {
            is Boolean -> v
            else -> true
        }
        instructEnabled = when (val v = instructMap["enabled"]) {
            is Boolean -> v
            else -> true
        }
        syspromptText = (oai["sysprompt_text"] as? String)
            ?: (powerUser["sysprompt_content"] as? String)
            ?: ""
        authorsNoteText = (oai["depth_prompt_prompt"] as? String)?.takeIf { it.isNotBlank() }
            ?: (s["author_note"] as? String).orEmpty()
        authorsNoteDepth = when (val v = oai["depth_prompt_depth"]) {
            is Number -> v.toInt().coerceIn(0, 32)
            is String -> (v.trim().toIntOrNull() ?: 4).coerceIn(0, 32)
            else -> 4
        }
    }

    fun buildUpdatedSettingsMap(): Map<String, Any?> {
        val updated = settings.toMutableMap()
        val oai = ((updated["oai_settings"] as? Map<*, *>)
            ?.entries?.associate { (k, v) -> k.toString() to v }
            ?: emptyMap()).toMutableMap()
        val textGen = ((updated["textgenerationwebui_settings"] as? Map<*, *>)
            ?.entries?.associate { (k, v) -> k.toString() to v }
            ?: emptyMap()).toMutableMap()
        val powerUser = ((updated["power_user"] as? Map<*, *>)
            ?.entries?.associate { (k, v) -> k.toString() to v }
            ?: emptyMap()).toMutableMap()
        val instructMap = ((powerUser["instruct"] as? Map<*, *>)
            ?.entries?.associate { (k, v) -> k.toString() to v }
            ?: emptyMap()).toMutableMap()

        val parsedSeed = seedText.trim().toIntOrNull() ?: -1

        oai["openai_max_context"] = maxContext.toInt()
        updated["max_context"] = maxContext.toInt()
        oai["openai_max_tokens"] = maxTokens.toInt()
        updated["amount_gen"] = maxTokens.toInt()
        oai["temp_openai"] = temp
        oai["temp"] = temp
        oai["top_p_openai"] = topP
        oai["top_p"] = topP
        oai["top_k_openai"] = topK.toInt()
        oai["top_k"] = topK.toInt()
        oai["min_p_openai"] = minP
        oai["min_p"] = minP
        oai["freq_pen_openai"] = freqPenalty
        oai["frequency_penalty_openai"] = freqPenalty
        oai["pres_pen_openai"] = presPenalty
        oai["presence_penalty_openai"] = presPenalty
        oai["seed"] = parsedSeed
        oai["stream_openai"] = streamingEnabled
        oai["show_thoughts"] = showThoughts
        oai["reasoning_effort"] = reasoningEffort
        oai["enable_web_search"] = enableWebSearch
        oai["custom_prompt_post_processing"] = promptPostProcessing
        oai["use_sysprompt"] = useSysprompt
        oai["sysprompt_text"] = syspromptText
        oai["depth_prompt_prompt"] = authorsNoteText
        oai["depth_prompt_depth"] = authorsNoteDepth
        updated["author_note"] = authorsNoteText

        textGen["temp"] = temp
        textGen["top_p"] = topP
        textGen["top_k"] = topK.toInt()
        textGen["min_p"] = minP
        textGen["max_context"] = maxContext.toInt()

        instructMap["enabled"] = instructEnabled
        powerUser["instruct"] = instructMap
        updated["power_user"] = powerUser
        updated["textgenerationwebui_settings"] = textGen
        updated["oai_settings"] = oai
        return updated
    }

    fun persistSettings(successMessage: String = "采样与上下文参数已保存至后端") {
        if (!running) {
            onShowMessage("服务未运行，无法保存")
            return
        }
        scope.launch {
            runCatching {
                val updated = buildUpdatedSettingsMap()
                personaPresetRepository.saveSettings(updated)
                settings = updated
                onSettingsChanged()
                onShowMessage(successMessage)
            }.onFailure { onShowMessage("保存失败：${it.message}") }
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
            persistSettings()
        })
    }) {
        val activeConnectionState = remember(settings, running) {
            buildApiConnectionUiState(
                settings = settings,
                secrets = emptyList(),
                serviceRunning = running
            )
        }
        PremiumCard(
            borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Tune, null, tint = STThemePrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "当前生效后端：${activeConnectionState.activeProvider.label} · ${activeConnectionState.activeModel}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "本页仅管理采样参数、上下文窗口与提示词模板；API 密钥与端点请在「API 连接设置」中配置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
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
        STSectionHeader("上下文与响应预算 (Context & Response)")
        STStatefulIntSlider("上下文窗口 (Max Context)", maxContext, 2048f..128000f, suffix = " tokens") { maxContext = it }
        STStatefulIntSlider("单次最大回复 (Max Response Tokens)", maxTokens, 64f..16384f, suffix = " tokens") { maxTokens = it }
        STSectionHeader("提示模板", trailing = {
            Text("点击配置模板与内容", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        })
        val instructName = presetLibrary?.categories?.firstOrNull { it.apiId == "instruct" }
            ?.presets?.firstOrNull { it.selected }?.name ?: "—"
        val contextName = presetLibrary?.categories?.firstOrNull { it.apiId == "context" }
            ?.presets?.firstOrNull { it.selected }?.name ?: "—"
        val syspromptName = presetLibrary?.categories?.firstOrNull { it.apiId == "sysprompt" }
            ?.presets?.firstOrNull { it.selected }?.name ?: "—"
        val authorsNoteSummary = if (authorsNoteText.isBlank()) {
            "未设置 (深度 $authorsNoteDepth)"
        } else {
            "已设置 · 深度 $authorsNoteDepth"
        }
        STTemplateRow(
            icon = Icons.Filled.Code,
            label = "Instruct 模板",
            value = instructName,
            hint = "角色名 / 系统提示遵循模型格式",
            toggle = true,
            checked = instructEnabled,
            onToggleChange = { instructEnabled = it },
            onClick = { activeDialog = TemplateDialogType.INSTRUCT }
        )
        STTemplateRow(
            icon = Icons.Filled.Bookmarks,
            label = "上下文模板",
            value = contextName,
            hint = "角色描述 + 场景 + 历史的组织方式",
            onClick = { activeDialog = TemplateDialogType.CONTEXT }
        )
        STTemplateRow(
            icon = Icons.Filled.Tune,
            label = "系统提示",
            value = syspromptName,
            hint = if (syspromptText.isBlank()) "注入到对话最前（点击编辑全局覆盖提示词）" else "自定义覆盖：${syspromptText.take(28)}",
            toggle = true,
            checked = useSysprompt,
            onToggleChange = { useSysprompt = it },
            onClick = { activeDialog = TemplateDialogType.SYSPROMPT }
        )
        STTemplateRow(
            icon = Icons.AutoMirrored.Filled.StickyNote2,
            label = "作者注 / 深度笔记",
            value = authorsNoteSummary,
            hint = if (authorsNoteText.isBlank()) "按需注入到指定深度（点击设置）" else authorsNoteText.take(36),
            onClick = { activeDialog = TemplateDialogType.AUTHORS_NOTE }
        )
        STSectionHeader("核心采样")
        STStatefulSlider("温度 Temperature", temp, 0f..2f) { temp = it }
        STStatefulSlider("Top P", topP, 0f..1f) { topP = it }
        STStatefulSlider("Top K", topK, 0f..100f) { topK = it }
        STStatefulSlider("Min P", minP, 0f..1f) { minP = it }
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            OutlinedTextField(
                value = seedText,
                onValueChange = { seedText = it },
                label = { Text("随机种子 (Seed，-1 表示随机)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        STSectionHeader("重复抑制")
        STStatefulSlider("频率惩罚", freqPenalty, -2f..2f) { freqPenalty = it }
        STStatefulSlider("存在惩罚", presPenalty, -2f..2f) { presPenalty = it }

        STSectionHeader("推理与思考 (Reasoning)")
        STSwitchRow("包含推理思考链 (Include Reasoning)", "请求模型返回思考过程 (show_thoughts)", showThoughts) { showThoughts = it }
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            Text(
                text = "推理强度 (Reasoning Effort)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "自动 (Auto) 时不会向普通自定义源强制注入 reasoning_effort 字段，避免不兼容反代报错。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                REASONING_EFFORT_OPTIONS.forEach { (value, label) ->
                    FilterChip(
                        selected = reasoningEffort == value,
                        onClick = { reasoningEffort = value },
                        label = { Text(label) }
                    )
                }
            }
        }
        STSwitchRow("启用联网搜索 (Web Search)", "请求后端模型启用内置联网搜索能力", enableWebSearch) { enableWebSearch = it }

        STSectionHeader("提示词后处理 (Prompt Post-Processing)")
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            Text(
                text = "控制发送前消息角色序列的整理方式（DeepSeek / Claude 反代推荐「严格交替」或「合并连续同角色消息」）：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            PROMPT_POST_PROCESSING_OPTIONS.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { promptPostProcessing = value }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = promptPostProcessing == value,
                        onClick = { promptPostProcessing = value }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        STSectionHeader("高级 — 极少改动")
        STSwitchRow("启用流式输出", "边生成边显示", streamingEnabled) { streamingEnabled = it }
        STSwitchRow("启用原生 System Prompt", "Google Gemini / Claude 转换 systemInstruction", useSysprompt) { useSysprompt = it }
    }

    when (activeDialog) {
        TemplateDialogType.INSTRUCT -> {
            STPresetSelectionDialog(
                title = "Instruct 模板配置",
                subtitle = "选择 Instruct 预设，或切换是否启用 Instruct 格式化",
                presetLibrary = presetLibrary,
                apiId = "instruct",
                toggleLabel = "启用 Instruct 模式",
                toggleChecked = instructEnabled,
                onToggleChange = { instructEnabled = it },
                onSelectPreset = { name ->
                    scope.launch {
                        runCatching {
                            personaPresetRepository.selectPreset("instruct", name)
                            loadPresets()
                            onShowMessage("已切换 Instruct 模板：$name")
                        }.onFailure { onShowMessage(it.message ?: "切换 Instruct 模板失败") }
                    }
                },
                onRestorePreset = { name ->
                    scope.launch {
                        runCatching {
                            personaPresetRepository.restorePreset("instruct", name)
                            loadPresets()
                            onShowMessage("已恢复默认预设：$name")
                        }.onFailure { onShowMessage(it.message ?: "恢复预设失败") }
                    }
                },
                onDismiss = { activeDialog = null }
            )
        }
        TemplateDialogType.CONTEXT -> {
            STPresetSelectionDialog(
                title = "上下文模板配置",
                subtitle = "选择角色描述、场景与对话历史的上下文组装模板",
                presetLibrary = presetLibrary,
                apiId = "context",
                onSelectPreset = { name ->
                    scope.launch {
                        runCatching {
                            personaPresetRepository.selectPreset("context", name)
                            loadPresets()
                            onShowMessage("已切换上下文模板：$name")
                        }.onFailure { onShowMessage(it.message ?: "切换上下文模板失败") }
                    }
                },
                onRestorePreset = { name ->
                    scope.launch {
                        runCatching {
                            personaPresetRepository.restorePreset("context", name)
                            loadPresets()
                            onShowMessage("已恢复默认预设：$name")
                        }.onFailure { onShowMessage(it.message ?: "恢复预设失败") }
                    }
                },
                onDismiss = { activeDialog = null }
            )
        }
        TemplateDialogType.SYSPROMPT -> {
            val category = presetLibrary?.categories?.firstOrNull { it.apiId == "sysprompt" }
            var draftSysprompt by remember(syspromptText) { mutableStateOf(syspromptText) }
            var draftUseSysprompt by remember(useSysprompt) { mutableStateOf(useSysprompt) }
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("系统提示 (System Prompt)") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("启用原生 System Prompt", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = draftUseSysprompt, onCheckedChange = { draftUseSysprompt = it })
                        }
                        if (category != null && category.presets.isNotEmpty()) {
                            Text(
                                text = "系统提示预设：",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                category.presets.forEach { preset ->
                                    FilterChip(
                                        selected = preset.selected,
                                        onClick = {
                                            scope.launch {
                                                runCatching {
                                                    personaPresetRepository.selectPreset("sysprompt", preset.name)
                                                    loadPresets()
                                                    onShowMessage("已切换系统提示预设：${preset.name}")
                                                }.onFailure { onShowMessage(it.message ?: "切换预设失败") }
                                            }
                                        },
                                        label = { Text(preset.name) }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = draftSysprompt,
                            onValueChange = { draftSysprompt = it },
                            label = { Text("默认系统提示覆盖内容 (留空则使用角色卡或默认主提示)") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        syspromptText = draftSysprompt
                        useSysprompt = draftUseSysprompt
                        activeDialog = null
                        persistSettings("系统提示已保存")
                    }) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("取消")
                    }
                }
            )
        }
        TemplateDialogType.AUTHORS_NOTE -> {
            var draftNote by remember(authorsNoteText) { mutableStateOf(authorsNoteText) }
            var draftDepth by remember(authorsNoteDepth) { mutableStateOf(authorsNoteDepth.toString()) }
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("作者注 / 深度笔记 (Author's Note)") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "按指定轮次深度从对话末尾向前回溯注入一条 System 提示，支持 {{char}} 与 {{user}} 宏替换。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = draftNote,
                            onValueChange = { draftNote = it },
                            label = { Text("作者注内容 (留空则不注入)") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = draftDepth,
                            onValueChange = { draftDepth = it },
                            label = { Text("注入深度 (Depth，默认 4)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        authorsNoteText = draftNote
                        authorsNoteDepth = (draftDepth.trim().toIntOrNull() ?: 4).coerceIn(0, 32)
                        activeDialog = null
                        persistSettings("作者注与注入深度已保存")
                    }) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("取消")
                    }
                }
            )
        }
        null -> Unit
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun STPresetSelectionDialog(
    title: String,
    subtitle: String,
    presetLibrary: PresetLibrary?,
    apiId: String,
    toggleLabel: String? = null,
    toggleChecked: Boolean = false,
    onToggleChange: ((Boolean) -> Unit)? = null,
    onSelectPreset: (String) -> Unit,
    onRestorePreset: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val category = presetLibrary?.categories?.firstOrNull { it.apiId == apiId }
    val selectedPreset = category?.presets?.firstOrNull { it.selected }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (toggleLabel != null && onToggleChange != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(toggleLabel, style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = toggleChecked, onCheckedChange = onToggleChange)
                    }
                }
                if (category == null || category.presets.isEmpty()) {
                    Text(
                        text = "当前无可用预设（请确认后端服务已启动）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "可用预设（当前选中：${selectedPreset?.name ?: "未选择"}）：",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        category.presets.forEach { preset ->
                            FilterChip(
                                selected = preset.selected,
                                onClick = { onSelectPreset(preset.name) },
                                label = { Text(preset.name) }
                            )
                        }
                    }
                    if (selectedPreset != null) {
                        OutlinedButton(
                            onClick = { onRestorePreset(selectedPreset.name) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("恢复「${selectedPreset.name}」为默认")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}

@Composable
private fun STTemplateRow(
    icon: ImageVector,
    label: String,
    value: String,
    hint: String,
    toggle: Boolean = false,
    checked: Boolean = false,
    onToggleChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit = {},
) {
    STListItem(
        headline = "$label · $value",
        supporting = hint,
        leading = { STTileIcon(icon) },
        trailing = {
            if (toggle) {
                Switch(checked = checked, onCheckedChange = onToggleChange)
            } else {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        onClick = onClick
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

@Composable
private fun STStatefulIntSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String = "",
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "${value.toInt()}$suffix",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
