package com.claudecode.history.data

import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

private val logger = KotlinLogging.logger {}

object DatabaseFactory {
    private const val DB_NAME = "claude_history.db"

    fun init() {
        val dbPath = getDatabasePath()
        logger.info { "Initializing database at: $dbPath" }

        Database.connect(
            url = "jdbc:sqlite:$dbPath",
            driver = "org.sqlite.JDBC"
        )

        transaction {
            // Create regular tables
            SchemaUtils.create(Conversations, Embeddings)
            logger.info { "Created base tables" }

            // Create FTS5 virtual table for full-text search
            createFTS5Table()
            logger.info { "Created FTS5 virtual table" }

            // Create triggers for auto-sync with FTS5
            createFTS5Triggers()
            logger.info { "Created FTS5 sync triggers" }
        }
    }

    private fun createFTS5Table() {
        val sql = """
            CREATE VIRTUAL TABLE IF NOT EXISTS conversations_fts
            USING fts5(
                content,
                project_path,
                file_path,
                content='conversations',
                content_rowid='id'
            )
        """.trimIndent()

        executeSqlStatement(sql)
    }

    private fun createFTS5Triggers() {
        // Insert trigger
        val insertTrigger = """
            CREATE TRIGGER IF NOT EXISTS conversations_ai
            AFTER INSERT ON conversations
            BEGIN
                INSERT INTO conversations_fts(rowid, content, project_path, file_path)
                VALUES (new.id, new.content, new.project_path, new.file_path);
            END
        """.trimIndent()

        executeSqlStatement(insertTrigger)

        // Update trigger
        val updateTrigger = """
            CREATE TRIGGER IF NOT EXISTS conversations_au
            AFTER UPDATE ON conversations
            BEGIN
                UPDATE conversations_fts
                SET content = new.content,
                    project_path = new.project_path,
                    file_path = new.file_path
                WHERE rowid = new.id;
            END
        """.trimIndent()

        executeSqlStatement(updateTrigger)

        // Delete trigger
        val deleteTrigger = """
            CREATE TRIGGER IF NOT EXISTS conversations_ad
            AFTER DELETE ON conversations
            BEGIN
                DELETE FROM conversations_fts WHERE rowid = old.id;
            END
        """.trimIndent()

        executeSqlStatement(deleteTrigger)
    }

    private fun executeSqlStatement(sql: String) {
        val connection = TransactionManager.current().connection
        val statement = connection.prepareStatement(sql, false)
        statement.executeUpdate()
        statement.closeIfPossible()
    }

    private fun getDatabasePath(): String {
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".claude-history")
        if (!appDir.exists()) {
            appDir.mkdirs()
            logger.info { "Created application directory: ${appDir.absolutePath}" }
        }
        return File(appDir, DB_NAME).absolutePath
    }
}
