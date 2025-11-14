package com.claudecode.history.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.claudecode.history.domain.MessageRole
import com.claudecode.history.domain.SearchResult
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun SearchResultCard(
    result: SearchResult,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val conversation = result.conversation

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Role icon and label
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (conversation.role) {
                            MessageRole.USER -> Icons.Default.Person
                            MessageRole.ASSISTANT -> Icons.Default.SmartToy
                            MessageRole.SYSTEM -> Icons.Default.Code
                        },
                        contentDescription = conversation.role.name,
                        modifier = Modifier.size(20.dp),
                        tint = when (conversation.role) {
                            MessageRole.USER -> MaterialTheme.colorScheme.primary
                            MessageRole.ASSISTANT -> MaterialTheme.colorScheme.secondary
                            MessageRole.SYSTEM -> MaterialTheme.colorScheme.tertiary
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        conversation.role.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Timestamp
                Text(
                    formatTimestamp(conversation.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Project path
            Text(
                conversation.projectPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Snippet
            Text(
                result.snippet,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Metadata row
            conversation.metadata?.let { metadata ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    metadata.language?.let { language ->
                        AssistChip(
                            onClick = { },
                            label = { Text(language, style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    if (metadata.toolUses.isNotEmpty()) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    "${metadata.toolUses.size} tool${if (metadata.toolUses.size > 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }

                    if (metadata.filePaths.isNotEmpty()) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    "${metadata.filePaths.size} file${if (metadata.filePaths.size > 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }

            // Relevance score
            if (result.rank < 0) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { minOf(1f, (-result.rank / 10f).toFloat()) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }
        }
    }
}

private fun formatTimestamp(localDateTime: kotlinx.datetime.LocalDateTime): String {
    return "${localDateTime.year}-${localDateTime.monthNumber.toString().padStart(2, '0')}-" +
            "${localDateTime.dayOfMonth.toString().padStart(2, '0')} " +
            "${localDateTime.hour.toString().padStart(2, '0')}:" +
            "${localDateTime.minute.toString().padStart(2, '0')}"
}
