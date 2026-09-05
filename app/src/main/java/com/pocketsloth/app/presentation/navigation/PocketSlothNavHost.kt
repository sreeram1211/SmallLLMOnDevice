package com.pocketsloth.app.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pocketsloth.app.presentation.screens.ChatPlaygroundScreen
import com.pocketsloth.app.presentation.screens.DatasetScreen
import com.pocketsloth.app.presentation.screens.TrainingConfigScreen
import com.pocketsloth.app.presentation.screens.TrainingDashboardScreen
import com.pocketsloth.app.presentation.screens.home.HomeScreen
import com.pocketsloth.app.presentation.viewmodel.ChatPlaygroundViewModel
import com.pocketsloth.app.presentation.viewmodel.DatasetViewModel
import com.pocketsloth.app.presentation.viewmodel.TrainingConfigViewModel
import com.pocketsloth.app.presentation.viewmodel.TrainingDashboardViewModel

private data class NavItem(
    val destination: TopLevelDestination,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavItem(TopLevelDestination.Home, Icons.Filled.Home),
    NavItem(TopLevelDestination.Datasets, Icons.Filled.List),
    NavItem(TopLevelDestination.Config, Icons.Filled.Settings),
    NavItem(TopLevelDestination.Training, Icons.Filled.PlayArrow),
    NavItem(TopLevelDestination.Chat, Icons.Filled.Send),
)

@Composable
fun PocketSlothNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.HOME,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                navItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == item.destination.route
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.destination.label,
                            )
                        },
                        label = { Text(item.destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateDatasets = { navController.navigate(Routes.DATASETS) },
                    onNavigateConfig = { navController.navigate(Routes.CONFIG) },
                    onNavigateTraining = { navController.navigate(Routes.TRAINING) },
                    onNavigateChat = { navController.navigate(Routes.CHAT) },
                )
            }
            composable(Routes.DATASETS) {
                val vm: DatasetViewModel = viewModel()
                DatasetScreen(viewModel = vm)
            }
            composable(Routes.CONFIG) {
                val vm: TrainingConfigViewModel = viewModel()
                TrainingConfigScreen(
                    viewModel = vm,
                    onTrainingStarted = {
                        navController.navigate(Routes.TRAINING) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.TRAINING) {
                val vm: TrainingDashboardViewModel = viewModel()
                TrainingDashboardScreen(viewModel = vm)
            }
            composable(Routes.CHAT) {
                val vm: ChatPlaygroundViewModel = viewModel()
                ChatPlaygroundScreen(viewModel = vm)
            }
        }
    }
}
