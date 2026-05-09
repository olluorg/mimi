# Adaptive System

## Overview

Two independent adapters run in parallel:

| Adapter | Speed | Role |
|---|---|---|
| `adaptiveHint` | Fast (seconds) | Immediate support when child struggles |
| `difficultyScaler` | Slow (20–30s) | Long-term difficulty calibration |

They are **not directly linked**. Both read the same world metrics independently.

```
World Metrics (mistakes, idleTicks, successRate, ...)
        │                        │
        ▼                        ▼
  adaptiveHint            difficultyScaler
  (fast adapter)          (slow adapter)
        │                        │
   HintLevel               DifficultyScale
        │                        │
   TriggerFeedback         SetProperty commands
```

---

## adaptiveHint

### Hint Levels

| Level | Condition | Hint type |
|---|---|---|
| 0 | mistakes < 1 AND idleTime < 4s | No hint |
| 1 | mistakes ≥ 1 OR idleTime ≥ 4s | Subtle (glow / pulse on target) |
| 2 | mistakes ≥ 2 OR idleTime ≥ 8s | Directional (arrow pointing to target) |
| 3 | mistakes ≥ 3 OR idleTime ≥ 12s | Full assist (animated object moves to target) |

`idleTime` is in seconds. In ticks at 60tps: 4s=240, 8s=480, 12s=720.

### Level Dynamics

- Levels **rise fast**: threshold crossed → level upgrades immediately
- Levels **fall slow**: one level down every 60s of correct interactions (no mistakes, no idle)
- Level never drops below 0

### Cooldown

- Minimum 10–15s (600–900 ticks) between hint triggers
- If level increases during cooldown, cooldown resets
- Cooldown is per-entity, not global

### Config

```json
{
  "type": "adaptiveHint",
  "config": {
    "cooldownMin": 600,
    "cooldownMax": 900,
    "hintTypes": {
      "1": "glow",
      "2": "arrow",
      "3": "assist"
    }
  }
}
```

### Commands produced

```
TriggerFeedback { feedbackType: "hint", params: { level: 2, entityId: "apple" } }
```

---

## difficultyScaler

### Scale Range

`difficulty` is a float in `[0.1, 1.0]`.

- `0.1` = maximum assistance (very large radii, slow timing requirements)
- `1.0` = full challenge (precise radii, strict timing)
- Initial value: `0.5` (mid-point default)

### Update Frequency

Recalculated every **20–30 seconds** (1200–1800 ticks).
The engine picks a random value in that range per session to prevent rhythmic patterns.

### Decrease Triggers

| Condition | Adjustment |
|---|---|
| `successRate < 40%` in last window | `−0.10` |
| 3+ failures in last window | `−0.15` |
| High idle (avg idleTime > 10s) | `−0.10` |

Multiple conditions may apply in the same window: adjustments are summed, clamped to `[0.1, 1.0]`.

### Increase Triggers

| Condition | Adjustment |
|---|---|
| `successRate > 80%` in last window | `+0.05` |
| 3 perfect interactions (no mistakes, no hints) | `+0.05` |

### What difficultyScaler controls

The `difficulty` value is written to World State as a scene-global property.
Individual behaviors read it from the snapshot:

| Behavior | How it uses `difficulty` |
|---|---|
| `snapToTarget` | `snapRadius = baseRadius × (2.0 - difficulty)` |
| `traceProgressTracker` | `waypointRadius = baseRadius × (2.0 - difficulty)` |
| `sequenceValidator` | `timeLimitTicks = baseLimit × (2.0 - difficulty)` |

Formula: `effectiveValue = baseValue × (2.0 - difficulty)`
- At `difficulty = 1.0`: `effectiveValue = baseValue` (unchanged)
- At `difficulty = 0.5`: `effectiveValue = baseValue × 1.5` (50% more forgiving)
- At `difficulty = 0.1`: `effectiveValue = baseValue × 1.9` (very forgiving)

### Commands produced

```
SetProperty { target: "_scene", field: "difficulty", value: Number(0.65), priority: 50, ... }
```

### Config

```json
{
  "type": "difficultyScaler",
  "config": {
    "initialDifficulty": 0.5,
    "updateIntervalMin": 1200,
    "updateIntervalMax": 1800
  }
}
```

---

## Metrics tracked in World State

All adaptive behaviors read these counters from `_scene` entity:

| Metric | Field | Updated by |
|---|---|---|
| Total mistakes | `mistakes` | `progressTracker` |
| Total matches | `matches` | `progressTracker` |
| Hints triggered | `hints` | `adaptiveHint` |
| Idle ticks (current) | `idleTicks` | Engine (incremented each tick without input) |
| Success rate (last window) | `successRateWindow` | `difficultyScaler` |
| Current difficulty | `difficulty` | `difficultyScaler` |
| Current hint level | `hintLevel` | `adaptiveHint` |

---

## Invariants

1. `adaptiveHint` and `difficultyScaler` never communicate directly.
2. `difficulty` is always in `[0.1, 1.0]` — clamped at write time.
3. `hintLevel` is always in `[0, 3]` — no level above 3.
4. Hint cooldown is enforced by checking `(currentTick - lastHintTick) >= cooldownTicks`.
5. `difficultyScaler` never fires during the first update window (initial 1200–1800 ticks grace period).
6. No hint is triggered during `SceneCompleted` or `SceneReset` processing.
