package org.ollu.mini.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val BG = Color(0xFFF5F7FA)
private val INK = Color(0xFF1D3557)
private val MUTED = Color(0xFF687381)

/**
 * Where the recordings came from.
 *
 * An adult's screen, not a child's: it exists because most of the animal
 * voices are Creative Commons recordings whose licence asks that the person
 * who made them be named wherever they are heard. Keeping that in a file in
 * the repository would credit the developers, not the users of the app.
 */
@Composable
fun CreditsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(BG).safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("⬅  Назад", color = MUTED) }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(Modifier.padding(bottom = 6.dp)) {
                    Text(
                        "Голоса животных",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = INK
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Записи с Wikimedia Commons. Длинные записи обрезаны до пяти секунд.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MUTED
                    )
                }
            }
            items(ANIMAL_SOUND_CREDITS) { credit ->
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(
                            credit.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = INK
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(credit.title, style = MaterialTheme.typography.bodyMedium, color = INK)
                        Text(
                            "${credit.author} · ${credit.licence}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MUTED
                        )
                        Text(
                            credit.page,
                            style = MaterialTheme.typography.bodySmall,
                            color = MUTED
                        )
                    }
                }
            }
        }
    }
}
