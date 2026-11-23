# Engraved crypt floor

The user approved the generated gameplay-view proposal and requested implementation
on 23 November 2026. The floor now has an original shallow Gothic medallion, broad
flush paths and quiet varied stone flags. It remains cosmetic: arena bounds,
movement, spawns, collision and the open landmark layout are unchanged.

## Placement and rendering

`assets/arena/crypt-floor.json` authors the central 600-unit medallion, eight
200-unit-wide paving spokes and six smooth wear zones. This diameter matches the
approved gameplay-camera scale (roughly 1,050 pixels wide in a 1920-wide solo view).
The true ground-plane circle projects to a 2:1 ellipse. A 45-degree world-plane
rotation aligns the flourish with screen axes and its inner paving with the
surrounding floor. Everything is flush; there is no platform or raised border.

The field material repeats every 240 world units, with smaller flags than the path's
broader dressed slabs. Paths have separately painted edging stones and repeat
along their length every 200 units. Geometry is clipped at the existing 2400×1800
floor bounds with UV interpolation, so partial cells crop rather than stretch.
Quiet stone colour/chips/cracks come from the original paintings; authored smooth
wear changes vertex colours around landmarks without random per-frame decoration.

Base paving, paths and the retained foundation/exterior share one startup-built
SpriteCache. Cells are grouped by texture page within each layer, avoiding a
texture switch for every storage cut. The 292 medallion quads are cached once and
grouped by page through the existing SpriteBatch. They use ordinary alpha before
hazards, targeting rings, pickups and actors; the original floor-cache coverage
shader stays limited to opaque paving/foundation. There are no floor frame-loop
allocations or uploads. Both cameras reuse the same geometry.

## Art and resource bounds

[Original masters, approved concept and exact prompts](../art-source/engraved-crypt-floor/README.md)
were made with built-in ImageGen. Production uses separate field/path materials
and a 256×256 medallion, not the flattened proposal screenshot. The four existing
managed gameplay/foundation pages hold 364 source pieces with 24×8 cores and one pixel
of actual source-neighbour context on every side. These are storage cuts, not
repeated fragments of the medallion. Field repeats in both axes; the path repeats
only along its length so its side edging stays intact. Source masters and the
first path attempt are preserved outside runtime assets.

No new texture page or retained decoded Pixmap is added. Accounted combined GPU
texture residency including QR remains 33,516,364 bytes (31.9637 MiB); retained
Pixmaps remain 20,245,324 bytes. The static floor cache is bounded to 8,000 quads:
640,000 vertex bytes plus 96,000 index bytes of allocated mesh capacity. The shipped layout uses 5,666 cached quads (453,280 vertex bytes and 67,992
index bytes of used payload). Medallion vertex payload adds 23,360 bytes, plus bounded
quad/array metadata. These geometry costs are separate from texture residency.

The standalone final stage is `scripts/BakeEngravedFloor.py`, after the painted
border baker. Its source archive retains the untouched pre-floor four pages and
five metadata files. [Bake verification](../art-source/engraved-crypt-floor/bake-verification.json)
checks every old sprite/wall/foundation pixel and sampling gutter, exact new
source coverage/context copies, unchanged old metadata, and an independent
archive-only byte-identical rebuild.

## Verification and limits

Twenty-two focused JDK 21 checks passed across floor source/UV coverage, repeat
collars, clipped path geometry, wear continuity, malformed metadata and existing
painted-wall/foundation contracts. Desktop classes and Android debug assembly
passed. Brief native Android 13/API 33 ARM 64 Television_1080 p checks cover the centre,
a landmark and separated co-op cameras; captures are ignored under
`.run/engraved-floor-*.png`. The attack indicator remains separate above the stone
engraving. No whole-run survival or balance playtest was performed.

The visual-check timing logs include ADB navigation and screenshots and contain
large callback/interval spikes; their cause is not established by this check.
The performance-baseline chat has the evidence for a controlled follow-up.
These captures do not prove sustained 60 FPS or physical-TV performance. Temporary
inspection controllers/logger/scripts are stopped and removed after the check;
the emulator and LAN relay remain running for handoff.
