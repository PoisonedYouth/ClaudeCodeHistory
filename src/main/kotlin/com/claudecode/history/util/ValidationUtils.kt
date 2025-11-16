package com.claudecode.history.util

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile

/**
 * Validation utilities for input validation and security
 */
object ValidationUtils {

    // File size limits
    const val MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024 // 100 MB
    const val MAX_LINE_LENGTH = 1_000_000 // 1 MB per line
    const val MAX_LINES_PER_FILE = 100_000 // Maximum lines to read from a file

    // Content limits
    const val MAX_CONTENT_LENGTH = 500_000 // 500 KB max content for embedding
    const val MAX_SEARCH_QUERY_LENGTH = 1000
    const val MAX_PROJECT_PATH_LENGTH = 500
    const val MAX_SESSION_ID_LENGTH = 200

    // Ollama limits
    const val MAX_EMBEDDING_TEXT_LENGTH = 8192 // Common context window for embedding models
    const val MIN_EMBEDDING_TEXT_LENGTH = 10 // Minimum meaningful text length

    /**
     * Validate that a file is safe to read
     * @throws ValidationException if validation fails
     */
    fun validateFile(file: Path) {
        // Check existence
        if (!file.exists()) {
            throw ValidationException("File does not exist: $file")
        }

        // Check it's a regular file
        if (!file.isRegularFile()) {
            throw ValidationException("Not a regular file: $file")
        }

        // Check file size
        val size = try {
            file.fileSize()
        } catch (e: Exception) {
            throw ValidationException("Cannot read file size: $file", e)
        }

        if (size > MAX_FILE_SIZE_BYTES) {
            throw ValidationException(
                "File too large: ${size / (1024 * 1024)} MB (max ${MAX_FILE_SIZE_BYTES / (1024 * 1024)} MB): $file"
            )
        }

        // Check file is readable
        if (!Files.isReadable(file)) {
            throw ValidationException("File is not readable: $file")
        }
    }

    /**
     * Validate search query string
     * @throws ValidationException if validation fails
     */
    fun validateSearchQuery(query: String): String {
        if (query.length > MAX_SEARCH_QUERY_LENGTH) {
            throw ValidationException("Search query too long: ${query.length} characters (max $MAX_SEARCH_QUERY_LENGTH)")
        }

        // Remove control characters except newlines and tabs
        val sanitized = query.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"), "")

        return sanitized
    }

    /**
     * Validate project path
     * @throws ValidationException if validation fails
     */
    fun validateProjectPath(path: String): String {
        if (path.isBlank()) {
            throw ValidationException("Project path cannot be blank")
        }

        if (path.length > MAX_PROJECT_PATH_LENGTH) {
            throw ValidationException("Project path too long: ${path.length} characters (max $MAX_PROJECT_PATH_LENGTH)")
        }

        // Check for null bytes and other suspicious characters
        if (path.contains('\u0000')) {
            throw ValidationException("Project path contains null byte")
        }

        return path.trim()
    }

    /**
     * Validate session ID
     * @throws ValidationException if validation fails
     */
    fun validateSessionId(sessionId: String): String {
        if (sessionId.isBlank()) {
            throw ValidationException("Session ID cannot be blank")
        }

        if (sessionId.length > MAX_SESSION_ID_LENGTH) {
            throw ValidationException("Session ID too long: ${sessionId.length} characters (max $MAX_SESSION_ID_LENGTH)")
        }

        // Session IDs should be alphanumeric with hyphens/underscores
        if (!sessionId.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) {
            throw ValidationException("Invalid session ID format: $sessionId")
        }

        return sessionId
    }

    /**
     * Validate content length
     * @throws ValidationException if validation fails
     */
    fun validateContentLength(content: String, context: String = "Content"): String {
        if (content.length > MAX_CONTENT_LENGTH) {
            throw ValidationException("$context too long: ${content.length} characters (max $MAX_CONTENT_LENGTH)")
        }

        return content
    }

    /**
     * Validate text for embedding generation
     * @throws ValidationException if validation fails
     */
    fun validateEmbeddingText(text: String): String {
        val trimmed = text.trim()

        if (trimmed.length < MIN_EMBEDDING_TEXT_LENGTH) {
            throw ValidationException("Text too short for embedding: ${trimmed.length} characters (min $MIN_EMBEDDING_TEXT_LENGTH)")
        }

        if (trimmed.length > MAX_EMBEDDING_TEXT_LENGTH) {
            throw ValidationException("Text too long for embedding: ${trimmed.length} characters (max $MAX_EMBEDDING_TEXT_LENGTH)")
        }

        return trimmed
    }

    /**
     * Validate line length
     * @throws ValidationException if validation fails
     */
    fun validateLineLength(line: String, lineNumber: Int) {
        if (line.length > MAX_LINE_LENGTH) {
            throw ValidationException("Line $lineNumber too long: ${line.length} characters (max $MAX_LINE_LENGTH)")
        }
    }

    /**
     * Sanitize file path to prevent path traversal attacks
     */
    fun sanitizeFilePath(path: String): String {
        // Remove null bytes
        var sanitized = path.replace("\u0000", "")

        // Normalize path separators
        sanitized = sanitized.replace('\\', '/')

        // Remove path traversal attempts
        sanitized = sanitized.replace(Regex("\\.\\./"), "")
        sanitized = sanitized.replace(Regex("/\\.\\./"), "/")

        return sanitized
    }

    /**
     * Validate that a number is within acceptable range
     */
    fun validateRange(value: Int, min: Int, max: Int, fieldName: String) {
        if (value < min || value > max) {
            throw ValidationException("$fieldName out of range: $value (allowed: $min-$max)")
        }
    }

    /**
     * Validate that a number is positive
     */
    fun validatePositive(value: Int, fieldName: String) {
        if (value < 0) {
            throw ValidationException("$fieldName must be positive: $value")
        }
    }
}

/**
 * Exception thrown when validation fails
 */
class ValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)
