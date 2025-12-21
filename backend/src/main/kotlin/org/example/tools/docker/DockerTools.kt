package org.example.tools.docker

import kotlinx.serialization.json.*
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool
import org.example.tools.core.AnnotatedAgentTool
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Инструмент 1: Запуск Docker контейнера
 */
@Tool(
    name = "docker_run",
    description = "Запускает Docker контейнер с указанным образом и параметрами. " +
            "Поддерживает проброс портов, переменные окружения, имя контейнера."
)
@Param(name = "image", description = "Образ Docker (например: nginx:latest, postgres:15, redis:alpine)", type = "string", required = true)
@Param(name = "name", description = "Имя контейнера (опционально)", type = "string", required = false)
@Param(name = "ports", description = "Проброс портов в формате 'host:container' (например: '8080:80')", type = "string", required = false)
@Param(name = "env", description = "Переменные окружения в формате 'KEY=VALUE', разделённые запятыми", type = "string", required = false)
@Param(name = "detach", description = "Запустить в фоновом режиме (по умолчанию: true)", type = "boolean", required = false)
object DockerRunTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val image = json["image"]?.jsonPrimitive?.content ?: return "❌ Ошибка: image не может быть пустым"
        val name = json["name"]?.jsonPrimitive?.content
        val ports = json["ports"]?.jsonPrimitive?.content
        val env = json["env"]?.jsonPrimitive?.content
        val detach = json["detach"]?.jsonPrimitive?.booleanOrNull ?: true

        // Проверка доступности Docker
        if (!isDockerAvailable()) {
            return "❌ Docker не доступен. Установите Docker и убедитесь что он запущен."
        }

        // Формируем команду docker run
        val command = buildList {
            add("docker")
            add("run")
            if (detach) add("-d")
            if (name != null) {
                add("--name")
                add(name)
            }
            if (ports != null) {
                add("-p")
                add(ports)
            }
            if (env != null) {
                env.split(",").forEach { envVar ->
                    add("-e")
                    add(envVar.trim())
                }
            }
            add(image)
        }

        return try {
            val result = executeCommand(command, timeoutSeconds = 30)

            if (result.exitCode == 0) {
                val containerId = result.output.trim().take(12)
                """
                🐳 Контейнер успешно запущен

                Образ: $image
                ${if (name != null) "Имя: $name" else ""}
                ID: $containerId
                ${if (ports != null) "Порты: $ports" else ""}
                ${if (env != null) "Переменные: $env" else ""}
                Режим: ${if (detach) "фоновый" else "интерактивный"}

                Команда: ${command.joinToString(" ")}
                """.trimIndent()
            } else {
                """
                ❌ Ошибка запуска контейнера

                Код выхода: ${result.exitCode}
                Вывод: ${result.output}
                Ошибки: ${result.error}
                Команда: ${command.joinToString(" ")}
                """.trimIndent()
            }
        } catch (e: Exception) {
            "❌ Исключение при запуске контейнера: ${e.message}"
        }
    }
}

/**
 * Инструмент 2: Выполнение команды в контейнере
 */
@Tool(
    name = "docker_exec",
    description = "Выполняет команду внутри запущенного Docker контейнера. " +
            "Полезно для проверки работы сервисов, получения информации из контейнера."
)
@Param(name = "container", description = "Имя или ID контейнера", type = "string", required = true)
@Param(name = "command", description = "Команда для выполнения (например: 'ls -la', 'curl localhost')", type = "string", required = true)
object DockerExecTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val container = json["container"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: container не может быть пустым"
        val command = json["command"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: command не может быть пустым"

        if (!isDockerAvailable()) {
            return "❌ Docker не доступен"
        }

        // Разбиваем команду на части
        val cmdParts = command.split(" ")
        val dockerCommand = listOf("docker", "exec", container) + cmdParts

        return try {
            val result = executeCommand(dockerCommand, timeoutSeconds = 30)

            """
            🐳 Выполнение команды в контейнере

            Контейнер: $container
            Команда: $command
            Код выхода: ${result.exitCode}

            --- ВЫВОД ---
            ${result.output}
            ${if (result.error.isNotBlank()) "\n--- ОШИБКИ ---\n${result.error}" else ""}
            """.trimIndent()
        } catch (e: Exception) {
            "❌ Исключение при выполнении команды: ${e.message}"
        }
    }
}

/**
 * Инструмент 3: Получение логов контейнера
 */
@Tool(
    name = "docker_logs",
    description = "Получает логи Docker контейнера. Полезно для диагностики проблем и проверки работы."
)
@Param(name = "container", description = "Имя или ID контейнера", type = "string", required = true)
@Param(name = "tail", description = "Количество последних строк (по умолчанию: 50)", type = "integer", required = false)
object DockerLogsTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val container = json["container"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: container не может быть пустым"
        val tail = json["tail"]?.jsonPrimitive?.intOrNull ?: 50

        if (!isDockerAvailable()) {
            return "❌ Docker не доступен"
        }

        val command = listOf("docker", "logs", "--tail", tail.toString(), container)

        return try {
            val result = executeCommand(command, timeoutSeconds = 10)

            if (result.exitCode == 0) {
                """
                📋 Логи контейнера: $container

                Последние $tail строк:

                ${result.output}
                ${if (result.error.isNotBlank()) "\n--- STDERR ---\n${result.error}" else ""}
                """.trimIndent()
            } else {
                "❌ Не удалось получить логи контейнера '$container': ${result.error}"
            }
        } catch (e: Exception) {
            "❌ Исключение при получении логов: ${e.message}"
        }
    }
}

