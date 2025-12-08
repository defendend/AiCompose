package org.example.di

import org.example.agent.Agent
import org.example.agent.PromptBuilder
import org.example.agent.ToolExecutor
import org.example.data.ConversationRepository
import org.example.data.DeepSeekClient
import org.example.data.InMemoryConversationRepository
import org.example.data.LLMClient
import org.example.data.RedisConversationRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AppModule")

/**
 * Конфигурация для репозитория диалогов.
 *
 * @param useRedis использовать Redis вместо In-Memory
 * @param redisUrl URL подключения к Redis
 * @param redisTtlSeconds TTL для диалогов в секундах
 */
data class RepositoryConfig(
    val useRedis: Boolean = false,
    val redisUrl: String = "redis://localhost:6379",
    val redisTtlSeconds: Long = 86400 // 24 часа
) {
    companion object {
        /**
         * Создаёт конфигурацию из переменных окружения:
         * - REDIS_ENABLED=true — включить Redis
         * - REDIS_URL=redis://host:port — URL Redis
         * - REDIS_TTL_HOURS=24 — TTL диалогов в часах
         */
        fun fromEnv(): RepositoryConfig {
            val useRedis = System.getenv("REDIS_ENABLED")?.toBoolean() ?: false
            val redisUrl = System.getenv("REDIS_URL") ?: "redis://localhost:6379"
            val ttlHours = System.getenv("REDIS_TTL_HOURS")?.toLongOrNull() ?: 24
            return RepositoryConfig(
                useRedis = useRedis,
                redisUrl = redisUrl,
                redisTtlSeconds = ttlHours * 3600
            )
        }
    }
}

/**
 * Модуль DI для backend приложения.
 * Определяет все зависимости и их связи.
 *
 * @param apiKey API ключ для DeepSeek
 * @param repositoryConfig конфигурация репозитория (In-Memory или Redis)
 */
fun appModule(
    apiKey: String,
    repositoryConfig: RepositoryConfig = RepositoryConfig.fromEnv()
) = module {
    // Data layer
    single<LLMClient> { DeepSeekClient(apiKey) }

    single<ConversationRepository> {
        if (repositoryConfig.useRedis) {
            logger.info("Using Redis conversation repository: ${repositoryConfig.redisUrl}")
            RedisConversationRepository(
                redisUrl = repositoryConfig.redisUrl,
                ttlSeconds = repositoryConfig.redisTtlSeconds
            )
        } else {
            logger.info("Using In-Memory conversation repository")
            InMemoryConversationRepository()
        }
    }

    // Agent layer
    singleOf(::PromptBuilder)
    singleOf(::ToolExecutor)
    single { Agent(get(), get(), get(), get()) }
}
