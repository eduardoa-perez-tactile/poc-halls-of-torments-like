#!/usr/bin/env python3
"""Bake the licensed dark-fantasy score. Requires numpy, soundfile, scipy and FFmpeg.

The source manifest pins download hashes; masters stay in an ignored cache.
No synthesis, network or file conversion occurs inside the game.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from urllib.request import Request, urlopen

import numpy as np
import soundfile as sf
from scipy.signal import butter, sosfiltfilt

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / 'audio-source/music/manifest.json'


def ffmpeg_path(explicit):
    if explicit:
        return explicit
    found = shutil.which('ffmpeg')
    if found:
        return found
    import imageio_ffmpeg
    return imageio_ffmpeg.get_ffmpeg_exe()


def source_file(track, cache):
    path = cache / track['sourceFile']
    if not path.exists():
        request = Request(track['downloadUrl'], headers={'User-Agent': 'CouchCrypt audio bake'})
        with urlopen(request, timeout=120) as response:
            path.write_bytes(response.read())
    if hashlib.sha256(path.read_bytes()).hexdigest() != track['sourceSha256']:
        raise ValueError('Source checksum mismatch: ' + str(path))
    return path


def prepare_loop(samples, rate, overlap, trim_tail):
    if trim_tail:
        active = np.flatnonzero(np.max(np.abs(samples), axis=1) > 10 ** (-65 / 20))
        if len(active):
            samples = samples[:min(len(samples), active[-1] + int(.015 * rate))]
    count = int(overlap * rate)
    if count:
        # Circular overlap-add: the end meets the point immediately after its copied head.
        weight = np.linspace(0, 1, count, dtype=np.float32)[:, None]
        blended = samples[-count:] * (1 - weight) + samples[:count] * weight
        samples = np.concatenate((samples[count:-count], blended))
    return samples


def run_filter(ffmpeg, source, target, filters):
    result = subprocess.run([ffmpeg, '-hide_banner', '-nostdin', '-y', '-i', str(source),
                             '-af', filters, '-ar', '44100', '-c:a', 'pcm_f32le', str(target)],
                            capture_output=True, text=True, check=True)
    return result.stderr


def bake(track, settings, ffmpeg, cache, output, work):
    source = source_file(track, cache)
    samples, rate = sf.read(source, dtype='float32', always_2d=True)
    if samples.shape[1] != 2 or rate != settings['sampleRate']:
        raise ValueError('Unexpected source format: ' + str(source))
    # Remove DC/infrasonic energy while leaving the orchestral bass intact.
    samples = sosfiltfilt(butter(2, 25, 'highpass', fs=rate, output='sos'), samples, axis=0)
    if track['loop']:
        samples = prepare_loop(samples, rate, track['crossfadeSeconds'], track.get('trimTail', False))
    prepared = work / (track['name'] + '-prepared.wav')
    mastered = work / (track['name'] + '-mastered.wav')
    sf.write(prepared, samples, rate, subtype='FLOAT')
    loudness = f"loudnorm=I={track['targetLufs']}:TP=-3:LRA=11"
    measurement = subprocess.run([ffmpeg, '-hide_banner', '-nostdin', '-i', str(prepared),
                                  '-af', loudness + ':print_format=json', '-f', 'null', '-'],
                                 capture_output=True, text=True, check=True)
    stats = json.loads(re.findall(r'\{[^{}]+\}', measurement.stderr)[-1])
    measured = (f":measured_I={stats['input_i']}:measured_TP={stats['input_tp']}"
                f":measured_LRA={stats['input_lra']}:measured_thresh={stats['input_thresh']}"
                f":offset={stats['target_offset']}:linear=true")
    run_filter(ffmpeg, prepared, mastered, loudness + measured)
    samples, rate = sf.read(mastered, dtype='float32', always_2d=True)
    # Four-millisecond edge tapers avoid hard waveform steps at stream loop/restart boundaries.
    edge = int(.004 * rate)
    samples[:edge] *= np.linspace(0, 1, edge)[:, None]
    samples[-edge:] *= np.linspace(1, 0, edge)[:, None]
    if not track['loop']:
        tail = int(.15 * rate)
        samples[-tail:] *= np.linspace(1, 0, tail)[:, None]
    sf.write(mastered, samples, rate, subtype='PCM_24')
    target = output / (track['name'] + '.ogg')
    subprocess.run([ffmpeg, '-hide_banner', '-nostdin', '-loglevel', 'error', '-y', '-i', str(mastered),
                    '-c:a', settings['codec'], '-q:a', str(settings['quality']), '-ar', str(rate), '-ac', '2',
                    '-metadata', 'title=' + track['title'], '-metadata', 'artist=' + track['author'],
                    '-metadata', 'license=' + track['licenseUrl'], '-metadata', 'source=' + track['sourcePage'],
                    str(target)], check=True)
    decoded, decoded_rate = sf.read(target, dtype='float32', always_2d=True)
    return {'name': track['name'], 'seconds': round(len(decoded) / decoded_rate, 6),
            'bytes': target.stat().st_size, 'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
            'decodedPeakDbfs': round(float(20 * np.log10(np.max(np.abs(decoded)))), 3),
            'loopBoundaryStep': round(float(np.max(np.abs(decoded[0] - decoded[-1]))), 6),
            'sourceIntegratedLufs': float(stats['input_i']), 'targetLufs': track['targetLufs']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, default=ROOT / '.run/audio-production/music-sources')
    parser.add_argument('--output', type=Path, default=ROOT / 'assets/audio/music')
    parser.add_argument('--ffmpeg')
    args = parser.parse_args()
    manifest = json.loads(MANIFEST.read_text())
    args.cache.mkdir(parents=True, exist_ok=True)
    args.output.mkdir(parents=True, exist_ok=True)
    ffmpeg = ffmpeg_path(args.ffmpeg)
    report = []
    with tempfile.TemporaryDirectory(prefix='crypt-music-') as temp:
        for track in manifest['tracks']:
            result = bake(track, manifest['format'], ffmpeg, args.cache, args.output, Path(temp))
            report.append(result)
            print(json.dumps(result), flush=True)
    (MANIFEST.parent / 'bake-report.json').write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    main()
