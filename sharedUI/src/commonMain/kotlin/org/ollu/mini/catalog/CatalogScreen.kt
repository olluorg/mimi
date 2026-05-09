package org.ollu.mini.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

private val BG = Color(0xFFFFF8E1)

@Composable
fun CatalogScreen(
    catalogUrl:         String            = DEFAULT_CATALOG_URL,
    onSceneSelected:    (SceneDefinition) -> Unit,
    progressRepository: ProgressRepository? = null
) {
    val repo = remember { SceneRepository() }
    DisposableEffect(repo) { onDispose { repo.close() } }

    var uiState      by remember { mutableStateOf(CatalogState(isRefreshing = true)) }
    var completedIds by remember { mutableStateOf(progressRepository?.getCompletedSceneIds() ?: emptySet<String>()) }

    LaunchedEffect(catalogUrl) {
        repo.catalogFlow(catalogUrl).collect { state ->
            uiState      = state
            completedIds = progressRepository?.getCompletedSceneIds() ?: emptySet()
        }
    }

    Box(Modifier.fillMaxSize().background(BG)) {

        when {
            // First launch: cache is empty and still loading
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
                    item { ChildNameHeader(progressRepository) }

                    if (uiState.offlineWarning) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFFF3CD),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "📶 Нет интернета — показаны загруженные игры",
                                    style    = MaterialTheme.typography.bodySmall,
                                    color    = Color(0xFF856404),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    items(uiState.entries) { entry ->
                        var loading by remember(entry.id) { mutableStateOf(false) }
                        SceneCard(
                            entry     = entry,
                            loading   = loading,
                            completed = entry.id in completedIds,
                            onClick   = { if (!loading) loading = true }
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

                // Subtle refresh bar at top while network catalog is loading
                if (uiState.isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).height(3.dp),
                        color    = Color(0xFF4A90D9),
                        trackColor = Color.Transparent
                    )
                }
            }
        }
    }
}

@Composable
private fun ChildNameHeader(progressRepository: ProgressRepository?) {
    var name    by remember { mutableStateOf(progressRepository?.getChildName() ?: "") }
    var editing by remember { mutableStateOf(name.isEmpty()) }

    Column(Modifier.padding(bottom = 8.dp)) {
        if (editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Привет, $name! 👋",
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { editing = true }) { Text("✏️", fontSize = 18.sp) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Выбери игру", style = MaterialTheme.typography.titleMedium, color = Color(0xFF888888))
    }
}

@Composable
private fun SceneCard(entry: CatalogEntry, loading: Boolean, completed: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Text("🎮", fontSize = 40.sp)
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
            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (entry.description.isNotBlank()) {
                Text(entry.description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF888888))
            }
            Text("Возраст ${entry.ageMin}–${entry.ageMax}",
                style = MaterialTheme.typography.labelSmall, color = Color(0xFFAAAAAA))
        }
        if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        else Text("▶", fontSize = 24.sp, color = Color(0xFF4A90D9))
    }
}
