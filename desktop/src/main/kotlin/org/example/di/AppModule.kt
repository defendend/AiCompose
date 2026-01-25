package org.example.di

import org.example.network.ChatApiClient
import org.example.ui.ChatViewModel
import org.example.ui.ConversationListViewModel
import org.example.analytics.viewmodel.AnalyticsViewModel
import org.example.analytics.agent.DesktopOllamaAnalyticsClient
import org.example.analytics.agent.AnalyticsService
import org.example.analytics.agent.AnalyticsOllamaClient
import org.example.analytics.agent.DataAnalysisAgent
import org.example.analytics.parser.DataParserFactory
import org.koin.dsl.module

/**
 * Модуль DI для desktop приложения.
 */
val appModule = module {
    // Network layer
    single { ChatApiClient() }

    // Analytics layer
    single { DataParserFactory() }
    single { DataAnalysisAgent() }
    single<AnalyticsOllamaClient> { DesktopOllamaAnalyticsClient() }
    single { AnalyticsService(get(), get()) }

    // UI layer
    single { ChatViewModel(get()) }
    single { ConversationListViewModel(get()) }
    single { AnalyticsViewModel() }
}
