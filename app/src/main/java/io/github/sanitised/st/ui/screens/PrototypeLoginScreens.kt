package io.github.sanitised.st.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.StCurrentUser
import io.github.sanitised.st.api.StUserView
import io.github.sanitised.st.api.TavernCoreClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// P0 · 登录与账户分区（设计稿 screens/Login.jsx，画板 01–06）
// 接真实后端：/api/users/list|login|recover-step1|recover-step2|me|change-password。
// 单用户模式（listUsers 为空/失败）自动退回样例展示，不阻塞主流程。
// ─────────────────────────────────────────────────────────────────────────────

/** 样例账户（仅在单用户模式 / 服务未启动时展示）。 */
data class P0Account(
    val handle: String,
    val name: String,
    val admin: Boolean,
    val hasPassword: Boolean,
    val initial: String,
    val gradient: List<Long>,
    val created: String,
    val lastSeen: String,
)

val P0_SAMPLE_ACCOUNTS = listOf(
    P0Account("default-user", "User", admin = true, hasPassword = false, initial = "U",
        gradient = listOf(0xFFFFB871, 0xFF6B3B05), created = "—", lastSeen = "—"),
)

private fun formatCreated(epochMillis: Long): String =
    if (epochMillis > 0) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMillis)) else "—"

// ── 共享视觉零件 ─────────────────────────────────────────────────────────────

@Composable
private fun STMark(size: androidx.compose.ui.unit.Dp = 72.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(Color(0xFF6B3B05), Color(0xFF3A2305))))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(colors = listOf(Color(0x40FFD8A0), Color.Transparent))))
        Text(
            text = "ST", color = Color(0xFFFFDCBE), fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = (size.value * 0.42f).sp)
        )
    }
}

private enum class P0BannerTone { Neutral, Tertiary, Error }

@Composable
private fun P0Banner(text: String, icon: ImageVector, tone: P0BannerTone = P0BannerTone.Neutral, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        P0BannerTone.Neutral -> cs.surfaceContainer to cs.onSurfaceVariant
        P0BannerTone.Tertiary -> cs.tertiaryContainer to cs.onTertiaryContainer
        P0BannerTone.Error -> cs.errorContainer to cs.onErrorContainer
    }
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = fg)
    }
}

@Composable
private fun P0TextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    supporting: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
        supportingText = supporting?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        trailingIcon = when {
            password -> {
                {
                    androidx.compose.material3.IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (visible) "隐藏密码" else "显示密码", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> trailing
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        )
    )
}

@Composable
private fun LoginShell(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            content = content
        )
    }
}

@Composable
private fun LoginHeader(subtitle: String, markSize: androidx.compose.ui.unit.Dp = 72.dp) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(64.dp))
        STMark(markSize)
        Spacer(Modifier.height(20.dp))
        Text("SillyTavern", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
    }
}

// ── 01–03 登录 ───────────────────────────────────────────────────────────────

private enum class LoginStage { Select, Password, Recovery }

