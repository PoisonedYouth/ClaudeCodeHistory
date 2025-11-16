package com.claudecode.history.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.Test

class ValidationUtilsTest {

    // File validation tests
    @Test
    fun `validateFile accepts valid readable file`() {
        // Given
        val tempFile = createTempFile(suffix = ".txt")
        tempFile.writeText("test content")

        try {
            // When & Then - should not throw
            ValidationUtils.validateFile(tempFile)
        } finally {
            tempFile.deleteIfExists()
        }
    }

    @Test
    fun `validateFile throws ValidationException for non-existent file`() {
        // Given
        val nonExistent = createTempFile()
        nonExistent.deleteIfExists()

        // When & Then
        val exception = shouldThrow<ValidationException> {
            ValidationUtils.validateFile(nonExistent)
        }
        exception.message shouldContain "does not exist"
    }

    @Test
    fun `validateFile throws ValidationException for oversized file`() {
        // Given
        val tempFile = createTempFile()
        // Create content larger than MAX_FILE_SIZE_BYTES
        val largeContent = "x".repeat(ValidationUtils.MAX_FILE_SIZE_BYTES + 1000)
        tempFile.writeText(largeContent)

        try {
            // When & Then
            val exception = shouldThrow<ValidationException> {
                ValidationUtils.validateFile(tempFile)
            }
            exception.message shouldContain "File too large"
        } finally {
            tempFile.deleteIfExists()
        }
    }

    // Search query validation tests
    @Test
    fun `validateSearchQuery accepts valid query`() {
        // Given
        val query = "kotlin function test"

        // When
        val result = ValidationUtils.validateSearchQuery(query)

        // Then
        result shouldBe query
    }

    @Test
    fun `validateSearchQuery removes control characters`() {
        // Given
        val queryWithControlChars = "test\u0000\u0001\u0007query"

        // When
        val result = ValidationUtils.validateSearchQuery(queryWithControlChars)

        // Then
        result shouldBe "testquery"
    }

    @Test
    fun `validateSearchQuery throws ValidationException for too long query`() {
        // Given
        val tooLongQuery = "x".repeat(ValidationUtils.MAX_SEARCH_QUERY_LENGTH + 1)

        // When & Then
        val exception = shouldThrow<ValidationException> {
            ValidationUtils.validateSearchQuery(tooLongQuery)
        }
        exception.message shouldContain "Search query too long"
    }

    @Test
    fun `validateSearchQuery preserves newlines and tabs`() {
        // Given
        val query = "test\nquery\twith\twhitespace"

        // When
        val result = ValidationUtils.validateSearchQuery(query)

        // Then
        result shouldBe query
    }

    // Project path validation tests
    @Test
    fun `validateProjectPath accepts valid path`() {
        // Given
        val path = "/home/user/project"

        // When
        val result = ValidationUtils.validateProjectPath(path)

        // Then
        result shouldBe path
    }

    @Test
    fun `validateProjectPath trims whitespace`() {
        // Given
        val path = "  /home/user/project  "

        // When
        val result = ValidationUtils.validateProjectPath(path)

        // Then
        result shouldBe "/home/user/project"
    }

    @Test
    fun `validateProjectPath throws ValidationException for blank path`() {
        // When & Then
        val exception = shouldThrow<ValidationException> {
            ValidationUtils.validateProjectPath("   ")
        }
        exception.message shouldContain "cannot be blank"
    }

    @Test
    fun `validateProjectPath throws ValidationException for too long path`() {
        // Given
        val tooLongPath = "x".repeat(ValidationUtils.MAX_PROJECT_PATH_LENGTH + 1)

        // When & Then
        val exception = shouldThrow<ValidationException> {
            ValidationUtils.validateProjectPath(tooLongPath)
        }
        exception.message shouldContain "Project path too long"
    }

