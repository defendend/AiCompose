package org.example.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.example.logging.AppLogger
import org.example.network.ChatApiClient
import org.example.shared.model.ConversationExport
import org.example.shared.model.ConversationInfo
import org.example.shared.model.SearchResult

/**
 * ViewModel для управления списком диалогов.
 */
class ConversationListViewModel(
    private val apiClient: ChatApiClient
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Список диалогов
    private val _conversations = MutableStateFlow<List<ConversationInfo>>(emptyList())
    val conversations: StateFlow<List<ConversationInfo>> = _conversations.asStateFlow()

    // Текущий выбранный диалог
    private val _selectedConversationId = MutableStateFlow<String?>(null)
    val selectedConversationId: StateFlow<String?> = _selectedConversationId.asStateFlow()

    // Состояние загрузки
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Ошибка
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Поиск
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    /**
     * Загрузить список диалогов с сервера.
     */
    fun loadConversations() {
        scope.launch {
            _isLoading.value = true
            _error.value = null

            apiClient.getConversations()
                .onSuccess { response ->
                    _conversations.value = response.conversations
                    AppLogger.info("ConversationListViewModel", "Загружено ${response.totalCount} диалогов")
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Ошибка загрузки диалогов"
                    AppLogger.error("ConversationListViewModel", "Ошибка загрузки: ${e.message}")
                }

            _isLoading.value = false
        }
    }

    /**
     * Выбрать диалог.
     */
    fun selectConversation(conversationId: String?) {
        _selectedConversationId.value = conversationId
        AppLogger.info("ConversationListViewModel", "Выбран диалог: $conversationId")
    }

    /**
     * Создать новый диалог.
     */
    fun createNewConversation(title: String? = null, onCreated: (String) -> Unit = {}) {
        scope.launch {
            _isLoading.value = true
            _error.value = null

            apiClient.createConversation(title)
                .onSuccess { info ->
                    // Перезагружаем список
                    loadConversations()
                    // Выбираем новый диалог
                    _selectedConversationId.value = info.id
                    onCreated(info.id)
                    AppLogger.info("ConversationListViewModel", "Создан диалог: ${info.id}")
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Ошибка создания диалога"
                    AppLogger.error("ConversationListViewModel", "Ошибка создания: ${e.message}")
                }

            _isLoading.value = false
        }
    }

    /**
     * Удалить диалог.
     */
    fun deleteConversation(conversationId: String) {
        scope.launch {
            _isLoading.value = true
            _error.value = null

            apiClient.deleteConversation(conversationId)
                .onSuccess {
                    // Если удалили текущий диалог, сбрасываем выбор
                    if (_selectedConversationId.value == conversationId) {
                        _selectedConversationId.value = null
                    }
                    // Перезагружаем список
                    loadConversations()
                    AppLogger.info("ConversationListViewModel", "Удалён диалог: $conversationId")
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Ошибка удаления диалога"
                    AppLogger.error("ConversationListViewModel", "Ошибка удаления: ${e.message}")
                }

            _isLoading.value = false
        }
    }

    /**
     * Переименовать диалог.
     */
    fun renameConversation(conversationId: String, newTitle: String) {
        scope.launch {
            _error.value = null

            apiClient.renameConversation(conversationId, newTitle)
                .onSuccess {
                    // Перезагружаем список
                    loadConversations()
                    AppLogger.info("ConversationListViewModel", "Переименован диалог $conversationId: $newTitle")
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Ошибка переименования"
                    AppLogger.error("ConversationListViewModel", "Ошибка переименования: ${e.message}")
                }
        }
    }

    /**
     * Поиск по сообщениям.
     */
    fun search(query: String) {
        _searchQuery.value = query

        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        scope.launch {
            _isSearching.value = true

            apiClient.searchMessages(query)
                .onSuccess { response ->
                    _searchResults.value = response.results
                    AppLogger.info("ConversationListViewModel", "Найдено ${response.totalCount} результатов для '$query'")
                }
                .onFailure { e ->
                    _searchResults.value = emptyList()
                    AppLogger.error("ConversationListViewModel", "Ошибка поиска: ${e.message}")
                }

            _isSearching.value = false
        }
    }

    /**
     * Очистить результаты поиска.
     */
    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    /**
     * Экспорт диалога.
     */
    fun exportConversation(conversationId: String, format: String = "json", onExported: (ConversationExport) -> Unit) {
        scope.launch {
            apiClient.exportConversation(conversationId, format)
                .onSuccess { export ->
                    onExported(export)
                    AppLogger.info("ConversationListViewModel", "Экспортирован диалог: $conversationId")
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Ошибка экспорта"
                    AppLogger.error("ConversationListViewModel", "Ошибка экспорта: ${e.message}")
                }
        }
    }

    /**
     * Импорт диалога.
     */
    fun importConversation(export: ConversationExport, onImported: (String) -> Unit = {}) {
        scope.launch {
            _isLoading.value = true
            _error.value = null

            apiClient.importConversation(export)
                .onSuccess { info ->
                    loadConversations()
                    _selectedConversationId.value = info.id
                    onImported(info.id)
                    AppLogger.info("ConversationListViewModel", "Импортирован диалог: ${info.id}")
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Ошибка импорта"
                    AppLogger.error("ConversationListViewModel", "Ошибка импорта: ${e.message}")
                }

            _isLoading.value = false
        }
    }

    /**
     * Обновить информацию о диалоге в списке (после отправки сообщения).
     */
    fun refreshConversation(conversationId: String) {
        scope.launch {
            // Просто перезагружаем весь список
            loadConversations()
        }
    }

    /**
     * Очистить ошибку.
     */
    fun clearError() {
        _error.value = null
    }
}
