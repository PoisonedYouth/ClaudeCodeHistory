package com.claudecode.history.config

import com.claudecode.history.util.ValidationException
import com.claudecode.history.util.ValidationUtils
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Application configuration
 *
 * Can be loaded from:
 * - Environment variables (highest priority)
 * - Configuration file
 * - Defaults (lowest priority)
 */
data class AppConfig(
    val database: DatabaseConfig = DatabaseConfig(),
    val ollama: OllamaConfig = OllamaConfig(),
    val embedding: EmbeddingConfig = EmbeddingConfig(),
    val application: ApplicationConfig = ApplicationConfig()
) {
    // Note: Each sub-configuration validates itself in its init block

    companion object {
        /**
         * Environment variable prefix for all config values
         */
        const val ENV_PREFIX = "CLAUDE_HISTORY"
    }
}

/**
 * Database configuration
 */
data class DatabaseConfig(
    /**
     * Database file name (stored in ~/.claude-history/)
     */
    val databaseName: String = "claude_history.db",

    /**
     * Maximum number of database connections in the pool
     */
    val maxConnections: Int = 10,

    /**
     * SQLite busy timeout in milliseconds
     */
    val busyTimeoutMs: Int = 5000,

    /**
     * Enable SQLite Write-Ahead Logging (WAL) mode for better concurrency
     */
    val enableWalMode: Boolean = true,

    /**
     * SQLite synchronous mode: OFF, NORMAL, FULL
     * NORMAL is a good balance between safety and performance
     */
    val synchronousMode: String = "NORMAL"
) {
    init {
        validate()
    }

    private fun validate() {
        if (databaseName.isBlank()) {
            throw ValidationException("Database name cannot be blank")
        }

        ValidationUtils.validateRange(maxConnections, 1, 100, "Database max connections")
        ValidationUtils.validateRange(busyTimeoutMs, 100, 60000, "Database busy timeout")

        if (synchronousMode !in listOf("OFF", "NORMAL", "FULL")) {
            throw ValidationException("Database synchronous mode must be OFF, NORMAL, or FULL")
        }
    }

    companion object {
        const val ENV_DATABASE_NAME = "DATABASE_NAME"
        const val ENV_MAX_CONNECTIONS = "DATABASE_MAX_CONNECTIONS"
        const val ENV_BUSY_TIMEOUT = "DATABASE_BUSY_TIMEOUT_MS"
        const val ENV_WAL_MODE = "DATABASE_WAL_MODE"
        const val ENV_SYNC_MODE = "DATABASE_SYNC_MODE"
    }
}

/**
 * Ollama API configuration
 */
data class OllamaConfig(
    /**
     * Ollama API base URL
     */
    val baseUrl: String = "http://localhost:11434",

    /**
     * Embedding model name
     */
    val model: String = "nomic-embed-text",

    /**
     * Request timeout in seconds
     */
    val timeoutSeconds: Int = 30,

    /**
     * Maximum retry attempts for failed requests
     */
    val maxRetries: Int = 3,

    /**
     * Initial retry delay in milliseconds (doubles with each retry)
     */
    val retryDelayMs: Long = 1000,

    /**
     * Enable/disable Ollama integration
     */
    val enabled: Boolean = true
) {
    init {
        validate()
    }

    private fun validate() {
        if (baseUrl.isBlank()) {
            throw ValidationException("Ollama base URL cannot be blank")
        }

        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw ValidationException("Ollama base URL must start with http:// or https://")
        }

        if (model.isBlank()) {
            throw ValidationException("Ollama model name cannot be blank")
        }

        ValidationUtils.validateRange(timeoutSeconds, 1, 300, "Ollama timeout")
        ValidationUtils.validateRange(maxRetries, 0, 10, "Ollama max retries")
        ValidationUtils.validatePositive(retryDelayMs.toInt(), "Ollama retry delay")
    }

    fun getTimeout(): Duration = timeoutSeconds.seconds

    companion object {
        const val ENV_BASE_URL = "OLLAMA_BASE_URL"
        const val ENV_MODEL = "OLLAMA_MODEL"
        const val ENV_TIMEOUT = "OLLAMA_TIMEOUT_SECONDS"
        const val ENV_MAX_RETRIES = "OLLAMA_MAX_RETRIES"
        const val ENV_RETRY_DELAY = "OLLAMA_RETRY_DELAY_MS"
        const val ENV_ENABLED = "OLLAMA_ENABLED"
    }
}

/**
 * Embedding generation configuration
 */
data class EmbeddingConfig(
    /**
     * Number of embeddings to generate before adding delay
     */
    val batchSize: Int = 10,

    /**
     * Delay in milliseconds between batches
     */
    val batchDelayMs: Long = 100,

    /**
     * Maximum concurrent embedding generation tasks
     */
    val maxConcurrent: Int = 3,

    /**
     * Auto-generate embeddings for new conversations
     */
    val autoGenerate: Boolean = false
) {
    init {
        validate()
    }

    private fun validate() {
        ValidationUtils.validateRange(batchSize, 1, 1000, "Embedding batch size")
        ValidationUtils.validateRange(batchDelayMs.toInt(), 0, 10000, "Embedding batch delay")
        ValidationUtils.validateRange(maxConcurrent, 1, 20, "Embedding max concurrent")
    }

    companion object {
        const val ENV_BATCH_SIZE = "EMBEDDING_BATCH_SIZE"
        const val ENV_BATCH_DELAY = "EMBEDDING_BATCH_DELAY_MS"
        const val ENV_MAX_CONCURRENT = "EMBEDDING_MAX_CONCURRENT"
        const val ENV_AUTO_GENERATE = "EMBEDDING_AUTO_GENERATE"
    }
}

/**
 * General application configuration
 */
data class ApplicationConfig(
    /**
     * Application data directory (defaults to ~/.claude-history)
     */
    val dataDirectory: String? = null,

    /**
     * Enable debug logging
     */
    val debugMode: Boolean = false,

    /**
     * Default search result limit
     */
    val defaultSearchLimit: Int = 100,

    /**
     * Maximum search result limit
     */
    val maxSearchLimit: Int = 1000
) {
    init {
        validate()
    }

    private fun validate() {
        dataDirectory?.let {
            if (it.isBlank()) {
                throw ValidationException("Data directory cannot be blank")
            }
        }

        ValidationUtils.validateRange(defaultSearchLimit, 1, 10000, "Default search limit")
        ValidationUtils.validateRange(maxSearchLimit, 1, 10000, "Max search limit")

        if (defaultSearchLimit > maxSearchLimit) {
            throw ValidationException("Default search limit cannot exceed max search limit")
        }
    }

    /**
     * Resolve the data directory path, using default if not specified
     */
    fun resolveDataDirectory(): String {
        return dataDirectory ?: "${System.getProperty("user.home")}/.claude-history"
    }

    companion object {
        const val ENV_DATA_DIR = "DATA_DIRECTORY"
        const val ENV_DEBUG_MODE = "DEBUG_MODE"
        const val ENV_DEFAULT_SEARCH_LIMIT = "DEFAULT_SEARCH_LIMIT"
        const val ENV_MAX_SEARCH_LIMIT = "MAX_SEARCH_LIMIT"
    }
}
