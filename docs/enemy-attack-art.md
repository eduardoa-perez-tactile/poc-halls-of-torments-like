# Authored enemy attacks and aura sprites

The approved southwest Wraith dash, Brute slam and Cultist release proofs are now a four-facing sprite bank. Three additional views per actor were authored in whole-sheet passes with the built-in ImageGen tool, using the exact shipped directional idles and approved southwest poses as references. The original southwest frames are unchanged. First/last poses in every facing use exact shipped idles, with one fixed 96×128 canvas, (48,112) root and shared scale per strip. Character images are neither mirrored nor rotated.

The Wraith sequence lasts 1300 ms, Brute 1450 ms and Cultist 1440 ms. In the new rear Brute views, generated pose 5 supplies both contact slots 4/5 so the fists reach the floor at the 750 ms strike rather than remaining in the descending pose. Minor generated costume/pose variations remain; these are authored sprites rather than a skeletal rig.

The original neutral eight-slot aura loop is reused with ordinary batch tinting. A shared 1.075 vertical correction brings its approximately 2.15:1 generated ellipse to the arena’s 2:1 ground plane. Its horizontal ground radius is 47 source pixels, separate from the conservative whole-sprite culling radius. Cached back/front regions split at the ground root so rising wisps and foreground rim can draw around bodies. No complex aura shader or particle framework is used.

## Fixed memory and reproducibility

`EnemyAttackArt` borrows the two existing managed actor textures. `enemy-attacks.json` indexes **89 unique trimmed regions** (104 source slots before idle/duplicate aliasing) in unused actor-bank rectangles; it allocates **zero new textures and zero retained decoded Pixmaps**. Existing gameplay indices, rectangles, artwork and two-pixel sampling gutters remain unchanged. The environment page is reserved for the separate projectile pass.

Original whole sheets, exact prompts, editing reference canvases, southwest provenance, normalization/selection parameters, fixed-root canonical PNGs and SHA-256 source index are preserved in `art-source/enemy-attacks-canonical.zip`. Rebuild the original three-page bank with `scripts/BakeGameplayAtlas.py`, then run:

```sh
python3 scripts/BakeEnemyAttackArt.py --base-art /path/to/rebuilt-art --output /path/to/rebuilt-art
```

The stage may also run against the checkout’s current bank; it is idempotent. It preserves original frame/gutter pixels, clears unused actor pixels, packs/extrudes the additions without rotation, verifies source hashes and checks every saved-page reconstruction. Run any projectile overlay stage afterward; that pass uses the separate environment page.

## Checks

An archive-driven overlay rebuild reproduced both actor PNGs and metadata byte for byte. Focused GL-free checks cover all four facings, fixed-root/idle return, authored attack timings, aura closure, frame/page bounds, no overlap with existing artwork or other additions, and malformed metadata rejection. Generated normalized review boards remain outside runtime assets under `.run/art-exploration/enemy-attack-integration-v1/sources/`. Runtime gameplay/preview verification is reported by the integration handoff; these asset checks alone do not establish balance or physical-TV performance.
