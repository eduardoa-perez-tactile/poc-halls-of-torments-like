# Startup and retained pixel memory — 24 November 2026

This milestone preserves the current artwork, font appearance, layout, texture
sizes and gameplay. It removes runtime image baking and font rasterization,
preencodes controller artwork, and reloads UI textures from managed files.
The clearer floor from `af332dd` is unchanged.

## Runtime change

- Menu base/guardian/orb/crest layers, panel/button/logo skins, skill emblems,
  outcome titles and the shared font page are exported offline from the original
  LibGDX preparation code. Original painted masters and source fonts remain intact.
- TV textures use ordinary RGBA8888 KTX1 files: one level, one face, no GPU
  compression, no mipmaps, and the original linear filters. Their lossless bytes
  are compressed by APK packaging and uploaded without PNG scanline decoding.
  Upload buffers are released after consumption; managed restoration rereads files.
- Five fonts share the original single 1024² atlas. All glyph metrics, UVs,
  kerning, spaces, missing-glyph behavior and layout metrics are preserved in
  `fonts.json`. Runtime no longer invokes FreeType or retains a PixmapPacker.
- The six existing HTTP image routes read preencoded PNGs. Four reuse normalized
  skin/icon files; only the composite crypt and smaller logo need separate files.
  There is no launch-time compositing, cropping, image resize or PNG encoding.
- The 588 bytes of tiny generated UI swatches/rim and 518,400-byte lobby QR remain
  managed pixel copies. Animation, ambient particles and live text are unchanged.

