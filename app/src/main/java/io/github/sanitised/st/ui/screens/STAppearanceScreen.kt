package io.github.sanitised.st.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sanitised.st.ThemeColorSource
import io.github.sanitised.st.ThemeMode

// ─────────────────────────────────────────────────────────────
// 23 · 主题外观与阅读 (AppearanceScreen) - Premium Swatch Live Preview
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun STAppearanceScreen(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    colorSource: ThemeColorSource,
    onColorSourceChanged: (ThemeColorSource) -> Unit,
    fontSize: Float,
    onFontSizeChanged: (Float) -> Unit,
    bubbleStyle: Boolean,
    onBubbleStyleChanged: (Boolean) -> Unit,
    reduceMotion: Boolean,
    onReduceMotionChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            STTopHeader(
                title = "主题外观与阅读",
                leading = {
                    STIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                },
                titleBottomPadding = 4.dp
            )

            // ── 实时预览:随下方明暗/配色/字号/冒泡设置即时变化 ──
            PremiumCard(
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ) {
                Text(
                    text = "聊天阅读排版预览",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                        .padding(12.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppearancePreviewMessage(
                            name = "爱丽丝",
                            text = "热可可好啦。今天想加点什么？棉花糖、肉桂、还是都不加？",
                            isUser = false,
                            fontSize = fontSize,
                            bubbleStyle = bubbleStyle
                        )
                        AppearancePreviewMessage(
                            name = "你",
                            text = "加肉桂。窗边那个老位置还空着么？",
                            isUser = true,
                            fontSize = fontSize,
                            bubbleStyle = bubbleStyle
                        )
                    }
                }
            }

            // ── 外观模式 ──
            PremiumSectionHeader(title = "外观模式")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val modes = listOf(
                    ThemeMode.LIGHT to "浅色",
                    ThemeMode.DARK to "深色",
                    ThemeMode.AUTO to "跟随系统"
                )
                for ((mode, label) in modes) {
                    AppearanceChoiceChip(
                        selected = themeMode == mode,
                        label = label,
                        onClick = { onThemeModeChanged(mode) }
                    )
                }
            }

            // ── 界面配色 ──
            PremiumSectionHeader(title = "界面配色")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sources = listOf(
                    ThemeColorSource.BRAND to "品牌橙",
                    ThemeColorSource.DYNAMIC to "动态取色"
                )
                for ((src, label) in sources) {
                    AppearanceChoiceChip(
                        selected = colorSource == src,
                        label = label,
                        onClick = { onColorSourceChanged(src) }
                    )
                }
            }
            Text(
                text = "动态取色需 Android 12 及以上，低版本会自动回退到品牌配色。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // ── 字号 ──
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("聊天字号", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    Text(text = "${fontSize.toInt()} sp", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = fontSize,
                    onValueChange = onFontSizeChanged,
                    valueRange = 12f..20f,
                    steps = 7
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // ── 阅读与交互 ──
            PremiumSectionHeader(title = "阅读与交互")
            STListItem(
                headline = "消息冒泡风格",
                supporting = "关闭则使用全宽文档样式",
                trailing = { Switch(checked = bubbleStyle, onCheckedChange = onBubbleStyleChanged) },
                onClick = { onBubbleStyleChanged(!bubbleStyle) }
            )
            STListItem(
                headline = "减少动效渲染",
                supporting = "关闭全屏转场、抽屉划过的缓动动画",
                trailing = { Switch(checked = reduceMotion, onCheckedChange = onReduceMotionChanged) },
                onClick = { onReduceMotionChanged(!reduceMotion) }
            )
        }
    }
}

// 主题设置屏的选择 chip:选中态带对勾。
@Composable
private fun AppearanceChoiceChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(label)
            }
        }
    )
}

// 预览用的单条消息:随字号缩放,随 bubbleStyle 在气泡样式与全宽文档样式之间切换,
// 配色全部取自当前 MaterialTheme,故明暗/配色切换时预览同步变化。
@Composable
private fun AppearancePreviewMessage(
    name: String,
    text: String,
    isUser: Boolean,
    fontSize: Float,
    bubbleStyle: Boolean
) {
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.5f).sp
    )
    if (bubbleStyle) {
        val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
        val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isUser) 14.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 14.dp,
                    bottomStart = 14.dp,
                    bottomEnd = 14.dp
                ),
                color = bubbleColor,
                modifier = Modifier.fillMaxWidth(0.82f)
            ) {
                Text(
                    text = text,
                    style = textStyle,
                    color = textColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isUser) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = text, style = textStyle, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
