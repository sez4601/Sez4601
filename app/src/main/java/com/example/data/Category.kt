package com.example.data

import androidx.compose.ui.graphics.Color

data class CategoryItem(
    val name: String,
    val colorHex: String,
    val displayColor: Color
)

object Categories {
    val defaultList = listOf(
        CategoryItem("Gıda", "#FD7E14", Color(0xFFFD7E14)),
        CategoryItem("İçecek", "#0D6EFD", Color(0xFF0D6EFD)),
        CategoryItem("Atıştırmalık", "#FFC107", Color(0xFFFFC107)),
        CategoryItem("Temizlik", "#0DCAF0", Color(0xFF0DCAF0)),
        CategoryItem("Manav", "#198754", Color(0xFF198754)),
        CategoryItem("Şarküteri", "#DC3545", Color(0xFFDC3545)),
        CategoryItem("Kişisel Bakım", "#6F42C1", Color(0xFF6F42C1)),
        CategoryItem("Genel", "#6C757D", Color(0xFF6C757D))
    )

    fun getColorForCategory(categoryName: String): Color {
        return defaultList.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }?.displayColor
            ?: Color(0xFF0D6EFD)
    }

    fun getColorHexForCategory(categoryName: String): String {
        return defaultList.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }?.colorHex
            ?: "#0D6EFD"
    }
}
