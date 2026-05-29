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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
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

private const val NAVIGATION_RAIL_MIN_WIDTH_DP = 600

val LocalSTOpenDrawer = staticCompositionLocalOf<() -> Unit> { {} }

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

data class DrawerNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val supportingText: String? = null,
    val badgeText: String? = null,
    val isPrimaryGroup: Boolean = true,
    val danger: Boolean = false
)

@Composable
fun STNavigationScaffold(
    items: List<BottomNavItem>,
    drawerItems: List<DrawerNavItem> = emptyList(),
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    showNavigation: Boolean = true,
    snackbarHost: @Composable () -> Unit,
    drawerHeader: @Composable (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val useRail = LocalConfiguration.current.screenWidthDp >= NAVIGATION_RAIL_MIN_WIDTH_DP
    if (useRail && showNavigation) {
        CompositionLocalProvider(LocalSTOpenDrawer provides {}) {
            Row(modifier = Modifier.fillMaxSize()) {
                STNavigationRail(
                    items = items,
                    currentRoute = currentRoute,
                    onNavigate = onNavigate
                )
                Scaffold(
                    modifier = Modifier.weight(1f),
                    snackbarHost = snackbarHost,
                    containerColor = MaterialTheme.colorScheme.background,
                    content = content
                )
            }
        }
    } else if (useRail) {
        CompositionLocalProvider(LocalSTOpenDrawer provides {}) {
            Scaffold(
                snackbarHost = snackbarHost,
                containerColor = MaterialTheme.colorScheme.background,
                content = content
            )
        }
    } else {
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
                    bottomBar = {
                        if (showNavigation) {
                            STBottomBar(
                                items = items,
                                currentRoute = currentRoute,
                                onNavigate = onNavigate
                            )
                        }
                    },
                    snackbarHost = snackbarHost,
                    containerColor = MaterialTheme.colorScheme.background,
                    content = content
                )
            }
        }
    }
}

@Composable
private fun STDrawerSheet(
    drawerItems: List<DrawerNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    header: @Composable (() -> Unit)?
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
                header()
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

@Composable
fun STBottomBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = { Text(text = item.label) }
            )
        }
    }
}

@Composable
private fun STNavigationRail(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationRailItem(
                modifier = Modifier.padding(vertical = 4.dp),
                selected = selected,
                onClick = { onNavigate(item.route) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = { Text(text = item.label) }
            )
        }
    }
}
