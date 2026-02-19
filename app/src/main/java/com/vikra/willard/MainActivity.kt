package com.vikra.willard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vikra.willard.presentation.analytics.AnalyticsScreen
import com.vikra.willard.presentation.home.HomeScreen
import com.vikra.willard.ui.theme.WillardTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WillardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToAnalytics = { navController.navigate("analytics") },
                onNavigateToTaskHistory = { taskId -> navController.navigate("task_history/$taskId") }
            ) 
        }
        composable("analytics") {
            AnalyticsScreen(onBack = { navController.popBackStack() })
        }
        composable("task_history/{taskId}") { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId")?.toLongOrNull() ?: 0L
            com.vikra.willard.presentation.history.TaskHistoryScreen(
                taskId = taskId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
