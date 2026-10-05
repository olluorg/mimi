package mimi.core.scene

import mimi.core.behavior.BehaviorRegistry
import mimi.core.behaviors.AwaitResponsePlugin
import mimi.core.model.PropertyValue
import mimi.core.model.SCENE_ENTITY_ID
import mimi.core.model.ValidationResult
import mimi.core.objective.ObjectiveSystem
import mimi.core.plugin.ConstraintRegistry
import mimi.core.world.EntityDefinition
import mimi.core.world.ObjectiveDefinition
import mimi.core.world.SceneDefinition
import mimi.core.world.SceneFsmActionDefinition
import mimi.core.world.SpeechVocabulary

/**
 * Outcome of validating one scene.
 *
 * Errors mean the scene cannot be trusted to run — a child would hit a dead end,
 * a crash, or an objective that can never complete. Warnings mean the scene runs
 * but something looks unintended (an unused group, a target nothing can reach).
 */
data class SceneValidationReport(
    val sceneId:  String,
    val errors:   List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val isValid: Boolean get() = errors.isEmpty()

    fun toValidationResult(): ValidationResult =
        if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
}

/**
 * Components the renderer and input layer know how to draw and hit-test.
 * A scene using anything else renders as an inert tile, so it is an error.
 */
private val KNOWN_COMPONENTS = setOf(
    "drag", "dropTarget", "tap", "traceTarget",
    "connectSource", "connectTarget",
    "mazeStart", "mazeExit", "mazePath", "mazeWall"
)

/** Objectives whose completion is driven by a `group` tag on entities. */
private val GROUP_OBJECTIVES = setOf("allMatched", "allPairsMatched")

/**
 * Components that carry no geometry of their own: they are invisible state
 * holders the engine reads, so they are exempt from the placement check.
 */
private val NON_VISUAL_COMPONENTS = setOf("mazePath")

/**
 * Properties that place an entity when a single `position` point does not:
 * a polyline trace uses `waypoints`, a circular trace uses `center` + `radius`.
 */
private val ALTERNATIVE_PLACEMENTS = setOf("waypoints", "center")

/**
 * Every property whose value a tile may end up displaying.
 *
 * `face` is here because it was missed: memory cards keep their picture there,
 * so a check that looked only at `emoji` and `label` passed them while the card
 * faces rendered as nothing.
 */
private val DISPLAYED_TEXT_KEYS = listOf("emoji", "label", "name", "face", "faceLabel", "speech")

/** Properties the renderer turns into a picture rather than text. */
private val PICTURE_KEYS = listOf("image", "shape", "count", "pictogram")

private fun EntityDefinition.drawsPicture(): Boolean = PICTURE_KEYS.any { properties[it] != null }

/**
 * A picture a tile is trying to make out of characters.
 *
 * This has been the single most persistent source of scenes that look broken,
 * and every attempt to work around it failed: the web build draws nothing for
 * these, and bundling emoji fonts did not change that — Compose picks a font
 * from a family by weight, it does not fall back glyph by glyph. So pictures
 * are drawn or photographed, never typed, and this is an error rather than a
 * warning to keep it that way.
 */
private fun EntityDefinition.unrenderableText(): String? {
    if (drawsPicture()) return null
    for (key in DISPLAYED_TEXT_KEYS) {
        val text = (properties[key] as? PropertyValue.Text)?.value?.trim().orEmpty()
        if (text.isNotEmpty() && text.any { !it.hasGlyph() }) return text
    }
    return null
}

/**
 * A basket a child can only tell apart by reading it.
 *
 * A drop target's whole job is to say what belongs in it, and five scenes said
 * it in writing — "Большой", "Овощи", and one that fell through to its own id
 * and drew `fruit_bin` across the board. For a child who does not read, that is
 * an unlabelled basket: they drag until something sticks, and the sorting the
 * scene is meant to teach never happens.
 *
 * Whatever the scene sorts by is available to draw with: a size basket is drawn
 * at the size it takes, a colour basket in its colour, a category basket holds
 * an example. Unlike [unrenderableText] this is not about glyphs — these words
 * render perfectly. They just cannot be read.
 */
private fun EntityDefinition.readableOnly(): Boolean {
    if ("dropTarget" !in components) return false
    return !drawsPicture()
}

