package com.example.ui.scanner

import android.Manifest
import android.annotation.SuppressLint
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.example.util.BarcodeHelper
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun InlineBarcodeCameraView(
    onBarcodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    if (cameraPermissionState.status.isGranted) {
        BarcodeCameraContent(
            onBarcodeDetected = onBarcodeDetected,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier
                .background(Color(0xFF0F2B5C), RoundedCornerShape(16.dp))
                .border(2.dp, Color(0xFF2575FC), RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Kamera İzni Gerekli",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "Kamera İzni Gerekli",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Barkodları otomatik okumak için kameranıza erişim izni vermeniz gerekmektedir.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Kamera İzni Ver", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun BarcodeCameraContent(
    onBarcodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isFlashlightOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var hasScanned by remember { mutableStateOf(false) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "BarcodeLaser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserProgress"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, Color(0xFF2575FC), RoundedCornerShape(16.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null && !hasScanned) {
                                        val image = InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees
                                        )
                                        barcodeScanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                for (barcode in barcodes) {
                                                    val rawValue = barcode.rawValue
                                                    // SADECE 13 HANELİ ARDIŞIK RAKAMLARI AYIKLA
                                                    // Mağaza kodu, tire (-), f, virgül (,), fiyat vb. yabancı kısımları filtreler
                                                    val cleanBarcode = BarcodeHelper.extract13DigitBarcode(rawValue)
                                                    if (!cleanBarcode.isNullOrBlank() && !hasScanned) {
                                                        hasScanned = true
                                                        ContextCompat.getMainExecutor(ctx).execute {
                                                            onBarcodeDetected(cleanBarcode)
                                                        }
                                                        break
                                                    }
                                                }
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
                                }
                            }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay with scanning viewfinder cutout & laser line
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val boxWidth = canvasWidth * 0.88f
            val boxHeight = canvasHeight * 0.58f
            val left = (canvasWidth - boxWidth) / 2
            val top = (canvasHeight - boxHeight) / 2

            // Çerçeve Dışı Yarı Saydam Karartma
            drawRect(color = Color.Black.copy(alpha = 0.45f), topLeft = Offset.Zero, size = Size(canvasWidth, top))
            drawRect(color = Color.Black.copy(alpha = 0.45f), topLeft = Offset(0f, top + boxHeight), size = Size(canvasWidth, canvasHeight - (top + boxHeight)))
            drawRect(color = Color.Black.copy(alpha = 0.45f), topLeft = Offset(0f, top), size = Size(left, boxHeight))
            drawRect(color = Color.Black.copy(alpha = 0.45f), topLeft = Offset(left + boxWidth, top), size = Size(canvasWidth - (left + boxWidth), boxHeight))

            // Canlı Turkuaz Odak Çerçevesi
            drawRoundRect(
                color = Color(0xFF00E5FF),
                topLeft = Offset(left, top),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(14.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Lazer Tarama Çizgisi (Kırmızı / Fuşya)
            val laserY = top + (boxHeight * laserProgress)
            drawLine(
                color = Color(0xFFFF0055).copy(alpha = 0.9f),
                start = Offset(left + 10.dp.toPx(), laserY),
                end = Offset(left + boxWidth - 10.dp.toPx(), laserY),
                strokeWidth = 2.5.dp.toPx()
            )
        }

        // Üst Bilgi Rozeti
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.75f),
            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        ) {
            Text(
                text = "🎯 13 Haneli Barkodu Hizalayın",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                maxLines = 1
            )
        }

        // Flaş Aç/Kapat Butonu
        IconButton(
            onClick = {
                val cam = camera
                if (cam != null && cam.cameraInfo.hasFlashUnit()) {
                    isFlashlightOn = !isFlashlightOn
                    cam.cameraControl.enableTorch(isFlashlightOn)
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                .size(36.dp)
        ) {
            Icon(
                imageVector = if (isFlashlightOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = "Flaş",
                tint = if (isFlashlightOn) Color(0xFFFFD600) else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
