package org.example.tools.devassistant.ide

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool
import org.example.tools.core.AnnotatedAgentTool
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Глобальное хранилище IDE контекста.
 * Хранит информацию о текущем файле, открытых файлах, выделении и т.д.
 */
object IdeContext {
    /** Текущий (активный) файл */
    var currentFile: String? = null

    /** Список открытых файлов */
    val openFiles: MutableList<String> = mutableListOf()

    /** Выделенный текст (если есть) */
    var selectedText: String? = null

    /** Позиция курсора: line, column */
    var cursorPosition: Pair<Int, Int>? = null

    /** Путь к проекту */
    var projectPath: String? = null

    /** Дополнительные метаданные */
    val metadata: MutableMap<String, String> = ConcurrentHashMap()

    fun clear() {
        currentFile = null
        openFiles.clear()
        selectedText = null
        cursorPosition = null
        metadata.clear()
    }

    fun toSummary(): String {
        val sb = StringBuilder()
        sb.appendLine("# 🖥️ IDE Context")
        sb.appendLine()

        projectPath?.let { sb.appendLine("📁 Проект: $it") }
        currentFile?.let { sb.appendLine("📄 Текущий файл: $it") }
        cursorPosition?.let { (line, col) -> sb.appendLine("📍 Курсор: строка $line, колонка $col") }

        if (openFiles.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("📂 Открытые файлы (${openFiles.size}):")
            openFiles.forEach { sb.appendLine("  - $it") }
        }

        selectedText?.let {
            sb.appendLine()
            sb.appendLine("✏️ Выделенный текст:")
            sb.appendLine("```")
            sb.appendLine(it.take(500))
            if (it.length > 500) sb.appendLine("... (${it.length} символов)")
            sb.appendLine("```")
        }

        if (metadata.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("🏷️ Метаданные:")
            metadata.forEach { (k, v) -> sb.appendLine("  - $k: $v") }
        }

        return sb.toString()
    }
}

/**
 * Инструмент для установки контекста IDE.
 * Позволяет сообщить агенту о текущем файле, открытых файлах и т.д.
 */
@Tool(
    name = "ide_set_context",
    description = "Установить контекст IDE: текущий файл, открытые файлы, выделенный текст, позицию курсора"
)
@Param(name = "current_file", description = "Путь к текущему (активному) файлу", type = "string", required = false)
@Param(name = "open_files", description = "Список путей к открытым файлам (JSON array)", type = "array", required = false)
@Param(name = "selected_text", description = "Выделенный текст в редакторе", type = "string", required = false)
@Param(name = "cursor_line", description = "Номер строки курсора (1-based)", type = "integer", required = false)
@Param(name = "cursor_column", description = "Номер колонки курсора (1-based)", type = "integer", required = false)
@Param(name = "project_path", description = "Путь к корню проекта", type = "string", required = false)
object IdeSetContextTool : AnnotatedAgentTool() {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(arguments: String): String {
        if (arguments.isBlank()) {
            return "❌ Ошибка: укажите хотя бы один параметр контекста"
        }

        val args = try {
            json.parseToJsonElement(arguments).jsonObject
        } catch (e: Exception) {
            return "❌ Ошибка парсинга JSON: ${e.message}"
        }

        var updated = 0

        // Текущий файл
        args["current_file"]?.jsonPrimitive?.content?.let {
            IdeContext.currentFile = it
            updated++
        }

        // Открытые файлы
        args["open_files"]?.jsonArray?.let { array ->
            IdeContext.openFiles.clear()
            array.forEach { elem ->
                IdeContext.openFiles.add(elem.jsonPrimitive.content)
            }
            updated++
        }

        // Выделенный текст
        args["selected_text"]?.jsonPrimitive?.content?.let {
            IdeContext.selectedText = it
            updated++
        }

        // Позиция курсора
        val line = args["cursor_line"]?.jsonPrimitive?.content?.toIntOrNull()
        val col = args["cursor_column"]?.jsonPrimitive?.content?.toIntOrNull()
        if (line != null) {
            IdeContext.cursorPosition = Pair(line, col ?: 1)
            updated++
        }

        // Путь к проекту
        args["project_path"]?.jsonPrimitive?.content?.let {
            IdeContext.projectPath = it
            updated++
        }

        return if (updated > 0) {
            """
            |✅ Контекст IDE обновлён ($updated параметров)
            |
            |${IdeContext.toSummary()}
            """.trimMargin()
        } else {
            "⚠️ Ничего не было обновлено"
        }
    }
}

/**
 * Инструмент для получения текущего контекста IDE.
 */
@Tool(
    name = "ide_get_context",
    description = "Получить текущий контекст IDE: активный файл, открытые файлы, выделение, позицию курсора"
)
object IdeGetContextTool : AnnotatedAgentTool() {

