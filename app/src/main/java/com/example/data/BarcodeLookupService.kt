package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class BarcodeLookupResult(
    val name: String,
    val category: String,
    val brand: String? = null,
    val imageUrl: String? = null,
    val estimatedShelfLifeDays: Int? = null,
    val source: String,
    val barcode: String? = null
)

class BarcodeLookupService(private val repository: ProductRepository) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun lookupBarcode(barcode: String): BarcodeLookupResult? = withContext(Dispatchers.IO) {
        val cleanBarcode = barcode.trim()
        if (cleanBarcode.length < 4) return@withContext null

        // 1. Önce yerel veritabanında bu barkod kayıtlı mı kontrol et
        val localExisting = repository.getProductByBarcode(cleanBarcode)
        if (localExisting != null) {
            return@withContext BarcodeLookupResult(
                name = localExisting.name,
                category = localExisting.category,
                brand = null,
                imageUrl = localExisting.imageUri,
                estimatedShelfLifeDays = null,
                source = "Kayıtlı Ürün",
                barcode = cleanBarcode
            )
        }

        // 2. Open Food Facts API Sorgusu (Dünya / Türkiye Barkod Veritabanı)
        val offResult = fetchFromOpenFoodFacts(cleanBarcode)
        if (offResult != null && offResult.name.isNotBlank()) {
            return@withContext offResult.copy(barcode = cleanBarcode)
        }

        // 3. Gemini AI ile Akıllı Barkod & Ürün Tanıma (gemini-3.5-flash)
        val geminiResult = fetchFromGemini(cleanBarcode)
        if (geminiResult != null && geminiResult.name.isNotBlank()) {
            return@withContext geminiResult.copy(barcode = cleanBarcode)
        }

