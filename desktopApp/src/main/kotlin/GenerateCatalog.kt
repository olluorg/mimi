import mimi.core.model.BehaviorRef
import mimi.core.model.PropertyValue
import mimi.core.scene.SceneLoader
import mimi.core.world.*
import org.ollu.mini.scenes.DemoScene
import java.io.File

private const val BASE_URL = "https://olluorg.github.io/mimi"

fun main() {
    val outputDir = File(System.getProperty("catalog.outputDir", "catalog"))
    println("Generating catalog → ${outputDir.absolutePath}")
    generateCatalog(outputDir)
    println("Done.")
}

fun generateCatalog(outputDir: File) {
    outputDir.mkdirs()

    val scenes = listOf(
        DemoScene.scene.copy(sceneId = "sort_fruits_01"),
        matchColors(),
        sortAnimals(),
        matchShapes(),
        sortFood(),
        matchNumbers()
    )

    for (scene in scenes) {
        File(outputDir, "${scene.sceneId}.json").writeText(SceneLoader.serialize(scene))
        println("  ✓ ${scene.sceneId}.json")
    }

    File(outputDir, "catalog.json").writeText(buildCatalogJson())
    println("  ✓ catalog.json")
}

// ── Scenes ────────────────────────────────────────────────────────────────────

private fun matchColors(): SceneDefinition {
    fun circle(id: String, color: String, emoji: String, x: Float) = EntityDefinition(
        id         = id,
        components = listOf("drag"),
        properties = mapOf(
            "position" to PropertyValue.Vec2(x, 250f),
            "zOrder"   to PropertyValue.Num(1.0),
            "color"    to PropertyValue.Text(color),
            "emoji"    to PropertyValue.Text(emoji)
        ),
        tags = mapOf("group" to "circles")
    )

    fun box(id: String, color: String, emoji: String, x: Float) = EntityDefinition(
        id         = id,
        components = listOf("dropTarget"),
        properties = mapOf(
            "position"       to PropertyValue.Vec2(x, 700f),
            "zOrder"         to PropertyValue.Num(0.0),
            "constraintType" to PropertyValue.Text("propertyEquals"),
            "source"         to PropertyValue.Text("color"),
            "target"         to PropertyValue.Text("color"),
            "color"          to PropertyValue.Text(color),
            "emoji"          to PropertyValue.Text(emoji)
        )
    )

    return SceneDefinition(
        sceneId    = "match_colors_01",
        entities   = listOf(
            circle("red_circle",   "red",   "🔴", 200f),
            circle("blue_circle",  "blue",  "🔵", 500f),
            circle("green_circle", "green", "🟢", 800f),
            box("red_box",    "red",   "🟥", 200f),
            box("blue_box",   "blue",  "🟦", 500f),
            box("green_box",  "green", "🟩", 800f)
        ),
        objectives = listOf(ObjectiveDefinition("obj1", "allMatched", mapOf("group" to "circles"))),
        behaviors  = sharedBehaviors()
    )
}