    override suspend fun execute(arguments: String): String {
        if (IdeContext.currentFile == null && IdeContext.openFiles.isEmpty()) {
            return """
                |⚠️ Контекст IDE не установлен.
                |
                |Используйте `ide_set_context` чтобы указать текущий файл или открытые файлы.
                |
                |Пример:
                |```json
                |{
                |  "current_file": "src/main/kotlin/App.kt",
                |  "cursor_line": 42
                |}
                |```
            """.trimMargin()
        }

        return IdeContext.toSummary()
    }
}

/**
 * Инструмент для чтения текущего файла из контекста IDE.
 * Удобная обёртка над file_read с автоматическим использованием текущего файла.
 */
@Tool(
    name = "ide_read_current",
    description = "Прочитать содержимое текущего файла из контекста IDE. Если указан selected_text, возвращает его."
)
@Param(name = "around_cursor", description = "Если true, показать только ±N строк вокруг курсора", type = "boolean", required = false)
@Param(name = "context_lines", description = "Количество строк вокруг курсора (по умолчанию 10)", type = "integer", required = false)
object IdeReadCurrentTool : AnnotatedAgentTool() {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(arguments: String): String {
        val currentFile = IdeContext.currentFile
            ?: return "❌ Текущий файл не установлен. Используйте `ide_set_context` сначала."

        val args = if (arguments.isBlank()) {
            emptyMap()
        } else {
            try {
                json.parseToJsonElement(arguments).jsonObject.mapValues {
                    it.value.jsonPrimitive.content
                }
            } catch (e: Exception) {
                emptyMap()
            }
        }

        val aroundCursor = args["around_cursor"]?.toBoolean() ?: false
        val contextLines = args["context_lines"]?.toIntOrNull() ?: 10

        // Если есть выделенный текст, возвращаем его
        IdeContext.selectedText?.let { selected ->
            return """
                |📄 Выделенный текст из $currentFile:
                |
                |```${getFileExtension(currentFile)}
                |$selected
                |```
            """.trimMargin()
        }

        // Определяем базовый путь
        val basePath = IdeContext.projectPath ?: System.getenv("PROJECT_PATH") ?: "."
        val file = File(basePath, currentFile).takeIf { it.exists() }
            ?: File(currentFile).takeIf { it.exists() }
            ?: return "❌ Файл не найден: $currentFile"

        val lines = file.readLines()
        val totalLines = lines.size

        return if (aroundCursor && IdeContext.cursorPosition != null) {
            val (cursorLine, _) = IdeContext.cursorPosition!!
            val start = maxOf(0, cursorLine - contextLines - 1)
            val end = minOf(totalLines, cursorLine + contextLines)

            val snippet = lines.subList(start, end)
                .mapIndexed { idx, line ->
                    val lineNum = start + idx + 1
                    val marker = if (lineNum == cursorLine) " → " else "   "
                    "$marker${lineNum.toString().padStart(4)}│ $line"
                }
                .joinToString("\n")

            """
                |📄 $currentFile (строки ${start + 1}-$end из $totalLines, курсор на строке $cursorLine):
                |
                |```${getFileExtension(currentFile)}
                |$snippet
                |```
            """.trimMargin()
        } else {
            // Ограничиваем вывод до 100 строк
            val maxLines = 100
            val content = if (totalLines > maxLines) {
                lines.take(maxLines).joinToString("\n") + "\n\n... (ещё ${totalLines - maxLines} строк)"
            } else {
                lines.joinToString("\n")
            }

            """
                |📄 $currentFile ($totalLines строк):
                |
                |```${getFileExtension(currentFile)}
                |$content
                |```
            """.trimMargin()
        }
    }

    private fun getFileExtension(path: String): String {
        return when (path.substringAfterLast('.', "").lowercase()) {
            "kt" -> "kotlin"
            "kts" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "js" -> "javascript"
            "ts" -> "typescript"
            "tsx" -> "tsx"
            "jsx" -> "jsx"
            "md" -> "markdown"
            "json" -> "json"
            "xml" -> "xml"
            "yaml", "yml" -> "yaml"
            "sh" -> "bash"
            "sql" -> "sql"
            "html" -> "html"
            "css" -> "css"
            "scss" -> "scss"
            else -> ""
        }
    }
}

/**
 * Инструмент для очистки контекста IDE.
 */
@Tool(
    name = "ide_clear_context",
    description = "Очистить контекст IDE (текущий файл, открытые файлы, выделение и т.д.)"
)
object IdeClearContextTool : AnnotatedAgentTool() {

    override suspend fun execute(arguments: String): String {
        IdeContext.clear()
        return "✅ Контекст IDE очищен"
    }
}
