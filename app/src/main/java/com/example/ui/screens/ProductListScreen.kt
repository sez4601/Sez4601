package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.Categories
import com.example.data.ProductEntity
import com.example.data.ProductStatus
import com.example.ui.scanner.BarcodeScannerView
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProductViewModel
import com.example.ui.viewmodel.SortType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    viewModel: ProductViewModel,
    onNavigateToAddProduct: () -> Unit,
    onNavigateToEditProduct: (Long) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val products by viewModel.products.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedSortType by viewModel.selectedSortType.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showBarcodeScannerDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf<ProductEntity?>(null) }
    var previewImageUri by remember { mutableStateOf<String?>(null) }

    // Kullanıcı mesajlarını dinle (Toast göster)
    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SKT Takip",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Son Kullanma Tarihi Kontrol",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                actions = {
                    // Hızlı Barkod Arama Butonu
                    IconButton(onClick = { showBarcodeScannerDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Barkod Tara",
                            tint = Color.White
                        )
                    }

                    // Ayarlar Butonu
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ayarlar",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BluePrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddProduct,
                containerColor = BluePrimary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ürün Ekle", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = LightBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Arama Kutusu ve Sıralama Butonları
            Surface(
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Arama Girişi
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.searchQuery.value = it },
                        placeholder = { Text("Ürün adı veya barkod ara...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Temizle")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            focusedContainerColor = Color.White
                        )
                    )

                    // Sıralama Butonları (SKT | İsim | Yeni)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sırala:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SortType.values().forEach { sort ->
                                FilterChip(
                                    selected = selectedSortType == sort,
                                    onClick = { viewModel.selectedSortType.value = sort },
                                    label = { Text(sort.labelTr, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BluePrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Kategori Filtre Çipleri
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 4.dp)
                    ) {
                        items(Categories.list) { cat ->
                            val isSelected = selectedCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectedCategory.value = cat },
                                label = { Text(cat, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (cat == "Tümü") BluePrimary else Categories.getColorForCategory(cat),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFFF1F5F9)
                                )
                            )
                        }
                    }
                }
            }

            // Durum Özeti Şeridi (İstatistikler)
            val expiredCount = products.count { it.getStatus() == ProductStatus.EXPIRED }
            val criticalCount = products.count { it.getStatus() == ProductStatus.CRITICAL }
            val warningCount = products.count { it.getStatus() == ProductStatus.WARNING }

            if (products.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusBadge(
                        label = "Süresi Dolan",
                        count = expiredCount,
                        color = RedDanger,
                        modifier = Modifier.weight(1f)
                    )
                    StatusBadge(
                        label = "Kritik (0-3G)",
                        count = criticalCount,
                        color = OrangeCritical,
                        modifier = Modifier.weight(1f)
                    )
                    StatusBadge(
                        label = "Yaklaşan (4-7G)",
                        count = warningCount,
                        color = WarningYellow,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Ürün Listesi
            if (products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(72.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "Aramaya uygun ürün bulunamadı" else "Henüz eklenmiş ürün yok",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Sağ alttaki '+' butonuna basarak ilk ürününüzü ekleyebilirsiniz.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(products, key = { it.id }) { product ->
                        ProductItemCard(
                            product = product,
                            onEdit = { onNavigateToEditProduct(product.id) },
                            onDelete = { showDeleteConfirmDialog = product },
                            onImageClick = { uri -> previewImageUri = uri }
                        )
                    }
                }
            }
        }
    }

    // Barkod Arama Diyaloğu
    if (showBarcodeScannerDialog) {
        Dialog(onDismissRequest = { showBarcodeScannerDialog = false }) {
            BarcodeScannerView(
                onBarcodeDetected = { code ->
                    showBarcodeScannerDialog = false
                    viewModel.playSuccessFeedback()
                    coroutineScope.launch {
                        val existing = viewModel.getProductByBarcode(code)
                        if (existing != null) {
                            viewModel.searchQuery.value = code
                            Toast.makeText(context, "Kayıtlı ürün bulundu: ${existing.name}", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.fetchProductInfoByBarcode(code)
                            onNavigateToAddProduct()
                        }
                    }
                },
                onClose = { showBarcodeScannerDialog = false }
            )
        }
    }

    // Ürün Silme Onay Diyaloğu
    if (showDeleteConfirmDialog != null) {
        val prod = showDeleteConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Ürünü Sil") },
            text = { Text("'${prod.name}' ürününü silmek istediğinize emin misiniz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProduct(prod)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = RedDanger)
                ) {
                    Text("Sil", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("İptal")
                }
            }
        )
    }

    // Ayarlar Diyaloğu (Videodaki Ayarlar Ekranı)
    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
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

@Composable
fun StatusBadge(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )
            Text(
                text = count.toString(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun ProductItemCard(
    product: ProductEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onImageClick: (String) -> Unit
) {
    val remainingDays = product.getRemainingDays()
    val status = product.getStatus()

    val statusColor = when (status) {
        ProductStatus.EXPIRED -> RedDanger
        ProductStatus.CRITICAL -> OrangeCritical
        ProductStatus.WARNING -> WarningYellow
        ProductStatus.SAFE -> GreenSuccess
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sol: Fotoğraf veya Kategori İkonu
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF1F5F9))
                    .clickable {
                        product.imageUri?.let { onImageClick(it) }
                            ?: product.labelImageUri?.let { onImageClick(it) }
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
                        tint = Categories.getColorForCategory(product.category),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Orta: Ürün Bilgileri
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = product.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Adet Rozeti
                    if (product.quantity > 1) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE2E8F0)
                        ) {
                            Text(
                                text = "${product.quantity} Adet",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Kategori ve Barkod
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = product.category,
                        fontSize = 12.sp,
                        color = Categories.getColorForCategory(product.category),
                        fontWeight = FontWeight.Medium
                    )
                    if (!product.barcode.isNullOrBlank()) {
                        Text(
                            text = "• ${product.barcode}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                // SKT Bilgisi
                Text(
                    text = "SKT: ${product.getFormattedExpiryDate()}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }

            // Sağ: Kalan Gün Sayacı ve Menü
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Kalan Gün Kartı
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when {
                                remainingDays < 0 -> "${-remainingDays}G Geçti"
                                remainingDays == 0L -> "Bugün!"
                                else -> "$remainingDays Gün"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = statusColor
                        )
                    }
                }

                // Silme Butonu
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Sil",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    viewModel: ProductViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val soundEnabled by viewModel.alertSoundEnabled.collectAsState()
    val vibrationEnabled by viewModel.alertVibrationEnabled.collectAsState()
    val alertDays by viewModel.alertDaysThreshold.collectAsState()

    var showJsonBackupDialog by remember { mutableStateOf(false) }
    var jsonBackupText by remember { mutableStateOf("") }
    var isImportMode by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ayarlar & Yedekleme",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                }

                Divider()

                // Bildirim ve Ses Ayarları
                Text("Bildirim & Uyarılar", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BluePrimary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sesli Uyarı", fontSize = 14.sp)
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = { viewModel.alertSoundEnabled.value = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Titreşim", fontSize = 14.sp)
                    Switch(
                        checked = vibrationEnabled,
                        onCheckedChange = { viewModel.alertVibrationEnabled.value = it }
                    )
                }

                // Uyarı Zamanı Seçici (Kaç gün önceden uyarsın)
                Text("Uyarı Zamanı (Kaç gün önceden?):", fontSize = 13.sp, color = TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(3, 5, 7, 14).forEach { days ->
                        FilterChip(
                            selected = alertDays == days,
                            onClick = { viewModel.alertDaysThreshold.value = days },
                            label = { Text("$days Gün", fontSize = 12.sp) }
                        )
                    }
                }

                Button(
                    onClick = { viewModel.testAlert() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Uyarıyı Test Et")
                }

                Divider()

                // Veri Yönetimi & Yedekleme
                Text("Veri Yedekleme (JSON)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BluePrimary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                jsonBackupText = viewModel.exportJsonBackup()
                                isImportMode = false
                                showJsonBackupDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Yedeği Al", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            jsonBackupText = ""
                            isImportMode = true
                            showJsonBackupDialog = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Yedeği Yükle", fontSize = 12.sp)
                    }
                }

                // Tüm Verileri Temizle
                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedDanger)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tüm Verileri Sıfırla")
                }

                // Çevrimdışı Bilgi Notu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(18.dp))
                    Text(
                        text = "%100 Çevrimdışı & Güvenli (Cihazda Saklanır)",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }

    // JSON Yedekleme / Geri Yükleme Modalı
    if (showJsonBackupDialog) {
        Dialog(onDismissRequest = { showJsonBackupDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isImportMode) "Yedek JSON Kodunu Yapıştırın" else "Yedek JSON Kodu",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    OutlinedTextField(
                        value = jsonBackupText,
                        onValueChange = { jsonBackupText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        placeholder = { Text("Buraya JSON formatındaki ürün yedeğini girin...") },
                        maxLines = 10
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isImportMode) {
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(jsonBackupText))
                                    Toast.makeText(context, "JSON Kodu Kopyalandı!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Kopyala")
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.importJsonBackup(jsonBackupText)
                                    showJsonBackupDialog = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                            ) {
                                Text("İçe Aktar")
                            }
                        }

                        OutlinedButton(
                            onClick = { showJsonBackupDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Kapat")
                        }
                    }
                }
            }
        }
    }

    // Sıfırlama Onay Diyaloğu
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Tüm Verileri Sil") },
            text = { Text("Kayıtlı tüm ürünler silinecek. Bu işlem geri alınamaz. Devam etmek istiyor musunuz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = RedDanger)
                ) {
                    Text("Evet, Hepsini Sil", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("İptal")
                }
            }
        )
    }
}
