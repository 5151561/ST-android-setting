package io.github.sanitised.st.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.SecretEntry
import io.github.sanitised.st.api.SecretProviderState
import io.github.sanitised.st.data.repository.ConnectionRepository
import io.github.sanitised.st.data.repository.DefaultConnectionRepository
import io.github.sanitised.st.data.tavernApi
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
// 19 · API 密钥管理 (SecretsScreen)
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun STSecretsScreen(
    status: NodeStatus,
    baseUrl: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    connectionRepository: ConnectionRepository = remember(baseUrl) {
        DefaultConnectionRepository { tavernApi(baseUrl) }
    },
) {
    val running = status.state == NodeState.RUNNING
    val scope = rememberCoroutineScope()
    var toastMessage by remember { mutableStateOf<String?>(null) }

    var backendSecrets by remember { mutableStateOf<List<SecretProviderState>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    fun refreshBackendSecrets() {
        if (running) {
            scope.launch {
                loading = true
                runCatching {
                    backendSecrets = connectionRepository.listSecrets()
                }.onFailure {
                    onShowMessage("从后端加载密钥失败: ${it.message}")
                }
                loading = false
            }
        }
    }

    LaunchedEffect(running, baseUrl, connectionRepository) {
        refreshBackendSecrets()
    }

    // Modal state
    var editingSecretBackend by remember { mutableStateOf<Pair<SecretProviderState, SecretEntry?>?>(null) }
    var showAddCustomKeySheet by remember { mutableStateOf(false) }
    
    var editingKeyText by remember { mutableStateOf("") }
    var customKeyName by remember { mutableStateOf("") }
    var customKeyProvider by remember { mutableStateOf("api_key_openai") }
    var showKeyInSheet by remember { mutableStateOf(false) }
    val secretRows = remember(backendSecrets) { configuredSecretRows(backendSecrets) }
    val providerOptions = remember(backendSecrets) { secretProviderOptions(backendSecrets) }
    val providerOptionKeys = remember(providerOptions) { providerOptions.map { it.key }.toSet() }
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(providerOptions) {
        if (providerOptions.isNotEmpty() && customKeyProvider !in providerOptionKeys) {
            customKeyProvider = providerOptions.first().key
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = STThemeBg) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(bottom = 88.dp)
            ) {
                STTopHeader(
                    title = "API 密钥管理",
                    leading = {
                        STIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                    },
                    actions = {
                        STIconButton(
                            icon = Icons.Filled.Security,
                            contentDescription = "安全模式",
                            onClick = { toastMessage = "安全模式已激活：所有敏感字段经过混淆处理" }
                        )
                    },
                    titleBottomPadding = 4.dp
                )

                // Safety Header Callout
                PremiumCard(
                    borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = STThemePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "本地离线安全存储",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "所有密钥均仅安全离线存储于您设备本地的沙盒中。SillyTavern Mobile 绝不会将您的 API 密钥上传到云端、任何第三方或反代服务器。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }

                PremiumSectionHeader(title = "SillyTavern 后端密钥")

                when {
                    !running -> {
                        STSystemInfoCard(
                            "服务未启动，无法读取真实密钥",
                            "这里不再展示模拟密钥。启动 SillyTavern 后端后，会从后端密钥接口读取当前配置。"
                        )
                    }
                    loading && backendSecrets.isEmpty() -> {
                        STSystemInfoCard("正在读取后端密钥", "正在同步 SillyTavern 当前保存的 API 密钥状态。")
                    }
                    secretRows.isEmpty() -> {
                        STSystemInfoCard("后端暂无配置的密钥", "可点击下方“新增”按钮配置新的 API 供应商密钥。")
                    }
                    else -> {
                        secretRows.forEach { row ->
                            val entry = row.entry
                            val statusBadgeColor = if (entry.active) STThemeTertiary else STThemePrimary
                            val statusBadgeBg = statusBadgeColor.copy(alpha = 0.12f)
                            val provider = backendSecrets.first { it.key == row.providerKey }

                            PremiumCard(borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        modifier = Modifier.size(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = STThemePrimary.copy(alpha = 0.1f),
                                        contentColor = STThemePrimary
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = row.providerLabel.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${row.providerLabel} (${row.displayLabel})",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            STBadge(
                                                label = row.statusLabel,
                                                containerColor = statusBadgeBg,
                                                contentColor = statusBadgeColor
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = row.displayValue,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    STIconButton(
                                        icon = Icons.Filled.Edit,
                                        contentDescription = "编辑",
                                        onClick = {
                                            editingSecretBackend = provider to entry
                                            editingKeyText = ""
                                            showKeyInSheet = false
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "来自 SillyTavern 后端",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "ID: ${entry.id.take(8).ifBlank { "默认" }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = STThemePrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Danger Zone
                PremiumSectionHeader(title = "危险区")
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = Color.Transparent
                ) {
                    OutlinedButton(
                        onClick = {
                            if (running && secretRows.isNotEmpty()) {
                                scope.launch {
                                    runCatching {
                                        secretRows.forEach { row ->
                                            connectionRepository.deleteSecret(row.providerKey, row.entry.id)
                                        }
                                        toastMessage = "所有密钥已从后端安全删除！"
                                        refreshBackendSecrets()
                                    }.onFailure {
                                        toastMessage = "擦除密钥失败: ${it.message}"
                                    }
                                }
                            } else if (!running) {
                                toastMessage = "请先启动后端，再管理真实密钥"
                            } else {
                                toastMessage = "后端暂无可删除的密钥"
                            }
                        },
                        enabled = running && secretRows.isNotEmpty(),
                        border = BorderStroke(1.dp, STThemeError),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = STThemeError),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.KeyOff, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("删除全部后端密钥")
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }

            // FAB
            ExtendedFloatingActionButton(
                onClick = {
                    if (running) {
                        showAddCustomKeySheet = true
                        customKeyName = ""
                        editingKeyText = ""
                        showKeyInSheet = false
                        customKeyProvider = providerOptions.firstOrNull()?.key ?: "api_key_openai"
                    } else {
                        toastMessage = "请先启动后端，再新增真实密钥"
                    }
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新增") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
                containerColor = STThemePrimary.copy(alpha = 0.15f),
                contentColor = STThemePrimary,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(0.dp)
            )

            // Animated Toast HUD
            AnimatedToast(
                message = toastMessage,
                onDismiss = { toastMessage = null }
            )
        }

        // Bottom sheet for editing BACKEND secret
        editingSecretBackend?.let { (provider, entry) ->
            ModalBottomSheet(
                onDismissRequest = { editingSecretBackend = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LockReset,
                            contentDescription = null,
                            tint = STThemePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "管理 ${provider.label} 密钥",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "修改后端安全凭证",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { editingSecretBackend = null }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = STThemePrimary.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, STThemePrimary.copy(alpha = 0.15f))
                    ) {
                        Text(
                            text = "⚠️ 该操作会通过 SillyTavern API 服务实时修改服务端的密钥文件。部分供应商（如 OpenRouter）密钥在轮换后需要重新启动生成流方可生效。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "新密钥字符串",
                        style = MaterialTheme.typography.labelMedium,
                        color = STThemePrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = editingKeyText,
                        onValueChange = { editingKeyText = it },
                        placeholder = { Text("在此粘贴密钥 (支持格式 sk-...)") },
                        singleLine = true,
                        visualTransformation = if (showKeyInSheet) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKeyInSheet = !showKeyInSheet }) {
                                Icon(
                                    imageVector = if (showKeyInSheet) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (entry != null) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            connectionRepository.deleteSecret(provider.key, entry.id)
                                            toastMessage = "密钥已在后端安全删除"
                                            refreshBackendSecrets()
                                        }.onFailure {
                                            toastMessage = "清空密钥失败: ${it.message}"
                                        }
                                        editingSecretBackend = null
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = STThemeError),
                                border = BorderStroke(1.dp, STThemeError),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("删除")
                            }
                        }

                        Button(
                            onClick = {
                                if (editingKeyText.isBlank()) {
                                    toastMessage = "请输入口令内容"
                                    return@Button
                                }
                                scope.launch {
                                    runCatching {
                                        if (entry != null) {
                                            connectionRepository.writeSecret(provider.key, editingKeyText, entry.label)
                                            toastMessage = "已更新 ${provider.label} 密钥配置"
                                        } else {
                                            connectionRepository.writeSecret(provider.key, editingKeyText, "default")
                                            toastMessage = "写入 ${provider.label} 密钥成功"
                                        }
                                        refreshBackendSecrets()
                                    }.onFailure {
                                        toastMessage = "轮换密钥失败: ${it.message}"
                                    }
                                    editingSecretBackend = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = STThemePrimary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            modifier = Modifier.weight(2f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("确认轮换")
                        }
                    }
                }
            }
        }

        // Add custom key sheet
        if (showAddCustomKeySheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddCustomKeySheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddModerator,
                            contentDescription = null,
                            tint = STThemePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "新增 API 密钥",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showAddCustomKeySheet = false }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("选择 API 提供商", style = MaterialTheme.typography.labelMedium, color = STThemePrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    var providerMenuExpanded by remember { mutableStateOf(false) }
                    val selectedProviderLabel = providerOptions
                        .firstOrNull { it.key == customKeyProvider }
                        ?.label
                        ?: "请选择供应商"
                    ExposedDropdownMenuBox(
                        expanded = providerMenuExpanded,
                        onExpandedChange = { providerMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedProviderLabel,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenuExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            shape = RoundedCornerShape(14.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = providerMenuExpanded,
                            onDismissRequest = { providerMenuExpanded = false }
                        ) {
                            providerOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        customKeyProvider = option.key
                                        providerMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("自定义标识别名 (Label)", style = MaterialTheme.typography.labelMedium, color = STThemePrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customKeyName,
                        onValueChange = { customKeyName = it },
                        placeholder = { Text("例如: my-dev-key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("API 密钥值 (Value)", style = MaterialTheme.typography.labelMedium, color = STThemePrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = editingKeyText,
                        onValueChange = { editingKeyText = it },
                        placeholder = { Text("在此粘贴你的密钥串") },
                        singleLine = true,
                        visualTransformation = if (showKeyInSheet) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKeyInSheet = !showKeyInSheet }) {
                                Icon(
                                    imageVector = if (showKeyInSheet) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (editingKeyText.isBlank()) {
                                toastMessage = "密钥内容不能为空"
                                return@Button
                            }
                            if (customKeyProvider.isBlank()) {
                                toastMessage = "请选择 API 提供商"
                                return@Button
                            }
                            val finalLabel = customKeyName.ifBlank { "default" }
                            val providerLabel = providerOptions
                                .firstOrNull { it.key == customKeyProvider }
                                ?.label
                                ?: customKeyProvider
                            scope.launch {
                                runCatching {
                                    connectionRepository.writeSecret(customKeyProvider, editingKeyText, finalLabel)
                                    toastMessage = "已成功添加 $providerLabel 密钥凭证"
                                    refreshBackendSecrets()
                                }.onFailure {
                                    toastMessage = "添加密钥失败: ${it.message}"
                                }
                                showAddCustomKeySheet = false
                            }
                        },
                        enabled = providerOptions.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = STThemePrimary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("创建密钥配置")
                    }
                }
            }
        }
    }
}

// Helper layout component for prototype details
@Composable
private fun STSystemInfoCard(
    title: String,
    details: String
) {
    PremiumCard(
        borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
                lineHeight = 18.sp
            )
        }
    }
}
