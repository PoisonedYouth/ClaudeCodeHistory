package com.claudecode.history.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

class VectorUtilsTest {

    @Test
    fun `normalize returns unit vector for regular vector`() {
        // Given
        val vector = floatArrayOf(3.0f, 4.0f)

        // When
        val normalized = VectorUtils.normalize(vector)

        // Then
        normalized[0] shouldBeApproximately 0.6f
        normalized[1] shouldBeApproximately 0.8f

        val magnitude = kotlin.math.sqrt(normalized.sumOf { it * it.toDouble() }).toFloat()
        magnitude shouldBeApproximately 1.0f
    }

    @Test
    fun `normalize handles zero vector gracefully`() {
        // Given
        val zeroVector = floatArrayOf(0.0f, 0.0f, 0.0f)

        // When
        val normalized = VectorUtils.normalize(zeroVector)

        // Then
        normalized shouldBe zeroVector
    }

    @Test
    fun `normalize works with single dimension vector`() {
        // Given
        val vector = floatArrayOf(5.0f)

        // When
        val normalized = VectorUtils.normalize(vector)

        // Then
        normalized[0] shouldBeApproximately 1.0f
    }

    @Test
    fun `normalize works with high dimensional vectors`() {
        // Given
        val vector = FloatArray(768) { 1.0f }

        // When
        val normalized = VectorUtils.normalize(vector)

        // Then
        val magnitude = kotlin.math.sqrt(normalized.sumOf { it * it.toDouble() }).toFloat()
        magnitude shouldBeApproximately 1.0f

        val expected = 1.0f / kotlin.math.sqrt(768.0).toFloat()
        normalized.forEach { component ->
            component shouldBeApproximately expected
        }
    }

    @Test
    fun `cosineSimilarity returns 1 for identical vectors`() {
        // Given
        val vec1 = floatArrayOf(1.0f, 2.0f, 3.0f)
        val vec2 = floatArrayOf(1.0f, 2.0f, 3.0f)

        // When
        val similarity = VectorUtils.cosineSimilarity(vec1, vec2)

        // Then
        similarity shouldBeApproximately 1.0
    }

    @Test
    fun `cosineSimilarity returns 0 for orthogonal vectors`() {
        // Given
        val vec1 = floatArrayOf(1.0f, 0.0f)
        val vec2 = floatArrayOf(0.0f, 1.0f)

        // When
        val similarity = VectorUtils.cosineSimilarity(vec1, vec2)

        // Then
        similarity shouldBeApproximately 0.0
    }

    @Test
    fun `cosineSimilarity returns -1 for opposite vectors`() {
        // Given
        val vec1 = floatArrayOf(1.0f, 2.0f, 3.0f)
        val vec2 = floatArrayOf(-1.0f, -2.0f, -3.0f)

        // When
        val similarity = VectorUtils.cosineSimilarity(vec1, vec2)

        // Then
        similarity shouldBeApproximately (-1.0)
    }

    @Test
    fun `cosineSimilarity handles zero vectors`() {
        // Given
        val vec1 = floatArrayOf(0.0f, 0.0f, 0.0f)
        val vec2 = floatArrayOf(1.0f, 2.0f, 3.0f)

        // When
        val similarity = VectorUtils.cosineSimilarity(vec1, vec2)

        // Then
        similarity shouldBeApproximately 0.0
    }

    @Test
    fun `cosineSimilarity throws exception for dimension mismatch`() {
        // Given
        val vec1 = floatArrayOf(1.0f, 2.0f)
        val vec2 = floatArrayOf(1.0f, 2.0f, 3.0f)

        // When & Then
        val exception = shouldThrow<IllegalArgumentException> {
            VectorUtils.cosineSimilarity(vec1, vec2)
        }
        exception.message shouldContain "same dimensions"
    }

    @Test
    fun `cosineSimilarity works with high dimensional vectors`() {
        // Given
        val vec1 = FloatArray(768) { it.toFloat() }
        val vec2 = FloatArray(768) { it.toFloat() * 2 }

        // When
        val similarity = VectorUtils.cosineSimilarity(vec1, vec2)

        // Then
        similarity.shouldBeApproximately(1.0, 0.01)
    }

    @Test
    fun `floatArrayToBytes and bytesToFloatArray roundtrip correctly`() {
        // Given
        val original = floatArrayOf(1.5f, 2.5f, 3.5f, -4.5f, 0.0f)

        // When
        val bytes = VectorUtils.floatArrayToBytes(original)
        val restored = VectorUtils.bytesToFloatArray(bytes)

        // Then
        restored.size shouldBe original.size
        original.forEachIndexed { index, value ->
            restored[index] shouldBeApproximately value
        }
    }

