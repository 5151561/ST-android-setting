package io.github.sanitised.st

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sanitised.st.api.SettingsSnapshot
import io.github.sanitised.st.api.TavernCoreClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    internal val busyOperation = mutableStateOf<BusyOperation?>(null)
    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val snackbarMessages = _snackbarMessages.asSharedFlow()

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

    val isCustomInstalled: MutableState<Boolean> = customInstallManager.isCustomInstalled
    val customInstallLabel: MutableState<String?> = customInstallManager.customInstallLabel
    val customRepoInput: MutableState<String> = customInstallManager.customRepoInput
    val isLoadingCustomRefs: MutableState<Boolean> = customInstallManager.isLoadingCustomRefs
    val customRepoValidationMessage: MutableState<String> = customInstallManager.customRepoValidationMessage
    val customInstallValidationMessage: MutableState<String> = customInstallManager.customInstallValidationMessage
    val customFeaturedRefs: MutableState<List<CustomRepoRefOption>> = customInstallManager.customFeaturedRefs
    val customAllRefs: MutableState<List<CustomRepoRefOption>> = customInstallManager.customAllRefs
    val selectedCustomRefKey: MutableState<String?> = customInstallManager.selectedCustomRefKey
    val customOperationCard: MutableState<OperationCardState> = customInstallManager.customOperationCard
    val customOperationCardAnchor: MutableState<CustomOperationAnchor> = customInstallManager.customOperationCardAnchor
    val backupOperationCard: MutableState<OperationCardState> = backupManager.backupOperationCard
    val backupOperationCardAnchor: MutableState<BackupOperationAnchor> = backupManager.backupOperationCardAnchor
    val settingsSnapshots = mutableStateOf<List<SettingsSnapshot>>(emptyList())
    val settingsSnapshotsLoading = mutableStateOf(false)
    val settingsSnapshotMessage = mutableStateOf("")

    val bubbleStyle: MutableState<Boolean> = updateManager.bubbleStyle
    val vibrationFeedback: MutableState<Boolean> = updateManager.vibrationFeedback
    val secondConfirmation: MutableState<Boolean> = updateManager.secondConfirmation
    val swipeDrawer: MutableState<Boolean> = updateManager.swipeDrawer
    val developerMode: MutableState<Boolean> = updateManager.developerMode
    val fontSize: MutableState<Float> = updateManager.fontSize
    val reduceMotion: MutableState<Boolean> = updateManager.reduceMotion
    val chatBackground: MutableState<String> = updateManager.chatBackground
    val autoCheckForUpdates: MutableState<Boolean> = updateManager.autoCheckForUpdates
    val autoOpenBrowserWhenReady: MutableState<Boolean> = updateManager.autoOpenBrowserWhenReady
    val autoStartService: MutableState<Boolean> = updateManager.autoStartService
    val themeMode: MutableState<ThemeMode> = updateManager.themeMode
    val themeColorSource: MutableState<ThemeColorSource> = updateManager.themeColorSource
    val updateChannel: MutableState<UpdateChannel> = updateManager.updateChannel
    val isCheckingForUpdates: MutableState<Boolean> = updateManager.isCheckingForUpdates
    val isDownloadingUpdate: MutableState<Boolean> = updateManager.isDownloadingUpdate
    val downloadProgressPercent: MutableState<Int?> = updateManager.downloadProgressPercent
    val updateBannerMessage: MutableState<String> = updateManager.updateBannerMessage

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
        settingsSnapshotsLoading.value = true
        settingsSnapshotMessage.value = ""
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    TavernCoreClient(baseUrl = SillyTavernUrl.localWebUrl(port)).listSettingsSnapshots()
                }
            }
            settingsSnapshotsLoading.value = false
            result
                .onSuccess { snapshots ->
                    settingsSnapshots.value = snapshots.sortedByDescending { it.date }
                    settingsSnapshotMessage.value = getApplication<Application>().getString(
                        R.string.settings_snapshot_loaded,
                        snapshots.size
                    )
                }
                .onFailure { error ->
                    settingsSnapshotMessage.value = getApplication<Application>().getString(
                        R.string.settings_snapshot_failed,
                        error.message ?: getApplication<Application>().getString(R.string.unknown_error)
                    )
                }
        }
    }

    fun createSettingsSnapshot(port: Int) {
        settingsSnapshotsLoading.value = true
        settingsSnapshotMessage.value = ""
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    TavernCoreClient(baseUrl = SillyTavernUrl.localWebUrl(port)).makeSettingsSnapshot()
                }
            }
            settingsSnapshotsLoading.value = false
            result
                .onSuccess {
                    settingsSnapshotMessage.value = getApplication<Application>().getString(R.string.settings_snapshot_created)
                    refreshSettingsSnapshots(port)
                }
                .onFailure { error ->
                    settingsSnapshotMessage.value = getApplication<Application>().getString(
                        R.string.settings_snapshot_failed,
                        error.message ?: getApplication<Application>().getString(R.string.unknown_error)
                    )
                }
        }
    }

    fun restoreSettingsSnapshot(port: Int, name: String) {
        settingsSnapshotsLoading.value = true
        settingsSnapshotMessage.value = ""
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    TavernCoreClient(baseUrl = SillyTavernUrl.localWebUrl(port)).restoreSettingsSnapshot(name)
                }
            }
            settingsSnapshotsLoading.value = false
            result
                .onSuccess {
                    settingsSnapshotMessage.value = getApplication<Application>().getString(R.string.settings_snapshot_restored)
                    refreshSettingsSnapshots(port)
                }
                .onFailure { error ->
                    settingsSnapshotMessage.value = getApplication<Application>().getString(
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

    private fun postUserMessage(message: String) {
        if (message.isBlank()) return
        if (!_snackbarMessages.tryEmit(message)) {
            viewModelScope.launch {
                _snackbarMessages.emit(message)
            }
        }
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
