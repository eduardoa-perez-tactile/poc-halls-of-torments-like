# Open ruined crypt environment, version 2

Original painted artwork authored on 2026-11-23 with built-in `image_gen` for the
user's open arena and coherent isometric landmarks. The selected paintings follow the **ruined crypt with broken tops** and
**character height** direction. The user subsequently reported visible seams
between their independently painted end faces/courses; geometric calibration
does not establish that these fragments tile seamlessly. Chunky aged grey-brown masonry replaces the rejected tall
clean panels and gold pilasters. The floor and seven selected landmarks are unchanged.

Selected wall masters are `wall-*-source.png`; normalized sprites sit beside them.
`generation-records.json` records exact prompt chains, output provenance, hashes
and normalization. Corner masters, prompts and measurements use the
`corner-ruined-*` names. Landmark masters/prompts/provenance remain under `props/`.
Rejected painted candidates are omitted. The geometry guides and floor reference
were ImageGen inputs only; no guide is production artwork or a runtime texture.

## Projection and ground roots

World X projects toward image upper-right; world Y toward image upper-left.
Target image ground slopes are -0.5/+0.5. Each orientation and broken variant was
painted separately. There are no mirrored production facings.

All four wall modules describe 240×36 world footprints (36×240 for Y), with intact
lower masonry and broken upper interiors. Layout repeats are 240 units for X and
225 units for Y at uniform 0.9375 scale, alternating two paintings per axis.
Only the four corners use a 36×36 rough masonry support. There are no repeated
pilasters. Landmarks remain decorative and movement stays open.

Wall canvases are 112×128, ground-centre pivot X=56, with calibrated Y values
94.986655/95.091584/98.545704/98.972445 for X/X-broken/Y/Y-broken. Their projected
footprint width is 108. The corner is 48×112, pivot (24,99), projected footprint
width 44. Scale comes
from projected footprint width, never canvas height. Uncalibrated wall peaks
above ground centre are 117.43/123.04/113.02/113.79 render units. These include the
projected half-span of a 240-unit module; vertical masonry body height is
approximately 70–85 units with broken-top variation. Corner peak is 106.45.
Native views beside the hero establish perceived height.

Generation left small perspective errors. The bottom alpha contour is measured
at threshold 32 across the central 20–80% of each master and fitted by least
squares. `environment.json` records fit samples and source crops. A runtime
wall-only quad shear calibrates the ground plane to 2:1 while retaining upright
verticals: `renderShearY = measuredSlopeDown-targetSlopeDown`.
Values for X, X-broken, Y, Y-broken are respectively
-0.011264, -0.026131, -0.039143, -0.029882. Corner and landmark shear is zero.
The fit also calibrates each source pivot so front-face baselines land at the
same projected 36-unit-thickness ground offset. Ground roots stay fixed; source
and atlas pixels are never warped or repainted.

Offline normalization only crops alpha bounds at threshold 32 (avoiding isolated
nearly transparent noise), scales proportionally with Lanczos, and places art
on a transparent canvas. It never masks alpha, recolours, mirrors, rotates or
warps the selected painting. Review strips/native captures live under ignored `.run/`.

## Rebuild and verification

```sh
python3 scripts/BakeCryptEnvironment.py --output assets/art
```

`canonical-inputs.zip` contains all independent inputs: twelve environment
sprites, four unchanged utilities, 24 exact missile crops, original actor-prefix
metadata, calibration metadata and per-entry hashes. Only Pillow is required;
the baker does not read local exploration caches. Run this final stage after the
legacy gameplay, enemy-attack and magic-missile stages when rebuilding the bank.
It writes only `gameplay-environment.png`, `gameplay.json` and `magic-projectiles.json`.

The environment stays 512×512 RGBA (1 MiB); the gameplay bank stays 6 MiB. No
texture or retained decoded Pixmap is added. Actor pages 0/1 and frames 0–207
remain byte-identical. All 24 missile RGBA crops, timing, canvases and head pivots
are preserved; only atlas rectangles move. Four utilities retain pixels/pivots.
Packing uses two-pixel gutters, one-pixel extrusion, no rotation, complete
nonoverlap checks and saved-RGBA reconstruction validation.

An independent archive-only bake reproduced all three outputs byte for byte.
`verification.json` records output/archive/actor hashes and preserved metadata.
These checks establish reproducibility and resource preservation, not arena
balance or sustained 60 FPS on physical TVs.
