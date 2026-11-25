# Clear stone floor sources

The user requested clearer floor stones and actual path borders on 23 November
2026 after the first native implementation looked blurry and like flat stripes.
`approved-reference.png` preserves the selected floor concept. It is generated
concept art, not a native runtime screenshot.

## Original paintings

These assets were made with the built-in `image_gen.imagegen` tool. Exact requests
are saved alongside their masters; output paths and SHA-256 hashes are recorded
in `provenance.json`.

- `field-master.png` reduces the paving to about four staggered courses, with
  strongly readable inset joints, quiet broad stone faces and sparse shallow
  chips/cracks. This avoids squeezing eight rows of weakly defined stones into
  a tiny runtime material.
- `path-master.png` has two large centre slab columns across four courses, with
  separately painted narrow border pavers at both sides. The material keeps the
  cool grey palette but emphasizes stone outlines instead of dense grain.
- `curb-master.png` is one original top-down border stone, with transparent alpha
  outside its chipped rectangular silhouette. It supplies independently placed
  border blocks, so the actual path silhouette can have individual joints and
  slightly uneven edges rather than a smooth painted strip. The first overly
  long candidate remains as `curbstone-long-master.png`; one focused built-in
  correction produced the selected shorter block.

The source paintings remain untouched. `field-preview-*` and `path-preview-*`
are simple whole-image Lanczos downsampling checks at 64, 96 and 128 pixels;
`curbstone-preview-32x16.png` is a proportional alpha-bounds crop/downsample on a
transparent 32×16 canvas. These are previews, not runtime textures or packing
inputs. There is no sharpening filter, repaint, isometric warp or source masking.

## Integration boundary

The source-art task changes only this folder. The separate baker/integration task
owns selected runtime dimensions, repeat collars, premultiplied alpha where
needed by the existing opaque-cell shader, atlas packing, source archive and
resource proof. The primary agent owns floor geometry, native rendering checks
and delivery. Original source quality alone does not establish seamless runtime
joins, readability at gameplay scale or physical-TV performance.


## Selected runtime integration

The selected field is 64×64 with a 120-world-unit repeat; its fewer, stronger
painted stone joints receive more samples per world unit than the previous
96 px/240-unit material. The path uses 128×64 with a 200-unit repeat, doubling
samples across its width. Runtime crops its side margins and places individual
38×20-unit border blocks with 2-unit lengthwise grout gaps, clipped at arena bounds.
The curb is proportionally fitted to 32×16 with transparent margins. Its RGB is
premultiplied for the existing floor-cache coverage shader. It remains one whole
atlas piece, avoiding per-block storage cuts and bright alpha-edge fringes.

The original 256×256 medallion is unchanged. The four existing managed pages hold
317 source pieces (field 8, path 16, medallion 292, curb 1), using 90,100 new texels
including original-neighbour context. All earlier protected sprite/wall/foundation
pixels, their gutters and five metadata files are unchanged. GPU texture bytes
and retained decoded Pixmaps do not grow. `bake-verification.json` includes an
independent byte-identical current rebuild and a legacy-archive rebuild.

```sh
python 3 scripts/BakeEngravedFloor.py --output assets/art --source-output art-source/crypt-floor-clarity
```

[Runtime placement and native checks](../../docs/engraved-crypt-floor.md) record
what was actually verified in the game.
