package com.claudecode.history.service

import com.claudecode.history.domain.MessageRole
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain as shouldContainElement
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import kotlin.test.*

class ClaudeConversationParserTest {

    private lateinit var parser: ClaudeConversationParser
    private lateinit var testFile: File

    @BeforeTest
    fun setup() {
        parser = ClaudeConversationParser()
    }

    @AfterTest
    fun cleanup() {
        if (::testFile.isInitialized && testFile.exists()) {
            testFile.delete()
        }
    }

    @Test
    fun `parseConversationFile extracts session ID from filename`() {
        // Given
        testFile = File.createTempFile("prefix", ".jsonl")
        testFile.writeText("""
            {"type":"message","message":{"role":"user","content":"Hello"},"timestamp":"2025-01-01T12:00:00Z"}
        """.trimIndent())

        // When
        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        // Then
        val expectedSessionId = testFile.name.removeSuffix(".jsonl")
        conversations.first().sessionId shouldBe expectedSessionId
    }

    @Test
    fun `parseConversationFile handles user messages correctly`() {
        // Given
        testFile = File.createTempFile("session1", ".jsonl")
        testFile.writeText("""
            {"type":"message","message":{"role":"user","content":"What is Kotlin?"},"timestamp":"2025-01-01T12:00:00Z"}
        """.trimIndent())

        // When
        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        // Then
        conversations.size shouldBe 1
        conversations[0].role shouldBe MessageRole.USER
        conversations[0].content shouldBe "What is Kotlin?"
    }

    @Test
    fun `parseConversationFile handles assistant messages correctly`() {
        // Given
        testFile = File.createTempFile("session1", ".jsonl")
        testFile.writeText("""
            {"type":"message","message":{"role":"assistant","content":"Kotlin is a programming language."},"timestamp":"2025-01-01T12:00:00Z"}
        """.trimIndent())

        // When
        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        // Then
        conversations.size shouldBe 1
        conversations[0].role shouldBe MessageRole.ASSISTANT
        conversations[0].content shouldBe "Kotlin is a programming language."
    }

    @Test
    fun `parseConversationFile handles multiple messages in sequence`() {
        // Given
        testFile = File.createTempFile("session1", ".jsonl")
        testFile.writeText("""
            {"type":"message","message":{"role":"user","content":"Question 1"},"timestamp":"2025-01-01T12:00:00Z"}
            {"type":"message","message":{"role":"assistant","content":"Answer 1"},"timestamp":"2025-01-01T12:01:00Z"}
            {"type":"message","message":{"role":"user","content":"Question 2"},"timestamp":"2025-01-01T12:02:00Z"}
            {"type":"message","message":{"role":"assistant","content":"Answer 2"},"timestamp":"2025-01-01T12:03:00Z"}
        """.trimIndent())

        // When
        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        // Then
        conversations.size shouldBe 4
        conversations[0].role shouldBe MessageRole.USER
        conversations[1].role shouldBe MessageRole.ASSISTANT
        conversations[2].role shouldBe MessageRole.USER
        conversations[3].role shouldBe MessageRole.ASSISTANT
    }

    @Test
    fun `parseConversationFile assigns correct project path`() {
        // Given
        testFile = File.createTempFile("session1", ".jsonl")
        val projectPath = "/home/user/my-project"
        testFile.writeText("""
            {"type":"message","message":{"role":"user","content":"Test"},"timestamp":"2025-01-01T12:00:00Z"}
        """.trimIndent())

        // When
        val conversations = parser.parseConversationFile(testFile.toPath(), projectPath)

        // Then
        conversations[0].projectPath shouldBe projectPath
    }

    @Test
    fun `parseConversationFile handles empty file`() {
        // Given
        testFile = File.createTempFile("session1", ".jsonl")
        testFile.writeText("")

        // When
        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        // Then
        conversations.shouldBeEmpty()
    }

    @Test
    fun `parseConversationFile handles malformed JSON gracefully`() {
        // Given
        testFile = File.createTempFile("session1", ".jsonl")
        testFile.writeText("""
            {"type":"message","message":{"role":"user","content":"Valid message"},"timestamp":"2025-01-01T12:00:00Z"}
            {invalid json}
            {"type":"message","message":{"role":"assistant","content":"Another valid message"},"timestamp":"2025-01-01T12:01:00Z"}
        """.trimIndent())

        // When
        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        // Then
        conversations.size shouldBe 2
        conversations[0].content shouldBe "Valid message"
        conversations[1].content shouldBe "Another valid message"
    }

    @Test
    fun `parseConversationFile extracts text content from content arrays`() {
        // Given
        testFile = File.createTempFile("session1", ".jsonl")
        testFile.writeText("""
            {"type":"message","message":{"role":"user","content":[{"type":"text","text":"Hello world"}]},"timestamp":"2025-01-01T12:00:00Z"}
        """.trimIndent())

        // When
        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        // Then
        conversations.size shouldBe 1
        conversations[0].content shouldContain "Hello world"
    }

    @Test
    fun `parseConversationFile assigns timestamps in chronological order`() {
        // Given
        testFile = File.createTempFile("session1", ".jsonl")
        testFile.writeText("""
            {"type":"message","message":{"role":"user","content":"First"},"timestamp":"2025-01-01T12:00:00Z"}
            {"type":"message","message":{"role":"assistant","content":"Second"},"timestamp":"2025-01-01T12:01:00Z"}
            {"type":"message","message":{"role":"user","content":"Third"},"timestamp":"2025-01-01T12:02:00Z"}
        """.trimIndent())

        // When
        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        // Then
        (conversations[0].timestamp <= conversations[1].timestamp).shouldBeTrue()
        (conversations[1].timestamp <= conversations[2].timestamp).shouldBeTrue()
    }

    @Test
    fun `parseConversationFile handles special characters in content`() {
        // Given
        testFile = File.createTempFile("session1", ".jsonl")
        val specialContent = "Test with \\\"quotes\\\" and 'apostrophes' and newlines\\nand tabs\\t"
        testFile.writeText("""
            {"type":"message","message":{"role":"user","content":"$specialContent"},"timestamp":"2025-01-01T12:00:00Z"}
        """.trimIndent())

        // When
        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        // Then
        conversations.size shouldBe 1
        conversations[0].content shouldContain "quotes"
    }

    @Test
    fun `parseConversationFile extracts metadata from tool use`() {
        // Given
        testFile = File.createTempFile("session1", ".jsonl")
        testFile.writeText("""
            {"type":"message","message":{"role":"assistant","content":[{"type":"text","text":"Reading file"},{"type":"tool_use","name":"Read","input":{"file_path":"/test/Main.kt"}}]},"timestamp":"2025-01-01T12:00:00Z"}
        """.trimIndent())

        // When
        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        // Then
        conversations.size shouldBe 1
        val metadata = conversations[0].metadata
        metadata.shouldNotBeNull()
        metadata.language shouldBe "kotlin"
        metadata.filePaths shouldContainElement "/test/Main.kt"
    }
}
