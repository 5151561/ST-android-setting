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
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.api.WorldInfoBook
import io.github.sanitised.st.api.WorldInfoSummary
import io.github.sanitised.st.ui.navigation.LocalSTOpenDrawer

@Composable
fun PrototypeProviderDetailScreen(
    status: NodeStatus,
    baseUrl: String,
    providerId: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    onSettingsChanged: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    
    var customUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var initialApiKey by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    
    var modelsList by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedModel by remember { mutableStateOf("") }
    var isLoadingModels by remember { mutableStateOf(false) }

    var verifyStatus by remember { mutableStateOf(ConnectionVerifyStatus.NOT_VERIFIED) }
    var verifyErrorMessage by remember { mutableStateOf<String?>(null) }

    var temp by remember { mutableStateOf(1.0f) }
    var contextSize by remember { mutableStateOf(4096f) }
    
    val running = status.state == NodeState.RUNNING
    val providerDefinition = remember(providerId) {
        apiConnectionProviderForId(providerId) ?: apiConnectionProviderForId("openai")!!
    }
    var providerState by remember(providerDefinition.id) {
        mutableStateOf<ApiConnectionProviderState?>(null)
    }
    
    LaunchedEffect(running, baseUrl, providerId) {
        if (running) {
            runCatching {
                val client = TavernCoreClient(baseUrl)
                val coreSettings = client.getSettings()
                settings = coreSettings
                
                val group = providerDefinition.modelSettingsGroup
                val groupMap = if (!group.isNullOrBlank()) {
                    (coreSettings[group] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()
                } else emptyMap()

                customUrl = when (providerDefinition.mode) {
                    "cc" -> (groupMap["reverse_proxy"] as? String).orEmpty()
                    "tc" -> coreSettings.stringValue("api_server")
                    else -> ""
                }

                selectedModel = modelForProvider(coreSettings, providerDefinition)

                temp = when (providerDefinition.mode) {
                    "cc" -> groupMap.floatValue("temp", 1.0f)
                    "tc" -> groupMap.floatValue("temp", 1.0f)
                    else -> coreSettings.floatValue("temp", 1.0f)
                }
                contextSize = when (providerDefinition.mode) {
                    "cc" -> groupMap.intValue("openai_max_context", 4096).toFloat()
                    "tc" -> groupMap.intValue("max_context", 4096).toFloat()
                    else -> 4096f
                }
                
                val secretsList = client.listSecrets()
                providerState = buildApiConnectionUiState(
                    settings = coreSettings,
                    secrets = secretsList,
                    serviceRunning = true,
                    selectedProviderId = providerDefinition.id,
                    selectedMode = providerDefinition.mode
                ).activeProvider
                val firstEntry = providerDefinition.secretKeys
                    .asSequence()
                    .mapNotNull { secretKey -> secretsList.firstOrNull { it.key == secretKey } }
                    .flatMap { it.entries.asSequence() }
                    .firstOrNull { it.active }
                    ?: providerDefinition.secretKeys
                        .asSequence()
                        .mapNotNull { secretKey -> secretsList.firstOrNull { it.key == secretKey } }
                        .flatMap { it.entries.asSequence() }
                        .firstOrNull()
                apiKey = firstEntry?.value ?: ""
                initialApiKey = apiKey
                
                isLoadingModels = true
                modelsList = client.fetchModels(
                    mode = providerDefinition.mode,
                    sourceValue = providerDefinition.sourceValue.orEmpty(),
                    apiServer = customUrl
                )
                isLoadingModels = false
            }.onFailure {
                onShowMessage("加载配置失败：${it.message}")
            }
        }
    }
    
    val displayName = providerDefinition.label
    
    PrototypeBackRoot(
        title = "$displayName 配置",
        onBack = onBack,
        modifier = modifier,
        actions = {
            PrototypeIconButton(
                icon = Icons.Filled.Save,
                contentDescription = "保存配置",
                onClick = {
                    scope.launch {
                        runCatching {
                            val client = TavernCoreClient(baseUrl)
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

                            if (groupSettings != null && !modelKey.isNullOrBlank()) {
                                groupSettings[modelKey] = selectedModel
                            }

                            when (providerDefinition.mode) {
                                "cc" -> {
                                    if (groupSettings != null) {
                                        if (customUrl.isNotBlank()) groupSettings["reverse_proxy"] = customUrl
                                        groupSettings["temp"] = temp
                                        groupSettings["openai_max_context"] = contextSize.toInt()
                                    }
                                }
                                "tc" -> {
                                    if (customUrl.isNotBlank()) updatedSettings["api_server"] = customUrl
                                    if (groupSettings != null) {
                                        groupSettings["temp"] = temp
                                        groupSettings["max_context"] = contextSize.toInt()
                                    }
                                }
                            }

                            if (groupSettings != null && !modelGroup.isNullOrBlank()) {
                                updatedSettings[modelGroup] = groupSettings
                            }

                            client.saveSettings(updatedSettings)

                            if (apiKey != initialApiKey && providerDefinition.secretKeys.isNotEmpty()) {
                                client.writeSecret(providerDefinition.secretKeys.first(), apiKey, "默认密钥")
                            }

                            onSettingsChanged()
                            onShowMessage("配置已成功保存！")
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
                        val configured = providerState?.hasConfiguredSecret == true
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
            
            HorizontalDivider(color = Color(0x0DFFFFFF), modifier = Modifier.padding(vertical = 8.dp))
            
            PremiumSectionHeader(title = "端点设置")
            
            PrototypeGlassTextField(
                value = customUrl,
                onValueChange = { customUrl = it },
                label = if (providerDefinition.mode == "cc") "反向代理 URL (Reverse Proxy)" else "后端服务器地址",
                placeholder = if (providerDefinition.mode == "cc") "https://api.openai.com/v1" else "http://127.0.0.1:5000"
            )
            
            PrototypeGlassTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = "API 密钥 (API Key)",
                placeholder = "sk-...",
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

            val canTest = running && (apiKey.isNotBlank() || !providerDefinition.requiresSecret)
            val isTesting = verifyStatus == ConnectionVerifyStatus.TESTING
            Button(
                onClick = {
                    scope.launch {
                        verifyStatus = ConnectionVerifyStatus.TESTING
                        verifyErrorMessage = null
                        runCatching {
                            val client = TavernCoreClient(baseUrl)
                            if (apiKey != initialApiKey && providerDefinition.secretKeys.isNotEmpty() && apiKey.isNotBlank()) {
                                client.writeSecret(providerDefinition.secretKeys.first(), apiKey, "默认密钥")
                                initialApiKey = apiKey
                            }
                            client.testConnection(
                                mode = providerDefinition.mode,
                                sourceValue = providerDefinition.sourceValue.orEmpty(),
                                apiServer = customUrl
                            )
                        }.onSuccess { result ->
                            if (result.success) {
                                verifyStatus = ConnectionVerifyStatus.SUCCESS
                                modelsList = result.models
                                if (result.models.isNotEmpty() && selectedModel.isBlank()) {
                                    selectedModel = result.models.first()
                                }
                                onShowMessage("连接成功，找到 ${result.models.size} 个模型")
                            } else {
                                verifyStatus = ConnectionVerifyStatus.FAILED
                                verifyErrorMessage = result.errorMessage ?: "连接失败"
                            }
                        }.onFailure { e ->
                            verifyStatus = ConnectionVerifyStatus.FAILED
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
                            apiKey.isBlank() && providerDefinition.requiresSecret -> "请先填写密钥"
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

            HorizontalDivider(color = Color(0x0DFFFFFF), modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "可用模型列表",
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
                                    val client = TavernCoreClient(baseUrl)
                                    modelsList = client.fetchModels(
                                        mode = providerDefinition.mode,
                                        sourceValue = providerDefinition.sourceValue.orEmpty(),
                                        apiServer = customUrl
                                    )
                                    onShowMessage("模型列表已成功刷新！")
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
                PrototypeListSurface(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    modelsList.forEachIndexed { index, model ->
                        val isSelected = model == selectedModel
                        PrototypeListItem(
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
            
            HorizontalDivider(color = Color(0x0DFFFFFF), modifier = Modifier.padding(vertical = 12.dp))
            
            PremiumSectionHeader(title = "模型并发与参数重载")
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "推理温度 (Temperature)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f", temp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = STThemePrimary
                    )
                }
                Slider(
                    value = temp,
                    onValueChange = { temp = it },
                    valueRange = 0.0f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = STThemePrimary,
                        activeTrackColor = STThemePrimary,
                        inactiveTrackColor = Color(0x0DFFFFFF)
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "最大上下文窗口 (Context Window)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${contextSize.toInt()} tokens",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = STThemePrimary
                    )
                }
                Slider(
                    value = contextSize,
                    onValueChange = { contextSize = it },
                    valueRange = 2048f..200000f,
                    colors = SliderDefaults.colors(
                        thumbColor = STThemePrimary,
                        activeTrackColor = STThemePrimary,
                        inactiveTrackColor = Color(0x0DFFFFFF)
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