/**
 * A tile whose picture is a word.
 *
 * Cards carry no captions: the children using the app do not read. A letter or
 * a digit may still be a tile's whole content where it is the thing being
 * learned, but anything longer is a word, the renderer draws nothing for it,
 * and the child is left pressing a blank card.
 */
private fun EntityDefinition.wordAsPicture(): String? {
    if (components.none { it in INTERACTIVE_COMPONENTS }) return null
    if (drawsPicture() || properties["faceImage"] != null) return null
    return listOf("emoji", "face", "label")
        .firstNotNullOfOrNull { (properties[it] as? PropertyValue.Text)?.value?.trim()?.takeIf(String::isNotEmpty) }
        ?.takeIf { it.length > 2 }
}

/** Motions the renderer can act out when a card is got right. Kept in step with RewardMotion in the UI. */
private val REWARD_MOTIONS = setOf("drive", "fly", "jump", "spin", "wiggle")

/** Sounds the microphone can name. Kept in step with VowelClassifier in the UI. */
private val EXPECTABLE_SOUNDS = setOf("а", "о", "у", "и", "э")

private val INTERACTIVE_COMPONENTS = setOf("tap", "drag", "dropTarget", "connectSource", "connectTarget")

/** Characters that are reliably letters, digits or punctuation on every platform. */
private fun Char.hasGlyph(): Boolean =
    this == ' ' || code in 0x21..0x7E ||        // ASCII
        code in 0xA0..0xFF ||                   // Latin-1 supplement
        code in 0x400..0x4FF ||                 // Cyrillic
        this in "«»—–…"

object SceneValidator {

    /** Back-compat entry point: errors only, default objective/constraint registries. */
    fun validate(scene: SceneDefinition, behaviorRegistry: BehaviorRegistry): ValidationResult =
        inspect(scene, behaviorRegistry).toValidationResult()

    fun inspect(
        scene:              SceneDefinition,
        behaviorRegistry:   BehaviorRegistry,
        constraintRegistry: ConstraintRegistry? = null,
        objectiveTypes:     Set<String> = ObjectiveSystem.defaultEvaluators().keys
    ): SceneValidationReport {
        val findings = Findings()
        findings.check(scene, behaviorRegistry, constraintRegistry, objectiveTypes)
        return SceneValidationReport(scene.sceneId, findings.errors, findings.warnings)
    }

    private fun Findings.check(
        scene:              SceneDefinition,
        behaviorRegistry:   BehaviorRegistry,
        constraintRegistry: ConstraintRegistry?,
        objectiveTypes:     Set<String>
    ) {
        // Behaviors are scene-global, so they are checked once rather than per level.
        validateBehaviors(scene, behaviorRegistry)

        // A multi-level scene swaps its entities and objectives wholesale on every
        // level, so each level is effectively its own scene and is checked as one.
        if (scene.isMultiLevel) {
            if (scene.entities.isNotEmpty() || scene.objectives.isNotEmpty()) {
                warnings += "Scene declares both levels[] and top-level entities/objectives; " +
                    "the top-level ones are dead weight — levels[0] shadows them"
            }
            scene.levels.map { it.levelId }.duplicates().forEach {
                errors += "Duplicate level ID '$it'"
            }
            scene.levels.forEachIndexed { index, level ->
                validateLayer(
                    label              = "level '${level.levelId}' (#$index)",
                    entities           = level.entities,
                    objectives         = level.objectives,
                    behaviorRegistry   = behaviorRegistry,
                    constraintRegistry = constraintRegistry,
                    objectiveTypes     = objectiveTypes
                )
            }
        } else {
            validateLayer(
                label              = "scene",
                entities           = scene.entities,
                objectives         = scene.objectives,
                behaviorRegistry   = behaviorRegistry,
                constraintRegistry = constraintRegistry,
                objectiveTypes     = objectiveTypes
            )
        }

        validateSpokenInstruction(scene)
        validateTouchIsAnswered(scene)
        validateStateMachine(scene)
        validateSpeech(scene)
    }

    // ── One entity/objective layer (a level, or the scene itself) ─────────────

