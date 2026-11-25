# Painted wall join repair — 25 November 2026

The user rejected another geometry-only corner fit and approved
`approved-proposal.png` ("that's better"). The selected treatment wraps the
existing broad stone courses around each turn without a separate corner post,
pedestal, foot or doubled endcap. The low ruined wall direction is preserved.

## Original art and provenance

The proposal and all four directional paintings were generated with the built-in
ImageGen tool. No external artwork was downloaded. `proposal-prompt.txt` and
`asset-prompts.json` contain the exact prompts and generated-file provenance.
The proposal was an edit of native outside/inside corner views; the production
edits used the four transparent native wall-only guides in `guides/`, plus the
approved proposal as the style reference. `guide-geometry.json` records their
camera, ground roots and arm cut positions.

Canonical raw masters are preserved as `source/masters/*.png` inside
`bake-inputs.zip`, alongside the exact pre-append atlas baseline, normalized source
paintings, prompts and proposal. The loose directional PNGs are the selected
192×144 runtime-source derivatives. Each master receives one proportional
whole-canvas Lanczos resize; distant low-alpha generator noise is trimmed only
outside the alpha-32 bounds plus a two-pixel antialiasing margin. There is no
mirroring, rotation, material tiling or repainting in the baker.

The four facings are separate paintings. `pivot-calibration.json` records measured
ground-contour corrections against the native guides, retaining the same
2.083333 logical units per source pixel. Uprights remain vertical and source
pixels are unchanged after normalization. `corners.json` is the calibrated source
manifest.

## Resident storage

`BakeCornerJoins.py` appends 88 storage pieces into spare guarded space in the
four already managed gameplay pages, including the resident Grave King page.
It adds **zero GPU texture bytes**, allocates no new page and does not change
existing metadata, actors, attacks, floor art, chest artwork or sampling gutters.
Every old frame rectangle with its two-pixel gutter and every pre-existing
nonzero RGBA pixel is protected. Pieces keep a one-pixel original-neighbour
collar and two extruded gutter pixels.

`bake-report.json` records exact output/source hashes and preservation checks.
The saved pieces reconstruct all four normalized RGBA paintings exactly. A
separate archive-only rebuild reproduced all four PNG pages and
`painted-corners.json` byte for byte.

Rebuild to an isolated output directory, without recapturing or replacing the
canonical archive:

```sh
python3 scripts/BakeCornerJoins.py --output /path/to/rebuilt-corner-art
```

Integration behavior and native verification are recorded in
[`docs/painted-arena-borders.md`](../../docs/painted-arena-borders.md).
