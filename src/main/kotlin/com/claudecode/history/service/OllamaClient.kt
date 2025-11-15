package com.claudecode.history.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Client for interacting with Ollama API to generate text embeddings
 */
class OllamaClient(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "nomic-embed-text"
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = false
            })
        }
    }

    /**
     * Generate embedding vector for the given text
     * @param text The text to embed
     * @return FloatArray containing the embedding vector
     * @throws OllamaException if Ollama is not available or request fails
     */
    suspend fun generateEmbedding(text: String): FloatArray {
        return try {
            logger.debug { "Generating embedding for text of length ${text.length}" }

            val response: HttpResponse = client.post("$baseUrl/api/embed") {
                contentType(ContentType.Application.Json)
                setBody(EmbedRequest(
                    model = model,
                    input = text
                ))
            }

            if (response.status.isSuccess()) {
                val embedResponse = response.body<EmbedResponse>()
                if (embedResponse.embeddings.isEmpty()) {
                    logger.error { "Ollama returned empty embeddings list" }
                    throw OllamaException("Ollama returned empty embeddings. Is the model '$model' installed? Run: ollama pull $model")
                }
                logger.debug { "Successfully generated embedding with ${embedResponse.embeddings.first().size} dimensions" }
                embedResponse.embeddings.first()
            } else {
                val error = response.bodyAsText()
                logger.error { "Ollama API error: ${response.status} - $error" }
                throw OllamaException("Failed to generate embedding: ${response.status}")
            }
        } catch (e: OllamaException) {
            // Re-throw OllamaExceptions as-is
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to connect to Ollama at $baseUrl" }
            throw OllamaException("Ollama connection failed. Is Ollama running? Visit https://ollama.com for installation.", e)
        }
    }

    /**
     * Check if Ollama is available and the embedding model is installed
     * @return true if Ollama is ready, false otherwise
     */
    suspend fun isAvailable(): Boolean {
        return try {
            val response: HttpResponse = client.get("$baseUrl/api/tags")
            if (response.status.isSuccess()) {
                val tags = response.body<TagsResponse>()
                val modelInstalled = tags.models.any { it.name.contains(model, ignoreCase = true) }

                if (!modelInstalled) {
                    logger.warn { "Model '$model' not found. Run: ollama pull $model" }
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
    fun getModel(): String = model

    /**
     * Get the current Ollama base URL
     */
    fun getBaseUrl(): String = baseUrl

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
