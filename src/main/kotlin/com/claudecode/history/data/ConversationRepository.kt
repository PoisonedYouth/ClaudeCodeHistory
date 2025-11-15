package com.claudecode.history.data

import com.claudecode.history.domain.*
import com.claudecode.history.util.VectorUtils
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.ResultSet
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager

private val logger = KotlinLogging.logger {}

class ConversationRepository {

    fun insert(conversation: Conversation): Int = transaction {
        val id = Conversations.insertAndGetId {
            it[sessionId] = conversation.sessionId
            it[projectPath] = conversation.projectPath
            it[timestamp] = conversation.timestamp
            it[role] = conversation.role.name
            it[content] = conversation.content
            it[metadata] = conversation.metadata?.let { meta -> Json.encodeToString<ConversationMetadata>(meta) }
            it[language] = conversation.metadata?.language
            it[filePath] = conversation.metadata?.filePaths?.firstOrNull()
        }.value

        logger.debug { "Inserted conversation id=$id, session=${conversation.sessionId}" }
        id
    }

    fun search(query: String, filters: SearchFilters, limit: Int = 100): List<SearchResult> = transaction {
        val results = mutableListOf<SearchResult>()

        // Build the complete SQL query with all parameters embedded
        val sql = buildCompleteSearchQuery(query, filters, limit)

        // Execute using TransactionManager to avoid triggering hooks
        val statement = TransactionManager.current().connection.prepareStatement(sql, false)
        val resultSet = statement.executeQuery()

        while (resultSet.next()) {
            val conv = parseConversationFromResultSet(resultSet)
            val snippet = generateSnippet(conv.content, query)
            val rank = resultSet.getDouble("rank")

            results.add(SearchResult(conv, snippet, rank))
        }

        resultSet.close()
        statement.closeIfPossible()

        logger.info { "Search returned ${results.size} results for query: $query" }
        results
    }

    private fun parseConversationFromResultSet(rs: ResultSet): Conversation {
        return Conversation(
            id = rs.getInt("id"),
            sessionId = rs.getString("session_id"),
            projectPath = rs.getString("project_path"),
            timestamp = Instant.fromEpochMilliseconds(rs.getLong("timestamp")),
            role = MessageRole.valueOf(rs.getString("role")),
            content = rs.getString("content"),
            metadata = rs.getString("metadata")?.let { Json.decodeFromString<ConversationMetadata>(it) }
        )
    }

    private fun buildCompleteSearchQuery(query: String, filters: SearchFilters, limit: Int): String {
        val conditions = mutableListOf<String>()

        // Escape single quotes in query for SQL safety
        val escapedQuery = query.replace("'", "''")

        if (filters.projectPath != null) {
            val escapedPath = filters.projectPath.replace("'", "''")
            conditions.add("c.project_path LIKE '%$escapedPath%'")
        }
        if (filters.dateFrom != null) {
            conditions.add("c.timestamp >= ${filters.dateFrom.toEpochMilliseconds()}")
        }
        if (filters.dateTo != null) {
            conditions.add("c.timestamp <= ${filters.dateTo.toEpochMilliseconds()}")
        }
        if (filters.role != null) {
            conditions.add("c.role = '${filters.role.name}'")
        }
        if (filters.language != null) {
            val escapedLang = filters.language.replace("'", "''")
            conditions.add("c.language = '$escapedLang'")
        }
        if (filters.filePath != null) {
            val escapedFilePath = filters.filePath.replace("'", "''")
            conditions.add("c.file_path LIKE '%$escapedFilePath%'")
        }
        if (filters.model != null) {
            val escapedModel = filters.model.replace("'", "''")
            conditions.add("c.metadata LIKE '%\"model\":\"$escapedModel\"%'")
        }

        val whereClause = if (conditions.isNotEmpty()) {
            "AND ${conditions.joinToString(" AND ")}"
        } else ""

        return """
            SELECT c.id, c.session_id, c.project_path, c.timestamp, c.role, c.content, c.metadata,
                   conversations_fts.rank as rank
            FROM conversations c
            JOIN conversations_fts ON c.id = conversations_fts.rowid
            WHERE conversations_fts MATCH '$escapedQuery'
            $whereClause
            ORDER BY rank, c.timestamp DESC
            LIMIT $limit
        """.trimIndent()
    }

