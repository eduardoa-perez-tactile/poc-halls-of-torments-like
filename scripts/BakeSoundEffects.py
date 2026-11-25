#!/usr/bin/env python3
"""Bake sampled crypt foley into the bounded TV mixer bank; see audio-source/effects/README.md.

Requires numpy, scipy and soundfile. No downloads, random noise, oscillators or runtime synthesis.
`--check` rebuilds in memory and verifies committed PCM, cue durations and source hashes.
"""
from dataclasses import dataclass
from fractions import Fraction
import argparse
import hashlib
import json
from pathlib import Path
import re
import wave

import numpy as np
from scipy import signal
import soundfile as sf

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'audio-source/effects'
OUTPUT = ROOT / 'assets/audio'
RATE = 22050
RPG = 'rpg-audio/'
IMPACT = 'impact-sounds/'
INTERFACE = 'interface-sounds/'
OGA = 'oga-rpg-sound-pack/'


@dataclass(frozen=True)
class Layer:
    source: str
    gain: float = 1
    speed: float = 1
    delay: float = 0
    cut: float = 0
    start: float = 0
    reverse: bool = False
    lowpass: float = 7000
    highpass: float = 45


@dataclass(frozen=True)
class Cue:
    seconds: float
    layers: tuple
    room: float = .05
    peak: float = .80


def L(source, **kwargs):
    return Layer(source, **kwargs)


