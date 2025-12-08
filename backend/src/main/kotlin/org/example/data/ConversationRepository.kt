package org.example.data

import org.example.model.LLMMessage
import org.example.shared.model.CollectionSettings
import org.example.shared.model.ResponseFormat
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
}

/**
 * In-memory реализация репозитория диалогов с thread-safety.
 * Данные теряются при перезапуске сервера.
 */
class InMemoryConversationRepository : ConversationRepository {
    private val conversations = ConcurrentHashMap<String, MutableList<LLMMessage>>()
    private val formats = ConcurrentHashMap<String, ResponseFormat>()
    private val collectionSettings = ConcurrentHashMap<String, CollectionSettings>()
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
            }
        }
    }

    override fun addMessage(conversationId: String, message: LLMMessage) {
        val lock = getLock(conversationId)
        synchronized(lock) {
            val history = conversations.getOrPut(conversationId) { mutableListOf() }
            history.add(message)
        }
    }

    override fun addMessages(conversationId: String, messages: List<LLMMessage>) {
        val lock = getLock(conversationId)
        synchronized(lock) {
            val history = conversations.getOrPut(conversationId) { mutableListOf() }
            history.addAll(messages)
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
}
