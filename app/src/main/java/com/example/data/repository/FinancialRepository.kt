package com.example.data.repository

import com.example.data.local.FinancialDao
import com.example.data.model.AssetCategory
import com.example.data.model.Asset
import com.example.data.model.Transaction
import com.example.data.model.PortfolioSnapshot
import com.example.data.model.Bucket
import kotlinx.coroutines.flow.Flow

class FinancialRepository(private val financialDao: FinancialDao) {

    // Categories
    val allCategories: Flow<List<AssetCategory>> = financialDao.getAllCategories()

    suspend fun insertCategory(category: AssetCategory): Long {
        return financialDao.insertCategory(category)
    }

    suspend fun updateCategory(category: AssetCategory) {
        financialDao.updateCategory(category)
    }

    suspend fun deleteCategory(category: AssetCategory) {
        financialDao.deleteCategory(category)
    }

    suspend fun deleteCategoryById(id: Int) {
        financialDao.deleteCategoryById(id)
    }


    // Assets
    val allAssets: Flow<List<Asset>> = financialDao.getAllAssets()

    suspend fun insertAsset(asset: Asset): Long {
        return financialDao.insertAsset(asset)
    }

    suspend fun updateAsset(asset: Asset) {
        financialDao.updateAsset(asset)
    }

    suspend fun deleteAsset(asset: Asset) {
        // delete matching transactions too
        financialDao.deleteTransactionsForAsset(asset.id)
        financialDao.deleteAsset(asset)
    }

    suspend fun deleteAssetById(id: Int) {
        financialDao.deleteTransactionsForAsset(id)
        financialDao.deleteAssetById(id)
    }


    // Transactions
    val allTransactions: Flow<List<Transaction>> = financialDao.getAllTransactions()

    fun getTransactionsForAsset(assetId: Int): Flow<List<Transaction>> {
        return financialDao.getTransactionsForAsset(assetId)
    }

    suspend fun insertTransaction(transaction: Transaction): Long {
        return financialDao.insertTransaction(transaction)
    }

    suspend fun updateTransaction(transaction: Transaction) {
        financialDao.updateTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        financialDao.deleteTransaction(transaction)
    }

    suspend fun deleteTransactionById(id: Int) {
        financialDao.deleteTransactionById(id)
    }


    // Snapshots
    val allSnapshots: Flow<List<PortfolioSnapshot>> = financialDao.getAllSnapshots()

    suspend fun insertSnapshot(snapshot: PortfolioSnapshot) {
        financialDao.insertSnapshot(snapshot)
    }

    suspend fun clearSnapshots() {
        financialDao.clearSnapshots()
    }

    // Buckets
    val allBuckets: Flow<List<Bucket>> = financialDao.getAllBuckets()

    suspend fun insertBucket(bucket: Bucket): Long {
        return financialDao.insertBucket(bucket)
    }

    suspend fun updateBucket(bucket: Bucket) {
        financialDao.updateBucket(bucket)
    }

    suspend fun deleteBucket(bucket: Bucket) {
        financialDao.removeBucketFromAssets(bucket.id)
        financialDao.deleteBucket(bucket)
    }

    suspend fun deleteBucketById(id: Int) {
        financialDao.removeBucketFromAssets(id)
        financialDao.deleteBucketById(id)
    }

    suspend fun clearCategories() {
        financialDao.clearCategories()
    }

    suspend fun clearAssets() {
        financialDao.clearAssets()
    }

    suspend fun clearTransactions() {
        financialDao.clearTransactions()
    }

    suspend fun clearBuckets() {
        financialDao.clearBuckets()
    }
}
