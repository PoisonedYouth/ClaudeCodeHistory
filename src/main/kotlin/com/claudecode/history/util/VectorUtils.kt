package com.claudecode.history.util

import java.nio.ByteBuffer
import kotlin.math.sqrt

/**
 * Utility functions for vector operations used in semantic search
 */
object VectorUtils {

    /**
     * Normalize a vector to unit length for cosine similarity calculations
     */
    fun normalize(vector: FloatArray): FloatArray {
        val magnitude = sqrt(vector.sumOf { it * it.toDouble() }).toFloat()
        if (magnitude == 0f) return vector
        return vector.map { it / magnitude }.toFloatArray()
    }

    /**
     * Calculate cosine similarity between two normalized vectors
     * Returns a value between -1 and 1, where 1 means identical direction
     */
    fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Double {
        require(vec1.size == vec2.size) { "Vectors must have the same dimensions" }

        var dotProduct = 0.0
        var mag1 = 0.0
        var mag2 = 0.0

        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            mag1 += vec1[i] * vec1[i]
            mag2 += vec2[i] * vec2[i]
        }

        val magnitude = sqrt(mag1 * mag2)
        return if (magnitude == 0.0) 0.0 else dotProduct / magnitude
    }

    /**
     * Convert FloatArray to ByteArray for BLOB storage in SQLite
     */
    fun floatArrayToBytes(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4)
        buffer.asFloatBuffer().put(floats)
        return buffer.array()
    }

    /**
     * Convert ByteArray from BLOB storage back to FloatArray
     */
    fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
        val floats = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(floats)
        return floats
    }

    /**
     * Calculate L2 (Euclidean) distance between two vectors
     * Lower values indicate more similarity
     */
    fun euclideanDistance(vec1: FloatArray, vec2: FloatArray): Double {
        require(vec1.size == vec2.size) { "Vectors must have the same dimensions" }

        var sum = 0.0
        for (i in vec1.indices) {
            val diff = vec1[i] - vec2[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }
}
