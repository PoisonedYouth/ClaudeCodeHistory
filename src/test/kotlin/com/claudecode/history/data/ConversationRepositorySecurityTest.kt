package com.claudecode.history.data

import com.claudecode.history.domain.*
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Security tests for ConversationRepository to verify SQL injection prevention
 */
class ConversationRepositorySecurityTest {

    private lateinit var database: Database
    private lateinit var repository: ConversationRepository
    private lateinit var tempDbFile: File

    @BeforeTest
    fun setup() {
        // Use temporary file-based SQLite database for testing
        tempDbFile = File.createTempFile("test_db_", ".db")
        database = Database.connect("jdbc:sqlite:${tempDbFile.absolutePath}", driver = "org.sqlite.JDBC")
        repository = ConversationRepository()

        // Create schema
        transaction {
            SchemaUtils.create(Conversations, Embeddings)

            // Create FTS5 virtual table
            exec("""
                CREATE VIRTUAL TABLE conversations_fts USING fts5(
                    content='conversations',
                    content_rowid='id',
                    content,
                    tokenize='porter'
                )
            """.trimIndent())

            // Create triggers to keep FTS5 in sync
            exec("""
                CREATE TRIGGER conversations_ai AFTER INSERT ON conversations BEGIN
                    INSERT INTO conversations_fts(rowid, content) VALUES (new.id, new.content);
                END;
            """.trimIndent())

            exec("""
                CREATE TRIGGER conversations_ad AFTER DELETE ON conversations BEGIN
                    DELETE FROM conversations_fts WHERE rowid = old.id;
                END;
            """.trimIndent())

            exec("""
                CREATE TRIGGER conversations_au AFTER UPDATE ON conversations BEGIN
                    UPDATE conversations_fts SET content = new.content WHERE rowid = new.id;
                END;
            """.trimIndent())
        }
    }

    @AfterTest
    fun cleanup() {
        transaction {
            SchemaUtils.drop(Conversations, Embeddings)
        }
        // Delete temporary database file
        tempDbFile.delete()
    }

    @Test
    fun `search with SQL injection attempt in query is properly escaped`() {
        // Given
        val normalConversation = createConversation(
            content = "This is normal content about kotlin programming"
        )
        val secretConversation = createConversation(
            content = "Secret data that should not be accessible"
        )

        repository.insert(normalConversation)
        repository.insert(secretConversation)

        // SQL injection attempt: trying to match all records
        val maliciousQuery = "kotlin' OR '1'='1"

        // When
        val results = repository.search(maliciousQuery, SearchFilters())

        // Then
        // Should only match if the literal string "kotlin' OR '1'='1" appears in content
        // which it doesn't, so should return empty results
        results.shouldBeEmpty()
    }

    @Test
    fun `search with SQL injection in projectPath filter is properly escaped`() {
        // Given
        val conversation1 = createConversation(
            content = "Test content",
            projectPath = "/legitimate/project"
        )
        val conversation2 = createConversation(
            content = "Test content",
            projectPath = "/another/project"
        )

        repository.insert(conversation1)
        repository.insert(conversation2)

        // SQL injection attempt in projectPath filter
        val maliciousPath = "legitimate' OR '1'='1"
        val filters = SearchFilters(projectPath = maliciousPath)

        // When
        val results = repository.search("Test", filters)

        // Then
        // Should not match any records because the malicious path doesn't exist
        results.shouldBeEmpty()
    }

    @Test
    fun `search with SQL injection in role filter is properly escaped`() {
        // Given
        val userConversation = createConversation(
            content = "User message",
            role = MessageRole.USER
        )
        val assistantConversation = createConversation(
            content = "Assistant message",
            role = MessageRole.ASSISTANT
        )

        repository.insert(userConversation)
        repository.insert(assistantConversation)

        // Create a filter with normal role
        val filters = SearchFilters(role = MessageRole.USER)

        // When
        val results = repository.search("message", filters)

        // Then
        // Should only return user messages
        results shouldHaveSize 1
        results[0].conversation.role shouldBe MessageRole.USER
    }

    @Test
    fun `search with special characters in query is properly escaped`() {
        // Given
        val conversation = createConversation(
            content = "Content with special chars: ' \" % _ \\"
        )

        repository.insert(conversation)

        // When - search for special characters
        val results1 = repository.search("special", SearchFilters())
        val results2 = repository.search("chars", SearchFilters())

        // Then
        results1 shouldHaveSize 1
        results2 shouldHaveSize 1
    }

