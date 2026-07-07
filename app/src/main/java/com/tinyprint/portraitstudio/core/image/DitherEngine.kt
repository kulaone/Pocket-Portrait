package com.tinyprint.portraitstudio.core.image

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream

object DitherEngine {

    private val crc8Table = intArrayOf(
        0x00, 0x07, 0x0E, 0x09, 0x1C, 0x1B, 0x12, 0x15, 0x38, 0x3F, 0x36, 0x31, 0x24, 0x23, 0x2A, 0x2D,
        0x70, 0x77, 0x7E, 0x79, 0x6C, 0x6B, 0x62, 0x65, 0x48, 0x4F, 0x46, 0x41, 0x54, 0x53, 0x5A, 0x5D,
        0xE0, 0xE7, 0xEE, 0xE9, 0xFC, 0xFB, 0xF2, 0xF5, 0xD8, 0xDF, 0xD6, 0xD1, 0xC4, 0xC3, 0xCA, 0xCD,
        0x90, 0x97, 0x9E, 0x99, 0x8C, 0x8B, 0x82, 0x85, 0xA8, 0xAF, 0xA6, 0xA1, 0xB4, 0xB3, 0xBA, 0xBD,
        0xC7, 0xC0, 0xC9, 0xCE, 0xDB, 0xDC, 0xD5, 0xD2, 0xFF, 0xF8, 0xF1, 0xF6, 0xE3, 0xE4, 0xED, 0xEA,
        0xB7, 0xB0, 0xB9, 0xBE, 0xAB, 0xAC, 0xA5, 0xA2, 0x8F, 0x88, 0x81, 0x86, 0x93, 0x94, 0x9D, 0x9A,
        0x27, 0x20, 0x29, 0x2E, 0x3B, 0x3C, 0x35, 0x32, 0x1F, 0x18, 0x11, 0x16, 0x03, 0x04, 0x0D, 0x0A,
        0x57, 0x50, 0x59, 0x5E, 0x4B, 0x4C, 0x45, 0x42, 0x6F, 0x68, 0x61, 0x66, 0x73, 0x74, 0x7D, 0x7A,
        0x89, 0x8E, 0x87, 0x80, 0x95, 0x92, 0x9B, 0x9C, 0xB1, 0xB6, 0xBF, 0xB8, 0xAD, 0xAA, 0xA3, 0xA4,
        0xF9, 0xFE, 0xF7, 0xF0, 0xE5, 0xE2, 0xEB, 0xEC, 0xC1, 0xC6, 0xCF, 0xC8, 0xDD, 0xDA, 0xd3, 0xd4,
        0x69, 0x6E, 0x67, 0x60, 0x75, 0x72, 0x7B, 0x7C, 0x51, 0x56, 0x5F, 0x58, 0x4D, 0x4A, 0x43, 0x44,
        0x19, 0x1E, 0x17, 0x10, 0x05, 0x02, 0x0B, 0x0C, 0x21, 0x26, 0x2F, 0x28, 0x3D, 0x3A, 0x33, 0x34,
        0x4E, 0x49, 0x40, 0x47, 0x52, 0x55, 0x5C, 0x5B, 0x76, 0x71, 0x78, 0x7F, 0x6A, 0x6D, 0x64, 0x63,
        0x3E, 0x39, 0x30, 0x37, 0x22, 0x25, 0x2C, 0x2B, 0x06, 0x01, 0x08, 0x0F, 0x1A, 0x1D, 0x14, 0x13,
        0xAE, 0xA9, 0xA0, 0xA7, 0xB2, 0xB5, 0xBC, 0xBB, 0x96, 0x91, 0x98, 0x9F, 0x8A, 0x8D, 0x84, 0x83,
        0xDE, 0xD9, 0xD0, 0xD7, 0xC2, 0xC5, 0xCC, 0xCB, 0xE6, 0xE1, 0xE8, 0xEF, 0xFA, 0xFD, 0xF4, 0xF3
    )

    fun calculateCrc8(payload: ByteArray): Int {
        var crc = 0
        for (b in payload) {
            val byteVal = b.toInt() and 0xFF
            crc = crc8Table[(crc xor byteVal) and 0xFF]
        }
        return crc
    }

    /**
     * Wrap custom payload into the printer's GATT packet structure.
     */
    fun wrapPacket(commandId: Int, payload: ByteArray): ByteArray {
        val size = payload.size
        val packet = ByteArray(8 + size)
        packet[0] = 0x51.toByte() // magic 1
        packet[1] = 0x78.toByte() // magic 2
        packet[2] = commandId.toByte()
        packet[3] = 0x00.toByte()
        packet[4] = (size and 0xFF).toByte()
        packet[5] = ((size shr 8) and 0xFF).toByte()
        System.arraycopy(payload, 0, packet, 6, size)
        packet[6 + size] = calculateCrc8(payload).toByte()
        packet[7 + size] = 0xFF.toByte() // footer
        return packet
    }

    /**
     * Appies Floyd-Steinberg error diffusion to the grayscale pixel values.
     * threshold determines the black vs white cutoff (default 128).
     */
    fun dither(grayscale: FloatArray, width: Int, height: Int, threshold: Int = 128): IntArray {
        val dithered = IntArray(grayscale.size)
        val pixels = grayscale.clone()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val oldVal = pixels[idx]
                val newVal = if (oldVal < threshold) 0 else 255
                dithered[idx] = newVal

                val err = oldVal - newVal

                // Propagate errors to neighbors
                if (x + 1 < width) {
                    pixels[idx + 1] += err * (7.0f / 16.0f)
                }
                if (x - 1 >= 0 && y + 1 < height) {
                    pixels[(y + 1) * width + (x - 1)] += err * (3.0f / 16.0f)
                }
                if (y + 1 < height) {
                    pixels[(y + 1) * width + x] += err * (5.0f / 16.0f)
                }
                if (x + 1 < width && y + 1 < height) {
                    pixels[(y + 1) * width + (x + 1)] += err * (1.0f / 16.0f)
                }
            }
        }
        return dithered
    }

    /**
     * Packs 1-bit binary pixel array LSB-first into 48-byte rows (representing 384 px wide scanline rows).
     */
    fun packPixels(dithered: IntArray, width: Int, height: Int): ByteArray {
        require(width == 384) { "Printer only supports 384 pixel wide scanlines." }
        val out = ByteArrayOutputStream()

        for (y in 0 until height) {
            val rowBytes = ByteArray(48)
            for (byteIdx in 0 until 48) {
                var byteVal = 0
                for (bit in 0 until 8) {
                    val x = byteIdx * 8 + bit
                    val pixelIdx = y * width + x
                    // In Floyd-Steinberg output, black is 0
                    val isBlack = dithered[pixelIdx] < 128
                    if (isBlack) {
                        byteVal = byteVal or (1 shl bit)
                    }
                }
                rowBytes[byteIdx] = byteVal.toByte()
            }
            out.write(wrapPacket(0xA2, rowBytes))
        }
        return out.toByteArray()
    }

    /**
     * Helper to generate a Compose-compatible preview Bitmap.
     */
    fun toBinaryBitmap(dithered: IntArray, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(dithered.size)
        for (i in dithered.indices) {
            val color = if (dithered[i] < 128) Color.BLACK else Color.WHITE
            pixels[i] = color
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
