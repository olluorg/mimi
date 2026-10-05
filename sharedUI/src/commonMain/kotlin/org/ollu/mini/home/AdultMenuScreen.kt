package org.ollu.mini.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.ollu.mini.theme.AppIcon
import org.ollu.mini.theme.AppIconGlyph

/** The adult's tools, reached only through the hold-to-enter gate. */
@Composable
internal fun AdultMenuScreen(
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
