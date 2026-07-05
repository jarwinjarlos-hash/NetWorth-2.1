package com.example

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.FinancialRepository
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.NetWorthViewModel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val repository = FinancialRepository(database.financialDao())
    val viewModel = NetWorthViewModel(context, repository)

    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.screens.PortfolioSummaryHeader(
          summary = com.example.ui.viewmodel.PerformanceMetrics(
            totalValuationUsd = 125400.0,
            totalGainLossUsd = 15400.0,
            totalGainLossPercent = 14.1,
            netDepositsUsd = 110000.0,
            totalIncomeUsd = 450.0
          ),
          baseCurrency = "USD",
          viewModel = viewModel
        )
      }
    }

    composeTestRule.waitForIdle()

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")

    database.close()
  }
}
