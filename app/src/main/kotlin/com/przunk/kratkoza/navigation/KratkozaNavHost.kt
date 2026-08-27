package com.przunk.kratkoza.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.przunk.kratkoza.di.AppContainer
import com.przunk.kratkoza.ui.daily.DailyScreen
import com.przunk.kratkoza.ui.daily.DailyViewModel
import com.przunk.kratkoza.ui.game.GameScreen
import com.przunk.kratkoza.ui.game.GameViewModel
import com.przunk.kratkoza.ui.home.HomeScreen
import com.przunk.kratkoza.ui.home.HomeViewModel
import com.przunk.kratkoza.ui.newgame.NewGameScreen
import com.przunk.kratkoza.ui.newgame.NewGameViewModel
import com.przunk.kratkoza.ui.saves.SavedGamesScreen
import com.przunk.kratkoza.ui.saves.SavedGamesViewModel
import com.przunk.kratkoza.ui.settings.SettingsScreen
import com.przunk.kratkoza.ui.settings.SettingsViewModel
import com.przunk.kratkoza.ui.statistics.StatisticsScreen
import com.przunk.kratkoza.ui.statistics.StatisticsViewModel

/**
 * Every screen and every way between them (technical_requirements.md section 11).
 *
 * ViewModels are built here from [AppContainer] rather than injected by a framework
 * (technical_requirements.md section 8). Each factory is three lines and says exactly what its
 * screen depends on.
 */
@Composable
fun KratkozaNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.HOME,
        modifier = modifier,
    ) {
        composable(Destinations.HOME) {
            HomeScreen(
                viewModel = viewModel(factory = factory { HomeViewModel(container.gameRepository) }),
                onContinue = { navController.navigate(Destinations.game(it)) },
                onNewGame = { navController.navigate(Destinations.NEW_GAME) },
                onDaily = { navController.navigate(Destinations.DAILY) },
                onSavedGames = { navController.navigate(Destinations.SAVED_GAMES) },
                onStatistics = { navController.navigate(Destinations.STATISTICS) },
                onSettings = { navController.navigate(Destinations.SETTINGS) },
            )
        }

        composable(Destinations.NEW_GAME) {
            NewGameScreen(
                viewModel = viewModel(factory = factory { NewGameViewModel(container.gameRepository) }),
                onGameCreated = { puzzleId ->
                    // The chooser is replaced, not stacked on: pressing back from a fresh game
                    // should reach Home, not offer to generate another board.
                    navController.navigate(Destinations.game(puzzleId)) {
                        popUpTo(Destinations.NEW_GAME) { inclusive = true }
                    }
                },
                onNavigateBack = navController::popBackStack,
            )
        }

        composable(
            route = Destinations.GAME,
            arguments = listOf(navArgument(Destinations.GAME_PUZZLE_ID) { type = NavType.StringType }),
        ) { entry ->
            val puzzleId = entry.arguments?.getString(Destinations.GAME_PUZZLE_ID).orEmpty()
            GameScreen(
                viewModel = viewModel(
                    // Keyed by board, so navigating between two saved games does not hand the
                    // second one the first one's ViewModel.
                    key = puzzleId,
                    factory = factory {
                        GameViewModel(
                            puzzleId = puzzleId,
                            gameRepository = container.gameRepository,
                            settingsFlow = container.settingsRepository.settings,
                            ranking = container.ranking,
                        )
                    },
                ),
                onNavigateBack = navController::popBackStack,
                onNewGame = {
                    navController.navigate(Destinations.NEW_GAME) {
                        popUpTo(Destinations.HOME)
                    }
                },
            )
        }

        composable(Destinations.DAILY) {
            DailyScreen(
                viewModel = viewModel(factory = factory { DailyViewModel(container.gameRepository, container.ranking) }),
                onOpenGame = { navController.navigate(Destinations.game(it)) },
                onNavigateBack = navController::popBackStack,
            )
        }

        composable(Destinations.SAVED_GAMES) {
            SavedGamesScreen(
                viewModel = viewModel(factory = factory { SavedGamesViewModel(container.gameRepository) }),
                onOpenGame = { navController.navigate(Destinations.game(it)) },
                onNavigateBack = navController::popBackStack,
            )
        }

        composable(Destinations.STATISTICS) {
            StatisticsScreen(
                viewModel = viewModel(factory = factory { StatisticsViewModel(container.gameRepository) }),
                onNavigateBack = navController::popBackStack,
            )
        }

        composable(Destinations.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel(factory = factory { SettingsViewModel(container.settingsRepository) }),
                onNavigateBack = navController::popBackStack,
            )
        }
    }
}

/** One-liner around the ViewModel factory DSL, so each call site above stays readable. */
private inline fun <reified T : androidx.lifecycle.ViewModel> factory(
    crossinline create: () -> T,
): ViewModelProvider.Factory = viewModelFactory { initializer { create() } }
