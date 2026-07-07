package com.tinyprint.portraitstudio.prod

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinyprint.portraitstudio.core.ble.BleController
import com.tinyprint.portraitstudio.core.ble.PrintService
import com.tinyprint.portraitstudio.core.image.ContrastStretcher
import com.tinyprint.portraitstudio.core.image.DitherEngine
import com.tinyprint.portraitstudio.core.security.SecurityManager
import com.tinyprint.portraitstudio.core.api.GeminiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

@Keep
data class ArtStyle(
    val id: String,
    val name: String,
    val description: String,
    val prompt: String,
    val defaultThumbnailResId: Int
)

val BuiltInStyles = listOf(
    ArtStyle(
        id = "retro-comic",
        name = "Retro Comic Book",
        description = "Bold outlines & classic comic shading",
        prompt = "Stylize this portrait as a high-contrast retro comic book ink illustration. Use clean, bold black ink outlines and solid shadows on a pure white background. Minimal gray gradients, optimized for black-and-white printing.",
        defaultThumbnailResId = com.tinyprint.portraitstudio.R.drawable.style_thumb_comic
    ),
    ArtStyle(
        id = "vintage-engraving",
        name = "Vintage Engraving",
        description = "Woodcut engraving & newspaper lines",
        prompt = "Stylize this portrait as a 19th-century vintage woodcut engraving. Use clean hatching and crosshatched black lines on a solid white background to build shading and depth, mimicking old newspaper print.",
        defaultThumbnailResId = com.tinyprint.portraitstudio.R.drawable.style_thumb_engraving
    ),
    ArtStyle(
        id = "pixel-art",
        name = "8-Bit Pixel Art",
        description = "Retro Game Boy grid block style",
        prompt = "Transform this portrait into a retro 1-bit Game Boy pixel art graphic. Use only black and white pixel clusters to build textures and shading. Blocky, low-resolution grid aesthetic.",
        defaultThumbnailResId = com.tinyprint.portraitstudio.R.drawable.style_thumb_pixel
    ),
    ArtStyle(
        id = "cyberpunk-stencil",
        name = "Cyberpunk Stencil",
        description = "Minimalist street art silhouette",
        prompt = "Stylize this portrait as a clean, high-contrast cyberpunk vector stencil. High contrast black contours against white negative space, resembling street stencil art or a minimalist silhouette.",
        defaultThumbnailResId = com.tinyprint.portraitstudio.R.drawable.style_thumb_stencil
    ),
    ArtStyle(
        id = "fine-stippling",
        name = "Fine Stippling",
        description = "Elegant pen-and-ink dotted drawing",
        prompt = "Convert this portrait into a stippled ink drawing. Shading and gradients must be composed entirely of fine black ink dots on a pure white background, resembling a stippled pen illustration.",
        defaultThumbnailResId = com.tinyprint.portraitstudio.R.drawable.style_thumb_stippling
    )
)

enum class ScreenState {
    CAMERA,
    STYLE_SELECTION,
    CREATING_PORTRAIT,
    ARTISTIC_PREVIEW,
    PRINTING,
    ERROR,
    STYLE_EDITOR
}



class ConsumerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ConsumerViewModel"
    }

    val bleController = BleController.getInstance(application)
    val securityManager = SecurityManager(application)
    private val gson = Gson()

    // UI State flows
    private val _screenState = MutableStateFlow(ScreenState.CAMERA)
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    private val _useFrontCamera = MutableStateFlow(true)
    val useFrontCamera: StateFlow<Boolean> = _useFrontCamera.asStateFlow()

    private val _showPrinterDialog = MutableStateFlow(false)
    val showPrinterDialog: StateFlow<Boolean> = _showPrinterDialog.asStateFlow()

    private val _isScanningPrinters = MutableStateFlow(false)
    val isScanningPrinters: StateFlow<Boolean> = _isScanningPrinters.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    private val _stylizedBitmap = MutableStateFlow<Bitmap?>(null)
    val stylizedBitmap: StateFlow<Bitmap?> = _stylizedBitmap.asStateFlow()

    private val _selectedStyle = MutableStateFlow<ArtStyle?>(null)
    val selectedStyle: StateFlow<ArtStyle?> = _selectedStyle.asStateFlow()

    private val _printProgressValue = MutableStateFlow(0)
    val printProgressValue: StateFlow<Int> = _printProgressValue.asStateFlow()

    private val _errorTitle = MutableStateFlow("")
    val errorTitle: StateFlow<String> = _errorTitle.asStateFlow()

    private val _errorMessageDetail = MutableStateFlow("")
    val errorMessageDetail: StateFlow<String> = _errorMessageDetail.asStateFlow()

    private val _customStyles = MutableStateFlow<List<ArtStyle>>(emptyList())
    val customStyles: StateFlow<List<ArtStyle>> = _customStyles.asStateFlow()

    // Thumbnails state maps
    private val _customStyleThumbnails = mutableStateMapOf<String, Bitmap>()
    val customStyleThumbnails: Map<String, Bitmap> get() = _customStyleThumbnails

    private val _generatingStyles = MutableStateFlow<List<String>>(emptyList())
    val generatingStyles: StateFlow<List<String>> = _generatingStyles.asStateFlow()

    // Style editor state
    private val _editingStyle = MutableStateFlow<ArtStyle?>(null)
    val editingStyle: StateFlow<ArtStyle?> = _editingStyle.asStateFlow()

    private val _editorPreviewThumbnail = MutableStateFlow<Bitmap?>(null)
    val editorPreviewThumbnail: StateFlow<Bitmap?> = _editorPreviewThumbnail.asStateFlow()

    private val _isGeneratingEditorPreview = MutableStateFlow(false)
    val isGeneratingEditorPreview: StateFlow<Boolean> = _isGeneratingEditorPreview.asStateFlow()

    private val _editorPreviewError = MutableStateFlow<String?>(null)
    val editorPreviewError: StateFlow<String?> = _editorPreviewError.asStateFlow()

    // Crop settings
    private val _cropL = MutableStateFlow(0f)
    val cropL: StateFlow<Float> = _cropL.asStateFlow()

    private val _cropT = MutableStateFlow(0f)
    val cropT: StateFlow<Float> = _cropT.asStateFlow()

    private val _cropR = MutableStateFlow(0f)
    val cropR: StateFlow<Float> = _cropR.asStateFlow()

    private val _cropB = MutableStateFlow(0f)
    val cropB: StateFlow<Float> = _cropB.asStateFlow()

    private val _scaleW = MutableStateFlow(0f)
    val scaleW: StateFlow<Float> = _scaleW.asStateFlow()

    private val _scaleH = MutableStateFlow(0f)
    val scaleH: StateFlow<Float> = _scaleH.asStateFlow()

    // Cache of stylized bitmaps (session lifespan)
    private val _sessionCache = mutableStateMapOf<String, Bitmap>()
    val sessionCache: Map<String, Bitmap> get() = _sessionCache

    // Internal actions for retry logic
    private var currentRetryAction: (() -> Unit)? = null
    private var currentCancelAction: (() -> Unit)? = null

    private var stylizeJob: kotlinx.coroutines.Job? = null
    private var printJob: kotlinx.coroutines.Job? = null

    init {
        loadCustomStyles()

        // Sync with background foreground service progress
        viewModelScope.launch {
            PrintService.printProgress.collect { progress ->
                if (progress != null) {
                    _printProgressValue.value = progress
                    _screenState.value = ScreenState.PRINTING
                }
            }
        }

        // Sync print completion/failure state
        viewModelScope.launch {
            PrintService.printSuccess.collect { success ->
                if (success) {
                    _screenState.value = ScreenState.ARTISTIC_PREVIEW
                } else {
                    val currentBmp = _stylizedBitmap.value
                    setErrorState(
                        title = "Printer Disconnected",
                        detail = "Could not transmit data to the printer. Please make sure the printer is powered on and near your device.",
                        retry = { currentBmp?.let { triggerPrint(it) } },
                        cancel = { _screenState.value = ScreenState.ARTISTIC_PREVIEW }
                    )
                }
            }
        }
    }

    fun setCapturedBitmap(bitmap: Bitmap?) {
        _capturedBitmap.value = bitmap
    }

    fun setStylizedBitmap(bitmap: Bitmap?) {
        _stylizedBitmap.value = bitmap
    }

    fun setSelectedStyle(style: ArtStyle?) {
        _selectedStyle.value = style
    }

    fun setScreenState(state: ScreenState) {
        _screenState.value = state
    }

    fun setUseFrontCamera(useFront: Boolean) {
        _useFrontCamera.value = useFront
    }

    fun setShowPrinterDialog(show: Boolean) {
        _showPrinterDialog.value = show
    }

    fun setIsScanningPrinters(scanning: Boolean) {
        _isScanningPrinters.value = scanning
    }

    fun setCropBounds(l: Float, t: Float, r: Float, b: Float, w: Float, h: Float) {
        _cropL.value = l
        _cropT.value = t
        _cropR.value = r
        _cropB.value = b
        _scaleW.value = w
        _scaleH.value = h
    }

    fun clearSession() {
        _capturedBitmap.value = null
        _stylizedBitmap.value = null
        _selectedStyle.value = null
        _sessionCache.clear()
        _screenState.value = ScreenState.CAMERA
    }

    fun executeRetry() {
        currentRetryAction?.invoke()
    }

    fun executeCancel() {
        currentCancelAction?.invoke()
    }

    private fun setErrorState(title: String, detail: String, retry: () -> Unit, cancel: () -> Unit) {
        _errorTitle.value = title
        _errorMessageDetail.value = detail
        currentRetryAction = retry
        currentCancelAction = cancel
        _screenState.value = ScreenState.ERROR
    }

    fun triggerStylization(style: ArtStyle, apiKey: String, inputBmp: Bitmap) {
        stylizeJob?.cancel()
        _screenState.value = ScreenState.CREATING_PORTRAIT

        stylizeJob = viewModelScope.launch {
            try {
                val client = GeminiClient(apiKey)
                val isCustom = _customStyles.value.any { it.id == style.id }
                val promptToUse = if (isCustom) {
                    val systemInstructions = "Stylize this portrait as a high-contrast monochrome line art or dithered graphic. Use only pure black and pure white. Avoid colors, smooth gradients, or gray shades. Optimize details for low-resolution 1-bit thermal print output."
                    "$systemInstructions User style request: ${style.prompt}"
                } else {
                    style.prompt
                }
                
                val stylized = client.stylizeImage(inputBmp, promptToUse)
                if (stylized != null) {
                    _sessionCache[style.id] = stylized
                    _stylizedBitmap.value = stylized
                    _screenState.value = ScreenState.ARTISTIC_PREVIEW
                } else {
                    setErrorState(
                        title = "AI Stylization Error",
                        detail = "The Gemini model returned an empty result. Please try again.",
                        retry = { triggerStylization(style, apiKey, inputBmp) },
                        cancel = { _screenState.value = ScreenState.STYLE_SELECTION }
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val detailMsg = if (e.message?.contains("timeout", ignoreCase = true) == true) {
                    "The request to the Gemini API timed out. Please check your internet connection and try again."
                } else {
                    e.message ?: "An unexpected error occurred while communicating with the AI model."
                }
                setErrorState(
                    title = "Network / API Error",
                    detail = detailMsg,
                    retry = { triggerStylization(style, apiKey, inputBmp) },
                    cancel = { _screenState.value = ScreenState.STYLE_SELECTION }
                )
            }
        }
    }

    fun triggerPrint(bitmap: Bitmap) {
        if (bleController.connectionState.value != BleController.ConnectionState.CONNECTED) {
            _showPrinterDialog.value = true
            return
        }

        printJob?.cancel()
        _screenState.value = ScreenState.PRINTING
        _printProgressValue.value = 0

        printJob = viewModelScope.launch {
            try {
                val buffer = withContext(Dispatchers.IO) {
                    val w = 384
                    val scale = w.toFloat() / bitmap.width.toFloat()
                    val h = (bitmap.height.toFloat() * scale).toInt()
                    val scaledBmp = Bitmap.createScaledBitmap(bitmap, w, h, true)

                    val stretched = ContrastStretcher.stretch(scaledBmp)
                    val dithered = DitherEngine.dither(stretched, w, h)
                    val packedRows = DitherEngine.packPixels(dithered, w, h)

                    val bos = java.io.ByteArrayOutputStream()
                    bos.write(DitherEngine.wrapPacket(0xBE, byteArrayOf(0x00.toByte())))
                    bos.write(DitherEngine.wrapPacket(0xBD, byteArrayOf(StaticConfig.MOTOR_SPEED_DIVISOR.toByte())))
                    bos.write(DitherEngine.wrapPacket(0xA4, byteArrayOf(StaticConfig.QUALITY_LATTICE.toByte())))
                    
                    val energyBytes = byteArrayOf(
                        (StaticConfig.HEATING_ENERGY and 0xFF).toByte(),
                        ((StaticConfig.HEATING_ENERGY shr 8) and 0xFF).toByte()
                    )
                    bos.write(DitherEngine.wrapPacket(0xAF, energyBytes))
                    bos.write(packedRows)
                    
                    val feedBytes = byteArrayOf(
                        (StaticConfig.PAPER_FEED_LINES and 0xFF).toByte(),
                        ((StaticConfig.PAPER_FEED_LINES shr 8) and 0xFF).toByte()
                    )
                    bos.write(DitherEngine.wrapPacket(0xA1, feedBytes))
                    bos.toByteArray()
                }

                val intent = Intent(getApplication(), PrintService::class.java).apply {
                    putExtra("extra_print_data", buffer)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getApplication<Application>().startForegroundService(intent)
                } else {
                    getApplication<Application>().startService(intent)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                setErrorState(
                    title = "Print Preparation Failed",
                    detail = e.message ?: "Failed to process image commands.",
                    retry = { triggerPrint(bitmap) },
                    cancel = { _screenState.value = ScreenState.ARTISTIC_PREVIEW }
                )
            }
        }
    }

    private fun loadCustomStyles() {
        val savedJson = securityManager.getCustomStyles()
        if (!savedJson.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<ArtStyle>>() {}.type
                val rawList = gson.fromJson<List<ArtStyle>>(savedJson, type) ?: emptyList()
                val list = rawList.map { style ->
                    style.copy(defaultThumbnailResId = com.tinyprint.portraitstudio.R.drawable.style_thumb_stippling)
                }
                _customStyles.value = list

                list.forEach { style ->
                    val file = File(getApplication<Application>().filesDir, "thumb_${style.id}.png")
                    if (file.exists()) {
                        try {
                            val bmp = BitmapFactory.decodeFile(file.absolutePath)
                            if (bmp != null) {
                                _customStyleThumbnails[style.id] = bmp
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to decode thumbnail file for ${style.id}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse custom styles", e)
            }
        }
    }

    private fun saveCustomStyles() {
        try {
            val jsonStr = gson.toJson(_customStyles.value)
            securityManager.saveCustomStyles(jsonStr)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save custom styles", e)
        }
    }

    fun deleteCustomStyle(style: ArtStyle) {
        val currentList = _customStyles.value.toMutableList()
        currentList.removeAll { it.id == style.id }
        _customStyles.value = currentList
        saveCustomStyles()

        val file = File(getApplication<Application>().filesDir, "thumb_${style.id}.png")
        if (file.exists()) {
            file.delete()
        }
        _customStyleThumbnails.remove(style.id)
        _sessionCache.remove(style.id)

        if (_selectedStyle.value?.id == style.id) {
            _selectedStyle.value = null
        }
    }

    fun setEditingStyle(style: ArtStyle?) {
        _editingStyle.value = style
    }

    fun clearEditorPreviewState() {
        _editorPreviewThumbnail.value = null
        _isGeneratingEditorPreview.value = false
        _editorPreviewError.value = null
    }

    fun setEditorPreviewThumbnail(bitmap: Bitmap?) {
        _editorPreviewThumbnail.value = bitmap
    }

    fun generateThumbnailPreview(prompt: String, apiKey: String?) {
        if (apiKey.isNullOrEmpty()) {
            _editorPreviewError.value = "Gemini API Key is missing. Set it in Settings."
            return
        }
        _isGeneratingEditorPreview.value = true
        _editorPreviewError.value = null

        viewModelScope.launch {
            try {
                val baseBmp = BitmapFactory.decodeResource(
                    getApplication<Application>().resources,
                    com.tinyprint.portraitstudio.R.drawable.default_portrait_template
                )
                if (baseBmp != null) {
                    val client = GeminiClient(apiKey)
                    val systemInstructions = "Stylize this portrait as a high-contrast monochrome line art or dithered graphic. Use only pure black and pure white. Avoid colors, smooth gradients, or gray shades. Optimize details for low-resolution 1-bit thermal print output."
                    val promptToUse = "$systemInstructions User style request: $prompt"
                    val stylizedDefault = client.stylizeImage(baseBmp, promptToUse)
                    if (stylizedDefault != null) {
                        _editorPreviewThumbnail.value = stylizedDefault
                    } else {
                        _editorPreviewError.value = "Failed to generate preview. Please try again."
                    }
                } else {
                    _editorPreviewError.value = "Failed to load default template image."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Preview generation failed", e)
                _editorPreviewError.value = e.message ?: "An unexpected error occurred."
            } finally {
                _isGeneratingEditorPreview.value = false
            }
        }
    }

    fun createOrUpdateStyle(
        id: String?,
        name: String,
        prompt: String,
        apiKey: String?,
        preGeneratedThumbnail: Bitmap? = null
    ) {
        val styleId = id ?: "custom-${System.currentTimeMillis()}"
        val newStyle = ArtStyle(
            id = styleId,
            name = name,
            description = "Custom prompt style",
            prompt = prompt,
            defaultThumbnailResId = com.tinyprint.portraitstudio.R.drawable.style_thumb_stippling
        )

        val currentList = _customStyles.value.toMutableList()
        currentList.removeAll { it.id == styleId }
        currentList.add(newStyle)
        _customStyles.value = currentList
        saveCustomStyles()

        _sessionCache.remove(styleId)

        if (preGeneratedThumbnail != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val file = File(getApplication<Application>().filesDir, "thumb_${styleId}.png")
                    file.outputStream().use { fos ->
                        preGeneratedThumbnail.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                    _customStyleThumbnails[styleId] = preGeneratedThumbnail
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save pre-generated thumbnail", e)
                }
            }
        } else if (!apiKey.isNullOrEmpty()) {
            val list = _generatingStyles.value.toMutableList()
            list.add(styleId)
            _generatingStyles.value = list

            viewModelScope.launch {
                try {
                    val baseBmp = BitmapFactory.decodeResource(
                        getApplication<Application>().resources,
                        com.tinyprint.portraitstudio.R.drawable.default_portrait_template
                    )
                    if (baseBmp != null) {
                        val client = GeminiClient(apiKey)
                        val systemInstructions = "Stylize this portrait as a high-contrast monochrome line art or dithered graphic. Use only pure black and pure white. Avoid colors, smooth gradients, or gray shades. Optimize details for low-resolution 1-bit thermal print output."
                        val promptToUse = "$systemInstructions User style request: ${newStyle.prompt}"
                        val stylizedDefault = client.stylizeImage(baseBmp, promptToUse)
                        if (stylizedDefault != null) {
                            val file = File(getApplication<Application>().filesDir, "thumb_${styleId}.png")
                            file.outputStream().use { fos ->
                                stylizedDefault.compress(Bitmap.CompressFormat.PNG, 100, fos)
                            }
                            _customStyleThumbnails[styleId] = stylizedDefault
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Background generation failed", e)
                } finally {
                    val listAfter = _generatingStyles.value.toMutableList()
                    listAfter.remove(styleId)
                    _generatingStyles.value = listAfter
                }
            }
        }
    }
}
