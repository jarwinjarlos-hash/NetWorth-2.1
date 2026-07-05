package com.example

import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.NetWorthDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.NetWorthViewModel

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: NetWorthViewModel = viewModel(
        factory = NetWorthViewModel.Factory(
          LocalContext.current.applicationContext as Application
        )
      )
      val currentThemeIndex by viewModel.themeIndex.collectAsStateWithLifecycle()
      val appFontScale by viewModel.appFontScale.collectAsStateWithLifecycle()
      val currentDensity = androidx.compose.ui.platform.LocalDensity.current
      val customDensity = androidx.compose.ui.unit.Density(
        density = currentDensity.density,
        fontScale = appFontScale
      )

      androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides customDensity
      ) {
        MyApplicationTheme(themeIndex = currentThemeIndex) {
          NetWorthDashboard(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }
  }
}
