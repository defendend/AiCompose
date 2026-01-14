package org.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.example.ui.SupportMessage
import org.example.ui.SupportViewModel

/**
 * Экран поддержки пользователей.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    viewModel: SupportViewModel,
    onBack: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentTicketId by viewModel.currentTicketId.collectAsState()
    val error by viewModel.error.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var ticketIdInput by remember { mutableStateOf("") }
    var showTicketDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Автоскролл к новым сообщениям
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Поддержка")
                        currentTicketId?.let {
                            Text(
                                text = "Контекст: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    // Кнопка установки контекста тикета
                    IconButton(onClick = { showTicketDialog = true }) {
                        Icon(Icons.Default.Info, "Контекст тикета")
                    }
                    // Кнопка очистки
                    IconButton(onClick = { viewModel.clearHistory() }) {
                        Icon(Icons.Default.Clear, "Очистить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Список сообщений
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) { message ->
                    SupportMessageBubble(message = message)
                }

                // Индикатор загрузки
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text("Обрабатываю запрос...")
                                }
                            }
                        }
                    }
                }
            }

            // Быстрые действия
            QuickActionsRow(
                onAction = { action ->
                    inputText = action
                }
            )

            // Поле ввода
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Введите вопрос...") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank() && !isLoading) {
                                    viewModel.sendQuestion(inputText)
                                    inputText = ""
                                }
                            }
                        ),
                        singleLine = true,
                        enabled = !isLoading
                    )

                    Spacer(Modifier.width(8.dp))

                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendQuestion(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank() && !isLoading
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Отправить")
                    }
                }
            }
        }
    }

    // Диалог установки контекста тикета
    if (showTicketDialog) {
        AlertDialog(
            onDismissRequest = { showTicketDialog = false },
            title = { Text("Контекст тикета") },
            text = {
                Column {
                    Text("Укажите ID тикета для контекста (например TKT-001)")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ticketIdInput,
                        onValueChange = { ticketIdInput = it },
                        placeholder = { Text("TKT-001") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setTicketContext(ticketIdInput.takeIf { it.isNotBlank() })
                        showTicketDialog = false
                    }
                ) {
                    Text("Установить")
                }
            },
            dismissButton = {
                Row {
                    if (currentTicketId != null) {
                        TextButton(
                            onClick = {
                                viewModel.setTicketContext(null)
                                ticketIdInput = ""
                                showTicketDialog = false
                            }
                        ) {
                            Text("Очистить")
                        }
                    }
                    TextButton(onClick = { showTicketDialog = false }) {
                        Text("Отмена")
                    }
                }
            }
        )
    }
}

/**
 * Пузырь сообщения.
 */
@Composable
private fun SupportMessageBubble(message: SupportMessage) {
    val isUser = message.isUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 600.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.secondaryContainer
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Контент сообщения
                Text(
                    text = message.content,
                    color = if (isUser)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )

                // Метаданные
                if (!isUser && message.durationMs != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "⏱ ${message.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * Строка быстрых действий.
 */
@Composable
private fun QuickActionsRow(onAction: (String) -> Unit) {
    val actions = listOf(
        "Статистика" to "Покажи статистику поддержки",
        "Открытые тикеты" to "Покажи все открытые тикеты",
        "FAQ" to "Покажи FAQ по авторизации",
        "TKT-001" to "Расскажи о тикете TKT-001"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { (label, query) ->
            SuggestionChip(
                onClick = { onAction(query) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