    private fun buildSearchQuery(filters: SearchFilters, limit: Int): String {
        val conditions = mutableListOf<String>()

        if (filters.projectPath != null) conditions.add("c.project_path LIKE ?")
        if (filters.dateFrom != null) conditions.add("c.timestamp >= ?")
        if (filters.dateTo != null) conditions.add("c.timestamp <= ?")
        if (filters.role != null) conditions.add("c.role = ?")
        if (filters.language != null) conditions.add("c.language = ?")
        if (filters.filePath != null) conditions.add("c.file_path LIKE ?")

        val whereClause = if (conditions.isNotEmpty()) {
            "AND ${conditions.joinToString(" AND ")}"
        } else ""

        return """
            SELECT c.*, fts.rank
            FROM conversations c
            JOIN conversations_fts fts ON c.id = fts.rowid
            WHERE fts MATCH ?
            $whereClause
            ORDER BY fts.rank, c.timestamp DESC
            LIMIT $limit
        """.trimIndent()
    }

    private fun generateSnippet(content: String, query: String, maxLength: Int = 200): String {
        val queryTerms = query.split(" ").map { it.lowercase() }
        val contentLower = content.lowercase()

        // Find first occurrence of any query term
        val firstMatchIndex = queryTerms
            .mapNotNull { term -> contentLower.indexOf(term).takeIf { it >= 0 } }
            .minOrNull() ?: 0

        val start = maxOf(0, firstMatchIndex - maxLength / 2)
        val end = minOf(content.length, start + maxLength)

        var snippet = content.substring(start, end)

        if (start > 0) snippet = "...$snippet"
        if (end < content.length) snippet = "$snippet..."

        return snippet
    }

    fun getBySession(sessionId: String): List<Conversation> = transaction {
        Conversations.selectAll().where { Conversations.sessionId eq sessionId }
            .orderBy(Conversations.timestamp to SortOrder.ASC)
            .map { rowToConversation(it) }
    }

    fun getAllProjects(): List<String> = transaction {
        Conversations.select(Conversations.projectPath)
            .withDistinct()
            .map { it[Conversations.projectPath] }
            .sorted()
    }

    fun getAllLanguages(): List<String> = transaction {
        Conversations.select(Conversations.language)
            .where { Conversations.language.isNotNull() }
            .withDistinct()
            .mapNotNull { it[Conversations.language] }
            .sorted()
    }

    fun getAllModels(): List<String> = transaction {
        val models = mutableSetOf<String>()
        Conversations.select(Conversations.metadata)
            .where { Conversations.metadata.isNotNull() }
            .forEach { row ->
                row[Conversations.metadata]?.let { metadataJson ->
                    try {
                        val metadata = Json.decodeFromString<ConversationMetadata>(metadataJson)
                        metadata.model?.let { models.add(it) }
                    } catch (e: Exception) {
                        // Skip malformed metadata
                    }
                }
            }
        models.sorted()
    }

