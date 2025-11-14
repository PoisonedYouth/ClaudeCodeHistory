package com.claudecode.history.domain

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class SearchFiltersTest {

    @Test
    fun `hasActiveFilters returns false when no filters are set`() {
        val filters = SearchFilters()
        assertFalse(filters.hasActiveFilters())
    }

    @Test
    fun `hasActiveFilters returns true when projectPath is set`() {
        val filters = SearchFilters(projectPath = "/some/path")
        assertTrue(filters.hasActiveFilters())
    }

    @Test
    fun `hasActiveFilters returns true when dateFrom is set`() {
        val filters = SearchFilters(dateFrom = Clock.System.now())
        assertTrue(filters.hasActiveFilters())
    }

    @Test
    fun `hasActiveFilters returns true when dateTo is set`() {
        val filters = SearchFilters(dateTo = Clock.System.now())
        assertTrue(filters.hasActiveFilters())
    }

    @Test
    fun `hasActiveFilters returns true when role is set`() {
        val filters = SearchFilters(role = MessageRole.USER)
        assertTrue(filters.hasActiveFilters())
    }

    @Test
    fun `hasActiveFilters returns true when language is set`() {
        val filters = SearchFilters(language = "kotlin")
        assertTrue(filters.hasActiveFilters())
    }

    @Test
    fun `hasActiveFilters returns true when filePath is set`() {
        val filters = SearchFilters(filePath = "Main.kt")
        assertTrue(filters.hasActiveFilters())
    }

    @Test
    fun `hasActiveFilters returns true when multiple filters are set`() {
        val filters = SearchFilters(
            projectPath = "/some/path",
            dateFrom = Clock.System.now() - 7.days,
            role = MessageRole.ASSISTANT
        )
        assertTrue(filters.hasActiveFilters())
    }
}
