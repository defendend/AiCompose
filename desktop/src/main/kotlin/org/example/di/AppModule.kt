package org.example.di

import org.example.network.ChatApiClient
import org.example.ui.ChatViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Модуль DI для desktop приложения.
 */
val appModule = module {
    // Network layer
    singleOf(::ChatApiClient)

    // UI layer
    single { ChatViewModel(get()) }
}
