package com.example.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.AddInvoiceScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InvoiceDetailScreen
import com.example.ui.screens.RemindersTimelineScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.NothingRed
import com.example.ui.viewmodel.MainViewModel

object Routes {
    const val HOME = "home"
    const val TIMELINE = "timeline"
    const val SETTINGS = "settings"
    const val ADD = "add"
    const val EDIT = "edit/{id}"
    const val DETAIL = "detail/{id}"

    fun editRoute(id: Long) = "edit/$id"
    fun detailRoute(id: Long) = "detail/$id"
}

data class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

val bottomNavItems = listOf(
    BottomNavItem(Routes.HOME, "BILLS", Icons.Filled.Receipt, Icons.Outlined.Receipt, "nav_home"),
    BottomNavItem(Routes.TIMELINE, "TIMELINE", Icons.Filled.Notifications, Icons.Outlined.Notifications, "nav_timeline"),
    BottomNavItem(Routes.SETTINGS, "SETTINGS", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
)

@Composable
fun MainNavGraph(
    viewModel: MainViewModel,
    initialItemId: Long? = null,
    onThemeChanged: (String) -> Unit,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(initialItemId) {
        if (initialItemId != null && initialItemId > 0L) {
            navController.navigate(Routes.detailRoute(initialItemId))
        }
    }

    val isBottomBarVisible = currentRoute in listOf(Routes.HOME, Routes.TIMELINE, Routes.SETTINGS)

    Scaffold(
        bottomBar = {
            if (isBottomBarVisible) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(0.dp)
                    )
                ) {
                    bottomNavItems.forEach { item ->
                        val isSelected = currentRoute == item.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = DotoFontFamily,
                                        fontSize = 9.sp,
                                        letterSpacing = 1.2.sp
                                    ),
                                    color = if (isSelected) NothingRed else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NothingRed,
                                selectedTextColor = NothingRed,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() }
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        viewModel = viewModel,
                        onAddItemClick = { navController.navigate(Routes.ADD) },
                        onItemClick = { id -> navController.navigate(Routes.detailRoute(id)) },
                        onEditItem = { id -> navController.navigate(Routes.editRoute(id)) }
                    )
                }

                composable(Routes.TIMELINE) {
                    RemindersTimelineScreen(
                        viewModel = viewModel,
                        onItemClick = { itemId -> navController.navigate(Routes.detailRoute(itemId)) }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onThemeChanged = onThemeChanged
                    )
                }

                composable(Routes.ADD) {
                    AddInvoiceScreen(
                        viewModel = viewModel,
                        editItemId = null,
                        onSaved = { itemId ->
                            navController.navigate(Routes.detailRoute(itemId)) {
                                popUpTo(Routes.HOME)
                            }
                        },
                        onCancel = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Routes.EDIT,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("id") ?: 0L
                    AddInvoiceScreen(
                        viewModel = viewModel,
                        editItemId = id,
                        onSaved = { savedId ->
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Routes.DETAIL,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("id") ?: 0L
                    InvoiceDetailScreen(
                        itemId = id,
                        viewModel = viewModel,
                        onEditItem = { editId -> navController.navigate(Routes.editRoute(editId)) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