# Every audible layer originates in the selected CC0 source files. Speeds are playback ratios,
# delays/cuts/starts are seconds; filters and short room taps give materials one shared crypt space.
CUES = {
    'sword': Cue(.28, (
        L(OGA+'battle/swing.wav', gain=.8, speed=.95, cut=.24),
        L(RPG+'drawKnife1.ogg', gain=.25, speed=1.15, cut=.18, highpass=900))),
    'arrow': Cue(.22, (
        L(INTERFACE+'pluck_001.ogg', gain=.65, speed=.72, lowpass=5000),
        L(OGA+'battle/swing2.wav', gain=.45, speed=1.35, delay=.018),
        L(RPG+'metalClick.ogg', gain=.16, speed=1.35, cut=.09))),
    'hit': Cue(.25, (
        L(IMPACT+'impactPunch_heavy_000.ogg', gain=.85, speed=1.2, cut=.19),
        L(RPG+'cloth4.ogg', gain=.25, speed=1.3, cut=.17))),
    'pickup': Cue(.22, (
        L(RPG+'handleCoins.ogg', gain=.75, speed=1.2, cut=.16),
        L(IMPACT+'impactGlass_light_000.ogg', gain=.2, speed=1.25, delay=.025))),
    'level': Cue(1.20, (
        L(IMPACT+'impactBell_heavy_001.ogg', gain=.75, speed=.9, cut=1.05, lowpass=4800),
        L(OGA+'inventory/metal-ringing.wav', gain=.28, speed=1.12, delay=.18),
        L(RPG+'cloth1.ogg', gain=.15, speed=.8, cut=.3, reverse=True)), room=.12),
    'boss': Cue(1.40, (
        L(IMPACT+'impactBell_heavy_000.ogg', gain=.72, speed=.55, cut=1.3, lowpass=2600),
        L(OGA+'NPC/giant/giant1.wav', gain=.35, speed=.65, delay=.06, cut=.7, lowpass=1900),
        L(RPG+'creak1.ogg', gain=.2, speed=.55, delay=.08, cut=.7)), room=.15),
    'victory': Cue(1.40, (
        L(IMPACT+'impactBell_heavy_001.ogg', gain=.7, speed=1, cut=1.3, lowpass=4500),
        L(OGA+'inventory/metal-ringing.wav', gain=.3, speed=1.25, delay=.25),
        L(OGA+'inventory/metal-ringing.wav', gain=.20, speed=1.5, delay=.45)), room=.14),
    'defeat': Cue(1.25, (
        L(IMPACT+'impactBell_heavy_000.ogg', gain=.55, speed=.5, cut=1.2, lowpass=1600),
        L(IMPACT+'impactSoft_heavy_000.ogg', gain=.7, speed=.6, cut=.55),
        L(RPG+'dropLeather.ogg', gain=.3, speed=.75, delay=.05, cut=.5)), room=.12),
    'sword-hit': Cue(.28, (
        L(RPG+'chop.ogg', gain=.75, speed=.9),
        L(IMPACT+'impactPunch_heavy_000.ogg', gain=.35, speed=1.05, cut=.22),
        L(IMPACT+'impactMetal_light_000.ogg', gain=.2, speed=1.1, cut=.22, highpass=1100))),
    'bolt-hit': Cue(.23, (
        L(IMPACT+'impactWood_heavy_000.ogg', gain=.8, speed=1.15, cut=.2),
        L(IMPACT+'impactMetal_light_000.ogg', gain=.25, speed=1.3, cut=.16),
        L(IMPACT+'impactGlass_light_000.ogg', gain=.13, speed=.95, cut=.16))),
    'critical': Cue(.36, (
        L(RPG+'chop.ogg', gain=.65, speed=.75),
        L(OGA+'inventory/metal-ringing.wav', gain=.4, speed=.9, cut=.3),
        L(IMPACT+'impactGlass_light_000.ogg', gain=.25, speed=1.2, delay=.018))),
    'dodge': Cue(.28, (
        L(RPG+'cloth4.ogg', gain=.8, speed=1.25, cut=.24),
        L(OGA+'battle/swing2.wav', gain=.3, speed=.9, delay=.025))),
    'hurt': Cue(.32, (
        L(IMPACT+'impactPunch_heavy_000.ogg', gain=.85, speed=.85, cut=.28),
        L(OGA+'inventory/chainmail1.wav', gain=.28, speed=1.2, cut=.2, delay=.018))),
    'death': Cue(.38, (
        L(IMPACT+'impactSoft_heavy_000.ogg', gain=.65, speed=.85, cut=.32),
        L(RPG+'dropLeather.ogg', gain=.45, speed=.85, cut=.3),
        L(OGA+'NPC/shade/shade1.wav', gain=.15, speed=.75, cut=.25, lowpass=2400))),
    'heal': Cue(.60, (
        L(OGA+'inventory/bubble.wav', gain=.45, speed=1.12, cut=.38),
        L(OGA+'inventory/metal-ringing.wav', gain=.5, speed=1.35, delay=.055, lowpass=5500)), room=.12),
    'aura': Cue(.38, (
        L(RPG+'cloth1.ogg', gain=.7, speed=.8, cut=.25, reverse=True, lowpass=2600),
        L(OGA+'inventory/metal-ringing.wav', gain=.3, speed=.7, cut=.25, delay=.06, lowpass=2400))),
    'storm': Cue(.40, (
        L(IMPACT+'impactMining_000.ogg', gain=.65, speed=.8, cut=.33, lowpass=4000),
        L(IMPACT+'impactPlate_heavy_000.ogg', gain=.3, speed=.65, cut=.29),
        L(RPG+'cloth4.ogg', gain=.25, speed=1.3, cut=.14, reverse=True))),
    'block': Cue(.30, (
        L(IMPACT+'impactMetal_medium_000.ogg', gain=.7, speed=.95),
        L(IMPACT+'impactPlate_heavy_000.ogg', gain=.4, speed=1.3, cut=.25),
        L(RPG+'drawKnife1.ogg', gain=.2, speed=1.2, cut=.16))),
    'wraith-dash': Cue(.38, (
        L(OGA+'NPC/shade/shade1.wav', gain=.55, speed=.95, cut=.31, lowpass=4200),
        L(RPG+'cloth1.ogg', gain=.5, speed=1.4, cut=.22, reverse=True),
        L(OGA+'battle/swing.wav', gain=.2, speed=1.2, delay=.055))),
    'brute-slam': Cue(.40, (
        L(IMPACT+'impactPunch_heavy_000.ogg', gain=.7, speed=.58, cut=.35, lowpass=2200),
        L(IMPACT+'impactWood_heavy_000.ogg', gain=.45, speed=.6, cut=.34),
        L(IMPACT+'impactMining_000.ogg', gain=.3, speed=.75, cut=.3, delay=.025))),
    'magic-cast': Cue(.36, (
        L(OGA+'battle/magic1.wav', gain=.6, speed=.85, cut=.3, lowpass=5000),
        L(OGA+'inventory/metal-ringing.wav', gain=.25, speed=1.2, cut=.18, reverse=True),
        L(OGA+'battle/swing2.wav', gain=.2, speed=1.2, delay=.04))),
    'ui-focus': Cue(.11, (
        L(RPG+'metalClick.ogg', gain=.8, speed=1.3, cut=.085, lowpass=5200),
        L(RPG+'cloth4.ogg', gain=.12, speed=1.3, cut=.08)), room=.025, peak=.65),
    'ui-confirm': Cue(.24, (
        L(RPG+'metalLatch.ogg', gain=.65, speed=.9, cut=.2),
        L(OGA+'inventory/metal-ringing.wav', gain=.25, speed=1.2, cut=.16, delay=.03)), room=.05, peak=.7),
    'ui-back': Cue(.22, (
        L(RPG+'metalClick.ogg', gain=.65, speed=.75, cut=.17, lowpass=3000),
        L(RPG+'cloth4.ogg', gain=.25, speed=1.2, cut=.15)), room=.04, peak=.7),
    'loot': Cue(1.05, (
        L(RPG+'metalLatch.ogg', gain=.5, speed=.75, cut=.24),
        L(RPG+'handleCoins.ogg', gain=.35, speed=.85, delay=.12, cut=.55),
        L(IMPACT+'impactBell_heavy_001.ogg', gain=.5, speed=1.12, delay=.1, cut=.85, lowpass=4500)), room=.12),
    'player-down': Cue(.85, (
        L(IMPACT+'impactSoft_heavy_000.ogg', gain=.65, speed=.68, cut=.5),
        L(OGA+'inventory/chainmail1.wav', gain=.3, speed=.78, delay=.06, cut=.48),
        L(IMPACT+'impactBell_heavy_000.ogg', gain=.35, speed=.65, delay=.06, cut=.72, lowpass=2400)), room=.10),
    'revive': Cue(1.05, (
        L(RPG+'cloth1.ogg', gain=.35, speed=.7, cut=.38, reverse=True),
        L(OGA+'inventory/metal-ringing.wav', gain=.6, speed=.95, delay=.16),
        L(IMPACT+'impactBell_heavy_001.ogg', gain=.35, speed=1.3, delay=.20, cut=.78, lowpass=5000)), room=.12),
}


