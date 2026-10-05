#!/usr/bin/env python3
"""
Generates the catalog's instrument sounds.

A cause-and-effect game needs the *sound of the thing*, not its name: a child
pressing a drum expects a drum, and hearing the word "барабан" is a vocabulary
exercise instead. The card library has names, so these fill the gap.

They are synthesised rather than downloaded on purpose — every sample is
authored here, so there is no licence to track, they reproduce exactly from this
script, and each is a couple of kilobytes instead of a megabyte.

    python3 tools/generate_sounds.py

Writes WAV files into catalog/_sounds/.
"""
import math
import pathlib
import random
import struct
import wave

RATE = 22050
OUT = pathlib.Path(__file__).resolve().parent.parent / "catalog" / "_sounds"


def write(name: str, samples: list[float]) -> int:
    """Normalises to a comfortable level and writes 16-bit mono."""
    peak = max((abs(s) for s in samples), default=1.0) or 1.0
    # 0.82 rather than 1.0: these play under a child's hand on a tablet speaker,
    # and a sample that touches full scale distorts on small drivers.
    gain = 0.82 / peak
    frames = b"".join(
        struct.pack("<h", int(max(-1.0, min(1.0, s * gain)) * 32767)) for s in samples
    )
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f"{name}.wav"
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(frames)
    return path.stat().st_size


def envelope(i: int, total: int, attack: int, decay_power: float) -> float:
    """Fast rise, exponential fall — how a struck object actually behaves."""
    if i < attack:
        return i / attack
    t = (i - attack) / max(1, total - attack)
    return math.exp(-decay_power * t)


def partials(freqs_and_weights, seconds: float, attack_ms: float, decay: float):
    """A struck tone built from its overtones."""
    total = int(RATE * seconds)
    attack = max(1, int(RATE * attack_ms / 1000))
    out = []
    for i in range(total):
        env = envelope(i, total, attack, decay)
        s = sum(w * math.sin(2 * math.pi * f * i / RATE) for f, w in freqs_and_weights)
        out.append(s * env)
    return out


def noise_burst(seconds: float, decay: float, low_pass: float, seed: int):
    """Filtered noise: the body of a rattle, a shaker, a bubble popping."""
    rnd = random.Random(seed)
    total = int(RATE * seconds)
    out, prev = [], 0.0
    for i in range(total):
        white = rnd.uniform(-1.0, 1.0)
        # One-pole low pass keeps it from sounding like static.
        prev += (white - prev) * low_pass
        out.append(prev * envelope(i, total, max(1, int(RATE * 0.002)), decay))
    return out


def mix(*layers):
    length = max(len(l) for l in layers)
    out = [0.0] * length
    for layer in layers:
        for i, s in enumerate(layer):
            out[i] += s
    return out


def drum():
    """A struck skin: a pitch that drops fast, over a thump of noise."""
    seconds, total = 0.30, int(RATE * 0.30)
    tone = []
    for i in range(total):
        # 150 Hz falling to 55 Hz is what gives a drum its "thud" rather than a beep.
        f = 55 + 95 * math.exp(-14 * i / total)
        tone.append(math.sin(2 * math.pi * f * i / RATE) * envelope(i, total, 40, 7))
    return mix(tone, [s * 0.35 for s in noise_burst(0.08, 16, 0.55, seed=1)])


def bell():
    """Inharmonic partials in the ratios a real bell rings at, so it shimmers."""
    base = 740.0
    return partials(
        [(base, 1.0), (base * 2.76, 0.55), (base * 5.40, 0.28), (base * 8.93, 0.14)],
        seconds=1.1, attack_ms=3, decay=5.5,
    )


def xylophone():
    """A wooden bar: strong odd modes and a short, bright decay."""
    base = 1046.5  # C6, the register a child's xylophone actually sits in
    return partials(
        [(base, 1.0), (base * 3.0, 0.42), (base * 6.2, 0.16)],
        seconds=0.45, attack_ms=2, decay=8.0,
    )


