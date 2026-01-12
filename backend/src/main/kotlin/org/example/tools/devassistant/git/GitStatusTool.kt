package org.example.tools.devassistant.git

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool

/**
 * Инструмент для получения статуса git репозитория.
 * Показывает текущую ветку, staged/unstaged изменения и untracked файлы.
 */
@Tool(
    name = "git_status",
    description = "Получить статус git репозитория: текущая ветка, staged/unstaged изменения, untracked файлы"
)
@Param(
    name = "path",
    description = "Путь к репозиторию (по умолчанию: текущая директория)",
    type = "string",
    required = false
)
object GitStatusTool : GitToolBase() {

    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val path = json["path"]?.jsonPrimitive?.content ?: "."

        val result = runGitCommand("status", "--porcelain=v2", "--branch", workDir = path)

        if (!result.success) {
            return "❌ ${result.output}"
        }

        return formatGitStatus(result.output)
    }

    private fun formatGitStatus(rawOutput: String): String {
        val lines = rawOutput.lines()

        // Парсинг информации о ветке
        val branchHead = lines.find { it.startsWith("# branch.head") }
            ?.substringAfter("# branch.head ")?.trim() ?: "unknown"

        val branchUpstream = lines.find { it.startsWith("# branch.upstream") }
            ?.substringAfter("# branch.upstream ")?.trim()

        val branchAB = lines.find { it.startsWith("# branch.ab") }
            ?.substringAfter("# branch.ab ")?.trim()

        // Парсинг файлов
        val staged = mutableListOf<String>()
        val unstaged = mutableListOf<String>()
        val untracked = mutableListOf<String>()

        lines.forEach { line ->
            when {
                // Обычные изменения: "1 XY ... path"
                line.startsWith("1 ") -> {
                    val parts = line.split(" ")
                    if (parts.size >= 9) {
                        val xy = parts[1]
                        val filePath = parts.drop(8).joinToString(" ")
                        if (xy[0] != '.') staged.add("${getStatusSymbol(xy[0])} $filePath")
                        if (xy[1] != '.') unstaged.add("${getStatusSymbol(xy[1])} $filePath")
                    }
                }
                // Переименования: "2 XY ... path\torigPath"
                line.startsWith("2 ") -> {
                    val parts = line.split(" ")
                    if (parts.size >= 10) {
                        val xy = parts[1]
                        val filePath = parts.drop(9).joinToString(" ").split("\t").first()
                        if (xy[0] != '.') staged.add("R $filePath")
                        if (xy[1] != '.') unstaged.add("R $filePath")
                    }
                }
                // Untracked файлы
                line.startsWith("? ") -> {
                    untracked.add(line.substringAfter("? "))
                }
            }
        }

        return buildString {
            appendLine("📁 Git Status")
            appendLine("━".repeat(40))
            appendLine()
            appendLine("🌿 Ветка: $branchHead")
            if (branchUpstream != null) {
                appendLine("   Upstream: $branchUpstream")
            }
            if (branchAB != null) {
                val (ahead, behind) = parseAheadBehind(branchAB)
                if (ahead > 0 || behind > 0) {
                    appendLine("   ↑$ahead ↓$behind")
                }
            }
            appendLine()

            if (staged.isNotEmpty()) {
                appendLine("✅ Staged (${staged.size}):")
                staged.forEach { appendLine("   $it") }
                appendLine()
            }

            if (unstaged.isNotEmpty()) {
                appendLine("📝 Modified (${unstaged.size}):")
                unstaged.forEach { appendLine("   $it") }
                appendLine()
            }

            if (untracked.isNotEmpty()) {
                appendLine("❓ Untracked (${untracked.size}):")
                untracked.forEach { appendLine("   $it") }
                appendLine()
            }

            if (staged.isEmpty() && unstaged.isEmpty() && untracked.isEmpty()) {
                appendLine("✨ Working tree clean")
            }
        }
    }

    private fun getStatusSymbol(char: Char): String = when (char) {
        'M' -> "M" // Modified
        'A' -> "A" // Added
        'D' -> "D" // Deleted
        'R' -> "R" // Renamed
        'C' -> "C" // Copied
        'U' -> "U" // Updated but unmerged
        else -> char.toString()
    }

    private fun parseAheadBehind(ab: String): Pair<Int, Int> {
        val parts = ab.split(" ")
        val ahead = parts.getOrNull(0)?.removePrefix("+")?.toIntOrNull() ?: 0
        val behind = parts.getOrNull(1)?.removePrefix("-")?.toIntOrNull() ?: 0
        return ahead to behind
    }
}
