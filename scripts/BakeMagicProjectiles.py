#!/usr/bin/env python3
"""Bake original magic missiles into unused pixels of the existing environment page.

The tracked canonical archive contains the untouched base page, its occupied
rectangles and all generated sources/prompts. No image service or install runs.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile
from PIL import Image

PROJECT = Path(__file__).resolve().parent.parent
ARCHIVE = PROJECT / 'art-source/magic-projectiles-canonical.zip'
TYPES = ('RANGER', 'CULTIST', 'SOVEREIGN')
PADDING = 2


def intersect(a, b):
    return a[0] < b[0] + b[2] and b[0] < a[0] + a[2] and a[1] < b[1] + b[3] and b[1] < a[1] + a[3]


def contains(a, b):
    return a[0] <= b[0] and a[1] <= b[1] and a[0] + a[2] >= b[0] + b[2] and a[1] + a[3] >= b[1] + b[3]


def subtract(free, used):
    result = []
    for r in free:
        if not intersect(r, used):
            result.append(r); continue
        x, y, w, h = r; ux, uy, uw, uh = used
        if ux > x: result.append((x, y, ux - x, h))
        if ux + uw < x + w: result.append((ux + uw, y, x + w - ux - uw, h))
        if uy > y: result.append((x, y, w, uy - y))
        if uy + uh < y + h: result.append((x, uy + uh, w, y + h - uy - uh))
    result = sorted(set(result))
    return [r for i, r in enumerate(result) if not any(i != j and contains(s, r) for j, s in enumerate(result))]


def extrude(page, image, x, y):
    page.paste(image, (x, y))
    w, h = image.size
    page.paste(image.crop((0, 0, w, 1)), (x, y - 1))
    page.paste(image.crop((0, h - 1, w, h)), (x, y + h))
    page.paste(image.crop((0, 0, 1, h)), (x - 1, y))
    page.paste(image.crop((w - 1, 0, w, h)), (x + w, y))
    for dx, dy, sx, sy in [(-1, -1, 0, 0), (w, -1, w - 1, 0), (-1, h, 0, h - 1), (w, h, w - 1, h - 1)]:
        page.putpixel((x + dx, y + dy), image.getpixel((sx, sy)))


def bake(archive, output):
    with zipfile.ZipFile(archive) as z:
        manifest = json.loads(z.read('SOURCES.json'))
        for name, expected in manifest['files'].items():
            data = z.read(name)
            if hashlib.sha256(data).hexdigest() != expected['sha256'] or len(data) != expected['bytes']:
                raise ValueError('Canonical source changed: ' + name)
        base = Image.open(io.BytesIO(z.read('base-environment.png'))).convert('RGBA')
        gameplay = json.loads(z.read('base-gameplay.json'))
        page = base.copy(); free = [(0, 0, 512, 512)]; occupied = []
        for f in gameplay['frames']:
            if f['page'] != 2: continue
            x, y, w, h = f['rect']
            box = (max(0, x - PADDING), max(0, y - PADDING), min(512, x + w + PADDING) - max(0, x - PADDING),
                   min(512, y + h + PADDING) - max(0, y - PADDING))
            occupied.append(box); free = subtract(free, box)
        cells = []
        for kind in TYPES:
            raw = Image.open(io.BytesIO(z.read(kind.lower() + '-raw.png'))).convert('RGBA')
            if raw.size != (1536, 1024): raise ValueError('Expected 4x2 sheet: ' + kind)
            for i in range(8):
                x, y = (i % 4) * 384, (i // 4) * 512
                cell = raw.crop((x, y, x + 384, y + 512))
                cell.putalpha(cell.getchannel('A').point(lambda a: 0 if a <= 8 else a))
                frame = cell.resize((96, 128), Image.Resampling.LANCZOS)
                bounds = frame.getchannel('A').getbbox()
                if bounds is None: raise ValueError('Empty frame')
                if bounds[0] == 0 or bounds[1] == 0 or bounds[2] == 96 or bounds[3] == 128:
                    raise ValueError('Clipped frame: ' + kind + str(i))
                pivot = manifest['normalization'][kind]['pivot']
                cells.append({'kind': kind, 'index': i, 'image': frame.crop(bounds), 'offset': list(bounds[:2]), 'pivot': pivot})
        frames = [None] * 24
        for cell in sorted(cells, key=lambda c: (-(c['image'].width + 4)*(c['image'].height + 4), TYPES.index(c['kind']), c['index'])):
            image = cell['image']; w, h = image.size; rw, rh = w + 4, h + 4
            choices = [r for r in free if r[2] >= rw and r[3] >= rh]
            if not choices: raise ValueError('No spare environment-page rectangle for ' + cell['kind'])
            r = min(choices, key=lambda r: (min(r[2]-rw, r[3]-rh), max(r[2]-rw, r[3]-rh), r[1], r[0]))
            used = (r[0], r[1], rw, rh); free = subtract(free, used)
            x, y = r[0] + 2, r[1] + 2; extrude(page, image, x, y)
            index = TYPES.index(cell['kind']) * 8 + cell['index']
            frames[index] = {'rect': [x, y, w, h], 'source': [96, 128], 'offset': cell['offset'], 'pivot': cell['pivot']}
        for x, y, w, h in occupied:
            if page.crop((x,y,x+w,y+h)).tobytes() != base.crop((x,y,x+w,y+h)).tobytes():
                raise ValueError('Existing gameplay pixels were overwritten')
        metadata = {'version': 1, 'borrowedPage': 2, 'file': 'gameplay-environment.png', 'size': [512, 512],
                    'addedTextureBytes': 0, 'padding': 2, 'edgeExtrusion': 1, 'frames': frames,
                    'animations': [{'name': kind, 'frames': list(range(i*8,i*8+8)), 'durationsMs': [80]*8} for i,kind in enumerate(TYPES)]}
        output.mkdir(parents=True, exist_ok=True)
        page.save(output/'gameplay-environment.png')
        (output/'magic-projectiles.json').write_text(json.dumps(metadata, indent=2)+'\n')
        loaded = Image.open(output/'gameplay-environment.png').convert('RGBA')
        for cell in cells:
            f = frames[TYPES.index(cell['kind'])*8+cell['index']]; x,y,w,h = f['rect']
            if loaded.crop((x,y,x+w,y+h)).tobytes() != cell['image'].tobytes(): raise ValueError('Saved sprite mismatch')
        print(json.dumps({'output': str(output), 'projectileFrames': 24, 'addedTextureBytes': 0,
                          'preservedExistingFrames': sum(f['page']==2 for f in gameplay['frames']),
                          'rgbaPageBytes': 512*512*4}, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sources', type=Path, default=ARCHIVE)
    parser.add_argument('--output', type=Path, default=PROJECT/'assets/art')
    args = parser.parse_args(); bake(args.sources, args.output)


if __name__ == '__main__': main()
