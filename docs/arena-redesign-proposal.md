# Arena redesign proposal

This is the concrete replacement plan for the 2026-11-23 level-design feedback.
The open-arena versus solid-interior-obstacle decision remains pending. Keep one
2400×1800 world-unit arena, its twenty-minute encounter timeline and current UI.

## Why the current room feels disconnected

- The 14 renderer-only props sit on two sparse edge rows and a few side points.
  They have no authored footprint, collision, route or spawn-exclusion meaning.
- The current border is intentionally low coping, painted into floor cells. Its
  source contract uses a 120×60 diamond and 152×84 source canvas; it cannot contain
  a convincing tall wall by merely increasing the draw height.
- Props come from the older warm-brown environment kit; the newer floor/border is
  cool grey. Their shared lighting, viewing angle and base sizes were never
  measured against that floor. Bounds validation does not validate perspective.
- Every prop uses the same 62-unit shadow. Several ground pivots are approximate
  95–98% canvas-height values, and scale comes from full source-canvas height,
  rather than a measured ground footprint. This weakens contact with the floor.

## Replacement room

Use one coherent 2:1 isometric stone kit with the same camera, stone palette,
upper-left lighting and tile-scale reference in every source image. Require a
measured diamond footprint, ground pivot, facing and projected footprint width
for each prop. Derive prop scale from that footprint; draw height follows the
artwork. Use footprint-sized contact shadows instead of one universal shadow.

Keep the middle ritual court open: approximately x=-420…420, y=-320…320. Build
four readable peripheral landmark groups, with different silhouettes and density:

| Group | Approximate world bounds | Composition |
| --- | --- | --- |
| Fallen choir | x=-900…-500, y=160…620 | Grouped tombs, two broken columns, low debris |
| Sanctum | x=350…760, y=470…720 | One substantial altar and a wall niche |
| Sealed crypt | x=430…900, y=-650…-260 | Recessed gate, paired pilasters and a low offering stone |
| Broken gallery | x=-820…-440, y=-680…-360 | Uneven column group and a low rubble bank |

Leave a broad outer circulation loop and at least two approaches to every group.
Aim for 200–260 world-unit clear passages, not narrow doors or single-file lanes.
Keep the joining position, central boss space and reward presentation clear.
These are authored positions, not random scattering or a claim of proven balance.

## Tall borders and visibility

Retain the current cached floor and low foundation. Add tall wall bodies as
separate depth-sorted sprites with consistent footings, rather than stretching
the opaque floor-cache quads. Their tops should read roughly two-and-a-half to three hero heights
above the floor; validate that ratio in an actual camera capture.

Far walls can stay full-height. Near walls retain their bases while their upper
parts cut away or fade when they obscure the viewing player's body/threat space.
Compute that visibility separately for each split-screen camera. A wall near P2
must not change P1's unrelated view. Keep the geometry bounded and cull wall
segments before sorting; no framebuffer or extra scene texture is required.

## Authored placement data

Replace `ArenaRenderer`'s three parallel prop arrays with one startup-loaded,
bounded placement list, for example `assets/arena/crypt-layout.json`. Each entry
has an asset/facing, world position, zone, ground footprint and occlusion policy.
Keep positions reviewable in one source and validate bounds/clearance offline.
The renderer reads cached primitive fields, never JSON or layout code each frame.

If the arena stays open, footprints support shadows, placement validation and
spawn exclusions; they do not silently become colliders. If solid obstacles are
chosen, the same immutable footprint list must also drive player/Dodge movement,
enemy movement/knockback/dashes, valid spawns and late-join placement. Decide the
projectile/low-cover policy explicitly. Direct chase currently has no navigation;
adding body colliders alone would leave enemies stuck. A small bounded route or
visibility graph is sufficient for a few authored obstacle groups; no general
navigation framework is needed.

## Resource and source plan

Current accounted residency with QR is 33,516,364 bytes, leaving only 38,068 bytes
below 32 MiB. Replace artwork inside existing pages instead of adding textures.
The unused legacy floor/wall/corner regions consume 74,524 visible pixels in the
existing 512×512 environment page. Retire their runtime contract while retaining
their masters in source archives; use that area for tall wall pieces and coherent
replacement props. Keep the 6 MiB actor/environment bank and half-MiB border page.

Change `GameArt.floors`, `GameplayAtlas.REQUIRED_REGIONS` and the environment baker
together, then rerun the supplemental attack/projectile packing. Those animations
borrow the same environment page and must never be overlapped. Preserve actor
frames, existing combat atlases and art provenance.

Suggested production ownership: one art/baker chunk, one immutable layout and
optional movement/navigation chunk, and one renderer/depth/occlusion chunk. Verify
an assembled room and a few native near-wall solo/co-op views before delivery;
short checks cannot establish arena fun, balance or physical-TV performance.


## Visual proposal and approval boundary

The current painted proposal is preserved in
[`art-source/arena-redesign-proposal.zip`](../art-source/arena-redesign-proposal.zip)
alongside the exact built-in ImageGen prompts and reference input. It is a style,
projection and grouping proposal, not runtime art or proof of route balance.
No flattened environment painting is to be loaded as the arena. Production needs
separate modular walls, floor treatment and grounded prop sprites using the
metadata/resource plan above.

The combat range/slash changes are independently implemented. Replacing the arena
artwork and resolving solid obstacles are the next milestone; the current room
has not been silently relabelled as the redesigned room. The project agreement
requires approval before major visual direction changes. The open/solid-obstacle
question was presented to the user while the combat fixes were underway.
