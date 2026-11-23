# Implemented open crypt arena

The 2026-11-23 redesign replaces the sparse legacy prop rows with character-height ruined borders
and coherent, authored landmark groups. The user chose an OPEN arena: decorations
remain visual landmarks, with no new collision obstacles or navigation system.
The one-arena twenty-minute run, 2400×1800 playable bounds, player/enemy movement,
projectiles and spawn rules remain unchanged.

## Room composition

The startup-loaded layout is [`assets/arena/crypt-layout.json`](../assets/arena/crypt-layout.json).
It contains 59 bounded placements: 36 wall modules, four corner supports and
19 members across four interior landmark groups. A clear central ritual court
covers x=-420…420, y=-320…320; authored footprints are checked against that court.

| Landmark | Group origin in world units | Composition |
| --- | --- | --- |
| Fallen choir | (-750,430) | Paired tombs, broken column and low fragments |
| Sanctum | (600,610) | Altar, two hooded guardians and a column base |
| Sealed crypt | (770,-490) | Sealed gate, tomb, guardian, offering and fragments |
| Broken gallery | (-720,-570) | Broken columns and scattered low rubble |

These clusters provide different silhouettes and orientation cues without
turning the room into corridors. Their arrangement is authored rather than
randomly scattered. Decorative footprints are visual scale/clearance metadata;
players, enemies and projectiles can pass through interior scenery. Wide-looking
spaces and an open center are layout choices, not proof of gameplay balance.

## Grounding, walls and visibility

The original painted kit uses cool-grey crypt stone, upper-left lighting and a
shared nominal 2:1 isometric camera. Wall X/Y and sarcophagus X/Y are separately
authored facings. Each region records a ground-footprint center pivot, projected
source footprint width and world X/Y footprint. `ArenaLayout` derives uniform
scale from those footprints; it does not scale props from their canvas height.
Optional `sizeScale` preserves proportions, while aspect-changing footprint
overrides are rejected. Contact shadows follow the footprint size.

The painted wall sources retain small measured camera-angle deviations. A bounded
per-region vertical shear calibrates only their four existing draw vertices to
the exact floor diagonals, preserving upright edges and stone-body height. Ground
pivots also match each fitted base intercept and the 36-unit wall thickness.
Culling and reveal bounds include this projection correction. Source pixels remain
unchanged; no new textures or GPU buffers are introduced. One fixed 20-float staging array
is reused for the draw vertices, with no frame-loop allocation.

The user rejected the first tall, clean fortress-wall treatment and selected
ruined crypt masonry with broken tops, approximately character height. Four wall
runs alternate two painted variants per axis. X runs repeat at 240 world units;
Y runs use a uniform 0.9375 scale and repeat at 225 units so both axes meet the
original room bounds without shortened or stretched end panels. Four rough corner
supports cover the joins, replacing the repetitive gold pilasters. Roots remain
just outside the fighting floor at x=±1212 and y=±912. Module spacing is validated
against measured world footprints. The cached floor/low foundation stays in place;
wall bodies and landmarks depth-sort with actors as independent sprites.

Each camera computes upper-sprite reveal separately. When tall scenery obscures
a visible player or enemy, its upper portion becomes translucent while the low
base stays opaque. A wall can therefore reveal a hero in one split view while
remaining solid in the other. This uses ordinary sprite alpha and source UV
splitting, without an extra framebuffer or scene texture.

## Authoring and source files

`ArenaLayout` validates the layout once at startup and caches placements for the
renderer. The JSON groups named landmarks and border runs in one reviewable file,
replacing the old parallel X/Y/region arrays. The fixed placement cap is 192;
render loops use cached fields and culled, bounded sort storage.

The source concept is preserved in
[`art-source/arena-redesign-proposal.zip`](../art-source/arena-redesign-proposal.zip).
Runtime uses separate sprites, not the flattened concept painting. Selected
painted masters, exact ImageGen prompts, ground-footprint metadata and a canonical
baker-input archive are in
[`art-source/crypt-environment-v2/`](../art-source/crypt-environment-v2/README.md).

```sh
python3 scripts/BakeCryptEnvironment.py --output assets/art
```

This final bake stage retires the legacy runtime floor/wall/prop contract and
repacks only the existing 512×512 environment page. The old masters remain in the
base source archive. Actor pages and actor frames 0–207, all 24 missile sprite
pixels/timing/pivots, and utility sprites are preserved. The actor/environment
bank stays 6 MiB and the separate floor/border page stays 0.5 MiB: no new runtime
texture or retained decoded Pixmap is added. Source hashes, gutter/nonoverlap
checks and an archive-only byte-identical rebuild are recorded in the kit's
verification file.

Native rendering, solo/co-op occlusion and resource observations belong to the
integration handoff/performance record. Short automated or emulator checks do
not establish that the arena is fun, balanced or sustained 60 FPS on a physical TV.


## Approved painted border repair

The user approved the camera-scale painted proposal on 2026-11-23. The current
implementation uses one complete source painting for each wall orientation,
cut only for storage, instead of repeating separately capped fragments or
stretching a material patch. Source-neighbour context protects filtered joins.
The new low corner acts as backing for meeting wall ends; complete walls extend
to the adjoining outer faces. Native solo and co-op corner/straight-run captures
and the pixel-level checks are recorded in [the integration record](painted-arena-borders.md).

The 59 authored legacy placement records remain as layout/footprint data; the four
wall runs and four corner positions now produce 206 cached painted quads, while
nineteen interior landmark props continue through the normal sprite path. No
movement/collision or floor-art change is included.

## Approved engraved floor

The user approved the gameplay-scale floor proposal and authorized implementation
on 2026-11-23. The implemented floor adds the central shallow engraved medallion,
flush dressed-stone paths, muted flagstone variation and authored wear. The open
movement area and arena bounds stay unchanged. Sources, placement, texture limits
and native checks are recorded in [the floor integration](engraved-crypt-floor.md).