def rattle():
    """Four quick shakes — a rattle is a rhythm, not a single hit."""
    out = []
    for shake in range(4):
        out += noise_burst(0.055, 22, 0.75, seed=10 + shake)
        out += [0.0] * int(RATE * 0.045)
    return out


def pop():
    """A bubble giving way: a very short click with the pitch falling out of it."""
    seconds, total = 0.09, int(RATE * 0.09)
    tone = []
    for i in range(total):
        f = 320 + 900 * math.exp(-30 * i / total)
        tone.append(math.sin(2 * math.pi * f * i / RATE) * envelope(i, total, 30, 12))
    return mix(tone, [s * 0.5 for s in noise_burst(0.03, 26, 0.85, seed=42)])


def honk(seconds: float):
    """A car horn: two reedy tones a third apart, buzzy like a real horn."""
    total = int(RATE * seconds)
    out = []
    for i in range(total):
        t = i / RATE
        s = 0.0
        for f in (392.0, 494.0):                 # G4 + B4, the classic two-note horn
            # A few odd harmonics give it the reed buzz rather than a pure beep.
            s += sum(math.sin(2 * math.pi * f * k * t) / k for k in (1, 3, 5))
        env = min(1.0, i / (RATE * 0.01)) * min(1.0, (total - i) / (RATE * 0.02))
        out.append(s * env)
    return out


def car():
    """«Би-би!» — two honks, then the engine turning over and revving away."""
    gap = [0.0] * int(RATE * 0.07)
    engine, total = [], int(RATE * 0.95)
    rnd = random.Random(7)
    for i in range(total):
        t = i / RATE
        # Firing rate climbs as the engine revs; a lumpy low buzz, not a tone.
        f = 38 + 70 * (i / total) ** 1.6
        phase = (f * t) % 1.0
        pulse = 1.0 if phase < 0.18 else -0.2
        engine.append((pulse * 0.8 + rnd.uniform(-0.25, 0.25)) * envelope(i, total, int(RATE * 0.05), 1.2))
    return honk(0.16) + gap + honk(0.22) + [0.0] * int(RATE * 0.05) + [x * 0.6 for x in engine]


def train():
    """«Ту-ту!» — a steam whistle: a chord with breath in it, blown twice."""
    def blow(seconds):
        total = int(RATE * seconds)
        rnd = random.Random(3)
        out, prev = [], 0.0
        for i in range(total):
            t = i / RATE
            tone = sum(w * math.sin(2 * math.pi * f * t) for f, w in ((587, 1.0), (740, 0.8), (880, 0.6)))
            prev += (rnd.uniform(-1, 1) - prev) * 0.3
            env = min(1.0, i / (RATE * 0.04)) * min(1.0, (total - i) / (RATE * 0.08))
            out.append((tone + prev * 0.9) * env)
        return out
    return blow(0.35) + [0.0] * int(RATE * 0.1) + blow(0.6)


def airplane():
    """«У-у-у!» — a jet passing: a rising, then falling rush of air over a low drone."""
    total = int(RATE * 1.6)
    rnd = random.Random(11)
    out, prev = [], 0.0
    for i in range(total):
        x = i / total
        swell = math.sin(math.pi * x) ** 1.5               # comes in, passes, goes away
        cutoff = 0.08 + 0.35 * swell                       # brighter as it passes overhead
        prev += (rnd.uniform(-1, 1) - prev) * cutoff
        drone = math.sin(2 * math.pi * (110 + 40 * swell) * i / RATE) * 0.3
        out.append((prev + drone) * swell)
    return out


SOUNDS = {
    "car": car,
    "train": train,
    "airplane": airplane,
    "drum": drum,
    "bell": bell,
    "xylophone": xylophone,
    "rattle": rattle,
    "pop": pop,
}


if __name__ == "__main__":
    for name, make in SOUNDS.items():
        size = write(name, make())
        print(f"  {name:<12} {size / 1024:5.1f} KB")
    print(f"{len(SOUNDS)} sounds written to {OUT}")
