package com.autobuy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.autobuy.app.ui.theme.AutoBuyTheme
import com.autobuy.feature.config.ConfigScreen
import com.autobuy.feature.dashboard.DashboardScreen
import com.autobuy.feature.recorder.RecorderScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val navigateTo = intent.getStringExtra("navigate_to")

        setContent {
            AutoBuyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AutoBuyNavGraph(startDestination = navigateTo ?: NavRoutes.DASHBOARD)
                }
            }
        }
    }
}

object NavRoutes {
    const val DASHBOARD = "dashboard"
    const val CONFIG = "config"
    const val RECORDER = "recorder"
    const val HANDOVER = "handover"
}

@Composable
fun AutoBuyNavGraph(startDestination: String) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NavRoutes.DASHBOARD) {
            DashboardScreen(
                onNavigateToConfig = { navController.navigate(NavRoutes.CONFIG) },
                onNavigateToRecorder = { navController.navigate(NavRoutes.RECORDER) }
            )
        }
        composable(NavRoutes.CONFIG) {
            ConfigScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.RECORDER) {
            RecorderScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
