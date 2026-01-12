package org.example.tools.devassistant.code

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.tools.annotations.Param
import org.example.tools.annotations.Tool
import org.example.tools.core.AnnotatedAgentTool
import java.io.File

/**
 * Инструмент для анализа зависимостей проекта.
 * Парсит build.gradle.kts и build.gradle файлы.
 */
@Tool(
    name = "project_dependencies",
    description = "Анализирует зависимости проекта из build.gradle файлов. Показывает dependencies, plugins, версии."
)
@Param(name = "path", description = "Путь к директории проекта (по умолчанию текущая)", type = "string", required = false)
@Param(name = "module", description = "Конкретный модуль для анализа (например: backend, desktop)", type = "string", required = false)
@Param(name = "show_versions", description = "Показывать версии зависимостей (true/false)", type = "boolean", required = false)
object ProjectDependenciesTool : AnnotatedAgentTool() {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(arguments: String): String {
        val args = if (arguments.isBlank()) {
            emptyMap()
        } else {
            try {
                json.parseToJsonElement(arguments).jsonObject.mapValues {
                    it.value.jsonPrimitive.content
                }
            } catch (e: Exception) {
                emptyMap()
            }
        }

        val projectPath = args["path"] ?: System.getenv("PROJECT_PATH") ?: "."
        val module = args["module"]
        val showVersions = args["show_versions"]?.toBoolean() ?: true

        val baseDir = File(projectPath).absoluteFile
        if (!baseDir.exists()) {
            return "❌ Директория не существует: ${baseDir.absolutePath}"
        }

        return try {
            analyzeProject(baseDir, module, showVersions)
        } catch (e: Exception) {
            "❌ Ошибка анализа: ${e.message}"
        }
    }

    private fun analyzeProject(baseDir: File, targetModule: String?, showVersions: Boolean): String {
        val result = StringBuilder()

        // Ищем версии в gradle.properties или version catalogs
        val versions = mutableMapOf<String, String>()
        loadVersions(baseDir, versions)

        // Ищем build.gradle файлы
        val buildFiles = findBuildFiles(baseDir, targetModule)

        if (buildFiles.isEmpty()) {
            return "❌ Build файлы не найдены в ${baseDir.absolutePath}"
        }

        result.appendLine("# 📦 Анализ зависимостей проекта")
        result.appendLine()

        // Анализируем каждый модуль
        buildFiles.forEach { (moduleName, buildFile) ->
            val analysis = analyzeBuildFile(buildFile, versions, showVersions)

            result.appendLine("## 📁 $moduleName")
            result.appendLine()

            if (analysis.plugins.isNotEmpty()) {
                result.appendLine("### Plugins")
                analysis.plugins.forEach { plugin ->
                    result.appendLine("  - $plugin")
                }
                result.appendLine()
            }

            if (analysis.dependencies.isNotEmpty()) {
                result.appendLine("### Dependencies")
                analysis.dependencies.groupBy { it.configuration }.forEach { (config, deps) ->
                    result.appendLine("**$config:**")
                    deps.forEach { dep ->
                        result.appendLine("  - ${dep.format(showVersions)}")
                    }
                }
                result.appendLine()
            }

            if (analysis.kotlinOptions.isNotEmpty()) {
                result.appendLine("### Kotlin Options")
                analysis.kotlinOptions.forEach { (key, value) ->
                    result.appendLine("  - $key: $value")
                }
                result.appendLine()
            }
        }

        // Статистика
        val totalDeps = buildFiles.values.sumOf {
            analyzeBuildFile(it, versions, false).dependencies.size
        }
        result.appendLine("---")
        result.appendLine("📊 Всего модулей: ${buildFiles.size}")
        result.appendLine("📊 Всего зависимостей: $totalDeps")

        return result.toString()
    }

