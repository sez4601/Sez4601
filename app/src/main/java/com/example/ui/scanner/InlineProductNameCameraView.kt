package com.example.ui.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun InlineProductNameCameraView(
    onPhotoCaptured: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    if (cameraPermissionState.status.isGranted) {
        ProductNameCameraContent(
            onPhotoCaptured = onPhotoCaptured,
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
                    text = "Ürün ismini fotoğraflayıp otomatik okumak için kamera erişimi gereklidir.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
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
private fun ProductNameCameraContent(
    onPhotoCaptured: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isFlashlightOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isTakingPhoto by remember { mutableStateOf(false) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
            .background(Color.Black)
    ) {
        // Canlı Kamera Önizlemesi
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

                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        imageCapture = capture

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            capture
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Sadece Ürün İsmi Odak Çerçevesi (Barkod ekranı gibi odak alanı ve yarı saydam karartma)
        val infiniteTransition = rememberInfiniteTransition(label = "OcrScanLaser")
        val laserProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "LaserProgress"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            // Ürün adı için özel oranlanmış yatay çerçeve (Barkod ekranı gibi kompakt)
            val boxWidth = canvasWidth * 0.88f
            val boxHeight = canvasHeight * 0.40f
            val left = (canvasWidth - boxWidth) / 2
            val top = (canvasHeight - boxHeight) / 2 - 12.dp.toPx()

            // 1. Çerçeve dışını hafif karart (Barkod vizörü hissi)
            // Üst karartma
            drawRect(color = Color.Black.copy(alpha = 0.45f), topLeft = Offset.Zero, size = Size(canvasWidth, top))
            // Alt karartma
            drawRect(color = Color.Black.copy(alpha = 0.45f), topLeft = Offset(0f, top + boxHeight), size = Size(canvasWidth, canvasHeight - (top + boxHeight)))
            // Sol karartma
            drawRect(color = Color.Black.copy(alpha = 0.45f), topLeft = Offset(0f, top), size = Size(left, boxHeight))
            // Sağ karartma
            drawRect(color = Color.Black.copy(alpha = 0.45f), topLeft = Offset(left + boxWidth, top), size = Size(canvasWidth - (left + boxWidth), boxHeight))

            // 2. Canlı Turkuaz Odak Çerçevesi
            drawRoundRect(
                color = Color(0xFF00E5FF),
                topLeft = Offset(left, top),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx())
            )

            // 3. Lazer Tarama Çizgisi
            val laserY = top + (boxHeight * laserProgress)
            drawLine(
                color = Color(0xFFFF0055).copy(alpha = 0.85f),
                start = Offset(left + 8.dp.toPx(), laserY),
                end = Offset(left + boxWidth - 8.dp.toPx(), laserY),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Üst Kısım: Flaş Butonu ve Bilgi Rozeti
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.75f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
            ) {
                Text(
                    text = "🔍 Yalnızca Ürün İsmini Çerçeveye Getirin",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    maxLines = 1
                )
            }

            IconButton(
                onClick = {
                    val cam = camera
                    if (cam != null && cam.cameraInfo.hasFlashUnit()) {
                        isFlashlightOn = !isFlashlightOn
                        cam.cameraControl.enableTorch(isFlashlightOn)
                    }
                },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .size(34.dp)
            ) {
                Icon(
                    imageVector = if (isFlashlightOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Flaş",
                    tint = if (isFlashlightOn) Color(0xFFFFD600) else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Alt Kısım: Fotoğrafı Çek ve Oku Butonu
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
        ) {
            Button(
                onClick = {
                    val capture = imageCapture ?: return@Button
                    if (isTakingPhoto) return@Button
                    isTakingPhoto = true

                    val photoFile = File.createTempFile("prod_name_${System.currentTimeMillis()}", ".jpg", context.cacheDir)
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                    capture.takePicture(
                        outputOptions,
                        cameraExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    photoFile
                                )
                                ContextCompat.getMainExecutor(context).execute {
                                    isTakingPhoto = false
                                    onPhotoCaptured(uri)
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                exception.printStackTrace()
                                ContextCompat.getMainExecutor(context).execute {
                                    isTakingPhoto = false
                                }
                            }
                        }
                    )
                },
                enabled = !isTakingPhoto,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF22C55E),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier.height(44.dp)
            ) {
                if (isTakingPhoto) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("İsim Okunuyor...", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                } else {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Çek ve Oku",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "İsmi Çek ve Oku",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
