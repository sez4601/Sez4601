package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY expiryDate ASC")
    fun getAllProductsSortedByExpiry(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProductsSortedByName(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY addedDate DESC")
    fun getAllProductsSortedByNewest(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%' ORDER BY expiryDate ASC")
    fun searchProducts(query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE category = :category ORDER BY expiryDate ASC")
    fun getProductsByCategory(category: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query("SELECT * FROM products")
    suspend fun getAllProductsList(): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getCount(): Int
}
