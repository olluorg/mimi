#!/usr/bin/env python3
"""
Generates the catalog's Lottie animations.

Like the instrument sounds, these are authored here rather than downloaded: no
licence to track, exact reproduction from this script, and each file is a few
kilobytes. They are plain shape layers — circles, stars, rectangles moving on
keyframes — which every Lottie player renders the same way.

    python3 tools/generate_animations.py

Writes JSON files into catalog/_anim/.
"""
import json
import math
import pathlib
import random

OUT = pathlib.Path(__file__).resolve().parent.parent / "catalog" / "_anim"
FPS = 60

GOLD = (1.0, 0.80, 0.18)
COLOURS = [(0.95, 0.33, 0.31), (0.27, 0.62, 0.95), (0.40, 0.73, 0.42),
           (1.0, 0.76, 0.03), (0.61, 0.48, 0.72), (1.0, 0.55, 0.25)]


def static(v):
    return {"a": 0, "k": v}


def ease_out():
    return {"i": {"x": [0.2], "y": [1]}, "o": {"x": [0.3], "y": [0]}}


def ease_in():
    return {"i": {"x": [0.7], "y": [1]}, "o": {"x": [0.6], "y": [0]}}


def animated(frames, ease=ease_out):
    """[(frame, value), …] → a keyframed property."""
    keys = []
    for n, (t, v) in enumerate(frames):
        key = {"t": t, "s": v if isinstance(v, list) else [v]}
        if n < len(frames) - 1:
            key.update(ease())
        keys.append(key)
    return {"a": 1, "k": keys}


def transform(position, scale=None, opacity=None, rotation=None):
    return {
        "a": static([0, 0, 0]),
        "p": position,
        "s": scale or static([100, 100, 100]),
        "o": opacity or static(100),
        "r": rotation or static(0),
    }


def group(*items):
    return {"ty": "gr", "it": list(items) + [{
        "ty": "tr", "p": static([0, 0]), "a": static([0, 0]),
        "s": static([100, 100]), "r": static(0), "o": static(100)
    }]}


def fill(rgb):
    return {"ty": "fl", "c": static(list(rgb) + [1]), "o": static(100)}


def stroke(rgb, width):
    return {"ty": "st", "c": static(list(rgb) + [1]), "o": static(100), "w": static(width), "lc": 2, "lj": 2}


def ellipse(size):
    return {"ty": "el", "p": static([0, 0]), "s": static([size, size])}


def star(outer, inner):
    return {"ty": "sr", "sy": 1, "d": 1, "pt": static(5), "p": static([0, 0]), "r": static(0),
            "ir": static(inner), "is": static(0), "or": static(outer), "os": static(0)}


def rect(w, h):
    return {"ty": "rc", "p": static([0, 0]), "s": static([w, h]), "r": static(2)}


def layer(index, name, ks, shapes, end):
    return {"ddd": 0, "ind": index, "ty": 4, "nm": name, "sr": 1, "ks": ks, "ao": 0,
            "shapes": shapes, "ip": 0, "op": end, "st": 0, "bm": 0}


def composition(name, size, end, layers):
    return {"v": "5.7.4", "fr": FPS, "ip": 0, "op": end, "w": size, "h": size,
            "nm": name, "ddd": 0, "assets": [], "layers": layers}


