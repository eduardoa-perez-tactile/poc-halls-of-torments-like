# Continuous painted arena borders

## Separate epic gate — 25 November 2026

The later gate feedback supersedes preservation of the old gate artwork below.
The main entrance is now its own original transparent painting: a tall pointed
stone arch, carved crest, closed iron-bound wooden doors and stepped masonry
shoulders. Its fixed north-wall position allows both wings to be painted for the
actual adjoining wall plane. The continuous perimeter and approved corner wraps
remain intact; the gate is still render-only at `(0,912)`.

The production asset uses a single whole-canvas 256×256 normalization. Measured
bottom contours calibrate its ground pivot and a slight vertical shear; uprights
stay vertical and no source pieces are individually warped or resampled.
`PaintedGateArt` caches all storage-cut vertices, bounds and UVs once, borrows
resident textures and draws after the rear wall but before complete actors.
The old gate prop and its separate shadow are no longer submitted. The narrow
wall opening stays beneath opaque gate paint.
The wing handoffs stop inside their painted flat endcaps and use a short cached
opacity blend over the unchanged wall; the arch and doors stay fully opaque.

The offline baker reuses the retired gate frame 219's exclusive guarded slot
plus spare pixels. Its old pixels and metadata remain in the exact baseline
archive. Every other sprite, gutter, floor/border painting and approved corner
is protected. The new gate adds zero GPU texture bytes and retains no decoded
gameplay Pixmaps. Exact prompts, original master, native guide, calibration,
rebuild inputs and preservation proof are in the
[gate source package](../art-source/epic-crypt-gate/README.md).

### Epic gate verification

The full source is stored in 49 collared pieces; cached handoff meshes add only
bounded CPU geometry. The archive-only rebuild reproduced all four PNG pages
and gate metadata byte for byte. Every existing corner piece plus its two-pixel
gutter was independently compared with the pre-gate baseline and stayed identical.
All other protected active atlas pixels and gutters also passed their hash checks.

Twenty-four focused JDK 21 tests passed across the new gate, wall opening,
environment projection, original borders, corners and arena layout. Checks cover
resident source reconstruction, wing ground alignment, vertical uprights, opaque
doorway coverage, source/UV preservation across handoff splits, continuous vertex
opacity, rear-layer ordering and bounded metadata. Desktop compilation and Android
debug assembly passed. No full survival playthrough was run.

Native 1920×1080 screenshots under `.run/epic-gate-final/` show the actual updated
gate close-up, normal gameplay camera/HUD and full arena. A final 90-frame static
sample measured CPU submission p50/p95/max of 0.440/0.534/3.184 ms, excluding
simulation, audio and GPU completion; this does not prove physical-TV 60 FPS.
Gameplay art remains 8,978,432 texture bytes with zero retained gameplay Pixmaps,
and the combined texture inventory is unchanged. The emulator and its phone relay
were not restarted or reconfigured; the saved LAN join host and endpoint remain
available for the user's playtest.

## Painted gate/corner repair — 25 November 2026

The user found that the gate still looked freestanding and the four wall turns
still looked assembled from separate endcaps. A further geometry-only fit was
rejected. The user approved the subsequent painted join proposal in
[`art-source/crypt-wall-join-repair/approved-proposal.png`](../art-source/crypt-wall-join-repair/approved-proposal.png).
That approval supersedes the corner-backing treatment described in the older
records below; the long wall paintings and the rest of the level stay unchanged.

The gate is now seated at (0,912), on the north wall centre line, instead of
(0,870) in front of the wall. Its existing 250×50 footprint overlaps the 36-unit
wall by seven units on each face. `GateWallOpening` caches a narrow wall cut
inside the opaque door/jamb artwork. Resident alpha checks cover the complete
cut through the wall's thickness and height, and confirm that it cannot expose
transparent gaps. Gate scale, painted pixels and corrected ground projection
are preserved. The gate remains render-only; arena movement bounds are unchanged.

Four original directional join paintings wrap the broad masonry courses around
the turns without separate posts, lips or doubled cap faces. The native wall-only
guides keep the existing arms and camera projection. Whole-canvas normalization
produces 192×144 sources at 2.083333 logical units per pixel; measured ground
contours determine each pivot. The built-in ImageGen prompts, source guides,
approved proposal, raw masters and normalization/calibration records are preserved
in the [source package](../art-source/crypt-wall-join-repair/README.md).

`PaintedCornerArt` reuses spare space in the four already resident gameplay pages,
including the managed Grave King page. Its 88 storage pieces preserve neighbouring
source pixels and sampling gutters. No page, texture dimensions, GPU texture
bytes or retained Pixmaps are added. The archive-only baker verifies that every
old frame rectangle, gutter and nonzero RGBA pixel remains identical and that
the saved pieces reconstruct each new painting exactly.

All endpoint cuts, source UVs and join meshes are cached once. Old backing pieces
are hidden and the old endcaps are trimmed only beneath the new painted wraps,
with an opaque handoff overlap. Mixed front/back corners are split into cached
rear and foreground meshes so their rear wall arm stays behind the complete
actor pass. Joins are grouped by resident page within each drawing layer. There
is no per-frame geometry allocation, source resampling, texture upload, scenery
collision, gameplay rebalance or change to the approved floor.

### Repair verification and handoff

Twenty focused JDK 21 checks passed across the original painted borders, the
new corner meshes, gate opening, environment projection and arena layout.
Desktop compilation and Android debug assembly passed. The new tests check
source-area conservation across mixed front/back splits, original affine UVs,
layer order, bounded caches, page grouping, gate opacity coverage and separation
between doorway and endpoint cuts. The baker independently verifies original
protected atlas pixels and gutters.

Fresh native 1920×1080 views cover the seated gate, all four corners with a nearby
hero, and the entire updated arena. The user accepted these runtime views
("way much better"). Original long-wall paintings remain, so their short material
handoffs to the new wraps can still be visible at close zoom. Evidence is ignored
under `.run/wall-gate-final/`; rejected geometry-only studies are not implemented.
A 120-frame static renderer sample measured CPU submission p50/p95/p99/max
0.202/0.410/1.629/2.396 ms. This excludes simulation, audio and GPU completion and
does not prove physical-TV 60 FPS or full-run balance. No survival playthrough was
run. Temporary native processes and harness files were removed; the user's
emulator, LAN relay and ADB forwards were preserved by this repair task.

## Historical 24 November correction

The 24 November formal-layout correction removes the legacy tile-foundation skirt,
so these paintings now form the only visible perimeter. Whole-painting scale fits
the actual adjoining outer-face span; it no longer adds an extra wall thickness
to that span. Corner backing fits the 36×36 junction instead of projecting a 50×50
post beyond it. End-region depth uses the entire source interval, covering every
storage row and narrow terminal column at all four corners. North/east wall planes
draw before the complete interior pass, south/west planes draw afterward and
corner backing precedes both. This keeps rear columns behind both gate uprights
and prevents atlas storage cuts from slicing an actor vertically. Source PNGs,
UVs, neighbour collars and texture storage remain unchanged. The following record
describes the original 23 November integration.

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
