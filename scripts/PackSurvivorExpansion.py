#!/usr/bin/env python3
"""Append generated glyphs without changing any legacy skill-icon pixel."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'art-source/survivor-expansion'
RUNTIME = ROOT / 'assets/art/runtime-ui'


def normalized(cell):
    # Generated-sheet normalization only: crop its authored cell and preserve alpha.
    bounds = cell.getchannel('A').point(lambda value: 255 if value > 12 else 0).getbbox()
    if bounds is None:
        raise ValueError('Empty generated glyph')
    glyph = cell.crop(bounds)
    glyph.thumbnail((58, 58), Image.Resampling.LANCZOS)
    result = Image.new('RGBA', (64, 64))
    result.paste(glyph, ((64 - glyph.width) // 2, (64 - glyph.height) // 2))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=RUNTIME)
    parser.add_argument('--phone-output', type=Path, default=ROOT / 'assets/web/art')
    args = parser.parse_args()
    runtime = args.output
    runtime.mkdir(parents=True, exist_ok=True)
    args.phone_output.mkdir(parents=True, exist_ok=True)
    legacy = Image.open(SOURCE / 'legacy-skill-icons.png').convert('RGBA')
    assert legacy.size == (512, 384)
    atlas = Image.new('RGBA', (512, 512))
    atlas.paste(legacy, (0, 0))
    for filename, columns, rows, offset in [('skill-sheet-transparent.png', 4, 3, 0),
                                            ('world-glyph-sheet.png', 2, 2, 12)]:
        source = Image.open(SOURCE / filename).convert('RGBA')
        for i in range(columns * rows):
            x, y = i % columns, i // columns
            cell = source.crop((round(x * source.width / columns), round(y * source.height / rows),
                                round((x + 1) * source.width / columns), round((y + 1) * source.height / rows)))
            slot = offset + i
            atlas.paste(normalized(cell), (slot % 8 * 64, 384 + slot // 8 * 64))
    assert atlas.crop((0, 0, 512, 384)).tobytes() == legacy.tobytes()
    pixels = atlas.tobytes()
    atlas.save(runtime / 'skill-icons.png', optimize=True)
    atlas.save(args.phone_output / 'skill-icons.png', optimize=True)
    # Same uncompressed RGBA KTX 1 upload format as the existing managed UI bank.
    header = b'\xabKTX 11\xbb\r\n\x1a\n' + struct.pack('<13I', 0x04030201, 0x1401, 1,
        0x1908, 0x1908, 0x1908, 512, 512, 0, 0, 1, 1, 0)
    (runtime / 'skill-icons.ktx').write_bytes(header + struct.pack('<I', len(pixels)) + pixels)
    manifest_path = runtime / 'manifest.json'
    manifest = json.loads(manifest_path.read_text())
    rgba = hashlib.sha256(pixels).hexdigest()
    for name in ['skill-icons.png', 'skill-icons.ktx']:
        digest = hashlib.sha256((runtime / name).read_bytes()).hexdigest()
        info = {'width': 512, 'height': 512, 'rgbaSha256': rgba,
                'pngSha256' if name.endswith('.png') else 'fileSha256': digest}
        manifest['outputs'][name] = info
    manifest['outputs']['web/art/skill-icons.png']['pngSha256'] = manifest['outputs']['skill-icons.png']['pngSha256']
    manifest['expansionSource'] = 'art-source/survivor-expansion/README.md'
    manifest_path.write_text(json.dumps(manifest, separators=(',', ':'), sort_keys=True) + '\n')
    print('Packed 24 skill icons + 4 world glyphs in 512x512; preserved all legacy RGBA pixels.')


if __name__ == '__main__':
    main()
