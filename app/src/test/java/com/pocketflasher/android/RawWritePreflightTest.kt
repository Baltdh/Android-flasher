package com.pocketflasher.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawWritePreflightTest {
    private val validSource = RawWriteSource(
        displayName = "system.img",
        sizeBytes = 1024,
        sha256 = "a".repeat(64),
        officialSha256Matched = true
    )

    private val validTarget = RawWriteTarget(
        stableId = "usb:vendor-1234:product-5678:serial-X",
        capacityBytes = 4096,
        removable = true,
        writableBySelectedBackend = true,
        backend = RawWriteTarget.Backend.USB_MASS_STORAGE
    )

    @Test
    fun blocksWhenTargetIsUnknown() {
        assertFalse(RawWritePreflight.assess(validSource, null).allowedForFutureBackend)
    }

    @Test
    fun blocksWhenOfficialHashDoesNotMatch() {
        val source = validSource.copy(officialSha256Matched = false)
        assertFalse(RawWritePreflight.assess(source, validTarget).allowedForFutureBackend)
    }

    @Test
    fun blocksWhenImageIsLargerThanTarget() {
        val target = validTarget.copy(capacityBytes = 512)
        assertTrue(
            RawWritePreflight.assess(validSource, target).blockers
                .contains("A imagem é maior que a capacidade do destino.")
        )
    }

    @Test
    fun blocksWhenBackendHasNoWriteAccess() {
        val target = validTarget.copy(writableBySelectedBackend = false)
        assertFalse(RawWritePreflight.assess(validSource, target).allowedForFutureBackend)
    }

    @Test
    fun passesOnlyWhenAllDeclaredPreconditionsHold() {
        assertTrue(RawWritePreflight.assess(validSource, validTarget).allowedForFutureBackend)
    }
}
