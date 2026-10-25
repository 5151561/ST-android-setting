package io.github.sanitised.st.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.UpdateChannel
import io.github.sanitised.st.ui.navigation.LocalSTOpenDrawer

@Composable
fun STMeScreen(
    autoCheckEnabled: Boolean,
    onAutoCheckChanged: (Boolean) -> Unit,
    autoOpenBrowserEnabled: Boolean,
    onAutoOpenBrowserChanged: (Boolean) -> Unit,
    isBatteryUnrestricted: Boolean,
    onOpenBatterySettings: () -> Unit,
    channel: UpdateChannel,
    onChannelChanged: (UpdateChannel) -> Unit,
    onCheckNow: () -> Unit,
    isChecking: Boolean,
    vibrationFeedback: Boolean,
    onVibrationFeedbackChanged: (Boolean) -> Unit,
    secondConfirmation: Boolean,
    onSecondConfirmationChanged: (Boolean) -> Unit,
    swipeDrawer: Boolean,
    onSwipeDrawerChanged: (Boolean) -> Unit,
    developerMode: Boolean,
    onDeveloperModeChanged: (Boolean) -> Unit,
    onOpenWorldInfo: () -> Unit,
    onOpenPersona: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenChatBackups: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenManageSt: () -> Unit,
    onOpenSecrets: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenAccount: () -> Unit = {},
    onOpenBackgrounds: () -> Unit = {},
    onOpenChatBehavior: () -> Unit = {},
    appVersion: String = "",
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val openDrawer = LocalSTOpenDrawer.current

    STRoot(modifier = modifier) {
        STTopHeader(
            title = "我的",
            leading = { STIconButton(Icons.Filled.Menu, "打开抽屉", openDrawer) },
            actions = { STIconButton(Icons.Filled.Search, "搜索设置", { onShowMessage("搜索设置功能开发中") }) }
        )
        // User card with chevron - direct and simple borderless style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenAccount() }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            STAvatar("我", size = 56.dp, ringColor = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("我（默认）", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("默认用户配置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // ── 外观 ──
        STSectionHeader("外观")
        STSettingsGroup {
            STListItem(
                headline = "主题外观",
                supporting = "明暗模式、界面配色、字号、消息样式",
                leading = { STTileIcon(Icons.Filled.Palette) },
                trailing = { STMiniSwatch() },
                divider = true,
                onClick = onOpenAppearance
            )
            STListItem(
                headline = "聊天背景",
                supporting = "为聊天设置全局壁纸",
                leading = { STTileIcon(Icons.Filled.Image) },
                onClick = onOpenBackgrounds
            )
        }
        // ── 行为 ──
        STSectionHeader("行为")
        STSettingsGroup {
            STListItem(
                headline = "聊天与消息",
                supporting = "平滑流式、自动继续、消息显示、示例消息行为",
                leading = { STTileIcon(Icons.AutoMirrored.Filled.Chat) },
                divider = true,
                onClick = onOpenChatBehavior
            )
            STSwitchRow("流式生成时震动反馈", "逐字到达时轻微震动", vibrationFeedback, onVibrationFeedbackChanged)
            STSwitchRow("敏感操作二次确认", "删除消息、清空对话等", secondConfirmation, onSecondConfirmationChanged)
            STSwitchRow("服务就绪后自动打开浏览器", null, autoOpenBrowserEnabled, onAutoOpenBrowserChanged)
            STSwitchRow("滑动呼出抽屉", "从左边缘横扫", swipeDrawer, onSwipeDrawerChanged)
        }
        // ── 数据 ──
        STSectionHeader("数据")
        STSettingsGroup {
            STListItem(
                headline = "自动备份",
                supporting = "点击配置",
                leading = { STTileIcon(Icons.Filled.Backup) },
                divider = true,
                onClick = { onShowMessage("备份设置功能开发中") }
            )
            STListItem(
                headline = "同步",
                supporting = "未开启",
                leading = { STTileIcon(Icons.Filled.CloudSync) },
                divider = true,
                onClick = { onShowMessage("同步设置功能开发中") }
            )
            STListItem(
                headline = "导出全部数据",
                supporting = ".charx + .json 包",
                leading = { STTileIcon(Icons.Filled.FolderZip) },
                onClick = { onShowMessage("数据导出功能开发中") }
            )
        }
        // ── 服务与安全 ──
        STSectionHeader("服务与安全")
        STSettingsGroup {
            STListItem(
                headline = "API 连接设置",
                supporting = "SillyTavern 核心端点接入与预设管理",
                leading = { STTileIcon(Icons.Filled.Cable) },
                divider = true,
                onClick = onOpenConnections
            )
            STListItem(
                headline = "API 密钥管理",
                supporting = "管理所有 AI 供应商 API 凭证",
                leading = { STTileIcon(Icons.Filled.VpnKey) },
                divider = true,
                onClick = onOpenSecrets
            )
            STListItem(
                headline = "扩展管理",
                supporting = "管理 SillyTavern 扩展插件",
                leading = { STTileIcon(Icons.Filled.Extension) },
                onClick = onOpenExtensions
            )
        }

        // ── 实验性 ──
        STSectionHeader("实验性")
        STSettingsGroup {
            STSwitchRow("开发者模式", "显示 token 计数与请求 JSON", developerMode, onDeveloperModeChanged)
        }
        // ── 关于 ──
        STSectionHeader("关于")
        STSettingsGroup {
            STListItem(
                headline = "SillyTavern Mobile",
                supporting = "${appVersion.ifBlank { "—" }} · 第三方移动客户端",
                leading = { STTileIcon(Icons.Filled.Info) },
                onClick = { onShowMessage("版本信息") }
            )
        }
    }
}

private val MiniSwatchColors = listOf(0xFFFFB871, 0xFFC6CB95, 0xFFE5C0A2, 0xFF251F17)

@Composable
private fun STMiniSwatch() {
    Row {
        MiniSwatchColors.forEachIndexed { index, colorLong ->
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .then(if (index > 0) Modifier.padding(start = 0.dp) else Modifier)
                    .background(Color(colorLong), CircleShape)
                    .then(
                        Modifier.padding(0.dp) // border via overlay approach
                    )
            )
        }
    }
}
