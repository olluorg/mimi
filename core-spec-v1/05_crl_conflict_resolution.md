# CRL — Command Resolution Layer

## Purpose

The CRL is the engine subsystem that resolves conflicts when multiple behaviors produce commands targeting the same `(entity, field)` pair in the same tick.

> **Behaviors produce intentions. CRL produces truth.**

## Position in Pipeline

```
Behaviors → CommandBuffer → [ CRL ] → Applied Commands → World(t+1)
```

## Conflict Definition

A conflict exists when:
```
count(commands where target == X and field == F) > 1
```

Single commands targeting unique `(entity, field)` pairs pass through CRL unchanged.

## Resolution Policies

There are four resolution strategies. The applied strategy is determined per field.

### 1. PRIORITY
The command with the highest `priority` wins.
Equal priority: stable (earlier in buffer = wins).

```
Commands: [pos@priority=100, pos@priority=10]
Result:   [pos@priority=100]
```

Use for: `position`, `state`, fields with clear dominance semantics.

### 2. MERGE
All commands are combined into a single result.
The merge function is field-type specific:
- Numeric: sum of deltas (for `IncrementCounter`)
- List/Set: union
- Flags: bitwise OR

```
Commands: [matchCount += 1, matchCount += 1]
Result:   [matchCount += 2]
```

Use for: counters, flags, tags, visibility modifiers.

### 3. LAST_WINS
The command with the highest `tick` wins.
Within the same tick: last in buffer order wins.

Use for: `animationState`, rapidly-updated transient fields.

### 4. FIRST_WINS
The command with the lowest `tick` wins.
Within the same tick: first in buffer order wins.

Use for: sequence locking, puzzle snap (first correct answer locks).

## Engine Default Policies

These apply unless overridden at scene level:

| Field | Default Policy | Rationale |
|---|---|---|
| `position` | PRIORITY | Snap behavior dominates visual hints |
| `visibility` | MERGE | Multiple behaviors may toggle visibility |
| `state` | LAST_WINS | Last lifecycle transition wins |
| `animationState` | LAST_WINS | Latest animation request wins |
| `*Counter` (any) | MERGE | Counters always accumulate |
| `tags` | MERGE | Tags are additive |
| `locked` | FIRST_WINS | First lock is permanent |

## Scene-Level Override

A scene may override policies for specific fields:

```json
{
  "fieldPolicies": {
    "position": "MERGE",
    "customFlag": "PRIORITY"
  }
}
```

Rules:
- Scene overrides apply globally within the scene
- Engine defaults are used for fields not listed in the override
- Per-entity overrides are not supported in v1

## CRL Algorithm

```
input:  List<Command> (all commands for this tick)
output: List<Command> (resolved, one per (entity, field) pair)

1. Group commands by (target, field)
2. For each group:
   a. Lookup policy for field (scene override → engine default → LAST_WINS)
   b. Apply resolution strategy
   c. Emit single resolved command
3. Return flat list of resolved commands
```

## Invariants

1. CRL output contains at most one command per `(entity, field)` pair.
2. CRL never generates new commands — only selects or merges existing ones.
3. CRL never reads WorldState — it operates only on the command buffer.
4. `TriggerFeedback` commands bypass CRL entirely.
5. An empty buffer → empty output (no-op).
6. CRL is deterministic: same buffer + same policies → same output.

## What CRL Does NOT Do

- Does not validate business logic
- Does not check if target entity exists
- Does not evaluate constraints
- Does not know about behavior semantics
- Does not produce side effects
