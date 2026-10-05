package org.ollu.mini.sound

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import mimi.sharedui.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

actual fun createAudioFilePlayer(): AudioFilePlayer = WebAudioFilePlayer()

/**
 * Catalog audio in the browser.
 *
 * This was a no-op, so the web build played none of the card recordings and
 * none of the instrument sounds — everything the app says out loud was silent
 * there. Bytes are read from the bundled resources and handed to the browser as
 * a data URL, which avoids any cross-origin fetch the artifact sandbox or a
 * static host might refuse.
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalEncodingApi::class)
private class WebAudioFilePlayer : AudioFilePlayer {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val cache = mutableMapOf<String, String>()

    override fun play(resourcePath: String, maxMs: Long, onDone: () -> Unit) {
        val finish = Once(onDone)
        scope.launch {
            runCatching {
                val url = cache.getOrPut(resourcePath) {
                    val bytes = Res.readBytes("files/catalog/$resourcePath")
                    "data:${mimeFor(resourcePath)};base64,${Base64.encode(bytes)}"
                }
                playDataUrl(url, maxMs.toDouble()) { finish.run() }
            }.onFailure { finish.run() }
        }
    }

    private fun mimeFor(path: String): String =
        when (path.substringAfterLast('.', "").lowercase()) {
            "mp3"  -> "audio/mpeg"
            "ogg"  -> "audio/ogg"
            "opus" -> "audio/ogg"
            else   -> "audio/wav"
        }
}

private fun playDataUrl(url: String, maxMs: Double, onDone: () -> Unit) {
    js("""(function(){
        var done = false;
        function finish(){ if (!done) { done = true; onDone(); } }
        var a = new Audio(url);
        a.onended = finish;
        a.onerror = finish;
        a.play().catch(finish);
        if (maxMs > 0) setTimeout(function(){ a.pause(); finish(); }, maxMs);
    })()""")
}
