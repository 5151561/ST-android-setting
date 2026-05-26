package io.github.sanitised.st.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

private const val NAVIGATION_RAIL_MIN_WIDTH_DP = 600

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun STNavigationScaffold(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    snackbarHost: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val useRail = LocalConfiguration.current.screenWidthDp >= NAVIGATION_RAIL_MIN_WIDTH_DP
    if (useRail) {
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
    } else {
        Scaffold(
            bottomBar = {
                STBottomBar(
                    items = items,
                    currentRoute = currentRoute,
                    onNavigate = onNavigate
                )
            },
            snackbarHost = snackbarHost,
            containerColor = MaterialTheme.colorScheme.background,
            content = content
        )
    }
}

@Composable
fun STBottomBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
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
