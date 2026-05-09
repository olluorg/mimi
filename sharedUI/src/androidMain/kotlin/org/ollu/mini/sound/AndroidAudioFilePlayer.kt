package org.ollu.mini.sound

import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.runBlocking
import mimi.sharedui.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap

actual fun createAudioFilePlayer(): AudioFilePlayer = AndroidAudioFilePlayer()

@OptIn(ExperimentalResourceApi::class)
private class AndroidAudioFilePlayer : AudioFilePlayer {
    private val cache = ConcurrentHashMap<String, File>()

    override fun playResourceFile(resourcePath: String) {
        val ctx = AndroidAppContext.context ?: return
        Thread {
            val file = cache.getOrPut(resourcePath) {
                val bytes = runBlocking { Res.readBytes("files/catalog/$resourcePath") }
                val ext   = resourcePath.substringAfterLast('.', "mp3")
                val tmp   = File.createTempFile("mimi_", ".$ext", ctx.cacheDir).also { it.deleteOnExit() }
                tmp.writeBytes(bytes)
                tmp
            }
            val player = MediaPlayer.create(ctx, Uri.fromFile(file)) ?: return@Thread
            player.setOnCompletionListener { it.release() }
            player.start()
        }.apply { isDaemon = true; start() }
    }
}
