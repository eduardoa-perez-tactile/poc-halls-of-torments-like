# Soft atmospheric iris

The user approved the solo/split-screen visual prototype on 24 November 2026
and authorized its implementation as a cosmetic atmosphere effect. Each camera
has a constant feathered oval, with the HUD and shared menus drawn above it.
There is no exploration state or change to targeting, combat, enemy visibility,
spawning, timing, phone controls or cameras.

The selected prototype settings are a 72% clear opening and 94% maximum edge
darkness. For coordinates normalized to −1…1 on each viewport axis, opacity is
`0.94 × smoothstep(0.72, 1.08, length(position))`. The shade is RGB `(7, 11, 20)`.
The ellipse follows screen proportions rather than a world radius. Both
split-screen panes use their own centered oval, including their seam-side edges.
It is static and does not pulse, breathe or follow an actor's feet.

`SoftIris` owns one managed shader and one fixed indexed quad. `ArenaRenderer`
draws it after the world batch for each already-scissored camera, then restores
the full viewport and draws the HUD normally. Choice ceremonies and pause UI
retain their existing draw order above the arena. Results and menu backgrounds
do not use the effect.

The pass adds one draw in solo or two in co-op, tracked in the existing world
draw counters. It allocates no textures, framebuffers, decoded Pixmaps or
per-frame objects. The fixed mesh and managed shader support GL context
restoration. The combined 32 MiB texture ceiling is unchanged.

## Verification

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
