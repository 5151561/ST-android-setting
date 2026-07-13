@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.sanitised.st.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 单聊与群聊共享的日期/分段分隔条(取代原 DateChip 与 DateChipG)。
 *
 * @param verticalPadding 外层上下留白:单聊传 0dp、群聊传 12dp。
 * @param bold 文字是否加粗:群聊为粗体,单聊为常规。
 */
@Composable
fun ChatDateChip(
    text: String,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 0.dp,
    bold: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontWeight = if (bold) FontWeight.Bold else null
            )
        }
    }
}

/**
 * 单聊与群聊共享的消息气泡外壳:圆角方向、配色、内边距完全一致,
 * 差异全部收敛为参数——群聊 AI 气泡传 [accent] 得到左侧成员色竖条,
 * 单聊传 [onLongPress] 得到长按消息操作。
 */
@Composable
fun ChatBubbleSurface(
    isUser: Boolean,
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Unspecified,
    accent: Color? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = if (isUser) 18.dp else 4.dp,
        topEnd = if (isUser) 4.dp else 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 18.dp
    )
    val background = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    Box(
        modifier = modifier
            .then(if (maxWidth != Dp.Unspecified) Modifier.widthIn(max = maxWidth) else Modifier)
            .clip(shape)
            .then(
                if (onLongPress != null) {
                    Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress)
                } else {
                    Modifier
                }
            )
            .background(background)
    ) {
        if (accent != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            content = content
        )
    }
}

/**
 * 最后一条 AI 消息下方的操作行(swipe 切换 + 重写/继续/更多),单聊群聊共用。
 * [swipeIndex]/[swipeCount] 从 0 计数;swipe 回调收到 [messageId] 便于直接落库。
 */
@Composable
fun AssistantMessageControls(
    messageId: Int,
    swipeIndex: Int,
    swipeCount: Int,
    onSwipePrevious: (Int) -> Unit,
    onSwipeNext: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
    onMore: () -> Unit
) {
    Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSwipePrevious(messageId) }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = "上一个回复",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "${swipeIndex + 1}/$swipeCount",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 30.dp)
        )
        IconButton(onClick = { onSwipeNext(messageId) }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "下一个回复",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onRegenerate, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "重写",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onContinue, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "继续",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onMore, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.MoreHoriz,
                contentDescription = "更多消息操作",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 消息正文的富文本渲染,单聊群聊共用(原群聊 GText/gfmt):
 * "对话" 高亮为 primary 色、*动作* 转为弱化斜体,其余文本用 [color]。
 */
@Composable
fun ChatRichText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier) {
        text.split('\n').forEachIndexed { index, line ->
            if (index > 0) Spacer(modifier = Modifier.height(8.dp))
            ChatRichTextLine(line = line, color = color, primary = primary, muted = muted)
        }
    }
}

// 每行独立成可跳过的 composable:流式生成时整条消息的 text 每个节流 tick 都在变,
// 但只有最后一行的内容真的不同——其余行参数不变直接跳过,行内解析(chatRichLine)
// 就不会对全文反复重跑。remember 再挡一层父级强制重组时的重复解析。
@Composable
private fun ChatRichTextLine(
    line: String,
    color: Color,
    primary: Color,
    muted: Color,
) {
    Text(
        text = remember(line, primary, muted) { chatRichLine(line, primary, muted) },
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        lineHeight = 22.sp
    )
}

/** 单行的行内格式转换:*斜体* -> 弱化斜体,"引号对话" -> primary 色。 */
private fun chatRichLine(line: String, primaryColor: Color, mutedColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '*') {
                val close = line.indexOf('*', i + 1)
                if (close > i) {
                    withStyle(
                        style = SpanStyle(
                            color = mutedColor.copy(alpha = 0.85f),
                            fontStyle = FontStyle.Italic
                        )
                    ) {
                        append(line.substring(i + 1, close))
                    }
                    i = close + 1
                    continue
                }
            }
            if (ch == '"' || ch == '“' || ch == '”') {
                val close = line.indexOf(if (ch == '“') '”' else ch, i + 1)
                if (close > i) {
                    withStyle(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium)) {
                        append(line.substring(i, close + 1))
                    }
                    i = close + 1
                    continue
                }
            }

            // 普通文本。从 i+1 起扫:走到这里说明 line[i] 要么是普通字符,要么是没找到
            // 闭合的 * / " (流式输出时 chunk 边界常落在配对中间),都按普通文本消费掉,
            // 否则 i 原地不动会让主线程死循环。
            var nextSpecial = i + 1
            while (nextSpecial < line.length && line[nextSpecial] != '*' && line[nextSpecial] != '"' && line[nextSpecial] != '“') {
                nextSpecial++
            }
            append(line.substring(i, nextSpecial))
            i = nextSpecial
        }
    }
}
