#!/usr/bin/env python3
"""
Points every spoken instruction at its recording.

`text` is left in place. The behaviour prefers the recording and falls back to
synthesis, so a line added tomorrow still speaks before anybody bakes it — and
the catalog test says which ones are still waiting.
"""
import json, pathlib

CATALOG = pathlib.Path("/home/sknyazev/projects/mimi/catalog")
LINES = json.loads((CATALOG / "_sounds/speech/speech.json").read_text(encoding="utf-8"))["lines"]


def wire(scene):
    changed = 0
    bindings = list(scene.get("behaviors", []))
    for entity in scene.get("entities", []):
        bindings += entity.get("behaviors", [])
    for level in scene.get("levels", []):
        for entity in level["entities"]:
            bindings += entity.get("behaviors", [])
    for binding in bindings:
        ref = binding["behaviorRef"]
        config = ref.get("config") or {}
        text = config.get("text")
        if ref["type"] != "audioPrompt" or not text:
            continue
        recording = LINES.get(text)
        if recording and config.get("sound") != recording["file"]:
            config["sound"] = recording["file"]
            ref["config"] = config
            changed += 1
    return changed


def wire_asks(scene):
    """A tile's own question gets its recording beside it, as `askSound`."""
    changed = 0
    entities = list(scene.get("entities", []))
    for level in scene.get("levels", []):
        entities += level["entities"]
    for entity in entities:
        props = entity.get("properties", {})
        text = (props.get("askText") or {}).get("value")
        recording = LINES.get(text) if text else None
        if recording and (props.get("askSound") or {}).get("value") != recording["file"]:
            props["askSound"] = {"type": "text", "value": recording["file"]}
            changed += 1
    return changed


total = 0
for path in sorted(CATALOG.glob("*/*.json")):
    scene = json.loads(path.read_text(encoding="utf-8"))
    if "sceneId" not in scene:
        continue
    n = wire(scene) + wire_asks(scene)
    if n:
        path.write_text(json.dumps(scene, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        total += n
        print(f"  {scene['sceneId']}: {n}")
print(f"{total} prompts now play a recording")
