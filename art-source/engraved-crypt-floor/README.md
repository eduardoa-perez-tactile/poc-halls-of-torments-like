# Engraved crypt floor

The user approved `approved-proposal.png` and requested its implementation on
2026-11-23. That image is a generated floor proposal over the native camera view,
not evidence that the runtime has been implemented. The original proposal's tool
output and selected runtime masters are recorded in `provenance.json`.

Three original production assets were made with the built-in `image_gen.imagegen`
tool. `medallion-master.png` is an orthographic top-down circular carving, with
opaque cool-grey stone inside and actual transparent alpha outside. The double
inset stone ring surrounds the shallow four-point Gothic flourish. It has no
platform side faces, stairs, gold, glow, UI, character or external cast shadow.
`field-master.png` supplies quiet smaller stone flags; `path-master.png` supplies
broader, slightly lighter dressed paving with narrow rows of smaller edging
stones along both sides. These are separate materials so paths
can follow the authored arena layout rather than being a flattened screenshot.
The exact production prompts are `medallion-prompt.txt`, `field-prompt.txt` and
`path-prompt.txt`. The selected path is one focused built-in ImageGen correction
after native review found that plain slab bands lacked visible dressed edging.
Its previous master/prompt remain as `path-master-v1.png`/`path-prompt-v1.txt`;
the canonical archive selects only the revised path. Source masters are retained
without destructive painting edits.

## Runtime normalization

The baker reduces the complete medallion once to 256×256 with Lanczos, then adds
one transparent context pixel on all four sides. `medallion.png` is therefore
258×258 with `contentRect=[1,1,256,256]`. It remains a true top-down circle; runtime
projects it onto the ground plane. There is no offline isometric warp or quadrant
mirroring. Minor source alpha below 32 in otherwise empty packing cells may be
omitted; all significant original paint is retained exactly once.

The field and path normalize once to 96×96 and 64×64. A periodic boundary collar
samples the original centre of each painted material through smoothstep weights
over 14% of its repeat period. The field repeats in both axes. The path repeats
only along Y, preserving its authored narrow edging rows at the left/right sides;
its X context clamps to the original edge pixels. This removes residual generator
repeat drift without inventing stone geometry. One original-neighbour pixel is
then retained on each edge, producing `field.png` 98×98 and `path.png` 66×66. Each
`contentRect` excludes that collar. `wrapAxes` records the actual repeat axes, so
repeat edges filter the same source neighbours as the following material repeat.

## Bounded atlas stage

```sh
python3 scripts/BakeEngravedFloor.py --archive art-source/engraved-crypt-floor/bake-inputs.zip --output .run/legacy-floor-rebuild \
  --source-output art-source/engraved-crypt-floor
```

Run this stage after `BakePaintedArenaBorders.py`. `bake-inputs.zip` contains the
complete selected baseline, all existing metadata and the three untouched art
masters. A standalone rebuild requires no generator call. It reuses guarded blank
areas of the three gameplay pages plus the already resident 512×256 floor page:
no page dimension, file-backed management or GPU byte count changes.

There are 364 source pieces: field 48, path 24 and medallion 292, with 24×8 storage
cores. Every core retains exactly one actual source neighbour on all four sides.
The new pieces need no further outer extrusion: runtime UVs draw only their
cores, so linear filtering reads the retained source context. Existing sprite,
attack, projectile, wall and foundation rectangles keep their original two-pixel
protected gutters. The five old metadata files remain byte-identical. All old
protected pixels are compared against the baseline before writing outputs.

`bake-verification.json` records exact source coverage, context copies, resource
limits, output hashes and an independent archive-only rebuild. Source/bake checks
do not prove native visual integration or physical-TV performance; those belong
to the runtime handoff.


The later [clarity pass](../crypt-floor-clarity/README.md) supersedes these field
and path materials in the current game. This archive remains a byte-identical
historical export; its medallion master/normalized painting are preserved in the
current pass. Use the clarity source for current runtime rebuilds.
