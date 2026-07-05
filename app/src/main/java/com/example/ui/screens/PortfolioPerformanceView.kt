package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Asset
import com.example.data.model.AssetCategory
import com.example.data.model.Transaction
import com.example.ui.viewmodel.NetWorthViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class PerformanceChartPoint(
    val timestamp: Long,
    val netWorthUsd: Double
)

data class MonthlyPerformanceBar(
    val label: String,
    val timestamp: Long,
    val profitLossUsd: Double
)

data class AssetPerformanceRanking(
    val asset: Asset,
    val categoryName: String,
    val absoluteGainUsd: Double,
    val gainPercent: Double
)

data class DetailedAssetPerformance(
    val asset: Asset,
    val categoryName: String,
    val netDepositUsd: Double,
    val currentValueUsd: Double,
    val plUsd: Double,
    val plPercent: Double
)

data class AggregatedPerformanceMetrics(
    val startTimestamp: Long,
    val endTimestamp: Long,
    val startNetWorthUsd: Double,
    val endNetWorthUsd: Double,
    val netDepositsUsd: Double,
    val totalWithdrawalsUsd: Double,
    val totalIncomeUsd: Double,
    val absolutePlUsd: Double,
    val dietzReturnPercent: Double,
    val annualizedReturnPercent: Double,
    val averageMonthlyIncomeUsd: Double,
    val averageYearlyIncomeUsd: Double,
    val valuationTrendPoints: List<PerformanceChartPoint>,
    val monthlyPlBars: List<MonthlyPerformanceBar>,
    val monthlyIncomeBars: List<MonthlyPerformanceBar>,
    val topPerformingAssets: List<AssetPerformanceRanking>,
    val allAssetsPerformance: List<DetailedAssetPerformance>
)

