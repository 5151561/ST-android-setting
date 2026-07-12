package io.github.sanitised.st.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

val LocalSTOpenDrawer = staticCompositionLocalOf<() -> Unit> { {} }

data class DrawerNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val supportingText: String? = null,
    val badgeText: String? = null,
    val isPrimaryGroup: Boolean = true,
    val danger: Boolean = false
)

/**
 * 应用唯一的导航外壳:侧边抽屉(ModalNavigationDrawer)。
 * 底部导航栏已移除 —— 它与抽屉路由完全重复(见 drawerNavItems),保留两套只会分裂入口心智。
 */
@Composable
fun STNavigationScaffold(
    drawerItems: List<DrawerNavItem> = emptyList(),
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    showNavigation: Boolean = true,
    snackbarHost: @Composable () -> Unit,
    // 头部里的可点区域(扮演者/连接卡)拿到的是「关抽屉再导航」的回调
    drawerHeader: (@Composable ((String) -> Unit) -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val closeAndNavigate: (String) -> Unit = { route ->
        scope.launch { drawerState.close() }
        onNavigate(route)
    }

    CompositionLocalProvider(LocalSTOpenDrawer provides openDrawer) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = showNavigation,
            drawerContent = {
                STDrawerSheet(
                    drawerItems = drawerItems,
                    currentRoute = currentRoute,
                    onNavigate = closeAndNavigate,
                    header = drawerHeader
                )
            }
        ) {
            Scaffold(
                snackbarHost = snackbarHost,
                containerColor = MaterialTheme.colorScheme.background,
                content = content
            )
        }
    }
}

@Composable
private fun STDrawerSheet(
    drawerItems: List<DrawerNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    header: (@Composable ((String) -> Unit) -> Unit)?
) {
    ModalDrawerSheet(
        modifier = Modifier.width(width = (LocalConfiguration.current.screenWidthDp * 0.85f).dp).widthIn(max = 360.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp)
        ) {
            if (header != null) {
                header(onNavigate)
                Spacer(modifier = Modifier.height(8.dp))
            }
            drawerItems.forEachIndexed { index, item ->
                if (index > 0 && drawerItems[index - 1].isPrimaryGroup != item.isPrimaryGroup) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                STDrawerItem(
                    item = item,
                    selected = currentRoute == item.route,
                    onClick = { onNavigate(item.route) }
                )
            }
        }
    }
}

@Composable
private fun STDrawerItem(
    item: DrawerNavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = when {
        item.danger -> colors.error
        selected -> colors.onSecondaryContainer
        else -> colors.onSurface
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = CircleShape,
        color = if (selected) colors.secondaryContainer else colors.surface,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val badge = item.badgeText ?: item.supportingText
            if (!badge.isNullOrBlank()) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
