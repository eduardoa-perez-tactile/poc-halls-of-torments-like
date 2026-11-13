#!/usr/bin/env python3
"""Original compact mono PCM cues, generated offline with Python's standard library."""
import math
import random
import struct
import wave
from pathlib import Path

RATE = 22050
OUTPUT = Path(__file__).resolve().parent.parent / 'assets' / 'audio'
OUTPUT.mkdir(parents=True, exist_ok=True)


def write(name, seconds, synth):
    samples = []
    noise = random.Random(613)
    for i in range(int(RATE * seconds)):
        t = i / RATE
        envelope = min(1, t / .008) * max(0, 1 - t / seconds) ** 1.8
        value = max(-1, min(1, synth(t, seconds, noise) * envelope * .64))
        samples.append(struct.pack('<h', int(value * 32767)))
    with wave.open(str(OUTPUT / (name + '.wav')), 'wb') as file:
        file.setnchannels(1)
        file.setsampwidth(2)
        file.setframerate(RATE)
        file.writeframes(b''.join(samples))


def sine(t, hz):
    return math.sin(2 * math.pi * hz * t)


write('sword', .16, lambda t, d, n: n.uniform(-1, 1) * .5 + sine(t, 90 - t * 160) * .3)
write('arrow', .09, lambda t, d, n: n.uniform(-1, 1) * .35 + sine(t, 1200 - t * 5000) * .25)
write('hit', .10, lambda t, d, n: n.uniform(-1, 1) * .55 + sine(t, 65) * .35)
write('pickup', .18, lambda t, d, n: sine(t, 880) * .5 + sine(t, 1320) * .2)
write('level', .55, lambda t, d, n: sine(t, [440, 554.37, 659.25][min(2, int(t / .13))]) * .6)
write('boss', .65, lambda t, d, n: sine(t, 55) * .55 + sine(t, 82.41) * .25 + n.uniform(-1, 1) * .08)
write('victory', .75, lambda t, d, n: (sine(t, 523.25) + sine(t, 659.25) + sine(t, 783.99)) * .25)
write('defeat', .55, lambda t, d, n: sine(t, 220 - t * 150) * .6 + sine(t, 110) * .2)
