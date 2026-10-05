package org.ollu.mini

import androidx.compose.runtime.*
import mimi.core.world.SceneDefinition
import org.ollu.mini.about.CreditsScreen
import org.ollu.mini.catalog.CatalogEntry
import org.ollu.mini.catalog.CatalogScreen
import org.ollu.mini.game.CompletionAction
import org.ollu.mini.game.GameScreen
import org.ollu.mini.home.AdultMenuScreen
import org.ollu.mini.home.HomeScreen
import org.ollu.mini.learning.LearningModeScreen
import org.ollu.mini.learning.applyLearningSceneCompletion
import org.ollu.mini.navigation.SystemBackHandler
import org.ollu.mini.play.PlaySessionScreen
import org.ollu.mini.progress.AdultGate
import org.ollu.mini.progress.InsightsScreen
import org.ollu.mini.progress.ProfileManagerScreen
import org.ollu.mini.progress.ProfilePickerScreen
import org.ollu.mini.progress.ProgressRepository
import org.ollu.mini.progress.ProgressScreen
import org.ollu.mini.sound.ButtonLabels
import org.ollu.mini.theme.AppTheme
import kotlin.random.Random

private data class SelectedScene(
    val entry: CatalogEntry,
    val scene: SceneDefinition
)

private sealed class Screen {
    data object Home : Screen()
    data object Session : Screen()
    data object Profiles : Screen()
    data object ManageProfiles : Screen()
    data object AdultMenu : Screen()
    data object FreePlay : Screen()
    /** The catalog showing speech games only. */
    data object SpeechPlay : Screen()
    data object Learning : Screen()
    data object Progress : Screen()
    data object AdultGate : Screen()
    data object Insights : Screen()
    data object Credits : Screen()
    /**
     * [attempt] is what makes "again" work: the screen is otherwise identical, so
     * without it the state never changes, the engine session is reused, and the
     * button appeared to do nothing. It also deals the scene a new arrangement.
     */
    data class FreeGame(
        val selected: SelectedScene,
        val attempt:  Int = 0,
        /** Opened from «Запуск речи», so back and "to the catalog" return there. */
        val fromSpeech: Boolean = false,
        /**
         * Drawn fresh each time a game is opened. The engine is deterministic by
         * seed, and every free game used to start at seed 0 — so a scene that
         * draws its answer at random asked for the same one on every visit.
         */
        val seed: Long = Random.nextLong()
    ) : Screen()
    data class LearningGame(val selected: SelectedScene, val seed: Long = Random.nextLong()) : Screen()
}

