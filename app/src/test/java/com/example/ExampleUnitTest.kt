package com.example

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

    @Test
    fun runAudit() {
        // Read the backup JSON
        val jsonFile = File("backup.json")
        assertTrue("backup.json file must exist", jsonFile.exists())
        val jsonStr = jsonFile.readText()
        val json = JSONObject(jsonStr)

        val categoriesArray = json.getJSONArray("categories")
        val assetsArray = json.getJSONArray("assets")
        val transactionsArray = json.getJSONArray("transactions")

        // Parse Categories
        val categoriesMap = mutableMapOf<Int, CategoryInfo>()
        for (i in 0 until categoriesArray.length()) {
            val cat = categoriesArray.getJSONObject(i)
            val id = cat.getInt("id")
            val name = cat.getString("name")
            val isAsset = cat.getBoolean("isAsset")
            categoriesMap[id] = CategoryInfo(id, name, isAsset)
        }

        // Parse Assets
        val assetsMap = mutableMapOf<Int, AssetInfo>()
        for (i in 0 until assetsArray.length()) {
            val ast = assetsArray.getJSONObject(i)
            val id = ast.getInt("id")
            val name = ast.getString("name")
            val currency = ast.getString("currency")
            val categoryId = ast.getInt("categoryId")
            val includeInPortfolio = ast.optBoolean("includeInPortfolio", true)
            val isArchived = ast.optBoolean("isArchived", false)
            assetsMap[id] = AssetInfo(id, name, currency, categoryId, includeInPortfolio, isArchived)
        }

        // Parse Transactions
        val txList = mutableListOf<TransactionInfo>()
        for (i in 0 until transactionsArray.length()) {
            val tx = transactionsArray.getJSONObject(i)
            val id = tx.getInt("id")
            val assetId = tx.getInt("assetId")
            val type = tx.getString("type")
            val amount = tx.getDouble("amount")
            val timestamp = tx.getLong("timestamp")
            val destinationAssetId = if (tx.isNull("destinationAssetId")) null else tx.getInt("destinationAssetId")
            val exchangeRate = if (tx.isNull("exchangeRate")) null else tx.getDouble("exchangeRate")
            txList.add(TransactionInfo(id, assetId, type, amount, timestamp, destinationAssetId, exchangeRate))
        }

        // Rates
        val exchangeRates = mapOf(
            "USD" to 1.0,
            "EUR" to 1.10,
            "GBP" to 1.28,
            "CAD" to 0.73,
            "AUD" to 0.66,
            "SGD" to 0.74,
            "JPY" to 0.0063,
            "PHP" to 0.017,
            "SAR" to 0.2667,
            "BTC" to 65000.0
        )

        fun convertToUsd(amount: Double, currency: String): Double {
            val rate = exchangeRates[currency] ?: 1.0
            return amount * rate
        }

        println("==================================================")
        println("AUDITING PORTFOLIO ASSETS:")
        println("==================================================")

        var totalAssetsUsd = 0.0
        var totalLiabilitiesUsd = 0.0
        var totalNetDepositsUsd = 0.0
        var totalIncomeUsd = 0.0

        for ((id, asset) in assetsMap) {
            if (!asset.includeInPortfolio || asset.isArchived) {
                continue
            }

            val cat = categoriesMap[asset.categoryId]
            val isAssetType = cat?.isAsset ?: true

            // Get relevant txs
            val relevantTxs = txList.filter {
                it.assetId == asset.id || it.destinationAssetId == asset.id
            }.sortedWith(compareBy<TransactionInfo> { it.timestamp }.thenBy { it.id })

            // 1. Calculate Net Deposits
            var netDepositsNative = 0.0
            var totalIncomeNative = 0.0

            val firstUpdate = relevantTxs.firstOrNull { it.type == "UPDATE" }
            val depositsBeforeUpdate = if (firstUpdate != null) {
                relevantTxs.filter {
                    it.timestamp < firstUpdate.timestamp && (it.type == "DEPOSIT" || it.type == "TRANSFER")
                }.sumOf {
                    if (it.assetId == asset.id) {
                        if (it.type == "DEPOSIT") it.amount else 0.0
                    } else if (it.destinationAssetId == asset.id && it.type == "TRANSFER") {
                        it.amount * (it.exchangeRate ?: 1.0)
                    } else {
                        0.0
                    }
                }
            } else {
                0.0
            }

            if (firstUpdate != null && depositsBeforeUpdate == 0.0) {
                netDepositsNative += firstUpdate.amount
            }

            for (tx in relevantTxs) {
                if (tx.assetId == asset.id) {
                    if (isAssetType) {
                        when (tx.type) {
                            "DEPOSIT" -> netDepositsNative += tx.amount
                            "WITHDRAWAL" -> netDepositsNative -= tx.amount
                            "INCOME" -> totalIncomeNative += tx.amount
                            "TRANSFER" -> netDepositsNative -= tx.amount
                        }
                    } else {
                        when (tx.type) {
                            "DEPOSIT" -> netDepositsNative -= tx.amount
                            "WITHDRAWAL" -> netDepositsNative += tx.amount
                            "INCOME" -> netDepositsNative -= tx.amount
                            "TRANSFER" -> netDepositsNative += tx.amount
                        }
                    }
                } else if (tx.destinationAssetId == asset.id) {
                    if (tx.type == "TRANSFER") {
                        val incomingAmount = tx.amount * (tx.exchangeRate ?: 1.0)
                        if (isAssetType) {
                            netDepositsNative += incomingAmount
                        } else {
                            netDepositsNative -= incomingAmount
                        }
                    }
                }
            }

            // 2. Valuation
            val updateTxs = relevantTxs.filter { it.assetId == asset.id && it.type == "UPDATE" }
                .sortedWith(compareBy<TransactionInfo> { it.timestamp }.thenBy { it.id })

            val latestUpdate = updateTxs.lastOrNull()
            val latestUpdateTimestamp = latestUpdate?.timestamp ?: 0L
            val baseValuation = latestUpdate?.amount ?: 0.0

            var additionsAfterUpdate = 0.0
            val transactionsAfterUpdate = if (latestUpdate != null) {
                relevantTxs.filter { tx -> tx.timestamp > latestUpdateTimestamp || (tx.timestamp == latestUpdateTimestamp && tx.id > latestUpdate.id) }
            } else {
                relevantTxs
            }

            for (tx in transactionsAfterUpdate) {
                if (tx.assetId == asset.id) {
                    if (isAssetType) {
                        when (tx.type) {
                            "DEPOSIT" -> additionsAfterUpdate += tx.amount
                            "WITHDRAWAL" -> additionsAfterUpdate -= tx.amount
                            "INCOME" -> additionsAfterUpdate += tx.amount
                            "TRANSFER" -> additionsAfterUpdate -= tx.amount
                        }
                    } else {
                        when (tx.type) {
                            "DEPOSIT" -> additionsAfterUpdate -= tx.amount
                            "WITHDRAWAL" -> additionsAfterUpdate += tx.amount
                            "INCOME" -> additionsAfterUpdate -= tx.amount
                            "TRANSFER" -> additionsAfterUpdate += tx.amount
                        }
                    }
                } else if (tx.destinationAssetId == asset.id) {
                    if (tx.type == "TRANSFER") {
                        val incomingAmount = tx.amount * (tx.exchangeRate ?: 1.0)
                        if (isAssetType) {
                            additionsAfterUpdate += incomingAmount
                        } else {
                            additionsAfterUpdate -= incomingAmount
                        }
                    }
                }
            }

            val currentValNative = if (latestUpdate == null) {
                netDepositsNative + totalIncomeNative
            } else {
                baseValuation + additionsAfterUpdate
            }

            val currentValUsd = convertToUsd(currentValNative, asset.currency)
            val netDepositsUsd = convertToUsd(netDepositsNative, asset.currency)
            val totalIncomeUsdVal = convertToUsd(totalIncomeNative, asset.currency)

            println("Asset: ${asset.name} (${asset.currency})")
            println("  Category: ${cat?.name} (isAsset = $isAssetType)")
            println("  Valuation Native: $currentValNative -> USD: $currentValUsd")
            println("  Net Deposits Native: $netDepositsNative -> USD: $netDepositsUsd")
            println("  Income Native: $totalIncomeNative -> USD: $totalIncomeUsdVal")

            if (isAssetType) {
                totalAssetsUsd += currentValUsd
                totalNetDepositsUsd += netDepositsUsd
            } else {
                totalLiabilitiesUsd += currentValUsd
                totalNetDepositsUsd -= netDepositsUsd // Liabilities net deposits are negative outflows
            }
            totalIncomeUsd += totalIncomeUsdVal
        }

        val netWorth = totalAssetsUsd - totalLiabilitiesUsd
        val pl = netWorth - totalNetDepositsUsd

        println("==================================================")
        println("SUMMARY RESULTS (IN USD):")
        println("==================================================")
        println("Total Assets USD: $totalAssetsUsd")
        println("Total Liabilities USD: $totalLiabilitiesUsd")
        println("NET WORTH USD: $netWorth")
        println("TOTAL NET DEPOSITS USD: $totalNetDepositsUsd")
        println("TOTAL PL / GAIN USD: $pl")
        println("TOTAL INCOME USD: $totalIncomeUsd")
        println("==================================================")
    }

    data class CategoryInfo(val id: Int, val name: String, val isAsset: Boolean)
    data class AssetInfo(val id: Int, val name: String, val currency: String, val categoryId: Int, val includeInPortfolio: Boolean, val isArchived: Boolean)
    data class TransactionInfo(val id: Int, val assetId: Int, val type: String, val amount: Double, val timestamp: Long, val destinationAssetId: Int?, val exchangeRate: Double?)
}
