package org.example.tools.core

import org.example.model.Tool
import org.example.tools.historical.CompareErasTool
import org.example.tools.historical.HistoricalEventsTool
import org.example.tools.historical.HistoricalFigureTool
import org.example.tools.historical.HistoricalQuoteTool
import org.example.tools.pipeline.PipelineSearchDocs
import org.example.tools.pipeline.PipelineSummarize
import org.example.tools.pipeline.PipelineSaveToFile
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

        // Системные инструменты
        registerInternal(CurrentTimeTool, source = "built-in")
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
