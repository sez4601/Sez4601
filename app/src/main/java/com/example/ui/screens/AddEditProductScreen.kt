package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Categories
import com.example.data.ProductEntity
import com.example.ui.scanner.BarcodeScannerDialog
import com.example.ui.viewmodel.ProductViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductScreen(
    viewModel: ProductViewModel,
    existingProduct: ProductEntity? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(existingProduct?.title ?: "") }
    var selectedCategory by remember { mutableStateOf(existingProduct?.category ?: "Gıda") }
    var shortCode by remember {
        mutableStateOf(existingProduct?.shortCode ?: viewModel.generateRecommendedShortCode())
    }
    var barcode by remember { mutableStateOf(existingProduct?.barcode ?: "") }
    var stockQuantity by remember { mutableIntStateOf(existingProduct?.stockQuantity ?: 1) }
    var priceText by remember { mutableStateOf(existingProduct?.price?.let { if (it > 0) it.toString() else "" } ?: "") }
    var selectedImagePath by remember { mutableStateOf(existingProduct?.imagePath) }

    var activeScannerTarget by remember { mutableStateOf<String?>(null) } // "shortCode" or "barcode"
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val imagesDir = File(context.filesDir, "products").apply { if (!exists()) mkdirs() }
                val targetFile = File(imagesDir, "prod_${UUID.randomUUID()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                selectedImagePath = targetFile.absolutePath
            } catch (e: Exception) {
                selectedImagePath = uri.toString()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (existingProduct == null) "Yeni Ürün Ekle" else "Ürün Düzenle",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D6EFD)
                )
            )
        },
        containerColor = Color(0xFFF5F7FA)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF0D6EFD), Color(0xFF0DCAF0))
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AddShoppingCart,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (existingProduct == null) "Stok Takip Sistemine Ürün Ekle" else "Ürün Bilgilerini Güncelle",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Barkod, stok ve kategori bilgilerini girin",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Error banner
            errorMessage?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Main Form Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Ürün Adı *
                    Column {
                        Text(
                            text = "Ürün Adı *",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF212529)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            placeholder = { Text("Örn: Ekmek, Süt 1L, Deterjan") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    // Kategori Selection *
                    Column {
                        Text(
                            text = "Kategori *",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF212529)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val categories = Categories.defaultList
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Render 2 rows or grid of categories
                            categories.chunked(3).forEach { rowList ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowList.forEach { cat ->
                                        val isSelected = selectedCategory == cat.name
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isSelected) cat.displayColor.copy(alpha = 0.15f)
                                                    else Color(0xFFF8F9FA)
                                                )
                                                .border(
                                                    width = if (isSelected) 2.5.dp else 1.dp,
                                                    color = if (isSelected) cat.displayColor else Color(0xFFDEE2E6),
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .clickable { selectedCategory = cat.name }
                                                .padding(vertical = 10.dp, horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(cat.displayColor, CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = cat.name,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) cat.displayColor else Color(0xFF495057),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Kısa Kod * (Max 8 hane)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Kısa Kod * (Max 8 hane)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF212529)
                            )
                            TextButton(
                                onClick = { shortCode = viewModel.generateRecommendedShortCode() },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kod Üret", fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = shortCode,
                                onValueChange = {
                                    val filtered = it.filter { ch -> ch.isLetterOrDigit() }.take(8)
                                    shortCode = filtered
                                },
                                placeholder = { Text("EKM101") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                            )
                            Button(
                                onClick = { activeScannerTarget = "shortCode" },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Tara",
                                    tint = Color.Black
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tara", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = "Etiket üstündeki iç kodu tarayabilir veya girebilirsiniz",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // Barkod (13 hane EAN-13)
                    Column {
                        Text(
                            text = "Barkod (13 hane EAN-13)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF212529)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = barcode,
                                onValueChange = {
                                    val digits = it.filter { ch -> ch.isDigit() }.take(13)
                                    barcode = digits
                                },
                                placeholder = { Text("8690123456789") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Button(
                                onClick = { activeScannerTarget = "barcode" },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0DCAF0)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Tara",
                                    tint = Color.Black
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tara", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = "Ürün üzerindeki orijinal EAN-13 barkodu tarayın",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // Stock Quantity & Price
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Stok Adedi
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Stok Adedi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF212529)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFFDEE2E6), RoundedCornerShape(12.dp))
                                    .padding(4.dp)
                            ) {
                                IconButton(
                                    onClick = { if (stockQuantity > 0) stockQuantity-- },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Azalt"
                                    )
                                }
                                Text(
                                    text = stockQuantity.toString(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                IconButton(
                                    onClick = { stockQuantity++ },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Artır"
                                    )
                                }
                            }
                        }

                        // Fiyat (TL)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fiyat (₺)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF212529)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = priceText,
                                onValueChange = { priceText = it },
                                placeholder = { Text("0.00") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                    }

                    // Fotoğraf
                    Column {
                        Text(
                            text = "Ürün Fotoğrafı",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF212529)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE9ECEF))
                                    .border(1.dp, Color(0xFFCED4DA), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!selectedImagePath.isNullOrBlank()) {
                                    AsyncImage(
                                        model = selectedImagePath,
                                        contentDescription = "Ürün görseli",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Column {
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Fotoğraf Seç")
                                }
                                if (!selectedImagePath.isNullOrBlank()) {
                                    TextButton(
                                        onClick = { selectedImagePath = null },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Kaldır", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Kaydet Button
                    Button(
                        onClick = {
                            errorMessage = null
                            val price = priceText.toDoubleOrNull() ?: 0.0
                            viewModel.addOrUpdateProduct(
                                id = existingProduct?.id ?: 0,
                                title = title,
                                category = selectedCategory,
                                shortCode = shortCode,
                                barcode = barcode,
                                imagePath = selectedImagePath,
                                stockQuantity = stockQuantity,
                                price = price,
                                onSuccess = onNavigateBack
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF198754)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "💾 KAYDET",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }

                    // İptal Button
                    OutlinedButton(
                        onClick = onNavigateBack,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = "İptal",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF6C757D)
                        )
                    }
                }
            }
        }
    }

    // Active Barcode Scanner Modal Sheet
    activeScannerTarget?.let { target ->
        BarcodeScannerDialog(
            title = if (target == "shortCode") "📷 Kısa Kod Tara" else "📷 Barkod Tara",
            isShortCodeMode = target == "shortCode",
            onBarcodeDetected = { scannedCode ->
                if (target == "shortCode") {
                    shortCode = scannedCode
                } else {
                    barcode = scannedCode
                }
                activeScannerTarget = null
            },
            onDismiss = { activeScannerTarget = null }
        )
    }
}
