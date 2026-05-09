package org.ollu.mini

import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.ollu.mini.catalog.CatalogEntry
import mimi.core.world.SceneDefinition
import org.ollu.mini.catalog.CatalogScreen
import org.ollu.mini.game.GameScreen
import org.ollu.mini.learning.LearningModeScreen
import org.ollu.mini.learning.applyLearningSceneCompletion
import org.ollu.mini.progress.ProgressRepository
import org.ollu.mini.theme.AppTheme

private data class SelectedScene(
    val entry: CatalogEntry,
    val scene: SceneDefinition
)

private sealed class Screen {
    object Home : Screen()
    object FreePlay : Screen()
    object Learning : Screen()
    data class FreeGame(val selected: SelectedScene) : Screen()
    data class LearningGame(val selected: SelectedScene) : Screen()
}

@Composable
fun App(
    onThemeChanged: @Composable (isDark: Boolean) -> Unit = {}
) = AppTheme(onThemeChanged) {
    val progressRepository = remember { ProgressRepository() }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(
            onFreePlay = { screen = Screen.FreePlay },
            onLearning = { screen = Screen.Learning }
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
            progressRepository = progressRepository
        )
        is Screen.LearningGame -> GameScreen(
            scene              = s.selected.scene,
            onBack             = { screen = Screen.Learning },
            progressRepository = progressRepository,
            completedActionText = "Следующая сцена",
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
    onLearning: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                tonalElevation = 1.dp,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Mimi",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D3557)
                    )
                    Text(
                        "Развивающие сцены и персональный маршрут занятий",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF5F6B7A)
                    )
                }
            }

            ModeButton(
                title = "Свободный режим",
                subtitle = "Открывайте каталог и запускайте любую сцену вручную",
                accent = Color(0xFF457B9D),
                onClick = onFreePlay
            )
            ModeButton(
                title = "Режим обучения",
                subtitle = "Система подбирает следующую сцену по профилю навыков",
                accent = Color(0xFF2A9D8F),
                onClick = onLearning
            )
        }
    }
}

@Composable
private fun ModeButton(title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .background(accent, RoundedCornerShape(4.dp))
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF687381))
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = accent)
        }
    }
}
