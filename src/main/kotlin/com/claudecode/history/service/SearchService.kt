package com.claudecode.history.service

import com.claudecode.history.data.ConversationRepository
import com.claudecode.history.data.ConversationStatistics
import com.claudecode.history.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class SearchService(
    private val repository: ConversationRepository,
    private val embeddingService: EmbeddingService
) {

    suspend fun search(
        query: String,
        filters: SearchFilters = SearchFilters(),
        mode: SearchMode = SearchMode.HYBRID,
        limit: Int = 100
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            // If no query but filters are set, search by filters only
            if (filters.hasActiveFilters()) {
                logger.info { "Searching by filters only: $filters" }
                val conversations = repository.getByFilters(filters, limit)
                return@withContext conversations.map { conversation ->
                    SearchResult(
                        conversation = conversation,
                        snippet = conversation.content.take(200).let { snippet ->
                            if (conversation.content.length > 200) "$snippet..." else snippet
                        },
                        rank = 1.0
                    )
                }
            } else {
                logger.warn { "Empty search query and no filters" }
                return@withContext emptyList()
            }
        }

        when (mode) {
            SearchMode.KEYWORD -> {
                logger.info { "Keyword search for: $query" }
                repository.search(query, filters, limit)
            }
            SearchMode.SEMANTIC -> {
                logger.info { "Semantic search for: $query" }
                performSemanticSearch(query, filters, limit)
            }
            SearchMode.HYBRID -> {
                logger.info { "Hybrid search for: $query" }
                performHybridSearch(query, filters, limit)
            }
        }
    }

    private suspend fun performSemanticSearch(
        query: String,
        filters: SearchFilters,
        limit: Int
    ): List<SearchResult> {
        return try {
            val queryEmbedding = embeddingService.generateQueryEmbedding(query)
            repository.vectorSearch(queryEmbedding, filters, limit)
        } catch (e: OllamaException) {
            logger.warn { "Semantic search failed, Ollama not available: ${e.message}" }
            logger.info { "Falling back to keyword search" }
            repository.search(query, filters, limit)
        }
    }

    private suspend fun performHybridSearch(
        query: String,
        filters: SearchFilters,
        limit: Int
    ): List<SearchResult> {
        return try {
            val queryEmbedding = embeddingService.generateQueryEmbedding(query)
            repository.hybridSearch(query, queryEmbedding, filters, limit)
        } catch (e: OllamaException) {
            logger.warn { "Hybrid search failed, Ollama not available: ${e.message}" }
            logger.info { "Falling back to keyword search" }
            repository.search(query, filters, limit)
        }
    }

    /**
     * Find conversations similar to the given conversation
     * @param conversation The conversation to find similar ones for
     * @param limit Maximum number of similar conversations to return
     */
    suspend fun findSimilar(conversation: Conversation, limit: Int = 10): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val embedding = embeddingService.getEmbedding(conversation.id)
                ?: run {
                    // Generate embedding if it doesn't exist
                    embeddingService.generateAndStore(conversation.id, conversation.content)
                    embeddingService.getEmbedding(conversation.id)
                }

            embedding?.let {
                repository.vectorSearch(it, limit = limit)
                    .filter { result -> result.conversation.id != conversation.id } // Exclude the source conversation
            } ?: emptyList()
        } catch (e: Exception) {
            logger.error(e) { "Failed to find similar conversations for ${conversation.id}" }
            emptyList()
        }
    }

    suspend fun getConversationsBySession(sessionId: String): List<Conversation> = withContext(Dispatchers.IO) {
        repository.getBySession(sessionId)
    }

    suspend fun getAllProjects(): List<String> = withContext(Dispatchers.IO) {
        repository.getAllProjects()
    }

    suspend fun getAllLanguages(): List<String> = withContext(Dispatchers.IO) {
        repository.getAllLanguages()
    }

    suspend fun getStatistics(): ConversationStatistics = withContext(Dispatchers.IO) {
        repository.getStatistics()
    }

    suspend fun getLatestConversationByProject(projectPath: String): SearchResult? = withContext(Dispatchers.IO) {
        val conversation = repository.getLatestConversationByProject(projectPath)
        conversation?.let {
            SearchResult(
                conversation = it,
                snippet = it.content.take(200).let { snippet ->
                    if (it.content.length > 200) "$snippet..." else snippet
                },
                rank = 1.0
            )
        }
    }

    suspend fun getConversationsByDateRange(from: Instant, to: Instant, limit: Int = 100): List<SearchResult> = withContext(Dispatchers.IO) {
        val conversations = repository.getConversationsByDateRange(from, to, limit)
        conversations.map { conversation ->
            SearchResult(
                conversation = conversation,
                snippet = conversation.content.take(200).let { snippet ->
                    if (conversation.content.length > 200) "$snippet..." else snippet
                },
                rank = 1.0
            )
        }
    }
}
