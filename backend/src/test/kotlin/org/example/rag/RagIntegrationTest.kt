package org.example.rag

import kotlinx.coroutines.test.runTest
import org.example.tools.rag.RagIndexDocuments
import org.example.tools.rag.RagIndexInfo
import org.example.tools.rag.RagSearch
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Интеграционный тест RAG системы.
 * Проверяет полный цикл: индексация → информация → поиск
 */
class RagIntegrationTest {

    private val testDocsDir = File("/tmp/rag_test_docs")
    private val indexFile = File("document_index.json")

    @BeforeTest
    fun setUp() {
        // Создаём тестовые документы
        testDocsDir.mkdirs()

        File(testDocsDir, "docker.md").writeText("""
            # Docker Tools

            Docker позволяет управлять контейнерами:
            - docker_run - запуск контейнеров
            - docker_exec - выполнение команд
            - docker_logs - просмотр логов
            - docker_stop - остановка контейнера
            - docker_ps - список контейнеров
        """.trimIndent())

        File(testDocsDir, "rag.md").writeText("""
            # RAG System

            RAG (Retrieval-Augmented Generation):
            - Индексация документов
            - TF-IDF эмбеддинги
            - Семантический поиск
            - Сохранение в JSON
        """.trimIndent())

        File(testDocsDir, "history.md").writeText("""
            # Historical Tools

            Исторические инструменты агента:
            - get_historical_events - события по годам
            - get_historical_figure - биографии
            - compare_eras - сравнение эпох
            - get_historical_quote - цитаты великих
        """.trimIndent())

        // Удаляем старый индекс
        indexFile.delete()
    }

    @AfterTest
    fun tearDown() {
        // Очищаем тестовые файлы
        testDocsDir.deleteRecursively()
        indexFile.delete()
    }

    @Test
    fun `full RAG workflow - index, info, search`() = runTest {
        println("\n🧪 === RAG Integration Test ===\n")

        // 1. Индексация
        println("1️⃣ Индексация документов...")
        val indexResult = RagIndexDocuments.execute("""{"path": "${testDocsDir.absolutePath}"}""")
        println(indexResult)
        println()

        assertContains(indexResult, "Индексация завершена")
        assertContains(indexResult, "docker.md")
        assertContains(indexResult, "rag.md")
        assertContains(indexResult, "history.md")
        assertTrue(indexFile.exists(), "Индекс должен быть сохранён")

        // 2. Информация об индексе
        println("2️⃣ Информация об индексе...")
        val infoResult = RagIndexInfo.execute("{}")
        println(infoResult)
        println()

        assertContains(infoResult, "Статус: Активен ✅")
        assertContains(infoResult, "Документов:")

        // 3. Поиск по Docker
        println("3️⃣ Поиск: 'Docker контейнеры'")
        val searchResult1 = RagSearch.execute("""{"query": "Docker контейнеры", "top_k": 2}""")
        println(searchResult1)
        println()

        assertContains(searchResult1, "docker_run")
        assertContains(searchResult1, "Источник: docker.md")

        // 4. Поиск по RAG
        println("4️⃣ Поиск: 'эмбеддинги векторный поиск'")
        val searchResult2 = RagSearch.execute("""{"query": "эмбеддинги векторный поиск", "top_k": 2}""")
        println(searchResult2)
        println()

        assertContains(searchResult2, "TF-IDF")
        assertContains(searchResult2, "Источник: rag.md")

        // 5. Поиск по истории
        println("5️⃣ Поиск: 'исторические события'")
        val searchResult3 = RagSearch.execute("""{"query": "исторические события", "top_k": 2}""")
        println(searchResult3)
        println()

        assertContains(searchResult3, "historical")

        println("✅ Все тесты пройдены!")
    }

    @Test
    fun `search returns empty for non-existent query`() = runTest {
        // Индексируем
        RagIndexDocuments.execute("""{"path": "${testDocsDir.absolutePath}"}""")

        // Ищем что-то, чего точно нет
        val result = RagSearch.execute("""{"query": "квантовая физика нейронные сети блокчейн", "top_k": 3}""")

        // Должны быть результаты, но с низкой релевантностью
        assertContains(result, "Поиск по запросу")
    }

    @Test
    fun `index non-existent directory returns error`() = runTest {
        val result = RagIndexDocuments.execute("""{"path": "/nonexistent/directory/12345"}""")

        assertContains(result, "не существует")
    }
}
