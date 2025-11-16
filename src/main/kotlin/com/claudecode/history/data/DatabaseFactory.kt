package com.claudecode.history.data

import com.claudecode.history.config.DatabaseConfig
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

private val logger = KotlinLogging.logger {}

object DatabaseFactory {

    fun init(config: DatabaseConfig, dataDirectory: String) {
        val dbPath = getDatabasePath(config.databaseName, dataDirectory)
        logger.info { "Initializing database at: $dbPath" }

        // Build JDBC URL with configuration parameters
        val jdbcUrl = buildString {
            append("jdbc:sqlite:$dbPath")
            append("?busy_timeout=${config.busyTimeoutMs}")
            if (config.enableWalMode) {
                append("&journal_mode=WAL")
            }
        }

        val database = Database.connect(
            url = jdbcUrl,
            driver = "org.sqlite.JDBC"
        )

        // Configure SQLite PRAGMA statements (outside of any transaction)
        configureSQLite(config, database)

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

    private fun configureSQLite(config: DatabaseConfig, database: Database) {
        logger.info { "Configuring SQLite with synchronous=${config.synchronousMode}, WAL=${config.enableWalMode}" }

        // Execute PRAGMA statements that CAN be set inside transactions
        transaction {
            // Note: PRAGMA synchronous cannot be changed inside a transaction
            // Foreign keys and cache size can be configured here
            executeSqlStatement("PRAGMA foreign_keys = ON")
            executeSqlStatement("PRAGMA cache_size = -10000") // 10MB cache
        }

        logger.info { "SQLite configuration applied successfully" }
    }

    private fun getDatabasePath(databaseName: String, dataDirectory: String): String {
        val appDir = File(dataDirectory)
        if (!appDir.exists()) {
            appDir.mkdirs()
            logger.info { "Created application directory: ${appDir.absolutePath}" }
        }
        return File(appDir, databaseName).absolutePath
    }
}
