package org.example.di

import org.example.agent.Agent
import org.example.agent.PromptBuilder
import org.example.agent.ToolExecutor
import org.example.data.ConversationRepository
import org.example.data.DeepSeekClient
import org.example.data.InMemoryConversationRepository
import org.example.data.LLMClient
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Модуль DI для backend приложения.
 * Определяет все зависимости и их связи.
 */
fun appModule(apiKey: String) = module {
    // Data layer
    single<LLMClient> { DeepSeekClient(apiKey) }
    single<ConversationRepository> { InMemoryConversationRepository() }

    // Agent layer
    singleOf(::PromptBuilder)
    singleOf(::ToolExecutor)
    single { Agent(get(), get(), get(), get()) }
}
