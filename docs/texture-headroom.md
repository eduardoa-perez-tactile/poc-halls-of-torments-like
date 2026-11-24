# Texture headroom — 24 November 2026

The shared font atlas now occupies one 1024×768 RGBA texture instead of
1024×1024. This recovers exactly **1 MiB** without resizing or repainting any
glyph. Other artwork, text metrics, filters, font-page count and gameplay stay
unchanged. The existing file-backed managed texture path is retained.

| Accounted resource, with lobby QR | Before | After |
| --- | ---: | ---: |
| Shared font page | 4,194,304 bytes | 3,145,728 bytes |
| Combined GPU texture data | 33,516,364 bytes / 31.9637 MiB | 32,467,788 bytes / 30.9637 MiB |
| Headroom under 32 MiB | 38,068 bytes | 1,086,644 bytes / 1.0363 MiB |
| Retained decoded Pixmaps | 518,988 bytes | 518,988 bytes |

These are bounded uncompressed texture-storage calculations, not total graphics
driver/process memory. There is no claim of an FPS improvement from this change.

## Packing and preservation

`PackUiFonts.java` uses LibGDX's existing SkylineStrategy offline, sorting the
rectangles deterministically by height, width, font name and glyph ID. It copies
all 405 visible glyph blocks and their original two-pixel surroundings without
rotation, trimming or resampling. Blending is explicitly disabled for the final
copy so RGB values under transparent pixels survive unchanged.

Only glyph `srcX`, `srcY` and page coordinates change. All 410 glyph records keep
their original order, sizes, offsets, advances, fixed-width flags and signed
kerning. Font ascent/descent, scale, line height, spaces, missing-glyph behavior
and wrapping data remain unchanged. Empty layout glyphs still draw no pixels.
One shared page means no new font texture binds or rendering passes.

Verification checks:

- Every relocated RGBA pixel and two-pixel gutter matches its original block:
  517,885 copied pixels, with no overlapping stored rectangles.
- An independent comparison against the previous checked-in atlas confirms all
  glyph blocks, metrics and kerning, separately from the packing helper.
- All 40 existing real-font layout comparisons pass against the original
  FreeType-generated fonts, including wrapping and kerning examples.
- A fresh `scripts/bake-ui-assets.py` run into ignored directories reproduces all
  runtime/phone outputs byte for byte. The only changed runtime artifacts are
  `font-page-0.ktx`, `fonts.json` and their manifest; phone files are unchanged.
- Five focused font layout/bounds tests pass, including page edges, the complete
  two-pixel gutter, invalid pages, integer overflow and zero-height spaces.

The JSON declares 1024×768 dimensions. The loader also accepts bounded old-schema
1024×1024 files for compatibility; every loaded glyph is checked against the
actual page and gutter bounds. GPU upload data is released after loading as before.

## Native visual comparison

Before/after debug APKs ran on the existing Television_1080p emulator: Android
13/API 33, ARM64, 1920×1080. A profile-only END-key fixture uses an opaque black
background and the existing pixel texture, with no new texture allocation. It
renders all five fonts at scales 1.0 and 0.73, with fractional positions, multiple
colors, kerning pairs, digits and punctuation. The same fixture code was present
in both capture APKs.

- Play, How to Join, Settings, Exit, tagline and player-caption crops on Home
  are pixel-identical to the original atlas rendering.
- Across the full deterministic 1920×1080 font fixture, 25 pixels differ by at
  most one RGB level (1/255), all in fractional-scale sampling. There is no
  geometry/layout drift. The source glyph texels are exact; the tiny screen
  difference is normalized-UV/filtering roundoff from the smaller page.
- The same 25-pixel/one-level bound holds after managed file reload. There are
  no additional differences introduced by reload.
- Native Home, Help and Lobby/QR remain readable and correctly positioned.
  Font fixture/menus use the normal Scene2D fonts and shaders.
- The reload diagnostic reports 32,467,788 GPU texture bytes and 518,988 retained
  Pixmap bytes afterward. No fatal app exception was recorded.

This establishes visual parity on this emulator within the explicitly measured
one-level filtering tolerance. It does not prove byte-identical rasterization on
every TV GPU. No new shader, changed glyph art or changed font sizing is used.
No long gameplay test or physical-TV check was performed.

Desktop classes and Android debug assembly passed with JDK 21. Shared-checkout
results/controller work was kept out of this milestone's commits. The temporary
font fixture is profiling-only and is closed for ordinary play.

Before capture APK SHA-256:
`4ebcff73e485e250088dfae079d9f2b43e8dae0ecbc046dc7172726a80a0801d`.
After capture APK SHA-256:
`0d926b6485edc6f9a7f0f0dc7d56f482d8c53c30873d43333a6a3e966f4b5ef3`.

Ignored proof, screenshots, reload logs and build/test output are under
`.run/texture-headroom/`. Temporary capture/rebuild files are removed at handoff;
the emulator remains available to the results-screen chat.
