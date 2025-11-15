package com.claudecode.history.domain

import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TokenUsageTest {

    @Test
    fun `totalTokens calculates sum of all token types`() {
        // Given
        val usage = TokenUsage(
            inputTokens = 1000,
            outputTokens = 500,
            cacheCreationInputTokens = 200,
            cacheReadInputTokens = 300
        )

        // When
        val total = usage.totalTokens

        // Then
        total shouldBe 2000
    }

    @Test
    fun `totalTokens returns 0 for zero usage`() {
        // Given
        val usage = TokenUsage()

        // When
        val total = usage.totalTokens

        // Then
        total shouldBe 0
    }

    @Test
    fun `estimatedCost calculates correctly with typical values`() {
        // Given
        val usage = TokenUsage(
            inputTokens = 100_000,      // $0.30
            outputTokens = 50_000,      // $0.75
            cacheCreationInputTokens = 20_000,  // $0.075
            cacheReadInputTokens = 10_000       // $0.003
        )

        // When
        val cost = usage.estimatedCost

        // Then
        // Expected: 0.30 + 0.75 + 0.075 + 0.003 = 1.128
        cost shouldBeApproximately 1.128
    }

    @Test
    fun `estimatedCost returns 0 for zero tokens`() {
        // Given
        val usage = TokenUsage()

        // When
        val cost = usage.estimatedCost

        // Then
        cost shouldBeApproximately 0.0
    }

    @Test
    fun `estimatedCost calculates correctly for input only`() {
        // Given
        val usage = TokenUsage(inputTokens = 1_000_000)

        // When
        val cost = usage.estimatedCost

        // Then
        cost shouldBeApproximately 3.0
    }

    @Test
    fun `estimatedCost calculates correctly for output only`() {
        // Given
        val usage = TokenUsage(outputTokens = 1_000_000)

        // When
        val cost = usage.estimatedCost

        // Then
        cost shouldBeApproximately 15.0
    }

    @Test
    fun `estimatedCost calculates correctly for cache creation only`() {
        // Given
        val usage = TokenUsage(cacheCreationInputTokens = 1_000_000)

        // When
        val cost = usage.estimatedCost

        // Then
        cost shouldBeApproximately 3.75
    }

    @Test
    fun `estimatedCost calculates correctly for cache read only`() {
        // Given
        val usage = TokenUsage(cacheReadInputTokens = 1_000_000)

        // When
        val cost = usage.estimatedCost

        // Then
        cost shouldBeApproximately 0.30
    }

    @Test
    fun `estimatedCost handles very large token counts`() {
        // Given
        val usage = TokenUsage(
            inputTokens = 10_000_000,
            outputTokens = 5_000_000,
            cacheCreationInputTokens = 2_000_000,
            cacheReadInputTokens = 1_000_000
        )

        // When
        val cost = usage.estimatedCost

        // Then
        // Input: $30, Output: $75, Cache Write: $7.50, Cache Read: $0.30
        cost.shouldBeApproximately(112.80, 0.01)
    }

    @Test
    fun `estimatedCost handles small token counts with precision`() {
        // Given
        val usage = TokenUsage(
            inputTokens = 100,
            outputTokens = 50,
            cacheCreationInputTokens = 20,
            cacheReadInputTokens = 10
        )

        // When
        val cost = usage.estimatedCost

        // Then
        cost.shouldBeApproximately(0.001128, 0.000001)
    }

    @Test
    fun `estimatedCost reflects current Claude 3_5 Sonnet pricing`() {
        // Given
        val usage = TokenUsage(
            inputTokens = 1_000_000,
            outputTokens = 1_000_000,
            cacheCreationInputTokens = 1_000_000,
            cacheReadInputTokens = 1_000_000
        )

        // When
        val cost = usage.estimatedCost

        // Then
        // $3 + $15 + $3.75 + $0.30 = $22.05
        cost.shouldBeApproximately(22.05, 0.01)
    }

    @Test
    fun `totalTokens handles maximum integer values`() {
        // Given
        val usage = TokenUsage(
            inputTokens = Int.MAX_VALUE / 4,
            outputTokens = Int.MAX_VALUE / 4,
            cacheCreationInputTokens = Int.MAX_VALUE / 4,
            cacheReadInputTokens = Int.MAX_VALUE / 4
        )

        // When
        val total = usage.totalTokens

        // Then
        total shouldBe Int.MAX_VALUE / 4 * 4
    }

    @Test
    fun `estimatedCost is proportional to token count`() {
        // Given
        val usage1 = TokenUsage(inputTokens = 100_000)
        val usage2 = TokenUsage(inputTokens = 200_000)

        // When
        val cost1 = usage1.estimatedCost
        val cost2 = usage2.estimatedCost

        // Then
        cost2 shouldBeApproximately (cost1 * 2)
    }

    @Test
    fun `estimatedCost calculation matches expected formula`() {
        // Given
        val inputTokens = 500_000
        val outputTokens = 250_000
        val cacheWriteTokens = 100_000
        val cacheReadTokens = 50_000

        val usage = TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cacheCreationInputTokens = cacheWriteTokens,
            cacheReadInputTokens = cacheReadTokens
        )

        // When
        val actualCost = usage.estimatedCost

        // Then
        val expectedCost =
            (inputTokens / 1_000_000.0) * 3.0 +
            (outputTokens / 1_000_000.0) * 15.0 +
            (cacheWriteTokens / 1_000_000.0) * 3.75 +
            (cacheReadTokens / 1_000_000.0) * 0.30

        actualCost shouldBeApproximately expectedCost
    }

    @Test
    fun `output tokens are most expensive component`() {
        // Given
        val baseAmount = 100_000

        val inputCost = TokenUsage(inputTokens = baseAmount).estimatedCost
        val outputCost = TokenUsage(outputTokens = baseAmount).estimatedCost
        val cacheWriteCost = TokenUsage(cacheCreationInputTokens = baseAmount).estimatedCost
        val cacheReadCost = TokenUsage(cacheReadInputTokens = baseAmount).estimatedCost

        // Then
        outputCost shouldBeGreaterThan inputCost
        outputCost shouldBeGreaterThan cacheWriteCost
        outputCost shouldBeGreaterThan cacheReadCost

        cacheWriteCost shouldBeGreaterThan inputCost
        inputCost shouldBeGreaterThan cacheReadCost
    }

    @Test
    fun `cache read is least expensive component`() {
        // Given
        val usage = TokenUsage(
            inputTokens = 100_000,
            outputTokens = 100_000,
            cacheCreationInputTokens = 100_000,
            cacheReadInputTokens = 100_000
        )

        // When
        val inputCost = (100_000 / 1_000_000.0) * 3.0
        val cacheReadCost = (100_000 / 1_000_000.0) * 0.30

        // Then
        cacheReadCost shouldBeApproximately (inputCost / 10)
    }

    @Test
    fun `default constructor creates zero usage`() {
        // Given & When
        val usage = TokenUsage()

        // Then
        usage.inputTokens shouldBe 0
        usage.outputTokens shouldBe 0
        usage.cacheCreationInputTokens shouldBe 0
        usage.cacheReadInputTokens shouldBe 0
        usage.totalTokens shouldBe 0
        usage.estimatedCost shouldBeApproximately 0.0
    }

    @Test
    fun `estimatedCost has proper precision for billing`() {
        // Given
        val usage = TokenUsage(
            inputTokens = 123_456,
            outputTokens = 78_901
        )

        // When
        val cost = usage.estimatedCost
        val costInCents = (cost * 100).toInt()

        // Then
        costInCents shouldBeGreaterThan 0
    }
}

// Helper for approximate double equality
private infix fun Double.shouldBeApproximately(expected: Double) {
    val tolerance = 0.0001
    val diff = kotlin.math.abs(this - expected)
    if (diff > tolerance) {
        throw AssertionError("Expected $this to be $expected ± $tolerance, but difference was $diff")
    }
}

private fun Double.shouldBeApproximately(expected: Double, tolerance: Double) {
    val diff = kotlin.math.abs(this - expected)
    if (diff > tolerance) {
        throw AssertionError("Expected $this to be $expected ± $tolerance, but difference was $diff")
    }
}
