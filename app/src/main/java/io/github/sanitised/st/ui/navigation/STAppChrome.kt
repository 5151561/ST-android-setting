package io.github.sanitised.st.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.ui.screens.STAvatar
import io.github.sanitised.st.ui.screens.STDrawerAccount
import io.github.sanitised.st.ui.screens.STDrawerState
import io.github.sanitised.st.ui.screens.STStatusDot

internal val drawerNavItems = listOf(
    DrawerNavItem(STRoutes.HOME, "对话", Icons.AutoMirrored.Filled.Chat),
    DrawerNavItem(STRoutes.CHARACTERS, "角色库", Icons.Filled.Groups),
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

internal val rootTabRoutes = setOf(
    STRoutes.HOME,
    STRoutes.CHARACTERS,
    STRoutes.WORLD_INFO,
    STRoutes.SETTINGS
)

@Composable
internal fun STAppDrawerHeader(
    stLabel: String,
    nodeLabel: String,
    status: NodeStatus,
    account: STDrawerAccount?,
    onOpenPersonas: () -> Unit,
    onOpenConnections: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val drawerState = STDrawerState.from(
        status = status,
        stLabel = stLabel,
        nodeLabel = nodeLabel,
        account = account
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onOpenPersonas)
            ) {
                STAvatar(
                    label = drawerState.personaInitial,
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
                onClick = onOpenConnections,
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
                                text = drawerState.connectionInitial,
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

/**
 * 导航外壳:路由 → 抽屉高亮/是否显示外壳的推导收在这里。
 * navigator.currentRoute 的读取只让本作用域随导航重组,
 * setContent 根作用域不再因为每次导航整棵重跑。
 */
@Composable
internal fun STAppChrome(
    navigator: STNavigator,
    drawerItems: List<DrawerNavItem>,
    onNavigate: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    swipeDrawer: Boolean = true,
    drawerHeader: @Composable ((String) -> Unit) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val currentRoute = navigator.currentRoute
    // 子页面路由折叠到抽屉里对应的父条目,保证抽屉高亮正确。
    val drawerSelectedRoute = when (currentRoute) {
        STRoutes.CHAT -> STRoutes.HOME
        STRoutes.CHARACTER_NEW,
        STRoutes.CHARACTER_DETAIL,
        STRoutes.CHARACTER_EDIT,
        STRoutes.CHAR_FORM,
        STRoutes.CHAR_GREETINGS,
        STRoutes.CHAR_ADVANCED,
        STRoutes.PAST_CHATS -> STRoutes.CHARACTERS
        STRoutes.WORLD_INFO_MANAGE,
        STRoutes.WORLD_INFO_BOOK,
        STRoutes.WORLD_INFO_ENTRY,
        STRoutes.WORLD_INFO_GLOBAL -> STRoutes.WORLD_INFO
        STRoutes.GROUP_CHAT_DETAIL,
        STRoutes.GROUP_SETTINGS,
        STRoutes.GROUP_MEMBERS,
        STRoutes.NEW_GROUP_CHAT -> STRoutes.HOME
        STRoutes.SECRETS,
        STRoutes.PROVIDER_DETAIL -> STRoutes.CONNECTIONS
        STRoutes.QUICK_REPLIES -> STRoutes.EXTENSIONS
        STRoutes.APPEARANCE_THEME_COLOR,
        STRoutes.APPEARANCE_TYPOGRAPHY,
        STRoutes.BACKGROUNDS,
        STRoutes.APPEARANCE_CHAT_STYLE,
        STRoutes.CHAT_BEHAVIOR -> STRoutes.APPEARANCE
        STRoutes.LOGS,
        STRoutes.CONFIG -> STRoutes.MANAGE_ST
        STRoutes.LICENSE -> STRoutes.LEGAL
        STRoutes.ACCOUNT -> STRoutes.SETTINGS
        else -> currentRoute
    }
    val showNavigationChrome = currentRoute in rootTabRoutes
    STNavigationScaffold(
        drawerItems = drawerItems,
        currentRoute = drawerSelectedRoute,
        onNavigate = onNavigate,
        showNavigation = showNavigationChrome,
        swipeDrawer = swipeDrawer,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        },
        drawerHeader = drawerHeader,
        content = content
    )
}
