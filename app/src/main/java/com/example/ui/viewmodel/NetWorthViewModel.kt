package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Locale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.AssetCategory
import com.example.data.model.Asset
import com.example.data.model.Transaction
import com.example.data.model.PortfolioSnapshot
import com.example.data.model.ChartPoint
import com.example.data.model.Bucket
import com.example.data.repository.FinancialRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.absoluteValue

// Unified data wrapper for UI calculations
data class AssetWithMetrics(
    val asset: Asset,
    val categoryName: String,
    val isAssetType: Boolean,
    val currentValuationNative: Double,
    val currentValuationUsd: Double,
    val netDepositsNative: Double,
    val netDepositsUsd: Double,
    val totalIncomeNative: Double,
    val totalIncomeUsd: Double,
    val gainLossUsd: Double,
    val gainLossNative: Double,
    val gainLossPercent: Double,
    val periodCostBasisUsd: Double = 0.0,
)

data class CategoryWithMetrics(
    val category: AssetCategory,
    val totalValuationUsd: Double,
    val totalGainLossUsd: Double,
    val totalGainLossPercent: Double,
    val assets: List<AssetWithMetrics>
)

data class BucketWithMetrics(
    val bucket: Bucket?, // null represents Unassigned assets
    val totalValuationUsd: Double,
    val totalGainLossUsd: Double,
    val totalGainLossPercent: Double,
    val assets: List<AssetWithMetrics>
)

data class PerformanceMetrics(
    val totalValuationUsd: Double,
    val totalGainLossUsd: Double,
    val totalGainLossPercent: Double,
    val netDepositsUsd: Double,
    val totalIncomeUsd: Double,
    val ytdGainLossUsd: Double = 0.0,
    val ytdGainLossPercent: Double = 0.0,
    val totalLiabilitiesUsd: Double = 0.0
)

