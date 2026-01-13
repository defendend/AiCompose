package org.example.tools.core

import org.example.model.Tool
import org.example.tools.devassistant.code.CodeSearchTool
import org.example.tools.devassistant.code.FileReadTool
import org.example.tools.devassistant.code.ProjectDependenciesTool
import org.example.tools.devassistant.code.ProjectStructureTool
import org.example.tools.devassistant.ide.*
import org.example.tools.devassistant.docs.DocsIndexTool
import org.example.tools.devassistant.docs.DocsQueryTool
import org.example.tools.devassistant.docs.DocsSearchTool
import org.example.tools.devassistant.git.*
import org.example.tools.docker.*
import org.example.tools.historical.CompareErasTool
import org.example.tools.historical.HistoricalEventsTool
import org.example.tools.historical.HistoricalFigureTool
import org.example.tools.historical.HistoricalQuoteTool
import org.example.tools.pipeline.PipelineSearchDocs
import org.example.tools.pipeline.PipelineSummarize
import org.example.tools.pipeline.PipelineSaveToFile
import org.example.tools.rag.*
import org.example.tools.system.CurrentTimeTool
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Реестр всех доступных инструментов агента.
 *
 * Поддерживает два способа регистрации:
 * 1. Автоматическая регистрация встроенных инструментов при инициализации
 * 2. Ручная регистрация — через метод register()
 *
 * Использование:
 * ```kotlin
 * // Ручная регистрация
 * ToolRegistry.register(MyTool)
 *
 * // Получение всех инструментов
 * val tools = ToolRegistry.getAllTools()
 *
 * // Выполнение инструмента
 * val result = ToolRegistry.executeTool("my_tool", """{"arg": "value"}""")
 * ```
 */
object ToolRegistry {
    private val logger = LoggerFactory.getLogger(ToolRegistry::class.java)
    private val tools = ConcurrentHashMap<String, AgentTool>()
    private var initialized = false

    /**
     * Инициализирует реестр, регистрируя встроенные инструменты.
     * Вызывается автоматически при первом обращении.
     */
    @Synchronized
    fun initialize() {
        if (initialized) return

        logger.info("Initializing ToolRegistry...")

        // Регистрируем встроенные инструменты
        registerBuiltInTools()

        initialized = true
        logger.info("ToolRegistry initialized with ${tools.size} tools: ${tools.keys}")
    }

    /**
     * Регистрирует встроенные инструменты.
     */
    private fun registerBuiltInTools() {
        // Исторические инструменты
        registerInternal(HistoricalEventsTool, source = "built-in")
        registerInternal(HistoricalFigureTool, source = "built-in")
        registerInternal(CompareErasTool, source = "built-in")
        registerInternal(HistoricalQuoteTool, source = "built-in")

        // Pipeline инструменты (композиция)
        registerInternal(PipelineSearchDocs, source = "built-in")
        registerInternal(PipelineSummarize, source = "built-in")
        registerInternal(PipelineSaveToFile, source = "built-in")

        // Docker инструменты (управление окружением)
        registerInternal(DockerRunTool, source = "built-in")
        registerInternal(DockerExecTool, source = "built-in")
        registerInternal(DockerLogsTool, source = "built-in")
        registerInternal(DockerStopTool, source = "built-in")
        registerInternal(DockerPsTool, source = "built-in")

        // RAG инструменты (векторный поиск по документам)
        registerInternal(RagIndexDocuments, source = "built-in")
        registerInternal(RagSearch, source = "built-in")
        registerInternal(RagIndexInfo, source = "built-in")
        registerInternal(org.example.tools.rag.AskWithRagTool, source = "built-in")
        registerInternal(org.example.tools.rag.CompareRagAnswersTool, source = "built-in")
        registerInternal(org.example.tools.rag.CompareRagWithRerankingTool, source = "built-in")

        // Системные инструменты
        registerInternal(CurrentTimeTool, source = "built-in")

        // DevAssistant - Git инструменты
        registerInternal(GitStatusTool, source = "built-in")
        registerInternal(GitBranchTool, source = "built-in")
        registerInternal(GitLogTool, source = "built-in")
        registerInternal(GitDiffTool, source = "built-in")
        registerInternal(GitFilesTool, source = "built-in")

        // DevAssistant - Docs RAG инструменты
        registerInternal(DocsIndexTool, source = "built-in")
        registerInternal(DocsSearchTool, source = "built-in")
        registerInternal(DocsQueryTool, source = "built-in")

        // DevAssistant - Code инструменты
        registerInternal(CodeSearchTool, source = "built-in")
        registerInternal(FileReadTool, source = "built-in")
        registerInternal(ProjectStructureTool, source = "built-in")
        registerInternal(ProjectDependenciesTool, source = "built-in")

        // DevAssistant - IDE интеграция
        registerInternal(IdeSetContextTool, source = "built-in")
        registerInternal(IdeGetContextTool, source = "built-in")
        registerInternal(IdeReadCurrentTool, source = "built-in")
        registerInternal(IdeClearContextTool, source = "built-in")

        // DevAssistant - GitHub инструменты
        registerInternal(org.example.tools.devassistant.github.GithubPrInfoTool, source = "built-in")
        registerInternal(org.example.tools.devassistant.github.GithubPrDiffTool, source = "built-in")
        registerInternal(org.example.tools.devassistant.github.GithubPrFilesTool, source = "built-in")
        registerInternal(org.example.tools.devassistant.github.GithubPostReviewTool, source = "built-in")
    }

