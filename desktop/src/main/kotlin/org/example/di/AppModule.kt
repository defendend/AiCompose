package org.example.di

import org.example.network.ChatApiClient
import org.example.ui.ChatViewModel
import org.example.ui.ConversationListViewModel
import org.koin.dsl.module

/**
 * Модуль DI для desktop приложения.
 */
val appModule = module {
    // Network layer
    single { ChatApiClient() }

    // UI layer
    single { ChatViewModel(get()) }
    single { ConversationListViewModel(get()) }
}