    @Test
    fun `vectorSearch with SQL injection in projectPath filter is properly escaped`() {
        // Given
        val conversation = createConversation(
            content = "Test content",
            projectPath = "/test/project"
        )
        val id = repository.insert(conversation)

        // Insert embedding for the conversation
        val embedding = FloatArray(768) { 0.1f }
        transaction {
            Embeddings.insert { stmt ->
                stmt[Embeddings.conversationId] = id
                stmt[Embeddings.embedding] = ExposedBlob(com.claudecode.history.util.VectorUtils.floatArrayToBytes(embedding))
                stmt[Embeddings.model] = "test-model"
            }
        }

        // SQL injection attempt
        val maliciousPath = "test' OR '1'='1"
        val filters = SearchFilters(projectPath = maliciousPath)
        val queryEmbedding = FloatArray(768) { 0.1f }

        // When
        val results = repository.vectorSearch(queryEmbedding, filters)

        // Then
        // Should not match because malicious path doesn't exist
        results.shouldBeEmpty()
    }

    @Test
    fun `vectorSearch with SQL injection in language filter is properly escaped`() {
        // Given
        val conversation = createConversation(
            content = "Kotlin code",
            metadata = ConversationMetadata(language = "kotlin")
        )
        val id = repository.insert(conversation)

        // Insert embedding
        val embedding = FloatArray(768) { 0.2f }
        transaction {
            Embeddings.insert { stmt ->
                stmt[Embeddings.conversationId] = id
                stmt[Embeddings.embedding] = ExposedBlob(com.claudecode.history.util.VectorUtils.floatArrayToBytes(embedding))
                stmt[Embeddings.model] = "test-model"
            }
        }

        // SQL injection attempt
        val maliciousLang = "kotlin' OR '1'='1"
        val filters = SearchFilters(language = maliciousLang)
        val queryEmbedding = FloatArray(768) { 0.2f }

        // When
        val results = repository.vectorSearch(queryEmbedding, filters)

        // Then
        results.shouldBeEmpty()
    }

    @Test
    fun `getByFilters with SQL injection in filePath is properly escaped`() {
        // Given
        val conversation = createConversation(
            content = "Code content",
            metadata = ConversationMetadata(filePaths = listOf("/src/Main.kt"))
        )

        repository.insert(conversation)

        // SQL injection attempt
        val maliciousFilePath = "Main' OR '1'='1"
        val filters = SearchFilters(filePath = maliciousFilePath)

        // When
        val results = repository.getByFilters(filters)

        // Then
        results.shouldBeEmpty()
    }

    @Test
    fun `getByFilters with SQL injection in model filter is properly escaped`() {
        // Given
        val conversation = createConversation(
            content = "Assistant response",
            role = MessageRole.ASSISTANT,
            metadata = ConversationMetadata(model = "claude-sonnet-4-5")
        )

        repository.insert(conversation)

        // SQL injection attempt in model filter
        val maliciousModel = "claude' OR '1'='1"
        val filters = SearchFilters(model = maliciousModel)

        // When
        val results = repository.getByFilters(filters)

        // Then
        results.shouldBeEmpty()
    }

    @Test
    fun `search handles legitimate single quotes in content correctly`() {
        // Given
        val conversation = createConversation(
            content = "It's a nice day for programming in Kotlin's type system"
        )

        repository.insert(conversation)

        // When
        val results = repository.search("Kotlin", SearchFilters())

        // Then
        results shouldHaveSize 1
        results[0].conversation.content shouldBe "It's a nice day for programming in Kotlin's type system"
    }

    @Test
    fun `search with legitimate wildcards in projectPath works correctly`() {
        // Given
        val conversation1 = createConversation(
            content = "Test",
            projectPath = "/home/user/project1"
        )
        val conversation2 = createConversation(
            content = "Test",
            projectPath = "/home/user/project2"
        )
        val conversation3 = createConversation(
            content = "Test",
            projectPath = "/different/path"
        )

        repository.insert(conversation1)
        repository.insert(conversation2)
        repository.insert(conversation3)

        // When - search with partial path
        val filters = SearchFilters(projectPath = "user")
        val results = repository.search("Test", filters)

        // Then - should match both project1 and project2
        results shouldHaveSize 2
    }

    // Helper function
    private fun createConversation(
        content: String,
        sessionId: String = "test-session",
        projectPath: String = "/test/project",
        role: MessageRole = MessageRole.USER,
        metadata: ConversationMetadata? = null
    ): Conversation {
        return Conversation(
            id = 0,
            sessionId = sessionId,
            projectPath = projectPath,
            timestamp = Clock.System.now(),
            role = role,
            content = content,
            metadata = metadata
        )
    }
}
