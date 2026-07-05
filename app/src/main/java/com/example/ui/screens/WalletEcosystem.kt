package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Asset
import com.example.data.model.AssetCategory
import com.example.data.model.Transaction
import com.example.ui.viewmodel.NetWorthViewModel
import com.example.ui.viewmodel.AssetWithMetrics
import java.text.SimpleDateFormat
import java.util.*

// Helper: parse custom notes in format: "Category > Subcategory | User notes"
data class ParsedSpending(
    val mainCategory: String,
    val subcategory: String,
    val userNote: String
)

data class ReportBarBucket(
    val label: String,
    val start: Long,
    val end: Long,
    var inflow: Double,
    var outflow: Double
)

fun parseSpendingNote(note: String?): ParsedSpending {
    if (note == null) return ParsedSpending("Other", "General", "")
    val delimiterIndex = note.indexOf("|")
    val categorySection = if (delimiterIndex != -1) note.substring(0, delimiterIndex).trim() else note.trim()
    val userNote = if (delimiterIndex != -1) note.substring(delimiterIndex + 1).trim() else ""
    
    val catSplit = categorySection.split(">")
    if (catSplit.size >= 2) {
        return ParsedSpending(catSplit[0].trim(), catSplit[1].trim(), userNote)
    } else if (catSplit.size == 1 && catSplit[0].isNotBlank()) {
        // Check if looks like a standard tag or category
        val word = catSplit[0].trim()
        if (word == "Food" || word == "Transportation" || word == "Utilities" || word == "Leisure" || word == "Housing") {
            return ParsedSpending(word, "General", userNote)
        }
    }
    return ParsedSpending("Other", "General", if (delimiterIndex == -1) note else userNote)
}