    @Test
    fun `validateProjectPath throws ValidationException for null byte`() {
        // Given
        val pathWithNullByte = "/home/user\u0000/project"

        // When & Then
        val exception = shouldThrow<ValidationException> {
            ValidationUtils.validateProjectPath(pathWithNullByte)
        }
        exception.message shouldContain "null byte"
    }

    // Session ID validation tests
    @Test
    fun `validateSessionId accepts valid alphanumeric session ID`() {
        // Given
        val sessionId = "chat_12345-abcdef"

        // When
        val result = ValidationUtils.validateSessionId(sessionId)

        // Then
        result shouldBe sessionId
    }

    @Test
    fun `validateSessionId throws ValidationException for blank session ID`() {
        // When & Then
        val exception = shouldThrow<ValidationException> {
            ValidationUtils.validateSessionId("")
        }
        exception.message shouldContain "cannot be blank"
    }

    @Test
    fun `validateSessionId throws ValidationException for too long session ID`() {
        // Given
        val tooLongId = "x".repeat(ValidationUtils.MAX_SESSION_ID_LENGTH + 1)

        // When & Then
        val exception = shouldThrow<ValidationException> {
            ValidationUtils.validateSessionId(tooLongId)
        }
        exception.message shouldContain "Session ID too long"
    }

    @Test
    fun `validateSessionId throws ValidationException for invalid characters`() {
        // Given
        val invalidId = "session/id@special"

        // When & Then
        val exception = shouldThrow<ValidationException> {
            ValidationUtils.validateSessionId(invalidId)
        }
        exception.message shouldContain "Invalid session ID format"
    }

    @Test
    fun `validateSessionId accepts underscores and hyphens`() {
        // Given
        val sessionId = "chat-123_abc-def_456"

        // When
        val result = ValidationUtils.validateSessionId(sessionId)

        // Then
        result shouldBe sessionId
    }

    // Content length validation tests
    @Test
    fun `validateContentLength accepts valid content`() {
        // Given
        val content = "This is valid content"

        // When
        val result = ValidationUtils.validateContentLength(content, "Test content")

        // Then
        result shouldBe content
    }

    @Test
    fun `validateContentLength throws ValidationException for too long content`() {
        // Given
        val tooLongContent = "x".repeat(ValidationUtils.MAX_CONTENT_LENGTH + 1)

        // When & Then
        val exception = shouldThrow<ValidationException> {
            ValidationUtils.validateContentLength(tooLongContent, "Test content")
        }
        exception.message shouldContain "Test content too long"
    }

    // Embedding text validation tests
    @Test
    fun `validateEmbeddingText accepts valid text`() {
        // Given
        val text = "This is some text for embedding generation"

        // When
        val result = ValidationUtils.validateEmbeddingText(text)

        // Then
        result shouldBe text
    }

    @Test
    fun `validateEmbeddingText trims whitespace`() {
        // Given
        val text = "   This is some text   "

        // When
        val result = ValidationUtils.validateEmbeddingText(text)

        // Then
        result shouldBe "This is some text"
    }

    @Test
    fun `validateEmbeddingText throws ValidationException for too short text`() {
        // Given
        val tooShort = "short"

        // When & Then
        val exception = shouldThrow<ValidationException> {
            ValidationUtils.validateEmbeddingText(tooShort)
        }
        exception.message shouldContain "Text too short for embedding"
    }

    @Test
    fun `validateEmbeddingText throws ValidationException for too long text`() {
        // Given
        val tooLong = "x".repeat(ValidationUtils.MAX_EMBEDDING_TEXT_LENGTH + 1)

        // When & Then
        val exception = shouldThrow<ValidationException> {
            ValidationUtils.validateEmbeddingText(tooLong)
        }
        exception.message shouldContain "Text too long for embedding"
    }

    @Test
    fun `validateEmbeddingText accepts text at minimum length boundary`() {
        // Given
        val minLengthText = "x".repeat(ValidationUtils.MIN_EMBEDDING_TEXT_LENGTH)

        // When
        val result = ValidationUtils.validateEmbeddingText(minLengthText)

        // Then
        result shouldBe minLengthText
    }