@Composable
fun App(
    onThemeChanged: @Composable (isDark: Boolean) -> Unit = {}
) = AppTheme(onThemeChanged) {
    val progressRepository = remember { ProgressRepository() }
    // Always ask who is here, as soon as anybody has a profile. Carrying on as
    // whoever played last put one child's games and progress under another's
    // name whenever the tablet changed hands, and nobody noticed until the
    // progress screen stopped making sense.
    var screen by remember {
        mutableStateOf<Screen>(
            if (progressRepository.getProfiles().isNotEmpty()) Screen.Profiles else Screen.Home
        )
    }
    var whoseTurn by remember { mutableStateOf(progressRepository.currentProfile()) }

    // Where "back" leads from each screen — the on-screen button and the phone's
    // own. Kept in one place because they had drifted apart: the phone's button
    // closed the app from anywhere, and the child's rewards screen led "back"
    // into the adult menu, past the gate meant to keep a child out of it.
    // Null means back leaves the app, which is only right on the home screen.
    val goBack: (() -> Unit)? = when (val current = screen) {
        is Screen.Home           -> null
        is Screen.Profiles       -> { { screen = Screen.Home } }
        is Screen.Session        -> { { screen = Screen.Home } }
        is Screen.Progress       -> { { screen = Screen.Home } }
        is Screen.AdultGate      -> { { screen = Screen.Home } }
        is Screen.AdultMenu      -> { { screen = Screen.Home } }
        is Screen.ManageProfiles -> { {
            whoseTurn = progressRepository.currentProfile()
            screen = Screen.AdultMenu
        } }
        is Screen.Credits        -> { { screen = Screen.AdultMenu } }
        is Screen.Insights       -> { { screen = Screen.AdultMenu } }
        is Screen.FreePlay       -> { { screen = Screen.AdultMenu } }
        is Screen.SpeechPlay     -> { { screen = Screen.AdultMenu } }
        is Screen.Learning       -> { { screen = Screen.AdultMenu } }
        is Screen.FreeGame       -> {
            val catalog = if (current.fromSpeech) Screen.SpeechPlay else Screen.FreePlay
            { screen = catalog }
        }
        is Screen.LearningGame   -> { { screen = Screen.Learning } }
    }
    SystemBackHandler(enabled = goBack != null) { goBack?.invoke() }
    val back: () -> Unit = { goBack?.invoke() }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(
            child      = whoseTurn,
            onPlay     = { screen = Screen.Session },
            onProgress = { screen = Screen.Progress },
            onAdult    = { screen = Screen.AdultGate },
            onSwitchChild = if (progressRepository.getProfiles().isNotEmpty()) {
                { screen = Screen.Profiles }
            } else null
        )
        is Screen.Profiles -> ProfilePickerScreen(
            profiles = progressRepository.getProfiles(),
            onPicked = { profile ->
                progressRepository.setCurrentProfile(profile.id)
                whoseTurn = profile
                screen = Screen.Home
            },
            onManage = { screen = Screen.AdultGate }
        )
        is Screen.ManageProfiles -> ProfileManagerScreen(
            repository = progressRepository,
            onBack     = back
        )
        is Screen.Session -> PlaySessionScreen(
            progressRepository = progressRepository,
            onFinished         = { screen = Screen.Home }
        )
        is Screen.AdultGate -> AdultGate(
            onUnlocked = { screen = Screen.AdultMenu },
            onCancel   = back
        )
        is Screen.AdultMenu -> AdultMenuScreen(
            onProfiles = { screen = Screen.ManageProfiles },
            onInsights = { screen = Screen.Insights },
            onCatalog  = { screen = Screen.FreePlay },
            onSpeech   = { screen = Screen.SpeechPlay },
            onLearning = { screen = Screen.Learning },
            onCredits  = { screen = Screen.Credits },
            onBack     = back
        )
        is Screen.Credits -> CreditsScreen(onBack = back)

        is Screen.Insights -> InsightsScreen(
            progressRepository = progressRepository,
            onBack             = back
        )
        is Screen.Progress -> ProgressScreen(
            progressRepository = progressRepository,
            onBack             = back
        )
        is Screen.FreePlay -> CatalogScreen(
            onSceneSelected    = { entry, scene -> screen = Screen.FreeGame(SelectedScene(entry, scene)) },
            progressRepository = progressRepository,
            onBack             = back
        )
        is Screen.SpeechPlay -> CatalogScreen(
            onSceneSelected    = { entry, scene ->
                screen = Screen.FreeGame(SelectedScene(entry, scene), fromSpeech = true)
            },
            progressRepository = progressRepository,
            onBack             = back,
            speechOnly         = true
        )
        is Screen.Learning -> LearningModeScreen(
            progressRepository = progressRepository,
            onStartScene = { entry, scene -> screen = Screen.LearningGame(SelectedScene(entry, scene)) },
            onBack = back
        )
        is Screen.FreeGame -> GameScreen(
            scene              = s.selected.scene,
            seed               = s.seed + s.attempt,
            onBack             = back,
            progressRepository = progressRepository,
            completionActions  = listOf(
                CompletionAction(ButtonLabels.AGAIN, icon = "🔄", primary = true) {
                    screen = s.copy(attempt = s.attempt + 1)
                },
                CompletionAction(ButtonLabels.TO_CATALOG, icon = "🏠") {
                    screen = if (s.fromSpeech) Screen.SpeechPlay else Screen.FreePlay
                }
            )
        )
        is Screen.LearningGame -> GameScreen(
            scene              = s.selected.scene,
            seed               = s.seed,
            onBack             = back,
            progressRepository = progressRepository,
            completionActions  = listOf(
                CompletionAction(ButtonLabels.NEXT_SCENE, icon = "▶", primary = true) { screen = Screen.Learning }
            ),
            onCompleted = { metrics ->
                applyLearningSceneCompletion(
                    progressRepository = progressRepository,
                    childId            = progressRepository.currentChildId(),
                    entry              = s.selected.entry,
                    scene              = s.selected.scene,
                    metrics            = metrics
                )
            }
        )
    }
}
