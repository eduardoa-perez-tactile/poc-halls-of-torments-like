#!/usr/bin/env python3
"""Normalize the approved Grave King sheet into one bounded, managed actor page.

Run after the other gameplay/floor stages. The original three page files, all
existing frame indices and every existing actor/region entry remain unchanged.
The source may be the tracked source directory, its canonical ZIP, or the PNG.
No image-service request is needed to reproduce the runtime export.
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

ROOT = Path(__file__).resolve().parents[1]
SOURCE_NAME = 'generated-facing-sheet.png'
PAGE_NAME = 'gameplay-grave-king.png'
PAGE_SIZE = (512, 256)
CANVAS = (128, 144)
PIVOT = (64, 144)
FACINGS = ('SW', 'SE', 'NE', 'NW')
GUTTER = 2
TARGET_HEIGHT = 120
TOP = 8


def digest(data):
    return hashlib.sha256(data).hexdigest()


def source_bytes(path):
    if path.is_dir():
        return (path / SOURCE_NAME).read_bytes()
    if path.suffix.lower() == '.zip':
        with zipfile.ZipFile(path) as archive:
            matches = [name for name in archive.namelist()
                       if Path(name).name == SOURCE_NAME]
            if len(matches) != 1:
                raise ValueError('Canonical archive needs exactly one ' + SOURCE_NAME)
            return archive.read(matches[0])
    return path.read_bytes()


def normalized(data):
    sheet = Image.open(io.BytesIO(data)).convert('RGBA')
    if sheet.size != (1774, 887):
        raise ValueError('The approved four-column/two-row source must be 1774x887')
    cells, bounds = [], []
    for row in range(2):
        cells.append([])
        bounds.append([])
        for column in range(4):
            box = (round(column * sheet.width / 4), round(row * sheet.height / 2),
                   round((column + 1) * sheet.width / 4), round((row + 1) * sheet.height / 2))
            cell = sheet.crop(box)
            cell.putalpha(cell.getchannel('A').point(lambda value: 0 if value <= 8 else value))
            visible = cell.getchannel('A').point(lambda value: 255 if value >= 16 else 0).getbbox()
            if visible is None:
                raise ValueError('Empty source pose')
            cells[row].append(cell)
            bounds[row].append(visible)
    shared = []
    for column in range(4):
        a, b = bounds[0][column], bounds[1][column]
        shared.append((max(0, min(a[0], b[0]) - 2), max(0, min(a[1], b[1]) - 2),
                       min(cells[0][column].width, max(a[2], b[2]) + 2),
                       min(cells[0][column].height, max(a[3], b[3]) + 2)))
    # A single global scale and one shared crop/placement for each facing keep
    # both poses' crown and cranium fixed while the authored jaw opens downward.
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
              'posePolicy': 'one shared crop and placement per facing, one global scale; no per-pose fit'}
    return images, report


def extrude(page, sprite, x, y):
    page.paste(sprite, (x, y))
    width, height = sprite.size
    page.paste(sprite.crop((0, 0, width, 1)), (x, y - 1))
    page.paste(sprite.crop((0, height - 1, width, height)), (x, y + height))
    page.paste(sprite.crop((0, 0, 1, height)), (x - 1, y))
    page.paste(sprite.crop((width - 1, 0, width, height)), (x + width, y))
    for dx, dy, sx, sy in [(-1, -1, 0, 0), (width, -1, width - 1, 0),
                           (-1, height, 0, height - 1), (width, height, width - 1, height - 1)]:
        page.putpixel((x + dx, y + dy), sprite.getpixel((sx, sy)))


def baseline(metadata):
    original = copy.deepcopy(metadata)
    appended = [actor for actor in original['actors'] if actor['id'] == 'GRAVE_KING']
    if appended:
        if len(appended) != 1 or original['actors'][-1]['id'] != 'GRAVE_KING':
            raise ValueError('Grave King must be the final appended actor')
        indices = sorted({index for state in appended[0]['clips'].values()
                          for clip in state.values() for index in clip['frames']})
        start = indices[0]
        if indices != list(range(start, len(original['frames']))) or len(indices) != 8:
            raise ValueError('Existing Grave King frames are not an isolated eight-frame suffix')
        if any(frame['page'] != 3 for frame in original['frames'][start:]):
            raise ValueError('Existing Grave King frames changed residency')
        original['actors'].pop()
        original['frames'] = original['frames'][:start]
        if original['pages'][-1] != {'file': PAGE_NAME, 'size': list(PAGE_SIZE)}:
            raise ValueError('Existing Grave King page changed dimensions')
        original['pages'].pop()
    if len(original['pages']) != 3 or len(original['actors']) != 7:
        raise ValueError('Expected the existing three-page, seven-actor gameplay bank')
    if any(frame['page'] > 2 for frame in original['frames']):
        raise ValueError('Unexpected baseline frame page')
    return original


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
    occupied = []
    for index, full in enumerate(images):
        bounds = full.getchannel('A').getbbox()
        if bounds is None:
            raise ValueError('Normalized frame is empty')
        sprite = full.crop(bounds)
        # Four cells across and two rows leave a transparent outer gutter and
        # preserve enough source detail for the approved large gameplay skull.
        cell_width, cell_height = PAGE_SIZE[0] // 4, PAGE_SIZE[1] // 2
        if sprite.width + GUTTER * 2 > cell_width or sprite.height + GUTTER * 2 > cell_height:
            raise ValueError('Normalized frame exceeds its fixed cell')
        x = (index % 4) * cell_width + GUTTER
        y = (index // 4) * cell_height + GUTTER
        extrude(page, sprite, x, y)
        occupied.append((x, y, sprite.width, sprite.height))
        metadata['frames'].append({'page': 3, 'rect': [x, y, sprite.width, sprite.height],
                                   'source': list(CANVAS), 'offset': list(bounds[:2]), 'pivot': list(PIVOT)})
    clips = {state: {} for state in ('IDLE', 'MOVE', 'ATTACK', 'WINDUP')}
    for column, facing in enumerate(FACINGS):
        idle, cast = start + column, start + 4 + column
        clips['IDLE'][facing] = clip(idle, 1000, True)
        clips['MOVE'][facing] = clip(idle, 1000, True)
        clips['ATTACK'][facing] = clip(cast, 180, False)
        clips['WINDUP'][facing] = {'frames': [idle, cast], 'durationsMs': [100, 650], 'loop': True}
    metadata['actors'].append({'id': 'GRAVE_KING', 'canvas': list(CANVAS), 'worldHeight': 96, 'clips': clips})
    if metadata['frames'][:start] != original['frames'] or metadata['actors'][:-1] != original['actors']:
        raise ValueError('An existing frame or actor changed')
    if metadata['regions'] != original['regions'] or metadata['pages'][:3] != original['pages']:
        raise ValueError('An existing region or page changed')
    out = io.BytesIO()
    page.save(out, format='PNG', optimize=True)
    png = out.getvalue()
    encoded = (json.dumps(metadata, indent=2) + '\n').encode()
    if check:
        if (output / PAGE_NAME).read_bytes() != png or (output / 'gameplay.json').read_bytes() != encoded:
            raise ValueError('Runtime Grave King export does not match the canonical input')
    else:
        output.mkdir(parents=True, exist_ok=True)
        (output / PAGE_NAME).write_bytes(png)
        (output / 'gameplay.json').write_bytes(encoded)
    for filename, before in old_page_hashes.items():
        if digest((runtime / filename).read_bytes()) != before:
            raise ValueError('Existing gameplay page changed: ' + filename)
    saved = Image.open(io.BytesIO(png)).convert('RGBA')
    for index, full in enumerate(images):
        frame = metadata['frames'][start + index]
        x, y, width, height = frame['rect']
        bounds = full.getchannel('A').getbbox()
        if saved.crop((x, y, x + width, y + height)).tobytes() != full.crop(bounds).tobytes():
            raise ValueError('Saved frame reconstruction differs from normalized source')
    return {'sourceSha256': digest(data), 'page': PAGE_NAME, 'pageSize': list(PAGE_SIZE),
            'textureBytesAdded': PAGE_SIZE[0] * PAGE_SIZE[1] * 4, 'frames': 8,
            'oldFrameCount': start, 'oldGameplayPageHashes': old_page_hashes,
            'preservedOldFramesActorsRegions': True, 'gutter': GUTTER, 'edgeExtrusion': 1,
            'normalization': normalization, 'checked': check}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=ROOT / 'art-source/grave-king')
    parser.add_argument('--base-art', type=Path, default=ROOT / 'assets/art')
    parser.add_argument('--output', type=Path, default=ROOT / 'assets/art')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    print(json.dumps(bake(args.source, args.base_art, args.output, args.check), indent=2))


if __name__ == '__main__':
    main()
