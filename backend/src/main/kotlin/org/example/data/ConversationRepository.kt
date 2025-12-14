package org.example.data

import org.example.model.LLMMessage
import org.example.shared.model.CollectionSettings
import org.example.shared.model.CompressionSettings
import org.example.shared.model.ConversationExport
import org.example.shared.model.ConversationInfo
import org.example.shared.model.ExportedMessage
import org.example.shared.model.ResponseFormat
import org.example.shared.model.SearchResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Репозиторий для хранения истории диалогов.
 * Интерфейс позволяет легко заменить in-memory реализацию на Redis/PostgreSQL.
 */
interface ConversationRepository {
    fun getHistory(conversationId: String): List<LLMMessage>
    fun initConversation(conversationId: String, systemMessage: LLMMessage)
    fun addMessage(conversationId: String, message: LLMMessage)
    fun addMessages(conversationId: String, messages: List<LLMMessage>)
    fun updateSystemPrompt(conversationId: String, systemPrompt: String)
    fun hasConversation(conversationId: String): Boolean

    fun getFormat(conversationId: String): ResponseFormat?
    fun setFormat(conversationId: String, format: ResponseFormat)

    fun getCollectionSettings(conversationId: String): CollectionSettings?
    fun setCollectionSettings(conversationId: String, settings: CollectionSettings)

    // Методы для сжатия истории
    fun getCompressionSettings(conversationId: String): CompressionSettings?
    fun setCompressionSettings(conversationId: String, settings: CompressionSettings)
    fun replaceHistory(conversationId: String, newHistory: List<LLMMessage>)
    fun getMessageCount(conversationId: String): Int

    // Методы для управления чатами
    fun listConversations(): List<ConversationInfo>
    fun getConversationInfo(conversationId: String): ConversationInfo?
    fun createConversation(title: String? = null): String
    fun renameConversation(conversationId: String, newTitle: String)
    fun deleteConversation(conversationId: String)
    fun searchMessages(query: String): List<SearchResult>
    fun exportConversation(conversationId: String): ConversationExport?
    fun importConversation(export: ConversationExport): String
}

/**
 * Метаданные диалога для in-memory хранения
 */
