package org.ollu.mini.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberMicrophoneLevels(): MicrophoneLevels {
    val context = LocalContext.current.applicationContext
    val mic = remember { AndroidMicrophone(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        mic.onPermissionResult(granted)
    }
    mic.askPermission = { launcher.launch(Manifest.permission.RECORD_AUDIO) }
    return mic
}

/**
 * Loudness from the microphone through [AudioRecord].
 *
 * Opened as a voice-communication source where the device allows it: that is
 * the input the platform puts its echo canceller on, which takes out much of
 * the app's own voice before the loudness is even measured.
 */
private class AndroidMicrophone(private val context: Context) : MicrophoneLevels {
    override val isSupported = true

    var askPermission: (() -> Unit)? = null

    private var pending: Pair<(FloatArray, Int) -> Unit, () -> Unit>? = null
    @Volatile private var running = false
    private var worker: Thread? = null

    override fun start(onFrame: (FloatArray, Int) -> Unit, onUnavailable: () -> Unit) {
        if (running) return
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            open(onFrame, onUnavailable)
        } else {
            val ask = askPermission
            if (ask == null) { onUnavailable(); return }
            pending = onFrame to onUnavailable
            ask()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        val (onFrame, onUnavailable) = pending ?: return
        pending = null
        if (granted) open(onFrame, onUnavailable) else onUnavailable()
    }

    private fun open(onFrame: (FloatArray, Int) -> Unit, onUnavailable: () -> Unit) {
        running = true
        worker = Thread({
            val record = openRecord()
            if (record == null) {
                running = false
                onUnavailable()
                return@Thread
            }
            val effects = quietEffects(record.audioSessionId)
            val chunk = ShortArray(SAMPLE_RATE / 20)      // 50 ms
            try {
                record.startRecording()
                while (running) {
                    val read = record.read(chunk, 0, chunk.size)
                    if (read <= 0) continue
                    val samples = FloatArray(read) { chunk[it] / 32768f }
                    onFrame(samples, SAMPLE_RATE)
                }
            } catch (_: Exception) {
                if (running) onUnavailable()
            } finally {
                runCatching { record.stop() }
                effects.forEach { runCatching { it.release() } }
                record.release()
            }
        }, "mimi-microphone").apply { isDaemon = true; start() }
    }

    /**
     * Automatic gain control is off: it turns a silent room up until its hiss
     * is as loud as speech, which is exactly what made silence count as an
     * answer. Echo cancellation and noise suppression are on, where the device
     * has them.
     */
    private fun quietEffects(session: Int): List<AudioEffect> = buildList {
        if (AutomaticGainControl.isAvailable()) {
            AutomaticGainControl.create(session)?.let { it.enabled = false; add(it) }
        }
        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(session)?.let { it.enabled = true; add(it) }
        }
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(session)?.let { it.enabled = true; add(it) }
        }
    }

    @Suppress("MissingPermission")  // checked in start()
    private fun openRecord(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return null
        for (source in listOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.MIC)) {
            val record = runCatching {
                AudioRecord(
                    source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, SAMPLE_RATE / 5)
                )
            }.getOrNull() ?: continue
            if (record.state == AudioRecord.STATE_INITIALIZED) return record
            record.release()
        }
        return null
    }

    override fun stop() {
        running = false
        pending = null
        worker = null
    }

    private companion object {
        const val SAMPLE_RATE = 16000
    }
}
