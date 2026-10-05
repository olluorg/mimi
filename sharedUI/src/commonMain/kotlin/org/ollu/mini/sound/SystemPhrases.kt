package org.ollu.mini.sound

/**
 * Everything the app itself says aloud, outside any scene.
 *
 * Kept in one place so that `tools/bake_speech.py` can record every line and a
 * test can check that it did: a phrase typed inline at a call site is a phrase
 * that silently goes back to the robot voice.
 */
object SystemPhrases {
    const val WELL_DONE          = "Молодец!"
    const val SESSION_DONE       = "Молодец! Ты закончил."
    const val REWARDS            = "Награды"
}

/**
 * Button labels. Not spoken — pressing one opens a game that speaks for itself —
 * so they are not baked either.
 */
object ButtonLabels {
    const val AGAIN      = "Ещё раз"
    const val TO_CATALOG = "В каталог"
    const val NEXT_SCENE = "Следующая сцена"
}
