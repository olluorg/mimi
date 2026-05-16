package org.ollu.mini.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
fun ProgressScreen(
    progressRepository: ProgressRepository,
    onBack: () -> Unit
) {
    val childName    = remember { progressRepository.getChildName().ifBlank { "Ребёнок" } }
    val childId      = remember { progressRepository.getChildName().ifBlank { "default_child" } }
    val completedIds = remember { progressRepository.getCompletedSceneIds() }
    val skills       = remember { progressRepository.getSkillState(childId) }

    val topSkills    = remember(skills) { skills.entries.sortedByDescending { it.value }.take(8) }
    val weakSkills   = remember(skills) { skills.entries.sortedBy { it.value }.take(4) }

    Box(Modifier.fillMaxSize().background(Color(0xFFF5F7FA))) {
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                    Text("← Назад", color = Color(0xFF666666))
                }
            }

            item {
                Surface(shape = RoundedCornerShape(8.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Прогресс",
                            style      = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color      = Color(0xFF1D3557)
                        )
                        Text(
                            childName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF687381)
                        )
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value    = completedIds.size.toString(),
                        label    = "Пройдено игр",
                        accent   = Color(0xFF457B9D)
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value    = skills.size.toString(),
                        label    = "Навыков развито",
                        accent   = Color(0xFF2A9D8F)
                    )
                }
            }

            if (skills.isNotEmpty()) {
                item {
                    SkillProgressCard(
                        title  = "Навыки",
                        skills = topSkills,
                        color  = Color(0xFF457B9D)
                    )
                }

                if (weakSkills.any { it.value < 0.5 }) {
                    item {
                        WeakSkillsCard(weakSkills.filter { it.value < 0.5 })
                    }
                }
            } else {
                item {
                    Surface(shape = RoundedCornerShape(8.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Ещё нет данных. Запустите несколько игр в режиме обучения — и здесь появится прогресс.",
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = Color(0xFF687381),
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, value: String, label: String, accent: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = Color.White, modifier = modifier) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement   = Arrangement.spacedBy(4.dp),
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style      = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color      = accent
            )
            Text(
                label,
                style   = MaterialTheme.typography.bodySmall,
                color   = Color(0xFF687381),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SkillProgressCard(title: String, skills: List<Map.Entry<String, Double>>, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF263445))
            skills.forEach { (skill, value) ->
                val barColor = skillColor[skill] ?: color
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text(
                            skillLabel[skill] ?: skill,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF263445)
                        )
                        Text(
                            "${(value * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF687381)
                        )
                    }
                    LinearProgressIndicator(
                        progress       = { value.toFloat() },
                        modifier       = Modifier.fillMaxWidth().height(7.dp),
                        color          = barColor,
                        trackColor     = barColor.copy(alpha = 0.15f),
                        strokeCap      = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun WeakSkillsCard(skills: List<Map.Entry<String, Double>>) {
    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFF4D8), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Стоит поработать",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color      = Color(0xFF7A5600)
            )
            skills.forEach { (skill, _) ->
                Text(
                    "• ${skillLabel[skill] ?: skill}",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = Color(0xFF7A5600),
                    fontSize = 13.sp
                )
            }
        }
    }
}
