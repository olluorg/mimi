import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    androidTarget {
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
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }

    targets
        .withType<KotlinNativeTarget>()
        .matching { it.konanTarget.family.isAppleFamily }
        .configureEach {
            binaries {
                framework {
                    baseName = "Core"
                    isStatic = true
                }
            }
        }
}

// The catalog integrity tests read the real scene files, so they need to know
// where the catalog lives. Declared as a task input so a scene edit re-runs them.
tasks.withType<Test>().configureEach {
    val catalogDir = rootProject.file("catalog")
    systemProperty("mimi.catalogDir", catalogDir.absolutePath)
    // The index is derived from the scene folders and the tests check the two
    // agree. Without this the two tasks can run at once and the test reads a
    // half-written index — a failure that disappears when rerun, which is worse
    // than one that does not.
    dependsOn(rootProject.tasks.named("generateCatalogIndex"))
    // The naming guard reads the test sources of every module.
    systemProperty("mimi.repoDir", rootProject.projectDir.absolutePath)
    inputs.dir(catalogDir).withPropertyName("catalog").withPathSensitivity(PathSensitivity.RELATIVE)
}

android {
    namespace = "mimi.core"
    compileSdk = 36
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
