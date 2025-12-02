package org.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.model.*
import org.example.network.ChatApiClient
import org.example.ui.theme.AppTheme

@Composable
fun ServerLogWindow(
    apiClient: ChatApiClient,
    onCloseRequest: () -> Unit
) {
    Window(
        onCloseRequest = onCloseRequest,
        title = "Серверные логи",
        state = WindowState(width = 1000.dp, height = 600.dp)
    ) {
        AppTheme {
            ServerLogWindowContent(apiClient)
        }
    }
}

@Composable
private fun ServerLogWindowContent(apiClient: ChatApiClient) {
    var logs by remember { mutableStateOf<List<ServerLogEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var autoRefresh by remember { mutableStateOf(true) }
    var filterCategory by remember { mutableStateOf<ServerLogCategory?>(null) }
    var filterLevel by remember { mutableStateOf<ServerLogLevel?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val filteredLogs = remember(logs, filterCategory, filterLevel) {
        logs
            .let { list -> filterCategory?.let { cat -> list.filter { it.category == cat } } ?: list }
            .let { list -> filterLevel?.let { lvl -> list.filter { it.level == lvl } } ?: list }
    }

    fun loadLogs() {
        scope.launch {
            isLoading = true
            apiClient.getServerLogs(200).onSuccess {
                logs = it.logs
            }
            isLoading = false
        }
    }

    fun clearLogs() {
        scope.launch {
            apiClient.clearServerLogs().onSuccess {
                logs = emptyList()
            }
        }
    }

    LaunchedEffect(Unit) {
        loadLogs()
    }

    LaunchedEffect(autoRefresh) {
        while (autoRefresh) {
            delay(3000)
            loadLogs()
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
            Column {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Серверные логи (${filteredLogs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = autoRefresh,
                            onCheckedChange = { autoRefresh = it }
                        )
                        Text("Авто-обновление", style = MaterialTheme.typography.bodySmall)
                    }

                    IconButton(onClick = { loadLogs() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Обновить",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { clearLogs() }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Очистить логи",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Category filters
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = filterCategory == null,
                        onClick = { filterCategory = null },
                        label = { Text("Все", fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                    ServerLogCategory.entries.forEach { category ->
                        FilterChip(
                            selected = filterCategory == category,
                            onClick = { filterCategory = if (filterCategory == category) null else category },
                            label = { Text(getCategoryLabel(category), fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = getCategoryColor(category).copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                // Level filters
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Уровень:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterVertically))
                    ServerLogLevel.entries.forEach { level ->
                        FilterChip(
                            selected = filterLevel == level,
                            onClick = { filterLevel = if (filterLevel == level) null else level },
                            label = { Text(level.name, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = getLevelColor(level).copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        }

        // Logs list
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filteredLogs, key = { it.id }) { log ->
                ServerLogEntryRow(log)
            }
        }
    }
}

@Composable
private fun ServerLogEntryRow(log: ServerLogEntry) {
    var expanded by remember { mutableStateOf(false) }
    val hasDetails = log.details != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = getCategoryColor(log.category).copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (hasDetails) Modifier.clickable { expanded = !expanded } else Modifier),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasDetails) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    Spacer(modifier = Modifier.width(16.dp))
                }

                Text(
                    text = log.timestamp,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Surface(
                    color = getLevelColor(log.level).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = log.level.name,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = getLevelColor(log.level)
                    )
                }

                Surface(
                    color = getCategoryColor(log.category).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = getCategoryLabel(log.category),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                log.details?.durationMs?.let { duration ->
                    Text(
                        text = "${duration}ms",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = log.message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(
                visible = expanded && hasDetails,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                log.details?.let { details ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            details.method?.let { DetailRow("Method", it) }
                            details.path?.let { DetailRow("Path", it) }
                            details.statusCode?.let { DetailRow("Status", it.toString()) }
                            details.conversationId?.let { DetailRow("Conversation", it) }
                            details.model?.let { DetailRow("Model", it) }
                            details.toolName?.let { DetailRow("Tool", it) }
                            details.toolArguments?.let { DetailRow("Arguments", it) }
                            details.toolResult?.let { DetailRow("Result", it) }
                            details.requestBody?.let { DetailRow("Request Body", it) }
                            details.responseBody?.let { DetailRow("Response Body", it) }
                            details.error?.let { DetailRow("Error", it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun getLevelColor(level: ServerLogLevel): Color {
    return when (level) {
        ServerLogLevel.DEBUG -> Color(0xFF9E9E9E)
        ServerLogLevel.INFO -> Color(0xFF2196F3)
        ServerLogLevel.WARNING -> Color(0xFFFF9800)
        ServerLogLevel.ERROR -> Color(0xFFF44336)
    }
}

@Composable
private fun getCategoryColor(category: ServerLogCategory): Color {
    return when (category) {
        ServerLogCategory.REQUEST -> Color(0xFF4CAF50)
        ServerLogCategory.RESPONSE -> Color(0xFF8BC34A)
        ServerLogCategory.LLM_REQUEST -> Color(0xFF9C27B0)
        ServerLogCategory.LLM_RESPONSE -> Color(0xFFE91E63)
        ServerLogCategory.TOOL_CALL -> Color(0xFFFF9800)
        ServerLogCategory.TOOL_RESULT -> Color(0xFFFFC107)
        ServerLogCategory.SYSTEM -> Color(0xFF607D8B)
    }
}

private fun getCategoryLabel(category: ServerLogCategory): String {
    return when (category) {
        ServerLogCategory.REQUEST -> "REQ"
        ServerLogCategory.RESPONSE -> "RES"
        ServerLogCategory.LLM_REQUEST -> "LLM-REQ"
        ServerLogCategory.LLM_RESPONSE -> "LLM-RES"
        ServerLogCategory.TOOL_CALL -> "TOOL"
        ServerLogCategory.TOOL_RESULT -> "TOOL-RES"
        ServerLogCategory.SYSTEM -> "SYS"
    }
}
