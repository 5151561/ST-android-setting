@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.sanitised.st.ui.screens

import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import io.github.sanitised.st.api.PersonaSaveRequest
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.border
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.BackupOperationAnchor
import io.github.sanitised.st.CustomOperationAnchor
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.ThemeColorSource
import io.github.sanitised.st.ThemeMode
import io.github.sanitised.st.UpdateChannel
import io.github.sanitised.st.api.ChatBackupSummary
import io.github.sanitised.st.api.ConnectionProfile
import io.github.sanitised.st.api.PersonaProfile
import io.github.sanitised.st.api.SecretProviderState
import io.github.sanitised.st.api.SettingsSnapshot
import io.github.sanitised.st.api.ConnectionTestResult
import io.github.sanitised.st.api.WorldInfoBook
import io.github.sanitised.st.api.WorldInfoSummary
import io.github.sanitised.st.data.repository.ConnectionRepository
import io.github.sanitised.st.data.repository.DefaultConnectionRepository
import io.github.sanitised.st.data.tavernApi
import io.github.sanitised.st.ui.navigation.LocalSTOpenDrawer

@Composable
fun STProviderDetailScreen(
    status: NodeStatus,
    baseUrl: String,
    providerId: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    onSettingsChanged: () -> Unit = {},
    modifier: Modifier = Modifier,
    connectionRepository: ConnectionRepository = remember(baseUrl) {
        DefaultConnectionRepository { tavernApi(baseUrl) }
    },
) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    
    var customUrl by remember { mutableStateOf("") }
    var proxyPassword by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var configuredSecretMask by remember { mutableStateOf("") }
    var hasConfiguredSecret by remember { mutableStateOf(false) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Provider-specific connection settings
    var endpointOptionValue by remember { mutableStateOf("") }
    var vertexAuthMode by remember { mutableStateOf("express") }
    var vertexRegion by remember { mutableStateOf("us-central1") }
    var vertexExpressProjectId by remember { mutableStateOf("") }
    var configuredVertexSaMask by remember { mutableStateOf("") }
    var hasConfiguredVertexSa by remember { mutableStateOf(false) }
    var vertexServiceAccountJson by remember { mutableStateOf("") }

    var customApiFormat by remember { mutableStateOf("openai_compat") }
    var customIncludeHeaders by remember { mutableStateOf("") }
    var customIncludeBody by remember { mutableStateOf("") }
    var customExcludeBody by remember { mutableStateOf("") }

    var opencodeEndpoint by remember { mutableStateOf("") }
    var opencodeApiFormat by remember { mutableStateOf("openai") }
    var workersAiAccountId by remember { mutableStateOf("") }
    var awsBedrockRegion by remember { mutableStateOf("us-east-1") }
    var awsBedrockUseCustomTemplate by remember { mutableStateOf(false) }
    var awsBedrockCustomResponsePath by remember { mutableStateOf("") }
    var awsBedrockCustomStreamPath by remember { mutableStateOf("") }
    var minimaxGroupId by remember { mutableStateOf("") }
    var nanogptProvider by remember { mutableStateOf("") }
    var nanogptPaygOverride by remember { mutableStateOf(false) }
    
    var modelsList by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedModel by remember { mutableStateOf("") }
    var isLoadingModels by remember { mutableStateOf(false) }

    var verifyStatus by remember { mutableStateOf(ConnectionVerifyStatus.NOT_VERIFIED) }
    var verifyErrorMessage by remember { mutableStateOf<String?>(null) }
    
    val running = status.state == NodeState.RUNNING
    val providerDefinition = remember(providerId) {
        apiConnectionProviderForId(providerId) ?: apiConnectionProviderForId("openai")!!
    }
    var providerState by remember(providerDefinition.id) {
        mutableStateOf<ApiConnectionProviderState?>(null)
    }
    
    LaunchedEffect(running, baseUrl, providerId, connectionRepository) {
        verifyStatus = ConnectionVerificationRegistry.get(providerDefinition.id)
        modelsList = providerDefinition.defaultModels
        if (running) {
            runCatching {
                val coreSettings = connectionRepository.getSettings()
                settings = coreSettings
                
                val group = providerDefinition.modelSettingsGroup
                val groupMap = if (!group.isNullOrBlank()) {
                    (coreSettings[group] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()
                } else emptyMap()

                customUrl = when {
                    providerDefinition.sourceValue == "custom" -> (groupMap["custom_url"] as? String).orEmpty()
                    providerDefinition.mode == "cc" -> {
                        val scopedKey = "reverse_proxy_${providerDefinition.sourceValue}"
                        if (groupMap.containsKey(scopedKey)) {
                            (groupMap[scopedKey] as? String).orEmpty()
                        } else {
                            (groupMap["reverse_proxy"] as? String).orEmpty()
                        }
                    }
                    providerDefinition.mode == "tc" -> coreSettings.stringValue("api_server")
                    else -> ""
                }
                val scopedPassKey = "proxy_password_${providerDefinition.sourceValue}"
                proxyPassword = if (groupMap.containsKey(scopedPassKey)) {
                    (groupMap[scopedPassKey] as? String).orEmpty()
                } else {
                    (groupMap["proxy_password"] as? String).orEmpty()
                }

                providerDefinition.endpointSettingKey?.let { key ->
                    val defaultOpt = providerDefinition.endpointOptions.firstOrNull()?.first.orEmpty()
                    endpointOptionValue = (groupMap[key] as? String)?.takeIf { it.isNotBlank() } ?: defaultOpt
                }

                vertexAuthMode = (groupMap["vertexai_auth_mode"] as? String)?.takeIf { it.isNotBlank() } ?: "express"
                vertexRegion = (groupMap["vertexai_region"] as? String)?.takeIf { it.isNotBlank() } ?: "us-central1"
                vertexExpressProjectId = (groupMap["vertexai_express_project_id"] as? String).orEmpty()

                customApiFormat = (groupMap["custom_api_format"] as? String)?.takeIf { it.isNotBlank() } ?: "openai_compat"
                customIncludeHeaders = (groupMap["custom_include_headers"] as? String).orEmpty()
                customIncludeBody = (groupMap["custom_include_body"] as? String).orEmpty()
                customExcludeBody = (groupMap["custom_exclude_body"] as? String).orEmpty()

                opencodeEndpoint = (groupMap["opencode_endpoint"] as? String).orEmpty()
                opencodeApiFormat = (groupMap["opencode_api_format"] as? String)?.takeIf { it.isNotBlank() } ?: "openai"
                workersAiAccountId = (groupMap["workers_ai_account_id"] as? String).orEmpty()
                awsBedrockRegion = (groupMap["aws_bedrock_region"] as? String)?.takeIf { it.isNotBlank() } ?: "us-east-1"
                awsBedrockUseCustomTemplate = (groupMap["aws_bedrock_use_custom_template"] as? Boolean) ?: false
                awsBedrockCustomResponsePath = (groupMap["aws_bedrock_custom_response_path"] as? String).orEmpty()
                awsBedrockCustomStreamPath = (groupMap["aws_bedrock_custom_stream_path"] as? String).orEmpty()
                minimaxGroupId = (groupMap["minimax_group_id"] as? String).orEmpty()
                nanogptProvider = (groupMap["nanogpt_provider"] as? String).orEmpty()
                nanogptPaygOverride = (groupMap["nanogpt_payg_override"] as? Boolean) ?: false

                selectedModel = modelForProvider(coreSettings, providerDefinition)
                    .ifBlank { providerDefinition.defaultModels.firstOrNull().orEmpty() }
                
                val secretsList = connectionRepository.listSecrets()
                providerState = buildApiConnectionUiState(
                    settings = coreSettings,
                    secrets = secretsList,
                    serviceRunning = true,
                    selectedProviderId = providerDefinition.id,
                    selectedMode = providerDefinition.mode
                ).activeProvider
                val primaryKey = providerDefinition.secretKeys.firstOrNull()
                val firstEntry = primaryKey?.let { key ->
                    secretsList.firstOrNull { it.key == key }?.entries?.let { entries ->
                        entries.firstOrNull { it.active } ?: entries.firstOrNull()
                    }
                }
                val saEntry = secretsList.firstOrNull { it.key == "vertexai_service_account_json" }
                    ?.entries
                    ?.let { entries -> entries.firstOrNull { it.active } ?: entries.firstOrNull() }
                configuredVertexSaMask = saEntry?.value.orEmpty()
                hasConfiguredVertexSa = saEntry != null

                configuredSecretMask = firstEntry?.value.orEmpty()
                hasConfiguredSecret = when (providerDefinition.id) {
                    "vertexai" -> if (vertexAuthMode == "full") hasConfiguredVertexSa else (firstEntry != null)
                    "pollinations" -> endpointOptionValue == "anonymous" || firstEntry != null || !providerDefinition.requiresSecret
                    else -> firstEntry != null || !providerDefinition.requiresSecret
                }
                apiKey = ""
                vertexServiceAccountJson = ""
                
                if (hasConfiguredSecret) {
                    isLoadingModels = true
                    val fetched = connectionRepository.fetchModels(
                        mode = providerDefinition.mode,
                        sourceValue = providerDefinition.sourceValue.orEmpty(),
                        apiServer = customUrl
                    )
                    modelsList = fetched.ifEmpty { providerDefinition.defaultModels }
                    if (selectedModel.isBlank() && modelsList.isNotEmpty()) {
                        selectedModel = modelsList.first()
                    }
                    isLoadingModels = false
                }
            }.onFailure {
                isLoadingModels = false
                onShowMessage("加载配置失败：${it.message}")
            }
        }
    }

    suspend fun persistCurrentConnectionSettings() {
        val updatedSettings = settingsWithSelectedApiProvider(
            settings = settings,
            provider = providerDefinition
        ).toMutableMap()

        val modelGroup = providerDefinition.modelSettingsGroup
        val modelKey = providerDefinition.modelKey
        val groupSettings = if (!modelGroup.isNullOrBlank()) {
            (updatedSettings[modelGroup] as? Map<*, *>)
                ?.entries
                ?.associate { (key, value) -> key.toString() to value }
                ?.toMutableMap()
                ?: mutableMapOf()
        } else null

        val finalModel = selectedModel.trim()
            .ifBlank { providerDefinition.defaultModels.firstOrNull().orEmpty() }
        if (groupSettings != null && !modelKey.isNullOrBlank()) {
            groupSettings[modelKey] = finalModel
        }

        val trimmedUrl = customUrl.trim()
        when (providerDefinition.mode) {
            "cc" -> {
                if (groupSettings != null) {
                    if (providerDefinition.sourceValue == "custom") {
                        groupSettings["custom_url"] = trimmedUrl
                        groupSettings["reverse_proxy"] = ""
                        groupSettings["custom_api_format"] = customApiFormat
                        groupSettings["custom_include_headers"] = customIncludeHeaders.trim()
                        groupSettings["custom_include_body"] = customIncludeBody.trim()
                        groupSettings["custom_exclude_body"] = customExcludeBody.trim()
                    } else if (providerDefinition.supportsReverseProxy) {
                        groupSettings["reverse_proxy"] = trimmedUrl
                        val effectiveProxyPass = proxyPassword.trim().ifBlank {
                            if (trimmedUrl.isNotBlank()) apiKey.trim() else ""
                        }
                        if (trimmedUrl.isNotBlank() && effectiveProxyPass.isNotBlank()) {
                            groupSettings["proxy_password"] = effectiveProxyPass
                        } else if (trimmedUrl.isBlank()) {
                            groupSettings["proxy_password"] = ""
                        } else {
                            groupSettings["proxy_password"] = proxyPassword.trim()
                        }
                        providerDefinition.sourceValue?.let { src ->
                            groupSettings["reverse_proxy_$src"] = trimmedUrl
                            groupSettings["proxy_password_$src"] =
                                (groupSettings["proxy_password"] as? String).orEmpty()
                        }
                    } else {
                        groupSettings["reverse_proxy"] = ""
                    }

                    providerDefinition.endpointSettingKey?.let { endpointKey ->
                        if (endpointOptionValue.isNotBlank()) {
                            groupSettings[endpointKey] = endpointOptionValue
                        }
                    }

                    when (providerDefinition.id) {
                        "vertexai" -> {
                            groupSettings["vertexai_auth_mode"] = vertexAuthMode
                            groupSettings["vertexai_region"] = vertexRegion.trim().ifBlank { "us-central1" }
                            groupSettings["vertexai_express_project_id"] = vertexExpressProjectId.trim()
                        }
                        "opencode" -> {
                            groupSettings["opencode_endpoint"] = opencodeEndpoint.trim()
                            groupSettings["opencode_api_format"] = opencodeApiFormat
                        }
                        "workers_ai" -> {
                            groupSettings["workers_ai_account_id"] = workersAiAccountId.trim()
                        }
                        "aws_bedrock" -> {
                            groupSettings["aws_bedrock_region"] = awsBedrockRegion.trim().ifBlank { "us-east-1" }
                            groupSettings["aws_bedrock_use_custom_template"] = awsBedrockUseCustomTemplate
                            groupSettings["aws_bedrock_custom_response_path"] = awsBedrockCustomResponsePath.trim()
                            groupSettings["aws_bedrock_custom_stream_path"] = awsBedrockCustomStreamPath.trim()
                        }
                        "minimax" -> {
                            groupSettings["minimax_group_id"] = minimaxGroupId.trim()
                        }
                        "nanogpt" -> {
                            groupSettings["nanogpt_provider"] = nanogptProvider.trim()
                            groupSettings["nanogpt_payg_override"] = nanogptPaygOverride
                        }
                    }
                }
            }
            "tc" -> {
                updatedSettings["api_server"] = trimmedUrl
            }
        }

        if (groupSettings != null && !modelGroup.isNullOrBlank()) {
            updatedSettings[modelGroup] = groupSettings
        }

        connectionRepository.saveSettings(updatedSettings)
        settings = updatedSettings

        if (providerDefinition.id == "vertexai" && vertexAuthMode == "full") {
            val newSaJson = vertexServiceAccountJson.trim()
            if (newSaJson.isNotBlank()) {
                connectionRepository.writeSecret("vertexai_service_account_json", newSaJson, "Vertex Service Account")
                configuredVertexSaMask = "已配置 JSON"
                hasConfiguredVertexSa = true
                hasConfiguredSecret = true
                vertexServiceAccountJson = ""
            }
        } else {
            val newSecret = apiKey.trim()
            if (newSecret.isNotBlank() && providerDefinition.secretKeys.isNotEmpty()) {
                connectionRepository.writeSecret(providerDefinition.secretKeys.first(), newSecret, "默认密钥")
                configuredSecretMask = "*******" + newSecret.takeLast(3)
                hasConfiguredSecret = true
                apiKey = ""
            }
        }
        if (providerDefinition.id == "minimax" && minimaxGroupId.trim().isNotBlank()) {
            runCatching {
                connectionRepository.writeSecret("minimax_group_id", minimaxGroupId.trim(), "MiniMax Group ID")
            }
        }
    }
    
    val displayName = providerDefinition.label
    
    STBackRoot(
        title = "$displayName 配置",
        onBack = onBack,
        modifier = modifier,
        actions = {
            STIconButton(
                icon = Icons.Filled.Save,
                contentDescription = "保存配置",
                onClick = {
                    scope.launch {
                        runCatching {
                            persistCurrentConnectionSettings()
                            onSettingsChanged()
                            onShowMessage("连接配置已成功保存！")
                            onBack()
                        }.onFailure {
                            onShowMessage("保存失败：${it.message}")
                        }
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "连接状态",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        val configured = hasConfiguredSecret || providerState?.hasConfiguredSecret == true
                        val statusText = when (verifyStatus) {
                            ConnectionVerifyStatus.SUCCESS -> "连接成功"
                            ConnectionVerifyStatus.FAILED -> verifyErrorMessage ?: "连接失败"
                            ConnectionVerifyStatus.TESTING -> "正在测试连接…"
                            ConnectionVerifyStatus.NOT_VERIFIED -> when {
                                !running -> "服务未启动"
                                configured -> "密钥已配置，尚未验证"
                                else -> "未配置密钥"
                            }
                        }
                        val statusColor = when (verifyStatus) {
                            ConnectionVerifyStatus.SUCCESS -> STThemeTertiary
                            ConnectionVerifyStatus.FAILED -> STThemeError
                            ConnectionVerifyStatus.TESTING -> STThemePrimary
                            ConnectionVerifyStatus.NOT_VERIFIED -> if (running && configured) Color(0xFFFF9800) else STThemeError
                        }
                        if (verifyStatus == ConnectionVerifyStatus.TESTING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(8.dp),
                                strokeWidth = 1.5.dp,
                                color = STThemePrimary
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(statusColor, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 8.dp))
            
            PremiumSectionHeader(title = "认证与端点设置")

            // 1. Multi-endpoint selector (Moonshot, SiliconFlow, MiniMax, Z.AI, Pollinations)
            if (providerDefinition.endpointSettingKey != null && providerDefinition.endpointOptions.isNotEmpty()) {
                ProviderOptionChipSelector(
                    title = "接入站点 / 端点选择",
                    options = providerDefinition.endpointOptions,
                    selectedValue = endpointOptionValue,
                    onSelect = { selected ->
                        endpointOptionValue = selected
                        if (providerDefinition.id == "pollinations" && selected == "anonymous") {
                            hasConfiguredSecret = true
                        }
                    }
                )
            }

            // 2. Google Vertex AI specific settings (Express vs Full Service Account JSON, Region, Project ID)
            if (providerDefinition.id == "vertexai") {
                ProviderOptionChipSelector(
                    title = "认证模式 (Vertex AI Auth Mode)",
                    options = listOf(
                        "express" to "Express 模式 (API Key)",
                        "full" to "Full 模式 (Service Account JSON)"
                    ),
                    selectedValue = vertexAuthMode,
                    onSelect = { mode ->
                        vertexAuthMode = mode
                        hasConfiguredSecret = if (mode == "full") hasConfiguredVertexSa else configuredSecretMask.isNotBlank()
                    }
                )

                STGlassTextField(
                    value = vertexRegion,
                    onValueChange = { vertexRegion = it },
                    label = "Vertex AI 区域 (Region)",
                    placeholder = "us-central1"
                )
                ProviderOptionChipSelector(
                    title = "常用区域快捷选择",
                    options = listOf(
                        "us-central1" to "us-central1",
                        "global" to "global",
                        "us" to "us",
                        "eu" to "eu",
                        "asia-northeast1" to "asia-northeast1"
                    ),
                    selectedValue = vertexRegion,
                    onSelect = { vertexRegion = it }
                )

                if (vertexAuthMode == "express") {
                    STGlassTextField(
                        value = vertexExpressProjectId,
                        onValueChange = { vertexExpressProjectId = it },
                        label = "GCP Project ID (可选，留空使用全局 Express 端点)",
                        placeholder = "my-gcp-project-id"
                    )
                } else {
                    STGlassTextField(
                        value = vertexServiceAccountJson,
                        onValueChange = { vertexServiceAccountJson = it },
                        label = if (hasConfiguredVertexSa) "Service Account JSON (已配置 $configuredVertexSaMask)" else "Service Account JSON 密钥全文",
                        placeholder = if (hasConfiguredVertexSa) "留空保持现有 JSON 凭证，粘贴新 JSON 可覆盖" else "粘贴包含 project_id、private_key、client_email 的完整 JSON"
                    )
                }
            }

            // 3. Custom OpenAI-compatible protocol & advanced body/headers
            if (providerDefinition.id == "custom") {
                STGlassTextField(
                    value = customUrl,
                    onValueChange = { customUrl = it },
                    label = "自定义 API 端点地址 (Custom URL)",
                    placeholder = providerDefinition.endpointHint
                )
                ProviderOptionChipSelector(
                    title = "上游 API 协议格式 (Custom API Format)",
                    options = listOf(
                        "openai_compat" to "OpenAI Chat 兼容",
                        "openai_responses" to "OpenAI Responses",
                        "claude_messages" to "Claude Messages",
                        "gemini_interactions" to "Gemini Interactions",
                        "gemini_generate_content" to "Gemini GenerateContent"
                    ),
                    selectedValue = customApiFormat,
                    onSelect = { customApiFormat = it }
                )
            } else if (providerDefinition.mode == "cc" && providerDefinition.supportsReverseProxy) {
                STGlassTextField(
                    value = customUrl,
                    onValueChange = { customUrl = it },
                    label = "反向代理 URL (可选，留空使用官方直连)",
                    placeholder = providerDefinition.endpointHint
                )
                if (customUrl.isNotBlank()) {
                    STGlassTextField(
                        value = proxyPassword,
                        onValueChange = { proxyPassword = it },
                        label = "反向代理密码 / 令牌 (Proxy Password)",
                        placeholder = "留空则默认同步下方新输入的 API 密钥",
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation()
                    )
                }
            } else if (providerDefinition.mode == "tc") {
                STGlassTextField(
                    value = customUrl,
                    onValueChange = { customUrl = it },
                    label = "后端服务器地址 (API Server)",
                    placeholder = "http://127.0.0.1:5000"
                )
            }

            // 4. OpenCode specific settings
            if (providerDefinition.id == "opencode") {
                STGlassTextField(
                    value = opencodeEndpoint,
                    onValueChange = { opencodeEndpoint = it },
                    label = "OpenCode 端点地址 (可选)",
                    placeholder = providerDefinition.endpointHint
                )
                ProviderOptionChipSelector(
                    title = "OpenCode 协议格式",
                    options = listOf(
                        "openai" to "OpenAI",
                        "claude" to "Claude",
                        "gemini" to "Gemini"
                    ),
                    selectedValue = opencodeApiFormat,
                    onSelect = { opencodeApiFormat = it }
                )
            }

            // 5. Cloudflare Workers AI Account ID
            if (providerDefinition.id == "workers_ai") {
                STGlassTextField(
                    value = workersAiAccountId,
                    onValueChange = { workersAiAccountId = it },
                    label = "Cloudflare Account ID (必填)",
                    placeholder = "例如: 023e105f4ecef8ad9ca31a8372d0c353"
                )
            }

            // 6. AWS Bedrock Region & Custom Template
            if (providerDefinition.id == "aws_bedrock") {
                STGlassTextField(
                    value = awsBedrockRegion,
                    onValueChange = { awsBedrockRegion = it },
                    label = "AWS 区域 (Bedrock Region)",
                    placeholder = "us-east-1"
                )
                ProviderOptionChipSelector(
                    title = "常用 AWS 区域",
                    options = listOf(
                        "us-east-1" to "us-east-1",
                        "us-west-2" to "us-west-2",
                        "ap-northeast-1" to "ap-northeast-1",
                        "eu-central-1" to "eu-central-1"
                    ),
                    selectedValue = awsBedrockRegion,
                    onSelect = { awsBedrockRegion = it }
                )
            }

            // 7. MiniMax Group ID
            if (providerDefinition.id == "minimax") {
                STGlassTextField(
                    value = minimaxGroupId,
                    onValueChange = { minimaxGroupId = it },
                    label = "MiniMax Group ID (可选)",
                    placeholder = "如部分模型需要可填写 Group ID"
                )
            }

            // 8. NanoGPT Provider & PAYG
            if (providerDefinition.id == "nanogpt") {
                STGlassTextField(
                    value = nanogptProvider,
                    onValueChange = { nanogptProvider = it },
                    label = "指定上游 Provider (可选，对应 X-Provider)",
                    placeholder = "留空由 NanoGPT 自动路由"
                )
                STSwitchRow(
                    "按量计费覆盖 (PAYG Override)",
                    "启用后附带 X-Billing-Mode: paygo 请求头",
                    nanogptPaygOverride
                ) { nanogptPaygOverride = it }
            }

            // 9. API Key Input (shown unless Vertex AI Full mode without reverse proxy)
            val showStandardApiKeyField = providerDefinition.secretKeys.isNotEmpty() &&
                !(providerDefinition.id == "vertexai" && vertexAuthMode == "full" && customUrl.isBlank())
            if (showStandardApiKeyField) {
                STGlassTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = if (configuredSecretMask.isNotBlank()) "API 密钥 (已配置 $configuredSecretMask)" else "API 密钥 (API Key)",
                    placeholder = if (configuredSecretMask.isNotBlank()) "留空保持现有密钥，输入新密钥可覆盖" else "粘贴真实密钥 (如 AIza... / sk-...)",
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (isPasswordVisible) "隐藏密码" else "显示密码",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }

            // Custom additional headers/body (for custom provider)
            if (providerDefinition.id == "custom") {
                STGlassTextField(
                    value = customIncludeHeaders,
                    onValueChange = { customIncludeHeaders = it },
                    label = "附加请求头 (Custom Headers，可选 JSON 或 Key: Value)",
                    placeholder = "{\"X-Custom-Header\": \"value\"}"
                )
                STGlassTextField(
                    value = customIncludeBody,
                    onValueChange = { customIncludeBody = it },
                    label = "附加请求体字段 (Include Body，可选 JSON 或 YAML)",
                    placeholder = "{\"top_k\": 40}"
                )
                STGlassTextField(
                    value = customExcludeBody,
                    onValueChange = { customExcludeBody = it },
                    label = "排除请求体字段 (Exclude Body，逗号或换行分隔)",
                    placeholder = "frequency_penalty, presence_penalty"
                )
            }

            val hasPendingCredential = apiKey.isNotBlank() ||
                (providerDefinition.id == "vertexai" && vertexAuthMode == "full" && vertexServiceAccountJson.isNotBlank()) ||
                (providerDefinition.id == "pollinations" && endpointOptionValue == "anonymous")
            val canTest = running && (hasPendingCredential || hasConfiguredSecret || !providerDefinition.requiresSecret)
            val isTesting = verifyStatus == ConnectionVerifyStatus.TESTING
            Button(
                onClick = {
                    scope.launch {
                        verifyStatus = ConnectionVerifyStatus.TESTING
                        ConnectionVerificationRegistry.record(providerDefinition.id, ConnectionVerifyStatus.TESTING)
                        verifyErrorMessage = null
                        runCatching {
                            persistCurrentConnectionSettings()
                            connectionRepository.testConnection(
                                mode = providerDefinition.mode,
                                sourceValue = providerDefinition.sourceValue.orEmpty(),
                                apiServer = customUrl
                            )
                        }.onSuccess { result ->
                            if (result.success) {
                                verifyStatus = ConnectionVerifyStatus.SUCCESS
                                ConnectionVerificationRegistry.record(providerDefinition.id, ConnectionVerifyStatus.SUCCESS)
                                if (result.models.isNotEmpty()) {
                                    modelsList = result.models
                                    if (selectedModel.isBlank()) {
                                        selectedModel = result.models.first()
                                    }
                                } else if (modelsList.isEmpty()) {
                                    modelsList = providerDefinition.defaultModels
                                }
                                onSettingsChanged()
                                onShowMessage("连接成功，可用模型 ${modelsList.size} 个")
                            } else {
                                verifyStatus = ConnectionVerifyStatus.FAILED
                                ConnectionVerificationRegistry.record(providerDefinition.id, ConnectionVerifyStatus.FAILED)
                                verifyErrorMessage = result.errorMessage ?: "连接失败"
                            }
                        }.onFailure { e ->
                            verifyStatus = ConnectionVerifyStatus.FAILED
                            ConnectionVerificationRegistry.record(providerDefinition.id, ConnectionVerifyStatus.FAILED)
                            verifyErrorMessage = e.message?.take(80) ?: "连接失败"
                        }
                    }
                },
                enabled = canTest && !isTesting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = STThemePrimary,
                    disabledContainerColor = STThemePrimary.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(48.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("正在连接…", color = Color.White)
                } else {
                    Icon(
                        imageVector = Icons.Filled.Cable,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            !running -> "服务未启动"
                            !canTest -> "请先填写凭证密钥"
                            else -> "测试连接"
                        }
                    )
                }
            }

            if (verifyStatus == ConnectionVerifyStatus.FAILED && verifyErrorMessage != null) {
                Text(
                    text = verifyErrorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = STThemeError,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))

            STGlassTextField(
                value = selectedModel,
                onValueChange = { selectedModel = it },
                label = "当前模型 ID (可手动输入或从下方列表选择)",
                placeholder = providerDefinition.defaultModels.firstOrNull() ?: "输入模型名称"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "可用模型列表 (${modelsList.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                
                val angle = remember { androidx.compose.animation.core.Animatable(0f) }
                val isRefreshing = remember { mutableStateOf(false) }
                IconButton(
                    onClick = {
                        if (!isRefreshing.value && running) {
                            scope.launch {
                                isRefreshing.value = true
                                angle.animateTo(
                                    targetValue = angle.value + 360f,
                                    animationSpec = tween(800, easing = LinearEasing)
                                )
                                runCatching {
                                    persistCurrentConnectionSettings()
                                    val fetched = connectionRepository.fetchModels(
                                        mode = providerDefinition.mode,
                                        sourceValue = providerDefinition.sourceValue.orEmpty(),
                                        apiServer = customUrl
                                    )
                                    if (fetched.isNotEmpty()) {
                                        modelsList = fetched
                                        if (selectedModel.isBlank()) {
                                            selectedModel = fetched.first()
                                        }
                                        onShowMessage("已从服务端获取 ${fetched.size} 个模型")
                                    } else {
                                        modelsList = providerDefinition.defaultModels
                                        onShowMessage("在线模型列表为空，已显示内置推荐模型")
                                    }
                                }.onFailure {
                                    onShowMessage("模型刷新失败：${it.message}")
                                }
                                isRefreshing.value = false
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "刷新模型列表",
                        tint = STThemePrimary,
                        modifier = Modifier.rotate(angle.value)
                    )
                }
            }
            
            if (modelsList.isEmpty()) {
                Text(
                    text = "暂无可用模型，请点击刷新获取",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                STListSurface(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    modelsList.forEachIndexed { index, model ->
                        val isSelected = model == selectedModel
                        STListItem(
                            headline = model,
                            supporting = if (isSelected) "当前选中" else null,
                            leading = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "已选中",
                                        tint = STThemePrimary
                                    )
                                } else {
                                    Spacer(modifier = Modifier.size(24.dp))
                                }
                            },
                            divider = index != modelsList.lastIndex,
                            onClick = { selectedModel = model }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 12.dp))

            STSystemInfoCard(
                title = "采样参数与提示词预设已独立分离",
                body = "温度 (Temperature)、Top P、最大上下文长度 (Context Window)、最大回复长度 (Max Tokens) 以及提示词模版均由「预设 (AI 采样设置)」页面统一管理，保存 API 连接不会覆盖您的采样预设。"
            )
        }
    }
}

@Composable
private fun ProviderOptionChipSelector(
    title: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, label) ->
                val isSelected = selectedValue.equals(value, ignoreCase = true) ||
                    (selectedValue.isBlank() && value == options.first().first)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) STThemePrimary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) STThemePrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.clickable { onSelect(value) }
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) STThemePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
