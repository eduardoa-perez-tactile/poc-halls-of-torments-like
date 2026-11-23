#!/usr/bin/env python3
"""Original mono game cues, synthesized offline with the Python standard library.

Combat voices layer a short body transient, filtered noise and inharmonic material
resonance. The four existing ceremony cues retain their original synthesis/bytes.
No samples, recordings, downloaded libraries or runtime synthesis are used.
"""
import math
import random
import struct
import wave
from pathlib import Path

RATE = 22050
OUTPUT = Path(__file__).resolve().parent.parent / 'assets' / 'audio'
OUTPUT.mkdir(parents=True, exist_ok=True)


def sine(t, hz):
    return math.sin(2 * math.pi * hz * t)


def sweep(t, start_hz, end_hz, duration):
    # Integrate frequency so a falling pitch remains phase-continuous.
    return math.sin(2 * math.pi * (start_hz * t + (end_hz - start_hz) * t * t / (2 * duration)))


class Grain:
    def __init__(self, seed):
        self.random = random.Random(seed)
        self.low = 0.0
        self.high = 0.0

    def sample(self):
        white = self.random.uniform(-1, 1)
        self.low += .19 * (white - self.low)
        self.high = white - self.low
        return white


def save(name, values):
    with wave.open(str(OUTPUT / (name + '.wav')), 'wb') as file:
        file.setnchannels(1)
        file.setsampwidth(2)
        file.setframerate(RATE)
        file.writeframes(b''.join(struct.pack('<h', int(value * 32767)) for value in values))


def combat(name, seconds, synth, gain=.64):
    grain = Grain(613 + sum((i + 1) * ord(c) for i, c in enumerate(name)))
    count = int(RATE * seconds)
    values = []
    for i in range(count):
        t = i / RATE
        # Fast attack and short release avoid boundary clicks while keeping the contact transient sharp.
        edge = min(1, t / .0008, (count - 1 - i) / (RATE * .012))
        values.append(synth(t, seconds, grain) * max(0, edge) * gain)
    peak = max(abs(value) for value in values)
    if peak > .82:
        values = [value * .82 / peak for value in values]
    values[0] = values[-1] = 0.0
    save(name, values)


def original(name, seconds, synth):
    # Preserve the already approved ceremony cues byte for byte, including their legacy envelope.
    noise = random.Random(613)
    values = []
    for i in range(int(RATE * seconds)):
        t = i / RATE
        envelope = min(1, t / .008) * max(0, 1 - t / seconds) ** 1.8
        values.append(max(-1, min(1, synth(t, seconds, noise) * envelope * .64)))
    save(name, values)


def sword(t, d, n):
    n.sample()
    air = math.sin(math.pi * t / d) ** .65
    return (n.high * .65 + n.low * .45) * air * math.exp(-t * 7) + sweep(t, 370, 100, d) * .16 * math.exp(-t * 22)


def arrow(t, d, n):
    n.sample()
    return (sine(t, 580) * .40 + sine(t, 917) * .20) * math.exp(-t * 42) + n.high * .55 * math.exp(-t * 90) + sweep(t, 170, 70, d) * .25 * math.exp(-t * 32)


def sword_hit(t, d, n):
    n.sample()
    body = sweep(t, 170, 48, d) * .74 * math.exp(-t * 21)
    steel = (sine(t, 985) * .18 + sine(t, 1687) * .12) * math.exp(-t * 47)
    return body + steel + n.high * .46 * math.exp(-t * 66) + n.low * 1.15 * math.exp(-t * 24)


def bolt_hit(t, d, n):
    n.sample()
    return sweep(t, 250, 88, d) * .66 * math.exp(-t * 32) + sine(t, 1367) * .20 * math.exp(-t * 86) + n.high * .52 * math.exp(-t * 100) + n.low * .65 * math.exp(-t * 45)


def critical(t, d, n):
    n.sample()
    crack = n.high * .62 * math.exp(-t * 58) + sweep(t, 210, 52, d) * .56 * math.exp(-t * 26)
    shards = (sine(t, 1859) * .22 + sine(t, 2731) * .15 + sine(t, 3617) * .08) * math.exp(-t * 25)
    return crack + shards


def dodge(t, d, n):
    n.sample()
    air = math.sin(math.pi * min(1, t / d)) ** .65
    return n.high * .72 * air * math.exp(-t * 9) + n.low * .36 * air + sweep(t, 430, 130, d) * .12 * math.exp(-t * 25)


def hurt(t, d, n):
    n.sample()
    return sweep(t, 143, 42, d) * .82 * math.exp(-t * 19) + n.low * 1.25 * math.exp(-t * 24) + n.high * .28 * math.exp(-t * 60)


def death(t, d, n):
    n.sample()
    crumble = n.low * 1.3 * math.exp(-t * 17) + n.high * .25 * math.exp(-t * 35)
    soul = sweep(t, 250, 460, d) * .19 * math.sin(math.pi * t / d) * math.exp(-t * 9)
    return crumble + sweep(t, 105, 38, d) * .40 * math.exp(-t * 22) + soul


def pickup(t, d, n):
    first = sine(t, 880) * math.exp(-t * 28)
    second = sine(max(0, t - .038), 1320) * math.exp(-max(0, t - .038) * 34) if t > .038 else 0
    return first * .46 + second * .35


def heal(t, d, n):
    return (sine(t, 523.25) * .30 + sine(t, 659.25) * .25 + sine(t, 783.99) * .18) * math.exp(-t * 16)


def aura(t, d, n):
    n.sample()
    return (sweep(t, 123, 65, d) * .5 + sine(t, 196) * .18) * math.exp(-t * 15) + n.low * .5 * math.exp(-t * 30)


def storm(t, d, n):
    n.sample()
    crack = math.exp(-t * 90)
    for delay in (.034, .072):
        if t > delay:
            crack += .5 * math.exp(-(t - delay) * 110)
    return n.high * .74 * crack + sweep(t, 920, 130, d) * .22 * math.exp(-t * 22) + sine(t, 82) * .3 * math.exp(-t * 30)


def block(t, d, n):
    n.sample()
    return (sine(t, 1411) * .36 + sine(t, 2237) * .22 + sine(t, 331) * .25) * math.exp(-t * 30) + n.high * .3 * math.exp(-t * 65)


combat('sword', .17, sword)
combat('arrow', .095, arrow)
# Retained legacy file for source compatibility; enemy/player impacts now have distinct cues.
original('hit', .10, lambda t, d, n: n.uniform(-1, 1) * .55 + sine(t, 65) * .35)
combat('pickup', .17, pickup)
original('level', .55, lambda t, d, n: sine(t, [440, 554.37, 659.25][min(2, int(t / .13))]) * .6)
original('boss', .65, lambda t, d, n: sine(t, 55) * .55 + sine(t, 82.41) * .25 + n.uniform(-1, 1) * .08)
original('victory', .75, lambda t, d, n: (sine(t, 523.25) + sine(t, 659.25) + sine(t, 783.99)) * .25)
original('defeat', .55, lambda t, d, n: sine(t, 220 - t * 150) * .6 + sine(t, 110) * .2)
combat('sword-hit', .12, sword_hit)
combat('bolt-hit', .09, bolt_hit)
combat('critical', .18, critical)
combat('dodge', .16, dodge)
combat('hurt', .16, hurt)
combat('death', .24, death)
combat('heal', .24, heal)
combat('aura', .24, aura)
combat('storm', .22, storm)
combat('block', .13, block)
