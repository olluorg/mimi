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
import org.ollu.mini.skills.skillColorOf
import org.ollu.mini.skills.skillLabelOf

@Composable
fun ProgressScreen(
    progressRepository: ProgressRepository,
    onBack: () -> Unit
) {
    val childName    = remember { progressRepository.currentProfile()?.name ?: "Ребёнок" }
    val childId      = remember { progressRepository.currentChildId() }
    val completedIds = remember { progressRepository.getCompletedSceneIds(childId) }
    val skills       = remember { progressRepository.getSkillState(childId) }

    val topSkills    = remember(skills) { skills.entries.sortedByDescending { it.value }.take(8) }
    val weakSkills   = remember(skills) { skills.entries.sortedBy { it.value }.take(4) }

    Box(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).safeDrawingPadding()) {
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                    Text("⬅  Назад", color = Color(0xFF666666))
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
                val barColor = skillColorOf(skill)
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
                    "• ${skillLabelOf(skill)}",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = Color(0xFF7A5600),
                    fontSize = 13.sp
                )
            }
        }
    }
}
