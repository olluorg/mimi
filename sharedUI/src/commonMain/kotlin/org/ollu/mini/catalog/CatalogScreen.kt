package org.ollu.mini.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mimi.core.world.SceneDefinition
import mimi.core.world.SpeechVocabulary
import org.ollu.mini.theme.AppIcon
import org.ollu.mini.theme.AppIconGlyph
import org.ollu.mini.progress.ProgressRepository
import org.ollu.mini.skills.skillColorOf
import org.ollu.mini.skills.skillLabelOf

private val BG = Color(0xFFF5F7FA)

@Composable
fun CatalogScreen(
    catalogUrl:         String             = DEFAULT_CATALOG_URL,
    onSceneSelected:    (CatalogEntry, SceneDefinition) -> Unit,
    progressRepository: ProgressRepository? = null,
    onBack:             (() -> Unit)? = null,
    onEntriesUpdated:   ((List<CatalogEntry>) -> Unit)? = null,
    /** Open with only the speech games shown — the «Запуск речи» entry point. */
    speechOnly:         Boolean = false
) {
    val repo = remember { SceneRepository() }
    DisposableEffect(repo) { onDispose { repo.close() } }

    var uiState        by remember { mutableStateOf(CatalogState(isRefreshing = true)) }
    var completedIds   by remember { mutableStateOf(progressRepository?.getCompletedSceneIds(progressRepository.currentChildId()) ?: emptySet<String>()) }
    var filter         by remember { mutableStateOf(CatalogFilter(speechOnly = speechOnly)) }

    LaunchedEffect(catalogUrl) {
        repo.catalogFlow(catalogUrl).collect { state ->
            uiState      = state
            completedIds = progressRepository?.getCompletedSceneIds(progressRepository.currentChildId()) ?: emptySet()
            if (state.entries.isNotEmpty()) onEntriesUpdated?.invoke(state.entries)
        }
    }

    val allSkills = remember(uiState.entries) {
        uiState.entries.flatMap { it.skills }.distinct()
            .sortedBy { skillLabelOf(it) }
    }

    val displayedEntries = remember(uiState.entries, filter) { filter.apply(uiState.entries) }

    Box(Modifier.fillMaxSize().background(BG).safeDrawingPadding()) {

        when {
            uiState.entries.isEmpty() && uiState.isRefreshing -> {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color(0xFF4A90D9))
                    Spacer(Modifier.height(16.dp))
                    Text("Загружаем игры…", color = Color(0xFF666666))
                }
            }

            uiState.entries.isNotEmpty() -> {
                LazyVerticalGrid(
                    columns             = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding      = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier            = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ChildNameHeader(progressRepository, onBack)
                    }

                    if (uiState.offlineWarning) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Surface(
                                shape    = RoundedCornerShape(8.dp),
                                color    = Color(0xFFFFF4D8),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Нет интернета: показаны загруженные игры",
                                    style    = MaterialTheme.typography.bodySmall,
                                    color    = Color(0xFF7A5600),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SpeechFilterBar(
                            enabled  = filter.speechOnly,
                            target   = filter.speechTarget,
                            onToggle = { filter = filter.copy(speechOnly = !filter.speechOnly, speechTarget = null) },
                            onTarget = { filter = filter.copy(speechTarget = it) }
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AgeFilterBar(selected = filter.age, onSelect = { filter = filter.copy(age = it) })
                    }

                    if (allSkills.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SkillFilterBar(
                                allSkills      = allSkills,
                                selectedSkills = filter.skills,
                                onToggle       = { skill ->
                                    val skills = filter.skills
                                    filter = filter.copy(skills = if (skill in skills) skills - skill else skills + skill)
                                },
                                onClearAll     = { filter = filter.copy(skills = emptySet()) }
                            )
                        }
                    }

                    items(displayedEntries, key = { it.id }) { entry ->
                        var loading   by remember(entry.id) { mutableStateOf(false) }
                        var loadError by remember(entry.id) { mutableStateOf<String?>(null) }
                        SceneTile(
                            entry     = entry,
                            loading   = loading,
                            completed = entry.id in completedIds,
                            error     = loadError,
                            onClick   = { if (!loading) { loading = true; loadError = null } }
                        )
                        if (loading) {
                            LaunchedEffect(entry.id) {
                                try {
                                    val scene = repo.fetchScene(entry)
                                    onSceneSelected(entry, scene)
                                } catch (e: Exception) {
                                    loading   = false
                                    loadError = e.message ?: e::class.simpleName ?: "Неизвестная ошибка"
                                }
                            }
                        }
                    }
                }

                if (uiState.isRefreshing) {
                    LinearProgressIndicator(
                        modifier   = Modifier.fillMaxWidth().align(Alignment.TopCenter).height(3.dp),
                        color      = Color(0xFF4A90D9),
                        trackColor = Color.Transparent
                    )
                }
            }
        }
    }
}

private val SPEECH_ACCENT = Color(0xFF43A047)

/**
 * The switch to speech games only, and — once on — who they are for.
 *
 * Kept apart from the skill chips: those describe what a game touches, this
 * one is a promise that the game gets the child to communicate, and only
 * scenes whose speech mechanic the validator has checked are behind it.
 */
