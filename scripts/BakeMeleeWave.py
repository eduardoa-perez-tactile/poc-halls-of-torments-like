#!/usr/bin/env python3
"""Normalize the approved painted crescent and replace only the old sword bank.

The full-reach frame supplies every runtime pixel. Polar normalization measures
its ivory rim and fits the existing 110-degree attack cone; runtime expansion is
continuous and uses the simulation clock, rather than generated frame spacing.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace

from PIL import Image
from BakeGameplayAtlas import pack_page, padded_content
from BakeSpriteEffects import bake, encoded

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'art-source/warden-melee-wave'
SIZE, PIVOT, RADIUS = (160, 128), (72, 64), 72
SOURCE_CENTER = (329.22063980385076, 311.39679031027737)
SOURCE_RADIUS = 255.5904188982269
SOURCE_ANGLES = (-87, 141)
SOURCE_SHA256 = '35665d167e7798dd3f362b5bc3eab9c36650cc9f5cd57c945b8148d2aea7269c'
HEADING = math.pi / 4  # Image Y points down; the approved wave faces SE.
HALF_ARC = math.radians(55)


def sample(image, x, y):
    x0, y0 = math.floor(x), math.floor(y)
    if x0 < 0 or y0 < 0 or x0 + 1 >= image.width or y0 + 1 >= image.height:
        return (0, 0, 0, 0)
    fx, fy = x - x0, y - y0
    pixels = [image.getpixel((x0 + dx, y0 + dy)) for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1))]
    weights = ((1 - fx) * (1 - fy), fx * (1 - fy), (1 - fx) * fy, fx * fy)
    # Premultiplied interpolation prevents RGB hidden under alpha bleeding in.
    alpha = sum(p[3] * w for p, w in zip(pixels, weights))
    if alpha <= 2:
        return (0, 0, 0, 0)
    return tuple(round(sum(p[c] * p[3] * w for p, w in zip(pixels, weights)) / alpha)
                 for c in range(3)) + (round(alpha),)


def normalize():
    if hashlib.sha256((SOURCE / 'approved-isometric-sheet.png').read_bytes()).hexdigest() != SOURCE_SHA256:
        raise ValueError('Approved crescent source hash differs')
    sheet = Image.open(SOURCE / 'approved-isometric-sheet.png').convert('RGBA')
    # The approved generated sheet is 1267x1241, not an assumed power-of-two grid.
    master = sheet.crop((0, 620, 633, 1241))
    cx, cy = SOURCE_CENTER
    # Measure the outer ivory ridge independently along each ray. This removes
    # the generated rim's small radius drift while preserving its painted body.
    ridges = []
    for i in range(457):
        angle = math.radians(SOURCE_ANGLES[0] + i * .5)
        candidates = []
        for step in range(151):
            radius = SOURCE_RADIUS * (.65 + step * .004)
            p = sample(master, cx + math.cos(angle) * radius, cy + math.sin(angle) * radius * .5)
            brightness = min(p[:3]) * p[3] / 255
            candidates.append((brightness, radius))
        peak = max(v[0] for v in candidates)
        # Outer edge of the narrow white highlight, not transparent bloom.
        ridges.append(max(r for value, r in candidates if value >= peak * .985))
    output = Image.new('RGBA', SIZE)
    for y in range(SIZE[1]):
        for x in range(SIZE[0]):
            dx, dy = x + .5 - PIVOT[0], 2 * (y + .5 - PIVOT[1])
            relative = (math.atan2(dy, dx) - HEADING + math.pi) % (2 * math.pi) - math.pi
            radius = math.hypot(dx, dy) / RADIUS
            if abs(relative) > HALF_ARC or radius > 1.10 or radius < .25:
                continue
            fraction = (relative + HALF_ARC) / (2 * HALF_ARC)
            source_angle = math.radians(SOURCE_ANGLES[0] + fraction * (SOURCE_ANGLES[1] - SOURCE_ANGLES[0]))
            ridge = ridges[min(len(ridges) - 1, round(fraction * (len(ridges) - 1)))]
            pixel = sample(master, cx + math.cos(source_angle) * radius * ridge,
                           cy + math.sin(source_angle) * radius * ridge * .5)
            # Only a tiny edge feather; the open centre and the painted taper stay.
            edge = min(1, max(0, (HALF_ARC - abs(relative)) / math.radians(2)))
            output.putpixel((x, y), pixel[:3] + (round(pixel[3] * edge),))
    return output


def export(output, base_source=ROOT / 'art-source/sprite-effects-canonical.zip'):
    with TemporaryDirectory(prefix='couchcrypt-wave-') as temp:
        base = Path(temp)
        bake(base_source, base)
        metadata = json.loads((base / 'sprite-effects.json').read_text())
        page = Image.open(base / 'sprite-effects.png').convert('RGBA')
        old_sword = next(c for c in metadata['animations'] if c['name'] == 'SWORD_SWEEP')
        removed = set(old_sword['frames'])
        frames, images, remap = [], [], {}
        for i, descriptor in enumerate(metadata['frames']):
            if i in removed:
                continue
            x, y, w, h = descriptor['rect']
            remap[i] = len(frames)
            frames.append(dict(descriptor))
            images.append(page.crop((x, y, x + w, y + h)))
        wave = normalize()
        bounds = wave.getchannel('A').getbbox()
        wave_index = len(frames)
        frames.append({'source': list(SIZE), 'pivot': list(PIVOT), 'offset': list(bounds[:2])})
        images.append(wave.crop(bounds))
        packed = [SimpleNamespace(key=str(i), outer_size=(im.width + 4, im.height + 4))
                  for i, im in enumerate(images)]
        positions, method = pack_page(packed, (512, 512))
        result = Image.new('RGBA', (512, 512))
        for i, (descriptor, image) in enumerate(zip(frames, images)):
            x, y, _, _ = positions[str(i)]
            result.paste(padded_content(image), (x, y))
            descriptor['rect'] = [x + 2, y + 2, image.width, image.height]
        metadata['frames'] = frames
        for clip in metadata['animations']:
            if clip['name'] == 'SWORD_SWEEP':
                clip.update(frames=[wave_index], durationsMs=[380], groundRadius=RADIUS,
                            referenceScale=1, recommendedScale=1)
            else:
                clip['frames'] = [remap[i] for i in clip['frames']]
        output.mkdir(parents=True, exist_ok=True)
        result.save(output / 'sprite-effects.png')
        (output / 'sprite-effects.json').write_bytes(encoded(metadata))
        # Exact preservation of every unrelated painted frame, including gutters.
        saved = Image.open(output / 'sprite-effects.png').convert('RGBA')
        for descriptor, image in zip(frames, images):
            x, y, w, h = descriptor['rect']
            assert saved.crop((x - 2, y - 2, x + w + 2, y + h + 2)).tobytes() == padded_content(image).tobytes()
        return {'frames': len(frames), 'textureBytes': 512 * 512 * 4, 'retainedPixmapBytes': 0,
                'pack': method, 'waveRadius': RADIUS, 'waveSourceSize': SIZE,
                'sourceSha256': hashlib.sha256((SOURCE / 'approved-isometric-sheet.png').read_bytes()).hexdigest()}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'assets/art')
    args = parser.parse_args()
    print(json.dumps(export(args.output), indent=2))
