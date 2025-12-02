package org.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import org.example.logging.AppLogger
import org.example.logging.LogEntry
import org.example.logging.LogLevel
import org.example.ui.theme.AppTheme

@Composable
fun LogWindow(
    onCloseRequest: () -> Unit
) {
    Window(
        onCloseRequest = onCloseRequest,
        title = "Логи приложения",
        state = WindowState(width = 800.dp, height = 500.dp)
    ) {
        AppTheme {
            LogWindowContent()
        }
    }
}

@Composable
private fun LogWindowContent() {
    val logs by AppLogger.logs.collectAsState()
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    var filterLevel by remember { mutableStateOf<LogLevel?>(null) }

    val filteredLogs = remember(logs, filterLevel) {
        if (filterLevel == null) logs else logs.filter { it.level == filterLevel }
    }

    LaunchedEffect(filteredLogs.size, autoScroll) {
        if (autoScroll && filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Логи (${filteredLogs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.weight(1f))

                // Filter chips
                FilterChip(
                    selected = filterLevel == null,
                    onClick = { filterLevel = null },
                    label = { Text("Все") }
                )
                LogLevel.entries.forEach { level ->
                    FilterChip(
                        selected = filterLevel == level,
                        onClick = { filterLevel = if (filterLevel == level) null else level },
                        label = { Text(level.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = getLevelColor(level).copy(alpha = 0.3f)
                        )
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Auto-scroll toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = autoScroll,
                        onCheckedChange = { autoScroll = it }
                    )
                    Text("Авто-прокрутка", style = MaterialTheme.typography.bodySmall)
                }

                IconButton(onClick = { AppLogger.clear() }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Очистить логи",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Logs list
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(filteredLogs, key = { "${it.timestamp}-${it.message.hashCode()}" }) { log ->
                LogEntryRow(log)
            }
        }
    }
}

@Composable
private fun LogEntryRow(log: LogEntry) {
    val levelColor = getLevelColor(log.level)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = levelColor.copy(alpha = 0.05f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = log.timestamp,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Surface(
            color = levelColor.copy(alpha = 0.2f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = log.level.name.padEnd(7),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = levelColor
            )
        }

        Text(
            text = "[${log.source}]",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = log.message,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun getLevelColor(level: LogLevel): Color {
    return when (level) {
        LogLevel.DEBUG -> Color(0xFF9E9E9E)
        LogLevel.INFO -> Color(0xFF2196F3)
        LogLevel.WARNING -> Color(0xFFFF9800)
        LogLevel.ERROR -> Color(0xFFF44336)
    }
}
