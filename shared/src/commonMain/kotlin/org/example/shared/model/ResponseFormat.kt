package org.example.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class ResponseFormat {
    PLAIN,      // Простой текст
    JSON,       // Структурированный JSON
    MARKDOWN    // Markdown форматирование
}