    fun getLatestConversationByProject(projectPath: String): Conversation? = transaction {
        Conversations.selectAll()
            .where { Conversations.projectPath eq projectPath }
            .orderBy(Conversations.timestamp to SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.let { rowToConversation(it) }
    }

    fun getConversationsByDateRange(from: Instant, to: Instant, limit: Int = 100): List<Conversation> = transaction {
        Conversations.selectAll()
            .where { (Conversations.timestamp greaterEq from) and (Conversations.timestamp lessEq to) }
            .orderBy(Conversations.timestamp to SortOrder.DESC)
            .limit(limit)
            .map { rowToConversation(it) }
    }

    fun getByFilters(filters: SearchFilters, limit: Int = 100): List<Conversation> = transaction {
        var query = Conversations.selectAll()

        // Build combined where clause with all active filters
        query = query.where {
            var condition: Op<Boolean> = Op.TRUE

            filters.projectPath?.let { path ->
                condition = condition and (Conversations.projectPath like "%$path%")
            }
            filters.dateFrom?.let { from ->
                condition = condition and (Conversations.timestamp greaterEq from)
            }
            filters.dateTo?.let { to ->
                condition = condition and (Conversations.timestamp lessEq to)
            }
            filters.role?.let { role ->
                condition = condition and (Conversations.role eq role.name)
            }
            filters.language?.let { lang ->
                condition = condition and (Conversations.language eq lang)
            }
            filters.filePath?.let { path ->
                condition = condition and (Conversations.filePath like "%$path%")
            }
            filters.model?.let { model ->
                condition = condition and (Conversations.metadata like "%\"model\":\"$model\"%")
            }

            condition
        }

        query.orderBy(Conversations.timestamp to SortOrder.DESC)
            .limit(limit)
            .map { rowToConversation(it) }
    }

    fun getStatistics(): ConversationStatistics = transaction {
        val total = Conversations.selectAll().count()
        val byRole = Conversations.select(Conversations.role, Conversations.id.count())
            .groupBy(Conversations.role)
            .associate { it[Conversations.role] to it[Conversations.id.count()] }

        val oldestTimestamp = Conversations.select(Conversations.timestamp)
            .orderBy(Conversations.timestamp to SortOrder.ASC)
            .limit(1)
            .firstOrNull()
            ?.get(Conversations.timestamp)

        val newestTimestamp = Conversations.select(Conversations.timestamp)
            .orderBy(Conversations.timestamp to SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.get(Conversations.timestamp)

        // Calculate token and model statistics
        var totalInputTokens = 0L
        var totalOutputTokens = 0L
        var totalCacheCreationTokens = 0L
        var totalCacheReadTokens = 0L
        val modelCounts = mutableMapOf<String, Long>()

        Conversations.selectAll().forEach { row ->
            row[Conversations.metadata]?.let { metadataJson ->
                try {
                    val metadata = Json.decodeFromString<ConversationMetadata>(metadataJson)
                    metadata.usage?.let { usage ->
                        totalInputTokens += usage.inputTokens
                        totalOutputTokens += usage.outputTokens
                        totalCacheCreationTokens += usage.cacheCreationInputTokens
                        totalCacheReadTokens += usage.cacheReadInputTokens
                    }
                    metadata.model?.let { model ->
                        modelCounts[model] = modelCounts.getOrDefault(model, 0) + 1
                    }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to parse metadata for statistics" }
                }
            }
        }

        ConversationStatistics(
            totalConversations = total,
            conversationsByRole = byRole,
            conversationsByModel = modelCounts,
            oldestConversation = oldestTimestamp,
            newestConversation = newestTimestamp,
            totalInputTokens = totalInputTokens,
            totalOutputTokens = totalOutputTokens,
            totalCacheCreationTokens = totalCacheCreationTokens,
            totalCacheReadTokens = totalCacheReadTokens
        )
    }

    private fun rowToConversation(row: ResultRow) = Conversation(
        id = row[Conversations.id].value,
        sessionId = row[Conversations.sessionId],
        projectPath = row[Conversations.projectPath],
        timestamp = row[Conversations.timestamp],
        role = MessageRole.valueOf(row[Conversations.role]),
        content = row[Conversations.content],
        metadata = row[Conversations.metadata]?.let { Json.decodeFromString<ConversationMetadata>(it) }
    )

    /**
     * Get conversations that don't have embeddings yet
     */
    fun getConversationsWithoutEmbeddings(): List<Conversation> = transaction {
        val sql = """
            SELECT c.*
            FROM conversations c
            LEFT JOIN embeddings e ON c.id = e.conversation_id
            WHERE e.id IS NULL
            ORDER BY c.timestamp DESC
        """.trimIndent()

        val statement = TransactionManager.current().connection.prepareStatement(sql, false)
        val resultSet = statement.executeQuery()

        val conversations = mutableListOf<Conversation>()
        while (resultSet.next()) {
            conversations.add(parseConversationFromResultSet(resultSet))
        }

        resultSet.close()
        statement.closeIfPossible()

        conversations
    }

    /**
     * Perform vector similarity search
     * @param queryEmbedding The embedding vector to search for
     * @param filters Optional filters to apply
     * @param limit Maximum number of results
     * @return List of SearchResult ordered by similarity (most similar first)
     */
    fun vectorSearch(
        queryEmbedding: FloatArray,
        filters: SearchFilters = SearchFilters(),
        limit: Int = 100
    ): List<SearchResult> = transaction {
        val results = mutableListOf<SearchResult>()

        // Build filter conditions
        val conditions = mutableListOf<String>()
        if (filters.projectPath != null) {
            conditions.add("c.project_path LIKE '%${filters.projectPath.replace("'", "''")}%'")
        }
        if (filters.dateFrom != null) {
            conditions.add("c.timestamp >= ${filters.dateFrom.toEpochMilliseconds()}")
        }
        if (filters.dateTo != null) {
            conditions.add("c.timestamp <= ${filters.dateTo.toEpochMilliseconds()}")
        }
        if (filters.role != null) {
            conditions.add("c.role = '${filters.role.name}'")
        }
        if (filters.language != null) {
            conditions.add("c.language = '${filters.language.replace("'", "''")}'")
        }
        if (filters.filePath != null) {
            conditions.add("c.file_path LIKE '%${filters.filePath.replace("'", "''")}%'")
        }
        if (filters.model != null) {
            conditions.add("c.metadata LIKE '%\"model\":\"${filters.model.replace("'", "''")}\"%'")
        }

        val whereClause = if (conditions.isNotEmpty()) {
            "WHERE ${conditions.joinToString(" AND ")}"
        } else ""

        val sql = """
            SELECT c.id, c.session_id, c.project_path, c.timestamp, c.role, c.content, c.metadata,
                   e.embedding
            FROM conversations c
            INNER JOIN embeddings e ON c.id = e.conversation_id
            $whereClause
        """.trimIndent()

        val statement = TransactionManager.current().connection.prepareStatement(sql, false)
        val resultSet = statement.executeQuery()

        val similarities = mutableListOf<Pair<Conversation, Double>>()
        while (resultSet.next()) {
            val conv = parseConversationFromResultSet(resultSet)
            val embeddingBytes = resultSet.getBytes("embedding")
            val embedding = VectorUtils.bytesToFloatArray(embeddingBytes)

            val similarity = VectorUtils.cosineSimilarity(queryEmbedding, embedding)
            similarities.add(conv to similarity)
        }

        resultSet.close()
        statement.closeIfPossible()

        // Sort by similarity (highest first) and take top results
        val topResults = similarities
            .sortedByDescending { it.second }
            .take(limit)

        topResults.forEach { (conv, similarity) ->
            val snippet = conv.content.take(200).let {
                if (conv.content.length > 200) "$it..." else it
            }
            results.add(SearchResult(conv, snippet, similarity))
        }

        logger.info { "Vector search returned ${results.size} results" }
        results
    }

    /**
     * Hybrid search combining FTS5 keyword search and vector similarity using Reciprocal Rank Fusion
     * @param query Keyword query for FTS5 search
     * @param queryEmbedding Embedding vector for semantic search
     * @param filters Optional filters
     * @param limit Maximum number of results
     * @return Combined and re-ranked search results
     */
    fun hybridSearch(
        query: String,
        queryEmbedding: FloatArray,
        filters: SearchFilters = SearchFilters(),
        limit: Int = 100
    ): List<SearchResult> = transaction {
        // Get results from both search methods
        val fts5Results = search(query, filters, limit = 50)
        val vectorResults = vectorSearch(queryEmbedding, filters, limit = 50)

        // Reciprocal Rank Fusion algorithm
        val k = 60.0  // RRF constant
        val combinedScores = mutableMapOf<Int, Double>()
        val conversationMap = mutableMapOf<Int, Conversation>()
        val snippetMap = mutableMapOf<Int, String>()

        // Add FTS5 scores
        fts5Results.forEachIndexed { rank, result ->
            val rrf = 1.0 / (k + rank + 1)
            combinedScores[result.conversation.id] = rrf
            conversationMap[result.conversation.id] = result.conversation
            snippetMap[result.conversation.id] = result.snippet
        }

        // Add vector scores
        vectorResults.forEachIndexed { rank, result ->
            val rrf = 1.0 / (k + rank + 1)
            val currentScore = combinedScores.getOrDefault(result.conversation.id, 0.0)
            combinedScores[result.conversation.id] = currentScore + rrf
            conversationMap[result.conversation.id] = result.conversation
            if (!snippetMap.containsKey(result.conversation.id)) {
                snippetMap[result.conversation.id] = result.snippet
            }
        }

        // Sort by combined score and create results
        val results = combinedScores.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { (convId, score) ->
                SearchResult(
                    conversation = conversationMap[convId]!!,
                    snippet = snippetMap[convId]!!,
                    rank = score
                )
            }

        logger.info { "Hybrid search returned ${results.size} results (FTS5: ${fts5Results.size}, Vector: ${vectorResults.size})" }
        results
    }
}

data class ConversationStatistics(
    val totalConversations: Long,
    val conversationsByRole: Map<String, Long>,
    val conversationsByModel: Map<String, Long> = emptyMap(),
    val oldestConversation: Instant?,
    val newestConversation: Instant?,
    val totalInputTokens: Long = 0,
    val totalOutputTokens: Long = 0,
    val totalCacheCreationTokens: Long = 0,
    val totalCacheReadTokens: Long = 0
) {
    val totalTokens: Long
        get() = totalInputTokens + totalOutputTokens + totalCacheCreationTokens + totalCacheReadTokens

    val estimatedTotalCost: Double
        get() {
            val inputCost = (totalInputTokens / 1_000_000.0) * 3.0
            val outputCost = (totalOutputTokens / 1_000_000.0) * 15.0
            val cacheWriteCost = (totalCacheCreationTokens / 1_000_000.0) * 3.75
            val cacheReadCost = (totalCacheReadTokens / 1_000_000.0) * 0.30
            return inputCost + outputCost + cacheWriteCost + cacheReadCost
        }
}
