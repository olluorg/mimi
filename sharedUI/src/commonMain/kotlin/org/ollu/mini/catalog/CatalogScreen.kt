package org.ollu.mini.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mimi.core.world.SceneDefinition
import org.ollu.mini.progress.ProgressRepository

private val BG = Color(0xFFF5F7FA)

private val skillLabel = mapOf(
    "attention"             to "Внимание",
    "visual_attention"      to "Зрит. внимание",
    "selective_attention"   to "Избир. внимание",
    "sustained_attention"   to "Устойч. внимание",
    "fine_motor"            to "Мелкая моторика",
    "hand_eye_coordination" to "Глаз-рука",
    "gesture_control"       to "Жесты",
    "trace_control"         to "Обводка",
    "matching"              to "Соответствие",
    "categorization"        to "Категоризация",
    "sequencing"            to "Последовательность",
    "memory"                to "Память",
    "working_memory"        to "Рабочая память",
    "visual_memory"         to "Зрит. память",
    "sequential_memory"     to "Послед. память",
    "associative_memory"    to "Ассоц. память",
    "problem_solving"       to "Решение задач",
    "receptive_speech"      to "Понимание речи",
    "expressive_speech"     to "Активная речь",
    "word_association"      to "Ассоциации",
    "phonemic_awareness"    to "Фонем. слух",
    "color_recognition"     to "Цвета",
    "shape_recognition"     to "Формы",
    "size_comparison"       to "Размеры",
    "pattern_recognition"   to "Паттерны",
    "numbers"               to "Цифры",
    "counting"              to "Счёт",
    "letters"               to "Буквы",
    "reading_readiness"     to "Подгот. к чтению",
    "task_completion"       to "Завершение",
    "turn_taking"           to "Очерёдность",
    "patience"              to "Терпение",
    "impulse_control"       to "Контроль импульсов",
    "reaction_speed"        to "Реакция",
    "visual_planning"       to "Планирование",
    "inhibitory_control"    to "Торможение"
)

private val skillColor = mapOf(
    "attention"             to Color(0xFF5C9BD6),
    "visual_attention"      to Color(0xFF5C9BD6),
    "selective_attention"   to Color(0xFF5C9BD6),
    "sustained_attention"   to Color(0xFF5C9BD6),
    "fine_motor"            to Color(0xFFFF8A65),
    "hand_eye_coordination" to Color(0xFFFF8A65),
    "gesture_control"       to Color(0xFFFF8A65),
    "trace_control"         to Color(0xFFFF8A65),
    "matching"              to Color(0xFF9C7BB8),
    "categorization"        to Color(0xFF9C7BB8),
    "sequencing"            to Color(0xFF9C7BB8),
    "memory"                to Color(0xFF9C7BB8),
    "working_memory"        to Color(0xFF9C7BB8),
    "visual_memory"         to Color(0xFF26C6DA),
    "sequential_memory"     to Color(0xFFEF5350),
    "associative_memory"    to Color(0xFF66BB6A),
    "problem_solving"       to Color(0xFF9C7BB8),
    "receptive_speech"      to Color(0xFF66BB6A),
    "expressive_speech"     to Color(0xFF66BB6A),
    "word_association"      to Color(0xFF66BB6A),
    "phonemic_awareness"    to Color(0xFF66BB6A),
    "color_recognition"     to Color(0xFF26C6DA),
    "shape_recognition"     to Color(0xFF26C6DA),
    "size_comparison"       to Color(0xFF26C6DA),
    "pattern_recognition"   to Color(0xFF26C6DA),
    "numbers"               to Color(0xFFEF5350),
    "counting"              to Color(0xFFEF5350),
    "letters"               to Color(0xFFEF5350),
    "reading_readiness"     to Color(0xFFEF5350),
    "task_completion"       to Color(0xFFFFB300),
    "turn_taking"           to Color(0xFFFFB300),
    "patience"              to Color(0xFFFFB300),
    "impulse_control"       to Color(0xFFFFB300),
    "reaction_speed"        to Color(0xFFEF5350),
    "visual_planning"       to Color(0xFF9C7BB8),
    "inhibitory_control"    to Color(0xFFFFB300)
)