    private fun Findings.validateLayer(
        label:              String,
        entities:           List<EntityDefinition>,
        objectives:         List<ObjectiveDefinition>,
        behaviorRegistry:   BehaviorRegistry,
        constraintRegistry: ConstraintRegistry?,
        objectiveTypes:     Set<String>
    ) {
        if (entities.isEmpty()) errors += "$label has no entities"
        if (objectives.isEmpty()) errors += "$label has no objectives — it can never be completed"

        entities.map { it.id }.duplicates().forEach { errors += "$label: duplicate entity ID '$it'" }

        if (entities.any { it.id == SCENE_ENTITY_ID }) {
            errors += "$label: entity ID '$SCENE_ENTITY_ID' is reserved by the engine"
        }

        for (entity in entities) {
            validatePlacement(label, entity)
            entity.components.filterNot { it in KNOWN_COMPONENTS }.forEach {
                errors += "$label: entity '${entity.id}' uses unknown component '$it'"
            }
            if (entity.components.isEmpty()) {
                warnings += "$label: entity '${entity.id}' has no components — it is decoration only"
            }
            entity.unrenderableText()?.let { text ->
                errors += "$label: entity '${entity.id}' tries to show \"$text\" as characters; " +
                    "the web build draws nothing for these — use image, shape, count or pictogram"
            }
            entity.wordAsPicture()?.let { word ->
                errors += "$label: entity '${entity.id}' shows the word \"$word\" as its picture; " +
                    "cards carry no text — give it an image, shape, count or pictogram"
            }
            if (entity.readableOnly()) {
                errors += "$label: drop target '${entity.id}' is identified by text only; " +
                    "a child who cannot read sees an unlabelled basket — give it an image, " +
                    "shape, count or pictogram"
            }
            validateConstraint(label, entity, constraintRegistry)
            (entity.properties["expectSound"] as? PropertyValue.Text)?.value?.let { sound ->
                if (sound.lowercase() !in EXPECTABLE_SOUNDS) {
                    errors += "$label: entity '${entity.id}' expects sound '$sound'; only the vowels " +
                        EXPECTABLE_SOUNDS.joinToString(", ") + " can be heard — anything else never earns its bonus"
                }
            }
            (entity.properties["rewardMotion"] as? PropertyValue.Text)?.value?.let { motion ->
                if (motion !in REWARD_MOTIONS) {
                    errors += "$label: entity '${entity.id}' has rewardMotion='$motion'; expected one of " +
                        REWARD_MOTIONS.joinToString(", ") + " — anything else just sits still"
                }
            }
        }

        validateObjectives(label, entities, objectives, objectiveTypes)

        for (entity in entities) {
            for (ref in entity.behaviors) {
                if (!behaviorRegistry.isRegistered(ref.type)) {
                    errors += "$label: entity '${entity.id}' uses unknown behavior '${ref.type}'"
                } else {
                    errors += behaviorRegistry.validateRef(ref).errorList().map { "$label: ${entity.id}: $it" }
                }
            }
        }
    }

    /**
     * An entity the renderer cannot place never reaches the screen, and the child
     * is left with a scene that silently misses a piece. Placement can come from a
     * `position` point, a `waypoints` polyline, or a `center` + `radius` circle.
     */
    private fun Findings.validatePlacement(
        label:  String,
        entity: EntityDefinition
    ) {
        if (entity.components.any { it in NON_VISUAL_COMPONENTS }) return
        if (entity.properties["position"] != null) return

        val placement = ALTERNATIVE_PLACEMENTS.firstOrNull { entity.properties[it] != null }
        if (placement == null) {
            errors += "$label: entity '${entity.id}' has no 'position', 'waypoints' or 'center' — " +
                "the renderer cannot place it"
            return
        }
        if (placement == "center" && entity.properties["radius"] == null) {
            errors += "$label: entity '${entity.id}' is placed by 'center' but has no 'radius'"
        }
    }

