package com.medocr.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.medocr.app.data.model.Provider
import com.medocr.app.ui.components.StepCard
import com.medocr.app.ui.main.BatchProgress
import com.medocr.app.ui.main.SelectedImage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UploadSection(
    selectedImages: List<SelectedImage>,
    isAnalyzing: Boolean,
    batchProgress: BatchProgress?,
    provider: Provider,
    onImagesPicked: (List<SelectedImage>) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onClearAll: () -> Unit,
    onAnalyzeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // ML Kit Document Scanner handles both camera capture and gallery import in one flow,
    // auto-cropping/deskewing/enhancing the page before it ever reaches our OCR pipeline —
    // so a gallery-picked photo gets the same quality boost as a freshly captured one.
    val launchScanner = rememberDocumentScanner { images -> onImagesPicked(images) }

    StepCard(step = 3, title = "Upload Images", subtitle = "Photos of handwritten medical test lists", modifier = modifier) {
        if (selectedImages.isEmpty()) {
            EmptyUploadZone(onScanClick = launchScanner)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ThumbnailGallery(images = selectedImages, onRemove = onRemoveImage)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${selectedImages.size} image${if (selectedImages.size != 1) "s" else ""} selected",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 4.dp).align(Alignment.CenterVertically),
                    )
                    OutlinedButton(onClick = launchScanner, enabled = !isAnalyzing) { Text("📷 Scan / Add images") }
                    OutlinedButton(onClick = onClearAll, enabled = !isAnalyzing) { Text("✕ Clear all") }
                }

                if (batchProgress != null) {
                    BatchProgressBar(batchProgress)
                }

                Button(
                    onClick = onAnalyzeClick,
                    enabled = !isAnalyzing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Text("  Analysing...", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("🔍 Analyse with ${provider.displayName} AI", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyUploadZone(onScanClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(2.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .padding(vertical = 40.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Scan a page or import from your gallery", style = MaterialTheme.typography.titleSmall)
        Text("Auto-cropped and enhanced before upload", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
            Button(onClick = onScanClick) {
                Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Scan / Add images")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThumbnailGallery(images: List<SelectedImage>, onRemove: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        images.forEachIndexed { index, image ->
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
            ) {
                AsyncImage(
                    model = image.uri,
                    contentDescription = image.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                IconButton(
                    onClick = { onRemove(index) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun BatchProgressBar(progress: BatchProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(99.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            progress.message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Launches ML Kit's Document Scanner (Google Play services) for both capture and gallery
 * import — it auto-detects page edges, corrects perspective/skew, and enhances contrast
 * before handing back JPEG page(s), so the rest of the pipeline always gets a clean scan
 * regardless of source.
 */
@Composable
private fun rememberDocumentScanner(onScanned: (List<SelectedImage>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scanner = remember {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(10)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
    }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data) ?: return@rememberLauncherForActivityResult
        val images = scanResult.pages.orEmpty().mapIndexed { index, page ->
            SelectedImage(page.imageUri, "Scan ${index + 1}")
        }
        if (images.isNotEmpty()) onScanned(images)
    }

    return {
        val activity = context as Activity
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                scanLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
    }
}
