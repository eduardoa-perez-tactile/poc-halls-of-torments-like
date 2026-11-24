# Engraved crypt floor

The user approved the generated floor proposal and requested implementation on
23 November 2026. Subsequent native feedback identified blurry paving and flat
path stripes. The current clarity pass replaces those field/path materials with
more clearly painted stones and independently placed border blocks. The original
engraved medallion, open arena, combat bounds and gameplay remain unchanged.

## Current placement

The approved formal crypt layout of 24 November supersedes the eight spokes and
separate curbstones. `assets/arena/crypt-floor.json` now authors a central 600-unit
medallion, one 240-unit-wide processional aisle and two 320-unit-wide burial strips.
Paving spans each band's width and repeats every 200 units along its length, with
partial final slabs cropped rather than stretched. All three bands are flush,
render-only and cached once. The field still repeats every 120 units; wear follows
the fixed burial/shrine groups. The legacy exterior foundation skirt is no longer
drawn, leaving only the continuous painted-wall perimeter.

The medallion remains a true ground-plane circle projected to a 2:1 ellipse;
a 45-degree rotation aligns the flourish with screen axes. It stays flush and
uses ordinary alpha before hazards, targeting rings, pickups and actors.

## Historical floor-art integration

The field has roughly four clearly painted stone courses per repeat, avoiding
many weak joints compressed into a small texture. Its 64×64 material at a
120-unit repeat has 25% more samples per world unit than the original 96px/240-unit
material, with substantially more samples per individual painted stone. The
128×64 path doubles samples across its width while keeping its 200-unit repeat.

`StonePathBorders` builds separate 38×20-unit flat blocks on both sides of each
path, on a 40-unit period with 2-unit grout gaps. Their transparent chipped
silhouettes reveal the ground between blocks. The path's old painted margins
are cropped at U=0.1/0.9, so the fill does not form a smooth stripe under those
edges. All path/block geometry clips to the unchanged 2400×1800 arena with UV
interpolation rather than stretched final stones. None creates collision.

Base paving, paths, blocks and retained foundation/exterior share one startup
SpriteCache, grouped by page within each layer. The shipped floor uses 4,564
cached quads, versus 5,666 previously: 365,120 vertex bytes and 54,768 index bytes
of used payload. Allocated capacity remains 8,000 quads (640,000 vertex + 96,000
index bytes). The medallion's 292 cached quads retain 23,360 vertex bytes and
reuse the existing SpriteBatch. Both cameras share the static geometry; no
floor allocations or uploads were added to the frame loop.

## Art and texture bounds

[Selected original sources, exact prompts and provenance](../art-source/crypt-floor-clarity/README.md)
were created with built-in ImageGen. Production uses separate material paintings
and an isolated border block, not the flattened proposal screenshot. The old
medallion master and normalized source remain byte-identical.

The four existing managed gameplay/foundation pages hold 317 source pieces:
field 8, path 16, medallion 292 and curb 1. Field/path use 32×16 storage cores;
medallion cuts retain their 24×8 cores. The curb stays whole at 32×16 with one
pixel of context on every side. Its RGB is premultiplied for the existing
coverage shader, preventing bright edges when that shader divides sampled RGB
by coverage. All other field/path samples are opaque.

No texture page or retained decoded Pixmap is added. Combined accounted texture
residency including QR stays 33,516,364 bytes (31.9637 MiB); retained Pixmaps stay
20,245,324 bytes. The source archive preserves all four pre-floor baseline pages,
five metadata files and medallion source. The additive bake preserves every old
protected sprite/wall/foundation pixel and gutter. Its [verification record](../art-source/crypt-floor-clarity/bake-verification.json)
also proves exact new source coverage/context, an independent current rebuild
and a byte-identical historical floor rebuild.

## Checks and limits

Twenty-five focused JDK 21 checks passed across source/UV coverage, repeat collars,
curb alpha/density, clipped path/block geometry, real grout gaps, wear continuity,
metadata rejection and retained painted-wall/foundation contracts. Desktop
classes and Android debug assembly passed. Native Android 13/API 33 ARM64
Television_1080p checks inspected the centre, a landmark and separated co-op
cameras. Ignored captures/logs are `.run/clear-floor-*`.

A short co-op observation after resetting frame metrics recorded 298 frames:
interval p95/p99/max 21.81/23.81/56.85 ms; callback wall time 7.04/8.60/41.60 ms;
39 draws, including 16 floor draws across two cameras. These visual checks do not
establish a speedup, sustained 60 FPS, full-run balance or physical-TV performance.
The [controlled performance baseline](performance-baseline.md) was captured before
this clarity pass and remains evidence for that exact earlier floor/APK.

Temporary inspection clients/logger/scripts were stopped/removed. The emulator
and existing LAN relay remain available at ordinary Home for handoff.
