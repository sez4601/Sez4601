package com.example.util

object BarcodeHelper {

    /**
     * Taranan veya girilen ham barkod metninden SADECE ardışık 13 haneli rakam dizisini ayıklar.
     * 
     * Örnekler:
     * - "M102-8690504012345-F19,90" -> "8690504012345" (Mağaza kodu, tire, f, virgül, fiyat temizlenir)
     * - "8690123456789" -> "8690123456789"
     * - "8690123456789012" -> "8690123456789" (868/869 veya ilk 13 hane)
     * - "abc-8681234567890-f,25" -> "8681234567890"
     */
    fun extract13DigitBarcode(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null

        val trimmed = rawText.trim()

        // 1. Türkiye GS1 kodları (868 veya 869 ile başlayan 13 hane) metin içinde varsa öncelikle onu al
        val trMatch = Regex("""86[89]\d{10}""").find(trimmed)
        if (trMatch != null) {
            return trMatch.value
        }

        // 2. Metin içinde ardışık 13 haneli herhangi bir rakam grubu (\d{13})
        val general13Match = Regex("""\d{13}""").find(trimmed)
        if (general13Match != null) {
            return general13Match.value
        }

        // 3. Harf, tire, virgül, boşluk vb. temizlendikten sonra tam 13 rakam kalıyorsa
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.length == 13) {
            return digitsOnly
        }

        // 4. Eğer temizlenen rakamlar 13'ten fazlaysa (örn: mağaza kodu rakamsa + ürün barkodu + fiyat):
        val trDigitsMatch = Regex("""86[89]\d{10}""").find(digitsOnly)
        if (trDigitsMatch != null) {
            return trDigitsMatch.value
        }

        // 13'ten uzun genel rakam dizisi içinde 13 haneli blok:
        if (digitsOnly.length > 13) {
            val sub13 = Regex("""\d{13}""").find(digitsOnly)
            if (sub13 != null) return sub13.value
            return digitsOnly.substring(0, 13)
        }

        // 5. Eğer tam 8 haneli saf EAN-8 ise fallback olarak kabul et
        if (digitsOnly.length == 8) {
            return digitsOnly
        }

        return null
    }

    /**
     * Verilen barkodun geçerli 13 haneli bir rakam olup olmadığını kontrol eder.
     */
    fun isValid13DigitBarcode(barcode: String?): Boolean {
        if (barcode.isNullOrBlank()) return false
        return barcode.length == 13 && barcode.all { it.isDigit() }
    }

    /**
     * Manuel giriş alanında klavyeden girilen karakterleri filtreler:
     * Harf, tire (-), virgül (,), f gibi tüm yabancı karakterleri temizler, sadece rakamları alır.
     * Maksimum 13 haneye izin verir.
     */
    fun cleanDigitsOnly(input: String, maxLength: Int = 13): String {
        return input.filter { it.isDigit() }.take(maxLength)
    }
}
