package com.claudecode.history.service

import com.claudecode.history.domain.MessageRole
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
        testFile = File.createTempFile("prefix", ".jsonl")
        testFile.writeText("""
            {"type":"message","message":{"role":"user","content":"Hello"},"timestamp":"2025-01-01T12:00:00Z"}
        """.trimIndent())

        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        // Session ID should be the filename without the .jsonl extension
        val expectedSessionId = testFile.name.removeSuffix(".jsonl")
        assertEquals(expectedSessionId, conversations.firstOrNull()?.sessionId)
    }

    @Test
    fun `parseConversationFile handles user messages correctly`() {
        testFile = File.createTempFile("session1", ".jsonl")
        testFile.writeText("""
            {"type":"message","message":{"role":"user","content":"What is Kotlin?"},"timestamp":"2025-01-01T12:00:00Z"}
        """.trimIndent())

        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        assertEquals(1, conversations.size)
        assertEquals(MessageRole.USER, conversations[0].role)
        assertEquals("What is Kotlin?", conversations[0].content)
    }

    @Test
    fun `parseConversationFile handles assistant messages correctly`() {
        testFile = File.createTempFile("session1", ".jsonl")
        testFile.writeText("""
            {"type":"message","message":{"role":"assistant","content":"Kotlin is a programming language."},"timestamp":"2025-01-01T12:00:00Z"}
        """.trimIndent())

        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        assertEquals(1, conversations.size)
        assertEquals(MessageRole.ASSISTANT, conversations[0].role)
        assertEquals("Kotlin is a programming language.", conversations[0].content)
    }

    @Test
    fun `parseConversationFile handles multiple messages in sequence`() {
        testFile = File.createTempFile("session1", ".jsonl")
        testFile.writeText("""
            {"type":"message","message":{"role":"user","content":"Question 1"},"timestamp":"2025-01-01T12:00:00Z"}
            {"type":"message","message":{"role":"assistant","content":"Answer 1"},"timestamp":"2025-01-01T12:01:00Z"}
            {"type":"message","message":{"role":"user","content":"Question 2"},"timestamp":"2025-01-01T12:02:00Z"}
            {"type":"message","message":{"role":"assistant","content":"Answer 2"},"timestamp":"2025-01-01T12:03:00Z"}
        """.trimIndent())

        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        assertEquals(4, conversations.size)
        assertEquals(MessageRole.USER, conversations[0].role)
        assertEquals(MessageRole.ASSISTANT, conversations[1].role)
        assertEquals(MessageRole.USER, conversations[2].role)
        assertEquals(MessageRole.ASSISTANT, conversations[3].role)
    }

    @Test
    fun `parseConversationFile assigns correct project path`() {
        testFile = File.createTempFile("session1", ".jsonl")
        val projectPath = "/home/user/my-project"
        testFile.writeText("""
            {"type":"message","message":{"role":"user","content":"Test"},"timestamp":"2025-01-01T12:00:00Z"}
        """.trimIndent())

        val conversations = parser.parseConversationFile(testFile.toPath(), projectPath)

        assertEquals(projectPath, conversations[0].projectPath)
    }

    @Test
    fun `parseConversationFile handles empty file`() {
        testFile = File.createTempFile("session1", ".jsonl")
        testFile.writeText("")

        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        assertTrue(conversations.isEmpty())
    }

    @Test
    fun `parseConversationFile handles malformed JSON gracefully`() {
        testFile = File.createTempFile("session1", ".jsonl")
        testFile.writeText("""
            {"type":"message","message":{"role":"user","content":"Valid message"},"timestamp":"2025-01-01T12:00:00Z"}
            {invalid json}
            {"type":"message","message":{"role":"assistant","content":"Another valid message"},"timestamp":"2025-01-01T12:01:00Z"}
        """.trimIndent())

        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        // Should parse valid messages and skip invalid ones
        assertEquals(2, conversations.size)
        assertEquals("Valid message", conversations[0].content)
        assertEquals("Another valid message", conversations[1].content)
    }

    @Test
    fun `parseConversationFile extracts text content from content arrays`() {
        testFile = File.createTempFile("session1", ".jsonl")
        testFile.writeText("""
            {"type":"message","message":{"role":"user","content":[{"type":"text","text":"Hello world"}]},"timestamp":"2025-01-01T12:00:00Z"}
        """.trimIndent())

        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        assertEquals(1, conversations.size)
        assertTrue(conversations[0].content.contains("Hello world"))
    }

    @Test
    fun `parseConversationFile assigns timestamps in chronological order`() {
        testFile = File.createTempFile("session1", ".jsonl")
        testFile.writeText("""
            {"type":"message","message":{"role":"user","content":"First"},"timestamp":"2025-01-01T12:00:00Z"}
            {"type":"message","message":{"role":"assistant","content":"Second"},"timestamp":"2025-01-01T12:01:00Z"}
            {"type":"message","message":{"role":"user","content":"Third"},"timestamp":"2025-01-01T12:02:00Z"}
        """.trimIndent())

        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        assertTrue(conversations[0].timestamp <= conversations[1].timestamp)
        assertTrue(conversations[1].timestamp <= conversations[2].timestamp)
    }

    @Test
    fun `parseConversationFile handles special characters in content`() {
        testFile = File.createTempFile("session1", ".jsonl")
        val specialContent = "Test with \\\"quotes\\\" and 'apostrophes' and newlines\\nand tabs\\t"
        testFile.writeText("""
            {"type":"message","message":{"role":"user","content":"$specialContent"},"timestamp":"2025-01-01T12:00:00Z"}
        """.trimIndent())

        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        assertEquals(1, conversations.size)
        assertTrue(conversations[0].content.contains("quotes"))
    }

    @Test
    fun `parseConversationFile extracts metadata from tool use`() {
        testFile = File.createTempFile("session1", ".jsonl")
        testFile.writeText("""
            {"type":"message","message":{"role":"assistant","content":[{"type":"text","text":"Reading file"},{"type":"tool_use","name":"Read","input":{"file_path":"/test/Main.kt"}}]},"timestamp":"2025-01-01T12:00:00Z"}
        """.trimIndent())

        val conversations = parser.parseConversationFile(testFile.toPath(), "/test/project")

        assertEquals(1, conversations.size)
        assertNotNull(conversations[0].metadata)
        assertEquals("kotlin", conversations[0].metadata?.language)
        assertTrue(conversations[0].metadata?.filePaths?.contains("/test/Main.kt") ?: false)
    }
}