The converted texture-data path uses LibGDX's existing
[KTX loader](https://raw.githubusercontent.com/libgdx/libgdx/1.14.2/gdx/src/com/badlogic/gdx/graphics/glutils/KTXTextureData.java).
Raw RGBA preserves every texel; this is a CPU/storage change, not lossy GPU art
compression. It does not reduce accounted GPU texture residency.

## Exact-output rebuild

```sh
python3 scripts/bake-ui-assets.py
```

The baker selects JDK 21 and the project's resolved LibGDX 1.14.2 dependencies.
It extracts only four original source files from pinned commit `af332dd` using
`git show` into a temporary compilation directory; it does not switch branches,
create a worktree or use an additional checkout. Native CPU pixel operations run
against a no-op GL interface, without opening a window or allocating GPU storage.
Normal game builds consume checked-in outputs and do not run this tool.

`assets/art/runtime-ui/manifest.json` records original-code revision, input
hashes, dimensions, output hashes and exact RGBA hashes. Its 20 export checks
include shared phone aliases. Verification checks KTX header/payload sizes,
channel order and row orientation against the original in-memory Pixmap bytes.
A fresh bake into ignored directories reproduced every checked-in runtime/phone
file, including metadata, byte for byte.

The exporter also compares every glyph's metrics, UVs and signed kerning against
the original generated font and runs 40 real layouts covering wrapping, blank
lines, spaces, punctuation, absent characters and current menu/stat labels.
Three focused parser/layout tests independently check font-data semantics.

## Measurements

Existing `Television_1080p`: Android 13/API 33, ARM64, 1920×1080, 2 GiB RAM,
automatic GPU backend. The AVD was stopped at the start of this task and was
booted through the existing development helper. No physical device was targeted.

### Retained memory

| Resource | Before | Final |
| --- | ---: | ---: |
| Retained decoded Pixmaps, including QR | 20,245,324 bytes / 19.3074 MiB | 518,988 bytes / 0.4950 MiB |
| Reduction | | 19,726,336 bytes / 18.8125 MiB / 97.44% |
| Accounted GPU textures including QR | 33,516,364 bytes | 33,516,364 bytes |
| Texture headroom under 32 MiB | 38,068 bytes | 38,068 bytes |
| Post-segment lobby process PSS | 86,495 KiB | 59,007 KiB |
| Post-segment results process PSS | 86,319 KiB | 60,321 KiB |

PSS is an OS process estimate, distinct from explicit pixel/texture accounting.
These short samples do not prove a long-run leak result or attribute every PSS
change to one allocation. KTX loading temporarily uses Java/direct upload buffers;
those are not retained decoded Pixmaps. GPU driver/framebuffer costs are excluded
from the 32 MiB asset-texture calculation.

### Startup

`ProfileStartup.createTotal` measures the LibGDX create callback, not launcher-click
to first presented frame or Android process initialization. File caches are warm.
No gameplay or network work was deferred to make the first menu appear sooner.

To reduce Android compilation-state differences, the old and final debug APKs
were each installed and compiled with the same per-app `cmd package compile -m
speed -f` setting on the emulator, then restarted three times:

| Build | Three create callback samples (ms) | Median (ms) |
| --- | --- | ---: |
| Original runtime preparation | 972.30, 951.24, 953.20 | 953.20 |
| Final ready-to-upload files | 896.74, 857.99, 853.84 | 857.99 |

The observed median decreased by about 10%. Host/GPU scheduling remains
uncontrolled: even unchanged gameplay-art stages varied substantially. Later short
lobby/results captures reached 250.62/250.19 ms; these faster samples are not used
as a portable speedup claim. Physical-TV startup still needs user observation.

A bounded method-sampling trace of the file-backed PNG intermediate build found
about 366 ms inclusive in native PNG loading during that observed startup.
The final UI upload containers eliminate that decoding for the converted assets.
All assets are still prewarmed at startup. A PNG-only intermediate and Java-gzip
container experiment did not give a consistent startup improvement; neither is
the final TV texture path.

The storage tradeoff is a larger debug APK: 53,154,350 bytes before and 62,044,475
after. Original source paintings remain packaged as before; no source art was
removed to offset the new runtime exports. This adds disk/download size, not
resident texture memory.

## Native checks and limits

- Desktop classes and Android debug assembly passed with JDK 21. Eight focused
  Java tests passed across font layout, menu particles and the existing unused
  outcome-loader lifecycle; font checks were rerun after the upload-path changes.
- The six live phone image routes matched the old Android-served images' decoded
  RGBA pixels exactly. PNG compression bytes can differ across Java/Android.
- Home text/button crops for Play, How to Join, Settings, Exit, tagline and player
  caption matched the original Android rendering pixel for pixel. Full-screen
  comparisons include the deliberately moving dust and orb-brightness animation.
- Brief native checks covered Home, Help, Lobby/QR, sample Victory, Run Details,
  sample Defeat, return to Lobby, background/resume and the restored QR.
- Profile-only Insert reloads owned UI files through `Texture.load(existingData)`
  into the same handles. Final Defeat rendered correctly afterward; texture bytes
  remained 33,516,364 and retained Pixmaps 518,988. This exercises managed file
  decoding/upload, not a forced OS GL-context loss. It avoids
  `Texture.invalidateAllTextures()` without a real context loss, which would
  abandon old handles.
- No fatal app exception was recorded. No whole-run gameplay, physical-phone or
  sustained physical-TV performance test was performed; simulation, floor and
  renderer rules were not changed.

Before source: `af332dd`; final runtime source: `6a30edf`.
Before APK SHA-256:
`7e6d5216ec28d0e3a97392032121a13d2c730b120032c39e2a1ccdeb15725b1e`.
Final APK SHA-256:
`b9ebc9065bd7ec388474396742bd540499508c2ce04bd759b0da9c9951478ac3`.

Ignored evidence is under `.run/startup-art-memory/`: `before/`,
`before-compiled/`, `after-upload/`, `final/`, `after-final/`, method-sampling
trace/SQL output and fresh-bake proof. Controllers/loggers/profilers and temporary
verification scripts/tools/APK copies are stopped or removed. The TV emulator and
development relay remain running at ordinary unprofiled Home for handoff.
