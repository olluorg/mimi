package org.ollu.mini.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mimi.core.world.SceneDefinition
import org.ollu.mini.scenes.DemoScene

private val BG = Color(0xFFFFF8E1)

@Composable
fun CatalogScreen(
    catalogUrl: String           = DEFAULT_CATALOG_URL,
    onSceneSelected: (SceneDefinition) -> Unit
) {
    val repo = remember { SceneRepository() }
    DisposableEffect(repo) { onDispose { repo.close() } }

    var state by remember { mutableStateOf<CatalogUiState>(CatalogUiState.Loading) }

    LaunchedEffect(catalogUrl) {
        state = CatalogUiState.Loading
        state = try {
            val catalog = repo.fetchCatalog(catalogUrl)
            CatalogUiState.Ready(catalog.scenes)
        } catch (e: Exception) {
            CatalogUiState.Error(e.message ?: "Network error")
        }
    }

    Box(
        Modifier.fillMaxSize().background(BG),
        contentAlignment = Alignment.Center
    ) {
        when (val s = state) {
            is CatalogUiState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFF4A90D9))
                Spacer(Modifier.height(16.dp))
                Text("Loading scenes…", color = Color(0xFF666666))
            }

            is CatalogUiState.Error -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text("⚠️", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Couldn't load catalog",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF666666)
                )
                Text(
                    s.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF999999),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { onSceneSelected(DemoScene.scene) },
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("🎮  Play Demo Scene")
                }
            }

            is CatalogUiState.Ready -> {
                if (s.entries.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No scenes available", color = Color(0xFF666666))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { onSceneSelected(DemoScene.scene) }) {
                            Text("🎮  Play Demo Scene")
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text(
                                "Choose a game",
                                style     = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                modifier  = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(s.entries) { entry ->
                            var loading by remember(entry.id) { mutableStateOf(false) }
                            SceneCard(
                                entry   = entry,
                                loading = loading,
                                onClick = {
                                    if (!loading) {
                                        loading = true
                                        // launch handled in LaunchedEffect below
                                    }
                                }
                            )
                            if (loading) {
                                LaunchedEffect(entry.id) {
                                    try {
                                        val scene = repo.fetchScene(entry)
                                        onSceneSelected(scene)
                                    } catch (e: Exception) {
                                        loading = false
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SceneCard(entry: CatalogEntry, loading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🎮", fontSize = 40.sp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (entry.description.isNotBlank()) {
                Text(entry.description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF888888))
            }
            Text("Ages ${entry.ageMin}–${entry.ageMax}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFAAAAAA))
        }
        if (loading) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Text("▶", fontSize = 24.sp, color = Color(0xFF4A90D9))
        }
    }
}

private sealed class CatalogUiState {
    object Loading : CatalogUiState()
    data class Ready(val entries: List<CatalogEntry>) : CatalogUiState()
    data class Error(val message: String) : CatalogUiState()
}
