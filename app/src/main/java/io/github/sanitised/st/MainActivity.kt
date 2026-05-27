package io.github.sanitised.st

import android.Manifest
import android.graphics.Color
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.sanitised.st.ui.navigation.BottomNavItem
import io.github.sanitised.st.ui.navigation.DrawerNavItem
import io.github.sanitised.st.ui.navigation.STNavigationScaffold
import io.github.sanitised.st.ui.navigation.STRoutes
import io.github.sanitised.st.ui.prototype.PrototypeAvatar
import io.github.sanitised.st.ui.prototype.PrototypeStatusDot
import io.github.sanitised.st.ui.prototype.PrototypeAISettingsScreen
import io.github.sanitised.st.ui.prototype.PrototypeApiConnectionScreen
import io.github.sanitised.st.ui.prototype.PrototypeCharacterCreateScreen
import io.github.sanitised.st.ui.prototype.PrototypeCharacterLibraryScreen
import io.github.sanitised.st.ui.prototype.PrototypeCharacterProfileScreen
import io.github.sanitised.st.ui.prototype.PrototypeChatListScreen
import io.github.sanitised.st.ui.prototype.PrototypeDrawerState
import io.github.sanitised.st.ui.prototype.PrototypeMeScreen
import io.github.sanitised.st.ui.prototype.PrototypeMemoryScreen
import io.github.sanitised.st.ui.prototype.PrototypePersonaScreen
import io.github.sanitised.st.ui.prototype.PrototypeStCoreScreen
import io.github.sanitised.st.ui.prototype.PrototypeWorldInfoScreen
import io.github.sanitised.st.ui.screens.rememberLocalTavernLibrarySnapshot
import io.github.sanitised.st.ui.components.STConfirmDialog
import io.github.sanitised.st.ui.components.STDialogButtonStyle
import io.github.sanitised.st.ui.theme.STAppTheme
import io.github.sanitised.st.chat.ChatRuntimeBridge
import io.github.sanitised.st.chat.ChatStore
import io.github.sanitised.st.chat.NativeChatScreen
import io.github.sanitised.st.ui.webview.ChatWebViewScreen
import io.github.sanitised.st.ui.webview.WebViewTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val bottomNavItems = listOf(
    BottomNavItem(STRoutes.HOME, "对话", Icons.AutoMirrored.Filled.Chat),
    BottomNavItem(STRoutes.CHARACTERS, "角色", Icons.Filled.Groups),
    BottomNavItem(STRoutes.WORLD_INFO, "世界书", Icons.Filled.Book),
    BottomNavItem(STRoutes.SETTINGS, "我的", Icons.Filled.AccountCircle)
)

private val drawerNavItems = listOf(
    DrawerNavItem(STRoutes.HOME, "对话", Icons.AutoMirrored.Filled.Chat, badgeText = "23"),
    DrawerNavItem(STRoutes.CHARACTERS, "角色库", Icons.Filled.Groups, badgeText = "247"),
    DrawerNavItem(STRoutes.CHARACTERS, "群聊", Icons.Filled.GroupAdd, badgeText = "2"),
    DrawerNavItem(STRoutes.PERSONA, "扮演者", Icons.Filled.Face, badgeText = "4"),
    DrawerNavItem(STRoutes.WORLD_INFO, "世界书", Icons.Filled.Book, badgeText = "4"),
    DrawerNavItem(STRoutes.CHAT_BACKUPS, "记忆与回顾", Icons.Filled.CloudSync),
    DrawerNavItem(STRoutes.PRESETS, "作者注 & CFG", Icons.Filled.History),
    DrawerNavItem(STRoutes.PRESETS, "AI 采样设置", Icons.Filled.Tune, isPrimaryGroup = false),
    DrawerNavItem(STRoutes.CONNECTIONS, "API 连接", Icons.Filled.SettingsEthernet, supportingText = "3 个已连接", isPrimaryGroup = false),
    DrawerNavItem(STRoutes.PRESETS, "扩展", Icons.Filled.Extension, supportingText = "6 个", isPrimaryGroup = false),
    DrawerNavItem(STRoutes.SETTINGS, "主题外观", Icons.Filled.Palette, isPrimaryGroup = false),
    DrawerNavItem(STRoutes.MANAGE_ST, "ST 内核", Icons.Filled.Memory, supportingText = "运行中", isPrimaryGroup = false),
    DrawerNavItem(STRoutes.SETTINGS, "设置", Icons.Filled.Settings, isPrimaryGroup = false),
    DrawerNavItem(STRoutes.LEGAL, "帮助 & 文档", Icons.Filled.Help, isPrimaryGroup = false),
    DrawerNavItem(STRoutes.SETTINGS, "退出登录", Icons.Filled.Logout, isPrimaryGroup = false)
)

