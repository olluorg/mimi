package org.ollu.mini.sound

import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.runBlocking
import mimi.sharedui.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap

actual fun createAudioFilePlayer(): AudioFilePlayer = AndroidAudioFilePlayer()

@OptIn(ExperimentalResourceApi::class)
private class AndroidAudioFilePlayer : AudioFilePlayer {
    private val cache = ConcurrentHashMap<String, File>()
    private val main = Handler(Looper.getMainLooper())

    override fun play(resourcePath: String, maxMs: Long, onDone: () -> Unit) {
        val ctx = AndroidAppContext.context ?: return onDone()
        Thread {
            val finish = Once(onDone)
            val file = runCatching {
                cache.getOrPut(resourcePath) {
                    val bytes = runBlocking { Res.readBytes("files/catalog/$resourcePath") }
                    val ext   = resourcePath.substringAfterLast('.', "mp3")
                    File.createTempFile("mimi_", ".$ext", ctx.cacheDir).also { it.deleteOnExit(); it.writeBytes(bytes) }
                }
            }.getOrNull()
            val player = file?.let { MediaPlayer.create(ctx, Uri.fromFile(it)) }
            if (player == null) { finish.run(); return@Thread }
            main.post {
                val stop = Once {
                    runCatching { player.stop() }
                    player.release()
                    finish.run()
                }
                player.setOnCompletionListener { stop.run() }
                player.setOnErrorListener { _, _, _ -> stop.run(); true }
                player.start()
                if (maxMs > 0) main.postDelayed({ stop.run() }, maxMs)
            }
        }.apply { isDaemon = true; start() }
    }
}
