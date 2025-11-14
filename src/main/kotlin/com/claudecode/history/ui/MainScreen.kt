package com.claudecode.history.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.claudecode.history.data.ConversationRepository
import com.claudecode.history.service.ClaudeConversationParser
import com.claudecode.history.service.IndexingService
import com.claudecode.history.service.SearchService

@Composable
fun MainScreen() {
    val repository = remember { ConversationRepository() }
    val searchService = remember { SearchService(repository) }
    val parser = remember { ClaudeConversationParser() }
    val indexingService = remember { IndexingService(repository, parser) }

    var currentScreen by remember { mutableStateOf(Screen.SEARCH) }

    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        Scaffold(
            topBar = {
                TopNavigationBar(
                    currentScreen = currentScreen,
                    onScreenChange = { currentScreen = it },
                    indexingService = indexingService
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                when (currentScreen) {
                    Screen.SEARCH -> SearchScreen(searchService)
                    Screen.STATISTICS -> StatisticsScreen(searchService)
                    Screen.SETTINGS -> SettingsScreen(indexingService)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        // Start watching for new conversations
        indexingService.startWatching()

        onDispose {
            indexingService.shutdown()
        }
    }
}

enum class Screen {
    SEARCH,
    STATISTICS,
    SETTINGS
}
