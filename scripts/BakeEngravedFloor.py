#!/usr/bin/env python3
"""Append the original engraved crypt floor to four already resident atlas pages.

Run after BakePaintedArenaBorders.py. Canonical inputs include the complete current
baseline and generated masters; no old gameplay/border pixel or gutter moves.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import io
import json
from pathlib import Path
import zipfile
from PIL import Image
import BakePaintedArenaBorders as packing

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / 'art-source/crypt-floor-clarity'
METADATA = ('gameplay.json', 'enemy-attacks.json', 'magic-projectiles.json',
            'painted-borders.json', 'arena-border.json')
ASSETS = ('field', 'path', 'medallion')
PADDING, CONTEXT = 2, 1


def digest(data):
    return hashlib.sha256(data).hexdigest()


def encoded(image):
    output = io.BytesIO()
    image.save(output, format='PNG', optimize=True)
    return output.getvalue()


def capture_inputs(archive, sources, runtime):
    if archive.exists() or (runtime / 'engraved-floor.json').exists():
        raise ValueError('Capture must use the untouched pre-floor baseline exactly once')
    entries = {'baseline/' + name: (runtime / name).read_bytes() for name in METADATA}
    pages = json.loads(entries['baseline/gameplay.json'])['pages']
    floor = json.loads(entries['baseline/arena-border.json'])
    for page in pages + [{'file': floor['file']}]:
        entries['baseline/' + page['file']] = (runtime / page['file']).read_bytes()
    assets = ASSETS + (('curb',) if (sources / 'curb-master.png').exists() else ())
    for name in assets:
        entries['source/' + name + '-master.png'] = (sources / (name + '-master.png')).read_bytes()
    index = {name: {'bytes': len(data), 'sha256': digest(data)} for name, data in sorted(entries.items())}
    entries['SOURCES.json'] = (json.dumps(index, indent=2, sort_keys=True) + '\n').encode()
    archive.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            output.writestr(info, data)
    return {'archive': archive.name, 'sha256': digest(archive.read_bytes()), 'entries': index}


def load_baseline(read):
    metadata = {name: json.loads(read('baseline/' + name)) for name in METADATA}
    specs = metadata['gameplay.json']['pages'] + [
        {'file': metadata['arena-border.json']['file'], 'size': metadata['arena-border.json']['size']}]
    if [p['size'] for p in specs] != [[1024, 1024], [512, 512], [512, 512], [512, 256]]:
        raise ValueError('The existing four page dimensions must remain unchanged')
    pages = [Image.open(io.BytesIO(read('baseline/' + p['file']))).convert('RGBA') for p in specs]
    rectangles = [set() for _ in pages]
    for frame in metadata['gameplay.json']['frames'] + metadata['enemy-attacks.json']['frames']:
        rectangles[frame['page']].add(tuple(frame['rect']))
    for frame in metadata['magic-projectiles.json']['frames']:
        rectangles[metadata['magic-projectiles.json']['borrowedPage']].add(tuple(frame['rect']))
    for painting in metadata['painted-borders.json']['paintings'].values():
        for piece in painting['pieces']:
            rectangles[piece['page']].add(tuple(piece['rect']))
    for tile in metadata['arena-border.json']['tiles']:
        rectangles[3].add(tuple(tile['rect']))
    return metadata, specs, pages, rectangles


def smooth(value):
    value = max(0., min(1., value))
    return value * value * (3. - 2. * value)


def periodic_material(master, side, wrap_axes=('x', 'y')):
    """One whole-material normalization; boundary collars sample its original centre.

    This keeps the generated painting, rather than inventing stone geometry. A
    cyclic centre transplant removes residual generator edge discontinuities.
    """
    width, height = (side, side) if isinstance(side, int) else side
    square = master.resize((width, height), Image.Resampling.LANCZOS).convert('RGBA')
    original = square.copy()
    pixels, source = square.load(), original.load()
    def phases(pos, size):
        u = (pos + .5) / size
        weight = 1. - smooth(min(u, 1. - u) / .14)
        return ((pos, 1. - weight), ((pos + size // 2) % size, weight))
    for y in range(height):
        for x in range(width):
            result = [0., 0., 0.]
            for sx, wx in phases(x, width) if 'x' in wrap_axes else ((x, 1.),):
                for sy, wy in phases(y, height) if 'y' in wrap_axes else ((y, 1.),):
                    color = source[sx, sy]
                    for c in range(3):
                        result[c] += color[c] * wx * wy
            pixels[x, y] = tuple(round(v) for v in result) + (255,)
    # An original opposite-side neighbour is retained outside every repeat edge.
    wrapped = Image.new('RGBA', (width + CONTEXT * 2, height + CONTEXT * 2))
    wp = wrapped.load()
    for y in range(wrapped.height):
        for x in range(wrapped.width):
            sx, sy = x - CONTEXT, y - CONTEXT
            sx = sx % width if 'x' in wrap_axes else max(0, min(width-1, sx))
            sy = sy % height if 'y' in wrap_axes else max(0, min(height-1, sy))
            wp[x, y] = pixels[sx, sy]
    return wrapped


def normalize(masters):
    clarity = 'curb' in masters
    medallion = Image.new('RGBA', (258, 258))
    medallion.paste(masters['medallion'].resize((256, 256), Image.Resampling.LANCZOS), (1, 1))
    images = {'field': periodic_material(masters['field'], 64 if clarity else 96),
              'path': periodic_material(masters['path'], (128, 64) if clarity else 64, ('y',)),
              'medallion': medallion}
    curb_normalization = None
    if clarity:
        curb = masters['curb']
        bounds = curb.getchannel('A').point(lambda a: 255 if a >= 32 else 0).getbbox()
        if bounds is None: raise ValueError('The selected curb must contain visible painted stone')
        trimmed = curb.crop(bounds)
        ratio = min(30 / trimmed.width, 14 / trimmed.height)
        size = (max(1, round(trimmed.width * ratio)), max(1, round(trimmed.height * ratio)))
        fitted = trimmed.resize(size, Image.Resampling.LANCZOS)
        normalized = Image.new('RGBA', (34, 18))
        offset = (1 + (32-size[0])//2, 1 + (16-size[1])//2)
        normalized.paste(fitted, offset)
        pixels = normalized.load()
        for y in range(normalized.height):
            for x in range(normalized.width):
                r, g, b, a = pixels[x, y]
                pixels[x, y] = (round(r*a/255), round(g*a/255), round(b*a/255), a)
        images['curb'] = normalized
        curb_normalization = {'alphaTrimThreshold': 32, 'sourceAlphaBounds': list(bounds),
                              'fittedSize': list(size), 'fittedOffset': list(offset),
                              'operation': 'alpha crop, proportional Lanczos fit, transparent margin, RGB premultiplication'}
    paintings = {}
    for name, image in images.items():
        repeat = name in ('field', 'path')
        paintings[name] = {'file': name + '.png', 'sourceSize': list(image.size),
                           'contentRect': [1, 1, image.width - 2, image.height - 2],
                           'repeat': repeat, 'wrapAxes': ['x', 'y'] if name == 'field' else ['y'] if name == 'path' else [],
                           'alphaBounds': list(image.getchannel('A').getbbox())}
        if name == 'curb':
            paintings[name]['premultipliedAlpha'] = True
            paintings[name]['normalization'] = curb_normalization
    return paintings, images


def cells_for(paintings, images, core_width, core_height):
    cells = []
    for name, entry in paintings.items():
        image = images[name]
        ox, oy, width, height = entry['contentRect']
        for cy in range(oy, oy + height, core_height):
            cb = min(oy + height, cy + core_height)
            for cx in range(ox, ox + width, core_width):
                cr = min(ox + width, cx + core_width)
                if not image.crop((cx, cy, cr, cb)).getchannel('A').point(lambda a: 255 if a >= 32 else 0).getbbox():
                    continue
                sx, sy, ex, ey = max(0, cx-1), max(0, cy-1), min(image.width, cr+1), min(image.height, cb+1)
                cells.append({'key': f'{name}/{cx:05d}/{cy:05d}', 'painting': name,
                              'coreRect': [cx, cy, cr-cx, cb-cy], 'sourceRect': [sx, sy, ex-sx, ey-sy],
                              'image': image.crop((sx, sy, ex, ey))})
    return cells


def bake(archive, output, source_output=None):
    with zipfile.ZipFile(archive) as source:
        index = json.loads(source.read('SOURCES.json'))
        for name, expected in index.items():
            data = source.read(name)
            if len(data) != expected['bytes'] or digest(data) != expected['sha256']:
                raise ValueError('Canonical input changed: ' + name)
        metadata, specs, baseline, rectangles = load_baseline(source.read)
        assets = ASSETS + (('curb',) if 'source/curb-master.png' in index else ())
        masters = {name: Image.open(io.BytesIO(source.read('source/' + name + '-master.png'))).convert('RGBA')
                   for name in assets}
    for name in METADATA:
        old = output / name
        if old.exists() and digest(old.read_bytes()) != index['baseline/' + name]['sha256']:
            raise ValueError('Existing metadata changed outside additive floor stage: ' + name)
    paintings, images = normalize(masters)
    masks, capacity = packing.protect(baseline, rectangles)
    free = packing.free_rectangles(baseline, rectangles, masks)
    selected = None
    if 'curb' in masters:
        curb_cells = cells_for({'curb': paintings['curb']}, {'curb': images['curb']}, 32, 16)
        if len(curb_cells) != 1: raise ValueError('The curb must remain one complete storage piece')
        cells = []
        for name, cw, ch in (('field', 32, 16), ('path', 32, 16), ('medallion', 24, 8)):
            cells += cells_for({name: paintings[name]}, {name: images[name]}, cw, ch)
        old_padding = packing.PADDING
        try:
            packing.PADDING = 0
            curb_placement = packing.pack(curb_cells, free, 'height', 'short-side')
            if curb_placement is None: raise ValueError('The whole painted curb does not fit')
            remaining = [rects[:] for rects in free]
            for page, x, y, w, h in curb_placement.values():
                remaining[page] = packing.subtract(remaining[page], (x, y, w, h))
            placement = packing.pack(cells, remaining, 'height', 'short-side')
        finally:
            packing.PADDING = old_padding
        if placement is None: raise ValueError('Clarity materials do not fit the guarded existing pages')
        placement.update(curb_placement)
        selected = cells + curb_cells, placement, None, None, 0, 'whole curb first; mixed cores height/short-side'
    for new_padding in (() if selected else (2, 0)):
        for core_width, core_height in ((48, 48), (32, 32), (24, 24), (24, 8), (16, 8), (8, 8)):
            cells = cells_for(paintings, images, core_width, core_height)
            if len(cells) > 512: continue
            for order in ('height', 'area', 'width'):
                for heuristic in ('short-side', 'area', 'bottom-left'):
                    old_padding = packing.PADDING
                    try:
                        packing.PADDING = new_padding
                        placement = packing.pack(cells, free, order, heuristic)
                    finally:
                        packing.PADDING = old_padding
                    if placement is not None:
                        selected = cells, placement, core_width, core_height, new_padding, order + '/' + heuristic
                        break
                if selected is not None: break
            if selected is not None: break
        if selected is not None: break
    if selected is None:
        raise ValueError('Approved floor does not fit unchanged existing page capacity')
    cells, placement, core_width, core_height, new_padding, packing_order = selected
    coverage_report = {}
    for name, entry in paintings.items():
        image = images[name]
        counts = bytearray(image.width * image.height)
        for cell in cells:
            if cell['painting'] != name: continue
            x, y, w, h = cell['coreRect']
            sx, sy, sw, sh = cell['sourceRect']
            if x-sx != 1 or y-sy != 1 or sx+sw-(x+w) != 1 or sy+sh-(y+h) != 1:
                raise ValueError('Every core edge must retain exactly one real source neighbour')
            for yy in range(y, y+h):
                for xx in range(x, x+w): counts[yy*image.width+xx] += 1
        ox, oy, w, h = entry['contentRect']
        excluded, maximum = 0, 0
        for y in range(oy, oy+h):
            for x in range(ox, ox+w):
                alpha = image.getpixel((x, y))[3]
                count = counts[y*image.width+x]
                if count > 1 or (alpha >= 32 and count != 1):
                    raise ValueError('Significant original content is missing or duplicated')
                if alpha and not count:
                    excluded += 1
                    maximum = max(maximum, alpha)
        coverage_report[name] = {'significantPixelsCoveredExactlyOnce': True,
                                'excludedLowAlphaPixels': excluded, 'maximumExcludedAlpha': maximum}
    pages = [image.copy() for image in baseline]
    manifest = {'version': 1, 'addedTextureBytes': 0, 'pages': copy.deepcopy(specs), 'context': CONTEXT,
                'padding': new_padding, 'edgeExtrusion': 1 if new_padding else 0, 'trimAlphaThreshold': 32,
                'sourceArchiveSha256': digest(archive.read_bytes()), 'paintings': paintings}
    if 'curb' in masters: manifest['materialVersion'] = 2
    for entry in paintings.values(): entry['pieces'] = []
    for cell in cells:
        page, x, y, w, h = placement[cell['key']]
        if new_padding:
            packing.extrude(pages[page], cell['image'], x, y)
        else:
            pages[page].paste(cell['image'], (x, y))
        paintings[cell['painting']]['pieces'].append({'page': page, 'rect': [x, y, w, h],
            'sourceRect': cell['sourceRect'], 'coreRect': cell['coreRect']})
    for before, after, rows in zip(baseline, pages, masks):
        a, b = before.load(), after.load()
        for y, row in enumerate(rows):
            while row:
                bit = row & -row
                x = bit.bit_length() - 1
                if a[x, y] != b[x, y]: raise ValueError('Old protected sprite/gutter pixel changed')
                row ^= bit
    output.mkdir(parents=True, exist_ok=True)
    source_output = source_output or output / 'engraved-floor-sources'
    source_output.mkdir(parents=True, exist_ok=True)
    for name, image in images.items():
        (source_output / paintings[name]['file']).write_bytes(encoded(image))
    for page, spec in zip(pages, specs): page.save(output / spec['file'], optimize=True)
    saved = [Image.open(output / spec['file']).convert('RGBA') for spec in specs]
    for cell in cells:
        page, x, y, w, h = placement[cell['key']]
        if saved[page].crop((x, y, x+w, y+h)).tobytes() != cell['image'].tobytes():
            raise ValueError('Packed source context changed')
    manifest_data = (json.dumps(manifest, indent=2) + '\n').encode()
    (output / 'engraved-floor.json').write_bytes(manifest_data)
    report = {'version': 1, 'sourceArchive': archive.name, 'sourceArchiveSha256': digest(archive.read_bytes()),
            'addedTextureBytes': 0, 'newTexturePages': 0, 'sourcePixelsAndNeighbourContextPreserved': True,
            'existingPixelsAndGuttersPreserved': True, 'coreSize': [core_width, core_height],
            'newPieceOuterPadding': new_padding, 'originalNeighbourContext': CONTEXT, 'packingOrder': packing_order,
            'pieces': len(cells), 'paintingPieces': {name: len(entry['pieces']) for name, entry in paintings.items()},
            'contentCoverage': coverage_report,
            'pageCapacityBeforeAppend': capacity,
            'paintedPixelsWithPadding': sum((c['image'].width+new_padding*2)*(c['image'].height+new_padding*2) for c in cells),
            'baselineMetadataSha256': {name: index['baseline/'+name]['sha256'] for name in METADATA},
            'normalizedSourceSha256': {paintings[name]['file']: digest(encoded(image)) for name, image in images.items()},
            'outputs': {spec['file']: digest((output/spec['file']).read_bytes()) for spec in specs}
                       | {'engraved-floor.json': digest(manifest_data)}}
    if 'curb' in masters:
        report.pop('coreSize')
        report['materialVersion'] = 2
        report['coreSizes'] = {'field': [32, 16], 'path': [32, 16], 'medallion': [24, 8], 'curb': [32, 16]}
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, default=SOURCES/'bake-inputs.zip')
    parser.add_argument('--output', type=Path, default=ROOT/'assets/art')
    parser.add_argument('--source-dir', type=Path, default=SOURCES)
    parser.add_argument('--source-output', type=Path)
    parser.add_argument('--report', type=Path)
    parser.add_argument('--capture-inputs', action='store_true')
    args = parser.parse_args()
    result = capture_inputs(args.archive, args.source_dir, args.output) if args.capture_inputs else bake(args.archive, args.output, args.source_output)
    rendered = json.dumps(result, indent=2)+'\n'
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(rendered)
    print(rendered, end='')


if __name__ == '__main__': main()
