package com.example.ui.screens

import android.app.DatePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.Categories
import com.example.data.ProductEntity
import com.example.data.ProductStatus
import com.example.ui.scanner.InlineBarcodeCameraView
import com.example.ui.scanner.InlineProductNameCameraView
import com.example.ui.scanner.LivePhotoCaptureDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProductViewModel
import com.example.ui.viewmodel.SortType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

enum class MainNavTab {
    PRODUCTS,   // "Ürünler"
    ADD_PRODUCT, // "Ürün Ekle"
    SETTINGS    // "Ayarlar"
}

enum class FormInnerTab {
    BARCODE, // "Barkod"
    NAME,    // "Ürün Adı"
    IMAGE    // "Görüntü"
}

@Composable
fun MainAppScreen(
    viewModel: ProductViewModel
) {
    val context = LocalContext.current

    // Ana Tab (Ürünler / Ürün Ekle / Ayarlar) - Varsayılan ÜRÜNLER (Ana Ekran)
    var currentMainTab by remember { mutableStateOf(MainNavTab.PRODUCTS) }
    // Ürün Ekle Formunun İç Sekmesi (Barkod / Ürün Adı / Görüntü)
    var currentFormTab by remember { mutableStateOf(FormInnerTab.BARCODE) }

    // Ürünler, Filtreler ve Form State
    val products by viewModel.products.collectAsState()
    val allProducts by viewModel.allProductsList.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedSortType by viewModel.selectedSortType.collectAsState()
    val selectedStatusFilter by viewModel.selectedStatusFilter.collectAsState()
    val formState by viewModel.addProductFormState.collectAsState()
    val isSearchingBarcode by viewModel.isSearchingBarcode.collectAsState()
    val barcodeSearchResult by viewModel.barcodeSearchResult.collectAsState()

    // Kamera / Diyalog durumları
    var isBarcodeCameraOpen by remember { mutableStateOf(false) }
    var isNameCameraOpen by remember { mutableStateOf(false) }
    var activeCameraCaptureField by remember { mutableStateOf<String?>(null) } // "product" or "label"
    var previewImageUri by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmProduct by remember { mutableStateOf<ProductEntity?>(null) }

    // Galeri Başlatıcı (Ürün Adı OCR, Ürün Fotoğrafı veya Etiket)
    var targetGalleryField by remember { mutableStateOf("name_ocr") }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            when (targetGalleryField) {
                "name_ocr" -> {
                    // Yalnızca Ürün Adı sekmesinde OCR çalıştırıp metin satırlarını listele
                    viewModel.processImageWithOcr(context, uri, autoFillName = formState.name.isBlank())
                }
                "product_image" -> {
                    // Görüntü sekmesinde SADECE ürün fotoğrafını ekle, İSME ASLA DOKUNMA
                    viewModel.updateProductImageUri(uri.toString())
                    viewModel.playSuccessFeedback()
                }
                "label_image" -> {
                    // Sadece etiket fotoğrafını ekle, İSME ASLA DOKUNMA
                    viewModel.updateProductLabelImageUri(uri.toString())
                    viewModel.playSuccessFeedback()
                }
            }
        }
    }

    // Kullanıcı bildirim mesajları (Toast)
    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Barkod arama sonucu başarılı olduğunda otomatik Ürün Adı sekmesine geç
    LaunchedEffect(barcodeSearchResult) {
        if (barcodeSearchResult != null) {
            currentFormTab = FormInnerTab.NAME
        }
    }

    // Tarih Seçici Dialog Fonksiyonu
    fun openDatePickerDialog(currentDateStr: String, onDatePicked: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        try {
            if (currentDateStr.isNotBlank()) {
                val parsed = LocalDate.parse(currentDateStr)
                calendar.set(parsed.year, parsed.monthValue - 1, parsed.dayOfMonth)
            }
        } catch (e: Exception) {}

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                onDatePicked(selectedDate.toString())
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // Ana Arka Plan: Koyu Mavi Gradyan
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MainGradientBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ==========================================
            // 1. ÜST BAŞLIK (HEADER)
            // ==========================================
            TopHeaderSection(
                productCount = allProducts.size,
                isSettingsActive = currentMainTab == MainNavTab.SETTINGS,
                onSettingsClick = {
                    currentMainTab = if (currentMainTab == MainNavTab.SETTINGS) {
                        MainNavTab.PRODUCTS
                    } else {
                        MainNavTab.SETTINGS
                    }
                }
            )

            // ==========================================
            // 2. ORTA İÇERİK ALANI
            // ==========================================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                AnimatedContent(
                    targetState = currentMainTab,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220)))
                            .togetherWith(fadeOut(animationSpec = tween(180)))
                    },
                    label = "MainTabAnimation"
                ) { targetMainTab ->
                    when (targetMainTab) {
                        MainNavTab.SETTINGS -> {
                            // AYARLAR EKRANI (Görseldeki ile birebir aynı)
                            SettingsScreenContent(
                                viewModel = viewModel,
                                products = allProducts,
                                onNavigateToProducts = { currentMainTab = MainNavTab.PRODUCTS },
                                onNavigateToAddProduct = { currentMainTab = MainNavTab.ADD_PRODUCT }
                            )
                        }

                        MainNavTab.ADD_PRODUCT -> {
                            // --- ÜRÜN EKLE / DÜZENLE FORMU ---
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // 3'LÜ SEKMELER: Barkod | Ürün Adı | Görüntü
                                FormTabsRow(
                                    selectedTab = currentFormTab,
                                    onTabSelected = { currentFormTab = it }
                                )

                                // SEKME İÇERİKLERİ (Animasyonlu geçiş)
                                AnimatedContent(
                                    targetState = currentFormTab,
                                    transitionSpec = {
                                        (fadeIn(animationSpec = tween(200)) + slideInHorizontally(animationSpec = tween(200)) { it / 5 })
                                            .togetherWith(fadeOut(animationSpec = tween(160)) + slideOutHorizontally(animationSpec = tween(160)) { -it / 5 })
                                    },
                                    label = "FormTabAnimation"
                                ) { targetTab ->
                                    when (targetTab) {
                                        FormInnerTab.BARCODE -> {
                                            BarcodeTabCard(
                                                barcodeValue = formState.barcode,
                                                isCameraOpen = isBarcodeCameraOpen,
                                                isSearching = isSearchingBarcode,
                                                onBarcodeChange = { newBarcode ->
                                                    viewModel.updateProductBarcode(newBarcode)
                                                    if (newBarcode.length >= 8) {
                                                        viewModel.fetchProductInfoByBarcode(newBarcode)
                                                    }
                                                },
                                                onToggleCamera = { isBarcodeCameraOpen = !isBarcodeCameraOpen },
                                                onBarcodeDetected = { scannedCode ->
                                                    viewModel.updateProductBarcode(scannedCode)
                                                    isBarcodeCameraOpen = false
                                                    viewModel.playSuccessFeedback()
                                                    viewModel.fetchProductInfoByBarcode(scannedCode) {
                                                        currentFormTab = FormInnerTab.NAME
                                                    }
                                                    currentFormTab = FormInnerTab.NAME
                                                }
                                            )
                                        }

                                        FormInnerTab.NAME -> {
                                            ProductInfoTabCard(
                                                formState = formState,
                                                isCameraOpen = isNameCameraOpen,
                                                onToggleCamera = { isNameCameraOpen = !isNameCameraOpen },
                                                onPhotoCaptured = { uri ->
                                                    viewModel.processImageWithOcr(context, uri, autoFillName = true)
                                                    isNameCameraOpen = false
                                                    viewModel.playSuccessFeedback()
                                                },
                                                onNameChange = { viewModel.updateProductName(it) },
                                                onExpiryDateClick = {
                                                    openDatePickerDialog(formState.expiryDate) { picked ->
                                                        viewModel.updateProductExpiryDate(picked)
                                                    }
                                                },
                                                onGalleryClick = {
                                                    targetGalleryField = "name_ocr"
                                                    galleryLauncher.launch("image/*")
                                                },
                                                onSelectOcrText = { selected ->
                                                    viewModel.selectOcrTextAsName(selected)
                                                },
                                                onAppendOcrText = { textToAppend ->
                                                    viewModel.appendOcrTextToName(textToAppend)
                                                },
                                                onClearOcr = {
                                                    viewModel.clearOcrResults()
                                                },
                                                onNextToImages = {
                                                    currentFormTab = FormInnerTab.IMAGE
                                                }
                                            )
                                        }

                                        FormInnerTab.IMAGE -> {
                                            ProductImageTabCard(
                                                formState = formState,
                                                onCaptureProductPhoto = { activeCameraCaptureField = "product_image" },
                                                onPickProductGallery = {
                                                    targetGalleryField = "product_image"
                                                    galleryLauncher.launch("image/*")
                                                },
                                                onCaptureLabelPhoto = { activeCameraCaptureField = "label_image" },
                                                onPickLabelGallery = {
                                                    targetGalleryField = "label_image"
                                                    galleryLauncher.launch("image/*")
                                                },
                                                onClearProductImage = { viewModel.updateProductImageUri(null) },
                                                onClearLabelImage = { viewModel.updateProductLabelImageUri(null) },
                                                onImageClick = { previewImageUri = it },
                                                onSaveProduct = {
                                                    if (formState.name.isBlank()) {
                                                        Toast.makeText(context, "Lütfen ürün ismini doldurun!", Toast.LENGTH_SHORT).show()
                                                        currentFormTab = FormInnerTab.NAME
                                                    } else {
                                                        viewModel.saveProductFromForm {
                                                            viewModel.playSuccessFeedback()
                                                            currentMainTab = MainNavTab.PRODUCTS
                                                        }
                                                    }
                                                },
                                                onBackToName = {
                                                    currentFormTab = FormInnerTab.NAME
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        MainNavTab.PRODUCTS -> {
                            // --- ÜRÜNLER LİSTESİ (Görseldeki Ana Ekran Birebir) ---
                            ProductsListTabContent(
                                products = products,
                                allProducts = allProducts,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                selectedSortType = selectedSortType,
                                onSortTypeChange = { viewModel.setSortType(it) },
                                selectedStatusFilter = selectedStatusFilter,
                                onStatusFilterClick = { viewModel.toggleStatusFilter(it) },
                                onEditProduct = { prod ->
                                    viewModel.initializeFormForProduct(prod.id)
                                    currentMainTab = MainNavTab.ADD_PRODUCT
                                    currentFormTab = FormInnerTab.NAME
                                },
                                onDeleteProduct = { prod ->
                                    showDeleteConfirmProduct = prod
                                },
                                onImageClick = { previewImageUri = it },
                                onNavigateToAdd = {
                                    viewModel.resetAddProductForm()
                                    currentMainTab = MainNavTab.ADD_PRODUCT
                                    currentFormTab = FormInnerTab.BARCODE
                                }
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 3. ALT NAVİGASYON ÇUBUĞU (BOTTOM BAR)
            // ==========================================
            BottomNavBarSection(
                activeTab = currentMainTab,
                onTabSelect = { tab ->
                    if (tab == MainNavTab.ADD_PRODUCT && currentMainTab != MainNavTab.ADD_PRODUCT) {
                        viewModel.resetAddProductForm()
                        currentFormTab = FormInnerTab.BARCODE
                    }
                    currentMainTab = tab
                },
                onSaveProduct = {
                    viewModel.saveProductFromForm {
                        viewModel.playSuccessFeedback()
                        currentMainTab = MainNavTab.PRODUCTS
                    }
                },
                isEditMode = formState.isEditMode
            )
        }
    }

    // ==========================================
    // CANLI KAMERA FOTOĞRAF ÇEKME DİYALOĞU
    // ==========================================
    if (activeCameraCaptureField != null) {
        val field = activeCameraCaptureField
        val isLabel = field == "label_image" || field == "label"
        LivePhotoCaptureDialog(
            title = if (isLabel) "Etiket / SKT Fotoğrafı Çek" else "Ürün Fotoğrafı Çek",
            onPhotoCaptured = { uri ->
                if (isLabel) {
                    viewModel.updateProductLabelImageUri(uri.toString())
                } else {
                    // Görüntü sekmesinde SADECE ürün kartına fotoğrafı ekle, İSME DOKUNMA
                    viewModel.updateProductImageUri(uri.toString())
                }
                activeCameraCaptureField = null
                viewModel.playSuccessFeedback()
            },
            onDismiss = { activeCameraCaptureField = null }
        )
    }

    // Fotoğraf Tam Ekran Önizleme Diyaloğu
    if (previewImageUri != null) {
        Dialog(onDismissRequest = { previewImageUri = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = previewImageUri,
                        contentDescription = "Büyük Önizleme",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { previewImageUri = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0084FF))
                    ) {
                        Text("Kapat", color = Color.White)
                    }
                }
            }
        }
    }

    // Tek Ürün Silme Onay Diyaloğu
    showDeleteConfirmProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmProduct = null },
            title = { Text("Ürünü Sil?", fontWeight = FontWeight.Bold) },
            text = { Text("'${product.name}' listelerden kalıcı olarak silinecek.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProduct(product)
                        showDeleteConfirmProduct = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("Sil", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmProduct = null }) {
                    Text("İptal")
                }
            }
        )
    }
}

// -------------------------------------------------------------------------
// 1. ÜST BAŞLIK (HEADER SECTION)
// -------------------------------------------------------------------------
@Composable
fun TopHeaderSection(
    productCount: Int,
    isSettingsActive: Boolean,
    onSettingsClick: () -> Unit
) {
    // Canlı Tarih ve Saat
    val trLocale = Locale("tr", "TR")
    val dateFormat = remember { SimpleDateFormat("d MMMM yyyy EEEE", trLocale) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", trLocale) }

    var currentTimeStr by remember { mutableStateOf(timeFormat.format(Date())) }
    val currentDateStr = remember { dateFormat.format(Date()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeStr = timeFormat.format(Date())
            delay(10000)
        }
    }

    val settingsRotation by animateFloatAsState(
        targetValue = if (isSettingsActive) 90f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "SettingsRotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sol Logo ve Başlık (Ekrana göre dinamik sığar)
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Sarı Koli/Kutu İkonu
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF59E0B),
                shadowElevation = 4.dp,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = "SKT TAKİP",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
                Text(
                    text = "Son Kullanma Tarihi Kontrol",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF).copy(alpha = 0.8f),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "$currentDateStr • $currentTimeStr",
                        color = Color(0xFF00E5FF).copy(alpha = 0.9f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Sağ: Belirgin ve Ayrık Butonlar (Toplam Ürün Rozeti + Ayarlar Butonu)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp) // Geniş ve net aralık
        ) {
            // 1. TOPLAM ÜRÜN ROZETİ (Özel Turkuaz/Zümrüt Gradyanlı, Belirgin ve Şık)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0369A1), // Parlak Canlı Mavi-Turkuaz
                border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = productCount.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "toplam",
                        color = Color(0xFFE0F2FE),
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
                        lineHeight = 11.sp
                    )
                }
            }

            // 2. AYARLAR BUTONU (Ayrık Canlı Mor/Violet Renk - Üzerine binmez)
            val settingsBgColor by animateColorAsState(
                targetValue = if (isSettingsActive) Color(0xFF9333EA) else Color(0xFF6B21A8).copy(alpha = 0.85f),
                label = "SettingsBg"
            )
            val settingsBorderColor by animateColorAsState(
                targetValue = if (isSettingsActive) Color(0xFFE9D5FF) else Color(0xFFA855F7),
                label = "SettingsBorder"
            )

            Surface(
                onClick = onSettingsClick,
                shape = RoundedCornerShape(12.dp),
                color = settingsBgColor,
                border = BorderStroke(1.5.dp, settingsBorderColor),
                shadowElevation = 3.dp,
                modifier = Modifier.size(42.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ayarlar",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer(rotationZ = settingsRotation)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// 2. FORM ÜSTÜ 3'LÜ SEKMELER: Barkod | Ürün Adı | Görüntü
// -------------------------------------------------------------------------
@Composable
fun FormTabsRow(
    selectedTab: FormInnerTab,
    onTabSelected: (FormInnerTab) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FormTabButton(
            title = "Barkod",
            icon = Icons.Default.QrCode,
            isSelected = selectedTab == FormInnerTab.BARCODE,
            onClick = { onTabSelected(FormInnerTab.BARCODE) },
            modifier = Modifier.weight(1f)
        )
        FormTabButton(
            title = "Ürün Adı",
            icon = Icons.Default.Description,
            isSelected = selectedTab == FormInnerTab.NAME,
            onClick = { onTabSelected(FormInnerTab.NAME) },
            modifier = Modifier.weight(1f)
        )
        FormTabButton(
            title = "Görüntü",
            icon = Icons.Default.PhotoCamera,
            isSelected = selectedTab == FormInnerTab.IMAGE,
            onClick = { onTabSelected(FormInnerTab.IMAGE) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun FormTabButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF0084FF) else Color(0x33FFFFFF),
        label = "FormTabBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF60A5FA) else Color(0x22FFFFFF),
        label = "FormTabBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1.0f,
        label = "FormTabScale"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(64.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale),
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = title,
                color = Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}

// -------------------------------------------------------------------------
// BARKOD SEKMESİ KARTI
// -------------------------------------------------------------------------
@Composable
fun BarcodeTabCard(
    barcodeValue: String,
    isCameraOpen: Boolean,
    isSearching: Boolean,
    onBarcodeChange: (String) -> Unit,
    onToggleCamera: () -> Unit,
    onBarcodeDetected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D3B7A)),
        border = BorderStroke(1.5.dp, Color(0xFF2575FC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Başlık
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Barkod Tarama",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }

            // KAMERA ALANI VEYA AÇMA BUTONU
            if (isCameraOpen) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
                    ) {
                        InlineBarcodeCameraView(
                            onBarcodeDetected = onBarcodeDetected,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Button(
                        onClick = onToggleCamera,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kamerayı Kapat", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = onToggleCamera,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0084FF))
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kamerayı Aç",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            // Manuel Barkod Giriş Alanı
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "13 Haneli Ürün Barkodu",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    if (barcodeValue.isNotBlank()) {
                        Text(
                            text = "${barcodeValue.length}/13 hane",
                            color = if (barcodeValue.length == 13) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedTextField(
                    value = barcodeValue,
                    onValueChange = { input ->
                        onBarcodeChange(input)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = {
                        Text(
                            text = "13 haneli barkod (örn: 8690123456789)",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF072450),
                        unfocusedContainerColor = Color(0xFF072450),
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF2575FC)
                    ),
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF00E5FF),
                                strokeWidth = 2.dp
                            )
                        } else if (barcodeValue.isNotBlank()) {
                            IconButton(onClick = { onBarcodeChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Temizle", tint = Color.White)
                            }
                        }
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ℹ️ Kamera sadece 13 haneli ardışık ürün barkodunu algılar. Mağaza kodu, tire, harf ve fiyatlar otomatik ayıklanır.",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// 2. ÜRÜN BİLGİLERİ SEKMESİ KARTI (OCR METİN TANIMA DESTEKLİ)
// -------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductInfoTabCard(
    formState: com.example.ui.viewmodel.AddProductFormState,
    isCameraOpen: Boolean,
    onToggleCamera: () -> Unit,
    onPhotoCaptured: (Uri) -> Unit,
    onNameChange: (String) -> Unit,
    onExpiryDateClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onSelectOcrText: (String) -> Unit,
    onAppendOcrText: (String) -> Unit,
    onClearOcr: () -> Unit,
    onNextToImages: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var showFullTextDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D3B7A)),
        border = BorderStroke(1.5.dp, Color(0xFF2575FC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Başlık
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ürün Bilgileri",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }

            // GÖMÜLÜ KAMERA (Barkod ekranı gibi odaklanmış) VEYA FOTOĞRAF / GALERİ BUTONLARI
            if (isCameraOpen) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
                    ) {
                        InlineProductNameCameraView(
                            onPhotoCaptured = onPhotoCaptured,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Button(
                        onClick = onToggleCamera,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kamerayı Kapat", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // ETİKETİ FOTOĞRAFLA / GALERİDEN EKLE KUTUSU
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF082855),
                    border = BorderStroke(1.dp, Color(0xFF1E5BB5))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Ürün İsmini Fotoğrafla / Galeriden Ekle",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Kamera açıldığında sadece ürün ismi çerçevelenip otomatik doldurulur.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.Center
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Turuncu "Fotoğraf Çek / Kamerayı Aç" Butonu (Canlı kamera)
                            Button(
                                onClick = onToggleCamera,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316))
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Fotoğraf Çek", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            // Mor "Galeriden Ekle" Butonu
                            Button(
                                onClick = onGalleryClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Galeriden Ekle", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // OCR İŞLEME VE METİN SEÇİCİ ALANI (Fotoğraftan okunan metinler)
            if (formState.isOcrProcessing) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0084FF).copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF00E5FF),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Fotoğraftaki ürün adı taranıyor...",
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else if (formState.ocrDetectedLines.isNotEmpty()) {
                // Algılanan Metin Parçacıkları (Kullanıcı tek tıkla veya kopyalayarak seçebilir)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF063A48),
                    border = BorderStroke(1.dp, Color(0xFF0D9488))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("✨", fontSize = 14.sp)
                                Text(
                                    text = "Fotoğraftan Algılanan Metinler",
                                    color = Color(0xFF5EEAD4),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                            Row {
                                TextButton(
                                    onClick = { showFullTextDialog = true },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Tüm Metin", color = Color(0xFF60A5FA), fontSize = 11.sp)
                                }
                                IconButton(
                                    onClick = onClearOcr,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Gizle",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Dokunarak ürün adını belirleyin veya sağındaki (+) ile ekleyin:",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )

                        // Algılanan Satırlar (Chip Listesi)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            formState.ocrDetectedLines.take(8).forEach { line ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0B2D64),
                                    border = BorderStroke(1.dp, Color(0xFF2575FC)),
                                    modifier = Modifier.clickable {
                                        onSelectOcrText(line)
                                        Toast.makeText(context, "Ürün adı: $line", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = line,
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        IconButton(
                                            onClick = { onAppendOcrText(line) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Ekle",
                                                tint = Color(0xFF00E5FF),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Ürün Adı Girişi
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ürün Adı",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    if (formState.isAutoPopulated) {
                        Text(
                            text = "✨ ${formState.autoPopulatedSource ?: "Otomatik"}",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                OutlinedTextField(
                    value = formState.name,
                    onValueChange = onNameChange,
                    placeholder = {
                        Text(
                            text = "Ürün adını girin veya fotoğraftan seçin...",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF072450),
                        unfocusedContainerColor = Color(0xFF072450),
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF2575FC)
                    ),
                    trailingIcon = {
                        if (formState.name.isNotBlank()) {
                            IconButton(onClick = { onNameChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Temizle", tint = Color.White)
                            }
                        }
                    },
                    singleLine = true
                )
            }

            // Son Kullanma Tarihi (SKT)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Son Kullanma Tarihi (SKT)",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Surface(
                    onClick = onExpiryDateClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF072450),
                    border = BorderStroke(1.dp, Color(0xFF2575FC))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formState.expiryDate.ifBlank { "gg.aa.yyyy" },
                            color = if (formState.expiryDate.isNotBlank()) Color.White else Color.White.copy(alpha = 0.5f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Tarih Seç",
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Ek Bilgi / Barkod & Onayla Geç Butonu
            if (formState.barcode.isNotBlank()) {
                Text(
                    text = "Barkod: ${formState.barcode}",
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // İSMİ ONAYLA VE FOTOĞRAF SEKMESİNE GEÇ BUTONU
            Button(
                onClick = {
                    if (formState.name.isBlank()) {
                        Toast.makeText(context, "Lütfen önce bir ürün ismi girin veya fotoğraftan seçin!", Toast.LENGTH_SHORT).show()
                    } else {
                        onNextToImages()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text(
                    text = "✅ İsmi Onayla ve Fotoğrafa Geç",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    // OCR Tüm Metin Önizleme ve Kopyalama Diyaloğu
    if (showFullTextDialog && formState.ocrFullText.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showFullTextDialog = false },
            title = { Text("Fotoğraftan Okunan Tüm Metin", fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Text(
                            text = formState.ocrFullText,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(formState.ocrFullText))
                        Toast.makeText(context, "Metin panoya kopyalandı!", Toast.LENGTH_SHORT).show()
                        showFullTextDialog = false
                    }
                ) {
                    Text("Panoya Kopyala", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFullTextDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }
}

// -------------------------------------------------------------------------
// 3. GÖRÜNTÜ SEKMESİ KARTI (FOTOĞRAF ÇEK / GALERİDEN SEÇ)
// -------------------------------------------------------------------------
@Composable
fun ProductImageTabCard(
    formState: com.example.ui.viewmodel.AddProductFormState,
    onCaptureProductPhoto: () -> Unit,
    onPickProductGallery: () -> Unit,
    onCaptureLabelPhoto: () -> Unit,
    onPickLabelGallery: () -> Unit,
    onClearProductImage: () -> Unit,
    onClearLabelImage: () -> Unit,
    onImageClick: (String) -> Unit,
    onSaveProduct: () -> Unit,
    onBackToName: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D3B7A)),
        border = BorderStroke(1.5.dp, Color(0xFF2575FC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Başlık
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ürün Fotoğrafı",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }

            // Onaylanmış Ürün Bilgi Özeti
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF082855),
                border = BorderStroke(1.dp, Color(0xFF1E5BB5))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✓", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Seçilen Ürün İsmi:",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                        Text(
                            text = formState.name.ifBlank { "(İsim Henüz Girilmedi)" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (formState.expiryDate.isNotBlank()) {
                            Text(
                                text = "SKT: ${formState.expiryDate}",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    TextButton(
                        onClick = onBackToName,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Değiştir", color = Color(0xFF60A5FA), fontSize = 11.5.sp)
                    }
                }
            }

            Text(
                text = "Ürünün ana fotoğrafını çekin veya galeriden seçin (ürün ismi değişmez)",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Ürün Fotoğrafı Butonları / Önizleme
            if (formState.imageUri != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black)
                            .clickable { onImageClick(formState.imageUri) }
                    ) {
                        AsyncImage(
                            model = formState.imageUri,
                            contentDescription = "Ürün Fotoğrafı",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "📸 Ürün Fotoğrafı",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        IconButton(
                            onClick = onClearProductImage,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Kaldır", tint = Color.White)
                        }
                    }

                    // Fotoğrafı Değiştir Butonları
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCaptureProductPhoto,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF60A5FA))
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Yeniden Çek", color = Color.White, fontSize = 11.5.sp)
                        }
                        OutlinedButton(
                            onClick = onPickProductGallery,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF8B5CF6))
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Galeriden Seç", color = Color.White, fontSize = 11.5.sp)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Mavi "Fotoğraf Çek" Butonu
                    Button(
                        onClick = onCaptureProductPhoto,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fotoğraf Çek", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Mor "Galeriden Seç" Butonu
                    Button(
                        onClick = onPickProductGallery,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Galeriden Seç", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Divider(color = Color(0x33FFFFFF), modifier = Modifier.padding(vertical = 2.dp))

            // İkinci: SKT / Etiket Fotoğrafı (Opsiyonel)
            Text(
                text = "SKT / Tarih Etiketi (Opsiyonel)",
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp
            )

            if (formState.labelImageUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .clickable { onImageClick(formState.labelImageUri) }
                ) {
                    AsyncImage(
                        model = formState.labelImageUri,
                        contentDescription = "Etiket Fotoğrafı",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = onClearLabelImage,
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCaptureLabelPhoto,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF60A5FA))
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Etiket Çek", color = Color.White, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onPickLabelGallery,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6))
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Etiket Seç", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ==========================================
            // ONYALA VE KAYDET ANA BUTONU
            // ==========================================
            Button(
                onClick = onSaveProduct,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF16A34A) // Canlı Yeşil
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (formState.isEditMode) "💾 Değişiklikleri Kaydet" else "💾 Onayla ve Ürünü Kaydet",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// 4. ALT ÇUBUK (BOTTOM BAR: 🏠 Ürünler & ➕ Ürün Ekle / Kaydet)
// -------------------------------------------------------------------------
@Composable
fun BottomNavBarSection(
    activeTab: MainNavTab,
    onTabSelect: (MainNavTab) -> Unit,
    onSaveProduct: () -> Unit,
    isEditMode: Boolean
) {
    val homeBgColor by animateColorAsState(
        targetValue = if (activeTab == MainNavTab.PRODUCTS) Color(0xFF0084FF) else Color(0xFF0D3B7A),
        label = "HomeBg"
    )
    val homeBorderColor by animateColorAsState(
        targetValue = if (activeTab == MainNavTab.PRODUCTS) Color(0xFF60A5FA) else Color(0x33FFFFFF),
        label = "HomeBorder"
    )
    val addBgColor by animateColorAsState(
        targetValue = if (activeTab == MainNavTab.ADD_PRODUCT) Color(0xFF16A34A) else Color(0xFFD946EF),
        label = "AddBg"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF072450),
        border = BorderStroke(1.dp, Color(0xFF1E5BB5).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sol: "🏠 Ürünler" Butonu (Mavi)
            Button(
                onClick = { onTabSelect(MainNavTab.PRODUCTS) },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = homeBgColor),
                border = BorderStroke(1.5.dp, homeBorderColor)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Ürünler",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }

            // Sağ: "➕ Ürün Ekle" / "💾 Kaydet" Butonu (Pembe/Yeşil)
            Button(
                onClick = {
                    if (activeTab == MainNavTab.ADD_PRODUCT) {
                        onSaveProduct()
                    } else {
                        onTabSelect(MainNavTab.ADD_PRODUCT)
                    }
                },
                modifier = Modifier
                    .weight(1.1f)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = addBgColor)
            ) {
                Icon(
                    imageVector = if (activeTab == MainNavTab.ADD_PRODUCT) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (activeTab == MainNavTab.ADD_PRODUCT) {
                        if (isEditMode) "Güncelle" else "Ürünü Kaydet"
                    } else {
                        "+ Ürün Ekle"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }
    }
}

// -------------------------------------------------------------------------
// 5. ÜRÜNLER LİSTESİ İÇERİĞİ (GÖRSELDEKİ BİREBİR ANA EKRAN)
// -------------------------------------------------------------------------
@Composable
fun ProductsListTabContent(
    products: List<ProductEntity>,
    allProducts: List<ProductEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedSortType: SortType,
    onSortTypeChange: (SortType) -> Unit,
    selectedStatusFilter: ProductStatus?,
    onStatusFilterClick: (ProductStatus) -> Unit,
    onEditProduct: (ProductEntity) -> Unit,
    onDeleteProduct: (ProductEntity) -> Unit,
    onImageClick: (String) -> Unit,
    onNavigateToAdd: () -> Unit
) {
    // 4 Durum Sayımı (Veritabanındaki tüm ürünler baz alınır)
    val expiredCount = allProducts.count { it.getStatus() == ProductStatus.EXPIRED }
    val criticalCount = allProducts.count { it.getStatus() == ProductStatus.CRITICAL }
    val warningCount = allProducts.count { it.getStatus() == ProductStatus.WARNING }
    val safeCount = allProducts.count { it.getStatus() == ProductStatus.SAFE }

    // Hangi ürün kartının genişletilmiş olduğunu tutan state
    var expandedProductId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ==========================================
        // 1. DÖRT DURUM KARTI (Geçmiş | Kritik | Uyarı | Normal)
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. GEÇMİŞ (Tam Kırmızı - %100 Belirgin Kırmızı)
            MainStatusStatBox(
                count = expiredCount,
                label = "Geçmiş",
                activeBgColor = Color(0xFFDC2626), // Tam Parlak Kırmızı
                inactiveBgColor = Color(0xFF7F1D1D).copy(alpha = 0.85f),
                activeBorderColor = Color(0xFFFCA5A5),
                inactiveBorderColor = Color(0xFFEF4444).copy(alpha = 0.6f),
                isSelected = selectedStatusFilter == ProductStatus.EXPIRED,
                onClick = { onStatusFilterClick(ProductStatus.EXPIRED) },
                modifier = Modifier.weight(1f)
            )

            // 2. KRİTİK (Tam Turuncu / Kritik)
            MainStatusStatBox(
                count = criticalCount,
                label = "Kritik",
                activeBgColor = Color(0xFFEA580C), // Tam Parlak Turuncu
                inactiveBgColor = Color(0xFF7C2D12).copy(alpha = 0.85f),
                activeBorderColor = Color(0xFFFED7AA),
                inactiveBorderColor = Color(0xFFF97316).copy(alpha = 0.6f),
                isSelected = selectedStatusFilter == ProductStatus.CRITICAL,
                onClick = { onStatusFilterClick(ProductStatus.CRITICAL) },
                modifier = Modifier.weight(1f)
            )

            // 3. UYARI (Tam Sarı - Belirgin Canlı Sarı)
            MainStatusStatBox(
                count = warningCount,
                label = "Uyarı",
                activeBgColor = Color(0xFFCA8A04), // Tam Parlak Sarı/Amber
                inactiveBgColor = Color(0xFF713F12).copy(alpha = 0.85f),
                activeBorderColor = Color(0xFFFEF08A),
                inactiveBorderColor = Color(0xFFEAB308).copy(alpha = 0.6f),
                isSelected = selectedStatusFilter == ProductStatus.WARNING,
                onClick = { onStatusFilterClick(ProductStatus.WARNING) },
                modifier = Modifier.weight(1f)
            )

            // 4. NORMAL (Tam Yeşil - Canlı Zümrüt Yeşil)
            MainStatusStatBox(
                count = safeCount,
                label = "Normal",
                activeBgColor = Color(0xFF16A34A), // Tam Parlak Yeşil
                inactiveBgColor = Color(0xFF14532D).copy(alpha = 0.85f),
                activeBorderColor = Color(0xFFBBF7D0),
                inactiveBorderColor = Color(0xFF22C55E).copy(alpha = 0.6f),
                isSelected = selectedStatusFilter == ProductStatus.SAFE,
                onClick = { onStatusFilterClick(ProductStatus.SAFE) },
                modifier = Modifier.weight(1f)
            )
        }

        // ==========================================
        // 2. ARAMA ÇUBUĞU
        // ==========================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF082142),
            border = BorderStroke(1.dp, Color(0xFF1E3A68))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Ara",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = "Ürün ara (ad veya barkod)...",
                            color = Color(0xFF64748B),
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Temizle",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // ==========================================
        // 3. SIRALAMA SEKMELERİ (Sırala: [SKT] [İsim] [Yeni])
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Sırala: ",
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )

            SortPillButton(
                title = "SKT",
                isSelected = selectedSortType == SortType.EXPIRY,
                onClick = { onSortTypeChange(SortType.EXPIRY) }
            )

            SortPillButton(
                title = "İsim",
                isSelected = selectedSortType == SortType.NAME,
                onClick = { onSortTypeChange(SortType.NAME) }
            )

            SortPillButton(
                title = "Yeni",
                isSelected = selectedSortType == SortType.NEWEST,
                onClick = { onSortTypeChange(SortType.NEWEST) }
            )
        }

        // ==========================================
        // 4. SAYAÇ BİLGİSİ ("1 / 1 ürün gösteriliyor")
        // ==========================================
        Text(
            text = "${products.size} / ${allProducts.size} ürün gösteriliyor",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        // ==========================================
        // 5. ÜRÜN LİSTESİ VEYA BOŞ DURUM
        // ==========================================
        if (products.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D3B7A)),
                    border = BorderStroke(1.5.dp, Color(0xFF2575FC))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFFF59E0B), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Text(
                            text = if (allProducts.isEmpty()) "Henüz Ürün Yok" else "Eşleşen Ürün Bulunamadı",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )

                        Text(
                            text = if (allProducts.isEmpty()) {
                                "Ürün eklemek için aşağıdaki \"+ Ürün Ekle\" butonuna tıklayın"
                            } else {
                                "Arama veya filtre kriterlerinizi değiştirin"
                            },
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        if (allProducts.isEmpty()) {
                            Button(
                                onClick = onNavigateToAdd,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Hemen Ürün Ekle", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(products, key = { it.id }) { product ->
                    ExpandableProductCard(
                        product = product,
                        isExpanded = expandedProductId == product.id,
                        onToggleExpand = {
                            expandedProductId = if (expandedProductId == product.id) null else product.id
                        },
                        onEdit = { onEditProduct(product) },
                        onDelete = { onDeleteProduct(product) },
                        onImageClick = onImageClick
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// DURUM SAYAÇ KUTUCUĞU COMPOSABLE (Vibrant & Anında Ayırt Edilen Renkler)
// -------------------------------------------------------------------------
@Composable
fun MainStatusStatBox(
    count: Int,
    label: String,
    activeBgColor: Color,
    inactiveBgColor: Color,
    activeBorderColor: Color,
    inactiveBorderColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) activeBgColor else inactiveBgColor,
        label = "StatBoxBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) activeBorderColor else inactiveBorderColor,
        label = "StatBoxBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "StatBoxScale"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(72.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale),
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(if (isSelected) 2.5.dp else 1.2.dp, borderColor),
        shadowElevation = if (isSelected) 6.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = count.toString(),
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                color = Color.White,
                lineHeight = 24.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

// -------------------------------------------------------------------------
// SIRALAMA BUTONU (Pill) COMPOSABLE
// -------------------------------------------------------------------------
@Composable
fun SortPillButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF0084FF) else Color(0xFF132B52),
        label = "SortPillBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF60A5FA) else Color(0xFF1E3A68),
        label = "SortPillBorder"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else Color(0xFF94A3B8),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
        )
    }
}

// -------------------------------------------------------------------------
// GENİŞLETİLEBİLİR DETAYLI ÜRÜN KARTI (GÖRSELLERLE BİREBİR AYNI)
// -------------------------------------------------------------------------
@Composable
fun ExpandableProductCard(
    product: ProductEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val remainingDays = product.getRemainingDays()
    val status = product.getStatus()

    val statusColor = when (status) {
        ProductStatus.EXPIRED -> Color(0xFFEF4444)
        ProductStatus.CRITICAL -> Color(0xFFF97316)
        ProductStatus.WARNING -> Color(0xFFFBBF24)
        ProductStatus.SAFE -> Color(0xFF22C55E)
    }

    val activeBorderColor = when (status) {
        ProductStatus.EXPIRED -> Color(0xFFEF4444)
        ProductStatus.CRITICAL -> Color(0xFFF97316)
        ProductStatus.WARNING -> Color(0xFFF59E0B) // Görseldeki Altın/Kehribar Kenarlık
        ProductStatus.SAFE -> Color(0xFF22C55E)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F223D)),
        border = BorderStroke(1.5.dp, activeBorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // ==========================================
            // 1. ÜST KISIM (KAPALI VE AÇIK HALDE HER ZAMAN GÖRÜNÜR)
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Sol: Küçük Resim / İkon (54x54 dp)
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF072450))
                        .clickable {
                            product.imageUri?.let(onImageClick)
                                ?: product.labelImageUri?.let(onImageClick)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (product.imageUri != null) {
                        AsyncImage(
                            model = product.imageUri,
                            contentDescription = product.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (product.labelImageUri != null) {
                        AsyncImage(
                            model = product.labelImageUri,
                            contentDescription = "Etiket",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Fastfood,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Orta: Ürün Adı, Barkod ve SKT Rozeti
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = product.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!product.barcode.isNullOrBlank()) {
                        Text(
                            text = product.barcode,
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    // Turuncu / Kehribar SKT Tarih Rozeti (Görseldeki Gibi)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF59E0B)
                    ) {
                        Text(
                            text = product.getFormattedExpiryDate(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                // Sağ: Renkli Durum Noktası ve "3 gün kaldı" Metni
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Yuvarlak Renkli İndikatör
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(statusColor, CircleShape)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = product.getRemainingDaysText(),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            // ==========================================
            // 2. GENİŞLETİLMİŞ DETAYLAR (GÖRSELDEKİ BİREBİR DETAY ALANI)
            // ==========================================
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // İki Kolonlu Bilgi Grid'i
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Sol Kolon: Barkod & Eklenme
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Barkod:",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                        Text(
                            text = product.barcode ?: "-",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Eklenme:",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                        Text(
                            text = product.getFormattedAddedDate(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Sağ Kolon: SKT & Kalan
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "SKT:",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                        Text(
                            text = product.getFormattedExpiryDate(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Kalan:",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                        Text(
                            text = product.getRemainingDaysText(),
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Büyük Fotoğraf Gösterimi (Görseldeki sek st fotoğrafı gibi)
                val displayImageUri = product.imageUri ?: product.labelImageUri
                if (displayImageUri != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clickable { onImageClick(displayImageUri) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E3A68))
                    ) {
                        AsyncImage(
                            model = displayImageUri,
                            contentDescription = product.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // Alt Aksiyon Butonları: ✏️ Düzenle (Mavi) | 🗑️ Sil (Kırmızı)
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Düzenle Butonu (Mavi)
                    Button(
                        onClick = onEdit,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0084FF))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Düzenle",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Düzenle",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Sil Butonu (Kırmızı)
                    Button(
                        onClick = onDelete,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Sil",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sil",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
