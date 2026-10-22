package io.github.sanitised.st

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.sanitised.st.chat.ChatTarget
import io.github.sanitised.st.chat.ChatViewModel
import io.github.sanitised.st.ui.navigation.PendingDialog
import io.github.sanitised.st.ui.navigation.STAppChrome
import io.github.sanitised.st.ui.navigation.STAppDialogs
import io.github.sanitised.st.ui.navigation.STAppDrawerHeader
import io.github.sanitised.st.ui.navigation.STAppNavHost
import io.github.sanitised.st.ui.navigation.STRoutes
import io.github.sanitised.st.ui.navigation.backupImportPreviewText
import io.github.sanitised.st.ui.navigation.chatTargetSaver
import io.github.sanitised.st.ui.navigation.drawerNavItems
import io.github.sanitised.st.ui.navigation.isReadyForNav
import io.github.sanitised.st.ui.navigation.pendingDialogStateSaver
import io.github.sanitised.st.ui.screens.ChatSeenStore
import io.github.sanitised.st.ui.screens.STDrawerAccount
import io.github.sanitised.st.ui.screens.buildHomeChatItems
import io.github.sanitised.st.ui.screens.buildSTDrawerAccount
import io.github.sanitised.st.ui.screens.chatSeenKey
import io.github.sanitised.st.ui.screens.configuredApiConnectionProviderCount
import io.github.sanitised.st.ui.screens.groupSeenKey
import io.github.sanitised.st.ui.screens.rememberLocalTavernLibrarySnapshot
import io.github.sanitised.st.ui.theme.STAppTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
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
            packageManager.getPackageInfo(packageName, 0).versionName ?: getString(R.string.unknown_short)
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
            val nodeValue = it.nodeTag?.takeIf(String::isNotBlank)
                ?: it.nodeVersion?.takeIf(String::isNotBlank)
                ?: it.nodeCommit?.takeIf(String::isNotBlank)
            if (nodeValue == null) getString(R.string.node_unknown) else getString(R.string.node_label, nodeValue)
        } ?: getString(R.string.node_unknown)
        val appContainer = (application as STApplication).container.apply {
            updateLocalPort(readConfiguredPort())
        }
        setContent {
            val viewModel: MainViewModel = viewModel()
            val chatViewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.factory(appContainer.tavernClientProvider::get)
            )
            val navController = rememberNavController()
            // 只观察「是否在首页」这个布尔(库快照刷新需要它),而不是在根作用域读
            // navBackStackEntry:后者会让每次导航都整棵重组本作用域(约千行)。
            // State 值不变不触发重组,所以只有进入/离开首页两个时刻各重组一次。
            // 抽屉高亮/是否显示外壳的路由推导收进 STAppChrome,随导航重组的只有它。
            val isOnHome by produceState(initialValue = true, navController) {
                navController.currentBackStackEntryFlow.collect { entry ->
                    value = entry.destination.route == STRoutes.HOME
                }
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
            val tavernReadyForAutoOpen = remember { mutableStateOf(false) }
            val wasReadyToAutoOpen = remember { mutableStateOf(false) }
            val pendingDialogState = rememberSaveable(stateSaver = pendingDialogStateSaver()) {
                mutableStateOf<PendingDialog?>(null)
            }
            val notificationGrantedState = remember { mutableStateOf(isNotificationPermissionGranted()) }
            val notificationAutoPromptAttempted = rememberSaveable { mutableStateOf(false) }
            val batteryUnrestrictedState = remember { mutableStateOf(isBatteryUnrestricted()) }
            val scope = rememberCoroutineScope()
            val lifecycleOwner = LocalLifecycleOwner.current
            val snackbarHostState = remember { SnackbarHostState() }
            val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

            var connectedCount by remember { mutableStateOf(0) }
            var drawerAccount by remember { mutableStateOf<STDrawerAccount?>(null) }
            val running = statusState.value.state == NodeState.RUNNING
            LaunchedEffect(running, statusState.value.port) {
                if (running) {
                    runCatching {
                        appContainer.updateLocalPort(statusState.value.port)
                        val client = appContainer.tavernClientProvider.get()
                        val secretsList = client.listSecrets()
                        connectedCount = configuredApiConnectionProviderCount(secretsList)
                        drawerAccount = runCatching {
                            buildSTDrawerAccount(client.getSettings(), secretsList)
                        }.getOrNull()
                    }
                } else {
                    connectedCount = 0
                    drawerAccount = null
                }
            }
            LaunchedEffect(statusState.value.state, statusState.value.port) {
                if (statusState.value.state != NodeState.RUNNING) {
                    tavernReadyForAutoOpen.value = false
                    return@LaunchedEffect
                }
                tavernReadyForAutoOpen.value = false
                appContainer.updateLocalPort(statusState.value.port)
                val client = appContainer.tavernClientProvider.get()
                val deadline = System.currentTimeMillis() + 60_000L
                while (
                    statusState.value.state == NodeState.RUNNING &&
                    !tavernReadyForAutoOpen.value &&
                    System.currentTimeMillis() < deadline
                ) {
                    val ok = withContext(Dispatchers.IO) { client.healthCheck().ok }
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
                        appContainer.updateLocalPort(status.port)
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
            // refreshKey 带上「是否在首页」:从聊天屏回到列表时重扫本地文件,
            // 未读/预览/排序才反映刚刚发生的对话。
            val librarySnapshot by rememberLocalTavernLibrarySnapshot(
                dataRoot = appPaths.dataDir,
                serverRunning = statusState.value.state == NodeState.RUNNING,
                baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                refreshKey = statusState.value.state to isOnHome
            )

            val service = nodeServiceState.value
            DisposableEffect(service) {
                viewModel.nodeService = service
                service?.registerListener(listener)
                onDispose { service?.unregisterListener(listener) }
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

            LaunchedEffect(Unit) { viewModel.maybeAutoCheckForUpdates() }
            LaunchedEffect(userMessage?.id) {
                userMessage?.let { message ->
                    snackbarHostState.showSnackbar(message.text)
                    viewModel.messageShown(message.id)
                }
            }
            LaunchedEffect(notificationGrantedState.value, notificationAutoPromptAttempted.value) {
                if (!notificationGrantedState.value && !notificationAutoPromptAttempted.value) {
                    notificationAutoPromptAttempted.value = true
                    maybeRequestNotificationPermission()
                }
            }
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
            ) { uri -> if (uri != null) viewModel.export(uri) }
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
            ) { uri -> if (uri != null) viewModel.installCustomZip(uri) }
            val triggerExport: () -> Unit = {
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
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
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                diagnosticExportLauncher.launch("sillytavern-diagnostics-$stamp.zip")
            }
            val chatStore = chatViewModel.store
            val nativeChatLoader = chatViewModel.loader
            val nativeChatRuntime = chatViewModel.runtime
            val chatEngine = chatViewModel.engine
            var pendingChatTarget by rememberSaveable(stateSaver = chatTargetSaver()) {
                mutableStateOf<ChatTarget>(ChatTarget.Current)
            }
            val chatSeenStore = remember { ChatSeenStore(this@MainActivity) }
            val generatingSeenKey = if (chatStore.isGenerating && chatStore.mode == "character") {
                chatSeenKey(chatStore.avatarUrl, chatStore.chatFile)
            } else null
            val homeChatItems = remember(librarySnapshot, generatingSeenKey, chatSeenStore.revision) {
                buildHomeChatItems(
                    snapshot = librarySnapshot,
                    generatingKey = generatingSeenKey,
                    lastSeen = chatSeenStore::lastSeen
                )
            }
            val navigateMainTab: (String) -> Unit = { route ->
                if (!navController.isReadyForNav()) {
                    // 上一次导航的过渡动画还没结束,丢弃这次点击,避免 NavHost 白屏。
                } else if (route == STRoutes.LOGIN) {
                    // 抽屉「退出登录」走通用导航：先向后端注销并清本地会话 cookie，再进登录页，
                    // 否则旧会话 cookie 仍在共享 jar 里，后续私有接口会沿用上一个账户。
                    scope.launch {
                        if (statusState.value.state == NodeState.RUNNING) {
                            runCatching {
                                appContainer.updateLocalPort(statusState.value.port)
                                appContainer.tavernClientProvider.get().logoutUser()
                            }
                        }
                        navController.navigate(STRoutes.LOGIN) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    }
                } else {
                    if (route == STRoutes.CHAT) {
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
                        popUpTo(navController.graph.findStartDestination().id)
                        launchSingleTop = true
                    }
                }
            }
            val openCharacterChatFromCharacterManagement: (String, String?) -> Unit = { avatar, chatFile ->
                chatSeenStore.markSeen(chatSeenKey(avatar, chatFile))
                pendingChatTarget = ChatTarget.CharacterChat(avatar, chatFile)
                navController.navigate(STRoutes.CHAT) { launchSingleTop = true }
            }
            val openGroupChat: (String, String?) -> Unit = { groupId, chatId ->
                chatSeenStore.markSeen(groupSeenKey(groupId))
                navController.navigate(STRoutes.groupChatDetail(groupId, chatId)) {
                    launchSingleTop = true
                }
            }
            val dynamicDrawerItems = remember(drawerNavItems, connectedCount, librarySnapshot, homeChatItems, statusState.value.state) {
                drawerNavItems.map { item ->
                    when (item.route) {
                        STRoutes.HOME -> item.copy(badgeText = homeChatItems.size.takeIf { it > 0 }?.toString())
                        STRoutes.CHARACTERS -> item.copy(badgeText = librarySnapshot.characters.size.takeIf { it > 0 }?.toString())
                        STRoutes.CONNECTIONS -> item.copy(supportingText = "${connectedCount} 个已配置")
                        STRoutes.MANAGE_ST -> item.copy(
                            supportingText = if (statusState.value.state == NodeState.RUNNING) "运行中" else "未运行"
                        )
                        else -> item
                    }
                }
            }

            STAppTheme(
                useDarkTheme = useDarkTheme,
                colorSource = themeColorSource,
                chatFontSize = viewModel.fontSize.value,
                chatBubbleStyle = viewModel.bubbleStyle.value
            ) {
                STAppChrome(
                    navController = navController,
                    drawerItems = dynamicDrawerItems,
                    onNavigate = navigateMainTab,
                    snackbarHostState = snackbarHostState,
                    drawerHeader = { navigateFromDrawer ->
                        STAppDrawerHeader(
                            stLabel = currentStLabel,
                            nodeLabel = nodeLabel,
                            status = statusState.value,
                            account = drawerAccount,
                            onOpenPersonas = { navigateFromDrawer(STRoutes.PERSONA) },
                            onOpenConnections = { navigateFromDrawer(STRoutes.CONNECTIONS) }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            .fillMaxSize()
                    ) {
                        STAppNavHost(
                            navController = navController,
                            statusState = statusState,
                            viewModel = viewModel,
                            chatStore = chatStore,
                            chatEngine = chatEngine,
                            nativeChatLoader = nativeChatLoader,
                            nativeChatRuntime = nativeChatRuntime,
                            chatSeenStore = chatSeenStore,
                            appPaths = appPaths,
                            characterRepository = appContainer.characterRepository,
                            homeChatItems = homeChatItems,
                            pendingChatTarget = pendingChatTarget,
                            currentStLabel = currentStLabel,
                            nodeLabel = nodeLabel,
                            versionLabel = versionLabel,
                            legalDocs = legalDocs,
                            batteryUnrestrictedState = batteryUnrestrictedState,
                            pendingDialogState = pendingDialogState,
                            startNode = ::startNode,
                            stopNode = ::stopNode,
                            openBatteryOptimizationSettings = ::openBatteryOptimizationSettings,
                            openConfigDocs = ::openConfigDocs,
                            openUrl = ::openUrl,
                            openSillyTavernInBrowser = ::openSillyTavernInBrowser,
                            triggerExport = triggerExport,
                            triggerImport = triggerImport,
                            triggerDiagnosticExport = triggerDiagnosticExport,
                            onLoadCustomZip = {
                                customZipLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/octet-stream"
                                    )
                                )
                            },
                            navigateMainTab = navigateMainTab,
                            openCharacterChatFromCharacterManagement = openCharacterChatFromCharacterManagement,
                            openGroupChat = openGroupChat,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    STAppDialogs(
                        pendingDialogState = pendingDialogState,
                        statusState = statusState,
                        viewModel = viewModel
                    )
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

    private fun openBatteryOptimizationSettings() {
        val packageUri = Uri.fromParts("package", packageName, null)
        val powerManager = getSystemService(PowerManager::class.java)
        val isIgnoringOptimizations = powerManager?.isIgnoringBatteryOptimizations(packageName) == true
        val intentCandidates = listOfNotNull(
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
        )
        val intent = intentCandidates.firstOrNull { it.resolveActivity(packageManager) != null }
            ?: Intent(Settings.ACTION_SETTINGS)
        runCatching { startActivity(intent) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }

    private fun openConfigDocs() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.sillytavern.app/administration/config-yaml/")))
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
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
}

private fun ThemeMode.shouldUseDarkTheme(systemInDarkTheme: Boolean): Boolean = when (this) {
    ThemeMode.AUTO -> systemInDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
