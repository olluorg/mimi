package org.ollu.mini.sound

interface AudioFilePlayer {
    // resourcePath: path relative to files/catalog/ in composeResources
    fun playResourceFile(resourcePath: String)
}

expect fun createAudioFilePlayer(): AudioFilePlayer
