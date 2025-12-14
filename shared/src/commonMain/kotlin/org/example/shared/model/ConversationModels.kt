package org.example.shared.model

import kotlinx.serialization.Serializable

/**
 * Информация о диалоге для списка чатов
 */
@Serializable
data class ConversationInfo(
    val id: String,
    val title: String,
    val lastMessage: String? = null,
    val messageCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Ответ со списком диалогов
 */
@Serializable
data class ConversationListResponse(
    val conversations: List<ConversationInfo>,
    val totalCount: Int
)

/**
 * Результат поиска по сообщениям
 */
@Serializable
data class SearchResult(
    val conversationId: String,
    val conversationTitle: String,
    val messageId: String,
    val content: String,
    val role: String,
    val timestamp: Long,
    val highlight: String // Выделенный фрагмент с query
)

/**
 * Ответ поиска
 */
@Serializable
data class SearchResponse(
    val results: List<SearchResult>,
    val totalCount: Int,
    val query: String
)

/**
 * Экспортированное сообщение
 */
@Serializable
data class ExportedMessage(
    val id: String,
    val role: String,
    val content: String?,
    val timestamp: Long,
    val toolCalls: String? = null, // JSON если есть
    val toolCallId: String? = null
)

/**
 * Экспорт диалога
 */
@Serializable
data class ConversationExport(
    val id: String,
    val title: String,
    val messages: List<ExportedMessage>,
    val exportedAt: Long,
    val format: String = "json" // json, markdown
)

/**
 * Запрос на создание диалога
 */
@Serializable
data class CreateConversationRequest(
    val title: String? = null // Если null, будет "Новый чат"
)

/**
 * Запрос на переименование диалога
 */
@Serializable
data class RenameConversationRequest(
    val title: String
)

/**
 * Запрос на импорт диалога
 */
@Serializable
data class ImportConversationRequest(
    val export: ConversationExport
)

/**
 * Ответ с полным диалогом (история сообщений)
 */
@Serializable
data class ConversationDetailResponse(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>,
    val responseFormat: ResponseFormat,
    val createdAt: Long,
    val updatedAt: Long
)
