package io.github.sanitised.st.ui.navigation

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
import io.github.sanitised.st.data.tavernApi
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * NavHost 与全部路由目的地。从 setContent 根作用域抽出成独立组件:
 * 目的地内部的状态读取(statusState.value 等)只重组各自的目的地作用域,
 * 根作用域不再随之整棵重跑。
 */
@Composable
internal fun STAppNavHost(
    navController: NavHostController,
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

    NavHost(
        navController = navController,
        startDestination = STRoutes.HOME,
        modifier = modifier
    ) {
        homeAndChatGraph(
            navController = navController,
            statusState = statusState,
            viewModel = viewModel,
            chatStore = chatStore,
            chatEngine = chatEngine,
            nativeChatLoader = nativeChatLoader,
            nativeChatRuntime = nativeChatRuntime,
            chatSeenStore = chatSeenStore,
            appPaths = appPaths,
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

        characterGraph(
            navController = navController,
            statusState = statusState,
            viewModel = viewModel,
            characterRepository = characterRepository,
            startNode = startNode,
            openCharacterChatFromCharacterManagement = openCharacterChatFromCharacterManagement
        )

        worldInfoAndToolsGraph(
            navController = navController,
            statusState = statusState,
            viewModel = viewModel,
            startNode = startNode
        )

        settingsAndSystemGraph(
            navController = navController,
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
}

private fun NavGraphBuilder.homeAndChatGraph(
    navController: NavHostController,
    statusState: State<NodeStatus>,
    viewModel: MainViewModel,
    chatStore: ChatStore,
    chatEngine: ChatEngine,
    nativeChatLoader: NativeChatLoader,
    nativeChatRuntime: NativeChatRuntime,
    chatSeenStore: ChatSeenStore,
    appPaths: AppPaths,
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
    composable(STRoutes.HOME) {
        // Replaces the old STAndroidApp dashboard with the prototype ChatListScreen.
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
                navController.navigate(STRoutes.CHARACTERS)
                viewModel.showTransientMessage("请选择角色开始新对话")
            },
            onNewGroupChat = { navController.navigate("group-chat/new") },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    composable(STRoutes.CHAT) {
        // 离开时打「看过」时间戳;若离开时仍在后台生成,
        // 生成完成的更新会晚于该时间戳,列表如实标未读。
        DisposableEffect(Unit) {
            onDispose {
                if (chatStore.avatarUrl.isNotBlank()) {
                    chatSeenStore.markSeen(chatSeenKey(chatStore.avatarUrl, chatStore.chatFile))
                }
            }
        }
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
                if (!navController.popBackStackSafely()) navigateMainTab(STRoutes.HOME)
            },
            onOpenPastChats = {
                val avatar = chatStore.avatarUrl
                if (avatar.isNotBlank()) {
                    navController.navigate(STRoutes.pastChats(avatar))
                }
            },
            chatBackground = viewModel.chatBackground.value,
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
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
                onBack = { navController.popBackStackSafely() },
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
        route = STRoutes.GROUP_CHAT_DETAIL,
        arguments = listOf(
            navArgument("groupId") { type = NavType.StringType },
            navArgument("chatId") {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) { backStackEntry ->
        BackHandler { navController.popBackStackSafely() }
        val gid = backStackEntry.arguments?.getString("groupId").orEmpty()
        val cid = backStackEntry.arguments?.getString("chatId").orEmpty()
        DisposableEffect(gid) {
            onDispose {
                if (gid.isNotBlank()) chatSeenStore.markSeen(groupSeenKey(gid))
            }
        }
        GroupChatScreen(
            groupId = gid,
            chatId = cid.takeIf { it.isNotBlank() },
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navController.popBackStackSafely() },
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
        BackHandler { navController.popBackStackSafely() }
        GroupSettingsScreen(
            groupId = backStackEntry.arguments?.getString("groupId").orEmpty(),
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navController.popBackStackSafely() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    composable(
        route = STRoutes.GROUP_MEMBERS,
        arguments = listOf(navArgument("groupId") { type = NavType.StringType })
    ) { backStackEntry ->
        BackHandler { navController.popBackStackSafely() }
        GroupMembersScreen(
            groupId = backStackEntry.arguments?.getString("groupId").orEmpty(),
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navController.popBackStackSafely() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    composable("group-chat/new") {
        BackHandler { navController.popBackStackSafely() }
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
            onClose = { navController.popBackStackSafely() },
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
                        navController.popBackStackSafely()
                        openGroupChat(created.id, null)
                    }.onFailure { error ->
                        viewModel.showTransientMessage(error.message ?: "创建群聊失败")
                    }
                    creatingGroup = false
                }
            }
        )
    }
}

private fun NavGraphBuilder.characterGraph(
    navController: NavHostController,
    statusState: State<NodeStatus>,
    viewModel: MainViewModel,
    characterRepository: CharacterRepository,
    startNode: () -> Unit,
    openCharacterChatFromCharacterManagement: (String, String?) -> Unit,
) {
    composable(STRoutes.CHARACTERS) {
        // The visible route uses the prototype character library surface.
        STCharacterLibraryScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            repository = characterRepository,
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
            repository = characterRepository,
            onStartService = { startNode() },
            onBack = { navController.popBackStackSafely() },
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
                repository = characterRepository,
                avatar = avatar,
                onStartService = { startNode() },
                onBack = { navController.popBackStackSafely() },
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
                repository = characterRepository,
                avatar = avatar,
                onStartService = { startNode() },
                onBack = { navController.popBackStackSafely() },
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
        route = STRoutes.CHAR_FORM,
        arguments = listOf(navArgument("avatar") { type = NavType.StringType })
    ) { backStackEntry ->
        BackHandler { navController.popBackStackSafely() }
        val avatar = backStackEntry.arguments?.getString("avatar")?.let { Uri.decode(it) }.orEmpty()
        STCharacterFormScreen(
            avatar = avatar,
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onClose = { navController.popBackStackSafely() },
            onOpenGreetings = { navController.navigate(STRoutes.characterGreetings(avatar)) },
            onOpenAdvanced = { navController.navigate(STRoutes.characterAdvanced(avatar)) },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    composable(
        route = STRoutes.CHAR_GREETINGS,
        arguments = listOf(navArgument("avatar") { type = NavType.StringType })
    ) { backStackEntry ->
        BackHandler { navController.popBackStackSafely() }
        val avatar = backStackEntry.arguments?.getString("avatar")?.let { Uri.decode(it) }.orEmpty()
        STAltGreetingsScreen(
            avatar = avatar,
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navController.popBackStackSafely() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    composable(
        route = STRoutes.CHAR_ADVANCED,
        arguments = listOf(navArgument("avatar") { type = NavType.StringType })
    ) { backStackEntry ->
        BackHandler { navController.popBackStackSafely() }
        val avatar = backStackEntry.arguments?.getString("avatar")?.let { Uri.decode(it) }.orEmpty()
        STCharacterAdvancedScreen(
            avatar = avatar,
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onClose = { navController.popBackStackSafely() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }
}

private fun NavGraphBuilder.worldInfoAndToolsGraph(
    navController: NavHostController,
    statusState: State<NodeStatus>,
    viewModel: MainViewModel,
    startNode: () -> Unit,
) {
    composable(STRoutes.WORLD_INFO) {
        BackHandler { navController.popBackStackSafely() }
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
        BackHandler { navController.popBackStackSafely() }
        STWorldBookManageScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navController.popBackStackSafely() },
            onOpenBook = { name -> navController.navigate(STRoutes.worldInfoBook(name)) },
            onOpenGlobalSettings = { navController.navigate(STRoutes.WORLD_INFO_GLOBAL) },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    composable(
        route = STRoutes.WORLD_INFO_BOOK,
        arguments = listOf(navArgument("name") { type = NavType.StringType })
    ) { backStackEntry ->
        BackHandler { navController.popBackStackSafely() }
        val name = backStackEntry.arguments?.getString("name").orEmpty()
        STLorebookDetailScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            bookName = name,
            onBack = { navController.popBackStackSafely() },
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
        BackHandler { navController.popBackStackSafely() }
        STWorldEntryEditScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            entryUid = backStackEntry.arguments?.getInt("uid") ?: -1,
            bookName = backStackEntry.arguments?.getString("book").orEmpty(),
            onClose = { navController.popBackStackSafely() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    composable(STRoutes.WORLD_INFO_GLOBAL) {
        BackHandler { navController.popBackStackSafely() }
        STWIGlobalSettingsScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navController.popBackStackSafely() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    composable(STRoutes.PERSONA) {
        BackHandler { navController.popBackStackSafely() }
        STPersonaScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navController.popBackStackSafely() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    composable(STRoutes.PRESETS) {
        BackHandler { navController.popBackStackSafely() }
        STAISettingsScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navController.popBackStackSafely() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) },
            onSettingsChanged = { }
        )
    }

    composable(STRoutes.CONNECTIONS) {
        BackHandler { navController.popBackStackSafely() }
        STApiConnectionScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navController.popBackStackSafely() },
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
        BackHandler { navController.popBackStackSafely() }
        STProviderDetailScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            providerId = providerId,
            onBack = { navController.popBackStackSafely() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) },
            onSettingsChanged = { }
        )
    }

    composable(STRoutes.CHAT_BACKUPS) {
        BackHandler { navController.popBackStackSafely() }
        STMemoryScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navController.popBackStackSafely() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    composable(STRoutes.SECRETS) {
        BackHandler { navController.popBackStackSafely() }
        STSecretsScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navController.popBackStackSafely() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    composable(STRoutes.EXTENSIONS) {
        BackHandler { navController.popBackStackSafely() }
        STExtensionsScreen(
            onBack = { navController.popBackStackSafely() },
            onOpenQuickReplies = { navController.navigate(STRoutes.QUICK_REPLIES) },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    composable(STRoutes.AUTHOR_NOTE) {
        BackHandler { navController.popBackStackSafely() }
        STAuthorNoteCFGScreen(
            onBack = { navController.popBackStackSafely() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    composable(STRoutes.QUICK_REPLIES) {
        BackHandler { navController.popBackStackSafely() }
        STQuickReplyScreen(
            onBack = { navController.popBackStackSafely() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }
}

private fun NavGraphBuilder.settingsAndSystemGraph(
    navController: NavHostController,
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
    composable(STRoutes.SETTINGS) {
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
            onOpenChatBehavior = { navController.navigate(STRoutes.CHAT_BEHAVIOR) },
            appVersion = versionLabel,
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    composable(STRoutes.LOGIN) {
        STLoginScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onClose = { if (!navController.popBackStackSafely()) navigateMainTab(STRoutes.HOME) },
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
        BackHandler { navController.popBackStackSafely() }
        STAccountScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navController.popBackStackSafely() },
            onLogout = {
                navController.navigate(STRoutes.LOGIN) {
                    popUpTo(STRoutes.HOME) { inclusive = true }
                }
            },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    composable(STRoutes.BACKGROUNDS) {
        BackHandler { navController.popBackStackSafely() }
        STBackgroundsScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navController.popBackStackSafely() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) },
            chatBackground = viewModel.chatBackground.value,
            onChatBackgroundChanged = { value -> viewModel.setChatBackground(value) }
        )
    }

    composable(STRoutes.CHAT_BEHAVIOR) {
        BackHandler { navController.popBackStackSafely() }
        STChatBehaviorScreen(
            status = statusState.value,
            baseUrl = SillyTavernUrl.localWebUrl(statusState.value.port),
            onBack = { navController.popBackStackSafely() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    composable(STRoutes.APPEARANCE) {
        BackHandler { navController.popBackStackSafely() }
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
            onBack = { navController.popBackStackSafely() },
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    composable(STRoutes.LOGS) {
        BackHandler { navController.popBackStackSafely() }
        // 日志轮询只在本页存活:原先 3 个 state 挂在 setContent
        // 根作用域,停在日志页时每秒 3 次状态写入会牵动整棵根作用域重组。
        var stdoutLog by remember { mutableStateOf("") }
        var stderrLog by remember { mutableStateOf("") }
        var serviceLog by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            val logsDir = appPaths.logsDir
            while (true) {
                stdoutLog = readLogTail(File(logsDir, "node_stdout.log"), 16 * 1024)
                stderrLog = readLogTail(File(logsDir, "node_stderr.log"), 16 * 1024)
                serviceLog = readLogTail(File(logsDir, "service.log"), 16 * 1024)
                delay(1000)
            }
        }
        LogsScreen(
            onBack = { navController.popBackStackSafely() },
            onExportDiagnostics = triggerDiagnosticExport,
            stdoutLog = stdoutLog,
            stderrLog = stderrLog,
            serviceLog = serviceLog
        )
    }

    composable(STRoutes.CONFIG) {
        ConfigScreen(
            onBack = { navController.popBackStackSafely() },
            onOpenDocs = { openConfigDocs() },
            canEdit = statusState.value.state == NodeState.STOPPED || statusState.value.state == NodeState.ERROR,
            configFile = appPaths.configFile,
            onShowMessage = { message -> viewModel.showTransientMessage(message) }
        )
    }

    composable(STRoutes.LEGAL) {
        BackHandler { navController.popBackStackSafely() }
        LegalScreen(
            onBack = { navController.popBackStackSafely() },
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
            BackHandler { navController.popBackStackSafely() }
            LicenseTextScreen(
                onBack = { navController.popBackStackSafely() },
                doc = doc
            )
        }
    }

    composable(STRoutes.MANAGE_ST) {
        BackHandler { navController.popBackStackSafely() }
        STStCoreScreen(
            onBack = { navController.popBackStackSafely() },
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
