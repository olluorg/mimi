package org.ollu.mini.renderer

import mimi.core.model.EntitySnapshot

/**
 * How big a tile is, in the scene's own 1000×1000 space.
 *
 * Both the renderer and the hit test read it from here. They used to disagree:
 * the picture was a fixed 90dp while the hit test allowed a fixed 80 logical
 * units, so how much of a card actually responded to a finger depended on the
 * window size — on a short window the corners of a card were outside its own
 * hit area, and a child pressing the card they could plainly see got nothing.
 */
object TileMetrics {

    /** A piece the child moves or presses. */
    const val ITEM: Float = 120f

    /** A place to put things: bigger, so it reads as a target and is easy to hit. */
    const val TARGET: Float = 150f

    fun sizeOf(entity: EntitySnapshot): Float {
        entity.num("width")?.let { return it.toFloat() }
        entity.num("radius")?.let { return it.toFloat() * 2f }
        return if (entity.hasComponent("dropTarget")) TARGET else ITEM
    }

    /** Half the tile's width: the reach of a press aimed at its centre. */
    fun reachOf(entity: EntitySnapshot): Float = sizeOf(entity) / 2f
}