@Composable
fun CatalogScreen(
    catalogUrl:         String             = DEFAULT_CATALOG_URL,
    onSceneSelected:    (CatalogEntry, SceneDefinition) -> Unit,
    progressRepository: ProgressRepository? = null,
    onBack:             (() -> Unit)? = null
) {
    val repo = remember { SceneRepository() }
    DisposableEffect(repo) { onDispose { repo.close() } }

    var uiState        by remember { mutableStateOf(CatalogState(isRefreshing = true)) }
    var completedIds   by remember { mutableStateOf(progressRepository?.getCompletedSceneIds() ?: emptySet<String>()) }
    var selectedSkills by remember { mutableStateOf(emptySet<String>()) }
    var ageFilter      by remember { mutableStateOf(0) }  // 0=все 1=2-3 2=4-5 3=6-7

    LaunchedEffect(catalogUrl) {
        repo.catalogFlow(catalogUrl).collect { state ->
            uiState      = state
            completedIds = progressRepository?.getCompletedSceneIds() ?: emptySet()
        }
    }

    val allSkills = remember(uiState.entries) {
        uiState.entries.flatMap { it.skills }.distinct()
            .sortedBy { skillLabel[it] ?: it }
    }

    val displayedEntries = remember(uiState.entries, selectedSkills, ageFilter) {
        uiState.entries
            .let { list ->
                when (ageFilter) {
                    1    -> list.filter { it.ageMin <= 3 && it.ageMax >= 2 }
                    2    -> list.filter { it.ageMin <= 5 && it.ageMax >= 4 }
                    3    -> list.filter { it.ageMin <= 7 && it.ageMax >= 6 }
                    else -> list
                }
            }
            .let { list ->
                if (selectedSkills.isEmpty()) list
                else list.filter { entry -> entry.skills.any { it in selectedSkills } }
            }
    }

    Box(Modifier.fillMaxSize().background(BG)) {

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
                LazyColumn(
                    contentPadding      = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier            = Modifier.fillMaxSize()
                ) {
                    item { ChildNameHeader(progressRepository, onBack) }

                    if (uiState.offlineWarning) {
                        item {
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

                    item {
                        AgeFilterBar(selected = ageFilter, onSelect = { ageFilter = it })
                    }

                    if (allSkills.isNotEmpty()) {
                        item {
                            SkillFilterBar(
                                allSkills      = allSkills,
                                selectedSkills = selectedSkills,
                                onToggle       = { skill ->
                                    selectedSkills = if (skill in selectedSkills)
                                        selectedSkills - skill
                                    else
                                        selectedSkills + skill
                                },
                                onClearAll     = { selectedSkills = emptySet() }
                            )
                        }
                    }

                    items(displayedEntries) { entry ->
                        var loading   by remember(entry.id) { mutableStateOf(false) }
                        var loadError by remember(entry.id) { mutableStateOf<String?>(null) }
                        SceneCard(
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

@Composable
private fun AgeFilterBar(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("Все", "2–3 года", "4–5 лет", "6–7 лет")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            val active = selected == index
            Surface(
                onClick = { onSelect(index) },
                shape   = RoundedCornerShape(20.dp),
                color   = if (active) Color(0xFF457B9D) else Color.White,
                tonalElevation = if (active) 0.dp else 1.dp,
                shadowElevation = if (active) 0.dp else 1.dp
            ) {
                Text(
                    label,
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
                    Text("Сбросить", fontSize = 12.sp, color = Color(0xFF4A90D9))
                }
            }
        }
        Row(
            modifier              = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            allSkills.forEach { skill ->
                val selected = skill in selectedSkills
                val color    = skillColor[skill] ?: Color(0xFF888888)
                FilterChip(
                    selected = selected,
                    onClick  = { onToggle(skill) },
                    label    = {
                        Text(
                            skillLabel[skill] ?: skill,
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
private fun SceneCard(entry: CatalogEntry, loading: Boolean, completed: Boolean, error: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFE8F1FA), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (entry.icon.isNotBlank()) {
                    Text(entry.icon, fontSize = 24.sp)
                } else {
                    Text(entry.title.firstOrNull()?.uppercase() ?: "M", fontSize = 20.sp, color = Color(0xFF457B9D), fontWeight = FontWeight.Bold)
                }
                if (completed) {
                    Box(
                        modifier         = Modifier.align(Alignment.TopEnd).size(18.dp)
                            .background(Color(0xFF4CAF50), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("✓", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF263445))
                if (entry.description.isNotBlank()) {
                    Text(entry.description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF687381))
                }
                Text(
                    "Возраст ${entry.ageMin}–${entry.ageMax}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8B96A3)
                )
            }
            if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Text("›", fontSize = 28.sp, color = Color(0xFF457B9D))
        }

        if (entry.skills.isNotEmpty()) {
            SkillChips(entry.skills)
        }

        if (error != null) {
            Text(
                "⚠️ $error",
                fontSize = 11.sp,
                color    = Color(0xFFB00020),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFEBEE), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SkillChips(skills: List<String>) {
    Row(
        modifier              = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        skills.forEach { skill ->
            val color = skillColor[skill] ?: Color(0xFF888888)
            Box(
                modifier         = Modifier
                    .background(color.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    skillLabel[skill] ?: skill,
                    fontSize = 10.sp,
                    color    = color,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ChildNameHeader(progressRepository: ProgressRepository?, onBack: (() -> Unit)?) {
    var name    by remember { mutableStateOf(progressRepository?.getChildName() ?: "") }
    var editing by remember { mutableStateOf(name.isEmpty()) }

    Column(Modifier.padding(bottom = 8.dp)) {
        if (onBack != null) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("← Назад", color = Color(0xFF666666))
            }
        }
        if (editing) {
            Surface(shape = RoundedCornerShape(8.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value           = name,
                    onValueChange   = { name = it },
                    placeholder     = { Text("Имя ребёнка") },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (name.isNotBlank()) { progressRepository?.setChildName(name); editing = false }
                    }),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (name.isNotBlank()) { progressRepository?.setChildName(name); editing = false }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) { Text("OK") }
            }
            }
        } else {
            Surface(shape = RoundedCornerShape(8.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Привет, $name",
                            style      = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF263445)
                        )
                        Text("Свободный режим", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF687381))
                    }
                    TextButton(onClick = { editing = true }) { Text("Изменить", color = Color(0xFF457B9D)) }
                }
            }
        }
    }
}