        // 4. EAN Ülke / Kod Önekine Göre Temel Bilgi (Fallback)
        return@withContext inferFromBarcodePrefix(cleanBarcode)?.copy(barcode = cleanBarcode)
    }

    private fun fetchFromOpenFoodFacts(barcode: String): BarcodeLookupResult? {
        try {
            val url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SKTTakipApp/1.0 (Android; Contact: support@skt-takip.com)")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyString = response.body?.string() ?: return null
                val json = JSONObject(bodyString)

                val status = json.optInt("status", 0)
                if (status != 1) return null

                val product = json.optJSONObject("product") ?: return null

                val nameTr = product.optString("product_name_tr")
                val nameDef = product.optString("product_name")
                val nameEn = product.optString("product_name_en")
                val brands = product.optString("brands")

                val finalName = when {
                    nameTr.isNotBlank() -> nameTr
                    nameDef.isNotBlank() -> nameDef
                    nameEn.isNotBlank() -> nameEn
                    else -> ""
                }

                if (finalName.isBlank()) return null

                val combinedName = if (brands.isNotBlank() && !finalName.startsWith(brands, ignoreCase = true)) {
                    "$brands $finalName"
                } else {
                    finalName
                }

                val imageUrl = product.optString("image_front_url").ifBlank {
                    product.optString("image_url").ifBlank { null }
                }

                val categoriesTags = product.optJSONArray("categories_tags")
                val matchedCategory = mapCategoriesTagsToAppCategory(categoriesTags, combinedName)

                return BarcodeLookupResult(
                    name = combinedName,
                    category = matchedCategory,
                    brand = brands.ifBlank { null },
                    imageUrl = imageUrl,
                    estimatedShelfLifeDays = estimateShelfLifeDays(matchedCategory),
                    source = "Open Food Facts"
                )
            }
        } catch (e: Exception) {
            Log.w("BarcodeLookup", "OpenFoodFacts error: ${e.message}")
            return null
        }
    }

    private fun fetchFromGemini(barcode: String): BarcodeLookupResult? {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return null
        }

        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val prompt = """
                Sen bir barkod ve ürün analiz uzmanısın.
                Verilen EAN barkod numarasını analiz et: "$barcode".
                Bu barkodun hangi ürüne, markaya veya ürün tipine ait olduğunu ve kategorisini belirle.
                Kategori için SADECE aşağıdaki listeden tam olarak eşleşen birini seç:
                ${Categories.formCategories.joinToString(", ")}

                Lütfen SADECE geçerli bir JSON yanıtı döndür, başka hiçbir metin veya markdown bloğu yazma.
                JSON formatı:
                {
                    "name": "Ürün Adı ve Gramajı/Hacmi",
                    "brand": "Marka",
                    "category": "Kategori",
                    "estimatedShelfLifeDays": 90,
                    "note": "Kısa saklama önerisi"
                }
            """.trimIndent()

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val responseString = response.body?.string() ?: return null
                val rootJson = JSONObject(responseString)

                val candidates = rootJson.optJSONArray("candidates") ?: return null
                val firstCandidate = candidates.optJSONObject(0) ?: return null
                val content = firstCandidate.optJSONObject("content") ?: return null
                val parts = content.optJSONArray("parts") ?: return null
                val text = parts.optJSONObject(0)?.optString("text") ?: return null

                val cleanedJsonStr = text.replace("```json", "").replace("```", "").trim()
                val parsed = JSONObject(cleanedJsonStr)

                val name = parsed.optString("name")
                val brand = parsed.optString("brand")
                var category = parsed.optString("category")
                val shelfLife = parsed.optInt("estimatedShelfLifeDays", 30)

                if (!Categories.formCategories.contains(category)) {
                    category = mapCategoriesTagsToAppCategory(null, name)
                }

                if (name.isNotBlank()) {
                    return BarcodeLookupResult(
                        name = name,
                        category = category,
                        brand = brand.ifBlank { null },
                        imageUrl = null,
                        estimatedShelfLifeDays = shelfLife,
                        source = "Gemini AI"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("BarcodeLookup", "Gemini error: ${e.message}")
        }
        return null
    }

    private fun inferFromBarcodePrefix(barcode: String): BarcodeLookupResult? {
        // Türkiye barkodları (869 veya 868 ile başlar)
        if (barcode.startsWith("869") || barcode.startsWith("868")) {
            return BarcodeLookupResult(
                name = "",
                category = "Temel Gıda & Bakliyat",
                brand = null,
                imageUrl = null,
                estimatedShelfLifeDays = 60,
                source = "GS1 Türkiye"
            )
        }
        return null
    }

    private fun mapCategoriesTagsToAppCategory(categoriesTags: JSONArray?, productName: String): String {
        val lowerText = (categoriesTags?.toString() ?: "") + " " + productName.lowercase()

        return when {
            lowerText.contains("süt") || lowerText.contains("milk") || lowerText.contains("peynir") ||
            lowerText.contains("cheese") || lowerText.contains("yoğurt") || lowerText.contains("yogurt") ||
            lowerText.contains("tereyağ") || lowerText.contains("butter") || lowerText.contains("kahvaltılık") -> "Süt & Kahvaltılık"

            lowerText.contains("et") || lowerText.contains("meat") || lowerText.contains("tavuk") ||
            lowerText.contains("chicken") || lowerText.contains("sucuk") || lowerText.contains("sosis") ||
            lowerText.contains("salam") || lowerText.contains("şarküteri") || lowerText.contains("balık") -> "Et & Tavuk & Şarküteri"

            lowerText.contains("meyve") || lowerText.contains("fruit") || lowerText.contains("sebze") ||
            lowerText.contains("vegetable") || lowerText.contains("salata") -> "Meyve & Sebze"

            lowerText.contains("içecek") || lowerText.contains("beverage") || lowerText.contains("drink") ||
            lowerText.contains("su") || lowerText.contains("water") || lowerText.contains("soda") ||
            lowerText.contains("gazoz") || lowerText.contains("kola") || lowerText.contains("juice") ||
            lowerText.contains("meyve suyu") || lowerText.contains("kahve") || lowerText.contains("çay") -> "İçecek & Meşrubat"

            lowerText.contains("bisküvi") || lowerText.contains("çikolata") || lowerText.contains("chocolate") ||
            lowerText.contains("gofret") || lowerText.contains("cips") || lowerText.contains("snack") ||
            lowerText.contains("atıştırmalık") || lowerText.contains("şekerleme") || lowerText.contains("kraker") -> "Atıştırmalık & Bisküvi"

            lowerText.contains("dondurma") || lowerText.contains("ice cream") || lowerText.contains("dondurulmuş") ||
            lowerText.contains("frozen") || lowerText.contains("pizza") -> "Dondurulmuş Gıda"

            lowerText.contains("ekmek") || lowerText.contains("bread") || lowerText.contains("fırın") ||
            lowerText.contains("unlu") || lowerText.contains("pasta") || lowerText.contains("kek") ||
            lowerText.contains("un") || lowerText.contains("un ") -> "Fırın & Unlu Mamul"

            lowerText.contains("şampuan") || lowerText.contains("sabun") || lowerText.contains("krem") ||
            lowerText.contains("kozmetik") || lowerText.contains("diş macunu") || lowerText.contains("losyon") -> "Kozmetik & Bakım"

            lowerText.contains("deterjan") || lowerText.contains("çamaşır") || lowerText.contains("bulaşık") ||
            lowerText.contains("temizlik") || lowerText.contains("yumuşatıcı") -> "Temizlik & Deterjan"

            lowerText.contains("ilaç") || lowerText.contains("vitamin") || lowerText.contains("sağlık") ||
            lowerText.contains("şurup") || lowerText.contains("tablet") || lowerText.contains("hap") -> "İlaç & Sağlık"

            lowerText.contains("makarna") || lowerText.contains("pirinç") || lowerText.contains("bulgur") ||
            lowerText.contains("bakliyat") || lowerText.contains("yağ") || lowerText.contains("salça") ||
            lowerText.contains("baharat") || lowerText.contains("konserve") -> "Temel Gıda & Bakliyat"

            else -> "Genel"
        }
    }

    private fun estimateShelfLifeDays(category: String): Int {
        return when (category) {
            "Süt & Kahvaltılık" -> 14
            "Et & Tavuk & Şarküteri" -> 5
            "Meyve & Sebze" -> 7
            "Fırın & Unlu Mamul" -> 4
            "Atıştırmalık & Bisküvi" -> 180
            "İçecek & Meşrubat" -> 120
            "Dondurulmuş Gıda" -> 240
            "Temel Gıda & Bakliyat" -> 365
            "Kozmetik & Bakım" -> 730
            "Temizlik & Deterjan" -> 730
            "İlaç & Sağlık" -> 365
            else -> 60
        }
    }
}
