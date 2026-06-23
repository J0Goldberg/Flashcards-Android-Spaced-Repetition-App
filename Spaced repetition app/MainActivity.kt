package com.flashcards

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.*
import androidx.navigation.compose.*
import com.flashcards.ui.home.HomeScreen
import com.flashcards.ui.study.StudyScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FlashcardsApp() }
    }
}

// ── Route definitions ────────────────────────────────────────────────────────

object Routes {
    const val HOME   = "home"
    const val STUDY  = "study/{deckId}/{deckName}"
    const val STATS  = "stats"
    const val EDITOR = "editor?deckId={deckId}"

    fun study(deckId: Long, deckName: String) = "study/$deckId/$deckName"
    fun editor(deckId: Long? = null) = if (deckId != null) "editor?deckId=$deckId" else "editor"
}

// ── Nav graph ────────────────────────────────────────────────────────────────

@Composable
fun FlashcardsApp() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onStudy  = { id, name -> navController.navigate(Routes.study(id, name)) },
                onStats  = { navController.navigate(Routes.STATS) },
                onEditor = { id -> navController.navigate(Routes.editor(id)) }
            )
        }

        composable(
            route     = Routes.STUDY,
            arguments = listOf(
                navArgument("deckId")   { type = NavType.LongType },
                navArgument("deckName") { type = NavType.StringType }
            )
        ) { backStack ->
            val deckName = backStack.arguments?.getString("deckName") ?: ""
            StudyScreen(
                deckName = deckName,
                onBack   = { navController.popBackStack() }
            )
        }

        composable(Routes.STATS) {
            // StatsScreen — wire up similarly; omitted for brevity
            // StatsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route     = Routes.EDITOR,
            arguments = listOf(navArgument("deckId") {
                type     = NavType.LongType
                nullable = false
                defaultValue = -1L
            })
        ) {
            // EditorScreen — wire up similarly; omitted for brevity
            // EditorScreen(onBack = { navController.popBackStack() })
        }
    }
}
