package com.tinyprint.portraitstudio.prod

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import com.tinyprint.portraitstudio.core.ble.BleController
import com.tinyprint.portraitstudio.core.security.SecurityManager
import com.tinyprint.portraitstudio.core.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun ConsumerScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ConsumerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Observe state flows from ViewModel
    val screenState by viewModel.screenState.collectAsState()
    val useFrontCamera by viewModel.useFrontCamera.collectAsState()
    val showPrinterDialog by viewModel.showPrinterDialog.collectAsState()
    val isScanningPrinters by viewModel.isScanningPrinters.collectAsState()
    val capturedBitmap by viewModel.capturedBitmap.collectAsState()
    val stylizedBitmap by viewModel.stylizedBitmap.collectAsState()
    val selectedStyle by viewModel.selectedStyle.collectAsState()
    val printProgressValue by viewModel.printProgressValue.collectAsState()
    val errorTitle by viewModel.errorTitle.collectAsState()
    val errorMessageDetail by viewModel.errorMessageDetail.collectAsState()
    val customStyles by viewModel.customStyles.collectAsState()
    val generatingStyles by viewModel.generatingStyles.collectAsState()

    val editingStyle by viewModel.editingStyle.collectAsState()
    val editorPreviewThumbnail by viewModel.editorPreviewThumbnail.collectAsState()
    val isGeneratingEditorPreview by viewModel.isGeneratingEditorPreview.collectAsState()
    val editorPreviewError by viewModel.editorPreviewError.collectAsState()

    val cropL by viewModel.cropL.collectAsState()
    val cropT by viewModel.cropT.collectAsState()
    val cropR by viewModel.cropR.collectAsState()
    val cropB by viewModel.cropB.collectAsState()
    val scaleW by viewModel.scaleW.collectAsState()
    val scaleH by viewModel.scaleH.collectAsState()

    val connectionState by viewModel.bleController.connectionState.collectAsState()
    val discoveredPrinters by viewModel.bleController.discoveredPrinters.collectAsState()

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bmp = BitmapFactory.decodeStream(inputStream)
                    if (bmp != null) {
                        viewModel.setCapturedBitmap(bmp)
                        viewModel.setScreenState(ScreenState.STYLE_SELECTION)
                    } else {
                        Toast.makeText(context, "Could not decode selected image.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ConsumerScreen", "Error loading gallery image", e)
                Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showPrinterDialog) {
        PrinterConnectDialog(
            connectionState = connectionState,
            isScanningPrinters = isScanningPrinters,
            discoveredPrinters = discoveredPrinters,
            securityManager = viewModel.securityManager,
            bleController = viewModel.bleController,
            onDismiss = {
                viewModel.bleController.stopScan()
                viewModel.setIsScanningPrinters(false)
                viewModel.setShowPrinterDialog(false)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pocket Portrait", fontWeight = FontWeight.Black, fontSize = 18.sp) },
                actions = {
                    IconButton(onClick = { viewModel.setShowPrinterDialog(true) }) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Printer Status",
                            tint = if (connectionState == BleController.ConnectionState.CONNECTED) SuccessGreen else Slate400
                        )
                    }

                    if (capturedBitmap != null) {
                        IconButton(onClick = { viewModel.clearSession() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Start Over",
                                tint = Slate100
                            )
                        }
                    }

                    IconButton(onClick = onNavigateToSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = Slate100)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900,
                    titleContentColor = Slate100
                )
            )
        },
        containerColor = Slate900
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (screenState) {
                ScreenState.CAMERA -> {
                    CameraPreviewScreen(
                        useFrontCamera = useFrontCamera,
                        onFlipCamera = { viewModel.setUseFrontCamera(!useFrontCamera) },
                        onPhotoCaptured = { bmp ->
                            viewModel.setCapturedBitmap(bmp)
                            viewModel.setScreenState(ScreenState.STYLE_SELECTION)
                        },
                        onLaunchGallery = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }

                ScreenState.STYLE_SELECTION -> {
                    StyleSelectionScreen(
                        capturedBitmap = capturedBitmap,
                        customStyles = customStyles,
                        generatingStyles = generatingStyles,
                        customStyleThumbnails = viewModel.customStyleThumbnails,
                        sessionCache = viewModel.sessionCache,
                        selectedStyle = selectedStyle,
                        cropL = cropL,
                        cropT = cropT,
                        cropR = cropR,
                        cropB = cropB,
                        scaleW = scaleW,
                        scaleH = scaleH,
                        securityManager = viewModel.securityManager,
                        onCropBoundsChanged = { l, t, r, b, sw, sh ->
                            viewModel.setCropBounds(l, t, r, b, sw, sh)
                        },
                        onRetakePhoto = {
                            viewModel.setCapturedBitmap(null)
                            viewModel.setScreenState(ScreenState.CAMERA)
                        },
                        onSelectStyle = { style ->
                            viewModel.setSelectedStyle(style)
                            val apiKey = viewModel.securityManager.getApiKey()
                            if (apiKey.isNullOrEmpty()) {
                                Toast.makeText(context, "Please set your Gemini API Key in Settings first!", Toast.LENGTH_LONG).show()
                                onNavigateToSettings()
                            } else {
                                val cached = viewModel.sessionCache[style.id]
                                if (cached != null) {
                                    viewModel.setStylizedBitmap(cached)
                                    viewModel.setScreenState(ScreenState.ARTISTIC_PREVIEW)
                                } else {
                                    val inputBmp = capturedBitmap
                                    if (inputBmp != null) {
                                        val croppedInput = cropBitmapIfNeeded(inputBmp, scaleW, scaleH, cropL, cropT, cropR, cropB) ?: inputBmp
                                        viewModel.triggerStylization(style, apiKey, croppedInput)
                                    }
                                }
                            }
                        },
                        onStartCreateStyle = {
                            viewModel.setEditingStyle(null)
                            viewModel.clearEditorPreviewState()
                            viewModel.setScreenState(ScreenState.STYLE_EDITOR)
                        },
                        onStartEditStyle = { style ->
                            viewModel.setEditingStyle(style)
                            viewModel.clearEditorPreviewState()
                            val existingThumb = viewModel.customStyleThumbnails[style.id]
                            if (existingThumb != null) {
                                viewModel.setEditorPreviewThumbnail(existingThumb)
                            }
                            viewModel.setScreenState(ScreenState.STYLE_EDITOR)
                        },
                        onDeleteStyle = { style ->
                            viewModel.deleteCustomStyle(style)
                        }
                    )
                }

                ScreenState.STYLE_EDITOR -> {
                    StyleEditorScreen(
                        editingStyle = editingStyle,
                        previewThumbnail = editorPreviewThumbnail,
                        isGeneratingPreview = isGeneratingEditorPreview,
                        previewError = editorPreviewError,
                        onGeneratePreview = { prompt ->
                            val apiKey = viewModel.securityManager.getApiKey()
                            viewModel.generateThumbnailPreview(prompt, apiKey)
                        },
                        onSaveStyle = { id, name, prompt ->
                            val apiKey = viewModel.securityManager.getApiKey()
                            viewModel.createOrUpdateStyle(id, name, prompt, apiKey, editorPreviewThumbnail)
                            viewModel.setScreenState(ScreenState.STYLE_SELECTION)
                        },
                        onCancel = {
                            viewModel.setScreenState(ScreenState.STYLE_SELECTION)
                        }
                    )
                }

                ScreenState.CREATING_PORTRAIT -> {
                    CreatingPortraitScreen()
                }

                ScreenState.ARTISTIC_PREVIEW -> {
                    ArtisticPreviewScreen(
                        stylizedBitmap = stylizedBitmap,
                        selectedStyle = selectedStyle,
                        capturedBitmap = capturedBitmap,
                        cropL = cropL,
                        cropT = cropT,
                        cropR = cropR,
                        cropB = cropB,
                        scaleW = scaleW,
                        scaleH = scaleH,
                        securityManager = viewModel.securityManager,
                        onNavigateToSettings = onNavigateToSettings,
                        onChangeStyle = {
                            viewModel.setScreenState(ScreenState.STYLE_SELECTION)
                        },
                        onRegenerate = { style ->
                            val apiKey = viewModel.securityManager.getApiKey()
                            if (apiKey.isNullOrEmpty()) {
                                Toast.makeText(context, "Please set your Gemini API Key in Settings first!", Toast.LENGTH_LONG).show()
                                onNavigateToSettings()
                            } else {
                                val inputBmp = capturedBitmap
                                if (inputBmp != null) {
                                    val croppedInput = cropBitmapIfNeeded(inputBmp, scaleW, scaleH, cropL, cropT, cropR, cropB) ?: inputBmp
                                    viewModel.triggerStylization(style, apiKey, croppedInput)
                                }
                            }
                        },
                        onPrint = { bitmap ->
                            viewModel.triggerPrint(bitmap)
                        }
                    )
                }

                ScreenState.PRINTING -> {
                    PrintingScreen(printProgressValue = printProgressValue)
                }

                ScreenState.ERROR -> {
                    ErrorScreen(
                        errorTitle = errorTitle,
                        errorMessageDetail = errorMessageDetail,
                        onRetry = { viewModel.executeRetry() },
                        onCancel = { viewModel.executeCancel() }
                    )
                }
            }
        }
    }
}

