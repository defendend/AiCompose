package org.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
    val error by viewModel.error.collectAsState()
    val responseFormat by viewModel.responseFormat.collectAsState()
    val collectionSettings by viewModel.collectionSettings.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

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
                    modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
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

            if (isLoading) {
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

            // Отображение вызова инструмента
            message.toolCall?.let { toolCall ->
                Spacer(modifier = Modifier.height(8.dp))
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
            }

            // Отображение результата инструмента
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