    private fun Findings.validateConstraint(
        label:              String,
        entity:             EntityDefinition,
        constraintRegistry: ConstraintRegistry?
    ) {
        if ("dropTarget" !in entity.components) return
        val constraintType = (entity.properties["constraintType"] as? PropertyValue.Text)?.value
        if (constraintType == null) {
            errors += "$label: dropTarget '${entity.id}' has no 'constraintType' — nothing can ever match it"
            return
        }
        if (constraintRegistry != null && !constraintRegistry.isRegistered(constraintType)) {
            errors += "$label: dropTarget '${entity.id}' uses unknown constraint '$constraintType'"
        }
        // propertyEquals/propertyIncludes read their key names from these properties;
        // without them the evaluator silently returns false and the scene is unwinnable.
        if (constraintType == "propertyEquals" || constraintType == "propertyIncludes") {
            val hasSource = entity.properties["source"] != null || entity.properties["sourceProperty"] != null
            val hasTarget = entity.properties["target"] != null || entity.properties["targetProperty"] != null
            if (!hasSource || !hasTarget) {
                errors += "$label: dropTarget '${entity.id}' with '$constraintType' needs both " +
                    "'source' and 'target' properties naming the keys to compare"
            }
        }
    }

    private fun Findings.validateObjectives(
        label:          String,
        entities:       List<EntityDefinition>,
        objectives:     List<ObjectiveDefinition>,
        objectiveTypes: Set<String>
    ) {
        val objectiveIds = objectives.map { it.id }
        objectiveIds.duplicates().forEach { errors += "$label: duplicate objective ID '$it'" }

        val objIdSet  = objectiveIds.toSet()
        val entityIds = entities.map { it.id }.toSet()

        for (obj in objectives) {
            if (obj.type !in objectiveTypes) {
                errors += "$label: objective '${obj.id}' has unknown type '${obj.type}' — " +
                    "known types: ${objectiveTypes.sorted().joinToString(", ")}"
            }
            val req = obj.requires
            if (req != null && req !in objIdSet) {
                errors += "$label: objective '${obj.id}' requires unknown objective '$req'"
            }
            if (req == obj.id) {
                errors += "$label: objective '${obj.id}' requires itself"
            }
            validateObjectiveReachable(label, obj, entities, entityIds)
        }

        if (hasPrereqCycle(objectives)) {
            errors += "$label: objective prerequisites contain a cycle"
        }
    }

    /**
     * Unreachable-objective detection, required by the engine spec: an objective
     * keyed on a group or an entity that the level never declares can never fire,
     * leaving the child stuck on a scene with no way to finish.
     */
    private fun Findings.validateObjectiveReachable(
        label:     String,
        obj:       ObjectiveDefinition,
        entities:  List<EntityDefinition>,
        entityIds: Set<String>
    ) {
        val where = "$label: objective '${obj.id}'"

        fun requireConfig(key: String): String? {
            val value = obj.config[key]?.takeIf { it.isNotBlank() }
            if (value == null) {
                errors += "$where of type '${obj.type}' needs config.$key"
            }
            return value
        }

        fun requireGroup(key: String = "group") {
            val group = requireConfig(key) ?: return
            if (entities.none { it.tags["group"] == group }) {
                errors += "$where waits on group '$group', but no entity carries " +
                    "tag group='$group' — it can never complete"
            }
        }

        when (obj.type) {
            in GROUP_OBJECTIVES, "sequenceCompleted", "traceCompleted", "anySelected" -> requireGroup()

            "geometrySelection" -> {
                requireGroup()
                val select = obj.config["select"]
                if (select != null && select !in setOf("largest", "smallest")) {
                    errors += "$where has config.select='$select'; expected 'largest' or 'smallest'"
                }
            }

            "interactionCount" -> {
                val raw = requireConfig("count") ?: return
                val count = raw.toIntOrNull()
                if (count == null || count <= 0) {
                    errors += "$where has config.count='$raw'; expected a positive number of turns"
                }
            }

            "allSorted" -> {
                val tag = requireConfig("tag") ?: return
                if (entities.none { tag in it.tags }) {
                    errors += "$where waits on tag '$tag', but no entity carries it — " +
                        "it can never complete"
                }
            }

            "sequenceComplete" -> {
                val raw = requireConfig("sequence") ?: return
                val ids = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (ids.isEmpty()) {
                    errors += "$where has an empty config.sequence"
                    return
                }
                ids.filterNot { it in entityIds }.forEach {
                    errors += "$where expects entity '$it' in its sequence, but no such entity exists"
                }
            }

            "traceComplete" -> {
                val pathId = requireConfig("pathId") ?: return
                if (pathId !in entityIds) {
                    errors += "$where traces path '$pathId', which is not an entity in this scene"
                }
                val accuracy = obj.config["minAccuracy"]
                if (accuracy != null) {
                    val parsed = accuracy.toFloatOrNull()
                    if (parsed == null || parsed !in 0f..1f) {
                        errors += "$where has config.minAccuracy='$accuracy'; expected a number in 0..1"
                    }
                }
            }

            // Completes when any entity whose <property> equals <value> is selected,
            // so both keys are required and at least one entity must be able to match.
            "entitySelected" -> {
                val property = requireConfig("property") ?: return
                val valueFrom = obj.config["valueFrom"]
                if (valueFrom != null) {
                    // The answer is drawn at play time; what can be checked is
                    // that there is something to draw from.
                    if (entities.none { it.properties[property] is PropertyValue.Text }) {
                        errors += "$where draws its answer from '$valueFrom', but no entity " +
                            "has a '$property' to choose — it can never complete"
                    }
                    return
                }
                val value    = requireConfig("value") ?: return
                val reachable = entities.any {
                    (it.properties[property] as? PropertyValue.Text)?.value == value
                }
                if (!reachable) {
                    errors += "$where waits for an entity with $property='$value', but no entity " +
                        "in this scene has it — it can never complete"
                }
            }
        }
    }