def burst():
    """
    A ring of stars flying out from the centre, a ring widening behind them,
    and a big star popping in the middle: the "yes!" over a card the child
    has just answered. Under a second, transparent everywhere else.
    """
    size, end, c = 400, 54, 200
    layers = []
    rays = 10
    for n in range(rays):
        angle = 2 * math.pi * n / rays
        far = 150 + (n % 2) * 25
        x, y = c + far * math.cos(angle), c + far * math.sin(angle)
        colour = COLOURS[n % len(COLOURS)]
        layers.append(layer(
            len(layers) + 1, f"ray{n}",
            transform(
                position=animated([(0, [c, c, 0]), (40, [x, y, 0])]),
                scale=animated([(0, [40, 40, 100]), (14, [120, 120, 100]), (end, [60, 60, 100])]),
                opacity=animated([(0, 100), (30, 100), (end, 0)]),
                rotation=animated([(0, 0), (end, 180 if n % 2 else -180)]),
            ),
            [group(star(18, 8), fill(colour))], end))
    layers.append(layer(
        len(layers) + 1, "ring",
        transform(
            position=static([c, c, 0]),
            scale=animated([(0, [20, 20, 100]), (36, [100, 100, 100])]),
            opacity=animated([(0, 90), (36, 0)]),
        ),
        [group(ellipse(300), stroke(GOLD, 10))], end))
    layers.append(layer(
        len(layers) + 1, "star",
        transform(
            position=static([c, c, 0]),
            scale=animated([(0, [0, 0, 100]), (12, [130, 130, 100]), (22, [100, 100, 100]), (end, [100, 100, 100])]),
            opacity=animated([(0, 100), (38, 100), (end, 0)]),
            rotation=animated([(0, -30), (22, 0)]),
        ),
        [group(star(60, 26), fill(GOLD), stroke((1.0, 0.62, 0.0), 5))], end))
    return composition("burst", size, end, layers)


def bonus():
    """
    The bigger "yes!" for saying exactly the sound that was modelled: two
    rings of stars, a double ring, and a large golden star that spins in —
    unmistakably more than the ordinary burst, so the child can feel the
    difference between "you tried" and "that was it".
    """
    size, end, c = 500, 78, 250
    layers = []
    for ring, (count, far, star_size) in enumerate(((12, 200, 22), (8, 120, 16))):
        for n in range(count):
            angle = 2 * math.pi * (n + 0.5 * ring) / count
            x, y = c + far * math.cos(angle), c + far * math.sin(angle)
            colour = GOLD if (n + ring) % 3 == 0 else COLOURS[n % len(COLOURS)]
            delay = 6 * ring
            layers.append(layer(
                len(layers) + 1, f"ray{ring}_{n}",
                transform(
                    position=animated([(delay, [c, c, 0]), (delay + 44, [x, y, 0])]),
                    scale=animated([(delay, [30, 30, 100]), (delay + 16, [140, 140, 100]), (end, [70, 70, 100])]),
                    opacity=animated([(0, 0), (delay, 100), (end - 14, 100), (end, 0)]),
                    rotation=animated([(delay, 0), (end, 240 if n % 2 else -240)]),
                ),
                [group(star(star_size, star_size * 0.45), fill(colour))], end))
    for n, (width, delay) in enumerate(((12, 0), (7, 10))):
        layers.append(layer(
            len(layers) + 1, f"ring{n}",
            transform(
                position=static([c, c, 0]),
                scale=animated([(delay, [15, 15, 100]), (delay + 44, [100, 100, 100])]),
                opacity=animated([(0, 0), (delay, 95), (delay + 44, 0)]),
            ),
            [group(ellipse(440), stroke(GOLD, width))], end))
    layers.append(layer(
        len(layers) + 1, "star",
        transform(
            position=static([c, c, 0]),
            scale=animated([(0, [0, 0, 100]), (16, [160, 160, 100]), (28, [125, 125, 100]), (end, [125, 125, 100])]),
            opacity=animated([(0, 100), (end - 16, 100), (end, 0)]),
            rotation=animated([(0, -200), (28, 0)]),
        ),
        [group(star(80, 34), fill(GOLD), stroke((1.0, 0.55, 0.0), 7))], end))
    return composition("bonus", size, end, layers)


