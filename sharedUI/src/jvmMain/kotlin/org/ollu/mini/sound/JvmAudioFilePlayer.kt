package org.ollu.mini.sound

import kotlinx.coroutines.runBlocking
import mimi.sharedui.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap

actual fun createAudioFilePlayer(): AudioFilePlayer = JvmAudioFilePlayer()

@OptIn(ExperimentalResourceApi::class)
private class JvmAudioFilePlayer : AudioFilePlayer {
    private val cache = ConcurrentHashMap<String, File>()

    override fun playResourceFile(resourcePath: String) {
        Thread {
            val file = cache.getOrPut(resourcePath) {
                val bytes = runBlocking { Res.readBytes("files/catalog/$resourcePath") }
                val ext   = resourcePath.substringAfterLast('.', "mp3")
                val tmp   = File.createTempFile("mimi_", ".$ext").also { it.deleteOnExit() }
                tmp.writeBytes(bytes)
                tmp
            }
            Runtime.getRuntime().exec(arrayOf("afplay", file.absolutePath)).waitFor()
        }.apply { isDaemon = true; start() }
    }
}