    /**
     * Every scene has to say what to do.
     *
     * A child playing alone cannot read the title and has nobody to ask. More
     * than half the catalog used to open in silence, leaving the game to be
     * worked out by trial and error — which for a non-verbal child is
     * indistinguishable from a screen that does not respond.
     */
    private fun Findings.validateSpokenInstruction(scene: SceneDefinition) {
        val speaks = scene.behaviors.any { binding ->
            val ref = binding.behaviorRef
            val on  = ref.config["on"] ?: "sceneStarted"
            ref.type == "audioPrompt" && on == "sceneStarted" &&
                // `soundFrom` counts: in a listening game the recording is the
                // question, and naming it per round is the only way to ask a
                // different one each time.
                (!ref.config["text"].isNullOrBlank() || !ref.config["sound"].isNullOrBlank() ||
                    !ref.config["soundFrom"].isNullOrBlank())
        }
        if (!speaks) {
            errors += "Scene says nothing when it opens — add an audioPrompt on 'sceneStarted' " +
                "so the child is told what to do"
        }
    }

    /**
     * A tile the child can keep pressing has to answer every press.
     *
     * `repeatable` deliberately leaves the tile unchanged so it can be played
     * with again — which means nothing on screen moves. Without a voice or a
     * sound attached, pressing it does nothing observable at all, and a
     * cause-and-effect game becomes a picture of one.
     */
    private fun Findings.validateTouchIsAnswered(
        scene:    SceneDefinition
    ) {
        val repeatable = scene.behaviors.map { it.behaviorRef }.filter {
            it.type == "freeSelection" && it.config["repeatable"]?.toBooleanStrictOrNull() == true
        }
        if (repeatable.isEmpty()) return

        // A repeatable tile never settles into a chosen state, and a hidden one is
        // gone rather than chosen — so an objective waiting for "something is
        // selected" waits forever. The child presses everything, hears it all,
        // and the scene simply never ends.
        val waitsForSelection = scene.objectivesForLevel(0).any { it.type == "anySelected" }
        if (waitsForSelection) {
            errors += "objective 'anySelected' can never complete here: freeSelection is " +
                "repeatable or hides what it selects, so nothing is ever left in a chosen " +
                "state — use 'interactionCount' instead"
        }

        val speaks = repeatable.any { it.config["speak"]?.toBooleanStrictOrNull() == true }
        if (!speaks) {
            errors += "freeSelection is 'repeatable', so the tile never changes when pressed — " +
                "set speak=true (or give the tile a sound) or the child gets no response at all"
            return
        }

        val group = repeatable.firstNotNullOfOrNull { it.config["group"] }
        val silentTiles = scene.entitiesForLevel(0)
            .filter { group == null || it.tags["group"] == group }
            .filter { it.properties["sound"] == null && it.properties["name"] == null &&
                      it.properties["label"] == null && it.properties["speech"] == null }
            .map { it.id }
        if (silentTiles.isNotEmpty()) {
            warnings += "tiles with nothing to say when pressed: ${silentTiles.joinToString(", ")}"
        }
    }

    // ── Speech games ──────────────────────────────────────────────────────────

