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
import org.ollu.mini.progress.AdultEntryButton
import org.ollu.mini.progress.ChildProfile
import org.ollu.mini.renderer.CatalogImage
import org.ollu.mini.sound.SystemPhrases
import org.ollu.mini.sound.rememberTtsPlayer
import org.ollu.mini.theme.AppIcon
import org.ollu.mini.theme.AppIconGlyph

/**
 * Home for a child using the app by themselves.
 *
 * One enormous play button and nothing to read. The screens that involve
 * choosing from a list, filtering by skill or reading a profile are an adult's
 * tools, so they sit behind the grown-up gate rather than competing for a
 * toddler's attention here.
 */
@Composable
internal fun HomeScreen(
    child: ChildProfile?,
    onPlay: () -> Unit,
    onProgress: () -> Unit,
    onAdult: () -> Unit,
    onSwitchChild: (() -> Unit)?
) {
    val tts = rememberTtsPlayer()
    Box(Modifier.fillMaxSize().background(Color(0xFFFFF8E1)).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            if (child != null && onSwitchChild != null) {
                // Whose turn it is, and a way to hand the tablet over — a picture,
                // because the child recognises their own animal, not their name.
                Surface(
                    onClick = onSwitchChild,
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    shadowElevation = 1.dp
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CatalogImage(
                            path = "_cards/${child.avatar}.jpg",
                            contentDescription = child.name,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            child.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1D3557)
                        )
                    }
                }
            } else {
                Text(
                    "Mimi",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D3557)
                )
            }

            Surface(
                onClick = onPlay,
                shape = CircleShape,
                color = Color(0xFF2A9D8F),
                shadowElevation = 8.dp,
                modifier = Modifier.size(220.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    AppIconGlyph(AppIcon.Play, Color.White, size = 104.dp)
                }
            }

            Surface(
                onClick = { tts.speak(SystemPhrases.REWARDS); onProgress() },
                shape = CircleShape,
                color = Color(0xFFE76F51).copy(alpha = 0.16f),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    AppIconGlyph(AppIcon.Trophy, Color(0xFFE76F51), size = 46.dp)
                }
            }

            AdultEntryButton(onClick = onAdult)
        }
    }
}
