# Magic crossbow and hostile missiles

The Ranger now uses a magic crossbow firing cyan/ivory arcane bolts. Cultist soulflame is violet/rose, and the Hollow Sovereign uses gold/crimson hellfire. Each missile has eight original raster frames with a bright head, short shaped energy tail and a few rune fragments. No wooden shaft or feather remains in the projectile art. Character artwork and stable skill/protocol identifiers are preserved; character information, phone upgrade copy, equipment summaries and Run Details now call the Ranger's projectiles bolts.

All three loops last **640 ms**. Their normalized canvases are **96 × 128**, with one fixed head pivot per type: Ranger **(72, 64)**, Cultist **(70, 66)** and Sovereign **(73, 67)**. Uniform quarter-size normalization preserves every strip's shared scale; no per-frame fitting or recentering is used. Only alpha values at or below 8 are cleared to remove negligible background specks. The floating sprites face local +X and ordinary SpriteBatch rotation follows projected world velocity. Floor perspective is handled independently by the arena projection.

## Shared atlas storage

`magic-projectiles.json` references spare rectangles in the existing managed **512 × 512** `gameplay-environment.png`. The twenty-four new frames and their sampling gutters occupy **88,641 pixels**, adding **zero GPU texture bytes** and no retained decoded Pixmap. Existing `gameplay.json` frame indices, geometry and all nineteen environment frame/gutter crops remain unchanged. The supplemental loader borrows page 2 from `GameArt`, caches trimmed regions once and does not own or dispose the page. The bank uses two-pixel padding and one-pixel edge extrusion; it introduces no custom shader or per-frame allocations.

## Sources and rebuild

The art was generated as three whole eight-frame sheets with the **built-in ImageGen tool**. The archive `art-source/magic-projectiles-canonical.zip` retains every exact prompt, raw RGBA sheet, normalization pivot, provenance, source hash, untouched base environment page and its original occupancy metadata. No purchased artwork, API fallback or tool/dependency installation was used.

```sh
python3 scripts/BakeMagicProjectiles.py --output /path/to/rebuilt-art
```

The default output is `assets/art`; `--sources` can select a different canonical archive. The baker starts from the archived untouched base page, packs only unused space, checks every preserved environment sample/gutter and validates each exported sprite against the saved PNG. Run this supplemental baker after rebuilding the original gameplay bank. An archive-only rebuild reproduced both supplemental outputs byte for byte.

## Focused verification

Explicit JDK 21 checks passed: three new metadata/loop/packing checks, three hero catalog checks, twenty-two progression checks, fifteen equipment checks and four item catalog checks (**47 checks**). New checks cover loop advancement, fixed head geometry, rejection of malformed page/timing data, and separation from existing gameplay sampling gutters. Offline previews confirmed the three distinct magical silhouettes at game-scale sizes. The actual game preview and physical-TV performance remain part of the integration handoff; this asset check does not establish balance or sustained 60 FPS.
