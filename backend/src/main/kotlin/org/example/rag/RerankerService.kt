package org.example.rag

import org.slf4j.LoggerFactory

/**
 * Сервис для реранкинга и фильтрации результатов поиска.
 *
 * Поддерживает:
 * 1. Фильтрацию по порогу релевантности (min_relevance)
 * 2. Реранкинг результатов (в будущем можно добавить ML модель)
 */
object RerankerService {
    private val logger = LoggerFactory.getLogger(RerankerService::class.java)

    /**
     * Фильтрует результаты поиска по минимальному порогу релевантности.
     *
     * @param results Результаты поиска
     * @param minRelevance Минимальный порог релевантности (0.0 - 1.0)
     * @return Отфильтрованные результаты
     */
    fun filterByRelevance(
        results: List<DocumentIndex.SearchResult>,
        minRelevance: Float
    ): FilterResult {
        require(minRelevance in 0.0..1.0) { "minRelevance должен быть в диапазоне 0.0-1.0" }

        val filtered = results.filter { it.score >= minRelevance }
        val removed = results.size - filtered.size

        logger.debug(
            "Фильтрация: порог=$minRelevance, всего=${results.size}, " +
                "отфильтровано=${filtered.size}, удалено=$removed"
        )

        return FilterResult(
            filtered = filtered,
            removed = removed,
            originalCount = results.size,
            threshold = minRelevance
        )
    }

    /**
     * Реранкинг результатов на основе дополнительных факторов.
     *
     * Текущая реализация: сортировка по релевантности (уже есть в поиске).
     * В будущем можно добавить:
     * - Cross-encoder модель для более точного ранжирования
     * - Учет свежести документа
     * - Учет авторитетности источника
     * - Diversity (разнообразие результатов)
     *
     * @param query Поисковый запрос
     * @param results Результаты поиска
     * @return Реранкированные результаты
     */
    fun rerank(
        query: String,
        results: List<DocumentIndex.SearchResult>
    ): RerankResult {
        // Текущая реализация: простая сортировка по score (уже отсортировано)
        // TODO: Добавить cross-encoder или другую модель реранкинга

        val reranked = results.sortedByDescending { it.score }

        logger.debug("Реранкинг: запрос='$query', результатов=${results.size}")

        return RerankResult(
            reranked = reranked,
            method = "score_based", // Метод реранкинга
            improved = false // Пока не улучшаем, только сортируем
        )
    }

    /**
     * Комбинированная фильтрация и реранкинг.
     *
     * @param query Поисковый запрос
     * @param results Результаты поиска
     * @param minRelevance Минимальный порог релевантности
     * @param enableRerank Включить реранкинг
     * @return Обработанные результаты
     */
    fun processResults(
        query: String,
        results: List<DocumentIndex.SearchResult>,
        minRelevance: Float? = null,
        enableRerank: Boolean = false
    ): ProcessedResults {
        var processed = results

        // Шаг 1: Фильтрация по порогу
        val filterResult = if (minRelevance != null && minRelevance > 0.0f) {
            val filtered = filterByRelevance(results, minRelevance)
            processed = filtered.filtered
            filtered
        } else {
            null
        }

        // Шаг 2: Реранкинг
        val rerankResult = if (enableRerank && processed.isNotEmpty()) {
            val reranked = rerank(query, processed)
            processed = reranked.reranked
            reranked
        } else {
            null
        }

        return ProcessedResults(
            results = processed,
            filterResult = filterResult,
            rerankResult = rerankResult,
            originalCount = results.size,
            finalCount = processed.size
        )
    }

    /**
     * Рекомендуемые пороги релевантности для разных сценариев.
     */
    object RelevanceThresholds {
        const val STRICT = 0.5f      // Строгий фильтр: только очень релевантные
        const val MODERATE = 0.3f    // Умеренный: хорошая релевантность
        const val RELAXED = 0.1f     // Мягкий: допускает слабо релевантные
        const val NONE = 0.0f        // Без фильтрации
    }

    /**
     * Результат фильтрации.
     */
    data class FilterResult(
        val filtered: List<DocumentIndex.SearchResult>,
        val removed: Int,
        val originalCount: Int,
        val threshold: Float
    )

    /**
     * Результат реранкинга.
     */
    data class RerankResult(
        val reranked: List<DocumentIndex.SearchResult>,
        val method: String,
        val improved: Boolean
    )

    /**
     * Объединённый результат обработки.
     */
    data class ProcessedResults(
        val results: List<DocumentIndex.SearchResult>,
        val filterResult: FilterResult?,
        val rerankResult: RerankResult?,
        val originalCount: Int,
        val finalCount: Int
    ) {
        val wasFiltered: Boolean get() = filterResult != null
        val wasReranked: Boolean get() = rerankResult != null
        val removedCount: Int get() = originalCount - finalCount
    }
}
