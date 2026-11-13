#!/usr/bin/env python3
"""Archive complete canonical atlas inputs and provenance, excluding raw posters."""
import hashlib
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
EXPLORE = ROOT / '.run/art-exploration'
REPORT = EXPLORE / 'weekend/packed/pack-report.json'
ARCHIVE = ROOT / 'art-source/gameplay-canonical.zip'
ACTOR_DIRS = ['warden-pilot-v1', 'warden-pilot-v2'] + [
    'weekend/' + name for name in ('ranger', 'ghoul', 'wraith', 'brute', 'cultist', 'sovereign', 'environment')]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    report = json.loads(REPORT.read_text())
    if report.get('status') != 'complete' or not report.get('runtimeValid') or report.get('frameCount') != 227:
        raise ValueError('A complete strict 208-actor-frame / 19-region pack is required')
    contents = {}
    for source in report['sources']:
        path = (ROOT / source['path']).resolve()
        relative = path.relative_to(EXPLORE)
        data = path.read_bytes()
        if sha(data) != source['sha256']:
            raise ValueError('Packed input changed: ' + str(relative))
        contents[str(relative)] = data
    # Text records include prompts, landmarks, normalization and original output hashes.
    # Canonical pixels above are sufficient for an exact atlas rebuild; large generated
    # master posters and review media remain in the local art exploration directory.
    for directory in ACTOR_DIRS:
        for path in sorted((EXPLORE / directory).rglob('*')):
            if path.is_file() and path.suffix.lower() in ('.json', '.txt', '.md'):
                if path.stat().st_size > 2 * 1024 * 1024:
                    raise ValueError('Unexpected oversized text provenance: ' + str(path))
                contents.setdefault(str(path.relative_to(EXPLORE)), path.read_bytes())
    for relative in ('weekend/emitter-anchors.json', 'weekend/emitter-review.md'):
        path = EXPLORE / relative
        contents[relative] = path.read_bytes()
    index = {
        'version': 1, 'method': 'Built-in ImageGen painted masters; deterministic canonical PNG exports and lossless offline atlas packing.',
        'scope': 'Exact canonical pack inputs plus text provenance. Raw generated posters and review videos are retained locally, outside this archive.',
        'historicalStatus': 'Exploration metadata preserves its original review status. Final inclusion is established by the complete strict pack and integration handoff.',
        'files': [{'path': name, 'sha256': sha(data), 'bytes': len(data)} for name, data in sorted(contents.items())],
        'runtimeOutputHashes': report['outputHashes'],
        'rebuild': 'Extract archive into .run/art-exploration, then run python3 scripts/BakeGameplayAtlas.py with installed Pillow.'
    }
    contents['CANONICAL-SOURCES.json'] = (json.dumps(index, indent=2) + '\n').encode()
    ARCHIVE.parent.mkdir(exist_ok=True)
    temporary = ARCHIVE.with_suffix('.zip.new')
    try:
        with zipfile.ZipFile(temporary, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name, data in sorted(contents.items()):
                entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                entry.external_attr = 0o100644 << 16
                archive.writestr(entry, data)
        with zipfile.ZipFile(temporary) as archive:
            for name, data in contents.items():
                if archive.read(name) != data:
                    raise ValueError('Archive reconstruction failed: ' + name)
        temporary.replace(ARCHIVE)
    finally:
        temporary.unlink(missing_ok=True)
    print(json.dumps({'archive': str(ARCHIVE.relative_to(ROOT)), 'files': len(contents),
                      'bytes': ARCHIVE.stat().st_size, 'sha256': sha(ARCHIVE.read_bytes())}))


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError, TypeError) as failure:
        print('Canonical archive rejected: ' + str(failure), file=sys.stderr)
        raise SystemExit(2)