def filtered(samples, highpass, lowpass):
    if highpass:
        samples = signal.sosfilt(signal.butter(2, highpass, fs=RATE, btype='highpass', output='sos'), samples)
    if lowpass:
        samples = signal.sosfilt(signal.butter(2, lowpass, fs=RATE, output='sos'), samples)
    return samples


def read_layer(layer):
    samples, rate = sf.read(SOURCE / 'sources' / layer.source, always_2d=True, dtype='float64')
    samples = samples.mean(axis=1)
    samples -= samples.mean()
    peak = np.max(np.abs(samples))
    if peak == 0:
        raise ValueError('Silent source: '+layer.source)
    # Preserve a 2 ms lead before the first material transient, removing source-editor silence.
    active = np.flatnonzero(np.abs(samples) > peak * .008)
    samples = samples[max(0, active[0]-round(rate*.002)):active[-1]+1]
    samples = samples[round(layer.start*rate):]
    ratio = Fraction(RATE / (rate * layer.speed)).limit_denominator(10000)
    samples = signal.resample_poly(samples, ratio.numerator, ratio.denominator)
    if layer.cut:
        samples = samples[:round(layer.cut*RATE)]
    if layer.reverse:
        samples = samples[::-1].copy()
    samples = filtered(samples, layer.highpass, layer.lowpass)
    samples /= max(np.max(np.abs(samples)), 1e-8)
    attack = min(len(samples)//2, round(RATE*.002))
    release = min(len(samples)//2, round(RATE*.025))
    samples[:attack] *= np.linspace(0, 1, attack)
    samples[-release:] *= np.linspace(1, 0, release)
    return samples * layer.gain


def render(cue):
    count = round(cue.seconds*RATE)
    mixed = np.zeros(count, dtype=np.float64)
    for layer in cue.layers:
        samples = read_layer(layer)
        start = round(layer.delay*RATE)
        end = min(count, start+len(samples))
        mixed[start:end] += samples[:end-start]
    # A small, filtered early-reflection room. No randomized impulse response or synthetic bed.
    dry = mixed.copy()
    for delay, gain in ((.013, .65), (.029, .4), (.047, .25), (.083, .15)):
        shift = round(delay*RATE)
        mixed[shift:] += filtered(dry[:-shift], 100, 3200) * cue.room * gain
    mixed = np.tanh(mixed*.8)
    mixed /= max(np.max(np.abs(mixed)), 1e-8)
    mixed *= cue.peak
    fade = min(count//3, round(RATE*(.08 if cue.seconds > .6 else .025)))
    mixed[-fade:] *= np.linspace(1, 0, fade)**1.2
    mixed[0] = mixed[-1] = 0
    return np.round(mixed*32767).astype('<i2')


def verify_sources():
    manifest = json.loads((SOURCE/'manifest.json').read_text())
    for entry in manifest['selected_sources']:
        actual = hashlib.sha256((SOURCE/entry['file']).read_bytes()).hexdigest()
        if actual != entry['sha256']:
            raise ValueError('Source hash mismatch: '+entry['file'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Validate committed bytes without writing')
    args = parser.parse_args()
    verify_sources()
    catalog = (ROOT/'core/src/main/java/com/couchcrypt/game/SoundCue.java').read_text()
    total = 0
    for name, cue in CUES.items():
        pcm = render(cue)
        file = OUTPUT/(name+'.wav')
        if not args.check:
            with wave.open(str(file), 'wb') as audio:
                audio.setnchannels(1); audio.setsampwidth(2); audio.setframerate(RATE)
                audio.writeframes(pcm.tobytes())
        with wave.open(str(file), 'rb') as audio:
            assert audio.getnchannels() == 1 and audio.getsampwidth() == 2 and audio.getframerate() == RATE, name
            assert audio.getnframes() == len(pcm), name+' duration'
            assert audio.readframes(audio.getnframes()) == pcm.tobytes(), name+' non-reproducible PCM'
        match = re.search(r'\("'+re.escape(name)+r'",\s*([.\d]+)f', catalog)
        assert match and abs(float(match.group(1))-len(pcm)/RATE) < 1/RATE, name+' catalog duration'
        assert np.max(np.abs(pcm.astype(np.int32))) < 30000, name+' clipping'
        assert np.sqrt(np.mean((pcm.astype(float)/32768)**2)) > .01, name+' empty/quiet output'
        assert pcm[0] == 0 and pcm[-1] == 0, name+' edge clicks'
        total += len(pcm)*2
        print(f'{name:14s} {len(pcm)/RATE:.3f}s  peak {np.max(np.abs(pcm))/32768:.3f}')
    print(f'{len(CUES)} cues verified, {total:,} decoded PCM bytes ({total/1048576:.3f} MiB)')


if __name__ == '__main__':
    main()
