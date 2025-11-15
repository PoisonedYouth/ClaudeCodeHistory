package com.claudecode.history.service

import com.claudecode.history.data.ConversationRepository
import com.claudecode.history.data.ConversationStatistics
import com.claudecode.history.domain.*
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.days

class SearchServiceTest {

    private lateinit var repository: ConversationRepository
    private lateinit var embeddingService: EmbeddingService
    private lateinit var searchService: SearchService

    @BeforeTest
    fun setup() {
        repository = mockk()
        embeddingService = mockk()
        searchService = SearchService(repository, embeddingService)
    }

    @AfterTest
    fun cleanup() {
        clearAllMocks()
    }

    @Test
    fun `search with blank query and no filters returns empty list`() = runTest {
        // Given
        val query = ""
        val filters = SearchFilters()

        // When
        val results = searchService.search(query, filters)

        // Then
        results.shouldBeEmpty()
        verify(exactly = 0) { repository.search(any(), any(), any()) }
    }

    @Test
    fun `search with blank query but active filters returns filtered results`() = runTest {
        // Given
        val query = ""
        val filters = SearchFilters(projectPath = "/test/project")
        val mockConversations = listOf(
            createMockConversation(id = 1, content = "Test content", projectPath = "/test/project")
        )

        coEvery { repository.getByFilters(filters, 100) } returns mockConversations

        // When
        val results = searchService.search(query, filters)

        // Then
        results shouldHaveSize 1
        results[0].conversation.id shouldBe 1
        results[0].rank shouldBe 1.0
        coVerify { repository.getByFilters(filters, 100) }
    }

    @Test
    fun `search with KEYWORD mode uses repository search`() = runTest {
        // Given
        val query = "kotlin"
        val filters = SearchFilters()
        val mockResults = listOf(
            SearchResult(
                conversation = createMockConversation(id = 1, content = "Learn Kotlin"),
                snippet = "Learn Kotlin",
                rank = 1.0
            )
        )

        coEvery { repository.search(query, filters, 100) } returns mockResults

        // When
        val results = searchService.search(query, filters, SearchMode.KEYWORD)

        // Then
        results shouldHaveSize 1
        results[0].conversation.content shouldBe "Learn Kotlin"
        coVerify { repository.search(query, filters, 100) }
    }

    @Test
    fun `search with SEMANTIC mode generates embedding and uses vectorSearch`() = runTest {
        // Given
        val query = "how to use kotlin"
        val filters = SearchFilters()
        val mockEmbedding = FloatArray(768) { 0.1f }
        val mockResults = listOf(
            SearchResult(
                conversation = createMockConversation(id = 1, content = "Kotlin tutorial"),
                snippet = "Kotlin tutorial",
                rank = 0.95
            )
        )

        coEvery { embeddingService.generateQueryEmbedding(query) } returns mockEmbedding
        coEvery { repository.vectorSearch(mockEmbedding, filters, 100) } returns mockResults

        // When
        val results = searchService.search(query, filters, SearchMode.SEMANTIC)

        // Then
        results shouldHaveSize 1
        results[0].rank shouldBe 0.95
        coVerify { embeddingService.generateQueryEmbedding(query) }
        coVerify { repository.vectorSearch(mockEmbedding, filters, 100) }
    }

    @Test
    fun `search with SEMANTIC mode falls back to KEYWORD when Ollama unavailable`() = runTest {
        // Given
        val query = "kotlin"
        val filters = SearchFilters()
        val mockResults = listOf(
            SearchResult(
                conversation = createMockConversation(id = 1, content = "Kotlin content"),
                snippet = "Kotlin content",
                rank = 1.0
            )
        )

        coEvery { embeddingService.generateQueryEmbedding(query) } throws OllamaException("Ollama not available")
        coEvery { repository.search(query, filters, 100) } returns mockResults

        // When
        val results = searchService.search(query, filters, SearchMode.SEMANTIC)

        // Then
        results shouldHaveSize 1
        results[0].conversation.content shouldBe "Kotlin content"
        coVerify { embeddingService.generateQueryEmbedding(query) }
        coVerify { repository.search(query, filters, 100) }
    }