@Composable
fun PrototypeLoginScreen(
    status: NodeStatus,
    baseUrl: String,
    onClose: () -> Unit,
    onLoggedIn: () -> Unit,
    onOnboarding: () -> Unit,
    onShowMessage: (String) -> Unit = {},
    serverEndpoint: String = "127.0.0.1:8000",
) {
    val running = status.state == NodeState.RUNNING
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(LoginStage.Select) }
    var selected by remember { mutableStateOf<StUserView?>(null) }
    var users by remember { mutableStateOf<List<StUserView>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var multiUser by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var handleInput by remember { mutableStateOf("") }
    var manualPwd by remember { mutableStateOf("") }

    LaunchedEffect(running, baseUrl) {
        if (!running) { loading = false; multiUser = false; return@LaunchedEffect }
        loading = true
        runCatching { TavernCoreClient(baseUrl).listUsers() }
            .onSuccess { users = it; multiUser = it.isNotEmpty() }
            .onFailure { multiUser = false }
        loading = false
    }

    BackHandler(enabled = stage != LoginStage.Select) {
        stage = if (stage == LoginStage.Recovery) LoginStage.Password else LoginStage.Select
    }
    BackHandler(enabled = stage == LoginStage.Select, onBack = onClose)

    fun loginWith(handle: String, password: String) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { TavernCoreClient(baseUrl).loginUser(handle, password) }
                .onSuccess { busy = false; onLoggedIn() }
                .onFailure { busy = false; onShowMessage(it.message ?: "登录失败") }
        }
    }

    LoginShell {
        when (stage) {
            LoginStage.Select -> {
                LoginHeader("选择账户登录")
                when {
                    loading && running -> {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }
                    multiUser -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            users.forEach { u ->
                                UserRow(u, onClick = {
                                    selected = u
                                    if (u.hasPassword) stage = LoginStage.Password else loginWith(u.handle, "")
                                })
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                    else -> {
                        // 用户列表为空：可能是单用户（无需登录），也可能是 discreet-login
                        // （服务器要求认证但故意隐藏用户列表）。两种都要支持：保留 Handle 登录表单，
                        // 再给一个单用户直接进入的兜底，避免 discreet-login 下无法建立会话。
                        if (running) {
                            P0Banner(
                                "未检测到用户列表。多用户 / discreet 模式请用 Handle 登录；单用户可直接进入。",
                                icon = Icons.Filled.Keyboard, tone = P0BannerTone.Neutral,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            P0TextField(label = "Handle", value = handleInput, onValueChange = { handleInput = it }, placeholder = "用户 handle")
                            Spacer(Modifier.height(12.dp))
                            P0TextField(label = "密码", value = manualPwd, onValueChange = { manualPwd = it }, password = true)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { if (handleInput.isNotBlank()) loginWith(handleInput.trim(), manualPwd) },
                                enabled = !busy && handleInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (busy) "登录中…" else "使用 Handle 登录")
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                TextButton(onClick = onLoggedIn) { Text("以单用户身份直接进入") }
                            }
                        } else {
                            P0Banner(
                                "本地服务未启动。",
                                icon = Icons.Filled.LockOpen, tone = P0BannerTone.Tertiary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }
                }
                if (multiUser) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = { onShowMessage("使用 Handle 登录：在用户列表中选择") }) {
                            Icon(Icons.Filled.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("使用 Handle 登录")
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    "本机服务 · $serverEndpoint\n多用户模式由服务器 config.yaml 控制",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)
                )
            }
            LoginStage.Password -> {
                val u = selected
                LoginPassword(
                    user = u,
                    busy = busy,
                    onBack = { stage = LoginStage.Select },
                    onForgot = { stage = LoginStage.Recovery },
                    onLogin = { pwd -> if (u != null) loginWith(u.handle, pwd) },
                    onSwitch = { stage = LoginStage.Select }
                )
            }
            LoginStage.Recovery -> {
                LoginRecovery(
                    user = selected,
                    baseUrl = baseUrl,
                    onCancel = { stage = LoginStage.Password },
                    onReset = { code, pwd ->
                        val u = selected ?: return@LoginRecovery
                        scope.launch {
                            runCatching {
                                val client = TavernCoreClient(baseUrl)
                                client.recoverPasswordStep2(u.handle, code, pwd)
                                client.loginUser(u.handle, pwd)
                            }.onSuccess { onLoggedIn() }.onFailure { onShowMessage(it.message ?: "恢复失败") }
                        }
                    },
                    onShowMessage = onShowMessage
                )
            }
        }
    }
}

