package com.pocketflasher.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Sha256VerifierTest {
    private val validHash = "0123456789abcdef".repeat(4)

    @Test
    fun normalizesUppercaseAndWhitespace() {
        assertEquals(validHash, Sha256Verifier.normalize("  " + validHash.uppercase() + "\n"))
    }

    @Test
    fun acceptsExactly64HexCharacters() {
        assertTrue(Sha256Verifier.isValidExpectedHash(validHash))
        assertFalse(Sha256Verifier.isValidExpectedHash("abc"))
        assertFalse(Sha256Verifier.isValidExpectedHash("g".repeat(64)))
        assertFalse(Sha256Verifier.isValidExpectedHash("0".repeat(63)))
    }

    @Test
    fun matchesHashWithoutCaseSensitivity() {
        assertEquals(true, Sha256Verifier.matches(validHash, validHash.uppercase()))
    }

    @Test
    fun detectsMismatch() {
        val otherHash = "f".repeat(64)
        assertEquals(false, Sha256Verifier.matches(validHash, otherHash))
    }

    @Test
    fun rejectsInvalidExpectedOrActualHash() {
        assertNull(Sha256Verifier.matches(validHash, "not-a-hash"))
        assertNull(Sha256Verifier.matches("bad", validHash))
    }
}
