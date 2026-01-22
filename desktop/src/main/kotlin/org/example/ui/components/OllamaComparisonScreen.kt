package org.example.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.model.*
import org.example.ui.OllamaComparisonViewModel
import org.example.ui.OllamaTestType

@Composable
fun OllamaComparisonScreen(
    viewModel: OllamaComparisonViewModel,
    onBack: () -> Unit
) {
    val isOllamaRunning by viewModel.isOllamaRunning.collectAsState()
    val installedModels by viewModel.installedModels.collectAsState()
    val selectedModels by viewModel.selectedModels.collectAsState()
    val prompt by viewModel.prompt.collectAsState()
    val benchmarkState by viewModel.benchmarkState.collectAsState()
    val testType by viewModel.testType.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Бенчмарк Ollama",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Статус Ollama
                when (isOllamaRunning) {
                    null -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    true -> {
                        Surface(
                            color = Color(0xFF4CAF50),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Ollama запущен",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    false -> {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Ollama не запущен",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Кнопка обновления статуса
                IconButton(onClick = { viewModel.checkOllamaStatus() }) {
                    Icon(Icons.Default.Refresh, "Обновить статус")
                }

                if (benchmarkState.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { viewModel.cancelBenchmark() }) {
                        Text("Отмена")
                    }
                }
            }
        }

        // Предупреждение если Ollama не запущен
        if (isOllamaRunning == false) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ollama не запущен",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Для работы необходимо:\n" +
                                "1. Установить Ollama: brew install ollama\n" +
                                "2. Запустить сервер: ollama serve\n" +
                                "3. Скачать модели: ollama pull qwen2.5:1.5b",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Секция выбора моделей
            item {
                OllamaBenchmarkModelSelectionSection(
                    selectedModels = selectedModels,
                    installedModels = installedModels,
                    onToggleModel = { viewModel.toggleModelSelection(it) },
                    onSelectCategory = { viewModel.selectAllInCategory(it) },
                    onClearSelection = { viewModel.clearSelection() },
                    isModelInstalled = { viewModel.isModelInstalled(it) }
                )
            }

            // Секция типа теста
            item {
                TestTypeSection(
                    currentType = testType,
                    onTypeChange = { viewModel.setTestType(it) }
                )
            }

            // Секция промпта
            item {
                OllamaPromptSection(
                    prompt = prompt,
                    onPromptChange = { viewModel.setPrompt(it) },
                    onRun = { viewModel.runBenchmark() },
                    isRunning = benchmarkState.isRunning,
                    canRun = selectedModels.isNotEmpty() && prompt.isNotBlank() && isOllamaRunning == true,
                    progress = if (benchmarkState.totalTests > 0) {
                        benchmarkState.progress.toFloat() / benchmarkState.totalTests
                    } else 0f,
                    currentTest = benchmarkState.currentTest
                )
            }

            // Результаты
            if (benchmarkState.results.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Результаты бенчмарка",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        TextButton(onClick = { viewModel.clearResults() }) {
                            Text("Очистить")
                        }
                    }
                }

                item {
                    OllamaSummaryCard(results = benchmarkState.results)
                }

                items(benchmarkState.results.sortedBy { it.responseTimeMs }) { result ->
                    OllamaResultCard(result = result)
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OllamaBenchmarkModelSelectionSection(
    selectedModels: Set<OllamaBenchmarkModel>,
    installedModels: List<String>,
    onToggleModel: (OllamaBenchmarkModel) -> Unit,
    onSelectCategory: (OllamaBenchmarkCategory) -> Unit,
    onClearSelection: () -> Unit,
    isModelInstalled: (String) -> Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Выберите модели для тестирования",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (selectedModels.isNotEmpty()) {
                TextButton(onClick = onClearSelection) {
                    Text("Очистить (${selectedModels.size})")
                }
            }
        }

        // Информация об установленных моделях
        if (installedModels.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Установлено моделей: ${installedModels.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Быстрый выбор по категориям
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OllamaBenchmarkCategory.entries.forEach { category ->
                FilterChip(
                    selected = false,
                    onClick = { onSelectCategory(category) },
                    label = { Text("+ ${category.label}") },
                    leadingIcon = { Text(category.icon) }
                )
            }
        }

        // Категории моделей
        OllamaBenchmarkCategory.entries.forEach { category ->
            OllamaCategorySection(
                category = category,
                models = AvailableOllamaModels.getByCategory(category),
                selectedModels = selectedModels,
                onToggleModel = onToggleModel,
                isModelInstalled = isModelInstalled
            )
        }
    }
}