@Composable
fun PortfolioPerformanceView(
    viewModel: NetWorthViewModel,
    transactions: List<Transaction>,
    assets: List<Asset>,
    categories: List<AssetCategory>,
    onBack: () -> Unit
) {
    val selectedTimeline by viewModel.selectedTimeline.collectAsStateWithLifecycle()
    val customStartDate by viewModel.customStartDate.collectAsStateWithLifecycle()
    val customEndDate by viewModel.customEndDate.collectAsStateWithLifecycle()
    val exchangeRates by viewModel.exchangeRatesState.collectAsStateWithLifecycle()
    val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()
    val valuesHidden by viewModel.valuesHidden.collectAsStateWithLifecycle()

    // 1. Core performance analytics calculation block
    val performanceMetrics = remember(transactions, assets, categories, selectedTimeline, customStartDate, customEndDate, exchangeRates) {
        val activeAssets = assets.filter { it.includeInPortfolio && !it.isArchived }
        
        // Define timeline start and end timestamps
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        val startTimestamp = when (selectedTimeline) {
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
            "CST" -> customStartDate
            else -> {
                val earliestTx = transactions.minOfOrNull { it.timestamp }
                if (earliestTx != null) {
                    earliestTx - 24 * 60 * 60 * 1000L
                } else {
                    calendar.add(Calendar.YEAR, -1)
                    calendar.timeInMillis
                }
            }
        }
        val endTimestamp = when (selectedTimeline) {
            "CST" -> customEndDate
            else -> now
        }

        val periodDurationMs = endTimestamp - startTimestamp

        // Calculate Starting and Ending global portfolio Net Worth
        val startNetWorth = calculatePortfolioNetWorthAt(startTimestamp, activeAssets, categories, transactions, exchangeRates)
        val endNetWorth = calculatePortfolioNetWorthAt(endTimestamp, activeAssets, categories, transactions, exchangeRates)

        // Calculate transaction metrics isolated to the selected timeline
        var totalDepositsUsd = 0.0
        var totalWithdrawalsUsd = 0.0
        var totalIncomeUsd = 0.0
        val cashFlows = mutableListOf<Pair<Long, Double>>()

        for (tx in transactions) {
            if (tx.timestamp > startTimestamp && tx.timestamp <= endTimestamp) {
                val asset = assets.find { it.id == tx.assetId }
                val destAsset = assets.find { it.id == tx.destinationAssetId }

                if (asset != null && asset.includeInPortfolio && !asset.isArchived) {
                    val category = categories.find { it.id == asset.categoryId }
                    val isAsset = category?.isAsset ?: true
                    val amountUsd = tx.amount * (exchangeRates[asset.currency] ?: 1.0)

                    when (tx.type) {
                        "DEPOSIT" -> {
                            if (isAsset) {
                                totalDepositsUsd += amountUsd
                                cashFlows.add(Pair(tx.timestamp, amountUsd))
                            } else {
                                totalWithdrawalsUsd += amountUsd
                                cashFlows.add(Pair(tx.timestamp, -amountUsd))
                            }
                        }
                        "WITHDRAWAL" -> {
                            if (isAsset) {
                                totalWithdrawalsUsd += amountUsd
                                cashFlows.add(Pair(tx.timestamp, -amountUsd))
                            } else {
                                totalDepositsUsd += amountUsd
                                cashFlows.add(Pair(tx.timestamp, amountUsd))
                            }
                        }
                        "INCOME" -> {
                            totalIncomeUsd += amountUsd
                        }
                        "TRANSFER" -> {
                            // Net-zero logic for internal transfer: if destination is also included and active, net impact is zero.
                            val isDestIncluded = destAsset != null && destAsset.includeInPortfolio && !destAsset.isArchived
                            if (!isDestIncluded) {
                                if (isAsset) {
                                    totalWithdrawalsUsd += amountUsd
                                    cashFlows.add(Pair(tx.timestamp, -amountUsd))
                                } else {
                                    totalDepositsUsd += amountUsd
                                    cashFlows.add(Pair(tx.timestamp, amountUsd))
                                }
                            }
                        }
                    }
                }

                // Verify receiving side of external transfer
                if (tx.type == "TRANSFER" && destAsset != null && destAsset.includeInPortfolio && !destAsset.isArchived) {
                    val isSourceIncluded = asset != null && asset.includeInPortfolio && !asset.isArchived
                    if (!isSourceIncluded) {
                        val destCategory = categories.find { it.id == destAsset.categoryId }
                        val isDestAsset = destCategory?.isAsset ?: true
                        val incomingAmount = tx.amount * (tx.exchangeRate ?: 1.0)
                        val incomingUsd = incomingAmount * (exchangeRates[destAsset.currency] ?: 1.0)

                        if (isDestAsset) {
                            totalDepositsUsd += incomingUsd
                            cashFlows.add(Pair(tx.timestamp, incomingUsd))
                        } else {
                            totalWithdrawalsUsd += incomingUsd
                            cashFlows.add(Pair(tx.timestamp, -incomingUsd))
                        }
                    }
                }
            }
        }

        val netDepositsUsd = totalDepositsUsd - totalWithdrawalsUsd

        // Absolute portfolio P/L
        // Valuation End - Valuation Start - Net Deposits
        val absolutePl = endNetWorth - startNetWorth - netDepositsUsd

        // Modified Dietz formula rate of return R
        var weightedCashFlowSum = 0.0
        for ((timestamp, amountUsd) in cashFlows) {
            val weight = if (periodDurationMs > 0) {
                ((endTimestamp - timestamp).toDouble() / periodDurationMs.toDouble()).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            weightedCashFlowSum += amountUsd * weight
        }

        val dietzDenominator = startNetWorth + weightedCashFlowSum
        val dietzReturn = if (dietzDenominator != 0.0) {
            absolutePl / dietzDenominator
        } else {
            0.0
        }

        // Annualized Return
        val yearMs = 365.25 * 24 * 60 * 60 * 1000.0
        val Y = if (periodDurationMs > 0) periodDurationMs.toDouble() / yearMs else 0.0
        val annualizedReturn = if (Y > 0.0) {
            if (1.0 + dietzReturn > 0.0) {
                (Math.pow(1.0 + dietzReturn, 1.0 / Y) - 1.0) * 100.0
            } else {
                (dietzReturn / Y) * 100.0
            }
        } else {
            dietzReturn * 100.0
        }

        // Run rate calculations
        val monthMs = 30.4375 * 24 * 60 * 60 * 1000.0
        val monthsElapsed = if (periodDurationMs > 0) Math.max(0.1, periodDurationMs.toDouble() / monthMs) else 1.0
        val avgMonthlyIncome = totalIncomeUsd / monthsElapsed
        val avgYearlyIncome = if (Y > 0.0) totalIncomeUsd / Y else totalIncomeUsd

        // Valuation trend points projection (12 sequential snapshots)
        val trendPoints = mutableListOf<PerformanceChartPoint>()
        val segments = 12
        val stepMs = if (periodDurationMs > 0) periodDurationMs / (segments - 1) else 0L
        for (i in 0 until segments) {
            val sTime = if (periodDurationMs > 0) startTimestamp + i * stepMs else startTimestamp
            val sNetWorth = calculatePortfolioNetWorthAt(sTime, activeAssets, categories, transactions, exchangeRates)
            trendPoints.add(PerformanceChartPoint(timestamp = sTime, netWorthUsd = sNetWorth))
        }

        // Monthly P/L Bar groupings
        val monthlyBarsResult = mutableListOf<MonthlyPerformanceBar>()
        val monthlyIncomeBarsResult = mutableListOf<MonthlyPerformanceBar>()
        val calLoop = Calendar.getInstance()
        calLoop.timeInMillis = startTimestamp

        while (calLoop.timeInMillis < endTimestamp) {
            val mStart = calLoop.timeInMillis
            val currentMonth = calLoop.get(Calendar.MONTH)
            val currentYear = calLoop.get(Calendar.YEAR)

            calLoop.set(Calendar.DAY_OF_MONTH, calLoop.getActualMaximum(Calendar.DAY_OF_MONTH))
            calLoop.set(Calendar.HOUR_OF_DAY, 23)
            calLoop.set(Calendar.MINUTE, 59)
            calLoop.set(Calendar.SECOND, 59)
            calLoop.set(Calendar.MILLISECOND, 999)

            val mEnd = Math.min(endTimestamp, calLoop.timeInMillis)

            val valStartM = calculatePortfolioNetWorthAt(mStart, activeAssets, categories, transactions, exchangeRates)
            val valEndM = calculatePortfolioNetWorthAt(mEnd, activeAssets, categories, transactions, exchangeRates)

            var mNetDeposits = 0.0
            var mIncome = 0.0
            for (tx in transactions) {
                if (tx.timestamp > mStart && tx.timestamp <= mEnd) {
                    val asset = assets.find { it.id == tx.assetId }
                    val destAsset = assets.find { it.id == tx.destinationAssetId }

                    if (asset != null && asset.includeInPortfolio && !asset.isArchived) {
                        val cat = categories.find { it.id == asset.categoryId }
                        val isAsset = cat?.isAsset ?: true
                        val amtUsd = tx.amount * (exchangeRates[asset.currency] ?: 1.0)

                        when (tx.type) {
                            "DEPOSIT" -> mNetDeposits += if (isAsset) amtUsd else -amtUsd
                            "WITHDRAWAL" -> mNetDeposits -= if (isAsset) amtUsd else -amtUsd
                            "INCOME" -> {
                                mIncome += amtUsd
                            }
                            "TRANSFER" -> {
                                val isDestIncluded = destAsset != null && destAsset.includeInPortfolio && !destAsset.isArchived
                                if (!isDestIncluded) {
                                    mNetDeposits -= if (isAsset) amtUsd else -amtUsd
                                }
                            }
                        }
                    }

                    if (tx.type == "TRANSFER" && destAsset != null && destAsset.includeInPortfolio && !destAsset.isArchived) {
                        val isSourceIncluded = asset != null && asset.includeInPortfolio && !asset.isArchived
                        if (!isSourceIncluded) {
                            val destCategory = categories.find { it.id == destAsset.categoryId }
                            val isDestAsset = destCategory?.isAsset ?: true
                            val incomingNative = tx.amount * (tx.exchangeRate ?: 1.0)
                            val incomingUsd = incomingNative * (exchangeRates[destAsset.currency] ?: 1.0)
                            mNetDeposits += if (isDestAsset) incomingUsd else -incomingUsd
                        }
                    }
                }
            }

            val mPl = valEndM - valStartM - mNetDeposits
            val mNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val label = "${mNames[currentMonth]} '${currentYear.toString().takeLast(2)}"

            monthlyBarsResult.add(MonthlyPerformanceBar(label = label, timestamp = mStart, profitLossUsd = mPl))
            monthlyIncomeBarsResult.add(MonthlyPerformanceBar(label = label, timestamp = mStart, profitLossUsd = mIncome))

            calLoop.timeInMillis = mStart
            calLoop.add(Calendar.MONTH, 1)
            calLoop.set(Calendar.DAY_OF_MONTH, 1)
            calLoop.set(Calendar.HOUR_OF_DAY, 0)
            calLoop.set(Calendar.MINUTE, 0)
            calLoop.set(Calendar.SECOND, 0)
            calLoop.set(Calendar.MILLISECOND, 0)
        }

        val finalPlBars = if (monthlyBarsResult.size > 12) monthlyBarsResult.takeLast(12) else monthlyBarsResult
        val finalIncomeBars = if (monthlyIncomeBarsResult.size > 12) monthlyIncomeBarsResult.takeLast(12) else monthlyIncomeBarsResult

        // Top 5 Performing Assets
        val assetPerformances = mutableListOf<AssetPerformanceRanking>()
        val detailedPerformances = mutableListOf<DetailedAssetPerformance>()
        for (asset in activeAssets) {
            val category = categories.find { it.id == asset.categoryId } ?: continue
            val isAsset = category.isAsset
            if (!isAsset) continue // Do not include liabilities in individual asset performance analysis

            val assetStartVal = calculateAssetValuationAt(startTimestamp, asset, transactions, categories, exchangeRates) * (exchangeRates[asset.currency] ?: 1.0)
            val assetEndVal = calculateAssetValuationAt(endTimestamp, asset, transactions, categories, exchangeRates) * (exchangeRates[asset.currency] ?: 1.0)

            var netAssetDepUsd = 0.0
            var totalAssetDepUsd = 0.0
            for (tx in transactions) {
                if (tx.timestamp > startTimestamp && tx.timestamp <= endTimestamp) {
                    val amountUsd = tx.amount * (exchangeRates[asset.currency] ?: 1.0)
                    if (tx.assetId == asset.id) {
                        when (tx.type) {
                            "DEPOSIT" -> {
                                netAssetDepUsd += if (isAsset) amountUsd else -amountUsd
                                totalAssetDepUsd += amountUsd
                            }
                            "WITHDRAWAL" -> {
                                netAssetDepUsd -= if (isAsset) amountUsd else -amountUsd
                            }
                            "TRANSFER" -> {
                                netAssetDepUsd -= if (isAsset) amountUsd else -amountUsd
                            }
                        }
                    } else if (tx.destinationAssetId == asset.id && tx.type == "TRANSFER") {
                        val incomingNative = tx.amount * (tx.exchangeRate ?: 1.0)
                        val incomingUsd = incomingNative * (exchangeRates[asset.currency] ?: 1.0)
                        netAssetDepUsd += if (isAsset) incomingUsd else -incomingUsd
                        totalAssetDepUsd += incomingUsd
                    }
                }
            }

            val absGain = if (isAsset) {
                assetEndVal - assetStartVal - netAssetDepUsd
            } else {
                -(assetEndVal - assetStartVal - netAssetDepUsd)
            }

            val baseCost = Math.max(1.0, assetStartVal + totalAssetDepUsd)
            val pctGain = (absGain / baseCost) * 100.0

            assetPerformances.add(
                AssetPerformanceRanking(
                    asset = asset,
                    categoryName = category.name,
                    absoluteGainUsd = absGain,
                    gainPercent = pctGain
                )
            )

            detailedPerformances.add(
                DetailedAssetPerformance(
                    asset = asset,
                    categoryName = category.name,
                    netDepositUsd = netAssetDepUsd,
                    currentValueUsd = assetEndVal,
                    plUsd = absGain,
                    plPercent = pctGain
                )
            )
        }

        val topAssets = assetPerformances
            .filter { it.absoluteGainUsd > 0.0 }
            .sortedByDescending { it.gainPercent }
            .take(5)

        AggregatedPerformanceMetrics(
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            startNetWorthUsd = startNetWorth,
            endNetWorthUsd = endNetWorth,
            netDepositsUsd = netDepositsUsd,
            totalWithdrawalsUsd = totalWithdrawalsUsd,
            totalIncomeUsd = totalIncomeUsd,
            absolutePlUsd = absolutePl,
            dietzReturnPercent = dietzReturn * 100.0,
            annualizedReturnPercent = annualizedReturn,
            averageMonthlyIncomeUsd = avgMonthlyIncome,
            averageYearlyIncomeUsd = avgYearlyIncome,
            valuationTrendPoints = trendPoints,
            monthlyPlBars = finalPlBars,
            monthlyIncomeBars = finalIncomeBars,
            topPerformingAssets = topAssets,
            allAssetsPerformance = detailedPerformances
        )
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Portfolio Performance",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val dateForm = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                        Text(
                            text = "${dateForm.format(Date(performanceMetrics.startTimestamp))} - ${dateForm.format(Date(performanceMetrics.endTimestamp))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // A. Timeline Slider Choice Segment
            item {
                Spacer(modifier = Modifier.height(4.dp))
                TimePeriodSelector(
                    selectedTimeline = selectedTimeline,
                    onSelect = { viewModel.setTimeline(it) }
                )
            }

            // B. Hero KPIs Widget
            item {
                PortfolioGainHeroWidget(performanceMetrics, baseCurrency, viewModel, valuesHidden)
            }

            // C. Secondary Metrics (Grid layout breakdown)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricWidgetCard(
                        modifier = Modifier.weight(1f),
                        title = "Net Deposits",
                        value = formatPortfolioAmount(performanceMetrics.netDepositsUsd, baseCurrency, viewModel, valuesHidden = valuesHidden),
                        icon = Icons.Default.VerticalAlignBottom,
                        accentColor = MaterialTheme.colorScheme.primary,
                        supportingText = "Deposited: ${formatPortfolioAmount(performanceMetrics.netDepositsUsd + performanceMetrics.totalWithdrawalsUsd, baseCurrency, viewModel, valuesHidden = valuesHidden)}"
                    )
                    MetricWidgetCard(
                        modifier = Modifier.weight(1f),
                        title = "Withdrawals",
                        value = formatPortfolioAmount(performanceMetrics.totalWithdrawalsUsd, baseCurrency, viewModel, valuesHidden = valuesHidden),
                        icon = Icons.Default.VerticalAlignTop,
                        accentColor = MaterialTheme.colorScheme.error,
                        supportingText = "Capital withdrawn"
                    )
                }
            }

            // D. Passive Income Run-Rate Metrics
            item {
                PassiveIncomeRunRateWidget(performanceMetrics, baseCurrency, viewModel, valuesHidden)
            }

            // E. Valuation Line Graph Trend
            item {
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShowChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Valuation Trend",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        ValuationTrendChart(performanceMetrics.valuationTrendPoints)
                    }
                }
            }

            // F. Monthly P/L Bar Graph
            item {
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Monthly Organic P/L",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        MonthlyPlBarChart(performanceMetrics.monthlyPlBars, baseCurrency, viewModel, valuesHidden)
                    }
                }
            }

            // F2. Monthly Total Income Bar Graph
            item {
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Monthly Total Income",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        MonthlyIncomeBarChart(performanceMetrics.monthlyIncomeBars, baseCurrency, viewModel, valuesHidden)
                    }
                }
            }

            // G. Top 5 Performing Assets ranking
            item {
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFBC02D)
                            )
                            Text(
                                text = "Top Performing Assets",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        if (performanceMetrics.topPerformingAssets.isEmpty()) {
                            Text(
                                text = "No assets with positive gains recorded in this timeline.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            performanceMetrics.topPerformingAssets.forEachIndexed { idx, ranking ->
                                TopAssetItemRow(idx + 1, ranking, baseCurrency, viewModel, valuesHidden)
                                if (idx < performanceMetrics.topPerformingAssets.size - 1) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // H. Expandable Tabulized Asset Class Directory
            item {
                AssetClassDirectoryWidget(performanceMetrics, baseCurrency, viewModel, valuesHidden)
            }
        }
    }
}

