# Trace System

## Definition

The Trace system handles path-following interactions, where a child draws or follows a defined path on screen.

Used for:
- letter/number tracing
- shape drawing
- guided path navigation

## Trace Path Model

A `TracePath` is a sequence of waypoints with tolerance radii.

```json
{
  "pathId": "letter_A",
  "points": [
    { "x": 100, "y": 200, "radius": 50 },
    { "x": 150, "y": 100, "radius": 50 },
    { "x": 200, "y": 200, "radius": 50 },
    { "x": 130, "y": 160, "radius": 40 },
    { "x": 170, "y": 160, "radius": 40 }
  ],
  "direction": "required",
  "minProgress": 0.8
}
```

## Progress Model

Trace progress is a **continuous float from 0.0 to 1.0**, not a discrete waypoint index.

```
progress = pointsReached / totalPoints
```

This is used for:
- smooth progress bar animation
- partial credit evaluation
- `adaptiveHint` triggers at specific thresholds

### Progress is Monotonic (by default)

By default, progress only increases. A child cannot "go back" to lose progress.

Exception: if `direction: "required"` and child moves backwards significantly (> 2 waypoints), `progress` may decrease slightly (jitter tolerance applies).

## Waypoint Semantics

A waypoint is "reached" when the touch/pointer enters its `radius` circle.

Waypoints must be reached in order. Skipping a waypoint is not allowed.

Jitter tolerance: if the child hovers near a waypoint without cleanly entering, a `hoverThreshold` (default: `radius * 1.5`) may count as partial entry. No backtracking from the next waypoint.

## Direction

| Value | Meaning |
|---|---|
| `"none"` | No direction requirement. Path traversal in any order. |
| `"preferred"` | Direction is encouraged but not enforced. Score penalty for reverse. |
| `"required"` | Must traverse in declared point order. Backtracking reduces progress. |

Default: `"preferred"` for young children (2–4), `"required"` for older / advanced.

`difficultyScaler` behavior may override direction policy at runtime.

## Accuracy

After trace completion:

```
accuracy = totalProgressAchieved / 1.0
```

Accuracy is passed in the `TraceComplete` event payload.

`traceComplete` objective uses `minAccuracy` threshold:

```json
{ "type": "traceComplete", "pathId": "letter_A", "minAccuracy": 0.7 }
```

## Adaptive Radius

The `radius` field may be scaled at runtime by `difficultyScaler`:

```
effectiveRadius = baseRadius * difficultyMultiplier
```

Recommended defaults:
- Age 2–3: `difficultyMultiplier = 1.5` (very forgiving)
- Age 4–5: `difficultyMultiplier = 1.0` (default)
- Advanced: `difficultyMultiplier = 0.7` (precise)

## Trace Events

| Event | When |
|---|---|
| `TracePoint` | Each touch move while tracing is active |
| `TraceComplete` | When `progress >= minProgress` and touch released |
| `TraceFailed` | When touch released with `progress < minProgress` |
| `TraceAbandoned` | When no touch input for `abandonTimeout` ticks |

## Commands Produced by Trace Behaviors

| Behavior | Commands produced |
|---|---|
| `traceProgressTracker` | `SetProperty(target=path, field=progress, value=Float)` |
| `traceCompletionValidator` | `SetObjectiveState(objectiveId, TENTATIVE_COMPLETE)` |
| `adaptiveHint` | `TriggerFeedback(type=highlight, entityId=nextWaypoint)` |

## Invariants

1. `progress` is always in range `[0.0, 1.0]`.
2. Waypoints are evaluated in declared order only.
3. `TraceComplete` is emitted at most once per trace interaction (requires release gesture).
4. Trace accuracy is computed from final progress at release, not peak progress.
5. Trace path geometry is defined in scene JSON and never modified at runtime.
