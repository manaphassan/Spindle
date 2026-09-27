package com.hana.spindle.data.db

import java.util.Locale

/**
 * Utility to sanitize and format user input into safe, high-performance SQLite FTS4 match queries.
 *
 * Converts space-delimited words into prefix-matching tokens (e.g. "ludwig bee" -> "ludwig* bee*")
 * while stripping reserved SQLite/FTS operators to prevent syntax errors.
 */
object FtsQueryBuilder {

    // FTS reserved words that shouldn't be treated as match tokens if solitary
    private val RESERVED_KEYWORDS = setOf("and", "or", "not", "near")

    /**
     * Builds a sanitized FTS query string with prefix matching for each term.
     * Returns null if the query contains no valid search tokens.
     */
    fun buildPrefixQuery(rawQuery: String?): String? {
        if (rawQuery.isNullOrBlank()) return null

        // 1. Remove FTS4 syntax characters, punctuation, and control symbols
        // Keep unicode letters, numbers, and whitespace
        val cleaned = rawQuery
            .replace(Regex("[*\"'`:;~^&|()\\[\\]{}<>?+=/\\\\#\$%!,.-]"), " ")
            .trim()

        if (cleaned.isEmpty()) return null

        // 2. Split by whitespace into individual tokens
        val tokens = cleaned.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) return null

        // 3. Format each token for prefix matching, filtering out solitary reserved operators
        val validTokens = tokens.mapNotNull { token ->
            val lower = token.lowercase(Locale.ROOT)
            if (lower in RESERVED_KEYWORDS && tokens.size > 1) {
                // If it's a reserved word inside a multi-word phrase, quote it so FTS treats it as literal
                "\"$token\"*"
            } else if (lower in RESERVED_KEYWORDS && tokens.size == 1) {
                // Single reserved word -> quote it
                "\"$token\"*"
            } else {
                "$token*"
            }
        }

        return if (validTokens.isNotEmpty()) validTokens.joinToString(" ") else null
    }

    /**
     * Checks if a string query has at least one valid alphanumeric search token.
     */
    fun isValidQuery(rawQuery: String?): Boolean {
        return buildPrefixQuery(rawQuery) != null
    }
}
