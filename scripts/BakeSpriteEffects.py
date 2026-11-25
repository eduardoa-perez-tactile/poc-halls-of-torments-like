#!/usr/bin/env python3
"""Bake the nine approved original effects from a portable canonical ZIP.

No generation, runtime rendering, or existing-art mutation occurs here. The exact
15/64 export scale preserves the measured 2:1 footprint and fits all 72 frames,
with sampling gutters, in a single 512-square RGBA page.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
from types import SimpleNamespace
import zipfile

from PIL import Image

from BakeGameplayAtlas import pack_page, padded_content

ROOT = Path(__file__).resolve().parents[1]
SCALE = 15 / 64
PAGE = (512, 512)
NAMES = {
    'target-boundary': 'TARGET', 'soul-pulse': 'SOUL_PULSE',
    'danger-warning': 'WARNING', 'ground-impact': 'GROUND_IMPACT',
    'dodge-trail': 'DODGE', 'sword-sweep': 'SWORD_SWEEP',
    'storm-arc': 'STORM', 'contact-burst': 'CONTACT', 'block-flash': 'BLOCK',
}


def encoded(value):
    return (json.dumps(value, indent=2, ensure_ascii=False) + '\n').encode()


def sha(data):
    return hashlib.sha256(data).hexdigest()


def package_review(review: Path, archive: Path):
    """Preserve approved masters, their exact prompts and normalization evidence."""
    review = review.resolve()
    manifest = json.loads((review / 'review-input.json').read_text())
    blobs, animations, portable_review = {}, [], dict(manifest)
    portable_review['reference'] = 'provenance/user-reference.png'
    portable_review['animations'] = []
    for source in manifest['animations']:
        selected = Path(source['raw'])
        selected_name = str(selected.resolve().relative_to(review))
        blobs[selected_name] = selected.read_bytes()
        image = Image.open(io.BytesIO(blobs[selected_name]))
        columns, rows = source.get('columns', 4), source.get('rows', 2)
        original = dict(source)
        original['raw'] = selected_name
        original.pop('normalizationEvidence', None)
        if source.get('originalRaw'):
            original_name = str(Path(source['originalRaw']).resolve().relative_to(review))
            blobs[original_name] = Path(source['originalRaw']).read_bytes()
            original['originalRaw'] = original_name
        elif source['id'] == 'sword-sweep':
            original_name = 'sources/sword-sweep-raw.png'
            blobs[original_name] = (review / 'sources/sword-sweep-raw.png').read_bytes()
            original['originalRaw'] = original_name
        portable_review['animations'].append(original)
        clip = {'name': NAMES[source['id']], 'source': selected_name,
                'grid': [columns, rows], 'cell': [image.width // columns, image.height // rows],
                'pivot': source['pivot'], 'durationsMs': source['durationsMs'],
                'groundRadius': source.get('groundRadius', 0),
                'loop': source.get('loop', False),
                'recommendedScale': source.get('effectScale', 1),
                'normalization': source.get('normalization', 'Original approved source; no projection edit.')}
        if source['id'] == 'storm-arc':
            clip['endpoints'] = [[65, 345], [340, 270]]
        animations.append(clip)
    blobs['provenance/user-reference.png'] = (review / 'sources/reference.png').read_bytes()
    blobs['provenance/review-input.json'] = encoded(portable_review)
    for local, target in [('ground-measurements.json', 'ground-measurements.json'),
                          ('normalized/manifest.json', 'normalized/manifest.json')]:
        # Paths in evidence become archive-relative; numeric measurements remain exact.
        text = (review / local).read_text().replace(str(review) + '/', '')
        blobs[target] = text.encode()
    blobs['normalize_ground.py'] = (review / 'normalize_ground.py').read_bytes()
    blobs['provenance/README.md'] = (
        '# Approved original sprite effects\n\n'
        'Created with the built-in image_gen tool on 2026-11-24, using the user-provided '
        'screenshot as visual inspiration. These are original generated raster sheets, '
        'not downloaded or baked Godot shader assets. Exact prompts are retained in '
        'review-input.json. The user approved the composited Warden/Ranger review before '
        'authorizing integration.\n\n'
        'Ground sheets use measured rim centers and per-frame Y correction to the '
        'arena 2:1 plane. Original masters remain included. The sword source uses one '
        'shared 0.45 Y correction around its fixed anchor. No runtime perspective '
        'fitting, procedural rings or effect-line generation is needed. Pulse/impact '
        'rim fits have organic/low-confidence edges; faint particle-only frames inherit '
        'the nearest stable correction. Existing source clipping is not reconstructed.\n'
    ).encode()
    source = {'version': 1, 'date': '2026-11-24', 'generator': 'built-in image_gen',
              'approval': 'User approved the nine original isometric sprite proposals before integration.',
              'exportScale': [15, 64], 'sourceAlphaCutoff': 4, 'resampledAlphaCutoff': 2,
              'page': list(PAGE), 'animations': animations,
              'hashes': {name: sha(data) for name, data in sorted(blobs.items())}}
    blobs['SOURCES.json'] = encoded(source)
    archive.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, data in sorted(blobs.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, data)
    return source


def bake(archive: Path, output: Path):
    frames, cells, clips = [], [], []
    with zipfile.ZipFile(archive) as z:
        source = json.loads(z.read('SOURCES.json'))
        for name, expected in source['hashes'].items():
            if sha(z.read(name)) != expected:
                raise ValueError('Canonical source hash differs: ' + name)
        if source['exportScale'] != [15, 64] or source['page'] != list(PAGE):
            raise ValueError('Unexpected scale or resource envelope')
        if [a['name'] for a in source['animations']] != list(NAMES.values()):
            raise ValueError('The complete nine approved effects are required')
        for clip in source['animations']:
            raw = Image.open(io.BytesIO(z.read(clip['source']))).convert('RGBA')
            cw, ch = clip['cell']; cols, rows = clip['grid']
            if raw.size != (cw * cols, ch * rows) or cols * rows != 8:
                raise ValueError('Canonical grid differs: ' + clip['name'])
            width, height = cw * SCALE, ch * SCALE
            if int(width) != width or int(height) != height:
                raise ValueError('Export scale would distort the approved source aspect')
            size = [int(width), int(height)]
            pivot = [v * SCALE for v in clip['pivot']]
            refs = []
            for i in range(8):
                x, y = i % cols * cw, i // cols * ch
                cell = raw.crop((x, y, x + cw, y + ch))
                cell.putalpha(cell.getchannel('A').point(lambda a: 0 if a <= 4 else round((a - 4) * 255 / 251)))
                cell = cell.resize(tuple(size), Image.Resampling.LANCZOS)
                # Discard only nearly invisible interpolation tails before trimming.
                cell.putalpha(cell.getchannel('A').point(lambda a: 0 if a <= 2 else a))
                bounds = cell.getchannel('A').getbbox() or (0, 0, 1, 1)
                key = f"{clip['name']}/{i}"
                refs.append(len(frames)); cells.append(cell)
                frames.append(SimpleNamespace(key=key, bounds=bounds, source=size, pivot=pivot,
                    outer_size=(bounds[2] - bounds[0] + 4, bounds[3] - bounds[1] + 4)))
            exported = {'name': clip['name'], 'frames': refs, 'durationsMs': clip['durationsMs'],
                        'groundRadius': clip['groundRadius'] * SCALE, 'referenceScale': 1 / SCALE,
                        'recommendedScale': clip['recommendedScale'] / SCALE, 'loop': clip['loop']}
            if 'endpoints' in clip:
                exported['endpoints'] = [[v * SCALE for v in point] for point in clip['endpoints']]
            clips.append(exported)
    positions, method = pack_page(frames, PAGE)
    image = Image.new('RGBA', PAGE)
    descriptors = []
    for frame, cell in zip(frames, cells):
        x, y, _, _ = positions[frame.key]
        l, t, r, b = frame.bounds
        image.paste(padded_content(cell.crop(frame.bounds)), (x, y))
        descriptors.append({'rect': [x + 2, y + 2, r - l, b - t],
                            'source': frame.source, 'offset': [l, t], 'pivot': frame.pivot})
    output.mkdir(parents=True, exist_ok=True)
    png = output / 'sprite-effects.png'
    image.save(png)
    saved = Image.open(png).convert('RGBA')
    rectangles = []
    for frame, cell, descriptor in zip(frames, cells, descriptors):
        outer = positions[frame.key]; x, y, w, h = outer
        if x < 0 or y < 0 or x + w > PAGE[0] or y + h > PAGE[1]:
            raise ValueError('Packed frame outside atlas: ' + frame.key)
        for ox, oy, ow, oh in rectangles:
            if x < ox + ow and ox < x + w and y < oy + oh and oy < y + h:
                raise ValueError('Packed frames overlap: ' + frame.key)
        rectangles.append(outer)
        expected = padded_content(cell.crop(frame.bounds))
        if saved.crop((x, y, x + w, y + h)).tobytes() != expected.tobytes():
            raise ValueError('Saved frame/gutter differs: ' + frame.key)
    metadata = {'version': 1, 'file': 'sprite-effects.png', 'size': list(PAGE),
                'frames': descriptors, 'animations': clips}
    (output / 'sprite-effects.json').write_bytes(encoded(metadata))
    return {'frames': len(frames), 'dimensions': list(PAGE), 'textureBytes': 512 * 512 * 4,
            'retainedPixmapBytes': 0, 'paddedPixels': sum(f.outer_size[0] * f.outer_size[1] for f in frames),
            'pack': method, 'exportScale': SCALE, 'combinedTextureBytesIncludingQr': 33_516_364,
            'headroomBelow32MiB': 38_068,
            'hashes': {name: sha((output / name).read_bytes()) for name in ('sprite-effects.png', 'sprite-effects.json')}}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=ROOT / 'art-source/sprite-effects-canonical.zip')
    parser.add_argument('--output', type=Path, default=ROOT / 'assets/art')
    parser.add_argument('--package-review', type=Path, help='Archive the approved local review before baking')
    args = parser.parse_args()
    if args.package_review:
        package_review(args.package_review, args.source)
    # The approved 25 November melee wave supersedes the canonical sword strip.
    # Keep bake() available to reconstruct the untouched base bank, then overlay
    # the current production replacement for both public export entry points.
    from BakeMeleeWave import export
    print(json.dumps(export(args.output, args.source), indent=2))
