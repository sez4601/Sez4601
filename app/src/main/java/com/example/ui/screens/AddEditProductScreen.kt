package com.example.ui.screens

import android.app.DatePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.Categories
import com.example.data.ProductEntity
import com.example.ui.scanner.BarcodeScannerView
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProductViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

enum class AddProductTab(val title: String) {
    BARCODE("Barkod"),
    INFO("Ürün Adı"),
    IMAGES("Görüntü")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductScreen(
    productId: Long = 0,
    viewModel: ProductViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(AddProductTab.INFO) }

    // ViewModel Form State (Tek Gerçeklik Kaynağı)
    val formState by viewModel.addProductFormState.collectAsState()
    val isSearchingBarcode by viewModel.isSearchingBarcode.collectAsState()
    val barcodeSearchResult by viewModel.barcodeSearchResult.collectAsState()

    var showBarcodeScanner by remember { mutableStateOf(false) }
    var tempCameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var targetCameraField by remember { mutableStateOf("product") } // "product" or "label"

    // Fotoğraf büyütme önizleme diyaloğu
    var previewImageUri by remember { mutableStateOf<String?>(null) }

    // Ekran açılışında form başlatma
    LaunchedEffect(productId) {
        viewModel.initializeFormForProduct(productId)
    }

    // Barkod arama sonucu geldiğinde otomatik Ürün Bilgileri sekmesine geç
    LaunchedEffect(barcodeSearchResult) {
        val result = barcodeSearchResult
        if (result != null && !formState.isEditMode) {
            selectedTab = AddProductTab.INFO
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearBarcodeSearchResult()
        }
    }

