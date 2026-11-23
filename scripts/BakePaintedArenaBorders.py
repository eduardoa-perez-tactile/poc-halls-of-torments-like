#!/usr/bin/env python3
"""Append continuous painted border strips to unused texels of the existing gameplay pages.

The canonical archive contains the unmodified baseline pages/metadata and whole
paintings. Each strip retains neighbouring pixels from its original painting;
adjacent core UV edges therefore see identical bilinear samples. No chunk is
resized and no existing frame or supplemental attack/projectile rectangle moves.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import io
import json
import math
from pathlib import Path
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / 'art-source/painted-arena-borders'
ARCHIVE = SOURCES / 'bake-inputs.zip'
PADDING = 2
CONTEXT = 1
TRIM_ALPHA_THRESHOLD = 32
RUNTIME_SOURCE_SCALE = .85
METADATA = ('gameplay.json', 'enemy-attacks.json', 'magic-projectiles.json')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def load_baseline(read):
    metadata = {name: json.loads(read('baseline/' + name)) for name in METADATA}
    specs = metadata['gameplay.json']['pages']
    if len(specs) != 3 or [p['size'] for p in specs] != [[1024, 1024], [512, 512], [512, 512]]:
        raise ValueError('The three bounded gameplay pages must retain their existing dimensions')
    pages = [Image.open(io.BytesIO(read('baseline/' + p['file']))).convert('RGBA') for p in specs]
    if any(list(image.size) != spec['size'] for image, spec in zip(pages, specs)):
        raise ValueError('Baseline page dimensions disagree with the retained metadata')
    rectangles = [set() for _ in pages]
    for frame in metadata['gameplay.json']['frames'] + metadata['enemy-attacks.json']['frames']:
        rectangles[frame['page']].add(tuple(frame['rect']))
    borrowed = metadata['magic-projectiles.json']['borrowedPage']
    for frame in metadata['magic-projectiles.json']['frames']:
        rectangles[borrowed].add(tuple(frame['rect']))
    return metadata, specs, pages, rectangles


def protect(pages, rectangles):
    """Known frame gutters and *any* other nonzero RGBA texel are unavailable."""
    masks, stats = [], []
    for image, rects in zip(pages, rectangles):
        w, h = image.size
        rows = [0] * h
        for x, y, rw, rh in rects:
            if min(x, y) < 0 or x + rw > w or y + rh > h:
                raise ValueError('Retained frame lies outside its existing page')
            left, right = max(0, x - PADDING), min(w, x + rw + PADDING)
            bits = ((1 << (right - left)) - 1) << left
            for yy in range(max(0, y - PADDING), min(h, y + rh + PADDING)):
                rows[yy] |= bits
        unknown = 0
        pixels = image.load()
        for y in range(h):
            for x in range(w):
                if pixels[x, y] != (0, 0, 0, 0):
                    bit = 1 << x
                    if not rows[y] & bit:
                        unknown += 1
                    rows[y] |= bit
        protected = sum(bin(row).count('1') for row in rows)
        stats.append({'size': [w, h], 'legacyRectangles': len(rects), 'protectedPixels': protected,
                      'unknownNonzeroPixelsProtected': unknown, 'availablePixels': w * h - protected})
        masks.append(rows)
    return masks, stats


def overlaps(a, b):
    return a[0] < b[0] + b[2] and b[0] < a[0] + a[2] and a[1] < b[1] + b[3] and b[1] < a[1] + a[3]


def contains(a, b):
    return a[0] <= b[0] and a[1] <= b[1] and a[0] + a[2] >= b[0] + b[2] and a[1] + a[3] >= b[1] + b[3]


def subtract(free, used):
    result = []
    ux, uy, uw, uh = used
    for x, y, w, h in free:
        if not overlaps((x, y, w, h), used):
            result.append((x, y, w, h))
            continue
        if ux > x:
            result.append((x, y, ux - x, h))
        if ux + uw < x + w:
            result.append((ux + uw, y, x + w - ux - uw, h))
        if uy > y:
            result.append((x, y, w, uy - y))
        if uy + uh < y + h:
            result.append((x, uy + uh, w, y + h - uy - uh))
    result = sorted(set(result))
    return [r for i, r in enumerate(result) if not any(i != j and contains(other, r) for j, other in enumerate(result))]


def free_rectangles(pages, rectangles, masks):
    result = []
    for image, rects, protected in zip(pages, rectangles, masks):
        free = [(0, 0, image.width, image.height)]
        known = [0] * image.height
        for x, y, w, h in sorted(rects, key=lambda r: (-r[2] * r[3], r)):
            left, top = max(0, x - PADDING), max(0, y - PADDING)
            right, bottom = min(image.width, x + w + PADDING), min(image.height, y + h + PADDING)
            free = subtract(free, (left, top, right - left, bottom - top))
            bits = ((1 << (right - left)) - 1) << left
            for yy in range(top, bottom):
                known[yy] |= bits
        for y, (reserved, old) in enumerate(zip(protected, known)):
            unknown = reserved & ~old
            while unknown:
                x = (unknown & -unknown).bit_length() - 1
                shifted = unknown >> x
                width = (shifted ^ (shifted + 1)).bit_length() - 1
                free = subtract(free, (x, y, width, 1))
                unknown &= ~(((1 << width) - 1) << x)
        result.append(free)
    return result


def pack(cells, original_free, order, heuristic):
    free = [rects[:] for rects in original_free]
    keys = {'height': lambda c: (-c['image'].height, -c['image'].width, c['key']),
            'area': lambda c: (-c['image'].width * c['image'].height, -c['image'].height, c['key']),
            'width': lambda c: (-c['image'].width, -c['image'].height, c['key'])}
    placements = {}
    for cell in sorted(cells, key=keys[order]):
        w, h = cell['image'].size
        outer_w, outer_h = w + PADDING * 2, h + PADDING * 2
        candidates = []
        for page, rects in enumerate(free):
            for x, y, fw, fh in rects:
                if outer_w > fw or outer_h > fh:
                    continue
                short, long = min(fw - outer_w, fh - outer_h), max(fw - outer_w, fh - outer_h)
                score = {'short-side': (short, long, y, x, page),
                         'area': (fw * fh - outer_w * outer_h, short, y, x, page),
                         'bottom-left': (y + outer_h, x, short, long, page)}[heuristic]
                candidates.append((score, page, x, y))
        if not candidates:
            return None
        _, page, x, y = min(candidates)
        free[page] = subtract(free[page], (x, y, outer_w, outer_h))
        placements[cell['key']] = (page, x + PADDING, y + PADDING, w, h)
    return placements


def slices(paintings, images, width, max_height=None):
    cells = []
    for name, entry in paintings.items():
        image = images[name]
        if list(image.size) != entry['sourceSize'] or not image.getchannel('A').getbbox():
            raise ValueError('Painting source dimensions/alpha changed: ' + name)
        for left in range(0, image.width, width):
            right = min(left + width, image.width)
            # Include the neighbour columns when bounding the drawn height too: a broken
            # skyline at a strip cut can otherwise lose its half-texel bilinear coverage.
            alpha = image.crop((max(0, left - CONTEXT), 0, min(image.width, right + CONTEXT), image.height)).getchannel('A')
            bounds = alpha.point(lambda value: 255 if value >= TRIM_ALPHA_THRESHOLD else 0).getbbox()
            if bounds is None:
                continue
            # Keep the full horizontal interval. Only transparent top/bottom rows are trimmed.
            top, bottom = max(0, bounds[1] - CONTEXT), min(image.height, bounds[3] + CONTEXT)
            step = max_height or bottom - top
            for cy in range(top, bottom, step):
                cb = min(bottom, cy + step)
                cl, cr = left, right
                if max_height:
                    local = image.crop((left, max(0, cy - CONTEXT), right, min(image.height, cb + CONTEXT))).getchannel('A')
                    horizontal = local.point(lambda value: 255 if value >= TRIM_ALPHA_THRESHOLD else 0).getbbox()
                    if horizontal is None:
                        continue
                    cl = max(left, left + horizontal[0] - CONTEXT)
                    cr = min(right, left + horizontal[2] + CONTEXT)
                sx, sy = max(0, cl - CONTEXT), max(0, cy - CONTEXT)
                ex, ey = min(image.width, cr + CONTEXT), min(image.height, cb + CONTEXT)
                cells.append({'key': name + '/' + str(left).zfill(5) + '/' + str(cy).zfill(5), 'painting': name,
                              'coreRect': [cl, cy, cr - cl, cb - cy],
                              'sourceRect': [sx, sy, ex - sx, ey - sy],
                              'image': image.crop((sx, sy, ex, ey))})
    return cells


def trim_report(images, cells):
    result = {}
    for name, image in images.items():
        coverage = [[] for _ in range(image.width)]
        cores = [cell['coreRect'] for cell in cells if cell['painting'] == name]
        for x, y, w, h in cores:
            for xx in range(x, x + w):
                coverage[xx].append((y, y + h))
        excluded, maximum = 0, 0
        pixels = image.load()
        for x, covered in enumerate(coverage):
            for y in range(image.height):
                alpha = pixels[x, y][3]
                if alpha and not any(top <= y < bottom for top, bottom in covered):
                    excluded += 1
                    maximum = max(maximum, alpha)
        if maximum >= TRIM_ALPHA_THRESHOLD:
            raise ValueError('Atlas trimming lost significant source alpha: ' + name)
        result[name] = {'excludedLowAlphaPixels': excluded, 'maximumExcludedAlpha': maximum,
                        'drawnBounds': [min(c[0] for c in cores), min(c[1] for c in cores),
                                        max(c[0] + c[2] for c in cores), max(c[1] + c[3] for c in cores)]}
    return result


def runtime_paintings(paintings, originals):
    """One explicit proportional whole-painting normalization, before any strip exists."""
    derived = copy.deepcopy(paintings)
    images, files = {}, {}
    for name, original in originals.items():
        entry = derived[name]
        if name == 'corner':
            images[name] = original
            continue
        width, height = round(original.width * RUNTIME_SOURCE_SCALE), round(original.height * RUNTIME_SOURCE_SCALE)
        sx, sy = width / original.width, height / original.height
        image = original.resize((width, height), Image.Resampling.LANCZOS)
        encoded = io.BytesIO()
        image.save(encoded, format='PNG', optimize=True)
        filename = 'wall-' + name + '-runtime.png'
        files[filename] = encoded.getvalue()
        images[name] = image
        old_fit = entry['groundFit']
        target = -.5 if name == 'x' else .5
        slope = old_fit['normalizedSlopeDown'] * sy / sx
        old_base = old_fit.get('frontBaseYAtPivot', old_fit.get('normalizedYAtPivotX'))
        if old_base is None or not math.isfinite(old_base):
            raise ValueError('A globally fitted front base is required for proportional normalization')
        new_base = old_base * sy
        scale = entry['scale'] / sx
        fit = copy.deepcopy(old_fit)
        fit['normalizedSlopeDown'] = slope
        fit['frontBaseYAtPivot'] = fit['normalizedYAtPivotX'] = new_base
        intercept = old_fit.get('normalizedInterceptDown', old_fit.get('normalizedIntercept'))
        if intercept is not None:
            fit['normalizedInterceptDown'] = fit['normalizedIntercept'] = intercept * sy
        fit['targetSlopeDown'] = target
        fit['frontGroundOffsetSource'] = .35355339 * entry['worldThickness'] / scale
        entry['originalSource'] = {'file': entry['file'], 'sourceSize': list(original.size),
                                   'sha256': entry['generation']['normalizedSha256'] if 'normalizedSha256' in entry.get('generation', {})
                                   else digest(original.tobytes())}
        entry['file'] = filename
        entry['sourceSize'] = [width, height]
        entry['pivot'] = [entry['pivot'][0] * sx, new_base - fit['frontGroundOffsetSource']]
        entry['scale'] = scale
        entry['projectedFootprintWidth'] *= sx
        entry['renderShearY'] = slope - target
        entry['groundFit'] = fit
        entry['alphaBounds'] = list(image.getchannel('A').getbbox())
        entry['bakeNormalization'] = {'requestedScale': RUNTIME_SOURCE_SCALE, 'sx': sx, 'sy': sy,
                                     'resample': 'one proportional whole-painting Lanczos before slicing',
                                     'sha256': digest(files[filename]), 'originalSourceSize': list(original.size),
                                     'originalNormalization': copy.deepcopy(entry.get('normalization'))}
    return derived, images, files


def extrude(page, image, x, y):
    """Atlas gutter lies *outside* the retained original-neighbour context."""
    w, h = image.size
    page.paste(image, (x, y))
    page.paste(image.crop((0, 0, w, 1)), (x, y - 1))
    page.paste(image.crop((0, h - 1, w, h)), (x, y + h))
    page.paste(image.crop((0, 0, 1, h)), (x - 1, y))
    page.paste(image.crop((w - 1, 0, w, h)), (x + w, y))
    for dx, dy, sx, sy in [(-1, -1, 0, 0), (w, -1, w - 1, 0), (-1, h, 0, h - 1), (w, h, w - 1, h - 1)]:
        page.putpixel((x + dx, y + dy), image.getpixel((sx, sy)))


def capture_inputs(archive, source_dir, runtime_dir):
    if archive.exists():
        raise ValueError('Canonical archive already exists; do not silently replace its baseline')
    if (runtime_dir / 'painted-borders.json').exists():
        raise ValueError('Capture must use the original baseline, never pages already extended by this baker')
    manifest_bytes = (source_dir / 'paintings.json').read_bytes()
    manifest = json.loads(manifest_bytes)
    entries = {'source/paintings.json': manifest_bytes}
    for name in METADATA:
        entries['baseline/' + name] = (runtime_dir / name).read_bytes()
    specs = json.loads(entries['baseline/gameplay.json'])['pages']
    for spec in specs:
        entries['baseline/' + spec['file']] = (runtime_dir / spec['file']).read_bytes()
    for name, painting in manifest['paintings'].items():
        filename = painting['file']
        if Path(filename).name != filename:
            raise ValueError('Painting inputs must be direct files, not arbitrary paths')
        entries['source/' + filename] = (source_dir / filename).read_bytes()
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


def bake(archive, output_dir, source_output=None):
    with zipfile.ZipFile(archive) as source:
        index = json.loads(source.read('SOURCES.json'))
        for name, expected in index.items():
            data = source.read(name)
            if len(data) != expected['bytes'] or digest(data) != expected['sha256']:
                raise ValueError('Canonical input hash changed: ' + name)
        old_metadata, specs, baseline, rectangles = load_baseline(source.read)
        manifest = json.loads(source.read('source/paintings.json'))
        if manifest.get('version') != 1 or set(manifest['paintings']) != {'x', 'y', 'corner'}:
            raise ValueError('One full X side, one full Y side and one corner painting are required')
        paintings = manifest['paintings']
        images = {name: Image.open(io.BytesIO(source.read('source/' + entry['file']))).convert('RGBA')
                  for name, entry in paintings.items()}
        original_hashes = {name: digest(source.read('source/' + entry['file'])) for name, entry in paintings.items()}
        corner_name = paintings['corner']['file']
        corner_bytes = source.read('source/' + corner_name)
    paintings, images, derived_files = runtime_paintings(paintings, images)
    for name in ('x', 'y'):
        paintings[name]['originalSource']['sha256'] = original_hashes[name]
    for name in METADATA:
        existing = output_dir / name
        if existing.exists() and digest(existing.read_bytes()) != index['baseline/' + name]['sha256']:
            raise ValueError('Existing gameplay/combat metadata changed outside this append-only stage: ' + name)
    masks, capacity = protect(baseline, rectangles)
    free = free_rectangles(baseline, rectangles, masks)
    selected = None
    for max_height in (None, 48, 32, 24):
        for width in (64, 48, 40, 32):
            cells = slices(paintings, images, width, max_height)
            if len(cells) > 256 or sum(4 if cell['painting'] == 'corner' else 2 for cell in cells) > 512:
                continue
            for order in ('height', 'area', 'width'):
                for heuristic in ('short-side', 'area', 'bottom-left'):
                    placement = pack(cells, free, order, heuristic)
                    if placement is not None:
                        selected = cells, placement, width, max_height, order + '/' + heuristic
                        break
                if selected is not None:
                    break
            if selected is not None:
                break
        if selected is not None:
            break
    if selected is None:
        raise ValueError('Continuous paintings do not fit genuinely empty texels of the existing three pages')
    cells, placement, width, max_height, order = selected
    trimmed = trim_report(images, cells)
    pages = [page.copy() for page in baseline]
    metadata = {'version': 1, 'addedTextureBytes': 0, 'pages': copy.deepcopy(specs), 'context': CONTEXT,
                'padding': PADDING, 'edgeExtrusion': 1, 'sourceArchiveSha256': digest(archive.read_bytes()),
                'trimAlphaThreshold': TRIM_ALPHA_THRESHOLD,
                'paintings': copy.deepcopy(paintings)}
    for name, entry in metadata['paintings'].items():
        entry['pieces'] = []
        entry['trim'] = trimmed[name]
    for cell in cells:
        page, x, y, w, h = placement[cell['key']]
        extrude(pages[page], cell['image'], x, y)
        metadata['paintings'][cell['painting']]['pieces'].append({
            'page': page, 'rect': [x, y, w, h], 'sourceRect': cell['sourceRect'], 'coreRect': cell['coreRect']})
    # Check every previously protected texel, including unknown pixels and transparent frame gutters.
    for page, (before, after, rows) in enumerate(zip(baseline, pages, masks)):
        old, new = before.load(), after.load()
        for y, row in enumerate(rows):
            while row:
                bit = row & -row
                x = bit.bit_length() - 1
                if old[x, y] != new[x, y]:
                    raise ValueError('An existing pixel/gutter changed on page ' + str(page))
                row ^= bit
    output_dir.mkdir(parents=True, exist_ok=True)
    source_output = source_output or output_dir / 'painted-border-sources'
    source_output.mkdir(parents=True, exist_ok=True)
    for filename, data in derived_files.items():
        (source_output / filename).write_bytes(data)
    corner_output = source_output / corner_name
    if corner_output.exists():
        if corner_output.read_bytes() != corner_bytes:
            raise ValueError('An original corner source changed outside the canonical archive')
    else:
        corner_output.write_bytes(corner_bytes)
    for page, spec in zip(pages, specs):
        page.save(output_dir / spec['file'], optimize=True)
    # Saved reconstruction ensures packed contextual strips retain original RGBA, without chunk resampling.
    saved = [Image.open(output_dir / spec['file']).convert('RGBA') for spec in specs]
    for cell in cells:
        page, x, y, w, h = placement[cell['key']]
        if saved[page].crop((x, y, x + w, y + h)).tobytes() != cell['image'].tobytes():
            raise ValueError('Saved contextual strip differs from the original continuous painting')
    metadata_bytes = (json.dumps(metadata, indent=2) + '\n').encode()
    (output_dir / 'painted-borders.json').write_bytes(metadata_bytes)
    return {'version': 1, 'sourceArchive': archive.name, 'sourceArchiveSha256': digest(archive.read_bytes()),
            'addedTextureBytes': 0, 'newTexturePages': 0, 'stripCoreWidth': width, 'packingOrder': order,
            'maxCoreHeight': max_height,
            'runtimeSourceScale': RUNTIME_SOURCE_SCALE,
            'derivedSourceSha256': {name: digest(data) for name, data in derived_files.items()},
            'strips': len(cells), 'runtimePlacedQuads': sum(4 if cell['painting'] == 'corner' else 2 for cell in cells),
            'originalNeighbourContext': CONTEXT, 'existingPixelsAndGuttersPreserved': True,
            'trimAlphaThreshold': TRIM_ALPHA_THRESHOLD, 'paintingTrim': trimmed,
            'sourceStripRgbaPreserved': True, 'pageCapacityBeforeAppend': capacity,
            'paintedPixelsWithPadding': sum((c['image'].width + PADDING * 2) * (c['image'].height + PADDING * 2) for c in cells),
            'baselineMetadataSha256': {name: index['baseline/' + name]['sha256'] for name in METADATA},
            'outputs': {spec['file']: digest((output_dir / spec['file']).read_bytes()) for spec in specs}
            | {'painted-borders.json': digest(metadata_bytes)}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, default=ARCHIVE)
    parser.add_argument('--output', type=Path, default=ROOT / 'assets/art')
    parser.add_argument('--source-dir', type=Path, default=SOURCES)
    parser.add_argument('--source-output', type=Path, help='Derived proof PNG directory; defaults inside --output, never the tracked source directory')
    parser.add_argument('--report', type=Path)
    parser.add_argument('--capture-inputs', action='store_true')
    parser.add_argument('--capacity', action='store_true')
    args = parser.parse_args()
    if args.capacity:
        _, specs, pages, rects = load_baseline(lambda name: (args.output / name.removeprefix('baseline/')).read_bytes())
        _, capacity = protect(pages, rects)
        result = {'pages': [{**spec, **stats} for spec, stats in zip(specs, capacity)],
                  'availablePixels': sum(p['availablePixels'] for p in capacity)}
    elif args.capture_inputs:
        result = capture_inputs(args.archive, args.source_dir, args.output)
    else:
        result = bake(args.archive, args.output, args.source_output)
    rendered = json.dumps(result, indent=2) + '\n'
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(rendered)
    print(rendered, end='')


if __name__ == '__main__':
    main()
