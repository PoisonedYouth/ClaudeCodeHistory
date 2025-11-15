package com.claudecode.history.domain

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.time.Duration.Companion.days

class SearchFiltersTest {

    @Test
    fun `hasActiveFilters returns false when no filters are set`() {
        // Given
        val filters = SearchFilters()

        // When
        val hasActiveFilters = filters.hasActiveFilters()

        // Then
        hasActiveFilters.shouldBeFalse()
    }

    @Test
    fun `hasActiveFilters returns true when projectPath is set`() {
        // Given
        val filters = SearchFilters(projectPath = "/some/path")

        // When
        val hasActiveFilters = filters.hasActiveFilters()

        // Then
        hasActiveFilters.shouldBeTrue()
    }

    @Test
    fun `hasActiveFilters returns true when dateFrom is set`() {
        // Given
        val filters = SearchFilters(dateFrom = Clock.System.now())

        // When
        val hasActiveFilters = filters.hasActiveFilters()

        // Then
        hasActiveFilters.shouldBeTrue()
    }

    @Test
    fun `hasActiveFilters returns true when dateTo is set`() {
        // Given
        val filters = SearchFilters(dateTo = Clock.System.now())

        // When
        val hasActiveFilters = filters.hasActiveFilters()

        // Then
        hasActiveFilters.shouldBeTrue()
    }

    @Test
    fun `hasActiveFilters returns true when role is set`() {
        // Given
        val filters = SearchFilters(role = MessageRole.USER)

        // When
        val hasActiveFilters = filters.hasActiveFilters()

        // Then
        hasActiveFilters.shouldBeTrue()
    }

    @Test
    fun `hasActiveFilters returns true when language is set`() {
        // Given
        val filters = SearchFilters(language = "kotlin")

        // When
        val hasActiveFilters = filters.hasActiveFilters()

        // Then
        hasActiveFilters.shouldBeTrue()
    }

    @Test
    fun `hasActiveFilters returns true when filePath is set`() {
        // Given
        val filters = SearchFilters(filePath = "Main.kt")

        // When
        val hasActiveFilters = filters.hasActiveFilters()

        // Then
        hasActiveFilters.shouldBeTrue()
    }

    @Test
    fun `hasActiveFilters returns true when model is set`() {
        // Given
        val filters = SearchFilters(model = "claude-sonnet-4-5")

        // When
        val hasActiveFilters = filters.hasActiveFilters()

        // Then
        hasActiveFilters.shouldBeTrue()
    }

    @Test
    fun `hasActiveFilters returns true when multiple filters are set`() {
        // Given
        val filters = SearchFilters(
            projectPath = "/some/path",
            dateFrom = Clock.System.now() - 7.days,
            role = MessageRole.ASSISTANT
        )

        // When
        val hasActiveFilters = filters.hasActiveFilters()

        // Then
        hasActiveFilters.shouldBeTrue()
    }

    @Test
    fun `hasActiveFilters returns true when all filters are set`() {
        // Given
        val filters = SearchFilters(
            projectPath = "/some/path",
            dateFrom = Clock.System.now() - 7.days,
            dateTo = Clock.System.now(),
            role = MessageRole.USER,
            language = "kotlin",
            filePath = "Main.kt",
            model = "claude-sonnet-4-5"
        )

        // When
        val hasActiveFilters = filters.hasActiveFilters()

        // Then
        hasActiveFilters.shouldBeTrue()
    }
}
