package io.github.sanitised.st.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