    @Test
    fun `search with HYBRID mode uses both keyword and vector search`() = runTest {
        // Given
        val query = "kotlin coroutines"
        val filters = SearchFilters()
        val mockEmbedding = FloatArray(768) { 0.2f }
        val mockResults = listOf(
            SearchResult(
                conversation = createMockConversation(id = 1, content = "Coroutines tutorial"),
                snippet = "Coroutines tutorial",
                rank = 0.85
            )
        )

        coEvery { embeddingService.generateQueryEmbedding(query) } returns mockEmbedding
        coEvery { repository.hybridSearch(query, mockEmbedding, filters, 100) } returns mockResults

        // When
        val results = searchService.search(query, filters, SearchMode.HYBRID)

        // Then
        results shouldHaveSize 1
        coVerify { embeddingService.generateQueryEmbedding(query) }
        coVerify { repository.hybridSearch(query, mockEmbedding, filters, 100) }
    }

    @Test
    fun `search with HYBRID mode falls back to KEYWORD when Ollama unavailable`() = runTest {
        // Given
        val query = "kotlin"
        val filters = SearchFilters()
        val mockResults = listOf(
            SearchResult(
                conversation = createMockConversation(id = 1, content = "Kotlin"),
                snippet = "Kotlin",
                rank = 1.0
            )
        )

        coEvery { embeddingService.generateQueryEmbedding(query) } throws OllamaException("Ollama not running")
        coEvery { repository.search(query, filters, 100) } returns mockResults

        // When
        val results = searchService.search(query, filters, SearchMode.HYBRID)

        // Then
        results shouldHaveSize 1
        coVerify { repository.search(query, filters, 100) }
    }

    @Test
    fun `findSimilar returns similar conversations using existing embedding`() = runTest {
        // Given
        val conversation = createMockConversation(id = 1, content = "Test content")
        val mockEmbedding = FloatArray(768) { 0.3f }
        val similarResults = listOf(
            SearchResult(
                conversation = createMockConversation(id = 2, content = "Similar content"),
                snippet = "Similar content",
                rank = 0.92
            ),
            SearchResult(
                conversation = createMockConversation(id = 3, content = "Also similar"),
                snippet = "Also similar",
                rank = 0.88
            )
        )

        coEvery { embeddingService.getEmbedding(1) } returns mockEmbedding
        coEvery { repository.vectorSearch(mockEmbedding, limit = 10) } returns
            (listOf(SearchResult(conversation, "snippet", 1.0)) + similarResults)

        // When
        val results = searchService.findSimilar(conversation, 10)

        // Then
        results shouldHaveSize 2 // Excludes the source conversation
        results[0].conversation.id shouldBe 2
        results[1].conversation.id shouldBe 3
        coVerify { embeddingService.getEmbedding(1) }
    }

    @Test
    fun `findSimilar generates embedding if not exists`() = runTest {
        // Given
        val conversation = createMockConversation(id = 1, content = "New content")
        val mockEmbedding = FloatArray(768) { 0.4f }
        val similarResults = listOf(
            SearchResult(
                conversation = createMockConversation(id = 2, content = "Similar"),
                snippet = "Similar",
                rank = 0.90
            )
        )

        coEvery { embeddingService.getEmbedding(1) } returns null andThen mockEmbedding
        coEvery { embeddingService.generateAndStore(1, "New content") } returns true
        coEvery { repository.vectorSearch(mockEmbedding, limit = 10) } returns
            (listOf(SearchResult(conversation, "snippet", 1.0)) + similarResults)

        // When
        val results = searchService.findSimilar(conversation, 10)

        // Then
        results shouldHaveSize 1
        coVerify { embeddingService.generateAndStore(1, "New content") }
        coVerify(exactly = 2) { embeddingService.getEmbedding(1) }
    }

    @Test
    fun `findSimilar returns empty list on error`() = runTest {
        // Given
        val conversation = createMockConversation(id = 1, content = "Test")

        coEvery { embeddingService.getEmbedding(1) } throws Exception("Database error")

        // When
        val results = searchService.findSimilar(conversation, 10)

        // Then
        results.shouldBeEmpty()
    }

    @Test
    fun `getConversationsBySession returns conversations from repository`() = runTest {
        // Given
        val sessionId = "session123"
        val mockConversations = listOf(
            createMockConversation(id = 1, sessionId = sessionId),
            createMockConversation(id = 2, sessionId = sessionId)
        )

        coEvery { repository.getBySession(sessionId) } returns mockConversations

        // When
        val results = searchService.getConversationsBySession(sessionId)

        // Then
        results shouldHaveSize 2
        results.all { it.sessionId == sessionId } shouldBe true
        coVerify { repository.getBySession(sessionId) }
    }

