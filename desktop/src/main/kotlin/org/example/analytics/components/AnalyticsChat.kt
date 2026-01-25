package org.example.analytics.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import org.example.analytics.model.*
import org.example.analytics.viewmodel.AnalyticsViewModel

/**
 * Компонент чата с аналитиком.
 */
@Composable
fun AnalyticsChat(
    state: AnalyticsState,
    viewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier
) {
    var queryText by remember { mutableStateOf("") }
    var currentStreamingResponse by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Слушаем потоковые ответы
    LaunchedEffect(Unit) {
        viewModel.streamingResponse.collectLatest { chunk ->
            if (chunk.isComplete) {
                currentStreamingResponse = ""
            } else {
                currentStreamingResponse += chunk.content
            }

            // Автоскролл к концу
            if (state.queryHistory.isNotEmpty() || currentStreamingResponse.isNotEmpty()) {
                listState.animateScrollToItem(
                    index = maxOf(0, state.queryHistory.size * 2 + if (currentStreamingResponse.isNotEmpty()) 1 else 0)
                )
            }
        }
    }

    Column(
        modifier = modifier.padding(16.dp)
    ) {
        // Заголовок чата
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Аналитик (${state.selectedModel})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (state.queryHistory.isNotEmpty()) {
                IconButton(onClick = { viewModel.clearHistory() }) {
                    Icon(
                        imageVector = Icons.Default.ClearAll,
                        contentDescription = "Очистить историю"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Область сообщений
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.queryHistory.isEmpty() && currentStreamingResponse.isEmpty()) {
                item {
                    WelcomeMessage(
                        dataFiles = state.loadedFiles,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // История сообщений
            items(state.queryHistory) { result ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Вопрос пользователя
                    UserMessage(
                        question = result.question,
                        timestamp = result.timestamp
                    )

                    // Ответ аналитика
                    AnalystMessage(
                        result = result
                    )
                }
            }

            // Текущий потоковый ответ
            if (currentStreamingResponse.isNotEmpty()) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.currentQuery?.let { currentQuery ->
                            UserMessage(
                                question = currentQuery.question,
                                timestamp = currentQuery.timestamp
                            )
                        }

                        StreamingAnalystMessage(
                            content = currentStreamingResponse
                        )
                    }
                }
            }

            // Индикатор загрузки
            if (state.isLoading && currentStreamingResponse.isEmpty()) {
                item {
                    LoadingMessage()
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Поле ввода
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = queryText,
                onValueChange = { queryText = it },
                placeholder = { Text("Задайте вопрос о данных...") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (queryText.isNotBlank() && !state.isLoading) {
                            viewModel.performAnalysis(queryText)
                            queryText = ""
                        }
                    }
                ),
                maxLines = 3
            )

            FilledIconButton(
                onClick = {
                    if (queryText.isNotBlank() && !state.isLoading) {
                        viewModel.performAnalysis(queryText)
                        queryText = ""
                    }
                },
                enabled = queryText.isNotBlank() && !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Отправить вопрос"
                    )
                }
            }
        }
    }
}

/**
 * Приветственное сообщение.
 */
@Composable
private fun WelcomeMessage(
    dataFiles: List<ParsedData>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Локальный аналитик готов к работе! 🚀",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "Я проанализирую ваши данные с помощью локальной модели. " +
                        "Загружено файлов: ${dataFiles.size}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                "Попробуйте задать вопросы вроде:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "• Какие основные тренды видны в данных?",
                    "• Есть ли аномалии или выбросы?",
                    "• Какие ошибки чаще всего встречаются?",
                    "• Покажи статистику по числовым показателям"
                ).forEach { example ->
                    Text(
                        example,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Сообщение пользователя.
 */
@Composable
private fun UserMessage(
    question: String,
    timestamp: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 4.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )

                Text(
                    formatTime(timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Ответ аналитика.
 */
@Composable
private fun AnalystMessage(
    result: AnalyticsResult,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Аналитик",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    result.answer,
                    style = MaterialTheme.typography.bodyMedium
                )

                // Инсайты
                if (result.insights.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Ключевые находки:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        result.insights.forEach { insight ->
                            Text(
                                insight,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatTime(result.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    if (result.executionTimeMs > 0) {
                        Text(
                            "${result.executionTimeMs}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Потоковое сообщение аналитика.
 */
@Composable
private fun StreamingAnalystMessage(
    content: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp
                    )
                    Text(
                        "Аналитик анализирует...",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (content.isNotEmpty()) {
                    Text(
                        content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * Сообщение о загрузке.
 */
@Composable
private fun LoadingMessage(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    "Локальная модель анализирует данные...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * Форматирует время для отображения.
 */
private fun formatTime(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return format.format(date)
}