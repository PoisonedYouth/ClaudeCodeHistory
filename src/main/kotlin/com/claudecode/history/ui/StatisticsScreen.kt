package com.claudecode.history.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecode.history.data.ConversationStatistics
import com.claudecode.history.service.SearchService
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun StatisticsScreen(searchService: SearchService) {
    var statistics by remember { mutableStateOf<ConversationStatistics?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                statistics = searchService.getStatistics()
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            "Statistics",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            statistics?.let { stats ->
                // Total conversations
                StatCard(
                    title = "Total Conversations",
                    value = stats.totalConversations.toString(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Conversations by role
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Conversations by Role",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        stats.conversationsByRole.forEach { (role, count) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    role.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    count.toString(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (role != stats.conversationsByRole.keys.last()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Conversations by Model
                if (stats.conversationsByModel.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Conversations by Model",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            stats.conversationsByModel.entries.sortedByDescending { it.value }.forEach { (model, count) ->
                                val displayName = when {
                                    model.contains("claude-sonnet-4-5") -> "Sonnet 4.5"
                                    model.contains("claude-sonnet-4") -> "Sonnet 4"
                                    model.contains("claude-sonnet-3-5") -> "Sonnet 3.5"
                                    model.contains("claude-opus") -> "Opus"
                                    model.contains("claude-haiku") -> "Haiku"
                                    else -> model.take(30)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        displayName,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        count.toString(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (model != stats.conversationsByModel.keys.last()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Token Usage Statistics
                if (stats.totalTokens > 0) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Token Usage & Cost Estimation",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Total tokens
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Total Tokens",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Text(
                                    String.format("%,d", stats.totalTokens),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Input tokens
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Input Tokens",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    String.format("%,d", stats.totalInputTokens),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            // Output tokens
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Output Tokens",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    String.format("%,d", stats.totalOutputTokens),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            // Cache creation tokens
                            if (stats.totalCacheCreationTokens > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Cache Writes",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        String.format("%,d", stats.totalCacheCreationTokens),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            // Cache read tokens
                            if (stats.totalCacheReadTokens > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Cache Reads",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        String.format("%,d", stats.totalCacheReadTokens),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            // Estimated cost
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Estimated Total Cost",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Text(
                                    "$${String.format("%.4f", stats.estimatedTotalCost)}",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                "Based on Claude 3.5 Sonnet pricing: Input \$3/M, Output \$15/M, Cache Write \$3.75/M, Cache Read \$0.30/M",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Date range
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    stats.oldestConversation?.let { oldest ->
                        StatCard(
                            title = "Oldest Conversation",
                            value = oldest.toLocalDateTime(TimeZone.currentSystemDefault())
                                .date.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    stats.newestConversation?.let { newest ->
                        StatCard(
                            title = "Newest Conversation",
                            value = newest.toLocalDateTime(TimeZone.currentSystemDefault())
                                .date.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
