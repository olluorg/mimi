package org.ollu.mini

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
import org.ollu.mini.catalog.CatalogEntry
import org.ollu.mini.catalog.CatalogScreen
import org.ollu.mini.game.CompletionAction
import org.ollu.mini.game.GameScreen
import org.ollu.mini.learning.LearningModeScreen
import org.ollu.mini.learning.applyLearningSceneCompletion
import org.ollu.mini.progress.ProgressRepository
import org.ollu.mini.progress.ProgressScreen
import org.ollu.mini.sound.rememberTtsPlayer
import org.ollu.mini.theme.AppTheme

private data class SelectedScene(
    val entry: CatalogEntry,
    val scene: SceneDefinition
)

private sealed class Screen {
    object Home : Screen()
    object FreePlay : Screen()
    object Learning : Screen()
    object Progress : Screen()
    data class FreeGame(val selected: SelectedScene) : Screen()
    data class LearningGame(val selected: SelectedScene) : Screen()
}

@Composable
fun App(
    onThemeChanged: @Composable (isDark: Boolean) -> Unit = {}
) = AppTheme(onThemeChanged) {
    val progressRepository = remember { ProgressRepository() }
    var screen             by remember { mutableStateOf<Screen>(Screen.Home) }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(
            onFreePlay = { screen = Screen.FreePlay },
            onLearning = { screen = Screen.Learning },
            onProgress = { screen = Screen.Progress }
        )
        is Screen.Progress -> ProgressScreen(
            progressRepository = progressRepository,
            onBack             = { screen = Screen.Home }
        )
        is Screen.FreePlay -> CatalogScreen(
            onSceneSelected    = { entry, scene -> screen = Screen.FreeGame(SelectedScene(entry, scene)) },
            progressRepository = progressRepository,
            onBack             = { screen = Screen.Home }
        )
        is Screen.Learning -> LearningModeScreen(
            progressRepository = progressRepository,
            onStartScene = { entry, scene -> screen = Screen.LearningGame(SelectedScene(entry, scene)) },
            onBack = { screen = Screen.Home }
        )
        is Screen.FreeGame -> GameScreen(
            scene              = s.selected.scene,
            onBack             = { screen = Screen.FreePlay },
            progressRepository = progressRepository,
            completionActions  = listOf(
                CompletionAction("Ещё раз", icon = "🔄", primary = true) {
                    screen = Screen.FreeGame(s.selected)
                },
                CompletionAction("В каталог", icon = "🏠") { screen = Screen.FreePlay }
            )
        )
        is Screen.LearningGame -> GameScreen(
            scene              = s.selected.scene,
            onBack             = { screen = Screen.Learning },
            progressRepository = progressRepository,
            completionActions  = listOf(
                CompletionAction("Следующая сцена", icon = "▶", primary = true) { screen = Screen.Learning }
            ),
            onCompleted = {
                val childId = progressRepository.getChildName().ifBlank { "default_child" }
                applyLearningSceneCompletion(
                    progressRepository = progressRepository,
                    childId = childId,
                    entry = s.selected.entry,
                    scene = s.selected.scene
                )
            }
        )
    }
}

@Composable
private fun HomeScreen(
    onFreePlay: () -> Unit,
    onLearning: () -> Unit,
    onProgress: () -> Unit
) {
    val tts = rememberTtsPlayer()
    Box(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Mimi",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D3557)
            )

            ModeButton(
                title = "Играть",
                subtitle = "Выбрать игру",
                icon = "🎮",
                accent = Color(0xFF457B9D),
                onClick = { tts.speak("Играть"); onFreePlay() }
            )
            ModeButton(
                title = "Учиться",
                subtitle = "Подберём игру",
                icon = "📚",
                accent = Color(0xFF2A9D8F),
                onClick = { tts.speak("Учиться"); onLearning() }
            )
            ModeButton(
                title = "Награды",
                subtitle = "Что я уже умею",
                icon = "🏆",
                accent = Color(0xFFE76F51),
                onClick = { tts.speak("Награды"); onProgress() }
            )
        }
    }
}

@Composable
private fun ModeButton(title: String, subtitle: String, icon: String, accent: Color, onClick: () -> Unit) {
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
                Text(icon, fontSize = 60.sp)
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
            Text("›", fontSize = 44.sp, color = accent, fontWeight = FontWeight.Bold)
        }
    }
}
