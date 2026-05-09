# Mimi ILE — Core Spec v1: Overview

## What is Mimi ILE

Mimi is a **data-driven Interactive Learning Engine** for 2D educational games.

Target audience:
- non-verbal children
- children with speech delay
- ASD / neurodivergent learners
- early developmental education (2–6 years)

## What it is NOT

| Common misconception | Reality |
|---|---|
| A game engine | A behavioral simulation runtime |
| A scripting host | A behavior registry with declarative config |
| A UI framework | UI is a renderer only — no logic |
| A state machine | A reactive constraint + intent system |

## Mental Model

> A **reactive constraint-based interaction system** that transforms input events into learning outcomes through deterministic behavioral simulation.

Core paradigm:

```
Event → Constraint → Behavior → Command → CRL → World → Feedback
```

## Five Fundamental Laws

### 1. No Direct Mutation
Behaviors never write to World State directly.
They produce `Command` objects (intentions).
The engine applies them atomically.

### 2. No Behavior Ordering Dependency
Behaviors are isolated.
They read the same snapshot.
They do not know about each other.
Execution order never affects outcome.

### 3. No Irreversible Learning State (until Lock)
Progress is always tentative.
Only explicit lock events make state permanent.
Children can undo without "losing".

### 4. No Scripting
No Lua, JS, KotlinScript, or user-defined code.
All logic lives in the predefined behavior registry.
Scenes are pure data.

### 5. Deterministic Replay
Same `tick` sequence + same `World(t0)` = identical outcome.
Wall-clock time never enters core.
Every state transition is reproducible.

## Extension Model

New functionality is added only via:
1. New `Component` definitions
2. New `Behavior` implementations (Kotlin, engine level)
3. New `Constraint` evaluators
4. New `Interaction` types

NOT via scripting. NOT via scene-specific logic.
