package org.ollu.mini.scenes

import mimi.core.model.BehaviorRef
import mimi.core.model.PropertyValue
import mimi.core.world.*

object DemoScene {
    val scene = SceneDefinition(
        sceneId = "sort_fruits_01",
        entities = listOf(
            EntityDefinition(
                id = "apple",
                components = listOf("drag"),
                properties = mapOf(
                    "position" to PropertyValue.Vec2(200f, 300f),
                    "zOrder"   to PropertyValue.Num(1.0),
                    "category" to PropertyValue.Text("fruit"),
                    "emoji"    to PropertyValue.Text("🍎")
                ),
                tags = mapOf("group" to "fruits")
            ),
            EntityDefinition(
                id = "banana",
                components = listOf("drag"),
                properties = mapOf(
                    "position" to PropertyValue.Vec2(500f, 300f),
                    "zOrder"   to PropertyValue.Num(1.0),
                    "category" to PropertyValue.Text("fruit"),
                    "emoji"    to PropertyValue.Text("🍌")
                ),
                tags = mapOf("group" to "fruits")
            ),
            EntityDefinition(
                id = "cherry",
                components = listOf("drag"),
                properties = mapOf(
                    "position" to PropertyValue.Vec2(800f, 300f),
                    "zOrder"   to PropertyValue.Num(1.0),
                    "category" to PropertyValue.Text("fruit"),
                    "emoji"    to PropertyValue.Text("🍒")
                ),
                tags = mapOf("group" to "fruits")
            ),
            // basket: constraintType + source/target tell propertyEquals which keys to compare
            // config built from all Text properties → source="category", target="acceptsCategory"
            // then: dragged["category"] == basket["acceptsCategory"] → "fruit" == "fruit" ✓
            EntityDefinition(
                id = "basket",
                components = listOf("dropTarget"),
                properties = mapOf(
                    "position"        to PropertyValue.Vec2(500f, 700f),
                    "zOrder"          to PropertyValue.Num(0.0),
                    "constraintType"  to PropertyValue.Text("propertyEquals"),
                    "source"          to PropertyValue.Text("category"),
                    "target"          to PropertyValue.Text("acceptsCategory"),
                    "acceptsCategory" to PropertyValue.Text("fruit"),
                    "emoji"           to PropertyValue.Text("🧺")
                )
            )
        ),
        objectives = listOf(
            ObjectiveDefinition(id = "obj1", type = "allMatched", config = mapOf("group" to "fruits"))
        ),
        behaviors = listOf(
            SceneBehaviorBinding(BehaviorRef("progressTracker")),
            SceneBehaviorBinding(BehaviorRef("snapToTarget")),
            SceneBehaviorBinding(BehaviorRef("playSoundOnEvent", mapOf("on" to "sceneCompleted", "sound" to "complete")))
        )
    )
}
