package org.example.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object AppLogger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private const val MAX_LOGS = 1000

    fun debug(source: String, message: String) {
        addLog(LogLevel.DEBUG, source, message)
    }

    fun info(source: String, message: String) {
        addLog(LogLevel.INFO, source, message)
    }

    fun warning(source: String, message: String) {
        addLog(LogLevel.WARNING, source, message)
    }

    fun error(source: String, message: String) {
        addLog(LogLevel.ERROR, source, message)
    }

    private fun addLog(level: LogLevel, source: String, message: String) {
        val entry = LogEntry(level = level, source = source, message = message)
        _logs.update { currentLogs ->
            val newLogs = currentLogs + entry
            if (newLogs.size > MAX_LOGS) {
                newLogs.drop(newLogs.size - MAX_LOGS)
            } else {
                newLogs
            }
        }
        // Также выводим в консоль для отладки
        println("[${entry.timestamp}] [${entry.level}] [$source] $message")
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
