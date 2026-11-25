#!/usr/bin/env python3
"""Pack the approved north-wall gate into protected spare pixels of resident gameplay pages.

Capture the current baseline once, after final gate.json/source PNG/pivot approval.
Every prior frame, original-neighbour corner collar, two-pixel atlas gutter and
nonzero RGBA pixel is protected. Explicit --reclaim-legacy-gate may reuse only the
now-unused sealed-gate frame 219 and its exclusive guarded rectangle; the archive
retains its exact old pixels and metadata. The archive supports exact rebuilding.
No image is resized here, no existing metadata changes and no texture grows.
"""
from __future__ import annotations

import argparse
import copy
import io
import json
from pathlib import Path
import zipfile
from PIL import Image
import BakeCornerJoins as common

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / 'art-source/epic-crypt-gate'
METADATA = common.METADATA + ('painted-corners.json',)
PADDING, CONTEXT, MAX_PIECES = 2, 1, 128
digest, json_bytes = common.digest, common.json_bytes


def load_baseline(read):
    documents, specs, pages, rectangles = common.load_baseline(read)
    corners = json.loads(read('baseline/painted-corners.json'))
    documents['painted-corners.json'] = corners
    runtime_pages = {page['file']: index for index, page in enumerate(specs)}
    for corner in corners['corners'].values():
        for piece in corner['pieces']:
            filename = corners['pages'][piece['page']]['file']
            rectangles[runtime_pages[filename]].add(tuple(piece['rect']))
    return documents, specs, pages, rectangles


def legacy_gate_policy(documents, pages, rectangles):
    gameplay = documents['gameplay.json']
    if gameplay['regions']['sealed-gate']['frame'] != 219:
        raise ValueError('The authorized reclaim is limited to original sealed-gate frame 219')
    frame = gameplay['frames'][219]
    page = frame['page']; x, y, w, h = frame['rect']
    if page != 2 or frame['rect'] != [2, 2, 124, 130]:
        raise ValueError('Authorized legacy gate storage geometry changed')
    if sum(f['page'] == page and f['rect'] == frame['rect'] for f in gameplay['frames']) != 1:
        raise ValueError('Legacy gate pixels are shared with another gameplay frame')
    guarded = (x - PADDING, y - PADDING, w + PADDING * 2, h + PADDING * 2)
    for rect in rectangles[page]:
        if rect == tuple(frame['rect']):
            continue
        rx, ry, rw, rh = rect
        other = (max(0, rx - PADDING), max(0, ry - PADDING),
                 min(pages[page].width, rx + rw + PADDING) - max(0, rx - PADDING),
                 min(pages[page].height, ry + rh + PADDING) - max(0, ry - PADDING))
        if common.packing.overlaps(guarded, other):
            raise ValueError('Authorized legacy gate gutter overlaps another required rectangle')
    return {'explicitOptIn': '--reclaim-legacy-gate', 'frame': 219, 'page': page,
            'rect': frame['rect'], 'guardedRect': list(guarded),
            'reason': 'Original sealed-gate renderer replaced by the approved separate painted gate',
            'originalPixelsAndMetadataRetainedInArchive': True}


def protection_baseline(documents, baseline, rectangles, reclaim):
    pages = [page.copy() for page in baseline]
    policy = None
    if reclaim:
        policy = legacy_gate_policy(documents, pages, rectangles)
        page = policy['page']; x, y, w, h = policy['guardedRect']
        rectangles[page].remove(tuple(policy['rect']))
        pages[page].paste((0, 0, 0, 0), (x, y, x + w, y + h))
    return pages, policy


def validate_manifest(manifest):
    if manifest.get('version') != 1 or not isinstance(manifest.get('gate'), dict):
        raise ValueError('One approved north-wall gate is required')
    gate = manifest['gate']
    common.valid_source_name(gate['file'])
    size, pivot, scale = gate['sourceSize'], gate['pivot'], gate['worldScale']
    if len(size) != 2 or len(pivot) != 2 or not 0 < scale <= 8:
        raise ValueError('Invalid gate canvas/pivot/scale')
    if any(not isinstance(side, int) or side < 1 or side > 512 for side in size):
        raise ValueError('Gate source canvas must remain bounded')
    if any(not 0 <= point <= side for point, side in zip(pivot, size)):
        raise ValueError('Gate ground pivot must stay on its original canvas')


