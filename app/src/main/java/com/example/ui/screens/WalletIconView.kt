package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

fun getWalletPresetIcon(key: String?): ImageVector {
    return when (key) {
        "savings" -> Icons.Default.Savings
        "credit_card" -> Icons.Default.CreditCard
        "bank" -> Icons.Default.AccountBalance
        "phone" -> Icons.Default.PhoneAndroid
        "shopping" -> Icons.Default.ShoppingCart
        "work" -> Icons.Default.Work
        "global" -> Icons.Default.Language
        "car" -> Icons.Default.DirectionsCar
        "home" -> Icons.Default.Home
        "wallet" -> Icons.Default.Wallet
        else -> Icons.Default.Savings
    }
}

val ALL_PRESET_ICONS = listOf(
    "savings" to "Savings / Cash",
    "credit_card" to "Credit Card",
    "bank" to "Bank Account",
    "phone" to "Mobile / PayPal",
    "shopping" to "Shopping / Card",
    "work" to "Business / Income",
    "global" to "Foreign Exchange",
    "car" to "Automotive",
    "home" to "Real Estate / Rent",
    "wallet" to "Cash Wallet"
)

@Composable
fun WalletIconView(
    iconKey: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    fallbackIcon: ImageVector = Icons.Default.Savings
) {
    if (iconKey != null && (iconKey.startsWith("file://") || iconKey.startsWith("http://") || iconKey.startsWith("https://") || iconKey.startsWith("content://"))) {
        AsyncImage(
            model = iconKey,
            contentDescription = "Wallet Logo",
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        val vector = getWalletPresetIcon(iconKey)
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    }
}
