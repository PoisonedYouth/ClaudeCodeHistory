package com.claudecode.history.data

import com.claudecode.history.domain.*
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

        ConversationStatistics(
            totalConversations = total,
            conversationsByRole = byRole,
            oldestConversation = oldestTimestamp,
            newestConversation = newestTimestamp
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
}

data class ConversationStatistics(
    val totalConversations: Long,
    val conversationsByRole: Map<String, Long>,
    val oldestConversation: Instant?,
    val newestConversation: Instant?
)