def capture_inputs(archive, source_dir, runtime, reclaim=False):
    if archive.exists() or (runtime / 'painted-gate.json').exists():
        raise ValueError('Capture the pre-gate baseline exactly once; preserve canonical archives')
    data = (source_dir / 'gate.json').read_bytes()
    manifest = json.loads(data)
    validate_manifest(manifest)
    entries = {'source/gate.json': data}
    for name in METADATA:
        entries['baseline/' + name] = (runtime / name).read_bytes()
    for spec in json.loads(entries['baseline/gameplay.json'])['pages']:
        entries['baseline/' + spec['file']] = (runtime / spec['file']).read_bytes()
    names = {manifest['gate']['file']} | set(manifest.get('provenanceFiles', []))
    for name in sorted(names):
        common.valid_source_name(name)
        entries['source/' + name] = (source_dir / name).read_bytes()
    if reclaim:
        documents, _, pages, rectangles = load_baseline(lambda name: entries[name])
        entries['RECLAIM.json'] = json_bytes(legacy_gate_policy(documents, pages, rectangles))
    index = {name: {'bytes': len(value), 'sha256': digest(value)} for name, value in sorted(entries.items())}
    entries['SOURCES.json'] = json_bytes(index)
    archive.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        for name, value in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            target.writestr(info, value)
    return {'archive': archive.name, 'sha256': digest(archive.read_bytes()), 'entries': index}


def storage_cells(image, width, height):
    cells = []
    for top in range(0, image.height, height):
        for left in range(0, image.width, width):
            right, bottom = min(image.width, left + width), min(image.height, top + height)
            bounds = common.rgba_bounds(image.crop((left, top, right, bottom)))
            if bounds is None:
                continue
            cl, ct, cr, cb = left + bounds[0], top + bounds[1], left + bounds[2], top + bounds[3]
            sx, sy = max(0, cl - CONTEXT), max(0, ct - CONTEXT)
            ex, ey = min(image.width, cr + CONTEXT), min(image.height, cb + CONTEXT)
            cells.append({'key': str(top).zfill(4) + '/' + str(left).zfill(4),
                          'coreRect': [cl, ct, cr - cl, cb - ct],
                          'sourceRect': [sx, sy, ex - sx, ey - sy],
                          'image': image.crop((sx, sy, ex, ey))})
    return cells


def densest_core(image, width, height):
    """Keep the main opaque arch in one efficient block, rather than padding every thin strip."""
    prefix = [[0] * (image.width + 1) for _ in range(image.height + 1)]
    pixels = image.load()
    for y in range(image.height):
        running = 0
        for x in range(image.width):
            running += pixels[x, y] != (0, 0, 0, 0)
            prefix[y + 1][x + 1] = prefix[y][x + 1] + running
    best = None
    for y in range(image.height - height + 1):
        for x in range(image.width - width + 1):
            count = prefix[y + height][x + width] - prefix[y][x + width] - prefix[y + height][x] + prefix[y][x]
            candidate = (-count, y, x)
            if best is None or candidate < best:
                best = candidate
    return best[2], best[1], width, height


def body_and_wing_cells(image, core, width, height):
    cells = []

    def append(left, top, right, bottom, key):
        bounds = common.rgba_bounds(image.crop((left, top, right, bottom)))
        if bounds is None:
            return
        cl, ct, cr, cb = left + bounds[0], top + bounds[1], left + bounds[2], top + bounds[3]
        sx, sy = max(0, cl - CONTEXT), max(0, ct - CONTEXT)
        ex, ey = min(image.width, cr + CONTEXT), min(image.height, cb + CONTEXT)
        cells.append({'key': key, 'coreRect': [cl, ct, cr - cl, cb - ct],
                      'sourceRect': [sx, sy, ex - sx, ey - sy],
                      'image': image.crop((sx, sy, ex, ey))})

    x, y, w, h = core
    append(x, y, x + w, y + h, 'body')
    regions = [(0, 0, image.width, y), (0, y + h, image.width, image.height),
               (0, y, x, y + h), (x + w, y, image.width, y + h)]
    for region, (left, top, right, bottom) in enumerate(regions):
        for row in range(top, bottom, height):
            for column in range(left, right, width):
                append(column, row, min(right, column + width), min(bottom, row + height),
                       'wing/' + str(region) + '/' + str(row).zfill(4) + '/' + str(column).zfill(4))
    return cells


