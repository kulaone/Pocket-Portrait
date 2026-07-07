package com.tinyprint.portraitstudio.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyprint.portraitstudio.core.security.SecurityManager
import com.tinyprint.portraitstudio.core.ui.theme.NeonCyan
import com.tinyprint.portraitstudio.core.ui.theme.NeonViolet
import com.tinyprint.portraitstudio.core.ui.theme.Slate100
import com.tinyprint.portraitstudio.core.ui.theme.Slate400
import com.tinyprint.portraitstudio.core.ui.theme.Slate700
import com.tinyprint.portraitstudio.core.ui.theme.Slate900
import com.tinyprint.portraitstudio.core.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val securityManager = remember { SecurityManager(context) }
    val versionName = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }
    
    var apiKeyInput by remember { mutableStateOf("") }
    var isKeySaved by remember { mutableStateOf(false) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val savedKey = securityManager.getApiKey()
        if (!savedKey.isNullOrEmpty()) {
            apiKeyInput = savedKey
            isKeySaved = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Configuration",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Slate100,
                modifier = Modifier.align(Alignment.Start)
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Google Gemini API Key",
                        fontWeight = FontWeight.Bold,
                        color = Slate100
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "The AI portrait stylization requires a Gemini API key. It is saved securely inside your device's hardware-backed Keystore and never sent to any backend proxy.",
                        fontSize = 13.sp,
                        color = Slate400,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            saveMessage = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            val image = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(imageVector = image, contentDescription = "Toggle Visibility", tint = Slate400)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate100,
                            unfocusedTextColor = Slate100,
                            focusedBorderColor = NeonViolet,
                            unfocusedBorderColor = Slate700,
                            focusedLabelColor = NeonViolet,
                            unfocusedLabelColor = Slate400
                        )
                    )

                    if (isKeySaved) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Configured",
                                tint = SuccessGreen,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Text(
                                text = "API Key Status: Configured",
                                color = SuccessGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    if (saveMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = saveMessage,
                            color = NeonCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (apiKeyInput.trim().isNotEmpty()) {
                                securityManager.saveApiKey(apiKeyInput.trim())
                                isKeySaved = true
                                saveMessage = "API Key saved successfully."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Save API Key", color = Slate100, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            securityManager.deleteApiKey()
                            apiKeyInput = ""
                            isKeySaved = false
                            saveMessage = "API Key cleared from storage."
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate400),
                        border = ButtonDefaults.outlinedButtonBorder.copy()
                    ) {
                        Text("Delete / Clear Key", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Help Info",
                        tint = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "How to get a key?",
                        fontWeight = FontWeight.Bold,
                        color = Slate100,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "You can obtain a free personal Gemini API Key from Google AI Studio at: aistudio.google.com",
                        color = Slate400,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Version $versionName",
                color = Slate400,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
