package org.ollu.mini.scenes

import mimi.core.model.BehaviorRef
import mimi.core.model.PropertyValue
import mimi.core.world.*
import org.ollu.mini.catalog.CatalogEntry

object BuiltinScenes {

    val all: List<Pair<CatalogEntry, SceneDefinition>> = listOf(
        entry("sort_fruits_01",  "Разложи фрукты",       "Перетащи фрукты в корзину",                    2, 5) to
            DemoScene.scene.copy(sceneId = "sort_fruits_01"),
        entry("match_colors_01", "Совмести цвета",        "Перетащи кружок к квадрату своего цвета",      2, 6) to
            matchColorsScene(),
        entry("sort_animals_01", "Животные идут домой",   "Помоги каждому животному найти свой дом",      3, 6) to
            sortAnimalsScene(),
        entry("match_shapes_01", "Совмести фигуры",       "Перетащи каждую фигуру к её контуру",          2, 5) to
            matchShapesScene(),
        entry("sort_food_01",    "Фрукты и овощи",        "Рассортируй еду по корзинам",                  3, 6) to
            sortFoodScene(),
        entry("match_numbers_01","Считаем вместе",        "Сопоставь цифру с нужным количеством точек",   3, 6) to
            matchNumbersScene()
    )

    val entries:  List<CatalogEntry>           get() = all.map { it.first }
    val sceneById: Map<String, SceneDefinition> get() = all.associate { (e, s) -> e.id to s }

    private fun entry(id: String, title: String, desc: String, ageMin: Int, ageMax: Int) =
        CatalogEntry(id = id, title = title, description = desc,
                     url = "local://$id", ageMin = ageMin, ageMax = ageMax)
}

// ── Shared ────────────────────────────────────────────────────────────────────

internal fun sharedBehaviors() = listOf(
    SceneBehaviorBinding(BehaviorRef("progressTracker")),
    SceneBehaviorBinding(BehaviorRef("snapToTarget")),
    SceneBehaviorBinding(BehaviorRef("adaptiveHint")),
    SceneBehaviorBinding(BehaviorRef("playSoundOnEvent",
        mapOf("on" to "sceneCompleted", "sound" to "complete")))
)

// ── Scene builders ────────────────────────────────────────────────────────────

internal fun matchColorsScene(): SceneDefinition {
    fun circle(id: String, color: String, emoji: String, x: Float) = EntityDefinition(
        id = id, components = listOf("drag"),
        properties = mapOf(
            "position" to PropertyValue.Vec2(x, 250f), "zOrder" to PropertyValue.Num(1.0),
            "color"    to PropertyValue.Text(color),   "emoji"  to PropertyValue.Text(emoji)
        ),
        tags = mapOf("group" to "circles")
    )
    fun box(id: String, color: String, emoji: String, x: Float) = EntityDefinition(
        id = id, components = listOf("dropTarget"),
        properties = mapOf(
            "position"       to PropertyValue.Vec2(x, 700f), "zOrder" to PropertyValue.Num(0.0),
            "constraintType" to PropertyValue.Text("propertyEquals"),
            "source"         to PropertyValue.Text("color"),
            "target"         to PropertyValue.Text("color"),
            "color"          to PropertyValue.Text(color),   "emoji"  to PropertyValue.Text(emoji)
        )
    )
    return SceneDefinition(
        sceneId    = "match_colors_01",
        entities   = listOf(
            circle("red_circle",   "red",   "🔴", 200f),
            circle("blue_circle",  "blue",  "🔵", 500f),
            circle("green_circle", "green", "🟢", 800f),
            box("red_box",   "red",   "🟥", 200f),
            box("blue_box",  "blue",  "🟦", 500f),
            box("green_box", "green", "🟩", 800f)
        ),
        objectives = listOf(ObjectiveDefinition("obj1", "allMatched", mapOf("group" to "circles"))),
        behaviors  = sharedBehaviors()
    )
}

internal fun sortAnimalsScene(): SceneDefinition {
    fun animal(id: String, home: String, emoji: String, x: Float) = EntityDefinition(
        id = id, components = listOf("drag"),
        properties = mapOf(
            "position" to PropertyValue.Vec2(x, 250f), "zOrder" to PropertyValue.Num(1.0),
            "home"     to PropertyValue.Text(home),    "emoji"  to PropertyValue.Text(emoji)
        ),
        tags = mapOf("group" to "animals")
    )
    fun home(id: String, homeKey: String, emoji: String, x: Float) = EntityDefinition(
        id = id, components = listOf("dropTarget"),
        properties = mapOf(
            "position"       to PropertyValue.Vec2(x, 700f), "zOrder" to PropertyValue.Num(0.0),
            "constraintType" to PropertyValue.Text("propertyEquals"),
            "source"         to PropertyValue.Text("home"),
            "target"         to PropertyValue.Text("home"),
            "home"           to PropertyValue.Text(homeKey), "emoji"  to PropertyValue.Text(emoji)
        )
    )
    return SceneDefinition(
        sceneId    = "sort_animals_01",
        entities   = listOf(
            animal("cat",  "house", "🐱", 200f),
            animal("fish", "bowl",  "🐠", 500f),
            animal("bird", "nest",  "🐦", 800f),
            home("house", "house", "🏠", 200f),
            home("bowl",  "bowl",  "🫙", 500f),
            home("nest",  "nest",  "🪺", 800f)
        ),
        objectives = listOf(ObjectiveDefinition("obj1", "allMatched", mapOf("group" to "animals"))),
        behaviors  = sharedBehaviors()
    )
}

