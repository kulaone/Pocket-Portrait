package com.tinyprint.portraitstudio.core.image

import android.graphics.Bitmap
import android.graphics.Color

object ContrastStretcher {

    /**
     * Stretches the contrast of a bitmap and returns a flat float array of grayscale values (0f to 255f).
     * Clips the lowest 5% and highest 5% of luminances.
     */
    fun stretch(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val grayscale = FloatArray(totalPixels)
        val histogram = IntArray(256)

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            // Standard relative luminance formula
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
            grayscale[i] = gray.toFloat()
            histogram[gray]++
        }

        // Clip 5% from both ends
        val lowPercentileLimit = (totalPixels * 0.05).toInt()
        val highPercentileLimit = (totalPixels * 0.95).toInt()

        var lowThreshold = 0
        var sum = 0
        for (i in 0..255) {
            sum += histogram[i]
            if (sum >= lowPercentileLimit) {
                lowThreshold = i
                break
            }
        }

        var highThreshold = 255
        sum = 0
        for (i in 255 downTo 0) {
            sum += histogram[i]
            if (sum >= (totalPixels - highPercentileLimit)) {
                highThreshold = i
                break
            }
        }

        if (highThreshold <= lowThreshold) {
            highThreshold = (lowThreshold + 1).coerceAtMost(255)
        }

        val range = (highThreshold - lowThreshold).toFloat()
        val stretched = FloatArray(totalPixels)
        for (i in grayscale.indices) {
            val v = grayscale[i]
            stretched[i] = when {
                v <= lowThreshold -> 0.0f
                v >= highThreshold -> 255.0f
                else -> ((v - lowThreshold) * 255.0f / range)
            }
        }

        return stretched
    }

    /**
     * Converts a flat grayscale FloatArray back into a Bitmap for display purposes.
     */
    fun toGrayscaleBitmap(grayscale: FloatArray, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(grayscale.size)
        for (i in grayscale.indices) {
            val g = grayscale[i].toInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(g, g, g)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
