package com.tinyprint.portraitstudio.core.image

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContrastStretcherTest {

    @Test
    fun testContrastStretchAndClipping() {
        // Create a 10x10 bitmap (100 pixels total)
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(100)

        // Populate with:
        // - First 10 pixels (index 0 to 9) = Color.rgb(50, 50, 50)  -> Gray 50
        // - Next 80 pixels (index 10 to 89) = Color.rgb(100, 100, 100) -> Gray 100
        // - Last 10 pixels (index 90 to 99) = Color.rgb(200, 200, 200) -> Gray 200
        for (i in 0 until 10) {
            pixels[i] = Color.rgb(50, 50, 50)
        }
        for (i in 10 until 90) {
            pixels[i] = Color.rgb(100, 100, 100)
        }
        for (i in 90 until 100) {
            pixels[i] = Color.rgb(200, 200, 200)
        }
        bitmap.setPixels(pixels, 0, 10, 0, 0, 10, 10)

        // Run stretch
        val stretched = ContrastStretcher.stretch(bitmap)

        // Check dimensions and bounds
        assertEquals(100, stretched.size)

        // Low threshold should be 50, high threshold should be 200
        // Stretched value mapping:
        // - Input 50 -> should be 0.0f
        // - Input 200 -> should be 255.0f
        // - Input 100 -> should be (100 - 50) * 255.0f / (200 - 50) = 50 * 255 / 150 = 85.0f
        
        // Assert first 10 pixels are stretched to 0f
        for (i in 0 until 10) {
            assertEquals(0.0f, stretched[i], 0.01f)
        }

        // Assert middle 80 pixels are stretched to 85f
        for (i in 10 until 90) {
            assertEquals(85.0f, stretched[i], 0.01f)
        }

        // Assert last 10 pixels are stretched to 255f
        for (i in 90 until 100) {
            assertEquals(255.0f, stretched[i], 0.01f)
        }
    }

    @Test
    fun testToGrayscaleBitmap() {
        val width = 5
        val height = 5
        val size = width * height
        val grayscale = FloatArray(size) { 128f }

        val destBitmap = ContrastStretcher.toGrayscaleBitmap(grayscale, width, height)
        assertNotNull(destBitmap)
        assertEquals(width, destBitmap.width)
        assertEquals(height, destBitmap.height)

        val outPixels = IntArray(size)
        destBitmap.getPixels(outPixels, 0, width, 0, 0, width, height)

        // Every pixel should be RGB(128, 128, 128)
        val expectedColor = Color.rgb(128, 128, 128)
        for (pixel in outPixels) {
            assertEquals(expectedColor, pixel)
        }
    }
}
