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
        sortAnimals()
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
    SceneBehaviorBinding(BehaviorRef("playSoundOnEvent", mapOf("on" to "sceneCompleted", "sound" to "complete")))
)

private fun buildCatalogJson() = """
{
  "version": 1,
  "scenes": [
    {
      "id": "sort_fruits_01",
      "title": "Sort the Fruits",
      "description": "Drag fruits into the basket",
      "url": "$BASE_URL/sort_fruits_01.json",
      "ageMin": 2, "ageMax": 5
    },
    {
      "id": "match_colors_01",
      "title": "Match the Colors",
      "description": "Match each circle to its colored square",
      "url": "$BASE_URL/match_colors_01.json",
      "ageMin": 2, "ageMax": 6
    },
    {
      "id": "sort_animals_01",
      "title": "Animals Go Home",
      "description": "Help each animal find its home",
      "url": "$BASE_URL/sort_animals_01.json",
      "ageMin": 3, "ageMax": 6
    }
  ]
}
""".trimIndent()
