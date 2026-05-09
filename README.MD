# Mimi — Interactive Learning Engine (ILE)

## Overview

This project is a **data-driven interactive learning engine** for 2D educational games designed primarily for:

* non-verbal children
* children with speech delay
* ASD / neurodivergent learners
* early developmental education (2–6 years)

The engine is not a game. It is a **runtime system for composing learning interactions from declarative scenes**.

Instead of scripting, the system uses:

> Entity + Component + Behavior + Constraint + Event model

No scripting languages (Lua/JS/Kotlin Script) are used.

All game logic is expressed via **structured data + pluggable behaviors**.

---

# 🧠 Core Design Principles

## 1. No imperative scripting

No user-defined code execution. Only predefined behaviors.

## 2. Data-driven scenes

All content is defined in JSON (or equivalent serialized format).

## 3. Composition over inheritance

Behavior emerges from combining components.

## 4. Deterministic runtime

Same input + same state = same result.

## 5. Accessibility-first design

Mechanics must support:

* minimal cognitive load
* predictable feedback
* multi-modal reinforcement (audio/visual/haptic)

---

# 🧱 Core Concepts

## Entity

A runtime object in the scene.

```json
{
  "id": "string",
  "components": [],
  "behaviors": [],
  "tags": {},
  "properties": {}
}
```

Entities are passive containers.

---

## Component (data-only)

Represents capabilities or data.

Examples:

* `transform`
* `sprite`
* `drag`
* `dropTarget`
* `traceTarget`
* `sequenceItem`

---

## Behavior (logic unit, prebuilt)

Reusable logic modules.

Examples:

* `snapToTarget`
* `adaptiveHint`
* `playSoundOnEvent`
* `progressTracker`
* `sequenceValidator`

Behaviors are NOT scripts. They are parameterized implementations.

```json
{
  "type": "adaptiveHint",
  "config": {
    "afterMistakes": 3
  }
}
```

---

## Event System

All interactions are event-driven.

Events:

* `Tap`
* `DragStart`
* `DragMove`
* `Drop`
* `MatchSuccess`
* `MatchFail`
* `ObjectiveCompleted`

---

## Constraint System (Matching Logic)

Used instead of hardcoded target IDs.

Example:

```json
{
  "type": "propertyMatch",
  "sourceProperty": "color",
  "targetProperty": "acceptsColor"
}
```

Supports:

* equality
* inclusion
* predicates
* composite conditions (`allOf`, `anyOf`)

---

## World State

Mutable runtime state:

* progress
* mistakes
* timers
* visibility state
* entity states

---

## Objective System

Defines win conditions.

Example:

```json
{
  "type": "allMatched",
  "group": "fruits"
}
```

Supports multi-step goals (sequence of objectives).

---

## Feedback System

Unified reinforcement output:

* sound
* animation
* particles
* speech
* vibration

Triggered by events or behaviors.

---

# 🎮 Interaction Model

Supported interactions:

* Tap
* Drag & Drop
* Trace (path following)
* Sequence input
* Hold gesture
* Multi-touch (optional)

---

# 🧩 Scene Definition

A scene is a complete interactive configuration.

```json
{
  "sceneId": "sorting_colors_01",
  "entities": [],
  "rules": [],
  "objectives": [],
  "behaviors": [],
  "feedback": [],
  "meta": {}
}
```

---

# 🧠 Matching Model (IMPORTANT)

No direct mapping:

❌ WRONG:

```
object A → slot 1
```

✅ CORRECT:

```
object.property ↔ target.constraint
```

Example:

```json
{
  "type": "constraintMatch",
  "source": { "property": "color" },
  "target": { "accepts": "color" }
}
```

---

# 🔄 Runtime Pipeline

1. Input event occurs
2. Event dispatched
3. Relevant entities selected
4. Constraints evaluated
5. Behaviors executed
6. World state updated
7. Feedback triggered
8. Objectives checked

---

# 📊 Adaptive System

Tracks:

* mistake count
* idle time
* completion speed
* success ratio

Used by behaviors like:

* `adaptiveHint`
* `difficultyScaler`

---

# ⚠️ Critical Design Constraints

## 1. No free-form code execution

Only predefined behavior registry.

## 2. All logic must be serializable

Scenes must be fully inspectable.

## 3. No hidden state in UI layer

UI is a renderer only.

## 4. Deterministic behavior preferred

---

# 🧪 Testing Requirements

Each scene must support:

* deterministic replay
* validation of missing assets
* invalid constraint detection
* unreachable objective detection

---

# 📦 Extension Model

New functionality is added via:

### 1. New Component types

### 2. New Behavior implementations

### 3. New Constraint evaluators

### 4. New Interaction types

NOT via scripting.

---

# 🧱 Example: Sorting Game

```json
{
  "sceneId": "sort_fruits_01",

  "entities": [
    {
      "id": "apple",
      "components": ["drag"],
      "properties": {
        "category": "fruit",
        "color": "red"
      }
    }
  ],

  "constraints": [
    {
      "type": "propertyMatch",
      "source": "category",
      "target": "acceptsCategory"
    }
  ],

  "objectives": [
    {
      "type": "allSorted",
      "group": "fruits"
    }
  ]
}
```

---

# 🧭 Mental Model Summary

This engine is:

> A **reactive constraint-based interaction system**, not a game engine.

Core paradigm:

```
Event → Constraint → Behavior → State → Feedback
```

---

# 🚀 What NOT to do

* No Lua/JS scripting layer
* No arbitrary code execution
* No scene-specific logic outside behaviors
* No hardcoded object-to-slot mapping
* No UI-driven game logic

---

# 🧩 What IS allowed

* Adding new behaviors in Kotlin (engine level)
* Adding new components
* Defining scenes in JSON
* Defining constraints declaratively
* Extending interaction types

---

# 🧠 Intended Outcome

The system should allow:

* rapid creation of new educational games
* reuse of mechanics across games
* adaptive difficulty for children
* accessibility-first interaction design
* therapist/educator-driven content expansion

---

Если хочешь, следующим шагом можно:

* превратить это в **реальный Kotlin module structure (Gradle multi-module)**
* или спроектировать **behavior registry + plugin system**, чтобы это реально было расширяемо без переписывания ядра.
