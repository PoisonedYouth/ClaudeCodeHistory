package com.claudecode.history.service

import com.claudecode.history.config.OllamaConfig
import com.claudecode.history.util.ValidationException
import com.claudecode.history.util.ValidationUtils
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Client for interacting with Ollama API to generate text embeddings
 */
class OllamaClient(
    private val config: OllamaConfig = OllamaConfig()
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = false
            })
        }

        install(HttpTimeout) {
            requestTimeoutMillis = config.getTimeout().inWholeMilliseconds
            connectTimeoutMillis = config.getTimeout().inWholeMilliseconds
        }
    }

    /**
     * Generate embedding vector for the given text with retry logic
     * @param text The text to embed
     * @return FloatArray containing the embedding vector
     * @throws OllamaException if Ollama is not available or request fails after retries
     * @throws ValidationException if text doesn't meet requirements
     */
    suspend fun generateEmbedding(text: String): FloatArray {
        if (!config.enabled) {
            throw OllamaException("Ollama integration is disabled")
        }

        // Validate and sanitize input
        val validatedText = try {
            ValidationUtils.validateEmbeddingText(text)
        } catch (e: ValidationException) {
            logger.warn(e) { "Embedding text validation failed" }
            throw OllamaException("Invalid text for embedding: ${e.message}", e)
        }

        logger.debug { "Generating embedding for text of length ${validatedText.length}" }

        // Retry logic with exponential backoff
        var lastException: Exception? = null
        repeat(config.maxRetries + 1) { attempt ->
            try {
                val response: HttpResponse = client.post("${config.baseUrl}/api/embed") {
                    contentType(ContentType.Application.Json)
                    setBody(EmbedRequest(
                        model = config.model,
                        input = validatedText
                    ))
                }

                if (response.status.isSuccess()) {
                    val embedResponse = response.body<EmbedResponse>()
                    if (embedResponse.embeddings.isEmpty()) {
                        logger.error { "Ollama returned empty embeddings list for text length ${validatedText.length}" }
                        logger.error { "Text preview: ${validatedText.take(100)}" }
                        throw OllamaException("Ollama returned empty embeddings. Is the model '${config.model}' installed? Run: ollama pull ${config.model}")
                    }
                    logger.debug { "Successfully generated embedding with ${embedResponse.embeddings.first().size} dimensions" }
                    return embedResponse.embeddings.first()
                } else {
                    val error = response.bodyAsText()
                    logger.error { "Ollama API error: ${response.status} - $error" }
                    throw OllamaException("Failed to generate embedding: ${response.status}")
                }
            } catch (e: OllamaException) {
                lastException = e
                if (attempt < config.maxRetries) {
                    val delayMs = config.retryDelayMs * (1 shl attempt) // Exponential backoff: 1x, 2x, 4x, etc.
                    logger.warn { "Attempt ${attempt + 1} failed, retrying in ${delayMs}ms: ${e.message}" }
                    delay(delayMs)
                } else {
                    logger.error { "All ${config.maxRetries + 1} attempts failed" }
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < config.maxRetries) {
                    val delayMs = config.retryDelayMs * (1 shl attempt)
                    logger.warn(e) { "Attempt ${attempt + 1} failed, retrying in ${delayMs}ms" }
                    delay(delayMs)
                } else {
                    logger.error(e) { "All ${config.maxRetries + 1} attempts failed" }
                }
            }
        }

        // If we get here, all retries failed
        throw OllamaException(
            "Failed to generate embedding after ${config.maxRetries + 1} attempts. Is Ollama running at ${config.baseUrl}? Visit https://ollama.com for installation.",
            lastException
        )
    }

    /**
     * Check if Ollama is available and the embedding model is installed
     * @return true if Ollama is ready, false otherwise
     */
    suspend fun isAvailable(): Boolean {
        if (!config.enabled) {
            return false
        }

        return try {
            val response: HttpResponse = client.get("${config.baseUrl}/api/tags")
            if (response.status.isSuccess()) {
                val tags = response.body<TagsResponse>()
                val modelInstalled = tags.models.any { it.name.contains(config.model, ignoreCase = true) }

                if (!modelInstalled) {
                    logger.warn { "Model '${config.model}' not found. Run: ollama pull ${config.model}" }
                }

                modelInstalled
            } else {
                logger.warn { "Ollama API returned: ${response.status}" }
                false
            }
        } catch (e: Exception) {
            logger.warn { "Ollama not available: ${e.message}" }
            false
        }
    }

    /**
     * Get the current embedding model name
     */
    fun getModel(): String = config.model

    /**
     * Get the current Ollama base URL
     */
    fun getBaseUrl(): String = config.baseUrl

    /**
     * Get the current configuration
     */
    fun getConfig(): OllamaConfig = config

    /**
     * Close the HTTP client
     */
    fun close() {
        client.close()
    }

    @Serializable
    private data class EmbedRequest(
        val model: String,
        val input: String
    )

    @Serializable
    private data class EmbedResponse(
        val model: String,
        val embeddings: List<FloatArray>
    )

    @Serializable
    private data class TagsResponse(
        val models: List<ModelInfo>
    )

    @Serializable
    private data class ModelInfo(
        val name: String,
        val size: Long = 0,
        @SerialName("modified_at") val modifiedAt: String = ""
    )
}

/**
 * Exception thrown when Ollama operations fail
 */
class OllamaException(message: String, cause: Throwable? = null) : Exception(message, cause)