private fun sortAnimals(): SceneDefinition {
    fun animal(id: String, home: String, emoji: String, x: Float) = EntityDefinition(
        id         = id,
        components = listOf("drag"),
        properties = mapOf(
            "position" to PropertyValue.Vec2(x, 250f),
            "zOrder"   to PropertyValue.Num(1.0),
            "home"     to PropertyValue.Text(home),
            "emoji"    to PropertyValue.Text(emoji)
        ),
        tags = mapOf("group" to "animals")
    )

    fun home(id: String, homeKey: String, emoji: String, x: Float) = EntityDefinition(
        id         = id,
        components = listOf("dropTarget"),
        properties = mapOf(
            "position"       to PropertyValue.Vec2(x, 700f),
            "zOrder"         to PropertyValue.Num(0.0),
            "constraintType" to PropertyValue.Text("propertyEquals"),
            "source"         to PropertyValue.Text("home"),
            "target"         to PropertyValue.Text("home"),
            "home"           to PropertyValue.Text(homeKey),
            "emoji"          to PropertyValue.Text(emoji)
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

private fun sharedBehaviors() = listOf(
    SceneBehaviorBinding(BehaviorRef("progressTracker")),
    SceneBehaviorBinding(BehaviorRef("snapToTarget")),
    SceneBehaviorBinding(BehaviorRef("adaptiveHint")),
    SceneBehaviorBinding(BehaviorRef("playSoundOnEvent", mapOf("on" to "sceneCompleted", "sound" to "complete")))
)

// ── New Scenes ────────────────────────────────────────────────────────────────

private fun matchShapes(): SceneDefinition {
    fun shape(id: String, shape: String, emoji: String, x: Float) = EntityDefinition(
        id         = id,
        components = listOf("drag"),
        properties = mapOf(
            "position" to PropertyValue.Vec2(x, 250f),
            "zOrder"   to PropertyValue.Num(1.0),
            "shape"    to PropertyValue.Text(shape),
            "emoji"    to PropertyValue.Text(emoji)
        ),
        tags = mapOf("group" to "shapes")
    )

    fun outline(id: String, shape: String, emoji: String, x: Float) = EntityDefinition(
        id         = id,
        components = listOf("dropTarget"),
        properties = mapOf(
            "position"       to PropertyValue.Vec2(x, 700f),
            "zOrder"         to PropertyValue.Num(0.0),
            "constraintType" to PropertyValue.Text("propertyEquals"),
            "source"         to PropertyValue.Text("shape"),
            "target"         to PropertyValue.Text("shape"),
            "shape"          to PropertyValue.Text(shape),
            "emoji"          to PropertyValue.Text(emoji)
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

private fun sortFood(): SceneDefinition {
    fun food(id: String, type: String, emoji: String, x: Float, y: Float) = EntityDefinition(
        id         = id,
        components = listOf("drag"),
        properties = mapOf(
            "position" to PropertyValue.Vec2(x, y),
            "zOrder"   to PropertyValue.Num(1.0),
            "type"     to PropertyValue.Text(type),
            "emoji"    to PropertyValue.Text(emoji)
        ),
        tags = mapOf("group" to "food")
    )

    fun bin(id: String, type: String, emoji: String, x: Float) = EntityDefinition(
        id         = id,
        components = listOf("dropTarget"),
        properties = mapOf(
            "position"       to PropertyValue.Vec2(x, 750f),
            "zOrder"         to PropertyValue.Num(0.0),
            "constraintType" to PropertyValue.Text("propertyEquals"),
            "source"         to PropertyValue.Text("type"),
            "target"         to PropertyValue.Text("type"),
            "type"           to PropertyValue.Text(type),
            "emoji"          to PropertyValue.Text(emoji)
        )
    )

    return SceneDefinition(
        sceneId    = "sort_food_01",
        entities   = listOf(
            food("apple",    "fruit",     "🍎", 150f, 200f),
            food("banana",   "fruit",     "🍌", 420f, 280f),
            food("carrot",   "vegetable", "🥕", 580f, 200f),
            food("broccoli", "vegetable", "🥦", 850f, 280f),
            bin("fruit_bin", "fruit",     "🍱", 300f),
            bin("veggie_bin","vegetable", "🥗", 700f)
        ),
        objectives = listOf(ObjectiveDefinition("obj1", "allMatched", mapOf("group" to "food"))),
        behaviors  = sharedBehaviors()
    )
}

private fun matchNumbers(): SceneDefinition {
    fun card(id: String, number: String, emoji: String, x: Float) = EntityDefinition(
        id         = id,
        components = listOf("drag"),
        properties = mapOf(
            "position" to PropertyValue.Vec2(x, 250f),
            "zOrder"   to PropertyValue.Num(1.0),
            "number"   to PropertyValue.Text(number),
            "emoji"    to PropertyValue.Text(emoji)
        ),
        tags = mapOf("group" to "numbers")
    )

    fun dots(id: String, number: String, emoji: String, x: Float) = EntityDefinition(
        id         = id,
        components = listOf("dropTarget"),
        properties = mapOf(
            "position"       to PropertyValue.Vec2(x, 700f),
            "zOrder"         to PropertyValue.Num(0.0),
            "constraintType" to PropertyValue.Text("propertyEquals"),
            "source"         to PropertyValue.Text("number"),
            "target"         to PropertyValue.Text("number"),
            "number"         to PropertyValue.Text(number),
            "emoji"          to PropertyValue.Text(emoji)
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

private fun buildCatalogJson() = """
{
  "version": 1,
  "scenes": [
    {
      "id": "sort_fruits_01",
      "title": "Разложи фрукты",
      "description": "Перетащи фрукты в корзину",
      "url": "$BASE_URL/sort_fruits_01.json",
      "ageMin": 2, "ageMax": 5
    },
    {
      "id": "match_colors_01",
      "title": "Совмести цвета",
      "description": "Перетащи каждый кружок к квадрату своего цвета",
      "url": "$BASE_URL/match_colors_01.json",
      "ageMin": 2, "ageMax": 6
    },
    {
      "id": "sort_animals_01",
      "title": "Животные идут домой",
      "description": "Помоги каждому животному найти свой дом",
      "url": "$BASE_URL/sort_animals_01.json",
      "ageMin": 3, "ageMax": 6
    },
    {
      "id": "match_shapes_01",
      "title": "Совмести фигуры",
      "description": "Перетащи каждую фигуру к её контуру",
      "url": "$BASE_URL/match_shapes_01.json",
      "ageMin": 2, "ageMax": 5
    },
    {
      "id": "sort_food_01",
      "title": "Фрукты и овощи",
      "description": "Рассортируй еду по корзинам",
      "url": "$BASE_URL/sort_food_01.json",
      "ageMin": 3, "ageMax": 6
    },
    {
      "id": "match_numbers_01",
      "title": "Считаем вместе",
      "description": "Сопоставь цифру с нужным количеством точек",
      "url": "$BASE_URL/match_numbers_01.json",
      "ageMin": 3, "ageMax": 6
    }
  ]
}
""".trimIndent()