class NetWorthViewModel(
    application: Application,
    val repository: FinancialRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("networth_prefs", Context.MODE_PRIVATE)

    val syncManager = com.example.data.sync.GoogleDriveSyncManager(application, repository)

    private val _isDriveSignedIn = MutableStateFlow(syncManager.isUserSignedIn())
    val isDriveSignedIn = _isDriveSignedIn.asStateFlow()

    private val _driveEmail = MutableStateFlow(syncManager.getSignedInAccount()?.email ?: "")
    val driveEmail = _driveEmail.asStateFlow()

    private val _autoSyncDrive = MutableStateFlow(prefs.getBoolean("auto_sync_drive", false))
    val autoSyncDrive: StateFlow<Boolean> = _autoSyncDrive.asStateFlow()

    fun setAutoSyncDrive(enabled: Boolean) {
        _autoSyncDrive.value = enabled
        prefs.edit().putBoolean("auto_sync_drive", enabled).apply()
        if (enabled) {
            triggerInitialSync()
        }
    }

    fun triggerInitialSync() {
        viewModelScope.launch {
            if (syncManager.isUserSignedIn()) {
                try {
                    val downloadRes = syncManager.downloadCloudToLocal()
                    if (downloadRes.isSuccess) {
                        android.util.Log.d("NetWorthViewModel", "Auto-sync initial download: Success")
                        refreshAllSettingsFlows()
                    } else {
                        android.util.Log.d("NetWorthViewModel", "Auto-sync initial download skipped/no-op: ${downloadRes.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NetWorthViewModel", "Auto-sync initial download error", e)
                }

                try {
                    if (syncManager.hasDirtyData()) {
                        val uploadRes = syncManager.uploadLocalToCloud()
                        if (uploadRes.isSuccess) {
                            android.util.Log.d("NetWorthViewModel", "Auto-sync initial upload: Success")
                        } else {
                            android.util.Log.d("NetWorthViewModel", "Auto-sync initial upload skipped/no-op: ${uploadRes.exceptionOrNull()?.message}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NetWorthViewModel", "Auto-sync initial upload error", e)
                }
            }
        }
    }

    fun updateDriveState() {
        _isDriveSignedIn.value = syncManager.isUserSignedIn()
        _driveEmail.value = syncManager.getSignedInAccount()?.email ?: ""
    }

    init {
        updateDriveState()
        if (autoSyncDrive.value) {
            triggerInitialSync()
        }

        viewModelScope.launch {
            combine(
                repository.allCategories,
                repository.allAssets,
                repository.allBuckets,
                repository.allTransactions,
                repository.allSnapshots
            ) { _, _, _, _, _ -> }
                .drop(1)
                .debounce(5000)
                .collect {
                    if (autoSyncDrive.value && syncManager.isUserSignedIn() && syncManager.hasDirtyData()) {
                        android.util.Log.d("NetWorthViewModel", "Auto-sync database change detected. Uploading...")
                        try {
                            val uploadRes = syncManager.uploadLocalToCloud()
                            if (uploadRes.isSuccess) {
                                android.util.Log.d("NetWorthViewModel", "Auto-sync change upload: Success")
                            } else {
                                android.util.Log.d("NetWorthViewModel", "Auto-sync change upload skipped/failed: ${uploadRes.exceptionOrNull()?.message}")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("NetWorthViewModel", "Auto-sync change upload exception", e)
                        }
                    }
                }
        }
    }

    fun refreshAllSettingsFlows() {
        _categoryOrder.value = (prefs.getString("category_order", "") ?: "")
            .split(",")
            .mapNotNull { it.toIntOrNull() }
        _baseCurrency.value = prefs.getString("base_currency", "USD") ?: "USD"
        _valuesHidden.value = prefs.getBoolean("values_hidden", false)
        _themeIndex.value = prefs.getInt("theme_index", 0)
        _appFontScale.value = prefs.getFloat("app_font_scale", 1.0f)
        _appLockPin.value = prefs.getString("app_lock_pin", null)
        _useBiometrics.value = prefs.getBoolean("use_biometrics", false)
        _manualConversionMode.value = prefs.getBoolean("manual_conversion_mode", false)
        _spendingCategories.value = getInitialSpendingCategories()
        _spendingWalletIds.value = getInitialSpendingWalletIds()
        val defaultId = prefs.getInt("default_spending_wallet_id", -1)
        _defaultSpendingWalletId.value = if (defaultId == -1) null else defaultId
        val bucketId = prefs.getInt("selected_wallet_bucket_id", -1)
        _selectedWalletBucketId.value = if (bucketId == -1) null else bucketId
        _walletAliases.value = getInitialWalletAliases()
        _walletIcons.value = getInitialWalletIcons()
        _walletBudgets.value = getInitialWalletBudgets()
    }

    private val _categoryOrder = MutableStateFlow<List<Int>>(
        (prefs.getString("category_order", "") ?: "")
            .split(",")
            .mapNotNull { it.toIntOrNull() }
    )
    val categoryOrder: StateFlow<List<Int>> = _categoryOrder.asStateFlow()

    fun saveCategoryOrder(order: List<Int>) {
        _categoryOrder.value = order
        prefs.edit().putString("category_order", order.joinToString(",")).apply()
    }

    private val _baseCurrency = MutableStateFlow(prefs.getString("base_currency", "USD") ?: "USD")
    val baseCurrency: StateFlow<String> = _baseCurrency.asStateFlow()

    fun setBaseCurrency(currency: String) {
        _baseCurrency.value = currency
        prefs.edit().putString("base_currency", currency).apply()
    }

    private val _valuesHidden = MutableStateFlow(prefs.getBoolean("values_hidden", false))
    val valuesHidden: StateFlow<Boolean> = _valuesHidden.asStateFlow()

    fun setValuesHidden(hidden: Boolean) {
        _valuesHidden.value = hidden
        prefs.edit().putBoolean("values_hidden", hidden).apply()
    }

    private val _themeIndex = MutableStateFlow(prefs.getInt("theme_index", 0))
    val themeIndex: StateFlow<Int> = _themeIndex.asStateFlow()

    fun setThemeIndex(index: Int) {
        _themeIndex.value = index
        prefs.edit().putInt("theme_index", index).apply()
    }

    private val _appFontScale = MutableStateFlow(prefs.getFloat("app_font_scale", 1.0f))
    val appFontScale: StateFlow<Float> = _appFontScale.asStateFlow()

    fun setAppFontScale(scale: Float) {
        _appFontScale.value = scale
        prefs.edit().putFloat("app_font_scale", scale).apply()
    }

    private val _appLockPin = MutableStateFlow(prefs.getString("app_lock_pin", null))
    val appLockPin: StateFlow<String?> = _appLockPin.asStateFlow()

    fun setAppLockPin(pin: String?) {
        _appLockPin.value = pin
        if (pin == null) {
            prefs.edit().remove("app_lock_pin").apply()
        } else {
            prefs.edit().putString("app_lock_pin", pin).apply()
        }
    }

    private val _useBiometrics = MutableStateFlow(prefs.getBoolean("use_biometrics", false))
    val useBiometrics: StateFlow<Boolean> = _useBiometrics.asStateFlow()

    fun setUseBiometrics(use: Boolean) {
        _useBiometrics.value = use
        prefs.edit().putBoolean("use_biometrics", use).apply()
    }

    private val _excludedBucketIds = MutableStateFlow<Set<Int>>(
        prefs.getStringSet("excluded_bucket_ids", null)?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    )
    val excludedBucketIds: StateFlow<Set<Int>> = _excludedBucketIds.asStateFlow()

    fun setExcludedBucketIds(ids: Set<Int>) {
        _excludedBucketIds.value = ids
        prefs.edit().putStringSet("excluded_bucket_ids", ids.map { it.toString() }.toSet()).apply()
    }

    private fun getInitialBucketTargets(): Map<Int, Double> {
        val map = mutableMapOf<Int, Double>()
        val jsonStr = prefs.getString("bucket_target_allocations", null) ?: return map
        try {
            val obj = org.json.JSONObject(jsonStr)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key.toInt()] = obj.getDouble(key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    private val _bucketTargets = MutableStateFlow<Map<Int, Double>>(getInitialBucketTargets())
    val bucketTargets: StateFlow<Map<Int, Double>> = _bucketTargets.asStateFlow()

    fun saveBucketTarget(bucketId: Int, target: Double) {
        val current = _bucketTargets.value.toMutableMap()
        current[bucketId] = target
        _bucketTargets.value = current
        try {
            val obj = org.json.JSONObject()
            for ((k, v) in current) {
                obj.put(k.toString(), v)
            }
            prefs.edit().putString("bucket_target_allocations", obj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveAllBucketTargets(targets: Map<Int, Double>) {
        _bucketTargets.value = targets
        try {
            val obj = org.json.JSONObject()
            for ((k, v) in targets) {
                obj.put(k.toString(), v)
            }
            prefs.edit().putString("bucket_target_allocations", obj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val _rebalancingFrequency = MutableStateFlow<String>(prefs.getString("rebalancing_frequency", "Once a year") ?: "Once a year")
    val rebalancingFrequency: StateFlow<String> = _rebalancingFrequency.asStateFlow()

    fun saveRebalancingFrequency(freq: String) {
        _rebalancingFrequency.value = freq
        prefs.edit().putString("rebalancing_frequency", freq).apply()
    }

    private val _lastRebalancedTimestamp = MutableStateFlow<Long>(prefs.getLong("last_rebalanced_timestamp", 0L))
    val lastRebalancedTimestamp: StateFlow<Long> = _lastRebalancedTimestamp.asStateFlow()

    fun markAsRebalanced() {
        val now = System.currentTimeMillis()
        _lastRebalancedTimestamp.value = now
        prefs.edit().putLong("last_rebalanced_timestamp", now).apply()
    }

    // Dynamic Exchange rates management (Automatic vs Manual rates)
    val defaultRates = mapOf(
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

    private val _exchangeRates = MutableStateFlow<Map<String, Double>>(defaultRates)
    val exchangeRatesState: StateFlow<Map<String, Double>> = _exchangeRates.asStateFlow()

    val exchangeRates: Map<String, Double>
        get() = _exchangeRates.value

    private val _manualConversionMode = MutableStateFlow(prefs.getBoolean("manual_conversion_mode", false))
    val manualConversionMode: StateFlow<Boolean> = _manualConversionMode.asStateFlow()

    sealed interface SyncStatus {
        object Idle : SyncStatus
        object Loading : SyncStatus
        data class Success(val message: String) : SyncStatus
        data class Error(val error: String) : SyncStatus
    }

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    init {
        loadExchangeRates()
        // If automatic conversion mode, automatically trigger safe sync from internet in background
        if (!prefs.getBoolean("manual_conversion_mode", false)) {
            syncRatesFromInternet()
        }
    }

    fun loadExchangeRates() {
        val rates = defaultRates.toMutableMap()
        defaultRates.keys.forEach { currency ->
            if (currency != "USD") {
                val savedRate = prefs.getFloat("rate_$currency", 0.0f)
                if (savedRate > 0f) {
                    rates[currency] = savedRate.toDouble()
                }
            }
        }
        _exchangeRates.value = rates
    }

    fun setManualConversionMode(manual: Boolean) {
        _manualConversionMode.value = manual
        prefs.edit().putBoolean("manual_conversion_mode", manual).apply()
        if (!manual) {
            syncRatesFromInternet()
        }
    }

    fun setManualExchangeRate(currency: String, rate: Double) {
        prefs.edit().putFloat("rate_$currency", rate.toFloat()).apply()
        loadExchangeRates()
    }

    fun syncRatesFromInternet() {
        viewModelScope.launch {
            _syncStatus.value = SyncStatus.Loading
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val url = java.net.URL("https://open.er-api.com/v6/latest/USD")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000
                    
                    val responseCode = connection.responseCode
                    if (responseCode == 200) {
                        val text = connection.inputStream.bufferedReader().use { it.readText() }
                        val json = org.json.JSONObject(text)
                        if (json.optString("result") == "success") {
                            val ratesObj = json.getJSONObject("rates")
                            val updatedRates = defaultRates.toMutableMap()
                            
                            defaultRates.keys.forEach { currency ->
                                if (currency != "USD" && ratesObj.has(currency)) {
                                    val apiRate = ratesObj.getDouble(currency)
                                    if (apiRate > 0) {
                                        val rateValue = 1.0 / apiRate
                                        updatedRates[currency] = rateValue
                                        // Save to preferences
                                        prefs.edit().putFloat("rate_$currency", rateValue.toFloat()).apply()
                                    }
                                }
                            }
                            SyncStatus.Success("Synced successfully.")
                        } else {
                            SyncStatus.Error("API returned error result response.")
                        }
                    } else {
                        SyncStatus.Error("HTTP error status code: $responseCode")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    SyncStatus.Error(e.localizedMessage ?: "Unknown connection failure")
                }
            }
            _syncStatus.value = result
            if (result is SyncStatus.Success) {
                loadExchangeRates()
            }
        }
    }

    fun convertToUsd(amount: Double, currency: String, rates: Map<String, Double> = exchangeRates): Double {
        val rate = rates[currency] ?: 1.0
        return amount * rate
    }

    // Flows from DB
    val categories: StateFlow<List<AssetCategory>> = combine(
        repository.allCategories,
        categoryOrder
    ) { categoryList, order ->
        if (order.isEmpty()) {
            categoryList
        } else {
            categoryList.sortedBy { category ->
                val index = order.indexOf(category.id)
                if (index == -1) Int.MAX_VALUE else index
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val assets: StateFlow<List<Asset>> = repository.allAssets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val buckets: StateFlow<List<Bucket>> = repository.allBuckets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val snapshots: StateFlow<List<PortfolioSnapshot>> = repository.allSnapshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently selected timeline performance range: YTD, 1YR, 5YR, ALL, CST
    private val _selectedTimeline = MutableStateFlow("YTD")
    val selectedTimeline: StateFlow<String> = _selectedTimeline.asStateFlow()

    fun setTimeline(timeline: String) {
        _selectedTimeline.value = timeline
    }

    private val _customStartDate = MutableStateFlow(prefs.getLong("custom_start_date", System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000))
    val customStartDate: StateFlow<Long> = _customStartDate.asStateFlow()

    private val _customEndDate = MutableStateFlow(prefs.getLong("custom_end_date", System.currentTimeMillis()))
    val customEndDate: StateFlow<Long> = _customEndDate.asStateFlow()

    fun setCustomDateRange(start: Long, end: Long) {
        _customStartDate.value = start
        _customEndDate.value = end
        prefs.edit()
            .putLong("custom_start_date", start)
            .putLong("custom_end_date", end)
            .apply()
    }

    // Complex Flow to calculate each asset valuation and metrics dynamically
    val assetMetrics: StateFlow<List<AssetWithMetrics>> = combine(
        assets,
        categories,
        transactions,
        _selectedTimeline,
        _exchangeRates,
        _customStartDate,
        _customEndDate
    ) { array ->
        val assetList = array[0] as List<Asset>
        val categoryList = array[1] as List<AssetCategory>
        val txList = array[2] as List<com.example.data.model.Transaction>
        val timeline = array[3] as String
        val ratesMap = array[4] as Map<String, Double>
        val customStart = array[5] as Long
        val customEnd = array[6] as Long

        val referenceEndTime = if (timeline == "CST") customEnd else Long.MAX_VALUE

        assetList.map { asset ->
            val cat = categoryList.find { it.id == asset.categoryId }
            val catName = cat?.name ?: "Other"
            val isAssetType = cat?.isAsset ?: true

            // Gather txs for this asset, sorted chronologically with ID to break ties
            val relevantTxs = txList.filter { 
                (it.assetId == asset.id || it.destinationAssetId == asset.id) && it.timestamp <= referenceEndTime 
            }.sortedWith(compareBy<com.example.data.model.Transaction> { it.timestamp }.thenBy { it.id })

            // 1. Calculate Net Deposits (Cost basis)
            var netDepositsNative = 0.0
            var totalIncomeNative = 0.0

            val firstUpdate = relevantTxs.firstOrNull { it.type == "UPDATE" }
            val depositsBeforeUpdate = if (firstUpdate != null) {
                relevantTxs.filter { 
                    (it.timestamp < firstUpdate.timestamp || (it.timestamp == firstUpdate.timestamp && it.id < firstUpdate.id)) && (it.type == "DEPOSIT" || it.type == "TRANSFER") 
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
                    // Outflow from source asset
                    if (isAssetType) {
                        when (tx.type) {
                            "DEPOSIT" -> netDepositsNative += tx.amount
                            "WITHDRAWAL" -> netDepositsNative -= tx.amount
                            "INCOME" -> totalIncomeNative += tx.amount // dividend direct
                            "TRANSFER" -> netDepositsNative -= tx.amount // transfer out
                        }
                    } else {
                        // For liability (Credit Card):
                        // Spending (withdrawal / transfer out) INCREASES the debt
                        // Repayment (deposit / transfer in) / Refund (income) DECREASES the debt
                        when (tx.type) {
                            "DEPOSIT" -> netDepositsNative -= tx.amount
                            "WITHDRAWAL" -> netDepositsNative += tx.amount
                            "INCOME" -> netDepositsNative -= tx.amount
                            "TRANSFER" -> netDepositsNative += tx.amount
                        }
                    }
                } else if (tx.destinationAssetId == asset.id) {
                    // Inflow to destination asset
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

            // 2. Calculate Current Valuation based on latest "UPDATE" or rolling ledger logic
            val updateTxs = relevantTxs.filter { it.assetId == asset.id && it.type == "UPDATE" }
                .sortedWith(compareBy<com.example.data.model.Transaction> { it.timestamp }.thenBy { it.id })
            
            val latestUpdate = updateTxs.lastOrNull()
            val latestUpdateTimestamp = latestUpdate?.timestamp ?: 0L
            val baseValuation = latestUpdate?.amount ?: 0.0

            // Apply all non-update transactions occurring strictly after latestUpdate
            var additionsAfterUpdate = 0.0
            val transactionsAfterUpdate = if (latestUpdate != null) {
                relevantTxs.filter { tx -> tx.timestamp > latestUpdate.timestamp || (tx.timestamp == latestUpdate.timestamp && tx.id > latestUpdate.id) }
            } else {
                relevantTxs
            }

            for (tx in transactionsAfterUpdate) {
                if (tx.assetId == asset.id) {
                    if (isAssetType) {
                        when (tx.type) {
                            "DEPOSIT" -> additionsAfterUpdate += tx.amount
                            "WITHDRAWAL" -> additionsAfterUpdate -= tx.amount
                            "INCOME" -> {
                                // Divider/Interest income expands the native value of asset holdings
                                additionsAfterUpdate += tx.amount
                            }
                            "TRANSFER" -> additionsAfterUpdate -= tx.amount
                        }
                    } else {
                        // For liability (Credit Card):
                        // Spending (withdrawal / transfer out) INCREASES the debt
                        // Repayment (deposit / transfer in) / Refund (income) DECREASES the debt
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

            // If there was never an manual UPDATE point, the raw rolling sum of deposits is its baseline valuation
            val currentValNative = if (latestUpdate == null) {
                netDepositsNative + totalIncomeNative
            } else {
                baseValuation + additionsAfterUpdate
            }

            // 3. Resolve start time of the timeline is selected
            val now = System.currentTimeMillis()
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = now

            val startTimestamp = when (timeline) {
                "YTD" -> {
                    calendar.set(Calendar.MONTH, Calendar.JANUARY)
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    calendar.timeInMillis
                }
                "1YR" -> {
                    calendar.add(Calendar.YEAR, -1)
                    calendar.timeInMillis
                }
                "5YR" -> {
                    calendar.add(Calendar.YEAR, -5)
                    calendar.timeInMillis
                }
                "CST" -> customStart
                else -> 0L // ALL
            }

            // Calculate valuation, net deposits, income up to startTimestamp
            var startValNative = 0.0
            var startNetDepositsNative = 0.0
            var startIncomeNative = 0.0

            if (startTimestamp > 0L) {
                val relevantStartTxs = relevantTxs.filter { it.timestamp <= startTimestamp }

                val startFirstUpdate = relevantStartTxs.firstOrNull { it.type == "UPDATE" }
                val startDepositsBeforeUpdate = if (startFirstUpdate != null) {
                    relevantStartTxs.filter { 
                        (it.timestamp < startFirstUpdate.timestamp || (it.timestamp == startFirstUpdate.timestamp && it.id < startFirstUpdate.id)) && (it.type == "DEPOSIT" || it.type == "TRANSFER") 
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

                if (startFirstUpdate != null && startDepositsBeforeUpdate == 0.0) {
                    startNetDepositsNative += startFirstUpdate.amount
                }

                for (tx in relevantStartTxs) {
                    if (tx.assetId == asset.id) {
                        if (isAssetType) {
                            when (tx.type) {
                                "DEPOSIT" -> startNetDepositsNative += tx.amount
                                "WITHDRAWAL" -> startNetDepositsNative -= tx.amount
                                "INCOME" -> startIncomeNative += tx.amount
                                "TRANSFER" -> startNetDepositsNative -= tx.amount
                            }
                        } else {
                            when (tx.type) {
                                "DEPOSIT" -> startNetDepositsNative -= tx.amount
                                "WITHDRAWAL" -> startNetDepositsNative += tx.amount
                                "INCOME" -> startNetDepositsNative -= tx.amount
                                "TRANSFER" -> startNetDepositsNative += tx.amount
                            }
                        }
                    } else if (tx.destinationAssetId == asset.id) {
                        if (tx.type == "TRANSFER") {
                            val incomingAmount = tx.amount * (tx.exchangeRate ?: 1.0)
                            if (isAssetType) {
                                startNetDepositsNative += incomingAmount
                            } else {
                                startNetDepositsNative -= incomingAmount
                            }
                        }
                    }
                }

                val startUpdateTxs = relevantStartTxs.filter { it.assetId == asset.id && it.type == "UPDATE" }
                    .sortedWith(compareBy<com.example.data.model.Transaction> { it.timestamp }.thenBy { it.id })
                
                val startLatestUpdate = startUpdateTxs.lastOrNull()
                val startLatestUpdateTimestamp = startLatestUpdate?.timestamp ?: 0L
                val startBaseValuation = startLatestUpdate?.amount ?: 0.0

                var startAdditionsAfterUpdate = 0.0
                val startTransactionsAfterUpdate = if (startLatestUpdate != null) {
                    relevantStartTxs.filter { tx -> tx.timestamp > startLatestUpdate.timestamp || (tx.timestamp == startLatestUpdate.timestamp && tx.id > startLatestUpdate.id) }
                } else {
                    relevantStartTxs
                }

                for (tx in startTransactionsAfterUpdate) {
                    if (tx.assetId == asset.id) {
                        if (isAssetType) {
                            when (tx.type) {
                                "DEPOSIT" -> startAdditionsAfterUpdate += tx.amount
                                "WITHDRAWAL" -> startAdditionsAfterUpdate -= tx.amount
                                "INCOME" -> startAdditionsAfterUpdate += tx.amount
                                "TRANSFER" -> startAdditionsAfterUpdate -= tx.amount
                            }
                        } else {
                            when (tx.type) {
                                "DEPOSIT" -> startAdditionsAfterUpdate -= tx.amount
                                "WITHDRAWAL" -> startAdditionsAfterUpdate += tx.amount
                                "INCOME" -> startAdditionsAfterUpdate -= tx.amount
                                "TRANSFER" -> startAdditionsAfterUpdate += tx.amount
                            }
                        }
                    } else if (tx.destinationAssetId == asset.id) {
                        if (tx.type == "TRANSFER") {
                            val incomingAmount = tx.amount * (tx.exchangeRate ?: 1.0)
                            if (isAssetType) {
                                startAdditionsAfterUpdate += incomingAmount
                            } else {
                                startAdditionsAfterUpdate -= incomingAmount
                            }
                        }
                    }
                }

                startValNative = if (startLatestUpdate == null) {
                    startNetDepositsNative + startIncomeNative
                } else {
                    startBaseValuation + startAdditionsAfterUpdate
                }
            }

            // USD conversions
            val currentValUsd = convertToUsd(currentValNative, asset.currency, ratesMap)
            val netDepositsUsd = convertToUsd(netDepositsNative, asset.currency, ratesMap)
            val totalIncomeUsd = convertToUsd(totalIncomeNative, asset.currency, ratesMap)

            val gainLossUsd = if (!isAssetType) {
                0.0
            } else if (timeline == "ALL" || startTimestamp == 0L) {
                currentValUsd - netDepositsUsd
            } else {
                val startValUsd = convertToUsd(startValNative, asset.currency, ratesMap)
                val startNetDepositsUsd = convertToUsd(startNetDepositsNative, asset.currency, ratesMap)

                val gainLossUsdToday = currentValUsd - netDepositsUsd
                val gainLossUsdStart = startValUsd - startNetDepositsUsd
                gainLossUsdToday - gainLossUsdStart
            }

            val gainLossNative = if (!isAssetType) {
                0.0
            } else if (timeline == "ALL" || startTimestamp == 0L) {
                currentValNative - netDepositsNative
            } else {
                val gainLossNativeToday = currentValNative - netDepositsNative
                val gainLossNativeStart = startValNative - startNetDepositsNative
                gainLossNativeToday - gainLossNativeStart
            }

            val costBasisUsdPeriod = if (timeline == "ALL" || startTimestamp == 0L) {
                netDepositsUsd
            } else {
                val startValUsd = convertToUsd(startValNative, asset.currency, ratesMap)
                val netDepositsDuringPeriodUsd = convertToUsd(netDepositsNative - startNetDepositsNative, asset.currency, ratesMap)
                startValUsd + netDepositsDuringPeriodUsd
            }

            val gainLossPercent = if (!isAssetType) {
                0.0
            } else if (costBasisUsdPeriod > 0) {
                (gainLossUsd / costBasisUsdPeriod) * 100.0
            } else if (gainLossUsd > 0) {
                100.0
            } else {
                0.0
            }

            AssetWithMetrics(
                asset = asset,
                categoryName = catName,
                isAssetType = isAssetType,
                currentValuationNative = currentValNative,
                currentValuationUsd = currentValUsd,
                netDepositsNative = netDepositsNative,
                netDepositsUsd = netDepositsUsd,
                totalIncomeNative = totalIncomeNative,
                totalIncomeUsd = totalIncomeUsd,
                gainLossUsd = gainLossUsd,
                gainLossNative = gainLossNative,
                gainLossPercent = gainLossPercent,
                periodCostBasisUsd = costBasisUsdPeriod
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Group assets with their categories dynamically
    val categoryMetrics: StateFlow<List<CategoryWithMetrics>> = combine(
        categories,
        assetMetrics
    ) { categoryList, assetMetricsList ->
        categoryList.map { category ->
            val matchingAssets = assetMetricsList.filter { it.asset.categoryId == category.id && !it.asset.isArchived }
            val totalValUsd = matchingAssets.sumOf { if (category.isAsset) it.currentValuationUsd else -it.currentValuationUsd }
            val totalGainLossUsd = matchingAssets.sumOf { if (category.isAsset) it.gainLossUsd else -it.gainLossUsd }
            val totalPeriodCostBasis = matchingAssets.sumOf { it.periodCostBasisUsd }

            val totalGainLossPercent = if (totalPeriodCostBasis > 0) {
                (totalGainLossUsd / totalPeriodCostBasis) * 100.0
            } else if (totalGainLossUsd > 0) {
                100.0
            } else {
                0.0
            }

            CategoryWithMetrics(
                category = category,
                totalValuationUsd = if (category.isAsset) totalValUsd else -totalValUsd, // keep display positive
                totalGainLossUsd = totalGainLossUsd,
                totalGainLossPercent = totalGainLossPercent,
                assets = matchingAssets
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Group assets by buckets dynamically
    val bucketMetrics: StateFlow<List<BucketWithMetrics>> = combine(
        buckets,
        assetMetrics
    ) { bucketList, assetMetricsList ->
        val activeAssets = assetMetricsList.filter { !it.asset.isArchived }
        val assignedAndUnassigned = mutableListOf<BucketWithMetrics>()

        // 1. Assigned buckets
        bucketList.forEach { bucket ->
            val matchingAssets = activeAssets.filter { it.asset.bucketId == bucket.id }
            val totalValUsd = matchingAssets.sumOf { if (it.isAssetType) it.currentValuationUsd else -it.currentValuationUsd }
            val totalGainLossUsd = matchingAssets.sumOf { if (it.isAssetType) it.gainLossUsd else -it.gainLossUsd }
            val totalPeriodCostBasis = matchingAssets.sumOf { it.periodCostBasisUsd }

            val totalGainLossPercent = if (totalPeriodCostBasis > 0) {
                (totalGainLossUsd / totalPeriodCostBasis) * 100.0
            } else if (totalGainLossUsd > 0) {
                100.0
            } else {
                0.0
            }

            assignedAndUnassigned.add(
                BucketWithMetrics(
                    bucket = bucket,
                    totalValuationUsd = totalValUsd,
                    totalGainLossUsd = totalGainLossUsd,
                    totalGainLossPercent = totalGainLossPercent,
                    assets = matchingAssets
                )
            )
        }

        // 2. Unassigned bucket
        val unassignedAssets = activeAssets.filter { it.asset.bucketId == null || !bucketList.any { b -> b.id == it.asset.bucketId } }
        if (unassignedAssets.isNotEmpty()) {
            val totalValUsd = unassignedAssets.sumOf { if (it.isAssetType) it.currentValuationUsd else -it.currentValuationUsd }
            val totalGainLossUsd = unassignedAssets.sumOf { if (it.isAssetType) it.gainLossUsd else -it.gainLossUsd }
            val totalPeriodCostBasis = unassignedAssets.sumOf { it.periodCostBasisUsd }

            val totalGainLossPercent = if (totalPeriodCostBasis > 0) {
                (totalGainLossUsd / totalPeriodCostBasis) * 100.0
            } else if (totalGainLossUsd > 0) {
                100.0
            } else {
                0.0
            }

            assignedAndUnassigned.add(
                BucketWithMetrics(
                    bucket = null, // null represents Unassigned
                    totalValuationUsd = totalValUsd,
                    totalGainLossUsd = totalGainLossUsd,
                    totalGainLossPercent = totalGainLossPercent,
                    assets = unassignedAssets
                )
            )
        }

        assignedAndUnassigned
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun calculateNetWorthAt(
        targetTime: Long,
        assetList: List<Asset>,
        categoryList: List<AssetCategory>,
        txList: List<Transaction>
    ): Double {
        var stepAssetsUsd = 0.0
        var stepLiabilitiesUsd = 0.0

        for (asset in assetList) {
            if (!asset.includeInPortfolio || asset.isArchived) continue
            val cat = categoryList.find { it.id == asset.categoryId }
            val isAssetType = cat?.isAsset ?: true

            val assetTxsAtStep = txList.filter { 
                (it.assetId == asset.id || it.destinationAssetId == asset.id) && it.timestamp <= targetTime 
            }.sortedWith(compareBy<com.example.data.model.Transaction> { it.timestamp }.thenBy { it.id })

            if (assetTxsAtStep.isEmpty()) continue

            // Find Net Deposits up to targetTime
            var netDepositsAtStep = 0.0
            var incomeAtStep = 0.0

            val stepFirstUpdate = assetTxsAtStep.firstOrNull { it.type == "UPDATE" }
            val stepDepositsBeforeUpdate = if (stepFirstUpdate != null) {
                assetTxsAtStep.filter { 
                    (it.timestamp < stepFirstUpdate.timestamp || (it.timestamp == stepFirstUpdate.timestamp && it.id < stepFirstUpdate.id)) && (it.type == "DEPOSIT" || it.type == "TRANSFER") 
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

            if (stepFirstUpdate != null && stepDepositsBeforeUpdate == 0.0) {
                netDepositsAtStep += stepFirstUpdate.amount
            }

            for (tx in assetTxsAtStep) {
                if (tx.assetId == asset.id) {
                    if (isAssetType) {
                        when (tx.type) {
                            "DEPOSIT" -> netDepositsAtStep += tx.amount
                            "WITHDRAWAL" -> netDepositsAtStep -= tx.amount
                            "INCOME" -> incomeAtStep += tx.amount
                            "TRANSFER" -> netDepositsAtStep -= tx.amount
                        }
                    } else {
                        when (tx.type) {
                            "DEPOSIT" -> netDepositsAtStep -= tx.amount
                            "WITHDRAWAL" -> netDepositsAtStep += tx.amount
                            "INCOME" -> netDepositsAtStep -= tx.amount
                            "TRANSFER" -> netDepositsAtStep += tx.amount
                        }
                    }
                } else if (tx.destinationAssetId == asset.id) {
                    if (tx.type == "TRANSFER") {
                        val incoming = tx.amount * (tx.exchangeRate ?: 1.0)
                        if (isAssetType) {
                            netDepositsAtStep += incoming
                        } else {
                            netDepositsAtStep -= incoming
                        }
                    }
                }
            }

            // Find Valuation up to targetTime
            val updateTxsAtStep = assetTxsAtStep.filter { it.assetId == asset.id && it.type == "UPDATE" }
                .sortedWith(compareBy<com.example.data.model.Transaction> { it.timestamp }.thenBy { it.id })

            val stepLatestUpdate = updateTxsAtStep.lastOrNull()
            val stepLatestUpdateTimestamp = stepLatestUpdate?.timestamp ?: 0L
            val stepBaseValuation = stepLatestUpdate?.amount ?: 0.0

            var stepAdditionsAfterUpdate = 0.0
            val transactionsAfterStepUpdate = if (stepLatestUpdate != null) {
                assetTxsAtStep.filter { tx -> tx.timestamp > stepLatestUpdate.timestamp || (tx.timestamp == stepLatestUpdate.timestamp && tx.id > stepLatestUpdate.id) }
            } else {
                assetTxsAtStep
            }

            for (tx in transactionsAfterStepUpdate) {
                if (tx.assetId == asset.id) {
                    if (isAssetType) {
                        when (tx.type) {
                            "DEPOSIT" -> stepAdditionsAfterUpdate += tx.amount
                            "WITHDRAWAL" -> stepAdditionsAfterUpdate -= tx.amount
                            "INCOME" -> stepAdditionsAfterUpdate += tx.amount
                            "TRANSFER" -> stepAdditionsAfterUpdate -= tx.amount
                        }
                    } else {
                        when (tx.type) {
                            "DEPOSIT" -> stepAdditionsAfterUpdate -= tx.amount
                            "WITHDRAWAL" -> stepAdditionsAfterUpdate += tx.amount
                            "INCOME" -> stepAdditionsAfterUpdate -= tx.amount
                            "TRANSFER" -> stepAdditionsAfterUpdate += tx.amount
                        }
                    }
                } else if (tx.destinationAssetId == asset.id) {
                    if (tx.type == "TRANSFER") {
                        val incoming = tx.amount * (tx.exchangeRate ?: 1.0)
                        if (isAssetType) {
                            stepAdditionsAfterUpdate += incoming
                        } else {
                            stepAdditionsAfterUpdate -= incoming
                        }
                    }
                }
            }

            val currentValNativeAtStep = if (stepLatestUpdate == null) {
                netDepositsAtStep + incomeAtStep
            } else {
                stepBaseValuation + stepAdditionsAfterUpdate
            }

            val convertedUsd = convertToUsd(currentValNativeAtStep, asset.currency)
            if (isAssetType) {
                stepAssetsUsd += convertedUsd
            } else {
                stepLiabilitiesUsd += convertedUsd
            }
        }
        return stepAssetsUsd - stepLiabilitiesUsd
    }

    // Combined global wealth portfolio performance summary
    val portfolioSummary: StateFlow<PerformanceMetrics> = combine(
        assetMetrics,
        transactions,
        assets,
        categories
    ) { metricsList, txList, assetList, categoryList ->
        val activeMetrics = metricsList.filter { it.asset.includeInPortfolio && !it.asset.isArchived }
        val totalAssetsUsd = activeMetrics.filter { it.isAssetType }.sumOf { it.currentValuationUsd }
        val totalLiabilitiesUsd = activeMetrics.filter { !it.isAssetType }.sumOf { it.currentValuationUsd }
        val netWorthTotal = totalAssetsUsd - totalLiabilitiesUsd

        val totalNetDepositsRaw = activeMetrics.sumOf { if (it.isAssetType) it.netDepositsUsd else -it.netDepositsUsd }
        val assetToLiabilityTransfersUsd = txList.filter { tx ->
            tx.type == "TRANSFER" &&
            activeMetrics.any { it.asset.id == tx.assetId && it.isAssetType } &&
            activeMetrics.any { it.asset.id == tx.destinationAssetId && !it.isAssetType }
        }.sumOf { tx ->
            val asset = assetList.find { it.id == tx.assetId }
            if (asset != null) convertToUsd(tx.amount, asset.currency) else 0.0
        }
        val totalNetDeposits = totalNetDepositsRaw - assetToLiabilityTransfersUsd
        val totalIncome = activeMetrics.sumOf { it.totalIncomeUsd }
        
        val overallGain = netWorthTotal - totalNetDeposits
        val overallGainPercent = if (totalNetDeposits > 0) {
            (overallGain / totalNetDeposits) * 100.0
        } else {
            0.0
        }

        // Calculate YTD parameters
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.set(Calendar.MONTH, Calendar.JANUARY)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val ytdStart = calendar.timeInMillis

        val activeAssets = assetList.filter { it.includeInPortfolio && !it.isArchived }
        val startOfYearNetWorth = calculateNetWorthAt(ytdStart, activeAssets, categoryList, txList)

        var ytdNetDeposits = 0.0
        var ytdIncome = 0.0
        for (tx in txList) {
            if (tx.timestamp in ytdStart..now) {
                val asset = assetList.find { it.id == tx.assetId }
                val destAsset = assetList.find { it.id == tx.destinationAssetId }
                
                val isSourceIncluded = asset != null && asset.includeInPortfolio && !asset.isArchived
                val isDestIncluded = destAsset != null && destAsset.includeInPortfolio && !destAsset.isArchived

                if (tx.type == "TRANSFER") {
                    val sourceCat = if (asset != null) categoryList.find { it.id == asset.categoryId } else null
                    val isSourceAsset = sourceCat?.isAsset ?: true
                    val destCat = if (destAsset != null) categoryList.find { it.id == destAsset.categoryId } else null
                    val isDestAsset = destCat?.isAsset ?: true

                    if (isSourceIncluded && isDestIncluded && isSourceAsset && !isDestAsset) {
                        // Payment to liability: withdrawal from portfolio
                        val usd = convertToUsd(tx.amount, asset!!.currency)
                        ytdNetDeposits -= usd
                    } else if (isSourceIncluded && !isDestIncluded) {
                        // External transfer OUT (withdrawal of funds from portfolio)
                        val cat = categoryList.find { it.id == asset!!.categoryId }
                        val isAsset = cat?.isAsset ?: true
                        val usd = convertToUsd(tx.amount, asset.currency)
                        ytdNetDeposits -= if (isAsset) usd else -usd
                    } else if (!isSourceIncluded && isDestIncluded) {
                        // External transfer IN (deposit of funds to portfolio)
                        val cat = categoryList.find { it.id == destAsset!!.categoryId }
                        val isAsset = cat?.isAsset ?: true
                        val incomingAmount = tx.amount * (tx.exchangeRate ?: 1.0)
                        val usd = convertToUsd(incomingAmount, destAsset.currency)
                        ytdNetDeposits += if (isAsset) usd else -usd
                    }
                } else {
                    // Non-transfer transactions
                    if (isSourceIncluded) {
                        val cat = categoryList.find { it.id == asset!!.categoryId }
                        val isAsset = cat?.isAsset ?: true
                        
                        when (tx.type) {
                            "DEPOSIT" -> {
                                val usd = convertToUsd(tx.amount, asset.currency)
                                ytdNetDeposits += if (isAsset) usd else -usd
                            }
                            "WITHDRAWAL" -> {
                                val usd = convertToUsd(tx.amount, asset.currency)
                                ytdNetDeposits -= if (isAsset) usd else -usd
                            }
                            "INCOME" -> {
                                val usd = convertToUsd(tx.amount, asset.currency)
                                ytdIncome += usd
                            }
                        }
                    }
                }
            }
        }

        val ytdGainLossUsd = netWorthTotal - startOfYearNetWorth - ytdNetDeposits
        val ytdCostBasis = startOfYearNetWorth + ytdNetDeposits
        val ytdGainLossPercent = if (ytdCostBasis > 0) {
            (ytdGainLossUsd / ytdCostBasis) * 100.0
        } else if (ytdGainLossUsd > 0) {
            100.0
        } else {
            0.0
        }

        PerformanceMetrics(
            totalValuationUsd = netWorthTotal,
            totalGainLossUsd = overallGain,
            totalGainLossPercent = overallGainPercent,
            netDepositsUsd = totalNetDeposits,
            totalIncomeUsd = totalIncome,
            ytdGainLossUsd = ytdGainLossUsd,
            ytdGainLossPercent = ytdGainLossPercent,
            totalLiabilitiesUsd = totalLiabilitiesUsd
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PerformanceMetrics(0.0, 0.0, 0.0, 0.0, 0.0))

    // Computes dynamically standard performance points (points on chart) following selected timeline
    val historicalPerformancePoints: StateFlow<List<ChartPoint>> = combine(
        selectedTimeline,
        transactions,
        assets,
        categories,
        _exchangeRates,
        _customStartDate,
        _customEndDate
    ) { array ->
        val range = array[0] as String
        val txList = array[1] as List<com.example.data.model.Transaction>
        val assetList = array[2] as List<com.example.data.model.Asset>
        val categoryList = array[3] as List<com.example.data.model.AssetCategory>
        val rates = array[4] as Map<String, Double>
        val customStart = array[5] as Long
        val customEnd = array[6] as Long

        if (txList.isEmpty() || assetList.isEmpty()) return@combine emptyList()

        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)

        // Generate specific snapshot timestamps based on selected timeline range
        val targetTimes = when (range) {
            "YTD" -> {
                val list = mutableListOf<Long>()
                // Baseline: Jan 1 of current year at 00:00:00.000
                val baselineCal = Calendar.getInstance()
                baselineCal.set(Calendar.YEAR, currentYear)
                baselineCal.set(Calendar.MONTH, Calendar.JANUARY)
                baselineCal.set(Calendar.DAY_OF_MONTH, 1)
                baselineCal.set(Calendar.HOUR_OF_DAY, 0)
                baselineCal.set(Calendar.MINUTE, 0)
                baselineCal.set(Calendar.SECOND, 0)
                baselineCal.set(Calendar.MILLISECOND, 0)
                list.add(baselineCal.timeInMillis)

                for (m in 0..currentMonth) {
                    if (m < currentMonth) {
                        val cal = Calendar.getInstance()
                        cal.set(Calendar.YEAR, currentYear)
                        cal.set(Calendar.MONTH, m)
                        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                        cal.set(Calendar.HOUR_OF_DAY, 23)
                        cal.set(Calendar.MINUTE, 59)
                        cal.set(Calendar.SECOND, 59)
                        cal.set(Calendar.MILLISECOND, 999)
                        list.add(cal.timeInMillis)
                    } else {
                        list.add(now)
                    }
                }
                list.distinct().sorted()
            }
            "1YR" -> {
                val list = mutableListOf<Long>()
                val tempCal = Calendar.getInstance()
                tempCal.timeInMillis = now
                // Go back 11 months
                tempCal.add(Calendar.MONTH, -11)
                val firstYear = tempCal.get(Calendar.YEAR)
                val firstMonth = tempCal.get(Calendar.MONTH)

                // Baseline: 1st day of that starting month at 00:00:00.000
                val baselineCal = Calendar.getInstance()
                baselineCal.set(Calendar.YEAR, firstYear)
                baselineCal.set(Calendar.MONTH, firstMonth)
                baselineCal.set(Calendar.DAY_OF_MONTH, 1)
                baselineCal.set(Calendar.HOUR_OF_DAY, 0)
                baselineCal.set(Calendar.MINUTE, 0)
                baselineCal.set(Calendar.SECOND, 0)
                baselineCal.set(Calendar.MILLISECOND, 0)
                list.add(baselineCal.timeInMillis)

                val nowCal = Calendar.getInstance()
                nowCal.timeInMillis = now
                val nowYear = nowCal.get(Calendar.YEAR)
                val nowMonth = nowCal.get(Calendar.MONTH)

                for (i in 0..11) {
                    val targetMonthCal = Calendar.getInstance()
                    targetMonthCal.set(Calendar.YEAR, firstYear)
                    targetMonthCal.set(Calendar.MONTH, firstMonth)
                    targetMonthCal.add(Calendar.MONTH, i)

                    val itemYear = targetMonthCal.get(Calendar.YEAR)
                    val itemMonth = targetMonthCal.get(Calendar.MONTH)

                    if (itemYear == nowYear && itemMonth == nowMonth) {
                        list.add(now)
                    } else {
                        val cal = Calendar.getInstance()
                        cal.set(Calendar.YEAR, itemYear)
                        cal.set(Calendar.MONTH, itemMonth)
                        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                        cal.set(Calendar.HOUR_OF_DAY, 23)
                        cal.set(Calendar.MINUTE, 59)
                        cal.set(Calendar.SECOND, 59)
                        cal.set(Calendar.MILLISECOND, 999)
                        list.add(cal.timeInMillis)
                    }
                }
                list.distinct().sorted()
            }
            "5YR" -> {
                val list = mutableListOf<Long>()
                // Baseline: Jan 1 of the earliest year at 00:00:00.000
                val baselineCal = Calendar.getInstance()
                baselineCal.set(Calendar.YEAR, currentYear - 4)
                baselineCal.set(Calendar.MONTH, Calendar.JANUARY)
                baselineCal.set(Calendar.DAY_OF_MONTH, 1)
                baselineCal.set(Calendar.HOUR_OF_DAY, 0)
                baselineCal.set(Calendar.MINUTE, 0)
                baselineCal.set(Calendar.SECOND, 0)
                baselineCal.set(Calendar.MILLISECOND, 0)
                list.add(baselineCal.timeInMillis)

                for (yr in (currentYear - 4)..currentYear) {
                    if (yr < currentYear) {
                        val cal = Calendar.getInstance()
                        cal.set(Calendar.YEAR, yr)
                        cal.set(Calendar.MONTH, Calendar.DECEMBER)
                        cal.set(Calendar.DAY_OF_MONTH, 31)
                        cal.set(Calendar.HOUR_OF_DAY, 23)
                        cal.set(Calendar.MINUTE, 59)
                        cal.set(Calendar.SECOND, 59)
                        cal.set(Calendar.MILLISECOND, 999)
                        list.add(cal.timeInMillis)
                    } else {
                        list.add(now)
                    }
                }
                list.distinct().sorted()
            }
            "CST" -> {
                val list = mutableListOf<Long>()
                val duration = customEnd - customStart
                val stepSize = duration / 8
                for (i in 0..8) {
                    list.add(customStart + (i * stepSize))
                }
                list.distinct().sorted()
            }
            else -> {
                // ALL - One bar for each year from the oldest transaction year to current year
                val minTxTimestamp = txList.minOfOrNull { it.timestamp } ?: (now - 30L * 24 * 60 * 60 * 1000)
                val minCal = Calendar.getInstance()
                minCal.timeInMillis = minTxTimestamp
                val minYr = minCal.get(Calendar.YEAR)
                val startYr = if (minYr >= currentYear) currentYear - 1 else minYr

                val list = mutableListOf<Long>()
                // Baseline: Jan 1 of starting year at 00:00:00.000
                val baselineCal = Calendar.getInstance()
                baselineCal.set(Calendar.YEAR, startYr)
                baselineCal.set(Calendar.MONTH, Calendar.JANUARY)
                baselineCal.set(Calendar.DAY_OF_MONTH, 1)
                baselineCal.set(Calendar.HOUR_OF_DAY, 0)
                baselineCal.set(Calendar.MINUTE, 0)
                baselineCal.set(Calendar.SECOND, 0)
                baselineCal.set(Calendar.MILLISECOND, 0)
                list.add(baselineCal.timeInMillis)

                for (yr in startYr..currentYear) {
                    if (yr < currentYear) {
                        val cal = Calendar.getInstance()
                        cal.set(Calendar.YEAR, yr)
                        cal.set(Calendar.MONTH, Calendar.DECEMBER)
                        cal.set(Calendar.DAY_OF_MONTH, 31)
                        cal.set(Calendar.HOUR_OF_DAY, 23)
                        cal.set(Calendar.MINUTE, 59)
                        cal.set(Calendar.SECOND, 59)
                        cal.set(Calendar.MILLISECOND, 999)
                        list.add(cal.timeInMillis)
                    } else {
                        list.add(now)
                    }
                }
                list.distinct().sorted()
            }
        }

        val snapshotsList = mutableListOf<ChartPoint>()
        var prevNetWorth = 0.0

        for (idx in targetTimes.indices) {
            val targetTime = targetTimes[idx]
            // Calculate valuation of each asset AT THIS EXACT moment in history (targetTime)
            var stepAssetsUsd = 0.0
            var stepLiabilitiesUsd = 0.0

            for (asset in assetList) {
                if (!asset.includeInPortfolio || asset.isArchived) continue
                val cat = categoryList.find { it.id == asset.categoryId }
                val isAssetType = cat?.isAsset ?: true

                val assetTxsAtStep = txList.filter { 
                    (it.assetId == asset.id || it.destinationAssetId == asset.id) && it.timestamp <= targetTime 
                }.sortedWith(compareBy<com.example.data.model.Transaction> { it.timestamp }.thenBy { it.id })

                if (assetTxsAtStep.isEmpty()) continue

                // Find Net Deposits up to targetTime
                var netDepositsAtStep = 0.0
                var incomeAtStep = 0.0
                for (tx in assetTxsAtStep) {
                    if (tx.assetId == asset.id) {
                        if (isAssetType) {
                            when (tx.type) {
                                "DEPOSIT" -> netDepositsAtStep += tx.amount
                                "WITHDRAWAL" -> netDepositsAtStep -= tx.amount
                                "INCOME" -> incomeAtStep += tx.amount
                                "TRANSFER" -> netDepositsAtStep -= tx.amount
                            }
                        } else {
                            when (tx.type) {
                                "DEPOSIT" -> netDepositsAtStep -= tx.amount
                                "WITHDRAWAL" -> netDepositsAtStep += tx.amount
                                "INCOME" -> netDepositsAtStep -= tx.amount
                                "TRANSFER" -> netDepositsAtStep += tx.amount
                            }
                        }
                    } else if (tx.destinationAssetId == asset.id) {
                        if (tx.type == "TRANSFER") {
                            val incoming = tx.amount * (tx.exchangeRate ?: 1.0)
                            if (isAssetType) {
                                netDepositsAtStep += incoming
                            } else {
                                netDepositsAtStep -= incoming
                            }
                        }
                    }
                }

                // Find Valuation up to targetTime
                val updateTxsAtStep = assetTxsAtStep.filter { it.assetId == asset.id && it.type == "UPDATE" }
                    .sortedWith(compareBy<com.example.data.model.Transaction> { it.timestamp }.thenBy { it.id })

                val stepLatestUpdate = updateTxsAtStep.lastOrNull()
                val stepLatestUpdateTimestamp = stepLatestUpdate?.timestamp ?: 0L
                val stepBaseValuation = stepLatestUpdate?.amount ?: 0.0

                var stepAdditionsAfterUpdate = 0.0
                val transactionsAfterStepUpdate = if (stepLatestUpdate != null) {
                    assetTxsAtStep.filter { tx -> tx.timestamp > stepLatestUpdate.timestamp || (tx.timestamp == stepLatestUpdate.timestamp && tx.id > stepLatestUpdate.id) }
                } else {
                    assetTxsAtStep
                }

                for (tx in transactionsAfterStepUpdate) {
                    if (tx.assetId == asset.id) {
                        if (isAssetType) {
                            when (tx.type) {
                                "DEPOSIT" -> stepAdditionsAfterUpdate += tx.amount
                                "WITHDRAWAL" -> stepAdditionsAfterUpdate -= tx.amount
                                "INCOME" -> stepAdditionsAfterUpdate += tx.amount
                                "TRANSFER" -> stepAdditionsAfterUpdate -= tx.amount
                            }
                        } else {
                            when (tx.type) {
                                "DEPOSIT" -> stepAdditionsAfterUpdate -= tx.amount
                                "WITHDRAWAL" -> stepAdditionsAfterUpdate += tx.amount
                                "INCOME" -> stepAdditionsAfterUpdate -= tx.amount
                                "TRANSFER" -> stepAdditionsAfterUpdate += tx.amount
                            }
                        }
                    } else if (tx.destinationAssetId == asset.id) {
                        if (tx.type == "TRANSFER") {
                            val incoming = tx.amount * (tx.exchangeRate ?: 1.0)
                            if (isAssetType) {
                                stepAdditionsAfterUpdate += incoming
                            } else {
                                stepAdditionsAfterUpdate -= incoming
                            }
                        }
                    }
                }

                val currentValNativeAtStep = if (stepLatestUpdate == null) {
                    netDepositsAtStep + incomeAtStep
                } else {
                    stepBaseValuation + stepAdditionsAfterUpdate
                }

                val usdVal = convertToUsd(currentValNativeAtStep, asset.currency, rates)
                if (isAssetType) {
                    stepAssetsUsd += usdVal
                } else {
                    stepLiabilitiesUsd += usdVal
                }
            }

            val stepNetWorth = stepAssetsUsd - stepLiabilitiesUsd

            var intervalGain = 0.0
            if (idx > 0) {
                val intervalStart = targetTimes[idx - 1]
                val intervalEnd = targetTime
                var intervalNetDeposits = 0.0

                for (tx in txList) {
                    if (tx.timestamp > intervalStart && tx.timestamp <= intervalEnd) {
                        val asset = assetList.find { it.id == tx.assetId }
                        val destAsset = assetList.find { it.id == tx.destinationAssetId }

                        val isSourceIncluded = asset != null && asset.includeInPortfolio && !asset.isArchived
                        val isDestIncluded = destAsset != null && destAsset.includeInPortfolio && !destAsset.isArchived

                        if (tx.type == "TRANSFER") {
                            val sourceCat = if (asset != null) categoryList.find { it.id == asset.categoryId } else null
                            val isSourceAsset = sourceCat?.isAsset ?: true
                            val destCat = if (destAsset != null) categoryList.find { it.id == destAsset.categoryId } else null
                            val isDestAsset = destCat?.isAsset ?: true

                            if (isSourceIncluded && isDestIncluded && isSourceAsset && !isDestAsset) {
                                val usd = convertToUsd(tx.amount, asset!!.currency, rates)
                                intervalNetDeposits -= usd
                            } else if (isSourceIncluded && !isDestIncluded) {
                                // External transfer OUT (withdrawal of funds from portfolio)
                                val cat = categoryList.find { it.id == asset!!.categoryId }
                                val isAsset = cat?.isAsset ?: true
                                val usd = convertToUsd(tx.amount, asset.currency, rates)
                                intervalNetDeposits -= if (isAsset) usd else -usd
                            } else if (!isSourceIncluded && isDestIncluded) {
                                // External transfer IN (deposit of funds to portfolio)
                                val cat = categoryList.find { it.id == destAsset!!.categoryId }
                                val isAsset = cat?.isAsset ?: true
                                val incomingAmount = tx.amount * (tx.exchangeRate ?: 1.0)
                                val usd = convertToUsd(incomingAmount, destAsset.currency, rates)
                                intervalNetDeposits += if (isAsset) usd else -usd
                            }
                        } else {
                            // Non-transfer transactions
                            if (isSourceIncluded) {
                                val cat = categoryList.find { it.id == asset!!.categoryId }
                                val isAsset = cat?.isAsset ?: true

                                when (tx.type) {
                                    "DEPOSIT" -> {
                                        val usd = convertToUsd(tx.amount, asset.currency, rates)
                                        intervalNetDeposits += if (isAsset) usd else -usd
                                    }
                                    "WITHDRAWAL" -> {
                                        val usd = convertToUsd(tx.amount, asset.currency, rates)
                                        intervalNetDeposits -= if (isAsset) usd else -usd
                                    }
                                }
                            }
                        }
                    }
                }

                intervalGain = stepNetWorth - prevNetWorth - intervalNetDeposits
            }

            snapshotsList.add(
                ChartPoint(
                    timestamp = targetTime,
                    netWorthUsd = stepNetWorth,
                    totalAssetsUsd = stepAssetsUsd,
                    totalLiabilitiesUsd = stepLiabilitiesUsd,
                    intervalGainUsd = intervalGain
                )
            )
            prevNetWorth = stepNetWorth
        }

        snapshotsList
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // Category Actions
    fun addCategory(name: String, isAsset: Boolean) {
        viewModelScope.launch {
            val cat = AssetCategory(name = name, isAsset = isAsset)
            repository.insertCategory(cat)
        }
    }

    fun updateCategory(id: Int, name: String, isAsset: Boolean) {
        viewModelScope.launch {
            val cat = AssetCategory(id = id, name = name, isAsset = isAsset)
            repository.updateCategory(cat)
        }
    }

    fun deleteCategory(category: AssetCategory) {
        viewModelScope.launch {
            // Find all assets in this category first, delete them and their txs
            assets.value.filter { it.categoryId == category.id }.forEach { asset ->
                // Record transactions for this asset
                transactions.value.filter { it.assetId == asset.id || it.destinationAssetId == asset.id }.forEach { tx ->
                    syncManager.recordDeletion("Transaction", tx.id)
                }
                syncManager.recordDeletion("Asset", asset.id)
                repository.deleteAsset(asset)
            }
            syncManager.recordDeletion("Category", category.id)
            repository.deleteCategory(category)
        }
    }


    // Asset Actions
    fun addAsset(name: String, currency: String, categoryId: Int) {
        viewModelScope.launch {
            val asset = Asset(name = name, currency = currency, categoryId = categoryId)
            val assetId = repository.insertAsset(asset).toInt()
            val tx = Transaction(
                assetId = assetId,
                type = "UPDATE",
                amount = 0.0,
                timestamp = System.currentTimeMillis(),
                notes = "Initial Balance Setup"
            )
            repository.insertTransaction(tx)
        }
    }

    fun updateAsset(id: Int, name: String, currency: String, categoryId: Int) {
        viewModelScope.launch {
            val asset = Asset(id = id, name = name, currency = currency, categoryId = categoryId)
            repository.updateAsset(asset)
        }
    }

    fun deleteAsset(asset: Asset) {
        viewModelScope.launch {
            // Record deletions for all transactions associated with this asset
            transactions.value.filter { it.assetId == asset.id || it.destinationAssetId == asset.id }.forEach { tx ->
                syncManager.recordDeletion("Transaction", tx.id)
            }
            syncManager.recordDeletion("Asset", asset.id)
            repository.deleteAsset(asset)
        }
    }

    fun archiveAsset(asset: Asset) {
        viewModelScope.launch {
            val updated = asset.copy(isArchived = true)
            repository.updateAsset(updated)
        }
    }

    fun unarchiveAsset(asset: Asset) {
        viewModelScope.launch {
            val updated = asset.copy(isArchived = false)
            repository.updateAsset(updated)
        }
    }

    fun toggleIncludeInPortfolio(asset: Asset) {
        viewModelScope.launch {
            val updated = asset.copy(includeInPortfolio = !asset.includeInPortfolio)
            repository.updateAsset(updated)
        }
    }

    // Bucket Actions
    fun addBucket(
        name: String,
        description: String = "",
        targetAmount: Double = 0.0,
        isDecumulation: Boolean = false,
        yearlySpendBudget: Double = 0.0,
        bufferYears: Int = 5,
        warningThresholdPercent: Double = 20.0,
        targetGainPercent: Double = 6.0,
        lastYearPerformancePercent: Double = 0.0
    ) {
        viewModelScope.launch {
            val bucket = Bucket(
                name = name,
                description = description,
                targetAmount = targetAmount,
                isDecumulation = isDecumulation,
                yearlySpendBudget = yearlySpendBudget,
                bufferYears = bufferYears,
                warningThresholdPercent = warningThresholdPercent,
                targetGainPercent = targetGainPercent,
                lastYearPerformancePercent = lastYearPerformancePercent
            )
            repository.insertBucket(bucket)
        }
    }

    fun updateBucket(
        id: Int,
        name: String,
        description: String = "",
        targetAmount: Double = 0.0,
        isDecumulation: Boolean = false,
        yearlySpendBudget: Double = 0.0,
        bufferYears: Int = 5,
        warningThresholdPercent: Double = 20.0,
        targetGainPercent: Double = 6.0,
        lastYearPerformancePercent: Double = 0.0
    ) {
        viewModelScope.launch {
            val bucket = Bucket(
                id = id,
                name = name,
                description = description,
                targetAmount = targetAmount,
                isDecumulation = isDecumulation,
                yearlySpendBudget = yearlySpendBudget,
                bufferYears = bufferYears,
                warningThresholdPercent = warningThresholdPercent,
                targetGainPercent = targetGainPercent,
                lastYearPerformancePercent = lastYearPerformancePercent
            )
            repository.updateBucket(bucket)
        }
    }

    fun deleteBucket(bucket: Bucket) {
        viewModelScope.launch {
            syncManager.recordDeletion("Bucket", bucket.id)
            repository.deleteBucket(bucket)
        }
    }

    fun assignAssetToBucket(asset: Asset, bucketId: Int?) {
        viewModelScope.launch {
            val updated = asset.copy(bucketId = bucketId)
            repository.updateAsset(updated)
        }
    }


    // Dynamic Transaction Actions
    fun addTransaction(
        assetId: Int,
        type: String,
        amount: Double,
        destinationAssetId: Int? = null,
        exchangeRate: Double? = null,
        notes: String? = null,
        customTimestamp: Long? = null
    ) {
        viewModelScope.launch {
            val tx = Transaction(
                assetId = assetId,
                type = type,
                amount = amount,
                timestamp = customTimestamp ?: System.currentTimeMillis(),
                destinationAssetId = destinationAssetId,
                exchangeRate = exchangeRate,
                notes = notes
            )
            repository.insertTransaction(tx)
        }
    }

    fun updateTransaction(
        id: Int,
        assetId: Int,
        type: String,
        amount: Double,
        destinationAssetId: Int? = null,
        exchangeRate: Double? = null,
        notes: String? = null,
        timestamp: Long
    ) {
        viewModelScope.launch {
            val tx = Transaction(
                id = id,
                assetId = assetId,
                type = type,
                amount = amount,
                timestamp = timestamp,
                destinationAssetId = destinationAssetId,
                exchangeRate = exchangeRate,
                notes = notes
            )
            repository.updateTransaction(tx)
        }
    }

    fun deleteTransaction(tx: Transaction) {
        viewModelScope.launch {
            syncManager.recordDeletion("Transaction", tx.id)
            repository.deleteTransaction(tx)
        }
    }


    // WALLET SPENDING CONFIGURATIONS & CATEGORY MANAGEMENT
    
    // Default categories map helper
    private val DEFAULT_SPENDING_CATEGORIES = mapOf(
        "Food" to listOf("Groceries", "Restaurants", "Coffee"),
        "Transportation" to listOf("Public Transit", "Fuel", "Taxi/Ride-share"),
        "Utilities" to listOf("Electricity", "Water", "Internet", "Phone"),
        "Leisure" to listOf("Movies", "Hobby", "Shopping"),
        "Housing" to listOf("Rent/Mortgage", "Maintenance", "Furnishing"),
        "Other" to listOf("Uncategorized")
    )

    private val _spendingCategories = MutableStateFlow<Map<String, List<String>>>(getInitialSpendingCategories())
    val spendingCategories: StateFlow<Map<String, List<String>>> = _spendingCategories.asStateFlow()

    private fun getInitialSpendingCategories(): Map<String, List<String>> {
        val jsonStr = prefs.getString("spending_categories_json", null) ?: return DEFAULT_SPENDING_CATEGORIES
        return try {
            val json = org.json.JSONObject(jsonStr)
            val map = mutableMapOf<String, List<String>>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val arr = json.getJSONArray(key)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                map[key] = list
            }
            map
        } catch (e: Exception) {
            DEFAULT_SPENDING_CATEGORIES
        }
    }

    fun saveSpendingCategories(map: Map<String, List<String>>) {
        try {
            val json = org.json.JSONObject()
            map.forEach { (k, v) ->
                val arr = org.json.JSONArray()
                v.forEach { arr.put(it) }
                json.put(k, arr)
            }
            prefs.edit().putString("spending_categories_json", json.toString()).apply()
            _spendingCategories.value = map
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addSpendingCategory(category: String, subcategories: List<String> = listOf("General")) {
        val current = _spendingCategories.value.toMutableMap()
        current[category] = subcategories
        saveSpendingCategories(current)
    }

    fun deleteSpendingCategory(category: String) {
        val current = _spendingCategories.value.toMutableMap()
        current.remove(category)
        saveSpendingCategories(current)
    }

    fun updateSpendingCategory(oldCategory: String, newCategory: String, subcategories: List<String>) {
        val current = _spendingCategories.value.toMutableMap()
        current.remove(oldCategory)
        current[newCategory] = subcategories
        saveSpendingCategories(current)
    }

    // Selected Wallet Bucket ID (custom Networth Bucket mapped for Wallet spending mode)
    private val _selectedWalletBucketId = MutableStateFlow<Int?>(getInitialSelectedWalletBucketId())
    val selectedWalletBucketId: StateFlow<Int?> = _selectedWalletBucketId.asStateFlow()

    private fun getInitialSelectedWalletBucketId(): Int? {
        val id = prefs.getInt("selected_wallet_bucket_id", -1)
        return if (id == -1) null else id
    }

    fun setSelectedWalletBucketId(id: Int?) {
        _selectedWalletBucketId.value = id
        if (id == null) {
            prefs.edit().remove("selected_wallet_bucket_id").apply()
        } else {
            prefs.edit().putInt("selected_wallet_bucket_id", id).apply()
        }
    }

    // Active spending wallets asset ID list (up to 3 assigned)
    private val _spendingWalletIds = MutableStateFlow<List<Int>>(getInitialSpendingWalletIds())
    val spendingWalletIds: StateFlow<List<Int>> = _spendingWalletIds.asStateFlow()

    private fun getInitialSpendingWalletIds(): List<Int> {
        val str = prefs.getString("spending_wallet_ids", "") ?: ""
        if (str.isEmpty()) return emptyList()
        return str.split(",").mapNotNull { it.toIntOrNull() }
    }

    fun saveSpendingWalletIds(ids: List<Int>) {
        val limited = ids.take(5)
        prefs.edit().putString("spending_wallet_ids", limited.joinToString(",")).apply()
        _spendingWalletIds.value = limited
        
        // Ensure default wallet ID is still valid
        val currentDefault = _defaultSpendingWalletId.value
        if (currentDefault != null && currentDefault !in limited) {
            setDefaultSpendingWalletId(limited.firstOrNull())
        } else if (currentDefault == null && limited.isNotEmpty()) {
            setDefaultSpendingWalletId(limited.first())
        }
    }

    // Default Spending Wallet ID
    private val _defaultSpendingWalletId = MutableStateFlow<Int?>(getInitialDefaultSpendingWalletId())
    val defaultSpendingWalletId: StateFlow<Int?> = _defaultSpendingWalletId.asStateFlow()

    private fun getInitialDefaultSpendingWalletId(): Int? {
        val id = prefs.getInt("default_spending_wallet_id", -1)
        return if (id == -1) null else id
    }

    fun setDefaultSpendingWalletId(id: Int?) {
        _defaultSpendingWalletId.value = id
        if (id == null) {
            prefs.edit().remove("default_spending_wallet_id").apply()
        } else {
            prefs.edit().putInt("default_spending_wallet_id", id).apply()
        }
    }

    // Wallet aliases map (assetId -> alias string)
    private val _walletAliases = MutableStateFlow<Map<Int, String>>(getInitialWalletAliases())
    val walletAliases: StateFlow<Map<Int, String>> = _walletAliases.asStateFlow()

    private val _walletIcons = MutableStateFlow<Map<Int, String>>(getInitialWalletIcons())
    val walletIcons: StateFlow<Map<Int, String>> = _walletIcons.asStateFlow()

    private val _walletBudgets = MutableStateFlow<Map<Int, Double>>(getInitialWalletBudgets())
    val walletBudgets: StateFlow<Map<Int, Double>> = _walletBudgets.asStateFlow()

    private val _subcategoryBudgets = MutableStateFlow<Map<String, Double>>(getInitialSubcategoryBudgets())
    val subcategoryBudgets: StateFlow<Map<String, Double>> = _subcategoryBudgets.asStateFlow()

    private val _subcategoryBudgetCurrencies = MutableStateFlow<Map<String, String>>(getInitialSubcategoryBudgetCurrencies())
    val subcategoryBudgetCurrencies: StateFlow<Map<String, String>> = _subcategoryBudgetCurrencies.asStateFlow()

    fun convertCurrency(amount: Double, fromCurrency: String, toCurrency: String, rates: Map<String, Double> = exchangeRates): Double {
        if (fromCurrency == toCurrency) return amount
        val amountUsd = convertToUsd(amount, fromCurrency, rates)
        val toRate = rates[toCurrency] ?: 1.0
        return if (toRate > 0.0) amountUsd / toRate else amountUsd
    }

    private fun getInitialSubcategoryBudgets(): Map<String, Double> {
        val budgets = mutableMapOf<String, Double>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("subcat_budget_") && !key.startsWith("subcat_budget_curr_")) {
                val subcatKey = key.substringAfter("subcat_budget_")
                val budgetVal = (value as? Number)?.toDouble() ?: (value as? String)?.toDoubleOrNull()
                if (budgetVal != null) {
                    budgets[subcatKey] = budgetVal
                }
            }
        }
        return budgets
    }

    private fun getInitialSubcategoryBudgetCurrencies(): Map<String, String> {
        val currencies = mutableMapOf<String, String>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("subcat_budget_curr_")) {
                val subcatKey = key.substringAfter("subcat_budget_curr_")
                if (value is String) {
                    currencies[subcatKey] = value
                }
            }
        }
        return currencies
    }

    private fun getInitialWalletAliases(): Map<Int, String> {
        val aliases = mutableMapOf<Int, String>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("wallet_alias_")) {
                val assetId = key.substringAfter("wallet_alias_").toIntOrNull()
                if (assetId != null && value is String) {
                    aliases[assetId] = value
                }
            }
        }
        return aliases
    }

    private fun getInitialWalletIcons(): Map<Int, String> {
        val icons = mutableMapOf<Int, String>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("wallet_icon_")) {
                val assetId = key.substringAfter("wallet_icon_").toIntOrNull()
                if (assetId != null && value is String) {
                    icons[assetId] = value
                }
            }
        }
        return icons
    }

    private fun getInitialWalletBudgets(): Map<Int, Double> {
        val budgets = mutableMapOf<Int, Double>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("wallet_budget_")) {
                val assetId = key.substringAfter("wallet_budget_").toIntOrNull()
                val budgetVal = (value as? Number)?.toDouble() ?: (value as? String)?.toDoubleOrNull()
                if (assetId != null && budgetVal != null) {
                    budgets[assetId] = budgetVal
                }
            }
        }
        return budgets
    }

    fun saveWalletAlias(assetId: Int, alias: String) {
        if (alias.isBlank()) {
            prefs.edit().remove("wallet_alias_$assetId").apply()
        } else {
            prefs.edit().putString("wallet_alias_$assetId", alias).apply()
        }
        _walletAliases.value = getInitialWalletAliases()
    }

    fun saveWalletIcon(assetId: Int, icon: String) {
        if (icon.isBlank()) {
            prefs.edit().remove("wallet_icon_$assetId").apply()
        } else {
            prefs.edit().putString("wallet_icon_$assetId", icon).apply()
        }
        _walletIcons.value = getInitialWalletIcons()
    }

    fun saveWalletBudget(assetId: Int, budget: Double?) {
        if (budget == null || budget <= 0.0) {
            prefs.edit().remove("wallet_budget_$assetId").apply()
        } else {
            prefs.edit().putFloat("wallet_budget_$assetId", budget.toFloat()).apply()
        }
        _walletBudgets.value = getInitialWalletBudgets()
    }

    fun saveSubcategoryBudget(categoryName: String, subcategoryName: String, budget: Double?, currency: String? = null) {
        val key = "subcat_budget_${categoryName}_${subcategoryName}"
        val currKey = "subcat_budget_curr_${categoryName}_${subcategoryName}"
        if (budget == null || budget <= 0.0) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putFloat(key, budget.toFloat()).apply()
        }
        if (currency != null) {
            prefs.edit().putString(currKey, currency).apply()
        }
        _subcategoryBudgets.value = getInitialSubcategoryBudgets()
        _subcategoryBudgetCurrencies.value = getInitialSubcategoryBudgetCurrencies()
    }

    data class TransactionTemplate(
        val id: String,
        val name: String,
        val amount: Double,
        val mainCategory: String,
        val subcategory: String,
        val notes: String
    )

    private val _transactionTemplates = MutableStateFlow<List<TransactionTemplate>>(getInitialTransactionTemplates())
    val transactionTemplates: StateFlow<List<TransactionTemplate>> = _transactionTemplates.asStateFlow()

    private fun getInitialTransactionTemplates(): List<TransactionTemplate> {
        val list = mutableListOf<TransactionTemplate>()
        val jsonStr = prefs.getString("wallet_transaction_templates", null) ?: return list
        try {
            val arr = org.json.JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    TransactionTemplate(
                        id = o.optString("id", java.util.UUID.randomUUID().toString()),
                        name = o.optString("name", ""),
                        amount = o.optDouble("amount", 0.0),
                        mainCategory = o.optString("mainCategory", "Food"),
                        subcategory = o.optString("subcategory", "Groceries"),
                        notes = o.optString("notes", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveTransactionTemplate(template: TransactionTemplate) {
        val current = _transactionTemplates.value.toMutableList()
        val index = current.indexOfFirst { it.id == template.id }
        if (index >= 0) {
            current[index] = template
        } else {
            current.add(template)
        }
        persistTemplates(current)
    }

    fun deleteTransactionTemplate(templateId: String) {
        val current = _transactionTemplates.value.filter { it.id != templateId }
        persistTemplates(current)
    }

    private fun persistTemplates(list: List<TransactionTemplate>) {
        try {
            val arr = org.json.JSONArray()
            list.forEach { t ->
                val o = org.json.JSONObject()
                o.put("id", t.id)
                o.put("name", t.name)
                o.put("amount", t.amount)
                o.put("mainCategory", t.mainCategory)
                o.put("subcategory", t.subcategory)
                o.put("notes", t.notes)
                arr.put(o)
            }
            prefs.edit().putString("wallet_transaction_templates", arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _transactionTemplates.value = getInitialTransactionTemplates()
    }

    data class WalletNotification(
        val id: String,
        val title: String,
        val message: String,
        val timestamp: Long,
        val isRead: Boolean,
        val type: String, // "WARNING", "LIMIT_HIT"
        val month: String, // e.g. "2026-07"
        val category: String,
        val subcategory: String,
        val walletId: Int,
        val recurringExpenseId: String? = null
    )

    private val _notifications = MutableStateFlow<List<WalletNotification>>(getInitialNotifications())
    val notifications: StateFlow<List<WalletNotification>> = _notifications.asStateFlow()

    private fun getInitialNotifications(): List<WalletNotification> {
        val list = mutableListOf<WalletNotification>()
        val jsonStr = prefs.getString("wallet_notifications", null) ?: return list
        try {
            val arr = org.json.JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    WalletNotification(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        message = obj.getString("message"),
                        timestamp = obj.getLong("timestamp"),
                        isRead = obj.optBoolean("isRead", false),
                        type = obj.getString("type"),
                        month = obj.optString("month", ""),
                        category = obj.optString("category", ""),
                        subcategory = obj.optString("subcategory", ""),
                        walletId = obj.optInt("walletId", -1),
                        recurringExpenseId = if (obj.has("recurringExpenseId")) obj.optString("recurringExpenseId", null) else null
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.timestamp }
    }

    private fun persistNotifications(list: List<WalletNotification>) {
        try {
            val arr = org.json.JSONArray()
            for (item in list) {
                val obj = org.json.JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("message", item.message)
                    put("timestamp", item.timestamp)
                    put("isRead", item.isRead)
                    put("type", item.type)
                    put("month", item.month)
                    put("category", item.category)
                    put("subcategory", item.subcategory)
                    put("walletId", item.walletId)
                    if (item.recurringExpenseId != null) {
                        put("recurringExpenseId", item.recurringExpenseId)
                    }
                }
                arr.put(obj)
            }
            prefs.edit().putString("wallet_notifications", arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _notifications.value = getInitialNotifications()
    }

    fun markAllNotificationsAsRead() {
        val updated = _notifications.value.map { it.copy(isRead = true) }
        persistNotifications(updated)
    }

    fun deleteNotification(id: String) {
        val updated = _notifications.value.filter { it.id != id }
        persistNotifications(updated)
    }

    fun clearAllNotifications() {
        persistNotifications(emptyList())
    }

    fun triggerRebalancingNotification(message: String) {
        val currentNotifications = _notifications.value.toMutableList()
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val duplicateId = "rebalance_$todayStr"
        if (currentNotifications.any { it.id == duplicateId }) return

        val title = "Portfolio Rebalancing Alert ⚖️"
        val newNotif = WalletNotification(
            id = duplicateId,
            title = title,
            message = message,
            timestamp = System.currentTimeMillis(),
            isRead = false,
            type = "WARNING",
            month = todayStr.substring(0, 7),
            category = "Rebalancing",
            subcategory = "Portfolio",
            walletId = -1
        )
        currentNotifications.add(0, newNotif)
        persistNotifications(currentNotifications)
        triggerLocalSystemNotification(title, message)
    }

    private val CHANNEL_ID = "wallet_budget_notifications"

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Wallet Budget Alerts"
            val descriptionText = "Notifications for wallet monthly budget limits and warnings"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @Suppress("MissingPermission")
    fun triggerLocalSystemNotification(title: String, message: String) {
        try {
            createNotificationChannel()
            val context = getApplication<Application>()
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val id = (System.currentTimeMillis() % 100000).toInt()
            notificationManager.notify(id, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getStartOfMonthTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    data class ParsedSpendingVM(
        val mainCategory: String,
        val subcategory: String,
        val userNote: String
    )

    private fun parseSpendingNoteVM(note: String?): ParsedSpendingVM {
        if (note == null) return ParsedSpendingVM("Other", "General", "")
        val delimiterIndex = note.indexOf("|")
        val categorySection = if (delimiterIndex != -1) note.substring(0, delimiterIndex).trim() else note.trim()
        val userNote = if (delimiterIndex != -1) note.substring(delimiterIndex + 1).trim() else ""
        
        val catSplit = categorySection.split(">")
        if (catSplit.size >= 2) {
            return ParsedSpendingVM(catSplit[0].trim(), catSplit[1].trim(), userNote)
        } else if (catSplit.size == 1 && catSplit[0].isNotBlank()) {
            val word = catSplit[0].trim()
            if (word == "Food" || word == "Transportation" || word == "Utilities" || word == "Leisure" || word == "Housing") {
                return ParsedSpendingVM(word, "General", userNote)
            }
        }
        return ParsedSpendingVM("Other", "General", if (delimiterIndex == -1) note else userNote)
    }

    fun checkBudgetsAndGenerateNotifications() {
        val txs = transactions.value
        val budgets = subcategoryBudgets.value
        val budgetCurrencies = subcategoryBudgetCurrencies.value
        val walletIds = spendingWalletIds.value
        if (txs.isEmpty() || budgets.isEmpty() || walletIds.isEmpty()) return

        val currentMonthStart = getStartOfMonthTimestamp()
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val monthStr = String.format(Locale.US, "%04d-%02d", year, month)

        val rates = exchangeRates
        val spentMapGlobalInBudgetCurrency = mutableMapOf<String, Double>()

        txs.filter { tx ->
            tx.timestamp >= currentMonthStart && (
                tx.type == "WITHDRAWAL" || 
                tx.type == "EXPENSE" || 
                tx.type == "TRANSFER"
            )
        }.forEach { tx ->
            val parsed = parseSpendingNoteVM(tx.notes)
            val isTransferFromSpendingWallet = tx.type == "TRANSFER" && tx.assetId in walletIds
            val isExpenseOnSpendingWallet = (tx.type == "WITHDRAWAL" || tx.type == "EXPENSE") && tx.assetId in walletIds

            if (isExpenseOnSpendingWallet || isTransferFromSpendingWallet) {
                val category = parsed.mainCategory
                val subcategory = parsed.subcategory
                val subcatKey = "${category}_${subcategory}"

                if (budgets.containsKey(subcatKey)) {
                    val budgetCurr = budgetCurrencies[subcatKey] ?: baseCurrency.value
                    val sourceAsset = assets.value.find { it.id == tx.assetId }
                    val sourceCurrency = sourceAsset?.currency ?: baseCurrency.value

                    val txAmountInBudgetCurrency = convertCurrency(tx.amount, sourceCurrency, budgetCurr, rates)
                    spentMapGlobalInBudgetCurrency[subcatKey] = (spentMapGlobalInBudgetCurrency[subcatKey] ?: 0.0) + txAmountInBudgetCurrency
                }
            }
        }

        val currentNotifications = _notifications.value.toMutableList()
        var hasChanges = false

        val triggeredKeysStr = prefs.getString("wallet_triggered_budget_keys", "") ?: ""
        val triggeredKeys = triggeredKeysStr.split(",").filter { it.isNotEmpty() }.toMutableSet()

        budgets.forEach { (subcatKey, activeBudget) ->
            if (activeBudget <= 0.0) return@forEach
            val parts = subcatKey.split("_")
            val category = parts.getOrNull(0) ?: "Other"
            val subcategory = parts.getOrNull(1) ?: "General"

            val spentAmount = spentMapGlobalInBudgetCurrency[subcatKey] ?: 0.0
            val ratio = spentAmount / activeBudget
            val budgetCurr = budgetCurrencies[subcatKey] ?: baseCurrency.value

            val limitTriggerKey = "limit_${monthStr}_global_${category}_${subcategory}"
            val warningTriggerKey = "warning_${monthStr}_global_${category}_${subcategory}"

            if (ratio >= 1.0) {
                if (!triggeredKeys.contains(limitTriggerKey)) {
                    triggeredKeys.add(limitTriggerKey)

                    val title = "Budget Limit Exceeded 🚨"
                    val message = "Your spent on $subcategory ($category) globally has reached ${String.format(Locale.US, "%.1f", spentAmount)} $budgetCurr, exceeding your budget limit of ${String.format(Locale.US, "%.1f", activeBudget)} $budgetCurr."

                    val newNotif = WalletNotification(
                        id = "limit_${System.currentTimeMillis()}_global_${category}_${subcategory}",
                        title = title,
                        message = message,
                        timestamp = System.currentTimeMillis(),
                        isRead = false,
                        type = "LIMIT_HIT",
                        month = monthStr,
                        category = category,
                        subcategory = subcategory,
                        walletId = -1
                    )
                    currentNotifications.add(0, newNotif)
                    hasChanges = true

                    triggerLocalSystemNotification(title, message)
                }
            } else if (ratio >= 0.9) {
                if (!triggeredKeys.contains(warningTriggerKey) && !triggeredKeys.contains(limitTriggerKey)) {
                    triggeredKeys.add(warningTriggerKey)

                    val title = "Budget Warning ⚠️"
                    val message = "Your spent on $subcategory ($category) globally is at ${String.format(Locale.US, "%.1f", spentAmount)} $budgetCurr, which is over 90% of your budget limit (${String.format(Locale.US, "%.1f", activeBudget)} $budgetCurr)."

                    val newNotif = WalletNotification(
                        id = "warning_${System.currentTimeMillis()}_global_${category}_${subcategory}",
                        title = title,
                        message = message,
                        timestamp = System.currentTimeMillis(),
                        isRead = false,
                        type = "WARNING",
                        month = monthStr,
                        category = category,
                        subcategory = subcategory,
                        walletId = -1
                    )
                    currentNotifications.add(0, newNotif)
                    hasChanges = true

                    triggerLocalSystemNotification(title, message)
                }
            }
        }

        if (hasChanges) {
            prefs.edit().putString("wallet_triggered_budget_keys", triggeredKeys.joinToString(",")).apply()
            persistNotifications(currentNotifications)
        }
    }

    data class RecurringExpense(
        val id: String,
        val name: String,
        val amount: Double,
        val walletId: Int,
        val mainCategory: String,
        val subcategory: String,
        val frequency: String, // "Daily", "Weekly", "Monthly", "Yearly"
        val notes: String,
        val lastPostedTimestamp: Long,
        val isEnabled: Boolean = true,
        val startDateTimestamp: Long = System.currentTimeMillis(),
        val endDateTimestamp: Long? = null
    )

    private val _recurringExpenses = MutableStateFlow<List<RecurringExpense>>(getInitialRecurringExpenses())
    val recurringExpenses: StateFlow<List<RecurringExpense>> = _recurringExpenses.asStateFlow()

    private fun getInitialRecurringExpenses(): List<RecurringExpense> {
        val list = mutableListOf<RecurringExpense>()
        val jsonStr = prefs.getString("wallet_recurring_expenses", null) ?: return list
        try {
            val arr = org.json.JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val endVal = if (o.has("endDateTimestamp") && !o.isNull("endDateTimestamp")) {
                    val v = o.optLong("endDateTimestamp", -1L)
                    if (v == -1L) null else v
                } else null

                list.add(
                    RecurringExpense(
                        id = o.optString("id", java.util.UUID.randomUUID().toString()),
                        name = o.optString("name", ""),
                        amount = o.optDouble("amount", 0.0),
                        walletId = o.optInt("walletId", -1),
                        mainCategory = o.optString("mainCategory", "Food"),
                        subcategory = o.optString("subcategory", "Groceries"),
                        frequency = o.optString("frequency", "Monthly"),
                        notes = o.optString("notes", ""),
                        lastPostedTimestamp = o.optLong("lastPostedTimestamp", 0L),
                        isEnabled = o.optBoolean("isEnabled", true),
                        startDateTimestamp = o.optLong("startDateTimestamp", System.currentTimeMillis()),
                        endDateTimestamp = endVal
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveRecurringExpense(expense: RecurringExpense) {
        val current = _recurringExpenses.value.toMutableList()
        val index = current.indexOfFirst { it.id == expense.id }
        if (index >= 0) {
            current[index] = expense
        } else {
            current.add(expense)
        }
        persistRecurringExpenses(current)
    }

    fun deleteRecurringExpense(id: String) {
        val current = _recurringExpenses.value.filter { it.id != id }
        persistRecurringExpenses(current)
    }

    private fun persistRecurringExpenses(list: List<RecurringExpense>) {
        try {
            val arr = org.json.JSONArray()
            list.forEach { t ->
                val o = org.json.JSONObject()
                o.put("id", t.id)
                o.put("name", t.name)
                o.put("amount", t.amount)
                o.put("walletId", t.walletId)
                o.put("mainCategory", t.mainCategory)
                o.put("subcategory", t.subcategory)
                o.put("frequency", t.frequency)
                o.put("notes", t.notes)
                o.put("lastPostedTimestamp", t.lastPostedTimestamp)
                o.put("isEnabled", t.isEnabled)
                o.put("startDateTimestamp", t.startDateTimestamp)
                if (t.endDateTimestamp != null) {
                    o.put("endDateTimestamp", t.endDateTimestamp)
                } else {
                    o.put("endDateTimestamp", org.json.JSONObject.NULL)
                }
                arr.put(o)
            }
            prefs.edit().putString("wallet_recurring_expenses", arr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _recurringExpenses.value = getInitialRecurringExpenses()
        checkRecurringExpensesAndGenerateNotifications()
    }

    fun checkRecurringExpensesAndGenerateNotifications() {
        val expenses = _recurringExpenses.value
        val now = System.currentTimeMillis()
        val currentNotifications = _notifications.value.toMutableList()
        var hasChanges = false

        expenses.filter { it.isEnabled }.forEach { exp ->
            // Date bounds filtering
            if (now < exp.startDateTimestamp) return@forEach
            if (exp.endDateTimestamp != null && now > exp.endDateTimestamp) return@forEach

            val due = if (exp.lastPostedTimestamp == 0L) {
                true
            } else {
                val elapsed = now - exp.lastPostedTimestamp
                when (exp.frequency) {
                    "Daily" -> elapsed >= 24 * 60 * 60 * 1000L
                    "Weekly" -> elapsed >= 7 * 24 * 60 * 60 * 1000L
                    "Monthly" -> elapsed >= 30 * 24 * 60 * 60 * 1000L
                    "Yearly" -> elapsed >= 365 * 24 * 60 * 60 * 1000L
                    else -> elapsed >= 30 * 24 * 60 * 60 * 1000L
                }
            }

            if (due) {
                // Check if there is already an unread/pending notification for this recurring expense in notifications
                val alreadyNotified = currentNotifications.any {
                    it.type == "RECURRING_DUE" && it.recurringExpenseId == exp.id && !it.isRead
                }

                if (!alreadyNotified) {
                    val walletAsset = assets.value.find { it.id == exp.walletId }
                    val walletNameResolved = walletAsset?.name ?: "Wallet"
                    val walletCurrency = walletAsset?.currency ?: "USD"

                    val title = "Recurring Expense Due ⏰"
                    val message = "Your recurring expense '${exp.name}' of ${String.format(Locale.US, "%.2f", exp.amount)} $walletCurrency is due on wallet '$walletNameResolved'."

                    val newNotif = WalletNotification(
                        id = "recurring_${System.currentTimeMillis()}_${exp.id}",
                        title = title,
                        message = message,
                        timestamp = System.currentTimeMillis(),
                        isRead = false,
                        type = "RECURRING_DUE",
                        month = "",
                        category = exp.mainCategory,
                        subcategory = exp.subcategory,
                        walletId = exp.walletId,
                        recurringExpenseId = exp.id
                    )
                    currentNotifications.add(0, newNotif)
                    hasChanges = true

                    triggerLocalSystemNotification(title, message)
                }
            }
        }

        if (hasChanges) {
            persistNotifications(currentNotifications)
        }
    }

    fun postRecurringExpense(notifId: String, expenseId: String) {
        val expense = _recurringExpenses.value.find { it.id == expenseId } ?: return
        
        val formattedNotes = if (expense.notes.isNotBlank()) {
            "${expense.mainCategory} > ${expense.subcategory} | ${expense.notes}"
        } else {
            "${expense.mainCategory} > ${expense.subcategory}"
        }
        
        addTransaction(
            assetId = expense.walletId,
            type = "EXPENSE",
            amount = expense.amount,
            destinationAssetId = null,
            exchangeRate = null,
            notes = formattedNotes,
            customTimestamp = System.currentTimeMillis()
        )

        val updatedExpense = expense.copy(lastPostedTimestamp = System.currentTimeMillis())
        saveRecurringExpense(updatedExpense)
        deleteNotification(notifId)
    }


    // Global Clear and Demo Seed logic
    fun clearAllData() {
        viewModelScope.launch {
            repository.clearSnapshots()
            // clear all transactions
            transactions.value.forEach { repository.deleteTransaction(it) }
            // clear assets
            assets.value.forEach { repository.deleteAsset(it) }
            // clear categories
            categories.value.forEach { repository.deleteCategory(it) }
            // clear buckets
            buckets.value.forEach { repository.deleteBucket(it) }

            // clear wallet entries
            saveSpendingWalletIds(emptyList())
            setDefaultSpendingWalletId(null)
            prefs.all.keys.filter { it.startsWith("wallet_alias_") || it.startsWith("wallet_icon_") || it.startsWith("wallet_budget_") }.forEach { k ->
                prefs.edit().remove(k).apply()
            }
            _walletAliases.value = emptyMap()
            _walletIcons.value = emptyMap()
            _walletBudgets.value = emptyMap()
            saveSpendingCategories(DEFAULT_SPENDING_CATEGORIES)
        }
    }

    fun exportDatabaseToJson(): String {
        return try {
            val root = org.json.JSONObject()
            
            val catsArray = org.json.JSONArray()
            categories.value.forEach { cat ->
                val o = org.json.JSONObject()
                o.put("id", cat.id)
                o.put("name", cat.name)
                o.put("isAsset", cat.isAsset)
                catsArray.put(o)
            }
            root.put("categories", catsArray)

            val bucketsArray = org.json.JSONArray()
            buckets.value.forEach { b ->
                val o = org.json.JSONObject()
                o.put("id", b.id)
                o.put("name", b.name)
                o.put("description", b.description)
                o.put("targetAmount", b.targetAmount)
                o.put("isDecumulation", b.isDecumulation)
                o.put("yearlySpendBudget", b.yearlySpendBudget)
                o.put("bufferYears", b.bufferYears)
                o.put("warningThresholdPercent", b.warningThresholdPercent)
                o.put("targetGainPercent", b.targetGainPercent)
                o.put("lastYearPerformancePercent", b.lastYearPerformancePercent)
                bucketsArray.put(o)
            }
            root.put("buckets", bucketsArray)

            val assetsArray = org.json.JSONArray()
            assets.value.forEach { a ->
                val o = org.json.JSONObject()
                o.put("id", a.id)
                o.put("name", a.name)
                o.put("currency", a.currency)
                o.put("categoryId", a.categoryId)
                o.put("currentValuation", a.currentValuation)
                o.put("isArchived", a.isArchived)
                o.put("includeInPortfolio", a.includeInPortfolio)
                o.put("bucketId", a.bucketId ?: org.json.JSONObject.NULL)
                assetsArray.put(o)
            }
            root.put("assets", assetsArray)

            val txsArray = org.json.JSONArray()
            transactions.value.forEach { tx ->
                val o = org.json.JSONObject()
                o.put("id", tx.id)
                o.put("assetId", tx.assetId)
                o.put("type", tx.type)
                o.put("amount", tx.amount)
                o.put("timestamp", tx.timestamp)
                o.put("destinationAssetId", tx.destinationAssetId ?: org.json.JSONObject.NULL)
                o.put("exchangeRate", tx.exchangeRate ?: org.json.JSONObject.NULL)
                o.put("notes", tx.notes ?: org.json.JSONObject.NULL)
                txsArray.put(o)
            }
            root.put("transactions", txsArray)

            // 5. Settings Configuration
            val settingsObj = org.json.JSONObject()
            settingsObj.put("base_currency", _baseCurrency.value)
            settingsObj.put("values_hidden", _valuesHidden.value)
            settingsObj.put("theme_index", _themeIndex.value)
            settingsObj.put("app_font_scale", _appFontScale.value)
            settingsObj.put("app_lock_pin", _appLockPin.value ?: org.json.JSONObject.NULL)

            // Include spending budget categories 
            val spendingCatsObj = org.json.JSONObject()
            _spendingCategories.value.forEach { (catName, subCats) ->
                val arr = org.json.JSONArray()
                subCats.forEach { arr.put(it) }
                spendingCatsObj.put(catName, arr)
            }
            settingsObj.put("spending_categories", spendingCatsObj)

            // Include assigned spending wallet ids
            val spendingWalletsArr = org.json.JSONArray()
            _spendingWalletIds.value.forEach { spendingWalletsArr.put(it) }
            settingsObj.put("spending_wallet_ids", spendingWalletsArr)

            // Include default spending wallet id
            settingsObj.put("default_spending_wallet_id", _defaultSpendingWalletId.value ?: org.json.JSONObject.NULL)

            // Include selected wallet bucket id
            settingsObj.put("selected_wallet_bucket_id", _selectedWalletBucketId.value ?: org.json.JSONObject.NULL)

            // Include wallet nicknames/aliases
            val aliasesObj = org.json.JSONObject()
            _walletAliases.value.forEach { (assetId, alias) ->
                aliasesObj.put(assetId.toString(), alias)
            }
            settingsObj.put("wallet_aliases", aliasesObj)

            // Include wallet icons
            val iconsObj = org.json.JSONObject()
            _walletIcons.value.forEach { (assetId, icon) ->
                iconsObj.put(assetId.toString(), icon)
            }
            settingsObj.put("wallet_icons", iconsObj)

            // Include wallet budgets
            val budgetsObj = org.json.JSONObject()
            _walletBudgets.value.forEach { (assetId, budget) ->
                budgetsObj.put(assetId.toString(), budget)
            }
            settingsObj.put("wallet_budgets", budgetsObj)

            root.put("settings", settingsObj)

            root.toString(2)
        } catch (e: Exception) {
            "{}"
        }
    }

    fun exportTransactionsToCsv(): String {
        val sb = StringBuilder()
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        val exportDate = dateFormat.format(java.util.Date())
        val baseCurr = baseCurrency.value
        val rates = exchangeRates

        fun escapeCsv(value: Any?): String {
            val str = value?.toString() ?: ""
            val clean = str.replace("\"", "\"\"")
            return if (clean.contains(",") || clean.contains("\n") || clean.contains("\"")) {
                "\"$clean\""
            } else {
                clean
            }
        }

        fun convertNativeToUsd(amount: Double, currency: String): Double {
            val rate = rates[currency] ?: 1.0
            return amount * rate
        }

        fun convertUsdToBase(amountUsd: Double): Double {
            val rate = rates[baseCurr] ?: 1.0
            return if (rate > 0.0) amountUsd / rate else amountUsd
        }

        // 1. METADATA & SUMMARY
        sb.append("# === FINANCIAL REPORT & DATABASE EXPORT ===\n")
        sb.append("Export Date,${escapeCsv(exportDate)}\n")
        sb.append("Base Currency,${escapeCsv(baseCurr)}\n")
        sb.append("Total Categories,${categories.value.size}\n")
        sb.append("Total Assets,${assets.value.size}\n")
        sb.append("Total Transactions,${transactions.value.size}\n")
        sb.append("Total Buckets,${buckets.value.size}\n")
        sb.append("Total Snapshots,${snapshots.value.size}\n")
        sb.append("\n")

        // 2. TRANSACTIONS
        sb.append("# === SECTION: TRANSACTIONS ===\n")
        sb.append("Transaction ID,Date & Time,Timestamp (ms),Asset Name,Asset Currency,Asset Category,Flow Nature,Type,Amount (Native),Amount (USD),Amount (${baseCurr}),Destination Asset,Destination Currency,Destination Category,Exchange Rate,Parsed Expense Category,Parsed Expense Subcategory,Notes\n")
        
        transactions.value.sortedWith(compareByDescending<com.example.data.model.Transaction> { it.timestamp }.thenByDescending { it.id }).forEach { tx ->
            val asset = assets.value.find { it.id == tx.assetId }
            val assetName = asset?.name ?: "Unknown Asset"
            val currency = asset?.currency ?: ""
            val category = categories.value.find { it.id == asset?.categoryId ?: -1 }
            val categoryName = category?.name ?: ""
            val flowNature = if (category != null) (if (category.isAsset) "Asset" else "Liability") else ""
            
            val amtUsd = convertNativeToUsd(tx.amount, currency)
            val amtBase = convertUsdToBase(amtUsd)

            val destAsset = tx.destinationAssetId?.let { dId -> assets.value.find { it.id == dId } }
            val destAssetName = destAsset?.name ?: ""
            val destCurrency = destAsset?.currency ?: ""
            val destCategory = destAsset?.let { da -> categories.value.find { it.id == da.categoryId } }?.name ?: ""

            val parsedParts = if (tx.notes != null && tx.notes.contains("|")) {
                val tokens = tx.notes.split("|").map { it.trim() }
                val pCat = tokens.getOrNull(0) ?: "Other"
                val pSub = tokens.getOrNull(1) ?: "General"
                val pNote = tokens.getOrNull(2) ?: ""
                Triple(pCat, pSub, pNote)
            } else {
                Triple("", "", tx.notes ?: "")
            }

            sb.append("${tx.id},")
            sb.append("${dateFormat.format(java.util.Date(tx.timestamp))},")
            sb.append("${tx.timestamp},")
            sb.append("${escapeCsv(assetName)},")
            sb.append("${escapeCsv(currency)},")
            sb.append("${escapeCsv(categoryName)},")
            sb.append("${escapeCsv(flowNature)},")
            sb.append("${tx.type},")
            sb.append("${tx.amount},")
            sb.append("${amtUsd},")
            sb.append("${amtBase},")
            sb.append("${escapeCsv(destAssetName)},")
            sb.append("${escapeCsv(destCurrency)},")
            sb.append("${escapeCsv(destCategory)},")
            sb.append("${tx.exchangeRate ?: ""},")
            sb.append("${escapeCsv(parsedParts.first)},")
            sb.append("${escapeCsv(parsedParts.second)},")
            sb.append("${escapeCsv(parsedParts.third)}\n")
        }
        sb.append("\n")

        // 3. ASSETS
        sb.append("# === SECTION: ASSETS ===\n")
        sb.append("Asset ID,Name,Currency,Category Name,Flow Nature,Current Valuation (Native),Current Valuation (USD),Current Valuation (${baseCurr}),Archived Status,Include in Portfolio,Bucket Name,Last Updated\n")
        
        assets.value.sortedBy { it.id }.forEach { asset ->
            val category = categories.value.find { it.id == asset.categoryId }
            val categoryName = category?.name ?: ""
            val flowNature = if (category != null) (if (category.isAsset) "Asset" else "Liability") else ""
            
            val valUsd = convertNativeToUsd(asset.currentValuation, asset.currency)
            val valBase = convertUsdToBase(valUsd)

            val bucketName = asset.bucketId?.let { bId -> buckets.value.find { it.id == bId }?.name } ?: ""
            val formattedLastUpdated = dateFormat.format(java.util.Date(asset.lastUpdated))

            sb.append("${asset.id},")
            sb.append("${escapeCsv(asset.name)},")
            sb.append("${escapeCsv(asset.currency)},")
            sb.append("${escapeCsv(categoryName)},")
            sb.append("${escapeCsv(flowNature)},")
            sb.append("${asset.currentValuation},")
            sb.append("${valUsd},")
            sb.append("${valBase},")
            sb.append("${asset.isArchived},")
            sb.append("${asset.includeInPortfolio},")
            sb.append("${escapeCsv(bucketName)},")
            sb.append("${formattedLastUpdated}\n")
        }
        sb.append("\n")

        // 4. BUCKETS
        sb.append("# === SECTION: BUCKETS ===\n")
        sb.append("Bucket ID,Name,Description,Target Amount,Is Decumulation (Spend Phase),Yearly Spend Budget,Buffer Years,Warning Threshold %,Target Gain %,Last Year Performance %,Last Updated\n")
        
        buckets.value.sortedBy { it.id }.forEach { bucket ->
            val formattedLastUpdated = dateFormat.format(java.util.Date(bucket.lastUpdated))
            sb.append("${bucket.id},")
            sb.append("${escapeCsv(bucket.name)},")
            sb.append("${escapeCsv(bucket.description)},")
            sb.append("${bucket.targetAmount},")
            sb.append("${bucket.isDecumulation},")
            sb.append("${bucket.yearlySpendBudget},")
            sb.append("${bucket.bufferYears},")
            sb.append("${bucket.warningThresholdPercent},")
            sb.append("${bucket.targetGainPercent},")
            sb.append("${bucket.lastYearPerformancePercent},")
            sb.append("${formattedLastUpdated}\n")
        }
        sb.append("\n")

        // 5. CATEGORIES
        sb.append("# === SECTION: CATEGORIES ===\n")
        sb.append("Category ID,Name,Is Asset/Liability,Last Updated\n")
        
        categories.value.sortedBy { it.id }.forEach { category ->
            val flowNature = if (category.isAsset) "Asset" else "Liability"
            val formattedLastUpdated = dateFormat.format(java.util.Date(category.lastUpdated))
            sb.append("${category.id},")
            sb.append("${escapeCsv(category.name)},")
            sb.append("${escapeCsv(flowNature)},")
            sb.append("${formattedLastUpdated}\n")
        }
        sb.append("\n")

        // 6. PORTFOLIO SNAPSHOTS
        sb.append("# === SECTION: PORTFOLIO HISTORICAL SNAPSHOTS ===\n")
        sb.append("Snapshot ID,Date & Time,Timestamp (ms),Net Worth (USD),Total Assets (USD),Total Liabilities (USD),Net Worth (${baseCurr}),Total Assets (${baseCurr}),Total Liabilities (${baseCurr}),Last Updated\n")
        
        snapshots.value.sortedByDescending { it.timestamp }.forEach { snap ->
            val nwBase = convertUsdToBase(snap.netWorthUsd)
            val assetsBase = convertUsdToBase(snap.totalAssetsUsd)
            val liabBase = convertUsdToBase(snap.totalLiabilitiesUsd)
            val formattedSnapDate = dateFormat.format(java.util.Date(snap.timestamp))
            val formattedLastUpdated = dateFormat.format(java.util.Date(snap.lastUpdated))

            sb.append("${snap.id},")
            sb.append("${formattedSnapDate},")
            sb.append("${snap.timestamp},")
            sb.append("${snap.netWorthUsd},")
            sb.append("${snap.totalAssetsUsd},")
            sb.append("${snap.totalLiabilitiesUsd},")
            sb.append("${nwBase},")
            sb.append("${assetsBase},")
            sb.append("${liabBase},")
            sb.append("${formattedLastUpdated}\n")
        }

        return sb.toString()
    }

    suspend fun importDatabaseFromJson(jsonStr: String): Boolean {
        return try {
            val root = org.json.JSONObject(jsonStr)
            
            if (!root.has("categories") || !root.has("assets") || !root.has("transactions")) {
                return false
            }
            
            repository.clearSnapshots()
            transactions.value.forEach { repository.deleteTransaction(it) }
            assets.value.forEach { repository.deleteAsset(it) }
            categories.value.forEach { repository.deleteCategory(it) }
            buckets.value.forEach { repository.deleteBucket(it) }

            val categoryMap = mutableMapOf<Int, Int>()
            val bucketMap = mutableMapOf<Int, Int>()
            val assetMap = mutableMapOf<Int, Int>()

            // 1. Categories
            val catsArray = root.getJSONArray("categories")
            for (i in 0 until catsArray.length()) {
                val o = catsArray.getJSONObject(i)
                val oldId = o.getInt("id")
                val name = o.getString("name")
                val isAsset = o.optBoolean("isAsset", true)
                
                val newId = repository.insertCategory(com.example.data.model.AssetCategory(name = name, isAsset = isAsset)).toInt()
                categoryMap[oldId] = newId
            }

            // 2. Buckets
            if (root.has("buckets")) {
                val bucketsArray = root.getJSONArray("buckets")
                for (i in 0 until bucketsArray.length()) {
                    val o = bucketsArray.getJSONObject(i)
                    val oldId = o.getInt("id")
                    val name = o.getString("name")
                    val description = o.optString("description", "")
                    val targetAmount = o.optDouble("targetAmount", 0.0)
                    val isDecumulation = o.optBoolean("isDecumulation", false)
                    val yearlySpendBudget = o.optDouble("yearlySpendBudget", 0.0)
                    val bufferYears = o.optInt("bufferYears", 5)
                    val warningThresholdPercent = o.optDouble("warningThresholdPercent", 20.0)
                    val targetGainPercent = o.optDouble("targetGainPercent", 6.0)
                    val lastYearPerformancePercent = o.optDouble("lastYearPerformancePercent", 0.0)
                    
                    val newId = repository.insertBucket(
                        com.example.data.model.Bucket(
                            name = name,
                            description = description,
                            targetAmount = targetAmount,
                            isDecumulation = isDecumulation,
                            yearlySpendBudget = yearlySpendBudget,
                            bufferYears = bufferYears,
                            warningThresholdPercent = warningThresholdPercent,
                            targetGainPercent = targetGainPercent,
                            lastYearPerformancePercent = lastYearPerformancePercent
                        )
                    ).toInt()
                    bucketMap[oldId] = newId
                }
            }

            // 3. Assets
            val assetsArray = root.getJSONArray("assets")
            for (i in 0 until assetsArray.length()) {
                val o = assetsArray.getJSONObject(i)
                val oldId = o.getInt("id")
                val name = o.getString("name")
                val currency = o.getString("currency")
                val oldCatId = o.getInt("categoryId")
                val currentValuation = o.optDouble("currentValuation", 0.0)
                val isArchived = o.optBoolean("isArchived", false)
                val includeInPortfolio = o.optBoolean("includeInPortfolio", true)
                
                val oldBucketId = if (o.isNull("bucketId")) null else o.getInt("bucketId")
                
                val mappedCatId = categoryMap[oldCatId] ?: categoryMap.values.firstOrNull() ?: 1
                val mappedBucketId = if (oldBucketId != null) bucketMap[oldBucketId] else null
                
                val newId = repository.insertAsset(
                    com.example.data.model.Asset(
                        name = name,
                        currency = currency,
                        categoryId = mappedCatId,
                        currentValuation = currentValuation,
                        isArchived = isArchived,
                        includeInPortfolio = includeInPortfolio,
                        bucketId = mappedBucketId
                    )
                ).toInt()
                assetMap[oldId] = newId
            }

            // 4. Transactions
            val txsArray = root.getJSONArray("transactions")
            for (i in 0 until txsArray.length()) {
                val o = txsArray.getJSONObject(i)
                val oldAssetId = o.getInt("assetId")
                val type = o.getString("type")
                val amount = o.getDouble("amount")
                val timestamp = o.getLong("timestamp")
                
                val oldDestId = if (o.isNull("destinationAssetId")) null else o.getInt("destinationAssetId")
                val exchangeRate = if (o.isNull("exchangeRate")) null else o.optDouble("exchangeRate")
                val notes = if (o.isNull("notes")) null else o.getString("notes")
                
                val mappedAssetId = assetMap[oldAssetId] ?: continue
                val mappedDestId = if (oldDestId != null) assetMap[oldDestId] else null
                
                repository.insertTransaction(
                    com.example.data.model.Transaction(
                        assetId = mappedAssetId,
                        type = type,
                        amount = amount,
                        timestamp = timestamp,
                        destinationAssetId = mappedDestId,
                        exchangeRate = exchangeRate,
                        notes = notes
                    )
                )
            }
            
            // 5. Restore Settings Configuration if available
            if (root.has("settings")) {
                val settingsObj = root.optJSONObject("settings")
                if (settingsObj != null) {
                    if (settingsObj.has("base_currency")) {
                        setBaseCurrency(settingsObj.getString("base_currency"))
                    }
                    if (settingsObj.has("values_hidden")) {
                        setValuesHidden(settingsObj.getBoolean("values_hidden"))
                    }
                    if (settingsObj.has("theme_index")) {
                        setThemeIndex(settingsObj.getInt("theme_index"))
                    }
                    if (settingsObj.has("app_font_scale")) {
                        setAppFontScale(settingsObj.getDouble("app_font_scale").toFloat())
                    }
                    if (settingsObj.has("app_lock_pin")) {
                        val pin = if (settingsObj.isNull("app_lock_pin")) null else settingsObj.getString("app_lock_pin")
                        setAppLockPin(pin)
                    }

                    // 6. Restore Spending Categories if available
                    if (settingsObj.has("spending_categories")) {
                        val catsObj = settingsObj.optJSONObject("spending_categories")
                        if (catsObj != null) {
                            val map = mutableMapOf<String, List<String>>()
                            val keys = catsObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val arr = catsObj.getJSONArray(key)
                                val list = mutableListOf<String>()
                                for (j in 0 until arr.length()) {
                                    list.add(arr.getString(j))
                                }
                                map[key] = list
                            }
                            saveSpendingCategories(map)
                        }
                    }

                    // 7. Restore Assigned Spending Wallet IDs (mapping old asset ID -> new asset ID)
                    if (settingsObj.has("spending_wallet_ids")) {
                        val arr = settingsObj.optJSONArray("spending_wallet_ids")
                        if (arr != null) {
                            val oldWalletIdsList = mutableListOf<Int>()
                            for (j in 0 until arr.length()) {
                                oldWalletIdsList.add(arr.getInt(j))
                            }
                            val newWalletIdsList = oldWalletIdsList.mapNotNull { assetMap[it] }
                            saveSpendingWalletIds(newWalletIdsList)
                        }
                    }

                    // 8. Restore Default Spending Wallet ID
                    if (settingsObj.has("default_spending_wallet_id") && !settingsObj.isNull("default_spending_wallet_id")) {
                        val oldDefaultId = settingsObj.getInt("default_spending_wallet_id")
                        val newDefaultId = assetMap[oldDefaultId]
                        setDefaultSpendingWalletId(newDefaultId)
                    }

                    // Restore Selected Wallet Bucket ID
                    if (settingsObj.has("selected_wallet_bucket_id") && !settingsObj.isNull("selected_wallet_bucket_id")) {
                        val oldBucketId = settingsObj.getInt("selected_wallet_bucket_id")
                        val newBucketId = bucketMap[oldBucketId]
                        setSelectedWalletBucketId(newBucketId)
                    }

                    // 9. Restore Wallet Aliases (mapping old asset ID -> new asset ID)
                    prefs.all.keys.filter { it.startsWith("wallet_alias_") }.forEach { k ->
                        prefs.edit().remove(k).apply()
                    }
                    if (settingsObj.has("wallet_aliases")) {
                        val aliasesObj = settingsObj.optJSONObject("wallet_aliases")
                        if (aliasesObj != null) {
                            val keys = aliasesObj.keys()
                            while (keys.hasNext()) {
                                val oldAssetIdStr = keys.next()
                                val oldAssetId = oldAssetIdStr.toIntOrNull()
                                val alias = aliasesObj.getString(oldAssetIdStr)
                                if (oldAssetId != null) {
                                    val newAssetId = assetMap[oldAssetId]
                                    if (newAssetId != null) {
                                        prefs.edit().putString("wallet_alias_$newAssetId", alias).apply()
                                    }
                                }
                            }
                        }
                    }
                    _walletAliases.value = getInitialWalletAliases()

                    // Restore Wallet Icons
                    prefs.all.keys.filter { it.startsWith("wallet_icon_") }.forEach { k ->
                        prefs.edit().remove(k).apply()
                    }
                    if (settingsObj.has("wallet_icons")) {
                        val iconsObj = settingsObj.optJSONObject("wallet_icons")
                        if (iconsObj != null) {
                            val keys = iconsObj.keys()
                            while (keys.hasNext()) {
                                val oldAssetIdStr = keys.next()
                                val oldAssetId = oldAssetIdStr.toIntOrNull()
                                val icon = iconsObj.getString(oldAssetIdStr)
                                if (oldAssetId != null) {
                                    val newAssetId = assetMap[oldAssetId]
                                    if (newAssetId != null) {
                                        prefs.edit().putString("wallet_icon_$newAssetId", icon).apply()
                                    }
                                }
                            }
                        }
                    }
                    _walletIcons.value = getInitialWalletIcons()

                    // Restore Wallet Budgets
                    prefs.all.keys.filter { it.startsWith("wallet_budget_") }.forEach { k ->
                        prefs.edit().remove(k).apply()
                    }
                    if (settingsObj.has("wallet_budgets")) {
                        val budgetsObj = settingsObj.optJSONObject("wallet_budgets")
                        if (budgetsObj != null) {
                            val keys = budgetsObj.keys()
                            while (keys.hasNext()) {
                                val oldAssetIdStr = keys.next()
                                val oldAssetId = oldAssetIdStr.toIntOrNull()
                                val budget = budgetsObj.optDouble(oldAssetIdStr, 0.0)
                                if (oldAssetId != null) {
                                    val newAssetId = assetMap[oldAssetId]
                                    if (newAssetId != null) {
                                        prefs.edit().putFloat("wallet_budget_$newAssetId", budget.toFloat()).apply()
                                    }
                                }
                            }
                        }
                    }
                    _walletBudgets.value = getInitialWalletBudgets()
                }
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun seedDemoData() {
        viewModelScope.launch {
            clearAllData()

            // 1. Create demo buckets
            val emergencyFundId = repository.insertBucket(Bucket(name = "Emergency Fund", description = "Liquid deposit reserves for rainy-day readiness", targetAmount = 15000.0, isDecumulation = false, targetGainPercent = 5.0, lastYearPerformancePercent = 5.2)).toInt()
            val retirementId = repository.insertBucket(Bucket(name = "Retirement Equities Growth", description = "Tactical long-term asset growth allocations", targetAmount = 250000.0, isDecumulation = false, targetGainPercent = 6.0, lastYearPerformancePercent = 8.5)).toInt()
            val cashBufferId = repository.insertBucket(Bucket(name = "Cash Spend Buffer (5Y)", description = "Decumulating emergency & annual cash buffer for living expenses budget", targetAmount = 0.0, isDecumulation = true, yearlySpendBudget = 4000.0, bufferYears = 5, warningThresholdPercent = 20.0)).toInt()
            val realEstateId = repository.insertBucket(Bucket(name = "Real Estate Wealth", description = "Property holdings and long-term equity", targetAmount = 150000.0, isDecumulation = false, targetGainPercent = 4.0, lastYearPerformancePercent = 5.0)).toInt()

            // 2. Create default categories
            val cashCatId = repository.insertCategory(AssetCategory(name = "Cash & Bank", isAsset = true)).toInt()
            val investCatId = repository.insertCategory(AssetCategory(name = "Brokerage & Stocks", isAsset = true)).toInt()
            val realEstateCatId = repository.insertCategory(AssetCategory(name = "Real Estate", isAsset = true)).toInt()
            val debtCatId = repository.insertCategory(AssetCategory(name = "Credit Cards", isAsset = false)).toInt()

            // 3. Create assets and assign to buckets
            val checkingsId = repository.insertAsset(Asset(name = "Checking Account", currency = "USD", categoryId = cashCatId, bucketId = cashBufferId)).toInt()
            val savingsId = repository.insertAsset(Asset(name = "High Yield Savings", currency = "USD", categoryId = cashCatId, bucketId = emergencyFundId)).toInt()
            val travelWalletId = repository.insertAsset(Asset(name = "Digital Travel Wallet", currency = "EUR", categoryId = cashCatId, bucketId = emergencyFundId)).toInt()
            val stockBrokerId = repository.insertAsset(Asset(name = "Index ETF Portfolio", currency = "USD", categoryId = investCatId, bucketId = retirementId)).toInt()
            val cryptoWalletId = repository.insertAsset(Asset(name = "Bitcoin Cold Wallet", currency = "BTC", categoryId = investCatId, bucketId = retirementId)).toInt()
            val houseId = repository.insertAsset(Asset(name = "Urban Family Home", currency = "EUR", categoryId = realEstateCatId, bucketId = realEstateId)).toInt()
            val visaId = repository.insertAsset(Asset(name = "Ultimate Premium Visa", currency = "USD", categoryId = debtCatId)).toInt()

            // 4. Save spending wallet settings & subcategory budgets into SharedPreferences
            val editor = prefs.edit()
            editor.putString("spending_wallet_ids", "$checkingsId,$savingsId,$travelWalletId,$visaId")
            editor.putInt("default_spending_wallet_id", checkingsId)

            // Wallet Budgets
            editor.putFloat("wallet_budget_$checkingsId", 2000.0f)
            editor.putFloat("wallet_budget_$savingsId", 4000.0f)
            editor.putFloat("wallet_budget_$travelWalletId", 1500.0f)
            editor.putFloat("wallet_budget_$visaId", 3000.0f)

            // Wallet Aliases & Icons
            editor.putString("wallet_alias_$checkingsId", "My Checking Account")
            editor.putString("wallet_icon_$checkingsId", "AccountBalance")
            editor.putString("wallet_alias_$savingsId", "Rainy Day Savings")
            editor.putString("wallet_icon_$savingsId", "Savings")
            editor.putString("wallet_alias_$travelWalletId", "Euro Travel Card")
            editor.putString("wallet_icon_$travelWalletId", "FlightTakeoff")
            editor.putString("wallet_alias_$visaId", "Ultimate Visa Card")
            editor.putString("wallet_icon_$visaId", "CreditCard")

            // Subcategory Budgets
            editor.putFloat("subcat_budget_Food_Groceries", 550.0f)
            editor.putString("subcat_budget_curr_Food_Groceries", "USD")
            editor.putFloat("subcat_budget_Food_Restaurants", 350.0f)
            editor.putString("subcat_budget_curr_Food_Restaurants", "USD")
            editor.putFloat("subcat_budget_Leisure_Shopping", 250.0f)
            editor.putString("subcat_budget_curr_Leisure_Shopping", "USD")
            editor.putFloat("subcat_budget_Leisure_Movies", 80.0f)
            editor.putString("subcat_budget_curr_Leisure_Movies", "USD")
            editor.putFloat("subcat_budget_Utilities_Electricity", 150.0f)
            editor.putString("subcat_budget_curr_Utilities_Electricity", "USD")
            editor.putFloat("subcat_budget_Utilities_Internet", 65.0f)
            editor.putString("subcat_budget_curr_Utilities_Internet", "USD")
            editor.putFloat("subcat_budget_Transportation_Fuel", 120.0f)
            editor.putString("subcat_budget_curr_Transportation_Fuel", "USD")
            editor.putFloat("subcat_budget_Transportation_Taxi/Ride-share", 100.0f)
            editor.putString("subcat_budget_curr_Transportation_Taxi/Ride-share", "USD")

            editor.apply()

            // Update ViewModel state flows immediately for smooth UI transition
            _spendingWalletIds.value = listOf(checkingsId, savingsId, travelWalletId, visaId)
            _defaultSpendingWalletId.value = checkingsId
            _walletBudgets.value = getInitialWalletBudgets()
            _walletAliases.value = getInitialWalletAliases()
            _walletIcons.value = getInitialWalletIcons()
            _subcategoryBudgets.value = getInitialSubcategoryBudgets()
            _subcategoryBudgetCurrencies.value = getInitialSubcategoryBudgetCurrencies()

            // 5. Populate historic transactions to construct stunning historical curves & wallet metrics!
            val now = System.currentTimeMillis()
            val oneMonth = 30L * 24 * 60 * 60 * 1000
            val oneDay = 24L * 60 * 60 * 1000

            // ----------------------------------------------------
            // CHECKING ACCOUNT LEDGER
            // ----------------------------------------------------
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "DEPOSIT", amount = 8500.0, timestamp = now - 5 * oneMonth))
            
            // Salary inflows over 6 months
            for (i in 0..5) {
                repository.insertTransaction(Transaction(
                    assetId = checkingsId, 
                    type = "INCOME", 
                    amount = 4500.0, 
                    timestamp = now - i * oneMonth - 15 * oneDay,
                    notes = "Other > Uncategorized | Monthly Salary Deposit"
                ))
            }

            // Food & Groceries
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 120.0, timestamp = now - 5 * oneMonth, notes = "Food > Groceries | Whole Foods Organic"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 145.0, timestamp = now - 4 * oneMonth, notes = "Food > Groceries | Supermarket weekly"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 160.0, timestamp = now - 3 * oneMonth, notes = "Food > Groceries | Groceries at Target"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 110.0, timestamp = now - 2 * oneMonth, notes = "Food > Groceries | Weekly fresh market"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 240.0, timestamp = now - 1 * oneMonth, notes = "Food > Groceries | Costco bulk items"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 130.0, timestamp = now - 25 * oneDay, notes = "Food > Groceries | Groceries Trader Joes"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 115.0, timestamp = now - 18 * oneDay, notes = "Food > Groceries | Whole Foods weekly run"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 90.0, timestamp = now - 12 * oneDay, notes = "Food > Groceries | Local supermarket stock"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 75.0, timestamp = now - 5 * oneDay, notes = "Food > Groceries | Trader Joe's snacks"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 45.0, timestamp = now - 1 * oneDay, notes = "Food > Groceries | Quick grocery fill-up"))

            // Restaurants
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 85.0, timestamp = now - 4 * oneMonth, notes = "Food > Restaurants | Friday Night Sushi"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 150.0, timestamp = now - 3 * oneMonth, notes = "Food > Restaurants | Birthday steak dinner"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 95.0, timestamp = now - 2 * oneMonth, notes = "Food > Restaurants | Italian bistro dinner"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 60.0, timestamp = now - 1 * oneMonth, notes = "Food > Restaurants | Ramen and gyoza with team"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 45.0, timestamp = now - 20 * oneDay, notes = "Food > Restaurants | Saturday brunch"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 120.0, timestamp = now - 10 * oneDay, notes = "Food > Restaurants | Fine dining date night"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 40.0, timestamp = now - 3 * oneDay, notes = "Food > Restaurants | Pizza delivery feast"))

            // Coffee
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 6.50, timestamp = now - 28 * oneDay, notes = "Food > Coffee | Starbucks espresso"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 5.20, timestamp = now - 22 * oneDay, notes = "Food > Coffee | Local roasters latte"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 7.00, timestamp = now - 15 * oneDay, notes = "Food > Coffee | Specialty coffee"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 5.50, timestamp = now - 8 * oneDay, notes = "Food > Coffee | Cold brew energy"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 9.50, timestamp = now - 2 * oneDay, notes = "Food > Coffee | Coffee and croissant"))

            // Utilities
            for (i in 1..4) {
                repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 110.0 + (i * 5), timestamp = now - i * oneMonth - 10 * oneDay, notes = "Utilities > Electricity | Power company bill"))
                repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 65.0, timestamp = now - i * oneMonth - 12 * oneDay, notes = "Utilities > Internet | Gigabit Fiber subscription"))
            }
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 135.0, timestamp = now - 8 * oneDay, notes = "Utilities > Electricity | Power company bill"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 65.0, timestamp = now - 10 * oneDay, notes = "Utilities > Internet | Gigabit Fiber subscription"))

            // Leisure Shopping
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 180.0, timestamp = now - 4 * oneMonth, notes = "Leisure > Shopping | New winter jacket"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 110.0, timestamp = now - 3 * oneMonth, notes = "Leisure > Shopping | Amazon Kindle e-reader"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 220.0, timestamp = now - 2 * oneMonth, notes = "Leisure > Shopping | Noise-cancelling headphones"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 130.0, timestamp = now - 1 * oneMonth, notes = "Leisure > Shopping | Running sneakers"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 55.0, timestamp = now - 16 * oneDay, notes = "Leisure > Shopping | Bookstore shopping spree"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 75.0, timestamp = now - 4 * oneDay, notes = "Leisure > Shopping | Amazon gadgets and cables"))

            // Leisure Movies/Entertainment
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 35.0, timestamp = now - 3 * oneMonth, notes = "Leisure > Movies | IMAX theater premium tickets"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 120.0, timestamp = now - 1 * oneMonth, notes = "Leisure > Movies | Streaming video yearly pass"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 40.0, timestamp = now - 9 * oneDay, notes = "Leisure > Movies | Cinema tickets and snacks"))

            // Transportation Taxi/Ride-share
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 28.0, timestamp = now - 27 * oneDay, notes = "Transportation > Taxi/Ride-share | Weekend ride back home"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 32.0, timestamp = now - 21 * oneDay, notes = "Transportation > Taxi/Ride-share | Premium ride-share to office"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 18.0, timestamp = now - 13 * oneDay, notes = "Transportation > Taxi/Ride-share | Ride-share to dinner"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 45.0, timestamp = now - 6 * oneDay, notes = "Transportation > Taxi/Ride-share | Late night airport ride"))

            // Transportation Fuel
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 55.0, timestamp = now - 2 * oneMonth, notes = "Transportation > Fuel | Fuel station refill"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 60.0, timestamp = now - 1 * oneMonth, notes = "Transportation > Fuel | Fuel station refill"))
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 65.0, timestamp = now - 11 * oneDay, notes = "Transportation > Fuel | Full tank gasoline"))

            // Housing Rent/Mortgage
            for (i in 1..4) {
                repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 1400.0, timestamp = now - i * oneMonth - 3 * oneDay, notes = "Housing > Rent/Mortgage | Monthly Apartment Lease"))
            }
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "WITHDRAWAL", amount = 1400.0, timestamp = now - 2 * oneDay, notes = "Housing > Rent/Mortgage | Monthly Apartment Lease"))

            // Interest & Yields
            repository.insertTransaction(Transaction(assetId = checkingsId, type = "INCOME", amount = 45.0, timestamp = now - 1 * oneMonth, notes = "Other > Uncategorized | Account Yield Interest"))

            // ----------------------------------------------------
            // HIGH YIELD SAVINGS LEDGER
            // ----------------------------------------------------
            repository.insertTransaction(Transaction(assetId = savingsId, type = "DEPOSIT", amount = 25000.0, timestamp = now - 6 * oneMonth))
            for (i in 1..5) {
                repository.insertTransaction(Transaction(assetId = savingsId, type = "DEPOSIT", amount = 500.0, timestamp = now - i * oneMonth - 10 * oneDay, notes = "Other > Uncategorized | Auto Savings Transfer"))
            }
            repository.insertTransaction(Transaction(assetId = savingsId, type = "DEPOSIT", amount = 500.0, timestamp = now - 8 * oneDay, notes = "Other > Uncategorized | Auto Savings Transfer"))
            repository.insertTransaction(Transaction(assetId = savingsId, type = "INCOME", amount = 85.0, timestamp = now - 3 * oneMonth, notes = "Other > Uncategorized | Savings Interest"))
            repository.insertTransaction(Transaction(assetId = savingsId, type = "INCOME", amount = 92.0, timestamp = now - 1 * oneMonth, notes = "Other > Uncategorized | Savings Interest"))

            // ----------------------------------------------------
            // DIGITAL TRAVEL WALLET LEDGER (EUR)
            // ----------------------------------------------------
            repository.insertTransaction(Transaction(assetId = travelWalletId, type = "DEPOSIT", amount = 1200.0, timestamp = now - 3 * oneMonth, notes = "Other > Uncategorized | Fund Travel Wallet"))
            repository.insertTransaction(Transaction(assetId = travelWalletId, type = "WITHDRAWAL", amount = 12.50, timestamp = now - 2 * oneMonth - 5 * oneDay, notes = "Food > Restaurants | Parisian cafe espresso"))
            repository.insertTransaction(Transaction(assetId = travelWalletId, type = "WITHDRAWAL", amount = 65.00, timestamp = now - 2 * oneMonth - 4 * oneDay, notes = "Food > Restaurants | Parisian Bistro Dinner"))
            repository.insertTransaction(Transaction(assetId = travelWalletId, type = "WITHDRAWAL", amount = 22.00, timestamp = now - 2 * oneMonth - 3 * oneDay, notes = "Leisure > Hobby | Louvre entry ticket"))
            repository.insertTransaction(Transaction(assetId = travelWalletId, type = "WITHDRAWAL", amount = 15.00, timestamp = now - 2 * oneMonth - 2 * oneDay, notes = "Transportation > Public Transit | Paris Metro Ticket Bundle"))
            repository.insertTransaction(Transaction(assetId = travelWalletId, type = "WITHDRAWAL", amount = 85.00, timestamp = now - 2 * oneMonth - 1 * oneDay, notes = "Leisure > Shopping | Paris souvenirs and gifts"))

            // ----------------------------------------------------
            // INDEX ETF PORTFOLIO LEDGER
            // ----------------------------------------------------
            repository.insertTransaction(Transaction(assetId = stockBrokerId, type = "DEPOSIT", amount = 65000.0, timestamp = now - 6 * oneMonth))
            for (i in 1..5) {
                repository.insertTransaction(Transaction(assetId = stockBrokerId, type = "DEPOSIT", amount = 1000.0, timestamp = now - i * oneMonth - 12 * oneDay))
            }
            // Valuation updates over time
            repository.insertTransaction(Transaction(assetId = stockBrokerId, type = "UPDATE", amount = 67800.0, timestamp = now - 5 * oneMonth))
            repository.insertTransaction(Transaction(assetId = stockBrokerId, type = "UPDATE", amount = 70200.0, timestamp = now - 4 * oneMonth))
            repository.insertTransaction(Transaction(assetId = stockBrokerId, type = "UPDATE", amount = 74500.0, timestamp = now - 3 * oneMonth))
            repository.insertTransaction(Transaction(assetId = stockBrokerId, type = "UPDATE", amount = 79100.0, timestamp = now - 2 * oneMonth))
            repository.insertTransaction(Transaction(assetId = stockBrokerId, type = "UPDATE", amount = 84300.0, timestamp = now - 1 * oneMonth))
            repository.insertTransaction(Transaction(assetId = stockBrokerId, type = "UPDATE", amount = 87600.0, timestamp = now - 4 * oneDay))
            repository.insertTransaction(Transaction(assetId = stockBrokerId, type = "INCOME", amount = 420.0, timestamp = now - 3 * oneMonth, notes = "Other > Uncategorized | Q1 Dividends Reinvested"))
            repository.insertTransaction(Transaction(assetId = stockBrokerId, type = "INCOME", amount = 480.0, timestamp = now - 20 * oneDay, notes = "Other > Uncategorized | Q2 Dividends Reinvested"))

            // ----------------------------------------------------
            // BITCOIN COLD WALLET LEDGER (BTC)
            // ----------------------------------------------------
            repository.insertTransaction(Transaction(assetId = cryptoWalletId, type = "DEPOSIT", amount = 0.25, timestamp = now - 6 * oneMonth))
            repository.insertTransaction(Transaction(assetId = cryptoWalletId, type = "DEPOSIT", amount = 0.05, timestamp = now - 3 * oneMonth))
            repository.insertTransaction(Transaction(assetId = cryptoWalletId, type = "UPDATE", amount = 0.30, timestamp = now - 5 * oneDay))

            // ----------------------------------------------------
            // URBAN FAMILY HOME LEDGER (EUR)
            // ----------------------------------------------------
            repository.insertTransaction(Transaction(assetId = houseId, type = "DEPOSIT", amount = 350000.0, timestamp = now - 6 * oneMonth))
            repository.insertTransaction(Transaction(assetId = houseId, type = "UPDATE", amount = 355000.0, timestamp = now - 3 * oneMonth))
            repository.insertTransaction(Transaction(assetId = houseId, type = "UPDATE", amount = 362000.0, timestamp = now - 1 * oneDay))

            // ----------------------------------------------------
            // ULTIMATE PREMIUM VISA LEDGER
            // ----------------------------------------------------
            repository.insertTransaction(Transaction(assetId = visaId, type = "DEPOSIT", amount = 2400.0, timestamp = now - 4 * oneMonth))
            repository.insertTransaction(Transaction(assetId = visaId, type = "WITHDRAWAL", amount = 1800.0, timestamp = now - 3 * oneMonth))
            repository.insertTransaction(Transaction(assetId = visaId, type = "DEPOSIT", amount = 1200.0, timestamp = now - 2 * oneMonth))
            repository.insertTransaction(Transaction(assetId = visaId, type = "WITHDRAWAL", amount = 1200.0, timestamp = now - 1 * oneMonth))
            repository.insertTransaction(Transaction(assetId = visaId, type = "DEPOSIT", amount = 450.0, timestamp = now - 15 * oneDay, notes = "Leisure > Shopping | Electronics store purchase"))
            repository.insertTransaction(Transaction(assetId = visaId, type = "DEPOSIT", amount = 220.0, timestamp = now - 5 * oneDay, notes = "Food > Restaurants | Michelin star dinner"))
        }
    }

    init {
        viewModelScope.launch {
            combine(
                transactions,
                subcategoryBudgets,
                spendingWalletIds
            ) { _, _, _ -> }
                .collect {
                    checkBudgetsAndGenerateNotifications()
                    checkRecurringExpensesAndGenerateNotifications()
                }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NetWorthViewModel::class.java)) {
                val db = AppDatabase.getDatabase(application)
                val repo = FinancialRepository(db.financialDao())
                @Suppress("UNCHECKED_CAST")
                return NetWorthViewModel(application, repo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
