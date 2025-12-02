package org.example

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import org.example.logging.AppLogger
import org.example.network.ChatApiClient
import org.example.ui.ChatViewModel
import org.example.ui.components.ChatScreen
import org.example.ui.components.LogWindow
import org.example.ui.components.ServerLogWindow
import org.example.ui.theme.AppTheme

fun main() = application {
    AppLogger.info("App", "Приложение запущено")

    var showLogWindow by remember { mutableStateOf(false) }
    var showServerLogWindow by remember { mutableStateOf(false) }
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
            MainContent(
                chatViewModel = chatViewModel,
                onToggleLogs = { showLogWindow = !showLogWindow },
                onToggleServerLogs = { showServerLogWindow = !showServerLogWindow }
            )
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
    onToggleServerLogs: () -> Unit
) {
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
                horizontalArrangement = Arrangement.spacedBy(4.dp)
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
            }
        }

        // Chat content
        ChatScreen(viewModel = chatViewModel)
    }
}