def confetti():
    """Paper falling across the whole screen, for finishing a game."""
    size, end = 600, 150
    rnd = random.Random(5)
    layers = []
    for n in range(46):
        x = rnd.uniform(20, size - 20)
        drift = rnd.uniform(-90, 90)
        start = rnd.randint(0, 40)
        colour = COLOURS[n % len(COLOURS)]
        spin = rnd.choice([-1, 1]) * rnd.uniform(360, 900)
        w, h = rnd.uniform(10, 18), rnd.uniform(16, 28)
        layers.append(layer(
            n + 1, f"paper{n}",
            transform(
                position=animated([(start, [x, -30, 0]), (end, [x + drift, size + 40, 0])], ease=ease_in),
                rotation=animated([(start, 0), (end, spin)], ease=ease_in),
                # Flipping in the air: the width folding in and out.
                scale=animated([(start, [100, 100, 100]), (start + 20, [20, 100, 100]),
                                (start + 40, [100, 100, 100]), (end, [30, 100, 100])]),
            ),
            [group(rect(w, h), fill(colour))], end))
    return composition("confetti", size, end, layers)


LIP = (0.87, 0.42, 0.40)
LIP_DARK = (0.66, 0.24, 0.24)
INNER = (0.30, 0.10, 0.12)
TOOTH = (1.0, 0.98, 0.96)


def mouth(shapes):
    """
    A mouth saying one sound, for the child to copy.
    The strongest cue for a child who cannot plan the movement is seeing it:
    «а» drops the jaw, «у» pushes the lips into a ring, «и» spreads them. So
    the lips are drawn big and plain — no face, no distractions — and the
    shape is held long enough to be copied, not flashed.

    [shapes] is [(frame, width, height), …] for the opening of the mouth.
    """
    size, c = 300, 150
    inner = [(t, [w, h]) for t, w, h in shapes]
    lips = [(t, [w + 46, h + 34]) for t, w, h in shapes]
    # A beat of stillness after the lips close, so the shape can be shown
    # again a moment later rather than running on like a tic.
    end = max(t for t, _, _ in shapes) + 48
    # Lottie draws the first layer on top, so these run front to back: the
    # teeth over the dark of the mouth, both inside the lips.
    layers = [
        layer(1, "teeth", transform(position=static([c, c - 24, 0])),
              [group({"ty": "rc", "p": static([0, 0]), "r": static(4), "s": animated(
                  # Only a spread mouth shows teeth; on a round «у» or an open
                  # «а» they would be a white bar floating in the dark.
                  # Both sides go to zero together: a rounded rectangle of zero
                  # width still draws as a white blob from its corner radius.
                  [(t, [w - 46.0, min(16.0, h * 0.3)] if w > h * 1.6 else [0.0, 0.0]) for t, w, h in shapes])},
                fill(TOOTH))], end),
        layer(2, "inner", transform(position=static([c, c, 0])),
              [group({"ty": "el", "p": static([0, 0]), "s": animated(inner)}, fill(INNER))], end),
        layer(3, "lips", transform(position=static([c, c, 0])),
              [group({"ty": "el", "p": static([0, 0]), "s": animated(lips)}, fill(LIP), stroke(LIP_DARK, 7))], end),
    ]
    return composition("mouth", size, end, layers)


def closed(w=150.0):
    return (0.0, w, 6.0)


def lip_stop():
    """
    «п» and «б»: the lips press flat together, hold a beat, then burst open.
    One animation for both, because they are the same movement — the only
    difference is the voice behind it, which cannot be seen. Telling an adult
    that is worth more than two files pretending otherwise.
    """
    return mouth([
        (0.0, 150.0, 6.0),
        (10, 132.0, 3.0),          # pressed: narrower and tighter than at rest
        (26, 132.0, 3.0),
        (33, 124.0, 128.0),        # the burst
        (54, 116.0, 112.0),
        (74, 150.0, 6.0),
    ])


def lip_hum():
    """
    «м»: the lips stay together while the sound goes on — it hums through the
    nose — and only then open. Held far longer than the burst of «п», which
    is the whole visible difference between them.
    """
    return mouth([
        (0.0, 150.0, 6.0),
        (8, 140.0, 5.0),
        (66, 140.0, 5.0),          # the long closure, where the humming is
        (74, 120.0, 120.0),
        (96, 114.0, 108.0),
        (116, 150.0, 6.0),
    ])


