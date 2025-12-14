package org.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import org.example.di.appModule
import org.example.logging.AppLogger
import org.example.model.CollectionModeTemplates
import org.example.network.ChatApiClient
import org.example.ui.ChatViewModel
import org.example.ui.ConversationListViewModel
import org.example.ui.components.ChatScreen
import org.example.ui.components.ConversationListPanel
import org.example.ui.components.LogWindow
import org.example.ui.components.ModelComparisonScreen
import org.example.ui.components.ServerLogWindow
import org.example.ui.components.SettingsScreen
import org.example.ui.ModelComparisonViewModel
import org.example.ui.theme.AppTheme
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

enum class Screen {
    CHAT,
    SETTINGS,
    MODEL_COMPARISON
}

fun main() = application {
    AppLogger.info("App", "Приложение запущено")

    KoinApplication(application = { modules(appModule) }) {
        App()
    }
}

@Composable
private fun ApplicationScope.App() {
    var showLogWindow by remember { mutableStateOf(false) }
    var showServerLogWindow by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf(Screen.CHAT) }

    val chatViewModel: ChatViewModel = koinInject()
    val conversationListViewModel: ConversationListViewModel = koinInject()
    val apiClient: ChatApiClient = koinInject()
    val modelComparisonViewModel = remember { ModelComparisonViewModel() }

    // Загружаем список чатов при запуске
    LaunchedEffect(Unit) {
        conversationListViewModel.loadConversations()
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "AiCompose",
        state = rememberWindowState(
            size = DpSize(1100.dp, 700.dp)  // Увеличили ширину для боковой панели
        )
    ) {
        AppTheme {
            when (currentScreen) {
                Screen.CHAT -> MainContent(
                    chatViewModel = chatViewModel,
                    conversationListViewModel = conversationListViewModel,
                    onToggleLogs = { showLogWindow = !showLogWindow },
                    onToggleServerLogs = { showServerLogWindow = !showServerLogWindow },
                    onOpenSettings = { currentScreen = Screen.SETTINGS },
                    onOpenModelComparison = { currentScreen = Screen.MODEL_COMPARISON }
                )
                Screen.SETTINGS -> {
                    val collectionSettings by chatViewModel.collectionSettings.collectAsState()
                    val temperature by chatViewModel.temperature.collectAsState()
                    val compressionSettings by chatViewModel.compressionSettings.collectAsState()
                    SettingsScreen(
                        currentSettings = collectionSettings,
                        currentTemperature = temperature,
                        currentCompressionSettings = compressionSettings,
                        onSettingsChanged = { settings ->
                            chatViewModel.setCollectionSettings(settings)
                        },
                        onTemperatureChanged = { temp ->
                            chatViewModel.setTemperature(temp)
                        },
                        onCompressionSettingsChanged = { settings ->
                            chatViewModel.setCompressionSettings(settings)
                        },
                        onBack = { currentScreen = Screen.CHAT }
                    )
                }
                Screen.MODEL_COMPARISON -> {
                    ModelComparisonScreen(
                        viewModel = modelComparisonViewModel,
                        onBack = { currentScreen = Screen.CHAT }
                    )
                }
            }
        }
    }

    if (showLogWindow) {
        LogWindow(
            onCloseRequest = { showLogWindow = false }
        )
    }

    if (showServerLogWindow) {
        ServerLogWindow(
            apiClient = apiClient,
            onCloseRequest = { showServerLogWindow = false }
        )
    }
}

@Composable
private fun MainContent(
    chatViewModel: ChatViewModel,
    conversationListViewModel: ConversationListViewModel,
    onToggleLogs: () -> Unit,
    onToggleServerLogs: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModelComparison: () -> Unit
) {
    val collectionSettings by chatViewModel.collectionSettings.collectAsState()
    val conversations by conversationListViewModel.conversations.collectAsState()
    val selectedConversationId by conversationListViewModel.selectedConversationId.collectAsState()
    val isLoadingConversations by conversationListViewModel.isLoading.collectAsState()
    val searchQuery by conversationListViewModel.searchQuery.collectAsState()
    val searchResults by conversationListViewModel.searchResults.collectAsState()
    val isSearching by conversationListViewModel.isSearching.collectAsState()
    val currentConversationId by chatViewModel.conversationId.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showSidebar by remember { mutableStateOf(true) }

    // Синхронизация selectedConversationId с chatViewModel
    LaunchedEffect(currentConversationId) {
        if (currentConversationId != null && currentConversationId != selectedConversationId) {
            conversationListViewModel.selectConversation(currentConversationId)
            conversationListViewModel.refreshConversation(currentConversationId!!)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Боковая панель со списком чатов
        AnimatedVisibility(
            visible = showSidebar,
            enter = expandHorizontally(),
            exit = shrinkHorizontally()
        ) {
            ConversationListPanel(
                conversations = conversations,
                selectedId = selectedConversationId,
                isLoading = isLoadingConversations,
                searchQuery = searchQuery,
                searchResults = searchResults,
                isSearching = isSearching,
                onSelect = { id ->
                    conversationListViewModel.selectConversation(id)
                    chatViewModel.switchConversation(id)
                },
                onNew = {
                    conversationListViewModel.createNewConversation { newId ->
                        chatViewModel.startNewConversation()
                    }
                },
                onDelete = { id ->
                    conversationListViewModel.deleteConversation(id)
                    if (id == selectedConversationId) {
                        chatViewModel.startNewConversation()
                    }
                },
                onRename = { id, newTitle ->
                    conversationListViewModel.renameConversation(id, newTitle)
                },
                onSearch = { query ->
                    conversationListViewModel.search(query)
                },
                onSearchResultClick = { result ->
                    conversationListViewModel.selectConversation(result.conversationId)
                    chatViewModel.switchConversation(result.conversationId)
                    conversationListViewModel.clearSearch()
                },
                modifier = Modifier.width(280.dp)
            )
        }

        // Основной контент
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // Top App Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кнопка скрыть/показать боковую панель
                    IconButton(onClick = { showSidebar = !showSidebar }) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = if (showSidebar) "Скрыть панель чатов" else "Показать панель чатов",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Название приложения
                    Text(
                        text = "AI Agent Chat",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Индикатор активного режима сбора
                    if (collectionSettings.enabled) {
                        val template = CollectionModeTemplates.getTemplate(collectionSettings.mode)
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = template.icon,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = template.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Меню-кнопка с выпадающим списком
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Меню",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Настройки") },
                                onClick = {
                                    showMenu = false
                                    onOpenSettings()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Сравнение моделей") },
                                onClick = {
                                    showMenu = false
                                    onOpenModelComparison()
                                },
                                leadingIcon = {
                                    Text("🔬")
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Локальные логи") },
                                onClick = {
                                    showMenu = false
                                    onToggleLogs()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Серверные логи") },
                                onClick = {
                                    showMenu = false
                                    onToggleServerLogs()
                                }
                            )
                        }
                    }
                }
            }

            // Chat content
            ChatScreen(viewModel = chatViewModel)
        }
    }
}