    private fun findBuildFiles(baseDir: File, targetModule: String?): Map<String, File> {
        val result = mutableMapOf<String, File>()

        // Корневой build файл
        val rootBuildKts = File(baseDir, "build.gradle.kts")
        val rootBuild = File(baseDir, "build.gradle")

        if (targetModule == null) {
            if (rootBuildKts.exists()) {
                result["root"] = rootBuildKts
            } else if (rootBuild.exists()) {
                result["root"] = rootBuild
            }
        }

        // Ищем модули
        val settingsFile = File(baseDir, "settings.gradle.kts").takeIf { it.exists() }
            ?: File(baseDir, "settings.gradle").takeIf { it.exists() }

        val modules = settingsFile?.let { extractModules(it) } ?: emptyList()

        modules.forEach { moduleName ->
            if (targetModule != null && !moduleName.contains(targetModule, ignoreCase = true)) {
                return@forEach
            }

            val modulePath = moduleName.replace(":", "/")
            val moduleDir = File(baseDir, modulePath)

            val moduleBuildKts = File(moduleDir, "build.gradle.kts")
            val moduleBuild = File(moduleDir, "build.gradle")

            when {
                moduleBuildKts.exists() -> result[moduleName] = moduleBuildKts
                moduleBuild.exists() -> result[moduleName] = moduleBuild
            }
        }

        // Если модули не найдены через settings, ищем в типичных директориях
        if (result.isEmpty() || (result.size == 1 && result.containsKey("root"))) {
            listOf("backend", "desktop", "shared", "app", "core", "common").forEach { dir ->
                if (targetModule != null && !dir.contains(targetModule, ignoreCase = true)) {
                    return@forEach
                }

                val moduleDir = File(baseDir, dir)
                val moduleBuildKts = File(moduleDir, "build.gradle.kts")
                val moduleBuild = File(moduleDir, "build.gradle")

                when {
                    moduleBuildKts.exists() -> result[dir] = moduleBuildKts
                    moduleBuild.exists() -> result[dir] = moduleBuild
                }
            }
        }

        return result
    }

    private fun extractModules(settingsFile: File): List<String> {
        val content = settingsFile.readText()
        val modules = mutableListOf<String>()

        // include(":module")
        val includeRegex = """include\s*\(\s*["']([^"']+)["']\s*\)""".toRegex()
        includeRegex.findAll(content).forEach { match ->
            modules.add(match.groupValues[1])
        }

        // include ":module" (Groovy)
        val includeGroovyRegex = """include\s+["']([^"']+)["']""".toRegex()
        includeGroovyRegex.findAll(content).forEach { match ->
            modules.add(match.groupValues[1])
        }

        return modules.distinct()
    }

    private fun loadVersions(baseDir: File, versions: MutableMap<String, String>) {
        // gradle.properties
        val gradleProps = File(baseDir, "gradle.properties")
        if (gradleProps.exists()) {
            gradleProps.readLines()
                .filter { it.contains("=") && !it.startsWith("#") }
                .forEach { line ->
                    val (key, value) = line.split("=", limit = 2)
                    versions[key.trim()] = value.trim()
                }
        }

        // libs.versions.toml (version catalog)
        val versionsCatalog = File(baseDir, "gradle/libs.versions.toml")
        if (versionsCatalog.exists()) {
            parseVersionsCatalog(versionsCatalog, versions)
        }
    }

    private fun parseVersionsCatalog(file: File, versions: MutableMap<String, String>) {
        var inVersionsSection = false
        file.readLines().forEach { line ->
            when {
                line.trim() == "[versions]" -> inVersionsSection = true
                line.trim().startsWith("[") -> inVersionsSection = false
                inVersionsSection && line.contains("=") -> {
                    val (key, value) = line.split("=", limit = 2)
                    versions["libs.versions.${key.trim()}"] = value.trim().removeSurrounding("\"")
                }
            }
        }
    }

    private fun analyzeBuildFile(file: File, versions: Map<String, String>, showVersions: Boolean): BuildAnalysis {
        val content = file.readText()
        val isKts = file.extension == "kts"

        val plugins = extractPlugins(content, isKts)
        val dependencies = extractDependencies(content, isKts, versions)
        val kotlinOptions = extractKotlinOptions(content)

        return BuildAnalysis(plugins, dependencies, kotlinOptions)
    }

