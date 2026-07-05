package com.example.data.local

import androidx.room.*
import com.example.data.model.AssetCategory
import com.example.data.model.Asset
import com.example.data.model.Transaction
import com.example.data.model.PortfolioSnapshot
import com.example.data.model.Bucket
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialDao {

    // Asset Categories
    @Query("SELECT * FROM asset_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<AssetCategory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: AssetCategory): Long

    @Update
    suspend fun updateCategory(category: AssetCategory)

    @Delete
    suspend fun deleteCategory(category: AssetCategory)

    @Query("DELETE FROM asset_categories WHERE id = :id")
    suspend fun deleteCategoryById(id: Int)


    // Assets
    @Query("SELECT * FROM assets ORDER BY name ASC")
    fun getAllAssets(): Flow<List<Asset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: Asset): Long

    @Update
    suspend fun updateAsset(asset: Asset)

    @Delete
    suspend fun deleteAsset(asset: Asset)

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun deleteAssetById(id: Int)


    // Transactions
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE assetId = :assetId OR destinationAssetId = :assetId ORDER BY timestamp DESC, id DESC")
    fun getTransactionsForAsset(assetId: Int): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Int)

    @Query("DELETE FROM transactions WHERE assetId = :assetId OR destinationAssetId = :assetId")
    suspend fun deleteTransactionsForAsset(assetId: Int)


    // Snapshots
    @Query("SELECT * FROM portfolio_snapshots ORDER BY timestamp ASC")
    fun getAllSnapshots(): Flow<List<PortfolioSnapshot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: PortfolioSnapshot): Long

    @Query("DELETE FROM portfolio_snapshots")
    suspend fun clearSnapshots()

    @Query("DELETE FROM asset_categories")
    suspend fun clearCategories()

    @Query("DELETE FROM assets")
    suspend fun clearAssets()

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM buckets")
    suspend fun clearBuckets()

    // Buckets
    @Query("SELECT * FROM buckets ORDER BY name ASC")
    fun getAllBuckets(): Flow<List<Bucket>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBucket(bucket: Bucket): Long

    @Update
    suspend fun updateBucket(bucket: Bucket)

    @Delete
    suspend fun deleteBucket(bucket: Bucket)

    @Query("DELETE FROM buckets WHERE id = :id")
    suspend fun deleteBucketById(id: Int)

    @Query("UPDATE assets SET bucketId = NULL WHERE bucketId = :bucketId")
    suspend fun removeBucketFromAssets(bucketId: Int)
}