    /**
     * A scene marked as a speech game has to actually be one.
     *
     * `meta.speech` is what puts a scene under «Запуск речи» in the catalog, and
     * an adult choosing from that list is trusting that every game in it asks
     * the child to communicate. A tag is easy to stick on a sorting game; this
     * check makes every claimed mode point at the behavior that delivers it.
     */
    private fun Findings.validateSpeech(
        scene:    SceneDefinition
    ) {
        val refs = scene.behaviors.map { it.behaviorRef } +
            allEntities(scene).flatMap { it.behaviors }
        val awaits = refs.filter { it.type == "awaitResponse" }
        val drives = refs.filter { it.type == "voiceDrive" }
        val speakingChoice = refs.any {
            it.type == "freeSelection" && it.config["speak"]?.toBooleanStrictOrNull() == true
        }
        fun accepts(kind: String) = awaits.any { kind in AwaitResponsePlugin.acceptKinds(it.config) }

        val speech = scene.meta.speech
        if (speech == null) {
            if (awaits.isNotEmpty() || drives.isNotEmpty()) {
                warnings += "Scene listens for the child's voice but has no meta.speech — " +
                    "it will not be listed under «Запуск речи»"
            }
            return
        }

        if (speech.stage !in SpeechVocabulary.STAGES) {
            errors += "meta.speech.stage='${speech.stage}'; expected one of " +
                SpeechVocabulary.STAGES.keys.joinToString(", ")
        }
        if (speech.targets.isEmpty()) {
            errors += "meta.speech.targets is empty — say who the game is for " +
                "(${SpeechVocabulary.TARGETS.keys.joinToString(", ")})"
        }
        speech.targets.filterNot { it in SpeechVocabulary.TARGETS }.forEach {
            errors += "meta.speech.targets has unknown '$it'; expected any of " +
                SpeechVocabulary.TARGETS.keys.joinToString(", ")
        }
        if (speech.modes.isEmpty()) {
            errors += "meta.speech.modes is empty — a speech game has to say how it gets the child to communicate"
        }
        if (speech.adultTip.isBlank()) {
            errors += "meta.speech.adultTip is empty — the adult beside the child is most of " +
                "the effect; tell them their part in one sentence"
        }

        for (mode in speech.modes) {
            val missing: String? = when (mode) {
                "aac" -> if (speakingChoice || accepts("tap")) null
                    else "a freeSelection with speak=true, or awaitResponse accepting 'tap'"
                "voice" -> if (drives.isNotEmpty() || accepts("voice")) null
                    else "voiceDrive, or awaitResponse accepting 'voice'"
                "cloze", "imitation" -> if (awaits.isNotEmpty()) null
                    else "awaitResponse — a model, then a pause for the child to answer"
                "drill" -> {
                    val drill = awaits.any { ref ->
                        ref.config["order"] == "random" &&
                            (ref.config["trials"]?.toDoubleOrNull() ?: 0.0) >= SpeechVocabulary.MIN_DRILL_TRIALS
                    }
                    if (drill) null
                    else "awaitResponse with order=random and at least " +
                        "${SpeechVocabulary.MIN_DRILL_TRIALS} trials"
                }
                else -> {
                    errors += "meta.speech.modes has unknown '$mode'; expected any of " +
                        SpeechVocabulary.MODES.keys.joinToString(", ")
                    null
                }
            }
            if (missing != null) {
                errors += "meta.speech claims mode '$mode', but the scene has no mechanic for it — needs $missing"
            }
        }

        // A turn-based speech game ends by counting turns; nothing else in it
        // ever completes, so without this the last word is followed by nothing.
        if (awaits.isNotEmpty() || drives.isNotEmpty()) {
            val counted = allLevels(scene).all { objectives -> objectives.any { it.type == "interactionCount" } }
            if (!counted) {
                errors += "awaitResponse and voiceDrive finish by counting turns — add an " +
                    "'interactionCount' objective or the scene can never end"
            }
        }

        val entities = allEntities(scene)
        for (ref in awaits) {
            val group = ref.config["group"] ?: "targets"
            val members = entities.filter { it.tags["group"] == group }
            if (members.isEmpty()) {
                errors += "awaitResponse asks words from group '$group', but no entity carries it"
            }
            members.filter { member ->
                listOf("prompt", "speech", "name", "sound").none { member.properties[it] != null }
            }.forEach {
                errors += "awaitResponse: '${it.id}' has nothing to model — give it a 'prompt' " +
                    "recording or a 'speech' line"
            }
        }
        for (ref in drives) {
            val target = ref.config["entity"]
            if (target == null || entities.none { it.id == target }) {
                errors += "voiceDrive moves entity '${target ?: "(none)"}', which is not in the scene"
            }
        }
    }

