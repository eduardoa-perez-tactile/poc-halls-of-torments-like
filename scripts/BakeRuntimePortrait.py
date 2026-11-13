"""Export the approved hero illustration at its bounded runtime resolution.

Uses only Pillow already present in the development environment. The source
master and its two equal square hero regions remain unchanged. This export is
larger than the largest current portrait draw and keeps normalized crop math.
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/art/adventurers.png"
OUTPUT = ROOT / "assets/art/adventurers-runtime.png"

source = Image.open(SOURCE).convert("RGBA")
if source.width != 2 * source.height:
    raise ValueError("Expected two equal square hero illustrations")
source.resize((1024, 512), Image.Resampling.LANCZOS).save(OUTPUT)
print(f"Exported {OUTPUT.relative_to(ROOT)}: 1024x512 RGBA, 2 MiB GPU without mipmaps")
