package io.github.sanitised.st

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sanitised.st.api.SettingsSnapshot
import io.github.sanitised.st.api.TavernCoreClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    internal val busyOperation = mutableStateOf<BusyOperation?>(null)
    private val _userMessage = MutableStateFlow<AppUiMessage?>(null)
    val userMessage = _userMessage.asStateFlow()
    private var userMessageId = 0L

    // Updated by MainActivity when the service connection changes.
    var nodeService: NodeService? = null

    private val backupManager = BackupManager(
        application = application,
        scope = viewModelScope,
        setBusyOperation = { busyOperation.value = it },
        getNodeService = { nodeService },
        appendServiceLog = { message -> appendServiceLog(message) }
    )

    private val customInstallManager = CustomInstallManager(
        application = application,
        scope = viewModelScope,
        getBusyOperation = { busyOperation.value },
        setBusyOperation = { busyOperation.value = it },
        postUserMessage = { message -> postUserMessage(message) },
        appendServiceLog = { message -> appendServiceLog(message) }
    )

    private val updateManager = UpdateManager(
        application = application,
        scope = viewModelScope,
        postUserMessage = { message -> postUserMessage(message) },
        appendServiceLog = { message -> appendServiceLog(message) }
    )

    private val batteryPromptManager = BatteryPromptManager(
        application = application,
        postUserMessage = { message -> postUserMessage(message) }
    )

    val isCustomInstalled: State<Boolean> = customInstallManager.isCustomInstalled
    val customInstallLabel: State<String?> = customInstallManager.customInstallLabel
    val customRepoInput: State<String> = customInstallManager.customRepoInput
    val isLoadingCustomRefs: State<Boolean> = customInstallManager.isLoadingCustomRefs
    val customRepoValidationMessage: State<String> = customInstallManager.customRepoValidationMessage
    val customInstallValidationMessage: State<String> = customInstallManager.customInstallValidationMessage
    val customFeaturedRefs: State<List<CustomRepoRefOption>> = customInstallManager.customFeaturedRefs
    val customAllRefs: State<List<CustomRepoRefOption>> = customInstallManager.customAllRefs
    val selectedCustomRefKey: State<String?> = customInstallManager.selectedCustomRefKey
    val customOperationCard: State<OperationCardState> = customInstallManager.customOperationCard
    val customOperationCardAnchor: State<CustomOperationAnchor> = customInstallManager.customOperationCardAnchor
    val backupOperationCard: State<OperationCardState> = backupManager.backupOperationCard
    val backupOperationCardAnchor: State<BackupOperationAnchor> = backupManager.backupOperationCardAnchor
    private val _settingsSnapshots = mutableStateOf<List<SettingsSnapshot>>(emptyList())
    val settingsSnapshots: State<List<SettingsSnapshot>> = _settingsSnapshots
    private val _settingsSnapshotsLoading = mutableStateOf(false)
    val settingsSnapshotsLoading: State<Boolean> = _settingsSnapshotsLoading
    private val _settingsSnapshotMessage = mutableStateOf("")
    val settingsSnapshotMessage: State<String> = _settingsSnapshotMessage

    val bubbleStyle: State<Boolean> = updateManager.bubbleStyle
    val vibrationFeedback: State<Boolean> = updateManager.vibrationFeedback
    val secondConfirmation: State<Boolean> = updateManager.secondConfirmation
    val swipeDrawer: State<Boolean> = updateManager.swipeDrawer
    val developerMode: State<Boolean> = updateManager.developerMode
    val fontSize: State<Float> = updateManager.fontSize
    val reduceMotion: State<Boolean> = updateManager.reduceMotion
    val chatBackground: State<String> = updateManager.chatBackground
    val autoCheckForUpdates: State<Boolean> = updateManager.autoCheckForUpdates
    val autoOpenBrowserWhenReady: State<Boolean> = updateManager.autoOpenBrowserWhenReady
    val autoStartService: State<Boolean> = updateManager.autoStartService
    val themeMode: State<ThemeMode> = updateManager.themeMode
    val themeColorSource: State<ThemeColorSource> = updateManager.themeColorSource
    val updateChannel: State<UpdateChannel> = updateManager.updateChannel
    val isCheckingForUpdates: State<Boolean> = updateManager.isCheckingForUpdates
    val isDownloadingUpdate: State<Boolean> = updateManager.isDownloadingUpdate
    val downloadProgressPercent: State<Int?> = updateManager.downloadProgressPercent
    val updateBannerMessage: State<String> = updateManager.updateBannerMessage

    override fun onCleared() {
        updateManager.onCleared()
        customInstallManager.onCleared()
        super.onCleared()
    }

    val busyMessage: String
        get() = when (busyOperation.value) {
            BusyOperation.EXPORTING -> getApplication<Application>().getString(R.string.busy_exporting_data)
            BusyOperation.IMPORTING -> getApplication<Application>().getString(R.string.busy_importing_data)
            BusyOperation.INSTALLING -> getApplication<Application>().getString(R.string.busy_installing_custom_st)
            BusyOperation.RESETTING -> getApplication<Application>().getString(R.string.busy_resetting_default)
            BusyOperation.REMOVING_DATA -> getApplication<Application>().getString(R.string.busy_removing_data)
            BusyOperation.DOWNLOADING_CUSTOM_SOURCE -> getApplication<Application>().getString(R.string.busy_downloading_custom_source)
            null -> ""
        }

    fun export(uri: Uri) {
        backupManager.export(uri)
    }

    fun import(uri: Uri) {
        backupManager.import(uri)
    }

    fun refreshSettingsSnapshots(port: Int) {
        _settingsSnapshotsLoading.value = true
        _settingsSnapshotMessage.value = ""
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    TavernCoreClient(baseUrl = SillyTavernUrl.localWebUrl(port)).listSettingsSnapshots()
                }
            }
            _settingsSnapshotsLoading.value = false
            result
                .onSuccess { snapshots ->
                    _settingsSnapshots.value = snapshots.sortedByDescending { it.date }
                    _settingsSnapshotMessage.value = getApplication<Application>().getString(
                        R.string.settings_snapshot_loaded,
                        snapshots.size
                    )
                }
                .onFailure { error ->
                    _settingsSnapshotMessage.value = getApplication<Application>().getString(
                        R.string.settings_snapshot_failed,
                        error.message ?: getApplication<Application>().getString(R.string.unknown_error)
                    )
                }
        }
    }

    fun createSettingsSnapshot(port: Int) {
        _settingsSnapshotsLoading.value = true
        _settingsSnapshotMessage.value = ""
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    TavernCoreClient(baseUrl = SillyTavernUrl.localWebUrl(port)).makeSettingsSnapshot()
                }
            }
            _settingsSnapshotsLoading.value = false
            result
                .onSuccess {
                    _settingsSnapshotMessage.value = getApplication<Application>().getString(R.string.settings_snapshot_created)
                    refreshSettingsSnapshots(port)
                }
                .onFailure { error ->
                    _settingsSnapshotMessage.value = getApplication<Application>().getString(
                        R.string.settings_snapshot_failed,
                        error.message ?: getApplication<Application>().getString(R.string.unknown_error)
                    )
                }
        }
    }

    fun restoreSettingsSnapshot(port: Int, name: String) {
        _settingsSnapshotsLoading.value = true
        _settingsSnapshotMessage.value = ""
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    TavernCoreClient(baseUrl = SillyTavernUrl.localWebUrl(port)).restoreSettingsSnapshot(name)
                }
            }
            _settingsSnapshotsLoading.value = false
            result
                .onSuccess {
                    _settingsSnapshotMessage.value = getApplication<Application>().getString(R.string.settings_snapshot_restored)
                    refreshSettingsSnapshots(port)
                }
                .onFailure { error ->
                    _settingsSnapshotMessage.value = getApplication<Application>().getString(
                        R.string.settings_snapshot_failed,
                        error.message ?: getApplication<Application>().getString(R.string.unknown_error)
                    )
                }
        }
    }

    fun exportDiagnostics(
        uri: Uri,
        appVersion: String,
        stLabel: String,
        nodeLabel: String,
        status: NodeStatus
    ) {
        val application = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val paths = AppPaths(application)
                    val output = application.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException(application.getString(R.string.diagnostics_export_open_failed))
                    output.use { stream ->
                        DiagnosticExporter.export(
                            DiagnosticExportRequest(
                                appVersion = appVersion,
                                stLabel = stLabel,
                                nodeLabel = nodeLabel,
                                generatedAtEpochMs = System.currentTimeMillis(),
                                status = status,
                                logsDir = paths.logsDir,
                                configFile = paths.configFile,
                                stDir = paths.stDir,
                                dataDir = paths.dataDir,
                                outputStream = stream
                            )
                        )
                    }
                }
            }
            result
                .onSuccess { postUserMessage(application.getString(R.string.diagnostics_export_complete)) }
                .onFailure { error ->
                    postUserMessage(
                        application.getString(
                            R.string.diagnostics_export_failed,
                            error.message ?: application.getString(R.string.unknown_error)
                        )
                    )
                }
        }
    }

    fun installCustomZip(uri: Uri) {
        customInstallManager.installCustomZip(uri)
    }

    fun resetToDefault() {
        customInstallManager.resetToDefault()
    }

    fun setCustomRepoInput(value: String) {
        customInstallManager.setCustomRepoInput(value)
    }

    fun selectCustomRepoRef(key: String) {
        customInstallManager.selectCustomRepoRef(key)
    }

    fun loadCustomRepoRefs() {
        customInstallManager.loadCustomRepoRefs()
    }

    fun startCustomRepoInstall() {
        customInstallManager.startCustomRepoInstall()
    }

    fun cancelCustomSourceDownload() {
        customInstallManager.cancelCustomSourceDownload()
    }

    fun removeUserData() {
        customInstallManager.removeUserData()
    }

    fun setBubbleStyle(enabled: Boolean) {
        updateManager.setBubbleStyle(enabled)
    }

    fun setVibrationFeedback(enabled: Boolean) {
        updateManager.setVibrationFeedback(enabled)
    }

    fun setSecondConfirmation(enabled: Boolean) {
        updateManager.setSecondConfirmation(enabled)
    }

    fun setSwipeDrawer(enabled: Boolean) {
        updateManager.setSwipeDrawer(enabled)
    }

    fun setDeveloperMode(enabled: Boolean) {
        updateManager.setDeveloperMode(enabled)
    }

    fun setFontSize(size: Float) {
        updateManager.setFontSize(size)
    }

    fun setReduceMotion(enabled: Boolean) {
        updateManager.setReduceMotion(enabled)
    }

    fun setChatBackground(pathOrUrl: String) {
        updateManager.setChatBackground(pathOrUrl)
    }

    fun setAutoCheckForUpdates(enabled: Boolean) {
        updateManager.setAutoCheckForUpdates(enabled)
    }

    fun setAutoOpenBrowserWhenReady(enabled: Boolean) {
        updateManager.setAutoOpenBrowserWhenReady(enabled)
    }

    fun setAutoStartService(enabled: Boolean) {
        updateManager.setAutoStartService(enabled)
    }

    fun setThemeMode(mode: ThemeMode) {
        updateManager.setThemeMode(mode)
    }

    fun setThemeColorSource(source: ThemeColorSource) {
        updateManager.setThemeColorSource(source)
    }

    fun setUpdateChannel(channel: UpdateChannel) {
        updateManager.setUpdateChannel(channel)
    }

    fun maybeAutoCheckForUpdates() {
        updateManager.maybeAutoCheckForUpdates()
    }

    fun shouldShowAutoCheckOptInPrompt(): Boolean {
        return updateManager.shouldShowAutoCheckOptInPrompt()
    }

    fun acceptAutoCheckOptInPrompt() {
        updateManager.acceptAutoCheckOptInPrompt()
    }

    fun dismissAutoCheckOptInPrompt() {
        updateManager.dismissAutoCheckOptInPrompt()
    }

    fun shouldShowUpdatePrompt(): Boolean {
        return updateManager.shouldShowUpdatePrompt()
    }

    fun availableUpdateVersionLabel(): String {
        return updateManager.availableUpdateVersionLabel()
    }

    fun isAvailableUpdateDownloaded(): Boolean {
        return updateManager.isAvailableUpdateDownloaded()
    }

    fun dismissAvailableUpdatePrompt() {
        updateManager.dismissAvailableUpdatePrompt()
    }

    fun startAvailableUpdateDownload() {
        updateManager.startAvailableUpdateDownload()
    }

    fun cancelUpdateDownload() {
        updateManager.cancelUpdateDownload()
    }

    fun installDownloadedUpdate(context: Context) {
        updateManager.installDownloadedUpdate(context)
    }

    fun checkForUpdates(reason: String = "manual") {
        updateManager.checkForUpdates(reason)
    }

    fun shouldShowBatteryPrompt(isBatteryUnrestricted: Boolean): Boolean {
        return batteryPromptManager.shouldShowPrompt(isBatteryUnrestricted)
    }

    fun dismissBatteryPrompt() {
        batteryPromptManager.dismissPrompt()
    }

    fun showTransientMessage(message: String) {
        postUserMessage(message)
    }

    fun messageShown(id: Long) {
        if (_userMessage.value?.id == id) {
            _userMessage.value = null
        }
    }

    private fun postUserMessage(message: String) {
        if (message.isBlank()) return
        userMessageId += 1
        _userMessage.value = AppUiMessage(userMessageId, message)
    }

    private suspend fun appendServiceLog(message: String) {
        withContext(Dispatchers.IO) {
            val logsDir = AppPaths(getApplication<Application>()).logsDir
            if (!logsDir.exists()) logsDir.mkdirs()
            val logFile = File(logsDir, "service.log")
            logFile.appendText(formatServiceLogLine(message), Charsets.UTF_8)
        }
    }
}
