package org.example.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.data.schema.ConversationMetadata as DbMetadata
import org.example.data.schema.Conversations
import org.example.data.schema.Messages
import org.example.model.LLMMessage
import org.example.model.LLMToolCall
import org.example.shared.model.CollectionSettings
import org.example.shared.model.CompressionSettings
import org.example.shared.model.ConversationExport
import org.example.shared.model.ConversationInfo
import org.example.shared.model.ExportedMessage
import org.example.shared.model.ResponseFormat
import org.example.shared.model.SearchResult
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * PostgreSQL реализация репозитория диалогов с использованием Exposed ORM.
 */
class PostgresConversationRepository(
    private val database: Database
) : ConversationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    init {
        // Создаём таблицы при инициализации
        transaction(database) {
            SchemaUtils.create(Conversations, Messages, DbMetadata)
        }
    }

    override fun getHistory(conversationId: String): List<LLMMessage> = transaction(database) {
        val uuid = UUID.fromString(conversationId)
        Messages.select { Messages.conversationId eq uuid }
            .orderBy(Messages.sequenceNumber)
            .map { row ->
                LLMMessage(
                    role = row[Messages.role],
                    content = row[Messages.content],
                    tool_calls = row[Messages.toolCalls]?.let {
                        json.decodeFromString<List<LLMToolCall>>(it)
                    },
                    tool_call_id = row[Messages.toolCallId]
                )
            }
    }

    override fun initConversation(conversationId: String, systemMessage: LLMMessage) {
        transaction(database) {
            val uuid = UUID.fromString(conversationId)

            // Создаём диалог если не существует
            if (Conversations.select { Conversations.id eq uuid }.empty()) {
                val now = LocalDateTime.now()
                Conversations.insert {
                    it[id] = uuid
                    it[title] = "Новый чат"
                    it[createdAt] = now
                    it[updatedAt] = now
                    it[responseFormat] = "PLAIN"
                }

                // Добавляем системное сообщение
                Messages.insert {
                    it[id] = UUID.randomUUID()
                    it[this.conversationId] = uuid
                    it[role] = systemMessage.role
                    it[content] = systemMessage.content
                    it[toolCalls] = systemMessage.tool_calls?.let { calls ->
                        json.encodeToString(calls)
                    }
                    it[toolCallId] = systemMessage.tool_call_id
                    it[createdAt] = now
                    it[sequenceNumber] = 0
                }
            }
        }
    }

    override fun addMessage(conversationId: String, message: LLMMessage) {
        transaction(database) {
            val uuid = UUID.fromString(conversationId)
            val now = LocalDateTime.now()

            // Получаем следующий sequence number
            val nextSeq = Messages
                .slice(Messages.sequenceNumber.max())
                .select { Messages.conversationId eq uuid }
                .firstOrNull()
                ?.get(Messages.sequenceNumber.max())
                ?.plus(1) ?: 0

            Messages.insert {
                it[id] = UUID.randomUUID()
                it[this.conversationId] = uuid
                it[role] = message.role
                it[content] = message.content
                it[toolCalls] = message.tool_calls?.let { calls ->
                    json.encodeToString(calls)
                }
                it[toolCallId] = message.tool_call_id
                it[createdAt] = now
                it[sequenceNumber] = nextSeq
            }

            // Обновляем updatedAt диалога
            Conversations.update({ Conversations.id eq uuid }) {
                it[updatedAt] = now
            }
        }
    }

    override fun addMessages(conversationId: String, messages: List<LLMMessage>) {
        transaction(database) {
            val uuid = UUID.fromString(conversationId)
            val now = LocalDateTime.now()

            var nextSeq = Messages
                .slice(Messages.sequenceNumber.max())
                .select { Messages.conversationId eq uuid }
                .firstOrNull()
                ?.get(Messages.sequenceNumber.max())
                ?.plus(1) ?: 0

            for (message in messages) {
                Messages.insert {
                    it[id] = UUID.randomUUID()
                    it[this.conversationId] = uuid
                    it[role] = message.role
                    it[content] = message.content
                    it[toolCalls] = message.tool_calls?.let { calls ->
                        json.encodeToString(calls)
                    }
                    it[toolCallId] = message.tool_call_id
                    it[createdAt] = now
                    it[sequenceNumber] = nextSeq++
                }
            }

            Conversations.update({ Conversations.id eq uuid }) {
                it[updatedAt] = now
            }
        }
    }

    override fun updateSystemPrompt(conversationId: String, systemPrompt: String) {
        transaction(database) {
            val uuid = UUID.fromString(conversationId)

            Messages.update({ (Messages.conversationId eq uuid) and (Messages.role eq "system") }) {
                it[content] = systemPrompt
            }

            Conversations.update({ Conversations.id eq uuid }) {
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }

    override fun hasConversation(conversationId: String): Boolean = transaction(database) {
        val uuid = UUID.fromString(conversationId)
        !Conversations.select { Conversations.id eq uuid }.empty()
    }

    override fun getFormat(conversationId: String): ResponseFormat? = transaction(database) {
        val uuid = UUID.fromString(conversationId)
        Conversations.select { Conversations.id eq uuid }
            .firstOrNull()
            ?.get(Conversations.responseFormat)
            ?.let { ResponseFormat.valueOf(it) }
    }

    override fun setFormat(conversationId: String, format: ResponseFormat) {
        transaction(database) {
            val uuid = UUID.fromString(conversationId)
            Conversations.update({ Conversations.id eq uuid }) {
                it[responseFormat] = format.name
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }

    override fun getCollectionSettings(conversationId: String): CollectionSettings? = transaction(database) {
        val uuid = UUID.fromString(conversationId)
        DbMetadata.select { DbMetadata.conversationId eq uuid }
            .firstOrNull()
            ?.get(DbMetadata.collectionSettings)
            ?.let { json.decodeFromString<CollectionSettings>(it) }
    }

    override fun setCollectionSettings(conversationId: String, settings: CollectionSettings) {
        transaction(database) {
            val uuid = UUID.fromString(conversationId)
            val settingsJson = json.encodeToString(settings)

            // Upsert
            val exists = !DbMetadata.select { DbMetadata.conversationId eq uuid }.empty()
            if (exists) {
                DbMetadata.update({ DbMetadata.conversationId eq uuid }) {
                    it[collectionSettings] = settingsJson
                }
            } else {
                DbMetadata.insert {
                    it[this.conversationId] = uuid
                    it[collectionSettings] = settingsJson
                }
            }

            Conversations.update({ Conversations.id eq uuid }) {
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }

    override fun getCompressionSettings(conversationId: String): CompressionSettings? = transaction(database) {
        val uuid = UUID.fromString(conversationId)
        DbMetadata.select { DbMetadata.conversationId eq uuid }
            .firstOrNull()
            ?.get(DbMetadata.compressionSettings)
            ?.let { json.decodeFromString<CompressionSettings>(it) }
    }

    override fun setCompressionSettings(conversationId: String, settings: CompressionSettings) {
        transaction(database) {
            val uuid = UUID.fromString(conversationId)
            val settingsJson = json.encodeToString(settings)

            val exists = !DbMetadata.select { DbMetadata.conversationId eq uuid }.empty()
            if (exists) {
                DbMetadata.update({ DbMetadata.conversationId eq uuid }) {
                    it[compressionSettings] = settingsJson
                }
            } else {
                DbMetadata.insert {
                    it[this.conversationId] = uuid
                    it[compressionSettings] = settingsJson
                }
            }
        }
    }

    override fun replaceHistory(conversationId: String, newHistory: List<LLMMessage>) {
        transaction(database) {
            val uuid = UUID.fromString(conversationId)
            val now = LocalDateTime.now()

            // Удаляем все сообщения
            Messages.deleteWhere { Messages.conversationId eq uuid }

            // Вставляем новые
            newHistory.forEachIndexed { index, message ->
                Messages.insert {
                    it[id] = UUID.randomUUID()
                    it[this.conversationId] = uuid
                    it[role] = message.role
                    it[content] = message.content
                    it[toolCalls] = message.tool_calls?.let { calls ->
                        json.encodeToString(calls)
                    }
                    it[toolCallId] = message.tool_call_id
                    it[createdAt] = now
                    it[sequenceNumber] = index
                }
            }

            Conversations.update({ Conversations.id eq uuid }) {
                it[updatedAt] = now
            }
        }
    }

    override fun getMessageCount(conversationId: String): Int = transaction(database) {
        val uuid = UUID.fromString(conversationId)
        Messages.select { Messages.conversationId eq uuid }.count().toInt()
    }

    // Методы для управления чатами

    override fun listConversations(): List<ConversationInfo> = transaction(database) {
        Conversations.selectAll()
            .orderBy(Conversations.updatedAt, SortOrder.DESC)
            .map { row ->
                val convId = row[Conversations.id]

                // Получаем последнее сообщение
                val lastMessage = Messages
                    .select { (Messages.conversationId eq convId) and (Messages.role inList listOf("user", "assistant")) }
                    .orderBy(Messages.sequenceNumber, SortOrder.DESC)
                    .firstOrNull()
                    ?.get(Messages.content)
                    ?.take(100)

                val messageCount = Messages
                    .select { Messages.conversationId eq convId }
                    .count().toInt()

                ConversationInfo(
                    id = convId.toString(),
                    title = row[Conversations.title],
                    lastMessage = lastMessage,
                    messageCount = messageCount,
                    createdAt = row[Conversations.createdAt].toInstant(ZoneOffset.UTC).toEpochMilli(),
                    updatedAt = row[Conversations.updatedAt].toInstant(ZoneOffset.UTC).toEpochMilli()
                )
            }
    }

    override fun getConversationInfo(conversationId: String): ConversationInfo? = transaction(database) {
        val uuid = UUID.fromString(conversationId)

        Conversations.select { Conversations.id eq uuid }
            .firstOrNull()
            ?.let { row ->
                val lastMessage = Messages
                    .select { (Messages.conversationId eq uuid) and (Messages.role inList listOf("user", "assistant")) }
                    .orderBy(Messages.sequenceNumber, SortOrder.DESC)
                    .firstOrNull()
                    ?.get(Messages.content)
                    ?.take(100)

                val messageCount = Messages
                    .select { Messages.conversationId eq uuid }
                    .count().toInt()

                ConversationInfo(
                    id = uuid.toString(),
                    title = row[Conversations.title],
                    lastMessage = lastMessage,
                    messageCount = messageCount,
                    createdAt = row[Conversations.createdAt].toInstant(ZoneOffset.UTC).toEpochMilli(),
                    updatedAt = row[Conversations.updatedAt].toInstant(ZoneOffset.UTC).toEpochMilli()
                )
            }
    }

    override fun createConversation(title: String?): String = transaction(database) {
        val uuid = UUID.randomUUID()
        val now = LocalDateTime.now()

        Conversations.insert {
            it[id] = uuid
            it[this.title] = title ?: "Новый чат"
            it[createdAt] = now
            it[updatedAt] = now
            it[responseFormat] = "PLAIN"
        }

        uuid.toString()
    }

    override fun renameConversation(conversationId: String, newTitle: String) {
        transaction(database) {
            val uuid = UUID.fromString(conversationId)
            Conversations.update({ Conversations.id eq uuid }) {
                it[title] = newTitle
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }

    override fun deleteConversation(conversationId: String) {
        transaction(database) {
            val uuid = UUID.fromString(conversationId)
            // Messages удалятся автоматически благодаря CASCADE
            // DbMetadata тоже
            Conversations.deleteWhere { id eq uuid }
        }
    }

    override fun searchMessages(query: String): List<SearchResult> = transaction(database) {
        val lowerQuery = query.lowercase()
        val results = mutableListOf<SearchResult>()

        // Поиск по всем сообщениям
        (Messages innerJoin Conversations)
            .select { Messages.content.isNotNull() }
            .forEach { row ->
                val content = row[Messages.content] ?: return@forEach
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
                            conversationId = row[Messages.conversationId].toString(),
                            conversationTitle = row[Conversations.title],
                            messageId = row[Messages.id].toString(),
                            content = content.take(200),
                            role = row[Messages.role],
                            timestamp = row[Messages.createdAt].toInstant(ZoneOffset.UTC).toEpochMilli(),
                            highlight = highlight
                        )
                    )
                }
            }

        results.sortedByDescending { it.timestamp }
    }

    override fun exportConversation(conversationId: String): ConversationExport? = transaction(database) {
        val uuid = UUID.fromString(conversationId)

        val conversation = Conversations.select { Conversations.id eq uuid }.firstOrNull()
            ?: return@transaction null

        val messages = Messages
            .select { Messages.conversationId eq uuid }
            .orderBy(Messages.sequenceNumber)
            .map { row ->
                ExportedMessage(
                    id = row[Messages.id].toString(),
                    role = row[Messages.role],
                    content = row[Messages.content],
                    timestamp = row[Messages.createdAt].toInstant(ZoneOffset.UTC).toEpochMilli(),
                    toolCalls = row[Messages.toolCalls],
                    toolCallId = row[Messages.toolCallId]
                )
            }

        ConversationExport(
            id = conversationId,
            title = conversation[Conversations.title],
            messages = messages,
            exportedAt = System.currentTimeMillis(),
            format = "json"
        )
    }

    override fun importConversation(export: ConversationExport): String = transaction(database) {
        val uuid = UUID.randomUUID()
        val now = LocalDateTime.now()

        Conversations.insert {
            it[id] = uuid
            it[title] = export.title
            it[createdAt] = now
            it[updatedAt] = now
            it[responseFormat] = "PLAIN"
        }

        export.messages.forEachIndexed { index, msg ->
            Messages.insert {
                it[id] = UUID.randomUUID()
                it[conversationId] = uuid
                it[role] = msg.role
                it[content] = msg.content
                it[toolCalls] = msg.toolCalls
                it[toolCallId] = msg.toolCallId
                it[createdAt] = now
                it[sequenceNumber] = index
            }
        }

        uuid.toString()
    }
}