def adaptive_pack(image, cells, original_free, order, heuristic):
    """Split only unplaceable source cores; narrow leftover gaps need not force every piece to be narrow."""
    free = [rects[:] for rects in original_free]
    queue = cells[:]
    placed, placements = [], {}
    keys = {'area': lambda c: (-c['image'].width * c['image'].height, -c['image'].height, c['key']),
            'height': lambda c: (-c['image'].height, -c['image'].width, c['key']),
            'width': lambda c: (-c['image'].width, -c['image'].height, c['key'])}
    while queue:
        queue.sort(key=keys[order]); cell = queue.pop(0)
        w, h = cell['image'].size; outer_w, outer_h = w + 4, h + 4
        candidates = []
        for page, rects in enumerate(free):
            for x, y, fw, fh in rects:
                if outer_w <= fw and outer_h <= fh:
                    short, long = min(fw - outer_w, fh - outer_h), max(fw - outer_w, fh - outer_h)
                    score = {'short-side': (short, long, y, x, page),
                             'area': (fw * fh - outer_w * outer_h, short, y, x, page),
                             'bottom-left': (y + outer_h, x, short, long, page)}[heuristic]
                    candidates.append((score, page, x, y))
        if candidates:
            _, page, x, y = min(candidates)
            free[page] = common.packing.subtract(free[page], (x, y, outer_w, outer_h))
            placements[cell['key']] = (page, x + 2, y + 2, w, h)
            placed.append(cell)
            continue
        cx, cy, cw, ch = cell['coreRect']
        choices = set()
        for rects in free:
            for _, _, fw, fh in rects:
                sw, sh = min(cw, fw - 6), min(ch, fh - 6)
                if sw > 0 and sh > 0 and (sw < cw or sh < ch):
                    choices.add((sw, sh))
        split = None
        for sw, sh in sorted(choices, key=lambda size: (-size[0] * size[1], -size[1], -size[0])):
            pieces = []
            for top in range(cy, cy + ch, sh):
                for left in range(cx, cx + cw, sw):
                    right, bottom = min(cx + cw, left + sw), min(cy + ch, top + sh)
                    bounds = common.rgba_bounds(image.crop((left, top, right, bottom)))
                    if bounds is None:
                        continue
                    cl, ct, cr, cb = left + bounds[0], top + bounds[1], left + bounds[2], top + bounds[3]
                    sx, sy = max(0, cl - 1), max(0, ct - 1)
                    ex, ey = min(image.width, cr + 1), min(image.height, cb + 1)
                    pieces.append({'key': cell['key'] + '/split/' + str(top) + '/' + str(left),
                                   'coreRect': [cl, ct, cr - cl, cb - ct],
                                   'sourceRect': [sx, sy, ex - sx, ey - sy],
                                   'image': image.crop((sx, sy, ex, ey))})
            if len(placed) + len(queue) + len(pieces) <= MAX_PIECES:
                split = pieces
                break
        if split is None:
            return None
        queue.extend(split)
    return placed, placements


