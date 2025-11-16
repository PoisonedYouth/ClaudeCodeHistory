package com.claudecode.history.config

import mu.KotlinLogging
import java.io.File
import java.util.Properties

private val logger = KotlinLogging.logger {}

/**
 * Loads application configuration from multiple sources
 *
 * Priority (highest to lowest):
 * 1. Environment variables
 * 2. Configuration file (~/.claude-history/config.properties)
 * 3. Default values
 */
object ConfigLoader {

    /**
     * Load application configuration
     *
     * @param configFile Optional path to configuration file (defaults to ~/.claude-history/config.properties)
     * @return Loaded and validated AppConfig
     */
    fun load(configFile: File? = null): AppConfig {
        logger.info { "Loading configuration..." }

        // Load from properties file if it exists
        val properties = loadPropertiesFile(configFile)

        // Build configuration with priority: env vars > file > defaults
        val config = AppConfig(
            database = loadDatabaseConfig(properties),
            ollama = loadOllamaConfig(properties),
            embedding = loadEmbeddingConfig(properties),
            application = loadApplicationConfig(properties)
        )

        logger.info { "Configuration loaded successfully" }
        logger.debug { "Database: ${config.database.databaseName}, Ollama: ${config.ollama.baseUrl}, Model: ${config.ollama.model}" }

        return config
    }

    private fun loadPropertiesFile(configFile: File?): Properties {
        val properties = Properties()

        val file = configFile ?: File(
            System.getProperty("user.home"),
            ".claude-history/config.properties"
        )

        if (file.exists() && file.isFile) {
            try {
                file.inputStream().use { properties.load(it) }
                logger.info { "Loaded configuration from: ${file.absolutePath}" }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to load configuration file: ${file.absolutePath}" }
            }
        } else {
            logger.debug { "No configuration file found at: ${file.absolutePath}, using defaults" }
        }

        return properties
    }

    private fun loadDatabaseConfig(props: Properties): DatabaseConfig {
        val defaults = DatabaseConfig()
        return DatabaseConfig(
            databaseName = getConfigValue(
                envKey = "${AppConfig.ENV_PREFIX}_${DatabaseConfig.ENV_DATABASE_NAME}",
                propKey = "database.name",
                props = props,
                default = defaults.databaseName
            ) ?: defaults.databaseName,
            maxConnections = getConfigValue(
                envKey = "${AppConfig.ENV_PREFIX}_${DatabaseConfig.ENV_MAX_CONNECTIONS}",
                propKey = "database.maxConnections",
                props = props,
                default = defaults.maxConnections.toString()
            )?.toIntOrNull() ?: defaults.maxConnections,
            busyTimeoutMs = getConfigValue(
                envKey = "${AppConfig.ENV_PREFIX}_${DatabaseConfig.ENV_BUSY_TIMEOUT}",
                propKey = "database.busyTimeoutMs",
                props = props,
                default = defaults.busyTimeoutMs.toString()
            )?.toIntOrNull() ?: defaults.busyTimeoutMs,
            enableWalMode = getConfigValue(
                envKey = "${AppConfig.ENV_PREFIX}_${DatabaseConfig.ENV_WAL_MODE}",
                propKey = "database.walMode",
                props = props,
                default = defaults.enableWalMode.toString()
            )?.toBooleanStrictOrNull() ?: defaults.enableWalMode,
            synchronousMode = getConfigValue(
                envKey = "${AppConfig.ENV_PREFIX}_${DatabaseConfig.ENV_SYNC_MODE}",
                propKey = "database.synchronousMode",
                props = props,
                default = defaults.synchronousMode
            ) ?: defaults.synchronousMode
        )
    }

    private fun loadOllamaConfig(props: Properties): OllamaConfig {
        val defaults = OllamaConfig()
        return OllamaConfig(
            baseUrl = getConfigValue(
                envKey = "${AppConfig.ENV_PREFIX}_${OllamaConfig.ENV_BASE_URL}",
                propKey = "ollama.baseUrl",
                props = props,
                default = defaults.baseUrl
            ) ?: defaults.baseUrl,
            model = getConfigValue(
                envKey = "${AppConfig.ENV_PREFIX}_${OllamaConfig.ENV_MODEL}",
                propKey = "ollama.model",
                props = props,
                default = defaults.model
            ) ?: defaults.model,
            timeoutSeconds = getConfigValue(
                envKey = "${AppConfig.ENV_PREFIX}_${OllamaConfig.ENV_TIMEOUT}",
                propKey = "ollama.timeoutSeconds",
                props = props,
                default = defaults.timeoutSeconds.toString()
            )?.toIntOrNull() ?: defaults.timeoutSeconds,
            maxRetries = getConfigValue(
                envKey = "${AppConfig.ENV_PREFIX}_${OllamaConfig.ENV_MAX_RETRIES}",
                propKey = "ollama.maxRetries",
                props = props,
                default = defaults.maxRetries.toString()
            )?.toIntOrNull() ?: defaults.maxRetries,
            retryDelayMs = getConfigValue(
                envKey = "${AppConfig.ENV_PREFIX}_${OllamaConfig.ENV_RETRY_DELAY}",
                propKey = "ollama.retryDelayMs",
                props = props,
                default = defaults.retryDelayMs.toString()
            )?.toLongOrNull() ?: defaults.retryDelayMs,
            enabled = getConfigValue(
                envKey = "${AppConfig.ENV_PREFIX}_${OllamaConfig.ENV_ENABLED}",
                propKey = "ollama.enabled",
                props = props,
                default = defaults.enabled.toString()
            )?.toBooleanStrictOrNull() ?: defaults.enabled
        )
    }

