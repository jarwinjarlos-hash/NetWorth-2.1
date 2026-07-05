package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Asset
import com.example.data.model.AssetCategory
import com.example.data.model.Bucket
import com.example.data.model.Transaction
import com.example.ui.viewmodel.NetWorthViewModel
import java.util.*

// Configuration state class for Retirement Bucket Strategy
data class RetirementConfig(
    val isAlreadyRetired: Boolean = false,
    val targetMonth: Int = 6, // June (1-indexed: 1 = Jan, 12 = Dec)
    val targetYear: Int = 2035,
    val expectedYearlyExpenses: Double = 60000.0, // Entered in base currency of app
    val inflationRate: Double = 2.5,
    val actualMonthlySavings: Double = 500.0, // Entered in base currency of app
    val bucketMappings: Map<Int, Int> = emptyMap(), // bucketId -> strategy bucket (1, 2, or 3)
    val horizonB1: Int = 5,
    val horizonB2: Int = 5,
    val horizonB3: Int = 20,
    val returnB1: Double = 3.0,
    val returnB2: Double = 5.5,
    val returnB3: Double = 8.5
)

// Monte Carlo simulator results
data class MonteCarloResult(
    val successRate: Double,
    val successRateInt: Int,
    val averageEndingBalanceUsd: Double,
    val failedYearAverage: Double?
)

/**
 * 4. ADVANCED FEATURE: MONTE CARLO STRESS TEST
 * Runs a 150-iteration simulation projecting compound asset growths paired with randomized yield distributions.
 * Implements Box-Muller normal variance modeling matching standard deviations per bucket.
 */
fun runMonteCarloSimulation(
    config: RetirementConfig,
    currentB1: Double,
    currentB2: Double,
    currentB3: Double,
    targetB1: Double,
    targetB2: Double,
    targetB3: Double,
    pmtB1: Double,
    pmtB2: Double,
    pmtB3: Double,
    yearsToRetire: Double,
    expectedYearlyExpensesUsd: Double,
    actualMonthlySavingsUsd: Double
): MonteCarloResult {
    val iterations = 150
    var successes = 0
    val totalRetirementYears = config.horizonB1 + config.horizonB2 + config.horizonB3
    
    val monthsToRetire = yearsToRetire * 12.0
    
    // Annual returns to raw monthly standard values during accumulation
    val accR1 = config.returnB1 / 100.0 / 12.0
    val accR2 = config.returnB2 / 100.0 / 12.0
    val accR3 = config.returnB3 / 100.0 / 12.0

    // Distribute actual monthly savings proportionally to recommended payments if total recommended payments > 0,
    // otherwise split equally or proportional to some standard metric.
    val totalRecommendedPmt = pmtB1 + pmtB2 + pmtB3
    val actualSavings = actualMonthlySavingsUsd
    
    val actualPmtB1 = if (totalRecommendedPmt > 0.0) actualSavings * (pmtB1 / totalRecommendedPmt) else actualSavings / 3.0
    val actualPmtB2 = if (totalRecommendedPmt > 0.0) actualSavings * (pmtB2 / totalRecommendedPmt) else actualSavings / 3.0
    val actualPmtB3 = if (totalRecommendedPmt > 0.0) actualSavings * (pmtB3 / totalRecommendedPmt) else actualSavings / 3.0

    // Compute Projected Starting Balances on Day 1 of Retirement
    val startB1 = currentB1 * Math.pow(1.0 + accR1, monthsToRetire) + 
            (if (accR1 > 0.0) actualPmtB1 * (Math.pow(1.0 + accR1, monthsToRetire) - 1.0) / accR1 else actualPmtB1 * monthsToRetire)
    
    val startB2 = currentB2 * Math.pow(1.0 + accR2, monthsToRetire) + 
            (if (accR2 > 0.0) actualPmtB2 * (Math.pow(1.0 + accR2, monthsToRetire) - 1.0) / accR2 else actualPmtB2 * monthsToRetire)
            
    val startB3 = currentB3 * Math.pow(1.0 + accR3, monthsToRetire) + 
            (if (accR3 > 0.0) actualPmtB3 * (Math.pow(1.0 + accR3, monthsToRetire) - 1.0) / accR3 else actualPmtB3 * monthsToRetire)

    val inflationFraction = config.inflationRate / 100.0
    val day1Expenses = expectedYearlyExpensesUsd * Math.pow(1.0 + inflationFraction, yearsToRetire)
    
    // Function to draw random normal value using Box-Muller polar method
    fun drawNormal(mean: Double, stdDev: Double): Double {
        val u1 = Math.random()
        val u2 = Math.random()
        val z = Math.sqrt(-2.0 * Math.log(if (u1 <= 0.0) 1e-9 else u1)) * Math.cos(2.0 * Math.PI * u2)
        return mean + stdDev * z
    }

    var totalEndingBalanceSum = 0.0
    var failedYearsSum = 0.0
    var failCount = 0

    for (i in 1..iterations) {
        var b1 = maxOf(0.0, startB1)
        var b2 = maxOf(0.0, startB2)
        var b3 = maxOf(0.0, startB3)
        var survived = true
        var failYear = 0
        
        for (y in 1..totalRetirementYears) {
            // Draw returns for this year
            val r1 = drawNormal(config.returnB1 / 100.0, 0.015) // small stddev for cash/immediate
            val r2 = drawNormal(config.returnB2 / 100.0, 0.050) // medium stddev for income
            val r3 = drawNormal(config.returnB3 / 100.0, 0.160) // high stddev for growth

            // Inflation-compounded expenses for this year
            val requiredExpense = day1Expenses * Math.pow(1.0 + inflationFraction, (y - 1).toDouble())

            // Spend from B1, then B2, then B3
            var remainingExpense = requiredExpense
            
            // Spend from B1
            if (b1 >= remainingExpense) {
                b1 -= remainingExpense
                remainingExpense = 0.0
            } else {
                remainingExpense -= b1
                b1 = 0.0
            }

            // Spend from B2
            if (remainingExpense > 0.0) {
                if (b2 >= remainingExpense) {
                    b2 -= remainingExpense
                    remainingExpense = 0.0
                } else {
                    remainingExpense -= b2
                    b2 = 0.0
                }
            }

            // Spend from B3
            if (remainingExpense > 0.0) {
                if (b3 >= remainingExpense) {
                    b3 -= remainingExpense
                    remainingExpense = 0.0
                } else {
                    remainingExpense -= b3
                    b3 = 0.0
                }
            }

            // If there's still un-funded expense, total portfolio is exhausted!
            if (remainingExpense > 0.0 || (b1 + b2 + b3) <= 0.0) {
                survived = false
                failYear = y
                break
            }

            // Grow remaining balances
            b1 *= (1.0 + r1)
            b2 *= (1.0 + r2)
            b3 *= (1.0 + r3)

            if ((b1 + b2 + b3) <= 0.0) {
                survived = false
                failYear = y
                break
            }
        }

        if (survived) {
            successes++
            totalEndingBalanceSum += (b1 + b2 + b3)
        } else {
            failCount++
            failedYearsSum += failYear
        }
    }

    val successRate = (successes.toDouble() / iterations) * 100.0
    val averageEndingBalance = if (successes > 0) totalEndingBalanceSum / successes else 0.0
    val failedYearAverage = if (failCount > 0) failedYearsSum / failCount else null

    return MonteCarloResult(
        successRate = successRate,
        successRateInt = successRate.toInt(),
        averageEndingBalanceUsd = averageEndingBalance,
        failedYearAverage = failedYearAverage
    )
}

/**
 * Persists the retirement parameters configuration in secure local SharedPreferences.
 */