@Composable
private fun UserRow(u: StUserView, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)).clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PrototypeAvatar(u.name.ifBlank { u.handle }, size = 48.dp, imageUrl = u.avatar, gradient = prototypeGradientFor(u.handle.hashCode()))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(u.name.ifBlank { u.handle }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text("@${u.handle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            if (u.hasPassword) Icons.Filled.Lock else Icons.Filled.LockOpen,
            contentDescription = if (u.hasPassword) "已设密码" else "无密码",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (u.hasPassword) 1f else 0.35f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ColumnScope.LoginPassword(
    user: StUserView?,
    busy: Boolean,
    onBack: () -> Unit,
    onForgot: () -> Unit,
    onLogin: (String) -> Unit,
    onSwitch: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    Spacer(Modifier.height(48.dp))
    Row(Modifier.fillMaxWidth()) {
        PrototypeIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        PrototypeAvatar(user?.name?.ifBlank { user.handle } ?: "用户", size = 88.dp, imageUrl = user?.avatar, gradient = prototypeGradientFor((user?.handle ?: "").hashCode()))
        Spacer(Modifier.height(18.dp))
        Text(user?.name?.ifBlank { user.handle } ?: "用户", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text("@${user?.handle ?: ""}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
    }
    P0TextField(label = "密码", value = password, onValueChange = { password = it }, password = true)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onForgot) { Text("忘记密码？") }
    }
    Spacer(Modifier.height(12.dp))
    Button(onClick = { onLogin(password) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (busy) "登录中…" else "登录")
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = onSwitch) { Text("换个账户") }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ColumnScope.LoginRecovery(
    user: StUserView?,
    baseUrl: String,
    onCancel: () -> Unit,
    onReset: (String, String) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    // 进入时请求服务器把恢复码打印到控制台。
    LaunchedEffect(user?.handle) {
        val h = user?.handle ?: return@LaunchedEffect
        runCatching { TavernCoreClient(baseUrl).recoverPasswordStep1(h) }
            .onFailure { onShowMessage(it.message ?: "请求恢复码失败") }
    }
    Spacer(Modifier.height(48.dp))
    Row(Modifier.fillMaxWidth()) {
        PrototypeIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onCancel)
    }
    Spacer(Modifier.height(12.dp))
    Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
    Spacer(Modifier.height(14.dp))
    Text("找回密码", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(8.dp))
    Text(
        "@${user?.handle ?: ""} 的恢复码已打印到服务器控制台。在运行 SillyTavern 的终端（或 ST 核心 → 运行日志）里查看。",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(16.dp))
    P0Banner("恢复码 6 位，在运行日志中查找 “password recovery code”。", icon = Icons.Filled.Terminal, tone = P0BannerTone.Tertiary)
    Spacer(Modifier.height(16.dp))
    P0TextField(label = "恢复码", value = code, onValueChange = { code = it }, placeholder = "6 位恢复码", keyboardType = KeyboardType.Number)
    Spacer(Modifier.height(12.dp))
    P0TextField(label = "新密码", value = newPwd, onValueChange = { newPwd = it }, password = true)
    Spacer(Modifier.height(28.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("取消") }
        Button(onClick = { onReset(code, newPwd) }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("重设并登录") }
    }
    Spacer(Modifier.height(24.dp))
}

// ── 04 初次引导（保持原型：本地引导，不写后端）────────────────────────────────
@Composable
fun PrototypeOnboardingScreen(onFinish: () -> Unit, onSkip: () -> Unit) {
    BackHandler(onBack = onSkip)
    var personaName by remember { mutableStateOf("我") }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(56.dp))
            STMark(56.dp)
            Spacer(Modifier.height(24.dp))
            Text("初次见面", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Text("三步把 SillyTavern 准备好。所有设置之后都能改。", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            OnbCard(step = "1", title = "界面语言") { P0Selectish(label = "UI 语言", value = "简体中文") }
            OnbCard(step = "2", title = "你叫什么？", sub = "这会成为你的默认 Persona — 角色眼中的「你」。") {
                P0TextField(label = "名字", value = personaName, onValueChange = { personaName = it }, trailing = {
                    PrototypeAvatar(personaName.ifBlank { "我" }, size = 30.dp, gradient = listOf(0xFFFFB871, 0xFF6B3B05))
                })
            }
            OnbCard(step = "3", title = "去哪找角色？", sub = "导入 .png / .json 角色卡，或先用内置示例逛逛。") {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f).height(44.dp)) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("导入角色卡")
                    }
                    OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).height(44.dp)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("看内置示例")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("开始使用")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { TextButton(onClick = onSkip) { Text("跳过引导") } }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun OnbCard(step: String, title: String, sub: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)).padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text(step, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        if (sub != null) {
            Spacer(Modifier.height(8.dp))
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

@Composable
private fun P0Selectish(label: String, value: String) {
    OutlinedTextField(
        value = value, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(12.dp),
        trailingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant, focusedBorderColor = MaterialTheme.colorScheme.primary)
    )
}

// ── 05–06 账户资料 + 修改密码 ────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypeAccountScreen(
    status: NodeStatus,
    baseUrl: String,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onShowMessage: (String) -> Unit = {},
) {
    val running = status.state == NodeState.RUNNING
    val scope = rememberCoroutineScope()
    var showPasswordSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var user by remember { mutableStateOf<StCurrentUser?>(null) }

    LaunchedEffect(running, baseUrl) {
        if (!running) return@LaunchedEffect
        runCatching { TavernCoreClient(baseUrl).getCurrentUser() }
            .onSuccess { user = it }
            .onFailure { /* 单用户/未启用：保留样例展示 */ }
    }

    val handle = user?.handle ?: P0_SAMPLE_ACCOUNTS.first().handle
    val name = user?.name ?: P0_SAMPLE_ACCOUNTS.first().name
    val admin = user?.admin ?: true
    val created = user?.let { formatCreated(it.created) } ?: "—"

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PrototypeIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                Text("账户", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                PrototypeIconButton(Icons.AutoMirrored.Filled.Logout, "退出登录", {
                    // 先向后端注销并清本地会话 cookie，再回登录页（避免沿用旧账户会话）。
                    scope.launch {
                        if (running) runCatching { TavernCoreClient(baseUrl).logoutUser() }
                        onLogout()
                    }
                })
            }
            Row(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    PrototypeAvatar(name, size = 76.dp, imageUrl = user?.avatar, gradient = prototypeGradientFor(handle.hashCode()))
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Edit, contentDescription = "更换头像", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(15.dp)) }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text("@$handle", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (admin) AccountChip("管理员", selected = true)
                        if (!running) AccountChip("服务未启动", selected = false)
                        else if (user == null) AccountChip("单用户模式", selected = false)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountStatCard("创建于", created, Modifier.weight(1f))
                AccountStatCard("Handle", "@$handle", Modifier.weight(1f))
                AccountStatCard("数据目录", "data/$handle", Modifier.weight(1f))
            }

            PrototypeSectionHeader("资料")
            PrototypeListItem(headline = "显示名", supporting = name, leading = { AccountLead(Icons.Filled.Badge) }, trailing = { Chevron() }, divider = true, onClick = { onShowMessage("修改显示名（后续）") })
            PrototypeListItem(headline = "Handle", supporting = "@$handle · 登录与数据目录标识，不可改", leading = { AccountLead(Icons.Filled.AlternateEmail) }, divider = true)
            PrototypeListItem(headline = "修改密码", supporting = "通过恢复码或当前密码修改", leading = { AccountLead(Icons.Filled.Password) }, trailing = { Chevron() }, onClick = { if (running) showPasswordSheet = true else onShowMessage("请先启动服务") })

            PrototypeSectionHeader("数据")
            PrototypeListItem(headline = "下载账户备份", supporting = "角色、聊天、设置打包为 .zip", leading = { AccountLead(Icons.Filled.Archive) }, trailing = { Chevron() }, divider = true, onClick = { onShowMessage("账户备份：/api/users/backup（在桌面端下载）") })
            PrototypeListItem(
                headline = "保存设置快照",
                supporting = "把当前设置存一份快照",
                leading = { AccountLead(Icons.Filled.SettingsBackupRestore) },
                trailing = { Chevron() },
                divider = true,
                onClick = {
                    if (!running) { onShowMessage("请先启动服务"); return@PrototypeListItem }
                    scope.launch {
                        runCatching { TavernCoreClient(baseUrl).makeSettingsSnapshot() }
                            .onSuccess { onShowMessage("已保存设置快照") }
                            .onFailure { onShowMessage(it.message ?: "快照失败") }
                    }
                }
            )
            PrototypeListItem(headline = "重置设置", supporting = "恢复出厂设置，角色与聊天保留", leading = { AccountLead(Icons.Filled.RestartAlt, tint = MaterialTheme.colorScheme.error) }, trailing = { Chevron() }, onClick = { onShowMessage("重置设置：/api/users/reset-settings（需二次确认，后续）") })
        }
    }

    if (showPasswordSheet) {
        ModalBottomSheet(onDismissRequest = { showPasswordSheet = false }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            ChangePasswordSheet(
                onCancel = { showPasswordSheet = false },
                onSave = { old, new ->
                    scope.launch {
                        runCatching { TavernCoreClient(baseUrl).changeUserPassword(handle, old, new) }
                            .onSuccess { showPasswordSheet = false; onShowMessage("密码已更新") }
                            .onFailure { onShowMessage(it.message ?: "修改失败") }
                    }
                }
            )
        }
    }
}

