# Plugin & Extension Model

## Philosophy

The engine core is closed: it defines types, contracts, and the runtime pipeline.
Extension happens at designated seams only.

> You extend by adding — not by modifying core.

## Extension Seams (v1)

| Seam | What you add | How |
|---|---|---|
| Behavior | New logic unit | Implement `Behavior`, register in `BehaviorRegistry` |
| Component | New entity capability/data | Define `ComponentDefinition`, register in `ComponentRegistry` |
| Constraint | New matching predicate | Implement `ConstraintEvaluator`, register in `ConstraintRegistry` |
| Interaction | New input gesture type | Implement `InteractionHandler`, register in `InteractionRegistry` |
| Feedback | New output channel | Implement `FeedbackChannel`, register in `FeedbackRegistry` |

## What Cannot Be Extended

| Concern | Reason |
|---|---|
| Runtime pipeline phases | Fixed order is a correctness guarantee |
| CRL resolution algorithm | Core invariant — must be stable |
| Command structure | Sealed type — exhaustive matching required |
| Tick mechanism | Single source of truth |
| WorldState shape | Snapshot contract must remain stable |

## Behavior Plugin

The primary extension point.

### Contract

```
BehaviorPlugin {
    id:         BehaviorId          // unique, stable, used in scene JSON
    phase:      Phase               // which pipeline phase
    eventTypes: Set<EventType>      // which events to subscribe to
    create(config: Map<String, Any>): Behavior
    validateConfig(config: Map<String, Any>): ValidationResult
}
```

### Registration

```kotlin
registry.register(MyBehaviorPlugin())
```

Registration happens at engine startup — not at scene load time.
Dynamic registration at runtime is not supported in v1.

### Config Validation

`validateConfig()` is called at scene load time.
If validation fails, the scene is rejected (not at runtime).

This ensures:
- no runtime config errors
- scenes are fully validatable before play

## Component Definition

Components declare what data fields an entity can have.

```
ComponentDefinition {
    type:             ComponentType
    defaultProperties: Map<PropertyKey, PropertyValue>
    fieldPolicies:    Map<PropertyKey, ResolutionPolicy>  // optional, overrides engine defaults
}
```

Components are data-only. They do not contain logic.

## Constraint Evaluator

Constraints define matching logic between entity properties.

```
ConstraintEvaluator {
    type: String
    evaluate(source: EntitySnapshot, target: EntitySnapshot, config: Map<String, Any>): Boolean
}
```

Built-in evaluators (v1):

| Type | Logic |
|---|---|
| `propertyEquals` | `source[key] == target[key]` |
| `propertyIncludes` | `target[key] contains source[key]` |
| `tagMatch` | `source.tags intersect target.tags != empty` |
| `allOf` | All child constraints must pass |
| `anyOf` | At least one child constraint must pass |

## Feedback Channel

Feedback channels handle output that crosses the boundary of core into platform.

```
FeedbackChannel {
    type: String    // "sound", "animation", "haptic", "speech", "particle"
    handle(command: TriggerFeedback, context: FeedbackContext)
}
```

Platform availability:

| Channel | Android | iOS | Desktop | Web |
|---|---|---|---|---|
| `sound` | ✓ | ✓ | ✓ | ✓ |
| `animation` | ✓ | ✓ | ✓ | ✓ |
| `haptic` | ✓ | ✓ | — | — |
| `speech` | ✓ | ✓ | ✓ | ✓ |
| `particle` | ✓ | ✓ | ✓ | ✓ |

Unavailable channels silently no-op. No error, no fallback required in v1.

## Invariants

1. All plugins are registered before any scene is loaded.
2. Plugin IDs are globally unique — duplicate registration is an error at startup.
3. Plugins never modify core engine state directly.
4. `BehaviorPlugin.create()` must return a new instance per call (behaviors are not singletons).
5. `ConstraintEvaluator.evaluate()` is pure — no side effects.
