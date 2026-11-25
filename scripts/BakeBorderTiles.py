#!/usr/bin/env python3
"""Export integrated border tiles from one continuous original painting; never touches runtime assets."""
import argparse
import hashlib
import json
import math
from pathlib import Path
from PIL import Image, ImageFilter, ImageStat

ROOT = Path(__file__).resolve().parents[1]
IDS = {(0, 0): 'corner-n', (1, 0): 'edge-ne', (2, 0): 'corner-e',
       (0, 1): 'edge-nw', (1, 1): 'interior', (2, 1): 'edge-se',
       (0, 2): 'corner-w', (1, 2): 'edge-sw', (2, 2): 'corner-s'}
MASKS = {'interior': 0, 'edge-nw': 1, 'edge-ne': 2, 'edge-se': 4, 'edge-sw': 8,
         'corner-n': 3, 'corner-e': 6, 'corner-w': 9, 'corner-s': 12}


def smooth(value):
    value = max(0., min(1., value))
    return value * value * (3. - 2. * value)


def sample(image, x, y):
    """Bilinear sampling in image-center coordinates, outside pixels transparent."""
    x -= .5
    y -= .5
    ix, iy = math.floor(x), math.floor(y)
    fx, fy = x - ix, y - iy
    result = [0.] * 4
    for dx, dy, weight in ((0, 0, (1-fx)*(1-fy)), (1, 0, fx*(1-fy)),
                           (0, 1, (1-fx)*fy), (1, 1, fx*fy)):
        xx, yy = ix + dx, iy + dy
        if 0 <= xx < image.width and 0 <= yy < image.height:
            pixel = image.getpixel((xx, yy))
            for channel in range(4):
                result[channel] += pixel[channel] * weight
    return result


def periodic_coordinates(value, collar=.18):
    phase = value % 1.
    weight = 1. - smooth(min(phase, 1.-phase) / collar)
    # Transplant a continuous strip through the center of the original painting
    # to both sides of a repeat boundary, rather than mirroring the artwork.
    return ((1.+phase, 1.-weight), (1.+((phase+.5) % 1.), weight))


def normalize_repeat_edges(master, width, height):
    """Keep painted wall/footing intact; normalize only repeat textures/collars."""
    side = 192
    # Recover the unobstructed central floor in world-plane coordinates.
    floor = master.transform((side, side), Image.Transform.AFFINE,
        (width / 2 / side, -width / 2 / side, width * 1.5,
         height / 2 / side, height / 2 / side, height), Image.Resampling.BICUBIC)
    # Remove low-frequency lighting drift while retaining painted stone/grout.
    blurred = floor.filter(ImageFilter.GaussianBlur(18))
    means = ImageStat.Stat(floor).mean
    fp, bp = floor.load(), blurred.load()
    for y in range(side):
        for x in range(side):
            color, low = fp[x, y], bp[x, y]
            fp[x, y] = tuple(max(0, min(255, round(color[c] + .9*(means[c]-low[c]))))
                              for c in range(3)) + (255,)
    def floor_repeat(u, v):
        result = [0.] * 4
        for uu, wu in periodic_coordinates(u):
            for vv, wv in periodic_coordinates(v):
                color = sample(floor, (uu-1)*side, (vv-1)*side)
                for c in range(4):
                    result[c] += color[c] * wu * wv
        return result
    normalized = master.copy()
    np = normalized.load()
    for y in range(master.height):
        for x in range(master.width):
            u = (x+.5-master.width/2)/width + (y+.5)/height
            v = (y+.5)/height - (x+.5-master.width/2)/width
            distance = min(u, v, 3-u, 3-v)
            weight = smooth((distance-.32)/.34)
            if weight:
                original, target = master.getpixel((x, y)), floor_repeat(u, v)
                np[x, y] = tuple(round(original[c]*(1-weight)+target[c]*weight)
                                 for c in range(3)) + (original[3],)
    output = normalized.copy()
    op = output.load()
    def chart(u, v):
        return sample(normalized, master.width/2 + (u-v)*width/2, (u+v)*height/2)
    for y in range(master.height):
        for x in range(master.width):
            u = (x+.5-master.width/2)/width + (y+.5)/height
            v = (y+.5)/height - (x+.5-master.width/2)/width
            if not normalized.getpixel((x, y))[3]:
                continue
            original = list(normalized.getpixel((x, y)))
            for distance, along, axis in ((u, v, 0), (v, u, 1), (3-u, v, 0), (3-v, u, 1)):
                weight = (1.-smooth((distance-.55)/.4)) * smooth((min(along, 3-along)-.38)/.48)
                if not weight:
                    continue
                target = [0.] * 4
                for coordinate, fraction in periodic_coordinates(along):
                    color = chart(u, coordinate) if axis == 0 else chart(coordinate, v)
                    for c in range(4):
                        target[c] += color[c] * fraction
                # Do not invent exterior coverage from a sampled transparent pixel.
                if target[3] >= 240:
                    for c in range(3):
                        original[c] = original[c]*(1-weight)+target[c]*weight
            op[x, y] = tuple(round(c) for c in original)
    return output


