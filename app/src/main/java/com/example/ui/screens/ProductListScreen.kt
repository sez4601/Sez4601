package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.Categories
import com.example.data.ProductEntity
import com.example.ui.scanner.BarcodeScannerDialog
import com.example.ui.viewmodel.ProductViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    viewModel: ProductViewModel,
    onNavigateToAddProduct: () -> Unit,
    onNavigateToEditProduct: (ProductEntity) -> Unit
) {
    val products by viewModel.productsList.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()

    var isQuickScannerOpen by remember { mutableStateOf(false) }
    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.message.collect { (msg, isSuccess) ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Stok & Ürün Yönetimi",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Envanter ve Barkod Takip Sistemi",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isQuickScannerOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Barkod Ara",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D6EFD)
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAddProduct,
                containerColor = Color(0xFF0D6EFD),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Yeni Ürün Ekle", fontWeight = FontWeight.Bold) }
            )
        },
        containerColor = Color(0xFFF5F7FA)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar & Scan Button
            Surface(
                color = Color(0xFF0D6EFD),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.searchQuery.value = it },
                        placeholder = { Text("Ürün adı, kısa kod veya barkod ara...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Temizle")
                                }
                            } else {
                                IconButton(onClick = { isQuickScannerOpen = true }) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Tara",
                                        tint = Color(0xFF0D6EFD)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            disabledContainerColor = Color.White,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Categories Filter Chips
            val categories = remember { listOf("Tümü") + Categories.defaultList.map { it.name } }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { catName ->
                    val isSelected = selectedCategoryFilter == catName
                    val catColor = if (catName == "Tümü") Color(0xFF0D6EFD) else Categories.getColorForCategory(catName)

                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectedCategoryFilter.value = catName },
                        label = { Text(catName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = catColor,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                            containerColor = Color.White,
                            labelColor = Color(0xFF495057)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color(0xFFDEE2E6),
                            selectedBorderColor = catColor
                        )
                    )
                }
            }

            // Inventory Summary Bar
            val totalProducts = products.size
            val totalStock = products.sumOf { it.stockQuantity }
            val lowStockCount = products.count { it.stockQuantity in 1..5 }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Çeşit", fontSize = 11.sp, color = Color.Gray)
                        Text(text = "$totalProducts", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0D6EFD))
                    }
                    Divider(modifier = Modifier.height(24.dp).width(1.dp), color = Color(0xFFDEE2E6))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Toplam Stok", fontSize = 11.sp, color = Color.Gray)
                        Text(text = "$totalStock Adet", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF198754))
                    }
                    Divider(modifier = Modifier.height(24.dp).width(1.dp), color = Color(0xFFDEE2E6))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Azalan Stok", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = "$lowStockCount Ürün",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (lowStockCount > 0) Color(0xFFDC3545) else Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Products List or Empty State
            if (products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Henüz ürün bulunamadı",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF495057)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sağ alttaki '+ Yeni Ürün Ekle' butonuna basarak ilk ürününüzü ekleyebilirsiniz.",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyLazyProductsList(
                    products = products,
                    viewModel = viewModel,
                    onEdit = onNavigateToEditProduct,
                    onDelete = { productToDelete = it }
                )
            }
        }
    }

    // Delete confirmation dialog
    productToDelete?.let { prod ->
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("Ürünü Sil", fontWeight = FontWeight.Bold) },
            text = { Text("'${prod.title}' isimli ürünü silmek istediğinize emin misiniz?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProduct(prod)
                        productToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sil", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) {
                    Text("Vazgeç")
                }
            }
        )
    }

    // Quick Barcode Search Scanner
    if (isQuickScannerOpen) {
        BarcodeScannerDialog(
            title = "📷 Barkod İle Ürün Ara",
            isShortCodeMode = false,
            onBarcodeDetected = { scannedCode ->
                viewModel.searchQuery.value = scannedCode
                isQuickScannerOpen = false
            },
            onDismiss = { isQuickScannerOpen = false }
        )
    }
}

@Composable
private fun LazyLazyProductsList(
    products: List<ProductEntity>,
    viewModel: ProductViewModel,
    onEdit: (ProductEntity) -> Unit,
    onDelete: (ProductEntity) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(products, key = { it.id }) { product ->
            ProductCardItem(
                product = product,
                onIncrementStock = { viewModel.updateStock(product, 1) },
                onDecrementStock = { viewModel.updateStock(product, -1) },
                onEdit = { onEdit(product) },
                onDelete = { onDelete(product) }
            )
        }
    }
}

@Composable
fun ProductCardItem(
    product: ProductEntity,
    onIncrementStock: () -> Unit,
    onDecrementStock: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryColor = Categories.getColorForCategory(product.category)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Image or category icon avatar
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(categoryColor.copy(alpha = 0.15f))
                        .border(1.dp, categoryColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!product.imagePath.isNullOrBlank()) {
                        AsyncImage(
                            model = product.imagePath,
                            contentDescription = product.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Title & Category tag
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = categoryColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = product.category,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = categoryColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (product.price > 0) {
                            Text(
                                text = "₺${String.format("%.2f", product.price)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF198754)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = product.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF212529),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "KOD: ${product.shortCode}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF495057)
                        )
                        if (product.barcode.isNotBlank()) {
                            Text(
                                text = "•  BARKOD: ${product.barcode}",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = Color(0xFFF1F3F5))
            Spacer(modifier = Modifier.height(8.dp))

            // Stock Controller & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stock Stepper Pill
                Surface(
                    color = if (product.stockQuantity == 0) Color(0xFFFFE8E6) else Color(0xFFEBF3FE),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = onDecrementStock,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Azalt",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF0D6EFD)
                            )
                        }

                        Text(
                            text = "Stok: ${product.stockQuantity}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (product.stockQuantity == 0) Color(0xFFDC3545) else Color(0xFF0D6EFD),
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )

                        IconButton(
                            onClick = onIncrementStock,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Artır",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF0D6EFD)
                            )
                        }
                    }
                }

                // Edit / Delete Buttons
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Düzenle",
                            tint = Color(0xFF0D6EFD),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Sil",
                            tint = Color(0xFFDC3545),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
