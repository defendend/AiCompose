package org.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.model.CollectionMode
import org.example.model.CollectionModeTemplate
import org.example.model.CollectionModeTemplates
import org.example.model.CollectionSettings

@Composable
fun SettingsScreen(
    currentSettings: CollectionSettings,
    onSettingsChanged: (CollectionSettings) -> Unit,
    onBack: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(currentSettings.mode) }
    var customPrompt by remember { mutableStateOf(currentSettings.customPrompt) }
    var customResultTitle by remember { mutableStateOf(currentSettings.resultTitle.ifEmpty { "Результат" }) }

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
                    text = "Настройки чата",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Режим сбора данных",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Выберите режим, и модель будет собирать информацию по заданному шаблону, " +
                            "а затем автоматически сформирует результат.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }

            items(CollectionModeTemplates.templates) { template ->
                ModeCard(
                    template = template,
                    isSelected = selectedMode == template.mode,
                    onClick = {
                        selectedMode = template.mode
                        if (template.mode != CollectionMode.CUSTOM) {
                            val settings = CollectionSettings.forMode(template.mode)
                            onSettingsChanged(settings)
                        }
                    }
                )
            }

            // Настройки для пользовательского режима
            if (selectedMode == CollectionMode.CUSTOM) {
                item {
                    CustomModeSettings(
                        customPrompt = customPrompt,
                        customResultTitle = customResultTitle,
                        onPromptChanged = { customPrompt = it },
                        onResultTitleChanged = { customResultTitle = it },
                        onApply = {
                            onSettingsChanged(
                                CollectionSettings(
                                    mode = CollectionMode.CUSTOM,
                                    customPrompt = customPrompt,
                                    resultTitle = customResultTitle,
                                    enabled = true
                                )
                            )
                        }
                    )
                }
            }

            // Кнопка сброса
            if (selectedMode != CollectionMode.NONE) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            selectedMode = CollectionMode.NONE
                            customPrompt = ""
                            customResultTitle = "Результат"
                            onSettingsChanged(CollectionSettings.DISABLED)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Отключить режим сбора")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeCard(
    template: CollectionModeTemplate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = template.icon,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(end = 12.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = template.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Выбрано",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                if (template.requiredFields.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Что соберу:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        template.requiredFields.forEach { field ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = field,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomModeSettings(
    customPrompt: String,
    customResultTitle: String,
    onPromptChanged: (String) -> Unit,
    onResultTitleChanged: (String) -> Unit,
    onApply: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Настройки пользовательского режима",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = customResultTitle,
                onValueChange = onResultTitleChanged,
                label = { Text("Название результата") },
                placeholder = { Text("Например: Маркетинговый план") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = customPrompt,
                onValueChange = onPromptChanged,
                label = { Text("Описание того, что нужно собрать") },
                placeholder = {
                    Text(
                        "Опишите, какую информацию должна собрать модель.\n" +
                        "Например: Собери информацию о бюджете, сроках, целях и KPI для маркетинговой кампании"
                    )
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                minLines = 4
            )

            Button(
                onClick = onApply,
                modifier = Modifier.align(Alignment.End),
                enabled = customPrompt.isNotBlank()
            ) {
                Text("Применить")
            }
        }
    }
}