@Composable
fun TimePeriodSelector(
    selectedTimeline: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val options = listOf(
            "YTD" to "YTD",
            "1YR" to "1 Year",
            "5YR" to "5 Years",
            "ALL" to "All",
            "CST" to "Custom"
        )

        options.forEach { (code, label) ->
            val isSelected = selectedTimeline == code
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(code) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PortfolioGainHeroWidget(
    metrics: AggregatedPerformanceMetrics,
    baseCurrency: String,
    viewModel: NetWorthViewModel,
    valuesHidden: Boolean
) {
    val isProfit = metrics.absolutePlUsd >= 0
    val accentColor = if (isProfit) Color(0xFF2E7D32) else Color(0xFFC62828)

    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ABSOLUTE PORTFOLIO P/L",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isProfit) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatPortfolioAmount(metrics.absolutePlUsd, baseCurrency, viewModel, showSign = true, valuesHidden = valuesHidden),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = accentColor
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(14.dp))

            // Annualized Return badge row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Annualized Return",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Return formula tooltip",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = "Modified Dietz Method",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = String.format("%s%.2f%%", if (metrics.annualizedReturnPercent >= 0) "+" else "", metrics.annualizedReturnPercent),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }
        }
    }
}

@Composable
fun MetricWidgetCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    supportingText: String
) {
    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = accentColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PassiveIncomeRunRateWidget(
    metrics: AggregatedPerformanceMetrics,
    baseCurrency: String,
    viewModel: NetWorthViewModel,
    valuesHidden: Boolean
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE8F5E9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Savings,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Passive Income Run-Rate",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Calculated real-world yield metrics",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Monthly Avg Income",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatPortfolioAmount(metrics.averageMonthlyIncomeUsd, baseCurrency, viewModel, valuesHidden = valuesHidden),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF2E7D32)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Annualized Run-Rate",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatPortfolioAmount(metrics.averageYearlyIncomeUsd, baseCurrency, viewModel, valuesHidden = valuesHidden),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }
    }
}

