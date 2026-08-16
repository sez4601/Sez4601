package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val barcode: String? = null,
    val expiryDate: String, // format: yyyy-MM-dd
    val productionDate: String? = null,
    val category: String = "Genel",
    val quantity: Int = 1,
    val note: String? = null,
    val imageUri: String? = null,
    val labelImageUri: String? = null,
    val addedDate: Long = System.currentTimeMillis()
) {
    // Kalan gün sayısını hesaplar
    fun getRemainingDays(): Long {
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val expiry = LocalDate.parse(expiryDate, formatter)
            val today = LocalDate.now()
            ChronoUnit.DAYS.between(today, expiry)
        } catch (e: Exception) {
            0L
        }
    }

    // Durum: EXPIRED, CRITICAL, WARNING, SAFE
    fun getStatus(): ProductStatus {
        val days = getRemainingDays()
        return when {
            days < 0 -> ProductStatus.EXPIRED
            days <= 3 -> ProductStatus.CRITICAL
            days <= 7 -> ProductStatus.WARNING
            else -> ProductStatus.SAFE
        }
    }

    // Formatlanmış SKT gösterimi (dd.MM.yyyy)
    fun getFormattedExpiryDate(): String {
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val targetFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            val date = LocalDate.parse(expiryDate, formatter)
            date.format(targetFormatter)
        } catch (e: Exception) {
            expiryDate
        }
    }

    // Formatlanmış Eklenme Tarihi (dd.MM.yyyy)
    fun getFormattedAddedDate(): String {
        return try {
            val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale("tr", "TR"))
            sdf.format(java.util.Date(addedDate))
        } catch (e: Exception) {
            "-"
        }
    }

    // Kalan Gün Metni (örn: "3 gün kaldı", "Bugün son gün!", "2 gün geçti")
    fun getRemainingDaysText(): String {
        val days = getRemainingDays()
        return when {
            days < 0 -> "${-days} gün geçti"
            days == 0L -> "Bugün son gün!"
            days == 1L -> "1 gün kaldı"
            else -> "$days gün kaldı"
        }
    }
}

enum class ProductStatus(val labelTr: String) {
    EXPIRED("Süresi Doldu"),
    CRITICAL("Kritik"),
    WARNING("Yaklaşıyor"),
    SAFE("Güvenli")
}
