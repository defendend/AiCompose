package org.example.analytics.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.analytics.components.*
import org.example.analytics.viewmodel.AnalyticsViewModel
import org.koin.compose.koinInject

/**
 * Экран аналитики данных.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = koinInject(),
    onBack: (() -> Unit)? = null
) {
    val state = viewModel.state

    LaunchedEffect(Unit) {
        viewModel.checkModelAvailability()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null
                        )
                        Text("Локальный аналитик")
                    }
                },
                navigationIcon = {
                    onBack?.let { backAction ->
                        IconButton(onClick = backAction) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Назад"
                            )
                        }
                    }
                },
                actions = {
                    if (state.loadedFiles.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAllFiles() }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Очистить все файлы"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.loadedFiles.isEmpty()) {
                // Экран загрузки данных
                EmptyDataScreen(
                    onFileSelected = { viewModel.loadDataFile(it) },
                    isLoading = state.isLoading,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Основной экран аналитики
                AnalyticsMainContent(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Показываем ошибки
            state.error?.let { error ->
                LaunchedEffect(error) {
                    // Автоматически скрываем ошибку через 5 секунд
                    kotlinx.coroutines.delay(5000)
                    viewModel.clearError()
                }

                SnackbarHost(
                    hostState = remember { SnackbarHostState() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Snackbar(
                        action = {
                            TextButton(onClick = { viewModel.clearError() }) {
                                Text("Закрыть")
                            }
                        }
                    ) {
                        Text(error)
                    }
                }
            }
        }
    }
}

/**
 * Основной контент экрана аналитики.
 */
@Composable
private fun AnalyticsMainContent(
    state: org.example.analytics.model.AnalyticsState,
    viewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Левая панель - управление данными
        Card(
            modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Загруженные данные",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.loadedFiles) { dataFile ->
                        DataFileCard(
                            data = dataFile,
                            onRemove = { viewModel.removeDataFile(dataFile.fileName) }
                        )
                    }

                    item {
                        AddDataFileButton(
                            onFileSelected = { viewModel.loadDataFile(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Быстрые вопросы
                val suggestedQuestions = remember(state.loadedFiles) {
                    viewModel.getSuggestedQuestions()
                }

                if (suggestedQuestions.isNotEmpty()) {
                    Text(
                        "Быстрые вопросы",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    suggestedQuestions.take(4).forEach { suggestion ->
                        QuickQuestionButton(
                            suggestion = suggestion,
                            onClick = { viewModel.performSuggestedAnalysis(suggestion) },
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Правая панель - чат с аналитиком
        Card(
            modifier = Modifier
                .weight(0.7f)
                .fillMaxHeight()
        ) {
            AnalyticsChat(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}