package com.lycoris.mochitv.presentation.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lycoris.mochitv.presentation.detail.DetailScreen
import com.lycoris.mochitv.presentation.home.HomeScreen
import com.lycoris.mochitv.presentation.player.PlayerScreen

/**
 * All top-level navigation destinations.
 *
 * Path parameters are URL-encoded via [Uri.encode] when creating a route so
 * that IDs containing special characters (slashes, spaces, etc.) do not break
 * the Navigation component's route matching.  Navigation automatically
 * URL-decodes path arguments when extracting them from [androidx.lifecycle.SavedStateHandle].
 */
sealed class Screen(val route: String) {

    data object Home : Screen("home")

    data object Detail : Screen("detail/{animeId}") {
        fun createRoute(animeId: String) = "detail/${Uri.encode(animeId)}"
    }

    data object Player : Screen("player/{episodeId}/{animeId}") {
        fun createRoute(episodeId: String, animeId: String) =
            "player/${Uri.encode(episodeId)}/${Uri.encode(animeId)}"
    }
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

        // ---- Home / Search -------------------------------------------------
        composable(Screen.Home.route) {
            HomeScreen(
                onAnimeClick = { animeId ->
                    navController.navigate(Screen.Detail.createRoute(animeId))
                },
            )
        }

        // ---- Anime detail --------------------------------------------------
        composable(
            route = Screen.Detail.route,
            arguments = listOf(
                navArgument("animeId") { type = NavType.StringType },
            ),
        ) {
            DetailScreen(
                onEpisodeClick = { episodeId, animeId ->
                    navController.navigate(Screen.Player.createRoute(episodeId, animeId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        // ---- Video player --------------------------------------------------
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
}
