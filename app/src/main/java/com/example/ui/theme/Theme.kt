package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
  lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
  )

private val OceanicDarkColorScheme = darkColorScheme(
  primary = Color(0xFF38BDF8),
  secondary = Color(0xFF7DD3FC),
  tertiary = Color(0xFF0EA5E9)
)

private val OceanicLightColorScheme = lightColorScheme(
  primary = Color(0xFF0284C7),
  secondary = Color(0xFF0EA5E9),
  tertiary = Color(0xFF38BDF8)
)

private val ForestDarkColorScheme = darkColorScheme(
  primary = Color(0xFF34D399),
  secondary = Color(0xFF6EE7B7),
  tertiary = Color(0xFF059669)
)

private val ForestLightColorScheme = lightColorScheme(
  primary = Color(0xFF059669),
  secondary = Color(0xFF10B981),
  tertiary = Color(0xFF34D399)
)

@Composable
fun MyApplicationTheme(
  themeIndex: Int = 0,
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when (themeIndex) {
      1 -> if (darkTheme) OceanicDarkColorScheme else OceanicLightColorScheme
      2 -> if (darkTheme) ForestDarkColorScheme else ForestLightColorScheme
      else -> {
        when {
          dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
          }
          darkTheme -> DarkColorScheme
          else -> LightColorScheme
        }
      }
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
