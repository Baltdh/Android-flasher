package com.pocketflasher.android

/**
 * Pure USB Mass Storage Bulk-Only Transport (BOT) protocol helpers.
 *
 * This class only builds/parses protocol packets. It does not open USB devices,
 * transfer data, or issue any command itself.
 */
object UsbMassStorageBot {
    const val CBW_LENGTH = 31
    const val CSW_LENGTH = 13
    private const val CBW_SIGNATURE = 0x43425355L // bytes "USBC"
    private const val CSW_SIGNATURE = 0x53425355L // bytes "USBS"

    data class CommandStatusWrapper(
        val tag: Long,
        val residue: Long,
        val status: Int
    )

    /** Build a BOT Command Block Wrapper for a SCSI command descriptor block. */
    fun buildCommandBlockWrapper(
        tag: Long,
        transferLength: Long,
        directionIn: Boolean,
        lun: Int,
        commandDescriptorBlock: ByteArray
    ): ByteArray? {
        if (tag !in 1L..0xFFFF_FFFFL) return null
        if (transferLength !in 0L..0xFFFF_FFFFL) return null
        if (lun !in 0..15) return null
        if (commandDescriptorBlock.isEmpty() || commandDescriptorBlock.size > 16) return null

        val result = ByteArray(CBW_LENGTH)
        putLe32(result, 0, CBW_SIGNATURE)
        putLe32(result, 4, tag)
        putLe32(result, 8, transferLength)
        result[12] = if (directionIn) 0x80.toByte() else 0x00
        result[13] = lun.toByte()
        result[14] = commandDescriptorBlock.size.toByte()
        commandDescriptorBlock.copyInto(result, destinationOffset = 15)
        return result
    }

    /** Parse a BOT Command Status Wrapper. Rejects invalid length, signature and status. */
    fun parseCommandStatusWrapper(bytes: ByteArray, expectedTag: Long): CommandStatusWrapper? {
        if (bytes.size != CSW_LENGTH) return null
        if (expectedTag !in 1L..0xFFFF_FFFFL) return null
        if (readLe32(bytes, 0) != CSW_SIGNATURE) return null
        val tag = readLe32(bytes, 4)
        val residue = readLe32(bytes, 8)
        val status = bytes[12].toInt() and 0xFF
        if (tag != expectedTag || status !in 0..2) return null
        return CommandStatusWrapper(tag, residue, status)
    }

    /** SCSI INQUIRY (standard 36-byte response), read-only command. */
    fun inquiryCdb(): ByteArray = byteArrayOf(
        0x12, 0x00, 0x00, 0x00, 36, 0x00
    )

    /** SCSI READ CAPACITY(10), read-only command. */
    fun readCapacity10Cdb(): ByteArray = byteArrayOf(
        0x25, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    )

    private fun putLe32(target: ByteArray, offset: Int, value: Long) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        target[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun readLe32(source: ByteArray, offset: Int): Long {
        return (source[offset].toLong() and 0xFF) or
            ((source[offset + 1].toLong() and 0xFF) shl 8) or
            ((source[offset + 2].toLong() and 0xFF) shl 16) or
            ((source[offset + 3].toLong() and 0xFF) shl 24)
    }
}
