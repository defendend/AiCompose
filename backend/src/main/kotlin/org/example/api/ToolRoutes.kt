package org.example.api

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.example.tools.core.ToolRegistry

/**
 * API для прямого вызова инструментов (без LLM).
 * Полезно для тестирования и демонстрации работы tools.
 */
fun Route.toolRoutes() {
    route("/api/tools") {
        /**
         * POST /api/tools/execute
         *
         * Выполняет инструмент напрямую, минуя LLM.
         *
         * Request:
         * ```json
         * {
         *   "tool": "rag_index_documents",
         *   "arguments": "{\"path\": \"/tmp/docs\", \"extensions\": \"md\"}"
         * }
         * ```
         *
         * Response:
         * ```json
         * {
         *   "result": "📚 Индексация завершена...",
         *   "tool": "rag_index_documents",
         *   "success": true
         * }
         * ```
         */
        post("/execute") {
            val request = call.receive<ToolExecuteRequest>()

            val result = try {
                val output = ToolRegistry.executeTool(request.tool, request.arguments)
                ToolExecuteResponse(
                    result = output,
                    tool = request.tool,
                    success = !output.contains("Ошибка")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ToolExecuteResponse(
                        result = "Ошибка: ${e.message}",
                        tool = request.tool,
                        success = false
                    )
                )
                return@post
            }

            call.respond(result)
        }

        /**
         * GET /api/tools/list
         *
         * Возвращает список всех доступных инструментов.
         *
         * Response:
         * ```json
         * {
         *   "tools": ["rag_index_documents", "rag_search", "docker_run", ...]
         * }
         * ```
         */
        get("/list") {
            val tools = ToolRegistry.getToolNames()
            call.respond(ToolListResponse(tools = tools.sorted()))
        }
    }
}

@Serializable
data class ToolExecuteRequest(
    val tool: String,
    val arguments: String
)

@Serializable
data class ToolExecuteResponse(
    val result: String,
    val tool: String,
    val success: Boolean
)

@Serializable
data class ToolListResponse(
    val tools: List<String>
)