/**
 * Инструмент 4: Остановка контейнера
 */
@Tool(
    name = "docker_stop",
    description = "Останавливает запущенный Docker контейнер."
)
@Param(name = "container", description = "Имя или ID контейнера", type = "string", required = true)
@Param(name = "remove", description = "Удалить контейнер после остановки (по умолчанию: true)", type = "boolean", required = false)
object DockerStopTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        val json = Json.parseToJsonElement(arguments).jsonObject
        val container = json["container"]?.jsonPrimitive?.content
            ?: return "❌ Ошибка: container не может быть пустым"
        val remove = json["remove"]?.jsonPrimitive?.booleanOrNull ?: true

        if (!isDockerAvailable()) {
            return "❌ Docker не доступен"
        }

        return try {
            // Останавливаем контейнер
            val stopResult = executeCommand(listOf("docker", "stop", container), timeoutSeconds = 30)

            if (stopResult.exitCode != 0) {
                return "❌ Не удалось остановить контейнер '$container': ${stopResult.error}"
            }

            val message = buildString {
                appendLine("🛑 Контейнер '$container' успешно остановлен")

                if (remove) {
                    val removeResult = executeCommand(listOf("docker", "rm", container), timeoutSeconds = 10)
                    if (removeResult.exitCode == 0) {
                        appendLine("🗑️  Контейнер удалён")
                    } else {
                        appendLine("⚠️  Не удалось удалить контейнер: ${removeResult.error}")
                    }
                }
            }

            message
        } catch (e: Exception) {
            "❌ Исключение при остановке контейнера: ${e.message}"
        }
    }
}

/**
 * Инструмент 5: Список запущенных контейнеров
 */
@Tool(
    name = "docker_ps",
    description = "Показывает список всех запущенных Docker контейнеров с их статусом, портами и именами."
)
object DockerPsTool : AnnotatedAgentTool() {
    override suspend fun execute(arguments: String): String {
        if (!isDockerAvailable()) {
            return "❌ Docker не доступен"
        }

        val command = listOf("docker", "ps", "--format", "{{.ID}}|{{.Image}}|{{.Names}}|{{.Status}}|{{.Ports}}")

        return try {
            val result = executeCommand(command, timeoutSeconds = 10)

            if (result.exitCode == 0) {
                if (result.output.isBlank()) {
                    "📦 Нет запущенных контейнеров"
                } else {
                    val containers = result.output.lines().filter { it.isNotBlank() }
                    buildString {
                        appendLine("📦 Запущенные контейнеры (${containers.size}):")
                        appendLine()
                        containers.forEach { line ->
                            val parts = line.split("|")
                            if (parts.size >= 4) {
                                val id = parts[0].take(12)
                                val image = parts[1]
                                val name = parts[2]
                                val status = parts[3]
                                val ports = parts.getOrNull(4) ?: ""

                                appendLine("🐳 $name")
                                appendLine("   ID: $id")
                                appendLine("   Образ: $image")
                                appendLine("   Статус: $status")
                                if (ports.isNotBlank()) {
                                    appendLine("   Порты: $ports")
                                }
                                appendLine()
                            }
                        }
                    }
                }
            } else {
                "❌ Ошибка получения списка контейнеров: ${result.error}"
            }
        } catch (e: Exception) {
            "❌ Исключение при получении списка: ${e.message}"
        }
    }
}

// === Вспомогательные функции ===

/**
 * Результат выполнения команды
 */
data class CommandResult(
    val exitCode: Int,
    val output: String,
    val error: String
)

/**
 * Проверяет доступность Docker
 */
private fun isDockerAvailable(): Boolean {
    return try {
        val result = executeCommand(listOf("docker", "--version"), timeoutSeconds = 5)
        result.exitCode == 0
    } catch (e: Exception) {
        false
    }
}

/**
 * Выполняет системную команду с таймаутом
 */
private fun executeCommand(command: List<String>, timeoutSeconds: Long = 30): CommandResult {
    val processBuilder = ProcessBuilder(command)
    processBuilder.redirectErrorStream(false)

    val process = processBuilder.start()

    val outputReader = BufferedReader(InputStreamReader(process.inputStream))
    val errorReader = BufferedReader(InputStreamReader(process.errorStream))

    val output = StringBuilder()
    val error = StringBuilder()

    // Читаем вывод в отдельных потоках
    val outputThread = Thread {
        outputReader.use { reader ->
            reader.lines().forEach { line ->
                output.appendLine(line)
            }
        }
    }

    val errorThread = Thread {
        errorReader.use { reader ->
            reader.lines().forEach { line ->
                error.appendLine(line)
            }
        }
    }

    outputThread.start()
    errorThread.start()

    // Ждём завершения с таймаутом
    val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

    if (!finished) {
        process.destroyForcibly()
        throw RuntimeException("Команда превысила таймаут ${timeoutSeconds}с")
    }

    outputThread.join(1000)
    errorThread.join(1000)

    return CommandResult(
        exitCode = process.exitValue(),
        output = output.toString().trim(),
        error = error.toString().trim()
    )
}
