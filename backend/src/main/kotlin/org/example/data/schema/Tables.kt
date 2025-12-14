package org.example.data.schema

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

/**
 * Таблица conversations - хранит метаданные диалогов
 */
object Conversations : Table("conversations") {
    val id = uuid("id")
    val title = varchar("title", 255).default("Новый чат")
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
    val responseFormat = varchar("response_format", 20).default("PLAIN")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Таблица messages - хранит сообщения диалогов
 */
object Messages : Table("messages") {
    val id = uuid("id")
    val conversationId = uuid("conversation_id").references(
        Conversations.id,
        onDelete = ReferenceOption.CASCADE
    )
    val role = varchar("role", 20) // system, user, assistant, tool
    val content = text("content").nullable()
    val toolCalls = text("tool_calls").nullable() // JSON сериализованные tool_calls
    val toolCallId = varchar("tool_call_id", 255).nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val sequenceNumber = integer("sequence_number")

    override val primaryKey = PrimaryKey(id)
}

/**
 * Таблица conversation_metadata - хранит настройки диалогов
 */
object ConversationMetadata : Table("conversation_metadata") {
    val conversationId = uuid("conversation_id").references(
        Conversations.id,
        onDelete = ReferenceOption.CASCADE
    )
    val collectionSettings = text("collection_settings").nullable() // JSON
    val compressionSettings = text("compression_settings").nullable() // JSON

    override val primaryKey = PrimaryKey(conversationId)
}
