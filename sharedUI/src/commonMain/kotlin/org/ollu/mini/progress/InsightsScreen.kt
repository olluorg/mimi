package org.ollu.mini.progress

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ollu.mini.catalog.CatalogState
import org.ollu.mini.catalog.DEFAULT_CATALOG_URL
import org.ollu.mini.catalog.SceneRepository
import org.ollu.mini.skills.skillColorOf
import org.ollu.mini.skills.skillLabelOf
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val BG = Color(0xFFF5F7FA)
private val INK = Color(0xFF1D3557)
private val MUTED = Color(0xFF687381)

/**
 * The adult view: what the child has actually been doing, and where they are
 * stuck. Deliberately separate from the child's reward screen — this one is
 * dense, text-heavy and reports difficulty honestly.
 */
@OptIn(ExperimentalTime::class)
@Composable
fun InsightsScreen(
    progressRepository: ProgressRepository,
    onBack: () -> Unit,
    catalogUrl: String = DEFAULT_CATALOG_URL
) {
    val repo = remember { SceneRepository() }
    DisposableEffect(repo) { onDispose { repo.close() } }

    var catalog by remember { mutableStateOf(CatalogState()) }
    LaunchedEffect(catalogUrl) {
        repo.catalogFlow(catalogUrl).collect { catalog = it }
    }

    val childId = remember { progressRepository.currentChildId() }
    val sessions = remember(childId) { progressRepository.getSessions(childId) }
    val now = remember { Clock.System.now().toEpochMilliseconds() }
    val insights = remember(sessions, now) { buildProgressInsights(sessions, now) }
    val skills = remember(childId) { progressRepository.getSkillState(childId) }

    val titleOf: (String) -> String = { sceneId ->
        catalog.entries.firstOrNull { it.id == sceneId }?.title?.takeIf { it.isNotBlank() } ?: sceneId
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
                Card {
                    Text(
                        "Для взрослых",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = INK
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        progressRepository.currentProfile()?.name ?: "Профиль ребёнка",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MUTED
                    )
                }
            }

            if (insights.isEmpty) {
                item {
                    Card {
                        Text(
                            "Пока нет сыгранных сессий. Как только ребёнок пройдёт первую игру, " +
                                "здесь появится статистика и подсказки, над чем стоит поработать.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MUTED
                        )
                    }
                }
                return@LazyColumn
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat(Modifier.weight(1f), insights.sessionsLast7Days.toString(), "За неделю", Color(0xFF457B9D))
                    Stat(Modifier.weight(1f), insights.totalSessions.toString(), "Всего игр", Color(0xFF2A9D8F))
                    Stat(Modifier.weight(1f), formatDuration(insights.totalTimeMs), "Время", Color(0xFFE76F51))
                }
            }

            insights.speech?.let { speech ->
                item { SpeechCard(speech) }
            }

            if (insights.needsAttention.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF4D8),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Стоит поработать",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF7A5600)
                            )
                            Text(
                                "Здесь ребёнку чаще всего нужна помощь.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9A7320)
                            )
                            insights.needsAttention.forEach { scene ->
                                SceneRow(titleOf(scene.sceneId), scene, now, accent = Color(0xFF7A5600))
                            }
                        }
                    }
                }
            }

            // A choice game has no score, so it gets its own section: what the
            // child reaches for is the result, and for a non-verbal child it may
            // be the only place that preference is ever recorded.
            if (insights.choices.isNotEmpty()) {
                item {
                    Card {
                        SectionTitle("Что выбирает")
                        Text(
                            "В играх на выбор нет неправильных ответов — здесь видно, что ребёнок предпочитает.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MUTED
                        )
                        insights.choices.forEach { tally ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    titleOf(tally.sceneId),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF263445)
                                )
                                Text(
                                    tally.counts.entries.joinToString("   ") { "${it.key} — ${it.value}" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MUTED,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            if (insights.strongest.isNotEmpty()) {
                item {
                    Card {
                        SectionTitle("Уверенно получается")
                        insights.strongest.forEach { scene ->
                            SceneRow(titleOf(scene.sceneId), scene, now, accent = Color(0xFF2A9D8F))
                        }
                    }
                }
            }

            if (skills.isNotEmpty()) {
                item {
                    Card {
                        SectionTitle("Профиль навыков")
                        skills.entries.sortedByDescending { it.value }.forEach { (skill, value) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row {
                                    Text(
                                        skillLabelOf(skill),
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF263445)
                                    )
                                    Text(
                                        "${(value * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MUTED
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { value.toFloat() },
                                    modifier = Modifier.fillMaxWidth().height(7.dp),
                                    color = skillColorOf(skill),
                                    trackColor = skillColorOf(skill).copy(alpha = 0.15f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card {
                    SectionTitle("Все игры")
                    insights.perScene.forEach { scene ->
                        SceneRow(titleOf(scene.sceneId), scene, now, accent = Color(0xFF457B9D))
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF263445))
}

@Composable
private fun Stat(modifier: Modifier, value: String, label: String, accent: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, modifier = modifier) {
        Column(
            Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = accent)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MUTED, maxLines = 1)
        }
    }
}

@Composable
private fun SceneRow(title: String, scene: SceneInsight, nowEpochMs: Long, accent: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(10.dp).background(trendColor(scene.trend), CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF263445), maxLines = 1)
            Text(
                buildString {
                    append(pluralAttempts(scene.attempts))
                    append(" · ")
                    append(trendLabel(scene.trend))
                    append(" · ")
                    append(relativeTime(nowEpochMs - scene.lastAtEpochMs))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MUTED,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            helpLabel(scene.avgHelp),
            style = MaterialTheme.typography.bodySmall,
            color = accent,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun trendColor(trend: Trend): Color = when (trend) {
    Trend.IMPROVING  -> Color(0xFF2A9D8F)
    Trend.STEADY     -> Color(0xFF5C9BD6)
    Trend.STRUGGLING -> Color(0xFFE76F51)
    Trend.NEW        -> Color(0xFFBFC8D2)
}

private fun trendLabel(trend: Trend): String = when (trend) {
    Trend.IMPROVING  -> "лучше"
    Trend.STEADY     -> "ровно"
    Trend.STRUGGLING -> "тяжелее"
    Trend.NEW        -> "мало данных"
}

private fun helpLabel(avgHelp: Double): String = when {
    avgHelp < 0.05 -> "сам"
    else           -> "помощь ${formatOneDecimal(avgHelp)}"
}

private fun formatOneDecimal(value: Double): String {
    val rounded = ((value * 10).toLong())
    return "${rounded / 10}.${rounded % 10}"
}

internal fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1   -> "<1 мин"
        minutes < 60  -> "$minutes мин"
        else          -> "${minutes / 60} ч"
    }
}

internal fun pluralAttempts(count: Int): String {
    val form = russianPlural(count, "попытка", "попытки", "попыток")
    return "$count $form"
}

internal fun relativeTime(deltaMs: Long): String {
    val minutes = deltaMs / 60_000
    val hours   = minutes / 60
    val days    = hours / 24
    return when {
        minutes < 1 -> "только что"
        minutes < 60 -> "$minutes ${russianPlural(minutes.toInt(), "минуту", "минуты", "минут")} назад"
        hours < 24  -> "$hours ${russianPlural(hours.toInt(), "час", "часа", "часов")} назад"
        days < 30   -> "$days ${russianPlural(days.toInt(), "день", "дня", "дней")} назад"
        else        -> "давно"
    }
}

/** Russian needs three plural forms, picked by the last digits of the count. */
internal fun russianPlural(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    if (mod100 in 11..14) return many
    return when (count % 10) {
        1    -> one
        2, 3, 4 -> few
        else -> many
    }
}

/** One word and how it is going: a bar for how often it is answered. */
@Composable
private fun WordRow(word: WordInsight, accent: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            word.word,
            style = MaterialTheme.typography.bodyMedium,
            color = INK,
            maxLines = 1,
            modifier = Modifier.width(96.dp)
        )
        Box(
            Modifier.weight(1f).height(14.dp)
                .background(Color.Black.copy(alpha = 0.08f), RoundedCornerShape(7.dp))
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(word.share.toFloat().coerceIn(0.02f, 1f))
                    .background(
                        // Red where the child almost never answers: that is the
                        // word to make easier, not to keep drilling.
                        if (word.share >= 0.6) accent else if (word.share >= 0.3) Color(0xFFFFB300)
                        else Color(0xFFE57373),
                        RoundedCornerShape(7.dp)
                    )
            )
        }
        Text(
            "${word.answered}/${word.asked}" + if (word.exact > 0) " · точно ${word.exact}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MUTED,
            maxLines = 1
        )
    }
}

/**
 * Attempts to communicate, week on week. The one card that is about the child
 * trying rather than getting things right.
 */
@Composable
private fun SpeechCard(speech: SpeechInsight) {
    val accent = Color(0xFF43A047)
    Card {
        SectionTitle("Речь")
        Text(
            "Сколько раз ребёнок пытался сказать что-то сам в речевых играх — за эту неделю и за прошлую. " +
                "Важны не правильные слова, а то, что попыток становится больше.",
            style = MaterialTheme.typography.bodySmall,
            color = MUTED
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Short labels: the long ones were cut off mid-word in the tile.
            Stat(Modifier.weight(1f), speech.attemptsThisWeek.toString(), "Эта неделя", accent)
            Stat(Modifier.weight(1f), speech.attemptsLastWeek.toString(), "Прошлая", Color(0xFF457B9D))
            speech.answerRate?.let { rate ->
                Stat(Modifier.weight(1f), "${(rate * 100).toInt()}%", "С ответом", Color(0xFFE76F51))
            }
        }
        Text(
            when (speech.trend) {
                Trend.IMPROVING  -> "Попыток становится больше — отлично, продолжайте."
                Trend.STRUGGLING -> "Попыток меньше, чем неделей раньше. Возможно, слова пока сложные " +
                    "или ребёнок устал — вернитесь к звукоподражаниям."
                Trend.STEADY     -> "Примерно столько же попыток, сколько неделей раньше."
                Trend.NEW        -> "Пока мало данных для сравнения."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = trendColor(speech.trend)
        )
        if (speech.words.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "По словам — сложные сверху",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = INK
            )
            speech.words.take(12).forEach { word -> WordRow(word, accent) }
        }
        if (speech.recent.size >= 2) {
            val top = (speech.recent.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                speech.recent.forEach { count ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight((count.toFloat() / top).coerceIn(0.06f, 1f))
                            .background(accent.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                    )
                }
            }
            Text(
                "Последние ${speech.recent.size} ${russianPlural(speech.recent.size, "игра", "игры", "игр")}: " +
                    "микрофон услышал ${speech.heardByMic}, взрослый отметил ${speech.confirmedByAdult}" +
                    if (speech.exact > 0) ", точная гласная — ${speech.exact}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MUTED
            )
        }
    }
}
