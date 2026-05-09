package org.ollu.mini.catalog

import kotlinx.serialization.Serializable

@Serializable
data class Catalog(
    val version: Int              = 1,
    val scenes:  List<CatalogEntry> = emptyList()
)

@Serializable
data class CatalogEntry(
    val id:          String,
    val title:       String,
    val description: String = "",
    val url:         String,          // absolute URL to scene JSON
    val sha256:      String = "",
    val ageMin:      Int    = 2,
    val ageMax:      Int    = 6
)

const val DEFAULT_CATALOG_URL = "https://olluorg.github.io/mimi/catalog.json"