def select_pack(image, free):
    for width, height in ((96, 96), (64, 64), (48, 48), (32, 48), (24, 48),
                          (18, 48), (16, 48), (14, 48), (14, 32), (12, 48),
                          (12, 32), (12, 24), (10, 32), (8, 32), (8, 24)):
        cells = storage_cells(image, width, height)
        if len(cells) > MAX_PIECES:
            continue
        for order in ('area', 'height', 'width'):
            for heuristic in ('short-side', 'area', 'bottom-left'):
                placements = common.packing.pack(cells, free, order, heuristic)
                if placements is not None:
                    return cells, placements, [width, height], order + '/' + heuristic
    # The explicitly reclaimed legacy slot is large; nearly all other gaps are narrow.
    # A single dense source core uses that slot efficiently while wings retain small storage cuts.
    for body_width, body_height in ((122, 128), (120, 124), (116, 124), (112, 128)):
        if image.width < body_width or image.height < body_height:
            continue
        if not any(w >= body_width + 6 and h >= body_height + 6 for rects in free for _, _, w, h in rects):
            continue
        body = densest_core(image, body_width, body_height)
        for width, height in ((14, 48), (12, 48), (10, 48), (10, 32), (8, 48), (8, 32)):
            cells = body_and_wing_cells(image, body, width, height)
            if len(cells) > MAX_PIECES:
                continue
            for order in ('area', 'height', 'width'):
                for heuristic in ('short-side', 'area', 'bottom-left'):
                    placements = common.packing.pack(cells, free, order, heuristic)
                    if placements is not None:
                        return cells, placements, {'body': list(body), 'wings': [width, height]}, order + '/' + heuristic
    for body_width, body_height in ((122, 128), (120, 124), (116, 124), (112, 128)):
        if image.width < body_width or image.height < body_height:
            continue
        if not any(w >= body_width + 6 and h >= body_height + 6 for rects in free for _, _, w, h in rects):
            continue
        body = densest_core(image, body_width, body_height)
        for width, height in ((14, 32), (12, 24), (10, 24), (14, 48)):
            cells = body_and_wing_cells(image, body, width, height)
            for order in ('area', 'height', 'width'):
                for heuristic in ('short-side', 'area', 'bottom-left'):
                    result = adaptive_pack(image, cells, free, order, heuristic)
                    if result is not None:
                        actual_cells, placements = result
                        return actual_cells, placements, {'body': list(body), 'initialWings': [width, height],
                                                          'adaptiveStorageCuts': True}, order + '/' + heuristic
    raise ValueError('Approved gate does not fit protected resident spare pixels at its original resolution')


