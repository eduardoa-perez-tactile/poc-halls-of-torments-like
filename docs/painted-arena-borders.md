# Continuous painted arena borders

The user approved the painted camera-scale proposal on 2026-11-23. This replaces
the independently capped wall fragments that showed visible repeat seams. The
intermediate stretched material-surface experiment was rejected and is not used.

One original ImageGen painting covers each complete 2400/1800-unit wall axis.
Broad staggered masonry, chipped tops and roughly hero-height bodies are painted
into those sources. There is no runtime material tiling, mirrored wall facing,
procedural brick geometry or reset at an atlas piece boundary. The full paintings
receive one fitted ground pivot/projection each. Their typical stone body height
is about 84–86 units before a small final placement adjustment; heroes are about
104 visible units high. A matching low corner provides backing at the four turns.

The four logical wall runs come from the existing arena JSON. Complete paintings
extend about 2.5%/3.3% to meet the actual adjoining outer wall faces, rather than
stopping short at the combat-floor bounds. This also changes their height by that
small uniform factor. The corner backing is sorted before those wall ends, so it
closes the join without drawing a narrow pillar over the continuous courses.
Terminal pieces share a fixed overlap order at each turn; a narrow storage cut
therefore cannot switch which intersecting wall face is on top.
Low perimeter art stays opaque. Existing tall landmark reveal, open movement,
combat bounds, cameras, HUD and floor art are unchanged.

## Storage and rendering

The baker uses a single global 85% proportional Lanczos normalization of each wall
before splitting it into storage rectangles. Original masters/normalized paintings
are retained. The corner keeps its original resolution. Each packed rectangle has
one pixel of the original painting as context on every side; these are neighbouring
source pixels, not a repetition of the rectangle's own edge. Runtime UVs draw only
the core. Adjacent cores therefore filter identical source texels at both vertical
and horizontal boundaries. Far-away alpha-1–9 generation noise is omitted by ordinary
alpha-32 storage trimming with an extra margin; significant paint is retained.

The 99 source pieces create 206 placed quads. Their positions, bounds, source UVs
and sort depths are cached once. The renderer culls and depth-sorts those pieces
with actors, drawing them through the existing SpriteBatch. Cached vertex payload
is 16,480 bytes, plus bounded object/metadata storage. There are no frame-loop
allocations, texture uploads, new texture pages or new GPU buffers.

Existing gameplay/attack/missile metadata remain byte-identical. The bake appends
only into blank guarded areas of the three existing managed gameplay textures;
all earlier sprite pixels and sampling gutters are verified unchanged. The bank
remains 6 MiB. Accounted combined GPU residency with QR remains 33,516,364 bytes
(31.9637 MiB), and retained Pixmaps remain 20,245,324 bytes. Gameplay retains no
decoded source Pixmaps.

## Sources and checks

[Selected art and exact prompts](../art-source/painted-arena-borders/README.md),
[canonical bake proof](../art-source/painted-arena-borders/bake-verification.json),
and [baker](../scripts/BakePaintedArenaBorders.py) are tracked. The source archive
rebuild reproduced all three PNGs, runtime metadata and derived paintings exactly.

Twenty-two focused JDK 21 tests passed: all significant source pixels are covered
exactly once; packed context copies match source RGBA; actual draw vertices and
bilinear samples agree across storage joins in both axes; wall scale/residency and
malformed metadata are checked; existing atlas, attack and projectile contracts
remain valid. A regression check also covers the final narrow atlas column at
the near corner. Desktop classes and Android debug assembly passed. The five
border checks passed after the final corner-order correction.

Brief native API-33 ARM64 Television_1080p checks covered straight runs, landmarks,
all four corners and separated co-op cameras. The final five-second co-op window
had 301 frames: interval p95/p99/max 20.91/21.85/22.57 ms; CPU submission
4.42/4.77/4.99 ms, with 36 draw calls. There were 142 intervals above 16.7 ms and
none above 33.4 ms. This is rendering/join evidence, not a full survival playtest,
proof of fun/balance or sustained physical-TV 60 FPS. The 16.7 ms target remains
unproven on physical hardware.

Native screenshots/logs are ignored under `.run/painted-borders-*`. Temporary
inspection controllers/loggers/scripts are stopped/removed. The emulator and LAN
relay remain running at Home for the user; no physical phone was targeted. Floor
engraving, paths and variation remain the next separate design discussion.
