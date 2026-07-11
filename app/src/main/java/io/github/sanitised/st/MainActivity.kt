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
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Logout
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
import io.github.sanitised.st.ui.navigation.DrawerNavItem
import io.github.sanitised.st.ui.navigation.STNavigationScaffold
import io.github.sanitised.st.ui.navigation.STRoutes
import io.github.sanitised.st.ui.screens.STAvatar
import io.github.sanitised.st.ui.screens.STStatusDot
import io.github.sanitised.st.ui.screens.STAISettingsScreen
import io.github.sanitised.st.ui.screens.STApiConnectionScreen
import io.github.sanitised.st.ui.screens.STProviderDetailScreen
import io.github.sanitised.st.ui.screens.STCharacterCreateScreen
import io.github.sanitised.st.ui.screens.STCharacterLibraryScreen
import io.github.sanitised.st.ui.screens.STCharacterProfileScreen
import io.github.sanitised.st.ui.screens.STChatListScreen
import io.github.sanitised.st.ui.screens.STPastChatsScreen
import io.github.sanitised.st.ui.screens.STDrawerState
import io.github.sanitised.st.ui.screens.STMeScreen
import io.github.sanitised.st.ui.screens.STMemoryScreen
import io.github.sanitised.st.ui.screens.STPersonaScreen
import io.github.sanitised.st.ui.screens.STStCoreScreen
import io.github.sanitised.st.ui.screens.STWorldInfoScreen
import io.github.sanitised.st.ui.screens.STSecretsScreen
import io.github.sanitised.st.ui.screens.STExtensionsScreen
import io.github.sanitised.st.ui.screens.STAuthorNoteCFGScreen
import io.github.sanitised.st.ui.screens.STQuickReplyScreen
import io.github.sanitised.st.ui.screens.STAppearanceScreen
import io.github.sanitised.st.ui.screens.STGroupChatScreen
import io.github.sanitised.st.ui.screens.STLoginScreen
import io.github.sanitised.st.ui.screens.STOnboardingScreen
import io.github.sanitised.st.ui.screens.STAccountScreen
import io.github.sanitised.st.ui.screens.STWorldBookManageScreen
import io.github.sanitised.st.ui.screens.STLorebookDetailScreen
import io.github.sanitised.st.ui.screens.STWorldEntryEditScreen
import io.github.sanitised.st.ui.screens.STWIGlobalSettingsScreen
import io.github.sanitised.st.ui.screens.STCharacterFormScreen
import io.github.sanitised.st.ui.screens.STAltGreetingsScreen
import io.github.sanitised.st.ui.screens.STCharacterAdvancedScreen
import io.github.sanitised.st.ui.screens.STBackgroundsScreen
import io.github.sanitised.st.ui.screens.STThemeScreen
import io.github.sanitised.st.ui.screens.STChatBehaviorScreen
import io.github.sanitised.st.ui.screens.configuredApiConnectionProviderCount
import io.github.sanitised.st.ui.screens.rememberLocalTavernLibrarySnapshot
import io.github.sanitised.st.ui.components.STConfirmDialog
import io.github.sanitised.st.ui.components.STDialogButtonStyle
import io.github.sanitised.st.ui.theme.STAppTheme
import io.github.sanitised.st.chat.*
import io.github.sanitised.st.chat.engine.NativeChatEngine
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.GroupCreateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val drawerNavItems = listOf(
    DrawerNavItem(STRoutes.HOME, "对话", Icons.AutoMirrored.Filled.Chat),
    DrawerNavItem(STRoutes.CHARACTERS, "角色库", Icons.Filled.Groups),
    DrawerNavItem(STRoutes.GROUP_CHAT, "群聊", Icons.Filled.GroupAdd),
    DrawerNavItem(STRoutes.PERSONA, "扮演者", Icons.Filled.Face),
    DrawerNavItem(STRoutes.WORLD_INFO, "世界书", Icons.Filled.Book),
    DrawerNavItem(STRoutes.CHAT_BACKUPS, "记忆与回顾", Icons.Filled.CloudSync),
    DrawerNavItem(STRoutes.AUTHOR_NOTE, "作者注 & CFG", Icons.Filled.History),
    DrawerNavItem(STRoutes.PRESETS, "AI 采样设置", Icons.Filled.Tune, isPrimaryGroup = false),
    DrawerNavItem(STRoutes.CONNECTIONS, "API 连接", Icons.Filled.SettingsEthernet, isPrimaryGroup = false),
    DrawerNavItem(STRoutes.EXTENSIONS, "扩展", Icons.Filled.Extension, isPrimaryGroup = false),
    DrawerNavItem(STRoutes.APPEARANCE, "主题外观", Icons.Filled.Palette, isPrimaryGroup = false),
    DrawerNavItem(STRoutes.MANAGE_ST, "ST 内核", Icons.Filled.Memory, isPrimaryGroup = false),
    DrawerNavItem(STRoutes.SETTINGS, "设置", Icons.Filled.Settings, isPrimaryGroup = false),
    DrawerNavItem(STRoutes.LEGAL, "帮助 & 文档", Icons.AutoMirrored.Filled.Help, isPrimaryGroup = false),
    DrawerNavItem(STRoutes.LOGIN, "退出登录", Icons.AutoMirrored.Filled.Logout, isPrimaryGroup = false)
)