data class ConversationMetadata(
    val id: String,
    var title: String = "Новый чат",
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

/**
 * In-memory реализация репозитория диалогов с thread-safety.
 * Данные теряются при перезапуске сервера.
 */
class InMemoryConversationRepository : ConversationRepository {
    private val conversations = ConcurrentHashMap<String, MutableList<LLMMessage>>()
    private val metadata = ConcurrentHashMap<String, ConversationMetadata>()
    private val formats = ConcurrentHashMap<String, ResponseFormat>()
    private val collectionSettings = ConcurrentHashMap<String, CollectionSettings>()
    private val compressionSettings = ConcurrentHashMap<String, CompressionSettings>()
    private val locks = ConcurrentHashMap<String, Any>()

    private fun getLock(conversationId: String): Any = locks.getOrPut(conversationId) { Any() }

    override fun getHistory(conversationId: String): List<LLMMessage> {
        val lock = getLock(conversationId)
        synchronized(lock) {
            return conversations[conversationId]?.toList() ?: emptyList()
        }
    }

    override fun hasConversation(conversationId: String): Boolean {
        return conversations.containsKey(conversationId)
    }

    override fun initConversation(conversationId: String, systemMessage: LLMMessage) {
        val lock = getLock(conversationId)
        synchronized(lock) {
            if (!conversations.containsKey(conversationId)) {
                conversations[conversationId] = mutableListOf(systemMessage)
                // Создаём metadata если нет
                if (!metadata.containsKey(conversationId)) {
                    val now = System.currentTimeMillis()
                    metadata[conversationId] = ConversationMetadata(
                        id = conversationId,
                        title = "Новый чат",
                        createdAt = now,
                        updatedAt = now
                    )
                }
            }
        }
    }

    override fun addMessage(conversationId: String, message: LLMMessage) {
        val lock = getLock(conversationId)
        synchronized(lock) {
            val history = conversations.getOrPut(conversationId) { mutableListOf() }
            history.add(message)
            touchConversation(conversationId)
        }
    }

    override fun addMessages(conversationId: String, messages: List<LLMMessage>) {
        val lock = getLock(conversationId)
        synchronized(lock) {
            val history = conversations.getOrPut(conversationId) { mutableListOf() }
            history.addAll(messages)
            touchConversation(conversationId)
        }
    }

    override fun updateSystemPrompt(conversationId: String, systemPrompt: String) {
        val lock = getLock(conversationId)
        synchronized(lock) {
            val history = conversations[conversationId] ?: return
            if (history.isNotEmpty() && history[0].role == "system") {
                history[0] = LLMMessage(role = "system", content = systemPrompt)
            }
        }
    }

    override fun getFormat(conversationId: String): ResponseFormat? {
        return formats[conversationId]
    }

    override fun setFormat(conversationId: String, format: ResponseFormat) {
        formats[conversationId] = format
    }

    override fun getCollectionSettings(conversationId: String): CollectionSettings? {
        return collectionSettings[conversationId]
    }

    override fun setCollectionSettings(conversationId: String, settings: CollectionSettings) {
        collectionSettings[conversationId] = settings
    }

    override fun getCompressionSettings(conversationId: String): CompressionSettings? {
        return compressionSettings[conversationId]
    }

    override fun setCompressionSettings(conversationId: String, settings: CompressionSettings) {
        compressionSettings[conversationId] = settings
    }

    override fun replaceHistory(conversationId: String, newHistory: List<LLMMessage>) {
        val lock = getLock(conversationId)
        synchronized(lock) {
            conversations[conversationId] = newHistory.toMutableList()
        }
    }

    override fun getMessageCount(conversationId: String): Int {
        val lock = getLock(conversationId)
        synchronized(lock) {
            return conversations[conversationId]?.size ?: 0
        }
    }

    // Новые методы для управления чатами

    override fun listConversations(): List<ConversationInfo> {
        return metadata.values
            .sortedByDescending { it.updatedAt }
            .map { meta ->
                val messages = conversations[meta.id] ?: emptyList()
                val lastUserMessage = messages
                    .filter { it.role == "user" || it.role == "assistant" }
                    .lastOrNull()
                ConversationInfo(
                    id = meta.id,
                    title = meta.title,
                    lastMessage = lastUserMessage?.content?.take(100),
                    messageCount = messages.size,
                    createdAt = meta.createdAt,
                    updatedAt = meta.updatedAt
                )
            }
    }

    override fun getConversationInfo(conversationId: String): ConversationInfo? {
        val meta = metadata[conversationId] ?: return null
        val messages = conversations[conversationId] ?: emptyList()
        val lastUserMessage = messages
            .filter { it.role == "user" || it.role == "assistant" }
            .lastOrNull()
        return ConversationInfo(
            id = meta.id,
            title = meta.title,
            lastMessage = lastUserMessage?.content?.take(100),
            messageCount = messages.size,
            createdAt = meta.createdAt,
            updatedAt = meta.updatedAt
        )
    }

    override fun createConversation(title: String?): String {
        val id = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        metadata[id] = ConversationMetadata(
            id = id,
            title = title ?: "Новый чат",
            createdAt = now,
            updatedAt = now
        )
        conversations[id] = mutableListOf()
        return id
    }

    override fun renameConversation(conversationId: String, newTitle: String) {
        metadata[conversationId]?.let { meta ->
            meta.title = newTitle
            meta.updatedAt = System.currentTimeMillis()
        }
    }

    override fun deleteConversation(conversationId: String) {
        val lock = getLock(conversationId)
        synchronized(lock) {
            conversations.remove(conversationId)
            metadata.remove(conversationId)
            formats.remove(conversationId)
            collectionSettings.remove(conversationId)
            compressionSettings.remove(conversationId)
            locks.remove(conversationId)
        }
    }

    override fun searchMessages(query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val lowerQuery = query.lowercase()

        for ((convId, messages) in conversations) {
            val meta = metadata[convId] ?: continue
            for ((index, message) in messages.withIndex()) {
                val content = message.content ?: continue
                if (content.lowercase().contains(lowerQuery)) {
                    // Создаём highlight с выделением найденного фрагмента
                    val startIndex = content.lowercase().indexOf(lowerQuery)
                    val contextStart = maxOf(0, startIndex - 30)
                    val contextEnd = minOf(content.length, startIndex + query.length + 30)
                    val highlight = buildString {
                        if (contextStart > 0) append("...")
                        append(content.substring(contextStart, contextEnd))
                        if (contextEnd < content.length) append("...")
                    }

                    results.add(
                        SearchResult(
                            conversationId = convId,
                            conversationTitle = meta.title,
                            messageId = "$convId-$index",
                            content = content.take(200),
                            role = message.role,
                            timestamp = meta.updatedAt,
                            highlight = highlight
                        )
                    )
                }
            }
        }

        return results.sortedByDescending { it.timestamp }
    }

    override fun exportConversation(conversationId: String): ConversationExport? {
        val meta = metadata[conversationId] ?: return null
        val messages = conversations[conversationId] ?: return null

        val exportedMessages = messages.mapIndexed { index, msg ->
            ExportedMessage(
                id = "$conversationId-$index",
                role = msg.role,
                content = msg.content,
                timestamp = meta.updatedAt,
                toolCalls = msg.tool_calls?.let {
                    kotlinx.serialization.json.Json.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(org.example.model.LLMToolCall.serializer()),
                        it
                    )
                },
                toolCallId = msg.tool_call_id
            )
        }

        return ConversationExport(
            id = conversationId,
            title = meta.title,
            messages = exportedMessages,
            exportedAt = System.currentTimeMillis(),
            format = "json"
        )
    }

    override fun importConversation(export: ConversationExport): String {
        val id = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        metadata[id] = ConversationMetadata(
            id = id,
            title = export.title,
            createdAt = now,
            updatedAt = now
        )

        val messages = export.messages.map { msg ->
            LLMMessage(
                role = msg.role,
                content = msg.content,
                tool_calls = msg.toolCalls?.let {
                    kotlinx.serialization.json.Json.decodeFromString<List<org.example.model.LLMToolCall>>(it)
                },
                tool_call_id = msg.toolCallId
            )
        }

        conversations[id] = messages.toMutableList()
        return id
    }

    // Обновление updatedAt при добавлении сообщений
    private fun touchConversation(conversationId: String) {
        metadata[conversationId]?.updatedAt = System.currentTimeMillis()
    }
}
