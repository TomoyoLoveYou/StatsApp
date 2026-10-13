package com.stats.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stats.app.ui.categories.CategoriesScreen
import com.stats.app.ui.history.HistoryScreen
import com.stats.app.ui.main.MainScreen
import com.stats.app.ui.tasks.TaskEditorScreen
import kotlinx.serialization.Serializable

@Serializable
object MainRoute

@Serializable
data class TaskEditorRoute(val taskId: Long? = null)

@Serializable
object CategoriesRoute

@Serializable
object HistoryRoute

@Composable
fun StatsAppRoot() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = MainRoute) {
        composable<MainRoute> {
            MainScreen(
                onCreateTask = { navController.navigate(TaskEditorRoute()) },
                onEditTask = { id -> navController.navigate(TaskEditorRoute(id)) },
                onOpenCategories = { navController.navigate(CategoriesRoute) },
                onOpenHistory = { navController.navigate(HistoryRoute) },
            )
        }
        composable<TaskEditorRoute> {
            TaskEditorScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<CategoriesRoute> {
            CategoriesScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable<HistoryRoute> {
            HistoryScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
