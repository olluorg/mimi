# Runtime Model

## Full Pipeline

```
┌─────────────────────────────────────────────────────┐
│                    ENGINE LOOP                      │
│                                                     │
│  tick++                                             │
│     ↓                                               │
│  Input Event received                               │
│     ↓                                               │
│  [Phase 1] INTERPRETATION                           │
│     Constraints evaluated                           │
│     Eligible entities selected                      │
│     ↓                                               │
│  [Phase 2] RESOLUTION                               │
│     Behaviors produce List<Command>                 │
│     All behaviors read World(t) snapshot            │
│     ↓                                               │
│  [Phase 3] COMMAND BUFFER                           │
│     All commands collected                          │
│     ↓                                               │
│  [Phase 4] CRL — Conflict Resolution Layer          │
│     Per-field policies applied                      │
│     Conflicts resolved                              │
│     ↓                                               │
│  [Phase 5] COMMIT                                   │
│     World(t+1) = apply(resolved commands)           │
│     ↓                                               │
│  [Phase 6] OBJECTIVES                               │
│     Objective states re-evaluated                   │
│     ↓                                               │
│  [Phase 7] FEEDBACK                                 │
│     Sound / Animation / Particles / Speech triggered│
└─────────────────────────────────────────────────────┘
```

## Tick

The `tick` is the single time primitive in core.

Rules:
- `tick` is a monotonically increasing `Long`
- Incremented **once per engine loop iteration**, by the engine only
- No behavior, plugin, or scene may read or write `tick` directly
- `tick` is embedded in every `Command` at generation time
- Two `Command` objects with the same `tick` and `source` are siblings from the same behavior invocation

```
tick: 0  → initial World state
tick: 1  → first event processed
tick: N  → deterministic replay point
```

## Snapshot Semantics

At the start of each tick:
- A `WorldSnapshot` is captured (immutable view of `World(t)`)
- All behaviors in this tick receive **the same snapshot**
- No behavior sees mutations produced by sibling behaviors in the same tick

```
World(t)  ──read──►  Behavior A  ──►  [CommandA1, CommandA2]
          ──read──►  Behavior B  ──►  [CommandB1]
          ──read──►  Behavior C  ──►  []

                        ↓
                   Command Buffer
                        ↓
                       CRL
                        ↓
                   World(t+1)
```

## Invariants

1. `World(t)` is never modified. `World(t+1)` is always a new state.
2. `Behavior.handle()` is a pure function: same inputs → same output.
3. No I/O inside behavior execution (no file read, no network, no random).
4. `tick` is the only temporal reference in the system.
5. `Feedback` is triggered after World commit, never before.
6. Objectives are evaluated after World commit, never during.

## What Lives Where

| Concern | Location |
|---|---|
| Game logic rules | Behavior implementations (Kotlin, engine) |
| Content | Scene JSON |
| Conflict resolution | CRL |
| Rendering | UI layer (renderer only) |
| Persistence | Storage adapter (outside core) |
| Time | Engine loop tick counter |
