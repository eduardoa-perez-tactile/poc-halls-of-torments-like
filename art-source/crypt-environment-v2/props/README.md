# Original isometric crypt landmarks

Generated on 2026-11-23 with the built-in `image_gen` tool, each with
`transparent_background: true`. The parent reference layout is a style and
projection reference, not a flattened runtime background. Each asset was generated
independently; the two sarcophagi are separately painted facings, not mirrors.
Exact prompts and generation-output paths are retained here.

The kit uses cool grey stone, upper-left lighting and the reference orthographic
2:1 ground-axis direction. Altar and rubble run along image slope +0.5 (world Y);
the gate and `sarcophagus-x` use image slope -0.5 (world X). Natural painted contours
are not asserted to be mathematically exact projection meshes.

Normalization crops to the alpha>=32 bounding rectangle, proportionally downsamples
with Lanczos to the requested bounded canvas, and centers with two pixels below
the source base. It does not alter source alpha within the crop, remove backgrounds,
repaint, mirror, rotate or recolour. Original masters preserve generated alpha.
Low-alpha stray edge pixels outside the selected bounds are excluded by the crop.

`props.json` records the normalized source sizes, estimated contact-centre ground
pivots, measured base-width pixel spans and suggested world X/Y footprints.
These are visual placement/scaling metadata, not gameplay colliders. The parent
baker owns final atlas placement and may refine visual pivots against assembled
floor views. Parent layout remains an open arena.

All seven normalized assets were visually inspected together at runtime source
scale in `contact-sheet.png`; they are coherent readable shapes with complete bases.
Source sprites are never directly loaded as additional runtime textures.
