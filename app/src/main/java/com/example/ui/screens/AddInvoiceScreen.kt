package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ai.ExtractedInvoiceResult
import com.example.ui.components.NothingButton
import com.example.ui.components.NothingHeader
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.NothingRed
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.ExtractionUiState
import com.example.ui.viewmodel.MainViewModel
import java.io.File

@Composable
fun AddInvoiceScreen(
    viewModel: MainViewModel,
    onNavigateToReview: (ExtractedInvoiceResult) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val extractionState by viewModel.extractionState.collectAsStateWithLifecycle()

    var photoUri by remember { mutableStateOf<Uri?>(null) }

    // Launcher for PDF file picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.processInvoiceUri(it) }
    }

    // Launcher for Image file picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.processInvoiceUri(it) }
    }

    // Launcher for Camera photo capture
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            viewModel.processInvoiceUri(photoUri!!)
        }
    }

    LaunchedEffect(extractionState) {
        if (extractionState is ExtractionUiState.Success) {
            val result = (extractionState as ExtractionUiState.Success).result
            onNavigateToReview(result)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                NothingHeader(
                    title = "ADD INVOICE",
                    subtitle = "Select document or image to extract purchase details"
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Options list
                AddOptionCard(
                    title = "IMPORT PDF DOCUMENT",
                    subtitle = "Extract text & table details from electronic PDF receipt",
                    icon = Icons.Default.Description,
                    testTag = "option_import_pdf",
                    onClick = { pdfPickerLauncher.launch("application/pdf") }
                )

                Spacer(modifier = Modifier.height(16.dp))

                AddOptionCard(
                    title = "IMPORT IMAGE FROM GALLERY",
                    subtitle = "Run ML Kit OCR on screenshot or bill photo",
                    icon = Icons.Default.Image,
                    testTag = "option_import_image",
                    onClick = { imagePickerLauncher.launch("image/*") }
                )

                Spacer(modifier = Modifier.height(16.dp))

                AddOptionCard(
                    title = "TAKE A PHOTO OF BILL",
                    subtitle = "Capture invoice using device camera",
                    icon = Icons.Default.CameraAlt,
                    testTag = "option_take_photo",
                    onClick = {
                        try {
                            val tempFile = File(context.cacheDir, "camera_invoice_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                tempFile
                            )
                            photoUri = uri
                            cameraLauncher.launch(uri)
                        } catch (e: Exception) {
                            // Fallback if fileprovider fails
                            imagePickerLauncher.launch("image/*")
                        }
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                // Manual Entry Option
                NothingButton(
                    text = "ENTER DETAILS MANUALLY",
                    isPrimary = false,
                    onClick = {
                        onNavigateToReview(ExtractedInvoiceResult(sellerName = "New Seller"))
                    },
                    testTag = "option_manual_entry"
                )
            }

            // Processing overlay
            AnimatedVisibility(
                visible = extractionState is ExtractionUiState.Processing,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator(
                            color = NothingRed,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "EXTRACTING DETAILS...",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = DotoFontFamily,
                                letterSpacing = 2.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Running PDF text parser, ML Kit OCR, and AI extraction engine",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = SpaceGroteskFontFamily
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Error Overlay
            if (extractionState is ExtractionUiState.Error) {
                val errorMsg = (extractionState as ExtractionUiState.Error).message
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(NothingRed)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "EXTRACTION FAILED",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = DotoFontFamily,
                                letterSpacing = 1.5.sp
                            ),
                            color = NothingRed
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        NothingButton(
                            text = "MANUAL REVIEW & ENTRY",
                            onClick = {
                                viewModel.resetExtractionState()
                                onNavigateToReview(ExtractedInvoiceResult())
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        NothingButton(
                            text = "TRY AGAIN",
                            isPrimary = false,
                            onClick = { viewModel.resetExtractionState() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .testTag(testTag)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
