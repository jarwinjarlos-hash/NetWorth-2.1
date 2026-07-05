package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "asset_categories")
data class AssetCategory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isAsset: Boolean = true,
    val isDirty: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "assets")
data class Asset(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val currency: String, // e.g. "USD", "EUR", "PHP", "GBP", etc.
    val categoryId: Int, // references AssetCategory.id
    val currentValuation: Double = 0.0, // updated periodically via manual input or transactions
    val isArchived: Boolean = false,
    val includeInPortfolio: Boolean = true,
    val bucketId: Int? = null,
    val isDirty: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "buckets")
data class Bucket(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String = "",
    val targetAmount: Double = 0.0,
    val isDecumulation: Boolean = false,
    val yearlySpendBudget: Double = 0.0,
    val bufferYears: Int = 5,
    val warningThresholdPercent: Double = 20.0,
    val targetGainPercent: Double = 6.0,
    val lastYearPerformancePercent: Double = 0.0,
    val isDirty: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val assetId: Int, // source asset ID
    val type: String, // "DEPOSIT", "WITHDRAWAL", "UPDATE", "INCOME", "TRANSFER"
    val amount: Double, // in native currency of assetId (for UPDATE, it is the new total valuation)
    val timestamp: Long,
    val destinationAssetId: Int? = null, // for transfer
    val exchangeRate: Double? = null, // exchange rate from source currency to target currency: targeting = source * exchangeRate
    val notes: String? = null,
    val isDirty: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "portfolio_snapshots")
data class PortfolioSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val netWorthUsd: Double,
    val totalAssetsUsd: Double,
    val totalLiabilitiesUsd: Double,
    val isDirty: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class ChartPoint(
    val timestamp: Long,
    val netWorthUsd: Double,
    val totalAssetsUsd: Double,
    val totalLiabilitiesUsd: Double,
    val intervalGainUsd: Double
)

