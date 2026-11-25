#!/usr/bin/env python3
"""Bake the approved elevated Mourning Bell sheet into one managed 256-square page.

This stage runs after the existing gameplay, attacks, borders, floor, Grave King
and corner stages. It appends eight frames without repacking any resident art.
The original source is retained unchanged in art-source/mourning-bell.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import io
import json
from pathlib import Path
import zipfile

from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
SOURCE_NAME = 'generated-facing-sheet.png'
PAGE_NAME = 'gameplay-mourning-bell.png'
PAGE_SIZE = (256, 256)
CANVAS, PIVOT = (96, 96), (48, 96)
FACINGS = ('SW', 'SE', 'NE', 'NW')
GUTTER, TARGET_HEIGHT, TOP = 2, 80, 4
WORLD_HEIGHT = 120


def digest(data):
    return hashlib.sha256(data).hexdigest()


def encoded_json(value):
    return (json.dumps(value, indent=2) + '\n').encode()


def source_bytes(path):
    if path.is_dir():
        return (path / SOURCE_NAME).read_bytes()
    if path.suffix.lower() == '.zip':
        with zipfile.ZipFile(path) as archive:
            matches = [name for name in archive.namelist() if Path(name).name == SOURCE_NAME]
            if len(matches) != 1:
                raise ValueError('Source archive must contain exactly one ' + SOURCE_NAME)
            return archive.read(matches[0])
    return path.read_bytes()


def defringe(cell):
    """Remove saturated-red generator matte only beside the transparent boundary.

    Dark burgundy mouth flesh, the amber eye and every interior pixel are kept.
    Alpha is retained when a nearby true body colour can replace the matte RGB.
    Isolated red matte with no nearby body sample is fully transparent.
    """
    pixels = cell.load()
    near_edge = cell.getchannel('A').filter(ImageFilter.MinFilter(7)).load()
    source = cell.copy().load()
    changed, cleared = 0, 0

    def red(value):
        r, g, b, _ = value
        return r > 120 and r > g * 1.8 and r > b * 1.8 and r - g > 70

    for y in range(cell.height):
        for x in range(cell.width):
            colour = source[x, y]
            if colour[3] <= 8 or near_edge[x, y] >= 32 or not red(colour):
                continue
            closest = None
            for dy in range(-5, 6):
                for dx in range(-5, 6):
                    xx, yy = x + dx, y + dy
                    if not (0 <= xx < cell.width and 0 <= yy < cell.height):
                        continue
                    candidate = source[xx, yy]
                    if candidate[3] < 192 or red(candidate):
                        continue
                    score = (dx * dx + dy * dy, -candidate[3], yy, xx)
                    if closest is None or score < closest[0]:
                        closest = score, candidate
            if closest:
                pixels[x, y] = closest[1][:3] + (colour[3],)
            else:
                pixels[x, y] = (0, 0, 0, 0)
                cleared += 1
            changed += 1
    return cell, changed, cleared


def normalized(data):
    sheet = Image.open(io.BytesIO(data)).convert('RGBA')
    if sheet.size != (1774, 887):
        raise ValueError('The approved four-column/two-row sheet must be 1774x887')
    cells, bounds = [], []
    changed, cleared = 0, 0
    for row in range(2):
        row_cells, row_bounds = [], []
        for column in range(4):
            box = (round(column * sheet.width / 4), round(row * sheet.height / 2),
                   round((column + 1) * sheet.width / 4), round((row + 1) * sheet.height / 2))
            cell = sheet.crop(box)
            cell, changed_here, cleared_here = defringe(cell)
            changed += changed_here
            cleared += cleared_here
            cell.putalpha(cell.getchannel('A').point(lambda value: 0 if value <= 8 else value))
            visible = cell.getchannel('A').point(lambda value: 255 if value >= 16 else 0).getbbox()
            if visible is None:
                raise ValueError('Empty source pose')
            row_cells.append(cell)
            row_bounds.append(visible)
        cells.append(row_cells)
        bounds.append(row_bounds)
    shared = []
    for column in range(4):
        a, b = bounds[0][column], bounds[1][column]
        shared.append((max(0, min(a[0], b[0]) - 2), max(0, min(a[1], b[1]) - 2),
                       min(cells[0][column].width, max(a[2], b[2]) + 2),
                       min(cells[0][column].height, max(a[3], b[3]) + 2)))
    scale = TARGET_HEIGHT / max(box[3] - box[1] for box in shared)
    images = []
    for row in range(2):
        for column in range(4):
            box = shared[column]
            size = (round((box[2] - box[0]) * scale), round((box[3] - box[1]) * scale))
            sprite = cells[row][column].crop(box).resize(size, Image.Resampling.LANCZOS)
            sprite.putalpha(sprite.getchannel('A').point(lambda value: 0 if value <= 2 else value))
            full = Image.new('RGBA', CANVAS)
            full.paste(sprite, ((CANVAS[0] - size[0]) // 2, TOP))
            images.append(full)
    report = {'sourceSize': list(sheet.size), 'sharedFacingCrops': dict(zip(FACINGS, shared)),
              'globalScale': scale, 'canvas': list(CANVAS), 'groundPivot': list(PIVOT),
              'targetMaxHeight': TARGET_HEIGHT, 'top': TOP,
              'boundaryRedMattePixelsCorrected': changed, 'isolatedMattePixelsCleared': cleared,
              'defringePolicy': 'saturated red only within three source pixels of alpha<32; nearest opaque body RGB, original alpha; source unchanged',
              'posePolicy': 'one shared facing crop and placement, one global scale; no per-pose fit'}
    return images, report


def baseline(metadata):
    original = copy.deepcopy(metadata)
    appended = [actor for actor in original['actors'] if actor['id'] == 'MOURNING_BELL']
    if appended:
        if len(appended) != 1 or original['actors'][-1]['id'] != 'MOURNING_BELL':
            raise ValueError('Mourning Bell must remain the final appended actor')
        indices = sorted({index for state in appended[0]['clips'].values()
                          for clip in state.values() for index in clip['frames']})
        start = indices[0]
        if indices != list(range(start, len(original['frames']))) or len(indices) != 8:
            raise ValueError('Mourning Bell needs an isolated eight-frame suffix')
        if any(frame['page'] != 4 for frame in original['frames'][start:]):
            raise ValueError('Existing Mourning Bell frames changed residency')
        original['actors'].pop()
        original['frames'] = original['frames'][:start]
        if original['pages'][-1] != {'file': PAGE_NAME, 'size': list(PAGE_SIZE)}:
            raise ValueError('Mourning Bell page changed dimensions')
        original['pages'].pop()
    if len(original['pages']) != 4 or len(original['actors']) != 8:
        raise ValueError('Expected the existing four-page, eight-actor gameplay bank')
    if any(frame['page'] > 3 for frame in original['frames']):
        raise ValueError('Unexpected baseline frame page')
    return original


def extrude(page, sprite, x, y):
    width, height = sprite.size
    for dy in range(-GUTTER, height + GUTTER):
        for dx in range(-GUTTER, width + GUTTER):
            if 0 <= dx < width and 0 <= dy < height:
                continue
            page.putpixel((x + dx, y + dy),
                          sprite.getpixel((max(0, min(width - 1, dx)), max(0, min(height - 1, dy)))))
    page.paste(sprite, (x, y))


def clip(frame, duration, loop):
    return {'frames': [frame], 'durationsMs': [duration], 'loop': loop}


def bake(source, runtime, output, check=False):
    data = source_bytes(source)
    images, normalization = normalized(data)
    original = baseline(json.loads((runtime / 'gameplay.json').read_text()))
    old_page_hashes = {page['file']: digest((runtime / page['file']).read_bytes())
                       for page in original['pages']}
    metadata = copy.deepcopy(original)
    metadata['pages'].append({'file': PAGE_NAME, 'size': list(PAGE_SIZE)})
    page = Image.new('RGBA', PAGE_SIZE)
    start = len(metadata['frames'])
    # A three-column grid preserves ~80px body detail and fixed two-pixel gutters.
    cell_size = PAGE_SIZE[0] // 3
    for index, full in enumerate(images):
        bounds = full.getchannel('A').getbbox()
        if bounds is None:
            raise ValueError('Normalized frame is empty')
        sprite = full.crop(bounds)
        if sprite.width + GUTTER * 2 > cell_size or sprite.height + GUTTER * 2 > cell_size:
            raise ValueError('Normalized sprite exceeds its fixed atlas cell')
        x = (index % 3) * cell_size + GUTTER
        y = (index // 3) * cell_size + GUTTER
        extrude(page, sprite, x, y)
        metadata['frames'].append({'page': 4, 'rect': [x, y, sprite.width, sprite.height],
                                   'source': list(CANVAS), 'offset': list(bounds[:2]), 'pivot': list(PIVOT)})
    clips = {state: {} for state in ('IDLE', 'MOVE', 'ATTACK', 'WINDUP')}
    for column, facing in enumerate(FACINGS):
        idle, attack = start + column, start + 4 + column
        clips['IDLE'][facing] = clip(idle, 1000, True)
        clips['MOVE'][facing] = clip(idle, 1000, True)
        clips['ATTACK'][facing] = clip(attack, 180, False)
        clips['WINDUP'][facing] = {'frames': [idle, attack], 'durationsMs': [100, 650], 'loop': True}
    metadata['actors'].append({'id': 'MOURNING_BELL', 'canvas': list(CANVAS),
                               'worldHeight': WORLD_HEIGHT, 'clips': clips})
    if metadata['frames'][:start] != original['frames'] or metadata['actors'][:-1] != original['actors']:
        raise ValueError('An existing frame or actor changed')
    if metadata['regions'] != original['regions'] or metadata['pages'][:4] != original['pages']:
        raise ValueError('An existing region or page changed')
    stream = io.BytesIO()
    page.save(stream, format='PNG', optimize=True)
    png, encoded = stream.getvalue(), encoded_json(metadata)
    if check:
        if (output / PAGE_NAME).read_bytes() != png or (output / 'gameplay.json').read_bytes() != encoded:
            raise ValueError('Runtime Mourning Bell export differs from the canonical source')
    else:
        output.mkdir(parents=True, exist_ok=True)
        (output / PAGE_NAME).write_bytes(png)
        (output / 'gameplay.json').write_bytes(encoded)
    for filename, before in old_page_hashes.items():
        if digest((runtime / filename).read_bytes()) != before:
            raise ValueError('Existing gameplay texture changed: ' + filename)
    saved = Image.open(io.BytesIO(png)).convert('RGBA')
    for index, full in enumerate(images):
        x, y, width, height = metadata['frames'][start + index]['rect']
        if saved.crop((x, y, x + width, y + height)).tobytes() != full.crop(full.getchannel('A').getbbox()).tobytes():
            raise ValueError('Saved frame reconstruction differs from normalized source')
    return {'sourceSha256': digest(data), 'page': PAGE_NAME, 'pageSize': list(PAGE_SIZE),
            'textureBytesAdded': PAGE_SIZE[0] * PAGE_SIZE[1] * 4, 'frames': 8,
            'oldFrameCount': start, 'oldGameplayPageHashes': old_page_hashes,
            'preservedOldFramesActorsRegions': True, 'gutter': GUTTER, 'edgeExtrusion': GUTTER,
            'normalization': normalization, 'checked': check}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=ROOT / 'art-source/mourning-bell')
    parser.add_argument('--base-art', type=Path, default=ROOT / 'assets/art')
    parser.add_argument('--output', type=Path, default=ROOT / 'assets/art')
    parser.add_argument('--report', type=Path)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    report = bake(args.source, args.base_art, args.output, args.check)
    if args.report:
        args.report.write_bytes(encoded_json(report))
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
