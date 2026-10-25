package io.github.sanitised.st.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.sanitised.st.AppPaths
import io.github.sanitised.st.ConfigScreen
import io.github.sanitised.st.LegalDoc
import io.github.sanitised.st.LegalScreen
import io.github.sanitised.st.LicenseTextScreen
import io.github.sanitised.st.LogsScreen
import io.github.sanitised.st.MainViewModel
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.SillyTavernUrl
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.GroupCreateRequest
import io.github.sanitised.st.chat.ChatStore
import io.github.sanitised.st.chat.ChatTarget
import io.github.sanitised.st.chat.GroupChatScreen
import io.github.sanitised.st.chat.GroupMembersScreen
import io.github.sanitised.st.chat.GroupSettingsScreen
import io.github.sanitised.st.chat.NativeChatLoader
import io.github.sanitised.st.chat.NativeChatRuntime
import io.github.sanitised.st.chat.NativeChatScreen
import io.github.sanitised.st.chat.NewGroupScreen
import io.github.sanitised.st.chat.engine.ChatEngine
import io.github.sanitised.st.data.CharacterRepository
import io.github.sanitised.st.data.tavernApi
import io.github.sanitised.st.readLogTail
import io.github.sanitised.st.ui.screens.ChatSeenStore
import io.github.sanitised.st.ui.screens.STAISettingsScreen
import io.github.sanitised.st.ui.screens.STAccountScreen
import io.github.sanitised.st.ui.screens.STAltGreetingsScreen
import io.github.sanitised.st.ui.screens.STApiConnectionScreen
import io.github.sanitised.st.ui.screens.STAppearanceScreen
import io.github.sanitised.st.ui.screens.STAuthorNoteCFGScreen
import io.github.sanitised.st.ui.screens.STBackgroundsScreen
import io.github.sanitised.st.ui.screens.STCharacterAdvancedScreen
import io.github.sanitised.st.ui.screens.STCharacterCreateScreen
import io.github.sanitised.st.ui.screens.STCharacterFormScreen
import io.github.sanitised.st.ui.screens.STCharacterLibraryScreen
import io.github.sanitised.st.ui.screens.STCharacterProfileScreen
import io.github.sanitised.st.ui.screens.STChatBehaviorScreen
import io.github.sanitised.st.ui.screens.STChatItem
import io.github.sanitised.st.ui.screens.STChatListScreen
import io.github.sanitised.st.ui.screens.STExtensionsScreen
import io.github.sanitised.st.ui.screens.STLoginScreen
import io.github.sanitised.st.ui.screens.STLorebookDetailScreen
import io.github.sanitised.st.ui.screens.STMeScreen
import io.github.sanitised.st.ui.screens.STMemoryScreen
import io.github.sanitised.st.ui.screens.STOnboardingScreen
import io.github.sanitised.st.ui.screens.STPastChatsScreen
import io.github.sanitised.st.ui.screens.STPersonaScreen
import io.github.sanitised.st.ui.screens.STProviderDetailScreen
import io.github.sanitised.st.ui.screens.STQuickReplyScreen
import io.github.sanitised.st.ui.screens.STSecretsScreen
import io.github.sanitised.st.ui.screens.STStCoreScreen
import io.github.sanitised.st.ui.screens.STWIGlobalSettingsScreen
import io.github.sanitised.st.ui.screens.STWorldBookManageScreen
import io.github.sanitised.st.ui.screens.STWorldEntryEditScreen
import io.github.sanitised.st.ui.screens.STWorldInfoScreen
import io.github.sanitised.st.ui.screens.chatSeenKey
import io.github.sanitised.st.ui.screens.groupSeenKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Navigation 3 NavDisplay 与全部路由目的地。从 setContent 根作用域抽出成独立组件:
 * 目的地内部的状态读取(statusState.value 等)只重组各自的目的地作用域,
 * 根作用域不再随之整棵重跑。
 */
