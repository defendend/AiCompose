package org.example.data

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.model.LLMMessage
import org.example.shared.model.CollectionSettings
import org.example.shared.model.ResponseFormat
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
