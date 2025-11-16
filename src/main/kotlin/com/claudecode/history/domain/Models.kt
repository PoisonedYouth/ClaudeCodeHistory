package com.claudecode.history.domain

import com.claudecode.history.util.ValidationException
import com.claudecode.history.util.ValidationUtils
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
    val model: String? = null,
    val usage: TokenUsage? = null
)

@Serializable
data class TokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cacheCreationInputTokens: Int = 0,
    val cacheReadInputTokens: Int = 0
) {
    val totalTokens: Int
        get() = inputTokens + outputTokens + cacheCreationInputTokens + cacheReadInputTokens

    val estimatedCost: Double
        get() = calculateCost()

    private fun calculateCost(): Double {
        // Claude 3.5 Sonnet pricing (as of 2025)
        // Input: $3 per million tokens
        // Output: $15 per million tokens
        // Cache writes: $3.75 per million tokens
        // Cache reads: $0.30 per million tokens
        val inputCost = (inputTokens / 1_000_000.0) * 3.0
        val outputCost = (outputTokens / 1_000_000.0) * 15.0
        val cacheWriteCost = (cacheCreationInputTokens / 1_000_000.0) * 3.75
        val cacheReadCost = (cacheReadInputTokens / 1_000_000.0) * 0.30

        return inputCost + outputCost + cacheWriteCost + cacheReadCost
    }
}

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
    val filePath: String? = null,
    val model: String? = null
) {
    init {
        // Validate inputs to prevent injection attacks and excessive resource usage
        projectPath?.let { ValidationUtils.validateProjectPath(it) }
        filePath?.let { ValidationUtils.sanitizeFilePath(it) }

        // Validate string lengths
        language?.let {
            if (it.length > 50) {
                throw ValidationException("Language name too long: ${it.length} characters (max 50)")
            }
        }
        model?.let {
            if (it.length > 100) {
                throw ValidationException("Model name too long: ${it.length} characters (max 100)")
            }
        }

        // Validate date range
        if (dateFrom != null && dateTo != null && dateFrom > dateTo) {
            throw ValidationException("dateFrom cannot be after dateTo")
        }
    }

    fun hasActiveFilters(): Boolean {
        return projectPath != null || dateFrom != null || dateTo != null ||
               role != null || language != null || filePath != null || model != null
    }
}

data class SearchResult(
    val conversation: Conversation,
    val snippet: String,
    val rank: Double
)
