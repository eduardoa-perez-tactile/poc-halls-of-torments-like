#!/usr/bin/env python3
"""Append four approved painted corner joins to genuinely unused resident gameplay texels.

Capture the current baseline once after final source PNGs/corners.json are present,
then rebuild deterministically from bake-inputs.zip. No source or piece is resized.
All old rectangles/gutters and all nonzero RGBA pixels are protected, including
later supplemental content that was absent from historical gameplay archives.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import io
import json
from pathlib import Path
import zipfile
from PIL import Image, ImageChops
import BakePaintedArenaBorders as packing

ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / 'art-source/crypt-wall-join-repair'
METADATA = ('gameplay.json', 'enemy-attacks.json', 'magic-projectiles.json',
            'painted-borders.json', 'engraved-floor.json', 'arena-border.json')
DIRECTIONS = ('ne', 'nw', 'se', 'sw')
PADDING, CONTEXT, MAX_PIECES = 2, 1, 128


def digest(data):
    return hashlib.sha256(data).hexdigest()


def json_bytes(value):
    return (json.dumps(value, indent=2) + '\n').encode()


def load_baseline(read):
    documents = {name: json.loads(read('baseline/' + name)) for name in METADATA}
    specs = documents['gameplay.json']['pages']
    if [page['size'] for page in specs] != [[1024, 1024], [512, 512], [512, 512], [512, 256]]:
        raise ValueError('The existing four managed gameplay page dimensions must remain bounded')
    pages = [Image.open(io.BytesIO(read('baseline/' + spec['file']))).convert('RGBA') for spec in specs]
    if any(list(page.size) != spec['size'] for page, spec in zip(pages, specs)):
        raise ValueError('A baseline page disagrees with its declared dimensions')
    rectangles = [set() for _ in pages]

    runtime_page = {spec['file']: page for page, spec in enumerate(specs)}

    def reserve(document, page, rect):
        # The floor's page 3 is arena-border.png, not gameplay's resident Grave King page 3.
        filename = document['pages'][page]['file']
        if filename in runtime_page:
            rectangles[runtime_page[filename]].add(tuple(rect))

    for name in ('gameplay.json', 'enemy-attacks.json'):
        for frame in documents[name]['frames']:
            reserve(documents[name], frame['page'], frame['rect'])
    missiles = documents['magic-projectiles.json']
    for frame in missiles['frames']:
        rectangles[runtime_page[missiles['file']]].add(tuple(frame['rect']))
    for name in ('painted-borders.json', 'engraved-floor.json'):
        for painting in documents[name]['paintings'].values():
            for piece in painting['pieces']:
                reserve(documents[name], piece['page'], piece['rect'])
    return documents, specs, pages, rectangles


def valid_source_name(name):
    path = Path(name)
    if path.is_absolute() or '..' in path.parts:
        raise ValueError('Source files must stay within the selected art-source directory')
    return name


def capture_inputs(archive, source_dir, runtime):
    if archive.exists() or (runtime / 'painted-corners.json').exists():
        raise ValueError('Capture the unmodified pre-corner baseline once; never replace a canonical archive')
    manifest_bytes = (source_dir / 'corners.json').read_bytes()
    manifest = json.loads(manifest_bytes)
    validate_manifest(manifest)
    entries = {'baseline/' + name: (runtime / name).read_bytes() for name in METADATA}
    for spec in json.loads(entries['baseline/gameplay.json'])['pages']:
        entries['baseline/' + spec['file']] = (runtime / spec['file']).read_bytes()
    entries['source/corners.json'] = manifest_bytes
    names = {entry['file'] for entry in manifest['corners'].values()}
    names.update(manifest.get('provenanceFiles', []))
    for name in sorted(names):
        valid_source_name(name)
        entries['source/' + name] = (source_dir / name).read_bytes()
    index = {name: {'bytes': len(data), 'sha256': digest(data)} for name, data in sorted(entries.items())}
    entries['SOURCES.json'] = json_bytes(index)
    archive.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            output.writestr(info, data)
    return {'archive': archive.name, 'sha256': digest(archive.read_bytes()), 'entries': index}


def validate_manifest(manifest):
    if manifest.get('version') != 1 or set(manifest.get('corners', {})) != set(DIRECTIONS):
        raise ValueError('Exactly four original directional corner paintings are required')
    for direction, entry in manifest['corners'].items():
        valid_source_name(entry['file'])
        size, pivot, scale = entry['sourceSize'], entry['pivot'], entry['worldScale']
        if len(size) != 2 or len(pivot) != 2 or not 0 < scale <= 8:
            raise ValueError('Invalid source geometry for ' + direction)
        if any(not isinstance(side, int) or side < 1 or side > 512 for side in size):
            raise ValueError('Corner source canvases must remain bounded')
        if any(not 0 <= point <= side for point, side in zip(pivot, size)):
            raise ValueError('Corner pivots must lie on their original source canvases')


def rgba_bounds(image):
    mask = image.getchannel('R')
    for channel in ('G', 'B', 'A'):
        mask = ImageChops.lighter(mask, image.getchannel(channel))
    return mask.getbbox()


def storage_cells(images, width, height):
    cells = []
    for direction in DIRECTIONS:
        image = images[direction]
        for top in range(0, image.height, height):
            for left in range(0, image.width, width):
                right, bottom = min(image.width, left + width), min(image.height, top + height)
                bounds = rgba_bounds(image.crop((left, top, right, bottom)))
                if bounds is None:
                    continue
                cl, ct, cr, cb = left + bounds[0], top + bounds[1], left + bounds[2], top + bounds[3]
                sx, sy = max(0, cl - CONTEXT), max(0, ct - CONTEXT)
                ex, ey = min(image.width, cr + CONTEXT), min(image.height, cb + CONTEXT)
                cells.append({'key': direction + '/' + str(top).zfill(4) + '/' + str(left).zfill(4),
                              'painting': direction, 'coreRect': [cl, ct, cr - cl, cb - ct],
                              'sourceRect': [sx, sy, ex - sx, ey - sy],
                              'image': image.crop((sx, sy, ex, ey))})
    return cells


def extrude(page, image, x, y):
    """Two atlas gutter texels lie outside the one original-neighbour source collar."""
    w, h = image.size
    for dy in range(-PADDING, h + PADDING):
        for dx in range(-PADDING, w + PADDING):
            if 0 <= dx < w and 0 <= dy < h:
                continue
            page.putpixel((x + dx, y + dy), image.getpixel((max(0, min(w - 1, dx)), max(0, min(h - 1, dy)))))
    page.paste(image, (x, y))


def protection_hash(image, rows):
    selected = bytearray()
    pixels = image.load()
    for y, row in enumerate(rows):
        while row:
            bit = row & -row
            selected.extend(pixels[bit.bit_length() - 1, y])
            row ^= bit
    return digest(selected)


def bake(archive, output):
    with zipfile.ZipFile(archive) as source:
        index = json.loads(source.read('SOURCES.json'))
        for name, expected in index.items():
            data = source.read(name)
            if len(data) != expected['bytes'] or digest(data) != expected['sha256']:
                raise ValueError('Canonical input changed: ' + name)
        documents, specs, baseline, rectangles = load_baseline(source.read)
        manifest = json.loads(source.read('source/corners.json'))
        validate_manifest(manifest)
        images = {name: Image.open(io.BytesIO(source.read('source/' + manifest['corners'][name]['file']))).convert('RGBA')
                  for name in DIRECTIONS}
        for name, image in images.items():
            if list(image.size) != manifest['corners'][name]['sourceSize'] or rgba_bounds(image) is None:
                raise ValueError('Corner source canvas/paint changed: ' + name)
    masks, capacity = packing.protect(baseline, rectangles)
    free = packing.free_rectangles(baseline, rectangles, masks)
    selected = None
    for width, height in ((64, 48), (48, 48), (48, 32), (32, 32), (32, 24), (24, 24),
                          (18, 32), (18, 24), (16, 32), (16, 24), (12, 24), (12, 16)):
        cells = storage_cells(images, width, height)
        if len(cells) > MAX_PIECES:
            continue
        for order in ('area', 'height', 'width'):
            for heuristic in ('short-side', 'area', 'bottom-left'):
                placements = packing.pack(cells, free, order, heuristic)
                if placements is not None:
                    selected = cells, placements, width, height, order + '/' + heuristic
                    break
            if selected:
                break
        if selected:
            break
    if selected is None:
        raise ValueError('Four original corner paintings do not fit protected spare pixels of the resident pages')
    cells, placements, width, height, strategy = selected
    pages = [page.copy() for page in baseline]
    metadata = {'version': 1, 'addedTextureBytes': 0, 'pages': copy.deepcopy(specs),
                'context': CONTEXT, 'padding': PADDING, 'edgeExtrusion': PADDING,
                'sourceArchiveSha256': digest(archive.read_bytes()), 'corners': copy.deepcopy(manifest['corners'])}
    for entry in metadata['corners'].values():
        entry['pieces'] = []
    for cell in cells:
        page, x, y, w, h = placements[cell['key']]
        extrude(pages[page], cell['image'], x, y)
        metadata['corners'][cell['painting']]['pieces'].append(
            {'page': page, 'rect': [x, y, w, h], 'sourceRect': cell['sourceRect'], 'coreRect': cell['coreRect']})
    protected = []
    for before, after, rows in zip(baseline, pages, masks):
        old_hash, new_hash = protection_hash(before, rows), protection_hash(after, rows)
        if old_hash != new_hash:
            raise ValueError('A protected old pixel, sampling rectangle or gutter was modified')
        protected.append(old_hash)
    # Reject concurrent unrelated atlas changes before replacing any output page.
    for name in METADATA:
        path = output / name
        if path.exists() and digest(path.read_bytes()) != index['baseline/' + name]['sha256']:
            raise ValueError('Current metadata changed after baseline capture: ' + name)
    encoded_pages = []
    for page, spec in zip(pages, specs):
        encoded = io.BytesIO(); page.save(encoded, format='PNG', optimize=True)
        data = encoded.getvalue(); encoded_pages.append(data)
        path = output / spec['file']
        if path.exists() and digest(path.read_bytes()) not in (index['baseline/' + spec['file']]['sha256'], digest(data)):
            raise ValueError('Current texture changed after baseline capture: ' + spec['file'])
    output.mkdir(parents=True, exist_ok=True)
    for data, spec in zip(encoded_pages, specs):
        (output / spec['file']).write_bytes(data)
    metadata_data = json_bytes(metadata)
    (output / 'painted-corners.json').write_bytes(metadata_data)
    saved = [Image.open(output / spec['file']).convert('RGBA') for spec in specs]
    rebuilt = {name: Image.new('RGBA', image.size) for name, image in images.items()}
    for cell in cells:
        page, x, y, w, h = placements[cell['key']]
        stored = saved[page].crop((x, y, x + w, y + h))
        if stored.tobytes() != cell['image'].tobytes():
            raise ValueError('A saved collar differs from its original continuous corner painting')
        cx, cy, cw, ch = cell['coreRect']
        sx, sy, _, _ = cell['sourceRect']
        rebuilt[cell['painting']].paste(stored.crop((cx - sx, cy - sy, cx - sx + cw, cy - sy + ch)), (cx, cy))
    for name, image in images.items():
        if rebuilt[name].tobytes() != image.tobytes():
            raise ValueError('Saved storage cores failed exact whole-painting RGBA reconstruction: ' + name)
    for old_hash, image, rows in zip(protected, saved, masks):
        if protection_hash(image, rows) != old_hash:
            raise ValueError('Saved protected baseline pixels differ')
    return {'version': 1, 'sourceArchiveSha256': digest(archive.read_bytes()), 'addedTextureBytes': 0,
            'pieceCount': len(cells), 'maxPieces': MAX_PIECES, 'coreGrid': [width, height], 'packing': strategy,
            'context': CONTEXT, 'padding': PADDING, 'edgeExtrusion': PADDING,
            'pageCapacityBeforeAppend': capacity, 'baselineProtectedPixelSha256': protected,
            'baselineMetadataSha256': {name: index['baseline/' + name]['sha256'] for name in METADATA},
            'sourceRgbaPreserved': True, 'existingPixelsAndGuttersPreserved': True,
            'sourceRgbaSha256': {name: digest(image.tobytes()) for name, image in images.items()},
            'outputs': {spec['file']: digest(data) for spec, data in zip(specs, encoded_pages)}
                       | {'painted-corners.json': digest(metadata_data)}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, default=SOURCE_DIR / 'bake-inputs.zip')
    parser.add_argument('--source-dir', type=Path, default=SOURCE_DIR)
    parser.add_argument('--output', type=Path, default=ROOT / 'assets/art')
    parser.add_argument('--report', type=Path)
    parser.add_argument('--capture-inputs', action='store_true')
    parser.add_argument('--capacity', action='store_true')
    args = parser.parse_args()
    if args.capacity:
        _, specs, pages, rectangles = load_baseline(lambda name: (args.output / name.removeprefix('baseline/')).read_bytes())
        masks, capacity = packing.protect(pages, rectangles)
        free = packing.free_rectangles(pages, rectangles, masks)
        print(json.dumps({'pages': specs, 'capacity': capacity,
                          'largestFreeRectangles': [sorted(rects, key=lambda r: -r[2] * r[3])[:5] for rects in free]}, indent=2))
        return
    if args.capture_inputs:
        print(json.dumps(capture_inputs(args.archive, args.source_dir, args.output), indent=2))
        return
    report = bake(args.archive, args.output)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_bytes(json_bytes(report))
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
