package com.pocketflasher.android

/**
 * Parser for the 8-byte payload returned by SCSI READ CAPACITY(10).
 * Pure parsing only: this class does not open USB, send commands, or write storage.
 */
data class ScsiCapacity(
    val lastLogicalBlockAddress: Long,
    val logicalBlockSizeBytes: Long
) {
    val blockCount: Long get() = lastLogicalBlockAddress + 1L
    val capacityBytes: Long
        get() = Math.multiplyExact(blockCount, logicalBlockSizeBytes)
}

object ScsiReadCapacity10 {
    /**
     * Parses the response payload in big-endian order.
     * Returns null for malformed, unsupported, or unsafe values.
     */
    fun parse(payload: ByteArray): ScsiCapacity? {
        if (payload.size != 8) return null

        val lastLba = unsignedInt32(payload, 0) ?: return null
        val blockSize = unsignedInt32(payload, 4) ?: return null

        // READ CAPACITY(10) uses 0xFFFFFFFF to signal that READ CAPACITY(16)
        // is needed. This first implementation intentionally declines that path.
        if (lastLba == 0xFFFF_FFFFL) return null
        if (blockSize == 0L) return null

        val capacity = try {
            Math.multiplyExact(lastLba + 1L, blockSize)
        } catch (_: ArithmeticException) {
            return null
        }
        if (capacity <= 0L) return null

        return ScsiCapacity(lastLba, blockSize)
    }

    private fun unsignedInt32(bytes: ByteArray, offset: Int): Long? {
        if (offset < 0 || offset + 4 > bytes.size) return null
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }
}
