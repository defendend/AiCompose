package org.example

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
import org.example.ui.components.ChatScreen
import org.example.ui.components.LogWindow
import org.example.ui.components.ServerLogWindow
import org.example.ui.components.SettingsScreen
import org.example.ui.theme.AppTheme
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

enum class Screen {
    CHAT,
    SETTINGS
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
    val apiClient: ChatApiClient = koinInject()

    Window(
        onCloseRequest = ::exitApplication,
        title = "AiCompose",
        state = rememberWindowState(
            size = DpSize(900.dp, 700.dp)
        )
    ) {
        AppTheme {
            when (currentScreen) {
                Screen.CHAT -> MainContent(
                    chatViewModel = chatViewModel,
                    onToggleLogs = { showLogWindow = !showLogWindow },
                    onToggleServerLogs = { showServerLogWindow = !showServerLogWindow },
                    onOpenSettings = { currentScreen = Screen.SETTINGS }
                )
                Screen.SETTINGS -> {
                    val collectionSettings by chatViewModel.collectionSettings.collectAsState()
                    val temperature by chatViewModel.temperature.collectAsState()
                    SettingsScreen(
                        currentSettings = collectionSettings,
                        currentTemperature = temperature,
                        onSettingsChanged = { settings ->
                            chatViewModel.setCollectionSettings(settings)
                        },
                        onTemperatureChanged = { temp ->
                            chatViewModel.setTemperature(temp)
                        },
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
    onToggleLogs: () -> Unit,
    onToggleServerLogs: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val collectionSettings by chatViewModel.collectionSettings.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
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
                // Меню-кнопка с выпадающим списком
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.Menu,
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
            }
        }

        // Chat content
        ChatScreen(viewModel = chatViewModel)
    }
}
