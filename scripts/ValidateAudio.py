#!/usr/bin/env python3
"""Check shipped score decoding/headroom/loop seams and sampled-effect reproducibility."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parents[1]


def main():
    subprocess.run([sys.executable, str(ROOT / 'scripts/BakeSoundEffects.py'), '--check'], check=True)
    manifest = json.loads((ROOT / 'audio-source/music/manifest.json').read_text())
    report = json.loads((ROOT / 'audio-source/music/bake-report.json').read_text())
    reports = {entry['name']: entry for entry in report}
    total = 0
    for track in manifest['tracks']:
        path = ROOT / 'assets/audio/music' / (track['name'] + '.ogg')
        info = sf.info(path)
        assert info.format == 'OGG' and info.subtype == 'VORBIS', path
        assert info.channels == 2 and info.samplerate == 44100, path
        assert 20 < info.duration < 180, (path, info.duration)
        samples, rate = sf.read(path, dtype='float32', always_2d=True)
        assert np.all(np.isfinite(samples)), path
        assert np.max(np.abs(samples)) < .85, 'Insufficient decoded headroom: ' + str(path)
        assert np.sqrt(np.mean(samples ** 2)) > .005, 'Silent score: ' + str(path)
        if track['loop']:
            step = np.max(np.abs(samples[0] - samples[-1]))
            assert step < .015, ('Loop seam discontinuity', path, step)
            assert np.sqrt(np.mean(samples[:int(.1 * rate)] ** 2)) > .0001, path
            assert np.sqrt(np.mean(samples[-int(.1 * rate):] ** 2)) > .0001, path
        entry = reports[track['name']]
        assert hashlib.sha256(path.read_bytes()).hexdigest() == entry['sha256'], path
        assert abs(info.duration - entry['seconds']) < .0001, path
        total += path.stat().st_size
    assert len(manifest['tracks']) == 6 and total < 8 * 1024 * 1024
    catalog = (ROOT / 'core/src/main/java/com/couchcrypt/game/SoundCue.java').read_text()
    names = re.findall(r'^\s+\w+\("([^\"]+)"', catalog, re.M)
    assert len(names) == 27 and all((ROOT / 'assets/audio' / (name + '.wav')).exists() for name in names)
    print(f'Validated 27 sampled effects and 6 streamed tracks ({total:,} encoded music bytes).')


if __name__ == '__main__':
    main()
