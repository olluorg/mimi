# Coordinate System

## Logical Units

All positions in scene.json and in the engine core use **logical units** on a fixed 1000×1000 grid.

```
(0, 0) ──────────────── (1000, 0)
  │                          │
  │     scene canvas         │
  │                          │
(0, 1000) ──────────── (1000, 1000)
```

- Origin: **top-left**
- X: increases to the right
- Y: increases downward (screen convention)
- All coordinates are `Float` in range `[0.0, 1000.0]`

## Renderer Mapping

The renderer maps logical units to physical pixels at render time.
The engine core never knows the physical screen size.

Mapping strategy: **uniform scale with letterbox/pillarbox**.

```
physicalX = logicalX × (physicalWidth  / 1000.0)
physicalY = logicalY × (physicalHeight / 1000.0)
```

For non-square screens, the shorter axis defines the scale (letterbox).
Safe area: content critical for interaction should be placed in `[100, 100]–[900, 900]`.

## Waypoint and Drop Target Radii

`radius` in trace waypoints and drop target hit areas is also in logical units.

Recommended defaults:
- Drop target radius: `80` (generous for young children)
- Trace waypoint radius: `50` (default), scaled by `difficultyMultiplier`

## Vec2 in Core Model

```kotlin
data class Vec2(val x: Float, val y: Float) : PropertyValue
```

Constraints on values:
- `x` in `[0.0, 1000.0]`
- `y` in `[0.0, 1000.0]`
- Values outside range are clamped by the renderer (not an error in core)

## Z-Order (Rendering Layer)

Z-order is declared statically in scene.json per entity. It is not a runtime property in v1.

```json
{
  "id": "apple",
  "zOrder": 10
}
```

- Higher `zOrder` renders on top
- Default `zOrder`: `0`
- Z-order does not affect game logic — only rendering

## Invariants

1. Core engine never uses physical pixel values.
2. All positions in `GameEvent` payloads (e.g. `Tap.position`) are in logical units, converted by the input layer before dispatch.
3. Z-order is read-only at runtime — no `Command` may change it in v1.
4. `radius` fields are always in logical units.
