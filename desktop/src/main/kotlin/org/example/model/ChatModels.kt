package org.example.model

import kotlinx.serialization.Serializable

/**
 * Модель для парсинга структурированного JSON ответа от агента
 * Специфична для desktop UI — используется только для отображения
 */
@Serializable
data class StructuredResponse(
    val topic: String = "",
    val period: String = "",
    val summary: String = "",
    val main_content: String = "",
    val interesting_facts: List<String> = emptyList(),
    val related_topics: List<String> = emptyList(),
    val quote: String = ""
)
