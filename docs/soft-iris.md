# Soft atmospheric iris

The user approved the solo/split-screen visual prototype on 24 November 2026
and authorized its implementation as a cosmetic atmosphere effect. The later
split-screen correction uses one wide oval across the outer TV border, with
the HUD and shared menus drawn above it. Solo retains its original treatment.
There is no exploration state or change to targeting, combat, enemy visibility,
spawning, timing, phone controls or cameras.

The original solo settings remain a 72% clear opening and 94% maximum edge
darkness. Co-op uses the user's selected 76% opening and 82% darkness from the
corrected preview. For coordinates normalized to −1…1 on the full TV axes,
opacity is `darkness × smoothstep(opening, 1.08, length(position))`. The shade
remains RGB `(7, 11, 20)`. Both parameters are set each draw so returning to solo
restores the original values. The ellipse follows screen proportions rather
than a world radius. The rejected two narrow ovals and their seam-side darkness
are removed. It is static and does not pulse, breathe or follow an actor's feet.

`SoftIris` owns one managed shader and one fixed indexed quad. `ArenaRenderer`
finishes both world cameras, disables their scissor and restores the full
viewport, then draws the iris once before the HUD. Choice ceremonies and pause UI
retain their existing draw order above the arena. Results and menu backgrounds
do not use the effect.

The pass adds one draw in either solo or co-op, tracked in the existing world
draw counters. It allocates no textures, framebuffers, decoded Pixmaps or
per-frame objects. The fixed mesh and managed shader support GL context
restoration. The combined 32 MiB texture ceiling is unchanged.

## Original per-camera verification

Desktop classes and the Android debug APK build with JDK 21. The managed shader
compiled and rendered on the existing Television_1080p emulator (Android 13/API
33, ARM64, 1920×1080). Solo and split-screen captures show the native boss header,
health orbs, Dodge, skill ranks/overflow and shared XP above the shading.
Quiet central-court captures also check the selected oval in both camera modes;
remote Pause/Resume and the two-player loot ceremony remain clear above it.

Eight-second populated boss fixtures, with two seconds of warm-up and real
controller heartbeats, gave these complete in-window report samples:

| Observation | Before, co-op | Iris, co-op | Iris, solo |
| --- | ---: | ---: | ---: |
| Frames in report | 301 | 301 | 301 |
| Arena render callback p95 | 6.75 ms | 6.53 ms | 4.44 ms |
| Frame interval p95 | 18.65 ms | 18.68 ms | 18.62 ms |

The co-op draw-count minimum rises from 54 to 56, consistent with one new pass
per pane. Each sample reports the same 33,221,452 texture bytes (31.6824 MiB,
including the resident lobby QR) and 518,988 retained Pixmap bytes. The iris
adds no texture storage; its quad contains only 32 vertex bytes and 12 index
bytes. These texture counts do not represent total graphics-driver memory.

The runs have different random seeds and changing combat populations. These
short observations establish rendering and bounded resources, not an isolated
GPU cost or an improvement in frame time. No GPU timer was measured; emulator
frame intervals already exceed 16.7 ms in the baseline. Physical-TV readability
and sustained 60 FPS remain subject to user playtesting.

Ignored screenshots, APK hashes, metadata and measurement logs are under
`.run/soft-iris/`. The existing emulator, phone-reachable LAN relay and ADB
forward are retained for handoff; synthetic controllers are closed afterward.

## Outer-border correction

Desktop classes and the Android debug APK compile with JDK 21 after the
correction. Native solo/split-screen central-court captures, a populated co-op
boss capture, remote Pause/Resume and the loot ceremony all render correctly.
The central divider is clear and the HUD/ceremony remains above the iris. Solo
retains its original shader parameters and shading; a 260,000-pixel static-floor
crop matches the original capture apart from eight isolated changed pixels.

An eight-second co-op boss sample after two seconds of warm-up reports 301
frames, arena callback p95 5.53 ms and frame-interval p95 18.47 ms. Texture and
retained-Pixmap storage remain 33,221,452 and 518,988 bytes respectively. The
shared checkout included the independent enemy work during these captures, so
these observations do not isolate an iris performance change. Only the iris
rendering and documentation changes are included in the iris milestone.

Captures, metadata and observations for the corrected pass are under
`.run/soft-iris-outer/`; the original measurements above describe the
superseded per-camera implementation. The emulator is left at the lobby,
synthetic phones are closed, and the saved `192.168.14.248:8787` join address
and existing `tcp:58408` ADB forward are retained. Sustained physical-TV
performance remains unverified.