fun loadRetirementConfig(context: Context): RetirementConfig {
    val prefs = context.getSharedPreferences("retirement_strategy_prefs", Context.MODE_PRIVATE)
    val mappingStr = prefs.getString("bucket_mappings", "") ?: ""
    val bucketMappings = mutableMapOf<Int, Int>()
    if (mappingStr.isNotEmpty()) {
        mappingStr.split(",").forEach { pair ->
            val parts = pair.split(":")
            if (parts.size == 2) {
                val bId = parts[0].toIntOrNull()
                val sId = parts[1].toIntOrNull()
                if (bId != null && sId != null) {
                    bucketMappings[bId] = sId
                }
            }
        }
    }

    return RetirementConfig(
        isAlreadyRetired = prefs.getBoolean("is_already_retired", false),
        targetMonth = prefs.getInt("target_month", 6),
        targetYear = prefs.getInt("target_year", 2035),
        expectedYearlyExpenses = prefs.getFloat("expected_yearly_expenses", 60000f).toDouble(),
        inflationRate = prefs.getFloat("inflation_rate", 2.5f).toDouble(),
        actualMonthlySavings = prefs.getFloat("actual_monthly_savings", 500f).toDouble(),
        bucketMappings = bucketMappings,
        horizonB1 = prefs.getInt("horizon_b1", 5),
        horizonB2 = prefs.getInt("horizon_b2", 5),
        horizonB3 = prefs.getInt("horizon_b3", 20),
        returnB1 = prefs.getFloat("return_b1", 3.0f).toDouble(),
        returnB2 = prefs.getFloat("return_b2", 5.5f).toDouble(),
        returnB3 = prefs.getFloat("return_b3", 8.5f).toDouble()
    )
}

fun saveRetirementConfig(context: Context, config: RetirementConfig) {
    val prefs = context.getSharedPreferences("retirement_strategy_prefs", Context.MODE_PRIVATE)
    val mappingStr = config.bucketMappings.map { "${it.key}:${it.value}" }.joinToString(",")
    prefs.edit()
        .putBoolean("is_already_retired", config.isAlreadyRetired)
        .putInt("target_month", config.targetMonth)
        .putInt("target_year", config.targetYear)
        .putFloat("expected_yearly_expenses", config.expectedYearlyExpenses.toFloat())
        .putFloat("inflation_rate", config.inflationRate.toFloat())
        .putFloat("actual_monthly_savings", config.actualMonthlySavings.toFloat())
        .putString("bucket_mappings", mappingStr)
        .putInt("horizon_b1", config.horizonB1)
        .putInt("horizon_b2", config.horizonB2)
        .putInt("horizon_b3", config.horizonB3)
        .putFloat("return_b1", config.returnB1.toFloat())
        .putFloat("return_b2", config.returnB2.toFloat())
        .putFloat("return_b3", config.returnB3.toFloat())
        .apply()
}

/**
 * Currency conversion internal utilities translating values between Base Currency and USD.
 */
fun convertBaseToUsd(amountBase: Double, baseCurrency: String, viewModel: NetWorthViewModel): Double {
    if (baseCurrency == "USD") return amountBase
    val rate = viewModel.exchangeRates[baseCurrency] ?: 1.0
    return amountBase * rate
}

fun convertUsdToBase(amountUsd: Double, baseCurrency: String, viewModel: NetWorthViewModel): Double {
    if (baseCurrency == "USD") return amountUsd
    val rate = viewModel.exchangeRates[baseCurrency] ?: 1.0
    return amountUsd / rate
}

// Local duplicate helper formatting compliant with NetWorthDashboard standard
private fun formatLocalAssetCurrency(
    amount: Double,
    currencyCode: String,
    valuesHidden: Boolean = false
): String {
    val symbol = when (currencyCode) {
        "PHP" -> "₱"
        "SAR" -> "SR"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        "AUD" -> "A$"
        "CAD" -> "C$"
        else -> currencyCode
    }
    val prefix = if (symbol == currencyCode) "" else symbol
    val suffix = if (symbol == currencyCode) " $currencyCode" else ""
    val formattedNum = if (valuesHidden) "••••" else String.format("%,.0f", amount)
    return "$prefix$formattedNum$suffix"
}

@Composable
fun RetirementStrategyTrackerView(
    viewModel: NetWorthViewModel,
    transactions: List<Transaction>,
    assets: List<Asset>,
    categories: List<AssetCategory>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()
    val valuesHidden by viewModel.valuesHidden.collectAsStateWithLifecycle()
    val bucketMetrics by viewModel.bucketMetrics.collectAsStateWithLifecycle()

    var config by remember { mutableStateOf(loadRetirementConfig(context)) }
    var activeTab by remember { mutableStateOf(0) } // 0 = Dashboard, 1 = Config Sandbox

    fun updateConfig(newConfig: RetirementConfig) {
        config = newConfig
        saveRetirementConfig(context, newConfig)
    }

    // Top Header & Tab switcher
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .testTag("retirement_back_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "Retirement Bucket Strategy",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Dynamic Config toggle icon
            IconButton(
                onClick = { activeTab = if (activeTab == 0) 1 else 0 },
                modifier = Modifier
                    .background(
                        color = if (activeTab == 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        shape = CircleShape
                    )
                    .testTag("retirement_config_toggle")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = if (activeTab == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Active Theme/Navigation Tabs
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                modifier = Modifier.testTag("tab_dashboard")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Spa, contentDescription = null)
                    Text("Plan Dashboard", fontWeight = FontWeight.Bold)
                }
            }
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                modifier = Modifier.testTag("tab_config")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                    Text("Scenario Sandbox", fontWeight = FontWeight.Bold)
                }
            }
        }

        // ROUTING states based on Retirement status
        if (config.isAlreadyRetired) {
            PostRetirementDrawdownPlaceholder(
                config = config,
                viewModel = viewModel,
                bucketMetrics = bucketMetrics,
                baseCurrency = baseCurrency,
                valuesHidden = valuesHidden,
                onConfigChanged = { updateConfig(it) },
                onModifyStatus = {
                    updateConfig(config.copy(isAlreadyRetired = false))
                }
            )
        } else {
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    slideInHorizontally { width -> if (targetState > initialState) width else -width } togetherWith
                            slideOutHorizontally { width -> if (targetState > initialState) -width else width }
                },
                label = "retirement_tabs"
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> PreRetirementDashboardView(
                        config = config,
                        viewModel = viewModel,
                        bucketMetrics = bucketMetrics,
                        baseCurrency = baseCurrency,
                        valuesHidden = valuesHidden,
                        onGotoSandbox = { activeTab = 1 }
                    )
                    1 -> RetirementSandboxScreen(
                        config = config,
                        viewModel = viewModel,
                        bucketMetrics = bucketMetrics,
                        baseCurrency = baseCurrency,
                        onConfigChanged = { updateConfig(it) }
                    )
                }
            }
        }
    }
}

/**
 * 2. POST-RETIREMENT DRAWDOWN VIEW (Professional Drawdown Strategy & Analytics Center)
 */
