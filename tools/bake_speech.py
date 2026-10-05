#!/usr/bin/env python3
"""
Records every spoken instruction in the catalog, once, with voicy.

Until now the app read its instructions with whatever speech engine the device
happened to have. That voice is the first thing a child hears in every game and
it is the one part of the app that sounds like a machine — on the web it is a
browser voice, on Android the system one, on desktop often nothing at all, and
each pronounces the same sentence differently. A child who is learning what
"разложи по корзинам" means cannot afford it to sound different every time.

So the lines are baked: synthesised once here, shipped as files, and played
like any other recording. `text` stays in the scene as the fallback for a line
nobody has baked yet.

The file name carries a hash of the line, so editing the words produces a
different file and the stale recording cannot keep playing.
"""
import audioop, hashlib, json, pathlib, re, subprocess, sys, wave

ROOT = pathlib.Path("/home/sknyazev/projects/mimi")
CATALOG = ROOT / "catalog"
OUT = CATALOG / "_sounds" / "speech"
VOICY = pathlib.Path.home() / "projects/voicy"
URL = "http://localhost:8090"

# Slower, with a little more room between words, than the voice voicy picks for
# an adult reading an article. These lines are instructions to a child who is
# still learning them.
VOICE = "dostoevsky"

# Speech stays intelligible well below the 24 kHz voicy returns, and the whole
# catalog ships inside the app — a third off every file is worth having.
RATE = 16000

TRANSLIT = {
    "а":"a","б":"b","в":"v","г":"g","д":"d","е":"e","ё":"e","ж":"zh","з":"z","и":"i",
    "й":"y","к":"k","л":"l","м":"m","н":"n","о":"o","п":"p","р":"r","с":"s","т":"t",
    "у":"u","ф":"f","х":"h","ц":"c","ч":"ch","ш":"sh","щ":"sch","ъ":"","ы":"y","ь":"",
    "э":"e","ю":"yu","я":"ya",
}


def slug(text):
    latin = "".join(TRANSLIT.get(ch, ch) for ch in text.lower())
    latin = re.sub(r"[^a-z0-9]+", "-", latin).strip("-")[:48]
    digest = hashlib.sha1(text.encode("utf-8")).hexdigest()[:6]
    return f"{latin or 'line'}-{digest}"


SYSTEM_PHRASES = ROOT / "sharedUI/src/commonMain/kotlin/org/ollu/mini/sound/SystemPhrases.kt"


def _bindings(scene):
    bindings = list(scene.get("behaviors", []))
    for entity in scene.get("entities", []):
        bindings += entity.get("behaviors", [])
    for level in scene.get("levels", []):
        for entity in level["entities"]:
            bindings += entity.get("behaviors", [])
    return bindings


def _entities(scene):
    yield from scene.get("entities", [])
    for level in scene.get("levels", []):
        yield from level["entities"]


def _text(entity, key):
    value = entity.get("properties", {}).get(key)
    return value.get("value") if isinstance(value, dict) else None


def spoken_lines():
    """
    Every line the app says out loud, and where it comes from.

    Three sources, because the app speaks from three places: a scene's opening
    instruction, the app's own phrases ("Молодец"), and a tile in a choice game
    that has no recording of its own. Game titles are not spoken — the game's
    own instruction follows at once and the two talked over each other. Missing any one of them is how the robot voice survives.
    """
    lines = {}

    def add(text, origin):
        text = (text or "").strip()
        if text:
            lines.setdefault(text, set()).add(origin)

    source = SYSTEM_PHRASES.read_text(encoding="utf-8")
    phrases = source.split("object SystemPhrases", 1)[1].split("}", 1)[0]
    for match in re.finditer(r'const val \w+\s*=\s*"([^"]+)"', phrases):
        add(match.group(1), "app")

    for path in sorted(CATALOG.glob("*/*.json")):
        scene = json.loads(path.read_text(encoding="utf-8"))
        if "sceneId" not in scene:
            continue
        sid = scene["sceneId"]

        speaking_groups = set()
        turn_groups = set()
        for binding in _bindings(scene):
            ref = binding["behaviorRef"]
            config = ref.get("config") or {}
            if ref["type"] == "audioPrompt":
                add(config.get("text"), sid)
            if ref["type"] == "freeSelection" and config.get("speak") == "true":
                speaking_groups.add(config.get("group"))
            # A speech game's model word and the full answer it says back: the
            # child is copying these, so a robot voice is worse here than anywhere.
            if ref["type"] == "awaitResponse":
                turn_groups.add(config.get("group") or "targets")

        for entity in _entities(scene):
            if entity.get("tags", {}).get("group") in turn_groups and not _text(entity, "prompt"):
                add(_text(entity, "speech") or _text(entity, "name"), sid)
                add(_text(entity, "answer"), sid)

        # A question a tile brings with it, for a scene that draws its answer
        # at random: "Нажми на букву эм" is only asked if М is the one drawn.
        for entity in _entities(scene):
            add(_text(entity, "askText"), sid)

        for entity in _entities(scene):
            if not speaking_groups or _text(entity, "sound"):
                continue
            if None not in speaking_groups and entity.get("tags", {}).get("group") not in speaking_groups:
                continue
            add(_text(entity, "speech") or _text(entity, "name") or _text(entity, "label"), sid)
    return lines


def say(text, target):
    result = subprocess.run(
        [sys.executable, str(VOICY / "voicy"), "say", text, str(target),
         "--voice", VOICE],
        cwd=VOICY, env={**__import__("os").environ, "VOICY_URL": URL},
        capture_output=True, text=True, timeout=600)
    if result.returncode != 0:
        raise RuntimeError(f"{text!r}: {result.stderr.strip()[:200]}")


def downsample(path):
    with wave.open(str(path), "rb") as src:
        channels, width, rate, frames = (src.getnchannels(), src.getsampwidth(),
                                         src.getframerate(), src.getnframes())
        data = src.readframes(frames)
    if channels > 1:
        data = audioop.tomono(data, width, 0.5, 0.5)
    if rate != RATE:
        data, _ = audioop.ratecv(data, width, 1, rate, RATE, None)
    with wave.open(str(path), "wb") as dst:
        dst.setnchannels(1)
        dst.setsampwidth(width)
        dst.setframerate(RATE)
        dst.writeframes(data)
    return len(data) / (RATE * width)


def main():
    lines = spoken_lines()
    OUT.mkdir(parents=True, exist_ok=True)
    manifest = {}
    for index, (text, scenes) in enumerate(sorted(lines.items()), start=1):
        name = slug(text) + ".wav"
        target = OUT / name
        if not target.exists():
            say(text, target)
            seconds = downsample(target)
        else:
            with wave.open(str(target), "rb") as f:
                seconds = f.getnframes() / f.getframerate()
        manifest[text] = {"file": f"_sounds/speech/{name}",
                          "seconds": round(seconds, 2),
                          "scenes": sorted(scenes)}
        print(f'{index:3}/{len(lines)} {seconds:5.2f}s  {text}')

    (OUT / "speech.json").write_text(
        json.dumps({"voice": VOICE, "rate": RATE, "lines": manifest},
                   ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    wanted = {pathlib.Path(v["file"]).name for v in manifest.values()}
    for stale in OUT.glob("*.wav"):
        if stale.name not in wanted:
            stale.unlink()
            print(f"  - {stale.name} (no longer spoken)")
    total = sum(p.stat().st_size for p in OUT.glob("*.wav"))
    print(f"\n{len(manifest)} lines, {total / 1024 / 1024:.1f} MB")


if __name__ == "__main__":
    main()
