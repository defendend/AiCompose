package org.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.example.model.CollectionModeTemplates
import org.example.model.StructuredResponse
import org.example.shared.model.ChatMessage
import org.example.shared.model.CollectionMode
import org.example.shared.model.CollectionSettings
import org.example.shared.model.MessageRole
import org.example.shared.model.ResponseFormat
import org.example.ui.ChatViewModel

private val jsonFormatter = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

// Режимы решения задач для быстрого переключения
private val solveModes = listOf(
    CollectionMode.NONE,
    CollectionMode.SOLVE_DIRECT,
    CollectionMode.SOLVE_STEP_BY_STEP,
    CollectionMode.SOLVE_EXPERT_PANEL
)

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val streamingContent by viewModel.streamingContent.collectAsState()
    val error by viewModel.error.collectAsState()
    val responseFormat by viewModel.responseFormat.collectAsState()
    val collectionSettings by viewModel.collectionSettings.collectAsState()
    val currentNotification by viewModel.currentNotification.collectAsState()

    // Offline mode state
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()
    val ollamaAvailable by viewModel.ollamaAvailable.collectAsState()
    val currentOllamaModel by viewModel.currentOllamaModel.collectAsState()
    val availableOllamaModels by viewModel.availableOllamaModels.collectAsState()
    val lastResponseTime by viewModel.lastResponseTime.collectAsState()
    val generationSpeed by viewModel.generationSpeed.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Проверяем доступность Ollama при запуске
    LaunchedEffect(Unit) {
        viewModel.checkOllamaAvailability()
    }

    // Автоскролл при новых сообщениях или streaming контенте
    LaunchedEffect(messages.size, streamingContent) {
        if (messages.isNotEmpty() || streamingContent.isNotEmpty()) {
            val targetIndex = if (isStreaming) messages.size else messages.size - 1
            if (targetIndex >= 0) {
                // При стриминге используем большой offset чтобы показать низ сообщения
                val scrollOffset = if (isStreaming) 100000 else 0
                listState.animateScrollToItem(targetIndex.coerceAtLeast(0), scrollOffset)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI Agent Chat",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Очистить чат",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Переключатель формата ответа
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Формат:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ResponseFormat.entries.forEach { format ->
                        FilterChip(
                            selected = responseFormat == format,
                            onClick = { viewModel.setResponseFormat(format) },
                            label = { Text(getFormatLabel(format), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                // Переключатель режима решения задач
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Режим:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    solveModes.forEach { mode ->
                        val template = CollectionModeTemplates.getTemplate(mode)
                        val isSelected = collectionSettings.mode == mode ||
                                (mode == CollectionMode.NONE && !collectionSettings.enabled)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (mode == CollectionMode.NONE) {
                                    viewModel.setCollectionSettings(CollectionSettings.DISABLED)
                                } else {
                                    viewModel.setCollectionSettings(CollectionSettings.forMode(mode))
                                }
                            },
                            label = {
                                Text(
                                    "${template.icon} ${getSolveModeLabel(mode)}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                // Переключатель Offline/Online режима
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Индикатор статуса
                    Surface(
                        color = if (isOfflineMode) {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isOfflineMode) "🔌" else "🌐",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = if (isOfflineMode) "Offline" else "Online",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isOfflineMode) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }

                    // Переключатель
                    Switch(
                        checked = isOfflineMode,
                        onCheckedChange = { enabled ->
                            viewModel.setOfflineMode(enabled)
                        },
                        enabled = ollamaAvailable || isOfflineMode,
                        modifier = Modifier.height(24.dp)
                    )

                    // Индикатор доступности Ollama
                    if (!ollamaAvailable) {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Ollama недоступен",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else if (isOfflineMode && availableOllamaModels.isNotEmpty()) {
                        // Выбор модели (dropdown)
                        Box {
                            Surface(
                                onClick = { modelDropdownExpanded = true },
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📦",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        text = currentOllamaModel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "▼",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = modelDropdownExpanded,
                                onDismissRequest = { modelDropdownExpanded = false }
                            ) {
                                availableOllamaModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = model.name,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                    text = formatModelSize(model.size),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.setOllamaModel(model.name)
                                            modelDropdownExpanded = false
                                        },
                                        leadingIcon = {
                                            if (model.name == currentOllamaModel) {
                                                Text("✓", color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Статистика генерации (показываем только в offline режиме после получения ответа)
                if (isOfflineMode && (lastResponseTime != null || generationSpeed != null)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Время ответа
                        lastResponseTime?.let { time ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⏱️", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        text = formatResponseTime(time),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Скорость генерации
                        generationSpeed?.let { speed ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⚡", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        text = "${String.format("%.1f", speed)} tok/s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        // Error message
        error?.let { errorMessage ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Закрыть")
                    }
                }
            }
        }

        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message)
            }

            // Показываем streaming контент пока идёт загрузка
            if (isStreaming && streamingContent.isNotEmpty()) {
                item(key = "streaming") {
                    StreamingBubble(streamingContent)
                }
            } else if (isLoading && !isStreaming) {
                item {
                    LoadingIndicator()
                }
            }
        }

        // Input area
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .onKeyEvent { event ->
                            if (event.key == Key.Enter && event.type == KeyEventType.KeyDown && !event.isShiftPressed) {
                                if (inputText.isNotBlank() && !isLoading) {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                }
                                true
                            } else {
                                false
                            }
                        },
                    placeholder = { Text("Введите сообщение...") },
                    maxLines = 3,
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !isLoading
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Отправить",
                        tint = if (inputText.isNotBlank() && !isLoading)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
        }

        // Snackbar для уведомлений
        currentNotification?.let { notification ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(0.9f),
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = { viewModel.dismissNotification() }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = MaterialTheme.colorScheme.inverseOnSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val backgroundColor = if (isUser)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val textColor = if (isUser)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .padding(12.dp)
        ) {
            Text(
                text = if (isUser) "Вы" else "Агент",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Пробуем распарсить как JSON
            val structuredContent = remember(message.content) {
                tryParseStructuredResponse(message.content)
            }

            if (structuredContent != null) {
                StructuredResponseView(structuredContent, message.content, textColor)
            } else {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }

            // Отображение вызова инструмента (с красивой визуализацией для Pipeline)
            message.toolCall?.let { toolCall ->
                Spacer(modifier = Modifier.height(8.dp))
                if (toolCall.name.startsWith("pipeline_")) {
                    PipelineToolCallView(toolCall, message.toolResult, textColor)
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "🔧 Инструмент: ${toolCall.name}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (toolCall.arguments.isNotBlank()) {
                                Text(
                                    text = toolCall.arguments,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Отображение результата для обычных инструментов
                    message.toolResult?.let { result ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "📋 Результат:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = result.result,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // Отображение информации о токенах (только для ассистента)
            if (!isUser && message.tokenUsage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TokenUsageInfo(message.tokenUsage!!)
            }
        }
    }
}

@Composable
private fun TokenUsageInfo(tokenUsage: org.example.shared.model.TokenUsage) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Токены
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔢",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "${tokenUsage.totalTokens}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "(${tokenUsage.promptTokens}→${tokenUsage.completionTokens})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Стоимость
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💰",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = tokenUsage.formatCost(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun StreamingBubble(content: String) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Агент",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Агент думает...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Красивая визуализация Pipeline инструментов
 */
@Composable
private fun PipelineToolCallView(
    toolCall: org.example.shared.model.ToolCall,
    toolResult: org.example.shared.model.ToolResult?,
    textColor: androidx.compose.ui.graphics.Color
) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Заголовок с иконкой и названием
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Иконка в зависимости от типа инструмента
                val (icon, stepName, stepColor) = when (toolCall.name) {
                    "pipeline_search_docs" -> Triple("🔍", "Поиск документов", MaterialTheme.colorScheme.primary)
                    "pipeline_summarize" -> Triple("📝", "Суммаризация", MaterialTheme.colorScheme.secondary)
                    "pipeline_save_to_file" -> Triple("💾", "Сохранение в файл", MaterialTheme.colorScheme.tertiary)
                    else -> Triple("🔧", toolCall.name, MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Text(
                    text = icon,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(end = 8.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stepName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = stepColor
                    )
                    Text(
                        text = "Шаг пайплайна",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.6f)
                    )
                }

                // Кнопка раскрытия деталей
                TextButton(onClick = { isExpanded = !isExpanded }) {
                    Text(
                        text = if (isExpanded) "Скрыть ▲" else "Детали ▼",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Входные параметры (всегда видны)
            if (toolCall.arguments.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "📥 Входные параметры:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = textColor.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // Парсим JSON для красивого отображения
                        val params = remember(toolCall.arguments) {
                            try {
                                val json = Json.parseToJsonElement(toolCall.arguments).jsonObject
                                json.entries.associate { (key, value) ->
                                    key to value.toString().removeSurrounding("\"")
                                }
                            } catch (e: Exception) {
                                mapOf("raw" to toolCall.arguments)
                            }
                        }

                        params.forEach { (key, value) ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(
                                    text = "$key: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = textColor.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            }

            // Результат (раскрываемый)
            toolResult?.let { result ->
                if (isExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "✅ Результат выполнения:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = result.result,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = textColor.copy(alpha = 0.9f)
                            )
                        }
                    }
                } else {
                    // Показываем краткий превью результата
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "✅",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "Выполнено успешно",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private fun getFormatLabel(format: ResponseFormat): String {
    return when (format) {
        ResponseFormat.PLAIN -> "Текст"
        ResponseFormat.JSON -> "JSON"
        ResponseFormat.MARKDOWN -> "Markdown"
    }
}

private fun getSolveModeLabel(mode: CollectionMode): String {
    return when (mode) {
        CollectionMode.NONE -> "Обычный"
        CollectionMode.SOLVE_DIRECT -> "Прямой"
        CollectionMode.SOLVE_STEP_BY_STEP -> "Пошаговый"
        CollectionMode.SOLVE_EXPERT_PANEL -> "Эксперты"
        else -> "Другой"
    }
}

private fun formatModelSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.0f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.0f KB", bytes / 1_000.0)
        bytes > 0 -> "$bytes B"
        else -> ""
    }
}

private fun formatResponseTime(ms: Long): String {
    return when {
        ms >= 60_000 -> String.format("%.1f мин", ms / 60_000.0)
        ms >= 1_000 -> String.format("%.1f сек", ms / 1_000.0)
        else -> "$ms мс"
    }
}

private fun tryParseStructuredResponse(content: String): StructuredResponse? {
    return try {
        // Пробуем найти JSON в тексте (может быть обёрнут в markdown блок)
        val jsonContent = content
            .replace(Regex("^```json\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^```\\s*$", RegexOption.MULTILINE), "")
            .trim()

        if (jsonContent.startsWith("{") && jsonContent.endsWith("}")) {
            jsonFormatter.decodeFromString<StructuredResponse>(jsonContent)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun StructuredResponseView(
    response: StructuredResponse,
    rawJson: String,
    textColor: androidx.compose.ui.graphics.Color
) {
    var showRawJson by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Тема и период
        if (response.topic.isNotBlank()) {
            Text(
                text = response.topic,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }

        if (response.period.isNotBlank()) {
            Text(
                text = response.period,
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.7f)
            )
        }

        // Краткое резюме
        if (response.summary.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = response.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        // Основной контент
        if (response.main_content.isNotBlank()) {
            Text(
                text = response.main_content,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }

        // Интересные факты
        if (response.interesting_facts.isNotEmpty()) {
            Text(
                text = "Интересные факты:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            response.interesting_facts.forEach { fact ->
                Row(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = fact,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor
                    )
                }
            }
        }

        // Связанные темы
        if (response.related_topics.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                response.related_topics.forEach { topic ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = topic,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Цитата
        if (response.quote.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "💬 ",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "\"${response.quote}\"",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = textColor
                    )
                }
            }
        }

        // Кнопка показать/скрыть raw JSON
        TextButton(
            onClick = { showRawJson = !showRawJson },
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text(
                text = if (showRawJson) "Скрыть JSON" else "Показать JSON",
                style = MaterialTheme.typography.labelSmall
            )
        }

        // Raw JSON
        if (showRawJson) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                val formattedJson = remember(rawJson) {
                    try {
                        val jsonContent = rawJson
                            .replace(Regex("^```json\\s*", RegexOption.MULTILINE), "")
                            .replace(Regex("^```\\s*$", RegexOption.MULTILINE), "")
                            .trim()
                        val element = jsonFormatter.parseToJsonElement(jsonContent)
                        jsonFormatter.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element)
                    } catch (e: Exception) {
                        rawJson
                    }
                }

                Text(
                    text = formattedJson,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = textColor.copy(alpha = 0.8f),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
