package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val category: String,
    val categoryColorHex: String,
    val shortCode: String,
    val barcode: String = "",
    val imagePath: String? = null,
    val stockQuantity: Int = 1,
    val price: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)
