package com.claudecode.history.service

import com.claudecode.history.data.ConversationRepository
import com.claudecode.history.domain.Conversation
import com.claudecode.history.domain.MessageRole
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class IndexingServiceTest {

    private lateinit var repository: ConversationRepository
    private lateinit var parser: ClaudeConversationParser
    private lateinit var indexingService: IndexingService
    private lateinit var tempDir: Path

    @BeforeTest
    fun setup() {
        repository = mockk(relaxed = true)
        parser = mockk()
        indexingService = IndexingService(repository, parser)

        // Create temporary directory for test files
        tempDir = Files.createTempDirectory("claude_test")
    }

    @AfterTest
    fun cleanup() {
        indexingService.shutdown()
        clearAllMocks()

        // Clean up temporary directory
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `indexAllConversations indexes all found conversation files`() = runTest {
        // Given
        val testFile1 = tempDir.resolve("session1.jsonl")
        val testFile2 = tempDir.resolve("session2.jsonl")

        val conversation1 = createMockConversation(1, "Content 1", "session1")
        val conversation2 = createMockConversation(2, "Content 2", "session1")
        val conversation3 = createMockConversation(3, "Content 3", "session2")

        coEvery { parser.findConversationFiles() } returns listOf(
            testFile1 to "/project1",
            testFile2 to "/project2"
        )
        coEvery { parser.parseConversationFile(testFile1, "/project1") } returns listOf(conversation1, conversation2)
        coEvery { parser.parseConversationFile(testFile2, "/project2") } returns listOf(conversation3)
        coEvery { repository.insert(any()) } returns 1

        // When
        val result = indexingService.indexAllConversations()

        // Then
        result.indexed shouldBe 3
        result.failed shouldBe 0
        coVerify(exactly = 3) { repository.insert(any()) }
        coVerify { parser.parseConversationFile(testFile1, "/project1") }
        coVerify { parser.parseConversationFile(testFile2, "/project2") }
    }

    @Test
    fun `indexAllConversations handles parsing errors gracefully`() = runTest {
        // Given
        val testFile1 = tempDir.resolve("valid.jsonl")
        val testFile2 = tempDir.resolve("invalid.jsonl")

        val conversation1 = createMockConversation(1, "Valid content", "session1")

        coEvery { parser.findConversationFiles() } returns listOf(
            testFile1 to "/project1",
            testFile2 to "/project2"
        )
        coEvery { parser.parseConversationFile(testFile1, "/project1") } returns listOf(conversation1)
        coEvery { parser.parseConversationFile(testFile2, "/project2") } throws Exception("Parse error")
        coEvery { repository.insert(any()) } returns 1

        // When
        val result = indexingService.indexAllConversations()

        // Then
        result.indexed shouldBe 1
        result.failed shouldBe 1
        coVerify(exactly = 1) { repository.insert(any()) }
    }

    @Test
    fun `indexAllConversations handles insertion errors gracefully`() = runTest {
        // Given
        val testFile = tempDir.resolve("session1.jsonl")

        val conversation1 = createMockConversation(1, "Content 1", "session1")
        val conversation2 = createMockConversation(2, "Content 2", "session1")

        coEvery { parser.findConversationFiles() } returns listOf(testFile to "/project1")
        coEvery { parser.parseConversationFile(testFile, "/project1") } returns listOf(conversation1, conversation2)
        coEvery { repository.insert(conversation1) } returns 1
        coEvery { repository.insert(conversation2) } throws Exception("Database error")

        // When
        val result = indexingService.indexAllConversations()

        // Then
        result.indexed shouldBe 1
        result.failed shouldBe 1
        coVerify { repository.insert(conversation1) }
        coVerify { repository.insert(conversation2) }
    }

    @Test
    fun `indexAllConversations returns zero when no files found`() = runTest {
        // Given
        coEvery { parser.findConversationFiles() } returns emptyList()

        // When
        val result = indexingService.indexAllConversations()

        // Then
        result.indexed shouldBe 0
        result.failed shouldBe 0
        coVerify(exactly = 0) { repository.insert(any()) }
    }

    @Test
    fun `startWatching starts file watching successfully`() {
        // Given - Before starting

        // When
        indexingService.startWatching()

        // Then
        // Watching started (no exception thrown)
        // Note: Cannot easily verify file watching without integration test
        // Would need to create files and verify they get indexed
    }

    @Test
    fun `startWatching prevents multiple concurrent watchers`() {
        // Given
        indexingService.startWatching()

        // When
        indexingService.startWatching() // Second call should be ignored

        // Then
        // No exception thrown, second call ignored
        // In real scenario, would log warning
    }

    @Test
    fun `stopWatching stops file watching`() {
        // Given
        indexingService.startWatching()

        // When
        indexingService.stopWatching()

        // Then
        // Watching stopped (no exception thrown)
    }

    @Test
    fun `stopWatching is idempotent`() {
        // Given
        indexingService.startWatching()
        indexingService.stopWatching()

        // When
        indexingService.stopWatching() // Second call should be safe

        // Then
        // No exception thrown
    }

    @Test
    fun `shutdown stops watching and cancels coroutine scope`() {
        // Given
        indexingService.startWatching()

        // When
        indexingService.shutdown()

        // Then
        // Service shut down successfully (no exception)
    }

    @Test
    fun `indexAllConversations processes multiple conversations from single file`() = runTest {
        // Given
        val testFile = tempDir.resolve("session1.jsonl")
        val conversations = (1..5).map { createMockConversation(it, "Content $it", "session1") }

        coEvery { parser.findConversationFiles() } returns listOf(testFile to "/project1")
        coEvery { parser.parseConversationFile(testFile, "/project1") } returns conversations
        coEvery { repository.insert(any()) } returns 1

        // When
        val result = indexingService.indexAllConversations()

        // Then
        result.indexed shouldBe 5
        result.failed shouldBe 0
        coVerify(exactly = 5) { repository.insert(any()) }
    }

    @Test
    fun `indexAllConversations continues after partial file failure`() = runTest {
        // Given
        val testFile1 = tempDir.resolve("file1.jsonl")
        val testFile2 = tempDir.resolve("file2.jsonl")
        val testFile3 = tempDir.resolve("file3.jsonl")

        val conversation1 = createMockConversation(1, "Content 1", "session1")
        val conversation3 = createMockConversation(3, "Content 3", "session3")

        coEvery { parser.findConversationFiles() } returns listOf(
            testFile1 to "/project1",
            testFile2 to "/project2",
            testFile3 to "/project3"
        )
        coEvery { parser.parseConversationFile(testFile1, "/project1") } returns listOf(conversation1)
        coEvery { parser.parseConversationFile(testFile2, "/project2") } throws Exception("Parse error")
        coEvery { parser.parseConversationFile(testFile3, "/project3") } returns listOf(conversation3)
        coEvery { repository.insert(any()) } returns 1

        // When
        val result = indexingService.indexAllConversations()

        // Then
        result.indexed shouldBe 2
        result.failed shouldBe 1
        coVerify { parser.parseConversationFile(testFile1, "/project1") }
        coVerify { parser.parseConversationFile(testFile2, "/project2") }
        coVerify { parser.parseConversationFile(testFile3, "/project3") }
    }

    @Test
    fun `indexAllConversations accumulates totals correctly`() = runTest {
        // Given
        val files = (1..3).map { tempDir.resolve("file$it.jsonl") }
        val conversationsPerFile = listOf(2, 3, 4) // Total: 9 conversations

        coEvery { parser.findConversationFiles() } returns files.mapIndexed { i, file ->
            file to "/project$i"
        }

        files.forEachIndexed { index, file ->
            val conversations = (1..conversationsPerFile[index]).map {
                createMockConversation(it, "Content", "session$index")
            }
            coEvery { parser.parseConversationFile(file, "/project$index") } returns conversations
        }

        coEvery { repository.insert(any()) } returns 1

        // When
        val result = indexingService.indexAllConversations()

        // Then
        result.indexed shouldBe 9
        result.failed shouldBe 0
    }

    @Test
    fun `indexAllConversations handles mixed success and failure`() = runTest {
        // Given
        val testFile = tempDir.resolve("session1.jsonl")
        val conversations = (1..4).map { createMockConversation(it, "Content $it", "session1") }

        coEvery { parser.findConversationFiles() } returns listOf(testFile to "/project1")
        coEvery { parser.parseConversationFile(testFile, "/project1") } returns conversations

        // First and third succeed, second and fourth fail
        coEvery { repository.insert(conversations[0]) } returns 1
        coEvery { repository.insert(conversations[1]) } throws Exception("DB error")
        coEvery { repository.insert(conversations[2]) } returns 1
        coEvery { repository.insert(conversations[3]) } throws Exception("DB error")

        // When
        val result = indexingService.indexAllConversations()

        // Then
        result.indexed shouldBe 2
        result.failed shouldBe 2
    }

    // Helper function
    private fun createMockConversation(
        id: Int,
        content: String,
        sessionId: String,
        projectPath: String = "/test/project"
    ): Conversation {
        return Conversation(
            id = id,
            sessionId = sessionId,
            projectPath = projectPath,
            timestamp = Clock.System.now(),
            role = MessageRole.USER,
            content = content,
            metadata = null
        )
    }
}
