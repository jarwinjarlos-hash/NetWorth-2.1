package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AssetCategory
import com.example.data.model.Asset
import com.example.data.model.Transaction
import com.example.data.model.PortfolioSnapshot
import com.example.data.model.ChartPoint
import com.example.data.model.Bucket
import com.example.ui.viewmodel.NetWorthViewModel
import com.example.ui.viewmodel.AssetWithMetrics
import com.example.ui.viewmodel.CategoryWithMetrics
import com.example.ui.viewmodel.BucketWithMetrics
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

val CURRENCY_NAMES = mapOf(
    "USD" to "USD - United States Dollar",
    "EUR" to "EUR - Euro",
    "GBP" to "GBP - British Pound",
    "CAD" to "CAD - Canadian Dollar",
    "AUD" to "AUD - Australian Dollar",
    "SGD" to "SGD - Singapore Dollar",
    "JPY" to "JPY - Japanese Yen",
    "PHP" to "PHP - Philippine Peso",
    "SAR" to "SAR - Saudi Riyal",
    "BTC" to "BTC - Bitcoin"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthDashboard(
    viewModel: NetWorthViewModel,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val categoryMetrics by viewModel.categoryMetrics.collectAsStateWithLifecycle()
    val bucketMetrics by viewModel.bucketMetrics.collectAsStateWithLifecycle()
    val buckets by viewModel.buckets.collectAsStateWithLifecycle()
    val assetMetrics by viewModel.assetMetrics.collectAsStateWithLifecycle()
    val portfolioSummary by viewModel.portfolioSummary.collectAsStateWithLifecycle()
    val snapshots by viewModel.historicalPerformancePoints.collectAsStateWithLifecycle()
    val selectedTimeline by viewModel.selectedTimeline.collectAsStateWithLifecycle()
    val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()
    val valuesHidden by viewModel.valuesHidden.collectAsStateWithLifecycle()
    val themeIndex by viewModel.themeIndex.collectAsStateWithLifecycle()
    val appLockPin by viewModel.appLockPin.collectAsStateWithLifecycle()
    val useBiometrics by viewModel.useBiometrics.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var isUnlocked by rememberSaveable { mutableStateOf(viewModel.appLockPin.value == null) }

    var showManageCategoriesMode by remember { mutableStateOf<Boolean?>(null) }
    var showManageBuckets by remember { mutableStateOf(false) }
    var showBarChart by remember { mutableStateOf(false) }
    var showAddAssetType by remember { mutableStateOf<Boolean?>(null) }
    var expandedCategoryIds by rememberSaveable { mutableStateOf(emptySet<Int>()) }
    var expandedBucketId by remember { mutableStateOf<Int?>(null) }
    var activeTransactionAsset by remember { mutableStateOf<Asset?>(null) }
    var showAssetHistory by remember { mutableStateOf<Asset?>(null) }
    var editingCategory by remember { mutableStateOf<AssetCategory?>(null) }
    var showSeedOptions by remember { mutableStateOf(false) }
    var assetToArchive by remember { mutableStateOf<Asset?>(null) }
    var assetToEdit by remember { mutableStateOf<Asset?>(null) }
    var assetToAssignBucket by remember { mutableStateOf<Asset?>(null) }
    var groupingMode by remember { mutableStateOf("CATEGORY") } // "CATEGORY" or "BUCKET"
    var activeTab by remember { mutableStateOf(0) }
    var showPlusMenu by remember { mutableStateOf(false) }
    var showDropdownMenu by remember { mutableStateOf(false) }
    var showAppSettingsDialog by remember { mutableStateOf(false) }
    var showDatabaseBackupDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var walletDefaultTxType by remember { mutableStateOf("DEPOSIT") }
    var showUpdateManualHoldings by remember { mutableStateOf(false) }

    var activeWalletAssetId by rememberSaveable { mutableStateOf<Int?>(null) }
    var activeWalletTab by rememberSaveable { mutableStateOf(0) }
    var showWalletTransactionDialog by remember { mutableStateOf(false) }
    var walletTxToEdit by remember { mutableStateOf<Transaction?>(null) }
    var showWalletSettingsDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    var categoryToDelete by remember { mutableStateOf<AssetCategory?>(null) }
    var bucketToDelete by remember { mutableStateOf<Bucket?>(null) }

    var mainDraggedCategoryId by remember { mutableStateOf<Int?>(null) }
    var mainDragOffsetY by remember { mutableStateOf(0f) }
    val mainCategoryHeights = remember { mutableStateMapOf<Int, Int>() }

    var isExcludedSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var isAssetClassesExpanded by rememberSaveable { mutableStateOf(false) }
    var isLiabilityClassesExpanded by rememberSaveable { mutableStateOf(false) }

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val dashboardLazyListState = rememberLazyListState()
    var highlightedAssetId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(highlightedAssetId) {
        if (highlightedAssetId != null) {
            kotlinx.coroutines.delay(3000)
            highlightedAssetId = null
        }
    }

    val defaultSpendingWalletId by viewModel.defaultSpendingWalletId.collectAsStateWithLifecycle()
    val spendingWalletIds by viewModel.spendingWalletIds.collectAsStateWithLifecycle()
    val walletAliases by viewModel.walletAliases.collectAsStateWithLifecycle()
    val walletIcons by viewModel.walletIcons.collectAsStateWithLifecycle()

    var walletHistoryFilterWalletId by rememberSaveable { mutableStateOf<Int?>(null) }
    var walletHistoryFilterCurrency by rememberSaveable { mutableStateOf<String?>(null) }

    var walletReportFilterWalletId by rememberSaveable { mutableStateOf<Int?>(null) }
    var walletReportFilterCurrency by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(activeWalletAssetId) {
        if (activeWalletAssetId != null) {
            walletHistoryFilterWalletId = activeWalletAssetId
            walletHistoryFilterCurrency = null
            walletReportFilterWalletId = activeWalletAssetId
            walletReportFilterCurrency = null
        }
    }

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

    val spendingWallets = remember(assets, cashCategoryIds, spendingWalletIds) {
        val filtered = if (spendingWalletIds.isNotEmpty()) {
            assets.filter { it.id in spendingWalletIds }
        } else {
            assets.filter { cashCategoryIds.contains(it.categoryId) }
        }
        filtered.filter { it.includeInPortfolio }
    }

    if (!isUnlocked) {
        LockScreen(
            appLockPin = appLockPin,
            useBiometrics = useBiometrics,
            onSuccessfulUnlock = { isUnlocked = true }
        )
    } else if (showUpdateManualHoldings) {
        UpdateManualHoldingsScreen(
            viewModel = viewModel,
            assets = assets,
            categories = categories,
            transactions = transactions,
            assetMetrics = assetMetrics,
            onBack = { showUpdateManualHoldings = false },
            modifier = modifier
        )
    } else {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .testTag("dashboard_scaffold"),
        topBar = {
            if (activeWalletAssetId != null) {
                val activeWallet = assets.find { it.id == activeWalletAssetId }
                TopAppBar(
                    title = {
                        if (activeWallet != null) {
                            if (activeWalletTab == 1) {
                                var dropdownExpanded by remember { mutableStateOf(false) }
                                val currentFilterWallet = assets.find { it.id == walletHistoryFilterWalletId }
                                val currentFilterAlias = currentFilterWallet?.let { walletAliases[it.id] }
                                
                                val uniqueCurrencies = remember(spendingWallets) {
                                    spendingWallets.map { it.currency }.distinct().sorted()
                                }

                                val displayTitle = if (walletHistoryFilterWalletId == null && walletHistoryFilterCurrency == null) {
                                    "All Wallets Ledger"
                                } else if (walletHistoryFilterCurrency != null) {
                                    "All $walletHistoryFilterCurrency Wallets"
                                } else {
                                    currentFilterAlias ?: currentFilterWallet?.name ?: "Unknown Wallet"
                                }

                                val displaySubtitle = if (walletHistoryFilterWalletId == null && walletHistoryFilterCurrency == null) {
                                    "Showing transaction stream for all liquid wallets"
                                } else if (walletHistoryFilterCurrency != null) {
                                    "Showing all transactions in $walletHistoryFilterCurrency"
                                } else {
                                    if (currentFilterAlias != null) currentFilterWallet.name else "Wallet Mode Scope (${currentFilterWallet?.currency ?: ""})"
                                }

                                Box {
                                    Column(
                                        modifier = Modifier
                                            .clickable { dropdownExpanded = true }
                                            .padding(vertical = 4.dp, horizontal = 8.dp)
                                            .testTag("ledger_wallet_filter_header")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = displayTitle,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Select wallet filter",
                                                tint = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                        Text(
                                            text = displaySubtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = dropdownExpanded,
                                        onDismissRequest = { dropdownExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { 
                                                Text(
                                                    text = "All Wallets",
                                                    fontWeight = if (walletHistoryFilterWalletId == null && walletHistoryFilterCurrency == null) FontWeight.Bold else FontWeight.Normal
                                                ) 
                                            },
                                            onClick = {
                                                walletHistoryFilterWalletId = null
                                                walletHistoryFilterCurrency = null
                                                dropdownExpanded = false
                                            },
                                            modifier = Modifier.testTag("filter_all_wallets_item")
                                        )

                                        if (uniqueCurrencies.isNotEmpty()) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                            Text(
                                                text = "  BY CURRENCY",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                            )
                                            uniqueCurrencies.forEach { curr ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = "All $curr Wallets",
                                                            fontWeight = if (walletHistoryFilterWalletId == null && walletHistoryFilterCurrency == curr) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    onClick = {
                                                        walletHistoryFilterWalletId = null
                                                        walletHistoryFilterCurrency = curr
                                                        dropdownExpanded = false
                                                    },
                                                    modifier = Modifier.testTag("filter_currency_${curr}_item")
                                                )
                                            }
                                        }

                                        if (spendingWallets.isNotEmpty()) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                            Text(
                                                text = "  INDIVIDUAL WALLETS",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                            )
                                            spendingWallets.forEach { w ->
                                                val aliasName = walletAliases[w.id] ?: w.name
                                                DropdownMenuItem(
                                                    text = { 
                                                        Text(
                                                            text = aliasName,
                                                            fontWeight = if (walletHistoryFilterWalletId == w.id) FontWeight.Bold else FontWeight.Normal
                                                        ) 
                                                    },
                                                    onClick = {
                                                        walletHistoryFilterWalletId = w.id
                                                        walletHistoryFilterCurrency = null
                                                        dropdownExpanded = false
                                                    },
                                                    modifier = Modifier.testTag("filter_wallet_${w.id}_item")
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (activeWalletTab == 2) {
                                var dropdownExpanded by remember { mutableStateOf(false) }
                                val currentFilterWallet = assets.find { it.id == walletReportFilterWalletId }
                                val currentFilterAlias = currentFilterWallet?.let { walletAliases[it.id] }
                                
                                val uniqueCurrencies = remember(spendingWallets) {
                                    spendingWallets.map { it.currency }.distinct().sorted()
                                }

                                val displayTitle = if (walletReportFilterWalletId == null && walletReportFilterCurrency == null) {
                                    "All Wallets Reports"
                                } else if (walletReportFilterCurrency != null) {
                                    "All $walletReportFilterCurrency Wallets"
                                } else {
                                    currentFilterAlias ?: currentFilterWallet?.name ?: "Unknown Wallet"
                                }

                                val displaySubtitle = if (walletReportFilterWalletId == null && walletReportFilterCurrency == null) {
                                    "Showing performance for all liquid wallets"
                                } else if (walletReportFilterCurrency != null) {
                                    "Showing performance in $walletReportFilterCurrency"
                                } else {
                                    if (currentFilterAlias != null) currentFilterWallet.name else "Wallet Mode Scope (${currentFilterWallet?.currency ?: ""})"
                                }

                                Box {
                                    Column(
                                        modifier = Modifier
                                            .clickable { dropdownExpanded = true }
                                            .padding(vertical = 4.dp, horizontal = 8.dp)
                                            .testTag("report_wallet_filter_header")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = displayTitle,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Select wallet filter",
                                                tint = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                        Text(
                                            text = displaySubtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = dropdownExpanded,
                                        onDismissRequest = { dropdownExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { 
                                                Text(
                                                    text = "All Wallets",
                                                    fontWeight = if (walletReportFilterWalletId == null && walletReportFilterCurrency == null) FontWeight.Bold else FontWeight.Normal
                                                ) 
                                            },
                                            onClick = {
                                                walletReportFilterWalletId = null
                                                walletReportFilterCurrency = null
                                                dropdownExpanded = false
                                            },
                                            modifier = Modifier.testTag("report_filter_all_wallets_item")
                                        )

                                        if (uniqueCurrencies.isNotEmpty()) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                            Text(
                                                text = "  BY CURRENCY",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                            )
                                            uniqueCurrencies.forEach { curr ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = "All $curr Wallets",
                                                            fontWeight = if (walletReportFilterWalletId == null && walletReportFilterCurrency == curr) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    onClick = {
                                                        walletReportFilterWalletId = null
                                                        walletReportFilterCurrency = curr
                                                        dropdownExpanded = false
                                                    },
                                                    modifier = Modifier.testTag("report_filter_currency_${curr}_item")
                                                )
                                            }
                                        }

                                        if (spendingWallets.isNotEmpty()) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                            Text(
                                                text = "  INDIVIDUAL WALLETS",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                            )
                                            spendingWallets.forEach { w ->
                                                val aliasName = walletAliases[w.id] ?: w.name
                                                DropdownMenuItem(
                                                    text = { 
                                                        Text(
                                                            text = aliasName,
                                                            fontWeight = if (walletReportFilterWalletId == w.id) FontWeight.Bold else FontWeight.Normal
                                                        ) 
                                                    },
                                                    onClick = {
                                                        walletReportFilterWalletId = w.id
                                                        walletReportFilterCurrency = null
                                                        dropdownExpanded = false
                                                    },
                                                    modifier = Modifier.testTag("report_filter_wallet_${w.id}_item")
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                val alias = walletAliases[activeWallet.id]
                                Column {
                                    Text(
                                        text = alias ?: activeWallet.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = if (alias != null) activeWallet.name else "Wallet Mode Scope (${activeWallet.currency})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (alias != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Spending Wallet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        val notifications by viewModel.notifications.collectAsStateWithLifecycle()
                        val unreadCount = remember(notifications) { notifications.count { !it.isRead } }

                        IconButton(
                            onClick = { showNotificationsDialog = true },
                            modifier = Modifier.testTag("wallet_notification_bell_button")
                        ) {
                            Box {
                                Icon(
                                    imageVector = if (unreadCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                    contentDescription = "Wallet Notifications",
                                    tint = if (unreadCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                                if (unreadCount > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.error,
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 4.dp, y = (-4).dp)
                                            .size(14.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                                                color = MaterialTheme.colorScheme.onError,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = { showWalletSettingsDialog = true },
                            modifier = Modifier.testTag("wallet_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Wallet settings menu",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            } else if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search assets or liabilities...", style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_text_input"),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear search query"
                                        )
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Close search",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            } else {
                TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val icon = when (activeTab) {
                            0 -> Icons.Default.AccountBalance
                            1 -> Icons.Default.History
                            3 -> Icons.Default.Analytics
                            else -> Icons.Default.AccountBalanceWallet
                        }
                        val titleText = when (activeTab) {
                            0 -> "Net Worth"
                            1 -> "All Transactions"
                            3 -> "Projections"
                            else -> "Wallet"
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (activeTab == 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.padding(top = 1.dp)
                                ) {
                                    Text(
                                        text = "Beta 2.1",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (activeTab == 0) {
                        val startOfMonthVal = remember {
                            val cal = java.util.Calendar.getInstance()
                            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                            cal.set(java.util.Calendar.MINUTE, 0)
                            cal.set(java.util.Calendar.SECOND, 0)
                            cal.set(java.util.Calendar.MILLISECOND, 0)
                            cal.timeInMillis
                        }
                        val missingUpdatesCount = remember(assetMetrics, transactions) {
                            assetMetrics.count {
                                !it.asset.isArchived && transactions.none { tx ->
                                    tx.assetId == it.asset.id && tx.type == "UPDATE" && tx.timestamp >= startOfMonthVal
                                }
                            }
                        }

                        if (missingUpdatesCount > 0) {
                            IconButton(
                                onClick = { showUpdateManualHoldings = true },
                                modifier = Modifier.testTag("alarm_reminder_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = Color(0xFFEF4444)
                                        ) {
                                            Text(text = "$missingUpdatesCount", color = Color.White)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = "Review manually updated assets",
                                        tint = Color(0xFF34D399)
                                    )
                                }
                            }
                        }

                        // Search Button
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier.testTag("search_dashboard_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Assets and Liabilities",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Quick Toggle to Hide/Mask valuations
                        IconButton(
                            onClick = { viewModel.setValuesHidden(!valuesHidden) },
                            modifier = Modifier.testTag("hide_values_button")
                        ) {
                            Icon(
                                imageVector = if (valuesHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (valuesHidden) "Show valuation figures" else "Hide valuation figures",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showDropdownMenu = !showDropdownMenu },
                                modifier = Modifier.testTag("settings_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "App Options Menu",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showDropdownMenu,
                                onDismissRequest = { showDropdownMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("App Settings") },
                                    onClick = {
                                        showDropdownMenu = false
                                        showAppSettingsDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Database Backup") },
                                    onClick = {
                                        showDropdownMenu = false
                                        showDatabaseBackupDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("About") },
                                    onClick = {
                                        showDropdownMenu = false
                                        showAboutDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Load Demo Data") },
                                    onClick = {
                                        showDropdownMenu = false
                                        viewModel.seedDemoData()
                                        android.widget.Toast.makeText(context, "Demo data successfully seeded!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
            }
        },
        bottomBar = {
            if (activeWalletAssetId != null) {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    // 1st: Wallet Dashboard
                    NavigationBarItem(
                        selected = activeWalletTab == 0,
                        onClick = { activeWalletTab = 0 },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Wallet Dashboard") },
                        label = {
                            Text(
                                text = "Dashboard",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.3.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        },
                        modifier = Modifier.testTag("wallet_dashboard_tab")
                    )

                    // 2nd: Transaction History (Ledger)
                    NavigationBarItem(
                        selected = activeWalletTab == 1,
                        onClick = { activeWalletTab = 1 },
                        icon = { Icon(Icons.Default.History, contentDescription = "Wallet Ledger/History") },
                        label = {
                            Text(
                                text = "Ledger",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.3.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        },
                        modifier = Modifier.testTag("wallet_history_tab")
                    )

                    // 3rd: Add Transaction (+)
                    NavigationBarItem(
                        selected = false,
                        onClick = { showWalletTransactionDialog = true },
                        icon = {
                            Surface(
                                modifier = Modifier.size(54.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                tonalElevation = 6.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "New Wallet Entry",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        },
                        label = null,
                        modifier = Modifier.testTag("wallet_add_tab")
                    )

                    // 4th: Wallet Reports
                    NavigationBarItem(
                        selected = activeWalletTab == 2,
                        onClick = { activeWalletTab = 2 },
                        icon = { Icon(Icons.Default.Analytics, contentDescription = "Wallet Reports") },
                        label = {
                            Text(
                                text = "Reports",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.3.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        },
                        modifier = Modifier.testTag("wallet_reports_tab")
                    )

                    // 5th: Exit/Return to Net Worth Tracker
                    NavigationBarItem(
                        selected = false,
                        onClick = { 
                            activeWalletAssetId = null
                            activeTab = 0
                        },
                        icon = { Icon(Icons.Default.ExitToApp, contentDescription = "Exit to NetWorth Tracker") },
                        label = {
                            Text(
                                text = "Return",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.3.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        },
                        modifier = Modifier.testTag("wallet_exit_tab")
                    )
                }
            } else {
                NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = {
                        Text(
                            text = "Dashboard",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.3.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                )

                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = "Transactions") },
                    label = {
                        Text(
                            text = "History",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.3.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                )

                NavigationBarItem(
                    selected = false,
                    onClick = {
                        if (activeTab == 4) {
                            val cashKeys = categories.filter {
                                it.isAsset && (
                                    it.name.contains("cash", ignoreCase = true) ||
                                    it.name.contains("bank", ignoreCase = true) ||
                                    it.name.contains("liquid", ignoreCase = true) ||
                                    it.name.contains("wallet", ignoreCase = true) ||
                                    it.name.contains("savings", ignoreCase = true)
                                )
                            }.map { it.id }.toSet()
                            val firstCashMetric = assetMetrics.find { it.asset.categoryId in cashKeys && !it.asset.isArchived }
                            val firstCash = firstCashMetric?.asset ?: assets.firstOrNull { !it.isArchived }
                            if (firstCash != null) {
                                activeTransactionAsset = firstCash
                                walletDefaultTxType = "WITHDRAWAL"
                            } else {
                                showPlusMenu = true
                            }
                        } else {
                            showPlusMenu = true
                        }
                    },
                    icon = {
                        Surface(
                            modifier = Modifier.size(54.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            tonalElevation = 6.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Entry",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    },
                    label = null
                )

                NavigationBarItem(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    icon = { Icon(Icons.Default.Analytics, contentDescription = "Analytics Projections") },
                    label = {
                        Text(
                            text = "Analytics",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.3.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                )

                 NavigationBarItem(
                    selected = activeTab == 4,
                    onClick = {
                        activeTab = 4
                        // Immediately launch the independent Wallet Ecosystem mode on the configured default spending wallet
                        val defaultId = defaultSpendingWalletId
                        val assignedDefault = if (defaultId != null) assets.find { it.id == defaultId } else null
                        
                        val firstAssigned = if (assignedDefault == null) {
                            val firstId = spendingWalletIds.firstOrNull()
                            if (firstId != null) assets.find { it.id == firstId } else null
                        } else null
                        
                        val selectedWallet = assignedDefault ?: firstAssigned ?: assets.find { asset ->
                            val cat = categories.find { it.id == asset.categoryId }
                            cat != null && (
                                cat.name.contains("cash", ignoreCase = true) ||
                                cat.name.contains("bank", ignoreCase = true) ||
                                cat.name.contains("wallet", ignoreCase = true) ||
                                cat.name.contains("liquid", ignoreCase = true)
                            )
                        } ?: assets.firstOrNull { !it.isArchived }

                        if (selectedWallet != null) {
                            activeWalletAssetId = selectedWallet.id
                            activeWalletTab = 0
                        } else {
                            scope.launch {
                                var cashCat = categories.find {
                                    it.isAsset && (
                                        it.name.contains("cash", ignoreCase = true) ||
                                        it.name.contains("bank", ignoreCase = true) ||
                                        it.name.contains("wallet", ignoreCase = true) ||
                                        it.name.contains("liquid", ignoreCase = true)
                                    )
                                }
                                val catId = if (cashCat == null) {
                                    viewModel.repository.insertCategory(com.example.data.model.AssetCategory(name = "Cash & Bank", isAsset = true)).toInt()
                                } else {
                                    cashCat.id
                                }
                                val newAssetId = viewModel.repository.insertAsset(
                                    com.example.data.model.Asset(
                                        name = "Main Wallet",
                                        currency = baseCurrency,
                                        categoryId = catId
                                    )
                                ).toInt()
                                viewModel.repository.insertTransaction(
                                    com.example.data.model.Transaction(
                                        assetId = newAssetId,
                                        type = "UPDATE",
                                        amount = 0.0,
                                        timestamp = System.currentTimeMillis(),
                                        notes = "Setup Main Wallet"
                                     )
                                )
                                viewModel.saveSpendingWalletIds(listOf(newAssetId))
                                viewModel.setDefaultSpendingWalletId(newAssetId)
                                activeWalletAssetId = newAssetId
                                activeWalletTab = 0
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Wallet") },
                    label = {
                        Text(
                            text = "Wallet",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.3.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                )
            }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    if (activeWalletAssetId != null) {
                        if (isSystemInDarkTheme()) Color(0xFF1F2937) else Color(0xFFF3F4F6)
                    } else {
                        MaterialTheme.colorScheme.background
                    }
                )
        ) {
            val isTablet = maxWidth > 650.dp

            if (isSearchActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable {
                            isSearchActive = false
                            searchQuery = ""
                        }
                        .zIndex(10f)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter)
                            .clickable(enabled = false) {}
                            .testTag("search_results_container"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Search Results",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            val filteredAssets = remember(searchQuery, assetMetrics) {
                                if (searchQuery.isBlank()) {
                                    emptyList()
                                } else {
                                    assetMetrics.filter {
                                        it.asset.name.contains(searchQuery, ignoreCase = true) ||
                                        it.categoryName.contains(searchQuery, ignoreCase = true)
                                    }
                                }
                            }

                            if (searchQuery.isBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Type name or category to find items...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else if (filteredAssets.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No matching items found.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 350.dp)
                                ) {
                                    items(filteredAssets, key = { "search_${it.asset.id}" }) { amt ->
                                        val isAsset = amt.isAssetType
                                        val formattedVal = formatPortfolioAmount(amt.currentValuationUsd, baseCurrency, viewModel, valuesHidden = valuesHidden)
                                        
                                        Column {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        groupingMode = "CATEGORY"
                                                        val assetCategoriesList = categoryMetrics.filter { it.category.isAsset }
                                                        val liabilityCategoriesList = categoryMetrics.filter { !it.category.isAsset }
                                                        val excludedAssetsList = assetMetrics.filter { !it.asset.isArchived && !it.asset.includeInPortfolio }

                                                        var targetIndex = -1
                                                        if (amt.asset.includeInPortfolio) {
                                                            expandedCategoryIds = expandedCategoryIds + amt.asset.categoryId
                                                            if (amt.isAssetType) {
                                                                isAssetClassesExpanded = true
                                                                val catIndex = assetCategoriesList.indexOfFirst { it.category.id == amt.asset.categoryId }
                                                                if (catIndex != -1) {
                                                                    targetIndex = 3 + catIndex
                                                                }
                                                            } else {
                                                                isLiabilityClassesExpanded = true
                                                                val catIndex = liabilityCategoriesList.indexOfFirst { it.category.id == amt.asset.categoryId }
                                                                if (catIndex != -1) {
                                                                    val offset = 3 + (if (isAssetClassesExpanded) assetCategoriesList.size else 0) + 1
                                                                    targetIndex = offset + catIndex
                                                                }
                                                            }
                                                        } else {
                                                            isExcludedSectionExpanded = true
                                                            val exclIndex = excludedAssetsList.indexOfFirst { it.asset.id == amt.asset.id }
                                                            if (exclIndex != -1) {
                                                                val offset = 3 + 
                                                                    (if (isAssetClassesExpanded) assetCategoriesList.size else 0) + 
                                                                    (if (liabilityCategoriesList.isNotEmpty()) 1 + (if (isLiabilityClassesExpanded) liabilityCategoriesList.size else 0) else 0) +
                                                                    1 // Excluded header
                                                                targetIndex = offset + exclIndex
                                                            }
                                                        }

                                                        highlightedAssetId = amt.asset.id
                                                        isSearchActive = false
                                                        searchQuery = ""

                                                        if (targetIndex != -1) {
                                                            scope.launch {
                                                                kotlinx.coroutines.delay(150)
                                                                try {
                                                                    dashboardLazyListState.animateScrollToItem(targetIndex)
                                                                } catch (e: Exception) {
                                                                    // Fallback ignore
                                                                }
                                                            }
                                                        }
                                                    }
                                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Surface(
                                                        color = if (isAsset) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = if (isAsset) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                                                contentDescription = null,
                                                                tint = if (isAsset) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }
                                                    Column {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            Text(
                                                                text = amt.asset.name,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            if (amt.asset.isArchived) {
                                                                Surface(
                                                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                                                    shape = RoundedCornerShape(4.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Archived",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.outline,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        Text(
                                                            text = amt.categoryName,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = formattedVal,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isAsset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                                )
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                if (activeWalletAssetId != null) {
                    val activeWallet = assets.find { it.id == activeWalletAssetId }
                    if (activeWallet != null) {
                        val walletTxs = transactions.filter { it.assetId == activeWallet.id && it.type != "UPDATE" }
                        when (activeWalletTab) {
                            0 -> {
                                WalletDashboardTabContent(
                                    walletAsset = activeWallet,
                                    allAssets = assets,
                                    categories = categories,
                                    transactions = transactions.filter { it.type != "UPDATE" },
                                    viewModel = viewModel,
                                    onSwitchWallet = { newWallet ->
                                        activeWalletAssetId = newWallet.id
                                    },
                                    onAddTransaction = {
                                        showWalletTransactionDialog = true
                                    },
                                    onViewAllTransactions = {
                                        activeWalletTab = 1
                                    }
                                )
                            }
                            1 -> {
                                val filteredLedgerTransactions = remember(transactions, walletHistoryFilterWalletId, walletHistoryFilterCurrency, spendingWallets) {
                                    val nonUpdateTxs = transactions.filter { it.type != "UPDATE" }
                                    val spendingWalletIdsSet = spendingWallets.map { it.id }.toSet()
                                    
                                    val baseTxs = nonUpdateTxs.filter { 
                                        it.assetId in spendingWalletIdsSet || (it.type == "TRANSFER" && it.destinationAssetId in spendingWalletIdsSet)
                                    }
                                    
                                    if (walletHistoryFilterWalletId != null) {
                                        nonUpdateTxs.filter { 
                                            it.assetId == walletHistoryFilterWalletId || 
                                            (it.type == "TRANSFER" && it.destinationAssetId == walletHistoryFilterWalletId)
                                        }
                                    } else if (walletHistoryFilterCurrency != null) {
                                        baseTxs.filter { tx ->
                                            val txWallet = assets.find { it.id == tx.assetId }
                                            val rxWallet = tx.destinationAssetId?.let { destId -> assets.find { it.id == destId } }
                                            txWallet?.currency == walletHistoryFilterCurrency || rxWallet?.currency == walletHistoryFilterCurrency
                                        }
                                    } else {
                                        baseTxs
                                    }
                                }

                                WalletHistoryTabContent(
                                    walletAsset = activeWallet,
                                    transactions = filteredLedgerTransactions,
                                    viewModel = viewModel,
                                    onEditTx = { tx ->
                                        walletTxToEdit = tx
                                        showWalletTransactionDialog = true
                                    },
                                    onDeleteTx = { tx ->
                                        viewModel.deleteTransaction(tx)
                                    },
                                    allAssets = assets,
                                    walletAliases = walletAliases,
                                    showWalletBadge = (walletHistoryFilterWalletId == null)
                                )
                            }
                            2 -> {
                                WalletReportsTabContent(
                                    walletAsset = activeWallet,
                                    allAssets = assets,
                                    categories = categories,
                                    allTransactions = transactions,
                                    viewModel = viewModel,
                                    filterWalletId = walletReportFilterWalletId,
                                    filterCurrency = walletReportFilterCurrency
                                )
                            }
                        }
                    }
                } else if (activeTab == 0) {
                    // Top Settings bar
                    AnimatedVisibility(
                        visible = showSeedOptions,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.seedDemoData()
                                        showSeedOptions = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 6.dp)
                                        .testTag("seed_data_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Load Demo Portfolio", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.clearAllData()
                                        showSeedOptions = false
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 6.dp)
                                        .testTag("clear_data_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Clear All", fontSize = 12.sp)
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Paid,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Base Portfolio Currency",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val currencies = listOf("USD", "EUR", "GBP", "CAD", "AUD", "SGD", "JPY", "PHP", "SAR", "BTC")
                                    currencies.forEach { curr ->
                                        val isSel = curr == baseCurrency
                                        FilterChip(
                                            selected = isSel,
                                            onClick = { viewModel.setBaseCurrency(curr) },
                                            label = { Text(curr, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    state = dashboardLazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Global portfolio summary stats (Gain/Loss + Percentage metrics)
                    item {
                        PortfolioSummaryHeader(
                            summary = portfolioSummary,
                            baseCurrency = baseCurrency,
                            viewModel = viewModel
                        )
                    }

                    // Interactive Timeline Selector + Dynamic Trendline Chart
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(16.dp)
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
                                    // Left: Toggle for Chart type (Line Trend vs Bar Chart)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                            .padding(3.dp)
                                    ) {
                                        val options = listOf("Line" to false, "Bar" to true)
                                        options.forEach { (label, isBar) ->
                                            val isSelected = showBarChart == isBar
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                                    .clickable { showBarChart = isBar }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }

                                    TimelineSelectorRow(
                                        selected = selectedTimeline,
                                        onSelected = { viewModel.setTimeline(it) }
                                    )
                                }

                                if (selectedTimeline == "CST") {
                                    val customStart by viewModel.customStartDate.collectAsStateWithLifecycle()
                                    val customEnd by viewModel.customEndDate.collectAsStateWithLifecycle()
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    val sdf = remember { java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()) }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Start Date button
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Start Date",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = customStart }
                                                        android.app.DatePickerDialog(
                                                            context,
                                                            { _, year, month, day ->
                                                                val selectedCal = java.util.Calendar.getInstance()
                                                                selectedCal.set(year, month, day, 0, 0, 0)
                                                                viewModel.setCustomDateRange(selectedCal.timeInMillis, customEnd)
                                                            },
                                                            cal.get(java.util.Calendar.YEAR),
                                                            cal.get(java.util.Calendar.MONTH),
                                                            cal.get(java.util.Calendar.DAY_OF_MONTH)
                                                        ).show()
                                                    }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CalendarToday,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = sdf.format(java.util.Date(customStart)),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )

                                        // End Date button
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "End Date",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = customEnd }
                                                        android.app.DatePickerDialog(
                                                            context,
                                                            { _, year, month, day ->
                                                                val selectedCal = java.util.Calendar.getInstance()
                                                                selectedCal.set(year, month, day, 23, 59, 59)
                                                                viewModel.setCustomDateRange(customStart, selectedCal.timeInMillis)
                                                            },
                                                            cal.get(java.util.Calendar.YEAR),
                                                            cal.get(java.util.Calendar.MONTH),
                                                            cal.get(java.util.Calendar.DAY_OF_MONTH)
                                                        ).show()
                                                    }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CalendarToday,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = sdf.format(java.util.Date(customEnd)),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                NetWorthHistoryGraph(
                                    snapshots = snapshots,
                                    baseCurrency = baseCurrency,
                                    viewModel = viewModel,
                                    showBarChart = showBarChart,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                )
                            }
                        }
                    }

                    // Secondary header for clarity with toggle options
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable(enabled = groupingMode == "CATEGORY") {
                                        isAssetClassesExpanded = !isAssetClassesExpanded
                                    }
                                    .padding(end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (groupingMode == "CATEGORY") "ASSET CLASSES" else "RETIREMENT BUCKETS",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                                if (groupingMode == "CATEGORY") {
                                    val assetCategoriesCount = categoryMetrics.count { it.category.isAsset }
                                    if (assetCategoriesCount > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                        ) {
                                            Text(
                                                text = assetCategoriesCount.toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (isAssetClassesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isAssetClassesExpanded) "Collapse" else "Expand",
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Grouping Mode switch
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (groupingMode == "CATEGORY") MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { groupingMode = "CATEGORY" }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("toggle_category_mode")
                                ) {
                                    Text(
                                        text = "Classes",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (groupingMode == "CATEGORY") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (groupingMode == "BUCKET") MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { groupingMode = "BUCKET" }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("toggle_bucket_mode")
                                ) {
                                    Text(
                                        text = "Buckets",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (groupingMode == "BUCKET") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Dynamically render collapsible listings depending on grouping mode
                    if (groupingMode == "CATEGORY") {
                        val assetCategories = categoryMetrics.filter { it.category.isAsset }
                        val liabilityCategories = categoryMetrics.filter { !it.category.isAsset }

                        if (assetCategories.isEmpty() && liabilityCategories.isEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.FolderOpen,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "No Asset Classes configured",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "Load dynamic demo portfolio or tap '+' below to begin.",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            if (isAssetClassesExpanded && assetCategories.isNotEmpty()) {
                                itemsIndexed(assetCategories, key = { _, cat -> cat.category.id }) { index, catWithMetrics ->
                                    val isExpanded = expandedCategoryIds.contains(catWithMetrics.category.id)
                                    val isCurrentDragged = mainDraggedCategoryId == catWithMetrics.category.id
 
                                    Box(
                                        modifier = Modifier
                                            .onGloballyPositioned { coordinates ->
                                                mainCategoryHeights[catWithMetrics.category.id] = coordinates.size.height
                                            }
                                            .offset {
                                                if (isCurrentDragged) {
                                                     IntOffset(0, mainDragOffsetY.roundToInt())
                                                } else {
                                                     IntOffset(0, 0)
                                                }
                                            }
                                            .zIndex(if (isCurrentDragged) 10f else 1f)
                                    ) {
                                        CategoryMetricCard(baseCurrency = baseCurrency, viewModel = viewModel,
                                            categoryMetrics = catWithMetrics,
                                            isExpanded = isExpanded,
                                            highlightedAssetId = highlightedAssetId,
                                            onToggleExpand = {
                                                expandedCategoryIds = if (isExpanded) expandedCategoryIds - catWithMetrics.category.id else expandedCategoryIds + catWithMetrics.category.id
                                            },
                                            onAddTransaction = { activeTransactionAsset = it },
                                            onViewHistory = { showAssetHistory = it },
                                            onToggleInclusion = { viewModel.toggleIncludeInPortfolio(it) },
                                            onArchive = { assetToArchive = it },
                                            onAssignBucket = { assetToAssignBucket = it },
                                            onEditAsset = { assetToEdit = it },
                                            dragIndex = index,
                                            onDragStart = { idx ->
                                                mainDraggedCategoryId = catWithMetrics.category.id
                                                mainDragOffsetY = 0f
                                            },
                                            onDrag = { delta ->
                                                mainDragOffsetY += delta
                                                val currIndex = index
                                                if (mainDragOffsetY > 0 && currIndex < assetCategories.size - 1) {
                                                    val nextCat = assetCategories[currIndex + 1]
                                                    val nextHeight = mainCategoryHeights[nextCat.category.id] ?: 0
                                                    if (mainDragOffsetY > nextHeight / 2f) {
                                                        val fullListOrder = categories.toMutableList()
                                                        val p1 = fullListOrder.indexOfFirst { it.id == catWithMetrics.category.id }
                                                        val p2 = fullListOrder.indexOfFirst { it.id == nextCat.category.id }
                                                        if (p1 != -1 && p2 != -1) {
                                                            val tmp = fullListOrder[p1]
                                                            fullListOrder[p1] = fullListOrder[p2]
                                                            fullListOrder[p2] = tmp
                                                            viewModel.saveCategoryOrder(fullListOrder.map { it.id })
                                                        }
                                                        mainDragOffsetY -= nextHeight
                                                    }
                                                } else if (mainDragOffsetY < 0 && currIndex > 0) {
                                                    val prevCat = assetCategories[currIndex - 1]
                                                    val prevHeight = mainCategoryHeights[prevCat.category.id] ?: 0
                                                    if (mainDragOffsetY < -prevHeight / 2f) {
                                                        val fullListOrder = categories.toMutableList()
                                                        val p1 = fullListOrder.indexOfFirst { it.id == catWithMetrics.category.id }
                                                        val p2 = fullListOrder.indexOfFirst { it.id == prevCat.category.id }
                                                        if (p1 != -1 && p2 != -1) {
                                                            val tmp = fullListOrder[p1]
                                                            fullListOrder[p1] = fullListOrder[p2]
                                                            fullListOrder[p2] = tmp
                                                            viewModel.saveCategoryOrder(fullListOrder.map { it.id })
                                                        }
                                                        mainDragOffsetY += prevHeight
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                mainDraggedCategoryId = null
                                                mainDragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                mainDraggedCategoryId = null
                                                mainDragOffsetY = 0f
                                            }
                                        )
                                    }
                                }
                            }

                            if (liabilityCategories.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                isLiabilityClassesExpanded = !isLiabilityClassesExpanded
                                            }
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "LIABILITY CLASSES",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                            )
                                            val liabilityCategoriesCount = liabilityCategories.size
                                            if (liabilityCategoriesCount > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                ) {
                                                    Text(
                                                        text = liabilityCategoriesCount.toString(),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Icon(
                                                imageVector = if (isLiabilityClassesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (isLiabilityClassesExpanded) "Collapse" else "Expand",
                                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                if (isLiabilityClassesExpanded) {
                                    itemsIndexed(liabilityCategories, key = { _, cat -> cat.category.id }) { index, catWithMetrics ->
                                    val isExpanded = expandedCategoryIds.contains(catWithMetrics.category.id)
                                    val isCurrentDragged = mainDraggedCategoryId == catWithMetrics.category.id
 
                                    Box(
                                        modifier = Modifier
                                            .onGloballyPositioned { coordinates ->
                                                mainCategoryHeights[catWithMetrics.category.id] = coordinates.size.height
                                            }
                                            .offset {
                                                if (isCurrentDragged) {
                                                    IntOffset(0, mainDragOffsetY.roundToInt())
                                                } else {
                                                    IntOffset(0, 0)
                                                }
                                            }
                                            .zIndex(if (isCurrentDragged) 10f else 1f)
                                    ) {
                                        CategoryMetricCard(baseCurrency = baseCurrency, viewModel = viewModel,
                                            categoryMetrics = catWithMetrics,
                                            isExpanded = isExpanded,
                                            highlightedAssetId = highlightedAssetId,
                                            onToggleExpand = {
                                                expandedCategoryIds = if (isExpanded) expandedCategoryIds - catWithMetrics.category.id else expandedCategoryIds + catWithMetrics.category.id
                                            },
                                            onAddTransaction = { activeTransactionAsset = it },
                                            onViewHistory = { showAssetHistory = it },
                                            onToggleInclusion = { viewModel.toggleIncludeInPortfolio(it) },
                                            onArchive = { assetToArchive = it },
                                            onAssignBucket = { assetToAssignBucket = it },
                                            onEditAsset = { assetToEdit = it },
                                            dragIndex = index,
                                            onDragStart = { idx ->
                                                mainDraggedCategoryId = catWithMetrics.category.id
                                                mainDragOffsetY = 0f
                                            },
                                            onDrag = { delta ->
                                                mainDragOffsetY += delta
                                                val currIndex = index
                                                if (mainDragOffsetY > 0 && currIndex < liabilityCategories.size - 1) {
                                                    val nextCat = liabilityCategories[currIndex + 1]
                                                    val nextHeight = mainCategoryHeights[nextCat.category.id] ?: 0
                                                    if (mainDragOffsetY > nextHeight / 2f) {
                                                        val fullListOrder = categories.toMutableList()
                                                        val p1 = fullListOrder.indexOfFirst { it.id == catWithMetrics.category.id }
                                                        val p2 = fullListOrder.indexOfFirst { it.id == nextCat.category.id }
                                                        if (p1 != -1 && p2 != -1) {
                                                            val tmp = fullListOrder[p1]
                                                            fullListOrder[p1] = fullListOrder[p2]
                                                            fullListOrder[p2] = tmp
                                                            viewModel.saveCategoryOrder(fullListOrder.map { it.id })
                                                        }
                                                        mainDragOffsetY -= nextHeight
                                                    }
                                                } else if (mainDragOffsetY < 0 && currIndex > 0) {
                                                    val prevCat = liabilityCategories[currIndex - 1]
                                                    val prevHeight = mainCategoryHeights[prevCat.category.id] ?: 0
                                                    if (mainDragOffsetY < -prevHeight / 2f) {
                                                        val fullListOrder = categories.toMutableList()
                                                        val p1 = fullListOrder.indexOfFirst { it.id == catWithMetrics.category.id }
                                                        val p2 = fullListOrder.indexOfFirst { it.id == prevCat.category.id }
                                                        if (p1 != -1 && p2 != -1) {
                                                            val tmp = fullListOrder[p1]
                                                            fullListOrder[p1] = fullListOrder[p2]
                                                            fullListOrder[p2] = tmp
                                                            viewModel.saveCategoryOrder(fullListOrder.map { it.id })
                                                        }
                                                        mainDragOffsetY += prevHeight
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                mainDraggedCategoryId = null
                                                mainDragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                mainDraggedCategoryId = null
                                                mainDragOffsetY = 0f
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                        // 3. Excluded Asset/Liability Section (MANDATORY per user request)
                        val excludedAssets = assetMetrics.filter { !it.asset.isArchived && !it.asset.includeInPortfolio }
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isExcludedSectionExpanded = !isExcludedSectionExpanded
                                    }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "EXCLUDED ASSET/LIABILITY",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                    if (excludedAssets.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                        ) {
                                            Text(
                                                text = excludedAssets.size.toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Icon(
                                    imageVector = if (isExcludedSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExcludedSectionExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (isExcludedSectionExpanded) {
                            if (excludedAssets.isEmpty()) {
                                item {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp, horizontal = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "All active asset/liability classes are included in net worth calculation.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(excludedAssets, key = { "excl_${it.asset.id}" }) { amt ->
                                    Box(modifier = Modifier.padding(vertical = 4.dp)) {
                                        SingleAssetMetricRow(
                                            amt = amt,
                                            baseCurrency = baseCurrency,
                                            viewModel = viewModel,
                                            onAddTransaction = { activeTransactionAsset = amt.asset },
                                            onViewHistory = { showAssetHistory = amt.asset },
                                            onToggleInclusion = { viewModel.toggleIncludeInPortfolio(amt.asset) },
                                            onArchive = { assetToArchive = amt.asset },
                                            onAssignBucket = { assetToAssignBucket = amt.asset },
                                            onEdit = { assetToEdit = amt.asset },
                                            isHighlighted = highlightedAssetId == amt.asset.id
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Bucket grouping mode!
                        if (bucketMetrics.isEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.Layers,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "No Retirement Buckets configured",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "Tap the bucket icon at the top to configure your retirement assets buckets strategy.",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                             items(bucketMetrics) { bWithMetrics ->
                                val bucketId = bWithMetrics.bucket?.id ?: -1
                                val isExpanded = expandedBucketId == bucketId
                                BucketMetricCard(baseCurrency = baseCurrency, viewModel = viewModel,
                                    bucketMetrics = bWithMetrics,
                                    isExpanded = isExpanded,
                                    highlightedAssetId = highlightedAssetId,
                                    onToggleExpand = {
                                        expandedBucketId = if (isExpanded) null else bucketId
                                    },
                                    onAddTransaction = { activeTransactionAsset = it },
                                    onViewHistory = { showAssetHistory = it },
                                    onToggleInclusion = { viewModel.toggleIncludeInPortfolio(it) },
                                    onArchive = { assetToArchive = it },
                                    onAssignBucket = { assetToAssignBucket = it },
                                    onEditAsset = { assetToEdit = it }
                                )
                            }
                        }
                    }

                    // Archived Items section (if any)
                    val archivedAssetsList = assetMetrics.filter { it.asset.isArchived }
                    if (archivedAssetsList.isNotEmpty()) {
                        item {
                            var showArchivedList by remember { mutableStateOf(false) }
                            var showDeleteConfirmByAsset by remember { mutableStateOf<Asset?>(null) }

                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("archived_section_card"),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showArchivedList = !showArchivedList },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Archive,
                                                contentDescription = "Archived Assets",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Archived Items (${archivedAssetsList.size})",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Icon(
                                            imageVector = if (showArchivedList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = "Toggle archived list",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    AnimatedVisibility(visible = showArchivedList) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            archivedAssetsList.forEach { amt ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(
                                                            MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = amt.asset.name,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = "${String.format("%,.2f", amt.currentValuationNative)} ${amt.asset.currency} (${amt.categoryName})",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        IconButton(
                                                            onClick = { viewModel.unarchiveAsset(amt.asset) },
                                                            modifier = Modifier.size(36.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Unarchive,
                                                                contentDescription = "Restore asset",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                        
                                                        IconButton(
                                                            onClick = { showDeleteConfirmByAsset = amt.asset },
                                                            modifier = Modifier.size(36.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.DeleteForever,
                                                                contentDescription = "Delete permanently",
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

                            if (showDeleteConfirmByAsset != null) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteConfirmByAsset = null },
                                    title = { Text("Delete Asset Permanently?") },
                                    text = { Text("Are you sure you want to permanently delete \"${showDeleteConfirmByAsset?.name}\" and ALL associated transaction histories? This action is irreversible.") },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                showDeleteConfirmByAsset?.let { viewModel.deleteAsset(it) }
                                                showDeleteConfirmByAsset = null
                                            },
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Text("Delete")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDeleteConfirmByAsset = null }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Bottom padding indicator for floating buttons
                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
                } else if (activeTab == 1) {
                    AllTransactionsTab(
                        transactions = transactions,
                        assets = assets,
                        onDeleteTransaction = { tx ->
                            transactionToDelete = tx
                        },
                        viewModel = viewModel
                    )
                } else if (activeTab == 3) {
                    AnalyticsTab(
                        transactions = transactions,
                        assets = assets,
                        categories = categories,
                        viewModel = viewModel
                    )
                } else if (activeTab == 4) {
                    WalletTab(
                        viewModel = viewModel,
                        transactions = transactions,
                        assets = assets,
                        categories = categories,
                        onLaunchWalletMode = { activeWalletAssetId = it.id }
                    )
                }
            }
        }
    }

    if (showWalletTransactionDialog) {
        val activeWallet = assets.find { it.id == activeWalletAssetId }
        if (activeWallet != null) {
            WalletTransactionDialog(
                walletAsset = activeWallet,
                viewModel = viewModel,
                onDismiss = {
                    showWalletTransactionDialog = false
                    walletTxToEdit = null
                },
                editingTransaction = walletTxToEdit,
                onSave = { walletId, type, amount, notes, date ->
                    showWalletTransactionDialog = false
                    val txToEdit = walletTxToEdit
                    if (txToEdit != null) {
                        viewModel.updateTransaction(
                            id = txToEdit.id,
                            assetId = walletId,
                            type = type,
                            amount = amount,
                            destinationAssetId = txToEdit.destinationAssetId,
                            exchangeRate = txToEdit.exchangeRate,
                            notes = notes,
                            timestamp = date
                        )
                        walletTxToEdit = null
                    } else {
                        viewModel.addTransaction(
                            assetId = walletId,
                            type = type,
                            amount = amount,
                            notes = notes,
                            customTimestamp = date
                        )
                    }
                }
            )
        }
    }

    // Modal Sheet 1: Manage Categories
    showManageCategoriesMode?.let { isAssetMode ->
        ManageCategoriesDialog(
            categories = categories,
            isAssetMode = isAssetMode,
            onDismiss = { showManageCategoriesMode = null },
            onSaveCategory = { name, isAsset ->
                viewModel.addCategory(name, isAsset)
            },
            onDeleteCategory = { categoryToDelete = it }
        )
    }

    // Modal Sheet 1.5: Manage Buckets
    if (showManageBuckets) {
        ManageBucketsDialog(
            buckets = buckets,
            baseCurrency = baseCurrency,
            onDismiss = { showManageBuckets = false },
            onSaveBucket = { name, description, targetAmount, isDecum, yearlySpend, bufferYrs, warningPct, targetGain, lastPerf ->
                viewModel.addBucket(
                    name = name,
                    description = description,
                    targetAmount = targetAmount,
                    isDecumulation = isDecum,
                    yearlySpendBudget = yearlySpend,
                    bufferYears = bufferYrs,
                    warningThresholdPercent = warningPct,
                    targetGainPercent = targetGain,
                    lastYearPerformancePercent = lastPerf
                )
            },
            onDeleteBucket = { bucketToDelete = it },
            onUpdateBucket = { id, name, description, targetAmount, isDecum, yearlySpend, bufferYrs, warningPct, targetGain, lastPerf ->
                viewModel.updateBucket(
                    id = id,
                    name = name,
                    description = description,
                    targetAmount = targetAmount,
                    isDecumulation = isDecum,
                    yearlySpendBudget = yearlySpend,
                    bufferYears = bufferYrs,
                    warningThresholdPercent = warningPct,
                    targetGainPercent = targetGain,
                    lastYearPerformancePercent = lastPerf
                )
            }
        )
    }

    transactionToDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("Delete Transaction permanently?") },
            text = { Text("Are you sure you want to permanently delete this transaction? This action is irreversible.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTransaction(tx)
                        transactionToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Category permanently?") },
            text = { Text("Are you sure you want to permanently delete category \"${cat.name}\"? Associated items will be unassigned.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCategory(cat)
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    bucketToDelete?.let { bucket ->
        AlertDialog(
            onDismissRequest = { bucketToDelete = null },
            title = { Text("Delete Bucket permanently?") },
            text = { Text("Are you sure you want to permanently delete bucket \"${bucket.name}\"? Associated assets will be unassigned.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBucket(bucket)
                        bucketToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { bucketToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal Sheet 1.6: Assign Asset to Bucket
    assetToAssignBucket?.let { asset ->
        AssignBucketDialog(
            asset = asset,
            buckets = buckets,
            onDismiss = { assetToAssignBucket = null },
            onAssign = { bucketId ->
                viewModel.assignAssetToBucket(asset, bucketId)
                assetToAssignBucket = null
            }
        )
    }

    // Modal Sheet 2: Add Asset or Liability
    showAddAssetType?.let { isAsset ->
        AddAssetDialog(
            categories = categories,
            isAssetMode = isAsset,
            onDismiss = { showAddAssetType = null },
            onSave = { name, currency, categoryId ->
                viewModel.addAsset(name, currency, categoryId)
                showAddAssetType = null
            }
        )
    }

    // Modal Sheet 2.5: Edit Asset or Liability
    assetToEdit?.let { asset ->
        EditAssetDialog(
            asset = asset,
            categories = categories,
            onDismiss = { assetToEdit = null },
            onSave = { name, currency, categoryId ->
                viewModel.updateAsset(asset.id, name, currency, categoryId)
                assetToEdit = null
            }
        )
    }

    if (showPlusMenu) {
        AddActionSelectionDialog(
            assets = assets,
            onDismiss = { showPlusMenu = false },
            onAddAsset = { showAddAssetType = true },
            onAddLiability = { showAddAssetType = false },
            onAddTransactionForAsset = { asset ->
                activeTransactionAsset = asset
            }
        )
    }

    // Modal Sheet 3: Add Transaction to an Asset
    activeTransactionAsset?.let { asset ->
        val existingAssets = assets.filter { it.id != asset.id }
        val cat = categories.find { it.id == asset.categoryId }
        val isAssetType = cat?.isAsset ?: true
        val resolvedDefaultType = if (isAssetType) walletDefaultTxType else "UPDATE"
        
        AddTransactionDialog(
            asset = asset,
            otherAssets = existingAssets,
            isAssetType = isAssetType,
            transactions = transactions,
            viewModel = viewModel,
            onDismiss = { 
                activeTransactionAsset = null 
                walletDefaultTxType = "DEPOSIT"
            },
            defaultType = resolvedDefaultType,
            onSave = { type, amount, destAssetId, rate, notes, date ->
                viewModel.addTransaction(
                    assetId = asset.id,
                    type = type,
                    amount = amount,
                    destinationAssetId = destAssetId,
                    exchangeRate = rate,
                    notes = notes,
                    customTimestamp = date
                )
                activeTransactionAsset = null
                walletDefaultTxType = "DEPOSIT"
            }
        )
    }

    // Modal Sheet 4: View and Edit Asset Ledger History
    showAssetHistory?.let { asset ->
        val assetTxs = transactions.filter { it.assetId == asset.id || it.destinationAssetId == asset.id }
        AssetHistoryDialog(
            asset = asset,
            transactions = assetTxs,
            otherAssets = assets,
            viewModel = viewModel,
            onDismiss = { showAssetHistory = null },
            onDeleteTx = { transactionToDelete = it },
            onUpdateTx = { id, assetId, type, amount, destAssetId, rate, notes, time ->
                viewModel.updateTransaction(id, assetId, type, amount, destAssetId, rate, notes, time)
            },
            onAddTransaction = {
                showAssetHistory = null
                activeTransactionAsset = asset
            }
        )
    }

    assetToArchive?.let { asset ->
        AlertDialog(
            onDismissRequest = { assetToArchive = null },
            title = { Text("Archive Asset?") },
            text = { Text("Are you sure you want to archive \"${asset.name}\"? This will hide it from active lists and exclude it from all portfolio calculations. You can view or restore it at any time from the bottom after archiving.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.archiveAsset(asset)
                        assetToArchive = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = { assetToArchive = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAppSettingsDialog) {
        AppSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showAppSettingsDialog = false },
            onManageCategories = { showManageCategoriesMode = true },
            onManageLiabilityCategories = { showManageCategoriesMode = false },
            onManageBuckets = { showManageBuckets = true }
        )
    }

    if (showDatabaseBackupDialog) {
        DatabaseBackupDialog(
            viewModel = viewModel,
            onDismiss = { showDatabaseBackupDialog = false }
        )
    }

    if (showWalletSettingsDialog) {
        WalletSettingsDialog(
            viewModel = viewModel,
            allAssets = assets,
            buckets = buckets,
            onDismiss = { showWalletSettingsDialog = false }
        )
    }

    if (showNotificationsDialog) {
        val notifications by viewModel.notifications.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            confirmButton = {
                TextButton(
                    onClick = { showNotificationsDialog = false },
                    modifier = Modifier.testTag("close_notifications_button")
                ) {
                    Text("Close")
                }
            },
            dismissButton = {
                if (notifications.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { viewModel.markAllNotificationsAsRead() },
                            modifier = Modifier.testTag("mark_all_read_button")
                        ) {
                            Text("Mark All Read")
                        }
                        TextButton(
                            onClick = { viewModel.clearAllNotifications() },
                            modifier = Modifier.testTag("clear_all_notifications_button")
                        ) {
                            Text("Clear All", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text("Wallet Alerts")
                }
            },
            text = {
                if (notifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "All Clear!",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "No budget warnings or limit exceedances.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(notifications) { notif ->
                            val itemBgColor = if (notif.isRead) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            }
                            
                            val alertColor = when (notif.type) {
                                "LIMIT_HIT" -> MaterialTheme.colorScheme.error
                                "RECURRING_DUE" -> MaterialTheme.colorScheme.primary
                                else -> Color(0xFFF97316) // Warning Orange
                            }
                            
                            val alertIcon = when (notif.type) {
                                "LIMIT_HIT" -> Icons.Default.Error
                                "RECURRING_DUE" -> Icons.Default.Schedule
                                else -> Icons.Default.Warning
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = itemBgColor),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(alertColor.copy(alpha = 0.1f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = alertIcon,
                                            contentDescription = null,
                                            tint = alertColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = notif.title,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (!notif.isRead) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                )
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        Text(
                                            text = notif.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (notif.type == "RECURRING_DUE" && notif.recurringExpenseId != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    viewModel.postRecurringExpense(notif.id, notif.recurringExpenseId)
                                                },
                                                modifier = Modifier.fillMaxWidth().testTag("post_recurring_expense_${notif.recurringExpenseId}"),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary
                                                ),
                                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 12.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "Acknowledge & Post",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        val timeFormatted = remember(notif.timestamp) {
                                            val cal = Calendar.getInstance()
                                            cal.timeInMillis = notif.timestamp
                                            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)
                                            sdf.format(cal.time)
                                        }
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = timeFormatted,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                            
                                            IconButton(
                                                onClick = { viewModel.deleteNotification(notif.id) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Notification",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
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
        )
    }

    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false }
        )
    }
    }
}

@Composable
fun TimelineSelectorRow(
    selected: String,
    onSelected: (String) -> Unit
) {
    val options = listOf("YTD", "1YR", "5YR", "ALL", "CST")
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .horizontalScroll(scrollState)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(2.dp)
    ) {
        options.forEach { opt ->
            val isSelected = selected == opt
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelected(opt) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = opt,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PortfolioSummaryHeader(
    summary: com.example.ui.viewmodel.PerformanceMetrics,
    baseCurrency: String,
    viewModel: NetWorthViewModel
) {
    val coinColor = Color(0xFFF59E0B)
    val positiveColor = Color(0xFF10B981)
    val negativeColor = Color(0xFFEF4444)
    val isPositive = summary.totalGainLossUsd >= 0
    val valuesHidden by viewModel.valuesHidden.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("net_worth_summary_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
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
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Surface(
                    color = if (isPositive) positiveColor.copy(alpha = 0.1f) else negativeColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    val signStr = if (isPositive) "+" else ""
                    Text(
                        text = "$signStr${String.format("%.2f", summary.totalGainLossPercent)}%",
                        color = if (isPositive) positiveColor else negativeColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = formatPortfolioAmount(summary.totalValuationUsd, baseCurrency, viewModel, valuesHidden = valuesHidden, isNetWorth = true),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier.testTag("net_worth_amount")
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Net Cost Basis",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatPortfolioAmount(summary.netDepositsUsd, baseCurrency, viewModel, valuesHidden = valuesHidden),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("total_assets_amount")
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "YTD Profit/Loss",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val isYtdPositive = summary.ytdGainLossUsd >= 0
                        val ytdSign = if (isYtdPositive) "+" else ""
                        Text(
                            text = "$ytdSign${formatPortfolioAmount(summary.ytdGainLossUsd, baseCurrency, viewModel, valuesHidden = valuesHidden)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isYtdPositive) positiveColor else negativeColor,
                            modifier = Modifier.testTag("ytd_gain_loss_amount")
                        )
                        Text(
                            text = "($ytdSign${String.format("%.1f", summary.ytdGainLossPercent)}%)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isYtdPositive) positiveColor else negativeColor
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Cumulative Profit/Loss:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = (if (isPositive) "+" else "") + formatPortfolioAmount(summary.totalGainLossUsd, baseCurrency, viewModel, valuesHidden = valuesHidden),
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isPositive) positiveColor else negativeColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Liabilities:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val formattedLiabilities = formatPortfolioAmount(summary.totalLiabilitiesUsd, baseCurrency, viewModel, valuesHidden = valuesHidden)
                val displayLiabilitiesStr = if (summary.totalLiabilitiesUsd > 0 && !valuesHidden) "-$formattedLiabilities" else formattedLiabilities
                Text(
                    text = displayLiabilitiesStr,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (summary.totalLiabilitiesUsd > 0) negativeColor else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun CategoryMetricCard(
    categoryMetrics: CategoryWithMetrics,
    isExpanded: Boolean,
    baseCurrency: String,
    highlightedAssetId: Int? = null,
    viewModel: NetWorthViewModel,
    onToggleExpand: () -> Unit,
    onAddTransaction: (Asset) -> Unit,
    onViewHistory: (Asset) -> Unit,
    onToggleInclusion: (Asset) -> Unit,
    onArchive: (Asset) -> Unit,
    onAssignBucket: (Asset) -> Unit,
    onEditAsset: (Asset) -> Unit,
    dragIndex: Int = 0,
    onDragStart: (Int) -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {}
) {
    val isAsset = categoryMetrics.category.isAsset
    val displayColor = if (isAsset) Color(0xFF10B981) else Color(0xFFEF4444)
    val valuesHidden by viewModel.valuesHidden.collectAsStateWithLifecycle()

    val currentDragIndex by rememberUpdatedState(dragIndex)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
            .testTag("category_card_${categoryMetrics.category.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Summary row of the class
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .pointerInput(categoryMetrics.category.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { currentOnDragStart(currentDragIndex) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentOnDrag(dragAmount.y)
                                },
                                onDragEnd = { currentOnDragEnd() },
                                onDragCancel = { currentOnDragCancel() }
                            )
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Long press and drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(14.dp)
                    )

                    val icon = getAssetOrLiabilityIcon(categoryMetrics.category.name, categoryMetrics.category.name, isAsset)
                    Surface(
                        color = displayColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = displayColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = categoryMetrics.category.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isAsset) {
                            val gainLossText = formatPortfolioAmount(
                                amountUsd = categoryMetrics.totalGainLossUsd,
                                baseCurrency = baseCurrency,
                                viewModel = viewModel,
                                showSign = true,
                                valuesHidden = valuesHidden
                            )
                            val isGain = categoryMetrics.totalGainLossUsd >= 0
                            val gainLossColor = if (isGain) Color(0xFF10B981) else Color(0xFFEF4444)
                            Text(
                                text = gainLossText,
                                style = MaterialTheme.typography.labelSmall,
                                color = gainLossColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    val valuationTextColor = if (isAsset) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        if (categoryMetrics.totalValuationUsd > 0) Color(0xFFEF4444) else Color(0xFF10B981)
                    }
                    Text(
                        text = formatPortfolioAmount(categoryMetrics.totalValuationUsd, baseCurrency, viewModel, valuesHidden = valuesHidden),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = valuationTextColor
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isAsset) {
                            val profitVal = categoryMetrics.totalGainLossUsd
                            val percent = categoryMetrics.totalGainLossPercent
                            val isProf = profitVal >= 0
                            Text(
                                text = (if (isProf) "+" else "") + String.format("%.1f", percent) + "%",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isProf) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand asset list",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Expanded single asset listings
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    if (categoryMetrics.assets.isEmpty()) {
                        Text(
                            text = "No assets inside this category. Open float add button to append.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    categoryMetrics.assets.forEach { amt ->
                        SingleAssetMetricRow(
                            amt = amt,
                            baseCurrency = baseCurrency,
                            viewModel = viewModel,
                            onAddTransaction = { onAddTransaction(amt.asset) },
                            onViewHistory = { onViewHistory(amt.asset) },
                            onToggleInclusion = { onToggleInclusion(amt.asset) },
                            onArchive = { onArchive(amt.asset) },
                            onAssignBucket = { onAssignBucket(amt.asset) },
                            onEdit = { onEditAsset(amt.asset) },
                            isHighlighted = highlightedAssetId == amt.asset.id
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BucketMetricCard(
    bucketMetrics: BucketWithMetrics,
    isExpanded: Boolean,
    baseCurrency: String,
    viewModel: NetWorthViewModel,
    highlightedAssetId: Int? = null,
    onToggleExpand: () -> Unit,
    onAddTransaction: (Asset) -> Unit,
    onViewHistory: (Asset) -> Unit,
    onToggleInclusion: (Asset) -> Unit,
    onArchive: (Asset) -> Unit,
    onAssignBucket: (Asset) -> Unit,
    onEditAsset: (Asset) -> Unit
) {
    val displayColor = MaterialTheme.colorScheme.primary
    val bucketName = bucketMetrics.bucket?.name ?: "Unassigned Assets"
    val valuesHidden by viewModel.valuesHidden.collectAsStateWithLifecycle()

    val rate = viewModel.exchangeRates[baseCurrency] ?: 1.0
    val valuationInBase = if (baseCurrency == "USD") {
        bucketMetrics.totalValuationUsd
    } else {
        bucketMetrics.totalValuationUsd / rate
    }
    val targetAmount = bucketMetrics.bucket?.targetAmount ?: 0.0
    val hasBreached = targetAmount > 0.0 && valuationInBase >= targetAmount
    var showSkimAdvicePopup by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
            .testTag("bucket_card_${bucketMetrics.bucket?.id ?: -1}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Summary row of the bucket
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val icon = if (bucketMetrics.bucket == null) Icons.Default.FolderOpen else Icons.Default.Folder
                    Surface(
                        color = displayColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = displayColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = bucketName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (hasBreached) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Target reached",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        val gainLossText = formatPortfolioAmount(
                            amountUsd = bucketMetrics.totalGainLossUsd,
                            baseCurrency = baseCurrency,
                            viewModel = viewModel,
                            showSign = true,
                            valuesHidden = valuesHidden
                        )
                        val isGain = bucketMetrics.totalGainLossUsd >= 0
                        val gainLossColor = if (isGain) Color(0xFF10B981) else Color(0xFFEF4444)
                        Text(
                            text = gainLossText,
                            style = MaterialTheme.typography.labelSmall,
                            color = gainLossColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatPortfolioAmount(bucketMetrics.totalValuationUsd, baseCurrency, viewModel, valuesHidden = valuesHidden),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val profitVal = bucketMetrics.totalGainLossUsd
                        val percent = bucketMetrics.totalGainLossPercent
                        val isProf = profitVal >= 0
                        Text(
                            text = (if (isProf) "+" else "") + String.format("%.1f", percent) + "%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isProf) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand asset list",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Target Progress visualizer or Decumulation level indicator
            val bucket = bucketMetrics.bucket
            if (bucket != null) {
                if (bucket.isDecumulation) {
                    val totalBufferCapacity = bucket.yearlySpendBudget * bucket.bufferYears
                    val percentFilled = if (totalBufferCapacity > 0.0) (valuationInBase / totalBufferCapacity) * 100.0 else 0.0
                    val progressFraction = if (totalBufferCapacity > 0.0) (valuationInBase / totalBufferCapacity).coerceIn(0.0..1.0).toFloat() else 0.0f
                    
                    val warningThreshold = bucket.warningThresholdPercent
                    val indicatorColor = when {
                        percentFilled < warningThreshold -> Color(0xFFEF4444) // Critical Red
                        percentFilled < 50.0 -> Color(0xFFF59E0B) // Warning Yellow/Amber
                        else -> Color(0xFF10B981) // Healthy Green
                    }
                    val statusText = when {
                        percentFilled < warningThreshold -> "🔴 CRITICAL (Below ${String.format(Locale.US, "%.0f", warningThreshold)}%)"
                        percentFilled < 50.0 -> "🟡 WARNING (Below 50%)"
                        else -> "💚 HEALTHY BUFFER"
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = indicatorColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${bucket.bufferYears}-Year Spend Buffer Target: ${formatAssetCurrency(totalBufferCapacity, baseCurrency, valuesHidden = false)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Annual Spend Budget: ${formatAssetCurrency(bucket.yearlySpendBudget, baseCurrency, valuesHidden = false)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }

                        // Badge with filled progress info
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(indicatorColor.copy(alpha = 0.15f))
                                .border(1.dp, indicatorColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", percentFilled)}% funded",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = indicatorColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Smooth dynamic warning level visualizer linear bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .fillMaxHeight()
                                .background(
                                    color = indicatorColor,
                                    shape = RoundedCornerShape(4.dp)
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "0% Buffer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Guardrail: ${String.format(Locale.US, "%.0f", warningThreshold)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = indicatorColor.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${bucket.bufferYears}Y Cap",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    // Accumulation mode
                    if (targetAmount > 0.0) {
                        val percentFilled = (valuationInBase / targetAmount) * 100.0
                        val progressFraction = (valuationInBase / targetAmount).coerceIn(0.0..1.0).toFloat()

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Target Goal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = formatAssetCurrency(targetAmount, baseCurrency, valuesHidden = false),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Dynamic progress-filled status badge label!
                            val progressColor = if (hasBreached) Color(0xFF10B981) else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            val inactiveBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            val badgeBorderColor = if (hasBreached) Color(0xFF10B981) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            val textLabelAndProgress = if (hasBreached) {
                                "🎯 Reached! ${String.format(Locale.US, "%.1f", percentFilled)}%"
                            } else {
                                "${String.format(Locale.US, "%.1f", percentFilled)}% filled"
                            }

                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .widthIn(min = 90.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(inactiveBgColor)
                                    .drawBehind {
                                        if (!hasBreached) {
                                            drawRect(
                                                color = progressColor,
                                                size = androidx.compose.ui.geometry.Size(size.width * progressFraction, size.height)
                                            )
                                        } else {
                                            drawRect(
                                                color = Color(0xFF10B981),
                                                size = size
                                            )
                                        }
                                    }
                                    .border(1.dp, badgeBorderColor, RoundedCornerShape(13.dp))
                                    .padding(horizontal = 10.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = textLabelAndProgress,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = if (hasBreached) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Row with progress bar / level bar and potential 'i' info icon on the right end
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progressFraction)
                                        .fillMaxHeight()
                                        .background(
                                            color = if (hasBreached) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                )
                            }

                            if (bucket.lastYearPerformancePercent > 0.0 && bucket.lastYearPerformancePercent >= bucket.targetGainPercent) {
                                Box {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Safe to skim info button",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onPress = {
                                                        showSkimAdvicePopup = true
                                                        try {
                                                            awaitRelease()
                                                        } finally {
                                                            showSkimAdvicePopup = false
                                                        }
                                                    }
                                                )
                                            }
                                    )

                                    if (showSkimAdvicePopup) {
                                        Popup(
                                            alignment = Alignment.TopEnd,
                                            onDismissRequest = { showSkimAdvicePopup = false }
                                        ) {
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .width(280.dp)
                                                    .padding(top = 24.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.Top,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "💡",
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        text = "Safe to Skim! Prior Year performance of ${String.format(Locale.US, "%.1f", bucket.lastYearPerformancePercent)}% exceeds the ${String.format(Locale.US, "%.1f", bucket.targetGainPercent)}% target threshold. You can transfer excess growth to cash spending decumulation buckets.",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = FontWeight.SemiBold
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

            // Expanded single asset listings
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                    if (bucketMetrics.assets.isEmpty()) {
                        Text(
                            text = if (bucketMetrics.bucket == null) "No unassigned assets or liabilities present." else "No assets or liabilities inside this bucket. Tap the 3-dots on an item to assign it here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    bucketMetrics.assets.forEach { amt ->
                        SingleAssetMetricRow(
                            amt = amt,
                            baseCurrency = baseCurrency,
                            viewModel = viewModel,
                            onAddTransaction = { onAddTransaction(amt.asset) },
                            onViewHistory = { onViewHistory(amt.asset) },
                            onToggleInclusion = { onToggleInclusion(amt.asset) },
                            onArchive = { onArchive(amt.asset) },
                            onAssignBucket = { onAssignBucket(amt.asset) },
                            onEdit = { onEditAsset(amt.asset) },
                            isHighlighted = highlightedAssetId == amt.asset.id
                        )
                    }
                }
            }
        }
    }
}

fun getAssetOrLiabilityIcon(assetName: String, categoryName: String?, isAsset: Boolean): ImageVector {
    val nameLower = assetName.lowercase()
    val catLower = categoryName?.lowercase() ?: ""
    
    // Check key root-words in both assetName & categoryName
    return if (isAsset) {
        when {
            // Real Estate & Property
            nameLower.contains("home") || nameLower.contains("house") || nameLower.contains("property") || nameLower.contains("land") || nameLower.contains("real estate") || nameLower.contains("estate") ||
            catLower.contains("home") || catLower.contains("house") || catLower.contains("property") || catLower.contains("land") || catLower.contains("real estate") || catLower.contains("estate") -> {
                Icons.Default.Home
            }
            // Bank accounts, Cash, Checking, Savings
            nameLower.contains("bank") || nameLower.contains("checking") || nameLower.contains("savings") || nameLower.contains("deposit") || nameLower.contains("cash") ||
            catLower.contains("bank") || catLower.contains("checking") || catLower.contains("savings") || catLower.contains("deposit") || catLower.contains("cash") -> {
                Icons.Default.AccountBalance
            }
            // Crypto assets / Precious metals
            nameLower.contains("crypto") || nameLower.contains("bitcoin") || nameLower.contains("btc") || nameLower.contains("eth") || nameLower.contains("ethereum") || nameLower.contains("gold") || nameLower.contains("silver") || nameLower.contains("metal") ||
            catLower.contains("crypto") || catLower.contains("bitcoin") || catLower.contains("btc") || catLower.contains("eth") || catLower.contains("ethereum") || catLower.contains("gold") || catLower.contains("silver") || catLower.contains("metal") -> {
                Icons.Default.MonetizationOn
            }
            // Stocks, Equities, Mutual Funds, ETFs, Investments
            nameLower.contains("stock") || nameLower.contains("equity") || nameLower.contains("fund") || nameLower.contains("mutual") || nameLower.contains("etf") || nameLower.contains("index") || nameLower.contains("invest") || nameLower.contains("portfolio") || nameLower.contains("share") ||
            catLower.contains("stock") || catLower.contains("equity") || catLower.contains("fund") || catLower.contains("mutual") || catLower.contains("etf") || catLower.contains("index") || catLower.contains("invest") || catLower.contains("portfolio") || catLower.contains("share") -> {
                Icons.Default.TrendingUp
            }
            // Bonds, Treasuries
            nameLower.contains("bond") || nameLower.contains("treasury") || nameLower.contains("fixed") || nameLower.contains("t-bill") ||
            catLower.contains("bond") || catLower.contains("treasury") || catLower.contains("fixed") || catLower.contains("t-bill") -> {
                Icons.Default.Receipt
            }
            // Vehicle / Automobile
            nameLower.contains("car") || nameLower.contains("vehicle") || nameLower.contains("auto") || nameLower.contains("motorcycle") || nameLower.contains("truck") ||
            catLower.contains("car") || catLower.contains("vehicle") || catLower.contains("auto") || catLower.contains("motorcycle") || catLower.contains("truck") -> {
                Icons.Default.DirectionsCar
            }
            // Business equity
            nameLower.contains("business") || nameLower.contains("startup") || nameLower.contains("company") ||
            catLower.contains("business") || catLower.contains("startup") || catLower.contains("company") -> {
                Icons.Default.Business
            }
            // Default Asset Icon
            else -> Icons.Default.AccountBalanceWallet
        }
    } else {
        when {
            // Mortgage (Debt)
            nameLower.contains("mortgage") || nameLower.contains("home") || nameLower.contains("house") || nameLower.contains("property") ||
            catLower.contains("mortgage") || catLower.contains("home") || catLower.contains("house") || catLower.contains("property") -> {
                Icons.Default.Home
            }
            // Credit Cards, Visa, Mastercard, AMEX
            nameLower.contains("card") || nameLower.contains("credit") || nameLower.contains("visa") || nameLower.contains("mastercard") || nameLower.contains("amex") ||
            catLower.contains("card") || catLower.contains("credit") || catLower.contains("visa") || catLower.contains("mastercard") || catLower.contains("amex") -> {
                Icons.Default.CreditCard
            }
            // Student Loans
            nameLower.contains("student") || nameLower.contains("education") || nameLower.contains("school") ||
            catLower.contains("student") || catLower.contains("education") || catLower.contains("school") -> {
                Icons.Default.School
            }
            // Auto / Car loans
            nameLower.contains("car") || nameLower.contains("vehicle") || nameLower.contains("auto") ||
            catLower.contains("car") || catLower.contains("vehicle") || catLower.contains("auto") -> {
                Icons.Default.DirectionsCar
            }
            // Insurance
            nameLower.contains("insurance") || nameLower.contains("premium") ||
            catLower.contains("insurance") || catLower.contains("premium") -> {
                Icons.Default.Shield
            }
            // Default/Generic Liabilities, Debts, Loans
            nameLower.contains("loan") || nameLower.contains("debt") || nameLower.contains("owe") || nameLower.contains("borrow") || nameLower.contains("pay") ||
            catLower.contains("loan") || catLower.contains("debt") || catLower.contains("owe") || catLower.contains("borrow") || catLower.contains("pay") -> {
                Icons.Default.Payments
            }
            // Default Liability Icon
            else -> Icons.Default.Payments
        }
    }
}

@Composable
fun SingleAssetMetricRow(
    amt: AssetWithMetrics,
    baseCurrency: String,
    viewModel: NetWorthViewModel,
    onAddTransaction: () -> Unit,
    onViewHistory: () -> Unit,
    onToggleInclusion: () -> Unit,
    onArchive: () -> Unit,
    onAssignBucket: () -> Unit,
    onEdit: () -> Unit,
    isHighlighted: Boolean = false
) {
    val gainsColor = if (amt.gainLossUsd >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
    val isIncluded = amt.asset.includeInPortfolio
    val valuesHidden by viewModel.valuesHidden.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var showDetailsDialog by remember { mutableStateOf(false) }

    val baseBgColor = if (isSystemInDarkTheme()) Color(0xFF1F2937) else Color(0xFFF3F4F6)
    val defaultBgColor = if (isIncluded) baseBgColor else baseBgColor.copy(alpha = 0.5f)
    val highlightBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    val animatedBgColor by animateColorAsState(
        targetValue = if (isHighlighted) highlightBgColor else defaultBgColor,
        animationSpec = tween(durationMillis = 600),
        label = "asset_highlight_bg"
    )

    Surface(
        color = animatedBgColor,
        shape = RoundedCornerShape(12.dp),
        border = if (isHighlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewHistory() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            var showMenu by remember { mutableStateOf(false) }
 
            Box(modifier = Modifier.padding(end = 4.dp)) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("asset_options_button_${amt.asset.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Asset options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
 
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Add Transaction",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = {
                            onAddTransaction()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Capital & Valuation Details",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = {
                            showDetailsDialog = true
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.testTag("asset_capital_details_option_${amt.asset.id}")
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Rename / Edit",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = {
                            onEdit()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.testTag("rename_edit_option_${amt.asset.id}")
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isIncluded) "Exclude from Net Worth" else "Include in Net Worth",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = {
                            onToggleInclusion()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isIncluded) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isIncluded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Assign to Bucket...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = {
                            onAssignBucket()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Archive Asset",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = {
                            onArchive()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

             Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (isIncluded) 1.0f else 0.6f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = amt.asset.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (!isIncluded) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Text(
                                text = "Excluded",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (amt.isAssetType) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        val plText = formatAssetCurrency(amt.gainLossNative, amt.asset.currency, showSign = true, valuesHidden = valuesHidden)
                        Surface(
                            color = gainsColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = plText,
                                style = MaterialTheme.typography.labelSmall,
                                color = gainsColor,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (amt.isAssetType) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.alpha(if (isIncluded) 1.0f else 0.7f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val nativeDisplayStr = formatAssetCurrency(amt.currentValuationNative, amt.asset.currency, valuesHidden = valuesHidden)
                        Text(
                            text = nativeDisplayStr,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                        Surface(
                            color = gainsColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = (if (amt.gainLossNative >= 0) "+" else "") + String.format("%.2f", amt.gainLossPercent) + "%",
                                style = MaterialTheme.typography.labelSmall,
                                color = gainsColor,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.alpha(if (isIncluded) 1.0f else 0.7f)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        val nativeDisplayStr = formatAssetCurrency(amt.currentValuationNative, amt.asset.currency, valuesHidden = valuesHidden)
                        val valuationTextColor = if (amt.currentValuationNative > 0) Color(0xFFEF4444) else Color(0xFF10B981)
                        Text(
                            text = nativeDisplayStr,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = valuationTextColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
            }
        }

    if (showDetailsDialog) {
        val transactions by viewModel.transactions.collectAsStateWithLifecycle()
        val assetTxs = remember(transactions, amt.asset.id) {
            transactions.filter { it.assetId == amt.asset.id || it.destinationAssetId == amt.asset.id }
        }

        val totalDepositsNative = remember(assetTxs, amt.asset.id) {
            assetTxs.sumOf { tx ->
                if (tx.assetId == amt.asset.id && tx.type == "DEPOSIT") {
                    tx.amount
                } else if (tx.destinationAssetId == amt.asset.id && tx.type == "TRANSFER") {
                    tx.amount * (tx.exchangeRate ?: 1.0)
                } else {
                    0.0
                }
            }
        }

        val totalWithdrawalsNative = remember(assetTxs, amt.asset.id) {
            assetTxs.filter { it.assetId == amt.asset.id && (it.type == "WITHDRAWAL" || it.type == "TRANSFER") }
                .sumOf { it.amount }
        }

        val oneYearAgo = System.currentTimeMillis() - 365 * 24 * 60 * 60 * 1000L
        val displayPoints = remember(assetTxs, amt.isAssetType) {
            val points = mutableListOf<ValuationPoint>()
            var runningValuation = 0.0
            val sortedHistoryTxs = assetTxs.sortedWith(compareBy<com.example.data.model.Transaction> { it.timestamp }.thenBy { it.id })

            for (tx in sortedHistoryTxs) {
                val previousValuation = runningValuation
                val change: Double
                if (tx.assetId == amt.asset.id) {
                    if (!amt.isAssetType) { // isLiability
                        when (tx.type) {
                            "UPDATE" -> {
                                runningValuation = tx.amount
                                change = previousValuation - runningValuation
                            }
                            "DEPOSIT" -> {
                                runningValuation -= tx.amount
                                change = tx.amount
                            }
                            "WITHDRAWAL" -> {
                                runningValuation += tx.amount
                                change = -tx.amount
                            }
                            "INCOME" -> {
                                runningValuation -= tx.amount
                                change = tx.amount
                            }
                            "TRANSFER" -> {
                                runningValuation += tx.amount
                                change = -tx.amount
                            }
                            else -> {
                                change = 0.0
                            }
                        }
                    } else { // isAsset
                        when (tx.type) {
                            "UPDATE" -> {
                                runningValuation = tx.amount
                                change = runningValuation - previousValuation
                            }
                            "DEPOSIT" -> {
                                runningValuation += tx.amount
                                change = tx.amount
                            }
                            "WITHDRAWAL" -> {
                                runningValuation -= tx.amount
                                change = -tx.amount
                            }
                            "INCOME" -> {
                                runningValuation += tx.amount
                                change = tx.amount
                            }
                            "TRANSFER" -> {
                                runningValuation -= tx.amount
                                change = -tx.amount
                            }
                            else -> {
                                change = 0.0
                            }
                        }
                    }
                } else if (tx.destinationAssetId == amt.asset.id) {
                    val incomingAmount = tx.amount * (tx.exchangeRate ?: 1.0)
                    if (!amt.isAssetType) {
                        runningValuation -= incomingAmount
                        change = incomingAmount
                    } else {
                        runningValuation += incomingAmount
                        change = incomingAmount
                    }
                } else {
                    change = 0.0
                }

                val dateStr = SimpleDateFormat("dd-MMM-yy", Locale.US).format(Date(tx.timestamp))
                points.add(
                    ValuationPoint(
                        transaction = tx,
                        dateStr = dateStr,
                        diff = change,
                        amount = runningValuation
                    )
                )
            }
            points
        }

        val sortedPointsForTrend = remember(displayPoints) {
            val filtered = displayPoints.filter { it.transaction.timestamp >= oneYearAgo }
            if (filtered.isNotEmpty()) {
                filtered
            } else if (displayPoints.isNotEmpty()) {
                displayPoints
            } else {
                emptyList()
            }
        }

        Dialog(onDismissRequest = { showDetailsDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = amt.asset.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Capital & Valuation Details",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(onClick = { showDetailsDialog = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close details",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

                    // Valuation and Net Deposit (Two Lines)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column {
                            Text(
                                text = "Current Valuation",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatAssetCurrency(amt.currentValuationNative, amt.asset.currency, valuesHidden = valuesHidden),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = if (amt.isAssetType) MaterialTheme.colorScheme.onSurface else Color(0xFFEF4444)
                            )
                        }
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

                        Column {
                            Text(
                                text = "Net Deposit / Capital",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatAssetCurrency(amt.netDepositsNative, amt.asset.currency, valuesHidden = valuesHidden),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Total Deposits and Total Withdrawals Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Total Deposits
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Total Deposits",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formatAssetCurrency(totalDepositsNative, amt.asset.currency, valuesHidden = valuesHidden),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Total Withdrawals
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingDown,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Total Withdrawals",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formatAssetCurrency(totalWithdrawalsNative, amt.asset.currency, valuesHidden = valuesHidden),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Historical Trend Chart for 1 yr
                    Text(
                        text = "1-Year Valuation Trend",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (sortedPointsForTrend.isNotEmpty()) {
                        val amounts = sortedPointsForTrend.map { Point -> Point.amount }
                        val maxAmt = amounts.maxOrNull() ?: 0.0
                        val minAmt = amounts.minOrNull() ?: 0.0
                        val amtRange = maxAmt - minAmt
                        val pad = if (amtRange == 0.0) {
                            if (maxAmt == 0.0) 100.0 else maxAmt * 0.1
                        } else {
                            amtRange * 0.15
                        }
                        val minGraph = minAmt - pad
                        val maxGraph = maxAmt + pad
                        val graphRange = maxGraph - minGraph

                        val strokeColor = MaterialTheme.colorScheme.primary
                        val fillGradientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            ) {
                                val w = size.width
                                val h = size.height

                                val path = Path()
                                val fillPath = Path()

                                val stepX = if (sortedPointsForTrend.size > 1) {
                                    w / (sortedPointsForTrend.size - 1)
                                } else {
                                    w
                                }

                                sortedPointsForTrend.forEachIndexed { index, point ->
                                    val x = index * stepX
                                    val ratio = if (graphRange != 0.0) (point.amount - minGraph) / graphRange else 0.5
                                    val y = h - (ratio * h).toFloat()

                                    if (index == 0) {
                                        path.moveTo(x, y)
                                        fillPath.moveTo(x, h)
                                        fillPath.lineTo(x, y)
                                    } else {
                                        path.lineTo(x, y)
                                        fillPath.lineTo(x, y)
                                    }

                                    if (index == sortedPointsForTrend.size - 1) {
                                        fillPath.lineTo(x, h)
                                        fillPath.close()
                                    }
                                }

                                // Draw fill gradient area
                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(fillGradientColor, Color.Transparent),
                                        startY = 0f,
                                        endY = h
                                    )
                                )

                                // Draw trend line
                                drawPath(
                                    path = path,
                                    color = strokeColor,
                                    style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
                                )

                                // Draw circles at start and end
                                if (sortedPointsForTrend.isNotEmpty()) {
                                    val firstRatio = if (graphRange != 0.0) (sortedPointsForTrend.first().amount - minGraph) / graphRange else 0.5
                                    val firstY = h - (firstRatio * h).toFloat()
                                    drawCircle(
                                        color = strokeColor,
                                        radius = 4.dp.toPx(),
                                        center = Offset(0f, firstY)
                                    )

                                    val lastRatio = if (graphRange != 0.0) (sortedPointsForTrend.last().amount - minGraph) / graphRange else 0.5
                                    val lastY = h - (lastRatio * h).toFloat()
                                    drawCircle(
                                        color = strokeColor,
                                        radius = 4.dp.toPx(),
                                        center = Offset(w, lastY)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val sdf = SimpleDateFormat("MMM yyyy", Locale.getDefault())
                                Text(
                                    text = sdf.format(Date(sortedPointsForTrend.first().transaction.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = sdf.format(Date(sortedPointsForTrend.last().transaction.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timeline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "No historical valuation data available.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Close Button
                    Button(
                        onClick = { showDetailsDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close Details")
                    }
                }
            }
        }
    }
}

@Composable
fun NetWorthHistoryGraph(
    snapshots: List<ChartPoint>,
    baseCurrency: String,
    viewModel: NetWorthViewModel,
    modifier: Modifier = Modifier,
    showBarChart: Boolean = false
) {
    val valuesHidden by viewModel.valuesHidden.collectAsStateWithLifecycle()
    val selectedTimeline by viewModel.selectedTimeline.collectAsStateWithLifecycle()

    if (snapshots.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Establish Your Wealth Journey!",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Log transactions, buy assets, or perform valuation updates to draw trendlines.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    // Define and cache colors prior to non-composable Canvas scopes
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val backgroundColor = MaterialTheme.colorScheme.background

    // Gesture tracking state
    var activeIndex by remember(snapshots, showBarChart) { mutableStateOf<Int?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        val density = LocalDensity.current
        val leftPadding = with(density) { 55.dp.toPx() }
        val bottomPadding = with(density) { 25.dp.toPx() }
        val topPadding = with(density) { 10.dp.toPx() }
        val rightPadding = with(density) { 10.dp.toPx() }

        val chartWidth = widthPx - leftPadding - rightPadding
        val chartHeight = heightPx - bottomPadding - topPadding

        val updateTouch: (Float) -> Unit = { x ->
            if (snapshots.isNotEmpty()) {
                if (showBarChart) {
                    val barCount = snapshots.size - 1
                    if (barCount > 0) {
                        val j = ((x - leftPadding) / (chartWidth / barCount) - 0.5f).roundToInt().coerceIn(0, barCount - 1)
                        activeIndex = j
                    }
                } else {
                    val minTime = snapshots.minOf { it.timestamp }.toFloat()
                    val maxTime = snapshots.maxOf { it.timestamp }.toFloat()
                    val timeRange = maxTime - minTime
                    val listWithX = snapshots.mapIndexed { idx, snap ->
                        val xRatio = if (timeRange == 0f) 0.5f else (snap.timestamp - minTime).toFloat() / timeRange
                        val computedX = leftPadding + xRatio * chartWidth
                        Pair(idx, computedX)
                    }
                    val closestIdx = listWithX.minByOrNull { pair -> abs(pair.second - x) }?.first
                    activeIndex = closestIdx
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(snapshots, showBarChart) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var currentPosition = down.position

                            // Wait 0.2 seconds (200ms) for hold
                            val longPressActive = withTimeoutOrNull(200) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val anyActive = event.changes.any { it.pressed }
                                    if (!anyActive) {
                                        break
                                    }
                                    event.changes.firstOrNull { it.pressed }?.let {
                                        currentPosition = it.position
                                    }
                                }
                                false
                            } ?: true

                            if (longPressActive) {
                                updateTouch(currentPosition.x)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val anyActive = event.changes.any { it.pressed }
                                    if (!anyActive) {
                                        break
                                    }
                                    event.changes.firstOrNull { it.pressed }?.let {
                                        updateTouch(it.position.x)
                                    }
                                }
                            }
                            activeIndex = null
                        }
                    }
                }
        ) {
            // Interactive HUD (Heads-Up Display)
            if (activeIndex != null) {
                val idx = activeIndex!!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f), shape = RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (showBarChart) {
                            val gain = snapshots[idx + 1].intervalGainUsd
                            val isPositive = gain >= 0
                            val formattedGain = (if (isPositive) "+" else "") + formatAbbreviated(gain, baseCurrency, viewModel, valuesHidden = valuesHidden)
                            val dStart = sdf.format(Date(snapshots[idx].timestamp))
                            val dEnd = sdf.format(Date(snapshots[idx + 1].timestamp))
                            
                            Text(
                                text = "Gain/Loss: $formattedGain",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isPositive) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                            Text(
                                text = "Period: $dStart — $dEnd",
                                style = MaterialTheme.typography.labelSmall,
                                color = labelColor
                            )
                        } else {
                            val snap = snapshots[idx]
                            val formattedWorth = formatAbbreviated(snap.netWorthUsd, baseCurrency, viewModel, valuesHidden = valuesHidden)
                            val formattedDate = sdf.format(Date(snap.timestamp))
                            
                            Text(
                                text = "Net Worth: $formattedWorth",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Valuation on: $formattedDate",
                                style = MaterialTheme.typography.labelSmall,
                                color = labelColor
                            )
                        }
                    }
                    IconButton(
                        onClick = { activeIndex = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear Selection",
                            tint = labelColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (showBarChart) "Gain/loss History" else "Networth Trend",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Canvas Area takes up remaining height
            Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val width = size.width
                val height = size.height

                val leftPaddingPx = leftPadding
                val bottomPaddingPx = bottomPadding
                val topPaddingPx = topPadding
                val rightPaddingPx = rightPadding

                val cWidth = width - leftPaddingPx - rightPaddingPx
                val cHeight = height - bottomPaddingPx - topPaddingPx

                if (showBarChart) {
                    val gains = (1 until snapshots.size).map { i -> snapshots[i].intervalGainUsd }
                    val maxVal = maxOf(0.0, gains.maxOrNull() ?: 0.0)
                    val minVal = minOf(0.0, gains.minOrNull() ?: 0.0)
                    val valRange = maxVal - minVal
                    val padding = if (valRange == 0.0) 1000.0 else valRange * 0.15
                    val graphMin = minVal - padding
                    val graphMax = maxVal + padding
                    val graphRange = graphMax - graphMin

                    // 1. Horizontal grid lines
                    val gridLines = 3
                    for (i in 0..gridLines) {
                        val ratio = i.toFloat() / gridLines
                        val y = topPaddingPx + ratio * cHeight
                        drawLine(
                            color = labelColor.copy(alpha = 0.1f),
                            start = Offset(leftPaddingPx, y),
                            end = Offset(width - rightPaddingPx, y),
                            strokeWidth = 1.dp.toPx()
                        )

                        val gridValue = graphMax - ratio * graphRange
                        val labelStr = formatAbbreviated(gridValue, baseCurrency, viewModel, valuesHidden = valuesHidden)
                        drawContext.canvas.nativeCanvas.apply {
                            val textPaint = android.graphics.Paint().apply {
                                color = labelColor.toArgb()
                                textSize = 9.sp.toPx()
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                            }
                            drawText(
                                labelStr,
                                10.dp.toPx(),
                                y + 4.dp.toPx(),
                                textPaint
                            )
                        }
                    }

                    // 2. Compute 0 baseline
                    val zeroY = if (0.0 in graphMin..graphMax) {
                        topPaddingPx + cHeight - (((0.0 - graphMin) / graphRange) * cHeight).toFloat()
                    } else if (graphMin >= 0.0) {
                        topPaddingPx + cHeight
                    } else {
                        topPaddingPx
                    }

                    // Draw baseline line
                    drawLine(
                        color = labelColor.copy(alpha = 0.3f),
                        start = Offset(leftPaddingPx, zeroY),
                        end = Offset(width - rightPaddingPx, zeroY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )

                    // 3. Render Bars
                    val barCount = gains.size
                    val barWidthFraction = 0.6f
                    val spacePerBar = cWidth / barCount

                    for (j in 0 until barCount) {
                        val barCenterX = leftPaddingPx + (j + 0.5f) * spacePerBar
                        val barWidth = spacePerBar * barWidthFraction
                        val barLeft = barCenterX - barWidth / 2f

                        val gain = gains[j]
                        val gainY = topPaddingPx + cHeight - (((gain - graphMin) / graphRange) * cHeight).toFloat()

                        val top = if (gain >= 0.0) gainY else zeroY
                        val bottom = if (gain >= 0.0) zeroY else gainY
                        val barHeight = maxOf(2f, bottom - top)

                        // Highlight selected bar
                        val isSelected = activeIndex == j
                        val barAlpha = if (activeIndex == null || isSelected) 1f else 0.4f
                        val barColor = if (gain >= 0.0) Color(0xFF10B981).copy(alpha = barAlpha) else Color(0xFFEF4444).copy(alpha = barAlpha)

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(barLeft, top),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        // Highlight selected bar with a thin border/accent
                        if (isSelected) {
                            drawRoundRect(
                                color = primaryColor,
                                topLeft = Offset(barLeft - 1.dp.toPx(), top - 1.dp.toPx()),
                                size = Size(barWidth + 2.dp.toPx(), barHeight + 2.dp.toPx()),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                style = Stroke(width = 1.5.dp.toPx())
                            )

                            // Vertical indicator line
                            drawLine(
                                color = primaryColor.copy(alpha = 0.5f),
                                start = Offset(barCenterX, topPaddingPx),
                                end = Offset(barCenterX, topPaddingPx + cHeight),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                            )
                        }

                        // Date label below current bar
                        val showLabel = when (selectedTimeline) {
                            "YTD" -> true // Show for every month bar in YTD (usually <= 12)
                            "1YR" -> {
                                // For 1YR (12 bars), if space is tight, skip odd numbers
                                val approxBarWidthPx = spacePerBar
                                approxBarWidthPx > 35.dp.toPx() || (j % 2 == 0)
                            }
                            "5YR" -> true // Show for every year in 5YR
                            "ALL" -> {
                                val approxBarWidthPx = spacePerBar
                                approxBarWidthPx > 45.dp.toPx() || (j % 2 == 0) || j == barCount - 1
                            }
                            else -> (j == 0 || j == barCount - 1 || (barCount > 4 && j == barCount / 2))
                        }

                        if (showLabel) {
                            val labelText = when (selectedTimeline) {
                                "YTD" -> {
                                    val sdfMonth = SimpleDateFormat("MMM", Locale.getDefault())
                                    sdfMonth.format(Date(snapshots[j + 1].timestamp))
                                }
                                "1YR" -> {
                                    val sdfMonthYear = SimpleDateFormat("MMM ''yy", Locale.getDefault())
                                    sdfMonthYear.format(Date(snapshots[j + 1].timestamp))
                                }
                                "5YR", "ALL" -> {
                                    val sdfYear = SimpleDateFormat("yyyy", Locale.getDefault())
                                    sdfYear.format(Date(snapshots[j + 1].timestamp))
                                }
                                else -> {
                                    val sdfShort = SimpleDateFormat("MMM d", Locale.getDefault())
                                    sdfShort.format(Date(snapshots[j + 1].timestamp))
                                }
                            }

                            drawContext.canvas.nativeCanvas.apply {
                                val textPaint = android.graphics.Paint().apply {
                                    color = labelColor.toArgb()
                                    textSize = 8.5.sp.toPx()
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                                }
                                drawText(
                                    labelText,
                                    barCenterX,
                                    height - 4.dp.toPx(),
                                    textPaint
                                )
                            }
                        }
                    }
                } else {
                    val chartColor = primaryColor
                    val bgColor = backgroundColor

                    // Compute bounds
                    val minTime = snapshots.minOf { it.timestamp }.toDouble()
                    val maxTime = snapshots.maxOf { it.timestamp }.toDouble()
                    val timeRange = maxTime - minTime

                    val minAmount = snapshots.minOf { it.netWorthUsd }
                    val maxAmount = snapshots.maxOf { it.netWorthUsd }
                    val padding = if (maxAmount == minAmount) 1000.0 else (maxAmount - minAmount) * 0.15
                    val graphMin = minAmount - padding
                    val graphMax = maxAmount + padding
                    val graphRange = graphMax - graphMin

                    // 1. Horizontal grid lines
                    val gridLines = 3
                    for (i in 0..gridLines) {
                        val ratio = i.toFloat() / gridLines
                        val y = topPaddingPx + ratio * cHeight
                        drawLine(
                            color = labelColor.copy(alpha = 0.1f),
                            start = Offset(leftPaddingPx, y),
                            end = Offset(width - rightPaddingPx, y),
                            strokeWidth = 1.dp.toPx()
                        )

                        val gridValue = graphMax - ratio * graphRange
                        val labelStr = formatAbbreviated(gridValue, baseCurrency, viewModel, valuesHidden = valuesHidden)
                        drawContext.canvas.nativeCanvas.apply {
                            val textPaint = android.graphics.Paint().apply {
                                color = labelColor.toArgb()
                                textSize = 9.sp.toPx()
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                            }
                            drawText(
                                labelStr,
                                10.dp.toPx(),
                                y + 4.dp.toPx(),
                                textPaint
                            )
                        }
                    }

                    // 2. Generate spline points
                    val points = snapshots.map { snap ->
                        val xRatio = if (timeRange == 0.0) 0.5 else (snap.timestamp - minTime) / timeRange
                        val yRatio = if (graphRange == 0.0) 0.5 else (snap.netWorthUsd - graphMin) / graphRange

                        val x = leftPaddingPx + (xRatio * cWidth).toFloat()
                        val y = topPaddingPx + (cHeight - (yRatio * cHeight)).toFloat()
                        Offset(x, y)
                    }

                    val graphPath = Path().apply {
                        if (points.isNotEmpty()) {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                val pPrev = points[i - 1]
                                val pCurr = points[i]
                                val controlX = (pPrev.x + pCurr.x) / 2
                                cubicTo(controlX, pPrev.y, controlX, pCurr.y, pCurr.x, pCurr.y)
                            }
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(graphPath)
                        if (points.isNotEmpty()) {
                            lineTo(points.last().x, topPaddingPx + cHeight)
                            lineTo(points.first().x, topPaddingPx + cHeight)
                            close()
                        }
                    }

                    // Draw linear layout gradients
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                chartColor.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    )

                    drawPath(
                        path = graphPath,
                        color = chartColor,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            pathEffect = PathEffect.cornerPathEffect(10f)
                        )
                    )

                    // Dots and date labels
                    for (i in points.indices) {
                        val point = points[i]
                        val s = snapshots[i]

                        val isPointSelected = activeIndex == i
                        val dotRadius = if (isPointSelected) 7.dp.toPx() else 4.dp.toPx()

                        drawCircle(
                            color = bgColor,
                            radius = dotRadius + 2.dp.toPx(),
                            center = point
                        )
                        drawCircle(
                            color = if (isPointSelected) secondaryColor else chartColor,
                            radius = dotRadius,
                            center = point
                        )

                        if (isPointSelected) {
                            // Vertical selection indicator line
                            drawLine(
                                color = primaryColor.copy(alpha = 0.5f),
                                start = Offset(point.x, topPaddingPx),
                                end = Offset(point.x, topPaddingPx + cHeight),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                            )
                        }

                        // Dynamic date tag on endpoints or midpoint
                        if (i == 0 || i == points.size - 1 || (points.size > 4 && i == points.size / 2)) {
                            val sdfShort = SimpleDateFormat("MMM d", Locale.getDefault())
                            val formattedDate = sdfShort.format(Date(s.timestamp))
                            drawContext.canvas.nativeCanvas.apply {
                                val textPaint = android.graphics.Paint().apply {
                                    color = labelColor.toArgb()
                                    textSize = 9.sp.toPx()
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    typeface = android.graphics.Typeface.DEFAULT
                                }
                                drawText(
                                    formattedDate,
                                    point.x,
                                    height - 4.dp.toPx(),
                                    textPaint
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Dialog: Add/Manage custom Categories (Asset Classes)
@Composable
fun ManageCategoriesDialog(
    categories: List<AssetCategory>,
    isAssetMode: Boolean,
    onDismiss: () -> Unit,
    onSaveCategory: (name: String, isAsset: Boolean) -> Unit,
    onDeleteCategory: (AssetCategory) -> Unit
) {
    var newCatName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("manage_categories_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isAssetMode) "Manage Asset Classes" else "Manage Liability Classes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filteredCategories = categories.filter { it.isAsset == isAssetMode }
                    if (filteredCategories.isEmpty()) {
                        item {
                            Text(
                                "No classes configured. Formulate below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        items(filteredCategories) { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val pillColor = if (cat.isAsset) Color(0xFF10B981) else Color(0xFFEF4444)
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(pillColor)
                                            .align(Alignment.CenterVertically)
                                    )
                                    Text(
                                        text = cat.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                IconButton(
                                    onClick = { onDeleteCategory(cat) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete class",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

                // Create row
                Text(
                    text = if (isAssetMode) "New Asset Class" else "New Liability Class",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = newCatName,
                    onValueChange = { newCatName = it },
                    label = { Text(if (isAssetMode) "e.g. Index Funds, Crypto" else "e.g. Credit Cards, Home Loan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newCatName.isNotBlank()) {
                                onSaveCategory(newCatName, isAssetMode)
                                newCatName = ""
                            }
                        },
                        enabled = newCatName.isNotBlank()
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}// Dialog: Add a new tracking asset or liability
@Composable
fun AddAssetDialog(
    categories: List<AssetCategory>,
    isAssetMode: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, currency: String, categoryId: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("USD") }
    val filteredCategories = categories.filter { it.isAsset == isAssetMode }
    var selectedCategoryId by remember { mutableStateOf<Int?>(filteredCategories.firstOrNull()?.id) }
 
    val commonCurrencies = listOf("USD", "EUR", "GBP", "CAD", "AUD", "SGD", "JPY", "PHP", "SAR", "BTC")
 
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("add_item_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isAssetMode) "Add Tracking Asset" else "Add Liability / Debt",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
 
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isAssetMode) "Asset Name (e.g. BTC Coinbase, S&P 500)" else "Liability Name (e.g. Credit Card, Home Loan)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_input_name")
                )
 
                // Currency selector buttons
                Column {
                    Text("Reporting Currency", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        commonCurrencies.forEach { cur ->
                            val isChosen = currency == cur
                            Surface(
                                onClick = { currency = cur },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isChosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (isChosen) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = cur,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isChosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
 
                // Category class selection flow
                Column {
                    Text(if (isAssetMode) "Select Asset Class" else "Select Liability Class", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 130.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredCategories) { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selectedCategoryId == cat.id) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        else Color.Transparent
                                    )
                                    .clickable { selectedCategoryId = cat.id }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedCategoryId == cat.id,
                                    onClick = { selectedCategoryId = cat.id }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = cat.name + (if (cat.isAsset) " (Asset)" else " (Debt)"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
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
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { 
                            selectedCategoryId?.let { 
                                onSave(name, currency, it) 
                            } 
                        },
                        enabled = name.isNotBlank() && selectedCategoryId != null
                    ) {
                        Text("Add Target")
                    }
                }
            }
        }
    }
}

// Dialog: Rename and edit tracking asset or liability
@Composable
fun EditAssetDialog(
    asset: Asset,
    categories: List<AssetCategory>,
    onDismiss: () -> Unit,
    onSave: (name: String, currency: String, categoryId: Int) -> Unit
) {
    var name by remember(asset) { mutableStateOf(asset.name) }
    var currency by remember(asset) { mutableStateOf(asset.currency) }
    val assetCategory = categories.find { it.id == asset.categoryId }
    val isAssetMode = assetCategory?.isAsset ?: true
    val filteredCategories = categories.filter { it.isAsset == isAssetMode }
    var selectedCategoryId by remember(asset) { mutableStateOf<Int?>(asset.categoryId) }

    val commonCurrencies = listOf("USD", "EUR", "GBP", "CAD", "AUD", "SGD", "JPY", "PHP", "SAR", "BTC")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("edit_item_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isAssetMode) "Rename / Edit Asset" else "Rename / Edit Liability",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_dialog_input_name")
                )

                // Currency display (Read-only since it cannot be changed once created)
                OutlinedTextField(
                    value = asset.currency,
                    onValueChange = {},
                    label = { Text("Reporting Currency (Fixed)") },
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category class selection flow
                Column {
                    Text(if (isAssetMode) "Select Class" else "Select Class", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 130.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredCategories) { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selectedCategoryId == cat.id) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        else Color.Transparent
                                    )
                                    .clickable { selectedCategoryId = cat.id }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedCategoryId == cat.id,
                                    onClick = { selectedCategoryId = cat.id }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = cat.name + (if (cat.isAsset) " (Asset)" else " (Debt)"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
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
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            selectedCategoryId?.let {
                                onSave(name, currency, it)
                            }
                        },
                        enabled = name.isNotBlank() && selectedCategoryId != null
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}


// Dialog: Add Transaction ledger (Deposit, Withdrawal, Update, Income, Transfer)
@Composable
fun AddTransactionDialog(
    asset: Asset,
    otherAssets: List<Asset>,
    isAssetType: Boolean = true,
    transactions: List<Transaction> = emptyList(),
    viewModel: NetWorthViewModel,
    onDismiss: () -> Unit,
    onSave: (type: String, amount: Double, destAssetId: Int?, exRate: Double?, notes: String?, date: Long) -> Unit,
    defaultType: String = "DEPOSIT"
) {
    var type by remember(defaultType) { mutableStateOf(if (isAssetType) defaultType else "UPDATE") }
    var amountStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var transferDestId by remember { mutableStateOf<Int?>(otherAssets.firstOrNull()?.id) }
    var exRateStr by remember { mutableStateOf("1.0") }
    var timestamp by remember { mutableStateOf(System.currentTimeMillis()) }

    val rates by viewModel.exchangeRatesState.collectAsStateWithLifecycle()

    val lastUpdateTimestamp = remember(transactions, asset.id) {
        transactions
            .filter { it.assetId == asset.id && it.type == "UPDATE" }
            .maxOfOrNull { it.timestamp } ?: 0L
    }

    LaunchedEffect(lastUpdateTimestamp) {
        if (lastUpdateTimestamp > 0L && timestamp < lastUpdateTimestamp) {
            timestamp = lastUpdateTimestamp
        }
    }

    val transactionTypes = listOf("DEPOSIT", "WITHDRAWAL", "UPDATE", "INCOME", "TRANSFER")

    val selectedTargetAsset = otherAssets.find { it.id == transferDestId }
    val isMultiCurrencyTransfer = type == "TRANSFER" && selectedTargetAsset != null && selectedTargetAsset.currency != asset.currency

    LaunchedEffect(type, transferDestId, rates) {
        if (type == "TRANSFER" && selectedTargetAsset != null) {
            val rateSource = rates[asset.currency] ?: 1.0
            val rateDest = rates[selectedTargetAsset.currency] ?: 1.0
            val ratio = rateSource / rateDest
            exRateStr = String.format(Locale.US, "%.6f", ratio).trimEnd('0').trimEnd('.')
            if (exRateStr.endsWith(".")) {
                exRateStr = exRateStr.dropLast(1)
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val calendar = java.util.Calendar.getInstance().apply { 
        val adjusted = if (lastUpdateTimestamp > 0L) maxOf(timestamp, lastUpdateTimestamp) else timestamp
        timeInMillis = adjusted 
    }
    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val cal = java.util.Calendar.getInstance()
            cal.set(year, month, dayOfMonth, 12, 0, 0)
            timestamp = cal.timeInMillis
        },
        calendar.get(java.util.Calendar.YEAR),
        calendar.get(java.util.Calendar.MONTH),
        calendar.get(java.util.Calendar.DAY_OF_MONTH)
    ).apply {
        if (lastUpdateTimestamp > 0L) {
            datePicker.minDate = lastUpdateTimestamp
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Add Ledger Point",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Source Target: ${asset.name} (${asset.currency})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                if (!isAssetType) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "For liabilities, updating the liability balance downwards (e.g., from 100 to 80) represents debt payoff and will increase your net worth.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Tx Types Pills scrolls
                if (isAssetType) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        transactionTypes.forEach { t ->
                            val isSel = type == t
                            Surface(
                                onClick = { type = t },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = t,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Action: UPDATE Account Balance",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Amount Textfield
                val amountLabel = if (type == "UPDATE") {
                    if (isAssetType) "New Total Valuation" else "New Liability Balance / Debt Owed"
                } else {
                    "Amount (${asset.currency})"
                }
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text(amountLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_input_amount")
                )

                // Conditional Section: Transfers Destination selection
                if (type == "TRANSFER") {
                    Column {
                        Text("Destination Wallet/Asset", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (otherAssets.isEmpty()) {
                                item {
                                    Text("No third-party asset accounts to transfer. Open primary settings to append assets.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                items(otherAssets) { oa ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (transferDestId == oa.id) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                            .clickable { transferDestId = oa.id }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = transferDestId == oa.id, onClick = { transferDestId = oa.id })
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = "${oa.name} (${oa.currency})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    if (isMultiCurrencyTransfer && selectedTargetAsset != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = exRateStr,
                            onValueChange = { exRateStr = it },
                            label = { Text("Exchange Rate (1 ${asset.currency} = ? ${selectedTargetAsset.currency})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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

                    val rawAmount = amountStr.toDoubleOrNull() ?: 0.0
                    val rate = exRateStr.toDoubleOrNull() ?: 1.0
                    val isValid = if (type == "UPDATE") {
                        rawAmount >= 0.0 && (type != "TRANSFER" || transferDestId != null)
                    } else {
                        rawAmount > 0.0 && (type != "TRANSFER" || transferDestId != null)
                    }

                    Button(
                        onClick = {
                            onSave(
                                type,
                                rawAmount,
                                if (type == "TRANSFER") transferDestId else null,
                                if (isMultiCurrencyTransfer) rate else null,
                                notes.ifBlank { null },
                                timestamp
                            )
                        },
                        enabled = isValid
                    ) {
                        Text("Post")
                    }
                }
            }
        }
    }
}

private data class PeriodPerformanceData(
    val depositsUsd: Double,
    val withdrawalsUsd: Double,
    val incomeUsd: Double,
    val depositCount: Int,
    val withdrawalCount: Int
)

private data class ValuationPoint(
    val transaction: Transaction,
    val dateStr: String,
    val diff: Double,
    val amount: Double
)

internal fun formatAssetCurrency(
    amount: Double,
    currencyCode: String,
    showSign: Boolean = false,
    valuesHidden: Boolean = false,
    isNetWorth: Boolean = false
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
    
    if (valuesHidden) {
        return "$prefix••••$suffix"
    }
    
    val absVal = Math.abs(amount)
    val formattedNum = if (absVal >= 1_000_000.0 && !isNetWorth) {
        String.format(Locale.US, "%,.3fM", absVal / 1_000_000.0)
    } else {
        String.format(Locale.US, "%,.0f", absVal)
    }
    
    val baseString = "$prefix$formattedNum$suffix"
    
    return if (showSign) {
        if (amount > 0) "+$baseString" 
        else if (amount < 0) "-$baseString" 
        else baseString
    } else {
        if (amount < 0) "-$baseString"
        else baseString
    }
}

internal fun formatPortfolioAmount(
    amountUsd: Double,
    baseCurrency: String,
    viewModel: NetWorthViewModel,
    showSign: Boolean = false,
    valuesHidden: Boolean = false,
    isNetWorth: Boolean = false
): String {
    val amountInBase = if (baseCurrency == "USD") {
        amountUsd
    } else {
        val rate = viewModel.exchangeRates[baseCurrency] ?: 1.0
        amountUsd / rate
    }
    return formatAssetCurrency(amountInBase, baseCurrency, showSign = showSign, valuesHidden = valuesHidden, isNetWorth = isNetWorth)
}

// Dialog: View History Ledger, containing editing and deletion options
@Composable
fun AssetHistoryDialog(
    asset: Asset,
    transactions: List<Transaction>,
    otherAssets: List<Asset>,
    viewModel: NetWorthViewModel,
    onDismiss: () -> Unit,
    onDeleteTx: (Transaction) -> Unit,
    onUpdateTx: (id: Int, assetId: Int, type: String, amount: Double, destId: Int?, rate: Double?, notes: String?, time: Long) -> Unit,
    onAddTransaction: () -> Unit
) {
    val valuesHidden by viewModel.valuesHidden.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val isLiability = remember(categories, asset) {
        categories.find { it.id == asset.categoryId }?.isAsset == false
    }
    var editingTx by remember { mutableStateOf<Transaction?>(null) }

    val displayPoints = remember(transactions) {
        val points = mutableListOf<ValuationPoint>()
        var runningValuation = 0.0
        val sortedHistoryTxs = transactions.sortedWith(compareBy<com.example.data.model.Transaction> { it.timestamp }.thenBy { it.id })

        for (tx in sortedHistoryTxs) {
            val previousValuation = runningValuation
            val change: Double
            if (tx.assetId == asset.id) {
                if (isLiability) {
                    when (tx.type) {
                        "UPDATE" -> {
                            runningValuation = tx.amount
                            change = previousValuation - runningValuation // Positive if debt decreased (favorable)
                        }
                        "DEPOSIT" -> {
                            runningValuation -= tx.amount
                            change = tx.amount // Repaid debt (favorable)
                        }
                        "WITHDRAWAL" -> {
                            runningValuation += tx.amount
                            change = -tx.amount // Debt increased (unfavorable)
                        }
                        "INCOME" -> {
                            runningValuation -= tx.amount
                            change = tx.amount // Favorable
                        }
                        "TRANSFER" -> {
                            runningValuation += tx.amount
                            change = -tx.amount // Unfavorable
                        }
                        else -> {
                            change = 0.0
                        }
                    }
                } else {
                    when (tx.type) {
                        "UPDATE" -> {
                            runningValuation = tx.amount
                            change = runningValuation - previousValuation
                        }
                        "DEPOSIT" -> {
                            runningValuation += tx.amount
                            change = tx.amount
                        }
                        "WITHDRAWAL" -> {
                            runningValuation -= tx.amount
                            change = -tx.amount
                        }
                        "INCOME" -> {
                            runningValuation += tx.amount
                            change = tx.amount
                        }
                        "TRANSFER" -> {
                            runningValuation -= tx.amount
                            change = -tx.amount
                        }
                        else -> {
                            change = 0.0
                        }
                    }
                }
            } else if (tx.destinationAssetId == asset.id) {
                val incomingAmount = tx.amount * (tx.exchangeRate ?: 1.0)
                if (isLiability) {
                    runningValuation -= incomingAmount
                    change = incomingAmount // Repay debt (favorable)
                } else {
                    runningValuation += incomingAmount
                    change = incomingAmount // Inflow to asset (favorable)
                }
            } else {
                change = 0.0
            }

            val dateStr = SimpleDateFormat("dd-MMM-yy", Locale.US).format(Date(tx.timestamp))
            points.add(
                ValuationPoint(
                    transaction = tx,
                    dateStr = dateStr,
                    diff = change,
                    amount = runningValuation
                )
            )
        }
        points.reversed()
    }

    val appFontScale by viewModel.appFontScale.collectAsStateWithLifecycle()
    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val customDensity = remember(currentDensity, appFontScale) {
        androidx.compose.ui.unit.Density(
            density = currentDensity.density,
            fontScale = appFontScale
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides customDensity
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                color = Color(0xFF1E293B), // Elegant slate dark background matching user's image
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Row containing "Historical Valuations" and circled add button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Historical Valuations",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${asset.name} (${asset.currency})",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        // Circular outline border with plus sign matching screenshot
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { onAddTransaction() }
                                .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Valuation Log",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                    // Table Header labels row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Date",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1.3f),
                            textAlign = TextAlign.Start,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Diff",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1.1f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Amount",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1.2f),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.weight(0.4f),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        if (displayPoints.isEmpty()) {
                            item {
                                Text(
                                    "No valuations or transactions found for this asset.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            items(displayPoints) { point ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Date & Transaction Type column
                                    Column(
                                        modifier = Modifier.weight(1.3f),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = point.dateStr,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White,
                                            textAlign = TextAlign.Start,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Surface(
                                            color = when(point.transaction.type) {
                                                "DEPOSIT" -> {
                                                    Color(0xFF4ADE80).copy(alpha = 0.15f)
                                                }
                                                "WITHDRAWAL" -> {
                                                    if (isLiability) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                                                }
                                                "INCOME" -> Color(0xFF3B82F6).copy(alpha = 0.15f)
                                                "TRANSFER" -> Color(0xFF8B5CF6).copy(alpha = 0.15f)
                                                else -> {
                                                    val isLoss = point.diff < 0
                                                    if (isLoss) Color(0xFFF97316).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f)
                                                }
                                            },
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = point.transaction.type,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = when(point.transaction.type) {
                                                    "DEPOSIT" -> {
                                                        Color(0xFF4ADE80)
                                                    }
                                                    "WITHDRAWAL" -> {
                                                        if (isLiability) Color(0xFFF87171) else Color(0xFFF87171)
                                                    }
                                                    "INCOME" -> Color(0xFF60A5FA)
                                                    "TRANSFER" -> Color(0xFFA78BFA)
                                                    else -> {
                                                        val isLoss = point.diff < 0
                                                        if (isLoss) Color(0xFFF97316) else Color(0xFF10B981)
                                                    }
                                                },
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Diff column
                                    val isPositive = point.diff > 0
                                    val isNegative = point.diff < 0
                                    val diffColor = if (isPositive) Color(0xFF10B981) else if (isNegative) Color(0xFFEF4444) else Color.White.copy(alpha = 0.6f)
                                    val diffText = if (point.diff == 0.0) "—" else formatAssetCurrency(point.diff, asset.currency, showSign = true, valuesHidden = valuesHidden)

                                    Text(
                                        text = diffText,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = diffColor,
                                        modifier = Modifier.weight(1.1f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Amount column
                                    Text(
                                        text = formatAssetCurrency(point.amount, asset.currency, valuesHidden = valuesHidden),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Normal,
                                        color = Color.White,
                                        modifier = Modifier.weight(1.2f),
                                        textAlign = TextAlign.End,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Actions column (pencil and trash vertically stacked matching screenshot)
                                    Column(
                                        modifier = Modifier.weight(0.4f),
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Event",
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable { editingTx = point.transaction }
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Event",
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable { onDeleteTx(point.transaction) }
                                        )
                                    }
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) {
                            Text("Close", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Secondary sub dialogue for editing
    editingTx?.let { tx ->
        EditTransactionSubDialog(
            tx = tx,
            asset = asset,
            otherAssets = otherAssets.filter { it.id != asset.id },
            transactions = transactions,
            viewModel = viewModel,
            onDismiss = { editingTx = null },
            onSave = { type, amt, destId, rate, notes, updatedTimestamp ->
                onUpdateTx(tx.id, tx.assetId, type, amt, destId, rate, notes, updatedTimestamp)
                editingTx = null
            }
        )
    }
}

// Inline Sub-Dialog focusing strictly on modification updates
@Composable
fun EditTransactionSubDialog(
    tx: Transaction,
    asset: Asset,
    otherAssets: List<Asset>,
    transactions: List<Transaction> = emptyList(),
    viewModel: NetWorthViewModel,
    onDismiss: () -> Unit,
    onSave: (type: String, amount: Double, destId: Int?, rate: Double?, notes: String?, timestamp: Long) -> Unit
) {
    var type by remember { mutableStateOf(tx.type) }
    var amountStr by remember { mutableStateOf(tx.amount.toString()) }
    var timestamp by remember { mutableStateOf(tx.timestamp) }
    var notes by remember { mutableStateOf(tx.notes ?: "") }
    var transferDestId by remember { mutableStateOf<Int?>(tx.destinationAssetId ?: otherAssets.firstOrNull()?.id) }
    var exRateStr by remember { mutableStateOf((tx.exchangeRate ?: 1.0).toString()) }

    val rates by viewModel.exchangeRatesState.collectAsStateWithLifecycle()

    val selectedTargetAsset = otherAssets.find { it.id == transferDestId }
    val isMultiCurrencyTransfer = type == "TRANSFER" && selectedTargetAsset != null && selectedTargetAsset.currency != asset.currency

    LaunchedEffect(type, transferDestId, rates) {
        if (type == "TRANSFER" && selectedTargetAsset != null) {
            // If the selected target is the same as the original destination, use the original rate if present
            if (transferDestId == tx.destinationAssetId && tx.exchangeRate != null) {
                exRateStr = tx.exchangeRate.toString()
            } else {
                val rateSource = rates[asset.currency] ?: 1.0
                val rateDest = rates[selectedTargetAsset.currency] ?: 1.0
                val ratio = rateSource / rateDest
                exRateStr = String.format(Locale.US, "%.6f", ratio).trimEnd('0').trimEnd('.')
                if (exRateStr.endsWith(".")) {
                    exRateStr = exRateStr.dropLast(1)
                }
            }
        }
    }

    val lastUpdateTimestamp = 0L // Do not enforce last update timestamp constraint during edit of existing transactions

    val transactionTypes = listOf("DEPOSIT", "WITHDRAWAL", "UPDATE", "INCOME", "TRANSFER")

    val context = androidx.compose.ui.platform.LocalContext.current
    val calendar = java.util.Calendar.getInstance().apply { 
        val adjusted = if (lastUpdateTimestamp > 0L) maxOf(timestamp, lastUpdateTimestamp) else timestamp
        timeInMillis = adjusted 
    }
    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val cal = java.util.Calendar.getInstance()
            cal.set(year, month, dayOfMonth, 12, 0, 0)
            timestamp = cal.timeInMillis
        },
        calendar.get(java.util.Calendar.YEAR),
        calendar.get(java.util.Calendar.MONTH),
        calendar.get(java.util.Calendar.DAY_OF_MONTH)
    ).apply {
        if (lastUpdateTimestamp > 0L) {
            datePicker.minDate = lastUpdateTimestamp
        }
    }

    val appFontScale by viewModel.appFontScale.collectAsStateWithLifecycle()
    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val customDensity = remember(currentDensity, appFontScale) {
        androidx.compose.ui.unit.Density(
            density = currentDensity.density,
            fontScale = appFontScale
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides customDensity
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Edit Ledger Transaction", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)

                    // Ledger point selection row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    transactionTypes.forEach { t ->
                        val isSel = type == t
                        Surface(
                            onClick = { type = t },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = t,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

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
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Select Date"
                            )
                        }
                    )
                }

                // Amount
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Optional transfer items
                if (type == "TRANSFER") {
                    Text("Select Target Account/Wallet:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (otherAssets.isEmpty()) {
                            item {
                                Text("No other accounts/wallets to transfer. Create more assets to enable transfer.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            items(otherAssets) { oa ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (transferDestId == oa.id) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                        .clickable { transferDestId = oa.id }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = transferDestId == oa.id, onClick = { transferDestId = oa.id })
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "${oa.name} (${oa.currency})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (isMultiCurrencyTransfer && selectedTargetAsset != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = exRateStr,
                            onValueChange = { exRateStr = it },
                            label = { Text("Exchange Rate (1 ${asset.currency} = ? ${selectedTargetAsset.currency})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    val rawAmount = amountStr.toDoubleOrNull() ?: 0.0
                    val rate = exRateStr.toDoubleOrNull() ?: 1.0
                    val isValid = rawAmount > 0.0 && (type != "TRANSFER" || transferDestId != null)

                    Button(
                        onClick = {
                            onSave(
                                type,
                                rawAmount,
                                if (type == "TRANSFER") transferDestId else null,
                                if (isMultiCurrencyTransfer) rate else null,
                                notes.ifBlank { null },
                                timestamp
                            )
                        },
                        enabled = isValid
                    ) {
                        Text("Update Ledger")
                    }
                }
            }
        }
    }
}
}

// Formatter Helpers
private fun formatAmount(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.US)
    return formatter.format(amount)
}

internal fun formatAbbreviated(valueUsd: Double, baseCurrency: String, viewModel: NetWorthViewModel, valuesHidden: Boolean = false): String {
    val symbol = when (baseCurrency) {
        "PHP" -> "₱"
        "SAR" -> "SR"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        "AUD" -> "A$"
        "CAD" -> "C$"
        else -> baseCurrency
    }
    val prefix = if (symbol == baseCurrency) "" else symbol
    val suffix = if (symbol == baseCurrency) " $baseCurrency" else ""

    if (valuesHidden) {
        return "$prefix••••$suffix"
    }

    val rate = viewModel.exchangeRates[baseCurrency] ?: 1.0
    val value = if (baseCurrency == "USD") valueUsd else valueUsd / rate
    val absValue = Math.abs(value)
    
    val formatted = when {
        absValue >= 1_000_000 -> {
            val millions = value / 1_000_000
            String.format("%.1fM", millions)
        }
        absValue >= 1_000 -> {
            val thousands = value / 1_000
            String.format("%.0fk", thousands)
        }
        else -> {
            String.format("%.0f", value)
        }
    }
    return "$prefix$formatted$suffix"
}

@Composable
fun ManageBucketsDialog(
    buckets: List<Bucket>,
    baseCurrency: String,
    onDismiss: () -> Unit,
    onSaveBucket: (
        name: String,
        description: String,
        targetAmount: Double,
        isDecumulation: Boolean,
        yearlySpendBudget: Double,
        bufferYears: Int,
        warningThresholdPercent: Double,
        targetGainPercent: Double,
        lastYearPerformancePercent: Double
    ) -> Unit,
    onUpdateBucket: (
        id: Int,
        name: String,
        description: String,
        targetAmount: Double,
        isDecumulation: Boolean,
        yearlySpendBudget: Double,
        bufferYears: Int,
        warningThresholdPercent: Double,
        targetGainPercent: Double,
        lastYearPerformancePercent: Double
    ) -> Unit,
    onDeleteBucket: (Bucket) -> Unit
) {
    var newBucketName by remember { mutableStateOf("") }
    var newBucketDesc by remember { mutableStateOf("") }
    
    var isDecumulation by remember { mutableStateOf(false) }
    
    // Accumulation states
    var newBucketTarget by remember { mutableStateOf("") }
    var newTargetGain by remember { mutableStateOf("6.0") }
    var newLastYearPerf by remember { mutableStateOf("") }
    
    // Decumulation states
    var newYearlySpend by remember { mutableStateOf("") }
    var newBufferYears by remember { mutableStateOf("5") }
    var newWarningThreshold by remember { mutableStateOf("20.0") }
    
    var editingBucketId by remember { mutableStateOf<Int?>(null) }

    fun resetForm() {
        editingBucketId = null
        newBucketName = ""
        newBucketDesc = ""
        isDecumulation = false
        newBucketTarget = ""
        newTargetGain = "6.0"
        newLastYearPerf = ""
        newYearlySpend = ""
        newBufferYears = "5"
        newWarningThreshold = "20.0"
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("manage_buckets_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Manage Retirement Strategy Buckets",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "Set different strategy behaviors for each bucket: Accumulation (build and grow with skim alerts) or Decumulation (buffer spending guardrails).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Configured Buckets",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Render current buckets
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (buckets.isEmpty()) {
                            item {
                                Text(
                                    "No buckets configured. Create one below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            items(buckets) { bucket ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = bucket.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        val subText = if (bucket.isDecumulation) {
                                            "💸 Decumulation • Spend limit: ${formatAssetCurrency(bucket.yearlySpendBudget, baseCurrency, valuesHidden = false)}/yr • Buffer: ${bucket.bufferYears}Y"
                                        } else {
                                            "📈 Accumulation • Target: ${if (bucket.targetAmount > 0.0) formatAssetCurrency(bucket.targetAmount, baseCurrency, valuesHidden = false) else "None"} • Min Gain: ${bucket.targetGainPercent}%"
                                        }
                                        Text(
                                            text = subText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Edit button
                                        IconButton(
                                            onClick = {
                                                editingBucketId = bucket.id
                                                newBucketName = bucket.name
                                                newBucketDesc = bucket.description
                                                isDecumulation = bucket.isDecumulation
                                                newBucketTarget = if (bucket.targetAmount > 0.0) String.format(Locale.US, "%.2f", bucket.targetAmount) else ""
                                                newTargetGain = String.format(Locale.US, "%.1f", bucket.targetGainPercent)
                                                newLastYearPerf = if (bucket.lastYearPerformancePercent != 0.0) String.format(Locale.US, "%.1f", bucket.lastYearPerformancePercent) else ""
                                                newYearlySpend = if (bucket.yearlySpendBudget > 0.0) String.format(Locale.US, "%.2f", bucket.yearlySpendBudget) else ""
                                                newBufferYears = bucket.bufferYears.toString()
                                                newWarningThreshold = String.format(Locale.US, "%.1f", bucket.warningThresholdPercent)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit bucket",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Delete button
                                        IconButton(
                                            onClick = {
                                                if (editingBucketId == bucket.id) {
                                                    resetForm()
                                                }
                                                onDeleteBucket(bucket)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete bucket",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

                // Input form for creating/editing a bucket
                Text(
                    text = if (editingBucketId != null) "Edit Bucket details" else "Create New Strategy Bucket",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = newBucketName,
                    onValueChange = { newBucketName = it },
                    label = { Text("Bucket Name (e.g., Tactical Equities)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newBucketDesc,
                    onValueChange = { newBucketDesc = it },
                    label = { Text("Description (e.g., Long term growth and dividends)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Strategy selector row
                Text(
                    text = "Retirement Bucket Strategy Mode",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isDecumulation) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { isDecumulation = false }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Accumulation 📈",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (!isDecumulation) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDecumulation) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { isDecumulation = true }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Decumulation 💸",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDecumulation) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (!isDecumulation) {
                    // Accumulation Field Options
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = newBucketTarget,
                            onValueChange = { input ->
                                if (input.all { c -> c.isDigit() || c == '.' }) {
                                    newBucketTarget = input
                                }
                            },
                            label = { Text("Target Capital Amount (Optional, in $baseCurrency)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = newTargetGain,
                                onValueChange = { input ->
                                    if (input.all { c -> c.isDigit() || c == '.' }) {
                                        newTargetGain = input
                                    }
                                },
                                label = { Text("Annual Target % Gain (e.g. 6.0)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = newLastYearPerf,
                                onValueChange = { input ->
                                    if (input.all { c -> c.isDigit() || c == '.' || c == '-' }) {
                                        newLastYearPerf = input
                                    }
                                },
                                label = { Text("Prior Year Perf %") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1.0f)
                            )
                        }
                        Text(
                            text = "If Prior Year Performance exceeds the Annual Target % Gain, a skimming alert indicator lights up, alerting you to skim growth and funnel to Decumulation cash spending buckets.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Decumulation Field Options
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = newYearlySpend,
                            onValueChange = { input ->
                                if (input.all { c -> c.isDigit() || c == '.' }) {
                                    newYearlySpend = input
                                }
                            },
                            label = { Text("Yearly Spend Budget Limit (in $baseCurrency)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = newBufferYears,
                                onValueChange = { input ->
                                    if (input.all { c -> c.isDigit() }) {
                                        newBufferYears = input
                                    }
                                },
                                label = { Text("Buffer Capacity (Years)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1.2f)
                            )

                            OutlinedTextField(
                                value = newWarningThreshold,
                                onValueChange = { input ->
                                    if (input.all { c -> c.isDigit() || c == '.' }) {
                                        newWarningThreshold = input
                                    }
                                },
                                label = { Text("Min Level warning % (e.g. 20)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1.8f)
                            )
                        }
                        Text(
                            text = "Sets a multi-year cash spending runway. If the total bucket valuation falls below the Min Level guardrail percentage, the status indicator warns RED/Critical.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        if (editingBucketId != null) {
                            resetForm()
                        } else {
                            onDismiss()
                        }
                    }) {
                        Text(if (editingBucketId != null) "Cancel" else "Close")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newBucketName.isNotBlank()) {
                                val currId = editingBucketId
                                val targetVal = newBucketTarget.toDoubleOrNull() ?: 0.0
                                val targetGainVal = newTargetGain.toDoubleOrNull() ?: 6.0
                                val lastPerfVal = newLastYearPerf.toDoubleOrNull() ?: 0.0
                                val yearlySpendVal = newYearlySpend.toDoubleOrNull() ?: 0.0
                                val bufferYrsVal = newBufferYears.toIntOrNull() ?: 5
                                val warningPctVal = newWarningThreshold.toDoubleOrNull() ?: 20.0

                                if (currId != null) {
                                    onUpdateBucket(
                                        currId,
                                        newBucketName,
                                        newBucketDesc,
                                        targetVal,
                                        isDecumulation,
                                        yearlySpendVal,
                                        bufferYrsVal,
                                        warningPctVal,
                                        targetGainVal,
                                        lastPerfVal
                                    )
                                } else {
                                    onSaveBucket(
                                        newBucketName,
                                        newBucketDesc,
                                        targetVal,
                                        isDecumulation,
                                        yearlySpendVal,
                                        bufferYrsVal,
                                        warningPctVal,
                                        targetGainVal,
                                        lastPerfVal
                                    )
                                }
                                resetForm()
                            }
                        },
                        enabled = newBucketName.isNotBlank()
                    ) {
                        Text(if (editingBucketId != null) "Update" else "Add Bucket")
                    }
                }
            }
        }
    }
}

@Composable
fun AssignBucketDialog(
    asset: Asset,
    buckets: List<Bucket>,
    onDismiss: () -> Unit,
    onAssign: (bucketId: Int?) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("assign_bucket_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Assign Bucket for \"${asset.name}\"",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "Distribute this asset or liability to a specific retirement bucket for planning withdrawal phases.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1: Unassigned
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAssign(null)
                                },
                            color = if (asset.bucketId == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Unassigned Items",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (asset.bucketId == null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Keep asset or liability outside of retirement buckets",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (asset.bucketId == null) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (asset.bucketId == null) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Options: Buckets list
                    items(buckets) { bucket ->
                        val isSelected = asset.bucketId == bucket.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAssign(bucket.id)
                                },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bucket.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (bucket.description.isNotBlank()) {
                                        Text(
                                            text = bucket.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun AllTransactionsTab(
    transactions: List<Transaction>,
    assets: List<Asset>,
    onDeleteTransaction: (Transaction) -> Unit,
    viewModel: NetWorthViewModel
) {
    val valuesHidden by viewModel.valuesHidden.collectAsStateWithLifecycle()
    val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    
    var selectedAssetId by remember { mutableStateOf<Int?>(null) }
    var selectedDateShortcut by remember { mutableStateOf("ALL") }
    var editingTx by remember { mutableStateOf<Transaction?>(null) }

    val startTimestamp = remember(selectedDateShortcut) {
        val cal = java.util.Calendar.getInstance()
        when (selectedDateShortcut) {
            "1M" -> {
                cal.add(java.util.Calendar.MONTH, -1)
                cal.timeInMillis
            }
            "1Y" -> {
                cal.add(java.util.Calendar.YEAR, -1)
                cal.timeInMillis
            }
            "YTD" -> {
                cal.set(java.util.Calendar.DAY_OF_YEAR, 1)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            else -> 0L // "ALL"
        }
    }

    val filteredTransactions = remember(transactions, selectedAssetId, startTimestamp) {
        transactions.filter { tx ->
            val matchAsset = selectedAssetId == null || 
                             tx.assetId == selectedAssetId || 
                             (tx.type == "TRANSFER" && tx.destinationAssetId == selectedAssetId)
            val matchDate = startTimestamp == 0L || tx.timestamp >= startTimestamp
            matchAsset && matchDate
        }
    }

    val sortedTransactions = remember(filteredTransactions) {
        filteredTransactions.sortedWith(compareByDescending<com.example.data.model.Transaction> { it.timestamp }.thenByDescending { it.id })
    }

    // Totals calculations based on active filters
    val targetCurrency = remember(selectedAssetId, assets) {
        assets.find { it.id == selectedAssetId }?.currency ?: "USD"
    }

    val depositTotal = remember(filteredTransactions, assets, selectedAssetId, categories) {
        if (selectedAssetId == null) {
            filteredTransactions.sumOf { tx ->
                val txAsset = assets.find { it.id == tx.assetId }
                val destAsset = assets.find { it.id == tx.destinationAssetId }
                val isAsset = txAsset?.categoryId?.let { catId -> categories.find { it.id == catId }?.isAsset ?: true } ?: true
                
                val amountUsd = viewModel.convertToUsd(tx.amount, txAsset?.currency ?: "USD")
                
                when (tx.type) {
                    "DEPOSIT" -> {
                        if (isAsset) amountUsd else -amountUsd
                    }
                    "WITHDRAWAL" -> {
                        if (isAsset) -amountUsd else amountUsd
                    }
                    "TRANSFER" -> {
                        // Transfer out from source
                        var effect = if (isAsset) -amountUsd else amountUsd
                        
                        // Transfer in to destination
                        if (destAsset != null) {
                            val destIsAsset = categories.find { it.id == destAsset.categoryId }?.isAsset ?: true
                            val incomingAmount = tx.amount * (tx.exchangeRate ?: 1.0)
                            val incomingUsd = viewModel.convertToUsd(incomingAmount, destAsset.currency)
                            effect += if (destIsAsset) incomingUsd else -incomingUsd
                        }
                        effect
                    }
                    else -> 0.0
                }
            }
        } else {
            val selectedAsset = assets.find { it.id == selectedAssetId }
            val selectedIsAsset = selectedAsset?.categoryId?.let { catId -> categories.find { it.id == catId }?.isAsset ?: true } ?: true
            
            filteredTransactions.sumOf { tx ->
                var effect = 0.0
                if (tx.assetId == selectedAssetId) {
                    val amount = tx.amount
                    when (tx.type) {
                        "DEPOSIT" -> effect += if (selectedIsAsset) amount else -amount
                        "WITHDRAWAL" -> effect -= if (selectedIsAsset) amount else -amount
                        "TRANSFER" -> effect -= if (selectedIsAsset) amount else -amount
                    }
                }
                if (tx.destinationAssetId == selectedAssetId && tx.type == "TRANSFER") {
                    val incomingAmount = tx.amount * (tx.exchangeRate ?: 1.0)
                    effect += if (selectedIsAsset) incomingAmount else -incomingAmount
                }
                effect
            }
        }
    }

    val withdrawalTotal = remember(filteredTransactions, assets, selectedAssetId) {
        if (selectedAssetId == null) {
            filteredTransactions.filter { it.type == "WITHDRAWAL" }.sumOf { tx ->
                val txAsset = assets.find { it.id == tx.assetId }
                viewModel.convertToUsd(tx.amount, txAsset?.currency ?: "USD")
            }
        } else {
            filteredTransactions.filter { it.type == "WITHDRAWAL" }.sumOf { it.amount }
        }
    }

    val incomeTotal = remember(filteredTransactions, assets, selectedAssetId) {
        if (selectedAssetId == null) {
            filteredTransactions.filter { it.type == "INCOME" }.sumOf { tx ->
                val txAsset = assets.find { it.id == tx.assetId }
                viewModel.convertToUsd(tx.amount, txAsset?.currency ?: "USD")
            }
        } else {
            filteredTransactions.filter { it.type == "INCOME" }.sumOf { it.amount }
        }
    }

    val formattedDeposit = if (selectedAssetId == null) {
        formatPortfolioAmount(depositTotal, baseCurrency, viewModel, valuesHidden = valuesHidden)
    } else {
        formatAssetCurrency(depositTotal, targetCurrency, valuesHidden = valuesHidden)
    }

    val formattedWithdrawal = if (selectedAssetId == null) {
        formatPortfolioAmount(withdrawalTotal, baseCurrency, viewModel, valuesHidden = valuesHidden)
    } else {
        formatAssetCurrency(withdrawalTotal, targetCurrency, valuesHidden = valuesHidden)
    }

    val formattedIncome = if (selectedAssetId == null) {
        formatPortfolioAmount(incomeTotal, baseCurrency, viewModel, valuesHidden = valuesHidden)
    } else {
        formatAssetCurrency(incomeTotal, targetCurrency, valuesHidden = valuesHidden)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter and Metrics Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Filters Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Dropdown for Asset Selector
                    var assetDropdownExpanded by remember { mutableStateOf(false) }
                    val selectedAsset = assets.find { it.id == selectedAssetId }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedCard(
                            onClick = { assetDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = selectedAsset?.name ?: "All Assets",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = assetDropdownExpanded,
                            onDismissRequest = { assetDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Assets") },
                                onClick = {
                                    selectedAssetId = null
                                    assetDropdownExpanded = false
                                }
                            )
                            assets.forEach { asset ->
                                DropdownMenuItem(
                                    text = { Text(asset.name) },
                                    onClick = {
                                        selectedAssetId = asset.id
                                        assetDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Shortcut Date Filters
                    val shortcuts = listOf("1M", "1Y", "YTD", "ALL")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        shortcuts.forEach { shortcut ->
                            val isSel = selectedDateShortcut == shortcut
                            Surface(
                                onClick = { selectedDateShortcut = shortcut },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (isSel) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = shortcut,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                // Summary Numbers (Deposit, Withdraw, Income)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Net Deposit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formattedDeposit,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (depositTotal >= 0.0) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Withdraw",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formattedWithdrawal,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFEF4444)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Income",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formattedIncome,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF3B82F6)
                        )
                    }
                }
            }
        }

        if (sortedTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(72.dp)
                    )
                    Text(
                        text = "No filtered transactions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Try adjusting your asset or date filter combinations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sortedTransactions) { tx ->
                    val asset = assets.find { it.id == tx.assetId }
                    val isLiability = asset?.let { a -> categories.find { it.id == a.categoryId }?.isAsset == false } ?: false
                    TransactionCard(
                        tx = tx,
                        asset = asset,
                        isLiability = isLiability,
                        allTransactions = transactions,
                        allAssets = assets,
                        valuesHidden = valuesHidden,
                        selectedAssetId = selectedAssetId,
                        onDelete = { onDeleteTransaction(tx) },
                        onEdit = { editingTx = tx }
                    )
                }
            }
        }
    }

    // Trigger Edit Transaction dialog
    editingTx?.let { tx ->
        val asset = assets.find { it.id == tx.assetId }
        val restAssets = assets.filter { it.id != tx.assetId }
        if (asset != null) {
            EditTransactionSubDialog(
                tx = tx,
                asset = asset,
                otherAssets = restAssets,
                transactions = transactions,
                viewModel = viewModel,
                onDismiss = { editingTx = null },
                onSave = { type, amt, destId, rate, notes, updatedTimestamp ->
                    viewModel.updateTransaction(tx.id, tx.assetId, type, amt, destId, rate, notes, updatedTimestamp)
                    editingTx = null
                }
            )
        }
    }
}

@Composable
fun TransactionCard(
    tx: Transaction,
    asset: Asset?,
    isLiability: Boolean,
    allTransactions: List<Transaction>,
    allAssets: List<Asset>,
    valuesHidden: Boolean,
    selectedAssetId: Int? = null,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateStr = remember(tx.timestamp) {
        SimpleDateFormat("MMM dd, yyyy · hh:mma", Locale.getDefault()).format(Date(tx.timestamp))
    }

    val destAsset = remember(tx.destinationAssetId, allAssets) {
        allAssets.find { it.id == tx.destinationAssetId }
    }

    val isIncomingTransfer = remember(tx, selectedAssetId) {
        selectedAssetId != null && tx.type == "TRANSFER" && tx.destinationAssetId == selectedAssetId
    }

    val (displayAmount, displayCurrency) = remember(tx, selectedAssetId, asset, destAsset) {
        if (isIncomingTransfer && destAsset != null) {
            val incomingAmount = tx.amount * (tx.exchangeRate ?: 1.0)
            Pair(incomingAmount, destAsset.currency)
        } else {
            Pair(tx.amount, asset?.currency ?: "USD")
        }
    }

    val txColor = remember(tx, isLiability, allTransactions, isIncomingTransfer, selectedAssetId) {
        if (isIncomingTransfer) {
            Color(0xFF10B981) // Incoming transfer is always positive/green
        } else if (isLiability) {
            when (tx.type) {
                "DEPOSIT" -> Color(0xFF4ADE80) // Paying off liability/debt is good (lighter green)
                "WITHDRAWAL" -> Color(0xFFEF4444) // Adding liability balance is debt addition (red)
                "INCOME" -> Color(0xFF10B981)
                "TRANSFER" -> {
                    if (selectedAssetId == null) Color(0xFF8B5CF6) else Color(0xFFEF4444)
                }
                "UPDATE" -> {
                    val prevVal = getValuationBeforeTransaction(tx.assetId, tx, allTransactions)
                    if (prevVal == null) {
                        Color(0xFFF97316) // Initial liability setup is orange
                    } else if (tx.amount < prevVal) {
                        Color(0xFF10B981) // Debt decreased (standard green)
                    } else if (tx.amount > prevVal) {
                        Color(0xFFF97316) // Debt increased (orange)
                    } else {
                        Color(0xFF94A3B8)
                    }
                }
                else -> Color(0xFF94A3B8)
            }
        } else {
            when (tx.type) {
                "DEPOSIT" -> Color(0xFF4ADE80) // Lighter green for deposit
                "WITHDRAWAL" -> Color(0xFFEF4444)
                "INCOME" -> Color(0xFF3B82F6)
                "TRANSFER" -> {
                    if (selectedAssetId == null) Color(0xFF8B5CF6) else Color(0xFFEF4444)
                }
                "UPDATE" -> {
                    val prevVal = getValuationBeforeTransaction(tx.assetId, tx, allTransactions)
                    if (prevVal == null) {
                        Color(0xFF10B981) // Initial asset setup is standard green
                    } else if (tx.amount > prevVal) {
                        Color(0xFF10B981) // Value grew (standard green)
                    } else if (tx.amount < prevVal) {
                        Color(0xFFF97316) // Value shrank (orange)
                    } else {
                        Color(0xFF94A3B8)
                    }
                }
                else -> Color(0xFF94A3B8)
            }
        }
    }

    val sign = remember(tx, selectedAssetId, isLiability, isIncomingTransfer) {
        when (tx.type) {
            "DEPOSIT" -> "+"
            "WITHDRAWAL" -> "-"
            "INCOME" -> "+"
            "TRANSFER" -> {
                if (selectedAssetId == null) {
                    ""
                } else if (isIncomingTransfer) {
                    "+"
                } else {
                    "-"
                }
            }
            else -> ""
        }
    }

    val chipBgColor = remember(tx.type, txColor, isIncomingTransfer, selectedAssetId) {
        if (tx.type == "TRANSFER") {
            if (selectedAssetId == null) {
                Color(0xFF8B5CF6).copy(alpha = 0.15f)
            } else if (isIncomingTransfer) {
                Color(0xFF10B981).copy(alpha = 0.15f)
            } else {
                Color(0xFFEF4444).copy(alpha = 0.15f)
            }
        } else {
            txColor.copy(alpha = 0.15f)
        }
    }

    val chipTextColor = remember(tx.type, txColor, isIncomingTransfer, selectedAssetId) {
        if (tx.type == "TRANSFER") {
            if (selectedAssetId == null) {
                Color(0xFFA78BFA)
            } else if (isIncomingTransfer) {
                Color(0xFF10B981)
            } else {
                Color(0xFFEF4444)
            }
        } else {
            txColor
        }
    }

    val formattedAmount = formatAssetCurrency(displayAmount, displayCurrency, valuesHidden = valuesHidden)

    val titleText = remember(tx, selectedAssetId, asset, destAsset) {
        if (selectedAssetId != null && tx.type == "TRANSFER" && tx.destinationAssetId == selectedAssetId && destAsset != null) {
            destAsset.name
        } else {
            asset?.name ?: "Deleted Asset"
        }
    }

    val transferDetailText = remember(tx, selectedAssetId, asset, destAsset) {
        if (tx.type == "TRANSFER") {
            if (selectedAssetId == null) {
                "➔ ${destAsset?.name ?: "Unknown"}"
            } else if (tx.destinationAssetId == selectedAssetId) {
                "Received from ${asset?.name ?: "Unknown"}"
            } else {
                "Sent to ${destAsset?.name ?: "Unknown"}"
            }
        } else {
            ""
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
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Surface(
                        color = chipBgColor,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = tx.type,
                            style = MaterialTheme.typography.labelSmall,
                            color = chipTextColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                val displayNote = remember(tx.notes) {
                    if (tx.notes?.contains("|") == true) {
                        val parts = tx.notes.split("|")
                        if (parts.size > 1 && parts[1].trim().isNotEmpty()) {
                            parts[1].trim()
                        } else {
                            tx.notes.replace("|", "").trim()
                        }
                    } else {
                        tx.notes ?: ""
                    }
                }
                
                val noteLine = remember(displayNote, transferDetailText) {
                    if (transferDetailText.isNotEmpty()) {
                        if (displayNote.isNotEmpty()) "$transferDetailText · $displayNote" else transferDetailText
                    } else {
                        displayNote
                    }
                }
                
                if (noteLine.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = noteLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
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
                    text = "$sign$formattedAmount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = txColor
                )
                
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun getValuationBeforeTransaction(
    assetId: Int,
    targetTx: Transaction,
    transactions: List<Transaction>
): Double? {
    val relevant = transactions.filter {
        (it.assetId == assetId || it.destinationAssetId == assetId) &&
        (it.timestamp < targetTx.timestamp || (it.timestamp == targetTx.timestamp && it.id < targetTx.id))
    }

    val updateTxs = relevant.filter { it.assetId == assetId && it.type == "UPDATE" }
        .sortedWith(compareBy<Transaction> { it.timestamp }.thenBy { it.id })
    val latestUpdate = updateTxs.lastOrNull()

    if (latestUpdate == null && relevant.isEmpty()) {
        return null
    }

    val latestUpdateTimestamp = latestUpdate?.timestamp ?: 0L
    val baseValuation = latestUpdate?.amount ?: 0.0

    var additions = 0.0
    val txsAfterUpdate = if (latestUpdate != null) {
        relevant.filter { tx -> tx.timestamp > latestUpdateTimestamp || (tx.timestamp == latestUpdateTimestamp && tx.id > latestUpdate.id) }
    } else {
        relevant
    }

    if (latestUpdate == null) {
        var netDeposits = 0.0
        var totalIncome = 0.0
        for (tx in relevant) {
            if (tx.assetId == assetId) {
                when (tx.type) {
                    "DEPOSIT" -> netDeposits += tx.amount
                    "WITHDRAWAL" -> netDeposits -= tx.amount
                    "INCOME" -> totalIncome += tx.amount
                    "TRANSFER" -> netDeposits -= tx.amount
                }
            } else if (tx.destinationAssetId == assetId && tx.type == "TRANSFER") {
                val incomingAmount = tx.amount * (tx.exchangeRate ?: 1.0)
                netDeposits += incomingAmount
            }
        }
        return netDeposits + totalIncome
    } else {
        for (tx in txsAfterUpdate) {
            if (tx.assetId == assetId) {
                when (tx.type) {
                    "DEPOSIT" -> additions += tx.amount
                    "WITHDRAWAL" -> additions -= tx.amount
                    "INCOME" -> additions += tx.amount
                    "TRANSFER" -> additions -= tx.amount
                }
            } else if (tx.destinationAssetId == assetId && tx.type == "TRANSFER") {
                val incomingAmount = tx.amount * (tx.exchangeRate ?: 1.0)
                additions += incomingAmount
            }
        }
        return baseValuation + additions
    }
}

@Composable
fun ComingSoonTab(
    title: String,
    description: String,
    icon: ImageVector
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(100.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp)
            )
        }
    }
}

private fun getNetWorthLocal(
    targetTime: Long,
    txList: List<Transaction>,
    assetList: List<Asset>,
    categoryList: List<AssetCategory>,
    rates: Map<String, Double>
): Double {
    if (txList.isEmpty() || assetList.isEmpty()) return 0.0
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

        var netDepositsAtStep = 0.0
        var incomeAtStep = 0.0

        val stepFirstUpdate = assetTxsAtStep.firstOrNull { it.type == "UPDATE" }
        val stepDepositsBeforeUpdate = if (stepFirstUpdate != null) {
            assetTxsAtStep.filter { 
                it.timestamp < stepFirstUpdate.timestamp && (it.type == "DEPOSIT" || it.type == "TRANSFER") 
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
                when (tx.type) {
                    "DEPOSIT" -> netDepositsAtStep += tx.amount
                    "WITHDRAWAL" -> netDepositsAtStep -= tx.amount
                    "INCOME" -> incomeAtStep += tx.amount
                    "TRANSFER" -> netDepositsAtStep -= tx.amount
                }
            } else if (tx.destinationAssetId == asset.id) {
                if (tx.type == "TRANSFER") {
                    val incoming = tx.amount * (tx.exchangeRate ?: 1.0)
                    netDepositsAtStep += incoming
                }
            }
        }

        val updateTxsAtStep = assetTxsAtStep.filter { it.assetId == asset.id && it.type == "UPDATE" }
            .sortedWith(compareBy<com.example.data.model.Transaction> { it.timestamp }.thenBy { it.id })

        val stepLatestUpdate = updateTxsAtStep.lastOrNull()
        val stepLatestUpdateTimestamp = stepLatestUpdate?.timestamp ?: 0L
        val stepBaseValuation = stepLatestUpdate?.amount ?: 0.0

        var stepAdditionsAfterUpdate = 0.0
        val transactionsAfterStepUpdate = if (stepLatestUpdate != null) {
            assetTxsAtStep.filter { tx -> tx.timestamp > stepLatestUpdateTimestamp || (tx.timestamp == stepLatestUpdateTimestamp && tx.id > stepLatestUpdate.id) }
        } else {
            assetTxsAtStep
        }

        for (tx in transactionsAfterStepUpdate) {
            if (tx.assetId == asset.id) {
                when (tx.type) {
                    "DEPOSIT" -> stepAdditionsAfterUpdate += tx.amount
                    "WITHDRAWAL" -> stepAdditionsAfterUpdate -= tx.amount
                    "INCOME" -> stepAdditionsAfterUpdate += tx.amount
                    "TRANSFER" -> stepAdditionsAfterUpdate -= tx.amount
                }
            } else if (tx.destinationAssetId == asset.id) {
                if (tx.type == "TRANSFER") {
                    val incoming = tx.amount * (tx.exchangeRate ?: 1.0)
                    stepAdditionsAfterUpdate += incoming
                }
            }
        }

        val currentValNativeAtStep = if (stepLatestUpdate == null) {
            netDepositsAtStep + incomeAtStep
        } else {
            stepBaseValuation + stepAdditionsAfterUpdate
        }

        val rate = rates[asset.currency] ?: 0.0
        val usdVal = if (rate > 0.0) currentValNativeAtStep / rate else currentValNativeAtStep
        if (isAssetType) {
            stepAssetsUsd += usdVal
        } else {
            stepLiabilitiesUsd += usdVal
        }
    }
    return stepAssetsUsd - stepLiabilitiesUsd
}

private fun getFinancialSnapshotAt(
    targetTime: Long,
    txList: List<Transaction>,
    assetList: List<Asset>,
    categoryList: List<AssetCategory>,
    rates: Map<String, Double>
): Triple<Double, Double, Double> {
    if (txList.isEmpty() || assetList.isEmpty()) return Triple(0.0, 0.0, 0.0)
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

        var netDepositsAtStep = 0.0
        var incomeAtStep = 0.0

        val stepFirstUpdate = assetTxsAtStep.firstOrNull { it.type == "UPDATE" }
        val stepDepositsBeforeUpdate = if (stepFirstUpdate != null) {
            assetTxsAtStep.filter { 
                it.timestamp < stepFirstUpdate.timestamp && (it.type == "DEPOSIT" || it.type == "TRANSFER") 
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
                when (tx.type) {
                    "DEPOSIT" -> netDepositsAtStep += tx.amount
                    "WITHDRAWAL" -> netDepositsAtStep -= tx.amount
                    "INCOME" -> incomeAtStep += tx.amount
                    "TRANSFER" -> netDepositsAtStep -= tx.amount
                }
            } else if (tx.destinationAssetId == asset.id) {
                if (tx.type == "TRANSFER") {
                    val incoming = tx.amount * (tx.exchangeRate ?: 1.0)
                    netDepositsAtStep += incoming
                }
            }
        }

        val updateTxsAtStep = assetTxsAtStep.filter { it.assetId == asset.id && it.type == "UPDATE" }
            .sortedWith(compareBy<com.example.data.model.Transaction> { it.timestamp }.thenBy { it.id })

        val stepLatestUpdate = updateTxsAtStep.lastOrNull()
        val stepLatestUpdateTimestamp = stepLatestUpdate?.timestamp ?: 0L
        val stepBaseValuation = stepLatestUpdate?.amount ?: 0.0

        var stepAdditionsAfterUpdate = 0.0
        val transactionsAfterStepUpdate = if (stepLatestUpdate != null) {
            assetTxsAtStep.filter { tx -> tx.timestamp > stepLatestUpdateTimestamp || (tx.timestamp == stepLatestUpdateTimestamp && tx.id > stepLatestUpdate.id) }
        } else {
            assetTxsAtStep
        }

        for (tx in transactionsAfterStepUpdate) {
            if (tx.assetId == asset.id) {
                when (tx.type) {
                    "DEPOSIT" -> stepAdditionsAfterUpdate += tx.amount
                    "WITHDRAWAL" -> stepAdditionsAfterUpdate -= tx.amount
                    "INCOME" -> stepAdditionsAfterUpdate += tx.amount
                    "TRANSFER" -> stepAdditionsAfterUpdate -= tx.amount
                }
            } else if (tx.destinationAssetId == asset.id) {
                if (tx.type == "TRANSFER") {
                    val incoming = tx.amount * (tx.exchangeRate ?: 1.0)
                    stepAdditionsAfterUpdate += incoming
                }
            }
        }

        val currentValNativeAtStep = if (stepLatestUpdate == null) {
            netDepositsAtStep + incomeAtStep
        } else {
            stepBaseValuation + stepAdditionsAfterUpdate
        }

        val rate = rates[asset.currency] ?: 0.0
        val usdVal = if (rate > 0.0) currentValNativeAtStep / rate else currentValNativeAtStep
        if (isAssetType) {
            stepAssetsUsd += usdVal
        } else {
            stepLiabilitiesUsd += usdVal
        }
    }
    return Triple(stepAssetsUsd, stepLiabilitiesUsd, stepAssetsUsd - stepLiabilitiesUsd)
}

@Composable
fun AnalyticsTab(
    transactions: List<Transaction>,
    assets: List<Asset>,
    categories: List<AssetCategory>,
    viewModel: NetWorthViewModel
) {
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var showFutureDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    if (showFutureDialog != null) {
        AlertDialog(
            onDismissRequest = { showFutureDialog = null },
            title = { Text(showFutureDialog!!.first) },
            text = { Text(showFutureDialog!!.second) },
            confirmButton = {
                TextButton(onClick = { showFutureDialog = null }) {
                    Text("OK")
                }
            }
        )
    }

    if (selectedOption == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Analysis & Insights",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = "Select a report or simulation layout below to deep dive into your financial indicators.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 1. Portfolio Performance Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedOption = "performance" },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Portfolio Performance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Deep dive into your period-wise capital growths, net additions, and money-weighted return yields.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // 2. Asset Allocation Card
            Card(
                modifier = Modifier
                     .fillMaxWidth()
                     .clickable { selectedOption = "allocation" },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFF3B82F6).copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PieChart,
                                contentDescription = null,
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Asset Allocation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Analyze asset weight distributions, category breakdowns, and target portfolio rebalancing deviations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // 3. Retirement Bucket Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        selectedOption = "retirement"
                     }
                    .testTag("retirement_strategy_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Spa,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Retirement Bucket Strategy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Simulate retirement horizons, plan 3-bucket drawdown layers, and run volatility-based longevity stress tests dynamically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    } else if (selectedOption == "performance") {
        PortfolioPerformanceView(
            viewModel = viewModel,
            transactions = transactions,
            assets = assets,
            categories = categories,
            onBack = { selectedOption = null }
        )
    } else if (selectedOption == "allocation") {
        PortfolioAllocationView(
            viewModel = viewModel,
            transactions = transactions,
            assets = assets,
            categories = categories,
            onBack = { selectedOption = null }
        )
    } else if (selectedOption == "retirement") {
        RetirementStrategyTrackerView(
            viewModel = viewModel,
            transactions = transactions,
            assets = assets,
            categories = categories,
            onBack = { selectedOption = null }
        )
    }
 }


@Composable
fun AddActionSelectionDialog(
    assets: List<Asset>,
    onDismiss: () -> Unit,
    onAddAsset: () -> Unit,
    onAddLiability: () -> Unit,
    onAddTransactionForAsset: (Asset) -> Unit
) {
    var showAssetListForTx by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!showAssetListForTx) {
                    Text(
                        text = "Create New Entry",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Would you like to register a new tracking asset, a liability/debt, or record a financial transaction?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            onAddAsset()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Savings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Tracking Asset", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            onAddLiability()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CreditCard, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Liability / Debt", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val activeAssetsList = assets.filter { !it.isArchived }
                            if (activeAssetsList.isEmpty()) {
                                onAddAsset()
                                onDismiss()
                            } else {
                                showAssetListForTx = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Paid, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (assets.filter { !it.isArchived }.isEmpty()) "Add Tracking Asset First" else "Record Transaction",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "Select Target Asset",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Which asset is this transaction associated with?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(assets.filter { !it.isArchived }) { asset ->
                            Surface(
                                onClick = {
                                    onAddTransactionForAsset(asset)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = asset.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = asset.currency,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = { showAssetListForTx = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Back")
                    }
                }
            }
        }
    }
}

// ==========================================
// SECURITY LOCK SCREEN MODULE 
// ==========================================
@Composable
fun LockScreen(
    appLockPin: String?,
    useBiometrics: Boolean,
    onSuccessfulUnlock: () -> Unit
) {
    var pinAttempt by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? androidx.fragment.app.FragmentActivity

    val triggerBiometrics: () -> Unit = {
        if (activity != null && useBiometrics) {
            val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
            val biometricPrompt = androidx.biometric.BiometricPrompt(
                activity,
                executor,
                object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                    }

                    override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccessfulUnlock()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                    }
                }
            )

            val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Unlock")
                .setSubtitle("Use fingerprint or face recognition to unlock")
                .setNegativeButtonText("Use PIN")
                .build()

            try {
                biometricPrompt.authenticate(promptInfo)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(useBiometrics) {
        if (useBiometrics) {
            triggerBiometrics()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left column - logo & dots
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Secured Vault Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Vault Secured",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Enter your 4-digit PIN",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Dots indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 4) {
                            val filled = i < pinAttempt.length
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        color = if (filled) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    // Error Display
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Right column - compact keypad
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val addDigit: (String) -> Unit = { num ->
                        if (pinAttempt.length < 4) {
                            errorMessage = null
                            val newPin = pinAttempt + num
                            pinAttempt = newPin
                            if (newPin.length == 4) {
                                if (newPin == appLockPin) {
                                    onSuccessfulUnlock()
                                } else {
                                    errorMessage = "Invalid security PIN passcode"
                                    pinAttempt = ""
                                }
                            }
                        }
                    }

                    // Row 1
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumericButton("1", size = 52.dp) { addDigit("1") }
                        NumericButton("2", size = 52.dp) { addDigit("2") }
                        NumericButton("3", size = 52.dp) { addDigit("3") }
                    }

                    // Row 2
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumericButton("4", size = 52.dp) { addDigit("4") }
                        NumericButton("5", size = 52.dp) { addDigit("5") }
                        NumericButton("6", size = 52.dp) { addDigit("6") }
                    }

                    // Row 3
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumericButton("7", size = 52.dp) { addDigit("7") }
                        NumericButton("8", size = 52.dp) { addDigit("8") }
                        NumericButton("9", size = 52.dp) { addDigit("9") }
                    }

                    // Row 4
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (useBiometrics) {
                            Surface(
                                onClick = { triggerBiometrics() },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "Trigger Biometric Unlock",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.size(52.dp))
                        }

                        NumericButton("0", size = 52.dp) { addDigit("0") }

                        // Backspace
                        Surface(
                            onClick = {
                                if (pinAttempt.isNotEmpty()) {
                                    errorMessage = null
                                    pinAttempt = pinAttempt.dropLast(1)
                                }
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Backspace,
                                    contentDescription = "Remove Last Digit",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                // Header
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Secured Vault Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Vault Secured",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Enter your 4-digit PIN to access NetWorth ledger",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // Dots indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val filled = i < pinAttempt.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    color = if (filled) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                // Error Display
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Numeric Keypad
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val addDigit: (String) -> Unit = { num ->
                        if (pinAttempt.length < 4) {
                            errorMessage = null
                            val newPin = pinAttempt + num
                            pinAttempt = newPin
                            if (newPin.length == 4) {
                                if (newPin == appLockPin) {
                                    onSuccessfulUnlock()
                                } else {
                                    errorMessage = "Invalid security PIN passcode"
                                    pinAttempt = ""
                                }
                            }
                        }
                    }

                    // Row 1
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        NumericButton("1") { addDigit("1") }
                        NumericButton("2") { addDigit("2") }
                        NumericButton("3") { addDigit("3") }
                    }

                    // Row 2
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        NumericButton("4") { addDigit("4") }
                        NumericButton("5") { addDigit("5") }
                        NumericButton("6") { addDigit("6") }
                    }

                    // Row 3
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        NumericButton("7") { addDigit("7") }
                        NumericButton("8") { addDigit("8") }
                        NumericButton("9") { addDigit("9") }
                    }

                    // Row 4
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (useBiometrics) {
                            Surface(
                                onClick = { triggerBiometrics() },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "Trigger Biometric Unlock",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.size(72.dp))
                        }

                        NumericButton("0") { addDigit("0") }

                        // Backspace
                        Surface(
                            onClick = {
                                if (pinAttempt.isNotEmpty()) {
                                    errorMessage = null
                                    pinAttempt = pinAttempt.dropLast(1)
                                }
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Backspace,
                                    contentDescription = "Remove Last Digit",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NumericButton(text: String, size: androidx.compose.ui.unit.Dp = 72.dp, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = if (size < 60.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}


// ==========================================
// PREFERENCES SETTINGS TAB MODULE
// ==========================================
@Composable
fun SettingsTab(
    viewModel: NetWorthViewModel,
    baseCurrency: String,
    themeIndex: Int,
    appLockPin: String?,
    onManageCategories: () -> Unit,
    onManageLiabilityCategories: () -> Unit,
    onManageBuckets: () -> Unit,
    onImportSuccess: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    val manualConversionMode by viewModel.manualConversionMode.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val exchangeRatesMap by viewModel.exchangeRatesState.collectAsStateWithLifecycle()
    val useBiometrics by viewModel.useBiometrics.collectAsStateWithLifecycle()

    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPinDisableDialog by remember { mutableStateOf(false) }
    var showPortabilityDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    
    var editingCurrency by remember { mutableStateOf<String?>(null) }
    var editingRateValue by remember { mutableStateOf("") }
    var isExchangeRatesExpanded by remember { mutableStateOf(false) }
    
    var portabilityResultText by remember { mutableStateOf<String?>(null) }

    // Launcher to save database and settings JSON to folder memory
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val jsonString = viewModel.exportDatabaseToJson()
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(jsonString.toByteArray())
                }
                portabilityResultText = "Successfully exported database & settings (.json) to phone memory!"
            } catch (e: Exception) {
                portabilityResultText = "Failed to export JSON file: ${e.message}"
            }
        }
    }

    // Launcher to select and restore database and settings JSON from folder memory
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (content.isNullOrBlank()) {
                    portabilityResultText = "Selected backup file (.json) is empty."
                } else {
                    scope.launch {
                        val success = viewModel.importDatabaseFromJson(content)
                        if (success) {
                            android.widget.Toast.makeText(context, "Import successful", android.widget.Toast.LENGTH_SHORT).show()
                            showPortabilityDialog = false
                            onImportSuccess()
                        } else {
                            portabilityResultText = "Failed to restore backup: Invalid JSON schema format."
                        }
                    }
                }
            } catch (e: Exception) {
                portabilityResultText = "Error reading backup file: ${e.message}"
            }
        }
    }

    // Launcher to save transactions ledger CSV to folder memory
    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                val csvContent = viewModel.exportTransactionsToCsv()
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(csvContent.toByteArray())
                }
                portabilityResultText = "Successfully exported ledger logs (.csv) to phone memory!"
            } catch (e: Exception) {
                portabilityResultText = "Failed to export CSV file: ${e.message}"
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Section: Display & Currency Preference
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Display Preferences",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Base Currency selection row
                    SettingsRow(
                        title = "Base Currency",
                        subtitle = "Currently representing overall values in [${CURRENCY_NAMES[baseCurrency] ?: baseCurrency}]",
                        icon = Icons.Default.MonetizationOn,
                        onClick = { showCurrencyDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Theme combination Selection Row
                    val currentThemeLabel = when (themeIndex) {
                        1 -> "Oceanic Breeze"
                        2 -> "Forest Emerald"
                        else -> "Default Material"
                    }
                    SettingsRow(
                        title = "Visual Color Theme",
                        subtitle = "Active Style: $currentThemeLabel",
                        icon = Icons.Default.Palette,
                        onClick = { showThemeDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Font size scaling row
                    val appFontScale by viewModel.appFontScale.collectAsStateWithLifecycle()
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "UI Font Scale Override",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Bypasses system scale to prevent unwanted word wrapping. Current multiplier: ${"%.2f".format(appFontScale)}x",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            Text("Aa", fontSize = 11.sp, fontWeight = FontWeight.Normal)
                            Slider(
                                value = appFontScale,
                                onValueChange = { scale ->
                                    viewModel.setAppFontScale(scale)
                                },
                                valueRange = 0.8f..1.4f,
                                modifier = Modifier.weight(1f)
                            )
                            Text("Aa", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section: Firebase Cloud Sync
        item {
            FirebaseSyncSection(viewModel = viewModel)
        }

        // Section: Portfolio Currency Conversion Manager
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Portfolio Currency Conversion",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Manual vs Automatic switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Manual Conversion Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (manualConversionMode) "Using user-defined exchange rates" else "Syncing live api rates automatically",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = manualConversionMode,
                            onCheckedChange = { viewModel.setManualConversionMode(it) },
                            modifier = Modifier.testTag("manual_conversion_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Internet Sync Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Exchange Rate Sync",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            when (syncStatus) {
                                is NetWorthViewModel.SyncStatus.Loading -> {
                                    Text(
                                        text = "Connecting to open.er-api.com... ⏳",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                is NetWorthViewModel.SyncStatus.Success -> {
                                    Text(
                                        text = (syncStatus as NetWorthViewModel.SyncStatus.Success).message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                                is NetWorthViewModel.SyncStatus.Error -> {
                                    Text(
                                        text = (syncStatus as NetWorthViewModel.SyncStatus.Error).error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "Refresh to update all conversion rates instantly",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.syncRatesFromInternet() },
                            enabled = syncStatus !is NetWorthViewModel.SyncStatus.Loading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("sync_rates_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // List of configurable currencies & their manual entries
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExchangeRatesExpanded = !isExchangeRatesExpanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Exchange Rates (Value of 1 Unit in $baseCurrency)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = if (isExchangeRatesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExchangeRatesExpanded) "Collapse exchange rates" else "Expand exchange rates",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(visible = isExchangeRatesExpanded) {
                        val listCurrencies = remember(baseCurrency) {
                            if (baseCurrency == "USD") {
                                listOf("EUR", "GBP", "CAD", "AUD", "SGD", "JPY", "PHP", "SAR", "BTC")
                            } else {
                                listOf("USD") + listOf("EUR", "GBP", "CAD", "AUD", "SGD", "JPY", "PHP", "SAR", "BTC").filter { it != baseCurrency }
                            }
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            listCurrencies.forEach { curr ->
                                val fullLabel = CURRENCY_NAMES[curr] ?: curr
                                val R_curr = exchangeRatesMap[curr] ?: 1.0
                                val R_base = exchangeRatesMap[baseCurrency] ?: 1.0
                                val currentRateInBase = if (curr == "USD") {
                                    1.0 / R_base
                                } else {
                                    R_curr / R_base
                                }

                                Surface(
                                    onClick = {
                                        if (manualConversionMode) {
                                            editingCurrency = curr
                                            editingRateValue = if (curr == "BTC") {
                                                String.format(Locale.US, "%.2f", currentRateInBase)
                                            } else {
                                                String.format(Locale.US, "%.4f", currentRateInBase)
                                            }
                                        } else {
                                            android.widget.Toast.makeText(context, "Enable Manual Mode to manually edit exchange multipliers!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (manualConversionMode) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = fullLabel,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Tap to override manually",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (curr == "BTC") String.format("%.2f $baseCurrency", currentRateInBase) else String.format("%.4f $baseCurrency", currentRateInBase),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = if (manualConversionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            if (manualConversionMode) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Manual Rate",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
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

        // Section: Security Passcode locks
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Authentication Security",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // PIN toggled status
                    if (appLockPin == null) {
                        SettingsRow(
                            title = "Enable 4-Digit Security PIN",
                            subtitle = "Protect account contents on opening",
                            icon = Icons.Default.Security,
                            onClick = {
                                showPinSetupDialog = true
                            }
                        )
                    } else {
                        SettingsRow(
                            title = "Change Security PIN",
                            subtitle = "Modify your current 4-digit security passcode PIN",
                            icon = Icons.Default.Lock,
                            onClick = {
                                showPinSetupDialog = true
                            }
                        )
                        SettingsRow(
                            title = "Disable PIN Code Lock",
                            subtitle = "Remove passcode lock protection completely",
                            icon = Icons.Default.LockOpen,
                            onClick = {
                                showPinDisableDialog = true
                            }
                        )
                    }

                    if (appLockPin != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Biometric Fingerprint Lock",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Use fingerprint or face recognition on startup",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = useBiometrics,
                                onCheckedChange = { viewModel.setUseBiometrics(it) },
                                modifier = Modifier.testTag("biometrics_lock_switch")
                            )
                        }
                    } else {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Biometric Fingerprint Lock",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = "Requires enabling a security PIN first",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            Switch(
                                checked = false,
                                onCheckedChange = {},
                                enabled = false,
                                modifier = Modifier.testTag("biometrics_lock_switch_disabled")
                            )
                        }
                    }
                }
            }
        }

        // Section: Taxonomy / Category & Bucket management
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Taxonomic Controls",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    SettingsRow(
                        title = "Configure Asset Classes",
                        subtitle = "Modify groupings such as Cash, Real Estate, Equities, Crypto",
                        icon = Icons.Default.Category,
                        onClick = onManageCategories
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingsRow(
                        title = "Configure Liability Classes",
                        subtitle = "Modify debt groupings such as Credit Cards, Loans, Mortgages",
                        icon = Icons.Default.CreditCard,
                        onClick = onManageLiabilityCategories
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingsRow(
                        title = "Configure Portfolio Buckets",
                        subtitle = "Design targeted buckets like Retirement, Emergency, Growth",
                        icon = Icons.Default.Layers,
                        onClick = onManageBuckets
                    )
                }
            }
        }

        // Section: Data backups and ledger maintenance
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Data Portability & Portfolios",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    SettingsRow(
                        title = "Portability (Backup / Restores)",
                        subtitle = "JSON backup snapshots & external CSV transaction spreadsheets",
                        icon = Icons.Default.SyncAlt,
                        onClick = { showPortabilityDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingsRow(
                        title = "Wipe and Reset Ledger",
                        subtitle = "Deletes all transactions, snapshots, and customized structures",
                        icon = Icons.Default.DeleteForever,
                        onClick = { showResetDialog = true },
                        tintColor = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "App Version: Beta 2.1",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    // Modal 1: Base Currency Picker
    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Select Base Currency") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())
                ) {
                    val supportedCurrencies = viewModel.exchangeRates.keys.sorted()
                    supportedCurrencies.forEach { curr ->
                        val isSelected = baseCurrency == curr
                        Surface(
                            onClick = {
                                viewModel.setBaseCurrency(curr)
                                showCurrencyDialog = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = CURRENCY_NAMES[curr] ?: curr,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal 2: Visual Theme Selection
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Visual Themes Combination") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    val themes = listOf(
                        "Default Material Design (Jetpack Standard)" to 0,
                        "Oceanic Breeze Sky accents" to 1,
                        "Forest Emerald Lush Mint tones" to 2
                    )
                    themes.forEach { (name, idx) ->
                        val isSelected = themeIndex == idx
                        Surface(
                            onClick = {
                                viewModel.setThemeIndex(idx)
                                showThemeDialog = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = name,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Modal 3: Secure passcode setup
    if (showPinSetupDialog) {
        var setupStep by remember { mutableStateOf(1) } // 1 = Enter PIN, 2 = Confirm
        var enteredPin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var setupError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            title = { Text(if (setupStep == 1) "Initial Vault PIN" else "Confirm Vault PIN") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (setupStep == 1) "Enter a 4-digit passcode passcode to restrict access to this device." 
                        else "Re-type details to confirm identity passcode similarity.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val currentVal = if (setupStep == 1) enteredPin else confirmPin
                        for (i in 0 until 4) {
                            val activeVal = i < currentVal.length
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        color = if (activeVal) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (activeVal) {
                                    Text(
                                        text = "*",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (setupError != null) {
                        Text(
                            text = setupError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Simulated pad
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val onPadPress: (String) -> Unit = { digit ->
                            setupError = null
                            if (setupStep == 1) {
                                if (enteredPin.length < 4) {
                                    enteredPin += digit
                                    if (enteredPin.length == 4) {
                                        setupStep = 2
                                    }
                                }
                            } else {
                                if (confirmPin.length < 4) {
                                    confirmPin += digit
                                    if (confirmPin.length == 4) {
                                        if (enteredPin == confirmPin) {
                                            viewModel.setAppLockPin(enteredPin)
                                            showPinSetupDialog = false
                                        } else {
                                            setupError = "PIN match failure. Resetting."
                                            confirmPin = ""
                                            enteredPin = ""
                                            setupStep = 1
                                        }
                                    }
                                }
                            }
                        }

                        listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9")
                        ).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { num ->
                                    OutlinedButton(
                                        onClick = { onPadPress(num) },
                                        shape = CircleShape,
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Text(num, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    enteredPin = ""
                                    confirmPin = ""
                                    setupStep = 1
                                },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Text("Clear")
                            }

                            OutlinedButton(
                                onClick = { onPadPress("0") },
                                shape = CircleShape,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Text("0", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }

                            IconButton(
                                onClick = {
                                    if (setupStep == 1 && enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                    if (setupStep == 2 && confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                                },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(Icons.Default.Backspace, contentDescription = null)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPinSetupDialog = false }) {
                    Text("Cancel Setup")
                }
            }
        )
    }

    // Modal 4: Secure PIN disable/change dialog
    if (showPinDisableDialog) {
        var inputPin by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPinDisableDialog = false },
            title = { Text("Disable Passcode Security") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Enter current 4-digit PIN passcode to revoke lock privileges.",
                        textAlign = TextAlign.Center
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (i in 0 until 4) {
                            val filled = i < inputPin.length
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        color = if (filled) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Keypad grid
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val pressKey: (String) -> Unit = { key ->
                            errorMessage = null
                            if (inputPin.length < 4) {
                                val n = inputPin + key
                                inputPin = n
                                if (n.length == 4) {
                                    if (n == appLockPin) {
                                        viewModel.setAppLockPin(null)
                                        viewModel.setUseBiometrics(false)
                                        showPinDisableDialog = false
                                    } else {
                                        errorMessage = "Mismatch current Security PIN"
                                        inputPin = ""
                                    }
                                }
                            }
                        }

                        listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9")
                        ).forEach { r ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                r.forEach { key ->
                                    OutlinedButton(onClick = { pressKey(key) }, modifier = Modifier.size(52.dp), shape = CircleShape) {
                                        Text(key, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { inputPin = "" }, modifier = Modifier.size(52.dp)) {
                                Text("Clear")
                            }
                            OutlinedButton(onClick = { pressKey("0") }, modifier = Modifier.size(52.dp), shape = CircleShape) {
                                Text("0", fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { if (inputPin.isNotEmpty()) inputPin = inputPin.dropLast(1) },
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(Icons.Default.Backspace, contentDescription = null)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPinDisableDialog = false }) {
                    Text("Keep Active")
                }
            }
        )
    }

    // Modal 5: Portability Backups (File Export & Import via System Folder Picker / Clipboard)
    if (showPortabilityDialog) {
        var backingOption by remember { mutableStateOf("EXPORT_JSON") } // EXPORT_JSON, IMPORT_JSON, EXPORT_CSV
        var importJsonText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPortabilityDialog = false },
            title = { Text("Backup Data Portability") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Quick selector row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val opts = listOf(
                            "Export JSON" to "EXPORT_JSON",
                            "Import JSON" to "IMPORT_JSON",
                            "Export CSV" to "EXPORT_CSV"
                        )
                        opts.forEach { (lbl, code) ->
                            val isSel = backingOption == code
                            Surface(
                                onClick = {
                                    backingOption = code
                                    portabilityResultText = null
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = lbl,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (portabilityResultText != null) {
                        Text(
                            text = portabilityResultText ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (portabilityResultText?.contains("Successfully") == true) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }

                    when (backingOption) {
                        "EXPORT_JSON" -> {
                            val jsonString = remember { viewModel.exportDatabaseToJson() }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Export and save your entire database (categories, assets, ledger transactions) AND settings configuration to a JSON backup file in your phone storage (Folder).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        createDocumentLauncher.launch("networth_backup_${System.currentTimeMillis() / 1000}.json")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                    Text("Save Backup to Device (Folder)")
                                }
                                
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                
                                Text(
                                    text = "Alternatively, copy raw backup text snippet:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = jsonString,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Button(
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(jsonString))
                                        portabilityResultText = "Successfully copied JSON snapshot backup code to device memory!"
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors()
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                    Text("Copy Raw Text Code")
                                }
                            }
                        }
                        "IMPORT_JSON" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Restore all your data and settings from a previously saved JSON backup file. This will fully restore your balances, categories, buckets, and settings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        openDocumentLauncher.launch(arrayOf("application/json"))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                    Text("Select Backup File from Folder")
                                }
                                
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                
                                Text(
                                    text = "Alternatively, paste raw system backup JSON code text:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = importJsonText,
                                    onValueChange = { importJsonText = it },
                                    placeholder = { Text("Paste JSON code text...") },
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Button(
                                    onClick = {
                                        if (importJsonText.isBlank()) {
                                            portabilityResultText = "Please paste back-up code first!"
                                        } else {
                                            scope.launch {
                                                val success = viewModel.importDatabaseFromJson(importJsonText)
                                                if (success) {
                                                    importJsonText = ""
                                                    android.widget.Toast.makeText(context, "Import successful", android.widget.Toast.LENGTH_SHORT).show()
                                                    showPortabilityDialog = false
                                                    onImportSuccess()
                                                } else {
                                                    portabilityResultText = "Invalid JSON schema configuration."
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                    Text("Restore from Pasted Code")
                                }
                            }
                        }
                        "EXPORT_CSV" -> {
                            val csvContent = remember { viewModel.exportTransactionsToCsv() }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Export and save a spreadsheet containing your detailed ledger transaction history to a CSV file on your phone memory.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        createCsvLauncher.launch("networth_ledger_${System.currentTimeMillis() / 1000}.csv")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                    Text("Save CSV File to Phone Folder")
                                }
                                
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                
                                Text(
                                    text = "Alternatively, inspect/copy transaction tabular block:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = csvContent,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Button(
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(csvContent))
                                        portabilityResultText = "Successfully copied Transaction CSV ledger sheets to clipboard!"
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors()
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                    Text("Copy Spreadsheet CSV Text")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPortabilityDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Modal 6: Reset Database with Confirmation
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("Reset Vault Ledger?", color = MaterialTheme.colorScheme.error)
                }
            },
            text = {
                Text("Are you absolutely sure? This will run a destructive clear and permanently delete all tracked transactions, registered assets, custom snapshot dates, and portfolios from this system devices storage.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Wipe Database", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (editingCurrency != null) {
        val curr = editingCurrency!!
        AlertDialog(
            onDismissRequest = { editingCurrency = null },
            title = { Text("Edit Manual Rate for $curr") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter currency exchange rate to convert $curr to $baseCurrency (Multiplier: 1 $curr = X $baseCurrency)")
                    OutlinedTextField(
                        value = editingRateValue,
                        onValueChange = { editingRateValue = it },
                        label = { Text("Exchange Rate in $baseCurrency") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("manual_rate_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val rate = editingRateValue.toDoubleOrNull()
                        if (rate != null && rate > 0) {
                            val R_base = exchangeRatesMap[baseCurrency] ?: 1.0
                            if (curr == "USD") {
                                val newBaseRate = 1.0 / rate
                                viewModel.setManualExchangeRate(baseCurrency, newBaseRate)
                            } else {
                                val newCurrRate = rate * R_base
                                viewModel.setManualExchangeRate(curr, newCurrRate)
                            }
                            editingCurrency = null
                        } else {
                            android.widget.Toast.makeText(context, "Please enter a valid rate greater than 0", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("save_manual_rate_btn")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCurrency = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tintColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tintColor
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (tintColor == MaterialTheme.colorScheme.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

data class PieSlice(
    val name: String,
    val valuationUsd: Double,
    val percentage: Double,
    val color: Color
)

data class CurrencyAllocation(
    val currency: String,
    val nativeValue: Double,
    val usdValue: Double,
    val percentage: Double
)

data class SmartAssetClassGroup(
    val name: String,
    val totalValuationUsd: Double,
    val categories: List<com.example.data.model.AssetCategory>,
    val assets: List<com.example.ui.viewmodel.AssetWithMetrics>
)

fun getSmartAssetClass(categoryName: String): String {
    val name = categoryName.lowercase().trim()
    return when {
        name.contains("cash") || name.contains("bank") || name.contains("saving") || 
        name.contains("checking") || name.contains("deposit") || name.contains("wallet") || name.contains("current") -> "Cash"
        
        name.contains("bond") || name.contains("fixed income") || name.contains("treasury") || 
        name.contains("debt") || name.contains("debenture") || name.contains("bill") || name.contains("note") -> "Bond"
        
        name.contains("real estate") || name.contains("property") || name.contains("house") || 
        name.contains("land") || name.contains("reit") || name.contains("home") || name.contains("building") -> "Real Estate"
        
        name.contains("commodity") || name.contains("gold") || name.contains("silver") || 
        name.contains("oil") || name.contains("metal") || name.contains("gas") || name.contains("agriculture") -> "Commodities"
        
        name.contains("stock") || name.contains("equity") || name.contains("share") || 
        name.contains("fund") || name.contains("etf") || name.contains("crypto") || 
        name.contains("bitcoin") || name.contains("ethereum") || name.contains("mutual") || 
        name.contains("investment") || name.contains("brokerage") || name.contains("portfolio") -> "Equities"
        
        else -> "Equities"
    }
}

@Composable
fun PortfolioAllocationView(
    viewModel: NetWorthViewModel,
    transactions: List<com.example.data.model.Transaction>,
    assets: List<com.example.data.model.Asset>,
    categories: List<com.example.data.model.AssetCategory>,
    onBack: () -> Unit
) {
    val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()
    val valuesHidden by viewModel.valuesHidden.collectAsStateWithLifecycle()
    val bucketMetrics by viewModel.bucketMetrics.collectAsStateWithLifecycle()
    val categoryMetrics by viewModel.categoryMetrics.collectAsStateWithLifecycle()
    val assetMetrics by viewModel.assetMetrics.collectAsStateWithLifecycle()
    val excludedBucketIds by viewModel.excludedBucketIds.collectAsStateWithLifecycle()

    val checkedBucketIds = remember(bucketMetrics, excludedBucketIds) {
        bucketMetrics.map { it.bucket?.id ?: -1 }.filter { it !in excludedBucketIds }.toSet()
    }

    var showBucketSettingsDialog by remember { mutableStateOf(false) }
    var selectedAllocationType by remember { mutableStateOf("bucket") } // "bucket" or "class"
    var currencyFilterType by remember { mutableStateOf("portfolio") } // "portfolio" or "cash"

    val checkedMetrics = bucketMetrics.filter { (it.bucket?.id ?: -1) in checkedBucketIds }.sortedByDescending { it.totalValuationUsd }
    val totalCheckedUsd = checkedMetrics.sumOf { it.totalValuationUsd }

    val colors = listOf(
        Color(0xFF3B82F6), // Accent Blue
        Color(0xFF10B981), // Accent Emerald
        Color(0xFFF59E0B), // Accent Amber
        Color(0xFFEC4899), // Accent Pink
        Color(0xFF8B5CF6), // Accent Violet
        Color(0xFF06B6D4), // Accent Cyan
        Color(0xFFEF4444), // Accent Red
        Color(0xFFF43F5E), // Accent Rose
        Color(0xFF14B8A6)  // Accent Teal
    )

    val bucketSlices = checkedMetrics.mapIndexed { index, item ->
        val pct = if (totalCheckedUsd > 0) (item.totalValuationUsd / totalCheckedUsd) * 100.0 else 0.0
        PieSlice(
            name = item.bucket?.name ?: "Unassigned Assets",
            valuationUsd = item.totalValuationUsd,
            percentage = pct,
            color = colors[index % colors.size]
        )
    }

    val activeAssetClasses = categoryMetrics.filter { it.category.isAsset }
    
    // Smartly group categories into unified standard asset classes
    val smartGroups = activeAssetClasses.groupBy { getSmartAssetClass(it.category.name) }
        .map { (className, catList) ->
            val totalUsd = catList.sumOf { it.totalValuationUsd }
            val allAssets = catList.flatMap { it.assets }
                .distinctBy { it.asset.id }
                .sortedByDescending { it.currentValuationUsd }
            val allCategories = catList.map { it.category }
            SmartAssetClassGroup(
                name = className,
                totalValuationUsd = totalUsd,
                categories = allCategories,
                assets = allAssets
            )
        }.sortedByDescending { it.totalValuationUsd }

    val totalAssetClassUsd = smartGroups.sumOf { it.totalValuationUsd }
    val classSlices = smartGroups.mapIndexed { index, item ->
        val pct = if (totalAssetClassUsd > 0) (item.totalValuationUsd / totalAssetClassUsd) * 100.0 else 0.0
        PieSlice(
            name = item.name,
            valuationUsd = item.totalValuationUsd,
            percentage = pct,
            color = colors[index % colors.size]
        )
    }

    val activeSlices = if (selectedAllocationType == "bucket") bucketSlices else classSlices
    val totalActiveUsd = if (selectedAllocationType == "bucket") totalCheckedUsd else totalAssetClassUsd

    // Cash & Portfolio Currency exposure grouping - excluding liabilities!
    val nonLiabilityAssets = assets.filter { !it.isArchived && it.includeInPortfolio && (categories.find { c -> c.id == it.categoryId }?.isAsset ?: true) }
    val cashAssets = nonLiabilityAssets.filter {
        val cat = categories.find { c -> c.id == it.categoryId }
        cat?.name?.equals("Cash", ignoreCase = true) == true
    }

    val currencyGroup = if (currencyFilterType == "portfolio") nonLiabilityAssets else cashAssets
    val currencyGroupMapped = currencyGroup
        .groupBy { it.currency }
        .map { (curr, list) ->
            val totalUsd = list.sumOf { assetItem ->
                val metric = assetMetrics.find { m -> m.asset.id == assetItem.id }
                metric?.currentValuationUsd ?: 0.0
            }
            val totalNative = list.sumOf { assetItem ->
                val metric = assetMetrics.find { m -> m.asset.id == assetItem.id }
                metric?.currentValuationNative ?: 0.0
            }
            CurrencyAllocation(
                currency = curr,
                nativeValue = totalNative,
                usdValue = totalUsd,
                percentage = 0.0
            )
        }

    val totalExposureUsd = currencyGroupMapped.sumOf { it.usdValue }
    val finalCurrencyAllocations = currencyGroupMapped.map {
        it.copy(percentage = if (totalExposureUsd > 0) (it.usdValue / totalExposureUsd) * 100.0 else 0.0)
    }.sortedByDescending { it.percentage }

    // Dialog to choose buckets
    if (showBucketSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showBucketSettingsDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Filter Included Buckets")
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Toggle the buckets below to include or exclude them from the interactive chart and risk metrics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.setExcludedBucketIds(emptySet())
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Select All", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(
                            onClick = {
                                viewModel.setExcludedBucketIds(bucketMetrics.map { it.bucket?.id ?: -1 }.toSet())
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Deselect All", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        bucketMetrics.forEachIndexed { idx, item ->
                            val bId = item.bucket?.id ?: -1
                            val isChecked = bId in checkedBucketIds
                            val sliceColor = colors[idx % colors.size]
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val newExcluded = if (isChecked) {
                                            excludedBucketIds + bId
                                        } else {
                                            excludedBucketIds - bId
                                        }
                                        viewModel.setExcludedBucketIds(newExcluded)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        val newExcluded = if (isChecked) {
                                            excludedBucketIds + bId
                                        } else {
                                            excludedBucketIds - bId
                                        }
                                        viewModel.setExcludedBucketIds(newExcluded)
                                    }
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(sliceColor, CircleShape)
                                )
                                Text(
                                    text = item.bucket?.name ?: "Unassigned Assets",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showBucketSettingsDialog = false }) {
                    Text("Apply")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Dynamic Header Row with Back navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .testTag("allocation_back_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "Portfolio Allocation",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.size(48.dp))
        }

        // Section 1: Interactive Allocation Card (Smart Toggle)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp)
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (selectedAllocationType == "bucket") Icons.Default.Layers else Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = if (selectedAllocationType == "bucket") "Interactive Bucket Allocations" else "Asset Class Allocation",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (selectedAllocationType == "bucket") "Proportions scaled across included buckets" else "Valuation share across individual taxonomic groupings (excluding liabilities)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (selectedAllocationType == "bucket") {
                        IconButton(
                            onClick = { showBucketSettingsDialog = true },
                            modifier = Modifier.testTag("bucket_settings_icon")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Configure Slices",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Smart Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("bucket" to "Buckets", "class" to "Asset Classes").forEach { (type, label) ->
                        val isSelected = selectedAllocationType == type
                        val bgCol = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        val fgCol = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgCol)
                                .clickable { selectedAllocationType = type }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = fgCol
                            )
                        }
                    }
                }

                if (selectedAllocationType == "bucket" && bucketMetrics.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No buckets found. Build some in Taxonomic Preferences first!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (selectedAllocationType == "bucket" && bucketSlices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "No active buckets selected.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { showBucketSettingsDialog = true }) {
                                Text("Choose Buckets")
                            }
                        }
                    }
                } else if (selectedAllocationType == "class" && smartGroups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active asset class data found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Render modern Donut / Pie Chart representation using activeSlices!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AllocationPieChart(
                            slices = activeSlices,
                            modifier = Modifier.size(200.dp)
                        )
                    }

                    // Toggles & legend list
                    Text(
                        text = "Current Allocation Distribution:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedAllocationType == "bucket") {
                            checkedMetrics.forEachIndexed { idx, item ->
                                val bId = item.bucket?.id ?: -1
                                val sliceColor = colors[idx % colors.size]
                                val pct = if (totalCheckedUsd > 0) (item.totalValuationUsd / totalCheckedUsd) * 100.0 else 0.0

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("bucket_row_$bId")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .background(sliceColor, CircleShape)
                                                )
                                                Text(
                                                    text = item.bucket?.name ?: "Unassigned Assets",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = formatPortfolioAmount(
                                                        amountUsd = item.totalValuationUsd,
                                                        baseCurrency = baseCurrency,
                                                        viewModel = viewModel,
                                                        valuesHidden = valuesHidden
                                                    ),
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = sliceColor.copy(alpha = 0.15f),
                                                    modifier = Modifier.padding(start = 4.dp)
                                                ) {
                                                    Text(
                                                        text = String.format("%.1f%%", pct),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Black,
                                                        color = sliceColor,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                                    shape = RoundedCornerShape(2.dp)
                                                )
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(fraction = (pct / 100.0).toFloat().coerceIn(0f, 1f))
                                                    .background(
                                                        color = sliceColor,
                                                        shape = RoundedCornerShape(2.dp)
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            smartGroups.forEachIndexed { idx, item ->
                                val sliceColor = colors[idx % colors.size]
                                val pct = if (totalAssetClassUsd > 0) (item.totalValuationUsd / totalAssetClassUsd) * 100.0 else 0.0

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("class_row_${item.name}")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .background(sliceColor, CircleShape)
                                                )
                                                Text(
                                                    text = item.name,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = formatPortfolioAmount(
                                                        amountUsd = item.totalValuationUsd,
                                                        baseCurrency = baseCurrency,
                                                        viewModel = viewModel,
                                                        valuesHidden = valuesHidden
                                                    ),
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = sliceColor.copy(alpha = 0.15f),
                                                    modifier = Modifier.padding(start = 4.dp)
                                                ) {
                                                    Text(
                                                        text = String.format("%.1f%%", pct),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Black,
                                                        color = sliceColor,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                                    shape = RoundedCornerShape(2.dp)
                                                )
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(fraction = (pct / 100.0).toFloat().coerceIn(0f, 1f))
                                                    .background(
                                                        color = sliceColor,
                                                        shape = RoundedCornerShape(2.dp)
                                                    )
                                            )
                                        }

                                        if (item.categories.isNotEmpty() || item.assets.isNotEmpty()) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                                thickness = 1.dp,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )

                                            val catListText = item.categories.joinToString(", ") { it.name }
                                            if (catListText.isNotEmpty()) {
                                                Text(
                                                    text = "Taxonomic Groups: $catListText",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }

                                            if (item.assets.isNotEmpty()) {
                                                Column(
                                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = "Assets:",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    item.assets.forEach { assetMetric ->
                                                        val assetPct = if (totalAssetClassUsd > 0) (assetMetric.currentValuationUsd / totalAssetClassUsd) * 100.0 else 0.0
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                modifier = Modifier.weight(1f)
                                                            ) {
                                                                Text(
                                                                    text = "•",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                                Text(
                                                                    text = assetMetric.asset.name,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurface,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Text(
                                                                    text = formatPortfolioAmount(
                                                                        amountUsd = assetMetric.currentValuationUsd,
                                                                        baseCurrency = baseCurrency,
                                                                        viewModel = viewModel,
                                                                        valuesHidden = valuesHidden
                                                                    ),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                                Text(
                                                                    text = String.format("(%.1f%%)", assetPct),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
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
        }

        // Section 1B: Target Allocation & Portfolio Rebalancing Tool
        if (activeSlices.isNotEmpty()) {
            val bucketTargets by viewModel.bucketTargets.collectAsStateWithLifecycle()
            val rebalancingFrequency by viewModel.rebalancingFrequency.collectAsStateWithLifecycle()
            val lastRebalancedTimestamp by viewModel.lastRebalancedTimestamp.collectAsStateWithLifecycle()

            val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()
            val valuesHidden by viewModel.valuesHidden.collectAsStateWithLifecycle()

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Balance,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Target Allocation & Rebalancing Hub",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Maintain your target weights and view action recommendations",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Settings: Rebalancing Frequency & Mark as Rebalanced
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1.2f)) {
                            Text(
                                text = "Rebalance Schedule",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            // Frequency Selector Dropdown
                            var showFreqMenu by remember { mutableStateOf(false) }
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { showFreqMenu = true }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = rebalancingFrequency,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showFreqMenu,
                                    onDismissRequest = { showFreqMenu = false }
                                ) {
                                    listOf("Monthly", "Quarterly", "Semi-Annually", "Once a year").forEach { freq ->
                                        DropdownMenuItem(
                                            text = { Text(freq, style = MaterialTheme.typography.bodySmall) },
                                            onClick = {
                                                showFreqMenu = false
                                                viewModel.saveRebalancingFrequency(freq)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            val formattedDate = remember(lastRebalancedTimestamp) {
                                if (lastRebalancedTimestamp > 0) {
                                    java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(lastRebalancedTimestamp))
                                } else {
                                    "Never Rebalanced"
                                }
                            }
                            Text(
                                text = "Last Rebalanced",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Button(
                                onClick = { viewModel.markAsRebalanced() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Mark Done", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                            }
                        }
                    }

                    // Preset buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                // 3-Bucket Strategy preset: 15% cash, 25% bonds, 60% equities
                                val targets = mutableMapOf<Int, Double>()
                                if (checkedMetrics.isNotEmpty()) {
                                    checkedMetrics.forEachIndexed { idx, item ->
                                        val bId = item.bucket?.id ?: -1
                                        val name = (item.bucket?.name ?: "").lowercase()
                                        val value = when {
                                            name.contains("cash") || name.contains("1") -> 15.0
                                            name.contains("bond") || name.contains("2") || name.contains("fixed") -> 25.0
                                            name.contains("equity") || name.contains("stock") || name.contains("3") || name.contains("long") -> 60.0
                                            else -> when (idx) {
                                                0 -> 15.0
                                                1 -> 25.0
                                                2 -> 60.0
                                                else -> 0.0
                                            }
                                        }
                                        targets[bId] = value
                                    }
                                }
                                viewModel.saveAllBucketTargets(targets)
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text("3-Bucket Preset (15/25/60)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val targets = mutableMapOf<Int, Double>()
                                if (checkedMetrics.isNotEmpty()) {
                                    val equalPct = 100.0 / checkedMetrics.size
                                    checkedMetrics.forEach { item ->
                                        targets[item.bucket?.id ?: -1] = Math.round(equalPct).toDouble()
                                    }
                                }
                                viewModel.saveAllBucketTargets(targets)
                            },
                            modifier = Modifier.weight(0.8f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text("Equal Ratios", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Target sum checker
                    val targetsSum = checkedMetrics.sumOf { bucketTargets[it.bucket?.id ?: -1] ?: 0.0 }
                    val isTargetsValid = Math.abs(targetsSum - 100.0) < 0.1

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isTargetsValid) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Targets Allocation Sum:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isTargetsValid) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                        Text(
                            text = "${targetsSum.toInt()}% / 100%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Black,
                            color = if (isTargetsValid) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }

                    // List of Buckets with Steppers and Drift analysis
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        checkedMetrics.forEachIndexed { index, item ->
                            val bId = item.bucket?.id ?: -1
                            val bName = item.bucket?.name ?: "Unassigned Assets"
                            val actualPct = if (totalCheckedUsd > 0) (item.totalValuationUsd / totalCheckedUsd) * 100.0 else 0.0
                            val targetPct = bucketTargets[bId] ?: 0.0
                            val driftPct = actualPct - targetPct
                            val color = colors[index % colors.size]

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(color, CircleShape)
                                        )
                                        Text(
                                            text = bName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Stepper Input for Target Percentage
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Target:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        IconButton(
                                            onClick = {
                                                val newTarget = (targetPct - 5.0).coerceAtLeast(0.0)
                                                viewModel.saveBucketTarget(bId, newTarget)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Remove,
                                                contentDescription = "Decrease target",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = "${targetPct.toInt()}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.width(36.dp),
                                            textAlign = TextAlign.Center
                                        )
                                        IconButton(
                                            onClick = {
                                                val newTarget = (targetPct + 5.0).coerceAtMost(100.0)
                                                viewModel.saveBucketTarget(bId, newTarget)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Increase target",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Progress Comparison Bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                ) {
                                    // Actual Fill
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((actualPct / 100.0).toFloat().coerceIn(0f, 1f))
                                            .height(8.dp)
                                            .background(color, RoundedCornerShape(4.dp))
                                    )
                                    // Target indicator marker line
                                    if (targetPct > 0) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth((targetPct / 100.0).toFloat().coerceIn(0f, 1f))
                                                .width(3.dp)
                                                .height(8.dp)
                                                .background(MaterialTheme.colorScheme.onSurface)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Stats: Actual, Drift and Trade suggestion
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Actual: ${String.format("%.1f%%", actualPct)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    val (driftText, driftCol) = when {
                                        Math.abs(driftPct) < 0.5 -> "Balanced" to Color(0xFF4CAF50)
                                        driftPct > 0 -> "+${String.format("%.1f%%", driftPct)} Over" to Color(0xFFEF5350)
                                        else -> "${String.format("%.1f%%", driftPct)} Under" to Color(0xFF29B6F6)
                                    }

                                    Text(
                                        text = "Drift: $driftText",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = driftCol
                                    )
                                }

                                // Required Action Value
                                if (Math.abs(driftPct) >= 0.5 && isTargetsValid) {
                                    val driftValUsd = (driftPct / 100.0) * totalCheckedUsd
                                    val driftValBase = viewModel.convertCurrency(driftValUsd, "USD", baseCurrency, viewModel.exchangeRates)
                                    val actionText = if (driftPct > 0) {
                                        "Required: Move out ${formatAssetCurrency(Math.abs(driftValBase), baseCurrency, valuesHidden = valuesHidden)}"
                                    } else {
                                        "Required: Deposit in ${formatAssetCurrency(Math.abs(driftValBase), baseCurrency, valuesHidden = valuesHidden)}"
                                    }
                                    Text(
                                        text = actionText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                        color = if (driftPct > 0) Color(0xFFEF5350) else Color(0xFF29B6F6),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Rebalancing Alerts & Actions Recommendations
                    if (isTargetsValid) {
                        val limitDays = when (rebalancingFrequency) {
                            "Monthly" -> 30
                            "Quarterly" -> 90
                            "Semi-Annually" -> 180
                            else -> 365 // "Once a year"
                        }
                        val nowMs = System.currentTimeMillis()
                        val daysSinceLast = if (lastRebalancedTimestamp > 0) {
                            (nowMs - lastRebalancedTimestamp) / (24 * 60 * 60 * 1000)
                        } else {
                            -1
                        }

                        val isTimeOverdue = daysSinceLast >= limitDays || lastRebalancedTimestamp == 0L
                        val maxDrift = checkedMetrics.map { item ->
                            val bId = item.bucket?.id ?: -1
                            val actualPct = if (totalCheckedUsd > 0) (item.totalValuationUsd / totalCheckedUsd) * 100.0 else 0.0
                            val targetPct = bucketTargets[bId] ?: 0.0
                            Math.abs(actualPct - targetPct)
                        }.maxOrNull() ?: 0.0

                        val isDriftOverLimit = maxDrift >= 5.0

                        if (isTimeOverdue || isDriftOverLimit) {
                            val alertTitle = if (isTimeOverdue) "Rebalancing Overdue ⏳" else "Drift Alert (>5%) ⚖️"
                            val alertDesc = if (isTimeOverdue && lastRebalancedTimestamp == 0L) {
                                "Perform your baseline initial rebalancing to match targets."
                            } else if (isTimeOverdue) {
                                "Your $rebalancingFrequency schedule is overdue ($daysSinceLast days elapsed)."
                            } else {
                                "One or more of your buckets has drifted over the 5% warning threshold."
                            }

                            // Generate step-by-step recommendation list
                            val sells = checkedMetrics.mapNotNull { item ->
                                val bId = item.bucket?.id ?: -1
                                val name = item.bucket?.name ?: "Unassigned"
                                val actualPct = if (totalCheckedUsd > 0) (item.totalValuationUsd / totalCheckedUsd) * 100.0 else 0.0
                                val targetPct = bucketTargets[bId] ?: 0.0
                                val diffPct = actualPct - targetPct
                                if (diffPct >= 1.0) {
                                    val usdVal = (diffPct / 100.0) * totalCheckedUsd
                                    val baseVal = viewModel.convertCurrency(usdVal, "USD", baseCurrency, viewModel.exchangeRates)
                                    name to baseVal
                                } else null
                            }

                            val buys = checkedMetrics.mapNotNull { item ->
                                val bId = item.bucket?.id ?: -1
                                val name = item.bucket?.name ?: "Unassigned"
                                val actualPct = if (totalCheckedUsd > 0) (item.totalValuationUsd / totalCheckedUsd) * 100.0 else 0.0
                                val targetPct = bucketTargets[bId] ?: 0.0
                                val diffPct = targetPct - actualPct // underallocation amount
                                if (diffPct >= 1.0) {
                                    val usdVal = (diffPct / 100.0) * totalCheckedUsd
                                    val baseVal = viewModel.convertCurrency(usdVal, "USD", baseCurrency, viewModel.exchangeRates)
                                    name to baseVal
                                } else null
                            }

                            val adviceList = mutableListOf<String>()
                            if (sells.isNotEmpty() && buys.isNotEmpty()) {
                                sells.forEach { (sellName, sellAmt) ->
                                    val formattedAmt = formatAssetCurrency(sellAmt, baseCurrency, valuesHidden = valuesHidden)
                                    adviceList.add("• Withdraw/sell $formattedAmt from $sellName")
                                }
                                buys.forEach { (buyName, buyAmt) ->
                                    val formattedAmt = formatAssetCurrency(buyAmt, baseCurrency, valuesHidden = valuesHidden)
                                    adviceList.add("• Deposit/buy $formattedAmt into $buyName")
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = alertTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                Text(
                                    text = alertDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (adviceList.isNotEmpty()) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                                    Text(
                                        text = "Recommended Actions:",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        adviceList.forEach { adv ->
                                            Text(
                                                text = adv,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            // Trigger dashboard notification bell alert
                            val notificationMsg = "Rebalancing is recommended! $alertDesc"
                            LaunchedEffect(notificationMsg) {
                                viewModel.triggerRebalancingNotification(notificationMsg)
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Cash & Currency Allocations
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp)
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
                        imageVector = Icons.Default.MonetizationOn,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = if (currencyFilterType == "portfolio") "Portfolio Currency Allocations" else "Cash Currency Allocations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (currencyFilterType == "portfolio") "Overall portfolio currency diversifications & exposure representation" else "Global cash currency diversifications & exposure representation",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Currency Smart Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("portfolio" to "Overall Portfolio", "cash" to "Cash Only").forEach { (type, label) ->
                        val isSelected = currencyFilterType == type
                        val bgCol = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        val fgCol = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgCol)
                                .clickable { currencyFilterType = type }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = fgCol
                            )
                        }
                    }
                }

                if (finalCurrencyAllocations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No assets are registered yet to calculate currencies.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        finalCurrencyAllocations.forEachIndexed { index, item ->
                            val currentBarColor = colors[(index + 4) % colors.size]
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    color = currentBarColor.copy(alpha = 0.2f),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = item.currency,
                                                fontWeight = FontWeight.Black,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = currentBarColor
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = CURRENCY_NAMES[item.currency] ?: item.currency,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Text(
                                                    text = "(${item.currency})",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }

                                            Text(
                                                text = "Native Holdings: " + if (valuesHidden) "••••" else String.format("%,.2f %s", item.nativeValue, item.currency),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.wrapContentWidth()
                                    ) {
                                        Text(
                                            text = formatPortfolioAmount(
                                                amountUsd = item.usdValue,
                                                baseCurrency = baseCurrency,
                                                viewModel = viewModel,
                                                valuesHidden = valuesHidden
                                            ),
                                            fontWeight = FontWeight.Black,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Clip
                                        )
                                        Text(
                                            text = String.format("%.1f%% Float", item.percentage),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
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

@Composable
fun AllocationPieChart(
    slices: List<PieSlice>,
    modifier: Modifier = Modifier
) {
    if (slices.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "No active slices",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val textColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(120.dp)) {
            var startAngle = -90f
            val strokeWidthPx = 14.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f

            slices.forEach { slice ->
                val sweep = (slice.percentage.toFloat() / 100f) * 360f
                if (sweep > 0f) {
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = strokeWidthPx,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                    
                    // Only draw percentage line pointing outwards if it is significant (e.g., >= 3.5%) to avoid crowding
                    if (slice.percentage >= 3.5) {
                        val midAngle = startAngle + sweep / 2f
                        val rad = Math.toRadians(midAngle.toDouble())
                        
                        // Originating point: slightly outer part of the donut stroke
                        val r_start = size.width / 2f + strokeWidthPx / 4f
                        val xStart = (cx + r_start * cos(rad)).toFloat()
                        val yStart = (cy + r_start * sin(rad)).toFloat()
                        
                        // End point: angled outward extension
                        val r_end = r_start + 14.dp.toPx()
                        val xEnd = (cx + r_end * cos(rad)).toFloat()
                        val yEnd = (cy + r_end * sin(rad)).toFloat()
                        
                        // Horizontal extension
                        val isLeft = xEnd < cx
                        val horizLength = 8.dp.toPx()
                        val xText = if (isLeft) xEnd - horizLength else xEnd + horizLength
                        
                        // Draw angled connection line
                        drawLine(
                            color = lineColor,
                            start = androidx.compose.ui.geometry.Offset(xStart, yStart),
                            end = androidx.compose.ui.geometry.Offset(xEnd, yEnd),
                            strokeWidth = 1.dp.toPx()
                        )
                        
                        // Draw small horizontal baseline line
                        drawLine(
                            color = lineColor,
                            start = androidx.compose.ui.geometry.Offset(xEnd, yEnd),
                            end = androidx.compose.ui.geometry.Offset(xText, yEnd),
                            strokeWidth = 1.dp.toPx()
                        )
                        
                        // Determine text alignment and anchor offsets
                        val textAnchorX = if (isLeft) xText - 4.dp.toPx() else xText + 4.dp.toPx()
                        val textAnchorY = yEnd + 3.5.dp.toPx() // center vertically with line
                        val textAlign = if (isLeft) android.graphics.Paint.Align.RIGHT else android.graphics.Paint.Align.LEFT
                        
                        val displayName = if (slice.name.length > 12) slice.name.take(10) + ".." else slice.name
                        val labelText = "$displayName (${String.format("%.1f%%", slice.percentage)})"
                        
                        drawContext.canvas.nativeCanvas.drawText(
                            labelText,
                            textAnchorX,
                            textAnchorY,
                            android.graphics.Paint().apply {
                                color = textColor.toArgb()
                                textSize = 8.sp.toPx()
                                this.textAlign = textAlign
                                isAntiAlias = true
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                            }
                        )
                    }
                    
                    startAngle += sweep
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Total Active",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${slices.size} Slices",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun WalletTab(
    viewModel: NetWorthViewModel,
    transactions: List<Transaction>,
    assets: List<Asset>,
    categories: List<AssetCategory>,
    onLaunchWalletMode: (Asset) -> Unit
) {
    val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()
    val valuesHidden by viewModel.valuesHidden.collectAsStateWithLifecycle()
    val assetMetrics by viewModel.assetMetrics.collectAsStateWithLifecycle()
    val spendingWalletIds by viewModel.spendingWalletIds.collectAsStateWithLifecycle()
    val walletAliases by viewModel.walletAliases.collectAsStateWithLifecycle()
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

    val cashMetrics = remember(assetMetrics, cashCategoryIds, spendingWalletIds) {
        if (spendingWalletIds.isNotEmpty()) {
            assetMetrics.filter {
                it.asset.id in spendingWalletIds && !it.asset.isArchived
            }
        } else {
            assetMetrics.filter {
                it.asset.categoryId in cashCategoryIds && !it.asset.isArchived
            }
        }
    }

    val totalCashUsd = remember(cashMetrics) {
        cashMetrics.sumOf { it.currentValuationUsd }
    }

    val totalCashFormatted = remember(totalCashUsd, baseCurrency) {
        formatPortfolioAmount(totalCashUsd, baseCurrency, viewModel, valuesHidden = valuesHidden)
    }

    val currentMonthTransactions = remember(transactions) {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.timeInMillis

        transactions.filter { tx ->
            tx.timestamp >= startOfMonth && (tx.type == "WITHDRAWAL" || tx.type == "INCOME")
        }.sortedByDescending { it.timestamp }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("wallet_tab_column"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wallet_balance_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    Text(
                        text = "Available Cash / Total Wallet Balance",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = totalCashFormatted,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Black
                    )

                    if (cashMetrics.isEmpty()) {
                        val localScope = rememberCoroutineScope()
                        Text(
                            text = "No cash assets found. Go to Dashboard and add cash assets (e.g., Cash / Checking Accounts) to see details here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Button(
                            onClick = {
                                localScope.launch {
                                    var cashCat = categories.find {
                                        it.isAsset && (
                                            it.name.contains("cash", ignoreCase = true) ||
                                            it.name.contains("bank", ignoreCase = true) ||
                                            it.name.contains("wallet", ignoreCase = true) ||
                                            it.name.contains("liquid", ignoreCase = true)
                                        )
                                    }
                                    val catId = if (cashCat == null) {
                                        viewModel.repository.insertCategory(com.example.data.model.AssetCategory(name = "Cash & Bank", isAsset = true)).toInt()
                                    } else {
                                        cashCat.id
                                    }
                                    val newAssetId = viewModel.repository.insertAsset(
                                        com.example.data.model.Asset(
                                            name = "Main Wallet",
                                            currency = baseCurrency,
                                            categoryId = catId
                                        )
                                    ).toInt()
                                    viewModel.repository.insertTransaction(
                                        com.example.data.model.Transaction(
                                            assetId = newAssetId,
                                            type = "UPDATE",
                                            amount = 0.0,
                                            timestamp = System.currentTimeMillis(),
                                            notes = "Setup Main Wallet"
                                        )
                                    )
                                    viewModel.saveSpendingWalletIds(listOf(newAssetId))
                                    viewModel.setDefaultSpendingWalletId(newAssetId)
                                    onLaunchWalletMode(
                                        com.example.data.model.Asset(
                                            id = newAssetId,
                                            name = "Main Wallet",
                                            currency = baseCurrency,
                                            categoryId = catId
                                        )
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCard,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create & Open My Wallet App")
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                        ) {
                            cashMetrics.forEach { m ->
                                val assetValFormatted = formatAssetCurrency(
                                    amount = m.currentValuationNative,
                                    currencyCode = m.asset.currency,
                                    valuesHidden = valuesHidden
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .clickable { onLaunchWalletMode(m.asset) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        WalletIconView(
                                            iconKey = walletIcons[m.asset.id],
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "${walletAliases[m.asset.id] ?: m.asset.name}: $assetValFormatted",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (cashMetrics.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("spending_wallet_selector_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Active Spending Wallets",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tap any wallet below to activate spending tracker",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        cashMetrics.forEach { m ->
                            val assetValFormatted = formatAssetCurrency(
                                amount = m.currentValuationNative,
                                currencyCode = m.asset.currency,
                                valuesHidden = valuesHidden
                            )
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onLaunchWalletMode(m.asset) }
                                    .testTag("wallet_selector_${m.asset.id}"),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                border = BorderStroke(1.1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {

                                        Column {
                                            val mAlias = walletAliases[m.asset.id]
                                            Text(
                                                text = mAlias ?: m.asset.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (mAlias != null) {
                                                Text(
                                                    text = m.asset.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                            val categoryName = categories.find { it.id == m.asset.categoryId }?.name ?: "Cash Class"
                                            Text(
                                                text = "Category · $categoryName",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = assetValFormatted,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        WalletIconView(
                                            iconKey = walletIcons[m.asset.id],
                                            modifier = Modifier.size(32.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "Activate spending wallet",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "This Month's Wallet Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${currentMonthTransactions.size} rows",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (currentMonthTransactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Feed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No withdrawals or income this month",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Record dynamic expense changes or salaries directly using the [+] action button below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(currentMonthTransactions) { tx ->
                val associatedAsset = assets.find { it.id == tx.assetId }
                val classCategoryText = associatedAsset?.let { a ->
                    categories.find { it.id == a.categoryId }?.name
                } ?: "Cash Class"

                val dateFormat = SimpleDateFormat("MMM dd, yyyy · hh:mma", Locale.getDefault())
                val formattedTime = dateFormat.format(Date(tx.timestamp))

                val txAmountFormatted = formatAssetCurrency(
                    amount = tx.amount,
                    currencyCode = associatedAsset?.currency ?: "USD",
                    valuesHidden = valuesHidden
                )

                val txTypeLabel = if (tx.type == "WITHDRAWAL") "Withdrawal" else "Income"
                val txTypeColor = if (tx.type == "WITHDRAWAL") MaterialTheme.colorScheme.error else Color(0xFF10B981)
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wallet_transaction_item"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            color = txTypeColor.copy(alpha = 0.1f),
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (tx.type == "WITHDRAWAL") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = txTypeColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = classCategoryText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text(
                                    text = "(${associatedAsset?.name ?: "Unknown"})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = (if (tx.type == "WITHDRAWAL") "-" else "+") + txAmountFormatted,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = txTypeColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val displayNote = remember(tx.notes) {
                                if (tx.notes?.contains("|") == true) {
                                    val parts = tx.notes.split("|")
                                    if (parts.size > 1 && parts[1].trim().isNotEmpty()) {
                                        parts[1].trim()
                                    } else {
                                        tx.notes.replace("|", "").trim()
                                    }
                                } else {
                                    tx.notes ?: "No additional note."
                                }
                            }
                            Text(
                                text = displayNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = txTypeColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = txTypeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = txTypeColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

@Composable
fun AppSettingsDialog(
    viewModel: NetWorthViewModel,
    onDismiss: () -> Unit,
    onManageCategories: () -> Unit,
    onManageLiabilityCategories: () -> Unit,
    onManageBuckets: () -> Unit
) {
    val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()
    val themeIndex by viewModel.themeIndex.collectAsStateWithLifecycle()
    val appLockPin by viewModel.appLockPin.collectAsStateWithLifecycle()

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 700.dp)
                .fillMaxHeight(0.9f)
                .padding(8.dp),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "App Settings Center",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Settings")
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    SettingsTab(
                        viewModel = viewModel,
                        baseCurrency = baseCurrency,
                        themeIndex = themeIndex,
                        appLockPin = appLockPin,
                        onManageCategories = {
                            onDismiss()
                            onManageCategories()
                        },
                        onManageLiabilityCategories = {
                            onDismiss()
                            onManageLiabilityCategories()
                        },
                        onManageBuckets = {
                            onDismiss()
                            onManageBuckets()
                        },
                        onImportSuccess = {
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DatabaseBackupDialog(
    viewModel: NetWorthViewModel,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    var backingOption by remember { mutableStateOf("EXPORT_JSON") } // EXPORT_JSON, IMPORT_JSON, EXPORT_CSV
    var importJsonText by remember { mutableStateOf("") }
    var portabilityResultText by remember { mutableStateOf<String?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val jsonString = viewModel.exportDatabaseToJson()
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(jsonString.toByteArray())
                }
                portabilityResultText = "Successfully exported database & settings (.json) to phone memory!"
            } catch (e: Exception) {
                portabilityResultText = "Failed to export JSON file: ${e.message}"
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (content.isNullOrBlank()) {
                    portabilityResultText = "Selected backup file (.json) is empty."
                } else {
                    scope.launch {
                        val success = viewModel.importDatabaseFromJson(content)
                        if (success) {
                            android.widget.Toast.makeText(context, "Import successful", android.widget.Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            portabilityResultText = "Failed to restore backup: Invalid JSON schema format."
                        }
                    }
                }
            } catch (e: Exception) {
                portabilityResultText = "Error reading backup file: ${e.message}"
            }
        }
    }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                val csvContent = viewModel.exportTransactionsToCsv()
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(csvContent.toByteArray())
                }
                portabilityResultText = "Successfully exported ledger logs (.csv) to phone memory!"
            } catch (e: Exception) {
                portabilityResultText = "Failed to export CSV file: ${e.message}"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Backup & Portability")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val opts = listOf(
                        "Export JSON" to "EXPORT_JSON",
                        "Import JSON" to "IMPORT_JSON",
                        "Export CSV" to "EXPORT_CSV"
                    )
                    opts.forEach { (lbl, code) ->
                        val isSel = backingOption == code
                        Surface(
                            onClick = {
                                backingOption = code
                                portabilityResultText = null
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = lbl,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (portabilityResultText != null) {
                    Text(
                        text = portabilityResultText ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (portabilityResultText?.contains("Successfully") == true) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                when (backingOption) {
                    "EXPORT_JSON" -> {
                        val jsonString = remember { viewModel.exportDatabaseToJson() }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Export and save your entire database (categories, assets, ledger transactions) AND settings configuration to a JSON backup file in your phone storage (Folder).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    createDocumentLauncher.launch("networth_backup_${System.currentTimeMillis() / 1000}.json")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                Text("Save Backup to Device (Folder)")
                            }
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            
                            Text(
                                text = "Alternatively, copy raw backup text snippet:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = jsonString,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                textStyle = MaterialTheme.typography.bodySmall,
                                shape = RoundedCornerShape(8.dp)
                            )
                            Button(
                                onClick = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(jsonString))
                                    portabilityResultText = "Successfully copied JSON snapshot backup code to device memory!"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                Text("Copy Raw Text Code")
                            }
                        }
                    }
                    "IMPORT_JSON" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Restore all your data and settings from a previously saved JSON backup file. This will fully restore your balances, categories, buckets, and settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    openDocumentLauncher.launch(arrayOf("application/json"))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                Text("Select Backup File from Folder")
                            }
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            
                            Text(
                                text = "Alternatively, paste raw system backup JSON code text:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = importJsonText,
                                onValueChange = { importJsonText = it },
                                placeholder = { Text("Paste JSON code text...") },
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                textStyle = MaterialTheme.typography.bodySmall,
                                shape = RoundedCornerShape(8.dp)
                            )
                            Button(
                                onClick = {
                                    if (importJsonText.isBlank()) {
                                        portabilityResultText = "Please paste back-up code first!"
                                    } else {
                                        scope.launch {
                                            val success = viewModel.importDatabaseFromJson(importJsonText)
                                            if (success) {
                                                importJsonText = ""
                                                android.widget.Toast.makeText(context, "Import successful", android.widget.Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            } else {
                                                portabilityResultText = "Invalid JSON schema configuration."
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                Text("Restore from Pasted Code")
                            }
                        }
                    }
                    "EXPORT_CSV" -> {
                        val csvContent = remember { viewModel.exportTransactionsToCsv() }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Export and save a spreadsheet containing your detailed ledger transaction history to a CSV file on your phone memory.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    createCsvLauncher.launch("networth_ledger_${System.currentTimeMillis() / 1000}.csv")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                Text("Save CSV File to Phone Folder")
                            }
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            
                            Text(
                                text = "Alternatively, inspect/copy transaction tabular block:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = csvContent,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                textStyle = MaterialTheme.typography.bodySmall,
                                shape = RoundedCornerShape(8.dp)
                            )
                            Button(
                                onClick = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(csvContent))
                                    portabilityResultText = "Successfully copied Transaction CSV ledger sheets to clipboard!"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                Text("Copy Spreadsheet CSV Text")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(text = "About Net Worth")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "A modern, offline-first personal portfolio tracker designed to calculate real-time valuations, currency spreads, active project goals, and retirement readiness forecasts.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Features:\n• Multi-currency rates sync\n• Active Wallet & Logger\n• Tactical Retirement Buckets\n• Pin Passcode Lock",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Developer: DashJay\nContact: dashshallburn@gmail.com\nVersion: 2.1 (Beta 2.1)\nAI Studio Edition",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun FirebaseSyncSection(
    viewModel: com.example.ui.viewmodel.NetWorthViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val isSignedIn by viewModel.isDriveSignedIn.collectAsStateWithLifecycle()
    val userEmail by viewModel.driveEmail.collectAsStateWithLifecycle()

    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    val signInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                viewModel.updateDriveState()
                statusMessage = "Signed in successfully!"
            } catch (e: Exception) {
                statusMessage = "Google Sign-In failed: ${e.localizedMessage}"
            }
        } else {
            statusMessage = "Sign-In canceled or failed."
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Google Drive Sync (appDataFolder)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (statusMessage.isNotEmpty()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (statusMessage.contains("failed", true) || statusMessage.contains("error", true)) 
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (!isSignedIn) {
                Text(
                    text = "Sync your personal finance data securely across all devices with absolutely zero server costs by using your private Google Drive appDataFolder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)
                    )
                } else {
                    Button(
                        onClick = {
                            statusMessage = ""
                            try {
                                val signInIntent = viewModel.syncManager.getSignInIntent()
                                signInLauncher.launch(signInIntent)
                            } catch (e: Exception) {
                                statusMessage = "Failed to launch Google Sign-In: ${e.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sign In with Google Account")
                    }
                }
            } else {
                Text(
                    text = "Signed in as: $userEmail",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "AppDataFolder: networth_backup.json",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val isAutoSyncEnabled by viewModel.autoSyncDrive.collectAsStateWithLifecycle()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automatic Cloud Sync",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Sync automatically on app start and when changes are made",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isAutoSyncEnabled,
                        onCheckedChange = { viewModel.setAutoSyncDrive(it) }
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Button(
                            onClick = {
                                isLoading = true
                                statusMessage = ""
                                scope.launch {
                                    val result = viewModel.syncManager.uploadLocalToCloud()
                                    isLoading = false
                                    if (result.isSuccess) {
                                        statusMessage = "Upload successful! Data synchronized to Google Drive."
                                    } else {
                                        statusMessage = "Upload failed: ${result.exceptionOrNull()?.message}"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Upload Local to Drive (Last-Write-Wins)")
                        }

                        Button(
                            onClick = {
                                isLoading = true
                                statusMessage = ""
                                scope.launch {
                                    val result = viewModel.syncManager.downloadCloudToLocal()
                                    isLoading = false
                                    if (result.isSuccess) {
                                        viewModel.refreshAllSettingsFlows()
                                        statusMessage = "Download successful! Local database fully synchronized."
                                    } else {
                                        statusMessage = "Download failed: ${result.exceptionOrNull()?.message}"
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Download Drive to Local (Replace Local)")
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = {
                                val client = GoogleSignIn.getClient(
                                    context,
                                    GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                                )
                                client.signOut().addOnCompleteListener {
                                    viewModel.updateDriveState()
                                    statusMessage = "Signed out successfully."
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Sign Out", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}


