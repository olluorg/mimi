package org.ollu.mini.progress

import mimi.core.world.SpeechVocabulary
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import org.ollu.mini.renderer.CatalogImage
import org.ollu.mini.theme.AppIcon
import org.ollu.mini.theme.AppIconGlyph

private val BG = Color(0xFFFFF8E1)
private val INK = Color(0xFF1D3557)
private val MUTED = Color(0xFF687381)

/**
 * Who is playing.
 *
 * Shown to the child, so it is pictures: they find their own animal. Names are
 * there for the adult standing behind them, not for the child to read.
 */
@Composable
fun ProfilePickerScreen(
    profiles: List<ChildProfile>,
    onPicked: (ChildProfile) -> Unit,
    onManage: (() -> Unit)? = null
) {
    Box(Modifier.fillMaxSize().background(BG).safeDrawingPadding()) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                "Кто играет?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = INK
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileTile(profile) { onPicked(profile) }
                }
            }
            if (onManage != null) {
                TextButton(onClick = onManage) {
                    Text("Настроить профили", color = MUTED)
                }
            }
        }
    }
}

@Composable
private fun ProfileTile(profile: ChildProfile, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 3.dp
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CatalogImage(
                path = "_cards/${profile.avatar}.jpg",
                contentDescription = profile.name,
                modifier = Modifier.size(104.dp)
            )
            Text(
                profile.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = INK,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/**
 * Adding, renaming and removing children. An adult's screen: it has typing in
 * it, and removing a profile throws away everything recorded under it.
 */
@Composable
fun ProfileManagerScreen(
    repository: ProgressRepository,
    onBack: () -> Unit
) {
    var profiles by remember { mutableStateOf(repository.getProfiles()) }
    var newName  by remember { mutableStateOf("") }
    var avatar   by remember { mutableStateOf(PROFILE_AVATARS.first()) }
    var confirmRemoval by remember { mutableStateOf<ChildProfile?>(null) }

    fun refresh() { profiles = repository.getProfiles() }

    Box(Modifier.fillMaxSize().background(Color(0xFFF5F7FA)).safeDrawingPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                AppIconGlyph(AppIcon.Back, MUTED, size = 18.dp)
                Text("  Назад", color = MUTED)
            }
            Text(
                "Профили детей",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = INK
            )
            Text(
                "У каждого ребёнка свой прогресс и свой подбор игр.",
                style = MaterialTheme.typography.bodyMedium,
                color = MUTED
            )

            profiles.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    onRename = { repository.renameProfile(profile.id, it); refresh() },
                    onRemove = { confirmRemoval = profile },
                    onSpeechFocus = { repository.setSpeechFocus(profile.id, it); refresh() }
                )
            }

            MicrophoneCheck(repository)

            Surface(shape = RoundedCornerShape(12.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Добавить ребёнка", fontWeight = FontWeight.SemiBold, color = INK)
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("Имя") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (newName.isNotBlank()) {
                                repository.addProfile(newName, avatar); newName = ""; refresh()
                            }
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Картинка — по ней ребёнок себя узнает", style = MaterialTheme.typography.bodySmall, color = MUTED)
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 64.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 160.dp)
                    ) {
                        items(PROFILE_AVATARS) { candidate ->
                            Surface(
                                onClick = { avatar = candidate },
                                shape = RoundedCornerShape(12.dp),
                                color = if (candidate == avatar) Color(0xFFD8E9E7) else Color(0xFFF2F0EC),
                                border = if (candidate == avatar)
                                    androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF2A9D8F)) else null
                            ) {
                                CatalogImage(
                                    path = "_cards/$candidate.jpg",
                                    contentDescription = null,
                                    modifier = Modifier.padding(6.dp).size(48.dp)
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                repository.addProfile(newName, avatar); newName = ""; refresh()
                            }
                        },
                        enabled = newName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Добавить") }
                }
            }
        }
    }

    confirmRemoval?.let { profile ->
        AlertDialog(
            onDismissRequest = { confirmRemoval = null },
            title = { Text("Удалить «${profile.name}»?") },
            text = {
                Text(
                    "Вместе с профилем удалится весь прогресс: пройденные игры, " +
                        "история занятий и профиль навыков. Это нельзя отменить."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.removeProfile(profile.id)
                    confirmRemoval = null
                    refresh()
                }) { Text("Удалить", color = Color(0xFFB00020)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoval = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun ProfileRow(
    profile: ChildProfile,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    onSpeechFocus: (List<String>) -> Unit
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
      Column {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = Color(0xFFF2F0EC)) {
                CatalogImage(
                    path = "_cards/${profile.avatar}.jpg",
                    contentDescription = null,
                    modifier = Modifier.padding(4.dp).size(44.dp)
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onRename(name) }),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { if (name.isNotBlank()) onRename(name) }) { Text("OK") }
            TextButton(onClick = onRemove) { Text("Удалить", color = Color(0xFFB00020)) }
        }
        SpeechFocusRow(profile.speechFocus, onSpeechFocus)
      }
    }
}

/**
 * What the adult knows about the child's speech. Setting it mixes speech
 * games into every sitting; leaving it empty keeps play as it was.
 */
@Composable
private fun SpeechFocusRow(focus: List<String>, onChange: (List<String>) -> Unit) {
    Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        Text(
            "Речь: отметьте, если есть — тогда в каждом занятии будут речевые игры",
            style = MaterialTheme.typography.bodySmall,
            color = MUTED
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpeechVocabulary.TARGETS.forEach { (id, label) ->
                val selected = id in focus
                FilterChip(
                    selected = selected,
                    onClick  = { onChange(if (selected) focus - id else focus + id) },
                    label    = { Text(label) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF43A047),
                        selectedLabelColor     = Color.White
                    )
                )
            }
        }
    }
}
