package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Categories
import com.example.data.ProductDatabase
import com.example.data.ProductEntity
import com.example.data.ProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

class ProductViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ProductRepository

    val searchQuery = MutableStateFlow("")
    val selectedCategoryFilter = MutableStateFlow("Tümü")

    private val _message = MutableSharedFlow<Pair<String, Boolean>>() // message to success/error flag
    val message: SharedFlow<Pair<String, Boolean>> = _message.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val productsList: StateFlow<List<ProductEntity>> = combine(
        searchQuery,
        selectedCategoryFilter
    ) { query, cat ->
        Pair(query, cat)
    }.flatMapLatest { (query, cat) ->
        if (query.isNotBlank()) {
            repository.searchProducts(query)
        } else if (cat != "Tümü") {
            repository.getProductsByCategory(cat)
        } else {
            repository.allProducts
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        val dao = ProductDatabase.getDatabase(application).productDao()
        repository = ProductRepository(dao)

        // Seed default sample inventory if database is empty
        viewModelScope.launch {
            if (repository.getCount() == 0) {
                seedInitialData()
            }
        }
    }

    private suspend fun seedInitialData() {
        val sampleProducts = listOf(
            ProductEntity(
                title = "Taze Somun Ekmek",
                category = "Gıda",
                categoryColorHex = Categories.getColorHexForCategory("Gıda"),
                shortCode = "EKM101",
                barcode = "8690001234567",
                stockQuantity = 25,
                price = 10.0
            ),
            ProductEntity(
                title = "Tam Yağlı Süt 1L",
                category = "İçecek",
                categoryColorHex = Categories.getColorHexForCategory("İçecek"),
                shortCode = "SUT202",
                barcode = "8690002345678",
                stockQuantity = 14,
                price = 32.5
            ),
            ProductEntity(
                title = "Sızma Zeytinyağı 1L",
                category = "Gıda",
                categoryColorHex = Categories.getColorHexForCategory("Gıda"),
                shortCode = "ZYT303",
                barcode = "8690003456789",
                stockQuantity = 8,
                price = 280.0
            ),
            ProductEntity(
                title = "Çay 1000g Premium",
                category = "İçecek",
                categoryColorHex = Categories.getColorHexForCategory("İçecek"),
                shortCode = "CAY404",
                barcode = "8690004567890",
                stockQuantity = 19,
                price = 145.0
            ),
            ProductEntity(
                title = "Yüzey Temizleyici 1.5L",
                category = "Temizlik",
                categoryColorHex = Categories.getColorHexForCategory("Temizlik"),
                shortCode = "TMZ505",
                barcode = "8690005678901",
                stockQuantity = 5,
                price = 68.0
            )
        )
        sampleProducts.forEach { repository.insertProduct(it) }
    }

    fun generateRecommendedShortCode(): String {
        return Random.nextInt(100000, 99999999).toString()
    }

    fun addOrUpdateProduct(
        id: Int = 0,
        title: String,
        category: String,
        shortCode: String,
        barcode: String,
        imagePath: String?,
        stockQuantity: Int,
        price: Double,
        onSuccess: () -> Unit
    ) {
        if (title.isBlank()) {
            viewModelScope.launch { _message.emit(Pair("Lütfen ürün adını giriniz!", false)) }
            return
        }
        if (shortCode.isBlank()) {
            viewModelScope.launch { _message.emit(Pair("Lütfen kısa kodu giriniz!", false)) }
            return
        }

        viewModelScope.launch {
            val colorHex = Categories.getColorHexForCategory(category)
            val product = ProductEntity(
                id = id,
                title = title.trim(),
                category = category,
                categoryColorHex = colorHex,
                shortCode = shortCode.trim(),
                barcode = barcode.trim(),
                imagePath = imagePath,
                stockQuantity = stockQuantity,
                price = price
            )

            if (id == 0) {
                repository.insertProduct(product)
                _message.emit(Pair("✅ Ürün başarıyla eklendi!", true))
            } else {
                repository.updateProduct(product)
                _message.emit(Pair("✅ Ürün güncellendi!", true))
            }
            onSuccess()
        }
    }

    fun updateStock(product: ProductEntity, delta: Int) {
        val newQty = (product.stockQuantity + delta).coerceAtLeast(0)
        viewModelScope.launch {
            repository.updateStock(product.id, newQty)
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            _message.emit(Pair("🗑️ Ürün silindi: ${product.title}", true))
        }
    }
}
