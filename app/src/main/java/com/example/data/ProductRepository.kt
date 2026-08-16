package com.example.data

import kotlinx.coroutines.flow.Flow

class ProductRepository(private val dao: ProductDao) {
    val allProductsSortedByExpiry: Flow<List<ProductEntity>> = dao.getAllProductsSortedByExpiry()
    val allProductsSortedByName: Flow<List<ProductEntity>> = dao.getAllProductsSortedByName()
    val allProductsSortedByNewest: Flow<List<ProductEntity>> = dao.getAllProductsSortedByNewest()

    fun searchProducts(query: String): Flow<List<ProductEntity>> = dao.searchProducts(query)
    fun getProductsByCategory(category: String): Flow<List<ProductEntity>> = dao.getProductsByCategory(category)

    suspend fun getProductByBarcode(barcode: String): ProductEntity? = dao.getProductByBarcode(barcode)
    suspend fun getProductById(id: Long): ProductEntity? = dao.getProductById(id)
    suspend fun getAllProductsList(): List<ProductEntity> = dao.getAllProductsList()

    suspend fun insertProduct(product: ProductEntity): Long = dao.insertProduct(product)
    suspend fun insertAll(products: List<ProductEntity>) = dao.insertAll(products)
    suspend fun updateProduct(product: ProductEntity) = dao.updateProduct(product)
    suspend fun deleteProduct(product: ProductEntity) = dao.deleteProduct(product)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun getCount(): Int = dao.getCount()
}
