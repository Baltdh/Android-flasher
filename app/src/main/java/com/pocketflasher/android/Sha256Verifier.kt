package com.pocketflasher.android

import java.util.Locale

/**
 * Small, deterministic SHA-256 input validator used by the UI and unit tests.
 */
internal object Sha256Verifier {
    private val sha256Pattern = Regex("[0-9a-f]{64}")

    fun normalize(value: String): String =
        value.filterNot { it.isWhitespace() }.lowercase(Locale.ROOT)

    fun isValidExpectedHash(value: String): Boolean =
        sha256Pattern.matches(normalize(value))

    /**
     * Returns true/false for a valid expected hash, or null when its format is invalid.
     */
    fun matches(actualHash: String, expectedHash: String): Boolean? {
        val expected = normalize(expectedHash)
        if (!sha256Pattern.matches(expected)) return null
        val actual = normalize(actualHash)
        if (!sha256Pattern.matches(actual)) return null
        return actual == expected
    }
}