@Composable
fun ValuationTrendChart(points: List<PerformanceChartPoint>) {
    if (points.isEmpty()) return
    
    val prices = points.map { it.netWorthUsd }
    val minVal = prices.minOrNull() ?: 0.0
    val maxVal = prices.maxOrNull() ?: 1.0
    val delta = if (maxVal - minVal > 0.0) maxVal - minVal else 1.0

    // Custom high-contrast line chart
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val w = size.width
        val h = size.height

        val leftPad = 12f
        val rightPad = 12f
        val topPad = 16f
        val bottomPad = 16f

        val chartW = w - leftPad - rightPad
        val chartH = h - topPad - bottomPad

        // Draw horizontal grid lines (3 sections)
        for (i in 0..2) {
            val gridY = topPad + i * (chartH / 2f)
            drawLine(
                color = labelColor.copy(alpha = 0.12f),
                start = Offset(leftPad, gridY),
                end = Offset(w - rightPad, gridY),
                strokeWidth = 2f
            )
        }

        // Project trace line coordinates
        val drawPoints = mutableListOf<Offset>()
        for (idx in points.indices) {
            val x = leftPad + idx * (chartW / (points.size - 1))
            val percentY = (points[idx].netWorthUsd - minVal) / delta
            val y = h - bottomPad - percentY.toFloat() * chartH
            drawPoints.add(Offset(x, y))
        }

        // Draw smooth path trend
        val path = Path().apply {
            if (drawPoints.isNotEmpty()) {
                moveTo(drawPoints[0].x, drawPoints[0].y)
                for (idx in 1 until drawPoints.size) {
                    lineTo(drawPoints[idx].x, drawPoints[idx].y)
                }
            }
        }

        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 6f, cap = StrokeCap.Round)
        )

        // Draw soft background gradient underneath the curve
        val gradientPath = Path().apply {
            if (drawPoints.isNotEmpty()) {
                moveTo(drawPoints[0].x, h - bottomPad)
                for (idx in drawPoints.indices) {
                    lineTo(drawPoints[idx].x, drawPoints[idx].y)
                }
                lineTo(drawPoints.last().x, h - bottomPad)
                close()
            }
        }

        drawPath(
            path = gradientPath,
            brush = Brush.verticalGradient(
                colors = listOf(primaryColor.copy(alpha = 0.25f), Color.Transparent),
                startY = topPad,
                endY = h - bottomPad
            )
        )

        // Highlight endpoints
        if (drawPoints.isNotEmpty()) {
            drawCircle(
                color = primaryColor,
                radius = 12f,
                center = drawPoints.last()
            )
            drawCircle(
                color = Color.White,
                radius = 6f,
                center = drawPoints.last()
            )
            drawCircle(
                color = primaryColor,
                radius = 10f,
                center = drawPoints[0]
            )
            drawCircle(
                color = Color.White,
                radius = 5f,
                center = drawPoints[0]
            )
        }
    }
    
    // Bottom legend endpoints info
    val form = SimpleDateFormat("MMM d", Locale.getDefault())
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = form.format(Date(points.first().timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor
        )
        Text(
            text = form.format(Date(points.last().timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor
        )
    }
}

@Composable
fun MonthlyPlBarChart(
    bars: List<MonthlyPerformanceBar>,
    baseCurrency: String,
    viewModel: NetWorthViewModel,
    valuesHidden: Boolean
) {
    if (bars.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Calculating organic metrics...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val maxAbsVal = bars.map { Math.abs(it.profitLossUsd) }.maxOrNull() ?: 1.0
    val divisor = if (maxAbsVal > 0.0) maxAbsVal else 1.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEach { bar ->
            val positive = bar.profitLossUsd >= 0
            val fraction = (Math.abs(bar.profitLossUsd) / divisor).toFloat().coerceIn(0.04f, 1.0f)
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                // Top label formatted using base currency formatAbbreviated
                Text(
                    text = formatAbbreviated(bar.profitLossUsd, baseCurrency, viewModel, valuesHidden),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (positive) Color(0xFF2E7D32) else Color(0xFFC62828),
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                // The bar column itself representing metric
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .weight(1f, fill = false)
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (positive) Color(0xFF4CAF50) else Color(0xFFEF5350))
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Month label underneath
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun MonthlyIncomeBarChart(
    bars: List<MonthlyPerformanceBar>,
    baseCurrency: String,
    viewModel: NetWorthViewModel,
    valuesHidden: Boolean
) {
    if (bars.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Calculating income metrics...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val maxVal = bars.map { it.profitLossUsd }.maxOrNull() ?: 1.0
    val divisor = if (maxVal > 0.0) maxVal else 1.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEach { bar ->
            val fraction = (bar.profitLossUsd / divisor).toFloat().coerceIn(0.04f, 1.0f)
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                // Top label formatted using base currency formatAbbreviated
                Text(
                    text = formatAbbreviated(bar.profitLossUsd, baseCurrency, viewModel, valuesHidden),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                // The bar column itself representing metric
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .weight(1f, fill = false)
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Month label underneath
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun TopAssetItemRow(
    rank: Int,
    ranking: AssetPerformanceRanking,
    baseCurrency: String,
    viewModel: NetWorthViewModel,
    valuesHidden: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank circle badge
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Asset details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ranking.asset.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = ranking.categoryName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Profit values details formatted using base currency formatPortfolioAmount and formatAbbreviated
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatPortfolioAmount(ranking.absoluteGainUsd, baseCurrency, viewModel, showSign = true, valuesHidden = valuesHidden),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32)
            )
            Text(
                text = String.format("+%.1f%%", ranking.gainPercent),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2E7D32)
            )
        }
    }
}

@Composable
fun AssetTableHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Asset Name",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Net Dep",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.1f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Current",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.1f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "P/L Val",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.1f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "P/L %",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.9f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AssetItemTableRow(
    performance: DetailedAssetPerformance,
    baseCurrency: String,
    viewModel: NetWorthViewModel,
    valuesHidden: Boolean
) {
    val formattedNetDep = formatAbbreviated(performance.netDepositUsd, baseCurrency, viewModel, valuesHidden)
    val formattedCurrent = formatAbbreviated(performance.currentValueUsd, baseCurrency, viewModel, valuesHidden)
    
    val plVal = performance.plUsd
    val formattedPlVal = (if (plVal > 0) "+" else "") + formatAbbreviated(plVal, baseCurrency, viewModel, valuesHidden)
    
    val isPositive = plVal >= 0
    val plColor = if (isPositive) Color(0xFF2E7D32) else Color(0xFFC62828)
    val plPercentStr = String.format(Locale.US, "%s%.1f%%", if (isPositive) "+" else "", performance.plPercent)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = performance.asset.name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formattedNetDep,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.1f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formattedCurrent,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.1f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formattedPlVal,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = plColor,
            modifier = Modifier.weight(1.1f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = plPercentStr,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = plColor,
            modifier = Modifier.weight(0.9f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AssetClassDirectoryWidget(
    performance: AggregatedPerformanceMetrics,
    baseCurrency: String,
    viewModel: NetWorthViewModel,
    valuesHidden: Boolean
) {
    val groupedPerformances = remember(performance.allAssetsPerformance) {
        performance.allAssetsPerformance.groupBy { it.categoryName }
    }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Asset Class Directory",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (groupedPerformances.isEmpty()) {
                Text(
                    text = "No assets are registered in this performance timeline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                groupedPerformances.keys.forEachIndexed { groupIdx, assetClass ->
                    val assetsInGroup = groupedPerformances[assetClass].orEmpty()
                    val isExpanded = expandedGroups[assetClass] ?: false

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                )
                                .clickable { expandedGroups[assetClass] = !isExpanded }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = assetClass,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${assetsInGroup.size} Asset${if (assetsInGroup.size > 1) "s" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val totalVal = assetsInGroup.sumOf { it.currentValueUsd }
                                Text(
                                    text = formatAbbreviated(totalVal, baseCurrency, viewModel, valuesHidden),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = isExpanded,
                            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 12.dp)
                            ) {
                                AssetTableHeaderRow()
                                Spacer(modifier = Modifier.height(4.dp))
                                assetsInGroup.forEachIndexed { idx, perf ->
                                    AssetItemTableRow(
                                        performance = perf,
                                        baseCurrency = baseCurrency,
                                        viewModel = viewModel,
                                        valuesHidden = valuesHidden
                                    )
                                    if (idx < assetsInGroup.size - 1) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (groupIdx < groupedPerformances.size - 1) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

// Math valuation projection helpers
private fun calculateAssetValuationAt(
    targetTimestamp: Long,
    asset: Asset,
    assetTxsAll: List<Transaction>,
    categories: List<AssetCategory>,
    rates: Map<String, Double>
): Double {
    val cat = categories.find { it.id == asset.categoryId }
    val isAssetType = cat?.isAsset ?: true

    val assetTxs = assetTxsAll.filter {
        (it.assetId == asset.id || it.destinationAssetId == asset.id) && it.timestamp <= targetTimestamp
    }.sortedWith(compareBy<Transaction> { it.timestamp }.thenBy { it.id })

    if (assetTxs.isEmpty()) {
        return 0.0
    }

    // 1. Calculate Net Deposits and Income up to targetTimestamp
    var netDepositsNative = 0.0
    var totalIncomeNative = 0.0

    val firstUpdate = assetTxs.firstOrNull { it.type == "UPDATE" }
    val depositsBeforeUpdate = if (firstUpdate != null) {
        assetTxs.filter { 
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

    for (tx in assetTxs) {
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

    // 2. Calculate Current Valuation based on latest "UPDATE" or rolling ledger logic
    val updateTxs = assetTxs.filter { it.assetId == asset.id && it.type == "UPDATE" }
        .sortedWith(compareBy<Transaction> { it.timestamp }.thenBy { it.id })
    
    val latestUpdate = updateTxs.lastOrNull()
    val latestUpdateTimestamp = latestUpdate?.timestamp ?: 0L
    val baseValuation = latestUpdate?.amount ?: 0.0

    var additionsAfterUpdate = 0.0
    val transactionsAfterUpdate = if (latestUpdate != null) {
        assetTxs.filter { tx -> tx.timestamp > latestUpdate.timestamp || (tx.timestamp == latestUpdate.timestamp && tx.id > latestUpdate.id) }
    } else {
        assetTxs
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

    val valuationNative = if (latestUpdate == null) {
        netDepositsNative + totalIncomeNative
    } else {
        baseValuation + additionsAfterUpdate
    }

    return valuationNative
}

private fun calculatePortfolioNetWorthAt(
    targetTimestamp: Long,
    assets: List<Asset>,
    categories: List<AssetCategory>,
    transactions: List<Transaction>,
    rates: Map<String, Double>
): Double {
    var netWorthUsd = 0.0
    for (asset in assets) {
        val category = categories.find { it.id == asset.categoryId }
        val isAsset = category?.isAsset ?: true

        val valuationNative = calculateAssetValuationAt(targetTimestamp, asset, transactions, categories, rates)
        val valuationUsd = valuationNative * (rates[asset.currency] ?: 1.0)

        if (isAsset) {
            netWorthUsd += valuationUsd
        } else {
            netWorthUsd -= valuationUsd
        }
    }
    return netWorthUsd
}

// Format currency standard function helper
fun formatCurrency(value: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.US)
    return formatter.format(value)
}

// Compact formatting for high-density charts labels
fun formatCompactValue(value: Double): String {
    val absVal = Math.abs(value)
    val sign = if (value >= 0) "" else "-"
    return when {
        absVal >= 1_000_000.0 -> String.format("%s$%.1fM", sign, absVal / 1_000_000.0)
        absVal >= 1_000.0 -> String.format("%s$%.1fK", sign, absVal / 1_000.0)
        else -> String.format("%s$%.0f", sign, absVal)
    }
}