    @Test
    fun `floatArrayToBytes produces correct byte size`() {
        // Given
        val vector = FloatArray(768) { it.toFloat() }

        // When
        val bytes = VectorUtils.floatArrayToBytes(vector)

        // Then
        bytes.size shouldBe 768 * 4
    }

    @Test
    fun `bytesToFloatArray handles empty array`() {
        // Given
        val emptyBytes = ByteArray(0)

        // When
        val floats = VectorUtils.bytesToFloatArray(emptyBytes)

        // Then
        floats.size shouldBe 0
    }

    @Test
    fun `floatArrayToBytes handles single float`() {
        // Given
        val single = floatArrayOf(42.0f)

        // When
        val bytes = VectorUtils.floatArrayToBytes(single)
        val restored = VectorUtils.bytesToFloatArray(bytes)

        // Then
        restored.size shouldBe 1
        restored[0] shouldBeApproximately 42.0f
    }

    @Test
    fun `euclideanDistance returns 0 for identical vectors`() {
        // Given
        val vec1 = floatArrayOf(1.0f, 2.0f, 3.0f)
        val vec2 = floatArrayOf(1.0f, 2.0f, 3.0f)

        // When
        val distance = VectorUtils.euclideanDistance(vec1, vec2)

        // Then
        distance shouldBeApproximately 0.0
    }

    @Test
    fun `euclideanDistance calculates correctly for 2D vectors`() {
        // Given
        val vec1 = floatArrayOf(0.0f, 0.0f)
        val vec2 = floatArrayOf(3.0f, 4.0f)

        // When
        val distance = VectorUtils.euclideanDistance(vec1, vec2)

        // Then
        distance shouldBeApproximately 5.0
    }

    @Test
    fun `euclideanDistance is symmetric`() {
        // Given
        val vec1 = floatArrayOf(1.0f, 2.0f, 3.0f)
        val vec2 = floatArrayOf(4.0f, 5.0f, 6.0f)

        // When
        val distance1 = VectorUtils.euclideanDistance(vec1, vec2)
        val distance2 = VectorUtils.euclideanDistance(vec2, vec1)

        // Then
        distance1 shouldBeApproximately distance2
    }

    @Test
    fun `euclideanDistance throws exception for dimension mismatch`() {
        // Given
        val vec1 = floatArrayOf(1.0f, 2.0f)
        val vec2 = floatArrayOf(1.0f, 2.0f, 3.0f)

        // When & Then
        val exception = shouldThrow<IllegalArgumentException> {
            VectorUtils.euclideanDistance(vec1, vec2)
        }
        exception.message shouldContain "same dimensions"
    }

    @Test
    fun `euclideanDistance works with high dimensional vectors`() {
        // Given
        val vec1 = FloatArray(768) { 0.0f }
        val vec2 = FloatArray(768) { 1.0f }

        // When
        val distance = VectorUtils.euclideanDistance(vec1, vec2)

        // Then
        val expected = kotlin.math.sqrt(768.0)
        distance.shouldBeApproximately(expected, 0.001)
    }

    @Test
    fun `normalize preserves direction`() {
        // Given
        val vector = floatArrayOf(10.0f, 20.0f, 30.0f)

        // When
        val normalized = VectorUtils.normalize(vector)

        // Then
        (normalized[0].toDouble()) shouldBeLessThan normalized[1].toDouble()
        (normalized[1].toDouble()) shouldBeLessThan normalized[2].toDouble()
    }

    @Test
    fun `cosineSimilarity matches manual calculation`() {
        // Given
        val vec1 = floatArrayOf(2.0f, 1.0f)
        val vec2 = floatArrayOf(1.0f, 2.0f)

        // When
        val similarity = VectorUtils.cosineSimilarity(vec1, vec2)

        // Then
        // Manual: dot=4, mag1=sqrt(5), mag2=sqrt(5), sim=4/5=0.8
        similarity shouldBeApproximately 0.8
    }
}

// Helper for approximate float/double equality
private infix fun Float.shouldBeApproximately(expected: Float) {
    val tolerance = 0.0001f
    val diff = kotlin.math.abs(this - expected)
    if (diff > tolerance) {
        throw AssertionError("Expected $this to be $expected ± $tolerance, but difference was $diff")
    }
}

private fun Float.shouldBeApproximately(expected: Float, tolerance: Float) {
    val diff = kotlin.math.abs(this - expected)
    if (diff > tolerance) {
        throw AssertionError("Expected $this to be $expected ± $tolerance, but difference was $diff")
    }
}

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
