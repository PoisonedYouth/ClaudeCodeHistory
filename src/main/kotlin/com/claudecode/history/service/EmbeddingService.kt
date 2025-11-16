package com.claudecode.history.service

import com.claudecode.history.config.EmbeddingConfig
import com.claudecode.history.data.ConversationRepository
import com.claudecode.history.data.Embeddings
import com.claudecode.history.util.VectorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

/**
 * Service for generating and managing conversation embeddings
 */
class EmbeddingService(
    private val ollamaClient: OllamaClient,
    private val repository: ConversationRepository,
    private val config: EmbeddingConfig = EmbeddingConfig()
) {

    /**
     * Generate and store embedding for a conversation
     * @param conversationId The ID of the conversation
     * @param content The conversation content to embed
     * @return true if successful, false if Ollama is not available
     */
    suspend fun generateAndStore(conversationId: Int, content: String): Boolean {
        return try {
            // Validate content
            if (content.isBlank()) {
                logger.debug { "Skipping embedding for conversation $conversationId: empty content" }
                return true // Consider empty content as "success" to avoid retrying
            }

            if (content.length < 10) {
                logger.debug { "Skipping embedding for conversation $conversationId: content too short (${content.length} chars)" }
                return true
            }

            // Check if embedding already exists
            val exists = withContext(Dispatchers.IO) {
                transaction {
                    !Embeddings.selectAll().where { Embeddings.conversationId eq conversationId }.empty()
                }
            }

            if (exists) {
                logger.debug { "Embedding already exists for conversation $conversationId" }
                return true
            }

            // Generate embedding
            logger.debug { "Generating embedding for conversation $conversationId (${content.length} chars)" }
            val embedding = ollamaClient.generateEmbedding(content)

            // Normalize vector
            val normalized = VectorUtils.normalize(embedding)

            // Convert to bytes for storage
            val bytes = VectorUtils.floatArrayToBytes(normalized)

            // Store in database
            withContext(Dispatchers.IO) {
                transaction {
                    Embeddings.insert {
                        it[Embeddings.conversationId] = conversationId
                        it[Embeddings.embedding] = org.jetbrains.exposed.sql.statements.api.ExposedBlob(bytes)
                        it[Embeddings.model] = ollamaClient.getModel()
                    }
                }
            }

            logger.info { "Successfully generated and stored embedding for conversation $conversationId" }
            true
        } catch (e: OllamaException) {
            logger.warn { "Failed to generate embedding for conversation $conversationId: ${e.message}" }
            false
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error generating embedding for conversation $conversationId" }
            false
        }
    }

    /**
     * Generate embeddings for all conversations that don't have them
     * @param onProgress Callback for progress updates (current, total)
     * @return Number of embeddings successfully generated
     */
    suspend fun generateMissingEmbeddings(
        onProgress: suspend (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Int {
        val conversationsWithoutEmbeddings = withContext(Dispatchers.IO) {
            repository.getConversationsWithoutEmbeddings()
        }

        val total = conversationsWithoutEmbeddings.size
        logger.info { "Found $total conversations without embeddings" }

        if (total == 0) {
            return 0
        }

        var generated = 0
        conversationsWithoutEmbeddings.forEachIndexed { index, conversation ->
            val success = generateAndStore(conversation.id, conversation.content)
            if (success) {
                generated++
            }

            onProgress(index + 1, total)

            // Add configurable delay after each batch to avoid overwhelming Ollama
            if ((index + 1) % config.batchSize == 0) {
                logger.debug { "Processed batch of ${config.batchSize}, waiting ${config.batchDelayMs}ms" }
                kotlinx.coroutines.delay(config.batchDelayMs)
            }
        }

        logger.info { "Generated $generated out of $total embeddings" }
        return generated
    }

    /**
     * Check if an embedding exists for a conversation
     */
    suspend fun hasEmbedding(conversationId: Int): Boolean = withContext(Dispatchers.IO) {
        transaction {
            !Embeddings.selectAll().where { Embeddings.conversationId eq conversationId }.empty()
        }
    }

    /**
     * Get the embedding for a conversation
     * @return FloatArray if embedding exists, null otherwise
     */
    suspend fun getEmbedding(conversationId: Int): FloatArray? = withContext(Dispatchers.IO) {
        transaction {
            Embeddings.selectAll().where { Embeddings.conversationId eq conversationId }
                .singleOrNull()
                ?.let { row ->
                    val bytes = row[Embeddings.embedding].bytes
                    VectorUtils.bytesToFloatArray(bytes)
                }
        }
    }

    /**
     * Generate embedding for a search query (without storing it)
     * @param query The search query text
     * @return Normalized embedding vector
     */
    suspend fun generateQueryEmbedding(query: String): FloatArray {
        val embedding = ollamaClient.generateEmbedding(query)
        return VectorUtils.normalize(embedding)
    }

    /**
     * Check if Ollama is available and ready to generate embeddings
     */
    suspend fun isOllamaAvailable(): Boolean {
        return ollamaClient.isAvailable()
    }

    /**
     * Get statistics about embeddings
     */
    suspend fun getEmbeddingStats(): EmbeddingStats = withContext(Dispatchers.IO) {
        transaction {
            val totalConversations = repository.getStatistics().totalConversations
            val embeddingsCount = Embeddings.selectAll().where { Embeddings.id.isNotNull() }.count()

            EmbeddingStats(
                totalConversations = totalConversations.toInt(),
                conversationsWithEmbeddings = embeddingsCount.toInt(),
                model = ollamaClient.getModel()
            )
        }
    }
}

/**
 * Statistics about conversation embeddings
 */
data class EmbeddingStats(
    val totalConversations: Int,
    val conversationsWithEmbeddings: Int,
    val model: String
) {
    val percentageComplete: Int
        get() = if (totalConversations == 0) 0
        else (conversationsWithEmbeddings * 100) / totalConversations

    val conversationsWithoutEmbeddings: Int
        get() = totalConversations - conversationsWithEmbeddings
}