    @Test
    fun `validateEmbeddingText accepts text at maximum length boundary`() {
        // Given
        val maxLengthText = "x".repeat(ValidationUtils.MAX_EMBEDDING_TEXT_LENGTH)

        // When
        val result = ValidationUtils.validateEmbeddingText(maxLengthText)

        // Then
        result shouldBe maxLengthText
    }

    // Line length validation tests
    @Test
    fun `validateLineLength accepts valid line`() {
        // Given
        val line = "This is a normal line"

        // When & Then - should not throw
        ValidationUtils.validateLineLength(line, 1)
    }

    @Test
    fun `validateLineLength throws ValidationException for too long line`() {
        // Given
        val tooLongLine = "x".repeat(ValidationUtils.MAX_LINE_LENGTH + 1)

        // When & Then
        val exception = shouldThrow<ValidationException> {
            ValidationUtils.validateLineLength(tooLongLine, 42)
        }
        exception.message shouldContain "Line 42 too long"
    }

    // File path sanitization tests
    @Test
    fun `sanitizeFilePath removes null bytes`() {
        // Given
        val pathWithNullByte = "/home/user\u0000/file.txt"

        // When
        val result = ValidationUtils.sanitizeFilePath(pathWithNullByte)

        // Then
        result shouldBe "/home/user/file.txt"
    }

    @Test
    fun `sanitizeFilePath normalizes path separators`() {
        // Given
        val windowsPath = "C:\\Users\\user\\file.txt"

        // When
        val result = ValidationUtils.sanitizeFilePath(windowsPath)

        // Then
        result shouldBe "C:/Users/user/file.txt"
    }

    @Test
    fun `sanitizeFilePath removes path traversal attempts`() {
        // Given
        val maliciousPath = "/home/user/../../../etc/passwd"

        // When
        val result = ValidationUtils.sanitizeFilePath(maliciousPath)

        // Then
        result shouldBe "/home/user/etc/passwd"
    }

    @Test
    fun `sanitizeFilePath handles multiple traversal patterns`() {
        // Given
        val maliciousPath = "/home/../user/../../file.txt"

        // When
        val result = ValidationUtils.sanitizeFilePath(maliciousPath)

        // Then
        result shouldBe "/home/user/file.txt"
    }

    // Range validation tests
    @Test
    fun `validateRange accepts value within range`() {
        // When & Then - should not throw
        ValidationUtils.validateRange(50, 0, 100, "Test value")
    }

    @Test
    fun `validateRange accepts value at min boundary`() {
        // When & Then - should not throw
        ValidationUtils.validateRange(0, 0, 100, "Test value")
    }

    @Test
    fun `validateRange accepts value at max boundary`() {
        // When & Then - should not throw
        ValidationUtils.validateRange(100, 0, 100, "Test value")
    }

    @Test
    fun `validateRange throws ValidationException for value below min`() {
        // When & Then
        val exception = shouldThrow<ValidationException> {
            ValidationUtils.validateRange(-1, 0, 100, "Test value")
        }
        exception.message shouldContain "out of range"
    }

    @Test
    fun `validateRange throws ValidationException for value above max`() {
        // When & Then
        val exception = shouldThrow<ValidationException> {
            ValidationUtils.validateRange(101, 0, 100, "Test value")
        }
        exception.message shouldContain "out of range"
    }

    // Positive validation tests
    @Test
    fun `validatePositive accepts positive value`() {
        // When & Then - should not throw
        ValidationUtils.validatePositive(42, "Test value")
    }

    @Test
    fun `validatePositive accepts zero`() {
        // When & Then - should not throw
        ValidationUtils.validatePositive(0, "Test value")
    }

    @Test
    fun `validatePositive throws ValidationException for negative value`() {
        // When & Then
        val exception = shouldThrow<ValidationException> {
            ValidationUtils.validatePositive(-1, "Test value")
        }
        exception.message shouldContain "must be positive"
    }
}
