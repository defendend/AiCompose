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
import org.example.logging.AppLogger
import org.example.model.CollectionMode
import org.example.model.CollectionModeTemplates
import org.example.network.ChatApiClient
import org.example.ui.ChatViewModel
import org.example.ui.components.ChatScreen
import org.example.ui.components.LogWindow
import org.example.ui.components.ServerLogWindow
import org.example.ui.components.SettingsScreen
import org.example.ui.theme.AppTheme

enum class Screen {
    CHAT,
    SETTINGS
}

fun main() = application {
    AppLogger.info("App", "Приложение запущено")

    var showLogWindow by remember { mutableStateOf(false) }
    var showServerLogWindow by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf(Screen.CHAT) }
    val chatViewModel = remember { ChatViewModel() }
    val apiClient = remember { ChatApiClient() }

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
                    SettingsScreen(
                        currentSettings = collectionSettings,
                        onSettingsChanged = { settings ->
                            chatViewModel.setCollectionSettings(settings)
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
                IconButton(onClick = onToggleLogs) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Локальные логи",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(onClick = onToggleServerLogs) {
                    Text("Серверные логи")
                }

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

                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Настройки",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Chat content
        ChatScreen(viewModel = chatViewModel)
    }
}
