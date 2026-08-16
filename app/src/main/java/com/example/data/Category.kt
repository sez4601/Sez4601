package com.example.data

import androidx.compose.ui.graphics.Color

object Categories {
    val list = listOf(
        "Tümü",
        "Süt & Kahvaltılık",
        "Et & Tavuk & Şarküteri",
        "Temel Gıda & Bakliyat",
        "Meyve & Sebze",
        "İçecek & Meşrubat",
        "Atıştırmalık & Bisküvi",
        "Dondurulmuş Gıda",
        "Fırın & Unlu Mamul",
        "Kozmetik & Bakım",
        "Temizlik & Deterjan",
        "İlaç & Sağlık",
        "Diğer"
    )

    val formCategories = list.filter { it != "Tümü" }

    fun getColorForCategory(category: String): Color {
        return when (category) {
            "Süt & Kahvaltılık" -> Color(0xFF0284C7)
            "Et & Tavuk & Şarküteri" -> Color(0xFFDC2626)
            "Temel Gıda & Bakliyat" -> Color(0xFFD97706)
            "Meyve & Sebze" -> Color(0xFF16A34A)
            "İçecek & Meşrubat" -> Color(0xFF0D9488)
            "Atıştırmalık & Bisküvi" -> Color(0xFF9333EA)
            "Dondurulmuş Gıda" -> Color(0xFF2563EB)
            "Fırın & Unlu Mamul" -> Color(0xFFCA8A04)
            "Kozmetik & Bakım" -> Color(0xFFDB2777)
            "Temizlik & Deterjan" -> Color(0xFF0891B2)
            "İlaç & Sağlık" -> Color(0xFFE11D48)
            else -> Color(0xFF64748B)
        }
    }
}
