package io.github.sanitised.st.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.SecretEntry
import io.github.sanitised.st.api.SecretProviderState
import io.github.sanitised.st.api.TavernCoreClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
// Shared HUD Primitives & Style Tokens (1:1 Matching tokens.css)
// ─────────────────────────────────────────────────────────────

val STThemePrimary = Color(0xFFFFB871)
val STThemeTertiary = Color(0xFFC6CB95)
val STThemeError = Color(0xFFFFB4AB)
val STThemeBg = Color(0xFF18130E)

@Composable
fun GlowStatusDot(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(14.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.28f))
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = Color(0x1AFFFFFF),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = RoundedCornerShape(20.dp),
        color = Color(0x08FFFFFF),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun PremiumSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Bold
            ),
            color = STThemePrimary
        )
        if (trailing != null) {
            Row(content = trailing)
        }
    }
}

@Composable
fun STPreviewBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = STThemePrimary.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, STThemePrimary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Science,
                contentDescription = null,
                tint = STThemePrimary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "原型预览 — 此页面数据为设计占位，功能尚未接入后端",
                style = MaterialTheme.typography.bodySmall,
                color = STThemePrimary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AnimatedToast(
    message: String?,
    onDismiss: () -> Unit
) {
    var visible by remember(message) { mutableStateOf(message != null) }
    LaunchedEffect(message) {
        if (message != null) {
            visible = true
            delay(1200)
            visible = false
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(150))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 56.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 320.dp)
            ) {
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
}

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
    modifier: Modifier = Modifier
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
                    val client = TavernCoreClient(baseUrl)
                    backendSecrets = client.listSecrets()
                }.onFailure {
                    onShowMessage("从后端加载密钥失败: ${it.message}")
                }
                loading = false
            }
        }
    }

    LaunchedEffect(running, baseUrl) {
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
                    borderColor = Color(0x12FFFFFF)
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

                            PremiumCard(borderColor = Color(0x12FFFFFF)) {
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
                                HorizontalDivider(color = Color(0x0DFFFFFF))
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
                                        val client = TavernCoreClient(baseUrl)
                                        secretRows.forEach { row ->
                                            client.deleteSecret(row.providerKey, row.entry.id)
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, STThemeError),
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, STThemePrimary.copy(alpha = 0.15f))
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
                                            val client = TavernCoreClient(baseUrl)
                                            client.deleteSecret(provider.key, entry.id)
                                            toastMessage = "密钥已在后端安全删除"
                                            refreshBackendSecrets()
                                        }.onFailure {
                                            toastMessage = "清空密钥失败: ${it.message}"
                                        }
                                        editingSecretBackend = null
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = STThemeError),
                                border = androidx.compose.foundation.BorderStroke(1.dp, STThemeError),
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
                                        val client = TavernCoreClient(baseUrl)
                                        if (entry != null) {
                                            client.writeSecret(provider.key, editingKeyText, entry.label)
                                            toastMessage = "已更新 ${provider.label} 密钥配置"
                                        } else {
                                            client.writeSecret(provider.key, editingKeyText, "default")
                                            toastMessage = "写入 ${provider.label} 密钥成功"
                                        }
                                        refreshBackendSecrets()
                                    }.onFailure {
                                        toastMessage = "轮换密钥失败: ${it.message}"
                                    }
                                    editingSecretBackend = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = STThemePrimary, contentColor = Color(0xFF4A2700)),
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
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        providerOptions.forEach { option ->
                            val sel = customKeyProvider == option.key
                            FilterChip(
                                selected = sel,
                                onClick = { customKeyProvider = option.key },
                                label = { Text(option.label) }
                            )
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
                                    val client = TavernCoreClient(baseUrl)
                                    client.writeSecret(customKeyProvider, editingKeyText, finalLabel)
                                    toastMessage = "已成功添加 $providerLabel 密钥凭证"
                                    refreshBackendSecrets()
                                }.onFailure {
                                    toastMessage = "添加密钥失败: ${it.message}"
                                }
                                showAddCustomKeySheet = false
                            }
                        },
                        enabled = providerOptions.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = STThemePrimary, contentColor = Color(0xFF4A2700)),
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

