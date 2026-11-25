# Approved brighter elite chest

The user approved `approved-in-arena-mockup.png` on 2026-11-25. Its exact
generation prompt is retained as `approved-mockup-prompt.txt`. The existing
chest sources in the parent directory are preserved as the original version.

The built-in ImageGen tool edited the approved mockup to isolate the same chest
as a transparent production sprite. `extraction-prompt.txt` is the exact prompt;
`generated-master.png` is the unmodified generated RGBA result. The chest keeps
the approved substantial warm gold bands/corners and large keyhole latch,
mahogany lid, midnight metal body and fine glowing closed-lid seam. The ground
pool, rim, glints and wisps are excluded from the sprite and rendered separately.

`chest-runtime.png` is a 46×43 Lanczos reduction after an alpha ≥32 crop at
(226, 235)–(1028, 985); generated alpha inside the crop is preserved. The native
46-pixel sprite was visually inspected for silhouette, gold trim and large latch.
Its measured ground-centre pivot (23, 35) lies at 81% of source height, above
the forward corner. At the authored world height of 52 it draws about 55.6×52,
18% taller than the original 44-high chest.

`scripts/PackEliteLootChest.py` now defaults to this approved source. It replaces
only frame 232 / region `elite-chest`, at (464, 4), on the already resident
512×256 `gameplay-grave-king.png` page. The one-pixel extrusion remains inside
a two-pixel reservation. The union of old/new sprite patches is cleared before
packing, so changing source height cannot leave stale rows. All eight skull
frames and their gutters remain unchanged. Supplemental attack/projectile/
border/floor page file identities are checked before writing.

`pack-report.json` records the exact source and approved-mockup hashes, placement,
sampling reservations and outside-patch RGBA hash. Runtime texture dimensions,
GPU texture memory and retained decoded Pixmap memory do not grow.