@Composable
private fun OllamaCategorySection(
    category: OllamaBenchmarkCategory,
    models: List<OllamaBenchmarkModel>,
    selectedModels: Set<OllamaBenchmarkModel>,
    onToggleModel: (OllamaBenchmarkModel) -> Unit,
    isModelInstalled: (String) -> Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = category.icon, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${category.label} модели",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            models.forEach { model ->
                OllamaBenchmarkModelCheckboxItem(
                    model = model,
                    isSelected = selectedModels.contains(model),
                    isInstalled = isModelInstalled(model.id),
                    onToggle = { onToggleModel(model) }
                )
            }
        }
    }
}

@Composable
private fun OllamaBenchmarkModelCheckboxItem(
    model: OllamaBenchmarkModel,
    isSelected: Boolean,
    isInstalled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = model.parameters,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = model.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Статус установки
        if (isInstalled) {
            Surface(
                color = Color(0xFF4CAF50),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "Установлена",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Color.White
                )
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "Не установлена",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun TestTypeSection(
    currentType: OllamaTestType,
    onTypeChange: (OllamaTestType) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Тип теста",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OllamaTestType.entries.forEach { type ->
                    FilterChip(
                        selected = currentType == type,
                        onClick = { onTypeChange(type) },
                        label = { Text(type.label, maxLines = 1) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = currentType.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OllamaPromptSection(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onRun: () -> Unit,
    isRunning: Boolean,
    canRun: Boolean,
    progress: Float,
    currentTest: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Тестовый промпт",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                placeholder = { Text("Введите запрос для тестирования...") },
                minLines = 2,
                enabled = !isRunning
            )

            if (isRunning && progress > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentTest,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onRun,
                    enabled = canRun && !isRunning
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Тестирование...")
                    } else {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Запустить бенчмарк")
                    }
                }
            }
        }
    }
}

@Composable
private fun OllamaSummaryCard(results: List<OllamaTestResult>) {
    val successResults = results.filter { it.isSuccess }
    val fastestResult = successResults.minByOrNull { it.responseTimeMs }
    val mostEfficient = successResults.maxByOrNull { it.tokensPerSecond }
    val avgSpeed = if (successResults.isNotEmpty()) {
        successResults.map { it.tokensPerSecond }.average()
    } else 0.0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Сводка",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OllamaSummaryMetric(
                    icon = "⚡",
                    label = "Быстрейшая",
                    value = fastestResult?.model?.name ?: "—",
                    subValue = fastestResult?.let { "${it.responseTimeMs}ms" }
                )
                OllamaSummaryMetric(
                    icon = "🚀",
                    label = "Эффективная",
                    value = mostEfficient?.model?.name ?: "—",
                    subValue = mostEfficient?.let { "${String.format("%.1f", it.tokensPerSecond)} т/с" }
                )
                OllamaSummaryMetric(
                    icon = "📊",
                    label = "Средняя скорость",
                    value = "${String.format("%.1f", avgSpeed)} т/с",
                    subValue = "${successResults.size}/${results.size} успешно"
                )
            }
        }
    }
}

@Composable
private fun OllamaSummaryMetric(
    icon: String,
    label: String,
    value: String,
    subValue: String? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = icon, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        subValue?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun OllamaResultCard(result: OllamaTestResult) {
    var isExpanded by remember { mutableStateOf(false) }

    val cardColor = if (result.error != null) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { isExpanded = !isExpanded },
        color = cardColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = result.model.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (result.error != null) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = result.config.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Метрики
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OllamaMetricBadge(
                    icon = "⏱️",
                    value = "${result.responseTimeMs}ms",
                    label = "Время"
                )
                OllamaMetricBadge(
                    icon = "📝",
                    value = "~${result.estimatedTokens}",
                    label = "Токенов"
                )
                OllamaMetricBadge(
                    icon = "🚀",
                    value = "${String.format("%.1f", result.tokensPerSecond)} т/с",
                    label = "Скорость"
                )
            }

            // Ошибка или ответ
            if (result.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ошибка: ${result.error}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Ответ модели:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = result.response,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                // Preview ответа
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = result.response.take(150) + if (result.response.length > 150) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun OllamaMetricBadge(
    icon: String,
    value: String,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = icon, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