internal fun matchShapesScene(): SceneDefinition {
    fun shape(id: String, shape: String, emoji: String, x: Float) = EntityDefinition(
        id = id, components = listOf("drag"),
        properties = mapOf(
            "position" to PropertyValue.Vec2(x, 250f), "zOrder" to PropertyValue.Num(1.0),
            "shape"    to PropertyValue.Text(shape),   "emoji"  to PropertyValue.Text(emoji)
        ),
        tags = mapOf("group" to "shapes")
    )
    fun outline(id: String, shape: String, emoji: String, x: Float) = EntityDefinition(
        id = id, components = listOf("dropTarget"),
        properties = mapOf(
            "position"       to PropertyValue.Vec2(x, 700f), "zOrder" to PropertyValue.Num(0.0),
            "constraintType" to PropertyValue.Text("propertyEquals"),
            "source"         to PropertyValue.Text("shape"),
            "target"         to PropertyValue.Text("shape"),
            "shape"          to PropertyValue.Text(shape),   "emoji"  to PropertyValue.Text(emoji)
        )
    )
    return SceneDefinition(
        sceneId    = "match_shapes_01",
        entities   = listOf(
            shape("circle",   "circle",   "🔵", 200f),
            shape("triangle", "triangle", "🔺", 500f),
            shape("square",   "square",   "🟦", 800f),
            outline("circle_target",   "circle",   "⭕", 200f),
            outline("triangle_target", "triangle", "🔻", 500f),
            outline("square_target",   "square",   "🔲", 800f)
        ),
        objectives = listOf(ObjectiveDefinition("obj1", "allMatched", mapOf("group" to "shapes"))),
        behaviors  = sharedBehaviors()
    )
}

internal fun sortFoodScene(): SceneDefinition {
    fun food(id: String, type: String, emoji: String, x: Float, y: Float) = EntityDefinition(
        id = id, components = listOf("drag"),
        properties = mapOf(
            "position" to PropertyValue.Vec2(x, y),  "zOrder" to PropertyValue.Num(1.0),
            "type"     to PropertyValue.Text(type),  "emoji"  to PropertyValue.Text(emoji)
        ),
        tags = mapOf("group" to "food")
    )
    fun bin(id: String, type: String, emoji: String, x: Float) = EntityDefinition(
        id = id, components = listOf("dropTarget"),
        properties = mapOf(
            "position"       to PropertyValue.Vec2(x, 750f), "zOrder" to PropertyValue.Num(0.0),
            "constraintType" to PropertyValue.Text("propertyEquals"),
            "source"         to PropertyValue.Text("type"),
            "target"         to PropertyValue.Text("type"),
            "type"           to PropertyValue.Text(type),    "emoji"  to PropertyValue.Text(emoji)
        )
    )
    return SceneDefinition(
        sceneId    = "sort_food_01",
        entities   = listOf(
            food("apple",    "fruit",     "🍎", 150f, 200f),
            food("banana",   "fruit",     "🍌", 420f, 280f),
            food("carrot",   "vegetable", "🥕", 580f, 200f),
            food("broccoli", "vegetable", "🥦", 850f, 280f),
            bin("fruit_bin",  "fruit",     "🍱", 300f),
            bin("veggie_bin", "vegetable", "🥗", 700f)
        ),
        objectives = listOf(ObjectiveDefinition("obj1", "allMatched", mapOf("group" to "food"))),
        behaviors  = sharedBehaviors()
    )
}

internal fun matchNumbersScene(): SceneDefinition {
    fun card(id: String, number: String, emoji: String, x: Float) = EntityDefinition(
        id = id, components = listOf("drag"),
        properties = mapOf(
            "position" to PropertyValue.Vec2(x, 250f), "zOrder"  to PropertyValue.Num(1.0),
            "number"   to PropertyValue.Text(number),  "emoji"   to PropertyValue.Text(emoji)
        ),
        tags = mapOf("group" to "numbers")
    )
    fun dots(id: String, number: String, emoji: String, x: Float) = EntityDefinition(
        id = id, components = listOf("dropTarget"),
        properties = mapOf(
            "position"       to PropertyValue.Vec2(x, 700f), "zOrder" to PropertyValue.Num(0.0),
            "constraintType" to PropertyValue.Text("propertyEquals"),
            "source"         to PropertyValue.Text("number"),
            "target"         to PropertyValue.Text("number"),
            "number"         to PropertyValue.Text(number),  "emoji"  to PropertyValue.Text(emoji)
        )
    )
    return SceneDefinition(
        sceneId    = "match_numbers_01",
        entities   = listOf(
            card("one",   "1", "1️⃣", 200f),
            card("two",   "2", "2️⃣", 500f),
            card("three", "3", "3️⃣", 800f),
            dots("one_dots",   "1", "⚀", 200f),
            dots("two_dots",   "2", "⚁", 500f),
            dots("three_dots", "3", "⚂", 800f)
        ),
        objectives = listOf(ObjectiveDefinition("obj1", "allMatched", mapOf("group" to "numbers"))),
        behaviors  = sharedBehaviors()
    )
}
