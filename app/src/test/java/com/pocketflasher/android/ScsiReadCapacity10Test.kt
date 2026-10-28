package com.pocketflasher.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScsiReadCapacity10Test {
    @Test
    fun parsesTypical512ByteBlocks() {
        // Last LBA = 0x001D1C0F; block size = 512.
        val result = ScsiReadCapacity10.parse(
            byteArrayOf(0x00, 0x1D, 0x1C, 0x0F, 0x00, 0x00, 0x02, 0x00)
        )
        requireNotNull(result)
        assertEquals(0x001D1C0FL, result.lastLogicalBlockAddress)
        assertEquals(512L, result.logicalBlockSizeBytes)
        assertEquals(0x001D1C10L, result.blockCount)
        assertEquals(0x001D1C10L * 512L, result.capacityBytes)
    }

    @Test
    fun treatsFieldsAsUnsigned() {
        val result = ScsiReadCapacity10.parse(
            byteArrayOf(0x7F, -1, -1, -2, 0x00, 0x00, 0x10, 0x00)
        )
        requireNotNull(result)
        assertEquals(0x7FFF_FFFEL, result.lastLogicalBlockAddress)
        assertEquals(4096L, result.logicalBlockSizeBytes)
    }

    @Test
    fun rejectsWrongPayloadLength() {
        assertNull(ScsiReadCapacity10.parse(ByteArray(7)))
        assertNull(ScsiReadCapacity10.parse(ByteArray(9)))
    }

    @Test
    fun rejectsZeroBlockSize() {
        assertNull(
            ScsiReadCapacity10.parse(
                byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0)
            )
        )
    }

    @Test
    fun defersCapacity10SentinelToCapacity16Implementation() {
        assertNull(
            ScsiReadCapacity10.parse(
                byteArrayOf(-1, -1, -1, -1, 0, 0, 2, 0)
            )
        )
    }
}
