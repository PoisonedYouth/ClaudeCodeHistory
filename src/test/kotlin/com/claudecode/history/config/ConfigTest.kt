package com.claudecode.history.config

import com.claudecode.history.util.ValidationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

class ConfigTest {

    // DatabaseConfig tests
    @Test
    fun `DatabaseConfig accepts valid configuration`() {
        // When
        val config = DatabaseConfig(
            databaseName = "test.db",
            maxConnections = 5,
            busyTimeoutMs = 3000,
            enableWalMode = true,
            synchronousMode = "NORMAL"
        )

        // Then
        config.databaseName shouldBe "test.db"
        config.maxConnections shouldBe 5
        config.busyTimeoutMs shouldBe 3000
        config.enableWalMode shouldBe true
        config.synchronousMode shouldBe "NORMAL"
    }

    @Test
    fun `DatabaseConfig throws ValidationException for blank database name`() {
        // When & Then
        val exception = shouldThrow<ValidationException> {
            DatabaseConfig(databaseName = "")
        }
        exception.message shouldContain "Database name cannot be blank"
    }

    @Test
    fun `DatabaseConfig throws ValidationException for invalid max connections`() {
        // When & Then
        val exception = shouldThrow<ValidationException> {
            DatabaseConfig(maxConnections = 0)
        }
        exception.message shouldContain "out of range"
    }

    @Test
    fun `DatabaseConfig throws ValidationException for invalid synchronous mode`() {
        // When & Then
        val exception = shouldThrow<ValidationException> {
            DatabaseConfig(synchronousMode = "INVALID")
        }
        exception.message shouldContain "must be OFF, NORMAL, or FULL"
    }

    // OllamaConfig tests
    @Test
    fun `OllamaConfig accepts valid configuration`() {
        // When
        val config = OllamaConfig(
            baseUrl = "http://localhost:11434",
            model = "test-model",
            timeoutSeconds = 60,
            maxRetries = 5,
            retryDelayMs = 2000,
            enabled = true
        )

        // Then
        config.baseUrl shouldBe "http://localhost:11434"
        config.model shouldBe "test-model"
        config.timeoutSeconds shouldBe 60
        config.maxRetries shouldBe 5
        config.retryDelayMs shouldBe 2000L
        config.enabled shouldBe true
    }

    @Test
    fun `OllamaConfig throws ValidationException for blank base URL`() {
        // When & Then
        val exception = shouldThrow<ValidationException> {
            OllamaConfig(baseUrl = "")
        }
        exception.message shouldContain "base URL cannot be blank"
    }

    @Test
    fun `OllamaConfig throws ValidationException for invalid base URL`() {
        // When & Then
        val exception = shouldThrow<ValidationException> {
            OllamaConfig(baseUrl = "ftp://invalid")
        }
        exception.message shouldContain "must start with http:// or https://"
    }

    @Test
    fun `OllamaConfig throws ValidationException for blank model`() {
        // When & Then
        val exception = shouldThrow<ValidationException> {
            OllamaConfig(model = "")
        }
        exception.message shouldContain "model name cannot be blank"
    }

    @Test
    fun `OllamaConfig getTimeout returns correct duration`() {
        // Given
        val config = OllamaConfig(timeoutSeconds = 120)

        // When
        val timeout = config.getTimeout()

        // Then
        timeout.inWholeSeconds shouldBe 120L
    }

    // EmbeddingConfig tests
    @Test
    fun `EmbeddingConfig accepts valid configuration`() {
        // When
        val config = EmbeddingConfig(
            batchSize = 20,
            batchDelayMs = 200,
            maxConcurrent = 5,
            autoGenerate = true
        )

        // Then
        config.batchSize shouldBe 20
        config.batchDelayMs shouldBe 200L
        config.maxConcurrent shouldBe 5
        config.autoGenerate shouldBe true
    }

    @Test
    fun `EmbeddingConfig throws ValidationException for invalid batch size`() {
        // When & Then
        val exception = shouldThrow<ValidationException> {
            EmbeddingConfig(batchSize = 0)
        }
        exception.message shouldContain "out of range"
    }

    // ApplicationConfig tests
    @Test
    fun `ApplicationConfig accepts valid configuration`() {
        // When
        val config = ApplicationConfig(
            dataDirectory = "/custom/path",
            debugMode = true,
            defaultSearchLimit = 50,
            maxSearchLimit = 500
        )

        // Then
        config.dataDirectory shouldBe "/custom/path"
        config.debugMode shouldBe true
        config.defaultSearchLimit shouldBe 50
        config.maxSearchLimit shouldBe 500
    }

