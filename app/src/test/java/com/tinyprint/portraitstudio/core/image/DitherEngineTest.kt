package com.tinyprint.portraitstudio.core.image

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DitherEngineTest {

    @Test
    fun testCalculateCrc8() {
        // Test with empty payload
        assertEquals(0, DitherEngine.calculateCrc8(byteArrayOf()))

        // Test with known single bytes
        assertEquals(0x07, DitherEngine.calculateCrc8(byteArrayOf(0x01)))
        assertEquals(0x0E, DitherEngine.calculateCrc8(byteArrayOf(0x02)))
        assertEquals(0xAC, DitherEngine.calculateCrc8(byteArrayOf(0x55)))

        // Test with multi-byte payload
        // Loop 1: input 0x11 -> crc = crc8Table[17] = 0x77
        // Loop 2: input 0x22 -> crc = crc8Table[0x77 xor 0x22] = crc8Table[0x55] = 0xAC
        val payload = byteArrayOf(0x11, 0x22)
        assertEquals(0xAC, DitherEngine.calculateCrc8(payload))
    }

    @Test
    fun testWrapPacket() {
        val payload = byteArrayOf(0x11, 0x22)
        val commandId = 0xBE
        val wrapped = DitherEngine.wrapPacket(commandId, payload)

        // Expected format:
        // [0] Magic 1: 0x51
        // [1] Magic 2: 0x78
        // [2] Command: 0xBE
        // [3] Padding: 0x00
        // [4] Length Low: 0x02
        // [5] Length High: 0x00
        // [6] Payload[0]: 0x11
        // [7] Payload[1]: 0x22
        // [8] CRC-8 of payload: 0xAC
        // [9] Footer: 0xFF
        val expected = byteArrayOf(
            0x51.toByte(),
            0x78.toByte(),
            0xBE.toByte(),
            0x00.toByte(),
            0x02.toByte(),
            0x00.toByte(),
            0x11.toByte(),
            0x22.toByte(),
            0xAC.toByte(),
            0xFF.toByte()
        )
        assertArrayEquals(expected, wrapped)
    }

    @Test
    fun testDitherSolidColors() {
        val width = 4
        val height = 4
        val size = width * height

        // 1. All black input (0f)
        val allBlack = FloatArray(size) { 0f }
        val ditheredBlack = DitherEngine.dither(allBlack, width, height)
        val expectedBlack = IntArray(size) { 0 }
        assertArrayEquals(expectedBlack, ditheredBlack)

        // 2. All white input (255f)
        val allWhite = FloatArray(size) { 255f }
        val ditheredWhite = DitherEngine.dither(allWhite, width, height)
        val expectedWhite = IntArray(size) { 255 }
        assertArrayEquals(expectedWhite, ditheredWhite)
    }

    @Test
    fun testDitherErrorPropagation() {
        // Trace error propagation on a 2x2 grid
        // Initial input: [120f, 130f,
        //                 140f, 150f]
        val grayscale = floatArrayOf(120f, 130f, 140f, 150f)
        val dithered = DitherEngine.dither(grayscale, 2, 2, threshold = 128)

        // Expected output matching our manual trace:
        // (0,0): 120 < 128 -> 0. Error = 120.
        //        (0,1) becomes 130 + 120 * 7/16 = 182.5
        //        (1,0) becomes 140 + 120 * 5/16 = 177.5
        //        (1,1) becomes 150 + 120 * 1/16 = 157.5
        // (0,1): 182.5 >= 128 -> 255. Error = 182.5 - 255 = -72.5
        //        (1,0) becomes 177.5 + (-72.5 * 3/16) = 177.5 - 13.59375 = 163.90625
        //        (1,1) becomes 157.5 + (-72.5 * 5/16) = 157.5 - 22.65625 = 134.84375
        // (1,0): 163.90625 >= 128 -> 255. Error = 163.90625 - 255 = -91.09375
        //        (1,1) becomes 134.84375 + (-91.09375 * 7/16) = 134.84375 - 39.853515625 = 94.990234375
        // (1,1): 94.99 < 128 -> 0.
        // Expected: [0, 255, 255, 0]
        val expected = intArrayOf(0, 255, 255, 0)
        assertArrayEquals(expected, dithered)
    }

    @Test
    fun testPackPixels() {
        val width = 384
        val height = 2
        val size = width * height

        // Test with all black pixels (0), which should map to active thermal dots (bit=1)
        val allBlackDithered = IntArray(size) { 0 }
        val packedBytes = DitherEngine.packPixels(allBlackDithered, width, height)

        // There should be 2 rows. Each row:
        // - Cmd: 0xA2
        // - Payload size: 48 bytes
        // - Wrapped size: 8 + 48 = 56 bytes.
        // Total expected size: 112 bytes.
        assertEquals(112, packedBytes.size)

        // Verify the structure of the first row packet:
        // [0] Magic 1: 0x51
        // [1] Magic 2: 0x78
        // [2] Cmd: 0xA2 (162, which is -94 in signed byte)
        // [3] Padding: 0x00
        // [4] Length Low: 48 (0x30)
        // [5] Length High: 0 (0x00)
        // [6..53] Payload: 48 bytes of 0xFF (-1 in signed byte)
        // [54] CRC-8 of 48 bytes of 0xFF
        // [55] Footer: 0xFF (-1 in signed byte)
        assertEquals(0x51.toByte(), packedBytes[0])
        assertEquals(0x78.toByte(), packedBytes[1])
        assertEquals(0xA2.toByte(), packedBytes[2])
        assertEquals(0x00.toByte(), packedBytes[3])
        assertEquals(48.toByte(), packedBytes[4])
        assertEquals(0x00.toByte(), packedBytes[5])

        for (i in 6 until 54) {
            assertEquals(0xFF.toByte(), packedBytes[i])
        }

        // Expected CRC: CRC-8 of 48 bytes of 0xFF.
        // Let's compute CRC-8 of 48 bytes of 0xFF using our class to check consistency:
        val expectedCrc = DitherEngine.calculateCrc8(ByteArray(48) { 0xFF.toByte() }).toByte()
        assertEquals(expectedCrc, packedBytes[54])
        assertEquals(0xFF.toByte(), packedBytes[55])
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPackPixelsInvalidWidthThrows() {
        // Only 384px width is supported
        DitherEngine.packPixels(IntArray(100), 100, 1)
    }
}
