package com.claudecode.history.domain

/**
 * Defines the search mode for querying conversations
 */
enum class SearchMode {
    /**
     * Traditional keyword-based search using SQLite FTS5
     * Fast and precise for exact term matching
     */
    KEYWORD,

    /**
     * Semantic search using vector embeddings
     * Finds conceptually similar content even with different wording
     * Requires Ollama to be running and embeddings to be generated
     */
    SEMANTIC,

    /**
     * Combines keyword and semantic search using Reciprocal Rank Fusion
     * Provides best of both worlds - precision of keywords with semantic understanding
     * Recommended for most use cases
     */
    HYBRID
}