    // Galeri Seçici
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            if (targetCameraField == "product") {
                viewModel.updateProductImageUri(uri.toString())
            } else {
                viewModel.updateProductLabelImageUri(uri.toString())
            }
        }
    }

    // Kamera Çekim Başlatıcı
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraImageUri != null) {
            if (targetCameraField == "product") {
                viewModel.updateProductImageUri(tempCameraImageUri.toString())
            } else {
                viewModel.updateProductLabelImageUri(tempCameraImageUri.toString())
            }
        }
    }

    fun openCamera(field: String) {
        try {
            targetCameraField = field
            val photoFile = File.createTempFile("skt_img_${System.currentTimeMillis()}", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
            tempCameraImageUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            // Fallback: doğrudan galeri aç
            galleryLauncher.launch("image/*")
        }
    }

    // Tarih seçici açma fonksiyonu
    fun openDatePicker(currentDateStr: String, onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        try {
            if (currentDateStr.isNotBlank()) {
                val parsed = LocalDate.parse(currentDateStr)
                calendar.set(parsed.year, parsed.monthValue - 1, parsed.dayOfMonth)
            }
        } catch (e: Exception) {}

        val datePickerDialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selected = LocalDate.of(year, month + 1, dayOfMonth)
                onDateSelected(selected.toString())
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (formState.isEditMode) "Ürün Düzenle" else "Yeni Ürün Ekle",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.saveProductFromForm(onNavigateBack)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Kaydet",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BluePrimary
                )
            )
        },
        containerColor = LightBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Menüsü (Barkod | Ürün Adı | Görüntü)
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color.White,
                contentColor = BluePrimary,
                modifier = Modifier.fillMaxWidth()
            ) {
                AddProductTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = tab.title,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            when (tab) {
                                AddProductTab.BARCODE -> Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                                AddProductTab.INFO -> Icon(Icons.Default.EditNote, contentDescription = null)
                                AddProductTab.IMAGES -> Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            }
                        }
                    )
                }
            }

            // Tab İçerikleri
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    AddProductTab.BARCODE -> {
                        // Barkod Sekmesi
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Text(
                                        text = "Barkod & Karekod Tarama",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = TextPrimary
                                    )

                                    OutlinedTextField(
                                        value = formState.barcode,
                                        onValueChange = { newBarcode ->
                                            viewModel.updateProductBarcode(newBarcode)
                                            if (newBarcode.length >= 8) {
                                                viewModel.fetchProductInfoByBarcode(newBarcode)
                                            }
                                        },
                                        label = { Text("Barkod Numarası (EAN-13 / EAN-8)") },
                                        placeholder = { Text("Örn: 8690637011403") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        trailingIcon = {
                                            if (formState.barcode.isNotBlank()) {
                                                IconButton(onClick = { viewModel.updateProductBarcode("") }) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Temizle")
                                                }
                                            }
                                        }
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { showBarcodeScanner = true },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                                        ) {
                                            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Kamera ile Tara", fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                if (formState.barcode.isNotBlank()) {
                                                    viewModel.fetchProductInfoByBarcode(formState.barcode) { res ->
                                                        if (res != null) {
                                                            selectedTab = AddProductTab.INFO
                                                        }
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Lütfen önce barkod girin veya tarayın", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.weight(1.1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = WarningYellow),
                                            enabled = !isSearchingBarcode
                                        ) {
                                            if (isSearchingBarcode) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    color = Color.White,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Aranıyor...", fontSize = 13.sp, color = Color.White)
                                            } else {
                                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Otomatik Getir", fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }

                            // Yükleniyor Göstergesi
                            AnimatedVisibility(visible = isSearchingBarcode) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = BluePrimary,
                                            strokeWidth = 3.dp
                                        )
                                        Column {
                                            Text(
                                                text = "Ürün Bilgileri Aranıyor...",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = BluePrimary
                                            )
                                            Text(
                                                text = "Open Food Facts veritabanı ve Gemini AI taranıyor",
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            // Bulunan Ürün Kartı
                            AnimatedVisibility(visible = barcodeSearchResult != null && !isSearchingBarcode) {
                                val result = barcodeSearchResult
                                if (result != null) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                                        border = BorderStroke(1.dp, Color(0xFF86EFAC))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "✨ Ürün Bilgisi Bulundu",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = GreenSuccess
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFFDCFCE7)
                                                ) {
                                                    Text(
                                                        text = result.source,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = GreenSuccess,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }

                                            Text(
                                                text = result.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = TextPrimary
                                            )

                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFE2E8F0)
                                                ) {
                                                    Text(
                                                        text = "📂 ${result.category}",
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                if (result.brand != null) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color(0xFFE2E8F0)
                                                    ) {
                                                        Text(
                                                            text = "🏷️ ${result.brand}",
                                                            fontSize = 12.sp,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    selectedTab = AddProductTab.INFO
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                                            ) {
                                                Text("Bu Bilgileri Kullan ve Devam Et")
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(Icons.Default.ArrowForward, contentDescription = null)
                                            }
                                        }
                                    }
                                }
                            }

                            // Hızlı Test Barkodları
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "⚡ Hızlı Test Barkodları",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Aşağıdaki örnek barkodlara dokunarak API / Gemini otomatik ürün bulma özelliğini test edebilirsiniz:",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )

                                    val sampleCodes = listOf(
                                        "8690637011403" to "Pınar Süt 1L (Süt)",
                                        "8690504033323" to "Ülker Çikolatalı Gofret",
                                        "7622210449283" to "Jacobs Kahve (İçecek)",
                                        "8690506087508" to "Tadım Kavrulmuş Fındık"
                                    )

                                    sampleCodes.forEach { (code, label) ->
                                        OutlinedCard(
                                            onClick = {
                                                viewModel.fetchProductInfoByBarcode(code) { res ->
                                                    if (res != null) {
                                                        selectedTab = AddProductTab.INFO
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFF8FAFC))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                    Text(text = code, fontSize = 11.sp, color = TextSecondary)
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = BluePrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Hızlı İlerle Butonu
                            Button(
                                onClick = { selectedTab = AddProductTab.INFO },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                            ) {
                                Text("Devam Et: Ürün Bilgileri")
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = null)
                            }
                        }
                    }

                    AddProductTab.INFO -> {
                        // Ürün Bilgileri Sekmesi
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Otomatik Doldurulan Bilgi Kartı (Gemini AI / API)
                            AnimatedVisibility(visible = formState.isAutoPopulated || barcodeSearchResult != null) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF0FDF4),
                                    border = BorderStroke(1.dp, Color(0xFF86EFAC))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = GreenSuccess,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Ürün Adı ve Kategori Otomatik Dolduruldu",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = GreenSuccess
                                            )
                                            Text(
                                                text = "Kaynak: ${formState.autoPopulatedSource ?: barcodeSearchResult?.source ?: "Gemini AI"}${if (formState.autoPopulatedBrand != null) " • Marka: ${formState.autoPopulatedBrand}" else ""}",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            // Ürün Adı
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Temel Bilgiler",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = TextPrimary
                                        )
                                        if (formState.barcode.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFE0F2FE)
                                            ) {
                                                Text(
                                                    text = "Barkod: ${formState.barcode}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = BluePrimary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = formState.name,
                                        onValueChange = { viewModel.updateProductName(it) },
                                        label = { Text("Ürün Adı *") },
                                        placeholder = { Text("Örn: Süt 1L, Kaşar Peynir") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    // Kategori Seçici
                                    var expandedCategory by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = expandedCategory,
                                        onExpandedChange = { expandedCategory = it }
                                    ) {
                                        OutlinedTextField(
                                            value = formState.category,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Kategori") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor()
                                        )
                                        ExposedDropdownMenu(
                                            expanded = expandedCategory,
                                            onDismissRequest = { expandedCategory = false }
                                        ) {
                                            Categories.formCategories.forEach { cat ->
                                                DropdownMenuItem(
                                                    text = { Text(cat) },
                                                    onClick = {
                                                        viewModel.updateProductCategory(cat)
                                                        expandedCategory = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Adet / Miktar
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Adet / Miktar:", fontWeight = FontWeight.Medium)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            IconButton(
                                                onClick = { if (formState.quantity > 1) viewModel.updateProductQuantity(formState.quantity - 1) },
                                                modifier = Modifier
                                                    .background(Color(0xFFE2E8F0), CircleShape)
                                                    .size(36.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Azalt")
                                            }
                                            Text(
                                                text = formState.quantity.toString(),
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            )
                                            IconButton(
                                                onClick = { viewModel.updateProductQuantity(formState.quantity + 1) },
                                                modifier = Modifier
                                                    .background(Color(0xFFE2E8F0), CircleShape)
                                                    .size(36.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Arttır")
                                            }
                                        }
                                    }
                                }
                            }

                            // Son Kullanma Tarihi & Üretim Tarihi
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Tarih Bilgileri",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = TextPrimary
                                    )

                                    // SKT Seçim Alanı
                                    OutlinedCard(
                                        onClick = {
                                            openDatePicker(formState.expiryDate) { selected ->
                                                viewModel.updateProductExpiryDate(selected)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFFF8FAFC))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Son Kullanma Tarihi (SKT) *",
                                                    fontSize = 12.sp,
                                                    color = TextSecondary
                                                )
                                                Text(
                                                    text = formState.expiryDate.ifBlank { "Tarih Seçiniz" },
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (formState.expiryDate.isNotBlank()) BluePrimary else Color.Gray
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.CalendarMonth,
                                                contentDescription = "Tarih Seç",
                                                tint = BluePrimary
                                            )
                                        }
                                    }

                                    // Hızlı SKT Butonları (+1 Ay, +3 Ay, +6 Ay, +1 Yıl)
                                    Text("Hızlı Ekle:", fontSize = 12.sp, color = TextSecondary)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            Pair("+1 Ay", 1L),
                                            Pair("+3 Ay", 3L),
                                            Pair("+6 Ay", 6L),
                                            Pair("+1 Yıl", 12L)
                                        ).forEach { (label, months) ->
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.updateProductExpiryDate(LocalDate.now().plusMonths(months).toString())
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                            ) {
                                                Text(label, fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    // Not Alanı
                                    OutlinedTextField(
                                        value = formState.note,
                                        onValueChange = { viewModel.updateProductNote(it) },
                                        label = { Text("Açıklama / Not") },
                                        placeholder = { Text("Örn: Buzdolabının 2. rafında") },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 3
                                    )
                                }
                            }

                            // İleri / Kaydet Butonları
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { selectedTab = AddProductTab.IMAGES },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Fotoğraf Ekle")
                                }

                                Button(
                                    onClick = {
                                        viewModel.saveProductFromForm(onNavigateBack)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Kaydet")
                                }
                            }
                        }
                    }

                    AddProductTab.IMAGES -> {
                        // Görüntü / Fotoğraf Sekmesi
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Ürün Fotoğrafı Kartı
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "1. Ürün Fotoğrafı",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = TextPrimary
                                    )

                                    if (formState.imageUri != null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFFF1F5F9))
                                                .clickable { previewImageUri = formState.imageUri }
                                        ) {
                                            AsyncImage(
                                                model = formState.imageUri,
                                                contentDescription = "Ürün Fotoğrafı",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            IconButton(
                                                onClick = { viewModel.updateProductImageUri(null) },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                                    .size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Kaldır", tint = Color.White)
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { openCamera("product") },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Kamera")
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    targetCameraField = "product"
                                                    galleryLauncher.launch("image/*")
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Galeri")
                                            }
                                        }
                                    }
                                }
                            }

                            // SKT / Etiket Fotoğrafı Kartı
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "2. SKT / Etiket Fotoğrafı",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = TextPrimary
                                    )

                                    if (formState.labelImageUri != null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFFF1F5F9))
                                                .clickable { previewImageUri = formState.labelImageUri }
                                        ) {
                                            AsyncImage(
                                                model = formState.labelImageUri,
                                                contentDescription = "Etiket Fotoğrafı",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            IconButton(
                                                onClick = { viewModel.updateProductLabelImageUri(null) },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                                    .size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Kaldır", tint = Color.White)
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { openCamera("label") },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Kamera")
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    targetCameraField = "label"
                                                    galleryLauncher.launch("image/*")
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Galeri")
                                            }
                                        }
                                    }
                                }
                            }

                            // Kaydet Butonu
                            Button(
                                onClick = {
                                    viewModel.saveProductFromForm(onNavigateBack)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ürünü Kaydet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Barkod Tarama Tam Ekran Diyaloğu
    if (showBarcodeScanner) {
        Dialog(onDismissRequest = { showBarcodeScanner = false }) {
            BarcodeScannerView(
                onBarcodeDetected = { scannedCode ->
                    showBarcodeScanner = false
                    viewModel.playSuccessFeedback()
                    Toast.makeText(context, "Barkod Okundu: $scannedCode\nÜrün bilgileri aranıyor...", Toast.LENGTH_SHORT).show()
                    viewModel.fetchProductInfoByBarcode(scannedCode) { res ->
                        selectedTab = AddProductTab.INFO
                    }
                    selectedTab = AddProductTab.INFO
                },
                onClose = { showBarcodeScanner = false }
            )
        }
    }

    // Tam Ekran Fotoğraf Önizleme
    if (previewImageUri != null) {
        Dialog(onDismissRequest = { previewImageUri = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { previewImageUri = null }
            ) {
                AsyncImage(
                    model = previewImageUri,
                    contentDescription = "Önizleme",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { previewImageUri = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                }
            }
        }
    }
}
