# Elite loot chest

This original version is retained for provenance. The brighter user-approved
replacement and current packing source are in [v2](v2/README.md).

Original chest artwork generated on 2026-11-25 using the built-in ImageGen tool.
The exact generation prompt is in `prompt.txt`; `generated-master.png` retains
the unmodified RGBA output. The existing crypt environment atlas was inspected
for its cool stone/iron palette and painted game-prop style before generation;
no existing artwork was provided as an edit target or overwritten.

`chest-runtime.png` is a 46×38 Lanczos reduction of the master crop. Crop bounds
use alpha ≥32 to exclude nearly transparent generation specks; alpha inside
the crop is preserved. The sprite has a measured ground-centre pivot at
(23, 30.5), leaving the forward base below its world root. The renderer uses
44 logical pixels of source height (about 53×44 drawn pixels), depth-sorted
with the world actors. The closed lid and large iron bands keep it readable.

`scripts/PackEliteLootChest.py` adds the frame and `elite-chest` region to
`assets/art/gameplay.json` and uses spare space in the already resident
512×256 `gameplay-grave-king.png` page. The placement is (464, 4), with a
one-pixel edge extrusion inside a two-pixel reservation. All eight existing
skull frames retain their pixels and sampling gutters. Supplemental attack,
projectile, border and floor metadata were checked by file identity; their
local page numbers do not reserve this skull page.

`pack-report.json` records crop, placement, hashes and exact RGBA preservation
outside the new 48×40 patch. The patch adds no textures, GPU texture bytes or
retained runtime Pixmaps. Existing art is unchanged.
