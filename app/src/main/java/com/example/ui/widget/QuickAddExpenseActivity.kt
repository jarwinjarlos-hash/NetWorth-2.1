package com.example.ui.widget

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.data.local.AppDatabase
import com.example.data.model.Asset
import com.example.data.model.AssetCategory
import com.example.data.model.Transaction
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class LocalTransactionTemplate(
    val id: String,
    val name: String,
    val amount: Double,
    val mainCategory: String,
    val subcategory: String,
    val notes: String
)

class QuickAddExpenseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Transparent style setup
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        
        setContent {
            val currentThemeIndex = remember {
                val prefs = getSharedPreferences("networth_prefs", Context.MODE_PRIVATE)
                prefs.getInt("app_theme_index", 0)
            }
            
            MyApplicationTheme(themeIndex = currentThemeIndex) {
                QuickAddExpenseScreen(
                    onDismiss = {
                        finish()
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddExpenseScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    var assets by remember { mutableStateOf<List<Asset>>(emptyList()) }
    var templates by remember { mutableStateOf<List<LocalTransactionTemplate>>(emptyList()) }
    var walletIcons by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var selectedAsset by remember { mutableStateOf<Asset?>(null) }
    var assetDropdownExpanded by remember { mutableStateOf(false) }
    
    var amountText by remember { mutableStateOf("") }
    var userNotes by remember { mutableStateOf("") }
    
    val spendingCategories = remember {
        val prefs = context.getSharedPreferences("networth_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("spending_categories_json", null)
        val defaultCategories = mapOf(
            "Food" to listOf("Groceries", "Restaurants", "Coffee"),
            "Transportation" to listOf("Public Transit", "Fuel", "Taxi/Ride-share"),
            "Utilities" to listOf("Electricity", "Water", "Internet", "Phone"),
            "Leisure" to listOf("Movies", "Hobby", "Shopping"),
            "Housing" to listOf("Rent/Mortgage", "Maintenance", "Furnishing"),
            "Other" to listOf("Uncategorized")
        )
        if (jsonStr != null) {
            try {
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
                defaultCategories
            }
        } else {
            defaultCategories
        }
    }
    
    var selectedMainCategory by remember(spendingCategories) { 
        mutableStateOf(spendingCategories.keys.firstOrNull() ?: "Food") 
    }
    var selectedSubcategory by remember(spendingCategories, selectedMainCategory) { 
        mutableStateOf(spendingCategories[selectedMainCategory]?.firstOrNull() ?: "General") 
    }
    
    val categoryEmojis = remember {
        mapOf(
            "Food" to "🍔",
            "Transportation" to "🚗",
            "Utilities" to "💡",
            "Leisure" to "🛍️",
            "Housing" to "🏠",
            "Other" to "📝"
        )
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val dao = db.financialDao()
            
            val allAssets = dao.getAllAssets().first()
            val categories = dao.getAllCategories().first()
            
            // Get spending wallet IDs and cash category IDs
            val prefs = context.getSharedPreferences("networth_prefs", Context.MODE_PRIVATE)
            val spendingWalletIdsStr = prefs.getString("spending_wallet_ids", "") ?: ""
            val spendingWalletIds = if (spendingWalletIdsStr.isEmpty()) emptyList() else spendingWalletIdsStr.split(",").mapNotNull { it.toIntOrNull() }
            
            val cashCategoryIds = categories.filter {
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
            
            val activeWallets = if (spendingWalletIds.isNotEmpty()) {
                allAssets.filter { it.id in spendingWalletIds }
            } else {
                allAssets.filter { cashCategoryIds.contains(it.categoryId) }
            }
            
            val list = activeWallets.filter { !it.isArchived }
            
            // Check default spending wallet from preferences
            val defaultId = prefs.getInt("default_spending_wallet_id", -1)
            
            // Fetch transaction templates
            val templatesList = mutableListOf<LocalTransactionTemplate>()
            val jsonStr = prefs.getString("wallet_transaction_templates", null)
            if (jsonStr != null) {
                try {
                    val arr = org.json.JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        templatesList.add(
                            LocalTransactionTemplate(
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
            }

            // Fetch wallet icons/logos
            val walletIconsMap = mutableMapOf<Int, String>()
            prefs.all.forEach { (key, value) ->
                if (key.startsWith("wallet_icon_")) {
                    val assetId = key.substringAfter("wallet_icon_").toIntOrNull()
                    if (assetId != null && value is String) {
                        walletIconsMap[assetId] = value
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                assets = list
                templates = templatesList
                walletIcons = walletIconsMap
                if (list.isNotEmpty()) {
                    selectedAsset = list.find { it.id == defaultId } ?: list.first()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { onDismiss() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clickable(enabled = false) {}, // Prevent dismiss click
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Expense Entry",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Wallet / Asset Selector
                Text(
                    text = "Select Payment Source",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { assetDropdownExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("quick_add_asset_selector"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val currentIcon = selectedAsset?.let { walletIcons[it.id] }
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        com.example.ui.screens.WalletIconView(
                                            iconKey = currentIcon,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = selectedAsset?.let { "${it.name} (${it.currency})" } ?: "No Wallet Found",
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown"
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = assetDropdownExpanded,
                        onDismissRequest = { assetDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        assets.forEach { asset ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${asset.name} (${asset.currency})",
                                        fontWeight = if (asset.id == selectedAsset?.id) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    val currentIcon = walletIcons[asset.id]
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            com.example.ui.screens.WalletIconView(
                                                iconKey = currentIcon,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedAsset = asset
                                    assetDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Templates
                if (templates.isNotEmpty()) {
                    Text(
                        text = "Quick Templates",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        templates.forEach { template ->
                            val emoji = categoryEmojis[template.mainCategory] ?: "📝"
                            SuggestionChip(
                                onClick = {
                                    amountText = if (template.amount > 0.0) String.format(Locale.US, "%.2f", template.amount) else ""
                                    selectedMainCategory = template.mainCategory
                                    selectedSubcategory = template.subcategory
                                    userNotes = if (template.notes.isNotEmpty()) template.notes else template.name
                                    Toast.makeText(context, "Loaded template: ${template.name}", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text("$emoji ${template.name}", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag("template_chip_${template.id}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Expense Amount Input
                Text(
                    text = "Amount (${selectedAsset?.currency ?: "USD"})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.toDoubleOrNull() != null || input == ".") {
                            amountText = input
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_add_amount_input"),
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Category & Subcategory selection Dropdowns
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Category Dropdown
                    var catDropdownExpanded by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedCard(
                                onClick = { catDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val emoji = categoryEmojis[selectedMainCategory] ?: "📁"
                                    Text(
                                        text = "$emoji $selectedMainCategory",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            DropdownMenu(
                                expanded = catDropdownExpanded,
                                onDismissRequest = { catDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                spendingCategories.keys.forEach { cat ->
                                    val emoji = categoryEmojis[cat] ?: "📁"
                                    DropdownMenuItem(
                                        text = { Text("$emoji $cat") },
                                        onClick = {
                                            selectedMainCategory = cat
                                            selectedSubcategory = spendingCategories[cat]?.firstOrNull() ?: "General"
                                            catDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Subcategory Dropdown
                    var subcatDropdownExpanded by remember { mutableStateOf(false) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Subcategory",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedCard(
                                onClick = { subcatDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedSubcategory,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            DropdownMenu(
                                expanded = subcatDropdownExpanded,
                                onDismissRequest = { subcatDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                val subcats = spendingCategories[selectedMainCategory] ?: listOf("General")
                                subcats.forEach { sub ->
                                    DropdownMenuItem(
                                        text = { Text(sub) },
                                        onClick = {
                                            selectedSubcategory = sub
                                            subcatDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Description/Notes Input
                Text(
                    text = "Notes (Optional)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                OutlinedTextField(
                    value = userNotes,
                    onValueChange = { userNotes = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_add_notes_input"),
                    placeholder = { Text("What did you buy?") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Post Button
                val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
                Button(
                    onClick = {
                        val wallet = selectedAsset
                        if (wallet == null) {
                            Toast.makeText(context, "Please select a payment wallet first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (parsedAmount <= 0.0) {
                            Toast.makeText(context, "Please enter a valid expense amount", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabase.getDatabase(context)
                                
                                // Format notes field EXACTLY as required by standard spender system
                                val formattedNotes = "$selectedMainCategory > $selectedSubcategory | $userNotes"
                                
                                val transaction = Transaction(
                                    assetId = wallet.id,
                                    type = "WITHDRAWAL", // Expenses are stored as Withdrawals
                                    amount = parsedAmount,
                                    timestamp = System.currentTimeMillis(),
                                    notes = formattedNotes,
                                    isDirty = true
                                )
                                
                                db.financialDao().insertTransaction(transaction)
                            }
                            
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Logged ${wallet.currency} ${String.format("%.2f", parsedAmount)} to ${wallet.name}!", Toast.LENGTH_SHORT).show()
                                
                                // Update widget broadcast to trigger refresh
                                val intent = android.content.Intent(context, QuickExpenseWidgetProvider::class.java).apply {
                                    action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                                }
                                val ids = android.appwidget.AppWidgetManager.getInstance(context)
                                    .getAppWidgetIds(android.content.ComponentName(context, QuickExpenseWidgetProvider::class.java))
                                intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                                context.sendBroadcast(intent)
                                
                                onDismiss()
                            }
                        }
                    },
                    enabled = parsedAmount > 0.0 && selectedAsset != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("quick_add_submit_button"),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Save Expense Entry",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
