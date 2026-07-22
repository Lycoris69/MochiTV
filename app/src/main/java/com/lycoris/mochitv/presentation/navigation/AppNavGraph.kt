package com.lycoris.mochitv.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lycoris.mochitv.presentation.home.HomeScreen

/**
 * All top-level navigation destinations.
 *
 * Sealed class pattern keeps route strings co-located with the destination
 * type; add parameterised routes here as the app grows (e.g. Detail, Player).
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")

    // Planned destinations — uncomment and implement when ready:
    // data object Detail : Screen("detail/{animeId}") {
    //     fun createRoute(animeId: String) = "detail/$animeId"
    // }
    // data object Player : Screen("player/{episodeId}") {
    //     fun createRoute(episodeId: String) = "player/$episodeId"
    // }
}

/**
 * Compose [NavHost] wiring all screens together.
 *
 * The [navController] parameter is exposed so callers can pass a test controller;
 * production callers can use the default [rememberNavController].
 */
@Composable
fun AppNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
    ) {
        composable(Screen.Home.route) {
            HomeScreen()
        }
    }
}
