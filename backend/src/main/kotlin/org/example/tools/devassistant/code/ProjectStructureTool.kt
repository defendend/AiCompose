package org.example.tools.devassistant.code

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool
import org.example.tools.core.AnnotatedAgentTool
import java.io.File

/**
 * Инструмент для отображения структуры проекта (дерево файлов).
 */
@Tool(
    name = "project_structure",
    description = "Показать структуру проекта в виде дерева файлов и директорий"
)
@Param(
    name = "path",
    description = "Путь к проекту (по умолчанию: текущая директория)",
    type = "string",
    required = false
)
@Param(
    name = "depth",
    description = "Глубина дерева (по умолчанию: 3)",
    type = "integer",
    required = false
)
@Param(
    name = "show_files",
    description = "Показывать файлы (по умолчанию: true)",
    type = "boolean",
    required = false
)
@Param(
    name = "show_hidden",
    description = "Показывать скрытые файлы (по умолчанию: false)",
    type = "boolean",
    required = false
)
object ProjectStructureTool : AnnotatedAgentTool() {

    // Исключаемые директории
    private val EXCLUDED_DIRS = setOf(
        ".git", ".gradle", ".idea", "build", "out", "target",
        "node_modules", "__pycache__", ".venv", "venv",
        ".cache", ".npm", ".yarn"
    )

    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val path = json["path"]?.jsonPrimitive?.content
            ?: System.getenv("PROJECT_PATH")
            ?: "."
        val depth = json["depth"]?.jsonPrimitive?.intOrNull ?: 3
        val showFiles = json["show_files"]?.jsonPrimitive?.booleanOrNull ?: true
        val showHidden = json["show_hidden"]?.jsonPrimitive?.booleanOrNull ?: false

        val baseDir = File(path).absoluteFile
        if (!baseDir.exists()) {
            return "❌ Директория не существует: ${baseDir.absolutePath}"
        }

        if (!baseDir.isDirectory) {
            return "❌ Это файл, а не директория: ${baseDir.absolutePath}"
        }

        return buildString {
            appendLine("📁 Структура проекта: ${baseDir.name}")
            appendLine("━".repeat(50))
            appendLine()

            val stats = TreeStats()
            appendTree(this, baseDir, "", depth, showFiles, showHidden, stats)

            appendLine()
            appendLine("━".repeat(50))
            appendLine("📊 Статистика:")
            appendLine("   📁 Директорий: ${stats.directories}")
            appendLine("   📄 Файлов: ${stats.files}")
            if (stats.hiddenSkipped > 0) {
                appendLine("   👁️ Скрыто: ${stats.hiddenSkipped}")
            }
        }
    }

    private fun appendTree(
        sb: StringBuilder,
        dir: File,
        prefix: String,
        remainingDepth: Int,
        showFiles: Boolean,
        showHidden: Boolean,
        stats: TreeStats
    ) {
        if (remainingDepth <= 0) {
            sb.appendLine("$prefix└── ...")
            return
        }

        val children = dir.listFiles()
            ?.filter { file ->
                val isExcluded = file.name in EXCLUDED_DIRS
                val isHidden = file.name.startsWith(".") && !showHidden

                if (isHidden && file.isDirectory) stats.hiddenSkipped++

                !isExcluded && !isHidden
            }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: return

        children.forEachIndexed { index, child ->
            val isLast = index == children.lastIndex
            val connector = if (isLast) "└── " else "├── "
            val newPrefix = prefix + if (isLast) "    " else "│   "

            if (child.isDirectory) {
                stats.directories++
                val fileCount = child.listFiles()?.count { it.isFile } ?: 0
                val dirCount = child.listFiles()?.count { it.isDirectory } ?: 0

                val info = if (fileCount > 0 || dirCount > 0) {
                    " (${dirCount}d, ${fileCount}f)"
                } else {
                    ""
                }

                sb.appendLine("$prefix$connector📁 ${child.name}$info")
                appendTree(sb, child, newPrefix, remainingDepth - 1, showFiles, showHidden, stats)
            } else if (showFiles) {
                stats.files++
                val icon = getFileIcon(child.extension.lowercase())
                val size = formatSize(child.length())
                sb.appendLine("$prefix$connector$icon ${child.name} ($size)")
            }
        }
    }

    private fun getFileIcon(extension: String): String = when (extension) {
        // Kotlin/Java
        "kt", "kts" -> "🟣"
        "java" -> "☕"

        // Web
        "js", "jsx" -> "🟨"
        "ts", "tsx" -> "🔷"
        "html" -> "🌐"
        "css", "scss", "sass" -> "🎨"

        // Config
        "json" -> "📋"
        "yaml", "yml" -> "⚙️"
        "xml" -> "📄"
        "properties" -> "🔧"
        "gradle" -> "🐘"

        // Docs
        "md" -> "📝"
        "txt" -> "📃"

        // Other
        "py" -> "🐍"
        "rb" -> "💎"
        "go" -> "🔵"
        "rs" -> "🦀"
        "sh", "bash" -> "💻"
        "sql" -> "🗃️"

        else -> "📄"
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
    }

    private class TreeStats {
        var directories = 0
        var files = 0
        var hiddenSkipped = 0
    }
}
