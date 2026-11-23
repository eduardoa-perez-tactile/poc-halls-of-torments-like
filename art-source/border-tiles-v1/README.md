# Integrated crypt border tiles

Standalone tile assets for review before game integration. The game does not load this directory; `assets/`, Java, phone controls, collision and map construction are unchanged.

The nine RGBA PNGs in `tiles/` contain one interior, four straight borders and four corners. They come from one new continuous painting, including the masonry, coping, floor footing and paving. The export normalizes the camera to exact 2:1 projection, removes floor illumination drift and supplies periodic texture collars at repeated joins. It does not paste the old wall or floor sprites into the new artwork. The original image and exact built-in ImageGen prompt are retained in `painted-master.png` and `provenance.json`.

## Geometry

`tiles.json` is authoritative. The high-resolution floor footprint is 240 × 120 pixels, with a shared 304 × 168 transparent canvas and a `[152, 2]` anchor at the far footprint vertex. Tile `(i,j)` has screen root `((i-j)*120, (i+j)*60)`; subtract the anchor to obtain the PNG's top-left. The border cell includes the wall/footing. The foundation skirt can extend beyond the diamond, so do not discard the canvas or trim offset.

The full-size atlas is 1024 × 512 RGBA (2 MiB). The half-size atlas is 512 × 256 RGBA (0.5 MiB), with a 120 × 60 floor footprint. Both use two-pixel packing gutters, including one pixel of RGBA edge extrusion. Their JSON records preserve source sizes, trim offsets and scaled anchors. These are file assets, not textures currently resident in the game.

## Rebuild and inspect

Use the installed Python/Pillow environment; no image-generation service is needed for a rebuild:

```sh
python3 scripts/BakeBorderTiles.py
python3 scripts/PackBorderTiles.py --package-output art-source/border-tiles-v1/atlas
python3 scripts/ValidateBorderTiles.py
```

Review media stay under `.run/border-tiles-review/`: the contact sheet, actual 3×3 / 8×6 / 20×15 PNG assemblies, a map reconstructed from the smaller atlas, enlarged straight/corner crops and QA reports. Every map is rendered from the exported PNGs or reconstructed atlas regions. The painted master is not used as a map background. Map JSON records every tile placement.

## Scope and integration limits

This kit closes rectangular rooms of at least 2 × 2 cells. Concave corners, isolated one-cell rooms, doors, broken boundaries and additional landmark variants are not included. The open floor repeats one texture. The finished border reads as a low wall; approve its apparent height in the assembled map before integration.

Runtime collision, actor occlusion and texture residency have not been changed or tested. A later integration must align the playable edge with the visible inner footing, account for the cell reserved by each border and verify sorting against foreground walls. The smaller atlas is a bounded candidate, not permission to exceed the combined 32 MiB budget; recount actual resources when integrating it.
