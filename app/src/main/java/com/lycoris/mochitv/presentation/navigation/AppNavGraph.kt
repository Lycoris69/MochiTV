package com.lycoris.mochitv.presentation.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lycoris.mochitv.presentation.detail.DetailScreen
import com.lycoris.mochitv.presentation.downloads.DownloadsScreen
import com.lycoris.mochitv.presentation.home.HomeScreen
import com.lycoris.mochitv.presentation.library.LibraryScreen
import com.lycoris.mochitv.presentation.player.PlayerScreen

sealed class Screen(val route: String) {
    data object Home      : Screen("home")
    data object Library   : Screen("library")
    data object Downloads : Screen("downloads")

    data object Detail : Screen("detail/{animeId}") {
        fun createRoute(animeId: String) = "detail/${Uri.encode(animeId)}"
    }

    data object Player : Screen("player/{episodeId}/{animeId}") {
        fun createRoute(episodeId: String, animeId: String) =
            "player/${Uri.encode(episodeId)}/${Uri.encode(animeId)}"
    }
}

private val bottomNavItems = listOf(
    Triple(Screen.Home,      "Home",      Icons.Filled.Home),
    Triple(Screen.Library,   "Library",   Icons.Filled.Bookmark),
    Triple(Screen.Downloads, "Downloads", Icons.Filled.Download),
)

@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentDest = backStack?.destination
    val showBottomBar = currentDest?.route in setOf(
        Screen.Home.route,
        Screen.Library.route,
        Screen.Downloads.route,
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { (screen, label, icon) ->
                        NavigationBarItem(
                            selected = currentDest?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onAnimeClick = { animeId ->
                        navController.navigate(Screen.Detail.createRoute(animeId))
                    },
                )
            }

            composable(Screen.Library.route) {
                LibraryScreen(
                    onAnimeClick = { animeId ->
                        navController.navigate(Screen.Detail.createRoute(animeId))
                    },
                )
            }

            composable(Screen.Downloads.route) {
                DownloadsScreen(
                    onAnimeClick = { animeId ->
                        navController.navigate(Screen.Detail.createRoute(animeId))
                    },
                )
            }

            composable(
                route = Screen.Detail.route,
                arguments = listOf(navArgument("animeId") { type = NavType.StringType }),
            ) {
                DetailScreen(
                    onEpisodeClick = { episodeId, animeId ->
                        navController.navigate(Screen.Player.createRoute(episodeId, animeId))
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Screen.Player.route,
                arguments = listOf(
                    navArgument("episodeId") { type = NavType.StringType },
                    navArgument("animeId")   { type = NavType.StringType },
                ),
            ) {
                PlayerScreen(onBack = { navController.popBackStack() })
            }
        }
        // suppress unused innerPadding — Scaffold handles insets via NavigationBar
        innerPadding
    }
}
