# Integrated arena borders

The arena now renders the approved integrated floor/border tiles from `assets/art/arena-border.png` and its metadata. Runtime pixels are copied byte-for-byte from the source bank's 512 × 256 half-size atlas. The new file-backed managed page adds 524,288 texture bytes and retains no decoded Pixmap.

## Bounds and rendering

The simulation is unchanged: `ArenaWorld` still owns a 2400 × 1800 world-unit arena, the original movement clamps, spawns, collision, timings and combat. The 20 × 15 fighting-floor cells remain interior tiles. A closed 74-cell border occupies the first row of the existing cosmetic padding, making the visible room 22 × 17 cells without taking space from the playable floor. The other 742 padding cells form a dark backdrop. All 1,116 quads remain in one static managed mesh reused by solo and split-screen cameras.

The export uses a top-down PNG anchor at the far diamond vertex. `BorderTileArt` converts trim offsets to renderer coordinates, and `ArenaTileLayout` maps the export's i/j axes to the game's world -Y/-X directions. Exterior tiles render first; the room follows in far-to-near order so the outside floor cannot cover the foreground foundation skirts. The former scattered wall/corner props are removed; the remaining 14 landmarks keep their original positions. Cameras, HUD and shared UI are unchanged.

The decorative border is outside the existing playable bounds. This preserves movement and enemy behavior rather than adding new collision shapes or reducing the arena.

The cache uses a small GLES2 coverage shader for these opaque tiles. Ordinary alpha blending produced dark diagonal hairlines when linear filtering sampled the binary shared-edge masks at fractional scale. The shader recovers sampled color from alpha, discards nearly empty coverage and draws the owning cell opaque. It changes neither the atlas pixels nor other sprite rendering, adds no textures and is owned/disposed with the managed cache.

## Rebuild

```sh
python3 scripts/PackBorderTiles.py --runtime-output assets/art
```

This exports only the half-MiB runtime page and validated runtime metadata. The high-resolution atlas and source painting remain outside runtime assets. See [the source bank](../art-source/border-tiles-v1/README.md) for original prompts, tile exports and pixel-level assembly checks.

## Verification

Focused GL-free checks validate all nine regions, trim/anchor placement, source dimensions, the half-MiB limit and sampling gutters. Layout checks cover the preserved fighting floor, all four corner/edge facings, cache counts and padding coverage at solo/co-op camera limits. Desktop compilation and Android debug packaging use JDK 21.

All nine focused checks passed, along with desktop compilation and the Android debug build. Native GL readback at the normal solo camera and all four movement-limit corners in solo/co-op found **zero partial/transparent pixels across 8,755,200 tested floor pixels**. The initial uncorrected center view contained 44,958 partial-alpha pixels; the corrected view contains none. This also covers four-way floor junctions at fractional scale.

Brief actual-APK checks on `Television_1080p` (Android 13 / API 33 TV ARM64, 1920 × 1080) used ordinary phone-protocol movement and the existing F10 profiling fixture. Solo and split-screen screenshots show all four corner orientations and long wall runs with no dark tile grid, visible gaps or doubled borders. The fixture's `HUD PREVIEW` boss strip is a sample overlay, not a boss encounter. The screenshots and raw logs remain under ignored `.run/border-runtime-check/`; native readback evidence is in `.run/border-native-check/`.

Measured texture storage including the live QR was **33,418,060 bytes (31.8699455 MiB)**, leaving **136,372 bytes** below 32 MiB. Retained decoded Pixmaps stayed at **20,245,324 bytes**, with no new retained border Pixmap. One steady solo window reported CPU submission p95 1.60 ms / frame interval p95 18.41 ms; the last co-op window reported 3.31 / 18.27 ms. These brief emulator samples do not establish balance or sustained physical-TV 60 FPS; the frame-interval budget remains unproven.

No changes were made to `ArenaWorld`, progression, encounter timelines or phone gameplay controls. Synthetic verification phones were closed and the app restored to a fresh Home screen; the existing emulator and LAN relay remain running for handoff.