    @Test
    fun `ApplicationConfig resolveDataDirectory returns custom path when specified`() {
        // Given
        val config = ApplicationConfig(dataDirectory = "/custom/path")

        // When
        val path = config.resolveDataDirectory()

        // Then
        path shouldBe "/custom/path"
    }

    @Test
    fun `ApplicationConfig resolveDataDirectory returns default when not specified`() {
        // Given
        val config = ApplicationConfig(dataDirectory = null)

        // When
        val path = config.resolveDataDirectory()

        // Then
        path shouldContain ".claude-history"
    }

    @Test
    fun `ApplicationConfig throws ValidationException when default exceeds max`() {
        // When & Then
        val exception = shouldThrow<ValidationException> {
            ApplicationConfig(
                defaultSearchLimit = 1000,
                maxSearchLimit = 100
            )
        }
        exception.message shouldContain "cannot exceed max"
    }

    // AppConfig tests
    @Test
    fun `AppConfig accepts valid configuration`() {
        // When
        val config = AppConfig(
            database = DatabaseConfig(),
            ollama = OllamaConfig(),
            embedding = EmbeddingConfig(),
            application = ApplicationConfig()
        )

        // Then
        config.database.databaseName shouldBe "claude_history.db"
        config.ollama.baseUrl shouldBe "http://localhost:11434"
        config.embedding.batchSize shouldBe 10
        config.application.debugMode shouldBe false
    }

    @Test
    fun `AppConfig validates all sub-configurations`() {
        // When & Then - should throw due to invalid database config
        val exception = shouldThrow<ValidationException> {
            AppConfig(
                database = DatabaseConfig(databaseName = "")
            )
        }
        exception.message shouldContain "Database name cannot be blank"
    }

    // ConfigLoader tests
    @Test
    fun `ConfigLoader loads default configuration when no file exists`() {
        // When
        val config = ConfigLoader.load(File("/nonexistent/config.properties"))

        // Then
        config.database.databaseName shouldBe "claude_history.db"
        config.ollama.baseUrl shouldBe "http://localhost:11434"
        config.embedding.batchSize shouldBe 10
    }

    @Test
    fun `ConfigLoader loads configuration from properties file`() {
        // Given
        val tempFile = createTempFile(suffix = ".properties")
        try {
            tempFile.writeText("""
                database.name=custom.db
                database.maxConnections=20
                ollama.baseUrl=http://custom:8080
                ollama.model=custom-model
                embedding.batchSize=50
            """.trimIndent())

            // When
            val config = ConfigLoader.load(tempFile.toFile())

            // Then
            config.database.databaseName shouldBe "custom.db"
            config.database.maxConnections shouldBe 20
            config.ollama.baseUrl shouldBe "http://custom:8080"
            config.ollama.model shouldBe "custom-model"
            config.embedding.batchSize shouldBe 50
        } finally {
            tempFile.deleteIfExists()
        }
    }

    @Test
    fun `ConfigLoader handles invalid property values gracefully`() {
        // Given
        val tempFile = createTempFile(suffix = ".properties")
        try {
            tempFile.writeText("""
                database.maxConnections=invalid
                ollama.timeoutSeconds=not-a-number
            """.trimIndent())

            // When
            val config = ConfigLoader.load(tempFile.toFile())

            // Then - should use defaults when parsing fails
            config.database.maxConnections shouldBe 10 // default
            config.ollama.timeoutSeconds shouldBe 30 // default
        } finally {
            tempFile.deleteIfExists()
        }
    }

    @Test
    fun `ConfigLoader creates sample config file`() {
        // Given
        val tempFile = createTempFile(suffix = ".properties")
        try {
            tempFile.toFile().delete()

            // When
            ConfigLoader.createSampleConfigFile(tempFile.toFile())

            // Then
            val content = tempFile.toFile().readText()
            content shouldContain "database.name="
            content shouldContain "ollama.baseUrl="
            content shouldContain "embedding.batchSize="
            content shouldContain "application.debugMode="
        } finally {
            tempFile.deleteIfExists()
        }
    }

    @Test
    fun `ConfigLoader handles boolean values correctly`() {
        // Given
        val tempFile = createTempFile(suffix = ".properties")
        try {
            tempFile.writeText("""
                database.walMode=false
                ollama.enabled=true
                embedding.autoGenerate=false
                application.debugMode=true
            """.trimIndent())

            // When
            val config = ConfigLoader.load(tempFile.toFile())

            // Then
            config.database.enableWalMode shouldBe false
            config.ollama.enabled shouldBe true
            config.embedding.autoGenerate shouldBe false
            config.application.debugMode shouldBe true
        } finally {
            tempFile.deleteIfExists()
        }
    }
}
