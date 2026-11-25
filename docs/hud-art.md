# Combat HUD artwork

## Approved revision, 2026-11-24

The user's latest approval replaces the small masked boss header and plain orb
rims described below. A face-free painted frame spans 70% of the logical screen
(1344×86 at 1920×1080), with a larger centered live name above it. Orb destinations
increase from 176 to 224 pixels (27%), with painted gold and silver/teal Gothic
rims. Dodge and the five-skill rows move down 50 pixels, including cooldown/rank,
overflow and downed/revival feedback. P1/P2 labels follow the larger frames.
The bottom party XP lane and local low-health pulse/caption remain separate.

The approved proposal, original generated alpha masters, exact prompts and
normalization details are in
[epic HUD provenance](../art-source/epic-combat-hud/README.md). Runtime uses
separate art and live health/name layers, never the proposal screenshot.
Regenerate the replacement sources with `python3 scripts/NormalizeEpicHudArt.py`,
then bake with `python3 scripts/GenerateHudArt.py` (Pillow offline only).

The new frames replace the existing two 128×128 rim cells and 512×96 boss cell.
Original glass/blood/reflection, skill frames, Dodge, warning and crimson fill
remain unchanged. The file-backed managed atlas remains 512×352 RGBA, 720,896
bytes / 0.6875 MiB, with zero retained decoded Pixmaps. This revision adds no
textures or GPU texture bytes, preserving the combined 32 MiB ceiling.

Brief verification on 2026-11-24: JDK 21 core/desktop compilation and Android
APK build passed, along with the existing `PlayerHudModelTest` and
`ArenaHudStateTest`. Android 33 / Television_1080p native screenshots verified the
populated co-op boss HUD, 72% boss fill, low-health pulse/caption, Dodge cooldown,
and downed-player state. A five-second boss fixture reported 32,467,788 combined
GPU texture bytes, HUD render CPU p95 0.43 ms, total render callback p95 9.94 ms,
and frame interval p95 18.09 ms. This brief emulator observation does not prove
sustained physical-TV 60 FPS. The emulator was returned to the lobby.

## Earlier artwork provenance

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

The user approved an original painted Hollow Sovereign boss header later on
2026-11-23, with a smaller footprint than the full concept. A transparent
crowned death-mask and weathered iron/gold frame occupy a 512×96 strip at (0,256).
The crimson energy fill is a separate 120×16 region at (260,212), revealed by live
boss health behind the frame's transparent reservoir. Name text stays live in
the existing font. The title/fill anchors are recorded in `HudArt` and
`art-source/boss-hud/layout.json`. Exact prompts, generated masters and the
approved concept are in [boss HUD provenance](../art-source/boss-hud/README.md).

Regenerate the atlas with `python3 scripts/GenerateHudArt.py` (Pillow required
offline only). The file-backed managed atlas is now bounded at 512×352 RGBA:
720,896 bytes (0.6875 MiB) of GPU memory, with no retained decoded CPU Pixmap.
The boss strip adds 196,608 bytes; the previously measured 33,319,756-byte combined
texture total plus this strip is 33,516,364 bytes (31.96 MiB), below 32 MiB.
Existing orb, skill, Dodge and warning regions retain their previous coordinates
and pixels. No standalone boss texture or startup-retained master is loaded.
