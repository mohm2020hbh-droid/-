package com.voiceduel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.voiceduel.ui.screens.GameOverScreen
import com.voiceduel.ui.screens.HomeScreen
import com.voiceduel.ui.screens.PlayScreen
import com.voiceduel.ui.screens.RatingScreen
import com.voiceduel.ui.screens.RoundResultScreen
import com.voiceduel.ui.screens.WaitingScreen

/** The five screens of the game, addressed by route. */
object Routes {
    const val HOME = "home"
    const val WAITING = "waiting"
    const val PLAY = "play"
    const val RATING = "rating"
    const val ROUND_RESULT = "round_result"
    const val GAME_OVER = "game_over"
}

/**
 * Navigation host.
 *
 * The match is server-driven, so navigation follows [GameUiState.phase] rather
 * than being triggered from inside the screens.
 */
@Composable
fun VoiceDuelApp(
    viewModel: GameViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.phase) {
        val route = state.phase.toRoute()
        if (navController.currentDestination?.route == route) return@LaunchedEffect

        navController.navigate(route) {
            // The match is a linear flow; never stack screens behind it.
            popUpTo(Routes.HOME) { inclusive = route == Routes.HOME }
            launchSingleTop = true
        }
    }

    // Leaving mid-match must tell the server, so the opponent is not stranded.
    BackHandler(enabled = state.phase != GamePhase.HOME) {
        viewModel.onLeaveMatch()
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                state = state,
                onServerUrlChange = viewModel::onServerUrlChange,
                onJoinCodeChange = viewModel::onJoinCodeChange,
                onCreateRoom = viewModel::onCreateRoom,
                onJoinRoom = viewModel::onJoinRoom,
                onDismissNotice = viewModel::onDismissNotice,
            )
        }

        composable(Routes.WAITING) {
            WaitingScreen(
                state = state,
                onLeave = viewModel::onLeaveMatch,
                onDismissNotice = viewModel::onDismissNotice,
            )
        }

        composable(Routes.PLAY) {
            PlayScreen(
                state = state,
                onMicrophonePermission = viewModel::onMicrophonePermission,
                onSendNow = viewModel::onSendRecordingNow,
                onLeave = viewModel::onLeaveMatch,
                onDismissNotice = viewModel::onDismissNotice,
            )
        }

        composable(Routes.RATING) {
            RatingScreen(
                state = state,
                onScoreChange = viewModel::onScoreChange,
                onReplay = viewModel::onReplayRecording,
                onSubmit = viewModel::onSubmitRating,
                onDismissNotice = viewModel::onDismissNotice,
            )
        }

        composable(Routes.ROUND_RESULT) {
            RoundResultScreen(state = state, onDismissNotice = viewModel::onDismissNotice)
        }

        composable(Routes.GAME_OVER) {
            GameOverScreen(
                state = state,
                onPlayAgain = viewModel::onLeaveMatch,
                onDismissNotice = viewModel::onDismissNotice,
            )
        }
    }
}

private fun GamePhase.toRoute(): String = when (this) {
    GamePhase.HOME -> Routes.HOME
    GamePhase.WAITING -> Routes.WAITING
    GamePhase.PLAYING -> Routes.PLAY
    GamePhase.RATING -> Routes.RATING
    GamePhase.ROUND_RESULT -> Routes.ROUND_RESULT
    GamePhase.GAME_OVER -> Routes.GAME_OVER
}