def lip_teeth():
    """
    «в» and «ф»: the lower lip rises to the upper teeth and the air passes
    between them. Again one movement for two sounds.
    """
    size, c, end = 300, 150, 150
    inner = [(0.0, [150.0, 70.0]), (16, [150.0, 70.0]), (70, [150.0, 70.0]), (118, [150.0, 70.0])]
    lips = [(t, [w + 46, h + 34]) for t, (w, h) in inner]
    # The lower lip tucks up under the teeth, holds, then drops back.
    lip_y = [(0.0, [c, c + 62, 0]), (16, [c, c + 20, 0]), (70, [c, c + 20, 0]), (96, [c, c + 62, 0]), (end, [c, c + 62, 0])]
    # Front to back: the teeth are in front, the lower lip tucks up behind
    # them, and both sit inside the dark of the mouth and the outer lips.
    layers = [
        layer(1, "teeth", transform(position=static([c, c - 14, 0])),
              [group({"ty": "rc", "p": static([0, 0]), "r": static(5), "s": static([120, 24])}, fill(TOOTH))], end),
        layer(2, "lower lip", transform(position=animated(lip_y)),
              [group({"ty": "rc", "p": static([0, 0]), "r": static(22), "s": static([132, 52])},
                     fill(LIP), stroke(LIP_DARK, 5))], end),
        layer(3, "inner", transform(position=static([c, c, 0])),
              [group({"ty": "el", "p": static([0, 0]), "s": animated(inner)}, fill(INNER))], end),
        layer(4, "lips", transform(position=static([c, c, 0])),
              [group({"ty": "el", "p": static([0, 0]), "s": animated(lips)}, fill(LIP), stroke(LIP_DARK, 7))], end),
    ]
    return composition("mouth", size, end, layers)


# Each vowel: closed, open into the shape, hold it, close again.
MOUTHS = {
    "mouth_a": [closed(), (12, 120.0, 150.0), (16, 110.0, 140.0), (70, 110.0, 140.0), (88, 150.0, 6.0)],
    "mouth_o": [closed(), (14, 112.0, 124.0), (70, 104.0, 118.0), (88, 150.0, 6.0)],
    "mouth_u": [closed(), (16, 74.0, 84.0), (70, 70.0, 80.0), (88, 150.0, 6.0)],
    "mouth_i": [closed(), (14, 196.0, 54.0), (70, 190.0, 50.0), (88, 150.0, 6.0)],
    "mouth_e": [closed(), (14, 162.0, 96.0), (70, 156.0, 92.0), (88, 150.0, 6.0)],
    # Lips together, then open — the shape of «ма-ма», «па-па», «ба-ба».
    "mouth_ma": [closed(170.0), (10, 170.0, 6.0), (22, 118.0, 140.0), (40, 118.0, 134.0),
                 (52, 170.0, 6.0), (64, 118.0, 140.0), (82, 118.0, 134.0), (96, 170.0, 6.0)],
}


ANIMATIONS = {
    "mouth_pb": lip_stop,
    "mouth_m": lip_hum,
    "mouth_vf": lip_teeth,
    "burst": burst,
    "bonus": bonus,
    "confetti": confetti,
}


for _name, _shapes in MOUTHS.items():
    ANIMATIONS[_name] = (lambda sh: lambda: mouth(sh))(_shapes)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, make in ANIMATIONS.items():
        path = OUT / f"{name}.json"
        path.write_text(json.dumps(make(), separators=(",", ":")) + "\n", encoding="utf-8")
        print(f"  {name:<10} {path.stat().st_size / 1024:5.1f} KB")
    print(f"{len(ANIMATIONS)} animations written to {OUT}")
