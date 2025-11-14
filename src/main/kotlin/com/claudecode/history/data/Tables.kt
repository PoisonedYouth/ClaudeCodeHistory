package com.claudecode.history.data

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Conversations : IntIdTable("conversations") {
    val sessionId = varchar("session_id", 255).index()
    val projectPath = varchar("project_path", 500).index()
    val timestamp = timestamp("timestamp").index()
    val role = varchar("role", 20)
    val content = text("content")
    val metadata = text("metadata").nullable()
    val language = varchar("language", 50).nullable().index()
    val filePath = varchar("file_path", 1000).nullable().index()
}

object Embeddings : IntIdTable("embeddings") {
    val conversationId = reference("conversation_id", Conversations)
    val embedding = blob("embedding")
    val model = varchar("model", 100)
}
