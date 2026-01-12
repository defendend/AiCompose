package org.example.tools.devassistant.git

import org.example.tools.core.AnnotatedAgentTool
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Базовый класс для Git инструментов.
 * Предоставляет общую логику выполнения git команд через Process.
 */
abstract class GitToolBase : AnnotatedAgentTool() {

    /**
     * Выполняет git команду и возвращает результат.
     *
     * @param args Аргументы git команды (без "git")
     * @param workDir Рабочая директория (по умолчанию: текущая)
     * @param timeoutSeconds Таймаут выполнения
     * @return Результат выполнения команды
     */
    protected fun runGitCommand(
        vararg args: String,
        workDir: String = ".",
        timeoutSeconds: Long = 30
    ): GitResult {
        return try {
            val absolutePath = File(workDir).absoluteFile
            if (!absolutePath.exists()) {
                return GitResult(
                    success = false,
                    output = "Директория не существует: $absolutePath",
                    exitCode = -1
                )
            }

            // Проверяем, что это git репозиторий
            val gitDir = File(absolutePath, ".git")
            if (!gitDir.exists() && !isInsideGitRepo(absolutePath)) {
                return GitResult(
                    success = false,
                    output = "Не git репозиторий: $absolutePath",
                    exitCode = -1
                )
            }

            val process = ProcessBuilder("git", *args)
                .directory(absolutePath)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return GitResult(
                    success = false,
                    output = "Таймаут выполнения команды",
                    exitCode = -1
                )
            }

            GitResult(
                success = process.exitValue() == 0,
                output = output.trim(),
                exitCode = process.exitValue()
            )
        } catch (e: Exception) {
            GitResult(
                success = false,
                output = "Ошибка выполнения git: ${e.message}",
                exitCode = -1
            )
        }
    }

    /**
     * Проверяет, находится ли директория внутри git репозитория.
     */
    private fun isInsideGitRepo(dir: File): Boolean {
        var current: File? = dir
        while (current != null) {
            if (File(current, ".git").exists()) {
                return true
            }
            current = current.parentFile
        }
        return false
    }

    /**
     * Результат выполнения git команды.
     */
    data class GitResult(
        val success: Boolean,
        val output: String,
        val exitCode: Int
    )
}
