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
import org.example.ui.TokenDemoState

@Composable
fun ModelComparisonScreen(
    viewModel: ModelComparisonViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val selectedModels by viewModel.selectedModels.collectAsState()
    val prompt by viewModel.prompt.collectAsState()
    val tokenDemoState by viewModel.tokenDemoState.collectAsState()

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

            // Секция демо токенов
            item {
                TokenDemoSection(
                    tokenDemoState = tokenDemoState,
                    onRunDemo = { includeOverLimit -> viewModel.runTokenDemo(includeOverLimit) },
                    onClear = { viewModel.clearTokenDemo() },
                    hasApiToken = viewModel.hasApiToken
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

// === Секция демо сравнения токенов ===

@Composable
private fun TokenDemoSection(
    tokenDemoState: TokenDemoState,
    onRunDemo: (Boolean) -> Unit,
    onClear: () -> Unit,
    hasApiToken: Boolean
) {
    var includeOverLimit by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🔬", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Демо: Сравнение токенов",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (tokenDemoState.results != null) {
                    TextButton(onClick = onClear) {
                        Text("Очистить")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Сравните поведение моделей с разными размерами запросов: короткий (~20 токенов), средний (~200), длинный (~2000) и превышающий лимит.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Опция включения теста на превышение лимита
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { includeOverLimit = !includeOverLimit }
            ) {
                Checkbox(
                    checked = includeOverLimit,
                    onCheckedChange = { includeOverLimit = it }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Включить тест на превышение лимита (долго!)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { onRunDemo(includeOverLimit) },
                    enabled = hasApiToken && !tokenDemoState.isRunning
                ) {
                    if (tokenDemoState.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tokenDemoState.progress.ifEmpty { "Тестирование..." })
                    } else {
                        Icon(Icons.Default.Science, null, Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Запустить демо")
                    }
                }
            }

            // Ошибка
            tokenDemoState.error?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Ошибка: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Результаты
            tokenDemoState.results?.let { summary ->
                Spacer(modifier = Modifier.height(16.dp))

                // Сводка
                TokenDemoSummaryCard(summary)

                Spacer(modifier = Modifier.height(12.dp))

                // Результаты по моделям
                summary.modelResults.forEach { (model, results) ->
                    TokenDemoModelCard(model, results)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun TokenDemoSummaryCard(summary: org.example.demo.HuggingFaceTokenDemo.TokenComparisonSummary) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📊 Анализ поведения моделей",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            summary.insights.forEach { insight ->
                val textColor = when {
                    insight.startsWith("═══") -> MaterialTheme.colorScheme.primary
                    insight.startsWith("📊") || insight.startsWith("🥇") || insight.startsWith("🐢") -> MaterialTheme.colorScheme.onPrimaryContainer
                    insight.contains("✅") -> Color(0xFF2E7D32)  // Более тёмный зелёный
                    insight.contains("⚠️") || insight.contains("❌") -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }

                val fontWeight = if (insight.startsWith("═══") || insight.startsWith("📊")) FontWeight.Bold else FontWeight.Normal
                val textStyle = if (insight.startsWith("═══")) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium

                if (insight.isNotBlank()) {
                    Text(
                        text = insight,
                        style = textStyle,
                        fontWeight = fontWeight,
                        color = textColor
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun TokenDemoModelCard(
    model: HuggingFaceModel,
    results: List<org.example.demo.HuggingFaceTokenDemo.TokenTestResult>
) {
    var isExpanded by remember { mutableStateOf(true) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { isExpanded = !isExpanded },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Заголовок модели
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = model.parameters,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))

                // Таблица результатов
                results.forEach { result ->
                    TokenTestResultRow(result)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun TokenTestResultRow(result: org.example.demo.HuggingFaceTokenDemo.TokenTestResult) {
    var showResponse by remember { mutableStateOf(false) }

    val bgColor = if (result.success) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.errorContainer
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Основная строка с метриками
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (result.success) showResponse = !showResponse },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Тип теста
                Column(modifier = Modifier.weight(0.2f)) {
                    Text(
                        text = result.testType.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (result.success) MaterialTheme.colorScheme.onSurface
                               else MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = result.testType.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.success) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                               else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                }

                if (result.success) {
                    // Токены
                    Column(
                        modifier = Modifier.weight(0.22f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${result.actualPromptTokens}→${result.actualCompletionTokens}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "токенов",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    // Время
                    Column(
                        modifier = Modifier.weight(0.18f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${result.responseTimeMs}ms",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (result.responseTimeMs < 2000)
                                Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                        Text(
                            text = "время",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    // Скорость
                    Column(
                        modifier = Modifier.weight(0.18f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = result.tokensPerSecond?.let { "${"%.0f".format(it)}/с" } ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "скорость",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    // Стоимость
                    Column(
                        modifier = Modifier.weight(0.18f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = result.cost?.let {
                                if (it < 0.0001) "$%.7f".format(it)
                                else "$%.5f".format(it)
                            } ?: "FREE",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "стоимость",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    // Стрелка раскрытия
                    Icon(
                        if (showResponse) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    // Ошибка
                    Column(modifier = Modifier.weight(0.65f)) {
                        Text(
                            text = "❌ ${result.error?.take(80) ?: "Ошибка"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 2
                        )
                    }
                }
            }

            // Ответ модели (раскрывается по клику)
            if (showResponse && result.success && result.fullResponse != null) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "💬 Ответ модели:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = result.fullResponse,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
