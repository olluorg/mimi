package org.ollu.mini.sound

actual fun createTtsPlayer(): TtsPlayer = JvmTtsPlayer()

private class JvmTtsPlayer : TtsPlayer {
    override fun speak(text: String) {
        Thread {
            Runtime.getRuntime().exec(arrayOf("say", text)).waitFor()
        }.apply { isDaemon = true; start() }
    }
}
