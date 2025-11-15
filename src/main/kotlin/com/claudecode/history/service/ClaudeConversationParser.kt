package com.claudecode.history.service

import com.claudecode.history.domain.Conversation
import com.claudecode.history.domain.ConversationMetadata
import com.claudecode.history.domain.MessageRole
import com.claudecode.history.domain.ToolUse
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import java.io.File
import java.nio.file.Path
import kotlin.io.path.readLines

private val logger = KotlinLogging.logger {}

@Serializable
data class ClaudeEvent(
    val type: String,
    val message: ClaudeMessage? = null,
    val timestamp: String? = null
)

@Serializable
data class ClaudeMessage(
    val role: String,
    val content: JsonElement,
    val model: String? = null,
    val usage: ClaudeUsage? = null
)

@Serializable
data class ClaudeUsage(
    val input_tokens: Int? = null,
    val output_tokens: Int? = null,
    val cache_creation_input_tokens: Int? = null,
    val cache_read_input_tokens: Int? = null
)

class ClaudeConversationParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parse a JSONL file from Claude Code's conversation history
     */
    fun parseConversationFile(file: Path, projectPath: String): List<Conversation> {
        logger.info { "Parsing conversation file: $file" }

        val conversations = mutableListOf<Conversation>()
        val sessionId = extractSessionId(file)

        try {
            file.readLines().forEachIndexed { index, line ->
                if (line.isBlank()) return@forEachIndexed

                try {
                    val event = json.decodeFromString<ClaudeEvent>(line)

                    if (event.message != null && event.timestamp != null) {
                        val conversation = parseEvent(event, sessionId, projectPath)
                        if (conversation != null) {
                            conversations.add(conversation)
                        }
                    }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to parse line $index in file $file: ${line.take(100)}" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to read conversation file: $file" }
        }

        logger.info { "Parsed ${conversations.size} conversations from $file" }
        return conversations
    }

    private fun parseEvent(event: ClaudeEvent, sessionId: String, projectPath: String): Conversation? {
        val message = event.message ?: return null
        val timestamp = event.timestamp ?: return null

        val role = when (message.role.lowercase()) {
            "user" -> MessageRole.USER
            "assistant" -> MessageRole.ASSISTANT
            "system" -> MessageRole.SYSTEM
            else -> {
                logger.warn { "Unknown role: ${message.role}" }
                return null
            }
        }

        val (content, metadata) = extractContentAndMetadata(message.content, message.model, message.usage)

        return Conversation(
            id = 0, // Will be set by database
            sessionId = sessionId,
            projectPath = projectPath,
            timestamp = Instant.parse(timestamp),
            role = role,
            content = content,
            metadata = metadata
        )
    }

    private fun extractContentAndMetadata(
        contentElement: JsonElement,
        model: String? = null,
        usage: ClaudeUsage? = null
    ): Pair<String, ConversationMetadata?> {
        val content = StringBuilder()
        val toolUses = mutableListOf<ToolUse>()
        val filePaths = mutableListOf<String>()
        var detectedLanguage: String? = null

        when {
            contentElement is kotlinx.serialization.json.JsonPrimitive -> {
                content.append(contentElement.content)
            }
            contentElement is kotlinx.serialization.json.JsonArray -> {
                contentElement.jsonArray.forEach { item ->
                    if (item is kotlinx.serialization.json.JsonObject) {
                        val obj = item.jsonObject
                        val type = obj["type"]?.jsonPrimitive?.content

                        when (type) {
                            "text" -> {
                                obj["text"]?.jsonPrimitive?.content?.let { content.append(it).append("\n") }
                            }
                            "tool_use" -> {
                                val toolName = obj["name"]?.jsonPrimitive?.content ?: "unknown"
                                val input = obj["input"]?.jsonObject?.mapValues { (_, value) ->
                                    when (value) {
                                        is kotlinx.serialization.json.JsonPrimitive -> value.content
                                        else -> value.toString()
                                    }
                                } ?: emptyMap()

                                toolUses.add(ToolUse(toolName, input))

                                // Extract file paths from tool use
                                input["file_path"]?.let { filePaths.add(it) }
                                input["path"]?.let { filePaths.add(it) }

                                // Detect language from code
                                if (toolName in listOf("Write", "Edit", "Read")) {
                                    input["file_path"]?.let { path ->
                                        detectedLanguage = detectLanguageFromPath(path)
                                    }
                                }
                            }
                            "tool_result" -> {
                                obj["content"]?.let { resultContent ->
                                    if (resultContent is kotlinx.serialization.json.JsonPrimitive) {
                                        val toolId = obj["tool_use_id"]?.jsonPrimitive?.content
                                        // Could associate with tool use if needed
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Convert ClaudeUsage to TokenUsage
        val tokenUsage = usage?.let {
            com.claudecode.history.domain.TokenUsage(
                inputTokens = it.input_tokens ?: 0,
                outputTokens = it.output_tokens ?: 0,
                cacheCreationInputTokens = it.cache_creation_input_tokens ?: 0,
                cacheReadInputTokens = it.cache_read_input_tokens ?: 0
            )
        }

        val metadata = if (toolUses.isNotEmpty() || filePaths.isNotEmpty() || detectedLanguage != null || model != null || tokenUsage != null) {
            ConversationMetadata(
                toolUses = toolUses,
                filePaths = filePaths.distinct(),
                language = detectedLanguage,
                model = model,
                usage = tokenUsage
            )
        } else null

        return content.toString().trim() to metadata
    }

    private fun detectLanguageFromPath(path: String): String? {
        val extension = path.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "js", "jsx" -> "javascript"
            "ts", "tsx" -> "typescript"
            "rs" -> "rust"
            "go" -> "go"
            "rb" -> "ruby"
            "php" -> "php"
            "c", "h" -> "c"
            "cpp", "cc", "cxx", "hpp" -> "cpp"
            "cs" -> "csharp"
            "swift" -> "swift"
            "m", "mm" -> "objective-c"
            "sh", "bash" -> "bash"
            "sql" -> "sql"
            "md" -> "markdown"
            "html", "htm" -> "html"
            "css" -> "css"
            "json" -> "json"
            "xml" -> "xml"
            "yaml", "yml" -> "yaml"
            else -> null
        }
    }

    private fun extractSessionId(file: Path): String {
        // Extract session ID from filename
        // Handles: chat_12345.jsonl, agent-abc123.jsonl, uuid.jsonl, etc.
        val filename = file.fileName.toString()
        return filename.removeSuffix(".jsonl")
    }

    /**
     * Find all Claude Code conversation files in the default location
     */
    fun findConversationFiles(): List<Pair<Path, String>> {
        val claudeDir = File(System.getProperty("user.home"), ".claude/projects")

        if (!claudeDir.exists()) {
            logger.warn { "Claude directory not found: ${claudeDir.absolutePath}" }
            return emptyList()
        }

        val files = mutableListOf<Pair<Path, String>>()

        claudeDir.listFiles()?.forEach { projectDir ->
            if (projectDir.isDirectory) {
                val projectPath = decodeProjectPath(projectDir.name)

                // Find all .jsonl files (chat_*.jsonl, agent-*.jsonl, <uuid>.jsonl, etc.)
                projectDir.listFiles { _, name -> name.endsWith(".jsonl") }
                    ?.forEach { file ->
                        files.add(file.toPath() to projectPath)
                        logger.debug { "Found conversation file: ${file.name} in project: $projectPath" }
                    }
            }
        }

        logger.info { "Found ${files.size} conversation files" }
        return files
    }

    /**
     * Decode Claude's project directory name back to the original path
     * Example: -mypath-my-project -> /mypath/my_project/
     */
    private fun decodeProjectPath(encodedPath: String): String {
        return encodedPath
            .replace('-', '/')
            .let { if (it.startsWith("/")) it else "/$it" }
    }
}