    private fun allEntities(scene: SceneDefinition): List<EntityDefinition> =
        scene.entities + scene.levels.flatMap { it.entities }

    private fun allLevels(scene: SceneDefinition): List<List<ObjectiveDefinition>> =
        if (scene.isMultiLevel) scene.levels.map { it.objectives } else listOf(scene.objectives)

    // ── Scene FSM ─────────────────────────────────────────────────────────────

    private fun Findings.validateStateMachine(scene: SceneDefinition) {
        val fsm = scene.stateMachine ?: return

        val stateIds = fsm.states.map { it.id }
        stateIds.duplicates().forEach { errors += "Duplicate FSM state ID '$it'" }

        val stateIdSet = stateIds.toSet()
        if (fsm.initialState !in stateIdSet) {
            errors += "FSM initial state '${fsm.initialState}' is not declared"
        }
        for (transition in fsm.transitions) {
            if (transition.from !in stateIdSet) {
                errors += "FSM transition references unknown from-state '${transition.from}'"
            }
            if (transition.to !in stateIdSet) {
                errors += "FSM transition references unknown to-state '${transition.to}'"
            }
            if (transition.onEvent == null && transition.afterMs == null) {
                errors += "FSM transition '${transition.from}' -> '${transition.to}' needs onEvent or afterMs"
            }
        }

        // FSM actions address entities, never objectives. Every level's entities are
        // in scope because the FSM outlives level transitions.
        val addressable = buildSet {
            add(SCENE_ENTITY_ID)
            addAll(scene.entities.map { it.id })
            scene.levels.forEach { level -> addAll(level.entities.map { it.id }) }
        }
        fsm.states.forEach { validateFsmActions(it.onEnter, addressable) }
        fsm.transitions.forEach { validateFsmActions(it.actions, addressable) }
    }

    private fun Findings.validateFsmActions(
        actions:     List<SceneFsmActionDefinition>,
        addressable: Set<String>
    ) {
        for (action in actions) {
            val entityId = action.entityId
            if (entityId != null && entityId !in addressable) {
                errors += "FSM action references unknown entity '$entityId'"
            }
            when (action.type) {
                "setProperty" ->
                    if (action.entityId == null || action.field == null || action.value == null) {
                        errors += "FSM setProperty action requires entityId, field, and value"
                    }
                "setEntityState" ->
                    if (action.entityId == null || action.state == null) {
                        errors += "FSM setEntityState action requires entityId and state"
                    }
                "triggerFeedback" ->
                    if (action.feedbackType == null) {
                        errors += "FSM triggerFeedback action requires feedbackType"
                    }
                else -> errors += "Unknown FSM action type '${action.type}'"
            }
        }
    }

    // ── Shared ────────────────────────────────────────────────────────────────

    /** Where validation collects what it finds; the checks below write into it as their receiver. */
    private class Findings {
        val errors   = mutableListOf<String>()
        val warnings = mutableListOf<String>()
    }

    private fun List<String>.duplicates(): Set<String> =
        groupingBy { it }.eachCount().filterValues { it > 1 }.keys

    private fun Findings.validateBehaviors(
        scene:            SceneDefinition,
        behaviorRegistry: BehaviorRegistry
    ) {
        for (binding in scene.behaviors) {
            val ref = binding.behaviorRef
            if (!behaviorRegistry.isRegistered(ref.type)) {
                errors += "Unknown behavior type '${ref.type}'"
            } else {
                errors += behaviorRegistry.validateRef(ref).errorList()
            }
        }
    }

    private fun hasPrereqCycle(objectives: List<ObjectiveDefinition>): Boolean {
        val prereqMap = objectives.associate { it.id to it.requires }
        for (start in prereqMap.keys) {
            val visited = mutableSetOf<String>()
            var current: String? = start
            while (current != null) {
                if (!visited.add(current)) return true
                current = prereqMap[current]
            }
        }
        return false
    }
}
