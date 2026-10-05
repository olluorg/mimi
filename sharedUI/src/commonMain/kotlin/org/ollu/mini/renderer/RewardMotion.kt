package org.ollu.mini.renderer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** How long a card takes to act out its reward — inside a speech game's reward pause. */
const val REWARD_MOTION_MS = 1500

/** An offset in scene units, a turn in degrees and a size factor. */
data class Pose(val dx: Float = 0f, val dy: Float = 0f, val rotation: Float = 0f, val scale: Float = 1f) {
    companion object {
        val REST = Pose()
    }
}

/**
 * What a card does when the child gets it right.
 *
 * The reward has to be the thing itself: say «би-би» and the car drives off
 * and comes back, say «у-у» and the plane flies. A chime tells the child they
 * were right; the car moving tells them their word *did* something — which,
 * for a child who has never used their voice to make things happen, is the
 * reason to try again.
 *
 * Every motion starts and ends at rest, so the card is back in its place for
 * whatever the game does next.
 */
enum class RewardMotion(val id: String) {
    /** Drives off to the right and comes back in from the left. */
    DRIVE("drive") {
        override fun poseAt(t: Float): Pose = if (t < 0.45f) {
            val p = easeIn(t / 0.45f)
            Pose(dx = 800f * p, dy = bump(t))
        } else {
            val p = easeOut((t - 0.45f) / 0.55f)
            Pose(dx = -800f * (1f - p), dy = bump(t))
        }
    },

    /** Takes off up and to the right, and glides back in from below. */
    FLY("fly") {
        override fun poseAt(t: Float): Pose = if (t < 0.5f) {
            val p = easeIn(t / 0.5f)
            Pose(dx = 520f * p, dy = -560f * p, rotation = -18f * p)
        } else {
            val p = easeOut((t - 0.5f) / 0.5f)
            Pose(dx = -520f * (1f - p), dy = 380f * (1f - p), rotation = -18f * (1f - p))
        }
    },

    /** Two happy hops. */
    JUMP("jump") {
        override fun poseAt(t: Float): Pose {
            val hop = abs(sin(2f * PI.toFloat() * t))
            return Pose(dy = -150f * hop, scale = 1f + 0.08f * hop)
        }
    },

    /** One full turn, growing as it goes. */
    SPIN("spin") {
        override fun poseAt(t: Float): Pose =
            Pose(rotation = 360f * easeInOut(t), scale = 1f + 0.25f * sin(PI.toFloat() * t))
    },

    /** A shake that settles — a bell ringing, a head saying "no". */
    WIGGLE("wiggle") {
        override fun poseAt(t: Float): Pose =
            Pose(rotation = 16f * sin(6f * PI.toFloat() * t) * (1f - t), scale = 1f + 0.1f * sin(PI.toFloat() * t))
    };

    abstract fun poseAt(t: Float): Pose

    companion object {
        fun named(id: String?): RewardMotion? = entries.firstOrNull { it.id == id }

        private fun easeIn(x: Float) = x * x
        private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x)
        private fun easeInOut(x: Float) = if (x < 0.5f) 2f * x * x else 1f - 2f * (1f - x) * (1f - x)

        /** The judder of a vehicle under way. */
        private fun bump(t: Float) = 6f * sin(40f * t) * sin(PI.toFloat() * t)
    }
}
