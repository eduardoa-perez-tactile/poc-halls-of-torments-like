# Full painted arena borders

Original production artwork made with built-in ImageGen on 2026-11-23, following
the user's approved `arena-border-repair-proposal/native-camera-proposal.png`.
Each full wall is **one uninterrupted painting**. It is never repeated across a
side, unfolded from a material patch or rebuilt from procedural stone rectangles.

The X painting covers the entire 2400-unit side; Y covers the entire 1800-unit
side. Both have 36-unit thickness, broad staggered grey masonry courses and
natural broken tops. The X and Y paintings were generated independently, once
each, against explicit isometric blockout guides. A corner painting received one
focused proportions/transparency correction. It provides only the actual four
corner joins, with no special cap, pedestal or tower ornament.

## Selected sources

- `wall-x-source.png` / `wall-x.png`: original master / normalized full X wall.
- `wall-y-source.png` / `wall-y.png`: original master / normalized full Y wall.
- `corner-source.png` / `corner.png`: selected master / normalized corner.
- `*-prompt.txt`: exact requests; the corner also has its correction prompt.
- `*-geometry.png`: code-authored geometry references used as ImageGen inputs
  only, never runtime artwork.
- `paintings.json`: shared production metadata; individual generation records
  retain source fits, normalization and output provenance.
- `art-provenance.json`: source/reference hashes and exact art operations.

No floor, player, HUD, gameplay or runtime asset was changed by the art task.

## Projection and scale

Sources use image X right and Y down; runtime Y points up. Each pivot is the
ground footprint centre. A single fitted slope and ground pivot calibrate each
**whole painting**, before any packing slices are considered. There are no
independent fits, course resets or endcaps at slice boundaries.

| Painting | Normalized canvas | Footprint width | World footprint | Pivot | World scale | Global render shear Y |
| --- | --- | --- | --- | --- | --- | --- |
| X | 1004×559 | 1000 | 2400×36 | 502,305.509549 | 1.722512119 | -0.00383221 |
| Y | 758×380 | 754 | 36×1800 | 379,217.877159 | 1.721814390 | -0.069899809 |
| Corner | 54×91 | 50 | 50×50 | 27,76.5 | 1.414213562 | 0 |

The wall scales differ by less than 0.05%, preserving effectively the same pixel
density. Their measured typical physical masonry height is approximately
84/86 world units, with natural broken-top variation; the corner body is about
88 units. Bounding-box peaks also include the projected wall span and must not
be mistaken for physical height. The corner's peak above ground centre is about
105 units. Native views establish the perceived size beside the 104-unit hero.

For a source point (sx,sy), with pivot (px,py), scale s and global shear k:

```text
screenX = (sx-px)*s
screenY-up = (py-sy)*s + k*(sx-px)*s
s = (worldLength+worldThickness)*sqrt(.5)/projectedFootprintWidth
k = measuredSlopeDown-targetSlopeDown
```

The X target slope is -0.5; Y is +0.5. Front-ground intercept calibration accounts
for the projected 36-unit thickness. Corner scale derives from its 50×50
footprint. No painting is mirrored, rotated or warped offline.

## Normalization and packing boundary

Selected masters remain untouched. Normalized PNGs are cropped using alpha
bounds at threshold 32, proportionally downsampled with Lanczos and placed on
small transparent margins. No masking, recolouring, repainting or geometry warp
is applied. Both wall PNGs retain faint generated alpha noise; detected reddish
edge pixels reach alpha 6 at most in the normalized sources, with no opaque red
artifact. Canonical inputs preserve those source pixels.

The integration baker may trim individual packing cores using the authorized
alpha-32 bounds with one-pixel vertical margin and original-neighbour context.
This is an atlas storage crop, not an art edit: copied RGBA stays exact. Atlas
pieces must reconstruct the same global painting coordinates, without runtime
tiling or per-piece resampling. The baker/integration task owns atlas placement,
resource accounting and native verification; source art alone does not prove
seamless rendering or physical-TV performance.

## Integrated runtime derivation

The approved sources are now integrated. `BakePaintedArenaBorders.py` applies one
85% proportional normalization to each complete wall (852×475 / 644×323), then
stores 99 source pieces in blank guarded areas of the existing three gameplay
pages. The corner is unchanged. Each piece retains one original-neighbour pixel
on all four sides. Runtime placement uses 206 cached quads, with modest uniform
extension to the actual corner faces and consistent endpoint overlap ordering.
No new texture page is allocated; prior sprite pixels and sampling gutters remain
unchanged. `bake-inputs.zip` contains the standalone canonical rebuild inputs.
`bake-verification.json` records the independent rebuild and preservation checks.
See [runtime integration and native checks](../../docs/painted-arena-borders.md).
