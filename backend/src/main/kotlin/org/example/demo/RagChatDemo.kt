package org.example.demo

import kotlinx.coroutines.runBlocking
import org.example.data.DeepSeekClient
import org.example.rag.DocumentChunker
import org.example.rag.DocumentIndex
import org.example.rag.RagChatBot
import java.io.File

/**
 * Entry point для запуска RAG Chat Demo
 */
fun main(args: Array<String>) = RagChatDemo.run(args)

/**
 * CLI демо чат-бота с RAG-памятью.
 *
 * Запуск:
 * ./gradlew :backend:runRagChat --args="[путь_к_документам]"
 *
 * Или через скрипт:
 * DEEPSEEK_API_KEY=xxx ./scripts/rag_chat_demo.sh [путь_к_документам]
 */
object RagChatDemo {

    private const val ANSI_RESET = "\u001B[0m"
    private const val ANSI_GREEN = "\u001B[32m"
    private const val ANSI_BLUE = "\u001B[34m"
    private const val ANSI_YELLOW = "\u001B[33m"
    private const val ANSI_CYAN = "\u001B[36m"
    private const val ANSI_GRAY = "\u001B[90m"
    private const val ANSI_BOLD = "\u001B[1m"

    fun run(args: Array<String>) = runBlocking {
        printBanner()

        // Проверяем API ключ
        val apiKey = System.getenv("DEEPSEEK_API_KEY")
        if (apiKey.isNullOrBlank()) {
            printError("DEEPSEEK_API_KEY не установлен!")
            printInfo("Запустите: DEEPSEEK_API_KEY=xxx ./scripts/rag_chat_demo.sh")
            return@runBlocking
        }

        // Создаем LLM клиент
        val llmClient = DeepSeekClient(apiKey)

        // Создаем индекс и чат-бот
        val index = DocumentIndex()
        val chatBot = RagChatBot(
            llmClient = llmClient,
            documentIndex = index,
            topK = 3,
            minRelevance = 0.1f
        )

        // Индексируем документы если указан путь
        val docsPath = args.firstOrNull()
        if (!docsPath.isNullOrBlank()) {
            indexDocuments(docsPath, index)
        } else {
            printWarning("Путь к документам не указан. RAG будет работать без базы знаний.")
            printInfo("Для индексации документов укажите путь: ./scripts/rag_chat_demo.sh /path/to/docs")
        }

        // Основной цикл чата
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
                input == "/status" -> printStatus(chatBot, index)
                input.startsWith("/index ") -> {
                    val path = input.removePrefix("/index ").trim()
                    indexDocuments(path, index)
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

        llmClient.close()
    }

    private fun indexDocuments(path: String, index: DocumentIndex) {
        val file = File(path)
        if (!file.exists()) {
            printError("Путь не существует: $path")
            return
        }

        printInfo("Индексация документов из: $path")

        val chunks = if (file.isDirectory) {
            DocumentChunker.chunkDirectory(file, setOf("md", "txt", "kt", "java"))
        } else {
            DocumentChunker.chunkFile(file)
        }

        if (chunks.isEmpty()) {
            printWarning("Не найдено документов для индексации")
            return
        }

        index.clear()
        index.indexChunks(chunks)

        printSuccess("Проиндексировано ${chunks.size} чанков из ${chunks.map { it.source }.distinct().size} файлов")
    }

    private fun printResponse(response: RagChatBot.ChatResponse) {
        println()
        println("${ANSI_BLUE}${ANSI_BOLD}Ассистент:${ANSI_RESET}")
        println(response.answer)

        // Выводим источники
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

        // Статистика
        println()
        println("${ANSI_GRAY}⏱️  ${response.durationMs}ms | 📝 История: ${response.historySize} сообщений${ANSI_RESET}")
    }

    private fun printHistory(chatBot: RagChatBot) {
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

    private fun printStatus(chatBot: RagChatBot, index: DocumentIndex) {
        println("${ANSI_CYAN}Статус:${ANSI_RESET}")
        println("  📊 Документов в индексе: ${index.size()}")
        println("  💬 Сообщений в истории: ${chatBot.historySize()}")
    }

    private fun printBanner() {
        println()
        println("${ANSI_CYAN}${ANSI_BOLD}╔════════════════════════════════════════════════════════╗${ANSI_RESET}")
        println("${ANSI_CYAN}${ANSI_BOLD}║       🤖 RAG Chat Bot - Чат с памятью и поиском         ║${ANSI_RESET}")
        println("${ANSI_CYAN}${ANSI_BOLD}╚════════════════════════════════════════════════════════╝${ANSI_RESET}")
        println()
    }

    private fun printHelp() {
        println("${ANSI_CYAN}Команды:${ANSI_RESET}")
        println("  ${ANSI_YELLOW}/index <путь>${ANSI_RESET}  - Индексировать документы")
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
