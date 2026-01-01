package org.example.demo

import kotlinx.coroutines.runBlocking
import org.example.rag.RagChatBotRemote

/**
 * Entry point для запуска RAG Chat Demo (удалённый сервер)
 */
fun main(args: Array<String>) = RagChatRemoteDemo.run(args)

/**
 * CLI демо чат-бота с RAG через удалённый сервер.
 * НЕ требует DEEPSEEK_API_KEY — использует ключ на сервере.
 *
 * Запуск:
 * ./gradlew :backend:runRagChatRemote
 *
 * Или:
 * ./scripts/rag_chat_remote.sh
 */
object RagChatRemoteDemo {

    private const val ANSI_RESET = "\u001B[0m"
    private const val ANSI_GREEN = "\u001B[32m"
    private const val ANSI_BLUE = "\u001B[34m"
    private const val ANSI_YELLOW = "\u001B[33m"
    private const val ANSI_CYAN = "\u001B[36m"
    private const val ANSI_GRAY = "\u001B[90m"
    private const val ANSI_BOLD = "\u001B[1m"

    private const val DEFAULT_SERVER = "http://89.169.190.22"

    fun run(args: Array<String>) = runBlocking {
        printBanner()

        val serverUrl = args.firstOrNull() ?: DEFAULT_SERVER
        printInfo("Подключение к серверу: $serverUrl")

        val chatBot = RagChatBotRemote(serverUrl = serverUrl)

        // Проверяем индекс
        printInfo("Проверка индекса...")
        val indexInfo = chatBot.getIndexInfo()
        if (indexInfo.contains("не создан") || indexInfo.contains("Ошибка")) {
            printWarning("Индекс не найден на сервере")
            printInfo("Используйте /index <путь> для индексации документов на сервере")
        } else {
            printSuccess("Индекс доступен")
            println(indexInfo)
        }

        printHelp()
        println()

        while (true) {
            print("${ANSI_GREEN}Вы: ${ANSI_RESET}")
            val input = readLine()?.trim() ?: break

            when {
                input.isBlank() -> continue
                input == "/exit" || input == "/quit" -> {
                    printInfo("До свидания!")
                    break
                }
                input == "/help" -> printHelp()
                input == "/clear" -> {
                    chatBot.clearHistory()
                    printInfo("История диалога очищена")
                }
                input == "/history" -> printHistory(chatBot)
                input == "/status" -> {
                    println("${ANSI_CYAN}Статус:${ANSI_RESET}")
                    println("  🌐 Сервер: $serverUrl")
                    println("  💬 Сообщений в истории: ${chatBot.historySize()}")
                    println()
                    println(chatBot.getIndexInfo())
                }
                input.startsWith("/index ") -> {
                    val path = input.removePrefix("/index ").trim()
                    printInfo("Индексация на сервере: $path")
                    val result = chatBot.indexDocuments(path)
                    println(result)
                }
                else -> {
                    try {
                        val response = chatBot.chat(input)
                        printResponse(response)
                    } catch (e: Exception) {
                        printError("Ошибка: ${e.message}")
                    }
                }
            }
            println()
        }

        chatBot.close()
    }

    private fun printResponse(response: RagChatBotRemote.ChatResponse) {
        println()
        println("${ANSI_BLUE}${ANSI_BOLD}Ассистент:${ANSI_RESET}")
        println(response.answer)

        if (response.sources.isNotEmpty()) {
            println()
            println("${ANSI_CYAN}📚 Источники:${ANSI_RESET}")
            response.sources.forEach { source ->
                val relevanceBar = "█".repeat((source.relevance * 10).toInt().coerceIn(1, 10))
                println("${ANSI_GRAY}  • ${source.file}${ANSI_RESET}")
                println("${ANSI_GRAY}    Релевантность: ${ANSI_YELLOW}$relevanceBar${ANSI_GRAY} ${String.format("%.0f%%", source.relevance * 100)}${ANSI_RESET}")
            }
        } else {
            println()
            println("${ANSI_GRAY}ℹ️  Ответ без использования базы знаний${ANSI_RESET}")
        }

        println()
        println("${ANSI_GRAY}⏱️  ${response.durationMs}ms | 📝 История: ${response.historySize} сообщений${ANSI_RESET}")
    }

    private fun printHistory(chatBot: RagChatBotRemote) {
        val history = chatBot.getHistory()
        if (history.isEmpty()) {
            printInfo("История пуста")
            return
        }

        println("${ANSI_CYAN}История диалога (${history.size} сообщений):${ANSI_RESET}")
        history.takeLast(10).forEach { msg ->
            val roleColor = if (msg.role == "user") ANSI_GREEN else ANSI_BLUE
            val roleLabel = if (msg.role == "user") "Вы" else "AI"
            println("${roleColor}[$roleLabel]${ANSI_RESET} ${msg.content.take(100)}${if (msg.content.length > 100) "..." else ""}")
        }
    }

    private fun printBanner() {
        println()
        println("${ANSI_CYAN}${ANSI_BOLD}╔════════════════════════════════════════════════════════╗${ANSI_RESET}")
        println("${ANSI_CYAN}${ANSI_BOLD}║   🤖 RAG Chat Bot (Remote) - Без локального API ключа   ║${ANSI_RESET}")
        println("${ANSI_CYAN}${ANSI_BOLD}╚════════════════════════════════════════════════════════╝${ANSI_RESET}")
        println()
    }

    private fun printHelp() {
        println("${ANSI_CYAN}Команды:${ANSI_RESET}")
        println("  ${ANSI_YELLOW}/index <путь>${ANSI_RESET}  - Индексировать документы на сервере")
        println("  ${ANSI_YELLOW}/history${ANSI_RESET}       - Показать историю диалога")
        println("  ${ANSI_YELLOW}/clear${ANSI_RESET}         - Очистить историю")
        println("  ${ANSI_YELLOW}/status${ANSI_RESET}        - Показать статус")
        println("  ${ANSI_YELLOW}/help${ANSI_RESET}          - Эта справка")
        println("  ${ANSI_YELLOW}/exit${ANSI_RESET}          - Выход")
    }

    private fun printError(message: String) {
        println("${ANSI_YELLOW}❌ $message${ANSI_RESET}")
    }

    private fun printWarning(message: String) {
        println("${ANSI_YELLOW}⚠️  $message${ANSI_RESET}")
    }

    private fun printInfo(message: String) {
        println("${ANSI_CYAN}ℹ️  $message${ANSI_RESET}")
    }

    private fun printSuccess(message: String) {
        println("${ANSI_GREEN}✅ $message${ANSI_RESET}")
    }
}
