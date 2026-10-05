package org.ollu.mini.sound

import java.util.concurrent.Executors

actual fun createTtsPlayer(): TtsPlayer = JvmTtsPlayer()

/**
 * Desktop speech, delegated to whatever the OS provides.
 *
 * There is no portable JVM speech API, so this shells out: `say` on macOS,
 * `spd-say` on Linux. Windows has no equivalent one-liner and stays silent.
 * The previous version assumed `say` everywhere, so on Linux every prompt threw
 * an IOException on a bare thread instead of simply not speaking.
 */
private class JvmTtsPlayer : TtsPlayer {

    // Single worker: speech requests are serialised, and a slow command cannot
    // pile up threads when a child taps repeatedly.
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tts").apply { isDaemon = true }
    }

    private val command: List<String>? by lazy { detectCommand() }

    override fun speak(text: String) {
        if (text.isBlank()) return
        val base = command ?: return
        executor.submit {
            runCatching {
                ProcessBuilder(base + text)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor()
            }
        }
    }

    private fun detectCommand(): List<String>? {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val candidates = when {
            os.contains("mac")   -> listOf(listOf("say"))
            os.contains("linux") -> listOf(listOf("spd-say", "-l", "ru"), listOf("espeak", "-v", "ru"))
            else                 -> emptyList()
        }
        return candidates.firstOrNull { isAvailable(it.first()) }
    }

    private fun isAvailable(executable: String): Boolean = runCatching {
        ProcessBuilder("which", executable)
            .redirectErrorStream(true)
            .start()
            .waitFor() == 0
    }.getOrDefault(false)
}
