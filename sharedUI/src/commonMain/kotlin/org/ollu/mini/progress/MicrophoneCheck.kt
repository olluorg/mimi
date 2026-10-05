package org.ollu.mini.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.ollu.mini.voice.PlaybackGuard
import org.ollu.mini.voice.VoiceAnalysis
import org.ollu.mini.voice.VoiceDetector
import org.ollu.mini.voice.rememberMicrophoneLevels

private val SPEECH_GREEN = Color(0xFF43A047)
private val CHECK_INK = Color(0xFF1D3557)
private val CHECK_MUTED = Color(0xFF687381)

/**
 * Lets the adult tune how easily the microphone counts a sound, and see it work.
 *
 * The threshold that separates a child's voice from the room cannot be right
 * everywhere: a whisper in a quiet flat and a shout in a busy classroom sit at
 * opposite ends. Rather than guess, the adult says «а-а» at the tablet, watches
 * the bar cross the line, and moves the slider until it counts what it should.
 */
@Composable
fun MicrophoneCheck(repository: ProgressRepository) {
    val mic = rememberMicrophoneLevels()
    val scope = rememberCoroutineScope()
    var sensitivity by remember { mutableStateOf(repository.micSensitivity) }
    var testing by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(!mic.isSupported) }
    var level by remember { mutableStateOf(0f) }
    var threshold by remember { mutableStateOf(0f) }
    var heard by remember { mutableStateOf(0) }
    var lastVowel by remember { mutableStateOf<String?>(null) }

    DisposableEffect(testing, sensitivity) {
        if (testing && mic.isSupported) {
            val detector = VoiceDetector.forSensitivity(sensitivity)
            threshold = detector.threshold
            mic.start(
                onFrame = { samples, rate ->
                    val frame = VoiceAnalysis.analyze(samples, samples.size, rate)
                    scope.launch {
                        level = frame.loudness
                        val out = detector.feed(frame, PlaybackGuard.nowMs())
                        threshold = detector.voicedThreshold
                        out.filterIsInstance<VoiceDetector.Output.Utterance>().forEach { said ->
                            heard++
                            lastVowel = said.vowel
                        }
                    }
                },
                onUnavailable = { scope.launch { unavailable = true; testing = false } }
            )
        }
        onDispose { mic.stop(); level = 0f }
    }

    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Микрофон для речевых игр", fontWeight = FontWeight.SemiBold, color = CHECK_INK)
            if (unavailable) {
                Text(
                    "Микрофон недоступен на этом устройстве или нет разрешения. В речевых играх " +
                        "отмечайте попытки ребёнка кнопкой «Сказал(а)!».",
                    style = MaterialTheme.typography.bodySmall, color = CHECK_MUTED
                )
                return@Column
            }
            Text(
                "Скажите «а-а» обычным голосом ребёнка. Полоса должна пересечь черту, а тихий фон — нет. " +
                    "Слышит лишнее — уменьшите чувствительность, не слышит тихий голос — увеличьте. " +
                    "Хлопки и стук не засчитываются — только голос.",
                style = MaterialTheme.typography.bodySmall, color = CHECK_MUTED
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Чувствительность", color = CHECK_INK, modifier = Modifier.width(140.dp))
                Slider(
                    value = sensitivity,
                    onValueChange = { sensitivity = it },
                    onValueChangeFinished = { repository.micSensitivity = sensitivity },
                    modifier = Modifier.weight(1f)
                )
            }
            if (testing) {
                BoxWithConstraints(
                    Modifier.fillMaxWidth().height(18.dp)
                        .background(Color.Black.copy(alpha = 0.08f), RoundedCornerShape(9.dp))
                ) {
                    val over = level >= threshold
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(level.coerceIn(0f, 1f))
                            .background(if (over) SPEECH_GREEN else Color(0xFF9E9E9E), RoundedCornerShape(9.dp))
                    )
                    Box(
                        Modifier.offset(x = maxWidth * threshold.coerceIn(0f, 1f)).width(3.dp).fillMaxHeight()
                            .background(Color(0xFFE76F51))
                    )
                }
                Text("Услышано попыток: $heard", style = MaterialTheme.typography.bodySmall, color = CHECK_MUTED)
                if (heard > 0) {
                    // Lets the adult see the vowel check working — and how
                    // often it answers "not sure", which is by design.
                    Text(
                        "Гласная: " + (lastVowel?.uppercase() ?: "не уверен"),
                        style = MaterialTheme.typography.titleMedium,
                        color = CHECK_INK
                    )
                }
            }
            OutlinedButton(onClick = { testing = !testing; heard = 0 }) {
                Text(if (testing) "Остановить проверку" else "Проверить микрофон")
            }
        }
    }
}
