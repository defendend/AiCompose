package org.example.rag

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Индекс документов с эмбеддингами для векторного поиска
 */
class DocumentIndex(
    private val embeddings: SimpleEmbeddings = SimpleEmbeddings()
) {

    /**
     * Элемент индекса
     */
    @Serializable
    data class IndexEntry(
        val id: String,
        val source: String,
        val content: String,
        val embedding: List<Float>
    )

    /**
     * Результат поиска
     */
    data class SearchResult(
        val id: String,
        val source: String,
        val content: String,
        val score: Float
    )

    /**
     * Сохраненный индекс
     */
    @Serializable
    data class SavedIndex(
        val entries: List<IndexEntry>,
        val vectorDimension: Int,
        val totalDocuments: Int,
        val createdAt: Long = System.currentTimeMillis()
    )

    private val index = mutableListOf<IndexEntry>()
    private val json = Json { prettyPrint = true }

    /**
     * Индексирует чанки документов
     */
    fun indexChunks(chunks: List<DocumentChunker.DocumentChunk>) {
        if (chunks.isEmpty()) return

        // Обучаем векторайзер на всех чанках
        println("🔧 Обучение векторайзера на ${chunks.size} чанках...")
        embeddings.fit(chunks.map { it.content })

        // Создаем эмбеддинги для каждого чанка
        println("📊 Генерация эмбеддингов...")
        chunks.forEach { chunk ->
            val embedding = embeddings.embed(chunk.content)
            index.add(
                IndexEntry(
                    id = chunk.id,
                    source = chunk.source,
                    content = chunk.content,
                    embedding = embedding.toList()
                )
            )
        }

        println("✅ Проиндексировано ${index.size} чанков, размерность векторов: ${embeddings.vectorDimension}")
    }

    /**
     * Поиск по запросу
     *
     * @param query Поисковый запрос
     * @param topK Количество результатов (по умолчанию 5)
     * @param minRelevance Минимальный порог релевантности 0.0-1.0 (опционально)
     * @return Список результатов, отсортированных по релевантности
     */
    fun search(query: String, topK: Int = 5, minRelevance: Float? = null): List<SearchResult> {
        if (index.isEmpty()) {
            return emptyList()
        }

        // Генерируем эмбеддинг для запроса
        val queryEmbedding = embeddings.embed(query)

        // Вычисляем сходство со всеми документами
        val results = index.map { entry ->
            val score = embeddings.cosineSimilarity(
                queryEmbedding,
                entry.embedding.toFloatArray()
            )

            SearchResult(
                id = entry.id,
                source = entry.source,
                content = entry.content,
                score = score
            )
        }

        // Фильтруем по минимальной релевантности (если указан порог)
        val filtered = if (minRelevance != null && minRelevance > 0.0f) {
            results.filter { it.score >= minRelevance }
        } else {
            results
        }

        // Возвращаем топ-K наиболее похожих
        return filtered
            .sortedByDescending { it.score }
            .take(topK)
    }

    /**
     * Сохраняет индекс в JSON файл
     */
    fun save(file: File) {
        val savedIndex = SavedIndex(
            entries = index,
            vectorDimension = embeddings.vectorDimension,
            totalDocuments = index.size
        )

        file.writeText(json.encodeToString(savedIndex))
        println("💾 Индекс сохранен в ${file.absolutePath}")
        println("   Размер файла: ${file.length() / 1024} KB")
    }

    /**
     * Загружает индекс из JSON файла
     */
    fun load(file: File) {
        val savedIndex = json.decodeFromString<SavedIndex>(file.readText())

        index.clear()
        index.addAll(savedIndex.entries)

        println("📂 Индекс загружен из ${file.absolutePath}")
        println("   Документов: ${savedIndex.totalDocuments}")
        println("   Размерность: ${savedIndex.vectorDimension}")
        println("   Создан: ${java.time.Instant.ofEpochMilli(savedIndex.createdAt)}")
    }

    /**
     * Размер индекса
     */
    fun size(): Int = index.size

    /**
     * Очищает индекс
     */
    fun clear() {
        index.clear()
    }
}
