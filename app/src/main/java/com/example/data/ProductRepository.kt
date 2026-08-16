package com.example.data

import kotlinx.coroutines.flow.Flow

class ProductRepository(private val productDao: ProductDao) {
    val allProducts: Flow<List<ProductEntity>> = productDao.getAllProducts()

    fun searchProducts(query: String): Flow<List<ProductEntity>> {
        return if (query.isBlank()) {
            productDao.getAllProducts()
        } else {
            productDao.searchProducts(query)
        }
    }

    fun getProductsByCategory(category: String): Flow<List<ProductEntity>> {
        return if (category == "Tümü" || category.isBlank()) {
            productDao.getAllProducts()
        } else {
            productDao.getProductsByCategory(category)
        }
    }

    suspend fun getProductByBarcode(barcode: String): ProductEntity? = productDao.getProductByBarcode(barcode)
    suspend fun getProductByShortCode(shortCode: String): ProductEntity? = productDao.getProductByShortCode(shortCode)

    suspend fun insertProduct(product: ProductEntity): Long = productDao.insertProduct(product)
    suspend fun updateProduct(product: ProductEntity) = productDao.updateProduct(product)
    suspend fun deleteProduct(product: ProductEntity) = productDao.deleteProduct(product)
    suspend fun updateStock(id: Int, newQuantity: Int) = productDao.updateStock(id, newQuantity)
    suspend fun getCount(): Int = productDao.getCount()
}
