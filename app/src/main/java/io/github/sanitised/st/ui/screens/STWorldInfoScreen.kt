package io.github.sanitised.st.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.WorldInfoBook
import io.github.sanitised.st.api.WorldInfoSummary
import io.github.sanitised.st.data.repository.DefaultWorldInfoRepository
import io.github.sanitised.st.data.repository.WorldInfoRepository
import io.github.sanitised.st.data.tavernApi
import io.github.sanitised.st.ui.navigation.LocalSTOpenDrawer

@Composable
fun STWorldInfoScreen(
    status: NodeStatus,
    baseUrl: String,
    onStartService: () -> Unit,
    onShowMessage: (String) -> Unit,
    onOpenManage: () -> Unit = {},
    onOpenBook: (String) -> Unit = {},
    onOpenGlobalSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    worldInfoRepository: WorldInfoRepository = remember(baseUrl) { DefaultWorldInfoRepository { tavernApi(baseUrl) } }
) {
    val openDrawer = LocalSTOpenDrawer.current
    var loading by remember { mutableStateOf(false) }
    var books by remember { mutableStateOf<List<WorldInfoSummary>>(emptyList()) }
    var firstBook by remember { mutableStateOf<WorldInfoBook?>(null) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchBackState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = searchBackState,
        isBackEnabled = searchActive,
        onBackCompleted = {
            searchActive = false
            searchQuery = ""
        }
    )
    val running = status.state == NodeState.RUNNING

    LaunchedEffect(running, baseUrl, worldInfoRepository) {
        if (!running) return@LaunchedEffect
        loading = true
        runCatching {
            val list = worldInfoRepository.listWorldInfos()
            books = list
            firstBook = list.firstOrNull()?.let { worldInfoRepository.getWorldInfo(it.name) }
        }.onFailure { onShowMessage(it.message ?: "世界书加载失败") }
        loading = false
    }

    STRoot(modifier = modifier) {
        STTopHeader(
            title = "世界书",
            subtitle = "动态注入的设定。条目根据关键词触发或常驻。",
            leading = {
                STIconButton(Icons.Filled.Menu, "打开抽屉", openDrawer)
            },
            actions = {
                STIconButton(Icons.Filled.Tune, "管理", onOpenManage)
                STIconButton(Icons.Filled.Settings, "全局设置", onOpenGlobalSettings)
            }
        )
        if (!running) {
            STSystemInfoCard("本地服务未启动", "启动服务后会加载真实世界书。") {
                Button(onClick = onStartService) { Text("启动服务") }
            }
        } else if (loading) {
            STLoadingCard("正在读取世界书…")
        } else if (books.isEmpty()) {
            STSystemInfoCard("暂无世界书", "在 SillyTavern 中尚未创建世界书。")
        } else {
            STSectionHeader(
                title = "本地世界书",
                trailing = {
                    Text("${books.size} 本已加载", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            )
            STListSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
                books.forEachIndexed { index, book ->
                    STListItem(
                        headline = book.name,
                        supporting = "包含设定与条目",
                        leading = {
                            STTileIcon(
                                icon = Icons.Filled.AutoStories,
                                tint = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        },
                        trailing = { Switch(checked = true, onCheckedChange = null) },
                        divider = index != books.lastIndex,
                        onClick = { onOpenBook(book.id) }
                    )
                }
            }
            STSectionHeader(title = "常用条目预览")
            val entries = firstBook?.entries.orEmpty()
            if (entries.isNotEmpty()) {
                entries.take(4).forEach { entry ->
                    LoreEntryPreview(
                        keys = entry.keys.joinToString(", ").ifBlank { entry.comment.ifBlank { "未命名条目" } },
                        content = entry.content,
                        constant = entry.constant,
                        selective = entry.selective,
                        order = entry.order
                    )
                }
            } else {
                STSystemInfoCard("暂无世界书条目", "当绑定并启用世界书时，符合触发关键词的条目会在此预览。")
            }
        }
    }
}

@Composable
private fun LoreEntryPreview(keys: String, content: String, constant: Boolean, selective: Boolean, order: Int = 0) {
    // 直接点击整条即展开/收起,不再单独放按钮(展开后无需再找按钮,误触也能就地收起)。
    var expanded by remember(content) { mutableStateOf(false) }
    var overflowing by remember(content) { mutableStateOf(false) }
    val keyList = remember(keys) { keys.split(", ").filter { it.isNotEmpty() } }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(enabled = overflowing || expanded) { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (constant) {
                        STBadge(
                            "常驻",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    if (selective) {
                        STBadge(
                            "关键词触发",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                if (order > 0) {
                    Text("序号 $order", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("关键词", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                keyList.forEach { keyword ->
                    STBadge(
                        keyword,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result ->
                    if (!expanded && result.hasVisualOverflow) overflowing = true
                }
            )
        }
    }
}
