package com.claudecode.history.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecode.history.service.IndexingService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavigationBar(
    currentScreen: Screen,
    onScreenChange: (Screen) -> Unit,
    indexingService: IndexingService
) {
    var isIndexing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    TopAppBar(
        title = {
            Text("Claude Code History")
        },
        actions = {
            // Navigation buttons
            IconButton(
                onClick = { onScreenChange(Screen.SEARCH) }
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (currentScreen == Screen.SEARCH) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            IconButton(
                onClick = { onScreenChange(Screen.STATISTICS) }
            ) {
                Icon(
                    Icons.Default.BarChart,
                    contentDescription = "Statistics",
                    tint = if (currentScreen == Screen.STATISTICS) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            IconButton(
                onClick = { onScreenChange(Screen.SETTINGS) }
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = if (currentScreen == Screen.SETTINGS) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Index button
            Button(
                onClick = {
                    scope.launch {
                        isIndexing = true
                        try {
                            indexingService.indexAllConversations()
                        } finally {
                            isIndexing = false
                        }
                    }
                },
                enabled = !isIndexing
            ) {
                if (isIndexing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isIndexing) "Indexing..." else "Index All")
            }

            Spacer(modifier = Modifier.width(16.dp))
        }
    )
}