def bake(archive, output, reclaim=False):
    with zipfile.ZipFile(archive) as source:
        index = json.loads(source.read('SOURCES.json'))
        for name, expected in index.items():
            data = source.read(name)
            if len(data) != expected['bytes'] or digest(data) != expected['sha256']:
                raise ValueError('Canonical input changed: ' + name)
        documents, specs, baseline, rectangles = load_baseline(source.read)
        captured_reclaim = 'RECLAIM.json' in index
        if captured_reclaim != reclaim:
            raise ValueError('Use --reclaim-legacy-gate exactly when the canonical archive records that explicit authorization')
        manifest = json.loads(source.read('source/gate.json'))
        validate_manifest(manifest)
        image = Image.open(io.BytesIO(source.read('source/' + manifest['gate']['file']))).convert('RGBA')
        if list(image.size) != manifest['gate']['sourceSize'] or common.rgba_bounds(image) is None:
            raise ValueError('Gate source canvas/paint changed')
    working, policy = protection_baseline(documents, baseline, rectangles, reclaim)
    masks, capacity = common.packing.protect(working, rectangles)
    free = common.packing.free_rectangles(working, rectangles, masks)
    cells, placements, grid, strategy = select_pack(image, free)
    pages = [page.copy() for page in working]
    metadata = {'version': 1, 'addedTextureBytes': 0, 'pages': copy.deepcopy(specs),
                'context': CONTEXT, 'padding': PADDING, 'edgeExtrusion': PADDING,
                'sourceArchiveSha256': digest(archive.read_bytes()), 'gate': copy.deepcopy(manifest['gate'])}
    if policy:
        metadata['reclaimedLegacyGate'] = policy
    metadata['gate']['pieces'] = []
    for cell in cells:
        page, x, y, w, h = placements[cell['key']]
        common.extrude(pages[page], cell['image'], x, y)
        metadata['gate']['pieces'].append({'page': page, 'rect': [x, y, w, h],
                                          'sourceRect': cell['sourceRect'], 'coreRect': cell['coreRect']})
    protected = [common.protection_hash(page, mask) for page, mask in zip(baseline, masks)]
    for expected, page, mask in zip(protected, pages, masks):
        if common.protection_hash(page, mask) != expected:
            raise ValueError('An old gameplay/corner pixel, sampling rectangle or gutter changed')
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
    for spec, data in zip(specs, encoded_pages):
        (output / spec['file']).write_bytes(data)
    metadata_bytes = json_bytes(metadata)
    (output / 'painted-gate.json').write_bytes(metadata_bytes)
    saved = [Image.open(output / spec['file']).convert('RGBA') for spec in specs]
    rebuilt = Image.new('RGBA', image.size)
    for cell in cells:
        page, x, y, w, h = placements[cell['key']]
        stored = saved[page].crop((x, y, x + w, y + h))
        if stored.tobytes() != cell['image'].tobytes():
            raise ValueError('Saved gate collar differs from its original painting')
        cx, cy, cw, ch = cell['coreRect']; sx, sy, _, _ = cell['sourceRect']
        rebuilt.paste(stored.crop((cx - sx, cy - sy, cx - sx + cw, cy - sy + ch)), (cx, cy))
    if rebuilt.tobytes() != image.tobytes():
        raise ValueError('Gate storage cuts failed exact whole-source RGBA reconstruction')
    for expected, page, mask in zip(protected, saved, masks):
        if common.protection_hash(page, mask) != expected:
            raise ValueError('Saved protected baseline/corner pixels differ')
    return {'version': 1, 'sourceArchiveSha256': digest(archive.read_bytes()), 'addedTextureBytes': 0,
            'pieceCount': len(cells), 'maxPieces': MAX_PIECES, 'coreGrid': grid, 'packing': strategy,
            'context': CONTEXT, 'padding': PADDING, 'edgeExtrusion': PADDING,
            'pageCapacityBeforeAppend': capacity, 'baselineProtectedPixelSha256': protected,
            'baselineMetadataSha256': {name: index['baseline/' + name]['sha256'] for name in METADATA},
            'sourceRgbaSha256': digest(image.tobytes()), 'sourceRgbaPreserved': True,
            'protectedPixelsAndGuttersPreserved': True, 'reclaimedLegacyGate': policy,
            'onlyAuthorizedLegacyGateMayDiffer': bool(policy),
            'outputs': {spec['file']: digest(data) for spec, data in zip(specs, encoded_pages)}
                       | {'painted-gate.json': digest(metadata_bytes)}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, default=SOURCES / 'bake-inputs.zip')
    parser.add_argument('--source-dir', type=Path, default=SOURCES)
    parser.add_argument('--output', type=Path, default=ROOT / 'assets/art')
    parser.add_argument('--report', type=Path)
    parser.add_argument('--capture-inputs', action='store_true')
    parser.add_argument('--capacity', action='store_true')
    parser.add_argument('--reclaim-legacy-gate', action='store_true',
                        help='Explicitly reuse only unused original gate frame 219 and its exclusive 2px gutter; archive originals')
    args = parser.parse_args()
    if args.capacity:
        documents, specs, baseline, rectangles = load_baseline(lambda name: (args.output / name.removeprefix('baseline/')).read_bytes())
        pages, policy = protection_baseline(documents, baseline, rectangles, args.reclaim_legacy_gate)
        masks, capacity = common.packing.protect(pages, rectangles)
        free = common.packing.free_rectangles(pages, rectangles, masks)
        print(json.dumps({'pages': specs, 'capacity': capacity, 'reclaimedLegacyGate': policy,
                          'largestFreeRectangles': [sorted(rects, key=lambda r: -r[2] * r[3])[:8] for rects in free]}, indent=2))
        return
    if args.capture_inputs:
        print(json.dumps(capture_inputs(args.archive, args.source_dir, args.output, args.reclaim_legacy_gate), indent=2))
        return
    report = bake(args.archive, args.output, args.reclaim_legacy_gate)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_bytes(json_bytes(report))
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
