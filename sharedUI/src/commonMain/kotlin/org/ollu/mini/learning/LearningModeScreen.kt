package org.ollu.mini.learning

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import mimi.core.curriculum.BalancedSkillPlanner
import mimi.core.curriculum.ChildSkillState
import mimi.core.curriculum.DefaultSkillMapper
import mimi.core.curriculum.SceneMetrics
import mimi.core.engine.SessionMetrics
import mimi.core.curriculum.SceneResult
import mimi.core.curriculum.SceneSkillMapping
import mimi.core.world.SceneDefinition
import org.ollu.mini.catalog.CatalogEntry
import org.ollu.mini.catalog.CatalogState
import org.ollu.mini.catalog.DEFAULT_CATALOG_URL
import org.ollu.mini.catalog.SceneRepository
import org.ollu.mini.progress.ProgressRepository
import mimi.core.curriculum.CurriculumContext
import mimi.core.curriculum.SceneCatalogEntry as PlannerSceneEntry

private val BG = Color(0xFFF5F7FA)

@Composable
fun LearningModeScreen(
    progressRepository: ProgressRepository,
    onStartScene: (CatalogEntry, SceneDefinition) -> Unit,
    onBack: () -> Unit,
    catalogUrl: String = DEFAULT_CATALOG_URL
) {
    val repo = remember { SceneRepository() }
    DisposableEffect(repo) { onDispose { repo.close() } }

    val scope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf(CatalogState(isRefreshing = true)) }
    var loadingNext by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(catalogUrl) {
        repo.catalogFlow(catalogUrl).collect { uiState = it }
    }

    val childId = progressRepository.currentChildId()
    val skillState = remember(uiState.entries, childId) {
        ChildSkillState(childId = childId, skills = progressRepository.getSkillState(childId))
    }

    val plannerEntry = remember(uiState.entries, skillState) {
        selectNextEntry(uiState.entries, progressRepository, skillState)
    }

    Box(Modifier.fillMaxSize().background(BG).safeDrawingPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                    Text("⬅  Назад", color = Color(0xFF666666))
                }
                Surface(shape = RoundedCornerShape(8.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Режим обучения",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D3557)
                        )
                        Text(
                            "Следующая сцена выбирается по текущему профилю навыков.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF687381)
                        )
                    }
                }
            }

            item {
                ProfileCard(skillState)
            }

            item {
                NextSceneCard(
                    entry = plannerEntry,
                    loading = loadingNext || uiState.isRefreshing,
                    error = loadError,
                    onStart = {
                        val entry = plannerEntry ?: return@NextSceneCard
                        loadingNext = true
                        loadError = null
                        scope.launch {
                            try {
                                onStartScene(entry, repo.fetchScene(entry))
                            } catch (e: Exception) {
                                loadError = e.message ?: e::class.simpleName ?: "Не удалось загрузить сцену"
                            } finally {
                                loadingNext = false
                            }
                        }
                    }
                )
            }
        }

        if (uiState.isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).height(3.dp),
                color = Color(0xFF4A90D9),
                trackColor = Color.Transparent
            )
        }
    }
}

/**
 * Folds a finished attempt into the child's skill profile.
 *
 * The attempt is scored on what it actually cost — mistakes made and hints
 * taken — so a scene solved unaided moves the profile further than one the
 * child was walked through.
 */
fun applyLearningSceneCompletion(
    progressRepository: ProgressRepository,
    childId: String,
    entry: CatalogEntry,
    scene: SceneDefinition,
    metrics: SessionMetrics
) {
    val current = ChildSkillState(childId = childId, skills = progressRepository.getSkillState(childId))
    val weights = sceneSkillWeights(entry.skills)
    if (weights.isEmpty()) return

    val attempts = progressRepository.getSessions(childId).count { it.sceneId == scene.sceneId }

    val result = DefaultSkillMapper().updateSkills(
        state = current,
        result = SceneResult(
            sceneId = scene.sceneId,
            completed = true,
            metrics = SceneMetrics(
                attempts   = attempts.coerceAtLeast(1),
                mistakes   = metrics.mistakes,
                hintsUsed  = metrics.hintsUsed,
                durationMs = metrics.durationMs
            )
        ),
        mapping = SceneSkillMapping(sceneId = scene.sceneId, skillWeights = weights)
    )
    progressRepository.setSkillState(childId, result.newState.skills)
}

private fun selectNextEntry(
    entries: List<CatalogEntry>,
    progressRepository: ProgressRepository,
    skillState: ChildSkillState
): CatalogEntry? {
    val completedIds = progressRepository.getCompletedSceneIds(progressRepository.currentChildId())
    val plannerEntries = entries
        .filter { it.skills.isNotEmpty() }
        .map { PlannerSceneEntry(it.id, sceneSkillWeights(it.skills), it.requires) }

    val nextId = BalancedSkillPlanner().selectNextScene(
        CurriculumContext(
            childState = skillState,
            availableScenes = plannerEntries,
            recentSceneIds = completedIds.toList(),
            completedSceneCount = completedIds.size,
            completedSceneIds = completedIds
        )
    ) ?: return entries.firstOrNull()

    return entries.firstOrNull { it.id == nextId }
}

private fun sceneSkillWeights(skills: List<String>): Map<String, Double> {
    val unique = skills.distinct()
    return unique.associateWith { 1.0 }
}

@Composable
private fun ProfileCard(skillState: ChildSkillState) {
    Surface(shape = RoundedCornerShape(8.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(Color(0xFFE8F1FA), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(14.dp).background(Color(0xFF457B9D), RoundedCornerShape(4.dp)))
                }
                Spacer(Modifier.width(10.dp))
                Text("Профиль навыков", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (skillState.skills.isEmpty()) {
                Text("Навыки ещё не накоплены. Первая сцена выберется из доступного каталога.", color = Color(0xFF687381))
            } else {
                skillState.skills.entries.sortedBy { it.value }.take(5).forEach { (skill, value) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row {
                            Text(skill, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Color(0xFF263445))
                            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Color(0xFF687381))
                        }
                        LinearProgressIndicator(
                            progress = { value.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(7.dp),
                            color = Color(0xFF4A90D9),
                            trackColor = Color(0xFFE5EAF0)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NextSceneCard(
    entry: CatalogEntry?,
    loading: Boolean,
    error: String?,
    onStart: () -> Unit
) {
    Surface(shape = RoundedCornerShape(24.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Следующая игра",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF687381)
            )
            if (entry == null) {
                Text("Каталог пока пуст.", color = Color(0xFF687381))
            } else {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .background(Color(0xFFE5F5F1), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (entry.icon.isNotBlank()) {
                        Text(entry.icon, fontSize = 96.sp)
                    } else {
                        Text(
                            entry.title.firstOrNull()?.uppercase() ?: "M",
                            fontSize = 72.sp,
                            color = Color(0xFF2A9D8F),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    entry.title.ifBlank { entry.id },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF263445),
                    textAlign = TextAlign.Center
                )
                Surface(
                    onClick = {
                        // Silent on purpose — the game speaks its own instruction.
                        if (!loading) onStart()
                    },
                    shape = CircleShape,
                    color = Color(0xFF2A9D8F),
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        if (loading) {
                            CircularProgressIndicator(
                                Modifier.size(40.dp),
                                strokeWidth = 3.dp,
                                color = Color.White
                            )
                        } else {
                            Text("▶", fontSize = 48.sp, color = Color.White)
                        }
                    }
                }
                Text(
                    "Нажми, чтобы играть",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF687381)
                )
            }
            if (error != null) {
                Text(error, color = Color(0xFFB00020), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
