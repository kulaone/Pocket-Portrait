package com.tinyprint.portraitstudio.dev

import com.tinyprint.portraitstudio.core.image.DitherEngine
import java.io.ByteArrayOutputStream

object DarknessTest {

    /**
     * Generates a packet stream that configures the printer and prints solid black rows
     * followed by a feed command to verify motor speed and maximum thermal density.
     */
    fun generateTestBuffer(
        rowsCount: Int = 120,
        motorSpeedDivisor: Int = 0x28, // Extra slow speed
        qualityLattice: Int = 0x35,    // Quality Level 5 (0x35)
        heatingEnergy: Int = 0xFFFF    // Max thermal energy
    ): ByteArray {
        val bos = ByteArrayOutputStream()

        // 1. Drawing Mode Command (0xBE 00)
        bos.write(DitherEngine.wrapPacket(0xBE, byteArrayOf(0x00.toByte())))

        // 2. Motor Speed Divisor Command (0xBD 28)
        bos.write(DitherEngine.wrapPacket(0xBD, byteArrayOf(motorSpeedDivisor.toByte())))

        // 3. Quality Lattice Command (0xA4 35)
        bos.write(DitherEngine.wrapPacket(0xA4, byteArrayOf(qualityLattice.toByte())))

        // 4. Thermal Energy Command (0xAF FF FF)
        val energyBytes = byteArrayOf(
            (heatingEnergy and 0xFF).toByte(),
            ((heatingEnergy shr 8) and 0xFF).toByte()
        )
        bos.write(DitherEngine.wrapPacket(0xAF, energyBytes))

        // 5. Solid Black Rows (0xA2 with 48 bytes of 0xFF)
        val solidRow = ByteArray(48) { 0xFF.toByte() }
        val rowPacket = DitherEngine.wrapPacket(0xA2, solidRow)
        for (i in 0 until rowsCount) {
            bos.write(rowPacket)
        }

        // 6. Paper Feed Command (0xA1 50 00 -> 80 lines)
        val feedBytes = byteArrayOf(0x50.toByte(), 0x00.toByte())
        bos.write(DitherEngine.wrapPacket(0xA1, feedBytes))

        return bos.toByteArray()
    }
}
