package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import com.example.data.model.Asset
import com.example.data.model.AssetCategory
import com.example.data.model.Transaction
import com.example.ui.viewmodel.AssetWithMetrics
import com.example.ui.viewmodel.NetWorthViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateManualHoldingsScreen(
    viewModel: NetWorthViewModel,
    assets: List<Asset>,
    categories: List<AssetCategory>,
    transactions: List<Transaction>,
    assetMetrics: List<AssetWithMetrics>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val startOfMonth = remember { getStartOfMonthTimestamp() }

    // Map each Category ID to whether it is expanded
    val expandedStates = remember { mutableStateMapOf<Int, Boolean>() }

    // Hold current text inputs for each asset
    val textValues = remember { mutableStateMapOf<Int, String>() }

    // Create list of active categories with their active assets
    val activeAssets = remember(assets) { assets.filter { !it.isArchived } }
    val categoriesWithActiveAssets = remember(categories, activeAssets) {
        categories.map { category ->
            val matchingAssets = activeAssets.filter { it.categoryId == category.id }
            category to matchingAssets
        }.filter { it.second.isNotEmpty() }
    }

    var categoryItems by remember(categoriesWithActiveAssets) {
        mutableStateOf(categoriesWithActiveAssets)
    }

    // Populate or sync missing elements in text inputs maps on load / change
    LaunchedEffect(categoriesWithActiveAssets, assetMetrics, transactions) {
        categoriesWithActiveAssets.forEach { (_, matchingAssets) ->
            matchingAssets.forEach { asset ->
                if (!textValues.containsKey(asset.id)) {
                    val metric = assetMetrics.find { it.asset.id == asset.id }
                    val currentVal = metric?.currentValuationNative ?: getFallbackAssetValuation(asset.id, transactions)
                    textValues[asset.id] = if (currentVal == currentVal.toInt().toDouble()) {
                        currentVal.toInt().toString()
                    } else {
                        currentVal.toString()
                    }
                }
            }
        }
    }

    // Categories are collapsed by default so they can be expanded when selected
    LaunchedEffect(categoriesWithActiveAssets) {
        categoriesWithActiveAssets.forEach { (cat, _) ->
            if (!expandedStates.containsKey(cat.id)) {
                expandedStates[cat.id] = false
            }
        }
    }

    val totalPendingCount = activeAssets.count { asset ->
        transactions.none { tx ->
            tx.assetId == asset.id && tx.type == "UPDATE" && tx.timestamp >= startOfMonth
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Update manual holdings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            onBack()
                        },
                        modifier = Modifier.testTag("update_holdings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back to Net Worth Dashboard",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        BadgedBox(
                            badge = {
                                if (totalPendingCount > 0) {
                                    Badge(
                                        containerColor = Color(0xFFEF4444)
                                    ) {
                                        Text(text = "$totalPendingCount")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Holdings Status Alarm Notification",
                                tint = if (totalPendingCount > 0) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // General banner containing global "MARK ALL UP TO DATE" click macro
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (totalPendingCount == 0) "All portfolio up to date!" else "Monthly Update Due",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (totalPendingCount == 0) 
                                "All assets verified for ${getCurrentMonthName()} ${getCurrentYear()}." 
                                else "You have $totalPendingCount asset${if (totalPendingCount == 1) "" else "s"} to verify or update this month.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    if (totalPendingCount == 0) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Complete",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            val itemHeights = remember { mutableStateMapOf<Int, Int>() }
            var draggedIndex by remember { mutableStateOf<Int?>(null) }
            var dragOffsetY by remember { mutableStateOf(0f) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(categoryItems, key = { _, item -> item.first.id }) { index, (category, matchingAssets) ->
                    val currentIdx by rememberUpdatedState(index)
                    val currentItems by rememberUpdatedState(categoryItems)

                    val isExpanded = expandedStates[category.id] ?: false
                    val allUpdatedInThisCategory = matchingAssets.all { asset ->
                        transactions.any { tx ->
                            tx.assetId == asset.id && tx.type == "UPDATE" && tx.timestamp >= startOfMonth
                        }
                    }

                    val isCurrentDragged = draggedIndex == index

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentDragged) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (isCurrentDragged) 12.dp else 1.dp
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                itemHeights[category.id] = coordinates.size.height
                            }
                            .offset {
                                if (isCurrentDragged) {
                                    IntOffset(0, dragOffsetY.roundToInt())
                                } else {
                                    IntOffset(0, 0)
                                }
                            }
                            .zIndex(if (isCurrentDragged) 10f else 1f)
                    ) {
                        Column {
                            // Category Header row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left Section: Graggable Handle + Label (Holding here allows drag-and-drop!)
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { expandedStates[category.id] = !isExpanded }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Long press and drag to reorder",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .size(14.dp)
                                            .pointerInput(category.id) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { offset ->
                                                        draggedIndex = currentIdx
                                                        dragOffsetY = 0f
                                                        focusManager.clearFocus()
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffsetY += dragAmount.y

                                                        val currentIndex = draggedIndex
                                                        if (currentIndex != null) {
                                                            val itemsList = currentItems
                                                            // Dragging down?
                                                            if (dragOffsetY > 0 && currentIndex < itemsList.size - 1) {
                                                                val nextItem = itemsList[currentIndex + 1]
                                                                val nextHeight = itemHeights[nextItem.first.id] ?: 0
                                                                if (dragOffsetY > nextHeight / 2f) {
                                                                    val newList = itemsList.toMutableList()
                                                                    val temp = newList[currentIndex]
                                                                    newList[currentIndex] = newList[currentIndex + 1]
                                                                    newList[currentIndex + 1] = temp
                                                                    categoryItems = newList

                                                                    draggedIndex = currentIndex + 1
                                                                    dragOffsetY -= nextHeight

                                                                    // Update viewModel
                                                                    viewModel.saveCategoryOrder(newList.map { it.first.id })
                                                                }
                                                            }
                                                            // Dragging up?
                                                            else if (dragOffsetY < 0 && currentIndex > 0) {
                                                                val prevItem = itemsList[currentIndex - 1]
                                                                val prevHeight = itemHeights[prevItem.first.id] ?: 0
                                                                if (dragOffsetY < -prevHeight / 2f) {
                                                                    val newList = itemsList.toMutableList()
                                                                    val temp = newList[currentIndex]
                                                                    newList[currentIndex] = newList[currentIndex - 1]
                                                                    newList[currentIndex - 1] = temp
                                                                    categoryItems = newList

                                                                    draggedIndex = currentIndex - 1
                                                                    dragOffsetY += prevHeight

                                                                    // Update viewModel
                                                                    viewModel.saveCategoryOrder(newList.map { it.first.id })
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        draggedIndex = null
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragCancel = {
                                                        draggedIndex = null
                                                        dragOffsetY = 0f
                                                    }
                                                )
                                            }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Right Section: Collapsible Arrow & Check/Up-to-Date state (Clicking here expands/collapses!)
                                Row(
                                    modifier = Modifier
                                        .clickable { expandedStates[category.id] = !isExpanded }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val pendingCatCount = matchingAssets.count { asset ->
                                    transactions.none { tx ->
                                        tx.assetId == asset.id && tx.type == "UPDATE" && tx.timestamp >= startOfMonth
                                    }
                                }

                                if (pendingCatCount == 0) {
                                    Icon(
                                        imageVector = Icons.Default.ThumbUp,
                                        contentDescription = "Category up to date",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .size(14.dp)
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                ) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )

                                    matchingAssets.forEachIndexed { index, asset ->
                                        val lastUpdateDateLong = getLastUpdateDateLong(asset.id, transactions)
                                        val hasUpdateThisMonth = lastUpdateDateLong != null && lastUpdateDateLong >= startOfMonth

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp, horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 1. Asset Title and Subtitle Info
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = asset.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = formatDate(lastUpdateDateLong),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (hasUpdateThisMonth) 
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) 
                                                        else MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                    fontWeight = if (hasUpdateThisMonth) FontWeight.Normal else FontWeight.Medium
                                                )
                                            }

                                            // 2. Currency symbol + Underlined input layout matches screenshot exactly
                                            var textState by remember(textValues[asset.id]) {
                                                mutableStateOf(textValues[asset.id] ?: "")
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.End,
                                                modifier = Modifier.weight(1.3f)
                                            ) {
                                                val currencySymbol = getCurrencySymbol(asset.currency)
                                                Text(
                                                    text = currencySymbol,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                                    modifier = Modifier.padding(end = 4.dp),
                                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                                    fontWeight = FontWeight.Bold
                                                )

                                                TextField(
                                                    value = textState,
                                                    onValueChange = { newValue ->
                                                        val filtered = newValue.filter { it.isDigit() || it == '.' }
                                                        textState = filtered
                                                        textValues[asset.id] = filtered
                                                    },
                                                    placeholder = { Text("0.0", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp)) },
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Number,
                                                        imeAction = ImeAction.Done
                                                    ),
                                                    keyboardActions = KeyboardActions(
                                                        onDone = {
                                                            focusManager.clearFocus()
                                                            keyboardController?.hide()
                                                        }
                                                    ),
                                                    singleLine = true,
                                                    colors = TextFieldDefaults.colors(
                                                        focusedContainerColor = Color.Transparent,
                                                        unfocusedContainerColor = Color.Transparent,
                                                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                                        unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                                                    ),
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = 11.sp,
                                                        textAlign = TextAlign.End,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    modifier = Modifier
                                                        .width(135.dp)
                                                        .testTag("asset_update_input_${asset.id}")
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // 3. Thumb Up single click update marker
                                            IconButton(
                                                onClick = {
                                                    focusManager.clearFocus()
                                                    val metric = assetMetrics.find { it.asset.id == asset.id }
                                                    val typedVal = textState.toDoubleOrNull() ?: textValues[asset.id]?.toDoubleOrNull()
                                                    val fallbackVal = metric?.currentValuationNative ?: getFallbackAssetValuation(asset.id, transactions)
                                                    val finalVal = typedVal ?: fallbackVal
                                                    viewModel.addTransaction(
                                                        assetId = asset.id,
                                                        type = "UPDATE",
                                                        amount = finalVal,
                                                        notes = "Manual confirm via thumb up"
                                                    )
                                                },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .testTag("asset_thumb_button_${asset.id}")
                                            ) {
                                                Icon(
                                                    imageVector = if (hasUpdateThisMonth) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                                                    contentDescription = "Thumbs Up status",
                                                    tint = if (hasUpdateThisMonth) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        if (index < matchingAssets.size - 1) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
                                                thickness = 1.dp,
                                                modifier = Modifier.padding(horizontal = 16.dp)
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

private fun getStartOfMonthTimestamp(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun getLastUpdateDateLong(assetId: Int, transactions: List<Transaction>): Long? {
    val updates = transactions.filter { it.assetId == assetId && it.type == "UPDATE" }
    return updates.maxOfOrNull { it.timestamp }
}

private fun formatDate(timestamp: Long?): String {
    if (timestamp == null) return "Never updated"
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun getCurrencySymbol(code: String): String {
    return when (code) {
        "PHP" -> "₱"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        "CNY" -> "¥"
        "CAD" -> "CA$"
        "AUD" -> "A$"
        else -> "$code "
    }
}

private fun getCurrentMonthName(): String {
    val cal = Calendar.getInstance()
    return cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) ?: ""
}

private fun getCurrentYear(): Int {
    val cal = Calendar.getInstance()
    return cal.get(Calendar.YEAR)
}

private fun getFallbackAssetValuation(assetId: Int, transactions: List<Transaction>): Double {
    val relevant = transactions.filter { it.assetId == assetId || it.destinationAssetId == assetId }
    val updateTxs = relevant.filter { it.assetId == assetId && it.type == "UPDATE" }
        .sortedWith(compareBy<Transaction> { it.timestamp }.thenBy { it.id })
    val latestUpdate = updateTxs.lastOrNull()
    val latestUpdateTimestamp = latestUpdate?.timestamp ?: 0L
    val baseValuation = latestUpdate?.amount ?: 0.0

    var additions = 0.0
    val txsAfterUpdate = if (latestUpdate != null) {
        relevant.filter { tx -> tx.timestamp > latestUpdateTimestamp || (tx.timestamp == latestUpdateTimestamp && tx.id > latestUpdate.id) }
    } else {
        relevant
    }

    if (latestUpdate == null) {
        // Rolling sum of net deposits + income
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
