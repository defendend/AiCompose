package org.example.tools.devassistant.git

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool

/**
 * Инструмент для получения информации о ветках git репозитория.
 */
@Tool(
    name = "git_branch",
    description = "Получить информацию о ветках git репозитория: текущая ветка, список всех веток"
)
@Param(
    name = "list_all",
    description = "Показать все ветки включая remote (по умолчанию: false)",
    type = "boolean",
    required = false
)
@Param(
    name = "path",
    description = "Путь к репозиторию (по умолчанию: текущая директория)",
    type = "string",
    required = false
)
object GitBranchTool : GitToolBase() {

    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val listAll = json["list_all"]?.jsonPrimitive?.booleanOrNull ?: false
        val path = json["path"]?.jsonPrimitive?.content
            ?: System.getenv("PROJECT_PATH")
            ?: "."

        // Получаем текущую ветку
        val currentBranchResult = runGitCommand("rev-parse", "--abbrev-ref", "HEAD", workDir = path)
        val currentBranch = if (currentBranchResult.success) currentBranchResult.output else "unknown"

        // Получаем список веток
        val branchArgs = if (listAll) arrayOf("branch", "-a", "-v") else arrayOf("branch", "-v")
        val result = runGitCommand(*branchArgs, workDir = path)

        if (!result.success) {
            return "❌ ${result.output}"
        }

        return formatBranches(currentBranch, result.output, listAll)
    }

    private fun formatBranches(currentBranch: String, output: String, showRemote: Boolean): String {
        val lines = output.lines().filter { it.isNotBlank() }

        val localBranches = mutableListOf<BranchInfo>()
        val remoteBranches = mutableListOf<BranchInfo>()

        lines.forEach { line ->
            val isCurrent = line.startsWith("*")
            val cleanLine = line.removePrefix("*").trim()
            val parts = cleanLine.split(Regex("\\s+"), limit = 3)

            if (parts.isNotEmpty()) {
                val branchName = parts[0]
                val commitHash = parts.getOrNull(1) ?: ""
                val commitMessage = parts.getOrNull(2) ?: ""

                val info = BranchInfo(branchName, commitHash, commitMessage, isCurrent)

                if (branchName.startsWith("remotes/")) {
                    remoteBranches.add(info.copy(name = branchName.removePrefix("remotes/")))
                } else {
                    localBranches.add(info)
                }
            }
        }

        return buildString {
            appendLine("🌿 Git Branches")
            appendLine("━".repeat(50))
            appendLine()
            appendLine("📍 Текущая ветка: $currentBranch")
            appendLine()

            appendLine("📂 Локальные ветки (${localBranches.size}):")
            localBranches.forEach { branch ->
                val prefix = if (branch.isCurrent) " ▶ " else "   "
                val shortHash = branch.commitHash.take(7)
                appendLine("$prefix${branch.name} ($shortHash)")
                if (branch.commitMessage.isNotBlank()) {
                    appendLine("      ${branch.commitMessage.take(60)}")
                }
            }

            if (showRemote && remoteBranches.isNotEmpty()) {
                appendLine()
                appendLine("🌐 Remote ветки (${remoteBranches.size}):")
                remoteBranches.take(20).forEach { branch ->
                    val shortHash = branch.commitHash.take(7)
                    appendLine("   ${branch.name} ($shortHash)")
                }
                if (remoteBranches.size > 20) {
                    appendLine("   ... и ещё ${remoteBranches.size - 20}")
                }
            }
        }
    }

    private data class BranchInfo(
        val name: String,
        val commitHash: String,
        val commitMessage: String,
        val isCurrent: Boolean
    )
}
