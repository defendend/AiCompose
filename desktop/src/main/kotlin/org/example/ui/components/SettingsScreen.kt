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

// Предустановленные персонажи агента
data class AgentPersona(
    val name: String,
    val icon: String,
    val description: String,
    val systemPrompt: String
)

private val agentPersonas = listOf(
    AgentPersona(
        name = "Профессор Архивариус",
        icon = "📚",
        description = "Увлечённый историк с энциклопедическими знаниями",
        systemPrompt = "" // Пустой = использовать дефолтный
    ),
    AgentPersona(
        name = "Пират Джек",
        icon = "🏴‍☠️",
        description = "Морской волк, говорит на пиратском жаргоне",
        systemPrompt = """Ты — пират Джек Воробей, легендарный морской волк и искатель сокровищ.

Твой характер:
• Говоришь на пиратском жаргоне: "Йо-хо-хо!", "Тысяча чертей!", "Разрази меня гром!"
• Всё сравниваешь с морем, кораблями и пиратской жизнью
• Любишь рассказывать байки о своих приключениях
• Иногда вставляешь "Аррр!" в речь

Отвечай на русском языке, но в стиле пирата!"""
    ),
    AgentPersona(
        name = "Шерлок Холмс",
        icon = "🔍",
        description = "Гениальный детектив, логик и аналитик",
        systemPrompt = """Ты — Шерлок Холмс, величайший детектив всех времён.

Твой характер:
• Мыслишь логически и дедуктивно
• Замечаешь мельчайшие детали, которые другие упускают
• Говоришь: "Элементарно!", "Факты, только факты!"
• Объясняешь ход своих рассуждений
• Иногда снисходителен к "очевидным" вещам

Отвечай на русском языке, анализируя всё как детектив!"""
    ),
    AgentPersona(
        name = "Йода",
        icon = "🧙",
        description = "Мудрый джедай, говорит инверсиями",
        systemPrompt = """Ты — мастер Йода, мудрейший джедай галактики.

Твой характер:
• Говоришь инверсиями: "Сильным станешь ты" вместо "Ты станешь сильным"
• Делишься мудростью Силы
• Используешь метафоры о Светлой и Тёмной стороне
• Философствуешь о терпении и внутреннем покое

Примеры: "Делай или не делай. Не пробуй.", "Страх ведёт к гневу, гнев ведёт к ненависти."

Отвечай на русском языке в стиле Йоды!"""
    ),
    AgentPersona(
        name = "Формальный ассистент",
        icon = "👔",
        description = "Строгий, деловой, без лишних слов",
        systemPrompt = """Ты — профессиональный бизнес-ассистент.

Правила:
• Отвечай кратко и по существу
• Используй формальный деловой стиль
• Структурируй информацию списками и пунктами
• Избегай эмоций и неформальных выражений
• Фокусируйся на фактах и практических рекомендациях

Отвечай на русском языке в деловом стиле."""
    )
)

@Composable
fun SettingsScreen(
    currentSettings: CollectionSettings,
    onSettingsChanged: (CollectionSettings) -> Unit,
    onBack: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(currentSettings.mode) }
    var customPrompt by remember { mutableStateOf(currentSettings.customPrompt) }
    var customResultTitle by remember { mutableStateOf(currentSettings.resultTitle.ifEmpty { "Результат" }) }
    var customSystemPrompt by remember { mutableStateOf(currentSettings.customSystemPrompt) }
    var selectedPersonaIndex by remember { mutableStateOf(0) }

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
            // === Секция персонажа агента ===
            item {
                Text(
                    text = "Персонаж агента (System Prompt)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Выберите персонажа или напишите свой системный промпт. " +
                            "Изменение персонажа влияет на стиль ответов агента.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }

            // Карточки персонажей
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    agentPersonas.forEachIndexed { index, persona ->
                        PersonaCard(
                            persona = persona,
                            isSelected = selectedPersonaIndex == index && customSystemPrompt == persona.systemPrompt,
                            onClick = {
                                selectedPersonaIndex = index
                                customSystemPrompt = persona.systemPrompt
                                onSettingsChanged(
                                    currentSettings.copy(customSystemPrompt = persona.systemPrompt)
                                )
                            }
                        )
                    }
                }
            }

            // Редактор своего промпта
            item {
                SystemPromptEditor(
                    currentPrompt = customSystemPrompt,
                    onPromptChanged = { newPrompt ->
                        customSystemPrompt = newPrompt
                        selectedPersonaIndex = -1 // Снимаем выбор с персонажей
                    },
                    onApply = {
                        onSettingsChanged(
                            currentSettings.copy(customSystemPrompt = customSystemPrompt)
                        )
                    }
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // === Секция режимов сбора данных ===
            item {
                Text(
                    text = "Режим сбора данных",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Выберите режим, и модель будет собирать информацию по заданному шаблону.",
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
                                .copy(customSystemPrompt = customSystemPrompt)
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
                                    enabled = true,
                                    customSystemPrompt = customSystemPrompt
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
                            onSettingsChanged(CollectionSettings.DISABLED.copy(customSystemPrompt = customSystemPrompt))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Отключить режим сбора")
                    }
                }
            }

            // Отступ внизу
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PersonaCard(
    persona: AgentPersona,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = persona.icon,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(end = 12.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = persona.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = persona.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Выбрано",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SystemPromptEditor(
    currentPrompt: String,
    onPromptChanged: (String) -> Unit,
    onApply: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✏️ Написать свой System Prompt",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isExpanded) "▲" else "▼",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = currentPrompt,
                    onValueChange = onPromptChanged,
                    label = { Text("System Prompt") },
                    placeholder = {
                        Text("Опишите характер, стиль речи и поведение агента...")
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                    minLines = 6
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onApply,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Применить")
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
        MaterialTheme.colorScheme.outline
    }

    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
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
                        color = contentColor
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
                    color = contentColor.copy(alpha = 0.8f)
                )

                if (template.requiredFields.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Что соберу:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = contentColor.copy(alpha = 0.9f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        template.requiredFields.forEach { field ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = field,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
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
