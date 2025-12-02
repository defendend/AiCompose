package org.example.logging

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class LogEntry(
    val timestamp: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
    val level: LogLevel,
    val source: String,
    val message: String
)

@Serializable
enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}
