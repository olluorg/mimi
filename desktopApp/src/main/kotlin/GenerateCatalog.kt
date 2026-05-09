import mimi.core.scene.SceneLoader
import org.ollu.mini.scenes.BuiltinScenes
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

    for ((_, scene) in BuiltinScenes.all) {
        File(outputDir, "${scene.sceneId}.json").writeText(SceneLoader.serialize(scene))
        println("  ✓ ${scene.sceneId}.json")
    }

    File(outputDir, "catalog.json").writeText(buildCatalogJson())
    println("  ✓ catalog.json")
}

private fun buildCatalogJson(): String {
    val entries = BuiltinScenes.entries.joinToString(",\n") { entry ->
        """    {
      "id": "${entry.id}",
      "title": "${entry.title}",
      "description": "${entry.description}",
      "url": "$BASE_URL/${entry.id}.json",
      "ageMin": ${entry.ageMin}, "ageMax": ${entry.ageMax}
    }"""
    }
    return """{
  "version": 1,
  "scenes": [
$entries
  ]
}"""
}
