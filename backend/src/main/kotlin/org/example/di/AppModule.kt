package org.example.di

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.example.agent.Agent
import org.example.agent.PromptBuilder
import org.example.agent.ToolExecutor
import org.example.data.ConversationRepository
import org.example.data.DeepSeekClient
import org.example.data.InMemoryConversationRepository
import org.example.data.LLMClient
import org.example.data.PostgresConversationRepository
import org.example.data.RedisConversationRepository
import org.jetbrains.exposed.sql.Database
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AppModule")

/**
 * Тип хранилища данных.
 */
enum class StorageType {
    MEMORY,
    REDIS,
    POSTGRES
}

/**
 * Конфигурация для репозитория диалогов.
 *
 * @param storageType тип хранилища (memory, redis, postgres)
 * @param redisUrl URL подключения к Redis
 * @param redisTtlSeconds TTL для диалогов в секундах
 * @param dbUrl JDBC URL для PostgreSQL
 * @param dbUser пользователь БД
 * @param dbPassword пароль БД
 * @param dbPoolSize размер пула соединений
 */
data class RepositoryConfig(
    val storageType: StorageType = StorageType.MEMORY,
    val redisUrl: String = "redis://localhost:6379",
    val redisTtlSeconds: Long = 86400, // 24 часа
    val dbUrl: String = "jdbc:postgresql://localhost:5432/aicompose",
    val dbUser: String = "postgres",
    val dbPassword: String = "",
    val dbPoolSize: Int = 10
) {
    // Для обратной совместимости
    val useRedis: Boolean get() = storageType == StorageType.REDIS

    companion object {
        /**
         * Создаёт конфигурацию из переменных окружения:
         * - STORAGE_TYPE=memory|redis|postgres — тип хранилища
         * - REDIS_ENABLED=true — включить Redis (для обратной совместимости)
         * - REDIS_URL=redis://host:port — URL Redis
         * - REDIS_TTL_HOURS=24 — TTL диалогов в часах
         * - DB_URL=jdbc:postgresql://host:port/db — URL PostgreSQL
         * - DB_USER=username — пользователь PostgreSQL
         * - DB_PASSWORD=password — пароль PostgreSQL
         * - DB_POOL_SIZE=10 — размер пула соединений
         */
        fun fromEnv(): RepositoryConfig {
            val storageTypeStr = System.getenv("STORAGE_TYPE")?.lowercase() ?: ""
            val redisEnabled = System.getenv("REDIS_ENABLED")?.toBoolean() ?: false

            val storageType = when {
                storageTypeStr == "postgres" -> StorageType.POSTGRES
                storageTypeStr == "redis" -> StorageType.REDIS
                storageTypeStr == "memory" -> StorageType.MEMORY
                redisEnabled -> StorageType.REDIS  // Обратная совместимость
                else -> StorageType.MEMORY
            }

            val redisUrl = System.getenv("REDIS_URL") ?: "redis://localhost:6379"
            val ttlHours = System.getenv("REDIS_TTL_HOURS")?.toLongOrNull() ?: 24

            val dbUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/aicompose"
            val dbUser = System.getenv("DB_USER") ?: "postgres"
            val dbPassword = System.getenv("DB_PASSWORD") ?: ""
            val dbPoolSize = System.getenv("DB_POOL_SIZE")?.toIntOrNull() ?: 10

            return RepositoryConfig(
                storageType = storageType,
                redisUrl = redisUrl,
                redisTtlSeconds = ttlHours * 3600,
                dbUrl = dbUrl,
                dbUser = dbUser,
                dbPassword = dbPassword,
                dbPoolSize = dbPoolSize
            )
        }
    }
}

/**
 * Создаёт HikariCP DataSource для PostgreSQL.
 */
private fun createDataSource(config: RepositoryConfig): HikariDataSource {
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = config.dbUrl
        username = config.dbUser
        password = config.dbPassword
        maximumPoolSize = config.dbPoolSize
        isAutoCommit = true
        transactionIsolation = "TRANSACTION_READ_COMMITTED"
        poolName = "AiComposePool"

        // Настройки для PostgreSQL
        addDataSourceProperty("cachePrepStmts", "true")
        addDataSourceProperty("prepStmtCacheSize", "250")
        addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
    }
    return HikariDataSource(hikariConfig)
}

/**
 * Модуль DI для backend приложения.
 * Определяет все зависимости и их связи.
 *
 * @param apiKey API ключ для DeepSeek
 * @param repositoryConfig конфигурация репозитория (In-Memory, Redis или PostgreSQL)
 */
fun appModule(
    apiKey: String,
    repositoryConfig: RepositoryConfig = RepositoryConfig.fromEnv()
) = module {
    // Data layer
    single<LLMClient> { DeepSeekClient(apiKey) }

    single<ConversationRepository> {
        when (repositoryConfig.storageType) {
            StorageType.POSTGRES -> {
                logger.info("Using PostgreSQL conversation repository: ${repositoryConfig.dbUrl}")
                val dataSource = createDataSource(repositoryConfig)
                val database = Database.connect(dataSource)
                PostgresConversationRepository(database)
            }
            StorageType.REDIS -> {
                logger.info("Using Redis conversation repository: ${repositoryConfig.redisUrl}")
                RedisConversationRepository(
                    redisUrl = repositoryConfig.redisUrl,
                    ttlSeconds = repositoryConfig.redisTtlSeconds
                )
            }
            StorageType.MEMORY -> {
                logger.info("Using In-Memory conversation repository")
                InMemoryConversationRepository()
            }
        }
    }

    // Agent layer
    singleOf(::PromptBuilder)
    singleOf(::ToolExecutor)
    single { Agent(get(), get(), get(), get()) }
}
