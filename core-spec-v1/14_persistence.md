# Persistence

## Scope

Two types of local data:

| Type | What | When written |
|---|---|---|
| **Scene package** | scene.json + all assets | On download (once) |
| **Progress data** | session history, metrics | On scene complete + on reset |

Progress data is separate from scene package. Deleting a scene removes assets but optionally keeps progress history.

## Scene Package Storage

```
local/{sceneId}/scene.json
local/{sceneId}/assets/**
local/{sceneId}/.meta.json
```

`.meta.json`:

```json
{
  "sceneId": "sort_fruits_01",
  "version": "1.0.0",
  "downloadedAt": "2026-05-09T14:00:00Z",
  "totalSizeBytes": 4200000,
  "checksums": {
    "scene.json": "abc123",
    "assets/sounds/success.ogg": "def456"
  }
}
```

## Progress Storage

```
progress/{sceneId}/progress.json
```

```json
{
  "sceneId": "sort_fruits_01",
  "firstPlayedAt": "2026-05-09T14:05:00Z",
  "lastPlayedAt": "2026-05-09T15:30:00Z",
  "totalSessions": 5,
  "sessions": [
    {
      "startedAt": "2026-05-09T14:05:00Z",
      "completed": true,
      "completionTicks": 1240,
      "mistakes": 3,
      "hints": 1,
      "finalDifficulty": 0.6
    },
    {
      "startedAt": "2026-05-09T15:30:00Z",
      "completed": false,
      "completionTicks": null,
      "mistakes": 1,
      "hints": 0,
      "finalDifficulty": 0.65
    }
  ],
  "bestSession": {
    "completionTicks": 980,
    "mistakes": 0,
    "hints": 0
  }
}
```

## Write Rules

| Event | Action |
|---|---|
| `SceneCompleted` | Append completed session, update `bestSession` if better |
| `SceneReset` | Append incomplete session with `completed: false` |
| `SceneUnloaded` | Flush to disk if any pending writes |
| App backgrounded | Flush pending writes immediately |

Progress is **never written mid-session** (except on background) — only on terminal events.

## `finalDifficulty`

The `difficulty` value from `_scene` at session end.
Used to seed `initialDifficulty` for the next session of the same scene:

```
nextSession.initialDifficulty = lastSession.finalDifficulty
```

This provides continuity across sessions without a formal "player profile".

## Catalog Cache

```
catalog_cache.json   ← last fetched catalog.json, with timestamp
```

```json
{
  "fetchedAt": "2026-05-09T14:00:00Z",
  "catalog": { ... }
}
```

Used when offline. Stale if older than 24h (app shows "catalog may be outdated" notice).

## Invariants

1. Progress data survives scene re-download (stored separately).
2. All timestamps are ISO 8601 UTC strings — no device-local time.
3. `sessions` array is append-only — no session is ever modified or deleted.
4. `bestSession` is updated only for `completed: true` sessions.
5. `finalDifficulty` is always in `[0.1, 1.0]`.
6. If progress file is corrupted → reset to empty (do not crash).