@Composable
fun PostRetirementDrawdownPlaceholder(
    config: RetirementConfig,
    viewModel: NetWorthViewModel,
    bucketMetrics: List<com.example.ui.viewmodel.BucketWithMetrics>,
    baseCurrency: String,
    valuesHidden: Boolean,
    onConfigChanged: (RetirementConfig) -> Unit,
    onModifyStatus: () -> Unit
) {
    // Current date logic
    val currentCal = Calendar.getInstance()
    val currYear = currentCal.get(Calendar.YEAR)
    val currMonth = currentCal.get(Calendar.MONTH) + 1

    // A. CURRENT AGGREGATE BALANCES FROM DETAILED PORTFOLIO METRICS
    val effectiveMappings = if (config.bucketMappings.isEmpty()) {
        val fallback = mutableMapOf<Int, Int>()
        bucketMetrics.forEach { bWithMetrics ->
            val bucket = bWithMetrics.bucket
            if (bucket != null) {
                val nameLower = bucket.name.lowercase()
                val assignedId = when {
                    nameLower.contains("cash") || nameLower.contains("emergency") || nameLower.contains("immediate") || nameLower.contains("bank") || nameLower.contains("liquid") || nameLower.contains("checking") || nameLower.contains("saving") -> 1
                    nameLower.contains("bond") || nameLower.contains("income") || nameLower.contains("conservative") || nameLower.contains("fixed") || nameLower.contains("medium") -> 2
                    nameLower.contains("growth") || nameLower.contains("stock") || nameLower.contains("equity") || nameLower.contains("long") || nameLower.contains("retirement") || nameLower.contains("investment") || nameLower.contains("crypto") -> 3
                    else -> {
                        val index = bucketMetrics.indexOf(bWithMetrics)
                        if (index % 3 == 0) 1 else if (index % 3 == 1) 2 else 3
                    }
                }
                fallback[bucket.id] = assignedId
            } else {
                fallback[-1] = 1
            }
        }
        fallback
    } else {
        config.bucketMappings
    }

    var currentB1 = 0.0
    var currentB2 = 0.0
    var currentB3 = 0.0

    bucketMetrics.forEach { bWithMetrics ->
        val bId = bWithMetrics.bucket?.id ?: -1
        when (effectiveMappings[bId]) {
            1 -> currentB1 += bWithMetrics.totalValuationUsd
            2 -> currentB2 += bWithMetrics.totalValuationUsd
            3 -> currentB3 += bWithMetrics.totalValuationUsd
        }
    }

    val currentB1Base = convertUsdToBase(currentB1, baseCurrency, viewModel)
    val currentB2Base = convertUsdToBase(currentB2, baseCurrency, viewModel)
    val currentB3Base = convertUsdToBase(currentB3, baseCurrency, viewModel)
    val totalPortfolioBase = currentB1Base + currentB2Base + currentB3Base

    // B. METRIC COMPUTATIONS
    val expectedYearlyExpenses = config.expectedYearlyExpenses
    val initialWithdrawalRate = if (totalPortfolioBase > 0.0) (expectedYearlyExpenses / totalPortfolioBase) * 100.0 else 0.0
    val cashBufferRunwayMonths = if (expectedYearlyExpenses > 0.0) (currentB1Base / (expectedYearlyExpenses / 12.0)) else 0.0
    val conservedHorizonYears = if (expectedYearlyExpenses > 0.0) (currentB1Base + currentB2Base) / expectedYearlyExpenses else 0.0

    // C. INTERACTIVE SIMULATION STATE
    var portfolioChangePercent by remember { mutableStateOf(0f) }
    var simulatedScenario by remember { mutableStateOf(1) } // 0 = Bear Market / Downmarket, 1 = Normal, 2 = Bull Market / Skimming

    val simulatedPortfolioBase = totalPortfolioBase * (1.0 + portfolioChangePercent / 100.0)
    val simulatedWithdrawalRate = if (simulatedPortfolioBase > 0.0) (expectedYearlyExpenses / simulatedPortfolioBase) * 100.0 else 0.0

    // Format helpers
    val formattedExpenses = formatLocalAssetCurrency(expectedYearlyExpenses, baseCurrency, false)
    val formattedTotalPortfolio = formatLocalAssetCurrency(totalPortfolioBase, baseCurrency, valuesHidden)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome and intro card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Spa,
                            contentDescription = "Active Retirement",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Active Drawdown Dashboard",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "The 3-Bucket System is actively protecting your wealth, managing withdrawals and preventing forced liquidations of your equities during market downturns.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section: Total Portfolio Values
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Current 3-Bucket Allocation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Total Retirement Net Worth",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formattedTotalPortfolio,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Annual Drawdown Needs: $formattedExpenses",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Horizontal distribution chart
                val totalB = if (totalPortfolioBase > 0.0) totalPortfolioBase else 1.0
                val b1Pct = (currentB1Base / totalB).toFloat()
                val b2Pct = (currentB2Base / totalB).toFloat()
                val b3Pct = (currentB3Base / totalB).toFloat()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (b1Pct > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(maxOf(0.01f, b1Pct))
                                .fillMaxHeight()
                                .background(Color(0xFF10B981), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                        )
                    }
                    if (b2Pct > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(maxOf(0.01f, b2Pct))
                                .fillMaxHeight()
                                .background(Color(0xFF3B82F6))
                        )
                    }
                    if (b3Pct > 0f) {
                        val endRadius = if (b3Pct == 1f) 12.dp else 0.dp
                        Box(
                            modifier = Modifier
                                .weight(maxOf(0.01f, b3Pct))
                                .fillMaxHeight()
                                .background(Color(0xFF8B5CF6), RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                        )
                    }
                }

                // Bucket Details Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Bucket 1
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), CircleShape))
                            Text("B1 Cash Buffer", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = formatLocalAssetCurrency(currentB1Base, baseCurrency, valuesHidden),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format("%.1f%%", b1Pct * 100),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Bucket 2
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFF3B82F6), CircleShape))
                            Text("B2 Yield Income", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = formatLocalAssetCurrency(currentB2Base, baseCurrency, valuesHidden),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format("%.1f%%", b2Pct * 100),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Bucket 3
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFF8B5CF6), CircleShape))
                            Text("B3 Growth", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = formatLocalAssetCurrency(currentB3Base, baseCurrency, valuesHidden),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format("%.1f%%", b3Pct * 100),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section: Critical Professional Metrics Grid
        Text(
            text = "Professional Drawdown Metrics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Withdrawal Rate
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Withdrawal Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (valuesHidden) "••••" else String.format("%.2f%%", initialWithdrawalRate),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    val safetyText = when {
                        initialWithdrawalRate <= 0.0 -> "No Drawdown"
                        initialWithdrawalRate < 3.5 -> "Ultra Safe"
                        initialWithdrawalRate <= 4.5 -> "Safe Range"
                        initialWithdrawalRate <= 5.5 -> "Moderate Risk"
                        else -> "Danger / Reduce"
                    }
                    val safetyColor = when {
                        initialWithdrawalRate <= 4.5 -> Color(0xFF10B981)
                        initialWithdrawalRate <= 5.5 -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    }
                    Text(
                        text = safetyText,
                        style = MaterialTheme.typography.labelSmall,
                        color = safetyColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Card 2: Cash Buffer Months
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(imageVector = Icons.Default.HourglassEmpty, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Cash Buffer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (valuesHidden) "••••" else String.format("%.1f Mo", cashBufferRunwayMonths),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    val bufferText = when {
                        cashBufferRunwayMonths < 12.0 -> "Undersized"
                        cashBufferRunwayMonths < 24.0 -> "Adequate"
                        cashBufferRunwayMonths <= 48.0 -> "Optimal Horizon"
                        else -> "Growth Drag"
                    }
                    val bufferColor = when {
                        cashBufferRunwayMonths < 12.0 -> Color(0xFFEF4444)
                        cashBufferRunwayMonths < 24.0 -> Color(0xFFF59E0B)
                        cashBufferRunwayMonths <= 48.0 -> Color(0xFF10B981)
                        else -> Color(0xFF3B82F6)
                    }
                    Text(
                        text = bufferText,
                        style = MaterialTheme.typography.labelSmall,
                        color = bufferColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Card 3: Conserved Horizon
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Protected Years", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (valuesHidden) "••••" else String.format("%.1f Yrs", conservedHorizonYears),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                    val horizonText = when {
                        conservedHorizonYears < 3.0 -> "Low Shield"
                        conservedHorizonYears < 5.0 -> "Moderate"
                        conservedHorizonYears <= 10.0 -> "Optimal Guard"
                        else -> "Heavy Shield"
                    }
                    val horizonColor = when {
                        conservedHorizonYears < 3.0 -> Color(0xFFEF4444)
                        conservedHorizonYears < 5.0 -> Color(0xFFF59E0B)
                        conservedHorizonYears <= 10.0 -> Color(0xFF10B981)
                        else -> Color(0xFF10B981)
                    }
                    Text(
                        text = horizonText,
                        style = MaterialTheme.typography.labelSmall,
                        color = horizonColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Section: Live Guardrails & Market Simulator
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.OfflineBolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Dynamic Guardrail Simulator",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Text(
                    text = "A professional drawdown plan contains rules that adapt to portfolio swings. Drag the slider to simulate portfolio valuation swings (-40% to +40%) and see Guyton-Klinger rules trigger live:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Slider Row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format("Portfolio Swing: %+.0f%%", portfolioChangePercent),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (valuesHidden) "••••" else "New Total: ${formatLocalAssetCurrency(simulatedPortfolioBase, baseCurrency, false)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = portfolioChangePercent,
                        onValueChange = { portfolioChangePercent = it },
                        valueRange = -40f..40f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("-40% (Severe Crash)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF4444))
                        Text("0% (Baseline)", style = MaterialTheme.typography.labelSmall)
                        Text("+40% (High Bull)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
                    }
                }

                // Simulated Rate & Safety Status Card
                val (alertTitle, alertDesc, alertColor, alertBg, alertIcon) = when {
                    simulatedWithdrawalRate <= 0.0 -> {
                        GuardrailUIStatus(
                            "Stable",
                            "Setup your Expected Yearly Expenses in settings to run active drawdown simulations.",
                            Color.Gray,
                            MaterialTheme.colorScheme.surfaceVariant,
                            Icons.Default.Info
                        )
                    }
                    simulatedWithdrawalRate >= initialWithdrawalRate * 1.2 || simulatedWithdrawalRate >= 5.5 -> {
                        GuardrailUIStatus(
                            "Capital Preservation Guardrail Activated!",
                            "Your current withdrawal rate has spiked to ${String.format("%.2f%%", simulatedWithdrawalRate)} due to simulated portfolio depreciation. The Capital Preservation Guardrail recommends a temporary 10% reduction in retirement spending to shield the remaining balance from permanent compound depletion.",
                            Color(0xFFEF4444),
                            Color(0xFFFEF2F2),
                            Icons.Default.Warning
                        )
                    }
                    simulatedWithdrawalRate <= initialWithdrawalRate * 0.8 && initialWithdrawalRate > 0.0 && simulatedWithdrawalRate < 3.2 -> {
                        GuardrailUIStatus(
                            "Prosperity Rule Triggered!",
                            "Stellar growth has pushed your withdrawal rate down to a safe ${String.format("%.2f%%", simulatedWithdrawalRate)}. Professional standards indicate you can safely increase your lifestyle spending by 10% to enjoy surplus returns without risking the longevity of your portfolio.",
                            Color(0xFF10B981),
                            Color(0xFFECFDF5),
                            Icons.Default.TrendingUp
                        )
                    }
                    else -> {
                        GuardrailUIStatus(
                            "Drawdown Corridor Stable",
                            "The simulated withdrawal rate is ${String.format("%.2f%%", simulatedWithdrawalRate)}. Spending remains fully within the optimal safety boundaries. No spending adjustments required.",
                            Color(0xFF3B82F6),
                            Color(0xFFEFF6FF),
                            Icons.Default.CheckCircle
                        )
                    }
                }

                Surface(
                    color = alertBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, alertColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = alertIcon, contentDescription = null, tint = alertColor, modifier = Modifier.size(24.dp))
                        Column {
                            Text(
                                text = alertTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = alertColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = alertDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Section: Withdrawal Source & Refill (Skimming) Guardrails
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Withdrawal Source & Skimming Rules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Text(
                    text = "A hallmark of the 3-Bucket Strategy is changing WHERE withdrawals are pulled based on current market behavior. Select a simulated market year to see the guardrails in action:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Selector Tabs for Market States
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TabRow(
                        selectedTabIndex = simulatedScenario,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        indicator = {}
                    ) {
                    Tab(
                        selected = simulatedScenario == 0,
                        onClick = { simulatedScenario = 0 },
                        text = { Text("Bear Market", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = simulatedScenario == 1,
                        onClick = { simulatedScenario = 1 },
                        text = { Text("Normal Year", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = simulatedScenario == 2,
                        onClick = { simulatedScenario = 2 },
                        text = { Text("Bull Market", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                }
            }

                // Active Rules Card
                val (scenarioTitle, scenarioRules, activeBucket, ruleDesc) = when (simulatedScenario) {
                    0 -> MarketScenarioUIStatus(
                        "Market Condition: Severe Bear Downturn (-15%)",
                        "Bucket 3 Equity Sales: BLOCKED / SUSPENDED",
                        1,
                        "Guardrail Activated: Forced Equity Liquidation Protection. Withdrawal from risky stock assets is completely halted. Retirement income is drawn 100% from Bucket 1 (Cash Reserve) and Bucket 2. This locks in zero paper losses, giving growth assets a safe runway of ${String.format("%.1f Yrs", conservedHorizonYears)} to recover."
                    )
                    1 -> MarketScenarioUIStatus(
                        "Market Condition: Steady Baseline Year (+8.5%)",
                        "Bucket 1 Cash Refills: ORGANIC INTEREST/DIVIDENDS",
                        2,
                        "Active Process: Standard Drawdown & Compound. Monthly lifestyle spending is paid from Bucket 1 cash. Bond yields from Bucket 2 and organic stock dividends from Bucket 3 are automatically harvested and directed to Bucket 1 to preserve cash reserves."
                    )
                    else -> MarketScenarioUIStatus(
                        "Market Condition: Strong Bull Expansion (+15%)",
                        "Growth Harvesting: TRIGGERED",
                        3,
                        "Guardrail Activated: Growth Skimming Rule. Since Bucket 3 returns exceeded the standard baseline (+8.5%), excess capital gains are 'skimmed' from growth holdings and reallocated to refill Bucket 1 (Cash Buffer) and Bucket 2 (Income). This lock-in of gains secures future cash reserves."
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val tint = when (activeBucket) {
                                1 -> Color(0xFFEF4444)
                                2 -> Color(0xFF3B82F6)
                                else -> Color(0xFF10B981)
                            }
                            Icon(imageVector = Icons.Default.Circle, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
                            Text(text = scenarioTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }

                        Text(
                            text = scenarioRules,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = ruleDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onModifyStatus,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("switch_to_planning_button")
            ) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Planning Sandbox", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            OutlinedButton(
                onClick = { portfolioChangePercent = 0f; simulatedScenario = 1 },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("reset_simulation_button")
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset Swing", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * 3. PRE-RETIREMENT ACCUMULATION VIEW (Dashboard view)
 */
@Composable
fun PreRetirementDashboardView(
    config: RetirementConfig,
    viewModel: NetWorthViewModel,
    bucketMetrics: List<com.example.ui.viewmodel.BucketWithMetrics>,
    baseCurrency: String,
    valuesHidden: Boolean,
    onGotoSandbox: () -> Unit
) {
    // Current date logic to compute exact Years to retirement
    val currentCal = Calendar.getInstance()
    val currYear = currentCal.get(Calendar.YEAR)
    val currMonth = currentCal.get(Calendar.MONTH) + 1 // 1-indexed

    val yearsToRetire = maxOf(0.0, (config.targetYear - currYear) + (config.targetMonth - currMonth) / 12.0)

    // A. CURRENT AGGREGATE BALANCES FROM DETAILED PORTFOLIO METRICS
    val effectiveMappings = if (config.bucketMappings.isEmpty()) {
        val fallback = mutableMapOf<Int, Int>()
        bucketMetrics.forEach { bWithMetrics ->
            val bucket = bWithMetrics.bucket
            if (bucket != null) {
                val nameLower = bucket.name.lowercase()
                val assignedId = when {
                    nameLower.contains("cash") || nameLower.contains("emergency") || nameLower.contains("immediate") || nameLower.contains("bank") || nameLower.contains("liquid") || nameLower.contains("checking") || nameLower.contains("saving") -> 1
                    nameLower.contains("bond") || nameLower.contains("income") || nameLower.contains("conservative") || nameLower.contains("fixed") || nameLower.contains("medium") -> 2
                    nameLower.contains("growth") || nameLower.contains("stock") || nameLower.contains("equity") || nameLower.contains("long") || nameLower.contains("retirement") || nameLower.contains("investment") || nameLower.contains("crypto") -> 3
                    else -> {
                        val index = bucketMetrics.indexOf(bWithMetrics)
                        if (index % 3 == 0) 1 else if (index % 3 == 1) 2 else 3
                    }
                }
                fallback[bucket.id] = assignedId
            } else {
                fallback[-1] = 1
            }
        }
        fallback
    } else {
        config.bucketMappings
    }

    var currentB1 = 0.0
    var currentB2 = 0.0
    var currentB3 = 0.0

    bucketMetrics.forEach { bWithMetrics ->
        val bId = bWithMetrics.bucket?.id ?: -1
        when (effectiveMappings[bId]) {
            1 -> currentB1 += bWithMetrics.totalValuationUsd
            2 -> currentB2 += bWithMetrics.totalValuationUsd
            3 -> currentB3 += bWithMetrics.totalValuationUsd
        }
    }

    // Convert bucket USD valuations back to base currency to keep values relative to user preference
    val currentB1Base = convertUsdToBase(currentB1, baseCurrency, viewModel)
    val currentB2Base = convertUsdToBase(currentB2, baseCurrency, viewModel)
    val currentB3Base = convertUsdToBase(currentB3, baseCurrency, viewModel)
    val totalCurrentBase = currentB1Base + currentB2Base + currentB3Base

    // B. TARGET FUNDING MATHEMATICS ( nominal compound values inflation-scaled )
    val usdRate = viewModel.exchangeRates[baseCurrency] ?: 1.0
    val targetMonthlyExpensesBase = config.expectedYearlyExpenses / 12.0

    // COMPOUND EXPENSE AT RETIREMENT COMMENCEMENT (DAY 1)
    val inflationFraction = config.inflationRate / 100.0
    val futureYearlyExpensesBase = config.expectedYearlyExpenses * Math.pow(1.0 + inflationFraction, yearsToRetire)

    // COMPUTE ANNUITIES / DISCOUNTS FOR RETIREMENT LAYERS IN NOMINAL TERMS
    // Bucket 1 (Cash/Immediate): Covers years 1 to H1
    var targetB1Base = 0.0
    val r1Fraction = config.returnB1 / 100.0
    for (t in 1..config.horizonB1) {
        val expenseAtT = futureYearlyExpensesBase * Math.pow(1.0 + inflationFraction, (t - 1).toDouble())
        targetB1Base += expenseAtT / Math.pow(1.0 + r1Fraction, t.toDouble())
    }

    // Bucket 2 (Income/Medium-term): Covers years (H1+1) to (H1+H2)
    var targetB2Base = 0.0
    val r2Fraction = config.returnB2 / 100.0
    val startYrB2 = config.horizonB1 + 1
    val endYrB2 = config.horizonB1 + config.horizonB2
    for (t in startYrB2..endYrB2) {
        val expenseAtT = futureYearlyExpensesBase * Math.pow(1.0 + inflationFraction, (t - 1).toDouble())
        targetB2Base += expenseAtT / Math.pow(1.0 + r2Fraction, t.toDouble())
    }

    // Bucket 3 (Growth/Long-term): Covers years (H1+H2+1) to (H1+H2+H3)
    var targetB3Base = 0.0
    val r3Fraction = config.returnB3 / 100.0
    val startYrB3 = config.horizonB1 + config.horizonB2 + 1
    val endYrB3 = config.horizonB1 + config.horizonB2 + config.horizonB3
    for (t in startYrB3..endYrB3) {
        val expenseAtT = futureYearlyExpensesBase * Math.pow(1.0 + inflationFraction, (t - 1).toDouble())
        targetB3Base += expenseAtT / Math.pow(1.0 + r3Fraction, t.toDouble())
    }

    val totalTargetBase = targetB1Base + targetB2Base + targetB3Base

    // C. FUNDING GAPS
    val gapB1Base = maxOf(0.0, targetB1Base - currentB1Base)
    val gapB2Base = maxOf(0.0, targetB2Base - currentB2Base)
    val gapB3Base = maxOf(0.0, targetB3Base - currentB3Base)
    val totalGapBase = gapB1Base + gapB2Base + gapB3Base

    // D. RECOMMENDED MONTHLY SAVINGS RATE (PMT formula: Future value ordinary annuity solver)
    val monthsToRetire = yearsToRetire * 12.0

    fun calculatePMTBase(gap: Double, annualReturn: Double, months: Double): Double {
        if (gap <= 0.0) return 0.0
        if (months <= 0.0) return gap // instant capital required
        val monthlyReturnFraction = (annualReturn / 100.0) / 12.0
        if (monthlyReturnFraction <= 0.0) return gap / months
        val accumulatedMultiplier = Math.pow(1.0 + monthlyReturnFraction, months)
        return gap * (monthlyReturnFraction / (accumulatedMultiplier - 1.0))
    }

    val pmtB1Base = calculatePMTBase(gapB1Base, config.returnB1, monthsToRetire)
    val pmtB2Base = calculatePMTBase(gapB2Base, config.returnB2, monthsToRetire)
    val pmtB3Base = calculatePMTBase(gapB3Base, config.returnB3, monthsToRetire)
    val totalPmtBase = pmtB1Base + pmtB2Base + pmtB3Base

    val progressPct = if (totalTargetBase > 0.0) (totalCurrentBase / totalTargetBase) * 100.0 else 0.0

    // E. MONTE CARLO SIMULATOR ENGINE EXECUTION
    // Convert current figures back to USD representing simulator core state
    val currentB1Usd = convertBaseToUsd(currentB1Base, baseCurrency, viewModel)
    val currentB2Usd = convertBaseToUsd(currentB2Base, baseCurrency, viewModel)
    val currentB3Usd = convertBaseToUsd(currentB3Base, baseCurrency, viewModel)

    val targetB1Usd = convertBaseToUsd(targetB1Base, baseCurrency, viewModel)
    val targetB2Usd = convertBaseToUsd(targetB2Base, baseCurrency, viewModel)
    val targetB3Usd = convertBaseToUsd(targetB3Base, baseCurrency, viewModel)

    val pmtB1Usd = convertBaseToUsd(pmtB1Base, baseCurrency, viewModel)
    val pmtB2Usd = convertBaseToUsd(pmtB2Base, baseCurrency, viewModel)
    val pmtB3Usd = convertBaseToUsd(pmtB3Base, baseCurrency, viewModel)

    val expectedYearlyExpensesUsd = convertBaseToUsd(config.expectedYearlyExpenses, baseCurrency, viewModel)
    val actualMonthlySavingsUsd = convertBaseToUsd(config.actualMonthlySavings, baseCurrency, viewModel)

    val simulationResult = remember(config, currentB1Usd, currentB2Usd, currentB3Usd, targetB1Usd, targetB2Usd, targetB3Usd, yearsToRetire, expectedYearlyExpensesUsd, actualMonthlySavingsUsd) {
        runMonteCarloSimulation(
            config = config,
            currentB1 = currentB1Usd,
            currentB2 = currentB2Usd,
            currentB3 = currentB3Usd,
            targetB1 = targetB1Usd,
            targetB2 = targetB2Usd,
            targetB3 = targetB3Usd,
            pmtB1 = pmtB1Usd,
            pmtB2 = pmtB2Usd,
            pmtB3 = pmtB3Usd,
            yearsToRetire = yearsToRetire,
            expectedYearlyExpensesUsd = expectedYearlyExpensesUsd,
            actualMonthlySavingsUsd = actualMonthlySavingsUsd
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // 1. DYNAMIC SUMMARY SCORECARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Accumulation Health Score",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = String.format("%.1f yrs left", yearsToRetire),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Overall progress percentage + metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (valuesHidden) "••••" else String.format("%.1f%%", progressPct),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "Total strategy funded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatLocalAssetCurrency(totalCurrentBase, baseCurrency, valuesHidden),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "Target: ${formatLocalAssetCurrency(totalTargetBase, baseCurrency, valuesHidden)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { (progressPct / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .testTag("overall_progress_bar"),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round
                )

                // Bottom summary metrics split-cards
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Total Funding Gap",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatLocalAssetCurrency(totalGapBase, baseCurrency, valuesHidden),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Rec. Monthly Savings",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatLocalAssetCurrency(totalPmtBase, baseCurrency, valuesHidden),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // 2. FINANCIAL PLANNER PORTFOLIO AUDIT (New Professional Feature)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Planner Portfolio Safety Audit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Strategic cash buffers, safety runways, and sequence-risk score",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Safety Runway Metric
                val safeSpendingBuffer = (currentB1Base + currentB2Base) / maxOf(1.0, config.expectedYearlyExpenses)
                val srrLevel = when {
                    safeSpendingBuffer >= 7.0 -> "Fortress Buffer"
                    safeSpendingBuffer >= 3.0 -> "Resilient Buffer"
                    else -> "Vulnerable Buffer"
                }
                val srrColor = when {
                    safeSpendingBuffer >= 7.0 -> Color(0xFF10B981)
                    safeSpendingBuffer >= 3.0 -> Color(0xFFF59E0B)
                    else -> Color(0xFFEF4444)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sequence of Returns Risk (SRR)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Safe spending buffer to survive bear market downturns",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = srrColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = srrLevel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = srrColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Double columns for Buffer Years & Required Years
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Safe Liquid Buffer",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (valuesHidden) "••••" else String.format("%.1f Years", safeSpendingBuffer),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = srrColor,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "Funded by Buckets 1 & 2",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Planner Target Buffer",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val targetBuffer = config.horizonB1 + config.horizonB2
                            Text(
                                text = "$targetBuffer.0 Years",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "B1 + B2 Target Horizon",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Comparative allocation split table (Current Allocation Share % vs Target Allocation Share %)
                Text(
                    text = "Comparative Allocation Matrix",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Table Header Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bucket",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Current Share",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Target Share",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                        val totalCurrent = currentB1Base + currentB2Base + currentB3Base
                        val totalTarget = targetB1Base + targetB2Base + targetB3Base

                        val b1Share = if (totalCurrent > 0.0) (currentB1Base / totalCurrent) * 100.0 else 0.0
                        val b2Share = if (totalCurrent > 0.0) (currentB2Base / totalCurrent) * 100.0 else 0.0
                        val b3Share = if (totalCurrent > 0.0) (currentB3Base / totalCurrent) * 100.0 else 0.0

                        val b1TargetShare = if (totalTarget > 0.0) (targetB1Base / totalTarget) * 100.0 else 0.0
                        val b2TargetShare = if (totalTarget > 0.0) (targetB2Base / totalTarget) * 100.0 else 0.0
                        val b3TargetShare = if (totalTarget > 0.0) (targetB3Base / totalTarget) * 100.0 else 0.0

                        // Row 1: Cash/Immediate
                        PlannerAllocationRow(
                            name = "Bucket 1 (Cash)",
                            color = Color(0xFF3B82F6),
                            currentPct = b1Share,
                            targetPct = b1TargetShare,
                            valuesHidden = valuesHidden
                        )

                        // Row 2: Conservative
                        PlannerAllocationRow(
                            name = "Bucket 2 (Income)",
                            color = Color(0xFF10B981),
                            currentPct = b2Share,
                            targetPct = b2TargetShare,
                            valuesHidden = valuesHidden
                        )

                        // Row 3: Growth
                        PlannerAllocationRow(
                            name = "Bucket 3 (Growth)",
                            color = Color(0xFF8B5CF6),
                            currentPct = b3Share,
                            targetPct = b3TargetShare,
                            valuesHidden = valuesHidden
                        )
                    }
                }

                // Dynamic Advisor Tip Box
                val advisoryText = when {
                    safeSpendingBuffer >= 7.0 -> "Your cash reserves are in excellent shape. This provides a massive protective cushion, allowing Bucket 3 (Growth Assets) to compound aggressively without fear of short-term liquidations."
                    safeSpendingBuffer >= 3.0 -> "You have a solid defensive foundation. Keep an eye on market cycles to rebalance some high-performing equity gains into cash/conservative income reserves to maintain your target 5-year living runway."
                    else -> "Your liquid defense is vulnerable. With less than 3 years of living expenses in Bucket 1 + Bucket 2, you are heavily exposed to sequence of returns risk. Consider reallocating from growth assets or increasing your regular contributions."
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "PLANNER RECOMMENDED RECOMMENDATION",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = advisoryText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. MONTE CARLO STRESS TEST PANEL (Advanced simulation)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Retirement Longevity Stress Test",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Applying 150 randomized stock, bond, and cash standard returns",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Two column layouts containing canvas circular scale on the left and description on the right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Gauge representation
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .testTag("monte_carlo_gauge_container"),
                        contentAlignment = Alignment.Center
                    ) {
                        val successColor = when {
                            simulationResult.successRate >= 80.0 -> Color(0xFF10B981)
                            simulationResult.successRate >= 70.0 -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        }

                        Canvas(modifier = Modifier.size(100.dp)) {
                            // Draw background circular track
                            drawArc(
                                color = successColor.copy(alpha = 0.15f),
                                startAngle = -220f,
                                sweepAngle = 260f,
                                useCenter = false,
                                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                            )
                            // Draw progress arc representation
                            val sweepRatio = (simulationResult.successRate / 100.0).toFloat().coerceIn(0f, 1f)
                            drawArc(
                                color = successColor,
                                startAngle = -220f,
                                sweepAngle = 260f * sweepRatio,
                                useCenter = false,
                                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${simulationResult.successRateInt}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = successColor,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "Survival",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Descriptions column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Simulation Core Plan Success",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Text(
                            text = "Runs 150 dynamic scenarios compound-drawdowns over ${config.horizonB1 + config.horizonB2 + config.horizonB3} retirement years.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Dynamic projected ending results
                        if (simulationResult.successRate > 0.0) {
                            val baseEndingBal = convertUsdToBase(simulationResult.averageEndingBalanceUsd, baseCurrency, viewModel)
                            Text(
                                text = "Avg Surplus: " + formatLocalAssetCurrency(baseEndingBal, baseCurrency, valuesHidden),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        if (simulationResult.successRate < 100.0 && simulationResult.failedYearAverage != null) {
                            Text(
                                text = String.format("Average Fail Year: Yr %.1f of drawdown", simulationResult.failedYearAverage),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Dynamic Action Insight Message Box
                val (boxBg, textCol, insightHeader, insightMessage) = when {
                    simulationResult.successRate >= 80.0 -> Quadruple(
                        Color(0xFF10B981).copy(alpha = 0.08f),
                        Color(0xFF0F766E),
                        "EXCELLENT OUTLOOK",
                        "Your current portfolio strategy is highly resilient! With an outstanding success probability, you have an exceptional chance of outlasting inflation on dynamic expenses."
                    )
                    simulationResult.successRate >= 70.0 -> Quadruple(
                        Color(0xFFF59E0B).copy(alpha = 0.08f),
                        Color(0xFFB45309),
                        "CAUTION RECOMMENDED",
                        "Plan displays medium-level vulnerability to extended market down-cycles. Consider increasing your regular contributions or extending the horizon on Bucket 3."
                    )
                    else -> Quadruple(
                        Color(0xFFEF4444).copy(alpha = 0.08f),
                        Color(0xFFB91C1C),
                        "STRATEGY ADJUSTMENT ADVISED",
                        "High threat of premature asset depletion. VOLATILITY RISK is compounding your funding gaps. We heavily recommend boosting contributions or tweaking sandbox horizons."
                    )
                }

                Surface(
                    color = boxBg,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, textCol.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = textCol,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = insightHeader,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = textCol,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = insightMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = textCol.copy(alpha = 0.85f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // 4. THREE-BUCKET PROGRESS DETAILS (Visual Horizontal Progress Stack)
        Text(
            text = "3-Bucket Funding Proportions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // BUCKET 1 CARD
        BucketFundingDisplayCard(
            title = "Bucket 1: Cash/Immediate",
            subtitle = "Sustains early lifestyle drawdowns & prevents forced sales of equities in downturns",
            currentAmount = currentB1Base,
            targetAmount = targetB1Base,
            gapAmount = gapB1Base,
            recommendedPmt = pmtB1Base,
            annualReturn = config.returnB1,
            horizonYears = config.horizonB1,
            baseCurrency = baseCurrency,
            valuesHidden = valuesHidden,
            themeColor = Color(0xFF3B82F6), // Blue
            tagId = 1,
            totalCurrent = totalCurrentBase,
            totalTarget = totalTargetBase,
            expectedYearlyExpenses = config.expectedYearlyExpenses
        )

        // BUCKET 2 CARD
        BucketFundingDisplayCard(
            title = "Bucket 2: Conservative/Income",
            subtitle = "Stable yields, dividend shields, and bond layers funding medium-term years",
            currentAmount = currentB2Base,
            targetAmount = targetB2Base,
            gapAmount = gapB2Base,
            recommendedPmt = pmtB2Base,
            annualReturn = config.returnB2,
            horizonYears = config.horizonB2,
            baseCurrency = baseCurrency,
            valuesHidden = valuesHidden,
            themeColor = Color(0xFF10B981), // Emerald Green
            tagId = 2,
            totalCurrent = totalCurrentBase,
            totalTarget = totalTargetBase,
            expectedYearlyExpenses = config.expectedYearlyExpenses
        )

        // BUCKET 3 CARD
        BucketFundingDisplayCard(
            title = "Bucket 3: Growth/Long-Term",
            subtitle = "Equities, growth funds, and speculative gains maximizing compound speed",
            currentAmount = currentB3Base,
            targetAmount = targetB3Base,
            gapAmount = gapB3Base,
            recommendedPmt = pmtB3Base,
            annualReturn = config.returnB3,
            horizonYears = config.horizonB3,
            baseCurrency = baseCurrency,
            valuesHidden = valuesHidden,
            themeColor = Color(0xFF8B5CF6), // Violet Purple
            tagId = 3,
            totalCurrent = totalCurrentBase,
            totalTarget = totalTargetBase,
            expectedYearlyExpenses = config.expectedYearlyExpenses
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 5. SEPARATE SUB-CARD COMPONENT REPRESENTING ONE STRATEGY BUCKET
 */
@Composable
fun BucketFundingDisplayCard(
    title: String,
    subtitle: String,
    currentAmount: Double,
    targetAmount: Double,
    gapAmount: Double,
    recommendedPmt: Double,
    annualReturn: Double,
    horizonYears: Int,
    baseCurrency: String,
    valuesHidden: Boolean,
    themeColor: Color,
    tagId: Int,
    totalCurrent: Double,
    totalTarget: Double,
    expectedYearlyExpenses: Double
) {
    val bProgressPct = if (targetAmount > 0.0) (currentAmount / targetAmount) * 100.0 else 0.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("strategy_bucket_card_$tagId"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = themeColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (valuesHidden) "••••" else String.format("%.0f%%", bProgressPct),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Black,
                        color = themeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Real-time Valuation proportions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = formatLocalAssetCurrency(currentAmount, baseCurrency, valuesHidden),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "Current Value",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatLocalAssetCurrency(targetAmount, baseCurrency, valuesHidden),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "Plan Target",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Segmented linear progress meter
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(3.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = (bProgressPct / 100.0).toFloat().coerceIn(0f, 1f))
                        .background(
                            color = themeColor,
                            shape = RoundedCornerShape(3.dp)
                        )
                )
            }

            // NEW Professional Planner Metrics: Spending Runway Coverage & Share allocation
            val coverageRunway = currentAmount / maxOf(1.0, expectedYearlyExpenses)
            val currentShare = if (totalCurrent > 0.0) (currentAmount / totalCurrent) * 100.0 else 0.0
            val targetShare = if (totalTarget > 0.0) (targetAmount / totalTarget) * 100.0 else 0.0

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text(
                            text = "Spending Runway",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (valuesHidden) "•••• yrs covered" else String.format("%.1f yrs covered", coverageRunway),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1.8f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "Asset Share Allocation",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = String.format("Current %.1f%% | Target %.1f%%", currentShare, targetShare),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Parameters indicators (Horizon, Return, Gap, PMT recommendation)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "$horizonYears yrs",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = String.format("%.1f%% yield", annualReturn),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // PMT summary details if gap exists
                if (gapAmount > 0.0) {
                    Text(
                        text = "Need ${formatLocalAssetCurrency(recommendedPmt, baseCurrency, valuesHidden)} / mo",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = themeColor,
                        maxLines = 1,
                        softWrap = false
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Bucket Fully Funded!",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * Custom allocation row helper for professional comparative table.
 */
@Composable
fun PlannerAllocationRow(
    name: String,
    color: Color,
    currentPct: Double,
    targetPct: Double,
    valuesHidden: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1.5f)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (valuesHidden) "••••" else String.format("%.1f%%", currentPct),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = String.format("%.1f%%", targetPct),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * 1. THE SET-UP COMPONENT (Scenario sandbox view configuring variables and bucket maps)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RetirementSandboxScreen(
    config: RetirementConfig,
    viewModel: NetWorthViewModel,
    bucketMetrics: List<com.example.ui.viewmodel.BucketWithMetrics>,
    baseCurrency: String,
    onConfigChanged: (RetirementConfig) -> Unit
) {
    val focusManager = LocalFocusManager.current

    // Internal editable values copying state for smooth typing responses
    var yearlyExpensesStr by remember { mutableStateOf(String.format("%.0f", config.expectedYearlyExpenses)) }
    var inflationRateStr by remember { mutableStateOf(String.format("%.1f", config.inflationRate)) }
    var actualSavingsStr by remember { mutableStateOf(String.format("%.0f", config.actualMonthlySavings)) }
    var targetYearStr by remember { mutableStateOf(config.targetYear.toString()) }

    var hB1Str by remember { mutableStateOf(config.horizonB1.toString()) }
    var hB2Str by remember { mutableStateOf(config.horizonB2.toString()) }
    var hB3Str by remember { mutableStateOf(config.horizonB3.toString()) }

    var rB1Str by remember { mutableStateOf(String.format("%.1f", config.returnB1)) }
    var rB2Str by remember { mutableStateOf(String.format("%.1f", config.returnB2)) }
    var rB3Str by remember { mutableStateOf(String.format("%.1f", config.returnB3)) }

    // Dropdown visibility trackers for Bucket mapping row selectors
    var activeBucketDropdownId by remember { mutableStateOf<Int?>(null) }

    val formattedCurrency = when (baseCurrency) {
        "PHP" -> "₱ PHP"
        "SAR" -> "SR SAR"
        "USD" -> "$ USD"
        "EUR" -> "€ EUR"
        "GBP" -> "£ GBP"
        "JPY" -> "¥ JPY"
        "AUD" -> "A$ AUD"
        "CAD" -> "C$ CAD"
        else -> baseCurrency
    }

    // Function to parse typing states into structured RetirementConfig structure
    fun triggerPushChange() {
        val expenses = yearlyExpensesStr.toDoubleOrNull() ?: config.expectedYearlyExpenses
        val inflation = inflationRateStr.toDoubleOrNull() ?: config.inflationRate
        val savings = actualSavingsStr.toDoubleOrNull() ?: config.actualMonthlySavings
        val targetYr = targetYearStr.toIntOrNull() ?: config.targetYear

        val h1 = hB1Str.toIntOrNull() ?: config.horizonB1
        val h2 = hB2Str.toIntOrNull() ?: config.horizonB2
        val h3 = hB3Str.toIntOrNull() ?: config.horizonB3

        val r1 = rB1Str.toDoubleOrNull() ?: config.returnB1
        val r2 = rB2Str.toDoubleOrNull() ?: config.returnB2
        val r3 = rB3Str.toDoubleOrNull() ?: config.returnB3

        onConfigChanged(
            config.copy(
                expectedYearlyExpenses = expenses,
                inflationRate = inflation,
                actualMonthlySavings = savings,
                targetYear = targetYr,
                horizonB1 = h1,
                horizonB2 = h2,
                horizonB3 = h3,
                returnB1 = r1,
                returnB2 = r2,
                returnB3 = r3
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        // Retirement Status Selector Card (planning vs retired)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Spa,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Current Retirement Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Configure if you are planning or already drawing down assets",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Custom segmented toggle switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Pre-retired tab option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (!config.isAlreadyRetired) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable {
                                onConfigChanged(config.copy(isAlreadyRetired = false))
                            }
                            .padding(vertical = 10.dp)
                            .testTag("status_toggle_planning"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Planning Phase",
                            fontWeight = FontWeight.Bold,
                            color = if (!config.isAlreadyRetired) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Post-retired tab option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (config.isAlreadyRetired) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable {
                                onConfigChanged(config.copy(isAlreadyRetired = true))
                            }
                            .padding(vertical = 10.dp)
                            .testTag("status_toggle_retired"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Drawdown Phase",
                            fontWeight = FontWeight.Bold,
                            color = if (config.isAlreadyRetired) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Section: Lifestyle expense parameters & inflation scales
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Base Strategy Variables",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                // Expected Baseline Yearly Expenses
                OutlinedTextField(
                    value = yearlyExpensesStr,
                    onValueChange = {
                        yearlyExpensesStr = it
                        triggerPushChange()
                    },
                    label = { Text("Yearly Expenses ($formattedCurrency)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_yearly_expenses_input"),
                    leadingIcon = { Icon(imageVector = Icons.Default.AccountBalanceWallet, contentDescription = null) },
                    singleLine = true
                )

                // Current regular Monthly Savings target
                OutlinedTextField(
                    value = actualSavingsStr,
                    onValueChange = {
                        actualSavingsStr = it
                        triggerPushChange()
                    },
                    label = { Text("Monthly Savings ($formattedCurrency)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_actual_savings_input"),
                    leadingIcon = { Icon(imageVector = Icons.Default.Spa, contentDescription = null) },
                    helperTextContent = "Required to compound starting balance down-ranges and drive correct stress-test success rates.",
                    singleLine = true
                )

                // Split inputs layout for remaining variables
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = targetYearStr,
                        onValueChange = {
                            targetYearStr = it
                            triggerPushChange()
                        },
                        label = { Text("Target Year", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("setup_target_year_input"),
                        leadingIcon = { Icon(imageVector = Icons.Default.DateRange, contentDescription = null) },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inflationRateStr,
                        onValueChange = {
                            inflationRateStr = it
                            triggerPushChange()
                        },
                        label = { Text("Inflation (%)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("setup_inflation_input"),
                        leadingIcon = { Icon(imageVector = Icons.Default.TrendingUp, contentDescription = null) },
                        singleLine = true
                    )
                }
            }
        }

        // Section 3: Strategic Bucket return & funded horizon setup
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Bucket Target Attributes",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                // B1 Attributes
                ListItemBucketSetupRow(
                    label = "Bucket 1 (Cash)",
                    horizonVal = hB1Str,
                    returnVal = rB1Str,
                    onHorizonChanged = { hB1Str = it; triggerPushChange() },
                    onReturnChanged = { rB1Str = it; triggerPushChange() },
                    themeColor = Color(0xFF3B82F6)
                )

                // B2 Attributes
                ListItemBucketSetupRow(
                    label = "Bucket 2 (Conservative)",
                    horizonVal = hB2Str,
                    returnVal = rB2Str,
                    onHorizonChanged = { hB2Str = it; triggerPushChange() },
                    onReturnChanged = { rB2Str = it; triggerPushChange() },
                    themeColor = Color(0xFF10B981)
                )

                // B3 Attributes
                ListItemBucketSetupRow(
                    label = "Bucket 3 (Growth)",
                    horizonVal = hB3Str,
                    returnVal = rB3Str,
                    onHorizonChanged = { hB3Str = it; triggerPushChange() },
                    onReturnChanged = { rB3Str = it; triggerPushChange() },
                    themeColor = Color(0xFF8B5CF6)
                )
            }
        }

        // THE CRITICAL METRIC BUCKET MAPPING COMPONENT
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Map App Buckets to Strategy",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Connect custom app-created folders to the 3-Bucket layers",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (bucketMetrics.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No custom folders/buckets found inside portfolio.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Render Map selector lists for every available bucket, plus the "Unassigned" row
                    val availableBuckets = bucketMetrics.map { it.bucket }
                    val allRepresentations = listWithUnassignedBucketRepresentation(availableBuckets)

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        allRepresentations.forEach { bucketItem ->
                            val bId = bucketItem?.id ?: -1
                            val bName = bucketItem?.name ?: "Unassigned Assets"
                            val currentAssignedStratId = config.bucketMappings[bId] ?: 0 // 0 = None

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (bId == -1) Icons.Default.FolderOpen else Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = bName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Interactive strategy level assignment tag selector dropdown button
                                    Box {
                                        Surface(
                                            color = when (currentAssignedStratId) {
                                                1 -> Color(0xFF3B82F6).copy(alpha = 0.15f)
                                                2 -> Color(0xFF10B981).copy(alpha = 0.15f)
                                                3 -> Color(0xFF8B5CF6).copy(alpha = 0.15f)
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .clickable {
                                                    activeBucketDropdownId = if (activeBucketDropdownId == bId) null else bId
                                                }
                                                .testTag("bucket_dropdown_trigger_$bId")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                val textTag = when (currentAssignedStratId) {
                                                    1 -> "Bucket 1"
                                                    2 -> "Bucket 2"
                                                    3 -> "Bucket 3"
                                                    else -> "Not Mapped"
                                                }
                                                val textCol = when (currentAssignedStratId) {
                                                    1 -> Color(0xFF2563EB)
                                                    2 -> Color(0xFF059669)
                                                    3 -> Color(0xFF7C3AED)
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }

                                                Text(
                                                    text = textTag,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = textCol
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    tint = textCol,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        // Material Menu Dropdown selection options
                                        DropdownMenu(
                                            expanded = activeBucketDropdownId == bId,
                                            onDismissRequest = { activeBucketDropdownId = null },
                                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Bucket 1 (Cash / Immediate)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2563EB)) },
                                                onClick = {
                                                    val newMappings = config.bucketMappings.toMutableMap()
                                                    newMappings[bId] = 1
                                                    onConfigChanged(config.copy(bucketMappings = newMappings))
                                                    activeBucketDropdownId = null
                                                },
                                                modifier = Modifier.testTag("bucket_select_${bId}_b1")
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Bucket 2 (Conservative / Income)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF059669)) },
                                                onClick = {
                                                    val newMappings = config.bucketMappings.toMutableMap()
                                                    newMappings[bId] = 2
                                                    onConfigChanged(config.copy(bucketMappings = newMappings))
                                                    activeBucketDropdownId = null
                                                },
                                                modifier = Modifier.testTag("bucket_select_${bId}_b2")
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Bucket 3 (Growth / Long-Term)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF7C3AED)) },
                                                onClick = {
                                                    val newMappings = config.bucketMappings.toMutableMap()
                                                    newMappings[bId] = 3
                                                    onConfigChanged(config.copy(bucketMappings = newMappings))
                                                    activeBucketDropdownId = null
                                                },
                                                modifier = Modifier.testTag("bucket_select_${bId}_b3")
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Don't Map (Exclude)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                                onClick = {
                                                    val newMappings = config.bucketMappings.toMutableMap()
                                                    newMappings.remove(bId)
                                                    onConfigChanged(config.copy(bucketMappings = newMappings))
                                                    activeBucketDropdownId = null
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Custom sub-row representing parameters helper input within strategic bucket configuration listItem cards.
 */
@Composable
fun ListItemBucketSetupRow(
    label: String,
    horizonVal: String,
    returnVal: String,
    onHorizonChanged: (String) -> Unit,
    onReturnChanged: (String) -> Unit,
    themeColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(themeColor, CircleShape)
            )
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = horizonVal,
                onValueChange = onHorizonChanged,
                label = { Text("Horizon (Yrs)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = returnVal,
                onValueChange = onReturnChanged,
                label = { Text("Return (%)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    }
}

/**
 * Returns a clean representation including a synthetic unassigned bucket entry.
 */
private fun listWithUnassignedBucketRepresentation(buckets: List<com.example.data.model.Bucket?>): List<com.example.data.model.Bucket?> {
    val results = mutableListOf<com.example.data.model.Bucket?>()
    buckets.forEach {
        if (it != null) results.add(it)
    }
    // Add custom representing Unassigned Assets
    results.add(null)
    return results
}

/**
 * Custom input textfields wrapper helper indicating supporting texts cleanly.
 */
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    helperTextContent: String? = null,
    singleLine: Boolean = true
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            leadingIcon = leadingIcon,
            singleLine = singleLine,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (helperTextContent != null) {
            Text(
                text = helperTextContent,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                lineHeight = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

/**
 * Helper tuple container holding 4 generic parameters.
 */
data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private data class GuardrailUIStatus(
    val title: String,
    val description: String,
    val color: Color,
    val backgroundColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private data class MarketScenarioUIStatus(
    val title: String,
    val rules: String,
    val activeBucket: Int,
    val description: String
)
