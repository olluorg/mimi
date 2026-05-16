package org.ollu.mini

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
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
import org.ollu.mini.catalog.SceneRepository
import mimi.core.world.SceneDefinition
import org.ollu.mini.catalog.CatalogScreen
import org.ollu.mini.game.CompletionAction
import org.ollu.mini.game.GameScreen
import org.ollu.mini.learning.LearningModeScreen
import org.ollu.mini.learning.applyLearningSceneCompletion
import org.ollu.mini.progress.ProgressRepository
import org.ollu.mini.progress.ProgressScreen
import org.ollu.mini.theme.AppTheme

private fun findSimilar(current: CatalogEntry, all: List<CatalogEntry>): CatalogEntry? =
    all.filter { it.id != current.id && it.skills.isNotEmpty() }
        .maxByOrNull { candidate -> candidate.skills.count { it in current.skills } }
        ?.takeIf { it.skills.any { s -> s in current.skills } }

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
    val fetchRepo          = remember { SceneRepository() }
    var catalogEntries     by remember { mutableStateOf(emptyList<CatalogEntry>()) }
    var screen             by remember { mutableStateOf<Screen>(Screen.Home) }

    DisposableEffect(fetchRepo) { onDispose { fetchRepo.close() } }

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
            onBack             = { screen = Screen.Home },
            onEntriesUpdated   = { catalogEntries = it }
        )
        is Screen.Learning -> LearningModeScreen(
            progressRepository = progressRepository,
            onStartScene = { entry, scene -> screen = Screen.LearningGame(SelectedScene(entry, scene)) },
            onBack = { screen = Screen.Home }
        )
        is Screen.FreeGame -> {
            val scope = rememberCoroutineScope()
            var nextSimilar by remember(s.selected.entry.id) { mutableStateOf<SelectedScene?>(null) }
            GameScreen(
                scene              = s.selected.scene,
                onBack             = { screen = Screen.FreePlay },
                progressRepository = progressRepository,
                onCompleted        = {
                    val similar = findSimilar(s.selected.entry, catalogEntries)
                    if (similar != null) {
                        scope.launch {
                            runCatching {
                                val def = fetchRepo.fetchScene(similar)
                                nextSimilar = SelectedScene(similar, def)
                            }
                        }
                    }
                },
                completionActions  = buildList {
                    add(CompletionAction("Ещё раз", primary = true) {
                        screen = Screen.FreeGame(s.selected)
                    })
                    nextSimilar?.let { next ->
                        add(CompletionAction("Следующая похожая") {
                            screen = Screen.FreeGame(next)
                        })
                    }
                    add(CompletionAction("В каталог") { screen = Screen.FreePlay })
                }
            )
        }
        is Screen.LearningGame -> GameScreen(
            scene              = s.selected.scene,
            onBack             = { screen = Screen.Learning },
            progressRepository = progressRepository,
            completionActions  = listOf(
                CompletionAction("Следующая сцена", primary = true) { screen = Screen.Learning }
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
            ModeButton(
                title = "Прогресс",
                subtitle = "Статистика пройденных игр и развитых навыков",
                accent = Color(0xFFE76F51),
                onClick = onProgress
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