@Composable
fun WalletHistoryTabContent(
    walletAsset: Asset,
    transactions: List<Transaction>,
    viewModel: NetWorthViewModel,
    onEditTx: (Transaction) -> Unit,
    onDeleteTx: (Transaction) -> Unit,
    allAssets: List<Asset> = emptyList(),
    walletAliases: Map<Int, String> = emptyMap(),
    showWalletBadge: Boolean = false
) {
    val valuesHidden by viewModel.valuesHidden.collectAsStateWithLifecycle()
    var txToDelete by remember { mutableStateOf<Transaction?>(null) }
    
    // Sort transactions by descending date
    val sortedTxs = remember(transactions) {
        transactions.sortedByDescending { it.timestamp }
    }

    if (sortedTxs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Inbox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Text(
                    text = "No recorded wallet activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Withdrawals and inflows you log for this wallet will display here in a dedicated real-time historical ledger stream.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("wallet_scoped_history_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Wallet Transaction Stream",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            items(sortedTxs) { tx ->
                val parsed = remember(tx.notes) { parseSpendingNote(tx.notes) }
                val dateStr = remember(tx.timestamp) {
                    SimpleDateFormat("MMM dd, yyyy · hh:mma", Locale.getDefault()).format(Date(tx.timestamp))
                }
                
                val isIncome = tx.type == "DEPOSIT" || tx.type == "INCOME" || (tx.type == "TRANSFER" && tx.destinationAssetId == walletAsset.id)
                val isTransfer = tx.type == "TRANSFER"
                val txAmount = if (tx.type == "TRANSFER" && tx.destinationAssetId == walletAsset.id) {
                    tx.amount * (tx.exchangeRate ?: 1.0)
                } else {
                    tx.amount
                }
                val txColor = if (isIncome) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                
                val txWallet = remember(allAssets, tx.assetId) { allAssets.find { it.id == tx.assetId } }
                val txCurrency = if (tx.type == "TRANSFER" && tx.destinationAssetId == walletAsset.id) {
                    walletAsset.currency
                } else {
                    txWallet?.currency ?: walletAsset.currency
                }
                val formattedAmount = formatAssetCurrency(txAmount, txCurrency, valuesHidden = valuesHidden)

                val titleText = when {
                    isTransfer -> {
                        if (tx.destinationAssetId == walletAsset.id) {
                            val sourceWalletName = allAssets.find { it.id == tx.assetId }?.let { walletAliases[it.id] ?: it.name } ?: "Origin Wallet"
                            "Transfer from $sourceWalletName"
                        } else {
                            val destWalletName = tx.destinationAssetId?.let { destId -> allAssets.find { it.id == destId }?.let { walletAliases[it.id] ?: it.name } } ?: "Recipient Wallet"
                            "Transfer to $destWalletName"
                        }
                    }
                    isIncome -> "Income"
                    else -> parsed.mainCategory
                }

                val subtitleText = if (parsed.userNote.isNotBlank()) {
                    parsed.userNote
                } else {
                    when {
                        isTransfer -> {
                            if (tx.destinationAssetId == walletAsset.id) {
                                val sourceWalletName = allAssets.find { it.id == tx.assetId }?.let { walletAliases[it.id] ?: it.name } ?: "Origin Wallet"
                                "Received from $sourceWalletName"
                            } else {
                                val destWalletName = tx.destinationAssetId?.let { destId -> allAssets.find { it.id == destId }?.let { walletAliases[it.id] ?: it.name } } ?: "Recipient Wallet"
                                "Sent to $destWalletName"
                            }
                        }
                        isIncome -> "No notes registered."
                        else -> "Uncategorized spending note."
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            color = txColor.copy(alpha = 0.12f),
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isIncome) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = txColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (!isTransfer && !isIncome && parsed.subcategory != "General") {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = parsed.subcategory,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (showWalletBadge && txWallet != null) {
                                    val walletLabel = walletAliases[txWallet.id] ?: txWallet.name
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = walletLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = (if (isIncome) "+" else "-") + formattedAmount,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = txColor
                            )
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { onEditTx(tx) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit log entry",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { txToDelete = tx },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete log entry",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    txToDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { txToDelete = null },
            title = { Text("Delete Log Entry?") },
            text = { Text("Are you sure you want to permanently delete this transaction entry? This action is irreversible.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTx(tx)
                        txToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { txToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun WalletReportsTabContent(
    walletAsset: Asset,
    allAssets: List<Asset>,
    categories: List<AssetCategory>,
    allTransactions: List<Transaction>,
    viewModel: NetWorthViewModel,
    filterWalletId: Int?,
    filterCurrency: String?
) {
    val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()
    val spendingWalletIds by viewModel.spendingWalletIds.collectAsStateWithLifecycle()
    val valuesHidden by viewModel.valuesHidden.collectAsStateWithLifecycle()
    
    // Switchable spending wallets (liquid/cash assets matching filter)
    val cashCategoryIds = remember(categories) {
        categories.filter {
            (it.isAsset && (
                it.name.contains("cash", ignoreCase = true) ||
                it.name.contains("bank", ignoreCase = true) ||
                it.name.contains("liquid", ignoreCase = true) ||
                it.name.contains("wallet", ignoreCase = true) ||
                it.name.contains("savings", ignoreCase = true)
            )) || (!it.isAsset && (
                it.name.contains("card", ignoreCase = true) ||
                it.name.contains("credit", ignoreCase = true) ||
                it.name.contains("debt", ignoreCase = true) ||
                it.name.contains("liability", ignoreCase = true)
            ))
        }.map { it.id }.toSet()
    }
    
    val allActiveSpendingWallets = remember(allAssets, spendingWalletIds, cashCategoryIds) {
        val filtered = if (spendingWalletIds.isNotEmpty()) {
            allAssets.filter { it.id in spendingWalletIds }
        } else {
            allAssets.filter { cashCategoryIds.contains(it.categoryId) }
        }
        filtered.filter { !it.isArchived }
    }

    val activeReportWallet = remember(filterWalletId, allActiveSpendingWallets) {
        if (filterWalletId != null) {
            allActiveSpendingWallets.find { it.id == filterWalletId }
        } else {
            null
        }
    }
    
    val reportCurrency = remember(activeReportWallet, filterCurrency, baseCurrency) {
        if (filterCurrency != null) {
            filterCurrency
        } else {
            activeReportWallet?.currency ?: baseCurrency
        }
    }

    val rates = viewModel.exchangeRates
    
    val normalizedTransactions = remember(allTransactions, filterWalletId, filterCurrency, allActiveSpendingWallets, reportCurrency, rates, allAssets) {
        val activeWalletIds = allActiveSpendingWallets.map { it.id }.toSet()
        val baseFilteredTxs = allTransactions.filter { 
            (it.assetId in activeWalletIds || it.destinationAssetId in activeWalletIds) && it.type != "UPDATE" 
        }
        
        val filteredTxs = if (filterWalletId != null) {
            baseFilteredTxs.filter { it.assetId == filterWalletId || it.destinationAssetId == filterWalletId }
        } else if (filterCurrency != null) {
            baseFilteredTxs.filter { tx ->
                val txWallet = allAssets.find { it.id == tx.assetId }
                val destWallet = allAssets.find { it.id == tx.destinationAssetId }
                txWallet?.currency == filterCurrency || destWallet?.currency == filterCurrency
            }
        } else {
            baseFilteredTxs
        }

        filteredTxs.map { tx ->
            val sourceAsset = allAssets.find { it.id == tx.assetId }
            val destAsset = allAssets.find { it.id == tx.destinationAssetId }
            val sourceCurrency = sourceAsset?.currency ?: reportCurrency
            
            if (tx.type == "TRANSFER" && filterWalletId != null && tx.destinationAssetId == filterWalletId) {
                val amountInDestCurrency = tx.amount * (tx.exchangeRate ?: 1.0)
                val destCurrency = destAsset?.currency ?: reportCurrency
                if (destCurrency != reportCurrency) {
                    val amountUsd = viewModel.convertToUsd(amountInDestCurrency, destCurrency, rates)
                    val rateInReportCurrency = rates[reportCurrency] ?: 1.0
                    val normalizedAmt = if (rateInReportCurrency == 0.0) amountUsd else amountUsd / rateInReportCurrency
                    tx.copy(amount = normalizedAmt)
                } else {
                    tx.copy(amount = amountInDestCurrency)
                }
            } else {
                if (sourceCurrency != reportCurrency) {
                    val amountUsd = viewModel.convertToUsd(tx.amount, sourceCurrency, rates)
                    val rateInReportCurrency = rates[reportCurrency] ?: 1.0
                    val normalizedAmt = if (rateInReportCurrency == 0.0) amountUsd else amountUsd / rateInReportCurrency
                    tx.copy(amount = normalizedAmt)
                } else {
                    tx
                }
            }
        }
    }

    var selectedPeriod by remember { mutableStateOf("MTD") } // MTD, YTD, 1M, 1Y, ALL
    val periods = listOf("MTD", "YTD", "1M", "1Y", "ALL")
    val now = remember { System.currentTimeMillis() }
    
    val currentPeriodStart = remember(selectedPeriod, now) {
        val calendar = Calendar.getInstance()
        when (selectedPeriod) {
            "MTD" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            "YTD" -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            "1M" -> now - (30L * 24 * 60 * 60 * 1000)
            "1Y" -> now - (365L * 24 * 60 * 60 * 1000)
            else -> 0L
        }
    }
    
    val previousPeriodRange = remember(selectedPeriod, currentPeriodStart, now) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        
        when (selectedPeriod) {
            "MTD" -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                val start = cal.timeInMillis
                
                cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                start to cal.timeInMillis
            }
            "YTD" -> {
                cal.add(Calendar.YEAR, -1)
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                val start = cal.timeInMillis
                
                cal.set(Calendar.DAY_OF_YEAR, dayOfYear)
                start to cal.timeInMillis
            }
            "1M" -> {
                val duration = now - currentPeriodStart
                (currentPeriodStart - duration) to currentPeriodStart
            }
            "1Y" -> {
                val duration = now - currentPeriodStart
                (currentPeriodStart - duration) to currentPeriodStart
            }
            else -> {
                0L to 0L
            }
        }
    }

    val currentTxs = remember(normalizedTransactions, currentPeriodStart) {
        normalizedTransactions.filter { it.timestamp >= currentPeriodStart }
    }
    
    val previousTxs = remember(normalizedTransactions, previousPeriodRange) {
        val (start, end) = previousPeriodRange
        normalizedTransactions.filter { it.timestamp in start..end }
    }

    val reportBuckets = remember(selectedPeriod, currentPeriodStart, normalizedTransactions, rates, reportCurrency, now) {
        val buckets = mutableListOf<ReportBarBucket>()
        when (selectedPeriod) {
            "MTD", "1M" -> {
                val cal = Calendar.getInstance()
                cal.timeInMillis = currentPeriodStart
                var currentStart = currentPeriodStart
                val limit = now
                var weekNum = 1
                while (currentStart < limit) {
                    cal.timeInMillis = currentStart
                    val startMonth = cal.get(Calendar.MONTH)
                    val startDay = cal.get(Calendar.DAY_OF_MONTH)
                    
                    cal.add(Calendar.DAY_OF_YEAR, 6)
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    val currentEnd = minOf(limit, cal.timeInMillis)
                    
                    val mNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                    val label = "W$weekNum (${mNames[startMonth]} $startDay)"
                    
                    buckets.add(ReportBarBucket(label = label, start = currentStart, end = currentEnd, inflow = 0.0, outflow = 0.0))
                    
                    cal.timeInMillis = currentEnd
                    cal.add(Calendar.MILLISECOND, 1)
                    currentStart = cal.timeInMillis
                    weekNum++
                }
            }
            "YTD", "1Y" -> {
                val cal = Calendar.getInstance()
                cal.timeInMillis = currentPeriodStart
                var currentStart = currentPeriodStart
                val limit = now
                while (currentStart < limit) {
                    cal.timeInMillis = currentStart
                    val currentMonth = cal.get(Calendar.MONTH)
                    val currentYear = cal.get(Calendar.YEAR)
                    
                    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    val currentEnd = minOf(limit, cal.timeInMillis)
                    
                    val mNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                    val label = "${mNames[currentMonth]} '${currentYear.toString().takeLast(2)}"
                    
                    buckets.add(ReportBarBucket(label = label, start = currentStart, end = currentEnd, inflow = 0.0, outflow = 0.0))
                    
                    cal.timeInMillis = currentStart
                    cal.add(Calendar.MONTH, 1)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    currentStart = cal.timeInMillis
                }
            }
            else -> {
                val earliestTx = normalizedTransactions.minOfOrNull { it.timestamp } ?: (now - 5L * 365 * 24 * 60 * 60 * 1000)
                val cal = Calendar.getInstance()
                cal.timeInMillis = earliestTx
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                
                var currentStart = cal.timeInMillis
                val limit = now
                while (currentStart < limit) {
                    cal.timeInMillis = currentStart
                    val currentYear = cal.get(Calendar.YEAR)
                    
                    cal.set(Calendar.MONTH, Calendar.DECEMBER)
                    cal.set(Calendar.DAY_OF_MONTH, 31)
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    val currentEnd = minOf(limit, cal.timeInMillis)
                    
                    val label = currentYear.toString()
                    buckets.add(ReportBarBucket(label = label, start = currentStart, end = currentEnd, inflow = 0.0, outflow = 0.0))
                    
                    cal.timeInMillis = currentStart
                    cal.add(Calendar.YEAR, 1)
                    cal.set(Calendar.MONTH, Calendar.JANUARY)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    currentStart = cal.timeInMillis
                }
            }
        }

        for (tx in normalizedTransactions) {
            val bucket = buckets.find { tx.timestamp >= it.start && tx.timestamp <= it.end }
            if (bucket != null) {
                if (tx.type == "DEPOSIT" || tx.type == "INCOME" || (tx.type == "TRANSFER" && filterWalletId != null && tx.destinationAssetId == filterWalletId)) {
                    bucket.inflow += tx.amount
                } else if (tx.type == "WITHDRAWAL" || tx.type == "EXPENSE" || (tx.type == "TRANSFER" && filterWalletId != null && tx.assetId == filterWalletId)) {
                    bucket.outflow += tx.amount
                }
            }
        }

        buckets
    }

    // Core stats
    val totalExpenses = remember(currentTxs, filterWalletId) {
        currentTxs.filter { 
            it.type == "WITHDRAWAL" || 
            it.type == "EXPENSE" || 
            (it.type == "TRANSFER" && filterWalletId != null && it.assetId == filterWalletId)
        }.sumOf { it.amount }
    }
    
    val totalInflow = remember(currentTxs, filterWalletId) {
        currentTxs.filter { 
            it.type == "DEPOSIT" || 
            it.type == "INCOME" || 
            (it.type == "TRANSFER" && filterWalletId != null && it.destinationAssetId == filterWalletId)
        }.sumOf { it.amount }
    }

    // Calculated elapsed months for averages
    val elapsedMonths = remember(selectedPeriod, currentPeriodStart, normalizedTransactions) {
        if (selectedPeriod == "ALL") {
            val oldestTx = normalizedTransactions.minOfOrNull { it.timestamp } ?: System.currentTimeMillis()
            val msDiff = System.currentTimeMillis() - oldestTx
            maxOf(1.0, msDiff.toDouble() / (30.44 * 24 * 60 * 60 * 1000))
        } else {
            when (selectedPeriod) {
                "MTD" -> {
                    val cal = Calendar.getInstance()
                    val days = cal.get(Calendar.DAY_OF_MONTH)
                    maxOf(1.0, days.toDouble() / 30.44)
                }
                "YTD" -> {
                    val cal = Calendar.getInstance()
                    val months = cal.get(Calendar.MONTH) + 1
                    months.toDouble()
                }
                "1M" -> 1.0
                "1Y" -> 12.0
                else -> 1.0
            }
        }
    }

    val avgMonthlyExpenses = remember(totalExpenses, elapsedMonths) {
        totalExpenses / elapsedMonths
    }
    
    val avgYearlyExpenses = remember(avgMonthlyExpenses) {
        avgMonthlyExpenses * 12
    }

    // Spend distribution data
    val categoryDetails = remember(currentTxs) {
        val distribution = mutableMapOf<String, MutableMap<String, Double>>()
        currentTxs.filter { it.type == "WITHDRAWAL" }.forEach { tx ->
            val parsed = parseSpendingNote(tx.notes)
            val subMap = distribution.getOrPut(parsed.mainCategory) { mutableMapOf() }
            val curVal = subMap.getOrDefault(parsed.subcategory, 0.0)
            subMap[parsed.subcategory] = curVal + tx.amount
        }
        distribution
    }

    val chartData = remember(categoryDetails) {
        val list = mutableListOf<Pair<String, Double>>()
        categoryDetails.forEach { (catName, subMap) ->
            val sum = subMap.values.sum()
            if (sum > 0.0) {
                list.add(catName to sum)
            }
        }
        list.sortedByDescending { it.second }
    }

    val segmentColors = remember {
        listOf(
            Color(0xFF14B8A6), // Teal
            Color(0xFF8B5CF6), // Purple
            Color(0xFFF59E0B), // Orange
            Color(0xFFFBBF24), // Yellow
            Color(0xFF84CC16), // Lime Green
            Color(0xFF06B6D4), // Cyan
            Color(0xFF22C55E), // Green
            Color(0xFF3B82F6), // Blue
            Color(0xFFEC4899), // Pink
            Color(0xFF6B7280)  // Gray
        )
    }

    var selectedChartCategory by remember { mutableStateOf<String?>(null) }
    var selectedDetailCategory by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("wallet_reports_scrollable_container"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Performance Reports",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = reportCurrency,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Timeline Filter Bar
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    periods.forEach { prd ->
                        val isSelected = selectedPeriod == prd
                        Surface(
                            onClick = { 
                                selectedPeriod = prd 
                                selectedChartCategory = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wallet_report_filter_$prd")
                        ) {
                            Text(
                                text = prd,
                                modifier = Modifier.padding(vertical = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Core Statistics Cards
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Total Expenses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatAssetCurrency(totalExpenses, reportCurrency, valuesHidden = valuesHidden),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Total Inflow", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0F766E))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatAssetCurrency(totalInflow, reportCurrency, valuesHidden = valuesHidden),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Avg. Monthly Spend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatAssetCurrency(avgMonthlyExpenses, reportCurrency, valuesHidden = valuesHidden),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Avg. Yearly Spend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatAssetCurrency(avgYearlyExpenses, reportCurrency, valuesHidden = valuesHidden),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Cash Inflow vs. Outflow Bar Chart Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wallet_inflow_outflow_chart_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Cash Inflow vs. Outflow",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val periodUnit = when (selectedPeriod) {
                        "MTD", "1M" -> "weekly"
                        "YTD", "1Y" -> "monthly"
                        else -> "yearly"
                    }
                    Text(
                        text = "Visualizes cash inflow (deposits/income) versus outflow (withdrawals/expenses) grouped $periodUnit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    InflowOutflowBarChart(
                        buckets = reportBuckets,
                        reportCurrency = reportCurrency,
                        viewModel = viewModel,
                        valuesHidden = valuesHidden
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Legends
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFF10B981), RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Inflow",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFFEF4444), RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Outflow",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Spend Pacing Trend Chart (Canvas based Superimposed lines)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wallet_trend_line_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Spending Pacing vs. Previous Period",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Visualizes cumulative expenses pacing over selected period. High pacing relative to baseline indicates accelerated spending rate.",
                        style = java.lang.Deprecated::class.java.let { MaterialTheme.typography.bodySmall },
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val primaryPacing = remember(currentTxs, currentPeriodStart) {
                        val limit = System.currentTimeMillis()
                        getCumulativeWithdrawals(currentTxs, currentPeriodStart, limit, 20)
                    }
                    val previousPacing = remember(previousTxs, previousPeriodRange) {
                        val (start, end) = previousPeriodRange
                        getCumulativeWithdrawals(previousTxs, start, end, 20)
                    }
                    
                    val maxVal = remember(primaryPacing, previousPacing) {
                        val m1 = primaryPacing.maxOrNull() ?: 1.0
                        val m2 = previousPacing.maxOrNull() ?: 1.0
                        val rawMax = maxOf(m1, m2)
                        if (rawMax == 0.0) 100.0 else rawMax * 1.15
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            
                            // Draw horizontal gridlines
                            for (gridIdx in 1..3) {
                                val gridY = h * (gridIdx / 4.0).toFloat()
                                drawLine(
                                    color = Color.LightGray.copy(alpha = 0.15f),
                                    start = Offset(0f, gridY),
                                    end = Offset(w, gridY),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            
                            // 1. Draw Previous Period Trend Line (Light-tinted baseline comparison)
                            val prevPath = Path()
                            val prevStepW = w / (previousPacing.size - 1)
                            previousPacing.forEachIndexed { idx, value ->
                                val x = idx * prevStepW
                                val y = h - (value / maxVal).toFloat() * h
                                if (idx == 0) {
                                    prevPath.moveTo(x, y)
                                } else {
                                    prevPath.lineTo(x, y)
                                }
                            }
                            drawPath(
                                path = prevPath,
                                color = Color.Gray.copy(alpha = 0.35f),
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                            )
                            
                            // 2. Draw Current Pacing Trend Line (Bold red highlight)
                            val currPath = Path()
                            val currStepW = w / (primaryPacing.size - 1)
                            primaryPacing.forEachIndexed { idx, value ->
                                val x = idx * currStepW
                                val y = h - (value / maxVal).toFloat() * h
                                if (idx == 0) {
                                    currPath.moveTo(x, y)
                                } else {
                                    currPath.lineTo(x, y)
                                }
                            }
                            drawPath(
                                path = currPath,
                                color = Color(0xFFEF4444),
                                style = Stroke(width = 3.5.dp.toPx())
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Legends
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp, 4.dp).background(Color(0xFFEF4444), RoundedCornerShape(2.dp)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Current Period", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(modifier = Modifier.size(12.dp, 4.dp)) {
                                drawLine(
                                    color = Color.Gray.copy(alpha = 0.6f),
                                    start = Offset(0f, size.height/2),
                                    end = Offset(size.width, size.height/2),
                                    strokeWidth = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Previous Period", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Expense Categorisation arc / Donut chart
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wallet_donut_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Expense Distribution Breakdown",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (categoryDetails.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No spending records found in this interval range.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            // Donut Chart Canvas (Interactive arc drawing)
                            Box(
                                modifier = Modifier
                                    .size(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(165.dp)) {
                                    var currAngle = -90f
                                    chartData.forEachIndexed { idx, pair ->
                                        val sweep = (pair.second / totalExpenses).toFloat() * 360f
                                        val c = segmentColors[idx % segmentColors.size]
                                        drawArc(
                                            color = c,
                                            startAngle = currAngle,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            size = size,
                                            style = Stroke(width = 22.dp.toPx())
                                        )
                                        currAngle += sweep
                                    }
                                }
                                
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Total Spent",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val formattedTotalSpent = formatAssetCurrency(totalExpenses, reportCurrency, valuesHidden = valuesHidden)
                                    Text(
                                        text = formattedTotalSpent,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            // Interactive List Legend Panel
                            val chunkedData = chartData.chunked(2)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                chunkedData.forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowItems.forEach { pair ->
                                            val originalIdx = chartData.indexOf(pair)
                                            val isSelected = selectedChartCategory == pair.first
                                            val color = segmentColors[originalIdx % segmentColors.size]
                                            val pct = ((pair.second / totalExpenses) * 100.0)
                                            
                                            Surface(
                                                onClick = {
                                                    selectedChartCategory = if (isSelected) null else pair.first
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                                                border = BorderStroke(
                                                    width = 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                                                ),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(10.dp)
                                                                .background(color, CircleShape)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = pair.first,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                    Text(
                                                        text = String.format("%.1f%%", pct),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        if (rowItems.size < 2) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        // Category's Subcategories table disclosure
                        AnimatedVisibility(
                            visible = selectedChartCategory != null,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            selectedChartCategory?.let { selCat ->
                                val subcatMap = categoryDetails[selCat] ?: emptyMap()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "$selCat Subcategories",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(4.dp))
                                    subcatMap.forEach { (subName, amt) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(subName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = formatAssetCurrency(amt, reportCurrency, valuesHidden = valuesHidden),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
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

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wallet_category_bars_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Category Budget & Spending Breakdown",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap any category bar to view sub-category details.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    
                    if (chartData.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No spending stats to display for this range.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val maxCategoryValue = remember(chartData) {
                            chartData.firstOrNull()?.second ?: 1.0
                        }
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            chartData.forEachIndexed { idx, pair ->
                                val catName = pair.first
                                val catValue = pair.second
                                val barColor = segmentColors[idx % segmentColors.size]
                                val progress = if (maxCategoryValue == 0.0) 0f else (catValue / maxCategoryValue).toFloat()
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedDetailCategory = catName
                                        }
                                        .testTag("categories_bar_item_${catName}")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = catName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = formatAssetCurrency(catValue, reportCurrency, valuesHidden = valuesHidden),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    // Beautiful custom-styled horizontal bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(14.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(7.dp)
                                            )
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(progress)
                                                .fillMaxHeight()
                                                .background(
                                                    color = barColor,
                                                    shape = RoundedCornerShape(7.dp)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Subcategories Dialog
    if (selectedDetailCategory != null) {
        val catName = selectedDetailCategory!!
        val subcategoriesMap = categoryDetails[catName] ?: emptyMap()
        val sortedSubcategories = remember(subcategoriesMap) {
            subcategoriesMap.toList().sortedByDescending { it.second }
        }
        val totalCatSpend = remember(subcategoriesMap) {
            subcategoriesMap.values.sum()
        }
        
        Dialog(onDismissRequest = { selectedDetailCategory = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(vertical = 24.dp)
                    .testTag("categories_sub_detail_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = catName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Category Total: ${formatAssetCurrency(totalCatSpend, reportCurrency, valuesHidden = valuesHidden)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { selectedDetailCategory = null },
                            modifier = Modifier.testTag("close_category_desc_dialog")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close dialog",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (sortedSubcategories.isEmpty()) {
                        Text(
                            text = "No subcategory details available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        val maxSubValue = remember(sortedSubcategories) {
                            sortedSubcategories.firstOrNull()?.second ?: 1.0
                        }
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            sortedSubcategories.forEach { (subName, amt) ->
                                val subPctOfCat = if (totalCatSpend == 0.0) 0.0 else (amt / totalCatSpend) * 100.0
                                val relativeProgress = if (maxSubValue == 0.0) 0f else (amt / maxSubValue).toFloat()
                                
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = subName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = String.format(Locale.getDefault(), "%.1f%% of category", subPctOfCat),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = formatAssetCurrency(amt, reportCurrency, valuesHidden = valuesHidden),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // Custom progress bar for subcategory relative to max subcategory
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(relativeProgress)
                                                .fillMaxHeight()
                                                .background(
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { selectedDetailCategory = null },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

@Composable
fun InflowOutflowBarChart(
    buckets: List<ReportBarBucket>,
    reportCurrency: String,
    viewModel: NetWorthViewModel,
    valuesHidden: Boolean
) {
    if (buckets.isEmpty() || buckets.all { it.inflow == 0.0 && it.outflow == 0.0 }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No inflow or outflow transactions recorded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val rates = viewModel.exchangeRates
    val reportCurrencyRate = rates[reportCurrency] ?: 1.0

    // Find maximum value to normalize heights (in inflow or outflow)
    val maxVal = buckets.flatMap { listOf(it.inflow, it.outflow) }.maxOrNull() ?: 1.0
    val divisor = if (maxVal > 0.0) maxVal else 1.0

    // Horizontal scroll if too many items to fit beautifully
    val needsScroll = buckets.size > 5
    val scrollState = rememberScrollState()
    LaunchedEffect(buckets, scrollState.maxValue) {
        if (needsScroll && scrollState.maxValue > 0) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }
    val rowModifier = if (needsScroll) {
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 12.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    }

    Row(
        modifier = rowModifier,
        horizontalArrangement = if (needsScroll) Arrangement.spacedBy(16.dp) else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        buckets.forEach { bucket ->
            val inflowFraction = (bucket.inflow / divisor).toFloat().coerceIn(0f, 1.0f)
            val outflowFraction = (bucket.outflow / divisor).toFloat().coerceIn(0f, 1.0f)

            // Convert to USD equivalents for formatAbbreviated
            val inflowUsd = bucket.inflow * reportCurrencyRate
            val outflowUsd = bucket.outflow * reportCurrencyRate

            Column(
                modifier = if (needsScroll) Modifier.width(76.dp) else Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                // Side-by-side Top Labels (Inflow in green, Outflow in red)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (bucket.inflow > 0.0) {
                            Text(
                                text = formatAbbreviated(inflowUsd, reportCurrency, viewModel, valuesHidden),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                maxLines = 1
                            )
                        } else {
                            Text("", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (bucket.outflow > 0.0) {
                            Text(
                                text = formatAbbreviated(outflowUsd, reportCurrency, viewModel, valuesHidden),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                maxLines = 1
                            )
                        } else {
                            Text("", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp)
                        }
                    }
                }

                // Bar heights
                Row(
                    modifier = Modifier
                        .height(110.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Inflow Bar
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .fillMaxHeight(inflowFraction)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(Color(0xFF10B981))
                    )

                    // Outflow Bar
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .fillMaxHeight(outflowFraction)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(Color(0xFFEF4444))
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bucket Label
                Text(
                    text = bucket.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Draw cumulative withdrawals pacing over timeframe points
fun getCumulativeWithdrawals(
    txs: List<Transaction>,
    start: Long,
    end: Long,
    intervals: Int
): List<Double> {
    if (end <= start) return List(intervals) { 0.0 }
    val step = (end - start) / intervals
    val points = mutableListOf<Double>()
    
    for (i in 0 until intervals) {
        val checkpoint = start + (i + 1) * step
        val sumRange = txs.filter { 
            it.type == "WITHDRAWAL" && it.timestamp in start..checkpoint
        }.sumOf { it.amount }
        points.add(sumRange)
    }
    return points
}

@Composable
fun WalletTransactionDialog(
    walletAsset: Asset,
    viewModel: NetWorthViewModel,
    onDismiss: () -> Unit,
    onSave: (walletId: Int, type: String, amount: Double, notes: String, date: Long) -> Unit,
    editingTransaction: Transaction? = null
) {
    val allAssets by viewModel.assets.collectAsStateWithLifecycle()
    val spendingWalletIds by viewModel.spendingWalletIds.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val walletIcons by viewModel.walletIcons.collectAsStateWithLifecycle()
    val cashCategoryIds = remember(categories) {
        categories.filter {
            (it.isAsset && (
                it.name.contains("cash", ignoreCase = true) ||
                it.name.contains("bank", ignoreCase = true) ||
                it.name.contains("liquid", ignoreCase = true) ||
                it.name.contains("wallet", ignoreCase = true) ||
                it.name.contains("savings", ignoreCase = true)
            )) || (!it.isAsset && (
                it.name.contains("card", ignoreCase = true) ||
                it.name.contains("credit", ignoreCase = true) ||
                it.name.contains("debt", ignoreCase = true) ||
                it.name.contains("liability", ignoreCase = true)
            ))
        }.map { it.id }.toSet()
    }
    
    val activeWallets = remember(allAssets, spendingWalletIds, cashCategoryIds) {
        val filtered = if (spendingWalletIds.isNotEmpty()) {
            allAssets.filter { it.id in spendingWalletIds }
        } else {
            allAssets.filter { cashCategoryIds.contains(it.categoryId) }
        }
        filtered.filter { !it.isArchived }
    }

    var selectedWallet by remember(walletAsset, activeWallets) {
        mutableStateOf(activeWallets.find { it.id == walletAsset.id } ?: walletAsset)
    }

    var rawValueType by remember { mutableStateOf(if (editingTransaction?.type == "DEPOSIT") "INCOME" else "EXPENSE") } // EXPENSE (WITHDRAWAL) or INCOME (DEPOSIT)
    var inputStr by remember { mutableStateOf(editingTransaction?.amount?.toString() ?: "") }
    
    // Parsed properties if editing
    val initialNotesParsed = remember(editingTransaction) { 
        editingTransaction?.let { parseSpendingNote(it.notes) } 
    }
    
    // Dynamic custom categories mapping collected from NetWorthViewModel
    val categoriesMap by viewModel.spendingCategories.collectAsStateWithLifecycle()
    
    var selectedMainCategory by remember(categoriesMap, initialNotesParsed) { 
        mutableStateOf(initialNotesParsed?.mainCategory ?: categoriesMap.keys.firstOrNull() ?: "Food") 
    }
    var selectedSubcategory by remember(categoriesMap, selectedMainCategory, initialNotesParsed) { 
        mutableStateOf(initialNotesParsed?.subcategory ?: categoriesMap[selectedMainCategory]?.firstOrNull() ?: "Groceries") 
    }
    var userNotes by remember { 
        mutableStateOf(initialNotesParsed?.userNote ?: "") 
    }
    
    var timestamp by remember { mutableStateOf(editingTransaction?.timestamp ?: System.currentTimeMillis()) }

    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val lastUpdateTimestamp = remember(transactions, selectedWallet.id, editingTransaction) {
        transactions
            .filter { it.assetId == selectedWallet.id && it.type == "UPDATE" && (editingTransaction == null || it.id != editingTransaction.id) }
            .maxOfOrNull { it.timestamp } ?: 0L
    }

    LaunchedEffect(lastUpdateTimestamp) {
        if (editingTransaction == null && lastUpdateTimestamp > 0L && timestamp < lastUpdateTimestamp) {
            timestamp = lastUpdateTimestamp
        }
    }
    
    val context = LocalContext.current
    val calendar = Calendar.getInstance().apply { 
        val adjusted = if (editingTransaction == null && lastUpdateTimestamp > 0L) maxOf(timestamp, lastUpdateTimestamp) else timestamp
        timeInMillis = adjusted 
    }
    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth, 12, 0, 0)
            timestamp = cal.timeInMillis
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).apply {
        if (editingTransaction == null && lastUpdateTimestamp > 0L) {
            datePicker.minDate = lastUpdateTimestamp
        }
    }

    val enteredValue = inputStr.toDoubleOrNull() ?: 0.0
    val convertedAmountInAssetNative = enteredValue

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (editingTransaction != null) "Edit Wallet Journal" else "Record Wallet Journal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Quick Apply Templates horizontally scrollable chips
                val templates by viewModel.transactionTemplates.collectAsStateWithLifecycle()
                if (templates.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Quick Apply Template",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            templates.forEach { t ->
                                Surface(
                                    onClick = {
                                        rawValueType = "EXPENSE"
                                        inputStr = if (t.amount > 0.0) t.amount.toString() else ""
                                        selectedMainCategory = t.mainCategory
                                        selectedSubcategory = t.subcategory
                                        userNotes = t.notes
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = t.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Withdrawal vs. Income strictly choice
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val typesOpt = listOf("EXPENSE" to "Withdrawal (Expense)", "INCOME" to "Income (Deposit)")
                    typesOpt.forEach { (code, label) ->
                        val isSelected = rawValueType == code
                        Surface(
                            onClick = { rawValueType = code },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                if (code == "EXPENSE") MaterialTheme.colorScheme.error else Color(0xFF10B981)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }

                // Active Wallet Selection choice
                Column {
                    Text(
                        "Target Wallet",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    var showWalletDropdown by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            onClick = { showWalletDropdown = true },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val currentIcon = walletIcons[selectedWallet.id]
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            WalletIconView(
                                                iconKey = currentIcon,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${selectedWallet.name} (${selectedWallet.currency})",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        
                        DropdownMenu(
                            expanded = showWalletDropdown,
                            onDismissRequest = { showWalletDropdown = false },
                            modifier = Modifier.heightIn(max = 240.dp)
                        ) {
                            activeWallets.forEach { asset ->
                                DropdownMenuItem(
                                    text = { Text("${asset.name} (${asset.currency})") },
                                    leadingIcon = {
                                        val currentIcon = walletIcons[asset.id]
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                WalletIconView(
                                                    iconKey = currentIcon,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedWallet = asset
                                        showWalletDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Amount textfield
                OutlinedTextField(
                    value = inputStr,
                    onValueChange = { inputStr = it },
                    label = { Text("Transaction Amount (${selectedWallet.currency})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("wallet_journal_amount_field"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Conditional Categories layout only for Withdrawals
                if (rawValueType == "EXPENSE") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Expense Spending Category",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        
                        // Category choices horizontally scrollable
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categoriesMap.keys.forEach { catName ->
                                val isSel = selectedMainCategory == catName
                                Surface(
                                    onClick = {
                                        selectedMainCategory = catName
                                        selectedSubcategory = categoriesMap[catName]?.first() ?: "General"
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = catName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Subcategory choices horizontally scrollable
                        val subList = categoriesMap[selectedMainCategory] ?: emptyList()
                        Column {
                            Text(
                                "Spending Subcategory",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                subList.forEach { subName ->
                                    val isSel = selectedSubcategory == subName
                                    Surface(
                                        onClick = { selectedSubcategory = subName },
                                        shape = RoundedCornerShape(20.dp),
                                        border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(0.4f)),
                                        color = if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    ) {
                                        Text(
                                            text = subName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Notes textfield
                OutlinedTextField(
                    value = userNotes,
                    onValueChange = { userNotes = it },
                    label = { Text("Log notes (e.g. Starbucks coffee, grocery mall...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Date Picker Field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { datePickerDialog.show() }
                ) {
                    val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                    val formattedDate = dateFormat.format(Date(timestamp))

                    OutlinedTextField(
                        value = formattedDate,
                        onValueChange = {},
                        label = { Text("Transaction Date") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Select Date"
                            )
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            // Save note format as: "MainCategory > Subcategory | User notes"
                            // If Income, parse to standard Deposit
                            val txTypeToSave = if (rawValueType == "EXPENSE") "WITHDRAWAL" else "DEPOSIT"
                            
                            val formattedNotesString = if (rawValueType == "EXPENSE") {
                                "$selectedMainCategory > $selectedSubcategory | $userNotes"
                            } else {
                                "[Income] | $userNotes"
                            }
                            
                            onSave(selectedWallet.id, txTypeToSave, convertedAmountInAssetNative, formattedNotesString, timestamp)
                        },
                        enabled = convertedAmountInAssetNative > 0.0,
                        modifier = Modifier.testTag("wallet_journal_save_button")
                    ) {
                        Text(if (editingTransaction != null) "Update" else "Post Entry")
                    }
                }
            }
        }
    }
}

@Composable
fun WalletDashboardTabContent(
    walletAsset: Asset,
    allAssets: List<Asset>,
    categories: List<AssetCategory>,
    transactions: List<Transaction>,
    viewModel: NetWorthViewModel,
    onSwitchWallet: (Asset) -> Unit,
    onAddTransaction: () -> Unit,
    onViewAllTransactions: () -> Unit
) {
    val valuesHidden by viewModel.valuesHidden.collectAsStateWithLifecycle()
    val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()
    val walletAliases by viewModel.walletAliases.collectAsStateWithLifecycle()
    val walletIcons by viewModel.walletIcons.collectAsStateWithLifecycle()
    
    // Filter transactions for this wallet
    val walletTxs = remember(transactions, walletAsset.id) {
        transactions.filter { 
            it.assetId == walletAsset.id || (it.type == "TRANSFER" && it.destinationAssetId == walletAsset.id)
        }
    }
    
    // Calculate current month's withdrawals (expenses) and deposits (income)
    val now = remember { System.currentTimeMillis() }
    val currentMonthStart = remember(now) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.timeInMillis
    }
    
    val monthlyWithdrawals = remember(walletTxs, currentMonthStart, walletAsset.id) {
        walletTxs.filter { tx ->
            tx.timestamp >= currentMonthStart && (
                tx.type == "WITHDRAWAL" || 
                tx.type == "EXPENSE" || 
                (tx.type == "TRANSFER" && tx.assetId == walletAsset.id)
            )
        }.sumOf { tx -> tx.amount }
    }
    
    val monthlyDeposits = remember(walletTxs, currentMonthStart, walletAsset.id) {
        walletTxs.filter { tx ->
            tx.timestamp >= currentMonthStart && (
                tx.type == "DEPOSIT" || 
                tx.type == "INCOME" || 
                (tx.type == "TRANSFER" && tx.destinationAssetId == walletAsset.id)
            )
        }.sumOf { tx ->
            if (tx.type == "TRANSFER" && tx.destinationAssetId == walletAsset.id) {
                tx.amount * (tx.exchangeRate ?: 1.0)
            } else {
                tx.amount
            }
        }
    }

    val cumulativeCurrentMonth = remember(walletTxs, currentMonthStart, walletAsset.id) {
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        
        val withdrawalsByDay = walletTxs
            .filter { tx ->
                tx.timestamp >= currentMonthStart && (
                    tx.type == "WITHDRAWAL" || 
                    tx.type == "EXPENSE" || 
                    tx.type == "OUTFLOW" || 
                    (tx.type == "TRANSFER" && tx.assetId == walletAsset.id)
                )
            }
            .groupBy {
                val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                cal.get(Calendar.DAY_OF_MONTH)
            }
            .mapValues { it.value.sumOf { tx -> tx.amount } }
            
        val list = mutableListOf<Double>()
        var sum = 0.0
        for (day in 1..currentDay) {
            sum += withdrawalsByDay[day] ?: 0.0
            list.add(sum)
        }
        list
    }
    
    val previousMonthStartEnd = remember(now) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.MONTH, -1)
        val start = calendar.timeInMillis
        
        calendar.add(Calendar.MONTH, 1)
        calendar.add(Calendar.MILLISECOND, -1)
        val end = calendar.timeInMillis
        Pair(start, end)
    }
    
    val cumulativePreviousMonth = remember(walletTxs, previousMonthStartEnd, walletAsset.id) {
        val start = previousMonthStartEnd.first
        val end = previousMonthStartEnd.second
        val calendar = Calendar.getInstance().apply { timeInMillis = start }
        val maxDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        val withdrawalsByDay = walletTxs
            .filter { tx ->
                tx.timestamp in start..end && (
                    tx.type == "WITHDRAWAL" || 
                    tx.type == "EXPENSE" || 
                    tx.type == "OUTFLOW" || 
                    (tx.type == "TRANSFER" && tx.assetId == walletAsset.id)
                )
            }
            .groupBy {
                val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                cal.get(Calendar.DAY_OF_MONTH)
            }
            .mapValues { it.value.sumOf { tx -> tx.amount } }
            
        val list = mutableListOf<Double>()
        var sum = 0.0
        for (day in 1..maxDays) {
            sum += withdrawalsByDay[day] ?: 0.0
            list.add(sum)
        }
        list
    }

    val maxVal = remember(cumulativeCurrentMonth, cumulativePreviousMonth) {
        val maxCurrent = cumulativeCurrentMonth.maxOrNull() ?: 0.0
        val maxPrev = cumulativePreviousMonth.maxOrNull() ?: 0.0
        maxOf(maxCurrent, maxPrev, 100.0)
    }

    val currentDayIndex = cumulativeCurrentMonth.size - 1
    val comparisonText = remember(cumulativeCurrentMonth, cumulativePreviousMonth, currentDayIndex) {
        if (currentDayIndex >= 0 && currentDayIndex < cumulativePreviousMonth.size) {
            val currentCumulative = cumulativeCurrentMonth.lastOrNull() ?: 0.0
            val previousCumulativeAtSameDay = cumulativePreviousMonth[currentDayIndex]
            val diff = currentCumulative - previousCumulativeAtSameDay
            val percentage = if (previousCumulativeAtSameDay > 0) {
                (diff / previousCumulativeAtSameDay * 100).toInt()
            } else {
                0
            }
            if (diff > 0) {
                "Spending is $percentage% higher than last month at this point"
            } else if (diff < 0) {
                "Spending is ${-percentage}% lower than last month at this point"
            } else {
                "Spending is exactly the same as last month at this point"
            }
        } else {
            "Pacing comparisons will build as the month progresses"
        }
    }
    
    val comparisonColor = remember(comparisonText) {
        if (comparisonText.contains("higher")) Color(0xFFEF4444) else Color(0xFF10B981)
    }
    
    // Calculate current wallet balance (either historical flow or current native valuation)
    val assetMetrics by viewModel.assetMetrics.collectAsStateWithLifecycle()
    val matchingMetric = remember(assetMetrics, walletAsset.id) {
        assetMetrics.find { it.asset.id == walletAsset.id }
    }
    
    val isAsset = remember(categories, walletAsset.categoryId) {
        categories.find { it.id == walletAsset.categoryId }?.isAsset ?: true
    }
    val rawBalance = matchingMetric?.currentValuationNative ?: walletAsset.currentValuation
    val currentBalance = if (isAsset) rawBalance else -rawBalance
    val formattedBalance = formatAssetCurrency(currentBalance, walletAsset.currency, valuesHidden = valuesHidden)
    
    val spendingWalletIds by viewModel.spendingWalletIds.collectAsStateWithLifecycle()

    // Switchable spending wallets (liquid/cash assets except the current one)
    val cashCategoryIds = remember(categories) {
        categories.filter {
            (it.isAsset && (
                it.name.contains("cash", ignoreCase = true) ||
                it.name.contains("bank", ignoreCase = true) ||
                it.name.contains("liquid", ignoreCase = true) ||
                it.name.contains("wallet", ignoreCase = true) ||
                it.name.contains("savings", ignoreCase = true)
            )) || (!it.isAsset && (
                it.name.contains("card", ignoreCase = true) ||
                it.name.contains("credit", ignoreCase = true) ||
                it.name.contains("debt", ignoreCase = true) ||
                it.name.contains("liability", ignoreCase = true)
            ))
        }.map { it.id }.toSet()
    }
    
    val otherWallets = remember(allAssets, cashCategoryIds, spendingWalletIds, walletAsset.id) {
        val filtered = if (spendingWalletIds.isNotEmpty()) {
            allAssets.filter { it.id in spendingWalletIds }
        } else {
            allAssets.filter { cashCategoryIds.contains(it.categoryId) }
        }
        filtered.filter { it.id != walletAsset.id && it.includeInPortfolio }
    }

    val allSpendingWallets = remember(allAssets, cashCategoryIds, spendingWalletIds) {
        val filtered = if (spendingWalletIds.isNotEmpty()) {
            allAssets.filter { it.id in spendingWalletIds }
        } else {
            allAssets.filter { cashCategoryIds.contains(it.categoryId) }
        }
        filtered.filter { it.includeInPortfolio }
    }

    val totalWalletCashNative = remember(allAssets, spendingWalletIds, cashCategoryIds, assetMetrics, walletAsset.currency, viewModel.exchangeRates) {
        var totalUsd = 0.0
        val rates = viewModel.exchangeRates
        val activeSelectedAssets = if (spendingWalletIds.isNotEmpty()) {
            allAssets.filter { it.id in spendingWalletIds }
        } else {
            allAssets.filter { it.categoryId in cashCategoryIds && !it.isArchived }
        }
        for (asset in activeSelectedAssets) {
            val metric = assetMetrics.find { it.asset.id == asset.id }
            val balanceNative = metric?.currentValuationNative ?: asset.currentValuation
            val usdVal = viewModel.convertToUsd(balanceNative, asset.currency, rates)
            val isAssetType = categories.find { it.id == asset.categoryId }?.isAsset ?: true
            if (isAssetType) {
                totalUsd += usdVal
            } else {
                totalUsd -= usdVal
            }
        }
        val targetRate = rates[walletAsset.currency] ?: 1.0
        totalUsd / targetRate
    }

    val formattedTotalWalletCash = formatAssetCurrency(totalWalletCashNative, walletAsset.currency, valuesHidden = valuesHidden)
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("wallet_dashboard_tab_content"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            
            // Hero Balance Card (Compact scorecard)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wallet_dashboard_hero"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val activeAlias = walletAliases[walletAsset.id]
                            Text(
                                text = activeAlias ?: walletAsset.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (activeAlias != null) {
                                Text(
                                    text = walletAsset.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
                                )
                            }
                        }
                        
                        Surface(
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                WalletIconView(
                                    iconKey = walletIcons[walletAsset.id],
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = walletAsset.currency,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "Active Tracker Balance",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = formattedBalance,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Consolidated Total",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = formattedTotalWalletCash,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Active spending switch lists (Moved above Inflow/Outflow cards, showing up to 4 other wallets for 5 total active cards)
        if (allSpendingWallets.isNotEmpty()) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                ) {
                    var showAdjustmentMenu by remember { mutableStateOf(false) }
                    var showAdjustmentDialog by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Switch Active Tracked Wallet",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Box {
                            IconButton(
                                onClick = { showAdjustmentMenu = true },
                                modifier = Modifier.size(24.dp).testTag("wallet_adjustment_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreHoriz,
                                    contentDescription = "Wallet Balance Options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                            DropdownMenu(
                                expanded = showAdjustmentMenu,
                                onDismissRequest = { showAdjustmentMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Adjust Wallet Balance") },
                                    onClick = {
                                        showAdjustmentMenu = false
                                        showAdjustmentDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalanceWallet,
                                            contentDescription = null
                                        )
                                    },
                                    modifier = Modifier.testTag("adjust_wallet_balance_menu_item")
                                )
                            }
                        }
                    }

                    if (showAdjustmentDialog) {
                        WalletBalanceAdjustmentDialog(
                            spendingWallets = allSpendingWallets,
                            assetMetrics = assetMetrics,
                            walletAliases = walletAliases,
                            valuesHidden = valuesHidden,
                            onDismiss = { showAdjustmentDialog = false },
                            onAdjust = { walletId, amountDiff, note ->
                                if (amountDiff > 0) {
                                    viewModel.addTransaction(
                                        assetId = walletId,
                                        type = "DEPOSIT",
                                        amount = amountDiff,
                                        notes = "[Income] | Balance Adjustment: $note"
                                    )
                                } else if (amountDiff < 0) {
                                    viewModel.addTransaction(
                                        assetId = walletId,
                                        type = "WITHDRAWAL",
                                        amount = -amountDiff,
                                        notes = "Other > Uncategorized | Balance Adjustment: $note"
                                    )
                                }
                                showAdjustmentDialog = false
                            },
                            walletIcons = walletIcons
                        )
                    }
                    
                    val chunkedWallets = otherWallets.take(4).chunked(2)
                    chunkedWallets.forEach { rowWallets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowWallets.forEach { w ->
                                val otherMetric = assetMetrics.find { it.asset.id == w.id }
                                val otherBalance = otherMetric?.currentValuationNative ?: w.currentValuation
                                val otherBalanceFmt = formatAssetCurrency(otherBalance, w.currency, valuesHidden = valuesHidden)
                                val otherAlias = walletAliases[w.id]
                                
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onSwitchWallet(w) }
                                        .testTag("switch_to_wallet_${w.id}"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = otherAlias ?: w.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (otherAlias != null) {
                                                Text(
                                                    text = w.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = otherBalanceFmt,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        WalletIconView(
                                            iconKey = walletIcons[w.id],
                                            modifier = Modifier.size(26.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            if (rowWallets.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        
        // Income vs Expense Quick Stats
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Income Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Monthly Inflow",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatAssetCurrency(monthlyDeposits, walletAsset.currency, valuesHidden = valuesHidden),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }
                
                // Expense Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Monthly Outflow",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatAssetCurrency(monthlyWithdrawals, walletAsset.currency, valuesHidden = valuesHidden),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        
        // Cumulative Spending Line Chart Card (Replacing simple pacing limit card)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Monthly Spending Comparison",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Cumulative day-by-day outflow vs previous month",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // The Line Chart Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        val prevPoints = cumulativePreviousMonth
                        val currPoints = cumulativeCurrentMonth
                        val lineColorPrev = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        val lineColorCurr = MaterialTheme.colorScheme.primary
                        
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            
                            // Draw horizontal grid lines
                            val gridLines = 3
                            for (i in 0..gridLines) {
                                val y = h * (i.toFloat() / gridLines)
                                drawLine(
                                    color = lineColorPrev.copy(alpha = 0.1f),
                                    start = Offset(0f, y),
                                    end = Offset(w, y),
                                    strokeWidth = 1f
                                )
                            }
                            
                            // Draw previous month line (Dashed)
                            if (prevPoints.isNotEmpty()) {
                                val prevPath = Path()
                                val stepX = w / (prevPoints.size - 1).coerceAtLeast(1)
                                prevPoints.forEachIndexed { index, valDouble ->
                                    val x = index * stepX
                                    val y = h - (valDouble.toFloat() / maxVal.toFloat() * h)
                                    if (index == 0) {
                                        prevPath.moveTo(x, y)
                                    } else {
                                        prevPath.lineTo(x, y)
                                    }
                                }
                                drawPath(
                                    path = prevPath,
                                    color = lineColorPrev,
                                    style = Stroke(
                                        width = 3f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                    )
                                )
                            }
                            
                            // Draw current month line (Solid primary bold)
                            if (currPoints.isNotEmpty()) {
                                val currPath = Path()
                                val stepX = w / (prevPoints.size - 1).coerceAtLeast(1) // align with same daily grid
                                currPoints.forEachIndexed { index, valDouble ->
                                    val x = index * stepX
                                    val y = h - (valDouble.toFloat() / maxVal.toFloat() * h)
                                    if (index == 0) {
                                        currPath.moveTo(x, y)
                                    } else {
                                        currPath.lineTo(x, y)
                                    }
                                }
                                drawPath(
                                    path = currPath,
                                    color = lineColorCurr,
                                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                                )
                                
                                // Draw glow/dot at current day
                                if (currPoints.isNotEmpty()) {
                                    val lastX = (currPoints.size - 1) * stepX
                                    val lastY = h - (currPoints.last().toFloat() / maxVal.toFloat() * h)
                                    drawCircle(
                                        color = lineColorCurr,
                                        radius = 6f,
                                        center = Offset(lastX, lastY)
                                    )
                                    drawCircle(
                                        color = lineColorCurr.copy(alpha = 0.3f),
                                        radius = 12f,
                                        center = Offset(lastX, lastY)
                                    )
                                }
                            }
                        }
                    }

                    // Legend & Comparison Note
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Legend
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(12.dp, 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
                                Text("Last Month", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(12.dp, 4.dp).background(MaterialTheme.colorScheme.primary))
                                Text("This Month", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        
                        Text(
                            text = "Peak: " + formatAssetCurrency(maxVal, walletAsset.currency, valuesHidden = valuesHidden),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Comparison status badge
                    Surface(
                        color = comparisonColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (comparisonText.contains("higher")) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = comparisonColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = comparisonText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = comparisonColor
                            )
                        }
                    }
                }
            }
        }
        
        // Recent activity widget
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("wallet_recent_activity_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Active Ledger",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "View Ledger",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onViewAllTransactions() }
                        )
                    }
                    
                    val recentTxs = remember(walletTxs) { walletTxs.sortedByDescending { it.timestamp }.take(3) }
                    
                    if (recentTxs.isEmpty()) {
                        Text(
                            text = "No recent entries found. Record your first transaction to populate activity log.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        recentTxs.forEach { tx ->
                            val parsedNotes = remember(tx.notes) { parseSpendingNote(tx.notes) }
                            val isIncome = tx.type == "DEPOSIT" || tx.type == "INCOME" || (tx.type == "TRANSFER" && tx.destinationAssetId == walletAsset.id)
                            val isTransfer = tx.type == "TRANSFER"
                            val txAmount = if (tx.type == "TRANSFER" && tx.destinationAssetId == walletAsset.id) {
                                tx.amount * (tx.exchangeRate ?: 1.0)
                            } else {
                                tx.amount
                            }
                            val amtColor = if (isIncome) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                            
                            val titleText = when {
                                isTransfer -> {
                                    if (tx.destinationAssetId == walletAsset.id) {
                                        val sourceWalletName = allAssets.find { it.id == tx.assetId }?.let { walletAliases[it.id] ?: it.name } ?: "Origin Wallet"
                                        "Transfer from $sourceWalletName"
                                    } else {
                                        val destWalletName = tx.destinationAssetId?.let { destId -> allAssets.find { it.id == destId }?.let { walletAliases[it.id] ?: it.name } } ?: "Recipient Wallet"
                                        "Transfer to $destWalletName"
                                    }
                                }
                                isIncome -> "Income Inflow"
                                else -> "${parsedNotes.mainCategory} · ${parsedNotes.subcategory}"
                            }

                            val subtitleText = if (parsedNotes.userNote.isNotBlank()) {
                                parsedNotes.userNote
                            } else {
                                when {
                                    isTransfer -> {
                                        if (tx.destinationAssetId == walletAsset.id) {
                                            val sourceWalletName = allAssets.find { it.id == tx.assetId }?.let { walletAliases[it.id] ?: it.name } ?: "Origin Wallet"
                                            "Received from $sourceWalletName"
                                        } else {
                                            val destWalletName = tx.destinationAssetId?.let { destId -> allAssets.find { it.id == destId }?.let { walletAliases[it.id] ?: it.name } } ?: "Recipient Wallet"
                                            "Sent to $destWalletName"
                                        }
                                    }
                                    isIncome -> "No notes registered."
                                    else -> "Uncategorized spending note."
                                }
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isIncome) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                        contentDescription = null,
                                        tint = amtColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = titleText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (subtitleText.isNotBlank()) {
                                            Text(
                                                text = subtitleText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = (if (isIncome) "+" else "-") + formatAssetCurrency(txAmount, walletAsset.currency, valuesHidden = valuesHidden),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = amtColor
                                )
                            }
                        }
                    }
                }
            }
        }        // Budget Status Card (below recent active ledger card)
        item {
            val subcategoryBudgets by viewModel.subcategoryBudgets.collectAsStateWithLifecycle()
            val subcategoryBudgetCurrencies by viewModel.subcategoryBudgetCurrencies.collectAsStateWithLifecycle()
            val activeSubcatBudgets = remember(subcategoryBudgets) {
                subcategoryBudgets.filter { it.value > 0.0 }
            }
            
            val subcategorySpentInWalletCurrency = remember(transactions, currentMonthStart, walletAsset, viewModel.exchangeRates) {
                val spentMap = mutableMapOf<String, Double>()
                val rates = viewModel.exchangeRates
                transactions.filter { tx ->
                    tx.timestamp >= currentMonthStart && (
                        tx.type == "WITHDRAWAL" || 
                        tx.type == "EXPENSE" || 
                        tx.type == "TRANSFER"
                    )
                }.forEach { tx ->
                    val parsed = parseSpendingNote(tx.notes)
                    val key = "${parsed.mainCategory}_${parsed.subcategory}"
                    val catKey = "${parsed.mainCategory}_"
                    val txAsset = allSpendingWallets.find { it.id == tx.assetId }
                    if (txAsset != null) {
                        val txAmountInWalletCurrency = viewModel.convertCurrency(tx.amount, txAsset.currency, walletAsset.currency, rates)
                        spentMap[key] = (spentMap[key] ?: 0.0) + txAmountInWalletCurrency
                        spentMap[catKey] = (spentMap[catKey] ?: 0.0) + txAmountInWalletCurrency
                    }
                }
                spentMap
            }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wallet_budget_status_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Subcategory Budgets Monitor",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Monthly subcategory threshold health indicators",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (activeSubcatBudgets.isEmpty()) {
                        // Empty budget state
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "No Subcategory Budgets Active",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Configure monthly subcategory limits in Wallet Settings (top-right icon) > Categories.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // List each active subcategory and category budget
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            activeSubcatBudgets.forEach { (key, activeBudget) ->
                                val parts = key.split("_")
                                val category = parts.getOrNull(0) ?: "Other"
                                val subcategory = parts.getOrNull(1) ?: ""
                                
                                val displayName = if (subcategory.isEmpty()) {
                                    "Overall $category Category"
                                } else {
                                    "$subcategory ($category)"
                                }
                                
                                val budgetBaseCurr = subcategoryBudgetCurrencies[key] ?: baseCurrency
                                val convertedActiveBudget = viewModel.convertCurrency(activeBudget, budgetBaseCurr, walletAsset.currency, viewModel.exchangeRates)
                                
                                val spentAmount = subcategorySpentInWalletCurrency[key] ?: 0.0
                                val fraction = if (convertedActiveBudget > 0.0) {
                                    (spentAmount / convertedActiveBudget).coerceIn(0.0, 1.0).toFloat()
                                } else 0f
                                val ratio = if (convertedActiveBudget > 0.0) spentAmount / convertedActiveBudget else 0.0
                                
                                val barColor = when {
                                    ratio > 1.0 -> MaterialTheme.colorScheme.error // Red
                                    ratio >= 0.8 -> Color(0xFFF97316) // Warning/Orange
                                    else -> Color(0xFF10B981) // Green
                                }
                                
                                val statusText = when {
                                    ratio > 1.0 -> "Over Budget limit!"
                                    ratio >= 0.8 -> "Approaching budget threshold (80%+ spent)"
                                    else -> "Within budget limit"
                                }
                                
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = barColor
                                        )
                                    }
                                    
                                    LinearProgressIndicator(
                                        progress = { fraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        strokeCap = StrokeCap.Round,
                                        color = barColor,
                                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Spent: " + formatAssetCurrency(spentAmount, walletAsset.currency, valuesHidden = valuesHidden),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = barColor
                                        )
                                        Text(
                                            text = "Budget: " + formatAssetCurrency(convertedActiveBudget, walletAsset.currency, valuesHidden = valuesHidden),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun WalletSettingsDialog(
    viewModel: NetWorthViewModel,
    allAssets: List<com.example.data.model.Asset>,
    buckets: List<com.example.data.model.Bucket>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val spendingCategories by viewModel.spendingCategories.collectAsStateWithLifecycle()
    val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()
    val spendingWalletIds by viewModel.spendingWalletIds.collectAsStateWithLifecycle()
    val defaultSpendingWalletId by viewModel.defaultSpendingWalletId.collectAsStateWithLifecycle()
    val selectedWalletBucketId by viewModel.selectedWalletBucketId.collectAsStateWithLifecycle()

    // List of assets in the selected wallet bucket
    val walletBucketAssets = remember(allAssets, selectedWalletBucketId) {
        if (selectedWalletBucketId == null) {
            emptyList()
        } else {
            allAssets.filter { it.bucketId == selectedWalletBucketId && !it.isArchived }
        }
    }

    var selectedWalletIds by remember(spendingWalletIds) { mutableStateOf(spendingWalletIds.toSet()) }
    var selectedDefaultId by remember(defaultSpendingWalletId) { mutableStateOf(defaultSpendingWalletId) }

    val storedAliases by viewModel.walletAliases.collectAsStateWithLifecycle()
    var editedAliases by remember(storedAliases) { mutableStateOf(storedAliases) }

    val storedIcons by viewModel.walletIcons.collectAsStateWithLifecycle()
    var editedIcons by remember(storedIcons) { mutableStateOf(storedIcons) }

    val storedBudgets by viewModel.walletBudgets.collectAsStateWithLifecycle()
    var editedBudgets by remember(storedBudgets) {
        mutableStateOf(storedBudgets.mapValues { if (it.value > 0.0) String.format(Locale.US, "%.0f", it.value) else "" })
    }

    var activePhotoPickingAssetId by remember { mutableStateOf<Int?>(null) }
    val photoLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        val assetId = activePhotoPickingAssetId
        if (uri != null && assetId != null) {
            try {
                val file = java.io.File(context.filesDir, "wallet_icon_${assetId}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                editedIcons = editedIcons + (assetId to "file://" + file.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activePhotoPickingAssetId = null
    }

    // Dialog state tabs: -1 = MAIN LIST OF SETTINGS, 0 = WALLETS / DEFAULT, 1 = CATEGORIES & SUBCATEGORIES, 2 = TEMPLATES, 3 = RECURRING
    var activeSettingsTab by remember { mutableStateOf(-1) }

    // State for Category creation
    var newCategoryName by remember { mutableStateOf("") }
    var newSubcategoryName by remember { mutableStateOf("") }

    // Dialog trigger for adding/editing a category
    var editingCategoryName by remember { mutableStateOf<String?>(null) }
    var editingSubcategoriesText by remember { mutableStateOf("") } // comma-separated
    var spendingCategoryToDelete by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 700.dp)
                .fillMaxHeight(0.9f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeSettingsTab >= 0) {
                        IconButton(onClick = { activeSettingsTab = -1 }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back to settings list")
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text(
                        text = if (activeSettingsTab == -1) "Wallet Settings Center" else when(activeSettingsTab) {
                            0 -> "Wallets Configuration"
                            1 -> "Categories & Budgets"
                            2 -> "Transaction Templates"
                            3 -> "Recurring Expenses"
                            else -> "Wallet Settings Center"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close settings")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Switcher (Only shown if a setting category is entered)
                if (activeSettingsTab >= 0) {
                    TabRow(
                        selectedTabIndex = activeSettingsTab,
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        Tab(
                            selected = activeSettingsTab == 0,
                            onClick = { activeSettingsTab = 0 },
                            text = { Text("Wallets", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                        Tab(
                            selected = activeSettingsTab == 1,
                            onClick = { activeSettingsTab = 1 },
                            text = { Text("Categories", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                        Tab(
                            selected = activeSettingsTab == 2,
                            onClick = { activeSettingsTab = 2 },
                            text = { Text("Templates", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                        Tab(
                            selected = activeSettingsTab == 3,
                            onClick = { activeSettingsTab = 3 },
                            text = { Text("Recurring", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (activeSettingsTab == -1) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val menuItems = listOf(
                                Triple(0, "Wallets & Linked Accounts", Icons.Default.AccountBalanceWallet),
                                Triple(1, "Categories, Budgets & Limits", Icons.Default.Category),
                                Triple(2, "Transaction Templates & Quick Log", Icons.Default.Receipt),
                                Triple(3, "Reoccurring Expenses & Alerts", Icons.Default.Schedule)
                            )
                            
                            menuItems.forEach { (index, label, icon) ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { activeSettingsTab = index },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f)
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(0.12f),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val desc = when(index) {
                                                0 -> "Configure cash flow, default allocations, and budget limits."
                                                1 -> "Create categories, set monthly limits, and manage subcategories."
                                                2 -> "Draft predefined transaction forms for swift logging."
                                                3 -> "Automate frequent expense tracks and configure notifications."
                                                else -> ""
                                            }
                                            Text(
                                                text = desc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowRight,
                                            contentDescription = "Navigate",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (activeSettingsTab == 0) {
                        // WALLET SELECTION AND DEFAULT WALLET (Compact Layout)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Link Net Worth Bucket to Wallet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            var bucketDropdownExpanded by remember { mutableStateOf(false) }
                            val selectedBucket = remember(buckets, selectedWalletBucketId) {
                                buckets.find { it.id == selectedWalletBucketId }
                            }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { bucketDropdownExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = selectedBucket?.name ?: "No Bucket Selected (Tap to Choose)",
                                            color = if (selectedBucket != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select Bucket"
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = bucketDropdownExpanded,
                                    onDismissRequest = { bucketDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("None (Unassigned)", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            viewModel.setSelectedWalletBucketId(null)
                                            bucketDropdownExpanded = false
                                        }
                                    )
                                    buckets.forEach { b ->
                                        DropdownMenuItem(
                                            text = { Text(b.name, fontWeight = FontWeight.Bold) },
                                            onClick = {
                                                viewModel.setSelectedWalletBucketId(b.id)
                                                bucketDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Divider()

                            Text(
                                text = "Choose Spending Wallets (Limit 5)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Section: Selected Bucket Assets
                            if (selectedWalletBucketId == null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Please select a Net Worth bucket above to map assets to your wallets.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else if (walletBucketAssets.isEmpty()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "No Assets or Liabilities in the Chosen Bucket",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "Go to the Net Worth or Assets page and assign assets or liabilities to \"${selectedBucket?.name}\" first.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                walletBucketAssets.forEach { asset ->
                                    val isSelected = asset.id in selectedWalletIds
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedWalletIds = if (isSelected) {
                                                    selectedWalletIds - asset.id
                                                } else {
                                                    if (selectedWalletIds.size < 5) selectedWalletIds + asset.id else selectedWalletIds
                                                }
                                                // Refresh default selection
                                                if (selectedDefaultId == asset.id && !isSelected) {
                                                    selectedDefaultId = null
                                                }
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    selectedWalletIds = if (checked) {
                                                        if (selectedWalletIds.size < 5) selectedWalletIds + asset.id else selectedWalletIds
                                                    } else {
                                                        selectedWalletIds - asset.id
                                                    }
                                                    if (!checked && selectedDefaultId == asset.id) {
                                                        selectedDefaultId = null
                                                    }
                                                }
                                            )
                                            Column {
                                                Text(asset.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                        Text(asset.currency, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            Divider()

                            // Select Default Wallet Menu (Very Compact)
                            Text(
                                text = "Select Default Spending Wallet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            val activeWalletAssets = allAssets.filter { it.id in selectedWalletIds }
                            if (activeWalletAssets.isEmpty()) {
                                Text(
                                    text = "Select active spending wallets above to choose a default wallet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                activeWalletAssets.forEach { asset ->
                                    val isDefault = selectedDefaultId == asset.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedDefaultId = asset.id }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        RadioButton(
                                            selected = isDefault,
                                            onClick = { selectedDefaultId = asset.id }
                                        )
                                        Text(
                                            text = asset.name + " (${asset.currency})",
                                            fontWeight = if (isDefault) FontWeight.Bold else FontWeight.Normal,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            Divider()

                            // Option to assign Purpose name (alias) for each selected wallet (Compact Input Fields)
                            Text(
                                text = "Wallet Purpose & Monthly Budgets",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (activeWalletAssets.isEmpty()) {
                                Text(
                                    text = "Select active spending wallets above to configure custom purposes/budgets.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                activeWalletAssets.forEach { asset ->
                                    val currentAlias = editedAliases[asset.id] ?: ""
                                    val currentBudget = editedBudgets[asset.id] ?: ""
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.2f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            val currentIcon = editedIcons[asset.id] ?: ""
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        WalletIconView(
                                                            iconKey = currentIcon,
                                                            modifier = Modifier.size(20.dp),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                                Text(text = asset.name + " (" + asset.currency + ")", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            OutlinedTextField(
                                                value = currentAlias,
                                                onValueChange = { newValue ->
                                                    editedAliases = editedAliases + (asset.id to newValue)
                                                },
                                                label = { Text("Purpose (e.g. Personal, Business)", style = MaterialTheme.typography.labelSmall) },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            OutlinedTextField(
                                                value = currentBudget,
                                                onValueChange = { newValue ->
                                                    editedBudgets = editedBudgets + (asset.id to newValue)
                                                },
                                                label = { Text("Monthly Spend Limit", style = MaterialTheme.typography.labelSmall) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp)
                                            )

                                            // Preset Icons Picker
                                            Text(
                                                text = "Select Preset Icon",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )

                                            androidx.compose.foundation.lazy.LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                items(ALL_PRESET_ICONS.size) { index ->
                                                    val preset = ALL_PRESET_ICONS[index]
                                                    val key = preset.first
                                                    val isSelected = currentIcon == key || (currentIcon.isEmpty() && key == "savings")

                                                    Surface(
                                                        onClick = {
                                                            editedIcons = editedIcons + (asset.id to key)
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                        border = BorderStroke(
                                                            width = 1.dp,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                                        ),
                                                        modifier = Modifier.padding(vertical = 2.dp)
                                                    ) {
                                                        Box(modifier = Modifier.padding(8.dp)) {
                                                            val vector = getWalletPresetIcon(key)
                                                            Icon(
                                                                imageVector = vector,
                                                                contentDescription = preset.second,
                                                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(18.dp)
                                                             )
                                                         }
                                                     }
                                                 }
                                             }

                                             // Upload photo or logo input URL row
                                             Row(
                                                 modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                 horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                 verticalAlignment = Alignment.CenterVertically
                                             ) {
                                                 Button(
                                                     onClick = {
                                                         activePhotoPickingAssetId = asset.id
                                                         photoLauncher.launch("image/*")
                                                     },
                                                     modifier = Modifier.weight(1f),
                                                     colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                                     shape = RoundedCornerShape(8.dp),
                                                     contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                                 ) {
                                                     Row(
                                                         horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                         verticalAlignment = Alignment.CenterVertically
                                                     ) {
                                                         Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                         Text("Upload Photo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                     }
                                                 }

                                                 var showUrlInput by remember { mutableStateOf(currentIcon.startsWith("http")) }
                                                 Button(
                                                     onClick = { showUrlInput = !showUrlInput },
                                                     modifier = Modifier.weight(1f),
                                                     colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                                     shape = RoundedCornerShape(8.dp),
                                                     contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                                 ) {
                                                     Row(
                                                         horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                         verticalAlignment = Alignment.CenterVertically
                                                     ) {
                                                         Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                                         Text("Logo URL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                     }
                                                 }
                                             }

                                             var urlValue by remember(currentIcon) {
                                                 mutableStateOf(if (currentIcon.startsWith("http")) currentIcon else "")
                                             }

                                             if (currentIcon.startsWith("http") || urlValue.isNotEmpty()) {
                                                 OutlinedTextField(
                                                     value = urlValue,
                                                     onValueChange = { newValue ->
                                                         urlValue = newValue
                                                         if (newValue.startsWith("http://") || newValue.startsWith("https://")) {
                                                             editedIcons = editedIcons + (asset.id to newValue)
                                                         }
                                                     },
                                                     label = { Text("Bank Logo URL (e.g. https://...)", style = MaterialTheme.typography.labelSmall) },
                                                     placeholder = { Text("https://logo.clearbit.com/chase.com") },
                                                     modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                     singleLine = true,
                                                     shape = RoundedCornerShape(8.dp)
                                                 )
                                             }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    viewModel.saveSpendingWalletIds(selectedWalletIds.toList())
                                    viewModel.setDefaultSpendingWalletId(selectedDefaultId)
                                    // Save any customized aliases for the current selected wallets
                                    selectedWalletIds.forEach { id ->
                                        val aliasValue = editedAliases[id] ?: ""
                                        viewModel.saveWalletAlias(id, aliasValue)

                                        val iconValue = editedIcons[id] ?: ""
                                        viewModel.saveWalletIcon(id, iconValue)
                                        
                                        val budgetStr = editedBudgets[id] ?: ""
                                        val budgetVal = budgetStr.toDoubleOrNull()
                                        viewModel.saveWalletBudget(id, budgetVal)
                                    }
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                             ) {
                                Text("Save Spending Configuration")
                            }
                        }
                    } else if (activeSettingsTab == 1) {
                        // CUSTOM CATEGORIES MANAGEMENT
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Configure Categories & Subcategories",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Quick add category form (Compact Design)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Add New Category", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    OutlinedTextField(
                                        value = newCategoryName,
                                        onValueChange = { newCategoryName = it },
                                        label = { Text("Category (e.g. Health, Pets)", style = MaterialTheme.typography.bodySmall) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    OutlinedTextField(
                                        value = newSubcategoryName,
                                        onValueChange = { newSubcategoryName = it },
                                        label = { Text("Subcategories (comma separated)", style = MaterialTheme.typography.bodySmall) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    Button(
                                        onClick = {
                                            if (newCategoryName.isNotBlank()) {
                                                val subs = newSubcategoryName.split(",")
                                                    .map { it.trim() }
                                                    .filter { it.isNotEmpty() }
                                                    .ifEmpty { listOf("General") }
                                                viewModel.addSpendingCategory(newCategoryName.trim(), subs)
                                                newCategoryName = ""
                                                newSubcategoryName = ""
                                            }
                                        },
                                        modifier = Modifier.align(Alignment.End),
                                        enabled = newCategoryName.isNotBlank(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Add Category")
                                    }
                                }
                            }

                            Divider()

                            // List of categories and options to delete or edit them
                            spendingCategories.forEach { (category, subcategories) ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Header Row: Category name and actions
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = category,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        editingCategoryName = category
                                                        editingSubcategoriesText = subcategories.joinToString(", ")
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit category",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        spendingCategoryToDelete = category
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete category",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Divider(
                                            modifier = Modifier.fillMaxWidth(),
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                        )

                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            val subBudgets by viewModel.subcategoryBudgets.collectAsStateWithLifecycle()
                                            val subBudgetCurrencies by viewModel.subcategoryBudgetCurrencies.collectAsStateWithLifecycle()

                                            // Category-level Overall Budget Limit
                                            val catBudgetKey = "${category}_"
                                            val catBudgetVal = subBudgets[catBudgetKey]
                                            val catBudgetCurr = subBudgetCurrencies[catBudgetKey] ?: baseCurrency
                                            var catBudgetText by remember(catBudgetVal) { mutableStateOf(if (catBudgetVal != null && catBudgetVal > 0.0) String.format(Locale.US, "%.0f", catBudgetVal) else "") }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Category,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = "Overall $category Budget",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                
                                                // Currency Selector
                                                var catCurrExpanded by remember { mutableStateOf(false) }
                                                Box {
                                                    Text(
                                                        text = catBudgetCurr,
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier
                                                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f), RoundedCornerShape(4.dp))
                                                            .clickable { catCurrExpanded = true }
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                    DropdownMenu(
                                                        expanded = catCurrExpanded,
                                                        onDismissRequest = { catCurrExpanded = false }
                                                    ) {
                                                        val budgetCurrenciesList = remember(baseCurrency) {
                                                            (listOf(baseCurrency) + listOf("USD", "EUR", "GBP", "JPY", "PHP", "IDR", "SGD", "CAD", "AUD", "SAR", "BTC")).distinct()
                                                        }
                                                        budgetCurrenciesList.forEach { currencyCode ->
                                                            DropdownMenuItem(
                                                                text = { Text(currencyCode, style = MaterialTheme.typography.bodySmall) },
                                                                onClick = {
                                                                    catCurrExpanded = false
                                                                    val parsedDouble = catBudgetText.toDoubleOrNull()
                                                                    viewModel.saveSubcategoryBudget(category, "", parsedDouble, currencyCode)
                                                                }
                                                            )
                                                        }
                                                    }
                                                }

                                                // Budget Input Field
                                                var isCatFocused by remember { mutableStateOf(false) }
                                                BasicTextField(
                                                    value = catBudgetText,
                                                    onValueChange = { newValue ->
                                                        catBudgetText = newValue
                                                        val parsedDouble = newValue.toDoubleOrNull()
                                                        viewModel.saveSubcategoryBudget(category, "", parsedDouble, catBudgetCurr)
                                                    },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = FontWeight.Bold,
                                                        textAlign = TextAlign.Center
                                                    ),
                                                    modifier = Modifier
                                                        .width(80.dp)
                                                        .height(32.dp)
                                                        .onFocusChanged { isCatFocused = it.isFocused }
                                                        .border(
                                                            width = 1.dp,
                                                            color = if (isCatFocused) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else {
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                                            },
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .background(
                                                            color = if (isCatFocused) {
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                                            } else {
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.02f)
                                                            },
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .padding(horizontal = 8.dp),
                                                    decorationBox = { innerTextField ->
                                                        Box(
                                                            contentAlignment = Alignment.Center,
                                                            modifier = Modifier.fillMaxSize()
                                                        ) {
                                                            if (catBudgetText.isEmpty()) {
                                                                Text(
                                                                    text = "No limit",
                                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                                        textAlign = TextAlign.Center
                                                                    )
                                                                )
                                                            }
                                                            innerTextField()
                                                        }
                                                    }
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))

                                            Text(
                                                text = "Monthly Subcategory Budgets:",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                            )
                                            
                                            subcategories.forEach { sub ->
                                                val budgetKey = "${category}_${sub}"
                                                val bVal = subBudgets[budgetKey]
                                                val bCurr = subBudgetCurrencies[budgetKey] ?: baseCurrency
                                                var bText by remember(bVal) { mutableStateOf(if (bVal != null && bVal > 0.0) String.format(Locale.US, "%.0f", bVal) else "") }
                                                
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 2.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.KeyboardArrowRight,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                            text = sub,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    
                                                    // Currency Selector
                                                    var currExpanded by remember { mutableStateOf(false) }
                                                    Box {
                                                        Text(
                                                            text = bCurr,
                                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier
                                                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f), RoundedCornerShape(4.dp))
                                                                .clickable { currExpanded = true }
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                        DropdownMenu(
                                                            expanded = currExpanded,
                                                            onDismissRequest = { currExpanded = false }
                                                        ) {
                                                            val budgetCurrenciesList = remember(baseCurrency) {
                                                                (listOf(baseCurrency) + listOf("USD", "EUR", "GBP", "JPY", "PHP", "IDR", "SGD", "CAD", "AUD", "SAR", "BTC")).distinct()
                                                            }
                                                            budgetCurrenciesList.forEach { currencyCode ->
                                                                DropdownMenuItem(
                                                                    text = { Text(currencyCode, style = MaterialTheme.typography.bodySmall) },
                                                                    onClick = {
                                                                        currExpanded = false
                                                                        val parsedDouble = bText.toDoubleOrNull()
                                                                        viewModel.saveSubcategoryBudget(category, sub, parsedDouble, currencyCode)
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // Compact, vertically and horizontally centered input field
                                                    var isFocused by remember { mutableStateOf(false) }
                                                    BasicTextField(
                                                        value = bText,
                                                        onValueChange = { newValue ->
                                                            bText = newValue
                                                            val parsedDouble = newValue.toDoubleOrNull()
                                                            viewModel.saveSubcategoryBudget(category, sub, parsedDouble, bCurr)
                                                        },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                        singleLine = true,
                                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = FontWeight.Medium,
                                                            textAlign = TextAlign.Center
                                                        ),
                                                        modifier = Modifier
                                                            .width(80.dp)
                                                            .height(32.dp)
                                                            .onFocusChanged { isFocused = it.isFocused }
                                                            .border(
                                                                width = 1.dp,
                                                                color = if (isFocused) {
                                                                    MaterialTheme.colorScheme.primary
                                                                } else {
                                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                                                },
                                                                shape = RoundedCornerShape(8.dp)
                                                            )
                                                            .background(
                                                                color = if (isFocused) {
                                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                                                } else {
                                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                                                },
                                                                shape = RoundedCornerShape(8.dp)
                                                            )
                                                            .padding(horizontal = 8.dp),
                                                        decorationBox = { innerTextField ->
                                                            Box(
                                                                contentAlignment = Alignment.Center,
                                                                modifier = Modifier.fillMaxSize()
                                                            ) {
                                                                if (bText.isEmpty()) {
                                                                    Text(
                                                                        text = "No limit",
                                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                                            fontSize = 11.sp,
                                                                            fontWeight = FontWeight.Normal,
                                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                                            textAlign = TextAlign.Center
                                                                        )
                                                                    )
                                                                }
                                                                innerTextField()
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (activeSettingsTab == 2) {
                        // TRANSACTION TEMPLATES tab (Tab 2)
                        val templates by viewModel.transactionTemplates.collectAsStateWithLifecycle()

                        var editingTemplateId by remember { mutableStateOf<String?>(null) }
                        val isEditing = editingTemplateId != null

                        var templateName by remember { mutableStateOf("") }
                        var templateAmount by remember { mutableStateOf("") }
                        var templateNotes by remember { mutableStateOf("") }
                        var templateMainCat by remember(spendingCategories) { mutableStateOf(spendingCategories.keys.firstOrNull() ?: "Food") }
                        var templateSubCat by remember(spendingCategories, templateMainCat) { mutableStateOf(spendingCategories[templateMainCat]?.firstOrNull() ?: "Groceries") }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Expense Quick-Entry Templates",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Form to create/add template
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.35f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(if (isEditing) "Edit Reoccurring Template" else "Create Reoccurring Template", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    
                                    OutlinedTextField(
                                        value = templateName,
                                        onValueChange = { templateName = it },
                                        label = { Text("Template Name (e.g. Netflix, Rent, Gym)", style = MaterialTheme.typography.bodySmall) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = templateAmount,
                                            onValueChange = { templateAmount = it },
                                            label = { Text("Amount", style = MaterialTheme.typography.bodySmall) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        
                                        OutlinedTextField(
                                            value = templateNotes,
                                            onValueChange = { templateNotes = it },
                                            label = { Text("Notes", style = MaterialTheme.typography.bodySmall) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1.5f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }

                                    // Category Selectors for template
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Main Category Dropdown
                                        var mainCatExpanded by remember { mutableStateOf(false) }
                                        Box(modifier = Modifier.weight(1f)) {
                                            OutlinedButton(
                                                onClick = { mainCatExpanded = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(templateMainCat, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                            }
                                            DropdownMenu(
                                                expanded = mainCatExpanded,
                                                onDismissRequest = { mainCatExpanded = false }
                                            ) {
                                                spendingCategories.keys.forEach { cat ->
                                                    DropdownMenuItem(
                                                        text = { Text(cat) },
                                                        onClick = {
                                                            templateMainCat = cat
                                                            templateSubCat = spendingCategories[cat]?.firstOrNull() ?: "General"
                                                            mainCatExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        // Subcategory Dropdown
                                        var subCatExpanded by remember { mutableStateOf(false) }
                                        val sublist = spendingCategories[templateMainCat] ?: emptyList()
                                        Box(modifier = Modifier.weight(1f)) {
                                            OutlinedButton(
                                                onClick = { subCatExpanded = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(8.dp),
                                                enabled = sublist.isNotEmpty()
                                            ) {
                                                Text(templateSubCat, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                            }
                                            DropdownMenu(
                                                expanded = subCatExpanded,
                                                onDismissRequest = { subCatExpanded = false }
                                            ) {
                                                sublist.forEach { sub ->
                                                    DropdownMenuItem(
                                                        text = { Text(sub) },
                                                        onClick = {
                                                            templateSubCat = sub
                                                            subCatExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isEditing) {
                                            TextButton(
                                                onClick = {
                                                    editingTemplateId = null
                                                    templateName = ""
                                                    templateAmount = ""
                                                    templateNotes = ""
                                                    templateMainCat = spendingCategories.keys.firstOrNull() ?: "Food"
                                                    templateSubCat = spendingCategories[templateMainCat]?.firstOrNull() ?: "Groceries"
                                                },
                                                modifier = Modifier.padding(end = 8.dp)
                                            ) {
                                                Text("Cancel")
                                            }
                                        }
                                        Button(
                                            onClick = {
                                                if (templateName.isNotBlank()) {
                                                    val amt = templateAmount.toDoubleOrNull() ?: 0.0
                                                    viewModel.saveTransactionTemplate(
                                                        NetWorthViewModel.TransactionTemplate(
                                                            id = editingTemplateId ?: java.util.UUID.randomUUID().toString(),
                                                            name = templateName.trim(),
                                                            amount = amt,
                                                            mainCategory = templateMainCat,
                                                            subcategory = templateSubCat,
                                                            notes = templateNotes.trim()
                                                        )
                                                    )
                                                    editingTemplateId = null
                                                    templateName = ""
                                                    templateAmount = ""
                                                    templateNotes = ""
                                                }
                                            },
                                            enabled = templateName.isNotBlank(),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(if (isEditing) "Save Changes" else "Add Template")
                                        }
                                    }
                                }
                            }

                            Divider()

                            // List existing templates
                            if (templates.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No templates configured yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                templates.forEach { t ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(t.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    text = "${t.mainCategory} > ${t.subcategory} | Amount: ${t.amount}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                if (t.notes.isNotBlank()) {
                                                    Text(t.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        editingTemplateId = t.id
                                                        templateName = t.name
                                                        templateAmount = if (t.amount > 0.0) t.amount.toString() else ""
                                                        templateNotes = t.notes
                                                        templateMainCat = t.mainCategory
                                                        templateSubCat = t.subcategory
                                                    },
                                                    modifier = Modifier.size(36.dp).testTag("edit_template_${t.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit Template",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.deleteTransactionTemplate(t.id) },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete Template", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (activeSettingsTab == 3) {
                        // RECURRING EXPENSES tab (Tab 3)
                        val recurringList by viewModel.recurringExpenses.collectAsStateWithLifecycle()

                        var editingRecurringId by remember { mutableStateOf<String?>(null) }
                        val isEditingRecurring = editingRecurringId != null

                        var recName by remember { mutableStateOf("") }
                        var recAmount by remember { mutableStateOf("") }
                        var recNotes by remember { mutableStateOf("") }
                        var recFrequency by remember { mutableStateOf("Monthly") }
                        var recWalletId by remember(walletBucketAssets) { mutableStateOf(walletBucketAssets.firstOrNull()?.id ?: -1) }
                        var recMainCat by remember(spendingCategories) { mutableStateOf(spendingCategories.keys.firstOrNull() ?: "Food") }
                        var recSubCat by remember(spendingCategories, recMainCat) { mutableStateOf(spendingCategories[recMainCat]?.firstOrNull() ?: "Groceries") }

                        var recStartDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
                        var hasEndDate by remember { mutableStateOf(false) }
                        var recEndDate by remember { mutableLongStateOf(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000L) }

                        LaunchedEffect(walletBucketAssets, isEditingRecurring) {
                            if (!isEditingRecurring && recWalletId == -1 && walletBucketAssets.isNotEmpty()) {
                                recWalletId = walletBucketAssets.firstOrNull()?.id ?: -1
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Reoccurring Expenses & Auto-Alerts",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (walletBucketAssets.isEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "Please set up your Wallet Bucket and linked wallets in the 'Wallets' tab first to create recurring expenses.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            } else {
                                // Form to create/edit recurring expense
                                val context = LocalContext.current
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.35f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = if (isEditingRecurring) "Edit Reoccurring Expense" else "Create Reoccurring Expense",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        
                                        OutlinedTextField(
                                            value = recName,
                                            onValueChange = { recName = it },
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                            label = { Text("Expense Name (e.g. Netflix, Rent, Spotify)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = recAmount,
                                                onValueChange = { recAmount = it },
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                                label = { Text("Amount", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            
                                            // Frequency selector
                                            var freqExpanded by remember { mutableStateOf(false) }
                                            Box(modifier = Modifier.weight(1f)) {
                                                OutlinedTextField(
                                                    value = recFrequency,
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                                    label = { Text("Frequency", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp)) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { freqExpanded = true }
                                                )
                                                DropdownMenu(
                                                    expanded = freqExpanded,
                                                    onDismissRequest = { freqExpanded = false }
                                                ) {
                                                    listOf("Daily", "Weekly", "Monthly", "Yearly").forEach { f ->
                                                        DropdownMenuItem(
                                                            text = { Text(f) },
                                                            onClick = {
                                                                recFrequency = f
                                                                freqExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Wallet Selector Dropdown
                                            var walletExpanded by remember { mutableStateOf(false) }
                                            val selectedWalletAsset = walletBucketAssets.find { it.id == recWalletId } ?: walletBucketAssets.firstOrNull()
                                            if (selectedWalletAsset != null && recWalletId != selectedWalletAsset.id) {
                                                recWalletId = selectedWalletAsset.id
                                            }
                                            
                                            Box(modifier = Modifier.weight(1f)) {
                                                OutlinedTextField(
                                                    value = selectedWalletAsset?.name ?: "Select Wallet",
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                                    label = { Text("Wallet", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp)) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { walletExpanded = true }
                                                )
                                                DropdownMenu(
                                                    expanded = walletExpanded,
                                                    onDismissRequest = { walletExpanded = false }
                                                ) {
                                                    walletBucketAssets.forEach { asset ->
                                                        DropdownMenuItem(
                                                            text = { Text(asset.name) },
                                                            onClick = {
                                                                recWalletId = asset.id
                                                                walletExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            OutlinedTextField(
                                                value = recNotes,
                                                onValueChange = { recNotes = it },
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                                label = { Text("Notes", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }

                                        // Category select
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Main Category Dropdown
                                            var mainCatExpanded by remember { mutableStateOf(false) }
                                            Box(modifier = Modifier.weight(1f)) {
                                                OutlinedTextField(
                                                    value = recMainCat,
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                                    label = { Text("Category", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp)) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { mainCatExpanded = true }
                                                )
                                                DropdownMenu(
                                                    expanded = mainCatExpanded,
                                                    onDismissRequest = { mainCatExpanded = false }
                                                ) {
                                                    spendingCategories.keys.forEach { cat ->
                                                        DropdownMenuItem(
                                                            text = { Text(cat) },
                                                            onClick = {
                                                                recMainCat = cat
                                                                recSubCat = spendingCategories[cat]?.firstOrNull() ?: "General"
                                                                mainCatExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            // Subcategory Dropdown
                                            var subCatExpanded by remember { mutableStateOf(false) }
                                            val sublist = spendingCategories[recMainCat] ?: emptyList()
                                            Box(modifier = Modifier.weight(1f)) {
                                                OutlinedTextField(
                                                    value = recSubCat,
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                                    label = { Text("Subcategory", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp)) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(8.dp),
                                                    enabled = sublist.isNotEmpty()
                                                )
                                                if (sublist.isNotEmpty()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .matchParentSize()
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable { subCatExpanded = true }
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = subCatExpanded,
                                                    onDismissRequest = { subCatExpanded = false }
                                                ) {
                                                    sublist.forEach { sub ->
                                                        DropdownMenuItem(
                                                            text = { Text(sub) },
                                                            onClick = {
                                                                recSubCat = sub
                                                                subCatExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Start Date and End Date Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val startDateStr = remember(recStartDate) {
                                                SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(recStartDate))
                                            }
                                            val startCal = Calendar.getInstance().apply { timeInMillis = recStartDate }
                                            val startDialog = android.app.DatePickerDialog(
                                                context,
                                                { _, y, m, d ->
                                                    val cal = Calendar.getInstance()
                                                    cal.set(y, m, d, 0, 0, 0)
                                                    recStartDate = cal.timeInMillis
                                                },
                                                startCal.get(Calendar.YEAR),
                                                startCal.get(Calendar.MONTH),
                                                startCal.get(Calendar.DAY_OF_MONTH)
                                            )

                                            val endDateStr = remember(recEndDate, hasEndDate) {
                                                if (!hasEndDate) "No End Date"
                                                else SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(recEndDate))
                                            }
                                            val endCal = Calendar.getInstance().apply { timeInMillis = recEndDate }
                                            val endDialog = android.app.DatePickerDialog(
                                                context,
                                                { _, y, m, d ->
                                                    val cal = Calendar.getInstance()
                                                    cal.set(y, m, d, 23, 59, 59)
                                                    recEndDate = cal.timeInMillis
                                                    hasEndDate = true
                                                },
                                                endCal.get(Calendar.YEAR),
                                                endCal.get(Calendar.MONTH),
                                                endCal.get(Calendar.DAY_OF_MONTH)
                                            )

                                            // Start Date Button Field
                                            Box(modifier = Modifier.weight(1f)) {
                                                OutlinedTextField(
                                                    value = startDateStr,
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                                    label = { Text("Start Date", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                    trailingIcon = { Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp)) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { startDialog.show() }
                                                )
                                            }

                                            // End Date Button Field
                                            Box(modifier = Modifier.weight(1f)) {
                                                OutlinedTextField(
                                                    value = endDateStr,
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    singleLine = true,
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                                    label = { Text("End Date", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                    trailingIcon = {
                                                        if (hasEndDate) {
                                                            IconButton(
                                                                onClick = { hasEndDate = false },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(Icons.Default.Clear, null, modifier = Modifier.size(14.dp))
                                                            }
                                                        } else {
                                                            Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp))
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { endDialog.show() }
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isEditingRecurring) {
                                                TextButton(
                                                    onClick = {
                                                        editingRecurringId = null
                                                        recName = ""
                                                        recAmount = ""
                                                        recNotes = ""
                                                        recFrequency = "Monthly"
                                                        recWalletId = walletBucketAssets.firstOrNull()?.id ?: -1
                                                        recMainCat = spendingCategories.keys.firstOrNull() ?: "Food"
                                                        recSubCat = spendingCategories[recMainCat]?.firstOrNull() ?: "Groceries"
                                                        recStartDate = System.currentTimeMillis()
                                                        hasEndDate = false
                                                        recEndDate = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000L
                                                    },
                                                    modifier = Modifier.padding(end = 8.dp)
                                                ) {
                                                    Text("Cancel")
                                                }
                                            }
                                            Button(
                                                onClick = {
                                                    if (recName.isNotBlank() && recWalletId != -1) {
                                                        val amt = recAmount.toDoubleOrNull() ?: 0.0
                                                        viewModel.saveRecurringExpense(
                                                            NetWorthViewModel.RecurringExpense(
                                                                id = editingRecurringId ?: java.util.UUID.randomUUID().toString(),
                                                                name = recName.trim(),
                                                                amount = amt,
                                                                walletId = recWalletId,
                                                                mainCategory = recMainCat,
                                                                subcategory = recSubCat,
                                                                frequency = recFrequency,
                                                                notes = recNotes.trim(),
                                                                lastPostedTimestamp = if (isEditingRecurring) {
                                                                    recurringList.find { it.id == editingRecurringId }?.lastPostedTimestamp ?: 0L
                                                                } else {
                                                                    0L
                                                                },
                                                                isEnabled = if (isEditingRecurring) {
                                                                    recurringList.find { it.id == editingRecurringId }?.isEnabled ?: true
                                                                } else {
                                                                    true
                                                                },
                                                                startDateTimestamp = recStartDate,
                                                                endDateTimestamp = if (hasEndDate) recEndDate else null
                                                            )
                                                        )
                                                        editingRecurringId = null
                                                        recName = ""
                                                        recAmount = ""
                                                        recNotes = ""
                                                        recFrequency = "Monthly"
                                                        recMainCat = spendingCategories.keys.firstOrNull() ?: "Food"
                                                        recSubCat = spendingCategories[recMainCat]?.firstOrNull() ?: "Groceries"
                                                        recStartDate = System.currentTimeMillis()
                                                        hasEndDate = false
                                                        recEndDate = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000L
                                                    }
                                                },
                                                enabled = recName.isNotBlank() && recWalletId != -1,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(if (isEditingRecurring) "Save Changes" else "Add Expense")
                                            }
                                        }
                                    }
                                }

                                Divider()

                                // List existing recurring expenses
                                if (recurringList.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No recurring expenses configured yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    recurringList.forEach { r ->
                                        val wAsset = walletBucketAssets.find { it.id == r.walletId }
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Text(r.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                        SuggestionChip(
                                                            onClick = {},
                                                            label = { Text(r.frequency, fontSize = 10.sp) },
                                                            modifier = Modifier.height(24.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = "${r.mainCategory} > ${r.subcategory} | Amount: ${String.format(Locale.US, "%.2f", r.amount)} ${wAsset?.currency ?: "USD"}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = "Assigned Wallet: ${wAsset?.name ?: "Unknown Wallet"}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    val rangeStr = remember(r.startDateTimestamp, r.endDateTimestamp) {
                                                        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                                                        val startStr = sdf.format(Date(r.startDateTimestamp))
                                                        val endStr = if (r.endDateTimestamp != null) sdf.format(Date(r.endDateTimestamp)) else "No End Date"
                                                        "Duration: $startStr to $endStr"
                                                    }
                                                    Text(
                                                        text = rangeStr,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (r.notes.isNotBlank()) {
                                                        Text(r.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    
                                                    val lastPostedStr = remember(r.lastPostedTimestamp) {
                                                        if (r.lastPostedTimestamp == 0L) {
                                                            "Never posted"
                                                        } else {
                                                            val cal = java.util.Calendar.getInstance()
                                                            cal.timeInMillis = r.lastPostedTimestamp
                                                            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US)
                                                            "Last posted: ${sdf.format(cal.time)}"
                                                        }
                                                    }
                                                    Text(
                                                        text = lastPostedStr,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    // Toggle
                                                    Switch(
                                                        checked = r.isEnabled,
                                                        onCheckedChange = { isChecked ->
                                                            viewModel.saveRecurringExpense(r.copy(isEnabled = isChecked))
                                                        },
                                                        modifier = Modifier.scale(0.7f).testTag("toggle_recurring_${r.id}")
                                                    )
                                                    
                                                    IconButton(
                                                        onClick = {
                                                            editingRecurringId = r.id
                                                            recName = r.name
                                                            recAmount = if (r.amount > 0.0) r.amount.toString() else ""
                                                            recNotes = r.notes
                                                            recFrequency = r.frequency
                                                            recWalletId = r.walletId
                                                            recMainCat = r.mainCategory
                                                            recSubCat = r.subcategory
                                                            recStartDate = r.startDateTimestamp
                                                            hasEndDate = r.endDateTimestamp != null
                                                            recEndDate = r.endDateTimestamp ?: (System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000L)
                                                        },
                                                        modifier = Modifier.size(36.dp).testTag("edit_recurring_${r.id}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Edit,
                                                            contentDescription = "Edit Recurring Expense",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    
                                                    IconButton(
                                                        onClick = { viewModel.deleteRecurringExpense(r.id) },
                                                        modifier = Modifier.size(36.dp).testTag("delete_recurring_${r.id}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete Recurring Expense",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(18.dp)
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
                }
            }
        }
    }

    // Modal Sub-Dialog for editing category name & subcategories
    if (editingCategoryName != null) {
        val originalName = editingCategoryName!!
        var currentEditName by remember { mutableStateOf(originalName) }
        var currentEditSubs by remember { mutableStateOf(editingSubcategoriesText) }

        Dialog(onDismissRequest = { editingCategoryName = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Edit Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = currentEditName,
                        onValueChange = { currentEditName = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = currentEditSubs,
                        onValueChange = { currentEditSubs = it },
                        label = { Text("Subcategories (comma separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { editingCategoryName = null }) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (currentEditName.isNotBlank()) {
                                    val subs = currentEditSubs.split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .ifEmpty { listOf("General") }
                                    viewModel.updateSpendingCategory(originalName, currentEditName.trim(), subs)
                                    editingCategoryName = null
                                }
                            },
                            enabled = currentEditName.isNotBlank(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    spendingCategoryToDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { spendingCategoryToDelete = null },
            title = { Text("Delete Spending Category?") },
            text = { Text("Are you sure you want to delete \"$category\"? Deleting this category will unassign it from any transaction records referencing it.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSpendingCategory(category)
                        spendingCategoryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { spendingCategoryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun WalletBalanceAdjustmentDialog(
    spendingWallets: List<Asset>,
    assetMetrics: List<AssetWithMetrics>,
    walletAliases: Map<Int, String>,
    valuesHidden: Boolean,
    onDismiss: () -> Unit,
    onAdjust: (walletId: Int, difference: Double, note: String) -> Unit,
    walletIcons: Map<Int, String> = emptyMap()
) {
    var selectedWallet by remember { mutableStateOf(spendingWallets.firstOrNull()) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val currentWallet = selectedWallet
    val currentBalance = remember(currentWallet, assetMetrics) {
        if (currentWallet == null) 0.0 else {
            val metric = assetMetrics.firstOrNull { it.asset.id == currentWallet.id }
            metric?.currentValuationNative ?: currentWallet.currentValuation
        }
    }

    var newBalanceInput by remember(currentWallet) { 
        mutableStateOf(if (currentBalance == 0.0) "" else String.format(Locale.US, "%.2f", currentBalance)) 
    }
    
    var reasonInput by remember { mutableStateOf("") }

    val parsedNewBalance = newBalanceInput.toDoubleOrNull()
    val difference = if (parsedNewBalance != null) parsedNewBalance - currentBalance else 0.0

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Adjust Wallet Balance",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Select Wallet Dropdown
                if (spendingWallets.size > 1) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedWallet?.let { walletAliases[it.id] ?: it.name } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Wallet") },
                            leadingIcon = selectedWallet?.let { w ->
                                {
                                    val currentIcon = walletIcons[w.id]
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            WalletIconView(
                                                iconKey = currentIcon,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = if (dropdownExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle wallet list",
                                    modifier = Modifier.clickable { dropdownExpanded = !dropdownExpanded }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dropdownExpanded = true }
                                .testTag("adjustment_wallet_select")
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { dropdownExpanded = true }
                        )
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            spendingWallets.forEach { w ->
                                DropdownMenuItem(
                                    text = { Text(walletAliases[w.id] ?: w.name) },
                                    leadingIcon = {
                                        val currentIcon = walletIcons[w.id]
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                WalletIconView(
                                                    iconKey = currentIcon,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedWallet = w
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = selectedWallet?.let { walletAliases[it.id] ?: it.name } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Wallet") },
                        leadingIcon = selectedWallet?.let { w ->
                            {
                                val currentIcon = walletIcons[w.id]
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        WalletIconView(
                                            iconKey = currentIcon,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Current Balance Display
                selectedWallet?.let { w ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current Balance",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatAssetCurrency(currentBalance, w.currency, valuesHidden = valuesHidden),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // New Balance Input
                OutlinedTextField(
                    value = newBalanceInput,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.toDoubleOrNull() != null || input == "-" || input == "." || input == "-.") {
                            newBalanceInput = input
                        }
                    },
                    label = { Text("New Balance") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("adjustment_new_balance_input")
                )

                // Difference Calculation Display
                if (parsedNewBalance != null) {
                    val formattedDiff = formatAssetCurrency(Math.abs(difference), selectedWallet?.currency ?: "USD", valuesHidden = valuesHidden)
                    val (textColor, labelText) = when {
                        difference > 0 -> Color(0xFF10B981) to "Inflow / Income (+$formattedDiff)"
                        difference < 0 -> MaterialTheme.colorScheme.error to "Outflow / Expense (-$formattedDiff)"
                        else -> MaterialTheme.colorScheme.onSurfaceVariant to "No Change ($formattedDiff)"
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Adjustment Type:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }

                // Reason Input
                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = { Text("Adjustment Note / Reason") },
                    placeholder = { Text("e.g. Forgotten grocery purchase") },
                    modifier = Modifier.fillMaxWidth().testTag("adjustment_reason_input")
                )

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            selectedWallet?.let { wallet ->
                                if (parsedNewBalance != null && difference != 0.0) {
                                    val finalNote = if (reasonInput.isBlank()) "Forgotten transaction adjustment" else reasonInput
                                    onAdjust(wallet.id, difference, finalNote)
                                }
                            }
                        },
                        enabled = parsedNewBalance != null && difference != 0.0,
                        modifier = Modifier.testTag("adjustment_submit_button")
                    ) {
                        Text("Save Adjustment")
                    }
                }
            }
        }
    }
}
