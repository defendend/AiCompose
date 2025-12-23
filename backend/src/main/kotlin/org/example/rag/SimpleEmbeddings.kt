package org.example.rag

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Простой генератор эмбеддингов на основе TF-IDF
 * Не требует внешних API, работает локально
 */
class SimpleEmbeddings {

    /**
     * Словарь терминов с индексами
     */
    private val vocabulary = mutableMapOf<String, Int>()

    /**
     * IDF (Inverse Document Frequency) для каждого термина
     */
    private val idf = mutableMapOf<String, Double>()

    /**
     * Размерность векторов
     */
    var vectorDimension: Int = 0
        private set

    /**
     * Обучает векторайзер на наборе документов
     */
    fun fit(documents: List<String>) {
        // Подсчитываем количество документов с каждым термином
        val documentFrequency = mutableMapOf<String, Int>()

        documents.forEach { doc ->
            val terms = tokenize(doc).toSet()
            terms.forEach { term ->
                documentFrequency[term] = documentFrequency.getOrDefault(term, 0) + 1
            }
        }

        // Создаем словарь и вычисляем IDF
        val totalDocs = documents.size.toDouble()
        documentFrequency.entries
            .sortedByDescending { it.value }
            .take(5000) // Ограничиваем словарь топ-5000 терминами
            .forEachIndexed { index, (term, df) ->
                vocabulary[term] = index
                idf[term] = log10(totalDocs / df)
            }

        vectorDimension = vocabulary.size
    }

    /**
     * Преобразует текст в вектор эмбеддингов
     */
    fun embed(text: String): FloatArray {
        val vector = FloatArray(vectorDimension)
        val terms = tokenize(text)

        // Подсчитываем TF (Term Frequency)
        val termCounts = mutableMapOf<String, Int>()
        terms.forEach { term ->
            termCounts[term] = termCounts.getOrDefault(term, 0) + 1
        }

        // Вычисляем TF-IDF
        termCounts.forEach { (term, count) ->
            val index = vocabulary[term]
            if (index != null) {
                val tf = count.toDouble() / terms.size
                val idfValue = idf[term] ?: 0.0
                vector[index] = (tf * idfValue).toFloat()
            }
        }

        // Нормализуем вектор
        return normalize(vector)
    }

    /**
     * Токенизирует текст (простая версия)
     */
    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-zа-яё0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 } // Убираем слишком короткие слова
    }

    /**
     * Нормализует вектор (делает его единичной длины)
     */
    private fun normalize(vector: FloatArray): FloatArray {
        val magnitude = sqrt(vector.map { it * it }.sum())
        return if (magnitude > 0) {
            FloatArray(vector.size) { i -> vector[i] / magnitude }
        } else {
            vector
        }
    }

    /**
     * Вычисляет косинусное сходство между двумя векторами
     */
    fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        require(vec1.size == vec2.size) { "Векторы должны быть одинакового размера" }

        var dotProduct = 0f
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
        }

        return dotProduct // Векторы уже нормализованы
    }
}
