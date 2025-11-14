package com.claudecode.history.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

data class Conversation(
    val id: Int,
    val sessionId: String,
    val projectPath: String,
    val timestamp: Instant,
    val role: MessageRole,
    val content: String,
    val metadata: ConversationMetadata? = null
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

@Serializable
data class ConversationMetadata(
    val toolUses: List<ToolUse> = emptyList(),
    val filePaths: List<String> = emptyList(),
    val language: String? = null,
    val tokens: Int? = null,
    val model: String? = null
)

@Serializable
data class ToolUse(
    val toolName: String,
    val parameters: Map<String, String> = emptyMap(),
    val result: String? = null
)

data class SearchFilters(
    val projectPath: String? = null,
    val dateFrom: Instant? = null,
    val dateTo: Instant? = null,
    val role: MessageRole? = null,
    val language: String? = null,
    val filePath: String? = null
) {
    fun hasActiveFilters(): Boolean {
        return projectPath != null || dateFrom != null || dateTo != null ||
               role != null || language != null || filePath != null
    }
}

data class SearchResult(
    val conversation: Conversation,
    val snippet: String,
    val rank: Double
)