@Composable
private fun STAppDrawerHeader(
    stLabel: String,
    nodeLabel: String,
    status: NodeStatus
) {
    val colors = MaterialTheme.colorScheme
    val drawerState = STDrawerState.from(
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
                STAvatar(
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
                    STStatusDot(color = if (drawerState.connected) colors.tertiary else colors.outline)
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
            // 子页面路由折叠到抽屉里对应的父条目,保证抽屉高亮正确。
            val drawerSelectedRoute = when (currentRoute) {
                STRoutes.CHAT -> STRoutes.HOME
                STRoutes.CHARACTER_NEW,
                STRoutes.CHARACTER_DETAIL,
                STRoutes.CHARACTER_EDIT,
                STRoutes.CHAR_FORM,
                STRoutes.CHAR_GREETINGS,
                STRoutes.CHAR_ADVANCED -> STRoutes.CHARACTERS
                STRoutes.WORLD_INFO_MANAGE,
                STRoutes.WORLD_INFO_BOOK,
                STRoutes.WORLD_INFO_ENTRY,
                STRoutes.WORLD_INFO_GLOBAL -> STRoutes.WORLD_INFO
                STRoutes.GROUP_CHAT_DETAIL -> STRoutes.GROUP_CHAT
                STRoutes.SECRETS -> STRoutes.CONNECTIONS
                STRoutes.QUICK_REPLIES -> STRoutes.EXTENSIONS
                STRoutes.BACKGROUNDS,
                STRoutes.THEME,
                STRoutes.CHAT_BEHAVIOR -> STRoutes.APPEARANCE
                STRoutes.LOGS,
                STRoutes.CONFIG -> STRoutes.MANAGE_ST
                STRoutes.LICENSE -> STRoutes.LEGAL
                STRoutes.ACCOUNT -> STRoutes.SETTINGS
                else -> currentRoute
            }
            val showNavigationChrome = currentRoute != STRoutes.CHAT &&
                currentRoute != STRoutes.LOGIN &&
                currentRoute != STRoutes.ONBOARDING
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
            val tavernReadyForAutoOpen = remember { mutableStateOf(false) }
            val wasReadyToAutoOpen = remember { mutableStateOf(false) }
            val stdoutState = remember { mutableStateOf("") }
            val stderrState = remember { mutableStateOf("") }
            val serviceState = remember { mutableStateOf("") }
            val pendingDialogState = rememberSaveable(stateSaver = pendingDialogStateSaver()) {
                mutableStateOf<PendingDialog?>(null)
            }
            val notificationGrantedState = remember { mutableStateOf(isNotificationPermissionGranted()) }
            val notificationAutoPromptAttempted = rememberSaveable { mutableStateOf(false) }
            val batteryUnrestrictedState = remember { mutableStateOf(isBatteryUnrestricted()) }
            val scope = rememberCoroutineScope()
            val lifecycleOwner = LocalLifecycleOwner.current
            val snackbarHostState = remember { SnackbarHostState() }
            
            var connectedCount by remember { mutableStateOf(0) }
            val running = statusState.value.state == NodeState.RUNNING
            LaunchedEffect(running, statusState.value.port) {
                if (running) {
                    runCatching {
                        val client = io.github.sanitised.st.api.TavernCoreClient(
                            io.github.sanitised.st.SillyTavernUrl.localWebUrl(statusState.value.port)
                        )
                        val secretsList = client.listSecrets()
                        connectedCount = configuredApiConnectionProviderCount(secretsList)
                    }
                } else {
                    connectedCount = 0
                }
            }
            LaunchedEffect(statusState.value.state, statusState.value.port) {
                if (statusState.value.state != NodeState.RUNNING) {
                    tavernReadyForAutoOpen.value = false
                    return@LaunchedEffect
                }
                tavernReadyForAutoOpen.value = false
                val client = io.github.sanitised.st.api.TavernCoreClient(
                    SillyTavernUrl.localWebUrl(statusState.value.port)
                )
                val deadline = System.currentTimeMillis() + 60_000L
                while (
                    statusState.value.state == NodeState.RUNNING &&
                    !tavernReadyForAutoOpen.value &&
                    System.currentTimeMillis() < deadline
                ) {
                    val ok = withContext(Dispatchers.IO) {
                        client.healthCheck().ok
                    }
                    if (ok) {
                        tavernReadyForAutoOpen.value = true
                        break
                    }
                    delay(1000)
                }
            }
            val readyToAutoOpen = statusState.value.state == NodeState.RUNNING && tavernReadyForAutoOpen.value
            LaunchedEffect(readyToAutoOpen, viewModel.autoOpenBrowserWhenReady.value) {
                val justBecameReady = readyToAutoOpen && !wasReadyToAutoOpen.value
                if (
                    viewModel.autoOpenBrowserWhenReady.value && justBecameReady &&
                    !autoOpenBrowserTriggeredForCurrentRun.value
                ) {
                    openSillyTavernInBrowser(statusState.value.port)
                    autoOpenBrowserTriggeredForCurrentRun.value = true
                }
                wasReadyToAutoOpen.value = readyToAutoOpen
            }

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
                serverRunning = statusState.value.state == NodeState.RUNNING,
                baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
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
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { useDarkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { useDarkTheme }
                )
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
            val chatScope = rememberCoroutineScope()
            val nativeChatLoader = remember(chatStore) {
                NativeChatLoader(
                    store = chatStore,
                    clientProvider = { TavernCoreClient(SillyTavernUrl.localWebUrl(statusState.value.port)) }
                )
            }
            val nativeChatRuntime = remember(chatStore) {
                NativeChatRuntime(chatStore) {
                    TavernNativeChatDataSource(TavernCoreClient(SillyTavernUrl.localWebUrl(statusState.value.port)))
                }
            }
            val chatEngine = remember(chatStore) {
                NativeChatEngine(
                    scope = chatScope,
                    store = chatStore,
                    clientProvider = { TavernCoreClient(SillyTavernUrl.localWebUrl(statusState.value.port)) }
                )
            }
            var pendingChatTarget by rememberSaveable(stateSaver = chatTargetSaver()) {
                mutableStateOf<ChatTarget>(ChatTarget.Current)
            }
            val navigateMainTab: (String) -> Unit = { route ->
                if (route == STRoutes.LOGIN) {
                    // 抽屉「退出登录」走通用导航：先向后端注销并清本地会话 cookie，再进登录页，
                    // 否则旧会话 cookie 仍在共享 jar 里，后续私有接口会沿用上一个账户。
                    scope.launch {
                        if (statusState.value.state == NodeState.RUNNING) {
                            runCatching {
                                TavernCoreClient(SillyTavernUrl.localWebUrl(statusState.value.port)).logoutUser()
                            }
                        }
                        navController.navigate(STRoutes.LOGIN) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                } else {
                    if (route == STRoutes.CHAT) {
                        // store 里已有会话时沿用(ChatTarget.Current);冷启动后 store 为空,
                        // Current 没有任何加载逻辑会永久停在加载页,退回打开最近一条聊天。
                        val storeHasChat = chatStore.chatFile.isNotBlank() ||
                            chatStore.characterName.isNotBlank() ||
                            chatStore.messages.isNotEmpty()
                        pendingChatTarget = if (storeHasChat) {
                            ChatTarget.Current
                        } else {
                            librarySnapshot.recentChats.firstOrNull()?.let { recent ->
                                ChatTarget.CharacterChat(
                                    avatar = recent.characterId,
                                    chatFile = recent.id.substringAfter('/', "").ifBlank { null },
                                )
                            } ?: ChatTarget.Current
                        }
                    }
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
            val openCharacterChatFromCharacterManagement: (String, String?) -> Unit = { avatar, chatFile ->
                pendingChatTarget = ChatTarget.CharacterChat(avatar, chatFile)
                navController.navigate(STRoutes.CHAT) {
                    launchSingleTop = true
                }
            }
            val openGroupChat: (String, String?) -> Unit = { groupId, chatId ->
                navController.navigate(STRoutes.groupChatDetail(groupId, chatId)) {
                    launchSingleTop = true
                }
            }

            val dynamicDrawerItems = remember(drawerNavItems, connectedCount, librarySnapshot, statusState.value.state) {
                drawerNavItems.map { item ->
                    when (item.route) {
                        STRoutes.HOME -> item.copy(
                            badgeText = librarySnapshot.recentChats.size
                                .takeIf { it > 0 }
                                ?.toString()
                        )
                        STRoutes.CHARACTERS -> item.copy(
                            badgeText = librarySnapshot.characters.size
                                .takeIf { it > 0 }
                                ?.toString()
                        )
                        STRoutes.CONNECTIONS -> item.copy(supportingText = "${connectedCount} 个已配置")
                        STRoutes.MANAGE_ST -> item.copy(
                            supportingText = if (statusState.value.state == NodeState.RUNNING) "运行中" else "未运行"
                        )
                        else -> item
                    }
                }
            }

            STAppTheme(useDarkTheme = useDarkTheme, colorSource = themeColorSource) {
                STNavigationScaffold(
                    drawerItems = dynamicDrawerItems,
                    currentRoute = drawerSelectedRoute,
                    onNavigate = navigateMainTab,
                    showNavigation = showNavigationChrome,
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
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = STRoutes.HOME,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable(STRoutes.HOME) {
                                // Replaces the old STAndroidApp dashboard with the prototype ChatListScreen.
                                STChatListScreen(
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
                                        openCharacterChatFromCharacterManagement(chat.characterId, chat.chatFile)
                                    },
                                    onNewChat = {
                                        navController.navigate(STRoutes.CHARACTERS)
                                        viewModel.showTransientMessage("请选择角色开始新对话")
                                    },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.CHAT) {
                                NativeChatScreen(
                                    status = statusState.value,
                                    target = pendingChatTarget,
                                    store = chatStore,
                                    engine = chatEngine,
                                    nativeChatLoadingEnabled = true,
                                    nativeChatLoader = nativeChatLoader,
                                    nativeChatRuntime = nativeChatRuntime,
                                    quickReplyDataRoot = appPaths.dataDir,
                                    onBackToHome = {
                                        if (!navController.popBackStack()) navigateMainTab(STRoutes.HOME)
                                    },
                                    onOpenPastChats = {
                                        val avatar = chatStore.avatarUrl
                                        if (avatar.isNotBlank()) {
                                            navController.navigate(STRoutes.pastChats(avatar))
                                        }
                                    },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.CHARACTERS) {
                                // The visible route uses the prototype character library surface.
                                STCharacterLibraryScreen(
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
                                STCharacterCreateScreen(
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
                                    STCharacterProfileScreen(
                                        status = statusState.value,
                                        baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                        avatar = avatar,
                                        onStartService = { startNode() },
                                        onBack = { navController.popBackStack() },
                                        onOpenChat = { chatFile ->
                                            openCharacterChatFromCharacterManagement(avatar, chatFile)
                                        },
                                        onOpenPastChats = {
                                            navController.navigate(STRoutes.pastChats(avatar))
                                        },
                                        onOpenFullEdit = {
                                            navController.navigate(STRoutes.characterForm(avatar))
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
                                    STCharacterProfileScreen(
                                        status = statusState.value,
                                        baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                        avatar = avatar,
                                        onStartService = { startNode() },
                                        onBack = { navController.popBackStack() },
                                        onOpenChat = { chatFile ->
                                            openCharacterChatFromCharacterManagement(avatar, chatFile)
                                        },
                                        onOpenPastChats = {
                                            navController.navigate(STRoutes.pastChats(avatar))
                                        },
                                        onOpenFullEdit = {
                                            navController.navigate(STRoutes.characterForm(avatar))
                                        },
                                        onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                    )
                                }
                            }

                            composable(
                                route = STRoutes.PAST_CHATS,
                                arguments = listOf(navArgument("avatar") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val avatar = backStackEntry.arguments?.getString("avatar")?.let { Uri.decode(it) }
                                if (avatar != null) {
                                    STPastChatsScreen(
                                        status = statusState.value,
                                        baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                        avatar = avatar,
                                        currentChatFile = chatStore.chatFile,
                                        onBack = { navController.popBackStack() },
                                        onOpenChat = { chatFile ->
                                            openCharacterChatFromCharacterManagement(avatar, chatFile)
                                        },
                                        onNewChat = {
                                            scope.launch {
                                                runCatching { nativeChatRuntime.createNewChat(avatar) }
                                                    .onSuccess { newChat ->
                                                        openCharacterChatFromCharacterManagement(avatar, newChat)
                                                        viewModel.showTransientMessage("已创建新对话")
                                                    }
                                                    .onFailure { error ->
                                                        viewModel.showTransientMessage(error.message ?: "创建新对话失败")
                                                    }
                                            }
                                        },
                                        onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                    )
                                }
                            }

                            composable(
                                route = STRoutes.CHAR_FORM,
                                arguments = listOf(navArgument("avatar") { type = NavType.StringType })
                            ) { backStackEntry ->
                                BackHandler { navController.popBackStack() }
                                val avatar = backStackEntry.arguments?.getString("avatar")?.let { Uri.decode(it) }.orEmpty()
                                STCharacterFormScreen(
                                    avatar = avatar,
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onClose = { navController.popBackStack() },
                                    onOpenGreetings = { navController.navigate(STRoutes.characterGreetings(avatar)) },
                                    onOpenAdvanced = { navController.navigate(STRoutes.characterAdvanced(avatar)) },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(
                                route = STRoutes.CHAR_GREETINGS,
                                arguments = listOf(navArgument("avatar") { type = NavType.StringType })
                            ) { backStackEntry ->
                                BackHandler { navController.popBackStack() }
                                val avatar = backStackEntry.arguments?.getString("avatar")?.let { Uri.decode(it) }.orEmpty()
                                STAltGreetingsScreen(
                                    avatar = avatar,
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(
                                route = STRoutes.CHAR_ADVANCED,
                                arguments = listOf(navArgument("avatar") { type = NavType.StringType })
                            ) { backStackEntry ->
                                BackHandler { navController.popBackStack() }
                                val avatar = backStackEntry.arguments?.getString("avatar")?.let { Uri.decode(it) }.orEmpty()
                                STCharacterAdvancedScreen(
                                    avatar = avatar,
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onClose = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.WORLD_INFO) {
                                BackHandler { navController.popBackStack() }
                                STWorldInfoScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onStartService = { startNode() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) },
                                    onOpenManage = { navController.navigate(STRoutes.WORLD_INFO_MANAGE) },
                                    onOpenBook = { name -> navController.navigate(STRoutes.worldInfoBook(name)) },
                                    onOpenGlobalSettings = { navController.navigate(STRoutes.WORLD_INFO_GLOBAL) }
                                )
                            }

                            composable(STRoutes.WORLD_INFO_MANAGE) {
                                BackHandler { navController.popBackStack() }
                                STWorldBookManageScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onBack = { navController.popBackStack() },
                                    onOpenBook = { name -> navController.navigate(STRoutes.worldInfoBook(name)) },
                                    onOpenGlobalSettings = { navController.navigate(STRoutes.WORLD_INFO_GLOBAL) },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(
                                route = STRoutes.WORLD_INFO_BOOK,
                                arguments = listOf(navArgument("name") { type = NavType.StringType })
                            ) { backStackEntry ->
                                BackHandler { navController.popBackStack() }
                                val name = backStackEntry.arguments?.getString("name").orEmpty()
                                STLorebookDetailScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    bookName = name,
                                    onBack = { navController.popBackStack() },
                                    onOpenEntry = { uid -> navController.navigate(STRoutes.worldInfoEntry(uid, name)) },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(
                                route = STRoutes.WORLD_INFO_ENTRY,
                                arguments = listOf(
                                    navArgument("uid") { type = NavType.IntType; defaultValue = -1 },
                                    navArgument("book") { type = NavType.StringType; defaultValue = "" }
                                )
                            ) { backStackEntry ->
                                BackHandler { navController.popBackStack() }
                                STWorldEntryEditScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    entryUid = backStackEntry.arguments?.getInt("uid") ?: -1,
                                    bookName = backStackEntry.arguments?.getString("book").orEmpty(),
                                    onClose = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.WORLD_INFO_GLOBAL) {
                                BackHandler { navController.popBackStack() }
                                STWIGlobalSettingsScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.PERSONA) {
                                BackHandler { navController.popBackStack() }
                                STPersonaScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.PRESETS) {
                                BackHandler { navController.popBackStack() }
                                STAISettingsScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) },
                                    onSettingsChanged = { }
                                )
                            }

                            composable(STRoutes.CONNECTIONS) {
                                BackHandler { navController.popBackStack() }
                                STApiConnectionScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onBack = { navController.popBackStack() },
                                    onOpenSecrets = { navController.navigate(STRoutes.SECRETS) },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) },
                                    onOpenProviderDetail = { providerId ->
                                        navController.navigate(STRoutes.providerDetail(providerId))
                                    },
                                    onSettingsChanged = { }
                                )
                            }

                            composable(
                                route = STRoutes.PROVIDER_DETAIL,
                                arguments = listOf(navArgument("providerId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val providerId = backStackEntry.arguments?.getString("providerId")?.let { Uri.decode(it) } ?: "anthropic"
                                BackHandler { navController.popBackStack() }
                                STProviderDetailScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    providerId = providerId,
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) },
                                    onSettingsChanged = { }
                                )
                            }

                            composable(STRoutes.CHAT_BACKUPS) {
                                BackHandler { navController.popBackStack() }
                                STMemoryScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.GROUP_CHAT) {
                                BackHandler {
                                    if (!navController.popBackStack()) {
                                        navigateMainTab(STRoutes.HOME)
                                    }
                                }
                                STGroupChatScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onOpenGroupChat = openGroupChat,
                                    onStartService = { startNode() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) },
                                    onNavigateToNewGroup = { navController.navigate("group-chat/new") }
                                )
                            }

                            composable(
                                route = STRoutes.GROUP_CHAT_DETAIL,
                                arguments = listOf(
                                    navArgument("groupId") { type = NavType.StringType },
                                    navArgument("chatId") {
                                        type = NavType.StringType
                                        defaultValue = ""
                                    }
                                )
                            ) { backStackEntry ->
                                BackHandler { navController.popBackStack() }
                                val gid = backStackEntry.arguments?.getString("groupId").orEmpty()
                                val cid = backStackEntry.arguments?.getString("chatId").orEmpty()
                                GroupChatScreen(
                                    groupId = gid,
                                    chatId = cid.takeIf { it.isNotBlank() },
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onBack = { navController.popBackStack() },
                                    onNavigateToSettings = { navController.navigate(STRoutes.groupSettings(gid)) },
                                    onNavigateToMembers = { navController.navigate(STRoutes.groupMembers(gid)) },
                                    onNavigateToNewGroup = { navController.navigate("group-chat/new") },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(
                                route = STRoutes.GROUP_SETTINGS,
                                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                BackHandler { navController.popBackStack() }
                                GroupSettingsScreen(
                                    groupId = backStackEntry.arguments?.getString("groupId").orEmpty(),
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(
                                route = STRoutes.GROUP_MEMBERS,
                                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                BackHandler { navController.popBackStack() }
                                GroupMembersScreen(
                                    groupId = backStackEntry.arguments?.getString("groupId").orEmpty(),
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable("group-chat/new") {
                                BackHandler { navController.popBackStack() }
                                val newGroupBaseUrl = SillyTavernUrl.localWebUrl(statusState.value.port)
                                val newGroupScope = rememberCoroutineScope()
                                var newGroupCharacters by remember { mutableStateOf<List<CharacterSummary>>(emptyList()) }
                                var newGroupLoading by remember { mutableStateOf(true) }
                                var creatingGroup by remember { mutableStateOf(false) }
                                LaunchedEffect(newGroupBaseUrl) {
                                    newGroupLoading = true
                                    runCatching { TavernCoreClient(newGroupBaseUrl).listCharacters() }
                                        .onSuccess { newGroupCharacters = it.sortedBy { c -> c.name.lowercase() } }
                                        .onFailure { error ->
                                            viewModel.showTransientMessage(error.message ?: "角色列表加载失败")
                                        }
                                    newGroupLoading = false
                                }
                                NewGroupScreen(
                                    characters = newGroupCharacters,
                                    loading = newGroupLoading,
                                    onClose = { navController.popBackStack() },
                                    onCreate = { name, members, strategy ->
                                        if (creatingGroup) return@NewGroupScreen
                                        creatingGroup = true
                                        newGroupScope.launch {
                                            runCatching {
                                                TavernCoreClient(newGroupBaseUrl).createGroup(
                                                    GroupCreateRequest(
                                                        name = name,
                                                        members = members,
                                                        activationStrategy = strategy
                                                    )
                                                )
                                            }.onSuccess { created ->
                                                viewModel.showTransientMessage("已创建群聊「${created.name}」")
                                                navController.popBackStack()
                                            }.onFailure { error ->
                                                viewModel.showTransientMessage(error.message ?: "创建群聊失败")
                                            }
                                            creatingGroup = false
                                        }
                                    }
                                )
                            }

                            composable(STRoutes.SETTINGS) {
                                STMeScreen(
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
                                    bubbleStyle = viewModel.bubbleStyle.value,
                                    onBubbleStyleChanged = { enabled -> viewModel.setBubbleStyle(enabled) },
                                    vibrationFeedback = viewModel.vibrationFeedback.value,
                                    onVibrationFeedbackChanged = { enabled -> viewModel.setVibrationFeedback(enabled) },
                                    secondConfirmation = viewModel.secondConfirmation.value,
                                    onSecondConfirmationChanged = { enabled -> viewModel.setSecondConfirmation(enabled) },
                                    swipeDrawer = viewModel.swipeDrawer.value,
                                    onSwipeDrawerChanged = { enabled -> viewModel.setSwipeDrawer(enabled) },
                                    developerMode = viewModel.developerMode.value,
                                    onDeveloperModeChanged = { enabled -> viewModel.setDeveloperMode(enabled) },
                                    onOpenWorldInfo = { navController.navigate(STRoutes.WORLD_INFO) },
                                    onOpenPersona = { navController.navigate(STRoutes.PERSONA) },
                                    onOpenPresets = { navController.navigate(STRoutes.PRESETS) },
                                    onOpenConnections = { navController.navigate(STRoutes.CONNECTIONS) },
                                    onOpenChatBackups = { navController.navigate(STRoutes.CHAT_BACKUPS) },
                                    onOpenLogs = { navController.navigate(STRoutes.LOGS) },
                                    onOpenConfig = { navController.navigate(STRoutes.CONFIG) },
                                    onOpenManageSt = { navController.navigate(STRoutes.MANAGE_ST) },
                                    onOpenSecrets = { navController.navigate(STRoutes.SECRETS) },
                                    onOpenExtensions = { navController.navigate(STRoutes.EXTENSIONS) },
                                    onOpenAppearance = { navController.navigate(STRoutes.APPEARANCE) },
                                    onOpenAccount = { navController.navigate(STRoutes.ACCOUNT) },
                                    onOpenBackgrounds = { navController.navigate(STRoutes.BACKGROUNDS) },
                                    onOpenTheme = { navController.navigate(STRoutes.THEME) },
                                    onOpenChatBehavior = { navController.navigate(STRoutes.CHAT_BEHAVIOR) },
                                    appVersion = versionLabel,
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.LOGIN) {
                                STLoginScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onClose = { if (!navController.popBackStack()) navigateMainTab(STRoutes.HOME) },
                                    onLoggedIn = {
                                        navController.navigate(STRoutes.HOME) {
                                            popUpTo(STRoutes.HOME) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    },
                                    onOnboarding = { navController.navigate(STRoutes.ONBOARDING) }
                                )
                            }

                            composable(STRoutes.ONBOARDING) {
                                val goHome: () -> Unit = {
                                    navController.navigate(STRoutes.HOME) {
                                        popUpTo(STRoutes.HOME) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                }
                                STOnboardingScreen(onFinish = goHome, onSkip = goHome)
                            }

                            composable(STRoutes.ACCOUNT) {
                                BackHandler { navController.popBackStack() }
                                STAccountScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onBack = { navController.popBackStack() },
                                    onLogout = {
                                        navController.navigate(STRoutes.LOGIN) {
                                            popUpTo(STRoutes.HOME) { inclusive = true }
                                        }
                                    },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.BACKGROUNDS) {
                                BackHandler { navController.popBackStack() }
                                STBackgroundsScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.THEME) {
                                BackHandler { navController.popBackStack() }
                                STThemeScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.CHAT_BEHAVIOR) {
                                BackHandler { navController.popBackStack() }
                                STChatBehaviorScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.SECRETS) {
                                BackHandler { navController.popBackStack() }
                                STSecretsScreen(
                                    status = statusState.value,
                                    baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.EXTENSIONS) {
                                BackHandler { navController.popBackStack() }
                                STExtensionsScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenQuickReplies = { navController.navigate(STRoutes.QUICK_REPLIES) },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.AUTHOR_NOTE) {
                                BackHandler { navController.popBackStack() }
                                STAuthorNoteCFGScreen(
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.QUICK_REPLIES) {
                                BackHandler { navController.popBackStack() }
                                STQuickReplyScreen(
                                    onBack = { navController.popBackStack() },
                                    onShowMessage = { message -> viewModel.showTransientMessage(message) }
                                )
                            }

                            composable(STRoutes.APPEARANCE) {
                                BackHandler { navController.popBackStack() }
                                STAppearanceScreen(
                                    fontSize = viewModel.fontSize.value,
                                    onFontSizeChanged = { size -> viewModel.setFontSize(size) },
                                    reduceMotion = viewModel.reduceMotion.value,
                                    onReduceMotionChanged = { enabled -> viewModel.setReduceMotion(enabled) },
                                    onBack = { navController.popBackStack() },
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
                                STStCoreScreen(
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
                                    autoStartService = viewModel.autoStartService.value,
                                    onAutoStartServiceChanged = { enabled -> viewModel.setAutoStartService(enabled) },
                                    autoOpenBrowser = viewModel.autoOpenBrowserWhenReady.value,
                                    onAutoOpenBrowserChanged = { enabled -> viewModel.setAutoOpenBrowserWhenReady(enabled) },
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

private fun chatTargetSaver(): androidx.compose.runtime.saveable.Saver<ChatTarget, String> {
    return androidx.compose.runtime.saveable.Saver(
        save = { target ->
            when (target) {
                ChatTarget.Current -> "current"
                is ChatTarget.CharacterChat -> listOf(
                    "character",
                    Uri.encode(target.avatar),
                    Uri.encode(target.chatFile.orEmpty())
                ).joinToString("|")
                is ChatTarget.GroupChat -> listOf(
                    "group",
                    Uri.encode(target.groupId),
                    Uri.encode(target.chatId.orEmpty())
                ).joinToString("|")
            }
        },
        restore = { encoded ->
            val parts = encoded.split('|')
            when (parts.firstOrNull()) {
                "character" -> ChatTarget.CharacterChat(
                    avatar = parts.getOrNull(1)?.let { Uri.decode(it) }.orEmpty(),
                    chatFile = parts.getOrNull(2)?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() }
                )
                "group" -> ChatTarget.GroupChat(
                    groupId = parts.getOrNull(1)?.let { Uri.decode(it) }.orEmpty(),
                    chatId = parts.getOrNull(2)?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() }
                )
                else -> ChatTarget.Current
            }
        }
    )
}
