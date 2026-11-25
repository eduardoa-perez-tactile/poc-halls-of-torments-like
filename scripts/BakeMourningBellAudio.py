#!/usr/bin/env python3
"""Original short resonant monster toll; deterministic synthesis, no sampled source material."""
import math
from pathlib import Path
import struct
import wave

RATE = 22050
SECONDS = .85
OUTPUT = Path(__file__).resolve().parents[1] / "assets/audio/bell-toll.wav"


def bake():
    samples = bytearray()
    count = round(RATE * SECONDS)
    for i in range(count):
        t = i / RATE
        attack = min(1.0, t / .014)
        tail = min(1.0, (SECONDS - t) / .08)
        # Falling resonant growl beneath two inharmonic bell partials.
        phase = 2 * math.pi * (82 * t + 5 * (1 - math.exp(-10 * t)))
        throat = (math.sin(phase) + .30 * math.sin(2 * phase + .5 * math.sin(t * 32)))
        metal = .36 * math.sin(2 * math.pi * 213.7 * t) + .17 * math.sin(2 * math.pi * 481.3 * t)
        sound = math.tanh((throat + metal) * 1.1) * attack * tail * math.exp(-3.1 * t) * .72
        samples.extend(struct.pack("<h", round(sound * 32767)))
    with wave.open(str(OUTPUT), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(samples)


if __name__ == "__main__":
    bake()
