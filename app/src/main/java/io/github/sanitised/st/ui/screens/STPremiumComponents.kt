package io.github.sanitised.st.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────
// Shared HUD Primitives & Style Tokens (1:1 Matching tokens.css)
// ─────────────────────────────────────────────────────────────

// 这些原本是 dark-only 硬编码色板(1:1 对齐旧 tokens.css),主题重做后必须跟随
// MaterialTheme。改成 @Composable 只读访问器后,散落在各屏的 100+ 处引用无需改动即可
// 随明暗/配色切换,不再锁死在深色橙。
val STThemePrimary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val STThemeTertiary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
val STThemeError: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
val STThemeBg: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background

@Composable
fun GlowStatusDot(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(14.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.28f))
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun PremiumSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.Bold
            ),
            color = STThemePrimary
        )
        if (trailing != null) {
            Row(content = trailing)
        }
    }
}

@Composable
fun STPreviewBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = STThemePrimary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, STThemePrimary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Science,
                contentDescription = null,
                tint = STThemePrimary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "原型预览 — 此页面数据为设计占位，功能尚未接入后端",
                style = MaterialTheme.typography.bodySmall,
                color = STThemePrimary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AnimatedToast(
    message: String?,
    onDismiss: () -> Unit
) {
    var visible by remember(message) { mutableStateOf(message != null) }
    LaunchedEffect(message) {
        if (message != null) {
            visible = true
            delay(1200)
            visible = false
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(150))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 56.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 320.dp)
            ) {
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
    }
}
