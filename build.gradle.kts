plugins {
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.compose.multiplatform).apply(false)
    alias(libs.plugins.kotlin.android).apply(false)
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.kmp.library).apply(false)
    alias(libs.plugins.kotlin.jvm).apply(false)
    alias(libs.plugins.kotlinx.serialization).apply(false)
}

// ── Catalog index ─────────────────────────────────────────────────────────────
// catalog.json is derived from the scene directories, never hand-written. The
// publish workflow regenerates it the same way, and :core:jvmTest fails if the
// committed copy has drifted — the app bundles it for offline use, so a stale
// index means installed apps miss scenes until the next release.

val catalogBaseUrl = "https://olluorg.github.io/mimi"

tasks.register("generateCatalogIndex") {
    group       = "catalog"
    description = "Regenerates catalog/catalog.json from the scene directories."

    val catalogDir = layout.projectDirectory.dir("catalog")
    val indexFile  = catalogDir.file("catalog.json")
    val baseUrl    = catalogBaseUrl

    inputs.dir(catalogDir).withPropertyName("catalog")
    outputs.file(indexFile)

    doLast {
        val dir = catalogDir.asFile
        val ids = dir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith("_") && File(it, "${it.name}.json").isFile }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()

        val entries = ids.joinToString(",\n") { id ->
            """    {
      "id": "$id",
      "url": "$baseUrl/$id/$id.json"
    }"""
        }
        indexFile.asFile.writeText("{\n  \"version\": 2,\n  \"scenes\": [\n$entries\n  ]\n}\n")
        logger.lifecycle("catalog.json regenerated with ${ids.size} scenes")
    }
}
