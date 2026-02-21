package com.flow.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.flow.presentation.analytics.AnalyticsScreen
import com.flow.presentation.history.TaskHistoryScreen
import com.flow.presentation.home.HomeScreen
import com.flow.presentation.settings.SettingsScreen

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToAnalytics = { navController.navigate(Routes.ANALYTICS) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToTaskHistory = { taskId ->
                    navController.navigate(Routes.taskStreak(taskId))
                }
            )
        }

        composable(Routes.ANALYTICS) {
            AnalyticsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.HISTORY) {
            // Global history — reserved for future full-history screen
            AnalyticsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.TASK_STREAK) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId")?.toLongOrNull() ?: 0L
            TaskHistoryScreen(
                taskId = taskId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