// ─────────────────────────────────────────────────────────────
// 21 · 扩展中心 (ExtensionsScreen)
// ─────────────────────────────────────────────────────────────
@Composable
fun STExtensionsScreen(
    onBack: () -> Unit,
    onOpenQuickReplies: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeFilter by remember { mutableStateOf("all") }
    var activeExtras by remember { mutableStateOf(true) }

    var extensionsList by remember {
        mutableStateOf(
            listOf(
                SimulatedExtension("qr", Icons.Filled.Bolt, "快捷回复", "全局/单聊/角色三层触发", true, "chat"),
                SimulatedExtension("mem", Icons.Filled.Memory, "记忆与回顾", "RAG 检索及自动会话回滚机制", true, "data"),
                SimulatedExtension("tts", Icons.Filled.RecordVoiceOver, "TTS / STT 语音", "多音色配音与本地语音输入接口", true, "media"),
                SimulatedExtension("sd", Icons.Filled.Palette, "Stable Diffusion 绘图", "SD 绘图管道自动描述生成", false, "media"),
                SimulatedExtension("trans", Icons.Filled.Translate, "实时翻译扩展", "集成 Google/Deepl 离线词包自动互译", false, "chat")
            )
        )
    }

    val visibleExt = remember(activeFilter, extensionsList) {
        extensionsList.filter { activeFilter == "all" || it.cat == activeFilter }
    }

    Surface(modifier = modifier.fillMaxSize(), color = STThemeBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            STTopHeader(
                title = "扩展中心",
                leading = {
                    STIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                },
                actions = {
                    STIconButton(Icons.Filled.CloudDownload, "扩展商店", { onShowMessage("打开扩展应用商店…") })
                    STIconButton(Icons.Filled.Settings, "设置", { onShowMessage("扩展中心高级首选项功能开发中") })
                },
                titleBottomPadding = 4.dp
            )

            STPreviewBanner()

            // Horizontal Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "all" to "全部扩展",
                    "chat" to "聊天增强",
                    "media" to "多媒体",
                    "data" to "数据层"
                ).forEach { (id, label) ->
                    val sel = activeFilter == id
                    FilterChip(
                        selected = sel,
                        onClick = { activeFilter = id },
                        label = { Text(label) }
                    )
                }
            }

            // Extras API status card (Premium Translucent Card style)
            PremiumCard(
                borderColor = if (activeExtras) STThemeTertiary.copy(alpha = 0.25f) else Color(0x12FFFFFF)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlowStatusDot(color = if (activeExtras) STThemeTertiary else MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SillyTavern Extras API",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Switch(checked = activeExtras, onCheckedChange = { activeExtras = it })
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (activeExtras) "Extras API 已启用（原型预览，实际状态以后端为准）" else "Extras 离线。绘图、语音朗读、图片识别等依赖项将暂时无法使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            PremiumSectionHeader("已安装的扩展")

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (ext in visibleExt) {
                    Surface(
                        onClick = {
                            if (ext.id == "qr") {
                                onOpenQuickReplies()
                            } else {
                                onShowMessage("正在配置 '${ext.title}' 扩展…")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x06FFFFFF),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (ext.active) STThemePrimary.copy(alpha = 0.25f) else Color(0x0DFFFFFF)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = if (ext.active) STThemePrimary.copy(alpha = 0.1f) else Color(0x0DFFFFFF),
                                contentColor = if (ext.active) STThemePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(imageVector = ext.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = ext.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (ext.active) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        GlowStatusDot(color = STThemeTertiary)
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = ext.sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Switch(
                                    checked = ext.active,
                                    onCheckedChange = { active ->
                                        extensionsList = extensionsList.map {
                                            if (it.id == ext.id) it.copy(active = active) else it
                                        }
                                        onShowMessage("${ext.title} 已" + (if (active) "启用" else "禁用"))
                                    }
                                )
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 20 · 作者注 & CFG (AuthorNoteCFGScreen)
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun STAuthorNoteCFGScreen(
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableStateOf("note") }
    var cfgValue by remember { mutableStateOf(1.3f) }
    
    var positivePrompt by remember { mutableStateOf("细腻的心理活动，带有一丝犹豫，多用潜台词描写。") }
    var negativePrompt by remember { mutableStateOf("不要写粗鲁的词，不要快速推平剧情，不要抢玩家台词。") }
    var authorNote by remember { mutableStateOf("[场景：雨夜咖啡馆。风格：慢节奏、细腻动作、轻微暧昧。避免突然跳出角色。]") }

    var cascadeChips by remember {
        mutableStateOf(
            listOf(
                CascadeChipState("慢节奏", true),
                CascadeChipState("细腻动作", true),
                CascadeChipState("忽视提示词", false),
                CascadeChipState("强感情", false)
            )
        )
    }

    var activeSheet by remember { mutableStateOf<String?>(null) }
    var tempEditText by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val cfgDesc = remember(cfgValue) {
        when {
            cfgValue < 1.0f -> "保守严谨 (适合逻辑或事实问答，生成不易乱码)"
            cfgValue >= 1.0f && cfgValue < 1.4f -> "平衡采样 (日常角色扮演推荐，兼顾逻辑与性格特征)"
            cfgValue >= 1.4f && cfgValue < 1.8f -> "创意发散 (情节张力大，文字更具小说描绘感)"
            else -> "混沌创作 (AI 情感表达极度充沛，可能产生循环乱码)"
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = STThemeBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            STTopHeader(
                title = "作者注 & CFG",
                leading = {
                    STIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                },
                actions = {
                    STIconButton(Icons.Filled.Tune, "采样调试", { onShowMessage("打开采样调试仪表盘…") })
                    STIconButton(
                        icon = Icons.Filled.Save,
                        contentDescription = "保存",
                        onClick = { onShowMessage("作者注与采样逻辑已注入级联栈") }
                    )
                },
                titleBottomPadding = 4.dp
            )

            STPreviewBanner()

            // Stateful Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                listOf("note" to "作者注", "cfg" to "CFG 控制", "cascade" to "级联控制").forEach { (id, label) ->
                    val sel = tab == id
                    Button(
                        onClick = { tab = id },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .padding(horizontal = 2.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (sel) STThemePrimary.copy(alpha = 0.12f) else Color.Transparent,
                            contentColor = if (sel) STThemePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                when (tab) {
                    "note" -> {
                        // Cascade levels preview
                        PremiumCard(
                            borderColor = Color(0x12FFFFFF)
                        ) {
                            Text(
                                text = "注入层级级联预览",
                                style = MaterialTheme.typography.labelSmall,
                                color = STThemePrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("1. 系统提示词 (System)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("开", style = MaterialTheme.typography.bodySmall, color = STThemeTertiary, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("2. 局部作者注 (Author Note)", style = MaterialTheme.typography.bodySmall, color = STThemePrimary, fontWeight = FontWeight.Bold)
                                    Text("深度 4", style = MaterialTheme.typography.bodySmall, color = STThemePrimary, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("3. 聊天会话缓存 (History)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("就绪", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        PremiumSectionHeader(
                            title = "当前聊天作者注",
                            trailing = {
                                Text(
                                    text = "修改内容",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = STThemePrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        activeSheet = "note"
                                        tempEditText = authorNote
                                    }
                                )
                            }
                        )

                        PremiumCard(
                            borderColor = Color(0x12FFFFFF)
                        ) {
                            Text(
                                text = authorNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        var sw1 by remember { mutableStateOf(true) }
                        var sw2 by remember { mutableStateOf(true) }

                        STListItem(
                            headline = "随世界书触发扫描",
                            supporting = "允许作者注激活 Lorebook 中的关联关键词",
                            trailing = { Switch(checked = sw1, onCheckedChange = { sw1 = it }) },
                            onClick = { sw1 = !sw1 }
                        )

                        STListItem(
                            headline = "仅在当前单聊中生效",
                            supporting = "不将该作者注回写到角色卡元数据中",
                            trailing = { Switch(checked = sw2, onCheckedChange = { sw2 = it }) },
                            onClick = { sw2 = !sw2 }
                        )
                    }
                    "cfg" -> {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Classifier-Free Guidance (CFG)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                Text(text = String.format("%.1f", cfgValue), style = MaterialTheme.typography.titleMedium, color = STThemePrimary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = cfgValue,
                                onValueChange = { cfgValue = it },
                                valueRange = 0.5f..2.5f,
                                steps = 19
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = STThemePrimary.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, STThemePrimary.copy(alpha = 0.15f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("当前采样特征：", style = MaterialTheme.typography.labelSmall, color = STThemePrimary, fontWeight = FontWeight.Bold)
                                    Text(cfgDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }

                        PremiumSectionHeader(title = "提示词分块覆盖")

                        PremiumCard(
                            onClick = {
                                activeSheet = "positive"
                                tempEditText = positivePrompt
                            },
                            borderColor = Color(0x12FFFFFF)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlowStatusDot(color = STThemeTertiary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("正向文风暗示 (Positive Prompt)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(positivePrompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        PremiumCard(
                            onClick = {
                                activeSheet = "negative"
                                tempEditText = negativePrompt
                            },
                            borderColor = Color(0x12FFFFFF)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlowStatusDot(color = STThemeError)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("反向避开词 (Negative Prompt)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(negativePrompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    "cascade" -> {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = "文风微调与级联预设",
                                style = MaterialTheme.typography.titleSmall,
                                color = STThemePrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for ((index, chip) in cascadeChips.withIndex()) {
                                    FilterChip(
                                        selected = chip.active,
                                        onClick = {
                                            cascadeChips = cascadeChips.mapIndexed { i, c ->
                                                if (i == index) c.copy(active = !c.active) else c
                                            }
                                            onShowMessage("已调整合并预设: ${chip.label}")
                                        },
                                        label = { Text(chip.label) }
                                    )
                                }
                            }
                        }

                        PremiumSectionHeader(title = "高级级联属性")

                        var sw3 by remember { mutableStateOf(true) }
                        var sw4 by remember { mutableStateOf(true) }

                        STListItem(
                            headline = "强行合并角色反向提示词",
                            supporting = "自动追加当前角色卡内自带 of negative 字段",
                            trailing = { Switch(checked = sw3, onCheckedChange = { sw3 = it }) },
                            onClick = { sw3 = !sw3 }
                        )

                        STListItem(
                            headline = "对级联使用深度合并策略",
                            supporting = "深度 2 级合并，减少对上下文预算消耗",
                            trailing = { Switch(checked = sw4, onCheckedChange = { sw4 = it }) },
                            onClick = { sw4 = !sw4 }
                        )
                    }
                }
            }
        }

        // Bottom sheet for modifications
        activeSheet?.let { s ->
            ModalBottomSheet(
                onDismissRequest = { activeSheet = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    val title = when (s) {
                        "note" -> "修改作者注内容"
                        "positive" -> "修改正向文风提示词"
                        else -> "修改反向避开词"
                    }
                    val icon = when (s) {
                        "note" -> Icons.Filled.Edit
                        "positive" -> Icons.Filled.AddCircle
                        else -> Icons.Filled.RemoveCircle
                    }
                    val tint = when (s) {
                        "note" -> STThemePrimary
                        "positive" -> STThemeTertiary
                        else -> STThemeError
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { activeSheet = null }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = tempEditText,
                        onValueChange = { tempEditText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        shape = RoundedCornerShape(14.dp),
                        placeholder = { Text("在此输入内容…") }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            when (s) {
                                "note" -> authorNote = tempEditText
                                "positive" -> positivePrompt = tempEditText
                                "negative" -> negativePrompt = tempEditText
                            }
                            onShowMessage("已更新级联参数")
                            activeSheet = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = STThemePrimary, contentColor = Color(0xFF4A2700)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("保存并写入提示词")
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 22 · 快捷回复 (QuickReplyScreen)
// ─────────────────────────────────────────────────────────────
@Composable
fun STQuickReplyScreen(
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var scope by remember { mutableStateOf("chat") }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    
    var repliesList by remember {
        mutableStateOf(
            listOf(
                SimulatedReply("让 Aria 继续", "/sendas name=\"Aria\" ...", "促使 AI 立即追加生成多一轮动作描述"),
                SimulatedReply("改写上一句", "/rewrite last", "删掉最后回复并立刻发起新生成指令"),
                SimulatedReply("转场到夜晚", "/narrate \"夜色慢慢变浓…\"", "快捷叙述，推进物理场景时间线")
            )
        )
    }

    Surface(modifier = modifier.fillMaxSize(), color = STThemeBg) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                STTopHeader(
                    title = "快捷回复配置",
                    leading = {
                        STIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                    },
                    actions = {
                        STIconButton(Icons.Filled.FileUpload, "导出", { toastMessage = "已导出快捷回复配置" })
                        STIconButton(Icons.Filled.Add, "新增", { toastMessage = "新增快捷指令宏成功" })
                    },
                    titleBottomPadding = 4.dp
                )

                STPreviewBanner()

                // Stateful Scope Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    listOf("global" to "全局宏", "chat" to "单聊特有", "character" to "当前角色").forEach { (id, label) ->
                        val sel = scope == id
                        Button(
                            onClick = { scope = id },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .padding(horizontal = 2.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (sel) STThemePrimary.copy(alpha = 0.12f) else Color.Transparent,
                                contentColor = if (sel) STThemePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp)
                ) {
                    // REAL-TIME CHAT COMPOSER SIMULATION PILL PREVIEWER (Composer HUD) - Premium restored 1:1!
                    PremiumCard(
                        borderColor = STThemeTertiary.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = "聊天视图输入栏效果预览 (Composer HUD)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = STThemeTertiary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0x0AFFFFFF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x0DFFFFFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Horizontal scroll pills of replies
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    repliesList.forEach { r ->
                                        Surface(
                                            onClick = { toastMessage = "快捷发送指令：\"${r.label}\"" },
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0x0DFFFFFF),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x10FFFFFF)),
                                            modifier = Modifier.height(26.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Bolt,
                                                    contentDescription = null,
                                                    tint = STThemePrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = r.label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = STThemePrimary
                                                )
                                            }
                                        }
                                    }
                                }

                                // Composer input bar
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0x06FFFFFF),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x0AFFFFFF))
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.CenterStart,
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        ) {
                                            Text(
                                                text = "在此测试点击上方气泡发送指令…",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier.size(34.dp),
                                        shape = CircleShape,
                                        color = STThemePrimary,
                                        contentColor = Color(0xFF4A2700)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    PremiumSectionHeader(
                        title = "当前集合中指令宏 (${repliesList.size})",
                        trailing = {
                            Text(
                                text = "重置默认",
                                style = MaterialTheme.typography.labelMedium,
                                color = STThemePrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    repliesList = listOf(
                                        SimulatedReply("让 Aria 继续", "/sendas name=\"Aria\" ...", "促使 AI 立即追加生成多一轮动作描述"),
                                        SimulatedReply("改写上一句", "/rewrite last", "删掉最后回复并立刻发起新生成指令"),
                                        SimulatedReply("转场到夜晚", "/narrate \"夜色慢慢变浓…\"", "快捷叙述，推进物理场景时间线")
                                    )
                                    toastMessage = "已重置为出厂快捷宏"
                                }
                            )
                        }
                    )

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for ((index, reply) in repliesList.withIndex()) {
                            PremiumCard(
                                borderColor = Color(0x12FFFFFF)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0x0DFFFFFF),
                                        contentColor = STThemePrimary
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.SmartButton,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = reply.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = reply.macro,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            repliesList = repliesList.filterIndexed { i, _ -> i != index }
                                            toastMessage = "指令宏已安全删除"
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "删除",
                                            tint = STThemeError,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = reply.desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            AnimatedToast(
                message = toastMessage,
                onDismiss = { toastMessage = null }
            )
        }
    }
}

data class SimulatedReply(
    val label: String,
    val macro: String,
    val desc: String
)

// Helper layout component for prototype details
@Composable
private fun STSystemInfoCard(
    title: String,
    details: String
) {
    PremiumCard(
        borderColor = Color(0x12FFFFFF)
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

// ─────────────────────────────────────────────────────────────
// 19 · 记忆与回顾 (MemoryScreen) - Premium High-Fidelity 1:1 Restoration
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun STMemoryScreen(
    status: NodeStatus,
    baseUrl: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableStateOf("summary") }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var toastMessage by remember { mutableStateOf<String?>(null) }

    // Auto summary states
    var summaryText by remember {
        mutableStateOf(
            "Aria 记得用户常点拿铁和焦糖海盐蛋糕。今天用户改点热可可，Aria 借机提到店长今早做了限量蛋糕，并询问是否加棉花糖或肉桂。"
        )
    }
    var summaryLimit by remember { mutableFloatStateOf(180f) }
    var editingSummary by remember { mutableStateOf(false) }
    var tempSummaryText by remember { mutableStateOf("") }

    // Checkpoints states
    var activeCheckpoint by remember { mutableStateOf<SimulatedCheckpoint?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Vector search matching logic
    val searchResults = remember(query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) {
            emptyList()
        } else {
            val mockDB = listOf(
                VectorResult("焦糖海盐蛋糕, 招牌蛋糕", 0.94f, "店长今早现做的限量招牌焦糖海盐蛋糕，Aria 会偷偷留给熟客。"),
                VectorResult("店长, 老板娘, 雪", 0.82f, "六十多岁，叫雪。开店三十年。睡得早，下午一般不在店里。"),
                VectorResult("常客, 老顾客", 0.76f, "常客包括：每天读报的退休医生；一对中学生情侣；周三总迟到的小说家。"),
                VectorResult("拿铁, 咖啡", 0.71f, "店里最畅销的咖啡。选用埃塞俄比亚拼配豆，深烘，带有一丝可可与胡桃的香气。")
            )
            mockDB.filter { it.keys.lowercase().contains(q) || it.content.lowercase().contains(q) }
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = STThemeBg) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                STTopHeader(
                    title = "记忆与回顾",
                    leading = {
                        STIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                    },
                    actions = {
                        STIconButton(
                            icon = Icons.Filled.History,
                            contentDescription = "检查点历史",
                            onClick = { toastMessage = "检查点历史就绪" }
                        )
                        IconButton(
                            onClick = { toastMessage = "本地 RAG 向量数据库处于运行就绪状态 (SQLite-VSS)" }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storage,
                                contentDescription = "数据库状态",
                                tint = STThemePrimary
                            )
                        }
                    },
                    titleBottomPadding = 4.dp
                )

                STPreviewBanner()

                // Stateful Tabs (Summary, Vector search, checkpoints timeline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    for ((id, label) in listOf("summary" to "自动摘要", "vector" to "向量检索 (RAG)", "checkpoints" to "回滚检查点")) {
                        val sel = tab == id
                        Button(
                            onClick = { tab = id },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .padding(horizontal = 2.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (sel) STThemePrimary.copy(alpha = 0.12f) else Color.Transparent,
                                contentColor = if (sel) STThemePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp)
                ) {
                    when (tab) {
                        "summary" -> {
                            // Smart cognitive dashboard (Memory HUD)
                            PremiumCard(
                                borderColor = Color(0x12FFFFFF)
                            ) {
                                Text(
                                    text = "智能认知仪表盘 (Memory HUD)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 0.8.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = STThemePrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0x06FFFFFF))
                                            .border(1.dp, Color(0x0AFFFFFF), RoundedCornerShape(12.dp))
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("已压缩容量", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("342 t", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0x06FFFFFF))
                                            .border(1.dp, Color(0x0AFFFFFF), RoundedCornerShape(12.dp))
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("触发节奏", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("每 6 轮", style = MaterialTheme.typography.titleMedium, color = STThemeError, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0x06FFFFFF))
                                            .border(1.dp, Color(0x0AFFFFFF), RoundedCornerShape(12.dp))
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("RAG 模式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                GlowStatusDot(color = STThemeTertiary)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("活跃", style = MaterialTheme.typography.titleSmall, color = STThemeTertiary, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            PremiumSectionHeader(title = "自动摘要配置")

                            var autoSummaryUpdate by remember { mutableStateOf(true) }
                            STListItem(
                                headline = "自动摘要更新",
                                supporting = "在后台悄悄重构，不阻塞生成等待时间",
                                trailing = { Switch(checked = autoSummaryUpdate, onCheckedChange = { autoSummaryUpdate = it }) },
                                onClick = { autoSummaryUpdate = !autoSummaryUpdate }
                            )

                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("摘要目标字数上限", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                    Text(text = "${summaryLimit.toInt()} 字", style = MaterialTheme.typography.titleMedium, color = STThemePrimary, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Slider(
                                    value = summaryLimit,
                                    onValueChange = { summaryLimit = it },
                                    valueRange = 50f..400f,
                                    steps = 34
                                )
                            }

                            PremiumSectionHeader(
                                title = "当前合并摘要片段",
                                trailing = {
                                    Text(
                                        text = "手动编辑",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = STThemePrimary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            tempSummaryText = summaryText
                                            editingSummary = true
                                        }
                                    )
                                }
                            )

                            PremiumCard(
                                borderColor = Color(0x12FFFFFF)
                            ) {
                                Text(
                                    text = summaryText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        "vector" -> {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(
                                    text = "本地知识片段向量检索 (RAG Test)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = STThemePrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = CircleShape,
                                    color = Color(0x0AFFFFFF),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x0DFFFFFF))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.TravelExplore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Box(modifier = Modifier.weight(1f)) {
                                            if (query.isEmpty()) {
                                                Text(
                                                    text = "在此输入“蛋糕”或“店长”模拟召回…",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            androidx.compose.foundation.text.BasicTextField(
                                                value = query,
                                                onValueChange = { query = it },
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        if (query.isNotEmpty()) {
                                            IconButton(onClick = { query = "" }, modifier = Modifier.size(20.dp)) {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = "清除",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            PremiumSectionHeader(title = "向量库命中结果 (${searchResults.size})")

                            if (searchResults.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (res in searchResults) {
                                        PremiumCard(
                                            borderColor = Color(0x12FFFFFF)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = res.keys,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                                    color = STThemePrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                STBadge(
                                                    label = "相似度: " + String.format("%.2f", res.similarity),
                                                    containerColor = STThemeTertiary.copy(alpha = 0.12f),
                                                    contentColor = STThemeTertiary
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = res.content,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FindInPage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "在输入框检索，测试余弦相似度匹配",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        "checkpoints" -> {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Button(
                                    onClick = { toastMessage = "已保存当前节点快照" },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = STThemePrimary.copy(alpha = 0.15f), contentColor = STThemePrimary)
                                ) {
                                    Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("保存当前会话检查点")
                                }
                            }

                            PremiumSectionHeader(title = "时间线回滚点")

                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val checkpoints = listOf(
                                    SimulatedCheckpoint("cp1", "热可可分支 (加肉桂)", "刚才 · Aria 询问配方", true, "当前"),
                                    SimulatedCheckpoint("cp2", "橱窗蛋糕分支 (焦糖海盐)", "14:03 · 用户刚刚换口味", false, "回滚"),
                                    SimulatedCheckpoint("cp3", "开场白节点", "14:02 · 初始问候阶段", false, "根")
                                )
                                for (cp in checkpoints) {
                                    Surface(
                                        onClick = {
                                            if (!cp.active) {
                                                activeCheckpoint = cp
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (cp.active) STThemePrimary.copy(alpha = 0.05f) else Color(0x03FFFFFF),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = 1.dp,
                                            color = if (cp.active) STThemePrimary.copy(alpha = 0.25f) else Color(0x0AFFFFFF)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (cp.active) Icons.Filled.RadioButtonChecked else Icons.Filled.Restore,
                                                contentDescription = null,
                                                tint = if (cp.active) STThemePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = cp.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = cp.time,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            STBadge(
                                                label = cp.tag,
                                                containerColor = if (cp.active) STThemePrimary.copy(alpha = 0.12f) else Color(0x0DFFFFFF),
                                                contentColor = if (cp.active) STThemePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Animated Toast HUD
            AnimatedToast(
                message = toastMessage,
                onDismiss = { toastMessage = null }
            )
        }

        // Summary Editor sheet
        if (editingSummary) {
            ModalBottomSheet(
                onDismissRequest = { editingSummary = false },
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
                            imageVector = Icons.Filled.EditNote,
                            contentDescription = null,
                            tint = STThemePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "修改摘要内容",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { editingSummary = false }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = tempSummaryText,
                        onValueChange = { tempSummaryText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        shape = RoundedCornerShape(14.dp),
                        placeholder = { Text("在此输入内容…") }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            summaryText = tempSummaryText
                            toastMessage = "手动摘要修改成功"
                            editingSummary = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = STThemePrimary, contentColor = Color(0xFF4A2700)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("保存并写入提示词")
                    }
                }
            }
        }

        // Checkpoint Rollback Sheet
        activeCheckpoint?.let { cp ->
            ModalBottomSheet(
                onDismissRequest = { activeCheckpoint = null },
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
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = STThemeError,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "会话分支回滚确认",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { activeCheckpoint = null }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = STThemeError.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, STThemeError.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = "您正将当前会话回滚至 ${cp.name} 节点。此操作会永久丢弃后续的所有对话记录！建议先导出聊天。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp),
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { activeCheckpoint = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("取消")
                        }

                        Button(
                            onClick = {
                                toastMessage = "已成功回滚会话至 \"${cp.name}\""
                                activeCheckpoint = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = STThemeError, contentColor = Color(0xFF5B0009)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.History, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("确认回滚")
                        }
                    }
                }
            }
        }
    }
}

data class VectorResult(
    val keys: String,
    val similarity: Float,
    val content: String
)

data class SimulatedCheckpoint(
    val id: String,
    val name: String,
    val time: String,
    val active: Boolean,
    val tag: String
)

// ─────────────────────────────────────────────────────────────
// 23 · 主题外观与阅读 (AppearanceScreen) - Premium Swatch Live Preview
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun STAppearanceScreen(
    fontSize: Float,
    onFontSizeChanged: (Float) -> Unit,
    reduceMotion: Boolean,
    onReduceMotionChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var themePreset by remember { mutableStateOf("amber") }
    var fastMode by remember { mutableStateOf(true) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    // Swatches preview styles logic
    val swatchBg = when (themePreset) {
        "midnight" -> androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(Color(0xFF10192E), Color(0xFF080D1A))
        )
        "terminal" -> androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(Color(0xFF000000), Color(0xFF000000))
        )
        else -> androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(STThemePrimary.copy(alpha = 0.18f), STThemeBg.copy(alpha = 0.95f))
        )
    }

    val bubbleAsstColor = when (themePreset) {
        "midnight" -> Color(0xFF1B2A47)
        "terminal" -> Color(0xFF111111)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val bubbleAsstBorder = when (themePreset) {
        "terminal" -> androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF39FF14))
        else -> null
    }

    val bubbleUserColor = when (themePreset) {
        "midnight" -> Color(0xFF2E6BFF)
        "terminal" -> Color(0xFF111111)
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val bubbleUserBorder = when (themePreset) {
        "terminal" -> androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF39FF14))
        else -> null
    }

    val bubbleAsstTextColor = when (themePreset) {
        "midnight" -> Color(0xFFDCE5F5)
        "terminal" -> Color(0xFF39FF14)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val bubbleUserTextColor = when (themePreset) {
        "midnight" -> Color(0xFFFFFFFF)
        "terminal" -> Color(0xFF39FF14)
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    val previewHeight = 164.dp

    Surface(modifier = modifier.fillMaxSize(), color = STThemeBg) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                STTopHeader(
                    title = "主题外观与阅读",
                    leading = {
                        STIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                    },
                    actions = {
                        STIconButton(
                            icon = Icons.Filled.FileDownload,
                            contentDescription = "导入主题",
                            onClick = { toastMessage = "打开本地主题库导入…" }
                        )
                        IconButton(
                            onClick = { toastMessage = "当前主题偏好已成功固化" }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = "保存主题",
                                tint = STThemePrimary
                            )
                        }
                    },
                    titleBottomPadding = 4.dp
                )

                STPreviewBanner()

                // Live Preview Card (Live Typography Preview)
                PremiumCard(
                    borderColor = Color(0x1AFFFFFF)
                ) {
                    Text(
                        text = "聊天阅读排版预览 (Live Typography Preview)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.8.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = STThemePrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewHeight)
                            .clip(RoundedCornerShape(18.dp))
                            .background(swatchBg)
                            .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(18.dp))
                            .padding(12.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Assistant message bubble
                            Row(
                                modifier = Modifier.fillMaxWidth(0.85f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Mini avatar
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(STThemePrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "A",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF4A2700)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                                    color = bubbleAsstColor,
                                    border = bubbleAsstBorder,
                                    modifier = Modifier.wrapContentSize()
                                ) {
                                    Text(
                                        text = "热可可好啦。今天想加点什么？棉花糖、肉桂、还是都不加？",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = fontSize.sp,
                                            lineHeight = (fontSize * 1.4f).sp
                                        ),
                                        color = bubbleAsstTextColor,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            // User message bubble
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                                    color = bubbleUserColor,
                                    border = bubbleUserBorder,
                                    modifier = Modifier.fillMaxWidth(0.75f)
                                ) {
                                    Text(
                                        text = "加肉桂。窗边那个老位置还空着么？",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = fontSize.sp,
                                            lineHeight = (fontSize * 1.4f).sp
                                        ),
                                        color = bubbleUserTextColor,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Preset Theme Chips
                PremiumSectionHeader(title = "皮肤配色预设 (Themes Swatches)")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(
                        "amber" to "暖琥珀 (Warm Amber)",
                        "midnight" to "午夜蓝 (Midnight Blue)",
                        "terminal" to "黑客绿 (Terminal Matrix)"
                    )
                    for ((id, label) in presets) {
                        val sel = themePreset == id
                        FilterChip(
                            selected = sel,
                            onClick = {
                                themePreset = id
                                toastMessage = "已加载 " + label.substringBefore(" ") + " 样式主题"
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (sel) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(label)
                                }
                            }
                        )
                    }
                }

                // Font size slider
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("字体缩放大小", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Text(text = "${fontSize.toInt()} sp", style = MaterialTheme.typography.titleMedium, color = STThemePrimary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = fontSize,
                        onValueChange = onFontSizeChanged,
                        valueRange = 12f..20f,
                        steps = 8
                    )
                }

                HorizontalDivider(color = Color(0x0DFFFFFF), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

                PremiumSectionHeader(title = "性能与交互优化")

                STListItem(
                    headline = "减少动效渲染 (Reduce Motion)",
                    supporting = "关闭全屏转场、抽屉划过的贝塞尔缓动",
                    trailing = { Switch(checked = reduceMotion, onCheckedChange = onReduceMotionChanged) },
                    onClick = { onReduceMotionChanged(!reduceMotion) }
                )

                STListItem(
                    headline = "高能效省电模式 (Fast UI Mode)",
                    supporting = "降低背景图模糊毛玻璃滤镜运算",
                    trailing = { Switch(checked = fastMode, onCheckedChange = { fastMode = it }) },
                    onClick = { fastMode = !fastMode }
                )
            }

            AnimatedToast(
                message = toastMessage,
                onDismiss = { toastMessage = null }
            )
        }
    }
}

data class SimulatedExtension(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val sub: String,
    val active: Boolean,
    val cat: String
)

data class CascadeChipState(
    val label: String,
    val active: Boolean
)