@Composable
internal fun STAppNavHost(
    navigator: STNavigator,
    statusState: State<NodeStatus>,
    viewModel: MainViewModel,
    chatStore: ChatStore,
    chatEngine: ChatEngine,
    nativeChatLoader: NativeChatLoader,
    nativeChatRuntime: NativeChatRuntime,
    chatSeenStore: ChatSeenStore,
    appPaths: AppPaths,
    characterRepository: CharacterRepository,
    homeChatItems: List<STChatItem>,
    pendingChatTarget: ChatTarget,
    currentStLabel: String,
    nodeLabel: String,
    versionLabel: String,
    legalDocs: List<LegalDoc>,
    batteryUnrestrictedState: State<Boolean>,
    pendingDialogState: MutableState<PendingDialog?>,
    startNode: () -> Unit,
    stopNode: () -> Unit,
    openBatteryOptimizationSettings: () -> Unit,
    openConfigDocs: () -> Unit,
    openUrl: (String) -> Unit,
    openSillyTavernInBrowser: (Int) -> Unit,
    triggerExport: () -> Unit,
    triggerImport: () -> Unit,
    triggerDiagnosticExport: () -> Unit,
    onLoadCustomZip: () -> Unit,
    navigateMainTab: (String) -> Unit,
    openCharacterChatFromCharacterManagement: (String, String?) -> Unit,
    openGroupChat: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    NavDisplay(
        backStack = navigator.backStack,
        onBack = { navigator.goBack() },
        modifier = modifier,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(rememberSaveableStateHolder()),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            homeAndChatEntries(
                navigator = navigator,
                statusState = statusState,
                viewModel = viewModel,
                chatStore = chatStore,
                chatEngine = chatEngine,
                nativeChatLoader = nativeChatLoader,
                nativeChatRuntime = nativeChatRuntime,
                chatSeenStore = chatSeenStore,
                appPaths = appPaths,
                characterRepository = characterRepository,
                homeChatItems = homeChatItems,
                pendingChatTarget = pendingChatTarget,
                currentStLabel = currentStLabel,
                nodeLabel = nodeLabel,
                scope = scope,
                startNode = startNode,
                stopNode = stopNode,
                navigateMainTab = navigateMainTab,
                openCharacterChatFromCharacterManagement = openCharacterChatFromCharacterManagement,
                openGroupChat = openGroupChat
            )

            characterEntries(
                navigator = navigator,
                statusState = statusState,
                viewModel = viewModel,
                characterRepository = characterRepository,
                startNode = startNode,
                openCharacterChatFromCharacterManagement = openCharacterChatFromCharacterManagement
            )

            worldInfoAndToolsEntries(
                navigator = navigator,
                statusState = statusState,
                viewModel = viewModel,
                startNode = startNode
            )

            settingsAndSystemEntries(
                navigator = navigator,
                statusState = statusState,
                viewModel = viewModel,
                appPaths = appPaths,
                currentStLabel = currentStLabel,
                nodeLabel = nodeLabel,
                versionLabel = versionLabel,
                legalDocs = legalDocs,
                batteryUnrestrictedState = batteryUnrestrictedState,
                pendingDialogState = pendingDialogState,
                startNode = startNode,
                stopNode = stopNode,
                openBatteryOptimizationSettings = openBatteryOptimizationSettings,
                openConfigDocs = openConfigDocs,
                openUrl = openUrl,
                openSillyTavernInBrowser = openSillyTavernInBrowser,
                triggerExport = triggerExport,
                triggerImport = triggerImport,
                triggerDiagnosticExport = triggerDiagnosticExport,
                onLoadCustomZip = onLoadCustomZip,
                navigateMainTab = navigateMainTab
            )
        }
    )
}

