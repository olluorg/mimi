package org.ollu.mini.sound

import kotlinx.coroutines.runBlocking
import mimi.sharedui.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.sound.sampled.AudioSystem

actual fun createAudioFilePlayer(): AudioFilePlayer = JvmAudioFilePlayer()

/**
 * Desktop playback for catalog audio.
 *
 * WAV goes through the JVM's own mixer, which is present on every desktop and
 * needs nothing installed. MP3 has no built-in codec, so it falls back to
 * whatever the system provides. The previous version assumed macOS `afplay` for
 * everything, which meant desktop audio was silent on Linux and Windows.
 */
@OptIn(ExperimentalResourceApi::class)
private class JvmAudioFilePlayer : AudioFilePlayer {

    private val bytesCache = ConcurrentHashMap<String, ByteArray>()
    private val fileCache  = ConcurrentHashMap<String, File>()

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "catalog-audio").apply { isDaemon = true }
    }

    override fun play(resourcePath: String, maxMs: Long, onDone: () -> Unit) {
        val finish = Once(onDone)
        executor.submit {
            val played = runCatching {
                val bytes = bytesCache.getOrPut(resourcePath) {
                    runBlocking { Res.readBytes("files/catalog/$resourcePath") }
                }
                if (resourcePath.substringAfterLast('.', "").lowercase() == "mp3") {
                    playExternally(resourcePath, bytes, maxMs)
                    finish.run()
                } else {
                    playWithMixer(bytes, maxMs) { finish.run() }
                }
            }
            if (played.isFailure) finish.run()
        }
    }

    private fun playWithMixer(bytes: ByteArray, maxMs: Long, onDone: () -> Unit) {
        AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes)).use { stream ->
            val clip = AudioSystem.getClip()
            clip.open(stream)
            // Released once it finishes, so repeated taps do not leak lines.
            clip.addLineListener { event ->
                if (event.type == javax.sound.sampled.LineEvent.Type.STOP) {
                    clip.close()
                    onDone()
                }
            }
            clip.start()
            if (maxMs > 0) {
                java.util.Timer(true).schedule(object : java.util.TimerTask() {
                    override fun run() { if (clip.isOpen) clip.stop() }
                }, maxMs)
            }
        }
    }

    private fun playExternally(resourcePath: String, bytes: ByteArray, maxMs: Long) {
        val file = fileCache.getOrPut(resourcePath) {
            File.createTempFile("mimi_", ".mp3").also { it.deleteOnExit(); it.writeBytes(bytes) }
        }
        val player = MP3_PLAYERS.firstOrNull { isAvailable(it) } ?: return
        val process = ProcessBuilder(player + file.absolutePath).redirectErrorStream(true).start()
        if (maxMs > 0) {
            if (!process.waitFor(maxMs, java.util.concurrent.TimeUnit.MILLISECONDS)) process.destroy()
        } else {
            process.waitFor()
        }
    }

    private fun isAvailable(command: List<String>): Boolean = runCatching {
        ProcessBuilder("which", command.first()).redirectErrorStream(true).start().waitFor() == 0
    }.getOrDefault(false)

    private companion object {
        val MP3_PLAYERS = listOf(
            listOf("afplay"),                       // macOS
            listOf("mpg123", "-q"),                 // common on Linux
            listOf("ffplay", "-nodisp", "-autoexit", "-loglevel", "quiet")
        )
    }
}
