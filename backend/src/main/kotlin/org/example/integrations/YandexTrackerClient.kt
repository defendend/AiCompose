package org.example.integrations

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Клиент для работы с API Яндекс.Трекера
 *
 * API Documentation: https://yandex.cloud/ru/docs/tracker/
 */
class YandexTrackerClient(
    private val httpClient: HttpClient,
    private val oauthToken: String,
    private val orgId: String
) {
    private val baseUrl = "https://api.tracker.yandex.net/v2"

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * Получить список задач по фильтру
     *
     * @param queue Очередь (например, "MYPROJECT")
     * @param status Статус (например, "open", "closed")
     * @param assignee Исполнитель
     * @return Список задач
     */
    suspend fun searchIssues(
        queue: String? = null,
        status: String? = null,
        assignee: String? = null
    ): TrackerSearchResult {
        val filter = buildMap<String, Any> {
            queue?.let { put("queue", it) }
            status?.let { put("status", it) }
            assignee?.let { put("assignee", it) }
        }

        val response: HttpResponse = httpClient.post("$baseUrl/issues/_search") {
            header("Authorization", "OAuth $oauthToken")
            header("X-Org-ID", orgId)
            contentType(ContentType.Application.Json)
            setBody(mapOf("filter" to filter))
        }

        if (!response.status.isSuccess()) {
            throw TrackerException("Failed to search issues: ${response.status} - ${response.bodyAsText()}")
        }

        val issues: List<TrackerIssue> = response.body()
        return TrackerSearchResult(
            issues = issues,
            totalCount = issues.size
        )
    }

    /**
     * Получить количество открытых задач в очереди
     */
    suspend fun getOpenIssuesCount(queue: String): Int {
        val result = searchIssues(queue = queue, status = "open")
        return result.totalCount
    }

    /**
     * Получить детали задачи по ключу
     */
    suspend fun getIssue(issueKey: String): TrackerIssue {
        val response: HttpResponse = httpClient.get("$baseUrl/issues/$issueKey") {
            header("Authorization", "OAuth $oauthToken")
            header("X-Org-ID", orgId)
        }

        if (!response.status.isSuccess()) {
            throw TrackerException("Failed to get issue: ${response.status} - ${response.bodyAsText()}")
        }

        return response.body()
    }
}

/**
 * Результат поиска задач
 */
@Serializable
data class TrackerSearchResult(
    val issues: List<TrackerIssue>,
    val totalCount: Int
)

/**
 * Задача в Трекере
 */
@Serializable
data class TrackerIssue(
    val key: String,
    val summary: String,
    val description: String? = null,
    val status: TrackerStatus? = null,
    val assignee: TrackerUser? = null,
    val queue: TrackerQueue? = null
)

@Serializable
data class TrackerStatus(
    val key: String,
    val display: String
)

@Serializable
data class TrackerUser(
    val id: String,
    val display: String
)

@Serializable
data class TrackerQueue(
    val key: String,
    val display: String
)

/**
 * Исключение при работе с Трекером
 */
class TrackerException(message: String) : Exception(message)
