package com.tinyprint.portraitstudio.core.api

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiClientTest {

    @Test
    fun testDetermineAspectRatio() {
        val client = GeminiClient("fake-api-key")

        // 1:1 Aspect Ratio
        assertEquals("1:1", client.determineAspectRatio(500, 500))

        // Tall/Portrait Aspect Ratios (height > width)
        // Ratio = 16 / 9 = 1.777 >= 1.6
        assertEquals("9:16", client.determineAspectRatio(1080, 1920))
        // Ratio = 4 / 3 = 1.333 < 1.6
        assertEquals("3:4", client.determineAspectRatio(768, 1024))

        // Wide/Landscape Aspect Ratios (width > height)
        // Ratio = 16 / 9 = 1.777 >= 1.6
        assertEquals("16:9", client.determineAspectRatio(1920, 1080))
        // Ratio = 4 / 3 = 1.333 < 1.6
        assertEquals("4:3", client.determineAspectRatio(1024, 768))
    }
}
