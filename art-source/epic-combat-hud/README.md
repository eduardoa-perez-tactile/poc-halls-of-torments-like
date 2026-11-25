# Wide boss header and ornate health orbs

Approved and implemented 2026-11-24. All artwork was created with the built-in
`image_gen` tool. The approved screenshot is a design reference only; runtime
loads separate original painted frames and live health/name layers.

- `approved-proposal.png`: selected wide, face-free header and lower icon rows.
- `boss-frame-source.png`: original generated master, genuine alpha both outside
  the metal and through its reservoir.
- `orb-frames-source.png`: final compact gold and silver/teal rim master, genuine
  alpha outside and inside the two frames. The earlier extraction had oversized
  finials; the correction shortened them to fit the existing square atlas cells.
- `boss-frame-512x96.png`, `orb-gold-128.png`, `orb-teal-128.png`: normalized runtime
  sources, produced by `scripts/NormalizeEpicHudArt.py`. Only crop, resize and
  placement are applied, without masking, recoloring or background removal.
- `prompts.txt`: exact extraction/correction prompts and approved mockup prompt.

The boss master crop is (36,465)–(1448,589), placed at (4,8) with size 504×80 on
the 512×96 canvas. The final 1344×86 game destination restores its shallow screen
proportions. The live track is x30, y34, width452, height28 in bottom-left source
coordinates, behind the metal's rounded transparent opening. The frame is
centered at screen x960. The live name is independently centered above it.

Orb crops are 800×800, centered at source (454,447)/(1318,447), then reduced to
128×128. The glass openings register to the existing (64,64) orb centers.
The short engraved crest/scrollwork is contained within each cell.

All three sprites replace existing cells in the managed 512×352 HUD atlas.
The original glass/blood/reflection, skill emblems, Dodge art and crimson fill
are preserved. No standalone frame texture, retained decoded master, or baked
HUD screenshot is loaded. GPU texture use is unchanged.
