# Combat HUD artwork

The orb glass, blood, forged gold/teal rims, skill frames and rails are original
code-native artwork authored in `scripts/GenerateHudArt.py` on 2026-11-13.
The user's revised HUD approval on 2026-11-23 adds larger, number-free orbs and a
local low-health pulse. Their implementation request also removes every arrow,
triangle and diamond ornament from both orb rims, leaving smooth circles.

The soft warm-red warning ring replaces the obsolete Dodge glyph in the 64×64
region at atlas coordinates (320, 128). Runtime can vary its opacity around the affected
orb without changing the other player's view or obscuring the glass center.
The new original illustrated Dodge emblem occupies the previously unused 128×128
region at (384, 128), using `art-source/dodge-icon/dodge-128.png` as its normalized
source. Its generation provenance is retained alongside that source.

Regenerate the atlas with `python3 scripts/GenerateHudArt.py` (Pillow required
offline only). The file-backed managed atlas remains 512×256 RGBA: 524,288 bytes
(0.5 MiB) of GPU memory, with no retained decoded CPU Pixmap. The smooth rims and
warning halo are code-native; only the Dodge emblem uses generated illustration.
