package com.example.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.model.Asset
import com.example.data.model.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuickExpenseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val coroutineScope = CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            withContext(Dispatchers.Main) {
                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.quick_expense_widget)
                    
                    // Setup quick button action to open QuickAddExpenseActivity
                    val addExpenseIntent = Intent(context, QuickAddExpenseActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val addExpensePendingIntent = PendingIntent.getActivity(
                        context,
                        101,
                        addExpenseIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, addExpensePendingIntent)
                    views.setOnClickPendingIntent(R.id.widget_container, addExpensePendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, QuickExpenseWidgetProvider::class.java))
            onUpdate(context, appWidgetManager, ids)
        }
    }

    private fun calculateAssetBalance(asset: Asset, isAssetType: Boolean, txs: List<Transaction>): Double {
        val updateTxs = txs.filter { it.assetId == asset.id && it.type == "UPDATE" }
            .sortedWith(compareBy<Transaction> { it.timestamp }.thenBy { it.id })
        val latestUpdate = updateTxs.lastOrNull()
        val latestUpdateTimestamp = latestUpdate?.timestamp ?: 0L
        val baseValuation = latestUpdate?.amount ?: 0.0

        var additionsAfterUpdate = 0.0
        val transactionsAfterUpdate = if (latestUpdate != null) {
            txs.filter { tx -> tx.timestamp > latestUpdateTimestamp || (tx.timestamp == latestUpdateTimestamp && tx.id > latestUpdate.id) }
        } else {
            txs
        }

        val netDepositsNative = txs.filter { 
            it.assetId == asset.id && (it.type == "DEPOSIT" || it.type == "TRANSFER" || it.type == "WITHDRAWAL" || it.type == "INCOME")
        }.sumOf { tx ->
            if (isAssetType) {
                when (tx.type) {
                    "DEPOSIT" -> tx.amount
                    "WITHDRAWAL" -> -tx.amount
                    "TRANSFER" -> -tx.amount
                    "INCOME" -> tx.amount
                    else -> 0.0
                }
            } else {
                when (tx.type) {
                    "DEPOSIT" -> -tx.amount
                    "WITHDRAWAL" -> tx.amount
                    "TRANSFER" -> tx.amount
                    "INCOME" -> -tx.amount
                    else -> 0.0
                }
            }
        } + txs.filter { it.destinationAssetId == asset.id && it.type == "TRANSFER" }.sumOf { tx ->
            val incomingAmount = tx.amount * (tx.exchangeRate ?: 1.0)
            if (isAssetType) incomingAmount else -incomingAmount
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

        return if (latestUpdate == null) {
            netDepositsNative
        } else {
            baseValuation + additionsAfterUpdate
        }
    }
}