def bake(folder):
    source_path = folder / 'painted-master.png'
    source = Image.open(source_path).convert('RGBA')
    if source.size != (1774, 887):
        raise ValueError('Measured source geometry requires the selected 1774x887 painting')
    # Measured outer coping corners in the selected original painting. The border
    # cells include the wall and its integrated floor footing, not loose wall sprites.
    geometry = {'far': [886, 23], 'left': [120, 418], 'near': [886, 813], 'right': [1654, 418]}
    width, height, canvas_height = 240, 120, 168
    pad_x, pad_y, canvas_width = 32, 2, 304
    master_width, master_height = width * 3, 400
    scale_x = (geometry['right'][0] - geometry['left'][0]) / master_width
    scale_y = (geometry['near'][1] - geometry['far'][1]) / (height * 3)
    master = source.transform((master_width, master_height), Image.Transform.AFFINE,
        (scale_x, 0, geometry['far'][0] - master_width / 2 * scale_x,
         0, scale_y, geometry['far'][1]), Image.Resampling.BICUBIC)
    master = normalize_repeat_edges(master, width, height)
    pixels = master.load()
    tiles = {key: Image.new('RGBA', (canvas_width, canvas_height)) for key in IDS}
    tile_pixels = {key: value.load() for key, value in tiles.items()}
    outside_count = 0
    bounds = {}
    for y in range(master_height):
        for x in range(master_width):
            u = (x + .5 - master_width / 2) / width + (y + .5) / height
            v = (y + .5) / height - (x + .5 - master_width / 2) / width
            i = max(0, min(2, math.floor(u)))
            j = max(0, min(2, math.floor(v)))
            rgba = pixels[x, y]
            in_floor = 0 <= u < 3 and 0 <= v < 3
            if in_floor:
                rgba = (*rgba[:3], 255)
            elif rgba[3] < 32:
                rgba = (0, 0, 0, 0)
            else:
                # The opaque painted foundation skirt is real source art. Remove
                # generation's slight global translucency, retaining hard silhouettes.
                rgba = (*rgba[:3], 255)
            lx = pad_x + x - int(master_width / 2 + (i - j) * width / 2 - width / 2)
            ly = pad_y + y - int((i + j) * height / 2)
            if rgba[3] and not (0 <= lx < canvas_width and 0 <= ly < canvas_height):
                outside_count += 1
                box = bounds.setdefault((i, j), [lx, ly, lx, ly])
                box[0], box[1] = min(box[0], lx), min(box[1], ly)
                box[2], box[3] = max(box[2], lx), max(box[3], ly)
            elif 0 <= lx < canvas_width and 0 <= ly < canvas_height:
                tile_pixels[(i, j)][lx, ly] = rgba
    if outside_count:
        raise ValueError(f'{outside_count} opaque source pixels exceed shared tile canvas: {bounds}')
    output = folder / 'tiles'
    output.mkdir(exist_ok=True)
    records = []
    for (i, j), image in tiles.items():
        tile_id = IDS[(i, j)]
        path = output / f'{tile_id}.png'
        image.save(path, optimize=True)
        records.append({'id': tile_id, 'file': f'tiles/{tile_id}.png', 'mask': MASKS[tile_id],
                        'sourceCell': [i, j], 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    manifest = {'version': 1, 'status': 'standalone-art-review-not-runtime-integrated',
        'tileWidth': width, 'tileHeight': height, 'canvasWidth': canvas_width,
        'canvasHeight': canvas_height, 'anchor': [pad_x + width // 2, pad_y], 'logicalWorldCell': 120,
        'projection': '2:1; x=(i-j)*tileWidth/2; y=(i+j)*tileHeight/2',
        'source': {'file': source_path.name, 'sha256': hashlib.sha256(source_path.read_bytes()).hexdigest(),
                   'measuredCopingCorners': geometry,
                   'normalization': 'shared affine camera, periodic texture collars, floor illumination normalization; no separate wall composition'},
        'tiles': records}
    (folder / 'tiles.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps({'tiles': len(records), 'output': str(output), 'opaquePixelsClipped': outside_count}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'art-source/border-tiles-v1')
    args = parser.parse_args()
    bake(args.output)
