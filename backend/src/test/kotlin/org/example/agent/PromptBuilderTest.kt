package org.example.agent

import org.example.shared.model.CollectionMode
import org.example.shared.model.CollectionSettings
import org.example.shared.model.ResponseFormat
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptBuilderTest {
    private val builder = PromptBuilder()

    // === Тесты базового промпта ===

    @Test
    fun `buildSystemPrompt uses default Archivarius prompt when no custom prompt`() {
        val prompt = builder.buildSystemPrompt(ResponseFormat.PLAIN)

        assertContains(prompt, "профессор Архивариус")
        assertContains(prompt, "историк")
    }

    @Test
    fun `buildSystemPrompt uses custom system prompt when provided`() {
        val settings = CollectionSettings(
            customSystemPrompt = "Ты пират Джек, морской волк"
        )

        val prompt = builder.buildSystemPrompt(ResponseFormat.PLAIN, settings)

        assertContains(prompt, "пират Джек")
        assertFalse(prompt.contains("профессор Архивариус"))
    }

    @Test
    fun `buildSystemPrompt ignores blank custom prompt`() {
        val settings = CollectionSettings(
            customSystemPrompt = "   "
        )

        val prompt = builder.buildSystemPrompt(ResponseFormat.PLAIN, settings)

        assertContains(prompt, "профессор Архивариус")
    }

    // === Тесты форматов ответа ===

    @Test
    fun `buildSystemPrompt includes PLAIN format instruction`() {
        val prompt = builder.buildSystemPrompt(ResponseFormat.PLAIN)

        assertContains(prompt, "обычный текст")
    }

    @Test
    fun `buildSystemPrompt includes JSON format instruction`() {
        val prompt = builder.buildSystemPrompt(ResponseFormat.JSON)

        assertContains(prompt, "JSON")
        assertContains(prompt, "topic")
        assertContains(prompt, "summary")
    }

    @Test
    fun `buildSystemPrompt includes MARKDOWN format instruction`() {
        val prompt = builder.buildSystemPrompt(ResponseFormat.MARKDOWN)

        assertContains(prompt, "Markdown")
        assertContains(prompt, "##")
    }

    // === Тесты режимов сбора данных ===

    @Test
    fun `buildSystemPrompt returns no collection instruction when settings null`() {
        val prompt = builder.buildSystemPrompt(ResponseFormat.PLAIN, null)

        assertFalse(prompt.contains("РЕЖИМ СБОРА ДАННЫХ"))
        assertFalse(prompt.contains("РЕЖИМ:"))
    }

    @Test
    fun `buildSystemPrompt returns no collection instruction when not enabled`() {
        val settings = CollectionSettings(
            mode = CollectionMode.TECHNICAL_SPEC,
            enabled = false
        )

        val prompt = builder.buildSystemPrompt(ResponseFormat.PLAIN, settings)

        assertFalse(prompt.contains("РЕЖИМ СБОРА ДАННЫХ"))
    }

    @Test
    fun `buildSystemPrompt returns no collection instruction for NONE mode`() {
        val settings = CollectionSettings(
            mode = CollectionMode.NONE,
            enabled = true
        )

        val prompt = builder.buildSystemPrompt(ResponseFormat.PLAIN, settings)

        assertFalse(prompt.contains("РЕЖИМ СБОРА ДАННЫХ"))
    }

    @Test
    fun `buildSystemPrompt includes TECHNICAL_SPEC instruction`() {
        val settings = CollectionSettings(
            mode = CollectionMode.TECHNICAL_SPEC,
            resultTitle = "Техническое задание",
            enabled = true
        )

        val prompt = builder.buildSystemPrompt(ResponseFormat.PLAIN, settings)

        assertContains(prompt, "РЕЖИМ СБОРА ДАННЫХ")
        assertContains(prompt, "Техническое задание")
        assertContains(prompt, "Цель проекта")
        assertContains(prompt, "Функциональные требования")
    }

    @Test
    fun `buildSystemPrompt includes DESIGN_BRIEF instruction`() {
        val settings = CollectionSettings(
            mode = CollectionMode.DESIGN_BRIEF,
            resultTitle = "Дизайн-бриф",
            enabled = true
        )

        val prompt = builder.buildSystemPrompt(ResponseFormat.PLAIN, settings)

        assertContains(prompt, "РЕЖИМ СБОРА ДАННЫХ")
        assertContains(prompt, "Дизайн-бриф")
        assertContains(prompt, "Целевая аудитория")
        assertContains(prompt, "Референсы")
    }

    @Test
    fun `buildSystemPrompt includes PROJECT_SUMMARY instruction`() {
        val settings = CollectionSettings(
            mode = CollectionMode.PROJECT_SUMMARY,
            resultTitle = "Резюме проекта",
            enabled = true
        )

        val prompt = builder.buildSystemPrompt(ResponseFormat.PLAIN, settings)

        assertContains(prompt, "РЕЖИМ СБОРА ДАННЫХ")
        assertContains(prompt, "Резюме проекта")
        assertContains(prompt, "Бизнес-модель")
    }

    @Test
    fun `buildSystemPrompt includes CUSTOM instruction with custom prompt`() {
        val settings = CollectionSettings(
            mode = CollectionMode.CUSTOM,
            customPrompt = "Собери информацию о любимых книгах",
            resultTitle = "Книжный список",
            enabled = true
        )

        val prompt = builder.buildSystemPrompt(ResponseFormat.PLAIN, settings)

        assertContains(prompt, "РЕЖИМ СБОРА ДАННЫХ")
        assertContains(prompt, "Собери информацию о любимых книгах")
        assertContains(prompt, "Книжный список")
    }

    // === Тесты режимов решения задач ===

    @Test
    fun `buildSystemPrompt includes SOLVE_DIRECT instruction`() {
        val settings = CollectionSettings(
            mode = CollectionMode.SOLVE_DIRECT,
            enabled = true
        )

        val prompt = builder.buildSystemPrompt(ResponseFormat.PLAIN, settings)

        assertContains(prompt, "РЕЖИМ: ПРЯМОЙ ОТВЕТ")
        assertContains(prompt, "НАПРЯМУЮ")
        assertContains(prompt, "без объяснений")
    }

    @Test
    fun `buildSystemPrompt includes SOLVE_STEP_BY_STEP instruction`() {
        val settings = CollectionSettings(
            mode = CollectionMode.SOLVE_STEP_BY_STEP,
            enabled = true
        )

        val prompt = builder.buildSystemPrompt(ResponseFormat.PLAIN, settings)

        assertContains(prompt, "РЕЖИМ: ПОШАГОВОЕ РЕШЕНИЕ")
        assertContains(prompt, "ШАГ ЗА ШАГОМ")
        assertContains(prompt, "Анализ задачи")
    }

    @Test
    fun `buildSystemPrompt includes SOLVE_EXPERT_PANEL instruction`() {
        val settings = CollectionSettings(
            mode = CollectionMode.SOLVE_EXPERT_PANEL,
            enabled = true
        )

        val prompt = builder.buildSystemPrompt(ResponseFormat.PLAIN, settings)

        assertContains(prompt, "РЕЖИМ: ГРУППА ЭКСПЕРТОВ")
        assertContains(prompt, "Эксперт-Логик")
        assertContains(prompt, "Эксперт-Практик")
        assertContains(prompt, "Эксперт-Критик")
    }

    // === Тесты комбинаций ===

    @Test
    fun `buildSystemPrompt combines custom prompt with format and collection mode`() {
        val settings = CollectionSettings(
            mode = CollectionMode.SOLVE_STEP_BY_STEP,
            customSystemPrompt = "Ты мудрый Йода",
            enabled = true
        )

        val prompt = builder.buildSystemPrompt(ResponseFormat.MARKDOWN, settings)

        assertContains(prompt, "мудрый Йода")
        assertContains(prompt, "Markdown")
        assertContains(prompt, "ПОШАГОВОЕ РЕШЕНИЕ")
        assertFalse(prompt.contains("профессор Архивариус"))
    }

    @Test
    fun `DEFAULT_SYSTEM_PROMPT contains tool descriptions`() {
        val prompt = PromptBuilder.DEFAULT_SYSTEM_PROMPT

        assertContains(prompt, "get_historical_events")
        assertContains(prompt, "get_historical_figure")
        assertContains(prompt, "compare_eras")
        assertContains(prompt, "get_historical_quote")
    }
}