    private fun extractPlugins(content: String, isKts: Boolean): List<String> {
        val plugins = mutableListOf<String>()

        // plugins { ... }
        val pluginsBlock = """plugins\s*\{([^}]+)\}""".toRegex(RegexOption.DOT_MATCHES_ALL)
        pluginsBlock.find(content)?.groupValues?.get(1)?.let { block ->
            // id("plugin") or id "plugin"
            val idRegex = if (isKts) {
                """(?:id|kotlin|java|application|alias)\s*\(\s*["']([^"']+)["']\s*\)""".toRegex()
            } else {
                """(?:id|kotlin|java|application)\s+["']([^"']+)["']""".toRegex()
            }
            idRegex.findAll(block).forEach { match ->
                plugins.add(match.groupValues[1])
            }

            // libs.plugins.xxx
            """alias\s*\(\s*libs\.plugins\.([^)]+)\s*\)""".toRegex().findAll(block).forEach { match ->
                plugins.add("libs.plugins.${match.groupValues[1]}")
            }
        }

        return plugins.distinct()
    }

    private fun extractDependencies(content: String, isKts: Boolean, versions: Map<String, String>): List<Dependency> {
        val dependencies = mutableListOf<Dependency>()

        // dependencies { ... }
        val depsBlock = """dependencies\s*\{([^}]+(?:\{[^}]*\}[^}]*)*)\}""".toRegex(RegexOption.DOT_MATCHES_ALL)
        depsBlock.find(content)?.groupValues?.get(1)?.let { block ->
            // configuration("group:artifact:version")
            val depRegex = """(implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|kapt|ksp|annotationProcessor)\s*\(\s*["']([^"']+)["']\s*\)""".toRegex()
            depRegex.findAll(block).forEach { match ->
                val config = match.groupValues[1]
                val coords = match.groupValues[2]
                dependencies.add(parseDependency(config, coords, versions))
            }

            // configuration(libs.xxx)
            val libsRegex = """(implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|kapt|ksp)\s*\(\s*libs\.([^)]+)\s*\)""".toRegex()
            libsRegex.findAll(block).forEach { match ->
                val config = match.groupValues[1]
                val libRef = "libs.${match.groupValues[2]}"
                dependencies.add(Dependency(config, libRef, "", ""))
            }

            // project(":module")
            val projectRegex = """(implementation|api)\s*\(\s*project\s*\(\s*["']([^"']+)["']\s*\)\s*\)""".toRegex()
            projectRegex.findAll(block).forEach { match ->
                dependencies.add(Dependency(match.groupValues[1], "project", match.groupValues[2], ""))
            }
        }

        return dependencies
    }

    private fun parseDependency(config: String, coords: String, versions: Map<String, String>): Dependency {
        val parts = coords.split(":")
        return when (parts.size) {
            3 -> {
                var version = parts[2]
                // Замена переменных $xxx
                if (version.startsWith("\$")) {
                    val varName = version.removePrefix("\$").removeSurrounding("{", "}")
                    version = versions[varName] ?: version
                }
                Dependency(config, parts[0], parts[1], version)
            }
            2 -> Dependency(config, parts[0], parts[1], "")
            else -> Dependency(config, coords, "", "")
        }
    }

    private fun extractKotlinOptions(content: String): Map<String, String> {
        val options = mutableMapOf<String, String>()

        // jvmTarget
        """jvmTarget\s*=\s*["']([^"']+)["']""".toRegex().find(content)?.let {
            options["jvmTarget"] = it.groupValues[1]
        }

        // languageVersion
        """languageVersion\s*=\s*["']([^"']+)["']""".toRegex().find(content)?.let {
            options["languageVersion"] = it.groupValues[1]
        }

        // kotlin version
        """kotlin\s*\(\s*["']jvm["']\s*\)\s*version\s+["']([^"']+)["']""".toRegex().find(content)?.let {
            options["kotlinVersion"] = it.groupValues[1]
        }

        return options
    }

    data class BuildAnalysis(
        val plugins: List<String>,
        val dependencies: List<Dependency>,
        val kotlinOptions: Map<String, String>
    )

    data class Dependency(
        val configuration: String,
        val group: String,
        val artifact: String,
        val version: String
    ) {
        fun format(showVersion: Boolean): String {
            return if (group == "project") {
                "project($artifact)"
            } else if (artifact.isBlank()) {
                group  // libs.xxx reference
            } else if (showVersion && version.isNotBlank()) {
                "$group:$artifact:$version"
            } else {
                "$group:$artifact"
            }
        }
    }
}