@Composable
fun CameraPreviewScreen(
    useFrontCamera: Boolean,
    onFlipCamera: () -> Unit,
    onPhotoCaptured: (Bitmap) -> Unit,
    onLaunchGallery: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        key(useFrontCamera) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        
                        imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()

                        val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                imageCapture
                            )
                        } catch (e: Exception) {
                            Log.e("CameraPreviewScreen", "Binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onFlipCamera,
                modifier = Modifier
                    .size(56.dp)
                    .background(Slate800.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Flip Camera",
                    tint = Slate100
                )
            }

            IconButton(
                onClick = {
                    val photoFile = File(context.cacheDir, "captured_selfie.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                    imageCapture?.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                val rawBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                val exif = androidx.exifinterface.media.ExifInterface(photoFile.absolutePath)
                                val orientation = exif.getAttributeInt(
                                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                                )
                                val degrees = when (orientation) {
                                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                                    else -> 0
                                }
                                val finalBitmap = if (degrees != 0) {
                                    val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
                                    val rotated = Bitmap.createBitmap(
                                        rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
                                    )
                                    rawBitmap.recycle()
                                    rotated
                                } else {
                                    rawBitmap
                                }
                                onPhotoCaptured(finalBitmap)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                },
                modifier = Modifier
                    .size(72.dp)
                    .background(NeonViolet, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Camera,
                    contentDescription = "Capture",
                    tint = Slate100,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = onLaunchGallery,
                modifier = Modifier
                    .size(56.dp)
                    .background(Slate800.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Import Photo",
                    tint = Slate100
                )
            }
        }
    }
}

