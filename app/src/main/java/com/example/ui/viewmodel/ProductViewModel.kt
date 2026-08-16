package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Categories
import com.example.data.ProductDatabase
import com.example.data.ProductEntity
import com.example.data.ProductRepository
import com.example.data.ProductStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class SortType(val labelTr: String) {
    EXPIRY("SKT"),
    NAME("İsim"),
    NEWEST("Yeni")
}

// Form State
data class AddProductFormState(
    val name: String = "",
    val category: String = "Süt & Kahvaltılık",
    val barcode: String = "",
    val expiryDate: String = LocalDate.now().plusMonths(3).toString(),
    val productionDate: String = "",
    val quantity: Int = 1,
    val note: String = "",
    val imageUri: String? = null,
    val labelImageUri: String? = null,
    val isAutoPopulated: Boolean = false,
    val autoPopulatedSource: String? = null,
    val autoPopulatedBrand: String? = null,
    val isEditMode: Boolean = false,
    val editingProductId: Long = 0L
)

class ProductViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ProductRepository
    private val barcodeLookupService: com.example.data.BarcodeLookupService

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("Tümü")
    val selectedSortType = MutableStateFlow(SortType.EXPIRY)

    // Ayarlar State
    val alertDaysThreshold = MutableStateFlow(7) // Varsayılan 7 gün önceden uyarı
    val alertSoundEnabled = MutableStateFlow(true)
    val alertVibrationEnabled = MutableStateFlow(true)

    // Barkod Sorgulama State
    val isSearchingBarcode = MutableStateFlow(false)
    val barcodeSearchResult = MutableStateFlow<com.example.data.BarcodeLookupResult?>(null)

    // Add / Edit Product Form ViewModel State
    private val _addProductFormState = MutableStateFlow(AddProductFormState())
    val addProductFormState: StateFlow<AddProductFormState> = _addProductFormState.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    val products: StateFlow<List<ProductEntity>>

    init {
        val dao = ProductDatabase.getDatabase(application).productDao()
        repository = ProductRepository(dao)
        barcodeLookupService = com.example.data.BarcodeLookupService(repository)

        val flowSorted = selectedSortType.flatMapLatest { sort ->
            when (sort) {
                SortType.EXPIRY -> repository.allProductsSortedByExpiry
                SortType.NAME -> repository.allProductsSortedByName
                SortType.NEWEST -> repository.allProductsSortedByNewest
            }
        }

        products = combine(
            flowSorted,
            searchQuery,
            selectedCategory
        ) { list, query, category ->
            var filtered = list
            if (category != "Tümü") {
                filtered = filtered.filter { it.category == category }
            }
            if (query.isNotBlank()) {
                val q = query.trim().lowercase()
                filtered = filtered.filter {
                    it.name.lowercase().contains(q) ||
                    (it.barcode?.contains(q) == true) ||
                    it.category.lowercase().contains(q)
                }
            }
            filtered
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // İlk açılışta veritabanı boşsa örnek SKT verileri ekle
        viewModelScope.launch {
            if (repository.getCount() == 0) {
                seedInitialData()
            }
        }
    }

    private suspend fun seedInitialData() {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val sampleProducts = listOf(
            ProductEntity(
                name = "Tam Yağlı Taze Süt 1L",
                barcode = "8690001234567",
                expiryDate = today.minusDays(1).format(formatter), // Süresi Doldu
                category = "Süt & Kahvaltılık",
                quantity = 2,
                note = "Buzdolabında saklanmalı"
            ),
            ProductEntity(
                name = "Tavuk Göğsü 750g",
                barcode = "8690002345678",
                expiryDate = today.plusDays(2).format(formatter), // Kritik (2 gün)
                category = "Et & Tavuk & Şarküteri",
                quantity = 1,
                note = "Dondurucuya atılabilir"
            ),
            ProductEntity(
                name = "Süzme Peynir 500g",
                barcode = "8690003456789",
                expiryDate = today.plusDays(6).format(formatter), // Yaklaşan (6 gün)
                category = "Süt & Kahvaltılık",
                quantity = 3,
                note = "Kahvaltılık"
            ),
            ProductEntity(
                name = "Sızma Zeytinyağı 1L",
                barcode = "8690004567890",
                expiryDate = today.plusMonths(8).format(formatter), // Güvenli
                category = "Temel Gıda & Bakliyat",
                quantity = 4,
                note = "Kilerde serin yerde"
            ),
            ProductEntity(
                name = "Doğal Maden Suyu 6'lı",
                barcode = "8690005678901",
                expiryDate = today.plusMonths(4).format(formatter), // Güvenli
                category = "İçecek & Meşrubat",
                quantity = 6,
                note = ""
            )
        )
        repository.insertAll(sampleProducts)
    }

    fun saveProduct(
        id: Long = 0,
        name: String,
        barcode: String?,
        expiryDate: String,
        productionDate: String?,
        category: String,
        quantity: Int,
        note: String?,
        imageUri: String?,
        labelImageUri: String?,
        onSuccess: () -> Unit
    ) {
        if (name.isBlank()) {
            viewModelScope.launch { _userMessage.emit("⚠️ Lütfen ürün adını giriniz!") }
            return
        }
        if (expiryDate.isBlank()) {
            viewModelScope.launch { _userMessage.emit("⚠️ Lütfen son kullanma tarihini seçiniz!") }
            return
        }

        viewModelScope.launch {
            val entity = ProductEntity(
                id = id,
                name = name.trim(),
                barcode = barcode?.trim()?.ifBlank { null },
                expiryDate = expiryDate.trim(),
                productionDate = productionDate?.trim()?.ifBlank { null },
                category = category,
                quantity = quantity.coerceAtLeast(1),
                note = note?.trim()?.ifBlank { null },
                imageUri = imageUri,
                labelImageUri = labelImageUri
            )

            if (id == 0L) {
                repository.insertProduct(entity)
                playSuccessFeedback()
                _userMessage.emit("✅ Ürün başarıyla kaydedildi")
            } else {
                repository.updateProduct(entity)
                playSuccessFeedback()
                _userMessage.emit("✅ Ürün güncellendi")
            }
            onSuccess()
        }
    }

    fun updateProductName(name: String) {
        _addProductFormState.update { it.copy(name = name) }
    }

    fun updateProductCategory(category: String) {
        _addProductFormState.update { it.copy(category = category) }
    }

    fun updateProductBarcode(barcode: String) {
        _addProductFormState.update { it.copy(barcode = barcode) }
    }

    fun updateProductExpiryDate(expiryDate: String) {
        _addProductFormState.update { it.copy(expiryDate = expiryDate) }
    }

    fun updateProductProductionDate(productionDate: String) {
        _addProductFormState.update { it.copy(productionDate = productionDate) }
    }

    fun updateProductQuantity(quantity: Int) {
        _addProductFormState.update { it.copy(quantity = quantity.coerceAtLeast(1)) }
    }

    fun updateProductNote(note: String) {
        _addProductFormState.update { it.copy(note = note) }
    }

    fun updateProductImageUri(uri: String?) {
        _addProductFormState.update { it.copy(imageUri = uri) }
    }

    fun updateProductLabelImageUri(uri: String?) {
        _addProductFormState.update { it.copy(labelImageUri = uri) }
    }

    fun resetAddProductForm() {
        _addProductFormState.value = AddProductFormState()
        barcodeSearchResult.value = null
        isSearchingBarcode.value = false
    }

    fun initializeFormForProduct(productId: Long) {
        if (productId > 0L) {
            viewModelScope.launch {
                val existing = repository.getProductById(productId)
                if (existing != null) {
                    _addProductFormState.value = AddProductFormState(
                        name = existing.name,
                        category = existing.category,
                        barcode = existing.barcode ?: "",
                        expiryDate = existing.expiryDate,
                        productionDate = existing.productionDate ?: "",
                        quantity = existing.quantity,
                        note = existing.note ?: "",
                        imageUri = existing.imageUri,
                        labelImageUri = existing.labelImageUri,
                        isEditMode = true,
                        editingProductId = productId
                    )
                }
            }
        } else {
            if (_addProductFormState.value.isEditMode) {
                _addProductFormState.value = AddProductFormState()
            }
        }
    }

    fun saveProductFromForm(onSuccess: () -> Unit) {
        val form = _addProductFormState.value
        saveProduct(
            id = if (form.isEditMode) form.editingProductId else 0L,
            name = form.name,
            barcode = form.barcode,
            expiryDate = form.expiryDate,
            productionDate = form.productionDate,
            category = form.category,
            quantity = form.quantity,
            note = form.note,
            imageUri = form.imageUri,
            labelImageUri = form.labelImageUri,
            onSuccess = {
                resetAddProductForm()
                onSuccess()
            }
        )
    }

    suspend fun getProductById(id: Long): ProductEntity? {
        return repository.getProductById(id)
    }

    suspend fun getProductByBarcode(barcode: String): ProductEntity? {
        return repository.getProductByBarcode(barcode)
    }

    fun fetchProductInfoByBarcode(barcode: String, onResult: ((com.example.data.BarcodeLookupResult?) -> Unit)? = null) {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.isBlank()) return

        // Form state'deki barkod alanını güncelle
        _addProductFormState.update { it.copy(barcode = cleanBarcode) }

        viewModelScope.launch {
            isSearchingBarcode.value = true
            try {
                val result = barcodeLookupService.lookupBarcode(cleanBarcode)
                barcodeSearchResult.value = result
                if (result != null && result.name.isNotBlank()) {
                    // Gemini / API yanıtını doğrudan ViewModel Form State'e otomatik olarak aktar
                    _addProductFormState.update { current ->
                        val defaultExpiry = LocalDate.now().plusMonths(3).toString()
                        val calculatedExpiry = if (result.estimatedShelfLifeDays != null && current.expiryDate == defaultExpiry) {
                            LocalDate.now().plusDays(result.estimatedShelfLifeDays.toLong()).toString()
                        } else {
                            current.expiryDate
                        }
                        val formattedNote = if (result.source == "Gemini AI" && current.note.isBlank() && result.brand != null) {
                            "AI Tahmini: ${result.brand}"
                        } else {
                            current.note
                        }

                        current.copy(
                            name = result.name,
                            category = result.category,
                            barcode = result.barcode ?: cleanBarcode,
                            imageUri = current.imageUri ?: result.imageUrl,
                            expiryDate = calculatedExpiry,
                            note = formattedNote,
                            isAutoPopulated = true,
                            autoPopulatedSource = result.source,
                            autoPopulatedBrand = result.brand
                        )
                    }
                    _userMessage.emit("✨ Ürün bulundu: ${result.name} (${result.source})")
                } else {
                    _userMessage.emit("ℹ️ Bu barkod için ürün bilgisi bulunamadı, manuel girebilirsiniz.")
                }
                onResult?.invoke(result)
            } catch (e: Exception) {
                _userMessage.emit("⚠️ Ürün bilgisi alınırken hata: ${e.message}")
                onResult?.invoke(null)
            } finally {
                isSearchingBarcode.value = false
            }
        }
    }

    fun clearBarcodeSearchResult() {
        barcodeSearchResult.value = null
        isSearchingBarcode.value = false
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            _userMessage.emit("🗑️ '${product.name}' silindi")
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.deleteAll()
            _userMessage.emit("Tüm ürünler temizlendi")
        }
    }

    fun testAlert() {
        playAlertFeedback()
        viewModelScope.launch {
            _userMessage.emit("🔔 Uyarı testi çalıştırıldı!")
        }
    }

    fun playSuccessFeedback() {
        try {
            if (alertSoundEnabled.value) {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            }
            if (alertVibrationEnabled.value) {
                triggerVibration(100)
            }
        } catch (e: Exception) {
            // Güvenli yoksay
        }
    }

    fun playAlertFeedback() {
        try {
            if (alertSoundEnabled.value) {
                val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 350)
            }
            if (alertVibrationEnabled.value) {
                triggerVibration(300)
            }
        } catch (e: Exception) {
            // Güvenli yoksay
        }
    }

    private fun triggerVibration(milliseconds: Long) {
        try {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(milliseconds)
                }
            }
        } catch (e: Exception) {
            // Güvenli yoksay
        }
    }

    // Yedekleme: JSON Oluştur
    suspend fun exportJsonBackup(): String {
        val list = repository.getAllProductsList()
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("name", item.name)
                put("barcode", item.barcode ?: "")
                put("expiryDate", item.expiryDate)
                put("productionDate", item.productionDate ?: "")
                put("category", item.category)
                put("quantity", item.quantity)
                put("note", item.note ?: "")
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }

    // Yedekleme: JSON İçe Aktar
    fun importJsonBackup(jsonString: String) {
        viewModelScope.launch {
            try {
                val jsonArray = JSONArray(jsonString)
                val newItems = mutableListOf<ProductEntity>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    newItems.add(
                        ProductEntity(
                            name = obj.optString("name", "Ürün"),
                            barcode = obj.optString("barcode").ifBlank { null },
                            expiryDate = obj.optString("expiryDate", LocalDate.now().toString()),
                            productionDate = obj.optString("productionDate").ifBlank { null },
                            category = obj.optString("category", "Genel"),
                            quantity = obj.optInt("quantity", 1),
                            note = obj.optString("note").ifBlank { null }
                        )
                    )
                }
                if (newItems.isNotEmpty()) {
                    repository.insertAll(newItems)
                    _userMessage.emit("✅ ${newItems.size} ürün başarıyla içe aktarıldı!")
                } else {
                    _userMessage.emit("⚠️ Geçerli ürün bulunamadı.")
                }
            } catch (e: Exception) {
                _userMessage.emit("❌ Hata: Yedek dosyası formatı geçersiz.")
            }
        }
    }
}
