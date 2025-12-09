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
import org.example.ui.ModelComparisonViewModel

@Composable
fun ModelComparisonScreen(
    viewModel: ModelComparisonViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val selectedModels by viewModel.selectedModels.collectAsState()
    val prompt by viewModel.prompt.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Предупреждение об отсутствии токена
        if (!viewModel.hasApiToken) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HF_TOKEN не задан. Установите переменную окружения HF_TOKEN для доступа к HuggingFace API.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

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
                    text = "Сравнение моделей",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.weight(1f))

                if (state.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { viewModel.cancelComparison() }) {
                        Text("Отмена")
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Секция выбора моделей
            item {
                ModelSelectionSection(
                    selectedModels = selectedModels,
                    onToggleModel = { viewModel.toggleModelSelection(it) },
                    onSelectCategory = { viewModel.selectAllInCategory(it) },
                    onClearSelection = { viewModel.clearSelection() }
                )
            }

            // Секция промпта
            item {
                PromptSection(
                    prompt = prompt,
                    onPromptChange = { viewModel.setPrompt(it) },
                    onRun = { viewModel.runComparison() },
                    isRunning = state.isRunning,
                    canRun = selectedModels.isNotEmpty() && prompt.isNotBlank()
                )
            }

            // Результаты
            if (state.results.isNotEmpty()) {
                item {
                    Text(
                        text = "Результаты сравнения",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                item {
                    ResultsSummaryCard(results = state.results)
                }

                items(state.results.sortedBy { it.responseTimeMs }) { result ->
                    ResultCard(result = result)
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
private fun ModelSelectionSection(
    selectedModels: Set<HuggingFaceModel>,
    onToggleModel: (HuggingFaceModel) -> Unit,
    onSelectCategory: (ModelCategory) -> Unit,
    onClearSelection: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Выберите модели для сравнения",
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

        // Быстрый выбор по категориям
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = false,
                onClick = { onSelectCategory(ModelCategory.TOP) },
                label = { Text("+ Топовые") },
                leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = false,
                onClick = { onSelectCategory(ModelCategory.MIDDLE) },
                label = { Text("+ Средние") }
            )
            FilterChip(
                selected = false,
                onClick = { onSelectCategory(ModelCategory.SMALL) },
                label = { Text("+ Компактные") }
            )
        }

        // Топовые модели
        CategorySection(
            title = "Топовые модели (большие)",
            icon = "🏆",
            models = AvailableModels.topModels,
            selectedModels = selectedModels,
            onToggleModel = onToggleModel
        )

        // Средние модели
        CategorySection(
            title = "Средние модели",
            icon = "⚖️",
            models = AvailableModels.middleModels,
            selectedModels = selectedModels,
            onToggleModel = onToggleModel
        )

        // Маленькие модели
        CategorySection(
            title = "Компактные модели (быстрые)",
            icon = "🚀",
            models = AvailableModels.smallModels,
            selectedModels = selectedModels,
            onToggleModel = onToggleModel
        )
    }
}

@Composable
private fun CategorySection(
    title: String,
    icon: String,
    models: List<HuggingFaceModel>,
    selectedModels: Set<HuggingFaceModel>,
    onToggleModel: (HuggingFaceModel) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            models.forEach { model ->
                ModelCheckboxItem(
                    model = model,
                    isSelected = selectedModels.contains(model),
                    onToggle = { onToggleModel(model) }
                )
            }
        }
    }
}

@Composable
private fun ModelCheckboxItem(
    model: HuggingFaceModel,
    isSelected: Boolean,
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

        // Цена
        model.pricing?.let { pricing ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "$${pricing.inputPer1kTokens}/1k",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        } ?: Surface(
            color = Color(0xFF66BB6A),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = "FREE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                color = Color.White
            )
        }
    }
}

@Composable
private fun PromptSection(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onRun: () -> Unit,
    isRunning: Boolean,
    canRun: Boolean
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
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                placeholder = { Text("Введите запрос для тестирования...") },
                minLines = 3,
                enabled = !isRunning
            )

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
                        Text("Запустить сравнение")
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsSummaryCard(results: List<ModelComparisonResult>) {
    val successResults = results.filter { it.error == null }
    val fastestResult = successResults.minByOrNull { it.responseTimeMs }
    val cheapestResult = successResults.filter { it.totalCost != null }.minByOrNull { it.totalCost!! }
    val totalCost = successResults.mapNotNull { it.totalCost }.sum()

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
                SummaryMetric(
                    icon = "⚡",
                    label = "Быстрейшая",
                    value = fastestResult?.model?.name ?: "—",
                    subValue = fastestResult?.let { "${it.responseTimeMs}ms" }
                )
                SummaryMetric(
                    icon = "💰",
                    label = "Дешевейшая",
                    value = cheapestResult?.model?.name ?: "—",
                    subValue = cheapestResult?.totalCost?.let { String.format("$%.6f", it) }
                )
                SummaryMetric(
                    icon = "📊",
                    label = "Общая стоимость",
                    value = String.format("$%.6f", totalCost),
                    subValue = "${successResults.size}/${results.size} успешно"
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(
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
private fun ResultCard(result: ModelComparisonResult) {
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
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = result.model.parameters,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.primary
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
                MetricBadge(
                    icon = "⏱️",
                    value = "${result.responseTimeMs}ms",
                    label = "Время"
                )
                MetricBadge(
                    icon = "📝",
                    value = "${result.inputTokens}/${result.outputTokens}",
                    label = "In/Out"
                )
                result.totalCost?.let {
                    MetricBadge(
                        icon = "💵",
                        value = String.format("$%.6f", it),
                        label = "Стоимость"
                    )
                } ?: MetricBadge(
                    icon = "🆓",
                    value = "FREE",
                    label = "Стоимость"
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
private fun MetricBadge(
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
