# Separate epic crypt gate — 25 November 2026

The user requested a more epic gate that remains separate artwork, with its
shoulders painted to fit the fixed north-wall position. The approved perimeter
and four painted corner joins are preserved.

`epic-design-proposal.png` is the in-context design reference. `gate-master.png`
is the original transparent ImageGen output; `gate.png` is its single proportional
1280-to-256-pixel Lanczos normalization, preserving generated alpha. The exact
prompts are `proposal-prompt.txt` and `asset-prompt.txt`. The native transparent
guide and its camera/pivot geometry are retained alongside them. Both images were
created with the built-in image-generation tool from the project's existing
original artwork; no third-party gate art was imported.
The earlier lower-arch concept used as the epic proposal's input is preserved
as `earlier-gate-reference.png`, with its exact prompt beside it.

The new pointed stone arch, closed iron-bound wooden doors, worn brass fittings
and short stepped wall wings are one independent RGBA painting. It stays at
world `(0,912)` and draws after the rear wall, before actors. It introduces no
collision, doorway interaction or gameplay change. Uprights remain vertical;
the ground-root calibration follows the existing 2:1 wall plane.
The cached draw handoff stops inside the outer wall wings, leaving the original
wall underneath and omitting the painting's flat terminal endcaps. The complete
uncropped painting remains intact in both the source and atlas storage.
An eight-source-pixel cached opacity handoff blends each outer wing into that
wall without changing the opaque gate or adding any frame-loop geometry work.

Storage cuts are offline atlas storage only. Runtime vertices and UVs are cached
once and borrow existing managed gameplay pages. The superseded gate frame 219
is retired from rendering; its original metadata and pixels remain in the
canonical input archive and original environment sources. The bake explicitly
permits reuse of only that frame's exclusive guarded rectangle, while protecting
all other frames, attacks, projectiles, chests, floor/border paint and the recently
approved corner joins. No texture dimensions or GPU texture bytes increase, and
no decoded gameplay Pixmap is retained.

`bake-report.json` records source/output hashes, storage bounds and the protected
pixel checks. `bake-inputs.zip` contains the exact pre-gate texture baseline and
source inputs. Rebuild to an isolated directory with:

```sh
python3 scripts/BakeGateArt.py --reclaim-legacy-gate --output /path/to/rebuilt-gate-art
```

Implementation and native-render verification are recorded in
[`docs/painted-arena-borders.md`](../../docs/painted-arena-borders.md).