    /**
     * Регистрирует инструмент вручную.
     * Полезно для инструментов, которые не используют ServiceLoader.
     *
     * @param tool Инструмент для регистрации
     * @return true если инструмент успешно зарегистрирован
     */
    fun register(tool: AgentTool): Boolean {
        ensureInitialized()
        return registerInternal(tool, source = "manual")
    }

    /**
     * Регистрирует несколько инструментов.
     */
    fun registerAll(vararg tools: AgentTool) {
        ensureInitialized()
        tools.forEach { register(it) }
    }

    private fun registerInternal(tool: AgentTool, source: String): Boolean {
        val existing = tools.putIfAbsent(tool.name, tool)
        return if (existing == null) {
            logger.debug("Registered tool '${tool.name}' from $source")
            true
        } else {
            logger.warn("Tool '${tool.name}' already registered, skipping from $source")
            false
        }
    }

    /**
     * Снимает регистрацию инструмента.
     */
    fun unregister(name: String): AgentTool? {
        ensureInitialized()
        return tools.remove(name)?.also {
            logger.debug("Unregistered tool '$name'")
        }
    }

    /**
     * Возвращает список определений всех зарегистрированных инструментов.
     * Используется для отправки в LLM API.
     */
    fun getAllTools(): List<Tool> {
        ensureInitialized()
        return tools.values.map { it.getDefinition() }
    }

    /**
     * Возвращает инструмент по имени.
     */
    fun getTool(name: String): AgentTool? {
        ensureInitialized()
        return tools[name]
    }

    /**
     * Проверяет, зарегистрирован ли инструмент.
     */
    fun hasTool(name: String): Boolean {
        ensureInitialized()
        return tools.containsKey(name)
    }

    /**
     * Возвращает количество зарегистрированных инструментов.
     */
    fun size(): Int {
        ensureInitialized()
        return tools.size
    }

    /**
     * Возвращает имена всех зарегистрированных инструментов.
     */
    fun getToolNames(): Set<String> {
        ensureInitialized()
        return tools.keys.toSet()
    }

    /**
     * Выполняет инструмент по имени с заданными аргументами.
     *
     * @param name Имя инструмента
     * @param arguments JSON-строка с аргументами
     * @return Результат выполнения или сообщение об ошибке
     */
    suspend fun executeTool(name: String, arguments: String): String {
        ensureInitialized()
        val tool = getTool(name) ?: return "Ошибка: инструмент '$name' не найден"
        return try {
            tool.execute(arguments)
        } catch (e: Exception) {
            logger.error("Error executing tool '$name': ${e.message}", e)
            "Ошибка выполнения инструмента '$name': ${e.message}"
        }
    }

    /**
     * Сбрасывает реестр. Полезно для тестов.
     */
    @Synchronized
    fun reset() {
        tools.clear()
        initialized = false
        logger.debug("ToolRegistry reset")
    }

    private fun ensureInitialized() {
        if (!initialized) {
            initialize()
        }
    }
}
