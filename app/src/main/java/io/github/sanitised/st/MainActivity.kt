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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.sanitised.st.ui.navigation.BottomNavItem
import io.github.sanitised.st.ui.navigation.STBottomBar
import io.github.sanitised.st.ui.navigation.STRoutes
import io.github.sanitised.st.ui.screens.CharacterHubScreen
import io.github.sanitised.st.ui.screens.ToolsHubScreen
import io.github.sanitised.st.ui.screens.rememberLocalTavernLibrarySnapshot
import io.github.sanitised.st.ui.theme.STAppTheme
import io.github.sanitised.st.ui.webview.ChatWebViewScreen
import io.github.sanitised.st.ui.webview.WebViewTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.yaml.snakeyaml.Yaml

private val bottomNavItems = listOf(
    BottomNavItem(STRoutes.HOME, "首页", Icons.Filled.Home),
    BottomNavItem(STRoutes.CHAT, "聊天", Icons.AutoMirrored.Filled.Chat),
    BottomNavItem(STRoutes.CHARACTERS, "角色", Icons.Filled.Person),
    BottomNavItem(STRoutes.TOOLS, "工具", Icons.Filled.Build),
    BottomNavItem(STRoutes.SETTINGS, "设置", Icons.Filled.Settings)
)

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
            val useDarkTheme = themeMode.shouldUseDarkTheme(systemInDarkTheme)
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
                pendingDialogState.value = PendingDialog.ConfirmImport(uri)
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
            val navigateMainTab: (String) -> Unit = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            STAppTheme(useDarkTheme = useDarkTheme) {
                Scaffold(
                    bottomBar = {
                        STBottomBar(
                            items = bottomNavItems,
                            currentRoute = currentRoute,
                            onNavigate = navigateMainTab
                        )
                    },
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        NavHost(
                            navController = navController,
                            startDestination = STRoutes.HOME
                        ) {
                            composable(STRoutes.HOME) {
                                STAndroidApp(
                                    status = statusState.value,
                                    onStart = { startNode() },
                                    onStop = { stopNode() },
                                    onOpen = { navigateMainTab(STRoutes.CHAT) },
                                    autoOpenBrowserWhenReady = viewModel.autoOpenBrowserWhenReady.value,
                                    autoOpenBrowserTriggeredForCurrentRun = autoOpenBrowserTriggeredForCurrentRun.value,
                                    onAutoOpenBrowserTriggered = { autoOpenBrowserTriggeredForCurrentRun.value = true },
                                    onShowLogs = { navController.navigate(STRoutes.LOGS) },
                                    onOpenNotificationSettings = { openNotificationSettings() },
                                    onOpenBatterySettings = { openBatteryOptimizationSettings() },
                                    onEditConfig = { navController.navigate(STRoutes.CONFIG) },
                                    showNotificationPrompt = !notificationGrantedState.value,
                                    showBatteryPrompt = showBatteryPrompt,
                                    versionLabel = versionLabel,
                                    stLabel = if (viewModel.isCustomInstalled.value) {
                                        val customLabel = viewModel.customInstallLabel.value
                                        if (customLabel.isNullOrBlank()) {
                                            getString(R.string.sillytavern_custom_version)
                                        } else {
                                            getString(R.string.sillytavern_custom_with_label, customLabel)
                                        }
                                    } else stLabel,
                                    nodeLabel = nodeLabel,
                                    symlinkSupported = symlinkSupported,
                                    onShowLegal = { navController.navigate(STRoutes.LEGAL) },
                                    showAutoCheckOptInPrompt = showAutoCheckOptInPrompt,
                                    onEnableAutoCheck = { viewModel.acceptAutoCheckOptInPrompt() },
                                    onLaterAutoCheck = { viewModel.dismissAutoCheckOptInPrompt() },
                                    onDismissBatteryPrompt = { viewModel.dismissBatteryPrompt() },
                                    showUpdatePrompt = showUpdatePrompt,
                                    updateVersionLabel = viewModel.availableUpdateVersionLabel(),
                                    updateDetails = viewModel.updateBannerMessage.value,
                                    isDownloadingUpdate = viewModel.isDownloadingUpdate.value,
                                    downloadProgressPercent = viewModel.downloadProgressPercent.value,
                                    isUpdateReadyToInstall = isUpdateReadyToInstall,
                                    onUpdatePrimary = {
                                        if (isUpdateReadyToInstall) {
                                            viewModel.installDownloadedUpdate(this@MainActivity)
                                        } else {
                                            viewModel.startAvailableUpdateDownload()
                                        }
                                    },
                                    onUpdateDismiss = { viewModel.dismissAvailableUpdatePrompt() },
                                    onCancelUpdateDownload = { viewModel.cancelUpdateDownload() },
                                    showBackupOperationCard = viewModel.backupOperationCard.value.visible,
                                    backupOperationTitle = viewModel.backupOperationCard.value.title,
                                    backupOperationDetails = viewModel.backupOperationCard.value.details,
                                    backupOperationProgressPercent = viewModel.backupOperationCard.value.progressPercent,
                                    showCustomOperationCard = viewModel.customOperationCard.value.visible,
                                    customOperationTitle = viewModel.customOperationCard.value.title,
                                    customOperationDetails = viewModel.customOperationCard.value.details,
                                    customOperationProgressPercent = viewModel.customOperationCard.value.progressPercent,
                                    customOperationCancelable = viewModel.customOperationCard.value.cancelable,
                                    onCancelCustomOperation = { viewModel.cancelCustomSourceDownload() },
                                    onShowSettings = {
                                        navigateMainTab(STRoutes.SETTINGS)
                                    },
                                    onShowManageSt = { navController.navigate(STRoutes.MANAGE_ST) },
                                    recentChats = librarySnapshot.recentChats,
                                    recentCharacters = librarySnapshot.characters,
                                    onShowCharacters = { navigateMainTab(STRoutes.CHARACTERS) }
                                )
                            }

                            composable(STRoutes.CHAT) {
                                ChatWebViewScreen(
                                    status = statusState.value,
                                    target = WebViewTarget.CHAT,
                                    themeMode = themeMode,
                                    onStartService = { startNode() },
                                    onShowLogs = { navController.navigate(STRoutes.LOGS) },
                                    onBackToHome = { navigateMainTab(STRoutes.HOME) }
                                )
                            }

                            composable(STRoutes.CHARACTERS) {
                                CharacterHubScreen(
                                    characters = librarySnapshot.characters,
                                    onOpenChat = { navigateMainTab(STRoutes.CHAT) }
                                )
                            }

                            composable(STRoutes.TOOLS) {
                                ToolsHubScreen(
                                    serverRunning = statusState.value.state == NodeState.RUNNING ||
                                            statusState.value.state == NodeState.STARTING ||
                                            statusState.value.state == NodeState.STOPPING,
                                    busyMessage = viewModel.busyMessage,
                                    onExportData = triggerExport,
                                    onImportData = triggerImport,
                                    onOpenConfig = { navController.navigate(STRoutes.CONFIG) },
                                    onOpenLogs = { navController.navigate(STRoutes.LOGS) },
                                    onOpenManageSt = { navController.navigate(STRoutes.MANAGE_ST) }
                                )
                            }

                            composable(STRoutes.SETTINGS) {
                                SettingsScreen(
                                    onBack = { navController.popBackStack() },
                                    autoCheckEnabled = viewModel.autoCheckForUpdates.value,
                                    onAutoCheckChanged = { enabled -> viewModel.setAutoCheckForUpdates(enabled) },
                                    autoOpenBrowserEnabled = viewModel.autoOpenBrowserWhenReady.value,
                                    onAutoOpenBrowserChanged = { enabled -> viewModel.setAutoOpenBrowserWhenReady(enabled) },
                                    themeMode = themeMode,
                                    onThemeModeChanged = { mode -> viewModel.setThemeMode(mode) },
                                    isBatteryUnrestricted = batteryUnrestrictedState.value,
                                    onOpenBatterySettings = { openBatteryOptimizationSettings() },
                                    channel = viewModel.updateChannel.value,
                                    onChannelChanged = { channel -> viewModel.setUpdateChannel(channel) },
                                    onCheckNow = { viewModel.checkForUpdates("manual") },
                                    isChecking = viewModel.isCheckingForUpdates.value,
                                    showUpdatePrompt = showUpdatePrompt,
                                    updateVersionLabel = viewModel.availableUpdateVersionLabel(),
                                    updateDetails = viewModel.updateBannerMessage.value,
                                    isDownloadingUpdate = viewModel.isDownloadingUpdate.value,
                                    downloadProgressPercent = viewModel.downloadProgressPercent.value,
                                    isUpdateReadyToInstall = isUpdateReadyToInstall,
                                    onUpdatePrimary = {
                                        if (isUpdateReadyToInstall) {
                                            viewModel.installDownloadedUpdate(this@MainActivity)
                                        } else {
                                            viewModel.startAvailableUpdateDownload()
                                        }
                                    },
                                    onUpdateDismiss = { viewModel.dismissAvailableUpdatePrompt() },
                                    onCancelUpdateDownload = { viewModel.cancelUpdateDownload() },
                                    onOpenInBrowser = {
                                        openSillyTavernInBrowser(statusState.value.port)
                                    }
                                )
                            }

                            composable(STRoutes.LOGS) {
                                BackHandler { navController.popBackStack() }
                                LogsScreen(
                                    onBack = { navController.popBackStack() },
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
                                ManageStScreen(
                                    onBack = { navController.popBackStack() },
                                    isCustomInstalled = viewModel.isCustomInstalled.value,
                                    customInstalledLabel = viewModel.customInstallLabel.value,
                                    serverRunning = statusState.value.state == NodeState.RUNNING ||
                                            statusState.value.state == NodeState.STARTING,
                                    busyMessage = viewModel.busyMessage,
                                    onExport = triggerExport,
                                    onImport = triggerImport,
                                    customRepoInput = viewModel.customRepoInput.value,
                                    onCustomRepoInputChanged = { viewModel.setCustomRepoInput(it) },
                                    onLoadRepoRefs = { viewModel.loadCustomRepoRefs() },
                                    isLoadingRepoRefs = viewModel.isLoadingCustomRefs.value,
                                    customRepoValidationMessage = viewModel.customRepoValidationMessage.value,
                                    featuredRefs = viewModel.customFeaturedRefs.value,
                                    allRefs = viewModel.customAllRefs.value,
                                    selectedRefKey = viewModel.selectedCustomRefKey.value,
                                    onSelectRepoRef = { key -> viewModel.selectCustomRepoRef(key) },
                                    onDownloadAndInstallRef = { viewModel.startCustomRepoInstall() },
                                    customInstallValidationMessage = viewModel.customInstallValidationMessage.value,
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
                                    onRemoveUserData = { pendingDialogState.value = PendingDialog.RemoveUserData }
                                )
                            }
                        }
                    }

                    // Dialogs are rendered above the NavHost
                    when (val dialog = pendingDialogState.value) {
                        PendingDialog.ResetToDefault -> {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { pendingDialogState.value = null },
                                title = { Text(text = getString(R.string.dialog_reset_title)) },
                                text = { Text(text = getString(R.string.dialog_reset_body)) },
                                confirmButton = {
                                    Button(onClick = {
                                        pendingDialogState.value = null
                                        viewModel.resetToDefault()
                                    }) {
                                        Text(text = getString(R.string.reset))
                                    }
                                },
                                dismissButton = {
                                    Button(onClick = { pendingDialogState.value = null }) {
                                        Text(text = getString(R.string.cancel))
                                    }
                                }
                            )
                        }

                        PendingDialog.RemoveUserData -> {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { pendingDialogState.value = null },
                                title = { Text(text = getString(R.string.dialog_remove_data_title)) },
                                text = { Text(text = getString(R.string.dialog_remove_data_body)) },
                                confirmButton = {
                                    Button(onClick = {
                                        pendingDialogState.value = null
                                        viewModel.removeUserData()
                                    }) {
                                        Text(text = getString(R.string.remove))
                                    }
                                },
                                dismissButton = {
                                    Button(onClick = { pendingDialogState.value = null }) {
                                        Text(text = getString(R.string.cancel))
                                    }
                                }
                            )
                        }

                        is PendingDialog.ConfirmImport -> {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { pendingDialogState.value = null },
                                title = { Text(text = getString(R.string.dialog_import_title)) },
                                text = { Text(text = getString(R.string.dialog_import_body)) },
                                confirmButton = {
                                    Button(onClick = {
                                        val importUri = dialog.uri
                                        pendingDialogState.value = null
                                        viewModel.import(importUri)
                                    }) {
                                        Text(text = getString(R.string.import_action))
                                    }
                                },
                                dismissButton = {
                                    Button(onClick = { pendingDialogState.value = null }) {
                                        Text(text = getString(R.string.cancel))
                                    }
                                }
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
            val yamlRoot = configFile.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                Yaml().load<Any?>(reader)
            }
            val rawPort = (yamlRoot as? Map<*, *>)?.get("port")
            val parsedPort = when (rawPort) {
                is Number -> rawPort.toInt()
                is String -> rawPort.trim().toIntOrNull()
                else -> null
            }
            parsedPort?.takeIf { it in 1..65535 } ?: DEFAULT_PORT
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

private sealed interface PendingDialog {
    object ResetToDefault : PendingDialog
    object RemoveUserData : PendingDialog
    data class ConfirmImport(val uri: Uri) : PendingDialog
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
                is PendingDialog.ConfirmImport -> "import:${dialog.uri}"
            }
        },
        restore = { key ->
            when {
                key == "none" -> null
                key == "reset" -> PendingDialog.ResetToDefault
                key == "remove-data" -> PendingDialog.RemoveUserData
                key.startsWith("import:") -> PendingDialog.ConfirmImport(
                    Uri.parse(key.removePrefix("import:"))
                )
                else -> null
            }
        }
    )
}
