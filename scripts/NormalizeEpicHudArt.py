#!/usr/bin/env python3
"""Normalize original generated alpha frames; only crop, scale and place offline."""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'art-source/epic-combat-hud'


def main():
    with Image.open(SOURCE / 'boss-frame-source.png') as source:
        frame = source.convert('RGBA').crop((36, 465, 1448, 589))
        canvas = Image.new('RGBA', (512, 96))
        # The destination restores the approved broad, shallow screen proportions.
        canvas.alpha_composite(frame.resize((504, 80), Image.Resampling.LANCZOS), (4, 8))
        canvas.save(SOURCE / 'boss-frame-512x96.png', optimize=True)
    with Image.open(SOURCE / 'orb-frames-source.png') as source:
        # Equal square crops preserve circular openings, with their glass centers
        # registered to (64,64). No masking, recoloring or alpha removal is applied.
        for name, cx in [('gold', 454), ('teal', 1318)]:
            frame = source.convert('RGBA').crop((cx - 400, 47, cx + 400, 847))
            frame.resize((128, 128), Image.Resampling.LANCZOS).save(
                SOURCE / f'orb-{name}-128.png', optimize=True)
    print('Normalized three alpha frames into the existing HUD cell dimensions.')


if __name__ == '__main__':
    main()
