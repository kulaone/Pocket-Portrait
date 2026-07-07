package com.tinyprint.portraitstudio.core.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiClient(private val apiKey: String) {

    companion object {
        private const val TAG = "GeminiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-image:generateContent"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Determines the closest Gemini-supported aspect ratio based on source width and height.
     * Note: The 1.6f threshold separates widescreen formats (16:9 / 9:16) from traditional formats.
     * Standard 3:2 (1.5) photos fall below 1.6f and map to 3:4 / 4:3 (resulting in slight cropping).
     */
    fun determineAspectRatio(width: Int, height: Int): String {
        if (width == height) return "1:1"
        val ratio = if (height > width) height.toFloat() / width.toFloat() else width.toFloat() / height.toFloat()
        return if (height > width) {
            if (ratio >= 1.6f) "9:16" else "3:4"
        } else {
            if (ratio >= 1.6f) "16:9" else "4:3"
        }
    }

    /**
     * Sends the bitmap and stylization prompt to the Gemini API, returning the stylized bitmap response.
     */
    suspend fun stylizeImage(bitmap: Bitmap, prompt: String): Bitmap? = withContext(Dispatchers.IO) {
        val aspect = determineAspectRatio(bitmap.width, bitmap.height)
        Log.d(TAG, "Input image dimensions: ${bitmap.width}x${bitmap.height}, resolved aspect: $aspect")

        // 1. Compress input bitmap to JPEG bytes
        val jpegOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, jpegOutputStream)
        val jpegBytes = jpegOutputStream.toByteArray()
        val base64Data = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

        // 2. Build the JSON Payload exactly matching the verified Web POC configuration
        val payloadJson = buildPayloadJson(prompt, base64Data, aspect)

        // 3. Make POST Request
        val requestBody = payloadJson.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("x-goog-api-key", apiKey)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "API error ${response.code}")
                    val userFriendlyMsg = when (response.code) {
                        400 -> "Invalid request. Please check your input."
                        403 -> "Invalid API Key. Please verify your settings."
                        429 -> "Rate limit exceeded. Please wait a moment before trying again."
                        500, 503 -> "The Gemini server is temporarily unavailable. Please try again later."
                        else -> "API Error ${response.code}. Please try again."
                    }
                    throw Exception(userFriendlyMsg)
                }

                val bodyText = response.body?.string() ?: ""
                // 4. Parse candidates list for inline generated image data
                val resObj = gson.fromJson(bodyText, JsonObject::class.java)
                val candidates = resObj.getAsJsonArray("candidates")
                if (candidates == null || candidates.size() == 0) {
                    throw Exception("No candidates returned from Gemini.")
                }

                val firstCandidate = candidates.get(0).asJsonObject
                val content = firstCandidate.getAsJsonObject("content")
                val parts = content.getAsJsonArray("parts")
                
                var imageBase64: String? = null
                for (i in 0 until parts.size()) {
                    val partObj = parts.get(i).asJsonObject
                    if (partObj.has("inlineData")) {
                        val inlineData = partObj.getAsJsonObject("inlineData")
                        imageBase64 = inlineData.get("data").asString
                        break
                    }
                }

                if (imageBase64 == null) {
                    throw Exception("No inline image data found in response payload.")
                }

                // 5. Decode Base64 string back into Bitmap
                val decodedBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during stylization call: ${e.message}", e)
            throw e
        }
    }

    private fun buildPayloadJson(prompt: String, base64Image: String, aspect: String): String {
        // Build the tree manually to guarantee precise structure
        val root = JsonObject()
        
        val contentsArray = com.google.gson.JsonArray()
        val contentObj = JsonObject()
        val partsArray = com.google.gson.JsonArray()

        // Text Part
        val textPart = JsonObject()
        textPart.addProperty("text", prompt)
        partsArray.add(textPart)

        // Image Part
        val imagePart = JsonObject()
        val inlineData = JsonObject()
        inlineData.addProperty("mimeType", "image/jpeg")
        inlineData.addProperty("data", base64Image)
        imagePart.add("inlineData", inlineData)
        partsArray.add(imagePart)

        contentObj.add("parts", partsArray)
        contentsArray.add(contentObj)
        root.add("contents", contentsArray)

        // Generation Config
        val genConfig = JsonObject()
        val modalities = com.google.gson.JsonArray()
        modalities.add("IMAGE")
        genConfig.add("responseModalities", modalities)

        val imageConfig = JsonObject()
        imageConfig.addProperty("imageSize", "1K")
        imageConfig.addProperty("aspectRatio", aspect)
        genConfig.add("imageConfig", imageConfig)

        root.add("generationConfig", genConfig)

        return gson.toJson(root)
    }
}