@Composable
private fun ChangePasswordSheet(onCancel: () -> Unit, onSave: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("修改密码", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text("修改后其它已登录设备需要重新登录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        P0TextField(label = "当前密码", value = current, onValueChange = { current = it }, password = true)
        Spacer(Modifier.height(12.dp))
        P0TextField(label = "新密码", value = next, onValueChange = { next = it }, password = true, supporting = "至少 8 位；留空表示移除密码")
        Spacer(Modifier.height(12.dp))
        P0TextField(label = "确认新密码", value = confirm, onValueChange = { confirm = it }, password = true, supporting = if (confirm.isNotEmpty() && confirm != next) "两次输入不一致" else null)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(
                onClick = { if (next == confirm) onSave(current, next) },
                enabled = next.isNotEmpty() && next == confirm,
                modifier = Modifier.weight(2f).height(46.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors()
            ) { Text("保存") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AccountChip(label: String, selected: Boolean) {
    val cs = MaterialTheme.colorScheme
    val bg = if (selected) cs.secondaryContainer else Color.Transparent
    val fg = if (selected) cs.onSecondaryContainer else cs.onSurfaceVariant
    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).then(if (selected) Modifier else Modifier.border(1.dp, cs.outlineVariant, RoundedCornerShape(8.dp))).padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(label, style = MaterialTheme.typography.labelMedium, color = fg) }
}

@Composable
private fun AccountStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AccountLead(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
