package com.pocketflasher.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsbMassStorageBotTest {
    @Test
    fun buildsInquiryCommandWrapperWithExpectedBotFields() {
        val cbw = UsbMassStorageBot.buildCommandBlockWrapper(
            tag = 0x12345678L,
            transferLength = 36,
            directionIn = true,
            lun = 0,
            commandDescriptorBlock = UsbMassStorageBot.inquiryCdb()
        )
        requireNotNull(cbw)
        assertEquals(31, cbw.size)
        // BOT signature "USBC", little-endian.
        assertEquals(0x55, cbw[0].toInt() and 0xFF)
        assertEquals(0x53, cbw[1].toInt() and 0xFF)
        assertEquals(0x42, cbw[2].toInt() and 0xFF)
        assertEquals(0x43, cbw[3].toInt() and 0xFF)
        assertEquals(0x78, cbw[4].toInt() and 0xFF)
        assertEquals(0x80, cbw[12].toInt() and 0xFF)
        assertEquals(6, cbw[14].toInt() and 0xFF)
        assertEquals(0x12, cbw[15].toInt() and 0xFF)
    }

    @Test
    fun rejectsUnsafeWrapperArguments() {
        assertNull(UsbMassStorageBot.buildCommandBlockWrapper(0, 36, true, 0, UsbMassStorageBot.inquiryCdb()))
        assertNull(UsbMassStorageBot.buildCommandBlockWrapper(1, -1, true, 0, UsbMassStorageBot.inquiryCdb()))
        assertNull(UsbMassStorageBot.buildCommandBlockWrapper(1, 36, true, 16, UsbMassStorageBot.inquiryCdb()))
        assertNull(UsbMassStorageBot.buildCommandBlockWrapper(1, 36, true, 0, ByteArray(17)))
    }

    @Test
    fun exposesOnlyReadOnlyProbeCommandsInThisStage() {
        assertEquals(6, UsbMassStorageBot.inquiryCdb().size)
        assertEquals(0x12, UsbMassStorageBot.inquiryCdb()[0].toInt() and 0xFF)
        assertEquals(10, UsbMassStorageBot.readCapacity10Cdb().size)
        assertEquals(0x25, UsbMassStorageBot.readCapacity10Cdb()[0].toInt() and 0xFF)
    }

    @Test
    fun parsesValidCommandStatusWrapper() {
        val csw = byteArrayOf(
            0x55, 0x53, 0x42, 0x53, // "USBS"
            0x78, 0x56, 0x34, 0x12, // tag 0x12345678
            0x00, 0x00, 0x00, 0x00, // residue
            0x00 // command passed
        )
        val result = UsbMassStorageBot.parseCommandStatusWrapper(csw, 0x12345678L)
        requireNotNull(result)
        assertEquals(0x12345678L, result.tag)
        assertEquals(0L, result.residue)
        assertEquals(0, result.status)
    }

    @Test
    fun rejectsMalformedOrMismatchedCommandStatusWrapper() {
        val valid = byteArrayOf(
            0x55, 0x53, 0x42, 0x53,
            0x01, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00
        )
        assertNull(UsbMassStorageBot.parseCommandStatusWrapper(ByteArray(12), 1))
        assertNull(UsbMassStorageBot.parseCommandStatusWrapper(valid.copyOf().also { it[0] = 0 }, 1))
        assertNull(UsbMassStorageBot.parseCommandStatusWrapper(valid, 2))
        assertNull(UsbMassStorageBot.parseCommandStatusWrapper(valid.copyOf().also { it[12] = 3 }, 1))
    }
}
