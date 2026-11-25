"""Export the approved hero illustration at its bounded runtime resolution.

Uses only Pillow already present for offline resizing. Source masters and their
two equal square regions remain unchanged. File-backed runtime pages use
640x320 (full body) and 768x384 (faces), 1.90625 MiB GPU combined. The full-body
page's 320px source height still exceeds its largest 318px draw.
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/art/adventurers.png"
OUTPUT = ROOT / "assets/art/adventurers-runtime.png"

source = Image.open(SOURCE).convert("RGBA")
if source.width != 2 * source.height:
    raise ValueError("Expected two equal square hero illustrations")
source.resize((640, 320), Image.Resampling.LANCZOS).save(OUTPUT)
print(f"Exported {OUTPUT.relative_to(ROOT)}: 640x320 RGBA")

faces = Image.open(ROOT / "art-source/hero-portraits-v1/transparent.png").convert("RGBA")
if faces.width != 2 * faces.height:
    raise ValueError("Expected two equal square head-and-shoulders portraits")
faces.resize((768, 384), Image.Resampling.LANCZOS).save(ROOT / "assets/art/hero-portraits.png")
print("Exported assets/art/hero-portraits.png: 768x384 RGBA; combined portraits 1.90625 MiB GPU without mipmaps")
