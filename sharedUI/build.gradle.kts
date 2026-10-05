import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    androidTarget { //We need the deprecated target to have working previews
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    js { browser() }
    wasmJs { browser() }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.compose.foundation)
            api(libs.compose.resources)
            api(libs.compose.ui.tooling.preview)
            api(libs.compose.material3)
            implementation(libs.kermit)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatformSettings)
            implementation(libs.kotlinx.datetime)
            // Lottie animations: rewards, celebrations, the mouth that shows a word.
            implementation(libs.compottie)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            // An in-memory Settings, so the profile rules can be tested without a device.
            implementation(libs.multiplatformSettings.test)
            implementation(libs.compose.ui.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            // The system back button, which Compose Multiplatform does not route on its own.
            implementation(libs.androidx.activityCompose)
            implementation(libs.ktor.client.okhttp)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

    }

    targets
        .withType<KotlinNativeTarget>()
        .matching { it.konanTarget.family.isAppleFamily }
        .configureEach {
            binaries {
                framework {
                    baseName = "SharedUI"
                    isStatic = true
                }
            }
        }
}

dependencies {
    debugImplementation(libs.compose.ui.tooling)
}

// Session tests plan against the real catalog rather than a synthetic one, so
// they need to know where it lives.
tasks.withType<Test>().configureEach {
    val catalogDir = rootProject.file("catalog")
    systemProperty("mimi.catalogDir", catalogDir.absolutePath)
    // Opt-in scene previews: -Pmimi.previewDir=/path [-Pmimi.previewScenes=a,b]
    providers.gradleProperty("mimi.previewDir").orNull?.let { systemProperty("mimi.previewDir", it) }
    providers.gradleProperty("mimi.previewScenes").orNull?.let { systemProperty("mimi.previewScenes", it) }
    dependsOn(rootProject.tasks.named("generateCatalogIndex"))
    inputs.dir(catalogDir).withPropertyName("catalog").withPathSensitivity(PathSensitivity.RELATIVE)
}
android {
    namespace = "org.ollu.mini"
    compileSdk = 36
    defaultConfig {
        minSdk = 23
    }
    // Keep "_cards", "_sounds" and the rest: the default asset filter drops any
    // directory starting with "_", and this module's merge is where it happened.
    // See androidApp/build.gradle.kts for the full story.
    androidResources {
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// ── Bundled catalog sync ──────────────────────────────────────────────────────
// Copies catalog/**  into composeResources so scenes are available offline.
// Flat scenes:  catalog/*.json  → files/catalog/*.json
// Rich scenes:  catalog/<id>/*.json + media/**  → files/catalog/<id>/...
val syncBundledCatalog by tasks.registering(Sync::class) {
    val src = rootProject.file("catalog")
    // catalog.json is generated into the folder this copies, so the two tasks
    // have to be ordered; left implicit they can run at once and bundle a
    // half-written index.
    dependsOn(rootProject.tasks.named("generateCatalogIndex"))
    onlyIf { src.exists() }
    from(src)
    into("${projectDir}/src/commonMain/composeResources/files/catalog")
    include("*.json")        // catalog.json
    include("*/*.json")      // scene JSONs (e.g. find_animal_01/find_animal_01.json)
    include("*/media/**")    // per-scene media (mp3, ogg, …)
    include("_cards/**")     // shared PECS card library: pictures and their audio
    include("_sounds/**")    // generated instrument sounds
    include("_anim/**")      // generated Lottie animations
    exclude("README.md")
    // preserve .gitkeep so the directory stays tracked when catalog/ is empty
    preserve { include(".gitkeep") }
}

afterEvaluate {
    tasks.matching { it.name.contains("ComposeResources") || it.name.contains("NonXmlValueResources") }
        .configureEach { dependsOn(syncBundledCatalog) }
}

// The Compose plugin copies resources into the Android assets with a plain
// copy, so a file deleted from the catalog stays in every later APK — removed
// recordings kept shipping until a clean build. Emptying the output first makes
// the copy behave like a sync.
tasks.matching { it.name.startsWith("copy") && it.name.endsWith("ComposeResourcesToAndroidAssets") }
    .configureEach {
        val stale = outputs.files
        doFirst { stale.forEach { it.deleteRecursively() } }
    }