@Composable
private fun STAppDrawerHeader(
    stLabel: String,
    nodeLabel: String,
    status: NodeStatus
) {
    val colors = MaterialTheme.colorScheme
    val drawerState = PrototypeDrawerState.from(
        status = status,
        stLabel = stLabel,
        nodeLabel = nodeLabel
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrototypeAvatar(
                    label = "我",
                    size = 56.dp,
                    ringColor = colors.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "当前扮演者",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = drawerState.personaName,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface
                    )
                }
                Icon(
                    imageVector = Icons.Filled.SwapHoriz,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant
                )
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = MaterialTheme.shapes.large,
                color = colors.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = MaterialTheme.shapes.small,
                        color = colors.tertiaryContainer,
                        contentColor = colors.onTertiaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "C",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = drawerState.connectionEyebrow,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant
                        )
                        Text(
                            text = drawerState.connectionLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface,
                            maxLines = 1
                        )
                    }
                    PrototypeStatusDot(color = if (drawerState.connected) colors.tertiary else colors.outline)
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    private val nodeServiceState = mutableStateOf<NodeService?>(null)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? NodeService.LocalBinder
            nodeServiceState.value = binder?.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            nodeServiceState.value = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val versionLabel = runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            info.versionName ?: getString(R.string.unknown_short)
        }.getOrElse { getString(R.string.unknown) }
        val bundledInfo = NodePayload(this).readManifestInfo()
        val stLabel = bundledInfo?.let {
            when {
                !it.stVersion.isNullOrBlank() -> getString(R.string.sillytavern_label, it.stVersion)
                !it.stCommit.isNullOrBlank() -> getString(R.string.sillytavern_label, it.stCommit)
                else -> getString(R.string.sillytavern_unknown)
            }
        } ?: getString(R.string.sillytavern_unknown)
        val nodeLabel = bundledInfo?.let {
            val nodeValue = when {
                !it.nodeTag.isNullOrBlank() -> it.nodeTag
                !it.nodeVersion.isNullOrBlank() -> it.nodeVersion
                !it.nodeCommit.isNullOrBlank() -> it.nodeCommit
                else -> null
            }
            if (nodeValue.isNullOrBlank()) getString(R.string.node_unknown) else getString(R.string.node_label, nodeValue)
        } ?: getString(R.string.node_unknown)
        val symlinkSupported = isSymlinkSupported()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            val bottomBarSelectedRoute = when (currentRoute) {
                STRoutes.CHARACTER_NEW,
                STRoutes.CHARACTER_DETAIL,
                STRoutes.CHARACTER_EDIT -> STRoutes.CHARACTERS
                STRoutes.WORLD_INFO -> STRoutes.WORLD_INFO
                STRoutes.PERSONA,
                STRoutes.PRESETS,
                STRoutes.CONNECTIONS,
                STRoutes.CHAT_BACKUPS -> STRoutes.SETTINGS
                STRoutes.LOGS,
                STRoutes.CONFIG,
                STRoutes.LEGAL,
                STRoutes.LICENSE,
                STRoutes.MANAGE_ST -> STRoutes.SETTINGS
                else -> currentRoute
            }
            val appPaths = remember { AppPaths(this@MainActivity) }

            val legalDocs = remember {
                listOf(
                    LegalDoc(
                        title = getString(R.string.legal_doc_app_license_title),
                        assetPath = "legal/sillytavern_AGPL-3.0.txt",
                        description = getString(R.string.legal_doc_app_license_description)
                    ),
                    LegalDoc(
                        title = getString(R.string.legal_doc_node_license_title),
                        assetPath = "legal/node_MIT.txt",
                        description = getString(R.string.legal_doc_node_license_description)
                    ),
                    LegalDoc(
                        title = getString(R.string.legal_doc_android_license_title),
                        assetPath = "legal/apache-2.0.txt",
                    ),
                    LegalDoc(
                        title = getString(R.string.legal_doc_st_dependencies_title),
                        assetPath = "legal/sillytavern_package-lock.json"
                    )
                )
            }
            val statusState = remember { mutableStateOf(NodeStatus(NodeState.STOPPED, "Idle")) }
            val autoOpenBrowserTriggeredForCurrentRun = rememberSaveable { mutableStateOf(false) }
            val stdoutState = remember { mutableStateOf("") }
            val stderrState = remember { mutableStateOf("") }
            val serviceState = remember { mutableStateOf("") }
            val pendingDialogState = rememberSaveable(stateSaver = pendingDialogStateSaver()) {
                mutableStateOf<PendingDialog?>(null)
            }
            val notificationGrantedState = remember { mutableStateOf(isNotificationPermissionGranted()) }
            val notificationAutoPromptAttempted = rememberSaveable { mutableStateOf(false) }
            val batteryUnrestrictedState = remember { mutableStateOf(isBatteryUnrestricted()) }
            val lifecycleOwner = LocalLifecycleOwner.current
            val scope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }
            val listener = remember {
                object : NodeStatusListener {
                    override fun onStatus(status: NodeStatus) {
                        scope.launch(Dispatchers.Main) {
                            val previousState = statusState.value.state
                            if (previousState == NodeState.RUNNING && status.state != NodeState.RUNNING) {
                                autoOpenBrowserTriggeredForCurrentRun.value = false
                            }
                            statusState.value = status
                        }
                    }
                }
            }
            val librarySnapshot by rememberLocalTavernLibrarySnapshot(
                dataRoot = appPaths.dataDir,
                refreshKey = statusState.value.state
            )

            val service = nodeServiceState.value
            DisposableEffect(service) {
                viewModel.nodeService = service
                if (service != null) {
                    service.registerListener(listener)
                }
                onDispose {
                    service?.unregisterListener(listener)
                }
            }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        notificationGrantedState.value = isNotificationPermissionGranted()
                        batteryUnrestrictedState.value = isBatteryUnrestricted()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(currentRoute) {
                if (currentRoute != STRoutes.LOGS) return@LaunchedEffect
                val paths = appPaths
                while (true) {
                    val logsDir = paths.logsDir
                    val stdoutFile = File(logsDir, "node_stdout.log")
                    val stderrFile = File(logsDir, "node_stderr.log")
                    val serviceFile = File(logsDir, "service.log")
                    stdoutState.value = readLogTail(stdoutFile, 16 * 1024)
                    stderrState.value = readLogTail(stderrFile, 16 * 1024)
                    serviceState.value = readLogTail(serviceFile, 16 * 1024)
                    delay(1000)
                }
            }
            LaunchedEffect(Unit) {
                viewModel.maybeAutoCheckForUpdates()
            }
            LaunchedEffect(viewModel) {
                viewModel.snackbarMessages.collectLatest { message ->
                    snackbarHostState.showSnackbar(message)
                }
            }
            LaunchedEffect(notificationGrantedState.value, notificationAutoPromptAttempted.value) {
                if (!notificationGrantedState.value && !notificationAutoPromptAttempted.value) {
                    notificationAutoPromptAttempted.value = true
                    maybeRequestNotificationPermission()
                }
            }
            val showAutoCheckOptInPrompt = viewModel.shouldShowAutoCheckOptInPrompt()
            val showBatteryPrompt = viewModel.shouldShowBatteryPrompt(
                isBatteryUnrestricted = batteryUnrestrictedState.value
            )
            val showUpdatePrompt = viewModel.shouldShowUpdatePrompt()
            val isUpdateReadyToInstall = viewModel.isAvailableUpdateDownloaded()
            val systemInDarkTheme = isSystemInDarkTheme()
            val themeMode by viewModel.themeMode
            val themeColorSource by viewModel.themeColorSource
            val useDarkTheme = themeMode.shouldUseDarkTheme(systemInDarkTheme)
            val currentStLabel = if (viewModel.isCustomInstalled.value) {
                val customLabel = viewModel.customInstallLabel.value
                if (customLabel.isNullOrBlank()) {
                    getString(R.string.sillytavern_custom_version)
                } else {
                    getString(R.string.sillytavern_custom_with_label, customLabel)
                }
            } else {
                stLabel
            }
            SideEffect {
                window.statusBarColor = Color.TRANSPARENT
                window.navigationBarColor = Color.TRANSPARENT
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !useDarkTheme
                    isAppearanceLightNavigationBars = !useDarkTheme
                }
            }

            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/gzip")
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                viewModel.export(uri)
            }
            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                pendingDialogState.value = PendingDialog.CheckingImport(uri)
                scope.launch {
                    val previewResult = withContext(Dispatchers.IO) {
                        NodeBackup.inspectImportUri(this@MainActivity, uri)
                    }
                    val previewText = previewResult.fold(
                        onSuccess = { preview -> backupImportPreviewText(preview) },
                        onFailure = { error ->
                            getString(
                                R.string.dialog_import_invalid_body,
                                error.message ?: getString(R.string.unknown_error)
                            )
                        }
                    )
                    pendingDialogState.value = PendingDialog.ConfirmImport(
                        uri = uri,
                        previewText = previewText,
                        canImport = previewResult.isSuccess
                    )
                }
            }
            val diagnosticExportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/zip")
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                viewModel.exportDiagnostics(
                    uri = uri,
                    appVersion = versionLabel,
                    stLabel = currentStLabel,
                    nodeLabel = nodeLabel,
                    status = statusState.value
                )
            }
            val customZipLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                viewModel.installCustomZip(uri)
            }
            val triggerExport: () -> Unit = {
                val stamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                exportLauncher.launch("sillytavern-backup-$stamp.tar.gz")
            }
            val triggerImport: () -> Unit = {
                importLauncher.launch(
                    arrayOf(
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/gzip",
                        "application/x-gzip",
                        "application/octet-stream",
                        "application/x-tar"
                    )
                )
            }
            val triggerDiagnosticExport: () -> Unit = {
                val stamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                diagnosticExportLauncher.launch("sillytavern-diagnostics-$stamp.zip")
            }
            val chatStore = remember { ChatStore() }
            val chatBridge = remember { ChatRuntimeBridge(chatStore) }
            var pendingWebViewTarget by remember { mutableStateOf<WebViewTarget>(WebViewTarget.CHAT) }
            val navigateMainTab: (String) -> Unit = { route ->
                if (route == STRoutes.CHAT) {
                    pendingWebViewTarget = WebViewTarget.CHAT
                }
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            val openCharacterChatFromCharacterManagement: (String, String?) -> Unit = { avatar, chatFile ->
                pendingWebViewTarget = WebViewTarget.CharacterChat(avatar, chatFile)
                if (chatStore.runtimeState == io.github.sanitised.st.chat.RuntimeState.READY) {
                    chatBridge.openCharacter(avatar, chatFile)
                }
                navController.navigate(STRoutes.CHAT) {
                    launchSingleTop = true
                }
            }

            STAppTheme(useDarkTheme = useDarkTheme, colorSource = themeColorSource) {
                STNavigationScaffold(
                    items = bottomNavItems,
                    drawerItems = drawerNavItems,
                    currentRoute = bottomBarSelectedRoute,
                    onNavigate = navigateMainTab,
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    },
                    drawerHeader = {
                        STAppDrawerHeader(
                            stLabel = currentStLabel,
                            nodeLabel = nodeLabel,
                            status = statusState.value
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        NavHost(
                            navController = navController,
                            startDestination = STRoutes.HOME
                        ) {
                            composable(STRoutes.HOME) {
                                // Replaces the old STAndroidApp dashboard with the prototype ChatListScreen.
                                PrototypeChatListScreen(
                                    status = statusState.value,
                                    onStart = { startNode() },
                                    onStop = { stopNode() },
                                    stLabel = if (viewModel.isCustomInstalled.value) {
                                        val customLabel = viewModel.customInstallLabel.value
                                        if (customLabel.isNullOrBlank()) {
                                            getString(R.string.sillytavern_custom_version)
                                        } else {
                                            getString(R.string.sillytavern_custom_with_label, customLabel)
                                        }
                                    } else stLabel,
                                    nodeLabel = nodeLabel,
                                    recentChats = librarySnapshot.recentChats,
                                    onOpenChat = { chat ->
                                        if (chat.id.endsWith("/demo")) {
                                            navigateMainTab(STRoutes.CHAT)
                                        } else {
                                            openCharacterChatFromCharacterManagement(chat.characterId, chat.chatFile)
                                        }
                                    },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.CHAT) {
                                NativeChatScreen(
                                    status = statusState.value,
                                    target = pendingWebViewTarget,
                                    themeMode = themeMode,
                                    store = chatStore,
                                    bridge = chatBridge,
                                    onStartService = { startNode() },
                                    onShowLogs = { navController.navigate(STRoutes.LOGS) },
                                    onBackToHome = { if (!navController.popBackStack()) navigateMainTab(STRoutes.HOME) }
                                )
                            }

                            composable(STRoutes.CHARACTERS) {
                                // CharacterListScreen remains as the functional fallback; the visible route uses the prototype CharLibScreen.
                                PrototypeCharacterLibraryScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onStartService = { startNode() },
                                    onOpenCharacter = { avatar ->
                                        navController.navigate(STRoutes.characterDetail(avatar))
                                    },
                                    onOpenChat = { avatar ->
                                        openCharacterChatFromCharacterManagement(avatar, null)
                                    },
                                    onCreateCharacter = {
                                        navController.navigate(STRoutes.CHARACTER_NEW)
                                    },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.CHARACTER_NEW) {
                                // CharacterEditScreen remains available for advanced edit routes; creation now uses the prototype CharEdit surface.
                                PrototypeCharacterCreateScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onStartService = { startNode() },
                                    onBack = { navController.popBackStack() },
                                    onSaved = { avatar ->
                                        navController.navigate(STRoutes.characterDetail(avatar)) {
                                            popUpTo(STRoutes.CHARACTERS)
                                        }
                                    },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(
                                route = STRoutes.CHARACTER_DETAIL,
                                arguments = listOf(navArgument("avatar") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val avatar = backStackEntry.arguments?.getString("avatar")?.let { Uri.decode(it) }
                                if (avatar != null) {
                                    // CharacterDetailScreen remains compiled for advanced management; this route shows the prototype CharEdit layout.
                                    PrototypeCharacterProfileScreen(
                                        status = statusState.value,
                                        baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                        avatar = avatar,
                                        onStartService = { startNode() },
                                        onBack = { navController.popBackStack() },
                                        onOpenChat = { chatFile ->
                                            openCharacterChatFromCharacterManagement(avatar, chatFile)
                                        },
                                        onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                    )
                                }
                            }

                            composable(
                                route = STRoutes.CHARACTER_EDIT,
                                arguments = listOf(navArgument("avatar") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val avatar = backStackEntry.arguments?.getString("avatar")?.let { Uri.decode(it) }
                                if (avatar != null) {
                                    PrototypeCharacterProfileScreen(
                                        status = statusState.value,
                                        baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                        avatar = avatar,
                                        onStartService = { startNode() },
                                        onBack = { navController.popBackStack() },
                                        onOpenChat = { chatFile ->
                                            openCharacterChatFromCharacterManagement(avatar, chatFile)
                                        },
                                        onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                    )
                                }
                            }

                            composable(STRoutes.WORLD_INFO) {
                                BackHandler { navController.popBackStack() }
                                PrototypeWorldInfoScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onStartService = { startNode() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.PERSONA) {
                                BackHandler { navController.popBackStack() }
                                PrototypePersonaScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.PRESETS) {
                                BackHandler { navController.popBackStack() }
                                PrototypeAISettingsScreen(
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.CONNECTIONS) {
                                BackHandler { navController.popBackStack() }
                                PrototypeApiConnectionScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.CHAT_BACKUPS) {
                                BackHandler { navController.popBackStack() }
                                PrototypeMemoryScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.SETTINGS) {
                                PrototypeMeScreen(
                                    autoCheckEnabled = viewModel.autoCheckForUpdates.value,
                                    onAutoCheckChanged = { enabled -> viewModel.setAutoCheckForUpdates(enabled) },
                                    autoOpenBrowserEnabled = viewModel.autoOpenBrowserWhenReady.value,
                                    onAutoOpenBrowserChanged = { enabled -> viewModel.setAutoOpenBrowserWhenReady(enabled) },
                                    themeMode = themeMode,
                                    onThemeModeChanged = { mode -> viewModel.setThemeMode(mode) },
                                    colorSource = themeColorSource,
                                    onColorSourceChanged = { source -> viewModel.setThemeColorSource(source) },
                                    isBatteryUnrestricted = batteryUnrestrictedState.value,
                                    onOpenBatterySettings = { openBatteryOptimizationSettings() },
                                    channel = viewModel.updateChannel.value,
                                    onChannelChanged = { channel -> viewModel.setUpdateChannel(channel) },
                                    onCheckNow = { viewModel.checkForUpdates("manual") },
                                    isChecking = viewModel.isCheckingForUpdates.value,
                                    onOpenWorldInfo = { navController.navigate(STRoutes.WORLD_INFO) },
                                    onOpenPersona = { navController.navigate(STRoutes.PERSONA) },
                                    onOpenPresets = { navController.navigate(STRoutes.PRESETS) },
                                    onOpenConnections = { navController.navigate(STRoutes.CONNECTIONS) },
                                    onOpenChatBackups = { navController.navigate(STRoutes.CHAT_BACKUPS) },
                                    onOpenLogs = { navController.navigate(STRoutes.LOGS) },
                                    onOpenConfig = { navController.navigate(STRoutes.CONFIG) },
                                    onOpenManageSt = { navController.navigate(STRoutes.MANAGE_ST) },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.LOGS) {
                                BackHandler { navController.popBackStack() }
                                LogsScreen(
                                    onBack = { navController.popBackStack() },
                                    onExportDiagnostics = triggerDiagnosticExport,
                                    stdoutLog = stdoutState.value,
                                    stderrLog = stderrState.value,
                                    serviceLog = serviceState.value
                                )
                            }

                            composable(STRoutes.CONFIG) {
                                ConfigScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenDocs = { openConfigDocs() },
                                    canEdit = statusState.value.state == NodeState.STOPPED || statusState.value.state == NodeState.ERROR,
                                    configFile = appPaths.configFile,
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.LEGAL) {
                                BackHandler { navController.popBackStack() }
                                LegalScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenUrl = { url -> openUrl(url) },
                                    legalDocs = legalDocs,
                                    onOpenLicense = { doc ->
                                        navController.navigate(
                                            STRoutes.LICENSE.replace(
                                                "{assetPath}",
                                                Uri.encode(doc.assetPath)
                                            )
                                        )
                                    }
                                )
                            }

                            composable(STRoutes.LICENSE) { backStackEntry ->
                                val assetPath = backStackEntry.arguments?.getString("assetPath")
                                val doc = assetPath?.let { path ->
                                    val decoded = Uri.decode(path)
                                    legalDocs.firstOrNull { it.assetPath == decoded }
                                }
                                if (doc != null) {
                                    BackHandler { navController.popBackStack() }
                                    LicenseTextScreen(
                                        onBack = { navController.popBackStack() },
                                        doc = doc
                                    )
                                }
                            }

                            composable(STRoutes.MANAGE_ST) {
                                BackHandler { navController.popBackStack() }
                                PrototypeStCoreScreen(
                                    onBack = { navController.popBackStack() },
                                    status = statusState.value,
                                    stLabel = currentStLabel,
                                    nodeLabel = nodeLabel,
                                    isCustomInstalled = viewModel.isCustomInstalled.value,
                                    customInstalledLabel = viewModel.customInstallLabel.value,
                                    busyMessage = viewModel.busyMessage,
                                    onStartService = { startNode() },
                                    onStopService = { stopNode() },
                                    onOpenBrowser = { openSillyTavernInBrowser(statusState.value.port) },
                                    onShowLogs = { navController.navigate(STRoutes.LOGS) },
                                    onExport = triggerExport,
                                    onImport = triggerImport,
                                    settingsSnapshots = viewModel.settingsSnapshots.value,
                                    settingsSnapshotsLoading = viewModel.settingsSnapshotsLoading.value,
                                    settingsSnapshotMessage = viewModel.settingsSnapshotMessage.value,
                                    onRefreshSettingsSnapshots = {
                                        viewModel.refreshSettingsSnapshots(statusState.value.port)
                                    },
                                    onCreateSettingsSnapshot = {
                                        viewModel.createSettingsSnapshot(statusState.value.port)
                                    },
                                    onRestoreSettingsSnapshot = { name ->
                                        pendingDialogState.value = PendingDialog.RestoreSettingsSnapshot(name)
                                    },
                                    showBackupOperationCard = viewModel.backupOperationCard.value.visible,
                                    backupOperationTitle = viewModel.backupOperationCard.value.title,
                                    backupOperationDetails = viewModel.backupOperationCard.value.details,
                                    backupOperationProgressPercent = viewModel.backupOperationCard.value.progressPercent,
                                    backupOperationAnchor = viewModel.backupOperationCardAnchor.value,
                                    showCustomOperationCard = viewModel.customOperationCard.value.visible,
                                    customOperationTitle = viewModel.customOperationCard.value.title,
                                    customOperationDetails = viewModel.customOperationCard.value.details,
                                    customOperationProgressPercent = viewModel.customOperationCard.value.progressPercent,
                                    customOperationCancelable = viewModel.customOperationCard.value.cancelable,
                                    customOperationAnchor = viewModel.customOperationCardAnchor.value,
                                    onCancelCustomOperation = { viewModel.cancelCustomSourceDownload() },
                                    onLoadCustomZip = {
                                        customZipLauncher.launch(
                                            arrayOf(
                                                "application/zip",
                                                "application/x-zip-compressed",
                                                "application/octet-stream"
                                            )
                                        )
                                    },
                                    onResetToDefault = { pendingDialogState.value = PendingDialog.ResetToDefault },
                                    onRemoveUserData = { pendingDialogState.value = PendingDialog.RemoveUserData },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }
                        }
                    }

                    // Dialogs are rendered above the NavHost
                    when (val dialog = pendingDialogState.value) {
                        PendingDialog.ResetToDefault -> {
                            STConfirmDialog(
                                title = getString(R.string.dialog_reset_title),
                                confirmLabel = getString(R.string.reset),
                                onConfirm = {
                                    pendingDialogState.value = null
                                    viewModel.resetToDefault()
                                },
                                onDismiss = { pendingDialogState.value = null },
                                body = { Text(text = getString(R.string.dialog_reset_body)) },
                                buttonStyle = STDialogButtonStyle.FILLED
                            )
                        }

                        PendingDialog.RemoveUserData -> {
                            STConfirmDialog(
                                title = getString(R.string.dialog_remove_data_title),
                                confirmLabel = getString(R.string.remove),
                                onConfirm = {
                                    pendingDialogState.value = null
                                    viewModel.removeUserData()
                                },
                                onDismiss = { pendingDialogState.value = null },
                                body = { Text(text = getString(R.string.dialog_remove_data_body)) },
                                buttonStyle = STDialogButtonStyle.FILLED
                            )
                        }

                        is PendingDialog.RestoreSettingsSnapshot -> {
                            STConfirmDialog(
                                title = getString(R.string.settings_snapshot_restore_title),
                                confirmLabel = getString(R.string.settings_snapshot_restore),
                                onConfirm = {
                                    val snapshotName = dialog.name
                                    pendingDialogState.value = null
                                    viewModel.restoreSettingsSnapshot(statusState.value.port, snapshotName)
                                },
                                onDismiss = { pendingDialogState.value = null },
                                body = {
                                    Text(text = getString(R.string.settings_snapshot_restore_body, dialog.name))
                                },
                                buttonStyle = STDialogButtonStyle.FILLED
                            )
                        }

                        is PendingDialog.CheckingImport -> {
                            STConfirmDialog(
                                title = getString(R.string.dialog_import_title),
                                confirmLabel = getString(R.string.import_action),
                                onConfirm = {},
                                onDismiss = { pendingDialogState.value = null },
                                body = { Text(text = getString(R.string.dialog_import_checking_body)) },
                                confirmEnabled = false,
                                buttonStyle = STDialogButtonStyle.FILLED
                            )
                        }

                        is PendingDialog.ConfirmImport -> {
                            STConfirmDialog(
                                title = getString(R.string.dialog_import_title),
                                confirmLabel = getString(R.string.import_action),
                                onConfirm = {
                                    val importUri = dialog.uri
                                    pendingDialogState.value = null
                                    viewModel.import(importUri)
                                },
                                onDismiss = { pendingDialogState.value = null },
                                body = { Text(text = dialog.previewText) },
                                confirmEnabled = dialog.canImport,
                                buttonStyle = STDialogButtonStyle.FILLED
                            )
                        }

                        null -> Unit
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, NodeService::class.java), serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        unbindService(serviceConnection)
    }

    private fun startNode() {
        maybeRequestNotificationPermission()
        val port = readConfiguredPort()
        val intent = Intent(this, NodeService::class.java).apply {
            action = NodeService.ACTION_START
            putExtra(NodeService.EXTRA_PORT, port)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopNode() {
        val intent = Intent(this, NodeService::class.java).apply { action = NodeService.ACTION_STOP }
        startService(intent)
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    private fun openBatteryOptimizationSettings() {
        val packageUri = Uri.fromParts("package", packageName, null)
        val powerManager = getSystemService(PowerManager::class.java)
        val isIgnoringOptimizations = powerManager?.isIgnoringBatteryOptimizations(packageName) == true
        val intentCandidates = listOf(
            Intent("android.settings.APP_BATTERY_SETTINGS").apply {
                data = packageUri
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra("android.intent.extra.PACKAGE_NAME", packageName)
                putExtra("package_name", packageName)
            },
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
                .takeUnless { isIgnoringOptimizations },
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        ).filterNotNull()

        val intent = intentCandidates.firstOrNull { candidate ->
            candidate.resolveActivity(packageManager) != null
        } ?: Intent(Settings.ACTION_SETTINGS)

        runCatching { startActivity(intent) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }

    private fun openConfigDocs() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.sillytavern.app/administration/config-yaml/"))
        startActivity(intent)
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun openSillyTavernInBrowser(port: Int) {
        openUrl(SillyTavernUrl.localWebUrl(port))
    }

    private fun readConfiguredPort(): Int {
        val configFile = AppPaths(this).configFile
        if (!configFile.exists()) return DEFAULT_PORT
        return try {
            ConfigFormTools.readStartupPort(configFile.readText(Charsets.UTF_8)) ?: DEFAULT_PORT
        } catch (_: Exception) {
            DEFAULT_PORT
        }
    }

    private fun isNotificationPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val permission = Manifest.permission.POST_NOTIFICATIONS
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(permission)
        }
    }

    private fun isBatteryUnrestricted(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isSymlinkSupported(): Boolean {
        val dir = File(cacheDir, "symlink_check")
        val target = File(dir, "target")
        val link = File(dir, "link")
        return try {
            dir.mkdirs()
            if (target.exists()) target.delete()
            if (link.exists()) link.delete()
            target.writeText("x")
            Files.createSymbolicLink(link.toPath(), target.toPath())
            Files.isSymbolicLink(link.toPath())
        } catch (_: Exception) {
            false
        } finally {
            try {
                link.delete()
            } catch (_: Exception) {
            }
            try {
                target.delete()
            } catch (_: Exception) {
            }
            try {
                dir.delete()
            } catch (_: Exception) {
            }
        }
    }
}

private fun MainActivity.backupImportPreviewText(preview: BackupImportPreview): String {
    val kindLabel = when (preview.kind) {
        BackupImportKind.APP_BACKUP -> getString(R.string.backup_precheck_kind_app)
        BackupImportKind.ST_UI_USER_BACKUP -> getString(R.string.backup_precheck_kind_ui)
    }
    val configLine = if (preview.hasConfig) {
        getString(R.string.backup_precheck_config_present)
    } else {
        getString(R.string.backup_precheck_config_missing)
    }
    val manifestLine = preview.manifest?.let { manifest ->
        getString(
            R.string.backup_precheck_manifest,
            manifest.appVersion.ifBlank { getString(R.string.unknown_short) },
            manifest.stCommit ?: getString(R.string.unknown_short)
        )
    }
    val coverageLines = preview.coverage.joinToString(separator = "\n") { item ->
        val label = backupCoverageLabel(item.path)
        when (item.status) {
            BackupCoverageStatus.PRESENT ->
                getString(R.string.backup_precheck_item_present, label, item.count)
            BackupCoverageStatus.MISSING ->
                getString(R.string.backup_precheck_item_missing, label)
        }
    }
    val warningLines = preview.warningMessages.takeIf { it.isNotEmpty() }?.joinToString(separator = "\n") { warning ->
        val localized = if (warning.contains("secrets.json")) {
            getString(R.string.backup_precheck_warning_secrets)
        } else {
            warning
        }
        getString(R.string.backup_precheck_warning_item, localized)
    }

    return buildString {
        appendLine(getString(R.string.dialog_import_body))
        appendLine(getString(R.string.backup_precheck_snapshot_advice))
        appendLine()
        appendLine(getString(R.string.backup_precheck_type, kindLabel))
        appendLine(getString(R.string.backup_precheck_user, preview.userHandle))
        appendLine(configLine)
        if (manifestLine != null) appendLine(manifestLine)
        appendLine()
        appendLine(getString(R.string.backup_precheck_coverage))
        appendLine(coverageLines)
        if (warningLines != null) {
            appendLine()
            appendLine(getString(R.string.backup_precheck_warnings))
            append(warningLines)
        }
    }.trim()
}

private fun MainActivity.backupCoverageLabel(path: String): String {
    return when (path) {
        "settings.json" -> getString(R.string.backup_precheck_path_settings)
        "characters" -> getString(R.string.backup_precheck_path_characters)
        "chats" -> getString(R.string.backup_precheck_path_chats)
        "worlds" -> getString(R.string.backup_precheck_path_worlds)
        "groups" -> getString(R.string.backup_precheck_path_groups)
        "User Avatars" -> getString(R.string.backup_precheck_path_user_avatars)
        "QuickReplies" -> getString(R.string.backup_precheck_path_quick_replies)
        "secrets.json" -> getString(R.string.backup_precheck_path_secrets)
        else -> path
    }
}

private sealed interface PendingDialog {
    object ResetToDefault : PendingDialog
    object RemoveUserData : PendingDialog
    data class RestoreSettingsSnapshot(val name: String) : PendingDialog
    data class CheckingImport(val uri: Uri) : PendingDialog
    data class ConfirmImport(
        val uri: Uri,
        val previewText: String,
        val canImport: Boolean
    ) : PendingDialog
}

private fun ThemeMode.shouldUseDarkTheme(systemInDarkTheme: Boolean): Boolean {
    return when (this) {
        ThemeMode.AUTO -> systemInDarkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

private fun pendingDialogStateSaver(): androidx.compose.runtime.saveable.Saver<PendingDialog?, String> {
    return androidx.compose.runtime.saveable.Saver(
        save = { dialog ->
            when (dialog) {
                null -> "none"
                PendingDialog.ResetToDefault -> "reset"
                PendingDialog.RemoveUserData -> "remove-data"
                is PendingDialog.RestoreSettingsSnapshot -> "restore-snapshot:${Uri.encode(dialog.name)}"
                is PendingDialog.CheckingImport -> "none"
                is PendingDialog.ConfirmImport -> "import:${Uri.encode(dialog.uri.toString())}:${dialog.canImport}:${Uri.encode(dialog.previewText)}"
            }
        },
        restore = { key ->
            when {
                key == "none" -> null
                key == "reset" -> PendingDialog.ResetToDefault
                key == "remove-data" -> PendingDialog.RemoveUserData
                key.startsWith("restore-snapshot:") -> PendingDialog.RestoreSettingsSnapshot(
                    Uri.decode(key.removePrefix("restore-snapshot:"))
                )
                key.startsWith("import:") -> PendingDialog.ConfirmImport(
                    uri = Uri.parse(Uri.decode(key.removePrefix("import:").split(":", limit = 3).getOrElse(0) { "" })),
                    canImport = key.removePrefix("import:").split(":", limit = 3).getOrElse(1) { "false" }.toBoolean(),
                    previewText = Uri.decode(key.removePrefix("import:").split(":", limit = 3).getOrElse(2) { "" })
                )
                else -> null
            }
        }
    )
}