    private fun loadEmbeddingConfig(props: Properties): EmbeddingConfig {
        val defaults = EmbeddingConfig()
        return EmbeddingConfig(
            batchSize = getConfigValue(
                envKey = "${AppConfig.ENV_PREFIX}_${EmbeddingConfig.ENV_BATCH_SIZE}",
                propKey = "embedding.batchSize",
                props = props,
                default = defaults.batchSize.toString()
            )?.toIntOrNull() ?: defaults.batchSize,
            batchDelayMs = getConfigValue(
                envKey = "${AppConfig.ENV_PREFIX}_${EmbeddingConfig.ENV_BATCH_DELAY}",
                propKey = "embedding.batchDelayMs",
                props = props,
                default = defaults.batchDelayMs.toString()
            )?.toLongOrNull() ?: defaults.batchDelayMs,
            maxConcurrent = getConfigValue(
                envKey = "${AppConfig.ENV_PREFIX}_${EmbeddingConfig.ENV_MAX_CONCURRENT}",
                propKey = "embedding.maxConcurrent",
                props = props,
                default = defaults.maxConcurrent.toString()
            )?.toIntOrNull() ?: defaults.maxConcurrent,
            autoGenerate = getConfigValue(
                envKey = "${AppConfig.ENV_PREFIX}_${EmbeddingConfig.ENV_AUTO_GENERATE}",
                propKey = "embedding.autoGenerate",
                props = props,
                default = defaults.autoGenerate.toString()
            )?.toBooleanStrictOrNull() ?: defaults.autoGenerate
        )
    }

    private fun loadApplicationConfig(props: Properties): ApplicationConfig {
        val defaults = ApplicationConfig()
        val dataDir = getConfigValue(
            envKey = "${AppConfig.ENV_PREFIX}_${ApplicationConfig.ENV_DATA_DIR}",
            propKey = "application.dataDirectory",
            props = props,
            default = null
        )

        return ApplicationConfig(
            dataDirectory = if (dataDir.isNullOrBlank()) null else dataDir,
            debugMode = getConfigValue(
                envKey = "${AppConfig.ENV_PREFIX}_${ApplicationConfig.ENV_DEBUG_MODE}",
                propKey = "application.debugMode",
                props = props,
                default = defaults.debugMode.toString()
            )?.toBooleanStrictOrNull() ?: defaults.debugMode,
            defaultSearchLimit = getConfigValue(
                envKey = "${AppConfig.ENV_PREFIX}_${ApplicationConfig.ENV_DEFAULT_SEARCH_LIMIT}",
                propKey = "application.defaultSearchLimit",
                props = props,
                default = defaults.defaultSearchLimit.toString()
            )?.toIntOrNull() ?: defaults.defaultSearchLimit,
            maxSearchLimit = getConfigValue(
                envKey = "${AppConfig.ENV_PREFIX}_${ApplicationConfig.ENV_MAX_SEARCH_LIMIT}",
                propKey = "application.maxSearchLimit",
                props = props,
                default = defaults.maxSearchLimit.toString()
            )?.toIntOrNull() ?: defaults.maxSearchLimit
        )
    }

    /**
     * Get configuration value with priority: env var > properties file > default
     */
    private fun getConfigValue(
        envKey: String,
        propKey: String,
        props: Properties,
        default: String?
    ): String? {
        // 1. Try environment variable
        val envValue = System.getenv(envKey)
        if (!envValue.isNullOrBlank()) {
            logger.debug { "Using env var $envKey = $envValue" }
            return envValue
        }

        // 2. Try properties file
        val propValue = props.getProperty(propKey)
        if (!propValue.isNullOrBlank()) {
            logger.debug { "Using property $propKey = $propValue" }
            return propValue
        }

        // 3. Use default
        logger.debug { "Using default for $propKey = $default" }
        return default
    }

    /**
     * Create a sample configuration file
     */
    fun createSampleConfigFile(file: File) {
        val content = """
            # Claude Code History Configuration
            # All values shown are defaults and can be overridden by environment variables

            # Database Configuration
            database.name=claude_history.db
            database.maxConnections=10
            database.busyTimeoutMs=5000
            database.walMode=true
            database.synchronousMode=NORMAL

            # Ollama API Configuration
            ollama.baseUrl=http://localhost:11434
            ollama.model=nomic-embed-text
            ollama.timeoutSeconds=30
            ollama.maxRetries=3
            ollama.retryDelayMs=1000
            ollama.enabled=true

            # Embedding Generation Configuration
            embedding.batchSize=10
            embedding.batchDelayMs=100
            embedding.maxConcurrent=3
            embedding.autoGenerate=false

            # Application Configuration
            #application.dataDirectory=/custom/path
            application.debugMode=false
            application.defaultSearchLimit=100
            application.maxSearchLimit=1000
        """.trimIndent()

        file.parentFile?.mkdirs()
        file.writeText(content)
        logger.info { "Created sample configuration file at: ${file.absolutePath}" }
    }
}
