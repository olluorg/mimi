package org.ollu.mini.progress

import kotlinx.serialization.Serializable

/**
 * One child using this device.
 *
 * A tablet in a family or a speech room is shared, and until now everything —
 * what had been played, which skills were building, which scene came next — was
 * pooled into a single anonymous record. Two children would push each other's
 * difficulty around and unlock each other's scenes.
 *
 * [avatar] is a card id rather than a name because the child picks their own
 * profile, and they cannot read: they look for their animal.
 */
@Serializable
data class ChildProfile(
    val id:     String,
    val name:   String,
    val avatar: String,
    /**
     * What the child's speech difficulty is, when the adult has said so —
     * alalia, apraxia, dysarthria. Empty means speech is not a focus, and the
     * app plays as it always has. Set, it makes every other game in a sitting
     * a speech game suited to it: short practice spread through play, rather
     * than one long speech session a small child will not sit through.
     */
    val speechFocus: List<String> = emptyList()
)

/**
 * Pictures offered when a profile is created.
 *
 * Animals, and distinct ones: a child choosing between a cat and a dog is
 * making a real choice, where two similar cards would be a memory test.
 */
val PROFILE_AVATARS: List<String> = listOf(
    "cat", "dog", "rabbit", "bear", "elephant", "lion", "mouse", "fish",
    "hen", "cow", "duck", "frog"
)