@Composable
private fun SpeechFilterBar(
    enabled:  Boolean,
    target:   String?,
    onToggle: () -> Unit,
    onTarget: (String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onToggle,
            shape   = RoundedCornerShape(20.dp),
            color   = if (enabled) SPEECH_ACCENT else Color.White,
            shadowElevation = if (enabled) 0.dp else 1.dp
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppIconGlyph(AppIcon.Mic, if (enabled) Color.White else SPEECH_ACCENT, size = 20.dp)
                Text(
                    "Запуск речи",
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) Color.White else SPEECH_ACCENT
                )
            }
        }
        if (enabled) {
            Text(
                "Игры, где ребёнок говорит, повторяет или просит. Играйте рядом: " +
                    "произносите слово вместе с игрой и ждите ответа.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF5F6B78)
            )
            Row(
                modifier              = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SPEECH_ACCENT,
                    selectedLabelColor     = Color.White
                )
                FilterChip(
                    selected = target == null,
                    onClick  = { onTarget(null) },
                    label    = { Text("Все", fontSize = 12.sp) },
                    colors   = chipColors
                )
                SpeechVocabulary.TARGETS.forEach { (id, label) ->
                    FilterChip(
                        selected = target == id,
                        onClick  = { onTarget(if (target == id) null else id) },
                        label    = { Text(label, fontSize = 12.sp) },
                        colors   = chipColors
                    )
                }
            }
        }
    }
}

@Composable
private fun AgeFilterBar(selected: AgeBand, onSelect: (AgeBand) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AgeBand.entries.forEach { band ->
            val active = selected == band
            Surface(
                onClick = { onSelect(band) },
                shape   = RoundedCornerShape(20.dp),
                color   = if (active) Color(0xFF457B9D) else Color.White,
                tonalElevation = if (active) 0.dp else 1.dp,
                shadowElevation = if (active) 0.dp else 1.dp
            ) {
                Text(
                    band.label,
                    fontSize = 12.sp,
                    color    = if (active) Color.White else Color(0xFF687381),
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SkillFilterBar(
    allSkills:      List<String>,
    selectedSkills: Set<String>,
    onToggle:       (String) -> Unit,
    onClearAll:     () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Фильтр по навыкам",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF888888)
            )
            if (selectedSkills.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick        = onClearAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier       = Modifier.height(24.dp)
                ) {
                    Text("✕  Сбросить", fontSize = 12.sp, color = Color(0xFF4A90D9))
                }
            }
        }
        Row(
            modifier              = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            allSkills.forEach { skill ->
                val selected = skill in selectedSkills
                val color    = skillColorOf(skill)
                FilterChip(
                    selected = selected,
                    onClick  = { onToggle(skill) },
                    label    = {
                        Text(
                            skillLabelOf(skill),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor  = color,
                        selectedLabelColor      = Color.White
                    )
                )
            }
        }
    }
}

@Composable
private fun SceneTile(entry: CatalogEntry, loading: Boolean, completed: Boolean, error: String?, onClick: () -> Unit) {
    val accent = entry.skills.firstOrNull()?.let { skillColorOf(it) } ?: Color(0xFF457B9D)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .background(Color.White, RoundedCornerShape(20.dp))
            // No title read aloud: the game opens straight into its own spoken
            // instruction, and the two voices landed on top of each other.
            .clickable(enabled = !loading, onClick = onClick)
            .padding(12.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(accent.copy(alpha = 0.14f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (entry.icon.isNotBlank()) {
                Text(entry.icon, fontSize = 64.sp)
            } else {
                Text(
                    entry.title.firstOrNull()?.uppercase() ?: "M",
                    fontSize   = 48.sp,
                    color      = accent,
                    fontWeight = FontWeight.Bold
                )
            }
            if (completed) {
                Box(
                    modifier         = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .background(Color(0xFF4CAF50), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("✓", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold) }
            }
            if (entry.isSpeechGame) {
                Box(
                    modifier         = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(28.dp)
                        .background(SPEECH_ACCENT, CircleShape),
                    contentAlignment = Alignment.Center
                ) { AppIconGlyph(AppIcon.Mic, Color.White, size = 18.dp) }
            }
            if (loading) {
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center).size(36.dp),
                    strokeWidth = 3.dp,
                    color = accent
                )
            }
        }
        Text(
            entry.title,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color      = Color(0xFF263445),
            textAlign  = TextAlign.Center,
            maxLines   = 2,
            modifier   = Modifier.fillMaxWidth()
        )
        Text(
            "${entry.ageMin}–${entry.ageMax} лет",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF8B96A3)
        )
        entry.speech?.let { speech ->
            val stage = SpeechVocabulary.STAGES[speech.stage]
            val words = speech.words.take(4).joinToString(", ")
            Text(
                listOfNotNull(stage, words.ifBlank { null }).joinToString(" · "),
                style     = MaterialTheme.typography.labelSmall,
                color     = SPEECH_ACCENT,
                textAlign = TextAlign.Center,
                maxLines  = 2
            )
        }
        if (error != null) {
            Text(
                "⚠️ $error",
                fontSize = 11.sp,
                color    = Color(0xFFB00020),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFEBEE), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ChildNameHeader(progressRepository: ProgressRepository?, onBack: (() -> Unit)?) {
    // Naming and switching children lives in the grown-up menu now; this header
    // only says whose catalogue is on screen.
    val name = progressRepository?.currentProfile()?.name
    Column(Modifier.padding(bottom = 8.dp)) {
        if (onBack != null) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("⬅  Назад", color = Color(0xFF666666))
            }
        }
        if (!name.isNullOrBlank()) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1D3557),
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
    }
}