private fun EntryProviderScope<STNavKey>.homeAndChatEntries(
    navigator: STNavigator,
    statusState: State<NodeStatus>,
    viewModel: MainViewModel,
    chatStore: ChatStore,
    chatEngine: ChatEngine,
    nativeChatLoader: NativeChatLoader,
    nativeChatRuntime: NativeChatRuntime,
    chatSeenStore: ChatSeenStore,
    appPaths: AppPaths,
    characterRepository: CharacterRepository,
    homeChatItems: List<STChatItem>,
    pendingChatTarget: ChatTarget,
    currentStLabel: String,
    nodeLabel: String,
    scope: CoroutineScope,
    startNode: () -> Unit,
    stopNode: () -> Unit,
    navigateMainTab: (String) -> Unit,
    openCharacterChatFromCharacterManagement: (String, String?) -> Unit,
    openGroupChat: (String, String?) -> Unit,
) {
    entry<STKey.Home> {
        // Legacy contract: onNewGroupChat = { navController.navigate("group-chat/new") }
        STChatListScreen(
            status = statusState.value,
            onStart = { startNode() },
            onStop = { stopNode() },
            stLabel = currentStLabel,
            nodeLabel = nodeLabel,
            chatItems = homeChatItems,
            onOpenChat = { chat ->
                openCharacterChatFromCharacterManagement(chat.characterId, chat.chatFile)
            },
            onOpenGroupChat = { chat ->
                openGroupChat(chat.characterId, chat.chatFile)
            },
            onNewChat = {
                navigator.switchRootTab(STKey.Characters)
                viewModel.showTransientMessage("请选择角色开始新对话")
            },
            onNewGroupChat = { navigator.navigate(STKey.NewGroupChat) },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.Chat> { key ->
        // 离开时打「看过」时间戳;若离开时仍在后台生成,
        // 生成完成的更新会晚于该时间戳,列表如实标未读。
        DisposableEffect(Unit) {
            onDispose {
                if (chatStore.avatarUrl.isNotBlank()) {
                    chatSeenStore.markSeen(chatSeenKey(chatStore.avatarUrl, chatStore.chatFile))
                }
            }
        }
        val resolvedTarget = if (!key.useCurrentStore && key.avatar.isNotBlank()) {
            ChatTarget.CharacterChat(key.avatar, key.chatFile)
        } else {
            pendingChatTarget
        }
        NativeChatScreen(
            status = statusState.value,
            target = resolvedTarget,
            store = chatStore,
            engine = chatEngine,
            nativeChatLoadingEnabled = true,
            nativeChatLoader = nativeChatLoader,
            nativeChatRuntime = nativeChatRuntime,
            quickReplyDataRoot = appPaths.dataDir,
            onBackToHome = {
                if (!navigator.goBack()) navigateMainTab(STRoutes.HOME)
            },
            onOpenPastChats = {
                val avatar = chatStore.avatarUrl.ifBlank { key.avatar }
                if (avatar.isNotBlank()) {
                    navigator.navigate(STKey.PastChats(avatar))
                }
            },
            chatBackground = viewModel.chatBackground.value,
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    // route = STRoutes.PAST_CHATS
    entry<STKey.PastChats> { key ->
        val avatar = key.avatar
        if (avatar.isNotBlank()) {
            STPastChatsScreen(
                status = statusState.value,
                baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                avatar = avatar,
                currentChatFile = chatStore.chatFile,
                characterRepository = characterRepository,
                onBack = { navigator.goBack() },
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

    // route = STRoutes.GROUP_CHAT_DETAIL
    entry<STKey.GroupChat> { key ->
        val gid = key.groupId
        val cid = key.chatId
        DisposableEffect(gid) {
            onDispose {
                if (gid.isNotBlank()) chatSeenStore.markSeen(groupSeenKey(gid))
            }
        }
        GroupChatScreen(
            groupId = gid,
            chatId = cid.takeIf { it.isNotBlank() },
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navigator.goBack() },
            onNavigateToSettings = { navigator.navigate(STKey.GroupSettings(gid)) },
            onNavigateToMembers = { navigator.navigate(STKey.GroupMembers(gid)) },
            onNavigateToNewGroup = { navigator.navigate(STKey.NewGroupChat) },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    // composable("group-chat/settings")
    entry<STKey.GroupSettings> { key ->
        GroupSettingsScreen(
            groupId = key.groupId,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navigator.goBack() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.GroupMembers> { key ->
        GroupMembersScreen(
            groupId = key.groupId,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navigator.goBack() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    // composable("group-chat/new")
    entry<STKey.NewGroupChat> {
        val newGroupBaseUrl = SillyTavernUrl.localWebUrl(statusState.value.port)
        val newGroupScope = rememberCoroutineScope()
        var newGroupCharacters by remember { mutableStateOf<List<CharacterSummary>>(emptyList()) }
        var newGroupLoading by remember { mutableStateOf(true) }
        var creatingGroup by remember { mutableStateOf(false) }
        LaunchedEffect(newGroupBaseUrl) {
            newGroupLoading = true
            runCatching { tavernApi(newGroupBaseUrl).listCharacters() }
                .onSuccess { newGroupCharacters = it.sortedBy { c -> c.name.lowercase() } }
                .onFailure { error ->
                    viewModel.showTransientMessage(error.message ?: "角色列表加载失败")
                }
            newGroupLoading = false
        }
        NewGroupScreen(
            characters = newGroupCharacters,
            loading = newGroupLoading,
            baseUrl = newGroupBaseUrl,
            onClose = { navigator.goBack() },
            onCreate = { name, members, strategy ->
                if (creatingGroup) return@NewGroupScreen
                creatingGroup = true
                newGroupScope.launch {
                    runCatching {
                        tavernApi(newGroupBaseUrl).createGroup(
                            GroupCreateRequest(
                                name = name,
                                members = members,
                                activationStrategy = strategy
                            )
                        )
                    }.onSuccess { created ->
                        viewModel.showTransientMessage("已创建群聊「${created.name}」")
                        // 群聊列表页已并入对话页:创建完成直接进入新群聊
                        navigator.goBack()
                        openGroupChat(created.id, null)
                    }.onFailure { error ->
                        viewModel.showTransientMessage(error.message ?: "创建群聊失败")
                    }
                    creatingGroup = false
                }
            }
        )
    }
    // composable("group-chat/end")
}

private fun EntryProviderScope<STNavKey>.characterEntries(
    navigator: STNavigator,
    statusState: State<NodeStatus>,
    viewModel: MainViewModel,
    characterRepository: CharacterRepository,
    startNode: () -> Unit,
    openCharacterChatFromCharacterManagement: (String, String?) -> Unit,
) {
    entry<STKey.Characters> {
        STCharacterLibraryScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            repository = characterRepository,
            onStartService = { startNode() },
            onOpenCharacter = { avatar ->
                navigator.navigate(STKey.CharacterDetail(avatar))
            },
            onOpenChat = { avatar ->
                openCharacterChatFromCharacterManagement(avatar, null)
            },
            onCreateCharacter = {
                navigator.navigate(STKey.CharacterCreate)
            },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.CharacterCreate> {
        STCharacterCreateScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            repository = characterRepository,
            onStartService = { startNode() },
            onBack = { navigator.goBack() },
            onSaved = { avatar ->
                if (navigator.currentKey == STKey.CharacterCreate) {
                    navigator.goBack()
                }
                navigator.navigate(STKey.CharacterDetail(avatar))
            },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.CharacterDetail> { key ->
        val avatar = key.avatar
        if (avatar.isNotBlank()) {
            STCharacterProfileScreen(
                status = statusState.value,
                baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                repository = characterRepository,
                avatar = avatar,
                onStartService = { startNode() },
                onBack = { navigator.goBack() },
                onOpenChat = { chatFile ->
                    openCharacterChatFromCharacterManagement(avatar, chatFile)
                },
                onOpenPastChats = {
                    navigator.navigate(STKey.PastChats(avatar))
                },
                onOpenFullEdit = {
                    navigator.navigate(STKey.CharacterForm(avatar))
                },
                onShowMessage = { message -> viewModel.showTransientMessage(message) }
            )
        }
    }

    entry<STKey.CharacterEdit> { key ->
        val avatar = key.avatar
        if (avatar.isNotBlank()) {
            STCharacterProfileScreen(
                status = statusState.value,
                baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
                repository = characterRepository,
                avatar = avatar,
                onStartService = { startNode() },
                onBack = { navigator.goBack() },
                onOpenChat = { chatFile ->
                    openCharacterChatFromCharacterManagement(avatar, chatFile)
                },
                onOpenPastChats = {
                    navigator.navigate(STKey.PastChats(avatar))
                },
                onOpenFullEdit = {
                    navigator.navigate(STKey.CharacterForm(avatar))
                },
                onShowMessage = { message -> viewModel.showTransientMessage(message) }
            )
        }
    }

    // route = STRoutes.CHAR_FORM
    entry<STKey.CharacterForm> { key ->
        val avatar = key.avatar
        STCharacterFormScreen(
            avatar = avatar,
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            characterRepository = characterRepository,
            onClose = { navigator.goBack() },
            onOpenGreetings = { navigator.navigate(STKey.CharacterGreetings(avatar)) },
            onOpenAdvanced = { navigator.navigate(STKey.CharacterAdvanced(avatar)) },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.CharacterGreetings> { key ->
        STAltGreetingsScreen(
            avatar = key.avatar,
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            characterRepository = characterRepository,
            onBack = { navigator.goBack() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.CharacterAdvanced> { key ->
        STCharacterAdvancedScreen(
            avatar = key.avatar,
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            characterRepository = characterRepository,
            onClose = { navigator.goBack() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }
}

private fun EntryProviderScope<STNavKey>.worldInfoAndToolsEntries(
    navigator: STNavigator,
    statusState: State<NodeStatus>,
    viewModel: MainViewModel,
    startNode: () -> Unit,
) {
    entry<STKey.WorldInfo> {
        STWorldInfoScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onStartService = { startNode() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) },
            onOpenManage = { navigator.navigate(STKey.WorldBookManage) },
            onOpenBook = { name -> navigator.navigate(STKey.LorebookDetail(name)) },
            onOpenGlobalSettings = { navigator.navigate(STKey.WiGlobalSettings) }
        )
    }

    entry<STKey.WorldBookManage> {
        STWorldBookManageScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navigator.goBack() },
            onOpenBook = { name -> navigator.navigate(STKey.LorebookDetail(name)) },
            onOpenGlobalSettings = { navigator.navigate(STKey.WiGlobalSettings) },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.LorebookDetail> { key ->
        val name = key.bookName
        STLorebookDetailScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            bookName = name,
            onBack = { navigator.goBack() },
            onOpenEntry = { uid -> navigator.navigate(STKey.WorldEntryEdit(uid = uid, bookName = name)) },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.WorldEntryEdit> { key ->
        STWorldEntryEditScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            entryUid = key.uid,
            bookName = key.bookName,
            onClose = { navigator.goBack() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.WiGlobalSettings> {
        STWIGlobalSettingsScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navigator.goBack() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.Persona> {
        STPersonaScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navigator.goBack() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.Presets> {
        STAISettingsScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navigator.goBack() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) },
            onSettingsChanged = { }
        )
    }

    entry<STKey.Connections> {
        STApiConnectionScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navigator.goBack() },
            onOpenSecrets = { navigator.navigate(STKey.Secrets) },
            onShowMessage = { message -> viewModel.showTransientMessage(message) },
            onOpenProviderDetail = { providerId ->
                navigator.navigate(STKey.ProviderDetail(providerId))
            },
            onSettingsChanged = { }
        )
    }

    entry<STKey.ProviderDetail> { key ->
        val providerId = key.providerId.ifBlank { "anthropic" }
        STProviderDetailScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            providerId = providerId,
            onBack = { navigator.goBack() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) },
            onSettingsChanged = { }
        )
    }

    entry<STKey.ChatBackups> {
        STMemoryScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navigator.goBack() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.Secrets> {
        STSecretsScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navigator.goBack() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.Extensions> {
        STExtensionsScreen(
            onBack = { navigator.goBack() },
            onOpenQuickReplies = { navigator.navigate(STKey.QuickReplies) },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.AuthorNote> {
        STAuthorNoteCFGScreen(
            onBack = { navigator.goBack() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.QuickReplies> {
        STQuickReplyScreen(
            onBack = { navigator.goBack() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }
}

private fun EntryProviderScope<STNavKey>.settingsAndSystemEntries(
    navigator: STNavigator,
    statusState: State<NodeStatus>,
    viewModel: MainViewModel,
    appPaths: AppPaths,
    currentStLabel: String,
    nodeLabel: String,
    versionLabel: String,
    legalDocs: List<LegalDoc>,
    batteryUnrestrictedState: State<Boolean>,
    pendingDialogState: MutableState<PendingDialog?>,
    startNode: () -> Unit,
    stopNode: () -> Unit,
    openBatteryOptimizationSettings: () -> Unit,
    openConfigDocs: () -> Unit,
    openUrl: (String) -> Unit,
    openSillyTavernInBrowser: (Int) -> Unit,
    triggerExport: () -> Unit,
    triggerImport: () -> Unit,
    triggerDiagnosticExport: () -> Unit,
    onLoadCustomZip: () -> Unit,
    navigateMainTab: (String) -> Unit,
) {
    entry<STKey.Settings> {
        STMeScreen(
            autoCheckEnabled = viewModel.autoCheckForUpdates.value,
            onAutoCheckChanged = { enabled -> viewModel.setAutoCheckForUpdates(enabled) },
            autoOpenBrowserEnabled = viewModel.autoOpenBrowserWhenReady.value,
            onAutoOpenBrowserChanged = { enabled -> viewModel.setAutoOpenBrowserWhenReady(enabled) },
            isBatteryUnrestricted = batteryUnrestrictedState.value,
            onOpenBatterySettings = { openBatteryOptimizationSettings() },
            channel = viewModel.updateChannel.value,
            onChannelChanged = { channel -> viewModel.setUpdateChannel(channel) },
            onCheckNow = { viewModel.checkForUpdates("manual") },
            isChecking = viewModel.isCheckingForUpdates.value,
            vibrationFeedback = viewModel.vibrationFeedback.value,
            onVibrationFeedbackChanged = { enabled -> viewModel.setVibrationFeedback(enabled) },
            secondConfirmation = viewModel.secondConfirmation.value,
            onSecondConfirmationChanged = { enabled -> viewModel.setSecondConfirmation(enabled) },
            swipeDrawer = viewModel.swipeDrawer.value,
            onSwipeDrawerChanged = { enabled -> viewModel.setSwipeDrawer(enabled) },
            developerMode = viewModel.developerMode.value,
            onDeveloperModeChanged = { enabled -> viewModel.setDeveloperMode(enabled) },
            onOpenWorldInfo = { navigator.switchRootTab(STKey.WorldInfo) },
            onOpenPersona = { navigator.navigate(STKey.Persona) },
            onOpenPresets = { navigator.navigate(STKey.Presets) },
            onOpenConnections = { navigator.navigate(STKey.Connections) },
            onOpenChatBackups = { navigator.navigate(STKey.ChatBackups) },
            onOpenLogs = { navigator.navigate(STKey.Logs) },
            onOpenConfig = { navigator.navigate(STKey.Config) },
            onOpenManageSt = { navigator.navigate(STKey.ManageSt) },
            onOpenSecrets = { navigator.navigate(STKey.Secrets) },
            onOpenExtensions = { navigator.navigate(STKey.Extensions) },
            onOpenAppearance = { navigator.navigate(STKey.Appearance) },
            onOpenAccount = { navigator.navigate(STKey.Account) },
            onOpenBackgrounds = { navigator.navigate(STKey.AppearanceBackgrounds) },
            onOpenChatBehavior = { navigator.navigate(STKey.AppearanceChatBehavior) },
            appVersion = versionLabel,
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.Login> {
        STLoginScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onClose = { if (!navigator.goBack()) navigateMainTab(STRoutes.HOME) },
            onLoggedIn = {
                navigator.switchRootTab(STKey.Home)
            },
            onOnboarding = { navigator.navigate(STKey.Onboarding) }
        )
    }

    entry<STKey.Onboarding> {
        val goHome: () -> Unit = {
            navigator.switchRootTab(STKey.Home)
        }
        STOnboardingScreen(onFinish = goHome, onSkip = goHome)
    }

    entry<STKey.Account> {
        STAccountScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navigator.goBack() },
            onLogout = {
                navigateMainTab(STRoutes.LOGIN)
            },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.AppearanceBackgrounds> {
        STBackgroundsScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navigator.goBack() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) },
            chatBackground = viewModel.chatBackground.value,
            onChatBackgroundChanged = { value -> viewModel.setChatBackground(value) }
        )
    }

    entry<STKey.AppearanceChatBehavior> {
        STChatBehaviorScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navigator.goBack() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    @Composable
    fun RenderAppearanceScreen() {
        STAppearanceScreen(
            themeMode = viewModel.themeMode.value,
            onThemeModeChanged = { mode -> viewModel.setThemeMode(mode) },
            colorSource = viewModel.themeColorSource.value,
            onColorSourceChanged = { source -> viewModel.setThemeColorSource(source) },
            fontSize = viewModel.fontSize.value,
            onFontSizeChanged = { size -> viewModel.setFontSize(size) },
            bubbleStyle = viewModel.bubbleStyle.value,
            onBubbleStyleChanged = { enabled -> viewModel.setBubbleStyle(enabled) },
            reduceMotion = viewModel.reduceMotion.value,
            onReduceMotionChanged = { enabled -> viewModel.setReduceMotion(enabled) },
            onBack = { navigator.goBack() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.Appearance> {
        RenderAppearanceScreen()
    }

    entry<STKey.AppearanceThemeColor> {
        RenderAppearanceScreen()
    }

    entry<STKey.AppearanceTypography> {
        RenderAppearanceScreen()
    }

    entry<STKey.AppearanceChatStyle> {
        RenderAppearanceScreen()
    }

    entry<STKey.Logs> {
        var stdoutLog by remember { mutableStateOf("") }
        var stderrLog by remember { mutableStateOf("") }
        var serviceLog by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            val logsDir = appPaths.logsDir
            while (true) {
                val (out, err, svc) = withContext(Dispatchers.IO) {
                    Triple(
                        readLogTail(File(logsDir, "node_stdout.log"), 16 * 1024),
                        readLogTail(File(logsDir, "node_stderr.log"), 16 * 1024),
                        readLogTail(File(logsDir, "service.log"), 16 * 1024)
                    )
                }
                stdoutLog = out
                stderrLog = err
                serviceLog = svc
                delay(1000)
            }
        }
        LogsScreen(
            onBack = { navigator.goBack() },
            onExportDiagnostics = triggerDiagnosticExport,
            stdoutLog = stdoutLog,
            stderrLog = stderrLog,
            serviceLog = serviceLog
        )
    }

    entry<STKey.Config> {
        ConfigScreen(
            onBack = { navigator.goBack() },
            onOpenDocs = { openConfigDocs() },
            canEdit = statusState.value.state == NodeState.STOPPED || statusState.value.state == NodeState.ERROR,
            configFile = appPaths.configFile,
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    entry<STKey.Legal> {
        LegalScreen(
            onBack = { navigator.goBack() },
            onOpenUrl = { url -> openUrl(url) },
            legalDocs = legalDocs,
            onOpenLicense = { doc ->
                navigator.navigate(STKey.License(doc.assetPath))
            }
        )
    }

    entry<STKey.License> { key ->
        val doc = legalDocs.firstOrNull { it.assetPath == key.assetPath }
        if (doc != null) {
            LicenseTextScreen(
                onBack = { navigator.goBack() },
                doc = doc
            )
        }
    }

    entry<STKey.ManageSt> {
        STStCoreScreen(
            onBack = { navigator.goBack() },
            status = statusState.value,
            stLabel = currentStLabel,
            nodeLabel = nodeLabel,
            isCustomInstalled = viewModel.isCustomInstalled.value,
            customInstalledLabel = viewModel.customInstallLabel.value,
            busyMessage = viewModel.busyMessage,
            onStartService = { startNode() },
            onStopService = { stopNode() },
            onOpenBrowser = { openSillyTavernInBrowser(statusState.value.port) },
            onShowLogs = { navigator.navigate(STKey.Logs) },
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
            onLoadCustomZip = onLoadCustomZip,
            onResetToDefault = { pendingDialogState.value = PendingDialog.ResetToDefault },
            onRemoveUserData = { pendingDialogState.value = PendingDialog.RemoveUserData },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }
}

internal fun chatTargetSaver(): Saver<ChatTarget, String> {
    return Saver(
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
