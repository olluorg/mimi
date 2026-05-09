package org.ollu.mini

import androidx.compose.runtime.*
import mimi.core.world.SceneDefinition
import org.ollu.mini.catalog.CatalogScreen
import org.ollu.mini.game.GameScreen
import org.ollu.mini.theme.AppTheme

private sealed class Screen {
    object Catalog : Screen()
    data class Game(val scene: SceneDefinition) : Screen()
}

@Composable
fun App(
    onThemeChanged: @Composable (isDark: Boolean) -> Unit = {}
) = AppTheme(onThemeChanged) {
    var screen by remember { mutableStateOf<Screen>(Screen.Catalog) }

    when (val s = screen) {
        is Screen.Catalog -> CatalogScreen(
            onSceneSelected = { scene -> screen = Screen.Game(scene) }
        )
        is Screen.Game -> GameScreen(
            scene  = s.scene,
            onBack = { screen = Screen.Catalog }
        )
    }
}
