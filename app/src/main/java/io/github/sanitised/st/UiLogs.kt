package io.github.sanitised.st

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    onExportDiagnostics: () -> Unit,
    stdoutLog: String,
    stderrLog: String,
    serviceLog: String
) {
    var selectedTab by remember { mutableStateOf("stdout") }
    var realtimeFollow by remember { mutableStateOf(true) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val currentLogText = when (selectedTab) {
        "stdout" -> stdoutLog
        "stderr" -> stderrLog
        else -> serviceLog
    }

    val lines = remember(currentLogText) {
        currentLogText.split("\n").filter { it.isNotEmpty() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0D0A07) // Premium ultra dark terminal background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Premium Terminal Custom Top Bar
            TopAppBar(
                title = {
                    Text(
                        text = "运行日志",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(currentLogText)) },
                        enabled = currentLogText.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "复制日志",
                            tint = if (currentLogText.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.4f)
                        )
                    }
                    IconButton(onClick = onExportDiagnostics) {
                        Icon(
                            imageVector = Icons.Filled.FileDownload,
                            contentDescription = "导出诊断数据",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D0A07),
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )

            // Stream Tabs Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("stdout", "stdout", stdoutLog),
                    Triple("stderr", "stderr", stderrLog),
                    Triple("service", "service", serviceLog)
                ).forEach { (id, label, content) ->
                    val isSelected = id == selectedTab
                    val size = formatLogSize(content)
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.15f),
                                shape = CircleShape
                            )
                            .clickable { selectedTab = id }
                            .padding(vertical = 8.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = size,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            // Terminal Console Body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color(0xFF060503).copy(alpha = 0.5f), shape = MaterialTheme.shapes.large)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), shape = MaterialTheme.shapes.large)
                    .padding(12.dp)
            ) {

                
                LaunchedEffect(lines.size) {
                    if (realtimeFollow && lines.isNotEmpty()) {
                        listState.animateScrollToItem(lines.size - 1)
                    }
                }

                if (lines.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "日志为空",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = Color.White.copy(alpha = 0.35f)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = lines,
                            key = { index, _ -> index }
                        ) { _, line ->
                            ParsedLogLine(line = line)
                        }
                    }
                }
            }

            // Sticky Bottom Tools (Floating live toggle and Go Bottom FAB)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp, top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Real-time Follow toggle pill
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clickable { realtimeFollow = !realtimeFollow }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (realtimeFollow) Color(0xFF7FCE8E) else Color.White.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "实时跟随",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = realtimeFollow,
                                onCheckedChange = { realtimeFollow = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }

                    // Scroll to bottom FAB
                    FloatingActionButton(
                        onClick = {
                            realtimeFollow = true
                            if (lines.isNotEmpty()) {
                                scope.launch {
                                    listState.animateScrollToItem(lines.size - 1)
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerticalAlignBottom,
                            contentDescription = "滚动到底部"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParsedLogLine(line: String) {
    val words = line.split(" ", limit = 3)
    val time = words.getOrNull(0).orEmpty()
    val level = words.getOrNull(1).orEmpty()
    val message = words.getOrNull(2).orEmpty()

    val isTime = time.contains(":") && time.any { it.isDigit() }
    val isLvl = level.startsWith("[") && level.endsWith("]")

    val levelColor = when {
        level.contains("info", ignoreCase = true) -> Color(0xFF9CC79E)
        level.contains("warn", ignoreCase = true) -> Color(0xFFE8B86A)
        level.contains("error", ignoreCase = true) -> Color(0xFFF2B8B5)
        else -> Color(0xFF9CC79E)
    }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        if (isTime && isLvl) {
            Text(
                text = time,
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(
                text = level,
                color = levelColor,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(
                text = message,
                color = Color(0xFFD2C5B8),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
        } else {
            val color = when {
                line.contains("[error]", ignoreCase = true) || line.contains("error", ignoreCase = true) -> Color(0xFFF2B8B5)
                line.contains("[warn]", ignoreCase = true) || line.contains("warn", ignoreCase = true) -> Color(0xFFE8B86A)
                else -> Color(0xFFD2C5B8)
            }
            Text(
                text = line,
                color = color,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
        }
    }
}

private fun formatLogSize(content: String): String {
    val bytes = content.toByteArray(Charsets.UTF_8).size
    return when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes.toFloat() / (1024 * 1024))
        bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f kB", bytes.toFloat() / 1024)
        else -> "$bytes B"
    }
}

suspend fun readLogTail(file: File, maxBytes: Int): String {
    return withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext ""
        val length = file.length()
        if (length <= 0) return@withContext ""
        val toRead = if (length > maxBytes) maxBytes.toLong() else length
        val bytes = ByteArray(toRead.toInt())
        java.io.RandomAccessFile(file, "r").use { raf ->
            raf.seek(length - toRead)
            raf.readFully(bytes)
        }
        bytes.toString(Charsets.UTF_8)
    }
}
