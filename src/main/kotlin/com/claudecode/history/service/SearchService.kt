package com.claudecode.history.service

import com.claudecode.history.data.ConversationRepository
import com.claudecode.history.data.ConversationStatistics
import com.claudecode.history.domain.Conversation
import com.claudecode.history.domain.SearchFilters
import com.claudecode.history.domain.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class SearchService(
    private val repository: ConversationRepository
) {

    suspend fun search(
        query: String,
        filters: SearchFilters = SearchFilters(),
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

        logger.info { "Searching for: $query with filters: $filters" }
        repository.search(query, filters, limit)
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
