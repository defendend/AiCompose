package org.example.tools.devassistant.github

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.example.tools.core.AnnotatedAgentTool

/**
 * Базовый класс для GitHub API инструментов.
 */
abstract class GithubToolBase : AnnotatedAgentTool() {

    companion object {
        private const val GITHUB_API_URL = "https://api.github.com"
        private const val TIMEOUT_MS = 30000L

        private val httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = false
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MS
                connectTimeoutMillis = 10000
            }
            defaultRequest {
                header("Accept", "application/vnd.github.v3+json")
                header("X-GitHub-Api-Version", "2022-11-28")
            }
        }
    }

    /**
     * Выполнить GET запрос к GitHub API.
     */
    protected suspend fun githubGet(
        path: String,
        token: String,
        accept: String = "application/vnd.github.v3+json"
    ): GithubResponse {
        return try {
            val response = httpClient.get("$GITHUB_API_URL$path") {
                header("Authorization", "Bearer $token")
                header("Accept", accept)
            }
            GithubResponse(
                success = response.status.isSuccess(),
                statusCode = response.status.value,
                body = response.bodyAsText()
            )
        } catch (e: Exception) {
            GithubResponse(
                success = false,
                statusCode = 0,
                body = "",
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Выполнить POST запрос к GitHub API.
     */
    protected suspend fun githubPost(
        path: String,
        token: String,
        body: String
    ): GithubResponse {
        return try {
            val response = httpClient.post("$GITHUB_API_URL$path") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            GithubResponse(
                success = response.status.isSuccess(),
                statusCode = response.status.value,
                body = response.bodyAsText()
            )
        } catch (e: Exception) {
            GithubResponse(
                success = false,
                statusCode = 0,
                body = "",
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Форматировать ошибку.
     */
    protected fun formatError(message: String): String {
        return "❌ Ошибка GitHub API: $message"
    }

    /**
     * Проверить обязательный параметр.
     */
    protected fun requireParam(value: String?, name: String): String {
        return value?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Параметр '$name' обязателен")
    }
}

/**
 * Результат запроса к GitHub API.
 */
data class GithubResponse(
    val success: Boolean,
    val statusCode: Int,
    val body: String,
    val error: String? = null
)
