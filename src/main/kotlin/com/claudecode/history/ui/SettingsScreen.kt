package com.claudecode.history.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.claudecode.history.service.EmbeddingService
import com.claudecode.history.service.IndexingService
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen(indexingService: IndexingService, embeddingService: EmbeddingService) {
    val scrollState = rememberScrollState()
    var isIndexing by remember { mutableStateOf(false) }
    var indexingResult by remember { mutableStateOf<String?>(null) }
    var isGeneratingEmbeddings by remember { mutableStateOf(false) }
    var embeddingResult by remember { mutableStateOf<String?>(null) }
    var ollamaStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Check Ollama status on load
    LaunchedEffect(Unit) {
        ollamaStatus = if (embeddingService.isOllamaAvailable()) {
            "Connected"
        } else {
            "Not Available"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Indexing section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Indexing",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Index all conversations from your Claude Code history. " +
                            "This will scan ~/.claude/projects/ for conversation files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isIndexing = true
                            indexingResult = null
                            try {
                                val result = indexingService.indexAllConversations()
                                indexingResult = "Successfully indexed ${result.indexed} conversations. " +
                                        "Failed: ${result.failed}"
                            } catch (e: Exception) {
                                indexingResult = "Error: ${e.message}"
                            } finally {
                                isIndexing = false
                            }
                        }
                    },
                    enabled = !isIndexing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isIndexing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Indexing...")
                    } else {
                        Text("Index All Conversations")
                    }
                }

                indexingResult?.let { result ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        result,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.startsWith("Error")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Semantic Search / Embeddings section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Semantic Search (Ollama)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        ollamaStatus ?: "Checking...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (ollamaStatus) {
                            "Connected" -> MaterialTheme.colorScheme.primary
                            "Not Available" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Generate embeddings for semantic search. Requires Ollama with nomic-embed-text model installed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isGeneratingEmbeddings = true
                            embeddingResult = null
                            try {
                                val stats = embeddingService.getEmbeddingStats()
                                if (stats.conversationsWithoutEmbeddings == 0) {
                                    embeddingResult = "All ${stats.totalConversations} conversations already have embeddings"
                                } else {
                                    embeddingResult = "Generating embeddings for ${stats.conversationsWithoutEmbeddings} conversations..."
                                    val generated = embeddingService.generateMissingEmbeddings { current, total ->
                                        embeddingResult = "Generating embeddings: $current / $total"
                                    }
                                    embeddingResult = "Successfully generated $generated embeddings"
                                }
                            } catch (e: Exception) {
                                embeddingResult = "Error: ${e.message}"
                            } finally {
                                isGeneratingEmbeddings = false
                            }
                        }
                    },
                    enabled = !isGeneratingEmbeddings && ollamaStatus == "Connected",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isGeneratingEmbeddings) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating...")
                    } else {
                        Text("Generate Missing Embeddings")
                    }
                }

                embeddingResult?.let { result ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        result,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.startsWith("Error")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Database location
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Database Location",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    File(System.getProperty("user.home"), ".claude-history/claude_history.db")
                        .absolutePath,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Claude Code directory
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Claude Code Directory",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    File(System.getProperty("user.home"), ".claude/projects").absolutePath,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Auto-watch status
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Auto-Watch Conversations",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Automatically index new conversations as they are created",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        "Enabled",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hook configuration info
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Claude Code Hooks",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "To enable real-time conversation capture, configure Claude Code hooks. " +
                            "See hooks/README.md in the project directory for instructions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
