package org.ollu.mini

import kotlin.random.Random
import org.ollu.mini.navigation.SystemBackHandler
import org.ollu.mini.sound.ButtonLabels
import org.ollu.mini.sound.SystemPhrases
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mimi.core.world.SceneDefinition
import org.ollu.mini.about.CreditsScreen
import org.ollu.mini.catalog.CatalogEntry
import org.ollu.mini.catalog.CatalogScreen
import org.ollu.mini.game.CompletionAction
import org.ollu.mini.game.GameScreen
import org.ollu.mini.learning.LearningModeScreen
import org.ollu.mini.learning.applyLearningSceneCompletion
import org.ollu.mini.progress.AdultEntryButton
import org.ollu.mini.progress.AdultGate
import org.ollu.mini.progress.InsightsScreen
import org.ollu.mini.progress.ProfileManagerScreen
import org.ollu.mini.progress.ProfilePickerScreen
import org.ollu.mini.progress.ProgressRepository
import org.ollu.mini.play.PlaySessionScreen
import org.ollu.mini.progress.ProgressScreen
import org.ollu.mini.sound.rememberTtsPlayer
import org.ollu.mini.theme.AppIcon
import org.ollu.mini.theme.AppIconGlyph
import org.ollu.mini.theme.AppTheme

private data class SelectedScene(
    val entry: CatalogEntry,
    val scene: SceneDefinition
)

private sealed class Screen {
    object Home : Screen()
    object Session : Screen()
    object Profiles : Screen()
    object ManageProfiles : Screen()
    object AdultMenu : Screen()
    object FreePlay : Screen()
    /** The catalog showing speech games only. */
    object SpeechPlay : Screen()
    object Learning : Screen()
    object Progress : Screen()
    object AdultGate : Screen()
    object Insights : Screen()
    object Credits : Screen()
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
    val goBack: (() -> Unit)? = when (screen) {
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
            val catalog = if ((screen as Screen.FreeGame).fromSpeech) Screen.SpeechPlay else Screen.FreePlay
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

/**
 * Home for a child using the app by themselves.
 *
 * One enormous play button and nothing to read. The screens that involve
 * choosing from a list, filtering by skill or reading a profile are an adult's
 * tools, so they sit behind the grown-up gate rather than competing for a
 * toddler's attention here.
 */
@Composable
private fun HomeScreen(
    child: org.ollu.mini.progress.ChildProfile?,
    onPlay: () -> Unit,
    onProgress: () -> Unit,
    onAdult: () -> Unit,
    onSwitchChild: (() -> Unit)?
) {
    val tts = rememberTtsPlayer()
    Box(Modifier.fillMaxSize().background(Color(0xFFFFF8E1)).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            if (child != null && onSwitchChild != null) {
                // Whose turn it is, and a way to hand the tablet over — a picture,
                // because the child recognises their own animal, not their name.
                Surface(
                    onClick = onSwitchChild,
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    shadowElevation = 1.dp
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        org.ollu.mini.renderer.CatalogImage(
                            path = "_cards/${child.avatar}.jpg",
                            contentDescription = child.name,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            child.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1D3557)
                        )
                    }
                }
            } else {
                Text(
                    "Mimi",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D3557)
                )
            }

            Surface(
                onClick = onPlay,
                shape = CircleShape,
                color = Color(0xFF2A9D8F),
                shadowElevation = 8.dp,
                modifier = Modifier.size(220.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    AppIconGlyph(AppIcon.Play, Color.White, size = 104.dp)
                }
            }

            Surface(
                onClick = { tts.speak(SystemPhrases.REWARDS); onProgress() },
                shape = CircleShape,
                color = Color(0xFFE76F51).copy(alpha = 0.16f),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    AppIconGlyph(AppIcon.Trophy, Color(0xFFE76F51), size = 46.dp)
                }
            }

            AdultEntryButton(onClick = onAdult)
        }
    }
}

/** The adult's tools, reached only through the hold-to-enter gate. */
@Composable
private fun AdultMenuScreen(
    onProfiles: () -> Unit,
    onInsights: () -> Unit,
    onCatalog: () -> Unit,
    onSpeech: () -> Unit,
    onLearning: () -> Unit,
    onCredits: () -> Unit,
    onBack: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Text("⬅  Назад", color = Color(0xFF666666))
        }
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ModeButton(
                title = "Дети",
                subtitle = "Профили и прогресс каждого",
                icon = AppIcon.Person,
                accent = Color(0xFF8E7CC3),
                onClick = onProfiles
            )
            ModeButton(
                title = "Прогресс",
                subtitle = "Как идут дела",
                icon = AppIcon.Chart,
                accent = Color(0xFF457B9D),
                onClick = onInsights
            )
            ModeButton(
                title = "Все игры",
                subtitle = "Выбрать вручную",
                icon = AppIcon.Grid,
                accent = Color(0xFF2A9D8F),
                onClick = onCatalog
            )
            ModeButton(
                title = "Запуск речи",
                subtitle = "Игры, где ребёнок говорит, повторяет и просит",
                icon = AppIcon.Mic,
                accent = Color(0xFF43A047),
                onClick = onSpeech
            )
            ModeButton(
                title = "Подбор",
                subtitle = "Профиль навыков",
                icon = AppIcon.Book,
                accent = Color(0xFFE76F51),
                onClick = onLearning
            )
            ModeButton(
                title = "Источники",
                subtitle = "Авторы звуков и лицензии",
                icon = AppIcon.Star,
                accent = Color(0xFF6D8B74),
                onClick = onCredits
            )
        }
    }
}

@Composable
private fun ModeButton(title: String, subtitle: String, icon: AppIcon, accent: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .background(accent.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AppIconGlyph(icon, accent, size = 52.dp)
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D3557)
                )
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF687381))
            }
            AppIconGlyph(AppIcon.Forward, accent, size = 34.dp)
        }
    }
}