    @Test
    fun `getAllProjects returns project list from repository`() = runTest {
        // Given
        val mockProjects = listOf("/project1", "/project2", "/project3")

        coEvery { repository.getAllProjects() } returns mockProjects

        // When
        val results = searchService.getAllProjects()

        // Then
        results shouldHaveSize 3
        results shouldBe mockProjects
        coVerify { repository.getAllProjects() }
    }

    @Test
    fun `getAllLanguages returns language list from repository`() = runTest {
        // Given
        val mockLanguages = listOf("kotlin", "java", "python")

        coEvery { repository.getAllLanguages() } returns mockLanguages

        // When
        val results = searchService.getAllLanguages()

        // Then
        results shouldHaveSize 3
        results shouldBe mockLanguages
        coVerify { repository.getAllLanguages() }
    }

    @Test
    fun `getAllModels returns model list from repository`() = runTest {
        // Given
        val mockModels = listOf("claude-sonnet-4-5", "claude-opus", "claude-haiku")

        coEvery { repository.getAllModels() } returns mockModels

        // When
        val results = searchService.getAllModels()

        // Then
        results shouldHaveSize 3
        results shouldBe mockModels
        coVerify { repository.getAllModels() }
    }

    @Test
    fun `getStatistics returns statistics from repository`() = runTest {
        // Given
        val mockStats = ConversationStatistics(
            totalConversations = 100,
            conversationsByRole = mapOf("user" to 50L, "assistant" to 50L),
            conversationsByModel = mapOf("claude-sonnet-4-5" to 100L),
            oldestConversation = Clock.System.now() - 30.days,
            newestConversation = Clock.System.now(),
            totalInputTokens = 10000,
            totalOutputTokens = 5000
        )

        coEvery { repository.getStatistics() } returns mockStats

        // When
        val results = searchService.getStatistics()

        // Then
        results.totalConversations shouldBe 100
        results.totalInputTokens shouldBe 10000
        coVerify { repository.getStatistics() }
    }

    @Test
    fun `getLatestConversationByProject returns latest conversation`() = runTest {
        // Given
        val projectPath = "/test/project"
        val mockConversation = createMockConversation(
            id = 1,
            content = "Latest conversation",
            projectPath = projectPath
        )

        coEvery { repository.getLatestConversationByProject(projectPath) } returns mockConversation

        // When
        val result = searchService.getLatestConversationByProject(projectPath)

        // Then
        result.shouldNotBeNull()
        result.conversation.projectPath shouldBe projectPath
        coVerify { repository.getLatestConversationByProject(projectPath) }
    }

    @Test
    fun `getLatestConversationByProject returns null when no conversation exists`() = runTest {
        // Given
        val projectPath = "/empty/project"

        coEvery { repository.getLatestConversationByProject(projectPath) } returns null

        // When
        val result = searchService.getLatestConversationByProject(projectPath)

        // Then
        result shouldBe null
        coVerify { repository.getLatestConversationByProject(projectPath) }
    }

    @Test
    fun `getConversationsByDateRange returns conversations in date range`() = runTest {
        // Given
        val from = Clock.System.now() - 7.days
        val to = Clock.System.now()
        val mockConversations = listOf(
            createMockConversation(id = 1, timestamp = from + 1.days),
            createMockConversation(id = 2, timestamp = from + 3.days),
            createMockConversation(id = 3, timestamp = from + 5.days)
        )

        coEvery { repository.getConversationsByDateRange(from, to, 100) } returns mockConversations

        // When
        val results = searchService.getConversationsByDateRange(from, to, 100)

        // Then
        results shouldHaveSize 3
        coVerify { repository.getConversationsByDateRange(from, to, 100) }
    }

    // Helper function to create mock conversations
    private fun createMockConversation(
        id: Int,
        content: String = "Test content",
        sessionId: String = "session1",
        projectPath: String = "/test/project",
        timestamp: Instant = Clock.System.now(),
        role: MessageRole = MessageRole.USER
    ): Conversation {
        return Conversation(
            id = id,
            sessionId = sessionId,
            projectPath = projectPath,
            timestamp = timestamp,
            role = role,
            content = content,
            metadata = null
        )
    }
}