@Composable
fun StyleSelectionScreen(
    capturedBitmap: Bitmap?,
    customStyles: List<ArtStyle>,
    generatingStyles: List<String>,
    customStyleThumbnails: Map<String, Bitmap>,
    sessionCache: Map<String, Bitmap>,
    selectedStyle: ArtStyle?,
    cropL: Float,
    cropT: Float,
    cropR: Float,
    cropB: Float,
    scaleW: Float,
    scaleH: Float,
    securityManager: SecurityManager,
    onCropBoundsChanged: (left: Float, top: Float, right: Float, bottom: Float, destW: Float, destH: Float) -> Unit,
    onRetakePhoto: () -> Unit,
    onSelectStyle: (ArtStyle) -> Unit,
    onStartCreateStyle: () -> Unit,
    onStartEditStyle: (ArtStyle) -> Unit,
    onDeleteStyle: (ArtStyle) -> Unit
) {
    val context = LocalContext.current
    val styleScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(styleScrollState)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        capturedBitmap?.let { bitmap ->
            Box(
                modifier = Modifier
                    .height(260.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            ) {
                FreeformCropper(
                    bitmap = bitmap,
                    modifier = Modifier.fillMaxSize(),
                    onBoundsChanged = onCropBoundsChanged
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRetakePhoto,
                colors = ButtonDefaults.buttonColors(containerColor = Slate800),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Retake Photo", color = Slate100, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = "Choose Art Style",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Slate100,
            modifier = Modifier.align(Alignment.Start)
        )
        Text(
            text = "Select an artistic filter to transform your portrait.",
            color = Slate400,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            modifier = Modifier.heightIn(max = 4000.dp),
            userScrollEnabled = false,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(BuiltInStyles) { style ->
                Card(
                    onClick = { onSelectStyle(style) },
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val cachedBmp = sessionCache[style.id]
                        if (cachedBmp != null) {
                            Image(
                                bitmap = cachedBmp.asImageBitmap(),
                                contentDescription = style.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Image(
                                painter = painterResource(id = style.defaultThumbnailResId),
                                contentDescription = style.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(style.name, fontWeight = FontWeight.Bold, color = Slate100, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(style.description, color = Slate400, fontSize = 12.sp)
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = NeonCyan
                        )
                    }
                }
            }

            if (customStyles.isNotEmpty()) {
                items(customStyles) { style ->
                    val isGenerating = generatingStyles.contains(style.id)
                    Card(
                        onClick = { onSelectStyle(style) },
                        colors = CardDefaults.cardColors(containerColor = Slate800),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val cachedBmp = sessionCache[style.id]
                            val generatedThumb = customStyleThumbnails[style.id]

                            Box(modifier = Modifier.size(64.dp)) {
                                if (isGenerating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .align(Alignment.Center),
                                        color = NeonCyan,
                                        strokeWidth = 2.dp
                                    )
                                } else if (cachedBmp != null) {
                                    Image(
                                        bitmap = cachedBmp.asImageBitmap(),
                                        contentDescription = style.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else if (generatedThumb != null) {
                                    Image(
                                        bitmap = generatedThumb.asImageBitmap(),
                                        contentDescription = style.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = style.defaultThumbnailResId),
                                        contentDescription = style.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(style.name, fontWeight = FontWeight.Bold, color = Slate100, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(style.description, color = Slate400, fontSize = 12.sp)
                            }

                            IconButton(onClick = { onStartEditStyle(style) }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Style", tint = Slate400)
                            }

                            IconButton(onClick = { onDeleteStyle(style) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Style", tint = Slate400)
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    onClick = { onStartCreateStyle() },
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Create Custom Style",
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleEditorScreen(
    editingStyle: ArtStyle?,
    previewThumbnail: Bitmap?,
    isGeneratingPreview: Boolean,
    previewError: String?,
    onGeneratePreview: (prompt: String) -> Unit,
    onSaveStyle: (id: String?, name: String, prompt: String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var name by remember { mutableStateOf(editingStyle?.name ?: "") }
    var prompt by remember { mutableStateOf(editingStyle?.prompt ?: "Stylize this portrait as a high-contrast monochrome ") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editingStyle != null) "Edit Style" else "Create Style", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Slate100
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900,
                    titleContentColor = Slate100
                )
            )
        },
        containerColor = Slate900
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Custom Art Filter",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Slate100,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Explain to the AI how to transform the selfie. Describe line thickness, shading styles, backgrounds, and graphic styles.",
                color = Slate400,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Style Name (e.g. Manga Sketch)", color = Slate400) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Slate100,
                    unfocusedTextColor = Slate100,
                    focusedContainerColor = Slate800,
                    unfocusedContainerColor = Slate800,
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = Slate700
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Prompt Description", color = Slate400) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Slate100,
                    unfocusedTextColor = Slate100,
                    focusedContainerColor = Slate800,
                    unfocusedContainerColor = Slate800,
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = Slate700
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Filter Preview",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Slate100,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Slate800)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Slate900),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewThumbnail != null) {
                        Image(
                            bitmap = previewThumbnail.asImageBitmap(),
                            contentDescription = "Style preview thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = Slate700,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("No Preview", color = Slate400, fontSize = 10.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = {
                            if (prompt.isBlank()) {
                                Toast.makeText(context, "Please enter a prompt first!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            focusManager.clearFocus()
                            onGeneratePreview(prompt.trim())
                        },
                        enabled = !isGeneratingPreview,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isGeneratingPreview) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Slate900,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (previewThumbnail == null) "Generate Preview" else "Regenerate Preview",
                                color = Slate900,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (!previewError.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = previewError,
                    color = CrimsonError,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (name.isBlank() || prompt.isBlank()) {
                        Toast.makeText(context, "Please fill in both name and prompt!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onSaveStyle(editingStyle?.id, name.trim(), prompt.trim())
                    Toast.makeText(
                        context,
                        if (editingStyle != null) "Style updated!" else "Style added!",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(
                    imageVector = if (editingStyle != null) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    tint = Slate100
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (editingStyle != null) "Save Changes" else "Create Style",
                    color = Slate100,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun CreatingPortraitScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = NeonViolet, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Creating your portrait...",
            fontWeight = FontWeight.Bold,
            color = Slate100,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Generating artistic filter with Gemini AI...",
            color = Slate400,
            fontSize = 13.sp
        )
    }
}

@Composable
fun ArtisticPreviewScreen(
    stylizedBitmap: Bitmap?,
    selectedStyle: ArtStyle?,
    capturedBitmap: Bitmap?,
    cropL: Float,
    cropT: Float,
    cropR: Float,
    cropB: Float,
    scaleW: Float,
    scaleH: Float,
    securityManager: SecurityManager,
    onNavigateToSettings: () -> Unit,
    onChangeStyle: () -> Unit,
    onRegenerate: (ArtStyle) -> Unit,
    onPrint: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    stylizedBitmap?.let { bitmap ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "AI Portrait",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButtonWithColor(
                        onClick = onChangeStyle,
                        text = "Change Style",
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedButtonWithColor(
                        onClick = {
                            val style = selectedStyle
                            if (style != null) {
                                onRegenerate(style)
                            }
                        },
                        text = "Regenerate",
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = { onPrint(bitmap) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Print, contentDescription = null, tint = Slate100)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Print Portrait", color = Slate100, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButtonWithColor(
                        onClick = {
                            val success = saveBitmapToGallery(context, bitmap)
                            if (success) {
                                Toast.makeText(context, "Saved to Gallery under Pictures/PocketPortrait!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to save image!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        text = "Save",
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedButtonWithColor(
                        onClick = { shareBitmap(context, bitmap) },
                        text = "Share",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun PrintingScreen(printProgressValue: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Sending to printer...",
            fontWeight = FontWeight.Bold,
            color = Slate100,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { printProgressValue / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = NeonCyan,
            trackColor = Slate700
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("$printProgressValue% sent", color = Slate400, fontSize = 13.sp)
    }
}

@Composable
fun ErrorScreen(
    errorTitle: String,
    errorMessageDetail: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(NeonViolet.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error Icon",
                tint = NeonViolet,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = errorTitle,
            fontWeight = FontWeight.Bold,
            color = Slate100,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = errorMessageDetail,
            color = Slate400,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(36.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Retry", color = Slate900, fontWeight = FontWeight.Bold)
            }

            OutlinedButtonWithColor(
                onClick = onCancel,
                text = "Cancel",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun PrinterConnectDialog(
    connectionState: BleController.ConnectionState,
    isScanningPrinters: Boolean,
    discoveredPrinters: List<android.bluetooth.BluetoothDevice>,
    securityManager: SecurityManager,
    bleController: BleController,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            colors = CardDefaults.cardColors(containerColor = Slate800),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Connect Thermal Printer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Slate100
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Status: $connectionState",
                        color = if (connectionState == BleController.ConnectionState.CONNECTED) SuccessGreen else Slate400,
                        fontSize = 14.sp
                    )
                    IconButton(onClick = {
                        if (isScanningPrinters) {
                            bleController.stopScan()
                        } else {
                            bleController.startScan()
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Scan", tint = NeonCyan)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (discoveredPrinters.isEmpty()) {
                        item {
                            Text(
                                text = if (isScanningPrinters) "Searching for printers..." else "Press refresh to scan",
                                color = Slate400,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 20.dp)
                            )
                        }
                    } else {
                        items(discoveredPrinters) { device ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        bleController.stopScan()
                                        bleController.connect(device.address)
                                        securityManager.saveLastPrinterAddress(device.address)
                                    },
                                colors = CardDefaults.cardColors(containerColor = Slate700)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(device.name ?: "Unknown Printer", color = Slate100, fontWeight = FontWeight.Bold)
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = NeonCyan
                                    )
                                }
                            }
                        }
                    }
                }

                val savedAddress = securityManager.getLastPrinterAddress()
                if (connectionState == BleController.ConnectionState.CONNECTED || !savedAddress.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (connectionState == BleController.ConnectionState.CONNECTED) {
                            Button(
                                onClick = { bleController.disconnect() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text("Disconnect", color = Slate100, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                        
                        if (!savedAddress.isNullOrEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    securityManager.saveLastPrinterAddress(null)
                                    Toast.makeText(context, "Saved printer forgotten", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate100),
                                border = ButtonDefaults.outlinedButtonBorder.copy()
                            ) {
                                Text("Forget Printer", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun OutlinedButtonWithColor(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate100),
        border = ButtonDefaults.outlinedButtonBorder.copy()
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun FreeformCropper(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    onBoundsChanged: (left: Float, top: Float, right: Float, bottom: Float, destW: Float, destH: Float) -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val handleTouchRadius = with(density) { 32.dp.toPx() }
    val minSize = with(density) { 40.dp.toPx() }

    var cropLeft by remember { mutableStateOf(-1f) }
    var cropTop by remember { mutableStateOf(-1f) }
    var cropRight by remember { mutableStateOf(-1f) }
    var cropBottom by remember { mutableStateOf(-1f) }

    var imageWidth by remember { mutableStateOf(0f) }
    var imageHeight by remember { mutableStateOf(0f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    var activeHandle by remember { mutableStateOf(-1) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(bitmap) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val tl = Offset(offsetX + cropLeft, offsetY + cropTop)
                        val tr = Offset(offsetX + cropRight, offsetY + cropTop)
                        val bl = Offset(offsetX + cropLeft, offsetY + cropBottom)
                        val br = Offset(offsetX + cropRight, offsetY + cropBottom)

                        fun dist(o1: Offset, o2: Offset) = kotlin.math.hypot(o1.x - o2.x, o1.y - o2.y)

                        if (dist(startOffset, tl) < handleTouchRadius) {
                            activeHandle = 0
                        } else if (dist(startOffset, tr) < handleTouchRadius) {
                            activeHandle = 1
                        } else if (dist(startOffset, bl) < handleTouchRadius) {
                            activeHandle = 2
                        } else if (dist(startOffset, br) < handleTouchRadius) {
                            activeHandle = 3
                        } else if (startOffset.x >= offsetX + cropLeft && startOffset.x <= offsetX + cropRight &&
                            startOffset.y >= offsetY + cropTop && startOffset.y <= offsetY + cropBottom
                        ) {
                            activeHandle = 4
                        } else {
                            activeHandle = -1
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (activeHandle == -1) return@detectDragGestures
                        change.consume()

                        if (activeHandle == 0) {
                            cropLeft = (cropLeft + dragAmount.x).coerceIn(0f, cropRight - minSize)
                            cropTop = (cropTop + dragAmount.y).coerceIn(0f, cropBottom - minSize)
                        } else if (activeHandle == 1) {
                            cropRight = (cropRight + dragAmount.x).coerceIn(cropLeft + minSize, imageWidth)
                            cropTop = (cropTop + dragAmount.y).coerceIn(0f, cropBottom - minSize)
                        } else if (activeHandle == 2) {
                            cropLeft = (cropLeft + dragAmount.x).coerceIn(0f, cropRight - minSize)
                            cropBottom = (cropBottom + dragAmount.y).coerceIn(cropTop + minSize, imageHeight)
                        } else if (activeHandle == 3) {
                            cropRight = (cropRight + dragAmount.x).coerceIn(cropLeft + minSize, imageWidth)
                            cropBottom = (cropBottom + dragAmount.y).coerceIn(cropTop + minSize, imageHeight)
                        } else if (activeHandle == 4) {
                            val w = cropRight - cropLeft
                            val h = cropBottom - cropTop
                            val nextLeft = (cropLeft + dragAmount.x).coerceIn(0f, imageWidth - w)
                            val nextTop = (cropTop + dragAmount.y).coerceIn(0f, imageHeight - h)
                            cropLeft = nextLeft
                            cropRight = nextLeft + w
                            cropTop = nextTop
                            cropBottom = nextTop + h
                        }

                        onBoundsChanged(cropLeft, cropTop, cropRight, cropBottom, imageWidth, imageHeight)
                    },
                    onDragEnd = { activeHandle = -1 }
                )
            }
    ) {
        val canvasW = size.width
        val canvasH = size.height

        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()

        val scaleX = canvasW / bmpW
        val scaleY = canvasH / bmpH
        val scale = minOf(scaleX, scaleY)

        val destW = bmpW * scale
        val destH = bmpH * scale
        val destX = (canvasW - destW) / 2
        val destY = (canvasH - destH) / 2

        imageWidth = destW
        imageHeight = destH
        offsetX = destX
        offsetY = destY

        if (cropLeft < 0f) {
            cropLeft = 0f
            cropTop = 0f
            cropRight = destW
            cropBottom = destH
            onBoundsChanged(cropLeft, cropTop, cropRight, cropBottom, destW, destH)
        }

        drawImage(
            image = bitmap.asImageBitmap(),
            dstOffset = IntOffset(destX.toInt(), destY.toInt()),
            dstSize = IntSize(destW.toInt(), destH.toInt())
        )

        // Dark overlay outside crop box
        drawRect(
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
            topLeft = Offset(destX, destY),
            size = Size(cropLeft, destH)
        )
        drawRect(
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
            topLeft = Offset(destX + cropRight, destY),
            size = Size(destW - cropRight, destH)
        )
        drawRect(
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
            topLeft = Offset(destX + cropLeft, destY),
            size = Size(cropRight - cropLeft, cropTop)
        )
        drawRect(
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
            topLeft = Offset(destX + cropLeft, destY + cropBottom),
            size = Size(cropRight - cropLeft, destH - cropBottom)
        )

        // Bounding box thin border
        drawRect(
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f),
            topLeft = Offset(destX + cropLeft, destY + cropTop),
            size = Size(cropRight - cropLeft, cropBottom - cropTop),
            style = Stroke(width = 1.dp.toPx())
        )

        // Corner handles
        val handleLen = 20.dp.toPx()
        val strokeW = 4.dp.toPx()

        drawPath(
            path = Path().apply {
                moveTo(destX + cropLeft, destY + cropTop + handleLen)
                lineTo(destX + cropLeft, destY + cropTop)
                lineTo(destX + cropLeft + handleLen, destY + cropTop)
            },
            color = NeonCyan,
            style = Stroke(width = strokeW)
        )
        drawPath(
            path = Path().apply {
                moveTo(destX + cropRight - handleLen, destY + cropTop)
                lineTo(destX + cropRight, destY + cropTop)
                lineTo(destX + cropRight, destY + cropTop + handleLen)
            },
            color = NeonCyan,
            style = Stroke(width = strokeW)
        )
        drawPath(
            path = Path().apply {
                moveTo(destX + cropLeft, destY + cropBottom - handleLen)
                lineTo(destX + cropLeft, destY + cropBottom)
                lineTo(destX + cropLeft + handleLen, destY + cropBottom)
            },
            color = NeonCyan,
            style = Stroke(width = strokeW)
        )
        drawPath(
            path = Path().apply {
                moveTo(destX + cropRight - handleLen, destY + cropBottom)
                lineTo(destX + cropRight, destY + cropBottom)
                lineTo(destX + cropRight, destY + cropBottom - handleLen)
            },
            color = NeonCyan,
            style = Stroke(width = strokeW)
        )
    }
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
    return try {
        val filename = "PocketPortrait_${System.currentTimeMillis()}.png"
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/PocketPortrait")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } else {
            false
        }
    } catch (e: Exception) {
        Log.e("ConsumerScreen", "Failed to save bitmap", e)
        false
    }
}

private fun shareBitmap(context: Context, bitmap: Bitmap) {
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "temp_share_portrait.png")
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "com.tinyprint.portraitstudio.fileprovider",
            file
        )

        if (contentUri != null) {
            val shareIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, context.contentResolver.getType(contentUri))
                putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
                type = "image/png"
            }
            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Portrait"))
        }
    } catch (e: Exception) {
        Log.e("ConsumerScreen", "Failed to share bitmap", e)
        Toast.makeText(context, "Failed to share portrait: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun cropBitmapIfNeeded(
    inputBmp: Bitmap?,
    scaleW: Float,
    scaleH: Float,
    cropL: Float,
    cropT: Float,
    cropR: Float,
    cropB: Float
): Bitmap? {
    if (inputBmp == null) return null
    val bmpW = inputBmp.width
    val bmpH = inputBmp.height
    return if (scaleW > 0f && scaleH > 0f) {
        val x = ((cropL / scaleW) * bmpW).coerceIn(0f, bmpW.toFloat()).toInt()
        val y = ((cropT / scaleH) * bmpH).coerceIn(0f, bmpH.toFloat()).toInt()
        val w = (((cropR - cropL) / scaleW) * bmpW).coerceIn(1f, bmpW.toFloat() - x).toInt()
        val h = (((cropB - cropT) / scaleH) * bmpH).coerceIn(1f, bmpH.toFloat() - y).toInt()
        try {
            Bitmap.createBitmap(inputBmp, x, y, w, h)
        } catch (e: Exception) {
            inputBmp
        }
    } else {
        inputBmp
    }
}
