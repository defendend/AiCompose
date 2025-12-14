package org.example.data

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.model.LLMMessage
import org.example.model.LLMToolCall
import org.example.shared.model.CollectionSettings
import org.example.shared.model.CompressionSettings
import org.example.shared.model.ConversationExport
import org.example.shared.model.ConversationInfo
import org.example.shared.model.ExportedMessage
import org.example.shared.model.ResponseFormat
import org.example.shared.model.SearchResult
import org.slf4j.LoggerFactory

/**
 * Redis реализация репозитория диалогов.
 * Данные сохраняются между перезапусками сервера.
 *
 * Структура ключей:
 * - conv:{id}:messages - JSON массив сообщений диалога
 * - conv:{id}:format - формат ответа
 * - conv:{id}:settings - настройки сбора данных
 *
 * @param redisUrl URL подключения к Redis (redis://localhost:6379)
 * @param ttlSeconds TTL для диалогов в секундах (по умолчанию 24 часа)
 */
class RedisConversationRepository(
    redisUrl: String = "redis://localhost:6379",
    private val ttlSeconds: Long = 86400 // 24 часа
) : ConversationRepository {

    private val logger = LoggerFactory.getLogger(RedisConversationRepository::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client: RedisClient = RedisClient.create(redisUrl)
    private val connection: StatefulRedisConnection<String, String> = client.connect()
    private val commands: RedisAsyncCommands<String, String> = connection.async()

    private fun messagesKey(conversationId: String) = "conv:$conversationId:messages"
    private fun formatKey(conversationId: String) = "conv:$conversationId:format"
    private fun settingsKey(conversationId: String) = "conv:$conversationId:settings"
    private fun compressionKey(conversationId: String) = "conv:$conversationId:compression"
    private fun metadataKey(conversationId: String) = "conv:$conversationId:metadata"
    private val conversationListKey = "conversations:list"

    /**
     * Метаданные диалога для Redis хранения
     */
    @kotlinx.serialization.Serializable
    private data class RedisConversationMetadata(
        val id: String,
        val title: String = "Новый чат",
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
    )

    override fun getHistory(conversationId: String): List<LLMMessage> = runBlocking {
        try {
            val data = commands.get(messagesKey(conversationId)).await()
            if (data != null) {
                json.decodeFromString<List<LLMMessage>>(data)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logger.error("Failed to get history for conversation $conversationId", e)
            emptyList()
        }
    }

    override fun hasConversation(conversationId: String): Boolean = runBlocking {
        try {
            commands.exists(messagesKey(conversationId)).await() > 0
        } catch (e: Exception) {
            logger.error("Failed to check conversation existence $conversationId", e)
            false
        }
    }

    override fun initConversation(conversationId: String, systemMessage: LLMMessage) = runBlocking {
        try {
            val key = messagesKey(conversationId)
            val exists = commands.exists(key).await() > 0
            if (!exists) {
                val data = json.encodeToString(listOf(systemMessage))
                commands.setex(key, ttlSeconds, data).await()
                logger.debug("Initialized conversation $conversationId with system message")
            }
        } catch (e: Exception) {
            logger.error("Failed to init conversation $conversationId", e)
        }
    }

    override fun addMessage(conversationId: String, message: LLMMessage) = runBlocking {
        try {
            val key = messagesKey(conversationId)
            val history = getHistory(conversationId).toMutableList()
            history.add(message)
            val data = json.encodeToString(history)
            commands.setex(key, ttlSeconds, data).await()
            logger.debug("Added message to conversation $conversationId, total: ${history.size}")
        } catch (e: Exception) {
            logger.error("Failed to add message to conversation $conversationId", e)
        }
    }

    override fun addMessages(conversationId: String, messages: List<LLMMessage>) = runBlocking {
        try {
            val key = messagesKey(conversationId)
            val history = getHistory(conversationId).toMutableList()
            history.addAll(messages)
            val data = json.encodeToString(history)
            commands.setex(key, ttlSeconds, data).await()
            logger.debug("Added ${messages.size} messages to conversation $conversationId, total: ${history.size}")
        } catch (e: Exception) {
            logger.error("Failed to add messages to conversation $conversationId", e)
        }
    }

    override fun updateSystemPrompt(conversationId: String, systemPrompt: String) = runBlocking {
        try {
            val key = messagesKey(conversationId)
            val history = getHistory(conversationId).toMutableList()
            if (history.isNotEmpty() && history[0].role == "system") {
                history[0] = LLMMessage(role = "system", content = systemPrompt)
                val data = json.encodeToString(history)
                commands.setex(key, ttlSeconds, data).await()
                logger.debug("Updated system prompt for conversation $conversationId")
            }
        } catch (e: Exception) {
            logger.error("Failed to update system prompt for conversation $conversationId", e)
        }
    }

    override fun getFormat(conversationId: String): ResponseFormat? = runBlocking {
        try {
            val data = commands.get(formatKey(conversationId)).await()
            data?.let { json.decodeFromString<ResponseFormat>(it) }
        } catch (e: Exception) {
            logger.error("Failed to get format for conversation $conversationId", e)
            null
        }
    }

    override fun setFormat(conversationId: String, format: ResponseFormat) {
        runBlocking {
            try {
                val data = json.encodeToString(format)
                commands.setex(formatKey(conversationId), ttlSeconds, data).await()
            } catch (e: Exception) {
                logger.error("Failed to set format for conversation $conversationId", e)
            }
        }
    }

    override fun getCollectionSettings(conversationId: String): CollectionSettings? = runBlocking {
        try {
            val data = commands.get(settingsKey(conversationId)).await()
            data?.let { json.decodeFromString<CollectionSettings>(it) }
        } catch (e: Exception) {
            logger.error("Failed to get collection settings for conversation $conversationId", e)
            null
        }
    }

    override fun setCollectionSettings(conversationId: String, settings: CollectionSettings) {
        runBlocking {
            try {
                val data = json.encodeToString(settings)
                commands.setex(settingsKey(conversationId), ttlSeconds, data).await()
            } catch (e: Exception) {
                logger.error("Failed to set collection settings for conversation $conversationId", e)
            }
        }
    }

    override fun getCompressionSettings(conversationId: String): CompressionSettings? = runBlocking {
        try {
            val data = commands.get(compressionKey(conversationId)).await()
            data?.let { json.decodeFromString<CompressionSettings>(it) }
        } catch (e: Exception) {
            logger.error("Failed to get compression settings for conversation $conversationId", e)
            null
        }
    }

    override fun setCompressionSettings(conversationId: String, settings: CompressionSettings) {
        runBlocking {
            try {
                val data = json.encodeToString(settings)
                commands.setex(compressionKey(conversationId), ttlSeconds, data).await()
            } catch (e: Exception) {
                logger.error("Failed to set compression settings for conversation $conversationId", e)
            }
        }
    }

    override fun replaceHistory(conversationId: String, newHistory: List<LLMMessage>) {
        runBlocking {
            try {
                val key = messagesKey(conversationId)
                val data = json.encodeToString(newHistory)
                commands.setex(key, ttlSeconds, data).await()
                logger.debug("Replaced history for conversation $conversationId, new size: ${newHistory.size}")
            } catch (e: Exception) {
                logger.error("Failed to replace history for conversation $conversationId", e)
            }
        }
    }

    override fun getMessageCount(conversationId: String): Int = runBlocking {
        try {
            getHistory(conversationId).size
        } catch (e: Exception) {
            logger.error("Failed to get message count for conversation $conversationId", e)
            0
        }
    }

    // Методы для управления чатами

    private fun getMetadata(conversationId: String): RedisConversationMetadata? = runBlocking {
        try {
            val data = commands.get(metadataKey(conversationId)).await()
            data?.let { json.decodeFromString<RedisConversationMetadata>(it) }
        } catch (e: Exception) {
            logger.error("Failed to get metadata for conversation $conversationId", e)
            null
        }
    }

    private fun setMetadata(conversationId: String, metadata: RedisConversationMetadata) = runBlocking {
        try {
            val data = json.encodeToString(metadata)
            commands.setex(metadataKey(conversationId), ttlSeconds, data).await()
        } catch (e: Exception) {
            logger.error("Failed to set metadata for conversation $conversationId", e)
        }
    }

    private fun touchMetadata(conversationId: String) = runBlocking {
        val meta = getMetadata(conversationId) ?: return@runBlocking
        setMetadata(conversationId, meta.copy(updatedAt = System.currentTimeMillis()))
    }

    override fun listConversations(): List<ConversationInfo> = runBlocking {
        try {
            val conversationIds = commands.smembers(conversationListKey).await()
            conversationIds.mapNotNull { id ->
                val meta = getMetadata(id) ?: return@mapNotNull null
                val messages = getHistory(id)
                val lastMessage = messages
                    .filter { it.role == "user" || it.role == "assistant" }
                    .lastOrNull()

                ConversationInfo(
                    id = meta.id,
                    title = meta.title,
                    lastMessage = lastMessage?.content?.take(100),
                    messageCount = messages.size,
                    createdAt = meta.createdAt,
                    updatedAt = meta.updatedAt
                )
            }.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            logger.error("Failed to list conversations", e)
            emptyList()
        }
    }

    override fun getConversationInfo(conversationId: String): ConversationInfo? = runBlocking {
        try {
            val meta = getMetadata(conversationId) ?: return@runBlocking null
            val messages = getHistory(conversationId)
            val lastMessage = messages
                .filter { it.role == "user" || it.role == "assistant" }
                .lastOrNull()

            ConversationInfo(
                id = meta.id,
                title = meta.title,
                lastMessage = lastMessage?.content?.take(100),
                messageCount = messages.size,
                createdAt = meta.createdAt,
                updatedAt = meta.updatedAt
            )
        } catch (e: Exception) {
            logger.error("Failed to get conversation info for $conversationId", e)
            null
        }
    }

    override fun createConversation(title: String?): String = runBlocking {
        val id = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        try {
            val metadata = RedisConversationMetadata(
                id = id,
                title = title ?: "Новый чат",
                createdAt = now,
                updatedAt = now
            )
            setMetadata(id, metadata)

            // Добавляем в список диалогов
            commands.sadd(conversationListKey, id).await()

            // Создаём пустой массив сообщений
            val data = json.encodeToString(emptyList<LLMMessage>())
            commands.setex(messagesKey(id), ttlSeconds, data).await()

            logger.debug("Created conversation $id")
        } catch (e: Exception) {
            logger.error("Failed to create conversation", e)
        }

        id
    }

    override fun renameConversation(conversationId: String, newTitle: String) = runBlocking {
        try {
            val meta = getMetadata(conversationId)
            if (meta != null) {
                setMetadata(conversationId, meta.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
                logger.debug("Renamed conversation $conversationId to '$newTitle'")
            }
        } catch (e: Exception) {
            logger.error("Failed to rename conversation $conversationId", e)
        }
    }

    override fun deleteConversation(conversationId: String) = runBlocking {
        try {
            // Удаляем все ключи диалога
            commands.del(
                messagesKey(conversationId),
                metadataKey(conversationId),
                formatKey(conversationId),
                settingsKey(conversationId),
                compressionKey(conversationId)
            ).await()

            // Удаляем из списка диалогов
            commands.srem(conversationListKey, conversationId).await()

            logger.debug("Deleted conversation $conversationId")
        } catch (e: Exception) {
            logger.error("Failed to delete conversation $conversationId", e)
        }
    }

    override fun searchMessages(query: String): List<SearchResult> = runBlocking {
        val results = mutableListOf<SearchResult>()
        val lowerQuery = query.lowercase()

        try {
            val conversationIds = commands.smembers(conversationListKey).await()

            for (convId in conversationIds) {
                val meta = getMetadata(convId) ?: continue
                val messages = getHistory(convId)

                for ((index, message) in messages.withIndex()) {
                    val content = message.content ?: continue
                    if (content.lowercase().contains(lowerQuery)) {
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
        } catch (e: Exception) {
            logger.error("Failed to search messages", e)
        }

        results.sortedByDescending { it.timestamp }
    }

    override fun exportConversation(conversationId: String): ConversationExport? = runBlocking {
        try {
            val meta = getMetadata(conversationId) ?: return@runBlocking null
            val messages = getHistory(conversationId)

            val exportedMessages = messages.mapIndexed { index, msg ->
                ExportedMessage(
                    id = "$conversationId-$index",
                    role = msg.role,
                    content = msg.content,
                    timestamp = meta.updatedAt,
                    toolCalls = msg.tool_calls?.let { json.encodeToString(it) },
                    toolCallId = msg.tool_call_id
                )
            }

            ConversationExport(
                id = conversationId,
                title = meta.title,
                messages = exportedMessages,
                exportedAt = System.currentTimeMillis(),
                format = "json"
            )
        } catch (e: Exception) {
            logger.error("Failed to export conversation $conversationId", e)
            null
        }
    }

    override fun importConversation(export: ConversationExport): String = runBlocking {
        val id = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        try {
            val metadata = RedisConversationMetadata(
                id = id,
                title = export.title,
                createdAt = now,
                updatedAt = now
            )
            setMetadata(id, metadata)

            commands.sadd(conversationListKey, id).await()

            val messages = export.messages.map { msg ->
                LLMMessage(
                    role = msg.role,
                    content = msg.content,
                    tool_calls = msg.toolCalls?.let { json.decodeFromString<List<LLMToolCall>>(it) },
                    tool_call_id = msg.toolCallId
                )
            }

            val data = json.encodeToString(messages)
            commands.setex(messagesKey(id), ttlSeconds, data).await()

            logger.debug("Imported conversation as $id")
        } catch (e: Exception) {
            logger.error("Failed to import conversation", e)
        }

        id
    }

    /**
     * Закрывает соединение с Redis.
     * Вызывать при завершении работы приложения.
     */
    fun close() {
        connection.close()
        client.shutdown()
        logger.info("Redis connection closed")
    }
}
